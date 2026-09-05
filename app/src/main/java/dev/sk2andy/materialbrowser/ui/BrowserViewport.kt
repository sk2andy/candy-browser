@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserWebView
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.FindInPageRules
import dev.sk2andy.materialbrowser.browser.ExternalLinkPreviewCommitResult
import dev.sk2andy.materialbrowser.browser.ExternalLinkPreviewState
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import eightbitlab.com.blurview.BlurTarget
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
internal fun ExternalLinkPreviewScreen(
    controller: BrowserController,
    state: ExternalLinkPreviewState,
    onReturnToExternalApp: () -> Unit,
    onCommitted: (String) -> Unit,
    onTabOverviewPortraitLockChanged: (Boolean) -> Unit,
) {
    var rootBottomInWindowPx by remember { mutableIntStateOf(0) }
    var blurTarget by remember { mutableStateOf<BlurTarget?>(null) }
    val profiles = if (controller.profilesEnabled) {
        controller.profiles.toList()
    } else {
        controller.profiles.take(1)
    }
    val webViewRevision = controller.webViewRevision
    LaunchedEffect(state.sessionId, webViewRevision) {
        onTabOverviewPortraitLockChanged(false)
        controller.prepareExternalLinkPreview(state.sessionId)
    }
    BackHandler {
        when {
            controller.findInPageState != null -> controller.closeFindInPage()
            state.canGoBack -> controller.goBackInExternalLinkPreview(state.sessionId)
            controller.dismissExternalLinkPreview(state.sessionId) -> onReturnToExternalApp()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootBottomInWindowPx = coordinates.boundsInWindow().bottom.roundToInt()
            }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ExternalLinkPreviewViewport(
            controller = controller,
            onBlurTargetAttached = { target -> blurTarget = target },
            onBlurTargetReleased = { target ->
                if (blurTarget === target) blurTarget = null
            },
        )
        controller.findInPageState?.let { findState ->
            val matchPosition = FindInPageRules.displayPosition(findState)
            FindInPageBar(
                query = findState.query,
                onQueryChange = controller::updateFindInPageQuery,
                matchText = stringResource(
                    R.string.find_in_page_match_count,
                    matchPosition.activeMatchNumber,
                    matchPosition.matchCount,
                ),
                isCounting = findState.query.isNotEmpty() && !findState.isDoneCounting,
                canNavigate = FindInPageRules.canNavigate(findState),
                focusNonce = 0,
                autoFocus = true,
                placeholder = stringResource(R.string.action_find_in_page),
                queryContentDescription = stringResource(R.string.cd_find_in_page_query),
                countingContentDescription = stringResource(R.string.cd_find_in_page_counting),
                previousMatchContentDescription = stringResource(
                    R.string.cd_find_in_page_previous,
                ),
                nextMatchContentDescription = stringResource(R.string.cd_find_in_page_next),
                closeContentDescription = stringResource(R.string.cd_find_in_page_close),
                onPreviousMatch = { controller.findNextInPage(forward = false) },
                onNextMatch = { controller.findNextInPage(forward = true) },
                onClose = controller::closeFindInPage,
                blurTarget = blurTarget,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .zIndex(25f),
            )
        }
        ExternalLinkPreviewChrome(
            controller = controller,
            state = state,
            profiles = profiles,
            rootBottomInWindowPx = rootBottomInWindowPx,
            onReturnToExternalApp = onReturnToExternalApp,
            onCommitted = onCommitted,
        )
    }
}

