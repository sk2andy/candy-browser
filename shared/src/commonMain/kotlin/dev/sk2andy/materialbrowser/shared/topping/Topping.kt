package dev.sk2andy.materialbrowser.shared.topping

enum class ToppingRunAt { DocumentStart, DocumentEnd }

enum class ToppingGrant(val metadataValue: String) {
    AddStyle("GM_addStyle"), DeleteValue("GM_deleteValue"), GetValue("GM_getValue"),
    GetResourceText("GM_getResourceText"), GetResourceUrl("GM_getResourceURL"), Info("GM_info"),
    ListValues("GM_listValues"), OpenInTab("GM_openInTab"),
    RegisterMenuCommand("GM_registerMenuCommand"), SetValue("GM_setValue"),
    UnregisterMenuCommand("GM_unregisterMenuCommand"),
}

data class ToppingRequire(val url: String, val sha256: String? = null)

data class ToppingResource(val name: String, val url: String, val sha256: String? = null)

data class ToppingScript(
    val id: String,
    val name: String,
    val source: String,
    val enabled: Boolean = true,
    val matchPatterns: List<String>,
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val grants: List<ToppingGrant> = emptyList(),
    val runAt: ToppingRunAt,
    val requires: List<ToppingRequire> = emptyList(),
    val resources: List<ToppingResource> = emptyList(),
    val forMainFrameOnly: Boolean = true,
)

data class ToppingInjectionPlan(
    val id: String,
    val source: String,
    val runAt: ToppingRunAt,
    val forMainFrameOnly: Boolean,
    val contentWorldName: String,
)

sealed interface ToppingParseResult {
    data class Accepted(val script: ToppingScript) : ToppingParseResult
    data class Rejected(val reason: String) : ToppingParseResult
}

/** Engine-neutral metadata, grants, dependency and URL policy. */
object ToppingRules {
    const val MAX_REQUIRE_COUNT = 16
    const val MAX_RESOURCE_COUNT = 16
    const val MAX_REQUIRE_BYTES = 256 * 1_024
    const val MAX_RESOURCE_BYTES = 512 * 1_024
    const val MAX_TOTAL_DEPENDENCY_BYTES = 2 * 1_024 * 1_024
    const val MAX_RESOURCE_NAME_LENGTH = 128

    private const val MAX_SOURCE_BYTES = 256 * 1_024
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_PATTERNS_PER_KIND = 64
    private val validId = Regex("[A-Za-z0-9._-]{1,80}")
    private val metadataEntry = Regex("^@([A-Za-z][A-Za-z0-9_-]*)\\s*(.*?)$")
    private val sha256Fragment = Regex("^sha256=([0-9A-Fa-f]{64})$")
    private val resourceName = Regex("[^\\s\\p{Cc}]{1,$MAX_RESOURCE_NAME_LENGTH}")
    private val hostname = Regex("[A-Za-z0-9.-]{1,253}")
    private val ipv4Like = Regex("[0-9.]+")

