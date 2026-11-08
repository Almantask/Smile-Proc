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
        DEBUG_SEAL_SAVED,
        DEBUG_SEAL_FAILED,
        PROBE_WRITTEN,
        PROBE_FAILED,
        STORAGE_KEY_MISSING,
        STORAGE_SEAL_FAILED,
        STORAGE_OPEN_FAILED,
        STORAGE_START_FRESH,
        STORAGE_START_FRESH_FAILED,
        LOCATION_FIX_RECEIVED,
        LOCATION_FIX_FAILED,
        ENTRY_SAVED,
        ENTRY_UPDATED,
        ENTRY_DELETED,
        NOTIFY_FAILED,
        ALARM_SCHEDULE_FAILED,
        NOTIFICATION_TAP_SAVED,
        NOTIFICATION_TAP_FAILED,
        REMINDER_FIRED,
        TEST_REQUESTED,
        TEST_COMPLETED,
        DEBUG_DEMO_SEEDED,
        DEBUG_DEMO_CLEARED,
    }

    fun event(event: Event) {
        Log.i(TAG, event.name)
    }

    fun failure(event: Event, error: Throwable) {
        Log.w(TAG, "${event.name}: ${error.javaClass.name}")
    }
}
