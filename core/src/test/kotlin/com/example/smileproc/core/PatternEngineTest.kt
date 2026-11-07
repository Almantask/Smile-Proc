package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class PatternEngineTest {

    private val config: PatternConfig =
        PatternConfig.fromJson(File(TestData.content, "pattern-config.json").readText(Charsets.UTF_8))
    private val emotionTags: EmotionTags =
        EmotionTags.fromJson(File(TestData.content, "emotion-tags.json").readText(Charsets.UTF_8))
    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    @Test
    fun weekdayDetectorsAreSkippedUntilFirstEntryReachesWeekdayMinDataDaysBeforePeriodEnd() {
        val now = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, rome).toInstant()
        val period = CardPeriod.resolve(now, rome)
        // 4 Low Mondays across 2 Mondays (5 Oct and 12 Oct), 10 Good other days.
        // First entry is Mon 5 Oct 09:00 = 13.4 days before Sun 18 Oct 19:00 (< weekdayMinDataDays 14).
        val baseEntries = buildList {
            add(entry(1, 2026, 10, 5, 9, Mood.LOW))
            add(entry(2, 2026, 10, 5, 15, Mood.LOW))
            add(entry(3, 2026, 10, 12, 9, Mood.LOW))
            add(entry(4, 2026, 10, 12, 15, Mood.LOW))
            var idx = 10
            for (d in 13..17) {
                add(entry(idx++, 2026, 10, d, 9, Mood.GOOD))
                add(entry(idx++, 2026, 10, d, 15, Mood.GOOD))
            }
        }

        val skipped = PatternEngine.analyze(baseEntries, emptyList(), period, config, emotionTags)
        assertFalse(
            "Weekday detectors must be skipped when first entry is < 14 days before periodEnd",
            skipped.candidates.any { it.family == Family.WEEKDAY },
        )

        // Adding a single history entry 20 days before periodEnd activates weekday detectors.
        val withHistory = baseEntries + entry(99, 2026, 9, 28, 12, Mood.OKAY)
        val active = PatternEngine.analyze(withHistory, emptyList(), period, config, emotionTags)
        assertTrue(
            "weekday.lower.1 should fire once history >= weekdayMinDataDays",
            active.candidates.any { it.id == "weekday.lower.1" && it.confidence == Confidence.PATTERN },
        )
    }

    @Test
    fun weekdayMinDistinctDatesRejectsFourCheckInsAllOnSameDate() {
        val now = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, rome).toInstant()
        val period = CardPeriod.resolve(now, rome)
        // History 30 days earlier, then 4 Low entries ALL on a single Monday (12 Oct) -> 1 distinct date < 2.
        val singleDateEntries = buildList {
            add(entry(1, 2026, 9, 15, 10, Mood.OKAY))
            add(entry(2, 2026, 10, 12, 8, Mood.LOW))
            add(entry(3, 2026, 10, 12, 10, Mood.LOW))
            add(entry(4, 2026, 10, 12, 13, Mood.LOW))
            add(entry(5, 2026, 10, 12, 16, Mood.LOW))
            var idx = 10
            for (d in 13..17) {
                add(entry(idx++, 2026, 10, d, 9, Mood.GOOD))
                add(entry(idx++, 2026, 10, d, 15, Mood.GOOD))
            }
        }
        val singleDateResult = PatternEngine.analyze(singleDateEntries, emptyList(), period, config, emotionTags)
        assertFalse(singleDateResult.candidates.any { it.id == "weekday.lower.1" })

        // Spread the 4 Monday Low entries across 2 distinct Mondays (5 Oct and 12 Oct) -> fires.
        val twoDatesEntries = singleDateEntries.map { e ->
            if (e.id.startsWith("00000002") || e.id.startsWith("00000003")) {
                val earlier = ZonedDateTime.of(2026, 10, 5, e.zonedDateTime().hour, 0, 0, 0, rome).toInstant()
                e.copy(createdAt = earlier, updatedAt = earlier)
            } else {
                e
            }
        }
        val twoDatesResult = PatternEngine.analyze(twoDatesEntries, emptyList(), period, config, emotionTags)
        assertTrue(twoDatesResult.candidates.any { it.id == "weekday.lower.1" })
    }

    @Test
    fun weekdayDaypartExcludesNightSegmentAndSortingRoundsScoreToNineDecimals() {
        val now = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, rome).toInstant()
        val period = CardPeriod.resolve(now, rome)
        // 4 Low Friday nights (23:00) across 4 Fridays -> weekday.lower.5 can fire, but weekdayDaypart.lower.5.night must NOT exist.
        val entries = buildList {
            add(entry(1, 2026, 9, 1, 12, Mood.OKAY))
            add(entry(2, 2026, 9, 25, 23, Mood.LOW))
            add(entry(3, 2026, 10, 2, 23, Mood.LOW))
            add(entry(4, 2026, 10, 9, 23, Mood.LOW))
            add(entry(5, 2026, 10, 16, 23, Mood.LOW))
            var idx = 10
            for (d in 12..18) {
                if (d != 16) {
                    add(entry(idx++, 2026, 10, d, 10, Mood.GOOD))
                    add(entry(idx++, 2026, 10, d, 15, Mood.GOOD))
                }
            }
        }
        val result = PatternEngine.analyze(entries, emptyList(), period, config, emotionTags)
        assertFalse(result.candidates.any { it.id.startsWith("weekdayDaypart.") && it.id.endsWith(".night") })
        assertTrue(result.candidates.any { it.id == "weekday.lower.5" })

        // Score rounding to 9 decimals: a difference at the 12th decimal is treated as a tie and sorted by id ascending.
        val c1 = DetectedPattern(
            id = "b.id",
            detector = Detector.TIME_OF_DAY,
            family = Family.TIME,
            direction = Direction.LOWER,
            params = PatternParams(segment = "afternoon"),
            nSegment = 5,
            nRest = 5,
            meanSegment = 1.0,
            meanRest = 2.0,
            diff = -1.0,
            z = -3.0,
            score = 0.700000000004,
            confidence = Confidence.PATTERN,
            supportingEntryIDs = emptyList(),
        )
        val c2 = c1.copy(id = "a.id", score = 0.700000000001)
        val sorted = listOf(c1, c2).sortedWith(PatternEngine.CANDIDATE_COMPARATOR)
        assertEquals(listOf("a.id", "b.id"), sorted.map { it.id })
    }

    @Test
    fun weekdayZPatternDemotesBorderlineTwoSigmaWeekdayCandidateToEarlyHint() {
        val now = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, rome).toInstant()
        val period = CardPeriod.resolve(now, rome)
        // History entry before the 28-day window so weekday detectors are active (>= 14 days).
        // Within the 28-day window [20 Sep 19:00, 18 Oct 19:00):
        // - 4 Tuesdays (22 Sep, 29 Sep, 6 Oct, 13 Oct) with moods [LOW, LOW, OKAY, OKAY] -> mean = 1.5
        // - 16 non-Tuesday entries: 2 LOW, 6 OKAY, 8 GOOD -> mean = 2.375, diff = -0.875, z = -2.245
        val nonTuesdayDates = listOf(
            2026 to (9 to 23), 2026 to (9 to 24), 2026 to (9 to 25), 2026 to (9 to 26),
            2026 to (9 to 30), 2026 to (10 to 1), 2026 to (10 to 2), 2026 to (10 to 3),
            2026 to (10 to 7), 2026 to (10 to 8), 2026 to (10 to 9), 2026 to (10 to 10),
            2026 to (10 to 14), 2026 to (10 to 15), 2026 to (10 to 16), 2026 to (10 to 17),
        )
        val nonTuesdayMoods = List(2) { Mood.LOW } + List(6) { Mood.OKAY } + List(8) { Mood.GOOD }
        val entries = buildList {
            add(entry(1, 2026, 9, 1, 12, Mood.OKAY))
            add(entry(2, 2026, 9, 22, 10, Mood.LOW))
            add(entry(3, 2026, 9, 29, 10, Mood.LOW))
            add(entry(4, 2026, 10, 6, 10, Mood.OKAY))
            add(entry(5, 2026, 10, 13, 10, Mood.OKAY))
            nonTuesdayDates.forEachIndexed { i, (y, md) ->
                add(entry(10 + i, y, md.first, md.second, 10, nonTuesdayMoods[i]))
            }
        }
        val strict = PatternEngine.analyze(entries, emptyList(), period, config, emotionTags)
            .candidates.first { it.id == "weekday.lower.2" }
        assertTrue("Expected 2.0 <= |z| < 2.5, got z=${strict.z}", kotlin.math.abs(strict.z!!) in 2.0..2.49)
        assertEquals(Confidence.EARLY_HINT, strict.confidence)

        val relaxed = PatternEngine.analyze(
            entries,
            emptyList(),
            period,
            config.copy(weekdayZPattern = 2.0),
            emotionTags,
        ).candidates.first { it.id == "weekday.lower.2" }
        assertEquals(Confidence.PATTERN, relaxed.confidence)
    }

    private fun entry(num: Int, y: Int, m: Int, d: Int, h: Int, mood: Mood): Entry {
        val hex = num.toString().padStart(8, '0')
        val ts: Instant = ZonedDateTime.of(y, m, d, h, 0, 0, 0, rome).toInstant()
        return Entry(
            id = "$hex-0000-4000-8000-000000000000",
            createdAt = ts,
            updatedAt = ts,
            timeZoneID = "Europe/Rome",
            mood = mood,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
    }
}
