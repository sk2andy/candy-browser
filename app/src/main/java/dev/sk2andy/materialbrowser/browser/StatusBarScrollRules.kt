package dev.sk2andy.materialbrowser.browser

import kotlin.math.roundToInt

object StatusBarScrollRules {
    const val DEFAULT_TARGET_WIDTH_PERCENT = 20
    const val MIN_TARGET_WIDTH_PERCENT = 10
    const val MAX_TARGET_WIDTH_PERCENT = 50
    const val MAX_DISABLED_DOMAINS_PER_PROFILE = SiteDomainRules.MAX_PER_PROFILE

    fun targetWidthPercent(value: Int): Int =
        value.coerceIn(MIN_TARGET_WIDTH_PERCENT, MAX_TARGET_WIDTH_PERCENT)

    fun targetWidthPx(availableWidthPx: Int, widthPercent: Int): Int {
        val screenWidth = availableWidthPx.coerceAtLeast(1)
        return (screenWidth * targetWidthPercent(widthPercent) / 100f)
            .roundToInt()
            .coerceIn(1, screenWidth)
    }

    fun shouldShowOverlay(
        enabled: Boolean,
        foreground: Boolean,
        pageAllowed: Boolean,
        previewActive: Boolean,
    ): Boolean = enabled && (previewActive || (foreground && pageAllowed))

    fun normalizedDomain(host: String?): String? = SiteDomainRules.normalizedDomain(host)

    fun isEnabled(url: String?, disabledDomains: Collection<String>): Boolean {
        if (SiteDomainRules.domainForUrl(url) == null) return false
        return !SiteDomainRules.contains(url, disabledDomains)
    }

    fun withEnabledState(
        disabledDomains: Collection<String>,
        domain: String,
        enabled: Boolean,
        limit: Int = MAX_DISABLED_DOMAINS_PER_PROFILE,
    ): Set<String> = SiteDomainRules.withState(
        current = disabledDomains,
        domain = domain,
        enabled = !enabled,
        limit = limit,
    )
}
