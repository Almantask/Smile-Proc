package com.example.smileproc.core

import kotlinx.serialization.Serializable

/**
 * Record of an error or failure event stored locally and sealed at rest.
 *
 * Strictly non-sensitive per privacy non-negotiables: captures event name, timestamp,
 * exception class, and safe diagnostic reason code only. Never captures user content,
 * coordinates, place names, or arbitrary error messages.
 */
@Serializable
data class ErrorLogEntry(
    val id: String,
    val timestamp: String,
    val event: String,
    val errorClass: String,
    val details: String? = null,
)
