package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.SharedPreferences
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.SiteExceptionRules
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrides
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineRules
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.DesktopSiteRules
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsProvider
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsRules
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.browser.DomainMuteRules
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.PopupSiteRules
import dev.sk2andy.materialbrowser.browser.ProfileWallpaper
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperRules
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.ProfileProtectionRules
import dev.sk2andy.materialbrowser.browser.PrivacySignalSettings
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.browser.SearxngRules
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.BrowserSessionResidencyRules
import dev.sk2andy.materialbrowser.browser.TabStack
import dev.sk2andy.materialbrowser.browser.TabStackColor
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.browser.LinkPeekActionLayoutRules
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.shared.browser.AddressBarLongPressAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.sync.SyncTabRules
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
                            syncCandyId = item.optString("syncCandyId")
                                .takeIf(SyncTabRules::isValidCandyId),
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

    fun flush(): Boolean = preferences.edit().commit()

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
                        .put("url", tab.url)
                        .put("syncCandyId", tab.syncCandyId),
                )
            }
            editor.putString(KEY_TABS, array.toString())
            if (persistentSelection == null) editor.remove(KEY_SELECTED_TAB)
            else editor.putString(KEY_SELECTED_TAB, persistentSelection)
        }

    fun saveSelectedTab(selectedTabId: String) {
        preferences.edit().putString(KEY_SELECTED_TAB, selectedTabId).apply()
    }

    fun loadTabStacks(tabs: Collection<BrowserTab>): List<TabStack> {
        val raw = preferences.getString(KEY_TAB_STACKS, null) ?: return emptyList()
        if (raw.length > MAX_TAB_STACKS_JSON_LENGTH) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            check(array.length() <= TabStackRules.MAX_STACKS)
            val stacks = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val tabIdsArray = item.optJSONArray("tabIds") ?: JSONArray()
                    check(tabIdsArray.length() <= tabs.size)
                    val tabIds = buildList {
                        for (tabIndex in 0 until tabIdsArray.length()) {
                            tabIdsArray.optString(tabIndex)
                                .takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }
                    add(
                        TabStack(
                            id = item.getString("id"),
                            profileId = item.getString("profileId"),
                            name = item.getString("name"),
                            color = TabStackColor.fromWireValue(item.optString("color")),
                            tabIds = tabIds,
                            previewTabId = item.optString("previewTabId")
                                .takeIf(String::isNotBlank),
                            collapsedAnchorTabId = item.optString("collapsedAnchorTabId")
                                .takeIf(String::isNotBlank),
                            isCollapsed = item.optBoolean("isCollapsed", false),
                        ),
                    )
                }
            }
            TabStackRules.persistent(stacks, tabs)
        }.getOrDefault(emptyList())
    }

    fun saveTabStacks(stacks: List<TabStack>, tabs: Collection<BrowserTab>) {
        val array = JSONArray()
        TabStackRules.persistent(stacks, tabs).forEach { stack ->
            array.put(
                JSONObject()
                    .put("id", stack.id)
                    .put("profileId", stack.profileId)
                    .put("name", stack.name)
                    .put("color", stack.color.wireValue)
                    .put("tabIds", JSONArray(stack.tabIds))
                    .put("previewTabId", stack.previewTabId)
                    .put("collapsedAnchorTabId", stack.collapsedAnchorTabId)
                    .put("isCollapsed", stack.isCollapsed),
            )
        }
        preferences.edit().putString(KEY_TAB_STACKS, array.toString()).apply()
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
                                val legacyWallpaper = item.optJSONObject("wallpaper")
                                    ?.toProfileWallpaper()
                                val hasTargetWallpapers = item.has("newTabWallpaper") ||
                                    item.has("tabSwitcherWallpaper")
                                add(
                                    BrowserProfile(
                                        id = id,
                                        emoji = emoji,
                                        selectedTabId = item.optString("selectedTabId")
                                            .takeIf(String::isNotBlank),
                                        isolationEnabled = item.optBoolean("isolationEnabled", false),
                                        protection = item.optJSONObject("protection")
                                            ?.toProfileProtection(),
                                        newTabWallpaper = if (hasTargetWallpapers) {
                                            item.optJSONObject("newTabWallpaper")
                                                ?.toProfileWallpaper()
                                        } else {
                                            legacyWallpaper
                                        },
                                        tabSwitcherWallpaper = if (hasTargetWallpapers) {
                                            item.optJSONObject("tabSwitcherWallpaper")
                                                ?.toProfileWallpaper()
                                        } else {
                                            legacyWallpaper
                                        },
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
                    .put("isolationEnabled", profile.isolationEnabled)
                    .put("protection", profile.protection.toJson())
                    .put(
                        "newTabWallpaper",
                        profile.newTabWallpaper.toJson(),
                    )
                    .put(
                        "tabSwitcherWallpaper",
                        profile.tabSwitcherWallpaper.toJson(),
                    ),
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

    @Synchronized
    fun loadHistory(): List<HistoryEntry> = loadArray(KEY_HISTORY) { item ->
        HistoryEntry(
            url = item.getString("url"),
            title = item.optString("title"),
            lastVisitedAt = item.optLong("lastVisitedAt"),
            profileId = item.optString("profileId", DEFAULT_PROFILE_ID)
                .takeIf(String::isNotBlank)
                ?: DEFAULT_PROFILE_ID,
            visitId = item.optString("visitId"),
        )
    }

    @Synchronized
    fun saveHistory(history: List<HistoryEntry>) {
        preferences.edit().putString(KEY_HISTORY, encodeHistory(history)).apply()
    }

    internal fun commitHistory(history: List<HistoryEntry>): Boolean =
        preferences.edit().putString(KEY_HISTORY, encodeHistory(history)).commit()

    internal fun saveHistoryAndSessionState(
        history: List<HistoryEntry>,
        sessionActive: Boolean,
    ): Boolean = preferences.edit()
        .putString(KEY_HISTORY, encodeHistory(history))
        .putBoolean(KEY_HISTORY_SESSION_ACTIVE, sessionActive)
        .commit()

    internal fun saveHistoryAndTrailRedaction(
        history: List<HistoryEntry>,
        redaction: PendingCandyTrailRedaction,
        sessionActive: Boolean? = null,
    ): Boolean = synchronized(HISTORY_TRAIL_JOURNAL_LOCK) {
        val pending = loadPendingCandyTrailRedactions()
            .filterNot { existing -> existing.id == redaction.id } + redaction
        preferences.edit()
            .putString(KEY_HISTORY, encodeHistory(history))
            .putString(KEY_PENDING_CANDY_TRAIL_REDACTIONS, encodeTrailRedactions(pending))
            .apply {
                if (sessionActive != null) {
                    putBoolean(KEY_HISTORY_SESSION_ACTIVE, sessionActive)
                }
            }
            .commit()
    }

    @Synchronized
    internal fun loadPendingCandyTrailRedactions(): List<PendingCandyTrailRedaction> =
        loadArray(KEY_PENDING_CANDY_TRAIL_REDACTIONS) { item ->
            PendingCandyTrailRedaction(
                id = item.getString("id"),
                tabIds = buildSet {
                    val values = item.getJSONArray("tabIds")
                    repeat(values.length()) { index ->
                        values.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                },
                sinceInclusiveMillis = item.getLong("sinceInclusiveMillis"),
                untilExclusiveMillis = item.getLong("untilExclusiveMillis"),
            )
        }.filter { redaction ->
            redaction.id.isNotBlank() &&
                redaction.tabIds.isNotEmpty() &&
                redaction.sinceInclusiveMillis < redaction.untilExclusiveMillis
        }

    internal fun removePendingCandyTrailRedaction(id: String): Boolean =
        synchronized(HISTORY_TRAIL_JOURNAL_LOCK) {
            val retained = loadPendingCandyTrailRedactions().filterNot { redaction ->
                redaction.id == id
            }
            preferences.edit()
                .putString(KEY_PENDING_CANDY_TRAIL_REDACTIONS, encodeTrailRedactions(retained))
                .commit()
        }

    internal fun loadHistoryRecordingMode(): HistoryRecordingMode =
        HistoryRecordingMode.fromStoredValue(
            preferences.getString(KEY_HISTORY_RECORDING_MODE, null),
        )

    internal fun saveHistoryRecordingMode(
        mode: HistoryRecordingMode,
        sessionActive: Boolean = mode == HistoryRecordingMode.ClearOnExit,
    ): Boolean = preferences.edit()
        .putString(KEY_HISTORY_RECORDING_MODE, mode.storedId)
        .putBoolean(KEY_HISTORY_SESSION_ACTIVE, sessionActive)
        .commit()

    internal fun loadHistorySessionActive(): Boolean =
        preferences.getBoolean(KEY_HISTORY_SESSION_ACTIVE, false)

    internal fun saveHistorySessionActive(active: Boolean): Boolean =
        preferences.edit().putBoolean(KEY_HISTORY_SESSION_ACTIVE, active).commit()

    @Synchronized
    fun loadFavorites(): List<FavoriteEntry> = loadFavoriteLibrary().favorites

    @Synchronized
    fun loadFavoriteLibrary(): FavoriteLibrary {
        val raw = preferences.getString(KEY_FAVORITES, null) ?: return FavoriteLibrary()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return FavoriteLibrary()
        val entries = buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::readFavoriteLibraryEntry)?.let(::add)
            }
        }
        return BrowsingFavoritesRules.normalizeLibrary(FavoriteLibrary(entries))
    }

    @Synchronized
    fun saveFavorites(favorites: List<FavoriteEntry>) {
        saveFavoriteLibrary(FavoriteLibrary(favorites))
    }

    @Synchronized
    fun saveFavoriteLibrary(library: FavoriteLibrary) {
        saveArray(
            key = KEY_FAVORITES,
            values = BrowsingFavoritesRules.normalizeLibrary(library).entries,
            write = ::writeFavoriteLibraryEntry,
        )
    }

    internal fun saveFavoritesCommitted(
        favorites: List<FavoriteEntry>,
        expectedCurrent: List<FavoriteEntry>? = null,
    ): Boolean = saveFavoriteLibraryCommitted(
        library = FavoriteLibrary(favorites),
        expectedCurrent = expectedCurrent?.let(::FavoriteLibrary),
    )

    internal fun saveFavoriteLibraryCommitted(
        library: FavoriteLibrary,
        expectedCurrent: FavoriteLibrary? = null,
    ): Boolean =
        synchronized(FAVORITES_COMMIT_LOCK) {
            synchronized(this) favoriteWrite@{
                if (expectedCurrent != null && loadFavoriteLibrary() != expectedCurrent) {
                    return@favoriteWrite false
                }
                saveArrayCommitted(
                    key = KEY_FAVORITES,
                    values = BrowsingFavoritesRules.normalizeLibrary(library).entries,
                    write = ::writeFavoriteLibraryEntry,
                )
            }
        }

    internal fun mergeImportedFavoritesCommitted(
        imported: List<FavoriteEntry>,
    ): FavoriteBookmarkMergeResult? = synchronized(FAVORITES_COMMIT_LOCK) {
        val current = loadFavoriteLibrary()
        val result = FavoriteBookmarkImportRules.merge(
            current = current.favorites,
            imported = imported,
        )
        val importedRootEntries = result.favorites.take(result.importedCount)
        val merged = FavoriteLibrary(
            entries = importedRootEntries + current.entries,
        )
        if (
            result.importedCount == 0 ||
            saveFavoriteLibraryCommitted(
                library = merged,
                expectedCurrent = current,
            )
        ) {
            result
        } else {
            null
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
    fun loadDesktopViewDomains(): Map<String, Set<String>> =
        loadProfileHosts(
            key = KEY_DESKTOP_VIEW_DOMAINS,
            normalizeHost = DesktopSiteRules::normalizedDomain,
            limit = DesktopSiteRules.MAX_PER_PROFILE,
        )

    @Synchronized
    fun saveDesktopViewDomains(domainsByProfile: Map<String, Set<String>>) {
        saveProfileHosts(
            key = KEY_DESKTOP_VIEW_DOMAINS,
            hostsByProfile = domainsByProfile,
            normalizeHost = DesktopSiteRules::normalizedDomain,
            limit = DesktopSiteRules.MAX_PER_PROFILE,
        )
    }

    @Synchronized
    fun loadAlwaysBlockPopupDomains(): Map<String, Set<String>> =
        loadProfileHosts(
            key = KEY_ALWAYS_BLOCK_POPUP_DOMAINS,
            normalizeHost = PopupSiteRules::normalizedDomain,
            limit = PopupSiteRules.MAX_PER_PROFILE,
        )

    @Synchronized
    fun saveAlwaysBlockPopupDomains(domainsByProfile: Map<String, Set<String>>) {
        saveProfileHosts(
            key = KEY_ALWAYS_BLOCK_POPUP_DOMAINS,
            hostsByProfile = domainsByProfile,
            normalizeHost = PopupSiteRules::normalizedDomain,
            limit = PopupSiteRules.MAX_PER_PROFILE,
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
                    forcePageZooming = item.optBoolean("forcePageZoomingOverride", false)
                        .takeIf { item.has("forcePageZoomingOverride") },
                    forceSafeArea = item.optBoolean("forceSafeAreaOverride", false)
                        .takeIf { item.has("forceSafeAreaOverride") },
                    thirdPartyLoginAllowed = item.optBoolean(
                        "thirdPartyLoginAllowedOverride",
                        false,
                    ).takeIf { item.has("thirdPartyLoginAllowedOverride") },
                    captchaCompatibilityAllowed = item.optBoolean(
                        "captchaCompatibilityAllowedOverride",
                        false,
                    ).takeIf { item.has("captchaCompatibilityAllowedOverride") },
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
                    overrides.forcePageZooming?.let { enabled ->
                        item.put("forcePageZoomingOverride", enabled)
                    }
                    overrides.forceSafeArea?.let { enabled ->
                        item.put("forceSafeAreaOverride", enabled)
                    }
                    overrides.thirdPartyLoginAllowed?.let { enabled ->
                        item.put("thirdPartyLoginAllowedOverride", enabled)
                    }
                    overrides.captchaCompatibilityAllowed?.let { enabled ->
                        item.put("captchaCompatibilityAllowedOverride", enabled)
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

    fun loadPendingTabClearOnTaskRemoval(): Boolean =
        preferences.getBoolean(KEY_PENDING_TAB_CLEAR_ON_TASK_REMOVAL, false)

    fun savePendingTabClearOnTaskRemoval(pending: Boolean): Boolean = preferences.edit()
        .putBoolean(KEY_PENDING_TAB_CLEAR_ON_TASK_REMOVAL, pending)
        .commit()

    fun loadResidentTabLimit(): Int = runCatching {
        preferences.getInt(
            KEY_RESIDENT_TAB_LIMIT,
            BrowserSessionResidencyRules.DEFAULT_LIMIT,
        )
    }.getOrDefault(BrowserSessionResidencyRules.DEFAULT_LIMIT)
        .let(BrowserSessionResidencyRules::normalizedLimit)

    fun saveResidentTabLimit(limit: Int) {
        preferences.edit()
            .putInt(KEY_RESIDENT_TAB_LIMIT, BrowserSessionResidencyRules.normalizedLimit(limit))
            .apply()
    }

    fun loadSearchEngine(): SearchEngine =
        SearchEngine.fromStableId(preferences.getString(KEY_SEARCH_ENGINE, null))

    fun saveSearchEngine(searchEngine: SearchEngine) {
        preferences.edit().putString(KEY_SEARCH_ENGINE, searchEngine.stableId).apply()
    }

    fun loadPageTranslationProvider(): PageTranslationProvider =
        PageTranslationProvider.fromStableId(
            preferences.getString(KEY_PAGE_TRANSLATION_PROVIDER, null),
        )

    fun savePageTranslationProvider(provider: PageTranslationProvider) {
        preferences.edit().putString(KEY_PAGE_TRANSLATION_PROVIDER, provider.stableId).apply()
    }

    fun loadSearxngSettings(): SearxngSettings = SearxngRules.sanitize(
        SearxngSettings(
            instanceUrl = preferences.getString(KEY_SEARXNG_INSTANCE_URL, null).orEmpty(),
            suggestionFallback = SearchSuggestionProvider.fromStableId(
                preferences.getString(KEY_SEARXNG_SUGGESTION_FALLBACK, null),
                fallback = SearchSuggestionProvider.None,
            ),
        ),
    )

    fun saveSearxngSettings(settings: SearxngSettings) {
        val safeSettings = SearxngRules.sanitize(settings)
        preferences.edit()
            .putString(KEY_SEARXNG_INSTANCE_URL, safeSettings.instanceUrl)
            .putString(
                KEY_SEARXNG_SUGGESTION_FALLBACK,
                safeSettings.suggestionFallback.stableId,
            )
            .apply()
    }

    fun loadAiModeToggleVisible(): Boolean =
        preferences.getBoolean(KEY_AI_MODE_TOGGLE_VISIBLE, false)

    fun saveAiModeToggleVisible(visible: Boolean) {
        preferences.edit().putBoolean(KEY_AI_MODE_TOGGLE_VISIBLE, visible).apply()
    }

    fun loadRecallEnabled(): Boolean = preferences.getBoolean(KEY_RECALL_ENABLED, false)

    fun saveRecallEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RECALL_ENABLED, enabled).apply()
    }

    fun loadSearchSuggestionProvider(
        fallback: SearchSuggestionProvider = SearchSuggestionProvider.DuckDuckGo,
    ): SearchSuggestionProvider =
        SearchSuggestionProvider.fromStableId(
            preferences.getString(KEY_SEARCH_SUGGESTION_PROVIDER, null),
            fallback = fallback,
        )

    fun saveSearchSuggestionProvider(provider: SearchSuggestionProvider) {
        preferences.edit().putString(KEY_SEARCH_SUGGESTION_PROVIDER, provider.stableId).apply()
    }

    fun loadHistorySuggestionsEnabled(): Boolean =
        preferences.getBoolean(KEY_HISTORY_SUGGESTIONS_ENABLED, true)

    fun saveHistorySuggestionsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HISTORY_SUGGESTIONS_ENABLED, enabled).apply()
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

    fun loadTabStackFolderMode(): TabOverviewMode =
        TabOverviewMode.fromWireValue(
            value = preferences.getString(KEY_TAB_STACK_FOLDER_MODE, null),
            fallback = TabOverviewMode.Grid,
        )

    fun saveTabStackFolderMode(mode: TabOverviewMode) {
        preferences.edit().putString(KEY_TAB_STACK_FOLDER_MODE, mode.wireValue).apply()
    }

    fun loadTabListStartsAtBottom(): Boolean =
        preferences.getBoolean(KEY_TAB_LIST_STARTS_AT_BOTTOM, false)

    fun saveTabListStartsAtBottom(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_TAB_LIST_STARTS_AT_BOTTOM, enabled).apply()
    }

    fun loadAutomaticTabSortingEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTOMATIC_TAB_SORTING_ENABLED, false)

    fun saveAutomaticTabSortingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOMATIC_TAB_SORTING_ENABLED, enabled).apply()
    }

    fun loadClosedTabUndoEnabled(): Boolean =
        preferences.getBoolean(KEY_CLOSED_TAB_UNDO_ENABLED, false)

    fun saveClosedTabUndoEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CLOSED_TAB_UNDO_ENABLED, enabled).apply()
    }

    fun loadAddressBarDocked(): Boolean =
        runCatching { preferences.getBoolean(KEY_ADDRESS_BAR_DOCKED, false) }.getOrDefault(false)

    fun saveAddressBarDocked(docked: Boolean) {
        preferences.edit().putBoolean(KEY_ADDRESS_BAR_DOCKED, docked).apply()
    }

    fun loadAddressBarDockPlacement(): AddressBarDockPlacement? {
        if (!loadAddressBarDocked()) return null
        return loadLastAddressBarDockPlacement() ?: AddressBarDockPlacement.Default
    }

    fun loadLastAddressBarDockPlacement(): AddressBarDockPlacement? {
        if (
            !preferences.contains(KEY_ADDRESS_BAR_DOCK_EDGE) &&
            !preferences.contains(KEY_ADDRESS_BAR_DOCK_VERTICAL_FRACTION)
        ) {
            return null
        }
        return AddressBarDockPlacement(
            edge = AddressBarDockEdge.fromWireValue(
                runCatching { preferences.getString(KEY_ADDRESS_BAR_DOCK_EDGE, null) }
                    .getOrNull(),
            ),
            verticalFraction = runCatching {
                preferences.getFloat(KEY_ADDRESS_BAR_DOCK_VERTICAL_FRACTION, 0f)
            }.getOrDefault(0f),
        ).normalized()
    }

    fun saveAddressBarDockPlacement(placement: AddressBarDockPlacement?) {
        val normalized = placement?.normalized()
        preferences.edit()
            .putBoolean(KEY_ADDRESS_BAR_DOCKED, normalized != null)
            .apply {
                if (normalized != null) {
                    putString(KEY_ADDRESS_BAR_DOCK_EDGE, normalized.edge.wireValue)
                    putFloat(
                        KEY_ADDRESS_BAR_DOCK_VERTICAL_FRACTION,
                        normalized.verticalFraction,
                    )
                }
            }
            .apply()
    }

    fun loadAddressBarDockingEnabled(): Boolean =
        preferences.getBoolean(KEY_ADDRESS_BAR_DOCKING_ENABLED, true)

    fun saveAddressBarDockingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ADDRESS_BAR_DOCKING_ENABLED, enabled).apply()
    }

    fun loadExternalLinkPreviewEnabled(): Boolean =
        preferences.getBoolean(KEY_EXTERNAL_LINK_PREVIEW_ENABLED, false)

    fun saveExternalLinkPreviewEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EXTERNAL_LINK_PREVIEW_ENABLED, enabled).apply()
    }

    fun loadExternalAppLinkHandling(): ExternalAppLinkHandling =
        ExternalAppLinkHandling.fromStableId(
            preferences.getString(KEY_EXTERNAL_APP_LINK_HANDLING, null),
        )

    fun saveExternalAppLinkHandling(handling: ExternalAppLinkHandling) {
        preferences.edit().putString(KEY_EXTERNAL_APP_LINK_HANDLING, handling.stableId).apply()
    }

    fun loadLinkLongPressAction(): LinkLongPressAction = LinkLongPressAction.fromStableId(
        preferences.getString(KEY_LINK_LONG_PRESS_ACTION, null),
    )

    fun saveLinkLongPressAction(action: LinkLongPressAction) {
        preferences.edit().putString(KEY_LINK_LONG_PRESS_ACTION, action.stableId).apply()
    }

    fun loadAddressBarLongPressAction(): AddressBarLongPressAction =
        AddressBarLongPressAction.fromStableId(
            preferences.getString(KEY_ADDRESS_BAR_LONG_PRESS_ACTION, null),
        )

    fun saveAddressBarLongPressAction(action: AddressBarLongPressAction) {
        preferences.edit().putString(KEY_ADDRESS_BAR_LONG_PRESS_ACTION, action.stableId).apply()
    }

    fun loadLinkPeekActionLayout(): LinkPeekActionLayout {
        val stored = preferences.getString(KEY_LINK_PEEK_ACTION_LAYOUT, null)
            ?: return LinkPeekActionLayout.Default
        return runCatching {
            val encoded = JSONArray(stored)
            LinkPeekActionLayoutRules.fromWireValues(
                List(encoded.length()) { index -> encoded.opt(index) as? String },
            )
        }.getOrDefault(LinkPeekActionLayout.Default)
    }

    fun saveLinkPeekActionLayout(layout: LinkPeekActionLayout) {
        val normalized = LinkPeekActionLayoutRules.normalize(layout)
        val encoded = JSONArray().apply {
            normalized.actions.forEach { action -> put(action?.wireValue ?: JSONObject.NULL) }
        }
        preferences.edit().putString(KEY_LINK_PEEK_ACTION_LAYOUT, encoded.toString()).apply()
    }

    fun loadAddressBarActionLayout(): AddressBarActionLayout {
        if (!preferences.contains(KEY_ADDRESS_BAR_ACTION_LAYOUT)) {
            val legacyTabButtonVisible = runCatching {
                preferences.getBoolean(KEY_TAB_BUTTON_VISIBLE, true)
            }.getOrDefault(true)
            val migrated = AddressBarActionLayout.Default.copy(
                beforeAddress = AddressBarActionLayout.Default.beforeAddress
                    .takeIf { legacyTabButtonVisible }
                    .orEmpty(),
            )
            saveAddressBarActionLayout(migrated)
            preferences.edit().remove(KEY_TAB_BUTTON_VISIBLE).apply()
            return migrated
        }
        val stored = runCatching {
            val root = JSONObject(preferences.getString(KEY_ADDRESS_BAR_ACTION_LAYOUT, null)!!)
            AddressBarActionLayoutRules.fromWireLists(
                beforeAddress = root.optJSONArray("beforeAddress").stringValues(),
                afterAddress = root.optJSONArray("afterAddress").stringValues(),
            )
        }.getOrElse { AddressBarActionLayout.Default }
        val normalized = AddressBarActionLayoutRules.normalize(stored)
        saveAddressBarActionLayout(normalized)
        return normalized
    }

    fun saveAddressBarActionLayout(layout: AddressBarActionLayout) {
        val normalized = AddressBarActionLayoutRules.normalize(layout)
        val root = JSONObject()
            .put(
                "beforeAddress",
                JSONArray(normalized.beforeAddress.map(AddressBarAction::wireValue)),
            )
            .put(
                "afterAddress",
                JSONArray(normalized.afterAddress.map(AddressBarAction::wireValue)),
            )
        preferences.edit().putString(KEY_ADDRESS_BAR_ACTION_LAYOUT, root.toString()).apply()
    }

    fun loadBrowserMenuLayout(): BrowserMenuLayout {
        val stored = preferences.getString(KEY_BROWSER_MENU_LAYOUT, null)
            ?: return BrowserMenuLayout.Default
        return runCatching {
            val root = JSONObject(stored)
            BrowserMenuLayoutRules.fromWireValues(
                root.keys().asSequence().associateWith { key -> root.optString(key) },
            )
        }.getOrDefault(BrowserMenuLayout.Default)
    }

    fun saveBrowserMenuLayout(layout: BrowserMenuLayout) {
        val root = JSONObject()
        BrowserMenuLayoutRules.toWireValues(layout).forEach { (entry, location) ->
            root.put(entry, location)
        }
        preferences.edit().putString(KEY_BROWSER_MENU_LAYOUT, root.toString()).apply()
    }

    fun loadFullImmersiveModeEnabled(): Boolean =
        preferences.getBoolean(KEY_FULL_IMMERSIVE_MODE_ENABLED, false)

    fun saveFullImmersiveModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FULL_IMMERSIVE_MODE_ENABLED, enabled).apply()
    }

    fun loadStartupAnimationEnabled(): Boolean =
        preferences.getBoolean(KEY_STARTUP_ANIMATION_ENABLED, true)

    fun saveStartupAnimationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_STARTUP_ANIMATION_ENABLED, enabled).apply()
    }

    fun loadStartupAddressFocusMode(): StartupAddressFocusMode =
        StartupAddressFocusMode.fromStableId(
            preferences.getString(KEY_STARTUP_ADDRESS_FOCUS_MODE, null),
        )

    fun saveStartupAddressFocusMode(mode: StartupAddressFocusMode) {
        preferences.edit().putString(KEY_STARTUP_ADDRESS_FOCUS_MODE, mode.stableId).apply()
    }

    fun loadHttpPasswordAutofillEnabled(): Boolean =
        preferences.getBoolean(KEY_HTTP_PASSWORD_AUTOFILL_ENABLED, false)

    fun saveHttpPasswordAutofillEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HTTP_PASSWORD_AUTOFILL_ENABLED, enabled).apply()
    }

    fun loadFavoriteLaunchAnimationEnabled(): Boolean =
        preferences.getBoolean(KEY_FAVORITE_LAUNCH_ANIMATION_ENABLED, true)

    fun saveFavoriteLaunchAnimationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FAVORITE_LAUNCH_ANIMATION_ENABLED, enabled).apply()
    }

    fun loadFavoriteAnimationSpeed(): FavoriteAnimationSpeed =
        FavoriteAnimationSpeed.fromWireValue(
            preferences.getString(KEY_FAVORITE_ANIMATION_SPEED, null),
        )

    fun saveFavoriteAnimationSpeed(speed: FavoriteAnimationSpeed) {
        preferences.edit().putString(KEY_FAVORITE_ANIMATION_SPEED, speed.wireValue).apply()
    }

    fun loadOpenHomeOnStartupEnabled(): Boolean =
        preferences.getBoolean(KEY_OPEN_HOME_ON_STARTUP_ENABLED, false)

    fun saveOpenHomeOnStartupEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_OPEN_HOME_ON_STARTUP_ENABLED, enabled).apply()
    }

    fun loadScrollBarEnabled(): Boolean = preferences.getBoolean(KEY_SCROLL_BAR_ENABLED, false)

    fun saveScrollBarEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SCROLL_BAR_ENABLED, enabled).apply()
    }

    fun loadInlineMediaPlayerMode(): InlineMediaPlayerMode {
        val storedMode = preferences.getString(KEY_INLINE_MEDIA_PLAYER_MODE, null)
        if (storedMode != null) return InlineMediaPlayerMode.fromStableId(storedMode)
        return if (preferences.getBoolean(KEY_INLINE_MEDIA_PLAYER_ENABLED, false)) {
            InlineMediaPlayerMode.ButtonInlineAndFullscreen
        } else {
            InlineMediaPlayerMode.Default
        }
    }

    fun saveInlineMediaPlayerMode(mode: InlineMediaPlayerMode) {
        preferences.edit()
            .putString(KEY_INLINE_MEDIA_PLAYER_MODE, mode.stableId)
            .remove(KEY_INLINE_MEDIA_PLAYER_ENABLED)
            .apply()
    }

    fun loadDeveloperOptionsUnlocked(): Boolean =
        preferences.getBoolean(KEY_DEVELOPER_OPTIONS_UNLOCKED, false)

    fun saveDeveloperOptionsUnlocked(unlocked: Boolean) {
        preferences.edit().putBoolean(KEY_DEVELOPER_OPTIONS_UNLOCKED, unlocked).apply()
    }

    fun loadDeveloperSettings(): DeveloperSettings = DeveloperSettings(
        browserChromeScrollDispatchMode = BrowserChromeScrollDispatchMode.fromStableId(
            preferences.getString(KEY_DEVELOPER_BROWSER_CHROME_SCROLL_DISPATCH_MODE, null),
        ),
        safeAreaLayoutQuietPeriodMillis = loadBoundedInt(
            key = KEY_DEVELOPER_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
            defaultValue = DeveloperSettings.DEFAULT_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
            range = DeveloperSettings.MIN_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS..
                DeveloperSettings.MAX_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
        ),
        safeAreaRequiredFailureCount = loadBoundedInt(
            key = KEY_DEVELOPER_SAFE_AREA_REQUIRED_FAILURE_COUNT,
            defaultValue = DeveloperSettings.DEFAULT_SAFE_AREA_REQUIRED_FAILURE_COUNT,
            range = DeveloperSettings.MIN_SAFE_AREA_REQUIRED_FAILURE_COUNT..
                DeveloperSettings.MAX_SAFE_AREA_REQUIRED_FAILURE_COUNT,
        ),
        forceSafeAreaFallback = preferences.getBoolean(
            KEY_DEVELOPER_FORCE_SAFE_AREA_FALLBACK,
            false,
        ),
        geckoSafeAreaSettings = GeckoSafeAreaSettings(
            enabled = loadBoolean(KEY_GECKO_SAFE_AREA_ENABLED, true),
            recheckAddedElements = loadBoolean(KEY_GECKO_SAFE_AREA_RECHECK_ADDED_ELEMENTS, true),
            recheckChangedElements = loadBoolean(KEY_GECKO_SAFE_AREA_RECHECK_CHANGED_ELEMENTS, true),
            requireInteractionForUpdates = loadBoolean(
                KEY_GECKO_SAFE_AREA_REQUIRE_INTERACTION_FOR_UPDATES,
                true,
            ),
            recheckOnResize = loadBoolean(KEY_GECKO_SAFE_AREA_RECHECK_ON_RESIZE, true),
            interactionWindowMillis = loadBoundedInt(
                key = KEY_GECKO_SAFE_AREA_INTERACTION_WINDOW_MILLIS,
                defaultValue = GeckoSafeAreaSettings.DEFAULT_INTERACTION_WINDOW_MILLIS,
                range = GeckoSafeAreaSettings.MIN_INTERACTION_WINDOW_MILLIS..
                    GeckoSafeAreaSettings.MAX_INTERACTION_WINDOW_MILLIS,
            ),
            mutationDebounceMillis = loadBoundedInt(
                key = KEY_GECKO_SAFE_AREA_MUTATION_DEBOUNCE_MILLIS,
                defaultValue = GeckoSafeAreaSettings.DEFAULT_MUTATION_DEBOUNCE_MILLIS,
                range = GeckoSafeAreaSettings.MIN_MUTATION_DEBOUNCE_MILLIS..
                    GeckoSafeAreaSettings.MAX_MUTATION_DEBOUNCE_MILLIS,
            ),
            maxElementsPerBatch = loadBoundedInt(
                key = KEY_GECKO_SAFE_AREA_MAX_ELEMENTS_PER_BATCH,
                defaultValue = GeckoSafeAreaSettings.DEFAULT_MAX_ELEMENTS_PER_BATCH,
                range = GeckoSafeAreaSettings.MIN_MAX_ELEMENTS_PER_BATCH..
                    GeckoSafeAreaSettings.MAX_MAX_ELEMENTS_PER_BATCH,
            ),
            maxBatchDurationMillis = loadBoundedInt(
                key = KEY_GECKO_SAFE_AREA_MAX_BATCH_DURATION_MILLIS,
                defaultValue = GeckoSafeAreaSettings.DEFAULT_MAX_BATCH_DURATION_MILLIS,
                range = GeckoSafeAreaSettings.MIN_MAX_BATCH_DURATION_MILLIS..
                    GeckoSafeAreaSettings.MAX_MAX_BATCH_DURATION_MILLIS,
            ),
            maxInitialElements = loadBoundedInt(
                key = KEY_GECKO_SAFE_AREA_MAX_INITIAL_ELEMENTS,
                defaultValue = GeckoSafeAreaSettings.DEFAULT_MAX_INITIAL_ELEMENTS,
                range = GeckoSafeAreaSettings.MIN_MAX_INITIAL_ELEMENTS..
                    GeckoSafeAreaSettings.MAX_MAX_INITIAL_ELEMENTS,
            ),
        ),
    ).normalized()

    fun saveDeveloperSettings(settings: DeveloperSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putString(
                KEY_DEVELOPER_BROWSER_CHROME_SCROLL_DISPATCH_MODE,
                normalized.browserChromeScrollDispatchMode.stableId,
            )
            .putInt(
                KEY_DEVELOPER_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
                normalized.safeAreaLayoutQuietPeriodMillis,
            )
            .putInt(
                KEY_DEVELOPER_SAFE_AREA_REQUIRED_FAILURE_COUNT,
                normalized.safeAreaRequiredFailureCount,
            )
            .putBoolean(
                KEY_DEVELOPER_FORCE_SAFE_AREA_FALLBACK,
                normalized.forceSafeAreaFallback,
            )
            .putBoolean(KEY_GECKO_SAFE_AREA_ENABLED, normalized.geckoSafeAreaSettings.enabled)
            .putBoolean(
                KEY_GECKO_SAFE_AREA_RECHECK_ADDED_ELEMENTS,
                normalized.geckoSafeAreaSettings.recheckAddedElements,
            )
            .putBoolean(
                KEY_GECKO_SAFE_AREA_RECHECK_CHANGED_ELEMENTS,
                normalized.geckoSafeAreaSettings.recheckChangedElements,
            )
            .putBoolean(
                KEY_GECKO_SAFE_AREA_REQUIRE_INTERACTION_FOR_UPDATES,
                normalized.geckoSafeAreaSettings.requireInteractionForUpdates,
            )
            .putBoolean(
                KEY_GECKO_SAFE_AREA_RECHECK_ON_RESIZE,
                normalized.geckoSafeAreaSettings.recheckOnResize,
            )
            .putInt(
                KEY_GECKO_SAFE_AREA_INTERACTION_WINDOW_MILLIS,
                normalized.geckoSafeAreaSettings.interactionWindowMillis,
            )
            .putInt(
                KEY_GECKO_SAFE_AREA_MUTATION_DEBOUNCE_MILLIS,
                normalized.geckoSafeAreaSettings.mutationDebounceMillis,
            )
            .putInt(
                KEY_GECKO_SAFE_AREA_MAX_ELEMENTS_PER_BATCH,
                normalized.geckoSafeAreaSettings.maxElementsPerBatch,
            )
            .putInt(
                KEY_GECKO_SAFE_AREA_MAX_BATCH_DURATION_MILLIS,
                normalized.geckoSafeAreaSettings.maxBatchDurationMillis,
            )
            .putInt(
                KEY_GECKO_SAFE_AREA_MAX_INITIAL_ELEMENTS,
                normalized.geckoSafeAreaSettings.maxInitialElements,
            )
            .apply()
    }

    fun loadVideoAutoplayBlocked(): Boolean =
        preferences.getBoolean(KEY_VIDEO_AUTOPLAY_BLOCKED, true)

    fun saveVideoAutoplayBlocked(blocked: Boolean) {
        preferences.edit().putBoolean(KEY_VIDEO_AUTOPLAY_BLOCKED, blocked).apply()
    }

    fun loadWebRtcProtectionMode(): WebRtcProtectionMode = WebRtcProtectionMode.fromStableId(
        preferences.getString(KEY_WEBRTC_PROTECTION_MODE, null),
    )

    fun saveWebRtcProtectionMode(mode: WebRtcProtectionMode) {
        preferences.edit().putString(KEY_WEBRTC_PROTECTION_MODE, mode.stableId).apply()
    }

    fun loadPrivacySignalSettings(): PrivacySignalSettings = PrivacySignalSettings(
        doNotTrackEnabled = preferences.getBoolean(KEY_DO_NOT_TRACK_ENABLED, true),
        globalPrivacyControlEnabled = preferences.getBoolean(
            KEY_GLOBAL_PRIVACY_CONTROL_ENABLED,
            true,
        ),
    )

    fun savePrivacySignalSettings(settings: PrivacySignalSettings) {
        preferences.edit()
            .putBoolean(KEY_DO_NOT_TRACK_ENABLED, settings.doNotTrackEnabled)
            .putBoolean(
                KEY_GLOBAL_PRIVACY_CONTROL_ENABLED,
                settings.globalPrivacyControlEnabled,
            )
            .apply()
    }

    fun loadAutoDeAmpEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTO_DE_AMP_ENABLED, true)

    fun saveAutoDeAmpEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_DE_AMP_ENABLED, enabled).apply()
    }

    fun loadDnsOverHttpsSettings(): DnsOverHttpsSettings = DnsOverHttpsRules.sanitize(
        DnsOverHttpsSettings(
            provider = DnsOverHttpsProvider.fromStableId(
                preferences.getString(KEY_DNS_OVER_HTTPS_PROVIDER, null),
            ),
            customEndpoint = preferences.getString(KEY_DNS_OVER_HTTPS_CUSTOM_ENDPOINT, "")
                .orEmpty(),
        ),
    )

    fun saveDnsOverHttpsSettings(settings: DnsOverHttpsSettings) {
        val sanitized = DnsOverHttpsRules.sanitize(settings)
        preferences.edit()
            .putString(KEY_DNS_OVER_HTTPS_PROVIDER, sanitized.provider.stableId)
            .putString(KEY_DNS_OVER_HTTPS_CUSTOM_ENDPOINT, sanitized.customEndpoint)
            .apply()
    }

    fun loadAndroidBrowserEngineKind(): AndroidBrowserEngineKind =
        AndroidBrowserEngineRules.persistedKind(
            stableId = preferences.getString(KEY_ANDROID_BROWSER_ENGINE, null),
            systemWebViewOnly = BuildConfig.SYSTEM_WEBVIEW_ONLY,
        )

    fun saveAndroidBrowserEngineKind(kind: AndroidBrowserEngineKind): Boolean {
        if (!AndroidBrowserEngineRules.canSelect(kind, BuildConfig.SYSTEM_WEBVIEW_ONLY)) return false
        return preferences.edit().putString(KEY_ANDROID_BROWSER_ENGINE, kind.stableId).commit()
    }

    fun loadAppearanceSettings(): AppearanceSettings {
        val frostedTransparencyPercent = loadBoundedInt(
            key = KEY_FROSTED_TRANSPARENCY_PERCENT,
            defaultValue = AppearanceSettings.DEFAULT_FROSTED_TRANSPARENCY_PERCENT,
            range = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT..
                AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT,
        )
        return AppearanceSettings(
            appearanceMode = BrowserAppearanceMode.fromStableId(
                preferences.getString(KEY_APPEARANCE_MODE, null),
            ),
            animationsEnabled = loadBoolean(KEY_ANIMATIONS_ENABLED, true),
            forceDarkWebsites = runCatching {
                preferences.getBoolean(KEY_FORCE_DARK_WEBSITES, false)
            }.getOrDefault(false),
            webContentFontSizePercent = loadBoundedInt(
                key = KEY_WEB_CONTENT_FONT_SIZE_PERCENT,
                defaultValue = AppearanceSettings.DEFAULT_WEB_CONTENT_FONT_SIZE_PERCENT,
                range = AppearanceSettings.MIN_WEB_CONTENT_FONT_SIZE_PERCENT..
                    AppearanceSettings.MAX_WEB_CONTENT_FONT_SIZE_PERCENT,
            ),
            colorPalette = BrowserColorPalette.fromStableId(
                preferences.getString(KEY_COLOR_PALETTE, null),
            ),
            surfaceStyle = BrowserSurfaceStyle.fromStableId(
                preferences.getString(KEY_SURFACE_STYLE, null),
            ),
            shapeStyle = BrowserShapeStyle.fromStableId(
                preferences.getString(KEY_SHAPE_STYLE, null),
            ),
            addressBarStyle = BrowserAddressBarStyle.fromStableId(
                preferences.getString(KEY_ADDRESS_BAR_STYLE, null),
            ),
            addressBarColorPreset = BrowserAddressBarColorPreset.fromStableId(
                preferences.getString(KEY_ADDRESS_BAR_COLOR_PRESET, null),
            ),
            addressBarCustomColorHex = preferences.getString(
                KEY_ADDRESS_BAR_CUSTOM_COLOR_HEX,
                null,
            ).orEmpty(),
            frostedTransparencyPercent = frostedTransparencyPercent,
            frostedAddressBarTransparencyPercent = loadBoundedInt(
                key = KEY_FROSTED_ADDRESS_BAR_TRANSPARENCY_PERCENT,
                defaultValue = frostedTransparencyPercent,
                range = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT..
                    AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT,
            ),
            frostedBlurPercent = loadBoundedInt(
                key = KEY_FROSTED_BLUR_PERCENT,
                defaultValue = AppearanceSettings.DEFAULT_FROSTED_BLUR_PERCENT,
                range = AppearanceSettings.MIN_FROSTED_BLUR_PERCENT..
                    AppearanceSettings.MAX_FROSTED_BLUR_PERCENT,
            ),
        ).normalized()
    }

    fun saveAppearanceSettings(settings: AppearanceSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putString(KEY_APPEARANCE_MODE, normalized.appearanceMode.stableId)
            .putBoolean(KEY_ANIMATIONS_ENABLED, normalized.animationsEnabled)
            .putBoolean(KEY_FORCE_DARK_WEBSITES, normalized.forceDarkWebsites)
            .putInt(KEY_WEB_CONTENT_FONT_SIZE_PERCENT, normalized.webContentFontSizePercent)
            .putString(KEY_COLOR_PALETTE, normalized.colorPalette.stableId)
            .putString(KEY_SURFACE_STYLE, normalized.surfaceStyle.stableId)
            .putString(KEY_SHAPE_STYLE, normalized.shapeStyle.stableId)
            .putString(KEY_ADDRESS_BAR_STYLE, normalized.addressBarStyle.stableId)
            .putString(KEY_ADDRESS_BAR_COLOR_PRESET, normalized.addressBarColorPreset.stableId)
            .putString(KEY_ADDRESS_BAR_CUSTOM_COLOR_HEX, normalized.addressBarCustomColorHex)
            .putInt(
                KEY_FROSTED_TRANSPARENCY_PERCENT,
                normalized.frostedTransparencyPercent,
            )
            .putInt(
                KEY_FROSTED_ADDRESS_BAR_TRANSPARENCY_PERCENT,
                normalized.frostedAddressBarTransparencyPercent,
            )
            .putInt(KEY_FROSTED_BLUR_PERCENT, normalized.frostedBlurPercent)
            .apply()
    }

    private fun loadBoolean(key: String, defaultValue: Boolean): Boolean =
        runCatching { preferences.getBoolean(key, defaultValue) }.getOrDefault(defaultValue)

    private fun loadBoundedInt(
        key: String,
        defaultValue: Int,
        range: IntRange,
    ): Int = runCatching { preferences.getInt(key, defaultValue) }
        .getOrDefault(defaultValue)
        .coerceIn(range)

    fun loadDownloadSettings(): BrowserDownloadSettings = BrowserDownloadSettings(
        managerMode = DownloadManagerMode.fromStableId(
            preferences.getString(KEY_DOWNLOAD_MANAGER_MODE, null),
        ),
        externalManagerId = preferences.getString(KEY_EXTERNAL_DOWNLOAD_MANAGER_ID, null),
        shareSessionDataWithOneDm = preferences.getBoolean(
            KEY_SHARE_SESSION_DATA_WITH_ONE_DM,
            false,
        ),
        downloadSubdirectory = preferences.getString(KEY_DOWNLOAD_SUBDIRECTORY, null),
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
            .apply {
                if (normalized.downloadSubdirectory == null) {
                    remove(KEY_DOWNLOAD_SUBDIRECTORY)
                } else {
                    putString(KEY_DOWNLOAD_SUBDIRECTORY, normalized.downloadSubdirectory)
                }
            }
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

    private fun <T> saveArrayCommitted(
        key: String,
        values: List<T>,
        write: (T) -> JSONObject,
    ): Boolean = runCatching {
        val array = JSONArray()
        values.forEach { array.put(write(it)) }
        preferences.edit().putString(key, array.toString()).commit()
    }.getOrDefault(false)

    private fun JSONArray?.stringValues(): List<String> = buildList {
        val source = this@stringValues ?: return@buildList
        for (index in 0 until source.length()) {
            (source.opt(index) as? String)?.let(::add)
        }
    }

    private fun encodeHistory(history: List<HistoryEntry>): String {
        val array = JSONArray()
        history.forEach { entry ->
            array.put(
                JSONObject()
                    .put("url", entry.url)
                    .put("title", entry.title)
                    .put("lastVisitedAt", entry.lastVisitedAt)
                    .put("profileId", entry.profileId)
                    .put("visitId", entry.visitId),
            )
        }
        return array.toString()
    }

    private fun encodeTrailRedactions(redactions: List<PendingCandyTrailRedaction>): String {
        val array = JSONArray()
        redactions.forEach { redaction ->
            array.put(
                JSONObject()
                    .put("id", redaction.id)
                    .put("tabIds", JSONArray(redaction.tabIds.toList()))
                    .put("sinceInclusiveMillis", redaction.sinceInclusiveMillis)
                    .put("untilExclusiveMillis", redaction.untilExclusiveMillis),
            )
        }
        return array.toString()
    }

    internal companion object {
        private val HISTORY_TRAIL_JOURNAL_LOCK = Any()
        const val PREFERENCES_NAME = "browser_session"
        const val KEY_TABS = "tabs"
        const val KEY_SELECTED_TAB = "selected_tab"
        const val KEY_TAB_STACKS = "tab_stacks"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_PROFILES_ENABLED = "profiles_enabled"
        const val KEY_BLOCK_ADS = "block_ads"
        const val KEY_HIDE_CONSENT = "hide_consent"
        const val KEY_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
        const val KEY_SITE_EXCEPTIONS = "site_exceptions"
        const val KEY_MUTED_DOMAINS = "muted_domains"
        const val KEY_DESKTOP_VIEW_DOMAINS = "desktop_view_domains"
        const val KEY_ALWAYS_BLOCK_POPUP_DOMAINS = "always_block_popup_domains"
        const val KEY_SITE_PRIVACY_OVERRIDES = "site_privacy_overrides"
        const val KEY_HISTORY = "history"
        const val KEY_HISTORY_RECORDING_MODE = "history_recording_mode"
        const val KEY_HISTORY_SESSION_ACTIVE = "history_session_active"
        const val KEY_PENDING_CANDY_TRAIL_REDACTIONS = "pending_candy_trail_redactions"
        const val KEY_FAVORITES = "favorites"
        private val FAVORITES_COMMIT_LOCK = Any()
        const val KEY_INACTIVE_TAB_LIFETIME = "inactive_tab_lifetime"
        const val KEY_PENDING_TAB_CLEAR_ON_TASK_REMOVAL =
            "pending_tab_clear_on_task_removal"
        const val KEY_RESIDENT_TAB_LIMIT = "resident_tab_limit"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_PAGE_TRANSLATION_PROVIDER = "page_translation_provider"
        const val KEY_SEARXNG_INSTANCE_URL = "searxng_instance_url"
        const val KEY_SEARXNG_SUGGESTION_FALLBACK = "searxng_suggestion_fallback"
        const val KEY_AI_MODE_TOGGLE_VISIBLE = "ai_mode_toggle_visible"
        const val KEY_RECALL_ENABLED = "recall_enabled"
        const val KEY_SEARCH_SUGGESTION_PROVIDER = "search_suggestion_provider"
        const val KEY_HISTORY_SUGGESTIONS_ENABLED = "history_suggestions_enabled"
        const val KEY_DISMISS_RESISTANCE_START_PERCENT = "dismiss_resistance_start_percent"
        const val KEY_TAB_OVERVIEW_MODE = "tab_overview_mode"
        const val KEY_TAB_STACK_FOLDER_MODE = "tab_stack_folder_mode"
        const val KEY_TAB_LIST_STARTS_AT_BOTTOM = "tab_list_starts_at_bottom"
        const val KEY_AUTOMATIC_TAB_SORTING_ENABLED = "automatic_tab_sorting_enabled"
        const val KEY_CLOSED_TAB_UNDO_ENABLED = "closed_tab_undo_enabled"
        const val KEY_ADDRESS_BAR_DOCKED = "address_bar_docked"
        const val KEY_ADDRESS_BAR_DOCK_EDGE = "address_bar_dock_edge"
        const val KEY_ADDRESS_BAR_DOCK_VERTICAL_FRACTION =
            "address_bar_dock_vertical_fraction"
        const val KEY_ADDRESS_BAR_DOCKING_ENABLED = "address_bar_docking_enabled"
        const val KEY_EXTERNAL_LINK_PREVIEW_ENABLED = "external_link_preview_enabled"
        const val KEY_EXTERNAL_APP_LINK_HANDLING = "external_app_link_handling"
        const val KEY_LINK_LONG_PRESS_ACTION = "link_long_press_action"
        const val KEY_ADDRESS_BAR_LONG_PRESS_ACTION = "address_bar_long_press_action"
        const val KEY_LINK_PEEK_ACTION_LAYOUT = "link_peek_action_layout"
        const val KEY_ADDRESS_BAR_ACTION_LAYOUT = "address_bar_action_layout"
        const val KEY_BROWSER_MENU_LAYOUT = "browser_menu_layout"
        const val KEY_TAB_BUTTON_VISIBLE = "tab_button_visible"
        const val KEY_FULL_IMMERSIVE_MODE_ENABLED = "full_immersive_mode_enabled"
        const val KEY_STARTUP_ANIMATION_ENABLED = "startup_animation_enabled"
        const val KEY_STARTUP_ADDRESS_FOCUS_MODE = "startup_address_focus_mode"
        const val KEY_HTTP_PASSWORD_AUTOFILL_ENABLED = "http_password_autofill_enabled"
        const val KEY_FAVORITE_LAUNCH_ANIMATION_ENABLED =
            "favorite_launch_animation_enabled"
        const val KEY_FAVORITE_ANIMATION_SPEED = "favorite_animation_speed"
        const val KEY_OPEN_HOME_ON_STARTUP_ENABLED = "open_home_on_startup_enabled"
        const val KEY_SCROLL_BAR_ENABLED = "scroll_bar_enabled"
        const val KEY_INLINE_MEDIA_PLAYER_ENABLED = "inline_media_player_enabled"
        const val KEY_INLINE_MEDIA_PLAYER_MODE = "inline_media_player_mode"
        const val KEY_DEVELOPER_OPTIONS_UNLOCKED = "developer_options_unlocked"
        const val KEY_DEVELOPER_BROWSER_CHROME_SCROLL_DISPATCH_MODE =
            "developer_browser_chrome_scroll_dispatch_mode"
        const val KEY_DEVELOPER_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS =
            "developer_safe_area_layout_quiet_period_millis"
        const val KEY_DEVELOPER_SAFE_AREA_REQUIRED_FAILURE_COUNT =
            "developer_safe_area_required_failure_count"
        const val KEY_DEVELOPER_FORCE_SAFE_AREA_FALLBACK =
            "developer_force_safe_area_fallback"
        const val KEY_GECKO_SAFE_AREA_ENABLED = "gecko_safe_area_enabled"
        const val KEY_GECKO_SAFE_AREA_RECHECK_ADDED_ELEMENTS = "gecko_safe_area_recheck_added_elements"
        const val KEY_GECKO_SAFE_AREA_RECHECK_CHANGED_ELEMENTS =
            "gecko_safe_area_recheck_changed_elements"
        const val KEY_GECKO_SAFE_AREA_REQUIRE_INTERACTION_FOR_UPDATES =
            "gecko_safe_area_require_interaction_for_updates"
        const val KEY_GECKO_SAFE_AREA_RECHECK_ON_RESIZE = "gecko_safe_area_recheck_on_resize"
        const val KEY_GECKO_SAFE_AREA_INTERACTION_WINDOW_MILLIS =
            "gecko_safe_area_interaction_window_millis"
        const val KEY_GECKO_SAFE_AREA_MUTATION_DEBOUNCE_MILLIS =
            "gecko_safe_area_mutation_debounce_millis"
        const val KEY_GECKO_SAFE_AREA_MAX_ELEMENTS_PER_BATCH = "gecko_safe_area_max_elements_per_batch"
        const val KEY_GECKO_SAFE_AREA_MAX_BATCH_DURATION_MILLIS =
            "gecko_safe_area_max_batch_duration_millis"
        const val KEY_GECKO_SAFE_AREA_MAX_INITIAL_ELEMENTS = "gecko_safe_area_max_initial_elements"
        const val KEY_VIDEO_AUTOPLAY_BLOCKED = "video_autoplay_blocked"
        const val KEY_WEBRTC_PROTECTION_MODE = "webrtc_protection_mode"
        const val KEY_DO_NOT_TRACK_ENABLED = "do_not_track_enabled"
        const val KEY_GLOBAL_PRIVACY_CONTROL_ENABLED = "global_privacy_control_enabled"
        const val KEY_AUTO_DE_AMP_ENABLED = "auto_de_amp_enabled"
        const val KEY_DNS_OVER_HTTPS_PROVIDER = "dns_over_https_provider"
        const val KEY_DNS_OVER_HTTPS_CUSTOM_ENDPOINT = "dns_over_https_custom_endpoint"
        const val KEY_ANDROID_BROWSER_ENGINE = "android_browser_engine"
        const val KEY_APPEARANCE_MODE = "appearance_mode"
        const val KEY_ANIMATIONS_ENABLED = "appearance_animations_enabled"
        const val KEY_FORCE_DARK_WEBSITES = "force_dark_websites"
        const val KEY_WEB_CONTENT_FONT_SIZE_PERCENT = "web_content_font_size_percent"
        const val KEY_COLOR_PALETTE = "color_palette"
        const val KEY_SURFACE_STYLE = "surface_style"
        const val KEY_SHAPE_STYLE = "shape_style"
        const val KEY_ADDRESS_BAR_STYLE = "address_bar_style"
        const val KEY_ADDRESS_BAR_COLOR_PRESET = "address_bar_color_preset"
        const val KEY_ADDRESS_BAR_CUSTOM_COLOR_HEX = "address_bar_custom_color_hex"
        const val KEY_FROSTED_TRANSPARENCY_PERCENT = "frosted_transparency_percent"
        const val KEY_FROSTED_ADDRESS_BAR_TRANSPARENCY_PERCENT =
            "frosted_address_bar_transparency_percent"
        const val KEY_FROSTED_BLUR_PERCENT = "frosted_blur_percent"
        const val KEY_DOWNLOAD_MANAGER_MODE = "download_manager_mode"
        const val KEY_EXTERNAL_DOWNLOAD_MANAGER_ID = "external_download_manager_id"
        const val KEY_DOWNLOAD_SUBDIRECTORY = "download_subdirectory"
        const val KEY_SHARE_SESSION_DATA_WITH_ONE_DM = "share_session_data_with_one_dm"
        const val KEY_WEB_CONTENT_EDGE_TO_EDGE = "web_content_edge_to_edge"
        const val DEFAULT_DISMISS_RESISTANCE_START_PERCENT = 40
        const val MIN_DISMISS_RESISTANCE_START_PERCENT = 10
        const val MAX_DISMISS_RESISTANCE_START_PERCENT = 90
        private const val MAX_TAB_STACKS_JSON_LENGTH = 256 * 1024
    }
}

