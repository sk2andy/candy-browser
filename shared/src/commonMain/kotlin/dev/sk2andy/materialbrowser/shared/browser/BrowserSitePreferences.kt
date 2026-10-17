package dev.sk2andy.materialbrowser.shared.browser

data class BrowserDesktopPreference(
    val profileId: String,
    val host: String,
    val enabled: Boolean,
)

object BrowserSitePreferenceRules {
    const val MAX_PER_PROFILE = 64
    private const val MAX_PROFILE_ID_LENGTH = 256

    fun validProfileId(value: String): Boolean = value.isNotBlank() &&
        value.length <= MAX_PROFILE_ID_LENGTH && value.none { it.code < 32 || it.code == 127 }

    /** Caller supplies validated host identities: registrable domains on Android, exact hosts on iOS. */
    fun withEnabledHost(
        current: Collection<String>,
        host: String,
        enabled: Boolean,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> {
        val retained = current.asSequence().filterNot { it == host }.distinct()
        if (!enabled) return retained.toCollection(linkedSetOf())
        if (limit <= 0) return emptySet()
        return (retained.take(limit - 1) + sequenceOf(host)).toCollection(linkedSetOf())
    }
}

/** WebKit's per-profile desktop preferences, with private overrides scoped to one live tab. */
class BrowserSitePreferenceController {
    private var regularDesktopHosts = emptyMap<String, Set<String>>()
    private var privateDesktopHosts = emptyMap<String, Set<String>>()

    fun restore(entries: List<BrowserDesktopPreference>) {
        regularDesktopHosts = entries.asSequence()
            .filter { it.enabled && BrowserSitePreferenceRules.validProfileId(it.profileId) }
            .mapNotNull { entry ->
                val host = BrowserUrlRules.httpHost("https://${entry.host}") ?: return@mapNotNull null
                if (host != entry.host.lowercase()) return@mapNotNull null
                entry.profileId to host
            }
            .groupBy({ it.first }, { it.second })
            .entries.take(MAX_PROFILES)
            .associate { (profileId, hosts) ->
                profileId to hosts.distinct().take(BrowserSitePreferenceRules.MAX_PER_PROFILE).toSet()
            }
    }

    fun canToggle(tab: BrowserTabState): Boolean =
        BrowserSitePreferenceRules.validProfileId(tab.profileId) &&
            BrowserUrlRules.httpHost(tab.address) != null

    fun isDesktop(tab: BrowserTabState): Boolean = isDesktopForAddress(tab, tab.address)

    fun isDesktopForAddress(tab: BrowserTabState, address: String): Boolean {
        val host = BrowserUrlRules.httpHost(address) ?: return false
        val hosts = if (tab.isPrivate) privateDesktopHosts[tab.id] else regularDesktopHosts[tab.profileId]
        return host in hosts.orEmpty()
    }

    fun setDesktop(tab: BrowserTabState, enabled: Boolean) {
        if (!canToggle(tab)) return
        val host = BrowserUrlRules.httpHost(tab.address) ?: return
        if (tab.isPrivate) {
            privateDesktopHosts = privateDesktopHosts + (tab.id to BrowserSitePreferenceRules.withEnabledHost(
                current = privateDesktopHosts[tab.id].orEmpty(), host = host, enabled = enabled,
            ))
        } else {
            if (tab.profileId !in regularDesktopHosts && regularDesktopHosts.size >= MAX_PROFILES) return
            regularDesktopHosts = regularDesktopHosts + (tab.profileId to BrowserSitePreferenceRules.withEnabledHost(
                current = regularDesktopHosts[tab.profileId].orEmpty(), host = host, enabled = enabled,
            ))
        }
    }

    fun persistentEntries(): List<BrowserDesktopPreference> = regularDesktopHosts.flatMap { (profileId, hosts) ->
        hosts.map { BrowserDesktopPreference(profileId = profileId, host = it, enabled = true) }
    }

    fun clearTabOverrides(tabId: String) {
        privateDesktopHosts = privateDesktopHosts - tabId
    }

    fun removeProfile(profileId: String, tabIds: List<String>) {
        regularDesktopHosts = regularDesktopHosts - profileId
        val removedIds = tabIds.toHashSet()
        privateDesktopHosts = privateDesktopHosts.filterKeys { it !in removedIds }
    }

    fun retainTabs(tabs: List<BrowserTabState>) {
        val privateIds = tabs.filter(BrowserTabState::isPrivate).mapTo(hashSetOf(), BrowserTabState::id)
        privateDesktopHosts = privateDesktopHosts.filterKeys { it in privateIds }
    }

    private companion object {
        const val MAX_PROFILES = dev.sk2andy.materialbrowser.browser.MAX_PROFILES
    }
}
