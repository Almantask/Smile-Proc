package com.example.smileproc.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.core.CheckInMachine
import kotlinx.coroutines.launch

/**
 * Handles reminder alarm firings (`docs/spec/android.md` › Reminders).
 *
 * Invariant: Not exported. Uses `goAsync()`, alerts the check-in notification via
 * [CheckInMachine.Event.ReminderFired], and schedules the next occurrence.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
        SafeLog.event(SafeLog.Event.REMINDER_FIRED)
        CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.ReminderFired)
        ReminderScheduler.scheduleNext(context)
    }

    companion object {
        const val ACTION_REMINDER_FIRED = "com.example.smileproc.action.REMINDER_FIRED"
    }
}
