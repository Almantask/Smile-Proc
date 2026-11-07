package com.example.smileproc.core

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS-84 coordinate pair used by [Geo] and [PlaceResolver]. */
@Serializable
data class LatLon(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude: $latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude: $longitude" }
    }
}

/** Geographic distance and centroid utilities (`docs/spec/logic.md` › Place clustering). */
object Geo {
    const val EARTH_RADIUS_METERS: Double = 6_371_000.0

    /** Haversine great-circle distance in metres (`R = 6,371,000 m`). */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)

        val sinHalfPhi = sin(dPhi / 2.0)
        val sinHalfLambda = sin(dLambda / 2.0)
        val a = sinHalfPhi.pow(2) + cos(phi1) * cos(phi2) * sinHalfLambda.pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
        return EARTH_RADIUS_METERS * c
    }

    fun haversineMeters(a: LatLon, b: LatLon): Double =
        haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Arithmetic mean of member coordinates (`docs/spec/logic.md` › Place clustering). */
    fun centroid(points: Collection<LatLon>): LatLon {
        require(points.isNotEmpty()) { "Cannot compute centroid of empty point collection" }
        var sumLat = 0.0
        var sumLon = 0.0
        for (p in points) {
            sumLat += p.latitude
            sumLon += p.longitude
        }
        val n = points.size.toDouble()
        return LatLon(latitude = sumLat / n, longitude = sumLon / n)
    }
}
