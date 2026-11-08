package com.example.smileproc.data

import android.content.Context
import com.example.smileproc.AppContainer
import com.example.smileproc.SafeLog
import com.example.smileproc.StorageState
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.export.ExportAppInfo
import com.example.smileproc.core.export.ExportCrypto
import com.example.smileproc.core.export.ExportEnvelope
import com.example.smileproc.core.export.ExportException
import com.example.smileproc.core.export.ExportPayload
import com.example.smileproc.core.export.ExportSettings
import com.example.smileproc.notify.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

data class ImportResult(
    val entriesImported: Int,
    val placesImported: Int,
)

class ExportImportManager(
    private val context: Context,
    private val container: AppContainer,
) {
    suspend fun createExportPayload(): ExportPayload = withContext(Dispatchers.IO) {
        val ready = container.awaitState() as StorageState.Ready
        val storage = ready.storage
        val entries = storage.entryRepository.getAllOldestFirst()
        val places = storage.placeRepository.getAll()

        val reminders = container.settingsRepository.reminders.first()
        val keepCheckIn = container.settingsRepository.standing.first()
        val weeklyCard = container.settingsRepository.weeklyCardTime.first()
        val tagPickerMode = container.settingsRepository.tagPickerMode.first()
        val captureLocation = container.settingsRepository.captureLocation.first()

        ExportPayload(
            exportedAt = Instant.now().toString(),
            app = ExportAppInfo(platform = "android", version = "1.0.0"),
            entries = entries,
            places = places,
            settings = ExportSettings(
                reminders = reminders,
                keepCheckIn = keepCheckIn,
                weeklyCard = weeklyCard,
                tagPickerMode = tagPickerMode.storageValue,
                captureLocation = captureLocation,
            ),
        )
    }

    suspend fun exportToStream(
        outputStream: OutputStream,
        password: CharArray,
    ) = withContext(Dispatchers.IO) {
        try {
            val payload = createExportPayload()
            val plaintextJson = CanonicalJson.json.encodeToString(ExportPayload.serializer(), payload)
            val envelope = ExportCrypto.encrypt(plaintextJson, password)
            val envelopeJson = CanonicalJson.json.encodeToString(ExportEnvelope.serializer(), envelope)
            outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(envelopeJson)
            }
            SafeLog.event(SafeLog.Event.EXPORT_SAVED)
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.EXPORT_FAILED, e)
            throw e
        }
    }

    suspend fun importFromStream(
        inputStream: InputStream,
        password: CharArray,
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val fileContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val envelope = try {
                CanonicalJson.json.decodeFromString(ExportEnvelope.serializer(), fileContent)
            } catch (e: Exception) {
                throw ExportException(ExportException.Reason.INVALID_PAYLOAD, e)
            }

            val plaintextJson = ExportCrypto.decrypt(envelope, password)
            val payload = try {
                CanonicalJson.json.decodeFromString(ExportPayload.serializer(), plaintextJson)
            } catch (e: Exception) {
                throw ExportException(ExportException.Reason.INVALID_PAYLOAD, e)
            }

            val ready = container.awaitState() as StorageState.Ready
            val storage = ready.storage

            var entriesCount = 0
            for (entry in payload.entries) {
                val existing = storage.entryRepository.getById(entry.id)
                if (existing == null || entry.updatedAt.isAfter(existing.updatedAt)) {
                    storage.entryRepository.save(entry)
                    entriesCount++
                }
            }

            var placesCount = 0
            for (place in payload.places) {
                val existing = storage.placeRepository.getById(place.id)
                if (existing == null || place.createdAt.isAfter(existing.createdAt)) {
                    storage.placeRepository.save(place)
                    placesCount++
                }
            }

            // Restore settings
            container.settingsRepository.setReminders(payload.settings.reminders)
            container.settingsRepository.setStanding(payload.settings.keepCheckIn)
            container.settingsRepository.setWeeklyCardTime(payload.settings.weeklyCard)
            container.settingsRepository.setTagPickerMode(TagPickerMode.fromStorage(payload.settings.tagPickerMode))
            container.settingsRepository.setCaptureLocation(payload.settings.captureLocation)

            // Weekly cards are rebuilt after import per docs/spec/android.md:102
            storage.weeklyCardRepository.deleteAll()
            container.settingsRepository.setLastSeenWeeklyCardPeriodStart(null)

            // Reschedule reminder alarm with restored reminders
            ReminderScheduler.scheduleNext(context)

            SafeLog.event(SafeLog.Event.IMPORT_RESTORED)
            ImportResult(entriesImported = entriesCount, placesImported = placesCount)
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.IMPORT_FAILED, e)
            throw e
        }
    }
}
