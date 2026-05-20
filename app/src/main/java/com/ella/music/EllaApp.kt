package com.ella.music

import android.app.Application
import com.ella.music.data.AppLogcatCollector
import com.ella.music.data.AppLogStore

class EllaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogStore.install(this)
        AppLogcatCollector.start(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogStore.crash(this, thread.name, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        AppLogStore.info(this, "EllaApp", "Application started")
    }
}
