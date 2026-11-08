package com.example.smileproc.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.StorageState
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Reminder
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.core.export.ExportCrypto
import com.example.smileproc.core.export.ExportException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ExportImportTest {

    private lateinit var context: Context
    private lateinit var dbName1: String
    private lateinit var dbName2: String
    private val openContainers = mutableListOf<AppContainer>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName1 = "export-test-1-${System.nanoTime()}.db"
        dbName2 = "export-test-2-${System.nanoTime()}.db"
    }

    @After
    fun tearDown() = runTest {
        openContainers.forEach { it.close() }
        openContainers.clear()
        context.deleteDatabase(dbName1)
        context.deleteDatabase(dbName2)
    }

    @Test
    fun roundTripExportAndImportRestoresEntriesPlacesAndSettings() = runTest {
        val sealer1 = FakeManagedSealer()
        val container1 = createContainer(sealer1, dbName1)
        val ready1 = container1.initialize() as StorageState.Ready
        val storage1 = ready1.storage

        val entry = Entry(
            id = "e1111111-1111-4111-8111-111111111111",
            createdAt = Instant.parse("2026-10-12T09:00:00Z"),
            updatedAt = Instant.parse("2026-10-12T09:00:00Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.GOOD,
            tags = listOf("calm.peaceful"),
            comment = "Great morning",
            location = null,
            source = EntrySource.APP,
        )
        storage1.entryRepository.save(entry)

        val place = PlaceLabel(
            id = "p1111111-1111-4111-8111-111111111111".replace('p', '2'),
            name = "Coffee Shop",
            kind = PlaceKind.OTHER,
            anchorLatitude = 41.9028,
            anchorLongitude = 12.4964,
            createdAt = Instant.parse("2026-10-10T12:00:00Z"),
        )
        storage1.placeRepository.save(place)

        container1.settingsRepository.setReminders(listOf(Reminder("r1", 9, 30, setOf(1, 2, 3))))
        container1.settingsRepository.setStanding(true)
        container1.settingsRepository.setWeeklyCardTime(WeeklyCardTime(weekday = 7, hour = 19, minute = 0))
        container1.settingsRepository.setTagPickerMode(TagPickerMode.DETAILED)
        container1.settingsRepository.setCaptureLocation(false)

        val manager1 = ExportImportManager(context, container1)
        val password = "StrongPassword123!".toCharArray()

        val outputStream = ByteArrayOutputStream()
        manager1.exportToStream(outputStream, password)
        val exportedBytes = outputStream.toByteArray()
        assertTrue(exportedBytes.isNotEmpty())

        // Now import into fresh container 2
        val sealer2 = FakeManagedSealer()
        val container2 = createContainer(sealer2, dbName2)
        container2.initialize()

        val manager2 = ExportImportManager(context, container2)
        val importResult = manager2.importFromStream(ByteArrayInputStream(exportedBytes), password)

        assertEquals(1, importResult.entriesImported)
        assertEquals(1, importResult.placesImported)

        val ready2 = container2.awaitState() as StorageState.Ready
        val storage2 = ready2.storage

        val restoredEntry = storage2.entryRepository.getById(entry.id)
        assertNotNull(restoredEntry)
        assertEquals("Great morning", restoredEntry?.comment)
        assertEquals(Mood.GOOD, restoredEntry?.mood)

        val restoredPlace = storage2.placeRepository.getById(place.id)
        assertNotNull(restoredPlace)
        assertEquals("Coffee Shop", restoredPlace?.name)
        assertEquals(PlaceKind.OTHER, restoredPlace?.kind)

        assertEquals(listOf(Reminder("r1", 9, 30, setOf(1, 2, 3))), container2.settingsRepository.reminders.first())
        assertEquals(true, container2.settingsRepository.standing.first())
        assertEquals(WeeklyCardTime(7, 19, 0), container2.settingsRepository.weeklyCardTime.first())
        assertEquals(TagPickerMode.DETAILED, container2.settingsRepository.tagPickerMode.first())
        assertEquals(false, container2.settingsRepository.captureLocation.first())
    }

    @Test
    fun importMergesByNewerUpdatedAt() = runTest {
        val sealer = FakeManagedSealer()
        val container = createContainer(sealer, dbName1)
        val ready = container.initialize() as StorageState.Ready
        val storage = ready.storage

        val existing = Entry(
            id = "e1111111-1111-4111-8111-111111111111",
            createdAt = Instant.parse("2026-10-12T09:00:00Z"),
            updatedAt = Instant.parse("2026-10-12T10:00:00Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.OKAY,
            tags = emptyList(),
            comment = "Initial local edit",
            location = null,
            source = EntrySource.APP,
        )
        storage.entryRepository.save(existing)

        val olderEntry = existing.copy(
            updatedAt = Instant.parse("2026-10-12T09:30:00Z"),
            comment = "Older imported edit",
        )
        val newerEntry = existing.copy(
            updatedAt = Instant.parse("2026-10-12T11:00:00Z"),
            comment = "Newer imported edit",
        )

        val password = "SecretPassword1!".toCharArray()
        val manager = ExportImportManager(context, container)

        // 1. Export with older entry
        val payloadOlder = manager.createExportPayload().copy(entries = listOf(olderEntry))
        val jsonOlder = com.example.smileproc.core.CanonicalJson.json.encodeToString(
            com.example.smileproc.core.export.ExportPayload.serializer(), payloadOlder
        )
        val envelopeOlder = ExportCrypto.encrypt(jsonOlder, password, iterations = 1000)
        val bytesOlder = com.example.smileproc.core.CanonicalJson.json.encodeToString(
            com.example.smileproc.core.export.ExportEnvelope.serializer(), envelopeOlder
        ).toByteArray(Charsets.UTF_8)

        // Importing older entry should NOT overwrite local newer entry
        manager.importFromStream(ByteArrayInputStream(bytesOlder), password)
        assertEquals("Initial local edit", storage.entryRepository.getById(existing.id)?.comment)

        // 2. Export with newer entry
        val payloadNewer = manager.createExportPayload().copy(entries = listOf(newerEntry))
        val jsonNewer = com.example.smileproc.core.CanonicalJson.json.encodeToString(
            com.example.smileproc.core.export.ExportPayload.serializer(), payloadNewer
        )
        val envelopeNewer = ExportCrypto.encrypt(jsonNewer, password, iterations = 1000)
        val bytesNewer = com.example.smileproc.core.CanonicalJson.json.encodeToString(
            com.example.smileproc.core.export.ExportEnvelope.serializer(), envelopeNewer
        ).toByteArray(Charsets.UTF_8)

        // Importing newer entry SHOULD overwrite local entry
        manager.importFromStream(ByteArrayInputStream(bytesNewer), password)
        assertEquals("Newer imported edit", storage.entryRepository.getById(existing.id)?.comment)
    }

    @Test
    fun wrongPasswordThrowsExportException() = runTest {
        val sealer = FakeManagedSealer()
        val container = createContainer(sealer, dbName1)
        container.initialize()
        val manager = ExportImportManager(context, container)

        val outputStream = ByteArrayOutputStream()
        manager.exportToStream(outputStream, "CorrectPassword123!".toCharArray())

        var caught: ExportException? = null
        try {
            manager.importFromStream(
                ByteArrayInputStream(outputStream.toByteArray()),
                "WrongPassword456!".toCharArray(),
            )
        } catch (e: ExportException) {
            caught = e
        }
        assertNotNull(caught)
        assertEquals(ExportException.Reason.WRONG_PASSWORD_OR_CORRUPT, caught?.reason)
    }

    @Test
    fun corruptFileThrowsInvalidPayload() = runTest {
        val sealer = FakeManagedSealer()
        val container = createContainer(sealer, dbName1)
        container.initialize()
        val manager = ExportImportManager(context, container)

        val corruptBytes = "not-json-content".toByteArray(Charsets.UTF_8)
        var caught: ExportException? = null
        try {
            manager.importFromStream(ByteArrayInputStream(corruptBytes), "AnyPassword123!".toCharArray())
        } catch (e: ExportException) {
            caught = e
        }
        assertNotNull(caught)
        assertEquals(ExportException.Reason.INVALID_PAYLOAD, caught?.reason)
    }

    @Test
    fun importingTwiceDoesNotDuplicate() = runTest {
        val sealer = FakeManagedSealer()
        val container = createContainer(sealer, dbName1)
        val ready = container.initialize() as StorageState.Ready
        val storage = ready.storage
        val manager = ExportImportManager(context, container)

        val entry = Entry(
            id = "e1111111-1111-4111-8111-111111111111",
            createdAt = Instant.parse("2026-10-12T09:00:00Z"),
            updatedAt = Instant.parse("2026-10-12T10:00:00Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.GOOD,
            tags = emptyList(),
            comment = "Check-in 1",
            location = null,
            source = EntrySource.APP,
        )
        val place = PlaceLabel(
            id = "b1111111-1111-4111-8111-111111111111",
            name = "Home Base",
            kind = PlaceKind.HOME,
            createdAt = Instant.parse("2026-10-12T09:00:00Z"),
            anchorLatitude = 45.0,
            anchorLongitude = 9.0,
        )
        storage.entryRepository.save(entry)
        storage.placeRepository.save(place)

        val password = "ExportPassword123!".toCharArray()
        val out = ByteArrayOutputStream()
        manager.exportToStream(out, password)
        val exportedBytes = out.toByteArray()

        // Import the same export once
        manager.importFromStream(ByteArrayInputStream(exportedBytes), password)
        // Import the exact same export a second time
        manager.importFromStream(ByteArrayInputStream(exportedBytes), password)

        // Verify counts did not double
        val allEntries = storage.entryRepository.getAllOldestFirst()
        assertEquals(1, allEntries.size)
        assertEquals("e1111111-1111-4111-8111-111111111111", allEntries[0].id)

        val allPlaces = storage.placeRepository.getAll()
        assertEquals(1, allPlaces.size)
        assertEquals("b1111111-1111-4111-8111-111111111111", allPlaces[0].id)
    }

    private fun createContainer(sealer: ManagedSealer, dbName: String): AppContainer =
        AppContainer(
            context = context,
            sealer = sealer,
            dbName = dbName,
            autoInitialize = false,
        ).also { openContainers += it }

    private class FakeManagedSealer : ManagedSealer {
        private var delegate: JcaSealer? = null
        override fun isKeyAvailable(): Boolean = delegate?.isKeyAvailable() == true
        override fun ensureKeyExists() {
            if (delegate == null) delegate = JcaSealer.withNewKey()
        }
        override fun deleteKeyIfPresent() { delegate = null }
        override fun seal(rowId: String, plaintext: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).seal(rowId, plaintext)
        override fun open(rowId: String, payload: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).open(rowId, payload)
    }
}
