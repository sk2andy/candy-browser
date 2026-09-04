package dev.sk2andy.materialbrowser.blocking

import java.net.IDN
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

enum class PrivacyRequestCategory {
    Advertising,
    Analytics,
    Social,
    Other,
}

enum class PrivacyPartyRelation {
    FirstParty,
    ThirdParty,
    Unknown,
}

data class SanitizedPrivacyRequest(
    val requestHost: String,
    val pageHost: String?,
)

data class PrivacyDomainSummary(
    val host: String,
    val blockedCount: Int,
    val allowedCount: Int = 0,
    val category: PrivacyRequestCategory,
    val partyRelation: PrivacyPartyRelation,
    val ruleDecision: PrivacyRuleDecisionSummary? = null,
)

enum class PrivacyRuleDecisionAction { Block, Allow }

data class PrivacyRuleDecisionSummary(
    val ruleId: String?,
    val label: String,
    val action: PrivacyRuleDecisionAction,
)

data class PrivacyXRaySnapshot(
    val totalBlocked: Int = 0,
    val categoryCounts: Map<PrivacyRequestCategory, Int> = emptyMap(),
    val partyCounts: Map<PrivacyPartyRelation, Int> = emptyMap(),
    val domains: List<PrivacyDomainSummary> = emptyList(),
    val omittedDomainRequests: Int = 0,
) {
    companion object {
        val Empty = PrivacyXRaySnapshot()
    }
}

data class SiteProtectionState(
    val host: String? = null,
    val isPaused: Boolean = false,
    val isPersistent: Boolean = false,
    val canPersist: Boolean = false,
    val cookieBannerRemovalDisabled: Boolean = false,
    val forceVerticalScrolling: Boolean = false,
    val forcePageZooming: Boolean = false,
    val forceSafeArea: Boolean = false,
    val thirdPartyLoginAllowed: Boolean = false,
    val captchaCompatibilityAllowed: Boolean = false,
)

data class SitePrivacyOverrides(
    val cookieBannerRemovalDisabled: Boolean? = null,
    val forceVerticalScrolling: Boolean? = null,
    val forcePageZooming: Boolean? = null,
    val forceSafeArea: Boolean? = null,
    val thirdPartyLoginAllowed: Boolean? = null,
    val captchaCompatibilityAllowed: Boolean? = null,
) {
    val isDefault: Boolean
        get() = cookieBannerRemovalDisabled == null &&
            forceVerticalScrolling == null &&
            forcePageZooming == null &&
            forceSafeArea == null &&
            thirdPartyLoginAllowed == null &&
            captchaCompatibilityAllowed == null
}

object SitePrivacyOverrideRules {
    fun overrideForSelection(enabled: Boolean, bundledDefault: Boolean): Boolean? =
        enabled.takeIf { it != bundledDefault }

    fun forceVerticalScrolling(
        overrides: SitePrivacyOverrides?,
        bundledDefault: Boolean,
    ): Boolean = overrides?.forceVerticalScrolling ?: bundledDefault

    fun forcePageZooming(overrides: SitePrivacyOverrides?): Boolean =
        overrides?.forcePageZooming ?: false

    fun forceSafeArea(overrides: SitePrivacyOverrides?): Boolean =
        overrides?.forceSafeArea ?: false

    fun thirdPartyLoginAllowed(overrides: SitePrivacyOverrides?): Boolean =
        overrides?.thirdPartyLoginAllowed ?: false

    fun captchaCompatibilityAllowed(overrides: SitePrivacyOverrides?): Boolean =
        overrides?.captchaCompatibilityAllowed ?: false

    fun cookieBannerRemovalDisabled(
        overrides: SitePrivacyOverrides?,
        bundledDefault: Boolean,
    ): Boolean = overrides?.cookieBannerRemovalDisabled ?: bundledDefault

    fun withOverride(
        current: Map<String, SitePrivacyOverrides>,
        host: String,
        overrides: SitePrivacyOverrides,
        limit: Int = SiteExceptionRules.MAX_PER_PROFILE,
    ): Map<String, SitePrivacyOverrides> {
        val normalizedHost = PrivacyRequestSanitizer.normalizeHost(host) ?: return current
        if (limit <= 0) return emptyMap()
        return buildMap {
            current.asSequence()
                .mapNotNull { (candidate, value) ->
                    PrivacyRequestSanitizer.normalizeHost(candidate)?.let { it to value }
                }
                .filter { (candidate, value) -> candidate != normalizedHost && !value.isDefault }
                .take(if (overrides.isDefault) limit else limit - 1)
                .forEach { (candidate, value) -> put(candidate, value) }
            if (!overrides.isDefault) put(normalizedHost, overrides)
        }
    }
}

