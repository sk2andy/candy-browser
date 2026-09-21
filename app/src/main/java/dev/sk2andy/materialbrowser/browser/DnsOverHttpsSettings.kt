package dev.sk2andy.materialbrowser.browser

import java.net.IDN
import java.net.URI

data class DnsOverHttpsSettings(
    val provider: DnsOverHttpsProvider = DnsOverHttpsProvider.System,
    val customEndpoint: String = "",
)

enum class DnsOverHttpsProvider(
    val stableId: String,
    internal val endpoint: String?,
) {
    System("system", null),
    Cloudflare("cloudflare", "https://cloudflare-dns.com/dns-query"),
    Google("google", "https://dns.google/dns-query"),
    Quad9("quad9", "https://dns.quad9.net/dns-query"),
    Custom("custom", null),
    ;

    companion object {
        fun fromStableId(value: String?): DnsOverHttpsProvider =
            entries.firstOrNull { provider -> provider.stableId == value } ?: System
    }
}

object DnsOverHttpsRules {
    const val MAX_ENDPOINT_LENGTH = 2_048

    val Default = DnsOverHttpsSettings()

    fun sanitize(settings: DnsOverHttpsSettings): DnsOverHttpsSettings = when (settings.provider) {
        DnsOverHttpsProvider.Custom -> normalizedCustomEndpoint(settings.customEndpoint)
            ?.let { endpoint -> settings.copy(customEndpoint = endpoint) }
            ?: Default
        else -> settings.copy(
            customEndpoint = normalizedCustomEndpoint(settings.customEndpoint).orEmpty(),
        )
    }

    fun endpoint(settings: DnsOverHttpsSettings): String? {
        val sanitized = sanitize(settings)
        return when (sanitized.provider) {
            DnsOverHttpsProvider.System -> null
            DnsOverHttpsProvider.Custom -> sanitized.customEndpoint
            else -> sanitized.provider.endpoint
        }
    }

    fun normalizedCustomEndpoint(value: String): String? {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.length > MAX_ENDPOINT_LENGTH) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.normalize().rawPath != uri.rawPath) return null
        if (hasMalformedExplicitPort(uri.rawAuthority.orEmpty())) return null
        val url = runCatching { uri.toURL() }.getOrNull() ?: return null
        if (url.port != -1 && url.port !in 1..65_535) return null
        val host = url.host
            ?.takeIf(String::isNotBlank)
            ?: return null
        val asciiHost = if (host.startsWith('[') && host.endsWith(']')) {
            host
        } else {
            runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: return null
        }
        val authority = buildString {
            append(asciiHost.lowercase())
            if (url.port >= 0) append(':').append(url.port)
        }
        return "https://$authority${uri.rawPath.orEmpty()}"
    }

    private fun hasMalformedExplicitPort(rawAuthority: String): Boolean {
        val authority = rawAuthority.substringAfterLast('@')
        val separatorIndex = if (authority.startsWith('[')) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket < 0) return true
            if (closingBracket == authority.lastIndex) return false
            if (authority.getOrNull(closingBracket + 1) != ':') return true
            closingBracket + 1
        } else {
            authority.lastIndexOf(':').takeIf { index -> index >= 0 } ?: return false
        }
        val port = authority.substring(separatorIndex + 1).toIntOrNull() ?: return true
        return port !in 1..65_535
    }
}
