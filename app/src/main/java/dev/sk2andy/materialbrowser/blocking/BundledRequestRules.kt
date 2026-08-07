package dev.sk2andy.materialbrowser.blocking

import java.net.URI
import java.util.Base64

internal enum class BundledRequestAction { Block, Allow }

internal data class BundledRequestRule(
    val id: String,
    val action: BundledRequestAction,
    val pageHost: String,
    val requestHost: String,
    val pathPrefix: String,
)

internal class BundledRequestRules private constructor(
    val rules: List<BundledRequestRule>,
) {
    fun decide(request: URI, pageHost: String?): BundledRequestAction? {
        val safePageHost = CandyHostCanonicalizer.canonicalHost(pageHost) ?: return null
        val requestHost = CandyHostCanonicalizer.canonicalHost(request.host) ?: return null
        val requestPath = request.rawPath.orEmpty().ifEmpty { "/" }
        return rules.asSequence()
            .filter { rule ->
                CandyHostCanonicalizer.matches(safePageHost, rule.pageHost) &&
                    CandyHostCanonicalizer.matches(requestHost, rule.requestHost) &&
                    pathMatches(requestPath, rule.pathPrefix)
            }
            .firstOrNull()
            ?.action
    }

    companion object {
        const val HEADER = "candy-request-rules:1"
        private const val MAX_BYTES = 128 * 1_024
        private const val MAX_RULES = 512
        private const val MAX_PATH_LENGTH = 1_024
        val Empty = BundledRequestRules(emptyList())
        private val PRECEDENCE = compareByDescending<BundledRequestRule> { it.pageHost.length }
            .thenByDescending { it.requestHost.length }
            .thenByDescending { it.pathPrefix.length }
            .thenByDescending { it.action == BundledRequestAction.Allow }
            .thenBy(BundledRequestRule::id)

        fun parse(text: String): BundledRequestRules {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                "Bundled request rules exceed size limit"
            }
            val lines = text.lineSequence().map(String::trim).toList()
            val firstContent = lines.indexOfFirst { line ->
                line.isNotEmpty() && !line.startsWith('#')
            }
            require(firstContent >= 0 && lines[firstContent] == HEADER) {
                "Missing bundled request rule header"
            }
            val rules = lines.drop(firstContent + 1).mapIndexedNotNull { offset, line ->
                if (line.isEmpty() || line.startsWith('#')) return@mapIndexedNotNull null
                parseLine(line, firstContent + offset + 2)
            }
            require(rules.size <= MAX_RULES) { "Too many bundled request rules" }
            require(rules.map(BundledRequestRule::id).distinct().size == rules.size) {
                "Duplicate bundled request rule id"
            }
            return BundledRequestRules(rules.sortedWith(PRECEDENCE))
        }

        fun parseOrEmpty(text: String): BundledRequestRules =
            runCatching { parse(text) }.getOrDefault(Empty)

        private fun parseLine(line: String, lineNumber: Int): BundledRequestRule {
            val fields = line.split('\t')
            require(fields.size == 5) { "Invalid bundled request rule at line $lineNumber" }
            val action = when (fields[0]) {
                "block" -> BundledRequestAction.Block
                "allow" -> BundledRequestAction.Allow
                else -> null
            }
            val pageHost = CandyHostCanonicalizer.canonicalHost(fields[1])
            val requestHost = CandyHostCanonicalizer.canonicalHost(fields[2])
            val pathPrefix = runCatching {
                String(Base64.getUrlDecoder().decode(fields[3]), Charsets.UTF_8)
            }.getOrNull()
            val id = fields[4]
            require(
                action != null && pageHost != null && requestHost != null &&
                    CandyPublicSuffixRules.registrableDomain(pageHost) != null &&
                    CandyPublicSuffixRules.registrableDomain(requestHost) != null &&
                    pathPrefix != null && pathPrefix.startsWith('/') && pathPrefix != "/" &&
                    pathPrefix.length <= MAX_PATH_LENGTH &&
                    pathPrefix.none(Char::isISOControl) && '?' !in pathPrefix && '#' !in pathPrefix &&
                    id.isNotEmpty() && id.length <= 128 && id.none(Char::isWhitespace),
            ) { "Invalid bundled request rule at line $lineNumber" }
            return BundledRequestRule(id, action, pageHost, requestHost, pathPrefix)
        }

        private fun pathMatches(path: String, prefix: String): Boolean =
            path == prefix || path.startsWith(
                if (prefix.endsWith('/')) prefix else "$prefix/",
            )
    }
}
