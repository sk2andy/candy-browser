package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkLifecycle
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyTrailGraphMotionTest {
    @Test
    fun `edge geometry bends symmetrically from parent to child`() {
        val geometry = CandyTrailGraphMotionRules.edgeGeometry(
            startX = 10f,
            startY = 20f,
            endX = 110f,
            endY = 80f,
        )

        assertEquals(62f, geometry.firstControlX, 0.001f)
        assertEquals(20f, geometry.firstControlY, 0.001f)
        assertEquals(58f, geometry.secondControlX, 0.001f)
        assertEquals(80f, geometry.secondControlY, 0.001f)
    }

    @Test
    fun `geometry and visual progress stay bounded`() {
        val reversed = CandyTrailGraphMotionRules.edgeGeometry(100f, 10f, 20f, 30f)

        assertEquals(100f, reversed.firstControlX, 0.001f)
        assertEquals(20f, reversed.secondControlX, 0.001f)
        assertEquals(0f, CandyTrailGraphMotionRules.revealProgress(-1f), 0.001f)
        assertEquals(1f, CandyTrailGraphMotionRules.revealProgress(2f), 0.001f)
        assertEquals(0.72f, CandyTrailGraphMotionRules.targetScale(-1f), 0.001f)
        assertEquals(1f, CandyTrailGraphMotionRules.targetScale(2f), 0.001f)
        assertEquals(0f, CandyTrailGraphMotionRules.arrowAlpha(0.8f), 0.001f)
        assertEquals(1f, CandyTrailGraphMotionRules.arrowAlpha(1f), 0.001f)
    }

    @Test
    fun `additions distinguish new nodes and forks from restore updates`() {
        val previous = CandyTrailGraphSnapshot(setOf("root"), setOf("fork-1"))
        val next = CandyTrailGraphSnapshot(setOf("root", "child"), setOf("fork-1", "fork-2"))

        assertEquals(
            setOf(
                CandyTrailGraphTarget.Node("child"),
                CandyTrailGraphTarget.Fork("fork-2"),
            ),
            CandyTrailGraphMotionRules.additions(previous, next),
        )
        assertTrue(CandyTrailGraphMotionRules.additions(next, next).isEmpty())
    }

    @Test
    fun `selected path runs root to target and then fork`() {
        val trail = trail()

        assertTrue(
            CandyTrailGraphMotionRules.pathTargets(
                trail,
                CandyTrailGraphTarget.Node("root"),
            ).isEmpty(),
        )
        assertEquals(
            listOf(
                CandyTrailGraphTarget.Node("child"),
                CandyTrailGraphTarget.Node("leaf"),
            ),
            CandyTrailGraphMotionRules.pathTargets(
                trail,
                CandyTrailGraphTarget.Node("leaf"),
            ),
        )
        assertEquals(
            listOf(
                CandyTrailGraphTarget.Node("child"),
                CandyTrailGraphTarget.Fork("fork"),
            ),
            CandyTrailGraphMotionRules.pathTargets(
                trail,
                CandyTrailGraphTarget.Fork("fork"),
            ),
        )
    }

    @Test
    fun `pulse clips safely across adjacent edge lengths`() {
        assertNull(
            CandyTrailGraphMotionRules.pulseSegment(
                progress = 0f,
                edgeStartDistance = 0f,
                edgeLength = 100f,
                totalLength = 200f,
            ),
        )
        assertEquals(
            CandyTrailPathSegment(0.44f, 1f),
            CandyTrailGraphMotionRules.pulseSegment(
                progress = 0.5f,
                edgeStartDistance = 0f,
                edgeLength = 100f,
                totalLength = 200f,
            ),
        )
        assertEquals(
            CandyTrailPathSegment(0f, 0.5f),
            CandyTrailGraphMotionRules.pulseSegment(
                progress = 0.75f,
                edgeStartDistance = 100f,
                edgeLength = 100f,
                totalLength = 200f,
            ),
        )
        assertEquals(0f, CandyTrailGraphMotionRules.pulseAlpha(-1f), 0.001f)
        assertEquals(1f, CandyTrailGraphMotionRules.pulseAlpha(0.5f), 0.001f)
        assertEquals(0f, CandyTrailGraphMotionRules.pulseAlpha(2f), 0.001f)
    }

    private fun trail(): CandyTrail = CandyTrail(
        tabId = "tab",
        nodes = listOf(
            CandyTrailNode("root", null, "https://root.test", "Root", 1L),
            CandyTrailNode("child", "root", "https://child.test", "Child", 2L),
            CandyTrailNode("leaf", "child", "https://leaf.test", "Leaf", 3L),
        ),
        currentNodeId = "leaf",
        forks = listOf(
            CandyTrailFork(
                id = "fork",
                originTabId = "tab",
                originNodeId = "child",
                destinationTabId = "destination",
                profileId = "profile",
                isIncognito = false,
                url = "https://child.test",
                title = "Child",
                createdAt = 4L,
                updatedAt = 4L,
                lifecycle = CandyTrailForkLifecycle.Open,
            ),
        ),
    )
}
