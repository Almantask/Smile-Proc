package com.example.smileproc.core

import com.example.smileproc.core.export.ExportAppInfo
import com.example.smileproc.core.export.ExportCrypto
import com.example.smileproc.core.export.ExportEnvelope
import com.example.smileproc.core.export.ExportException
import com.example.smileproc.core.export.ExportPayload
import com.example.smileproc.core.export.ExportSettings
import com.example.smileproc.core.export.KdfParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Base64

class ExportCryptoTest {

    @Test
    fun roundTrip_encryptsAndDecryptsSuccessfully() {
        val password = "my-secret-password-123".toCharArray()
        val plaintext = """{"test":"data","number":42}"""

        val envelope = ExportCrypto.encrypt(
            payloadJson = plaintext,
            password = password,
            iterations = 1000,
        )

        assertEquals("smileproc-export", envelope.format)
        assertEquals(1, envelope.version)
        assertEquals("AES-256-GCM", envelope.cipher)
        assertEquals("PBKDF2-HMAC-SHA256", envelope.kdf.name)
        assertEquals(1000, envelope.kdf.iterations)

        val decrypted = ExportCrypto.decrypt(envelope, password)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_withWrongPassword_failsCleanly() {
        val password = "correct-password-123".toCharArray()
        val wrongPassword = "incorrect-password-999".toCharArray()
        val plaintext = """{"sensitive":"information"}"""

        val envelope = ExportCrypto.encrypt(
            payloadJson = plaintext,
            password = password,
            iterations = 1000,
        )

        try {
            ExportCrypto.decrypt(envelope, wrongPassword)
            fail("Expected ExportException for wrong password")
        } catch (e: ExportException) {
            assertEquals(ExportException.Reason.WRONG_PASSWORD_OR_CORRUPT, e.reason)
        }
    }

    @Test
    fun passwordTooShort_throwsException() {
        val shortPassword = "short".toCharArray()
        val plaintext = """{"test":true}"""

        try {
            ExportCrypto.encrypt(plaintext, shortPassword, iterations = 1000)
            fail("Expected ExportException for password shorter than 8 chars")
        } catch (e: ExportException) {
            assertEquals(ExportException.Reason.PASSWORD_TOO_SHORT, e.reason)
        }
    }

    @Test
    fun testVector1_decryptsAndReproducesIdentically() {
        val password = "test-password-123".toCharArray()
        val salt = ByteArray(16) { it.toByte() } // 0, 1, 2, ..., 15
        val nonce = ByteArray(12) { (it + 1).toByte() } // 1, 2, ..., 12
        val iterations = 1000

        val sampleEntry = Entry(
            id = "00000000-0000-0000-0000-000000000001",
            createdAt = Instant.parse("2026-10-18T10:00:00Z"),
            updatedAt = Instant.parse("2026-10-18T10:00:00Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.OKAY,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
        val samplePlace = PlaceLabel(
            id = "00000000-0000-0000-0000-000000000002",
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 45.4642,
            anchorLongitude = 9.1900,
            createdAt = Instant.parse("2026-10-18T09:00:00Z"),
        )
        val sampleSettings = ExportSettings(
            reminders = listOf(Reminder(id = "r1", hour = 8, minute = 0, days = (1..7).toSet())),
            keepCheckIn = true,
            weeklyCard = WeeklyCardTime(weekday = 7, hour = 19, minute = 0),
            tagPickerMode = "gradual",
            captureLocation = true,
        )
        val payload = ExportPayload(
            exportedAt = "2026-10-18T12:00:00Z",
            app = ExportAppInfo(platform = "android", version = "1.0.0"),
            entries = listOf(sampleEntry),
            places = listOf(samplePlace),
            settings = sampleSettings,
        )

        val plaintextJson = CanonicalJson.json.encodeToString(ExportPayload.serializer(), payload)

        val envelope = ExportCrypto.encrypt(
            payloadJson = plaintextJson,
            password = password,
            salt = salt,
            nonce = nonce,
            iterations = iterations,
        )

        // Verify decryption matches plaintext
        val decrypted = ExportCrypto.decrypt(envelope, password)
        assertEquals(plaintextJson, decrypted)

        // Read static vector fixture
        val fixtureFile = File("fixtures/export/vector-1.json").let {
            if (it.exists()) it else File("../fixtures/export/vector-1.json")
        }
        assertTrue("vector-1.json fixture must exist", fixtureFile.exists())
        val fileContent = fixtureFile.readText(Charsets.UTF_8)
        val fileEnvelope = CanonicalJson.json.decodeFromString(ExportEnvelope.serializer(), fileContent)
        val decryptedFromFixture = ExportCrypto.decrypt(fileEnvelope, password)
        assertEquals(plaintextJson, decryptedFromFixture)
        assertEquals(fileEnvelope.payload, envelope.payload)
    }
}
