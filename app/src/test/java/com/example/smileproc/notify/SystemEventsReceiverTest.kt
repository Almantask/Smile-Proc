package com.example.smileproc.notify

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemEventsReceiverTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ReminderScheduler.cancelAll(context)
        container = SmileProcApplication.container(context)
        container.initialize()
        container.settingsRepository.clearAll()
    }

    @After
    fun tearDown() = runTest {
        ReminderScheduler.cancelAll(context)
    }

    @Test
    fun bootCompletedReschedulesAlarmsAndRestoresRestingState() = runTest {
        val shadowAlarm = shadowOf(alarmManager)

        container.settingsRepository.setOnboardingCompleted(true)
        container.settingsRepository.setReminders(Reminder.DEFAULT_REMINDERS)
        container.settingsRepository.setStanding(true)
        container.settingsRepository.setDismissed(false)

        val receiver = SystemEventsReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.handleIntent(context, intent)

        // Verify alarm is scheduled
        assertNotNull(shadowAlarm.nextScheduledAlarm)

        // Verify machine state is resting state (Faces silent)
        val state = container.settingsRepository.machineState.first()
        assertTrue(state is CheckInMachine.State.Faces && !state.alerting)
    }

    @Test
    fun timeChangeReschedulesAlarm() = runTest {
        val shadowAlarm = shadowOf(alarmManager)

        container.settingsRepository.setOnboardingCompleted(true)
        container.settingsRepository.setReminders(Reminder.DEFAULT_REMINDERS)

        val receiver = SystemEventsReceiver()
        val intent = Intent(Intent.ACTION_TIME_CHANGED)
        receiver.handleIntent(context, intent)

        assertNotNull(shadowAlarm.nextScheduledAlarm)
    }

    @Test
    fun timezoneChangeReschedulesAlarm() = runTest {
        val shadowAlarm = shadowOf(alarmManager)

        container.settingsRepository.setOnboardingCompleted(true)
        container.settingsRepository.setReminders(Reminder.DEFAULT_REMINDERS)

        val receiver = SystemEventsReceiver()
        val intent = Intent(Intent.ACTION_TIMEZONE_CHANGED)
        receiver.handleIntent(context, intent)

        assertNotNull(shadowAlarm.nextScheduledAlarm)
    }

    @Test
    fun onboardingNotCompletedIgnoresEvents() = runTest {
        val shadowAlarm = shadowOf(alarmManager)
        ReminderScheduler.cancelAll(context)

        container.settingsRepository.setOnboardingCompleted(false)
        container.settingsRepository.setReminders(Reminder.DEFAULT_REMINDERS)

        val receiver = SystemEventsReceiver()
        receiver.handleIntent(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.handleIntent(context, Intent(Intent.ACTION_TIME_CHANGED))
        receiver.handleIntent(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        assertNull(shadowAlarm.nextScheduledAlarm)
    }
}
