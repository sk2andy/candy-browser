package dev.sk2andy.materialbrowser.blocking

import java.util.Locale

/**
 * Safe, intentionally small importer for ABP/EasyList/uBlock static-filter syntax.
 * Unsupported semantics are reported and skipped; they are never approximated.
 */
object CandyRuleImport {
    fun parse(text: String): CandyRulePreview {
        val contentLines = text.lineSequence()
            .map { it.trim().trimStart('\uFEFF') }
            .filter(String::isNotEmpty)
            .toList()
        return when {
            contentLines.isEmpty() -> CandyRuleFormat.parse(text)
            contentLines.any { it == CandyRuleFormat.HEADER } -> CandyRuleFormat.parse(text)
            contentLines.any { it.startsWith("rule\t") } -> CandyRuleFormat.parse(text)
            else -> AdblockRuleFormat.parse(text)
        }
    }
}

object CandyImportScope {
    fun apply(preview: CandyRulePreview, profileId: String?): CandyRulePreview = preview.copy(
        rules = preview.rules.map { rule -> rule.copy(profileId = profileId) },
    )

    fun isAllowed(profileId: String?, availableProfileIds: Collection<String>): Boolean =
        profileId == null || profileId in availableProfileIds
}

object AdblockRuleFormat {
    const val MAX_IMPORT_BYTES = 5 * 1_024 * 1_024
    const val MAX_IMPORT_LINES = 100_000
    const val MAX_LINE_BYTES = 8 * 1_024
    private const val MAX_SKIPPED_DETAILS = 20
    private const val GROUP = "Imported"
    private val whitespace = Regex("\\s+")

    fun parse(text: String): CandyRulePreview {
        if ('\u0000' in text) return failure("invalid-input", truncated = false)
        if (text.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) {
            return failure("size-limit", truncated = true)
        }
        val lines = text.lineSequence().toList()
        if (lines.size > MAX_IMPORT_LINES) return failure("line-limit", truncated = true)
        if (lines.any { it.toByteArray(Charsets.UTF_8).size > MAX_LINE_BYTES }) {
            return failure("line-limit", truncated = true)
        }

        val disabledByBadfilter = unconditionalLines(lines).mapNotNull(::badfilterTarget).toSet()
        val rules = ArrayList<CandyRule>()
        val semanticKeys = HashSet<String>()
        val skipped = ArrayList<CandyRuleLineError>()
        var skippedCount = 0
        var cosmeticCount = 0
        var limitExceeded = false
        var conditionalDepth = 0

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim().trimStart('\uFEFF')
            if (line.startsWith("!#if")) {
                conditionalDepth++
                recordSkipped(skipped, index + 1, "unsupported-adblock")
                skippedCount++
                return@forEachIndexed
            }
            if (line.startsWith("!#endif")) {
                conditionalDepth = (conditionalDepth - 1).coerceAtLeast(0)
                return@forEachIndexed
            }
            if (conditionalDepth > 0) {
                if (line.isNotEmpty() && !line.startsWith("!#else")) {
                    recordSkipped(skipped, index + 1, "unsupported-adblock")
                    skippedCount++
                }
                return@forEachIndexed
            }
            if (badfilterTarget(line) != null || canonicalNetworkLine(line) in disabledByBadfilter) {
                return@forEachIndexed
            }
            when (val result = parseLine(line, index + 1)) {
                LineResult.Ignored -> Unit
                is LineResult.Skipped -> {
                    skippedCount++
                    recordSkipped(skipped, index + 1, result.reason)
                }
                is LineResult.Rules -> {
                    val unique = result.rules.filter { semanticKeys.add(semanticKey(it)) }
                    if (unique.isEmpty()) return@forEachIndexed
                    val newCosmeticCount = unique.count { it.kind == CandyRuleKind.CosmeticCss }
                    if (rules.size + unique.size > CandyRuleValidator.MAX_RULES ||
                        cosmeticCount + newCosmeticCount > CandyRuleValidator.MAX_COSMETIC_RULES
                    ) {
                        limitExceeded = true
                        unique.forEach { semanticKeys.remove(semanticKey(it)) }
                        skippedCount++
                        recordSkipped(skipped, index + 1, "rule-limit")
                    } else {
                        rules += unique
                        cosmeticCount += newCosmeticCount
                    }
                }
            }
        }

