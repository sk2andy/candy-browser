package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.ui.geometry.Rect

internal data class FeedCatalogTopicOrigin(val bounds: Rect, val index: Int)

internal data class FeedCatalogMorphTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
)

internal object FeedCatalogMorphRules {
    fun transform(source: Rect, target: Rect, progress: Float): FeedCatalogMorphTransform {
        if (source.isEmpty || target.isEmpty || !source.isFinite() || !target.isFinite() || !progress.isFinite()) {
            return FeedCatalogMorphTransform()
        }
        val remaining = 1f - progress.coerceIn(0f, 1f)
        return FeedCatalogMorphTransform(
            scaleX = 1f - (1f - source.width / target.width) * remaining,
            scaleY = 1f - (1f - source.height / target.height) * remaining,
            translationX = (source.left - target.left) * remaining,
            translationY = (source.top - target.top) * remaining,
        )
    }

    private fun Rect.isFinite(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
}
