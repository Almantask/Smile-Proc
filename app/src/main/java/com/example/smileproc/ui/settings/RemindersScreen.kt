package com.example.smileproc.ui.settings

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.AppContainer
import com.example.smileproc.AppLocale
import com.example.smileproc.R
import com.example.smileproc.core.Reminder
import com.example.smileproc.core.ReminderPause
import com.example.smileproc.core.ReminderScheduleCalculator
import com.example.smileproc.notify.ReminderScheduler
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassTopBar
import com.example.smileproc.ui.theme.LocalSmileProcColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val smileColors = LocalSmileProcColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    val reminders by container.settingsRepository.reminders.collectAsStateWithLifecycle(
        initialValue = Reminder.DEFAULT_REMINDERS,
    )
    val reminderWindows by container.settingsRepository.reminderWindows.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )
    val reminderPause by container.settingsRepository.reminderPause.collectAsStateWithLifecycle(
        initialValue = ReminderPause.NONE,
    )

    val sortedReminders = remember(reminders) {
        reminders.sortedWith(compareBy<Reminder>({ it.hour }, { it.minute }, { it.id }))
    }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var showPauseDialog by remember { mutableStateOf(false) }

    val duplicateNotice = stringResource(R.string.reminders_duplicate_notice)
    val locale = AppLocale.effectiveLocale(context)
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }
    val now = Instant.now()
    val isPaused = reminderPause.isPausedAt(now)
    val pausedUntil = reminderPause.effectivePausedUntil(now)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = smileColors.dialogContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.reminders_title),
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Pause / Resume card
            GlassCard(
                highlighted = isPaused,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            isPaused && pausedUntil != null ->
                                stringResource(R.string.reminders_paused_until, dateFormatter.format(pausedUntil))
                            isPaused ->
                                stringResource(R.string.reminders_paused_indefinitely)
                            else ->
                                stringResource(R.string.reminders_pause_title)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isPaused) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.setReminderPause(ReminderPause.NONE)
                                    ReminderScheduler.scheduleNext(context)
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.reminders_resume_action))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showPauseDialog = true },
                            shape = CircleShape,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pause),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.reminders_pause_action))
                        }
                    }
                }
            }

            if (sortedReminders.isEmpty()) {
                GlassCard(
                    contentPadding = PaddingValues(18.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reminders_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortedReminders.forEach { reminder ->
                        val windowMinutes = ReminderScheduleCalculator.sanitizeWindowMinutes(
                            reminderWindows[reminder.id] ?: 0,
                        )
                        ReminderRow(
                            reminder = reminder,
                            windowMinutes = windowMinutes,
                            onEdit = {
                                editingReminder = reminder
                                showEditorDialog = true
                            },
                            onDelete = {
                                val updated = reminders.filterNot { it.id == reminder.id }
                                val updatedWindows = reminderWindows - reminder.id
                                scope.launch {
                                    container.settingsRepository.setReminders(updated)
                                    container.settingsRepository.setReminderWindows(updatedWindows)
                                    ReminderScheduler.scheduleNext(context)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (sortedReminders.size < 6) {
                Button(
                    onClick = {
                        editingReminder = null
                        showEditorDialog = true
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                ) {
                    Text(stringResource(R.string.reminders_add))
                }
            } else {
                GlassCard(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reminders_max),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showEditorDialog) {
        val currentEditing = editingReminder
        val initialWindow = currentEditing?.let { reminderWindows[it.id] ?: 0 } ?: 0
        ReminderEditorDialog(
            editingReminder = currentEditing,
            initialWindowMinutes = initialWindow,
            onDismiss = {
                showEditorDialog = false
                editingReminder = null
            },
            onConfirm = { hour, minute, days, windowMinutes ->
                val duplicate = reminders.firstOrNull {
                    it.hour == hour && it.minute == minute && it.id != currentEditing?.id
                }
                val mutableWindows = reminderWindows.toMutableMap()
                val nextList: List<Reminder>
                var mergedDuplicate = false

                if (duplicate != null) {
                    mergedDuplicate = true
                    val merged = duplicate.copy(days = days)
                    nextList = reminders
                        .filterNot { it.id == currentEditing?.id }
                        .map { if (it.id == duplicate.id) merged else it }
                    currentEditing?.let { mutableWindows.remove(it.id) }
                    if (windowMinutes > 0) {
                        mutableWindows[duplicate.id] = windowMinutes
                    } else {
                        mutableWindows.remove(duplicate.id)
                    }
                } else if (currentEditing != null) {
                    val edited = currentEditing.copy(hour = hour, minute = minute, days = days)
                    nextList = reminders.map { if (it.id == currentEditing.id) edited else it }
                    if (windowMinutes > 0) {
                        mutableWindows[edited.id] = windowMinutes
                    } else {
                        mutableWindows.remove(edited.id)
                    }
                } else {
                    val created = Reminder(hour = hour, minute = minute, days = days)
                    nextList = reminders + created
                    if (windowMinutes > 0) {
                        mutableWindows[created.id] = windowMinutes
                    }
                }

                val sortedNext = nextList.sortedWith(compareBy<Reminder>({ it.hour }, { it.minute }, { it.id }))
                val validIds = sortedNext.mapTo(hashSetOf()) { it.id }
                mutableWindows.keys.retainAll(validIds)

                scope.launch {
                    container.settingsRepository.setReminders(sortedNext)
                    container.settingsRepository.setReminderWindows(mutableWindows)
                    ReminderScheduler.scheduleNext(context)
                    if (mergedDuplicate) {
                        snackbarHostState.showSnackbar(duplicateNotice)
                    }
                }
                showEditorDialog = false
                editingReminder = null
            },
        )
    }

    if (showPauseDialog) {
        PauseRemindersDialog(
            onDismiss = { showPauseDialog = false },
            onSelectPause = { pause ->
                showPauseDialog = false
                scope.launch {
                    container.settingsRepository.setReminderPause(pause)
                    ReminderScheduler.scheduleNext(context)
                }
            },
        )
    }
}

@Composable
private fun PauseRemindersDialog(
    onDismiss: () -> Unit,
    onSelectPause: (ReminderPause) -> Unit,
) {
    val smileColors = LocalSmileProcColors.current
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = smileColors.dialogContainer,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.reminders_pause_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSelectPause(ReminderPause.untilTomorrow(now, zone)) },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.reminders_pause_tomorrow))
                }
                OutlinedButton(
                    onClick = { onSelectPause(ReminderPause.forDays(now, 3L, zone)) },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.reminders_pause_three_days))
                }
                OutlinedButton(
                    onClick = { onSelectPause(ReminderPause.INDEFINITE) },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.reminders_pause_indefinite))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    windowMinutes: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val smileColors = LocalSmileProcColors.current
    val timeString = String.format(Locale.ROOT, "%02d:%02d", reminder.hour, reminder.minute)
    val timeCd = stringResource(R.string.reminders_time_cd, timeString)
    val deleteCd = stringResource(R.string.reminders_delete_cd, timeString)

    val daysText = if (reminder.days == (1..7).toSet()) {
        stringResource(R.string.reminders_every_day)
    } else {
        val dayNames = reminder.days.sorted().map { weekdayShortLabel(it) }
        dayNames.joinToString(", ")
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onEdit),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = timeCd },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (windowMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(smileColors.chipFill)
                                .border(1.dp, smileColors.chipBorder, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.reminders_window_plus_minus, windowMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
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
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditorDialog(
    editingReminder: Reminder?,
    initialWindowMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, days: Set<Int>, windowMinutes: Int) -> Unit,
) {
    val smileColors = LocalSmileProcColors.current
    var hour by remember { mutableIntStateOf(editingReminder?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(editingReminder?.minute ?: 0) }
    var selectedDays by remember { mutableStateOf(editingReminder?.days ?: (1..7).toSet()) }
    var windowMinutes by remember {
        mutableIntStateOf(ReminderScheduleCalculator.sanitizeWindowMinutes(initialWindowMinutes))
    }

    val hourLabel = stringResource(R.string.reminders_hour_label)
    val minuteLabel = stringResource(R.string.reminders_minute_label)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = smileColors.dialogContainer,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                stringResource(
                    if (editingReminder == null) R.string.reminders_add else R.string.reminders_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimePickerWheel(
                        value = hour,
                        range = 0..23,
                        onValueChange = { hour = it },
                        label = hourLabel,
                    )
                    Text(" : ", style = MaterialTheme.typography.headlineMedium)
                    TimePickerWheel(
                        value = minute,
                        range = 0..59,
                        step = 5,
                        onValueChange = { minute = it },
                        label = minuteLabel,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.reminders_days),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.reminders_window_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReminderScheduleCalculator.ALLOWED_WINDOWS_MINUTES.forEach { optionMinutes ->
                        val isSelected = windowMinutes == optionMinutes
                        val chipLabel = if (optionMinutes == 0) {
                            stringResource(R.string.reminders_window_exact)
                        } else {
                            stringResource(R.string.reminders_window_plus_minus, optionMinutes)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { windowMinutes = optionMinutes },
                            label = { Text(chipLabel) },
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

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.reminders_window_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(hour, minute, selectedDays, windowMinutes) },
                shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = CircleShape,
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
    val increaseCd = stringResource(R.string.time_picker_increase, label)
    val decreaseCd = stringResource(R.string.time_picker_decrease, label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {
                val next = value + step
                onValueChange(if (next > range.last) range.first else next)
            },
            modifier = Modifier.semantics { contentDescription = increaseCd },
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
            modifier = Modifier.semantics { contentDescription = decreaseCd },
        ) {
            Text("-", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun weekdayShortLabel(isoDay: Int): String {
    val resId = when (isoDay) {
        1 -> R.string.weekday_short_1
        2 -> R.string.weekday_short_2
        3 -> R.string.weekday_short_3
        4 -> R.string.weekday_short_4
        5 -> R.string.weekday_short_5
        6 -> R.string.weekday_short_6
        else -> R.string.weekday_short_7
    }
    return stringResource(resId)
}
