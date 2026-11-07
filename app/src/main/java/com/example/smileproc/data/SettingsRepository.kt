package com.example.smileproc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import java.time.Instant

enum class TagPickerMode(val storageValue: String) {
    GRADUAL("gradual"),
    DETAILED("detailed");

    companion object {
        fun fromStorage(value: String?): TagPickerMode =
            if (value == DETAILED.storageValue) DETAILED else GRADUAL
    }
}

interface SettingsRepository {
    val captureLocation: Flow<Boolean>
    val onboardingCompleted: Flow<Boolean>
    val tagPickerMode: Flow<TagPickerMode>
    val lowRunDismissed: Flow<Boolean>
    val lowRunDismissedEntryId: Flow<String?>
    val standing: Flow<Boolean>
    val dismissed: Flow<Boolean>
    val machineState: Flow<CheckInMachine.State>
    val reminders: Flow<List<Reminder>>
    val lastTestSuccess: Flow<Instant?>

    suspend fun setCaptureLocation(enabled: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setTagPickerMode(mode: TagPickerMode)
    suspend fun setLowRunDismissed(dismissed: Boolean)
    suspend fun setLowRunDismissedEntryId(entryId: String?)
    suspend fun setStanding(enabled: Boolean)
    suspend fun setDismissed(dismissed: Boolean)
    suspend fun setMachineState(state: CheckInMachine.State)
    suspend fun setReminders(reminders: List<Reminder>)
    suspend fun recordTestSuccess(completedAt: Instant)
    suspend fun clearLastTestSuccess()
    suspend fun clearAll()
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val captureLocation: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_CAPTURE_LOCATION] ?: true }

    override val onboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    override val tagPickerMode: Flow<TagPickerMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> TagPickerMode.fromStorage(prefs[KEY_TAG_PICKER_MODE]) }

    override val lowRunDismissed: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_LOW_RUN_DISMISSED_ENTRY_ID] != null || (prefs[KEY_LOW_RUN_DISMISSED] ?: false) }

    override val lowRunDismissedEntryId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_LOW_RUN_DISMISSED_ENTRY_ID] }

    override val standing: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_STANDING] ?: true }

    override val dismissed: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_DISMISSED] ?: false }

    override val machineState: Flow<CheckInMachine.State> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[KEY_MACHINE_STATE]
            if (raw.isNullOrBlank()) {
                CheckInMachine.State.Hidden
            } else {
                try {
                    CanonicalJson.json.decodeFromString(CheckInMachine.State.serializer(), raw)
                } catch (e: Exception) {
                    CheckInMachine.State.Hidden
                }
            }
        }

    override val reminders: Flow<List<Reminder>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[KEY_REMINDERS]
            if (raw == null) {
                Reminder.DEFAULT_REMINDERS
            } else {
                try {
                    CanonicalJson.json.decodeFromString(ListSerializer(Reminder.serializer()), raw)
                } catch (e: Exception) {
                    Reminder.DEFAULT_REMINDERS
                }
            }
        }

    override val lastTestSuccess: Flow<Instant?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[KEY_LAST_TEST_SUCCESS]?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        }

    override suspend fun setCaptureLocation(enabled: Boolean) {
        dataStore.edit { it[KEY_CAPTURE_LOCATION] = enabled }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setTagPickerMode(mode: TagPickerMode) {
        dataStore.edit { it[KEY_TAG_PICKER_MODE] = mode.storageValue }
    }

    override suspend fun setLowRunDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LOW_RUN_DISMISSED] = dismissed
            if (!dismissed) {
                prefs.remove(KEY_LOW_RUN_DISMISSED_ENTRY_ID)
            }
        }
    }

    override suspend fun setLowRunDismissedEntryId(entryId: String?) {
        dataStore.edit { prefs ->
            if (entryId == null) {
                prefs.remove(KEY_LOW_RUN_DISMISSED_ENTRY_ID)
                prefs[KEY_LOW_RUN_DISMISSED] = false
            } else {
                prefs[KEY_LOW_RUN_DISMISSED_ENTRY_ID] = entryId
                prefs[KEY_LOW_RUN_DISMISSED] = true
            }
        }
    }

    override suspend fun setStanding(enabled: Boolean) {
        dataStore.edit { it[KEY_STANDING] = enabled }
    }

    override suspend fun setDismissed(dismissed: Boolean) {
        dataStore.edit { it[KEY_DISMISSED] = dismissed }
    }

    override suspend fun setMachineState(state: CheckInMachine.State) {
        dataStore.edit {
            it[KEY_MACHINE_STATE] = CanonicalJson.json.encodeToString(CheckInMachine.State.serializer(), state)
        }
    }

    override suspend fun setReminders(reminders: List<Reminder>) {
        dataStore.edit {
            it[KEY_REMINDERS] = CanonicalJson.json.encodeToString(ListSerializer(Reminder.serializer()), reminders)
        }
    }

    override suspend fun recordTestSuccess(completedAt: Instant) {
        dataStore.edit { it[KEY_LAST_TEST_SUCCESS] = completedAt.toString() }
    }

    override suspend fun clearLastTestSuccess() {
        dataStore.edit { it.remove(KEY_LAST_TEST_SUCCESS) }
    }

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        val KEY_CAPTURE_LOCATION = booleanPreferencesKey("capture_location")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_TAG_PICKER_MODE = stringPreferencesKey("tag_picker_mode")
        val KEY_LOW_RUN_DISMISSED = booleanPreferencesKey("low_run_dismissed")
        val KEY_LOW_RUN_DISMISSED_ENTRY_ID = stringPreferencesKey("low_run_dismissed_entry_id")
        val KEY_STANDING = booleanPreferencesKey("standing")
        val KEY_DISMISSED = booleanPreferencesKey("dismissed")
        val KEY_MACHINE_STATE = stringPreferencesKey("machine_state")
        val KEY_REMINDERS = stringPreferencesKey("reminders")
        val KEY_LAST_TEST_SUCCESS = stringPreferencesKey("last_test_success")
    }
}
