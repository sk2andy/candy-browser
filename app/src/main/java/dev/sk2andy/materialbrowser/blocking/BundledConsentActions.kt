package dev.sk2andy.materialbrowser.blocking

import java.util.Base64

internal data class BundledConsentAction(
    val id: String,
    val frameHost: String,
    val selector: String,
)

internal object BundledConsentActions {
    const val HEADER = "candy-consent-actions:1"
    private const val MAX_BYTES = 64 * 1_024
    private const val MAX_RULES = 128

    fun parse(text: String): List<BundledConsentAction> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Bundled consent actions exceed size limit"
        }
        val lines = text.lineSequence().map(String::trim).toList()
        val firstContent = lines.indexOfFirst { line ->
            line.isNotEmpty() && !line.startsWith('#')
        }
        require(firstContent >= 0 && lines[firstContent] == HEADER) {
            "Missing bundled consent action header"
        }
        val actions = lines.drop(firstContent + 1).mapIndexedNotNull { offset, line ->
            if (line.isEmpty() || line.startsWith('#')) return@mapIndexedNotNull null
            parseLine(line, firstContent + offset + 2)
        }
        require(actions.size <= MAX_RULES) { "Too many bundled consent actions" }
        require(actions.map(BundledConsentAction::id).distinct().size == actions.size) {
            "Duplicate bundled consent action id"
        }
        return actions
    }

    fun parseOrEmpty(text: String): List<BundledConsentAction> =
        runCatching { parse(text) }.getOrDefault(emptyList())

    private fun parseLine(line: String, lineNumber: Int): BundledConsentAction {
        val fields = line.split('\t')
        require(fields.size == 4 && fields[0] == "reject") {
            "Invalid bundled consent action at line $lineNumber"
        }
        val id = fields[3]
        val selector = runCatching {
            String(Base64.getUrlDecoder().decode(fields[2]), Charsets.UTF_8)
        }.getOrNull()
        val validated = CandyRuleValidator.validate(
            CandyRule(
                id = id,
                action = CandyRuleAction.Cosmetic,
                kind = CandyRuleKind.CosmeticCss,
                firstPartyHost = fields[1],
                cosmeticSelector = selector,
            ),
        ) as? CandyRuleValidation.Valid
        require(validated != null) {
            "Invalid bundled consent action at line $lineNumber"
        }
        require(isSpecificActionSelector(requireNotNull(validated.rule.cosmeticSelector))) {
            "Bundled consent action selector is too broad at line $lineNumber"
        }
        return BundledConsentAction(
            id = validated.rule.id,
            frameHost = requireNotNull(validated.rule.firstPartyHost),
            selector = requireNotNull(validated.rule.cosmeticSelector),
        )
    }

    private fun isSpecificActionSelector(selector: String): Boolean =
        selector.matches(Regex("^#[A-Za-z][A-Za-z0-9_-]*$")) ||
            selector.matches(
                Regex("^\\[data-testid=(?:\"[A-Za-z0-9_-]+\"|'[A-Za-z0-9_-]+')\\]$"),
            )
}
