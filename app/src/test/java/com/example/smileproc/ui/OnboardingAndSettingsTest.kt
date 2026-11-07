package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.smileproc.ui.onboarding.OnboardingScreen
import com.example.smileproc.ui.settings.SettingsScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingAndSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboarding_advancesAndCanBeSkipped() {
        var completed = false

        composeTestRule.setContent {
            OnboardingScreen(onComplete = { completed = true })
        }

        // Screen 1: Just for you
        composeTestRule.onNodeWithText("Just for you").assertIsDisplayed()

        // Tap Next
        composeTestRule.onNodeWithContentDescription("Next").performClick()

        // Screen 2: Notice places
        composeTestRule.onNodeWithText("Notice places").assertIsDisplayed()

        // Tap Skip
        composeTestRule.onNodeWithContentDescription("Skip").performClick()

        assertTrue(completed)
    }

    @Test
    fun settings_togglesLocationCapture() {
        var captureLocation = true

        composeTestRule.setContent {
            SettingsScreen(
                captureLocation = captureLocation,
                onCaptureLocationChanged = { captureLocation = it },
            )
        }

        composeTestRule.onNodeWithText("Capture location").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Capture location").performClick()

        assertFalse(captureLocation)
    }
}
