package dev.sk2andy.materialbrowser.browser.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class UserScriptDependencyResolverTest {
    @Test
    fun `network client allowlist excludes arbitrary and local dependency hosts`() {
        assertTrue(UserScriptDependencyRules.isTrustedFetchHost("raw.githubusercontent.com"))
        assertTrue(UserScriptDependencyRules.isTrustedFetchHost("CDN.JSDELIVR.NET"))
        assertFalse(UserScriptDependencyRules.isTrustedFetchHost("cdn.example"))
        assertFalse(UserScriptDependencyRules.isTrustedFetchHost("localhost"))
    }

    @Test
    fun `redirects stay on trusted https dependency hosts`() {
        assertEquals(
            "https://update.greasyfork.org/scripts/408776/toolkit.js",
            UserScriptDependencyRules.resolveTrustedRedirect(
                currentUrl = "https://greasyfork.org/scripts/408776/code/toolkit.js",
                location = "https://update.greasyfork.org/scripts/408776/toolkit.js",
            ),
        )
        assertEquals(
            "https://greasyfork.org/scripts/toolkit.js",
            UserScriptDependencyRules.resolveTrustedRedirect(
                currentUrl = "https://greasyfork.org/scripts/408776/code/toolkit.js",
                location = "../../toolkit.js",
            ),
        )
        assertEquals(
            null,
            UserScriptDependencyRules.resolveTrustedRedirect(
                currentUrl = "https://greasyfork.org/scripts/408776/code/toolkit.js",
                location = "http://update.greasyfork.org/toolkit.js",
            ),
        )
        assertEquals(
            null,
            UserScriptDependencyRules.resolveTrustedRedirect(
                currentUrl = "https://greasyfork.org/scripts/408776/code/toolkit.js",
                location = "https://attacker.example/toolkit.js",
            ),
        )
    }

    @Test
    fun `resolves requires and resources in metadata order`() {
        val requested = mutableListOf<String>()
        val resolver = UserScriptDependencyResolver { url, _ ->
            requested += url
            when (url) {
                "https://cdn.example/one.js" -> UserScriptDependencyFetch("window.one = true;".toByteArray())
                "https://cdn.example/two.js" -> UserScriptDependencyFetch("window.two = true;".toByteArray())
                else -> UserScriptDependencyFetch(byteArrayOf(0, 1, 2), "image/png; charset=binary")
            }
        }

        val result = resolver.resolve(
            script(
                """
                // @require https://cdn.example/one.js
                // @require https://cdn.example/two.js
                // @resource icon https://cdn.example/icon.png
                // @grant GM_getResourceURL
                """.trimIndent(),
            ),
        ) as UserScriptDependencyResolution.Resolved

        assertEquals(
            listOf(
                "https://cdn.example/one.js",
                "https://cdn.example/two.js",
                "https://cdn.example/icon.png",
            ),
            requested,
        )
        assertEquals("window.one = true;", result.script.requires[0].source)
        assertEquals("window.two = true;", result.script.requires[1].source)
        assertEquals("AAEC", result.script.resources.single().encodedContent)
        assertEquals("image/png", result.script.resources.single().mimeType)
        assertTrue(UserScriptRules.isCanonical(result.script))
    }

    @Test
    fun `verifies sha256 before accepting dependency`() {
        val bytes = "trusted".toByteArray()
        val expected = sha256(bytes)
        val resolver = UserScriptDependencyResolver { _, _ -> UserScriptDependencyFetch(bytes) }
        val accepted = resolver.resolve(script("// @require https://cdn.example/a.js#sha256=$expected"))
        val rejected = resolver.resolve(script("// @require https://cdn.example/a.js#sha256=${"0".repeat(64)}"))

        assertTrue(accepted is UserScriptDependencyResolution.Resolved)
        assertEquals(
            UserScriptDependencyFailureReason.IntegrityMismatch,
            (rejected as UserScriptDependencyResolution.Failed).reason,
        )
    }

    @Test
    fun `rejects invalid utf8 require while allowing binary resource`() {
        val invalidUtf8 = byteArrayOf(0xC3.toByte(), 0x28)
        val resolver = UserScriptDependencyResolver { _, _ -> UserScriptDependencyFetch(invalidUtf8) }

        val requireResult = resolver.resolve(script("// @require https://cdn.example/a.js"))
        val resourceResult = resolver.resolve(
            script(
                """
                // @resource blob https://cdn.example/a.bin
                // @grant GM_getResourceURL
                """.trimIndent(),
            ),
        )

        assertEquals(
            UserScriptDependencyFailureReason.InvalidUtf8,
            (requireResult as UserScriptDependencyResolution.Failed).reason,
        )
        assertTrue(resourceResult is UserScriptDependencyResolution.Resolved)
    }

    @Test
    fun `rejects per dependency and aggregate byte overflow`() {
        val tooLarge = UserScriptDependencyResolver { _, max ->
            UserScriptDependencyFetch(ByteArray(max + 1))
        }.resolve(script("// @require https://cdn.example/a.js"))
        assertEquals(
            UserScriptDependencyFailureReason.TooLarge,
            (tooLarge as UserScriptDependencyResolution.Failed).reason,
        )

        val chunk = ByteArray(UserScriptDependencyResolver.MAX_REQUIRE_BYTES) { 'x'.code.toByte() }
        val declarations = List(9) { index -> "// @require https://cdn$index.example/a.js" }
            .joinToString("\n")
        val aggregate = UserScriptDependencyResolver { _, _ -> UserScriptDependencyFetch(chunk) }
            .resolve(script(declarations))
        assertEquals(
            UserScriptDependencyFailureReason.TotalTooLarge,
            (aggregate as UserScriptDependencyResolution.Failed).reason,
        )
    }

    @Test
    fun `maps fetch exception to bounded network failure`() {
        val result = UserScriptDependencyResolver { _, _ -> error("network details") }
            .resolve(script("// @require https://cdn.example/a.js"))

        assertEquals(UserScriptDependencyFailureReason.Network, (result as UserScriptDependencyResolution.Failed).reason)
        assertEquals("https://cdn.example/a.js", result.dependencyUrl)
        assertFalse(result.toString().contains("network details"))
    }

    private fun script(metadata: String): UserScript {
        val source = """
            // ==UserScript==
            // @name Dependency test
            // @match https://example.com/*
            $metadata
            // ==/UserScript==
            window.main = true;
        """.trimIndent()
        return (UserScriptParser.parse("dependency-test", source) as UserScriptParseResult.Accepted).script
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
