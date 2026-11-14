package com.example.smileproc.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WeeklyCardBuilder
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassTopBar
import com.example.smileproc.ui.components.MoodEntryCard
import com.example.smileproc.ui.history.EntryDetailSheet
import com.example.smileproc.ui.places.PatternConfigCatalog
import com.example.smileproc.ui.places.PlaceNamingSheet
import com.example.smileproc.ui.places.getPlaceDisplayName
import com.example.smileproc.ui.places.resolvePlaces
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.SmileProcColors
import com.example.smileproc.ui.theme.moodAccent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.hypot
import kotlin.math.min

private enum class ReflectionSubtab {
    WEEK,
    MONTH,
    INSIGHTS,
}

data class PlotPoint(
    val entryId: String,
    val mood: Mood,
    val dayIndex: Int,
    val minutesOfDay: Int,
    val yFractionFromBottom: Float,
)

object WeekPlotMath {
    const val MINUTES_PER_DAY = 24 * 60
    const val GRID_ROWS = 12 // 2 hours per row
    const val GRID_COLS = 7  // Monday..Sunday

    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun defaultInitialWeekStart(
        entries: List<Entry>,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    ): LocalDate {
        val currentMonday = mondayOf(today)
        val currentSunday = currentMonday.plusDays(6)
        if (entries.isEmpty() || entries.any { it.localDate() in currentMonday..currentSunday }) {
            return currentMonday
        }
        val latestEntry = entries.maxByOrNull { it.createdAt } ?: return currentMonday
        return mondayOf(latestEntry.localDate())
    }

    fun entriesForWeek(
        entries: List<Entry>,
        weekStartMonday: LocalDate,
    ): List<Entry> {
        val weekEndSunday = weekStartMonday.plusDays(6)
        return entries
            .filter { it.localDate() in weekStartMonday..weekEndSunday }
            .sortedWith(compareBy<Entry> { it.createdAt }.thenBy { it.id })
    }

    fun pointForEntry(entry: Entry): PlotPoint {
        val zdt = entry.zonedDateTime()
        val dayIndex = (entry.isoWeekday() - 1).coerceIn(0, 6)
        val minutesOfDay = (zdt.hour * 60 + zdt.minute).coerceIn(0, MINUTES_PER_DAY - 1)
        val yFractionFromBottom = (minutesOfDay.toFloat() / MINUTES_PER_DAY.toFloat()).coerceIn(0f, 1f)
        return PlotPoint(
            entryId = entry.id,
            mood = entry.mood,
            dayIndex = dayIndex,
            minutesOfDay = minutesOfDay,
            yFractionFromBottom = yFractionFromBottom,
        )
    }

