package com.example.microbusinesssystem

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.microbusinesssystem.BuildConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MicroBusinessApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        val nightMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(nightMode)

        if (BuildConfig.DEBUG) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                android.util.Log.e("FATAL_CRASH", "Uncaught exception in thread ${thread.name}", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
