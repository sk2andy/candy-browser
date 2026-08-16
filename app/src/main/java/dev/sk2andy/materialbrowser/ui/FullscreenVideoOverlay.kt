package dev.sk2andy.materialbrowser.ui

import android.graphics.Color
import android.graphics.Rect
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.FullscreenVideoOffset
import dev.sk2andy.materialbrowser.browser.FullscreenVideoPlacement
import dev.sk2andy.materialbrowser.browser.FullscreenVideoRules
import kotlin.math.roundToInt

internal object FullscreenVideoTestTags {
    const val Expanded = "fullscreen_video_expanded"
    const val MiniPlayer = "fullscreen_video_mini_player"
    const val DragHandle = "fullscreen_video_drag_handle"
    const val Minimize = "fullscreen_video_minimize"
    const val Expand = "fullscreen_video_expand"
    const val Close = "fullscreen_video_close"
}

@Composable
internal fun FullscreenVideoOverlay(
    controller: BrowserController,
    videoOnlyPresentation: Boolean,
    onBoundsChanged: (Rect) -> Unit,
) {
    val state = controller.fullscreenVideoState ?: return
    if (
        !FullscreenVideoRules.hostsSourceInOverlay(
            host = state.host,
            videoOnlyPresentation = videoOnlyPresentation,
        )
    ) return
    val placement = controller.fullscreenVideoPlacement(videoOnlyPresentation) ?: return
    BackHandler(
        enabled = placement == FullscreenVideoPlacement.Expanded && !videoOnlyPresentation,
        onBack = controller::exitFullscreenVideo,
    )
    StableFullscreenVideoHost(
        controller = controller,
        sessionTabId = state.tabId,
        placement = placement,
        videoOnlyPresentation = videoOnlyPresentation,
        canMinimize = controller.canMinimizeFullscreenVideo,
        onBoundsChanged = onBoundsChanged,
    )
}

