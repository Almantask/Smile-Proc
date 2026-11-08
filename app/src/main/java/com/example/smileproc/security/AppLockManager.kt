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
    private var isFirstLaunch = true

    private val _isLocked = MutableStateFlow(false)
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

    fun onResumeCheck(appLockEnabled: Boolean) {
        if (appLockEnabled) {
            if (isFirstLaunch) {
                isFirstLaunch = false
                _isLocked.value = true
            } else if (isAppInBackground) {
                val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
                if (elapsed >= TIMEOUT_MS) {
                    _isLocked.value = true
                }
            }
        } else {
            _isLocked.value = false
            isFirstLaunch = false
        }
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

    fun resetForTesting() {
        _isLocked.value = false
        backgroundedAt = 0L
        isAppInBackground = false
        isFirstLaunch = true
    }
}