    fun hourRangeForWeek(weekEntries: List<Entry>): Pair<Int, Int>? {
        if (weekEntries.isEmpty()) return null
        val sortedByTime = weekEntries
            .map { it.zonedDateTime() }
            .sortedBy { it.hour * 60 + it.minute }
        val startHour = sortedByTime.first().hour
        val endZdt = sortedByTime.last()
        val rawEndHour = if (endZdt.minute > 0 && endZdt.hour < 23) endZdt.hour + 1 else endZdt.hour
        val endHour = if (rawEndHour <= startHour) (startHour + 1).coerceAtMost(23) else rawEndHour
        return startHour to endHour
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeekScreen(
    entries: List<Entry>,
    initialWeekStart: LocalDate? = null,
    modifier: Modifier = Modifier,
    places: PlaceAssignment? = null,
    placeLabels: List<PlaceLabel> = emptyList(),
    weeklyCards: List<WeeklyCard> = emptyList(),
    weeklyCardTime: WeeklyCardTime = WeeklyCardTime.DEFAULT,
    entryExtrasById: Map<String, EntryExtras> = emptyMap(),
    customTags: List<CustomTag> = emptyList(),
    tagPickerMode: TagPickerMode = TagPickerMode.DETAILED,
    onTagPickerModeToggle: () -> Unit = {},
    onSaveEntry: (Entry) -> Unit = {},
    onDeleteEntry: (String) -> Unit = {},
    onSavePlaceLabel: (PlaceLabel) -> Unit = {},
    onSaveEntryExtras: (EntryExtras) -> Unit = {},
    onCreateCustomTag: (CustomTag) -> Unit = {},
    onDeleteCustomTag: (String) -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val resolvedPlaces = places ?: remember(context, entries, placeLabels) {
        resolvePlaces(context, entries, placeLabels)
    }
    val customTagsById = remember(customTags) { customTags.associateBy { it.id } }

    var activeSubtab by rememberSaveable { mutableStateOf(ReflectionSubtab.WEEK) }
    var selectedPlotEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightedPatternId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var namingClusterId by rememberSaveable { mutableStateOf<String?>(null) }

    val computedInitialMonday = remember(entries, initialWeekStart) {
        initialWeekStart?.let(WeekPlotMath::mondayOf)
            ?: WeekPlotMath.defaultInitialWeekStart(entries)
    }

    var userNavigated by rememberSaveable { mutableStateOf(false) }
    var selectedWeekEpochDay by rememberSaveable {
        mutableLongStateOf(computedInitialMonday.toEpochDay())
    }

    LaunchedEffect(computedInitialMonday, userNavigated) {
        if (!userNavigated) {
            selectedWeekEpochDay = computedInitialMonday.toEpochDay()
        }
    }

    val weekStart = LocalDate.ofEpochDay(selectedWeekEpochDay)
    val weekEnd = weekStart.plusDays(7)
    val weekEntries = remember(entries, weekStart) {
        WeekPlotMath.entriesForWeek(entries, weekStart)
    }
    val plotPoints = remember(weekEntries) {
        weekEntries.map(WeekPlotMath::pointForEntry)
    }

    val weekCard = remember(context, entries, placeLabels, weekStart, weeklyCards, weeklyCardTime, weekEntries) {
        if (weekEntries.isEmpty()) {
            null
        } else {
            resolveWeeklyCardForWeek(
                context = context,
                weekStart = weekStart,
                entries = entries,
                placeLabels = placeLabels,
                weeklyCards = weeklyCards,
                weeklyCardTime = weeklyCardTime,
            )
        }
    }

    val highlightedItem = remember(weekCard, highlightedPatternId) {
        highlightedPatternId?.let { id ->
            weekCard?.items?.firstOrNull { it.patternID == id }
        }
    }
    val highlightedEntryIds = remember(highlightedItem) {
        highlightedItem?.pattern?.supportingEntryIDs?.toSet().orEmpty()
    }
    val highlightedInWeekCount = remember(weekEntries, highlightedEntryIds) {
        if (highlightedEntryIds.isEmpty()) 0 else weekEntries.count { it.id in highlightedEntryIds }
    }

    val selectedPlotEntry = remember(weekEntries, selectedPlotEntryId) {
        selectedPlotEntryId?.let { id -> weekEntries.firstOrNull { it.id == id } }
    }

    val dateFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val startStr = dateFormatter.format(weekStart)
    val endStr = dateFormatter.format(weekEnd)

    val prevLabel = stringResource(R.string.week_previous_action)
    val nextLabel = stringResource(R.string.week_next_action)
    val goPreviousWeek = {
        userNavigated = true
        selectedWeekEpochDay = weekStart.minusWeeks(1).toEpochDay()
        selectedPlotEntryId = null
        highlightedPatternId = null
    }
    val goNextWeek = {
        userNavigated = true
        selectedWeekEpochDay = weekStart.plusWeeks(1).toEpochDay()
        selectedPlotEntryId = null
        highlightedPatternId = null
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassTopBar(title = stringResource(R.string.week_title))
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Segmented subtab selector: Week · Month · Insights
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ReflectionSubtab.WEEK to R.string.week_subtab_week,
                    ReflectionSubtab.MONTH to R.string.week_subtab_month,
                    ReflectionSubtab.INSIGHTS to R.string.week_subtab_insights,
                ).forEach { (tab, labelRes) ->
                    val isSelected = activeSubtab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { activeSubtab = tab },
                        label = { Text(stringResource(labelRes)) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = smileColors.chipFill,
                            selectedContainerColor = smileColors.brandAccent.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
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

            when (activeSubtab) {
                ReflectionSubtab.WEEK -> {
                    // Period date range header with accessible week navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.week_period, startStr, endStr),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("week_period_header"),
                        )
                        WeekNavButton(
                            contentDescription = prevLabel,
                            mirrorChevron = true,
                            onClick = goPreviousWeek,
                        )
                        WeekNavButton(
                            contentDescription = nextLabel,
                            mirrorChevron = false,
                            onClick = goNextWeek,
                        )
                    }

                    if (highlightedEntryIds.isNotEmpty()) {
                        val badgeCount = if (highlightedInWeekCount > 0) highlightedInWeekCount else highlightedEntryIds.size
                        GlassCard(
                            highlighted = true,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.week_highlighting_count,
                                        badgeCount,
                                        badgeCount,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { highlightedPatternId = null },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.week_highlight_clear))
                                }
                            }
                        }
                    }