private fun JSONObject.toProfileWallpaper(): ProfileWallpaper = ProfileWallpaperRules.sanitize(
    ProfileWallpaper(
        zoom = optDouble("zoom", 1.0).toFloat(),
        normalizedPanX = optDouble("normalizedPanX", 0.0).toFloat(),
        normalizedPanY = optDouble("normalizedPanY", 0.0).toFloat(),
    ),
)

private fun ProfileWallpaper?.toJson(): Any = this
    ?.let(ProfileWallpaperRules::sanitize)
    ?.let { wallpaper ->
        JSONObject()
            .put("zoom", wallpaper.zoom.toDouble())
            .put("normalizedPanX", wallpaper.normalizedPanX.toDouble())
            .put("normalizedPanY", wallpaper.normalizedPanY.toDouble())
    }
    ?: JSONObject.NULL

private fun JSONObject.toProfileProtection(): ProfileProtection? {
    val trigger = ProfileLockTrigger.fromWireValue(optString("lockTrigger")) ?: return null
    return ProfileProtectionRules.normalize(
        ProfileProtection(
            lockTrigger = trigger,
            cooldownMinutes = optInt(
                "cooldownMinutes",
                ProfileProtectionRules.DEFAULT_COOLDOWN_MINUTES,
            ),
        ),
    )
}

