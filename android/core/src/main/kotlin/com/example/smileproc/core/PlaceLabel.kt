package com.example.smileproc.core

import java.time.Instant

enum class PlaceKind(val wire: String) {
    HOME("home"),
    WORK("work"),
    OTHER("other");

    companion object {
        fun fromWire(value: String): PlaceKind =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("place kind")
    }
}

data class PlaceLabel(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val anchorLatitude: Double,
    val anchorLongitude: Double,
    val createdAt: Instant,
)
