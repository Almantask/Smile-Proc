package com.example.smileproc.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.LowMoodMonitor
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.components.FaceButton
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassPillButton
import com.example.smileproc.ui.components.MoodEntryCard
import com.example.smileproc.ui.components.SmileProcBrandHeader
import com.example.smileproc.ui.history.EntryDetailSheet
import com.example.smileproc.ui.places.PatternConfigCatalog
import com.example.smileproc.ui.places.getPlaceDisplayName
import com.example.smileproc.ui.support.SupportDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    entries: List<Entry>,
    places: PlaceAssignment?,
    lowRunDismissedEntryId: String?,
    tagPickerMode: TagPickerMode,
    onTagPickerModeToggle: () -> Unit,
    onSaveEntry: (Entry) -> Unit,
    onUpdateEntry: (Entry) -> Unit,
    onUpdateEntryDetails: (id: String, tags: List<String>, comment: String?) -> Unit = { _, _, _ -> },
    onDeleteEntry: (String) -> Unit,
    onSavePlaceLabel: (PlaceLabel) -> Unit,
    onDismissLowRun: (String?) -> Unit,
    onResetLowRunDismissed: () -> Unit,
    onCaptureLocationForEntry: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    placeLabels: List<PlaceLabel> = emptyList(),
    onNavigateToSupport: (() -> Unit)? = null,
    entryExtrasById: Map<String, EntryExtras> = emptyMap(),
    customTags: List<CustomTag> = emptyList(),
    onSaveEntryExtras: (EntryExtras) -> Unit = {},
    onCreateCustomTag: (CustomTag) -> Unit = {},
    onDeleteCustomTag: (String) -> Unit = {},
    recentNotificationEntryId: String? = null,
    openRecentFollowUp: Boolean = false,
    onClearRecentNotificationFollowUp: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentZone = remember { ZoneId.systemDefault() }
    val todayDate = remember { LocalDate.now(currentZone) }
    val customTagsById = remember(customTags) { customTags.associateBy { it.id } }

    var activeFollowUpEntry by remember { mutableStateOf<Entry?>(null) }
    var selectedDetailEntryId by remember { mutableStateOf<String?>(null) }
    var showSupportDialog by rememberSaveable { mutableStateOf(false) }

    val recentNotificationEntry = remember(entries, recentNotificationEntryId) {
        recentNotificationEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    }

    LaunchedEffect(openRecentFollowUp, recentNotificationEntry) {
        if (openRecentFollowUp && recentNotificationEntry != null) {
            activeFollowUpEntry = recentNotificationEntry
            onClearRecentNotificationFollowUp()
        } else if (openRecentFollowUp && recentNotificationEntryId == null) {
            onClearRecentNotificationFollowUp()
        }
    }

    // Today's entries sorted newest first
    val todayEntries = remember(entries, todayDate, currentZone) {
        entries.filter { entry ->
            val zone = try { ZoneId.of(entry.timeZoneID) } catch (_: Exception) { currentZone }
            entry.createdAt.atZone(zone).toLocalDate() == todayDate
        }.sortedByDescending { it.createdAt }
    }

    val config = remember(context) { PatternConfigCatalog.get(context) }
    val lowRunEvaluation = remember(entries, lowRunDismissedEntryId, config) {
        LowMoodMonitor.evaluate(entries, dismissedEntryId = lowRunDismissedEntryId, config = config)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmileProcBrandHeader()
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // "How are you right now?" question
            Text(
                text = stringResource(R.string.today_question),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Three large face buttons; highlight the most recent check-in mood today
            val latestTodayMood = todayEntries.firstOrNull()?.mood
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaceButton(
                    mood = Mood.LOW,
                    isLatestHighlight = latestTodayMood == Mood.LOW,
                    onClick = {
                        val newEntry = createNewEntry(Mood.LOW, currentZone)
                        onSaveEntry(newEntry)
                        onCaptureLocationForEntry(newEntry.id)
                        activeFollowUpEntry = newEntry
                    },
                )
                FaceButton(
                    mood = Mood.OKAY,
                    isLatestHighlight = latestTodayMood == Mood.OKAY,
                    onClick = {
                        val newEntry = createNewEntry(Mood.OKAY, currentZone)
                        onSaveEntry(newEntry)
                        onResetLowRunDismissed()
                        onCaptureLocationForEntry(newEntry.id)
                        activeFollowUpEntry = newEntry
                    },
                )
                FaceButton(
                    mood = Mood.GOOD,
                    isLatestHighlight = latestTodayMood == Mood.GOOD,
                    onClick = {
                        val newEntry = createNewEntry(Mood.GOOD, currentZone)
                        onSaveEntry(newEntry)
                        onResetLowRunDismissed()
                        onCaptureLocationForEntry(newEntry.id)
                        activeFollowUpEntry = newEntry
                    },
                )
            }

            // Quick follow-up pill for an untagged recent notification check-in
            val pendingBannerEntry = recentNotificationEntry?.takeIf { e ->
                val ex = entryExtrasById[e.id]
                e.tags.isEmpty() && e.comment.isNullOrBlank() && (ex == null || ex.isEmpty())
            }
            if (pendingBannerEntry != null) {
                GlassPillButton(
                    text = stringResource(R.string.today_followup_recent_banner),
                    onClick = {
                        activeFollowUpEntry = pendingBannerEntry
                        onClearRecentNotificationFollowUp()
                    },
                )
            }

            // Low-run card if armed
            if (lowRunEvaluation.shouldShowCard) {
                LowRunCard(
                    onSeeSupport = {
                        if (onNavigateToSupport != null) {
                            onNavigateToSupport()
                        } else {
                            showSupportDialog = true
                        }
                    },
                    onDismiss = {
                        val dismissedId = LowMoodMonitor.recordDismissal(entries)
                        onDismissLowRun(dismissedId)
                    },
                )
            }

            // Today's entries section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.today_entries_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (todayEntries.isEmpty()) {
                    GlassCard(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.today_entries_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    todayEntries.forEach { entry ->
                        MoodEntryCard(
                            entry = entry,
                            placeName = places?.let { getPlaceDisplayName(context, entry.id, it) },
                            maxTags = 3,
                            extras = entryExtrasById[entry.id],
                            customTagsById = customTagsById,
                            onClick = { selectedDetailEntryId = entry.id },
                        )
                    }
                }
            }

            // "All entries" button navigating to History
            val allEntriesLabel = stringResource(R.string.today_all_entries)
            GlassPillButton(
                text = allEntriesLabel,
                onClick = onNavigateToHistory,
            )
        }
    }

    // Follow-up sheet
    activeFollowUpEntry?.let { entry ->
        FollowUpSheet(
            entry = entry,
            tagPickerMode = tagPickerMode,
            onTagPickerModeToggle = onTagPickerModeToggle,
            onSaveDetails = { tags, comment ->
                val updated = entry.copy(
                    tags = tags,
                    comment = comment,
                    updatedAt = Instant.now(),
                )
                onUpdateEntry(updated)
                onUpdateEntryDetails(entry.id, tags, comment)
            },
            onDismiss = { activeFollowUpEntry = null },
            initialExtras = entryExtrasById[entry.id],
            availableCustomTags = customTags,
            onSaveExtras = onSaveEntryExtras,
            onCreateCustomTag = onCreateCustomTag,
            onDeleteCustomTag = onDeleteCustomTag,
        )
    }

    // Detail sheet
    val selectedDetailEntry = entries.find { it.id == selectedDetailEntryId }
    selectedDetailEntry?.let { entry ->
        EntryDetailSheet(
            entry = entry,
            placeName = places?.let { getPlaceDisplayName(context, entry.id, it) },
            tagPickerMode = tagPickerMode,
            onTagPickerModeToggle = onTagPickerModeToggle,
            onSaveEntry = { updated ->
                onUpdateEntry(updated)
                selectedDetailEntryId = null
            },
            onDeleteEntry = { id ->
                onDeleteEntry(id)
                selectedDetailEntryId = null
            },
            onSavePlaceLabel = onSavePlaceLabel,
            onDismiss = { selectedDetailEntryId = null },
            places = places,
            placeLabels = placeLabels,
            allEntries = entries,
            initialExtras = entryExtrasById[entry.id],
            availableCustomTags = customTags,
            onSaveExtras = onSaveEntryExtras,
            onCreateCustomTag = onCreateCustomTag,
            onDeleteCustomTag = onDeleteCustomTag,
        )
    }

    // Support dialog
    if (showSupportDialog) {
        SupportDialog(onDismiss = { showSupportDialog = false })
    }
}

private fun createNewEntry(mood: Mood, zoneId: ZoneId): Entry {
    val now = Instant.now()
    return Entry(
        id = UUID.randomUUID().toString(),
        createdAt = now,
        updatedAt = now,
        timeZoneID = zoneId.id,
        mood = mood,
        tags = emptyList(),
        comment = null,
        location = null,
        source = EntrySource.APP,
    )
}
