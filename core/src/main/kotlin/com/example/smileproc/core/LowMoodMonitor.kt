package com.example.smileproc.core

/** Evaluation output of [LowMoodMonitor] for Today's low-run support card. */
data class LowRunEvaluation(
    val shouldShowCard: Boolean,
    val latestEntryId: String?,
)

/**
 * Monitors consecutive Low check-ins for the gentle, dismissible Today support card
 * (`docs/spec/logic.md` › Low-mood support).
 *
 * - Armed when the last `lowRunLength` entries are all `Low` **and** span `>= lowRunMinDays`
 *   distinct local dates (each in its own `timeZoneID`).
 * - Dismissing stores the latest entry ID; the card re-arms **only** after a non-Low entry.
 */
object LowMoodMonitor {

    private val ENTRY_ORDER: Comparator<Entry> = compareBy<Entry> { it.createdAt }.thenBy { it.id }

    fun evaluate(
        entries: List<Entry>,
        dismissedEntryId: String?,
        config: PatternConfig,
    ): LowRunEvaluation {
        if (entries.isEmpty()) {
            return LowRunEvaluation(shouldShowCard = false, latestEntryId = null)
        }
        val sorted = entries.sortedWith(ENTRY_ORDER)
        val latestId = sorted.last().id

        if (dismissedEntryId != null) {
            val dismissIdx = sorted.indexOfFirst { it.id == dismissedEntryId }
            if (dismissIdx >= 0) {
                val hasNonLowAfterDismiss = sorted
                    .subList(dismissIdx + 1, sorted.size)
                    .any { it.mood != Mood.LOW }
                if (!hasNonLowAfterDismiss) {
                    return LowRunEvaluation(shouldShowCard = false, latestEntryId = latestId)
                }
            }
        }

        if (sorted.size < config.lowRunLength) {
            return LowRunEvaluation(shouldShowCard = false, latestEntryId = latestId)
        }

        val tail = sorted.takeLast(config.lowRunLength)
        if (tail.any { it.mood != Mood.LOW }) {
            return LowRunEvaluation(shouldShowCard = false, latestEntryId = latestId)
        }

        val distinctDates = tail.mapTo(hashSetOf()) { it.localDate() }.size
        val show = distinctDates >= config.lowRunMinDays
        return LowRunEvaluation(shouldShowCard = show, latestEntryId = latestId)
    }

    fun shouldShowCard(
        entries: List<Entry>,
        dismissedEntryId: String?,
        config: PatternConfig,
    ): Boolean = evaluate(entries, dismissedEntryId, config).shouldShowCard

    /** Entry ID to persist when the user taps "Not now" on the low-run card. */
    fun recordDismissal(entries: List<Entry>): String? =
        entries.minOrMaxLatest()?.id

    private fun List<Entry>.minOrMaxLatest(): Entry? = maxWithOrNull(ENTRY_ORDER)
}
