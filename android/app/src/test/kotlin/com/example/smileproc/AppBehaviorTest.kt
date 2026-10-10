package com.example.smileproc

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.ContentLoader
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.ExportApp
import com.example.smileproc.core.ExportDocument
import com.example.smileproc.core.ExportSettings
import com.example.smileproc.core.JcaSealer
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.ReminderSpec
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.data.SmileDatabase
import com.example.smileproc.data.SmileRepository
import com.example.smileproc.notify.CheckInNotifications
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppBehaviorTest {
    private val database: SmileDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SmileDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val repository: SmileRepository

    init {
        val spec = File(requireNotNull(System.getProperty("smileproc.spec")))
        val config = ContentLoader.patternConfig(spec.resolve("content/pattern-config.json").readText())
        val library = ContentLoader.patternLibrary(spec.resolve("content/pattern-library.en.json").readText())
        val emotions = ContentLoader.emotionTags(spec.resolve("content/emotion-tags.json").readText())
        repository = SmileRepository(
            database = database,
            sealer = JcaSealer(ByteArray(32) { 7 }),
            allowedTags = emotions.ids,
            config = config,
            library = library,
        )
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun sealedEntryRoundTrips() = runBlocking {
        val created = Instant.parse("2026-10-10T12:00:00.000Z")
        val entry = Entry(
            id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            createdAt = created,
            updatedAt = created,
            timeZoneID = "Europe/Rome",
            mood = Mood.LOW,
            tags = listOf("sad", "sad", "tired"),
            comment = "  hello  ",
            location = null,
            source = EntrySource.APP,
        )
        repository.upsert(entry)
        val stored = repository.entry(entry.id)
        assertEquals(Mood.LOW, stored?.mood)
        assertEquals(listOf("sad", "tired"), stored?.tags)
        assertEquals("hello", stored?.comment)
        assertEquals("app", stored?.source?.wire)
    }

    @Test
    fun importMergesByIdAndDoesNotDuplicate() = runBlocking {
        val older = sample("same-id", "2026-10-01T00:00:00.000Z", Mood.OKAY)
        val newer = sample("same-id", "2026-10-02T00:00:00.000Z", Mood.GOOD)
        val other = sample("other-id", "2026-10-03T00:00:00.000Z", Mood.LOW)
        repository.importDocument(document(listOf(older), emptyList()))
        repository.importDocument(document(listOf(newer, other), emptyList()))
        repository.importDocument(document(listOf(older, other), emptyList()))
        val entries = repository.entries()
        assertEquals(listOf("other-id", "same-id"), entries.map { it.id }.sorted())
        assertEquals(Mood.GOOD, entries.first { it.id == "same-id" }.mood)
    }

    @Test
    fun importKeepsExistingPlaceWhenCreatedAtIsNotNewer() = runBlocking {
        val created = Instant.parse("2026-10-01T00:00:00.000Z")
        val existing = PlaceLabel("place-1", "Home", PlaceKind.HOME, 1.0, 2.0, created)
        val same = existing.copy(name = "Elsewhere")
        val later = existing.copy(name = "Work", createdAt = Instant.parse("2026-10-05T00:00:00.000Z"))
        repository.importDocument(document(emptyList(), listOf(existing)))
        repository.importDocument(document(emptyList(), listOf(same)))
        assertEquals("Home", repository.places().single().name)
        repository.importDocument(document(emptyList(), listOf(later)))
        assertEquals("Work", repository.places().single().name)
    }

    @Test
    fun checkInNotificationStaysPublicWithoutAuth() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = CheckInNotifications.checkIn(context)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(context.getString(R.string.notification_checkin_title), notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(context.getString(R.string.notification_checkin_body), notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(3, notification.actions.size)
        for (action in notification.actions) {
            assertFalse(action.extras.getBoolean("android.authenticationRequired"))
            val pending = requireNotNull(action.actionIntent)
            val shadow = Shadows.shadowOf(pending)
            assertTrue(shadow.isBroadcast)
            assertTrue(shadow.isImmutable)
            assertEquals(PendingIntent.FLAG_IMMUTABLE, shadow.flags and PendingIntent.FLAG_IMMUTABLE)
        }
        assertTrue(notification.actions[0].title.isNotEmpty())
    }

    private fun sample(id: String, stamp: String, mood: Mood): Entry {
        val instant = Instant.parse(stamp)
        return Entry(id, instant, instant, "Europe/Rome", mood, emptyList(), null, null, EntrySource.APP)
    }

    private fun document(entries: List<Entry>, places: List<PlaceLabel>): ExportDocument {
        val encoded = CanonicalJson.exportDocument(
            ExportDocument(
                exportedAt = Instant.parse("2026-10-10T00:00:00.000Z"),
                app = ExportApp("android", "test"),
                entries = entries,
                places = places,
                settings = ExportSettings(emptyList<ReminderSpec>(), WeeklyCardTime(7, 19, 0), "gradual"),
            ),
        )
        return CanonicalJson.parseExport(encoded)
    }
}
