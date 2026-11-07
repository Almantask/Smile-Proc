package com.example.smileproc.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.core.crypto.Sealer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSealerAndroidTest {

    private lateinit var sealer: KeystoreSealer

    @Before
    fun setUp() {
        sealer = KeystoreSealer(alias = TEST_ALIAS)
        sealer.deleteKeyIfPresent()
    }

    @After
    fun tearDown() {
        sealer.deleteKeyIfPresent()
    }

    @Test
    fun androidKeystoreKeyGeneratesPersistsAcrossInstancesAndEnforcesGcmAndAad() {
        assertFalse(sealer.isKeyAvailable())
        sealer.ensureKeyExists()
        assertTrue(sealer.isKeyAvailable())

        val rowId = "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10"
        val json = """{"id":"$rowId","mood":2}"""

        val payload1 = sealer.sealUtf8(rowId, json)
        val payload2 = sealer.sealUtf8(rowId, json)

        assertTrue(payload1.size >= Sealer.MIN_PAYLOAD_BYTES)
        assertFalse(
            "Randomized GCM encryption in AndroidKeyStore must generate fresh IVs",
            payload1.copyOfRange(0, Sealer.IV_BYTES)
                .contentEquals(payload2.copyOfRange(0, Sealer.IV_BYTES)),
        )

        // Fresh KeystoreSealer instance loads the persisted AndroidKeyStore entry and opens both.
        val reopenedSealer = KeystoreSealer(alias = TEST_ALIAS)
        assertTrue(reopenedSealer.isKeyAvailable())
        assertEquals(json, reopenedSealer.openUtf8(rowId, payload1))
        assertEquals(json, reopenedSealer.openUtf8(rowId, payload2))
        assertArrayEquals(ByteArray(0), reopenedSealer.open(rowId, sealer.seal(rowId, ByteArray(0))))

        // Wrong rowId fails AAD verification.
        expectReason(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED) {
            reopenedSealer.openUtf8("00000000-0000-4000-8000-000000000000", payload1)
        }

        // Tampered IV, ciphertext body, and tag each fail GCM authentication.
        for (flipIndex in listOf(0, Sealer.IV_BYTES, payload1.lastIndex)) {
            val tampered = payload1.copyOf().also {
                it[flipIndex] = (it[flipIndex].toInt() xor 0x01).toByte()
            }
            expectReason(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED) {
                reopenedSealer.openUtf8(rowId, tampered)
            }
        }

        // Deleting the key leaves isKeyAvailable() false and never auto-creates a replacement key.
        reopenedSealer.deleteKeyIfPresent()
        assertFalse(reopenedSealer.isKeyAvailable())
        expectReason(SealException.Reason.KEY_UNAVAILABLE) {
            reopenedSealer.openUtf8(rowId, payload1)
        }
        assertFalse(reopenedSealer.isKeyAvailable())
    }

    private fun expectReason(expected: SealException.Reason, block: () -> Any?) {
        try {
            block()
            throw AssertionError("Expected SealException($expected)")
        } catch (e: SealException) {
            assertEquals(expected, e.reason)
        }
    }

    companion object {
        private const val TEST_ALIAS = "smileproc-keystore-androidtest-v1"
    }
}
