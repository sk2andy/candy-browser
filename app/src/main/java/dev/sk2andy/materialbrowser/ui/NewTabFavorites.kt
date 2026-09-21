package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteFolder
import dev.sk2andy.materialbrowser.data.FavoriteLibrary
import dev.sk2andy.materialbrowser.data.FavoriteLibraryEntry
import dev.sk2andy.materialbrowser.data.FavoriteFolderIcon
import dev.sk2andy.materialbrowser.data.BrowsingFavoritesRules
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal object NewTabFavoritesTestTags {
    const val Container = "new_tab_favorites_container"
    const val LaunchOverlay = "new_tab_favorite_launch_overlay"
    const val DragPreview = "new_tab_favorite_drag_preview"
    const val Manage = "new_tab_favorites_manage"

    fun favorite(url: String): String = "new_tab_favorite_$url"

    fun favicon(url: String): String = "new_tab_favorite_favicon_$url"

    fun folder(id: String): String = "new_tab_folder_$id"
}

internal object NewTabFavoriteGridRules {
    const val COLUMN_COUNT = 4
    const val MAX_VISIBLE_ROWS = 5
    const val CELL_HEIGHT_DP = 84
    const val VERTICAL_CONTENT_PADDING_DP = 12

    fun rowCount(itemCount: Int): Int =
        (itemCount.coerceAtLeast(0) + COLUMN_COUNT - 1) / COLUMN_COUNT

    fun containerHeightDp(itemCount: Int, hasUpNavigation: Boolean = false): Int {
        val visibleRows = rowCount(itemCount + if (hasUpNavigation) 1 else 0)
            .coerceAtMost(MAX_VISIBLE_ROWS)
        return visibleRows * CELL_HEIGHT_DP + VERTICAL_CONTENT_PADDING_DP * 2
    }
}

internal enum class NewTabFavoriteShapeVariant {
    Circle,
    RoundedSquare,
    Arch,
    Fan,
    Triangle,
    Diamond,
    ClamShell,
    Pentagon,
    Gem,
    FourSidedCookie,
    SixSidedCookie,
    SevenSidedCookie,
    NineSidedCookie,
    TwelveSidedCookie,
    FourLeafClover,
    EightLeafClover,
    Flower,
    Puffy,
    PuffyDiamond,
    Bun,
}

internal data class NewTabFavoriteMorphState(
    val from: NewTabFavoriteShapeVariant,
    val to: NewTabFavoriteShapeVariant,
    val progress: Float,
)

internal object NewTabFavoriteShapeRules {
    const val MORPH_FRAME_INTERVAL_MILLIS = 100

    fun startVariants(urls: List<String>): List<NewTabFavoriteShapeVariant> {
        return List(urls.size) { NewTabFavoriteShapeVariant.Circle }
    }

    fun morphState(
        start: NewTabFavoriteShapeVariant,
        phase: Float,
    ): NewTabFavoriteMorphState {
        return NewTabFavoriteMorphState(
            from = NewTabFavoriteShapeVariant.Circle,
            to = NewTabFavoriteShapeVariant.Circle,
            progress = 0f,
        )
    }

    fun morphProgress(
        elapsedMillis: Long,
        durationMillis: Int,
    ): Float {
        if (durationMillis <= 0) return 1f
        return elapsedMillis.coerceIn(0L, durationMillis.toLong()) / durationMillis.toFloat()
    }

}

internal object NewTabFavoriteLaunchMotionRules {
    const val DURATION_MILLIS = 620

    fun durationMillis(speed: FavoriteAnimationSpeed): Int = when (speed) {
        FavoriteAnimationSpeed.Relaxed -> 780
        FavoriteAnimationSpeed.Normal -> DURATION_MILLIS
        FavoriteAnimationSpeed.Fast -> 480
    }

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
    val shapeState: NewTabFavoriteMorphState,
)

