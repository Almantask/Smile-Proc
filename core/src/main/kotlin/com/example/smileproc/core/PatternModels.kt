package com.example.smileproc.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Grouping family enforcing at most one card item per family (`docs/spec/logic.md`). */
@Serializable
enum class Family(val wireName: String) {
    @SerialName("time")
    TIME("time"),

    @SerialName("weekday")
    WEEKDAY("weekday"),

    @SerialName("place")
    PLACE("place"),

    @SerialName("tag")
    TAG("tag"),
}

/** All 7 pattern detectors (`docs/spec/logic.md` › Pattern engine). */
@Serializable
enum class Detector(val wireName: String, val family: Family) {
    @SerialName("timeOfDay")
    TIME_OF_DAY("timeOfDay", Family.TIME),

    @SerialName("weekday")
    WEEKDAY("weekday", Family.WEEKDAY),

    @SerialName("weekdayDaypart")
    WEEKDAY_DAYPART("weekdayDaypart", Family.WEEKDAY),

    @SerialName("place")
    PLACE("place", Family.PLACE),

    @SerialName("awayFromHome")
    AWAY_FROM_HOME("awayFromHome", Family.PLACE),

    @SerialName("tagOnLow")
    TAG_ON_LOW("tagOnLow", Family.TAG),

    @SerialName("tagAtPlace")
    TAG_AT_PLACE("tagAtPlace", Family.TAG);

    companion object {
        fun fromWireName(name: String): Detector = entries.firstOrNull { it.wireName == name }
            ?: throw IllegalArgumentException("Unknown detector: $name")
    }
}

/** Mood comparison direction (`lower` when segment mean < rest mean, else `higher`). */
@Serializable
enum class Direction(val wireName: String) {
    @SerialName("lower")
    LOWER("lower"),

    @SerialName("higher")
    HIGHER("higher");

    companion object {
        fun fromWireName(name: String): Direction = entries.firstOrNull { it.wireName == name }
            ?: throw IllegalArgumentException("Unknown direction: $name")
    }
}

/** Candidate strength (`pattern` sorts ahead of `earlyHint`). */
@Serializable
enum class Confidence(val wireName: String) {
    @SerialName("pattern")
    PATTERN("pattern"),

    @SerialName("earlyHint")
    EARLY_HINT("earlyHint"),
}

/** Card-window span used by non-weekday detectors (`week` or 14-day `twoWeeks`). */
@Serializable
enum class WindowKind(val wireName: String) {
    @SerialName("week")
    WEEK("week"),

    @SerialName("twoWeeks")
    TWO_WEEKS("twoWeeks"),
}

/** Structured parameters of a [DetectedPattern] used for library matching and display rendering. */
@Serializable
data class PatternParams(
    val segment: String? = null,
    val weekday: String? = null,
    val clusterID: ClusterID? = null,
    val placeKind: PlaceKind? = null,
    val tag: String? = null,
)

/** Candidate pattern or early hint produced by [PatternEngine] (`docs/spec/logic.md`). */
@Serializable
data class DetectedPattern(
    val id: String,
    val detector: Detector,
    val family: Family,
    val direction: Direction?,
    val params: PatternParams,
    val nSegment: Int,
    val nRest: Int,
    val meanSegment: Double?,
    val meanRest: Double?,
    val diff: Double?,
    val z: Double?,
    val score: Double,
    val confidence: Confidence,
    val supportingEntryIDs: List<String>,
)
