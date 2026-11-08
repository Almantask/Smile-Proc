package com.example.smileproc

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.smileproc.ui.onboarding.OnboardingScreen
import com.example.smileproc.ui.settings.ImportPasswordDialog
import com.example.smileproc.ui.theme.SmileProcTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var appLockEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = SmileProcApplication.container(this)
        AppLockManager.init(this)

        setContent {
            val storageState by container.storageState.collectAsStateWithLifecycle()
            val onboardingCompleted by container.settingsRepository.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
            val appLockSetting by container.settingsRepository.appLock.collectAsStateWithLifecycle(initialValue = false)
            val isLocked by AppLockManager.isLocked.collectAsStateWithLifecycle()

            LaunchedEffect(appLockSetting) {
                appLockEnabled = appLockSetting
                updateRecentsMasking(appLockSetting)
            }

            LaunchedEffect(appLockSetting, isLocked) {
                if (appLockSetting && isLocked) {
                    AppLockManager.promptUnlock(this@MainActivity)
                }
            }

            SmileProcTheme {
                if (appLockSetting && isLocked && storageState is StorageState.Ready && onboardingCompleted == true) {
                    AppLockScreen(
                        onUnlock = { AppLockManager.promptUnlock(this@MainActivity) },
                    )
                } else {
                    when (val state = storageState) {
                        StorageState.Loading -> {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {}
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
                                                    CanonicalJson.json.decodeFromString(ExportPayload.serializer(), plaintextJson)
                                                } catch (e: Exception) {
                                                    throw ExportException(ExportException.Reason.INVALID_PAYLOAD, e)
                                                }

                                                val freshState = container.startFresh()
                                                if (freshState is StorageState.Ready) {
                                                    freshState.storage.entryRepository.saveAll(payload.entries)
                                                    payload.places.forEach { freshState.storage.placeRepository.save(it) }
                                                    container.settingsRepository.setReminders(payload.settings.reminders)
                                                    container.settingsRepository.setStanding(payload.settings.keepCheckIn)
                                                    container.settingsRepository.setWeeklyCardTime(payload.settings.weeklyCard)
                                                    container.settingsRepository.setTagPickerMode(TagPickerMode.fromStorage(payload.settings.tagPickerMode))
                                                    container.settingsRepository.setCaptureLocation(payload.settings.captureLocation)
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
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.background,
                                    ) {}
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
                                true -> {
                                    MainApp(
                                        container = container,
                                        storage = state.storage,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.onResumeCheck(appLockEnabled)
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
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_lock_locked_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(
                onClick = onUnlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.app_lock_unlock))
            }
        }
    }
}
