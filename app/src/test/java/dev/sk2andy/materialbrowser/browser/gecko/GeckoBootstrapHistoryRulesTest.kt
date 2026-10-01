package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoBootstrapHistoryRulesTest {
    @Test
    fun `bootstrap anywhere in native history rejects the entire snapshot`() {
        assertFalse(GeckoBootstrapHistoryRules.canRestoreHistory(listOf(BOOTSTRAP_URL)))
        assertFalse(
            GeckoBootstrapHistoryRules.canRestoreHistory(
                listOf(BOOTSTRAP_URL, "https://example.com/current"),
            ),
        )
        assertFalse(
            GeckoBootstrapHistoryRules.canRestoreHistory(
                listOf("https://example.com/current", BOOTSTRAP_URL),
            ),
        )
    }

    @Test
    fun `web internal and extension options history remain restorable`() {
        assertTrue(
            GeckoBootstrapHistoryRules.canRestoreHistory(
                listOf(
                    "about:blank",
                    "https://example.com/root",
                    "https://example.com/current",
                    "moz-extension://5e6344e7-68a0-4a80-863c-0123456789ab/options.html",
                ),
            ),
        )
    }

    @Test
    fun `empty native history retains existing restore eligibility`() {
        assertTrue(GeckoBootstrapHistoryRules.canRestoreHistory(emptyList()))
    }

    @Test
    fun `web page resembling bootstrap is not an internal binding document`() {
        assertTrue(
            GeckoBootstrapHistoryRules.canRestoreHistory(
                listOf(
                    "https://example.com/bootstrap.html?token=11111111-2222-4333-8444-555555555555",
                ),
            ),
        )
    }

    @Test
    fun `history traversal skips bootstrap while preserving native indices`() {
        val urls = listOf(
            BOOTSTRAP_URL,
            "https://example.com/first",
            BOOTSTRAP_URL,
            "https://example.com/second",
            "https://example.com/third",
            BOOTSTRAP_URL,
        )

        assertEquals(1, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 4, offset = -2))
        assertEquals(3, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 1, offset = 1))
        assertEquals(4, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 1, offset = 2))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 1, offset = -1))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 4, offset = 1))
    }

    @Test
    fun `first page and pushed state remain separate back and forward entries`() {
        val urls = listOf(BOOTSTRAP_URL, "https://example.com/first", "https://example.com/pushed")

        assertEquals(1, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 2, offset = -1))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 1, offset = -1))
        assertEquals(2, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 1, offset = 1))
    }

    @Test
    fun `history traversal preserves ordinary blank and internal page semantics`() {
        val urls = listOf(BOOTSTRAP_URL, "about:blank", "about:config", "https://example.com/current")

        assertEquals(1, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 3, offset = -2))
        assertEquals(2, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 3, offset = -1))
        assertEquals(3, GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 3, offset = 0))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 0, offset = 0))
    }

    @Test
    fun `invalid native positions and extreme offsets do not wrap or escape history`() {
        val urls = listOf(BOOTSTRAP_URL, "https://example.com/current")

        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(emptyList(), currentIndex = 0, offset = 0))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = -1, offset = 1))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = urls.size, offset = -1))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 1, offset = Int.MIN_VALUE))
        assertNull(GeckoBootstrapHistoryRules.historyIndexAtOffset(urls, currentIndex = 1, offset = Int.MAX_VALUE))
    }

    private companion object {
        const val BOOTSTRAP_URL =
            "moz-extension://5e6344e7-68a0-4a80-863c-0123456789ab/" +
                "bootstrap.html?token=11111111-2222-4333-8444-555555555555"
    }
}
