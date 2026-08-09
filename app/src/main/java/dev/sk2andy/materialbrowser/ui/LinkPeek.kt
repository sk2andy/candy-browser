package dev.sk2andy.materialbrowser.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.net.URI
import kotlin.math.roundToInt

internal object LinkPeekTestTags {
    const val Root = "link_peek"
    const val OpenTarget = "link_peek_open_target"
    const val Card = "link_peek_card"
    const val Preview = "link_peek_preview"
    const val Url = "link_peek_url"
    const val NewTabTargetOverlay = "link_peek_new_tab_target_overlay"
    const val NewTabTargetPulseRing = "link_peek_new_tab_target_pulse_ring"
}

@Composable
internal fun LinkPeekOverlay(
    url: String,
    progress: Float,
    armed: Boolean,
    committing: Boolean = false,
    newTabTargetBounds: Rect? = null,
    createPreviewWebView: ((Int) -> Unit, (String) -> Unit) -> WebView,
    releasePreviewWebView: (WebView) -> Unit,
    onOpen: () -> Unit,
    onCommitRequested: () -> Unit = onOpen,
    onDownloadImage: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    BackHandler {
        if (!committing) onDismiss()
    }
    var opened by remember(url) { mutableStateOf(false) }
    var commitRequested by remember(url) { mutableStateOf(false) }
    var previewProgress by remember(url) { mutableIntStateOf(0) }
    var committedUrl by remember(url) { mutableStateOf(url) }
    var cardBounds by remember(url) { mutableStateOf<Rect?>(null) }
    var commitStartBounds by remember(url) { mutableStateOf<Rect?>(null) }
    val commitProgress = remember(url) { Animatable(0f) }
    val openOnce = {
        if (!opened) {
            opened = true
            onOpen()
        }
    }
    val motionProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "linkPeekProgress",
    )
    val targetPulseScale = remember { Animatable(1f) }
    val targetPulseTransition = rememberInfiniteTransition(label = "linkPeekPlusPulse")
    val targetRingProgress by targetPulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = FastOutSlowInEasing),
        ),
        label = "linkPeekPlusRing",
    )
    val targetBreathProgress by targetPulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "linkPeekPlusBreath",
    )
    val commitRingProgress = remember(committing) { targetRingProgress }
    val commitBreathProgress = remember(committing) { targetBreathProgress }
    val commitTargetPulseScale = remember(committing) { targetPulseScale.value }
    LaunchedEffect(armed) {
        if (!armed) {
            targetPulseScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
            )
            return@LaunchedEffect
        }
        targetPulseScale.snapTo(1f)
        targetPulseScale.animateTo(
            targetValue = 1.13f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 620f),
        )
        targetPulseScale.animateTo(
            targetValue = 1.04f,
            animationSpec = spring(dampingRatio = 0.76f, stiffness = 560f),
        )
    }
    val scrimAlpha = 0.42f + 0.16f * motionProgress
    val density = LocalDensity.current
    val committedUri = remember(committedUrl) {
        runCatching { URI(committedUrl) }.getOrNull()
    }
    val host = remember(committedUrl) { BrowserUriPolicy.displayHttpHost(committedUrl) }
    val isSecure = committedUri?.scheme.equals("https", ignoreCase = true)
    val openLabel = stringResource(R.string.action_open_in_new_tab)
    val cancelLabel = stringResource(R.string.action_cancel)

    LaunchedEffect(committing) {
        if (!committing) {
            commitProgress.snapTo(0f)
            commitStartBounds = null
            return@LaunchedEffect
        }
        commitStartBounds = cardBounds
        commitProgress.snapTo(0f)
        commitProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
        openOnce()
    }

    val flyProgress = commitProgress.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = scrimAlpha * (1f - flyProgress),
                ),
            )
            .testTag(LinkPeekTestTags.Root),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = !committing,
                    onClickLabel = cancelLabel,
                    role = Role.Button,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        if (!committing) cardBounds = coordinates.boundsInRoot()
                    }
                    .graphicsLayer {
                        val startBounds = commitStartBounds
                        val destination = newTabTargetBounds
                        val dragScale = 0.985f - motionProgress * 0.015f
                        if (startBounds != null && destination != null && flyProgress > 0f) {
                            translationX =
                                (destination.center.x - startBounds.center.x) * flyProgress
                            translationY = motionProgress * 18.dp.toPx() +
                                (destination.center.y - startBounds.center.y) * flyProgress
                            val targetScale = (
                                destination.width / startBounds.width.coerceAtLeast(1f)
                                ).coerceIn(0.04f, 0.14f)
                            val scale = lerp(dragScale, targetScale, flyProgress)
                            scaleX = scale
                            scaleY = scale
                            alpha = if (flyProgress < 0.72f) {
                                1f
                            } else {
                                1f - (flyProgress - 0.72f) / 0.28f
                            }
                        } else {
                            translationY = motionProgress * 18.dp.toPx() +
                                flyProgress * 220.dp.toPx()
                            val scale = lerp(dragScale, 0.08f, flyProgress)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - flyProgress
                        }
                    }
                    .testTag(LinkPeekTestTags.Card),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 8.dp,
                shadowElevation = 18.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSecure) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                "HTTP",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                host,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                committedUrl,
                                modifier = Modifier.testTag(LinkPeekTestTags.Url),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            stringResource(R.string.link_peek_title),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (previewProgress < 100) {
                        LinearProgressIndicator(
                            progress = { previewProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    key(url) {
                        AndroidView(
                            factory = {
                                createPreviewWebView(
                                    { loaded -> previewProgress = loaded },
                                    { committed -> committedUrl = committed },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag(LinkPeekTestTags.Preview),
                            onRelease = releasePreviewWebView,
                        )
                    }
                    if (onDownloadImage != null) {
                        Text(
                            stringResource(R.string.action_download_image),
                            modifier = Modifier
                                .clickable(onClick = onDownloadImage)
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Spacer(Modifier.height(64.dp))
        }
        newTabTargetBounds?.let { targetBounds ->
            val targetWidth = with(density) { targetBounds.width.toDp() }
            val targetHeight = with(density) { targetBounds.height.toDp() }
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset {
                        IntOffset(
                            x = targetBounds.left.roundToInt(),
                            y = targetBounds.top.roundToInt(),
                        )
                    }
                    .size(targetWidth, targetHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val ringProgress = if (committing) {
                                commitRingProgress
                            } else {
                                targetRingProgress
                            }
                            val ringScale = lerp(
                                1.02f,
                                if (armed) 1.42f else 1.28f,
                                ringProgress,
                            )
                            scaleX = ringScale
                            scaleY = ringScale
                            val ringAlpha = if (armed) 0.48f else 0.3f
                            alpha = (1f - ringProgress) *
                                ringAlpha *
                                (1f - flyProgress)
                        }
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        .testTag(LinkPeekTestTags.NewTabTargetPulseRing),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val breathProgress = if (committing) {
                                commitBreathProgress
                            } else {
                                targetBreathProgress
                            }
                            val pulseScale = if (committing) {
                                commitTargetPulseScale
                            } else {
                                targetPulseScale.value
                            }
                            val progressScale = 1f + motionProgress * 0.04f
                            val breathScale = lerp(
                                1f,
                                if (armed) 1.035f else 1.02f,
                                breathProgress,
                            )
                            val scale = pulseScale * progressScale * breathScale
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - flyProgress * 0.18f
                        }
                        .testTag(LinkPeekTestTags.NewTabTargetOverlay),
                    shape = CircleShape,
                    color = if (armed) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shadowElevation = if (armed) 8.dp else 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = !committing,
                                onClickLabel = openLabel,
                                role = Role.Button,
                                onClick = {
                                    if (!commitRequested) {
                                        commitRequested = true
                                        onCommitRequested()
                                    }
                                },
                            )
                            .testTag(LinkPeekTestTags.OpenTarget),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = openLabel,
                            modifier = Modifier.size(26.dp),
                            tint = if (armed) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
