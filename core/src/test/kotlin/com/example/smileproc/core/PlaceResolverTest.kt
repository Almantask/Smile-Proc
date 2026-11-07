package com.example.smileproc.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class PlaceResolverTest {

    private val config: PatternConfig =
        PatternConfig.fromJson(File(TestData.content, "pattern-config.json").readText(Charsets.UTF_8))

    @Test
    fun allPlaceFixturesMatchExpectations() {
        val inputs = File(TestData.fixtures, "places")
            .listFiles().orEmpty()
            .filter { it.name.endsWith(".input.json") }
            .sortedBy { it.name }
        assertEquals(5, inputs.size)

        for (inputFile in inputs) {
            val name = inputFile.name.removeSuffix(".input.json")
            val inputObj = TestData.json(inputFile).jsonObject
            val expectObj = TestData.json(File(inputFile.parentFile, "$name.expect.json")).jsonObject

            val labels = inputObj.getValue("labels").jsonArray.map { CanonicalJson.decodePlaceLabel(it.toString()) }
            val entries = inputObj.getValue("entries").jsonArray.map { CanonicalJson.decodeEntry(it.toString()) }

            val assignment = PlaceResolver.resolve(entries, labels, config)

            expectObj["sameCluster"]?.jsonArray?.forEach { groupElem ->
                val ids = groupElem.jsonArray.map { it.jsonPrimitive.content }
                val firstCluster = assignment.clusterOf(ids.first())
                assertNotNull("$name: ${ids.first()} should be clustered", firstCluster)
                for (id in ids) {
                    assertEquals("$name: $id should share cluster with ${ids.first()}", firstCluster, assignment.clusterOf(id))
                }
            }

            expectObj["differentCluster"]?.jsonArray?.forEach { pairElem ->
                val pair = pairElem.jsonArray.map { it.jsonPrimitive.content }
                assertEquals(2, pair.size)
                val c1 = assignment.clusterOf(pair[0])
                val c2 = assignment.clusterOf(pair[1])
                assertNotNull("$name: ${pair[0]} should have cluster", c1)
                assertNotNull("$name: ${pair[1]} should have cluster", c2)
                assertNotEquals("$name: ${pair[0]} and ${pair[1]} must be in different clusters", c1, c2)
            }

            expectObj["unclustered"]?.jsonArray?.forEach { idElem ->
                val id = idElem.jsonPrimitive.content
                assertNull("$name: $id must be unclustered", assignment.clusterOf(id))
            }

            // For centroid-drift, verify the second cluster keeps "u:<earliest member id>"
            if (name == "centroid-drift") {
                val secondGroup = expectObj.getValue("sameCluster").jsonArray[1].jsonArray.map { it.jsonPrimitive.content }
                assertEquals(ClusterID.unnamed(secondGroup.first()), assignment.clusterOf(secondGroup.first()))
            }
        }
    }

    @Test
    fun tieRulePicksLowerClusterIdStringInNamedAndUnnamedPasses() {
        val baseTime = Instant.parse("2026-10-12T10:00:00.000Z")
        // Named pass tie: two labels at identical coordinates -> lower "p:<id>" wins.
        val labelHigher = PlaceLabel(
            id = "bbbbbbbb-0000-4000-8000-000000000002",
            name = "B",
            kind = PlaceKind.OTHER,
            anchorLatitude = 45.0,
            anchorLongitude = 0.0,
            createdAt = baseTime,
        )
        val labelLower = PlaceLabel(
            id = "aaaaaaaa-0000-4000-8000-000000000001",
            name = "A",
            kind = PlaceKind.HOME,
            anchorLatitude = 45.0,
            anchorLongitude = 0.0,
            createdAt = baseTime,
        )
        val entry = locatedEntry(
            id = "11111111-0000-4000-8000-000000000001",
            createdAt = baseTime,
            lat = 45.0,
            lon = 0.0,
        )
        val namedAssignment = PlaceResolver.resolve(listOf(entry), listOf(labelHigher, labelLower), config)
        assertEquals(ClusterID.named(labelLower.id), namedAssignment.clusterOf(entry.id))

        // Unnamed pass tie: two clusters symmetric east/west around (0.0, 0.0) at +-0.0005 deg (~55.6 m);
        // third point at (0.0, 0.0) is equidistant to both -> lower "u:<id>" wins.
        val westHigherId = "ffffffff-0000-4000-8000-000000000001"
        val eastLowerId = "00000001-0000-4000-8000-000000000002"
        val middleId = "99999999-0000-4000-8000-000000000003"
        val e1 = locatedEntry(westHigherId, baseTime, lat = 0.0, lon = -0.0005) // 111 m from e2 -> separate clusters
        val e2 = locatedEntry(eastLowerId, baseTime.plusSeconds(60), lat = 0.0, lon = 0.0005)
        val e3 = locatedEntry(middleId, baseTime.plusSeconds(120), lat = 0.0, lon = 0.0) // 55.6 m from both!

        assertEquals(
            Geo.haversineMeters(0.0, 0.0, 0.0, -0.0005),
            Geo.haversineMeters(0.0, 0.0, 0.0, 0.0005),
            0.0,
        )
        val unnamedAssignment = PlaceResolver.resolve(listOf(e1, e2, e3), emptyList(), config)
        assertEquals(ClusterID.unnamed(eastLowerId), unnamedAssignment.clusterOf(middleId))
    }

    @Test
    fun mergePromptAndUnnamedPlaceSummaryWorkPerSpec() {
        val t0 = Instant.parse("2026-10-12T08:00:00.000Z") // Mon 12 Oct 2026, 10:00 CEST (morning)
        val homeLabel = PlaceLabel(
            id = "aaaaaaaa-0000-4000-8000-000000000001",
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 45.4642,
            anchorLongitude = 9.1900,
            createdAt = t0,
        )
        val nearby = LatLon(45.4646, 9.1900) // ~44 m north
        val far = LatLon(45.4800, 9.1900) // > 1.7 km north
        assertEquals(homeLabel, PlaceResolver.findMergeCandidate(nearby, listOf(homeLabel), config))
        assertNull(PlaceResolver.findMergeCandidate(far, listOf(homeLabel), config))

        val merged = PlaceResolver.mergedAnchor(
            existingLabel = homeLabel,
            existingMemberPoints = listOf(LatLon(45.4640, 9.1900)),
            newMemberPoints = listOf(LatLon(45.4650, 9.1900)),
        )
        assertEquals(45.4645, merged.latitude, 1e-9)

        val entries = (0 until 5).map { i ->
            locatedEntry(
                id = "0000000$i-0000-4000-8000-000000000001",
                createdAt = t0.plusSeconds(i * 86_400L), // Mon..Fri mornings in Europe/Rome
                lat = 45.4642,
                lon = 9.19,
                zone = "Europe/Rome",
            )
        }
        val summary = PlaceResolver.summarizeUnnamed(entries)
        assertEquals(5, summary.checkInCount)
        assertFalse(summary.mostlyWeekend)
        assertEquals(Daypart.MORNING, summary.dominantDaypart)
    }

    private fun locatedEntry(
        id: String,
        createdAt: Instant,
        lat: Double,
        lon: Double,
        zone: String = "UTC",
    ): Entry = Entry(
        id = id,
        createdAt = createdAt,
        updatedAt = createdAt,
        timeZoneID = zone,
        mood = Mood.OKAY,
        tags = emptyList(),
        comment = null,
        location = LocationSample(
            latitude = lat,
            longitude = lon,
            horizontalAccuracy = 10.0,
            capturedAt = createdAt,
            kind = LocationKind.FRESH,
        ),
        source = EntrySource.APP,
    )
}
