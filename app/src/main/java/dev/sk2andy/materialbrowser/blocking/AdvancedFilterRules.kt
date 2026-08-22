package dev.sk2andy.materialbrowser.blocking

import java.net.URI
import java.util.Base64
import java.util.Collections
import java.util.LinkedHashMap

internal enum class AdvancedFilterScope { Request, Popup, Popunder }

internal enum class AdvancedFilterAction { Block, Allow }

internal enum class AdvancedParty { Any, FirstParty, ThirdParty }

internal data class AdvancedUrlRule(
    val action: AdvancedFilterAction,
    val scope: AdvancedFilterScope,
    val targetHostPattern: String?,
    val urlPattern: String,
    val pageHostPatterns: List<String>,
    val excludedPageHostPatterns: List<String>,
    val party: AdvancedParty,
    val windowOpenDefuser: Boolean,
)

internal class AdvancedFilterRules private constructor(
    val rules: List<AdvancedUrlRule>,
) {
    private val requestRules = RuleIndex(rules.filter { it.scope == AdvancedFilterScope.Request })
    private val popupRules = RuleIndex(
        rules.filter { it.scope == AdvancedFilterScope.Popup && !it.windowOpenDefuser },
    )
    private val popunderRules = RuleIndex(
        rules.filter { it.scope == AdvancedFilterScope.Popunder },
    )
    private val windowOpenDefuserRules = rules.filter(AdvancedUrlRule::windowOpenDefuser)
    private val windowOpenDefuserIndex = PageRuleIndex(windowOpenDefuserRules)
    private val windowOpenDefuserCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(DEFUSER_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Boolean>?,
            ): Boolean = size > DEFUSER_CACHE_SIZE
        },
    )

    fun shouldBlockRequest(requestUrl: String, pageUrl: String?): Boolean {
        return decideRequest(requestUrl, pageUrl) == AdvancedFilterAction.Block
    }

    fun decideRequest(requestUrl: String, pageUrl: String?): AdvancedFilterAction? {
        val request = ParsedWebUrl.parse(requestUrl) ?: return null
        val page = ParsedWebUrl.parse(pageUrl)
        return requestRules.decide(request, page)
    }

    fun decideRequest(
        requestUrl: String,
        requestHost: String?,
        pageHost: String?,
    ): AdvancedFilterAction? = requestRules.decide(
        targetUrl = requestUrl,
        targetHost = requestHost,
        pageHost = pageHost,
    )

    fun shouldBlockPopup(targetUrl: String, openerUrl: String?): Boolean =
        decidePopup(targetUrl, openerUrl) == AdvancedFilterAction.Block

    fun decidePopup(targetUrl: String, openerUrl: String?): AdvancedFilterAction? =
        popupRules.decide(targetUrl, openerUrl)

    fun decidePopunder(openerTargetUrl: String, childUrl: String?): AdvancedFilterAction? =
        popunderRules.decide(openerTargetUrl, childUrl)

    fun shouldBlockPopupWithoutTarget(openerUrl: String?): Boolean =
        popupRules.decideWithoutTarget(ParsedWebUrl.parse(openerUrl)) ==
            AdvancedFilterAction.Block

    fun shouldDefuseWindowOpen(pageUrl: String?): Boolean {
        val page = ParsedWebUrl.parse(pageUrl) ?: return false
        windowOpenDefuserCache[page.host]?.let { return it }
        return windowOpenDefuserIndex.matches(page.host).also { result ->
            windowOpenDefuserCache[page.host] = result
        }
    }

    private class RuleIndex(input: List<AdvancedUrlRule>) {
        private val potentialTargetAllows = input.filter {
            it.action == AdvancedFilterAction.Allow
        }
        private val byTargetHost = input.asSequence()
            .filter { it.targetHostPattern != null && '*' !in it.targetHostPattern }
            .groupBy { it.targetHostPattern.orEmpty() }
        private val wildcardTargetHosts = input.filter {
            it.targetHostPattern?.contains('*') == true
        }
        private val anyTargetHostByPage = input.asSequence()
            .filter { it.targetHostPattern == null }
            .flatMap { rule ->
                rule.pageHostPatterns.asSequence()
                    .filterNot { '*' in it }
                    .map { pageHost -> pageHost to rule }
            }
            .groupBy({ it.first }, { it.second })
        private val anyTargetHostWildcardPages = input.filter { rule ->
            rule.targetHostPattern == null && rule.pageHostPatterns.any { '*' in it }
        }

        fun decide(targetUrl: String, pageUrl: String?): AdvancedFilterAction? {
            val target = ParsedWebUrl.parse(targetUrl) ?: return null
            val page = ParsedWebUrl.parse(pageUrl)
            return decide(target, page)
        }

        fun decide(
            targetUrl: String,
            targetHost: String?,
            pageHost: String?,
        ): AdvancedFilterAction? {
            val safeTargetHost = CandyHostCanonicalizer.canonicalHost(targetHost) ?: return null
            val safePageHost = CandyHostCanonicalizer.canonicalHost(pageHost)
            val candidates = candidates(safeTargetHost, safePageHost)
            if (candidates.isEmpty()) return null
            val target = ParsedWebUrl.parse(targetUrl, safeTargetHost) ?: return null
            val page = safePageHost?.let(ParsedWebUrl::fromHost)
            return decide(candidates, target, page)
        }

        fun decide(target: ParsedWebUrl, page: ParsedWebUrl?): AdvancedFilterAction? {
            val candidates = candidates(target.host, page?.host)
            return decide(candidates, target, page)
        }

        private fun decide(
            candidates: Collection<AdvancedUrlRule>,
            target: ParsedWebUrl,
            page: ParsedWebUrl?,
        ): AdvancedFilterAction? {
            val thirdParty = if (
                page != null && candidates.any { rule -> rule.party != AdvancedParty.Any }
            ) {
                CandyPublicSuffixRules.registrableDomain(target.host) !=
                    CandyPublicSuffixRules.registrableDomain(page.host)
            } else {
                null
            }
            var blockMatched = false
            candidates.forEach { rule ->
                if (!rule.matches(target, page, thirdParty)) return@forEach
                if (rule.action == AdvancedFilterAction.Allow) {
                    return AdvancedFilterAction.Allow
                }
                blockMatched = true
            }
            return AdvancedFilterAction.Block.takeIf { blockMatched }
        }

        private fun candidates(
            targetHost: String,
            pageHost: String?,
        ): Set<AdvancedUrlRule> = buildSet {
            var host = targetHost
            while (true) {
                byTargetHost[host]?.let(::addAll)
                val dot = host.indexOf('.')
                if (dot < 0) break
                host = host.substring(dot + 1)
            }
            wildcardTargetHosts.filterTo(this) { rule ->
                CosmeticHostPattern.matches(targetHost, rule.targetHostPattern.orEmpty())
            }
            if (pageHost != null) {
                var candidatePageHost: String = pageHost
                while (true) {
                    anyTargetHostByPage[candidatePageHost]?.let(::addAll)
                    val dot = candidatePageHost.indexOf('.')
                    if (dot < 0) break
                    candidatePageHost = candidatePageHost.substring(dot + 1)
                }
                anyTargetHostWildcardPages.filterTo(this) { rule ->
                    rule.pageHostPatterns.any { pattern ->
                        CosmeticHostPattern.matches(pageHost, pattern)
                    }
                }
            }
        }

        fun decideWithoutTarget(page: ParsedWebUrl?): AdvancedFilterAction? {
            if (page == null) return null
            if (potentialTargetAllows.any { rule -> rule.matchesPage(page) }) return null
            val candidates = buildList {
                var pageHost = page.host
                while (true) {
                    anyTargetHostByPage[pageHost]?.let(::addAll)
                    val dot = pageHost.indexOf('.')
                    if (dot < 0) break
                    pageHost = pageHost.substring(dot + 1)
                }
                anyTargetHostWildcardPages.filterTo(this) { rule ->
                    rule.pageHostPatterns.any { pattern ->
                        CosmeticHostPattern.matches(page.host, pattern)
                    }
                }
            }.distinct().filter { rule ->
                rule.urlPattern == "*" && rule.party == AdvancedParty.Any &&
                    rule.excludedPageHostPatterns.none { pattern ->
                        CosmeticHostPattern.matches(page.host, pattern)
                    }
            }
            if (candidates.any { it.action == AdvancedFilterAction.Allow }) {
                return AdvancedFilterAction.Allow
            }
            return AdvancedFilterAction.Block.takeIf {
                candidates.any { rule -> rule.action == AdvancedFilterAction.Block }
            }
        }
    }

    companion object {
        const val HEADER = "candy-advanced-filter:2"
        private const val MAX_BYTES = 8 * 1_024 * 1_024
        private const val MAX_LINES = 100_000
        private const val MAX_PATTERN_LENGTH = 512
        private const val DEFUSER_CACHE_SIZE = 256

        val Empty = AdvancedFilterRules(emptyList())

        fun parse(text: String): AdvancedFilterRules {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                "Advanced filter asset too large"
            }
            val lines = text.lineSequence().toList()
            require(lines.size <= MAX_LINES) { "Too many advanced filter rules" }
            require(lines.firstOrNull()?.trimStart('\uFEFF') == HEADER) {
                "Invalid advanced filter asset header"
            }
            val declaredRules = lines.asSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith(RULE_COUNT_PREFIX) }
                ?.removePrefix(RULE_COUNT_PREFIX)
                ?.trim()
                ?.toIntOrNull()
                ?: error("Missing advanced filter rule count")
            val rules = lines.drop(1).mapIndexedNotNull { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith('#')) return@mapIndexedNotNull null
                parseRule(line, index + 2)
            }
            require(rules.size == declaredRules) { "Advanced filter rule count mismatch" }
            require(rules.distinct().size == rules.size) { "Duplicate advanced filter rule" }
            return AdvancedFilterRules(rules)
        }

        private fun parseRule(line: String, lineNumber: Int): AdvancedUrlRule {
            val fields = line.split('\t')
            require(fields.size == 8) { "Invalid advanced filter at line $lineNumber" }
            val action = when (fields[0]) {
                "B" -> AdvancedFilterAction.Block
                "A" -> AdvancedFilterAction.Allow
                else -> error("Invalid advanced action at line $lineNumber")
            }
            val scope = when (fields[1]) {
                "N" -> AdvancedFilterScope.Request
                "P" -> AdvancedFilterScope.Popup
                "U" -> AdvancedFilterScope.Popunder
                else -> error("Invalid advanced scope at line $lineNumber")
            }
            val targetHost = fields[2].takeUnless { it == "*" }?.let { value ->
                CosmeticHostPattern.canonicalize(value)
                    ?: error("Invalid target host at line $lineNumber")
            }
            val urlPattern = decode(fields[3], lineNumber)
            require(urlPattern.length in 1..MAX_PATTERN_LENGTH && isSafePattern(urlPattern)) {
                "Invalid URL pattern at line $lineNumber"
            }
            val pageHosts = parseHostPatterns(fields[4], lineNumber)
            val excludedPageHosts = parseHostPatterns(fields[5], lineNumber)
            val party = when (fields[6]) {
                "*" -> AdvancedParty.Any
                "1" -> AdvancedParty.FirstParty
                "3" -> AdvancedParty.ThirdParty
                else -> error("Invalid party at line $lineNumber")
            }
            val windowOpenDefuser = when (fields[7]) {
                "-" -> false
                "W" -> true
                else -> error("Unsupported advanced field at line $lineNumber")
            }
            require(!windowOpenDefuser || (
                scope == AdvancedFilterScope.Popup && action == AdvancedFilterAction.Block &&
                    targetHost == null && urlPattern == "*" && party == AdvancedParty.Any
            )) { "Invalid window-open defuser at line $lineNumber" }
            require(targetHost != null || pageHosts.isNotEmpty()) {
                "Unscoped generic filter at line $lineNumber"
            }
            return AdvancedUrlRule(
                action = action,
                scope = scope,
                targetHostPattern = targetHost,
                urlPattern = urlPattern,
                pageHostPatterns = pageHosts,
                excludedPageHostPatterns = excludedPageHosts,
                party = party,
                windowOpenDefuser = windowOpenDefuser,
            )
        }

        private fun parseHostPatterns(field: String, lineNumber: Int): List<String> =
            if (field == "-") {
                emptyList()
            } else {
                field.split(',').map { value ->
                    CosmeticHostPattern.canonicalize(value)
                        ?: error("Invalid page host at line $lineNumber")
                }.distinct().sorted()
            }

        private fun decode(value: String, lineNumber: Int): String = runCatching {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        }.getOrElse { error("Invalid URL pattern encoding at line $lineNumber") }

        private fun isSafePattern(pattern: String): Boolean =
            (pattern == "*" || pattern.startsWith('|')) &&
                pattern.none(Char::isISOControl) && '\\' !in pattern &&
                pattern.count { it == '*' } <= 8

        private const val RULE_COUNT_PREFIX = "# Rules:"
    }

    private class PageRuleIndex(input: List<AdvancedUrlRule>) {
        private val exact = input.asSequence()
            .flatMap { rule ->
                rule.pageHostPatterns.asSequence()
                    .filterNot { '*' in it }
                    .map { host -> host to rule }
            }
            .groupBy({ it.first }, { it.second })
        private val wildcard = input.filter { rule ->
            rule.pageHostPatterns.any { '*' in it }
        }

        fun matches(pageHost: String): Boolean {
            var host = pageHost
            while (true) {
                if (exact[host].orEmpty().any { rule -> rule.matchesPageHost(pageHost) }) {
                    return true
                }
                val dot = host.indexOf('.')
                if (dot < 0) break
                host = host.substring(dot + 1)
            }
            return wildcard.any { rule -> rule.matchesPageHost(pageHost) }
        }
    }
}

