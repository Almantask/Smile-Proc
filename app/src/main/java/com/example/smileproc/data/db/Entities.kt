package com.example.smileproc.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Room entities (`docs/spec/android.md` › Storage and encryption).
 *
 * Every `payload` is `IV(12) || ciphertext || tag(16)` (`AES/GCM/NoPadding`, `AAD = row id` UTF-8
 * bytes) over canonical JSON. Only timestamps needed for ordering/lookup stay plaintext.
 */
@Entity(tableName = "entry", indices = [Index("createdAt")])
data class EntryRow(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntryRow) return false
        return id == other.id &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

@Entity(tableName = "place")
data class PlaceRow(
    @PrimaryKey val id: String,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaceRow) return false
        return id == other.id && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + payload.contentHashCode()
}

@Entity(tableName = "weekly_card")
data class WeeklyCardRow(
    @PrimaryKey val periodStart: Long,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeeklyCardRow) return false
        return periodStart == other.periodStart && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * periodStart.hashCode() + payload.contentHashCode()
}

@Entity(tableName = "entry_extras")
data class EntryExtrasRow(
    @PrimaryKey val entryId: String,
    val updatedAt: Long,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntryExtrasRow) return false
        return entryId == other.entryId &&
            updatedAt == other.updatedAt &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = entryId.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

@Entity(tableName = "custom_tag")
data class CustomTagRow(
    @PrimaryKey val id: String,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomTagRow) return false
        return id == other.id && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + payload.contentHashCode()
}
