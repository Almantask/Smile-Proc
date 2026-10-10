package com.example.smileproc.core

enum class Mood(val value: Int) {
    LOW(1),
    OKAY(2),
    GOOD(3);

    companion object {
        fun fromValue(value: Int): Mood =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("mood")
    }
}
