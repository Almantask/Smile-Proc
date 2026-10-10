package com.example.smileproc.core

enum class EntrySource(val wire: String) {
    APP("app"),
    LOCK_SCREEN_WIDGET("lockScreenWidget"),
    HOME_SCREEN_WIDGET("homeScreenWidget"),
    NOTIFICATION("notification");

    companion object {
        fun fromWire(value: String): EntrySource =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("source")
    }
}
