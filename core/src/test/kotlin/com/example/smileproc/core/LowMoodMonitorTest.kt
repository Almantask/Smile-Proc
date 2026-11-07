package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class LowMoodMonitorTest {

    private val config: PatternConfig =
        PatternConfig.fromJson(File(TestData.content, "pattern-config.json").readText(Charsets.UTF_8))
    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    @Test
    fun fiveLowOnOneDayDoesNotShowCard() {
        val sameDay = listOf(8, 10, 13, 16, 20).mapIndexed { idx, hour ->
            entry(idx + 1, day = 12, hour = hour, mood = Mood.LOW)
        }
        assertFalse(LowMoodMonitor.shouldShowCard(sameDay, dismissedEntryId = null, config = config))
    }

    @Test
    fun fiveLowAcrossTwoDaysShowsCardAndDismissReArmsOnlyAfterNonLowEntry() {
        val runAcrossTwoDays = listOf(
            entry(1, day = 12, hour = 9, mood = Mood.LOW),
            entry(2, day = 12, hour = 14, mood = Mood.LOW),
            entry(3, day = 12, hour = 20, mood = Mood.LOW),
            entry(4, day = 13, hour = 9, mood = Mood.LOW),
            entry(5, day = 13, hour = 18, mood = Mood.LOW),
        )
        assertTrue(LowMoodMonitor.shouldShowCard(runAcrossTwoDays, dismissedEntryId = null, config = config))

        // Dismissing stores the latest entry ID and suppresses the card
        val dismissedId = LowMoodMonitor.recordDismissal(runAcrossTwoDays)
        assertEquals(runAcrossTwoDays.last().id, dismissedId)
        assertFalse(LowMoodMonitor.shouldShowCard(runAcrossTwoDays, dismissedId, config))

        // A 6th consecutive Low entry (even on a 3rd day) does NOT re-arm the card before a non-Low entry!
        val withSixthLow = runAcrossTwoDays + entry(6, day = 14, hour = 10, mood = Mood.LOW)
        assertFalse(LowMoodMonitor.shouldShowCard(withSixthLow, dismissedId, config))

        // A non-Low entry breaks the run (still false right away, since last 5 aren't all Low)
        val afterOkay = withSixthLow + entry(7, day = 14, hour = 18, mood = Mood.OKAY)
        assertFalse(LowMoodMonitor.shouldShowCard(afterOkay, dismissedId, config))

        // Subsequent new 5-Low run across >= 2 days re-arms and shows the card again!
        val reArmed = afterOkay + listOf(
            entry(8, day = 15, hour = 9, mood = Mood.LOW),
            entry(9, day = 15, hour = 15, mood = Mood.LOW),
            entry(10, day = 15, hour = 20, mood = Mood.LOW),
            entry(11, day = 16, hour = 9, mood = Mood.LOW),
            entry(12, day = 16, hour = 18, mood = Mood.LOW),
        )
        assertTrue(LowMoodMonitor.shouldShowCard(reArmed, dismissedId, config))
    }

    private fun entry(num: Int, day: Int, hour: Int, mood: Mood): Entry {
        val hex = num.toString().padStart(8, '0')
        val ts = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, rome).toInstant()
        return Entry(
            id = "$hex-0000-4000-8000-000000000000",
            createdAt = ts,
            updatedAt = ts,
            timeZoneID = "Europe/Rome",
            mood = mood,
            tags = emptyList(),
            comment = null,
            location = null,
            source = EntrySource.APP,
        )
    }
}