                    // Day & Time Patterns card
                    GlassCard(
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.week_day_time_patterns),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            DayTimePlot(
                                points = plotPoints,
                                startStr = startStr,
                                endStr = endStr,
                                smileColors = smileColors,
                                selectedEntryId = selectedPlotEntryId,
                                highlightedEntryIds = highlightedEntryIds,
                                onSelectPoint = { tappedId ->
                                    selectedPlotEntryId = if (tappedId == selectedPlotEntryId) null else tappedId
                                },
                                onPreviousWeek = goPreviousWeek,
                                onNextWeek = goNextWeek,
                            )

                            if (plotPoints.isNotEmpty() && selectedPlotEntry == null) {
                                Text(
                                    text = stringResource(R.string.week_tap_dot_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (selectedPlotEntry != null) {
                        GlassCard(
                            highlighted = true,
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MoodEntryCard(
                                    entry = selectedPlotEntry,
                                    placeName = getPlaceDisplayName(context, selectedPlotEntry.id, resolvedPlaces),
                                    extras = entryExtrasById[selectedPlotEntry.id],
                                    customTagsById = customTagsById,
                                    onClick = { detailEntryId = selectedPlotEntry.id },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { selectedPlotEntryId = null },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text(stringResource(R.string.action_close))
                                    }
                                    OutlinedButton(
                                        onClick = { detailEntryId = selectedPlotEntry.id },
                                        shape = CircleShape,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text(stringResource(R.string.week_entry_preview_open))
                                    }
                                }
                            }
                        }
                    }

                    // Summary, Legend, and Tracked Footer card
                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        WeekPlotFooter(weekEntries = weekEntries)
                    }

                    // Weekly Card & gentle pattern notes for this week
                    WeeklyCardNotesSection(
                        weekCard = weekCard,
                        weekEntries = weekEntries,
                        placeLabels = placeLabels,
                        places = resolvedPlaces,
                        highlightedPatternId = highlightedPatternId,
                        onToggleHighlight = { patternId ->
                            highlightedPatternId = if (highlightedPatternId == patternId) null else patternId
                        },
                        onNameUnnamedCluster = { clusterId ->
                            namingClusterId = clusterId
                        },
                        onNavigateToSupport = onNavigateToSupport,
                    )
                }

                ReflectionSubtab.MONTH -> {
                    MonthlyRhythmSection(
                        entries = entries,
                        places = resolvedPlaces,
                        entryExtrasById = entryExtrasById,
                        customTagsById = customTagsById,
                        onSelectEntry = { entry -> detailEntryId = entry.id },
                    )
                }

                ReflectionSubtab.INSIGHTS -> {
                    BreakdownsInsightsSection(
                        entries = entries,
                        places = resolvedPlaces,
                        entryExtrasById = entryExtrasById,
                        customTags = customTags,
                        onNameUnnamedCluster = { clusterId -> namingClusterId = clusterId },
                    )
                }
            }
        }
    }

    val entryToEdit = detailEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    if (entryToEdit != null) {
        EntryDetailSheet(
            entry = entryToEdit,
            placeName = getPlaceDisplayName(context, entryToEdit.id, resolvedPlaces),
            tagPickerMode = tagPickerMode,
            onTagPickerModeToggle = onTagPickerModeToggle,
            onSaveEntry = { updated ->
                onSaveEntry(updated)
                detailEntryId = null
            },
            onDeleteEntry = { deletedId ->
                onDeleteEntry(deletedId)
                if (selectedPlotEntryId == deletedId) {
                    selectedPlotEntryId = null
                }
                detailEntryId = null
            },
            onSavePlaceLabel = onSavePlaceLabel,
            onDismiss = { detailEntryId = null },
            places = resolvedPlaces,
            placeLabels = placeLabels,
            allEntries = entries,
            initialExtras = entryExtrasById[entryToEdit.id],
            availableCustomTags = customTags,
            onSaveExtras = onSaveEntryExtras,
            onCreateCustomTag = onCreateCustomTag,
            onDeleteCustomTag = onDeleteCustomTag,
        )
    }

    if (namingClusterId != null) {
        val targetCluster = resolvedPlaces.clusters[ClusterID(namingClusterId!!)]
        if (targetCluster != null) {
            PlaceNamingSheet(
                centroid = targetCluster.centroid,
                existingLabels = placeLabels,
                allEntries = entries,
                onDismiss = { namingClusterId = null },
                onSaveLabel = { label ->
                    onSavePlaceLabel(label)
                    namingClusterId = null
                },
            )
        }
    }
}