object PrivacyRequestSanitizer {
    fun sanitize(requestUrl: String, pageUrl: String?): SanitizedPrivacyRequest? {
        val requestHost = webHost(requestUrl) ?: return null
        return SanitizedPrivacyRequest(
            requestHost = requestHost,
            pageHost = pageUrl?.let(::webHost),
        )
    }

    fun webHost(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in WEB_SCHEMES) return null
        val host = uri.host ?: runCatching { uri.toURL().host }.getOrNull()
        return normalizeHost(host)
    }

    fun normalizeHost(host: String?): String? {
        val candidate = host?.trim()?.trim('.')?.lowercase()?.takeIf(String::isNotEmpty)
            ?: return null
        return runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrNull()
            ?.takeIf { ascii ->
                ascii.length <= MAX_HOST_LENGTH &&
                    ascii.split('.').all { label ->
                        label.isNotEmpty() && label.length <= MAX_LABEL_LENGTH &&
                            label.first() != '-' && label.last() != '-'
                    }
            }
    }

    private const val MAX_HOST_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private val WEB_SCHEMES = setOf("http", "https")
}

object PrivacyRequestClassifier {
    private val advertisingHosts = setOf(
        "2mdn.net",
        "adform.net",
        "adnxs.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "doubleclick.net",
        "googlesyndication.com",
        "outbrain.com",
        "taboola.com",
    )
    private val analyticsHosts = setOf(
        "amplitude.com",
        "clarity.ms",
        "google-analytics.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
    )
    private val socialHosts = setOf(
        "facebook.com",
        "facebook.net",
        "fbcdn.net",
        "instagram.com",
        "linkedin.com",
        "pinterest.com",
        "tiktok.com",
        "twitter.com",
        "x.com",
    )
    fun classify(host: String): PrivacyRequestCategory {
        return when {
            host.matchesKnownHost(advertisingHosts) -> PrivacyRequestCategory.Advertising
            host.matchesKnownHost(analyticsHosts) -> PrivacyRequestCategory.Analytics
            host.matchesKnownHost(socialHosts) -> PrivacyRequestCategory.Social
            else -> PrivacyRequestCategory.Other
        }
    }

    private fun String.matchesKnownHost(knownHosts: Set<String>): Boolean =
        knownHosts.any { known -> this == known || endsWith(".$known") }
}

object PrivacyPartyClassifier {
    fun classify(requestHost: String, pageHost: String?): PrivacyPartyRelation = when {
        pageHost == null -> PrivacyPartyRelation.Unknown
        SiteExceptionRules.hostMatches(requestHost, pageHost) ||
            SiteExceptionRules.hostMatches(pageHost, requestHost) -> PrivacyPartyRelation.FirstParty
        CandyPublicSuffixRules.registrableDomain(requestHost) == null ||
            CandyPublicSuffixRules.registrableDomain(pageHost) == null -> PrivacyPartyRelation.Unknown
        CandyPublicSuffixRules.registrableDomain(requestHost) ==
            CandyPublicSuffixRules.registrableDomain(pageHost) -> PrivacyPartyRelation.FirstParty
        else -> PrivacyPartyRelation.ThirdParty
    }
}

object PrivacyRetention {
    const val MAX_DOMAINS_PER_TAB = 24

    fun mayRetainDomain(
        retainedHosts: Collection<String>,
        candidateHost: String,
        limit: Int = MAX_DOMAINS_PER_TAB,
    ): Boolean = candidateHost in retainedHosts || retainedHosts.size < limit.coerceAtLeast(0)
}

