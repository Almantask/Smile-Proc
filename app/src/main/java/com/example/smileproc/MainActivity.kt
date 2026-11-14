package com.example.smileproc

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.export.ExportCrypto
import com.example.smileproc.core.export.ExportEnvelope
import com.example.smileproc.core.export.ExportException
import com.example.smileproc.core.export.ExportPayload
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.notify.CheckInNotifier
import com.example.smileproc.notify.ReminderScheduler
import com.example.smileproc.security.AppLockManager
import com.example.smileproc.ui.MainApp
import com.example.smileproc.ui.RecoveryScreen
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.SmileProcBackground
import com.example.smileproc.ui.onboarding.OnboardingScreen
import com.example.smileproc.ui.settings.ImportPasswordDialog
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.SmileProcTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var appLockEnabled: Boolean = false
    private var pendingNotificationFollowUp by mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNotificationFollowUp =
            intent?.getBooleanExtra(CheckInNotifier.EXTRA_OPEN_FOLLOWUP, false) == true
        val container = SmileProcApplication.container(this)
        AppLockManager.init(this)

        setContent {
            val storageState by container.storageState.collectAsStateWithLifecycle()
            val onboardingCompleted by container.settingsRepository.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
            // null until DataStore answers: the launch lock is decided from the real value only.
            val appLockSetting by container.settingsRepository.appLock
                .collectAsStateWithLifecycle<Boolean?>(initialValue = null)
            val isLocked by AppLockManager.isLocked.collectAsStateWithLifecycle()

            LaunchedEffect(appLockSetting) {
                val enabled = appLockSetting ?: return@LaunchedEffect
                appLockEnabled = enabled
                updateRecentsMasking(enabled)
                AppLockManager.onSettingLoaded(enabled)
            }

            LaunchedEffect(appLockSetting, isLocked) {
                if (appLockSetting == true && isLocked) {
                    AppLockManager.promptUnlock(this@MainActivity)
                }
            }

            SmileProcTheme {
                val readyState = storageState as? StorageState.Ready
                val isUnlockedOrLockOff = appLockSetting != null && !(appLockSetting == true && isLocked)
                if (readyState != null && onboardingCompleted == true && isUnlockedOrLockOff) {
                    MainApp(
                        container = container,
                        storage = readyState.storage,
                        openNotificationFollowUp = pendingNotificationFollowUp,
                        onNotificationFollowUpHandled = { pendingNotificationFollowUp = false },
                    )
                } else {
                SmileProcBackground {
                    if (appLockSetting == null) {
                        // Don't show any history before we know whether App lock is on.
                        Box(modifier = Modifier.fillMaxSize())
                    } else if (appLockSetting == true && isLocked && storageState is StorageState.Ready && onboardingCompleted == true) {
                        AppLockScreen(
                            onUnlock = { AppLockManager.promptUnlock(this@MainActivity) },
                        )
                    } else {
                        when (val state = storageState) {
                            StorageState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        StorageState.NeedsRecovery -> {
                            var showImportPasswordDialog by rememberSaveable { mutableStateOf(false) }
                            var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
                            var isImporting by remember { mutableStateOf(false) }
                            var importErrorMessage by remember { mutableStateOf<String?>(null) }

                            val importLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument()
                            ) { uri ->
                                if (uri != null) {
                                    pendingImportUri = uri
                                    showImportPasswordDialog = true
                                    importErrorMessage = null
                                }
                            }

                            RecoveryScreen(
                                onImportExport = {
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                                onStartFresh = {
                                    container.applicationScope.launch {
                                        container.startFresh()
                                    }
                                },
                            )

                            if (showImportPasswordDialog && pendingImportUri != null) {
                                val uri = pendingImportUri!!
                                ImportPasswordDialog(
                                    isImporting = isImporting,
                                    errorMessage = importErrorMessage,
                                    onDismiss = {
                                        if (!isImporting) {
                                            showImportPasswordDialog = false
                                            pendingImportUri = null
                                            importErrorMessage = null
                                        }
                                    },
                                    onConfirm = { password ->
                                        isImporting = true
                                        importErrorMessage = null
                                        container.applicationScope.launch {
                                            try {
                                                val fileContent = contentResolver.openInputStream(uri)?.use {
                                                    it.bufferedReader(Charsets.UTF_8).readText()
                                                } ?: throw ExportException(ExportException.Reason.INVALID_PAYLOAD)

                                                val envelope = try {
                                                    CanonicalJson.json.decodeFromString(ExportEnvelope.serializer(), fileContent)
                                                } catch (e: Exception) {
                                                    throw ExportException(ExportException.Reason.INVALID_PAYLOAD, e)
                                                }

                                                val plaintextJson = ExportCrypto.decrypt(envelope, password)
                                                val payload = try {
                                                    com.example.smileproc.core.export.ExtendedExportPayload.decodeFromString(plaintextJson)
                                                } catch (e: Exception) {
                                                    throw ExportException(ExportException.Reason.INVALID_PAYLOAD, e)
                                                }

                                                val freshState = container.startFresh()
                                                if (freshState is StorageState.Ready) {
                                                    freshState.storage.entryRepository.saveAll(payload.entries)
                                                    payload.places.forEach { freshState.storage.placeRepository.save(it) }
                                                    freshState.storage.customTagRepository.saveAll(payload.customTags)
                                                    freshState.storage.entryExtrasRepository.saveAll(payload.entryExtras)
                                                    container.settingsRepository.setReminders(payload.settings.reminders)
                                                    container.settingsRepository.setStanding(payload.settings.keepCheckIn)
                                                    container.settingsRepository.setWeeklyCardTime(payload.settings.weeklyCard)
                                                    container.settingsRepository.setTagPickerMode(TagPickerMode.fromStorage(payload.settings.tagPickerMode))
                                                    container.settingsRepository.setCaptureLocation(payload.settings.captureLocation)
                                                    container.settingsRepository.setReminderWindows(payload.reminderWindows)
                                                    container.settingsRepository.setReminderPause(payload.reminderPause)
                                                    container.settingsRepository.setOnboardingCompleted(true)
                                                    ReminderScheduler.scheduleNext(this@MainActivity)
                                                    SafeLog.event(SafeLog.Event.IMPORT_RESTORED)
                                                }
                                                showImportPasswordDialog = false
                                                pendingImportUri = null
                                            } catch (e: ExportException) {
                                                isImporting = false
                                                importErrorMessage = if (e.reason == ExportException.Reason.WRONG_PASSWORD_OR_CORRUPT) {
                                                    getString(R.string.import_wrong_password)
                                                } else {
                                                    getString(R.string.import_failed)
                                                }
                                            } catch (e: Exception) {
                                                isImporting = false
                                                importErrorMessage = getString(R.string.import_failed)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        is StorageState.Ready -> {
                            when (onboardingCompleted) {
                                null -> {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                                false -> {
                                    OnboardingScreen(
                                        container = container,
                                        onComplete = { reminders, standing, appLock ->
                                            container.applicationScope.launch {
                                                container.settingsRepository.setReminders(reminders)
                                                container.settingsRepository.setStanding(standing)
                                                container.settingsRepository.setAppLock(appLock)
                                                container.settingsRepository.setOnboardingCompleted(true)
                                                ReminderScheduler.scheduleNext(this@MainActivity)
                                                if (standing) {
                                                    val nextState = CheckInMachine.State.Faces(alerting = false)
                                                    container.settingsRepository.setMachineState(nextState)
                                                    CheckInNotifier.render(this@MainActivity, nextState, ongoing = true)
                                                }
                                            }
                                        },
                                    )
                                }
                                true -> {}
                            }
                        }
                    }
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(CheckInNotifier.EXTRA_OPEN_FOLLOWUP, false)) {
            pendingNotificationFollowUp = true
        }
    }

    override fun onResume() {
        super.onResume()
        // The launch lock is decided in onSettingLoaded and the 60 s rule in
        // AppLockManager.onStart; resume only re-applies recents masking.
        updateRecentsMasking(appLockEnabled)
    }

    private fun updateRecentsMasking(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!enabled)
        } else {
            if (enabled) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

@Composable
private fun AppLockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier.widthIn(max = 420.dp),
            highlighted = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(colors.navActiveGlow, Color.Transparent),
                                    center = center,
                                    radius = size.maxDimension * 0.72f,
                                ),
                                radius = size.maxDimension * 0.72f,
                                center = center,
                            )
                        }
                        .clip(CircleShape)
                        .background(colors.navActivePill, CircleShape)
                        .border(1.dp, colors.navActiveBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = colors.navActiveContent,
                    )
                }
                Text(
                    text = stringResource(R.string.app_lock_locked_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Button(
                    onClick = onUnlock,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                ) {
                    Text(stringResource(R.string.app_lock_unlock))
                }
            }
        }
    }
}
