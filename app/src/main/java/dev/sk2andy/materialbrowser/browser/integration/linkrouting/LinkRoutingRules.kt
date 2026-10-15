package dev.sk2andy.materialbrowser.browser.integration.linkrouting

import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.capsule.CapsuleNavigationDecision
import dev.sk2andy.materialbrowser.capsule.CapsuleNavigationRules
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import java.net.URI

internal enum class LinkRoutingTargetKind(val wireValue: String) {
    Profile("profile"),
    Reader("reader"),
    Capsule("capsule"),
}

internal data class LinkRoutingTarget(
    val kind: LinkRoutingTargetKind,
    val id: String? = null,
)

internal data class LinkRoutingRule(
    val id: String,
    val domain: String,
    val includeSubdomains: Boolean = false,
    val target: LinkRoutingTarget,
    val enabled: Boolean = true,
)

internal sealed interface LinkRoutingResolution {
    data object NoMatch : LinkRoutingResolution
    data object Unavailable : LinkRoutingResolution
    data class Open(val url: String, val profileId: String, val target: LinkRoutingTarget) : LinkRoutingResolution
}

internal data class LinkRoutingReaderRequest(val tabId: String, val sourceUrl: String)

internal object LinkRoutingRules {
    const val MAX_RULES = 64
    const val MAX_URL_LENGTH = 8_192

    fun sanitize(rule: LinkRoutingRule): LinkRoutingRule? {
        if (!validIdentifier(rule.id)) return null
        val domain = CandyHostCanonicalizer.canonicalHost(rule.domain) ?: return null
        val target = rule.target
        if (target.kind == LinkRoutingTargetKind.Reader) {
            if (target.id != null) return null
        } else if (!validIdentifier(target.id)) {
            return null
        }
        return rule.copy(domain = domain)
    }

    fun bounded(rules: List<LinkRoutingRule>): List<LinkRoutingRule> = rules.asSequence()
        .take(MAX_RULES * 2)
        .mapNotNull(::sanitize)
        .distinctBy(LinkRoutingRule::id)
        .take(MAX_RULES)
        .toList()

    fun eligibleProfiles(profiles: List<BrowserProfile>): List<BrowserProfile> = profiles.filter {
        !it.isSynced && it.protection == null
    }

    fun shouldCancelReaderRequest(request: LinkRoutingReaderRequest, tab: BrowserTab): Boolean =
        tab.id != request.tabId || tab.isIncognito || tab.error != null || tab.url != request.sourceUrl

    fun canOpenReader(request: LinkRoutingReaderRequest, tab: BrowserTab): Boolean =
        !shouldCancelReaderRequest(request, tab) && !tab.isLoading &&
            BrowserUriPolicy.normalizeHttpUrl(tab.url) != null

    fun resolve(
        url: String,
        rules: List<LinkRoutingRule>,
        profiles: List<BrowserProfile>,
        capsules: List<SiteCapsule>,
        activeProfileId: String,
        profilesEnabled: Boolean,
        isPrivate: Boolean,
    ): LinkRoutingResolution {
        if (isPrivate || url.length > MAX_URL_LENGTH) return LinkRoutingResolution.NoMatch
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return LinkRoutingResolution.NoMatch
        val host = runCatching { URI(safeUrl).toURL().host }.getOrNull()
            ?.let(CandyHostCanonicalizer::canonicalHost) ?: return LinkRoutingResolution.NoMatch
        val rule = bounded(rules).firstOrNull {
            it.enabled && (host == it.domain || it.includeSubdomains && host.endsWith(".${it.domain}"))
        } ?: return LinkRoutingResolution.NoMatch
        val target = rule.target
        val profileId = when (target.kind) {
            LinkRoutingTargetKind.Profile -> target.id
            LinkRoutingTargetKind.Reader -> activeProfileId
            LinkRoutingTargetKind.Capsule -> {
                val capsule = capsules.firstOrNull { it.id == target.id }
                    ?: return LinkRoutingResolution.Unavailable
                if (CapsuleNavigationRules.decide(capsule, safeUrl) != CapsuleNavigationDecision.StayInCapsule) {
                    return LinkRoutingResolution.Unavailable
                }
                capsule.profileId
            }
        } ?: return LinkRoutingResolution.Unavailable
        if (eligibleProfiles(profiles).none { it.id == profileId } ||
            !profilesEnabled && activeProfileId != profileId
        ) return LinkRoutingResolution.Unavailable
        return LinkRoutingResolution.Open(safeUrl, profileId, target)
    }

    private fun validIdentifier(value: String?): Boolean = value != null &&
        value.isNotBlank() && value.length <= 128 && value.none { it.isWhitespace() || it.isISOControl() }
}
