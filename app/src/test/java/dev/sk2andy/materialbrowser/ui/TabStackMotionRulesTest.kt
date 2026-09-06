package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabStackMotionRulesTest {
    @Test
    fun `collapse moves member into anchor and fades only near end`() {
        val halfway = TabStackMotionRules.overviewTransform(
            phase = TabStackMotionPhase.Collapsing,
            progress = 0.5f,
            deltaX = -120f,
            deltaY = 80f,
            memberIndex = 1,
            isAnchor = false,
        )
        val finished = TabStackMotionRules.overviewTransform(
            phase = TabStackMotionPhase.Collapsing,
            progress = 1f,
            deltaX = -120f,
            deltaY = 80f,
            memberIndex = 1,
            isAnchor = false,
        )

        assertEquals(-60f, halfway.translationX, 0.0001f)
        assertEquals(40f, halfway.translationY, 0.0001f)
        assertEquals(1f, halfway.alpha, 0.0001f)
        assertEquals(-120f, finished.translationX, 0.0001f)
        assertEquals(80f, finished.translationY, 0.0001f)
        assertEquals(0f, finished.alpha, 0.0001f)
    }

    @Test
    fun `hero edge centers leave part of distant card visible`() {
        assertEquals(
            -54f,
            TabStackMotionRules.heroEdgeCenterX(
                viewportLeft = 0f,
                viewportRight = 1_000f,
                cardWidth = 300f,
                startsBeforeAnchor = true,
            ),
            0.0001f,
        )
        assertEquals(
            1_054f,
            TabStackMotionRules.heroEdgeCenterX(
                viewportLeft = 0f,
                viewportRight = 1_000f,
                cardWidth = 300f,
                startsBeforeAnchor = false,
            ),
            0.0001f,
        )
    }

    @Test
    fun `hero motion limits cards and favors members nearest anchor`() {
        assertEquals(
            listOf("three", "five", "two"),
            TabStackMotionRules.heroMotionMemberIds(
                memberIds = listOf("one", "two", "three", "anchor", "five", "six"),
                anchorTabId = "anchor",
                limit = 3,
            ),
        )
        assertEquals(
            emptyList<String>(),
            TabStackMotionRules.heroMotionMemberIds(
                memberIds = listOf("one", "two"),
                anchorTabId = "missing",
            ),
        )
    }

    @Test
    fun `expand starts folded at anchor and ends at natural layout`() {
        val folded = TabStackMotionRules.overviewTransform(
            phase = TabStackMotionPhase.Expanding,
            progress = 0f,
            deltaX = 72f,
            deltaY = -48f,
            memberIndex = 2,
            isAnchor = false,
        )
        val expanded = TabStackMotionRules.overviewTransform(
            phase = TabStackMotionPhase.Expanding,
            progress = 1f,
            deltaX = 72f,
            deltaY = -48f,
            memberIndex = 2,
            isAnchor = false,
        )

        assertEquals(72f, folded.translationX, 0.0001f)
        assertEquals(-48f, folded.translationY, 0.0001f)
        assertEquals(0.0625f, folded.alpha, 0.0001f)
        assertEquals(0f, expanded.translationX, 0.0001f)
        assertEquals(0f, expanded.translationY, 0.0001f)
        assertEquals(1f, expanded.scale, 0.0001f)
        assertEquals(1f, expanded.alpha, 0.0001f)
    }

    @Test
    fun `anchor stays fixed and only pulses`() {
        val transform = TabStackMotionRules.overviewTransform(
            phase = TabStackMotionPhase.Collapsing,
            progress = 0.5f,
            deltaX = 200f,
            deltaY = 100f,
            memberIndex = 0,
            isAnchor = true,
        )

        assertEquals(0f, transform.translationX, 0.0001f)
        assertEquals(0f, transform.translationY, 0.0001f)
        assertEquals(0f, transform.rotationZ, 0.0001f)
        assertEquals(0.975f, transform.scale, 0.0001f)
        assertEquals(1f, transform.alpha, 0.0001f)
    }

    @Test
    fun `folder coverflow fans side cards with bounded transform`() {
        val center = TabStackMotionRules.folderCoverflowTransform(0f)
        val side = TabStackMotionRules.folderCoverflowTransform(1f)
        val far = TabStackMotionRules.folderCoverflowTransform(20f)

        assertEquals(1f, center.scale, 0.0001f)
        assertEquals(0f, center.rotationZ, 0.0001f)
        assertEquals(18f, side.translationY, 0.0001f)
        assertEquals(-5.5f, side.rotationZ, 0.0001f)
        assertEquals(0.66f, side.alpha, 0.0001f)
        assertEquals(-8.25f, far.rotationZ, 0.0001f)
    }

    @Test
    fun `invalid progress and offsets stay finite`() {
        val invalidProgress = TabStackMotionRules.overviewTransform(
            phase = TabStackMotionPhase.Expanding,
            progress = Float.NaN,
            deltaX = 40f,
            deltaY = 20f,
            memberIndex = 1,
            isAnchor = false,
        )
        val invalidOffset = TabStackMotionRules.folderCoverflowTransform(
            Float.POSITIVE_INFINITY,
        )

        assertEquals(40f, invalidProgress.translationX, 0.0001f)
        assertEquals(1f, invalidOffset.scale, 0.0001f)
        assertEquals(1f, invalidOffset.alpha, 0.0001f)
    }

    @Test
    fun `collapse trigger draws above incoming members`() {
        val anchor = TabStackMotionRules.overviewZIndex(
            isMotionMember = true,
            isAnchor = true,
        )
        val incoming = TabStackMotionRules.overviewZIndex(
            isMotionMember = true,
            isAnchor = false,
        )

        assertEquals(2f, anchor, 0.0001f)
        assertEquals(1f, incoming, 0.0001f)
    }
}
