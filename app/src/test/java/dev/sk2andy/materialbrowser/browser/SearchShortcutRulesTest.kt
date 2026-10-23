package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchShortcutRulesTest {
    private val github = SearchShortcut("gh", "https://github.com/search?q=%s")

    @Test
    fun `bang and legacy shortcuts resolve encoded terms through the same template`() {
        val shortcuts = listOf(github)
        assertEquals(github, SearchShortcutRules.normalize(github.copy(keyword = " !GH ")))
        listOf("!gh compose & ✓", "gh compose & ✓", "!GH compose & ✓").forEach { input ->
            assertEquals(
                "https://github.com/search?q=compose%20%26%20%E2%9C%93",
                SearchShortcutRules.resolve(input, shortcuts),
            )
        }
        assertNull(SearchShortcutRules.resolve("!gh ", shortcuts))
        assertNull(SearchShortcutRules.resolve("!unknown compose", shortcuts))
    }

    @Test
    fun `chip matching waits for space and accepts empty query without matching ordinary text`() {
        val shortcuts = listOf(github, github.copy(keyword = "github"))
        assertNull(SearchShortcutRules.match("!gh", shortcuts))
        assertNull(SearchShortcutRules.match("!g", shortcuts))
        assertNull(SearchShortcutRules.match("gh compose", shortcuts))
        assertNull(SearchShortcutRules.match("!unknown compose", shortcuts))
        assertEquals(SearchShortcutMatch(github, ""), SearchShortcutRules.match("!GH ", shortcuts))
        assertEquals("compose  ui", SearchShortcutRules.match("!gh compose  ui", shortcuts)?.query)
        assertEquals("github", SearchShortcutRules.match("!github compose", shortcuts)?.shortcut?.keyword)
        assertNull(SearchShortcutRules.match("!gh query\n", shortcuts))
    }

    @Test
    fun `editing a shortcut preserves order and rejects conflicting or invalid definitions`() {
        val youtube = SearchShortcut("yt", "https://www.youtube.com/results?search_query=%s")
        val shortcuts = listOf(github, youtube)
        assertEquals(
            listOf(github.copy(keyword = "code"), youtube),
            SearchShortcutRules.update(shortcuts, "gh", "!code", github.template),
        )
        assertEquals(shortcuts, SearchShortcutRules.update(shortcuts, "GH", "!gh", github.template))
        assertNull(SearchShortcutRules.update(shortcuts, "gh", "!yt", github.template))
        assertNull(SearchShortcutRules.update(shortcuts, "missing", "code", github.template))
        assertNull(SearchShortcutRules.update(shortcuts, "gh", "gh", "javascript:%s"))
    }

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
