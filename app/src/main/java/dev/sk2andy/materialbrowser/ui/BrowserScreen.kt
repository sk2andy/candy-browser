@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.sk2andy.materialbrowser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabPinningRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private data class TabHandoff(
    val tabId: String,
    val preview: Bitmap?,
    val title: String,
    val favicon: Bitmap?,
    val isIncognito: Boolean,
)

private data class TabExitHero(
    val tabId: String,
    val preview: Bitmap?,
    val startBounds: Rect,
    val isIncognito: Boolean,
)

private data class TabReorderAnimation(
    val tabId: String,
    val targetIndex: Int,
    val indexDeltas: Map<String, Int>,
)

private enum class BrowserBackTarget {
    Settings,
    AddressEditor,
    TabOverview,
    WebHistory,
    None,
}

@Composable
fun BrowserScreen(controller: BrowserController) {
    var tabOverviewVisible by remember { mutableStateOf(false) }
    var addressEditorVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var privacyXRayTabId by remember { mutableStateOf<String?>(null) }
    var clearDialogVisible by remember { mutableStateOf(false) }
    var addressValue by remember { mutableStateOf(TextFieldValue()) }
    val browserDragOffset = remember { mutableFloatStateOf(0f) }
    var browserWidthPx by remember { mutableFloatStateOf(1f) }
    var tabOverviewOpening by remember { mutableStateOf(false) }
    var tabHandoff by remember { mutableStateOf<TabHandoff?>(null) }
    val liveFrameTabIdState = remember { mutableStateOf<String?>(null) }
    var liveFrameTabId by liveFrameTabIdState
    val reportLiveFrame = remember { { tabId: String -> liveFrameTabIdState.value = tabId } }
    val tabHandoffAlpha = remember { Animatable(1f) }
    val settingsBackProgress = remember { Animatable(0f) }
    val backAnimationScope = rememberCoroutineScope()
    var settingsBackEdgeSign by remember { mutableIntStateOf(1) }
    var qrScanInProgress by remember { mutableStateOf(false) }
    val selectedTab = controller.selectedTab
    val context = LocalContext.current
    val qrScanFailureMessage = stringResource(R.string.toast_qr_scan_failed)
    val qrScanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }
    val density = LocalDensity.current
    val rootView = LocalView.current
    val tabSwitchGapPx = with(density) { 8.dp.toPx() }
    val tabSwitchTravelPx = browserWidthPx + tabSwitchGapPx
    val openTabOverview = {
        if (!tabOverviewVisible && !tabOverviewOpening) {
            tabOverviewOpening = true
            controller.prepareTabOverview {
                tabOverviewOpening = false
                tabOverviewVisible = true
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
    val openAddressEditor = {
        val initialAddress = selectedTab.url.takeUnless { it == BLANK_URL }.orEmpty()
        addressValue = TextFieldValue(
            text = initialAddress,
            selection = TextRange(initialAddress.length, 0),
        )
        addressEditorVisible = true
    }
    val openNewTabAndEdit = {
        val previousTabId = controller.selectedTabId
        val createdTabId = controller.createTab()
        if (createdTabId != previousTabId) {
            addressValue = TextFieldValue()
            addressEditorVisible = true
            rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    LaunchedEffect(
        tabHandoff?.tabId,
        liveFrameTabId,
        tabOverviewVisible,
        controller.selectedTabId,
    ) {
        val handoff = tabHandoff ?: return@LaunchedEffect
        if (handoff.tabId != controller.selectedTabId) {
            tabHandoffAlpha.snapTo(1f)
            tabHandoff = null
            return@LaunchedEffect
        }
        if (tabOverviewVisible) return@LaunchedEffect
        if (liveFrameTabId != handoff.tabId) return@LaunchedEffect
        tabHandoffAlpha.snapTo(0f)
        if (tabHandoff?.tabId == handoff.tabId) tabHandoff = null
    }

    LaunchedEffect(selectedTab.id, selectedTab.error) {
        if (selectedTab.error != null && tabHandoff?.tabId == selectedTab.id) {
            tabHandoff = null
        }
    }

    LaunchedEffect(controller.tabs.size, privacyXRayTabId) {
        val xRayTabId = privacyXRayTabId ?: return@LaunchedEffect
        if (controller.tabs.none { it.id == xRayTabId }) privacyXRayTabId = null
    }

    LaunchedEffect(tabOverviewVisible, addressEditorVisible, settingsVisible) {
        if (tabOverviewVisible || addressEditorVisible || settingsVisible) {
            controller.setPreviewCaptureEnabled(false)
        } else {
            delay(120)
            controller.setPreviewCaptureEnabled(true)
        }
    }

    val currentBackTarget by rememberUpdatedState(
        when {
            settingsVisible -> BrowserBackTarget.Settings
            addressEditorVisible -> BrowserBackTarget.AddressEditor
            tabOverviewVisible -> BrowserBackTarget.TabOverview
            selectedTab.canGoBack -> BrowserBackTarget.WebHistory
            else -> BrowserBackTarget.None
        },
    )
    PredictiveBackHandler(enabled = currentBackTarget != BrowserBackTarget.None) { events ->
        val target = currentBackTarget
        var receivedProgress = false
        try {
            events.collect { event ->
                if (target == BrowserBackTarget.Settings) {
                    receivedProgress = true
                    settingsBackEdgeSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1
                    settingsBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
            }
            when (target) {
                BrowserBackTarget.Settings -> {
                    if (receivedProgress) settingsBackProgress.snapTo(1f)
                    settingsVisible = false
                }
                BrowserBackTarget.AddressEditor -> addressEditorVisible = false
                BrowserBackTarget.TabOverview -> tabOverviewVisible = false
                BrowserBackTarget.WebHistory -> controller.goBack()
                BrowserBackTarget.None -> Unit
            }
        } catch (cancellation: CancellationException) {
            if (target == BrowserBackTarget.Settings) {
                backAnimationScope.launch {
                    settingsBackProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
                    )
                }
            }
            throw cancellation
        }
    }
    LaunchedEffect(settingsVisible) {
        if (!settingsVisible && settingsBackProgress.value > 0f) {
            delay(110)
            settingsBackProgress.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { browserWidthPx = it.width.toFloat() }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        BrowserViewport(
            controller = controller,
            selectedTab = selectedTab,
            dragOffset = browserDragOffset,
            travelDistance = tabSwitchTravelPx,
            handoff = tabHandoff,
            handoffAlpha = tabHandoffAlpha.value,
            liveFrameTabId = liveFrameTabId,
            tabOverviewVisible = tabOverviewVisible,
            onLiveFrame = reportLiveFrame,
            onSearch = openAddressEditor,
            onReload = controller::reload,
        )

        if (addressEditorVisible) {
            AddressEditorBackdrop(
                showStartContent = selectedTab.url == BLANK_URL,
                incognito = selectedTab.isIncognito,
                onDismiss = { addressEditorVisible = false },
            )
            AddressSuggestions(
                suggestions = controller.addressSuggestions(addressValue.text, limit = 6),
                onSelect = { suggestion ->
                    val target = suggestion.openTabId
                        ?.let { tabId -> controller.activeTabs.firstOrNull { it.id == tabId } }
                    if (target == null) {
                        controller.submitAddress(suggestion.url)
                    } else {
                        val targetHandoff = TabHandoff(
                            tabId = target.id,
                            preview = controller.previews[target.id].takeUnless { target.isIncognito },
                            title = target.title,
                            favicon = controller.favicons[target.id],
                            isIncognito = target.isIncognito,
                        )
                        if (controller.switchToOpenTab(target.id)) {
                            liveFrameTabId = null
                            tabHandoff = targetHandoff
                            backAnimationScope.launch { tabHandoffAlpha.snapTo(1f) }
                            rootView.performConfirmHaptic()
                        } else {
                            controller.submitAddress(suggestion.url)
                        }
                    }
                    addressEditorVisible = false
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        BrowserBottomBar(
            tab = selectedTab,
            compact = controller.isBottomBarCompact,
            editing = addressEditorVisible,
            onBack = controller::goBack,
            onForward = controller::goForward,
            onAddress = openAddressEditor,
            editValue = addressValue,
            onEditValueChange = { addressValue = it },
            onDismissEditor = { addressEditorVisible = false },
            onSubmitAddress = {
                controller.submitAddress(it)
                addressEditorVisible = false
            },
            onScanQrCode = {
                if (!qrScanInProgress) {
                    qrScanInProgress = true
                    qrScanner.startScan()
                        .addOnSuccessListener { barcode ->
                            qrScanInProgress = false
                            val scannedValue = barcode.rawValue?.trim().orEmpty()
                            if (scannedValue.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    qrScanFailureMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                controller.submitAddress(scannedValue)
                                addressEditorVisible = false
                            }
                        }
                        .addOnCanceledListener { qrScanInProgress = false }
                        .addOnFailureListener {
                            qrScanInProgress = false
                            Toast.makeText(
                                context,
                                qrScanFailureMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                }
            },
            onExpand = controller::expandBottomBar,
            onTabDrag = { delta ->
                if (!addressEditorVisible && !tabOverviewVisible) {
                    val proposed = browserDragOffset.floatValue + delta
                    val tabs = controller.activeTabs
                    val currentIndex = tabs.indexOfFirst { it.id == controller.selectedTabId }
                    val hasTarget = if (proposed < 0f) {
                        currentIndex in 0 until tabs.lastIndex
                    } else {
                        currentIndex > 0
                    }
                    browserDragOffset.floatValue = if (hasTarget) {
                        proposed.coerceIn(-tabSwitchTravelPx, tabSwitchTravelPx)
                    } else {
                        (proposed * 0.24f).coerceIn(
                            -with(density) { 42.dp.toPx() },
                            with(density) { 42.dp.toPx() },
                        )
                    }
                }
            },
            onTabDragStopped = { velocity ->
                val direction = browserDragOffset.floatValue.compareTo(0f)
                val tabs = controller.activeTabs
                val currentIndex = tabs.indexOfFirst { it.id == controller.selectedTabId }
                val targetTab = tabs.getOrNull(currentIndex - direction)
                val minTravel = with(density) { 24.dp.toPx() }
                val fastEnough = browserDragOffset.floatValue.absoluteValue >= minTravel &&
                    velocity.absoluteValue >= with(density) { 900.dp.toPx() } &&
                    velocity.compareTo(0f) == direction
                val shouldSwitch = targetTab != null &&
                    (browserDragOffset.floatValue.absoluteValue >= browserWidthPx * 0.24f || fastEnough)
                val settle = Animatable(browserDragOffset.floatValue)
                settle.animateTo(
                    targetValue = if (shouldSwitch) direction * tabSwitchTravelPx else 0f,
                    initialVelocity = velocity,
                    animationSpec = if (shouldSwitch) {
                        tween(150, easing = FastOutSlowInEasing)
                    } else {
                        spring(dampingRatio = 0.82f, stiffness = 520f)
                    },
                ) { browserDragOffset.floatValue = value }
                if (shouldSwitch && targetTab != null) {
                    liveFrameTabId = null
                    tabHandoffAlpha.snapTo(1f)
                    tabHandoff = TabHandoff(
                        tabId = targetTab.id,
                        preview = controller.previews[targetTab.id].takeUnless { targetTab.isIncognito },
                        title = targetTab.title,
                        favicon = controller.favicons[targetTab.id],
                        isIncognito = targetTab.isIncognito,
                    )
                    browserDragOffset.floatValue = 0f
                    controller.selectTab(targetTab.id)
                    rootView.performConfirmHaptic()
                } else {
                    browserDragOffset.floatValue = 0f
                }
            },
            onTabs = {
                addressEditorVisible = false
                openTabOverview()
            },
            onReload = controller::reload,
            onStop = controller::stopLoading,
            onNewTab = openNewTabAndEdit,
            onToggleIncognito = {
                controller.setBlankTabIncognito(enabled = !selectedTab.isIncognito)
            },
            isFavorite = controller.isSelectedTabFavorite,
            onToggleFavorite = { controller.toggleFavorite() },
            onSettings = {
                addressEditorVisible = false
                settingsVisible = true
            },
            onClearData = { clearDialogVisible = true },
            onPrivacyXRay = {
                privacyXRayTabId = selectedTab.id
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            addressBarPulseNonce = controller.contentActions.addressBarPulseNonce,
            onOpenExternal = controller::openSelectedPageExternally,
            onSummarizeWithAssistant = controller::summarizeSelectedPageWithAssistant,
            onShare = controller::shareSelectedPage,
            onPrint = controller::printSelectedPage,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        TabOverview(
            controller = controller,
            visible = tabOverviewVisible,
            onClose = { tabOverviewVisible = false },
            onSelect = {
                val target = controller.activeTabs.firstOrNull { tab -> tab.id == it }
                if (target != null && target.id != controller.selectedTabId) {
                    liveFrameTabId = null
                    tabHandoff = TabHandoff(
                        tabId = target.id,
                        preview = controller.previews[target.id].takeUnless { target.isIncognito },
                        title = target.title,
                        favicon = controller.favicons[target.id],
                        isIncognito = target.isIncognito,
                    )
                    controller.selectTab(target.id)
                } else {
                    controller.selectTab(it)
                }
            },
            onNewTab = {
                val previousTabId = controller.selectedTabId
                openNewTabAndEdit()
                if (controller.selectedTabId != previousTabId) tabOverviewVisible = false
            },
        )

        AnimatedVisibility(
            visible = settingsVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
        ) {
            SettingsScreen(
                blockerSettings = controller.blockerSettings,
                inactiveTabLifetime = controller.inactiveTabLifetime,
                searchEngine = controller.searchEngine,
                dismissResistancePercent = controller.dismissResistancePercent,
                blockedCount = selectedTab.blockedCount,
                isDefaultBrowser = controller.isDefaultBrowser,
                onBlockerSettingsChanged = controller::updateBlockerSettings,
                onInactiveTabLifetimeChanged = controller::updateInactiveTabLifetime,
                onSearchEngineChanged = controller::updateSearchEngine,
                onDismissResistancePercentChanged = controller::updateDismissResistancePercent,
                onRequestDefaultBrowser = controller::requestDefaultBrowserRole,
                onPrivacyXRay = {
                    privacyXRayTabId = selectedTab.id
                    rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onDismiss = { settingsVisible = false },
                modifier = Modifier.graphicsLayer {
                    val progress = settingsBackProgress.value
                    val transform = PredictiveBackMotion.transform(
                        progress = progress,
                        width = size.width,
                        swipeEdgeSign = settingsBackEdgeSign,
                    )
                    translationX = transform.translationX
                    scaleX = transform.scale
                    scaleY = transform.scale
                    shape = RoundedCornerShape((28f * progress).dp)
                    clip = progress > 0f
                },
            )
        }


        privacyXRayTabId?.let { tabId ->
            val xRayTab = controller.tabs.firstOrNull { it.id == tabId }
            if (xRayTab != null) {
                PrivacyXRaySheet(
                    snapshot = controller.privacySnapshot(tabId),
                    blockerSettings = controller.blockerSettings,
                    siteState = controller.siteProtectionState(tabId),
                    onPause = { persistently ->
                        controller.pauseSiteProtection(tabId, persistently)
                    },
                    onResume = { controller.resumeSiteProtection(tabId) },
                    onDismiss = { privacyXRayTabId = null },
                )
            }
        }

    }

    if (clearDialogVisible) {
        AlertDialog(
            onDismissRequest = { clearDialogVisible = false },
            title = { Text(stringResource(R.string.clear_data_title)) },
            text = { Text(stringResource(R.string.clear_data_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        controller.clearBrowsingData()
                        clearDialogVisible = false
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { clearDialogVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (controller.contentActions.isVisible) {
        WebContentContextSheet(
            target = controller.contentActions.target,
            onOpenLinkInBackground = controller::openContextLinkInBackground,
            onDownloadImage = controller::downloadContextImage,
            onDismiss = controller.contentActions::dismiss,
        )
    }
}

@Composable
private fun BrowserViewport(
    controller: BrowserController,
    selectedTab: BrowserTab,
    dragOffset: MutableFloatState,
    travelDistance: Float,
    handoff: TabHandoff?,
    handoffAlpha: Float,
    liveFrameTabId: String?,
    tabOverviewVisible: Boolean,
    onLiveFrame: (String) -> Unit,
    onSearch: () -> Unit,
    onReload: () -> Unit,
) {
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
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
    var pullProgress by remember(selectedTab.id) { mutableFloatStateOf(0f) }
    var pullRefreshActive by remember(selectedTab.id) { mutableStateOf(false) }
    val pullRefreshEnabled = selectedTab.url != BLANK_URL &&
        !selectedTab.isLoading &&
        !tabOverviewVisible
    val currentPullRefreshEnabled = rememberUpdatedState(pullRefreshEnabled)
    val currentPullProgress = rememberUpdatedState<(Float) -> Unit> { pullProgress = it }
    val currentPullRefresh = rememberUpdatedState {
        if (!pullRefreshActive) {
            pullRefreshActive = true
            pullProgress = 0f
            onReload()
        }
    }
    val pullRefreshTouchListener = remember(selectedTab.id, density.density, touchSlop) {
        PagePullRefreshTouchListener(
            maxStartScroll = PagePullRefreshRules.MAX_START_SCROLL_DP * density.density,
            triggerDistance = PagePullRefreshRules.TRIGGER_DISTANCE_DP * density.density,
            touchSlop = touchSlop,
            isEnabled = { currentPullRefreshEnabled.value },
            onProgress = { currentPullProgress.value(it) },
            onRefresh = { currentPullRefresh.value() },
        )
    }
    LaunchedEffect(selectedTab.id, selectedTab.isLoading, pullRefreshActive) {
        if (pullRefreshActive && !selectedTab.isLoading) {
            pullRefreshActive = false
            pullProgress = 0f
        }
    }

    adjacentTab?.let { tab ->
        TabSwitchPreview(
            tab = tab,
            preview = controller.previews[tab.id],
            favicon = controller.favicons[tab.id],
            dragOffset = dragOffset,
            dragDirection = dragDirection,
            travelDistance = travelDistance,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
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
        ActiveWebView(
            controller = controller,
            visible = !tabOverviewVisible,
            onLiveFrame = onLiveFrame,
            pullRefreshTouchListener = pullRefreshTouchListener,
        )

        AnimatedVisibility(
            visible = selectedTab.url == BLANK_URL,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            NewTabPage(
                favorites = controller.favorites,
                incognito = selectedTab.isIncognito,
                onSearch = onSearch,
                onFavorite = controller::submitAddress,
            )
        }

        selectedTab.error?.let { error ->
            ErrorCard(
                message = error,
                onRetry = controller::reload,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = pullProgress > 0f || pullRefreshActive,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(160)),
        ) {
            ExpressivePullRefreshIndicator(
                progress = pullProgress,
                refreshing = pullRefreshActive,
            )
        }
    }

    handoff?.let { currentHandoff ->
        TabHandoffOverlay(
            handoff = currentHandoff,
            alpha = if (liveFrameTabId == currentHandoff.tabId && !tabOverviewVisible) {
                handoffAlpha
            } else {
                1f
            },
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun ActiveWebView(
    controller: BrowserController,
    visible: Boolean,
    onLiveFrame: (String) -> Unit,
    pullRefreshTouchListener: View.OnTouchListener,
) {
    val selectedTabId = controller.selectedTabId
    val webViewRevision = controller.webViewRevision
    val currentOnLiveFrame by rememberUpdatedState(onLiveFrame)
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply { tag = WebViewHostState(this) }
        },
        update = { hostView ->
            hostView.alpha = if (visible) 1f else 0f
            val hostState = hostView.tag as WebViewHostState
            controller.attachSelectedWebView(hostState.container)
            val attachedWebView = hostState.container.getChildAt(0) as? WebView
            if (attachedWebView != null) {
                hostState.bindTouchListener(attachedWebView, pullRefreshTouchListener)
                hostState.bind(
                    tabId = selectedTabId,
                    revision = webViewRevision,
                    webView = attachedWebView,
                ) {
                    currentOnLiveFrame(it)
                }
            }
        },
        onRelease = { hostView ->
            val hostState = hostView.tag as? WebViewHostState
            hostState?.release()
            hostView.tag = null
            hostState?.let { controller.detachWebView(it.container) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ExpressivePullRefreshIndicator(
    progress: Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveProgress by animateFloatAsState(
        targetValue = if (refreshing) 1f else progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "Pull-to-refresh progress",
    )
    val motion = rememberInfiniteTransition(label = "Expressive loading motion")
    val rotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "Expressive loading rotation",
    )
    val morph by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Expressive loading morph",
    )
    val shapeProgress = if (refreshing) morph else effectiveProgress

    Surface(
        modifier = modifier
            .size(52.dp)
            .then(
                if (refreshing) {
                    Modifier.progressSemantics()
                } else {
                    Modifier.progressSemantics(effectiveProgress)
                },
            )
            .graphicsLayer {
                val entrance = effectiveProgress.coerceIn(0f, 1f)
                alpha = entrance
                scaleX = 0.72f + 0.28f * entrance
                scaleY = scaleX
                translationY = (1f - entrance) * -size.height * 0.35f
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        rotationZ = if (refreshing) rotation else 120f * effectiveProgress
                        scaleX = 0.88f + 0.20f * shapeProgress
                        scaleY = 1.08f - 0.20f * shapeProgress
                        shape = RoundedCornerShape((5f + 8f * shapeProgress).dp)
                        clip = true
                    }
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private class WebViewHostState(val container: FrameLayout) {
    private var boundTabId: String? = null
    private var boundRevision = -1
    private var boundWebView: WebView? = null
    private var touchWebView: WebView? = null
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

    @SuppressLint("ClickableViewAccessibility")
    fun bindTouchListener(webView: WebView, listener: View.OnTouchListener) {
        if (touchWebView !== webView) {
            touchWebView?.setOnTouchListener(null)
            touchWebView = webView
        }
        webView.setOnTouchListener(listener)
    }

    fun release() {
        generation++
        clearCallbacks()
        touchWebView?.setOnTouchListener(null)
        touchWebView = null
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
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (handoff.isIncognito) {
            IncognitoTabPlaceholder()
        } else if (handoff.preview != null && !handoff.preview.isRecycled) {
            Image(
                bitmap = handoff.preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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
    dragOffset: MutableFloatState,
    dragDirection: Int,
    travelDistance: Float,
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
        if (tab.isIncognito) {
            IncognitoTabPlaceholder()
        } else if (preview != null && !preview.isRecycled) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            TabPreviewPlaceholder(title = displayTabTitle(tab), favicon = favicon)
        }
    }
}

@Composable
private fun NewTabPage(
    favorites: List<FavoriteEntry>,
    incognito: Boolean,
    onSearch: () -> Unit,
    onFavorite: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = if (incognito) {
                        listOf(colors.inverseSurface, colors.surface)
                    } else {
                        listOf(colors.primaryContainer, colors.surface)
                    },
                    radius = 1100f,
                ),
            )
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                onClick = onSearch,
                shape = CircleShape,
                color = if (incognito) colors.inverseSurface else colors.primary,
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (incognito) {
                                R.drawable.ic_incognito_filled
                            } else {
                                R.drawable.ic_launcher_foreground_art
                            },
                        ),
                        contentDescription = stringResource(R.string.cd_open_search),
                        modifier = Modifier.size(if (incognito) 48.dp else 68.dp),
                        tint = if (incognito) colors.inverseOnSurface else Color.Unspecified,
                    )
                }
            }
            if (!incognito) {
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.favorites_title),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surfaceContainerHigh.copy(alpha = 0.9f),
                    tonalElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        if (favorites.isEmpty()) {
                            Text(
                                stringResource(R.string.favorites_empty),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        } else {
                            favorites.take(6).forEach { favorite ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onFavorite(favorite.url) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = colors.primary,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            favorite.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            AddressResolver.displayText(favorite.url),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserBottomBar(
    tab: BrowserTab,
    compact: Boolean,
    editing: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    onDismissEditor: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    onScanQrCode: () -> Unit,
    onExpand: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: () -> Unit,
    onToggleIncognito: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSettings: () -> Unit,
    onClearData: () -> Unit,
    onPrivacyXRay: () -> Unit,
    addressBarPulseNonce: Int,
    onOpenExternal: () -> Unit,
    onSummarizeWithAssistant: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val effectiveCompact = compact && !editing
    val tabDragState = rememberDraggableState(onTabDrag)
    val pulseScale = remember { Animatable(1f) }
    val domain = AddressResolver.displayText(tab.url)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val keyboardVisible = editing && imeBottom > navigationBottom
    LaunchedEffect(addressBarPulseNonce) {
        if (addressBarPulseNonce == 0) return@LaunchedEffect
        pulseScale.snapTo(1f)
        pulseScale.animateTo(
            targetValue = 1.055f,
            animationSpec = spring(dampingRatio = 0.48f, stiffness = 650f),
        )
        pulseScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
        )
    }
    val animatedLoadProgress by animateFloatAsState(
        targetValue = (tab.progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(80),
        label = "Ladefortschritt",
    )
    val compactWidth = with(density) {
        textMeasurer.measure(
            text = domain,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        ).size.width.toDp() + 36.dp
    }
    BoxWithConstraints(
        modifier = modifier
            .offset { IntOffset(0, if (keyboardVisible) -imeBottom else 0) }
            .then(if (keyboardVisible) Modifier else Modifier.navigationBarsPadding())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val targetWidth = if (effectiveCompact) compactWidth.coerceIn(96.dp, maxWidth) else maxWidth
        val animatedWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 820f),
            label = "Adressleistenbreite",
        )
        Surface(
            modifier = Modifier
            .width(animatedWidth)
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            },
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 12.dp,
            shadowElevation = 14.dp,
        ) {
            Box {
                AnimatedContent(
                    targetState = effectiveCompact,
                    transitionSpec = {
                        ((fadeIn(tween(90)) + slideInVertically(tween(120)) { it / 3 }) togetherWith
                            (fadeOut(tween(70)) + slideOutVertically(tween(100)) { it / 4 }))
                            .using(SizeTransform(clip = false))
                    },
                    label = "Adressleisteninhalt",
                ) { isCompact ->
                    if (isCompact) {
                        Surface(
                            onClick = onExpand,
                            modifier = Modifier.draggable(
                                state = tabDragState,
                                orientation = Orientation.Horizontal,
                                enabled = !editing,
                                onDragStopped = { velocity -> onTabDragStopped(velocity) },
                            ),
                            color = Color.Transparent,
                        ) {
                            Box {
                                Text(
                                    text = domain,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                AddressBarGrabArea(
                                    onSwipeUp = onTabs,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            }
                        }
                    } else {
                        ExpandedBottomBarContent(
                        tab = tab,
                        menuExpanded = menuExpanded,
                        onMenuExpandedChange = { menuExpanded = it },
                        onBack = onBack,
                        onForward = onForward,
                        onAddress = onAddress,
                        editing = editing,
                        editValue = editValue,
                        onEditValueChange = onEditValueChange,
                        focusRequester = focusRequester,
                        onDismissEditor = onDismissEditor,
                        onSubmitAddress = {
                            keyboard?.hide()
                            onSubmitAddress(it)
                        },
                        onScanQrCode = onScanQrCode,
                        onTabDrag = onTabDrag,
                        onTabDragStopped = onTabDragStopped,
                        onTabs = onTabs,
                        onReload = onReload,
                        onStop = onStop,
                        onNewTab = onNewTab,
                        onToggleIncognito = onToggleIncognito,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onSettings = onSettings,
                        onClearData = onClearData,
                        onPrivacyXRay = onPrivacyXRay,
                        onOpenExternal = onOpenExternal,
                        onSummarizeWithAssistant = onSummarizeWithAssistant,
                        onShare = onShare,
                        onPrint = onPrint,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = tab.isLoading,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(90)),
                ) {
                    if (tab.progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { animatedLoadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    }
                }
            }
        }

    LaunchedEffect(editing, tab.id) {
        if (editing) {
            delay(40)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
}

@Composable
private fun AddressBarGrabArea(
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val gestureView = LocalView.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .pointerInput(Unit) {
                var dragDistance = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val action = AddressBarGestureRules.action(
                            dragDistance = dragDistance,
                            threshold = 56.dp.toPx(),
                        )
                        when (action) {
                            AddressBarVerticalAction.OpenTabs -> currentOnSwipeUp()
                            AddressBarVerticalAction.None -> Unit
                        }
                        if (action != AddressBarVerticalAction.None) {
                            gestureView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            },
    )
}

@Composable
private fun ExpandedBottomBarContent(
    tab: BrowserTab,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    editing: Boolean,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    onDismissEditor: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    onScanQrCode: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: () -> Unit,
    onToggleIncognito: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSettings: () -> Unit,
    onClearData: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onOpenExternal: () -> Unit,
    onSummarizeWithAssistant: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
) {
    val tabDragState = rememberDraggableState(onTabDrag)
    Column {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .draggable(
                        state = tabDragState,
                        orientation = Orientation.Horizontal,
                        enabled = !editing,
                        onDragStopped = { velocity -> onTabDragStopped(velocity) },
                    ),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Box {
                    if (editing) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BasicTextField(
                            value = editValue,
                            onValueChange = onEditValueChange,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = { onSubmitAddress(editValue.text) },
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (editValue.text.isEmpty()) {
                                        Text(
                                            stringResource(R.string.search_or_enter_url),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        IconButton(onClick = onDismissEditor) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close_address_input),
                            )
                        }
                    }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (tab.url == BLANK_URL) {
                                    stringResource(R.string.address_empty_hint)
                                } else {
                                    AddressResolver.displayText(tab.url)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(onClick = onAddress)
                                    .padding(
                                        start = 13.dp,
                                        end = 6.dp,
                                        top = 15.dp,
                                        bottom = 15.dp,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (tab.blockedCount > 0) {
                                PrivacyXRayBadge(
                                    blockedCount = tab.blockedCount,
                                    onClick = onPrivacyXRay,
                                    modifier = Modifier
                                        .zIndex(2f)
                                        .padding(end = 2.dp),
                                )
                            }
                        }
                    }
                    if (!editing) {
                        AddressBarGrabArea(
                            onSwipeUp = onTabs,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
            if (editing && tab.url == BLANK_URL) {
                Spacer(Modifier.width(8.dp))
                IncognitoModeButton(
                    enabled = tab.isIncognito,
                    onClick = onToggleIncognito,
                )
            } else {
                IconButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_tab))
                }
            }
            if (editing && tab.url == BLANK_URL) {
                IconButton(onClick = onScanQrCode) {
                    Icon(
                        painterResource(R.drawable.ic_qr_code_scanner),
                        contentDescription = stringResource(R.string.cd_scan_qr_code),
                    )
                }
            } else {
                Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_back)) },
                        enabled = tab.canGoBack,
                        onClick = {
                            onMenuExpandedChange(false)
                            onBack()
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_forward)) },
                        enabled = tab.canGoForward,
                        onClick = {
                            onMenuExpandedChange(false)
                            onForward()
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (tab.isLoading) R.string.action_stop_loading else R.string.action_reload,
                                ),
                            )
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            if (tab.isLoading) onStop() else onReload()
                        },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_tab_title)) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onNewTab()
                        },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isFavorite) {
                                        R.string.action_remove_favorite
                                    } else {
                                        R.string.action_add_favorite
                                    },
                                ),
                            )
                        },
                        enabled = tab.url != BLANK_URL && !tab.isIncognito,
                        onClick = {
                            onMenuExpandedChange(false)
                            onToggleFavorite()
                        },
                        leadingIcon = {
                            Text(
                                if (isFavorite) "★" else "☆",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 22.sp,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open_external_app)) },
                        enabled = tab.url != BLANK_URL,
                        onClick = {
                            onMenuExpandedChange(false)
                            onOpenExternal()
                        },
                        leadingIcon = { Text("↗", fontSize = 20.sp) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_summarize_with_assistant)) },
                        enabled = tab.url != BLANK_URL,
                        onClick = {
                            onMenuExpandedChange(false)
                            onSummarizeWithAssistant()
                        },
                        leadingIcon = {
                            Text(
                                "✦",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        enabled = tab.url != BLANK_URL,
                        onClick = {
                            onMenuExpandedChange(false)
                            onShare()
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_share),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_print)) },
                        enabled = tab.url != BLANK_URL,
                        onClick = {
                            onMenuExpandedChange(false)
                            onPrint()
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_print),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_settings)) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onSettings()
                        },
                        leadingIcon = { Text("⚙", fontSize = 20.sp) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_clear_browsing_data)) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onClearData()
                        },
                    )
                }
                }
            }
            }
    }
}

