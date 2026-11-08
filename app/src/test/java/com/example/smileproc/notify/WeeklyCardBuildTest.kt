package com.example.smileproc.notify

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.StorageState
import com.example.smileproc.core.CardPeriod
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.WeeklyCardBuilder
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.data.ManagedSealer
import com.example.smileproc.ui.places.PatternConfigCatalog
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.week.PatternLibraryCatalog
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WeeklyCardBuildTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var container: AppContainer
    private lateinit var fakeSealer: FakeManagedSealer
    private lateinit var dbName: String

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        fakeSealer = FakeManagedSealer()
        dbName = "test-card-build-${System.nanoTime()}.db"
        container = AppContainer(
            context = context,
            sealer = fakeSealer,
            dbName = dbName,
            autoInitialize = false,
        )
        container.initialize()
    }

    @After
    fun tearDown() = runTest {
        container.close()
        context.deleteDatabase(dbName)
        notificationManager.cancelAll()
    }

    @Test
    fun buildingWeeklyCard_postsNoNotifications() = runTest {
        val ready = container.awaitState() as StorageState.Ready
        val storage = ready.storage
        val zone = ZoneId.of("Europe/Rome")
        val cardTime = WeeklyCardTime.DEFAULT
        val now = Instant.parse("2026-10-18T20:00:00Z")
        val period = CardPeriod.resolve(now, zone, cardTime)

        // Seed 10 entries across the week
        val entries = (0 until 10).map { i ->
            val t = period.periodStart.plus((i + 1) * 12L, ChronoUnit.HOURS)
            Entry(
                id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
                createdAt = t,
                updatedAt = t,
                timeZoneID = zone.id,
                mood = if (i % 2 == 0) Mood.OKAY else Mood.GOOD,
                tags = emptyList(),
                comment = null,
                location = null,
                source = EntrySource.APP,
            )
        }
        storage.entryRepository.saveAll(entries)

        val config = PatternConfigCatalog.get(context)
        val tags = EmotionTagsCatalog.get(context)
        val library = PatternLibraryCatalog.get(context)

        // Build the card
        val buildResult = WeeklyCardBuilder.build(
            entries = storage.entryRepository.getAllOldestFirst(),
            labels = emptyList(),
            now = now,
            zone = zone,
            config = config,
            emotionTags = tags,
            library = library,
            cardTime = cardTime,
        )

        // Save card into sealed repository
        storage.weeklyCardRepository.save(buildResult.card)

        // Verify stored in repository
        val stored = storage.weeklyCardRepository.getByPeriodStart(period.periodStart)
        assertNotNull(stored)
        assertEquals(10, stored!!.entryCount)

        // INVARIANT: Building or storing weekly card MUST NOT post any notifications
        val notifications = shadowOf(notificationManager).allNotifications
        assertEquals(0, notifications.size)
    }

    @Test
    fun autoBuildEligibility_requiresMinimumFiveEntries() = runTest {
        val ready = container.awaitState() as StorageState.Ready
        val storage = ready.storage
        val config = PatternConfigCatalog.get(context)
        assertEquals(5, config.firstCardMinEntries)

        val zone = ZoneId.of("Europe/Rome")
        val cardTime = WeeklyCardTime.DEFAULT
        val now = Instant.parse("2026-10-18T20:00:00Z")
        val period = CardPeriod.resolve(now, zone, cardTime)

        // Seed 4 entries (< firstCardMinEntries)
        val fourEntries = (0 until 4).map { i ->
            val t = period.periodStart.plus((i + 1) * 12L, ChronoUnit.HOURS)
            Entry(
                id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
                createdAt = t,
                updatedAt = t,
                timeZoneID = zone.id,
                mood = Mood.OKAY,
                tags = emptyList(),
                comment = null,
                location = null,
                source = EntrySource.APP,
            )
        }
        storage.entryRepository.saveAll(fourEntries)

        val entries = storage.entryRepository.getAllOldestFirst()
        val shouldBuildWith4 = entries.size >= config.firstCardMinEntries &&
            storage.weeklyCardRepository.getByPeriodStart(period.periodStart) == null
        org.junit.Assert.assertFalse(shouldBuildWith4)

        // Add 5th entry
        val fifthEntry = Entry(
            id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
            createdAt = period.periodStart.plus(60, ChronoUnit.HOURS),
            updatedAt = period.periodStart.plus(60, ChronoUnit.HOURS),
            timeZoneID = zone.id,
            mood = Mood.GOOD,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
        storage.entryRepository.save(fifthEntry)

        val updatedEntries = storage.entryRepository.getAllOldestFirst()
        val shouldBuildWith5 = updatedEntries.size >= config.firstCardMinEntries &&
            storage.weeklyCardRepository.getByPeriodStart(period.periodStart) == null
        org.junit.Assert.assertTrue(shouldBuildWith5)
    }

    private class FakeManagedSealer : ManagedSealer {
        private var delegate: JcaSealer? = null
        override fun isKeyAvailable(): Boolean = delegate != null
        override fun ensureKeyExists() {
            if (delegate == null) delegate = JcaSealer.withNewKey()
        }
        override fun deleteKeyIfPresent() { delegate = null }
        override fun seal(rowId: String, plaintext: ByteArray): ByteArray =
            (delegate ?: throw com.example.smileproc.core.crypto.SealException(com.example.smileproc.core.crypto.SealException.Reason.KEY_UNAVAILABLE)).seal(rowId, plaintext)
        override fun open(rowId: String, payload: ByteArray): ByteArray =
            (delegate ?: throw com.example.smileproc.core.crypto.SealException(com.example.smileproc.core.crypto.SealException.Reason.KEY_UNAVAILABLE)).open(rowId, payload)
    }
}
