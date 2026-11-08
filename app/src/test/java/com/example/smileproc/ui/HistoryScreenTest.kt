package com.example.smileproc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.history.HistoryScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEntriesAndDeletesWithConfirmation() {
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = listOf("happy"),
            comment = "Great coffee morning",
            location = null,
            source = EntrySource.APP,
        )

        var deletedId: String? = null

        composeTestRule.setContent {
            HistoryScreen(
                entries = listOf(entry),
                places = null,
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = {},
                onDeleteEntry = { deletedId = it },
                onSavePlaceLabel = {},
                onNavigateBack = {},
            )
        }

        // Check entry comment is displayed in history
        composeTestRule.onNodeWithText("Great coffee morning").assertIsDisplayed()

        // Tap the entry row to open EntryDetailSheet
        composeTestRule.onNodeWithText("Great coffee morning").performClick()

        // Detail sheet opens with "Check-in"
        composeTestRule.onNodeWithText("Check-in").assertIsDisplayed()

        // Tap "Delete check-in"
        composeTestRule.onNodeWithContentDescription("Delete check-in")
            .performScrollTo()
            .performClick()

        // Confirmation dialog appears
        composeTestRule.onNodeWithText("Delete this check-in?").assertIsDisplayed()

        // Tap confirm "Delete"
        composeTestRule.onNodeWithContentDescription("Delete").performClick()

        assertEquals(entry.id, deletedId)
    }

    @Test
    fun editsMoodAndComment_savesOnSaveClick() {
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = emptyList(),
            comment = "Initial note",
            location = null,
            source = EntrySource.APP,
        )

        var savedEntry: Entry? = null

        composeTestRule.setContent {
            HistoryScreen(
                entries = listOf(entry),
                places = null,
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = { savedEntry = it },
                onDeleteEntry = {},
                onSavePlaceLabel = {},
                onNavigateBack = {},
            )
        }

        // Tap entry to open EntryDetailSheet
        composeTestRule.onNodeWithText("Initial note").performClick()
        composeTestRule.onNodeWithText("Check-in").assertIsDisplayed()

        // Change mood to Okay
        composeTestRule.onNodeWithContentDescription("Okay").performClick()

        // Tap Save
        composeTestRule.onNodeWithContentDescription("Save")
            .performScrollTo()
            .performClick()

        assertNotNull(savedEntry)
        assertEquals(entry.id, savedEntry?.id)
        assertEquals(Mood.OKAY, savedEntry?.mood)
    }

    @Test
    fun displaysPlaceNameInEntryRow() {
        val entryId = UUID.randomUUID().toString()
        val entry = Entry(
            id = entryId,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = emptyList(),
            comment = "Coffee at Home",
            location = LocationSample(52.5200, 13.4050, 20.0, Instant.now(), LocationKind.FRESH),
            source = EntrySource.APP,
        )

        val homeLabel = PlaceLabel(
            id = UUID.randomUUID().toString(),
            name = "Home",
            kind = PlaceKind.HOME,
            anchorLatitude = 52.5200,
            anchorLongitude = 13.4050,
            createdAt = Instant.now(),
        )
        val homeCluster = Cluster(
            id = ClusterID.named(homeLabel.id),
            centroid = LatLon(52.5200, 13.4050),
            memberIDs = listOf(entryId),
            label = homeLabel,
        )
        val assignment = PlaceAssignment(
            entryToCluster = mapOf(entryId to homeCluster.id),
            clusters = mapOf(homeCluster.id to homeCluster),
        )

        composeTestRule.setContent {
            HistoryScreen(
                entries = listOf(entry),
                places = assignment,
                placeLabels = listOf(homeLabel),
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = {},
                onDeleteEntry = {},
                onSavePlaceLabel = {},
                onNavigateBack = {},
            )
        }

        // Place name "Home" appears in the entry row
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffee at Home").assertIsDisplayed()
    }

    @Test
    fun entryDetailSheet_nameThisPlace_opensPlaceNamingSheet() {
        val entryId = UUID.randomUUID().toString()
        val entry = Entry(
            id = entryId,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = emptyList(),
            comment = "At mystery place",
            location = LocationSample(52.5200, 13.4050, 20.0, Instant.now(), LocationKind.FRESH),
            source = EntrySource.APP,
        )

        val unnamedCluster = Cluster(
            id = ClusterID.unnamed(entryId),
            centroid = LatLon(52.5200, 13.4050),
            memberIDs = listOf(entryId),
            label = null,
        )
        val assignment = PlaceAssignment(
            entryToCluster = mapOf(entryId to unnamedCluster.id),
            clusters = mapOf(unnamedCluster.id to unnamedCluster),
        )

        composeTestRule.setContent {
            HistoryScreen(
                entries = listOf(entry),
                places = assignment,
                placeLabels = emptyList(),
                tagPickerMode = TagPickerMode.GRADUAL,
                onTagPickerModeToggle = {},
                onSaveEntry = {},
                onDeleteEntry = {},
                onSavePlaceLabel = {},
                onNavigateBack = {},
            )
        }

        // Tap entry to open EntryDetailSheet
        composeTestRule.onNodeWithText("At mystery place").performClick()
        composeTestRule.onNodeWithText("Check-in").assertIsDisplayed()

        // Click "Name this place"
        composeTestRule.onNodeWithText("Name this place")
            .performScrollTo()
            .performClick()

        // PlaceNamingSheet is opened with "Name this place" header and suggestion chips
        composeTestRule.onNodeWithText("Another name").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Home")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Work")[0].assertIsDisplayed()
    }
}
