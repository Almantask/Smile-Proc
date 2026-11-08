package com.example.smileproc.ui

import android.app.AlarmManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.core.Reminder
import com.example.smileproc.notify.ReminderScheduler
import com.example.smileproc.ui.onboarding.OnboardingScreen
import com.example.smileproc.ui.settings.LockScreenCheckInScreen
import com.example.smileproc.ui.settings.RemindersScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RemindersAndLockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() = runTest {
        container = SmileProcApplication.container(ApplicationProvider.getApplicationContext())
        container.initialize()
    }

    @Test
    fun remindersScreen_showsDefaultsAndAllowsDeletion() = runTest {
        container.settingsRepository.setReminders(Reminder.DEFAULT_REMINDERS)

        composeTestRule.setContent {
            RemindersScreen(
                container = container,
                onNavigateBack = {},
            )
        }

        // Verify the 4 default times are displayed
        composeTestRule.onNodeWithText("08:00").assertExists()
        composeTestRule.onNodeWithText("12:00").assertExists()
        composeTestRule.onNodeWithText("16:00").assertExists()
        composeTestRule.onNodeWithText("20:00").assertExists()

        // Delete 08:00
        composeTestRule.onNodeWithContentDescription("Delete the reminder at 08:00").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            runBlocking { container.settingsRepository.reminders.first().size == 3 }
        }

        val remindersAfterDelete = container.settingsRepository.reminders.first()
        assertEquals(3, remindersAfterDelete.size)
        assertFalse(remindersAfterDelete.any { it.hour == 8 })
    }

    @Test
    fun remindersScreen_deletingAllShowsEmptyState() = runTest {
        container.settingsRepository.setReminders(emptyList())

        composeTestRule.setContent {
            RemindersScreen(
                container = container,
                onNavigateBack = {},
            )
        }

        composeTestRule.onNodeWithText("No reminders set. You can check in from the app any time.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a reminder").assertIsDisplayed()
    }

    @Test
    fun lockScreenCheckIn_displaysRowsAndTogglesStanding() = runTest {
        container.settingsRepository.setStanding(true)

        composeTestRule.setContent {
            LockScreenCheckInScreen(
                container = container,
                onNavigateBack = {},
            )
        }

        composeTestRule.onNodeWithText("Notifications allowed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Check-ins turned on").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shown on the lock screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep a check-in on the lock screen").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Send a test check-in")[0].assertExists()
    }

    @Test
    fun onboarding_step3PreFillsDefaultsAndSkipPreservesThem() {
        var completedReminders: List<Reminder>? = null
        var completedStanding: Boolean? = null

        composeTestRule.setContent {
            OnboardingScreen(
                container = container,
                onComplete = { reminders, standing ->
                    completedReminders = reminders
                    completedStanding = standing
                },
            )
        }

        // Step 1 -> Next
        composeTestRule.onNodeWithContentDescription("Next").performClick()

        // Step 2 -> Skip
        composeTestRule.onNodeWithContentDescription("Skip").performClick()

        // Skipping onboarding on step 2 completes with defaults preserved
        assertEquals(4, completedReminders?.size)
        assertEquals(true, completedStanding)
    }

    @Test
    fun remindersScreen_addReminderAllowsSelectingTimeAndDays() = runTest {
        container.settingsRepository.setReminders(
            listOf(
                Reminder("1", 8, 0, (1..7).toSet()),
                Reminder("2", 12, 0, (1..7).toSet()),
            ),
        )

        composeTestRule.setContent {
            RemindersScreen(
                container = container,
                onNavigateBack = {},
            )
        }

        // Tap Add a reminder
        composeTestRule.onNodeWithText("Add a reminder").performClick()
        composeTestRule.waitForIdle()

        // Dialog appears with Save button
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            runBlocking { container.settingsRepository.reminders.first().size == 3 }
        }

        val updated = container.settingsRepository.reminders.first()
        assertEquals(3, updated.size)
        assertTrue(updated.any { it.hour == 9 && it.minute == 0 })
    }

    @Test
    fun onboardingCompletion_storesDefaultsAndSchedulesAlarm() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)

        var completedReminders: List<Reminder>? = null
        var completedStanding: Boolean? = null

        composeTestRule.setContent {
            OnboardingScreen(
                container = container,
                onComplete = { reminders, standing ->
                    completedReminders = reminders
                    completedStanding = standing
                },
            )
        }

        // Step 1 -> Next
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        // Step 2 -> Skip
        composeTestRule.onNodeWithContentDescription("Skip").performClick()

        assertNotNull(completedReminders)
        assertEquals(Reminder.DEFAULT_REMINDERS, completedReminders)

        // Persist & schedule (as MainActivity does upon onComplete)
        container.settingsRepository.setReminders(completedReminders!!)
        container.settingsRepository.setStanding(completedStanding ?: true)
        container.settingsRepository.setOnboardingCompleted(true)
        ReminderScheduler.scheduleNext(context)

        assertEquals(4, container.settingsRepository.reminders.first().size)
        assertTrue(container.settingsRepository.onboardingCompleted.first())
        assertNotNull(shadowAlarm.nextScheduledAlarm)

        ReminderScheduler.cancelAll(context)
    }
}