@Composable
private fun WeeklyCardNotesSection(
    weekCard: WeeklyCard?,
    weekEntries: List<Entry>,
    placeLabels: List<PlaceLabel>,
    places: PlaceAssignment,
    highlightedPatternId: String?,
    onToggleHighlight: (String) -> Unit,
    onNameUnnamedCluster: (String) -> Unit,
    onNavigateToSupport: () -> Unit,
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val library = remember(context) { PatternLibraryCatalog.get(context) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.week_insights_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (weekCard != null && weekCard.isLowWeek && weekEntries.isNotEmpty()) {
            GlassCard(
                highlighted = true,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.week_low_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.week_low_card_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onNavigateToSupport,
                        shape = CircleShape,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.week_low_card_cta))
                    }
                }
            }
        }

        if (weekCard == null || weekCard.items.isEmpty()) {
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.week_card_no_entries_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val weekEntryIds = remember(weekEntries) { weekEntries.mapTo(hashSetOf()) { it.id } }
            weekCard.items.forEach { item ->
                val rendered = remember(context, item, library, placeLabels, places) {
                    WeeklyCardRenderer.renderItem(
                        context = context,
                        item = item,
                        library = library,
                        labels = placeLabels,
                        places = places,
                    )
                }
                val isHighlighted = highlightedPatternId == item.patternID
                val hasPlotMatches = item.pattern.supportingEntryIDs.any { it in weekEntryIds }

                GlassCard(
                    highlighted = isHighlighted,
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClick = { onToggleHighlight(item.patternID) },
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = rendered.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (rendered.isEarlyHint) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(smileColors.chipFill)
                                        .border(1.dp, smileColors.chipBorder, CircleShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.week_pattern_early_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Text(
                            text = rendered.observation,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = rendered.common,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = rendered.tip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = rendered.question,
                            style = MaterialTheme.typography.bodySmall,
                            color = smileColors.brandAccent,
                        )

                        if (hasPlotMatches) {
                            Text(
                                text = stringResource(R.string.week_pattern_tap_highlight),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (rendered.unnamedClusterId != null) {
                            OutlinedButton(
                                onClick = { onNameUnnamedCluster(rendered.unnamedClusterId) },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.week_pattern_name_place))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveWeeklyCardForWeek(
    context: android.content.Context,
    weekStart: LocalDate,
    entries: List<Entry>,
    placeLabels: List<PlaceLabel>,
    weeklyCards: List<WeeklyCard>,
    weeklyCardTime: WeeklyCardTime,
): WeeklyCard? {
    return try {
        val zone = ZoneId.systemDefault()
        val weekEndCutoff = ZonedDateTime.of(weekStart.plusDays(7), LocalTime.MIN, zone).toInstant()
        val periodStartFloor = ZonedDateTime.of(weekStart.minusDays(1), LocalTime.MIN, zone).toInstant()

        val entriesUpToWeek = entries.filter { it.createdAt.isBefore(weekEndCutoff) }
        if (entriesUpToWeek.isEmpty()) return null

        val matchingSaved = weeklyCards.firstOrNull { card ->
            !card.periodEnd.isBefore(periodStartFloor) && !card.periodEnd.isAfter(weekEndCutoff)
        }

        val config = PatternConfigCatalog.get(context)
        val emotionTags = EmotionTagsCatalog.get(context)
        val library = PatternLibraryCatalog.get(context)
        val built = WeeklyCardBuilder.build(
            entries = entriesUpToWeek,
            labels = placeLabels,
            now = weekEndCutoff,
            zone = zone,
            config = config,
            emotionTags = emotionTags,
            library = library,
            cardTime = weeklyCardTime,
        ).card

        if (matchingSaved != null && matchingSaved.entryCount == built.entryCount && matchingSaved.periodStart == built.periodStart) {
            matchingSaved
        } else {
            built
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun WeekNavButton(
    contentDescription: String,
    mirrorChevron: Boolean,
    onClick: () -> Unit,
) {
    val smileColors = LocalSmileProcColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(smileColors.chipFill)
                .border(1.dp, smileColors.chipBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(18.dp)
                    .then(
                        if (mirrorChevron) Modifier.graphicsLayer { scaleX = -1f } else Modifier,
                    ),
            )
        }
    }
}

@Composable
private fun DayTimePlot(
    points: List<PlotPoint>,
    startStr: String,
    endStr: String,
    smileColors: SmileProcColors,
    selectedEntryId: String?,
    highlightedEntryIds: Set<String>,
    onSelectPoint: (String?) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val plotCd = stringResource(R.string.week_plot_cd, startStr, endStr, points.size)
    val prevLabel = stringResource(R.string.week_previous_action)
    val nextLabel = stringResource(R.string.week_next_action)

    val yAxisLabels = listOf(
        stringResource(R.string.week_hour_12am),
        stringResource(R.string.week_hour_8pm),
        stringResource(R.string.week_hour_4pm),
        stringResource(R.string.week_hour_12pm),
        stringResource(R.string.week_hour_8am),
        stringResource(R.string.week_hour_4am),
        stringResource(R.string.week_hour_12am),
    )

    val weekdayLetters = listOf(
        stringResource(R.string.weekday_1).take(1),
        stringResource(R.string.weekday_2).take(1),
        stringResource(R.string.weekday_3).take(1),
        stringResource(R.string.weekday_4).take(1),
        stringResource(R.string.weekday_5).take(1),
        stringResource(R.string.weekday_6).take(1),
        stringResource(R.string.weekday_7).take(1),
    )

    val gridHeight = 252.dp
    val gridBackgroundColor = smileColors.glassBottom.copy(alpha = if (smileColors.isDark) 0.45f else 0.55f)
    val smileyBorderColor = smileColors.canvasBase
    val hasHighlight = highlightedEntryIds.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("week_plot")
            .semantics {
                contentDescription = plotCd
                customActions = listOf(
                    CustomAccessibilityAction(prevLabel) {
                        onPreviousWeek()
                        true
                    },
                    CustomAccessibilityAction(nextLabel) {
                        onNextWeek()
                        true
                    },
                )
            }
            .pointerInput(onPreviousWeek, onNextWeek) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        when {
                            dragAccumulator < -swipeThresholdPx -> onNextWeek()
                            dragAccumulator > swipeThresholdPx -> onPreviousWeek()
                        }
                        dragAccumulator = 0f
                    },
                    onDragCancel = { dragAccumulator = 0f },
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Y-axis time labels (12 AM at top down to 12 AM at bottom)
            Column(
                modifier = Modifier
                    .height(gridHeight)
                    .widthIn(min = 42.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yAxisLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Right column: Grid Canvas + X-axis weekday labels aligned to the same width
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(points, canvasSize, selectedEntryId) {
                            detectTapGestures { tapOffset ->
                                if (canvasSize.width <= 0 || canvasSize.height <= 0 || points.isEmpty()) {
                                    return@detectTapGestures
                                }
                                val width = canvasSize.width.toFloat()
                                val height = canvasSize.height.toFloat()
                                val colWidth = width / WeekPlotMath.GRID_COLS
                                val smileyRadius = min(colWidth * 0.34f, 15.dp.toPx())
                                val hitRadius = maxOf(smileyRadius * 2.1f, 24.dp.toPx())

                                val nearest = points
                                    .map { pt ->
                                        val cx = (pt.dayIndex + 0.5f) * colWidth
                                        val cy = (height * (1f - pt.yFractionFromBottom))
                                            .coerceIn(smileyRadius, height - smileyRadius)
                                        pt to hypot(tapOffset.x - cx, tapOffset.y - cy)
                                    }
                                    .filter { it.second <= hitRadius }
                                    .minByOrNull { it.second }

                                if (nearest != null) {
                                    onSelectPoint(nearest.first.entryId)
                                } else if (selectedEntryId != null) {
                                    onSelectPoint(null)
                                }
                            }
                        },
                ) {
                    val width = size.width
                    val height = size.height
                    val colWidth = width / WeekPlotMath.GRID_COLS
                    val rowHeight = height / WeekPlotMath.GRID_ROWS
                    val strokePx = 1.dp.toPx()

                    // Soft aurora plot wash
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                smileColors.brandAccent.copy(alpha = if (smileColors.isDark) 0.14f else 0.10f),
                                smileColors.glowSecondary.copy(alpha = if (smileColors.isDark) 0.10f else 0.08f),
                                gridBackgroundColor,
                            ),
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    )

                    // Gentle vertical column capsules (Mon..Sun)
                    val colPad = 3.dp.toPx()
                    for (col in 0 until WeekPlotMath.GRID_COLS) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = if (smileColors.isDark) 0.03f else 0.34f),
                            topLeft = Offset(col * colWidth + colPad, colPad),
                            size = Size((colWidth - colPad * 2).coerceAtLeast(1f), (height - colPad * 2).coerceAtLeast(1f)),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        )
                    }

                    // Soft dashed 2-hour horizontal guide rules
                    val softLine = smileColors.pillBorder.copy(alpha = 0.28f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                    for (row in 1 until WeekPlotMath.GRID_ROWS) {
                        val y = row * rowHeight
                        drawLine(
                            color = softLine,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = strokePx,
                            pathEffect = if (row % 2 == 1) dashEffect else null,
                        )
                    }

                    // Chronological coordinates
                    val smileyRadius = min(colWidth * 0.34f, 15.dp.toPx())
                    val coords = points.map { point ->
                        val cx = (point.dayIndex + 0.5f) * colWidth
                        val rawCy = height * (1f - point.yFractionFromBottom)
                        val cy = rawCy.coerceIn(smileyRadius, height - smileyRadius)
                        point to Offset(cx, cy)
                    }

                    // Subtle flowing chronological mood ribbon
                    if (coords.size >= 2) {
                        val ribbonPath = Path().apply {
                            moveTo(coords.first().second.x, coords.first().second.y)
                            for (i in 1 until coords.size) {
                                val prev = coords[i - 1].second
                                val curr = coords[i].second
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }
                        drawPath(
                            path = ribbonPath,
                            color = smileColors.brandAccent.copy(
                                alpha = if (hasHighlight) 0.14f else if (smileColors.isDark) 0.36f else 0.30f,
                            ),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }

                    // Plotted check-in smileys with ambient glow halos & highlight/selection rings
                    for ((point, ptCenter) in coords) {
                        val isHighlightedPoint = point.entryId in highlightedEntryIds
                        val isSelectedPoint = point.entryId == selectedEntryId
                        val isDimmed = hasHighlight && !isHighlightedPoint && !isSelectedPoint
                        val alpha = if (isDimmed) 0.28f else 1f
                        val moodAccent = smileColors.moodAccent(point.mood)
                        val haloRadius = smileyRadius * (if (isHighlightedPoint || isSelectedPoint) 2.25f else 1.95f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(moodAccent.copy(alpha = 0.42f * alpha), Color.Transparent),
                                center = ptCenter,
                                radius = haloRadius,
                            ),
                            radius = haloRadius,
                            center = ptCenter,
                        )
                        if (isSelectedPoint || isHighlightedPoint) {
                            drawCircle(
                                color = smileColors.brandAccent,
                                radius = smileyRadius * 1.38f,
                                center = ptCenter,
                                style = Stroke(width = 2.5.dp.toPx()),
                            )
                        }
                        drawPlotSmiley(
                            mood = point.mood,
                            center = ptCenter,
                            radius = smileyRadius,
                            borderColor = smileyBorderColor,
                            smileColors = smileColors,
                            alpha = alpha,
                        )
                    }
                }

                // X-axis weekday letters (M T W T F S S)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    weekdayLetters.forEach { letter ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekPlotFooter(
    weekEntries: List<Entry>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hourRange = remember(weekEntries) {
        WeekPlotMath.hourRangeForWeek(weekEntries)
    }

    val rangeText = if (hourRange != null) {
        val startLabel = formatHour12(context, hourRange.first)
        val endLabel = formatHour12(context, hourRange.second)
        stringResource(R.string.week_most_checkins_between, startLabel, endLabel)
    } else {
        stringResource(R.string.week_no_checkins)
    }

    val daysTracked = remember(weekEntries) {
        weekEntries.map { it.localDate() }.distinct().size
    }
    val totalCheckIns = weekEntries.size
    val daysTrackedStr = pluralStringResource(R.plurals.week_days_tracked, daysTracked, daysTracked)
    val checkInsCountStr = pluralStringResource(R.plurals.week_checkins_count, totalCheckIns, totalCheckIns)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // "Most check-ins are between 8 AM and 6 PM"
        Text(
            text = rangeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Legend: "Check-ins: ☹️ sad  😐 neutral  😊 happy"
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.week_legend_prefix),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            LegendItem(
                mood = Mood.LOW,
                label = stringResource(R.string.week_legend_sad),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            LegendItem(
                mood = Mood.OKAY,
                label = stringResource(R.string.week_legend_neutral),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            LegendItem(
                mood = Mood.GOOD,
                label = stringResource(R.string.week_legend_happy),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // "7 days tracked • 18 check-ins"
        Text(
            text = stringResource(R.string.week_summary_footer, daysTrackedStr, checkInsCountStr),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LegendItem(
    mood: Mood,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlotSmileyIcon(mood = mood, size = 18.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PlotSmileyIcon(
    mood: Mood,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val smileColors = LocalSmileProcColors.current
    val borderColor = smileColors.canvasBase
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawPlotSmiley(
            mood = mood,
            center = center,
            radius = radius,
            borderColor = borderColor,
            smileColors = smileColors,
        )
    }
}

private fun DrawScope.drawPlotSmiley(
    mood: Mood,
    center: Offset,
    radius: Float,
    borderColor: Color,
    smileColors: SmileProcColors,
    alpha: Float = 1f,
) {
    val fillColor = when (mood) {
        Mood.LOW -> smileColors.moodLow
        Mood.OKAY -> smileColors.moodOkay
        Mood.GOOD -> smileColors.moodGood
    }.copy(alpha = alpha)
    val featureColor = when (mood) {
        Mood.LOW -> smileColors.moodLowFeature
        Mood.OKAY -> smileColors.moodOkayFeature
        Mood.GOOD -> smileColors.moodGoodFeature
    }.copy(alpha = alpha)

    // Thin outer separation stroke so overlapping points stay clearly distinguishable
    drawCircle(
        color = borderColor.copy(alpha = alpha),
        radius = radius,
        center = center,
    )

    val faceRadius = radius * 0.92f
    val topHighlight = lerp(fillColor, Color.White, 0.24f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(topHighlight, fillColor),
            center = Offset(center.x - faceRadius * 0.2f, center.y - faceRadius * 0.25f),
            radius = faceRadius * 1.25f,
        ),
        radius = faceRadius,
        center = center,
    )

    val strokeWidth = (faceRadius * 0.14f).coerceAtLeast(1.5f)
    val eyeOffsetX = faceRadius * 0.34f
    val eyeOffsetY = faceRadius * 0.16f
    val eyeRadius = (faceRadius * 0.11f).coerceAtLeast(1.2f)

    when (mood) {
        Mood.LOW -> {
            // Eyes
            drawCircle(
                color = featureColor,
                radius = eyeRadius,
                center = Offset(center.x - eyeOffsetX, center.y - eyeOffsetY * 0.6f),
            )
            drawCircle(
                color = featureColor,
                radius = eyeRadius,
                center = Offset(center.x + eyeOffsetX, center.y - eyeOffsetY * 0.6f),
            )
            // Subtle furrowed brows
            drawLine(
                color = featureColor,
                start = Offset(center.x - eyeOffsetX * 1.35f, center.y - eyeOffsetY * 1.4f),
                end = Offset(center.x - eyeOffsetX * 0.55f, center.y - eyeOffsetY * 1.9f),
                strokeWidth = strokeWidth * 0.85f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = featureColor,
                start = Offset(center.x + eyeOffsetX * 0.55f, center.y - eyeOffsetY * 1.9f),
                end = Offset(center.x + eyeOffsetX * 1.35f, center.y - eyeOffsetY * 1.4f),
                strokeWidth = strokeWidth * 0.85f,
                cap = StrokeCap.Round,
            )
            // Downturned sad arc mouth
            val mouthWidth = faceRadius * 0.72f
            val mouthTop = center.y + faceRadius * 0.22f
            val path = Path().apply {
                arcTo(
                    rect = Rect(
                        left = center.x - mouthWidth / 2f,
                        top = mouthTop,
                        right = center.x + mouthWidth / 2f,
                        bottom = mouthTop + faceRadius * 0.42f,
                    ),
                    startAngleDegrees = 200f,
                    sweepAngleDegrees = 140f,
                    forceMoveTo = true,
                )
            }
            drawPath(
                path = path,
                color = featureColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Mood.OKAY -> {
            // Round eyes
            drawCircle(
                color = featureColor,
                radius = eyeRadius,
                center = Offset(center.x - eyeOffsetX, center.y - eyeOffsetY),
            )
            drawCircle(
                color = featureColor,
                radius = eyeRadius,
                center = Offset(center.x + eyeOffsetX, center.y - eyeOffsetY),
            )
            // Straight neutral mouth
            val mouthHalfWidth = faceRadius * 0.34f
            val mouthY = center.y + faceRadius * 0.28f
            drawLine(
                color = featureColor,
                start = Offset(center.x - mouthHalfWidth, mouthY),
                end = Offset(center.x + mouthHalfWidth, mouthY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        Mood.GOOD -> {
            // Happy curved eyes
            val eyeArcWidth = faceRadius * 0.30f
            val eyeArcHeight = faceRadius * 0.22f
            val leftEyePath = Path().apply {
                arcTo(
                    rect = Rect(
                        left = center.x - eyeOffsetX - eyeArcWidth / 2f,
                        top = center.y - eyeOffsetY - eyeArcHeight / 2f,
                        right = center.x - eyeOffsetX + eyeArcWidth / 2f,
                        bottom = center.y - eyeOffsetY + eyeArcHeight / 2f,
                    ),
                    startAngleDegrees = 200f,
                    sweepAngleDegrees = 140f,
                    forceMoveTo = true,
                )
            }
            val rightEyePath = Path().apply {
                arcTo(
                    rect = Rect(
                        left = center.x + eyeOffsetX - eyeArcWidth / 2f,
                        top = center.y - eyeOffsetY - eyeArcHeight / 2f,
                        right = center.x + eyeOffsetX + eyeArcWidth / 2f,
                        bottom = center.y - eyeOffsetY + eyeArcHeight / 2f,
                    ),
                    startAngleDegrees = 200f,
                    sweepAngleDegrees = 140f,
                    forceMoveTo = true,
                )
            }
            drawPath(
                path = leftEyePath,
                color = featureColor,
                style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round),
            )
            drawPath(
                path = rightEyePath,
                color = featureColor,
                style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round),
            )
            // Upturned smile arc
            val mouthWidth = faceRadius * 0.76f
            val mouthTop = center.y + faceRadius * 0.02f
            val mouthPath = Path().apply {
                arcTo(
                    rect = Rect(
                        left = center.x - mouthWidth / 2f,
                        top = mouthTop,
                        right = center.x + mouthWidth / 2f,
                        bottom = mouthTop + faceRadius * 0.48f,
                    ),
                    startAngleDegrees = 20f,
                    sweepAngleDegrees = 140f,
                    forceMoveTo = true,
                )
            }
            drawPath(
                path = mouthPath,
                color = featureColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

private fun formatHour12(context: android.content.Context, hour: Int): String {
    val normalized = ((hour % 24) + 24) % 24
    if (com.example.smileproc.AppLocale.effectiveLanguageCode(context) == "lt") {
        return context.getString(R.string.week_hour_24, normalized)
    }
    return when (normalized) {
        0 -> context.getString(R.string.week_hour_12am)
        12 -> context.getString(R.string.week_hour_12pm)
        in 1..11 -> context.getString(R.string.week_hour_am, normalized)
        else -> context.getString(R.string.week_hour_pm, normalized - 12)
    }
}
