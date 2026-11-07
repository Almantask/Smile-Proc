package com.example.smileproc.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** User-configured weekly card schedule (`weekday` is ISO `1 = Monday … 7 = Sunday`). */
@Serializable
data class WeeklyCardTime(
    val weekday: Int = DEFAULT_WEEKDAY,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
) {
    init {
        require(weekday in 1..7) { "ISO weekday must be in 1..7, got $weekday" }
        require(hour in 0..23) { "Hour must be in 0..23, got $hour" }
        require(minute in 0..59) { "Minute must be in 0..59, got $minute" }
    }

    fun toLocalTime(): LocalTime = LocalTime.of(hour, minute)

    companion object {
        const val DEFAULT_WEEKDAY = 7
        const val DEFAULT_HOUR = 19
        const val DEFAULT_MINUTE = 0
        val DEFAULT = WeeklyCardTime()
    }
}

/**
 * Weekly card period boundaries (`docs/spec/logic.md` › Card period):
 * - `periodEnd` = latest occurrence of [cardTime] in [zone] that is `<= now`.
 * - `periodStart` = the same local wall time 7 calendar days earlier in [zone] (not `7 × 24 h`).
 */
data class CardPeriod(
    val periodStart: Instant,
    val periodEnd: Instant,
    val endLocalDate: LocalDate,
    val zone: ZoneId,
    val cardTime: WeeklyCardTime,
) {
    /** Start of the 14-calendar-day `twoWeeks` fallback window ending at [periodEnd]. */
    val twoWeekStart: Instant
        get() = startDaysBeforeEnd(TWO_WEEK_WINDOW_DAYS)

    /** Start of the 28-calendar-day weekday-detector window ending at [periodEnd]. */
    val weekdayWindowStart: Instant
        get() = startDaysBeforeEnd(WEEKDAY_WINDOW_DAYS)

    /** Same local [cardTime] in [zone], [days] calendar days before [periodEnd]. */
    fun startDaysBeforeEnd(days: Int): Instant {
        require(days >= 0) { "days must be >= 0, got $days" }
        val targetDate = endLocalDate.minusDays(days.toLong())
        return ZonedDateTime.of(targetDate, cardTime.toLocalTime(), zone).toInstant()
    }

    companion object {
        const val WEEK_WINDOW_DAYS = 7
        const val TWO_WEEK_WINDOW_DAYS = 14
        const val WEEKDAY_WINDOW_DAYS = 28

        fun resolve(
            now: Instant,
            zone: ZoneId,
            cardTime: WeeklyCardTime = WeeklyCardTime.DEFAULT,
        ): CardPeriod {
            val nowLocal = now.atZone(zone)
            val daysBack = Math.floorMod(nowLocal.dayOfWeek.value - cardTime.weekday, 7)
            var endDate = nowLocal.toLocalDate().minusDays(daysBack.toLong())
            val localCardTime = cardTime.toLocalTime()
            var endInstant = ZonedDateTime.of(endDate, localCardTime, zone).toInstant()

            if (endInstant.isAfter(now)) {
                endDate = endDate.minusDays(WEEK_WINDOW_DAYS.toLong())
                endInstant = ZonedDateTime.of(endDate, localCardTime, zone).toInstant()
            }

            val startDate = endDate.minusDays(WEEK_WINDOW_DAYS.toLong())
            val startInstant = ZonedDateTime.of(startDate, localCardTime, zone).toInstant()

            return CardPeriod(
                periodStart = startInstant,
                periodEnd = endInstant,
                endLocalDate = endDate,
                zone = zone,
                cardTime = cardTime,
            )
        }

        /** Next occurrence of [cardTime] strictly after [now] in [zone]. */
        fun nextAfter(
            now: Instant,
            zone: ZoneId,
            cardTime: WeeklyCardTime = WeeklyCardTime.DEFAULT,
        ): ZonedDateTime {
            val latest = resolve(now, zone, cardTime)
            val nextDate = latest.endLocalDate.plusDays(WEEK_WINDOW_DAYS.toLong())
            return ZonedDateTime.of(nextDate, cardTime.toLocalTime(), zone)
        }
    }
}
