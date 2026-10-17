package com.example.smileproc.spikes

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A2b: a short location foreground service. It reuses ID 1 with whatever the check-in
 * notification shows (so there is never a second notification), asks for a current fix with a
 * 10 s limit, records the result against the tap, then detaches the notification and stops.
 */
class LocationFgs : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tapId = intent?.getStringExtra(EXTRA_TAP_ID)
        val origin = intent?.getStringExtra(EXTRA_ORIGIN) ?: "unknown"
        val started = SystemClock.elapsedRealtime()
        val wasLocked = locked(this)
        try {
            ServiceCompat.startForeground(
                this, SpikeNotifier.ID_CHECKIN, SpikeNotifier.build(this, SpikeNotifier.lastState(this)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            spikeScope.launch {
                Records.add(
                    this@LocationFgs, "fgs",
                    JSONObject().put("tapId", tapId ?: JSONObject.NULL).put("origin", origin).put("started", false)
                        .put("locked", wasLocked).put("exception", e.javaClass.simpleName),
                )
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        spikeScope.launch {
            try {
                val location = Locator.current(this@LocationFgs, TIMEOUT_MS)
                Records.add(
                    this@LocationFgs, "fgs",
                    JSONObject().put("tapId", tapId ?: JSONObject.NULL).put("origin", origin).put("started", true)
                        .put("locked", wasLocked).put("location", location)
                        .put("totalMs", SystemClock.elapsedRealtime() - started),
                )
            } finally {
                ServiceCompat.stopForeground(this@LocationFgs, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_TAP_ID = "tapId"
        private const val EXTRA_ORIGIN = "origin"
        private const val TIMEOUT_MS = 10_000L

        fun intent(context: Context, tapId: String?, origin: String): Intent =
            Intent(context, LocationFgs::class.java).putExtra(EXTRA_TAP_ID, tapId).putExtra(EXTRA_ORIGIN, origin)
    }
}
