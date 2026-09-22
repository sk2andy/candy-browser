package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.net.IDN
import java.net.URI
import java.util.Locale

internal object AutoDeAmpRules {
    fun publisherUrlFor(url: String?): String? {
        val sourceUrl = BrowserUriPolicy.normalizeHttpUrl(url)
            ?.takeIf { value -> value.length <= MAX_URL_LENGTH }
            ?: return null
        if (ENCODED_CONTROL.containsMatchIn(sourceUrl)) return null
        val sourceUri = parse(sourceUrl) ?: return null
        val candidate = extractPublisherUrl(sourceUri) ?: return null
        val targetUrl = BrowserUriPolicy.normalizeHttpUrl(candidate)
            ?.takeIf { value -> value.length <= MAX_URL_LENGTH }
            ?: return null
        val targetUri = parse(targetUrl) ?: return null
        if (targetUri.rawUserInfo != null || targetUri.port != -1) return null
        if (canonicalHost(targetUri) == null) return null
        if (isWrapperShape(targetUri)) return null
        return targetUrl.takeIf { value -> value != sourceUrl }
    }

    private fun isWrapperShape(uri: URI): Boolean {
        val host = canonicalHost(uri) ?: return false
        val path = uri.rawPath ?: return false
        return when {
            host in GOOGLE_AMP_HOSTS -> path.startsWith(GOOGLE_HTTP_PATH_PREFIX)
            host.endsWith(AMP_CACHE_SUFFIX) ->
                CACHE_HTTPS_PATH_PREFIXES.any(path::startsWith) ||
                    CACHE_HTTP_PATH_PREFIXES.any(path::startsWith)
            else -> false
        }
    }

    private fun extractPublisherUrl(uri: URI): String? {
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) return null
        val sourceHost = canonicalHost(uri) ?: return null
        return when {
            sourceHost in GOOGLE_AMP_HOSTS -> googlePublisherUrl(uri)
            sourceHost.endsWith(AMP_CACHE_SUFFIX) -> cachePublisherUrl(uri, sourceHost)
            else -> null
        }
    }

    private fun googlePublisherUrl(uri: URI): String? {
        val rawPath = uri.rawPath ?: return null
        val (scheme, publisherPath) = when {
            rawPath.startsWith(GOOGLE_HTTPS_PATH_PREFIX) ->
                "https" to rawPath.removePrefix(GOOGLE_HTTPS_PATH_PREFIX)
            rawPath.startsWith(GOOGLE_HTTP_PATH_PREFIX) ->
                "http" to rawPath.removePrefix(GOOGLE_HTTP_PATH_PREFIX)
            else -> return null
        }
        return publisherUrl(
            scheme = scheme,
            publisherPath = publisherPath,
            source = uri,
            stripViewerMetadata = true,
        )
    }

    private fun cachePublisherUrl(uri: URI, sourceHost: String): String? {
        val cachePrefix = sourceHost.removeSuffix(AMP_CACHE_SUFFIX)
            .takeIf { prefix -> prefix.isNotBlank() && '.' !in prefix }
            ?: return null
        val rawPath = uri.rawPath ?: return null
        val httpsPrefix = CACHE_HTTPS_PATH_PREFIXES.firstOrNull(rawPath::startsWith)
        val httpPrefix = CACHE_HTTP_PATH_PREFIXES.firstOrNull(rawPath::startsWith)
        val (scheme, publisherPath) = when {
            httpsPrefix != null -> "https" to rawPath.removePrefix(httpsPrefix)
            httpPrefix != null -> "http" to rawPath.removePrefix(httpPrefix)
            else -> return null
        }
        val candidate = publisherUrl(
            scheme = scheme,
            publisherPath = publisherPath,
            source = uri,
            stripViewerMetadata = httpsPrefix?.startsWith("/v/") == true ||
                httpPrefix?.startsWith("/v/") == true,
        ) ?: return null
        val publisherHost = BrowserUriPolicy.normalizeHttpUrl(candidate)
            ?.let(::parse)
            ?.let(::canonicalHost)
            ?: return null
        return candidate.takeIf { cachePrefixFor(publisherHost) == cachePrefix }
    }

    private fun publisherUrl(
        scheme: String,
        publisherPath: String,
        source: URI,
        stripViewerMetadata: Boolean,
    ): String? {
        if (publisherPath.isBlank() || publisherPath.startsWith('/')) return null
        val publisherQuery = if (stripViewerMetadata) {
            stripParameters(source.rawQuery, CACHE_ONLY_QUERY_PARAMETERS)
        } else {
            source.rawQuery
        }
        val publisherFragment = if (stripViewerMetadata) {
            stripParameters(source.rawFragment, VIEWER_FRAGMENT_PARAMETERS)
        } else {
            source.rawFragment
        }
        return buildString {
            append(scheme)
            append("://")
            append(publisherPath)
            publisherQuery?.let { query ->
                append('?')
                append(query)
            }
            publisherFragment?.let { fragment ->
                append('#')
                append(fragment)
            }
        }
    }

    private fun stripParameters(rawValue: String?, names: Set<String>): String? = rawValue
        ?.split('&')
        ?.filterNot { parameter ->
            parameter.substringBefore('=').lowercase(Locale.ROOT) in names
        }
        ?.joinToString("&")
        ?.takeIf(String::isNotEmpty)

    private fun cachePrefixFor(host: String): String? {
        val unicodeHost = runCatching { IDN.toUnicode(host) }.getOrNull() ?: return null
        var prefix = buildString {
            unicodeHost.forEach { character ->
                when (character) {
                    '-' -> append("--")
                    '.' -> append('-')
                    else -> append(character)
                }
            }
        }
        if (prefix.length >= 4 && prefix[2] == '-' && prefix[3] == '-') {
            prefix = "0-$prefix-0"
        }
        return runCatching { IDN.toASCII(prefix, IDN.USE_STD3_ASCII_RULES) }
            .getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { value ->
                value.length in 1..63 && value.first() != '-' && value.last() != '-'
            }
    }

    private fun canonicalHost(uri: URI): String? = runCatching { uri.toURL().host }
        .getOrNull()
        ?.let(CandyHostCanonicalizer::canonicalHost)

    private fun parse(value: String): URI? = runCatching { URI(value) }.getOrNull()

    private const val MAX_URL_LENGTH = 32_768
    private const val GOOGLE_HTTPS_PATH_PREFIX = "/amp/s/"
    private const val GOOGLE_HTTP_PATH_PREFIX = "/amp/"
    private const val AMP_CACHE_SUFFIX = ".cdn.ampproject.org"
    private val CACHE_HTTPS_PATH_PREFIXES = listOf("/c/s/", "/v/s/")
    private val CACHE_HTTP_PATH_PREFIXES = listOf("/c/", "/v/")
    private val GOOGLE_AMP_HOSTS = setOf("google.com", "www.google.com")
    private val ENCODED_CONTROL = Regex("%0[09AaDd]", RegexOption.IGNORE_CASE)
    private val CACHE_ONLY_QUERY_PARAMETERS = setOf(
        "amp_gsa",
        "amp_js_v",
        "amp_lite",
        "amp_r",
        "usqp",
    )
    private val VIEWER_FRAGMENT_PARAMETERS = setOf(
        "amp_tf",
        "ampshare",
        "aoh",
        "referrer",
    )
}
