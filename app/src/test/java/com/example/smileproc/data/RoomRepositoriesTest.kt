package com.example.smileproc.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.CardItem
import com.example.smileproc.core.Confidence
import com.example.smileproc.core.DetectedPattern
import com.example.smileproc.core.Detector
import com.example.smileproc.core.Direction
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Family
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PatternParams
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Variant
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WindowKind
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.db.EntryRow
import com.example.smileproc.data.db.SmileProcDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {

    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: SmileProcDatabase
    private lateinit var sealer: JcaSealer
    private lateinit var entryRepo: EntryRepository
    private lateinit var placeRepo: PlaceRepository
    private lateinit var cardRepo: WeeklyCardRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-smileproc-${System.nanoTime()}.db"
        db = SmileProcDatabase.open(context = context, dbName = dbName)
        sealer = JcaSealer.withNewKey()
        entryRepo = EntryRepository(db.entryDao(), sealer)
        placeRepo = PlaceRepository(db.placeDao(), sealer)
        cardRepo = WeeklyCardRepository(db.weeklyCardDao(), sealer)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun entryPlaceAndWeeklyCardRoundTripOrderingAndZeroPlaintextInRawRows() = runTest {
        val earlier = Entry(
            id = "11111111-1111-4111-8111-111111111111",
            createdAt = Instant.parse("2026-10-12T08:00:00.000Z"),
            updatedAt = Instant.parse("2026-10-12T08:00:00.000Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.LOW,
            tags = listOf("tired.drained"),
            comment = "UniqueSecretMorningComment",
            location = LocationSample(
                latitude = 45.4642,
                longitude = 9.19,
                horizontalAccuracy = 10.0,
                capturedAt = Instant.parse("2026-10-12T08:00:02.000Z"),
                kind = LocationKind.FRESH,
            ),
            source = EntrySource.APP,
        )
        val later = Entry(
            id = "22222222-2222-4222-8222-222222222222",
            createdAt = Instant.parse("2026-10-12T18:30:00.000Z"),
            updatedAt = Instant.parse("2026-10-12T18:31:00.000Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.GOOD,
            tags = listOf("happy.grateful"),
            comment = null,
            location = null,
            source = EntrySource.NOTIFICATION,
        )

        entryRepo.save(earlier)
        entryRepo.save(later)

        assertEquals(2, entryRepo.count())
        assertEquals(listOf(later, earlier), entryRepo.getAllNewestFirst())
        assertEquals(listOf(earlier, later), entryRepo.getAllOldestFirst())
        assertEquals(listOf(later, earlier), entryRepo.observeAllNewestFirst().first())
        assertEquals(
            listOf(earlier),
            entryRepo.observeInRange(
                Instant.parse("2026-10-12T07:00:00.000Z"),
                Instant.parse("2026-10-12T12:00:00.000Z"),
            ).first(),
        )

        // Inspect raw SQLite payload bytes: zero plaintext mood tags, comments, or coordinates.
        val rawEntryRow = db.entryDao().getById(earlier.id)!!
        val rawLatin = rawEntryRow.payload.toString(Charsets.ISO_8859_1)
        assertFalse(rawLatin.contains("UniqueSecretMorningComment"))
        assertFalse(rawLatin.contains("tired.drained"))
        assertFalse(rawLatin.contains("45.4642"))

        // PlaceRepository round-trip + raw check
        val home = PlaceLabel(
            id = "33333333-3333-4333-8333-333333333333",
            name = "SecretHomeLabelName",
            kind = PlaceKind.HOME,
            anchorLatitude = 45.4642,
            anchorLongitude = 9.19,
            createdAt = Instant.parse("2026-10-01T10:00:00.000Z"),
        )
        placeRepo.save(home)
        assertEquals(home, placeRepo.getById(home.id))
        assertEquals(listOf(home), placeRepo.observeAll().first())
        val rawPlaceLatin = db.placeDao().getById(home.id)!!.payload.toString(Charsets.ISO_8859_1)
        assertFalse(rawPlaceLatin.contains("SecretHomeLabelName"))

        // WeeklyCardRepository round-trip
        val card = WeeklyCard(
            periodStart = Instant.parse("2026-10-11T17:00:00.000Z"),
            periodEnd = Instant.parse("2026-10-18T17:00:00.000Z"),
            windowKind = WindowKind.WEEK,
            entryCount = 2,
            moodCounts = listOf(1, 0, 1),
            days = listOf(
                listOf(1, 0, 1),
                listOf(0, 0, 0),
                listOf(0, 0, 0),
                listOf(0, 0, 0),
                listOf(0, 0, 0),
                listOf(0, 0, 0),
                listOf(0, 0, 0),
            ),
            isLowWeek = false,
            variant = Variant.NOT_ENOUGH_DATA,
            items = listOf(
                CardItem(
                    patternID = "timeOfDay.lower.morning",
                    libraryID = "time-lower",
                    pattern = DetectedPattern(
                        id = "timeOfDay.lower.morning",
                        detector = Detector.TIME_OF_DAY,
                        family = Family.TIME,
                        direction = Direction.LOWER,
                        params = PatternParams(segment = "morning"),
                        nSegment = 3,
                        nRest = 3,
                        meanSegment = 1.0,
                        meanRest = 3.0,
                        diff = -2.0,
                        z = -3.0,
                        score = 0.6,
                        confidence = Confidence.EARLY_HINT,
                        supportingEntryIDs = listOf(earlier.id),
                    ),
                ),
            ),
        )
        cardRepo.save(card)
        assertEquals(card, cardRepo.getByPeriodStart(card.periodStart))
        assertEquals(listOf(card), cardRepo.observeAllNewestFirst().first())

        entryRepo.deleteById(earlier.id)
        assertNull(entryRepo.getById(earlier.id))
    }

    @Test
    fun payloadTamperAndRowIdSwapFailAadAuthentication() = runTest {
        val entryA = Entry(
            id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            createdAt = Instant.parse("2026-10-12T08:00:00.000Z"),
            updatedAt = Instant.parse("2026-10-12T08:00:00.000Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.OKAY,
            tags = emptyList(),
            comment = "Row A payload",
            location = null,
            source = EntrySource.APP,
        )
        entryRepo.save(entryA)
        val rawA = db.entryDao().getById(entryA.id)!!

        // Copy valid ciphertext from row A into row B (wrong id -> AAD mismatch).
        val swappedId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        db.entryDao().upsert(
            EntryRow(
                id = swappedId,
                createdAt = rawA.createdAt,
                updatedAt = rawA.updatedAt,
                payload = rawA.payload.copyOf(),
            ),
        )
        expectAuthFailure { entryRepo.getById(swappedId) }

        // Flip one byte in row A's stored ciphertext -> GCM authentication fails.
        val tampered = rawA.payload.copyOf().also { it[15] = (it[15].toInt() xor 0xFF).toByte() }
        db.entryDao().upsert(rawA.copy(payload = tampered))
        expectAuthFailure { entryRepo.getById(entryA.id) }
    }

    private suspend fun expectAuthFailure(block: suspend () -> Any?) {
        try {
            block()
            throw AssertionError("Expected SealException(AUTHENTICATION_OR_DECRYPT_FAILED)")
        } catch (e: SealException) {
            assertEquals(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED, e.reason)
        }
    }
}
