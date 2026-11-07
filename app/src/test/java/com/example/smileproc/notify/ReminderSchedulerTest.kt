package com.example.smileproc.notify

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.core.Reminder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private lateinit var context: Context
    private val zoneId = ZoneId.of("Europe/Rome")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun nextReminderInstantSelectsCorrectNextOccurrence() {
        val reminders = Reminder.DEFAULT_REMINDERS // 08:00, 12:00, 16:00, 20:00 every day
        val today = LocalDate.of(2026, 11, 7) // Saturday (ISO 6)

        // Case 1: At 07:30 -> next is 08:00 today
        val now1 = today.atTime(LocalTime.of(7, 30)).atZone(zoneId).toInstant()
        val next1 = ReminderScheduler.nextReminderInstant(reminders, now1, zoneId)
        val expected1 = today.atTime(LocalTime.of(8, 0)).atZone(zoneId).toInstant()
        assertEquals(expected1, next1)

        // Case 2: At 10:00 -> next is 12:00 today
        val now2 = today.atTime(LocalTime.of(10, 0)).atZone(zoneId).toInstant()
        val next2 = ReminderScheduler.nextReminderInstant(reminders, now2, zoneId)
        val expected2 = today.atTime(LocalTime.of(12, 0)).atZone(zoneId).toInstant()
        assertEquals(expected2, next2)

        // Case 3: At 20:30 -> next is 08:00 tomorrow
        val now3 = today.atTime(LocalTime.of(20, 30)).atZone(zoneId).toInstant()
        val next3 = ReminderScheduler.nextReminderInstant(reminders, now3, zoneId)
        val expected3 = today.plusDays(1).atTime(LocalTime.of(8, 0)).atZone(zoneId).toInstant()
        assertEquals(expected3, next3)

        // Case 4: Custom reminder only on Wednesdays (ISO 3)
        // Today is Saturday (ISO 6) -> next Wednesday is in 4 days
        val wednesdayReminder = listOf(
            Reminder(hour = 15, minute = 30, days = setOf(3)),
        )
        val next4 = ReminderScheduler.nextReminderInstant(wednesdayReminder, now2, zoneId)
        val expected4 = today.plusDays(4).atTime(LocalTime.of(15, 30)).atZone(zoneId).toInstant()
        assertEquals(expected4, next4)

        // Case 5: Empty reminders -> returns null
        assertNull(ReminderScheduler.nextReminderInstant(emptyList(), now1, zoneId))

        // Case 6: Reminder with empty days -> returns null
        val emptyDaysReminder = listOf(Reminder(hour = 12, minute = 0, days = emptySet()))
        assertNull(ReminderScheduler.nextReminderInstant(emptyDaysReminder, now1, zoneId))
    }

    @Test
    fun scheduleNextSetsAlarmManagerAlarm() = runTest {
        val container = SmileProcApplication.container(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)

        container.settingsRepository.setOnboardingCompleted(true)
        // Set the 4 default reminders
        container.settingsRepository.setReminders(Reminder.DEFAULT_REMINDERS)

        val fixedNow = LocalDate.of(2026, 11, 7).atTime(10, 0).atZone(zoneId).toInstant()
        ReminderScheduler.scheduleNext(context, now = fixedNow, zoneId = zoneId)

        val nextScheduled = shadowAlarm.nextScheduledAlarm
        assertNotNull("An alarm should be scheduled", nextScheduled)
        assertEquals(AlarmManager.RTC_WAKEUP, nextScheduled!!.type)
        val expectedTime = LocalDate.of(2026, 11, 7).atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expectedTime, nextScheduled.triggerAtTime)

        // When reminders deleted completely -> alarm cancelled
        container.settingsRepository.setReminders(emptyList())
        ReminderScheduler.scheduleNext(context, now = fixedNow, zoneId = zoneId)
        assertNull("Alarm should be cancelled when no reminders exist", shadowAlarm.nextScheduledAlarm)
    }

    @Test
    fun scheduleNextCancelsAlarmsWhenOnboardingNotCompleted() = runTest {
        val container = SmileProcApplication.container(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)

        container.settingsRepository.setOnboardingCompleted(false)
        container.settingsRepository.setReminders(Reminder.DEFAULT_REMINDERS)

        val fixedNow = LocalDate.of(2026, 11, 7).atTime(10, 0).atZone(zoneId).toInstant()
        ReminderScheduler.scheduleNext(context, now = fixedNow, zoneId = zoneId)

        assertNull(shadowAlarm.nextScheduledAlarm)
    }
}
