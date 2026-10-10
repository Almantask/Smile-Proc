package com.example.smileproc.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.smileproc.core.Sealer
import com.example.smileproc.core.SealerException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreSealer : Sealer {
    override fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val iv = cipher.iv
        return iv + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < 12 + 16) throw SealerException("sealed")
        val iv = sealed.copyOfRange(0, 12)
        val body = sealed.copyOfRange(12, sealed.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(body)
        } catch (error: GeneralSecurityException) {
            throw SealerException("open", error)
        }
    }

    private fun key(): SecretKey {
        val store = keyStore()
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        const val ALIAS = "smileproc-db-v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun hasKey(): Boolean {
            val store = keyStore()
            return store.containsAlias(ALIAS)
        }

        fun deleteKey() {
            val store = keyStore()
            if (store.containsAlias(ALIAS)) {
                store.deleteEntry(ALIAS)
            }
        }

        private fun keyStore(): KeyStore =
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }
}
