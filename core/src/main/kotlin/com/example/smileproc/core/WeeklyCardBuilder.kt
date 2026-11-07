package com.example.smileproc.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

/** Weekly card layout variant (`docs/spec/logic.md` › Variant and limits). */
@Serializable
enum class Variant(val wireName: String) {
    @SerialName("notEnoughData")
    NOT_ENOUGH_DATA("notEnoughData"),

    @SerialName("firstWeek")
    FIRST_WEEK("firstWeek"),

    @SerialName("normal")
    NORMAL("normal"),
}

/** Selected pattern item stored inside a [WeeklyCard]. */
@Serializable
data class CardItem(
    val patternID: String,
    val libraryID: String,
    val pattern: DetectedPattern,
)

/**
 * Structured weekly reflection card (`docs/spec/logic.md` › Library matching and card building).
 *
 * - `moodCounts`: `[low, okay, good]` counts in `[periodStart, periodEnd)`.
 * - `days`: `7 × [low, okay, good]` counts indexed by ISO weekday `1 = Monday … 7 = Sunday`.
 */
@Serializable
data class WeeklyCard(
    @Serializable(with = IsoInstantSerializer::class) val periodStart: Instant,
    @Serializable(with = IsoInstantSerializer::class) val periodEnd: Instant,
    val windowKind: WindowKind,
    val entryCount: Int,
    val moodCounts: List<Int>,
    val days: List<List<Int>>,
    val isLowWeek: Boolean,
    val variant: Variant,
    val items: List<CardItem>,
) {
    init {
        require(moodCounts.size == 3 && moodCounts.all { it >= 0 }) {
            "moodCounts must have 3 non-negative elements [low, okay, good], got $moodCounts"
        }
        require(days.size == 7 && days.all { it.size == 3 && it.all { c -> c >= 0 } }) {
            "days must have 7 × [low, okay, good] non-negative counts"
        }
        require(entryCount == moodCounts.sum()) {
            "entryCount ($entryCount) must equal sum of moodCounts (${moodCounts.sum()})"
        }
    }
}

/** Result combining a built [WeeklyCard], its max item limit, and the full [PatternAnalysis]. */
data class WeeklyCardBuildResult(
    val card: WeeklyCard,
    val maxItemCount: Int,
    val analysis: PatternAnalysis,
)

/**
 * Builds structured [WeeklyCard]s (`docs/spec/logic.md` › Library matching and card building,
 * plus the human's balance-rule decision in `docs/progress.md`).
 */
object WeeklyCardBuilder {

    private val ENTRY_ORDER: Comparator<Entry> = compareBy<Entry> { it.createdAt }.thenBy { it.id }

    fun build(
        entries: List<Entry>,
        labels: List<PlaceLabel>,
        now: Instant,
        zone: ZoneId,
        config: PatternConfig,
        emotionTags: EmotionTags,
        library: PatternLibrary,
        cardTime: WeeklyCardTime = WeeklyCardTime.DEFAULT,
    ): WeeklyCardBuildResult {
        val period = CardPeriod.resolve(now, zone, cardTime)
        val sortedEntries = entries.sortedWith(ENTRY_ORDER)
        val analysis = PatternEngine.analyze(
            entries = sortedEntries,
            labels = labels,
            period = period,
            config = config,
            emotionTags = emotionTags,
        )
        return buildFromAnalysis(
            allSortedEntries = sortedEntries,
            analysis = analysis,
            config = config,
            library = library,
        )
    }

