package com.example.smileproc.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.Entry
import com.example.smileproc.core.Mood
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.min

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

private val LowFaceColor = Color(0xFFD84339)
private val LowFeatureColor = Color(0xFF2B110E)
private val OkayFaceColor = Color(0xFFF2C230)
private val OkayFeatureColor = Color(0xFF2B2108)
private val GoodFaceColor = Color(0xFF34A853)
private val GoodFeatureColor = Color(0xFF0E2B14)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeekScreen(
    entries: List<Entry>,
    initialWeekStart: LocalDate? = null,
    modifier: Modifier = Modifier,
) {
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

    val dateFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val startStr = dateFormatter.format(weekStart)
    val endStr = dateFormatter.format(weekEnd)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.week_title)) },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Period date range header (e.g., 2026-11-01 – 2026-11-08)
            Text(
                text = stringResource(R.string.week_period, startStr, endStr),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("week_period_header"),
            )

            // Day & Time Patterns section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    onPreviousWeek = {
                        userNavigated = true
                        selectedWeekEpochDay = weekStart.minusWeeks(1).toEpochDay()
                    },
                    onNextWeek = {
                        userNavigated = true
                        selectedWeekEpochDay = weekStart.plusWeeks(1).toEpochDay()
                    },
                )
            }

            // Summary, Legend, and Tracked Footer
            WeekPlotFooter(weekEntries = weekEntries)
        }
    }
}

@Composable
private fun DayTimePlot(
    points: List<PlotPoint>,
    startStr: String,
    endStr: String,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

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
    val gridBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val smileyBorderColor = MaterialTheme.colorScheme.background

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
                        .height(gridHeight),
                ) {
                    val width = size.width
                    val height = size.height
                    val colWidth = width / WeekPlotMath.GRID_COLS
                    val rowHeight = height / WeekPlotMath.GRID_ROWS
                    val strokePx = 1.dp.toPx()

                    // Grid background
                    drawRect(
                        color = gridBackgroundColor,
                        topLeft = Offset.Zero,
                        size = Size(width, height),
                    )

                    // 13 horizontal lines (12 2-hour bands)
                    for (row in 0..WeekPlotMath.GRID_ROWS) {
                        val y = row * rowHeight
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = strokePx,
                        )
                    }

                    // 8 vertical lines (7 day columns)
                    for (col in 0..WeekPlotMath.GRID_COLS) {
                        val x = col * colWidth
                        drawLine(
                            color = gridLineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = strokePx,
                        )
                    }

                    // Plot check-in smileys
                    val smileyRadius = min(colWidth * 0.34f, 15.dp.toPx())
                    for (point in points) {
                        val cx = (point.dayIndex + 0.5f) * colWidth
                        val rawCy = height * (1f - point.yFractionFromBottom)
                        val cy = rawCy.coerceIn(smileyRadius, height - smileyRadius)
                        drawPlotSmiley(
                            mood = point.mood,
                            center = Offset(cx, cy),
                            radius = smileyRadius,
                            borderColor = smileyBorderColor,
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
    val borderColor = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawPlotSmiley(
            mood = mood,
            center = center,
            radius = radius,
            borderColor = borderColor,
        )
    }
}

private fun DrawScope.drawPlotSmiley(
    mood: Mood,
    center: Offset,
    radius: Float,
    borderColor: Color,
) {
    val fillColor = when (mood) {
        Mood.LOW -> LowFaceColor
        Mood.OKAY -> OkayFaceColor
        Mood.GOOD -> GoodFaceColor
    }
    val featureColor = when (mood) {
        Mood.LOW -> LowFeatureColor
        Mood.OKAY -> OkayFeatureColor
        Mood.GOOD -> GoodFeatureColor
    }

    // Thin outer separation stroke so overlapping points stay clearly distinguishable
    drawCircle(
        color = borderColor,
        radius = radius,
        center = center,
    )

    val faceRadius = radius * 0.92f
    drawCircle(
        color = fillColor,
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
    return when (normalized) {
        0 -> context.getString(R.string.week_hour_12am)
        12 -> context.getString(R.string.week_hour_12pm)
        in 1..11 -> context.getString(R.string.week_hour_am, normalized)
        else -> context.getString(R.string.week_hour_pm, normalized - 12)
    }
}
