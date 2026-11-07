package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LocationAcceptanceTest {
    private val now = Instant.parse("2026-10-12T13:05:00.000Z")

    private fun candidate(acc: Double, secondsAgo: Long = 0L, lat: Double = 45.4642, lon: Double = 9.19) =
        RawLocationCandidate(lat, lon, acc, now.minusSeconds(secondsAgo))

    @Test
    fun offOrUnpermittedSkipsRequestsAndReturnsNull() {
        val fixes = listOf(candidate(10.0))
        assertFalse(LocationAcceptance.shouldRequestFix(captureLocationEnabled = false, hasPermission = true, deviceLocationEnabled = true))
        assertFalse(LocationAcceptance.shouldRequestFix(captureLocationEnabled = true, hasPermission = false, deviceLocationEnabled = true))
        assertFalse(LocationAcceptance.shouldRequestFix(captureLocationEnabled = true, hasPermission = true, deviceLocationEnabled = false))
        assertTrue(LocationAcceptance.shouldRequestFix(captureLocationEnabled = true, hasPermission = true, deviceLocationEnabled = true))

        assertNull(LocationAcceptance.resolveInApp(now, fixes, fixes, captureLocationEnabled = false))
        assertNull(LocationAcceptance.resolveInApp(now, fixes, fixes, hasPermission = false))
        assertNull(LocationAcceptance.resolveInApp(now, fixes, fixes, deviceLocationEnabled = false))
        assertNull(LocationAcceptance.resolveNotificationTap(now, fixes, captureLocationEnabled = false))
        assertNull(LocationAcceptance.resolveNotificationTap(now, fixes, hasPermission = false))
        assertNull(LocationAcceptance.resolveNotificationTap(now, fixes, deviceLocationEnabled = false))
    }

    @Test
    fun inAppAcceptsFirstFixAtOrBelow100MetersAsFresh() {
        val first150 = candidate(150.0, secondsAgo = 8)
        val first95 = candidate(95.0, secondsAgo = 5, lat = 45.47)
        val later20 = candidate(20.0, secondsAgo = 2, lat = 45.48)

        val result = LocationAcceptance.resolveInApp(now, listOf(first150, first95, later20), emptyList())
        assertEquals(LocationKind.FRESH, result?.kind)
        assertEquals(95.0, result!!.horizontalAccuracy, 0.0)
        assertEquals(45.47, result.latitude, 0.0)
    }

    @Test
    fun inAppTimeoutUsesBestObservedFixUpTo200MetersElseRecentLastKnown() {
        val observed = listOf(candidate(180.0, secondsAgo = 6), candidate(135.0, secondsAgo = 3), candidate(220.0, secondsAgo = 1))
        val timeoutFresh = LocationAcceptance.resolveInApp(now, observed, listOf(candidate(15.0, secondsAgo = 60)))
        assertEquals(LocationKind.FRESH, timeoutFresh?.kind)
        assertEquals(135.0, timeoutFresh!!.horizontalAccuracy, 0.0)

        // All observed fixes > 200 m -> fall back to last known <= 10 min and <= 200 m as cached
        val fallbackCached = LocationAcceptance.resolveInApp(
            now = now,
            observedFixes = listOf(candidate(201.0, secondsAgo = 2)),
            lastKnownCandidates = listOf(
                candidate(30.0, secondsAgo = 601), // > 10 min -> ignored
                candidate(200.0, secondsAgo = 600), // exact 10 min & 200 m -> eligible
            ),
        )
        assertEquals(LocationKind.CACHED, fallbackCached?.kind)
        assertEquals(200.0, fallbackCached!!.horizontalAccuracy, 0.0)

        // Stale or inaccurate last known -> null
        assertNull(
            LocationAcceptance.resolveInApp(
                now = now,
                observedFixes = emptyList(),
                lastKnownCandidates = listOf(candidate(50.0, secondsAgo = 601), candidate(205.0, secondsAgo = 30)),
            ),
        )
    }

    @Test
    fun notificationTapUsesLastKnownOnlyWithin10MinutesAnd200Meters() {
        val result = LocationAcceptance.resolveNotificationTap(
            now = now,
            lastKnownCandidates = listOf(
                candidate(120.0, secondsAgo = 120, lat = 45.46),
                candidate(45.0, secondsAgo = 300, lat = 45.49),
                candidate(10.0, secondsAgo = 601, lat = 45.50),
            ),
        )
        assertEquals(LocationKind.CACHED, result?.kind)
        assertEquals(45.0, result!!.horizontalAccuracy, 0.0)
        assertEquals(45.49, result.latitude, 0.0)
    }
}
