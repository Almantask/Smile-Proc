package com.example.smileproc.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.AppContainer
import com.example.smileproc.R
import com.example.smileproc.SafeLog
import com.example.smileproc.StorageBundle
import com.example.smileproc.core.CardPeriod
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.core.ReminderPause
import com.example.smileproc.core.WeeklyCardBuilder
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.core.export.ExportException
import com.example.smileproc.data.DeleteAllManager
import com.example.smileproc.data.ExportImportManager
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.notify.CheckInReceiver
import com.example.smileproc.ui.components.GlassBottomNavBar
import com.example.smileproc.ui.components.SmileProcBackground
import com.example.smileproc.ui.history.HistoryScreen
import com.example.smileproc.ui.places.PatternConfigCatalog
import com.example.smileproc.ui.places.PlaceNamingSheet
import com.example.smileproc.ui.places.PlacesScreen
import com.example.smileproc.ui.places.resolvePlaces
import com.example.smileproc.ui.privacy.PrivacyScreen
import com.example.smileproc.ui.settings.DeleteAllDialog
import com.example.smileproc.ui.settings.ExportDialog
import com.example.smileproc.ui.settings.ImportPasswordDialog
import com.example.smileproc.ui.settings.LockScreenCheckInScreen
import com.example.smileproc.ui.settings.RemindersScreen
import com.example.smileproc.ui.settings.SettingsScreen
import com.example.smileproc.ui.support.SupportScreen
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.today.TodayScreen
import com.example.smileproc.ui.week.PatternLibraryCatalog
import com.example.smileproc.ui.week.WeekScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AppTab {
    TODAY,
    WEEK,
    SETTINGS,
}

