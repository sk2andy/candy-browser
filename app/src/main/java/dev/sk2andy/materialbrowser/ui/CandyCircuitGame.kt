package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.CandyPink
import dev.sk2andy.materialbrowser.ui.theme.CandyPinkSoft
import dev.sk2andy.materialbrowser.ui.theme.CandyPurple
import dev.sk2andy.materialbrowser.ui.theme.CandyPurpleSoft
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CandyCircuitResolution(
    val id: Int,
    val rotatedIndex: Int,
    val outgoingTiles: List<CandyCircuitTile>,
    val incomingTiles: List<CandyCircuitTile>,
    val closedIndices: Set<Int>,
)

@Composable
internal fun CandyCircuitGame(
    isOnlineReady: Boolean,
    game: CandyCircuitGameState,
    onReload: () -> Unit,
    onGameChange: (CandyCircuitGameState) -> Unit,
) {
    val hapticView = LocalView.current
    var resolution by remember { mutableStateOf<CandyCircuitResolution?>(null) }
    var resolutionSequence by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PageErrorFeedbackTestTags.Game),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectionHeader(
            isOnlineReady = isOnlineReady,
            onReload = onReload,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.page_error_candy_circuit_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall.merge(
                    TextStyle(
                        brush = Brush.horizontalGradient(
                            listOf(
                                CandyPink,
                                CandyPurple,
                            ),
                        ),
                    ),
                ),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            CircuitMetrics(game)
            Spacer(Modifier.height(14.dp))
            CandyCircuitBoard(
                game = game,
                resolution = resolution,
                onRotate = { tileIndex ->
                    if (resolution != null) return@CandyCircuitBoard
                    val next = CandyCircuitRules.rotate(game, tileIndex)
                    if (next === game) return@CandyCircuitBoard
                    if (next.lastPointsGained > 0) {
                        hapticView.performConfirmHaptic()
                        val outgoingTiles = game.tiles.toMutableList().apply {
                            this[tileIndex] = this[tileIndex].copy(
                                rotation = this[tileIndex].rotation + 1,
                            )
                        }
                        resolutionSequence += 1
                        resolution = CandyCircuitResolution(
                            id = resolutionSequence,
                            rotatedIndex = tileIndex,
                            outgoingTiles = outgoingTiles,
                            incomingTiles = next.tiles,
                            closedIndices = next.lastClosedTileIndices,
                        )
                    } else {
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    onGameChange(next)
                },
                onResolutionFinished = { finishedId ->
                    if (resolution?.id == finishedId) resolution = null
                },
            )
            Spacer(Modifier.height(14.dp))
            CircuitResult(game = game, onRestart = {
                hapticView.performConfirmHaptic()
                resolution = null
                onGameChange(CandyCircuitRules.restart(game))
            })
            Spacer(Modifier.height(10.dp))
            CircuitRecordProgress(game)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.page_error_candy_circuit_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConnectionHeader(
    isOnlineReady: Boolean,
    onReload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isOnlineReady) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CandyPurpleSoft, RoundedCornerShape(28.dp))
                    .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
                    .testTag(PageErrorFeedbackTestTags.OnlineBanner)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectivityGlyph(
                    isOnline = true,
                    color = CandyPurple,
                    modifier = Modifier.size(38.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.page_error_back_online),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.page_error_back_online_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onReload,
                    modifier = Modifier.testTag(PageErrorFeedbackTestTags.Retry),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.page_error_load_page))
                }
            }
        } else {
            OfflinePill()
        }
    }
}

@Composable
private fun CircuitMetrics(game: CandyCircuitGameState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircuitMetric(
            label = stringResource(R.string.page_error_game_score_label),
            value = formattedScore(game.score),
            tint = CandyPink,
            background = CandyPinkSoft,
            modifier = Modifier
                .weight(1f)
                .testTag(PageErrorFeedbackTestTags.Score),
        )
        CircuitMetric(
            label = stringResource(R.string.page_error_game_best_label),
            value = formattedScore(game.bestScore),
            tint = CandyPurple,
            background = CandyPurpleSoft,
            modifier = Modifier
                .weight(1f)
                .testTag(PageErrorFeedbackTestTags.BestScore),
        )
        CircuitMetric(
            label = stringResource(R.string.page_error_game_moves_label),
            value = game.movesRemaining.toString(),
            unit = stringResource(R.string.page_error_game_moves_unit),
            tint = CandyPurple,
            background = CandyPurpleSoft,
            modifier = Modifier
                .weight(1f)
                .testTag(PageErrorFeedbackTestTags.Moves),
        )
    }
}

