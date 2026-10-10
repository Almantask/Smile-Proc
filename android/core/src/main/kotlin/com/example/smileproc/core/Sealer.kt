package com.example.smileproc.core

interface Sealer {
    fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray

    fun open(sealed: ByteArray, aad: ByteArray): ByteArray
}

class SealerException(message: String, cause: Throwable? = null) : Exception(message, cause)
