package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyRuleTest {
    @Test
    fun `canonicalizer handles IDN and rejects host traps`() {
        assertEquals("xn--bcher-kva.example", CandyHostCanonicalizer.canonicalHost("Bücher.Example."))
        assertNull(CandyHostCanonicalizer.canonicalHost("127.0.0.1"))
        assertNull(CandyHostCanonicalizer.canonicalHost("localhost"))
        assertNull(CandyHostCanonicalizer.canonicalHost("bad..example"))
        assertNull(CandyHostCanonicalizer.canonicalHost("-bad.example"))
        assertNull(CandyHostCanonicalizer.canonicalHost("example.com:443"))
        assertNull(CandyPublicSuffixRules.registrableDomain("github.io"))
        assertEquals("project.github.io", CandyPublicSuffixRules.registrableDomain("project.github.io"))
        assertNull(CandyPublicSuffixRules.registrableDomain("com.ar"))
        assertNull(CandyPublicSuffixRules.registrableDomain("co.nz"))
        assertNull(CandyPublicSuffixRules.registrableDomain("asn.au"))
        assertNull(CandyPublicSuffixRules.registrableDomain("id.au"))
        assertNull(CandyPublicSuffixRules.registrableDomain("k12.ca.us"))
        assertEquals(
            "school.k12.ca.us",
            CandyPublicSuffixRules.registrableDomain("www.school.k12.ca.us"),
        )
        assertEquals("example.co.nz", CandyPublicSuffixRules.registrableDomain("www.example.co.nz"))
    }

    @Test
    fun `validator rejects public suffixes and unsafe cosmetic input`() {
        assertEquals(
            CandyRuleError.PublicSuffixHost,
            invalidReason(rule(CandyRuleAction.Block, CandyRuleKind.RequestHost, request = "co.uk")),
        )
        assertEquals(
            CandyRuleError.PublicSuffixHost,
            invalidReason(
                rule(
                    CandyRuleAction.Block,
                    CandyRuleKind.HostPair,
                    request = "tracker.example",
                    firstParty = "co.uk",
                ),
            ),
        )
        assertEquals(
            CandyRuleError.InvalidSelector,
            invalidReason(
                rule(
                    CandyRuleAction.Cosmetic,
                    CandyRuleKind.CosmeticCss,
                    firstParty = "news.example",
                    selector = "@import url(https://evil.example/x)",
                ),
            ),
        )
        assertEquals(
            CandyRuleError.InvalidPair,
            invalidReason(
                rule(
                    CandyRuleAction.Block,
                    CandyRuleKind.HostPair,
                    request = "cdn.news.example",
                    firstParty = "news.example",
                ),
            ),
        )
        assertEquals(
            CandyRuleError.InvalidSource,
            invalidReason(
                rule(CandyRuleAction.Block, CandyRuleKind.RequestHost, request = "ads.example")
                    .copy(sourceUrl = "https://lists.example/" + "界".repeat(800)),
            ),
        )
        assertTrue(
            CandyRuleValidator.validate(
                rule(
                    CandyRuleAction.Cosmetic,
                    CandyRuleKind.CosmeticCss,
                    firstParty = "news.example",
                    selector = ".ad > span",
                ),
            ) is CandyRuleValidation.Valid,
        )
    }

    @Test
    fun `matcher respects complete host labels`() {
        val snapshot = CandyMatcherSnapshot.compile(
            listOf(rule(CandyRuleAction.Block, CandyRuleKind.RequestHost, request = "ads.example")),
        )

        assertEquals(
            CandyDecisionAction.Block,
            snapshot.decide(
                "https://cdn.ads.example/a.js",
                "https://news.example",
                "default",
                false,
            )?.action,
        )
        assertNull(
            snapshot.decide(
                "https://notads.example/a.js",
                "https://news.example",
                "default",
                false,
            ),
        )
    }

    @Test
    fun `site allow beats site block and main document is protected`() {
        val rules = listOf(
            rule(CandyRuleAction.Block, CandyRuleKind.RequestHost, request = "tracker.example", id = "a"),
            rule(
                CandyRuleAction.Block,
                CandyRuleKind.HostPair,
                request = "tracker.example",
                firstParty = "news.example",
                id = "b",
            ),
            rule(
                CandyRuleAction.Allow,
                CandyRuleKind.HostPair,
                request = "tracker.example",
                firstParty = "news.example",
                id = "c",
            ),
        )
        val snapshot = CandyMatcherSnapshot.compile(rules)

        val decision = snapshot.decide(
            "https://tracker.example/pixel",
            "https://sub.news.example/article",
            "default",
            false,
        )
        assertEquals(CandyDecisionAction.Allow, decision?.action)
        assertEquals("c", decision?.ruleId)
        assertNull(
            snapshot.decide(
                "https://tracker.example/pixel",
                "https://news.example/article",
                "default",
                true,
            ),
        )
    }

    @Test
    fun `site block can override global allow while other sites remain allowed`() {
        val snapshot = CandyMatcherSnapshot.compile(
            listOf(
                rule(CandyRuleAction.Allow, CandyRuleKind.RequestHost, request = "cdn.example", id = "a"),
                rule(
                    CandyRuleAction.Block,
                    CandyRuleKind.HostPair,
                    request = "cdn.example",
                    firstParty = "news.example",
                    id = "b",
                ),
            ),
        )

        assertEquals(
            CandyDecisionAction.Block,
            snapshot.decide(
                "https://cdn.example/file",
                "https://news.example",
                "default",
                false,
            )?.action,
        )
        assertEquals(
            CandyDecisionAction.Allow,
            snapshot.decide(
                "https://cdn.example/file",
                "https://shop.example",
                "default",
                false,
            )?.action,
        )
    }

    @Test
    fun `more specific host wins before allow at equal specificity`() {
        val specificBlockSnapshot = CandyMatcherSnapshot.compile(
            listOf(
                rule(CandyRuleAction.Allow, CandyRuleKind.RequestHost, request = "ads.example", id = "a"),
                rule(
                    CandyRuleAction.Block,
                    CandyRuleKind.RequestHost,
                    request = "sub.ads.example",
                    id = "b",
                ),
            ),
        )
        assertEquals(
            "b",
            specificBlockSnapshot.decide(
                "https://sub.ads.example/file",
                "https://news.example",
                "default",
                false,
            )?.ruleId,
        )
        val equalSpecificitySnapshot = CandyMatcherSnapshot.compile(
            listOf(
                rule(
                    CandyRuleAction.Block,
                    CandyRuleKind.RequestHost,
                    request = "sub.ads.example",
                    id = "b",
                ),
                rule(
                    CandyRuleAction.Allow,
                    CandyRuleKind.RequestHost,
                    request = "sub.ads.example",
                    id = "c",
                ),
            ),
        )

        val decision = equalSpecificitySnapshot.decide(
            "https://sub.ads.example/file",
            "https://news.example",
            "default",
            false,
        )

        assertEquals(CandyDecisionAction.Allow, decision?.action)
        assertEquals("c", decision?.ruleId)
    }

    @Test
    fun `compiled snapshot is immutable when source list changes`() {
        val source = mutableListOf(
            rule(CandyRuleAction.Block, CandyRuleKind.RequestHost, request = "ads.example"),
        )
        val snapshot = CandyMatcherSnapshot.compile(source)
        source.clear()

        assertEquals(
            CandyDecisionAction.Block,
            snapshot.decide(
                "https://ads.example/file",
                "https://news.example",
                "default",
                false,
            )?.action,
        )
    }

    @Test
    fun `ephemeral rules compile only into incognito snapshot`() {
        val temporary = rule(
            CandyRuleAction.Block,
            CandyRuleKind.RequestHost,
            request = "private.example",
            id = "temporary",
        )
        val snapshots = CandyMatcherSnapshots.compile(listOf(temporary), setOf(temporary.id))

        assertNull(
            snapshots.persistent.decide(
                "https://private.example/file",
                "https://news.example",
                "default",
                false,
            ),
        )
        assertEquals(
            CandyDecisionAction.Block,
            snapshots.incognito.decide(
                "https://private.example/file",
                "https://news.example",
                "default",
                false,
            )?.action,
        )
    }

    @Test
    fun `profile rule does not leak between profiles`() {
        val snapshot = CandyMatcherSnapshot.compile(
            listOf(
                rule(
                    CandyRuleAction.Block,
                    CandyRuleKind.RequestHost,
                    request = "tracker.example",
                    profile = "work",
                ),
            ),
        )
        assertTrue(
            snapshot.decide(
                "https://tracker.example/a",
                "https://news.example",
                "work",
                false,
            ) != null,
        )
        assertNull(
            snapshot.decide(
                "https://tracker.example/a",
                "https://news.example",
                "personal",
                false,
            ),
        )
    }

    @Test
    fun `cosmetic rules match only allowed origin and profile`() {
        val snapshot = CandyMatcherSnapshot.compile(
            listOf(
                rule(
                    CandyRuleAction.Cosmetic,
                    CandyRuleKind.CosmeticCss,
                    firstParty = "news.example",
                    selector = ".sponsor",
                    profile = "work",
                ),
            ),
        )

        assertEquals(1, snapshot.cosmeticRules("https://sub.news.example/a", "work").size)
        assertTrue(snapshot.cosmeticRules("https://notnews.example/a", "work").isEmpty())
        assertTrue(snapshot.cosmeticRules("https://news.example/a", "personal").isEmpty())
    }

    private fun rule(
        action: CandyRuleAction,
        kind: CandyRuleKind,
        request: String? = null,
        firstParty: String? = null,
        selector: String? = null,
        profile: String? = null,
        id: String = "rule-${request ?: firstParty}",
    ) = CandyRule(
        id = id,
        action = action,
        kind = kind,
        requestHost = request,
        firstPartyHost = firstParty,
        cosmeticSelector = selector,
        profileId = profile,
    )

    private fun invalidReason(rule: CandyRule): CandyRuleError =
        (CandyRuleValidator.validate(rule) as CandyRuleValidation.Invalid).reason
}
