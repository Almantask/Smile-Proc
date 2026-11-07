package com.example.smileproc.ui.history

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.components.FaceIcon
import com.example.smileproc.ui.places.getPlaceDisplayName
import com.example.smileproc.ui.tags.resolveTagLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    entries: List<Entry>,
    places: PlaceAssignment?,
    tagPickerMode: TagPickerMode,
    onTagPickerModeToggle: () -> Unit,
    onSaveEntry: (Entry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onSavePlaceLabel: (PlaceLabel) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedEntryId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val backLabel = stringResource(R.string.action_back)

    val currentZone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(currentZone) }
    val yesterday = remember { today.minusDays(1) }

    val grouped = remember(entries) {
        entries.groupBy { entry ->
            val zone = try { ZoneId.of(entry.timeZoneID) } catch (_: Exception) { currentZone }
            entry.createdAt.atZone(zone).toLocalDate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = backLabel },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = backLabel,
                        )
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
    ) { paddingValues ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                grouped.forEach { (date, dayEntries) ->
                    item(key = "header_$date") {
                        val headerText = when (date) {
                            today -> stringResource(R.string.history_today)
                            yesterday -> stringResource(R.string.history_yesterday)
                            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)
                        }
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    items(dayEntries, key = { it.id }) { entry ->
                        EntryRowItem(
                            entry = entry,
                            placeName = getPlaceDisplayName(context, entry.id, places),
                            onClick = { selectedEntryId = entry.id },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    val activeEntry = selectedEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    if (activeEntry != null) {
        EntryDetailSheet(
            entry = activeEntry,
            placeName = getPlaceDisplayName(context, activeEntry.id, places),
            tagPickerMode = tagPickerMode,
            onTagPickerModeToggle = onTagPickerModeToggle,
            onSaveEntry = onSaveEntry,
            onDeleteEntry = onDeleteEntry,
            onSavePlaceLabel = onSavePlaceLabel,
            onDismiss = { selectedEntryId = null },
        )
    }
}

@Composable
fun EntryRowItem(
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FaceIcon(mood = entry.mood, size = 40.dp)

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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (placeName != null) {
                    Text(
                        text = placeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.tags.isNotEmpty()) {
                val tagNames = entry.tags.map { context.resolveTagLabel(it) }.joinToString(" · ")
                Text(
                    text = tagNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val comment = entry.comment
            if (!comment.isNullOrBlank()) {
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