        // ABP exceptions always win over every matching block. Candy normally favors the most
        // specific host and pair rules, so normalize global exceptions into equivalent Candy
        // decisions instead of silently changing list semantics.
        val globalAllows = rules.filter {
            it.action == CandyRuleAction.Allow && it.kind == CandyRuleKind.RequestHost
        }
        rules.removeAll { block ->
            block.action == CandyRuleAction.Block && globalAllows.any { allow ->
                CandyHostCanonicalizer.matches(
                    block.requestHost.orEmpty(),
                    allow.requestHost.orEmpty(),
                )
            }
        }
        val pairAllows = ArrayList<CandyRule>()
        pairBlocks@ for (pairBlock in rules.filter {
            it.action == CandyRuleAction.Block && it.kind == CandyRuleKind.HostPair
        }) {
            for (allow in globalAllows) {
                val allowHost = allow.requestHost.orEmpty()
                val blockHost = pairBlock.requestHost.orEmpty()
                if (!CandyHostCanonicalizer.matches(allowHost, blockHost)) continue
                val candidate = newRule(
                    lineNumber = 0,
                    ordinal = pairAllows.size,
                    action = CandyRuleAction.Allow,
                    kind = CandyRuleKind.HostPair,
                    requestHost = allowHost,
                    firstPartyHost = pairBlock.firstPartyHost,
                )
                val key = semanticKey(candidate)
                if (key in semanticKeys) continue
                if (rules.size + pairAllows.size >= CandyRuleValidator.MAX_RULES) {
                    limitExceeded = true
                    break@pairBlocks
                }
                semanticKeys += key
                pairAllows += candidate
            }
        }
        if (!limitExceeded) {
            rules += pairAllows
        }