    fun buildFromAnalysis(
        allSortedEntries: List<Entry>,
        analysis: PatternAnalysis,
        config: PatternConfig,
        library: PatternLibrary,
    ): WeeklyCardBuildResult {
        val period = analysis.period
        val periodEntries = analysis.periodEntries
        val entryCount = periodEntries.size

        val lowCount = periodEntries.count { it.mood == Mood.LOW }
        val okayCount = periodEntries.count { it.mood == Mood.OKAY }
        val goodCount = periodEntries.count { it.mood == Mood.GOOD }

        val firstEntryEver = allSortedEntries.minWithOrNull(ENTRY_ORDER)
        val firstWeekCutoff = period.startDaysBeforeEnd(config.firstWeekDays)
        val variant = when {
            entryCount < config.notEnoughDataBelow -> Variant.NOT_ENOUGH_DATA
            firstEntryEver == null || firstEntryEver.createdAt.isAfter(firstWeekCutoff) -> Variant.FIRST_WEEK
            else -> Variant.NORMAL
        }

        val isLowWeek = entryCount >= config.lowWeekMinEntries &&
            (lowCount.toDouble() / entryCount.toDouble()) >= config.lowWeekShare

        val variantMax = when (variant) {
            Variant.NOT_ENOUGH_DATA, Variant.FIRST_WEEK -> 1
            Variant.NORMAL -> config.maxItems
        }
        val maxItems = if (isLowWeek) min(variantMax, config.maxItemsLowWeek) else variantMax

        val selectedPatterns = selectCandidates(analysis.candidates, maxItems)
        val items = selectedPatterns.map { pattern ->
            val libraryEntry = LibraryMatcher.matchOrThrow(pattern, library)
            CardItem(
                patternID = pattern.id,
                libraryID = libraryEntry.id,
                pattern = pattern,
            )
        }

        val dayBuckets = Array(7) { IntArray(3) }
        for (entry in periodEntries) {
            val dayIdx = entry.isoWeekday() - 1
            val moodIdx = entry.mood.value - 1
            dayBuckets[dayIdx][moodIdx]++
        }

        val card = WeeklyCard(
            periodStart = period.periodStart,
            periodEnd = period.periodEnd,
            windowKind = analysis.windowKind,
            entryCount = entryCount,
            moodCounts = listOf(lowCount, okayCount, goodCount),
            days = dayBuckets.map { it.toList() },
            isLowWeek = isLowWeek,
            variant = variant,
            items = items,
        )

        return WeeklyCardBuildResult(
            card = card,
            maxItemCount = maxItems,
            analysis = analysis,
        )
    }

    /**
     * Deterministic selection (`docs/spec/logic.md` › Selection & `docs/progress.md` › M2):
     * - Candidates are pre-sorted (`pattern` before `earlyHint`, 9-decimal score desc, `id` asc),
     *   so early hints only fill slots patterns didn't.
     * - Pick greedily with max one per family up to [limit].
     * - Balance swap: if `limit >= 2`, `picked.size >= 2`, and nothing picked is `higher`,
     *   replace the last pick with the highest-scoring `higher` candidate of `pattern` confidence
     *   whose family is not among `picked.dropLast(1)` (preserving max one per family).
     */
    fun selectCandidates(
        sortedCandidates: List<DetectedPattern>,
        limit: Int,
    ): List<DetectedPattern> {
        if (limit <= 0 || sortedCandidates.isEmpty()) return emptyList()

        val picked = mutableListOf<DetectedPattern>()
        val usedFamilies = hashSetOf<Family>()

        for (candidate in sortedCandidates) {
            if (picked.size >= limit) break
            if (candidate.family in usedFamilies) continue
            picked.add(candidate)
            usedFamilies.add(candidate.family)
        }

        if (limit >= 2 && picked.size >= 2 && picked.none { it.direction == Direction.HIGHER }) {
            val keptFamilies = picked.dropLast(1).mapTo(hashSetOf()) { it.family }
            val replacement = sortedCandidates.firstOrNull { candidate ->
                candidate.confidence == Confidence.PATTERN &&
                    candidate.direction == Direction.HIGHER &&
                    candidate.family !in keptFamilies
            }
            if (replacement != null) {
                picked[picked.lastIndex] = replacement
            }
        }

        return picked
    }

    /**
     * Returns the [CardPeriod] to build when the app opens at [now], or `null` if no new card
     * is due (`docs/spec/logic.md` › Timing).
     */
    fun dueCardPeriod(
        entries: List<Entry>,
        now: Instant,
        zone: ZoneId,
        existingPeriodStarts: Set<Instant>,
        config: PatternConfig,
        cardTime: WeeklyCardTime = WeeklyCardTime.DEFAULT,
    ): CardPeriod? {
        val period = CardPeriod.resolve(now, zone, cardTime)
        if (period.periodStart in existingPeriodStarts) return null
        if (existingPeriodStarts.isEmpty()) {
            val entriesAtCardTime = entries.count { !it.createdAt.isAfter(period.periodEnd) }
            if (entriesAtCardTime < config.firstCardMinEntries) return null
        }
        return period
    }
}
