package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.ui.week.WeekPlotMath
import com.example.smileproc.ui.week.WeekScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WeekScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testZone = ZoneId.of("UTC")

    private fun makeEntry(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
        mood: Mood = Mood.OKAY,
        zone: ZoneId = testZone,
    ): Entry {
        val instant = ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant()
        return Entry(
            id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
            createdAt = instant,
            updatedAt = instant,
            timeZoneID = zone.id,
            mood = mood,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
    }

    @Test
    fun plotMath_mapsDayIndexAndHourFractionAccurately() {
        val monday = LocalDate.of(2026, 11, 2) // Monday
        val sunday = LocalDate.of(2026, 11, 8) // Sunday

        val midnightEntry = makeEntry(monday, hour = 0, minute = 0, mood = Mood.LOW)
        val noonEntry = makeEntry(monday.plusDays(2), hour = 12, minute = 0, mood = Mood.OKAY)
        val eveningEntry = makeEntry(sunday, hour = 18, minute = 0, mood = Mood.GOOD)

        val pMidnight = WeekPlotMath.pointForEntry(midnightEntry)
        assertEquals(0, pMidnight.dayIndex)
        assertEquals(0f, pMidnight.yFractionFromBottom, 0.0001f)

        val pNoon = WeekPlotMath.pointForEntry(noonEntry)
        assertEquals(2, pNoon.dayIndex) // Wednesday
        assertEquals(0.5f, pNoon.yFractionFromBottom, 0.0001f)

        val pEvening = WeekPlotMath.pointForEntry(eveningEntry)
        assertEquals(6, pEvening.dayIndex) // Sunday
        assertEquals(0.75f, pEvening.yFractionFromBottom, 0.0001f)
    }

    @Test
    fun weekScreen_rendersDayTimePatternsAndSummaryAndLegend() {
        val monday = LocalDate.of(2026, 11, 2)
        // 18 check-ins across all 7 days between 8 AM and 6 PM (18:00)
        val entries = listOf(
            // Monday (4)
            makeEntry(monday, 8, 0, Mood.LOW),
            makeEntry(monday, 9, 0, Mood.LOW),
            makeEntry(monday, 12, 0, Mood.OKAY),
            makeEntry(monday, 17, 0, Mood.LOW),
            // Tuesday (5)
            makeEntry(monday.plusDays(1), 8, 0, Mood.LOW),
            makeEntry(monday.plusDays(1), 9, 0, Mood.GOOD),
            makeEntry(monday.plusDays(1), 12, 0, Mood.OKAY),
            makeEntry(monday.plusDays(1), 14, 0, Mood.LOW),
            makeEntry(monday.plusDays(1), 18, 0, Mood.GOOD),
            // Wednesday (3)
            makeEntry(monday.plusDays(2), 8, 0, Mood.LOW),
            makeEntry(monday.plusDays(2), 12, 0, Mood.OKAY),
            makeEntry(monday.plusDays(2), 18, 0, Mood.OKAY),
            // Thursday (2)
            makeEntry(monday.plusDays(3), 10, 0, Mood.OKAY),
            makeEntry(monday.plusDays(3), 18, 0, Mood.GOOD),
            // Friday (2)
            makeEntry(monday.plusDays(4), 10, 0, Mood.OKAY),
            makeEntry(monday.plusDays(4), 16, 0, Mood.LOW),
            // Saturday (1)
            makeEntry(monday.plusDays(5), 10, 0, Mood.GOOD),
            // Sunday (1)
            makeEntry(monday.plusDays(6), 14, 0, Mood.LOW),
        )

        composeTestRule.setContent {
            WeekScreen(
                entries = entries,
                initialWeekStart = monday,
            )
        }

        // Period header
        composeTestRule.onNodeWithText("2026-11-02 – 2026-11-09").assertIsDisplayed()

        // Section title
        composeTestRule.onNodeWithText("Day & Time Patterns").assertIsDisplayed()

        // Y-axis labels
        assertTrue(composeTestRule.onAllNodesWithText("12 AM").fetchSemanticsNodes().size >= 2)
        composeTestRule.onNodeWithText("8 PM").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 PM").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 PM").assertIsDisplayed()
        composeTestRule.onNodeWithText("8 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 AM").assertIsDisplayed()

        // Summary and legend
        composeTestRule.onNodeWithText("Most check-ins are between 8 AM and 6 PM").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Check-ins:").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("sad").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("neutral").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("happy").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("7 days tracked • 18 check-ins").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun draggingRightAndLeftOnPlot_movesWeekBackwardAndForward() {
        val week2Monday = LocalDate.of(2026, 11, 2)
        val week1Monday = week2Monday.minusWeeks(1) // 2026-10-26
        val week3Monday = week2Monday.plusWeeks(1)  // 2026-11-09

        val entries = listOf(
            // 2 check-ins in Week 1 (2026-10-26)
            makeEntry(week1Monday, 9, 0, Mood.GOOD),
            makeEntry(week1Monday.plusDays(1), 15, 0, Mood.OKAY),
            // 3 check-ins in Week 2 (2026-11-02)
            makeEntry(week2Monday, 8, 0, Mood.LOW),
            makeEntry(week2Monday.plusDays(2), 12, 0, Mood.OKAY),
            makeEntry(week2Monday.plusDays(4), 18, 0, Mood.GOOD),
        )

        composeTestRule.setContent {
            WeekScreen(
                entries = entries,
                initialWeekStart = week2Monday,
            )
        }

        // Starts on Week 2
        composeTestRule.onNodeWithText("2026-11-02 – 2026-11-09").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 days tracked • 3 check-ins").performScrollTo().assertIsDisplayed()

        // Dragging right on the plot moves 1 week backward (Week 1: 2026-10-26 – 2026-11-02)
        composeTestRule.onNodeWithTag("week_plot").performScrollTo().performTouchInput {
            swipeRight()
        }
        composeTestRule.onNodeWithText("2026-10-26 – 2026-11-02").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2 days tracked • 2 check-ins").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Most check-ins are between 9 AM and 3 PM").performScrollTo().assertIsDisplayed()

        // Dragging left on the plot moves 1 week forward (back to Week 2: 2026-11-02 – 2026-11-09)
        composeTestRule.onNodeWithTag("week_plot").performScrollTo().performTouchInput {
            swipeLeft()
        }
        composeTestRule.onNodeWithText("2026-11-02 – 2026-11-09").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("3 days tracked • 3 check-ins").performScrollTo().assertIsDisplayed()

        // Dragging left again moves forward to Week 3 (2026-11-09 – 2026-11-16, empty week)
        composeTestRule.onNodeWithTag("week_plot").performScrollTo().performTouchInput {
            swipeLeft()
        }
        composeTestRule.onNodeWithText("${week3Monday} – ${week3Monday.plusDays(7)}").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("No check-ins this week").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("0 days tracked • 0 check-ins").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun weekScreen_monthRhythmInsightsAndLowWeekSupportWork() {
        val today = LocalDate.now(testZone)
        val weekMonday = WeekPlotMath.mondayOf(today)
        val entries = (0..5).map { idx ->
            val day = weekMonday.plusDays(idx.toLong())
            val instant = ZonedDateTime.of(day, LocalTime.of(10, 0), testZone).toInstant()
            Entry(
                id = "00000000-0000-4000-8000-00000000000$idx",
                createdAt = instant,
                updatedAt = instant,
                timeZoneID = testZone.id,
                mood = Mood.LOW,
                tags = listOf("tired.drained"),
                comment = null,
                location = com.example.smileproc.core.LocationSample(
                    latitude = 54.6872,
                    longitude = 25.2797,
                    horizontalAccuracy = 15.0,
                    capturedAt = instant,
                    kind = com.example.smileproc.core.LocationKind.FRESH,
                ),
                source = EntrySource.APP,
            )
        }
        val unnamedCluster = com.example.smileproc.core.Cluster(
            id = com.example.smileproc.core.ClusterID.unnamed("00000000-0000-4000-8000-000000000000"),
            centroid = com.example.smileproc.core.LatLon(54.6872, 25.2797),
            memberIDs = entries.map { it.id },
            label = null,
        )
        val assignment = com.example.smileproc.core.PlaceAssignment(
            entryToCluster = entries.associate { it.id to unnamedCluster.id },
            clusters = mapOf(unnamedCluster.id to unnamedCluster),
        )
        val extras = mapOf(
            "00000000-0000-4000-8000-000000000000" to com.example.smileproc.core.EntryExtras(
                entryId = "00000000-0000-4000-8000-000000000000",
                contextTags = listOf(com.example.smileproc.core.BuiltInContextTag.WORK.id),
                customTagIds = emptyList(),
                bookmarked = false,
                updatedAt = entries.first().updatedAt,
            ),
        )
        var supportNavigated = false

        composeTestRule.setContent {
            WeekScreen(
                entries = entries,
                initialWeekStart = weekMonday,
                places = assignment,
                entryExtrasById = extras,
                onNavigateToSupport = { supportNavigated = true },
            )
        }

        // Low-week support CTA appears under "This week's notes"
        composeTestRule.onNodeWithText("This week's notes").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Talk to someone").performScrollTo().performClick()
        assertTrue(supportNavigated)

        // Month rhythm subtab
        composeTestRule.onNodeWithText("Month").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Day by day").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Time of day").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Day of the week").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("90 days").performScrollTo().performClick()

        // Insights subtab + inline place naming
        composeTestRule.onNodeWithText("Insights").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Feelings by mood").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Context & personal tags").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Places").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Name this place").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Another name").assertIsDisplayed()
    }
}