@Composable
private fun ExternalLinkPreviewChrome(
    controller: BrowserController,
    state: ExternalLinkPreviewState,
    profiles: List<BrowserProfile>,
    rootBottomInWindowPx: Int,
    onReturnToExternalApp: () -> Unit,
    onCommitted: (String) -> Unit,
) {
    val context = LocalContext.current
    ExternalLinkPreviewBar(
        state = state,
        profiles = profiles,
        isDesktopView = controller.isExternalLinkPreviewDesktopView,
        blurTarget = null,
        rootBottomInWindowPx = rootBottomInWindowPx,
        onDismissPreview = {
            if (controller.dismissExternalLinkPreview(state.sessionId)) {
                onReturnToExternalApp()
            }
        },
        onOpenInCandy = {
            when (val result = controller.commitExternalLinkPreview(state.sessionId)) {
                is ExternalLinkPreviewCommitResult.Opened -> onCommitted(result.tabId)
                ExternalLinkPreviewCommitResult.MissingPreview,
                ExternalLinkPreviewCommitResult.TabLimitReached,
                -> Unit
            }
        },
        onSelectProfile = { profileId ->
            controller.selectExternalLinkPreviewProfile(state.sessionId, profileId)
        },
        onShare = { controller.shareExternalLinkPreview(state.sessionId) },
        onCopyLink = {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText(
                    context.getString(R.string.external_link_preview_copy_label),
                    state.currentUrl,
                ),
            )
            Toast.makeText(context, R.string.toast_link_copied, Toast.LENGTH_SHORT).show()
        },
        onFindInPage = { controller.openExternalLinkPreviewFindInPage(state.sessionId) },
        onDesktopViewChange = { enabled ->
            controller.setExternalLinkPreviewDesktopView(state.sessionId, enabled)
        },
        modifier = Modifier.zIndex(10f),
    )
}

