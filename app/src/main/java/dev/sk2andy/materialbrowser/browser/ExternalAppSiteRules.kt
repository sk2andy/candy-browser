package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

/** Global website exceptions cover every path and subdomain of one registrable site. */
object ExternalAppSiteRules {
    const val MAX_SITES = SiteDomainRules.MAX_PER_PROFILE

    fun siteForUrl(url: String?): String? =
        BrowserUriPolicy.normalizeHttpUrl(url)?.let(SiteDomainRules::domainForUrl)

    fun contains(url: String?, domains: Collection<String>): Boolean {
        val site = siteForUrl(url) ?: return false
        return site in normalizedSites(domains)
    }

    fun normalizedSites(domains: Collection<String>): List<String> = domains.asSequence()
        .mapNotNull(SiteDomainRules::normalizedDomain)
        .distinct()
        .sorted()
        .take(MAX_SITES)
        .toList()

    fun withState(
        current: Collection<String>,
        domain: String,
        enabled: Boolean,
    ): List<String> = normalizedSites(
        SiteDomainRules.withState(
            current = normalizedSites(current),
            domain = domain,
            enabled = enabled,
            limit = MAX_SITES,
        ),
    )
}
