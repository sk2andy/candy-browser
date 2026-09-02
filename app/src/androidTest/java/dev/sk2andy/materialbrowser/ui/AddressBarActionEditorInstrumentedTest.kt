package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarActionSide
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarActionEditorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accessibilityActionsMoveBetweenPaletteAndPreviewWithoutDuplicates() {
        var layout by mutableStateOf(AddressBarActionLayout.Default)
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        performCustomAction(AddressBarAction.Favorite, index = 0)
        composeRule.runOnIdle {
            assertEquals(
                listOf(AddressBarAction.Tabs, AddressBarAction.Favorite),
                layout.beforeAddress,
            )
            assertEquals(listOf(AddressBarAction.NewTab), layout.afterAddress)
        }

        performCustomAction(AddressBarAction.Favorite, index = 2)
        composeRule.runOnIdle { assertEquals(AddressBarActionLayout.Default, layout) }
    }

    @Test
    fun fullToolbarRejectsAdditionalPaletteActionAndAnnouncesReason() {
        var layout by mutableStateOf(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs, AddressBarAction.Back),
                afterAddress = listOf(AddressBarAction.NewTab),
            ),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        performCustomAction(AddressBarAction.Print, index = 0)

        composeRule.onNodeWithTag(AddressBarActionEditorTestTags.FullMessage).assertExists()
        composeRule.onNodeWithText(FullMessage).assertExists()
        composeRule.runOnIdle {
            assertEquals(3, layout.beforeAddress.size + layout.afterAddress.size)
        }
    }

    @Test
    fun addressAndMoreAnchorsAlwaysExist() {
        setEditorContent(layout = { AddressBarActionLayout(emptyList(), emptyList()) })

        composeRule.onNodeWithTag(AddressBarActionEditorTestTags.Address).assertExists()
        composeRule.onNodeWithTag(AddressBarActionEditorTestTags.More).assertExists()
    }

    @Test
    fun draggingOnlyActionOnSideShowsOneCanonicalVacatedTarget() {
        setEditorContent(layout = { AddressBarActionLayout.Default })

        val sourceNode = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(AddressBarAction.Tabs),
            useUnmergedTree = true,
        )
        sourceNode.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveBy(Offset(80f, 80f), delayMillis = 600L)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.dropIndicators(
                listOf(
                    AddressBarActionEditorTarget(AddressBarActionSide.BeforeAddress, 0),
                    AddressBarActionEditorTarget(AddressBarActionSide.AfterAddress, 0),
                    AddressBarActionEditorTarget(AddressBarActionSide.AfterAddress, 1),
                ),
            ),
            useUnmergedTree = true,
        ).assertExists()

        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun longPressDragAddsPaletteActionAtVisibleToolbarSlot() {
        var layout by mutableStateOf(AddressBarActionLayout.Default)
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        val sourceNode = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(AddressBarAction.Favorite),
            useUnmergedTree = true,
        )
        val sourceBounds = sourceNode.fetchSemanticsNode().boundsInRoot
        val tabsBounds = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(AddressBarAction.Tabs),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val destinationInSource = Offset(
            x = tabsBounds.right - 3f - sourceBounds.left,
            y = tabsBounds.center.y - sourceBounds.top,
        )

        sourceNode.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(destinationInSource, delayMillis = 600L)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            layout.beforeAddress == listOf(AddressBarAction.Tabs, AddressBarAction.Favorite)
        }
    }

    @Test
    fun longPressDragMovesToolbarActionAcrossAddressAndCompletesMeasuredHandoff() {
        var layout by mutableStateOf(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs, AddressBarAction.Pin),
                afterAddress = listOf(AddressBarAction.NewTab),
            ),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        val sourceNode = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(AddressBarAction.Tabs),
            useUnmergedTree = true,
        )
        val sourceBounds = sourceNode.fetchSemanticsNode().boundsInRoot
        val newTabBounds = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(AddressBarAction.NewTab),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val destinationInSource = Offset(
            x = newTabBounds.right - 3f - sourceBounds.left,
            y = newTabBounds.center.y - sourceBounds.top,
        )

        sourceNode.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(destinationInSource, delayMillis = 600L)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            layout == AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Pin),
                afterAddress = listOf(AddressBarAction.NewTab, AddressBarAction.Tabs),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag(
                    AddressBarActionEditorTestTags.DragOverlay,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
            }.isFailure
        }
    }

    @Test
    fun longPressDragBackToPaletteCompletesAnimatedHandoff() {
        var layout by mutableStateOf(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs, AddressBarAction.Favorite),
                afterAddress = listOf(AddressBarAction.NewTab),
            ),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        val sourceNode = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(AddressBarAction.Favorite),
            useUnmergedTree = true,
        )
        val sourceBounds = sourceNode.fetchSemanticsNode().boundsInRoot
        val paletteBounds = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.Palette,
        ).fetchSemanticsNode().boundsInRoot
        val destinationInSource = paletteBounds.center - sourceBounds.topLeft

        sourceNode.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(destinationInSource, delayMillis = 600L)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            layout == AddressBarActionLayout.Default
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag(
                    AddressBarActionEditorTestTags.DragOverlay,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
            }.isFailure
        }
        composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(AddressBarAction.Favorite),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun setEditorContent(
        layout: () -> AddressBarActionLayout,
        onLayoutChanged: (AddressBarActionLayout) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                AddressBarActionEditorPage(
                    layout = layout(),
                    tabCount = 7,
                    onLayoutChanged = onLayoutChanged,
                    onBack = {},
                    backLabel = "Back",
                    title = "Address buttons",
                    instructions = "Drag buttons",
                    availableTitle = "Available",
                    beforeLabel = "Before",
                    afterLabel = "After",
                    moreLabel = "More",
                    fullMessage = FullMessage,
                    actionLabel = AddressBarAction::wireValue,
                )
            }
        }
    }

    private fun performCustomAction(action: AddressBarAction, index: Int) {
        val node = composeRule.onNodeWithTag(
            AddressBarActionEditorTestTags.action(action),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val customActions = node.config[SemanticsActions.CustomActions]
        composeRule.runOnIdle {
            customActions[index].action()
        }
    }

    private companion object {
        const val FullMessage = "Toolbar full"
    }
}
