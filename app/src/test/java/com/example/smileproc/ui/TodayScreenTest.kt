package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.today.TodayScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class TodayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingFaceButton_savesImmediatelyWithAppSourceAndNoLocation() {
        var savedEntry: Entry? = null
        var capturedLocationEntryId: String? = null

        composeTestRule.setContent {
            TodayScreen(
                entries = emptyList(),
                places = null,
                lowRunDismissedEntryId = null,
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = { savedEntry = it },
                onUpdateEntry = {},
                onDeleteEntry = {},
                onSavePlaceLabel = {},
                onDismissLowRun = {},
                onResetLowRunDismissed = {},
                onCaptureLocationForEntry = { capturedLocationEntryId = it },
                onNavigateToHistory = {},
            )
        }

        // Tapping Good face
        composeTestRule.onNodeWithContentDescription("Good").performClick()

        assertNotNull(savedEntry)
        assertEquals(Mood.GOOD, savedEntry?.mood)
        assertEquals(EntrySource.APP, savedEntry?.source)
        assertNull(savedEntry?.location)
        assertEquals(savedEntry?.id, capturedLocationEntryId)

        // Follow-up sheet should now be open
        composeTestRule.onNodeWithText("Saved").assertIsDisplayed()
    }

    @Test
    fun followUpSheet_entersNoteAndSavesOnDone() {
        var updatedEntry: Entry? = null

        composeTestRule.setContent {
            TodayScreen(
                entries = emptyList(),
                places = null,
                lowRunDismissedEntryId = null,
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = {},
                onUpdateEntry = { updatedEntry = it },
                onDeleteEntry = {},
                onSavePlaceLabel = {},
                onDismissLowRun = {},
                onResetLowRunDismissed = {},
                onCaptureLocationForEntry = {},
                onNavigateToHistory = {},
            )
        }

        // Tap Okay
        composeTestRule.onNodeWithContentDescription("Okay").performClick()
        composeTestRule.onNodeWithText("Saved").assertIsDisplayed()

        // Enter note
        composeTestRule.onNodeWithText("Note")
            .performScrollTo()
            .performTextInput("Walking in the park")

        // Click Done
        composeTestRule.onNodeWithText("Done")
            .performScrollTo()
            .performClick()

        assertNotNull(updatedEntry)
        assertEquals("Walking in the park", updatedEntry?.comment)
        assertEquals(Mood.OKAY, updatedEntry?.mood)
    }
}
