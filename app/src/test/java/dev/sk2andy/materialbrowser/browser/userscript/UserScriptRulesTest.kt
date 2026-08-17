package dev.sk2andy.materialbrowser.browser.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptRulesTest {
    @Test
    fun `match patterns constrain scheme host path and query`() {
        val script = script(
            """
            // @match https://*.example.com/articles/*
            """,
        )

        assertTrue(UserScriptRules.matches(script, "https://example.com/articles/1?view=full"))
        assertFalse(UserScriptRules.matches(script, "https://example.com:8443/articles/1"))
        assertTrue(UserScriptRules.matches(script, "https://news.example.com/articles/1"))
        assertFalse(UserScriptRules.matches(script, "http://news.example.com/articles/1"))
        assertFalse(UserScriptRules.matches(script, "https://notexample.com/articles/1"))
        assertFalse(UserScriptRules.matches(script, "https://example.com/videos/1"))
    }

    @Test
    fun `includes are additive and excludes win`() {
        val script = script(
            """
            // @match https://news.example.com/*
            // @include *://blog.example.net/posts/*
            // @exclude *://*/posts/private/*
            """,
        )

        assertTrue(UserScriptRules.matches(script, "https://news.example.com/home"))
        assertTrue(UserScriptRules.matches(script, "http://blog.example.net/posts/public/1"))
        assertFalse(UserScriptRules.matches(script, "https://blog.example.net/posts/private/1"))
        assertFalse(UserScriptRules.matches(script, "ftp://blog.example.net/posts/public/1"))
        assertFalse(UserScriptRules.matches(script, "javascript:https://news.example.com/home"))
    }

    @Test
    fun `match ignores fragment but keeps query`() {
        val script = script("// @match https://example.com/page?mode=read")

        assertTrue(UserScriptRules.matches(script, "https://example.com/page?mode=read#section"))
        assertFalse(UserScriptRules.matches(script, "https://example.com/page?mode=edit#section"))
    }

    @Test
    fun `registration is global enabled and regular only`() {
        val enabled = script("// @match https://example.com/*", id = "enabled")
        val disabled = script("// @match https://example.org/*", id = "disabled", enabled = false)

        assertEquals(listOf(enabled), UserScriptRules.selectForRegistration(listOf(enabled, disabled), isPrivate = false))
        assertTrue(UserScriptRules.selectForRegistration(listOf(enabled), isPrivate = true).isEmpty())
        assertTrue(
            UserScriptRules.selectForRegistration(
                List(UserScriptParser.MAX_SCRIPTS + 1) { enabled.copy(id = "script-$it") },
                isPrivate = false,
            ).isEmpty(),
        )
    }

    @Test
    fun `registration rejects models that disagree with source metadata`() {
        val canonical = script("// @match https://example.com/*")
        val forged = canonical.copy(matchPatterns = listOf("https://other.example/*"))

        assertTrue(UserScriptRules.selectForRegistration(listOf(forged), isPrivate = false).isEmpty())
        assertFalse(UserScriptRules.matches(forged, "https://other.example/"))
        assertTrue(UserScriptRules.allowedOriginRules(forged).isEmpty())
    }

    @Test
    fun `origin rules stay tight when positive patterns expose safe origins`() {
        val script = script(
            """
            // @include *://*.example.com/private/*
            // @include https://static.example.net:8443/assets/*
            """,
        )

        assertEquals(
            setOf(
                "http://*.example.com",
                "https://*.example.com",
                "https://static.example.net:8443",
            ),
            UserScriptRules.allowedOriginRules(script),
        )
    }

    @Test
    fun `match patterns derive tight default port origin rules`() {
        val exact = script("// @match https://example.com/*")
        val subdomains = script("// @match *://*.example.org/*")

        assertTrue(UserScriptRules.matches(exact, "https://example.com:443/path"))
        assertFalse(UserScriptRules.matches(exact, "https://example.com:8443/path"))
        assertEquals(setOf("https://example.com"), UserScriptRules.allowedOriginRules(exact))
        assertEquals(
            setOf(
                "http://example.org",
                "http://*.example.org",
                "https://example.org",
                "https://*.example.org",
            ),
            UserScriptRules.allowedOriginRules(subdomains),
        )
    }

    @Test
    fun `origin rules fall back to wildcard when include origin is not derivable`() {
        val broad = script("// @include *://*/*")
        val allUrls = script("// @match <all_urls>")

        assertEquals(setOf("*"), UserScriptRules.allowedOriginRules(broad))
        assertEquals(setOf("*"), UserScriptRules.allowedOriginRules(allUrls))
    }

    @Test
    fun `registration fails closed when enabled source budget is exceeded`() {
        val largeBody = "window.large = true;\n" + "x".repeat(240 * 1_024)
        val scripts = List(9) { index ->
            script(
                metadata = "// @match https://example$index.com/*",
                id = "large-$index",
                body = largeBody,
            )
        }

        assertTrue(UserScriptRules.selectForRegistration(scripts, isPrivate = false).isEmpty())
        assertFalse(UserScriptRules.isWithinCollectionBounds(scripts))
        assertTrue(
            UserScriptRules.isWithinCollectionBounds(
                scripts.map { script -> script.copy(enabled = false) },
            ),
        )
    }

    @Test
    fun `direct event injection does not encode raw source`() {
        val controlHeavyBody = "\u0001".repeat(240 * 1_024)
        val scripts = List(7) { index ->
            script(
                metadata = "// @include https://example$index.com/*",
                id = "escaped-$index",
                body = controlHeavyBody,
            )
        }

        assertTrue(
            scripts.sumOf { script -> script.source.toByteArray().size } <
                UserScriptRules.MAX_REGISTERED_SOURCE_BYTES,
        )
        assertTrue(UserScriptRules.isWithinCollectionBounds(scripts))
    }

    @Test
    fun `collection bounds all stored source including disabled scripts`() {
        val largeBody = "window.stored = true;\n" + "x".repeat(240 * 1_024)
        val scripts = List(18) { index ->
            script(
                metadata = "// @include https://stored$index.example/*",
                id = "stored-$index",
                enabled = false,
                body = largeBody,
            )
        }

        assertFalse(UserScriptRules.isWithinCollectionBounds(scripts))
    }

    private fun script(
        metadata: String,
        id: String = "script-id",
        enabled: Boolean = true,
        body: String = "window.candy = true;",
    ): UserScript {
        val source = """
            // ==UserScript==
            // @name Test script
            ${metadata.trimIndent()}
            // ==/UserScript==
            $body
        """.trimIndent()
        return (UserScriptParser.parse(id, source, enabled) as UserScriptParseResult.Accepted).script
    }
}
