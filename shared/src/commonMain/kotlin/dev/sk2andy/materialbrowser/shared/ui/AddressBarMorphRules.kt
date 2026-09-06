package dev.sk2andy.materialbrowser.shared.ui

import kotlin.math.min

data class AddressBarMorphCornerRadii(
    val horizontal: Float,
    val vertical: Float,
)

object AddressBarMorphRules {
    fun resistedProgress(progress: Float): Float {
        val boundedProgress = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return boundedProgress * (RESISTANCE_BASE + RESISTANCE_GROWTH * boundedProgress)
    }

    fun containerScale(progress: Float, sourceSize: Float, targetSize: Float): Float {
        if (
            !sourceSize.isFinite() ||
            !targetSize.isFinite() ||
            sourceSize <= 0f ||
            targetSize <= 0f
        ) {
            return 1f
        }
        return 1f + (targetSize / sourceSize - 1f) * resistedProgress(progress)
    }

    fun cornerRadii(
        progress: Float,
        sourceWidth: Float,
        sourceHeight: Float,
        targetSize: Float,
        sourceCornerRadius: Float? = null,
    ): AddressBarMorphCornerRadii {
        if (
            !sourceWidth.isFinite() ||
            !sourceHeight.isFinite() ||
            !targetSize.isFinite() ||
            sourceWidth <= 0f ||
            sourceHeight <= 0f ||
            targetSize <= 0f
        ) {
            return AddressBarMorphCornerRadii(horizontal = 0f, vertical = 0f)
        }
        val morphProgress = resistedProgress(progress)
        val maximumSourceRadius = min(sourceWidth, sourceHeight) / 2f
        val sourceRadius = sourceCornerRadius
            ?.takeIf(Float::isFinite)
            ?.coerceIn(0f, maximumSourceRadius)
            ?: maximumSourceRadius
        val displayedRadius = sourceRadius + (targetSize / 2f - sourceRadius) * morphProgress
        val scaleX = containerScale(progress, sourceWidth, targetSize)
        val scaleY = containerScale(progress, sourceHeight, targetSize)
        return AddressBarMorphCornerRadii(
            horizontal = (displayedRadius / scaleX).coerceIn(0f, sourceWidth / 2f),
            vertical = (displayedRadius / scaleY).coerceIn(0f, sourceHeight / 2f),
        )
    }

    private const val RESISTANCE_BASE = 0.7f
    private const val RESISTANCE_GROWTH = 0.3f
}
