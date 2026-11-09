package com.example.smileproc.security

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.smileproc.R
import com.example.smileproc.SafeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages biometric/device credential app lock and 60-second background timeout
 * (`docs/spec/android.md` › App lock and recents).
 */
object AppLockManager : DefaultLifecycleObserver {
    const val TIMEOUT_MS = 60_000L // 60 seconds per spec

    private var backgroundedAt: Long = 0L
    private var isAppInBackground = false

    /**
     * True until the first real App lock setting arrives in this process. The launch lock is
     * decided then, never from a placeholder value (QA H-1: a `false` placeholder on the first
     * resume used to consume the launch lock before DataStore delivered `true`).
     */
    private var launchLockPending = true

    // Locked until the setting is known, so no content shows before the launch decision.
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var isObserverRegistered = false

    fun init(context: Context) {
        if (!isObserverRegistered) {
            isObserverRegistered = true
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            } catch (e: Exception) {
                // Ignore in unit test environments without ProcessLifecycleOwner
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInBackground = true
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val now = SystemClock.elapsedRealtime()
        if (isAppInBackground) {
            isAppInBackground = false
            val elapsed = now - backgroundedAt
            if (elapsed >= TIMEOUT_MS) {
                _isLocked.value = true
            }
        }
    }

    /**
     * Call with every value of the stored App lock setting (not a placeholder). The first value in
     * a process decides the launch lock; later values only matter when App lock is turned off.
     * Turning it on while in use doesn't lock straight away (the 60 s background rule applies).
     */
    fun onSettingLoaded(appLockEnabled: Boolean) {
        if (launchLockPending) {
            launchLockPending = false
            _isLocked.value = appLockEnabled
        } else if (!appLockEnabled) {
            _isLocked.value = false
        }
    }

    /**
     * The person is using the app right now (e.g. just confirmed Delete all), so the launch lock
     * must not fire when onboarding later turns App lock on.
     */
    fun markUserPresent() {
        launchLockPending = false
        _isLocked.value = false
    }

    fun canAuthenticate(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(allowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun promptUnlock(
        activity: FragmentActivity,
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {},
    ) {
        if (!canAuthenticate(activity)) {
            _isLocked.value = false
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                _isLocked.value = false
                SafeLog.event(SafeLog.Event.APP_LOCK_AUTHENTICATED)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                SafeLog.event(SafeLog.Event.APP_LOCK_FAILED)
                onError()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_lock_prompt_title))
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()

        val prompt = BiometricPrompt(activity, executor, callback)
        prompt.authenticate(promptInfo)
    }

    fun unlockForTesting() {
        _isLocked.value = false
    }

    fun lockForTesting() {
        _isLocked.value = true
    }

    /** Back to a fresh process: locked until the setting is known. Tests only. */
    fun resetForTesting() {
        _isLocked.value = true
        backgroundedAt = 0L
        isAppInBackground = false
        launchLockPending = true
    }
}
