package com.example.smileproc.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.CardItem
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Variant
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WeeklyCardTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    cards: List<WeeklyCard>,
    labels: List<PlaceLabel>,
    places: PlaceAssignment?,
    weeklyCardTime: WeeklyCardTime,
    onNavigateToSupport: () -> Unit,
    onNamePlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPeriodStart by remember(cards) {
        mutableStateOf(cards.firstOrNull()?.periodStart)
    }

    val activeCard = cards.find { it.periodStart == selectedPeriodStart } ?: cards.firstOrNull()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.week_title)) },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (activeCard == null) {
            PreFirstCardView(
                weeklyCardTime = weeklyCardTime,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Past weeks selector if more than 1 card
                if (cards.size > 1) {
                    PastWeeksSelector(
                        cards = cards,
                        selectedPeriodStart = activeCard.periodStart,
                        onSelectCard = { selectedPeriodStart = it.periodStart },
                    )
                }

                // Period header
                PeriodHeader(card = activeCard)

                // 7-day strip of face counts
                DayStrip(days = activeCard.days)

                // Low-week block (leads with acknowledgment and Talk to someone before tips)
                if (activeCard.isLowWeek) {
                    LowWeekBlock(onNavigateToSupport = onNavigateToSupport)
                }

                // Variant note for notEnoughData / firstWeek
                when (activeCard.variant) {
                    Variant.NOT_ENOUGH_DATA -> {
                        Text(
                            text = stringResource(R.string.week_not_enough_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    Variant.FIRST_WEEK -> {
                        Text(
                            text = stringResource(R.string.week_first_week),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    Variant.NORMAL -> Unit
                }

                // Pattern items
                activeCard.items.forEach { item ->
                    PatternItemCard(
                        item = item,
                        labels = labels,
                        places = places,
                        onNamePlace = onNamePlace,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PreFirstCardView(
    weeklyCardTime: WeeklyCardTime,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val weekdayRes = when (weeklyCardTime.weekday) {
        1 -> R.string.weekday_1
        2 -> R.string.weekday_2
        3 -> R.string.weekday_3
        4 -> R.string.weekday_4
        5 -> R.string.weekday_5
        6 -> R.string.weekday_6
        else -> R.string.weekday_7
    }
    val weekdayName = stringResource(weekdayRes)
    val timeFormatted = String.format(Locale.ROOT, "%02d:%02d", weeklyCardTime.hour, weeklyCardTime.minute)
    val cardTimeStr = stringResource(R.string.week_card_time, weekdayName, timeFormatted)

    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.week_first_card, cardTimeStr),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PastWeeksSelector(
    cards: List<WeeklyCard>,
    selectedPeriodStart: Instant,
    onSelectCard: (WeeklyCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.week_past_cards),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cards) { card ->
                val isSelected = card.periodStart == selectedPeriodStart
                val endDate = card.periodEnd.atZone(zone).toLocalDate()
                val label = formatter.format(endDate)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCard(card) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun PeriodHeader(
    card: WeeklyCard,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val startStr = formatter.format(card.periodStart.atZone(zone).toLocalDate())
    val endStr = formatter.format(card.periodEnd.atZone(zone).toLocalDate())

    Text(
        text = stringResource(R.string.week_period, startStr, endStr),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun DayStrip(
    days: List<List<Int>>,
    modifier: Modifier = Modifier,
) {
    val weekdayLetters = listOf(
        stringResource(R.string.weekday_1).take(1),
        stringResource(R.string.weekday_2).take(1),
        stringResource(R.string.weekday_3).take(1),
        stringResource(R.string.weekday_4).take(1),
        stringResource(R.string.weekday_5).take(1),
        stringResource(R.string.weekday_6).take(1),
        stringResource(R.string.weekday_7).take(1),
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEachIndexed { index, counts ->
                val low = counts.getOrElse(0) { 0 }
                val okay = counts.getOrElse(1) { 0 }
                val good = counts.getOrElse(2) { 0 }
                val total = low + okay + good

                val dayNameRes = when (index + 1) {
                    1 -> R.string.weekday_1
                    2 -> R.string.weekday_2
                    3 -> R.string.weekday_3
                    4 -> R.string.weekday_4
                    5 -> R.string.weekday_5
                    6 -> R.string.weekday_6
                    else -> R.string.weekday_7
                }
                val dayName = stringResource(dayNameRes)
                val cd = stringResource(R.string.week_strip_day_cd, dayName, low, okay, good)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.semantics { contentDescription = cd },
                ) {
                    Text(
                        text = weekdayLetters[index],
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Small indicator dots / counts for Low, Okay, Good
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (good > 0) {
                            DayCountDot(count = good, color = Color(0xFF4CAF50))
                        }
                        if (okay > 0) {
                            DayCountDot(count = okay, color = Color(0xFFFFC107))
                        }
                        if (low > 0) {
                            DayCountDot(count = low, color = Color(0xFFE57373))
                        }
                        if (total == 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCountDot(count: Int, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LowWeekBlock(
    onNavigateToSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.week_low_acknowledgment),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.week_low_talk),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val actionLabel = stringResource(R.string.week_low_talk_action)
            OutlinedButton(
                onClick = onNavigateToSupport,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = actionLabel },
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun PatternItemCard(
    item: CardItem,
    labels: List<PlaceLabel>,
    places: PlaceAssignment?,
    onNamePlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val library = remember(context) { PatternLibraryCatalog.get(context) }
    val rendered = remember(item, labels, places) {
        WeeklyCardRenderer.renderItem(context, item, library, labels, places)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: Title and Early hint badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = rendered.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (rendered.isEarlyHint) {
                    val hintLabel = stringResource(R.string.week_early_hint)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = hintLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Observation line
            Text(
                text = rendered.observation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Common observation
            Text(
                text = rendered.common,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Tip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = rendered.tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Question
            Text(
                text = rendered.question,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
            )

            // "Name this place" button if applicable
            if (rendered.unnamedClusterId != null) {
                val namePlaceLabel = stringResource(R.string.week_name_this_place)
                OutlinedButton(
                    onClick = { onNamePlace(rendered.unnamedClusterId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = namePlaceLabel },
                ) {
                    Text(namePlaceLabel)
                }
            }
        }
    }
}
