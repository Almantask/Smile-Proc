package com.example.smileproc.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Complete output of [PatternEngine.analyze] for a [CardPeriod]. */
data class PatternAnalysis(
    val period: CardPeriod,
    val windowKind: WindowKind,
    val periodEntries: List<Entry>,
    val placeAssignment: PlaceAssignment,
    val candidates: List<DetectedPattern>,
)

/**
 * Runs SmileProc's 7 on-device pattern detectors (`docs/spec/logic.md` › Pattern engine).
 */
object PatternEngine {

    private val ENTRY_ORDER: Comparator<Entry> = compareBy<Entry> { it.createdAt }.thenBy { it.id }

    private val WEEKDAY_DAYPARTS: List<Daypart> = listOf(
        Daypart.MORNING,
        Daypart.AFTERNOON,
        Daypart.EVENING,
    )

    val CANDIDATE_COMPARATOR: Comparator<DetectedPattern> = Comparator { a, b ->
        val confCmp = a.confidence.ordinal.compareTo(b.confidence.ordinal)
        if (confCmp != 0) return@Comparator confCmp

        val scoreCmp = roundScore9(b.score).compareTo(roundScore9(a.score))
        if (scoreCmp != 0) return@Comparator scoreCmp

        a.id.compareTo(b.id)
    }

    fun analyze(
        entries: List<Entry>,
        labels: List<PlaceLabel>,
        period: CardPeriod,
        config: PatternConfig,
        emotionTags: EmotionTags,
        precomputedPlaces: PlaceAssignment? = null,
    ): PatternAnalysis {
        val sortedEntries = entries.sortedWith(ENTRY_ORDER)
        val placeAssignment = precomputedPlaces ?: PlaceResolver.resolve(sortedEntries, labels, config)

        val periodEntries = sortedEntries.inHalfOpenWindow(period.periodStart, period.periodEnd)
        val windowKind = if (periodEntries.size < config.shortWeekThreshold) {
            WindowKind.TWO_WEEKS
        } else {
            WindowKind.WEEK
        }
        val cardWindowEntries = if (windowKind == WindowKind.TWO_WEEKS) {
            sortedEntries.inHalfOpenWindow(period.twoWeekStart, period.periodEnd)
        } else {
            periodEntries
        }

        val candidates = mutableListOf<DetectedPattern>()

        // 1. timeOfDay (card window, all entries)
        detectTimeOfDay(cardWindowEntries, config, candidates)

        // 2 & 3. weekday and weekdayDaypart (28-day window; skipped if first entry ever is < weekdayMinDataDays before periodEnd)
        val firstEntryEver = sortedEntries.firstOrNull()
        val weekdayCutoff = period.startDaysBeforeEnd(config.weekdayMinDataDays)
        if (firstEntryEver != null && !firstEntryEver.createdAt.isAfter(weekdayCutoff)) {
            val weekdayEntries = sortedEntries.inHalfOpenWindow(period.weekdayWindowStart, period.periodEnd)
            detectWeekday(weekdayEntries, config, candidates)
            detectWeekdayDaypart(weekdayEntries, config, candidates)
        }

        // 4 & 5. place and awayFromHome (card window, located/clustered entries only)
        val locatedCardEntries = cardWindowEntries.filter { placeAssignment.clusterOf(it.id) != null }
        detectPlace(locatedCardEntries, placeAssignment, config, candidates)
        detectAwayFromHome(locatedCardEntries, labels, placeAssignment, config, candidates)

        // 6 & 7. tagOnLow and tagAtPlace (card window)
        detectTagOnLow(cardWindowEntries, emotionTags, config, candidates)
        detectTagAtPlace(cardWindowEntries, placeAssignment, emotionTags, config, candidates)

        val sortedCandidates = candidates.sortedWith(CANDIDATE_COMPARATOR)

        return PatternAnalysis(
            period = period,
            windowKind = windowKind,
            periodEntries = periodEntries,
            placeAssignment = placeAssignment,
            candidates = sortedCandidates,
        )
    }