@Composable
fun MainApp(
    container: AppContainer,
    storage: StorageBundle,
    modifier: Modifier = Modifier,
    openNotificationFollowUp: Boolean = false,
    onNotificationFollowUpHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var showingHistory by rememberSaveable { mutableStateOf(false) }
    var activeSubScreen by rememberSaveable { mutableStateOf<String?>(null) } // "reminders", "lockscreen", "places", "support"
    var namingClusterId by rememberSaveable { mutableStateOf<String?>(null) }

    val entries by storage.entryRepository.observeAllNewestFirst().collectAsStateWithLifecycle(initialValue = emptyList())
    val placeLabels by storage.placeRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val places = remember(context, entries, placeLabels) {
        resolvePlaces(context, entries, placeLabels)
    }

    val entryExtrasList by storage.entryExtrasRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val entryExtrasById = remember(entryExtrasList) { entryExtrasList.associateBy { it.entryId } }
    val customTags by storage.customTagRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    val weeklyCards by storage.weeklyCardRepository.observeAllNewestFirst().collectAsStateWithLifecycle(initialValue = emptyList())
    val weeklyCardTime by container.settingsRepository.weeklyCardTime.collectAsStateWithLifecycle(initialValue = WeeklyCardTime.DEFAULT)
    val lastSeenWeeklyCardPeriodStart by container.settingsRepository.lastSeenWeeklyCardPeriodStart.collectAsStateWithLifecycle(initialValue = null)

    val lowRunDismissedEntryId by container.settingsRepository.lowRunDismissedEntryId.collectAsStateWithLifecycle(initialValue = null)
    val captureLocation by container.settingsRepository.captureLocation.collectAsStateWithLifecycle(initialValue = true)
    val standing by container.settingsRepository.standing.collectAsStateWithLifecycle(initialValue = true)
    val tagPickerMode by container.settingsRepository.tagPickerMode.collectAsStateWithLifecycle(initialValue = TagPickerMode.DETAILED)
    val appLock by container.settingsRepository.appLock.collectAsStateWithLifecycle(initialValue = false)
    val reminderPause by container.settingsRepository.reminderPause.collectAsStateWithLifecycle(initialValue = ReminderPause.NONE)

    var entriesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(storage) {
        storage.entryRepository.observeAllNewestFirst().collect { entriesLoaded = true }
    }
    var lastNotificationLoaded by remember { mutableStateOf(false) }
    var lastNotificationPair by remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(container) {
        container.settingsRepository.lastNotificationEntry.collect { pair ->
            lastNotificationPair = pair
            lastNotificationLoaded = true
        }
    }
    val recentNotificationEntryId = remember(lastNotificationPair, entries) {
        val pair = lastNotificationPair ?: return@remember null
        val ageMs = Instant.now().toEpochMilli() - pair.second
        if (ageMs in 0..3_600_000L && entries.any { it.id == pair.first }) pair.first else null
    }

    LaunchedEffect(openNotificationFollowUp, lastNotificationLoaded, entriesLoaded, recentNotificationEntryId) {
        if (openNotificationFollowUp) {
            currentTab = AppTab.TODAY
            showingHistory = false
            activeSubScreen = null
            if (lastNotificationLoaded && entriesLoaded && recentNotificationEntryId == null) {
                onNotificationFollowUpHandled()
            }
        }
    }

    val saveEntryPreservingLocation: (Entry, Boolean) -> Unit = { entry, markRatedInApp ->
        container.applicationScope.launch {
            val existing = storage.entryRepository.getById(entry.id)
            val toSave = if (existing?.location != null && entry.location == null) {
                entry.copy(location = existing.location)
            } else {
                entry
            }
            storage.entryRepository.save(toSave)
            if (markRatedInApp) {
                CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.RatedInApp)
            }
        }
    }
    val deleteEntryAndExtras: (String) -> Unit = { id ->
        container.applicationScope.launch {
            storage.entryRepository.deleteById(id)
            storage.entryExtrasRepository.deleteByEntryId(id)
        }
    }
    val saveEntryExtras: (EntryExtras) -> Unit = { extras ->
        container.applicationScope.launch {
            storage.entryExtrasRepository.save(extras)
        }
    }
    val createCustomTag: (CustomTag) -> Unit = { tag ->
        container.applicationScope.launch {
            storage.customTagRepository.save(tag)
        }
    }
    val deleteCustomTag: (String) -> Unit = { id ->
        container.applicationScope.launch {
            storage.customTagRepository.deleteById(id)
        }
    }
    val toggleTagPickerMode: () -> Unit = {
        container.applicationScope.launch {
            val next = if (tagPickerMode == TagPickerMode.GRADUAL) TagPickerMode.DETAILED else TagPickerMode.GRADUAL
            container.settingsRepository.setTagPickerMode(next)
        }
    }

    val exportDoneMessage = stringResource(R.string.export_done)
    val exportFailedMessage = stringResource(R.string.export_failed)
    val importWrongPasswordMessage = stringResource(R.string.import_wrong_password)
    val importFailedMessage = stringResource(R.string.import_failed)
    val errorLogsExportedMessage = stringResource(R.string.error_logs_exported)
    val errorLogsExportFailedMessage = stringResource(R.string.error_logs_export_failed)

    val snackbarHostState = remember { SnackbarHostState() }
    val exportImportManager = remember(context, container) { ExportImportManager(context, container) }

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }

    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    var showDeleteAllDialog by rememberSaveable { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && exportPassword != null) {
            val pwd = exportPassword!!
            isExporting = true
            container.applicationScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        exportImportManager.exportToStream(stream, pwd)
                    }
                    isExporting = false
                    showExportDialog = false
                    exportPassword = null
                    snackbarHostState.showSnackbar(exportDoneMessage)
                } catch (e: Exception) {
                    isExporting = false
                    showExportDialog = false
                    exportPassword = null
                    snackbarHostState.showSnackbar(exportFailedMessage)
                }
            }
        } else {
            isExporting = false
            showExportDialog = false
            exportPassword = null
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportDialog = true
            importErrorMessage = null
        }
    }

    val createErrorLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            container.applicationScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        container.errorLogStore.exportDecrypted(stream)
                    }
                    SafeLog.event(SafeLog.Event.ERROR_LOGS_EXPORTED)
                    snackbarHostState.showSnackbar(errorLogsExportedMessage)
                } catch (e: Exception) {
                    SafeLog.failure(SafeLog.Event.ERROR_LOGS_EXPORT_FAILED, e)
                    snackbarHostState.showSnackbar(errorLogsExportFailedMessage)
                }
            }
        }
    }

    // Build weekly card on first open after card time if not yet built
    LaunchedEffect(entries, weeklyCardTime) {
        // Storage can be retired under us (Delete all, Start fresh): a failed build is logged,
        // never allowed to crash the UI (QA H-3).
        try {
            val config = PatternConfigCatalog.get(context)
            if (entries.size >= config.firstCardMinEntries) {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val period = CardPeriod.resolve(now, zone, weeklyCardTime)
                val existing = storage.weeklyCardRepository.getByPeriodStart(period.periodStart)
                if (existing == null) {
                    val emotionTags = EmotionTagsCatalog.get(context)
                    val library = PatternLibraryCatalog.get(context)
                    val buildResult = WeeklyCardBuilder.build(
                        entries = entries,
                        labels = placeLabels,
                        now = now,
                        zone = zone,
                        config = config,
                        emotionTags = emotionTags,
                        library = library,
                        cardTime = weeklyCardTime,
                    )
                    storage.weeklyCardRepository.save(buildResult.card)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.failure(SafeLog.Event.WEEKLY_CARD_BUILD_FAILED, e)
        }
    }

    val latestCard = weeklyCards.firstOrNull()
    val hasUnseenCard = latestCard != null && (lastSeenWeeklyCardPeriodStart == null || latestCard.periodStart.isAfter(lastSeenWeeklyCardPeriodStart))

    if (currentTab == AppTab.WEEK && latestCard != null && hasUnseenCard) {
        LaunchedEffect(latestCard.periodStart) {
            container.settingsRepository.setLastSeenWeeklyCardPeriodStart(latestCard.periodStart)
        }
    }

    val colors = LocalSmileProcColors.current
    val latestMood = entries.firstOrNull()?.mood
    SmileProcBackground(
        latestMood = latestMood,
        modifier = modifier,
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = colors.dialogContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        bottomBar = {
            if (!showingHistory && activeSubScreen == null) {
                GlassBottomNavBar(
                    currentTab = currentTab,
                    hasUnseenCard = hasUnseenCard,
                    onSelectTab = { currentTab = it },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (activeSubScreen == "support") {
                SupportScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            } else if (activeSubScreen == "privacy") {
                PrivacyScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            } else {
                when (currentTab) {
                AppTab.TODAY -> {
                    if (showingHistory) {
                        HistoryScreen(
                            entries = entries,
                            places = places,
                            tagPickerMode = tagPickerMode,
                            onTagPickerModeToggle = toggleTagPickerMode,
                            onSaveEntry = { entry -> saveEntryPreservingLocation(entry, true) },
                            onDeleteEntry = deleteEntryAndExtras,
                            onSavePlaceLabel = { label ->
                                container.applicationScope.launch {
                                    storage.placeRepository.save(label)
                                }
                            },
                            onNavigateBack = { showingHistory = false },
                            placeLabels = placeLabels,
                            entryExtrasById = entryExtrasById,
                            customTags = customTags,
                            onSaveEntryExtras = saveEntryExtras,
                            onCreateCustomTag = createCustomTag,
                            onDeleteCustomTag = deleteCustomTag,
                        )
                    } else {
                        TodayScreen(
                            entries = entries,
                            places = places,
                            placeLabels = placeLabels,
                            lowRunDismissedEntryId = lowRunDismissedEntryId,
                            tagPickerMode = tagPickerMode,
                            onTagPickerModeToggle = toggleTagPickerMode,
                            onSaveEntry = { entry -> saveEntryPreservingLocation(entry, true) },
                            onUpdateEntry = { entry -> saveEntryPreservingLocation(entry, false) },
                            onUpdateEntryDetails = { id, tags, comment ->
                                container.applicationScope.launch {
                                    val existing = storage.entryRepository.getById(id)
                                    if (existing != null) {
                                        storage.entryRepository.save(
                                            existing.copy(
                                                tags = tags,
                                                comment = comment,
                                                updatedAt = Instant.now(),
                                            )
                                        )
                                    }
                                }
                            },
                            onDeleteEntry = deleteEntryAndExtras,
                            onSavePlaceLabel = { label ->
                                container.applicationScope.launch {
                                    storage.placeRepository.save(label)
                                }
                            },
                            onDismissLowRun = { dismissedId ->
                                container.applicationScope.launch {
                                    container.settingsRepository.setLowRunDismissedEntryId(dismissedId)
                                }
                            },
                            onResetLowRunDismissed = {
                                container.applicationScope.launch {
                                    container.settingsRepository.setLowRunDismissedEntryId(null)
                                }
                            },
                            onCaptureLocationForEntry = { entryId ->
                                container.applicationScope.launch {
                                    val fix = container.locationClient.captureInAppFix()
                                    if (fix != null) {
                                        val existing = storage.entryRepository.getById(entryId)
                                        if (existing != null) {
                                            storage.entryRepository.save(
                                                existing.copy(location = fix, updatedAt = Instant.now())
                                            )
                                        }
                                    }
                                }
                            },
                            onNavigateToHistory = { showingHistory = true },
                            onNavigateToSupport = { activeSubScreen = "support" },
                            entryExtrasById = entryExtrasById,
                            customTags = customTags,
                            onSaveEntryExtras = saveEntryExtras,
                            onCreateCustomTag = createCustomTag,
                            onDeleteCustomTag = deleteCustomTag,
                            recentNotificationEntryId = recentNotificationEntryId,
                            openRecentFollowUp = openNotificationFollowUp && recentNotificationEntryId != null,
                            onClearRecentNotificationFollowUp = {
                                onNotificationFollowUpHandled()
                                container.applicationScope.launch {
                                    container.settingsRepository.clearLastNotificationEntry()
                                }
                            },
                        )
                    }
                }
                AppTab.WEEK -> {
                    WeekScreen(
                        entries = entries,
                        places = places,
                        placeLabels = placeLabels,
                        weeklyCards = weeklyCards,
                        weeklyCardTime = weeklyCardTime,
                        entryExtrasById = entryExtrasById,
                        customTags = customTags,
                        tagPickerMode = tagPickerMode,
                        onTagPickerModeToggle = toggleTagPickerMode,
                        onSaveEntry = { entry -> saveEntryPreservingLocation(entry, false) },
                        onDeleteEntry = deleteEntryAndExtras,
                        onSavePlaceLabel = { label ->
                            container.applicationScope.launch {
                                storage.placeRepository.save(label)
                            }
                        },
                        onSaveEntryExtras = saveEntryExtras,
                        onCreateCustomTag = createCustomTag,
                        onDeleteCustomTag = deleteCustomTag,
                        onNavigateToSupport = { activeSubScreen = "support" },
                    )
                }
                AppTab.SETTINGS -> {
                    when (activeSubScreen) {
                        "reminders" -> {
                            RemindersScreen(
                                container = container,
                                onNavigateBack = { activeSubScreen = null },
                            )
                        }
                        "lockscreen" -> {
                            LockScreenCheckInScreen(
                                container = container,
                                onNavigateBack = { activeSubScreen = null },
                            )
                        }
                        "places" -> {
                            PlacesScreen(
                                entries = entries,
                                placeLabels = placeLabels,
                                places = places,
                                onSavePlaceLabel = { label ->
                                    container.applicationScope.launch {
                                        storage.placeRepository.save(label)
                                    }
                                },
                                onDeletePlaceLabel = { labelId ->
                                    container.applicationScope.launch {
                                        storage.placeRepository.deleteById(labelId)
                                    }
                                },
                                onRemoveLocationFromAllEntries = {
                                    container.applicationScope.launch {
                                        val all = storage.entryRepository.getAllOldestFirst()
                                        val updated = all.map { it.copy(location = null, updatedAt = Instant.now()) }
                                        storage.entryRepository.saveAll(updated)
                                        storage.placeRepository.deleteAll()
                                    }
                                },
                                onNavigateBack = { activeSubScreen = null },
                            )
                        }
                        else -> {
                            SettingsScreen(
                                captureLocation = captureLocation,
                                onCaptureLocationChanged = { enabled ->
                                    container.applicationScope.launch {
                                        container.settingsRepository.setCaptureLocation(enabled)
                                    }
                                },
                                standing = standing,
                                onStandingChanged = { enabled ->
                                    container.applicationScope.launch {
                                        container.settingsRepository.setStanding(enabled)
                                        CheckInReceiver.dispatchMachineEvent(
                                            context,
                                            if (enabled) CheckInMachine.Event.StandingTurnedOn else CheckInMachine.Event.StandingTurnedOff,
                                        )
                                    }
                                },
                                weeklyCardTime = weeklyCardTime,
                                onWeeklyCardTimeChanged = { newTime ->
                                    container.applicationScope.launch {
                                        container.settingsRepository.setWeeklyCardTime(newTime)
                                    }
                                },
                                appLock = appLock,
                                onAppLockChanged = { enabled ->
                                    container.applicationScope.launch {
                                        container.settingsRepository.setAppLock(enabled)
                                    }
                                },
                                reminderPause = reminderPause,
                                onNavigateToReminders = { activeSubScreen = "reminders" },
                                onNavigateToLockScreenCheckIn = { activeSubScreen = "lockscreen" },
                                onNavigateToPlaces = { activeSubScreen = "places" },
                                onNavigateToPrivacy = { activeSubScreen = "privacy" },
                                onExportClick = { showExportDialog = true },
                                onImportClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                                onDeleteAllClick = { showDeleteAllDialog = true },
                                onExportErrorLogsClick = {
                                    val today = LocalDate.now().toString()
                                    createErrorLogsLauncher.launch("smileproc-error-logs-$today.json")
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    }
    }

    if (namingClusterId != null) {
        val clusterIdStr = namingClusterId!!
        val targetCluster = places.clusters[ClusterID(clusterIdStr)]
        if (targetCluster != null) {
            PlaceNamingSheet(
                centroid = targetCluster.centroid,
                existingLabels = placeLabels,
                allEntries = entries,
                onDismiss = { namingClusterId = null },
                onSaveLabel = { label ->
                    container.applicationScope.launch {
                        storage.placeRepository.save(label)
                        namingClusterId = null
                    }
                },
            )
        }
    }

    if (showExportDialog) {
        ExportDialog(
            isExporting = isExporting,
            onDismiss = {
                if (!isExporting) {
                    showExportDialog = false
                    exportPassword = null
                }
            },
            onConfirm = { pwd ->
                exportPassword = pwd
                val today = LocalDate.now().toString()
                createDocumentLauncher.launch("smileproc-$today.smileproc")
            },
        )
    }

    if (showImportDialog && pendingImportUri != null) {
        val uri = pendingImportUri!!
        ImportPasswordDialog(
            isImporting = isImporting,
            errorMessage = importErrorMessage,
            onDismiss = {
                if (!isImporting) {
                    showImportDialog = false
                    pendingImportUri = null
                    importErrorMessage = null
                }
            },
            onConfirm = { pwd ->
                isImporting = true
                importErrorMessage = null
                container.applicationScope.launch {
                    try {
                        val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                            exportImportManager.importFromStream(stream, pwd)
                        }
                        if (result != null) {
                            isImporting = false
                            showImportDialog = false
                            pendingImportUri = null
                            val msg = context.applicationContext.resources.getQuantityString(
                                R.plurals.import_done,
                                result.entriesImported,
                                result.entriesImported,
                            )
                            snackbarHostState.showSnackbar(msg)
                        }
                    } catch (e: ExportException) {
                        isImporting = false
                        importErrorMessage = if (e.reason == ExportException.Reason.WRONG_PASSWORD_OR_CORRUPT) {
                            importWrongPasswordMessage
                        } else {
                            importFailedMessage
                        }
                    } catch (e: Exception) {
                        isImporting = false
                        importErrorMessage = importFailedMessage
                    }
                }
            },
        )
    }

    if (showDeleteAllDialog) {
        DeleteAllDialog(
            onDismiss = { showDeleteAllDialog = false },
            onConfirm = {
                showDeleteAllDialog = false
                container.applicationScope.launch {
                    DeleteAllManager.deleteAll(context, container)
                }
            },
        )
    }
}
