package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.today.TodayScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LowRunCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createLowEntry(daysAgo: Long): Entry {
        val time = Instant.now().minus(daysAgo, ChronoUnit.DAYS)
        return Entry(
            id = UUID.randomUUID().toString(),
            createdAt = time,
            updatedAt = time,
            timeZoneID = "UTC",
            mood = Mood.LOW,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
    }

    @Test
    fun showsLowRunCardWhenFiveLowEntriesOverTwoDays() {
        val lowEntries = listOf(
            createLowEntry(3),
            createLowEntry(3),
            createLowEntry(2),
            createLowEntry(1),
            createLowEntry(0),
        )

        var dismissedId: String? = null

        composeTestRule.setContent {
            TodayScreen(
                entries = lowEntries,
                places = null,
                lowRunDismissedEntryId = null,
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = {},
                onUpdateEntry = {},
                onDeleteEntry = {},
                onSavePlaceLabel = {},
                onDismissLowRun = { dismissedId = it },
                onResetLowRunDismissed = {},
                onCaptureLocationForEntry = {},
                onNavigateToHistory = {},
            )
        }

        // Low run card should be displayed
        composeTestRule.onNodeWithText(
            "Your last few check-ins have been low. If it would help, talking to someone can make it lighter."
        ).assertIsDisplayed()

        // Tap Not now
        composeTestRule.onNodeWithContentDescription("Not now").performClick()
        assertNotNull(dismissedId)
    }
}
