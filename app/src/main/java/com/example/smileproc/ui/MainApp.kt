package com.example.smileproc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.AppContainer
import com.example.smileproc.R
import com.example.smileproc.StorageBundle
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.history.HistoryScreen
import com.example.smileproc.ui.places.resolvePlaces
import com.example.smileproc.ui.settings.SettingsScreen
import com.example.smileproc.ui.today.TodayScreen
import kotlinx.coroutines.launch
import java.time.Instant

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
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var showingHistory by rememberSaveable { mutableStateOf(false) }

    val entries by storage.entryRepository.observeAllNewestFirst().collectAsStateWithLifecycle(initialValue = emptyList())
    val placeLabels by storage.placeRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val places = remember(context, entries, placeLabels) {
        resolvePlaces(context, entries, placeLabels)
    }

    val lowRunDismissedEntryId by container.settingsRepository.lowRunDismissedEntryId.collectAsStateWithLifecycle(initialValue = null)
    val captureLocation by container.settingsRepository.captureLocation.collectAsStateWithLifecycle(initialValue = true)
    val tagPickerMode by container.settingsRepository.tagPickerMode.collectAsStateWithLifecycle(initialValue = TagPickerMode.GRADUAL)

    Scaffold(
        bottomBar = {
            if (!showingHistory) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == AppTab.TODAY,
                        onClick = { currentTab = AppTab.TODAY },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab_today),
                                contentDescription = stringResource(R.string.tab_today),
                            )
                        },
                        label = { Text(stringResource(R.string.tab_today)) },
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.WEEK,
                        onClick = { currentTab = AppTab.WEEK },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab_week),
                                contentDescription = stringResource(R.string.tab_week),
                            )
                        },
                        label = { Text(stringResource(R.string.tab_week)) },
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { currentTab = AppTab.SETTINGS },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab_settings),
                                contentDescription = stringResource(R.string.tab_settings),
                            )
                        },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (currentTab) {
                AppTab.TODAY -> {
                    if (showingHistory) {
                        HistoryScreen(
                            entries = entries,
                            places = places,
                            tagPickerMode = tagPickerMode,
                            onTagPickerModeToggle = {
                                container.applicationScope.launch {
                                    val next = if (tagPickerMode == TagPickerMode.GRADUAL) TagPickerMode.DETAILED else TagPickerMode.GRADUAL
                                    container.settingsRepository.setTagPickerMode(next)
                                }
                            },
                            onSaveEntry = { entry ->
                                container.applicationScope.launch {
                                    storage.entryRepository.save(entry)
                                }
                            },
                            onDeleteEntry = { id ->
                                container.applicationScope.launch {
                                    storage.entryRepository.deleteById(id)
                                }
                            },
                            onSavePlaceLabel = { label ->
                                container.applicationScope.launch {
                                    storage.placeRepository.save(label)
                                }
                            },
                            onNavigateBack = { showingHistory = false },
                        )
                    } else {
                        TodayScreen(
                            entries = entries,
                            places = places,
                            lowRunDismissedEntryId = lowRunDismissedEntryId,
                            tagPickerMode = tagPickerMode,
                            onTagPickerModeToggle = {
                                container.applicationScope.launch {
                                    val next = if (tagPickerMode == TagPickerMode.GRADUAL) TagPickerMode.DETAILED else TagPickerMode.GRADUAL
                                    container.settingsRepository.setTagPickerMode(next)
                                }
                            },
                            onSaveEntry = { entry ->
                                container.applicationScope.launch {
                                    storage.entryRepository.save(entry)
                                }
                            },
                            onUpdateEntry = { entry ->
                                container.applicationScope.launch {
                                    storage.entryRepository.save(entry)
                                }
                            },
                            onDeleteEntry = { id ->
                                container.applicationScope.launch {
                                    storage.entryRepository.deleteById(id)
                                }
                            },
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
                        )
                    }
                }
                AppTab.WEEK -> {
                    WeekPlaceholderScreen()
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(
                        captureLocation = captureLocation,
                        onCaptureLocationChanged = { enabled ->
                            container.applicationScope.launch {
                                container.settingsRepository.setCaptureLocation(enabled)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPlaceholderScreen(modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.week_title)) },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.week_first_card, "Sunday at 19:00"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
