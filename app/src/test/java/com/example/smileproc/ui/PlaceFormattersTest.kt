package com.example.smileproc.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.ui.places.PlaceFormatters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PlaceFormattersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun makeEntry(
        isoWeekday: Int, // 1 = Monday .. 7 = Sunday
        hour: Int,
    ): Entry {
        // Find a date with this weekday (e.g. 2026-11-02 is Monday = 1)
        val baseMonday = ZonedDateTime.of(2026, 11, 2, hour, 0, 0, 0, ZoneId.of("UTC"))
        val targetDateTime = baseMonday.plusDays((isoWeekday - 1).toLong())
        return Entry(
            id = UUID.randomUUID().toString(),
            createdAt = targetDateTime.toInstant(),
            updatedAt = targetDateTime.toInstant(),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = emptyList(),
            comment = null,
            location = LocationSample(52.5200, 13.4050, 20.0, targetDateTime.toInstant(), LocationKind.FRESH),
            source = EntrySource.APP,
        )
    }

    @Test
    fun formatUnnamedDescription_mostlyWeekdayMornings() {
        val entries = listOf(
            makeEntry(isoWeekday = 1, hour = 9), // Monday morning
            makeEntry(isoWeekday = 2, hour = 10), // Tuesday morning
            makeEntry(isoWeekday = 6, hour = 14), // Saturday afternoon
        )
        val formatted = PlaceFormatters.formatUnnamedDescription(context, entries)
        assertEquals("Unnamed place · 3 check-ins · mostly weekday mornings", formatted)
    }

    @Test
    fun formatUnnamedDescription_mostlyWeekendEvenings() {
        val entries = listOf(
            makeEntry(isoWeekday = 6, hour = 18), // Saturday evening
            makeEntry(isoWeekday = 7, hour = 19), // Sunday evening
            makeEntry(isoWeekday = 3, hour = 9), // Wednesday morning
        )
        val formatted = PlaceFormatters.formatUnnamedDescription(context, entries)
        assertEquals("Unnamed place · 3 check-ins · mostly weekend evenings", formatted)
    }

    @Test
    fun formatUnnamedDescription_singleCheckInPlural() {
        val entries = listOf(
            makeEntry(isoWeekday = 1, hour = 15), // Monday afternoon
        )
        val formatted = PlaceFormatters.formatUnnamedDescription(context, entries)
        assertEquals("Unnamed place · 1 check-in · mostly weekday afternoons", formatted)
    }

    @Test
    fun formatPlaceKind_returnsExpectedStrings() {
        assertEquals("Home", PlaceFormatters.formatPlaceKind(context, PlaceKind.HOME))
        assertEquals("Work", PlaceFormatters.formatPlaceKind(context, PlaceKind.WORK))
        assertEquals("Other", PlaceFormatters.formatPlaceKind(context, PlaceKind.OTHER))
    }
}
