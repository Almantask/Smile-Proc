package com.example.smileproc.spikes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * adb control for the emulator runs (scripts/emu/spikes.sh ctl). Guarded by DUMP in the manifest.
 *   POST --es variant alerting|silent · CANCEL · SET [--ez standing B] [--es mode lastKnown|fgs]
 *   SCHEDULE_REMINDER --el seconds N | --es at HH:MM · CLEAR · PROBE (writes files/probe.json)
 * Things that need the app in the foreground (a current fix, the FGS from a button) go through
 * SpikeActivity's `cmd` extra instead.
 */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        spikeScope.launch {
            try {
                handle(context, intent.action?.substringAfterLast('.') ?: "", intent)
            } catch (e: Exception) {
                runCatching { Records.add(context, "error", JSONObject().put("during", "control").put("exception", e.javaClass.simpleName)) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String, intent: Intent) {
        val prefs = SpikePrefs(context)
        when (action) {
            "POST" -> SpikeNotifier.render(
                context,
                if (intent.getStringExtra("variant") == "alerting") SpikeNotifier.State.FACES_ALERTING
                else SpikeNotifier.State.FACES_SILENT,
            )
            "CANCEL" -> SpikeNotifier.cancel(context)
            "SET" -> {
                if (intent.hasExtra("standing")) prefs.standing = intent.getBooleanExtra("standing", true)
                intent.getStringExtra("mode")?.let { prefs.mode = it }
            }
            "SCHEDULE_REMINDER" -> {
                val at = intent.getStringExtra("at")
                if (at != null) ReminderReceiver.scheduleAt(context, at)
                else ReminderReceiver.scheduleIn(context, intent.getLongExtra("seconds", 180))
            }
            "CLEAR" -> Records.clear(context)
            "PROBE" -> context.openFileOutput("probe.json", Context.MODE_PRIVATE).use {
                it.write(Records.probe(context).toString(2).toByteArray())
            }
        }
    }
}
