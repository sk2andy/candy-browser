package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupHomeRulesTest {
    @Test
    fun `regular blank tab is reusable`() {
        val tabs = listOf(
            tab(id = "private", isIncognito = true),
            tab(id = "regular"),
        )

        assertEquals("regular", StartupHomeRules.reusableBlankTabId(tabs))
    }

    @Test
    fun `private blank tab is not reused as normal home`() {
        assertNull(
            StartupHomeRules.reusableBlankTabId(
                listOf(tab(id = "private", isIncognito = true)),
            ),
        )
    }

    @Test
    fun `visited regular tab is not reusable`() {
        assertNull(
            StartupHomeRules.reusableBlankTabId(
                listOf(tab(id = "visited", url = "https://example.com")),
            ),
        )
    }

    private fun tab(
        id: String,
        isIncognito: Boolean = false,
        url: String = BLANK_URL,
    ) = BrowserTab(
        id = id,
        lastAccessedAt = 1L,
        isIncognito = isIncognito,
        url = url,
    )
}
