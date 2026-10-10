package com.example.smileproc.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.LocationSample
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class LocationCapture(private val context: Context) {
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(): LocationSample? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        var best: Location? = null
        for (provider in providers(manager)) {
            val location = try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } ?: continue
            if (best == null || location.accuracy < best.accuracy) best = location
        }
        val chosen = best ?: return null
        if (chosen.accuracy > CACHED_MAX_METERS) return null
        val age = System.currentTimeMillis() - chosen.time
        if (age > CACHED_MAX_AGE_MS) return null
        return sample(chosen, LocationKind.CACHED)
    }

    @SuppressLint("MissingPermission")
    suspend fun captureFresh(timeoutMs: Long): LocationSample? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return lastKnown()
        val provider = providers(manager).firstOrNull() ?: return lastKnown()
        val location = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        if (location != null && location.accuracy <= FRESH_MAX_METERS) {
            return sample(location, LocationKind.FRESH)
        }
        if (location != null && location.accuracy <= CACHED_MAX_METERS) {
            return sample(location, LocationKind.FRESH)
        }
        return lastKnown()
    }

    private fun providers(manager: LocationManager): List<String> {
        val names = ArrayList<String>(3)
        if (Build.VERSION.SDK_INT >= 31) names.add(LocationManager.FUSED_PROVIDER)
        names.add(LocationManager.GPS_PROVIDER)
        names.add(LocationManager.NETWORK_PROVIDER)
        return names.filter { provider ->
            try {
                manager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun sample(location: Location, kind: LocationKind): LocationSample = LocationSample(
        latitude = location.latitude,
        longitude = location.longitude,
        horizontalAccuracy = location.accuracy.toDouble(),
        capturedAt = Instant.ofEpochMilli(location.time),
        kind = kind,
    )

    companion object {
        const val FRESH_MAX_METERS = 100f
        const val CACHED_MAX_METERS = 200f
        const val CACHED_MAX_AGE_MS = 10 * 60 * 1000L
        const val IN_APP_TIMEOUT_MS = 10_000L
    }
}
