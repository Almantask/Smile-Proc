package com.example.smileproc.ui.week

import android.content.Context
import com.example.smileproc.R
import com.example.smileproc.core.CardItem
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.Confidence
import com.example.smileproc.core.Daypart
import com.example.smileproc.core.DetectedPattern
import com.example.smileproc.core.Detector
import com.example.smileproc.core.Direction
import com.example.smileproc.core.PatternLibrary
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.ui.tags.resolveTagLabel
import java.util.Locale

object PatternLibraryCatalog {
    private var cached: PatternLibrary? = null

    fun get(context: Context): PatternLibrary {
        cached?.let { return it }
        val json = context.assets.open("pattern-library.en.json").bufferedReader().use { it.readText() }
        val parsed = PatternLibrary.fromJson(json)
        cached = parsed
        return parsed
    }
}

data class RenderedCardItem(
    val patternId: String,
    val libraryId: String,
    val title: String,
    val observation: String,
    val common: String,
    val tip: String,
    val question: String,
    val isEarlyHint: Boolean,
    val unnamedClusterId: String?,
)

object WeeklyCardRenderer {

    fun renderItem(
        context: Context,
        item: CardItem,
        library: PatternLibrary,
        labels: List<PlaceLabel>,
        places: PlaceAssignment?,
    ): RenderedCardItem {
        val pattern = item.pattern
        val libraryEntry = library[item.libraryID]
            ?: library.entries.firstOrNull { it.match.detector == pattern.detector.wireName }
            ?: throw IllegalStateException("No library entry for pattern ${pattern.id}")

        val segmentStr = pattern.params.segment?.let { resolveSegmentString(context, it) }
        val weekdayStr = pattern.params.weekday?.let { resolveWeekdayString(context, it) }
        val (placeStr, unnamedClusterId) = resolvePlaceInfo(context, pattern, labels, places)
        val tagStr = pattern.params.tag?.let { context.resolveTagLabel(it).lowercase(Locale.getDefault()) }

        val renderedCopy = libraryEntry.render(
            segment = segmentStr,
            weekday = weekdayStr,
            place = placeStr,
            tag = tagStr,
        )

        val observation = renderObservation(
            context = context,
            pattern = pattern,
            segmentStr = segmentStr,
            weekdayStr = weekdayStr,
            placeStr = placeStr,
            tagStr = tagStr,
        )

        return RenderedCardItem(
            patternId = item.patternID,
            libraryId = item.libraryID,
            title = renderedCopy.title,
            observation = observation,
            common = renderedCopy.common,
            tip = renderedCopy.tip,
            question = renderedCopy.question,
            isEarlyHint = pattern.confidence == Confidence.EARLY_HINT,
            unnamedClusterId = unnamedClusterId,
        )
    }

    private fun resolveSegmentString(context: Context, rawSegment: String): String {
        return when (Daypart.entries.find { it.wireName == rawSegment }) {
            Daypart.MORNING -> context.getString(R.string.daypart_morning)
            Daypart.AFTERNOON -> context.getString(R.string.daypart_afternoon)
            Daypart.EVENING -> context.getString(R.string.daypart_evening)
            Daypart.NIGHT -> context.getString(R.string.daypart_night)
            null -> rawSegment
        }
    }

    private fun resolveWeekdayString(context: Context, rawWeekday: String): String {
        val dayNum = rawWeekday.toIntOrNull() ?: return rawWeekday
        val resId = when (dayNum) {
            1 -> R.string.weekday_1
            2 -> R.string.weekday_2
            3 -> R.string.weekday_3
            4 -> R.string.weekday_4
            5 -> R.string.weekday_5
            6 -> R.string.weekday_6
            7 -> R.string.weekday_7
            else -> null
        }
        return resId?.let { context.getString(it) } ?: rawWeekday
    }

