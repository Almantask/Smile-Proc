package com.example.smileproc.spikes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A4: one inexact `setAndAllowWhileIdle(RTC_WAKEUP)` reminder (android.md › Reminders). When it
 * fires it records how late it was and posts the alerting faces.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val firedAt = System.currentTimeMillis()
        val pending = goAsync()
        spikeScope.launch {
            try {
                val prefs = SpikePrefs(context)
                val scheduledAt = prefs.reminderAt
                prefs.reminderAt = 0
                Records.add(
                    context, "reminder",
                    JSONObject().put("scheduledAt", scheduledAt).put("firedAt", firedAt)
                        .put("delayS", if (scheduledAt > 0) (firedAt - scheduledAt) / 1000.0 else JSONObject.NULL)
                        .put("idle", context.getSystemService(PowerManager::class.java).isDeviceIdleMode)
                        .put("locked", locked(context)),
                )
                SpikeNotifier.render(context, SpikeNotifier.State.FACES_ALERTING)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_REMINDER = 30

        fun scheduleIn(context: Context, seconds: Long) = schedule(context, System.currentTimeMillis() + seconds * 1000)

        /** The next HH:MM in the device's current zone (today, else tomorrow). */
        fun scheduleAt(context: Context, hhmm: String) {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            var at = ZonedDateTime.of(LocalDate.now(zone), LocalTime.parse(hhmm), zone)
            if (!at.isAfter(now)) at = at.plusDays(1)
            schedule(context, at.toInstant().toEpochMilli())
        }

        fun schedule(context: Context, atMillis: Long) {
            SpikePrefs(context).reminderAt = atMillis
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_REMINDER, Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            context.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }
}
