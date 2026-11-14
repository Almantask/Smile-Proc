package com.example.smileproc.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Descriptive mood distribution for a single calendar date. Zero scores or streaks.
 */
data class DayMoodSummary(
    val date: LocalDate,
    val total: Int,
    val lowCount: Int,
    val okayCount: Int,
    val goodCount: Int,
    val entryIds: List<String>,
)

/**
 * Descriptive mood counts across one [Daypart] (`Morning`, `Afternoon`, `Evening`, `Night`).
 */
data class DaypartMoodSummary(
    val daypart: Daypart,
    val total: Int,
    val lowCount: Int,
    val okayCount: Int,
    val goodCount: Int,
)

/**
 * Descriptive mood counts for one ISO weekday (`1 = Monday` .. `7 = Sunday`).
 */
data class WeekdayMoodSummary(
    val isoWeekday: Int,
    val total: Int,
    val lowCount: Int,
    val okayCount: Int,
    val goodCount: Int,
)

/**
 * Multi-week / monthly rhythm report for 30-day or 90-day windows.
 */
data class MonthlyRhythmReport(
    val days: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalEntries: Int,
    val activeDays: Int,
    val daySummaries: List<DayMoodSummary>,
    val byDaypart: List<DaypartMoodSummary>,
    val byWeekday: List<WeekdayMoodSummary>,
)

/**
 * Descriptive breakdown for a single emotion, context, or custom personal tag.
 */
data class TagMoodSummary(
    val tagId: String,
    val kind: Kind,
    val coreId: String?,
    val total: Int,
    val lowCount: Int,
    val okayCount: Int,
    val goodCount: Int,
) {
    enum class Kind {
        EMOTION,
        CONTEXT,
        CUSTOM,
    }
}

/**
 * Top tags co-occurring with a particular [Mood] (`Low`, `Okay`, or `Good`).
 */
data class MoodTagHighlight(
    val mood: Mood,
    val entryCount: Int,
    val topTags: List<TagMoodSummary>,
)

/**
 * Descriptive mood & tag summary for a resolved location cluster.
 */
data class PlaceMoodSummary(
    val clusterId: ClusterID,
    val label: PlaceLabel?,
    val isUnnamed: Boolean,
    val centroid: LatLon,
    val memberEntries: List<Entry>,
    val total: Int,
    val lowCount: Int,
    val okayCount: Int,
    val goodCount: Int,
    val topTagIds: List<String>,
)

/**
 * Aggregated emotion, context/custom tag, per-mood, and per-place breakdowns.
 */
data class BreakdownsReport(
    val emotionTags: List<TagMoodSummary>,
    val contextAndCustomTags: List<TagMoodSummary>,
    val byMood: Map<Mood, MoodTagHighlight>,
    val places: List<PlaceMoodSummary>,
)

/**
 * In-memory filter specification for History search and chips.
 */
data class HistoryFilter(
    val query: String = "",
    val moods: Set<Mood> = emptySet(),
    val bookmarkedOnly: Boolean = false,
    val withNoteOnly: Boolean = false,
    val coreTagIds: Set<String> = emptySet(),
    val contextOrCustomTagIds: Set<String> = emptySet(),
    val clusterIds: Set<ClusterID> = emptySet(),
) {
    val isActive: Boolean
        get() = query.isNotBlank() ||
            moods.isNotEmpty() ||
            bookmarkedOnly ||
            withNoteOnly ||
            coreTagIds.isNotEmpty() ||
            contextOrCustomTagIds.isNotEmpty() ||
            clusterIds.isNotEmpty()
}

/**
 * Pure JVM analytics and search helpers powering Month Rhythm, Insights breakdowns, and History filtering.
 *
 * All calendar dates, dayparts, and weekdays are resolved using each entry's own `timeZoneID`.
 */
object InsightsEngine {

    fun buildMonthlyRhythm(
        entries: List<Entry>,
        days: Int,
        today: LocalDate,
    ): MonthlyRhythmReport {
        require(days >= 1) { "days must be >= 1, got $days" }
        val startDate = today.minusDays((days - 1).toLong())
        val entriesInWindow = entries
            .filter { entry ->
                val date = entry.localDate()
                !date.isBefore(startDate) && !date.isAfter(today)
            }
            .sortedWith(compareBy<Entry> { it.createdAt }.thenBy { it.id })

        val byDate = entriesInWindow.groupBy { it.localDate() }
        val daySummaries = (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val dayEntries = byDate[date].orEmpty()
            DayMoodSummary(
                date = date,
                total = dayEntries.size,
                lowCount = dayEntries.count { it.mood == Mood.LOW },
                okayCount = dayEntries.count { it.mood == Mood.OKAY },
                goodCount = dayEntries.count { it.mood == Mood.GOOD },
                entryIds = dayEntries.map { it.id },
            )
        }

        val byDaypartMap = entriesInWindow.groupBy { it.daypart() }
        val byDaypart = Daypart.entries.map { dp ->
            val bucket = byDaypartMap[dp].orEmpty()
            DaypartMoodSummary(
                daypart = dp,
                total = bucket.size,
                lowCount = bucket.count { it.mood == Mood.LOW },
                okayCount = bucket.count { it.mood == Mood.OKAY },
                goodCount = bucket.count { it.mood == Mood.GOOD },
            )
        }

        val byWeekdayMap = entriesInWindow.groupBy { it.isoWeekday() }
        val byWeekday = (1..7).map { isoDay ->
            val bucket = byWeekdayMap[isoDay].orEmpty()
            WeekdayMoodSummary(
                isoWeekday = isoDay,
                total = bucket.size,
                lowCount = bucket.count { it.mood == Mood.LOW },
                okayCount = bucket.count { it.mood == Mood.OKAY },
                goodCount = bucket.count { it.mood == Mood.GOOD },
            )
        }

        val activeDays = daySummaries.count { it.total > 0 }

        return MonthlyRhythmReport(
            days = days,
            startDate = startDate,
            endDate = today,
            totalEntries = entriesInWindow.size,
            activeDays = activeDays,
            daySummaries = daySummaries,
            byDaypart = byDaypart,
            byWeekday = byWeekday,
        )
    }

