package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.LinkPeekAction
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkPeekActionEditorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun previewKeepsFixedPulsingPlusInThirdSlot() {
        setEditorContent(layout = { LinkPeekActionLayout.Default })

        val copy = actionBounds(LinkPeekAction.Copy)
        val private = actionBounds(LinkPeekAction.OpenPrivate)
        val plus = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.FixedPlus,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val share = actionBounds(LinkPeekAction.Share)

        assertTrue(copy.center.x < private.center.x)
        assertTrue(private.center.x < plus.center.x)
        assertTrue(plus.center.x < share.center.x)
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.FixedPlus,
            useUnmergedTree = true,
        ).assertContentDescriptionEquals(FixedPlusLabel)
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.FixedPlusPulseRing,
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun fixedPlusRingActuallyPulsesWithoutMovingToolbarSlots() {
        composeRule.mainClock.autoAdvance = false
        try {
            setEditorContent(layout = { LinkPeekActionLayout.Default })
            composeRule.mainClock.advanceTimeByFrame()
            val restingRingWidth = composeRule.onNodeWithTag(
                LinkPeekActionEditorTestTags.FixedPlusPulseRing,
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot.width
            val restingActionBounds =
                LinkPeekActionLayout.Default.actions.filterNotNull().associateWith(::actionBounds)

            composeRule.mainClock.advanceTimeBy(220L)

            val guidingRingWidth = composeRule.onNodeWithTag(
                LinkPeekActionEditorTestTags.FixedPlusPulseRing,
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot.width
            assertTrue(guidingRingWidth > restingRingWidth)
            restingActionBounds.forEach { (action, bounds) ->
                assertEquals(bounds, actionBounds(action))
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun emptyLayoutStillShowsThreeEmptySlotsAndFixedPlus() {
        setEditorContent(layout = { LinkPeekActionLayout(emptyList()) })

        listOf(1, 2, 4).forEachIndexed { actionIndex, visualSlot ->
            composeRule.onNodeWithTag(
                LinkPeekActionEditorTestTags.slot(actionIndex),
                useUnmergedTree = true,
            ).assertContentDescriptionEquals("Empty slot $visualSlot")
        }
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.FixedPlus,
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun draggingToolbarActionToPaletteLeavesEmptySlot() {
        var layout by mutableStateOf(LinkPeekActionLayout.Default)
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })
        val originalCenters = toolbarCenters()

        val source = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(LinkPeekAction.Copy),
            useUnmergedTree = true,
        )
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val paletteBounds = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.Palette,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val destinationInSource = paletteBounds.center - sourceBounds.topLeft

        source.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(destinationInSource, delayMillis = 600L)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            layout.actions == listOf(
                null,
                LinkPeekAction.OpenPrivate,
                LinkPeekAction.Share,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag(
                    LinkPeekActionEditorTestTags.DragOverlay,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
            }.isFailure
        }
        composeRule.onNodeWithTag(LinkPeekActionEditorTestTags.action(LinkPeekAction.Copy))
            .assertExists()
        assertEquals(originalCenters, toolbarCenters())
    }

    @Test
    fun paletteActionFillsEmptySlotAndCompletesMeasuredHandoff() {
        var layout by mutableStateOf(
            LinkPeekActionLayout(listOf(null, LinkPeekAction.OpenPrivate, LinkPeekAction.Share)),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        val source = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(LinkPeekAction.Favorite),
            useUnmergedTree = true,
        )
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetBounds = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.slot(0),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

        source.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(targetBounds.center - sourceBounds.topLeft, delayMillis = 600L)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            layout.actions == listOf(
                LinkPeekAction.Favorite,
                LinkPeekAction.OpenPrivate,
                LinkPeekAction.Share,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag(
                    LinkPeekActionEditorTestTags.DragOverlay,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
            }.isFailure
        }
        assertEquals(targetBounds.center, actionBounds(LinkPeekAction.Favorite).center)
    }

    @Test
    fun toolbarActionMovesIntoEmptySlotWithoutCompactingOthers() {
        var layout by mutableStateOf(
            LinkPeekActionLayout(listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share)),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })
        val originalCenters = toolbarCenters()

        val source = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(LinkPeekAction.Copy),
            useUnmergedTree = true,
        )
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetBounds = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.slot(1),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

        source.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(targetBounds.center - sourceBounds.topLeft, delayMillis = 600L)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            layout.actions == listOf(null, LinkPeekAction.Copy, LinkPeekAction.Share)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag(
                    LinkPeekActionEditorTestTags.DragOverlay,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
            }.isFailure
        }
        assertEquals(originalCenters, toolbarCenters())
    }

    @Test
    fun accessibilityRemoveLeavesEmptySlot() {
        var layout by mutableStateOf(LinkPeekActionLayout.Default)
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        performCustomAction(LinkPeekAction.OpenPrivate, index = 0)

        composeRule.runOnIdle {
            assertEquals(
                LinkPeekActionLayout(
                    listOf(
                        LinkPeekAction.Copy,
                        null,
                        LinkPeekAction.Share,
                    ),
                ),
                layout,
            )
        }
    }

    @Test
    fun accessibilityActionFromPaletteFillsEmptySlot() {
        var layout by mutableStateOf(
            LinkPeekActionLayout(listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share)),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        performCustomAction(LinkPeekAction.Snooze, index = 0)

        composeRule.runOnIdle {
            assertEquals(
                LinkPeekActionLayout(
                    listOf(
                        LinkPeekAction.Copy,
                        LinkPeekAction.Snooze,
                        LinkPeekAction.Share,
                    ),
                ),
                layout,
            )
        }
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(LinkPeekAction.OpenPrivate),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun accessibilityActionsOfferOnlyEmptySlotsAndRemoval() {
        var layout by mutableStateOf(
            LinkPeekActionLayout(listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share)),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        val node = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(LinkPeekAction.Copy),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        assertEquals(2, node.config[SemanticsActions.CustomActions].size)
        performCustomAction(LinkPeekAction.Copy, index = 0)

        composeRule.runOnIdle {
            assertEquals(
                LinkPeekActionLayout(
                    listOf(
                        null,
                        LinkPeekAction.Copy,
                        LinkPeekAction.Share,
                    ),
                ),
                layout,
            )
        }
    }

    @Test
    fun dragIndicatorsOfferOnlyEmptySlots() {
        var layout by mutableStateOf(
            LinkPeekActionLayout(listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share)),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        val source = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(LinkPeekAction.ReaderLater),
            useUnmergedTree = true,
        )
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val emptyBounds = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.slot(1),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

        source.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(emptyBounds.center - sourceBounds.topLeft, delayMillis = 600L)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.dropIndicators(
                listOf(LinkPeekActionEditorTarget.ActionSlot(1)),
            ),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun longPressDragFromPaletteFillsEmptySlotAndCompletesMeasuredHandoff() {
        var layout by mutableStateOf(
            LinkPeekActionLayout(listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share)),
        )
        setEditorContent(layout = { layout }, onLayoutChanged = { layout = it })

        val source = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(LinkPeekAction.OpenForeground),
            useUnmergedTree = true,
        )
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetBounds = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.slot(1),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val destinationInSource = targetBounds.center - sourceBounds.topLeft

        source.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(destinationInSource, delayMillis = 600L)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            layout.actions == listOf(
                LinkPeekAction.Copy,
                LinkPeekAction.OpenForeground,
                LinkPeekAction.Share,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag(
                    LinkPeekActionEditorTestTags.DragOverlay,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
            }.isFailure
        }
    }

    private fun setEditorContent(
        layout: () -> LinkPeekActionLayout,
        onLayoutChanged: (LinkPeekActionLayout) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekActionEditorPage(
                    layout = layout(),
                    onLayoutChanged = onLayoutChanged,
                    onBack = {},
                    backLabel = "Back",
                    title = "Link Peek buttons",
                    instructions = "Drag buttons",
                    availableTitle = "Available",
                    fixedPlusLabel = FixedPlusLabel,
                    emptySlotLabel = "Empty slot",
                    moveToSlotLabel = "Move to slot",
                    moveToAvailableLabel = "Move to available actions",
                    actionLabel = LinkPeekAction::wireValue,
                )
            }
        }
    }

    private fun actionBounds(action: LinkPeekAction) = composeRule.onNodeWithTag(
        LinkPeekActionEditorTestTags.action(action),
        useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private fun toolbarCenters() = listOf(
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.slot(0),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot.center,
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.slot(1),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot.center,
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.FixedPlus,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot.center,
        composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.slot(2),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot.center,
    )

    private fun performCustomAction(action: LinkPeekAction, index: Int) {
        val node = composeRule.onNodeWithTag(
            LinkPeekActionEditorTestTags.action(action),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val customActions = node.config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { customActions[index].action() }
    }

    private companion object {
        const val FixedPlusLabel = "Open in background tab, fixed third button"
    }
}
