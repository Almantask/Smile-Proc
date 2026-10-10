package com.example.smileproc.core

import java.time.Instant

data class EngineResult(
    val candidates: List<DetectedPattern>,
    val windowKind: String,
    val periodEntries: List<Entry>,
    val cardEntries: List<Entry>,
    val places: PlaceAssignment,
    val period: CardPeriod,
)

object PatternEngine {
    val dayparts = listOf("morning", "afternoon", "evening", "night")
    val weekdayDayparts = listOf("morning", "afternoon", "evening")
    val coreIds = listOf("happy", "calm", "sad", "anxious", "angry", "tired", "surprised")

    fun detect(
        entries: List<Entry>,
        labels: List<PlaceLabel>,
        config: PatternConfig,
        period: CardPeriod,
    ): EngineResult {
        val zone = period.zone
        val start = period.periodStart
        val end = period.periodEnd
        val ordered = entries.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val periodEntries = ordered.filter { !it.createdAt.isBefore(start) && it.createdAt.isBefore(end) }
        var cardEntries = periodEntries
        var windowKind = "oneWeek"
        if (periodEntries.size < config.shortWeekThreshold) {
            val windowStart = CardPeriods.shiftLocal(end, zone, 14, period.hour, period.minute)
            cardEntries = ordered.filter { !it.createdAt.isBefore(windowStart) && it.createdAt.isBefore(end) }
            windowKind = "twoWeeks"
        }
        val weekdayStart = CardPeriods.shiftLocal(end, zone, 28, period.hour, period.minute)
        val weekdayEntries = ordered.filter { !it.createdAt.isBefore(weekdayStart) && it.createdAt.isBefore(end) }
        val first = ordered.firstOrNull()
        val weekdayOk = first != null &&
            Instants.ageDays(first.createdAt, end) >= config.weekdayMinDataDays

        val places = PlaceResolver.resolve(ordered, labels, config)
        val clusters = places.entryClusters
        val candidates = mutableListOf<DetectedPattern>()

        fun addMean(
            detector: String,
            ident: String,
            params: Map<String, String>,
            segment: List<Entry>,
            rest: List<Entry>,
            weekdayRules: Boolean,
        ) {
            if (segment.isEmpty() || rest.isEmpty()) return
            val stats = compareMoods(segment.map { it.mood.value.toDouble() }, rest.map { it.mood.value.toDouble() }, config.sdFloor)
            val found = candidateMean(detector, ident, params, stats, segment, config, weekdayRules)
            if (found != null) candidates.add(found)
        }

        for (part in dayparts) {
            val seg = cardEntries.filter { Instants.daypart(it.createdAt, it.timeZoneID) == part }
            val rest = cardEntries.filter { Instants.daypart(it.createdAt, it.timeZoneID) != part }
            addMean("timeOfDay", "timeOfDay.{dir}.$part", mapOf("segment" to part), seg, rest, false)
        }

        if (weekdayOk) {
            for (day in 1..7) {
                val seg = weekdayEntries.filter { Instants.isoWeekday(it.createdAt, it.timeZoneID) == day }
                val rest = weekdayEntries.filter { Instants.isoWeekday(it.createdAt, it.timeZoneID) != day }
                addMean(
                    "weekday",
                    "weekday.{dir}.$day",
                    mapOf("weekday" to day.toString()),
                    seg,
                    rest,
                    true,
                )
                for (part in weekdayDayparts) {
                    val partSeg = weekdayEntries.filter {
                        Instants.isoWeekday(it.createdAt, it.timeZoneID) == day &&
                            Instants.daypart(it.createdAt, it.timeZoneID) == part
                    }
                    val segIds = partSeg.map { it.id }.toSet()
                    val partRest = weekdayEntries.filter { it.id !in segIds }
                    addMean(
                        "weekdayDaypart",
                        "weekdayDaypart.{dir}.$day.$part",
                        mapOf("weekday" to day.toString(), "segment" to part),
                        partSeg,
                        partRest,
                        true,
                    )
                }
            }
        }

        val clusterIds = cardEntries.mapNotNull { clusters[it.id] }.toSortedSet()
        for (cid in clusterIds) {
            val seg = cardEntries.filter { clusters[it.id] == cid }
            val rest = cardEntries.filter { it.id in clusters && clusters[it.id] != cid }
            val label = labels.firstOrNull { cid == "p:${it.id}" }
            val params = linkedMapOf("clusterID" to cid)
            if (label != null) params["placeKind"] = label.kind.wire
            addMean("place", "place.{dir}.$cid", params, seg, rest, false)
        }

        val home = labels.filter { it.kind == PlaceKind.HOME }.minByOrNull { it.id }
        if (home != null) {
            val homeId = "p:${home.id}"
            val seg = cardEntries.filter { it.id in clusters && clusters[it.id] != homeId }
            val rest = cardEntries.filter { clusters[it.id] == homeId }
            addMean("awayFromHome", "awayFromHome.{dir}", emptyMap(), seg, rest, false)
        }

        val lowTagged = cardEntries.filter { it.mood == Mood.LOW && it.tags.isNotEmpty() }
        if (lowTagged.size >= config.tagMinLowTagged) {
            val okayGood = cardEntries.filter { (it.mood == Mood.OKAY || it.mood == Mood.GOOD) && it.tags.isNotEmpty() }
            for (core in coreIds) {
                val hits = lowTagged.filter { entryHasCore(it, core) }
                val count = hits.size
                val shareSeg = count.toDouble() / lowTagged.size
                val shareRest = if (okayGood.isEmpty()) {
                    0.0
                } else {
                    okayGood.count { entryHasCore(it, core) }.toDouble() / okayGood.size
                }
                if (count < config.tagMinCount || shareSeg < config.tagMinShare) continue
                if (shareSeg - shareRest < config.tagMinShareGap) continue
                val tag = dominantTag(core, hits, config)
                val found = candidateTag(
                    "tagOnLow",
                    tag,
                    emptyMap(),
                    count,
                    shareSeg,
                    shareRest,
                    okayGood.size,
                    hits,
                    config,
                )
                if (found != null) candidates.add(found)
            }
        }

        for (cid in clusterIds) {
            val inCluster = cardEntries.filter { clusters[it.id] == cid }
            val tagged = inCluster.filter { it.tags.isNotEmpty() }
            if (tagged.isEmpty()) continue
            val elsewhere = cardEntries.filter { it.id in clusters && clusters[it.id] != cid && it.tags.isNotEmpty() }
            for (core in coreIds) {
                val hits = tagged.filter { entryHasCore(it, core) }
                val count = hits.size
                val shareSeg = count.toDouble() / tagged.size
                if (count < config.tagMinCount || shareSeg < config.tagAtPlaceMinShare) continue
                val shareRest = if (elsewhere.isEmpty()) {
                    0.0
                } else {
                    elsewhere.count { entryHasCore(it, core) }.toDouble() / elsewhere.size
                }
                if (shareSeg - shareRest < config.tagMinShareGap) continue
                val tag = dominantTag(core, hits, config)
                val found = candidateTag(
                    "tagAtPlace",
                    tag,
                    mapOf("clusterID" to cid),
                    count,
                    shareSeg,
                    shareRest,
                    elsewhere.size,
                    hits,
                    config,
                )
                if (found != null) candidates.add(found)
            }
        }

        candidates.sortWith(
            compareBy<DetectedPattern> { if (it.confidence == "pattern") 0 else 1 }
                .thenByDescending { Rounding.r9(it.score) }
                .thenBy { it.id },
        )
        return EngineResult(candidates, windowKind, periodEntries, cardEntries, places, period)
    }