    private fun detectTimeOfDay(
        windowEntries: List<Entry>,
        config: PatternConfig,
        out: MutableList<DetectedPattern>,
    ) {
        if (windowEntries.isEmpty()) return
        for (daypart in Daypart.entries) {
            val (seg, rest) = windowEntries.partition { it.daypart() == daypart }
            evaluateMeanComparison(
                seg = seg,
                rest = rest,
                minSegForHint = config.minSegment,
                minSegForPattern = config.patternMinSegment,
                minAbsDiff = config.minAbsDiff,
                zPattern = config.zPattern,
                config = config,
                detector = Detector.TIME_OF_DAY,
                idForDirection = { dir -> "timeOfDay.${dir.wireName}.${daypart.wireName}" },
                params = PatternParams(segment = daypart.wireName),
            )?.let(out::add)
        }
    }

    private fun detectWeekday(
        weekdayEntries: List<Entry>,
        config: PatternConfig,
        out: MutableList<DetectedPattern>,
    ) {
        if (weekdayEntries.isEmpty()) return
        for (isoDay in 1..7) {
            val (seg, rest) = weekdayEntries.partition { it.isoWeekday() == isoDay }
            if (distinctLocalDates(seg) < config.weekdayMinDistinctDates) continue
            evaluateMeanComparison(
                seg = seg,
                rest = rest,
                minSegForHint = config.weekdayMinSegment,
                minSegForPattern = config.weekdayMinSegment,
                minAbsDiff = config.weekdayMinAbsDiff,
                zPattern = config.weekdayZPattern,
                config = config,
                detector = Detector.WEEKDAY,
                idForDirection = { dir -> "weekday.${dir.wireName}.$isoDay" },
                params = PatternParams(weekday = isoDay.toString()),
            )?.let(out::add)
        }
    }

    private fun detectWeekdayDaypart(
        weekdayEntries: List<Entry>,
        config: PatternConfig,
        out: MutableList<DetectedPattern>,
    ) {
        if (weekdayEntries.isEmpty()) return
        for (isoDay in 1..7) {
            for (daypart in WEEKDAY_DAYPARTS) {
                val (seg, rest) = weekdayEntries.partition {
                    it.isoWeekday() == isoDay && it.daypart() == daypart
                }
                if (distinctLocalDates(seg) < config.weekdayMinDistinctDates) continue
                evaluateMeanComparison(
                    seg = seg,
                    rest = rest,
                    minSegForHint = config.weekdayMinSegment,
                    minSegForPattern = config.weekdayMinSegment,
                    minAbsDiff = config.weekdayMinAbsDiff,
                    zPattern = config.weekdayZPattern,
                    config = config,
                    detector = Detector.WEEKDAY_DAYPART,
                    idForDirection = { dir -> "weekdayDaypart.${dir.wireName}.$isoDay.${daypart.wireName}" },
                    params = PatternParams(segment = daypart.wireName, weekday = isoDay.toString()),
                )?.let(out::add)
            }
        }
    }

    private fun detectPlace(
        locatedEntries: List<Entry>,
        placeAssignment: PlaceAssignment,
        config: PatternConfig,
        out: MutableList<DetectedPattern>,
    ) {
        if (locatedEntries.isEmpty()) return
        val clusterIds = locatedEntries
            .mapNotNull { placeAssignment.clusterOf(it.id) }
            .distinct()
            .sorted()

        for (clusterId in clusterIds) {
            val (seg, rest) = locatedEntries.partition { placeAssignment.clusterOf(it.id) == clusterId }
            val placeKind = placeAssignment.clusters[clusterId]?.label?.kind
            evaluateMeanComparison(
                seg = seg,
                rest = rest,
                minSegForHint = config.minSegment,
                minSegForPattern = config.patternMinSegment,
                minAbsDiff = config.minAbsDiff,
                zPattern = config.zPattern,
                config = config,
                detector = Detector.PLACE,
                idForDirection = { dir -> "place.${dir.wireName}.${clusterId.value}" },
                params = PatternParams(clusterID = clusterId, placeKind = placeKind),
            )?.let(out::add)
        }
    }

    private fun detectAwayFromHome(
        locatedEntries: List<Entry>,
        labels: List<PlaceLabel>,
        placeAssignment: PlaceAssignment,
        config: PatternConfig,
        out: MutableList<DetectedPattern>,
    ) {
        if (locatedEntries.isEmpty()) return
        val homeClusterIds = labels
            .filter { it.kind == PlaceKind.HOME }
            .mapTo(hashSetOf()) { ClusterID.named(it.id) }
        if (homeClusterIds.isEmpty()) return

        val (seg, rest) = locatedEntries.partition { placeAssignment.clusterOf(it.id) !in homeClusterIds }
        evaluateMeanComparison(
            seg = seg,
            rest = rest,
            minSegForHint = config.minSegment,
            minSegForPattern = config.patternMinSegment,
            minAbsDiff = config.minAbsDiff,
            zPattern = config.zPattern,
            config = config,
            detector = Detector.AWAY_FROM_HOME,
            idForDirection = { dir -> "awayFromHome.${dir.wireName}" },
            params = PatternParams(),
        )?.let(out::add)
    }

