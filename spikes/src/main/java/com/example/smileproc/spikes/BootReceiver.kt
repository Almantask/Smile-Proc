package com.example.smileproc.spikes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A3: after a reboot (delivered after the first unlock), bring the standing faces back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        spikeScope.launch {
            try {
                val prefs = SpikePrefs(context)
                // Booted: the resting state (logic.md).
                val posted = SpikeNotifier.rest(context)
                // Alarms don't survive a reboot.
                if (prefs.reminderAt > System.currentTimeMillis()) ReminderReceiver.schedule(context, prefs.reminderAt)
                Records.add(context, "boot", JSONObject().put("locked", locked(context)).put("posted", posted))
            } finally {
                pending.finish()
            }
        }
    }
}
