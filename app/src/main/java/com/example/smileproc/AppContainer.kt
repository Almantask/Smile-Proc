package com.example.smileproc

import android.content.Context
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.DataStoreSettingsRepository
import com.example.smileproc.data.EntryRepository
import com.example.smileproc.data.ErrorLogStore
import com.example.smileproc.data.KeystoreSealer
import com.example.smileproc.data.ManagedSealer
import com.example.smileproc.data.PlaceRepository
import com.example.smileproc.data.SettingsRepository
import com.example.smileproc.data.StorageLifetime
import com.example.smileproc.data.WeeklyCardRepository
import com.example.smileproc.data.db.SmileProcDatabase
import com.example.smileproc.data.settingsDataStore
import com.example.smileproc.location.LocationClient
import com.example.smileproc.location.PlatformLocationClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant

/**
 * Open, verified storage graph exposed only when the Keystore key and Room database agree.
 */
class StorageBundle(
    val database: SmileProcDatabase,
    val entryRepository: EntryRepository,
    val placeRepository: PlaceRepository,
    val weeklyCardRepository: WeeklyCardRepository,
    private val lifetime: StorageLifetime = StorageLifetime(),
) {
    /** Retire this bundle: its flows end quietly from now on, then the database closes. */
    fun close() {
        lifetime.markClosed()
        database.close()
    }
}

/**
 * Application-wide storage lifecycle state (`docs/spec/android.md` › Storage and encryption).
 */
sealed interface StorageState {
    data object Loading : StorageState
    data class Ready(val storage: StorageBundle) : StorageState
    data object NeedsRecovery : StorageState
}

/**
 * Process-scoped dependency container owned by [SmileProcApplication].
 *
 * Guarantees that when a database file already exists on disk while its Keystore key is missing or
 * invalidated, SmileProc routes to [StorageState.NeedsRecovery] without auto-creating a new key or
 * wiping existing ciphertext silently.
 */
