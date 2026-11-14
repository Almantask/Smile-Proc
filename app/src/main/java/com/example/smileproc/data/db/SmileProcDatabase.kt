package com.example.smileproc.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL

/**
 * Encrypted-payload Room 3 database (`docs/spec/android.md` › Storage and encryption).
 *
 * Schema JSON is exported to `app/schemas/`. After M5 (dogfood build 1), schema changes happen
 * strictly through explicit migrations with migration tests.
 */
@Database(
    entities = [
        EntryRow::class,
        PlaceRow::class,
        WeeklyCardRow::class,
        EntryExtrasRow::class,
        CustomTagRow::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SmileProcDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun placeDao(): PlaceDao
    abstract fun weeklyCardDao(): WeeklyCardDao
    abstract fun entryExtrasDao(): EntryExtrasDao
    abstract fun customTagDao(): CustomTagDao

    companion object {
        const val DB_NAME = "smileproc.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entry_extras` " +
                        "(`entryId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`entryId`))",
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_tag` " +
                        "(`id` TEXT NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

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
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
