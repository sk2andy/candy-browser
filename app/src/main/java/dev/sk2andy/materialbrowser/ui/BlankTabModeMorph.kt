package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import kotlin.math.hypot
import kotlin.math.max

internal object BlankTabModeMorphRules {
    const val DURATION_MILLIS = 360
    const val HERO_SHADOW_ELEVATION_DP = 14
    const val HERO_SHADOW_CLEARANCE_DP = 32

    fun target(incognito: Boolean): Float = if (incognito) 1f else 0f

    fun bounded(progress: Float): Float =
        if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

    fun interpolate(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * bounded(progress)

    fun regularIconAlpha(progress: Float): Float = 1f - bounded(progress)

    fun incognitoIconAlpha(progress: Float): Float = bounded(progress)

    fun iconScale(alpha: Float): Float = interpolate(0.82f, 1f, alpha)

    fun controlCornerRadiusDp(progress: Float): Float = interpolate(14f, 20f, progress)

    fun heroCornerRadiusDp(progress: Float): Float = interpolate(48f, 30f, progress)

    fun maxRevealRadius(
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
    ): Float {
        val safeWidth = width.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val safeHeight = height.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val safeOriginX = originX.takeIf { it.isFinite() } ?: safeWidth / 2f
        val safeOriginY = originY.takeIf { it.isFinite() } ?: safeHeight / 2f
        val farthestX = max(safeOriginX, safeWidth - safeOriginX)
        val farthestY = max(safeOriginY, safeHeight - safeOriginY)
        return hypot(farthestX, farthestY)
    }

    fun revealRadius(
        progress: Float,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
    ): Float = maxRevealRadius(originX, originY, width, height) * bounded(progress)
}

@Composable
internal fun rememberBlankTabModeProgress(tabId: String, incognito: Boolean): Float {
    val progress = remember(tabId) {
        Animatable(BlankTabModeMorphRules.target(incognito))
    }
    LaunchedEffect(progress, incognito) {
        progress.animateTo(
            targetValue = BlankTabModeMorphRules.target(incognito),
            animationSpec = tween(
                durationMillis = BlankTabModeMorphRules.DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    return BlankTabModeMorphRules.bounded(progress.value)
}

internal fun Modifier.blankTabModeBackground(
    progress: Float,
    revealOriginInRoot: Offset,
    regularCenterColor: Color,
    incognitoCenterColor: Color,
    edgeColor: Color,
): Modifier = drawWithCache {
    val center = Offset(size.width / 2f, size.height / 2f)
    val origin = revealOriginInRoot.takeIf { it.x.isFinite() && it.y.isFinite() } ?: center
    val gradientRadius = max(size.width, size.height).coerceAtLeast(1f)
    val regularBrush = Brush.radialGradient(
        colors = listOf(regularCenterColor, edgeColor),
        center = center,
        radius = gradientRadius,
    )
    val incognitoBrush = Brush.radialGradient(
        colors = listOf(incognitoCenterColor, edgeColor),
        center = center,
        radius = gradientRadius,
    )
    val revealRadius = BlankTabModeMorphRules.revealRadius(
        progress = progress,
        originX = origin.x,
        originY = origin.y,
        width = size.width,
        height = size.height,
    )
    val revealPath = Path().apply {
        addOval(
            Rect(
                left = origin.x - revealRadius,
                top = origin.y - revealRadius,
                right = origin.x + revealRadius,
                bottom = origin.y + revealRadius,
            ),
        )
    }
    onDrawBehind {
        drawRect(regularBrush)
        if (revealRadius > 0f) {
            clipPath(revealPath) { drawRect(incognitoBrush) }
        }
    }
}

@Composable
internal fun BlankTabIncognitoModeButton(
    enabled: Boolean,
    progress: Float,
    onCenterChanged: (Offset) -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val boundedProgress = BlankTabModeMorphRules.bounded(progress)
    val description = stringResource(
        if (enabled) {
            R.string.cd_make_blank_tab_regular
        } else {
            R.string.cd_make_blank_tab_incognito
        },
    )
    val regularAlpha = BlankTabModeMorphRules.regularIconAlpha(boundedProgress)
    val incognitoAlpha = BlankTabModeMorphRules.incognitoIconAlpha(boundedProgress)

    Box(
        modifier = Modifier
            .size(48.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                onCenterChanged(bounds.center)
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                BlankTabModeMorphRules.controlCornerRadiusDp(boundedProgress).dp,
            ),
            color = lerp(
                colors.surfaceContainerHighest,
                colors.tertiaryContainer,
                boundedProgress,
            ),
        ) {}
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .testTag(AddressBarTestTags.IncognitoToggle)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_incognito_outline),
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            alpha = regularAlpha
                            scaleX = BlankTabModeMorphRules.iconScale(regularAlpha)
                            scaleY = scaleX
                            rotationZ = -8f * boundedProgress
                        },
                    tint = colors.onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_incognito_filled),
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            alpha = incognitoAlpha
                            scaleX = BlankTabModeMorphRules.iconScale(incognitoAlpha)
                            scaleY = scaleX
                            rotationZ = 8f * (1f - boundedProgress)
                        },
                    tint = colors.onTertiaryContainer,
                )
            }
        }
    }
}
