package dev.sk2andy.materialbrowser.capsule

import com.google.common.net.InternetDomainName
import com.google.common.net.InetAddresses
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.net.IDN
import java.net.URI

enum class CapsuleNavigationDecision {
    StayInCapsule,
    OpenInFullCandy,
    UseExistingUriPolicy,
}

object CapsuleNavigationRules {
    fun decide(
        capsule: SiteCapsule,
        targetUrl: String,
    ): CapsuleNavigationDecision {
        val target = parseHttpUrl(targetUrl) ?: return if (
            BrowserUriPolicy.normalizeHttpUrl(targetUrl) != null
        ) {
            CapsuleNavigationDecision.OpenInFullCandy
        } else {
            CapsuleNavigationDecision.UseExistingUriPolicy
        }
        if (capsule.navigationMode == CapsuleNavigationMode.AllLinks) {
            return CapsuleNavigationDecision.StayInCapsule
        }
        val start = parseHttpUrl(capsule.startUrl)
            ?: return CapsuleNavigationDecision.OpenInFullCandy
        return when (capsule.navigationMode) {
            CapsuleNavigationMode.SameOrigin -> if (sameOrigin(start, target)) {
                CapsuleNavigationDecision.StayInCapsule
            } else {
                CapsuleNavigationDecision.OpenInFullCandy
            }
            CapsuleNavigationMode.SameRegistrableDomain -> {
                if (siteKey(start.host) == siteKey(target.host)) {
                    CapsuleNavigationDecision.StayInCapsule
                } else {
                    CapsuleNavigationDecision.OpenInFullCandy
                }
            }
            CapsuleNavigationMode.AllLinks -> CapsuleNavigationDecision.StayInCapsule
        }
    }

    private fun parseHttpUrl(value: String): ParsedHttpUrl? {
        val normalized = BrowserUriPolicy.normalizeHttpUrl(value) ?: return null
        return runCatching {
            val uri = URI(normalized)
            if (uri.rawAuthority.isNullOrBlank() || '@' in uri.rawAuthority) return null
            val url = uri.toURL()
            val host = normalizedHost(url.host) ?: return null
            ParsedHttpUrl(
                scheme = url.protocol.lowercase(),
                host = host,
                port = when {
                    url.port >= 0 -> url.port
                    url.protocol.equals("https", ignoreCase = true) -> 443
                    else -> 80
                },
            )
        }.getOrNull()
    }

    private fun sameOrigin(first: ParsedHttpUrl, second: ParsedHttpUrl): Boolean =
        first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host == second.host &&
            first.port == second.port

    internal fun siteKey(host: String?): String? {
        val normalized = normalizedHost(host) ?: return null
        if (InetAddresses.isInetAddress(normalized)) return normalized
        return runCatching {
            val domain = InternetDomainName.from(normalized)
            if (domain.isUnderPublicSuffix) domain.topPrivateDomain().toString() else normalized
        }.getOrDefault(normalized)
    }

    private fun normalizedHost(host: String?): String? {
        val candidate = host
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.trimEnd('.')
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (InetAddresses.isInetAddress(candidate)) {
            return InetAddresses.toAddrString(InetAddresses.forString(candidate))
        }
        return runCatching {
            IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES)
                .lowercase()
                .takeIf(InternetDomainName::isValid)
        }.getOrNull()
    }

    private data class ParsedHttpUrl(
        val scheme: String,
        val host: String,
        val port: Int,
    )
}
