package dev.sk2andy.materialbrowser.blocking

import com.google.common.net.InternetDomainName
import java.util.Base64
import java.util.Locale

internal data class ScopedCosmeticRule(
    val hostPattern: String,
    val selector: String,
    val excludedHostPatterns: List<String> = emptyList(),
)

internal data class GenericCosmeticPolicy(
    val disabled: Boolean,
    val deniedSelectors: List<String> = emptyList(),
)

internal class EasyListCosmeticRules private constructor(
    val hidingRules: List<ScopedCosmeticRule>,
    val exceptionRules: List<ScopedCosmeticRule>,
    val genericHideExceptions: List<ScopedCosmeticRule>,
) {
    private val genericHides = hidingRules.filter { it.hostPattern == "*" }
    private val genericSelectorSet = genericHides.mapTo(HashSet(), ScopedCosmeticRule::selector)
    private val conditionalGenericHides = genericHides.filter {
        it.excludedHostPatterns.isNotEmpty()
    }.groupBy(ScopedCosmeticRule::selector)
    private val genericExceptions = exceptionRules.filter { it.hostPattern == "*" }
    private val exactHides = hidingRules.filter { '*' !in it.hostPattern }
        .groupBy(ScopedCosmeticRule::hostPattern)
    private val wildcardHides = hidingRules.filter {
        it.hostPattern != "*" && '*' in it.hostPattern
    }
    private val exactExceptions = exceptionRules.filter { '*' !in it.hostPattern }
        .groupBy(ScopedCosmeticRule::hostPattern)
    private val wildcardExceptions = exceptionRules.filter {
        it.hostPattern != "*" && '*' in it.hostPattern
    }
    private val exactGenericHideExceptions = genericHideExceptions
        .filter { '*' !in it.hostPattern }
        .groupBy(ScopedCosmeticRule::hostPattern)
    private val wildcardGenericHideExceptions = genericHideExceptions
        .filter { '*' in it.hostPattern }

    val size: Int
        get() = hidingRules.size + exceptionRules.size + genericHideExceptions.size

    fun selectors(pageUrl: String?): List<String> {
        val host = CandyHostCanonicalizer.webHost(pageUrl) ?: return emptyList()
        if (isSensitiveHost(host)) return emptyList()
        val allowed = allowedSelectors(host)
        val scoped = matchingRules(host, exactHides, wildcardHides)
        val generic = if (isGenericHidingDisabled(host)) {
            emptyList()
        } else {
            genericHides.filter { rule ->
                rule.excludedHostPatterns.none { pattern ->
                    CosmeticHostPattern.matches(host, pattern)
                }
            }
        }
        return (scoped + generic).asSequence()
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

    fun scopedSelectors(pageUrl: String?): List<String> {
        val host = CandyHostCanonicalizer.webHost(pageUrl) ?: return emptyList()
        if (isSensitiveHost(host)) return emptyList()
        val allowed = allowedSelectors(host)
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

    fun genericSelectors(): List<String> = genericSelectorSet.sorted()

    fun genericPolicy(pageUrl: String?): GenericCosmeticPolicy {
        val host = CandyHostCanonicalizer.webHost(pageUrl)
            ?: return GenericCosmeticPolicy(disabled = true)
        return genericPolicyForCanonicalHost(host)
    }

    fun genericPolicyForHost(host: String?): GenericCosmeticPolicy {
        val canonicalHost = CandyHostCanonicalizer.canonicalHost(host)
            ?: return GenericCosmeticPolicy(disabled = true)
        return genericPolicyForCanonicalHost(canonicalHost)
    }

    private fun genericPolicyForCanonicalHost(host: String): GenericCosmeticPolicy {
        if (isSensitiveHost(host) || isGenericHidingDisabled(host)) {
            return GenericCosmeticPolicy(disabled = true)
        }
        val denied = allowedSelectors(host).asSequence()
            .filter(genericSelectorSet::contains)
            .toMutableSet()
        conditionalGenericHides.forEach { (selector, rules) ->
            if (rules.none { rule ->
                    rule.excludedHostPatterns.none { pattern ->
                        CosmeticHostPattern.matches(host, pattern)
                    }
                }
            ) {
                denied += selector
            }
        }
        return GenericCosmeticPolicy(
            disabled = false,
            deniedSelectors = denied.sorted(),
        )
    }

    private fun allowedSelectors(host: String): Set<String> =
        (genericExceptions + matchingRules(host, exactExceptions, wildcardExceptions))
            .mapTo(HashSet(), ScopedCosmeticRule::selector)

    private fun isGenericHidingDisabled(host: String): Boolean = matchingRules(
        host,
        exactGenericHideExceptions,
        wildcardGenericHideExceptions,
    ).any { rule ->
        rule.excludedHostPatterns.none { pattern ->
            CosmeticHostPattern.matches(host, pattern)
        }
    }

    private fun isSensitiveHost(host: String): Boolean = sensitiveGoogleHostPatterns.any { pattern ->
        CosmeticHostPattern.matches(host, pattern)
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
        const val EASYLIST_V2_HEADER = "candy-easylist-cosmetic:2"
        const val UASSETS_HEADER = "candy-uassets-cosmetic:2"
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
            val declaredGenericHideExceptions = declaredCount(
                lines,
                GENERIC_HIDE_EXCEPTION_COUNT_PREFIX,
                default = 0,
            )
            val hidingRules = ArrayList<ScopedCosmeticRule>()
            val exceptionRules = ArrayList<ScopedCosmeticRule>()
            val genericHideExceptions = ArrayList<ScopedCosmeticRule>()
            lines.drop(1).forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
                val fields = line.split('\t')
                require(fields.size == 4) { "Invalid cosmetic rule at line ${index + 2}" }
                if (expectedHeader == HEADER) {
                    require(fields[0] != "D" && fields[1] != "*") {
                        "Generic cosmetic rule requires the uAssets v2 header at line ${index + 2}"
                    }
                }
                val hostPattern = if (fields[1] == "*") {
                    "*"
                } else {
                    CosmeticHostPattern.canonicalize(fields[1])
                        ?: error("Invalid cosmetic host at line ${index + 2}")
                }
                val exclusions = if (fields[2] == "-") {
                    emptyList()
                } else {
                    fields[2].split(',').map { value ->
                        CosmeticHostPattern.canonicalize(value)
                            ?: error("Invalid cosmetic exclusion at line ${index + 2}")
                    }
                }
                if (fields[0] == "D") {
                    require(hostPattern != "*" && fields[3] == "-") {
                        "Invalid generic-hide exception at line ${index + 2}"
                    }
                    genericHideExceptions += ScopedCosmeticRule(
                        hostPattern = hostPattern,
                        selector = "",
                        excludedHostPatterns = exclusions,
                    )
                    return@forEachIndexed
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
            require(genericHideExceptions.distinct().size ==
                genericHideExceptions.size
            ) { "Duplicate generic-hide exception" }
            require(genericHideExceptions.size == declaredGenericHideExceptions) {
                "Generic-hide exception count mismatch"
            }
            return EasyListCosmeticRules(
                hidingRules,
                exceptionRules,
                genericHideExceptions,
            )
        }

        fun merge(vararg sources: EasyListCosmeticRules): EasyListCosmeticRules =
            EasyListCosmeticRules(
                hidingRules = sources.flatMap(EasyListCosmeticRules::hidingRules).distinct(),
                exceptionRules = sources.flatMap(EasyListCosmeticRules::exceptionRules).distinct(),
                genericHideExceptions = sources
                    .flatMap(EasyListCosmeticRules::genericHideExceptions)
                    .distinct(),
            )

        private fun declaredCount(
            lines: List<String>,
            prefix: String,
            default: Int? = null,
        ): Int = lines.asSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.toIntOrNull()
            ?: default
            ?: error("Missing cosmetic asset count: $prefix")

        private const val HIDE_COUNT_PREFIX = "# Hide rules:"
        private const val EXCEPTION_COUNT_PREFIX = "# Exception rules:"
        private const val GENERIC_HIDE_EXCEPTION_COUNT_PREFIX = "# Generic hide exceptions:"
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
