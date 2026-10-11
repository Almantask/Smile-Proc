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
}
