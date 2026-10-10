package com.example.smileproc.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.AppContainer
import com.example.smileproc.Biometrics
import com.example.smileproc.R
import com.example.smileproc.core.CardPeriods
import com.example.smileproc.core.Cluster
import com.example.smileproc.core.Entry
import com.example.smileproc.core.ExportApp
import com.example.smileproc.core.ExportDocument
import com.example.smileproc.core.ExportFormat
import com.example.smileproc.core.Instants
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.PlaceResolver
import com.example.smileproc.core.ReminderSpec
import com.example.smileproc.data.StoredCardItem
import com.example.smileproc.data.StoredWeeklyCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TodayScreen(app: AppContainer, model: TodayViewModel, onHistory: () -> Unit, onHelplines: () -> Unit) {
    RefreshOnResume(model::refresh)
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text(context, R.string.today_prompt), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FaceButton(Mood.LOW, text(context, R.string.mood_low), text(context, R.string.mood_a11y_low), {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                model.log(Mood.LOW)
            })
            FaceButton(Mood.OKAY, text(context, R.string.mood_okay), text(context, R.string.mood_a11y_okay), {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                model.log(Mood.OKAY)
            })
            FaceButton(Mood.GOOD, text(context, R.string.mood_good), text(context, R.string.mood_a11y_good), {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                model.log(Mood.GOOD)
            })
        }
        if (state.lowRun) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text(context, R.string.lowrun_body))
                    TextButton(onClick = onHelplines) { Text(text(context, R.string.lowrun_action)) }
                    TextButton(onClick = model::dismissLowRun) { Text(text(context, R.string.lowrun_dismiss)) }
                }
            }
        }
        if (state.today.isEmpty()) {
            Text(text(context, R.string.today_empty))
        } else {
            state.today.forEach { entry ->
                Text(entryLine(context, entry), style = MaterialTheme.typography.bodyLarge)
            }
        }
        TextButton(onClick = onHistory) { Text(text(context, R.string.today_allEntries)) }
    }
    val follow = state.followUpId
    if (follow != null) {
        FollowUpSheet(app, state.tagMode, onMode = model::setMode, onDone = { tags, comment ->
            model.saveFollowUp(follow, tags, comment)
        }, onDismiss = {
            model.dismissFollowUp()
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowUpSheet(
    app: AppContainer,
    mode: String,
    onMode: (String) -> Unit,
    onDone: (List<String>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var comment by remember { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text(context, R.string.today_saved), style = MaterialTheme.typography.headlineSmall)
            Text(text(context, R.string.today_followup_prompt))
            TagPicker(app.emotions, mode, tags, { tags = it }, onMode)
            if (tags.size >= 3) Text(text(context, R.string.tags_limit))
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 1000) comment = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text(context, R.string.tags_comment_placeholder)) },
            )
            Button(onClick = { onDone(tags, comment) }, modifier = Modifier.fillMaxWidth()) {
                Text(text(context, R.string.today_done))
            }
        }
    }
}

@Composable
fun HistoryScreen(model: HistoryViewModel, onEntry: (String) -> Unit, onBack: () -> Unit) {
    RefreshOnResume(model::refresh)
    val entries by model.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    ScreenColumn(text(context, R.string.history_title), onBack) {
        if (entries.isEmpty()) {
            Text(text(context, R.string.history_empty))
        }
        entries.groupBy { it.createdAt.atZone(zone).toLocalDate() }.forEach { (day, items) ->
            Text(day.format(formatter), style = MaterialTheme.typography.titleMedium)
            items.forEach { entry ->
                Text(
                    entryLine(context, entry),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntry(entry.id) }
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = entryLine(context, entry) },
                )
            }
        }
    }
}

