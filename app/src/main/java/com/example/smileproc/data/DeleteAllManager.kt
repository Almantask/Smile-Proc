package com.example.smileproc.data

import android.content.Context
import com.example.smileproc.AppContainer
import com.example.smileproc.SafeLog
import com.example.smileproc.StorageState
import com.example.smileproc.notify.CheckInNotifier
import com.example.smileproc.notify.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles "Delete all data" wipe coordination
 * (`docs/spec/android.md` › Delete all).
 *
 * Closes and deletes the database, deletes the Keystore entry, clears DataStore,
 * cancels all alarms, cancels ID_CHECKIN, returning the app to clean onboarding state.
 */
object DeleteAllManager {
    suspend fun deleteAll(
        context: Context,
        container: AppContainer,
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Cancel notification
            CheckInNotifier.cancel(context)

            // 2. Cancel all alarms
            ReminderScheduler.cancelAll(context)

            // 3. Clear repositories if accessible
            val state = container.awaitState()
            if (state is StorageState.Ready) {
                state.storage.entryRepository.deleteAll()
                state.storage.placeRepository.deleteAll()
                state.storage.weeklyCardRepository.deleteAll()
            }

            // 4. Clear DataStore preferences
            container.settingsRepository.clearAll()

            // 5. Reset AppLock in memory
            com.example.smileproc.security.AppLockManager.resetForTesting()

            // 6. Start fresh storage (deletes DB, replaces key, creates clean empty DB)
            container.startFresh()

            SafeLog.event(SafeLog.Event.DELETE_ALL_COMPLETED)
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.DELETE_ALL_FAILED, e)
            throw e
        }
    }
}
