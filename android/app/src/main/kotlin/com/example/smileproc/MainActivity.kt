package com.example.smileproc

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smileproc.ui.AboutScreen
import com.example.smileproc.ui.text
import com.example.smileproc.ui.DebugScreen
import com.example.smileproc.ui.DebugViewModel
import com.example.smileproc.ui.DeleteScreen
import com.example.smileproc.ui.EntryDetailScreen
import com.example.smileproc.ui.ExportScreen
import com.example.smileproc.ui.HelplinesScreen
import com.example.smileproc.ui.HistoryScreen
import com.example.smileproc.ui.HistoryViewModel
import com.example.smileproc.ui.ImportScreen
import com.example.smileproc.ui.LockCover
import com.example.smileproc.ui.OnboardingScreen
import com.example.smileproc.ui.PlacesScreen
import com.example.smileproc.ui.PlacesViewModel
import com.example.smileproc.ui.PrivacyScreen
import com.example.smileproc.ui.RecoveryScreen
import com.example.smileproc.ui.RemindersScreen
import com.example.smileproc.ui.SettingsScreen
import com.example.smileproc.ui.SettingsViewModel
import com.example.smileproc.ui.SmileFactory
import com.example.smileproc.ui.TodayScreen
import com.example.smileproc.ui.TodayViewModel
import com.example.smileproc.ui.WeekScreen
import com.example.smileproc.ui.WeekViewModel
import com.example.smileproc.ui.WeeklyTimeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(false)
        }
        val app = (application as SmileProcApp).container
        setContent {
            MaterialTheme {
                val locked by app.lock.locked.collectAsStateWithLifecycle()
                if (Build.VERSION.SDK_INT < 33) {
                    if (locked) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                SmileRoot(app, ::promptUnlock)
            }
        }
    }

    private fun promptUnlock() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    (application as SmileProcApp).container.lock.unlock()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setSubtitle(getString(R.string.lock_reason))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun SmileRoot(app: AppContainer, promptUnlock: () -> Unit) {
    val ready by app.settingsReady.collectAsStateWithLifecycle()
    val recovery by app.needsRecovery.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle(com.example.smileproc.data.UserSettings())
    val locked by app.lock.locked.collectAsStateWithLifecycle()
    val settingsModel: SettingsViewModel = viewModel(factory = SmileFactory { SettingsViewModel(app) })
    var openImport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (!ready) {
        Box(Modifier.fillMaxSize())
        return
    }
    when {
        recovery -> RecoveryScreen(
            app = app,
            onImport = {
                scope.launch {
                    app.startFreshAfterRecovery()
                    openImport = true
                }
            },
            onFresh = {},
        )
        !settings.onboardingComplete -> OnboardingScreen(settingsModel) {}
        else -> Box(Modifier.fillMaxSize()) {
            AppNav(app, settingsModel, openImport) { openImport = false }
            if (locked) {
                LockCover(onUnlock = promptUnlock)
                LaunchedEffect(Unit) { promptUnlock() }
            }
        }
    }
}

