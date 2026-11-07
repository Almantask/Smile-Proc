package com.example.smileproc.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.LowMoodMonitor
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.components.FaceButton
import com.example.smileproc.ui.components.FaceIcon
import com.example.smileproc.ui.history.EntryDetailSheet
import com.example.smileproc.ui.places.PatternConfigCatalog
import com.example.smileproc.ui.places.getPlaceDisplayName
import com.example.smileproc.ui.support.SupportDialog
import com.example.smileproc.ui.tags.resolveTagLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
) {
    val context = LocalContext.current
    val currentZone = remember { ZoneId.systemDefault() }
    val todayDate = remember { LocalDate.now(currentZone) }

    var activeFollowUpEntry by remember { mutableStateOf<Entry?>(null) }
    var selectedDetailEntryId by remember { mutableStateOf<String?>(null) }
    var showSupportDialog by rememberSaveable { mutableStateOf(false) }

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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
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
            )

            // Three large face buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaceButton(
                    mood = Mood.LOW,
                    onClick = {
                        val newEntry = createNewEntry(Mood.LOW, currentZone)
                        onSaveEntry(newEntry)
                        onCaptureLocationForEntry(newEntry.id)
                        activeFollowUpEntry = newEntry
                    },
                )
                FaceButton(
                    mood = Mood.OKAY,
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
                    onClick = {
                        val newEntry = createNewEntry(Mood.GOOD, currentZone)
                        onSaveEntry(newEntry)
                        onResetLowRunDismissed()
                        onCaptureLocationForEntry(newEntry.id)
                        activeFollowUpEntry = newEntry
                    },
                )
            }

            // Low-run card if armed
            if (lowRunEvaluation.shouldShowCard) {
                LowRunCard(
                    onSeeSupport = { showSupportDialog = true },
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
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (todayEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.today_entries_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    todayEntries.forEach { entry ->
                        TodayEntryRow(
                            entry = entry,
                            placeName = places?.let { getPlaceDisplayName(context, entry.id, it) },
                            onClick = { selectedDetailEntryId = entry.id },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // "All entries" button navigating to History
            val allEntriesLabel = stringResource(R.string.today_all_entries)
            OutlinedButton(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = allEntriesLabel },
            ) {
                Text(allEntriesLabel)
            }
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
        )
    }

    // Support dialog
    if (showSupportDialog) {
        SupportDialog(onDismiss = { showSupportDialog = false })
    }
}

@Composable
private fun TodayEntryRow(
    entry: Entry,
    placeName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timeFormatter = remember(entry.timeZoneID) {
        val zone = try { ZoneId.of(entry.timeZoneID) } catch (_: Exception) { ZoneId.systemDefault() }
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FaceIcon(mood = entry.mood, size = 36.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeFormatter.format(entry.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (placeName != null) {
                        Text(
                            text = placeName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (entry.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        entry.tags.take(3).forEach { tagId ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = context.resolveTagLabel(tagId),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                }

                val comment = entry.comment
                if (!comment.isNullOrBlank()) {
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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
