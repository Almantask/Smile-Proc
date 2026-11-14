package com.example.smileproc.data

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.StorageState
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.notify.CheckInNotifier
import kotlinx.coroutines.flow.first
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
class DeleteAllTest {

    private lateinit var context: Context
    private lateinit var dbName: String
    private val openContainers = mutableListOf<AppContainer>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "delete-all-test-${System.nanoTime()}.db"
    }

    @After
    fun tearDown() = runTest {
        openContainers.forEach { it.close() }
        openContainers.clear()
        context.deleteDatabase(dbName)
    }

    @Test
    fun deleteAllClearsAllDataCancelsNotificationAndResetsToFreshStorage() = runTest {
        val sealer = FakeManagedSealer()
        val container = AppContainer(
            context = context,
            sealer = sealer,
            dbName = dbName,
            autoInitialize = false,
        ).also { openContainers += it }

        val ready = container.initialize() as StorageState.Ready
        val storage = ready.storage

        // Seed data
        storage.entryRepository.save(
            Entry(
                id = "11111111-1111-4111-8111-111111111111",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                timeZoneID = "Europe/Rome",
                mood = Mood.GOOD,
                tags = emptyList(),
                comment = null,
                location = null,
                source = EntrySource.APP,
            )
        )
        storage.placeRepository.save(
            PlaceLabel(
                id = "22222222-2222-4222-8222-222222222222",
                name = "Home",
                kind = PlaceKind.HOME,
                anchorLatitude = 41.9028,
                anchorLongitude = 12.4964,
                createdAt = Instant.now(),
            )
        )
        container.settingsRepository.setOnboardingCompleted(true)
        container.settingsRepository.setStanding(true)
        container.settingsRepository.setAppLock(true)

        // Post a notification
        val nm = context.getSystemService(NotificationManager::class.java)
        CheckInNotifier.render(
            context,
            com.example.smileproc.core.CheckInMachine.State.Faces(alerting = true),
            ongoing = true,
        )
        assertTrue(nm.activeNotifications.any { it.id == CheckInNotifier.ID_CHECKIN })

        // Seed notification tracking, extras, custom tags, windows, pause
        val customTag = com.example.smileproc.core.CustomTag.create("Journaling", Instant.now(), id = "custom.j1")
        storage.customTagRepository.save(customTag)
        storage.entryExtrasRepository.save(
            com.example.smileproc.core.EntryExtras(
                entryId = "11111111-1111-4111-8111-111111111111",
                contextTags = listOf(com.example.smileproc.core.BuiltInContextTag.REST.id),
                customTagIds = listOf(customTag.id),
                bookmarked = true,
                updatedAt = Instant.now(),
            ),
        )
        container.settingsRepository.setReminderWindows(mapOf("r1" to 30))
        container.settingsRepository.setReminderPause(com.example.smileproc.core.ReminderPause.INDEFINITE)
        container.settingsRepository.setLastNotificationEntry("11111111-1111-4111-8111-111111111111", 123456L)

        // Seed error log
        container.errorLogStore.record(com.example.smileproc.SafeLog.Event.NOTIFY_FAILED, RuntimeException("Test"))
        assertTrue(container.errorLogStore.count() > 0)

        // Act: Delete all data
        DeleteAllManager.deleteAll(context, container)

        // Assert 1: Notification cancelled
        assertFalse(nm.activeNotifications.any { it.id == CheckInNotifier.ID_CHECKIN })

        // Assert 2: Onboarding, App lock, pause, windows, and lastNotificationEntry reset
        assertFalse(container.settingsRepository.onboardingCompleted.first())
        assertFalse(container.settingsRepository.appLock.first())
        assertEquals(emptyMap<String, Int>(), container.settingsRepository.reminderWindows.first())
        assertEquals(com.example.smileproc.core.ReminderPause.NONE, container.settingsRepository.reminderPause.first())
        assertEquals(null, container.settingsRepository.lastNotificationEntry.first())

        // Assert 4: Storage fresh and empty (including v2 tables)
        val stateAfter = container.awaitState() as StorageState.Ready
        assertEquals(0, stateAfter.storage.entryRepository.getAllOldestFirst().size)
        assertEquals(0, stateAfter.storage.placeRepository.getAll().size)
        assertEquals(0, stateAfter.storage.entryExtrasRepository.getAll().size)
        assertEquals(0, stateAfter.storage.customTagRepository.getAll().size)

        // Assert 5: Error logs cleared
        assertEquals(0, container.errorLogStore.count())
    }

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
