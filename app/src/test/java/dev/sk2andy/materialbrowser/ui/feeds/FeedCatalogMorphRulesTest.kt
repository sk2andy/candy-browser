package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedCatalogMorphRulesTest {
    @Test
    fun `entry begins at clicked tile relative to inset catalog`() {
        assertEquals(
            FeedCatalogMorphTransform(0.5f, 0.25f, 20f, 200f),
            FeedCatalogMorphRules.transform(
                source = Rect(30f, 230f, 230f, 430f),
                target = Rect(10f, 30f, 410f, 830f),
                progress = 0f,
            ),
        )
    }

    @Test
    fun `completed and overshooting spring always cover full catalog`() {
        listOf(1f, 1.2f).forEach { progress ->
            assertEquals(
                FeedCatalogMorphTransform(),
                FeedCatalogMorphRules.transform(
                    Rect(30f, 230f, 230f, 430f),
                    Rect(10f, 30f, 410f, 830f),
                    progress,
                ),
            )
        }
    }

    @Test
    fun `invalid geometry and progress preserve a visible full screen`() {
        val bounds = Rect(0f, 0f, 400f, 800f)
        assertEquals(
            FeedCatalogMorphTransform(),
            FeedCatalogMorphRules.transform(bounds, bounds, Float.NaN),
        )
        assertEquals(
            FeedCatalogMorphTransform(),
            FeedCatalogMorphRules.transform(Rect(0f, 0f, Float.POSITIVE_INFINITY, 100f), bounds, 0f),
        )
    }

    @Test
    fun `missing layout bounds do not collapse results`() {
        assertEquals(
            FeedCatalogMorphTransform(),
            FeedCatalogMorphRules.transform(Rect.Zero, Rect(0f, 0f, 400f, 800f), 0f),
        )
        assertEquals(
            FeedCatalogMorphTransform(),
            FeedCatalogMorphRules.transform(Rect(0f, 0f, 200f, 200f), Rect.Zero, 0f),
        )
    }
}