@Composable
fun EntryDetailScreen(app: AppContainer, model: HistoryViewModel, places: PlacesViewModel, id: String, onBack: () -> Unit) {
    RefreshOnResume(model::refresh)
    RefreshOnResume(places::refresh)
    val entries by model.entries.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == id }
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    if (entry == null) {
        ScreenColumn(text(context, R.string.history_title), onBack) {
            Text(text(context, R.string.history_empty))
        }
        return
    }
    var mood by remember(entry.id, entry.updatedAt) { mutableStateOf(entry.mood) }
    var tags by remember(entry.id, entry.updatedAt) { mutableStateOf(entry.tags) }
    var comment by remember(entry.id, entry.updatedAt) { mutableStateOf(entry.comment.orEmpty()) }
    val settings by app.settings.settings.collectAsStateWithLifecycle(com.example.smileproc.data.UserSettings())
    ScreenColumn(text(context, R.string.entry_mood), onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mood.entries.forEach { face ->
                val label = moodLabel(context, face)
                FilterChip(selected = mood == face, onClick = { mood = face }, label = { Text(label) })
            }
        }
        Text(text(context, R.string.entry_tags))
        TagPicker(app.emotions, settings.tagPickerMode, tags, { tags = it }, { mode ->
            app.containerScopeSetMode(mode)
        })
        OutlinedTextField(
            value = comment,
            onValueChange = { if (it.length <= 1000) comment = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text(context, R.string.entry_note)) },
        )
        Text(text(context, R.string.entry_place) + ": " + placeName(context, entry, places))
        if (entry.location != null) {
            TextButton(onClick = { naming = true }) { Text(text(context, R.string.entry_namePlace)) }
        }
        Button(onClick = {
            model.save(entry.copy(mood = mood, tags = tags, comment = comment))
            onBack()
        }) { Text(text(context, R.string.entry_save)) }
        TextButton(onClick = { confirm = true }) { Text(text(context, R.string.entry_delete)) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(text(context, R.string.entry_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    model.delete(entry.id)
                    confirm = false
                    onBack()
                }) { Text(text(context, R.string.entry_delete_action)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(text(context, R.string.entry_cancel)) } },
        )
    }
    if (naming) {
        NamePlaceDialog(places, entry, onDismiss = { naming = false })
    }
}

private fun AppContainer.containerScopeSetMode(mode: String) {
    scope.launch { settings.update { it.copy(tagPickerMode = mode) } }
}

@Composable
fun WeekScreen(app: AppContainer, model: WeekViewModel, onHelplines: () -> Unit, onName: (String) -> Unit) {
    RefreshOnResume(model::refresh)
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text(context, R.string.week_title), style = MaterialTheme.typography.headlineMedium)
        val latest = state.cards.firstOrNull()
        if (latest == null) {
            val zone = ZoneId.systemDefault()
            val next = CardPeriods.nextCardTime(
                Instant.now(),
                zone.id,
                state.weekly.weekday,
                state.weekly.hour,
                state.weekly.minute,
            )
            Text(
                text(context, 
                    R.string.week_empty,
                    weekdayLabel(context, next.dayOfWeek.value),
                    "%02d:%02d".format(next.hour, next.minute),
                ),
            )
        } else {
            CardView(context, app, latest, state, onHelplines, onName)
            if (state.cards.size > 1) {
                Text(text(context, R.string.week_past), style = MaterialTheme.typography.titleMedium)
                state.cards.drop(1).forEach { card ->
                    Text(card.periodEnd, style = MaterialTheme.typography.bodyMedium)
                    CardView(context, app, card, state, onHelplines, onName)
                }
            }
        }
    }
}

