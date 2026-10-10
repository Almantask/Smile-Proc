package com.example.smileproc.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class CardPeriod(
    val periodStart: Instant,
    val periodEnd: Instant,
    val zone: String,
    val hour: Int,
    val minute: Int,
)

object CardPeriods {
    fun cardPeriod(now: Instant, zoneName: String, weekday: Int, hour: Int, minute: Int): CardPeriod {
        val zone = ZoneId.of(zoneName)
        val local = now.atZone(zone)
        val deltaDays = Math.floorMod(local.dayOfWeek.value - weekday, 7)
        var endDate = local.toLocalDate().minusDays(deltaDays.toLong())
        var endLocal = ZonedDateTime.of(endDate, LocalTime.of(hour, minute), zone)
        if (endLocal.toInstant().isAfter(local.toInstant())) {
            endDate = endDate.minusDays(7)
            endLocal = ZonedDateTime.of(endDate, LocalTime.of(hour, minute), zone)
        }
        val startDate = endDate.minusDays(7)
        val startLocal = ZonedDateTime.of(startDate, LocalTime.of(hour, minute), zone)
        return CardPeriod(
            periodStart = startLocal.toInstant(),
            periodEnd = endLocal.toInstant(),
            zone = zoneName,
            hour = hour,
            minute = minute,
        )
    }

    fun shiftLocal(instant: Instant, zoneName: String, days: Int, hour: Int, minute: Int): Instant {
        val zone = ZoneId.of(zoneName)
        val shifted = instant.atZone(zone).toLocalDate().minusDays(days.toLong())
        return ZonedDateTime.of(shifted, LocalTime.of(hour, minute), zone).toInstant()
    }

    fun nextCardTime(now: Instant, zoneName: String, weekday: Int, hour: Int, minute: Int): ZonedDateTime {
        val period = cardPeriod(now, zoneName, weekday, hour, minute)
        val zone = ZoneId.of(zoneName)
        val endDate = period.periodEnd.atZone(zone).toLocalDate()
        return ZonedDateTime.of(endDate.plusDays(7), LocalTime.of(hour, minute), zone)
    }

    fun shouldBuildCard(
        entries: List<Entry>,
        config: PatternConfig,
        now: Instant,
        zoneName: String,
        weekday: Int,
        hour: Int,
        minute: Int,
        existingPeriodEnds: List<String>,
    ): Boolean {
        if (entries.size < config.firstCardMinEntries) return false
        val earliest = entries.minWith(compareBy({ it.createdAt }, { it.id }))
        val period = cardPeriod(now, zoneName, weekday, hour, minute)
        if (period.periodEnd.isBefore(earliest.createdAt)) return false
        return Instants.format(period.periodEnd) !in existingPeriodEnds
    }
}
