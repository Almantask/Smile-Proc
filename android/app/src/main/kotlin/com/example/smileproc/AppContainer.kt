package com.example.smileproc

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.Room
import com.example.smileproc.core.ContentLoader
import com.example.smileproc.core.EmotionCatalog
import com.example.smileproc.core.HelplineDirectory
import com.example.smileproc.core.LibraryRow
import com.example.smileproc.core.PatternConfig
import com.example.smileproc.data.KeystoreSealer
import com.example.smileproc.data.SettingsStore
import com.example.smileproc.data.SmileDatabase
import com.example.smileproc.data.SmileRepository
import com.example.smileproc.location.LocationCapture
import com.example.smileproc.notify.Channels
import com.example.smileproc.notify.ReminderScheduler
import androidx.glance.appwidget.updateAll
import com.example.smileproc.widget.SmileWidget
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(context)
    val location = LocationCapture(context)
    val reminders = ReminderScheduler(context)
    val lock = AppLock(context, settings, scope)
    val config: PatternConfig
    val library: List<LibraryRow>
    val emotions: EmotionCatalog
    val helplines: HelplineDirectory
    val sealer = KeystoreSealer()

    @Volatile
    private var database: SmileDatabase

    lateinit var repository: SmileRepository
        private set

    val ready = MutableStateFlow(false)
    val settingsReady = MutableStateFlow(false)
    val needsRecovery = MutableStateFlow(false)

    init {
        config = ContentLoader.patternConfig(readAsset("pattern-config.json"))
        library = ContentLoader.patternLibrary(readAsset("pattern-library.en.json"))
        emotions = ContentLoader.emotionTags(readAsset("emotion-tags.json"))
        helplines = ContentLoader.helplines(readAsset("helplines.json"))
        Channels.ensure(context)
        val recovery = context.getDatabasePath(SmileDatabase.NAME).exists() && !KeystoreSealer.hasKey()
        needsRecovery.value = recovery
        database = openDatabase()
        repository = newRepository()
        ready.value = true
        lock.start()
        scope.launch {
            settings.snapshot()
            settingsReady.value = true
            if (!recovery) refreshCard()
        }
    }

    fun refreshCardAsync() {
        scope.launch { refreshCard() }
    }

    suspend fun refreshCard() {
        val snapshot = settings.snapshot()
        repository.refreshCard(
            Instant.now(),
            ZoneId.systemDefault(),
            snapshot.weeklyCard.weekday,
            snapshot.weeklyCard.hour,
            snapshot.weeklyCard.minute,
        )
    }

    suspend fun wipe() {
        val snapshot = settings.snapshot()
        reminders.cancelAll(snapshot.reminders)
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
        withContext(Dispatchers.IO) {
            database.close()
            context.deleteDatabase(SmileDatabase.NAME)
            KeystoreSealer.deleteKey()
            settings.clear()
            database = openDatabase()
            repository = newRepository()
        }
        needsRecovery.value = false
        lock.locked.value = false
        SmileWidget().updateAll(context)
    }

    suspend fun startFreshAfterRecovery() {
        withContext(Dispatchers.IO) {
            if (database.isOpen) database.close()
            context.deleteDatabase(SmileDatabase.NAME)
            database = openDatabase()
            repository = newRepository()
        }
        needsRecovery.value = false
    }

    private fun openDatabase(): SmileDatabase =
        Room.databaseBuilder(context, SmileDatabase::class.java, SmileDatabase.NAME).build()

    private fun newRepository(): SmileRepository = SmileRepository(
        database = database,
        sealer = sealer,
        allowedTags = emotions.ids,
        config = config,
        library = library,
    )

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}

class AppLock(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    val locked = MutableStateFlow(false)
    private var backgroundedAt: Long? = null

    fun start() {
        scope.launch {
            val snapshot = settings.snapshot()
            locked.value = snapshot.onboardingComplete && snapshot.appLockEnabled && Biometrics.available(context)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val at = backgroundedAt ?: return
        scope.launch {
            val snapshot = settings.snapshot()
            val elapsed = System.currentTimeMillis() - at
            if (snapshot.onboardingComplete && snapshot.appLockEnabled && Biometrics.available(context) && elapsed >= 60_000) {
                locked.value = true
            }
        }
    }

    fun unlock() {
        locked.value = false
    }
}

object Biometrics {
    fun available(context: Context): Boolean {
        val manager = androidx.biometric.BiometricManager.from(context)
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return manager.canAuthenticate(authenticators) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
}
