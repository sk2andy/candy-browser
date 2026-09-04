package dev.sk2andy.materialbrowser.browser

object PopupSiteRules {
    const val MAX_PER_PROFILE = SiteDomainRules.MAX_PER_PROFILE

    fun domainForUrl(url: String?): String? = SiteDomainRules.domainForUrl(url)

    fun normalizedDomain(host: String?): String? = SiteDomainRules.normalizedDomain(host)

    fun shouldAlwaysBlock(openerUrl: String?, domains: Collection<String>): Boolean =
        SiteDomainRules.contains(openerUrl, domains)

    fun withAlwaysBlockState(
        current: Collection<String>,
        domain: String,
        enabled: Boolean,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> = SiteDomainRules.withState(current, domain, enabled, limit)
}
