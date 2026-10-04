package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.browser.permissions.PermissionOrigin
import java.net.URI

/** Immutable confirmation target; engine deletion must never retarget a later selection. */
data class SiteDataTarget(
    val tabId: String,
    val url: String,
    val origin: String,
    val host: String,
    val profileId: String,
    val isPrivate: Boolean,
    val navigationGeneration: Int?,
    val sharesStorage: Boolean = false,
)

internal object SiteDataRules {
    fun target(
        tab: BrowserTab,
        pageUrl: String,
        navigationGeneration: Int?,
        sharesStorage: Boolean,
    ): SiteDataTarget? {
        val url = BrowserUriPolicy.normalizeHttpUrl(pageUrl) ?: return null
        val origin = PermissionOrigin.normalize(url) ?: return null
        val host = URI(origin).host?.removePrefix("[")?.removeSuffix("]") ?: return null
        return SiteDataTarget(
            tabId = tab.id,
            url = url,
            origin = origin,
            host = host,
            profileId = tab.profileId,
            isPrivate = tab.isIncognito,
            navigationGeneration = navigationGeneration,
            sharesStorage = sharesStorage,
        )
    }

    fun isCurrent(target: SiteDataTarget, current: SiteDataTarget?): Boolean = target == current
}
