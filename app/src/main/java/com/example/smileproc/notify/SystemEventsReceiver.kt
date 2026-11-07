package com.example.smileproc.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.core.CheckInMachine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles device lifecycle events (`docs/spec/android.md` › Reminders; Manifest).
 *
 * Invariant: The only exported receiver in the dogfood and release APK. Reschedules reminder
 * alarms on boot, time changes, timezone changes, and app updates.
 */
class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        val appContext = context.applicationContext
        val container = SmileProcApplication.container(appContext)
        val pendingResult = goAsync()

        container.applicationScope.launch {
            try {
                handleIntent(appContext, intent)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    internal suspend fun handleIntent(context: Context, intent: Intent) {
        val container = SmileProcApplication.container(context)
        if (!container.settingsRepository.onboardingCompleted.first()) {
            return
        }
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.Booted)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.AppUpdated)
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                ReminderScheduler.scheduleNext(context)
            }
        }
    }
}