@Composable
private fun CardView(
    context: android.content.Context,
    app: AppContainer,
    card: StoredWeeklyCard,
    state: WeekViewModel.State,
    onHelplines: () -> Unit,
    onName: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            card.days.forEachIndexed { index, counts ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(weekdayLabel(context, index + 1).take(1))
                    Text(counts.joinToString("/"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (card.variant == "notEnoughData") Text(text(context, R.string.week_notEnough))
        if (card.variant == "firstWeek") Text(text(context, R.string.week_firstWeek))
        if (card.isLowWeek) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text(context, R.string.week_lowWeek_body))
                    Text(text(context, R.string.week_lowWeek_support))
                    TextButton(onClick = onHelplines) { Text(text(context, R.string.week_lowWeek_action)) }
                }
            }
        }
        card.items.forEach { item ->
            val library = app.library.firstOrNull { it.id == item.libraryID }
            val place = placeForItem(context, item, state)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.confidence != "pattern") Text(text(context, R.string.week_earlyHint))
                    if (library != null) {
                        Text(libraryText(library.title, item, place, context), style = MaterialTheme.typography.titleMedium)
                        Text(observation(context, item, place))
                        Text(text(context, R.string.week_common_label))
                        Text(libraryText(library.common, item, place, context))
                        Text(libraryText(library.tip, item, place, context))
                        Text(libraryText(library.question, item, place, context))
                    }
                    if (item.clusterID != null && place == text(context, R.string.history_place_unnamed)) {
                        TextButton(onClick = { onName(item.clusterID) }) { Text(text(context, R.string.week_namePlace)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PlacesScreen(model: PlacesViewModel, focusCluster: String?, onBack: () -> Unit) {
    RefreshOnResume(model::refresh)
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolved = remember(state) { PlaceResolver.resolve(state.entries, state.labels, model.config) }
    var naming by remember { mutableStateOf(focusCluster) }
    var confirmRemove by remember { mutableStateOf(false) }
    ScreenColumn(text(context, R.string.settings_places), onBack) {
        if (resolved.clusters.isEmpty()) Text(text(context, R.string.place_empty))
        resolved.clusters.forEach { cluster ->
            val label = state.labels.firstOrNull { it.id == cluster.labelID }
            val title = label?.name ?: unnamedDescription(context, cluster, state.entries)
            Text(
                title,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { naming = cluster.id }
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = text(context, R.string.place_a11y_cluster, cluster.memberIDs.size) },
            )
            if (label != null) {
                TextButton(onClick = { model.deleteLabel(label.id) }) { Text(text(context, R.string.place_deleteLabel)) }
            }
        }
        TextButton(onClick = { confirmRemove = true }) { Text(text(context, R.string.place_removeLocations)) }
    }
    val clusterId = naming
    if (clusterId != null) {
        val entry = state.entries.firstOrNull { resolved.entryClusters[it.id] == clusterId }
        if (entry != null) {
            NamePlaceDialog(model, entry, onDismiss = { naming = null })
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(text(context, R.string.place_removeLocations_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    model.removeLocations()
                    confirmRemove = false
                }) { Text(text(context, R.string.place_removeLocations_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(text(context, R.string.entry_cancel)) } },
        )
    }
}


@Composable
fun NamePlaceDialog(model: PlacesViewModel, entry: Entry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val resolved = PlaceResolver.resolve(state.entries, state.labels, model.config)
    val clusterId = resolved.entryClusters[entry.id]
    val cluster = resolved.clusters.firstOrNull { it.id == clusterId }
    var custom by remember { mutableStateOf("") }
    var merge by remember { mutableStateOf<PlaceLabel?>(null) }
    var pending by remember { mutableStateOf<Pair<String, PlaceKind>?>(null) }
    if (cluster == null) {
        LaunchedEffect(entry.id) { onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text(context, R.string.place_name_prompt)) },
        text = {
            Column {
                TextButton(onClick = { pending = text(context, R.string.place_home) to PlaceKind.HOME }) {
                    Text(text(context, R.string.place_home))
                }
                TextButton(onClick = { pending = text(context, R.string.place_work) to PlaceKind.WORK }) {
                    Text(text(context, R.string.place_work))
                }
                OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text(text(context, R.string.place_name_custom)) })
                TextButton(onClick = {
                    if (custom.isNotBlank()) pending = custom.trim() to PlaceKind.OTHER
                }) { Text(text(context, R.string.entry_save)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text(context, R.string.common_close)) } },
    )
    val request = pending
    if (request != null && merge == null) {
        val nearby = PlaceResolver.labelWithinRadius(
            cluster.centroidLatitude,
            cluster.centroidLongitude,
            state.labels,
            model.config.placeRadiusMeters,
        )
        if (nearby != null) {
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text(text(context, R.string.place_merge_ask, nearby.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        model.createOrMerge(cluster.id, request.first, request.second, nearby)
                        pending = null
                        onDismiss()
                    }) { Text(text(context, R.string.place_merge_yes)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        model.createOrMerge(cluster.id, request.first, request.second, null)
                        pending = null
                        onDismiss()
                    }) { Text(text(context, R.string.place_merge_no)) }
                },
            )
        } else {
            LaunchedEffect(request) {
                model.createOrMerge(cluster.id, request.first, request.second, null)
                pending = null
                onDismiss()
            }
        }
    }
}

