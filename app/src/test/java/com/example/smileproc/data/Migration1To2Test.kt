package com.example.smileproc.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.BuiltInContextTag
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Variant
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WindowKind
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.db.SmileProcDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var sealer: JcaSealer
    private var migratedDb: SmileProcDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "migration-1-2-test-${System.nanoTime()}.db"
        sealer = JcaSealer.withNewKey()
    }

    @After
    fun tearDown() {
        migratedDb?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_preservesV1RowsAndSupportsSealedExtrasAndCustomTags() = runTest {
        val v1Entry = Entry(
            id = "e1111111-1111-4111-8111-111111111111",
            createdAt = Instant.parse("2026-10-12T08:00:00Z"),
            updatedAt = Instant.parse("2026-10-12T08:05:00Z"),
            timeZoneID = "Europe/Vilnius",
            mood = Mood.OKAY,
            tags = listOf("calm.peaceful"),
            comment = "Preserved across v1->v2 migration",
            location = null,
            source = EntrySource.NOTIFICATION,
        )
        val v1Place = PlaceLabel(
            id = "22222222-2222-4222-8222-222222222222",
            name = "Bookshop",
            kind = PlaceKind.OTHER,
            anchorLatitude = 54.6872,
            anchorLongitude = 25.2797,
            createdAt = Instant.parse("2026-10-11T10:00:00Z"),
        )
        val v1Card = WeeklyCard(
            periodStart = Instant.parse("2026-10-04T16:00:00Z"),
            periodEnd = Instant.parse("2026-10-11T16:00:00Z"),
            windowKind = WindowKind.WEEK,
            entryCount = 1,
            moodCounts = listOf(0, 1, 0),
            days = List(7) { dayIdx -> if (dayIdx == 0) listOf(0, 1, 0) else listOf(0, 0, 0) },
            isLowWeek = false,
            variant = Variant.FIRST_WEEK,
            items = emptyList(),
        )

        // Create genuine Room v1 schema + sealed v1 rows before opening Room v2.
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `entry` " +
                    "(`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`id`))",
            )
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_createdAt` ON `entry` (`createdAt`)")
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `place` (`id` TEXT NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`id`))",
            )
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `weekly_card` (`periodStart` INTEGER NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`periodStart`))",
            )
            raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            raw.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '351af55cb99103e75e346e409be3ba79')",
            )
            raw.insertOrThrow(
                "entry",
                null,
                ContentValues().apply {
                    put("id", v1Entry.id)
                    put("createdAt", v1Entry.createdAt.toEpochMilli())
                    put("updatedAt", v1Entry.updatedAt.toEpochMilli())
                    put(
                        "payload",
                        sealer.sealUtf8(v1Entry.id, CanonicalJson.json.encodeToString(Entry.serializer(), v1Entry)),
                    )
                },
            )
            raw.insertOrThrow(
                "place",
                null,
                ContentValues().apply {
                    put("id", v1Place.id)
                    put(
                        "payload",
                        sealer.sealUtf8(v1Place.id, CanonicalJson.json.encodeToString(PlaceLabel.serializer(), v1Place)),
                    )
                },
            )
            raw.insertOrThrow(
                "weekly_card",
                null,
                ContentValues().apply {
                    put("periodStart", v1Card.periodStart.toEpochMilli())
                    put(
                        "payload",
                        sealer.sealUtf8(
                            v1Card.periodStart.toEpochMilli().toString(),
                            CanonicalJson.json.encodeToString(WeeklyCard.serializer(), v1Card),
                        ),
                    )
                },
            )
            raw.version = 1
        }

        // Open v2 database (executes MIGRATION_1_2 and Room schema verification).
        val db = SmileProcDatabase.open(context, dbName)
        migratedDb = db

        val entryRepo = EntryRepository(db.entryDao(), sealer)
        val placeRepo = PlaceRepository(db.placeDao(), sealer)
        val cardRepo = WeeklyCardRepository(db.weeklyCardDao(), sealer)
        val extrasRepo = EntryExtrasRepository(db.entryExtrasDao(), sealer)
        val customTagRepo = CustomTagRepository(db.customTagDao(), sealer)

        // All v1 records remain intact and decrypt cleanly.
        assertEquals(listOf(v1Entry), entryRepo.getAllOldestFirst())
        assertEquals(listOf(v1Place), placeRepo.getAll())
        assertEquals(listOf(v1Card), cardRepo.getAllNewestFirst())

        // Write and verify new sealed v2 tables (entry_extras + custom_tag).
        val customTag = CustomTag.create(
            rawLabel = "Evening walk",
            createdAt = Instant.parse("2026-10-12T18:00:00Z"),
            id = "custom.walk1",
        )
        customTagRepo.save(customTag)

        val extras = EntryExtras(
            entryId = v1Entry.id,
            contextTags = listOf(BuiltInContextTag.OUTDOORS.id, BuiltInContextTag.SOLO.id),
            customTagIds = listOf(customTag.id),
            bookmarked = true,
            updatedAt = Instant.parse("2026-10-12T18:05:00Z"),
        )
        extrasRepo.save(extras)

        assertEquals(listOf(customTag), customTagRepo.getAll())
        assertEquals(extras, extrasRepo.getByEntryId(v1Entry.id))

        db.close()
        migratedDb = null

        // Verify zero plaintext leakage and AAD binding on raw SQLite blobs after migration.
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { verifyRaw ->
            assertEquals(2, verifyRaw.version)
            val extrasCursor = verifyRaw.rawQuery("SELECT payload FROM entry_extras WHERE entryId = ?", arrayOf(v1Entry.id))
            assertTrue(extrasCursor.moveToFirst())
            val extrasBlob = extrasCursor.getBlob(0)
            extrasCursor.close()
            assertFalse(extrasBlob.decodeToString().contains(BuiltInContextTag.OUTDOORS.id))
            assertFalse(extrasBlob.decodeToString().contains(customTag.id))
            assertThrows(SealException::class.java) {
                sealer.openUtf8("wrong-entry-id", extrasBlob)
            }

            val tagCursor = verifyRaw.rawQuery("SELECT payload FROM custom_tag WHERE id = ?", arrayOf(customTag.id))
            assertTrue(tagCursor.moveToFirst())
            val tagBlob = tagCursor.getBlob(0)
            tagCursor.close()
            assertFalse(tagBlob.decodeToString().contains("Evening walk"))
            assertThrows(SealException::class.java) {
                sealer.openUtf8("wrong-tag-id", tagBlob)
            }

            val restoredTagRow = customTagRepo
            assertNotNull(restoredTagRow)
        }
    }
}
