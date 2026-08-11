package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.SharedPreferences
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.SiteExceptionRules
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrides
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.DomainMuteRules
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import org.json.JSONArray
import org.json.JSONObject

class BrowserSessionStore internal constructor(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

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
                            openerTabId = item.optString("openerTabId")
                                .takeIf(String::isNotBlank),
                            profileId = item.optString("profileId", DEFAULT_PROFILE_ID)
                                .takeIf(String::isNotBlank)
                                ?: DEFAULT_PROFILE_ID,
                            isIncognito = item.optBoolean("isIncognito", false),
                            isPinned = item.optBoolean("isPinned", false),
                            title = item.optString("title", ""),
                            url = item.optString("url", BLANK_URL),
                        ),
                    )
                }
            }
            val persistentTabs = TabPinningRules.orderedTabs(
                TabPersistenceRules.persistentTabs(tabs),
            )
            persistentTabs to preferences.getString(KEY_SELECTED_TAB, null)
                ?.takeIf { selectedId -> persistentTabs.any { it.id == selectedId } }
        }.getOrDefault(emptyList<BrowserTab>() to null)
    }

    fun saveTabs(tabs: List<BrowserTab>, selectedTabId: String) {
        tabsEditor(tabs, selectedTabId).apply()
    }

    fun saveTabsImmediately(tabs: List<BrowserTab>, selectedTabId: String): Boolean =
        tabsEditor(tabs, selectedTabId).commit()

    @Synchronized
    fun saveTabsAndSnoozedImmediately(
        tabs: List<BrowserTab>,
        selectedTabId: String,
        snoozedTabs: List<SnoozedTab>,
    ): Boolean {
        val originalTabs = preferences.getString(KEY_TABS, null)
        val originalSelection = preferences.getString(KEY_SELECTED_TAB, null)
        val originalSnoozedTabs = preferences.getString(SnoozedTabStore.KEY_TABS, null)
        val committed = SnoozedTabStore.putTabs(
            tabsEditor(tabs, selectedTabId),
            snoozedTabs,
        ).commit()
        if (committed) return true

        preferences.edit()
            .restoreString(KEY_TABS, originalTabs)
            .restoreString(KEY_SELECTED_TAB, originalSelection)
            .restoreString(SnoozedTabStore.KEY_TABS, originalSnoozedTabs)
            .commit()
        return false
    }

    private fun SharedPreferences.Editor.restoreString(
        key: String,
        value: String?,
    ): SharedPreferences.Editor = if (value == null) remove(key) else putString(key, value)

    private fun tabsEditor(tabs: List<BrowserTab>, selectedTabId: String) =
        preferences.edit().also { editor ->
            val persistentTabs = TabPersistenceRules.persistentTabs(tabs)
            val persistentSelection = TabPersistenceRules.persistentSelection(tabs, selectedTabId)
            val array = JSONArray()
            persistentTabs.forEach { tab ->
                array.put(
                    JSONObject()
                        .put("id", tab.id)
                        .put("lastAccessedAt", tab.lastAccessedAt)
                        .put("openerTabId", tab.openerTabId)
                        .put("profileId", tab.profileId)
                        .put("isIncognito", false)
                        .put("isPinned", tab.isPinned)
                        .put("title", tab.title)
                        .put("url", tab.url),
                )
            }
            editor.putString(KEY_TABS, array.toString())
            if (persistentSelection == null) editor.remove(KEY_SELECTED_TAB)
            else editor.putString(KEY_SELECTED_TAB, persistentSelection)
        }

    fun saveSelectedTab(selectedTabId: String) {
        preferences.edit().putString(KEY_SELECTED_TAB, selectedTabId).apply()
    }

    fun loadProfiles(): Pair<List<BrowserProfile>, String> {
        val profiles = preferences.getString(KEY_PROFILES, null)
            ?.let { raw ->
                runCatching {
                    val array = JSONArray(raw)
                    buildList<BrowserProfile> {
                        for (index in 0 until array.length()) {
                            val item = array.getJSONObject(index)
                            val id = item.optString("id").trim()
                            val emoji = item.optString("emoji").trim()
                            if (id.isNotEmpty() && emoji.isNotEmpty() && none { it.id == id }) {
                                add(
                                    BrowserProfile(
                                        id = id,
                                        emoji = emoji,
                                        selectedTabId = item.optString("selectedTabId")
                                            .takeIf(String::isNotBlank),
                                        isolationEnabled = item.optBoolean("isolationEnabled", false),
                                    ),
                                )
                            }
                        }
                    }
                }.getOrNull()
            }
            .orEmpty()
            .ifEmpty { listOf(DEFAULT_BROWSER_PROFILE) }
        val activeProfileId = preferences.getString(KEY_ACTIVE_PROFILE, null)
            ?.takeIf { candidate -> profiles.any { it.id == candidate } }
            ?: profiles.first().id
        return profiles to activeProfileId
    }

    fun saveProfiles(profiles: List<BrowserProfile>, activeProfileId: String) {
        val safeProfiles = profiles.ifEmpty { listOf(DEFAULT_BROWSER_PROFILE) }
        val array = JSONArray()
        safeProfiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("emoji", profile.emoji)
                    .put("selectedTabId", profile.selectedTabId)
                    .put("isolationEnabled", profile.isolationEnabled),
            )
        }
        preferences.edit()
            .putString(KEY_PROFILES, array.toString())
            .putString(
                KEY_ACTIVE_PROFILE,
                activeProfileId.takeIf { id -> safeProfiles.any { it.id == id } }
                    ?: safeProfiles.first().id,
            )
            .apply()
    }

    fun loadProfilesEnabled(): Boolean = preferences.getBoolean(KEY_PROFILES_ENABLED, true)

    fun saveProfilesEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PROFILES_ENABLED, enabled).apply()
    }

    fun loadPendingWebViewProfileDeletions(): Set<String> =
        preferences.getStringSet(KEY_PENDING_WEBVIEW_PROFILE_DELETIONS, emptySet())
            ?.toSet()
            .orEmpty()

    fun savePendingWebViewProfileDeletions(profileNames: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_PENDING_WEBVIEW_PROFILE_DELETIONS, profileNames)
            .apply()
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

    @Synchronized
    fun loadPermanentSiteExceptions(): Map<String, Set<String>> =
        loadProfileHosts(KEY_SITE_EXCEPTIONS)

    @Synchronized
    fun savePermanentSiteExceptions(exceptions: Map<String, Set<String>>) {
        saveProfileHosts(KEY_SITE_EXCEPTIONS, exceptions)
    }

    @Synchronized
    fun loadMutedDomains(): Map<String, Set<String>> =
        loadProfileHosts(
            key = KEY_MUTED_DOMAINS,
            normalizeHost = DomainMuteRules::normalizedDomain,
            limit = DomainMuteRules.MAX_PER_PROFILE,
        )

    @Synchronized
    fun saveMutedDomains(domainsByProfile: Map<String, Set<String>>) {
        saveProfileHosts(
            key = KEY_MUTED_DOMAINS,
            hostsByProfile = domainsByProfile,
            normalizeHost = DomainMuteRules::normalizedDomain,
            limit = DomainMuteRules.MAX_PER_PROFILE,
        )
    }

    @Synchronized
    fun loadSitePrivacyOverrides(): Map<String, Map<String, SitePrivacyOverrides>> =
        loadArray(KEY_SITE_PRIVACY_OVERRIDES) { item ->
            Triple(
                item.optString("profileId"),
                item.optString("host"),
                SitePrivacyOverrides(
                    cookieBannerRemovalDisabled = when {
                        item.has("cookieBannerRemovalDisabledOverride") ->
                            item.optBoolean("cookieBannerRemovalDisabledOverride")
                        item.optBoolean("cookieBannerRemovalDisabled", false) -> true
                        else -> null
                    },
                    forceVerticalScrolling = when {
                        item.has("forceVerticalScrollingOverride") ->
                            item.optBoolean("forceVerticalScrollingOverride")
                        item.optBoolean("forceVerticalScrolling", false) -> true
                        else -> null
                    },
                ),
            )
        }.mapNotNull { (profileId, host, overrides) ->
            val safeProfileId = profileId.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val safeHost = SiteExceptionRules.normalizedException(host) ?: return@mapNotNull null
            if (overrides.isDefault) return@mapNotNull null
            Triple(safeProfileId, safeHost, overrides)
        }.groupBy(Triple<String, String, SitePrivacyOverrides>::first)
            .mapValues { (_, entries) ->
                entries.asSequence()
                    .distinctBy { it.second }
                    .take(SiteExceptionRules.MAX_PER_PROFILE)
                    .associate { (_, host, overrides) -> host to overrides }
            }

    @Synchronized
    fun saveSitePrivacyOverrides(
        overridesByProfile: Map<String, Map<String, SitePrivacyOverrides>>,
    ) {
        val values = overridesByProfile.asSequence()
            .flatMap { (profileId, overridesByHost) ->
                val safeProfileId = profileId.trim()
                if (safeProfileId.isEmpty()) return@flatMap emptySequence()
                overridesByHost.asSequence()
                    .mapNotNull { (host, overrides) ->
                        val safeHost = SiteExceptionRules.normalizedException(host)
                            ?: return@mapNotNull null
                        if (overrides.isDefault) null else Triple(safeProfileId, safeHost, overrides)
                    }
                    .distinctBy { it.second }
                    .take(SiteExceptionRules.MAX_PER_PROFILE)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .toList()
        saveArray(KEY_SITE_PRIVACY_OVERRIDES, values) { (profileId, host, overrides) ->
            JSONObject()
                .put("profileId", profileId)
                .put("host", host)
                .also { item ->
                    overrides.cookieBannerRemovalDisabled?.let { disabled ->
                        item.put("cookieBannerRemovalDisabledOverride", disabled)
                    }
                    overrides.forceVerticalScrolling?.let { enabled ->
                        item.put("forceVerticalScrollingOverride", enabled)
                    }
                }
        }
    }

    private fun loadProfileHosts(
        key: String,
        normalizeHost: (String?) -> String? = SiteExceptionRules::normalizedException,
        limit: Int = SiteExceptionRules.MAX_PER_PROFILE,
    ): Map<String, Set<String>> =
        loadArray(key) { item ->
            item.optString("profileId") to item.optString("host")
        }.mapNotNull { (profileId, host) ->
            val safeProfileId = profileId.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val safeHost = normalizeHost(host) ?: return@mapNotNull null
            safeProfileId to safeHost
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, hosts) ->
                hosts.distinct().take(limit).toSet()
            }

    private fun saveProfileHosts(
        key: String,
        hostsByProfile: Map<String, Set<String>>,
        normalizeHost: (String?) -> String? = SiteExceptionRules::normalizedException,
        limit: Int = SiteExceptionRules.MAX_PER_PROFILE,
    ) {
        val values = hostsByProfile.asSequence()
            .flatMap { (profileId, hosts) ->
                val safeProfileId = profileId.trim()
                if (safeProfileId.isEmpty()) return@flatMap emptySequence()
                hosts.asSequence()
                    .mapNotNull(normalizeHost)
                    .distinct()
                    .take(limit)
                    .map { host -> safeProfileId to host }
            }
            .distinct()
            .sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .toList()
        saveArray(key, values) { (profileId, host) ->
            JSONObject()
                .put("profileId", profileId)
                .put("host", host)
        }
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

    fun loadSearchSuggestionProvider(): SearchSuggestionProvider =
        SearchSuggestionProvider.fromStableId(
            preferences.getString(KEY_SEARCH_SUGGESTION_PROVIDER, null),
        )

    fun saveSearchSuggestionProvider(provider: SearchSuggestionProvider) {
        preferences.edit().putString(KEY_SEARCH_SUGGESTION_PROVIDER, provider.stableId).apply()
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

    fun loadTabOverviewMode(): TabOverviewMode =
        TabOverviewMode.fromWireValue(preferences.getString(KEY_TAB_OVERVIEW_MODE, null))

    fun saveTabOverviewMode(mode: TabOverviewMode) {
        preferences.edit().putString(KEY_TAB_OVERVIEW_MODE, mode.wireValue).apply()
    }

    fun loadAddressBarDocked(): Boolean = preferences.getBoolean(KEY_ADDRESS_BAR_DOCKED, false)

    fun saveAddressBarDocked(docked: Boolean) {
        preferences.edit().putBoolean(KEY_ADDRESS_BAR_DOCKED, docked).apply()
    }

    fun loadTabButtonVisible(): Boolean = preferences.getBoolean(KEY_TAB_BUTTON_VISIBLE, true)

    fun saveTabButtonVisible(visible: Boolean) {
        preferences.edit().putBoolean(KEY_TAB_BUTTON_VISIBLE, visible).apply()
    }

    fun loadFullImmersiveModeEnabled(): Boolean =
        preferences.getBoolean(KEY_FULL_IMMERSIVE_MODE_ENABLED, false)

    fun saveFullImmersiveModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FULL_IMMERSIVE_MODE_ENABLED, enabled).apply()
    }

    fun loadVideoAutoplayBlocked(): Boolean =
        preferences.getBoolean(KEY_VIDEO_AUTOPLAY_BLOCKED, false)

    fun saveVideoAutoplayBlocked(blocked: Boolean) {
        preferences.edit().putBoolean(KEY_VIDEO_AUTOPLAY_BLOCKED, blocked).apply()
    }

    fun loadDownloadSettings(): BrowserDownloadSettings = BrowserDownloadSettings(
        managerMode = DownloadManagerMode.fromStableId(
            preferences.getString(KEY_DOWNLOAD_MANAGER_MODE, null),
        ),
        externalManagerId = preferences.getString(KEY_EXTERNAL_DOWNLOAD_MANAGER_ID, null),
        shareSessionDataWithOneDm = preferences.getBoolean(
            KEY_SHARE_SESSION_DATA_WITH_ONE_DM,
            false,
        ),
    ).normalized()

    fun saveDownloadSettings(settings: BrowserDownloadSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putString(KEY_DOWNLOAD_MANAGER_MODE, normalized.managerMode.stableId)
            .apply {
                if (normalized.externalManagerId == null) {
                    remove(KEY_EXTERNAL_DOWNLOAD_MANAGER_ID)
                } else {
                    putString(KEY_EXTERNAL_DOWNLOAD_MANAGER_ID, normalized.externalManagerId)
                }
            }
            .putBoolean(
                KEY_SHARE_SESSION_DATA_WITH_ONE_DM,
                normalized.shareSessionDataWithOneDm,
            )
            .apply()
    }

    fun clearLegacyWebContentEdgeToEdgePreference() {
        preferences.edit().remove(KEY_WEB_CONTENT_EDGE_TO_EDGE).apply()
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

    internal companion object {
        const val PREFERENCES_NAME = "browser_session"
        const val KEY_TABS = "tabs"
        const val KEY_SELECTED_TAB = "selected_tab"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_PROFILES_ENABLED = "profiles_enabled"
        const val KEY_PENDING_WEBVIEW_PROFILE_DELETIONS = "pending_webview_profile_deletions"
        const val KEY_BLOCK_ADS = "block_ads"
        const val KEY_HIDE_CONSENT = "hide_consent"
        const val KEY_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
        const val KEY_SITE_EXCEPTIONS = "site_exceptions"
        const val KEY_MUTED_DOMAINS = "muted_domains"
        const val KEY_SITE_PRIVACY_OVERRIDES = "site_privacy_overrides"
        const val KEY_HISTORY = "history"
        const val KEY_FAVORITES = "favorites"
        const val KEY_INACTIVE_TAB_LIFETIME = "inactive_tab_lifetime"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_SEARCH_SUGGESTION_PROVIDER = "search_suggestion_provider"
        const val KEY_DISMISS_RESISTANCE_START_PERCENT = "dismiss_resistance_start_percent"
        const val KEY_TAB_OVERVIEW_MODE = "tab_overview_mode"
        const val KEY_ADDRESS_BAR_DOCKED = "address_bar_docked"
        const val KEY_TAB_BUTTON_VISIBLE = "tab_button_visible"
        const val KEY_FULL_IMMERSIVE_MODE_ENABLED = "full_immersive_mode_enabled"
        const val KEY_VIDEO_AUTOPLAY_BLOCKED = "video_autoplay_blocked"
        const val KEY_DOWNLOAD_MANAGER_MODE = "download_manager_mode"
        const val KEY_EXTERNAL_DOWNLOAD_MANAGER_ID = "external_download_manager_id"
        const val KEY_SHARE_SESSION_DATA_WITH_ONE_DM = "share_session_data_with_one_dm"
        const val KEY_WEB_CONTENT_EDGE_TO_EDGE = "web_content_edge_to_edge"
        const val DEFAULT_DISMISS_RESISTANCE_START_PERCENT = 40
        const val MIN_DISMISS_RESISTANCE_START_PERCENT = 10
        const val MAX_DISMISS_RESISTANCE_START_PERCENT = 90
    }
}
