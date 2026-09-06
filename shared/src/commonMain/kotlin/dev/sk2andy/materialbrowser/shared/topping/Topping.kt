package dev.sk2andy.materialbrowser.shared.topping

enum class ToppingRunAt {
    DocumentStart,
    DocumentEnd,
}

enum class ToppingGrant {
    AddStyle,
    Info,
}

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

object ToppingRules {
    private const val MAX_SOURCE_BYTES = 256 * 1_024
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_PATTERNS_PER_KIND = 64
    private val validId = Regex("[A-Za-z0-9._-]{1,80}")
    private val metadataEntry = Regex("^@([A-Za-z][A-Za-z0-9_-]*)\\s*(.*?)$")

    fun parse(
        id: String,
        source: String,
        enabled: Boolean = true,
    ): ToppingParseResult {
        if (!validId.matches(id)) return ToppingParseResult.Rejected("invalid_id")
        if (source.isBlank() || source.encodeToByteArray().size > MAX_SOURCE_BYTES) {
            return ToppingParseResult.Rejected("invalid_source_size")
        }
        val metadata = metadataLines(source) ?: return ToppingParseResult.Rejected("missing_metadata")
        val values = metadata.mapNotNull { line ->
            metadataEntry.matchEntire(line)?.let { match ->
                match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        }.groupBy({ entry -> entry.first }, { entry -> entry.second })
        val name = values["name"].orEmpty().singleOrNull()
            ?.takeIf { it.length in 1..MAX_NAME_LENGTH && it.none(Char::isISOControl) }
            ?: return ToppingParseResult.Rejected("invalid_name")
        val matches = values["match"].orEmpty()
        val includes = values["include"].orEmpty()
        val excludes = values["exclude"].orEmpty()
        if (matches.isEmpty() && includes.isEmpty()) {
            return ToppingParseResult.Rejected("missing_match_or_include")
        }
        if (listOf(matches, includes, excludes).any { it.size > MAX_PATTERNS_PER_KIND }) {
            return ToppingParseResult.Rejected("too_many_patterns")
        }
        if (matches.any { !isWebMatchPattern(it) }) {
            return ToppingParseResult.Rejected("invalid_match")
        }
        if ((includes + excludes).any { !isWebGlob(it) }) {
            return ToppingParseResult.Rejected("invalid_glob")
        }
        val grants = parseGrants(values["grant"].orEmpty())
            ?: return ToppingParseResult.Rejected("privileged_grant")
        val runAt = when (values["run-at"].orEmpty().singleOrNull()) {
            null, "document-end" -> ToppingRunAt.DocumentEnd
            "document-start" -> ToppingRunAt.DocumentStart
            else -> return ToppingParseResult.Rejected("invalid_run_at")
        }
        return ToppingParseResult.Accepted(
            ToppingScript(
                id = id,
                name = name,
                source = source,
                enabled = enabled,
                matchPatterns = matches,
                includePatterns = includes,
                excludePatterns = excludes,
                grants = grants,
                runAt = runAt,
                // WKUserScript cannot safely emulate extension frame privileges. Candy therefore
                // makes the conservative top-frame boundary explicit for every iOS Topping.
                forMainFrameOnly = true,
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

    private fun parseGrants(values: List<String>): List<ToppingGrant>? {
        val normalized = values.map(String::lowercase)
        if ("none" in normalized && normalized.size > 1) return null
        if (normalized == listOf("none") || normalized.isEmpty()) return emptyList()
        return normalized.map { value ->
            when (value) {
                "gm_addstyle", "gm.addstyle" -> ToppingGrant.AddStyle
                "gm_info", "gm.info" -> ToppingGrant.Info
                else -> return null
            }
        }.distinct()
    }

    private fun guardedSource(script: ToppingScript): String {
        val matches = script.matchPatterns.javascriptArray()
        val includes = script.includePatterns.javascriptArray()
        val excludes = script.excludePatterns.javascriptArray()
        val grants = script.grants.joinToString(",") { grant -> "\"${grant.name}\"" }
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
              const grants = new Set([$grants]);
              const GM = Object.create(null);
              if (grants.has('AddStyle')) {
                const addStyle = css => {
                  const style = document.createElement('style');
                  style.textContent = String(css);
                  (document.head || document.documentElement).appendChild(style);
                  return style;
                };
                Object.defineProperty(globalThis, 'GM_addStyle', { value: addStyle });
                Object.defineProperty(GM, 'addStyle', { value: addStyle });
              }
              if (grants.has('Info')) {
                const info = Object.freeze({ script: Object.freeze({
                  id: "${script.id.javascriptEscaped()}",
                  name: "${script.name.javascriptEscaped()}"
                }) });
                Object.defineProperty(globalThis, 'GM_info', { value: info });
                Object.defineProperty(GM, 'info', { value: info });
              }
              Object.defineProperty(globalThis, 'GM', { value: Object.freeze(GM) });
              ${script.source}
            })();
        """.trimIndent()
    }

    private fun List<String>.javascriptArray(): String = joinToString(",") { value ->
        "\"${value.javascriptEscaped()}\""
    }

    private fun metadataLines(source: String): List<String>? {
        val lines = source.lineSequence().map { line -> line.removeSuffix("\r") }.toList()
        val start = lines.indexOfFirst { it.trim() == "// ==UserScript==" }
        if (start < 0) return null
        val end = lines.indexOfFirst { index, line ->
            index > start && line.trim() == "// ==/UserScript=="
        }
        if (end < 0) return null
        return lines.subList(start + 1, end).map { line -> line.trim().removePrefix("//").trim() }
    }

    private fun isWebMatchPattern(value: String): Boolean {
        if (value == "<all_urls>") return true
        val schemeEnd = value.indexOf("://")
        if (schemeEnd < 0) return false
        val scheme = value.substring(0, schemeEnd).lowercase()
        if (scheme !in setOf("http", "https", "*")) return false
        val pathStart = value.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return false
        val host = value.substring(schemeEnd + 3, pathStart)
        if (host.isEmpty() || '@' in host || value.length > 2_048) return false
        return host == "*" || !host.removePrefix("*.").contains('*')
    }

    private fun isWebGlob(value: String): Boolean = value.length in 1..2_048 &&
        value.none(Char::isISOControl) &&
        (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("*://"))

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
}

private inline fun List<String>.indexOfFirst(predicate: (Int, String) -> Boolean): Int {
    forEachIndexed { index, value -> if (predicate(index, value)) return index }
    return -1
}
