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
import android.view.accessibility.AccessibilityManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
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
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.browser.commands.AddressSubmission
import dev.sk2andy.materialbrowser.browser.commands.AddressSubmissionRules
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommand
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommandKind
import dev.sk2andy.materialbrowser.browser.commands.CommandActions
import dev.sk2andy.materialbrowser.browser.commands.CommandConfirmation
import dev.sk2andy.materialbrowser.browser.commands.CommandCookieScope
import dev.sk2andy.materialbrowser.browser.commands.CommandDispatchOutcome
import dev.sk2andy.materialbrowser.browser.commands.CommandDispatcher
import dev.sk2andy.materialbrowser.browser.commands.CommandMatcher
import dev.sk2andy.materialbrowser.browser.commands.CommandSuggestion
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabOverviewMode
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
    val previewTopInsetPx: Int,
)

private data class TabExitHero(
    val tabId: String,
    val preview: Bitmap?,
    val startBounds: Rect,
    val isIncognito: Boolean,
    val startCornerRadius: Dp = 28.dp,
    val previewTopInsetPx: Int = 0,
    val mode: TabOverviewMode = TabOverviewMode.Hero,
)

private data class TabReorderAnimation(
    val tabId: String,
    val targetIndex: Int,
    val indexDeltas: Map<String, Int>,
)

private enum class BrowserBackTarget {
    FilterStudio,
    Settings,
    AddressEditor,
    CandyTrail,
    TabOverview,
    WebHistory,
    None,
}

