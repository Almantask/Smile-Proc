package com.example.smileproc.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.smileproc.SafeLog
import com.example.smileproc.core.LocationAcceptance
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.RawLocationCandidate
import com.example.smileproc.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.resume

interface LocationClient {
    suspend fun captureInAppFix(): LocationSample?
    suspend fun getLastKnownFix(): LocationSample?
}

class PlatformLocationClient(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.systemUTC(),
) : LocationClient {

    private val appContext: Context = context.applicationContext
    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override suspend fun captureInAppFix(): LocationSample? {
        val captureEnabled = settingsRepository.captureLocation.first()
        val hasPermission = hasLocationPermission()
        val deviceEnabled = isDeviceLocationEnabled()

        if (!LocationAcceptance.shouldRequestFix(captureEnabled, hasPermission, deviceEnabled)) {
            return null
        }

        val lm = locationManager ?: return null
        val now = clock.instant()
        val observedFixes = mutableListOf<RawLocationCandidate>()

        try {
            val freshFix = withTimeoutOrNull(LocationAcceptance.IN_APP_TIMEOUT.toMillis()) {
                requestCurrentPlatformLocation(lm)
            }
            if (freshFix != null) {
                observedFixes.add(freshFix.toCandidate())
            }
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.LOCATION_FIX_FAILED, e)
        }

        val lastKnown = queryLastKnownLocations(lm)
        val resolved = LocationAcceptance.resolveInApp(
            now = now,
            observedFixes = observedFixes,
            lastKnownCandidates = lastKnown,
            captureLocationEnabled = captureEnabled,
            hasPermission = hasPermission,
            deviceLocationEnabled = deviceEnabled,
        )

        if (resolved != null) {
            SafeLog.event(SafeLog.Event.LOCATION_FIX_RECEIVED)
        }
        return resolved
    }

    override suspend fun getLastKnownFix(): LocationSample? {
        val captureEnabled = settingsRepository.captureLocation.first()
        val hasPermission = hasLocationPermission()
        val deviceEnabled = isDeviceLocationEnabled()

        if (!LocationAcceptance.shouldRequestFix(captureEnabled, hasPermission, deviceEnabled)) {
            return null
        }

        val lm = locationManager ?: return null
        val now = clock.instant()
        val lastKnown = queryLastKnownLocations(lm)
        return LocationAcceptance.resolveNotificationTap(
            now = now,
            lastKnownCandidates = lastKnown,
            captureLocationEnabled = captureEnabled,
            hasPermission = hasPermission,
            deviceLocationEnabled = deviceEnabled,
        )
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    fun isDeviceLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentPlatformLocation(lm: LocationManager): Location? {
        val provider = selectBestProvider(lm) ?: return null
        return suspendCancellableCoroutine { cont ->
            val cancellationSignal = CancellationSignal()
            cont.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    lm,
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    if (cont.isActive) {
                        cont.resume(location)
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryLastKnownLocations(lm: LocationManager): List<RawLocationCandidate> {
        val candidates = mutableListOf<RawLocationCandidate>()
        val providers = lm.allProviders
        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) {
                    candidates.add(loc.toCandidate())
                }
            } catch (_: Exception) {
                // Ignore providers that throw permission or security errors
            }
        }
        return candidates
    }

    private fun selectBestProvider(lm: LocationManager): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.allProviders.contains(LocationManager.FUSED_PROVIDER)) {
            if (LocationManagerCompat.hasProvider(lm, LocationManager.FUSED_PROVIDER)) {
                return LocationManager.FUSED_PROVIDER
            }
        }
        if (LocationManagerCompat.hasProvider(lm, LocationManager.GPS_PROVIDER) &&
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        ) {
            return LocationManager.GPS_PROVIDER
        }
        if (LocationManagerCompat.hasProvider(lm, LocationManager.NETWORK_PROVIDER) &&
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            return LocationManager.NETWORK_PROVIDER
        }
        if (LocationManagerCompat.hasProvider(lm, LocationManager.PASSIVE_PROVIDER)) {
            return LocationManager.PASSIVE_PROVIDER
        }
        return lm.allProviders.firstOrNull()
    }

    private fun Location.toCandidate(): RawLocationCandidate =
        RawLocationCandidate(
            latitude = latitude,
            longitude = longitude,
            horizontalAccuracy = if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
            capturedAt = Instant.ofEpochMilli(time),
        )
}