@Composable
internal fun NewTabFavoriteGrid(
    favorites: List<FavoriteEntry>,
    library: FavoriteLibrary? = null,
    favicons: Map<String, Bitmap>,
    folderIcons: Map<String, Bitmap> = emptyMap(),
    enabled: Boolean,
    animateShapes: Boolean,
    animationSpeed: FavoriteAnimationSpeed,
    onFavorite: (FavoriteEntry, Offset, NewTabFavoriteMorphState) -> Unit,
    onReorderFavorite: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (library != null) {
        NewTabFavoriteLibraryGrid(
            library = library,
            favicons = favicons,
            folderIcons = folderIcons,
            enabled = enabled,
            animateShapes = animateShapes,
            animationSpeed = animationSpeed,
            onFavorite = onFavorite,
            onReorder = onReorderFavorite,
            modifier = modifier,
        )
        return
    }
    if (favorites.isEmpty()) return
    val favoriteUrls = favorites.map(FavoriteEntry::url)
    val startVariantsByUrl = remember(favoriteUrls) {
        val variants = NewTabFavoriteShapeRules.startVariants(favoriteUrls)
        favoriteUrls.mapIndexed { index, url -> url to variants[index] }.toMap()
    }
    val shapeMorphs = rememberNewTabFavoriteMorphs()
    val shapeClock: NewTabFavoriteShapeClock? = null
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
                startVariant = startVariantsByUrl[favorite.url]
                    ?: NewTabFavoriteShapeVariant.Circle,
                shapeClock = shapeClock,
                shapeMorphs = shapeMorphs,
                onClick = onFavorite,
            )
        }
    }
}

