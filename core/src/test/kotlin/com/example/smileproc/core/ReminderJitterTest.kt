package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderJitterTest {

    private val zone = ZoneId.of("Europe/Vilnius")

    @Test
    fun exactReminder_withZeroWindow_hasZeroOffsetAndMatchesWallClock() {
        val reminder = Reminder(
            id = "r-exact",
            hour = 9,
            minute = 30,
            days = (1..7).toSet(),
        )
        val date = LocalDate.of(2026, 10, 12)
        assertEquals(0, ReminderScheduleCalculator.deterministicOffsetMinutes("r-exact", date, 0))
        val expected = ZonedDateTime.of(2026, 10, 12, 9, 30, 0, 0, zone).toInstant()
        assertEquals(expected, ReminderScheduleCalculator.occurrenceInstant(reminder, date, 0, zone))
    }

    @Test
    fun flexibleWindow_isDeterministicPerReminderAndDate_andStaysWithinBounds() {
        val reminder = Reminder(
            id = "r-flex",
            hour = 14,
            minute = 0,
            days = (1..7).toSet(),
        )
        val date = LocalDate.of(2026, 10, 12)
        val base = ZonedDateTime.of(2026, 10, 12, 14, 0, 0, 0, zone).toInstant()

        val firstCall = ReminderScheduleCalculator.occurrenceInstant(reminder, date, 30, zone)
        val secondCall = ReminderScheduleCalculator.occurrenceInstant(reminder, date, 30, zone)
        assertEquals(firstCall, secondCall)

        val diffMinutes = Duration.between(base, firstCall).toMinutes()
        assertTrue("Expected offset within [-30, 30], got $diffMinutes", diffMinutes in -30L..30L)
    }

    @Test
    fun flexibleWindow_nearMidnight_clampsWithinSameLocalDate() {
        val earlyReminder = Reminder(id = "early", hour = 0, minute = 5, days = setOf(1))
        val date = LocalDate.of(2026, 10, 12) // Monday
        val occurrence = ReminderScheduleCalculator.occurrenceInstant(earlyReminder, date, 45, zone)
        assertEquals(date, occurrence.atZone(zone).toLocalDate())
    }

    @Test
    fun nextOccurrence_respectsIndefinitePause_andTimedPauseUntilCutoff() {
        val reminder = Reminder(
            id = "r1",
            hour = 9,
            minute = 0,
            days = (1..7).toSet(),
        )
        val now = ZonedDateTime.of(2026, 10, 12, 8, 0, 0, 0, zone).toInstant()

        // Unpaused: today at 09:00
        val unpaused = ReminderScheduleCalculator.nextOccurrence(
            reminders = listOf(reminder),
            now = now,
            zone = zone,
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 12, 9, 0, 0, 0, zone).toInstant(),
            unpaused,
        )

        // Paused indefinitely: null
        assertNull(
            ReminderScheduleCalculator.nextOccurrence(
                reminders = listOf(reminder),
                now = now,
                zone = zone,
                pause = ReminderPause.INDEFINITE,
            ),
        )

        // Paused until tomorrow: skips Oct 12 09:00, picks Oct 13 09:00
        val tomorrowPause = ReminderPause.untilTomorrow(now, zone)
        val afterTomorrowPause = ReminderScheduleCalculator.nextOccurrence(
            reminders = listOf(reminder),
            now = now,
            zone = zone,
            pause = tomorrowPause,
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 13, 9, 0, 0, 0, zone).toInstant(),
            afterTomorrowPause,
        )

        // Paused for 3 days from Oct 12: skips Oct 12, 13, 14 -> picks Oct 15 09:00
        val threeDayPause = ReminderPause.forDays(now, 3L, zone)
        val afterThreeDays = ReminderScheduleCalculator.nextOccurrence(
            reminders = listOf(reminder),
            now = now,
            zone = zone,
            pause = threeDayPause,
        )
        assertNotNull(afterThreeDays)
        assertEquals(
            ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, zone).toInstant(),
            afterThreeDays,
        )
    }

    @Test
    fun nextOccurrence_ignoresExpiredTimedPause() {
        val reminder = Reminder(
            id = "r1",
            hour = 20,
            minute = 0,
            days = (1..7).toSet(),
        )
        val expiredPause = ReminderPause(
            pausedUntil = Instant.parse("2026-10-10T00:00:00Z"),
            pausedIndefinitely = false,
        )
        val now = ZonedDateTime.of(2026, 10, 12, 10, 0, 0, 0, zone).toInstant()
        val next = ReminderScheduleCalculator.nextOccurrence(
            reminders = listOf(reminder),
            now = now,
            zone = zone,
            pause = expiredPause,
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 12, 20, 0, 0, 0, zone).toInstant(),
            next,
        )
    }
}
