package com.example.smileproc.spikes

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Location results for spike A2, described without coordinates: outcome (fresh = at most 10 s
 * old, cached = older, none), provider, accuracy, age, plus any exception type.
 */
object Locator {
    private const val FRESH_S = 10.0

    fun hasPermission(context: Context) =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** A2a: the newest getLastKnownLocation across every provider. Never waits. */
    fun lastKnown(context: Context): JSONObject {
        val out = JSONObject().put("source", "lastKnown")
        if (!hasPermission(context)) return out.put("outcome", "noPermission")
        val lm = context.getSystemService(LocationManager::class.java)
        out.put("locationEnabled", LocationManagerCompat.isLocationEnabled(lm))
        val perProvider = JSONObject()
        var best: Location? = null
        for (provider in lm.allProviders) {
            try {
                val location = lm.getLastKnownLocation(provider)
                perProvider.put(provider, location?.let { round1(ageS(it)) } ?: JSONObject.NULL)
                if (location != null && (best == null || location.elapsedRealtimeNanos > best.elapsedRealtimeNanos)) {
                    best = location
                }
            } catch (e: Exception) {
                perProvider.put(provider, e.javaClass.simpleName)
            }
        }
        out.put("perProviderAgeS", perProvider)
        return describe(out, best)
    }

    /**
     * A2b and the in-app path (android.md › Location): getCurrentLocation on fused (12+), else gps,
     * else network, with a [timeoutMs] limit.
     */
    suspend fun current(context: Context, timeoutMs: Long): JSONObject {
        val out = JSONObject().put("source", "current")
        if (!hasPermission(context)) return out.put("outcome", "noPermission")
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = when {
            Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in lm.allProviders &&
                lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return out.put("outcome", "noProvider")
        }
        out.put("requestedProvider", provider)
        val started = SystemClock.elapsedRealtime()
        var location: Location? = null
        try {
            location = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        lm, provider, signal, ContextCompat.getMainExecutor(context),
                    ) { result -> if (cont.isActive) cont.resume(result) }
                }
            }
            if (location == null && SystemClock.elapsedRealtime() - started >= timeoutMs) out.put("timedOut", true)
        } catch (e: Exception) {
            out.put("exception", e.javaClass.simpleName)
        }
        out.put("waitMs", SystemClock.elapsedRealtime() - started)
        return describe(out, location)
    }

    private fun describe(out: JSONObject, location: Location?): JSONObject {
        if (location == null) return out.put("outcome", "none")
        val age = ageS(location)
        return out.put("outcome", if (age <= FRESH_S) "fresh" else "cached")
            .put("provider", location.provider ?: JSONObject.NULL)
            .put("accuracyM", if (location.hasAccuracy()) round1(location.accuracy.toDouble()) else JSONObject.NULL)
            .put("ageS", round1(age))
    }

    private fun ageS(location: Location) =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1e9

    private fun round1(value: Double) = Math.round(value * 10) / 10.0
}