@Composable
fun SettingsScreen(
    model: SettingsViewModel,
    onBack: (() -> Unit)?,
    onReminders: () -> Unit,
    onWeekly: () -> Unit,
    onPlaces: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPrivacy: () -> Unit,
    onDelete: () -> Unit,
    onAbout: () -> Unit,
    onDebug: (() -> Unit)?,
) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canLock = Biometrics.available(context)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        model.setCapture(granted.values.any { it })
    }
    ScreenColumn(text(context, R.string.tab_settings), onBack) {
        SettingsRow(text(context, R.string.settings_reminders), onReminders)
        SettingsRow(text(context, R.string.settings_weeklyCardTime), onWeekly)
        SettingsRow(text(context, R.string.settings_places), onPlaces)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(text(context, R.string.settings_location))
                Text(text(context, R.string.settings_location_detail), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = settings.captureLocation,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        permission.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    } else {
                        model.setCapture(false)
                    }
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(text(context, R.string.settings_appLock))
                if (!canLock) Text(text(context, R.string.settings_appLock_unavailable))
            }
            Switch(
                checked = settings.appLockEnabled && canLock,
                onCheckedChange = { model.setLock(it) },
                enabled = canLock,
            )
        }
        SettingsRow(text(context, R.string.settings_export), onExport)
        SettingsRow(text(context, R.string.settings_import), onImport)
        SettingsRow(text(context, R.string.settings_privacy), onPrivacy)
        SettingsRow(text(context, R.string.settings_deleteAll), onDelete)
        SettingsRow(text(context, R.string.settings_about), onAbout)
        if (onDebug != null) SettingsRow(text(context, R.string.debug_title), onDebug)
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .semantics { contentDescription = label },
        style = MaterialTheme.typography.titleMedium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(model: SettingsViewModel, onBack: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ReminderSpec?>(null) }
    ScreenColumn(text(context, R.string.reminders_title), onBack) {
        if (settings.reminders.isEmpty()) Text(text(context, R.string.reminders_empty))
        settings.reminders.forEach { reminder ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d:%02d".format(reminder.hour, reminder.minute), Modifier.weight(1f))
                TextButton(onClick = {
                    model.setReminders(settings.reminders.filter { it.id != reminder.id })
                }) { Text(text(context, R.string.reminders_remove)) }
            }
        }
        if (settings.reminders.size >= 6) {
            Text(text(context, R.string.reminders_limit))
        } else {
            Button(onClick = {
                editing = ReminderSpec(UUID.randomUUID().toString(), 9, 0, listOf(1, 2, 3, 4, 5, 6, 7))
            }) { Text(text(context, R.string.reminders_add)) }
        }
    }
    val draft = editing
    if (draft != null) {
        ReminderDialog(draft, onDismiss = { editing = null }, onSave = { saved ->
            model.setReminders(settings.reminders + saved)
            editing = null
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(initial: ReminderSpec, onDismiss: () -> Unit, onSave: (ReminderSpec) -> Unit) {
    val context = LocalContext.current
    val picker = rememberTimePickerState(initial.hour, initial.minute, true)
    var days by remember { mutableStateOf(initial.weekdays.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text(context, R.string.reminders_time)) },
        text = {
            Column {
                TimePicker(picker)
                Text(text(context, R.string.reminders_days))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(weekdayLabel(context, day).take(1)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(hour = picker.hour, minute = picker.minute, weekdays = days.sorted()))
            }) { Text(text(context, R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text(context, R.string.entry_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyTimeScreen(model: SettingsViewModel, onBack: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var day by remember(settings.weeklyCard) { mutableStateOf(settings.weeklyCard.weekday) }
    val picker = rememberTimePickerState(settings.weeklyCard.hour, settings.weeklyCard.minute, true)
    ScreenColumn(text(context, R.string.weeklyCard_time_title), onBack) {
        Text(text(context, R.string.weeklyCard_time_detail))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { value ->
                FilterChip(selected = day == value, onClick = { day = value }, label = { Text(weekdayLabel(context, value).take(1)) })
            }
        }
        TimePicker(picker)
        Button(onClick = { model.setWeekly(com.example.smileproc.core.WeeklyCardTime(day, picker.hour, picker.minute)) }) {
            Text(text(context, R.string.settings_done))
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, onHelplines: () -> Unit) {
    val context = LocalContext.current
    ScreenColumn(text(context, R.string.privacy_title), onBack) {
        Text(text(context, R.string.privacy_body))
        Text(text(context, R.string.privacy_exportHint))
        TextButton(onClick = onHelplines) { Text(text(context, R.string.helplines_title)) }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenColumn(text(context, R.string.settings_about), onBack) {
        Text(text(context, R.string.about_body))
        Text(text(context, R.string.about_version, com.example.smileproc.BuildConfig.VERSION_NAME))
        Text(text(context, R.string.about_pending))
    }
}

@Composable
fun HelplinesScreen(app: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val country = LocalLocale.current.platformLocale.country.uppercase(Locale.US)
    val lines = app.helplines.regions[country].orEmpty()
    val emergency = app.helplines.emergency[country] ?: app.helplines.emergency["default"].orEmpty()
    ScreenColumn(text(context, R.string.helplines_title), onBack) {
        Text(text(context, R.string.helplines_region, country.ifBlank { emergency }))
        lines.forEach { line ->
            Text(line.name, style = MaterialTheme.typography.titleMedium)
            if (!line.verified) Text(text(context, R.string.helplines_unverified))
            line.phone?.let { phone ->
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")))
                }) { Text(text(context, R.string.helplines_call)) }
            }
            line.sms?.let { sms ->
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${sms.filter { it.isDigit() || it == '+' }}")))
                }) { Text(text(context, R.string.helplines_text)) }
            }
        }
        Text(text(context, R.string.helplines_emergency), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergency")))
        }) { Text(emergency) }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.helplines.directory.url)))
        }) { Text(text(context, R.string.helplines_directory)) }
    }
}

