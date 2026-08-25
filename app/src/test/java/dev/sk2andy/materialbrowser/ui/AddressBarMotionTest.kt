package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarMotionTest {
    @Test
    fun `compact and expanded width targets stay valid across viewport sizes`() {
        listOf(288.dp, 328.dp, 568.dp).forEach { maxWidth ->
            val compact = AddressBarMotion.widthTarget(
                presentation = AddressBarPresentation.Compact,
                compactWidth = 184.dp,
                maxWidth = maxWidth,
                feedbackWidth = 220.dp,
                edgeTabWidth = 52.dp,
            )
            val expanded = AddressBarMotion.widthTarget(
                presentation = AddressBarPresentation.Expanded,
                compactWidth = 184.dp,
                maxWidth = maxWidth,
                feedbackWidth = 220.dp,
                edgeTabWidth = 52.dp,
            )

            assertEquals(184.dp, compact)
            assertEquals(maxWidth, expanded)
            assertTrue(compact <= expanded)
        }
    }

    @Test
    fun `all width targets clamp to unusually narrow viewport`() {
        AddressBarPresentation.entries.forEach { presentation ->
            assertEquals(
                80.dp,
                AddressBarMotion.widthTarget(
                    presentation = presentation,
                    compactWidth = 184.dp,
                    maxWidth = 80.dp,
                    feedbackWidth = 220.dp,
                    edgeTabWidth = 100.dp,
                ),
            )
        }
    }

    @Test
    fun `overview chrome has two-button width and centers docked chrome`() {
        assertEquals(
            AddressBarMotion.OVERVIEW_WIDTH,
            AddressBarMotion.widthTarget(
                presentation = AddressBarPresentation.Overview,
                compactWidth = 184.dp,
                maxWidth = 328.dp,
                feedbackWidth = 220.dp,
                edgeTabWidth = 52.dp,
            ),
        )
        assertEquals(56.dp, AddressBarMotion.heightTarget(AddressBarPresentation.Overview))
        assertEquals(
            DpOffset.Zero,
            AddressBarMotion.dockOffsetForPosition(
                position = Offset.Zero,
                maxWidth = 328.dp,
                barWidth = 52.dp,
                verticalTravel = 400.dp,
            ),
        )
    }

    @Test
    fun `scroll and overview transitions use non-overlapping fade through`() {
        assertTrue(
            AddressBarMotion.usesFadeThrough(
                AddressBarPresentation.Compact,
                AddressBarPresentation.Expanded,
            ),
        )
        assertTrue(
            AddressBarMotion.usesFadeThrough(
                AddressBarPresentation.Expanded,
                AddressBarPresentation.Compact,
            ),
        )
        assertFalse(
            AddressBarMotion.usesFadeThrough(
                AddressBarPresentation.Docked,
                AddressBarPresentation.Expanded,
            ),
        )
        assertTrue(
            AddressBarMotion.usesFadeThrough(
                AddressBarPresentation.Docked,
                AddressBarPresentation.Overview,
            ),
        )
        assertTrue(
            AddressBarMotion.usesFadeThrough(
                AddressBarPresentation.Overview,
                AddressBarPresentation.Expanded,
            ),
        )
        assertFalse(
            AddressBarMotion.usesFadeThrough(
                AddressBarPresentation.CommandFeedback,
                AddressBarPresentation.Expanded,
            ),
        )
        assertEquals(
            AddressBarMotion.FADE_THROUGH_EXIT_MILLIS,
            AddressBarMotion.exitDurationMillis(
                AddressBarPresentation.Compact,
                AddressBarPresentation.Expanded,
            ),
        )
    }

    @Test
    fun `container spring has intermediate frames in both directions at supported widths`() {
        listOf(288.dp, 328.dp, 568.dp).forEach { expandedWidth ->
            listOf(184.dp to expandedWidth, expandedWidth to 184.dp).forEach { (start, end) ->
                val animation = TargetBasedAnimation(
                    animationSpec = AddressBarMotion.containerAnimationSpec,
                    typeConverter = Dp.VectorConverter,
                    initialValue = start,
                    targetValue = end,
                )
                val firstFrame = animation.getValueFromNanos(16_000_000L)
                val lower = minOf(start, end)
                val upper = maxOf(start, end)

                assertTrue("$start -> $end had no intermediate first frame", firstFrame > lower)
                assertTrue("$start -> $end reached target in one frame", firstFrame < upper)
                assertEquals(end, animation.getValueFromNanos(animation.durationNanos))
            }
        }
    }

    @Test
    fun `docked offset supports physical edges and vertical placement`() {
        val right = AddressBarMotion.dockOffsetForPosition(
            position = Offset(1f, 0.5f),
            maxWidth = 328.dp,
            barWidth = 52.dp,
            verticalTravel = 400.dp,
        )
        val left = AddressBarMotion.dockOffsetForPosition(
            position = Offset(-1f, 0.5f),
            maxWidth = 328.dp,
            barWidth = 52.dp,
            verticalTravel = 400.dp,
        )

        assertEquals(DpOffset(150.dp, -200.dp), right)
        assertEquals(DpOffset(-150.dp, -200.dp), left)
    }

    @Test
    fun `dock position keeps drag and overview transitions on one path`() {
        assertEquals(
            DpOffset(75.dp, -100.dp),
            AddressBarMotion.dockOffsetForPosition(
                position = Offset(0.5f, 0.25f),
                maxWidth = 328.dp,
                barWidth = 52.dp,
                verticalTravel = 400.dp,
            ),
        )
    }

    @Test
    fun `dock progress spring has intermediate frames toward overview and back`() {
        listOf(0f to 1f, 1f to 0f).forEach { (start, end) ->
            val animation = TargetBasedAnimation(
                animationSpec = AddressBarMotion.dockProgressAnimationSpec,
                typeConverter = Float.VectorConverter,
                initialValue = start,
                targetValue = end,
            )
            val firstFrame = animation.getValueFromNanos(16_000_000L)

            assertTrue(firstFrame > minOf(start, end))
            assertTrue(firstFrame < maxOf(start, end))
            assertEquals(end, animation.getValueFromNanos(animation.durationNanos))
        }
    }
}