    fun parse(id: String, source: String, enabled: Boolean = true): ToppingParseResult {
        if (!validId.matches(id)) return rejected("invalid_id")
        val normalizedSource = source.removePrefix("\uFEFF")
        if (normalizedSource.isBlank() || normalizedSource.encodeToByteArray().size > MAX_SOURCE_BYTES) {
            return rejected("invalid_source_size")
        }
        val metadata = metadataLines(normalizedSource) ?: return rejected("missing_metadata")
        val values = metadata.mapNotNull { line ->
            metadataEntry.matchEntire(line)?.let { match ->
                match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        }.groupBy({ it.first }, { it.second })
        if ("connect" in values) return rejected("privileged_grant")
        val name = values["name"].orEmpty().singleOrNull()
            ?.takeIf { it.length in 1..MAX_NAME_LENGTH && it.none(Char::isISOControl) }
            ?: return rejected("invalid_name")
        val matches = values["match"].orEmpty()
        val includes = values["include"].orEmpty()
        val excludes = values["exclude"].orEmpty()
        if (matches.isEmpty() && includes.isEmpty()) return rejected("missing_match_or_include")
        if (listOf(matches, includes, excludes).any { it.size > MAX_PATTERNS_PER_KIND }) {
            return rejected("too_many_patterns")
        }
        if (matches.any { !isWebMatchPattern(it) }) return rejected("invalid_match")
        if ((includes + excludes).any { !isWebGlob(it) }) return rejected("invalid_glob")
        val grants = parseGrants(values["grant"].orEmpty()) ?: return rejected("privileged_grant")
        val runAt = when (values["run-at"].orEmpty().singleOrNull()) {
            null, "document-end" -> ToppingRunAt.DocumentEnd
            "document-start" -> ToppingRunAt.DocumentStart
            else -> return rejected("invalid_run_at")
        }
        val requireValues = values["require"].orEmpty()
        val resourceValues = values["resource"].orEmpty()
        if (requireValues.size > MAX_REQUIRE_COUNT || resourceValues.size > MAX_RESOURCE_COUNT) {
            return rejected("too_many_dependencies")
        }
        val requires = requireValues.map { declaration ->
            parseDependencyUrl(declaration)?.let { ToppingRequire(it.first, it.second) }
                ?: return rejected("invalid_require")
        }
        val resources = resourceValues.map { declaration ->
            val separator = declaration.indexOfFirst(Char::isWhitespace)
            if (separator <= 0) return rejected("invalid_resource")
            val nameValue = declaration.substring(0, separator)
            val dependency = parseDependencyUrl(declaration.substring(separator).trim())
                ?: return rejected("invalid_resource")
            if (!resourceName.matches(nameValue)) return rejected("invalid_resource")
            ToppingResource(nameValue, dependency.first, dependency.second)
        }
        if (resources.map(ToppingResource::name).distinct().size != resources.size) {
            return rejected("invalid_resource")
        }
        return ToppingParseResult.Accepted(
            ToppingScript(
                id = id,
                name = name,
                source = normalizedSource,
                enabled = enabled,
                matchPatterns = matches,
                includePatterns = includes,
                excludePatterns = excludes,
                grants = grants,
                runAt = runAt,
                requires = requires,
                resources = resources,
            ),
        )
    }

    fun injectionPlans(scripts: List<ToppingScript>): List<ToppingInjectionPlan> = scripts.asSequence()
        .filter(ToppingScript::enabled)
        .sortedBy(ToppingScript::id)
        .map(::injectionPlan)
        .toList()

    fun injectionPlan(script: ToppingScript): ToppingInjectionPlan = ToppingInjectionPlan(
        id = script.id,
        source = guardedSource(script),
        runAt = script.runAt,
        forMainFrameOnly = script.forMainFrameOnly,
        contentWorldName = "candy.topping.${script.id}",
    )

    fun matchesUrl(script: ToppingScript, url: String): Boolean {
        val parsed = parseWebUrl(url) ?: return false
        val matches = script.matchPatterns.isEmpty() || script.matchPatterns.any { matchPattern(it, parsed) }
        val includes = script.includePatterns.isEmpty() || script.includePatterns.any { globMatches(it, url) }
        return matches && includes && script.excludePatterns.none { globMatches(it, url) }
    }

    fun isTrustedDependencyHost(host: String): Boolean = host.lowercase() in trustedDependencyHosts

    private fun parseGrants(values: List<String>): List<ToppingGrant>? {
        if (values.any { it.equals("none", true) } && values.size > 1) return null
        if (values.isEmpty() || values.singleOrNull()?.equals("none", true) == true) return emptyList()
        return values.map { value ->
            ToppingGrant.entries.firstOrNull { grant ->
                grant.metadataValue.equals(value, true) || grant.aliases.any { it.equals(value, true) }
            } ?: return null
        }.distinct()
    }

    private fun guardedSource(script: ToppingScript): String {
        val matches = script.matchPatterns.javascriptArray()
        val includes = script.includePatterns.javascriptArray()
        val excludes = script.excludePatterns.javascriptArray()
        return """
            (() => {
              'use strict';
              const matchPatterns = [$matches];
              const includePatterns = [$includes];
              const excludePatterns = [$excludes];
              const globRegex = value => new RegExp('^' + value
                .replace(/[.+?^${'$'}()|[\]\\]/g, '\\${'$'}&')
                .replace(/\\\*/g, '.*') + '${'$'}');
              const matches = value => globRegex(value).test(location.href);
              const included = (matchPatterns.length === 0 || matchPatterns.some(matches)) &&
                (includePatterns.length === 0 || includePatterns.some(matches));
              if (!included || excludePatterns.some(matches) || window.top !== window) return;
              ${script.source}
            })();
        """.trimIndent()
    }

    private fun parseDependencyUrl(value: String): Pair<String, String?>? {
        if (value.isBlank() || value.any { it.isWhitespace() || it.isISOControl() }) return null
        val withoutFragment = value.substringBefore('#')
        val fragment = value.substringAfter('#', missingDelimiterValue = "").takeIf(String::isNotEmpty)
        val parsed = parseWebUrl(withoutFragment) ?: return null
        if (parsed.scheme != "https" || parsed.port != 443) return null
        if (!isPublicHostnameCandidate(parsed.host)) return null
        val sha256 = fragment?.let { sha256Fragment.matchEntire(it)?.groupValues?.get(1)?.lowercase() }
        if (fragment != null && sha256 == null) return null
        return withoutFragment to sha256
    }

    private fun isWebMatchPattern(value: String): Boolean {
        if (value == "<all_urls>") return true
        val parsed = parsePattern(value) ?: return false
        return parsed.scheme in setOf("http", "https", "*") && parsed.port == null &&
            isValidPatternHost(parsed.host)
    }

    private fun isWebGlob(value: String): Boolean {
        val parsed = parsePattern(value) ?: return false
        return parsed.scheme in setOf("http", "https", "*") && isValidPatternHost(parsed.host)
    }

    private fun parsePattern(value: String): PatternParts? {
        if (value.length !in 1..2_048 || value.any(Char::isISOControl)) return null
        val schemeEnd = value.indexOf("://")
        if (schemeEnd <= 0) return null
        val pathStart = value.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return null
        val authority = value.substring(schemeEnd + 3, pathStart)
        if (authority.isBlank() || '@' in authority) return null
        val portIndex = authority.lastIndexOf(':')
        val port = if (portIndex >= 0) authority.substring(portIndex + 1).toIntOrNull() else null
        if (portIndex >= 0 && port !in 1..65_535) return null
        return PatternParts(
            value.substring(0, schemeEnd).lowercase(),
            if (portIndex >= 0) authority.substring(0, portIndex).lowercase() else authority.lowercase(),
            port,
            value.substring(pathStart),
        )
    }

    private fun parseWebUrl(value: String): WebUrlParts? {
        val schemeEnd = value.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = value.substring(0, schemeEnd).lowercase()
        if (scheme !in setOf("http", "https")) return null
        val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd + 3)
            .takeIf { it >= 0 } ?: value.length
        val authority = value.substring(schemeEnd + 3, authorityEnd)
        if (authority.isBlank() || '@' in authority || authority.startsWith('[')) return null
        val portIndex = authority.lastIndexOf(':')
        val port = if (portIndex >= 0) authority.substring(portIndex + 1).toIntOrNull() else null
        if (portIndex >= 0 && port !in 1..65_535) return null
        val host = (if (portIndex >= 0) authority.substring(0, portIndex) else authority).lowercase()
        if (host.isBlank()) return null
        val path = value.substring(authorityEnd).substringBefore('#').ifEmpty { "/" }
        return WebUrlParts(scheme, host, port ?: if (scheme == "https") 443 else 80, path)
    }

