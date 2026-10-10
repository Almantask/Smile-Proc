package com.example.smileproc.core

object LowMoodMonitor {
    fun armed(entries: List<Entry>, config: PatternConfig, dismissedEntryId: String?): Boolean {
        val ordered = entries.sortedWith(compareBy({ it.createdAt }, { it.id }))
        if (dismissedEntryId != null) {
            val index = ordered.indexOfFirst { it.id == dismissedEntryId }
            if (index >= 0 && ordered.drop(index + 1).all { it.mood == Mood.LOW }) {
                return false
            }
        }
        val length = config.lowRunLength
        if (ordered.size < length) return false
        val tail = ordered.takeLast(length)
        if (tail.any { it.mood != Mood.LOW }) return false
        val dates = tail.map { Instants.localDate(it.createdAt, it.timeZoneID) }.toSet()
        return dates.size >= config.lowRunMinDays
    }
}