@Composable
private fun CircuitMetric(
    label: String,
    value: String,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    unit: String = "",
) {
    Surface(
        modifier = modifier
            .height(92.dp)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(18.dp),
        color = background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.Bold,
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = tween(durationMillis = 280),
                        initialOffsetY = { height -> height / 2 },
                    ) + fadeIn(animationSpec = tween(durationMillis = 180))) togetherWith
                        (slideOutVertically(
                            animationSpec = tween(durationMillis = 220),
                            targetOffsetY = { height -> -height / 2 },
                        ) + fadeOut(animationSpec = tween(durationMillis = 140)))
                },
                label = "circuit metric value",
            ) { animatedValue ->
                Text(
                    text = animatedValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = unit.ifEmpty { " " },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CandyCircuitBoard(
    game: CandyCircuitGameState,
    resolution: CandyCircuitResolution?,
    onRotate: (Int) -> Unit,
    onResolutionFinished: (Int) -> Unit,
) {
    val resolutionProgress = remember(resolution?.id) {
        Animatable(if (resolution == null) 1f else 0f)
    }
    LaunchedEffect(resolution?.id) {
        val activeResolution = resolution ?: return@LaunchedEffect
        resolutionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = CandyCircuitMotionRules.RESOLUTION_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        )
        onResolutionFinished(activeResolution.id)
    }
    val closedIndices = resolution?.closedIndices.orEmpty()
    val orderedClosedIndices = closedIndices.sorted()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .then(
                if (resolution == null) Modifier
                else Modifier.testTag(PageErrorFeedbackTestTags.Celebration),
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(CandyCircuitRules.ROW_COUNT) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(CandyCircuitRules.COLUMN_COUNT) { column ->
                    val index = row * CandyCircuitRules.COLUMN_COUNT + column
                    CandyCircuitTile(
                        tile = game.tiles[index],
                        tileIndex = index,
                        row = row,
                        column = column,
                        outgoingTile = resolution
                            ?.takeIf { index in closedIndices }
                            ?.outgoingTiles
                            ?.get(index),
                        incomingTile = resolution
                            ?.takeIf { index in closedIndices }
                            ?.incomingTiles
                            ?.get(index),
                        resolutionProgress = resolutionProgress.value,
                        resolutionOrder = orderedClosedIndices.indexOf(index).coerceAtLeast(0),
                        resolutionCount = orderedClosedIndices.size,
                        isRotatedTile = resolution?.rotatedIndex == index,
                        enabled = !game.isGameOver && resolution == null,
                        onRotate = { onRotate(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CandyCircuitTile(
    tile: CandyCircuitTile,
    tileIndex: Int,
    row: Int,
    column: Int,
    outgoingTile: CandyCircuitTile?,
    incomingTile: CandyCircuitTile?,
    resolutionProgress: Float,
    resolutionOrder: Int,
    resolutionCount: Int,
    isRotatedTile: Boolean,
    enabled: Boolean,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticTile = if (
        outgoingTile != null &&
        incomingTile != null &&
        CandyCircuitMotionRules.outgoingAlpha(resolutionProgress) > 0.5f
    ) {
        outgoingTile
    } else {
        tile
    }
    val connections = CandyCircuitRules.connections(semanticTile)
    val shapeName = circuitShapeName(semanticTile.shape)
    val directionNames = mapOf(
        CandyCircuitDirection.North to stringResource(R.string.page_error_game_direction_north),
        CandyCircuitDirection.East to stringResource(R.string.page_error_game_direction_east),
        CandyCircuitDirection.South to stringResource(R.string.page_error_game_direction_south),
        CandyCircuitDirection.West to stringResource(R.string.page_error_game_direction_west),
    )
    val separator = stringResource(R.string.page_error_game_connection_separator)
    val connectionNames = connections
        .sortedBy(CandyCircuitDirection::ordinal)
        .joinToString(separator) { directionNames.getValue(it) }
    val description = stringResource(
        R.string.page_error_game_tile_description,
        row + 1,
        column + 1,
        shapeName,
        connectionNames,
    )
    val clickLabel = stringResource(R.string.page_error_game_rotate_tile)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag(PageErrorFeedbackTestTags.TilePrefix + tileIndex)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = clickLabel,
                onClick = onRotate,
            )
            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier)
            .semantics(mergeDescendants = true) {
                this.contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        if (outgoingTile == null || incomingTile == null) {
            val animatedBackground by animateColorAsState(
                targetValue = circuitTileBackground(tileIndex),
                label = "circuit tile background",
            )
            val rotation by animateFloatAsState(
                targetValue = tile.rotation * 90f,
                animationSpec = tween(durationMillis = 220),
                label = "circuit tile rotation",
            )
            CircuitTileFace(
                tile = tile,
                tileIndex = tileIndex,
                rotation = rotation,
                background = animatedBackground,
            )
        } else {
            val closingRotation = CandyCircuitMotionRules.closingRotationProgress(resolutionProgress)
            val outgoingRotation = if (isRotatedTile) {
                (outgoingTile.rotation - 1) * 90f + closingRotation * 90f
            } else {
                outgoingTile.rotation * 90f
            }
            val outgoingAlpha = CandyCircuitMotionRules.outgoingAlpha(resolutionProgress)
            val outgoingScale = CandyCircuitMotionRules.outgoingScale(resolutionProgress)
            val glowAlpha = CandyCircuitMotionRules.glowAlpha(resolutionProgress)
            val incomingProgress = CandyCircuitMotionRules.incomingProgress(
                progress = resolutionProgress,
                order = resolutionOrder,
                count = resolutionCount,
            )
            val incomingScale = CandyCircuitMotionRules.incomingScale(incomingProgress)
            val incomingOffset = CandyCircuitMotionRules.incomingOffsetFraction(incomingProgress)
            CircuitTileFace(
                tile = outgoingTile,
                tileIndex = tileIndex,
                rotation = outgoingRotation,
                background = lerp(
                    circuitTileBackground(tileIndex),
                    CandyPink.copy(alpha = 0.42f),
                    glowAlpha,
                ),
                emphasized = true,
                modifier = Modifier.graphicsLayer {
                    alpha = outgoingAlpha
                    scaleX = outgoingScale
                    scaleY = outgoingScale
                    rotationZ = CandyCircuitMotionRules.particleProgress(resolutionProgress) *
                        if (tileIndex % 2 == 0) -9f else 9f
                },
            )
            CircuitTileFace(
                tile = incomingTile,
                tileIndex = tileIndex,
                rotation = incomingTile.rotation * 90f,
                background = circuitTileBackground(tileIndex),
                modifier = Modifier.graphicsLayer {
                    alpha = incomingProgress
                    scaleX = incomingScale
                    scaleY = incomingScale
                    translationY = size.height * incomingOffset
                    rotationZ = (1f - incomingProgress) * if (tileIndex % 2 == 0) 7f else -7f
                },
            )
            CandyCircuitParticles(
                progress = CandyCircuitMotionRules.particleProgress(resolutionProgress),
                tileIndex = tileIndex,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CircuitTileFace(
    tile: CandyCircuitTile,
    tileIndex: Int,
    rotation: Float,
    background: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        color = background,
        tonalElevation = if (emphasized) 7.dp else 3.dp,
        shadowElevation = if (emphasized) 9.dp else 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            CandyCircuitRibbon(
                shape = tile.shape,
                rotation = rotation,
                color = circuitTileColor(tileIndex),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CandyCircuitParticles(
    progress: Float,
    tileIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val alpha = sin(progress * PI).toFloat().coerceIn(0f, 1f)
        repeat(PARTICLE_COUNT) { particle ->
            val angle = (particle.toFloat() / PARTICLE_COUNT * 2f * PI).toFloat() +
                tileIndex * PARTICLE_ANGLE_OFFSET
            val distance = size.minDimension * (0.12f + progress * 0.48f)
            val radius = size.minDimension * (0.035f - progress * 0.017f)
            drawCircle(
                color = if ((particle + tileIndex) % 2 == 0) CandyPink else CandyPurple,
                radius = radius.coerceAtLeast(1f),
                center = Offset(
                    x = center.x + cos(angle) * distance,
                    y = center.y + sin(angle) * distance,
                ),
                alpha = alpha,
            )
        }
    }
}

private fun circuitTileBackground(tileIndex: Int): Color =
    if (tileIndex % 2 == 0) CandyPink.copy(alpha = 0.08f)
    else CandyPurple.copy(alpha = 0.10f)

private fun circuitTileColor(tileIndex: Int): Color =
    if (tileIndex % 3 == 1) CandyPurple else CandyPink

private const val PARTICLE_COUNT = 10
private const val PARTICLE_ANGLE_OFFSET = 0.37f

@Composable
private fun CandyCircuitRibbon(
    shape: CandyCircuitTileShape,
    rotation: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val top = Offset(center.x, 0f)
        val right = Offset(size.width, center.y)
        val bottom = Offset(center.x, size.height)
        val left = Offset(0f, center.y)
        val strokeWidth = size.minDimension * 0.19f
        val highlightWidth = strokeWidth * 0.28f
        rotate(rotation) {
            val paths = when (shape) {
                CandyCircuitTileShape.Straight -> listOf(
                    Path().apply {
                        moveTo(top.x, top.y)
                        lineTo(bottom.x, bottom.y)
                    },
                )
                CandyCircuitTileShape.Curve -> listOf(
                    Path().apply {
                        moveTo(top.x, top.y)
                        quadraticTo(center.x, center.y, right.x, right.y)
                    },
                )
                CandyCircuitTileShape.Branch -> listOf(top, right, left).map { endpoint ->
                    Path().apply {
                        moveTo(center.x, center.y)
                        lineTo(endpoint.x, endpoint.y)
                    }
                }
            }
            paths.forEach { path ->
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.18f),
                    style = Stroke(
                        width = strokeWidth * 1.45f,
                        cap = StrokeCap.Round,
                    ),
                )
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(color.copy(alpha = 0.72f), color),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.42f),
                    style = Stroke(width = highlightWidth, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun CircuitResult(
    game: CandyCircuitGameState,
    onRestart: () -> Unit,
) {
    val status = when {
        game.isGameOver -> stringResource(R.string.page_error_game_round_complete, formattedScore(game.score))
        game.lastPointsGained > 0 -> stringResource(
            R.string.page_error_game_loop_closed,
            formattedScore(game.lastPointsGained),
            game.lastMovesGained,
            game.combo,
        )
        game.lastRotatedIndex != null -> stringResource(R.string.page_error_game_keep_turning)
        else -> stringResource(R.string.page_error_game_ready)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = status,
            modifier = Modifier.testTag(PageErrorFeedbackTestTags.Combo),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (game.lastPointsGained > 0 || game.isGameOver) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            color = if (game.lastPointsGained > 0) {
                CandyPink
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
        AnimatedVisibility(visible = game.isGameOver) {
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag(PageErrorFeedbackTestTags.Restart),
                shape = CircleShape,
            ) {
                Text(stringResource(R.string.page_error_game_new_round))
            }
        }
    }
}

@Composable
private fun CircuitRecordProgress(game: CandyCircuitGameState) {
    val progress = when {
        game.bestScore == 0 -> 0f
        game.score >= game.bestScore -> 1f
        else -> game.score.toFloat() / game.bestScore
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp),
            color = CandyPink,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(6.dp))
        val recordText = when {
            game.hasNewBest ->
                stringResource(R.string.page_error_game_new_record)
            game.bestScore > game.score -> stringResource(
                R.string.page_error_game_points_to_record,
                formattedScore(game.bestScore - game.score),
            )
            else -> stringResource(R.string.page_error_game_first_record)
        }
        Text(
            text = recordText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun circuitShapeName(shape: CandyCircuitTileShape): String = stringResource(
    when (shape) {
        CandyCircuitTileShape.Straight -> R.string.page_error_game_shape_straight
        CandyCircuitTileShape.Curve -> R.string.page_error_game_shape_curve
        CandyCircuitTileShape.Branch -> R.string.page_error_game_shape_branch
    },
)

@Composable
private fun formattedScore(score: Int): String = NumberFormat.getIntegerInstance().format(score)
