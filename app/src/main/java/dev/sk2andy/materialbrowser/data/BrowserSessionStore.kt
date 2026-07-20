package dev.sk2andy.materialbrowser.data

import android.content.Context
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.SearchEngine
import org.json.JSONArray
import org.json.JSONObject

class BrowserSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("browser_session", Context.MODE_PRIVATE)

    fun loadTabs(nowMillis: Long = System.currentTimeMillis()): Pair<List<BrowserTab>, String?> {
        val raw = preferences.getString(KEY_TABS, null) ?: return emptyList<BrowserTab>() to null
        return runCatching {
            val array = JSONArray(raw)
            val tabs = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        BrowserTab(
                            id = item.getString("id"),
                            lastAccessedAt = item.optLong("lastAccessedAt")
                                .takeIf { it > 0L }
                                ?: nowMillis,
                            isIncognito = item.optBoolean("isIncognito", false),
                            title = item.optString("title", ""),
                            url = item.optString("url", BLANK_URL),
                        ),
                    )
                }
            }
            val persistentTabs = TabPersistenceRules.persistentTabs(tabs)
            persistentTabs to preferences.getString(KEY_SELECTED_TAB, null)
                ?.takeIf { selectedId -> persistentTabs.any { it.id == selectedId } }
        }.getOrDefault(emptyList<BrowserTab>() to null)
    }

    fun saveTabs(tabs: List<BrowserTab>, selectedTabId: String) {
        val persistentTabs = TabPersistenceRules.persistentTabs(tabs)
        val persistentSelection = TabPersistenceRules.persistentSelection(tabs, selectedTabId)
        val array = JSONArray()
        persistentTabs.forEach { tab ->
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("lastAccessedAt", tab.lastAccessedAt)
                    .put("isIncognito", false)
                    .put("title", tab.title)
                    .put("url", tab.url),
            )
        }
        val editor = preferences.edit()
            .putString(KEY_TABS, array.toString())
        if (persistentSelection == null) editor.remove(KEY_SELECTED_TAB)
        else editor.putString(KEY_SELECTED_TAB, persistentSelection)
        editor.apply()
    }

    fun saveSelectedTab(selectedTabId: String) {
        preferences.edit().putString(KEY_SELECTED_TAB, selectedTabId).apply()
    }

    @Synchronized
    fun loadHistory(): List<HistoryEntry> = loadArray(KEY_HISTORY) { item ->
        HistoryEntry(
            url = item.getString("url"),
            title = item.optString("title"),
            lastVisitedAt = item.optLong("lastVisitedAt"),
        )
    }

    @Synchronized
    fun saveHistory(history: List<HistoryEntry>) {
        saveArray(KEY_HISTORY, history) { entry ->
            JSONObject()
                .put("url", entry.url)
                .put("title", entry.title)
                .put("lastVisitedAt", entry.lastVisitedAt)
        }
    }

    @Synchronized
    fun loadFavorites(): List<FavoriteEntry> = loadArray(KEY_FAVORITES) { item ->
        FavoriteEntry(
            url = item.getString("url"),
            title = item.optString("title"),
            addedAt = item.optLong("addedAt"),
        )
    }

    @Synchronized
    fun saveFavorites(favorites: List<FavoriteEntry>) {
        saveArray(KEY_FAVORITES, favorites) { entry ->
            JSONObject()
                .put("url", entry.url)
                .put("title", entry.title)
                .put("addedAt", entry.addedAt)
        }
    }

    fun loadBlockerSettings(): BlockerSettings = BlockerSettings(
        blockAdsAndTrackers = preferences.getBoolean(KEY_BLOCK_ADS, true),
        hideCookieConsent = preferences.getBoolean(KEY_HIDE_CONSENT, true),
        blockThirdPartyCookies = preferences.getBoolean(KEY_BLOCK_THIRD_PARTY_COOKIES, true),
    )

    fun saveBlockerSettings(settings: BlockerSettings) {
        preferences.edit()
            .putBoolean(KEY_BLOCK_ADS, settings.blockAdsAndTrackers)
            .putBoolean(KEY_HIDE_CONSENT, settings.hideCookieConsent)
            .putBoolean(KEY_BLOCK_THIRD_PARTY_COOKIES, settings.blockThirdPartyCookies)
            .apply()
    }

    fun loadInactiveTabLifetime(): InactiveTabLifetime =
        InactiveTabLifetime.fromWireValue(preferences.getString(KEY_INACTIVE_TAB_LIFETIME, null))

    fun saveInactiveTabLifetime(lifetime: InactiveTabLifetime) {
        preferences.edit().putString(KEY_INACTIVE_TAB_LIFETIME, lifetime.wireValue).apply()
    }

    fun loadSearchEngine(): SearchEngine =
        SearchEngine.fromStableId(preferences.getString(KEY_SEARCH_ENGINE, null))

    fun saveSearchEngine(searchEngine: SearchEngine) {
        preferences.edit().putString(KEY_SEARCH_ENGINE, searchEngine.stableId).apply()
    }

    fun loadDismissResistancePercent(): Int =
        preferences.getInt(
            KEY_DISMISS_RESISTANCE_START_PERCENT,
            DEFAULT_DISMISS_RESISTANCE_START_PERCENT,
        ).coerceIn(MIN_DISMISS_RESISTANCE_START_PERCENT, MAX_DISMISS_RESISTANCE_START_PERCENT)

    fun saveDismissResistancePercent(percent: Int) {
        preferences.edit()
            .putInt(
                KEY_DISMISS_RESISTANCE_START_PERCENT,
                percent.coerceIn(
                    MIN_DISMISS_RESISTANCE_START_PERCENT,
                    MAX_DISMISS_RESISTANCE_START_PERCENT,
                ),
            )
            .apply()
    }

    private fun <T> loadArray(key: String, read: (JSONObject) -> T): List<T> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) add(read(array.getJSONObject(index)))
            }
        }.getOrDefault(emptyList())
    }

    private fun <T> saveArray(key: String, values: List<T>, write: (T) -> JSONObject) {
        val array = JSONArray()
        values.forEach { array.put(write(it)) }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val KEY_TABS = "tabs"
        const val KEY_SELECTED_TAB = "selected_tab"
        const val KEY_BLOCK_ADS = "block_ads"
        const val KEY_HIDE_CONSENT = "hide_consent"
        const val KEY_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
        const val KEY_HISTORY = "history"
        const val KEY_FAVORITES = "favorites"
        const val KEY_INACTIVE_TAB_LIFETIME = "inactive_tab_lifetime"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_DISMISS_RESISTANCE_START_PERCENT = "dismiss_resistance_start_percent"
        const val DEFAULT_DISMISS_RESISTANCE_START_PERCENT = 40
        const val MIN_DISMISS_RESISTANCE_START_PERCENT = 10
        const val MAX_DISMISS_RESISTANCE_START_PERCENT = 90
    }
}
