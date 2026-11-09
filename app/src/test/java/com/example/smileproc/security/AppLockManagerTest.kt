package com.example.smileproc.security

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class AppLockManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppLockManager.resetForTesting()
    }

    @After
    fun tearDown() {
        AppLockManager.resetForTesting()
    }

    @Test
    fun staysLockedUntilTheSettingIsKnown() {
        // A fresh process must not show content before DataStore says whether App lock is on.
        assertTrue(AppLockManager.isLocked.value)
    }

    @Test
    fun launchLocksWhenSettingLoadsEnabled() {
        AppLockManager.onSettingLoaded(appLockEnabled = true)
        assertTrue(AppLockManager.isLocked.value)

        AppLockManager.unlockForTesting()
        // Later emissions of the same setting (recomposition, resume) don't lock again.
        AppLockManager.onSettingLoaded(appLockEnabled = true)
        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun launchDoesNotLockWhenSettingLoadsDisabled() {
        AppLockManager.onSettingLoaded(appLockEnabled = false)
        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun turningAppLockOnWhileInUseDoesNotLockImmediately() {
        AppLockManager.onSettingLoaded(appLockEnabled = false)
        AppLockManager.onSettingLoaded(appLockEnabled = true)
        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun deleteAllMarksUserPresentSoOnboardingDoesNotRearmTheLaunchLock() {
        // Regression: Delete all used resetForTesting(), which re-armed the launch lock and
        // locked the app seconds after onboarding turned App lock on.
        AppLockManager.onSettingLoaded(appLockEnabled = true)
        AppLockManager.unlockForTesting()

        AppLockManager.markUserPresent()
        AppLockManager.onSettingLoaded(appLockEnabled = false) // cleared settings
        AppLockManager.onSettingLoaded(appLockEnabled = true) // onboarding turns it on

        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun backgroundUnder60SecondsDoesNotLock() {
        val testOwner = TestLifecycleOwner()
        AppLockManager.onSettingLoaded(appLockEnabled = true)
        AppLockManager.unlockForTesting()

        AppLockManager.onStop(testOwner)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30))
        AppLockManager.onStart(testOwner)

        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun background60SecondsOrMoreLocksApp() {
        val testOwner = TestLifecycleOwner()
        AppLockManager.onSettingLoaded(appLockEnabled = true)
        AppLockManager.unlockForTesting()

        AppLockManager.onStop(testOwner)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        AppLockManager.onStart(testOwner)

        assertTrue(AppLockManager.isLocked.value)
    }

    @Test
    fun disablingAppLockClearsLockedState() {
        AppLockManager.onSettingLoaded(appLockEnabled = true)
        assertTrue(AppLockManager.isLocked.value)

        AppLockManager.onSettingLoaded(appLockEnabled = false)
        assertFalse(AppLockManager.isLocked.value)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
