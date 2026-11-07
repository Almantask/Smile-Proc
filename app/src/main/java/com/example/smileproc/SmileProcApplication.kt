package com.example.smileproc

import android.app.Application
import android.content.Context

class SmileProcApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as SmileProcApplication).appContainer
    }
}
