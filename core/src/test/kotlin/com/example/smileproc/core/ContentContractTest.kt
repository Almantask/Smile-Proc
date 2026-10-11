package com.example.smileproc.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The runtime content files match docs/spec/data.md. */
class ContentContractTest {

    private val tagTable = linkedMapOf(
        "happy" to listOf("joyful", "grateful", "proud", "hopeful", "excited"),
        "calm" to listOf("relaxed", "content", "peaceful", "focused", "safe"),
        "sad" to listOf("down", "lonely", "disappointed", "hurt", "nostalgic"),
        "anxious" to listOf("stressed", "worried", "overwhelmed", "nervous", "insecure"),
        "angry" to listOf("frustrated", "irritated", "resentful", "jealous", "impatient"),
        "tired" to listOf("drained", "sleepy", "bored", "unmotivated", "numb"),
        "surprised" to listOf("amazed", "confused", "curious", "startled", "moved"),
    )

    @Test
    fun emotionTagsMatchTheSpecTable() {
        val root = TestData.content("emotion-tags.json").jsonObject
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
        val cores = root.getValue("cores").jsonArray.map { it.jsonObject }
        assertEquals(tagTable.keys.toList(), cores.map { it.str("id") })
        for (core in cores) {
            val id = core.str("id")
            val expected = tagTable.getValue(id).map { "$id.$it" }
            assertEquals("fine tags of $id", expected, core.getValue("fine").jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test
    fun tagColoursKeepDarkChipTextReadable() {
        // Tag colours are chip fills under dark text in both themes (docs/progress.md, M0 decisions).
        val chipText = "#1C1B1F"
        for (core in TestData.content("emotion-tags.json").jsonObject.getValue("cores").jsonArray) {
            val color = core.jsonObject.str("color")
            assertTrue("$color is #RRGGBB", Regex("#[0-9A-F]{6}").matches(color))
            val ratio = contrast(color, chipText)
            assertTrue("${core.jsonObject.str("id")} $color contrast $ratio < 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun patternConfigHasExactlyTheSpecKeys() {
        val keys = TestData.content("pattern-config.json").jsonObject.keys
        val expected = setOf(
            "version", "minSegment", "minRest", "patternMinSegment", "patternMinRest",
            "minAbsDiff", "zHint", "zPattern", "sdFloor",
            "weekdayMinSegment", "weekdayMinDistinctDates", "weekdayMinAbsDiff", "weekdayMinDataDays",
            "tagMinLowTagged", "tagMinCount", "tagMinShare", "tagMinShareGap", "tagPatternCount",
            "tagAtPlaceMinShare", "fineTagDominance",
            "shortWeekThreshold", "maxItems", "maxItemsLowWeek",
            "notEnoughDataBelow", "firstWeekDays", "firstCardMinEntries",
            "lowWeekMinEntries", "lowWeekShare", "lowRunLength", "lowRunMinDays",
            "placeRadiusMeters", "placeMaxAccuracyMeters",
        )
        assertEquals(expected, keys)
    }

    @Test
    fun libraryEntriesAreCompleteAndUseOnlyKnownPlaceholders() {
        val detectors = setOf("timeOfDay", "weekday", "weekdayDaypart", "place", "awayFromHome", "tagOnLow", "tagAtPlace")
        val matchKeys = setOf("detector", "direction", "segment", "weekday", "placeKind", "tag")
        val placeholders = setOf("{segment}", "{weekday}", "{place}", "{tag}")
        val entries = TestData.content("pattern-library.en.json").jsonArray.map { it.jsonObject }
        assertEquals(entries.size, entries.map { it.str("id") }.toSet().size)
        for (entry in entries) {
            val id = entry.str("id")
            val match = entry.getValue("match").jsonObject
            assertTrue("$id detector", match.str("detector") in detectors)
            assertTrue("$id match keys ${match.keys}", matchKeys.containsAll(match.keys))
            assertTrue("$id match values are strings", match.values.all { it is JsonPrimitive && it.isString })
            for (field in listOf("title", "common", "tip", "question")) {
                val text = entry.str(field)
                assertTrue("$id $field is empty", text.isNotBlank())
                val used = Regex("\\{[^}]*}").findAll(text).map { it.value }.toSet()
                assertTrue("$id $field uses $used", placeholders.containsAll(used))
            }
        }
    }

    @Test
    fun supportEntriesHaveTheDocumentedShape() {
        val root = TestData.content("support.json").jsonObject
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
        for (entry in root.getValue("entries").jsonArray) {
            val obj = entry.jsonObject
            assertTrue(obj.str("region").isNotBlank())
            assertTrue(obj.str("name").isNotBlank())
            obj.getValue("verified").jsonPrimitive.boolean
        }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun contrast(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(hex: String): Double {
        fun channel(i: Int): Double {
            val c = hex.substring(i, i + 2).toInt(16) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5)
    }
}