    private fun entryHasCore(entry: Entry, core: String): Boolean =
        entry.tags.any { it == core || it.startsWith("$core.") }

    private fun dominantTag(core: String, hits: List<Entry>, config: PatternConfig): String {
        if (hits.isEmpty()) return core
        val counts = linkedMapOf<String, Int>()
        for (entry in hits) {
            for (tag in entry.tags) {
                if (tag.startsWith("$core.")) {
                    counts[tag] = (counts[tag] ?: 0) + 1
                }
            }
        }
        if (counts.isEmpty()) return core
        val best = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first()
        return if (best.value.toDouble() / hits.size >= config.fineTagDominance) best.key else core
    }

    private data class MoodStats(
        val nSegment: Int,
        val nRest: Int,
        val meanSegment: Double,
        val meanRest: Double,
        val diff: Double,
        val z: Double,
    )

    private fun mean(values: List<Double>): Double = values.sum() / values.size

    private fun sampleVariance(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val m = mean(values)
        return values.sumOf { (it - m) * (it - m) } / (values.size - 1)
    }

    private fun compareMoods(segment: List<Double>, rest: List<Double>, sdFloor: Double): MoodStats {
        val nSeg = segment.size
        val nRest = rest.size
        val meanSeg = mean(segment)
        val meanRest = mean(rest)
        val diff = meanSeg - meanRest
        val varSeg = sampleVariance(segment)
        val varRest = sampleVariance(rest)
        val denomN = nSeg + nRest - 2
        val sdPooled = if (denomN <= 0) {
            0.0
        } else {
            kotlin.math.sqrt(((nSeg - 1) * varSeg + (nRest - 1) * varRest) / denomN)
        }
        val z = diff / (maxOf(sdPooled, sdFloor) * kotlin.math.sqrt(1.0 / nSeg + 1.0 / nRest))
        return MoodStats(nSeg, nRest, meanSeg, meanRest, diff, z)
    }

