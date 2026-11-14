package com.example.smileproc.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.BuiltInContextTag
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.HistoryFilter
import com.example.smileproc.core.InsightsEngine
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassTopBar
import com.example.smileproc.ui.components.MoodEntryCard
import com.example.smileproc.ui.places.getPlaceDisplayName
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.tags.coreTagChipColors
import com.example.smileproc.ui.tags.parseCoreColor
import com.example.smileproc.ui.tags.resolveAnyTagLabel
import com.example.smileproc.ui.tags.resolveContextTagLabel
import com.example.smileproc.ui.tags.resolveTagLabel
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.moodAccent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    placeLabels: List<PlaceLabel> = emptyList(),
    entryExtrasById: Map<String, EntryExtras> = emptyMap(),
    customTags: List<CustomTag> = emptyList(),
    onSaveEntryExtras: (EntryExtras) -> Unit = {},
    onCreateCustomTag: (CustomTag) -> Unit = {},
    onDeleteCustomTag: (String) -> Unit = {},
) {
    var selectedEntryId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMoods by remember { mutableStateOf(emptySet<Mood>()) }
    var bookmarkedOnly by remember { mutableStateOf(false) }
    var withNoteOnly by remember { mutableStateOf(false) }
    var selectedCoreIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedContextAndCustomIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedClusterIds by remember { mutableStateOf(emptySet<ClusterID>()) }
    var showMoreFilters by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val catalog = remember(context) { EmotionTagsCatalog.get(context) }
    val customTagsById = remember(customTags) { customTags.associateBy { it.id } }

    val activeFilter = remember(
        searchQuery,
        selectedMoods,
        bookmarkedOnly,
        withNoteOnly,
        selectedCoreIds,
        selectedContextAndCustomIds,
        selectedClusterIds,
    ) {
        HistoryFilter(
            query = searchQuery,
            moods = selectedMoods,
            bookmarkedOnly = bookmarkedOnly,
            withNoteOnly = withNoteOnly,
            coreTagIds = selectedCoreIds,
            contextOrCustomTagIds = selectedContextAndCustomIds,
            clusterIds = selectedClusterIds,
        )
    }

    val filteredEntries = remember(entries, activeFilter, entryExtrasById, customTagsById, places, catalog) {
        InsightsEngine.filterEntries(
            entries = entries,
            filter = activeFilter,
            extrasByEntryId = entryExtrasById,
            customTagsById = customTagsById,
            placeAssignment = places,
            emotionTags = catalog,
            tagLabelResolver = { id -> context.resolveAnyTagLabel(id, customTagsById) },
        )
    }

    val currentZone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(currentZone) }
    val yesterday = remember { today.minusDays(1) }

    val grouped = remember(filteredEntries) {
        filteredEntries.groupBy { entry ->
            val zone = try { ZoneId.of(entry.timeZoneID) } catch (_: Exception) { currentZone }
            entry.createdAt.atZone(zone).toLocalDate()
        }
    }

    val namedClusters = remember(places) {
        places?.clusters?.values
            ?.filter { it.label != null }
            ?.sortedBy { it.label!!.name }
            .orEmpty()
    }

    fun clearAllFilters() {
        searchQuery = ""
        selectedMoods = emptySet()
        bookmarkedOnly = false
        withNoteOnly = false
        selectedCoreIds = emptySet()
        selectedContextAndCustomIds = emptySet()
        selectedClusterIds = emptySet()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.history_title),
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier.fillMaxSize(),
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
                GlassCard(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
            ) {
                // In-memory search + filter controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val clearSearchDesc = stringResource(R.string.history_search_clear)
                    val toggleFiltersDesc = stringResource(R.string.history_filter_toggle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.history_search_hint)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.semantics { contentDescription = clearSearchDesc },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = clearSearchDesc,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            singleLine = true,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                        )

                        val hasExpandedFilters = selectedCoreIds.isNotEmpty() ||
                            selectedContextAndCustomIds.isNotEmpty() ||
                            selectedClusterIds.isNotEmpty()
                        IconButton(
                            onClick = { showMoreFilters = !showMoreFilters },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = toggleFiltersDesc },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filter),
                                contentDescription = toggleFiltersDesc,
                                tint = if (showMoreFilters || hasExpandedFilters) {
                                    smileColors.brandAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val savedLabel = stringResource(R.string.history_filter_saved_moments)
                        FilterChip(
                            selected = bookmarkedOnly,
                            onClick = { bookmarkedOnly = !bookmarkedOnly },
                            label = { Text(savedLabel) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (bookmarkedOnly) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = smileColors.chipFill,
                                selectedContainerColor = smileColors.brandAccent.copy(alpha = 0.22f),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = bookmarkedOnly,
                                borderColor = smileColors.chipBorder,
                                selectedBorderColor = smileColors.brandAccent,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )

                        FilterChip(
                            selected = withNoteOnly,
                            onClick = { withNoteOnly = !withNoteOnly },
                            label = { Text(stringResource(R.string.history_filter_with_note)) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = smileColors.chipFill,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = withNoteOnly,
                                borderColor = smileColors.chipBorder,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )

                        Mood.entries.forEach { m ->
                            val isSelected = m in selectedMoods
                            val moodLabel = when (m) {
                                Mood.LOW -> stringResource(R.string.face_low)
                                Mood.OKAY -> stringResource(R.string.face_okay)
                                Mood.GOOD -> stringResource(R.string.face_good)
                            }
                            val accent = smileColors.moodAccent(m)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMoods = if (isSelected) selectedMoods - m else selectedMoods + m
                                },
                                label = { Text(moodLabel) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = smileColors.chipFill,
                                    selectedContainerColor = accent.copy(alpha = 0.24f),
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = smileColors.chipBorder,
                                    selectedBorderColor = accent,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = showMoreFilters) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                catalog.cores.forEach { core ->
                                    val isSelected = core.id in selectedCoreIds
                                    val cColor = parseCoreColor(core.color)
                                    val chipColors = coreTagChipColors(
                                        coreColor = cColor,
                                        isDark = smileColors.isDark,
                                        emphasized = isSelected,
                                    )
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCoreIds = if (isSelected) {
                                                selectedCoreIds - core.id
                                            } else {
                                                selectedCoreIds + core.id
                                            }
                                        },
                                        label = { Text(context.resolveTagLabel(core.id)) },
                                        shape = CircleShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = smileColors.chipFill,
                                            selectedContainerColor = chipColors.container,
                                            selectedLabelColor = chipColors.content,
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = smileColors.chipBorder,
                                            selectedBorderColor = chipColors.border,
                                        ),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                BuiltInContextTag.entries.forEach { tag ->
                                    val isSelected = tag.id in selectedContextAndCustomIds
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedContextAndCustomIds = if (isSelected) {
                                                selectedContextAndCustomIds - tag.id
                                            } else {
                                                selectedContextAndCustomIds + tag.id
                                            }
                                        },
                                        label = { Text(context.resolveContextTagLabel(tag.id)) },
                                        shape = CircleShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = smileColors.chipFill,
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = smileColors.chipBorder,
                                        ),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                                customTags.forEach { customTag ->
                                    val isSelected = customTag.id in selectedContextAndCustomIds
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedContextAndCustomIds = if (isSelected) {
                                                selectedContextAndCustomIds - customTag.id
                                            } else {
                                                selectedContextAndCustomIds + customTag.id
                                            }
                                        },
                                        label = { Text(customTag.label) },
                                        shape = CircleShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = smileColors.chipFill,
                                            selectedContainerColor = smileColors.brandAccent.copy(alpha = 0.22f),
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = smileColors.chipBorder,
                                            selectedBorderColor = smileColors.brandAccent,
                                        ),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }

                            if (namedClusters.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    namedClusters.forEach { cluster ->
                                        val isSelected = cluster.id in selectedClusterIds
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedClusterIds = if (isSelected) {
                                                    selectedClusterIds - cluster.id
                                                } else {
                                                    selectedClusterIds + cluster.id
                                                }
                                            },
                                            label = { Text(cluster.label!!.name) },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = smileColors.chipFill,
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = smileColors.chipBorder,
                                            ),
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredEntries.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GlassCard(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.history_filter_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.history_filter_empty_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(
                                    onClick = { clearAllFilters() },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.history_filter_clear))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = paddingValues.calculateBottomPadding() + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        grouped.forEach { (date, dayEntries) ->
                            item(key = "header_$date") {
                                val locale = com.example.smileproc.AppLocale.effectiveLocale(
                                    androidx.compose.ui.platform.LocalContext.current,
                                )
                                val headerText = when (date) {
                                    today -> stringResource(R.string.history_today)
                                    yesterday -> stringResource(R.string.history_yesterday)
                                    else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                        .withLocale(locale)
                                        .format(date)
                                }
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 2.dp),
                                )
                            }

                            items(dayEntries, key = { it.id }) { entry ->
                                EntryRowItem(
                                    entry = entry,
                                    placeName = getPlaceDisplayName(context, entry.id, places),
                                    extras = entryExtrasById[entry.id],
                                    customTagsById = customTagsById,
                                    onClick = { selectedEntryId = entry.id },
                                )
                            }
                        }
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
            places = places,
            placeLabels = placeLabels,
            allEntries = entries,
            initialExtras = entryExtrasById[activeEntry.id],
            availableCustomTags = customTags,
            onSaveExtras = onSaveEntryExtras,
            onCreateCustomTag = onCreateCustomTag,
            onDeleteCustomTag = onDeleteCustomTag,
        )
    }
}

@Composable
fun EntryRowItem(
    entry: Entry,
    placeName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extras: EntryExtras? = null,
    customTagsById: Map<String, CustomTag> = emptyMap(),
) {
    MoodEntryCard(
        entry = entry,
        placeName = placeName,
        maxTags = Int.MAX_VALUE,
        onClick = onClick,
        modifier = modifier,
        extras = extras,
        customTagsById = customTagsById,
    )
}
