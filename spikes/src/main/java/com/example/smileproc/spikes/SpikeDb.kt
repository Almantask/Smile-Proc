package com.example.smileproc.spikes

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** One row per spike event. `kind` is tap, fgs, locate, reminder, dismiss or boot; the rest is sealed. */
@Entity(tableName = "record", indices = [Index("createdAt")])
class RecordRow(@PrimaryKey val id: String, val createdAt: Long, val kind: String, val payload: ByteArray)

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(row: RecordRow)

    @Query("SELECT * FROM record ORDER BY createdAt DESC")
    suspend fun all(): List<RecordRow>

    @Query("DELETE FROM record")
    suspend fun clear()
}

@Database(entities = [RecordRow::class], version = 1, exportSchema = false)
abstract class SpikeDb : RoomDatabase() {
    abstract fun records(): RecordDao

    companion object {
        @Volatile
        private var instance: SpikeDb? = null

        fun get(context: Context): SpikeDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SpikeDb::class.java, "spikes.db")
                .build().also { instance = it }
        }
    }
}
