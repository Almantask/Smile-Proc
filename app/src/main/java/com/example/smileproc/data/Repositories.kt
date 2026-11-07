package com.example.smileproc.data

import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.Entry
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.core.crypto.Sealer
import com.example.smileproc.data.db.EntryDao
import com.example.smileproc.data.db.EntryRow
import com.example.smileproc.data.db.PlaceDao
import com.example.smileproc.data.db.PlaceRow
import com.example.smileproc.data.db.WeeklyCardDao
import com.example.smileproc.data.db.WeeklyCardRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Sealed repository for [Entry] records.
 *
 * `AAD = entry.id` UTF-8 bytes; `createdAt` and `updatedAt` stay plaintext epoch millis for index
 * ordering and export merge checks (`docs/spec/android.md` › Storage and encryption).
 */
class EntryRepository(
    private val dao: EntryDao,
    private val sealer: Sealer,
) {
    @Throws(SealException::class)
    suspend fun save(entry: Entry) {
        dao.upsert(sealRow(entry))
    }

    @Throws(SealException::class)
    suspend fun saveAll(entries: List<Entry>) {
        if (entries.isEmpty()) return
        dao.upsertAll(entries.map(::sealRow))
    }

    @Throws(SealException::class)
    suspend fun getById(id: String): Entry? =
        dao.getById(id)?.let(::openRow)

    @Throws(SealException::class)
    suspend fun getAllNewestFirst(): List<Entry> =
        dao.getAllNewestFirst().map(::openRow)

    @Throws(SealException::class)
    suspend fun getAllOldestFirst(): List<Entry> =
        dao.getAllOldestFirst().map(::openRow)

    fun observeAllNewestFirst(): Flow<List<Entry>> =
        dao.observeAllNewestFirst().map { rows -> rows.map(::openRow) }

    fun observeInRange(startInclusive: Instant, endExclusive: Instant): Flow<List<Entry>> =
        dao.observeInRange(startInclusive.toEpochMilli(), endExclusive.toEpochMilli())
            .map { rows -> rows.map(::openRow) }

    suspend fun count(): Int = dao.count()

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun sealRow(entry: Entry): EntryRow {
        val payload = sealer.sealUtf8(entry.id, CanonicalJson.encodeEntry(entry))
        return EntryRow(
            id = entry.id,
            createdAt = entry.createdAt.toEpochMilli(),
            updatedAt = entry.updatedAt.toEpochMilli(),
            payload = payload,
        )
    }

    private fun openRow(row: EntryRow): Entry {
        val json = sealer.openUtf8(row.id, row.payload)
        val decoded = CanonicalJson.decodeEntry(json)
        if (decoded.id != row.id) {
            throw SealException(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED)
        }
        return decoded
    }
}

/**
 * Sealed repository for [PlaceLabel] records (`AAD = label.id` UTF-8 bytes).
 */
class PlaceRepository(
    private val dao: PlaceDao,
    private val sealer: Sealer,
) {
    @Throws(SealException::class)
    suspend fun save(label: PlaceLabel) {
        dao.upsert(sealRow(label))
    }

    @Throws(SealException::class)
    suspend fun saveAll(labels: List<PlaceLabel>) {
        if (labels.isEmpty()) return
        dao.upsertAll(labels.map(::sealRow))
    }

    @Throws(SealException::class)
    suspend fun getById(id: String): PlaceLabel? =
        dao.getById(id)?.let(::openRow)

    @Throws(SealException::class)
    suspend fun getAll(): List<PlaceLabel> =
        dao.getAll().map(::openRow)

    fun observeAll(): Flow<List<PlaceLabel>> =
        dao.observeAll().map { rows -> rows.map(::openRow) }

    suspend fun count(): Int = dao.count()

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun sealRow(label: PlaceLabel): PlaceRow =
        PlaceRow(
            id = label.id,
            payload = sealer.sealUtf8(label.id, CanonicalJson.encodePlaceLabel(label)),
        )

    private fun openRow(row: PlaceRow): PlaceLabel {
        val decoded = CanonicalJson.decodePlaceLabel(sealer.openUtf8(row.id, row.payload))
        if (decoded.id != row.id) {
            throw SealException(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED)
        }
        return decoded
    }
}

/**
 * Sealed repository for [WeeklyCard] records (`AAD = periodStart.toString()` UTF-8 bytes).
 */
class WeeklyCardRepository(
    private val dao: WeeklyCardDao,
    private val sealer: Sealer,
) {
    @Throws(SealException::class)
    suspend fun save(card: WeeklyCard) {
        val periodStartMillis = card.periodStart.toEpochMilli()
        val payload = sealer.sealUtf8(periodStartMillis.toString(), CanonicalJson.encodeWeeklyCard(card))
        dao.upsert(WeeklyCardRow(periodStart = periodStartMillis, payload = payload))
    }

    @Throws(SealException::class)
    suspend fun getByPeriodStart(periodStart: Instant): WeeklyCard? =
        dao.getByPeriodStart(periodStart.toEpochMilli())?.let(::openRow)

    @Throws(SealException::class)
    suspend fun getAllNewestFirst(): List<WeeklyCard> =
        dao.getAllNewestFirst().map(::openRow)

    fun observeAllNewestFirst(): Flow<List<WeeklyCard>> =
        dao.observeAllNewestFirst().map { rows -> rows.map(::openRow) }

    suspend fun count(): Int = dao.count()

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun openRow(row: WeeklyCardRow): WeeklyCard {
        val decoded = CanonicalJson.decodeWeeklyCard(
            sealer.openUtf8(row.periodStart.toString(), row.payload),
        )
        if (decoded.periodStart.toEpochMilli() != row.periodStart) {
            throw SealException(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED)
        }
        return decoded
    }
}
