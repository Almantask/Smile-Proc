package com.example.smileproc.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.core.Reminder
import com.example.smileproc.core.ReminderPause
import com.example.smileproc.core.ReminderScheduleCalculator
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Calculates and schedules the next reminder alarm across all configured reminders
 * (`docs/spec/android.md` › Reminders).
 *
 * Invariant: Exactly one pending alarm at any time across all reminders, using inexact
 * [AlarmManager.setAndAllowWhileIdle] with [AlarmManager.RTC_WAKEUP] in the device's current zone.
 */
object ReminderScheduler {
    const val REQUEST_REMINDER = 100

    fun nextReminderInstant(
        reminders: List<Reminder>,
        now: Instant,
        zoneId: ZoneId,
        windowsByReminderId: Map<String, Int> = emptyMap(),
        pause: ReminderPause = ReminderPause.NONE,
    ): Instant? = ReminderScheduleCalculator.nextOccurrence(
        reminders = reminders,
        now = now,
        zone = zoneId,
        windowsByReminderId = windowsByReminderId,
        pause = pause,
    )

    suspend fun scheduleNext(
        context: Context,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val container = SmileProcApplication.container(context)
        if (!container.settingsRepository.onboardingCompleted.first()) {
            cancelAll(context)
            return
        }
        val reminders = container.settingsRepository.reminders.first()
        val windows = container.settingsRepository.reminderWindows.first()
        val pause = container.settingsRepository.reminderPause.first()
        val nextInstant = nextReminderInstant(
            reminders = reminders,
            now = now,
            zoneId = zoneId,
            windowsByReminderId = windows,
            pause = pause,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER_FIRED
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_REMINDER,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        if (nextInstant != null) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextInstant.toEpochMilli(),
                    pending,
                )
            } catch (e: Exception) {
                SafeLog.failure(SafeLog.Event.ALARM_SCHEDULE_FAILED, e)
            }
        } else {
            try {
                alarmManager.cancel(pending)
            } catch (e: Exception) {
                SafeLog.failure(SafeLog.Event.ALARM_SCHEDULE_FAILED, e)
            }
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER_FIRED
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_REMINDER,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }
}
