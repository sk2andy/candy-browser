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
import org.junit.Assert.assertFalse
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
                    isHistorySuggestionsEnabled = true,
                    onSearchEngineChanged = {},
                    onSearxngSettingsChanged = { settings = it },
                    onAiModeToggleVisibleChanged = {},
                    onSearchSuggestionProviderChanged = {},
                    onHistorySuggestionsEnabledChanged = {},
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

    @Test
    fun historySuggestionsSwitchEmitsDisabledState() {
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                SearchSettingsPage(
                    searchEngine = SearchEngine.Google,
                    searxngSettings = SearxngSettings(),
                    isAiModeToggleVisible = false,
                    searchSuggestionProvider = SearchSuggestionProvider.Google,
                    isHistorySuggestionsEnabled = enabled,
                    onSearchEngineChanged = {},
                    onSearxngSettingsChanged = {},
                    onAiModeToggleVisibleChanged = {},
                    onSearchSuggestionProviderChanged = {},
                    onHistorySuggestionsEnabledChanged = { enabled = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchSettingsTestTags.HistorySuggestions).performClick()

        assertFalse(enabled)
    }

    @Test
    fun googleCanBeSelectedAsSuggestionProvider() {
        var provider by mutableStateOf(SearchSuggestionProvider.DuckDuckGo)
        composeRule.setContent {
            MaterialBrowserTheme {
                SearchSettingsPage(
                    searchEngine = SearchEngine.DuckDuckGo,
                    searxngSettings = SearxngSettings(),
                    isAiModeToggleVisible = false,
                    searchSuggestionProvider = provider,
                    isHistorySuggestionsEnabled = true,
                    onSearchEngineChanged = {},
                    onSearxngSettingsChanged = {},
                    onAiModeToggleVisibleChanged = {},
                    onSearchSuggestionProviderChanged = { provider = it },
                    onHistorySuggestionsEnabledChanged = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchSettingsTestTags.SuggestionProvider).performClick()
        composeRule.onNodeWithText("Google").performClick()

        assertEquals(SearchSuggestionProvider.Google, provider)
    }
}