    fun buildMonthlyRhythm(
        entries: List<Entry>,
        days: Int,
        now: Instant,
        zone: ZoneId,
    ): MonthlyRhythmReport = buildMonthlyRhythm(
        entries = entries,
        days = days,
        today = now.atZone(zone).toLocalDate(),
    )

    fun buildBreakdowns(
        entries: List<Entry>,
        extrasByEntryId: Map<String, EntryExtras>,
        customTags: List<CustomTag>,
        placeAssignment: PlaceAssignment?,
        emotionTags: EmotionTags,
        maxTagsPerMood: Int = 5,
    ): BreakdownsReport {
        val validCustomTagIds = customTags.mapTo(hashSetOf()) { it.id }

        data class MutableCounts(
            val kind: TagMoodSummary.Kind,
            val coreId: String?,
            var low: Int = 0,
            var okay: Int = 0,
            var good: Int = 0,
        ) {
            fun record(mood: Mood) {
                when (mood) {
                    Mood.LOW -> low++
                    Mood.OKAY -> okay++
                    Mood.GOOD -> good++
                }
            }

            fun toSummary(tagId: String): TagMoodSummary = TagMoodSummary(
                tagId = tagId,
                kind = kind,
                coreId = coreId,
                total = low + okay + good,
                lowCount = low,
                okayCount = okay,
                goodCount = good,
            )
        }

        val countsByTagKey = linkedMapOf<Pair<TagMoodSummary.Kind, String>, MutableCounts>()

        for (entry in entries) {
            for (tagId in entry.tags.distinct()) {
                val coreId = if (emotionTags.isValidTag(tagId)) emotionTags.coreOf(tagId) else null
                val key = TagMoodSummary.Kind.EMOTION to tagId
                countsByTagKey
                    .getOrPut(key) { MutableCounts(TagMoodSummary.Kind.EMOTION, coreId) }
                    .record(entry.mood)
            }
            val extras = extrasByEntryId[entry.id] ?: continue
            for (ctxId in extras.contextTags.distinct()) {
                if (!BuiltInContextTag.isContextTag(ctxId)) continue
                val key = TagMoodSummary.Kind.CONTEXT to ctxId
                countsByTagKey
                    .getOrPut(key) { MutableCounts(TagMoodSummary.Kind.CONTEXT, null) }
                    .record(entry.mood)
            }
            for (customId in extras.customTagIds.distinct()) {
                if (customId !in validCustomTagIds) continue
                val key = TagMoodSummary.Kind.CUSTOM to customId
                countsByTagKey
                    .getOrPut(key) { MutableCounts(TagMoodSummary.Kind.CUSTOM, null) }
                    .record(entry.mood)
            }
        }

        val allSummaries = countsByTagKey.entries.map { (key, counts) ->
            counts.toSummary(key.second)
        }

        val emotionSummaries = allSummaries
            .filter { it.kind == TagMoodSummary.Kind.EMOTION }
            .sortedWith(compareByDescending<TagMoodSummary> { it.total }.thenBy { it.tagId })

        val contextAndCustomSummaries = allSummaries
            .filter { it.kind != TagMoodSummary.Kind.EMOTION }
            .sortedWith(compareByDescending<TagMoodSummary> { it.total }.thenBy { it.tagId })

        val byMood = Mood.entries.associateWith { mood ->
            val moodCount = entries.count { it.mood == mood }
            val topTags = allSummaries
                .map { summary ->
                    val countForMood = when (mood) {
                        Mood.LOW -> summary.lowCount
                        Mood.OKAY -> summary.okayCount
                        Mood.GOOD -> summary.goodCount
                    }
                    summary to countForMood
                }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<TagMoodSummary, Int>> { it.second }
                        .thenByDescending { it.first.total }
                        .thenBy { it.first.tagId },
                )
                .take(maxTagsPerMood)
                .map { it.first }
            MoodTagHighlight(
                mood = mood,
                entryCount = moodCount,
                topTags = topTags,
            )
        }

