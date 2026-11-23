package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinnedTabsSheetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sheetSelectsTheExistingTabIdAndMarksTheCurrentTab() {
        var selectedId: String? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                PinnedTabsSheet(
                    tabs = listOf(
                        BrowserTab(id = "one", lastAccessedAt = 0, isPinned = true, title = "News"),
                        BrowserTab(id = "two", lastAccessedAt = 0, isPinned = true, title = "Mail"),
                    ),
                    selectedTabId = "one",
                    onSelect = { selectedId = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("News").assertIsSelected()
        composeRule.onNodeWithText("Mail").performClick()

        composeRule.runOnIdle { assertEquals("two", selectedId) }
    }

    @Test
    fun sheetExplainsEmptyState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialBrowserTheme {
                PinnedTabsSheet(
                    tabs = emptyList(),
                    selectedTabId = "not-pinned",
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.pinned_tabs_empty)).assertExists()
    }
}
