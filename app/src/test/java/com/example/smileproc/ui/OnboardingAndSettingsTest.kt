package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.ui.onboarding.OnboardingScreen
import com.example.smileproc.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
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
        composeTestRule.onNodeWithContentDescription("Next").performClick()

        // Screen 2: Notice places
        composeTestRule.onNodeWithText("Notice places").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Skip").performClick()

        // Screen 3: Gentle reminders
        composeTestRule.onNodeWithText("Gentle reminders").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Skip").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Screen 4: App lock
        composeTestRule.onAllNodes(hasText("App lock"))[0].assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next").performScrollTo().performClick()

        // Screen 5: Try it from your lock screen
        composeTestRule.onNodeWithText("Try it from your lock screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start").performScrollTo().performClick()

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

        composeTestRule.onNodeWithText("Capture location").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Capture location").performScrollTo().performClick()

        assertFalse(captureLocation)
    }

    @Test
    fun settings_opensWeeklyCardTimeDialog_andUpdates() {
        var weeklyCardTime = WeeklyCardTime(weekday = 7, hour = 19, minute = 0)

        composeTestRule.setContent {
            SettingsScreen(
                captureLocation = true,
                onCaptureLocationChanged = {},
                weeklyCardTime = weeklyCardTime,
                onWeeklyCardTimeChanged = { weeklyCardTime = it },
            )
        }

        composeTestRule.onNodeWithText("Weekly card time").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Your weekly card is ready Sunday at 19:00.").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Weekly card time").performClick()

        composeTestRule.onNodeWithText("Fri").performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals(5, weeklyCardTime.weekday)
    }
}