@Composable
private fun IncognitoModeButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (enabled) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ) {}
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (enabled) {
                        R.drawable.ic_incognito_filled
                    } else {
                        R.drawable.ic_incognito_outline
                    },
                ),
                contentDescription = stringResource(
                    if (enabled) {
                        R.string.cd_make_blank_tab_regular
                    } else {
                        R.string.cd_make_blank_tab_incognito
                    },
                ),
                modifier = Modifier.size(22.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun AddressEditorBackdrop(
    showStartContent: Boolean,
    incognito: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (showStartContent) {
                    Brush.radialGradient(
                        colors = listOf(
                            if (incognito) {
                                MaterialTheme.colorScheme.inverseSurface
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            MaterialTheme.colorScheme.surface,
                        ),
                        radius = 1100f,
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f),
                        ),
                    )
                },
            )
            .clickable(onClick = onDismiss)
            .statusBarsPadding(),
    ) {
        if (showStartContent) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp),
                shape = CircleShape,
                color = if (incognito) {
                    MaterialTheme.colorScheme.inverseSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shadowElevation = 14.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(
                            if (incognito) {
                                R.drawable.ic_incognito_filled
                            } else {
                                R.drawable.ic_launcher_foreground_art
                            },
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(if (incognito) 48.dp else 68.dp),
                        tint = if (incognito) {
                            MaterialTheme.colorScheme.inverseOnSurface
                        } else {
                            Color.Unspecified
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebContentContextSheet(
    target: WebContentTarget?,
    onOpenLinkInBackground: () -> Unit,
    onDownloadImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (target == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.content_actions_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (target.canOpenLinkInBackground) {
                TextButton(
                    onClick = onOpenLinkInBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_open_link_background_tab))
                }
            }
            if (target.canDownloadImage) {
                TextButton(
                    onClick = onDownloadImage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_download_image))
                }
            }
        }
    }
}

