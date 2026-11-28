package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Decorative only: no pointer input, inset changes or extra space in the scroll viewport. */
@Composable
internal fun BoxScope.TabOverviewScrollFades(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    bounds: () -> Rect?,
    viewportSize: () -> IntSize,
) {
    ScrollFade(atTop = true, visible = canScrollBackward, bounds = bounds, viewportSize = viewportSize)
    ScrollFade(atTop = false, visible = canScrollForward, bounds = bounds, viewportSize = viewportSize)
}

@Composable
private fun BoxScope.ScrollFade(
    atTop: Boolean,
    visible: Boolean,
    bounds: () -> Rect?,
    viewportSize: () -> IntSize,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "overviewScrollFade",
    )
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .height(if (atTop) 28.dp else 36.dp)
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val root = viewportSize()
                val viewport = bounds()
                val left = viewport?.left ?: 0f
                val top = if (atTop) viewport?.top ?: 0f else (viewport?.bottom ?: root.height.toFloat()) - size.height
                val background = Brush.linearGradient(
                    colors = listOf(colors.primaryContainer, colors.tertiaryContainer, colors.surface),
                    start = Offset(-left, -top),
                    end = Offset(root.width - left, root.height - top),
                )
                val mask = Brush.verticalGradient(
                    colors = if (atTop) listOf(Color.Black, Color.Transparent) else listOf(Color.Transparent, Color.Black),
                )
                onDrawBehind {
                    drawRect(background)
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            },
    )
}
