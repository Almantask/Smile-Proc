package com.example.smileproc.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dlat = Math.toRadians(lat2 - lat1)
        val dlon = Math.toRadians(lon2 - lon1)
        val a = sin(dlat / 2).let { it * it } + cos(p1) * cos(p2) * sin(dlon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
        return EARTH_RADIUS_M * c
    }
}
