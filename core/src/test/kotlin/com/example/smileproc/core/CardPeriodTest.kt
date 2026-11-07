package com.example.smileproc.core

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

class CardPeriodTest {

    @Test
    fun allPeriodFixtureCasesMatchHandCalculatedBoundaries() {
        val inputCases = TestData.json(File(TestData.fixtures, "periods/card-periods.input.json"))
            .jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
        val expectCases = TestData.json(File(TestData.fixtures, "periods/card-periods.expect.json"))
            .jsonObject.getValue("cases").jsonArray.map { it.jsonObject }

        assertEquals(10, inputCases.size)
        assertEquals(inputCases.size, expectCases.size)

        for ((inp, exp) in inputCases.zip(expectCases)) {
            val name = inp.getValue("name").jsonPrimitive.content
            assertEquals(name, exp.getValue("name").jsonPrimitive.content)

            val now = IsoInstantSerializer.parseOrThrow(inp.getValue("now").jsonPrimitive.content)
            val zone = ZoneId.of(inp.getValue("timeZoneID").jsonPrimitive.content)
            val ct = inp.getValue("cardTime").jsonObject
            val cardTime = WeeklyCardTime(
                weekday = ct.getValue("weekday").jsonPrimitive.int,
                hour = ct.getValue("hour").jsonPrimitive.int,
                minute = ct.getValue("minute").jsonPrimitive.int,
            )

            val resolved = CardPeriod.resolve(now, zone, cardTime)
            assertEquals("$name periodStart", exp.getValue("periodStart").jsonPrimitive.content, IsoInstantSerializer.format(resolved.periodStart))
            assertEquals("$name periodEnd", exp.getValue("periodEnd").jsonPrimitive.content, IsoInstantSerializer.format(resolved.periodEnd))
        }
    }

    @Test
    fun calendarWindowsRespectDstInTwoWeekAndWeekdaySpans() {
        // On Sun 25 Oct 2026 in Europe/Rome, periodEnd = 19:00 CET (18:00Z).
        // 14 days earlier (Sun 11 Oct 19:00 CEST) is 17:00Z; 28 days earlier (Sun 27 Sep 19:00 CEST) is 17:00Z.
        val period = CardPeriod.resolve(
            now = Instant.parse("2026-10-25T19:00:00.000Z"),
            zone = ZoneId.of("Europe/Rome"),
        )
        assertEquals("2026-10-11T17:00:00.000Z", IsoInstantSerializer.format(period.twoWeekStart))
        assertEquals("2026-09-27T17:00:00.000Z", IsoInstantSerializer.format(period.weekdayWindowStart))
    }
}
