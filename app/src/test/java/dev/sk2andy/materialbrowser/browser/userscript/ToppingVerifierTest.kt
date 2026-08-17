package dev.sk2andy.materialbrowser.browser.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToppingVerifierTest {
    @Test
    fun `accepts hash parser name and ordered scopes together`() {
        val source = source(
            metadata = """
                // @name Calm Reader
                // @match https://*.example.com/*
                // @include https://example.org:8443/articles/*
                // @grant none
            """.trimIndent(),
        )
        val bytes = source.toByteArray()
        val entry = entry(
            sha256 = ToppingVerifier.sha256(bytes),
            matches = listOf(
                "https://*.example.com/*",
                "https://example.org:8443/articles/*",
            ),
        )

        val result = ToppingVerifier.verify(entry, bytes, updatedAtMillis = 42L) as
            ToppingVerificationResult.Accepted

        assertEquals("topping.calm-reader", result.script.id)
        assertEquals(42L, result.script.updatedAtMillis)
        assertTrue(result.script.enabled)
    }

    @Test
    fun `rejects integrity mismatch before source parsing`() {
        val result = ToppingVerifier.verify(
            entry = entry(sha256 = "0".repeat(64)),
            bytes = "not a script".toByteArray(),
            updatedAtMillis = 0L,
        )

        assertEquals(ToppingVerificationResult.IntegrityMismatch, result)
    }

    @Test
    fun `rejects malformed utf8 after valid hash`() {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28)

        assertEquals(
            ToppingVerificationResult.InvalidUtf8,
            ToppingVerifier.verify(
                entry = entry(sha256 = ToppingVerifier.sha256(bytes)),
                bytes = bytes,
                updatedAtMillis = 0L,
            ),
        )
    }

    @Test
    fun `rejects bom so installed source hash remains comparable`() {
        val bytes = ("\uFEFF" + source(
            metadata = "// @name Calm Reader\n// @match https://*.example.com/*",
        )).toByteArray()

        assertEquals(
            ToppingVerificationResult.MetadataMismatch,
            ToppingVerifier.verify(
                entry = entry(sha256 = ToppingVerifier.sha256(bytes)),
                bytes = bytes,
                updatedAtMillis = 0L,
            ),
        )
    }

    @Test
    fun `rejects unsupported scripts and catalog metadata drift`() {
        val privileged = source(
            metadata = """
                // @name Calm Reader
                // @match https://*.example.com/*
                // @grant GM_xmlhttpRequest
            """.trimIndent(),
        ).toByteArray()
        val invalid = ToppingVerifier.verify(
            entry = entry(sha256 = ToppingVerifier.sha256(privileged)),
            bytes = privileged,
            updatedAtMillis = 0L,
        ) as ToppingVerificationResult.InvalidScript
        assertEquals(UserScriptRejectionReason.PrivilegedGrant, invalid.reason)

        val valid = source(
            metadata = """
                // @name Calm Reader
                // @match https://*.example.com/*
            """.trimIndent(),
        ).toByteArray()
        assertEquals(
            ToppingVerificationResult.MetadataMismatch,
            ToppingVerifier.verify(
                entry = entry(
                    sha256 = ToppingVerifier.sha256(valid),
                    matches = listOf("https://other.example/*"),
                ),
                bytes = valid,
                updatedAtMillis = 0L,
            ),
        )
    }

    private fun entry(
        sha256: String,
        matches: List<String> = listOf("https://*.example.com/*"),
    ) = ToppingCatalogEntry(
        id = "calm-reader",
        name = "Calm Reader",
        description = "Calmer articles.",
        author = "Candy Browser",
        license = "MIT",
        version = "1.0.0",
        source = "toppings/calm-reader.user.js",
        matches = matches,
        sha256 = sha256,
    )

    private fun source(metadata: String): String = """
        // ==UserScript==
        $metadata
        // ==/UserScript==
        document.documentElement.dataset.candy = "calm";
    """.trimIndent()
}
