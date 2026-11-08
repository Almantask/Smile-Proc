package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.smileproc.core.Cluster
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.LatLon
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.ui.places.PlaceNamingSheet
import com.example.smileproc.ui.places.PlacesScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PlacesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeEntry(
        lat: Double = 52.5200,
        lon: Double = 13.4050,
    ): Entry {
        val now = Instant.parse("2026-11-02T10:00:00Z")
        return Entry(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = emptyList(),
            comment = null,
            location = LocationSample(lat, lon, 15.0, now, LocationKind.FRESH),
            source = EntrySource.APP,
        )
    }

    @Test
    fun placesScreen_emptyState_showsEmptyMessage() {
        composeTestRule.setContent {
            PlacesScreen(
                entries = emptyList(),
                placeLabels = emptyList(),
                places = PlaceAssignment(emptyMap(), emptyMap()),
                onSavePlaceLabel = {},
                onDeletePlaceLabel = {},
                onRemoveLocationFromAllEntries = {},
                onNavigateBack = {},
            )
        }

        composeTestRule.onNodeWithText("Places show up here once you've checked in with location on.")
            .assertIsDisplayed()
    }

    @Test
    fun placesScreen_showsNamedAndUnnamedPlaces() {
        val entry1 = makeEntry(52.5200, 13.4050)
        val entry2 = makeEntry(52.5000, 13.4000)
        val homeLabelId = UUID.randomUUID().toString()

        val homeLabel = PlaceLabel(
            id = homeLabelId,
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.parse("2026-11-01T10:00:00Z"),
        )

        val homeCluster = Cluster(
            id = ClusterID.named(homeLabelId),
            centroid = LatLon(52.5200, 13.4050),
            memberIDs = listOf(entry1.id),
            label = homeLabel,
        )
        val unnamedCluster = Cluster(
            id = ClusterID.unnamed(entry2.id),
            centroid = LatLon(52.5000, 13.4000),
            memberIDs = listOf(entry2.id),
            label = null,
        )

        val assignment = PlaceAssignment(
            entryToCluster = mapOf(entry1.id to homeCluster.id, entry2.id to unnamedCluster.id),
            clusters = mapOf(homeCluster.id to homeCluster, unnamedCluster.id to unnamedCluster),
        )

        composeTestRule.setContent {
            PlacesScreen(
                entries = listOf(entry1, entry2),
                placeLabels = listOf(homeLabel),
                places = assignment,
                onSavePlaceLabel = {},
                onDeletePlaceLabel = {},
                onRemoveLocationFromAllEntries = {},
                onNavigateBack = {},
            )
        }

        // Named place displays name, kind and count
        composeTestRule.onAllNodesWithText("Home")[0].assertIsDisplayed()
        composeTestRule.onNodeWithText("1 check-in").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rename").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Forget this name").performScrollTo().assertIsDisplayed()

        // Unnamed place displays summary and "Name this place"
        composeTestRule.onNodeWithText("Name this place").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun placesScreen_forgetName_showsDialogAndInvokesCallback() {
        val entry = makeEntry(52.5200, 13.4050)
        val homeLabelId = UUID.randomUUID().toString()
        val homeLabel = PlaceLabel(
            id = homeLabelId,
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.now(),
        )
        val homeCluster = Cluster(
            id = ClusterID.named(homeLabelId),
            centroid = LatLon(52.5200, 13.4050),
            memberIDs = listOf(entry.id),
            label = homeLabel,
        )
        val assignment = PlaceAssignment(
            entryToCluster = mapOf(entry.id to homeCluster.id),
            clusters = mapOf(homeCluster.id to homeCluster),
        )

        var deletedLabelId: String? = null

        composeTestRule.setContent {
            PlacesScreen(
                entries = listOf(entry),
                placeLabels = listOf(homeLabel),
                places = assignment,
                onSavePlaceLabel = {},
                onDeletePlaceLabel = { deletedLabelId = it },
                onRemoveLocationFromAllEntries = {},
                onNavigateBack = {},
            )
        }

        composeTestRule.onNodeWithText("Forget this name").performClick()
        // Confirmation dialog shown
        composeTestRule.onNodeWithText("Forget the name Home? Its check-ins stay and show as an unnamed place again.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()

        assertEquals(homeLabelId, deletedLabelId)
    }

    @Test
    fun placesScreen_removeLocation_showsDialogAndInvokesCallback() {
        val entry = makeEntry(52.5200, 13.4050)
        val homeLabelId = UUID.randomUUID().toString()
        val homeLabel = PlaceLabel(
            id = homeLabelId,
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.now(),
        )
        val homeCluster = Cluster(
            id = ClusterID.named(homeLabelId),
            centroid = LatLon(52.5200, 13.4050),
            memberIDs = listOf(entry.id),
            label = homeLabel,
        )
        val assignment = PlaceAssignment(
            entryToCluster = mapOf(entry.id to homeCluster.id),
            clusters = mapOf(homeCluster.id to homeCluster),
        )

        var removeInvoked = false

        composeTestRule.setContent {
            PlacesScreen(
                entries = listOf(entry),
                placeLabels = listOf(homeLabel),
                places = assignment,
                onSavePlaceLabel = {},
                onDeletePlaceLabel = {},
                onRemoveLocationFromAllEntries = { removeInvoked = true },
                onNavigateBack = {},
            )
        }

        composeTestRule.onNodeWithText("Remove location from all entries").performClick()
        composeTestRule.onNodeWithText("Remove location from every check-in?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove location").performClick()

        assertTrue(removeInvoked)
    }

    @Test
    fun placeNamingSheet_homeChip_setsNameAndKind() {
        var savedLabel: PlaceLabel? = null

        composeTestRule.setContent {
            PlaceNamingSheet(
                centroid = LatLon(52.5200, 13.4050),
                existingLabels = emptyList(),
                onSaveLabel = { savedLabel = it },
                onDismiss = {},
            )
        }

        // Click Home suggestion chip (first 'Home' node)
        composeTestRule.onAllNodesWithText("Home")[0].performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        assertNotNull(savedLabel)
        assertEquals("Home", savedLabel?.name)
        assertEquals(PlaceKind.HOME, savedLabel?.kind)
    }

    @Test
    fun placeNamingSheet_mergeCandidate_promptsMergeAndCombinesAnchor() {
        val homeLabelId = UUID.randomUUID().toString()
        val existingHome = PlaceLabel(
            id = homeLabelId,
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.now(),
        )

        var savedLabel: PlaceLabel? = null

        // New centroid 30 meters away from Home (well within 100m)
        val nearbyCentroid = LatLon(52.5202, 13.4052)

        composeTestRule.setContent {
            PlaceNamingSheet(
                centroid = nearbyCentroid,
                existingLabels = listOf(existingHome),
                clusterMemberPoints = listOf(nearbyCentroid),
                allEntries = emptyList(),
                onSaveLabel = { savedLabel = it },
                onDismiss = {},
            )
        }

        // Click Work chip (first 'Work' node) and tap Save
        composeTestRule.onAllNodesWithText("Work")[0].performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        // "Is this Home?" merge dialog appears
        composeTestRule.onNodeWithText("Is this Home?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yes").performClick()

        assertNotNull(savedLabel)
        assertEquals(homeLabelId, savedLabel?.id) // Merged into existing Home!
        assertEquals("Home", savedLabel?.name)
    }

    @Test
    fun placesScreen_renamePlace_modifiesNameAndKind() {
        val entry = makeEntry(52.5200, 13.4050)
        val studioId = UUID.randomUUID().toString()
        val studioLabel = PlaceLabel(
            id = studioId,
            name = "Studio",
            kind = PlaceKind.OTHER,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.now(),
        )
        val cluster = Cluster(
            id = ClusterID.named(studioId),
            centroid = LatLon(52.5200, 13.4050),
            memberIDs = listOf(entry.id),
            label = studioLabel,
        )
        val assignment = PlaceAssignment(
            entryToCluster = mapOf(entry.id to cluster.id),
            clusters = mapOf(cluster.id to cluster),
        )

        var savedLabel: PlaceLabel? = null

        composeTestRule.setContent {
            PlacesScreen(
                entries = listOf(entry),
                placeLabels = listOf(studioLabel),
                places = assignment,
                onSavePlaceLabel = { savedLabel = it },
                onDeletePlaceLabel = {},
                onRemoveLocationFromAllEntries = {},
                onNavigateBack = {},
            )
        }

        composeTestRule.onNodeWithText("Rename").performScrollTo().performClick()

        // In naming sheet, change kind to WORK and name to "Work Studio"
        composeTestRule.onAllNodesWithText("Work")[1].performClick()
        composeTestRule.onNodeWithText("Another name").performTextClearance()
        composeTestRule.onNodeWithText("Another name").performTextInput("Work Studio")
        composeTestRule.onNodeWithText("Save").performClick()

        assertNotNull(savedLabel)
        assertEquals(studioId, savedLabel?.id) // Retains original id
        assertEquals("Work Studio", savedLabel?.name)
        assertEquals(PlaceKind.WORK, savedLabel?.kind)
    }

    @Test
    fun placeNamingSheet_mergeCandidate_declineMerge_savesNewPlace() {
        val homeLabelId = UUID.randomUUID().toString()
        val existingHome = PlaceLabel(
            id = homeLabelId,
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.now(),
        )

        var savedLabel: PlaceLabel? = null
        val nearbyCentroid = LatLon(52.5202, 13.4052)

        composeTestRule.setContent {
            PlaceNamingSheet(
                centroid = nearbyCentroid,
                existingLabels = listOf(existingHome),
                clusterMemberPoints = listOf(nearbyCentroid),
                allEntries = emptyList(),
                onSaveLabel = { savedLabel = it },
                onDismiss = {},
            )
        }

        // Enter custom name "Cafe" and click Save
        composeTestRule.onNodeWithText("Another name").performTextInput("Cafe")
        composeTestRule.onNodeWithText("Save").performClick()

        // "Is this Home?" appears; click "No"
        composeTestRule.onNodeWithText("Is this Home?").assertIsDisplayed()
        composeTestRule.onNodeWithText("No").performClick()

        assertNotNull(savedLabel)
        assertTrue("Declining merge creates distinct id", savedLabel?.id != homeLabelId)
        assertEquals("Cafe", savedLabel?.name)
    }
}
