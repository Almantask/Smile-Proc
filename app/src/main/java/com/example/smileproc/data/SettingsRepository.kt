package com.example.smileproc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

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

    suspend fun setCaptureLocation(enabled: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setTagPickerMode(mode: TagPickerMode)
    suspend fun setLowRunDismissed(dismissed: Boolean)
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
        .map { prefs -> prefs[KEY_LOW_RUN_DISMISSED] ?: false }

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
        dataStore.edit { it[KEY_LOW_RUN_DISMISSED] = dismissed }
    }

    companion object {
        val KEY_CAPTURE_LOCATION = booleanPreferencesKey("capture_location")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_TAG_PICKER_MODE = stringPreferencesKey("tag_picker_mode")
        val KEY_LOW_RUN_DISMISSED = booleanPreferencesKey("low_run_dismissed")
    }
}
