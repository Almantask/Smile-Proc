package com.example.smileproc.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smileproc.AppLocale
import com.example.smileproc.R
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.DayMoodSummary
import com.example.smileproc.core.Daypart
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.InsightsEngine
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceMoodSummary
import com.example.smileproc.core.TagMoodSummary
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassDivider
import com.example.smileproc.ui.components.MoodEntryCard
import com.example.smileproc.ui.places.PlaceFormatters
import com.example.smileproc.ui.places.getPlaceDisplayName
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.tags.coreTagChipColors
import com.example.smileproc.ui.tags.resolveAnyTagLabel
import com.example.smileproc.ui.tags.resolveTagCoreColor
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.SmileProcColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyRhythmSection(
    entries: List<Entry>,
    places: PlaceAssignment?,
    entryExtrasById: Map<String, EntryExtras>,
    customTagsById: Map<String, CustomTag>,
    onSelectEntry: (Entry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val locale = AppLocale.effectiveLocale(context)
    var selectedDaysWindow by rememberSaveable { mutableIntStateOf(30) }
    var selectedDateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    val report = remember(entries, selectedDaysWindow) {
        InsightsEngine.buildMonthlyRhythm(
            entries = entries,
            days = selectedDaysWindow,
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
        )
    }

    val entriesById = remember(entries) { entries.associateBy { it.id } }
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val shortDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }

    val selectedDaySummary = remember(report, selectedDateEpochDay) {
        selectedDateEpochDay?.let { epoch ->
            val date = LocalDate.ofEpochDay(epoch)
            report.daySummaries.firstOrNull { it.date == date && it.total > 0 }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                30 to stringResource(R.string.rhythm_window_30),
                90 to stringResource(R.string.rhythm_window_90),
            ).forEach { (daysCount, label) ->
                val isSelected = selectedDaysWindow == daysCount
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedDaysWindow = daysCount
                        selectedDateEpochDay = null
                    },
                    label = { Text(label) },
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

        if (report.totalEntries == 0) {
            GlassCard(
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.rhythm_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        val checkInsStr = pluralStringResource(
            R.plurals.rhythm_checkins_count,
            report.totalEntries,
            report.totalEntries,
        )
        val daysStr = pluralStringResource(
            R.plurals.rhythm_days_count,
            report.activeDays,
            report.activeDays,
        )

        // Day-by-day mosaic card
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.rhythm_mosaic_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$checkInsStr \u00B7 $daysStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 7 columns across (calendar rows aligned with leading spacers if needed, or compact grid of rows of 7/10)
                val columnsPerRow = if (selectedDaysWindow <= 30) 7 else 10
                val rows = report.daySummaries.chunked(columnsPerRow)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowDays.forEach { day ->
                                val isDaySelected = selectedDaySummary?.date == day.date
                                val dayColor = daySummaryColor(day, smileColors)
                                val formattedDate = dateFormatter.format(day.date)
                                val dayCd = if (day.total == 0) {
                                    stringResource(R.string.rhythm_empty_day, formattedDate)
                                } else {
                                    val countsText = stringResource(
                                        R.string.insights_mood_breakdown_counts,
                                        day.goodCount,
                                        day.okayCount,
                                        day.lowCount,
                                    )
                                    stringResource(R.string.rhythm_day_summary_a11y, formattedDate, countsText)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(dayColor)
                                        .border(
                                            width = if (isDaySelected) 2.dp else 1.dp,
                                            color = if (isDaySelected) {
                                                smileColors.brandAccent
                                            } else {
                                                smileColors.chipBorder.copy(alpha = if (day.total > 0) 0.7f else 0.35f)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .semantics { contentDescription = dayCd }
                                        .then(
                                            if (day.total > 0) {
                                                Modifier.clickable(role = Role.Button) {
                                                    selectedDateEpochDay = if (isDaySelected) {
                                                        null
                                                    } else {
                                                        day.date.toEpochDay()
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                            repeat(columnsPerRow - rowDays.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Text(
                    text = "${shortDateFormatter.format(report.startDate)} \u2013 ${shortDateFormatter.format(report.endDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (selectedDaySummary != null) {
            val dayEntries = remember(selectedDaySummary, entriesById) {
                selectedDaySummary.entryIds.mapNotNull { entriesById[it] }.reversed()
            }
            GlassCard(
                highlighted = true,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = dateFormatter.format(selectedDaySummary.date),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    dayEntries.forEach { entry ->
                        MoodEntryCard(
                            entry = entry,
                            placeName = getPlaceDisplayName(context, entry.id, places),
                            extras = entryExtrasById[entry.id],
                            customTagsById = customTagsById,
                            onClick = { onSelectEntry(entry) },
                        )
                    }
                }
            }
        }

        // Time-of-day distribution
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.rhythm_daypart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val maxDaypart = report.byDaypart.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
                report.byDaypart.forEach { summary ->
                    val rawLabel = when (summary.daypart) {
                        Daypart.MORNING -> stringResource(R.string.daypart_morning)
                        Daypart.AFTERNOON -> stringResource(R.string.daypart_afternoon)
                        Daypart.EVENING -> stringResource(R.string.daypart_evening)
                        Daypart.NIGHT -> stringResource(R.string.daypart_night)
                    }
                    val label = rawLabel.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                    }
                    MoodDistributionRow(
                        label = label,
                        lowCount = summary.lowCount,
                        okayCount = summary.okayCount,
                        goodCount = summary.goodCount,
                        scaleMax = maxDaypart,
                    )
                }
            }
        }

        // Day-of-week distribution
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.rhythm_weekday_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val maxWeekday = report.byWeekday.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
                report.byWeekday.forEach { summary ->
                    val weekdayRes = when (summary.isoWeekday) {
                        1 -> R.string.weekday_short_1
                        2 -> R.string.weekday_short_2
                        3 -> R.string.weekday_short_3
                        4 -> R.string.weekday_short_4
                        5 -> R.string.weekday_short_5
                        6 -> R.string.weekday_short_6
                        else -> R.string.weekday_short_7
                    }
                    MoodDistributionRow(
                        label = stringResource(weekdayRes),
                        lowCount = summary.lowCount,
                        okayCount = summary.okayCount,
                        goodCount = summary.goodCount,
                        scaleMax = maxWeekday,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakdownsInsightsSection(
    entries: List<Entry>,
    places: PlaceAssignment?,
    entryExtrasById: Map<String, EntryExtras>,
    customTags: List<CustomTag>,
    onNameUnnamedCluster: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val emotionCatalog = remember(context) { EmotionTagsCatalog.get(context) }
    val customTagsById = remember(customTags) { customTags.associateBy { it.id } }

    val report = remember(entries, entryExtrasById, customTags, places, emotionCatalog) {
        InsightsEngine.buildBreakdowns(
            entries = entries,
            extrasByEntryId = entryExtrasById,
            customTags = customTags,
            placeAssignment = places,
            emotionTags = emotionCatalog,
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Feelings by mood
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.insights_feelings_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val hasAnyTags = report.byMood.values.any { it.topTags.isNotEmpty() }
                if (!hasAnyTags) {
                    Text(
                        text = stringResource(R.string.insights_no_tags_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    listOf(Mood.GOOD, Mood.OKAY, Mood.LOW).forEachIndexed { idx, mood ->
                        val highlight = report.byMood[mood]
                        if (highlight != null && highlight.topTags.isNotEmpty()) {
                            if (idx > 0) {
                                GlassDivider()
                            }
                            val moodTitle = when (mood) {
                                Mood.GOOD -> stringResource(R.string.face_good)
                                Mood.OKAY -> stringResource(R.string.face_okay)
                                Mood.LOW -> stringResource(R.string.face_low)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PlotSmileyIcon(mood = mood, size = 20.dp)
                                Text(
                                    text = "$moodTitle (${highlight.entryCount})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                highlight.topTags.forEach { tagSummary ->
                                    val countInMood = when (mood) {
                                        Mood.LOW -> tagSummary.lowCount
                                        Mood.OKAY -> tagSummary.okayCount
                                        Mood.GOOD -> tagSummary.goodCount
                                    }
                                    val tagLabel = context.resolveAnyTagLabel(tagSummary.tagId, customTagsById)
                                    val coreColor = resolveTagCoreColor(emotionCatalog, tagSummary.tagId)
                                    val chipColors = if (coreColor != null) {
                                        coreTagChipColors(coreColor, smileColors.isDark, emphasized = false)
                                    } else {
                                        null
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(chipColors?.container ?: smileColors.chipFill)
                                            .border(
                                                width = 1.dp,
                                                color = chipColors?.border ?: smileColors.chipBorder,
                                                shape = CircleShape,
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = "$tagLabel \u00B7 $countInMood",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = chipColors?.content ?: MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Context & personal tags breakdown
        if (report.contextAndCustomTags.isNotEmpty()) {
            GlassCard(
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.insights_context_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val maxTotal = report.contextAndCustomTags.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
                    report.contextAndCustomTags.forEach { summary ->
                        val label = context.resolveAnyTagLabel(summary.tagId, customTagsById)
                        MoodDistributionRow(
                            label = label,
                            lowCount = summary.lowCount,
                            okayCount = summary.okayCount,
                            goodCount = summary.goodCount,
                            scaleMax = maxTotal,
                        )
                    }
                }
            }
        }

        // Places mood breakdown
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.insights_places_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (report.places.isEmpty()) {
                    Text(
                        text = stringResource(R.string.insights_no_places_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    report.places.forEachIndexed { idx, placeSummary ->
                        if (idx > 0) {
                            GlassDivider()
                        }
                        PlaceInsightItem(
                            summary = placeSummary,
                            customTagsById = customTagsById,
                            onNameUnnamedCluster = onNameUnnamedCluster,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceInsightItem(
    summary: PlaceMoodSummary,
    customTagsById: Map<String, CustomTag>,
    onNameUnnamedCluster: (String) -> Unit,
) {
    val context = LocalContext.current
    val title = summary.label?.name
        ?: PlaceFormatters.formatUnnamedDescription(context, summary.memberEntries)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SegmentedMoodBar(
            lowCount = summary.lowCount,
            okayCount = summary.okayCount,
            goodCount = summary.goodCount,
            scaleMax = summary.total.coerceAtLeast(1),
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )
        Text(
            text = stringResource(
                R.string.insights_mood_breakdown_counts,
                summary.goodCount,
                summary.okayCount,
                summary.lowCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.topTagIds.isNotEmpty()) {
            val tagNames = summary.topTagIds.joinToString(", ") {
                context.resolveAnyTagLabel(it, customTagsById)
            }
            Text(
                text = stringResource(R.string.insights_top_tags_prefix, tagNames),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.isUnnamed) {
            OutlinedButton(
                onClick = { onNameUnnamedCluster(summary.clusterId.value) },
                shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.week_pattern_name_place))
            }
        }
    }
}

@Composable
private fun MoodDistributionRow(
    label: String,
    lowCount: Int,
    okayCount: Int,
    goodCount: Int,
    scaleMax: Int,
) {
    val total = lowCount + okayCount + goodCount
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 76.dp, max = 120.dp),
        )
        SegmentedMoodBar(
            lowCount = lowCount,
            okayCount = okayCount,
            goodCount = goodCount,
            scaleMax = scaleMax.coerceAtLeast(1),
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
        )
        Text(
            text = total.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 24.dp),
        )
    }
}

@Composable
private fun SegmentedMoodBar(
    lowCount: Int,
    okayCount: Int,
    goodCount: Int,
    scaleMax: Int,
    modifier: Modifier = Modifier,
) {
    val smileColors = LocalSmileProcColors.current
    val total = lowCount + okayCount + goodCount
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(smileColors.chipFill),
    ) {
        if (total > 0 && scaleMax > 0) {
            val fillFraction = (total.toFloat() / scaleMax.toFloat()).coerceIn(0.06f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .fillMaxHeight()
                    .clip(CircleShape),
            ) {
                if (goodCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(goodCount.toFloat())
                            .fillMaxHeight()
                            .background(smileColors.moodGood),
                    )
                }
                if (okayCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(okayCount.toFloat())
                            .fillMaxHeight()
                            .background(smileColors.moodOkay),
                    )
                }
                if (lowCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(lowCount.toFloat())
                            .fillMaxHeight()
                            .background(smileColors.moodLow),
                    )
                }
            }
        }
    }
}

private fun daySummaryColor(day: DayMoodSummary, colors: SmileProcColors): Color {
    if (day.total <= 0) {
        return colors.chipFill.copy(alpha = if (colors.isDark) 0.45f else 0.55f)
    }
    // Blend softly across the day's recorded moods (purely visual color blend, never a score).
    val totalWeight = day.total.toFloat()
    val goodW = day.goodCount / totalWeight
    val okayW = day.okayCount / totalWeight
    val lowW = day.lowCount / totalWeight
    val r = colors.moodGood.red * goodW + colors.moodOkay.red * okayW + colors.moodLow.red * lowW
    val g = colors.moodGood.green * goodW + colors.moodOkay.green * okayW + colors.moodLow.green * lowW
    val b = colors.moodGood.blue * goodW + colors.moodOkay.blue * okayW + colors.moodLow.blue * lowW
    val base = Color(red = r, green = g, blue = b, alpha = 1f)
    return if (colors.isDark) base.copy(alpha = 0.85f) else lerp(base, Color.White, 0.12f)
}
