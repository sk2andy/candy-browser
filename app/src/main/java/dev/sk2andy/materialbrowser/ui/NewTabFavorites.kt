package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.testTag
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
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
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
    const val CELL_HEIGHT_DP = 84
    const val VERTICAL_CONTENT_PADDING_DP = 12

    fun rowCount(itemCount: Int): Int =
        (itemCount.coerceAtLeast(0) + COLUMN_COUNT - 1) / COLUMN_COUNT

    fun containerHeightDp(itemCount: Int): Int {
        val visibleRows = rowCount(itemCount).coerceAtMost(MAX_VISIBLE_ROWS)
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
        val variants = NewTabFavoriteShapeVariant.entries
        val seed = stableHash(urls)
        val offset = Math.floorMod(seed, variants.size)
        val stride = COPRIME_STEPS[Math.floorMod(seed xor (seed ushr 16), COPRIME_STEPS.size)]
        return urls.indices.map { index ->
            variants[Math.floorMod(offset + index * stride, variants.size)]
        }
    }

    fun morphState(
        start: NewTabFavoriteShapeVariant,
        phase: Float,
    ): NewTabFavoriteMorphState {
        val variants = NewTabFavoriteShapeVariant.entries
        val safePhase = phase.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        val segment = safePhase.toInt()
        val progress = safePhase - segment
        val fromIndex = Math.floorMod(start.ordinal + segment, variants.size)
        return NewTabFavoriteMorphState(
            from = variants[fromIndex],
            to = variants[(fromIndex + 1) % variants.size],
            progress = progress,
        )
    }

    fun morphProgress(
        elapsedMillis: Long,
        durationMillis: Int,
    ): Float {
        if (durationMillis <= 0) return 1f
        return elapsedMillis.coerceIn(0L, durationMillis.toLong()) / durationMillis.toFloat()
    }

    private fun stableHash(values: List<String>): Int {
        var hash = FNV_OFFSET_BASIS
        values.forEach { value ->
            value.forEach { character ->
                hash = hash xor character.code
                hash *= FNV_PRIME
            }
            hash = hash xor VALUE_SEPARATOR
            hash *= FNV_PRIME
        }
        return hash
    }

    private val COPRIME_STEPS = intArrayOf(1, 3, 7, 9, 11, 13, 17, 19)
    private const val FNV_OFFSET_BASIS = -2_128_831_035
    private const val FNV_PRIME = 16_777_619
    private const val VALUE_SEPARATOR = 0xFF
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
    val shapeState: NewTabFavoriteMorphState,
)

@Composable
internal fun NewTabFavoriteGrid(
    favorites: List<FavoriteEntry>,
    favicons: Map<String, Bitmap>,
    enabled: Boolean,
    animateShapes: Boolean,
    animationSpeed: FavoriteAnimationSpeed,
    onFavorite: (FavoriteEntry, Offset, NewTabFavoriteMorphState) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) return
    val favoriteUrls = favorites.map(FavoriteEntry::url)
    val startVariantsByUrl = remember(favoriteUrls) {
        val variants = NewTabFavoriteShapeRules.startVariants(favoriteUrls)
        favoriteUrls.mapIndexed { index, url -> url to variants[index] }.toMap()
    }
    val shapeMorphs = rememberNewTabFavoriteMorphs()
    val shapeClock = rememberNewTabFavoriteShapeClock(animateShapes, animationSpeed)
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
                NewTabFavoriteMorphingIcon(
                    favorite = favorite,
                    favicon = favicon,
                    modifier = Modifier.size(36.dp),
                    imageTestTag = NewTabFavoritesTestTags.favicon(favorite.url),
                    shapeMotion = shapeMotion,
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
private fun newTabFavoriteMorphShape(state: NewTabFavoriteMorphState): Shape {
    val from = newTabFavoritePolygon(state.from)
    val to = newTabFavoritePolygon(state.to)
    val morph = remember(from, to) { Morph(from, to) }
    return NewTabFavoriteMorphShape(
        morph = morph,
        progress = FastOutSlowInEasing.transform(state.progress.coerceIn(0f, 1f)),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberNewTabFavoriteMorphs(): List<Morph> = remember {
    val polygons = NewTabFavoriteShapeVariant.entries.map(::newTabFavoritePolygon)
    polygons.indices.map { index ->
        Morph(polygons[index], polygons[(index + 1) % polygons.size])
    }
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
