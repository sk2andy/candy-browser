package dev.sk2andy.materialbrowser.data

import android.content.Context

class GestureOnboardingStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val browserSessionPreferences =
        context.getSharedPreferences(BROWSER_SESSION_PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean =
        preferences.getInt(KEY_COMPLETED_VERSION, 0) >= CURRENT_VERSION

    fun markCompleted() {
        preferences.edit().putInt(KEY_COMPLETED_VERSION, CURRENT_VERSION).apply()
    }

    fun shouldShow(): Boolean {
        if (isCompleted()) return false
        if (
            preferences.getInt(KEY_COMPLETED_VERSION, 0) > 0 ||
            preferences.getBoolean(KEY_HAS_STARTED, false)
        ) {
            markStarted()
            return true
        }
        if (browserSessionPreferences.all.isNotEmpty()) {
            markCompleted()
            return false
        }
        markStarted()
        return true
    }

    private fun markStarted() {
        preferences.edit().putBoolean(KEY_HAS_STARTED, true).apply()
    }

    internal companion object {
        const val PREFERENCES_NAME = "gesture_onboarding"
        const val BROWSER_SESSION_PREFERENCES_NAME = "browser_session"
        const val KEY_COMPLETED_VERSION = "completed_version"
        const val KEY_HAS_STARTED = "has_started"
        const val CURRENT_VERSION = 4
    }
}
