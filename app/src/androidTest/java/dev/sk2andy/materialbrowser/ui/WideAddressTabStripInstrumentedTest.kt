package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideAddressTabStripInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inactiveTabOpensAndCurrentTabOpensAddressEditor() {
        val selectedTabId = mutableStateOf("first")
        val openedTabs = mutableListOf<String>()
        var addressEditorOpens = 0
        setStrip(
            tabs = listOf(tab("first"), tab("second")),
            selectedTabId = selectedTabId.value,
            onTabClick = { id ->
                openedTabs += id
                selectedTabId.value = id
            },
            onCurrentTabClick = { addressEditorOpens++ },
            selectedTabState = selectedTabId,
        )

        composeRule.onNodeWithTag(tabTag("first")).assertIsSelected()
        composeRule.onNodeWithTag(tabTag("second"))
            .assertIsNotSelected()
            .performClick()
        composeRule.onNodeWithTag(tabTag("second")).assertIsSelected().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("second"), openedTabs)
            assertEquals(1, addressEditorOpens)
        }
    }

    @Test
    fun closeButtonClosesOnlyRequestedClosableTab() {
        val closedTabs = mutableListOf<String>()
        val openedTabs = mutableListOf<String>()
        var addressEditorOpens = 0
        setStrip(
            tabs = listOf(tab("pinned", canClose = false), tab("regular")),
            selectedTabId = "pinned",
            onTabClick = { openedTabs += it },
            onCurrentTabClick = { addressEditorOpens++ },
            onCloseTab = { closedTabs += it },
        )

        composeRule.onNodeWithTag(closeTag("pinned")).assertDoesNotExist()
        composeRule.onNodeWithTag(closeTag("regular")).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("regular"), closedTabs)
            assertEquals(emptyList<String>(), openedTabs)
            assertEquals(0, addressEditorOpens)
        }
    }

    @Test
    fun horizontalSwipeRevealsTabsOutsideInitialViewport() {
        val tabs = (0 until 12).map { index -> tab("tab-$index") }
        setStrip(
            tabs = tabs,
            selectedTabId = tabs.first().id,
            onTabClick = {},
            onCurrentTabClick = {},
        )

        composeRule.onNodeWithTag(tabTag("tab-0")).assertIsDisplayed()
        composeRule.onNodeWithTag(tabTag("tab-11")).assertDoesNotExist()
        repeat(8) {
            composeRule.onNodeWithTag(WideAddressTabStripTestTags.Strip)
                .performTouchInput { swipeLeft() }
        }

        composeRule.onNodeWithTag(tabTag("tab-11")).assertIsDisplayed()
    }

    @Test
    fun currentTabRetainsLongPressAction() {
        var longPresses = 0
        setStrip(
            tabs = listOf(tab("first"), tab("second")),
            selectedTabId = "first",
            onTabClick = {},
            onCurrentTabClick = {},
            onCurrentTabLongPress = { longPresses++ },
        )

        composeRule.onNodeWithTag(tabTag("first"))
            .performTouchInput { longClick() }

        composeRule.runOnIdle { assertEquals(1, longPresses) }
    }

    @Test
    fun newlyAddedSelectedTabScrollsIntoView() {
        val initialTabs = (0 until 7).map { index ->
            tab("tab-$index").copy(title = "Browser tab $index with a long title")
        }
        val tabs = mutableStateOf(initialTabs)
        val selectedTabId = mutableStateOf("tab-5")
        setStrip(
            tabs = initialTabs,
            selectedTabId = selectedTabId.value,
            onTabClick = {},
            onCurrentTabClick = {},
            selectedTabState = selectedTabId,
            tabsState = tabs,
        )
        composeRule.onNodeWithTag(tabTag("tab-5")).assertIsDisplayed()

        composeRule.runOnIdle {
            tabs.value = initialTabs + tab("tab-7").copy(title = "Newly opened tab")
            selectedTabId.value = "tab-7"
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                val selected = composeRule.onNodeWithTag(tabTag("tab-7"))
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
                val strip = composeRule.onNodeWithTag(WideAddressTabStripTestTags.Strip)
                    .getUnclippedBoundsInRoot()
                selected.left >= strip.left - 1.dp && selected.right <= strip.right + 1.dp
            }.getOrDefault(false)
        }
        val selected = composeRule.onNodeWithTag(tabTag("tab-7"))
            .assertIsSelected()
            .getUnclippedBoundsInRoot()
        val strip = composeRule.onNodeWithTag(WideAddressTabStripTestTags.Strip)
            .getUnclippedBoundsInRoot()
        assertTrue(selected.left >= strip.left - 1.dp)
        assertTrue(selected.right <= strip.right + 1.dp)
    }

    @Test
    fun selectedLastTabRemainsFullyVisibleWhenItsTitleGrows() {
        val initialTabs = (0 until 7).map { index ->
            tab("tab-$index").copy(
                title = if (index == 6) "New tab" else "Browser tab $index with a long title",
            )
        }
        val tabs = mutableStateOf(initialTabs)
        setStrip(
            tabs = initialTabs,
            selectedTabId = "tab-6",
            onTabClick = {},
            onCurrentTabClick = {},
            tabsState = tabs,
        )
        composeRule.onNodeWithTag(tabTag("tab-6")).assertIsDisplayed()

        composeRule.runOnIdle {
            tabs.value = initialTabs.dropLast(1) + initialTabs.last().copy(
                title = "A website with a long title that expands the selected tab",
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                val selected = composeRule.onNodeWithTag(tabTag("tab-6"))
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
                val close = composeRule.onNodeWithTag(closeTag("tab-6"))
                    .getUnclippedBoundsInRoot()
                val strip = composeRule.onNodeWithTag(WideAddressTabStripTestTags.Strip)
                    .getUnclippedBoundsInRoot()
                selected.left >= strip.left - 1.dp &&
                    selected.right <= strip.right + 1.dp &&
                    close.right <= strip.right + 1.dp
            }.getOrDefault(false)
        }
    }

    private fun setStrip(
        tabs: List<WideAddressTabItem>,
        selectedTabId: String,
        onTabClick: (String) -> Unit,
        onCurrentTabClick: () -> Unit,
        onCurrentTabLongPress: () -> Unit = {},
        onCloseTab: (String) -> Unit = {},
        selectedTabState: State<String>? = null,
        tabsState: State<List<WideAddressTabItem>>? = null,
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.size(width = 420.dp, height = 56.dp)) {
                    WideAddressTabStrip(
                        tabs = tabsState?.value ?: tabs,
                        selectedTabId = selectedTabState?.value ?: selectedTabId,
                        onTabClick = onTabClick,
                        onCurrentTabClick = onCurrentTabClick,
                        onCurrentTabLongPress = onCurrentTabLongPress,
                        currentTabLongPressEnabled = true,
                        currentTabLongPressLabel = "Open Reader Studio",
                        onCloseTab = onCloseTab,
                    )
                }
            }
        }
    }

    private fun tab(id: String, canClose: Boolean = true) = WideAddressTabItem(
        id = id,
        title = id,
        favicon = null,
        canClose = canClose,
    )

    private fun tabTag(id: String) = WideAddressTabStripTestTags.TabPrefix + id

    private fun closeTag(id: String) = WideAddressTabStripTestTags.ClosePrefix + id
}
