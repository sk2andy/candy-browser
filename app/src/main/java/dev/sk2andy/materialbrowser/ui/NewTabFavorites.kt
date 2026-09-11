package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal object NewTabFavoritesTestTags {
    const val Container = "new_tab_favorites_container"
    const val LaunchOverlay = "new_tab_favorite_launch_overlay"

    fun favorite(url: String): String = "new_tab_favorite_$url"

    fun favicon(url: String): String = "new_tab_favorite_favicon_$url"
}

internal object NewTabFavoriteGridRules {
    const val COLUMN_COUNT = 4
    const val MAX_VISIBLE_ROWS = 5
    const val CELL_HEIGHT_DP = 76
    const val VERTICAL_CONTENT_PADDING_DP = 12

    fun rowCount(itemCount: Int): Int =
        (itemCount.coerceAtLeast(0) + COLUMN_COUNT - 1) / COLUMN_COUNT

    fun containerHeightDp(itemCount: Int): Int {
        val visibleRows = rowCount(itemCount).coerceAtMost(MAX_VISIBLE_ROWS)
        return visibleRows * CELL_HEIGHT_DP + VERTICAL_CONTENT_PADDING_DP * 2
    }
}

internal object NewTabFavoriteLaunchMotionRules {
    const val DURATION_MILLIS = 620

    fun position(
        start: Offset,
        target: Offset,
        progress: Float,
        curveDistancePx: Float,
    ): Offset {
        val boundedProgress = progress.coerceIn(0f, 1f)
        val side = if (start.x <= target.x) -1f else 1f
        val arc = sin(PI.toFloat() * boundedProgress) * curveDistancePx * side
        val curl = sin(PI.toFloat() * 2f * boundedProgress) *
            curveDistancePx * 0.16f * (1f - boundedProgress)
        return Offset(
            x = start.x + (target.x - start.x) * boundedProgress + arc,
            y = start.y + (target.y - start.y) * boundedProgress -
                sin(PI.toFloat() * boundedProgress) * curveDistancePx * 0.28f + curl,
        )
    }
}

internal data class NewTabFavoriteLaunchRequest(
    val favorite: FavoriteEntry,
    val startCenterInWindow: Offset,
)

@Composable
internal fun NewTabFavoriteGrid(
    favorites: List<FavoriteEntry>,
    favicons: Map<String, Bitmap>,
    enabled: Boolean,
    onFavorite: (FavoriteEntry, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(NewTabFavoriteGridRules.COLUMN_COUNT),
        modifier = modifier
            .height(NewTabFavoriteGridRules.containerHeightDp(favorites.size).dp)
            .testTag(NewTabFavoritesTestTags.Container),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = NewTabFavoriteGridRules.VERTICAL_CONTENT_PADDING_DP.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = enabled,
    ) {
        items(
            items = favorites,
            key = FavoriteEntry::url,
        ) { favorite ->
            FavoriteShortcut(
                favorite = favorite,
                favicon = favicons[favorite.url],
                enabled = enabled,
                onClick = onFavorite,
            )
        }
    }
}

