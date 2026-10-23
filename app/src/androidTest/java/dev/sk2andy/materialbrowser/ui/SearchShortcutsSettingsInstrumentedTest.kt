package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SearchShortcut
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchShortcutsSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var shortcuts by mutableStateOf(emptyList<SearchShortcut>())

    @Test
    fun customShortcutCanBeCreatedEditedAndRemoved() {
        shortcuts = listOf(SearchShortcut("gh", "https://github.com/search?q=%s"))
        showSettings()

        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Add).performScrollTo().performClick()
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Keyword).performTextReplacement("!docs")
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Template)
            .performTextReplacement("https://docs.example/search?q=%s")
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Save).assertIsEnabled().performClick()
        composeRule.onNodeWithText("!docs").assertExists()
        composeRule.runOnIdle {
            assertEquals(SearchShortcut("docs", "https://docs.example/search?q=%s"), shortcuts.last())
        }

        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.edit("docs")).performScrollTo().performClick()
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Keyword).assertTextContains("!docs")
            .performTextReplacement("!work")
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Template)
            .performTextReplacement("https://work.example/find?query=%s")
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Save).performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    SearchShortcut("gh", "https://github.com/search?q=%s"),
                    SearchShortcut("work", "https://work.example/find?query=%s"),
                ),
                shortcuts,
            )
        }

        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.remove("work")).performScrollTo().performClick()
        composeRule.onNodeWithText("!work").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(SearchShortcut("gh", "https://github.com/search?q=%s")), shortcuts)
        }
    }

    @Test
    fun duplicateAndInvalidTemplatesCannotBeSaved() {
        val original = listOf(
            SearchShortcut("gh", "https://github.com/search?q=%s"),
            SearchShortcut("yt", "https://www.youtube.com/results?search_query=%s"),
        )
        shortcuts = original
        showSettings()

        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.edit("yt")).performScrollTo().performClick()
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Keyword).performTextReplacement("!GH")
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Save).assertIsNotEnabled()
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Keyword).performTextReplacement("!yt")
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Template)
            .performTextReplacement("https://example.com/search")
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Save).assertIsNotEnabled()
        composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Cancel).performClick()

        composeRule.runOnIdle { assertEquals(original, shortcuts) }
    }

    @Test
    fun websiteExamplesFillEditableDraftsBeforeSaving() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showSettings()

        listOf(
            R.string.settings_search_shortcut_github to
                SearchShortcut("gh", "https://github.com/search?q=%s"),
            R.string.settings_search_shortcut_youtube to
                SearchShortcut("yt", "https://www.youtube.com/results?search_query=%s"),
            R.string.settings_search_shortcut_amazon to
                SearchShortcut("amazon", "https://www.amazon.de/s?k=%s"),
        ).forEach { (label, expected) ->
            composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Add).performScrollTo().performClick()
            composeRule.onNodeWithText(context.getString(label)).performScrollTo().performClick()
            composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Keyword)
                .assertTextContains("!${expected.keyword}")
            composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Template).assertTextContains(expected.template)
            composeRule.onNodeWithTag(SearchShortcutsSettingsTestTags.Save).performClick()
            composeRule.runOnIdle { assertEquals(expected, shortcuts.last()) }
        }
    }

    private fun showSettings() {
        composeRule.setContent {
            MaterialBrowserTheme {
                SettingsPage(title = "Search", onBack = {}) {
                    SearchShortcutsSettings(shortcuts = shortcuts, onShortcutsChanged = { shortcuts = it })
                }
            }
        }
    }
}