@Composable
private fun AddressSuggestions(
    suggestions: List<AddressSuggestion>,
    onSelect: (AddressSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Surface(
        modifier = modifier
            .imePadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 76.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 12.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            suggestions.forEach { suggestion ->
                val switchesToOpenTab = suggestion.openTabId != null
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(suggestion) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (switchesToOpenTab) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = if (switchesToOpenTab) {
                                painterResource(R.drawable.ic_switch_to_tab)
                            } else {
                                painterResource(R.drawable.ic_history)
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (switchesToOpenTab) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                suggestion.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (switchesToOpenTab) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                AddressResolver.displayText(suggestion.url),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (switchesToOpenTab) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        if (switchesToOpenTab) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_switch_to_tab),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabOverview(
    controller: BrowserController,
    visible: Boolean,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onNewTab: () -> Unit,
) {
    val rootView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overviewTabs = controller.activeTabs
    val initialPage = remember {
        overviewTabs.indexOfFirst { it.id == controller.selectedTabId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { controller.activeTabs.size },
    )
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(10),
        decayAnimationSpec = exponentialDecay(frictionMultiplier = 0.62f),
        snapAnimationSpec = spring(dampingRatio = 0.95f, stiffness = 1_000f),
        snapPositionalThreshold = 0.11f,
    )
    val initialTabId = controller.selectedTabId
    val initialTab = remember(initialTabId, overviewTabs) {
        overviewTabs.firstOrNull { it.id == initialTabId } ?: controller.selectedTab
    }
    val heroPreview = initialTabId
        .takeUnless { initialTab.isIncognito }
        ?.let(controller.previews::get)
        ?.takeIf { !it.isRecycled }
    val heroFavicon = initialTabId
        .let(controller.favicons::get)
        ?.takeIf { !it.isRecycled }
    val heroProgress = remember { Animatable(0f) }
    val overviewScope = rememberCoroutineScope()
    var heroTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var heroStarted by remember { mutableStateOf(false) }
    var heroCompleted by remember { mutableStateOf(false) }
    var heroVisible by remember { mutableStateOf(true) }
    var dismissingTabId by remember { mutableStateOf<String?>(null) }
    var exitHero by remember { mutableStateOf<TabExitHero?>(null) }
    var userPagerGestureActive by remember { mutableStateOf(false) }
    var lastHapticPage by remember { mutableStateOf<Int?>(null) }
    var pagerSessionEndJob by remember { mutableStateOf<Job?>(null) }
    var tabActionsTabId by remember { mutableStateOf<String?>(null) }
    var profileActionsProfileId by remember { mutableStateOf<String?>(null) }
    var emojiPickerTargetId by remember { mutableStateOf<String?>(null) }
    var movingTabId by remember { mutableStateOf<String?>(null) }
    var profileSwitching by remember { mutableStateOf(false) }
    var reorderAnimation by remember { mutableStateOf<TabReorderAnimation?>(null) }
    var reorderLayoutReady by remember { mutableStateOf(false) }
    val reorderProgress = remember { Animatable(1f) }
    val moveProgress = remember { Animatable(0f) }
    val profileSwitchProgress = remember { Animatable(1f) }
    val tabFocusHapticEvents = remember {
        Channel<Unit>(
            capacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    val exitHeroProgress = remember { Animatable(0f) }
    val titleContentColor = TabOverviewContrastRules.titleContentColor(
        primaryContainer = MaterialTheme.colorScheme.primaryContainer,
        tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer,
    )

    fun emitTabFocusHaptics(targetPage: Int) {
        val previousPage = lastHapticPage ?: targetPage
        lastHapticPage = targetPage
        repeat(TabFocusHapticRules.crossedEntryCount(previousPage, targetPage)) {
            tabFocusHapticEvents.trySend(Unit)
        }
    }

    LaunchedEffect(rootView, tabFocusHapticEvents) {
        for (event in tabFocusHapticEvents) {
            rootView.performTabFocusHaptic()
            delay(24)
        }
    }

    DisposableEffect(lifecycleOwner, rootView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                rootView.stopRubberbandHaptic()
                while (tabFocusHapticEvents.tryReceive().isSuccess) Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            pagerSessionEndJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            rootView.stopRubberbandHaptic()
            tabFocusHapticEvents.close()
        }
    }

    LaunchedEffect(pagerState.interactionSource) {
        pagerState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    pagerSessionEndJob?.cancel()
                    while (tabFocusHapticEvents.tryReceive().isSuccess) Unit
                    userPagerGestureActive = true
                    lastHapticPage = pagerState.currentPage
                }
                is DragInteraction.Stop,
                is DragInteraction.Cancel,
                -> {
                    pagerSessionEndJob?.cancel()
                    pagerSessionEndJob = overviewScope.launch {
                        delay(32)
                        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                        emitTabFocusHaptics(pagerState.currentPage)
                        userPagerGestureActive = false
                        lastHapticPage = null
                    }
                }
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { focusedPage ->
                if (userPagerGestureActive) {
                    emitTabFocusHaptics(focusedPage)
                }
            }
    }
    LaunchedEffect(
        controller.activeTabs.size,
        controller.activeProfileId,
        controller.selectedTabId,
        dismissingTabId,
        profileSwitching,
    ) {
        if (dismissingTabId != null || profileSwitching) return@LaunchedEffect
        val selectedIndex = controller.activeTabs.indexOfFirst { it.id == controller.selectedTabId }
            .coerceAtLeast(0)
        if (
            controller.activeTabs.isNotEmpty() &&
            pagerState.currentPage != selectedIndex
        ) {
            pagerState.scrollToPage(selectedIndex)
        }
    }
    LaunchedEffect(controller.activeProfileId) {
        if (profileSwitching) return@LaunchedEffect
        profileSwitchProgress.snapTo(0f)
        val selectedIndex = controller.activeTabs
            .indexOfFirst { it.id == controller.selectedTabId }
            .coerceAtLeast(0)
        if (pagerState.currentPage != selectedIndex) pagerState.scrollToPage(selectedIndex)
        profileSwitchProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 10f else -1f)
            .graphicsLayer { alpha = if (visible) 1f else 0f },
    ) {
        val density = LocalDensity.current
        val rootWidthPx = with(density) { maxWidth.toPx() }
        val rootHeightPx = with(density) { maxHeight.toPx() }
        val heroTarget = heroTargetBounds
        val isExiting = exitHero != null
        val tabCardWidth = (maxWidth * 0.68f).coerceIn(244.dp, 292.dp)
        val pageSlotWidth = tabCardWidth + 18.dp
        val pageSlotWidthPx = with(density) { pageSlotWidth.toPx() }
        val pageHorizontalPadding = ((maxWidth - pageSlotWidth) / 2).coerceAtLeast(0.dp)

        LaunchedEffect(visible, heroTarget) {
            if (!visible || heroStarted) return@LaunchedEffect
            if (TabOverviewHeroRules.canStart(heroTarget != null)) {
                heroStarted = true
                heroProgress.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
                heroCompleted = true
                withFrameNanos { }
                heroVisible = false
            }
        }
        LaunchedEffect(visible) {
            if (!visible) {
                heroProgress.snapTo(0f)
                exitHeroProgress.snapTo(0f)
                heroStarted = false
                heroCompleted = false
                heroVisible = true
                exitHero = null
                return@LaunchedEffect
            }
            delay(250)
            if (!heroStarted) {
                heroStarted = true
                heroProgress.snapTo(1f)
                heroCompleted = true
                withFrameNanos { }
                heroVisible = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = TabOverviewHeroRules.backgroundAlpha(
                        entryProgress = heroProgress.value,
                        isExiting = isExiting,
                    )
                }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = TabOverviewHeroRules.contentAlpha(
                        exitProgress = exitHeroProgress.value,
                        isExiting = isExiting,
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(12.dp))
            ProfileSwitcher(
                profiles = controller.profiles,
                activeProfileId = controller.activeProfileId,
                enabled = dismissingTabId == null &&
                    movingTabId == null &&
                    !profileSwitching &&
                    exitHero == null &&
                    reorderAnimation == null &&
                    tabActionsTabId == null &&
                    profileActionsProfileId == null &&
                    emojiPickerTargetId == null,
                onSelect = { profileId ->
                    if (profileId == controller.activeProfileId) return@ProfileSwitcher
                    overviewScope.launch {
                        profileSwitching = true
                        try {
                            profileSwitchProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 120,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                            if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
                            if (controller.selectProfile(profileId)) {
                                val selectedIndex = controller.activeTabs
                                    .indexOfFirst { it.id == controller.selectedTabId }
                                    .coerceAtLeast(0)
                                if (pagerState.currentPage != selectedIndex) {
                                    pagerState.scrollToPage(selectedIndex)
                                }
                                withFrameNanos { }
                                rootView.performConfirmHaptic()
                            }
                            profileSwitchProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 460f,
                                ),
                            )
                        } finally {
                            withContext(NonCancellable) {
                                profileSwitchProgress.snapTo(1f)
                                profileSwitching = false
                            }
                        }
                    }
                },
                onLongClick = { profileId ->
                    rootView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    profileActionsProfileId = profileId
                },
                onAdd = {
                    rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    emojiPickerTargetId = NEW_PROFILE_TARGET
                },
                modifier = Modifier.graphicsLayer {
                    val chromeProgress =
                        ((heroProgress.value - 0.34f) / 0.66f).coerceIn(0f, 1f)
                    alpha = chromeProgress
                    translationY = (1f - chromeProgress) * -18f
                },
            )
            Spacer(Modifier.height(4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        val progress = profileSwitchProgress.value
                        alpha = progress
                        translationY = (1f - progress) * 14f
                        val scale = 0.97f + progress * 0.03f
                        scaleX = scale
                        scaleY = scale
                    },
                contentPadding = PaddingValues(horizontal = pageHorizontalPadding, vertical = 4.dp),
                pageSpacing = 0.dp,
                pageSize = PageSize.Fixed(pageSlotWidth),
                flingBehavior = pagerFlingBehavior,
                verticalAlignment = Alignment.CenterVertically,
                beyondViewportPageCount = if (reorderAnimation == null) {
                    1
                } else {
                    (controller.activeTabs.size - 1).coerceAtLeast(0)
                },
                userScrollEnabled = dismissingTabId == null &&
                    movingTabId == null &&
                    exitHero == null &&
                    reorderAnimation == null &&
                    tabActionsTabId == null,
                key = { page ->
                    if (reorderAnimation == null) {
                        controller.activeTabs[page].id
                    } else {
                        "tab-reorder-$page"
                    }
                },
            ) { page ->
                val tab = controller.activeTabs[page]
                val cardGestureScope = rememberCoroutineScope()
                var dismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
                var rawDismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
                val breakFreeProgress = remember(tab.id) { Animatable(0f) }
                var breakFreeJob by remember(tab.id) { mutableStateOf<Job?>(null) }
                var dragActive by remember(tab.id) { mutableStateOf(false) }
                var resistanceCleared by remember(tab.id) { mutableStateOf(false) }
                var rubberbandHapticActive by remember(tab.id) { mutableStateOf(false) }
                var dismissHapticPlayed by remember(tab.id) { mutableStateOf(false) }
                var cardBounds by remember(tab.id) { mutableStateOf<Rect?>(null) }
                val dismissThreshold = with(density) {
                    (tabCardWidth.toPx() / 0.53f) * 0.28f
                }
                val resistanceFraction = controller.dismissResistancePercent / 100f
                val dragState = rememberDraggableState { delta ->
                    if (delta < 0f || rawDismissOffset < 0f) {
                        rawDismissOffset = (rawDismissOffset + delta).coerceAtMost(0f)
                        val rawDistance = -rawDismissOffset
                        val hasClearedResistance = TabDismissPhysics.hasClearedResistance(
                            rawDistance = rawDistance,
                            dismissThreshold = dismissThreshold,
                            resistanceFraction = resistanceFraction,
                        )
                        val shouldVibrate = TabDismissPhysics.isInResistancePhase(
                            rawDistance = rawDistance,
                            dismissThreshold = dismissThreshold,
                            resistanceFraction = resistanceFraction,
                        )
                        if (shouldVibrate && !rubberbandHapticActive) {
                            rootView.startRubberbandHaptic()
                            rubberbandHapticActive = true
                        } else if (!shouldVibrate && rubberbandHapticActive) {
                            rootView.stopRubberbandHaptic()
                            rubberbandHapticActive = false
                        }
                        if (hasClearedResistance != resistanceCleared) {
                            resistanceCleared = hasClearedResistance
                            breakFreeJob?.cancel()
                            breakFreeJob = cardGestureScope.launch {
                                breakFreeProgress.animateTo(
                                    targetValue = if (hasClearedResistance) 1f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.72f,
                                        stiffness = 800f,
                                    ),
                                )
                            }
                        }
                        if (
                            hasClearedResistance &&
                            !dismissHapticPlayed
                        ) {
                            rootView.performConfirmHaptic()
                            dismissHapticPlayed = true
                        }
                    }
                }
                val isInitialCard = tab.id == initialTabId
                val realCardVisible = TabOverviewHeroRules.isCardVisible(
                    isInitialCard = isInitialCard,
                    progress = if (heroCompleted) 1f else 0f,
                    isExitTarget = exitHero?.tabId == tab.id,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(
                            when {
                                reorderAnimation?.tabId == tab.id -> 4f
                                dragActive || dismissOffset < 0f -> 2f
                                else -> 0f
                            },
                        )
                        .graphicsLayer {
                            alpha = 1f
                            translationX = TabReorderMotion.translationX(
                                indexDelta = if (reorderLayoutReady) {
                                    reorderAnimation?.indexDeltas?.get(tab.id) ?: 0
                                } else {
                                    0
                                },
                                pageSlotWidthPx = pageSlotWidthPx,
                                progress = reorderProgress.value,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                clip = false
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                val currentDismissOffset = if (dragActive) {
                                    -TabDismissPhysics.visualDistance(
                                        rawDistance = -rawDismissOffset,
                                        releaseProgress = breakFreeProgress.value,
                                    )
                                } else {
                                    dismissOffset
                                }
                                translationY = currentDismissOffset
                                val dismissProgress =
                                    (-currentDismissOffset / (dismissThreshold * 1.7f))
                                        .coerceIn(0f, 1f)
                                val entryAlpha = if (isInitialCard) {
                                    1f
                                } else {
                                    TabOverviewHeroRules.neighborAlpha(heroProgress.value)
                                }
                                val movingProgress = if (movingTabId == tab.id) {
                                    moveProgress.value
                                } else {
                                    0f
                                }
                                alpha = (1f - dismissProgress * 0.72f) *
                                    entryAlpha *
                                    (1f - movingProgress * 0.82f)
                                translationX = movingProgress * 48f
                                val scale = (1f - dismissProgress * 0.05f) *
                                    (1f - movingProgress * 0.06f)
                                scaleX = scale
                                scaleY = scale
                            }
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                enabled = heroCompleted && !heroVisible &&
                                    TabDeletionRules.canDelete(tab) &&
                                    dismissingTabId == null &&
                                    movingTabId == null &&
                                    exitHero == null &&
                                    reorderAnimation == null &&
                                    tabActionsTabId == null,
                                onDragStarted = {
                                    breakFreeJob?.cancel()
                                    breakFreeProgress.snapTo(0f)
                                    rootView.stopRubberbandHaptic()
                                    rawDismissOffset = 0f
                                    dragActive = true
                                    resistanceCleared = false
                                    rubberbandHapticActive = false
                                    dismissHapticPlayed = false
                                },
                                onDragStopped = {
                                    rootView.stopRubberbandHaptic()
                                    rubberbandHapticActive = false
                                    breakFreeJob?.cancel()
                                    breakFreeProgress.stop()
                                    dismissOffset = -TabDismissPhysics.visualDistance(
                                        rawDistance = -rawDismissOffset,
                                        releaseProgress = breakFreeProgress.value,
                                    )
                                    dragActive = false
                                    val farEnough = TabDismissPhysics.hasClearedResistance(
                                        rawDistance = -rawDismissOffset,
                                        dismissThreshold = dismissThreshold,
                                        resistanceFraction = resistanceFraction,
                                    )
                                    if (farEnough) {
                                        val dismissedId = tab.id
                                        val tabs = controller.activeTabs
                                        val centeredId = tabs
                                            .getOrNull(pagerState.currentPage)?.id
                                        val anchorId = if (centeredId == dismissedId) {
                                            tabs.getOrNull(page + 1)?.id
                                                ?: tabs.getOrNull(page - 1)?.id
                                        } else {
                                            centeredId
                                        }
                                        dismissingTabId = dismissedId
                                        overviewScope.launch {
                                            try {
                                                Animatable(dismissOffset).animateTo(
                                                    targetValue = -rootHeightPx,
                                                    animationSpec = tween(
                                                        durationMillis = 180,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                ) { dismissOffset = value }
                                                anchorId?.let { stableAnchorId ->
                                                    val oldAnchorIndex = controller.activeTabs
                                                        .indexOfFirst { it.id == stableAnchorId }
                                                    if (
                                                        oldAnchorIndex >= 0 &&
                                                        pagerState.currentPage != oldAnchorIndex
                                                    ) {
                                                        pagerState.animateScrollToPage(
                                                            page = oldAnchorIndex,
                                                            animationSpec = tween(
                                                                durationMillis = 240,
                                                                easing = FastOutSlowInEasing,
                                                            ),
                                                        )
                                                    }
                                                    controller.selectTab(stableAnchorId)
                                                }
                                                controller.closeTab(dismissedId)
                                                val targetId = anchorId ?: controller.selectedTabId
                                                val newAnchorIndex = controller.activeTabs
                                                    .indexOfFirst { it.id == targetId }
                                                    .coerceAtLeast(0)
                                                if (pagerState.currentPage != newAnchorIndex) {
                                                    pagerState.scrollToPage(newAnchorIndex)
                                                }
                                            } finally {
                                                dismissingTabId = null
                                            }
                                        }
                                    } else {
                                        Animatable(dismissOffset).animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                                        ) { dismissOffset = value }
                                        rawDismissOffset = 0f
                                        breakFreeProgress.snapTo(0f)
                                        resistanceCleared = false
                                        dismissHapticPlayed = false
                                    }
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TabTitleRow(
                            tab = tab,
                            favicon = controller.favicons[tab.id],
                            contentColor = titleContentColor,
                            alpha = {
                                if (isInitialCard) {
                                    ((heroProgress.value - 0.72f) / 0.28f).coerceIn(0f, 1f)
                                } else {
                                    1f
                                }
                            },
                            modifier = Modifier.width(tabCardWidth),
                        )
                        Spacer(Modifier.height(10.dp))
                        TabCard(
                            tab = tab,
                            preview = controller.previews[tab.id],
                            favicon = controller.favicons[tab.id],
                            cardWidth = tabCardWidth,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = if (realCardVisible) 1f else 0f
                                }
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInRoot()
                                    cardBounds = bounds
                                    if (isInitialCard) heroTargetBounds = bounds
                                },
                            onClick = {
                                if (
                                    dismissingTabId != null ||
                                    movingTabId != null ||
                                    exitHero != null ||
                                    reorderAnimation != null ||
                                    tabActionsTabId != null
                                ) {
                                    return@TabCard
                                }
                                val bounds = cardBounds
                                if (bounds == null) {
                                    onSelect(tab.id)
                                    onClose()
                                    return@TabCard
                                }
                                val preview = controller.previews[tab.id]
                                    ?.takeIf { !tab.isIncognito && !it.isRecycled }
                                exitHero = TabExitHero(tab.id, preview, bounds, tab.isIncognito)
                                overviewScope.launch {
                                    exitHeroProgress.snapTo(0f)
                                    withFrameNanos { }
                                    exitHeroProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = 200,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                    onSelect(tab.id)
                                    onClose()
                                }
                            },
                            onLongClick = {
                                if (
                                    dismissingTabId == null &&
                                    movingTabId == null &&
                                    exitHero == null &&
                                    reorderAnimation == null
                                ) {
                                    tabActionsTabId = tab.id
                                }
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val chromeProgress =
                            ((heroProgress.value - 0.42f) / 0.58f).coerceIn(0f, 1f)
                        alpha = chromeProgress
                        translationY = (1f - chromeProgress) * 28f
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = {
                        rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onNewTab()
                    },
                    enabled = dismissingTabId == null &&
                        movingTabId == null &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        tabActionsTabId == null,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_tab))
                }
            }
        }

        if (heroTarget != null && heroVisible) {
            TabHeroLayer(
                targetBounds = heroTarget,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                targetFraction = { heroProgress.value },
            ) {
                if (initialTab.isIncognito) {
                    IncognitoTabPlaceholder()
                } else if (heroPreview != null) {
                    Image(
                        bitmap = heroPreview.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    TabPreviewPlaceholder(title = displayTabTitle(initialTab), favicon = heroFavicon)
                }
            }
        }
        if (visible && heroVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
        exitHero?.let { hero ->
            TabHeroLayer(
                targetBounds = hero.startBounds,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                targetFraction = { 1f - exitHeroProgress.value },
                modifier = Modifier.zIndex(20f),
            ) {
                val preview = hero.preview
                if (hero.isIncognito) {
                    IncognitoTabPlaceholder()
                } else if (preview != null && !preview.isRecycled) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surface,
                                    ),
                                    radius = 1100f,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground_art),
                            contentDescription = null,
                            modifier = Modifier.size(88.dp),
                            tint = Color.Unspecified,
                        )
                    }
                }
            }
        }

        val actionTab = tabActionsTabId?.let { tabId ->
            controller.activeTabs.firstOrNull { it.id == tabId }
        }
        TabActionsSheet(
            tab = actionTab,
            profiles = controller.profiles,
            onTogglePinned = {
                val target = actionTab ?: return@TabActionsSheet
                tabActionsTabId = null
                overviewScope.launch {
                    val oldOrder = controller.activeTabs.map(BrowserTab::id)
                    val newOrder = TabPinningRules.withPinnedState(
                        tabs = controller.activeTabs,
                        tabId = target.id,
                        isPinned = !target.isPinned,
                    ).map(BrowserTab::id)
                    if (oldOrder == newOrder) {
                        if (controller.setTabPinned(target.id, !target.isPinned)) {
                            rootView.performConfirmHaptic()
                        }
                        return@launch
                    }
                    val animation = TabReorderAnimation(
                        tabId = target.id,
                        targetIndex = newOrder.indexOf(target.id).coerceAtLeast(0),
                        indexDeltas = TabReorderMotion.indexDeltas(oldOrder, newOrder),
                    )
                    try {
                        reorderProgress.snapTo(0f)
                        reorderAnimation = animation
                        // Switch Pager to temporary position keys before list mutation. Stable tab
                        // keys would move viewport anchor with target and break FLIP start positions.
                        withFrameNanos { }
                        if (!controller.setTabPinned(target.id, !target.isPinned)) return@launch
                        reorderLayoutReady = true
                        withFrameNanos { }
                        rootView.performConfirmHaptic()
                        reorderProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 360,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        if (pagerState.currentPage != animation.targetIndex) {
                            pagerState.animateScrollToPage(
                                page = animation.targetIndex,
                                animationSpec = tween(
                                    durationMillis = 240,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    } finally {
                        reorderProgress.snapTo(1f)
                        reorderLayoutReady = false
                        reorderAnimation = null
                    }
                }
            },
            onMoveToProfile = { profileId ->
                val target = actionTab ?: return@TabActionsSheet
                tabActionsTabId = null
                overviewScope.launch {
                    try {
                        movingTabId = target.id
                        moveProgress.snapTo(0f)
                        moveProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                        )
                        if (controller.moveTabToProfile(target.id, profileId)) {
                            rootView.performConfirmHaptic()
                        }
                    } finally {
                        moveProgress.snapTo(0f)
                        movingTabId = null
                    }
                }
            },
            onDismiss = { tabActionsTabId = null },
        )

        val actionProfile = profileActionsProfileId?.let { profileId ->
            controller.profiles.firstOrNull { it.id == profileId }
        }
        ProfileActionsSheet(
            profile = actionProfile,
            canDelete = controller.profiles.size > 1,
            onChangeEmoji = {
                val target = actionProfile ?: return@ProfileActionsSheet
                profileActionsProfileId = null
                emojiPickerTargetId = target.id
            },
            onDelete = {
                val target = actionProfile ?: return@ProfileActionsSheet
                profileActionsProfileId = null
                if (controller.deleteProfile(target.id)) rootView.performConfirmHaptic()
            },
            onDismiss = { profileActionsProfileId = null },
        )

        val emojiPickerTarget = emojiPickerTargetId
        EmojiPickerSheet(
            visible = emojiPickerTarget != null,
            creatingProfile = emojiPickerTarget == NEW_PROFILE_TARGET,
            selectedEmoji = controller.profiles
                .firstOrNull { it.id == emojiPickerTarget }
                ?.emoji,
            onSelect = { emoji ->
                val target = emojiPickerTarget ?: return@EmojiPickerSheet
                emojiPickerTargetId = null
                val changed = if (target == NEW_PROFILE_TARGET) {
                    controller.createProfile(emoji) != null
                } else {
                    controller.updateProfileEmoji(target, emoji)
                }
                if (changed) rootView.performConfirmHaptic()
            },
            onDismiss = { emojiPickerTargetId = null },
        )
    }

}

