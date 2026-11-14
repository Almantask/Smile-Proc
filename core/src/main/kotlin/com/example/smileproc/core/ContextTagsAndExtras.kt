package com.example.smileproc.core

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.Random
import java.util.UUID

/**
 * Built-in, optional everyday life-context tags stored separately from the emotion catalog
 * (`EntryExtras`) so `content/emotion-tags.json` and v1 `Entry` schemas remain unchanged.
 */
enum class BuiltInContextTag(val id: String) {
    SLEEP("ctx.sleep"),
    MOVEMENT("ctx.movement"),
    OUTDOORS("ctx.outdoors"),
    SOCIAL("ctx.social"),
    WORK("ctx.work"),
    SOLO("ctx.solo"),
    REST("ctx.rest"),
    FOOD("ctx.food");

    companion object {
        private val byId = entries.associateBy { it.id }

        val ALL_IDS: List<String> = entries.map { it.id }

        fun fromId(id: String): BuiltInContextTag? = byId[id]

        fun isContextTag(id: String): Boolean = byId.containsKey(id)
    }
}

/**
 * Encrypted personal tag created by the user. Stored sealed under the Keystore key.
 */
@Serializable
data class CustomTag(
    val id: String,
    val label: String,
    @Serializable(with = IsoInstantSerializer::class)
    val createdAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "CustomTag id cannot be blank" }
        require(label.isNotBlank()) { "CustomTag label cannot be blank" }
        require(label.length <= MAX_LABEL_LENGTH) {
            "CustomTag label exceeds $MAX_LABEL_LENGTH chars"
        }
    }

    companion object {
        const val ID_PREFIX = "custom."
        const val MAX_LABEL_LENGTH = 28
        const val MAX_ACTIVE_TAGS = 20

        fun normalizeLabel(raw: String): String =
            raw.trim().replace(Regex("\\s+"), " ").take(MAX_LABEL_LENGTH)

        fun create(
            rawLabel: String,
            createdAt: Instant,
            id: String = "$ID_PREFIX${UUID.randomUUID().toString().lowercase(Locale.ROOT)}",
        ): CustomTag {
            val normalized = normalizeLabel(rawLabel)
            require(normalized.isNotEmpty()) { "Custom tag label cannot be blank" }
            return CustomTag(
                id = id,
                label = normalized,
                createdAt = createdAt,
            )
        }
    }
}

/**
 * Per-entry sealed companion metadata holding optional context tags, user-created personal tags,
 * and a quiet Saved Moments bookmark flag. Kept separate from [Entry] so `CanonicalJson` and
 * `fixtures/export/vector-1.json` remain byte-compatible.
 */
@Serializable
data class EntryExtras(
    val entryId: String,
    val contextTags: List<String> = emptyList(),
    val customTagIds: List<String> = emptyList(),
    val bookmarked: Boolean = false,
    @Serializable(with = IsoInstantSerializer::class)
    val updatedAt: Instant,
) {
    init {
        require(entryId.isNotBlank()) { "entryId cannot be blank" }
    }

    fun isEmpty(): Boolean =
        contextTags.isEmpty() && customTagIds.isEmpty() && !bookmarked

    fun normalized(): EntryExtras = copy(
        contextTags = contextTags
            .map { it.trim() }
            .filter { BuiltInContextTag.isContextTag(it) }
            .distinct(),
        customTagIds = customTagIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct(),
    )

    companion object {
        fun encode(extras: EntryExtras): String =
            CanonicalJson.json.encodeToString(serializer(), extras)

        fun decode(raw: String): EntryExtras =
            CanonicalJson.json.decodeFromString(serializer(), raw)

        fun encodeCustomTag(tag: CustomTag): String =
            CanonicalJson.json.encodeToString(CustomTag.serializer(), tag)

        fun decodeCustomTag(raw: String): CustomTag =
            CanonicalJson.json.decodeFromString(CustomTag.serializer(), raw)
    }
}

/**
 * Pause state for check-in reminders (`Pause until tomorrow`, `Pause for 3 days`, or `Pause until resumed`).
 */
