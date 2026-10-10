package com.example.smileproc.data

import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.CardPeriods
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.ExportDocument
import com.example.smileproc.core.LibraryRow
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PatternConfig
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Sealer
import com.example.smileproc.core.WeeklyCardBuilder
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class SmileRepository(
    private val database: SmileDatabase,
    private val sealer: Sealer,
    private val allowedTags: Set<String>,
    private val config: PatternConfig,
    private val library: List<LibraryRow>,
) {
    suspend fun entries(): List<Entry> =
        database.entries().all().map { row -> openEntry(row.id, row.payload) }

    suspend fun entry(id: String): Entry? =
        database.entries().get(id)?.let { openEntry(it.id, it.payload) }

    suspend fun latest(): Entry? = entries().maxWithOrNull(compareBy({ it.createdAt }, { it.id }))

    suspend fun upsert(entry: Entry) {
        val normalized = entry.normalized(allowedTags)
        val bytes = CanonicalJson.entry(normalized).toByteArray(Charsets.UTF_8)
        val sealed = sealer.seal(bytes, normalized.id.toByteArray(Charsets.UTF_8))
        database.entries().upsert(
            EntryRow(
                id = normalized.id,
                createdAt = normalized.createdAt.toEpochMilli(),
                updatedAt = normalized.updatedAt.toEpochMilli(),
                payload = sealed,
            ),
        )
    }

    suspend fun deleteEntry(id: String) {
        database.entries().delete(id)
    }

    suspend fun quickLog(mood: Mood, source: EntrySource, zone: ZoneId, now: Instant): Entry {
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
            timeZoneID = zone.id,
            mood = mood,
            tags = emptyList(),
            comment = null,
            location = null,
            source = source,
        )
        upsert(entry)
        return entry
    }

    suspend fun attachLocationIfMissing(id: String, sample: LocationSample, now: Instant) {
        val current = entry(id) ?: return
        if (current.location != null) return
        upsert(current.copy(location = sample, updatedAt = now))
    }

    suspend fun places(): List<PlaceLabel> =
        database.places().all().map { row ->
            CanonicalJson.parsePlace(openText(row.id, row.payload))
        }

    suspend fun upsertPlace(place: PlaceLabel) {
        val bytes = CanonicalJson.place(place).toByteArray(Charsets.UTF_8)
        val sealed = sealer.seal(bytes, place.id.toByteArray(Charsets.UTF_8))
        database.places().upsert(PlaceRow(place.id, sealed))
    }

    suspend fun deletePlace(id: String) {
        database.places().delete(id)
    }

    suspend fun removeAllLocations(now: Instant) {
        for (entry in entries()) {
            if (entry.location != null) {
                upsert(entry.copy(location = null, updatedAt = now))
            }
        }
        database.places().deleteAll()
    }

    suspend fun cards(): List<StoredWeeklyCard> =
        database.cards().all().map { row ->
            StoredCards.decode(openText(row.periodStart.toString(), row.payload))
        }

    suspend fun refreshCard(now: Instant, zone: ZoneId, weeklyWeekday: Int, hour: Int, minute: Int) {
        val allEntries = entries()
        val ends = cards().map { it.periodEnd }
        if (!CardPeriods.shouldBuildCard(allEntries, config, now, zone.id, weeklyWeekday, hour, minute, ends)) {
            return
        }
        val built = WeeklyCardBuilder.build(
            allEntries,
            places(),
            config,
            library,
            now,
            zone.id,
            weeklyWeekday,
            hour,
            minute,
        )
        val stored = StoredCards.from(built)
        val bytes = StoredCards.encode(stored).toByteArray(Charsets.UTF_8)
        val key = built.periodStart.toEpochMilli().toString()
        val sealed = sealer.seal(bytes, key.toByteArray(Charsets.UTF_8))
        database.cards().upsert(WeeklyCardRow(built.periodStart.toEpochMilli(), sealed))
    }

    suspend fun importDocument(document: ExportDocument) {
        for (incoming in document.entries) {
            val current = entry(incoming.id)
            if (current == null || incoming.updatedAt.isAfter(current.updatedAt)) {
                upsert(incoming)
            }
        }
        val existingPlaces = places().associateBy { it.id }
        for (incoming in document.places) {
            val current = existingPlaces[incoming.id]
            if (current == null || incoming.createdAt.isAfter(current.createdAt)) {
                upsertPlace(incoming)
            }
        }
    }

    suspend fun clearHistory() {
        database.entries().deleteAll()
        database.places().deleteAll()
        database.cards().deleteAll()
    }

    private fun openEntry(id: String, payload: ByteArray): Entry =
        CanonicalJson.parseEntry(openText(id, payload))

    private fun openText(id: String, payload: ByteArray): String =
        sealer.open(payload, id.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
}
