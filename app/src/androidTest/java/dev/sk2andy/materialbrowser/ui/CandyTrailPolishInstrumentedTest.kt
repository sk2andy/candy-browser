package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkLifecycle
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyTrailPolishInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tab = BrowserTab(id = "polish", lastAccessedAt = 1L, title = "Trail", url = "https://root.example")
    private val initialTrail = CandyTrail(
        tabId = tab.id,
        nodes = listOf(node("root", null), node("side", "root"), node("leaf", "side")),
        currentNodeId = "root",
        forks = listOf(
            CandyTrailFork(
                id = "fork",
                originTabId = tab.id,
                originNodeId = "side",
                destinationTabId = "destination",
                profileId = tab.profileId,
                isIncognito = false,
                url = "https://fork.example",
                title = "Fork",
                createdAt = 1L,
                updatedAt = 1L,
                lifecycle = CandyTrailForkLifecycle.Open,
            ),
        ),
    )

    @Test
    fun nodeSelectionCommitsWithoutWaitingForAnimationClock() {
        var selected: String? = null
        var finished = 0
        render(mutableStateOf(initialTrail), onSelect = { selected = it; true }, onFinished = { finished++ })
        compose.onNodeWithText(context.getString(R.string.candy_trail_list_view)).performClick()
        compose.onNodeWithText("root").assertExists()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("root").performClick()
        compose.runOnIdle {
            assertEquals("root", selected)
            assertEquals(1, finished)
        }
    }

    @Test
    fun removingNodesAndForksWhileGraphMountedDoesNotUseStaleLayoutIdentities() {
        val state = mutableStateOf(initialTrail)
        render(state)
        compose.runOnIdle { state.value = CandyTrail(tabId = tab.id) }
        compose.onNodeWithText(context.getString(R.string.candy_trail_empty)).assertExists()
    }

    @Test
    fun individualSideBranchCanBeFoldedAndExpandedWithoutHidingCurrentPage() {
        render(mutableStateOf(initialTrail))
        compose.onNodeWithText(context.getString(R.string.candy_trail_list_view)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_candy_trail_node_actions, "root")).performClick()
        compose.onNodeWithText(context.getString(R.string.candy_trail_collapse_branch)).performClick()
        compose.onNodeWithText("root").assertExists()
        compose.onNodeWithText("side").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.candy_trail_hidden_pages, 3)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_candy_trail_node_actions, "root")).performClick()
        compose.onNodeWithText(context.getString(R.string.candy_trail_expand_branch)).performClick()
        compose.onNodeWithText("side").assertExists()
    }

    private fun render(
        state: MutableState<CandyTrail>,
        onSelect: (String) -> Boolean = { false },
        onFinished: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialBrowserTheme {
                CandyTrailScreen(
                    tab = tab,
                    trail = state.value,
                    favicon = null,
                    forkFavicons = emptyMap(),
                    predictiveBackProgress = 0f,
                    predictiveBackEdgeSign = 1,
                    onSelectNode = onSelect,
                    onNodeSelectionFinished = onFinished,
                    onForkNode = { null },
                    onForkCreationFinished = {},
                    onSelectFork = { null },
                    onForkSelectionFinished = {},
                    onDismiss = {},
                )
            }
        }
    }

    private fun node(id: String, parentId: String?) = CandyTrailNode(id, parentId, "https://$id.example", id, 1L)
}