@Serializable
data class ReminderPause(
    @Serializable(with = IsoInstantSerializer::class)
    val pausedUntil: Instant? = null,
    val pausedIndefinitely: Boolean = false,
) {
    fun isPausedAt(now: Instant): Boolean =
        pausedIndefinitely || (pausedUntil != null && now.isBefore(pausedUntil))

    /** Returns the active timed cutoff if still in the future relative to [now], otherwise null. */
    fun effectivePausedUntil(now: Instant): Instant? = when {
        pausedIndefinitely -> null
        pausedUntil != null && now.isBefore(pausedUntil) -> pausedUntil
        else -> null
    }

    companion object {
        val NONE = ReminderPause()
        val INDEFINITE = ReminderPause(pausedUntil = null, pausedIndefinitely = true)

        fun untilTomorrow(now: Instant, zone: ZoneId): ReminderPause =
            forDays(now = now, days = 1L, zone = zone)

        fun forDays(now: Instant, days: Long, zone: ZoneId): ReminderPause {
            require(days >= 1L) { "days must be >= 1, got $days" }
            val resumeMidnight = now.atZone(zone)
                .toLocalDate()
                .plusDays(days)
                .atStartOfDay(zone)
                .toInstant()
            return ReminderPause(pausedUntil = resumeMidnight, pausedIndefinitely = false)
        }

        fun encode(pause: ReminderPause): String =
            CanonicalJson.json.encodeToString(serializer(), pause)

        fun decode(raw: String): ReminderPause =
            CanonicalJson.json.decodeFromString(serializer(), raw)
    }
}

/**
 * Pure schedule calculator supporting reminder pauses and deterministic per-(reminderId, localDate)
 * flexibility windows (`Exact`, `±15 min`, `±30 min`, `±45 min`).
 */
object ReminderScheduleCalculator {
    val ALLOWED_WINDOWS_MINUTES: List<Int> = listOf(0, 15, 30, 45)
    const val MAX_WINDOW_MINUTES = 60

    fun sanitizeWindowMinutes(minutes: Int): Int =
        minutes.coerceIn(0, MAX_WINDOW_MINUTES)

    /**
     * Returns a deterministic offset in `[-windowMinutes, +windowMinutes]` for `(reminderId, date)`,
     * seeded with SHA-256 so reschedules on app restart, time-change, or boot produce the same time.
     */
    fun deterministicOffsetMinutes(
        reminderId: String,
        date: LocalDate,
        windowMinutes: Int,
    ): Int {
        val clampedWindow = sanitizeWindowMinutes(windowMinutes)
        if (clampedWindow == 0) return 0

        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$reminderId:$date".toByteArray(Charsets.UTF_8))
        val seed = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long
        val random = Random(seed)
        return random.nextInt(clampedWindow * 2 + 1) - clampedWindow
    }

    /**
     * Resolves the local occurrence [Instant] of [reminder] on [date] in [zone], clamping within the
     * calendar day `[00:00, 23:59]` so jitter never shifts a scheduled weekday into a different day.
     */
    fun occurrenceInstant(
        reminder: Reminder,
        date: LocalDate,
        windowMinutes: Int,
        zone: ZoneId,
    ): Instant {
        val offset = deterministicOffsetMinutes(reminder.id, date, windowMinutes)
        val baseMinutes = reminder.hour * 60 + reminder.minute
        val targetMinutes = (baseMinutes + offset).coerceIn(0, 23 * 60 + 59)
        val localTime = LocalTime.of(targetMinutes / 60, targetMinutes % 60)
        return ZonedDateTime.of(date, localTime, zone).toInstant()
    }

    /**
     * Returns the earliest enabled occurrence strictly after [now] and respecting [pause],
     * or `null` if no reminders are enabled or reminders are paused indefinitely.
     */
    fun nextOccurrence(
        reminders: List<Reminder>,
        now: Instant,
        zone: ZoneId,
        windowsByReminderId: Map<String, Int> = emptyMap(),
        pause: ReminderPause = ReminderPause.NONE,
    ): Instant? {
        if (pause.pausedIndefinitely) return null

        val active = reminders.filter { it.days.isNotEmpty() }
        if (active.isEmpty()) return null

        val activePauseEnd = pause.effectivePausedUntil(now)
        val anchorDate = (activePauseEnd ?: now).atZone(zone).toLocalDate()

        var best: Instant? = null
        for (dayOffset in 0L..8L) {
            val date = anchorDate.plusDays(dayOffset)
            val isoWeekday = date.dayOfWeek.value
            for (reminder in active) {
                if (isoWeekday !in reminder.days) continue
                val windowMinutes = windowsByReminderId[reminder.id] ?: 0
                val candidate = occurrenceInstant(
                    reminder = reminder,
                    date = date,
                    windowMinutes = windowMinutes,
                    zone = zone,
                )
                val isAfterNow = candidate.isAfter(now)
                val isAfterPause = activePauseEnd == null || !candidate.isBefore(activePauseEnd)
                if (isAfterNow && isAfterPause) {
                    if (best == null || candidate.isBefore(best)) {
                        best = candidate
                    }
                }
            }
        }
        return best
    }
}