@Composable
private fun ExternalLinkPreviewViewport(
    controller: BrowserController,
    onBlurTargetAttached: (BlurTarget) -> Unit,
    onBlurTargetReleased: (BlurTarget) -> Unit,
) {
    val density = LocalDensity.current
    val geometry = StatusBarFrostedGlassRules.geometry(
        statusBarHeightPx = WindowInsets.statusBars.getTop(density),
        density = density.density,
    )
    val statusBarTint = MaterialTheme.colorScheme.surface.toArgb()
    val currentOnBlurTargetAttached by rememberUpdatedState(onBlurTargetAttached)
    val currentOnBlurTargetReleased by rememberUpdatedState(onBlurTargetReleased)
    AndroidView(
        factory = { context -> StatusBarFrostedGlassHost(context) },
        update = { host ->
            currentOnBlurTargetAttached(host.blurTarget)
            host.updateFrostedGlass(
                geometry = geometry,
                tint = statusBarTint,
                visible = true,
            )
            if (controller.externalLinkPreviewState?.isWebViewReady == true) {
                controller.attachExternalLinkPreview(host.blurTarget)
            } else {
                controller.detachExternalLinkPreview(host.blurTarget)
            }
        },
        onRelease = { host ->
            controller.detachExternalLinkPreview(host.blurTarget)
            host.release()
            currentOnBlurTargetReleased(host.blurTarget)
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
internal fun BrowserViewport(
    controller: BrowserController,
    webViewVideoOnlyPresentation: Boolean,
    selectedTab: BrowserTab,
    dragOffset: MutableFloatState,
    travelDistance: Float,
    rootHeightPx: Float,
    bottomBarTopPx: FloatState,
    handoff: TabHandoff?,
    handoffAlpha: Float,
    liveFrameTabId: String?,
    tabOverviewVisible: Boolean,
    onLiveFrame: (String) -> Unit,
    onSearch: () -> Unit,
    onFavorite: (String) -> Unit,
    blankTabModeProgress: Float,
    blankTabModeRevealOrigin: Offset,
    onRetry: () -> Boolean,
    onBlurTargetAttached: (BlurTarget) -> Unit,
    onBlurTargetReleased: (BlurTarget) -> Unit,
) {
    val density = LocalDensity.current
    val hapticView = LocalView.current
    val dragDirection by remember(dragOffset) {
        derivedStateOf { dragOffset.floatValue.compareTo(0f) }
    }
    val tabs = controller.activeTabs
    val selectedTabIndex = tabs.indexOfFirst { it.id == controller.selectedTabId }
    val adjacentTab = when {
        dragDirection < 0 -> tabs.getOrNull(selectedTabIndex + 1)
        dragDirection > 0 -> tabs.getOrNull(selectedTabIndex - 1)
        else -> null
    }
    var pageErrorFeedback by remember(selectedTab.id) {
        mutableStateOf(
            PageErrorFeedbackRules.observe(
                current = PageErrorFeedbackState.Hidden(),
                error = selectedTab.error,
                isLoading = selectedTab.isLoading,
            ),
        )
    }
    var scrollBarWebView by remember(selectedTab.id) { mutableStateOf<BrowserWebView?>(null) }
    LaunchedEffect(selectedTab.id, selectedTab.error, selectedTab.isLoading) {
        pageErrorFeedback = PageErrorFeedbackRules.observe(
            current = pageErrorFeedback,
            error = selectedTab.error,
            isLoading = selectedTab.isLoading,
        )
    }

    adjacentTab?.let { tab ->
        TabSwitchPreview(
            tab = tab,
            preview = controller.previews[tab.id],
            favicon = controller.favicons[tab.id],
            favorites = controller.favorites,
            dragOffset = dragOffset,
            dragDirection = dragDirection,
            travelDistance = travelDistance,
            rootHeightPx = rootHeightPx,
            previewTopInsetPx = controller.previewTopInsetPx(tab.id),
            bottomBarTopPx = bottomBarTopPx,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (webViewVideoOnlyPresentation) VIDEO_ONLY_WEB_VIEW_Z_INDEX else 0f)
            .graphicsLayer {
                if (webViewVideoOnlyPresentation) {
                    translationX = 0f
                    scaleX = 1f
                    scaleY = 1f
                    clip = false
                    shadowElevation = 0f
                    return@graphicsLayer
                }
                val offset = dragOffset.floatValue
                val travelProgress = (offset.absoluteValue / travelDistance).coerceIn(0f, 1f)
                val cardProgress = if (adjacentTab != null) {
                    (4f * travelProgress * (1f - travelProgress)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val scale = 1f - 0.03f * cardProgress
                translationX = offset
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape((32f * cardProgress).dp)
                clip = cardProgress > 0f
                shadowElevation = with(density) { (8f * cardProgress).dp.toPx() }
            }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (selectedTab.url != BLANK_URL) {
            ActiveWebView(
                controller = controller,
                visible = webViewVideoOnlyPresentation ||
                    !tabOverviewVisible ||
                    selectedTab.isIncognito,
                showStatusBarFrostedGlass = !webViewVideoOnlyPresentation && !tabOverviewVisible,
                statusBarTint = MaterialTheme.colorScheme.surface.toArgb(),
                onLiveFrame = onLiveFrame,
                onBlurTargetAttached = onBlurTargetAttached,
                onBlurTargetReleased = onBlurTargetReleased,
                onWebViewChanged = { scrollBarWebView = it },
            )
        }

        if (
            controller.isScrollBarEnabled &&
            !webViewVideoOnlyPresentation &&
            !tabOverviewVisible
        ) {
            scrollBarWebView?.let { webView ->
                WebViewScrollBar(
                    webView = webView,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        AnimatedVisibility(
            visible = selectedTab.url == BLANK_URL,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BrowserContentBlurTarget(
                enabled = selectedTab.url == BLANK_URL && !tabOverviewVisible,
                onTargetAttached = onBlurTargetAttached,
                onTargetReleased = onBlurTargetReleased,
                modifier = Modifier.fillMaxSize(),
            ) {
                NewTabPage(
                    favorites = controller.favorites,
                    incognito = selectedTab.isIncognito,
                    modeProgress = blankTabModeProgress,
                    revealOriginInRoot = blankTabModeRevealOrigin,
                    onSearch = onSearch,
                    onFavorite = onFavorite,
                )
            }
        }

        PageErrorFeedback(
            state = pageErrorFeedback,
            onRetry = retry@{
                val transition = PageErrorFeedbackRules.requestRetry(pageErrorFeedback)
                if (!transition.shouldReload) return@retry
                pageErrorFeedback = transition.state
                if (onRetry()) {
                    if (transition.emitConfirmHaptic) hapticView.performConfirmHaptic()
                } else {
                    pageErrorFeedback = PageErrorFeedbackRules.observe(
                        current = pageErrorFeedback,
                        error = selectedTab.error,
                        isLoading = selectedTab.isLoading,
                    )
                }
            },
            modifier = Modifier.align(Alignment.Center),
        )

    }

    handoff?.let { currentHandoff ->
        TabHandoffOverlay(
            handoff = currentHandoff,
            tab = controller.activeTabs.firstOrNull { it.id == currentHandoff.tabId },
            favorites = controller.favorites,
            alpha = if (liveFrameTabId == currentHandoff.tabId && !tabOverviewVisible) {
                handoffAlpha
            } else {
                1f
            },
            rootHeightPx = rootHeightPx,
            bottomBarTopPx = bottomBarTopPx,
        )
    }
}

@Composable
private fun ActiveWebView(
    controller: BrowserController,
    visible: Boolean,
    showStatusBarFrostedGlass: Boolean,
    statusBarTint: Int,
    onLiveFrame: (String) -> Unit,
    onBlurTargetAttached: (BlurTarget) -> Unit,
    onBlurTargetReleased: (BlurTarget) -> Unit,
    onWebViewChanged: (BrowserWebView?) -> Unit,
) {
    val density = LocalDensity.current
    val statusBarGeometry = StatusBarFrostedGlassRules.geometry(
        statusBarHeightPx = WindowInsets.statusBars.getTop(density),
        density = density.density,
    )
    val selectedTabId = controller.selectedTabId
    val webViewRevision = controller.webViewRevision
    val currentOnLiveFrame by rememberUpdatedState(onLiveFrame)
    val currentOnBlurTargetAttached by rememberUpdatedState(onBlurTargetAttached)
    val currentOnBlurTargetReleased by rememberUpdatedState(onBlurTargetReleased)
    val currentOnWebViewChanged by rememberUpdatedState(onWebViewChanged)
    AndroidView(
        factory = { context ->
            StatusBarFrostedGlassHost(context).apply {
                tag = WebViewHostState(blurTarget)
            }
        },
        update = { hostView ->
            currentOnBlurTargetAttached(hostView.blurTarget)
            hostView.alpha = if (visible) 1f else 0f
            hostView.updateFrostedGlass(
                geometry = statusBarGeometry,
                tint = statusBarTint,
                visible = showStatusBarFrostedGlass,
            )
            val hostState = hostView.tag as WebViewHostState
            controller.attachSelectedWebView(hostState.container)
            val attachedWebView = hostState.container.getChildAt(0) as? WebView
            if (attachedWebView != null) {
                hostState.bind(
                    tabId = selectedTabId,
                    revision = webViewRevision,
                    webView = attachedWebView,
                ) {
                    currentOnLiveFrame(it)
                }
            }
            currentOnWebViewChanged(attachedWebView as? BrowserWebView)
        },
        onRelease = { hostView ->
            val hostState = hostView.tag as? WebViewHostState
            hostState?.release()
            hostView.tag = null
            hostState?.let { controller.detachWebView(it.container) }
            hostView.release()
            currentOnBlurTargetReleased(hostView.blurTarget)
            currentOnWebViewChanged(null)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private class WebViewHostState(val container: FrameLayout) {
    private var boundTabId: String? = null
    private var boundRevision = -1
    private var boundWebView: WebView? = null
    private var generation = 0
    private var drawObserver: android.view.ViewTreeObserver? = null
    private var drawListener: android.view.ViewTreeObserver.OnDrawListener? = null
    private var drawCompletion: Runnable? = null
    private var drawFallback: Runnable? = null

    fun bind(
        tabId: String,
        revision: Int,
        webView: WebView,
        reportLiveFrame: (String) -> Unit,
    ) {
        if (
            boundTabId == tabId &&
            boundRevision == revision &&
            boundWebView === webView
        ) return
        clearCallbacks()
        boundTabId = tabId
        boundRevision = revision
        boundWebView = webView
        val currentGeneration = ++generation
        var frameReported = false

        fun isCurrent(): Boolean =
            generation == currentGeneration &&
                boundTabId == tabId &&
                boundRevision == revision &&
                boundWebView === webView &&
                webView.parent === container

        lateinit var report: () -> Unit
        fun awaitNextDraw() {
            if (!isCurrent() || frameReported || drawListener != null) return
            val observer = webView.viewTreeObserver
            var drawObserved = false
            val listener = object : android.view.ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (drawObserved) return
                    drawObserved = true
                    webView.post {
                        if (observer.isAlive) observer.removeOnDrawListener(this)
                        if (drawListener === this) drawListener = null
                    }
                    drawCompletion = Runnable(report)
                    webView.postOnAnimation(drawCompletion)
                }
            }
            drawObserver = observer
            drawListener = listener
            observer.addOnDrawListener(listener)
            webView.invalidate()
        }

        report = report@{
            if (!isCurrent() || frameReported) return@report
            frameReported = true
            clearCallbacks()
            reportLiveFrame(tabId)
        }

        webView.postVisualStateCallback(
            System.nanoTime(),
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) = awaitNextDraw()
            },
        )
        drawFallback = Runnable(::awaitNextDraw).also { container.postDelayed(it, 500L) }
    }

    fun release() {
        generation++
        clearCallbacks()
        boundTabId = null
        boundRevision = -1
        boundWebView = null
    }

    private fun clearCallbacks() {
        drawListener?.let { listener ->
            drawObserver?.takeIf { it.isAlive }?.removeOnDrawListener(listener)
        }
        drawListener = null
        drawObserver = null
        drawCompletion?.let { boundWebView?.removeCallbacks(it) }
        drawCompletion = null
        drawFallback?.let(container::removeCallbacks)
        drawFallback = null
    }
}

@Composable
private fun TabHandoffOverlay(
    handoff: TabHandoff,
    tab: BrowserTab?,
    favorites: List<FavoriteEntry>,
    alpha: Float,
    rootHeightPx: Float,
    bottomBarTopPx: FloatState,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (tab != null) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = handoff.preview,
                favicon = handoff.favicon,
                favorites = favorites,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = handoff.previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
            )
        } else if (handoff.isIncognito) {
            IncognitoTabPlaceholder()
        } else {
            TabPreviewPlaceholder(
                title = handoff.title.ifBlank { stringResource(R.string.new_tab_title) },
                favicon = handoff.favicon,
            )
        }
    }
}

@Composable
private fun TabSwitchPreview(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    dragOffset: MutableFloatState,
    dragDirection: Int,
    travelDistance: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
) {
    val density = LocalDensity.current
    val startOffset = if (dragDirection < 0) travelDistance else -travelDistance
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = dragOffset.floatValue
                val travelProgress = (offset.absoluteValue / travelDistance).coerceIn(0f, 1f)
                val cardProgress = (4f * travelProgress * (1f - travelProgress)).coerceIn(0f, 1f)
                translationX = startOffset + offset
                val scale = 1f - 0.03f * cardProgress
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape((32f * cardProgress).dp)
                clip = cardProgress > 0f
                shadowElevation = with(density) { (8f * cardProgress).dp.toPx() }
            }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        FullscreenTabPreviewContent(
            tab = tab,
            preview = preview,
            favicon = favicon,
            favorites = favorites,
            rootHeightPx = rootHeightPx,
            previewTopInsetPx = previewTopInsetPx,
            bottomBarTopPx = bottomBarTopPx,
        )
    }
}

@Composable
internal fun FullscreenTabPreviewContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
    favorites: List<FavoriteEntry>,
    blankFavoritesAlpha: () -> Float = { 1f },
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val capturedHeightPx = preview
            ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            ?.let { bitmap ->
                with(density) { maxWidth.toPx() } * bitmap.height / bitmap.width
            }
        val previewLayout = TabSwitchPreviewLayoutRules.resolve(
            rootHeightPx = rootHeightPx,
            previewTopInsetPx = previewTopInsetPx,
            bottomBarTopPx = bottomBarTopPx.floatValue,
            capturedHeightPx = capturedHeightPx,
        )
        val topInset = with(density) { previewLayout.topInsetPx.toDp() }
        val visibleHeight = with(density) { previewLayout.visibleHeightPx.toDp() }
        when {
            tab.isIncognito -> IncognitoTabPlaceholder()
            tab.url == BLANK_URL -> BlankTabPreview(
                favorites = favorites,
                favoritesAlpha = blankFavoritesAlpha,
            )
            else -> {
                Box(
                    modifier = Modifier
                        .offset(y = topInset)
                        .fillMaxWidth()
                        .height(visibleHeight)
                        .clipToBounds(),
                ) {
                    if (preview != null && !preview.isRecycled) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                        )
                    } else {
                        TabPreviewPlaceholder(title = displayTabTitle(tab), favicon = favicon)
                    }
                }
            }
        }
    }
}