@Composable
private fun AppNav(
    app: AppContainer,
    settingsModel: SettingsViewModel,
    openImport: Boolean,
    onImportConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = listOf("today", "week", "settings")
    val context = LocalContext.current
    LaunchedEffect(openImport) {
        if (openImport) {
            nav.navigate("import")
            onImportConsumed()
        }
    }
    LaunchedEffect(Unit) {
        val data = (context as? MainActivity)?.intent?.data ?: return@LaunchedEffect
        if (data.scheme == "smileproc" && data.host == "entry") {
            val latest = withContext(Dispatchers.IO) { app.repository.latest() }
            if (latest != null) nav.navigate("entry/${latest.id}")
        }
    }
    Scaffold(
        bottomBar = {
            if (route in tabs) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == "today",
                        onClick = { nav.tab("today") },
                        label = { Text(text(context, R.string.tab_today)) },
                        icon = { Text(text(context, R.string.tab_today)) },
                    )
                    NavigationBarItem(
                        selected = route == "week",
                        onClick = { nav.tab("week") },
                        label = { Text(text(context, R.string.tab_week)) },
                        icon = { Text(text(context, R.string.tab_week)) },
                    )
                    NavigationBarItem(
                        selected = route == "settings",
                        onClick = { nav.tab("settings") },
                        label = { Text(text(context, R.string.tab_settings)) },
                        icon = { Text(text(context, R.string.tab_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                val model: TodayViewModel = viewModel(factory = SmileFactory { TodayViewModel(app) })
                TodayScreen(
                    app,
                    model,
                    onHistory = { nav.navigate("history") },
                    onHelplines = { nav.navigate("helplines") },
                )
            }
            composable("week") {
                val model: WeekViewModel = viewModel(factory = SmileFactory { WeekViewModel(app) })
                WeekScreen(
                    app,
                    model,
                    onHelplines = { nav.navigate("helplines") },
                    onName = { cluster -> nav.navigate("places?cluster=$cluster") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    model = settingsModel,
                    onBack = null,
                    onReminders = { nav.navigate("reminders") },
                    onWeekly = { nav.navigate("weekly") },
                    onPlaces = { nav.navigate("places") },
                    onExport = { nav.navigate("export") },
                    onImport = { nav.navigate("import") },
                    onPrivacy = { nav.navigate("privacy") },
                    onDelete = { nav.navigate("delete") },
                    onAbout = { nav.navigate("about") },
                    onDebug = if (BuildConfig.DEBUG) {
                        { nav.navigate("debug") }
                    } else {
                        null
                    },
                )
            }
            composable("history") {
                val model: HistoryViewModel = viewModel(factory = SmileFactory { HistoryViewModel(app) })
                HistoryScreen(model, onEntry = { id -> nav.navigate("entry/$id") }, onBack = { nav.popBackStack() })
            }
            composable(
                "entry/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val history: HistoryViewModel = viewModel(factory = SmileFactory { HistoryViewModel(app) })
                val places: PlacesViewModel = viewModel(factory = SmileFactory { PlacesViewModel(app) })
                EntryDetailScreen(app, history, places, id) { nav.popBackStack() }
            }
            composable(
                "places?cluster={cluster}",
                arguments = listOf(
                    navArgument("cluster") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val places: PlacesViewModel = viewModel(factory = SmileFactory { PlacesViewModel(app) })
                PlacesScreen(places, entry.arguments?.getString("cluster")) { nav.popBackStack() }
            }
            composable("places") {
                val places: PlacesViewModel = viewModel(factory = SmileFactory { PlacesViewModel(app) })
                PlacesScreen(places, null) { nav.popBackStack() }
            }
            composable("reminders") { RemindersScreen(settingsModel) { nav.popBackStack() } }
            composable("weekly") { WeeklyTimeScreen(settingsModel) { nav.popBackStack() } }
            composable("privacy") {
                PrivacyScreen(onBack = { nav.popBackStack() }, onHelplines = { nav.navigate("helplines") })
            }
            composable("about") { AboutScreen { nav.popBackStack() } }
            composable("helplines") { HelplinesScreen(app) { nav.popBackStack() } }
            composable("delete") {
                DeleteScreen(app, onBack = { nav.popBackStack() }, onDone = { nav.popBackStack(nav.graph.findStartDestination().id, false) })
            }
            composable("export") { ExportScreen(app) { nav.popBackStack() } }
            composable("import") {
                ImportScreen(app, onBack = { nav.popBackStack() }, onFinished = { nav.popBackStack() })
            }
            if (BuildConfig.DEBUG) {
                composable("debug") {
                    val model: DebugViewModel = viewModel(factory = SmileFactory { DebugViewModel(app) })
                    DebugScreen(model) { nav.popBackStack() }
                }
            }
        }
    }
}

private fun androidx.navigation.NavHostController.tab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
