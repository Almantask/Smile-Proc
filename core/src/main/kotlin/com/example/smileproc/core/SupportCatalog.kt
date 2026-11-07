package com.example.smileproc.core

import kotlinx.serialization.Serializable
import java.util.Locale

/** Regional support contact from `content/support.json` (`docs/spec/data.md`). */
@Serializable
data class SupportContact(
    val region: String,
    val name: String,
    val phone: String? = null,
    val sms: String? = null,
    val url: String? = null,
    val verified: Boolean,
) {
    init {
        require(region.isNotBlank()) { "SupportContact region cannot be blank" }
        require(name.isNotBlank()) { "SupportContact name cannot be blank" }
    }
}

/**
 * Loader for human-owned `content/support.json`.
 * Entries with `verified == false` never show in dogfood or release builds.
 */
@Serializable
data class SupportCatalog(
    val version: Int,
    val entries: List<SupportContact>,
) {
    init {
        require(version == 1) { "Unsupported support.json version: $version" }
    }

    fun contactsForRegion(
        regionCode: String,
        includeUnverified: Boolean = false,
    ): List<SupportContact> {
        val target = regionCode.trim().uppercase(Locale.ROOT)
        if (target.isEmpty()) return emptyList()
        return entries.filter { contact ->
            contact.region.trim().uppercase(Locale.ROOT) == target &&
                (contact.verified || includeUnverified)
        }
    }

    companion object {
        fun fromJson(raw: String): SupportCatalog =
            CanonicalJson.contentJson.decodeFromString(serializer(), raw)
    }
}
