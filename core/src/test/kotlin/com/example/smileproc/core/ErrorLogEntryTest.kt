package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorLogEntryTest {

    @Test
    fun roundTrip_serializesAndDeserializesCorrectly() {
        val entry = ErrorLogEntry(
            id = "test-error-1",
            timestamp = "2026-11-08T19:30:00.000Z",
            event = "STORAGE_OPEN_FAILED",
            errorClass = "com.example.smileproc.core.crypto.SealException",
            details = "AUTHENTICATION_OR_DECRYPT_FAILED",
        )

        val json = CanonicalJson.json.encodeToString(ErrorLogEntry.serializer(), entry)
        val decoded = CanonicalJson.json.decodeFromString(ErrorLogEntry.serializer(), json)

        assertEquals(entry, decoded)
        assertEquals("test-error-1", decoded.id)
        assertEquals("2026-11-08T19:30:00.000Z", decoded.timestamp)
        assertEquals("STORAGE_OPEN_FAILED", decoded.event)
        assertEquals("com.example.smileproc.core.crypto.SealException", decoded.errorClass)
        assertEquals("AUTHENTICATION_OR_DECRYPT_FAILED", decoded.details)
    }

    @Test
    fun roundTrip_withNullDetails() {
        val entry = ErrorLogEntry(
            id = "test-error-2",
            timestamp = "2026-11-08T19:35:00.000Z",
            event = "LOCATION_FIX_FAILED",
            errorClass = "java.lang.SecurityException",
            details = null,
        )

        val json = CanonicalJson.json.encodeToString(ErrorLogEntry.serializer(), entry)
        val decoded = CanonicalJson.json.decodeFromString(ErrorLogEntry.serializer(), json)

        assertEquals(entry, decoded)
        assertNull(decoded.details)
    }
}
