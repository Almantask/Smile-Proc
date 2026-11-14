package com.example.smileproc.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * app/src/main/res/values/strings.xml has every id the spec names (data.md › Strings,
 * logic.md › Library matching) and the copy screens.md gives word for word.
 */
class StringsContractTest {

    /** name -> text of each <string>, or of the "other" item of each <plurals>, unescaped. */
    private val strings: Map<String, String> = run {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(TestData.strings)
        val out = mutableMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> out[name] = unescape(node.textContent)
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        if (item.getAttribute("quantity") == "other") out[name] = unescape(item.textContent)
                    }
                }
            }
        }
        out
    }

    private fun unescape(text: String) = text.replace("\\'", "'").replace("\\\"", "\"")

    @Test
    fun everyTagHasALabel() {
        val cores = TestData.content("emotion-tags.json").jsonObject.getValue("cores").jsonArray.map { it.jsonObject }
        val missing = cores.flatMap { core ->
            val id = core.getValue("id").jsonPrimitive.content
            listOf("tag_$id") + core.getValue("fine").jsonArray.map { "tag_" + it.jsonPrimitive.content.replace('.', '_') }
        }.filterNot { it in strings }
        assertEquals("missing tag labels", emptyList<String>(), missing)
    }

    @Test
    fun everyDetectorAndDirectionHasAnObservationLine() {
        val withDirection = listOf("timeOfDay", "weekday", "weekdayDaypart", "place", "awayFromHome")
            .flatMap { d -> listOf("lower", "higher").map { "week_observation_${d}_$it" } }
        val tagDetectors = listOf("week_observation_tagOnLow", "week_observation_tagAtPlace")
        val missing = (withDirection + tagDetectors).filterNot { it in strings }
        assertEquals("missing observation lines", emptyList<String>(), missing)
        assertEquals(
            "Your %1\$s check-ins were lower than the rest of your day (%2\$d check-ins).",
            strings["week_observation_timeOfDay_lower"],
        )
    }

    @Test
    fun facesDaypartsAndNotificationTextMatchTheSpec() {
        assertEquals(listOf("Low", "Okay", "Good"), listOf("face_low", "face_okay", "face_good").map { strings[it] })
        for (part in listOf("morning", "afternoon", "evening", "night")) assertTrue(part, "daypart_$part" in strings)
        assertEquals("SmileProc", strings["notification_title"])
        assertEquals(
            listOf("How are you right now?", "Saved ✓", "It works ✓", "Couldn't save. Open SmileProc."),
            listOf("notification_text_ask", "notification_text_saved", "notification_text_test_done", "notification_text_failed")
                .map { strings[it] },
        )
    }

    @Test
    fun lowMoodCopyMatchesScreensMd() {
        assertEquals("This week looked heavy. That's worth noticing, and it's okay.", strings["week_low_acknowledgment"])
        assertEquals("Talking to someone you trust can make it lighter.", strings["week_low_talk"])
        assertEquals("Talk to someone", strings["week_low_talk_action"])
        assertEquals(
            "Your last few check-ins have been low. If it would help, talking to someone can make it lighter.",
            strings["lowrun_text"],
        )
        assertEquals("See support options", strings["lowrun_see_support"])
        assertEquals("Not now", strings["action_not_now"])
    }

    @Test
    fun lithuanianStringsCoverAllTranslatableKeysClrdPluralsAndPlaceholders() {
        val factory = DocumentBuilderFactory.newInstance()
        val enDoc = factory.newDocumentBuilder().parse(TestData.strings)
        val ltFile = java.io.File(TestData.strings.parentFile.parentFile, "values-lt/strings.xml")
        assertTrue("values-lt/strings.xml must exist at $ltFile", ltFile.exists())
        val ltDoc = factory.newDocumentBuilder().parse(ltFile)

        data class ResourceData(
            val strings: Map<String, String>,
            val nonTranslatableStrings: Set<String>,
            val plurals: Map<String, Map<String, String>>,
        )

        fun parse(doc: org.w3c.dom.Document): ResourceData {
            val strMap = linkedMapOf<String, String>()
            val nonTranslatable = linkedSetOf<String>()
            val pluralMap = linkedMapOf<String, Map<String, String>>()
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? Element ?: continue
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "string" -> {
                        if (node.getAttribute("translatable") == "false") {
                            nonTranslatable += name
                        } else {
                            strMap[name] = unescape(node.textContent)
                        }
                    }
                    "plurals" -> {
                        val itemsMap = linkedMapOf<String, String>()
                        val items = node.getElementsByTagName("item")
                        for (j in 0 until items.length) {
                            val item = items.item(j) as Element
                            itemsMap[item.getAttribute("quantity")] = unescape(item.textContent)
                        }
                        pluralMap[name] = itemsMap
                    }
                }
            }
            return ResourceData(strMap, nonTranslatable, pluralMap)
        }

        val en = parse(enDoc)
        val lt = parse(ltDoc)

        val missingStrings = en.strings.keys - lt.strings.keys
        assertEquals("Missing LT translations for <string>", emptySet<String>(), missingStrings)

        val extraLtStrings = lt.strings.keys - en.strings.keys
        assertEquals("Extra LT <string> keys not in default strings.xml", emptySet<String>(), extraLtStrings)

        val emptyLtStrings = lt.strings.filterValues { it.isBlank() }.keys
        assertEquals("Blank LT <string> values", emptySet<String>(), emptyLtStrings)

        val missingPlurals = en.plurals.keys - lt.plurals.keys
        assertEquals("Missing LT translations for <plurals>", emptySet<String>(), missingPlurals)

        val extraLtPlurals = lt.plurals.keys - en.plurals.keys
        assertEquals("Extra LT <plurals> keys not in default strings.xml", emptySet<String>(), extraLtPlurals)

        val requiredLtQuantities = setOf("one", "few", "many", "other")
        val formatRegex = Regex("%\\d+\\$[\\d.]*[sd]")

        for ((name, enText) in en.strings) {
            val ltText = lt.strings.getValue(name)
            val enArgs = formatRegex.findAll(enText).map { it.value }.toSet()
            val ltArgs = formatRegex.findAll(ltText).map { it.value }.toSet()
            assertEquals("Format arguments mismatch for <string name=\"$name\">", enArgs, ltArgs)
        }

        for ((name, enForms) in en.plurals) {
            val ltForms = lt.plurals.getValue(name)
            assertEquals("CLDR Lithuanian plural quantities for $name", requiredLtQuantities, ltForms.keys)
            val enArgs = formatRegex.findAll(enForms.getValue("other")).map { it.value }.toSet()
            for ((qty, ltText) in ltForms) {
                assertTrue("$name[$qty] must not be blank", ltText.isNotBlank())
                val ltArgs = formatRegex.findAll(ltText).map { it.value }.toSet()
                assertEquals("Format arguments mismatch for $name[$qty]", enArgs, ltArgs)
            }
        }

        for (day in 1..7) {
            assertTrue("weekday_short_$day in EN", "weekday_short_$day" in en.strings)
            assertTrue("weekday_short_$day in LT", "weekday_short_$day" in lt.strings)
        }
        assertTrue("week_hour_24 in EN", "week_hour_24" in (en.strings.keys + en.nonTranslatableStrings))
    }
}
