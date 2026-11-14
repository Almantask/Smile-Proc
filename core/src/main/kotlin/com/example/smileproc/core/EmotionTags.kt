package com.example.smileproc.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

/**
 * One middle-ring emotion (`<core>.<middle>`) and its outer-ring sub-emotions (`<core>.<outer>`)
 * on the 3-ring Feelings Wheel.
 */
@Serializable
data class MiddleEmotionGroup(
    val id: String,
    val outer: List<String>,
) {
    init {
        require(id.isNotBlank() && '.' in id) { "Invalid middle emotion id: $id" }
        require(outer.isNotEmpty() && outer.all { it.isNotBlank() && '.' in it }) {
            "Invalid outer emotion list for $id: $outer"
        }
        require(outer.distinct().size == outer.size) { "Duplicate outer emotions in $id" }
    }
}

/** One core emotion family, its flat fine tag ids (`<core>.<fine>`), and its 3-ring wheel groups. */
@Serializable
data class CoreEmotionTag(
    val id: String,
    val color: String,
    val fine: List<String>,
    val groups: List<MiddleEmotionGroup> = emptyList(),
) {
    init {
        require(id.isNotBlank() && '.' !in id) { "Invalid core emotion id: $id" }
        require(HEX_COLOR.matches(color)) { "Invalid hex color for $id: $color" }
        require(fine.isNotEmpty() && fine.all { it.startsWith("$id.") && it.substringAfter('.').isNotBlank() }) {
            "Invalid fine tag list for $id: $fine"
        }
        require(fine.distinct().size == fine.size) { "Duplicate fine tags in $id" }
        val fineSet = fine.toSet()
        for (group in groups) {
            require(group.id in fineSet) { "Middle group id ${group.id} must be listed in fine tags of $id" }
            require(group.outer.all { it in fineSet }) {
                "Outer tags ${group.outer} of ${group.id} must be listed in fine tags of $id"
            }
        }
    }
}

/** Catalog of core and fine emotion tags loaded from `content/emotion-tags.json`. */
@Serializable
data class EmotionTags(
    val version: Int,
    val cores: List<CoreEmotionTag>,
) {
    init {
        require(version == 1) { "Unsupported emotion-tags version: $version" }
        require(cores.isNotEmpty()) { "emotion-tags cores cannot be empty" }
        val ids = cores.map { it.id }
        require(ids.distinct().size == ids.size) { "Duplicate core emotion ids: $ids" }
    }

    @Transient
    val coreIds: List<String> = cores.map { it.id }

    @Transient
    val allTagIds: Set<String> = cores.flatMapTo(linkedSetOf()) { listOf(it.id) + it.fine }

    @Transient
    private val coreById: Map<String, CoreEmotionTag> = cores.associateBy { it.id }

    fun isValidTag(tagId: String): Boolean = tagId in allTagIds

    fun coreOf(tagId: String): String {
        require(tagId in allTagIds) { "Unknown emotion tag id: $tagId" }
        return tagId.substringBefore('.')
    }

    fun core(coreId: String): CoreEmotionTag =
        coreById[coreId] ?: throw IllegalArgumentException("Unknown core emotion id: $coreId")

    companion object {
        fun fromJson(raw: String): EmotionTags =
            CanonicalJson.json.decodeFromString(serializer(), raw)
    }
}
