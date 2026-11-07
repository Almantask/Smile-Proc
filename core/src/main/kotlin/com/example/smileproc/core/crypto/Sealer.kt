package com.example.smileproc.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Thrown when sealing or opening an encrypted payload fails.
 *
 * Never carries plaintext, ciphertext bytes, row content, or underlying JCA detail messages that
 * could echo input data.
 */
class SealException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {
    enum class Reason {
        KEY_UNAVAILABLE,
        INVALID_ROW_ID,
        PAYLOAD_TOO_SHORT,
        SEAL_FAILED,
        AUTHENTICATION_OR_DECRYPT_FAILED,
    }
}

/**
 * Seals and opens stored database payloads (`docs/spec/android.md` › Storage and encryption).
 *
 * Wire format: `IV (12 bytes) || ciphertext || GCM tag (16 bytes)`, `AES/GCM/NoPadding` with
 * 128-bit authentication tag and `AAD = rowId` UTF-8 bytes over canonical JSON UTF-8 bytes.
 */
interface Sealer {
    /** Whether the underlying encryption key currently exists and is usable. */
    fun isKeyAvailable(): Boolean

    @Throws(SealException::class)
    fun seal(rowId: String, plaintext: ByteArray): ByteArray

    @Throws(SealException::class)
    fun open(rowId: String, payload: ByteArray): ByteArray

    @Throws(SealException::class)
    fun sealUtf8(rowId: String, canonicalJson: String): ByteArray =
        seal(rowId, canonicalJson.toByteArray(Charsets.UTF_8))

    @Throws(SealException::class)
    fun openUtf8(rowId: String, payload: ByteArray): String =
        open(rowId, payload).toString(Charsets.UTF_8)

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val MIN_PAYLOAD_BYTES = IV_BYTES + TAG_BYTES
    }
}

/**
 * Pure Kotlin/JVM [Sealer] backed by standard JCA `AES/GCM/NoPadding` (`javax.crypto`).
 * Used in `:core` and Robolectric repository tests, and shares the exact wire format of
 * `:app`'s `KeystoreSealer`.
 */
class JcaSealer(
    private val keyProvider: () -> SecretKey?,
    private val random: SecureRandom = SecureRandom(),
) : Sealer {

    constructor(
        key: SecretKey,
        random: SecureRandom = SecureRandom(),
    ) : this(keyProvider = { key }, random = random)

    override fun isKeyAvailable(): Boolean = keyProvider() != null

    override fun seal(rowId: String, plaintext: ByteArray): ByteArray {
        val aad = validatedAad(rowId)
        val secretKey = keyProvider() ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)
        return try {
            val iv = ByteArray(Sealer.IV_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(Sealer.TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(Sealer.TAG_BITS, iv))
            cipher.updateAAD(aad)
            val cipherAndTag = cipher.doFinal(plaintext)
            ByteArray(iv.size + cipherAndTag.size).also { out ->
                System.arraycopy(iv, 0, out, 0, iv.size)
                System.arraycopy(cipherAndTag, 0, out, iv.size, cipherAndTag.size)
            }
        } catch (e: SealException) {
            throw e
        } catch (e: Exception) {
            throw SealException(SealException.Reason.SEAL_FAILED, e)
        }
    }

    override fun open(rowId: String, payload: ByteArray): ByteArray {
        val aad = validatedAad(rowId)
        if (payload.size < Sealer.MIN_PAYLOAD_BYTES) {
            throw SealException(SealException.Reason.PAYLOAD_TOO_SHORT)
        }
        val secretKey = keyProvider() ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)
        return try {
            val cipher = Cipher.getInstance(Sealer.TRANSFORMATION)
            val spec = GCMParameterSpec(Sealer.TAG_BITS, payload, 0, Sealer.IV_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.updateAAD(aad)
            cipher.doFinal(payload, Sealer.IV_BYTES, payload.size - Sealer.IV_BYTES)
        } catch (e: SealException) {
            throw e
        } catch (e: Exception) {
            throw SealException(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED, e)
        }
    }

    companion object {
        fun validatedAad(rowId: String): ByteArray {
            if (rowId.isBlank()) {
                throw SealException(SealException.Reason.INVALID_ROW_ID)
            }
            return rowId.toByteArray(Charsets.UTF_8)
        }

        fun generateAes256Key(random: SecureRandom = SecureRandom()): SecretKey {
            val generator = KeyGenerator.getInstance("AES")
            generator.init(256, random)
            return generator.generateKey()
        }

        fun withNewKey(random: SecureRandom = SecureRandom()): JcaSealer =
            JcaSealer(generateAes256Key(random), random)
    }
}
