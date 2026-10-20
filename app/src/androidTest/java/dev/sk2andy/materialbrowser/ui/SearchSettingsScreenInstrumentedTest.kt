package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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
            .performScrollTo()
            .performTextReplacement("https://search.example/searxng")
        assertEquals("https://search.example/searxng", settings.instanceUrl)

        composeRule.onNodeWithTag(SearchSettingsTestTags.SearxngInstanceUrl)
            .performTextReplacement("https://alice:secret@search.example?token=secret")
        assertEquals("https://search.example/searxng", settings.instanceUrl)

        composeRule.onNodeWithTag(SearchSettingsTestTags.fallback(SearchSuggestionProvider.Brave))
            .performScrollTo().performClick().assertIsSelected()
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

        composeRule.onNodeWithTag(SearchSettingsTestTags.HistorySuggestions).performScrollTo().performClick()

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

        composeRule.onNodeWithTag(SearchSettingsTestTags.suggestion(SearchSuggestionProvider.Google))
            .performScrollTo().performClick().assertIsSelected()

        assertEquals(SearchSuggestionProvider.Google, provider)
    }

    @Test
    fun radioChoicesKeepAiInstanceAndFallbackDependencies() {
        var engine by mutableStateOf(SearchEngine.Google)
        var provider by mutableStateOf(SearchSuggestionProvider.None)
        var settings by mutableStateOf(SearxngSettings(instanceUrl = "https://search.example"))
        var settingsWrites = 0
        var aiWrites = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                SearchSettingsPage(
                    searchEngine = engine,
                    searxngSettings = settings,
                    isAiModeToggleVisible = false,
                    searchSuggestionProvider = provider,
                    isHistorySuggestionsEnabled = true,
                    onSearchEngineChanged = { engine = it },
                    onSearxngSettingsChanged = {
                        settings = it
                        settingsWrites++
                    },
                    onAiModeToggleVisibleChanged = { aiWrites++ },
                    onSearchSuggestionProviderChanged = { provider = it },
                    onHistorySuggestionsEnabledChanged = {},
                    onBack = {},
                )
            }
        }
        for (candidate in SearchEngine.entries) {
            composeRule.onNodeWithTag("search_settings_engine_${candidate.stableId}")
                .performScrollTo().performClick().assertIsSelected()
            composeRule.runOnIdle { assertEquals(candidate, engine) }
            val ai = composeRule.onNodeWithTag(SearchSettingsTestTags.AiMode).performScrollTo()
            if (candidate.supportsAiSearch) {
                ai.assertIsEnabled()
            } else {
                ai.assertIsNotEnabled().performClick()
                composeRule.runOnIdle { assertEquals(0, aiWrites) }
            }
            val instance = composeRule.onNodeWithTag(SearchSettingsTestTags.SearxngInstanceUrl)
                .performScrollTo()
            if (candidate == SearchEngine.SearXNG) instance.assertIsEnabled()
            else instance.assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("search_settings_engine_google").performScrollTo().performClick()
        for (candidate in SearchSuggestionProvider.entries) {
            composeRule.onNodeWithTag(SearchSettingsTestTags.suggestion(candidate))
                .performScrollTo().performClick().assertIsSelected()
            composeRule.runOnIdle { assertEquals(candidate, provider) }
            val fallback = composeRule.onNodeWithTag(
                SearchSettingsTestTags.fallback(SearchSuggestionProvider.Brave),
            ).performScrollTo()
            if (candidate == SearchSuggestionProvider.SearXNG) {
                fallback.assertIsEnabled()
            } else {
                fallback.assertIsNotEnabled().performClick()
                composeRule.runOnIdle { assertEquals(0, settingsWrites) }
            }
            val instance = composeRule.onNodeWithTag(SearchSettingsTestTags.SearxngInstanceUrl)
            if (candidate == SearchSuggestionProvider.SearXNG) instance.assertIsEnabled()
            else instance.assertIsNotEnabled()
        }
        composeRule.onNodeWithTag(SearchSettingsTestTags.suggestion(SearchSuggestionProvider.SearXNG))
            .performScrollTo().performClick()
        for (candidate in SearchSuggestionProvider.entries.filterNot { it == SearchSuggestionProvider.SearXNG }) {
            composeRule.onNodeWithTag(SearchSettingsTestTags.fallback(candidate))
                .performScrollTo().performClick().assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(candidate, settings.suggestionFallback)
                assertEquals("https://search.example", settings.instanceUrl)
            }
        }
        composeRule.onNodeWithTag(SearchSettingsTestTags.fallback(SearchSuggestionProvider.SearXNG))
            .assertDoesNotExist()
    }

}
