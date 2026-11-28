package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer

internal class WebContentSafeAreaSession {
    private val protectedHosts = mutableSetOf<Host>()

    fun remember(tab: BrowserTab, url: String?) {
        key(tab, url)?.let(protectedHosts::add)
    }

    fun contains(tab: BrowserTab, url: String?): Boolean =
        key(tab, url)?.let(protectedHosts::contains) == true

    fun clearPrivate() {
        protectedHosts.removeAll { host -> host.isPrivate }
    }

    fun clear() = protectedHosts.clear()

    private fun key(tab: BrowserTab, url: String?): Host? =
        url?.let(PrivacyRequestSanitizer::webHost)?.let { host ->
            Host(profileId = tab.profileId, isPrivate = tab.isIncognito, name = host)
        }

    private data class Host(val profileId: String, val isPrivate: Boolean, val name: String)
}
