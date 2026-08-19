package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyXRayTest {
    @Test
    fun `explicit force scroll override wins over bundled default`() {
        assertTrue(SitePrivacyOverrideRules.forceVerticalScrolling(null, bundledDefault = true))
        assertFalse(
            SitePrivacyOverrideRules.forceVerticalScrolling(
                SitePrivacyOverrides(forceVerticalScrolling = false),
                bundledDefault = true,
            ),
        )
        assertTrue(
            SitePrivacyOverrideRules.forceVerticalScrolling(
                SitePrivacyOverrides(forceVerticalScrolling = true),
                bundledDefault = false,
            ),
        )
    }

    @Test
    fun `page zooming is forced only by explicit override`() {
        assertFalse(SitePrivacyOverrideRules.forcePageZooming(null))
        assertFalse(
            SitePrivacyOverrideRules.forcePageZooming(
                SitePrivacyOverrides(forcePageZooming = false),
            ),
        )
        assertTrue(
            SitePrivacyOverrideRules.forcePageZooming(
                SitePrivacyOverrides(forcePageZooming = true),
            ),
        )
    }

    @Test
    fun `safe area is forced only by explicit override`() {
        assertFalse(SitePrivacyOverrideRules.forceSafeArea(null))
        assertFalse(
            SitePrivacyOverrideRules.forceSafeArea(
                SitePrivacyOverrides(forceSafeArea = false),
            ),
        )
        assertTrue(
            SitePrivacyOverrideRules.forceSafeArea(
                SitePrivacyOverrides(forceSafeArea = true),
            ),
        )
    }

    @Test
    fun `explicit cookie removal override wins over bundled default`() {
        assertTrue(SitePrivacyOverrideRules.cookieBannerRemovalDisabled(null, bundledDefault = true))
        assertFalse(
            SitePrivacyOverrideRules.cookieBannerRemovalDisabled(
                SitePrivacyOverrides(cookieBannerRemovalDisabled = false),
                bundledDefault = true,
            ),
        )
        assertTrue(
            SitePrivacyOverrideRules.cookieBannerRemovalDisabled(
                SitePrivacyOverrides(cookieBannerRemovalDisabled = true),
                bundledDefault = false,
            ),
        )
    }

    @Test
    fun `selection stores only values that differ from bundled default`() {
        assertEquals(null, SitePrivacyOverrideRules.overrideForSelection(false, false))
        assertEquals(true, SitePrivacyOverrideRules.overrideForSelection(true, false))
        assertEquals(null, SitePrivacyOverrideRules.overrideForSelection(true, true))
        assertEquals(false, SitePrivacyOverrideRules.overrideForSelection(false, true))
    }

    @Test
    fun `site privacy overrides normalize replace and keep new value within limit`() {
        val existing = linkedMapOf(
            "one.example" to SitePrivacyOverrides(cookieBannerRemovalDisabled = true),
            "two.example" to SitePrivacyOverrides(forceVerticalScrolling = true),
        )

        val updated = SitePrivacyOverrideRules.withOverride(
            current = existing,
            host = "News.Example",
            overrides = SitePrivacyOverrides(forcePageZooming = true),
            limit = 2,
        )

        assertEquals(setOf("one.example", "news.example"), updated.keys)
        assertEquals(true, updated.getValue("news.example").forcePageZooming)
        assertTrue(
            SitePrivacyOverrideRules.withOverride(
                current = updated,
                host = "NEWS.EXAMPLE",
                overrides = SitePrivacyOverrides(),
                limit = 2,
            ).keys == setOf("one.example"),
        )
    }

    @Test
    fun `sanitizer retains hosts but no path query fragment or credentials`() {
        val sanitized = PrivacyRequestSanitizer.sanitize(
            "https://user:secret@Metrics.Example/private/account?email=a%40b.test#token",
            "https://News.Example/article/customer-42?session=secret#comments",
        )

        assertEquals("metrics.example", sanitized?.requestHost)
        assertEquals("news.example", sanitized?.pageHost)
        assertFalse(sanitized.toString().contains("private"))
        assertFalse(sanitized.toString().contains("secret"))
        assertFalse(sanitized.toString().contains("customer"))
    }

    @Test
    fun `sanitizer rejects non-web malformed and invalid hosts`() {
        assertNull(PrivacyRequestSanitizer.sanitize("data:text/plain,hello", null))
        assertNull(PrivacyRequestSanitizer.sanitize("not a url", null))
        assertNull(PrivacyRequestSanitizer.normalizeHost("-invalid.example"))
    }

    @Test
    fun `sanitizer normalizes internationalized host names`() {
        assertEquals(
            "xn--bcher-kva.example",
            PrivacyRequestSanitizer.webHost("https://bücher.example/private?token=secret"),
        )
    }

    @Test
    fun `classifier only uses explicit hosts and complete labels`() {
        assertEquals(
            PrivacyRequestCategory.Advertising,
            PrivacyRequestClassifier.classify("cdn.doubleclick.net"),
        )
        assertEquals(
            PrivacyRequestCategory.Analytics,
            PrivacyRequestClassifier.classify("cdn.google-analytics.com"),
        )
        assertEquals(
            PrivacyRequestCategory.Social,
            PrivacyRequestClassifier.classify("connect.facebook.net"),
        )
        assertEquals(
            PrivacyRequestCategory.Other,
            PrivacyRequestClassifier.classify("shadow.example"),
        )
    }

    @Test
    fun `aggregation is bounded and keeps totals for omitted domains`() {
        val repository = PrivacyXRayRepository(domainLimit = 2)
        assertTrue(repository.record("tab", "https://a.doubleclick.net/ad", "https://news.test"))
        assertTrue(repository.record("tab", "https://b.google-analytics.com/pixel", "https://news.test"))
        assertTrue(repository.record("tab", "https://c.unknown.test/secret", "https://news.test"))
        assertTrue(repository.record("tab", "https://a.doubleclick.net/again", "https://news.test"))

        val snapshot = repository.snapshot("tab")
        assertEquals(4, snapshot.totalBlocked)
        assertEquals(2, snapshot.domains.size)
        assertEquals(1, snapshot.omittedDomainRequests)
        assertEquals(2, snapshot.domains.first { it.host == "a.doubleclick.net" }.blockedCount)
        assertEquals(2, snapshot.categoryCounts[PrivacyRequestCategory.Advertising])
    }

    @Test
    fun `pure aggregation sorts domains and marks mixed party history unknown`() {
        val snapshot = PrivacyAggregation.snapshot(
            totalBlocked = 3,
            categoryCounts = mapOf(PrivacyRequestCategory.Other to 3),
            partyCounts = mapOf(PrivacyPartyRelation.ThirdParty to 3),
            domains = listOf(
                PrivacyDomainSummary(
                    host = "low.example",
                    blockedCount = 1,
                    category = PrivacyRequestCategory.Other,
                    partyRelation = PrivacyPartyRelation.Unknown,
                ),
                PrivacyDomainSummary(
                    host = "high.example",
                    blockedCount = 2,
                    category = PrivacyRequestCategory.Other,
                    partyRelation = PrivacyPartyRelation.ThirdParty,
                ),
            ),
            omittedDomainRequests = 0,
        )

        assertEquals(listOf("high.example", "low.example"), snapshot.domains.map { it.host })
        assertEquals(
            PrivacyPartyRelation.Unknown,
            PrivacyAggregation.stablePartyRelation(
                mapOf(
                    PrivacyPartyRelation.FirstParty to 1,
                    PrivacyPartyRelation.ThirdParty to 1,
                ),
            ),
        )
    }

    @Test
    fun `pure batch aggregation applies classification retention and mixed party rules`() {
        val first = PrivacyAggregation.aggregateBatch(
            current = PrivacyXRaySnapshot.Empty,
            requests = listOf(
                SanitizedPrivacyRequest("cdn.doubleclick.net", "news.example"),
                SanitizedPrivacyRequest("cdn.doubleclick.net", "doubleclick.net"),
                SanitizedPrivacyRequest("overflow.example", "news.example"),
            ),
            domainLimit = 1,
        )

        assertEquals(3, first.totalBlocked)
        assertEquals(1, first.domains.size)
        assertEquals(1, first.omittedDomainRequests)
        assertEquals(PrivacyPartyRelation.Unknown, first.domains.single().partyRelation)
        assertEquals(2, first.categoryCounts[PrivacyRequestCategory.Advertising])
    }

    @Test
    fun `site exceptions respect dot boundary and direction`() {
        assertTrue(SiteExceptionRules.isPaused("news.example.com", setOf("example.com")))
        assertTrue(SiteExceptionRules.isPaused("example.com", setOf("example.com")))
        assertFalse(SiteExceptionRules.isPaused("notexample.com", setOf("example.com")))
        assertFalse(SiteExceptionRules.isPaused("example.com", setOf("news.example.com")))
    }

    @Test
    fun `main document is never blocked even when listed`() {
        assertFalse(
            RequestProtectionRules.shouldBlock(
                isForMainFrame = true,
                blockerEnabled = true,
                sitePaused = false,
                isListedRequest = true,
            ),
        )
        assertTrue(
            RequestProtectionRules.shouldBlock(
                isForMainFrame = false,
                blockerEnabled = true,
                sitePaused = false,
                isListedRequest = true,
            ),
        )
    }

    @Test
    fun `cookie policy allows third party cookies only when global rule is off or site paused`() {
        assertFalse(PrivacyPolicyRules.acceptsThirdPartyCookies(true, false))
        assertTrue(PrivacyPolicyRules.acceptsThirdPartyCookies(true, true))
        assertTrue(PrivacyPolicyRules.acceptsThirdPartyCookies(false, false))
    }

    @Test
    fun `party relation does not suffix-match lookalike host`() {
        assertEquals(
            PrivacyPartyRelation.FirstParty,
            PrivacyPartyClassifier.classify("cdn.example.com", "example.com"),
        )
        assertEquals(
            PrivacyPartyRelation.ThirdParty,
            PrivacyPartyClassifier.classify("notexample.com", "example.com"),
        )
        assertEquals(
            PrivacyPartyRelation.ThirdParty,
            PrivacyPartyClassifier.classify("tracker.example.net", "news.example.com"),
        )
        assertEquals(
            PrivacyPartyRelation.Unknown,
            PrivacyPartyClassifier.classify("tracker.example", null),
        )
        assertEquals(
            PrivacyPartyRelation.ThirdParty,
            PrivacyPartyClassifier.classify("cdn.publisher.co.uk", "news.other.co.uk"),
        )
    }

    @Test
    fun `repository retains concrete allow decision without increasing blocked total`() {
        val repository = PrivacyXRayRepository()
        repository.recordDecision(
            tabId = "tab",
            requestUrl = "https://tracker.example/pixel",
            pageUrl = "https://news.example/article",
            wasBlocked = false,
            decision = PrivacyRuleDecisionSummary(
                ruleId = "allow-1",
                label = "Privacy X-Ray",
                action = PrivacyRuleDecisionAction.Allow,
            ),
        )

        val snapshot = repository.snapshot("tab")
        assertEquals(0, snapshot.totalBlocked)
        assertEquals(1, snapshot.domains.single().allowedCount)
        assertEquals("allow-1", snapshot.domains.single().ruleDecision?.ruleId)
    }

    @Test
    fun `persistent exception retention is bounded and keeps newest host`() {
        val retained = SiteExceptionRules.withException(
            current = listOf("one.example", "two.example"),
            host = "three.example",
            limit = 2,
        )

        assertEquals(setOf("one.example", "three.example"), retained)
        assertTrue(SiteExceptionRules.mayPersist(isIncognito = false))
        assertFalse(SiteExceptionRules.mayPersist(isIncognito = true))
    }

    @Test
    fun `repository removes tab and all in-memory state`() {
        val repository = PrivacyXRayRepository()
        repository.record("tab", "https://tracker.example/pixel", "https://news.example")
        repository.remove("tab")

        assertEquals(PrivacyXRaySnapshot.Empty, repository.snapshot("tab"))
    }
}
