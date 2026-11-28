package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentSafeAreaSessionTest {
    private val regular = BrowserTab(id = "regular", lastAccessedAt = 0, profileId = "one")

    @Test
    fun `remembered host applies to other paths and tabs but not other hosts`() {
        val session = WebContentSafeAreaSession()
        session.remember(regular, "https://Example.com/product")

        assertTrue(session.contains(regular.copy(id = "another"), "http://example.com/checkout"))
        assertFalse(session.contains(regular, "https://other.example.com/"))
        assertFalse(session.contains(regular, "https://example.org/"))
        assertFalse(session.contains(regular, "about:blank"))
    }

    @Test
    fun `private and other profile hosts remain separate and private state is cleared`() {
        val session = WebContentSafeAreaSession()
        val privateTab = regular.copy(id = "private", isIncognito = true)
        session.remember(privateTab, "https://example.com/")

        assertFalse(session.contains(regular, "https://example.com/"))
        assertFalse(session.contains(privateTab.copy(profileId = "two"), "https://example.com/"))
        session.remember(regular, "https://example.com/")
        session.clearPrivate()
        assertFalse(session.contains(privateTab, "https://example.com/"))
        assertTrue(session.contains(regular, "https://example.com/"))
        session.clear()
        assertFalse(session.contains(regular, "https://example.com/"))
    }
}
