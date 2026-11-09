package com.example.smileproc.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.StorageState
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Regression (QA H-3): Delete all crashed the app with `IllegalStateException: Database is closed`.
 * MainApp still collects the old StorageBundle's Room flows when [AppContainer.startFresh] closes
 * that database; a re-query on the closed database threw inside the composition's collector.
 * Flows of a retired bundle must end quietly instead; the UI then moves to the new bundle.
 */
@RunWith(RobolectricTestRunner::class)
class StorageCloseTest {

    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val dbName = "storage-close-${System.nanoTime()}.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(
            context = context,
            sealer = FakeManagedSealer(),
            dbName = dbName,
            autoInitialize = false,
        )
    }

    @After
    fun tearDown() = runTest {
        container.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun flowsOfTheOldStorageEndQuietlyAfterStartFresh() = runTest {
        val old = (container.initialize() as StorageState.Ready).storage
        old.entryRepository.save(
            Entry(
                id = "11111111-1111-4111-8111-111111111111",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                timeZoneID = "Europe/Rome",
                mood = Mood.GOOD,
                tags = emptyList(),
                comment = null,
                location = null,
                source = EntrySource.APP,
            ),
        )

        val fresh = container.startFresh()
        assertTrue(fresh is StorageState.Ready)

        // Each would throw "Database is closed" before the fix.
        old.entryRepository.observeAllNewestFirst().toList()
        old.entryRepository.observeInRange(Instant.EPOCH, Instant.now()).toList()
        old.placeRepository.observeAll().toList()
        old.weeklyCardRepository.observeAllNewestFirst().toList()
    }

    @Test
    fun theNewStorageStillWorksAfterStartFresh() = runTest {
        container.initialize()
        val fresh = (container.startFresh() as StorageState.Ready).storage
        assertTrue(fresh.entryRepository.getAllNewestFirst().isEmpty())
    }

    private class FakeManagedSealer : ManagedSealer {
        private var delegate: JcaSealer? = null
        override fun isKeyAvailable(): Boolean = delegate?.isKeyAvailable() == true
        override fun ensureKeyExists() {
            if (delegate == null) delegate = JcaSealer.withNewKey()
        }
        override fun deleteKeyIfPresent() { delegate = null }
        override fun seal(rowId: String, plaintext: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).seal(rowId, plaintext)
        override fun open(rowId: String, payload: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).open(rowId, payload)
    }
}
