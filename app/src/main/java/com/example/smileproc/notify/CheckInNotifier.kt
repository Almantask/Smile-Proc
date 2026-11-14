package com.example.smileproc.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smileproc.MainActivity
import com.example.smileproc.R
import com.example.smileproc.SafeLog
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.Mood

/**
 * Manages SmileProc's single check-in notification (`docs/spec/android.md` › The check-in notification).
 *
 * Invariants:
 * - Channel: single channel `checkin` ("Check-ins"), IMPORTANCE_DEFAULT, lock-screen visibility public.
 * - Single notification ID: [ID_CHECKIN] (1). Only [render] posts or cancels notifications.
 * - Text is strictly one of the 4 safe strings from strings.xml.
 * - Face action buttons use explicit Broadcast PendingIntents with FLAG_IMMUTABLE,
 *   setAuthenticationRequired(false), and setShowsUserInterface(false).
 */
object CheckInNotifier {
    const val CHANNEL_ID = "checkin"
    const val ID_CHECKIN = CheckInMachine.ID_CHECKIN

    const val REQUEST_CONTENT = 0
    const val REQUEST_CONTENT_FOLLOWUP = 7
    const val REQUEST_RATE_LOW = 1
    const val REQUEST_RATE_OKAY = 2
    const val REQUEST_RATE_GOOD = 3
    const val REQUEST_DISMISSED = 4
    const val REQUEST_RESTORE = 5
    const val REQUEST_TEST_TIMER = 6
    const val REQUEST_TEST_LOW = 11
    const val REQUEST_TEST_OKAY = 12
    const val REQUEST_TEST_GOOD = 13

    /**
     * Privacy-safe intent extra indicating the user tapped the `"Saved ✓"` notification so the
     * app can open the follow-up tag/note sheet after passing App Lock (if enabled).
     * Carries no mood, entry ID, tag, comment, or location data.
     */
    const val EXTRA_OPEN_FOLLOWUP = "com.example.smileproc.extra.OPEN_FOLLOWUP"

    fun ensureChannel(context: Context) {
        val localized = com.example.smileproc.AppLocale.wrapContext(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = localized.getString(R.string.notification_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: CheckInMachine.State, ongoing: Boolean): Notification {
        val localized = com.example.smileproc.AppLocale.wrapContext(context)
        ensureChannel(localized)
        val copyKey = CheckInMachine.notificationCopyKey(state)
            ?: throw IllegalArgumentException("Cannot build notification for state $state")

        val textRes = when (copyKey) {
            CheckInMachine.NotificationCopyKey.ASK -> R.string.notification_text_ask
            CheckInMachine.NotificationCopyKey.SAVED -> R.string.notification_text_saved
            CheckInMachine.NotificationCopyKey.TEST_DONE -> R.string.notification_text_test_done
            CheckInMachine.NotificationCopyKey.FAILED -> R.string.notification_text_failed
        }

        val isSaved = state == CheckInMachine.State.Saved
        val contentIntent = PendingIntent.getActivity(
            context,
            if (isSaved) REQUEST_CONTENT_FOLLOWUP else REQUEST_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_FOLLOWUP, isSaved)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISSED,
            Intent(context, CheckInReceiver::class.java).apply {
                action = CheckInReceiver.ACTION_DISMISSED
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_face)
            .setContentTitle(localized.getString(R.string.notification_title))
            .setContentText(localized.getString(textRes))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setAutoCancel(false)
            .setOngoing(ongoing)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)

        val isAlerting = (state is CheckInMachine.State.Faces && state.alerting) || state is CheckInMachine.State.Test
        if (isAlerting) {
            builder.setOnlyAlertOnce(false)
        } else {
            builder.setSilent(true)
            builder.setOnlyAlertOnce(true)
        }

        val hasFaceButtons = state is CheckInMachine.State.Faces || state is CheckInMachine.State.Test
        if (hasFaceButtons) {
            val isTest = state is CheckInMachine.State.Test
            val faceList = listOf(
                Triple(Mood.LOW, R.string.face_low, if (isTest) REQUEST_TEST_LOW else REQUEST_RATE_LOW),
                Triple(Mood.OKAY, R.string.face_okay, if (isTest) REQUEST_TEST_OKAY else REQUEST_RATE_OKAY),
                Triple(Mood.GOOD, R.string.face_good, if (isTest) REQUEST_TEST_GOOD else REQUEST_RATE_GOOD),
            )

            for ((mood, stringRes, reqCode) in faceList) {
                val rateIntent = Intent(context, CheckInReceiver::class.java).apply {
                    action = CheckInReceiver.ACTION_RATE
                    putExtra(CheckInReceiver.EXTRA_MOOD, mood.value)
                    putExtra(CheckInReceiver.EXTRA_TEST, isTest)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    rateIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val action = NotificationCompat.Action.Builder(0, localized.getString(stringRes), pending)
                    .setAuthenticationRequired(false)
                    .setShowsUserInterface(false)
                    .build()
                builder.addAction(action)
            }
        }

        return builder.build()
    }

    /**
     * Posts or cancels [ID_CHECKIN] per [state].
     * Returns true if posted, false if cancelled or notifications disabled.
     */
    fun render(context: Context, state: CheckInMachine.State, ongoing: Boolean): Boolean {
        if (state == CheckInMachine.State.Hidden) {
            cancel(context)
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return false
        }
        val notification = build(context, state, ongoing)
        return try {
            manager.notify(ID_CHECKIN, notification)
            true
        } catch (e: SecurityException) {
            SafeLog.failure(SafeLog.Event.NOTIFY_FAILED, e)
            false
        }
    }

    fun cancel(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        try {
            manager.cancel(ID_CHECKIN)
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.NOTIFY_FAILED, e)
        }
    }

    fun scheduleRestore(context: Context, delaySeconds: Long = CheckInMachine.RESTORE_DELAY_SECONDS) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_RESTORE
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_RESTORE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = SystemClock.elapsedRealtime() + delaySeconds * 1000L
        try {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.ALARM_SCHEDULE_FAILED, e)
        }
    }

    fun cancelRestore(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_RESTORE
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_RESTORE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun scheduleTestTimer(context: Context, delaySeconds: Long = CheckInMachine.TEST_DELAY_SECONDS) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_TEST_TIMER
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_TEST_TIMER,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = SystemClock.elapsedRealtime() + delaySeconds * 1000L
        try {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.ALARM_SCHEDULE_FAILED, e)
        }
    }
}
