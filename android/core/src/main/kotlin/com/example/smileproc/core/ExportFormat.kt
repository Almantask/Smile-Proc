package com.example.smileproc.core

import java.security.GeneralSecurityException
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ReminderSpec(
    val id: String,
    val hour: Int,
    val minute: Int,
    val weekdays: List<Int>,
)

data class WeeklyCardTime(
    val weekday: Int,
    val hour: Int,
    val minute: Int,
)

data class ExportSettings(
    val reminders: List<ReminderSpec>,
    val weeklyCard: WeeklyCardTime,
    val tagPickerMode: String,
)

data class ExportApp(val platform: String, val version: String)

data class ExportDocument(
    val exportedAt: Instant,
    val app: ExportApp,
    val entries: List<Entry>,
    val places: List<PlaceLabel>,
    val settings: ExportSettings,
)

data class ExportEnvelope(
    val format: String,
    val version: Int,
    val kdfName: String,
    val iterations: Int,
    val salt: ByteArray,
    val cipher: String,
    val payload: ByteArray,
) {
    fun payloadBase64(): String = Base64.getEncoder().encodeToString(payload)

    fun saltBase64(): String = Base64.getEncoder().encodeToString(salt)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportEnvelope) return false
        return format == other.format &&
            version == other.version &&
            kdfName == other.kdfName &&
            iterations == other.iterations &&
            salt.contentEquals(other.salt) &&
            cipher == other.cipher &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + version
        result = 31 * result + iterations
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

class ExportCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ExportFormat {
    const val PRODUCTION_ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8
    const val FORMAT = "smileproc-export"

    private val json = Json { ignoreUnknownKeys = true }

    fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun sealPayload(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    fun openPayload(payload: ByteArray, key: ByteArray): ByteArray {
        if (payload.size < 12 + 16) throw ExportCryptoException("payload")
        val nonce = payload.copyOfRange(0, 12)
        val body = payload.copyOfRange(12, payload.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.doFinal(body)
        } catch (error: GeneralSecurityException) {
            throw ExportCryptoException("decrypt", error)
        }
    }

    fun seal(
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        plaintext: ByteArray,
    ): ExportEnvelope {
        val key = deriveKey(password, salt, iterations)
        return ExportEnvelope(
            format = FORMAT,
            version = 1,
            kdfName = "PBKDF2-HMAC-SHA256",
            iterations = iterations,
            salt = salt,
            cipher = "AES-256-GCM",
            payload = sealPayload(plaintext, key, nonce),
        )
    }

    fun open(envelope: ExportEnvelope, password: String): ByteArray {
        val key = deriveKey(password, envelope.salt, envelope.iterations)
        return openPayload(envelope.payload, key)
    }

    fun parseEnvelope(text: String): ExportEnvelope {
        val obj = json.parseToJsonElement(text).jsonObject
        val kdf = obj.getValue("kdf").jsonObject
        return ExportEnvelope(
            format = obj.getValue("format").jsonPrimitive.content,
            version = obj.getValue("version").jsonPrimitive.int,
            kdfName = kdf.getValue("name").jsonPrimitive.content,
            iterations = kdf.getValue("iterations").jsonPrimitive.int,
            salt = Base64.getDecoder().decode(kdf.getValue("salt").jsonPrimitive.content),
            cipher = obj.getValue("cipher").jsonPrimitive.content,
            payload = Base64.getDecoder().decode(obj.getValue("payload").jsonPrimitive.content),
        )
    }
}

object ImportMerge {
    fun mergeEntries(existing: List<Entry>, incoming: List<Entry>): List<Entry> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        for (entry in incoming) {
            val current = byId[entry.id]
            if (current == null || entry.updatedAt.isAfter(current.updatedAt)) {
                byId[entry.id] = entry
            }
        }
        return byId.values.sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    fun mergePlaces(existing: List<PlaceLabel>, incoming: List<PlaceLabel>): List<PlaceLabel> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        for (place in incoming) {
            val current = byId[place.id]
            if (current == null || place.createdAt.isAfter(current.createdAt)) {
                byId[place.id] = place
            }
        }
        return byId.values.sortedBy { it.id }
    }
}
