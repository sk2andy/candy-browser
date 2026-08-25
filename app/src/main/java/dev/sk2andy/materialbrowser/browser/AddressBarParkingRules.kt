package dev.sk2andy.materialbrowser.browser

object AddressBarParkingRules {
    const val MAX_PER_PROFILE = SiteDomainRules.MAX_PER_PROFILE

    fun domainForUrl(url: String?): String? = SiteDomainRules.domainForUrl(url)

    fun normalizedDomain(host: String?): String? = SiteDomainRules.normalizedDomain(host)

    fun isAlwaysParked(url: String?, domains: Collection<String>): Boolean =
        SiteDomainRules.contains(url, domains)

    fun withAlwaysParkedState(
        current: Collection<String>,
        domain: String,
        enabled: Boolean,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> = SiteDomainRules.withState(current, domain, enabled, limit)

    fun shouldParkAfterLoad(
        alwaysParkAfterLoad: Boolean,
        url: String?,
        alwaysParkedDomains: Collection<String>,
    ): Boolean = url != null &&
        url != BLANK_URL &&
        (alwaysParkAfterLoad || isAlwaysParked(url, alwaysParkedDomains))

    fun isCurrentPageCompletion(
        controllerDestroyed: Boolean,
        isCurrentWebView: Boolean,
        finishedNavigationGeneration: Int,
        currentNavigationGeneration: Int?,
        callbackUrl: String,
        currentWebViewUrl: String?,
        currentProgress: Int,
        currentPageUrl: String?,
    ): Boolean = !controllerDestroyed &&
        isCurrentWebView &&
        currentNavigationGeneration == finishedNavigationGeneration &&
        currentWebViewUrl == callbackUrl &&
        currentProgress == 100 &&
        currentPageUrl == callbackUrl
}