        val entriesById = entries.associateBy { it.id }
        val placeSummaries = if (placeAssignment == null) {
            emptyList()
        } else {
            placeAssignment.clusters.values
                .mapNotNull { cluster ->
                    val members = cluster.memberIDs.mapNotNull { entriesById[it] }
                    if (members.isEmpty()) return@mapNotNull null

                    val tagFrequency = linkedMapOf<String, Int>()
                    for (member in members) {
                        for (tag in member.tags.distinct()) {
                            tagFrequency[tag] = (tagFrequency[tag] ?: 0) + 1
                        }
                        val extras = extrasByEntryId[member.id]
                        if (extras != null) {
                            for (ctx in extras.contextTags.distinct()) {
                                if (BuiltInContextTag.isContextTag(ctx)) {
                                    tagFrequency[ctx] = (tagFrequency[ctx] ?: 0) + 1
                                }
                            }
                            for (cid in extras.customTagIds.distinct()) {
                                if (cid in validCustomTagIds) {
                                    tagFrequency[cid] = (tagFrequency[cid] ?: 0) + 1
                                }
                            }
                        }
                    }

                    val topTagIds = tagFrequency.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                        .take(3)
                        .map { it.key }

                    PlaceMoodSummary(
                        clusterId = cluster.id,
                        label = cluster.label,
                        isUnnamed = cluster.id.isUnnamed,
                        centroid = cluster.centroid,
                        memberEntries = members,
                        total = members.size,
                        lowCount = members.count { it.mood == Mood.LOW },
                        okayCount = members.count { it.mood == Mood.OKAY },
                        goodCount = members.count { it.mood == Mood.GOOD },
                        topTagIds = topTagIds,
                    )
                }
                .sortedWith(compareByDescending<PlaceMoodSummary> { it.total }.thenBy { it.clusterId })
        }

        return BreakdownsReport(
            emotionTags = emotionSummaries,
            contextAndCustomTags = contextAndCustomSummaries,
            byMood = byMood,
            places = placeSummaries,
        )
    }

    /**
     * Filters entries completely in memory. Matches across comments, localized emotion/context labels,
     * encrypted custom tag labels, and resolved place names.
     */
    fun filterEntries(
        entries: List<Entry>,
        filter: HistoryFilter,
        extrasByEntryId: Map<String, EntryExtras>,
        customTagsById: Map<String, CustomTag>,
        placeAssignment: PlaceAssignment?,
        emotionTags: EmotionTags,
        tagLabelResolver: (String) -> String,
    ): List<Entry> {
        if (!filter.isActive) return entries

        val tokens = filter.query
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        return entries.filter { entry ->
            if (filter.moods.isNotEmpty() && entry.mood !in filter.moods) {
                return@filter false
            }

            val extras = extrasByEntryId[entry.id]
            if (filter.bookmarkedOnly && extras?.bookmarked != true) {
                return@filter false
            }

            if (filter.withNoteOnly && entry.comment.isNullOrBlank()) {
                return@filter false
            }

            if (filter.coreTagIds.isNotEmpty()) {
                val entryCores = entry.tags.mapNotNullTo(hashSetOf()) { tagId ->
                    if (emotionTags.isValidTag(tagId)) emotionTags.coreOf(tagId) else null
                }
                if (filter.coreTagIds.none { it in entryCores }) {
                    return@filter false
                }
            }

            if (filter.contextOrCustomTagIds.isNotEmpty()) {
                val entryContextAndCustom = buildSet {
                    if (extras != null) {
                        addAll(extras.contextTags)
                        addAll(extras.customTagIds)
                    }
                }
                if (filter.contextOrCustomTagIds.none { it in entryContextAndCustom }) {
                    return@filter false
                }
            }

            val cluster = placeAssignment?.clusterForEntry(entry.id)
            if (filter.clusterIds.isNotEmpty()) {
                if (cluster == null || cluster.id !in filter.clusterIds) {
                    return@filter false
                }
            }

            if (tokens.isNotEmpty()) {
                val searchableParts = buildList {
                    entry.comment?.let { add(it.lowercase()) }
                    cluster?.label?.name?.let { add(it.lowercase()) }
                    for (tagId in entry.tags) {
                        add(tagId.lowercase())
                        add(tagLabelResolver(tagId).lowercase())
                        if (emotionTags.isValidTag(tagId)) {
                            val coreId = emotionTags.coreOf(tagId)
                            add(coreId.lowercase())
                            add(tagLabelResolver(coreId).lowercase())
                        }
                    }
                    if (extras != null) {
                        for (ctxId in extras.contextTags) {
                            add(ctxId.lowercase())
                            add(tagLabelResolver(ctxId).lowercase())
                        }
                        for (customId in extras.customTagIds) {
                            customTagsById[customId]?.let { add(it.label.lowercase()) }
                        }
                    }
                }
                val combined = searchableParts.joinToString(separator = "\n")
                if (tokens.any { token -> !combined.contains(token) }) {
                    return@filter false
                }
            }

            true
        }
    }
}
