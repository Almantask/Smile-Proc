package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.smileproc.ui.support.SupportScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SupportScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysGentleCopyAndEmergencyLine() {
        var backClicked = false

        composeTestRule.setContent {
            SupportScreen(
                onNavigateBack = { backClicked = true },
            )
        }

        // Title
        composeTestRule.onNodeWithText("Talk to someone").assertIsDisplayed()

        // Gentle body
        composeTestRule.onNodeWithText(
            "When things feel heavy, talking to someone you trust can make it a little lighter. That could be a friend, someone in your family, or someone whose job is to listen.",
        ).assertIsDisplayed()

        // Emergency line
        composeTestRule.onNodeWithText(
            "If you're in danger right now, contact your local emergency number.",
        ).assertIsDisplayed()

        // Back button
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        assertTrue(backClicked)
    }
}
