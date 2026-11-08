package com.example.smileproc.core.export

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ExportException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {
    enum class Reason {
        PASSWORD_TOO_SHORT,
        UNSUPPORTED_FORMAT,
        UNSUPPORTED_VERSION,
        UNSUPPORTED_CIPHER,
        UNSUPPORTED_KDF,
        INVALID_PAYLOAD,
        WRONG_PASSWORD_OR_CORRUPT,
    }
}

/**
 * Pure JVM cryptographic engine for `.smileproc` exports and imports
 * (`docs/spec/android.md` › Export and import).
 */
object ExportCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128
    const val TAG_BYTES = TAG_BITS / 8
    const val KEY_BITS = 256

    private val random = SecureRandom()

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        }
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(
        payloadJson: String,
        password: CharArray,
        salt: ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes),
        nonce: ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes),
        iterations: Int = DEFAULT_ITERATIONS,
    ): ExportEnvelope {
        if (password.size < MIN_PASSWORD_LENGTH) {
            throw ExportException(ExportException.Reason.PASSWORD_TOO_SHORT)
        }
        require(salt.size == SALT_BYTES) { "Salt must be $SALT_BYTES bytes, got ${salt.size}" }
        require(nonce.size == NONCE_BYTES) { "Nonce must be $NONCE_BYTES bytes, got ${nonce.size}" }

        val secretKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertextWithTag = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(nonce.size + ciphertextWithTag.size)
        System.arraycopy(nonce, 0, combined, 0, nonce.size)
        System.arraycopy(ciphertextWithTag, 0, combined, nonce.size, ciphertextWithTag.size)

        val b64Payload = Base64.getEncoder().encodeToString(combined)
        val b64Salt = Base64.getEncoder().encodeToString(salt)

        return ExportEnvelope(
            format = ExportEnvelope.FORMAT_NAME,
            version = ExportEnvelope.FORMAT_VERSION,
            kdf = KdfParams(
                name = ExportEnvelope.KDF_NAME,
                iterations = iterations,
                salt = b64Salt,
            ),
            cipher = ExportEnvelope.CIPHER_NAME,
            payload = b64Payload,
        )
    }

    fun decrypt(
        envelope: ExportEnvelope,
        password: CharArray,
    ): String {
        if (envelope.format != ExportEnvelope.FORMAT_NAME) {
            throw ExportException(ExportException.Reason.UNSUPPORTED_FORMAT)
        }
        if (envelope.version != ExportEnvelope.FORMAT_VERSION) {
            throw ExportException(ExportException.Reason.UNSUPPORTED_VERSION)
        }
        if (envelope.cipher != ExportEnvelope.CIPHER_NAME) {
            throw ExportException(ExportException.Reason.UNSUPPORTED_CIPHER)
        }
        if (envelope.kdf.name != ExportEnvelope.KDF_NAME) {
            throw ExportException(ExportException.Reason.UNSUPPORTED_KDF)
        }

        val salt = try {
            Base64.getDecoder().decode(envelope.kdf.salt)
        } catch (e: Exception) {
            throw ExportException(ExportException.Reason.INVALID_PAYLOAD, e)
        }
        val combined = try {
            Base64.getDecoder().decode(envelope.payload)
        } catch (e: Exception) {
            throw ExportException(ExportException.Reason.INVALID_PAYLOAD, e)
        }

        if (combined.size < NONCE_BYTES + TAG_BYTES) {
            throw ExportException(ExportException.Reason.INVALID_PAYLOAD)
        }

        val nonce = combined.copyOfRange(0, NONCE_BYTES)
        val ciphertextWithTag = combined.copyOfRange(NONCE_BYTES, combined.size)

        val secretKey = try {
            deriveKey(password, salt, envelope.kdf.iterations)
        } catch (e: IllegalArgumentException) {
            throw ExportException(ExportException.Reason.PASSWORD_TOO_SHORT, e)
        }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
            val plaintext = cipher.doFinal(ciphertextWithTag)
            plaintext.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw ExportException(ExportException.Reason.WRONG_PASSWORD_OR_CORRUPT, e)
        }
    }
}
