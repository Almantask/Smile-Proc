package com.example.smileproc.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.smileproc.MainActivity
import com.example.smileproc.R
import com.example.smileproc.SmileProcApp
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.ReminderSpec
import com.example.smileproc.core.WeeklyCardTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

object Channels {
    const val REMINDERS = "checkin"
    const val WEEKLY = "weekly"
    const val CHECKIN_NOTIFICATION = 1001
    const val SAVED_NOTIFICATION = 1002
    const val WEEKLY_NOTIFICATION = 1003

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        reminders.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val weekly = NotificationChannel(
            WEEKLY,
            context.getString(R.string.notification_channel_weekly),
            NotificationManager.IMPORTANCE_LOW,
        )
        weekly.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(reminders)
        manager.createNotificationChannel(weekly)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

object CheckInNotifications {
    const val ACTION_LOG = "com.example.smileproc.LOG_MOOD"
    const val EXTRA_MOOD = "mood"

    fun checkIn(context: Context): Notification {
        val builder = NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_checkin_title))
            .setContentText(context.getString(R.string.notification_checkin_body))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, 40))
        for (mood in Mood.entries) {
            builder.addAction(action(context, mood))
        }
        return builder.build()
    }

    fun saved(context: Context): Notification =
        NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_checkin_title))
            .setContentText(context.getString(R.string.notification_saved))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000)
            .build()

    fun weekly(context: Context): Notification =
        NotificationCompat.Builder(context, Channels.WEEKLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_weekly_title))
            .setContentText(context.getString(R.string.notification_weekly_body))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, 41))
            .build()

    private fun action(context: Context, mood: Mood): NotificationCompat.Action {
        val label = when (mood) {
            Mood.LOW -> R.string.mood_low
            Mood.OKAY -> R.string.mood_okay
            Mood.GOOD -> R.string.mood_good
        }
        val intent = Intent(context, CheckInActionReceiver::class.java).apply {
            action = ACTION_LOG
            putExtra(EXTRA_MOOD, mood.value)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            mood.value,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), pending)
            .setAuthenticationRequired(false)
            .build()
    }

    private fun openApp(context: Context, request: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class CheckInActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val moodValue = intent.getIntExtra(CheckInNotifications.EXTRA_MOOD, 0)
        val mood = Mood.entries.firstOrNull { it.value == moodValue } ?: return
        val pending = goAsync()
        val app = context.applicationContext as SmileProcApp
        app.container.scope.launch {
            try {
                val now = java.time.Instant.now()
                val zone = ZoneId.systemDefault()
                val entry = app.container.repository.quickLog(mood, EntrySource.NOTIFICATION, zone, now)
                if (app.container.settings.snapshot().captureLocation) {
                    val sample = app.container.location.lastKnown()
                    if (sample != null) {
                        app.container.repository.attachLocationIfMissing(entry.id, sample, java.time.Instant.now())
                    }
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.cancel(Channels.CHECKIN_NOTIFICATION)
                if (Channels.canPost(context)) {
                    manager?.notify(Channels.SAVED_NOTIFICATION, CheckInNotifications.saved(context))
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as SmileProcApp
        app.container.scope.launch {
            try {
                Channels.ensure(context)
                val manager = context.getSystemService(NotificationManager::class.java)
                if (Channels.canPost(context) && manager != null) {
                    if (intent.action == ReminderScheduler.ACTION_WEEKLY) {
                        manager.notify(Channels.WEEKLY_NOTIFICATION, CheckInNotifications.weekly(context))
                    } else {
                        manager.notify(Channels.CHECKIN_NOTIFICATION, CheckInNotifications.checkIn(context))
                    }
                }
                app.container.reminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as SmileProcApp
        app.container.scope.launch {
            try {
                app.container.reminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        val app = context.applicationContext as SmileProcApp
        val settings = app.container.settings.snapshot()
        cancelReminders(settings.reminders)
        cancelWeekly()
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        for (reminder in settings.reminders) {
            val next = nextReminder(now, reminder) ?: continue
            schedule(next.toInstant().toEpochMilli(), reminderRequest(reminder.id), checkInIntent(reminder.id))
        }
        val weekly = nextWeekly(now, settings.weeklyCard)
        schedule(weekly.toInstant().toEpochMilli(), WEEKLY_REQUEST, weeklyIntent())
    }

    fun cancelAll(reminders: List<ReminderSpec>) {
        cancelReminders(reminders)
        cancelWeekly()
    }

    private fun schedule(whenMillis: Long, request: Int, intent: Intent) {
        val pending = PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
    }

    private fun cancelReminders(reminders: List<ReminderSpec>) {
        for (reminder in reminders) {
            val pending = PendingIntent.getBroadcast(
                context,
                reminderRequest(reminder.id),
                checkInIntent(reminder.id),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) alarms?.cancel(pending)
        }
    }

    private fun cancelWeekly() {
        val pending = PendingIntent.getBroadcast(
            context,
            WEEKLY_REQUEST,
            weeklyIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) alarms?.cancel(pending)
    }

    private fun checkInIntent(id: String): Intent =
        Intent(context, ReminderFireReceiver::class.java).apply {
            action = ACTION_CHECKIN
            putExtra(EXTRA_ID, id)
        }

    private fun weeklyIntent(): Intent =
        Intent(context, ReminderFireReceiver::class.java).apply { action = ACTION_WEEKLY }

    companion object {
        const val ACTION_CHECKIN = "com.example.smileproc.REMINDER"
        const val ACTION_WEEKLY = "com.example.smileproc.WEEKLY"
        const val EXTRA_ID = "reminder_id"
        private const val WEEKLY_REQUEST = 7_000

        fun reminderRequest(id: String): Int = 8_000 + (id.hashCode() and 0x0FFF)

        fun nextReminder(now: ZonedDateTime, reminder: ReminderSpec): ZonedDateTime? {
            if (reminder.weekdays.isEmpty()) return null
            val days = reminder.weekdays.toSet()
            for (offset in 0..7) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (date.dayOfWeek.value !in days) continue
                val candidate = ZonedDateTime.of(date, LocalTime.of(reminder.hour, reminder.minute), now.zone)
                if (candidate.isAfter(now)) return candidate
            }
            return null
        }

        fun nextWeekly(now: ZonedDateTime, card: WeeklyCardTime): ZonedDateTime {
            for (offset in 0..7) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (date.dayOfWeek.value != card.weekday) continue
                val candidate = ZonedDateTime.of(date, LocalTime.of(card.hour, card.minute), now.zone)
                if (candidate.isAfter(now)) return candidate
            }
            return ZonedDateTime.of(
                now.toLocalDate().plusDays(7),
                LocalTime.of(card.hour, card.minute),
                now.zone,
            )
        }
    }
}
