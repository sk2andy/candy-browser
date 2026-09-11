package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class FavoriteFeedbackEvent(
    val id: Int,
    val added: Boolean,
)

@Composable
internal fun ExpressiveFavoriteStar(
    filled: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val fillProgress by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 680f),
        label = "Favorite star fill",
    )
    val semanticsModifier = if (contentDescription == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription
            selected = filled
        }
    }
    FavoriteStarCanvas(
        fillProgress = fillProgress,
        popScale = 1f,
        rotationDegrees = 0f,
        sparkleProgress = 1f,
        modifier = modifier.then(semanticsModifier),
    )
}

@Composable
internal fun FavoriteToggleFeedback(
    event: FavoriteFeedbackEvent,
    onFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fillProgress = remember(event.id) { Animatable(if (event.added) 0f else 1f) }
    val popScale = remember(event.id) { Animatable(if (event.added) 0.82f else 1.08f) }
    val rotation = remember(event.id) { Animatable(if (event.added) -7f else 5f) }
    val sparkleProgress = remember(event.id) { Animatable(0f) }
    val alpha = remember(event.id) { Animatable(1f) }

    LaunchedEffect(event.id) {
        coroutineScope {
            launch {
                fillProgress.animateTo(
                    targetValue = if (event.added) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = 620f),
                )
            }
            launch {
                popScale.animateTo(
                    targetValue = if (event.added) 1.15f else 0.9f,
                    animationSpec = tween(120, easing = FastOutSlowInEasing),
                )
                popScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 580f),
                )
            }
            launch {
                rotation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
                )
            }
            launch {
                sparkleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                )
            }
        }
        alpha.animateTo(0f, tween(140))
        onFinished(event.id)
    }

    Box(modifier = modifier.clearAndSetSemantics { }) {
        FavoriteStarCanvas(
            fillProgress = fillProgress.value,
            popScale = popScale.value,
            rotationDegrees = rotation.value,
            sparkleProgress = sparkleProgress.value,
            modifier = Modifier.size(56.dp),
            alpha = alpha.value,
        )
    }
}

@Composable
private fun FavoriteStarCanvas(
    fillProgress: Float,
    popScale: Float,
    rotationDegrees: Float,
    sparkleProgress: Float,
    modifier: Modifier,
    alpha: Float = 1f,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val starRadius = size.minDimension * 0.3f
        val innerRadius = starRadius * (0.42f + 0.08f * fillProgress.coerceIn(0f, 1f))
        val path = Path().apply {
            repeat(STAR_POINT_COUNT * 2) { index ->
                val angle = -PI / 2 + index * PI / STAR_POINT_COUNT
                val radius = if (index % 2 == 0) starRadius else innerRadius
                val point = Offset(
                    x = center.x + cos(angle).toFloat() * radius,
                    y = center.y + sin(angle).toFloat() * radius,
                )
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
            close()
        }
        rotate(rotationDegrees, center) {
            scale(popScale.coerceIn(0.8f, 1.16f), center) {
                drawPath(path, color.copy(alpha = alpha * fillProgress.coerceIn(0f, 1f)))
                drawPath(
                    path = path,
                    color = color.copy(alpha = alpha),
                    style = Stroke(width = 1.7.dp.toPx()),
                )
            }
        }
        drawFavoriteSparkles(
            center = center,
            radius = starRadius,
            progress = sparkleProgress,
            color = color.copy(alpha = alpha),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFavoriteSparkles(
    center: Offset,
    radius: Float,
    progress: Float,
    color: Color,
) {
    if (progress !in 0f..1f || progress == 1f) return
    val visibility = sin(PI * progress).toFloat().coerceIn(0f, 1f)
    val angles = floatArrayOf(-62f, -25f, 28f)
    angles.forEachIndexed { index, angleDegrees ->
        val angle = angleDegrees * PI.toFloat() / 180f
        val distance = radius * (1.25f + progress * (0.35f + index * 0.08f))
        val point = Offset(
            x = center.x + cos(angle) * distance,
            y = center.y + sin(angle) * distance,
        )
        drawCircle(
            color = color.copy(alpha = color.alpha * visibility),
            radius = radius * (0.10f - index * 0.015f) * visibility,
            center = point,
        )
    }
}

private const val STAR_POINT_COUNT = 5
