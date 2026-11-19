package com.example.microbusinesssystem.data.session

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("microbusiness_session", Context.MODE_PRIVATE)
    private val buildPrefs: SharedPreferences = context.getSharedPreferences("microbusiness_build", Context.MODE_PRIVATE)

    /** Require fresh credentials after install or a newly built APK; background resumes keep auth. */
    fun requiresReauthentication(buildId: String): Boolean {
        val previousBuildId = buildPrefs.getString("build_id", null)
        buildPrefs.edit().putString("build_id", buildId).apply()
        return previousBuildId != buildId
    }

    /** Clears profile/session values written by older app versions. Firebase Auth owns login state. */
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
