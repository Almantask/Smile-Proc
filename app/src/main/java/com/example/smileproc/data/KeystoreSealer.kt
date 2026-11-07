package com.example.smileproc.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.smileproc.SafeLog
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.core.crypto.Sealer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore [Sealer] (`docs/spec/android.md` › Storage and encryption).
 *
 * - Key alias: [KEY_ALIAS] (`smileproc-db-v1`), AES-256-GCM, no padding, randomized encryption.
 * - Deliberately sets **neither** `setUserAuthenticationRequired` nor `setUnlockedDeviceRequired`
 *   so notification face taps can seal and persist entries while the phone is locked (after the
 *   first unlock since boot).
 * - Never auto-creates a replacement key inside [open] or [hasUsableKey], so a present database
 *   with a missing/invalidated key routes to the recovery screen instead of silently stranding or
 *   wiping existing history.
 */
class KeystoreSealer(
    private val alias: String = KEY_ALIAS,
) : Sealer {

    @Synchronized
    override fun isKeyAvailable(): Boolean = loadExistingKey() != null

    /**
     * Provisions the Keystore key if it does not yet exist.
     * Call only on fresh install (when no database exists yet) or after explicit *Start fresh*.
     */
    @Synchronized
    @Throws(SealException::class)
    fun ensureKeyExists() {
        if (loadExistingKey() != null) return
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            generator.init(spec)
            generator.generateKey()
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.STORAGE_SEAL_FAILED, e)
            throw SealException(SealException.Reason.SEAL_FAILED, e)
        }
    }

    /** Deletes the Keystore entry if present (used by *Start fresh* and *Delete all data*). */
    @Synchronized
    fun deleteKeyIfPresent() {
        val store = openKeyStore()
        if (store.containsAlias(alias)) {
            store.deleteEntry(alias)
        }
    }

    override fun seal(rowId: String, plaintext: ByteArray): ByteArray {
        val aad = JcaSealer.validatedAad(rowId)
        val secretKey = loadExistingKey() ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)
        return try {
            val cipher = Cipher.getInstance(Sealer.TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.updateAAD(aad)
            val iv = cipher.iv
            if (iv == null || iv.size != Sealer.IV_BYTES) {
                throw SealException(SealException.Reason.SEAL_FAILED)
            }
            val cipherAndTag = cipher.doFinal(plaintext)
            ByteArray(iv.size + cipherAndTag.size).also { out ->
                System.arraycopy(iv, 0, out, 0, iv.size)
                System.arraycopy(cipherAndTag, 0, out, iv.size, cipherAndTag.size)
            }
        } catch (e: SealException) {
            throw e
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.STORAGE_SEAL_FAILED, e)
            throw SealException(SealException.Reason.SEAL_FAILED, e)
        }
    }

    override fun open(rowId: String, payload: ByteArray): ByteArray {
        val aad = JcaSealer.validatedAad(rowId)
        if (payload.size < Sealer.MIN_PAYLOAD_BYTES) {
            throw SealException(SealException.Reason.PAYLOAD_TOO_SHORT)
        }
        val secretKey = loadExistingKey() ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)
        return try {
            val cipher = Cipher.getInstance(Sealer.TRANSFORMATION)
            val spec = GCMParameterSpec(Sealer.TAG_BITS, payload, 0, Sealer.IV_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.updateAAD(aad)
            cipher.doFinal(payload, Sealer.IV_BYTES, payload.size - Sealer.IV_BYTES)
        } catch (e: SealException) {
            throw e
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.STORAGE_OPEN_FAILED, e)
            throw SealException(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED, e)
        }
    }

    @Synchronized
    private fun loadExistingKey(): SecretKey? {
        return try {
            val store = openKeyStore()
            if (!store.containsAlias(alias)) return null
            store.getKey(alias, null) as? SecretKey
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.STORAGE_KEY_MISSING, e)
            null
        }
    }

    private fun openKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    companion object {
        const val KEY_ALIAS = "smileproc-db-v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}
