package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyTrailLayoutRulesTest {
    @Test
    fun `menu trail keeps graph layer visible without tab overview`() {
        assertTrue(CandyTrailLayerRules.isVisible(false, "tab"))
        assertTrue(CandyTrailLayerRules.isVisible(true, null))
        assertTrue(!CandyTrailLayerRules.isVisible(false, null))
    }

    @Test
    fun `layout is deterministic and keeps nodes apart`() {
        val trail = CandyTrail(
            tabId = "tab",
            nodes = listOf(
                node("root", null, 1L),
                node("left", "root", 2L),
                node("right", "root", 3L),
                node("leaf", "left", 4L),
            ),
            currentNodeId = "leaf",
        )

        val first = CandyTrailLayoutRules.layout(trail)
        val second = CandyTrailLayoutRules.layout(trail)

        assertEquals(first, second)
        assertTrue(first.width > 0f && first.height > 0f)
        first.positions.forEach { position ->
            assertTrue(position.x.isFinite() && position.y.isFinite())
        }
        val siblings = first.positions.filter { it.nodeId == "left" || it.nodeId == "right" }
        assertTrue(kotlin.math.abs(siblings[0].y - siblings[1].y) >= CandyTrailLayoutRules.NODE_HEIGHT)
    }

    @Test
    fun `viewport scale is bounded`() {
        assertEquals(CandyTrailViewportRules.MIN_SCALE, CandyTrailViewportRules.scale(0f))
        assertEquals(CandyTrailViewportRules.MAX_SCALE, CandyTrailViewportRules.scale(99f))
    }

    @Test
    fun `deep graph centers current node and zoom keeps content reachable`() {
        val centered = CandyTrailViewportRules.centeredPan(
            contentCenter = 18_500f,
            viewportSize = 1_080f,
            graphSize = 18_700f,
            scale = CandyTrailViewportRules.MIN_SCALE,
            minimumVisible = 72f,
        )
        val currentCenterOnScreen = centered + 18_500f * CandyTrailViewportRules.MIN_SCALE
        assertEquals(540f, currentCenterOnScreen, 0.001f)

        val zoomedOut = CandyTrailViewportRules.zoomedPan(
            value = -2_000f,
            focalPoint = 540f,
            oldScale = 1f,
            newScale = 0.2f,
            viewportSize = 1_080f,
            graphSize = 3_000f,
            minimumVisible = 72f,
        )
        assertTrue(zoomedOut >= 72f - 3_000f * 0.2f)
        assertTrue(zoomedOut <= 1_080f - 72f)
    }

    @Test
    fun `stagger reveals every node at completion`() {
        assertEquals(0f, CandyTrailMotionRules.staggeredProgress(0f, 63, 64))
        assertEquals(1f, CandyTrailMotionRules.staggeredProgress(1f, 63, 64))
        assertEquals(1f, CandyTrailMotionRules.staggeredProgress(1f, 0, 64))
    }

    private fun node(id: String, parentId: String?, at: Long) = CandyTrailNode(
        id = id,
        parentId = parentId,
        url = "https://$id.example",
        title = id,
        visitedAt = at,
    )
}
