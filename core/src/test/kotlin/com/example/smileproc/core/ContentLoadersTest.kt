package com.example.smileproc.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentLoadersTest {

    @Test
    fun patternConfigReadsEverySingleKeyAndRejectsMissingOrExtraKeys() {
        val file = File(TestData.content, "pattern-config.json")
        val raw = file.readText(Charsets.UTF_8)
        val base = PatternConfig.fromJson(raw)
        val root = TestData.json(file).jsonObject
        assertEquals(32, root.size)

        // Mutating each threshold key produces a distinct PatternConfig (every key is read).
        for ((key, value) in root) {
            val prim = value.jsonPrimitive
            if (key == "version") {
                val bumped = JsonObject(root + (key to JsonPrimitive(2))).toString()
                assertThrows(IllegalArgumentException::class.java) {
                    PatternConfig.fromJson(bumped)
                }
            } else {
                val mutatedValue = if ('.' in prim.content) {
                    JsonPrimitive(prim.double + 0.05)
                } else {
                    JsonPrimitive(prim.int + 1)
                }
                val mutatedJson = JsonObject(root + (key to mutatedValue)).toString()
                val mutated = PatternConfig.fromJson(mutatedJson)
                assertNotEquals("Mutating $key must change PatternConfig", base, mutated)
            }

            // Removing any key must fail (no fallback defaults in code).
            val missingKeyJson = JsonObject(root - key).toString()
            assertThrows(SerializationException::class.java) {
                PatternConfig.fromJson(missingKeyJson)
            }
        }

        // Unknown extra key must also fail.
        val extraJson = JsonObject(root + ("unexpectedThreshold" to JsonPrimitive(1))).toString()
        assertThrows(SerializationException::class.java) {
            PatternConfig.fromJson(extraJson)
        }
    }

    @Test
    fun emotionTagsAndPatternLibraryLoadRuntimeFiles() {
        val tags = EmotionTags.fromJson(File(TestData.content, "emotion-tags.json").readText(Charsets.UTF_8))
        assertEquals(7, tags.cores.size)
        assertEquals(42, tags.allTagIds.size)
        assertEquals("anxious", tags.coreOf("anxious.stressed"))
        assertEquals("tired", tags.coreOf("tired"))
        assertTrue(tags.isValidTag("surprised.moved"))
        assertFalse(tags.isValidTag("surprised.unknown"))

        val library = PatternLibrary.fromJson(File(TestData.content, "pattern-library.en.json").readText(Charsets.UTF_8))
        assertEquals(16, library.entries.size)
        val rendered = library.getValue("stress-at-place").render(place = "Office")
        assertEquals("Stress tends to show up at Office", rendered.title)
        assertTrue(rendered.tip.contains("Office"))
    }

    @Test
    fun supportCatalogLoadsSeedAndFiltersUnverifiedUnlessRequested() {
        val seed = SupportCatalog.fromJson(File(TestData.content, "support.json").readText(Charsets.UTF_8))
        assertTrue(seed.entries.isEmpty())
        assertTrue(seed.contactsForRegion("IT").isEmpty())

        val synthetic = SupportCatalog.fromJson(
            """
            {
              "version": 1,
              "entries": [
                { "region": "IT", "name": "Example Unverified", "phone": "000", "verified": false },
                { "region": "IT", "name": "Example Verified", "url": "https://example.invalid", "verified": true },
                { "region": "US", "name": "Other Region", "verified": true }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(listOf("Example Verified"), synthetic.contactsForRegion("it").map { it.name })
        assertEquals(
            listOf("Example Unverified", "Example Verified"),
            synthetic.contactsForRegion("IT", includeUnverified = true).map { it.name },
        )
    }

    private fun <T : Throwable> assertThrows(expected: Class<T>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (expected.isInstance(t)) return
            throw AssertionError("Expected ${expected.simpleName}, got ${t::class.simpleName}: ${t.message}", t)
        }
        throw AssertionError("Expected ${expected.simpleName}, nothing thrown")
    }
}
