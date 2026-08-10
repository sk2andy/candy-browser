package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class TabReorderMotionTest {
    @Test
    fun `edge scroll accelerates toward viewport edges`() {
        assertEquals(
            -1_000f,
            TabReorderMotion.edgeScrollSpeed(
                pointerPx = 0f,
                viewportStartPx = 0f,
                viewportEndPx = 1_000f,
                edgeSizePx = 100f,
                maxSpeedPxPerSecond = 1_000f,
            ),
            0.001f,
        )
        assertEquals(
            500f,
            TabReorderMotion.edgeScrollSpeed(
                pointerPx = 950f,
                viewportStartPx = 0f,
                viewportEndPx = 1_000f,
                edgeSizePx = 100f,
                maxSpeedPxPerSecond = 1_000f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            TabReorderMotion.edgeScrollSpeed(
                pointerPx = 500f,
                viewportStartPx = 0f,
                viewportEndPx = 1_000f,
                edgeSizePx = 100f,
                maxSpeedPxPerSecond = 1_000f,
            ),
            0.001f,
        )
    }

    @Test
    fun `shifted indices open destination slot in both directions`() {
        assertEquals(2, TabReorderMotion.shiftedIndex(0, sourceIndex = 0, destinationIndex = 2))
        assertEquals(0, TabReorderMotion.shiftedIndex(1, sourceIndex = 0, destinationIndex = 2))
        assertEquals(1, TabReorderMotion.shiftedIndex(2, sourceIndex = 0, destinationIndex = 2))

        assertEquals(1, TabReorderMotion.shiftedIndex(0, sourceIndex = 2, destinationIndex = 0))
        assertEquals(2, TabReorderMotion.shiftedIndex(1, sourceIndex = 2, destinationIndex = 0))
        assertEquals(0, TabReorderMotion.shiftedIndex(2, sourceIndex = 2, destinationIndex = 0))
    }

    @Test
    fun `horizontal destination clamps to pin group`() {
        assertEquals(
            2,
            TabReorderMotion.horizontalDestinationIndex(
                sourceIndex = 1,
                dragOffsetPx = 500f,
                slotWidthPx = 100f,
                allowedRange = 0..2,
            ),
        )
        assertEquals(
            0,
            TabReorderMotion.horizontalDestinationIndex(
                sourceIndex = 1,
                dragOffsetPx = -160f,
                slotWidthPx = 100f,
                allowedRange = 0..2,
            ),
        )
    }

    @Test
    fun `grid destination combines column drag and autoscrolled rows`() {
        assertEquals(
            9,
            TabReorderMotion.gridDestinationIndex(
                sourceIndex = 1,
                dragOffsetPx = Offset(x = 0f, y = 400f),
                columnPitchPx = 100f,
                rowPitchPx = 100f,
                columnCount = 2,
                allowedRange = 0..11,
            ),
        )
        assertEquals(
            6,
            TabReorderMotion.gridDestinationIndex(
                sourceIndex = 1,
                dragOffsetPx = Offset(x = -100f, y = 300f),
                columnPitchPx = 100f,
                rowPitchPx = 100f,
                columnCount = 2,
                allowedRange = 0..11,
            ),
        )
    }

    @Test
    fun `hero viewport offset advances full slots without fractional rounding drift`() {
        assertEquals(
            4,
            TabReorderMotion.horizontalDestinationIndexWithViewportOffset(
                sourceIndex = 1,
                dragOffsetPx = 60f,
                viewportOffsetPx = 200f,
                slotWidthPx = 100f,
                allowedRange = 0..6,
            ),
        )
        val forwardAnchor = TabReorderMotion.heroPagerAnchorIndex(1, 4)
        val backwardAnchor = TabReorderMotion.heroPagerAnchorIndex(4, 1)
        assertEquals(3, forwardAnchor)
        assertEquals(
            forwardAnchor,
            TabReorderMotion.shiftedIndex(index = 4, sourceIndex = 1, destinationIndex = 4),
        )
        assertEquals(2, backwardAnchor)
        assertEquals(
            backwardAnchor,
            TabReorderMotion.shiftedIndex(index = 1, sourceIndex = 4, destinationIndex = 1),
        )
        assertEquals(3, TabReorderMotion.heroPagerAnchorIndex(3, 3))
    }

    @Test
    fun `hero edge step owns destination until reverse edge step updates anchor`() {
        assertEquals(
            2,
            TabReorderMotion.heroDestinationIndexForDrag(
                sourceIndex = 1,
                currentDestinationIndex = 1,
                edgeStepping = false,
                dragOffsetPx = 60f,
                viewportOffsetPx = 0f,
                slotWidthPx = 100f,
                allowedRange = 0..5,
            ),
        )
        assertEquals(
            3,
            TabReorderMotion.heroDestinationIndexForDrag(
                sourceIndex = 1,
                currentDestinationIndex = 3,
                edgeStepping = true,
                dragOffsetPx = 40f,
                viewportOffsetPx = 100f,
                slotWidthPx = 100f,
                allowedRange = 0..5,
            ),
        )
        assertEquals(
            2,
            TabReorderMotion.heroDestinationIndexForDrag(
                sourceIndex = 1,
                currentDestinationIndex = 1,
                edgeStepping = false,
                dragOffsetPx = 260f,
                viewportOffsetPx = 0f,
                slotWidthPx = 100f,
                allowedRange = 0..5,
            ),
        )
        val reversedDestination = 2
        assertEquals(
            1,
            TabReorderMotion.heroPagerAnchorIndex(
                sourceIndex = 1,
                destinationIndex = reversedDestination,
            ),
        )
    }

    @Test
    fun `pin target starts at old position while preceding tabs shift right`() {
        val deltas = TabReorderMotion.indexDeltas(
            oldOrder = listOf("one", "two", "target"),
            newOrder = listOf("target", "one", "two"),
        )

        assertEquals(2, deltas["target"])
        assertEquals(-1, deltas["one"])
        assertEquals(-1, deltas["two"])
        assertEquals(200f, TabReorderMotion.translationX(2, 100f, 0f), 0.001f)
        assertEquals(0f, TabReorderMotion.translationX(2, 100f, 1f), 0.001f)
    }

    @Test
    fun `unpin target moves right behind remaining pinned tabs`() {
        val deltas = TabReorderMotion.indexDeltas(
            oldOrder = listOf("target", "pin", "one"),
            newOrder = listOf("pin", "target", "one"),
        )

        assertEquals(-1, deltas["target"])
        assertEquals(1, deltas["pin"])
        assertEquals(0, deltas["one"])
    }
}
