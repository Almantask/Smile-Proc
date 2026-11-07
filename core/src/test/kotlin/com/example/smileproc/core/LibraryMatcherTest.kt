package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class LibraryMatcherTest {

    private val library: PatternLibrary =
        PatternLibrary.fromJson(File(TestData.content, "pattern-library.en.json").readText(Charsets.UTF_8))
    private val emotionTags: EmotionTags =
        EmotionTags.fromJson(File(TestData.content, "emotion-tags.json").readText(Charsets.UTF_8))

    @Test
    fun everyValidDetectorDirectionAndParameterCombinationMatchesAnEntry() {
        val directions = Direction.entries
        val allDayparts = Daypart.entries.map { it.wireName }
        val weekdayDayparts = listOf("morning", "afternoon", "evening")
        val weekdays = (1..7).map { it.toString() }
        val placeKinds: List<PlaceKind?> = listOf(null) + PlaceKind.entries
        val allTags: List<String> = emotionTags.allTagIds.toList()
        val dummyCluster = ClusterID.unnamed("00000001-0000-4000-8000-000000000001")

        // 1. timeOfDay × direction × segment
        for (dir in directions) {
            for (seg in allDayparts) {
                val p = dummyPattern(Detector.TIME_OF_DAY, dir, PatternParams(segment = seg))
                assertNotNull("Unmatched $p", LibraryMatcher.match(p, library))
            }
        }

        // 2. weekday × direction × weekday
        for (dir in directions) {
            for (wd in weekdays) {
                val p = dummyPattern(Detector.WEEKDAY, dir, PatternParams(weekday = wd))
                assertNotNull("Unmatched $p", LibraryMatcher.match(p, library))
            }
        }

        // 3. weekdayDaypart × direction × weekday × segment
        for (dir in directions) {
            for (wd in weekdays) {
                for (seg in weekdayDayparts) {
                    val p = dummyPattern(Detector.WEEKDAY_DAYPART, dir, PatternParams(segment = seg, weekday = wd))
                    assertNotNull("Unmatched $p", LibraryMatcher.match(p, library))
                }
            }
        }

        // 4. place × direction × placeKind (including unnamed null)
        for (dir in directions) {
            for (kind in placeKinds) {
                val p = dummyPattern(Detector.PLACE, dir, PatternParams(clusterID = dummyCluster, placeKind = kind))
                assertNotNull("Unmatched $p", LibraryMatcher.match(p, library))
            }
        }

        // 5. awayFromHome × direction
        for (dir in directions) {
            val p = dummyPattern(Detector.AWAY_FROM_HOME, dir, PatternParams())
            assertNotNull("Unmatched $p", LibraryMatcher.match(p, library))
        }

        // 6. tagOnLow × all cores and fine tags
        for (tag in allTags) {
            val p = dummyPattern(Detector.TAG_ON_LOW, null, PatternParams(tag = tag))
            assertNotNull("Unmatched $p", LibraryMatcher.match(p, library))
        }

        // 7. tagAtPlace × placeKind × all cores and fine tags
        for (kind in placeKinds) {
            for (tag in allTags) {
                val p = dummyPattern(
                    Detector.TAG_AT_PLACE,
                    null,
                    PatternParams(clusterID = dummyCluster, placeKind = kind, tag = tag),
                )
                assertNotNull("Unmatched $p", LibraryMatcher.match(p, library))
            }
        }
    }

    @Test
    fun specificityPrefersMostKeysAndTiesBreakByFileOrder() {
        // afternoon-dip (3 keys) beats time-lower (2 keys)
        val afternoonLower = dummyPattern(Detector.TIME_OF_DAY, Direction.LOWER, PatternParams(segment = "afternoon"))
        assertEquals("afternoon-dip", LibraryMatcher.matchOrThrow(afternoonLower, library).id)

        // sunday-evening (4 keys) beats weekday-segment-lower (2 keys)
        val sundayEvening = dummyPattern(
            Detector.WEEKDAY_DAYPART,
            Direction.LOWER,
            PatternParams(segment = "evening", weekday = "7"),
        )
        assertEquals("sunday-evening", LibraryMatcher.matchOrThrow(sundayEvening, library).id)

        // Core tag rule: a fine tag like tired.drained matches tired-on-low (tag = "tired", 2 keys) over tag-on-low (1 key)
        val tiredDrainedLow = dummyPattern(Detector.TAG_ON_LOW, null, PatternParams(tag = "tired.drained"))
        assertEquals("tired-on-low", LibraryMatcher.matchOrThrow(tiredDrainedLow, library).id)

        // File-order tie-breaking when two entries specify the same number of matching keys
        val customLib = PatternLibrary(
            listOf(
                LibraryEntry(
                    id = "first-tie",
                    match = LibraryMatchCriteria(detector = "timeOfDay", direction = "lower"),
                    title = "First",
                    common = "Common",
                    tip = "Tip",
                    question = "Question?",
                ),
                LibraryEntry(
                    id = "second-tie",
                    match = LibraryMatchCriteria(detector = "timeOfDay", segment = "morning"),
                    title = "Second",
                    common = "Common",
                    tip = "Tip",
                    question = "Question?",
                ),
            ),
        )
        val morningLower = dummyPattern(Detector.TIME_OF_DAY, Direction.LOWER, PatternParams(segment = "morning"))
        assertEquals("first-tie", LibraryMatcher.matchOrThrow(morningLower, customLib).id)
    }

    private fun dummyPattern(detector: Detector, direction: Direction?, params: PatternParams) = DetectedPattern(
        id = "test.${detector.wireName}",
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
        score = 0.8,
        confidence = Confidence.PATTERN,
        supportingEntryIDs = emptyList(),
    )
}
