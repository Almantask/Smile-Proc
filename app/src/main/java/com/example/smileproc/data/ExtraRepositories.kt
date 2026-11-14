package com.example.smileproc.data

import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.core.crypto.Sealer
import com.example.smileproc.data.db.CustomTagDao
import com.example.smileproc.data.db.CustomTagRow
import com.example.smileproc.data.db.EntryExtrasDao
import com.example.smileproc.data.db.EntryExtrasRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Sealed storage repository for per-entry optional metadata ([EntryExtras]):
 * built-in life-context tags, encrypted custom tag references, and Saved Moments bookmark state.
 */
class EntryExtrasRepository(
    private val dao: EntryExtrasDao,
    private val sealer: Sealer,
    private val lifetime: StorageLifetime = StorageLifetime(),
) {
    suspend fun save(extras: EntryExtras) {
        dao.upsert(sealRow(extras.normalized()))
    }

    suspend fun saveAll(items: List<EntryExtras>) {
        if (items.isEmpty()) return
        dao.upsertAll(items.map { sealRow(it.normalized()) })
    }

    @Throws(SealException::class)
    suspend fun getByEntryId(entryId: String): EntryExtras? =
        dao.getByEntryId(entryId)?.let(::openRow)

    @Throws(SealException::class)
    suspend fun getAll(): List<EntryExtras> =
        dao.getAll().map(::openRow)

    fun observeAll(): Flow<List<EntryExtras>> =
        observing(lifetime) { dao.observeAll().map { rows -> rows.map(::openRow) } }

    suspend fun deleteByEntryId(entryId: String) {
        dao.deleteByEntryId(entryId)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun sealRow(extras: EntryExtras): EntryExtrasRow {
        val json = EntryExtras.encode(extras)
        val sealed = sealer.sealUtf8(extras.entryId, json)
        return EntryExtrasRow(
            entryId = extras.entryId,
            updatedAt = extras.updatedAt.toEpochMilli(),
            payload = sealed,
        )
    }

    private fun openRow(row: EntryExtrasRow): EntryExtras {
        val decoded = EntryExtras.decode(sealer.openUtf8(row.entryId, row.payload))
        if (decoded.entryId != row.entryId) {
            throw SealException(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED)
        }
        return decoded
    }
}

/**
 * Sealed storage repository for user-created personal tags ([CustomTag]).
 */
class CustomTagRepository(
    private val dao: CustomTagDao,
    private val sealer: Sealer,
    private val lifetime: StorageLifetime = StorageLifetime(),
) {
    suspend fun save(tag: CustomTag) {
        dao.upsert(sealRow(tag))
    }

    suspend fun saveAll(tags: List<CustomTag>) {
        if (tags.isEmpty()) return
        dao.upsertAll(tags.map(::sealRow))
    }

    @Throws(SealException::class)
    suspend fun getById(id: String): CustomTag? =
        dao.getById(id)?.let(::openRow)

    @Throws(SealException::class)
    suspend fun getAll(): List<CustomTag> =
        dao.getAll().map(::openRow).sortedWith(compareBy<CustomTag> { it.createdAt }.thenBy { it.label })

    fun observeAll(): Flow<List<CustomTag>> =
        observing(lifetime) {
            dao.observeAll().map { rows ->
                rows.map(::openRow).sortedWith(compareBy<CustomTag> { it.createdAt }.thenBy { it.label })
            }
        }

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun sealRow(tag: CustomTag): CustomTagRow {
        val json = EntryExtras.encodeCustomTag(tag)
        val sealed = sealer.sealUtf8(tag.id, json)
        return CustomTagRow(
            id = tag.id,
            payload = sealed,
        )
    }

    private fun openRow(row: CustomTagRow): CustomTag {
        val decoded = EntryExtras.decodeCustomTag(sealer.openUtf8(row.id, row.payload))
        if (decoded.id != row.id) {
            throw SealException(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED)
        }
        return decoded
    }
}
