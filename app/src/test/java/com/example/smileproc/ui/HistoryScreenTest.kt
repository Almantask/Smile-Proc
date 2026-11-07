package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.history.HistoryScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEntriesAndDeletesWithConfirmation() {
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = listOf("happy"),
            comment = "Great coffee morning",
            location = null,
            source = EntrySource.APP,
        )

        var deletedId: String? = null

        composeTestRule.setContent {
            HistoryScreen(
                entries = listOf(entry),
                places = null,
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = {},
                onDeleteEntry = { deletedId = it },
                onSavePlaceLabel = {},
                onNavigateBack = {},
            )
        }

        // Check entry comment is displayed in history
        composeTestRule.onNodeWithText("Great coffee morning").assertIsDisplayed()

        // Tap the entry row to open EntryDetailSheet
        composeTestRule.onNodeWithText("Great coffee morning").performClick()

        // Detail sheet opens with "Check-in"
        composeTestRule.onNodeWithText("Check-in").assertIsDisplayed()

        // Tap "Delete check-in"
        composeTestRule.onNodeWithContentDescription("Delete check-in")
            .performScrollTo()
            .performClick()

        // Confirmation dialog appears
        composeTestRule.onNodeWithText("Delete this check-in?").assertIsDisplayed()

        // Tap confirm "Delete"
        composeTestRule.onNodeWithContentDescription("Delete").performClick()

        assertEquals(entry.id, deletedId)
    }
}