        val errors = buildList {
            if (rules.isEmpty()) add(CandyRuleLineError(0, "no-supported-rules"))
            if (limitExceeded) add(CandyRuleLineError(0, "rule-limit"))
        }
        return CandyRulePreview(
            rules = rules,
            errors = errors,
            format = CandyImportFormat.AdblockCompatible,
            skipped = skipped,
            skippedCount = skippedCount,
        )
    }

    private fun parseLine(line: String, lineNumber: Int): LineResult {
        if (line.isEmpty() || isMetadataHeader(line)) {
            return LineResult.Ignored
        }
        if (line.startsWith("!#include") || line.startsWith("!#")) {
            return LineResult.Skipped("unsupported-adblock")
        }
        if (line.startsWith('!')) return LineResult.Ignored
        if (line.startsWith('#')) return LineResult.Skipped("unsupported-adblock")
        return if (containsCosmeticMarker(line)) {
            parseCosmetic(line, lineNumber)
        } else {
            parseNetwork(line, lineNumber)
        }
    }

    private fun parseNetwork(line: String, lineNumber: Int): LineResult {
        parseHostsLine(line)?.let { requestHost ->
            return validatedRules(
                listOf(
                    newRule(
                        lineNumber,
                        0,
                        CandyRuleAction.Block,
                        CandyRuleKind.RequestHost,
                        requestHost,
                    ),
                ),
            )
        }
        val action = if (line.startsWith("@@")) CandyRuleAction.Allow else CandyRuleAction.Block
        val body = if (action == CandyRuleAction.Allow) line.removePrefix("@@") else line
        val optionIndex = body.indexOf('$')
        val pattern = if (optionIndex >= 0) body.substring(0, optionIndex) else body
        val options = if (optionIndex >= 0) body.substring(optionIndex + 1) else ""
        if (!pattern.startsWith("||") || !pattern.endsWith('^')) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val requestHost = CandyHostCanonicalizer.canonicalHost(pattern.substring(2, pattern.length - 1))
            ?: return LineResult.Skipped("unsupported-adblock")
        if (CandyPublicSuffixRules.isPublicSuffix(requestHost)) {
            return LineResult.Skipped("unsupported-adblock")
        }

        if (options.isEmpty()) {
            return validatedRules(
                listOf(newRule(lineNumber, 0, action, CandyRuleKind.RequestHost, requestHost)),
            )
        }
        val optionParts = options.split(',')
        val domainParts = optionParts.filter {
            it.startsWith("domain=") || it.startsWith("from=")
        }
        val modifiers = optionParts - domainParts.toSet()
        if (domainParts.size != 1 || modifiers.any { it !in setOf("third-party", "3p") }) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val domains = domainParts.single()
            .removePrefix("domain=")
            .removePrefix("from=")
            .split('|')
        if (domains.isEmpty() || domains.any { it.isBlank() || it.startsWith('~') || '*' in it }) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val firstPartyHosts = domains.map(CandyHostCanonicalizer::canonicalHost)
        if (firstPartyHosts.any { it == null }) return LineResult.Skipped("unsupported-adblock")
        return validatedRules(
            firstPartyHosts.filterNotNull().mapIndexed { index, firstPartyHost ->
                newRule(
                    lineNumber = lineNumber,
                    ordinal = index,
                    action = action,
                    kind = CandyRuleKind.HostPair,
                    requestHost = requestHost,
                    firstPartyHost = firstPartyHost,
                )
            },
        )
    }

    private fun parseCosmetic(line: String, lineNumber: Int): LineResult {
        if (listOf("#@#", "#?#", "#$#", "#%#").any(line::contains)) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val marker = line.indexOf("##")
        if (marker <= 0) return LineResult.Skipped("unsupported-adblock")
        val domainPart = line.substring(0, marker)
        val selector = line.substring(marker + 2).trim()
        if (selector.isEmpty() || hasExtendedCosmeticSyntax(selector)) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val domains = domainPart.split(',')
        if (domains.any { it.isBlank() || it.startsWith('~') || '*' in it || '/' in it }) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val origins = domains.map(CandyHostCanonicalizer::canonicalHost)
        if (origins.any { it == null }) return LineResult.Skipped("unsupported-adblock")
        return validatedRules(
            origins.filterNotNull().mapIndexed { index, origin ->
                CandyRule(
                    id = stableId(lineNumber, index, "css\u0000$origin\u0000$selector"),
                    action = CandyRuleAction.Cosmetic,
                    kind = CandyRuleKind.CosmeticCss,
                    firstPartyHost = origin,
                    cosmeticSelector = selector,
                    group = GROUP,
                    origin = CandyRuleOrigin.Import,
                )
            },
        )
    }

    private fun validatedRules(candidates: List<CandyRule>): LineResult {
        val validated = candidates.map { candidate ->
            (CandyRuleValidator.validate(candidate) as? CandyRuleValidation.Valid)?.rule
                ?: return LineResult.Skipped("unsupported-adblock")
        }
        return LineResult.Rules(validated)
    }

    private fun newRule(
        lineNumber: Int,
        ordinal: Int,
        action: CandyRuleAction,
        kind: CandyRuleKind,
        requestHost: String,
        firstPartyHost: String? = null,
    ): CandyRule {
        val key = listOf(action.name, kind.name, requestHost, firstPartyHost.orEmpty())
            .joinToString("\u0000")
        return CandyRule(
            id = stableId(lineNumber, ordinal, key),
            action = action,
            kind = kind,
            requestHost = requestHost,
            firstPartyHost = firstPartyHost,
            group = GROUP,
            origin = CandyRuleOrigin.Import,
        )
    }

    private fun stableId(lineNumber: Int, ordinal: Int, value: String): String =
        "adblock-$lineNumber-$ordinal-${value.hashCode().toUInt().toString(16)}"

    private fun semanticKey(rule: CandyRule): String = listOf(
        rule.action.name,
        rule.kind.name,
        rule.requestHost.orEmpty(),
        rule.firstPartyHost.orEmpty(),
        rule.cosmeticSelector.orEmpty(),
    ).joinToString("\u0000")

    private fun isMetadataHeader(line: String): Boolean =
        line.startsWith('[') && line.endsWith(']')

    private fun parseHostsLine(line: String): String? {
        val fields = line.split(whitespace).filter(String::isNotEmpty)
        if (fields.size != 2 || fields[0] !in setOf("0.0.0.0", "127.0.0.1")) return null
        return CandyHostCanonicalizer.canonicalHost(fields[1])
    }

    private fun badfilterTarget(rawLine: String): String? {
        val line = rawLine.trim().trimStart('\uFEFF')
        val optionIndex = line.indexOf('$')
        if (optionIndex < 0) return null
        val options = line.substring(optionIndex + 1).split(',')
        if ("badfilter" !in options) return null
        val retained = options.filterNot { it == "badfilter" }
        return canonicalNetworkLine(
            line.substring(0, optionIndex) + if (retained.isEmpty()) {
                ""
            } else {
                retained.joinToString(",", prefix = "\$")
            },
        )
    }

    private fun unconditionalLines(lines: List<String>): Sequence<String> = sequence {
        var conditionalDepth = 0
        lines.forEach { rawLine ->
            val line = rawLine.trim().trimStart('\uFEFF')
            when {
                line.startsWith("!#if") -> conditionalDepth++
                line.startsWith("!#endif") -> {
                    conditionalDepth = (conditionalDepth - 1).coerceAtLeast(0)
                }
                conditionalDepth == 0 -> yield(line)
            }
        }
    }

    private fun canonicalNetworkLine(line: String): String {
        val optionIndex = line.indexOf('$')
        if (optionIndex < 0) return line
        return line.substring(0, optionIndex) + line.substring(optionIndex + 1)
            .split(',')
            .sorted()
            .joinToString(",", prefix = "\$")
    }

    private fun recordSkipped(
        skipped: MutableList<CandyRuleLineError>,
        line: Int,
        reason: String,
    ) {
        if (skipped.size < MAX_SKIPPED_DETAILS) skipped += CandyRuleLineError(line, reason)
    }

    private fun containsCosmeticMarker(line: String): Boolean =
        listOf("##", "#@#", "#?#", "#$#", "#%#").any(line::contains)

    private fun hasExtendedCosmeticSyntax(selector: String): Boolean {
        val lower = selector.lowercase(Locale.ROOT)
        return selector.startsWith('^') || listOf(
            "+js(", ":has-text(", ":matches-path(", ":upward(", ":remove(",
            ":style(", ":xpath(", ":-abp-",
        ).any(lower::contains)
    }

    private fun failure(reason: String, truncated: Boolean): CandyRulePreview = CandyRulePreview(
        rules = emptyList(),
        errors = listOf(CandyRuleLineError(0, reason)),
        truncated = truncated,
        format = CandyImportFormat.AdblockCompatible,
    )

    private sealed interface LineResult {
        data object Ignored : LineResult
        data class Rules(val rules: List<CandyRule>) : LineResult
        data class Skipped(val reason: String) : LineResult
    }
}
