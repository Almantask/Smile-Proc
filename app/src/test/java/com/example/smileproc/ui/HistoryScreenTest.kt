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

    @Test
    fun searchAndSavedMomentsFiltersNarrowEntriesAndReset() {
        val e1 = Entry(
            id = "00000000-0000-4000-8000-000000000001",
            createdAt = Instant.parse("2026-11-10T09:00:00Z"),
            updatedAt = Instant.parse("2026-11-10T09:00:00Z"),
            timeZoneID = "UTC",
            mood = Mood.GOOD,
            tags = listOf("calm.peaceful"),
            comment = "Quiet lake morning",
            location = null,
            source = EntrySource.APP,
        )
        val e2 = Entry(
            id = "00000000-0000-4000-8000-000000000002",
            createdAt = Instant.parse("2026-11-10T15:00:00Z"),
            updatedAt = Instant.parse("2026-11-10T15:00:00Z"),
            timeZoneID = "UTC",
            mood = Mood.LOW,
            tags = listOf("tired.drained"),
            comment = "Heavy rain commute",
            location = null,
            source = EntrySource.APP,
        )
        val extras = mapOf(
            e1.id to com.example.smileproc.core.EntryExtras(
                entryId = e1.id,
                contextTags = listOf(com.example.smileproc.core.BuiltInContextTag.OUTDOORS.id),
                customTagIds = emptyList(),
                bookmarked = true,
                updatedAt = Instant.parse("2026-11-10T09:01:00Z"),
            ),
        )

        composeTestRule.setContent {
            HistoryScreen(
                entries = listOf(e1, e2),
                places = null,
                tagPickerMode = TagPickerMode.DETAILED,
                onTagPickerModeToggle = {},
                onSaveEntry = {},
                onDeleteEntry = {},
                onSavePlaceLabel = {},
                onNavigateBack = {},
                entryExtrasById = extras,
            )
        }

        // Filter to Saved moments -> only e1 remains
        composeTestRule.onNodeWithText("Saved moments").performClick()
        composeTestRule.onNodeWithText("Quiet lake morning").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Heavy rain commute").fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }

        // Search for unmatched text -> empty filter card + Reset filters button
        composeTestRule.onNodeWithText("Search notes, tags, or places").performTextInput("zzzunmatched")
        composeTestRule.onNodeWithText("No check-ins match these filters").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset filters").performClick()

        // Both entries reappear
        composeTestRule.onNodeWithText("Quiet lake morning").assertIsDisplayed()
        composeTestRule.onNodeWithText("Heavy rain commute").assertIsDisplayed()
    }
}
