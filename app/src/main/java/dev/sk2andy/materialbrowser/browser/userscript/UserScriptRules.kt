package dev.sk2andy.materialbrowser.browser.userscript

import java.net.URI

internal object UserScriptRules {
    const val MAX_STORED_SOURCE_BYTES = 4 * 1_024 * 1_024
    const val MAX_REGISTERED_SOURCE_BYTES = 2 * 1_024 * 1_024
    const val MAX_REGISTERED_WRAPPER_BYTES = 4 * 1_024 * 1_024

    fun selectForRegistration(
        scripts: List<UserScript>,
        isPrivate: Boolean,
    ): List<UserScript> {
        if (isPrivate || !isWithinCollectionBounds(scripts)) return emptyList()
        return scripts.filter(UserScript::enabled)
    }

    fun isWithinCollectionBounds(scripts: List<UserScript>): Boolean {
        if (scripts.size > UserScriptParser.MAX_SCRIPTS) return false
        if (scripts.map(UserScript::id).distinct().size != scripts.size) return false
        if (scripts.any { script -> !isCanonical(script) }) return false
        var storedSourceBytes = 0L
        var enabledSourceBytes = 0L
        scripts.forEach { script ->
            val sourceBytes = script.source.toByteArray(Charsets.UTF_8).size.toLong()
            storedSourceBytes += sourceBytes
            if (storedSourceBytes > MAX_STORED_SOURCE_BYTES) return false
            if (script.enabled) enabledSourceBytes += sourceBytes
            if (enabledSourceBytes > MAX_REGISTERED_SOURCE_BYTES) return false
        }
        val wrapperBytes = scripts.asSequence()
            .filter(UserScript::enabled)
            .sumOf(UserScriptInjection::estimatedInjectedBytes)
        return wrapperBytes <= MAX_REGISTERED_WRAPPER_BYTES
    }

    fun matches(script: UserScript, url: String): Boolean {
        if (!isCanonical(script)) return false
        val webUrl = ParsedWebUrl.parse(url) ?: return false
        val included = script.matchPatterns.any { pattern ->
            UserScriptParser.parseMatchPattern(pattern)?.matches(webUrl) == true
        } || script.includePatterns.any { pattern -> globMatches(pattern, webUrl.value) }
        return included && script.excludePatterns.none { pattern -> globMatches(pattern, webUrl.value) }
    }

    fun allowedOriginRules(script: UserScript): Set<String> {
        if (!isCanonical(script)) return emptySet()
        val origins = linkedSetOf<String>()
        script.matchPatterns.forEach { value ->
            val pattern = UserScriptParser.parseMatchPattern(value) ?: return emptySet()
            val rules = pattern.allowedOrigins() ?: return setOf(ALL_ORIGINS)
            origins += rules
        }
        script.includePatterns.forEach { value ->
            val rules = UserScriptParser.parseGlobPattern(value)?.allowedOrigins()
                ?: return setOf(ALL_ORIGINS)
            origins += rules
        }
        return origins.ifEmpty { setOf(ALL_ORIGINS) }
    }

    internal fun matchJavascriptRegexes(script: UserScript): List<String> =
        script.matchPatterns.mapNotNull { pattern ->
            UserScriptParser.parseMatchPattern(pattern)?.javascriptRegex()
        }

    internal fun includeJavascriptRegexes(script: UserScript): List<String> =
        script.includePatterns.map(::globJavascriptRegex)

    internal fun excludeJavascriptRegexes(script: UserScript): List<String> =
        script.excludePatterns.map(::globJavascriptRegex)

    internal fun isCanonical(script: UserScript): Boolean =
        (UserScriptParser.parse(
            id = script.id,
            source = script.source,
            enabled = script.enabled,
            updatedAtMillis = script.updatedAtMillis,
        ) as? UserScriptParseResult.Accepted)?.script == script

    private fun ParsedUserScriptMatchPattern.matches(url: ParsedWebUrl): Boolean {
        if (scheme != "*" && scheme != url.scheme) return false
        if (!url.usesDefaultPort) return false
        val hostMatches = when {
            host == "*" -> true
            host.startsWith("*.") -> {
                val base = host.removePrefix("*.")
                url.host == base || url.host.endsWith(".$base")
            }
            else -> host == url.host
        }
        return hostMatches && globMatches(path, url.pathAndSuffix)
    }

