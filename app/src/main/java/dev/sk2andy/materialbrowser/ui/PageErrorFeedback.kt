package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineFailureKind
import dev.sk2andy.materialbrowser.ui.theme.CandyPink
import dev.sk2andy.materialbrowser.ui.theme.CandyInk
import dev.sk2andy.materialbrowser.ui.theme.CandyPinkSoft
import dev.sk2andy.materialbrowser.ui.theme.CandyPurple

internal sealed interface PageErrorFeedbackState {
    data object Hidden : PageErrorFeedbackState

    data object NotFound : PageErrorFeedbackState

    data class Error(val message: String) : PageErrorFeedbackState

    data object Retrying : PageErrorFeedbackState

    data class Offline(
        val isOnlineReady: Boolean = false,
        val game: CandyCircuitGameState = CandyCircuitGameState(),
    ) : PageErrorFeedbackState
}

internal data class PageErrorObservation(
    val state: PageErrorFeedbackState,
    val shouldReload: Boolean = false,
)

internal data class PageErrorRetryTransition(
    val state: PageErrorFeedbackState,
    val shouldReload: Boolean,
    val emitConfirmHaptic: Boolean,
)

internal object PageErrorFeedbackRules {
    fun observe(
        current: PageErrorFeedbackState,
        error: String?,
        httpStatusCode: Int?,
        isLoading: Boolean,
        isOnline: Boolean,
        failureKind: BrowserEngineFailureKind? = null,
        isWebPage: Boolean = true,
    ): PageErrorObservation = when {
        !isWebPage -> PageErrorObservation(PageErrorFeedbackState.Hidden)
        !isOnline && current is PageErrorFeedbackState.Offline -> PageErrorObservation(
            state = current.copy(isOnlineReady = false),
        )
        !isOnline && error != null -> {
            val offline = current as? PageErrorFeedbackState.Offline
            PageErrorObservation(
                state = PageErrorFeedbackState.Offline(
                    game = offline?.game ?: CandyCircuitGameState(),
                    isOnlineReady = false,
                ),
            )
        }
        isOnline && current is PageErrorFeedbackState.Offline ->
            PageErrorObservation(state = current.copy(isOnlineReady = true))
        failureKind == BrowserEngineFailureKind.Offline && error != null -> PageErrorObservation(
            state = PageErrorFeedbackState.Offline(isOnlineReady = isOnline),
        )
        httpStatusCode == HTTP_NOT_FOUND_STATUS && !isLoading ->
            PageErrorObservation(PageErrorFeedbackState.NotFound)
        error != null -> PageErrorObservation(PageErrorFeedbackState.Error(error))
        else -> PageErrorObservation(PageErrorFeedbackState.Hidden)
    }

    fun requestRetry(current: PageErrorFeedbackState): PageErrorRetryTransition = when (current) {
        PageErrorFeedbackState.NotFound,
        is PageErrorFeedbackState.Error,
        -> PageErrorRetryTransition(
            state = PageErrorFeedbackState.Retrying,
            shouldReload = true,
            emitConfirmHaptic = true,
        )
        is PageErrorFeedbackState.Offline -> PageErrorRetryTransition(
            state = PageErrorFeedbackState.Retrying,
            shouldReload = current.isOnlineReady,
            emitConfirmHaptic = current.isOnlineReady,
        )
        PageErrorFeedbackState.Hidden,
        PageErrorFeedbackState.Retrying,
        -> PageErrorRetryTransition(
            state = current,
            shouldReload = false,
            emitConfirmHaptic = false,
        )
    }

    private const val HTTP_NOT_FOUND_STATUS = 404
}

internal object PageErrorFeedbackTestTags {
    const val Page = "page_error_page"
    const val Retry = "page_error_retry"
    const val RetryProgress = "page_error_retry_progress"
    const val Game = "page_error_candy_game"
    const val OnlineBanner = "page_error_online_banner"
    const val Score = "page_error_game_score"
    const val BestScore = "page_error_game_best_score"
    const val Moves = "page_error_game_moves"
    const val Combo = "page_error_game_combo"
    const val Celebration = "page_error_game_celebration"
    const val Restart = "page_error_game_restart"
    const val TilePrefix = "page_error_game_tile_"
}

@Composable
internal fun PageErrorFeedback(
    state: PageErrorFeedbackState,
    onRetry: () -> Unit,
    onGameChange: (CandyCircuitGameState) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state !is PageErrorFeedbackState.Hidden,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageErrorBackground())
                .testTag(PageErrorFeedbackTestTags.Page),
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "page problem",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    PageErrorFeedbackState.NotFound -> NotFoundPage(onRetry = onRetry)
                    is PageErrorFeedbackState.Error -> UnreachablePage(onRetry = onRetry)
                    is PageErrorFeedbackState.Offline -> OfflinePage(
                        state = current,
                        onRetry = onRetry,
                        onGameChange = onGameChange,
                    )
                    PageErrorFeedbackState.Retrying -> RetryingPage()
                    PageErrorFeedbackState.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun pageErrorBackground(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to CandyPink.copy(alpha = 0.10f),
        0.38f to MaterialTheme.colorScheme.surface,
        0.72f to CandyPurple.copy(alpha = 0.08f),
        1f to CandyPink.copy(alpha = 0.18f),
    ),
)

