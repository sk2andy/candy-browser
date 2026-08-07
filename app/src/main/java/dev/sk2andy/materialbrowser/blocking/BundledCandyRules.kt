package dev.sk2andy.materialbrowser.blocking

internal object BundledCandyRuleGroups {
    const val Ads = "Candy Ads"
    const val Cookies = "Candy Cookies"
}

internal data class BundledCandyRules private constructor(
    val rules: List<CandyRule>,
) {
    val matcher = CandyMatcherSnapshot.compile(rules)

    val cookieCosmeticRules: List<CandyRule>
        get() = rules.filter { rule ->
            rule.active && rule.action == CandyRuleAction.Cosmetic &&
                rule.kind == CandyRuleKind.CosmeticCss &&
                rule.group == BundledCandyRuleGroups.Cookies
        }

    val adCosmeticRules: List<CandyRule>
        get() = rules.filter { rule ->
            rule.active && rule.action == CandyRuleAction.Cosmetic &&
                rule.kind == CandyRuleKind.CosmeticCss &&
                rule.group == BundledCandyRuleGroups.Ads
        }

    fun adCosmeticSelectors(pageUrl: String?): List<String> {
        if (pageUrl == null) return emptyList()
        return matcher.cosmeticRules(pageUrl, profileId = "")
            .filter { rule -> rule.group == BundledCandyRuleGroups.Ads }
            .mapNotNull(CandyRule::cosmeticSelector)
    }

    companion object {
        val Empty = BundledCandyRules(emptyList())

        fun parse(text: String): BundledCandyRules {
            val preview = CandyRuleFormat.parse(text)
            require(preview.isApplicable) { "Invalid bundled Candy Rules" }
            require(preview.rules.all(::isSupportedBundledRule)) {
                "Unsupported bundled Candy Rule"
            }
            require(preview.rules.map(CandyRule::id).distinct().size == preview.rules.size) {
                "Duplicate bundled Candy Rule id"
            }
            return BundledCandyRules(preview.rules)
        }

        fun parseOrEmpty(text: String): BundledCandyRules =
            runCatching { parse(text) }.getOrDefault(Empty)

        private fun isSupportedBundledRule(rule: CandyRule): Boolean =
            rule.active && rule.profileId == null && when (rule.group) {
                BundledCandyRuleGroups.Ads -> true
                BundledCandyRuleGroups.Cookies ->
                    rule.action == CandyRuleAction.Cosmetic &&
                        rule.kind == CandyRuleKind.CosmeticCss
                else -> false
            }
    }
}
