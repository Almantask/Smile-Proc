package com.example.smileproc.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val LOWERCASE_UUID = Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""")

/** 3-face mood scale (`1 = Low`, `2 = Okay`, `3 = Good`). */
@Serializable(with = MoodSerializer::class)
enum class Mood(val value: Int) {
    LOW(1),
    OKAY(2),
    GOOD(3);

    companion object {
        fun fromValue(value: Int): Mood = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("Mood must be 1, 2, or 3, got $value")
    }
}

object MoodSerializer : KSerializer<Mood> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.example.smileproc.core.Mood", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Mood) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): Mood {
        val raw = decoder.decodeInt()
        return try {
            Mood.fromValue(raw)
        } catch (e: IllegalArgumentException) {
            throw SerializationException(e.message, e)
        }
    }
}

/** Rating origin (`app` or `notification`). */
@Serializable
enum class EntrySource(val wireName: String) {
    @SerialName("app")
    APP("app"),

    @SerialName("notification")
    NOTIFICATION("notification"),
}

/** Location sample origin (`fresh` or `cached`). */
@Serializable
enum class LocationKind(val wireName: String) {
    @SerialName("fresh")
    FRESH("fresh"),

    @SerialName("cached")
    CACHED("cached"),
}

/** Local time-of-day bucket computed in an entry's own `timeZoneID`. */
@Serializable
enum class Daypart(val wireName: String) {
    @SerialName("morning")
    MORNING("morning"),

    @SerialName("afternoon")
    AFTERNOON("afternoon"),

    @SerialName("evening")
    EVENING("evening"),

    @SerialName("night")
    NIGHT("night");

    companion object {
        fun fromHour(hour: Int): Daypart {
            require(hour in 0..23) { "Hour must be in 0..23, got $hour" }
            return when (hour) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..21 -> EVENING
                else -> NIGHT
            }
        }

        fun fromWireName(name: String): Daypart = entries.firstOrNull { it.wireName == name }
            ?: throw IllegalArgumentException("Unknown daypart: $name")
    }
}

/** Place label category (`home`, `work`, or `other`). */
@Serializable
enum class PlaceKind(val wireName: String) {
    @SerialName("home")
    HOME("home"),

    @SerialName("work")
    WORK("work"),

    @SerialName("other")
    OTHER("other");

    companion object {
        fun fromWireName(name: String): PlaceKind = entries.firstOrNull { it.wireName == name }
            ?: throw IllegalArgumentException("Unknown place kind: $name")
    }
}

/** Captured GPS sample stored inside an [Entry]. */
@Serializable
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracy: Double,
    @Serializable(with = IsoInstantSerializer::class) val capturedAt: Instant,
    val kind: LocationKind,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Invalid latitude: $latitude"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Invalid longitude: $longitude"
        }
        require(horizontalAccuracy.isFinite() && horizontalAccuracy >= 0.0) {
            "Invalid horizontalAccuracy: $horizontalAccuracy"
        }
    }
}

/** Canonical mood check-in record (`docs/spec/data.md`). */
@Serializable
data class Entry(
    val id: String,
    @Serializable(with = IsoInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = IsoInstantSerializer::class) val updatedAt: Instant,
    val timeZoneID: String,
    val mood: Mood,
    val tags: List<String>,
    val comment: String?,
    val location: LocationSample?,
    val source: EntrySource,
) {
    init {
        require(LOWERCASE_UUID.matches(id)) { "Entry id must be a lower-case UUID: $id" }
        try {
            ZoneId.of(timeZoneID)
        } catch (e: DateTimeException) {
            throw IllegalArgumentException("Invalid IANA timeZoneID: $timeZoneID", e)
        }
        require(tags.size <= MAX_TAGS) { "At most $MAX_TAGS tags are allowed, got ${tags.size}" }
        require(tags.distinct().size == tags.size) { "Duplicate tags are not allowed: $tags" }
        if (comment != null) {
            require(comment.isNotEmpty() && comment == comment.trim()) {
                "Entry comment must be trimmed and null when empty"
            }
            require(comment.length <= MAX_COMMENT_LENGTH) {
                "Entry comment exceeds $MAX_COMMENT_LENGTH characters: ${comment.length}"
            }
        }
    }

    /** Entry creation instant expressed in the entry's own IANA zone. */
    fun zonedDateTime(): ZonedDateTime = createdAt.atZone(ZoneId.of(timeZoneID))

    /** Local calendar date in the entry's own `timeZoneID`. */
    fun localDate(): LocalDate = zonedDateTime().toLocalDate()

    /** ISO-8601 weekday (`1 = Monday … 7 = Sunday`) in the entry's own `timeZoneID`. */
    fun isoWeekday(): Int = zonedDateTime().dayOfWeek.value

    /** Daypart in the entry's own `timeZoneID`. */
    fun daypart(): Daypart = Daypart.fromHour(zonedDateTime().hour)

    /** True if any tag on this entry equals [coreId] or begins with `"$coreId."`. */
    fun hasCore(coreId: String): Boolean {
        val prefix = "$coreId."
        return tags.any { it == coreId || it.startsWith(prefix) }
    }

    companion object {
        const val MAX_TAGS = 3
        const val MAX_COMMENT_LENGTH = 1000

        /** Trims [raw], maps blank strings to `null`, and enforces the 1,000-character limit. */
        fun normalizeComment(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            require(trimmed.length <= MAX_COMMENT_LENGTH) {
                "Comment exceeds $MAX_COMMENT_LENGTH characters (${trimmed.length})"
            }
            return trimmed
        }
    }
}

/** Named place anchor (`docs/spec/data.md`). */
@Serializable
data class PlaceLabel(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val anchorLatitude: Double,
    val anchorLongitude: Double,
    @Serializable(with = IsoInstantSerializer::class) val createdAt: Instant,
) {
    init {
        require(LOWERCASE_UUID.matches(id)) { "PlaceLabel id must be a lower-case UUID: $id" }
        require(name.isNotBlank() && name == name.trim()) { "PlaceLabel name must be non-blank and trimmed" }
        require(anchorLatitude.isFinite() && anchorLatitude in -90.0..90.0) {
            "Invalid anchorLatitude: $anchorLatitude"
        }
        require(anchorLongitude.isFinite() && anchorLongitude in -180.0..180.0) {
            "Invalid anchorLongitude: $anchorLongitude"
        }
    }
}