    private fun isValidPatternHost(value: String): Boolean = value == "*" ||
        value.removePrefix("*.").let { it.isNotBlank() && '*' !in it && isPublicHostnameCandidate(it) }

    private fun isPublicHostnameCandidate(value: String): Boolean {
        val normalized = value.lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized.endsWith(".local")) return false
        if (':' in normalized || ipv4Like.matches(normalized) || !hostname.matches(normalized)) return false
        return normalized.split('.').all { label ->
            label.length in 1..63 && !label.startsWith('-') && !label.endsWith('-')
        }
    }

    private fun matchPattern(pattern: String, url: WebUrlParts): Boolean {
        if (pattern == "<all_urls>") return true
        val parsed = parsePattern(pattern) ?: return false
        val baseHost = parsed.host.removePrefix("*.")
        val usesDefaultPort = url.port == if (url.scheme == "https") 443 else 80
        return usesDefaultPort && (parsed.scheme == "*" || parsed.scheme == url.scheme) &&
            (parsed.host == "*" || parsed.host == url.host ||
                (parsed.host.startsWith("*.") && (url.host == baseHost || url.host.endsWith(".$baseHost")))) &&
            globMatches(parsed.path, url.path)
    }

    private fun globMatches(pattern: String, value: String): Boolean {
        var patternIndex = 0
        var valueIndex = 0
        var wildcard = -1
        var backtrack = 0
        while (valueIndex < value.length) {
            if (patternIndex < pattern.length && pattern[patternIndex] == value[valueIndex]) {
                patternIndex++
                valueIndex++
            } else if (patternIndex < pattern.length && pattern[patternIndex] == '*') {
                wildcard = patternIndex++
                backtrack = valueIndex
            } else if (wildcard >= 0) {
                patternIndex = wildcard + 1
                valueIndex = ++backtrack
            } else return false
        }
        while (patternIndex < pattern.length && pattern[patternIndex] == '*') patternIndex++
        return patternIndex == pattern.length
    }

    private fun List<String>.javascriptArray(): String = joinToString(",") { "\"${it.javascriptEscaped()}\"" }

    private fun metadataLines(source: String): List<String>? {
        val lines = source.lineSequence().map { it.removeSuffix("\r") }.toList()
        val start = lines.indexOfFirst { it.trim() == "// ==UserScript==" }
        if (start < 0) return null
        val end = lines.indexOfFirst { index, line -> index > start && line.trim() == "// ==/UserScript==" }
        if (end < 0) return null
        return lines.subList(start + 1, end).map { it.trim().removePrefix("//").trim() }
    }

    private fun String.javascriptEscaped(): String = buildString {
        this@javascriptEscaped.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private val ToppingGrant.aliases: List<String>
        get() = when (this) {
            ToppingGrant.AddStyle -> listOf("GM.addStyle")
            ToppingGrant.DeleteValue -> listOf("GM.deleteValue")
            ToppingGrant.GetValue -> listOf("GM.getValue")
            ToppingGrant.GetResourceText -> listOf("GM.getResourceText")
            ToppingGrant.GetResourceUrl -> listOf("GM.getResourceUrl", "GM.getResourceURL")
            ToppingGrant.Info -> listOf("GM.info")
            ToppingGrant.ListValues -> listOf("GM.listValues")
            ToppingGrant.OpenInTab -> listOf("GM.openInTab")
            ToppingGrant.RegisterMenuCommand -> listOf("GM.registerMenuCommand")
            ToppingGrant.SetValue -> listOf("GM.setValue")
            ToppingGrant.UnregisterMenuCommand -> listOf("GM.unregisterMenuCommand")
        }

    private fun rejected(reason: String) = ToppingParseResult.Rejected(reason)
    private data class PatternParts(val scheme: String, val host: String, val port: Int?, val path: String)
    private data class WebUrlParts(val scheme: String, val host: String, val port: Int, val path: String)

    private val trustedDependencyHosts = setOf(
        "cdn.jsdelivr.net", "fonts.googleapis.com", "fonts.gstatic.com", "gist.githubusercontent.com",
        "gitlab.com", "greasyfork.org", "openuserjs.org", "raw.githubusercontent.com", "unpkg.com",
        "update.greasyfork.org",
    )
}

private inline fun List<String>.indexOfFirst(predicate: (Int, String) -> Boolean): Int {
    forEachIndexed { index, value -> if (predicate(index, value)) return index }
    return -1
}
