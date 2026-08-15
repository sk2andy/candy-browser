package dev.sk2andy.materialbrowser.browser

import com.google.common.net.InternetDomainName
import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer

object SiteDomainRules {
    const val MAX_PER_PROFILE = 64

    fun domainForUrl(url: String?): String? {
        val safeUrl = url ?: return null
        return PrivacyRequestSanitizer.webHost(safeUrl)?.let(::normalizedDomain)
    }

    fun normalizedDomain(host: String?): String? {
        val normalizedHost = CandyHostCanonicalizer.canonicalHost(host) ?: return null
        val domain = runCatching { InternetDomainName.from(normalizedHost) }.getOrNull()
            ?: return null
        return if (domain.isUnderPublicSuffix) {
            domain.topPrivateDomain().toString()
        } else {
            normalizedHost
        }
    }

    fun contains(url: String?, domains: Collection<String>): Boolean {
        val domain = domainForUrl(url) ?: return false
        return domains.any { normalizedDomain(it) == domain }
    }

    fun withState(
        current: Collection<String>,
        domain: String,
        enabled: Boolean,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> {
        val normalizedDomain = normalizedDomain(domain) ?: return current.toSet()
        val retained = current.asSequence()
            .mapNotNull(::normalizedDomain)
            .filterNot { it == normalizedDomain }
            .distinct()
        if (!enabled) return retained.toCollection(linkedSetOf())
        if (limit <= 0) return emptySet()
        return (retained.take(limit - 1) + sequenceOf(normalizedDomain))
            .toCollection(linkedSetOf())
    }
}