@Composable
private fun ProfileSwitcher(
    profiles: List<BrowserProfile>,
    activeProfileId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileDescription = stringResource(R.string.cd_profile)
    val scrollState = rememberScrollState()
    val activeIndex = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
    val indicatorSlotOffset by animateDpAsState(
        targetValue = (activeIndex * PROFILE_SLOT_WIDTH).dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f),
        label = "profile-indicator-offset",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        val profileContentWidth = (profiles.size * PROFILE_SLOT_WIDTH).dp
        val barWidth = (profileContentWidth + 60.dp)
            .coerceAtMost(maxWidth - 24.dp)
            .coerceAtLeast(116.dp)
        val profileViewportWidth = barWidth - 60.dp
        val density = LocalDensity.current
        LaunchedEffect(activeIndex, profiles.size, profileViewportWidth) {
            withFrameNanos { }
            val slotWidthPx = with(density) { PROFILE_SLOT_WIDTH.dp.roundToPx() }
            val viewportWidthPx = with(density) { profileViewportWidth.roundToPx() }
            val selectedStart = activeIndex * slotWidthPx
            val selectedEnd = selectedStart + slotWidthPx
            val targetScroll = when {
                selectedStart < scrollState.value -> selectedStart
                selectedEnd > scrollState.value + viewportWidthPx ->
                    selectedEnd - viewportWidthPx
                else -> scrollState.value
            }.coerceIn(0, scrollState.maxValue)
            if (targetScroll != scrollState.value) scrollState.animateScrollTo(targetScroll)
        }
        Surface(
            modifier = Modifier
                .width(barWidth)
                .height(60.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF20222C),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(profileViewportWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .horizontalScroll(scrollState),
                ) {
                    Box(
                        modifier = Modifier
                            .width(profileContentWidth)
                            .fillMaxHeight(),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        x = (indicatorSlotOffset + 2.dp).roundToPx(),
                                        y = 0,
                                    )
                                }
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.12f), CircleShape),
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        x = (indicatorSlotOffset + 4.dp).roundToPx(),
                                        y = 0,
                                    )
                                }
                                .size(48.dp),
                            shape = CircleShape,
                            color = Color(0xFF5E6572),
                            shadowElevation = 9.dp,
                        ) {}
                        Row(modifier = Modifier.fillMaxHeight()) {
                            profiles.forEach { profile ->
                                val isSelected = profile.id == activeProfileId
                                val scale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.06f else 0.92f,
                                    animationSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = 540f,
                                    ),
                                    label = "profile-emoji-scale",
                                )
                                Box(
                                    modifier = Modifier
                                        .width(PROFILE_SLOT_WIDTH.dp)
                                        .fillMaxHeight()
                                        .semantics {
                                            contentDescription =
                                                "$profileDescription ${profile.emoji}"
                                            selected = isSelected
                                        }
                                        .combinedClickable(
                                            enabled = enabled,
                                            role = Role.Tab,
                                            onClick = { onSelect(profile.id) },
                                            onLongClick = { onLongClick(profile.id) },
                                            onLongClickLabel = stringResource(
                                                R.string.action_edit_profile,
                                            ),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AnimatedContent(
                                        targetState = profile.emoji,
                                        transitionSpec = {
                                            fadeIn(tween(150)) togetherWith fadeOut(tween(90))
                                        },
                                        label = "profile-emoji",
                                    ) { emoji ->
                                        Text(
                                            text = emoji,
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            },
                                            fontSize = 25.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onAdd,
                    enabled = enabled,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_add_profile),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private const val PROFILE_SLOT_WIDTH = 56

@Composable
private fun TabHeroLayer(
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    targetFraction: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val targetCornerRadiusPx = with(density) { 28.dp.toPx() }
    val heroClipPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val fraction = targetFraction().coerceIn(0f, 1f)
                val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                val scale = width / rootWidthPx
                val clipTop = (rootHeightPx - height / scale) * PREVIEW_CROP_TOP_FRACTION
                translationX = targetBounds.left * fraction
                translationY = targetBounds.top * fraction - clipTop * scale
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .drawWithContent {
                val fraction = targetFraction().coerceIn(0f, 1f)
                val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                val scale = width / rootWidthPx
                val visibleHeight = height / scale
                val clipTop = (rootHeightPx - visibleHeight) * PREVIEW_CROP_TOP_FRACTION
                val cornerRadius = targetCornerRadiusPx * fraction / scale
                heroClipPath.reset()
                heroClipPath.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = clipTop,
                        right = rootWidthPx,
                        bottom = clipTop + visibleHeight,
                        cornerRadius = CornerRadius(cornerRadius),
                    ),
                )
                clipPath(heroClipPath) { this@drawWithContent.drawContent() }
            }
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
private fun TabTitleRow(
    tab: BrowserTab,
    favicon: Bitmap?,
    contentColor: Color,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.graphicsLayer { this.alpha = alpha() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab.isIncognito) {
            Icon(
                painter = painterResource(R.drawable.ic_incognito_outline),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = contentColor,
            )
        } else if (favicon != null && !favicon.isRecycled) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Surface(
                modifier = Modifier.size(26.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        displayTabTitle(tab).take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            displayTabTitle(tab),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
        if (tab.isPinned) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = stringResource(R.string.cd_pinned_tab),
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun displayTabTitle(tab: BrowserTab): String =
    if (tab.url == BLANK_URL || tab.title.isBlank()) {
        stringResource(R.string.new_tab_title)
    } else {
        tab.title
    }

@Composable
private fun TabCard(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(0.53f)
            .then(modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(
                    if (tab.isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
                ),
                role = Role.Button,
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (tab.isIncognito) {
                IncognitoTabPlaceholder()
            } else if (preview != null && !preview.isRecycled) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(
                        horizontalBias = 0f,
                        verticalBias = PREVIEW_CROP_TOP_FRACTION * 2f - 1f,
                    ),
                )
            } else {
                TabPreviewPlaceholder(title = displayTabTitle(tab), favicon = favicon)
            }
        }
    }
}

@Composable
private fun TabActionsSheet(
    tab: BrowserTab?,
    profiles: List<BrowserProfile>,
    onTogglePinned: () -> Unit,
    onMoveToProfile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (tab == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.tab_actions_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onTogglePinned,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (tab.isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
                    ),
                )
            }
            val targetProfiles = profiles.filter { it.id != tab.profileId }
            if (targetProfiles.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.action_move_tab_to_profile),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    targetProfiles.forEach { profile ->
                        Surface(
                            modifier = Modifier
                                .size(52.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onMoveToProfile(profile.id) },
                                ),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(profile.emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileActionsSheet(
    profile: BrowserProfile?,
    canDelete: Boolean,
    onChangeEmoji: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (profile == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.profile_actions_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onChangeEmoji,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_change_profile_icon))
            }
            if (canDelete) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.action_delete_profile_keep_tabs),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerSheet(
    visible: Boolean,
    creatingProfile: Boolean,
    selectedEmoji: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(
                    if (creatingProfile) R.string.add_profile_title
                    else R.string.change_profile_icon_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            PROFILE_EMOJIS.chunked(6).forEach { rowEmojis ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    rowEmojis.forEach { emoji ->
                        val isSelected = emoji == selectedEmoji
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onSelect(emoji) },
                                ),
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            tonalElevation = if (isSelected) 5.dp else 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 23.sp)
                            }
                        }
                    }
                    repeat(6 - rowEmojis.size) { Spacer(Modifier.size(48.dp)) }
                }
            }
        }
    }
}

