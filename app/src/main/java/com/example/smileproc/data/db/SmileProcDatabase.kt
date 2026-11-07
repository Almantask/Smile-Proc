package com.example.smileproc.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver

/**
 * Encrypted-payload Room 3 database (`docs/spec/android.md` › Storage and encryption).
 *
 * Schema JSON is exported to `app/schemas/`. After M5 (dogfood build 1), schema changes happen
 * strictly through explicit migrations with migration tests.
 */
@Database(
    entities = [EntryRow::class, PlaceRow::class, WeeklyCardRow::class],
    version = 1,
    exportSchema = true,
)
abstract class SmileProcDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun placeDao(): PlaceDao
    abstract fun weeklyCardDao(): WeeklyCardDao

    companion object {
        const val DB_NAME = "smileproc.db"

        fun open(
            context: Context,
            dbName: String = DB_NAME,
            driver: SQLiteDriver = AndroidSQLiteDriver(),
        ): SmileProcDatabase {
            val appContext = context.applicationContext
            val dbPath = appContext.getDatabasePath(dbName).absolutePath
            return Room.databaseBuilder<SmileProcDatabase>(
                context = appContext,
                name = dbPath,
            )
                .setDriver(driver)
                .build()
        }
    }
}
