package com.example.smileproc.core.export

import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.IsoInstantSerializer
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.ReminderPause
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Extended `.smileproc` decrypted payload wrapping the v1 fields plus optional v2 metadata
 * (entry extras, encrypted custom tags, reminder flexibility windows, and reminder pause state).
 *
 * Uses [jsonCodec] (`ignoreUnknownKeys = true`) and defaults on extension fields so both v1
 * backups (`ExportPayload`) and extended backups decode cleanly without altering
 * `CanonicalJson.json` or `fixtures/export/vector-1.json`.
 */
@Serializable
data class ExtendedExportPayload(
    val exportedAt: String,
    val app: ExportAppInfo,
    val entries: List<Entry>,
    val places: List<PlaceLabel>,
    val settings: ExportSettings,
    val entryExtras: List<EntryExtras> = emptyList(),
    val customTags: List<CustomTag> = emptyList(),
    val reminderWindows: Map<String, Int> = emptyMap(),
    val reminderPause: ReminderPause = ReminderPause.NONE,
) {
    fun toBasePayload(): ExportPayload = ExportPayload(
        exportedAt = exportedAt,
        app = app,
        entries = entries,
        places = places,
        settings = settings,
    )

    companion object {
        val jsonCodec: Json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }

        fun encodeToString(payload: ExtendedExportPayload): String =
            jsonCodec.encodeToString(serializer(), payload)

        fun decodeFromString(raw: String): ExtendedExportPayload =
            jsonCodec.decodeFromString(serializer(), raw)
    }
}
