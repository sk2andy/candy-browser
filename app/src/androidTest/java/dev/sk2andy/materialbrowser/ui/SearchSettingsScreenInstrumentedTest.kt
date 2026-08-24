package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searxngConfigurationUpdatesInstanceAndFallback() {
        var settings by mutableStateOf(SearxngSettings())
        composeRule.setContent {
            MaterialBrowserTheme {
                SearchSettingsPage(
                    searchEngine = SearchEngine.SearXNG,
                    searxngSettings = settings,
                    isAiModeToggleVisible = false,
                    searchSuggestionProvider = SearchSuggestionProvider.SearXNG,
                    onSearchEngineChanged = {},
                    onSearxngSettingsChanged = { settings = it },
                    onAiModeToggleVisibleChanged = {},
                    onSearchSuggestionProviderChanged = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchSettingsTestTags.SearxngInstanceUrl)
            .assertExists()
            .performTextReplacement("https://search.example/searxng")
        assertEquals("https://search.example/searxng", settings.instanceUrl)

        composeRule.onNodeWithTag(SearchSettingsTestTags.SearxngInstanceUrl)
            .performTextReplacement("https://alice:secret@search.example?token=secret")
        assertEquals("https://search.example/searxng", settings.instanceUrl)

        composeRule.onNodeWithTag(SearchSettingsTestTags.SearxngFallback).performClick()
        composeRule.onNodeWithText("Brave Search").performClick()
        assertEquals(SearchSuggestionProvider.Brave, settings.suggestionFallback)
    }
}
