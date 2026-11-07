package com.example.smileproc.core

import kotlinx.serialization.Serializable

/**
 * Runtime pattern, place, weekly-card, and low-mood thresholds loaded strictly from
 * `content/pattern-config.json` (`docs/spec/data.md` › `content/pattern-config.json`).
 *
 * No property carries a default value so missing or misspelled keys fail immediately.
 */
@Serializable
data class PatternConfig(
    val version: Int,
    val minSegment: Int,
    val minRest: Int,
    val patternMinSegment: Int,
    val patternMinRest: Int,
    val minAbsDiff: Double,
    val zHint: Double,
    val zPattern: Double,
    val sdFloor: Double,
    val weekdayMinSegment: Int,
    val weekdayMinDistinctDates: Int,
    val weekdayMinAbsDiff: Double,
    val weekdayMinDataDays: Int,
    val tagMinLowTagged: Int,
    val tagMinCount: Int,
    val tagMinShare: Double,
    val tagMinShareGap: Double,
    val tagPatternCount: Int,
    val tagAtPlaceMinShare: Double,
    val fineTagDominance: Double,
    val shortWeekThreshold: Int,
    val maxItems: Int,
    val maxItemsLowWeek: Int,
    val notEnoughDataBelow: Int,
    val firstWeekDays: Int,
    val firstCardMinEntries: Int,
    val lowWeekMinEntries: Int,
    val lowWeekShare: Double,
    val lowRunLength: Int,
    val lowRunMinDays: Int,
    val placeRadiusMeters: Double,
    val placeMaxAccuracyMeters: Double,
) {
    init {
        require(version == 1) { "Unsupported pattern-config version: $version" }
        require(minSegment >= 1 && minRest >= 1) { "minSegment and minRest must be >= 1" }
        require(patternMinSegment >= minSegment && patternMinRest >= minRest) {
            "patternMinSegment/patternMinRest must be >= minSegment/minRest"
        }
        require(sdFloor > 0.0) { "sdFloor must be > 0" }
        require(placeRadiusMeters > 0.0 && placeMaxAccuracyMeters > 0.0) {
            "Place thresholds must be > 0"
        }
    }

    companion object {
        fun fromJson(raw: String): PatternConfig =
            CanonicalJson.json.decodeFromString(serializer(), raw)
    }
}
