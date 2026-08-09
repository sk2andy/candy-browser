package dev.sk2andy.materialbrowser.blocking

import com.google.common.net.InternetDomainName
import java.util.Base64
import java.util.Locale

internal data class ScopedCosmeticRule(
    val hostPattern: String,
    val selector: String,
    val excludedHostPatterns: List<String> = emptyList(),
)

internal class EasyListCosmeticRules private constructor(
    val hidingRules: List<ScopedCosmeticRule>,
    val exceptionRules: List<ScopedCosmeticRule>,
) {
    private val exactHides = hidingRules.filterNot { '*' in it.hostPattern }
        .groupBy(ScopedCosmeticRule::hostPattern)
    private val wildcardHides = hidingRules.filter { '*' in it.hostPattern }
    private val exactExceptions = exceptionRules.filterNot { '*' in it.hostPattern }
        .groupBy(ScopedCosmeticRule::hostPattern)
    private val wildcardExceptions = exceptionRules.filter { '*' in it.hostPattern }

    val size: Int
        get() = hidingRules.size + exceptionRules.size

    fun selectors(pageUrl: String?): List<String> {
        val host = CandyHostCanonicalizer.webHost(pageUrl) ?: return emptyList()
        if (sensitiveGoogleHostPatterns.any { pattern ->
                CosmeticHostPattern.matches(host, pattern)
            }
        ) return emptyList()
        val allowed = matchingRules(host, exactExceptions, wildcardExceptions)
            .mapTo(HashSet(), ScopedCosmeticRule::selector)
        return matchingRules(host, exactHides, wildcardHides).asSequence()
            .filter { rule ->
                rule.excludedHostPatterns.none { pattern ->
                    CosmeticHostPattern.matches(host, pattern)
                }
            }
            .map(ScopedCosmeticRule::selector)
            .filterNot(allowed::contains)
            .distinct()
            .sorted()
            .toList()
    }

    private fun matchingRules(
        host: String,
        exact: Map<String, List<ScopedCosmeticRule>>,
        wildcard: List<ScopedCosmeticRule>,
    ): List<ScopedCosmeticRule> = buildList {
        var candidate = host
        while (true) {
            exact[candidate]?.let(::addAll)
            val dot = candidate.indexOf('.')
            if (dot < 0) break
            candidate = candidate.substring(dot + 1)
        }
        wildcard.filterTo(this) { rule ->
            CosmeticHostPattern.matches(host, rule.hostPattern)
        }
    }

    companion object {
        const val HEADER = "candy-easylist-cosmetic:1"
        const val UASSETS_HEADER = "candy-uassets-cosmetic:1"
        private const val MAX_BYTES = 16 * 1_024 * 1_024
        private const val MAX_LINES = 200_000
        private val sensitiveGoogleHostPatterns = listOf(
            "accounts.google.*",
            "mail.google.*",
            "maps.google.*",
        )

        fun parse(
            text: String,
            expectedHeader: String = HEADER,
        ): EasyListCosmeticRules {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Cosmetic asset too large" }
            val lines = text.lineSequence().toList()
            require(lines.size <= MAX_LINES) { "Too many cosmetic rules" }
            require(lines.firstOrNull()?.trimStart('\uFEFF') == expectedHeader) {
                "Invalid cosmetic asset header"
            }
            val declaredHides = declaredCount(lines, HIDE_COUNT_PREFIX)
            val declaredExceptions = declaredCount(lines, EXCEPTION_COUNT_PREFIX)
            val hidingRules = ArrayList<ScopedCosmeticRule>()
            val exceptionRules = ArrayList<ScopedCosmeticRule>()
            lines.drop(1).forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
                val fields = line.split('\t')
                require(fields.size == 4) { "Invalid cosmetic rule at line ${index + 2}" }
                val hostPattern = CosmeticHostPattern.canonicalize(fields[1])
                    ?: error("Invalid cosmetic host at line ${index + 2}")
                val exclusions = if (fields[2] == "-") {
                    emptyList()
                } else {
                    fields[2].split(',').map { value ->
                        CosmeticHostPattern.canonicalize(value)
                            ?: error("Invalid cosmetic exclusion at line ${index + 2}")
                    }
                }
                val selector = runCatching {
                    String(Base64.getUrlDecoder().decode(fields[3]), Charsets.UTF_8)
                }.getOrElse { error("Invalid cosmetic selector encoding at line ${index + 2}") }
                require(CandyRuleValidator.isSafeSelector(selector)) {
                    "Unsafe cosmetic selector at line ${index + 2}"
                }
                val rule = ScopedCosmeticRule(hostPattern, selector, exclusions)
                when (fields[0]) {
                    "H" -> hidingRules += rule
                    "A" -> {
                        require(exclusions.isEmpty()) {
                            "Cosmetic exception has exclusions at line ${index + 2}"
                        }
                        exceptionRules += rule
                    }
                    else -> error("Invalid cosmetic action at line ${index + 2}")
                }
            }
            require(hidingRules.distinct().size == hidingRules.size) { "Duplicate cosmetic hide" }
            require(exceptionRules.distinct().size == exceptionRules.size) {
                "Duplicate cosmetic exception"
            }
            require(hidingRules.size == declaredHides) { "Cosmetic hide count mismatch" }
            require(exceptionRules.size == declaredExceptions) {
                "Cosmetic exception count mismatch"
            }
            return EasyListCosmeticRules(hidingRules, exceptionRules)
        }

        fun merge(vararg sources: EasyListCosmeticRules): EasyListCosmeticRules =
            EasyListCosmeticRules(
                hidingRules = sources.flatMap(EasyListCosmeticRules::hidingRules).distinct(),
                exceptionRules = sources.flatMap(EasyListCosmeticRules::exceptionRules).distinct(),
            )

        private fun declaredCount(lines: List<String>, prefix: String): Int = lines.asSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.toIntOrNull()
            ?: error("Missing cosmetic asset count: $prefix")

        private const val HIDE_COUNT_PREFIX = "# Hide rules:"
        private const val EXCEPTION_COUNT_PREFIX = "# Exception rules:"
    }
}

internal object CosmeticHostPattern {
    private val safeLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun canonicalize(value: String): String? {
        val candidate = value.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (!candidate.endsWith(".*")) return CandyHostCanonicalizer.canonicalHost(candidate)
        val labels = candidate.removeSuffix(".*").split('.')
        return candidate.takeIf {
            labels.isNotEmpty() && labels.all(safeLabel::matches)
        }
    }

    fun matches(host: String, pattern: String): Boolean {
        if (!pattern.endsWith(".*")) return CandyHostCanonicalizer.matches(host, pattern)
        val prefix = pattern.removeSuffix("*")
        val suffix = when {
            host.startsWith(prefix) -> host.removePrefix(prefix)
            host.contains(".$prefix") -> host.substringAfter(".$prefix")
            else -> return false
        }
        return runCatching { InternetDomainName.from(suffix).isRegistrySuffix }
            .getOrDefault(false)
    }
}
