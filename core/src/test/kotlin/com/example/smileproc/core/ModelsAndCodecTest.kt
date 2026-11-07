package com.example.smileproc.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class ModelsAndCodecTest {

    @Test
    fun canonicalEntryRoundTripsSpecExampleAndPreservesExplicitNulls() {
        val entry = Entry(
            id = "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
            createdAt = Instant.parse("2026-10-12T13:05:00Z"),
            updatedAt = Instant.parse("2026-10-12T13:06:10Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.OKAY,
            tags = listOf("anxious.stressed"),
            comment = null,
            location = LocationSample(
                latitude = 45.4642,
                longitude = 9.19,
                horizontalAccuracy = 12.0,
                capturedAt = Instant.parse("2026-10-12T13:05:03Z"),
                kind = LocationKind.FRESH,
            ),
            source = EntrySource.NOTIFICATION,
        )

        val encoded = CanonicalJson.encodeEntry(entry)
        assertTrue("keeps .000Z milliseconds", encoded.contains(""""createdAt":"2026-10-12T13:05:00.000Z""""))
        assertTrue("keeps explicit comment null", encoded.contains(""""comment":null"""))
        assertTrue("encodes mood as int", encoded.contains(""""mood":2"""))
        assertEquals(entry, CanonicalJson.decodeEntry(encoded))
    }

    @Test
    fun everyFixtureEntryAndPlaceLabelDecodesAndReEncodesLosslessly() {
        val files = listOf("patterns", "places").flatMap { dir ->
            File(TestData.fixtures, dir).listFiles().orEmpty().filter { it.name.endsWith(".input.json") }
        }
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val obj = TestData.json(file).jsonObject
            obj["labels"]?.jsonArray?.forEach { elem ->
                val label = CanonicalJson.decodePlaceLabel(elem.toString())
                val roundTrip = Json.parseToJsonElement(CanonicalJson.encodePlaceLabel(label))
                assertEquals("${file.name} label ${label.id}", elem, roundTrip)
            }
            obj.getValue("entries").jsonArray.forEach { elem ->
                val entry = CanonicalJson.decodeEntry(elem.toString())
                val roundTrip = Json.parseToJsonElement(CanonicalJson.encodeEntry(entry))
                assertEquals("${file.name} entry ${entry.id}", elem, roundTrip)
            }
        }
    }

    @Test
    fun daypartBoundariesAndEntryOwnTimeZoneHandleDstAndTravel() {
        val baseId = "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10"
        fun entryAt(isoUtc: String, zone: String) = Entry(
            id = baseId,
            createdAt = Instant.parse(isoUtc),
            updatedAt = Instant.parse(isoUtc),
            timeZoneID = zone,
            mood = Mood.OKAY,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )

        // Daypart hour boundaries in UTC
        assertEquals(Daypart.NIGHT, entryAt("2026-10-12T04:59:59.999Z", "UTC").daypart())
        assertEquals(Daypart.MORNING, entryAt("2026-10-12T05:00:00.000Z", "UTC").daypart())
        assertEquals(Daypart.MORNING, entryAt("2026-10-12T11:59:59.999Z", "UTC").daypart())
        assertEquals(Daypart.AFTERNOON, entryAt("2026-10-12T12:00:00.000Z", "UTC").daypart())
        assertEquals(Daypart.AFTERNOON, entryAt("2026-10-12T16:59:59.999Z", "UTC").daypart())
        assertEquals(Daypart.EVENING, entryAt("2026-10-12T17:00:00.000Z", "UTC").daypart())
        assertEquals(Daypart.EVENING, entryAt("2026-10-12T21:59:59.999Z", "UTC").daypart())
        assertEquals(Daypart.NIGHT, entryAt("2026-10-12T22:00:00.000Z", "UTC").daypart())

        // Europe/Rome DST fall-back on 2026-10-25: 10:30Z is 12:30 CEST on 24 Oct, 11:30 CET on 25 Oct
        assertEquals(Daypart.AFTERNOON, entryAt("2026-10-24T10:30:00.000Z", "Europe/Rome").daypart())
        assertEquals(Daypart.MORNING, entryAt("2026-10-25T10:30:00.000Z", "Europe/Rome").daypart())
        assertEquals(7, entryAt("2026-10-25T10:30:00.000Z", "Europe/Rome").isoWeekday())

        // Travel: 2026-10-15T00:30Z is Wed 14 Oct 20:30 EDT in America/New_York (ISO weekday 3, evening)
        val nyEvening = entryAt("2026-10-15T00:30:00.000Z", "America/New_York")
        assertEquals(LocalDate.of(2026, 10, 14), nyEvening.localDate())
        assertEquals(3, nyEvening.isoWeekday())
        assertEquals(Daypart.EVENING, nyEvening.daypart())
    }

    @Test
    fun commentNormalizationAndValidationRulesHold() {
        assertNull(Entry.normalizeComment(null))
        assertNull(Entry.normalizeComment("   \n\t "))
        assertEquals("Quiet walk", Entry.normalizeComment("  Quiet walk \n"))
        val max = "a".repeat(1000)
        assertEquals(1000, Entry.normalizeComment(max)?.length)

        assertThrows(IllegalArgumentException::class.java) {
            Entry.normalizeComment("a".repeat(1001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Entry(
                id = "UPPERCASE-7d1a-4a52-9a8e-0c6f1d2e4b10",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                timeZoneID = "UTC",
                mood = Mood.LOW,
                tags = emptyList(),
                comment = null,
                location = null,
                source = EntrySource.APP,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Entry(
                id = "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                timeZoneID = "UTC",
                mood = Mood.LOW,
                tags = listOf("happy", "calm", "sad", "tired"),
                comment = null,
                location = null,
                source = EntrySource.APP,
            )
        }
        assertThrows(SerializationException::class.java) {
            IsoInstantSerializer.parseOrThrow("2026-10-12T13:05:00Z") // missing .000
        }
    }

    @Test
    fun hasCoreMatchesBothCoreAndFineTagsWithoutPrefixCollision() {
        val entry = Entry(
            id = "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            timeZoneID = "UTC",
            mood = Mood.LOW,
            tags = listOf("anxious.stressed", "tired"),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
        assertTrue(entry.hasCore("anxious"))
        assertTrue(entry.hasCore("tired"))
        assertFalse(entry.hasCore("anx"))
        assertFalse(entry.hasCore("sad"))
    }

    private fun <T : Throwable> assertThrows(expected: Class<T>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (expected.isInstance(t)) return
            throw AssertionError("Expected ${expected.simpleName}, got ${t::class.simpleName}: ${t.message}", t)
        }
        throw AssertionError("Expected ${expected.simpleName}, nothing thrown")
    }
}
