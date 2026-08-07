package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class FavoriteFeedbackEvent(
    val id: Int,
    val added: Boolean,
)

internal object FavoriteRowOrderRules {
    fun mergeForExit(
        currentUrls: List<String>,
        targetUrls: List<String>,
    ): List<String> {
        val targetSet = targetUrls.toSet()
        return targetUrls.distinct().toMutableList().apply {
            currentUrls.withIndex()
                .filter { (_, url) -> url !in targetSet }
                .forEach { (index, url) -> add(index.coerceAtMost(size), url) }
        }
    }
}

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
internal fun ExpressiveFavoriteRows(
    favorites: List<FavoriteEntry>,
    onFavorite: (String) -> Unit,
    enabled: Boolean = true,
) {
    val targetFavorites = favorites.take(MAX_VISIBLE_FAVORITES)
    val initialFavorites = remember { targetFavorites }
    val rows = remember {
        mutableStateListOf<FavoriteRowState>().apply {
            initialFavorites.forEach { add(FavoriteRowState(it, initiallyVisible = true)) }
        }
    }

    LaunchedEffect(targetFavorites) {
        val targetUrls = targetFavorites.map(FavoriteEntry::url)
        val targetUrlSet = targetUrls.toSet()
        val currentUrls = rows.map { it.entry.url }
        val rowsByUrl = rows.associateBy { it.entry.url }.toMutableMap()
        rows.filter { it.entry.url !in targetUrlSet }.forEach { it.visible = false }
        targetFavorites.forEach { favorite ->
            val row = rowsByUrl.getOrPut(favorite.url) {
                FavoriteRowState(favorite, initiallyVisible = false)
            }
            row.entry = favorite
            row.visible = true
        }
        val mergedUrls = FavoriteRowOrderRules.mergeForExit(currentUrls, targetUrls)
        val mergedRows = mergedUrls.mapNotNull(rowsByUrl::get)
        if (rows != mergedRows) {
            rows.clear()
            rows.addAll(mergedRows)
        }
    }

    Column {
        rows.forEach { row ->
            key(row.entry.url) {
                AnimatedFavoriteRow(
                    row = row,
                    enabled = enabled,
                    onClick = { onFavorite(row.entry.url) },
                    onExited = { if (!row.visible) rows.remove(row) },
                )
            }
        }
    }
}

@Composable
private fun AnimatedFavoriteRow(
    row: FavoriteRowState,
    enabled: Boolean,
    onClick: () -> Unit,
    onExited: () -> Unit,
) {
    val visibility = remember(row) {
        MutableTransitionState(row.initiallyVisible).apply { targetState = row.visible }
    }
    LaunchedEffect(row.visible) { visibility.targetState = row.visible }
    LaunchedEffect(visibility) {
        snapshotFlow { visibility.isIdle && !visibility.currentState }
            .first { it }
        onExited()
    }
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn(tween(150)) +
            expandVertically(spring(dampingRatio = 0.84f, stiffness = 520f)) +
            slideInVertically(spring(dampingRatio = 0.82f, stiffness = 560f)) { -it / 4 },
        exit = fadeOut(tween(110)) +
            shrinkVertically(tween(190, easing = FastOutSlowInEasing)) +
            slideOutVertically(tween(170, easing = FastOutSlowInEasing)) { it / 5 },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveFavoriteStar(
                filled = true,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    AddressResolver.displayText(row.entry.url),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

private class FavoriteRowState(
    entry: FavoriteEntry,
    val initiallyVisible: Boolean,
) {
    var entry by mutableStateOf(entry)
    var visible by mutableStateOf(true)
}

private const val MAX_VISIBLE_FAVORITES = 6
private const val STAR_POINT_COUNT = 5
