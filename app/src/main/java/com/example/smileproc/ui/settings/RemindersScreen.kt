package com.example.smileproc.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.AppContainer
import com.example.smileproc.R
import com.example.smileproc.core.Reminder
import com.example.smileproc.notify.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reminders by container.settingsRepository.reminders.collectAsStateWithLifecycle(initialValue = Reminder.DEFAULT_REMINDERS)

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (reminders.isEmpty()) {
                Text(
                    text = stringResource(R.string.reminders_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            onDelete = {
                                val updated = reminders.filterNot { it.id == reminder.id }
                                scope.launch {
                                    container.settingsRepository.setReminders(updated)
                                    ReminderScheduler.scheduleNext(context)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (reminders.size < 6) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.reminders_add))
                }
            } else {
                Text(
                    text = stringResource(R.string.reminders_max),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { hour, minute, days ->
                val newReminder = Reminder(hour = hour, minute = minute, days = days)
                val updated = reminders + newReminder
                scope.launch {
                    container.settingsRepository.setReminders(updated)
                    ReminderScheduler.scheduleNext(context)
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeString = String.format(Locale.ROOT, "%02d:%02d", reminder.hour, reminder.minute)
    val timeCd = stringResource(R.string.reminders_time_cd, timeString)
    val deleteCd = stringResource(R.string.reminders_delete_cd, timeString)

    val daysText = if (reminder.days == (1..7).toSet()) {
        stringResource(R.string.reminders_every_day)
    } else {
        val dayNames = reminder.days.sorted().map { weekdayShortLabel(it) }
        dayNames.joinToString(", ")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.semantics { contentDescription = timeCd }) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = daysText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = deleteCd },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = deleteCd,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, days: Set<Int>) -> Unit,
) {
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var selectedDays by remember { mutableStateOf((1..7).toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_add)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimePickerWheel(
                        value = hour,
                        range = 0..23,
                        onValueChange = { hour = it },
                        label = "Hour",
                    )
                    Text(" : ", style = MaterialTheme.typography.headlineMedium)
                    TimePickerWheel(
                        value = minute,
                        range = 0..59,
                        step = 5,
                        onValueChange = { minute = it },
                        label = "Min",
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.reminders_days),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (isoDay in 1..7) {
                        val isSelected = isoDay in selectedDays
                        val label = weekdayShortLabel(isoDay)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDays = if (isSelected) {
                                    if (selectedDays.size > 1) selectedDays - isoDay else selectedDays
                                } else {
                                    selectedDays + isoDay
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(hour, minute, selectedDays) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TimePickerWheel(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    label: String,
    step: Int = 1,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {
                val next = value + step
                onValueChange(if (next > range.last) range.first else next)
            },
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = String.format(Locale.ROOT, "%02d", value),
            style = MaterialTheme.typography.headlineMedium,
        )
        IconButton(
            onClick = {
                val prev = value - step
                onValueChange(if (prev < range.first) range.last - (range.last % step) else prev)
            },
        ) {
            Text("-", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun weekdayShortLabel(isoDay: Int): String {
    val resId = when (isoDay) {
        1 -> R.string.weekday_1
        2 -> R.string.weekday_2
        3 -> R.string.weekday_3
        4 -> R.string.weekday_4
        5 -> R.string.weekday_5
        6 -> R.string.weekday_6
        else -> R.string.weekday_7
    }
    return stringResource(resId).take(3)
}
