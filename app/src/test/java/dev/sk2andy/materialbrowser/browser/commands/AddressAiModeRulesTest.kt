package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearchMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressAiModeRulesTest {
    @Test
    fun `toggle appears only for enabled ai capable search queries`() {
        assertTrue(
            AddressAiModeRules.isToggleVisible(
                input = "why is the sky blue",
                searchEngine = SearchEngine.Google,
                settingEnabled = true,
            ),
        )
        assertFalse(
            AddressAiModeRules.isToggleVisible(
                input = "why is the sky blue",
                searchEngine = SearchEngine.Google,
                settingEnabled = false,
            ),
        )
        assertFalse(
            AddressAiModeRules.isToggleVisible(
                input = "why is the sky blue",
                searchEngine = SearchEngine.DuckDuckGo,
                settingEnabled = true,
            ),
        )
    }

    @Test
    fun `toggle stays hidden for addresses empty input and commands`() {
        listOf("example.com", "https://example.com", "", "  ", "> reload").forEach { input ->
            assertFalse(
                AddressAiModeRules.isToggleVisible(
                    input = input,
                    searchEngine = SearchEngine.Google,
                    settingEnabled = true,
                ),
            )
        }
    }

    @Test
    fun `ai search mode requires visible selected toggle`() {
        assertSame(SearchMode.Ai, AddressAiModeRules.searchMode(true, true))
        assertSame(SearchMode.Web, AddressAiModeRules.searchMode(true, false))
        assertSame(SearchMode.Web, AddressAiModeRules.searchMode(false, true))
    }
}
