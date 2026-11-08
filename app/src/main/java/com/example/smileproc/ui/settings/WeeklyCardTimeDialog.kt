package com.example.smileproc.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.WeeklyCardTime
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyCardTimeDialog(
    initialTime: WeeklyCardTime,
    onDismiss: () -> Unit,
    onConfirm: (WeeklyCardTime) -> Unit,
) {
    var selectedDay by remember { mutableIntStateOf(initialTime.weekday) }
    var hour by remember { mutableIntStateOf(initialTime.hour) }
    var minute by remember { mutableIntStateOf(initialTime.minute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_weekly_card_time)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.reminders_days),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (isoDay in 1..7) {
                        val isSelected = isoDay == selectedDay
                        val label = weekdayShortLabel(isoDay)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDay = isoDay },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(WeeklyCardTime(weekday = selectedDay, hour = hour, minute = minute))
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
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
