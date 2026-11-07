package com.example.smileproc.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Upsert
    suspend fun upsert(row: EntryRow)

    @Upsert
    suspend fun upsertAll(rows: List<EntryRow>)

    @Query("SELECT * FROM entry WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EntryRow?

    @Query("SELECT * FROM entry LIMIT 1")
    suspend fun peekOne(): EntryRow?

    @Query("SELECT * FROM entry ORDER BY createdAt DESC, id ASC")
    suspend fun getAllNewestFirst(): List<EntryRow>

    @Query("SELECT * FROM entry ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOldestFirst(): List<EntryRow>

    @Query("SELECT * FROM entry ORDER BY createdAt DESC, id ASC")
    fun observeAllNewestFirst(): Flow<List<EntryRow>>

    @Query("SELECT * FROM entry WHERE createdAt >= :startInclusive AND createdAt < :endExclusive ORDER BY createdAt DESC, id ASC")
    fun observeInRange(startInclusive: Long, endExclusive: Long): Flow<List<EntryRow>>

    @Query("SELECT COUNT(*) FROM entry")
    suspend fun count(): Int

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM entry")
    suspend fun deleteAll()
}

@Dao
interface PlaceDao {
    @Upsert
    suspend fun upsert(row: PlaceRow)

    @Upsert
    suspend fun upsertAll(rows: List<PlaceRow>)

    @Query("SELECT * FROM place WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaceRow?

    @Query("SELECT * FROM place LIMIT 1")
    suspend fun peekOne(): PlaceRow?

    @Query("SELECT * FROM place ORDER BY id ASC")
    suspend fun getAll(): List<PlaceRow>

    @Query("SELECT * FROM place ORDER BY id ASC")
    fun observeAll(): Flow<List<PlaceRow>>

    @Query("SELECT COUNT(*) FROM place")
    suspend fun count(): Int

    @Query("DELETE FROM place WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM place")
    suspend fun deleteAll()
}

@Dao
interface WeeklyCardDao {
    @Upsert
    suspend fun upsert(row: WeeklyCardRow)

    @Query("SELECT * FROM weekly_card WHERE periodStart = :periodStart LIMIT 1")
    suspend fun getByPeriodStart(periodStart: Long): WeeklyCardRow?

    @Query("SELECT * FROM weekly_card LIMIT 1")
    suspend fun peekOne(): WeeklyCardRow?

    @Query("SELECT * FROM weekly_card ORDER BY periodStart DESC")
    suspend fun getAllNewestFirst(): List<WeeklyCardRow>

    @Query("SELECT * FROM weekly_card ORDER BY periodStart DESC")
    fun observeAllNewestFirst(): Flow<List<WeeklyCardRow>>

    @Query("SELECT COUNT(*) FROM weekly_card")
    suspend fun count(): Int

    @Query("DELETE FROM weekly_card")
    suspend fun deleteAll()
}
