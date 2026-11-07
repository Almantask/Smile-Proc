package com.example.smileproc.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.StorageState
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Debug-only broadcasts (`scripts/emu/fire.sh`), guarded by `android.permission.DUMP`.
 *
 * - `SEAL_TEST_ENTRY` (M3): seals and persists a synthetic check-in via [AppContainer] using
 *   `goAsync()`, proving `KeystoreSealer` + Room save while the device is locked.
 * - `FIRE_REMINDER`, `FIRE_RESTORE` (wired in M5) and `SEED_DEMO`, `CLEAR_DEMO` (wired in M8).
 */
class DebugEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SEAL_TEST_ENTRY -> handleSealTestEntry(context.applicationContext)
            else -> SafeLog.event(SafeLog.Event.DEBUG_EVENT_NOT_WIRED)
        }
    }

    private fun handleSealTestEntry(appContext: Context) {
        val container = SmileProcApplication.container(appContext)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (val state = container.awaitState()) {
                    is StorageState.Ready -> {
                        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                        val entry = Entry(
                            id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
                            createdAt = now,
                            updatedAt = now,
                            timeZoneID = ZoneId.systemDefault().id,
                            mood = Mood.OKAY,
                            tags = emptyList(),
                            comment = null,
                            location = null,
                            source = EntrySource.NOTIFICATION,
                        )
                        state.storage.entryRepository.save(entry)
                        SafeLog.event(SafeLog.Event.DEBUG_SEAL_SAVED)
                    }
                    else -> {
                        SafeLog.event(SafeLog.Event.DEBUG_SEAL_FAILED)
                    }
                }
            } catch (e: Exception) {
                SafeLog.failure(SafeLog.Event.DEBUG_SEAL_FAILED, e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEAL_TEST_ENTRY = "com.example.smileproc.debug.SEAL_TEST_ENTRY"
    }
}