    private fun familyOf(detector: String): String = when (detector) {
        "timeOfDay" -> "time"
        "weekday", "weekdayDaypart" -> "weekday"
        "place", "awayFromHome" -> "place"
        "tagOnLow", "tagAtPlace" -> "tag"
        else -> throw IllegalArgumentException(detector)
    }

    private fun distinctDates(entries: List<Entry>): Int =
        entries.map { Instants.localDate(it.createdAt, it.timeZoneID) }.toSet().size

    private fun candidateMean(
        detector: String,
        ident: String,
        params: Map<String, String>,
        stats: MoodStats,
        supporting: List<Entry>,
        config: PatternConfig,
        weekdayRules: Boolean,
    ): DetectedPattern? {
        val distinctPattern = !weekdayRules || distinctDates(supporting) >= config.weekdayMinDistinctDates
        val distinctHint = !weekdayRules || distinctDates(supporting) >= 1
        val patternN = if (weekdayRules) config.weekdayMinSegment else config.patternMinSegment
        val patternDiff = if (weekdayRules) config.weekdayMinAbsDiff else config.minAbsDiff
        val isPattern = stats.nSegment >= patternN &&
            stats.nRest >= config.patternMinRest &&
            kotlin.math.abs(stats.diff) >= patternDiff &&
            kotlin.math.abs(stats.z) >= config.zPattern &&
            distinctPattern
        val isHint = stats.nSegment >= config.minSegment &&
            stats.nRest >= config.minRest &&
            kotlin.math.abs(stats.diff) >= config.minAbsDiff &&
            kotlin.math.abs(stats.z) >= config.zHint &&
            distinctHint
        if (!isPattern && !isHint) return null
        val confidence = if (isPattern) "pattern" else "earlyHint"
        val direction = if (stats.diff < 0) "lower" else "higher"
        val score = kotlin.math.abs(stats.diff) * minOf(stats.nSegment, 10) / 10.0 +
            if (confidence == "pattern") 0.2 else 0.0
        return DetectedPattern(
            id = ident.replace("{dir}", direction),
            detector = detector,
            family = familyOf(detector),
            direction = direction,
            params = params,
            nSegment = stats.nSegment,
            nRest = stats.nRest,
            meanSegment = stats.meanSegment,
            meanRest = stats.meanRest,
            diff = stats.diff,
            z = stats.z,
            score = score,
            confidence = confidence,
            supportingEntryIDs = supporting.map { it.id },
        )
    }

    private fun candidateTag(
        detector: String,
        tag: String,
        params: Map<String, String>,
        count: Int,
        shareSeg: Double,
        shareRest: Double,
        nRest: Int,
        supporting: List<Entry>,
        config: PatternConfig,
    ): DetectedPattern? {
        if (count < config.tagMinCount) return null
        val confidence = if (count >= config.tagPatternCount) "pattern" else "earlyHint"
        val score = (shareSeg - shareRest) * minOf(count, 10) / 10.0 +
            if (confidence == "pattern") 0.2 else 0.0
        val suffix = params["clusterID"]?.let { ".$it" } ?: ""
        val stored = linkedMapOf("tag" to tag)
        stored.putAll(params)
        return DetectedPattern(
            id = "$detector.$tag$suffix",
            detector = detector,
            family = familyOf(detector),
            direction = null,
            params = stored,
            nSegment = count,
            nRest = nRest,
            meanSegment = null,
            meanRest = null,
            diff = null,
            z = null,
            score = score,
            confidence = confidence,
            supportingEntryIDs = supporting.map { it.id },
        )
    }
}

