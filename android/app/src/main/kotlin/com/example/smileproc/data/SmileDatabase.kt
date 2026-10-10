package com.example.smileproc.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "entry", indices = [Index("createdAt")])
class EntryRow(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payload: ByteArray,
)

@Entity(tableName = "place")
class PlaceRow(
    @PrimaryKey val id: String,
    val payload: ByteArray,
)

@Entity(tableName = "weekly_card")
class WeeklyCardRow(
    @PrimaryKey val periodStart: Long,
    val payload: ByteArray,
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entry ORDER BY createdAt ASC, id ASC")
    suspend fun all(): List<EntryRow>

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun get(id: String): EntryRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: EntryRow)

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM entry")
    suspend fun deleteAll()
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place")
    suspend fun all(): List<PlaceRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: PlaceRow)

    @Query("DELETE FROM place WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM place")
    suspend fun deleteAll()
}

@Dao
interface WeeklyCardDao {
    @Query("SELECT * FROM weekly_card ORDER BY periodStart DESC")
    suspend fun all(): List<WeeklyCardRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WeeklyCardRow)

    @Query("DELETE FROM weekly_card")
    suspend fun deleteAll()
}

@Database(
    entities = [EntryRow::class, PlaceRow::class, WeeklyCardRow::class],
    version = 1,
    exportSchema = true,
)
abstract class SmileDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun places(): PlaceDao
    abstract fun cards(): WeeklyCardDao

    companion object {
        const val NAME = "smileproc.db"
    }
}
