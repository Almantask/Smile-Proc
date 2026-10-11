package com.example.smileproc.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Shape checks for the generated fixtures (tools/generate_fixtures.py). The pattern engine
 * that meets the expectations arrives in M2; this only proves the data is well formed.
 */
class FixturesSmokeTest {

    private val sets = listOf("patterns", "places", "periods")
    private val expectKeys = setOf(
        "firstItemLibraryID", "itemsIncludeLibraryIDs", "variant", "isLowWeek", "maxItemCount",
        "candidatesInclude", "dayparts", "sameCluster", "differentCluster", "unclustered",
        "cases", "notes",
    )
    private val entryKeys = listOf("id", "createdAt", "updatedAt", "timeZoneID", "mood", "tags", "comment", "location", "source")
    private val instant = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
    private val uuid = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")

    private val tagIds: Set<String> = TestData.content("emotion-tags.json").jsonObject.getValue("cores").jsonArray
        .flatMap { core -> listOf(core.jsonObject.str("id")) + core.jsonObject.getValue("fine").jsonArray.map { it.jsonPrimitive.content } }
        .toSet()
    private val libraryIds: Set<String> = TestData.content("pattern-library.en.json").jsonArray
        .map { it.jsonObject.str("id") }.toSet()

    @Test
    fun everyInputHasAnExpectationAndBothParse() {
        for (set in sets) {
            val files = File(TestData.fixtures, set).listFiles().orEmpty().map { it.name }.toSet()
            val inputs = files.filter { it.endsWith(".input.json") }.map { it.removeSuffix(".input.json") }.toSet()
            val expects = files.filter { it.endsWith(".expect.json") }.map { it.removeSuffix(".expect.json") }.toSet()
            assertTrue("$set has no fixtures", inputs.isNotEmpty())
            assertEquals("$set inputs vs expectations", inputs, expects)
            for (name in inputs) {
                TestData.json(File(TestData.fixtures, "$set/$name.input.json"))
                val expect = TestData.json(File(TestData.fixtures, "$set/$name.expect.json")).jsonObject
                assertTrue("$set/$name expect keys ${expect.keys}", expectKeys.containsAll(expect.keys))
            }
        }
    }

    @Test
    fun entriesUseTheCanonicalFormat() {
        for (file in inputFiles("patterns") + inputFiles("places")) {
            val entries = TestData.json(file).jsonObject.getValue("entries").jsonArray.map { it.jsonObject }
            assertTrue("${file.name} has entries", entries.isNotEmpty())
            for (e in entries) {
                val where = "${file.name} ${e["id"]}"
                assertEquals(where, entryKeys, e.keys.toList())
                assertTrue(where, uuid.matches(e.str("id")))
                assertTrue(where, instant.matches(e.str("createdAt")) && instant.matches(e.str("updatedAt")))
                assertTrue(where, e.getValue("mood").jsonPrimitive.int in 1..3)
                val tags = e.getValue("tags").jsonArray.map { it.jsonPrimitive.content }
                assertTrue(where, tags.size <= 3 && tagIds.containsAll(tags))
                assertTrue(where, e.str("source") in setOf("app", "notification"))
                val location = e.getValue("location")
                if (location != JsonNull) {
                    val loc = location.jsonObject
                    assertTrue(where, loc.str("kind") in setOf("fresh", "cached"))
                    assertTrue(where, instant.matches(loc.str("capturedAt")))
                }
            }
        }
    }

    @Test
    fun expectationsReferToRealEntriesAndLibraryIds() {
        for (input in inputFiles("patterns") + inputFiles("places")) {
            val ids = TestData.json(input).jsonObject.getValue("entries").jsonArray.map { it.jsonObject.str("id") }.toSet()
            val expect = TestData.json(File(input.parentFile, input.name.replace(".input.", ".expect."))).jsonObject
            val referenced = buildList {
                expect["dayparts"]?.jsonObject?.keys?.let(::addAll)
                for (key in listOf("sameCluster", "differentCluster")) {
                    expect[key]?.jsonArray?.forEach { group -> group.jsonArray.forEach { add(it.jsonPrimitive.content) } }
                }
                expect["unclustered"]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
            }
            assertTrue("${input.name} refers to unknown entries", ids.containsAll(referenced))
            val library = buildList {
                expect["firstItemLibraryID"]?.let { add(it.jsonPrimitive.content) }
                expect["itemsIncludeLibraryIDs"]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
            }
            assertTrue("${input.name} refers to unknown library ids $library", libraryIds.containsAll(library))
        }
    }

    private fun inputFiles(set: String): List<File> =
        File(TestData.fixtures, set).listFiles().orEmpty().filter { it.name.endsWith(".input.json") }.sortedBy { it.name }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
