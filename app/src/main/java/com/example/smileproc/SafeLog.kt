package com.example.smileproc

import android.util.Log

/**
 * The only logger in the app. It logs a fixed event name and, for failures, the exception
 * type: never moods, tags, comments, coordinates, place names or exception messages.
 */
object SafeLog {
    private const val TAG = "SmileProc"

    enum class Event {
        DEBUG_EVENT_NOT_WIRED,
        PROBE_WRITTEN,
        PROBE_FAILED,
    }

    fun event(event: Event) {
        Log.i(TAG, event.name)
    }

    fun failure(event: Event, error: Throwable) {
        Log.w(TAG, "${event.name}: ${error.javaClass.name}")
    }
}
