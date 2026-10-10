package com.example.smileproc.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.ExportSettings
import com.example.smileproc.core.ReminderSpec
import com.example.smileproc.core.WeeklyCardTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val appLockEnabled: Boolean = false,
    val captureLocation: Boolean = false,
    val tagPickerMode: String = "gradual",
    val reminders: List<ReminderSpec> = emptyList(),
    val weeklyCard: WeeklyCardTime = WeeklyCardTime(weekday = 7, hour = 19, minute = 0),
    val dismissedLowRunId: String? = null,
) {
    fun exportSettings(): ExportSettings = ExportSettings(
        reminders = reminders,
        weeklyCard = weeklyCard,
        tagPickerMode = tagPickerMode,
    )
}

class SettingsStore(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("smileproc") },
    )
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<UserSettings> = dataStore.data.map { prefs -> decode(prefs) }

    suspend fun snapshot(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val next = transform(decode(prefs))
            prefs[onboarding] = next.onboardingComplete
            prefs[appLock] = next.appLockEnabled
            prefs[captureLocation] = next.captureLocation
            prefs[tagMode] = next.tagPickerMode
            prefs[reminders] = encodeReminders(next.reminders)
            prefs[weeklyWeekday] = next.weeklyCard.weekday
            prefs[weeklyHour] = next.weeklyCard.hour
            prefs[weeklyMinute] = next.weeklyCard.minute
            val dismissed = next.dismissedLowRunId
            if (dismissed == null) {
                prefs.remove(dismissedLowRun)
            } else {
                prefs[dismissedLowRun] = dismissed
            }
        }
    }

    suspend fun applyExport(settings: ExportSettings) {
        update { current ->
            current.copy(
                reminders = settings.reminders,
                weeklyCard = settings.weeklyCard,
                tagPickerMode = settings.tagPickerMode,
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun decode(prefs: Preferences): UserSettings = UserSettings(
        onboardingComplete = prefs[onboarding] == true,
        appLockEnabled = prefs[appLock] == true,
        captureLocation = prefs[captureLocation] == true,
        tagPickerMode = prefs[tagMode] ?: "gradual",
        reminders = decodeReminders(prefs[reminders].orEmpty()),
        weeklyCard = WeeklyCardTime(
            weekday = prefs[weeklyWeekday] ?: 7,
            hour = prefs[weeklyHour] ?: 19,
            minute = prefs[weeklyMinute] ?: 0,
        ),
        dismissedLowRunId = prefs[dismissedLowRun],
    )

    private fun decodeReminders(text: String): List<ReminderSpec> {
        if (text.isBlank()) return emptyList()
        return json.parseToJsonElement(text).jsonArray.map { element ->
            val row = element.jsonObject
            ReminderSpec(
                id = row.getValue("id").jsonPrimitive.content,
                hour = row.getValue("hour").jsonPrimitive.int,
                minute = row.getValue("minute").jsonPrimitive.int,
                weekdays = row.getValue("weekdays").jsonArray.map { it.jsonPrimitive.int },
            )
        }
    }

    private fun encodeReminders(reminders: List<ReminderSpec>): String =
        reminders.joinToString(prefix = "[", postfix = "]") { reminder ->
            buildString {
                append("{\"id\":").append(CanonicalJson.string(reminder.id))
                append(",\"hour\":").append(reminder.hour)
                append(",\"minute\":").append(reminder.minute)
                append(",\"weekdays\":[").append(reminder.weekdays.joinToString(",")).append("]}")
            }
        }

    private companion object {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val appLock = booleanPreferencesKey("app_lock")
        val captureLocation = booleanPreferencesKey("capture_location")
        val tagMode = stringPreferencesKey("tag_picker_mode")
        val reminders = stringPreferencesKey("reminders")
        val weeklyWeekday = intPreferencesKey("weekly_weekday")
        val weeklyHour = intPreferencesKey("weekly_hour")
        val weeklyMinute = intPreferencesKey("weekly_minute")
        val dismissedLowRun = stringPreferencesKey("dismissed_low_run")
    }
}