private data class ParsedWebUrl(
    val host: String,
    val pathAndQuery: String,
) {
    companion object {
        fun parse(rawUrl: String?): ParsedWebUrl? {
            val uri = runCatching { URI(rawUrl ?: return null) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
            val host = CandyHostCanonicalizer.canonicalHost(uri.host) ?: return null
            val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
            val query = uri.rawQuery?.let { value -> "?$value" }.orEmpty()
            val value = path + query
            if (value.length > MAX_URL_LENGTH) return null
            return ParsedWebUrl(host, value)
        }

        fun parse(rawUrl: String, knownHost: String): ParsedWebUrl? {
            val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
            if (CandyHostCanonicalizer.canonicalHost(uri.host) != knownHost) return null
            val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
            val query = uri.rawQuery?.let { value -> "?$value" }.orEmpty()
            val value = path + query
            if (value.length > MAX_URL_LENGTH) return null
            return ParsedWebUrl(knownHost, value)
        }

        fun fromHost(host: String): ParsedWebUrl = ParsedWebUrl(host, "/")

        private const val MAX_URL_LENGTH = 8 * 1_024
    }
}

private fun AdvancedUrlRule.matches(
    target: ParsedWebUrl,
    page: ParsedWebUrl?,
    isThirdParty: Boolean?,
): Boolean {
    if (targetHostPattern != null && !CosmeticHostPattern.matches(target.host, targetHostPattern)) {
        return false
    }
    if (pageHostPatterns.isNotEmpty() &&
        (page == null || pageHostPatterns.none { CosmeticHostPattern.matches(page.host, it) })
    ) return false
    if (page != null && excludedPageHostPatterns.any { CosmeticHostPattern.matches(page.host, it) }) {
        return false
    }
    if (party == AdvancedParty.FirstParty && isThirdParty != false) return false
    if (party == AdvancedParty.ThirdParty && isThirdParty != true) return false
    return CandyUrlPattern.matches(target.pathAndQuery, urlPattern)
}

private fun AdvancedUrlRule.matchesPageHost(pageHost: String): Boolean =
    pageHostPatterns.any { pattern -> CosmeticHostPattern.matches(pageHost, pattern) } &&
        excludedPageHostPatterns.none { pattern ->
            CosmeticHostPattern.matches(pageHost, pattern)
        }

private fun AdvancedUrlRule.matchesPage(page: ParsedWebUrl): Boolean =
    (pageHostPatterns.isEmpty() ||
        pageHostPatterns.any { CosmeticHostPattern.matches(page.host, it) }) &&
        excludedPageHostPatterns.none { CosmeticHostPattern.matches(page.host, it) }

internal object CandyUrlPattern {
    fun matches(value: String, pattern: String): Boolean {
        val anchoredStart = pattern.startsWith('|')
        val anchoredEnd = pattern.endsWith('|') && pattern.length > 1
        val body = pattern
            .removePrefix("|")
            .removeSuffix("|")
        if (body.isEmpty()) return false
        if (anchoredStart) return matchAt(value, body, 0, anchoredEnd)
        for (start in 0..value.length) {
            if (matchAt(value, body, start, anchoredEnd)) return true
        }
        return false
    }

    private fun matchAt(value: String, pattern: String, start: Int, anchoredEnd: Boolean): Boolean {
        var valueIndex = start
        var patternIndex = 0
        var starPatternIndex = -1
        var starValueIndex = -1
        while (valueIndex < value.length) {
            if (patternIndex == pattern.length) return !anchoredEnd
            if (patternIndex < pattern.length && pattern[patternIndex] == '*') {
                starPatternIndex = patternIndex++
                starValueIndex = valueIndex
            } else if (patternIndex < pattern.length &&
                tokenMatches(pattern[patternIndex], value[valueIndex])
            ) {
                patternIndex++
                valueIndex++
            } else if (starPatternIndex >= 0) {
                patternIndex = starPatternIndex + 1
                valueIndex = ++starValueIndex
            } else {
                return false
            }
        }
        while (patternIndex < pattern.length && pattern[patternIndex] == '*') patternIndex++
        val matched = patternIndex == pattern.length ||
            (patternIndex == pattern.lastIndex && pattern[patternIndex] == '^')
        return matched && (!anchoredEnd || valueIndex == value.length)
    }

    private fun tokenMatches(token: Char, value: Char): Boolean = when (token) {
        '^' -> !value.isLetterOrDigit() && value !in setOf('_', '-', '.', '%')
        else -> token.equals(value, ignoreCase = true)
    }
}

internal object CandyWindowOpenDefuserScript {
    const val script =
        "(function(){if(window.__candyWindowOpenState)return;" +
            "var original=window.open;window.__candyWindowOpenState={open:original};" +
            "window.open=function(){return null}})()"

    const val cleanupScript =
        "(function clean(w){try{var s=w.__candyWindowOpenState;if(s){" +
            "w.open=s.open;delete w.__candyWindowOpenState}" +
            "Array.from(w.frames).forEach(clean)}catch(ignored){}})(window)"
}
