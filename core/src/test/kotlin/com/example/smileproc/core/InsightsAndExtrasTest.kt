package com.example.smileproc.core

import com.example.smileproc.core.export.ExportAppInfo
import com.example.smileproc.core.export.ExportSettings
import com.example.smileproc.core.export.ExtendedExportPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class InsightsAndExtrasTest {

    private fun loadEmotionTags(): EmotionTags {
        val candidates = listOf(
            File("content/emotion-tags.json"),
            File("../content/emotion-tags.json"),
        )
        val file = candidates.first { it.isFile }
        return EmotionTags.fromJson(file.readText())
    }

    private fun loadPatternConfig(): PatternConfig {
        val candidates = listOf(
            File("content/pattern-config.json"),
            File("../content/pattern-config.json"),
        )
        val file = candidates.first { it.isFile }
        return PatternConfig.fromJson(file.readText())
    }

    private fun makeEntry(
        id: String = UUID.randomUUID().toString().lowercase(),
        isoInstant: String,
        zoneId: String = "Europe/Vilnius",
        mood: Mood,
        tags: List<String> = emptyList(),
        comment: String? = null,
        location: LocationSample? = null,
    ): Entry {
        val t = Instant.parse(isoInstant)
        return Entry(
            id = id,
            createdAt = t,
            updatedAt = t,
            timeZoneID = zoneId,
            mood = mood,
            tags = tags,
            comment = comment,
            location = location,
            source = EntrySource.APP,
        )
    }

    @Test
    fun entryExtras_normalizationAndSerialization_preservesValues() {
        val now = Instant.parse("2026-10-10T10:00:00Z")
        val raw = EntryExtras(
            entryId = "11111111-1111-4111-8111-111111111111",
            contextTags = listOf("ctx.sleep", "ctx.sleep", "invalid.tag", " ctx.work "),
            customTagIds = listOf("custom.1", "custom.1", " ", "custom.2"),
            bookmarked = true,
            updatedAt = now,
        ).normalized()

        assertEquals(listOf("ctx.sleep", "ctx.work"), raw.contextTags)
        assertEquals(listOf("custom.1", "custom.2"), raw.customTagIds)
        assertTrue(raw.bookmarked)

        val encoded = EntryExtras.encode(raw)
        val decoded = EntryExtras.decode(encoded)
        assertEquals(raw, decoded)
    }

    @Test
    fun customTag_normalizesWhitespaceAndTruncatesAtMaxLength() {
        val tag = CustomTag.create(
            rawLabel = "   Morning    walk   in   the   forest   today   ",
            createdAt = Instant.parse("2026-10-10T10:00:00Z"),
        )
        assertTrue(tag.id.startsWith(CustomTag.ID_PREFIX))
        assertEquals(CustomTag.MAX_LABEL_LENGTH, tag.label.length)
        assertFalse(tag.label.contains("  "))

        val roundTrip = EntryExtras.decodeCustomTag(EntryExtras.encodeCustomTag(tag))
        assertEquals(tag, roundTrip)
    }

    @Test
    fun buildMonthlyRhythm_groupsDatesUsingEachEntryTimeZone() {
        // 2026-10-10T22:30:00Z is 2026-10-11 01:30 AM in Europe/Vilnius (UTC+3), NIGHT daypart.
        val e1 = makeEntry(
            isoInstant = "2026-10-10T22:30:00Z",
            zoneId = "Europe/Vilnius",
            mood = Mood.LOW,
        )
        // 2026-10-11T07:00:00Z is 10:00 AM in Europe/Vilnius, MORNING daypart.
        val e2 = makeEntry(
            isoInstant = "2026-10-11T07:00:00Z",
            zoneId = "Europe/Vilnius",
            mood = Mood.GOOD,
        )
        // Entry outside 30-day window should be excluded
        val tooOld = makeEntry(
            isoInstant = "2026-08-01T10:00:00Z",
            zoneId = "Europe/Vilnius",
            mood = Mood.OKAY,
        )

        val report = InsightsEngine.buildMonthlyRhythm(
            entries = listOf(e1, e2, tooOld),
            days = 30,
            today = LocalDate.of(2026, 10, 11),
        )

        assertEquals(30, report.daySummaries.size)
        assertEquals(2, report.totalEntries)
        assertEquals(1, report.activeDays)

        val oct11 = report.daySummaries.last()
        assertEquals(LocalDate.of(2026, 10, 11), oct11.date)
        assertEquals(2, oct11.total)
        assertEquals(1, oct11.lowCount)
        assertEquals(0, oct11.okayCount)
        assertEquals(1, oct11.goodCount)

        val morning = report.byDaypart.first { it.daypart == Daypart.MORNING }
        assertEquals(1, morning.goodCount)
        val night = report.byDaypart.first { it.daypart == Daypart.NIGHT }
        assertEquals(1, night.lowCount)
    }

    @Test
    fun buildBreakdowns_aggregatesEmotionsContextCustomAndPlaces() {
        val emotionTags = loadEmotionTags()
        val config = loadPatternConfig()
        val t1 = Instant.parse("2026-10-10T09:00:00Z")
        val locHome = LocationSample(54.6872, 25.2797, 15.0, t1, LocationKind.FRESH)
        val homeLabel = PlaceLabel(
            id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 54.6872,
            anchorLongitude = 25.2797,
            createdAt = t1,
        )

        val e1 = makeEntry(
            id = "00000001-0000-4000-8000-000000000001",
            isoInstant = "2026-10-10T09:00:00Z",
            mood = Mood.GOOD,
            tags = listOf("calm.peaceful", "happy"),
            location = locHome,
        )
        val e2 = makeEntry(
            id = "00000002-0000-4000-8000-000000000002",
            isoInstant = "2026-10-11T15:00:00Z",
            mood = Mood.LOW,
            tags = listOf("anxious.tense"),
            location = locHome,
        )

        val custom = CustomTag("custom.coffee", "Espresso", t1)
        val extras = mapOf(
            e1.id to EntryExtras(
                entryId = e1.id,
                contextTags = listOf("ctx.outdoors"),
                customTagIds = listOf(custom.id),
                bookmarked = true,
                updatedAt = t1,
            ),
            e2.id to EntryExtras(
                entryId = e2.id,
                contextTags = listOf("ctx.work", "ctx.sleep"),
                customTagIds = listOf("custom.deleted"),
                bookmarked = false,
                updatedAt = t1,
            ),
        )

        val assignment = PlaceResolver.resolve(listOf(e1, e2), listOf(homeLabel), config)
        val report = InsightsEngine.buildBreakdowns(
            entries = listOf(e1, e2),
            extrasByEntryId = extras,
            customTags = listOf(custom),
            placeAssignment = assignment,
            emotionTags = emotionTags,
        )

        val peaceful = report.emotionTags.first { it.tagId == "calm.peaceful" }
        assertEquals("calm", peaceful.coreId)
        assertEquals(1, peaceful.goodCount)

        // Deleted custom tag is safely excluded
        assertTrue(report.contextAndCustomTags.none { it.tagId == "custom.deleted" })
        assertTrue(report.contextAndCustomTags.any { it.tagId == custom.id && it.goodCount == 1 })

        val lowMood = report.byMood[Mood.LOW]
        assertNotNull(lowMood)
        assertTrue(lowMood!!.topTags.any { it.tagId == "anxious.tense" || it.tagId == "ctx.work" })

        assertEquals(1, report.places.size)
        assertEquals("Home", report.places.first().label?.name)
        assertEquals(2, report.places.first().total)
        assertEquals(1, report.places.first().goodCount)
        assertEquals(1, report.places.first().lowCount)
    }

    @Test
    fun filterEntries_matchesQueryBookmarksNotesCoresContextAndPlace() {
        val emotionTags = loadEmotionTags()
        val config = loadPatternConfig()
        val t1 = Instant.parse("2026-10-10T09:00:00Z")
        val homeLabel = PlaceLabel(
            id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            name = "Quiet Studio",
            kind = PlaceKind.WORK,
            anchorLatitude = 54.6872,
            anchorLongitude = 25.2797,
            createdAt = t1,
        )
        val e1 = makeEntry(
            id = "00000001-0000-4000-8000-000000000001",
            isoInstant = "2026-10-10T09:00:00Z",
            mood = Mood.GOOD,
            tags = listOf("calm.peaceful"),
            comment = "Sunlight by the window",
            location = LocationSample(54.6872, 25.2797, 10.0, t1, LocationKind.FRESH),
        )
        val e2 = makeEntry(
            id = "00000002-0000-4000-8000-000000000002",
            isoInstant = "2026-10-11T09:00:00Z",
            mood = Mood.LOW,
            tags = listOf("tired.drained"),
            comment = null,
        )

        val custom = CustomTag("custom.tea", "Herbal Tea", t1)
        val extras = mapOf(
            e1.id to EntryExtras(
                entryId = e1.id,
                contextTags = listOf("ctx.rest"),
                customTagIds = listOf(custom.id),
                bookmarked = true,
                updatedAt = t1,
            ),
        )
        val places = PlaceResolver.resolve(listOf(e1, e2), listOf(homeLabel), config)
        val resolver: (String) -> String = { id ->
            when (id) {
                "calm.peaceful" -> "Peaceful"
                "calm" -> "Calm"
                "ctx.rest" -> "Rest"
                "tired.drained" -> "Drained"
                "tired" -> "Tired"
                else -> id
            }
        }

        // Bookmark + note filter
        val bookmarked = InsightsEngine.filterEntries(
            entries = listOf(e1, e2),
            filter = HistoryFilter(bookmarkedOnly = true, withNoteOnly = true),
            extrasByEntryId = extras,
            customTagsById = mapOf(custom.id to custom),
            placeAssignment = places,
            emotionTags = emotionTags,
            tagLabelResolver = resolver,
        )
        assertEquals(listOf(e1), bookmarked)

        // Multi-token search combining place label & custom tag
        val searchMatch = InsightsEngine.filterEntries(
            entries = listOf(e1, e2),
            filter = HistoryFilter(query = "studio herbal"),
            extrasByEntryId = extras,
            customTagsById = mapOf(custom.id to custom),
            placeAssignment = places,
            emotionTags = emotionTags,
            tagLabelResolver = resolver,
        )
        assertEquals(listOf(e1), searchMatch)

        // Emotion core filter matches sub-tag under that core
        val coreMatch = InsightsEngine.filterEntries(
            entries = listOf(e1, e2),
            filter = HistoryFilter(coreTagIds = setOf("tired")),
            extrasByEntryId = extras,
            customTagsById = mapOf(custom.id to custom),
            placeAssignment = places,
            emotionTags = emotionTags,
            tagLabelResolver = resolver,
        )
        assertEquals(listOf(e2), coreMatch)
    }

    @Test
    fun extendedExportPayload_decodesBothV1AndExtendedJson() {
        val vectorFile = listOf(
            File("fixtures/export/vector-1.json"),
            File("../fixtures/export/vector-1.json"),
        ).first { it.isFile }
        // Confirm v1 canonical ExportPayload sample decodes as ExtendedExportPayload seamlessly
        val t = Instant.parse("2026-10-10T10:00:00Z")
        val basePayloadJson = """
            {
              "exportedAt": "2026-10-10T10:00:00Z",
              "app": {"platform": "android", "version": "1.0"},
              "entries": [],
              "places": [],
              "settings": {
                "reminders": [],
                "keepCheckIn": false,
                "weeklyCard": {"weekday": 7, "hour": 19, "minute": 0},
                "tagPickerMode": "gradual",
                "captureLocation": true
              }
            }
        """.trimIndent()
        val decodedFromV1 = ExtendedExportPayload.decodeFromString(basePayloadJson)
        assertTrue(decodedFromV1.entryExtras.isEmpty())
        assertTrue(decodedFromV1.customTags.isEmpty())
        assertTrue(decodedFromV1.reminderWindows.isEmpty())
        assertEquals(ReminderPause.NONE, decodedFromV1.reminderPause)
        assertTrue(vectorFile.readText().isNotEmpty())

        val extended = ExtendedExportPayload(
            exportedAt = t.toString(),
            app = ExportAppInfo(version = "1.0"),
            entries = emptyList(),
            places = emptyList(),
            settings = ExportSettings(
                reminders = emptyList(),
                keepCheckIn = true,
                weeklyCard = WeeklyCardTime.DEFAULT,
                tagPickerMode = "gradual",
                captureLocation = false,
            ),
            entryExtras = listOf(
                EntryExtras(
                    entryId = "11111111-1111-4111-8111-111111111111",
                    contextTags = listOf("ctx.sleep"),
                    customTagIds = listOf("custom.1"),
                    bookmarked = true,
                    updatedAt = t,
                ),
            ),
            customTags = listOf(CustomTag("custom.1", "Walk", t)),
            reminderWindows = mapOf("r1" to 30),
            reminderPause = ReminderPause.forDays(t, 3L, ZoneId.of("UTC")),
        )
        val roundTrip = ExtendedExportPayload.decodeFromString(
            ExtendedExportPayload.encodeToString(extended),
        )
        assertEquals(extended, roundTrip)
    }
}
