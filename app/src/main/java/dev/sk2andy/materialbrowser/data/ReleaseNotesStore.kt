package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.SharedPreferences

internal class ReleaseNotesStore internal constructor(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    fun lastHandledVersionCode(): Long? = runCatching {
        preferences
            .takeIf { values -> values.contains(KEY_LAST_HANDLED_VERSION_CODE) }
            ?.getLong(KEY_LAST_HANDLED_VERSION_CODE, 0L)
            ?.takeIf { versionCode -> versionCode > 0L }
    }.getOrNull()

    fun markHandled(versionCode: Long): Boolean {
        if (versionCode <= 0L) return false
        val stored = lastHandledVersionCode()
        if (stored != null && stored >= versionCode) return true
        return preferences.edit()
            .putLong(KEY_LAST_HANDLED_VERSION_CODE, versionCode)
            .commit()
    }

    companion object {
        const val PREFERENCES_NAME = "release_notes"
        private const val KEY_LAST_HANDLED_VERSION_CODE = "last_presented_version_code"
    }
}
