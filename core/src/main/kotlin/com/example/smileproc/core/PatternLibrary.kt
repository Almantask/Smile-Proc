package com.example.smileproc.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Match predicate inside `content/pattern-library.*.json`. */
@Serializable
data class LibraryMatchCriteria(
    val detector: String,
    val direction: String? = null,
    val segment: String? = null,
    val weekday: String? = null,
    val placeKind: String? = null,
    val tag: String? = null,
) {
    /** Count of specified keys (including `detector`) used for specificity ranking. */
    val keyCount: Int
        get() = 1 +
            (if (direction != null) 1 else 0) +
            (if (segment != null) 1 else 0) +
            (if (weekday != null) 1 else 0) +
            (if (placeKind != null) 1 else 0) +
            (if (tag != null) 1 else 0)
}

/** Rendered text fields of a [LibraryEntry] with placeholders substituted. */
data class RenderedLibraryEntry(
    val id: String,
    val title: String,
    val common: String,
    val tip: String,
    val question: String,
)

/** Hand-written weekly-card copy item loaded from `content/pattern-library.*.json`. */
@Serializable
data class LibraryEntry(
    val id: String,
    val match: LibraryMatchCriteria,
    val title: String,
    val common: String,
    val tip: String,
    val question: String,
) {
    init {
        require(id.isNotBlank()) { "LibraryEntry id cannot be blank" }
        require(title.isNotBlank() && common.isNotBlank() && tip.isNotBlank() && question.isNotBlank()) {
            "LibraryEntry $id has blank copy fields"
        }
    }

    fun render(
        segment: String? = null,
        weekday: String? = null,
        place: String? = null,
        tag: String? = null,
    ): RenderedLibraryEntry {
        fun substitute(template: String): String {
            var out = template
            if (segment != null) out = out.replace("{segment}", segment)
            if (weekday != null) out = out.replace("{weekday}", weekday)
            if (place != null) out = out.replace("{place}", place)
            if (tag != null) out = out.replace("{tag}", tag)
            return out
        }
        return RenderedLibraryEntry(
            id = id,
            title = substitute(title),
            common = substitute(common),
            tip = substitute(tip),
            question = substitute(question),
        )
    }
}

/** Loaded pattern library preserving file order for tie-breaking (`docs/spec/logic.md`). */
data class PatternLibrary(
    val entries: List<LibraryEntry>,
) {
    private val byId: Map<String, LibraryEntry>

    init {
        require(entries.isNotEmpty()) { "PatternLibrary cannot be empty" }
        val ids = entries.map { it.id }
        require(ids.distinct().size == ids.size) { "Duplicate library entry ids: $ids" }
        byId = entries.associateBy { it.id }
    }

    operator fun get(id: String): LibraryEntry? = byId[id]

    fun getValue(id: String): LibraryEntry =
        byId[id] ?: throw NoSuchElementException("Unknown library entry id: $id")

    companion object {
        fun fromJson(raw: String): PatternLibrary {
            val parsed = CanonicalJson.contentJson.decodeFromString(ListSerializer(LibraryEntry.serializer()), raw)
            return PatternLibrary(parsed)
        }
    }
}
