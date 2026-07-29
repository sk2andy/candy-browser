package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `candy import accepts utf8 bom before header`() {
        val rule = CandyRule.new(
            CandyRuleAction.Block,
            CandyRuleKind.RequestHost,
            requestHost = "ads.example",
        )

        val preview = CandyRuleImport.parse("\uFEFF${CandyRuleFormat.export(listOf(rule))}")

        assertTrue(preview.isApplicable)
        assertEquals("ads.example", preview.rules.single().requestHost)
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
    fun `subscription accepts safe adblock network subset and skips css and scriptlets`() {
        val preview = CandySubscriptionRules.validatePreview(
            CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL,
            """
            ||ads.example^
            news.example##.cookie-overlay
            news.example##+js(set-constant, consent, true)
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(listOf("ads.example"), preview.rules.map(CandyRule::requestHost))
        assertEquals(2, preview.skippedCount)
        assertTrue(preview.rules.none { it.kind == CandyRuleKind.CosmeticCss })
    }

    @Test
    fun `subscription css volume cannot exhaust network rule capacity`() {
        val body = buildString {
            appendLine("||ads.example^")
            repeat(CandyRuleValidator.MAX_COSMETIC_RULES + 1) { index ->
                appendLine("site$index.example##.cookie-banner")
            }
        }

        val preview = CandySubscriptionRules.validatePreview(
            CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL,
            body,
        )

        assertTrue(preview.isApplicable)
        assertEquals(listOf("ads.example"), preview.rules.map(CandyRule::requestHost))
        assertEquals(CandyRuleValidator.MAX_COSMETIC_RULES + 1, preview.skippedCount)
    }

    @Test
    fun `official ublock preset is a safe explicit https source`() {
        assertTrue(
            CandyRuleValidator.isSafeHttpsUrl(CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL),
        )
        assertEquals(
            "uBlock Origin",
            CandyFilterPresets.groupFor(CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL),
        )
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

    @Test
    fun `subscription identity keeps global and profile instances separate`() {
        val global = CandyRule.new(
            CandyRuleAction.Block,
            CandyRuleKind.RequestHost,
            requestHost = "global.example",
        ).copy(sourceUrl = CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL)
        val work = global.copy(id = "work", profileId = "work")

        assertTrue(
            CandySubscriptionRules.isSameSourceScope(
                global,
                CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL,
                null,
            ),
        )
        assertFalse(
            CandySubscriptionRules.isSameSourceScope(
                work,
                CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL,
                null,
            ),
        )
        assertTrue(
            CandySubscriptionRules.isSameSourceScope(
                work,
                CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL,
                "work",
            ),
        )
    }

    @Test
    fun `overlapping subscriptions keep separate source ownership`() {
        val first = CandyRule.new(
            CandyRuleAction.Block,
            CandyRuleKind.RequestHost,
            requestHost = "shared.example",
            origin = CandyRuleOrigin.Subscription,
            sourceUrl = "https://lists.example/a.txt",
        )
        val second = first.copy(
            id = "second",
            sourceUrl = "https://lists.example/b.txt",
        )
        val sameSourceUpdate = first.copy(id = "updated")

        assertNotEquals(
            CandySubscriptionRules.storageKey(first),
            CandySubscriptionRules.storageKey(second),
        )
        assertEquals(
            CandySubscriptionRules.storageKey(first),
            CandySubscriptionRules.storageKey(sameSourceUpdate),
        )
    }

    @Test
    fun `import detects adblock host block and allow rules`() {
        val preview = CandyRuleImport.parse(
            """
            [Adblock Plus 2.0]
            ! comment
            ||ads.example^
            @@||cdn.example^
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(CandyImportFormat.AdblockCompatible, preview.format)
        assertEquals(2, preview.rules.size)
        assertEquals(CandyRuleAction.Block, preview.rules.first().action)
        assertEquals(CandyRuleAction.Allow, preview.rules.last().action)
    }

    @Test
    fun `adblock positive domain option expands to exact pair rules`() {
        val preview = CandyRuleImport.parse(
            "||tracker.example^\$domain=news.example|shop.example",
        )

        assertTrue(preview.isApplicable)
        assertEquals(
            setOf("news.example", "shop.example"),
            preview.rules.mapNotNull(CandyRule::firstPartyHost).toSet(),
        )
        assertTrue(preview.rules.all { it.kind == CandyRuleKind.HostPair })
    }

    @Test
    fun `adblock from alias third party modifier and hosts syntax map exactly`() {
        val preview = CandyRuleImport.parse(
            """
            ||tracker.example^${'$'}from=news.example,3p
            0.0.0.0 ads.example
            127.0.0.1 metrics.example
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(3, preview.rules.size)
        assertEquals(1, preview.rules.count { it.kind == CandyRuleKind.HostPair })
        assertEquals(2, preview.rules.count { it.kind == CandyRuleKind.RequestHost })
    }

    @Test
    fun `adblock origin cosmetic rules use safe standard css only`() {
        val preview = CandyRuleImport.parse(
            """
            news.example,shop.example##.sponsor
            news.example##+js(set-cookie, consent, yes)
            ##.generic-ad
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(2, preview.rules.size)
        assertTrue(preview.rules.all { it.kind == CandyRuleKind.CosmeticCss })
        assertEquals(2, preview.skippedCount)
    }

    @Test
    fun `adblock importer skips semantics Candy cannot represent`() {
        val preview = CandyRuleImport.parse(
            """
            ||safe.example^
            ||path.example/ads/*
            ||typed.example^${'$'}script
            ||except.example^${'$'}domain=~news.example
            /tracker[0-9]+/
            news.example#@#.sponsor
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(1, preview.rules.size)
        assertEquals(5, preview.skippedCount)
    }

    @Test
    fun `badfilter disables exact supported target regardless of order`() {
        val preview = CandyRuleImport.parse(
            """
            ||disabled.example^${'$'}domain=news.example,3p
            ||kept.example^
            ||disabled.example^${'$'}3p,badfilter,domain=news.example
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(listOf("kept.example"), preview.rules.map(CandyRule::requestHost))
    }

    @Test
    fun `preprocessor blocks and remote includes are never imported`() {
        val preview = CandyRuleImport.parse(
            """
            ||safe.example^
            !#if env_chromium
            ||conditional.example^
            !#else
            ||alternate.example^
            !#endif
            !#include https://filters.example/remote.txt
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(listOf("safe.example"), preview.rules.map(CandyRule::requestHost))
        assertEquals(4, preview.skippedCount)
    }

    @Test
    fun `badfilter inside skipped conditional cannot affect unconditional rule`() {
        val preview = CandyRuleImport.parse(
            """
            ||ads.example^
            !#if false
            ||ads.example^${'$'}badfilter
            !#endif
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(listOf("ads.example"), preview.rules.map(CandyRule::requestHost))
    }

    @Test
    fun `html and response header filters are not treated as cosmetic css`() {
        val preview = CandyRuleImport.parse(
            """
            news.example##^responseheader(set-cookie)
            news.example##.sponsored
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals(listOf(".sponsored"), preview.rules.map(CandyRule::cosmeticSelector))
        assertEquals(1, preview.skippedCount)
    }

    @Test
    fun `global exception preserves adblock precedence across host boundaries`() {
        val preview = CandyRuleImport.parse(
            """
            ||sub.example.com^
            ||example.com^${'$'}domain=news.example
            @@||sub.example.com^
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        val snapshot = CandyMatcherSnapshot.compile(preview.rules)
        assertEquals(
            CandyDecisionAction.Allow,
            snapshot.decide(
                requestUrl = "https://deep.sub.example.com/ad.js",
                pageUrl = "https://news.example/story",
                profileId = "default",
                isForMainFrame = false,
            )?.action,
        )
        assertEquals(
            CandyDecisionAction.Block,
            snapshot.decide(
                requestUrl = "https://other.example.com/ad.js",
                pageUrl = "https://news.example/story",
                profileId = "default",
                isForMainFrame = false,
            )?.action,
        )
    }

    @Test
    fun `broad global exception removes narrower global and pair blocks`() {
        val preview = CandyRuleImport.parse(
            """
            @@||example.com^
            ||sub.example.com^
            ||deep.sub.example.com^${'$'}domain=news.example
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        val snapshot = CandyMatcherSnapshot.compile(preview.rules)
        assertEquals(
            CandyDecisionAction.Allow,
            snapshot.decide(
                requestUrl = "https://deep.sub.example.com/ad.js",
                pageUrl = "https://news.example/story",
                profileId = "default",
                isForMainFrame = false,
            )?.action,
        )
    }

    @Test
    fun `exception normalization stops before quadratic rule expansion`() {
        val text = buildString {
            repeat(100) { index -> appendLine("@@||allow$index.example.com^") }
            repeat(100) { index ->
                appendLine("||example.com^${'$'}domain=site$index.test")
            }
        }

        val preview = CandyRuleImport.parse(text)

        assertFalse(preview.isApplicable)
        assertTrue(preview.errors.any { it.message == "rule-limit" })
        assertTrue(preview.rules.size <= CandyRuleValidator.MAX_RULES)
    }

    @Test
    fun `nul input is a fatal import error`() {
        val preview = CandyRuleImport.parse("||safe.example^\u0000||hidden.example^")

        assertFalse(preview.isApplicable)
        assertTrue(preview.rules.isEmpty())
    }

    @Test
    fun `adblock importer canonicalizes unicode and rejects public suffix traps`() {
        val preview = CandyRuleImport.parse(
            """
            ||bücher.example^
            ||co.uk^
            """.trimIndent(),
        )

        assertTrue(preview.isApplicable)
        assertEquals("xn--bcher-kva.example", preview.rules.single().requestHost)
        assertEquals(1, preview.skippedCount)
    }

    @Test
    fun `adblock input without representable rules fails closed`() {
        val preview = CandyRuleImport.parse("*${'$'}script,third-party")

        assertFalse(preview.isApplicable)
        assertEquals("no-supported-rules", preview.errors.single().message)
        assertEquals(1, preview.skippedCount)
    }

    @Test
    fun `adblock oversized imports stop before parsing`() {
        val preview = CandyRuleImport.parse("x".repeat(AdblockRuleFormat.MAX_IMPORT_BYTES + 1))

        assertFalse(preview.isApplicable)
        assertTrue(preview.truncated)
        assertTrue(preview.rules.isEmpty())
    }

    @Test
    fun `adblock imports over rule capacity are never partially applicable`() {
        val text = buildString {
            repeat(CandyRuleValidator.MAX_RULES + 1) { index ->
                appendLine("||tracker$index.example^")
            }
        }

        val preview = CandyRuleImport.parse(text)

        assertFalse(preview.isApplicable)
        assertEquals(CandyRuleValidator.MAX_RULES, preview.rules.size)
        assertTrue(preview.errors.any { it.message == "rule-limit" })
    }

    @Test
    fun `import scope deterministically overrides embedded scopes`() {
        val imported = CandyRule.new(
            CandyRuleAction.Block,
            CandyRuleKind.RequestHost,
            requestHost = "ads.example",
            profileId = "old",
        )
        val preview = CandyRulePreview(listOf(imported), emptyList())

        assertEquals(
            listOf("work"),
            CandyImportScope.apply(preview, "work").rules.map(CandyRule::profileId),
        )
        assertEquals(
            listOf(null),
            CandyImportScope.apply(preview, null).rules.map(CandyRule::profileId),
        )
        assertTrue(CandyImportScope.isAllowed("work", listOf("home", "work")))
        assertTrue(CandyImportScope.isAllowed(null, listOf("home", "work")))
        assertFalse(CandyImportScope.isAllowed("deleted", listOf("home", "work")))
    }
}