object PrivacyAggregation {
    fun aggregateBatch(
        current: PrivacyXRaySnapshot,
        requests: Iterable<SanitizedPrivacyRequest>,
        domainLimit: Int = PrivacyRetention.MAX_DOMAINS_PER_TAB,
    ): PrivacyXRaySnapshot {
        var totalBlocked = current.totalBlocked
        var omittedDomainRequests = current.omittedDomainRequests
        val categoryCounts = current.categoryCounts.toMutableMap()
        val partyCounts = current.partyCounts.toMutableMap()
        val domains = current.domains.associateByTo(linkedMapOf(), PrivacyDomainSummary::host)

        requests.forEach { request ->
            val category = PrivacyRequestClassifier.classify(request.requestHost)
            val party = PrivacyPartyClassifier.classify(request.requestHost, request.pageHost)
            totalBlocked = totalBlocked.saturatedIncrement()
            categoryCounts[category] = categoryCounts.getOrDefault(category, 0).saturatedIncrement()
            partyCounts[party] = partyCounts.getOrDefault(party, 0).saturatedIncrement()
            val existing = domains[request.requestHost]
            if (existing != null) {
                domains[request.requestHost] = existing.copy(
                    blockedCount = existing.blockedCount.saturatedIncrement(),
                    partyRelation = when {
                        existing.partyRelation == party -> party
                        else -> PrivacyPartyRelation.Unknown
                    },
                )
            } else if (PrivacyRetention.mayRetainDomain(
                    retainedHosts = domains.keys,
                    candidateHost = request.requestHost,
                    limit = domainLimit,
                )
            ) {
                domains[request.requestHost] = PrivacyDomainSummary(
                    host = request.requestHost,
                    blockedCount = 1,
                    category = category,
                    partyRelation = party,
                )
            } else {
                omittedDomainRequests = omittedDomainRequests.saturatedIncrement()
            }
        }

        return snapshot(
            totalBlocked = totalBlocked,
            categoryCounts = categoryCounts,
            partyCounts = partyCounts,
            domains = domains.values,
            omittedDomainRequests = omittedDomainRequests,
        )
    }

    fun snapshot(
        totalBlocked: Int,
        categoryCounts: Map<PrivacyRequestCategory, Int>,
        partyCounts: Map<PrivacyPartyRelation, Int>,
        domains: Collection<PrivacyDomainSummary>,
        omittedDomainRequests: Int,
    ): PrivacyXRaySnapshot = PrivacyXRaySnapshot(
        totalBlocked = totalBlocked,
        categoryCounts = categoryCounts.filterValues { it > 0 }.toMap(),
        partyCounts = partyCounts.filterValues { it > 0 }.toMap(),
        domains = domains.sortedWith(
                compareByDescending<PrivacyDomainSummary> { it.blockedCount + it.allowedCount }
                    .thenBy(PrivacyDomainSummary::host),
            ),
        omittedDomainRequests = omittedDomainRequests,
    )

    fun stablePartyRelation(counts: Map<PrivacyPartyRelation, Int>): PrivacyPartyRelation {
        val observed = counts.filterValues { it > 0 }.keys
        return observed.singleOrNull() ?: PrivacyPartyRelation.Unknown
    }

    private fun Int.saturatedIncrement(): Int = if (this == Int.MAX_VALUE) this else this + 1
}

