package com.example.smileproc.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.theme.LocalSmileProcColors

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smileproc.AppContainer
import com.example.smileproc.R
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.Reminder
import com.example.smileproc.notify.CheckInNotifier
import com.example.smileproc.notify.CheckInReceiver
import com.example.smileproc.ui.settings.CheckRow
import com.example.smileproc.ui.settings.checkReadiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScreen(
        container = null,
        onComplete = { _, _, _ -> onComplete() },
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    onComplete: (reminders: List<Reminder>, standing: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    container: AppContainer? = null,
) {
    OnboardingScreen(
        onComplete = { r, s, _ -> onComplete(r, s) },
        modifier = modifier,
        container = container,
    )
}

@Composable
fun OnboardingScreen(
    onComplete: (reminders: List<Reminder>, standing: Boolean, appLock: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    container: AppContainer? = null,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }

    var configuredReminders by remember { mutableStateOf(Reminder.DEFAULT_REMINDERS) }
    var standingEnabled by rememberSaveable { mutableStateOf(true) }
    var appLockEnabled by rememberSaveable { mutableStateOf(true) }
    var testSent by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        step = 2
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        step = 3
    }

    fun finishOnboarding() {
        val canAuth = com.example.smileproc.security.AppLockManager.canAuthenticate(context)
        onComplete(configuredReminders, standingEnabled, appLockEnabled && canAuth)
    }

    val smileColors = LocalSmileProcColors.current
    val scrollState = rememberScrollState()
    LaunchedEffect(step) {
        scrollState.scrollTo(0)
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Subtle 5-step progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index <= step) smileColors.brandAccent else smileColors.chipFill,
                                ),
                        )
                    }
                }

                when (step) {
                    0 -> {
                        // Step 1: Privacy promise + quick language switcher
                        val localeContext = androidx.compose.ui.platform.LocalContext.current
                        val currentLang = com.example.smileproc.AppLocale.currentLanguage(localeContext)
                        val activeCode = com.example.smileproc.AppLocale.effectiveLanguageCode(localeContext)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            val isEn = currentLang == com.example.smileproc.AppLanguage.EN ||
                                (currentLang == com.example.smileproc.AppLanguage.SYSTEM && activeCode != "lt")
                            androidx.compose.material3.FilterChip(
                                selected = isEn,
                                onClick = {
                                    com.example.smileproc.AppLocale.setLanguage(
                                        localeContext,
                                        com.example.smileproc.AppLanguage.EN,
                                    )
                                },
                                label = { Text(stringResource(R.string.language_english)) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = smileColors.chipFill,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isEn,
                                    borderColor = smileColors.chipBorder,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                            val isLt = currentLang == com.example.smileproc.AppLanguage.LT ||
                                (currentLang == com.example.smileproc.AppLanguage.SYSTEM && activeCode == "lt")
                            androidx.compose.material3.FilterChip(
                                selected = isLt,
                                onClick = {
                                    com.example.smileproc.AppLocale.setLanguage(
                                        localeContext,
                                        com.example.smileproc.AppLanguage.LT,
                                    )
                                },
                                label = { Text(stringResource(R.string.language_lithuanian)) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = smileColors.chipFill,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isLt,
                                    borderColor = smileColors.chipBorder,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }

                        Text(
                            text = stringResource(R.string.onboarding_privacy_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_privacy_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        GlassCard(
                            highlighted = true,
                            contentPadding = PaddingValues(18.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_privacy_backup),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    1 -> {
                        // Step 2: Location
                        Text(
                            text = stringResource(R.string.onboarding_location_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_location_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        GlassCard(
                            highlighted = true,
                            contentPadding = PaddingValues(18.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.rationale_location),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    2 -> {
                        // Step 3: Reminders & Standing
                        Text(
                            text = stringResource(R.string.onboarding_reminders_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_reminders_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Reminders preview list
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            configuredReminders.forEach { reminder ->
                                val timeStr = String.format(Locale.ROOT, "%02d:%02d", reminder.hour, reminder.minute)
                                val deleteCd = stringResource(R.string.reminders_delete_cd, timeStr)
                                GlassCard(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        IconButton(
                                            onClick = {
                                                configuredReminders = configuredReminders.filterNot { it.id == reminder.id }
                                            },
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
                        }

                        // Keep on lock screen toggle
                        GlassCard(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_keep_checkin),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(R.string.lockscreen_row_keep_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = standingEnabled,
                                    onCheckedChange = { standingEnabled = it },
                                )
                            }
                        }
                    }
                    3 -> {
                        // Step 4: App lock
                        Text(
                            text = stringResource(R.string.onboarding_applock_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_applock_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val canAuth = remember(context) { com.example.smileproc.security.AppLockManager.canAuthenticate(context) }
                        GlassCard(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_app_lock),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = appLockEnabled && canAuth,
                                    onCheckedChange = { appLockEnabled = it },
                                    enabled = canAuth,
                                )
                            }
                        }
                    }
                    4 -> {
                        // Step 5: Test check-in & readiness checks
                        val lifecycleOwner = LocalLifecycleOwner.current
                        var readiness by remember { mutableStateOf(checkReadiness(context)) }

                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    readiness = checkReadiness(context)
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        Text(
                            text = stringResource(R.string.onboarding_test_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_test_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Problem rows show here if readiness checks fail
                        if (!readiness.notificationsAllowed) {
                            CheckRow(
                                title = stringResource(R.string.lockscreen_row_notifications),
                                isOk = false,
                                problemSummary = stringResource(R.string.lockscreen_row_notifications_fix),
                                fixActionLabel = stringResource(R.string.lockscreen_row_notifications_action),
                                onFix = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                            )
                            GlassCard(
                                highlighted = true,
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.lockscreen_app_only),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else if (!readiness.visibilityPublic) {
                            CheckRow(
                                title = stringResource(R.string.lockscreen_row_visibility),
                                isOk = false,
                                problemSummary = stringResource(R.string.lockscreen_row_visibility_fix),
                                fixActionLabel = stringResource(R.string.action_open_settings),
                                onFix = {
                                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, CheckInNotifier.CHANNEL_ID)
                                    }
                                    context.startActivity(intent)
                                },
                            )
                        } else if (!readiness.channelEnabled) {
                            CheckRow(
                                title = stringResource(R.string.lockscreen_row_channel),
                                isOk = false,
                                problemSummary = stringResource(R.string.lockscreen_row_channel_fix),
                                fixActionLabel = stringResource(R.string.action_open_settings),
                                onFix = {
                                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, CheckInNotifier.CHANNEL_ID)
                                    }
                                    context.startActivity(intent)
                                },
                            )
                        }

                        if (testSent) {
                            GlassCard(
                                highlighted = true,
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.lockscreen_lock_now),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    testSent = true
                                    CoroutineScope(Dispatchers.IO).launch {
                                        CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.TestRequested)
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp),
                            ) {
                                Text(stringResource(R.string.lockscreen_send_test))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> {
                        val nextLabel = stringResource(R.string.action_next)
                        Button(
                            onClick = { step = 1 },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .semantics { contentDescription = nextLabel },
                        ) {
                            Text(nextLabel)
                        }
                    }
                    1 -> {
                        val allowLabel = stringResource(R.string.onboarding_location_action)
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .semantics { contentDescription = allowLabel },
                        ) {
                            Text(allowLabel)
                        }

                        val skipLabel = stringResource(R.string.action_skip)
                        TextButton(
                            onClick = { step = 2 },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = skipLabel },
                        ) {
                            Text(skipLabel)
                        }
                    }
                    2 -> {
                        val allowLabel = stringResource(R.string.onboarding_reminders_action)
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    step = 3
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .semantics { contentDescription = allowLabel },
                        ) {
                            Text(allowLabel)
                        }

                        val skipLabel = stringResource(R.string.action_skip)
                        TextButton(
                            onClick = { step = 3 },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = skipLabel },
                        ) {
                            Text(skipLabel)
                        }
                    }
                    3 -> {
                        val nextLabel = stringResource(R.string.action_next)
                        Button(
                            onClick = { step = 4 },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .semantics { contentDescription = nextLabel },
                        ) {
                            Text(nextLabel)
                        }
                    }
                    4 -> {
                        val startLabel = stringResource(R.string.onboarding_start)
                        Button(
                            onClick = { finishOnboarding() },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .semantics { contentDescription = startLabel },
                        ) {
                            Text(startLabel)
                        }
                    }
                }
            }
        }
    }
}
