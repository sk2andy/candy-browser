package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPeekActionLayoutRulesTest {
    @Test
    fun `four slot bar keeps plus in third slot near right edge`() {
        val target = Rect(296f, 700f, 344f, 748f)

        val slots = LinkPeekActionLayoutRules.actionBarSlots(
            containerBounds = Rect(0f, 0f, 360f, 800f),
            newTabTargetBounds = target,
            slotCount = 4,
            fixedNewTabSlotIndex = 2,
            preferredSpacingPx = 8f,
        )

        assertEquals(4, slots.size)
        assertEquals(Rect(144f, 700f, 192f, 748f), slots[0])
        assertEquals(Rect(200f, 700f, 248f, 748f), slots[1])
        assertEquals(Rect(256f, 700f, 304f, 748f), slots[2])
        assertEquals(Rect(312f, 700f, 360f, 748f), slots[3])
    }

    @Test
    fun `four slot bar aligns fixed slot with target when space permits`() {
        val target = Rect(128f, 700f, 176f, 748f)

        val slots = LinkPeekActionLayoutRules.actionBarSlots(
            containerBounds = Rect(0f, 0f, 360f, 800f),
            newTabTargetBounds = target,
            slotCount = 4,
            fixedNewTabSlotIndex = 2,
            preferredSpacingPx = 8f,
        )

        assertEquals(target, slots[2])
    }

    @Test
    fun `four slot bar stacks on narrow width and rejects invalid fixed slot`() {
        val container = Rect(0f, 0f, 180f, 800f)
        val target = Rect(66f, 700f, 114f, 748f)

        val stacked = LinkPeekActionLayoutRules.actionBarSlots(
            containerBounds = container,
            newTabTargetBounds = target,
            slotCount = 4,
            fixedNewTabSlotIndex = 2,
            preferredSpacingPx = 8f,
        )
        assertEquals(4, stacked.size)
        assertTrue(stacked.all { bounds ->
            bounds.left >= container.left &&
                bounds.top >= container.top &&
                bounds.right <= container.right &&
                bounds.bottom <= container.bottom
        })
        assertTrue(stacked[0].bottom <= stacked[2].top)
        assertTrue(
            LinkPeekActionLayoutRules.actionBarSlots(
                containerBounds = Rect(0f, 0f, 360f, 800f),
                newTabTargetBounds = target,
                slotCount = 4,
                fixedNewTabSlotIndex = 4,
                preferredSpacingPx = 8f,
            ).isEmpty(),
        )
    }

}