class AppContainer(
    context: Context,
    val sealer: ManagedSealer = KeystoreSealer(),
    val dbName: String = SmileProcDatabase.DB_NAME,
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.applicationContext.settingsDataStore),
    locationClientFactory: (Context, SettingsRepository) -> LocationClient = { ctx, settings -> PlatformLocationClient(ctx, settings) },
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    autoInitialize: Boolean = true,
) {
    private val appContext: Context = context.applicationContext
    val locationClient: LocationClient = locationClientFactory(appContext, settingsRepository)
    val errorLogStore: ErrorLogStore = ErrorLogStore(appContext, sealer)
    private val mutex = Mutex()
    private val _storageState = MutableStateFlow<StorageState>(StorageState.Loading)
    val storageState: StateFlow<StorageState> = _storageState.asStateFlow()

    init {
        SafeLog.init(errorLogStore)
        if (autoInitialize) {
            applicationScope.launch {
                initialize()
            }
        }
    }

    /**
     * Initializes storage once if still [StorageState.Loading] and returns the settled state.
     */
    suspend fun initialize(): StorageState = mutex.withLock {
        val current = _storageState.value
        if (current !is StorageState.Loading) return@withLock current

        if (!hasExistingDatabase()) {
            var openedDb: SmileProcDatabase? = null
            return@withLock try {
                sealer.ensureKeyExists()
                if (!verifyKeyUsable()) {
                    SafeLog.event(SafeLog.Event.STORAGE_KEY_MISSING)
                    _storageState.value = StorageState.NeedsRecovery
                    StorageState.NeedsRecovery
                } else {
                    val db = SmileProcDatabase.open(appContext, dbName)
                    openedDb = db
                    db.entryDao().count()
                    val ready = StorageState.Ready(buildBundle(db))
                    _storageState.value = ready
                    ready
                }
            } catch (e: CancellationException) {
                openedDb?.close()
                throw e
            } catch (e: Exception) {
                openedDb?.close()
                SafeLog.failure(SafeLog.Event.STORAGE_SEAL_FAILED, e)
                _storageState.value = StorageState.NeedsRecovery
                StorageState.NeedsRecovery
            }
        }

        // Database exists on disk: never provision a replacement key automatically.
        if (!sealer.isKeyAvailable() || !verifyKeyUsable()) {
            SafeLog.event(SafeLog.Event.STORAGE_KEY_MISSING)
            _storageState.value = StorageState.NeedsRecovery
            return@withLock StorageState.NeedsRecovery
        }

        var openedDb: SmileProcDatabase? = null
        return@withLock try {
            val db = SmileProcDatabase.open(appContext, dbName)
            openedDb = db
            val bundle = buildBundle(db)
            db.entryDao().peekOne()?.let { bundle.entryRepository.getById(it.id) }
            db.placeDao().peekOne()?.let { bundle.placeRepository.getById(it.id) }
            verifyOrClearCachedWeeklyCards(db, bundle)
            val ready = StorageState.Ready(bundle)
            _storageState.value = ready
            ready
        } catch (e: CancellationException) {
            openedDb?.close()
            throw e
        } catch (e: SealException) {
            openedDb?.close()
            SafeLog.failure(SafeLog.Event.STORAGE_KEY_MISSING, e)
            _storageState.value = StorageState.NeedsRecovery
            StorageState.NeedsRecovery
        } catch (e: Exception) {
            openedDb?.close()
            SafeLog.failure(SafeLog.Event.STORAGE_OPEN_FAILED, e)
            _storageState.value = StorageState.NeedsRecovery
            StorageState.NeedsRecovery
        }
    }

    /**
     * Suspends until initialization settles into [StorageState.Ready] or [StorageState.NeedsRecovery].
     */
    suspend fun awaitState(): StorageState {
        val current = _storageState.value
        if (current !is StorageState.Loading) return current
        return initialize()
    }

    /**
     * Explicit user recovery action (*Start fresh*): deletes the unopenable database, replaces the
     * Keystore key, and opens an empty encrypted database.
     *
     * Never throws non-cancellation exceptions, so launching from UI scopes cannot crash the app.
     */
    suspend fun startFresh(): StorageState = mutex.withLock {
        var openedDb: SmileProcDatabase? = null
        try {
            (_storageState.value as? StorageState.Ready)?.storage?.close()
            appContext.deleteDatabase(dbName)
            settingsRepository.setLowRunDismissed(false)
            sealer.deleteKeyIfPresent()
            sealer.ensureKeyExists()
            if (!verifyKeyUsable()) {
                SafeLog.event(SafeLog.Event.STORAGE_START_FRESH_FAILED)
                _storageState.value = StorageState.NeedsRecovery
                StorageState.NeedsRecovery
            } else {
                val db = SmileProcDatabase.open(appContext, dbName)
                openedDb = db
                db.entryDao().count()
                val ready = StorageState.Ready(buildBundle(db))
                _storageState.value = ready
                SafeLog.event(SafeLog.Event.STORAGE_START_FRESH)
                ready
            }
        } catch (e: CancellationException) {
            openedDb?.close()
            throw e
        } catch (e: Exception) {
            openedDb?.close()
            SafeLog.failure(SafeLog.Event.STORAGE_START_FRESH_FAILED, e)
            _storageState.value = StorageState.NeedsRecovery
            StorageState.NeedsRecovery
        }
    }

    /** Closes any open Room database instance (used by tests during teardown). */
    suspend fun close() = mutex.withLock {
        (_storageState.value as? StorageState.Ready)?.storage?.close()
    }

    private fun hasExistingDatabase(): Boolean {
        val dbFile = appContext.getDatabasePath(dbName)
        val walFile = File("${dbFile.path}-wal")
        return (dbFile.exists() && dbFile.length() > 0L) ||
            (walFile.exists() && walFile.length() > 0L)
    }

    private fun verifyKeyUsable(): Boolean =
        try {
            val probe = sealer.seal(KEY_PROBE_AAD, ByteArray(0))
            sealer.open(KEY_PROBE_AAD, probe).isEmpty()
        } catch (_: Exception) {
            false
        }

    private suspend fun verifyOrClearCachedWeeklyCards(
        db: SmileProcDatabase,
        bundle: StorageBundle,
    ) {
        try {
            db.weeklyCardDao().peekOne()?.let {
                bundle.weeklyCardRepository.getByPeriodStart(Instant.ofEpochMilli(it.periodStart))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SealException) {
            // Key/integrity failure must still route through NeedsRecovery without dropping data.
            throw e
        } catch (e: Exception) {
            // WeeklyCard is cached/derived from entries and places; if an update changes its JSON
            // shape, drop only the cache so entries and places remain intact.
            SafeLog.failure(SafeLog.Event.WEEKLY_CARD_BUILD_FAILED, e)
            db.weeklyCardDao().deleteAll()
        }
    }

    private fun buildBundle(db: SmileProcDatabase): StorageBundle {
        val lifetime = StorageLifetime()
        return StorageBundle(
            database = db,
            entryRepository = EntryRepository(db.entryDao(), sealer, lifetime),
            placeRepository = PlaceRepository(db.placeDao(), sealer, lifetime),
            weeklyCardRepository = WeeklyCardRepository(db.weeklyCardDao(), sealer, lifetime),
            lifetime = lifetime,
        )
    }

    companion object {
        private const val KEY_PROBE_AAD = "__smileproc_key_probe_v1__"
    }
}
