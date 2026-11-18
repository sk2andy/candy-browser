@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

@Composable
internal fun AddressSuggestionLaunchEffect(
    origin: Rect,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(origin) { Animatable(0f) }
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    var target by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(origin) {
        progress.animateTo(1f, spatialSpec)
        onFinished()
    }
    Canvas(modifier.onGloballyPositioned { target = it.boundsInRoot() }) {
        if (!target.isEmpty) {
            val fraction = progress.value.coerceIn(0f, 1f)
            val remaining = 1f - fraction
            val left = (origin.left - target.left) * remaining
            val top = (origin.top - target.top) * remaining
            val width = origin.width * remaining + size.width * fraction
            val height = origin.height * remaining + size.height * fraction
            drawRoundRect(
                color = color.copy(alpha = 0.7f * remaining),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(20.dp.toPx() * remaining),
            )
        }
    }
}
