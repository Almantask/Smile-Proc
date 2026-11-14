package com.example.smileproc.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.ui.onboarding.OnboardingScreen
import com.example.smileproc.ui.settings.SettingsScreen
import com.example.smileproc.ui.settings.WeeklyCardTimeDialog
import com.example.smileproc.ui.theme.SmileProcTheme
import com.example.smileproc.ui.week.WeekScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LocalizationUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun mondayMorningEntry() = Entry(
        id = "00000000-0000-4000-8000-000000000001",
        createdAt = Instant.parse("2026-06-08T08:30:00Z"),
        updatedAt = Instant.parse("2026-06-08T08:30:00Z"),
        timeZoneID = "UTC",
        mood = Mood.GOOD,
        tags = emptyList(),
        comment = null,
        location = null,
        source = EntrySource.APP,
    )

    private fun mondayEveningEntry() = Entry(
        id = "00000000-0000-4000-8000-000000000002",
        createdAt = Instant.parse("2026-06-08T18:00:00Z"),
        updatedAt = Instant.parse("2026-06-08T18:00:00Z"),
        timeZoneID = "UTC",
        mood = Mood.OKAY,
        tags = emptyList(),
        comment = null,
        location = null,
        source = EntrySource.APP,
    )

    @Test
    @Config(qualifiers = "lt")
    fun weekScreenUses24HourFormatAndLithuanianLabels() {
        composeTestRule.setContent {
            SmileProcTheme {
                WeekScreen(
                    entries = listOf(mondayMorningEntry(), mondayEveningEntry()),
                    initialWeekStart = LocalDate.of(2026, 6, 8),
                )
            }
        }

        composeTestRule.onNodeWithText("Dienos ir laiko dėsningumai").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daugiausia įrašų tarp 08:00 ir 18:00").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1 sekta diena • 2 įrašai").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun weekScreenKeeps12HourFormatInEnglish() {
        composeTestRule.setContent {
            SmileProcTheme {
                WeekScreen(
                    entries = listOf(mondayMorningEntry(), mondayEveningEntry()),
                    initialWeekStart = LocalDate.of(2026, 6, 8),
                )
            }
        }

        composeTestRule.onNodeWithText("Most check-ins are between 8 AM and 6 PM").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "lt")
    fun weeklyCardTimeDialogShowsLithuanianShortWeekdays() {
        composeTestRule.setContent {
            SmileProcTheme {
                WeeklyCardTimeDialog(
                    initialTime = WeeklyCardTime.DEFAULT,
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Pr").assertIsDisplayed()
        composeTestRule.onNodeWithText("Št").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sk").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "lt")
    fun settingsScreenShowsLanguageRowAndOpensLanguagePicker() {
        composeTestRule.setContent {
            SmileProcTheme {
                SettingsScreen(
                    captureLocation = false,
                    onCaptureLocationChanged = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Kalba").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onAllNodesWithText("Pagal sistemą").assertCountEquals(2)
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lietuvių").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "lt")
    fun onboardingStepOneShowsLithuanianCopyAndLanguageChips() {
        composeTestRule.setContent {
            SmileProcTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Tik tau").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lietuvių").assertIsDisplayed()
    }
}
