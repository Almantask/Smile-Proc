package com.example.smileproc.spikes

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.location.LocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Process-wide scope for receivers (goAsync) and the service. */
val spikeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

object Records {
    /** Seals [data] (plus kind and time) and inserts it. Returns the row id. Throws on failure. */
    suspend fun add(context: Context, kind: String, data: JSONObject): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        data.put("kind", kind).put("at", now)
        val payload = SpikeSealer.seal(id, data.toString().toByteArray())
        SpikeDb.get(context).records().insert(RecordRow(id, now, kind, payload))
        return id
    }

    /** Every record, newest first, opened. A row that fails to open shows the exception type. */
    suspend fun all(context: Context): List<JSONObject> =
        SpikeDb.get(context).records().all().map { row ->
            try {
                JSONObject(String(SpikeSealer.open(row.id, row.payload))).put("id", row.id)
            } catch (e: Exception) {
                JSONObject().put("id", row.id).put("kind", row.kind).put("at", row.createdAt)
                    .put("openFailed", e.javaClass.simpleName)
            }
        }

    suspend fun clear(context: Context) = SpikeDb.get(context).records().clear()

    /** files/probe.json for scripts/emu/spikes.sh probe: device state plus the last 40 records. */
    suspend fun probe(context: Context): JSONObject {
        val records = all(context)
        val counts = JSONObject()
        records.groupingBy { it.optString("kind") }.eachCount().forEach { (k, n) -> counts.put(k, n) }
        val prefs = SpikePrefs(context)
        val lm = context.getSystemService(LocationManager::class.java)
        return JSONObject()
            .put("api", Build.VERSION.SDK_INT)
            .put("standing", prefs.standing)
            .put("mode", prefs.mode)
            .put("lastState", prefs.lastState ?: JSONObject.NULL)
            .put("reminderAt", prefs.reminderAt)
            .put("notificationsEnabled", NotificationManagerCompat.from(context).areNotificationsEnabled())
            .put("fine", granted(context, Manifest.permission.ACCESS_FINE_LOCATION))
            .put("coarse", granted(context, Manifest.permission.ACCESS_COARSE_LOCATION))
            .put("locationEnabled", LocationManagerCompat.isLocationEnabled(lm))
            .put("locked", locked(context))
            .put("idle", context.getSystemService(PowerManager::class.java).isDeviceIdleMode)
            .put("counts", counts)
            .put("records", JSONArray(records.take(40)))
    }
}

fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun locked(context: Context) = context.getSystemService(KeyguardManager::class.java).isDeviceLocked