private const val PREVIEW_CROP_TOP_FRACTION = 0.25f
private const val NEW_PROFILE_TARGET = "__new_profile__"
private val PROFILE_EMOJIS = listOf(
    "🍬", "⭐", "💼", "🛒", "🎮", "📚",
    "✈️", "🏠", "🎵", "🧪", "📰", "❤️",
    "🔥", "🌙", "🌿", "🎨", "🏋️", "💡",
    "🏫", "🎒", "✏️", "🎓", "📖", "🧑‍🎓",
    "👶", "🧸", "🍼", "👨‍👩‍👧", "💍", "💒",
    "💰", "💳", "🪙", "📈", "🎬", "🍿",
    "📺", "📷", "💻", "📱", "🚗", "🚲",
    "⚽", "🏀", "🏖️", "🍕", "☕", "🎉",
    "🎁", "🐶", "🐱", "🌍", "🩺", "📅",
)

@Composable
private fun IncognitoTabPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.inverseSurface,
                    ),
                    radius = 900f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_incognito_filled),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TabPreviewPlaceholder(
    title: String,
    favicon: Bitmap?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (favicon != null && !favicon.isRecycled) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = title.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    blockerSettings: BlockerSettings,
    inactiveTabLifetime: InactiveTabLifetime,
    searchEngine: SearchEngine,
    dismissResistancePercent: Int,
    blockedCount: Int,
    isDefaultBrowser: Boolean,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onRequestDefaultBrowser: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lifetimeMenuExpanded by remember { mutableStateOf(false) }
    var searchEngineMenuExpanded by remember { mutableStateOf(false) }
    var resistancePercent by remember(dismissResistancePercent) {
        mutableFloatStateOf(dismissResistancePercent.toFloat())
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SettingsSectionTitle(stringResource(R.string.settings_section_search))
            Spacer(Modifier.height(8.dp))
            Box {
                SettingsChoice(
                    title = stringResource(R.string.settings_search_engine),
                    value = searchEngine.displayName,
                    expanded = searchEngineMenuExpanded,
                    onClick = { searchEngineMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = searchEngineMenuExpanded,
                    onDismissRequest = { searchEngineMenuExpanded = false },
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    SearchEngine.entries.forEach { engine ->
                        DropdownMenuItem(
                            text = { Text(engine.displayName) },
                            onClick = {
                                searchEngineMenuExpanded = false
                                onSearchEngineChanged(engine)
                            },
                            trailingIcon = {
                                if (engine == searchEngine) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_tabs))
            Spacer(Modifier.height(8.dp))
            Box {
                SettingsChoice(
                    title = stringResource(R.string.settings_auto_close_tabs),
                    value = inactiveTabLifetime.displayName(),
                    expanded = lifetimeMenuExpanded,
                    onClick = { lifetimeMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = lifetimeMenuExpanded,
                    onDismissRequest = { lifetimeMenuExpanded = false },
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    InactiveTabLifetime.entries.forEach { lifetime ->
                        DropdownMenuItem(
                            text = { Text(lifetime.displayName()) },
                            onClick = {
                                lifetimeMenuExpanded = false
                                onInactiveTabLifetimeChanged(lifetime)
                            },
                            trailingIcon = {
                                if (lifetime == inactiveTabLifetime) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_gestures))
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        stringResource(R.string.settings_tab_dismiss_resistance),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            R.string.settings_tab_dismiss_resistance_summary,
                            resistancePercent.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = resistancePercent,
                        onValueChange = { resistancePercent = it },
                        onValueChangeFinished = {
                            onDismissResistancePercentChanged(resistancePercent.roundToInt())
                        },
                        valueRange = 10f..90f,
                        steps = 7,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_browser))
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = { if (!isDefaultBrowser) onRequestDefaultBrowser() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        stringResource(R.string.settings_default_browser),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            if (isDefaultBrowser) {
                                R.string.settings_default_browser_active
                            } else {
                                R.string.settings_make_default_browser
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDefaultBrowser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_protection))
            Spacer(Modifier.height(6.dp))
            PrivacyXRaySettingsCounter(
                blockedCount = blockedCount,
                onClick = onPrivacyXRay,
            )
            Spacer(Modifier.height(18.dp))
            SettingsSwitch(
                title = stringResource(R.string.settings_block_ads_title),
                subtitle = stringResource(R.string.settings_block_ads_subtitle),
                checked = blockerSettings.blockAdsAndTrackers,
                onCheckedChange = {
                    onBlockerSettingsChanged(blockerSettings.copy(blockAdsAndTrackers = it))
                },
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_hide_cookie_banners_title),
                subtitle = stringResource(R.string.settings_hide_cookie_banners_subtitle),
                checked = blockerSettings.hideCookieConsent,
                onCheckedChange = {
                    onBlockerSettingsChanged(blockerSettings.copy(hideCookieConsent = it))
                },
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_block_third_party_cookies_title),
                subtitle = stringResource(R.string.settings_block_third_party_cookies_subtitle),
                checked = blockerSettings.blockThirdPartyCookies,
                onCheckedChange = {
                    onBlockerSettingsChanged(blockerSettings.copy(blockThirdPartyCookies = it))
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_protection_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PrivacyXRaySettingsCounter(
    blockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .testTag(PrivacyXRayTestTags.SettingsCounter),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(
                    R.plurals.blocked_requests_count,
                    blockedCount,
                    blockedCount,
                ),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "◈",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun InactiveTabLifetime.displayName(): String = when (this) {
    InactiveTabLifetime.Never -> stringResource(R.string.tab_lifetime_never)
    InactiveTabLifetime.SixHours -> pluralStringResource(R.plurals.tab_lifetime_hours, 6, 6)
    InactiveTabLifetime.OneDay -> pluralStringResource(R.plurals.tab_lifetime_days, 1, 1)
    InactiveTabLifetime.ThreeDays -> pluralStringResource(R.plurals.tab_lifetime_days, 3, 3)
    InactiveTabLifetime.SevenDays -> pluralStringResource(R.plurals.tab_lifetime_days, 7, 7)
    InactiveTabLifetime.ThirtyDays -> pluralStringResource(R.plurals.tab_lifetime_days, 30, 30)
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsChoice(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "Auswahlindikator",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.error_page_unreachable), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private fun View.performConfirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )
}

private fun View.performTabFocusHaptic() {
    if (!performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)) {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

private fun View.startRubberbandHaptic() {
    val vibrator = rubberbandVibrator() ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(
                longArrayOf(0L, 1_000L),
                intArrayOf(0, 8),
                0,
            )
        } else {
            VibrationEffect.createWaveform(
                longArrayOf(0L, 3L, 117L),
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0),
                0,
            )
        }
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(5_000L)
    }
}

private fun View.stopRubberbandHaptic() {
    rubberbandVibrator()?.cancel()
}

private fun View.rubberbandVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
