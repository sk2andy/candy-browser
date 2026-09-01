package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarActionSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarActionEditorRulesTest {
    @Test
    fun `insertion slots use one stable target per physical gap`() {
        val slots = AddressBarActionEditorRules.insertionSlots(
            side = AddressBarActionSide.AfterAddress,
            actionBounds = listOf(
                rect(left = 100f, right = 148f),
                rect(left = 148f, right = 196f),
            ),
            emptyBounds = null,
            isRtl = false,
        )

        assertEquals(
            listOf(
                target(AddressBarActionSide.AfterAddress, 0),
                target(AddressBarActionSide.AfterAddress, 1),
                target(AddressBarActionSide.AfterAddress, 2),
            ),
            slots.map(AddressBarActionEditorSlot::target),
        )
        assertEquals(
            listOf(
                AddressBarActionEditorPoint(100f, 25f),
                AddressBarActionEditorPoint(148f, 25f),
                AddressBarActionEditorPoint(196f, 25f),
            ),
            slots.map(AddressBarActionEditorSlot::indicatorCenter),
        )
        assertEquals(
            AddressBarActionEditorRect(124f, 0f, 172f, 50f),
            slots[1].bounds,
        )
    }

    @Test
    fun `empty side keeps drop indicator centered in placeholder`() {
        val slots = AddressBarActionEditorRules.insertionSlots(
            side = AddressBarActionSide.BeforeAddress,
            actionBounds = emptyList(),
            emptyBounds = rect(left = 40f, right = 88f),
            isRtl = true,
        )

        assertEquals(1, slots.size)
        assertEquals(target(AddressBarActionSide.BeforeAddress, 0), slots.single().target)
        assertEquals(
            AddressBarActionEditorPoint(64f, 25f),
            slots.single().indicatorCenter,
        )
    }

    @Test
    fun `rtl drop geometry mirrors slot halves and indicator boundaries`() {
        val slots = AddressBarActionEditorRules.insertionSlots(
            side = AddressBarActionSide.AfterAddress,
            actionBounds = listOf(
                rect(left = 148f, right = 196f),
                rect(left = 100f, right = 148f),
            ),
            emptyBounds = null,
            isRtl = true,
        )

        assertEquals(
            listOf(
                AddressBarActionEditorPoint(196f, 25f),
                AddressBarActionEditorPoint(148f, 25f),
                AddressBarActionEditorPoint(100f, 25f),
            ),
            slots.map(AddressBarActionEditorSlot::indicatorCenter),
        )
    }

    @Test
    fun `removing dragged action merges its two equivalent targets`() {
        val rawSlots = AddressBarActionEditorRules.insertionSlots(
            side = AddressBarActionSide.BeforeAddress,
            actionBounds = listOf(
                rect(left = 20f, right = 68f),
                rect(left = 68f, right = 116f),
            ),
            emptyBounds = null,
            isRtl = false,
        )

        val canonical = AddressBarActionEditorRules.canonicalSlotsAfterRemoving(
            slots = rawSlots,
            removedSide = AddressBarActionSide.BeforeAddress,
            removedIndex = 0,
            removedBounds = rect(left = 20f, right = 68f),
        )

        assertEquals(
            listOf(
                target(AddressBarActionSide.BeforeAddress, 0),
                target(AddressBarActionSide.BeforeAddress, 1),
            ),
            canonical.map(AddressBarActionEditorSlot::target),
        )
        assertEquals(AddressBarActionEditorPoint(44f, 25f), canonical[0].indicatorCenter)
        assertEquals(2, canonical.map(AddressBarActionEditorSlot::target).distinct().size)
    }

    @Test
    fun `drag resists movement until breakaway distance`() {
        val resisted = AddressBarActionEditorRules.dragResistance(
            dragX = 30f,
            dragY = 40f,
            breakawayDistancePx = 100f,
        )

        assertEquals(4.2f, resisted.visibleX, 0.001f)
        assertEquals(5.6f, resisted.visibleY, 0.001f)
        assertEquals(0.5f, resisted.breakawayProgress, 0.001f)
        assertFalse(resisted.breakawayReached)
    }

    @Test
    fun `drag releases at exact breakaway distance`() {
        val released = AddressBarActionEditorRules.dragResistance(
            dragX = 60f,
            dragY = 80f,
            breakawayDistancePx = 100f,
        )

        assertEquals(60f, released.visibleX, 0.001f)
        assertEquals(80f, released.visibleY, 0.001f)
        assertEquals(1f, released.breakawayProgress, 0.001f)
        assertTrue(released.breakawayReached)
    }

    @Test
    fun `invalid drag inputs remain finite and unlocked`() {
        val invalidOffset = AddressBarActionEditorRules.dragResistance(
            dragX = Float.NaN,
            dragY = Float.POSITIVE_INFINITY,
            breakawayDistancePx = 40f,
        )
        val invalidThreshold = AddressBarActionEditorRules.dragResistance(
            dragX = 20f,
            dragY = 10f,
            breakawayDistancePx = Float.NaN,
        )

        assertEquals(0f, invalidOffset.visibleX, 0f)
        assertEquals(0f, invalidOffset.visibleY, 0f)
        assertEquals(0f, invalidOffset.breakawayProgress, 0f)
        assertFalse(invalidOffset.breakawayReached)
        assertEquals(20f, invalidThreshold.visibleX, 0f)
        assertEquals(10f, invalidThreshold.visibleY, 0f)
        assertFalse(invalidThreshold.breakawayReached)
    }

    @Test
    fun `hit testing ignores invalid slots and resolves overlap by nearest center`() {
        val start = target(AddressBarActionSide.BeforeAddress, 0)
        val end = target(AddressBarActionSide.AfterAddress, 0)
        val invalid = target(AddressBarActionSide.AfterAddress, -1)
        val slots = listOf(
            slot(start, left = 0f, right = 60f),
            slot(end, left = 40f, right = 100f),
            AddressBarActionEditorSlot(
                target = invalid,
                bounds = AddressBarActionEditorRect(Float.NaN, 0f, 50f, 50f),
            ),
        )

        assertEquals(end, AddressBarActionEditorRules.hitTarget(65f, 25f, slots))
        assertEquals(start, AddressBarActionEditorRules.hitTarget(45f, 25f, slots))
        assertNull(AddressBarActionEditorRules.hitTarget(Float.NaN, 25f, slots))
        assertNull(AddressBarActionEditorRules.hitTarget(200f, 25f, slots))
    }

    @Test
    fun `snap retains current target through larger exit zone`() {
        val start = target(AddressBarActionSide.BeforeAddress, 0)
        val end = target(AddressBarActionSide.AfterAddress, 0)
        val slots = listOf(
            slot(start, left = 0f, right = 40f),
            slot(end, left = 50f, right = 90f),
        )

        assertEquals(
            start,
            AddressBarActionEditorRules.snappedTarget(
                pointerX = 47f,
                pointerY = 25f,
                slots = slots,
                currentTarget = start,
                enterPaddingPx = 0f,
                retainPaddingPx = 8f,
            ),
        )
        assertEquals(
            end,
            AddressBarActionEditorRules.snappedTarget(
                pointerX = 50f,
                pointerY = 25f,
                slots = slots,
                currentTarget = start,
                enterPaddingPx = 0f,
                retainPaddingPx = 8f,
            ),
        )
        assertNull(
            AddressBarActionEditorRules.snappedTarget(
                pointerX = 45f,
                pointerY = 25f,
                slots = slots,
                currentTarget = start,
                enterPaddingPx = Float.NaN,
                retainPaddingPx = Float.POSITIVE_INFINITY,
            ),
        )
    }

    @Test
    fun `drop adds unique actions up to three`() {
        val initial = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs),
            afterAddress = listOf(AddressBarAction.Reload),
        )

        val accepted = AddressBarActionEditorRules.drop(
            layout = initial,
            action = AddressBarAction.Share,
            target = target(AddressBarActionSide.AfterAddress, 1),
        ) as AddressBarActionDropDecision.Accepted
        val rejected = AddressBarActionEditorRules.drop(
            layout = accepted.layout,
            action = AddressBarAction.Print,
            target = target(AddressBarActionSide.BeforeAddress, 1),
        )

        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs),
                afterAddress = listOf(AddressBarAction.Reload, AddressBarAction.Share),
            ),
            accepted.layout,
        )
        assertTrue(accepted.changed)
        assertEquals(
            AddressBarActionDropDecision.Rejected(AddressBarActionDropRejection.Full),
            rejected,
        )
    }

    @Test
    fun `drop moves assigned action without consuming capacity`() {
        val initial = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs, AddressBarAction.Pin),
            afterAddress = listOf(AddressBarAction.Reload),
        )

        val moved = AddressBarActionEditorRules.drop(
            layout = initial,
            action = AddressBarAction.Pin,
            target = target(AddressBarActionSide.AfterAddress, 0),
        ) as AddressBarActionDropDecision.Accepted

        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs),
                afterAddress = listOf(AddressBarAction.Pin, AddressBarAction.Reload),
            ),
            moved.layout,
        )
        assertTrue(moved.changed)
    }

    @Test
    fun `drop reports unchanged placement without duplicating action`() {
        val initial = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs, AddressBarAction.Pin),
            afterAddress = listOf(AddressBarAction.Reload),
        )

        val unchanged = AddressBarActionEditorRules.drop(
            layout = initial,
            action = AddressBarAction.Pin,
            target = target(AddressBarActionSide.BeforeAddress, 1),
        ) as AddressBarActionDropDecision.Accepted

        assertEquals(initial, unchanged.layout)
        assertFalse(unchanged.changed)
    }

    @Test
    fun `drop rejects duplicate layout and invalid index`() {
        val duplicateLayout = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs),
            afterAddress = listOf(AddressBarAction.Tabs),
        )
        val validLayout = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs),
            afterAddress = emptyList(),
        )

        assertEquals(
            AddressBarActionDropDecision.Rejected(AddressBarActionDropRejection.InvalidLayout),
            AddressBarActionEditorRules.drop(
                duplicateLayout,
                AddressBarAction.Share,
                target(AddressBarActionSide.AfterAddress, 0),
            ),
        )
        assertEquals(
            AddressBarActionDropDecision.Rejected(AddressBarActionDropRejection.InvalidTarget),
            AddressBarActionEditorRules.drop(
                validLayout,
                AddressBarAction.Share,
                target(AddressBarActionSide.AfterAddress, 1),
            ),
        )
        assertEquals(
            AddressBarActionDropDecision.Rejected(AddressBarActionDropRejection.InvalidTarget),
            AddressBarActionEditorRules.drop(
                validLayout,
                AddressBarAction.Share,
                target(AddressBarActionSide.AfterAddress, -1),
            ),
        )
        assertFalse(AddressBarActionEditorRules.isValidLayout(duplicateLayout))
    }

    private fun target(
        side: AddressBarActionSide,
        index: Int,
    ) = AddressBarActionEditorTarget(side, index)

    private fun rect(
        left: Float,
        right: Float,
    ) = AddressBarActionEditorRect(
        left = left,
        top = 0f,
        right = right,
        bottom = 50f,
    )

    private fun slot(
        target: AddressBarActionEditorTarget,
        left: Float,
        right: Float,
    ) = AddressBarActionEditorSlot(
        target = target,
        bounds = rect(left = left, right = right),
    )
}
