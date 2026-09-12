package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.LinkPeekAction
import dev.sk2andy.materialbrowser.browser.LinkPeekActionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPeekActionEditorRulesTest {
    @Test
    fun `action slots keep fixed plus at visual index two`() {
        val slots = LinkPeekActionEditorRules.actionSlots(
            actionBounds = listOf(
                rect(left = 0f),
                rect(left = 48f),
                rect(left = 144f),
            ),
            fixedPlusBounds = rect(left = 96f),
        )

        assertEquals(
            listOf(
                LinkPeekActionEditorTarget.ActionSlot(0),
                LinkPeekActionEditorTarget.ActionSlot(1),
                LinkPeekActionEditorTarget.FixedPlus,
                LinkPeekActionEditorTarget.ActionSlot(2),
            ),
            slots.map(LinkPeekActionEditorSlot::target),
        )
    }

    @Test
    fun `slot geometry rejects incomplete or invalid measurements`() {
        assertTrue(
            LinkPeekActionEditorRules.actionSlots(
                actionBounds = listOf(rect(0f), rect(48f)),
                fixedPlusBounds = rect(96f),
            ).isEmpty(),
        )
        assertTrue(
            LinkPeekActionEditorRules.actionSlots(
                actionBounds = listOf(rect(0f), rect(48f), rect(144f)),
                fixedPlusBounds = LinkPeekActionEditorRect(96f, 0f, 96f, 48f),
            ).isEmpty(),
        )
    }

    @Test
    fun `available drop slots include only empty configurable positions`() {
        val slots = LinkPeekActionEditorRules.actionSlots(
            actionBounds = listOf(rect(0f), rect(48f), rect(144f)),
            fixedPlusBounds = rect(96f),
        )

        assertEquals(
            listOf(LinkPeekActionEditorTarget.ActionSlot(1)),
            LinkPeekActionEditorRules.availableDropSlots(
                layout = LinkPeekActionLayout(
                    listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share),
                ),
                slots = slots,
            ).map(LinkPeekActionEditorSlot::target),
        )
    }

    @Test
    fun `drag resistance matches address editor breakaway feel`() {
        val held = LinkPeekActionEditorRules.dragResistance(
            dragX = 30f,
            dragY = 0f,
            breakawayDistancePx = 36f,
        )
        val released = LinkPeekActionEditorRules.dragResistance(
            dragX = 36f,
            dragY = 0f,
            breakawayDistancePx = 36f,
        )

        assertEquals(4.2f, held.visibleX, 0.0001f)
        assertFalse(held.breakawayReached)
        assertEquals(30f / 36f, held.breakawayProgress, 0.0001f)
        assertEquals(36f, released.visibleX, 0.0001f)
        assertTrue(released.breakawayReached)
        assertEquals(1f, released.breakawayProgress, 0f)
    }

    @Test
    fun `snap retains current slot across wider hysteresis`() {
        val slots = LinkPeekActionEditorRules.actionSlots(
            actionBounds = listOf(rect(0f), rect(64f), rect(192f)),
            fixedPlusBounds = rect(128f),
        )

        assertEquals(
            LinkPeekActionEditorTarget.ActionSlot(0),
            LinkPeekActionEditorRules.snappedTarget(
                pointerX = 55f,
                pointerY = 24f,
                slots = slots,
                currentTarget = LinkPeekActionEditorTarget.ActionSlot(0),
                enterPaddingPx = 8f,
                retainPaddingPx = 16f,
            ),
        )
        assertEquals(
            LinkPeekActionEditorTarget.ActionSlot(1),
            LinkPeekActionEditorRules.snappedTarget(
                pointerX = 70f,
                pointerY = 24f,
                slots = slots,
                currentTarget = LinkPeekActionEditorTarget.ActionSlot(0),
                enterPaddingPx = 8f,
                retainPaddingPx = 16f,
            ),
        )
    }

    @Test
    fun `selected action moves into empty target slot`() {
        val decision = LinkPeekActionEditorRules.drop(
            layout = LinkPeekActionLayout(
                listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share),
            ),
            action = LinkPeekAction.Copy,
            target = LinkPeekActionEditorTarget.ActionSlot(1),
        )

        assertEquals(
            LinkPeekActionEditorDropDecision.Accepted(
                layout = LinkPeekActionLayout(
                    listOf(
                        null,
                        LinkPeekAction.Copy,
                        LinkPeekAction.Share,
                    ),
                ),
                changed = true,
            ),
            decision,
        )
    }

    @Test
    fun `palette action fills empty target slot`() {
        val decision = LinkPeekActionEditorRules.drop(
            layout = LinkPeekActionLayout(
                listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share),
            ),
            action = LinkPeekAction.Snooze,
            target = LinkPeekActionEditorTarget.ActionSlot(1),
        )

        assertEquals(
            LinkPeekActionEditorDropDecision.Accepted(
                layout = LinkPeekActionLayout(
                    listOf(
                        LinkPeekAction.Copy,
                        LinkPeekAction.Snooze,
                        LinkPeekAction.Share,
                    ),
                ),
                changed = true,
            ),
            decision,
        )
    }

    @Test
    fun `palette drop removes toolbar action and preserves empty position`() {
        val decision = LinkPeekActionEditorRules.drop(
            layout = LinkPeekActionLayout.Default,
            action = LinkPeekAction.OpenPrivate,
            target = LinkPeekActionEditorTarget.Palette,
        )

        assertEquals(
            LinkPeekActionEditorDropDecision.Accepted(
                layout = LinkPeekActionLayout(
                    listOf(
                        LinkPeekAction.Copy,
                        null,
                        LinkPeekAction.Share,
                    ),
                ),
                changed = true,
            ),
            decision,
        )
    }

    @Test
    fun `fixed plus occupied and invalid slots reject without changing layout`() {
        assertEquals(
            LinkPeekActionEditorDropDecision.Rejected(
                LinkPeekActionEditorDropRejection.FixedPlus,
            ),
            LinkPeekActionEditorRules.drop(
                layout = LinkPeekActionLayout.Default,
                action = LinkPeekAction.Copy,
                target = LinkPeekActionEditorTarget.FixedPlus,
            ),
        )
        assertEquals(
            LinkPeekActionEditorDropDecision.Rejected(
                LinkPeekActionEditorDropRejection.Occupied,
            ),
            LinkPeekActionEditorRules.drop(
                layout = LinkPeekActionLayout.Default,
                action = LinkPeekAction.Snooze,
                target = LinkPeekActionEditorTarget.ActionSlot(0),
            ),
        )
        assertEquals(
            LinkPeekActionEditorDropDecision.Rejected(
                LinkPeekActionEditorDropRejection.InvalidTarget,
            ),
            LinkPeekActionEditorRules.drop(
                layout = LinkPeekActionLayout.Default,
                action = LinkPeekAction.Copy,
                target = LinkPeekActionEditorTarget.ActionSlot(Int.MAX_VALUE),
            ),
        )
    }

    @Test
    fun `non normalized layout rejects before placement`() {
        val invalid = LinkPeekActionLayout(
            listOf(LinkPeekAction.Copy, LinkPeekAction.Copy),
        )

        assertEquals(
            LinkPeekActionEditorDropDecision.Rejected(
                LinkPeekActionEditorDropRejection.InvalidLayout,
            ),
            LinkPeekActionEditorRules.drop(
                layout = invalid,
                action = LinkPeekAction.Share,
                target = LinkPeekActionEditorTarget.ActionSlot(0),
            ),
        )
    }

    private fun rect(left: Float): LinkPeekActionEditorRect = LinkPeekActionEditorRect(
        left = left,
        top = 0f,
        right = left + 48f,
        bottom = 48f,
    )
}
