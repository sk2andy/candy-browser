package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import java.text.NumberFormat

@Composable
internal fun CandyCircuitGame(
    isOnlineReady: Boolean,
    game: CandyCircuitGameState,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onGameChange: (CandyCircuitGameState) -> Unit,
) {
    val hapticView = LocalView.current
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
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
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
                onRotate = { tileIndex ->
                    val next = CandyCircuitRules.rotate(game, tileIndex)
                    if (next === game) return@CandyCircuitBoard
                    if (next.lastPointsGained > 0) {
                        hapticView.performConfirmHaptic()
                    } else {
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    onGameChange(next)
                },
            )
            Spacer(Modifier.height(14.dp))
            CircuitResult(game = game, onRestart = {
                hapticView.performConfirmHaptic()
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
            if (!isOnlineReady) {
                TextButton(onClick = onStop) {
                    Text(stringResource(R.string.page_error_stop_game))
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
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
        AnimatedContent(
            targetState = isOnlineReady,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "connection status",
        ) { online ->
            if (online) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PageErrorFeedbackTestTags.OnlineBanner)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.page_error_back_online),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.page_error_back_online_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        Button(
                            onClick = onReload,
                            modifier = Modifier.testTag(PageErrorFeedbackTestTags.Retry),
                            shape = CircleShape,
                        ) {
                            Text(stringResource(R.string.page_error_load_page))
                        }
                    }
                }
            } else {
                OfflinePill()
            }
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
            modifier = Modifier
                .weight(1f)
                .testTag(PageErrorFeedbackTestTags.Score),
        )
        CircuitMetric(
            label = stringResource(R.string.page_error_game_best_label),
            value = formattedScore(game.bestScore),
            modifier = Modifier
                .weight(1f)
                .testTag(PageErrorFeedbackTestTags.BestScore),
        )
        Surface(
            modifier = Modifier
                .weight(0.9f)
                .testTag(PageErrorFeedbackTestTags.Moves)
                .semantics(mergeDescendants = true) {},
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.page_error_game_moves_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = game.movesRemaining.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.page_error_game_moves_unit),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CircuitMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun CandyCircuitBoard(
    game: CandyCircuitGameState,
    onRotate: (Int) -> Unit,
) {
    val closedIndices = CandyCircuitRules.closedComponents(game.tiles)
        .flatMapTo(mutableSetOf()) { it.tileIndices }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
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
                        isClosed = index in closedIndices,
                        enabled = !game.isGameOver,
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
    isClosed: Boolean,
    enabled: Boolean,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connections = CandyCircuitRules.connections(tile)
    val animatedBackground = animateColorAsState(
        targetValue = if (isClosed) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "circuit tile background",
    )
    val rotation = animateFloatAsState(
        targetValue = tile.rotation * 90f,
        label = "circuit tile rotation",
    )
    val shapeName = circuitShapeName(tile.shape)
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
    Surface(
        modifier = modifier
            .aspectRatio(1f),
        shape = RoundedCornerShape(18.dp),
        color = animatedBackground.value,
        tonalElevation = if (isClosed) 6.dp else 3.dp,
        shadowElevation = if (isClosed) 7.dp else 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                }
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            CandyCircuitRibbon(
                shape = tile.shape,
                rotation = rotation.value,
                color = if (tileIndex % 3 == 1) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

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
                MaterialTheme.colorScheme.primary
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
            color = MaterialTheme.colorScheme.primary,
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
