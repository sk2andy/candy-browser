package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.data.BrowserSessionStore

sealed interface WebViewProfileAssignment {
    val storageKey: String

    data object Default : WebViewProfileAssignment {
        override val storageKey = DEFAULT_STORAGE_KEY
    }

    data class Incognito(val profileName: String) : WebViewProfileAssignment {
        override val storageKey = profileName
    }

    data class Isolated(val profileName: String) : WebViewProfileAssignment {
        override val storageKey = profileName
    }
}

object WebViewProfileRules {
    fun assignment(
        tab: BrowserTab,
        profiles: List<BrowserProfile>,
        multiProfileSupported: Boolean,
        incognitoProfileName: String = "${INCOGNITO_WEBVIEW_PROFILE_PREFIX}test",
    ): WebViewProfileAssignment {
        if (!multiProfileSupported) return WebViewProfileAssignment.Default
        if (tab.isIncognito) return WebViewProfileAssignment.Incognito(incognitoProfileName)
        val profile = profiles.firstOrNull { it.id == tab.profileId }
        return if (profile?.isolationEnabled == true) {
            WebViewProfileAssignment.Isolated(isolatedProfileName(profile.id))
        } else {
            WebViewProfileAssignment.Default
        }
    }

    fun isolatedProfileName(profileId: String): String {
        require(profileId.isNotBlank())
        return buildString(ISOLATED_PROFILE_PREFIX.length + profileId.length * 2) {
            append(ISOLATED_PROFILE_PREFIX)
            profileId.encodeToByteArray().forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    fun isManagedIsolatedProfileName(profileName: String): Boolean =
        profileName.startsWith(ISOLATED_PROFILE_PREFIX)

    fun regularTabIdsForStorageChange(tabs: List<BrowserTab>, profileId: String): Set<String> =
        tabs.asSequence()
            .filter { it.profileId == profileId && !it.isIncognito }
            .mapTo(linkedSetOf(), BrowserTab::id)

    fun tabIdsForProfileDeletion(tabs: List<BrowserTab>, profileId: String): Set<String> =
        tabs.asSequence()
            .filter { it.profileId == profileId }
            .mapTo(linkedSetOf(), BrowserTab::id)

    fun moveTabs(
        tabs: List<BrowserTab>,
        sourceProfileId: String,
        targetProfileId: String,
    ): List<BrowserTab> = tabs.map { tab ->
        if (tab.profileId == sourceProfileId) tab.copy(profileId = targetProfileId) else tab
    }

    fun withVisibleUrl(tab: BrowserTab, url: String?): BrowserTab {
        val visibleUrl = url?.takeIf(String::isNotBlank) ?: return tab
        return if (tab.url == visibleUrl) tab else tab.copy(url = visibleUrl)
    }

    fun storageKeysLosingLastWebView(
        assignments: Map<String, String>,
        removedTabIds: Set<String>,
    ): Set<String> {
        if (removedTabIds.isEmpty()) return emptySet()
        val remainingKeys = assignments.asSequence()
            .filter { (tabId, _) -> tabId !in removedTabIds }
            .mapTo(hashSetOf()) { (_, storageKey) -> storageKey }
        return removedTabIds.asSequence()
            .mapNotNull(assignments::get)
            .filter { it != DEFAULT_STORAGE_KEY && it !in remainingKeys }
            .toCollection(linkedSetOf())
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}

internal class WebViewProfileDeletionCoordinator(
    private val store: BrowserSessionStore,
    private val tryDeleteProfile: (String) -> Boolean,
) {
    fun deleteOrSchedule(profileName: String): Boolean {
        val deleted = tryDeleteProfile(profileName)
        val pending = store.loadPendingWebViewProfileDeletions().toMutableSet()
        if (deleted) pending.remove(profileName) else pending += profileName
        store.savePendingWebViewProfileDeletions(pending)
        return deleted
    }

    fun retry(profileNames: Set<String>) {
        val remaining = profileNames.filterNotTo(linkedSetOf(), tryDeleteProfile)
        store.savePendingWebViewProfileDeletions(remaining)
    }
}

internal const val DEFAULT_STORAGE_KEY = "Default"
internal const val INCOGNITO_WEBVIEW_PROFILE_PREFIX = "candy_incognito_v1_"
internal const val ISOLATED_PROFILE_PREFIX = "candy_profile_v1_"
