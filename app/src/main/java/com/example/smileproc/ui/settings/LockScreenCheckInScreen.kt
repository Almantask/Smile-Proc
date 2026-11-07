package com.example.smileproc.ui.settings

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.AppContainer
import com.example.smileproc.R
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.notify.CheckInNotifier
import com.example.smileproc.notify.CheckInReceiver
import kotlinx.coroutines.launch

data class ReadinessStatus(
    val notificationsAllowed: Boolean,
    val channelEnabled: Boolean,
    val visibilityPublic: Boolean,
)

fun checkReadiness(context: Context): ReadinessStatus {
    CheckInNotifier.ensureChannel(context)
    val notifManagerCompat = NotificationManagerCompat.from(context)
    val notificationsAllowed = notifManagerCompat.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = manager?.getNotificationChannel(CheckInNotifier.CHANNEL_ID)

    val channelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    val visibilityPublic = channel == null || channel.lockscreenVisibility != Notification.VISIBILITY_SECRET

    return ReadinessStatus(
        notificationsAllowed = notificationsAllowed,
        channelEnabled = channelEnabled,
        visibilityPublic = visibilityPublic,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreenCheckInScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val standing by container.settingsRepository.standing.collectAsStateWithLifecycle(initialValue = true)
    val lastTestSuccess by container.settingsRepository.lastTestSuccess.collectAsStateWithLifecycle(initialValue = null)

    var readiness by remember { mutableStateOf(checkReadiness(context)) }
    var testRequestedByMe by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness = checkReadiness(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        readiness = checkReadiness(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.lockscreen_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.lockscreen_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Row 1: Notifications allowed
            CheckRow(
                title = stringResource(R.string.lockscreen_row_notifications),
                isOk = readiness.notificationsAllowed,
                problemSummary = stringResource(R.string.lockscreen_row_notifications_fix),
                fixActionLabel = stringResource(R.string.lockscreen_row_notifications_action),
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: Check-ins turned on
            CheckRow(
                title = stringResource(R.string.lockscreen_row_channel),
                isOk = readiness.channelEnabled,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Row 3: Shown on lock screen
            CheckRow(
                title = stringResource(R.string.lockscreen_row_visibility),
                isOk = readiness.visibilityPublic,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Row 4: Keep a check-in on the lock screen toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lockscreen_row_keep),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.lockscreen_row_keep_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Switch(
                    checked = standing,
                    onCheckedChange = { isChecked ->
                        scope.launch {
                            container.settingsRepository.setStanding(isChecked)
                            CheckInReceiver.dispatchMachineEvent(
                                context,
                                if (isChecked) CheckInMachine.Event.StandingTurnedOn else CheckInMachine.Event.StandingTurnedOff,
                            )
                        }
                    },
                )
            }

            if (!readiness.notificationsAllowed) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.lockscreen_app_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Test check-in section
            Text(
                text = stringResource(R.string.lockscreen_send_test),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lockscreen_test_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (lastTestSuccess != null) {
                Text(
                    text = stringResource(R.string.lockscreen_test_works),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else if (testRequestedByMe) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.lockscreen_lock_now),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.lockscreen_test_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    testRequestedByMe = true
                    scope.launch {
                        container.settingsRepository.clearLastTestSuccess()
                        CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.TestRequested)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.lockscreen_send_test))
            }
        }
    }
}

@Composable
internal fun CheckRow(
    title: String,
    isOk: Boolean,
    problemSummary: String,
    fixActionLabel: String,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readyCd = stringResource(R.string.lockscreen_ok_cd)
    val problemCd = stringResource(R.string.lockscreen_problem_cd)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isOk) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = readyCd },
                )
            } else {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { contentDescription = problemCd },
                )
            }
        }

        if (!isOk) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = problemSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onFix) {
                Text(fixActionLabel)
            }
        }
    }
}
