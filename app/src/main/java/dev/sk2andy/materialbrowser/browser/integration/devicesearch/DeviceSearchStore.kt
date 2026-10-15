package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import android.content.Context
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID

class DeviceSearchStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): DeviceSearchSettings = runCatching { DeviceSearchSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        selectedIds = preferences.getStringSet(KEY_SELECTED_IDS, emptySet()).orEmpty()
            .filter(DeviceSearchRules::validId).sorted().take(DeviceSearchRules.MAX_ITEMS).toSet(),
        bookmarkProfileId = preferences.getString(KEY_BOOKMARK_PROFILE, DEFAULT_PROFILE_ID)
            ?.takeIf { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) }
            ?: DEFAULT_PROFILE_ID,
    ) }.getOrDefault(DeviceSearchSettings())

    fun save(settings: DeviceSearchSettings): Boolean = preferences.edit()
        .putBoolean(KEY_ENABLED, settings.enabled)
        .putStringSet(KEY_SELECTED_IDS, settings.selectedIds.filter(DeviceSearchRules::validId).sorted().take(DeviceSearchRules.MAX_ITEMS).toSet())
        .putString(KEY_BOOKMARK_PROFILE, settings.bookmarkProfileId)
        .commit()

    companion object {
        const val PREFERENCES_NAME = "device_search"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SELECTED_IDS = "selected_ids"
        private const val KEY_BOOKMARK_PROFILE = "bookmark_profile"
    }
}