@Composable
fun DeleteScreen(app: AppContainer, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ScreenColumn(text(context, R.string.deleteAll_title), onBack) {
        Text(text(context, R.string.deleteAll_body))
        var confirm by remember { mutableStateOf(false) }
        Button(onClick = { confirm = true }) { Text(text(context, R.string.deleteAll_action)) }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(text(context, R.string.deleteAll_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            app.wipe()
                            onDone()
                        }
                    }) { Text(text(context, R.string.deleteAll_action)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(text(context, R.string.entry_cancel)) } },
            )
        }
    }
}

@Composable
fun ExportScreen(app: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf<ByteArray?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = payload
        if (uri != null && bytes != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
    val scope = rememberCoroutineScope()
    ScreenColumn(text(context, R.string.export_title), onBack) {
        Text(text(context, R.string.export_warning))
        OutlinedTextField(password, { password = it }, label = { Text(text(context, R.string.export_password)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirm, { confirm = it }, label = { Text(text(context, R.string.export_confirm)) }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it) }
        if (busy) {
            Text(text(context, R.string.export_progress))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Button(onClick = {
            error = when {
                password.length < ExportFormat.MIN_PASSWORD_LENGTH -> text(context, R.string.export_tooShort)
                password != confirm -> text(context, R.string.export_mismatch)
                else -> null
            }
            if (error != null) return@Button
            busy = true
            scope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    val settings = app.settings.snapshot()
                    val document = ExportDocument(
                        exportedAt = Instant.now(),
                        app = ExportApp("android", com.example.smileproc.BuildConfig.VERSION_NAME),
                        entries = app.repository.entries(),
                        places = app.repository.places(),
                        settings = settings.exportSettings(),
                    )
                    val plain = com.example.smileproc.core.CanonicalJson.exportDocument(document).toByteArray(Charsets.UTF_8)
                    val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                    val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
                    val envelope = ExportFormat.seal(password, salt, nonce, ExportFormat.PRODUCTION_ITERATIONS, plain)
                    envelopeText(envelope).toByteArray(Charsets.UTF_8)
                }
                payload = bytes
                busy = false
                create.launch("smileproc-export.smileproc")
            }
        }, enabled = !busy) { Text(text(context, R.string.export_action)) }
    }
}

@Composable
fun ImportScreen(app: AppContainer, onBack: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }
    val scope = rememberCoroutineScope()
    ScreenColumn(text(context, R.string.import_title), onBack) {
        Button(onClick = { open.launch(arrayOf("*/*")) }) { Text(text(context, R.string.import_action)) }
        OutlinedTextField(password, { password = it }, label = { Text(text(context, R.string.import_password)) }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it) }
        if (busy) {
            Text(text(context, R.string.import_progress))
            CircularProgressIndicator()
        }
        Button(onClick = {
            val file = bytes ?: return@Button
            busy = true
            error = null
            scope.launch {
                val opened = withContext(Dispatchers.Default) {
                    try {
                        val envelope = ExportFormat.parseEnvelope(file.toString(Charsets.UTF_8))
                        val plain = ExportFormat.open(envelope, password)
                        com.example.smileproc.core.CanonicalJson.parseExport(plain.toString(Charsets.UTF_8))
                    } catch (_: Exception) {
                        null
                    }
                }
                if (opened == null) {
                    error = text(context, R.string.import_wrongPassword)
                    busy = false
                } else {
                    app.repository.importDocument(opened)
                    app.settings.applyExport(opened.settings)
                    app.reminders.rescheduleAll()
                    app.refreshCard()
                    busy = false
                    onFinished()
                }
            }
        }, enabled = bytes != null && !busy) { Text(text(context, R.string.import_action)) }
    }
}

