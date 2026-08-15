package dev.sk2andy.materialbrowser.browser

object DomainMuteRules {
    const val MAX_PER_PROFILE = SiteDomainRules.MAX_PER_PROFILE

    fun domainForUrl(url: String?): String? = SiteDomainRules.domainForUrl(url)

    fun normalizedDomain(host: String?): String? = SiteDomainRules.normalizedDomain(host)

    fun isMuted(url: String?, mutedDomains: Collection<String>): Boolean {
        return SiteDomainRules.contains(url, mutedDomains)
    }

    fun withMutedState(
        current: Collection<String>,
        domain: String,
        muted: Boolean,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> {
        return SiteDomainRules.withState(current, domain, muted, limit)
    }
}
