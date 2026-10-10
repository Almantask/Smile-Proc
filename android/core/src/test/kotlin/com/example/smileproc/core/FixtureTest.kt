package com.example.smileproc.core

import java.io.File
import java.util.Base64
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val spec: File = File(System.getProperty("smileproc.spec") ?: error("smileproc.spec"))
    private val config = ContentLoader.patternConfig(File(spec, "content/pattern-config.json").readText())
    private val library = ContentLoader.patternLibrary(File(spec, "content/pattern-library.en.json").readText())

    @Test
    fun configReadsEveryKey() {
        val raw = json.parseToJsonElement(File(spec, "content/pattern-config.json").readText()).jsonObject
        assertEquals(PatternConfig.keys.toSet(), raw.keys)
        for (key in PatternConfig.keys) {
            val text = raw.getValue(key).jsonPrimitive.content
            val actual = when (key) {
                "version" -> config.version.toString()
                "minSegment" -> config.minSegment.toString()
                "minRest" -> config.minRest.toString()
                "patternMinSegment" -> config.patternMinSegment.toString()
                "patternMinRest" -> config.patternMinRest.toString()
                "minAbsDiff" -> config.minAbsDiff.toString()
                "zHint" -> config.zHint.toString()
                "zPattern" -> config.zPattern.toString()
                "sdFloor" -> config.sdFloor.toString()
                "weekdayMinSegment" -> config.weekdayMinSegment.toString()
                "weekdayMinDistinctDates" -> config.weekdayMinDistinctDates.toString()
                "weekdayMinAbsDiff" -> config.weekdayMinAbsDiff.toString()
                "weekdayMinDataDays" -> config.weekdayMinDataDays.toString()
                "tagMinLowTagged" -> config.tagMinLowTagged.toString()
                "tagMinCount" -> config.tagMinCount.toString()
                "tagMinShare" -> config.tagMinShare.toString()
                "tagMinShareGap" -> config.tagMinShareGap.toString()
                "tagPatternCount" -> config.tagPatternCount.toString()
                "tagAtPlaceMinShare" -> config.tagAtPlaceMinShare.toString()
                "fineTagDominance" -> config.fineTagDominance.toString()
                "shortWeekThreshold" -> config.shortWeekThreshold.toString()
                "maxItems" -> config.maxItems.toString()
                "maxItemsLowWeek" -> config.maxItemsLowWeek.toString()
                "notEnoughDataBelow" -> config.notEnoughDataBelow.toString()
                "firstWeekDays" -> config.firstWeekDays.toString()
                "firstCardMinEntries" -> config.firstCardMinEntries.toString()
                "lowWeekMinEntries" -> config.lowWeekMinEntries.toString()
                "lowWeekShare" -> config.lowWeekShare.toString()
                "lowRunLength" -> config.lowRunLength.toString()
                "lowRunMinDays" -> config.lowRunMinDays.toString()
                "placeRadiusMeters" -> config.placeRadiusMeters.toString()
                "placeMaxAccuracyMeters" -> config.placeMaxAccuracyMeters.toString()
                else -> error("unread $key")
            }
            assertEquals(key, text.toDouble(), actual.toDouble(), 0.0)
        }
    }

    @Test
    fun canonicalEntryAndPlace() {
        val entryFile = File(spec, "fixtures/canonical/entry-1.json")
        val placeFile = File(spec, "fixtures/canonical/place-1.json")
        val entryText = entryFile.readText().trimEnd('\n')
        val placeText = placeFile.readText().trimEnd('\n')
        assertEquals(entryText, CanonicalJson.entry(CanonicalJson.parseEntry(entryText)))
        assertEquals(placeText, CanonicalJson.place(CanonicalJson.parsePlace(placeText)))
    }

    @Test
    fun patternFixtures() {
        val dir = File(spec, "fixtures/patterns")
        val names = dir.listFiles { file -> file.name.endsWith(".input.json") }!!
            .map { it.name.removeSuffix(".input.json") }
            .sorted()
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val input = json.parseToJsonElement(File(dir, "$name.input.json").readText()).jsonObject
            val card = cardFrom(input)
            val expect = json.parseToJsonElement(File(dir, "$name.expect.json").readText()).jsonObject
            assertEquals(name, expect.str("variant"), card.variant)
            assertEquals(name, expect.bool("isLowWeek"), card.isLowWeek)
            assertEquals(name, expect.str("windowKind"), card.windowKind)
            assertEquals(name, expect.int("maxItemCount"), card.maxItemCount)
            assertEquals(name, expect.int("itemCount"), card.items.size)
            val first = expect["firstItemLibraryID"]
            if (first == null || first is JsonNull) {
                assertTrue(name, card.items.isEmpty())
            } else {
                assertEquals(name, first.jsonPrimitive.content, card.items.first().libraryID)
            }
            val libs = expect.getValue("itemsIncludeLibraryIDs").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(name, libs, card.items.map { it.libraryID })
            val expectedCandidates = expect.getValue("candidatesInclude").jsonArray.map {
                it.jsonObject.str("id") to it.jsonObject.str("confidence")
            }
            assertEquals(
                name,
                expectedCandidates,
                card.candidates.map { it.id to it.confidence },
            )
            val dayparts = expect["dayparts"]?.jsonObject
            if (dayparts != null) {
                for ((id, value) in dayparts) {
                    assertEquals("$name $id", value.jsonPrimitive.content, card.dayparts[id])
                }
            }
            val golden = json.parseToJsonElement(File(dir, "$name.golden.json").readText()).jsonObject
            assertGolden(name, golden, card)
        }
    }

    @Test
    fun placeFixtures() {
        val dir = File(spec, "fixtures/places")
        val names = dir.listFiles { file -> file.name.endsWith(".input.json") }!!
            .map { it.name.removeSuffix(".input.json") }
            .sorted()
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val input = json.parseToJsonElement(File(dir, "$name.input.json").readText()).jsonObject
            val entries = input.getValue("entries").jsonArray.map { CanonicalJson.parseEntry(it.toString()) }
            val places = input.getValue("places").jsonArray.map { CanonicalJson.parsePlace(it.toString()) }
            val assignment = PlaceResolver.resolve(entries, places, config)
            val expect = json.parseToJsonElement(File(dir, "$name.expect.json").readText()).jsonObject
            for (group in expect.getValue("sameCluster").jsonArray) {
                val ids = group.jsonArray.map { it.jsonPrimitive.content }
                val clusters = ids.map { assignment.entryClusters[it] }
                assertTrue("$name $ids", clusters.all { it != null && it == clusters.first() })
            }
            for (pair in expect.getValue("differentCluster").jsonArray) {
                val ids = pair.jsonArray.map { it.jsonPrimitive.content }
                assertTrue(name, assignment.entryClusters[ids[0]] != assignment.entryClusters[ids[1]])
            }
            for (idElement in expect.getValue("unclustered").jsonArray) {
                assertNull("$name ${idElement.jsonPrimitive.content}", assignment.entryClusters[idElement.jsonPrimitive.content])
            }
            val clusterIds = expect["clusterIDs"]?.jsonObject
            if (clusterIds != null && "all" in clusterIds) {
                val expected = clusterIds.getValue("all").jsonPrimitive.content
                assertTrue(name, assignment.entryClusters.values.all { it == expected })
            }
            val golden = json.parseToJsonElement(File(dir, "$name.golden.json").readText()).jsonObject
            val goldenMap = golden.getValue("entryClusters").jsonObject.mapValues { it.value.jsonPrimitive.content }
            assertEquals(name, goldenMap, assignment.entryClusters)
            val goldenClusters = golden.getValue("clusters").jsonArray.map { it.jsonObject }
            assertEquals(name, goldenClusters.size, assignment.clusters.size)
            goldenClusters.zip(assignment.clusters).forEach { (expected, actual) ->
                assertEquals(name, expected.str("id"), actual.id)
                assertClose("$name lat", expected.dbl("centroidLatitude"), Rounding.r6(actual.centroidLatitude))
                assertClose("$name lon", expected.dbl("centroidLongitude"), Rounding.r6(actual.centroidLongitude))
                val members = expected.getValue("memberIDs").jsonArray.map { it.jsonPrimitive.content }
                assertEquals(name, members, actual.memberIDs)
                val label = expected["labelID"]
                val labelId = if (label == null || label is JsonNull) null else label.jsonPrimitive.content
                assertEquals(name, labelId, actual.labelID)
            }
        }
    }

    @Test
    fun periods() {
        val root = json.parseToJsonElement(File(spec, "fixtures/periods/periods.json").readText()).jsonObject
        for (case in root.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = case.str("name")
            val weekly = case.getValue("weeklyCard").jsonObject
            val period = CardPeriods.cardPeriod(
                Instants.parse(case.str("now")),
                case.str("timeZoneID"),
                weekly.int("weekday"),
                weekly.int("hour"),
                weekly.int("minute"),
            )
            assertEquals(name, case.str("periodStart"), Instants.format(period.periodStart))
            assertEquals(name, case.str("periodEnd"), Instants.format(period.periodEnd))
        }
    }

    @Test
    fun buildGate() {
        val root = json.parseToJsonElement(File(spec, "fixtures/periods/build-gate.json").readText()).jsonObject
        for (case in root.getValue("cases").jsonArray.map { it.jsonObject }) {
            val entries = case.getValue("entries").jsonArray.map { CanonicalJson.parseEntry(it.toString()) }
            val ends = case.getValue("existingPeriodEnds").jsonArray.map { it.jsonPrimitive.content }
            val actual = CardPeriods.shouldBuildCard(
                entries,
                config,
                Instants.parse(case.str("now")),
                "Europe/Rome",
                7,
                19,
                0,
                ends,
            )
            assertEquals(case.str("name"), case.bool("shouldBuild"), actual)
        }
    }

    @Test
    fun lowMood() {
        val root = json.parseToJsonElement(File(spec, "fixtures/lowmood/cases.json").readText()).jsonObject
        for (case in root.getValue("cases").jsonArray.map { it.jsonObject }) {
            val entries = case.getValue("entries").jsonArray.map { CanonicalJson.parseEntry(it.toString()) }
            val dismissed = case["dismissedEntryID"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
            assertEquals(case.str("name"), case.bool("armed"), LowMoodMonitor.armed(entries, config, dismissed))
        }
    }

    @Test
    fun libraryCoverage() {
        val root = json.parseToJsonElement(File(spec, "fixtures/library/coverage.json").readText()).jsonObject
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val params = linkedMapOf<String, String>()
            fun put(key: String) {
                val value = case[key]
                if (value != null && value !is JsonNull) params[key] = value.jsonPrimitive.content
            }
            put("segment")
            put("weekday")
            put("placeKind")
            put("tag")
            val direction = case["direction"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
            val matched = LibraryMatcher.match(case.str("detector"), direction, params, library)
            assertEquals(case.toString(), case.str("libraryID"), matched)
            assertNotNull(matched)
        }
    }

    @Test
    fun noiseGuard() {
        val rng = Random(20261010)
        val zone = java.time.ZoneId.of("Europe/Rome")
        val now = java.time.ZonedDateTime.of(2026, 10, 18, 19, 0, 0, 0, zone).toInstant()
        var total = 0
        for (week in 0 until 100) {
            val entries = ArrayList<Entry>(25)
            for (index in 0 until 25) {
                val mood = rng.nextInt(1, 4)
                val dayOffset = rng.nextInt(0, 7)
                val hour = rng.nextInt(0, 24)
                val minute = rng.nextInt(0, 60)
                val localEnd = now.atZone(zone)
                var created = java.time.ZonedDateTime.of(
                    localEnd.toLocalDate().minusDays(dayOffset.toLong()),
                    java.time.LocalTime.of(hour, minute),
                    zone,
                )
                if (!created.toInstant().isBefore(now)) {
                    created = created.minusDays(7)
                }
                val stamp = created.toInstant()
                entries.add(
                    Entry(
                        id = "n%03d-%02d".format(week, index),
                        createdAt = stamp,
                        updatedAt = stamp,
                        timeZoneID = "Europe/Rome",
                        mood = Mood.fromValue(mood),
                        tags = emptyList(),
                        comment = null,
                        location = null,
                        source = EntrySource.APP,
                    ),
                )
            }
            val card = WeeklyCardBuilder.build(entries, emptyList(), config, library, now, "Europe/Rome", 7, 19, 0)
            val byId = card.candidates.associateBy { it.id }
            total += card.items.count { byId.getValue(it.patternID).confidence == "pattern" }
        }
        val average = total / 100.0
        assertTrue("noise average $average", average <= 0.3)
    }

    @Test
    fun exportVectorRoundTrip() {
        val root = json.parseToJsonElement(File(spec, "fixtures/export/vector-1.json").readText()).jsonObject
        val password = root.str("password")
        val iterations = root.int("iterations")
        val salt = Base64.getDecoder().decode(root.str("saltBase64"))
        val nonce = Base64.getDecoder().decode(root.str("nonceBase64"))
        val plaintext = Base64.getDecoder().decode(root.str("plaintextBase64"))
        val envelope = ExportFormat.seal(password, salt, nonce, iterations, plaintext)
        val expected = ExportFormat.parseEnvelope(root.getValue("envelope").toString())
        assertEquals(expected.format, envelope.format)
        assertEquals(expected.version, envelope.version)
        assertEquals(expected.kdfName, envelope.kdfName)
        assertEquals(expected.iterations, envelope.iterations)
        assertTrue(expected.salt.contentEquals(envelope.salt))
        assertEquals(expected.cipher, envelope.cipher)
        assertEquals(expected.payloadBase64(), envelope.payloadBase64())
        assertTrue(plaintext.contentEquals(ExportFormat.open(envelope, password)))
        assertTrue(plaintext.contentEquals(ExportFormat.open(expected, password)))
        assertThrows(ExportCryptoException::class.java) {
            ExportFormat.open(expected, "not-the-password")
        }
        val parsed = CanonicalJson.parseExport(plaintext.toString(Charsets.UTF_8))
        assertEquals(plaintext.toString(Charsets.UTF_8), CanonicalJson.exportDocument(parsed))
    }

    @Test
    fun sealVector() {
        val root = json.parseToJsonElement(File(spec, "fixtures/export/seal-vector.json").readText()).jsonObject
        val key = Base64.getDecoder().decode(root.str("keyBase64"))
        val nonce = Base64.getDecoder().decode(root.str("nonceBase64"))
        val aad = root.str("aadUtf8").toByteArray(Charsets.UTF_8)
        val plaintext = Base64.getDecoder().decode(root.str("plaintextBase64"))
        val sealer = JcaSealer(key) { nonce }
        val sealed = sealer.seal(plaintext, aad)
        assertEquals(root.str("sealedBase64"), Base64.getEncoder().encodeToString(sealed))
        assertTrue(plaintext.contentEquals(sealer.open(sealed, aad)))
        val tampered = sealed.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x01).toByte()
        assertThrows(SealerException::class.java) { sealer.open(tampered, aad) }
        assertThrows(SealerException::class.java) { sealer.open(sealed, "other-id".toByteArray()) }
    }

    @Test
    fun importMergeDoesNotDuplicateAndNewerWins() {
        val older = sampleEntry("a", "2026-10-01T00:00:00.000Z", "2026-10-01T00:00:00.000Z", Mood.LOW)
        val newer = older.copy(updatedAt = Instants.parse("2026-10-02T00:00:00.000Z"), mood = Mood.GOOD)
        val once = ImportMerge.mergeEntries(emptyList(), listOf(older))
        val twice = ImportMerge.mergeEntries(once, listOf(older))
        assertEquals(1, twice.size)
        assertEquals(Mood.LOW, twice.first().mood)
        val updated = ImportMerge.mergeEntries(twice, listOf(newer))
        assertEquals(1, updated.size)
        assertEquals(Mood.GOOD, updated.first().mood)
        val stale = ImportMerge.mergeEntries(updated, listOf(older))
        assertEquals(Mood.GOOD, stale.first().mood)
    }

    private fun sampleEntry(id: String, created: String, updated: String, mood: Mood) = Entry(
        id = id,
        createdAt = Instants.parse(created),
        updatedAt = Instants.parse(updated),
        timeZoneID = "Europe/Rome",
        mood = mood,
        tags = emptyList(),
        comment = null,
        location = null,
        source = EntrySource.APP,
    )

    private fun cardFrom(input: JsonObject): WeeklyCard {
        val weekly = input.getValue("weeklyCard").jsonObject
        val entries = input.getValue("entries").jsonArray.map { CanonicalJson.parseEntry(it.toString()) }
        val places = input["places"]?.jsonArray?.map { CanonicalJson.parsePlace(it.toString()) } ?: emptyList()
        return WeeklyCardBuilder.build(
            entries,
            places,
            config,
            library,
            Instants.parse(input.str("now")),
            input.str("timeZoneID"),
            weekly.int("weekday"),
            weekly.int("hour"),
            weekly.int("minute"),
        )
    }

    private fun assertGolden(name: String, golden: JsonObject, card: WeeklyCard) {
        assertEquals(name, golden.str("periodStart"), Instants.format(card.periodStart))
        assertEquals(name, golden.str("periodEnd"), Instants.format(card.periodEnd))
        assertEquals(name, golden.str("windowKind"), card.windowKind)
        assertEquals(name, golden.int("entryCount"), card.entryCount)
        assertEquals(name, golden.getValue("moodCounts").jsonArray.map { it.jsonPrimitive.int }, card.moodCounts)
        val days = golden.getValue("days").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.int } }
        assertEquals(name, days, card.days)
        assertEquals(name, golden.bool("isLowWeek"), card.isLowWeek)
        assertEquals(name, golden.str("variant"), card.variant)
        assertEquals(name, golden.int("maxItemCount"), card.maxItemCount)
        val items = golden.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals("$name items", items.size, card.items.size)
        items.zip(card.items).forEach { (expected, actual) ->
            assertEquals(name, expected.str("patternID"), actual.patternID)
            assertEquals(name, expected.str("libraryID"), actual.libraryID)
        }
        val candidates = golden.getValue("candidates").jsonArray.map { it.jsonObject }
        assertEquals("$name candidates", candidates.size, card.candidates.size)
        candidates.zip(card.candidates.map { it.toPublic() }).forEach { (expected, actual) ->
            assertEquals(name, expected.str("id"), actual.id)
            assertEquals(name, expected.str("confidence"), actual.confidence)
            assertEquals(name, expected.int("nSegment"), actual.nSegment)
            assertEquals(name, expected.int("nRest"), actual.nRest)
            assertCloseNull("$name meanSeg", expected.dblOrNull("meanSegment"), actual.meanSegment)
            assertCloseNull("$name meanRest", expected.dblOrNull("meanRest"), actual.meanRest)
            assertCloseNull("$name diff", expected.dblOrNull("diff"), actual.diff)
            assertCloseNull("$name z", expected.dblOrNull("z"), actual.z)
            assertClose("$name score", expected.dbl("score"), actual.score)
        }
    }

    private fun assertClose(label: String, expected: Double, actual: Double) {
        assertTrue("$label expected $expected actual $actual", abs(expected - actual) <= 5e-7)
    }

    private fun assertCloseNull(label: String, expected: Double?, actual: Double?) {
        if (expected == null || actual == null) {
            assertEquals(label, expected, actual)
        } else {
            assertClose(label, expected, actual)
        }
    }
}

private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

private fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.boolean

private fun JsonObject.dbl(key: String): Double = getValue(key).jsonPrimitive.content.toDouble()

private fun JsonObject.dblOrNull(key: String): Double? {
    val value = get(key) ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.contentOrNull?.toDouble()
}
