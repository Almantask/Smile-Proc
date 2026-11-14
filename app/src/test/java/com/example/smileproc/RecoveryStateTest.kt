package com.example.smileproc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Variant
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WindowKind
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.ManagedSealer
import com.example.smileproc.data.db.SmileProcDatabase
import com.example.smileproc.data.db.WeeklyCardRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RecoveryStateTest {

    private lateinit var context: Context
    private lateinit var dbName: String
    private val openContainers = mutableListOf<AppContainer>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "recovery-test-${System.nanoTime()}.db"
    }

    @After
    fun tearDown() = runTest {
        openContainers.forEach { it.close() }
        openContainers.clear()
        context.deleteDatabase(dbName)
    }

    @Test
    fun freshInstallProvisionsKeyReopensCleanlyAndMissingKeyRoutesToRecoveryWithoutWipingData() = runTest {
        val sealer = FakeManagedSealer()
        assertFalse(sealer.isKeyAvailable())

        // 1. Fresh install: no DB on disk -> provisions key and opens Ready.
        val firstLaunch = newContainer(sealer)
        val firstState = firstLaunch.initialize()
        assertTrue(firstState is StorageState.Ready)
        assertTrue(sealer.isKeyAvailable())

        val sample = Entry(
            id = "aaaaaaaa-1111-4111-8111-111111111111",
            createdAt = Instant.parse("2026-10-12T09:00:00.000Z"),
            updatedAt = Instant.parse("2026-10-12T09:00:00.000Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.GOOD,
            tags = listOf("calm.peaceful"),
            comment = "Preserved across normal restart",
            location = null,
            source = EntrySource.APP,
        )
        (firstState as StorageState.Ready).storage.entryRepository.save(sample)
        firstLaunch.close()

        val dbFile = context.getDatabasePath(dbName)
        assertTrue(dbFile.exists() && dbFile.length() > 0L)

        // 2. Normal restart with same key -> Ready and entry still readable.
        val secondLaunch = newContainer(sealer)
        val secondState = secondLaunch.initialize()
        assertTrue(secondState is StorageState.Ready)
        assertEquals(
            listOf(sample),
            (secondState as StorageState.Ready).storage.entryRepository.getAllNewestFirst(),
        )
        secondLaunch.close()

        // 3. Key disappears while DB remains on disk -> NeedsRecovery, no auto-key, DB untouched.
        val preRecoveryBytes = dbFile.readBytes()
        sealer.deleteKeyIfPresent()
        assertFalse(sealer.isKeyAvailable())

        val recoveryLaunch = newContainer(sealer)
        val recoveryState = recoveryLaunch.initialize()
        assertEquals(StorageState.NeedsRecovery, recoveryState)
        assertFalse("Must never auto-provision a replacement key", sealer.isKeyAvailable())
        assertTrue("Must never wipe existing DB silently", dbFile.exists() && dbFile.length() > 0L)
        assertTrue(preRecoveryBytes.contentEquals(dbFile.readBytes()))

        // 4. Explicit Start fresh -> wipes unopenable DB, provisions new key, opens empty Ready DB.
        val freshState = recoveryLaunch.startFresh()
        assertTrue(freshState is StorageState.Ready)
        assertTrue(sealer.isKeyAvailable())
        val freshRepo = (freshState as StorageState.Ready).storage.entryRepository
        assertEquals(0, freshRepo.count())

        val postRecoveryEntry = sample.copy(
            id = "bbbbbbbb-2222-4222-8222-222222222222",
            comment = "Saved after Start fresh",
        )
        freshRepo.save(postRecoveryEntry)
        assertEquals(listOf(postRecoveryEntry), freshRepo.getAllNewestFirst())
    }

    @Test
    fun mismatchedKeyWithPopulatedDatabaseRoutesToRecoveryWithoutDeletingDatabase() = runTest {
        val originalSealer = FakeManagedSealer()
        val firstLaunch = newContainer(originalSealer)
        val firstState = firstLaunch.initialize() as StorageState.Ready
        firstState.storage.entryRepository.save(
            Entry(
                id = "cccccccc-3333-4333-8333-333333333333",
                createdAt = Instant.parse("2026-10-12T12:00:00.000Z"),
                updatedAt = Instant.parse("2026-10-12T12:00:00.000Z"),
                timeZoneID = "UTC",
                mood = Mood.OKAY,
                tags = emptyList(),
                comment = null,
                location = null,
                source = EntrySource.NOTIFICATION,
            ),
        )
        firstLaunch.close()

        // Different key present in Keystore while old DB remains on disk.
        val wrongSealer = FakeManagedSealer().apply { ensureKeyExists() }
        val mismatchedLaunch = newContainer(wrongSealer)
        assertEquals(StorageState.NeedsRecovery, mismatchedLaunch.initialize())
        assertTrue(context.getDatabasePath(dbName).exists())
    }

    @Test
    fun startFreshFailureReturnsNeedsRecoveryWithoutThrowing() = runTest {
        val failingSealer = FakeManagedSealer().apply { failNextEnsureKey = true }
        val container = newContainer(failingSealer)

        val result = container.startFresh()
        assertEquals(StorageState.NeedsRecovery, result)
        assertEquals(StorageState.NeedsRecovery, container.storageState.value)
    }

    @Test
    fun cancellingCallerDuringInitializeDoesNotPoisonStateIntoNeedsRecovery() = runTest {
        val sealer = FakeManagedSealer().apply { cancelNextEnsureKey = true }
        val container = newContainer(sealer)

        var caughtCancellation = false
        try {
            container.awaitState()
        } catch (_: CancellationException) {
            caughtCancellation = true
        }
        assertTrue(caughtCancellation)
        assertEquals(StorageState.Loading, container.storageState.value)

        // Subsequent caller succeeds cleanly and transitions to Ready.
        val settled = container.awaitState()
        assertTrue(settled is StorageState.Ready)
        assertEquals(settled, container.storageState.value)
    }

    @Test
    fun reopenPreservesEntriesPlacesWeeklyCardsAcrossUpdateWithSameKey() = runTest {
        val sealer = FakeManagedSealer()
        val firstLaunch = newContainer(sealer)
        val firstStorage = (firstLaunch.initialize() as StorageState.Ready).storage

        val entry = Entry(
            id = "dddddddd-4444-4444-8444-444444444444",
            createdAt = Instant.parse("2026-10-12T08:30:00.000Z"),
            updatedAt = Instant.parse("2026-10-12T08:30:00.000Z"),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = listOf("calm.peaceful"),
            comment = "Preserved across APK update",
            location = null,
            source = EntrySource.APP,
        )
        val place = PlaceLabel(
            id = "eeeeeeee-5555-4555-8555-555555555555",
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 37.7749,
            anchorLongitude = -122.4194,
            createdAt = Instant.parse("2026-10-12T08:00:00.000Z"),
        )
        val cardStart = Instant.parse("2026-10-12T00:00:00.000Z")
        val card = WeeklyCard(
            periodStart = cardStart,
            periodEnd = Instant.parse("2026-10-19T00:00:00.000Z"),
            windowKind = WindowKind.WEEK,
            entryCount = 1,
            moodCounts = listOf(0, 0, 1),
            days = List(7) { idx -> if (idx == 0) listOf(0, 0, 1) else listOf(0, 0, 0) },
            isLowWeek = false,
            variant = Variant.FIRST_WEEK,
            items = emptyList(),
        )
        firstStorage.entryRepository.save(entry)
        firstStorage.placeRepository.save(place)
        firstStorage.weeklyCardRepository.save(card)
        firstLaunch.close()

        val upgradedLaunch = newContainer(sealer)
        val upgradedStorage = (upgradedLaunch.initialize() as StorageState.Ready).storage
        assertEquals(listOf(entry), upgradedStorage.entryRepository.getAllNewestFirst())
        assertEquals(listOf(place), upgradedStorage.placeRepository.getAll())
        assertEquals(card, upgradedStorage.weeklyCardRepository.getByPeriodStart(cardStart))
    }

    @Test
    fun staleWeeklyCardFormatAfterUpdateClearsOnlyCacheWithoutLosingEntries() = runTest {
        val sealer = FakeManagedSealer()
        val firstLaunch = newContainer(sealer)
        val firstStorage = (firstLaunch.initialize() as StorageState.Ready).storage

        val entry = Entry(
            id = "ffffffff-6666-4666-8666-666666666666",
            createdAt = Instant.parse("2026-10-13T10:00:00.000Z"),
            updatedAt = Instant.parse("2026-10-13T10:00:00.000Z"),
            timeZoneID = "UTC",
            mood = Mood.OKAY,
            tags = emptyList(),
            comment = "Must survive weekly card cache reset",
            location = null,
            source = EntrySource.NOTIFICATION,
        )
        firstStorage.entryRepository.save(entry)
        firstLaunch.close()

        // Validly sealed payload whose JSON structure doesn't match WeeklyCard after an upgrade.
        val periodStartMillis = Instant.parse("2026-10-12T00:00:00.000Z").toEpochMilli()
        val rawDb = SmileProcDatabase.open(context, dbName)
        try {
            rawDb.weeklyCardDao().upsert(
                WeeklyCardRow(
                    periodStart = periodStartMillis,
                    payload = sealer.seal(
                        periodStartMillis.toString(),
                        "{\"legacy\":true}".toByteArray(Charsets.UTF_8),
                    ),
                ),
            )
        } finally {
            rawDb.close()
        }

        val upgradedLaunch = newContainer(sealer)
        val state = upgradedLaunch.initialize()
        assertTrue(state is StorageState.Ready)
        val upgradedStorage = (state as StorageState.Ready).storage
        assertEquals(listOf(entry), upgradedStorage.entryRepository.getAllNewestFirst())
        assertEquals(0, upgradedStorage.weeklyCardRepository.count())
    }

    @Test
    fun corruptedWeeklyCardSealStillRoutesToRecoveryWithoutDroppingEntries() = runTest {
        val sealer = FakeManagedSealer()
        val firstLaunch = newContainer(sealer)
        val firstStorage = (firstLaunch.initialize() as StorageState.Ready).storage
        val entry = Entry(
            id = "11111111-7777-4777-8777-777777777777",
            createdAt = Instant.parse("2026-10-14T10:00:00.000Z"),
            updatedAt = Instant.parse("2026-10-14T10:00:00.000Z"),
            timeZoneID = "UTC",
            mood = Mood.LOW,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
        firstStorage.entryRepository.save(entry)
        firstLaunch.close()

        val periodStartMillis = Instant.parse("2026-10-12T00:00:00.000Z").toEpochMilli()
        val rawDb = SmileProcDatabase.open(context, dbName)
        try {
            rawDb.weeklyCardDao().upsert(
                WeeklyCardRow(
                    periodStart = periodStartMillis,
                    payload = ByteArray(32) { 0x5A },
                ),
            )
        } finally {
            rawDb.close()
        }

        val upgradedLaunch = newContainer(sealer)
        assertEquals(StorageState.NeedsRecovery, upgradedLaunch.initialize())
    }

    private fun newContainer(sealer: ManagedSealer): AppContainer =
        AppContainer(
            context = context,
            sealer = sealer,
            dbName = dbName,
            autoInitialize = false,
        ).also { openContainers += it }

    private class FakeManagedSealer : ManagedSealer {
        private var delegate: JcaSealer? = null
        var failNextEnsureKey: Boolean = false
        var cancelNextEnsureKey: Boolean = false

        override fun isKeyAvailable(): Boolean = delegate?.isKeyAvailable() == true

        override fun ensureKeyExists() {
            if (cancelNextEnsureKey) {
                cancelNextEnsureKey = false
                throw CancellationException("Caller cancelled")
            }
            if (failNextEnsureKey) {
                failNextEnsureKey = false
                throw SealException(SealException.Reason.SEAL_FAILED)
            }
            if (delegate == null) {
                delegate = JcaSealer.withNewKey()
            }
        }

        override fun deleteKeyIfPresent() {
            delegate = null
        }

        override fun seal(rowId: String, plaintext: ByteArray): ByteArray {
            val active = delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)
            return active.seal(rowId, plaintext)
        }

        override fun open(rowId: String, payload: ByteArray): ByteArray {
            val active = delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)
            return active.open(rowId, payload)
        }
    }
}
