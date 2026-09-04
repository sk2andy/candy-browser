package dev.sk2andy.materialbrowser.recall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallRulesTest {
    @Test
    fun `normal address query requires two meaningful words`() {
        assertNull(RecallRules.addressQuery("candy"))
        assertNull(RecallRules.addressQuery("a browser"))
        assertEquals("candy browser", RecallRules.addressQuery("  candy   browser  "))
    }

    @Test
    fun `explicit recall command is isolated from normal command prefix`() {
        assertTrue(RecallRules.isExplicitCommand(">recall browser history"))
        assertTrue(RecallRules.isExplicitCommand("  >ReCaLl browser"))
        assertFalse(RecallRules.isExplicitCommand(">recalling browser"))
        assertEquals("browser history", RecallRules.explicitQuery(">recall browser history"))
        assertNull(RecallRules.explicitQuery(">recall"))
    }

    @Test
    fun `disabled history suggestions keep only explicit recall address search`() {
        assertFalse(
            RecallRules.canSearchFromAddress(
                input = "browser history",
                historySuggestionsEnabled = false,
            ),
        )
        assertTrue(
            RecallRules.canSearchFromAddress(
                input = ">recall browser history",
                historySuggestionsEnabled = false,
            ),
        )
        assertTrue(
            RecallRules.canSearchFromAddress(
                input = "browser history",
                historySuggestionsEnabled = true,
            ),
        )
    }

    @Test
    fun `match expression uses bounded prefix terms with implicit conjunction`() {
        assertEquals("candy* browser*", RecallRules.matchExpression("Candy, browser!"))
    }

    @Test
    fun `document normalizes bounds and rejects unsafe input`() {
        val safe = RecallRules.sanitizeDocument(
            RecallDocument(
                profileId = " personal ",
                url = "https://example.com/path#fragment",
                title = "  Example\n page  ",
                text = " readable\u0000 text ".repeat(40_000),
                visitedAt = 1L,
            ),
        )

        assertEquals("personal", safe?.profileId)
        assertEquals("https://example.com/path", safe?.url)
        assertEquals("Example page", safe?.title)
        assertEquals(RecallRules.MAX_DOCUMENT_CHARS, safe?.text?.length)
        assertNull(
            RecallRules.sanitizeDocument(
                RecallDocument("personal", "file:///secret", "Secret", "text", 1L),
            ),
        )
        assertNull(
            RecallRules.sanitizeDocument(
                RecallDocument(
                    "p".repeat(RecallRules.MAX_PROFILE_ID_CHARS + 1),
                    "https://example.com",
                    "Page",
                    "text",
                    1L,
                ),
            ),
        )
        assertNull(
            RecallRules.sanitizeDocument(
                RecallDocument(
                    "personal",
                    "https://example.com/${"a".repeat(RecallRules.MAX_URL_CHARS)}",
                    "Page",
                    "text",
                    1L,
                ),
            ),
        )
    }

    @Test
    fun `stale extraction identity is rejected`() {
        val expected = RecallExtractionIdentity("tab", "personal", "https://example.com", 4)

        assertTrue(RecallRules.isCurrent(expected, expected, true, true, false, true))
        assertFalse(RecallRules.isCurrent(expected, expected, false, true, false, true))
        assertFalse(RecallRules.isCurrent(expected, expected.copy(navigationGeneration = 5), true, true, false, true))
        assertFalse(RecallRules.isCurrent(expected, expected, true, false, false, true))
        assertFalse(RecallRules.isCurrent(expected, expected, true, true, true, true))
        assertFalse(RecallRules.isCurrent(expected, expected, true, true, false, false))
    }
}