@Composable
fun OnboardingScreen(model: SettingsViewModel, onFinished: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    var capture by remember { mutableStateOf(false) }
    var lock by remember { mutableStateOf(true) }
    var reminders by remember { mutableStateOf<List<ReminderSpec>>(emptyList()) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        capture = result.values.any { it }
        page = 2
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        page = 3
    }
    val canLock = Biometrics.available(context)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (page) {
            0 -> {
                Text(text(context, R.string.onboarding_privacy_title), style = MaterialTheme.typography.headlineMedium)
                Text(text(context, R.string.onboarding_privacy_body))
                Button(onClick = { page = 1 }) { Text(text(context, R.string.onboarding_privacy_next)) }
            }
            1 -> {
                Text(text(context, R.string.onboarding_location_title), style = MaterialTheme.typography.headlineMedium)
                Text(text(context, R.string.onboarding_location_body))
                Button(onClick = {
                    locationPermission.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }) { Text(text(context, R.string.onboarding_location_allow)) }
                TextButton(onClick = { page = 2 }) { Text(text(context, R.string.onboarding_location_skip)) }
            }
            2 -> {
                Text(text(context, R.string.onboarding_reminders_title), style = MaterialTheme.typography.headlineMedium)
                Text(text(context, R.string.onboarding_reminders_body))
                Button(onClick = {
                    reminders = listOf(ReminderSpec(UUID.randomUUID().toString(), 9, 0, listOf(1, 2, 3, 4, 5)))
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        page = 3
                    }
                }) { Text(text(context, R.string.onboarding_reminders_allow)) }
                TextButton(onClick = { page = 3 }) { Text(text(context, R.string.onboarding_reminders_skip)) }
            }
            else -> {
                Text(text(context, R.string.onboarding_widget_title), style = MaterialTheme.typography.headlineMedium)
                Text(text(context, R.string.onboarding_widget_android))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text(context, R.string.onboarding_widget_androidLock), Modifier.weight(1f))
                    Switch(checked = lock && canLock, onCheckedChange = { lock = it }, enabled = canLock)
                }
                if (!canLock) Text(text(context, R.string.settings_appLock_unavailable))
                Button(onClick = {
                    model.finishOnboarding(lock, capture, reminders)
                    onFinished()
                }) { Text(text(context, R.string.onboarding_finish)) }
            }
        }
    }
}

@Composable
fun RecoveryScreen(app: AppContainer, onImport: () -> Unit, onFresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text(context, R.string.recovery_title), style = MaterialTheme.typography.headlineMedium)
        Text(text(context, R.string.recovery_body))
        Button(onClick = onImport) { Text(text(context, R.string.recovery_import)) }
        TextButton(onClick = {
            scope.launch {
                app.startFreshAfterRecovery()
                onFresh()
            }
        }) { Text(text(context, R.string.recovery_fresh)) }
    }
}