object WeeklyCardBuilder {
    fun build(
        entries: List<Entry>,
        labels: List<PlaceLabel>,
        config: PatternConfig,
        library: List<LibraryRow>,
        now: Instant,
        zoneName: String,
        weekday: Int,
        hour: Int,
        minute: Int,
    ): WeeklyCard {
        val prepared = entries.map { entry ->
            entry.copy(comment = entry.comment?.ifBlank { null }, tags = entry.tags)
        }
        val period = CardPeriods.cardPeriod(now, zoneName, weekday, hour, minute)
        val detected = PatternEngine.detect(prepared, labels, config, period)
        val variant = variantFor(detected.periodEntries, prepared, period.periodEnd, config)
        val low = isLowWeek(detected.periodEntries, config)
        val limit = itemLimit(variant, low, config)
        val picked = selectItems(detected.candidates, limit)
        val items = picked.mapNotNull { candidate ->
            val libraryId = LibraryMatcher.match(candidate, library) ?: return@mapNotNull null
            CardItem(patternID = candidate.id, libraryID = libraryId, pattern = candidate)
        }
        return WeeklyCard(
            periodStart = period.periodStart,
            periodEnd = period.periodEnd,
            windowKind = detected.windowKind,
            entryCount = detected.periodEntries.size,
            moodCounts = moodCounts(detected.periodEntries),
            days = weekStrip(detected.periodEntries, period.periodEnd, zoneName),
            isLowWeek = low,
            variant = variant,
            maxItemCount = limit,
            items = items,
            candidates = detected.candidates,
            dayparts = prepared.associate { it.id to Instants.daypart(it.createdAt, it.timeZoneID) },
            places = detected.places,
        )
    }

    fun variantFor(periodEntries: List<Entry>, allEntries: List<Entry>, periodEnd: Instant, config: PatternConfig): String {
        if (periodEntries.size < config.notEnoughDataBelow) return "notEnoughData"
        if (allEntries.isEmpty()) return "notEnoughData"
        val first = allEntries.minWith(compareBy({ it.createdAt }, { it.id }))
        val ageDays = Instants.ageDays(first.createdAt, periodEnd)
        if (ageDays <= config.firstWeekDays) return "firstWeek"
        return "normal"
    }

    fun itemLimit(variant: String, isLowWeek: Boolean, config: PatternConfig): Int {
        var limit = if (variant == "notEnoughData" || variant == "firstWeek") 1 else config.maxItems
        if (isLowWeek) limit = minOf(limit, config.maxItemsLowWeek)
        return limit
    }

    fun isLowWeek(periodEntries: List<Entry>, config: PatternConfig): Boolean {
        if (periodEntries.size < config.lowWeekMinEntries) return false
        val lows = periodEntries.count { it.mood == Mood.LOW }
        return lows.toDouble() / periodEntries.size >= config.lowWeekShare
    }

    fun selectItems(candidates: List<DetectedPattern>, limit: Int): List<DetectedPattern> {
        val patterns = candidates.filter { it.confidence == "pattern" }
        val hints = candidates.filter { it.confidence == "earlyHint" }
        val picked = mutableListOf<DetectedPattern>()
        val families = mutableSetOf<String>()
        fun take(pool: List<DetectedPattern>) {
            for (candidate in pool) {
                if (picked.size >= limit) return
                if (candidate.family in families) continue
                picked.add(candidate)
                families.add(candidate.family)
            }
        }
        take(patterns)
        take(hints)
        if (limit >= 2 && picked.isNotEmpty() && picked.none { it.direction == "higher" }) {
            val replacement = patterns.firstOrNull { it.direction == "higher" && it !in picked }
            if (replacement != null) {
                picked[picked.lastIndex] = replacement
            }
        }
        return picked
    }

    fun moodCounts(entries: List<Entry>): List<Int> =
        listOf(Mood.LOW, Mood.OKAY, Mood.GOOD).map { mood -> entries.count { it.mood == mood } }

    fun weekStrip(periodEntries: List<Entry>, periodEnd: Instant, zoneName: String): List<List<Int>> {
        val endLocal = periodEnd.atZone(java.time.ZoneId.of(zoneName)).minusNanos(1_000_000)
        val monday = endLocal.toLocalDate().minusDays((endLocal.dayOfWeek.value - 1).toLong())
        return (0 until 7).map { index ->
            val day = monday.plusDays(index.toLong())
            val onDay = periodEntries.filter { Instants.localDate(it.createdAt, it.timeZoneID) == day }
            moodCounts(onDay)
        }
    }
}

data class CardItem(
    val patternID: String,
    val libraryID: String,
    val pattern: DetectedPattern,
)

data class WeeklyCard(
    val periodStart: Instant,
    val periodEnd: Instant,
    val windowKind: String,
    val entryCount: Int,
    val moodCounts: List<Int>,
    val days: List<List<Int>>,
    val isLowWeek: Boolean,
    val variant: String,
    val maxItemCount: Int,
    val items: List<CardItem>,
    val candidates: List<DetectedPattern>,
    val dayparts: Map<String, String>,
    val places: PlaceAssignment,
)
