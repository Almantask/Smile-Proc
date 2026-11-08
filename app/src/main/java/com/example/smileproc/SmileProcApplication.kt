package com.example.smileproc

import android.app.Application
import android.content.Context
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.notify.CheckInNotifier
import com.example.smileproc.notify.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmileProcApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CheckInNotifier.ensureChannel(this)
        com.example.smileproc.security.AppLockManager.init(this)
        appContainer = AppContainer(this)

        appContainer.applicationScope.launch {
            try {
                val onboardingCompleted = appContainer.settingsRepository.onboardingCompleted.first()
                if (onboardingCompleted) {
                    val standing = appContainer.settingsRepository.standing.first()
                    val dismissed = appContainer.settingsRepository.dismissed.first()
                    val machineState = appContainer.settingsRepository.machineState.first()
                    val settings = CheckInMachine.Settings(standing = standing, dismissed = dismissed)
                    val targetState = if (machineState == CheckInMachine.State.Hidden) {
                        CheckInMachine.restingState(settings)
                    } else {
                        machineState
                    }
                    if (targetState != CheckInMachine.State.Hidden) {
                        CheckInNotifier.render(this@SmileProcApplication, targetState, ongoing = standing)
                    }
                    ReminderScheduler.scheduleNext(this@SmileProcApplication)
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        @Volatile
        private var testContainer: AppContainer? = null

        fun setContainerForTest(container: AppContainer?) {
            testContainer = container
        }

        fun container(context: Context): AppContainer {
            testContainer?.let { return it }
            val app = context.applicationContext
            return if (app is SmileProcApplication) {
                app.appContainer
            } else {
                synchronized(this) {
                    testContainer ?: AppContainer(context).also { testContainer = it }
                }
            }
        }
    }
}
