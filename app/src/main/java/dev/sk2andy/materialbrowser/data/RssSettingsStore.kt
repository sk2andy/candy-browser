package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.SharedPreferences
import dev.sk2andy.materialbrowser.reader.rss.RssRefreshScheduler

internal class RssSettingsStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun refreshIntervalMinutes(): Long = RssRefreshScheduler.INTERVAL_OPTIONS_MINUTES
        .firstOrNull { it == preferences.getLong(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES) }
        ?: DEFAULT_INTERVAL_MINUTES

    fun setRefreshIntervalMinutes(minutes: Long): Boolean {
        if (minutes !in RssRefreshScheduler.INTERVAL_OPTIONS_MINUTES) return false
        val saved = preferences.edit().putLong(KEY_INTERVAL_MINUTES, minutes).commit()
        if (saved) RssRefreshScheduler.schedule(appContext)
        return saved
    }

    companion object {
        const val PREFERENCES_NAME = "rss_settings"
        const val DEFAULT_INTERVAL_MINUTES = 15L
        private const val KEY_INTERVAL_MINUTES = "refresh_interval_minutes"
    }

    private val appContext = context.applicationContext
}
