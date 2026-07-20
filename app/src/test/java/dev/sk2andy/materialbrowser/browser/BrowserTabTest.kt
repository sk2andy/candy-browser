package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabTest {
    private val freshBlankTab = BrowserTab(
        id = "blank",
        lastAccessedAt = 1,
    )

    @Test
    fun `fresh blank tab can be discarded after switching to an open tab`() {
        assertTrue(freshBlankTab.isFreshBlankTab)
    }

    @Test
    fun `blank page with navigation state is not treated as a fresh tab`() {
        assertFalse(freshBlankTab.copy(canGoBack = true).isFreshBlankTab)
        assertFalse(freshBlankTab.copy(canGoForward = true).isFreshBlankTab)
        assertFalse(freshBlankTab.copy(progress = 100).isFreshBlankTab)
    }
}
