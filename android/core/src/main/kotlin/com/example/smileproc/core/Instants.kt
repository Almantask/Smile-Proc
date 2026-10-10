package com.example.smileproc.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

object Instants {
    fun parse(text: String): Instant {
        val normalized = if (text.endsWith("Z")) text.dropLast(1) + "+00:00" else text
        return Instant.parse(normalized)
    }

    fun format(instant: Instant): String {
        val utc = instant.atZone(ZoneOffset.UTC)
        val millis = utc.nano / 1_000_000
        return String.format(
            Locale.US,
            "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            utc.year,
            utc.monthValue,
            utc.dayOfMonth,
            utc.hour,
            utc.minute,
            utc.second,
            millis,
        )
    }

    fun localDate(instant: Instant, zoneName: String): LocalDate =
        instant.atZone(ZoneId.of(zoneName)).toLocalDate()

    fun isoWeekday(instant: Instant, zoneName: String): Int =
        instant.atZone(ZoneId.of(zoneName)).dayOfWeek.value

    fun daypart(instant: Instant, zoneName: String): String {
        val hour = instant.atZone(ZoneId.of(zoneName)).hour
        return when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
    }

    fun ageDays(start: Instant, end: Instant): Double {
        val seconds = (end.epochSecond - start.epochSecond).toDouble()
        val nanos = (end.nano - start.nano) / 1_000_000_000.0
        return (seconds + nanos) / 86_400.0
    }
}
