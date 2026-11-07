package com.example.smileproc.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.StorageState
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Writes `files/probe.json` for `scripts/emu/probe.sh`: counts and structural state only, never
 * moods of individual entries, tags, comments, coordinates, or place names.
 */
class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val container = SmileProcApplication.container(appContext)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val machineState = container.settingsRepository.machineState.first().toString()
                val lastTestSuccess = container.settingsRepository.lastTestSuccess.first() != null
                val dismissed = container.settingsRepository.dismissed.first()
                val standing = container.settingsRepository.standing.first()

                val probeJson = when (val state = container.awaitState()) {
                    is StorageState.Ready -> {
                        val entries = state.storage.entryRepository.getAllOldestFirst()
                        val low = entries.count { it.mood == Mood.LOW }
                        val okay = entries.count { it.mood == Mood.OKAY }
                        val good = entries.count { it.mood == Mood.GOOD }
                        val fromApp = entries.count { it.source == EntrySource.APP }
                        val fromNotif = entries.count { it.source == EntrySource.NOTIFICATION }
                        val located = entries.count { it.location != null }
                        buildString {
                            append("{\"stub\": false")
                            append(", \"storageState\": \"ready\"")
                            append(", \"entries\": ").append(entries.size)
                            append(", \"perMood\": {\"low\": ").append(low)
                            append(", \"okay\": ").append(okay)
                            append(", \"good\": ").append(good).append('}')
                            append(", \"perSource\": {\"app\": ").append(fromApp)
                            append(", \"notification\": ").append(fromNotif).append('}')
                            append(", \"located\": ").append(located)
                            append(", \"machineState\": \"").append(machineState).append('"')
                            append(", \"lastTestResult\": ").append(lastTestSuccess)
                            append(", \"dismissed\": ").append(dismissed)
                            append(", \"standing\": ").append(standing).append('}')
                        }
                    }
                    StorageState.NeedsRecovery -> {
                        "{\"stub\": false, \"storageState\": \"needs_recovery\", \"entries\": null, " +
                            "\"perMood\": null, \"perSource\": null, \"located\": null, " +
                            "\"machineState\": \"$machineState\", \"lastTestResult\": $lastTestSuccess, " +
                            "\"dismissed\": $dismissed, \"standing\": $standing}"
                    }
                    StorageState.Loading -> {
                        "{\"stub\": false, \"storageState\": \"loading\", \"entries\": null, " +
                            "\"perMood\": null, \"perSource\": null, \"located\": null, " +
                            "\"machineState\": \"$machineState\", \"lastTestResult\": $lastTestSuccess, " +
                            "\"dismissed\": $dismissed, \"standing\": $standing}"
                    }
                }
                appContext.openFileOutput(FILE, Context.MODE_PRIVATE).use {
                    it.write(probeJson.toByteArray(Charsets.UTF_8))
                }
                SafeLog.event(SafeLog.Event.PROBE_WRITTEN)
            } catch (e: Exception) {
                SafeLog.failure(SafeLog.Event.PROBE_FAILED, e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val FILE = "probe.json"
    }
}