    /**
     * Resolves the place name and whether "Name this place" should be shown.
     * Returns Pair(placeDisplayName, unnamedClusterIdOrNull).
     */
    fun resolvePlaceInfo(
        context: Context,
        pattern: DetectedPattern,
        labels: List<PlaceLabel>,
        places: PlaceAssignment?,
    ): Pair<String, String?> {
        val unnamedPlaceFallback = "an unnamed place"
        val clusterGoneFallback = "one of your places"

        val clusterID = pattern.params.clusterID ?: return Pair(clusterGoneFallback, null)
        val clusterVal = clusterID.value

        if (clusterVal.startsWith("p:")) {
            val labelId = clusterVal.removePrefix("p:")
            val label = labels.find { it.id == labelId }
            return if (label != null) {
                Pair(label.name, null)
            } else {
                Pair(clusterGoneFallback, null)
            }
        }

        if (clusterVal.startsWith("u:")) {
            val firstEntryId = clusterVal.removePrefix("u:")
            val currentClusterId = places?.clusterOf(firstEntryId)
            if (currentClusterId != null && currentClusterId.value.startsWith("p:")) {
                val labelId = currentClusterId.value.removePrefix("p:")
                val label = labels.find { it.id == labelId }
                if (label != null) {
                    return Pair(label.name, null)
                }
            }

            val isStillCluster = places == null || places.clusters.containsKey(clusterID)
            return if (isStillCluster) {
                val canName = pattern.detector in listOf(Detector.PLACE, Detector.TAG_AT_PLACE)
                Pair(unnamedPlaceFallback, if (canName) clusterVal else null)
            } else {
                Pair(clusterGoneFallback, null)
            }
        }

        return Pair(clusterGoneFallback, null)
    }

    private fun renderObservation(
        context: Context,
        pattern: DetectedPattern,
        segmentStr: String?,
        weekdayStr: String?,
        placeStr: String?,
        tagStr: String?,
    ): String {
        val count = pattern.nSegment
        val res = context.resources
        return when (pattern.detector) {
            Detector.TIME_OF_DAY -> {
                val seg = segmentStr ?: ""
                if (pattern.direction == Direction.HIGHER) {
                    res.getQuantityString(R.plurals.week_observation_timeOfDay_higher, count, seg, count)
                } else {
                    res.getQuantityString(R.plurals.week_observation_timeOfDay_lower, count, seg, count)
                }
            }
            Detector.WEEKDAY -> {
                val day = weekdayStr ?: ""
                if (pattern.direction == Direction.HIGHER) {
                    res.getQuantityString(R.plurals.week_observation_weekday_higher, count, day, count)
                } else {
                    res.getQuantityString(R.plurals.week_observation_weekday_lower, count, day, count)
                }
            }
            Detector.WEEKDAY_DAYPART -> {
                val day = weekdayStr ?: ""
                val seg = segmentStr ?: ""
                if (pattern.direction == Direction.HIGHER) {
                    res.getQuantityString(R.plurals.week_observation_weekdayDaypart_higher, count, day, seg, count)
                } else {
                    res.getQuantityString(R.plurals.week_observation_weekdayDaypart_lower, count, day, seg, count)
                }
            }
            Detector.PLACE -> {
                val pl = placeStr ?: "an unnamed place"
                if (pattern.direction == Direction.HIGHER) {
                    res.getQuantityString(R.plurals.week_observation_place_higher, count, pl, count)
                } else {
                    res.getQuantityString(R.plurals.week_observation_place_lower, count, pl, count)
                }
            }
            Detector.AWAY_FROM_HOME -> {
                if (pattern.direction == Direction.HIGHER) {
                    res.getQuantityString(R.plurals.week_observation_awayFromHome_higher, count, count)
                } else {
                    res.getQuantityString(R.plurals.week_observation_awayFromHome_lower, count, count)
                }
            }
            Detector.TAG_ON_LOW -> {
                val tag = tagStr ?: ""
                res.getQuantityString(R.plurals.week_observation_tagOnLow, count, tag, count)
            }
            Detector.TAG_AT_PLACE -> {
                val tag = tagStr ?: ""
                val pl = placeStr ?: "an unnamed place"
                res.getQuantityString(R.plurals.week_observation_tagAtPlace, count, tag, count, pl)
            }
        }
    }
}
