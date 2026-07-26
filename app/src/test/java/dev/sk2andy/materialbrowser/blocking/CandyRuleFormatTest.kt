package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyRuleFormatTest {
    @Test
    fun `export round trips supported rule types`() {
        val rules = listOf(
            CandyRule.new(CandyRuleAction.Block, CandyRuleKind.RequestHost, requestHost = "ads.example"),
            CandyRule.new(
                CandyRuleAction.Allow,
                CandyRuleKind.HostPair,
                requestHost = "cdn.example",
                firstPartyHost = "news.example",
                profileId = "work",
            ),
            CandyRule.new(
                CandyRuleAction.Cosmetic,
                CandyRuleKind.CosmeticCss,
                firstPartyHost = "news.example",
                cosmeticSelector = ".sponsor, #ad",
            ),
        )

        val preview = CandyRuleFormat.parse(CandyRuleFormat.export(rules))

        assertTrue(preview.errors.isEmpty())
        assertEquals(3, preview.rules.size)
        assertEquals(
            ".sponsor, #ad",
            preview.rules.single { it.kind == CandyRuleKind.CosmeticCss }.cosmeticSelector,
        )
    }

    @Test
    fun `malformed fuzz like lines fail closed without throwing`() {
        val values = listOf(
            "", "\u0000", "rule", "rule\tblock", "rule\tboom\thost\ta.example",
            "rule\tblock\tpair\t->", "rule\tcss\torigin\tco.uk\teA",
            "rule\tblock\thost\t127.0.0.1", "rule\tblock\thost\tbad..example",
        )

        values.forEach { value ->
            val preview = CandyRuleFormat.parse("${CandyRuleFormat.HEADER}\n$value")
            if (value.isNotEmpty()) assertFalse("input=$value", preview.isApplicable)
        }
    }

    @Test
    fun `oversized imports stop before parsing`() {
        val body = CandyRuleFormat.HEADER + "\n" + "x".repeat(CandyRuleFormat.MAX_IMPORT_BYTES)
        val preview = CandyRuleFormat.parse(body)

        assertTrue(preview.truncated)
        assertTrue(preview.rules.isEmpty())
    }

    @Test
    fun `imports beyond line limit fail without partial preview`() {
        val text = buildString {
            appendLine(CandyRuleFormat.HEADER)
            repeat(CandyRuleFormat.MAX_IMPORT_LINES) { appendLine() }
        }

        val preview = CandyRuleFormat.parse(text)

        assertTrue(preview.truncated)
        assertTrue(preview.rules.isEmpty())
    }

    @Test
    fun `imports beyond rule limit are rejected without becoming applicable`() {
        val text = buildString {
            appendLine(CandyRuleFormat.HEADER)
            repeat(CandyRuleValidator.MAX_RULES + 1) { index ->
                appendLine("rule\tblock\thost\tt$index.example")
            }
        }

        val preview = CandyRuleFormat.parse(text)

        assertFalse(preview.isApplicable)
        assertEquals(CandyRuleValidator.MAX_RULES, preview.rules.size)
        assertTrue(preview.errors.any { it.message == "rule-limit" })
    }

    @Test
    fun `normalization keeps one rule for duplicate ids`() {
        val first = CandyRule(
            id = "same",
            action = CandyRuleAction.Block,
            kind = CandyRuleKind.RequestHost,
            requestHost = "one.example",
        )
        val second = first.copy(requestHost = "two.example")

        assertEquals(listOf(first), CandyRuleValidator.normalizeAll(listOf(first, second)))
    }

    @Test
    fun `subscriptions reject css and non https sources`() {
        val css = CandyRuleFormat.export(
            listOf(
                CandyRule.new(
                    CandyRuleAction.Cosmetic,
                    CandyRuleKind.CosmeticCss,
                    firstPartyHost = "news.example",
                    cosmeticSelector = ".ad",
                ),
            ),
        )

        assertFalse(CandySubscriptionRules.validatePreview("https://lists.example/a", css).isApplicable)
        assertFalse(CandySubscriptionRules.validatePreview("http://lists.example/a", css).isApplicable)
    }

    @Test
    fun `subscription diff reports add remove and stable entries`() {
        val stable = CandyRule.new(
            CandyRuleAction.Block,
            CandyRuleKind.RequestHost,
            requestHost = "stable.example",
        )
        val removed = CandyRule.new(
            CandyRuleAction.Block,
            CandyRuleKind.RequestHost,
            requestHost = "old.example",
        )
        val added = CandyRule.new(
            CandyRuleAction.Allow,
            CandyRuleKind.RequestHost,
            requestHost = "new.example",
        )

        val diff = CandySubscriptionRules.diff(listOf(stable, removed), listOf(stable, added))

        assertEquals(listOf(added.id), diff.added.map(CandyRule::id))
        assertEquals(listOf(removed.id), diff.removed.map(CandyRule::id))
        assertEquals(listOf(stable.id), diff.unchanged.map(CandyRule::id))
    }
}