@Composable
fun DebugScreen(model: DebugViewModel, onBack: () -> Unit) {
    val rows by model.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.load() }
    ScreenColumn(text(context, R.string.debug_title), onBack) {
        Button(onClick = model::seed) { Text(text(context, R.string.debug_seed)) }
        Text(text(context, R.string.debug_patterns), style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            Text(
                "${row.id} nSeg ${row.nSegment} nRest ${row.nRest} mean ${row.meanSegment}/${row.meanRest} diff ${row.diff} z ${row.z} score ${row.score} ${row.confidence}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(text(context, R.string.debug_spikes), style = MaterialTheme.typography.titleMedium)
        Text(text(context, R.string.debug_spikeNotes))
    }
}

@Composable
fun LockCover(onUnlock: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text(context, R.string.lock_title), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 16.dp)) {
            Text(text(context, R.string.lock_action))
        }
    }
}

@Composable
private fun RefreshOnResume(refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, refresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenColumn(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text(text(LocalContext.current, R.string.common_back)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(24.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = { content() },
        )
    }
}

private fun entryLine(context: android.content.Context, entry: Entry): String {
    val zone = ZoneId.of(entry.timeZoneID)
    val time = entry.createdAt.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    val mood = moodLabel(context, entry.mood)
    val tags = entry.tags.joinToString(" ") { tagLabel(context, it) }
    val note = entry.comment?.take(80).orEmpty()
    return listOf(time, mood, tags, note).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun moodLabel(context: android.content.Context, mood: Mood): String = when (mood) {
    Mood.LOW -> text(context, R.string.mood_low)
    Mood.OKAY -> text(context, R.string.mood_okay)
    Mood.GOOD -> text(context, R.string.mood_good)
}

private fun placeName(context: android.content.Context, entry: Entry, model: PlacesViewModel): String {
    val state = model.state.value
    val resolved = PlaceResolver.resolve(state.entries, state.labels, model.config)
    val cluster = resolved.entryClusters[entry.id] ?: return ""
    val label = state.labels.firstOrNull { it.id == resolved.clusters.firstOrNull { item -> item.id == cluster }?.labelID }
    return label?.name ?: text(context, R.string.history_place_unnamed)
}

private fun placeForItem(context: android.content.Context, item: StoredCardItem, state: WeekViewModel.State): String {
    val clusterId = item.clusterID ?: return ""
    if (clusterId.startsWith("p:")) {
        val label = state.places.firstOrNull { it.id == clusterId.removePrefix("p:") }
        if (label != null) return label.name
        return text(context, R.string.history_place_gone)
    }
    return text(context, R.string.history_place_unnamed)
}

private fun unnamedDescription(context: android.content.Context, cluster: Cluster, entries: List<Entry>): String {
    val members = entries.filter { it.id in cluster.memberIDs }
    val daypart = members.groupingBy { Instants.daypart(it.createdAt, it.timeZoneID) }.eachCount().maxByOrNull { it.value }?.key ?: "morning"
    val weekday = members.count { Instants.isoWeekday(it.createdAt, it.timeZoneID) in 1..5 }
    val whenText = if (weekday >= members.size - weekday) {
        text(context, R.string.place_when_weekday, daypartLabel(context, daypart))
    } else {
        text(context, R.string.place_when_weekend, daypartLabel(context, daypart))
    }
    return text(context, R.string.place_unnamed_description, members.size, whenText)
}

private fun envelopeText(envelope: com.example.smileproc.core.ExportEnvelope): String = buildString {
    append("{\n")
    append("  \"format\": \"${envelope.format}\",\n")
    append("  \"version\": ${envelope.version},\n")
    append("  \"kdf\": {\"name\": \"${envelope.kdfName}\", \"iterations\": ${envelope.iterations}, \"salt\": \"${envelope.saltBase64()}\"},\n")
    append("  \"cipher\": \"${envelope.cipher}\",\n")
    append("  \"payload\": \"${envelope.payloadBase64()}\"\n")
    append("}\n")
}