    private fun detectTagOnLow(
        windowEntries: List<Entry>,
        emotionTags: EmotionTags,
        config: PatternConfig,
        out: MutableList<DetectedPattern>,
    ) {
        val lowTagged = windowEntries.filter { it.mood == Mood.LOW && it.tags.isNotEmpty() }
        if (lowTagged.size < config.tagMinLowTagged) return
        val otherTagged = windowEntries.filter { it.mood != Mood.LOW && it.tags.isNotEmpty() }
        if (otherTagged.isEmpty()) return

        for (core in emotionTags.cores) {
            val hitEntries = lowTagged.filter { it.hasCore(core.id) }
            val count = hitEntries.size
            if (count < config.tagMinCount) continue

            val shareSegment = count.toDouble() / lowTagged.size.toDouble()
            if (shareSegment < config.tagMinShare) continue

            val restHits = otherTagged.count { it.hasCore(core.id) }
            val shareRest = restHits.toDouble() / otherTagged.size.toDouble()
            val shareGap = shareSegment - shareRest
            if (shareGap < config.tagMinShareGap) continue

            val confidence = if (count >= config.tagPatternCount) {
                Confidence.PATTERN
            } else {
                Confidence.EARLY_HINT
            }
            val score = shareGap * (min(count, 10).toDouble() / 10.0) +
                (if (confidence == Confidence.PATTERN) 0.2 else 0.0)
            val reportedTag = dominantFineOrCore(core, hitEntries, config.fineTagDominance)

            out.add(
                DetectedPattern(
                    id = "tagOnLow.$reportedTag",
                    detector = Detector.TAG_ON_LOW,
                    family = Family.TAG,
                    direction = null,
                    params = PatternParams(tag = reportedTag),
                    nSegment = count,
                    nRest = otherTagged.size,
                    meanSegment = null,
                    meanRest = null,
                    diff = null,
                    z = null,
                    score = score,
                    confidence = confidence,
                    supportingEntryIDs = hitEntries.map { it.id },
                ),
            )
        }
    }

    private fun detectTagAtPlace(
        windowEntries: List<Entry>,
        placeAssignment: PlaceAssignment,
        emotionTags: EmotionTags,
        config: PatternConfig,
        out: MutableList<DetectedPattern>,
    ) {
        val locatedTagged = windowEntries.filter {
            it.tags.isNotEmpty() && placeAssignment.clusterOf(it.id) != null
        }
        if (locatedTagged.isEmpty()) return

        val clusterIds = locatedTagged
            .mapNotNull { placeAssignment.clusterOf(it.id) }
            .distinct()
            .sorted()

        for (clusterId in clusterIds) {
            val (clusterTagged, elsewhereTagged) = locatedTagged.partition {
                placeAssignment.clusterOf(it.id) == clusterId
            }
            if (clusterTagged.isEmpty() || elsewhereTagged.isEmpty()) continue
            val placeKind = placeAssignment.clusters[clusterId]?.label?.kind

            for (core in emotionTags.cores) {
                val hitEntries = clusterTagged.filter { it.hasCore(core.id) }
                val count = hitEntries.size
                if (count < config.tagMinCount) continue

                val shareSegment = count.toDouble() / clusterTagged.size.toDouble()
                if (shareSegment < config.tagAtPlaceMinShare) continue

                val restHits = elsewhereTagged.count { it.hasCore(core.id) }
                val shareRest = restHits.toDouble() / elsewhereTagged.size.toDouble()
                val shareGap = shareSegment - shareRest
                if (shareGap < config.tagMinShareGap) continue

                val confidence = if (count >= config.tagPatternCount) {
                    Confidence.PATTERN
                } else {
                    Confidence.EARLY_HINT
                }
                val score = shareGap * (min(count, 10).toDouble() / 10.0) +
                    (if (confidence == Confidence.PATTERN) 0.2 else 0.0)
                val reportedTag = dominantFineOrCore(core, hitEntries, config.fineTagDominance)

                out.add(
                    DetectedPattern(
                        id = "tagAtPlace.$reportedTag.${clusterId.value}",
                        detector = Detector.TAG_AT_PLACE,
                        family = Family.TAG,
                        direction = null,
                        params = PatternParams(
                            clusterID = clusterId,
                            placeKind = placeKind,
                            tag = reportedTag,
                        ),
                        nSegment = count,
                        nRest = elsewhereTagged.size,
                        meanSegment = null,
                        meanRest = null,
                        diff = null,
                        z = null,
                        score = score,
                        confidence = confidence,
                        supportingEntryIDs = hitEntries.map { it.id },
                    ),
                )
            }
        }
    }

