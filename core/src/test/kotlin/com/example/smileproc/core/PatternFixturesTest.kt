package com.example.smileproc.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId

class PatternFixturesTest {

    private val config: PatternConfig =
        PatternConfig.fromJson(File(TestData.content, "pattern-config.json").readText(Charsets.UTF_8))
    private val emotionTags: EmotionTags =
        EmotionTags.fromJson(File(TestData.content, "emotion-tags.json").readText(Charsets.UTF_8))
    private val library: PatternLibrary =
        PatternLibrary.fromJson(File(TestData.content, "pattern-library.en.json").readText(Charsets.UTF_8))

    @Serializable
    data class GoldenCandidate(
        val id: String,
        val confidence: Confidence,
        val nSegment: Int,
        val nRest: Int,
        val diff: Double?,
        val z: Double?,
        val score: Double,
    )

    @Serializable
    data class GoldenSelectedItem(
        val patternID: String,
        val libraryID: String,
        val confidence: Confidence,
    )

    @Serializable
    data class PatternGolden(
        val scenario: String,
        val variant: Variant,
        val isLowWeek: Boolean,
        val windowKind: WindowKind,
        val entryCount: Int,
        val moodCounts: List<Int>,
        val items: List<GoldenSelectedItem>,
        val candidates: List<GoldenCandidate>,
    )

    @Test
    fun allFourteenPatternFixturesMatchExpectationsAndGoldens() {
        val patternsDir = File(TestData.fixtures, "patterns")
        val inputFiles = patternsDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".input.json") }
            .sortedBy { it.name }
        assertEquals(14, inputFiles.size)

        val writeGoldens = System.getProperty("smileproc.writeGoldens") == "1" ||
            System.getenv("WRITE_GOLDENS") == "1"

        for (inputFile in inputFiles) {
            val name = inputFile.name.removeSuffix(".input.json")
            val inputObj = TestData.json(inputFile).jsonObject
            val expectObj = TestData.json(File(patternsDir, "$name.expect.json")).jsonObject

            val now = IsoInstantSerializer.parseOrThrow(inputObj.getValue("now").jsonPrimitive.content)
            val zone = ZoneId.of(inputObj.getValue("timeZoneID").jsonPrimitive.content)
            val ct = inputObj.getValue("cardTime").jsonObject
            val cardTime = WeeklyCardTime(
                weekday = ct.getValue("weekday").jsonPrimitive.int,
                hour = ct.getValue("hour").jsonPrimitive.int,
                minute = ct.getValue("minute").jsonPrimitive.int,
            )
            val labels = inputObj.getValue("labels").jsonArray.map { CanonicalJson.decodePlaceLabel(it.toString()) }
            val entries = inputObj.getValue("entries").jsonArray.map { CanonicalJson.decodeEntry(it.toString()) }
            val entryById = entries.associateBy { it.id }

            val buildResult = WeeklyCardBuilder.build(
                entries = entries,
                labels = labels,
                now = now,
                zone = zone,
                config = config,
                emotionTags = emotionTags,
                library = library,
                cardTime = cardTime,
            )
            val card = buildResult.card
            val candidates = buildResult.analysis.candidates

            expectObj["variant"]?.jsonPrimitive?.content?.let { expectedVariant ->
                assertEquals("$name variant", expectedVariant, card.variant.wireName)
            }
            expectObj["isLowWeek"]?.jsonPrimitive?.boolean?.let { expectedLowWeek ->
                assertEquals("$name isLowWeek", expectedLowWeek, card.isLowWeek)
            }
            expectObj["maxItemCount"]?.jsonPrimitive?.int?.let { expectedMax ->
                assertTrue(
                    "$name items (${card.items.size}) exceeded maxItemCount ($expectedMax)",
                    card.items.size <= expectedMax,
                )
            }
            // Enforce max one per family on every built card
            val pickedFamilies = card.items.map { it.pattern.family }
            assertEquals("$name max one per family", pickedFamilies.distinct().size, pickedFamilies.size)

            expectObj["firstItemLibraryID"]?.jsonPrimitive?.content?.let { expectedFirstLib ->
                assertTrue("$name expected at least 1 item", card.items.isNotEmpty())
                assertEquals("$name firstItemLibraryID", expectedFirstLib, card.items.first().libraryID)
            }
            expectObj["itemsIncludeLibraryIDs"]?.jsonArray?.let { arr ->
                val actualLibIds = card.items.map { it.libraryID }.toSet()
                for (elem in arr) {
                    val req = elem.jsonPrimitive.content
                    assertTrue("$name items $actualLibIds missing $req", req in actualLibIds)
                }
            }
            expectObj["candidatesInclude"]?.jsonArray?.forEach { candElem ->
                val obj = candElem.jsonObject
                val expectedId = obj.getValue("id").jsonPrimitive.content
                val expectedConf = obj.getValue("confidence").jsonPrimitive.content
                val found = candidates.firstOrNull { it.id == expectedId }
                assertTrue("$name missing candidate $expectedId (got ${candidates.map { it.id }})", found != null)
                assertEquals("$name candidate $expectedId confidence", expectedConf, found!!.confidence.wireName)
            }
            expectObj["dayparts"]?.jsonObject?.forEach { (entryId, daypartElem) ->
                val entry = entryById.getValue(entryId)
                assertEquals("$name daypart of $entryId", daypartElem.jsonPrimitive.content, entry.daypart().wireName)
            }

            // Golden snapshot handling (docs/spec/data.md › Fixtures › Goldens)
            val golden = PatternGolden(
                scenario = name,
                variant = card.variant,
                isLowWeek = card.isLowWeek,
                windowKind = card.windowKind,
                entryCount = card.entryCount,
                moodCounts = card.moodCounts,
                items = card.items.map {
                    GoldenSelectedItem(
                        patternID = it.patternID,
                        libraryID = it.libraryID,
                        confidence = it.pattern.confidence,
                    )
                },
                candidates = candidates.map { c ->
                    GoldenCandidate(
                        id = c.id,
                        confidence = c.confidence,
                        nSegment = c.nSegment,
                        nRest = c.nRest,
                        diff = c.diff?.let(::round6),
                        z = c.z?.let(::round6),
                        score = round6(c.score),
                    )
                },
            )
            val renderedGolden = CanonicalJson.prettyJson.encodeToString(PatternGolden.serializer(), golden)
                .replace("\r\n", "\n") + "\n"
            val goldenFile = File(patternsDir, "$name.golden.json")

            if (writeGoldens) {
                goldenFile.writeText(renderedGolden, Charsets.UTF_8)
            } else {
                assertTrue("Missing golden file: ${goldenFile.name}", goldenFile.isFile)
                val existing = goldenFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
                assertEquals("$name golden mismatch", existing, renderedGolden)
            }
        }
    }

    private fun round6(v: Double): Double =
        BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).toDouble()
}