@Composable
private fun StableFullscreenVideoHost(
    controller: BrowserController,
    sessionTabId: String,
    placement: FullscreenVideoPlacement,
    videoOnlyPresentation: Boolean,
    canMinimize: Boolean,
    onBoundsChanged: (Rect) -> Unit,
) {
    val isMiniPlayer = placement == FullscreenVideoPlacement.MiniPlayer
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isMiniPlayer) {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                } else {
                    Modifier
                },
            )
            .zIndex(FULLSCREEN_VIDEO_Z_INDEX),
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val miniPlayerWidth = (maxWidth - MINI_PLAYER_HORIZONTAL_MARGIN * 2)
            .coerceAtMost(MINI_PLAYER_MAX_WIDTH)
        var rootSize by remember { mutableStateOf(IntSize.Zero) }
        var playerSize by remember { mutableStateOf(IntSize.Zero) }
        var offset by remember(sessionTabId) {
            mutableStateOf(FullscreenVideoOffset(x = 0f, y = 0f))
        }
        LaunchedEffect(isMiniPlayer, rootSize, playerSize) {
            if (isMiniPlayer) {
                offset = clampedMiniPlayerOffset(
                    offset = offset,
                    rootSize = rootSize,
                    playerSize = playerSize,
                    horizontalMarginPx = with(density) { MINI_PLAYER_HORIZONTAL_MARGIN.toPx() },
                    bottomMarginPx = with(density) { MINI_PLAYER_BOTTOM_MARGIN.toPx() },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootSize = it.size },
        ) {
            val playerModifier = if (isMiniPlayer) {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = MINI_PLAYER_HORIZONTAL_MARGIN,
                        bottom = MINI_PLAYER_BOTTOM_MARGIN,
                    )
                    .offset {
                        IntOffset(
                            x = offset.x.roundToInt(),
                            y = offset.y.roundToInt(),
                        )
                    }
                    .width(miniPlayerWidth)
                    .aspectRatio(VIDEO_ASPECT_RATIO)
                    .onGloballyPositioned { playerSize = it.size }
                    .testTag(FullscreenVideoTestTags.MiniPlayer)
            } else {
                Modifier
                    .fillMaxSize()
                    .testTag(FullscreenVideoTestTags.Expanded)
            }
            Surface(
                modifier = playerModifier
                    .onVideoBoundsChanged(onBoundsChanged)
                    .background(ComposeColor.Black),
                shape = if (isMiniPlayer) RoundedCornerShape(18.dp) else RectangleShape,
                color = ComposeColor.Black,
                shadowElevation = if (isMiniPlayer) 12.dp else 0.dp,
            ) {
                Box {
                    FullscreenVideoView(
                        controller = controller,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isMiniPlayer) {
                                    Modifier.clip(RoundedCornerShape(18.dp))
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (!isMiniPlayer && !videoOnlyPresentation && canMinimize) {
                        VideoOverlayButton(
                            onClick = controller::minimizeFullscreenVideo,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(16.dp)
                                .testTag(FullscreenVideoTestTags.Minimize),
                            contentDescription = stringResource(
                                R.string.cd_minimize_fullscreen_video,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }
                    if (isMiniPlayer) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                        ) {
                            VideoOverlayButton(
                                onClick = controller::expandFullscreenVideo,
                                modifier = Modifier.testTag(FullscreenVideoTestTags.Expand),
                                contentDescription = stringResource(
                                    R.string.cd_expand_fullscreen_video,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                )
                            }
                            VideoOverlayButton(
                                onClick = controller::exitFullscreenVideo,
                                modifier = Modifier.testTag(FullscreenVideoTestTags.Close),
                                contentDescription = stringResource(
                                    R.string.cd_close_fullscreen_video,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                )
                            }
                        }
                        VideoOverlayButton(
                            onClick = {
                                offset = nextMiniPlayerAnchor(
                                    offset = offset,
                                    rootSize = rootSize,
                                    playerSize = playerSize,
                                    horizontalMarginPx = with(density) {
                                        MINI_PLAYER_HORIZONTAL_MARGIN.toPx()
                                    },
                                    bottomMarginPx = with(density) {
                                        MINI_PLAYER_BOTTOM_MARGIN.toPx()
                                    },
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .pointerInput(rootSize, playerSize) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offset = clampedMiniPlayerOffset(
                                            offset = FullscreenVideoOffset(
                                                x = offset.x + dragAmount.x,
                                                y = offset.y + dragAmount.y,
                                            ),
                                            rootSize = rootSize,
                                            playerSize = playerSize,
                                            horizontalMarginPx =
                                                MINI_PLAYER_HORIZONTAL_MARGIN.toPx(),
                                            bottomMarginPx = MINI_PLAYER_BOTTOM_MARGIN.toPx(),
                                        )
                                    }
                                }
                                .testTag(FullscreenVideoTestTags.DragHandle),
                            contentDescription = stringResource(
                                R.string.cd_move_fullscreen_video,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.rotate(90f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenVideoView(
    controller: BrowserController,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        },
        update = controller::attachFullscreenVideoView,
        onRelease = controller::detachFullscreenVideoView,
        modifier = modifier,
    )
}

@Composable
private fun VideoOverlayButton(
    onClick: () -> Unit,
    modifier: Modifier,
    contentDescription: String,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f),
        contentColor = ComposeColor.White,
    ) {
        IconButton(
            onClick = onClick,
            content = icon,
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
            },
        )
    }
}

private fun Modifier.onVideoBoundsChanged(onBoundsChanged: (Rect) -> Unit): Modifier =
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        onBoundsChanged(
            Rect(
                bounds.left.roundToInt(),
                bounds.top.roundToInt(),
                bounds.right.roundToInt(),
                bounds.bottom.roundToInt(),
            ),
        )
    }

private fun clampedMiniPlayerOffset(
    offset: FullscreenVideoOffset,
    rootSize: IntSize,
    playerSize: IntSize,
    horizontalMarginPx: Float,
    bottomMarginPx: Float,
): FullscreenVideoOffset = FullscreenVideoRules.clampMiniPlayerOffset(
    proposedX = offset.x,
    proposedY = offset.y,
    maxLeftTravel = rootSize.width - playerSize.width - horizontalMarginPx * 2,
    maxUpTravel = rootSize.height - playerSize.height - bottomMarginPx,
)

private fun nextMiniPlayerAnchor(
    offset: FullscreenVideoOffset,
    rootSize: IntSize,
    playerSize: IntSize,
    horizontalMarginPx: Float,
    bottomMarginPx: Float,
): FullscreenVideoOffset = FullscreenVideoRules.nextMiniPlayerAnchor(
    current = offset,
    maxLeftTravel = rootSize.width - playerSize.width - horizontalMarginPx * 2,
    maxUpTravel = rootSize.height - playerSize.height - bottomMarginPx,
)

private const val VIDEO_ASPECT_RATIO = 16f / 9f
private const val FULLSCREEN_VIDEO_Z_INDEX = 100f
private val MINI_PLAYER_MAX_WIDTH = 240.dp
private val MINI_PLAYER_HORIZONTAL_MARGIN = 12.dp
private val MINI_PLAYER_BOTTOM_MARGIN = 88.dp
