package com.example.smileproc.core

import java.time.Duration
import java.time.Instant

/** Raw platform fix candidate evaluated by [LocationAcceptance]. */
data class RawLocationCandidate(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracy: Double,
    val capturedAt: Instant,
)

/**
 * Pure location acceptance rules (`docs/spec/logic.md` › Location acceptance).
 *
 * - Location off or permission missing -> `null`, no request made.
 * - In the app: accept first fix with accuracy <= 100 m (`fresh`); at the 10 s timeout,
 *   best fix seen if <= 200 m (`fresh`); else last known if <= 10 min old and <= 200 m (`cached`);
 *   else `null`.
 * - Notification tap: last known only, if <= 10 min old and <= 200 m (`cached`); else `null`.
 */
object LocationAcceptance {
    const val IMMEDIATE_FRESH_MAX_ACCURACY_METERS = 100.0
    const val FALLBACK_MAX_ACCURACY_METERS = 200.0
    val IN_APP_TIMEOUT: Duration = Duration.ofSeconds(10)
    val MAX_LAST_KNOWN_AGE: Duration = Duration.ofMinutes(10)

    fun shouldRequestFix(
        captureLocationEnabled: Boolean,
        hasPermission: Boolean,
        deviceLocationEnabled: Boolean,
    ): Boolean = captureLocationEnabled && hasPermission && deviceLocationEnabled

    fun isImmediateInAppMatch(candidate: RawLocationCandidate): Boolean =
        candidate.horizontalAccuracy.isFinite() &&
            candidate.horizontalAccuracy >= 0.0 &&
            candidate.horizontalAccuracy <= IMMEDIATE_FRESH_MAX_ACCURACY_METERS

    fun resolveInApp(
        now: Instant,
        observedFixes: List<RawLocationCandidate>,
        lastKnownCandidates: List<RawLocationCandidate>,
        captureLocationEnabled: Boolean = true,
        hasPermission: Boolean = true,
        deviceLocationEnabled: Boolean = true,
    ): LocationSample? {
        if (!shouldRequestFix(captureLocationEnabled, hasPermission, deviceLocationEnabled)) {
            return null
        }

        val immediate = observedFixes.firstOrNull(::isImmediateInAppMatch)
        if (immediate != null) {
            return immediate.toSample(LocationKind.FRESH)
        }

        val bestObserved = observedFixes
            .filter {
                it.horizontalAccuracy.isFinite() &&
                    it.horizontalAccuracy >= 0.0 &&
                    it.horizontalAccuracy <= FALLBACK_MAX_ACCURACY_METERS
            }
            .minWithOrNull(compareBy<RawLocationCandidate> { it.horizontalAccuracy }.thenByDescending { it.capturedAt })
        if (bestObserved != null) {
            return bestObserved.toSample(LocationKind.FRESH)
        }

        return selectBestLastKnown(now, lastKnownCandidates)
    }

    fun resolveNotificationTap(
        now: Instant,
        lastKnownCandidates: List<RawLocationCandidate>,
        captureLocationEnabled: Boolean = true,
        hasPermission: Boolean = true,
        deviceLocationEnabled: Boolean = true,
    ): LocationSample? {
        if (!shouldRequestFix(captureLocationEnabled, hasPermission, deviceLocationEnabled)) {
            return null
        }
        return selectBestLastKnown(now, lastKnownCandidates)
    }

    private fun selectBestLastKnown(
        now: Instant,
        candidates: List<RawLocationCandidate>,
    ): LocationSample? {
        val best = candidates
            .filter { candidate ->
                candidate.horizontalAccuracy.isFinite() &&
                    candidate.horizontalAccuracy >= 0.0 &&
                    candidate.horizontalAccuracy <= FALLBACK_MAX_ACCURACY_METERS &&
                    isRecentEnough(now, candidate.capturedAt)
            }
            .minWithOrNull(compareBy<RawLocationCandidate> { it.horizontalAccuracy }.thenByDescending { it.capturedAt })
        return best?.toSample(LocationKind.CACHED)
    }

    private fun isRecentEnough(now: Instant, capturedAt: Instant): Boolean {
        val age = Duration.between(capturedAt, now)
        return !age.isNegative && age <= MAX_LAST_KNOWN_AGE
    }

    private fun RawLocationCandidate.toSample(kind: LocationKind): LocationSample = LocationSample(
        latitude = latitude,
        longitude = longitude,
        horizontalAccuracy = horizontalAccuracy,
        capturedAt = capturedAt,
        kind = kind,
    )
}
