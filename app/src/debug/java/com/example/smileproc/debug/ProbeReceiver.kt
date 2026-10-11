package com.example.smileproc.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog

/**
 * Writes files/probe.json for scripts/emu/probe.sh: counts only, never content.
 * M0 stub: no database yet, so every count is null and "stub" is true. M3 and M5 fill it in.
 */
class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val probe = """
            {"stub": true, "entries": null, "perMood": null, "perSource": null, "located": null,
             "machineState": null, "lastTestResult": null}
        """.trimIndent()
        try {
            context.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(probe.toByteArray()) }
            SafeLog.event(SafeLog.Event.PROBE_WRITTEN)
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.PROBE_FAILED, e)
        }
    }

    companion object {
        const val FILE = "probe.json"
    }
}
