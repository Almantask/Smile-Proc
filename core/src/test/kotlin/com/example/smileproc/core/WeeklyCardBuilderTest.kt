package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

class WeeklyCardBuilderTest {

    private val config: PatternConfig =
        PatternConfig.fromJson(File(TestData.content, "pattern-config.json").readText(Charsets.UTF_8))

    @Test
    fun balanceSwapReplacesLastPickRespectingMaxOnePerFamily() {
        val tagPat = candidate("tagOnLow.tired", Detector.TAG_ON_LOW, null, 1.5, Confidence.PATTERN, PatternParams(tag = "tired"))
        val timeLower = candidate("timeOfDay.lower.afternoon", Detector.TIME_OF_DAY, Direction.LOWER, 1.4, Confidence.PATTERN, PatternParams(segment = "afternoon"))
        val weekdayLower = candidate("weekday.lower.1", Detector.WEEKDAY, Direction.LOWER, 1.2, Confidence.PATTERN, PatternParams(weekday = "1"))
        val placeHigher = candidate(
            "place.higher.u:park",
            Detector.PLACE,
            Direction.HIGHER,
            0.9,
            Confidence.PATTERN,
            PatternParams(clusterID = ClusterID.unnamed("park")),
        )

        // Greedy picks [tagOnLow.tired, timeOfDay.lower.afternoon, weekday.lower.1] (none higher).
        // Balance rule replaces last pick (weekday.lower.1) with place.higher.u:park (unpicked family PLACE).
        val swappedUnpicked = WeeklyCardBuilder.selectCandidates(
            listOf(tagPat, timeLower, weekdayLower, placeHigher),
            limit = 3,
        )
        assertEquals(
            listOf("tagOnLow.tired", "timeOfDay.lower.afternoon", "place.higher.u:park"),
            swappedUnpicked.map { it.id },
        )

        // When the higher pattern is in the SAME family as the dropped last pick (PLACE),
        // replacing the last pick still leaves at most one per family on the final card.
        val placeLower = candidate(
            "place.lower.u:work",
            Detector.PLACE,
            Direction.LOWER,
            1.1,
            Confidence.PATTERN,
            PatternParams(clusterID = ClusterID.unnamed("work")),
        )
        val swappedDroppedFamily = WeeklyCardBuilder.selectCandidates(
            listOf(timeLower, placeLower, placeHigher),
            limit = 2,
        )
        assertEquals(
            listOf("timeOfDay.lower.afternoon", "place.higher.u:park"),
            swappedDroppedFamily.map { it.id },
        )

        // When the ONLY higher pattern belongs to an EARLIER kept pick's family (TIME),
        // it must NOT replace the last pick (which would put two TIME items on the card).
        val timeHigher = candidate("timeOfDay.higher.morning", Detector.TIME_OF_DAY, Direction.HIGHER, 1.0, Confidence.PATTERN, PatternParams(segment = "morning"))
        val blockedDuplicateFamily = WeeklyCardBuilder.selectCandidates(
            listOf(timeLower, placeLower, timeHigher),
            limit = 2,
        )
        assertEquals(
            listOf("timeOfDay.lower.afternoon", "place.lower.u:work"),
            blockedDuplicateFamily.map { it.id },
        )

        // Early hints with direction HIGHER do NOT trigger the balance swap (must have pattern confidence).
        val placeHigherHint = placeHigher.copy(confidence = Confidence.EARLY_HINT, score = 0.6)
        val hintNotSwapped = WeeklyCardBuilder.selectCandidates(
            listOf(tagPat, timeLower, weekdayLower, placeHigherHint),
            limit = 3,
        )
        assertEquals(
            listOf("tagOnLow.tired", "timeOfDay.lower.afternoon", "weekday.lower.1"),
            hintNotSwapped.map { it.id },
        )
    }

    @Test
    fun earlyHintsFillRemainingSlotsWithoutDisplacingPatternsAndKeepOnePerFamily() {
        val timePat = candidate("timeOfDay.lower.afternoon", Detector.TIME_OF_DAY, Direction.LOWER, 1.4, Confidence.PATTERN, PatternParams(segment = "afternoon"))
        val sameFamilyHint = candidate("timeOfDay.higher.morning", Detector.TIME_OF_DAY, Direction.HIGHER, 0.9, Confidence.EARLY_HINT, PatternParams(segment = "morning"))
        val weekdayHint = candidate("weekday.lower.3", Detector.WEEKDAY, Direction.LOWER, 0.7, Confidence.EARLY_HINT, PatternParams(weekday = "3"))

        val selected = WeeklyCardBuilder.selectCandidates(
            listOf(timePat, sameFamilyHint, weekdayHint),
            limit = 3,
        )
        assertEquals(listOf("timeOfDay.lower.afternoon", "weekday.lower.3"), selected.map { it.id })
    }

    @Test
    fun firstCardTimingRequiresFirstCardMinEntriesAndSkipsAlreadyBuiltPeriod() {
        val rome = ZoneId.of("Europe/Rome")
        val now = Instant.parse("2026-10-18T18:00:00.000Z") // Sun 18 Oct 20:00 CEST, after 19:00 card time
        val entries4 = (1..4).map { i ->
            Entry(
                id = "0000000$i-0000-4000-8000-000000000000",
                createdAt = Instant.parse("2026-10-1${i + 1}T10:00:00.000Z"),
                updatedAt = Instant.parse("2026-10-1${i + 1}T10:00:00.000Z"),
                timeZoneID = "Europe/Rome",
                mood = Mood.OKAY,
                tags = emptyList(),
                comment = null,
                location = null,
                source = EntrySource.APP,
            )
        }
        // 4 < firstCardMinEntries (5) -> not due yet
        assertNull(WeeklyCardBuilder.dueCardPeriod(entries4, now, rome, emptySet(), config))

        val entries5 = entries4 + Entry(
            id = "00000005-0000-4000-8000-000000000000",
            createdAt = Instant.parse("2026-10-16T10:00:00.000Z"),
            updatedAt = Instant.parse("2026-10-16T10:00:00.000Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.OKAY,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
        val due = WeeklyCardBuilder.dueCardPeriod(entries5, now, rome, emptySet(), config)
        assertNotNull(due)
        // Once stored for periodStart, opening the app again does not rebuild
        assertNull(WeeklyCardBuilder.dueCardPeriod(entries5, now, rome, setOf(due!!.periodStart), config))
    }

    private fun candidate(
        id: String,
        detector: Detector,
        direction: Direction?,
        score: Double,
        confidence: Confidence,
        params: PatternParams,
    ) = DetectedPattern(
        id = id,
        detector = detector,
        family = detector.family,
        direction = direction,
        params = params,
        nSegment = 5,
        nRest = 5,
        meanSegment = if (direction != null) 2.0 else null,
        meanRest = if (direction != null) 2.0 else null,
        diff = if (direction != null) -1.0 else null,
        z = if (direction != null) -3.0 else null,
        score = score,
        confidence = confidence,
        supportingEntryIDs = emptyList(),
    )
}