@Composable
private fun FavoriteShortcut(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
    enabled: Boolean,
    onClick: (FavoriteEntry, Offset) -> Unit,
) {
    var centerInWindow by remember(favorite.url) { mutableStateOf(Offset.Unspecified) }
    Surface(
        onClick = { onClick(favorite, centerInWindow) },
        enabled = enabled,
        modifier = Modifier
            .height(NewTabFavoriteGridRules.CELL_HEIGHT_DP.dp)
            .onGloballyPositioned { coordinates ->
                centerInWindow = coordinates.boundsInWindow().center
            }
            .testTag(NewTabFavoritesTestTags.favorite(favorite.url)),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NewTabFavoriteIcon(
                    favorite = favorite,
                    favicon = favicon,
                    modifier = Modifier.size(36.dp),
                    imageTestTag = NewTabFavoritesTestTags.favicon(favorite.url),
                )
                Text(
                    text = favorite.title.ifBlank {
                        AddressResolver.displayText(favorite.url)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun NewTabFavoriteLaunchOverlay(
    request: NewTabFavoriteLaunchRequest,
    favicon: Bitmap?,
    rootOriginInWindow: Offset,
    targetCenterInWindow: Offset,
    onFinished: (FavoriteEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(request) { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val curveDistancePx = with(density) { 54.dp.toPx() }
    val iconSizePx = with(density) { FAVORITE_LAUNCH_ICON_SIZE_DP.dp.toPx() }
    val start = request.startCenterInWindow - rootOriginInWindow
    val target = targetCenterInWindow - rootOriginInWindow
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(request) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = NewTabFavoriteLaunchMotionRules.DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
        currentOnFinished(request.favorite)
    }

    val position = NewTabFavoriteLaunchMotionRules.position(
        start = start,
        target = target,
        progress = progress.value,
        curveDistancePx = curveDistancePx,
    )
    val boundedProgress = progress.value.coerceIn(0f, 1f)
    val iconScale = 1f + sin(PI.toFloat() * boundedProgress) * 0.72f
    val iconAlpha = ((1f - boundedProgress) / 0.14f).coerceIn(0f, 1f)
    val rotationDirection = if (start.x <= target.x) -1f else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(2f)
            .testTag(NewTabFavoritesTestTags.LaunchOverlay),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(TRAIL_POINT_COUNT) { index ->
                val trailProgress = (boundedProgress - (index + 1) * 0.045f).coerceAtLeast(0f)
                val trailPosition = NewTabFavoriteLaunchMotionRules.position(
                    start = start,
                    target = target,
                    progress = trailProgress,
                    curveDistancePx = curveDistancePx,
                )
                drawCircle(
                    color = launchColor(index, colors.primary, colors.tertiary)
                        .copy(alpha = (0.22f - index * 0.03f).coerceAtLeast(0f)),
                    radius = iconSizePx * (0.22f - index * 0.025f),
                    center = trailPosition,
                )
            }

            val sourceBurst = (boundedProgress / 0.36f).coerceIn(0f, 1f)
            repeat(SPARKLE_COUNT) { index ->
                val angle = index * (PI.toFloat() * 2f / SPARKLE_COUNT) + boundedProgress * 0.7f
                val distance = iconSizePx * (0.45f + sourceBurst * 1.15f)
                drawCircle(
                    color = launchColor(index, colors.secondary, colors.tertiary)
                        .copy(alpha = (1f - sourceBurst) * 0.78f),
                    radius = iconSizePx * 0.055f,
                    center = Offset(
                        x = start.x + cos(angle) * distance,
                        y = start.y + sin(angle) * distance,
                    ),
                )
            }

            val arrival = ((boundedProgress - 0.58f) / 0.42f).coerceIn(0f, 1f)
            if (arrival > 0f) {
                drawCircle(
                    color = colors.primary.copy(alpha = (1f - arrival) * 0.48f),
                    radius = iconSizePx * (0.5f + arrival * 2.2f),
                    center = target,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    color = colors.tertiary.copy(alpha = (1f - arrival) * 0.34f),
                    radius = iconSizePx * (0.32f + arrival * 1.55f),
                    center = target,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        NewTabFavoriteIcon(
            favorite = request.favorite,
            favicon = favicon,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (position.x - iconSizePx / 2f).roundToInt(),
                        y = (position.y - iconSizePx / 2f).roundToInt(),
                    )
                }
                .size(FAVORITE_LAUNCH_ICON_SIZE_DP.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    rotationZ = rotationDirection * boundedProgress * 245f
                    alpha = iconAlpha
                    shadowElevation = 12.dp.toPx() * sin(PI.toFloat() * boundedProgress)
                    shape = CircleShape
                    clip = true
                },
            imageTestTag = null,
        )
    }
}

@Composable
private fun NewTabFavoriteIcon(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
    modifier: Modifier,
    imageTestTag: String?,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (favicon != null && !favicon.isRecycled) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp)
                        .then(
                            if (imageTestTag != null) {
                                Modifier.testTag(imageTestTag)
                            } else {
                                Modifier
                            },
                        ),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = favoriteInitial(favorite),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

private fun launchColor(index: Int, first: Color, second: Color): Color =
    if (index % 2 == 0) first else second

private const val FAVORITE_LAUNCH_ICON_SIZE_DP = 44
private const val TRAIL_POINT_COUNT = 5
private const val SPARKLE_COUNT = 8
