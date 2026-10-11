package com.example.smileproc.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog

/**
 * FIRE_REMINDER, FIRE_RESTORE, SEED_DEMO and CLEAR_DEMO for scripts/emu/fire.sh.
 * M0 stub: each will call the same code path as its real trigger once that exists (M5, M8).
 */
class DebugEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SafeLog.event(SafeLog.Event.DEBUG_EVENT_NOT_WIRED)
    }
}
