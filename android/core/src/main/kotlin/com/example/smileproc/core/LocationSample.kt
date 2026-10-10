package com.example.smileproc.core

import java.time.Instant

enum class LocationKind(val wire: String) {
    FRESH("fresh"),
    CACHED("cached");

    companion object {
        fun fromWire(value: String): LocationKind =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("location kind")
    }
}

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracy: Double,
    val capturedAt: Instant,
    val kind: LocationKind,
)
