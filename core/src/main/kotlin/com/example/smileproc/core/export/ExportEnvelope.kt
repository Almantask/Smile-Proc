package com.example.smileproc.core.export

import com.example.smileproc.core.Entry
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Reminder
import com.example.smileproc.core.WeeklyCardTime
import kotlinx.serialization.Serializable

/**
 * Key derivation parameters for the encrypted `.smileproc` export envelope
 * (`docs/spec/android.md` › Export and import).
 */
@Serializable
data class KdfParams(
    val name: String,
    val iterations: Int,
    val salt: String, // base64
)

/**
 * Top-level encrypted envelope written to and read from `.smileproc` files.
 */
@Serializable
data class ExportEnvelope(
    val format: String = FORMAT_NAME,
    val version: Int = FORMAT_VERSION,
    val kdf: KdfParams,
    val cipher: String = CIPHER_NAME,
    val payload: String, // base64 of nonce(12) || ciphertext || tag(16)
) {
    companion object {
        const val FORMAT_NAME = "smileproc-export"
        const val FORMAT_VERSION = 1
        const val CIPHER_NAME = "AES-256-GCM"
        const val KDF_NAME = "PBKDF2-HMAC-SHA256"
    }
}

@Serializable
data class ExportAppInfo(
    val platform: String = "android",
    val version: String,
)

@Serializable
data class ExportSettings(
    val reminders: List<Reminder>,
    val keepCheckIn: Boolean,
    val weeklyCard: WeeklyCardTime,
    val tagPickerMode: String,
    val captureLocation: Boolean,
)

/**
 * Plaintext content contained within the encrypted export payload.
 */
@Serializable
data class ExportPayload(
    val exportedAt: String,
    val app: ExportAppInfo,
    val entries: List<Entry>,
    val places: List<PlaceLabel>,
    val settings: ExportSettings,
)