    private fun evaluateMeanComparison(
        seg: List<Entry>,
        rest: List<Entry>,
        minSegForHint: Int,
        minSegForPattern: Int,
        minAbsDiff: Double,
        zPattern: Double,
        config: PatternConfig,
        detector: Detector,
        idForDirection: (Direction) -> String,
        params: PatternParams,
    ): DetectedPattern? {
        val nSeg = seg.size
        val nRest = rest.size
        if (nSeg < minSegForHint || nRest < config.minRest) return null

        val meanSeg = meanMood(seg)
        val meanRest = meanMood(rest)
        val diff = meanSeg - meanRest
        val absDiff = abs(diff)
        if (absDiff < minAbsDiff) return null

        val varSeg = sampleVariance(seg, meanSeg)
        val varRest = sampleVariance(rest, meanRest)
        val sdPooled = if (nSeg + nRest <= 2) {
            0.0
        } else {
            sqrt(((nSeg - 1) * varSeg + (nRest - 1) * varRest) / (nSeg + nRest - 2).toDouble())
        }
        val se = max(sdPooled, config.sdFloor) * sqrt(1.0 / nSeg.toDouble() + 1.0 / nRest.toDouble())
        val z = diff / se
        val absZ = abs(z)
        if (absZ < config.zHint) return null

        val isPattern = nSeg >= minSegForPattern &&
            nRest >= config.patternMinRest &&
            absZ >= zPattern
        val confidence = if (isPattern) Confidence.PATTERN else Confidence.EARLY_HINT
        val direction = if (diff < 0.0) Direction.LOWER else Direction.HIGHER
        val score = absDiff * (min(nSeg, 10).toDouble() / 10.0) + (if (isPattern) 0.2 else 0.0)

        return DetectedPattern(
            id = idForDirection(direction),
            detector = detector,
            family = detector.family,
            direction = direction,
            params = params,
            nSegment = nSeg,
            nRest = nRest,
            meanSegment = meanSeg,
            meanRest = meanRest,
            diff = diff,
            z = z,
            score = score,
            confidence = confidence,
            supportingEntryIDs = seg.map { it.id },
        )
    }

    private fun dominantFineOrCore(
        core: CoreEmotionTag,
        hitEntries: List<Entry>,
        dominanceThreshold: Double,
    ): String {
        val total = hitEntries.size
        if (total == 0) return core.id
        var bestFine: String? = null
        var bestHits = 0
        for (fineId in core.fine) {
            val hits = hitEntries.count { fineId in it.tags }
            if (hits.toDouble() / total.toDouble() >= dominanceThreshold && hits > bestHits) {
                bestHits = hits
                bestFine = fineId
            }
        }
        return bestFine ?: core.id
    }

    private fun meanMood(entries: List<Entry>): Double {
        var sum = 0.0
        for (e in entries) {
            sum += e.mood.value.toDouble()
        }
        return sum / entries.size.toDouble()
    }

    private fun sampleVariance(entries: List<Entry>, mean: Double): Double {
        if (entries.size <= 1) return 0.0
        var sumSq = 0.0
        for (e in entries) {
            val d = e.mood.value.toDouble() - mean
            sumSq += d * d
        }
        return sumSq / (entries.size - 1).toDouble()
    }

    private fun distinctLocalDates(entries: List<Entry>): Int =
        entries.mapTo(hashSetOf()) { it.localDate() }.size

    private fun List<Entry>.inHalfOpenWindow(startInclusive: Instant, endExclusive: Instant): List<Entry> =
        filter { !it.createdAt.isBefore(startInclusive) && it.createdAt.isBefore(endExclusive) }

    fun roundScore9(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(9, RoundingMode.HALF_UP)
}
