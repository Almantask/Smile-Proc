package com.example.smileproc.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.StorageState
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Handles check-in notification actions and timer events (`docs/spec/android.md` › Saving from a notification tap).
 *
 * Invariant: Not exported. Uses `goAsync()`, dispatches events to [CheckInMachine], applies effects,
 * persists state, and never blocks on a location fix.
 */
class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val container = SmileProcApplication.container(appContext)
        val pendingResult = goAsync()

        container.applicationScope.launch {
            try {
                handleIntent(appContext, intent)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    internal suspend fun handleIntent(context: Context, intent: Intent) {
        val event: CheckInMachine.Event = when (intent.action) {
            ACTION_RATE -> {
                val moodValue = intent.getIntExtra(EXTRA_MOOD, 2)
                val mood = try {
                    Mood.fromValue(moodValue)
                } catch (e: Exception) {
                    Mood.OKAY
                }
                val isTest = intent.getBooleanExtra(EXTRA_TEST, false)
                if (isTest) {
                    CheckInMachine.Event.Tap(mood = mood, saveSucceeded = true)
                } else {
                    val saveSucceeded = saveNotificationTapEntry(context, mood)
                    CheckInMachine.Event.Tap(mood = mood, saveSucceeded = saveSucceeded)
                }
            }
            ACTION_RESTORE -> CheckInMachine.Event.RestoreTimer
            ACTION_DISMISSED -> CheckInMachine.Event.Dismissed
            ACTION_TEST_TIMER -> CheckInMachine.Event.TestTimerFired
            else -> return
        }

        dispatchMachineEvent(context, event)
    }

    private suspend fun saveNotificationTapEntry(context: Context, mood: Mood): Boolean {
        val container = SmileProcApplication.container(context)
        return try {
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            val locationSample = try {
                container.locationClient.getLastKnownFix()
            } catch (e: Exception) {
                null
            }
            val entry = Entry(
                id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
                createdAt = now,
                updatedAt = now,
                timeZoneID = ZoneId.systemDefault().id,
                mood = mood,
                tags = emptyList(),
                comment = null,
                location = locationSample,
                source = EntrySource.NOTIFICATION,
            )
            when (val storageState = container.awaitState()) {
                is StorageState.Ready -> {
                    storageState.storage.entryRepository.save(entry)
                    container.settingsRepository.setLastNotificationEntry(entry.id, now.toEpochMilli())
                    SafeLog.event(SafeLog.Event.NOTIFICATION_TAP_SAVED)
                    true
                }
                else -> {
                    SafeLog.event(SafeLog.Event.NOTIFICATION_TAP_FAILED)
                    false
                }
            }
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.NOTIFICATION_TAP_FAILED, e)
            false
        }
    }

    companion object {
        const val ACTION_RATE = "com.example.smileproc.action.RATE"
        const val ACTION_RESTORE = "com.example.smileproc.action.RESTORE"
        const val ACTION_DISMISSED = "com.example.smileproc.action.DISMISSED"
        const val ACTION_TEST_TIMER = "com.example.smileproc.action.TEST_TIMER"

        const val EXTRA_MOOD = "mood"
        const val EXTRA_TEST = "test"

        suspend fun dispatchMachineEvent(context: Context, event: CheckInMachine.Event) {
            val container = SmileProcApplication.container(context)
            val settingsRepo = container.settingsRepository
            val standing = settingsRepo.standing.first()
            val dismissed = settingsRepo.dismissed.first()
            val currentMachineState = settingsRepo.machineState.first()

            val machineSettings = CheckInMachine.Settings(
                standing = standing,
                dismissed = dismissed,
            )
            val now = Instant.now()
            val transition = CheckInMachine.reduce(currentMachineState, machineSettings, event, now)

            for (effect in transition.effects) {
                when (effect) {
                    is CheckInMachine.Effect.PostNotification -> {
                        CheckInNotifier.render(context, effect.state, effect.ongoing)
                    }
                    is CheckInMachine.Effect.CancelNotification -> {
                        CheckInNotifier.cancel(context)
                    }
                    is CheckInMachine.Effect.ScheduleRestoreTimer -> {
                        CheckInNotifier.scheduleRestore(context, effect.delaySeconds)
                    }
                    is CheckInMachine.Effect.CancelRestoreTimer -> {
                        CheckInNotifier.cancelRestore(context)
                    }
                    is CheckInMachine.Effect.ScheduleTestTimer -> {
                        CheckInNotifier.scheduleTestTimer(context, effect.delaySeconds)
                    }
                    is CheckInMachine.Effect.RecordTestSuccess -> {
                        settingsRepo.recordTestSuccess(effect.completedAt)
                        SafeLog.event(SafeLog.Event.TEST_COMPLETED)
                    }
                    is CheckInMachine.Effect.RescheduleAlarms -> {
                        ReminderScheduler.scheduleNext(context)
                    }
                    is CheckInMachine.Effect.InsertEntry -> {
                        // Real taps are sealed and saved synchronously in saveNotificationTapEntry
                    }
                    is CheckInMachine.Effect.RememberRestingState -> {
                        // Retained in transition.settings
                    }
                }
            }

            settingsRepo.setMachineState(transition.state)
            settingsRepo.setDismissed(transition.settings.dismissed)
        }
    }
}
