package com.example.smileproc.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.SafeLog
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.ErrorLogEntry
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.core.crypto.Sealer
import kotlinx.serialization.builtins.ListSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ErrorLogStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sealer: JcaSealer
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-08T19:30:00.000Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        SafeLog.clearStoreForTesting()
        context = ApplicationProvider.getApplicationContext()
        tempDir = tempFolder.newFolder("error_log_test_${System.nanoTime()}")
        sealer = JcaSealer.withNewKey()
    }

    @After
    fun tearDown() {
        SafeLog.clearStoreForTesting()
    }

    @Test
    fun record_sealsPayloadOnDisk_andDecryptsSuccessfully() {
        val store = ErrorLogStore(
            context = context,
            sealer = sealer,
            clock = fixedClock,
            baseDir = tempDir,
        )

        val exception = SealException(SealException.Reason.KEY_UNAVAILABLE)
        store.record(SafeLog.Event.STORAGE_OPEN_FAILED, exception)

        // 1. File exists on disk
        val file = File(tempDir, ErrorLogStore.FILE_NAME)
        assertTrue("Error logs file must exist on disk", file.exists())

        // 2. File content is encrypted (does not contain plaintext event or details strings)
        val rawBytes = file.readBytes()
        val rawString = String(rawBytes, Charsets.ISO_8859_1)
        assertFalse("Raw file must not contain plaintext event name", rawString.contains("STORAGE_OPEN_FAILED"))
        assertFalse("Raw file must not contain plaintext details", rawString.contains("KEY_UNAVAILABLE"))

        // 3. getAllDecrypted returns decrypted entry
        val decrypted = store.getAllDecrypted()
        assertEquals(1, decrypted.size)
        val entry = decrypted[0]
        assertNotNull(entry.id)
        assertEquals("2026-11-08T19:30:00.000Z", entry.timestamp)
        assertEquals("STORAGE_OPEN_FAILED", entry.event)
        assertEquals("com.example.smileproc.core.crypto.SealException", entry.errorClass)
        assertEquals("KEY_UNAVAILABLE", entry.details)
    }

    @Test
    fun exportDecrypted_writesFormattedJson() {
        val store = ErrorLogStore(
            context = context,
            sealer = sealer,
            clock = fixedClock,
            baseDir = tempDir,
        )

        store.record(SafeLog.Event.STORAGE_OPEN_FAILED, SealException(SealException.Reason.KEY_UNAVAILABLE))
        store.record(SafeLog.Event.LOCATION_FIX_FAILED, SecurityException("Location denied"))

        val out = ByteArrayOutputStream()
        store.exportDecrypted(out)

        val exportedJson = out.toString(Charsets.UTF_8.name())
        assertTrue(exportedJson.startsWith("["))
        assertTrue(exportedJson.contains("STORAGE_OPEN_FAILED"))
        assertTrue(exportedJson.contains("LOCATION_FIX_FAILED"))
        assertTrue(exportedJson.contains("SecurityException"))

        // Verify valid JSON list
        val decoded = CanonicalJson.json.decodeFromString(
            ListSerializer(ErrorLogEntry.serializer()),
            exportedJson,
        )
        assertEquals(2, decoded.size)
    }

    @Test
    fun buffering_whenKeyNotAvailable_andFlushesWhenKeyReady() {
        var keyAvailable = false
        val conditionalSealer = object : Sealer {
            override fun isKeyAvailable(): Boolean = keyAvailable
            override fun seal(rowId: String, plaintext: ByteArray): ByteArray =
                if (keyAvailable) sealer.seal(rowId, plaintext) else throw SealException(SealException.Reason.KEY_UNAVAILABLE)
            override fun open(rowId: String, payload: ByteArray): ByteArray =
                if (keyAvailable) sealer.open(rowId, payload) else throw SealException(SealException.Reason.KEY_UNAVAILABLE)
        }

        val store = ErrorLogStore(
            context = context,
            sealer = conditionalSealer,
            clock = fixedClock,
            baseDir = tempDir,
        )

        // Record while key unavailable
        store.record(SafeLog.Event.STORAGE_KEY_MISSING, null)
        val file = File(tempDir, ErrorLogStore.FILE_NAME)
        assertFalse("File should not be written while key is unavailable", file.exists())
        assertEquals(1, store.count())

        // Key becomes available
        keyAvailable = true
        store.record(SafeLog.Event.NOTIFY_FAILED, RuntimeException("Notification error"))

        // Flushed to disk
        assertTrue("File should exist after key became available", file.exists())
        val all = store.getAllDecrypted()
        assertEquals(2, all.size)
        assertEquals("STORAGE_KEY_MISSING", all[0].event)
        assertEquals("NOTIFY_FAILED", all[1].event)
    }

    @Test
    fun clear_deletesFileAndMemoryBuffer() {
        val store = ErrorLogStore(
            context = context,
            sealer = sealer,
            clock = fixedClock,
            baseDir = tempDir,
        )

        store.record(SafeLog.Event.NOTIFY_FAILED, RuntimeException("Test"))
        assertEquals(1, store.count())

        store.clear()
        assertEquals(0, store.count())
        assertFalse(File(tempDir, ErrorLogStore.FILE_NAME).exists())
    }

    @Test
    fun safeLog_automaticallyPersistsFailuresToStore() {
        val store = ErrorLogStore(
            context = context,
            sealer = sealer,
            clock = fixedClock,
            baseDir = tempDir,
        )
        SafeLog.init(store)

        SafeLog.failure(SafeLog.Event.LOCATION_FIX_FAILED, SecurityException("Denied"))
        SafeLog.event(SafeLog.Event.NOTIFY_FAILED) // Event ending with _FAILED is recorded

        assertEquals(2, store.count())
        val entries = store.getAllDecrypted()
        assertEquals("LOCATION_FIX_FAILED", entries[0].event)
        assertEquals("java.lang.SecurityException", entries[0].errorClass)
        assertEquals("NOTIFY_FAILED", entries[1].event)
    }
}
