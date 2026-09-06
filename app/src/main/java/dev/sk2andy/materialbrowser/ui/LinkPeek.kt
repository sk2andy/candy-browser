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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.data.LinkPeekAction
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayoutRules as LinkPeekActionSelectionRules
import dev.sk2andy.materialbrowser.data.LinkPeekActionSlot
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
    const val CopyLink = "link_peek_copy_link"
    const val OpenPrivate = "link_peek_open_private"
    const val Share = "link_peek_share"
    const val ReaderLater = "link_peek_reader_later"
    const val Favorite = "link_peek_favorite"
    const val Snooze = "link_peek_snooze"
    const val OpenForeground = "link_peek_open_foreground"
    const val DownloadLink = "link_peek_download_link"
    const val DownloadImage = "link_peek_download_image"
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
    onOpenUrl: (String) -> Unit = { onOpen() },
    onCommitRequested: () -> Unit = onOpen,
    onCopyLink: (String) -> Unit = {},
    onOpenInPrivate: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
    onSaveReaderOffline: (String, WebView) -> Unit = { _, _ -> },
    onFavorite: (String, String?) -> Unit = { _, _ -> },
    onSnooze: (String, String?) -> Unit = { _, _ -> },
    onOpenForeground: (String) -> Unit = {},
    actionLayout: LinkPeekActionLayout = LinkPeekActionLayout.Default,
    isFavorite: (String) -> Boolean = { false },
    canSaveReaderOffline: Boolean = true,
    canFavorite: Boolean = true,
    canSnooze: Boolean = true,
    canOpenInPrivate: Boolean = true,
    onDownloadLink: (() -> Unit)? = null,
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
    var previewWebView by remember(url) { mutableStateOf<WebView?>(null) }
    var cardBounds by remember(url) { mutableStateOf<Rect?>(null) }
    var commitStartBounds by remember(url) { mutableStateOf<Rect?>(null) }
    val commitProgress = remember(url) { Animatable(0f) }
    val openOnce = {
        if (!opened) {
            opened = true
            onOpenUrl(committedUrl)
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
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val committedUri = remember(committedUrl) {
        runCatching { URI(committedUrl) }.getOrNull()
    }
    val host = remember(committedUrl) { BrowserUriPolicy.displayHttpHost(committedUrl) }
    val isSecure = committedUri?.scheme.equals("https", ignoreCase = true)
    val openLabel = stringResource(R.string.action_open_in_new_tab)
    val copyLabel = stringResource(R.string.external_link_preview_copy_link)
    val openPrivateLabel = stringResource(R.string.action_open_link_in_private_tab)
    val shareLabel = stringResource(R.string.action_share)
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = scrimAlpha * (1f - flyProgress),
                ),
            )
            .testTag(LinkPeekTestTags.Root),
    ) {
        val fallbackTargetSizePx = with(density) { 48.dp.toPx() }
        val horizontalMarginPx = with(density) { 16.dp.toPx() }
        val verticalMarginPx = with(density) { 12.dp.toPx() }
        val fallbackTargetRight = constraints.maxWidth - horizontalMarginPx
        val fallbackTargetBottom = constraints.maxHeight -
            WindowInsets.navigationBars.getBottom(density) -
            verticalMarginPx
        val commitTargetBounds = newTabTargetBounds ?: Rect(
            left = fallbackTargetRight - fallbackTargetSizePx,
            top = fallbackTargetBottom - fallbackTargetSizePx,
            right = fallbackTargetRight,
            bottom = fallbackTargetBottom,
        )
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
                        val destination = commitTargetBounds
                        val dragScale = 0.985f - motionProgress * 0.015f
                        if (startBounds != null && flyProgress > 0f) {
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
                                ).also { previewWebView = it }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag(LinkPeekTestTags.Preview),
                            onRelease = { webView ->
                                if (previewWebView === webView) previewWebView = null
                                releasePreviewWebView(webView)
                            },
                        )
                    }
                    if (onDownloadLink != null) {
                        Text(
                            stringResource(R.string.action_download_link),
                            modifier = Modifier
                                .testTag(LinkPeekTestTags.DownloadLink)
                                .clickable(onClick = onDownloadLink)
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (onDownloadImage != null) {
                        Text(
                            stringResource(R.string.action_download_image),
                            modifier = Modifier
                                .testTag(LinkPeekTestTags.DownloadImage)
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
        commitTargetBounds.let { targetBounds ->
            val actionSpacingPx = with(density) { 4.dp.toPx() }
            val slotBounds = LinkPeekActionLayoutRules.actionBarSlots(
                containerBounds = Rect(
                    left = 0f,
                    top = 0f,
                    right = constraints.maxWidth.toFloat(),
                    bottom = constraints.maxHeight.toFloat(),
                ),
                newTabTargetBounds = targetBounds,
                slotCount = LinkPeekActionSelectionRules.TOOLBAR_SLOT_COUNT,
                fixedNewTabSlotIndex = LinkPeekActionSelectionRules.FIXED_PLUS_SLOT_INDEX,
                preferredSpacingPx = actionSpacingPx,
            )
            val visualSlotBounds = if (isRtl) slotBounds.reversed() else slotBounds
            val runtimeSlots = LinkPeekActionSelectionRules.runtimeSlots(actionLayout)
            val favoriteSelected = canFavorite && isFavorite(committedUrl)
            runtimeSlots.forEachIndexed { index, slot ->
                val bounds = visualSlotBounds.getOrNull(index) ?: return@forEachIndexed
                if (slot is LinkPeekActionSlot.Action) {
                    val action = slot.action
                    LinkPeekActionTarget(
                        targetBounds = bounds,
                        icon = action.iconRes(favoriteSelected),
                        contentDescription = action.contentDescription(
                            copyLabel = copyLabel,
                            openPrivateLabel = openPrivateLabel,
                            shareLabel = shareLabel,
                            isFavorite = favoriteSelected,
                        ),
                        testTag = action.testTag(),
                        enabled = !committing && when (action) {
                            LinkPeekAction.ReaderLater ->
                                canSaveReaderOffline && previewProgress >= 100 &&
                                    previewWebView != null
                            LinkPeekAction.OpenPrivate -> canOpenInPrivate
                            LinkPeekAction.Favorite -> canFavorite
                            LinkPeekAction.Snooze -> canSnooze
                            else -> true
                        },
                        alpha = 1f - flyProgress,
                        onClick = {
                            when (action) {
                                LinkPeekAction.ReaderLater -> previewWebView?.let { webView ->
                                    onSaveReaderOffline(committedUrl, webView)
                                }
                                LinkPeekAction.OpenPrivate -> onOpenInPrivate(committedUrl)
                                LinkPeekAction.Copy -> onCopyLink(committedUrl)
                                LinkPeekAction.Share -> onShare(committedUrl)
                                LinkPeekAction.Favorite -> onFavorite(
                                    committedUrl,
                                    previewWebView?.title,
                                )
                                LinkPeekAction.Snooze -> onSnooze(
                                    committedUrl,
                                    previewWebView?.title,
                                )
                                LinkPeekAction.OpenForeground ->
                                    onOpenForeground(committedUrl)
                            }
                        },
                    )
                }
            }
            val plusBounds = visualSlotBounds.getOrNull(
                LinkPeekActionSelectionRules.FIXED_PLUS_SLOT_INDEX,
            ) ?: return@let
            val targetWidth = with(density) { plusBounds.width.toDp() }
            val targetHeight = with(density) { plusBounds.height.toDp() }
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset {
                        IntOffset(
                            x = plusBounds.left.roundToInt(),
                            y = plusBounds.top.roundToInt(),
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

@Composable
private fun BoxScope.LinkPeekActionTarget(
    targetBounds: Rect,
    icon: Int,
    contentDescription: String,
    testTag: String,
    enabled: Boolean,
    alpha: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
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
            .size(targetWidth, targetHeight)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 3.dp,
        ) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(testTag),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (enabled) 1f else 0.38f,
                    ),
                )
            }
        }
    }
}

private fun LinkPeekAction.iconRes(isFavorite: Boolean): Int = when (this) {
    LinkPeekAction.ReaderLater -> R.drawable.ic_reader_download
    LinkPeekAction.OpenPrivate -> R.drawable.ic_incognito_outline
    LinkPeekAction.Copy -> R.drawable.ic_content_copy
    LinkPeekAction.Share -> R.drawable.ic_symbol_share
    LinkPeekAction.Favorite -> if (isFavorite) {
        R.drawable.ic_symbol_favorite_filled
    } else {
        R.drawable.ic_symbol_favorite
    }
    LinkPeekAction.Snooze -> R.drawable.ic_snooze
    LinkPeekAction.OpenForeground -> R.drawable.ic_symbol_open_in_new
}

@Composable
private fun LinkPeekAction.contentDescription(
    copyLabel: String,
    openPrivateLabel: String,
    shareLabel: String,
    isFavorite: Boolean,
): String = when (this) {
    LinkPeekAction.ReaderLater -> stringResource(R.string.reader_save_offline)
    LinkPeekAction.OpenPrivate -> openPrivateLabel
    LinkPeekAction.Copy -> copyLabel
    LinkPeekAction.Share -> shareLabel
    LinkPeekAction.Favorite -> stringResource(
        if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
    )
    LinkPeekAction.Snooze -> stringResource(R.string.action_snooze_tab)
    LinkPeekAction.OpenForeground -> stringResource(R.string.action_open_in_new_tab_and_switch)
}

private fun LinkPeekAction.testTag(): String = when (this) {
    LinkPeekAction.ReaderLater -> LinkPeekTestTags.ReaderLater
    LinkPeekAction.OpenPrivate -> LinkPeekTestTags.OpenPrivate
    LinkPeekAction.Copy -> LinkPeekTestTags.CopyLink
    LinkPeekAction.Share -> LinkPeekTestTags.Share
    LinkPeekAction.Favorite -> LinkPeekTestTags.Favorite
    LinkPeekAction.Snooze -> LinkPeekTestTags.Snooze
    LinkPeekAction.OpenForeground -> LinkPeekTestTags.OpenForeground
}
