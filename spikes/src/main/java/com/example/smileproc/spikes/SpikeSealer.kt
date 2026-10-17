package com.example.smileproc.spikes

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The spec's sealing (android.md › Storage and encryption) with its own alias: AES-256-GCM in
 * AndroidKeyStore, no user-authentication and no unlocked-device requirement, so a face tap on
 * the lock screen can seal. Payload = IV(12) ‖ ciphertext ‖ tag, AAD = row id.
 */
object SpikeSealer {
    private const val ALIAS = "spikes-db-v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    fun seal(id: String, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(id.toByteArray())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun open(id: String, payload: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, 12))
        cipher.updateAAD(id.toByteArray())
        return cipher.doFinal(payload, 12, payload.size - 12)
    }
}
