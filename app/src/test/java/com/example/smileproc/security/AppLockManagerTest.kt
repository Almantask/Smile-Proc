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
    fun initialLaunchLocksWhenAppLockEnabled() {
        assertFalse(AppLockManager.isLocked.value)

        // First resume with appLock = true
        AppLockManager.onResumeCheck(appLockEnabled = true)
        assertTrue(AppLockManager.isLocked.value)

        // User unlocks
        AppLockManager.unlockForTesting()
        assertFalse(AppLockManager.isLocked.value)

        // Subsequent resume without background timeout does not lock again
        AppLockManager.onResumeCheck(appLockEnabled = true)
        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun backgroundUnder60SecondsDoesNotLock() {
        val testOwner = TestLifecycleOwner()
        AppLockManager.onResumeCheck(appLockEnabled = true)
        AppLockManager.unlockForTesting()
        assertFalse(AppLockManager.isLocked.value)

        // Go to background for 30s (< 60s)
        AppLockManager.onStop(testOwner)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30))
        AppLockManager.onStart(testOwner)

        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun background60SecondsOrMoreLocksApp() {
        val testOwner = TestLifecycleOwner()
        AppLockManager.onResumeCheck(appLockEnabled = true)
        AppLockManager.unlockForTesting()
        assertFalse(AppLockManager.isLocked.value)

        // Go to background for 61s (>= 60s)
        AppLockManager.onStop(testOwner)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        AppLockManager.onStart(testOwner)

        assertTrue(AppLockManager.isLocked.value)
    }

    @Test
    fun disablingAppLockClearsLockedState() {
        AppLockManager.lockForTesting()
        assertTrue(AppLockManager.isLocked.value)

        AppLockManager.onResumeCheck(appLockEnabled = false)
        assertFalse(AppLockManager.isLocked.value)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
