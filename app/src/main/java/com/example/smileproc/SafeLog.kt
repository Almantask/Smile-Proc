package com.example.smileproc

import android.util.Log
import com.example.smileproc.data.ErrorLogStore

/**
 * The only logger in the app. It logs a fixed event name and, for failures, the exception
 * type: never moods, tags, comments, coordinates, place names or exception messages.
 *
 * Persists failure events to local sealed storage via [ErrorLogStore].
 */
object SafeLog {
    private const val TAG = "SmileProc"

    @Volatile
    private var store: ErrorLogStore? = null

    fun init(store: ErrorLogStore) {
        this.store = store
    }

    fun clearStoreForTesting() {
        this.store = null
    }

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
        EXPORT_SAVED,
        EXPORT_FAILED,
        IMPORT_RESTORED,
        IMPORT_FAILED,
        DELETE_ALL_COMPLETED,
        DELETE_ALL_FAILED,
        APP_LOCK_AUTHENTICATED,
        APP_LOCK_FAILED,
        ERROR_LOGS_EXPORTED,
        ERROR_LOGS_EXPORT_FAILED,
    }

    fun event(event: Event) {
        Log.i(TAG, event.name)
        if (event.name.endsWith("_FAILED") || event == Event.STORAGE_KEY_MISSING) {
            try {
                store?.record(event, null)
            } catch (_: Throwable) {
            }
        }
    }

    fun failure(event: Event, error: Throwable) {
        Log.w(TAG, "${event.name}: ${error.javaClass.name}")
        try {
            store?.record(event, error)
        } catch (_: Throwable) {
        }
    }
}
