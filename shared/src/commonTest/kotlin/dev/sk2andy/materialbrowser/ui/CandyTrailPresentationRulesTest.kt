package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkLifecycle
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandyTrailPresentationRulesTest {
    private val trail = CandyTrail(
        tabId = "tab",
        nodes = listOf(
            node("root", null),
            node("active", "root"),
            node("side", "root"),
            node("leaf", "side"),
        ),
        currentNodeId = "active",
        forks = listOf(
            CandyTrailFork(
                id = "fork",
                originTabId = "tab",
                originNodeId = "side",
                destinationTabId = "destination",
                profileId = "profile",
                isIncognito = false,
                url = "https://fork.example",
                title = "Fork",
                createdAt = 1L,
                updatedAt = 1L,
                lifecycle = CandyTrailForkLifecycle.Open,
            ),
        ),
    )

    @Test
    fun `folding root preserves active ancestry and hides side subtrees and forks`() {
        val projection = CandyTrailPresentationRules.visibleTrail(trail, false, setOf("root"))
        assertEquals(listOf("root", "active"), projection.nodes.map { it.id })
        assertTrue(projection.forks.isEmpty())
        assertEquals(3, CandyTrailPresentationRules.hiddenCount(trail, "root"))
        assertEquals(4, trail.nodes.size)
        assertEquals(trail, CandyTrailPresentationRules.visibleTrail(trail, false))
    }

    @Test
    fun `folded side branch keeps its root visible for expansion`() {
        val projection = CandyTrailPresentationRules.visibleTrail(trail, false, setOf("side"))
        assertEquals(listOf("root", "active", "side"), projection.nodes.map { it.id })
        assertTrue(projection.forks.isEmpty())
        assertEquals(2, CandyTrailPresentationRules.hiddenCount(trail, "side"))
    }

    @Test
    fun `active page cannot be hidden by stale collapsed state after navigation`() {
        val navigated = trail.copy(currentNodeId = "leaf")
        val projection = CandyTrailPresentationRules.visibleTrail(navigated, false, setOf("root", "side"))
        assertEquals(listOf("root", "side", "leaf"), projection.nodes.map { it.id })
        assertEquals("leaf", projection.currentNodeId)
    }

    @Test
    fun `route focus safely handles missing current page and cyclic ancestry`() {
        assertEquals(trail.copy(currentNodeId = "missing"), CandyTrailPresentationRules.visibleTrail(trail.copy(currentNodeId = "missing"), true))
        val cycle = trail.copy(nodes = listOf(node("root", "active"), node("active", "root")))
        assertEquals(setOf("root", "active"), CandyTrailPresentationRules.activeAncestry(cycle))
    }

    @Test
    fun `interpolation shares geometry for cards and edges while removed identities disappear`() {
        val old = CandyTrailLayoutRules.layout(trail)
        val projected = CandyTrailPresentationRules.visibleTrail(trail, true)
        val target = CandyTrailLayoutRules.layout(projected)
        val halfway = CandyTrailPresentationRules.interpolate(old, target, 0.5f)
        assertEquals(setOf("root", "active"), halfway.positions.map { it.nodeId }.toSet())
        assertTrue(halfway.forkPositions.isEmpty())
        val from = old.positions.single { it.nodeId == "root" }
        val to = target.positions.single { it.nodeId == "root" }
        assertEquals((from.y + to.y) / 2f, halfway.positions.single { it.nodeId == "root" }.y)
        assertEquals(target, CandyTrailPresentationRules.interpolate(old, target, 1f))
    }

    @Test
    fun `overview targets nearest fork or page instead of navigating tiny symbols`() {
        val layout = CandyTrailLayoutRules.layout(trail)
        val fork = layout.forkPositions.single()
        assertEquals(
            CandyTrailGraphTarget.Fork("fork"),
            CandyTrailPresentationRules.nearestTarget(
                layout,
                fork.x + CandyTrailLayoutRules.NODE_WIDTH / 2f,
                fork.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f,
            ),
        )
        val root = layout.positions.single { it.nodeId == "root" }
        assertEquals(
            CandyTrailGraphTarget.Node("root"),
            CandyTrailPresentationRules.nearestTarget(layout, root.x, root.y),
        )
    }

    @Test
    fun `dense overview symbols shrink before adjacent branch rows overlap`() {
        assertTrue(CandyTrailPresentationRules.compact(0.3f))
        assertFalse(CandyTrailPresentationRules.compact(1f))
        val layout = CandyTrailLayoutRules.layout(trail)
        val rows = (layout.positions.map { it.y } + layout.forkPositions.map { it.y }).distinct().sorted()
        val minimumGap = rows.zipWithNext { a, b -> b - a }.minOrNull()!!
        val symbolDiameter = 48f / CandyTrailPresentationRules.symbolScale(0.01f)
        // Related parent nodes may share halfway rows, but same-depth sibling rows stay disjoint.
        val siblings = layout.positions.filter { it.depth == 1 }.sortedBy { it.y }
        assertTrue(siblings.last().y - siblings.first().y > symbolDiameter)
        assertTrue(minimumGap > 0f)
    }

    private fun node(id: String, parentId: String?) = CandyTrailNode(
        id = id,
        parentId = parentId,
        url = "https://$id.example",
        title = id,
        visitedAt = 1L,
    )
}
