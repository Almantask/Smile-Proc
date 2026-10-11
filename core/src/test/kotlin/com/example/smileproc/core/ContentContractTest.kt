package com.example.smileproc.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentContractTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun contentFilesParseAndKeepTheContract() {
        val contentDir = File(System.getProperty("smileproc.contentDir") ?: error("smileproc.contentDir is not set"))
        val config = json.parseToJsonElement(File(contentDir, "pattern-config.json").readText()).jsonObject
        assertEquals(EXPECTED_CONFIG_KEYS, config.keys)

        val tags = json.parseToJsonElement(File(contentDir, "emotion-tags.json").readText()).jsonObject
        val cores = tags.getValue("cores").jsonArray
        assertEquals(7, cores.size)
        cores.forEach { element ->
            val core = element.jsonObject
            val fines = core.getValue("fine").jsonArray
            assertEquals(5, fines.size)
            val color = core.getValue("color").jsonPrimitive.content
            assertTrue(color.matches(Regex("#[0-9A-Fa-f]{6}")))
            fines.forEach { fine ->
                assertTrue(fine.jsonPrimitive.content.startsWith(core.getValue("id").jsonPrimitive.content + "."))
            }
        }

        val library = json.parseToJsonElement(File(contentDir, "pattern-library.en.json").readText()) as JsonArray
        val ids = library.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains("afternoon-dip"))
        assertTrue(ids.contains("sunday-evening"))

        val support = json.parseToJsonElement(File(contentDir, "support.json").readText()).jsonObject
        assertEquals(0, support.getValue("entries").jsonArray.size)

        val strings = File(contentDir.parentFile, "app/src/main/res/values/strings.xml").readText()
        listOf("depression", "disorder", "symptom", "diagnosis", "treatment").forEach { word ->
            assertTrue("$word is not user-facing copy", !strings.contains(word, ignoreCase = true))
        }
    }

    private companion object {
        val EXPECTED_CONFIG_KEYS = setOf(
            "version",
            "minSegment",
            "minRest",
            "patternMinSegment",
            "patternMinRest",
            "minAbsDiff",
            "zHint",
            "zPattern",
            "sdFloor",
            "weekdayMinSegment",
            "weekdayMinDistinctDates",
            "weekdayMinAbsDiff",
            "weekdayMinDataDays",
            "tagMinLowTagged",
            "tagMinCount",
            "tagMinShare",
            "tagMinShareGap",
            "tagPatternCount",
            "tagAtPlaceMinShare",
            "fineTagDominance",
            "shortWeekThreshold",
            "maxItems",
            "maxItemsLowWeek",
            "notEnoughDataBelow",
            "firstWeekDays",
            "firstCardMinEntries",
            "lowWeekMinEntries",
            "lowWeekShare",
            "lowRunLength",
            "lowRunMinDays",
            "placeRadiusMeters",
            "placeMaxAccuracyMeters",
        )
    }
}
