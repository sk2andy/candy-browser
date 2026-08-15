package dev.sk2andy.materialbrowser.browser

object DesktopSiteRules {
    const val MAX_PER_PROFILE = SiteDomainRules.MAX_PER_PROFILE

    private val androidPlatformPattern = Regex(
        pattern = """\([^)]*\bAndroid\b[^)]*\)""",
        option = RegexOption.IGNORE_CASE,
    )
    private val versionTokenPattern = Regex("""\s+Version/\S+""")
    private val mobileTokenPattern = Regex("""\s+Mobile(?=\s|$)""")
    private val repeatedWhitespacePattern = Regex("""\s{2,}""")

    fun domainForUrl(url: String?): String? = SiteDomainRules.domainForUrl(url)

    fun normalizedDomain(host: String?): String? = SiteDomainRules.normalizedDomain(host)

    fun isDesktopView(url: String?, desktopDomains: Collection<String>): Boolean =
        SiteDomainRules.contains(url, desktopDomains)

    fun withDesktopViewState(
        current: Collection<String>,
        domain: String,
        enabled: Boolean,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> = SiteDomainRules.withState(current, domain, enabled, limit)

    fun desktopUserAgent(defaultUserAgent: String): String = defaultUserAgent
        .replaceFirst(androidPlatformPattern, "(X11; Linux x86_64)")
        .replace(versionTokenPattern, "")
        .replace(mobileTokenPattern, "")
        .replace(repeatedWhitespacePattern, " ")
        .trim()
}
