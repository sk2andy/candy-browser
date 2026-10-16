package dev.sk2andy.materialbrowser.constellations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationGraphLabelRulesTest {
    @Test
    fun `clustered pages keep labels apart and clear of every node`() {
        val anchors = listOf(
            anchor("ai", 78f, 188f, 80f),
            anchor("local", 118f, 170f, 92f),
            anchor("privacy", 180f, 103f, 134f),
            anchor("private", 192f, 143f, 140f),
            anchor("hiking", 226f, 170f, 132f),
            anchor("alps", 220f, 210f, 134f),
            anchor("bread", 286f, 165f, 106f),
            anchor("sourdough", 283f, 205f, 136f),
        )

        val labels = ConstellationGraphLabelRules.layout(anchors, 360f, 320f, padding = 12f, gap = 4f)

        assertTrue("Small map should retain useful labels", labels.size >= 5)
        labels.forEach { label ->
            assertTrue(label.left >= 12f && label.top >= 12f)
            assertTrue(label.right <= 348f && label.bottom <= 308f)
            anchors.forEach { node ->
                val dx = node.x - node.x.coerceIn(label.left, label.right)
                val dy = node.y - node.y.coerceIn(label.top, label.bottom)
                assertTrue("${label.id} overlaps ${node.id}", dx * dx + dy * dy >= 18f * 18f)
            }
        }
        labels.forEachIndexed { index, left ->
            labels.drop(index + 1).forEach { right ->
                val overlap = left.left < right.right + 4f && left.right + 4f > right.left &&
                    left.top < right.bottom + 4f && left.bottom + 4f > right.top
                assertFalse("${left.id} overlaps ${right.id}", overlap)
            }
        }
        assertEquals(
            labels,
            ConstellationGraphLabelRules.layout(anchors.reversed(), 360f, 320f, padding = 12f, gap = 4f),
        )
    }

    @Test
    fun `selected label wins limited space even when its identifier sorts last`() {
        val anchors = listOf(
            anchor("alpha", 60f, 10f, 96f).copy(labelHeight = 42f, nodeRadius = 10f),
            anchor("selected", 60f, 10f, 96f).copy(labelHeight = 42f, nodeRadius = 10f, selected = true),
        )

        val labels = ConstellationGraphLabelRules.layout(anchors, 120f, 80f, padding = 6f, gap = 4f)

        assertEquals(listOf("selected"), labels.map { it.id })
    }

    @Test
    fun `labels at viewport corners stay wholly inside padded bounds`() {
        val anchors = listOf(
            anchor("top-left", 0f, 0f),
            anchor("top-right", 360f, 0f),
            anchor("bottom-left", 0f, 320f),
            anchor("bottom-right", 360f, 320f),
        )

        val labels = ConstellationGraphLabelRules.layout(anchors, 360f, 320f, padding = 12f, gap = 4f)

        assertEquals(4, labels.size)
        assertTrue(labels.all { it.left >= 12f && it.top >= 12f && it.right <= 348f && it.bottom <= 308f })
    }

    @Test
    fun `offscreen invalid and oversized labels remain hidden`() {
        val anchors = listOf(
            anchor("offscreen", -1f, 50f),
            anchor("nan", Float.NaN, 50f),
            anchor("oversized", 50f, 50f, width = 200f),
            anchor("infinite", 50f, 50f, width = Float.POSITIVE_INFINITY),
            anchor("no-label", 80f, 80f, width = 0f),
        )

        assertTrue(ConstellationGraphLabelRules.layout(anchors, 120f, 100f, padding = 6f, gap = 4f).isEmpty())
        assertTrue(ConstellationGraphLabelRules.layout(anchors, Float.NaN, 100f, padding = 6f, gap = 4f).isEmpty())
    }

    private fun anchor(id: String, x: Float, y: Float, width: Float = 100f) = ConstellationGraphLabelAnchor(
        id = id,
        x = x,
        y = y,
        nodeRadius = 14f,
        labelWidth = width,
        labelHeight = 20f,
    )
}
