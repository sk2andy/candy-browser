package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.TabStack
import dev.sk2andy.materialbrowser.browser.TabStackColor
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class TabStackUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createDialogRequiresNameAndTwoTabs() {
        val createdTabIds = AtomicReference<List<String>>()
        val previewTabId = AtomicReference<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                TabStackCreateDialog(
                    initialTabId = "one",
                    candidates = listOf(
                        BrowserTab(id = "one", lastAccessedAt = 1L, title = "One"),
                        BrowserTab(id = "two", lastAccessedAt = 2L, title = "Two"),
                    ),
                    preselectedTabIds = emptySet(),
                    onCreate = { tabIds, _, _, previewId ->
                        createdTabIds.set(tabIds)
                        previewTabId.set(previewId)
                    },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabStackTestTags.DialogConfirm).assertIsNotEnabled()
        composeRule.onNodeWithTag(TabStackTestTags.Name).performTextInput("Research")
        composeRule.onNodeWithTag(TabStackTestTags.candidate("two")).performClick()
        composeRule.onNodeWithTag(TabStackTestTags.previewChoice("two")).performClick()
        composeRule.onNodeWithTag(TabStackTestTags.DialogConfirm).performClick()

        assertEquals(setOf("one", "two"), createdTabIds.get().toSet())
        assertEquals("two", previewTabId.get())
    }

    @Test
    fun stackMarkerTogglesCollapsedState() {
        val toggledId = AtomicReference<String>()
        val stack = TabStack(
            id = "research",
            profileId = "candy",
            name = "Research",
            color = TabStackColor.Grape,
            tabIds = listOf("one", "two"),
        )
        composeRule.setContent {
            MaterialTheme {
                TabStackMarker(
                    stack = stack,
                    tabId = "one",
                    onToggleCollapsed = { toggledId.set(stack.id) },
                )
            }
        }

        composeRule.onNodeWithTag(TabStackTestTags.marker(stack.id, "one")).performClick()

        assertEquals(stack.id, toggledId.get())
    }

    @Test
    fun stackFolderSupportsCoverflow() = verifyFolderMode(
        mode = TabOverviewMode.Hero,
        layoutTag = TabStackTestTags.FolderHero,
    )

    @Test
    fun stackFolderSupportsGrid() = verifyFolderMode(
        mode = TabOverviewMode.Grid,
        layoutTag = TabStackTestTags.FolderGrid,
    )

    @Test
    fun stackFolderSupportsList() = verifyFolderMode(
        mode = TabOverviewMode.List,
        layoutTag = TabStackTestTags.FolderList,
    )

    private fun verifyFolderMode(mode: TabOverviewMode, layoutTag: String) {
        val selectedTabId = AtomicReference<String>()
        val stack = TabStack(
            id = "research",
            profileId = "candy",
            name = "Research",
            color = TabStackColor.Grape,
            tabIds = listOf("one", "two"),
            previewTabId = "one",
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                TabStackFolderDialog(
                    stack = stack,
                    tabs = listOf(
                        BrowserTab(id = "one", lastAccessedAt = 1L, title = "One"),
                        BrowserTab(id = "two", lastAccessedAt = 2L, title = "Two"),
                    ),
                    mode = mode,
                    previews = emptyMap(),
                    favicons = emptyMap(),
                    favorites = emptyList(),
                    onSelectTab = selectedTabId::set,
                    onPreviewTabChanged = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(layoutTag).assertIsDisplayed()
        composeRule.onNodeWithTag(TabStackTestTags.folderTab("one")).performClick()
        composeRule.mainClock.advanceTimeBy(
            TabStackMotionRules.FOLDER_EXIT_DURATION_MILLIS + 32L,
        )
        composeRule.waitForIdle()

        assertEquals("one", selectedTabId.get())
    }
}
