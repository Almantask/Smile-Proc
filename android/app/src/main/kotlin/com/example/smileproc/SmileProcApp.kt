package com.example.smileproc

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class SmileProcApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.lock)
    }
}
