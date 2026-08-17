package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearchMode

object AddressAiModeRules {
    fun isToggleVisible(
        input: String,
        searchEngine: SearchEngine,
        settingEnabled: Boolean,
    ): Boolean = settingEnabled &&
        searchEngine.supportsAiSearch &&
        !CommandMatcher.isExplicitCommandQuery(input) &&
        AddressResolver.isSearchQuery(input)

    fun searchMode(toggleVisible: Boolean, toggleSelected: Boolean): SearchMode =
        if (toggleVisible && toggleSelected) SearchMode.Ai else SearchMode.Web
}
