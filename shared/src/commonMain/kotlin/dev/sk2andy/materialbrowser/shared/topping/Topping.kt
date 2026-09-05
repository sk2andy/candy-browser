package dev.sk2andy.materialbrowser.shared.topping

enum class ToppingRunAt {
    DocumentStart,
    DocumentEnd,
}

data class ToppingScript(
    val id: String,
    val name: String,
    val source: String,
    val matchPatterns: List<String>,
    val runAt: ToppingRunAt,
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
    private const val MAX_SOURCE_BYTES = 512 * 1_024
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_MATCH_PATTERNS = 32
    private val validId = Regex("[A-Za-z0-9._-]{1,80}")

    fun parse(id: String, source: String): ToppingParseResult {
        if (!validId.matches(id)) return ToppingParseResult.Rejected("invalid_id")
        if (source.isEmpty() || source.encodeToByteArray().size > MAX_SOURCE_BYTES) {
            return ToppingParseResult.Rejected("invalid_source_size")
        }
        val metadata = metadataLines(source) ?: return ToppingParseResult.Rejected("missing_metadata")
        val name = metadata.firstValue("@name")
            ?.takeIf { it.length in 1..MAX_NAME_LENGTH }
            ?: return ToppingParseResult.Rejected("invalid_name")
        val matches = metadata.values("@match")
        if (matches.isEmpty() || matches.size > MAX_MATCH_PATTERNS || matches.any { !isWebMatchPattern(it) }) {
            return ToppingParseResult.Rejected("invalid_match")
        }
        val runAt = when (metadata.firstValue("@run-at")) {
            null, "document-end" -> ToppingRunAt.DocumentEnd
            "document-start" -> ToppingRunAt.DocumentStart
            else -> return ToppingParseResult.Rejected("invalid_run_at")
        }
        return ToppingParseResult.Accepted(
            ToppingScript(
                id = id,
                name = name,
                source = source,
                matchPatterns = matches,
                runAt = runAt,
            ),
        )
    }

    fun injectionPlan(script: ToppingScript): ToppingInjectionPlan = ToppingInjectionPlan(
        id = script.id,
        source = guardedSource(script),
        runAt = script.runAt,
        forMainFrameOnly = true,
        contentWorldName = "candy.topping.${script.id}",
    )

    private fun guardedSource(script: ToppingScript): String {
        val patterns = script.matchPatterns.joinToString(",") { pattern ->
            "\"${pattern.javascriptEscaped()}\""
        }
        return """
            (() => {
              const patterns = [$patterns];
              const escapeRegex = value => value.replace(/[.+?^${'$'}()|[\]\\]/g, '\\${'$'}&');
              const matches = patterns.some(pattern => {
                const expression = '^' + escapeRegex(pattern).replace(/\\\*/g, '.*') + '${'$'}';
                return new RegExp(expression).test(location.href);
              });
              if (!matches || window.top !== window) return;
              ${script.source}
            })();
        """.trimIndent()
    }

    private fun metadataLines(source: String): List<String>? {
        val lines = source.lineSequence().toList()
        val start = lines.indexOfFirst { it.trim() == "// ==UserScript==" }
        if (start < 0) return null
        val end = lines.indexOfFirst { index, line ->
            index > start && line.trim() == "// ==/UserScript=="
        }
        if (end < 0) return null
        return lines.subList(start + 1, end).map { line -> line.trim().removePrefix("//").trim() }
    }

    private fun List<String>.firstValue(key: String): String? = values(key).firstOrNull()

    private fun List<String>.values(key: String): List<String> = mapNotNull { line ->
        line.takeIf { it.startsWith(key) }
            ?.removePrefix(key)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun isWebMatchPattern(value: String): Boolean {
        val schemeEnd = value.indexOf("://")
        if (schemeEnd < 0) return false
        val scheme = value.substring(0, schemeEnd)
        if (scheme !in setOf("http", "https", "*")) return false
        val pathStart = value.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return false
        val host = value.substring(schemeEnd + 3, pathStart)
        return host.isNotEmpty() && '@' !in host && value.length <= 2_048
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
}

private inline fun List<String>.indexOfFirst(predicate: (Int, String) -> Boolean): Int {
    forEachIndexed { index, value -> if (predicate(index, value)) return index }
    return -1
}
