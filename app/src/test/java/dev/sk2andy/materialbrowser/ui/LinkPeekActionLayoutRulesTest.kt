package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPeekActionLayoutRulesTest {
    @Test
    fun `left edge target places all actions on the right`() {
        val offsets = LinkPeekActionLayoutRules.horizontalOffsets(
            containerBounds = Rect(0f, 0f, 360f, 800f),
            targetBounds = Rect(16f, 700f, 64f, 748f),
            actionCount = 3,
            preferredSpacingPx = 8f,
        )

        assertEquals(listOf(72f, 128f, 184f), offsets)
    }

    @Test
    fun `right edge target keeps action order on the left`() {
        val offsets = LinkPeekActionLayoutRules.horizontalOffsets(
            containerBounds = Rect(0f, 0f, 360f, 800f),
            targetBounds = Rect(296f, 700f, 344f, 748f),
            actionCount = 3,
            preferredSpacingPx = 8f,
        )

        assertEquals(listOf(128f, 184f, 240f), offsets)
    }

    @Test
    fun `narrow layout splits actions without crossing container edges`() {
        val container = Rect(0f, 0f, 240f, 800f)
        val target = Rect(96f, 700f, 144f, 748f)
        val offsets = LinkPeekActionLayoutRules.horizontalOffsets(
            containerBounds = container,
            targetBounds = target,
            actionCount = 3,
            preferredSpacingPx = 8f,
        )

        assertEquals(3, offsets.size)
        assertTrue(offsets.all { offset -> offset >= container.left })
        assertTrue(offsets.all { offset -> offset + target.width <= container.right })
        assertTrue(offsets.none { offset -> offset < target.right && offset + target.width > target.left })
    }

    @Test
    fun `layout hides actions when touch targets cannot fit`() {
        assertTrue(
            LinkPeekActionLayoutRules.horizontalOffsets(
                containerBounds = Rect(0f, 0f, 180f, 800f),
                targetBounds = Rect(66f, 700f, 114f, 748f),
                actionCount = 3,
                preferredSpacingPx = 8f,
            ).isEmpty(),
        )
    }
}