@Composable
private fun rootSafeDrawingPadding(rootView: View): PaddingValues {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = ViewCompat.getRootWindowInsets(rootView)?.getInsets(
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.ime() or
            WindowInsetsCompat.Type.displayCutout(),
    ) ?: return PaddingValues(0.dp)
    val startPx = if (layoutDirection == LayoutDirection.Ltr) insets.left else insets.right
    val endPx = if (layoutDirection == LayoutDirection.Ltr) insets.right else insets.left
    return PaddingValues(
        start = with(density) { startPx.toDp() },
        top = with(density) { insets.top.toDp() },
        end = with(density) { endPx.toDp() },
        bottom = with(density) { insets.bottom.toDp() },
    )
}

@Composable
internal fun BlankTabPreview(
    favorites: List<FavoriteEntry>,
    favoritesAlpha: () -> Float,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val rootView = LocalView.current
    val rootSafeDrawingPadding = rootSafeDrawingPadding(rootView)
    val sourceWidthPx = TabOverviewHeroRules.blankPreviewSourceExtentPx(
        rootViewExtentPx = rootView.width,
        configurationExtentPx = with(density) { configuration.screenWidthDp.dp.toPx() },
    )
    val sourceHeightPx = TabOverviewHeroRules.blankPreviewSourceExtentPx(
        rootViewExtentPx = rootView.height,
        configurationExtentPx = with(density) { configuration.screenHeightDp.dp.toPx() },
    )
    val sourceWidth = with(density) { sourceWidthPx.toDp() }
    val sourceHeight = with(density) { sourceHeightPx.toDp() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
    ) {
        val targetWidthPx = with(density) { maxWidth.toPx() }
        val targetHeightPx = with(density) { maxHeight.toPx() }
        val scale = (targetWidthPx / sourceWidthPx).coerceIn(0.01f, 1f)
        val previewLayout = TabOverviewHeroRules.cardPreviewLayout(
            rootWidthPx = sourceWidthPx,
            rootHeightPx = sourceHeightPx,
            targetWidthPx = targetWidthPx,
            targetHeightPx = targetHeightPx,
            cropTopFraction = PREVIEW_CROP_TOP_FRACTION,
        )
        Box(
            modifier = Modifier
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .size(sourceWidth, sourceHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -previewLayout.sourceTopPx * scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            NewTabPage(
                favorites = favorites,
                incognito = false,
                modeProgress = 0f,
                revealOriginInRoot = Offset.Zero,
                onSearch = {},
                onFavorite = {},
                interactive = false,
                favoritesAlpha = favoritesAlpha,
                explicitSafeDrawingPadding = rootSafeDrawingPadding,
            )
        }
    }
}