private fun ProfileProtection?.toJson(): Any = this
    ?.let(ProfileProtectionRules::normalize)
    ?.let { protection ->
        JSONObject()
            .put("lockTrigger", protection.lockTrigger.wireValue)
            .put("cooldownMinutes", protection.cooldownMinutes)
    }
    ?: JSONObject.NULL

private fun writeFavoriteLibraryEntry(entry: FavoriteLibraryEntry): JSONObject = when (entry) {
    is FavoriteEntry -> JSONObject()
        .put("type", "favorite")
        .put("id", entry.id)
        .put("url", entry.url)
        .put("title", entry.title)
        .put("addedAt", entry.addedAt)
        .put("parentFolderId", entry.parentFolderId)
    is FavoriteFolder -> JSONObject()
        .put("type", "folder")
        .put("id", entry.id)
        .put("title", entry.title)
        .put("parentFolderId", entry.parentFolderId)
        .put("icon", entry.icon.toJson())
}

private fun readFavoriteLibraryEntry(item: JSONObject): FavoriteLibraryEntry? = runCatching {
    when (item.optString("type")) {
        "folder" -> FavoriteFolder(
            id = item.optString("id"),
            title = item.optString("title"),
            parentFolderId = item.optString("parentFolderId").takeIf(String::isNotBlank),
            icon = readFavoriteFolderIcon(item),
        )
        else -> {
            val url = item.getString("url")
            FavoriteEntry(
                url = url,
                title = item.optString("title"),
                addedAt = item.optLong("addedAt"),
                id = item.optString("id").ifBlank { favoriteEntryId(url) },
                parentFolderId = item.optString("parentFolderId").takeIf(String::isNotBlank),
            )
        }
    }
}.getOrNull()

private fun readFavoriteFolderIcon(item: JSONObject): FavoriteFolderIcon? {
    val icon = item.optJSONObject("icon") ?: return null
    return when (icon.optString("type")) {
        "emoji" -> icon.optString("value").takeIf(String::isNotBlank)
            ?.let(FavoriteFolderIcon::Emoji)
        "custom" -> FavoriteFolderIcon.Custom
        else -> null
    }
}

private fun FavoriteFolderIcon?.toJson(): Any = when (this) {
    null -> JSONObject.NULL
    is FavoriteFolderIcon.Emoji -> JSONObject().put("type", "emoji").put("value", value)
    FavoriteFolderIcon.Custom -> JSONObject().put("type", "custom")
}
