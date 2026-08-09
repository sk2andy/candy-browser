@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.semantics.paneTitle
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import dev.sk2andy.materialbrowser.browser.CapsuleSaveResult
import dev.sk2andy.materialbrowser.browser.MAX_PROFILES
import dev.sk2andy.materialbrowser.browser.MAX_TABS
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionClient
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionRules
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
import dev.sk2andy.materialbrowser.browser.permissions.PermissionOrigin
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.capsule.CapsuleIconMode
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleDraft
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorContract
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorRequest
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.data.TabPinningRules
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.reader.ReaderStudioSession
import dev.sk2andy.materialbrowser.reader.ReaderStudioSessionRules
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

internal object AddressSuggestionTestTags {
    fun searchRow(query: String): String = "address_search_suggestion:$query"
    fun fillSearch(query: String): String = "address_search_suggestion_fill:$query"
}

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
    ReaderStudio,
    FilterStudio,
    SnoozedTabs,
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
    var addressEditorVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var snoozedTabsVisible by rememberSaveable { mutableStateOf(false) }
    var snoozeTabId by remember { mutableStateOf<String?>(null) }
    var privacyXRayTabId by remember { mutableStateOf<String?>(null) }
    var permissionRadarTabId by remember { mutableStateOf<String?>(null) }
    var permissionRadarOrigin by remember { mutableStateOf<String?>(null) }
    var filterStudioVisible by rememberSaveable { mutableStateOf(false) }
    var filterStudioSelectedRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var readerStudioSession by remember { mutableStateOf<ReaderStudioSession?>(null) }
    var readerStudioResult by remember { mutableStateOf<ReaderExtractionResult?>(null) }
    var readerStudioRequestId by remember { mutableIntStateOf(0) }
    var clearDialogVisible by remember { mutableStateOf(false) }
    var pendingCapsuleDelete by remember { mutableStateOf<SiteCapsule?>(null) }
    var addressValue by remember { mutableStateOf(TextFieldValue()) }
    var remoteSearchSuggestions by remember { mutableStateOf(emptyList<String>()) }
    val searchSuggestionClient = remember { SearchSuggestionClient() }
    var highlightedSuggestionIndex by remember { mutableIntStateOf(-1) }
    var addressFocusNonce by remember { mutableIntStateOf(0) }
    var pendingCommand by remember { mutableStateOf<CommandSuggestion?>(null) }
    val overviewGestureProgress = remember { mutableFloatStateOf(0f) }
    val overviewMorphProgress = remember { mutableFloatStateOf(0f) }
    var overviewGestureSettleJob by remember { mutableStateOf<Job?>(null) }
    var overviewMorphJob by remember { mutableStateOf<Job?>(null) }
    var overviewEntryHeroCompleted by remember { mutableStateOf(false) }
    val overviewGestureScope = rememberCoroutineScope()
    var favoriteFeedbackId by remember { mutableIntStateOf(0) }
    var favoriteFeedbackEvent by remember { mutableStateOf<FavoriteFeedbackEvent?>(null) }
    var feedbackSnackbarJob by remember { mutableStateOf<Job?>(null) }
    val feedbackSnackbarHostState = remember { SnackbarHostState() }
    var activeCommandExecutionId by remember { mutableStateOf<String?>(null) }
    var commandFeedback by remember { mutableStateOf<AddressCommandFeedback?>(null) }
    val browserDragOffset = remember { mutableFloatStateOf(0f) }
    var browserWidthPx by remember { mutableFloatStateOf(1f) }
    var browserHeightPx by remember { mutableFloatStateOf(1f) }
    val bottomBarTopPx = remember { mutableFloatStateOf(Float.NaN) }
    var addressNewTabButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var keepLinkPeekAddressBarExpanded by remember { mutableStateOf(false) }
    var tabOverviewOpening by remember { mutableStateOf(false) }
    var tabHandoff by remember { mutableStateOf<TabHandoff?>(null) }
    val liveFrameTabIdState = remember { mutableStateOf<String?>(null) }
    var liveFrameTabId by liveFrameTabIdState
    val reportLiveFrame = remember { { tabId: String -> liveFrameTabIdState.value = tabId } }
    val tabHandoffAlpha = remember { Animatable(1f) }
    val settingsBackProgress = remember { Animatable(0f) }
    val candyTrailBackProgress = remember { Animatable(0f) }
    var settingsPredictiveBackCommitted by remember { mutableStateOf(false) }
    var candyTrailPredictiveBackCommitted by remember { mutableStateOf(false) }
    val backAnimationScope = rememberCoroutineScope()
    var settingsBackEdgeSign by remember { mutableIntStateOf(1) }
    var candyTrailBackEdgeSign by remember { mutableIntStateOf(1) }
    var qrScanInProgress by remember { mutableStateOf(false) }
    val overviewDestinationChromeVisible by remember {
        derivedStateOf {
            overviewEntryHeroCompleted &&
                AddressBarOverviewGestureRules.isDestinationButtonVisible(
                    overviewMorphProgress.floatValue,
                )
        }
    }
    val addressBarMorphInFront =
        tabOverviewVisible && !overviewDestinationChromeVisible
    val selectedTab = controller.selectedTab
    val permissionActivityVisible = controller.hasPermissionActivity(selectedTab.id)
    LaunchedEffect(
        controller.contentActions.isLinkPeekVisible,
        controller.contentActions.linkPeekNewTabPulseNonce,
    ) {
        if (controller.contentActions.isLinkPeekVisible) {
            keepLinkPeekAddressBarExpanded = true
        } else if (keepLinkPeekAddressBarExpanded) {
            delay(620)
            keepLinkPeekAddressBarExpanded = false
        }
    }
    val linkPeekAddressBarExpanded = controller.contentActions.isLinkPeekVisible ||
        keepLinkPeekAddressBarExpanded
    val blankTabModeProgress = rememberBlankTabModeProgress(
        tabId = selectedTab.id,
        incognito = selectedTab.isIncognito,
    )
    var blankTabModeRevealOrigin by remember(selectedTab.id) {
        mutableStateOf(Offset.Unspecified)
    }
    val context = LocalContext.current
    val readerLibraryRepository = remember(context) { ReaderLibraryRepository.get(context) }
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
    val siteCapsuleEditorLauncher = rememberLauncherForActivityResult(
        contract = SiteCapsuleEditorContract(),
    ) { submission ->
        submission ?: return@rememberLauncherForActivityResult
        val existing = submission.existingId?.let { id ->
            controller.siteCapsules.firstOrNull { it.id == id }
        }
        if (submission.existingId != null && existing == null) {
            Toast.makeText(
                context,
                context.getString(R.string.capsule_invalid_configuration),
                Toast.LENGTH_SHORT,
            ).show()
            return@rememberLauncherForActivityResult
        }
        var profileId = submission.selectedProfileId
        var ownsDedicated = existing?.ownsDedicatedProfile == true &&
            existing.profileId == profileId
        val previousProfileId = controller.activeProfileId
        if (existing == null && submission.createDedicatedProfile) {
            profileId = controller.createProfile(
                emoji = submission.dedicatedEmoji,
                isolationEnabled = submission.isolatedStorageRequested,
            ) ?: return@rememberLauncherForActivityResult
            ownsDedicated = true
        }
        val result = controller.upsertSiteCapsule(
            draft = SiteCapsuleDraft(
                id = existing?.id,
                name = submission.name,
                startUrl = submission.startUrl,
                profileId = profileId,
                ownsDedicatedProfile = ownsDedicated,
                isolatedStorageRequested = submission.isolatedStorageRequested,
                navigationMode = submission.navigationMode,
                chromeMode = submission.chromeMode,
                iconMode = submission.iconMode,
            ),
            sourceFavicon = if (existing == null) {
                submission.sourceTabId?.let { controller.favicons[it] }
                    ?: submission.sourceFavicon
            } else {
                null
            },
        )
        if (controller.activeProfileId != previousProfileId) {
            controller.selectProfile(previousProfileId)
        }
        val message = when (result) {
            CapsuleSaveResult.PinRequested -> R.string.capsule_pin_requested
            CapsuleSaveResult.PinningUnsupported -> R.string.capsule_pinning_unsupported
            CapsuleSaveResult.PinRequestFailed -> R.string.capsule_pin_failed
            CapsuleSaveResult.Updated -> R.string.capsule_updated
            CapsuleSaveResult.UpdateFailed -> R.string.capsule_update_failed
            CapsuleSaveResult.LimitReached -> R.string.capsule_limit_reached
            CapsuleSaveResult.Invalid -> R.string.capsule_invalid_configuration
        }
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
        if (result == CapsuleSaveResult.PinRequested || result == CapsuleSaveResult.Updated) {
            rootView.performConfirmHaptic()
        }
    }
    fun openSiteCapsuleEditor(existing: SiteCapsule?, sourceTab: BrowserTab?) {
        if (existing == null && sourceTab == null) return
        val sourceTitle = existing?.name ?: sourceTab?.title.orEmpty().ifBlank {
            sourceTab?.url?.let(AddressResolver::displayText).orEmpty()
        }
        val sourceUrl = existing?.startUrl ?: sourceTab?.url.orEmpty()
        siteCapsuleEditorLauncher.launch(
            SiteCapsuleEditorRequest(
                existing = existing,
                sourceTabId = sourceTab?.id,
                sourceTitle = sourceTitle,
                sourceUrl = sourceUrl,
                profiles = controller.profiles.toList(),
                activeProfileId = controller.activeProfileId,
                profileIsolationSupported = controller.isProfileIsolationSupported,
                pinningSupported = controller.isCapsulePinningSupported,
                canCreate = controller.canCreateSiteCapsule,
                canCreateDedicatedProfile = controller.profiles.size < MAX_PROFILES &&
                    controller.tabs.size < MAX_TABS,
                previewIcon = if (existing?.iconMode == CapsuleIconMode.Favicon) {
                    controller.siteCapsuleIcon(existing.id)
                } else {
                    sourceTab?.let { controller.favicons[it.id] }
                },
            ),
        )
        rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val favoriteAddedMessage = stringResource(R.string.favorite_added_confirmation)
    val favoriteRemovedMessage = stringResource(R.string.favorite_removed_confirmation)
    val snoozeConfirmationMessage = stringResource(R.string.snooze_confirmation)
    val undoLabel = stringResource(R.string.action_undo)
    val toggleFavoriteWithFeedback: (String) -> Unit = { tabId ->
        controller.toggleFavorite(tabId)?.let { mutation ->
            rootView.performConfirmHaptic()
            favoriteFeedbackId++
            favoriteFeedbackEvent = FavoriteFeedbackEvent(
                id = favoriteFeedbackId,
                added = mutation.added,
            )
            feedbackSnackbarJob?.cancel()
            feedbackSnackbarJob = backAnimationScope.launch {
                val result = feedbackSnackbarHostState.showSnackbar(
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
    }
    val tabSwitchGapPx = with(density) { 8.dp.toPx() }
    val tabSwitchTravelPx = browserWidthPx + tabSwitchGapPx
    val settleOverviewGesture: () -> Unit = {
        overviewGestureSettleJob?.cancel()
        overviewGestureSettleJob = overviewGestureScope.launch {
            val settleProgress = Animatable(overviewGestureProgress.floatValue)
            settleProgress.updateBounds(lowerBound = 0f, upperBound = 1f)
            settleProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
            ) { overviewGestureProgress.floatValue = value }
        }
    }
    val openTabOverview = {
        if (!tabOverviewVisible && !tabOverviewOpening) {
            overviewMorphJob?.cancel()
            overviewMorphProgress.floatValue = 0f
            overviewEntryHeroCompleted = false
            tabOverviewOpening = true
            controller.prepareTabOverview {
                tabOverviewOpening = false
                tabOverviewVisible = true
            }
        }
    }
    val closeTabOverview = {
        overviewGestureSettleJob?.cancel()
        overviewMorphJob?.cancel()
        overviewGestureProgress.floatValue = 0f
        overviewMorphProgress.floatValue = 0f
        overviewEntryHeroCompleted = false
        tabOverviewVisible = false
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
    fun createTabAndConfirm(isIncognito: Boolean, emitHaptic: Boolean): Boolean {
        val previousTabId = controller.selectedTabId
        val createdTabId = controller.createTab(isIncognito = isIncognito)
        if (createdTabId == previousTabId) return false
        if (emitHaptic) rootView.performConfirmHaptic()
        return true
    }
    val openNewTabAndEdit: () -> Unit = {
        if (createTabAndConfirm(isIncognito = false, emitHaptic = true)) {
            addressValue = TextFieldValue()
            addressEditorVisible = true
            highlightedSuggestionIndex = -1
            addressFocusNonce++
        }
    }
    LaunchedEffect(
        addressEditorVisible,
        addressValue.text,
        controller.searchSuggestionProvider,
        selectedTab.id,
        selectedTab.isIncognito,
    ) {
        remoteSearchSuggestions = emptyList()
        if (
            !addressEditorVisible ||
            !SearchSuggestionRules.shouldRequest(
                query = addressValue.text,
                provider = controller.searchSuggestionProvider,
                isIncognito = selectedTab.isIncognito,
            )
        ) {
            return@LaunchedEffect
        }
        delay(SearchSuggestionRules.DEBOUNCE_MILLIS)
        remoteSearchSuggestions = searchSuggestionClient.suggestions(
            provider = controller.searchSuggestionProvider,
            query = addressValue.text.trim(),
        )
    }
    val suggestionItems by remember {
        derivedStateOf {
            if (addressEditorVisible) {
                controller.addressSuggestionItems(
                    query = addressValue.text,
                    searchQueries = remoteSearchSuggestions,
                    limit = 10,
                )
            } else {
                emptyList()
            }
        }
    }
    val domainCompletion = if (
        addressEditorVisible &&
        addressValue.selection.start == addressValue.selection.end &&
        addressValue.selection.end == addressValue.text.length
    ) {
        controller.addressDomainCompletion(addressValue.text)
    } else {
        null
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
        override fun createTab(isIncognito: Boolean): Boolean = createTabAndConfirm(
            isIncognito = isIncognito,
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
            rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
            is AddressSuggestionItem.Search -> {
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                controller.submitAddress(item.query)
                addressEditorVisible = false
            }
        }
    }

    fun fillAddressFromSuggestion(item: AddressSuggestionItem): Unit {
        val text = when (item) {
            is AddressSuggestionItem.Navigation -> item.suggestion.url
            is AddressSuggestionItem.Search -> item.query
            is AddressSuggestionItem.Command -> return
        }
        addressValue = TextFieldValue(text = text, selection = TextRange(text.length))
        highlightedSuggestionIndex = -1
        addressFocusNonce++
        rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
        tabHandoffAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        )
        if (tabHandoff?.tabId == handoff.tabId) tabHandoff = null
    }

    LaunchedEffect(selectedTab.id, selectedTab.error) {
        if (selectedTab.error != null && tabHandoff?.tabId == selectedTab.id) {
            tabHandoff = null
        }
    }

    LaunchedEffect(selectedTab.id, readerStudioSession) {
        if (ReaderStudioSessionRules.shouldClose(readerStudioSession, selectedTab.id)) {
            readerStudioSession = null
            readerStudioRequestId++
        }
    }

    LaunchedEffect(controller.tabs.size, privacyXRayTabId) {
        val xRayTabId = privacyXRayTabId ?: return@LaunchedEffect
        if (controller.tabs.none { it.id == xRayTabId }) privacyXRayTabId = null
    }
    LaunchedEffect(controller.tabs.size, permissionRadarTabId) {
        val radarTabId = permissionRadarTabId ?: return@LaunchedEffect
        if (controller.tabs.none { it.id == radarTabId }) {
            permissionRadarTabId = null
            permissionRadarOrigin = null
        }
    }

    LaunchedEffect(
        tabOverviewVisible,
        addressEditorVisible,
        settingsVisible,
        snoozedTabsVisible,
        filterStudioVisible,
        candyTrailTabId,
        readerStudioSession,
    ) {
        if (
            tabOverviewVisible || addressEditorVisible || settingsVisible || snoozedTabsVisible ||
            filterStudioVisible || candyTrailTabId != null || readerStudioSession != null
        ) {
            controller.setPreviewCaptureEnabled(false)
        } else {
            delay(120)
            controller.setPreviewCaptureEnabled(true)
        }
    }

    val currentBackTarget by rememberUpdatedState(
        when {
            readerStudioSession != null -> BrowserBackTarget.ReaderStudio
            filterStudioVisible -> BrowserBackTarget.FilterStudio
            snoozedTabsVisible -> BrowserBackTarget.SnoozedTabs
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
                BrowserBackTarget.ReaderStudio -> readerStudioSession = null
                BrowserBackTarget.FilterStudio -> filterStudioVisible = false
                BrowserBackTarget.SnoozedTabs -> snoozedTabsVisible = false
                BrowserBackTarget.Settings -> {
                    settingsPredictiveBackCommitted = receivedProgress
                    if (receivedProgress) {
                        settingsBackProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = PredictiveBackMotion.remainingDurationMillis(
                                    settingsBackProgress.value,
                                ),
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    settingsVisible = false
                }
                BrowserBackTarget.AddressEditor -> addressEditorVisible = false
                BrowserBackTarget.CandyTrail -> {
                    candyTrailPredictiveBackCommitted = receivedProgress
                    if (receivedProgress) {
                        candyTrailBackProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = PredictiveBackMotion.remainingDurationMillis(
                                    candyTrailBackProgress.value,
                                ),
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    candyTrailTabId = null
                    candyTrailSourceBounds = null
                }
                BrowserBackTarget.TabOverview -> closeTabOverview()
                BrowserBackTarget.WebHistory -> controller.goBack()
                BrowserBackTarget.None -> Unit
            }
        } catch (cancellation: CancellationException) {
            if (target == BrowserBackTarget.Settings) {
                settingsPredictiveBackCommitted = false
                backAnimationScope.launch {
                    settingsBackProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
                    )
                }
            } else if (target == BrowserBackTarget.CandyTrail) {
                candyTrailPredictiveBackCommitted = false
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
        if (settingsVisible) {
            settingsPredictiveBackCommitted = false
            if (settingsBackProgress.value > 0f) {
                settingsBackProgress.snapTo(0f)
            }
        } else if (!settingsVisible && settingsBackProgress.value > 0f) {
            delay(PredictiveBackMotion.EXIT_DURATION_MILLIS.toLong())
            settingsBackProgress.snapTo(0f)
            settingsPredictiveBackCommitted = false
        }
    }
    LaunchedEffect(candyTrailTabId, controller.activeTabs) {
        val trailTabId = candyTrailTabId
        if (trailTabId != null && controller.activeTabs.none { it.id == trailTabId }) {
            candyTrailTabId = null
            candyTrailSourceBounds = null
        }
    }
    LaunchedEffect(candyTrailTabId) {
        val trailTabId = candyTrailTabId
        if (trailTabId != null) {
            candyTrailPredictiveBackCommitted = false
            if (candyTrailBackProgress.value > 0f) {
                candyTrailBackProgress.snapTo(0f)
            }
        } else if (candyTrailBackProgress.value > 0f) {
            delay(PredictiveBackMotion.EXIT_DURATION_MILLIS.toLong())
            candyTrailBackProgress.snapTo(0f)
            candyTrailPredictiveBackCommitted = false
        }
    }
    LaunchedEffect(tabOverviewVisible) {
        if (!tabOverviewVisible) {
            overviewGestureProgress.floatValue = 0f
        }
    }

    SideEffect {
        controller.setBrowserChromeOwnsIme(addressEditorVisible)
    }
    DisposableEffect(controller) {
        onDispose { controller.setBrowserChromeOwnsIme(false) }
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
            onLiveFrame = reportLiveFrame,
            onSearch = openAddressEditor,
            onReload = controller::reload,
            blankTabModeProgress = blankTabModeProgress,
            blankTabModeRevealOrigin = blankTabModeRevealOrigin,
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
                    onFill = ::fillAddressFromSuggestion,
                    rootHeightPx = browserHeightPx,
                    bottomBarTopPx = bottomBarTopPx,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        BrowserBottomBar(
            tab = selectedTab,
            compact = controller.isBottomBarCompact && !linkPeekAddressBarExpanded,
            docked = controller.isAddressBarDocked && !linkPeekAddressBarExpanded,
            editing = addressEditorVisible,
            showTabButton = controller.isTabButtonVisible,
            tabCount = controller.activeTabs.size,
            commandFeedback = commandFeedback,
            feedbackGesturesEnabled = !addressEditorVisible && !settingsVisible,
            onBack = controller::goBack,
            onForward = controller::goForward,
            onAddress = openAddressEditor,
            editValue = addressValue,
            onEditValueChange = { addressValue = it },
            ghostCompletion = domainCompletion,
            onAcceptGhostCompletion = {
                domainCompletion?.let { completion ->
                    addressValue = TextFieldValue(
                        text = completion,
                        selection = TextRange(completion.length),
                    )
                    rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            },
            addressFocusNonce = addressFocusNonce,
            onMoveAddressSuggestion = ::moveSuggestionHighlight,
            onActivateAddressSuggestion = {
                val highlighted = suggestionItems.getOrNull(highlightedSuggestionIndex)
                if (highlighted == null) {
                    submitAddressOrCommand(
                        AddressEditorCompletionRules.submissionText(
                            input = addressValue.text,
                            ghostCompletion = domainCompletion,
                        ),
                    )
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
            onDock = { controller.updateAddressBarDocked(true) },
            onRestoreDock = { controller.updateAddressBarDocked(false) },
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
            showingTabOverview = tabOverviewVisible,
            visualOnly = addressBarMorphInFront,
            onOverviewGestureProgress = { progress ->
                overviewGestureSettleJob?.cancel()
                overviewGestureProgress.floatValue = progress.coerceIn(0f, 1f)
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
            onToggleFavorite = { toggleFavoriteWithFeedback(selectedTab.id) },
            canToggleDomainMute = controller.canToggleSelectedDomainMute,
            isDomainMuted = controller.isSelectedDomainMuted,
            onDomainMutedChange = controller::setSelectedDomainMuted,
            snoozedTabCount = controller.snoozedTabs.size,
            onSnoozedTabs = {
                addressEditorVisible = false
                snoozedTabsVisible = true
            },
            onSettings = {
                addressEditorVisible = false
                settingsVisible = true
            },
            onPrivacyXRay = {
                privacyXRayTabId = selectedTab.id
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            permissionActivityVisible = permissionActivityVisible,
            onPermissionRadar = {
                permissionRadarTabId = selectedTab.id
                permissionRadarOrigin = null
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            addressBarPulseNonce = controller.contentActions.addressBarPulseNonce,
            newTabPulseNonce = controller.contentActions.linkPeekNewTabPulseNonce,
            onNewTabButtonBounds = { addressNewTabButtonBounds = it },
            onOpenExternal = controller::openSelectedPageExternally,
            onSummarizeWithAssistant = controller::summarizeSelectedPageWithAssistant,
            onShare = controller::shareSelectedPage,
            onPrint = controller::printSelectedPage,
            onReaderStudio = {
                readerStudioResult = null
                val requestId = ++readerStudioRequestId
                readerStudioSession = ReaderStudioSession(
                    tabId = selectedTab.id,
                    sourceUrl = selectedTab.url,
                    isPrivate = selectedTab.isIncognito,
                    requestId = requestId,
                )
                controller.extractSelectedPageForReader { result ->
                    if (ReaderStudioSessionRules.acceptsResult(readerStudioSession, requestId)) {
                        readerStudioResult = result
                    }
                }
            },
            onOpenCandyTrail = {
                candyTrailSourceBounds = null
                candyTrailTabId = selectedTab.id
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            onSnooze = { snoozeTabId = selectedTab.id },
            onAddSiteCapsule = {
                openSiteCapsuleEditor(existing = null, sourceTab = selectedTab)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(
                    when {
                        commandFeedback != null && !tabOverviewVisible -> 30f
                        addressBarMorphInFront -> 20f
                        else -> 0f
                    },
                )
                .onGloballyPositioned { coordinates ->
                    if (
                        controller.isAddressBarDocked &&
                        !addressEditorVisible &&
                        commandFeedback == null
                    ) {
                        bottomBarTopPx.floatValue = Float.NaN
                        controller.setPreviewContentBottomInWindowPx(0)
                    } else {
                        bottomBarTopPx.floatValue = coordinates.boundsInRoot().top
                        controller.setPreviewContentBottomInWindowPx(
                            coordinates.boundsInWindow().top.roundToInt(),
                        )
                    }
                },
        )

        readerStudioSession?.let { session ->
            ReaderStudioScreen(
                result = readerStudioResult,
                sourceUrl = session.sourceUrl,
                isPrivate = session.isPrivate,
                repository = readerLibraryRepository,
                onRetry = {
                    readerStudioResult = null
                    val requestId = ++readerStudioRequestId
                    readerStudioSession = session.copy(requestId = requestId)
                    controller.extractSelectedPageForReader { result ->
                        if (ReaderStudioSessionRules.acceptsResult(
                                readerStudioSession,
                                requestId,
                            )
                        ) {
                            readerStudioResult = result
                        }
                    }
                },
                onDismiss = { readerStudioSession = null },
                onOpenOriginal = { url ->
                    readerStudioSession = null
                    if (url != selectedTab.url) controller.openUrl(url)
                },
                onOpenLink = { url ->
                    readerStudioSession = null
                    controller.openUrl(url)
                },
            )
        }

        TabOverview(
            controller = controller,
            visible = tabOverviewVisible,
            bottomBarTopPx = bottomBarTopPx,
            onClose = closeTabOverview,
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
                openNewTabAndEdit()
                if (controller.selectedTabId != previousTabId) closeTabOverview()
            },
            destinationChromeVisible = overviewDestinationChromeVisible,
            onEntryHeroStarted = { animated ->
                overviewMorphJob?.cancel()
                overviewMorphProgress.floatValue = 0f
                overviewEntryHeroCompleted = false
                if (animated) {
                    overviewMorphJob = overviewGestureScope.launch {
                        val progress = Animatable(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = TabOverviewHeroRules.ENTRY_DURATION_MILLIS,
                                easing = FastOutSlowInEasing,
                            ),
                        ) { overviewMorphProgress.floatValue = value }
                    }
                } else {
                    overviewMorphProgress.floatValue = 1f
                }
            },
            onEntryHeroCompleted = { overviewEntryHeroCompleted = true },
            candyTrailTabId = candyTrailTabId,
            candyTrailSourceBounds = candyTrailSourceBounds,
            candyTrailBackProgress = candyTrailBackProgress.value,
            candyTrailBackEdgeSign = candyTrailBackEdgeSign,
            candyTrailPredictiveBackCommitted = candyTrailPredictiveBackCommitted,
            onOpenCandyTrail = { tabId, bounds ->
                candyTrailSourceBounds = bounds
                candyTrailTabId = tabId
            },
            onCloseCandyTrail = {
                candyTrailTabId = null
                candyTrailSourceBounds = null
            },
            onToggleFavoriteTab = toggleFavoriteWithFeedback,
            onAddSiteCapsule = { tabId ->
                openSiteCapsuleEditor(
                    existing = null,
                    sourceTab = controller.tabs.firstOrNull { it.id == tabId },
                )
            },
            onSnoozeTab = { tabId -> snoozeTabId = tabId },
        )

        SnoozeTabDialog(
            tab = snoozeTabId?.let { id -> controller.tabs.firstOrNull { it.id == id } },
            onSnooze = { wakeAtMillis ->
                val tabId = snoozeTabId ?: return@SnoozeTabDialog false
                val undoToken = controller.snoozeTab(tabId, wakeAtMillis)
                if (undoToken != null) {
                    feedbackSnackbarJob?.cancel()
                    feedbackSnackbarJob = backAnimationScope.launch {
                        showSnoozeUndoFeedback(
                            hostState = feedbackSnackbarHostState,
                            message = snoozeConfirmationMessage,
                            undoLabel = undoLabel,
                        ) {
                            controller.undoSnooze(undoToken)
                        }
                    }
                }
                undoToken != null
            },
            onDismiss = { snoozeTabId = null },
        )

        AnimatedVisibility(
            visible = settingsVisible,
            enter = slideInHorizontally(
                initialOffsetX = { width ->
                    PredictiveBackMotion.entryTranslation(
                        progress = 0f,
                        width = width.toFloat(),
                    ).roundToInt()
                },
                animationSpec = tween(
                    durationMillis = PredictiveBackMotion.ENTRY_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            ),
            exit = if (settingsPredictiveBackCommitted) {
                ExitTransition.None
            } else {
                slideOutHorizontally(
                    targetOffsetX = { width -> width },
                    animationSpec = tween(
                        durationMillis = PredictiveBackMotion.EXIT_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            },
        ) {
            SettingsScreen(
                blockerSettings = controller.blockerSettings,
                inactiveTabLifetime = controller.inactiveTabLifetime,
                searchEngine = controller.searchEngine,
                searchSuggestionProvider = controller.searchSuggestionProvider,
                tabOverviewMode = controller.tabOverviewMode,
                dismissResistancePercent = controller.dismissResistancePercent,
                isTabButtonVisible = controller.isTabButtonVisible,
                isWebContentEdgeToEdgeEnabled = controller.isWebContentEdgeToEdgeEnabled,
                blockedCount = selectedTab.blockedCount,
                isDefaultBrowser = controller.isDefaultBrowser,
                siteCapsules = controller.siteCapsules,
                onBlockerSettingsChanged = controller::updateBlockerSettings,
                onInactiveTabLifetimeChanged = controller::updateInactiveTabLifetime,
                onSearchEngineChanged = controller::updateSearchEngine,
                onSearchSuggestionProviderChanged = controller::updateSearchSuggestionProvider,
                onTabOverviewModeChanged = controller::updateTabOverviewMode,
                onDismissResistancePercentChanged = controller::updateDismissResistancePercent,
                onTabButtonVisibleChanged = controller::updateTabButtonVisible,
                onWebContentEdgeToEdgeChanged = controller::updateWebContentEdgeToEdgeEnabled,
                onOpenDefaultBrowserSettings = controller::openDefaultBrowserSettings,
                onPrivacyXRay = {
                    privacyXRayTabId = selectedTab.id
                    rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onPermissionRadar = {
                    permissionRadarTabId = selectedTab.id
                    permissionRadarOrigin = null
                    rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onEditCapsule = { capsule ->
                    openSiteCapsuleEditor(existing = capsule, sourceTab = null)
                },
                onDeleteCapsule = { capsule -> pendingCapsuleDelete = capsule },
                onFilterStudio = {
                    filterStudioSelectedRuleId = null
                    filterStudioVisible = true
                },
                onClearData = { clearDialogVisible = true },
                onOpenLegalUrl = { url ->
                    settingsVisible = false
                    controller.openUrl(url, inNewTab = true)
                },
                onDismiss = { settingsVisible = false },
                modifier = Modifier.predictiveBackSurface(
                    settingsBackProgress.value,
                    settingsBackEdgeSign,
                ),
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
                    onCookieBannerRemovalEnabledChange = { enabled ->
                        controller.setCookieBannerRemovalDisabled(tabId, !enabled)
                    },
                    onForceVerticalScrollingChange = { enabled ->
                        controller.setForceVerticalScrolling(tabId, enabled)
                    },
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

        permissionRadarTabId?.let { tabId ->
            val radarTab = controller.tabs.firstOrNull { it.id == tabId }
            if (radarTab != null) {
                val snapshot = controller.permissionRadarSnapshot(tabId, permissionRadarOrigin)
                val profileEmoji = controller.profiles
                    .firstOrNull { it.id == radarTab.profileId }
                    ?.emoji
                    .orEmpty()
                PermissionRadarSheet(
                    snapshot = snapshot,
                    profileEmoji = profileEmoji,
                    onOriginSelected = { permissionRadarOrigin = it },
                    onDecisionChanged = { permission, decision ->
                        snapshot.site?.let { site ->
                            controller.setSitePermissionDecision(
                                tabId = tabId,
                                origin = site.origin,
                                permission = permission,
                                decision = decision,
                            )
                        }
                    },
                    onResetSite = {
                        snapshot.site?.let { site ->
                            controller.resetSitePermissions(tabId, site.origin)
                        }
                    },
                    onDismiss = {
                        permissionRadarTabId = null
                        permissionRadarOrigin = null
                    },
                )
            }
        }

        controller.permissionPrompt?.let { prompt ->
            PermissionPromptDialog(
                prompt = prompt,
                onChoice = { choice -> controller.respondToPermissionPrompt(prompt.id, choice) },
            )
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
            hostState = feedbackSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 84.dp)
                .zIndex(30f),
        )

        AnimatedVisibility(
            visible = snoozedTabsVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
        ) {
            SnoozedTabsScreen(
                snoozedTabs = controller.snoozedTabs,
                profiles = controller.profiles,
                onBack = { snoozedTabsVisible = false },
                onReschedule = controller::rescheduleSnoozedTab,
                onOpenNow = { tabId ->
                    controller.openSnoozedTabNow(tabId)
                },
                onDelete = controller::deleteSnoozedTab,
            )
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

    if (controller.contentActions.isLinkPeekVisible) {
        val linkTarget = controller.contentActions.target
        LinkPeekOverlay(
            url = requireNotNull(linkTarget?.linkUrl),
            progress = controller.contentActions.linkPeekProgress,
            armed = controller.contentActions.isLinkPeekArmed,
            committing = controller.contentActions.isLinkPeekCommitting,
            newTabTargetBounds = addressNewTabButtonBounds,
            createPreviewWebView = { onProgressChanged, onCommittedUrlChanged ->
                controller.createLinkPeekPreviewWebView(
                    url = requireNotNull(linkTarget?.linkUrl),
                    onProgressChanged = onProgressChanged,
                    onCommittedUrlChanged = onCommittedUrlChanged,
                )
            },
            releasePreviewWebView = controller::releaseLinkPeekPreviewWebView,
            onCommitRequested = controller.contentActions::startLinkPeekCommit,
            onOpen = {
                rootView.performConfirmHaptic()
                controller.openContextLinkInBackground()
            },
            onDownloadImage = linkTarget.takeIf { it?.canDownloadImage == true }?.let {
                controller::downloadContextImage
            },
            onDismiss = controller.contentActions::dismiss,
        )
    } else if (controller.contentActions.isVisible) {
        WebContentContextSheet(
            target = controller.contentActions.target,
            onOpenLinkInBackground = controller::openContextLinkInBackground,
            onDownloadImage = controller::downloadContextImage,
            onDismiss = controller.contentActions::dismiss,
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
    bottomBarTopPx: FloatState,
    handoff: TabHandoff?,
    handoffAlpha: Float,
    liveFrameTabId: String?,
    tabOverviewVisible: Boolean,
    onLiveFrame: (String) -> Unit,
    onSearch: () -> Unit,
    onReload: () -> Unit,
    blankTabModeProgress: Float,
    blankTabModeRevealOrigin: Offset,
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
    val webViewTouchListener = remember(
        selectedTab.id,
        density.density,
        touchSlop,
        pullRefreshTouchListener,
    ) {
        LinkPeekTouchListener(
            threshold = { view ->
                TabDismissPhysics.rawThresholdForCardWidth(
                    cardWidth = TabDismissPhysics.compactGridCardWidth(
                        viewportWidth = view.width.toFloat(),
                        totalHorizontalPadding = 32.dp.value * density.density,
                        horizontalGap = 12.dp.value * density.density,
                    ),
                    resistanceFraction = controller.dismissResistancePercent / 100f,
                )
            },
            touchSlop = touchSlop,
            delegate = pullRefreshTouchListener,
            isVisible = { controller.contentActions.isLinkPeekVisible },
            onProgress = controller.contentActions::updateLinkPeek,
            onOpen = controller.contentActions::startLinkPeekCommit,
            onDismiss = controller.contentActions::dismiss,
            onThresholdHaptic = { view ->
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
            },
            onPointerDown = controller.contentActions::beginPointerStream,
            onPointerEnd = controller.contentActions::endPointerStream,
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
        if (selectedTab.url != BLANK_URL) {
            ActiveWebView(
                controller = controller,
                visible = !tabOverviewVisible || selectedTab.isIncognito,
                onLiveFrame = onLiveFrame,
                pullRefreshTouchListener = webViewTouchListener,
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
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
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
private fun FullscreenTabPreviewContent(
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
    val previewLayout = TabSwitchPreviewLayoutRules.resolve(
        rootHeightPx = rootHeightPx,
        previewTopInsetPx = previewTopInsetPx,
        bottomBarTopPx = bottomBarTopPx.floatValue,
    )
    val topInset = with(density) { previewLayout.topInsetPx.toDp() }
    val visibleHeight = with(density) { previewLayout.visibleHeightPx.toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
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
private fun BlankTabPreview(
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
        val previewLayout = TabOverviewHeroRules.coverflowPreviewLayout(
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

@Composable
private fun NewTabPage(
    favorites: List<FavoriteEntry>,
    incognito: Boolean,
    modeProgress: Float,
    revealOriginInRoot: Offset,
    onSearch: () -> Unit,
    onFavorite: (String) -> Unit,
    interactive: Boolean = true,
    favoritesAlpha: () -> Float = { 1f },
    explicitSafeDrawingPadding: PaddingValues? = null,
) {
    val colors = MaterialTheme.colorScheme
    val boundedProgress = BlankTabModeMorphRules.bounded(modeProgress)
    val regularIconAlpha = BlankTabModeMorphRules.regularIconAlpha(boundedProgress)
    val incognitoIconAlpha = BlankTabModeMorphRules.incognitoIconAlpha(boundedProgress)
    val openSearchDescription = stringResource(R.string.cd_open_search)
    val scrollState = rememberScrollState()
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
            .then(
                if (explicitSafeDrawingPadding != null) {
                    Modifier.padding(explicitSafeDrawingPadding)
                } else {
                    Modifier.safeDrawingPadding()
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .heightIn(max = 520.dp)
                .then(if (interactive) Modifier.verticalScroll(scrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                onClick = onSearch,
                enabled = interactive,
                modifier = Modifier
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
            if (!incognito && favorites.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = favoritesAlpha().coerceIn(0f, 1f)
                        },
                ) {
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
                            ExpressiveFavoriteRows(
                                favorites = favorites,
                                onFavorite = onFavorite,
                                enabled = interactive,
                            )
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
    docked: Boolean,
    editing: Boolean,
    showTabButton: Boolean,
    tabCount: Int,
    commandFeedback: AddressCommandFeedback?,
    feedbackGesturesEnabled: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    ghostCompletion: String?,
    onAcceptGhostCompletion: () -> Unit,
    addressFocusNonce: Int,
    onMoveAddressSuggestion: (Int) -> Unit,
    onActivateAddressSuggestion: () -> Unit,
    onDismissEditor: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    onScanQrCode: () -> Unit,
    onExpand: () -> Unit,
    onDock: () -> Unit,
    onRestoreDock: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: () -> Unit,
    onToggleIncognito: () -> Unit,
    blankTabModeProgress: Float,
    onIncognitoControlCenterChanged: (Offset) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    onDomainMutedChange: (Boolean) -> Unit,
    snoozedTabCount: Int,
    onSnoozedTabs: () -> Unit,
    onSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    permissionActivityVisible: Boolean,
    onPermissionRadar: () -> Unit,
    addressBarPulseNonce: Int,
    newTabPulseNonce: Int,
    onNewTabButtonBounds: (Rect) -> Unit,
    onOpenExternal: () -> Unit,
    onSummarizeWithAssistant: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onReaderStudio: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onSnooze: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    overviewGestureEnabled: Boolean,
    overviewGestureProgress: FloatState,
    showingTabOverview: Boolean,
    visualOnly: Boolean,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val presentation = AddressBarPresentationRules.resolve(
        docked = docked,
        compact = compact,
        editing = editing,
        showingCommandFeedback = commandFeedback != null,
        showingTabOverview = showingTabOverview,
    )
    val tabDragState = rememberDraggableState(onTabDrag)
    val pulseScale = remember { Animatable(1f) }
    val newTabPulseScale = remember { Animatable(1f) }
    val domain = AddressResolver.displayText(tab.url)
    val readerSupported = ReaderStudioSessionRules.isSupportedSource(tab.url)
    val readerOpenLabel = stringResource(R.string.reader_open_action)
    val feedbackText = commandFeedback?.localizedText().orEmpty()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
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
    LaunchedEffect(newTabPulseNonce) {
        if (newTabPulseNonce == 0) return@LaunchedEffect
        newTabPulseScale.snapTo(1f)
        newTabPulseScale.animateTo(
            targetValue = 1.11f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 720f),
        )
        newTabPulseScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        )
    }
    val compactWidth = with(density) {
        textMeasurer.measure(
            text = domain,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        ).size.width.toDp() + 84.dp
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding()
            .fillMaxWidth()
            .then(if (visualOnly) Modifier.clearAndSetSemantics { } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val edgeTabWidth = 52.dp
        val layoutDirection = LocalLayoutDirection.current
        val motion = rememberAddressBarMotionState(
            presentation = presentation,
            compactWidth = compactWidth,
            maxWidth = maxWidth,
            feedbackWidth = feedbackWidth,
            edgeTabWidth = edgeTabWidth,
            layoutDirection = layoutDirection,
        )
        val animatedBarWidth = motion.width
        val animatedBarHeight = motion.height
        val dockOffset = motion.dockOffset
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .offset(x = dockOffset)
                    .width(animatedBarWidth)
                    .height(animatedBarHeight)
                    .graphicsLayer {
                        scaleX = pulseScale.value
                        scaleY = pulseScale.value
                    },
                shape = CircleShape,
                color = barColor,
                tonalElevation = 12.dp,
                shadowElevation = 14.dp,
            ) {
                Box {
                    AddressBarPresentationTransition(
                        presentation = presentation,
                    ) { targetPresentation ->
                        when (targetPresentation) {
                            AddressBarPresentation.Docked -> AddressBarEdgeTab(
                                onRestore = onRestoreDock,
                                onTabs = onTabs,
                                overviewGestureEnabled = overviewGestureEnabled,
                                overviewGestureProgress = overviewGestureProgress,
                                onOverviewGestureProgress = onOverviewGestureProgress,
                                onOverviewGestureStarted = onOverviewGestureStarted,
                                onOverviewGestureCancelled = onOverviewGestureCancelled,
                            )
                            AddressBarPresentation.Compact -> {
                                Surface(
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
                                            onDragStopped = { velocity ->
                                                onTabDragStopped(velocity)
                                            },
                                        )
                                        .addressBarReaderActions(
                                            readerEnabled = readerSupported,
                                            onClick = onExpand,
                                            onReaderStudio = onReaderStudio,
                                            readerLabel = readerOpenLabel,
                                        ),
                                    color = Color.Transparent,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = domain,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 18.dp, end = 4.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        IconButton(onClick = onDock) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled
                                                    .KeyboardArrowRight,
                                                contentDescription = stringResource(
                                                    R.string.action_dock_address_bar,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                            AddressBarPresentation.Expanded -> ExpandedBottomBarContent(
                                tab = tab,
                                showTabButton = showTabButton,
                                tabCount = tabCount,
                                menuExpanded = menuExpanded,
                                onMenuExpandedChange = { menuExpanded = it },
                                onBack = onBack,
                                onForward = onForward,
                                onAddress = onAddress,
                                editing = editing,
                                editValue = editValue,
                                onEditValueChange = onEditValueChange,
                                ghostCompletion = ghostCompletion,
                                onAcceptGhostCompletion = onAcceptGhostCompletion,
                                focusRequester = focusRequester,
                                addressFocusNonce = addressFocusNonce,
                                requestAddressFocus = editing && commandFeedback == null,
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
                                newTabPulseScale = newTabPulseScale.value,
                                onNewTabButtonBounds = onNewTabButtonBounds,
                                onToggleIncognito = onToggleIncognito,
                                blankTabModeProgress = blankTabModeProgress,
                                onIncognitoControlCenterChanged =
                                    onIncognitoControlCenterChanged,
                                isFavorite = isFavorite,
                                onToggleFavorite = onToggleFavorite,
                                canToggleDomainMute = canToggleDomainMute,
                                isDomainMuted = isDomainMuted,
                                onDomainMutedChange = onDomainMutedChange,
                                snoozedTabCount = snoozedTabCount,
                                onSnoozedTabs = onSnoozedTabs,
                                onSettings = onSettings,
                                onPrivacyXRay = onPrivacyXRay,
                                permissionActivityVisible = permissionActivityVisible,
                                onPermissionRadar = onPermissionRadar,
                                onOpenExternal = onOpenExternal,
                                onSummarizeWithAssistant = onSummarizeWithAssistant,
                                onShare = onShare,
                                onPrint = onPrint,
                                onReaderStudio = onReaderStudio,
                                onOpenCandyTrail = onOpenCandyTrail,
                                onSnooze = onSnooze,
                                onAddSiteCapsule = onAddSiteCapsule,
                                overviewGestureEnabled = overviewGestureEnabled,
                                overviewGestureProgress = overviewGestureProgress,
                                onOverviewGestureProgress = onOverviewGestureProgress,
                                onOverviewGestureStarted = onOverviewGestureStarted,
                                onOverviewGestureCancelled = onOverviewGestureCancelled,
                            )
                            AddressBarPresentation.Overview -> OverviewAddressBarContent(
                                onNewTab = onNewTab,
                                onMore = {},
                            )
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
                    if (commandFeedback == null && !showingTabOverview) {
                        AddressLoadCapsuleFeedback(
                            tabId = tab.id,
                            isLoading = tab.isLoading,
                            progressPercent = tab.progress,
                            morphProgress = 0f,
                            morphTargetSizePx = with(density) { 56.dp.toPx() },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
        if (visualOnly) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    },
            )
        }
    }
}

internal object AddressBarDockTestTags {
    const val EdgeTab = "address_bar_edge_tab"
}

internal object TabOverviewChromeTestTags {
    const val Bar = "tab_overview_address_bar"
    const val NewTab = "tab_overview_new_tab"
    const val More = "tab_overview_more"
}

@Composable
private fun OverviewAddressBarContent(
    onNewTab: () -> Unit,
    onMore: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onNewTab,
            enabled = enabled,
            modifier = Modifier.testTag(TabOverviewChromeTestTags.NewTab),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_tab))
        }
        IconButton(
            onClick = onMore,
            enabled = enabled,
            modifier = Modifier.testTag(TabOverviewChromeTestTags.More),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
            )
        }
    }
}

@Composable
internal fun AddressBarEdgeTab(
    onRestore: () -> Unit,
    onTabs: () -> Unit,
    overviewGestureEnabled: Boolean,
    overviewGestureProgress: FloatState,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val restoreDescription = stringResource(R.string.cd_restore_address_bar)
    Surface(
        onClick = onRestore,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(AddressBarDockTestTags.EdgeTab)
            .semantics { contentDescription = restoreDescription }
            .addressBarVerticalGesture(
                enabled = overviewGestureEnabled,
                initialProgress = overviewGestureProgress,
                onProgress = onOverviewGestureProgress,
                onStarted = onOverviewGestureStarted,
                onCancelled = onOverviewGestureCancelled,
                onSwipeUp = onTabs,
            ),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
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

internal fun Modifier.addressBarReaderActions(
    readerEnabled: Boolean,
    onClick: () -> Unit,
    onReaderStudio: () -> Unit,
    readerLabel: String,
): Modifier = combinedClickable(
    role = Role.Button,
    onClick = onClick,
    onLongClick = onReaderStudio.takeIf { readerEnabled },
    onLongClickLabel = readerLabel.takeIf { readerEnabled },
)

@Composable
internal fun Modifier.addressBarVerticalGesture(
    enabled: Boolean = true,
    initialProgress: FloatState? = null,
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
    val touchSlop = LocalViewConfiguration.current.touchSlop
    if (!enabled) return this
    return pointerInput(enabled, touchSlop) {
        val threshold = AddressBarGestureRules.OPEN_TABS_THRESHOLD_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var lastY = down.position.y
            var accumulatedX = 0f
            var accumulatedY = 0f
            var gestureActive = false
            var state = AddressBarOverviewGestureRules.stateForProgress(
                progress = currentInitialProgress?.floatValue ?: 0f,
                threshold = threshold,
            )
            var committed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position - change.previousPosition
                    if (!gestureActive) {
                        accumulatedX += delta.x
                        accumulatedY += delta.y
                        when (
                            AddressBarOverviewGestureRules.direction(
                                dragX = accumulatedX,
                                dragY = accumulatedY,
                                touchSlop = touchSlop,
                            )
                        ) {
                            AddressBarOverviewGestureDirection.Pending -> {
                                lastY = change.position.y
                                if (!change.pressed) break
                                continue
                            }
                            AddressBarOverviewGestureDirection.Rejected -> break
                            AddressBarOverviewGestureDirection.Upward -> {
                                gestureActive = true
                                currentOnStarted()
                                lastY = down.position.y
                            }
                        }
                    }
                    val update = AddressBarOverviewGestureRules.update(
                        state = state,
                        deltaY = change.position.y - lastY,
                        threshold = threshold,
                    )
                    state = update.state
                    lastY = change.position.y
                    change.consume()
                    currentOnProgress(update.progress)
                    if (!change.pressed) {
                        val release = AddressBarOverviewGestureRules.release(state)
                        if (release.shouldCommit) {
                            committed = true
                            currentOnProgress(release.progress)
                            gestureView.performHapticFeedback(
                                HapticFeedbackConstants.VIRTUAL_KEY,
                            )
                            currentOnSwipeUp()
                        }
                        break
                    }
                }
            } finally {
                if (gestureActive && !committed) currentOnCancelled()
            }
        }
    }
}

@Composable
private fun ExpandedBottomBarContent(
    tab: BrowserTab,
    showTabButton: Boolean,
    tabCount: Int,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    editing: Boolean,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    ghostCompletion: String?,
    onAcceptGhostCompletion: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    addressFocusNonce: Int,
    requestAddressFocus: Boolean,
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
    onNewTab: () -> Unit,
    newTabPulseScale: Float,
    onNewTabButtonBounds: (Rect) -> Unit,
    onToggleIncognito: () -> Unit,
    blankTabModeProgress: Float,
    onIncognitoControlCenterChanged: (Offset) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    onDomainMutedChange: (Boolean) -> Unit,
    snoozedTabCount: Int,
    onSnoozedTabs: () -> Unit,
    onSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    permissionActivityVisible: Boolean,
    onPermissionRadar: () -> Unit,
    onOpenExternal: () -> Unit,
    onSummarizeWithAssistant: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onReaderStudio: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onSnooze: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    overviewGestureEnabled: Boolean,
    overviewGestureProgress: FloatState,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
) {
    val tabDragState = rememberDraggableState(onTabDrag)
    val keyboard = LocalSoftwareKeyboardController.current
    var addressFieldFocused by remember(tab.id) { mutableStateOf(false) }
    val editorUsesFullWidth = AddressBarControlRules.editorUsesFullWidth(
        editing = editing,
        addressFieldFocused = addressFieldFocused,
        imeVisible = WindowInsets.isImeVisible,
    )
    LaunchedEffect(editorUsesFullWidth) {
        if (editorUsesFullWidth) onMenuExpandedChange(false)
    }
    LaunchedEffect(requestAddressFocus, tab.id, addressFocusNonce) {
        if (requestAddressFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Column {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            AnimatedVisibility(
                visible = showTabButton && !editorUsesFullWidth,
                enter = fadeIn(tween(120)) + expandHorizontally(tween(180)),
                exit = fadeOut(tween(80)) + shrinkHorizontally(tween(180)),
            ) {
                AddressBarTabCounterButton(
                    tabCount = tabCount,
                    onClick = onTabs,
                )
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
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
                shape = CircleShape,
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
                                        Key.DirectionRight,
                                        Key.Tab,
                                        -> if (
                                            event.type == KeyEventType.KeyDown &&
                                            ghostCompletion != null &&
                                            editValue.selection.start == editValue.text.length &&
                                            editValue.selection.end == editValue.text.length
                                        ) {
                                            onAcceptGhostCompletion()
                                            true
                                        } else {
                                            false
                                        }
                                        else -> false
                                    }
                                }
                                .focusRequester(focusRequester)
                                .onFocusChanged { addressFieldFocused = it.isFocused },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    onSubmitAddress(
                                        AddressEditorCompletionRules.submissionText(
                                            input = editValue.text,
                                            ghostCompletion = ghostCompletion,
                                        ),
                                    )
                                },
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
                                    } else if (ghostCompletion != null) {
                                        Row {
                                            Text(
                                                editValue.text,
                                                color = Color.Transparent,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                            Text(
                                                ghostCompletion.drop(editValue.text.length),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    .copy(alpha = 0.58f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip,
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        }
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
                                    .addressBarReaderActions(
                                        readerEnabled = ReaderStudioSessionRules
                                            .isSupportedSource(tab.url),
                                        onClick = onAddress,
                                        onReaderStudio = onReaderStudio,
                                        readerLabel = stringResource(R.string.reader_open_action),
                                    )
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
                            PermissionRadarBadge(
                                visible = permissionActivityVisible,
                                onClick = onPermissionRadar,
                                modifier = Modifier.padding(end = 2.dp),
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = !editorUsesFullWidth,
                enter = fadeIn(tween(120)) + expandHorizontally(tween(180)),
                exit = fadeOut(tween(80)) + shrinkHorizontally(tween(180)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            onClick = onNewTab,
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    onNewTabButtonBounds(coordinates.boundsInRoot())
                                }
                                .graphicsLayer {
                                    scaleX = newTabPulseScale
                                    scaleY = newTabPulseScale
                                }
                                .testTag("address_bar_new_tab_button"),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_new_tab),
                            )
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
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                )
                            }
                            BrowserMainMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { onMenuExpandedChange(false) },
                                pageSubtitle = if (tab.url == BLANK_URL) {
                                    stringResource(R.string.new_tab_title)
                                } else {
                                    AddressResolver.displayText(tab.url)
                                },
                                canGoBack = tab.canGoBack,
                                canGoForward = tab.canGoForward,
                                isLoading = tab.isLoading,
                                canToggleFavorite = tab.url != BLANK_URL && !tab.isIncognito,
                                isFavorite = isFavorite,
                                canUsePageActions = tab.url != BLANK_URL,
                                canOpenReader = ReaderStudioSessionRules.isSupportedSource(tab.url),
                                canToggleDomainMute = canToggleDomainMute,
                                isDomainMuted = isDomainMuted,
                                canAddSiteCapsule = tab.url != BLANK_URL &&
                                    !tab.isIncognito &&
                                    (
                                        tab.url.startsWith("https://") ||
                                            tab.url.startsWith("http://")
                                        ),
                                canSnooze = !tab.isIncognito,
                                snoozedTabCount = snoozedTabCount,
                                onBack = onBack,
                                onForward = onForward,
                                onReloadOrStop = { if (tab.isLoading) onStop() else onReload() },
                                onToggleFavorite = onToggleFavorite,
                                onShare = onShare,
                                onOpenExternal = onOpenExternal,
                                onPrint = onPrint,
                                onOpenReader = onReaderStudio,
                                onDomainMutedChange = onDomainMutedChange,
                                onOpenCandyTrail = onOpenCandyTrail,
                                onAddSiteCapsule = onAddSiteCapsule,
                                onSummarize = onSummarizeWithAssistant,
                                onSnooze = onSnooze,
                                onSnoozedTabs = onSnoozedTabs,
                                onSettings = onSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddressBarTabCounterButton(
    tabCount: Int,
    onClick: () -> Unit,
) {
    val label = AddressBarControlRules.tabCountLabel(tabCount)
    val description = pluralStringResource(
        R.plurals.cd_open_tab_overview_count,
        tabCount,
        tabCount,
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(AddressBarTestTags.TabButton)
            .semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(6.dp),
                )
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal object AddressBarTestTags {
    const val TabButton = "address_bar_tab_button"
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
    val regularIconAlpha = BlankTabModeMorphRules.regularIconAlpha(boundedProgress)
    val incognitoIconAlpha = BlankTabModeMorphRules.incognitoIconAlpha(boundedProgress)
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
    ) {
        if (showStartContent) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp),
                shape = RoundedCornerShape(
                    BlankTabModeMorphRules.heroCornerRadiusDp(boundedProgress).dp,
                ),
                color = lerp(colors.primary, colors.inverseSurface, boundedProgress),
                shadowElevation = 14.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
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
    suggestions: List<AddressSuggestionItem>,
    highlightedIndex: Int,
    onHighlight: (Int) -> Unit,
    onSelect: (AddressSuggestionItem) -> Unit,
    onFill: (AddressSuggestionItem) -> Unit,
    rootHeightPx: Float,
    bottomBarTopPx: FloatState,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedIndex, suggestions.map(AddressSuggestionItem::stableId)) {
        if (highlightedIndex in suggestions.indices) {
            listState.scrollToItem(highlightedIndex)
        }
    }
    val density = LocalDensity.current
    val currentBottomBarTopPx = bottomBarTopPx.floatValue
    val bottomPadding = AddressEditorLayoutRules.suggestionBottomPaddingDp(
        rootHeightPx = rootHeightPx,
        bottomBarTopPx = currentBottomBarTopPx,
        density = density.density,
    ).dp
    val maxHeight = AddressEditorLayoutRules.suggestionMaxHeightDp(
        bottomBarTopPx = currentBottomBarTopPx,
        topInsetPx = WindowInsets.statusBars.getTop(density).toFloat(),
        density = density.density,
    ).dp
    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomPadding)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 12.dp,
        shadowElevation = 12.dp,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = maxHeight),
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
                        onFill = { onFill(suggestion) },
                    )
                    is AddressSuggestionItem.Command -> CommandSuggestionRow(
                        suggestion = suggestion.suggestion,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                    )
                    is AddressSuggestionItem.Search -> SearchSuggestionRow(
                        query = suggestion.query,
                        highlighted = index == highlightedIndex,
                        onHighlight = { onHighlight(index) },
                        onClick = { onSelect(suggestion) },
                        onFill = { onFill(suggestion) },
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
    onFill: () -> Unit,
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
            .semantics { selected = highlighted }
            .clickable(
                role = Role.Button,
                onClick = {
                    onHighlight()
                    onClick()
                },
            ),
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
            IconButton(onClick = onFill, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(R.drawable.ic_north_east),
                    contentDescription = stringResource(
                        R.string.cd_fill_address_suggestion,
                        suggestion.url,
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun SearchSuggestionRow(
    query: String,
    highlighted: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
    onFill: () -> Unit,
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .fillMaxWidth()
            .testTag(AddressSuggestionTestTags.searchRow(query))
            .clip(RoundedCornerShape(16.dp))
            .semantics { selected = highlighted }
            .clickable(
                role = Role.Button,
                onClick = {
                    onHighlight()
                    onClick()
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                query,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
            IconButton(
                onClick = onFill,
                modifier = Modifier
                    .size(40.dp)
                    .testTag(AddressSuggestionTestTags.fillSearch(query)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_north_east),
                    contentDescription = stringResource(
                        R.string.cd_fill_address_suggestion,
                        query,
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
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
internal fun TabOverview(
    controller: BrowserController,
    visible: Boolean,
    bottomBarTopPx: FloatState,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onNewTab: () -> Unit,
    destinationChromeVisible: Boolean,
    onEntryHeroStarted: (Boolean) -> Unit,
    onEntryHeroCompleted: () -> Unit,
    candyTrailTabId: String?,
    candyTrailSourceBounds: Rect?,
    candyTrailBackProgress: Float,
    candyTrailBackEdgeSign: Int,
    candyTrailPredictiveBackCommitted: Boolean,
    onOpenCandyTrail: (String, Rect?) -> Unit,
    onCloseCandyTrail: () -> Unit,
    onToggleFavoriteTab: (String) -> Unit,
    onAddSiteCapsule: (String) -> Unit,
    onSnoozeTab: (String) -> Unit,
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

    val candyTrailTransition = updateTransition(
        targetState = candyTrailTabId,
        label = "Candy-Trail-Navigation",
    )
    val layerVisible = CandyTrailLayerRules.isVisible(
        tabOverviewVisible = visible,
        currentCandyTrailTabId = candyTrailTransition.currentState,
        targetCandyTrailTabId = candyTrailTransition.targetState,
    )
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
            onEntryHeroStarted(hasStableTarget)
            if (hasStableTarget) {
                heroProgress.animateTo(
                    1f,
                    tween(
                        durationMillis = TabOverviewHeroRules.ENTRY_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            } else {
                heroProgress.snapTo(1f)
            }
            heroCompleted = true
            onEntryHeroCompleted()
            withFrameNanos { }
            heroVisible = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (visible) {
                        TabOverviewHeroRules.backgroundAlpha(
                            entryProgress = heroProgress.value,
                            isExiting = isExiting,
                        )
                    } else {
                        0f
                    }
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
                    alpha = if (visible) {
                        TabOverviewHeroRules.contentAlpha(
                            exitProgress = exitHeroProgress.value,
                            isExiting = isExiting,
                        )
                    } else {
                        0f
                    }
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .then(
                    if (
                        candyTrailTransition.currentState != null ||
                        candyTrailTransition.targetState != null ||
                        tabActionsTabId != null
                    ) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
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
                            favorites = controller.favorites,
                            cardWidth = tabCardWidth,
                            modifier = Modifier
                                .testTag(SnoozeTestTags.overviewTab(tab.id))
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
                    favorites = controller.favorites,
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
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val progress = profileSwitchProgress.value
                            alpha = progress
                            translationY = (1f - progress) * 14f
                        },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val actionTargetId = if (controller.tabOverviewMode == TabOverviewMode.Hero) {
                    controller.activeTabs.getOrNull(pagerState.currentPage)?.id
                } else {
                    controller.selectedTabId
                }
                val chromeEnabled = destinationChromeVisible &&
                    dismissingTabId == null &&
                    movingTabId == null &&
                    exitHero == null &&
                    reorderAnimation == null &&
                    tabActionsTabId == null
                Surface(
                    modifier = Modifier
                        .width(AddressBarMotion.OVERVIEW_WIDTH)
                        .height(56.dp)
                        .testTag(TabOverviewChromeTestTags.Bar)
                        .graphicsLayer {
                            alpha = if (destinationChromeVisible) 1f else 0f
                        }
                        .then(
                            if (destinationChromeVisible) {
                                Modifier
                            } else {
                                Modifier.clearAndSetSemantics { }
                            },
                        ),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    tonalElevation = 12.dp,
                    shadowElevation = 14.dp,
                ) {
                    OverviewAddressBarContent(
                        onNewTab = onNewTab,
                        onMore = {
                            actionTargetId?.let { tabId ->
                                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                tabActionsTabId = tabId
                            }
                        },
                        enabled = chromeEnabled,
                    )
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
                modifier = if (initialTab.isIncognito) {
                    Modifier.graphicsLayer {
                        alpha = TabOverviewHeroRules.incognitoVeilAlpha(
                            heroProgress.value,
                        )
                    }
                } else {
                    Modifier
                },
            ) {
                when (controller.tabOverviewMode) {
                    TabOverviewMode.List -> TabListHeroContent(
                        tab = initialTab,
                        preview = heroPreview,
                        favicon = heroFavicon,
                        favorites = controller.favorites,
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
                        favorites = controller.favorites,
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
                        favorites = controller.favorites,
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
                        favorites = controller.favorites,
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
                        favorites = controller.favorites,
                        targetBounds = hero.startBounds,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { 1f - exitHeroProgress.value },
                    )
                } else if (heroTab?.url == BLANK_URL) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = null,
                        favicon = null,
                        favorites = controller.favorites,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                    )
                } else if (preview != null && !preview.isRecycled && heroTab != null) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = preview,
                        favicon = controller.favicons[hero.tabId],
                        favorites = controller.favorites,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                    )
                } else if (hero.isIncognito && heroTab != null) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = null,
                        favicon = null,
                        favorites = controller.favorites,
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

        candyTrailTransition.AnimatedContent(
            transitionSpec = {
                val transform = if (targetState != null) {
                    slideInHorizontally(
                        initialOffsetX = { width ->
                            PredictiveBackMotion.entryTranslation(
                                progress = 0f,
                                width = width.toFloat(),
                            ).roundToInt()
                        },
                        animationSpec = tween(
                            durationMillis = PredictiveBackMotion.ENTRY_DURATION_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith ExitTransition.None
                } else if (candyTrailPredictiveBackCommitted) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    EnterTransition.None togetherWith slideOutHorizontally(
                        targetOffsetX = { width -> width },
                        animationSpec = tween(
                            durationMillis = PredictiveBackMotion.EXIT_DURATION_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                transform.using(SizeTransform(clip = false))
            },
            contentKey = { it ?: "closed" },
        ) { presentedTabId ->
            val candyTrailTab = presentedTabId?.let { tabId ->
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
                    predictiveBackProgress = candyTrailBackProgress,
                    predictiveBackEdgeSign = candyTrailBackEdgeSign,
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
        }

        val actionTab = tabActionsTabId?.let { tabId ->
            controller.activeTabs.firstOrNull { it.id == tabId }
        }
        TabActionsFloatingMenu(
            tab = actionTab,
            profiles = controller.profiles,
            isFavorite = actionTab?.let { tab -> controller.isFavorite(tab.url) } == true,
            canToggleDomainMute = actionTab?.let { tab ->
                controller.canToggleDomainMute(tab.id)
            } == true,
            isDomainMuted = actionTab?.let { tab ->
                controller.isDomainMuted(tab.id)
            } == true,
            onToggleFavorite = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                onToggleFavoriteTab(target.id)
            },
            onOpenCandyTrail = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                val bounds = tabCardBounds[target.id]
                tabActionsTabId = null
                onOpenCandyTrail(target.id, bounds)
            },
            onTogglePinned = {
                val target = actionTab ?: return@TabActionsFloatingMenu
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
                val target = actionTab ?: return@TabActionsFloatingMenu
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
            onShare = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.sharePage(target.id)
            },
            onOpenExternal = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.openPageExternally(target.id)
            },
            onPrint = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.printPage(target.id)
            },
            onDomainMutedChange = { muted ->
                val target = actionTab ?: return@TabActionsFloatingMenu
                if (controller.setDomainMuted(target.id, muted)) {
                    rootView.performConfirmHaptic()
                }
            },
            onAddSiteCapsule = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                onAddSiteCapsule(target.id)
            },
            onSummarize = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.summarizePageWithAssistant(target.id)
            },
            onSnooze = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                onSnoozeTab(target.id)
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

internal object ProfileSwitcherTestTags {
    const val Switcher = "profile_switcher"
    const val Add = "profile_switcher_add"

    fun profile(profileId: String): String = "profile_switcher_profile:$profileId"
}

@Composable
internal fun ProfileSwitcher(
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
        val barWidth = (profileContentWidth + PROFILE_ACTION_SECTION_WIDTH.dp)
            .coerceAtMost(maxWidth - 24.dp)
            .coerceAtLeast(PROFILE_SWITCHER_MIN_WIDTH.dp)
        val profileViewportWidth = barWidth - PROFILE_ACTION_SECTION_WIDTH.dp
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
                .testTag(ProfileSwitcherTestTags.Switcher)
                .width(barWidth)
                .height(60.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
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
                        Row(modifier = Modifier.fillMaxHeight()) {
                            profiles.forEach { profile ->
                                val isSelected = profile.id == activeProfileId
                                val profileContainerColor by animateColorAsState(
                                    targetValue = when {
                                        !enabled -> MaterialTheme.colorScheme
                                            .surfaceContainerHighest
                                            .copy(alpha = 0.38f)
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    animationSpec = tween(durationMillis = 180),
                                    label = "profile-container-color",
                                )
                                val profileElevation by animateDpAsState(
                                    targetValue = if (isSelected) 2.dp else 0.dp,
                                    animationSpec = tween(durationMillis = 180),
                                    label = "profile-container-elevation",
                                )
                                val profileContainerSize by animateDpAsState(
                                    targetValue = if (isSelected) 48.dp else 44.dp,
                                    animationSpec = spring(
                                        dampingRatio = 0.72f,
                                        stiffness = 430f,
                                    ),
                                    label = "profile-container-size",
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.02f else 0.92f,
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
                                        .testTag(
                                            ProfileSwitcherTestTags.profile(profile.id),
                                        )
                                        .semantics {
                                            contentDescription =
                                                "$profileDescription ${profile.emoji}"
                                            selected = isSelected
                                        }
                                        .clip(CircleShape)
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
                                    Surface(
                                        modifier = Modifier.size(profileContainerSize),
                                        shape = CircleShape,
                                        color = profileContainerColor,
                                        tonalElevation = profileElevation,
                                        shadowElevation = profileElevation,
                                    ) {}
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
                                                alpha = if (enabled) 1f else 0.38f
                                            },
                                            fontSize = 25.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        x = (indicatorSlotOffset + 2.dp).roundToPx(),
                                        y = 0,
                                    )
                                }
                                .size(48.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(5.dp))
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = if (enabled) 0.62f else 0.22f,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f)
                        },
                        tonalElevation = 1.dp,
                    ) {}
                    IconButton(
                        onClick = onAdd,
                        enabled = enabled,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag(ProfileSwitcherTestTags.Add),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person_add_outline),
                            contentDescription = stringResource(R.string.cd_add_profile),
                            tint = if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val PROFILE_SLOT_WIDTH = 52
private const val PROFILE_ACTION_SECTION_WIDTH = 70
private const val PROFILE_SWITCHER_MIN_WIDTH = 122

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
    favorites: List<FavoriteEntry>,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
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
        if (tab.url == BLANK_URL && !tab.isIncognito) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
                blankFavoritesAlpha = {
                    TabOverviewHeroRules.blankFavoritesAlpha(targetFraction())
                },
            )
        } else {
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
                    favorites = favorites,
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
                TabPreviewContent(
                    tab = tab,
                    preview = preview,
                    favicon = favicon,
                    favorites = favorites,
                )
            }
        }
    }
}

@Composable
private fun TabListHeroContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
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
                favorites = favorites,
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
        } else if (tab.url == BLANK_URL) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground_art),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = Color.Unspecified,
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
    favorites: List<FavoriteEntry>,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(0.53f)
            .then(modifier)
            .clickable(
                onClick = onClick,
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
            TabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
            )
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
    favorites: List<FavoriteEntry>,
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
                    favorites = favorites,
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
                    modifier = Modifier
                        .animateItem()
                        .testTag(SnoozeTestTags.overviewTab(tab.id)),
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
    favorites: List<FavoriteEntry>,
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
    val dismissThreshold = cardWidthPx * TabDismissPhysics.CARD_DISMISS_THRESHOLD_FRACTION
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
            .clickable(
                enabled = interactionsEnabled,
                role = Role.Button,
                onClick = { boundsHolder.bounds?.let(onSelect) },
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
                TabPreviewContent(
                    tab = tab,
                    preview = preview,
                    favicon = favicon,
                    favorites = favorites,
                )
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
                modifier = Modifier
                    .animateItem()
                    .testTag(SnoozeTestTags.overviewTab(tab.id)),
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
            .clickable(
                enabled = interactionsEnabled,
                role = Role.Button,
                onClick = { boundsHolder.bounds?.let(onSelect) },
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
    } else if (tab.url == BLANK_URL) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground_art),
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = Color.Unspecified,
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
    favorites: List<FavoriteEntry> = emptyList(),
) {
    when {
        tab.isIncognito -> IncognitoTabPlaceholder()
        tab.url == BLANK_URL -> BlankTabPreview(
            favorites = favorites,
            favoritesAlpha = { 0f },
        )
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

internal object TabActionsMenuMotion {
    const val ENTER_DURATION_MILLIS = 120
    const val FADE_IN_DURATION_MILLIS = 30
    const val EXIT_DURATION_MILLIS = 160
    const val CLOSED_SCALE = 0.8f
    const val EXIT_SCALE = 0.9f
}

private data class TabActionsMenuPresentation(
    val tab: BrowserTab,
    val isFavorite: Boolean,
    val canToggleDomainMute: Boolean,
    val isDomainMuted: Boolean,
)

@Composable
internal fun TabActionsFloatingMenu(
    tab: BrowserTab?,
    profiles: List<BrowserProfile>,
    isFavorite: Boolean,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onTogglePinned: () -> Unit,
    onMoveToProfile: (String) -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onPrint: () -> Unit,
    onDomainMutedChange: (Boolean) -> Unit,
    onAddSiteCapsule: () -> Unit,
    onSummarize: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = tab != null, onBack = onDismiss)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val menuWidth = minOf(360.dp, screenWidth - 32.dp)
    val menuPaneTitle = stringResource(R.string.tab_actions_title)
    val menuTransformOrigin = if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        TransformOrigin(1f, 1f)
    } else {
        TransformOrigin(0f, 1f)
    }
    val requestedPresentation = tab?.let { presentedTab ->
        TabActionsMenuPresentation(
            tab = presentedTab,
            isFavorite = isFavorite,
            canToggleDomainMute = canToggleDomainMute,
            isDomainMuted = isDomainMuted,
        )
    }
    var presentation by remember { mutableStateOf(requestedPresentation) }
    if (requestedPresentation != null && requestedPresentation != presentation) {
        presentation = requestedPresentation
    }
    val visibilityState = remember { MutableTransitionState(tab != null) }
    visibilityState.targetState = tab != null
    LaunchedEffect(visibilityState.isIdle, visibilityState.currentState, tab) {
        if (visibilityState.isIdle && !visibilityState.currentState && tab == null) {
            presentation = null
        }
    }
    val presented = presentation
    if (presented != null && (visibilityState.currentState || visibilityState.targetState)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .zIndex(40f),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        role = Role.Button,
                        onClick = onDismiss,
                    )
                    .clearAndSetSemantics { },
            )
            AnimatedVisibility(
                visibleState = visibilityState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 82.dp),
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.FADE_IN_DURATION_MILLIS,
                    ),
                ) + scaleIn(
                    initialScale = TabActionsMenuMotion.CLOSED_SCALE,
                    transformOrigin = menuTransformOrigin,
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.ENTER_DURATION_MILLIS,
                        easing = LinearOutSlowInEasing,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.EXIT_DURATION_MILLIS,
                        easing = FastOutLinearInEasing,
                    ),
                ) + scaleOut(
                    targetScale = TabActionsMenuMotion.EXIT_SCALE,
                    transformOrigin = menuTransformOrigin,
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.EXIT_DURATION_MILLIS,
                        easing = FastOutLinearInEasing,
                    ),
                ),
                label = "Tab actions menu visibility",
            ) {
                val presentedTab = presented.tab
                Surface(
                    modifier = Modifier
                        .width(menuWidth)
                        .heightIn(max = screenHeight * 0.68f)
                        .testTag(SnoozeTestTags.TabActions)
                        .semantics { paneTitle = menuPaneTitle },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        TabActionsMenuContent(
                            pageSubtitle = if (presentedTab.url == BLANK_URL) {
                                stringResource(R.string.new_tab_title)
                            } else {
                                AddressResolver.displayText(presentedTab.url)
                            },
                            canToggleFavorite = presentedTab.url != BLANK_URL &&
                                !presentedTab.isIncognito,
                            isFavorite = presented.isFavorite,
                            isPinned = presentedTab.isPinned,
                            canUsePageActions = presentedTab.url != BLANK_URL,
                            canToggleDomainMute = presented.canToggleDomainMute,
                            isDomainMuted = presented.isDomainMuted,
                            canAddSiteCapsule = presentedTab.url != BLANK_URL &&
                                !presentedTab.isIncognito &&
                                (presentedTab.url.startsWith("https://") ||
                                    presentedTab.url.startsWith("http://")),
                            canSnooze = !presentedTab.isIncognito,
                            onToggleFavorite = onToggleFavorite,
                            onTogglePinned = onTogglePinned,
                            onShare = onShare,
                            onOpenExternal = onOpenExternal,
                            onPrint = onPrint,
                            onDomainMutedChange = onDomainMutedChange,
                            onOpenCandyTrail = onOpenCandyTrail,
                            onAddSiteCapsule = onAddSiteCapsule,
                            onSummarize = onSummarize,
                            onSnooze = onSnooze,
                            profileContent = {
                                val targetProfiles = profiles.filter {
                                    it.id != presentedTab.profileId
                                }
                                if (targetProfiles.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.action_move_tab_to_profile),
                                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
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
                            },
                        )
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
    searchSuggestionProvider: SearchSuggestionProvider,
    tabOverviewMode: TabOverviewMode,
    dismissResistancePercent: Int,
    isTabButtonVisible: Boolean,
    isWebContentEdgeToEdgeEnabled: Boolean,
    blockedCount: Int,
    isDefaultBrowser: Boolean,
    siteCapsules: List<SiteCapsule>,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onTabButtonVisibleChanged: (Boolean) -> Unit,
    onWebContentEdgeToEdgeChanged: (Boolean) -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onFilterStudio: () -> Unit,
    onClearData: () -> Unit,
    onOpenLegalUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lifetimeMenuExpanded by remember { mutableStateOf(false) }
    var searchEngineMenuExpanded by remember { mutableStateOf(false) }
    var searchSuggestionMenuExpanded by remember { mutableStateOf(false) }
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
            Spacer(Modifier.height(12.dp))
            Box {
                SettingsChoice(
                    title = stringResource(R.string.settings_search_suggestions),
                    value = searchSuggestionProvider.displayName(),
                    expanded = searchSuggestionMenuExpanded,
                    onClick = { searchSuggestionMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = searchSuggestionMenuExpanded,
                    onDismissRequest = { searchSuggestionMenuExpanded = false },
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    SearchSuggestionProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName()) },
                            onClick = {
                                searchSuggestionMenuExpanded = false
                                onSearchSuggestionProviderChanged(provider)
                            },
                            trailingIcon = {
                                if (provider == searchSuggestionProvider) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(
                    if (searchSuggestionProvider == SearchSuggestionProvider.None) {
                        R.string.settings_search_suggestions_none_summary
                    } else {
                        R.string.settings_search_suggestions_summary
                    },
                ),
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
            SettingsSwitch(
                title = stringResource(R.string.settings_tab_button_title),
                subtitle = stringResource(R.string.settings_tab_button_subtitle),
                checked = isTabButtonVisible,
                onCheckedChange = onTabButtonVisibleChanged,
            )
            Spacer(Modifier.height(12.dp))
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
                onClick = onOpenDefaultBrowserSettings,
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
            Spacer(Modifier.height(12.dp))
            SettingsSwitch(
                title = stringResource(R.string.settings_edge_to_edge_title),
                subtitle = stringResource(R.string.settings_edge_to_edge_subtitle),
                checked = isWebContentEdgeToEdgeEnabled,
                onCheckedChange = onWebContentEdgeToEdgeChanged,
            )

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
            Surface(
                onClick = onPermissionRadar,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.permission_radar_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.permission_radar_settings_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        "◉",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = onClearData,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    stringResource(R.string.action_clear_browsing_data),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
private fun SearchSuggestionProvider.displayName(): String = when (this) {
    SearchSuggestionProvider.None -> stringResource(R.string.search_suggestion_provider_none)
    SearchSuggestionProvider.DuckDuckGo -> "DuckDuckGo"
    SearchSuggestionProvider.Brave -> "Brave Search"
    SearchSuggestionProvider.Ecosia -> "Ecosia"
    SearchSuggestionProvider.Qwant -> "Qwant"
    SearchSuggestionProvider.Startpage -> "Startpage"
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
private fun SettingsLink(
    title: String,
    subtitle: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
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
            if (leadingIcon != null) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) { leadingIcon() }
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
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