@Composable
private fun NotFoundPage(onRetry: () -> Unit) {
    MissingPage(
        title = stringResource(R.string.page_error_not_found_title),
        body = stringResource(R.string.page_error_not_found_body),
        onRetry = onRetry,
    )
}

@Composable
private fun MissingPage(
    title: String,
    body: String,
    onRetry: () -> Unit,
) {
    ProblemPageLayout {
        MissingDestinationArtwork()
        Spacer(Modifier.height(30.dp))
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(PageErrorFeedbackTestTags.Retry),
            shape = CircleShape,
        ) {
            Text(
                text = stringResource(R.string.page_error_reload),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MissingDestinationArtwork() {
    val pink = MaterialTheme.colorScheme.primary
    val purple = MaterialTheme.colorScheme.tertiary
    val artworkDescription = stringResource(R.string.page_error_missing_destination_artwork)
    Surface(
        modifier = Modifier
            .size(width = 278.dp, height = 172.dp)
            .clearAndSetSemantics { contentDescription = artworkDescription },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        tonalElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                val points = listOf(
                    Offset(size.width * 0.16f, size.height * 0.72f),
                    Offset(size.width * 0.35f, size.height * 0.28f),
                    Offset(size.width * 0.57f, size.height * 0.48f),
                    Offset(size.width * 0.78f, size.height * 0.22f),
                )
                points.zipWithNext().forEachIndexed { index, (start, end) ->
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(pink, purple)),
                        start = start,
                        end = end,
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            intervals = floatArrayOf(9.dp.toPx(), 12.dp.toPx()),
                            phase = index * 4.dp.toPx(),
                        ),
                    )
                }
                val end = points.last()
                drawCircle(
                    color = purple.copy(alpha = 0.14f),
                    radius = 34.dp.toPx(),
                    center = end,
                )
                drawCircle(
                    color = purple,
                    radius = 19.dp.toPx(),
                    center = end,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx()),
                )
                drawLine(
                    color = purple,
                    start = end.copy(x = end.x + 13.dp.toPx(), y = end.y + 13.dp.toPx()),
                    end = end.copy(x = end.x + 27.dp.toPx(), y = end.y + 27.dp.toPx()),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground_art),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .size(82.dp),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "?",
                        color = purple,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnreachablePage(onRetry: () -> Unit) {
    MissingPage(
        title = stringResource(R.string.error_page_unreachable),
        body = stringResource(R.string.page_error_unreachable_body),
        onRetry = onRetry,
    )
}

@Composable
private fun OfflinePage(
    state: PageErrorFeedbackState.Offline,
    onRetry: () -> Unit,
    onGameChange: (CandyCircuitGameState) -> Unit,
) {
    CandyCircuitGame(
        isOnlineReady = state.isOnlineReady,
        game = state.game,
        onReload = onRetry,
        onGameChange = onGameChange,
    )
}

@Composable
internal fun OfflinePill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = CandyPinkSoft,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectivityGlyph(
                isOnline = false,
                color = CandyInk,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.page_error_offline_badge),
                style = MaterialTheme.typography.labelLarge,
                color = CandyInk,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun ConnectivityGlyph(
    isOnline: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val centerX = size.width / 2f
        listOf(0.08f to 0.84f, 0.24f to 0.52f).forEach { (inset, diameter) ->
            drawArc(
                color = color,
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(size.width * inset, size.height * inset),
                size = androidx.compose.ui.geometry.Size(
                    width = size.width * diameter,
                    height = size.height * diameter,
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                ),
            )
        }
        drawCircle(
            color = color,
            radius = stroke * 0.72f,
            center = Offset(centerX, size.height * 0.78f),
        )
        if (!isOnline) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.12f, size.height * 0.10f),
                end = Offset(size.width * 0.88f, size.height * 0.90f),
                strokeWidth = stroke * 1.15f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun ProblemPageLayout(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun CandyStatusArtwork(label: String) {
    Surface(
        modifier = Modifier.size(width = 204.dp, height = 124.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground_art),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun RetryingPage() {
    ProblemPageLayout {
        CandyStatusArtwork(label = "…")
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.action_retrying),
            modifier = Modifier
                .testTag(PageErrorFeedbackTestTags.RetryProgress)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