    private fun ParsedUserScriptMatchPattern.allowedOrigins(): Set<String>? {
        if (host == "*") return null
        val schemes = if (scheme == "*") listOf("http", "https") else listOf(scheme)
        return schemes.flatMapTo(linkedSetOf()) { candidateScheme ->
            if (host.startsWith("*.")) {
                listOf(
                    "$candidateScheme://${host.removePrefix("*.")}",
                    "$candidateScheme://$host",
                )
            } else {
                listOf("$candidateScheme://$host")
            }
        }
    }

    private fun ParsedUserScriptGlobPattern.allowedOrigins(): Set<String>? {
        if (authority == "*") return null
        val schemes = if (scheme == "*") listOf("http", "https") else listOf(scheme)
        return schemes.mapTo(linkedSetOf()) { candidateScheme -> "$candidateScheme://$authority" }
    }

    private fun ParsedUserScriptMatchPattern.javascriptRegex(): String {
        val schemeRegex = if (scheme == "*") "https?" else regexEscape(scheme)
        val hostRegex = when {
            host == "*" -> "[^/:]+"
            host.startsWith("*.") -> {
                val base = regexEscape(host.removePrefix("*."))
                "(?:[^./:]+\\.)*$base"
            }
            else -> regexEscape(host)
        }
        return "^$schemeRegex://$hostRegex${globRegexBody(path)}$"
    }

    private fun globJavascriptRegex(value: String): String = "^${globRegexBody(value)}$"

    private fun globRegexBody(value: String): String = buildString {
        value.forEach { char ->
            if (char == '*') append(".*") else append(regexEscape(char.toString()))
        }
    }

    private fun regexEscape(value: String): String = buildString {
        value.forEach { char ->
            if (char in REGEX_SPECIAL_CHARS) append('\\')
            append(char)
        }
    }

    private fun globMatches(pattern: String, value: String): Boolean {
        var patternIndex = 0
        var valueIndex = 0
        var wildcardIndex = -1
        var wildcardValueIndex = -1
        while (valueIndex < value.length) {
            when {
                patternIndex < pattern.length && pattern[patternIndex] == value[valueIndex] -> {
                    patternIndex++
                    valueIndex++
                }
                patternIndex < pattern.length && pattern[patternIndex] == '*' -> {
                    wildcardIndex = patternIndex++
                    wildcardValueIndex = valueIndex
                }
                wildcardIndex >= 0 -> {
                    patternIndex = wildcardIndex + 1
                    valueIndex = ++wildcardValueIndex
                }
                else -> return false
            }
        }
        while (patternIndex < pattern.length && pattern[patternIndex] == '*') patternIndex++
        return patternIndex == pattern.length
    }

    private data class ParsedWebUrl(
        val value: String,
        val scheme: String,
        val host: String,
        val pathAndSuffix: String,
        val usesDefaultPort: Boolean,
    ) {
        companion object {
            fun parse(value: String): ParsedWebUrl? {
                if (value.any { char -> char.code <= 0x20 || char.code == 0x7f }) return null
                val uri = runCatching { URI(value) }.getOrNull() ?: return null
                val scheme = uri.scheme?.lowercase() ?: return null
                if (scheme != "http" && scheme != "https") return null
                if (!uri.isAbsolute || uri.rawAuthority.isNullOrBlank() || uri.rawUserInfo != null) return null
                val host = runCatching { uri.toURL().host.lowercase() }.getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: return null
                val defaultPort = if (scheme == "http") 80 else 443
                val usesDefaultPort = uri.port == -1 || uri.port == defaultPort
                val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
                val suffix = buildString {
                    uri.rawQuery?.let { query -> append('?').append(query) }
                }
                return ParsedWebUrl(
                    value = value,
                    scheme = scheme,
                    host = host,
                    pathAndSuffix = path + suffix,
                    usesDefaultPort = usesDefaultPort,
                )
            }
        }
    }

    private const val ALL_ORIGINS = "*"
    private val REGEX_SPECIAL_CHARS = setOf('\\', '.', '+', '?', '^', '$', '(', ')', '[', ']', '{', '}', '|', '/')
}
