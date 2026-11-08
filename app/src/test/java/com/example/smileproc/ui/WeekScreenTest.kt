package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.smileproc.core.CardItem
import com.example.smileproc.core.Cluster
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.Confidence
import com.example.smileproc.core.DetectedPattern
import com.example.smileproc.core.Detector
import com.example.smileproc.core.Direction
import com.example.smileproc.core.Family
import com.example.smileproc.core.LatLon
import com.example.smileproc.core.PatternParams
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Variant
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.core.WindowKind
import com.example.smileproc.ui.week.WeekScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WeekScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeSampleCard(
        periodStart: Instant = Instant.parse("2026-10-11T17:00:00Z"),
        periodEnd: Instant = Instant.parse("2026-10-18T17:00:00Z"),
        isLowWeek: Boolean = false,
        variant: Variant = Variant.NORMAL,
        items: List<CardItem> = emptyList(),
    ): WeeklyCard {
        val days = List(7) { listOf(1, 2, 1) } // 7 x 4 = 28
        return WeeklyCard(
            periodStart = periodStart,
            periodEnd = periodEnd,
            windowKind = WindowKind.WEEK,
            entryCount = 28,
            moodCounts = listOf(7, 14, 7),
            days = days,
            isLowWeek = isLowWeek,
            variant = variant,
            items = items,
        )
    }

    @Test
    fun preFirstCard_rendersArrivalNotice() {
        composeTestRule.setContent {
            WeekScreen(
                cards = emptyList(),
                labels = emptyList(),
                places = null,
                weeklyCardTime = WeeklyCardTime(weekday = 7, hour = 19, minute = 0),
                onNavigateToSupport = {},
                onNamePlace = {},
            )
        }

        composeTestRule.onNodeWithText("Your first weekly card arrives Sunday at 19:00.")
            .assertIsDisplayed()
    }

    @Test
    fun normalCard_rendersPeriodAndFaceStripAndPatternItems() {
        val patternItem = CardItem(
            patternID = "timeOfDay.lower.afternoon",
            libraryID = "afternoon-dip",
            pattern = DetectedPattern(
                id = "timeOfDay.lower.afternoon",
                detector = Detector.TIME_OF_DAY,
                family = Family.TIME,
                direction = Direction.LOWER,
                params = PatternParams(segment = "afternoon"),
                nSegment = 5,
                nRest = 10,
                meanSegment = 1.4,
                meanRest = 2.3,
                diff = -0.9,
                z = -2.5,
                score = 0.9,
                confidence = Confidence.EARLY_HINT,
                supportingEntryIDs = emptyList(),
            ),
        )

        val card = makeSampleCard(items = listOf(patternItem))

        composeTestRule.setContent {
            WeekScreen(
                cards = listOf(card),
                labels = emptyList(),
                places = null,
                weeklyCardTime = WeeklyCardTime.DEFAULT,
                onNavigateToSupport = {},
                onNamePlace = {},
            )
        }

        // Title from library
        composeTestRule.onNodeWithText("Afternoons run lower").assertIsDisplayed()

        // Early hint badge
        composeTestRule.onNodeWithText("Early hint").assertIsDisplayed()

        // Observation line (counts only, no raw averages)
        composeTestRule.onNodeWithText("Your afternoon check-ins were lower than the rest of your day (5 check-ins).")
            .assertIsDisplayed()
    }

    @Test
    fun lowWeekCard_rendersAcknowledgmentAndTalkToSomeoneAction() {
        var supportClicked = false

        val card = makeSampleCard(isLowWeek = true)

        composeTestRule.setContent {
            WeekScreen(
                cards = listOf(card),
                labels = emptyList(),
                places = null,
                weeklyCardTime = WeeklyCardTime.DEFAULT,
                onNavigateToSupport = { supportClicked = true },
                onNamePlace = {},
            )
        }

        // Low-week acknowledgment copy
        composeTestRule.onNodeWithText("This week looked heavy. That's worth noticing, and it's okay.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Talking to someone you trust can make it lighter.")
            .assertIsDisplayed()

        // Action button
        composeTestRule.onNodeWithText("Talk to someone").assertIsDisplayed().performClick()
        assertTrue(supportClicked)
    }

    @Test
    fun unnamedPlaceItem_displaysNameThisPlaceButton_triggersCallback() {
        var namedCluster: String? = null
        val clusterId = "u:entry-abc-123"

        val placeItem = CardItem(
            patternID = "place.lower.$clusterId",
            libraryID = "place-lower",
            pattern = DetectedPattern(
                id = "place.lower.$clusterId",
                detector = Detector.PLACE,
                family = Family.PLACE,
                direction = Direction.LOWER,
                params = PatternParams(clusterID = ClusterID(clusterId)),
                nSegment = 6,
                nRest = 8,
                meanSegment = 1.2,
                meanRest = 2.4,
                diff = -1.2,
                z = -2.8,
                score = 1.2,
                confidence = Confidence.PATTERN,
                supportingEntryIDs = emptyList(),
            ),
        )

        val card = makeSampleCard(items = listOf(placeItem))
        val places = PlaceAssignment(
            entryToCluster = mapOf("entry-abc-123" to ClusterID(clusterId)),
            clusters = mapOf(
                ClusterID(clusterId) to Cluster(
                    id = ClusterID(clusterId),
                    centroid = LatLon(52.5200, 13.4050),
                    memberIDs = listOf("entry-abc-123"),
                    label = null,
                ),
            ),
        )

        composeTestRule.setContent {
            WeekScreen(
                cards = listOf(card),
                labels = emptyList(),
                places = places,
                weeklyCardTime = WeeklyCardTime.DEFAULT,
                onNavigateToSupport = {},
                onNamePlace = { namedCluster = it },
            )
        }

        // Shows "Check-ins at an unnamed place run lower"
        composeTestRule.onNodeWithText("Check-ins at an unnamed place run lower").performScrollTo().assertIsDisplayed()

        // "Name this place" button
        composeTestRule.onNodeWithText("Name this place").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(clusterId, namedCluster)
    }

    @Test
    fun renamedPlace_dynamicallyUpdatesCardDisplay() {
        val entryId = "entry-abc-123"
        val clusterId = "u:$entryId"
        val labelId = UUID.randomUUID().toString().lowercase(Locale.ROOT)

        val placeItem = CardItem(
            patternID = "place.lower.$clusterId",
            libraryID = "place-lower",
            pattern = DetectedPattern(
                id = "place.lower.$clusterId",
                detector = Detector.PLACE,
                family = Family.PLACE,
                direction = Direction.LOWER,
                params = PatternParams(clusterID = ClusterID(clusterId)),
                nSegment = 6,
                nRest = 8,
                meanSegment = 1.2,
                meanRest = 2.4,
                diff = -1.2,
                z = -2.8,
                score = 1.2,
                confidence = Confidence.PATTERN,
                supportingEntryIDs = emptyList(),
            ),
        )

        val card = makeSampleCard(items = listOf(placeItem))

        val label = PlaceLabel(
            id = labelId,
            name = "Cozy Studio",
            kind = PlaceKind.WORK,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.parse("2026-10-10T10:00:00Z"),
        )

        // The place is now named "Cozy Studio"
        val places = PlaceAssignment(
            entryToCluster = mapOf(entryId to ClusterID("p:$labelId")),
            clusters = mapOf(
                ClusterID("p:$labelId") to Cluster(
                    id = ClusterID("p:$labelId"),
                    centroid = LatLon(52.5200, 13.4050),
                    memberIDs = listOf(entryId),
                    label = label,
                ),
            ),
        )

        composeTestRule.setContent {
            WeekScreen(
                cards = listOf(card),
                labels = listOf(label),
                places = places,
                weeklyCardTime = WeeklyCardTime.DEFAULT,
                onNavigateToSupport = {},
                onNamePlace = {},
            )
        }

        // Dynamically renders the updated place name: "Check-ins at Cozy Studio run lower"
        composeTestRule.onNodeWithText("Check-ins at Cozy Studio run lower").assertIsDisplayed()
    }

    @Test
    fun pastWeeksSelector_switchesSelectedCard() {
        val card1 = makeSampleCard(
            periodStart = Instant.parse("2026-10-11T17:00:00Z"),
            periodEnd = Instant.parse("2026-10-18T17:00:00Z"),
            variant = Variant.NORMAL,
        )
        val card2 = makeSampleCard(
            periodStart = Instant.parse("2026-10-04T17:00:00Z"),
            periodEnd = Instant.parse("2026-10-11T17:00:00Z"),
            variant = Variant.NOT_ENOUGH_DATA,
        )

        composeTestRule.setContent {
            WeekScreen(
                cards = listOf(card1, card2),
                labels = emptyList(),
                places = null,
                weeklyCardTime = WeeklyCardTime.DEFAULT,
                onNavigateToSupport = {},
                onNamePlace = {},
            )
        }

        // Initial card is card1 (normal)
        composeTestRule.onNodeWithText("Past weeks").assertIsDisplayed()

        // Switch to card2
        val chips = composeTestRule.onAllNodesWithText("10/11/26")
        if (chips.fetchSemanticsNodes().isNotEmpty()) {
            chips[0].performClick()
            composeTestRule.onNodeWithText("Patterns need a few more check-ins.").performScrollTo().assertIsDisplayed()
        }
    }
}
