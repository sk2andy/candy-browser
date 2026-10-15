package dev.sk2andy.materialbrowser.browser.integration.linkrouting

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRoutingRulesTest {
    private val profiles = listOf(BrowserProfile("personal", "🍬"), BrowserProfile("work", "💼"))

    @Test
    fun `exact domain routes to selected profile without matching lookalikes`() {
        val rule = rule("github.com", LinkRoutingTarget(LinkRoutingTargetKind.Profile, "work"))
        assertEquals(
            LinkRoutingResolution.Open("https://github.com/project", "work", rule.target),
            resolve("https://github.com/project", listOf(rule)),
        )
        listOf("https://api.github.com", "https://evilgithub.com", "https://github.com.evil.example").forEach {
            assertEquals(LinkRoutingResolution.NoMatch, resolve(it, listOf(rule)))
        }
    }

    @Test
    fun `subdomain opt in uses label boundaries and canonicalized domains`() {
        val rule = rule("GITHUB.COM.").copy(includeSubdomains = true)
        assertEquals(
            LinkRoutingResolution.Open("https://API.GITHUB.com/issues", "personal", rule.target),
            resolve("https://API.GITHUB.com/issues", listOf(rule)),
        )
        assertEquals(LinkRoutingResolution.NoMatch, resolve("https://github.com.evil.example", listOf(rule)))
        assertEquals("xn--bcher-kva.example", LinkRoutingRules.sanitize(rule("bücher.example"))?.domain)
    }

    @Test
    fun `first enabled matching rule wins in user chosen order`() {
        val reader = rule("news.example")
        val work = reader.copy(id = "work-rule", target = LinkRoutingTarget(LinkRoutingTargetKind.Profile, "work"))
        assertEquals("personal", (resolve("https://news.example", listOf(reader, work)) as LinkRoutingResolution.Open).profileId)
        assertEquals("work", (resolve("https://news.example", listOf(work, reader)) as LinkRoutingResolution.Open).profileId)
        assertEquals("work", (resolve("https://news.example", listOf(reader.copy(enabled = false), work)) as LinkRoutingResolution.Open).profileId)
    }

    @Test
    fun `missing or protected first target cannot fall through to another matching rule`() {
        val protected = BrowserProfile("work", "💼", protection = ProfileProtection(ProfileLockTrigger.AppClosed))
        val first = rule("news.example", LinkRoutingTarget(LinkRoutingTargetKind.Profile, "work"))
        val later = rule("news.example").copy(id = "second-rule")
        assertEquals(LinkRoutingResolution.Unavailable, resolve("https://news.example", listOf(first, later), listOf(profiles.first(), protected)))
        assertEquals(LinkRoutingResolution.Unavailable, resolve("https://news.example", listOf(first), profiles.take(1)))
    }

    @Test
    fun `synced and disabled profile destinations remain unavailable`() {
        val synced = BrowserProfile("work", "💼", syncedDeviceId = "remote-device")
        val rule = rule("github.com", LinkRoutingTarget(LinkRoutingTargetKind.Profile, "work"))
        assertEquals(LinkRoutingResolution.Unavailable, resolve("https://github.com", listOf(rule), listOf(profiles.first(), synced)))
        assertEquals(LinkRoutingResolution.Unavailable, resolve("https://github.com", listOf(rule), profilesEnabled = false))
    }

    @Test
    fun `private incoming links bypass all stored rules`() {
        assertEquals(LinkRoutingResolution.NoMatch, resolve("https://news.example", listOf(rule("news.example")), isPrivate = true))
    }

    @Test
    fun `reader waits for accepted page load without reading other documents`() {
        val request = LinkRoutingReaderRequest("tab", "https://news.example/story")
        val tab = BrowserTab("tab", lastAccessedAt = 1, url = request.sourceUrl, isLoading = true)
        assertFalse(LinkRoutingRules.canOpenReader(request, tab))
        assertFalse(LinkRoutingRules.shouldCancelReaderRequest(request, tab))
        assertTrue(LinkRoutingRules.canOpenReader(request, tab.copy(isLoading = false)))
        listOf(
            tab.copy(id = "other"),
            tab.copy(url = "https://news.example/other"),
            tab.copy(isIncognito = true),
            tab.copy(error = "Failed"),
        ).forEach { stale ->
            assertTrue(LinkRoutingRules.shouldCancelReaderRequest(request, stale))
            assertFalse(LinkRoutingRules.canOpenReader(request, stale))
        }
    }

    @Test
    fun `untrusted URL shapes never route`() {
        val rule = rule("news.example")
        listOf("javascript:alert(1)", "https://user@news.example", "https://news.example/ bad", "file:///news.example", "https://news.example/" + "a".repeat(8_192)).forEach {
            assertEquals(LinkRoutingResolution.NoMatch, resolve(it, listOf(rule)))
        }
    }

    @Test
    fun `capsule route requires existing target and existing navigation boundary`() {
        val capsule = SiteCapsule("capsule", "Work", "https://work.example/start", "work", createdAtMillis = 1, updatedAtMillis = 1)
        val rule = rule("work.example", LinkRoutingTarget(LinkRoutingTargetKind.Capsule, "capsule"))
        assertEquals(
            LinkRoutingResolution.Open("https://work.example/project", "work", rule.target),
            resolve("https://work.example/project", listOf(rule), capsules = listOf(capsule)),
        )
        assertEquals(LinkRoutingResolution.Unavailable, resolve("https://work.example/project", listOf(rule)))
        assertEquals(LinkRoutingResolution.Unavailable, resolve("http://work.example/project", listOf(rule), capsules = listOf(capsule)))
    }

    @Test
    fun `malformed persisted rules are discarded and retained list stays bounded`() {
        listOf("https://github.com", "*.github.com", "com", "127.0.0.1", "github.com/path").forEach {
            assertNull(LinkRoutingRules.sanitize(rule(it)))
        }
        assertNull(LinkRoutingRules.sanitize(rule("github.com").copy(id = "bad id")))
        assertNull(LinkRoutingRules.sanitize(rule("github.com", LinkRoutingTarget(LinkRoutingTargetKind.Profile))))
        assertNull(LinkRoutingRules.sanitize(rule("github.com", LinkRoutingTarget(LinkRoutingTargetKind.Reader, "work"))))
        val repeated = List(100) { rule("news.example").copy(id = "rule-$it") }
        assertEquals(64, LinkRoutingRules.bounded(repeated).size)
        assertEquals(1, LinkRoutingRules.bounded(listOf(repeated.first(), repeated.first())).size)
    }

    private fun rule(domain: String, target: LinkRoutingTarget = LinkRoutingTarget(LinkRoutingTargetKind.Reader)) =
        LinkRoutingRule("rule", domain, target = target)

    private fun resolve(
        url: String,
        rules: List<LinkRoutingRule>,
        profiles: List<BrowserProfile> = this.profiles,
        capsules: List<SiteCapsule> = emptyList(),
        profilesEnabled: Boolean = true,
        isPrivate: Boolean = false,
    ): LinkRoutingResolution = LinkRoutingRules.resolve(
        url, rules, profiles, capsules, "personal", profilesEnabled, isPrivate,
    )
}
