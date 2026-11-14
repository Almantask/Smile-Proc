package com.example.smileproc.ui.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassModalBottomSheet
import com.example.smileproc.ui.theme.LocalSmileProcColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.LatLon
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.components.FaceButton
import com.example.smileproc.ui.places.PlaceNamingSheet
import com.example.smileproc.ui.tags.BookmarkMomentToggle
import com.example.smileproc.ui.tags.ContextAndCustomTagsSection
import com.example.smileproc.ui.tags.TagPicker
import com.example.smileproc.ui.tags.notePromptStringRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSheet(
    entry: Entry,
    placeName: String?,
    tagPickerMode: TagPickerMode,
    onTagPickerModeToggle: () -> Unit,
    onSaveEntry: (Entry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onSavePlaceLabel: (PlaceLabel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    places: PlaceAssignment? = null,
    placeLabels: List<PlaceLabel> = emptyList(),
    allEntries: List<Entry> = emptyList(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    initialExtras: EntryExtras? = null,
    availableCustomTags: List<CustomTag> = emptyList(),
    onSaveExtras: ((EntryExtras) -> Unit)? = null,
    onCreateCustomTag: ((CustomTag) -> Unit)? = null,
    onDeleteCustomTag: ((String) -> Unit)? = null,
) {
    var mood by rememberSaveable(entry.id) { mutableStateOf(entry.mood) }
    var selectedTags by rememberSaveable(entry.id) { mutableStateOf(entry.tags) }
    var noteText by rememberSaveable(entry.id) { mutableStateOf(entry.comment.orEmpty()) }
    var selectedContextTags by rememberSaveable(entry.id) {
        mutableStateOf(initialExtras?.contextTags.orEmpty())
    }
    var selectedCustomTagIds by rememberSaveable(entry.id) {
        mutableStateOf(initialExtras?.customTagIds.orEmpty())
    }
    var bookmarked by rememberSaveable(entry.id) {
        mutableStateOf(initialExtras?.bookmarked == true)
    }
    var showDeleteConfirm by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showNamingSheet by rememberSaveable(entry.id) { mutableStateOf(false) }

    val locale = com.example.smileproc.AppLocale.effectiveLocale(
        androidx.compose.ui.platform.LocalContext.current,
    )
    val formatter = remember(entry.timeZoneID, locale) {
        val zone = try { ZoneId.of(entry.timeZoneID) } catch (_: Exception) { ZoneId.systemDefault() }
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
    }

    val smileColors = LocalSmileProcColors.current
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Title, Time and Bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.entry_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = formatter.format(entry.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val sourceText = if (entry.source == EntrySource.APP) {
                        stringResource(R.string.entry_source_app)
                    } else {
                        stringResource(R.string.entry_source_notification)
                    }
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BookmarkMomentToggle(
                    bookmarked = bookmarked,
                    onToggle = { bookmarked = !bookmarked },
                )
            }

            // Mood selector
            Text(
                text = stringResource(R.string.entry_mood),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Mood.entries.forEach { m ->
                    FaceButton(
                        mood = m,
                        onClick = { mood = m },
                        size = 56.dp,
                        selected = mood == m,
                    )
                }
            }

            // Tags
            Text(
                text = stringResource(R.string.entry_feelings),
                style = MaterialTheme.typography.titleMedium,
            )
            TagPicker(
                selectedTags = selectedTags,
                onTagsChanged = { selectedTags = it },
                mode = tagPickerMode,
                onModeToggle = onTagPickerModeToggle,
            )

            ContextAndCustomTagsSection(
                selectedContextTags = selectedContextTags,
                onContextTagsChanged = { selectedContextTags = it },
                availableCustomTags = availableCustomTags,
                selectedCustomTagIds = selectedCustomTagIds,
                onCustomTagIdsChanged = { selectedCustomTagIds = it },
                onCreateCustomTag = onCreateCustomTag,
                onDeleteCustomTag = onDeleteCustomTag,
            )

            // Note
            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 1000) noteText = it },
                label = { Text(stringResource(R.string.entry_note)) },
                placeholder = { Text(stringResource(notePromptStringRes(entry.id))) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            // Location / Place
            GlassCard(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (placeName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = placeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (entry.location != null) {
                            val currentLabel = places?.clusterForEntry(entry.id)?.label
                            val actionText = if (currentLabel != null) {
                                stringResource(R.string.places_rename)
                            } else {
                                stringResource(R.string.entry_name_place)
                            }
                            TextButton(
                                onClick = { showNamingSheet = true },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(actionText)
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.entry_no_location),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Save and Delete buttons
            val saveLabel = stringResource(R.string.action_save)
            Button(
                onClick = {
                    val trimmed = noteText.trim().ifEmpty { null }
                    val updated = entry.copy(
                        mood = mood,
                        tags = selectedTags,
                        comment = trimmed,
                        updatedAt = Instant.now(),
                    )
                    onSaveEntry(updated)
                    onSaveExtras?.invoke(
                        EntryExtras(
                            entryId = entry.id,
                            contextTags = selectedContextTags,
                            customTagIds = selectedCustomTagIds,
                            bookmarked = bookmarked,
                            updatedAt = Instant.now(),
                        ),
                    )
                    onDismiss()
                },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .semantics { contentDescription = saveLabel },
            ) {
                Text(saveLabel)
            }

            val deleteLabel = stringResource(R.string.entry_delete)
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .semantics { contentDescription = deleteLabel },
            ) {
                Text(deleteLabel)
            }
        }
    }

    if (showDeleteConfirm) {
        val cancelLabel = stringResource(R.string.action_cancel)
        val deleteActionLabel = stringResource(R.string.action_delete)
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = smileColors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.entry_delete_confirm_title)) },
            text = { Text(stringResource(R.string.entry_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteEntry(entry.id)
                        onDismiss()
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = deleteActionLabel },
                ) {
                    Text(
                        text = deleteActionLabel,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = cancelLabel },
                ) {
                    Text(cancelLabel)
                }
            },
        )
    }

    val entryLoc = entry.location
    if (showNamingSheet && entryLoc != null) {
        val currentCluster = places?.clusterForEntry(entry.id)
        val currentLabel = currentCluster?.label
        val clusterPoints = currentCluster?.let { c ->
            allEntries.filter { it.id in c.memberIDs && it.location != null }
                .map { LatLon(it.location!!.latitude, it.location!!.longitude) }
        } ?: listOf(LatLon(entryLoc.latitude, entryLoc.longitude))

        PlaceNamingSheet(
            centroid = currentCluster?.centroid ?: LatLon(entryLoc.latitude, entryLoc.longitude),
            existingLabels = placeLabels,
            initialLabel = currentLabel,
            clusterMemberPoints = clusterPoints,
            allEntries = allEntries,
            onSaveLabel = { label ->
                onSavePlaceLabel(label)
                showNamingSheet = false
            },
            onDismiss = { showNamingSheet = false },
        )
    }
}
