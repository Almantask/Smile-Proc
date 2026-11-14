package com.example.smileproc.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassDivider
import com.example.smileproc.ui.components.GlassTopBar
import com.example.smileproc.ui.theme.LocalSmileProcColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.ReminderPause
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.security.AppLockManager
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    captureLocation: Boolean,
    onCaptureLocationChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    standing: Boolean = true,
    onStandingChanged: (Boolean) -> Unit = {},
    weeklyCardTime: WeeklyCardTime = WeeklyCardTime.DEFAULT,
    onWeeklyCardTimeChanged: (WeeklyCardTime) -> Unit = {},
    appLock: Boolean = false,
    onAppLockChanged: (Boolean) -> Unit = {},
    reminderPause: ReminderPause = ReminderPause.NONE,
    onNavigateToReminders: () -> Unit = {},
    onNavigateToLockScreenCheckIn: () -> Unit = {},
    onNavigateToPlaces: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onDeleteAllClick: () -> Unit = {},
    onExportErrorLogsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    var showAboutDialog by remember { mutableStateOf(false) }
    var showWeeklyCardTimeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val localeContext = androidx.compose.ui.platform.LocalContext.current
    val selectedLanguage = com.example.smileproc.AppLocale.currentLanguage(localeContext)
    val languageSummary = when (selectedLanguage) {
        com.example.smileproc.AppLanguage.SYSTEM -> stringResource(R.string.language_system)
        com.example.smileproc.AppLanguage.EN -> stringResource(R.string.language_english)
        com.example.smileproc.AppLanguage.LT -> stringResource(R.string.language_lithuanian)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassTopBar(title = stringResource(R.string.settings_title))
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Section 1: Check-in & Schedule
            GlassCard(
                contentPadding = PaddingValues(0.dp),
            ) {
                // Reminders row
                val remindersLabel = stringResource(R.string.settings_reminders)
                val remindersPausedSummary = if (reminderPause.isPausedAt(Instant.now())) {
                    stringResource(R.string.settings_reminders_paused)
                } else {
                    null
                }
                SettingsNavRow(
                    title = remindersLabel,
                    summary = remindersPausedSummary,
                    onClick = onNavigateToReminders,
                )

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // Keep a check-in on the lock screen row
                val standingLabel = stringResource(R.string.settings_keep_checkin)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onStandingChanged(!standing) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = standingLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = standing,
                        onCheckedChange = onStandingChanged,
                        modifier = Modifier.semantics { contentDescription = standingLabel },
                    )
                }

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // Lock-screen check-in readiness row
                val lockscreenLabel = stringResource(R.string.settings_lockscreen)
                SettingsNavRow(
                    title = lockscreenLabel,
                    onClick = onNavigateToLockScreenCheckIn,
                )

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // Weekly card time row
                val weeklyCardTimeLabel = stringResource(R.string.settings_weekly_card_time)
                val weekdayRes = when (weeklyCardTime.weekday) {
                    1 -> R.string.weekday_1
                    2 -> R.string.weekday_2
                    3 -> R.string.weekday_3
                    4 -> R.string.weekday_4
                    5 -> R.string.weekday_5
                    6 -> R.string.weekday_6
                    else -> R.string.weekday_7
                }
                val dayName = stringResource(weekdayRes)
                val timeFormatted = String.format(Locale.ROOT, "%02d:%02d", weeklyCardTime.hour, weeklyCardTime.minute)
                val cardTimeStr = stringResource(R.string.week_card_time, dayName, timeFormatted)
                val weeklyCardSummary = stringResource(R.string.weekly_card_time_summary, cardTimeStr)

                SettingsNavRow(
                    title = weeklyCardTimeLabel,
                    summary = weeklyCardSummary,
                    onClick = { showWeeklyCardTimeDialog = true },
                )
            }

            // Section 2: Places & Security
            GlassCard(
                contentPadding = PaddingValues(0.dp),
            ) {
                // Places row
                val placesLabel = stringResource(R.string.places_title)
                SettingsNavRow(
                    title = placesLabel,
                    onClick = onNavigateToPlaces,
                )

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // Location toggle row
                val captureLocationLabel = stringResource(R.string.settings_capture_location)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onCaptureLocationChanged(!captureLocation) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = captureLocationLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_capture_location_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = captureLocation,
                        onCheckedChange = onCaptureLocationChanged,
                        modifier = Modifier.semantics { contentDescription = captureLocationLabel },
                    )
                }

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // App lock row
                val appLockLabel = stringResource(R.string.settings_app_lock)
                val canAuth = remember(context) { AppLockManager.canAuthenticate(context) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(enabled = canAuth) { onAppLockChanged(!appLock) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = appLockLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (canAuth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                        Text(
                            text = if (canAuth) stringResource(R.string.settings_app_lock_summary) else stringResource(R.string.settings_app_lock_no_screen_lock),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = appLock && canAuth,
                        onCheckedChange = if (canAuth) onAppLockChanged else null,
                        enabled = canAuth,
                        modifier = Modifier.semantics { contentDescription = appLockLabel },
                    )
                }
            }

            // Section 3: Data & Privacy
            GlassCard(
                contentPadding = PaddingValues(0.dp),
            ) {
                // Export row
                val exportLabel = stringResource(R.string.settings_export)
                SettingsNavRow(
                    title = exportLabel,
                    summary = stringResource(R.string.export_body),
                    onClick = onExportClick,
                )

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // Import row
                val importLabel = stringResource(R.string.settings_import)
                SettingsNavRow(
                    title = importLabel,
                    summary = stringResource(R.string.import_body),
                    onClick = onImportClick,
                )

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // Privacy row
                val privacyLabel = stringResource(R.string.settings_privacy)
                SettingsNavRow(
                    title = privacyLabel,
                    summary = stringResource(R.string.settings_privacy_summary),
                    onClick = onNavigateToPrivacy,
                )

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // Delete all data row
                val deleteAllLabel = stringResource(R.string.settings_delete_all)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onDeleteAllClick() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deleteAllLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Section 4: App
            GlassCard(
                contentPadding = PaddingValues(0.dp),
            ) {
                // Language row
                SettingsNavRow(
                    title = stringResource(R.string.settings_language),
                    summary = languageSummary,
                    onClick = { showLanguageDialog = true },
                )

                GlassDivider(modifier = Modifier.padding(horizontal = 18.dp))

                // About row
                val aboutLabel = stringResource(R.string.settings_about)
                SettingsNavRow(
                    title = aboutLabel,
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    if (showLanguageDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = smileColors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val options = listOf(
                        com.example.smileproc.AppLanguage.SYSTEM to stringResource(R.string.language_system),
                        com.example.smileproc.AppLanguage.EN to stringResource(R.string.language_english),
                        com.example.smileproc.AppLanguage.LT to stringResource(R.string.language_lithuanian),
                    )
                    options.forEach { (option, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    showLanguageDialog = false
                                    com.example.smileproc.AppLocale.setLanguage(localeContext, option)
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedLanguage == option,
                                onClick = null,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showLanguageDialog = false },
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // About Dialog
    if (showAboutDialog) {
        val closeLabel = stringResource(R.string.action_close)
        val exportLogsCd = stringResource(R.string.export_error_logs_cd)
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = smileColors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GlassDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.about_error_logs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                showAboutDialog = false
                                onExportErrorLogsClick()
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = exportLogsCd },
                        ) {
                            Text(stringResource(R.string.export_error_logs))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAboutDialog = false },
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = closeLabel },
                ) {
                    Text(closeLabel)
                }
            },
        )
    }

    // Weekly Card Time Dialog
    if (showWeeklyCardTimeDialog) {
        WeeklyCardTimeDialog(
            initialTime = weeklyCardTime,
            onDismiss = { showWeeklyCardTimeDialog = false },
            onConfirm = { newTime ->
                onWeeklyCardTimeChanged(newTime)
                showWeeklyCardTimeDialog = false
            },
        )
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
