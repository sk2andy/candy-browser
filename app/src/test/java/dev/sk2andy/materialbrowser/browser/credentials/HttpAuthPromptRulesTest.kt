package dev.sk2andy.materialbrowser.browser.credentials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpAuthPromptRulesTest {
    @Test
    fun `keeps bounded challenge details for secure page`() {
        val details = HttpAuthPromptRules.challengeDetails(
            host = " auth.example.com ",
            realm = " Members ",
            pageUrl = "https://auth.example.com/private",
        )

        assertEquals("auth.example.com", details?.host)
        assertEquals("Members", details?.realm)
        assertTrue(details?.isPageSecure == true)
    }

    @Test
    fun `marks non HTTPS page as insecure`() {
        val details = HttpAuthPromptRules.challengeDetails(
            host = "192.168.1.10",
            realm = null,
            pageUrl = "http://192.168.1.10/library",
        )

        assertFalse(requireNotNull(details).isPageSecure)
        assertNull(details.realm)
    }

    @Test
    fun `rejects unsafe or oversized display values`() {
        assertNull(
            HttpAuthPromptRules.challengeDetails(
                host = "safe.example\nspoofed.example",
                realm = "Members",
                pageUrl = "https://safe.example",
            ),
        )
        assertNull(
            HttpAuthPromptRules.challengeDetails(
                host = "h".repeat(256),
                realm = null,
                pageUrl = "https://example.com",
            ),
        )
        assertNull(
            HttpAuthPromptRules.challengeDetails(
                host = "",
                realm = null,
                pageUrl = "https://example.com",
            ),
        )
    }

    @Test
    fun `drops unsafe realm without rejecting valid host`() {
        val details = HttpAuthPromptRules.challengeDetails(
            host = "auth.example.com",
            realm = "trusted\u202Espoofed",
            pageUrl = "https://auth.example.com",
        )

        assertEquals("auth.example.com", details?.host)
        assertNull(details?.realm)
    }

    @Test
    fun `rejects challenge from different top level host`() {
        assertNull(
            HttpAuthPromptRules.challengeDetails(
                host = "tracker.example",
                realm = "Members",
                pageUrl = "https://app.example/account",
            ),
        )
    }

    @Test
    fun `keeps callback host when page uses non default port`() {
        val details = HttpAuthPromptRules.challengeDetails(
            host = "localhost",
            realm = null,
            pageUrl = "http://localhost:8080/private",
        )

        assertEquals("localhost", details?.host)
    }
}
