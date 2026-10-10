package com.example.smileproc.core

import java.time.Instant

data class Entry(
    val id: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val timeZoneID: String,
    val mood: Mood,
    val tags: List<String>,
    val comment: String?,
    val location: LocationSample?,
    val source: EntrySource,
) {
    init {
        require(id.isNotBlank())
        require(timeZoneID.isNotBlank())
    }

    fun normalized(allowedTags: Set<String>? = null): Entry {
        val cleanedTags = tags
            .filter { allowedTags == null || it in allowedTags }
            .distinct()
            .take(3)
        val cleanedComment = comment?.trim()?.take(1000)?.ifBlank { null }
        return copy(
            id = id.lowercase(),
            tags = cleanedTags,
            comment = cleanedComment,
        )
    }
}
