package com.example.smileproc.core

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class JcaSealer(
    private val key: ByteArray,
    private val nonceSource: () -> ByteArray = { randomNonce() },
) : Sealer {
    override fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = nonceSource()
        require(nonce.size == 12) { "nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < 12 + 16) throw SealerException("sealed")
        val nonce = sealed.copyOfRange(0, 12)
        val body = sealed.copyOfRange(12, sealed.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(body)
        } catch (error: GeneralSecurityException) {
            throw SealerException("open", error)
        }
    }

    companion object {
        fun randomNonce(): ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }
    }
}
