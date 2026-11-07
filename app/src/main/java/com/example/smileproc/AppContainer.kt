package com.example.smileproc

import android.content.Context
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.EntryRepository
import com.example.smileproc.data.KeystoreSealer
import com.example.smileproc.data.ManagedSealer
import com.example.smileproc.data.PlaceRepository
import com.example.smileproc.data.WeeklyCardRepository
import com.example.smileproc.data.db.SmileProcDatabase
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
)

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
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    autoInitialize: Boolean = true,
) {
    private val appContext: Context = context.applicationContext
    private val mutex = Mutex()
    private val _storageState = MutableStateFlow<StorageState>(StorageState.Loading)
    val storageState: StateFlow<StorageState> = _storageState.asStateFlow()

    init {
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
            db.weeklyCardDao().peekOne()?.let {
                bundle.weeklyCardRepository.getByPeriodStart(Instant.ofEpochMilli(it.periodStart))
            }
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
            (_storageState.value as? StorageState.Ready)?.storage?.database?.close()
            appContext.deleteDatabase(dbName)
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
        (_storageState.value as? StorageState.Ready)?.storage?.database?.close()
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

    private fun buildBundle(db: SmileProcDatabase): StorageBundle =
        StorageBundle(
            database = db,
            entryRepository = EntryRepository(db.entryDao(), sealer),
            placeRepository = PlaceRepository(db.placeDao(), sealer),
            weeklyCardRepository = WeeklyCardRepository(db.weeklyCardDao(), sealer),
        )

    companion object {
        private const val KEY_PROBE_AAD = "__smileproc_key_probe_v1__"
    }
}
