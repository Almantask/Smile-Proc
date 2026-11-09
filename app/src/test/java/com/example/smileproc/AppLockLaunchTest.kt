package com.example.smileproc

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.ManagedSealer
import com.example.smileproc.security.AppLockManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

/**
 * Regression (QA H-1): with App lock on, a cold start opened straight to Today. MainActivity's
 * first onResume ran the launch check while the setting was still its `false` placeholder, which
 * used up the one-time launch lock before DataStore delivered `true`.
 */
@RunWith(RobolectricTestRunner::class)
class AppLockLaunchTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val dbName = "applock-launch-${System.nanoTime()}.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // A device with a screen lock, so AppLockManager.canAuthenticate() is true and the
        // prompt path doesn't auto-unlock.
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        Shadow.extract<ShadowBiometricManager>(context.getSystemService(BiometricManager::class.java))
            .setCanAuthenticate(true)

        container = AppContainer(
            context = context,
            sealer = FakeManagedSealer(),
            dbName = dbName,
            autoInitialize = false,
        )
        runBlocking {
            container.initialize()
            container.settingsRepository.setOnboardingCompleted(true)
            container.settingsRepository.setAppLock(true)
        }
        SmileProcApplication.setContainerForTest(container)
        AppLockManager.resetForTesting() // a fresh process
    }

    @After
    fun tearDown() {
        SmileProcApplication.setContainerForTest(null)
        AppLockManager.resetForTesting()
        runBlocking { container.close() }
        context.deleteDatabase(dbName)
    }

    @Test
    fun coldStartWithAppLockOnShowsLockScreenNotToday() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            assertTrue("App lock must be engaged on launch", AppLockManager.isLocked.value)
            composeRule.onNodeWithText(context.getString(R.string.app_lock_locked_title)).assertExists()
            composeRule.onNodeWithText(context.getString(R.string.today_question)).assertDoesNotExist()
        }
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