@Composable
private fun NewTabFavoriteLibraryGrid(
    library: FavoriteLibrary,
    favicons: Map<String, Bitmap>,
    folderIcons: Map<String, Bitmap>,
    enabled: Boolean,
    animateShapes: Boolean,
    animationSpeed: FavoriteAnimationSpeed,
    onFavorite: (FavoriteEntry, Offset, NewTabFavoriteMorphState) -> Unit,
    onReorder: (String, Int) -> Unit,
    modifier: Modifier,
) {
    var folderId by rememberSaveable(library.entries) { mutableStateOf<String?>(null) }
    val entries = remember(library, folderId) { BrowsingFavoritesRules.children(library, folderId) }
    val view = LocalView.current
    val bounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val iconCenters = remember { mutableStateMapOf<String, Offset>() }
    var drag by remember { mutableStateOf<NewTabFavoriteDrag?>(null) }
    val destination = drag?.destinationIndex ?: -1

    BackHandler(enabled = folderId != null) {
        folderId = folderId?.let { id ->
            BrowsingFavoritesRules.folder(library, id)?.parentFolderId
        }
    }
    AnimatedContent(
        targetState = folderId,
        transitionSpec = {
            (slideInHorizontally { it / 6 } + fadeIn()).togetherWith(
                slideOutHorizontally { -it / 6 } + fadeOut(),
            )
        },
        label = "new-tab-folder-navigation",
        modifier = modifier,
    ) { displayedFolderId ->
        val displayed = remember(library, displayedFolderId) {
            BrowsingFavoritesRules.children(library, displayedFolderId)
        }
        val favoriteUrls = displayed.filterIsInstance<FavoriteEntry>().map(FavoriteEntry::url)
        val variants = remember(favoriteUrls) {
            NewTabFavoriteShapeRules.startVariants(favoriteUrls)
                .let { values -> favoriteUrls.mapIndexed { index, url -> url to values[index] }.toMap() }
        }
        val morphs = rememberNewTabFavoriteMorphs()
        val shapeClock: NewTabFavoriteShapeClock? = null
        var gridOriginInWindow by remember { mutableStateOf(Offset.Zero) }
        val dragIconRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                gridOriginInWindow = coordinates.boundsInWindow().topLeft
            },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(NewTabFavoriteGridRules.COLUMN_COUNT),
                modifier = Modifier
                    .height(
                        NewTabFavoriteGridRules.containerHeightDp(
                            itemCount = displayed.size,
                            hasUpNavigation = displayedFolderId != null,
                        ).dp,
                    )
                    .testTag(NewTabFavoritesTestTags.Container),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = NewTabFavoriteGridRules.VERTICAL_CONTENT_PADDING_DP.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = enabled && drag == null,
            ) {
                if (displayedFolderId != null) {
                    item(key = "new-tab-folder-up") {
                        NewTabFolderUpShortcut(
                            enabled = enabled && drag == null,
                            onClick = {
                                folderId = BrowsingFavoritesRules.folder(library, displayedFolderId)
                                    ?.parentFolderId
                            },
                        )
                    }
                }
                items(items = displayed, key = FavoriteLibraryEntry::id) { entry ->
                    val index = displayed.indexOfFirst { it.id == entry.id }
                    val itemDrag = drag?.takeIf { it.entryId == entry.id }
                    val slotOffset = NewTabFavoriteReorderMotion.slotOffset(
                        entries = displayed,
                        sourceIndex = drag?.sourceIndex,
                        destinationIndex = drag?.destinationIndex,
                        index = index,
                        bounds = bounds,
                    )
                    val dragModifier = Modifier
                        .onGloballyPositioned { bounds[entry.id] = it.boundsInWindow() }
                        .newTabFavoriteSlotMotion(
                            isDragged = itemDrag != null,
                            targetOffset = slotOffset,
                        )
                        .newTabFavoriteReorderGesture(
                            enabled = enabled && (drag == null || itemDrag != null),
                            onStart = { position ->
                                if (displayed.size < 2) return@newTabFavoriteReorderGesture false
                                drag = NewTabFavoriteDrag(
                                    entry.id,
                                    index,
                                    index,
                                iconCenters[entry.id] ?: bounds[entry.id]?.center ?: position,
                                )
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                view.startRubberbandHaptic()
                                true
                            },
                            onDrag = { amount ->
                                val current = drag ?: return@newTabFavoriteReorderGesture
                                val projected = current.startCenter + current.offset + amount
                                val next = displayed.indices.minByOrNull { candidate ->
                                    val center = bounds[displayed[candidate].id]?.center ?: projected
                                    val delta = center - projected
                                    delta.x * delta.x + delta.y * delta.y
                                } ?: current.destinationIndex
                                if (next != current.destinationIndex) {
                                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                                }
                                drag = current.copy(destinationIndex = next, offset = current.offset + amount)
                            },
                            onEnd = { commit ->
                                val completed = drag
                                drag = null
                                view.stopRubberbandHaptic()
                                if (commit && completed != null && completed.destinationIndex != completed.sourceIndex) {
                                    onReorder(completed.entryId, completed.destinationIndex)
                                    view.performConfirmHaptic()
                                }
                            },
                        )
                    when (entry) {
                        is FavoriteEntry -> FavoriteShortcut(
                            favorite = entry,
                            favicon = favicons[entry.url],
                            enabled = enabled && drag == null,
                            startVariant = variants[entry.url] ?: NewTabFavoriteShapeVariant.Circle,
                            shapeClock = shapeClock,
                            shapeMorphs = morphs,
                            onClick = onFavorite,
                            onIconPositioned = { iconCenters[entry.id] = it },
                            modifier = dragModifier.graphicsLayer {
                                alpha = if (itemDrag != null) 0.18f else 1f
                            },
                        )
                        is FavoriteFolder -> NewTabFolderShortcut(
                            folder = entry,
                            previewFavorites = newTabFolderPreviewFavorites(library, entry.id),
                            favicons = favicons,
                            folderIcons = folderIcons,
                        enabled = enabled && drag == null,
                        onClick = { folderId = entry.id },
                        onIconPositioned = { iconCenters[entry.id] = it },
                            modifier = dragModifier.graphicsLayer {
                                alpha = if (itemDrag != null) 0.18f else 1f
                            },
                        )
                    }
                }
            }
            val activeDrag = drag
            val activeEntry = displayed.firstOrNull { it.id == activeDrag?.entryId }
            if (activeDrag != null && activeEntry != null) {
                val previewCenter = activeDrag.startCenter + activeDrag.offset - gridOriginInWindow
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (previewCenter.x - dragIconRadiusPx).roundToInt(),
                                (previewCenter.y - dragIconRadiusPx).roundToInt(),
                            )
                        }
                        .size(48.dp)
                        .zIndex(2f)
                        .testTag(NewTabFavoritesTestTags.DragPreview)
                        .graphicsLayer {
                            shape = CircleShape
                            clip = true
                            scaleX = 1.06f
                            scaleY = 1.06f
                            shadowElevation = 12.dp.toPx()
                        },
                ) {
                    when (activeEntry) {
                        is FavoriteEntry -> NewTabFavoriteIcon(
                            favorite = activeEntry,
                            favicon = favicons[activeEntry.url],
                            modifier = Modifier.fillMaxSize(),
                            imageTestTag = null,
                            shape = CircleShape,
                        )
                        is FavoriteFolder -> NewTabFolderIcon(
                            folder = activeEntry,
                            previewFavorites = newTabFolderPreviewFavorites(library, activeEntry.id),
                            favicons = favicons,
                            folderIcons = folderIcons,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
    DisposableEffect(drag) {
        onDispose { if (drag != null) view.stopRubberbandHaptic() }
    }
}

private data class NewTabFavoriteDrag(
    val entryId: String,
    val sourceIndex: Int,
    val destinationIndex: Int,
    val startCenter: Offset,
    val offset: Offset = Offset.Zero,
)

private object NewTabFavoriteReorderMotion {
    fun slotOffset(
        entries: List<FavoriteLibraryEntry>,
        sourceIndex: Int?,
        destinationIndex: Int?,
        index: Int,
        bounds: Map<String, androidx.compose.ui.geometry.Rect>,
    ): Offset {
        if (sourceIndex == null || destinationIndex == null || index == sourceIndex) return Offset.Zero
        val shiftedIndex = when {
            destinationIndex > sourceIndex && index in (sourceIndex + 1)..destinationIndex -> index - 1
            destinationIndex < sourceIndex && index in destinationIndex until sourceIndex -> index + 1
            else -> index
        }
        if (shiftedIndex == index) return Offset.Zero
        val from = bounds[entries[index].id]?.topLeft ?: return Offset.Zero
        val to = bounds[entries[shiftedIndex].id]?.topLeft ?: return Offset.Zero
        return to - from
    }
}

internal fun newTabFolderPreviewFavorites(
    library: FavoriteLibrary,
    folderId: String,
): List<FavoriteEntry> {
    if (BrowsingFavoritesRules.folder(library, folderId) == null) return emptyList()
    val visited = hashSetOf<String>()
    fun collect(parentId: String): List<FavoriteEntry> {
        if (!visited.add(parentId)) return emptyList()
        return BrowsingFavoritesRules.children(library, parentId).flatMap { entry ->
            when (entry) {
                is FavoriteEntry -> listOf(entry)
                is FavoriteFolder -> collect(entry.id)
            }
        }
    }
    return collect(folderId).take(4)
}

@Composable
private fun Modifier.newTabFavoriteSlotMotion(
    isDragged: Boolean,
    targetOffset: Offset,
): Modifier {
    val animatedOffset = animateValueAsState(
        targetValue = if (isDragged) Offset.Zero else targetOffset,
        typeConverter = Offset.VectorConverter,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
        label = "new-tab-favorite-slot",
    )
    return graphicsLayer {
        translationX = animatedOffset.value.x
        translationY = animatedOffset.value.y
    }
}

private fun Modifier.newTabFavoriteReorderGesture(
    enabled: Boolean,
    onStart: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onEnd: (Boolean) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    var accepted = false
    detectDragGesturesAfterLongPress(
        onDragStart = { position -> accepted = onStart(position) },
        onDrag = { change, amount -> if (accepted) { change.consume(); onDrag(amount) } },
        onDragEnd = { if (accepted) onEnd(true); accepted = false },
        onDragCancel = { if (accepted) onEnd(false); accepted = false },
    )
}

@Composable
private fun rememberNewTabFavoriteShapeClock(
    animateShapes: Boolean,
    animationSpeed: FavoriteAnimationSpeed,
): NewTabFavoriteShapeClock? {
    val clock = remember(animationSpeed) { NewTabFavoriteShapeClock(animationSpeed) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(clock, animateShapes, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (animateShapes) clock.start()
                Lifecycle.Event.ON_STOP -> clock.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (
            animateShapes &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            clock.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clock.stop()
        }
    }
    return clock.takeIf { animateShapes }
}

@Composable
private fun FavoriteShortcut(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
    enabled: Boolean,
    startVariant: NewTabFavoriteShapeVariant,
    shapeClock: NewTabFavoriteShapeClock?,
    shapeMorphs: List<Morph>,
    onClick: (FavoriteEntry, Offset, NewTabFavoriteMorphState) -> Unit,
    onIconPositioned: (Offset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var centerInWindow by remember(favorite.url) { mutableStateOf(Offset.Unspecified) }
    val shapeMotion = remember(startVariant, shapeClock, shapeMorphs) {
        NewTabFavoriteShapeMotion(
            start = startVariant,
            clock = shapeClock,
            morphs = shapeMorphs,
        )
    }
    Surface(
        onClick = { onClick(favorite, centerInWindow, shapeMotion.currentState()) },
        enabled = enabled,
        modifier = Modifier
            .height(NewTabFavoriteGridRules.CELL_HEIGHT_DP.dp)
            .testTag(NewTabFavoritesTestTags.favorite(favorite.url))
            .then(modifier),
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
                    modifier = Modifier
                        .size(36.dp)
                        .onGloballyPositioned { coordinates ->
                            val center = coordinates.boundsInWindow().center
                            centerInWindow = center
                            onIconPositioned(center)
                        },
                    imageTestTag = NewTabFavoritesTestTags.favicon(favorite.url),
                    shape = CircleShape,
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
private fun NewTabFolderUpShortcut(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(NewTabFavoriteGridRules.CELL_HEIGHT_DP.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_arrow_up),
                contentDescription = stringResource(R.string.favorites_parent),
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewTabFolderShortcut(
    folder: FavoriteFolder,
    previewFavorites: List<FavoriteEntry>,
    favicons: Map<String, Bitmap>,
    folderIcons: Map<String, Bitmap>,
    enabled: Boolean,
    onClick: () -> Unit,
    onIconPositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(NewTabFavoriteGridRules.CELL_HEIGHT_DP.dp)
            .testTag(NewTabFavoritesTestTags.folder(folder.id))
            .then(modifier),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NewTabFolderIcon(
                folder = folder,
                previewFavorites = previewFavorites,
                favicons = favicons,
                folderIcons = folderIcons,
                modifier = Modifier
                    .size(36.dp)
                    .onGloballyPositioned { coordinates ->
                        onIconPositioned(coordinates.boundsInWindow().center)
                    },
            )
            Text(
                text = folder.title,
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

@Composable
private fun NewTabFolderIcon(
    folder: FavoriteFolder,
    previewFavorites: List<FavoriteEntry>,
    favicons: Map<String, Bitmap>,
    folderIcons: Map<String, Bitmap>,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (val icon = folder.icon) {
                is FavoriteFolderIcon.Emoji ->
                    Text(icon.value, style = MaterialTheme.typography.titleMedium)
                FavoriteFolderIcon.Custom -> {
                    val customIcon = folderIcons[folder.id]
                    if (customIcon != null && !customIcon.isRecycled) {
                        Image(
                            bitmap = customIcon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            folder.title.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                null -> NewTabFolderPreview(
                    favorites = previewFavorites,
                    favicons = favicons,
                )
            }
        }
    }
}

@Composable
private fun NewTabFolderPreview(
    favorites: List<FavoriteEntry>,
    favicons: Map<String, Bitmap>,
) {
    val preview = favorites.take(4)
    if (preview.isEmpty()) {
        Text("+", style = MaterialTheme.typography.titleMedium)
        return
    }
    Column(
        modifier = Modifier.padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        preview.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { favorite ->
                    Surface(
                        modifier = Modifier.size(14.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        val favicon = favicons[favorite.url]
                        if (favicon != null && !favicon.isRecycled) {
                            Image(
                                bitmap = favicon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    favoriteInitial(favorite),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewTabFavoriteLaunchOverlay(
    request: NewTabFavoriteLaunchRequest,
    favicon: Bitmap?,
    animationSpeed: FavoriteAnimationSpeed,
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
                durationMillis = NewTabFavoriteLaunchMotionRules.durationMillis(animationSpeed),
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
    val iconShape = newTabFavoriteMorphShape(request.shapeState)

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
                    shape = iconShape
                    clip = true
                },
            imageTestTag = null,
            shape = iconShape,
        )
    }
}

@Composable
private fun NewTabFavoriteIcon(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
    modifier: Modifier,
    imageTestTag: String?,
    shape: Shape,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
    ) {
        NewTabFavoriteIconContent(favorite, favicon, imageTestTag)
    }
}

@Composable
private fun NewTabFavoriteMorphingIcon(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
    modifier: Modifier,
    imageTestTag: String?,
    shapeMotion: NewTabFavoriteShapeMotion,
) {
    val backgroundColor = MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier = modifier.newTabFavoriteShapeClip(shapeMotion, backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        NewTabFavoriteIconContent(favorite, favicon, imageTestTag)
    }
}

@Composable
private fun NewTabFavoriteIconContent(
    favorite: FavoriteEntry,
    favicon: Bitmap?,
    imageTestTag: String?,
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
                    .then(
                        if (imageTestTag != null) {
                            Modifier.testTag(imageTestTag)
                        } else {
                            Modifier
                        },
                    ),
                contentScale = ContentScale.Crop,
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

private class NewTabFavoriteShapeMotion(
    private val start: NewTabFavoriteShapeVariant,
    internal val clock: NewTabFavoriteShapeClock?,
    private val morphs: List<Morph>,
) {
    fun currentState(): NewTabFavoriteMorphState =
        NewTabFavoriteShapeRules.morphState(start, clock?.phase ?: 0f)

    fun path(size: Size): Path {
        val state = currentState()
        return newTabFavoriteMorphPath(
            morph = morphs[state.from.ordinal],
            progress = FastOutSlowInEasing.transform(state.progress.coerceIn(0f, 1f)),
            size = size,
        )
    }
}

private class NewTabFavoriteShapeClock(
    private val animationSpeed: FavoriteAnimationSpeed,
) {
    var phase: Float = 0f
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var segment = 0
    private var segmentStartedAtMillis = 0L
    private var running = false
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val elapsedMillis = SystemClock.uptimeMillis() - segmentStartedAtMillis
            val progress = NewTabFavoriteShapeRules.morphProgress(
                elapsedMillis = elapsedMillis,
                durationMillis = animationSpeed.morphDurationMillis,
            )
            phase = segment + progress
            listeners.toList().forEach { listener -> listener() }
            if (progress >= 1f) {
                segment = (segment + 1) % NewTabFavoriteShapeVariant.entries.size
                phase = segment.toFloat()
                segmentStartedAtMillis = SystemClock.uptimeMillis() +
                    animationSpeed.morphPauseMillis
                handler.postDelayed(
                    this,
                    animationSpeed.morphPauseMillis.toLong(),
                )
            } else {
                handler.postDelayed(
                    this,
                    NewTabFavoriteShapeRules.MORPH_FRAME_INTERVAL_MILLIS.toLong(),
                )
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        segment = phase.toInt() % NewTabFavoriteShapeVariant.entries.size
        phase = segment.toFloat()
        listeners.toList().forEach { listener -> listener() }
        segmentStartedAtMillis = SystemClock.uptimeMillis() +
            animationSpeed.morphPauseMillis
        handler.postDelayed(
            frame,
            animationSpeed.morphPauseMillis.toLong(),
        )
    }

    fun stop() {
        running = false
        handler.removeCallbacks(frame)
    }

    fun subscribe(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}

private fun Modifier.newTabFavoriteShapeClip(
    shapeMotion: NewTabFavoriteShapeMotion,
    backgroundColor: Color,
): Modifier = this then NewTabFavoriteShapeClipElement(shapeMotion, backgroundColor)

private data class NewTabFavoriteShapeClipElement(
    val shapeMotion: NewTabFavoriteShapeMotion,
    val backgroundColor: Color,
) : ModifierNodeElement<NewTabFavoriteShapeClipNode>() {
    override fun create(): NewTabFavoriteShapeClipNode =
        NewTabFavoriteShapeClipNode(shapeMotion, backgroundColor)

    override fun update(node: NewTabFavoriteShapeClipNode) {
        node.update(shapeMotion, backgroundColor)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "newTabFavoriteShapeClip"
        properties["backgroundColor"] = backgroundColor
    }
}

private class NewTabFavoriteShapeClipNode(
    private var shapeMotion: NewTabFavoriteShapeMotion,
    private var backgroundColor: Color,
) : Modifier.Node(), DrawModifierNode {
    private var unsubscribe: (() -> Unit)? = null
    private val onFrame: () -> Unit = { invalidateDraw() }

    override fun onAttach() {
        subscribeToClock()
    }

    override fun onDetach() {
        unsubscribe?.invoke()
        unsubscribe = null
    }

    fun update(
        shapeMotion: NewTabFavoriteShapeMotion,
        backgroundColor: Color,
    ) {
        val clockChanged = this.shapeMotion.clock !== shapeMotion.clock
        this.shapeMotion = shapeMotion
        this.backgroundColor = backgroundColor
        if (clockChanged && isAttached) subscribeToClock()
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val contentScope = this
        clipPath(shapeMotion.path(size)) {
            drawRect(backgroundColor)
            contentScope.drawContent()
        }
    }

    private fun subscribeToClock() {
        unsubscribe?.invoke()
        unsubscribe = shapeMotion.clock?.subscribe(onFrame)
    }
}

@Composable
private fun newTabFavoriteMorphShape(state: NewTabFavoriteMorphState): Shape = CircleShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberNewTabFavoriteMorphs(): List<Morph> = remember {
    listOf(Morph(MaterialShapes.Circle, MaterialShapes.Circle))
}

private class NewTabFavoriteMorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(newTabFavoriteMorphPath(morph, progress, size))
}

private fun newTabFavoriteMorphPath(
    morph: Morph,
    progress: Float,
    size: Size,
): Path = Path().apply {
    val cubics = morph.asCubics(progress)
    cubics.firstOrNull()?.let { first ->
        moveTo(first.anchor0X * size.width, first.anchor0Y * size.height)
        cubics.forEach { cubic ->
            cubicTo(
                cubic.control0X * size.width,
                cubic.control0Y * size.height,
                cubic.control1X * size.width,
                cubic.control1Y * size.height,
                cubic.anchor1X * size.width,
                cubic.anchor1Y * size.height,
            )
        }
        close()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun newTabFavoritePolygon(variant: NewTabFavoriteShapeVariant): RoundedPolygon =
    when (variant) {
        NewTabFavoriteShapeVariant.Circle -> MaterialShapes.Circle
        NewTabFavoriteShapeVariant.RoundedSquare -> MaterialShapes.Square
        NewTabFavoriteShapeVariant.Arch -> MaterialShapes.Arch
        NewTabFavoriteShapeVariant.Fan -> MaterialShapes.Fan
        NewTabFavoriteShapeVariant.Triangle -> MaterialShapes.Triangle
        NewTabFavoriteShapeVariant.Diamond -> MaterialShapes.Diamond
        NewTabFavoriteShapeVariant.ClamShell -> MaterialShapes.ClamShell
        NewTabFavoriteShapeVariant.Pentagon -> MaterialShapes.Pentagon
        NewTabFavoriteShapeVariant.Gem -> MaterialShapes.Gem
        NewTabFavoriteShapeVariant.FourSidedCookie -> MaterialShapes.Cookie4Sided
        NewTabFavoriteShapeVariant.SixSidedCookie -> MaterialShapes.Cookie6Sided
        NewTabFavoriteShapeVariant.SevenSidedCookie -> MaterialShapes.Cookie7Sided
        NewTabFavoriteShapeVariant.NineSidedCookie -> MaterialShapes.Cookie9Sided
        NewTabFavoriteShapeVariant.TwelveSidedCookie -> MaterialShapes.Cookie12Sided
        NewTabFavoriteShapeVariant.FourLeafClover -> MaterialShapes.Clover4Leaf
        NewTabFavoriteShapeVariant.EightLeafClover -> MaterialShapes.Clover8Leaf
        NewTabFavoriteShapeVariant.Flower -> MaterialShapes.Flower
        NewTabFavoriteShapeVariant.Puffy -> MaterialShapes.Puffy
        NewTabFavoriteShapeVariant.PuffyDiamond -> MaterialShapes.PuffyDiamond
        NewTabFavoriteShapeVariant.Bun -> MaterialShapes.Bun
    }

private fun launchColor(index: Int, first: Color, second: Color): Color =
    if (index % 2 == 0) first else second

private const val FAVORITE_LAUNCH_ICON_SIZE_DP = 44
private const val TRAIL_POINT_COUNT = 5
private const val SPARKLE_COUNT = 8
