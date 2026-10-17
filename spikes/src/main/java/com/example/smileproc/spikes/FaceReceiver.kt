package com.example.smileproc.spikes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Face buttons, the 60 s restore and the delete intent (android.md › Saving from a notification
 * tap). A tap saves first, with last known location only; in A2 mode "fgs" it then starts
 * [LocationFgs] for a fresh fix.
 */
class FaceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        spikeScope.launch {
            try {
                when (intent.action) {
                    ACTION_RATE -> rate(context, intent.getIntExtra(EXTRA_MOOD, 0))
                    ACTION_RESTORE -> SpikeNotifier.render(context, SpikeNotifier.State.FACES_SILENT)
                    ACTION_DISMISSED -> Records.add(context, "dismiss", JSONObject().put("locked", locked(context)))
                }
            } catch (e: Exception) {
                runCatching { Records.add(context, "error", JSONObject().put("exception", e.javaClass.simpleName)) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun rate(context: Context, mood: Int) {
        val started = SystemClock.elapsedRealtime()
        val wasLocked = locked(context)
        val tapId = try {
            val location = Locator.lastKnown(context)
            val data = JSONObject()
                .put("mood", mood)
                .put("locked", wasLocked)
                .put("location", location)
                .put("lastKnownMs", SystemClock.elapsedRealtime() - started)
            val id = Records.add(context, "tap", data)
            SpikeNotifier.render(context, SpikeNotifier.State.SAVED)
            id
        } catch (e: Exception) {
            SpikeNotifier.render(context, SpikeNotifier.State.FAILED)
            runCatching {
                Records.add(context, "error", JSONObject().put("during", "tap").put("exception", e.javaClass.simpleName))
            }
            null
        }
        if (tapId != null && SpikePrefs(context).mode == SpikePrefs.MODE_FGS) {
            try {
                ContextCompat.startForegroundService(context, LocationFgs.intent(context, tapId, "tap"))
            } catch (e: Exception) {
                Records.add(
                    context, "fgs",
                    JSONObject().put("tapId", tapId).put("origin", "tap").put("started", false)
                        .put("exception", e.javaClass.simpleName).put("locked", locked(context)),
                )
            }
        }
        scheduleRestore(context)
    }

    private fun scheduleRestore(context: Context) {
        context.getSystemService(AlarmManager::class.java).set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + RESTORE_MS,
            pending(context, ACTION_RESTORE, REQUEST_RESTORE),
        )
    }

    companion object {
        const val ACTION_RATE = "com.example.smileproc.spikes.RATE"
        const val ACTION_RESTORE = "com.example.smileproc.spikes.RESTORE"
        const val ACTION_DISMISSED = "com.example.smileproc.spikes.DISMISSED"
        const val EXTRA_MOOD = "mood"
        const val EXTRA_TEST = "test"
        private const val REQUEST_RESTORE = 21
        private const val RESTORE_MS = 60_000L

        fun pending(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode, Intent(context, FaceReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