class PrivacyXRayRepository(
    private val domainLimit: Int = PrivacyRetention.MAX_DOMAINS_PER_TAB,
) {
    private val accumulators = ConcurrentHashMap<String, TabAccumulator>()

    fun record(tabId: String, requestUrl: String, pageUrl: String?): Boolean {
        val request = PrivacyRequestSanitizer.sanitize(requestUrl, pageUrl) ?: return false
        accumulators.computeIfAbsent(tabId) { TabAccumulator(domainLimit) }.record(
            request = request,
            wasBlocked = true,
            decision = null,
        )
        return true
    }

    fun recordDecision(
        tabId: String,
        requestUrl: String,
        pageUrl: String?,
        wasBlocked: Boolean,
        decision: PrivacyRuleDecisionSummary,
    ): Boolean {
        val request = PrivacyRequestSanitizer.sanitize(requestUrl, pageUrl) ?: return false
        accumulators.computeIfAbsent(tabId) { TabAccumulator(domainLimit) }.record(
            request = request,
            wasBlocked = wasBlocked,
            decision = decision,
        )
        return true
    }

    fun snapshot(tabId: String): PrivacyXRaySnapshot =
        accumulators[tabId]?.snapshot() ?: PrivacyXRaySnapshot.Empty

    fun remove(tabId: String) {
        accumulators.remove(tabId)
    }

    fun clear() {
        accumulators.clear()
    }

    private class TabAccumulator(private val domainLimit: Int) {
        private val categoryCounts = IntArray(PrivacyRequestCategory.entries.size)
        private val partyCounts = IntArray(PrivacyPartyRelation.entries.size)
        private val domains = linkedMapOf<String, DomainAccumulator>()
        private var totalBlocked = 0
        private var omittedDomainRequests = 0

        @Synchronized
        fun record(
            request: SanitizedPrivacyRequest,
            wasBlocked: Boolean,
            decision: PrivacyRuleDecisionSummary?,
        ) {
            val category = PrivacyRequestClassifier.classify(request.requestHost)
            val party = PrivacyPartyClassifier.classify(request.requestHost, request.pageHost)
            if (wasBlocked) {
                totalBlocked = totalBlocked.saturatedIncrement()
                categoryCounts[category.ordinal] = categoryCounts[category.ordinal].saturatedIncrement()
                partyCounts[party.ordinal] = partyCounts[party.ordinal].saturatedIncrement()
            }
            val existing = domains[request.requestHost]
            if (existing != null) {
                existing.record(party, wasBlocked, decision)
            } else if (PrivacyRetention.mayRetainDomain(
                    retainedHosts = domains.keys,
                    candidateHost = request.requestHost,
                    limit = domainLimit,
                )
            ) {
                domains[request.requestHost] = DomainAccumulator(category).also {
                    it.record(party, wasBlocked, decision)
                }
            } else {
                omittedDomainRequests = omittedDomainRequests.saturatedIncrement()
            }
        }

        @Synchronized
        fun snapshot(): PrivacyXRaySnapshot = PrivacyAggregation.snapshot(
            totalBlocked = totalBlocked,
            categoryCounts = PrivacyRequestCategory.entries.associateWith { category ->
                categoryCounts[category.ordinal]
            },
            partyCounts = PrivacyPartyRelation.entries.associateWith { party ->
                partyCounts[party.ordinal]
            },
            domains = domains.map { (host, aggregate) -> aggregate.summary(host) },
            omittedDomainRequests = omittedDomainRequests,
        )

        private class DomainAccumulator(private val category: PrivacyRequestCategory) {
            private val partyCounts = IntArray(PrivacyPartyRelation.entries.size)
            private var blockedCount = 0
            private var allowedCount = 0
            private var ruleDecision: PrivacyRuleDecisionSummary? = null

            fun record(
                party: PrivacyPartyRelation,
                wasBlocked: Boolean,
                decision: PrivacyRuleDecisionSummary?,
            ) {
                if (wasBlocked && blockedCount < Int.MAX_VALUE) blockedCount++
                if (!wasBlocked && allowedCount < Int.MAX_VALUE) allowedCount++
                if (partyCounts[party.ordinal] < Int.MAX_VALUE) partyCounts[party.ordinal]++
                if (decision != null) ruleDecision = decision
            }

            fun summary(host: String): PrivacyDomainSummary {
                val parties = PrivacyPartyRelation.entries.associateWith { party ->
                    partyCounts[party.ordinal]
                }
                return PrivacyDomainSummary(
                    host = host,
                    blockedCount = blockedCount,
                    allowedCount = allowedCount,
                    category = category,
                    partyRelation = PrivacyAggregation.stablePartyRelation(parties),
                    ruleDecision = ruleDecision,
                )
            }
        }

        private fun Int.saturatedIncrement(): Int = if (this == Int.MAX_VALUE) this else this + 1
    }
}

object SiteExceptionRules {
    const val MAX_PER_PROFILE = 64

    fun normalizedException(host: String?): String? = PrivacyRequestSanitizer.normalizeHost(host)

    fun mayPersist(isIncognito: Boolean): Boolean = !isIncognito

    fun hostMatches(pageHost: String, exceptionHost: String): Boolean =
        pageHost == exceptionHost || pageHost.endsWith(".$exceptionHost")

    fun isPaused(pageHost: String?, exceptions: Collection<String>): Boolean {
        val normalizedPageHost = PrivacyRequestSanitizer.normalizeHost(pageHost) ?: return false
        return exceptions.any { exception ->
            normalizedException(exception)?.let { hostMatches(normalizedPageHost, it) } == true
        }
    }

    fun withException(
        current: Collection<String>,
        host: String,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> {
        val normalizedHost = normalizedException(host) ?: return current.toSet()
        if (limit <= 0) return emptySet()
        return (current.asSequence()
            .mapNotNull(::normalizedException)
            .filterNot { it == normalizedHost }
            .take(limit - 1) + sequenceOf(normalizedHost))
            .toCollection(linkedSetOf())
    }
}

object RequestProtectionRules {
    fun shouldBlock(
        isForMainFrame: Boolean,
        blockerEnabled: Boolean,
        sitePaused: Boolean,
        isListedRequest: Boolean,
    ): Boolean = !isForMainFrame && blockerEnabled && !sitePaused && isListedRequest
}

object PrivacyPolicyRules {
    fun acceptsThirdPartyCookies(
        blockThirdPartyCookies: Boolean,
        sitePaused: Boolean,
        thirdPartyLoginAllowed: Boolean = false,
        captchaCompatibilityAllowed: Boolean = false,
    ): Boolean = !blockThirdPartyCookies || sitePaused || thirdPartyLoginAllowed ||
        captchaCompatibilityAllowed
}