@Composable
fun BrowserScreen(controller: BrowserController) {
    controller.activeSiteCapsule?.let { capsule ->
        SiteCapsuleBrowserScreen(controller, capsule)
        return
    }
    var tabOverviewVisible by rememberSaveable { mutableStateOf(false) }
    var candyTrailTabId by rememberSaveable { mutableStateOf<String?>(null) }
    var candyTrailSourceBounds by remember { mutableStateOf<Rect?>(null) }
    var newTabDestinationBounds by remember { mutableStateOf<Rect?>(null) }
    var addressEditorVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var privacyXRayTabId by remember { mutableStateOf<String?>(null) }
    var filterStudioVisible by rememberSaveable { mutableStateOf(false) }
    var filterStudioSelectedRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var clearDialogVisible by remember { mutableStateOf(false) }
    var capsuleEditorVisible by remember { mutableStateOf(false) }
    var editingCapsuleId by remember { mutableStateOf<String?>(null) }
    var pendingCapsuleDelete by remember { mutableStateOf<SiteCapsule?>(null) }
    var addressValue by remember { mutableStateOf(TextFieldValue()) }
    var highlightedSuggestionIndex by remember { mutableIntStateOf(-1) }
    var addressFocusNonce by remember { mutableIntStateOf(0) }
    var pendingCommand by remember { mutableStateOf<CommandSuggestion?>(null) }
    var overviewGestureProgress by remember { mutableFloatStateOf(0f) }
    var overviewGestureSettleJob by remember { mutableStateOf<Job?>(null) }
    val overviewGestureScope = rememberCoroutineScope()
    var favoriteFeedbackId by remember { mutableIntStateOf(0) }
    var favoriteFeedbackEvent by remember { mutableStateOf<FavoriteFeedbackEvent?>(null) }
    var favoriteSnackbarJob by remember { mutableStateOf<Job?>(null) }
    val favoriteSnackbarHostState = remember { SnackbarHostState() }
    var activeCommandExecutionId by remember { mutableStateOf<String?>(null) }
    var commandFeedback by remember { mutableStateOf<AddressCommandFeedback?>(null) }
    val browserDragOffset = remember { mutableFloatStateOf(0f) }
    var browserWidthPx by remember { mutableFloatStateOf(1f) }
    var browserHeightPx by remember { mutableFloatStateOf(1f) }
    var bottomBarTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var tabOverviewOpening by remember { mutableStateOf(false) }
    var tabHandoff by remember { mutableStateOf<TabHandoff?>(null) }
    val liveFrameTabIdState = remember { mutableStateOf<String?>(null) }
    var liveFrameTabId by liveFrameTabIdState
    val reportLiveFrame = remember { { tabId: String -> liveFrameTabIdState.value = tabId } }
    val tabHandoffAlpha = remember { Animatable(1f) }
    val settingsBackProgress = remember { Animatable(0f) }
    val candyTrailBackProgress = remember { Animatable(0f) }
    val newTabCreationMotion = rememberNewTabCreationMotionController()
    val backAnimationScope = rememberCoroutineScope()
    var settingsBackEdgeSign by remember { mutableIntStateOf(1) }
    var candyTrailBackEdgeSign by remember { mutableIntStateOf(1) }
    var qrScanInProgress by remember { mutableStateOf(false) }
    val selectedTab = controller.selectedTab
    val blankTabModeProgress = rememberBlankTabModeProgress(
        tabId = selectedTab.id,
        incognito = selectedTab.isIncognito,
    )
    var blankTabModeRevealOrigin by remember(selectedTab.id) {
        mutableStateOf(Offset.Unspecified)
    }
    val startPageSearchTransform = remember(selectedTab.id) { StartPageSearchTransformState() }
    val startPageSearchTransformEnabled = selectedTab.url == BLANK_URL
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
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
    val keyboard = LocalSoftwareKeyboardController.current
    val favoriteAddedMessage = stringResource(R.string.favorite_added_confirmation)
    val favoriteRemovedMessage = stringResource(R.string.favorite_removed_confirmation)
    val undoLabel = stringResource(R.string.action_undo)
    val tabSwitchGapPx = with(density) { 8.dp.toPx() }
    val tabSwitchTravelPx = browserWidthPx + tabSwitchGapPx
    val settleOverviewGesture: () -> Unit = {
        overviewGestureSettleJob?.cancel()
        overviewGestureSettleJob = overviewGestureScope.launch {
            val settleProgress = Animatable(overviewGestureProgress)
            settleProgress.updateBounds(lowerBound = 0f, upperBound = 1f)
            settleProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
            ) { overviewGestureProgress = value }
        }
    }
    val openTabOverview = {
        if (!tabOverviewVisible && !tabOverviewOpening) {
            tabOverviewOpening = true
            controller.prepareTabOverview {
                tabOverviewOpening = false
                tabOverviewVisible = true
            }
        }
    }
    val openAddressEditor: () -> Unit = {
        if (activeCommandExecutionId == null) {
            val initialAddress = selectedTab.url.takeUnless { it == BLANK_URL }.orEmpty()
            addressValue = TextFieldValue(
                text = initialAddress,
                selection = TextRange(initialAddress.length, 0),
            )
            addressEditorVisible = true
            highlightedSuggestionIndex = -1
            addressFocusNonce++
        }
    }
    fun createTabWithMotion(
        isIncognito: Boolean,
        sourceBounds: Rect?,
        emitHaptic: Boolean,
    ): Boolean {
        val previousTabId = controller.selectedTabId
        val createdTabId = controller.createTab(isIncognito = isIncognito)
        if (createdTabId == previousTabId) return false
        newTabCreationMotion.launch(
            sourceBounds = sourceBounds,
            destinationBounds = newTabDestinationBounds,
            isIncognito = controller.selectedTab.isIncognito,
        )
        if (emitHaptic) rootView.performConfirmHaptic()
        return true
    }
    val openNewTabAndEdit: (Rect?) -> Unit = { sourceBounds ->
        if (createTabWithMotion(isIncognito = false, sourceBounds = sourceBounds, emitHaptic = true)) {
            addressValue = TextFieldValue()
            addressEditorVisible = true
            highlightedSuggestionIndex = -1
            addressFocusNonce++
        }
    }
    val suggestionItems = if (addressEditorVisible) {
        controller.addressSuggestionItems(addressValue.text, limit = 10)
    } else {
        emptyList()
    }
    val commandActions = object : CommandActions {
        override fun clearCacheAndReload(): Boolean = controller.clearCacheAndReload()
        override fun clearCookiesAndReload(onComplete: (Boolean) -> Unit): Boolean =
            controller.clearCookiesAndReload(onComplete)
        override fun reload(): Boolean {
            if (controller.selectedTab.url == BLANK_URL || controller.selectedTab.isLoading) return false
            controller.reload()
            return true
        }
        override fun stopLoading(): Boolean {
            if (!controller.selectedTab.isLoading) return false
            controller.stopLoading()
            return true
        }
        override fun setSelectedTabPinned(isPinned: Boolean): Boolean =
            controller.setTabPinned(controller.selectedTabId, isPinned)
        override fun closeDuplicateTabs(confirmedTabIds: List<String>): Int =
            controller.closeDuplicateTabs(confirmedTabIds)
        override fun moveSelectedTabToProfile(profileId: String): Boolean =
            controller.moveTabToProfile(controller.selectedTabId, profileId)
        override fun switchProfile(profileId: String): Boolean = controller.selectProfile(profileId)
        override fun createTab(isIncognito: Boolean): Boolean = createTabWithMotion(
            isIncognito = isIncognito,
            sourceBounds = null,
            emitHaptic = false,
        )
        override fun openSettings(): Boolean = true
    }

    fun handleCommandOutcome(
        command: BrowserCommand,
        outcome: CommandDispatchOutcome,
    ): Boolean = when (outcome) {
        is CommandDispatchOutcome.Pending -> true
        is CommandDispatchOutcome.Rejected -> {
            commandFeedback = checkNotNull(AddressCommandFeedbackRules.from(outcome))
            addressEditorVisible = true
            rootView.performRejectHaptic()
            false
        }
        is CommandDispatchOutcome.Succeeded -> {
            commandFeedback = checkNotNull(AddressCommandFeedbackRules.from(outcome))
            when (command.kind) {
                BrowserCommandKind.NewRegularTab,
                BrowserCommandKind.NewIncognitoTab,
                -> {
                    addressValue = TextFieldValue()
                    highlightedSuggestionIndex = -1
                    addressEditorVisible = true
                }
                BrowserCommandKind.OpenSettings -> {
                    addressEditorVisible = false
                    settingsVisible = true
                }
                else -> addressEditorVisible = false
            }
            rootView.performConfirmHaptic()
            true
        }
    }

    fun runCommand(command: BrowserCommand): Boolean {
        if (activeCommandExecutionId != null) return false
        activeCommandExecutionId = command.executionId
        val outcome = CommandDispatcher.dispatch(
            command = command,
            actions = commandActions,
            onPendingOutcome = { completedOutcome ->
                if (activeCommandExecutionId == command.executionId) {
                    activeCommandExecutionId = null
                    handleCommandOutcome(command, completedOutcome)
                }
            },
        )
        if (outcome !is CommandDispatchOutcome.Pending) {
            activeCommandExecutionId = null
        } else if (activeCommandExecutionId == command.executionId) {
            addressEditorVisible = false
        }
        return handleCommandOutcome(command, outcome)
    }

    fun selectCommand(suggestion: CommandSuggestion): Unit {
        if (suggestion.command.confirmation == CommandConfirmation.None) {
            runCommand(suggestion.command)
        } else {
            pendingCommand = suggestion
            keyboard?.hide()
        }
    }

    fun selectNavigation(suggestion: AddressSuggestion): Unit {
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
                previewTopInsetPx = controller.previewTopInsetPx(target.id),
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
    }

    fun selectSuggestion(item: AddressSuggestionItem): Unit {
        when (item) {
            is AddressSuggestionItem.Navigation -> selectNavigation(item.suggestion)
            is AddressSuggestionItem.Command -> selectCommand(item.suggestion)
        }
    }

    fun submitAddressOrCommand(input: String): Unit {
        when (
            val submission = AddressSubmissionRules.resolve(
                input = input,
                suggestions = suggestionItems,
                highlightedIndex = highlightedSuggestionIndex,
            )
        ) {
            is AddressSubmission.Select -> selectSuggestion(submission.suggestion)
            is AddressSubmission.Navigate -> {
                controller.submitAddress(submission.input)
                addressEditorVisible = false
            }
            AddressSubmission.None -> Unit
        }
    }

    fun moveSuggestionHighlight(delta: Int): Unit {
        if (suggestionItems.isEmpty()) return
        highlightedSuggestionIndex = when {
            highlightedSuggestionIndex < 0 && delta > 0 -> 0
            highlightedSuggestionIndex < 0 -> suggestionItems.lastIndex
            else -> (highlightedSuggestionIndex + delta).coerceIn(-1, suggestionItems.lastIndex)
        }
    }

    LaunchedEffect(addressValue.text, suggestionItems.map(AddressSuggestionItem::stableId)) {
        highlightedSuggestionIndex = if (
            CommandMatcher.isExplicitCommandQuery(addressValue.text) && suggestionItems.isNotEmpty()
        ) {
            0
        } else {
            -1
        }
    }

    LaunchedEffect(commandFeedback) {
        val shownFeedback = commandFeedback ?: return@LaunchedEffect
        val baseDuration = AddressCommandFeedbackRules.displayDurationMillis(shownFeedback)
        val recommendedDuration = accessibilityManager?.getRecommendedTimeoutMillis(
            baseDuration.toInt(),
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS,
        )?.toLong() ?: baseDuration
        delay(
            AddressCommandFeedbackRules.accessibleDurationMillis(
                feedback = shownFeedback,
                recommendedTimeoutMillis = recommendedDuration,
            ),
        )
        if (commandFeedback == shownFeedback) {
            commandFeedback = null
            if (addressEditorVisible) addressFocusNonce++
        }
    }

    LaunchedEffect(
        addressEditorVisible,
        startPageSearchTransformEnabled,
        startPageSearchTransform.hasSourceBounds,
        startPageSearchTransform.hasTargetBounds,
    ) {
        startPageSearchTransform.animate(
            editing = addressEditorVisible,
            enabled = startPageSearchTransformEnabled,
        )
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

    LaunchedEffect(
        tabOverviewVisible,
        addressEditorVisible,
        settingsVisible,
        filterStudioVisible,
        candyTrailTabId,
    ) {
        if (
            tabOverviewVisible || addressEditorVisible || settingsVisible ||
            filterStudioVisible || candyTrailTabId != null
        ) {
            controller.setPreviewCaptureEnabled(false)
        } else {
            delay(120)
            controller.setPreviewCaptureEnabled(true)
        }
    }

    val currentBackTarget by rememberUpdatedState(
        when {
            filterStudioVisible -> BrowserBackTarget.FilterStudio
            settingsVisible -> BrowserBackTarget.Settings
            addressEditorVisible -> BrowserBackTarget.AddressEditor
            candyTrailTabId != null -> BrowserBackTarget.CandyTrail
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
                } else if (target == BrowserBackTarget.CandyTrail) {
                    receivedProgress = true
                    candyTrailBackEdgeSign =
                        if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1
                    candyTrailBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
            }
            when (target) {
                BrowserBackTarget.FilterStudio -> filterStudioVisible = false
                BrowserBackTarget.Settings -> {
                    if (receivedProgress) settingsBackProgress.snapTo(1f)
                    settingsVisible = false
                }
                BrowserBackTarget.AddressEditor -> addressEditorVisible = false
                BrowserBackTarget.CandyTrail -> {
                    if (receivedProgress) candyTrailBackProgress.snapTo(1f)
                    candyTrailTabId = null
                    candyTrailSourceBounds = null
                }
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
            } else if (target == BrowserBackTarget.CandyTrail) {
                backAnimationScope.launch {
                    candyTrailBackProgress.animateTo(
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
    LaunchedEffect(candyTrailTabId, controller.activeTabs) {
        val trailTabId = candyTrailTabId
        if (trailTabId != null && controller.activeTabs.none { it.id == trailTabId }) {
            candyTrailTabId = null
            candyTrailSourceBounds = null
        }
        if (trailTabId == null && candyTrailBackProgress.value > 0f) {
            delay(110)
            candyTrailBackProgress.snapTo(0f)
        }
    }
    LaunchedEffect(tabOverviewVisible) {
        if (tabOverviewVisible) {
            overviewGestureSettleJob?.cancel()
            overviewGestureProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                browserWidthPx = it.width.toFloat()
                browserHeightPx = it.height.toFloat()
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = AddressBarOverviewGestureRules.resistedProgress(
                        overviewGestureProgress,
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
        BrowserViewport(
            controller = controller,
            selectedTab = selectedTab,
            dragOffset = browserDragOffset,
            travelDistance = tabSwitchTravelPx,
            rootHeightPx = browserHeightPx,
            bottomBarTopPx = bottomBarTopPx,
            handoff = tabHandoff,
            handoffAlpha = tabHandoffAlpha.value,
            liveFrameTabId = liveFrameTabId,
            tabOverviewVisible = tabOverviewVisible,
            overviewGestureProgress = overviewGestureProgress,
            onLiveFrame = reportLiveFrame,
            onSearch = openAddressEditor,
            onReload = controller::reload,
            onNewTabDestinationBounds = { bounds ->
                newTabDestinationBounds = bounds
                newTabCreationMotion.updateDestination(bounds)
            },
            blankTabModeProgress = blankTabModeProgress,
            blankTabModeRevealOrigin = blankTabModeRevealOrigin,
            startPageSearchTransform = startPageSearchTransform,
            searchEditing = addressEditorVisible,
            onRetry = controller::retryFailedPage,
        )

        if (addressEditorVisible) {
            AddressEditorBackdrop(
                showStartContent = selectedTab.url == BLANK_URL,
                modeProgress = blankTabModeProgress,
                revealOriginInRoot = blankTabModeRevealOrigin,
                onDismiss = { addressEditorVisible = false },
            )
            if (commandFeedback == null) {
                AddressSuggestions(
                    suggestions = suggestionItems,
                    highlightedIndex = highlightedSuggestionIndex,
                    onHighlight = { highlightedSuggestionIndex = it },
                    onSelect = ::selectSuggestion,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        BrowserBottomBar(
            tab = selectedTab,
            compact = controller.isBottomBarCompact,
            editing = addressEditorVisible,
            commandFeedback = commandFeedback,
            feedbackGesturesEnabled = !addressEditorVisible && !settingsVisible,
            onBack = controller::goBack,
            onForward = controller::goForward,
            onAddress = openAddressEditor,
            editValue = addressValue,
            onEditValueChange = { addressValue = it },
            addressFocusNonce = addressFocusNonce,
            onMoveAddressSuggestion = ::moveSuggestionHighlight,
            onActivateAddressSuggestion = {
                val highlighted = suggestionItems.getOrNull(highlightedSuggestionIndex)
                if (highlighted == null) {
                    submitAddressOrCommand(addressValue.text)
                } else {
                    selectSuggestion(highlighted)
                }
            },
            onDismissEditor = { addressEditorVisible = false },
            onSubmitAddress = ::submitAddressOrCommand,
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
                    (
                        AddressBarTabSwitchRules.hasReachedDistance(
                            dragDistance = browserDragOffset.floatValue.absoluteValue,
                            viewportWidth = browserWidthPx,
                        ) || fastEnough
                    )
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
                        previewTopInsetPx = controller.previewTopInsetPx(targetTab.id),
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
            overviewGestureEnabled = !tabOverviewOpening && !tabOverviewVisible,
            overviewGestureProgress = overviewGestureProgress,
            onOverviewGestureProgress = { progress ->
                overviewGestureSettleJob?.cancel()
                overviewGestureProgress = progress.coerceIn(0f, 1f)
            },
            onOverviewGestureStarted = { overviewGestureSettleJob?.cancel() },
            onOverviewGestureCancelled = settleOverviewGesture,
            onReload = controller::reload,
            onStop = controller::stopLoading,
            onNewTab = openNewTabAndEdit,
            onToggleIncognito = {
                if (controller.setBlankTabIncognito(enabled = !selectedTab.isIncognito)) {
                    rootView.performConfirmHaptic()
                }
            },
            blankTabModeProgress = blankTabModeProgress,
            onIncognitoControlCenterChanged = { blankTabModeRevealOrigin = it },
            isFavorite = controller.isSelectedTabFavorite,
            onToggleFavorite = {
                controller.toggleFavorite()?.let { mutation ->
                    rootView.performConfirmHaptic()
                    favoriteFeedbackId++
                    favoriteFeedbackEvent = FavoriteFeedbackEvent(
                        id = favoriteFeedbackId,
                        added = mutation.added,
                    )
                    favoriteSnackbarJob?.cancel()
                    favoriteSnackbarJob = backAnimationScope.launch {
                        val result = favoriteSnackbarHostState.showSnackbar(
                            message = if (mutation.added) {
                                favoriteAddedMessage
                            } else {
                                favoriteRemovedMessage
                            },
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            controller.undoFavorite(mutation)
                        }
                    }
                }
            },
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
            onOpenCandyTrail = {
                candyTrailSourceBounds = null
                candyTrailTabId = selectedTab.id
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            onAddSiteCapsule = {
                editingCapsuleId = null
                capsuleEditorVisible = true
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            startPageSearchTransform = startPageSearchTransform.takeIf {
                startPageSearchTransformEnabled
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(if (commandFeedback != null) 30f else 0f)
                .onGloballyPositioned { coordinates ->
                    bottomBarTopPx = coordinates.boundsInRoot().top
                },
        )

        if (startPageSearchTransformEnabled) {
            StartPageSearchTransformOverlay(
                state = startPageSearchTransform,
                editing = addressEditorVisible,
                incognito = selectedTab.isIncognito,
                modifier = Modifier.zIndex(1f),
            )
        }

        TabOverview(
            controller = controller,
            visible = tabOverviewVisible,
            bottomBarTopPx = bottomBarTopPx,
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
                        previewTopInsetPx = controller.previewTopInsetPx(target.id),
                    )
                    controller.selectTab(target.id)
                } else {
                    controller.selectTab(it)
                }
            },
            onNewTab = {
                val previousTabId = controller.selectedTabId
                openNewTabAndEdit(it)
                if (controller.selectedTabId != previousTabId) tabOverviewVisible = false
            },
            candyTrailTabId = candyTrailTabId,
            candyTrailSourceBounds = candyTrailSourceBounds,
            candyTrailBackProgress = candyTrailBackProgress.value,
            candyTrailBackEdgeSign = candyTrailBackEdgeSign,
            onOpenCandyTrail = { tabId, bounds ->
                candyTrailSourceBounds = bounds
                candyTrailTabId = tabId
            },
            onCloseCandyTrail = {
                candyTrailTabId = null
                candyTrailSourceBounds = null
            },
        )

        NewTabCreationMotionHost(controller = newTabCreationMotion)

        AnimatedVisibility(
            visible = settingsVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
        ) {
            SettingsScreen(
                blockerSettings = controller.blockerSettings,
                inactiveTabLifetime = controller.inactiveTabLifetime,
                searchEngine = controller.searchEngine,
                tabOverviewMode = controller.tabOverviewMode,
                dismissResistancePercent = controller.dismissResistancePercent,
                blockedCount = selectedTab.blockedCount,
                isDefaultBrowser = controller.isDefaultBrowser,
                siteCapsules = controller.siteCapsules,
                onBlockerSettingsChanged = controller::updateBlockerSettings,
                onInactiveTabLifetimeChanged = controller::updateInactiveTabLifetime,
                onSearchEngineChanged = controller::updateSearchEngine,
                onTabOverviewModeChanged = controller::updateTabOverviewMode,
                onDismissResistancePercentChanged = controller::updateDismissResistancePercent,
                onRequestDefaultBrowser = controller::requestDefaultBrowserRole,
                onPrivacyXRay = {
                    privacyXRayTabId = selectedTab.id
                    rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onEditCapsule = { capsule ->
                    editingCapsuleId = capsule.id
                    capsuleEditorVisible = true
                },
                onDeleteCapsule = { capsule -> pendingCapsuleDelete = capsule },
                onFilterStudio = {
                    filterStudioSelectedRuleId = null
                    filterStudioVisible = true
                },
                onOpenLegalUrl = { url ->
                    settingsVisible = false
                    controller.openUrl(url, inNewTab = true)
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
                    onRuleAction = { domain, action, siteScoped ->
                        val rule = controller.addFilterRuleFromXRay(
                            tabId = tabId,
                            requestHost = domain,
                            action = action,
                            siteScoped = siteScoped,
                        )
                        if (rule != null) {
                            filterStudioSelectedRuleId = rule.id
                            privacyXRayTabId = null
                            filterStudioVisible = true
                        }
                    },
                    onOpenStudio = { ruleId ->
                        filterStudioSelectedRuleId = ruleId
                        privacyXRayTabId = null
                        filterStudioVisible = true
                    },
                    onDismiss = { privacyXRayTabId = null },
                )
            }
        }

        if (filterStudioVisible) {
            FilterStudioScreen(
                rules = controller.filterRulesFor(controller.selectedTabId),
                subscriptionRules = controller.filterSubscriptionRulesFor(
                    controller.selectedTabId,
                ),
                isIncognito = controller.selectedTab.isIncognito,
                profiles = controller.profiles,
                currentProfileId = controller.selectedTab.profileId,
                currentUrl = controller.filterStudioTestUrl(controller.selectedTabId),
                recentDomain = controller.privacySnapshot(controller.selectedTabId)
                    .domains.firstOrNull()?.host,
                selectedRuleId = filterStudioSelectedRuleId,
                onTest = { controller.testFilterRule(controller.selectedTabId, it) },
                onAdd = controller::addFilterRule,
                onUpdate = controller::updateFilterRule,
                onToggle = controller::setFilterRuleActive,
                onDelete = controller::deleteFilterRule,
                onParseImport = controller::importFilterRules,
                onApplyImport = controller::applyFilterImport,
                onApplySubscription = controller::applyFilterSubscription,
                onExport = controller::exportFilterRules,
                onDismiss = {
                    filterStudioVisible = false
                    filterStudioSelectedRuleId = null
                },
            )
        }

        favoriteFeedbackEvent?.let { event ->
            FavoriteToggleFeedback(
                event = event,
                onFinished = { completedId ->
                    if (favoriteFeedbackEvent?.id == completedId) favoriteFeedbackEvent = null
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 104.dp)
                    .zIndex(5f),
            )
        }

        SnackbarHost(
            hostState = favoriteSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 84.dp)
                .zIndex(6f),
        )

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

    pendingCommand?.let { pending ->
        val isCookieCommand = pending.command.confirmation == CommandConfirmation.ClearCookies
        AlertDialog(
            onDismissRequest = {
                pendingCommand = null
                addressFocusNonce++
            },
            title = {
                Text(
                    stringResource(
                        if (isCookieCommand) {
                            R.string.command_cookie_confirm_title
                        } else {
                            R.string.command_duplicates_confirm_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    if (isCookieCommand) {
                        stringResource(
                            when (controller.commandCookieScope) {
                                CommandCookieScope.SharedRegularProfile ->
                                    R.string.command_cookie_confirm_regular
                                CommandCookieScope.IsolatedRegularProfile ->
                                    R.string.command_cookie_confirm_isolated
                                CommandCookieScope.PrivateProfile ->
                                    R.string.command_cookie_confirm_private
                                CommandCookieScope.AllWebViews ->
                                    R.string.command_cookie_confirm_all
                            },
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.command_duplicates_confirm_message,
                            pending.command.duplicateCount,
                            pending.command.duplicateCount,
                        )
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCommand = null
                        runCommand(pending.command)
                    },
                ) {
                    Text(
                        stringResource(
                            if (isCookieCommand) {
                                R.string.action_delete
                            } else {
                                R.string.command_close_duplicates_name
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingCommand = null
                        addressFocusNonce++
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
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

    if (capsuleEditorVisible) {
        SiteCapsuleEditorSheet(
            controller = controller,
            existing = editingCapsuleId?.let { id ->
                controller.siteCapsules.firstOrNull { it.id == id }
            },
            sourceTitle = selectedTab.title.ifBlank { AddressResolver.displayText(selectedTab.url) },
            sourceUrl = selectedTab.url,
            sourceFavicon = if (editingCapsuleId == null) controller.selectedFavicon else null,
            onDismiss = { capsuleEditorVisible = false },
        )
    }

    pendingCapsuleDelete?.let { capsule ->
        AlertDialog(
            onDismissRequest = { pendingCapsuleDelete = null },
            title = { Text(stringResource(R.string.capsule_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (capsule.ownsDedicatedProfile) {
                            R.string.capsule_delete_dedicated_message
                        } else {
                            R.string.capsule_delete_message
                        },
                    ),
                )
            },
            confirmButton = {
                if (capsule.ownsDedicatedProfile) {
                    Button(
                        onClick = {
                            controller.deleteSiteCapsule(capsule.id, true)
                            pendingCapsuleDelete = null
                        },
                    ) { Text(stringResource(R.string.capsule_delete_with_profile)) }
                } else {
                    Button(
                        onClick = {
                            controller.deleteSiteCapsule(capsule.id, false)
                            pendingCapsuleDelete = null
                        },
                    ) { Text(stringResource(R.string.action_delete)) }
                }
            },
            dismissButton = {
                Row {
                    if (capsule.ownsDedicatedProfile) {
                        TextButton(
                            onClick = {
                                controller.deleteSiteCapsule(capsule.id, false)
                                pendingCapsuleDelete = null
                            },
                        ) { Text(stringResource(R.string.capsule_delete_only)) }
                    }
                    TextButton(onClick = { pendingCapsuleDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun BrowserViewport(
    controller: BrowserController,
    selectedTab: BrowserTab,
    dragOffset: MutableFloatState,
    travelDistance: Float,
    rootHeightPx: Float,
    bottomBarTopPx: Float,
    handoff: TabHandoff?,
    handoffAlpha: Float,
    liveFrameTabId: String?,
    tabOverviewVisible: Boolean,
    overviewGestureProgress: Float,
    onLiveFrame: (String) -> Unit,
    onSearch: () -> Unit,
    onReload: () -> Unit,
    onNewTabDestinationBounds: (Rect) -> Unit,
    blankTabModeProgress: Float,
    blankTabModeRevealOrigin: Offset,
    startPageSearchTransform: StartPageSearchTransformState,
    searchEditing: Boolean,
    onRetry: () -> Boolean,
) {
    val density = LocalDensity.current
    val hapticView = LocalView.current
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
    var pageErrorFeedback by remember(selectedTab.id) {
        mutableStateOf(
            PageErrorFeedbackRules.observe(
                current = PageErrorFeedbackState.Hidden(),
                error = selectedTab.error,
                isLoading = selectedTab.isLoading,
            ),
        )
    }
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
            .graphicsLayer {
                val offset = dragOffset.floatValue
                val travelProgress = (offset.absoluteValue / travelDistance).coerceIn(0f, 1f)
                val cardProgress = if (adjacentTab != null) {
                    (4f * travelProgress * (1f - travelProgress)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val overviewProgress = AddressBarOverviewGestureRules.resistedProgress(
                    overviewGestureProgress,
                )
                val scale = (1f - 0.03f * cardProgress) * (1f - 0.055f * overviewProgress)
                translationX = offset
                translationY = -with(density) { (24f * overviewProgress).dp.toPx() }
                scaleX = scale
                scaleY = scale
                val shapeProgress = maxOf(cardProgress, overviewProgress)
                shape = RoundedCornerShape((32f * shapeProgress).dp)
                clip = shapeProgress > 0f
                shadowElevation = with(density) { (8f * shapeProgress).dp.toPx() }
            }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (selectedTab.url != BLANK_URL) {
            ActiveWebView(
                controller = controller,
                visible = !tabOverviewVisible,
                onLiveFrame = onLiveFrame,
                pullRefreshTouchListener = pullRefreshTouchListener,
            )
        }

        AnimatedVisibility(
            visible = selectedTab.url == BLANK_URL,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            NewTabPage(
                favorites = controller.favorites,
                incognito = selectedTab.isIncognito,
                modeProgress = blankTabModeProgress,
                revealOriginInRoot = blankTabModeRevealOrigin,
                onSearch = onSearch,
                onFavorite = controller::submitAddress,
                onDestinationBounds = onNewTabDestinationBounds,
                startPageSearchTransform = startPageSearchTransform,
                searchEditing = searchEditing,
            )
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
            tab = controller.activeTabs.firstOrNull { it.id == currentHandoff.tabId },
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
    tab: BrowserTab?,
    alpha: Float,
    rootHeightPx: Float,
    bottomBarTopPx: Float,
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
    dragOffset: MutableFloatState,
    dragDirection: Int,
    travelDistance: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: Float,
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
            rootHeightPx = rootHeightPx,
            previewTopInsetPx = previewTopInsetPx,
            bottomBarTopPx = bottomBarTopPx,
        )
    }
}

@Composable
private fun FullscreenTabPreviewContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: Float,
) {
    val density = LocalDensity.current
    val previewLayout = TabSwitchPreviewLayoutRules.resolve(
        rootHeightPx = rootHeightPx,
        previewTopInsetPx = previewTopInsetPx,
        bottomBarTopPx = bottomBarTopPx,
    )
    val topInset = with(density) { previewLayout.topInsetPx.toDp() }
    val visibleHeight = with(density) { previewLayout.visibleHeightPx.toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .offset(y = topInset)
                .fillMaxWidth()
                .height(visibleHeight)
                .clipToBounds(),
        ) {
            if (tab.isIncognito) {
                IncognitoTabPlaceholder()
            } else if (preview != null && !preview.isRecycled) {
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

@Composable
private fun NewTabPage(
    favorites: List<FavoriteEntry>,
    incognito: Boolean,
    modeProgress: Float,
    revealOriginInRoot: Offset,
    onSearch: () -> Unit,
    onFavorite: (String) -> Unit,
    onDestinationBounds: (Rect) -> Unit,
    startPageSearchTransform: StartPageSearchTransformState,
    searchEditing: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val boundedProgress = BlankTabModeMorphRules.bounded(modeProgress)
    val regularIconAlpha = BlankTabModeMorphRules.regularIconAlpha(boundedProgress)
    val incognitoIconAlpha = BlankTabModeMorphRules.incognitoIconAlpha(boundedProgress)
    val openSearchDescription = stringResource(R.string.cd_open_search)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blankTabModeBackground(
                progress = boundedProgress,
                revealOriginInRoot = revealOriginInRoot,
                regularCenterColor = colors.primaryContainer,
                incognitoCenterColor = colors.inverseSurface,
                edgeColor = colors.surface,
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
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        onDestinationBounds(coordinates.boundsInRoot())
                        startPageSearchTransform.updateSource(coordinates)
                    }
                    .graphicsLayer {
                        alpha = if (
                            StartPageSearchTransformRules.sourceVisible(
                                editing = searchEditing,
                                progress = startPageSearchTransform.progress.value,
                            )
                        ) {
                            1f
                        } else {
                            0f
                        }
                    }
                    .semantics {
                        contentDescription = openSearchDescription
                    },
                shape = RoundedCornerShape(
                    BlankTabModeMorphRules.heroCornerRadiusDp(boundedProgress).dp,
                ),
                color = lerp(colors.primary, colors.inverseSurface, boundedProgress),
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground_art),
                        contentDescription = null,
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer {
                                alpha = regularIconAlpha
                                scaleX = BlankTabModeMorphRules.iconScale(regularIconAlpha)
                                scaleY = scaleX
                            },
                        tint = Color.Unspecified,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_incognito_filled),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                alpha = incognitoIconAlpha
                                scaleX = BlankTabModeMorphRules.iconScale(incognitoIconAlpha)
                                scaleY = scaleX
                            },
                        tint = colors.inverseOnSurface,
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
                        AnimatedVisibility(
                            visible = favorites.isEmpty(),
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(100)),
                        ) {
                            Text(
                                stringResource(R.string.favorites_empty),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        ExpressiveFavoriteRows(
                            favorites = favorites,
                            onFavorite = onFavorite,
                        )
                    }
                }
            }
        }
    }
}

private enum class AddressBarPresentation {
    Compact,
    Expanded,
    CommandFeedback,
}

@Composable
private fun BrowserBottomBar(
    tab: BrowserTab,
    compact: Boolean,
    editing: Boolean,
    commandFeedback: AddressCommandFeedback?,
    feedbackGesturesEnabled: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    addressFocusNonce: Int,
    onMoveAddressSuggestion: (Int) -> Unit,
    onActivateAddressSuggestion: () -> Unit,
    onDismissEditor: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    onScanQrCode: () -> Unit,
    onExpand: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: (Rect?) -> Unit,
    onToggleIncognito: () -> Unit,
    blankTabModeProgress: Float,
    onIncognitoControlCenterChanged: (Offset) -> Unit,
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
    onOpenCandyTrail: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    startPageSearchTransform: StartPageSearchTransformState?,
    overviewGestureEnabled: Boolean,
    overviewGestureProgress: Float,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val presentation = when {
        commandFeedback != null -> AddressBarPresentation.CommandFeedback
        compact && !editing -> AddressBarPresentation.Compact
        else -> AddressBarPresentation.Expanded
    }
    val tabDragState = rememberDraggableState(onTabDrag)
    val pulseScale = remember { Animatable(1f) }
    val domain = AddressResolver.displayText(tab.url)
    val feedbackText = commandFeedback?.localizedText().orEmpty()
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
    val compactWidth = with(density) {
        textMeasurer.measure(
            text = domain,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        ).size.width.toDp() + 36.dp
    }
    val feedbackWidth = with(density) {
        textMeasurer.measure(
            text = feedbackText,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        ).size.width.toDp() + 64.dp
    }
    val barColor by animateColorAsState(
        targetValue = when (commandFeedback?.tone) {
            AddressCommandFeedbackTone.Confirm -> MaterialTheme.colorScheme.primaryContainer
            AddressCommandFeedbackTone.Reject -> MaterialTheme.colorScheme.errorContainer
            null -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        },
        animationSpec = tween(160),
        label = "Address command feedback color",
    )
    BoxWithConstraints(
        modifier = modifier
            .offset { IntOffset(0, if (keyboardVisible) -imeBottom else 0) }
            .then(if (keyboardVisible) Modifier else Modifier.navigationBarsPadding())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val targetWidth = when (presentation) {
            AddressBarPresentation.Compact -> compactWidth.coerceIn(96.dp, maxWidth)
            AddressBarPresentation.Expanded -> maxWidth
            AddressBarPresentation.CommandFeedback -> feedbackWidth
                .coerceAtLeast(160.dp)
                .coerceAtMost(maxWidth)
        }
        val animatedWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 820f),
            label = "Adressleistenbreite",
        )
        Surface(
            modifier = Modifier
                .width(animatedWidth)
                .graphicsLayer {
                    val overviewProgress = AddressBarOverviewGestureRules.resistedProgress(
                        overviewGestureProgress,
                    )
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                    scaleX *= 1f - 0.04f * overviewProgress
                    scaleY *= 1f - 0.04f * overviewProgress
                    translationY = -with(density) { (14f * overviewProgress).dp.toPx() }
                },
            shape = RoundedCornerShape(30.dp),
            color = barColor,
            tonalElevation = 12.dp,
            shadowElevation = 14.dp,
        ) {
            Box {
                AddressLoadCapsuleFeedback(
                    tabId = tab.id,
                    isLoading = tab.isLoading && commandFeedback == null,
                    progressPercent = tab.progress,
                    modifier = Modifier.matchParentSize(),
                )
                AnimatedContent(
                    targetState = presentation,
                    transitionSpec = {
                        ((fadeIn(tween(90)) + slideInVertically(tween(120)) { it / 3 }) togetherWith
                            (fadeOut(tween(70)) + slideOutVertically(tween(100)) { it / 4 }))
                            .using(SizeTransform(clip = false))
                    },
                    label = "Adressleisteninhalt",
                ) { targetPresentation ->
                    when (targetPresentation) {
                        AddressBarPresentation.Compact -> {
                            Surface(
                                onClick = onExpand,
                                modifier = Modifier
                                    .addressBarVerticalGesture(
                                        enabled = overviewGestureEnabled,
                                        initialProgress = overviewGestureProgress,
                                        onProgress = onOverviewGestureProgress,
                                        onStarted = onOverviewGestureStarted,
                                        onCancelled = onOverviewGestureCancelled,
                                        onSwipeUp = onTabs,
                                    )
                                    .draggable(
                                        state = tabDragState,
                                        orientation = Orientation.Horizontal,
                                        enabled = !editing,
                                        onDragStopped = { velocity -> onTabDragStopped(velocity) },
                                    ),
                                color = Color.Transparent,
                            ) {
                                Text(
                                    text = domain,
                                    modifier = Modifier.padding(
                                        horizontal = 18.dp,
                                        vertical = 11.dp,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        AddressBarPresentation.Expanded -> {
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
                                onMoveAddressSuggestion = onMoveAddressSuggestion,
                                onActivateAddressSuggestion = onActivateAddressSuggestion,
                                onDismissEditor = onDismissEditor,
                                onSubmitAddress = onSubmitAddress,
                                onScanQrCode = onScanQrCode,
                                onTabDrag = onTabDrag,
                                onTabDragStopped = onTabDragStopped,
                                onTabs = onTabs,
                                onReload = onReload,
                                onStop = onStop,
                                onNewTab = onNewTab,
                                onToggleIncognito = onToggleIncognito,
                                blankTabModeProgress = blankTabModeProgress,
                                onIncognitoControlCenterChanged =
                                    onIncognitoControlCenterChanged,
                                isFavorite = isFavorite,
                                onToggleFavorite = onToggleFavorite,
                                onSettings = onSettings,
                                onClearData = onClearData,
                                onPrivacyXRay = onPrivacyXRay,
                                onOpenExternal = onOpenExternal,
                                onSummarizeWithAssistant = onSummarizeWithAssistant,
                                onShare = onShare,
                                onPrint = onPrint,
                                onOpenCandyTrail = onOpenCandyTrail,
                                onAddSiteCapsule = onAddSiteCapsule,
                                startPageSearchTransform = startPageSearchTransform,
                                overviewGestureEnabled = overviewGestureEnabled,
                                overviewGestureProgress = overviewGestureProgress,
                                onOverviewGestureProgress = onOverviewGestureProgress,
                                onOverviewGestureStarted = onOverviewGestureStarted,
                                onOverviewGestureCancelled = onOverviewGestureCancelled,
                            )
                        }
                        AddressBarPresentation.CommandFeedback -> {
                            commandFeedback?.let { feedback ->
                                AddressCommandFeedbackContent(
                                    feedback = feedback,
                                    text = feedbackText,
                                    gesturesEnabled = feedbackGesturesEnabled,
                                    onAddress = if (compact) onExpand else onAddress,
                                    onTabDrag = onTabDrag,
                                    onTabDragStopped = onTabDragStopped,
                                    onTabs = onTabs,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(editing, tab.id, addressFocusNonce, commandFeedback) {
        if (editing && commandFeedback == null) {
            delay(40)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
}

@Composable
private fun AddressCommandFeedbackContent(
    feedback: AddressCommandFeedback,
    text: String,
    gesturesEnabled: Boolean,
    onAddress: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
) {
    val tabDragState = rememberDraggableState(onTabDrag)
    val contentColor = when (feedback.tone) {
        AddressCommandFeedbackTone.Confirm -> MaterialTheme.colorScheme.onPrimaryContainer
        AddressCommandFeedbackTone.Reject -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("address_command_feedback")
            .clickable(enabled = gesturesEnabled, onClick = onAddress)
            .addressBarVerticalGesture(
                enabled = gesturesEnabled,
                onSwipeUp = onTabs,
            )
            .draggable(
                state = tabDragState,
                orientation = Orientation.Horizontal,
                enabled = gesturesEnabled,
                onDragStopped = { velocity -> onTabDragStopped(velocity) },
            )
            .semantics(mergeDescendants = true) {
                liveRegion = if (feedback.tone == AddressCommandFeedbackTone.Reject) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (feedback.tone == AddressCommandFeedbackTone.Confirm) {
                Icons.Default.Check
            } else {
                Icons.Default.Close
            },
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AddressCommandFeedback.localizedText(): String = when (message) {
    AddressCommandFeedbackMessage.CacheCleared ->
        stringResource(R.string.command_feedback_cache_cleared)
    AddressCommandFeedbackMessage.CookiesCleared ->
        stringResource(R.string.command_feedback_cookies_cleared)
    AddressCommandFeedbackMessage.Reloaded ->
        stringResource(R.string.command_feedback_reloaded)
    AddressCommandFeedbackMessage.LoadingStopped ->
        stringResource(R.string.command_feedback_loading_stopped)
    AddressCommandFeedbackMessage.TabPinned ->
        stringResource(R.string.command_feedback_tab_pinned)
    AddressCommandFeedbackMessage.TabUnpinned ->
        stringResource(R.string.command_feedback_tab_unpinned)
    AddressCommandFeedbackMessage.DuplicateTabsClosed -> pluralStringResource(
        R.plurals.command_feedback_duplicates_closed,
        count,
        count,
    )
    AddressCommandFeedbackMessage.TabMoved -> stringResource(
        R.string.command_feedback_tab_moved,
        targetProfileLabel.orEmpty(),
    )
    AddressCommandFeedbackMessage.ProfileSwitched -> stringResource(
        R.string.command_feedback_profile_switched,
        targetProfileLabel.orEmpty(),
    )
    AddressCommandFeedbackMessage.RegularTabCreated ->
        stringResource(R.string.command_feedback_regular_tab_created)
    AddressCommandFeedbackMessage.IncognitoTabCreated ->
        stringResource(R.string.command_feedback_incognito_tab_created)
    AddressCommandFeedbackMessage.SettingsOpened ->
        stringResource(R.string.command_feedback_settings_opened)
    AddressCommandFeedbackMessage.Rejected ->
        stringResource(R.string.command_feedback_rejected)
}

@Composable
internal fun Modifier.addressBarVerticalGesture(
    enabled: Boolean = true,
    initialProgress: Float = 0f,
    onProgress: (Float) -> Unit = {},
    onStarted: () -> Unit = {},
    onCancelled: () -> Unit = {},
    onSwipeUp: () -> Unit,
): Modifier {
    val currentInitialProgress by rememberUpdatedState(initialProgress)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnStarted by rememberUpdatedState(onStarted)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val gestureView = LocalView.current
    if (!enabled) return this
    return pointerInput(enabled) {
        val threshold = AddressBarGestureRules.OPEN_TABS_THRESHOLD_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            currentOnStarted()
            var lastY = down.position.y
            var state = AddressBarOverviewGestureRules.stateForProgress(
                progress = currentInitialProgress,
                threshold = threshold,
            )
            var committed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val update = AddressBarOverviewGestureRules.update(
                        state = state,
                        deltaY = change.position.y - lastY,
                        threshold = threshold,
                    )
                    state = update.state
                    lastY = change.position.y
                    currentOnProgress(update.progress)
                    if (update.shouldCommit) {
                        committed = true
                        change.consume()
                        gestureView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        currentOnSwipeUp()
                        break
                    }
                    if (!change.pressed) break
                }
            } finally {
                if (!committed) currentOnCancelled()
            }
        }
    }
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
    onMoveAddressSuggestion: (Int) -> Unit,
    onActivateAddressSuggestion: () -> Unit,
    onDismissEditor: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    onScanQrCode: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: (Rect?) -> Unit,
    onToggleIncognito: () -> Unit,
    blankTabModeProgress: Float,
    onIncognitoControlCenterChanged: (Offset) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSettings: () -> Unit,
    onClearData: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onOpenExternal: () -> Unit,
    onSummarizeWithAssistant: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    startPageSearchTransform: StartPageSearchTransformState?,
    overviewGestureEnabled: Boolean,
    overviewGestureProgress: Float,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
) {
    val tabDragState = rememberDraggableState(onTabDrag)
    var newTabButtonBounds by remember { mutableStateOf<Rect?>(null) }
    Column {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (startPageSearchTransform == null) {
                            Modifier
                        } else {
                            Modifier
                                .onGloballyPositioned { coordinates ->
                                    if (
                                        StartPageSearchTransformRules.shouldUpdateTargetBounds(
                                            editing = editing,
                                            progress = startPageSearchTransform.progress.value,
                                        )
                                    ) {
                                        startPageSearchTransform.updateTarget(coordinates)
                                    }
                                }
                                .graphicsLayer {
                                    alpha = StartPageSearchTransformRules.targetContainerAlpha(
                                        editing = editing,
                                        progress = startPageSearchTransform.progress.value,
                                    )
                                }
                        },
                    )
                    .addressBarVerticalGesture(
                        enabled = !editing && overviewGestureEnabled,
                        initialProgress = overviewGestureProgress,
                        onProgress = onOverviewGestureProgress,
                        onStarted = onOverviewGestureStarted,
                        onCancelled = onOverviewGestureCancelled,
                        onSwipeUp = onTabs,
                    )
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
                                .onPreviewKeyEvent { event ->
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                onMoveAddressSuggestion(1)
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                onMoveAddressSuggestion(-1)
                                            }
                                            true
                                        }
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.DirectionCenter,
                                        -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                onActivateAddressSuggestion()
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
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
                            PrivacyXRayBadge(
                                blockedCount = tab.blockedCount,
                                onClick = onPrivacyXRay,
                                modifier = Modifier
                                    .zIndex(2f)
                                    .padding(end = 2.dp),
                                tabId = tab.id,
                            )
                        }
                    }
                }
            }
            if (editing && tab.url == BLANK_URL) {
                Spacer(Modifier.width(8.dp))
                BlankTabIncognitoModeButton(
                    enabled = tab.isIncognito,
                    progress = blankTabModeProgress,
                    onCenterChanged = onIncognitoControlCenterChanged,
                    onClick = onToggleIncognito,
                )
            } else {
                IconButton(
                    onClick = { onNewTab(newTabButtonBounds) },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        newTabButtonBounds = coordinates.boundsInRoot()
                    },
                ) {
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
                        text = { Text(stringResource(R.string.action_open_candy_trail)) },
                        enabled = tab.url != BLANK_URL,
                        onClick = {
                            onMenuExpandedChange(false)
                            onOpenCandyTrail()
                        },
                        leadingIcon = {
                            Text(
                                "⌘",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.capsule_add_action))
                                if (tab.isIncognito) {
                                    Text(
                                        stringResource(R.string.capsule_unavailable_private),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else if (tab.url == BLANK_URL ||
                                    (!tab.url.startsWith("https://") &&
                                        !tab.url.startsWith("http://"))
                                ) {
                                    Text(
                                        stringResource(R.string.capsule_unavailable_web_only),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        enabled = tab.url != BLANK_URL &&
                            !tab.isIncognito &&
                            (tab.url.startsWith("https://") || tab.url.startsWith("http://")),
                        onClick = {
                            onMenuExpandedChange(false)
                            onAddSiteCapsule()
                        },
                        leadingIcon = {
                            Text("◉", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_tab_title)) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onNewTab(null)
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
                            ExpressiveFavoriteStar(
                                filled = isFavorite,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
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
private fun AddressEditorBackdrop(
    showStartContent: Boolean,
    modeProgress: Float,
    revealOriginInRoot: Offset,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val boundedProgress = BlankTabModeMorphRules.bounded(modeProgress)
    val backgroundModifier = if (showStartContent) {
        Modifier.blankTabModeBackground(
            progress = boundedProgress,
            revealOriginInRoot = revealOriginInRoot,
            regularCenterColor = colors.primaryContainer,
            incognitoCenterColor = colors.inverseSurface,
            edgeColor = colors.surface,
        )
    } else {
        Modifier.background(
            Brush.linearGradient(
                listOf(
                    colors.scrim.copy(alpha = 0.08f),
                    colors.scrim.copy(alpha = 0.08f),
                ),
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .clickable(onClick = onDismiss)
            .statusBarsPadding(),
    ) { }
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
    suggestions: List<AddressSuggestionItem>,
    highlightedIndex: Int,
    onHighlight: (Int) -> Unit,
    onSelect: (AddressSuggestionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedIndex, suggestions.map(AddressSuggestionItem::stableId)) {
        if (highlightedIndex in suggestions.indices) {
            listState.animateScrollToItem(highlightedIndex)
        }
    }
    Surface(
        modifier = modifier
            .imePadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 92.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 12.dp,
        shadowElevation = 12.dp,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = 320.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(
                items = suggestions,
                key = { _, suggestion -> suggestion.stableId },
            ) { index, suggestion ->
                when (suggestion) {
                    is AddressSuggestionItem.Navigation -> NavigationSuggestionRow(
                        suggestion = suggestion.suggestion,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                    )
                    is AddressSuggestionItem.Command -> CommandSuggestionRow(
                        suggestion = suggestion.suggestion,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationSuggestionRow(
    suggestion: AddressSuggestion,
    highlighted: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
) {
    val switchesToOpenTab = suggestion.openTabId != null
    val containerColor = when {
        highlighted -> MaterialTheme.colorScheme.tertiaryContainer
        switchesToOpenTab -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        highlighted -> MaterialTheme.colorScheme.onTertiaryContainer
        switchesToOpenTab -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { selected = highlighted }
            .clickable(role = Role.Button) {
                onHighlight()
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (switchesToOpenTab) R.drawable.ic_switch_to_tab else R.drawable.ic_history,
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    suggestion.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Text(
                    AddressResolver.displayText(suggestion.url),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.76f),
                )
            }
            if (switchesToOpenTab) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_switch_to_tab),
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CommandSuggestionRow(
    suggestion: CommandSuggestion,
    highlighted: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (highlighted) 1.01f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "Command focus",
    )
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) { selected = highlighted }
            .clickable(role = Role.Button) {
                onHighlight()
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = contentColor.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CommandIcon(suggestion.command.kind, contentColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    suggestion.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    suggestion.effect,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
            suggestion.command.targetProfileLabel?.let { profile ->
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = contentColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = stringResource(R.string.command_target_profile, profile),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandIcon(kind: BrowserCommandKind, tint: Color) {
    val modifier = Modifier.size(22.dp)
    when (kind) {
        BrowserCommandKind.ClearCacheAndReload,
        BrowserCommandKind.Reload,
        -> Icon(Icons.Default.Refresh, contentDescription = null, modifier = modifier, tint = tint)
        BrowserCommandKind.ClearCookiesAndReload -> Icon(
            painterResource(R.drawable.ic_delete_outline),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.StopLoading ->
            Icon(Icons.Default.Close, contentDescription = null, modifier = modifier, tint = tint)
        BrowserCommandKind.PinTab,
        BrowserCommandKind.UnpinTab,
        -> Icon(
            painterResource(R.drawable.ic_push_pin),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.CloseDuplicateTabs -> Icon(
            painterResource(R.drawable.ic_content_copy),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.MoveTabToProfile,
        BrowserCommandKind.SwitchProfile,
        -> Icon(
            painterResource(R.drawable.ic_switch_to_tab),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.NewRegularTab ->
            Icon(Icons.Default.Add, contentDescription = null, modifier = modifier, tint = tint)
        BrowserCommandKind.NewIncognitoTab -> Icon(
            painterResource(R.drawable.ic_incognito_outline),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
        BrowserCommandKind.OpenSettings -> Icon(
            painterResource(R.drawable.ic_settings),
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
    }
}

@Composable
private fun TabOverview(
    controller: BrowserController,
    visible: Boolean,
    bottomBarTopPx: Float,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onNewTab: (Rect?) -> Unit,
    candyTrailTabId: String?,
    candyTrailSourceBounds: Rect?,
    candyTrailBackProgress: Float,
    candyTrailBackEdgeSign: Int,
    onOpenCandyTrail: (String, Rect?) -> Unit,
    onCloseCandyTrail: () -> Unit,
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
    val initialTabId = remember(visible) { controller.selectedTabId }
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
    val initialPreviewTopInsetPx = remember(initialTabId, visible) {
        controller.previewTopInsetPx(initialTabId)
    }
    val heroProgress = remember { Animatable(0f) }
    val overviewScope = rememberCoroutineScope()
    var heroTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var heroTargetMode by remember { mutableStateOf<TabOverviewMode?>(null) }
    var heroTargetTabId by remember { mutableStateOf<String?>(null) }
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
    var profileIsolationChange by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var emojiPickerTargetId by remember { mutableStateOf<String?>(null) }
    var movingTabId by remember { mutableStateOf<String?>(null) }
    var profileSwitching by remember { mutableStateOf(false) }
    var reorderAnimation by remember { mutableStateOf<TabReorderAnimation?>(null) }
    var reorderLayoutReady by remember { mutableStateOf(false) }
    var newTabButtonBounds by remember { mutableStateOf<Rect?>(null) }
    val reorderProgress = remember { Animatable(1f) }
    val moveProgress = remember { Animatable(0f) }
    val tabCardBounds = remember { mutableStateMapOf<String, Rect>() }
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

    fun startExitHero(
        tab: BrowserTab,
        bounds: Rect,
        cornerRadius: Dp = 28.dp,
    ) {
        if (
            dismissingTabId != null ||
            movingTabId != null ||
            exitHero != null ||
            reorderAnimation != null ||
            tabActionsTabId != null
        ) {
            return
        }
        val mode = controller.tabOverviewMode
        val preview = controller.previews[tab.id]
            ?.takeIf { !tab.isIncognito && !it.isRecycled }
        exitHero = TabExitHero(
            tabId = tab.id,
            preview = preview,
            startBounds = bounds,
            isIncognito = tab.isIncognito,
            startCornerRadius = cornerRadius,
            previewTopInsetPx = controller.previewTopInsetPx(tab.id),
            mode = mode,
        )
        overviewScope.launch {
            try {
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
            } finally {
                exitHero = null
            }
        }
    }

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

    LaunchedEffect(pagerState.interactionSource, controller.tabOverviewMode, visible) {
        if (controller.tabOverviewMode != TabOverviewMode.Hero || !visible) {
            return@LaunchedEffect
        }
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
    LaunchedEffect(pagerState, controller.tabOverviewMode, visible) {
        if (controller.tabOverviewMode != TabOverviewMode.Hero || !visible) {
            return@LaunchedEffect
        }
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
        controller.tabOverviewMode,
        visible,
    ) {
        if (
            controller.tabOverviewMode != TabOverviewMode.Hero ||
            !visible ||
            dismissingTabId != null ||
            profileSwitching
        ) {
            return@LaunchedEffect
        }
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
        if (!visible) {
            profileSwitchProgress.snapTo(1f)
            return@LaunchedEffect
        }
        if (profileSwitching) return@LaunchedEffect
        profileSwitchProgress.snapTo(0f)
        val selectedIndex = controller.activeTabs
            .indexOfFirst { it.id == controller.selectedTabId }
            .coerceAtLeast(0)
        if (
            controller.tabOverviewMode == TabOverviewMode.Hero &&
            pagerState.currentPage != selectedIndex
        ) {
            pagerState.scrollToPage(selectedIndex)
        }
        profileSwitchProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        )
    }

    val layerVisible = CandyTrailLayerRules.isVisible(visible, candyTrailTabId)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (layerVisible) 10f else -1f)
            .graphicsLayer { alpha = if (layerVisible) 1f else 0f },
    ) {
        val density = LocalDensity.current
        val rootWidthPx = with(density) { maxWidth.toPx() }
        val rootHeightPx = with(density) { maxHeight.toPx() }
        val heroTarget = heroTargetBounds?.takeIf {
            heroTargetMode == controller.tabOverviewMode && heroTargetTabId == initialTabId
        }
        val isExiting = exitHero != null
        val tabCardWidth = (maxWidth * 0.68f).coerceIn(244.dp, 292.dp)
        val pageSlotWidth = tabCardWidth + 18.dp
        val pageSlotWidthPx = with(density) { pageSlotWidth.toPx() }
        val pageHorizontalPadding = ((maxWidth - pageSlotWidth) / 2).coerceAtLeast(0.dp)

        LaunchedEffect(visible, controller.tabOverviewMode, initialTabId) {
            if (!visible) {
                heroProgress.snapTo(0f)
                exitHeroProgress.snapTo(0f)
                heroStarted = false
                heroCompleted = false
                heroVisible = true
                exitHero = null
                return@LaunchedEffect
            }

            heroProgress.snapTo(0f)
            heroStarted = false
            heroCompleted = false
            heroVisible = true

            var waitMillis = 0L
            while (
                waitMillis < 250L &&
                !TabOverviewHeroRules.canStart(
                    heroTargetBounds != null &&
                        heroTargetMode == controller.tabOverviewMode &&
                        heroTargetTabId == initialTabId,
                )
            ) {
                delay(16)
                waitMillis += 16
            }

            val hasStableTarget = TabOverviewHeroRules.canStart(
                heroTargetBounds != null &&
                    heroTargetMode == controller.tabOverviewMode &&
                    heroTargetTabId == initialTabId,
            )
            heroStarted = true
            if (hasStableTarget) {
                heroProgress.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            } else {
                heroProgress.snapTo(1f)
            }
            heroCompleted = true
            withFrameNanos { }
            heroVisible = false
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
                .navigationBarsPadding()
                .then(
                    if (candyTrailTabId != null) Modifier.clearAndSetSemantics { }
                    else Modifier,
                ),
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
                    profileIsolationChange == null &&
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
                            if (
                                controller.tabOverviewMode == TabOverviewMode.Hero &&
                                pagerState.currentPage != 0
                            ) {
                                pagerState.scrollToPage(0)
                            }
                            if (controller.selectProfile(profileId)) {
                                val selectedIndex = controller.activeTabs
                                    .indexOfFirst { it.id == controller.selectedTabId }
                                    .coerceAtLeast(0)
                                if (
                                    controller.tabOverviewMode == TabOverviewMode.Hero &&
                                    pagerState.currentPage != selectedIndex
                                ) {
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
            when (controller.tabOverviewMode) {
                TabOverviewMode.Hero -> HorizontalPager(
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
                                    tabCardBounds[tab.id] = bounds
                                    if (isInitialCard) {
                                        heroTargetBounds = bounds
                                        heroTargetMode = TabOverviewMode.Hero
                                        heroTargetTabId = tab.id
                                    }
                                },
                            onClick = {
                                val bounds = cardBounds
                                if (bounds == null) {
                                    onSelect(tab.id)
                                    onClose()
                                    return@TabCard
                                }
                                startExitHero(tab, bounds)
                            },
                            onLongClick = {
                                if (
                                    dismissingTabId == null &&
                                    movingTabId == null &&
                                    exitHero == null &&
                                    reorderAnimation == null
                                ) {
                                    onOpenCandyTrail(tab.id, cardBounds)
                                }
                            },
                        )
                    }
                }
                }
                TabOverviewMode.Grid -> CompactTabGrid(
                    tabs = controller.activeTabs,
                    selectedTabId = controller.selectedTabId,
                    initialTabId = initialTabId,
                    previews = controller.previews,
                    favicons = controller.favicons,
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = heroVisible,
                    exitHeroTabId = exitHero?.tabId,
                    dismissResistanceFraction = controller.dismissResistancePercent / 100f,
                    interactionsEnabled = dismissingTabId == null &&
                        movingTabId == null &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        tabActionsTabId == null,
                    onPreviewBounds = { tab, bounds ->
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.Grid
                            heroTargetTabId = tab.id
                        }
                    },
                    onSelect = { tab, bounds -> startExitHero(tab, bounds, 22.dp) },
                    onCloseTab = { tab ->
                        if (TabDeletionRules.canDelete(tab)) {
                            rootView.performConfirmHaptic()
                            controller.closeTab(tab.id)
                        }
                    },
                    onSwipeDismissStart = { tab ->
                        if (dismissingTabId == null) {
                            dismissingTabId = tab.id
                            true
                        } else {
                            false
                        }
                    },
                    onSwipeDismissEnd = { tab ->
                        if (dismissingTabId == tab.id) {
                            dismissingTabId = null
                        }
                    },
                    onSwipeDismiss = { tab ->
                        if (TabDeletionRules.canDelete(tab)) {
                            controller.closeTab(tab.id)
                        }
                    },
                    onLongClick = { tab, bounds ->
                        tabCardBounds[tab.id] = bounds
                        onOpenCandyTrail(tab.id, bounds)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val progress = profileSwitchProgress.value
                            alpha = progress
                            translationY = (1f - progress) * 14f
                        },
                )
                TabOverviewMode.List -> CompactTabList(
                    tabs = controller.activeTabs,
                    selectedTabId = controller.selectedTabId,
                    initialTabId = initialTabId,
                    favicons = controller.favicons,
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = heroVisible,
                    exitHeroTabId = exitHero?.tabId,
                    interactionsEnabled = dismissingTabId == null &&
                        movingTabId == null &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        tabActionsTabId == null,
                    onRowBounds = { tab, bounds ->
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.List
                            heroTargetTabId = tab.id
                        }
                    },
                    onSelect = { tab, bounds -> startExitHero(tab, bounds, 22.dp) },
                    onCloseTab = { tab ->
                        if (TabDeletionRules.canDelete(tab)) {
                            rootView.performConfirmHaptic()
                            controller.closeTab(tab.id)
                        }
                    },
                    onLongClick = { tab, bounds ->
                        tabCardBounds[tab.id] = bounds
                        onOpenCandyTrail(tab.id, bounds)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val progress = profileSwitchProgress.value
                            alpha = progress
                            translationY = (1f - progress) * 14f
                        },
                )
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
                        onNewTab(newTabButtonBounds)
                    },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        newTabButtonBounds = coordinates.boundsInRoot()
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
                targetCornerRadius = if (controller.tabOverviewMode == TabOverviewMode.Hero) {
                    28.dp
                } else {
                    22.dp
                },
                targetFraction = { heroProgress.value },
            ) {
                when (controller.tabOverviewMode) {
                    TabOverviewMode.List -> TabListHeroContent(
                        tab = initialTab,
                        preview = heroPreview,
                        favicon = heroFavicon,
                        targetBounds = heroTarget,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = initialPreviewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { heroProgress.value },
                    )
                    TabOverviewMode.Hero -> TabCoverflowHeroContent(
                        tab = initialTab,
                        preview = heroPreview,
                        favicon = heroFavicon,
                        targetBounds = heroTarget,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = initialPreviewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { heroProgress.value },
                    )
                    TabOverviewMode.Grid -> FullscreenTabPreviewContent(
                        tab = initialTab,
                        preview = heroPreview,
                        favicon = heroFavicon,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = initialPreviewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                    )
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
                targetCornerRadius = hero.startCornerRadius,
                targetFraction = { 1f - exitHeroProgress.value },
                modifier = Modifier.zIndex(20f),
            ) {
                val preview = hero.preview
                val heroTab = controller.activeTabs.firstOrNull { it.id == hero.tabId }
                if (hero.mode == TabOverviewMode.List && heroTab != null) {
                    TabListHeroContent(
                        tab = heroTab,
                        preview = preview,
                        favicon = controller.favicons[hero.tabId],
                        targetBounds = hero.startBounds,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { 1f - exitHeroProgress.value },
                    )
                } else if (hero.mode == TabOverviewMode.Hero && heroTab != null) {
                    TabCoverflowHeroContent(
                        tab = heroTab,
                        preview = preview,
                        favicon = controller.favicons[hero.tabId],
                        targetBounds = hero.startBounds,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { 1f - exitHeroProgress.value },
                    )
                } else if (preview != null && !preview.isRecycled && heroTab != null) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = preview,
                        favicon = controller.favicons[hero.tabId],
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                    )
                } else if (hero.isIncognito && heroTab != null) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = null,
                        favicon = null,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
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

        val candyTrailTab = candyTrailTabId?.let { tabId ->
            controller.activeTabs.firstOrNull { it.id == tabId }
        }
        if (candyTrailTab != null) {
            val candyTrail = controller.candyTrail(candyTrailTab.id)
            CandyTrailScreen(
                tab = candyTrailTab,
                trail = candyTrail,
                favicon = controller.favicons[candyTrailTab.id],
                forkFavicons = candyTrail.forks.mapNotNull { fork ->
                    val destinationId = fork.destinationTabId ?: return@mapNotNull null
                    controller.favicons[destinationId]?.let { destinationId to it }
                }.toMap(),
                sourceBounds = candyTrailSourceBounds,
                predictiveBackProgress = candyTrailBackProgress,
                predictiveBackEdgeSign = candyTrailBackEdgeSign,
                onOpenTabActions = { tabActionsTabId = candyTrailTab.id },
                onSelectNode = { nodeId ->
                    controller.navigateToCandyTrailNode(candyTrailTab.id, nodeId)
                },
                onNodeSelectionFinished = {
                    onCloseCandyTrail()
                    val bounds = tabCardBounds[candyTrailTab.id] ?: candyTrailSourceBounds
                    if (bounds == null) {
                        onSelect(candyTrailTab.id)
                        onClose()
                    } else {
                        val preview = controller.previews[candyTrailTab.id]
                            ?.takeIf { !candyTrailTab.isIncognito && !it.isRecycled }
                        exitHero = TabExitHero(
                            candyTrailTab.id,
                            preview,
                            bounds,
                            candyTrailTab.isIncognito,
                            previewTopInsetPx = controller.previewTopInsetPx(candyTrailTab.id),
                            mode = controller.tabOverviewMode,
                        )
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
                            onSelect(candyTrailTab.id)
                            onClose()
                        }
                    }
                },
                onForkNode = { nodeId ->
                    controller.forkCandyTrailNode(candyTrailTab.id, nodeId)
                },
                onForkCreationFinished = { destinationId ->
                    onCloseCandyTrail()
                    onSelect(destinationId)
                    onClose()
                },
                onSelectFork = { forkId ->
                    controller.activateCandyTrailFork(candyTrailTab.id, forkId)
                },
                onForkSelectionFinished = { destinationId ->
                    onCloseCandyTrail()
                    onSelect(destinationId)
                    onClose()
                },
                onDismiss = onCloseCandyTrail,
            )
        }

        val actionTab = tabActionsTabId?.let { tabId ->
            controller.activeTabs.firstOrNull { it.id == tabId }
        }
        TabActionsSheet(
            tab = actionTab,
            profiles = controller.profiles,
            canFork = actionTab?.let { tab ->
                tab.url != BLANK_URL && controller.candyTrail(tab.id).currentNodeId != null
            } == true,
            onFork = {
                val target = actionTab ?: return@TabActionsSheet
                val currentNodeId = controller.candyTrail(target.id).currentNodeId
                    ?: return@TabActionsSheet
                tabActionsTabId = null
                val destinationId = controller.forkCandyTrailNode(target.id, currentNodeId)
                if (destinationId != null) {
                    rootView.performConfirmHaptic()
                    onCloseCandyTrail()
                    onSelect(destinationId)
                    onClose()
                }
            },
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
                    if (controller.tabOverviewMode != TabOverviewMode.Hero) {
                        if (controller.setTabPinned(target.id, !target.isPinned)) {
                            rootView.performConfirmHaptic()
                        }
                        return@launch
                    }
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
            isolationSupported = controller.isProfileIsolationSupported,
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
            onIsolationChange = { enabled ->
                val target = actionProfile ?: return@ProfileActionsSheet
                profileActionsProfileId = null
                profileIsolationChange = target.id to enabled
            },
            onDismiss = { profileActionsProfileId = null },
        )

        profileIsolationChange?.let { (profileId, enabled) ->
            AlertDialog(
                onDismissRequest = { profileIsolationChange = null },
                title = { Text(stringResource(R.string.profile_isolation_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            if (enabled) R.string.profile_isolation_enable_message
                            else R.string.profile_isolation_disable_message,
                        ),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (controller.setProfileIsolation(profileId, enabled)) {
                                rootView.performConfirmHaptic()
                            }
                            profileIsolationChange = null
                        },
                    ) {
                        Text(stringResource(R.string.action_switch_storage))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileIsolationChange = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        val emojiPickerTarget = emojiPickerTargetId
        EmojiPickerSheet(
            visible = emojiPickerTarget != null,
            creatingProfile = emojiPickerTarget == NEW_PROFILE_TARGET,
            isolationSupported = controller.isProfileIsolationSupported,
            selectedEmoji = controller.profiles
                .firstOrNull { it.id == emojiPickerTarget }
                ?.emoji,
            onCreate = { emoji, isolationEnabled ->
                if (emojiPickerTarget != NEW_PROFILE_TARGET) return@EmojiPickerSheet
                val changed = controller.createProfile(emoji, isolationEnabled) != null
                if (changed) {
                    emojiPickerTargetId = null
                    rootView.performConfirmHaptic()
                }
            },
            onSelect = { emoji ->
                val target = emojiPickerTarget ?: return@EmojiPickerSheet
                if (target == NEW_PROFILE_TARGET) return@EmojiPickerSheet
                emojiPickerTargetId = null
                val changed = controller.updateProfileEmoji(target, emoji)
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
    targetCornerRadius: Dp = 28.dp,
    targetFraction: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val targetCornerRadiusPx = with(density) { targetCornerRadius.toPx() }
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
private fun TabCoverflowHeroContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: Float,
    targetFraction: () -> Float,
) {
    val density = LocalDensity.current
    val previewLayout = TabOverviewHeroRules.coverflowPreviewLayout(
        rootWidthPx = rootWidthPx,
        rootHeightPx = rootHeightPx,
        targetWidthPx = targetBounds.width,
        targetHeightPx = targetBounds.height,
        cropTopFraction = PREVIEW_CROP_TOP_FRACTION,
    )
    val targetHeight = with(density) { previewLayout.sourceHeightPx.toDp() }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = 1f - TabOverviewHeroRules.coverflowPreviewAlpha(targetFraction())
                },
        ) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(0, previewLayout.sourceTopPx.roundToInt()) }
                .fillMaxWidth()
                .height(targetHeight)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = TabOverviewHeroRules.coverflowPreviewAlpha(targetFraction())
                },
        ) {
            TabPreviewContent(tab = tab, preview = preview, favicon = favicon)
        }
    }
}

@Composable
private fun TabListHeroContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: Float,
    targetFraction: () -> Float,
) {
    val density = LocalDensity.current
    val targetScale = (targetBounds.width / rootWidthPx).coerceAtLeast(0.01f)
    val sourceRowHeight = with(density) { (targetBounds.height / targetScale).toDp() }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = 1f - TabOverviewHeroRules.compactChromeAlpha(targetFraction())
                },
        ) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(sourceRowHeight)
                .graphicsLayer {
                    val fraction = targetFraction().coerceIn(0f, 1f)
                    val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                    val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                    val scale = width / rootWidthPx
                    val visibleHeight = height / scale
                    translationY = (rootHeightPx - visibleHeight) * PREVIEW_CROP_TOP_FRACTION
                    alpha = TabOverviewHeroRules.compactChromeAlpha(fraction)
                },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .height(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabFavicon(tab = tab, favicon = favicon, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            displayTabTitle(tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (tab.url == BLANK_URL) {
                                stringResource(R.string.new_tab_title)
                            } else {
                                AddressResolver.displayText(tab.url)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tab.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_push_pin),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 15.dp)
                                .size(20.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }
        }
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
                onLongClickLabel = stringResource(R.string.action_open_candy_trail),
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
            TabPreviewContent(tab = tab, preview = preview, favicon = favicon)
        }
    }
}

private class TabBoundsHolder {
    var bounds: Rect? = null
}

@Composable
private fun CompactTabGrid(
    tabs: List<BrowserTab>,
    selectedTabId: String,
    initialTabId: String,
    previews: Map<String, Bitmap>,
    favicons: Map<String, Bitmap>,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitHeroTabId: String?,
    dismissResistanceFraction: Float,
    interactionsEnabled: Boolean,
    onPreviewBounds: (BrowserTab, Rect) -> Unit,
    onSelect: (BrowserTab, Rect) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    onSwipeDismissStart: (BrowserTab) -> Boolean,
    onSwipeDismissEnd: (BrowserTab) -> Unit,
    onSwipeDismiss: (BrowserTab) -> Unit,
    onLongClick: (BrowserTab, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(initialTabId, tabs.firstOrNull()?.id) {
        if (tabs.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            gridState.scrollToItem(selectedIndex)
        }
    }
    val topFadeAlpha by animateFloatAsState(
        targetValue = if (gridState.canScrollBackward) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "gridTopFade",
    )
    val bottomFadeAlpha by animateFloatAsState(
        targetValue = if (gridState.canScrollForward) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "gridBottomFade",
    )
    val rootView = LocalView.current
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    val overviewBackgroundColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surface,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { gridBounds = it.boundsInRoot() },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItemsIndexed(
                items = tabs,
                key = { _, tab -> tab.id },
                contentType = { _, _ -> "tab-grid-card" },
            ) { index, tab ->
                CompactGridTabItem(
                    tab = tab,
                    preview = previews[tab.id],
                    favicon = favicons[tab.id],
                    selected = tab.id == selectedTabId,
                    initial = tab.id == initialTabId,
                    heroProgress = heroProgress,
                    heroCompleted = heroCompleted,
                    heroVisible = heroVisible,
                    exitTarget = tab.id == exitHeroTabId,
                    dismissResistanceFraction = dismissResistanceFraction,
                    interactionsEnabled = interactionsEnabled,
                    revealDelayMillis = (index % 6) * 24L,
                    onPreviewBounds = { bounds -> onPreviewBounds(tab, bounds) },
                    onSelect = { bounds -> onSelect(tab, bounds) },
                    onClose = { onCloseTab(tab) },
                    onSwipeDismissStart = { onSwipeDismissStart(tab) },
                    onSwipeDismissEnd = { onSwipeDismissEnd(tab) },
                    onSwipeDismiss = { onSwipeDismiss(tab) },
                    onLongClick = { bounds -> onLongClick(tab, bounds) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(28.dp)
                .graphicsLayer {
                    alpha = topFadeAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    val topInRoot = gridBounds?.top ?: 0f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = overviewBackgroundColors,
                            start = Offset(0f, -topInRoot),
                            end = Offset(
                                rootView.width.toFloat(),
                                rootView.height.toFloat() - topInRoot,
                            ),
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .graphicsLayer {
                    alpha = bottomFadeAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    val bottomInRoot = gridBounds?.bottom ?: rootView.height.toFloat()
                    val topInRoot = bottomInRoot - size.height
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = overviewBackgroundColors,
                            start = Offset(0f, -topInRoot),
                            end = Offset(
                                rootView.width.toFloat(),
                                rootView.height.toFloat() - topInRoot,
                            ),
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

@Composable
private fun CompactGridTabItem(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    selected: Boolean,
    initial: Boolean,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitTarget: Boolean,
    dismissResistanceFraction: Float,
    interactionsEnabled: Boolean,
    revealDelayMillis: Long,
    onPreviewBounds: (Rect) -> Unit,
    onSelect: (Rect) -> Unit,
    onClose: () -> Unit,
    onSwipeDismissStart: () -> Boolean,
    onSwipeDismissEnd: () -> Unit,
    onSwipeDismiss: () -> Unit,
    onLongClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootView = LocalView.current
    val gestureScope = rememberCoroutineScope()
    val boundsHolder = remember(tab.id) { TabBoundsHolder() }
    val revealProgress = remember(tab.id) {
        Animatable(if (initial || heroCompleted) 1f else 0f)
    }
    val breakFreeProgress = remember(tab.id) { Animatable(0f) }
    var breakFreeJob by remember(tab.id) { mutableStateOf<Job?>(null) }
    var rawDismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
    var dismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
    var cardWidthPx by remember(tab.id) { mutableFloatStateOf(1f) }
    var dragActive by remember(tab.id) { mutableStateOf(false) }
    var resistanceCleared by remember(tab.id) { mutableStateOf(false) }
    var rubberbandHapticActive by remember(tab.id) { mutableStateOf(false) }
    var dismissHapticPlayed by remember(tab.id) { mutableStateOf(false) }
    var gestureRaised by remember(tab.id) { mutableStateOf(false) }
    var dismissInProgress by remember(tab.id) { mutableStateOf(false) }
    DisposableEffect(tab.id, rootView) {
        onDispose {
            breakFreeJob?.cancel()
            if (rubberbandHapticActive) {
                rootView.stopRubberbandHaptic()
            }
            if (dismissInProgress) {
                onSwipeDismissEnd()
            }
        }
    }
    LaunchedEffect(heroCompleted, tab.id) {
        if (initial) {
            revealProgress.snapTo(1f)
        } else if (!heroCompleted) {
            revealProgress.snapTo(0f)
        } else {
            delay(revealDelayMillis)
            revealProgress.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
        }
    }
    val shape = RoundedCornerShape(22.dp)
    val realCardVisible = TabOverviewHeroRules.isCardVisible(
        isInitialCard = initial,
        progress = if (heroCompleted) 1f else 0f,
        isExitTarget = exitTarget,
    )
    val dismissThreshold = cardWidthPx * 0.53f
    val dragState = rememberDraggableState { delta ->
        rawDismissOffset += delta
        val rawDistance = rawDismissOffset.absoluteValue
        val hasClearedResistance = TabDismissPhysics.hasClearedResistance(
            rawDistance = rawDistance,
            dismissThreshold = dismissThreshold,
            resistanceFraction = dismissResistanceFraction,
        )
        val shouldVibrate = TabDismissPhysics.isInResistancePhase(
            rawDistance = rawDistance,
            dismissThreshold = dismissThreshold,
            resistanceFraction = dismissResistanceFraction,
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
            breakFreeJob = gestureScope.launch {
                breakFreeProgress.animateTo(
                    targetValue = if (hasClearedResistance) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 800f,
                    ),
                )
            }
        }
        if (hasClearedResistance && !dismissHapticPlayed) {
            rootView.performConfirmHaptic()
            dismissHapticPlayed = true
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { cardWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .zIndex(if (gestureRaised) 2f else 0f)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val currentDismissOffset = if (dragActive) {
                    TabDismissPhysics.signedVisualDistance(
                        rawDistance = rawDismissOffset,
                        releaseProgress = breakFreeProgress.value,
                    )
                } else {
                    dismissOffset
                }
                translationX = currentDismissOffset
                val dismissProgress =
                    (currentDismissOffset.absoluteValue / (dismissThreshold * 1.7f))
                        .coerceIn(0f, 1f)
                alpha = (when {
                    initial -> TabOverviewHeroRules.compactChromeAlpha(heroProgress())
                    else -> TabOverviewHeroRules.neighborAlpha(heroProgress()) *
                        revealProgress.value
                }) * (1f - dismissProgress * 0.72f)
                val dismissScale = 1f - dismissProgress * 0.05f
                scaleX = dismissScale
                scaleY = dismissScale
                rotationZ = (currentDismissOffset / cardWidthPx).coerceIn(-1f, 1f) * 2f
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = interactionsEnabled &&
                    heroCompleted &&
                    !heroVisible &&
                    TabDeletionRules.canDelete(tab),
                onDragStarted = {
                    breakFreeJob?.cancel()
                    breakFreeProgress.snapTo(0f)
                    rootView.stopRubberbandHaptic()
                    rawDismissOffset = 0f
                    dismissOffset = 0f
                    dragActive = true
                    gestureRaised = true
                    resistanceCleared = false
                    rubberbandHapticActive = false
                    dismissHapticPlayed = false
                },
                onDragStopped = {
                    rootView.stopRubberbandHaptic()
                    rubberbandHapticActive = false
                    breakFreeJob?.cancel()
                    breakFreeProgress.stop()
                    dismissOffset = TabDismissPhysics.signedVisualDistance(
                        rawDistance = rawDismissOffset,
                        releaseProgress = breakFreeProgress.value,
                    )
                    dragActive = false
                    val farEnough = TabDismissPhysics.hasClearedResistance(
                        rawDistance = rawDismissOffset.absoluteValue,
                        dismissThreshold = dismissThreshold,
                        resistanceFraction = dismissResistanceFraction,
                    )
                    if (farEnough && onSwipeDismissStart()) {
                        dismissInProgress = true
                        gestureScope.launch {
                            try {
                                val direction = if (rawDismissOffset < 0f) -1f else 1f
                                Animatable(dismissOffset).animateTo(
                                    targetValue = direction * rootView.width * 1.1f,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ) { dismissOffset = value }
                                onSwipeDismiss()
                            } finally {
                                dismissInProgress = false
                                gestureRaised = false
                                onSwipeDismissEnd()
                            }
                        }
                    } else {
                        gestureScope.launch {
                            Animatable(dismissOffset).animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 520f,
                                ),
                            ) { dismissOffset = value }
                            rawDismissOffset = 0f
                            breakFreeProgress.snapTo(0f)
                            resistanceCleared = false
                            dismissHapticPlayed = false
                            gestureRaised = false
                        }
                    }
                },
            )
            .semantics { this.selected = selected }
            .combinedClickable(
                enabled = interactionsEnabled,
                role = Role.Button,
                onClick = { boundsHolder.bounds?.let(onSelect) },
                onLongClick = { boundsHolder.bounds?.let(onLongClick) },
                onLongClickLabel = stringResource(R.string.action_open_candy_trail),
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabFavicon(tab = tab, favicon = favicon, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    displayTabTitle(tab),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                )
                if (tab.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = stringResource(R.string.cd_pinned_tab),
                        modifier = Modifier
                            .padding(horizontal = 15.dp)
                            .size(16.dp),
                    )
                } else {
                    IconButton(
                        onClick = onClose,
                        enabled = interactionsEnabled,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(
                                R.string.cd_close_named_tab,
                                displayTabTitle(tab),
                            ),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .graphicsLayer {
                        alpha = if (realCardVisible && !heroVisible) 1f else 0f
                    }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        boundsHolder.bounds = bounds
                        onPreviewBounds(bounds)
                    },
            ) {
                TabPreviewContent(tab = tab, preview = preview, favicon = favicon)
            }
        }
    }
}

@Composable
private fun CompactTabList(
    tabs: List<BrowserTab>,
    selectedTabId: String,
    initialTabId: String,
    favicons: Map<String, Bitmap>,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitHeroTabId: String?,
    interactionsEnabled: Boolean,
    onRowBounds: (BrowserTab, Rect) -> Unit,
    onSelect: (BrowserTab, Rect) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    onLongClick: (BrowserTab, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(initialTabId, tabs.firstOrNull()?.id) {
        if (tabs.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.scrollToItem(selectedIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = tabs,
            key = { _, tab -> tab.id },
            contentType = { _, _ -> "tab-list-row" },
        ) { _, tab ->
            CompactListTabItem(
                tab = tab,
                favicon = favicons[tab.id],
                selected = tab.id == selectedTabId,
                initial = tab.id == initialTabId,
                heroProgress = heroProgress,
                heroCompleted = heroCompleted,
                heroVisible = heroVisible,
                exitTarget = tab.id == exitHeroTabId,
                interactionsEnabled = interactionsEnabled,
                onBounds = { bounds -> onRowBounds(tab, bounds) },
                onSelect = { bounds -> onSelect(tab, bounds) },
                onClose = { onCloseTab(tab) },
                onLongClick = { bounds -> onLongClick(tab, bounds) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun CompactListTabItem(
    tab: BrowserTab,
    favicon: Bitmap?,
    selected: Boolean,
    initial: Boolean,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitTarget: Boolean,
    interactionsEnabled: Boolean,
    onBounds: (Rect) -> Unit,
    onSelect: (Rect) -> Unit,
    onClose: () -> Unit,
    onLongClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val boundsHolder = remember(tab.id) { TabBoundsHolder() }
    val realRowVisible = TabOverviewHeroRules.isCardVisible(
        isInitialCard = initial,
        progress = if (heroCompleted) 1f else 0f,
        isExitTarget = exitTarget,
    )
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                alpha = when {
                    !realRowVisible || (initial && heroVisible) -> 0f
                    initial -> 1f
                    else -> TabOverviewHeroRules.neighborAlpha(heroProgress())
                }
                translationY = if (initial) 0f else {
                    (1f - TabOverviewHeroRules.neighborAlpha(heroProgress())) * 18f
                }
            }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                boundsHolder.bounds = bounds
                onBounds(bounds)
            }
            .combinedClickable(
                enabled = interactionsEnabled,
                role = Role.Button,
                onClick = { boundsHolder.bounds?.let(onSelect) },
                onLongClick = { boundsHolder.bounds?.let(onLongClick) },
                onLongClickLabel = stringResource(R.string.action_open_candy_trail),
            )
            .semantics { this.selected = selected },
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
        },
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .height(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                        ),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabFavicon(tab = tab, favicon = favicon, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayTabTitle(tab),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    )
                    Text(
                        if (tab.url == BLANK_URL) {
                            stringResource(R.string.new_tab_title)
                        } else {
                            AddressResolver.displayText(tab.url)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tab.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = stringResource(R.string.cd_pinned_tab),
                        modifier = Modifier
                            .padding(horizontal = 15.dp)
                            .size(20.dp),
                    )
                } else {
                    IconButton(
                        onClick = onClose,
                        enabled = interactionsEnabled,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(
                                R.string.cd_close_named_tab,
                                displayTabTitle(tab),
                            ),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabFavicon(
    tab: BrowserTab,
    favicon: Bitmap?,
    size: Dp,
) {
    if (tab.isIncognito) {
        Icon(
            painter = painterResource(R.drawable.ic_incognito_outline),
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    } else if (favicon != null && !favicon.isRecycled) {
        Image(
            bitmap = favicon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(size * 0.28f),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    displayTabTitle(tab).take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TabPreviewContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
) {
    when {
        tab.isIncognito -> IncognitoTabPlaceholder()
        preview != null && !preview.isRecycled -> Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(
                horizontalBias = 0f,
                verticalBias = PREVIEW_CROP_TOP_FRACTION * 2f - 1f,
            ),
        )
        else -> TabPreviewPlaceholder(title = displayTabTitle(tab), favicon = favicon)
    }
}

@Composable
private fun TabActionsSheet(
    tab: BrowserTab?,
    profiles: List<BrowserProfile>,
    canFork: Boolean,
    onFork: () -> Unit,
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
                onClick = onFork,
                modifier = Modifier.fillMaxWidth(),
                enabled = canFork,
            ) {
                Text(stringResource(R.string.action_fork_tab))
            }
            if (canFork) {
                Text(
                    stringResource(R.string.fork_url_only_disclaimer),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
    isolationSupported: Boolean,
    onChangeEmoji: () -> Unit,
    onDelete: () -> Unit,
    onIsolationChange: (Boolean) -> Unit,
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
            SettingsSwitch(
                title = stringResource(R.string.settings_profile_isolation_title),
                subtitle = stringResource(
                    if (isolationSupported) R.string.settings_profile_isolation_subtitle
                    else R.string.settings_profile_isolation_unsupported,
                ),
                checked = profile.isolationEnabled && isolationSupported,
                enabled = isolationSupported,
                onCheckedChange = onIsolationChange,
            )
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
internal fun EmojiPickerSheet(
    visible: Boolean,
    creatingProfile: Boolean,
    isolationSupported: Boolean,
    selectedEmoji: String?,
    onCreate: (String, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var draftEmoji by remember(creatingProfile, selectedEmoji) { mutableStateOf(selectedEmoji) }
    var draftIsolationEnabled by remember(creatingProfile) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = creatingProfile)
    val creationSheetHeight = (
        LocalConfiguration.current.screenHeightDp * PROFILE_CREATION_SHEET_HEIGHT_FRACTION
    ).dp
    val dragHandle: @Composable (() -> Unit)? = if (creatingProfile) {
        null
    } else {
        { BottomSheetDefaults.DragHandle() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(ProfileCreationTestTags.Sheet),
        dragHandle = dragHandle,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (creatingProfile) Modifier.height(creationSheetHeight) else Modifier)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            if (creatingProfile) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }
                Box(modifier = Modifier.testTag(ProfileCreationTestTags.Isolation)) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_profile_isolation_title),
                        subtitle = stringResource(
                            if (isolationSupported) R.string.settings_profile_isolation_subtitle
                            else R.string.settings_profile_isolation_unsupported,
                        ),
                        checked = draftIsolationEnabled && isolationSupported,
                        enabled = isolationSupported,
                        onCheckedChange = { draftIsolationEnabled = it },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                stringResource(
                    if (creatingProfile) R.string.add_profile_title
                    else R.string.change_profile_icon_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (creatingProfile) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .testTag(ProfileCreationTestTags.IconScroll),
            ) {
                PROFILE_EMOJIS.chunked(6).forEach { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowEmojis.forEach { emoji ->
                            val isSelected = emoji == draftEmoji
                            Surface(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .size(48.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            if (creatingProfile) {
                                                draftEmoji = emoji
                                            } else {
                                                onSelect(emoji)
                                            }
                                        },
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
            if (creatingProfile) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        draftEmoji?.let { emoji -> onCreate(emoji, draftIsolationEnabled) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileCreationTestTags.CreateButton),
                    enabled = draftEmoji != null,
                ) {
                    Text(stringResource(R.string.action_create_profile))
                }
            }
        }
    }
}

internal object ProfileCreationTestTags {
    const val Sheet = "profile_creation_sheet"
    const val Isolation = "profile_creation_isolation"
    const val IconScroll = "profile_creation_icon_scroll"
    const val CreateButton = "profile_creation_create_button"
}

private const val PREVIEW_CROP_TOP_FRACTION = 0.25f
private const val PROFILE_CREATION_SHEET_HEIGHT_FRACTION = 0.66f
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
    val titleContentColor = TabOverviewContrastRules.titleContentColor(
        primaryContainer = MaterialTheme.colorScheme.primaryContainer,
        tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer,
    )
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
                color = titleContentColor,
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
    tabOverviewMode: TabOverviewMode,
    dismissResistancePercent: Int,
    blockedCount: Int,
    isDefaultBrowser: Boolean,
    siteCapsules: List<SiteCapsule>,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onRequestDefaultBrowser: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onFilterStudio: () -> Unit,
    onOpenLegalUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lifetimeMenuExpanded by remember { mutableStateOf(false) }
    var searchEngineMenuExpanded by remember { mutableStateOf(false) }
    var overviewModeMenuExpanded by remember { mutableStateOf(false) }
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
                    title = stringResource(R.string.settings_tab_overview_mode),
                    value = tabOverviewMode.displayName(),
                    expanded = overviewModeMenuExpanded,
                    onClick = { overviewModeMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = overviewModeMenuExpanded,
                    onDismissRequest = { overviewModeMenuExpanded = false },
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    TabOverviewMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayName()) },
                            onClick = {
                                overviewModeMenuExpanded = false
                                onTabOverviewModeChanged(mode)
                            },
                            trailingIcon = {
                                if (mode == tabOverviewMode) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
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
            SettingsSectionTitle(stringResource(R.string.capsule_settings_title))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.capsule_settings_launcher_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (siteCapsules.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        stringResource(R.string.capsule_settings_empty),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                siteCapsules.forEach { capsule ->
                    Surface(
                        onClick = { onEditCapsule(capsule) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(capsule.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    AddressResolver.displayText(capsule.startUrl),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteCapsule(capsule) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.capsule_delete_title),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_protection))
            Spacer(Modifier.height(6.dp))
            PrivacyXRaySettingsCounter(
                blockedCount = blockedCount,
                onClick = onPrivacyXRay,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onFilterStudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
                    Text(
                        stringResource(R.string.filter_studio_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.filter_studio_settings_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
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
            Spacer(Modifier.height(24.dp))
            AboutLegalSection(onOpenUrl = onOpenLegalUrl)
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
private fun TabOverviewMode.displayName(): String = when (this) {
    TabOverviewMode.Hero -> stringResource(R.string.tab_overview_mode_hero)
    TabOverviewMode.Grid -> stringResource(R.string.tab_overview_mode_grid)
    TabOverviewMode.List -> stringResource(R.string.tab_overview_mode_list)
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.6f,
                ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

internal fun View.performConfirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )
}

private fun View.performRejectHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
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
