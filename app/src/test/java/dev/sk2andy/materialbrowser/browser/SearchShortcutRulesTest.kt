package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchShortcutRulesTest {
    @Test
    fun `validates aliases templates and conflicts without case sensitivity`() {
        val valid = SearchShortcut("GH", "https://github.com/search?q=%s")
        assertEquals(SearchShortcut("gh", valid.template), SearchShortcutRules.normalize(valid))
        assertNull(SearchShortcutRules.normalize(SearchShortcut("google", "https://example.com/?q=%s")))
        assertNull(SearchShortcutRules.normalize(SearchShortcut("two words", "https://example.com/?q=%s")))
        assertNull(SearchShortcutRules.normalize(SearchShortcut("gh", "https://example.com/?q=%s&x=%s")))
        assertNull(SearchShortcutRules.normalize(SearchShortcut("bad", "javascript:alert(%s)")))
        assertNull(SearchShortcutRules.add(listOf(valid), "gh", "https://other.example/?q=%s"))
    }

    @Test
    fun `sanitize drops invalid and duplicate persisted records`() {
        assertEquals(
            listOf(SearchShortcut("gh", "https://github.com/search?q=%s")),
            SearchShortcutRules.sanitize(
                listOf(
                    SearchShortcut("GH", "https://github.com/search?q=%s"),
                    SearchShortcut("gh", "https://other.example/?q=%s"),
                    SearchShortcut("bad", "file:///tmp/%s"),
                ),
            ),
        )
        assertTrue(SearchShortcutRules.sanitize(emptyList()).isEmpty())
    }

    @Test
    fun `shortcut resolution rejects control characters and oversized input`() {
        val shortcut = listOf(SearchShortcut("gh", "https://github.com/search?q=%s"))
        assertNull(SearchShortcutRules.resolve("gh query\r\nx", shortcut))
        assertNull(SearchShortcutRules.resolve("gh ${"x".repeat(16_384)}", shortcut))
    }
}
