@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

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
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Constraints
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
import dev.sk2andy.materialbrowser.browser.BrowserWebView
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.cast.CastUiState
import dev.sk2andy.materialbrowser.browser.CapsuleSaveResult
import dev.sk2andy.materialbrowser.browser.MAX_PROFILES
import dev.sk2andy.materialbrowser.browser.MAX_TABS
import dev.sk2andy.materialbrowser.browser.RootTabBackResult
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.UserScriptSaveOutcome
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionClient
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionRules
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.commands.AddressAiModeRules
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
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogRules
import dev.sk2andy.materialbrowser.browser.userscript.ToppingVerifier
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRunAt
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
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
import dev.sk2andy.materialbrowser.data.TabReorderingRules
import dev.sk2andy.materialbrowser.data.ToppingCatalogRefreshResult
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.reader.ReaderStudioSession
import dev.sk2andy.materialbrowser.reader.ReaderStudioSessionRules
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget
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

private data class ActiveTabReorder(
    val tabId: String,
    val mode: TabOverviewMode,
    val orderIds: List<String>,
    val sourceIndex: Int,
    val destinationIndex: Int,
    val allowedRange: IntRange,
    val sourceBounds: Rect,
    val slotBounds: Map<String, Rect>,
    val dragOffset: Offset = Offset.Zero,
    val autoScrollOffset: Offset = Offset.Zero,
    val heroEdgeStepping: Boolean = false,
    val lifted: Boolean = false,
    val settling: Boolean = false,
)

private enum class BrowserBackTarget {
    ReaderStudio,
    FilterStudio,
    SnoozedTabs,
    SettingsSubpage,
    Settings,
    AddressEditor,
    CandyTrail,
    TabOverview,
    WebHistory,
    RootTab,
}

@Composable
internal fun BrowserScreen(
    controller: BrowserController,
    castUiState: CastUiState = CastUiState(),
    onToggleCastPlayback: () -> Unit = {},
    onSeekCast: (Long) -> Unit = {},
    onCastVolumeChange: (Float) -> Unit = {},
    onDisconnectCast: () -> Unit = {},
    webViewVideoOnlyPresentation: Boolean = false,
    onTabOverviewPortraitLockChanged: (Boolean) -> Unit = {},
    onImportUserScript: () -> Unit = {},
) {
    val currentTabOverviewPortraitLockChanged by rememberUpdatedState(
        onTabOverviewPortraitLockChanged,
    )
    controller.activeSiteCapsule?.let { capsule ->
        LaunchedEffect(capsule.id) {
            currentTabOverviewPortraitLockChanged(false)
        }
        SiteCapsuleBrowserScreen(
            controller = controller,
            capsule = capsule,
            webViewVideoOnlyPresentation = webViewVideoOnlyPresentation,
        )
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var tabOverviewVisible by rememberSaveable { mutableStateOf(false) }
    var candyTrailTabId by rememberSaveable { mutableStateOf<String?>(null) }
    var candyTrailSourceBounds by remember { mutableStateOf<Rect?>(null) }
    var addressEditorVisible by remember { mutableStateOf(false) }
    val aiModeSelectedState = remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsDestination by rememberSaveable { mutableStateOf(SettingsDestination.Home) }
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
    var overviewExitHeroVisible by remember { mutableStateOf(false) }
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
    var webContentBlurTarget by remember { mutableStateOf<BlurTarget?>(null) }
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
    val addressBarMorphInFront = AddressBarOverviewGestureRules.isMorphInFront(
        tabOverviewVisible = tabOverviewVisible,
        destinationChromeVisible = overviewDestinationChromeVisible,
        exitHeroVisible = overviewExitHeroVisible,
    )
    val selectedTab = controller.selectedTab
    val selectedSiteState = controller.siteProtectionState(selectedTab.id)
    val selectedSiteHasHost = selectedSiteState.host != null
    val canToggleSelectedCookieBannerRemoval = selectedSiteHasHost &&
        controller.blockerSettings.hideCookieConsent &&
        !selectedSiteState.isPaused
    val visibleProfiles = if (controller.profilesEnabled) {
        controller.profiles.toList()
    } else {
        controller.profiles.take(1)
    }
    val visibleProfileIds = visibleProfiles.mapTo(hashSetOf(), BrowserProfile::id)
    val visibleSnoozedTabs = controller.snoozedTabs.filter {
        it.tab.profileId in visibleProfileIds
    }
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
                profiles = visibleProfiles,
                activeProfileId = controller.activeProfileId,
                profileIsolationSupported = controller.isProfileIsolationSupported,
                pinningSupported = controller.isCapsulePinningSupported,
                canCreate = controller.canCreateSiteCapsule,
                canCreateDedicatedProfile = controller.profilesEnabled &&
                    controller.profiles.size < MAX_PROFILES &&
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
            overviewExitHeroVisible = false
            tabOverviewOpening = true
            controller.prepareTabOverview prepared@{
                if (!tabOverviewOpening) return@prepared
                tabOverviewOpening = false
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    tabOverviewVisible = true
                }
            }
        }
    }
    val closeTabOverview = {
        overviewGestureSettleJob?.cancel()
        overviewMorphJob?.cancel()
        overviewGestureProgress.floatValue = 0f
        overviewMorphProgress.floatValue = 0f
        overviewEntryHeroCompleted = false
        overviewExitHeroVisible = false
        tabOverviewOpening = false
        tabOverviewVisible = false
    }
    val openAddressEditor: () -> Unit = {
        if (activeCommandExecutionId == null) {
            controller.refreshSelectedTabPreview {
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
    }
    fun createTabAndConfirm(isIncognito: Boolean, emitHaptic: Boolean): Boolean {
        val previousTabId = controller.selectedTabId
        val createdTabId = controller.createTab(isIncognito = isIncognito)
        if (createdTabId == previousTabId) return false
        if (emitHaptic) rootView.performConfirmHaptic()
        return true
    }
    val openNewTabAndEdit: () -> Unit = {
        val createAndEdit = {
            if (createTabAndConfirm(isIncognito = false, emitHaptic = true)) {
                addressValue = TextFieldValue()
                addressEditorVisible = true
                highlightedSuggestionIndex = -1
                addressFocusNonce++
            }
        }
        if (addressEditorVisible || tabOverviewVisible) {
            createAndEdit()
        } else {
            controller.refreshSelectedTabPreview(createAndEdit)
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
    val showAiModeToggle = addressEditorVisible && AddressAiModeRules.isToggleVisible(
        input = addressValue.text,
        searchEngine = controller.searchEngine,
        settingEnabled = controller.isAiModeToggleVisible,
    )
    LaunchedEffect(addressEditorVisible, selectedTab.id, showAiModeToggle) {
        if (!addressEditorVisible || !showAiModeToggle) {
            aiModeSelectedState.value = false
        }
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
                    settingsDestination = SettingsDestination.Home
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

    fun searchModeFor(input: String) = AddressAiModeRules.searchMode(
        toggleVisible = AddressAiModeRules.isToggleVisible(
            input = input,
            searchEngine = controller.searchEngine,
            settingEnabled = controller.isAiModeToggleVisible,
        ),
        toggleSelected = aiModeSelectedState.value,
    )

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
                controller.submitAddress(item.query, searchModeFor(item.query))
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
                controller.submitAddress(submission.input, searchModeFor(submission.input))
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
    LaunchedEffect(settingsVisible, settingsDestination) {
        if (settingsVisible && settingsDestination == SettingsDestination.ToppingCatalog) {
            controller.refreshToppingCatalog()
        }
    }


    val currentBackTarget by rememberUpdatedState(
        when {
            readerStudioSession != null -> BrowserBackTarget.ReaderStudio
            filterStudioVisible -> BrowserBackTarget.FilterStudio
            snoozedTabsVisible -> BrowserBackTarget.SnoozedTabs
            settingsVisible && settingsDestination != SettingsDestination.Home ->
                BrowserBackTarget.SettingsSubpage
            settingsVisible -> BrowserBackTarget.Settings
            addressEditorVisible -> BrowserBackTarget.AddressEditor
            candyTrailTabId != null -> BrowserBackTarget.CandyTrail
            tabOverviewVisible || tabOverviewOpening -> BrowserBackTarget.TabOverview
            selectedTab.canGoBack -> BrowserBackTarget.WebHistory
            else -> BrowserBackTarget.RootTab
        },
    )
    PredictiveBackHandler(enabled = true) { events ->
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
                BrowserBackTarget.SettingsSubpage -> {
                    settingsDestination = when (settingsDestination) {
                        SettingsDestination.ToppingCatalog -> SettingsDestination.Userscripts
                        else -> SettingsDestination.Home
                    }
                }
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
                BrowserBackTarget.RootTab -> {
                    if (controller.closeSelectedRootTab() == RootTabBackResult.ShowTabOverview) {
                        openTabOverview()
                    }
                }
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
        currentTabOverviewPortraitLockChanged(tabOverviewVisible)
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
    val showInteractiveBlankStart = addressEditorVisible &&
        selectedTab.url == BLANK_URL &&
        addressValue.text.isEmpty() &&
        !selectedTab.isIncognito &&
        controller.favorites.isNotEmpty()

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
            webViewVideoOnlyPresentation = webViewVideoOnlyPresentation,
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
            onSearch = if (showInteractiveBlankStart) {
                { addressEditorVisible = false }
            } else {
                openAddressEditor
            },
            onFavorite = { url ->
                addressEditorVisible = false
                controller.submitAddress(url)
            },
            blankTabModeProgress = blankTabModeProgress,
            blankTabModeRevealOrigin = blankTabModeRevealOrigin,
            onRetry = controller::retryFailedPage,
            onBlurTargetAttached = { target -> webContentBlurTarget = target },
            onBlurTargetReleased = { target ->
                if (webContentBlurTarget === target) webContentBlurTarget = null
            },
        )

        if (addressEditorVisible && !showInteractiveBlankStart) {
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
            showCastButton = controller.castMediaCandidate != null || castUiState.isConnected,
            tabCount = controller.activeTabs.size,
            userScriptMenuCommands = controller.selectedUserScriptMenuCommands,
            onUserScriptMenuCommand = controller::invokeUserScriptMenuCommand,
            commandFeedback = commandFeedback,
            blurTarget = webContentBlurTarget,
            blurSourceVisible = selectedTab.url != BLANK_URL && !tabOverviewVisible,
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
            showAiModeToggle = showAiModeToggle,
            aiModeSelected = aiModeSelectedState.value,
            onAiModeSelectedChange = { selected ->
                aiModeSelectedState.value = selected
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
            onDock = { controller.updateAddressBarDocked(true) },
            onRestoreDock = { controller.updateAddressBarDocked(false) },
            onTabDrag = { delta ->
                if (!addressEditorVisible && !tabOverviewVisible) {
                    if (browserDragOffset.floatValue == 0f && delta != 0f) {
                        controller.refreshSelectedTabPreviewBeforeDeparture()
                    }
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
                if (addressEditorVisible) {
                    addressEditorVisible = false
                    overviewGestureScope.launch {
                        withFrameNanos { }
                        withFrameNanos { }
                        openTabOverview()
                    }
                } else {
                    openTabOverview()
                }
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
            canToggleDesktopView = controller.canToggleSelectedDesktopView,
            isDesktopView = controller.isSelectedDesktopView,
            onDesktopViewChange = controller::setSelectedDesktopView,
            canToggleCookieBannerRemoval = canToggleSelectedCookieBannerRemoval,
            isCookieBannerRemovalEnabled = canToggleSelectedCookieBannerRemoval &&
                !selectedSiteState.cookieBannerRemovalDisabled,
            canToggleForceVerticalScrolling = selectedSiteHasHost,
            isForceVerticalScrollingEnabled = selectedSiteState.forceVerticalScrolling,
            canToggleForcePageZooming = selectedSiteHasHost,
            isForcePageZoomingEnabled = selectedSiteState.forcePageZooming,
            canToggleForceSafeArea = selectedSiteHasHost,
            isForceSafeAreaEnabled = selectedSiteState.forceSafeArea,
            onCookieBannerRemovalEnabledChange = { enabled ->
                if (controller.setCookieBannerRemovalDisabled(selectedTab.id, !enabled)) {
                    rootView.performConfirmHaptic()
                }
            },
            onForceVerticalScrollingChange = { enabled ->
                if (controller.setForceVerticalScrolling(selectedTab.id, enabled)) {
                    rootView.performConfirmHaptic()
                }
            },
            onForcePageZoomingChange = { enabled ->
                if (controller.setForcePageZooming(selectedTab.id, enabled)) {
                    rootView.performConfirmHaptic()
                }
            },
            onForceSafeAreaChange = { enabled ->
                if (controller.setForceSafeArea(selectedTab.id, enabled)) {
                    rootView.performConfirmHaptic()
                }
            },
            snoozedTabCount = visibleSnoozedTabs.size,
            onSnoozedTabs = {
                addressEditorVisible = false
                snoozedTabsVisible = true
            },
            onSettings = {
                addressEditorVisible = false
                settingsDestination = SettingsDestination.Home
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

        if (
            !selectedTab.isIncognito &&
            !tabOverviewVisible &&
            !addressEditorVisible &&
            !settingsVisible &&
            !webViewVideoOnlyPresentation
        ) {
            CastControls(
                state = castUiState,
                onTogglePlayback = onToggleCastPlayback,
                onSeek = onSeekCast,
                onVolumeChange = onCastVolumeChange,
                onDisconnect = onDisconnectCast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (controller.isAddressBarDocked) 12.dp else 88.dp,
                    )
                    .zIndex(10f),
            )
        }

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
            onExitHeroVisibilityChanged = { overviewExitHeroVisible = it },
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

        val toppingCatalogResult = controller.toppingCatalogResult
        val toppingCatalogScripts = controller.userScripts.toList()
        val busyToppingIds = controller.busyToppingIds.toSet()
        val toppingCatalogState = remember(
            toppingCatalogResult,
            controller.isToppingCatalogLoading,
            toppingCatalogScripts,
            busyToppingIds,
        ) {
            fun itemsFor(result: ToppingCatalogRefreshResult): List<ToppingCatalogItem> {
                val catalog = when (result) {
                    is ToppingCatalogRefreshResult.Fresh -> result.catalog
                    is ToppingCatalogRefreshResult.Cached -> result.catalog
                    is ToppingCatalogRefreshResult.Error -> return emptyList()
                }
                return catalog.toppings.map { entry ->
                    val installed = toppingCatalogScripts.firstOrNull { script ->
                        script.id == ToppingCatalogRules.stableScriptId(entry.id)
                    }
                    ToppingCatalogItem(
                        id = entry.id,
                        name = entry.name,
                        description = entry.description,
                        author = entry.author,
                        license = entry.license,
                        version = entry.version,
                        scopes = entry.matches,
                        installed = installed != null,
                        enabled = installed?.enabled == true,
                        updateAvailable = installed != null &&
                            ToppingVerifier.sha256(installed.source.toByteArray()) != entry.sha256,
                        busy = entry.id in busyToppingIds,
                    )
                }
            }

            when {
                controller.isToppingCatalogLoading || toppingCatalogResult == null -> {
                    ToppingCatalogUiState.Loading
                }
                toppingCatalogResult is ToppingCatalogRefreshResult.Fresh -> {
                    ToppingCatalogUiState.Content(itemsFor(toppingCatalogResult))
                }
                toppingCatalogResult is ToppingCatalogRefreshResult.Cached -> {
                    ToppingCatalogUiState.Cached(itemsFor(toppingCatalogResult))
                }
                else -> ToppingCatalogUiState.Error()
            }
        }

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
                destination = settingsDestination,
                appearanceSettings = controller.appearanceSettings,
                downloadSettings = controller.downloadSettings,
                externalDownloadManagers = controller.externalDownloadManagers,
                blockerSettings = controller.blockerSettings,
                inactiveTabLifetime = controller.inactiveTabLifetime,
                searchEngine = controller.searchEngine,
                isAiModeToggleVisible = controller.isAiModeToggleVisible,
                searchSuggestionProvider = controller.searchSuggestionProvider,
                tabOverviewMode = controller.tabOverviewMode,
                dismissResistancePercent = controller.dismissResistancePercent,
                profilesEnabled = controller.profilesEnabled,
                isTabButtonVisible = controller.isTabButtonVisible,
                isFullImmersiveModeEnabled = controller.isFullImmersiveModeEnabled,
                isScrollBarEnabled = controller.isScrollBarEnabled,
                isVideoAutoplayBlocked = controller.isVideoAutoplayBlocked,
                isVideoAutoplayBlockingSupported = controller.isVideoAutoplayBlockingSupported,
                blockedCount = selectedTab.blockedCount,
                isDefaultBrowser = controller.isDefaultBrowser,
                isUserScriptSupported = controller.isUserScriptSupported,
                siteCapsules = controller.siteCapsules.filter {
                    it.profileId in visibleProfileIds
                },
                userScripts = controller.userScripts.map { script ->
                    UserscriptUiItem(
                        id = script.id,
                        name = script.name,
                        source = script.source,
                        enabled = script.enabled,
                        runAtLabel = context.getString(
                            when (script.runAt) {
                                UserScriptRunAt.DocumentStart ->
                                    R.string.userscript_run_at_document_start
                                UserScriptRunAt.DocumentEnd ->
                                    R.string.userscript_run_at_document_end
                            },
                        ),
                        urlPatterns = script.matchPatterns + script.includePatterns,
                    )
                },
                toppingCatalogState = toppingCatalogState,
                onDestinationChanged = { settingsDestination = it },
                onAppearanceSettingsChanged = controller::updateAppearanceSettings,
                onDownloadSettingsChanged = controller::updateDownloadSettings,
                onBlockerSettingsChanged = controller::updateBlockerSettings,
                onInactiveTabLifetimeChanged = controller::updateInactiveTabLifetime,
                onSearchEngineChanged = controller::updateSearchEngine,
                onAiModeToggleVisibleChanged = controller::updateAiModeToggleVisible,
                onSearchSuggestionProviderChanged = controller::updateSearchSuggestionProvider,
                onTabOverviewModeChanged = controller::updateTabOverviewMode,
                onDismissResistancePercentChanged = controller::updateDismissResistancePercent,
                onProfilesEnabledChanged = controller::updateProfilesEnabled,
                onTabButtonVisibleChanged = controller::updateTabButtonVisible,
                onFullImmersiveModeEnabledChanged =
                    controller::updateFullImmersiveModeEnabled,
                onScrollBarEnabledChanged = controller::updateScrollBarEnabled,
                onVideoAutoplayBlockedChanged = controller::updateVideoAutoplayBlocked,
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
                onToggleUserScript = { id, enabled, onResult ->
                    controller.setUserScriptEnabled(id, enabled) { saved ->
                        onResult(
                            if (saved) null
                            else context.getString(R.string.userscript_error_generic),
                        )
                    }
                },
                onSaveUserScript = { id, source, onResult ->
                    controller.saveUserScript(id, source) { outcome ->
                        onResult(
                            when (outcome) {
                                UserScriptSaveOutcome.Saved -> null
                                UserScriptSaveOutcome.LimitReached -> context.getString(
                                    R.string.userscript_error_limit,
                                    UserScriptParser.MAX_SCRIPTS,
                                )
                                UserScriptSaveOutcome.Missing,
                                UserScriptSaveOutcome.PersistenceFailed,
                                is UserScriptSaveOutcome.Rejected,
                                is UserScriptSaveOutcome.DependencyFailed,
                                -> context.getString(R.string.userscript_error_generic)
                            },
                        )
                    }
                },
                onDeleteUserScript = { id, onResult ->
                    controller.deleteUserScript(id) { deleted ->
                        onResult(
                            if (deleted) null
                            else context.getString(R.string.userscript_error_generic),
                        )
                    }
                },
                onImportUserScript = onImportUserScript,
                onToggleTopping = { id, enabled ->
                    controller.setToppingEnabled(id, enabled) { saved ->
                        if (!saved) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.topping_catalog_action_error),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onUpdateTopping = { id ->
                    controller.updateTopping(id) { saved ->
                        if (!saved) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.topping_catalog_action_error),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onRefreshToppingCatalog = controller::refreshToppingCatalog,
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

        controller.pendingDownloadChoice?.let { choice ->
            DownloadManagerChooserDialog(
                choice = choice,
                onSelect = controller::confirmDownloadChoice,
                onDismiss = controller::dismissDownloadChoice,
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
                profiles = visibleProfiles,
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
                snoozedTabs = visibleSnoozedTabs,
                profiles = visibleProfiles,
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
            NewTabPage(
                favorites = controller.favorites,
                incognito = selectedTab.isIncognito,
                modeProgress = blankTabModeProgress,
                revealOriginInRoot = blankTabModeRevealOrigin,
                onSearch = onSearch,
                onFavorite = onFavorite,
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
                .then(if (interactive) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(vertical = BlankTabModeMorphRules.HERO_SHADOW_CLEARANCE_DP.dp),
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
                shadowElevation = BlankTabModeMorphRules.HERO_SHADOW_ELEVATION_DP.dp,
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
    showCastButton: Boolean,
    tabCount: Int,
    userScriptMenuCommands: List<UserScriptMenuCommand>,
    onUserScriptMenuCommand: (UserScriptMenuCommand) -> Unit,
    commandFeedback: AddressCommandFeedback?,
    blurTarget: BlurTarget?,
    blurSourceVisible: Boolean,
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
    showAiModeToggle: Boolean,
    aiModeSelected: Boolean,
    onAiModeSelectedChange: (Boolean) -> Unit,
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
    canToggleDesktopView: Boolean,
    isDesktopView: Boolean,
    onDesktopViewChange: (Boolean) -> Unit,
    canToggleCookieBannerRemoval: Boolean,
    isCookieBannerRemovalEnabled: Boolean,
    canToggleForceVerticalScrolling: Boolean,
    isForceVerticalScrollingEnabled: Boolean,
    canToggleForcePageZooming: Boolean,
    isForcePageZoomingEnabled: Boolean,
    canToggleForceSafeArea: Boolean,
    isForceSafeAreaEnabled: Boolean,
    onCookieBannerRemovalEnabledChange: (Boolean) -> Unit,
    onForceVerticalScrollingChange: (Boolean) -> Unit,
    onForcePageZoomingChange: (Boolean) -> Unit,
    onForceSafeAreaChange: (Boolean) -> Unit,
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
    val chromeTokens = browserChromeSurfaceTokens(BrowserChromeSurfaceRole.AddressBar)
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
        ).size.width.toDp() + 84.dp + if (showCastButton) 48.dp else 0.dp
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
            null -> chromeTokens.containerColor
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
            BrowserChromeSurface(
                blurTarget = blurTarget,
                tokens = chromeTokens,
                modifier = Modifier
                    .offset(x = dockOffset)
                    .width(animatedBarWidth)
                    .height(animatedBarHeight)
                    .graphicsLayer {
                        scaleX = pulseScale.value
                        scaleY = pulseScale.value
                    },
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = barColor,
                backdropBlurEnabled = commandFeedback == null &&
                    blurSourceVisible &&
                    !menuExpanded &&
                    chromeTokens.backdropBlurEnabled,
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
                                        if (showCastButton) {
                                            CastRouteButton()
                                        }
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
                                blurTarget = blurTarget.takeIf { blurSourceVisible },
                                showTabButton = showTabButton,
                                showCastButton = showCastButton,
                                tabCount = tabCount,
                                userScriptMenuCommands = userScriptMenuCommands,
                                onUserScriptMenuCommand = onUserScriptMenuCommand,
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
                                showAiModeToggle = showAiModeToggle,
                                aiModeSelected = aiModeSelected,
                                onAiModeSelectedChange = onAiModeSelectedChange,
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
                                canToggleDesktopView = canToggleDesktopView,
                                isDesktopView = isDesktopView,
                                onDesktopViewChange = onDesktopViewChange,
                                canToggleCookieBannerRemoval =
                                    canToggleCookieBannerRemoval,
                                isCookieBannerRemovalEnabled =
                                    isCookieBannerRemovalEnabled,
                                canToggleForceVerticalScrolling =
                                    canToggleForceVerticalScrolling,
                                isForceVerticalScrollingEnabled =
                                    isForceVerticalScrollingEnabled,
                                canToggleForcePageZooming = canToggleForcePageZooming,
                                isForcePageZoomingEnabled = isForcePageZoomingEnabled,
                                canToggleForceSafeArea = canToggleForceSafeArea,
                                isForceSafeAreaEnabled = isForceSafeAreaEnabled,
                                onCookieBannerRemovalEnabledChange =
                                    onCookieBannerRemovalEnabledChange,
                                onForceVerticalScrollingChange =
                                    onForceVerticalScrollingChange,
                                onForcePageZoomingChange = onForcePageZoomingChange,
                                onForceSafeAreaChange = onForceSafeAreaChange,
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
                                onDock = onDock,
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
                            sourceCornerRadiusPx = with(density) {
                                chromeTokens.cornerRadius.toPx()
                            },
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
    const val Root = "tab_overview_root"
    const val HeroPager = "tab_overview_hero_pager"
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
    blurTarget: BlurTarget?,
    showTabButton: Boolean,
    showCastButton: Boolean,
    tabCount: Int,
    userScriptMenuCommands: List<UserScriptMenuCommand>,
    onUserScriptMenuCommand: (UserScriptMenuCommand) -> Unit,
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
    showAiModeToggle: Boolean,
    aiModeSelected: Boolean,
    onAiModeSelectedChange: (Boolean) -> Unit,
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
    canToggleDesktopView: Boolean,
    isDesktopView: Boolean,
    onDesktopViewChange: (Boolean) -> Unit,
    canToggleCookieBannerRemoval: Boolean,
    isCookieBannerRemovalEnabled: Boolean,
    canToggleForceVerticalScrolling: Boolean,
    isForceVerticalScrollingEnabled: Boolean,
    canToggleForcePageZooming: Boolean,
    isForcePageZoomingEnabled: Boolean,
    canToggleForceSafeArea: Boolean,
    isForceSafeAreaEnabled: Boolean,
    onCookieBannerRemovalEnabledChange: (Boolean) -> Unit,
    onForceVerticalScrollingChange: (Boolean) -> Unit,
    onForcePageZoomingChange: (Boolean) -> Unit,
    onForceSafeAreaChange: (Boolean) -> Unit,
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
    onDock: () -> Unit,
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
                shape = MaterialTheme.shapes.extraLarge,
                color = browserChromeColor(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    frostedAlpha = 0.22f,
                    role = BrowserChromeSurfaceRole.AddressBar,
                ),
            ) {
                Box {
                    if (editing) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                                    modifier = Modifier.padding(
                                        start = 8.dp,
                                        top = 10.dp,
                                        bottom = 10.dp,
                                    ),
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
                        if (showAiModeToggle) {
                            AddressAiModeToggle(
                                selected = aiModeSelected,
                                onSelectedChange = onAiModeSelectedChange,
                            )
                        }
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
                    if (!editing && showCastButton) {
                        CastRouteButton()
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
                                blurTarget = blurTarget,
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
                                canToggleDesktopView = canToggleDesktopView,
                                isDesktopView = isDesktopView,
                                canToggleCookieBannerRemoval =
                                    canToggleCookieBannerRemoval,
                                isCookieBannerRemovalEnabled =
                                    isCookieBannerRemovalEnabled,
                                canToggleForceVerticalScrolling =
                                    canToggleForceVerticalScrolling,
                                isForceVerticalScrollingEnabled =
                                    isForceVerticalScrollingEnabled,
                                canToggleForcePageZooming = canToggleForcePageZooming,
                                isForcePageZoomingEnabled = isForcePageZoomingEnabled,
                                canToggleForceSafeArea = canToggleForceSafeArea,
                                isForceSafeAreaEnabled = isForceSafeAreaEnabled,
                                canAddSiteCapsule = tab.url != BLANK_URL &&
                                    !tab.isIncognito &&
                                    (
                                        tab.url.startsWith("https://") ||
                                            tab.url.startsWith("http://")
                                        ),
                                canSnooze = !tab.isIncognito,
                                snoozedTabCount = snoozedTabCount,
                                userScriptMenuCommands = userScriptMenuCommands,
                                onUserScriptMenuCommand = onUserScriptMenuCommand,
                                onBack = onBack,
                                onForward = onForward,
                                onReloadOrStop = { if (tab.isLoading) onStop() else onReload() },
                                onToggleFavorite = onToggleFavorite,
                                onShare = onShare,
                                onOpenExternal = onOpenExternal,
                                onPrint = onPrint,
                                onOpenReader = onReaderStudio,
                                onDomainMutedChange = onDomainMutedChange,
                                onDesktopViewChange = onDesktopViewChange,
                                onCookieBannerRemovalEnabledChange =
                                    onCookieBannerRemovalEnabledChange,
                                onForceVerticalScrollingChange =
                                    onForceVerticalScrollingChange,
                                onForcePageZoomingChange = onForcePageZoomingChange,
                                onForceSafeAreaChange = onForceSafeAreaChange,
                                onOpenCandyTrail = onOpenCandyTrail,
                                onAddSiteCapsule = onAddSiteCapsule,
                                onSummarize = onSummarizeWithAssistant,
                                onSnooze = onSnooze,
                                onSnoozedTabs = onSnoozedTabs,
                                onDockAddressBar = onDock,
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
internal fun AddressAiModeToggle(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(160),
        label = "Address AI mode container color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(160),
        label = "Address AI mode content color",
    )
    IconToggleButton(
        checked = selected,
        onCheckedChange = onSelectedChange,
        modifier = modifier
            .size(48.dp)
            .shadow(
                elevation = if (selected) 6.dp else 0.dp,
                shape = CircleShape,
            )
            .background(containerColor, CircleShape)
            .testTag(AddressBarTestTags.AiModeToggle),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_symbol_auto_awesome),
            contentDescription = stringResource(
                if (selected) {
                    R.string.cd_disable_google_ai_mode
                } else {
                    R.string.cd_enable_google_ai_mode
                },
            ),
            tint = contentColor,
        )
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
    const val AiModeToggle = "address_bar_ai_mode_toggle"
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
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
        shape = MaterialTheme.shapes.extraLarge,
        color = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            frostedAlpha = 0.9f,
        ),
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
    onExitHeroVisibilityChanged: (Boolean) -> Unit,
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
    val currentOnExitHeroVisibilityChanged by rememberUpdatedState(
        onExitHeroVisibilityChanged,
    )
    fun updateExitHero(hero: TabExitHero?) {
        exitHero = hero
        currentOnExitHeroVisibilityChanged(hero != null)
    }
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
    var activeTabReorder by remember { mutableStateOf<ActiveTabReorder?>(null) }
    var heroReorderDropAnimating by remember { mutableStateOf(false) }
    var tabReorderSettleJob by remember { mutableStateOf<Job?>(null) }
    val reorderProgress = remember { Animatable(1f) }
    val moveProgress = remember { Animatable(0f) }
    val tabCardBounds = remember { mutableStateMapOf<String, Rect>() }
    val tabReorderBounds = remember { mutableStateMapOf<String, Rect>() }
    var overviewRootBounds by remember { mutableStateOf<Rect?>(null) }
    val profileSwitchProgress = remember { Animatable(1f) }
    val tabFocusHapticEvents = remember {
        Channel<Unit>(
            capacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    val exitHeroProgress = remember { Animatable(0f) }
    DisposableEffect(Unit) {
        onDispose { currentOnExitHeroVisibilityChanged(false) }
    }
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
            activeTabReorder != null ||
            tabActionsTabId != null
        ) {
            return
        }
        val mode = controller.tabOverviewMode
        val preview = controller.previews[tab.id]
            ?.takeIf { !tab.isIncognito && !it.isRecycled }
        updateExitHero(
            TabExitHero(
                tabId = tab.id,
                preview = preview,
                startBounds = bounds,
                isIncognito = tab.isIncognito,
                startCornerRadius = cornerRadius,
                previewTopInsetPx = controller.previewTopInsetPx(tab.id),
                mode = mode,
            ),
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
                updateExitHero(null)
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
            tabReorderSettleJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            rootView.stopRubberbandHaptic()
            tabFocusHapticEvents.close()
            activeTabReorder = null
            heroReorderDropAnimating = false
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
            profileSwitching ||
            activeTabReorder != null
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
            .onGloballyPositioned { overviewRootBounds = it.boundsInRoot() }
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
        val coverflowCardLayout = TabOverviewHeroRules.coverflowCardLayout(
            viewportWidth = maxWidth.value,
            viewportHeight = maxHeight.value,
        )
        val tabCardWidth = coverflowCardLayout.width.dp
        val pageSlotWidth = tabCardWidth + 12.dp
        val pageSlotWidthPx = with(density) { pageSlotWidth.toPx() }
        val pageHorizontalPadding = ((maxWidth - pageSlotWidth) / 2).coerceAtLeast(0.dp)
        val gridLayout = TabOverviewGridRules.layout(
            viewportWidth = maxWidth.value,
            viewportHeight = maxHeight.value,
        )
        val gridColumnPitchPx = with(density) { gridLayout.columnPitch.dp.toPx() }
        val gridRowPitchPx = with(density) { gridLayout.rowPitch.dp.toPx() }
        val listRowPitchPx = with(density) { 72.dp.toPx() }
        val heroPagerTopOverflow = TAB_OVERVIEW_TOP_SPACING +
            if (controller.profilesEnabled) {
                PROFILE_SWITCHER_LAYOUT_HEIGHT + TAB_OVERVIEW_PROFILE_SPACING
            } else {
                0.dp
            }

        fun reorderSlotOffset(
            reorder: ActiveTabReorder,
            sourceIndex: Int,
            destinationIndex: Int,
        ): Offset {
            val slotOffset = when (reorder.mode) {
                TabOverviewMode.Hero -> Offset(
                    x = (destinationIndex - sourceIndex) * pageSlotWidthPx,
                    y = 0f,
                )
                TabOverviewMode.Grid -> {
                    val sourceRow = sourceIndex / gridLayout.columnCount
                    val sourceColumn = sourceIndex % gridLayout.columnCount
                    val destinationRow = destinationIndex / gridLayout.columnCount
                    val destinationColumn = destinationIndex % gridLayout.columnCount
                    Offset(
                        x = (destinationColumn - sourceColumn) * gridColumnPitchPx,
                        y = (destinationRow - sourceRow) * gridRowPitchPx,
                    )
                }
                TabOverviewMode.List -> Offset(
                    x = 0f,
                    y = (destinationIndex - sourceIndex) * listRowPitchPx,
                )
            }
            return if (sourceIndex == reorder.sourceIndex) {
                slotOffset - reorder.autoScrollOffset
            } else {
                slotOffset
            }
        }

        fun reorderTranslation(tabId: String): Offset {
            val reorder = activeTabReorder ?: return Offset.Zero
            val index = reorder.orderIds.indexOf(tabId)
            if (index < 0) return Offset.Zero
            if (index == reorder.sourceIndex) return Offset.Zero
            val shiftedIndex = TabReorderMotion.shiftedIndex(
                index = index,
                sourceIndex = reorder.sourceIndex,
                destinationIndex = reorder.destinationIndex,
            )
            return reorderSlotOffset(reorder, index, shiftedIndex)
        }

        fun startTabReorder(tab: BrowserTab, bounds: Rect) {
            if (
                activeTabReorder != null ||
                dismissingTabId != null ||
                movingTabId != null ||
                exitHero != null ||
                reorderAnimation != null ||
                heroReorderDropAnimating ||
                tabActionsTabId != null
            ) {
                return
            }
            val tabs = controller.activeTabs
            val sourceIndex = tabs.indexOfFirst { it.id == tab.id }
            val allowedRange = TabReorderingRules.destinationRange(tabs, tab.id) ?: return
            if (sourceIndex < 0 || allowedRange.first == allowedRange.last) return
            tabReorderSettleJob?.cancel()
            activeTabReorder = ActiveTabReorder(
                tabId = tab.id,
                mode = controller.tabOverviewMode,
                orderIds = tabs.map(BrowserTab::id),
                sourceIndex = sourceIndex,
                destinationIndex = sourceIndex,
                allowedRange = allowedRange,
                sourceBounds = bounds,
                slotBounds = tabReorderBounds.toMap() + (tab.id to bounds),
            )
            rootView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        fun requestedReorderDestination(
            reorder: ActiveTabReorder,
            dragOffset: Offset,
        ): Int {
            if (reorder.mode == TabOverviewMode.Hero) {
                return TabReorderMotion.heroDestinationIndexForDrag(
                    sourceIndex = reorder.sourceIndex,
                    currentDestinationIndex = reorder.destinationIndex,
                    edgeStepping = reorder.heroEdgeStepping,
                    dragOffsetPx = dragOffset.x,
                    viewportOffsetPx = reorder.autoScrollOffset.x,
                    slotWidthPx = pageSlotWidthPx,
                    allowedRange = reorder.allowedRange,
                )
            }
            if (reorder.autoScrollOffset != Offset.Zero) {
                val contentOffset = dragOffset + reorder.autoScrollOffset
                return when (reorder.mode) {
                    TabOverviewMode.Grid -> TabReorderMotion.gridDestinationIndex(
                        sourceIndex = reorder.sourceIndex,
                        dragOffsetPx = contentOffset,
                        columnPitchPx = gridColumnPitchPx,
                        rowPitchPx = gridRowPitchPx,
                        columnCount = gridLayout.columnCount,
                        allowedRange = reorder.allowedRange,
                    )
                    TabOverviewMode.List -> TabReorderMotion.horizontalDestinationIndex(
                        sourceIndex = reorder.sourceIndex,
                        dragOffsetPx = contentOffset.y,
                        slotWidthPx = listRowPitchPx,
                        allowedRange = reorder.allowedRange,
                    )
                    TabOverviewMode.Hero -> reorder.destinationIndex
                }
            }
            val projectedCenter = reorder.sourceBounds.center + dragOffset
            val visibleCandidate = reorder.allowedRange
                .mapNotNull { index ->
                    val candidateId = reorder.orderIds.getOrNull(index)
                    val candidateCenter = candidateId
                        ?.let { tabId -> tabReorderBounds[tabId] ?: reorder.slotBounds[tabId] }
                        ?.center
                        ?: return@mapNotNull null
                    val delta = candidateCenter - projectedCenter
                    val distanceSquared = delta.x * delta.x + delta.y * delta.y
                    index to distanceSquared
                }
                .minByOrNull { (_, distanceSquared) -> distanceSquared }
                ?.first
            return visibleCandidate ?: reorder.destinationIndex
        }

        fun updateReorderDestination(reorder: ActiveTabReorder, dragOffset: Offset) {
            val requestedIndex = requestedReorderDestination(reorder, dragOffset)
            if (requestedIndex != reorder.destinationIndex) {
                rootView.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            }
            activeTabReorder = reorder.copy(
                destinationIndex = requestedIndex,
                dragOffset = dragOffset,
            )
        }

        fun updateTabReorder(dragAmount: Offset) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling) ?: return
            updateReorderDestination(reorder, reorder.dragOffset + dragAmount)
        }

        fun advanceVerticalTabReorderAutoScroll(consumed: Float) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling) ?: return
            updateReorderDestination(
                reorder = reorder.copy(
                    autoScrollOffset = reorder.autoScrollOffset + Offset(0f, consumed),
                ),
                dragOffset = reorder.dragOffset,
            )
        }

        fun finishTabReorder(commit: Boolean) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling) ?: return
            val destinationIndex = if (commit) {
                reorder.destinationIndex
            } else {
                reorder.sourceIndex
            }
            val heroAnchorIndex = if (reorder.mode == TabOverviewMode.Hero) {
                TabReorderMotion.heroPagerAnchorIndex(
                    sourceIndex = reorder.sourceIndex,
                    destinationIndex = destinationIndex,
                )
            } else {
                null
            }
            val settling = reorder.copy(
                destinationIndex = destinationIndex,
                autoScrollOffset = heroAnchorIndex?.let { anchorIndex ->
                    Offset(
                        x = (anchorIndex - reorder.sourceIndex) * pageSlotWidthPx,
                        y = 0f,
                    )
                } ?: reorder.autoScrollOffset,
                lifted = false,
                settling = true,
            )
            activeTabReorder = settling
            tabReorderSettleJob?.cancel()
            tabReorderSettleJob = overviewScope.launch {
                try {
                    if (
                        heroAnchorIndex != null &&
                        (
                            pagerState.currentPage != heroAnchorIndex ||
                                pagerState.currentPageOffsetFraction.absoluteValue > 0.001f
                        )
                    ) {
                        pagerState.animateScrollToPage(
                            page = heroAnchorIndex,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 900f),
                        )
                    }
                    val targetOffset = reorderSlotOffset(
                        reorder = settling,
                        sourceIndex = settling.sourceIndex,
                        destinationIndex = destinationIndex,
                    )
                    Animatable(settling.dragOffset, Offset.VectorConverter).animateTo(
                        targetValue = targetOffset,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 680f),
                    ) {
                        activeTabReorder = activeTabReorder
                            ?.takeIf { it.tabId == settling.tabId }
                            ?.copy(dragOffset = value)
                    }
                    val orderUnchanged = controller.activeTabs.map(BrowserTab::id) == settling.orderIds
                    val animateHeroDrop = commit &&
                        orderUnchanged &&
                        settling.mode == TabOverviewMode.Hero &&
                        destinationIndex != settling.sourceIndex
                    if (animateHeroDrop) {
                        // Switch from stable tab keys to position keys before mutating the list.
                        // Otherwise Pager follows the moved key to its new index in one frame.
                        heroReorderDropAnimating = true
                        withFrameNanos { }
                    }
                    val changed = commit && orderUnchanged && controller.reorderTab(
                        tabId = settling.tabId,
                        destinationIndex = destinationIndex,
                    )
                    activeTabReorder = null
                    if (changed && animateHeroDrop) {
                        withFrameNanos { }
                        pagerState.animateScrollToPage(
                            page = destinationIndex,
                            animationSpec = spring(
                                dampingRatio = 0.84f,
                                stiffness = 430f,
                            ),
                        )
                    }
                    if (changed) rootView.performConfirmHaptic()
                } finally {
                    if (activeTabReorder?.tabId == settling.tabId) {
                        activeTabReorder = null
                    }
                    heroReorderDropAnimating = false
                }
            }
        }

        val heroReorder = activeTabReorder?.takeIf {
            it.mode == TabOverviewMode.Hero && !it.settling
        }
        LaunchedEffect(activeTabReorder?.tabId) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling)
                ?: return@LaunchedEffect
            withFrameNanos { }
            activeTabReorder
                ?.takeIf { it.tabId == reorder.tabId && !it.settling }
                ?.let { current -> activeTabReorder = current.copy(lifted = true) }
        }
        HeroTabReorderEdgeAutoScroll(
            sessionId = heroReorder?.tabId,
            pointerInRoot = heroReorder?.let { it.sourceBounds.center + it.dragOffset },
            viewportBounds = overviewRootBounds,
            canStepBackward = (heroReorder?.destinationIndex ?: 0) >
                (heroReorder?.allowedRange?.first ?: 0),
            canStepForward = (heroReorder?.destinationIndex ?: 0) <
                (heroReorder?.allowedRange?.last ?: 0),
            onStep = { direction ->
                val current = activeTabReorder
                    ?.takeIf { it.mode == TabOverviewMode.Hero && !it.settling }
                    ?: return@HeroTabReorderEdgeAutoScroll false
                val destinationIndex = (current.destinationIndex + direction)
                    .coerceIn(current.allowedRange.first, current.allowedRange.last)
                if (destinationIndex == current.destinationIndex) {
                    return@HeroTabReorderEdgeAutoScroll false
                }
                val anchorIndex = TabReorderMotion.heroPagerAnchorIndex(
                    sourceIndex = current.sourceIndex,
                    destinationIndex = destinationIndex,
                )
                activeTabReorder = current.copy(
                    destinationIndex = destinationIndex,
                    heroEdgeStepping = true,
                    autoScrollOffset = Offset(
                        x = (anchorIndex - current.sourceIndex) * pageSlotWidthPx,
                        y = 0f,
                    ),
                )
                rootView.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                pagerState.animateScrollToPage(
                    page = anchorIndex,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 1_350f),
                )
                true
            },
        )

        LaunchedEffect(visible, controller.tabOverviewMode, initialTabId) {
            if (!visible) {
                heroProgress.snapTo(0f)
                exitHeroProgress.snapTo(0f)
                heroStarted = false
                heroCompleted = false
                heroVisible = true
                updateExitHero(null)
                tabReorderSettleJob?.cancel()
                activeTabReorder = null
                heroReorderDropAnimating = false
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
                .then(if (visible) Modifier.testTag(TabOverviewChromeTestTags.Root) else Modifier)
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
                .longPressTabOverviewReorder(
                    enabled = visible &&
                        heroCompleted &&
                        !heroVisible &&
                        dismissingTabId == null &&
                        movingTabId == null &&
                        !profileSwitching &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        !heroReorderDropAnimating &&
                        tabActionsTabId == null,
                    sessionKey = Triple(
                        controller.activeProfileId,
                        controller.tabOverviewMode,
                        controller.activeTabs.map(BrowserTab::id),
                    ),
                    onDragStart = { position ->
                        val positionInRoot = position +
                            (overviewRootBounds?.topLeft ?: Offset.Zero)
                        val target = controller.activeTabs
                            .asSequence()
                            .filter { tab -> TabReorderingRules.canMove(controller.activeTabs, tab.id) }
                            .mapNotNull { tab ->
                                tabReorderBounds[tab.id]
                                    ?.takeIf { bounds -> bounds.contains(positionInRoot) }
                                    ?.let { bounds -> tab to bounds }
                            }
                            .minByOrNull { (_, bounds) ->
                                val delta = bounds.center - positionInRoot
                                delta.x * delta.x + delta.y * delta.y
                            }
                        if (target == null) {
                            false
                        } else {
                            startTabReorder(target.first, target.second)
                            activeTabReorder?.tabId == target.first.id
                        }
                    },
                    onDrag = ::updateTabReorder,
                    onDragEnd = { finishTabReorder(commit = true) },
                    onDragCancel = { finishTabReorder(commit = false) },
                )
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
            Spacer(Modifier.height(TAB_OVERVIEW_TOP_SPACING))
            if (controller.profilesEnabled) {
                ProfileSwitcher(
                    profiles = controller.profiles,
                    activeProfileId = controller.activeProfileId,
                    enabled = dismissingTabId == null &&
                        movingTabId == null &&
                        !profileSwitching &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        !heroReorderDropAnimating &&
                        activeTabReorder == null &&
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
                Spacer(Modifier.height(TAB_OVERVIEW_PROFILE_SPACING))
            }
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
                    }
                    .allowTopOverflow(heroPagerTopOverflow)
                    .testTag(TabOverviewChromeTestTags.HeroPager),
                contentPadding = PaddingValues(
                    start = pageHorizontalPadding,
                    top = heroPagerTopOverflow + HERO_PAGER_VERTICAL_PADDING,
                    end = pageHorizontalPadding,
                    bottom = HERO_PAGER_VERTICAL_PADDING,
                ),
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
                    !heroReorderDropAnimating &&
                    activeTabReorder == null &&
                    tabActionsTabId == null,
                key = { page ->
                    if (reorderAnimation == null && !heroReorderDropAnimating) {
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
                DisposableEffect(tab.id) {
                    onDispose {
                        if (tabCardBounds[tab.id] == cardBounds) {
                            tabCardBounds.remove(tab.id)
                        }
                        if (tabReorderBounds[tab.id] == cardBounds) {
                            tabReorderBounds.remove(tab.id)
                        }
                    }
                }
                val dismissThreshold = with(density) {
                    tabCardWidth.toPx() * TabDismissPhysics.CARD_DISMISS_THRESHOLD_FRACTION
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
                                activeTabReorder?.tabId == tab.id -> 6f
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
                        }
                        .tabReorderVisualMotion(
                            sessionId = activeTabReorder?.tabId,
                            isDragged = activeTabReorder?.tabId == tab.id,
                            targetOffset = reorderTranslation(tab.id),
                        ),
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
                                    activeTabReorder == null &&
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
                            .padding(horizontal = 4.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.width(tabCardWidth)) {
                            TabCard(
                                tab = tab,
                                preview = controller.previews[tab.id],
                                favicon = controller.favicons[tab.id],
                                favorites = controller.favorites,
                                cardWidth = tabCardWidth,
                                cardAspectRatio = coverflowCardLayout.aspectRatio,
                                modifier = Modifier
                                    .testTag(SnoozeTestTags.overviewTab(tab.id))
                                    .graphicsLayer {
                                        alpha = if (realCardVisible) 1f else 0f
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        val bounds = coordinates.boundsInRoot()
                                        cardBounds = bounds
                                        tabCardBounds[tab.id] = bounds
                                        tabReorderBounds[tab.id] = bounds
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
                            TabTitleRow(
                                tab = tab,
                                favicon = controller.favicons[tab.id],
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                alpha = {
                                    if (isInitialCard) {
                                        ((heroProgress.value - 0.72f) / 0.28f).coerceIn(0f, 1f)
                                    } else {
                                        1f
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 12.dp, top = 4.dp, end = 12.dp),
                            )
                        }
                    }
                }
                }
                TabOverviewMode.Grid -> CompactTabGrid(
                    layout = gridLayout,
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
                        activeTabReorder == null &&
                        tabActionsTabId == null,
                    reorderSessionId = activeTabReorder?.tabId,
                    reorderDraggedTabId = activeTabReorder?.tabId,
                    reorderTranslation = ::reorderTranslation,
                    reorderPointerInRoot = activeTabReorder
                        ?.takeUnless(ActiveTabReorder::settling)
                        ?.let {
                        it.sourceBounds.center + it.dragOffset
                    },
                    reorderCanScrollBackward = activeTabReorder?.let {
                        it.destinationIndex > it.allowedRange.first
                    } ?: false,
                    reorderCanScrollForward = activeTabReorder?.let {
                        it.destinationIndex < it.allowedRange.last
                    } ?: false,
                    onReorderAutoScroll = { consumed ->
                        advanceVerticalTabReorderAutoScroll(consumed)
                    },
                    onReorderBounds = { tab, bounds -> tabReorderBounds[tab.id] = bounds },
                    onReorderBoundsDisposed = { tab, bounds ->
                        if (tabReorderBounds[tab.id] == bounds) tabReorderBounds.remove(tab.id)
                    },
                    onPreviewBounds = { tab, bounds ->
                        tabCardBounds[tab.id] = bounds
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.Grid
                            heroTargetTabId = tab.id
                        }
                    },
                    onPreviewBoundsDisposed = { tab, bounds ->
                        if (tabCardBounds[tab.id] == bounds) tabCardBounds.remove(tab.id)
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
                        activeTabReorder == null &&
                        tabActionsTabId == null,
                    reorderSessionId = activeTabReorder?.tabId,
                    reorderDraggedTabId = activeTabReorder?.tabId,
                    reorderTranslation = ::reorderTranslation,
                    reorderPointerInRoot = activeTabReorder
                        ?.takeUnless(ActiveTabReorder::settling)
                        ?.let {
                        it.sourceBounds.center + it.dragOffset
                    },
                    reorderCanScrollBackward = activeTabReorder?.let {
                        it.destinationIndex > it.allowedRange.first
                    } ?: false,
                    reorderCanScrollForward = activeTabReorder?.let {
                        it.destinationIndex < it.allowedRange.last
                    } ?: false,
                    onReorderAutoScroll = { consumed ->
                        advanceVerticalTabReorderAutoScroll(consumed)
                    },
                    onRowBounds = { tab, bounds ->
                        tabCardBounds[tab.id] = bounds
                        tabReorderBounds[tab.id] = bounds
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.List
                            heroTargetTabId = tab.id
                        }
                    },
                    onRowBoundsDisposed = { tab, bounds ->
                        if (tabCardBounds[tab.id] == bounds) tabCardBounds.remove(tab.id)
                        if (tabReorderBounds[tab.id] == bounds) tabReorderBounds.remove(tab.id)
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
                    !heroReorderDropAnimating &&
                    activeTabReorder == null &&
                    tabActionsTabId == null
                val overviewChromeTokens = browserChromeSurfaceTokens(
                    BrowserChromeSurfaceRole.AddressBar,
                )
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
                    shape = MaterialTheme.shapes.extraLarge,
                    color = overviewChromeTokens.containerColor,
                    tonalElevation = overviewChromeTokens.tonalElevation,
                    shadowElevation = overviewChromeTokens.shadowElevation,
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

        activeTabReorder?.let { reorder ->
            controller.activeTabs.firstOrNull { it.id == reorder.tabId }?.let { tab ->
                DraggedTabReorderOverlay(
                    reorder = reorder,
                    tab = tab,
                    preview = controller.previews[tab.id],
                    favicon = controller.favicons[tab.id],
                    favorites = controller.favorites,
                    selected = tab.id == controller.selectedTabId,
                    rootTopLeft = overviewRootBounds?.topLeft ?: Offset.Zero,
                )
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
                    .statusBarsPadding()
                    .padding(
                        top = TAB_OVERVIEW_TOP_SPACING +
                            PROFILE_SWITCHER_LAYOUT_HEIGHT +
                            TAB_OVERVIEW_PROFILE_SPACING,
                    )
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
                            updateExitHero(
                                TabExitHero(
                                    candyTrailTab.id,
                                    preview,
                                    bounds,
                                    candyTrailTab.isIncognito,
                                    previewTopInsetPx = controller.previewTopInsetPx(
                                        candyTrailTab.id,
                                    ),
                                    mode = controller.tabOverviewMode,
                                ),
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
                                    onSelect(candyTrailTab.id)
                                    onClose()
                                } finally {
                                    updateExitHero(null)
                                }
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
            profiles = if (controller.profilesEnabled) {
                controller.profiles
            } else {
                controller.profiles.take(1)
            },
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
            .height(PROFILE_SWITCHER_LAYOUT_HEIGHT),
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
            shape = MaterialTheme.shapes.extraLarge,
            color = browserChromeColor(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                frostedAlpha = 0.88f,
            ),
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

private fun Modifier.allowTopOverflow(topOverflow: Dp): Modifier = layout { measurable, constraints ->
    val overflowPx = topOverflow.roundToPx().coerceAtLeast(0)
    if (overflowPx == 0 || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
    val expandedHeight = (constraints.maxHeight.toLong() + overflowPx)
        .coerceAtMost(Constraints.Infinity.toLong())
        .toInt()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = expandedHeight,
            maxHeight = expandedHeight,
        ),
    )
    layout(
        width = placeable.width,
        height = constraints.maxHeight,
    ) {
        placeable.placeRelative(0, -overflowPx)
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
    val previewLayout = TabOverviewHeroRules.coverflowPreviewLayout(
        rootWidthPx = rootWidthPx,
        rootHeightPx = rootHeightPx,
        targetWidthPx = targetBounds.width,
        targetHeightPx = targetBounds.height,
        cropTopFraction = PREVIEW_CROP_TOP_FRACTION,
    )
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
        } else if (tab.isIncognito) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
            )
        } else {
            Layout(
                content = {
                    TabPreviewContent(
                        tab = tab,
                        preview = preview,
                        favicon = favicon,
                        favorites = favorites,
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .clipToBounds(),
            ) { measurables, constraints ->
                val capturedHeightPx = preview
                    ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
                    ?.let { bitmap -> rootWidthPx * bitmap.height / bitmap.width }
                val startLayout = TabSwitchPreviewLayoutRules.resolve(
                    rootHeightPx = rootHeightPx,
                    previewTopInsetPx = previewTopInsetPx,
                    bottomBarTopPx = bottomBarTopPx.floatValue,
                    capturedHeightPx = capturedHeightPx,
                )
                val frame = TabOverviewHeroRules.coverflowPreviewFrame(
                    startTopPx = startLayout.topInsetPx,
                    startHeightPx = startLayout.visibleHeightPx,
                    targetLayout = previewLayout,
                    targetFraction = targetFraction(),
                )
                val frameHeight = frame.sourceHeightPx
                    .roundToInt()
                    .coerceAtLeast(1)
                val previewPlaceable = measurables.single().measure(
                    Constraints.fixed(
                        width = constraints.maxWidth,
                        height = frameHeight,
                    ),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    previewPlaceable.placeRelative(
                        x = 0,
                        y = frame.sourceTopPx.roundToInt(),
                    )
                }
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
    Surface(
        modifier = modifier.graphicsLayer { this.alpha = alpha() },
        shape = MaterialTheme.shapes.extraLarge,
        color = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            frostedAlpha = 0.88f,
        ),
        contentColor = contentColor,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab.isIncognito) {
                Icon(
                    painter = painterResource(R.drawable.ic_incognito_outline),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            } else if (tab.url == BLANK_URL) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.Unspecified,
                )
            } else if (favicon != null && !favicon.isRecycled) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            displayTabTitle(tab).take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                displayTabTitle(tab),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
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
}

@Composable
private fun displayTabTitle(tab: BrowserTab): String =
    if (tab.url == BLANK_URL || tab.title.isBlank()) {
        stringResource(R.string.new_tab_title)
    } else {
        tab.title
    }

private fun Modifier.longPressTabOverviewReorder(
    enabled: Boolean,
    sessionKey: Any,
    onDragStart: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(enabled, sessionKey) {
    if (!enabled) return@pointerInput
    var accepted = false
    detectDragGesturesAfterLongPress(
        onDragStart = { position -> accepted = onDragStart(position) },
        onDragEnd = {
            if (accepted) onDragEnd()
            accepted = false
        },
        onDragCancel = {
            if (accepted) onDragCancel()
            accepted = false
        },
        onDrag = { change, dragAmount ->
            if (accepted) {
                change.consume()
                onDrag(dragAmount)
            }
        },
    )
}

@Composable
private fun HeroTabReorderEdgeAutoScroll(
    sessionId: String?,
    pointerInRoot: Offset?,
    viewportBounds: Rect?,
    canStepBackward: Boolean,
    canStepForward: Boolean,
    onStep: suspend (Int) -> Boolean,
) {
    val density = LocalDensity.current
    val edgeSizePx = with(density) { 72.dp.toPx() }
    val maxSpeedPxPerSecond = with(density) { 1_100.dp.toPx() }
    val currentPointer by rememberUpdatedState(pointerInRoot)
    val currentBounds by rememberUpdatedState(viewportBounds)
    val currentCanStepBackward by rememberUpdatedState(canStepBackward)
    val currentCanStepForward by rememberUpdatedState(canStepForward)
    val currentOnStep by rememberUpdatedState(onStep)
    LaunchedEffect(sessionId) {
        if (sessionId == null) return@LaunchedEffect
        var activeDirection = 0
        var nextStepNanos = Long.MAX_VALUE
        while (true) {
            val frameNanos = withFrameNanos { it }
            val pointer = currentPointer
            val bounds = currentBounds
            val speed = if (pointer == null || bounds == null) {
                0f
            } else {
                TabReorderMotion.edgeScrollSpeed(
                    pointerPx = pointer.x,
                    viewportStartPx = bounds.left,
                    viewportEndPx = bounds.right,
                    edgeSizePx = edgeSizePx,
                    maxSpeedPxPerSecond = maxSpeedPxPerSecond,
                )
            }
            val direction = when {
                speed < 0f && currentCanStepBackward -> -1
                speed > 0f && currentCanStepForward -> 1
                else -> 0
            }
            if (direction == 0) {
                activeDirection = 0
                nextStepNanos = Long.MAX_VALUE
                continue
            }
            if (direction != activeDirection) {
                activeDirection = direction
                nextStepNanos = frameNanos + 180_000_000L
                continue
            }
            if (frameNanos < nextStepNanos) continue
            val moved = currentOnStep(direction)
            nextStepNanos = withFrameNanos { it } + if (moved) {
                24_000_000L
            } else {
                90_000_000L
            }
        }
    }
}

@Composable
private fun TabReorderEdgeAutoScroll(
    sessionId: String?,
    pointerInRoot: Offset?,
    viewportBounds: Rect?,
    orientation: Orientation,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    scrollBy: suspend (Float) -> Float,
    onScrolled: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val edgeSizePx = with(density) { 96.dp.toPx() }
    val maxSpeedPxPerSecond = with(density) { 1_800.dp.toPx() }
    val currentPointer by rememberUpdatedState(pointerInRoot)
    val currentBounds by rememberUpdatedState(viewportBounds)
    val currentCanScrollBackward by rememberUpdatedState(canScrollBackward)
    val currentCanScrollForward by rememberUpdatedState(canScrollForward)
    val currentScrollBy by rememberUpdatedState(scrollBy)
    val currentOnScrolled by rememberUpdatedState(onScrolled)
    LaunchedEffect(sessionId, orientation) {
        if (sessionId == null) return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f)
                .coerceIn(0f, 0.032f)
            previousFrameNanos = frameNanos
            val pointer = currentPointer ?: continue
            val bounds = currentBounds ?: continue
            val pointerAxis = if (orientation == Orientation.Horizontal) pointer.x else pointer.y
            val startAxis = if (orientation == Orientation.Horizontal) bounds.left else bounds.top
            val endAxis = if (orientation == Orientation.Horizontal) bounds.right else bounds.bottom
            var speed = TabReorderMotion.edgeScrollSpeed(
                pointerPx = pointerAxis,
                viewportStartPx = startAxis,
                viewportEndPx = endAxis,
                edgeSizePx = edgeSizePx,
                maxSpeedPxPerSecond = maxSpeedPxPerSecond,
            )
            if (speed < 0f && !currentCanScrollBackward) speed = 0f
            if (speed > 0f && !currentCanScrollForward) speed = 0f
            if (speed == 0f) continue
            val consumed = currentScrollBy(speed * elapsedSeconds)
            if (consumed.absoluteValue > 0.01f) currentOnScrolled(consumed)
        }
    }
}

@Composable
private fun Modifier.tabReorderVisualMotion(
    sessionId: String?,
    isDragged: Boolean,
    targetOffset: Offset,
): Modifier {
    val animatedOffset = key(sessionId) {
        animateValueAsState(
            targetValue = if (isDragged) Offset.Zero else targetOffset,
            typeConverter = Offset.VectorConverter,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
            label = "tab-reorder-slot",
        )
    }
    return graphicsLayer {
        val offset = if (isDragged) Offset.Zero else animatedOffset.value
        translationX = offset.x
        translationY = offset.y
        alpha = if (isDragged) 0f else 1f
    }
}

@Composable
private fun DraggedTabReorderOverlay(
    reorder: ActiveTabReorder,
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    selected: Boolean,
    rootTopLeft: Offset,
) {
    val density = LocalDensity.current
    val width = with(density) { reorder.sourceBounds.width.toDp() }
    val height = with(density) { reorder.sourceBounds.height.toDp() }
    val shape = when (reorder.mode) {
        TabOverviewMode.Hero -> RoundedCornerShape(28.dp)
        TabOverviewMode.Grid -> RoundedCornerShape(22.dp)
        TabOverviewMode.List -> RoundedCornerShape(18.dp)
    }
    val lift by animateFloatAsState(
        targetValue = if (reorder.lifted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "tab-reorder-overlay-lift",
    )
    val elevation = (2f + lift * 5f).dp
    Box(
        modifier = Modifier
            .offset {
                val topLeft = reorder.sourceBounds.topLeft + reorder.dragOffset - rootTopLeft
                IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
            }
            .size(width, height)
            .zIndex(30f)
            .graphicsLayer {
                val scale = 1f + lift * 0.018f
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
            )
            .clearAndSetSemantics { },
    ) {
        when (reorder.mode) {
            TabOverviewMode.Hero -> Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                TabPreviewContent(
                    tab = tab,
                    preview = preview,
                    favicon = favicon,
                    favorites = favorites,
                )
            }
            TabOverviewMode.Grid -> Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
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
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 15.dp)
                                    .size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 15.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
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
            TabOverviewMode.List -> Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f)
                },
            ) {
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
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 15.dp)
                                .size(20.dp),
                        )
                    } else {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 15.dp)
                                .size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    cardWidth: Dp,
    cardAspectRatio: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(cardAspectRatio)
            .then(modifier)
            .clickable(
                onClick = onClick,
                role = Role.Button,
            ),
        shape = MaterialTheme.shapes.extraLarge,
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
    layout: TabOverviewGridRules.Layout,
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
    reorderSessionId: String?,
    reorderDraggedTabId: String?,
    reorderTranslation: (String) -> Offset,
    reorderPointerInRoot: Offset?,
    reorderCanScrollBackward: Boolean,
    reorderCanScrollForward: Boolean,
    onReorderAutoScroll: (Float) -> Unit,
    onReorderBounds: (BrowserTab, Rect) -> Unit,
    onReorderBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    onPreviewBounds: (BrowserTab, Rect) -> Unit,
    onPreviewBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    onSelect: (BrowserTab, Rect) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    onSwipeDismissStart: (BrowserTab) -> Boolean,
    onSwipeDismissEnd: (BrowserTab) -> Unit,
    onSwipeDismiss: (BrowserTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(initialTabId, selectedTabId) {
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
    TabReorderEdgeAutoScroll(
        sessionId = reorderSessionId,
        pointerInRoot = reorderPointerInRoot,
        viewportBounds = gridBounds,
        orientation = Orientation.Vertical,
        canScrollBackward = gridState.canScrollBackward && reorderCanScrollBackward,
        canScrollForward = gridState.canScrollForward && reorderCanScrollForward,
        scrollBy = { delta -> gridState.scrollBy(delta) },
        onScrolled = onReorderAutoScroll,
    )
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
            columns = GridCells.Fixed(layout.columnCount),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = interactionsEnabled,
            contentPadding = PaddingValues(
                horizontal = layout.contentPadding.dp,
                vertical = 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(layout.itemSpacing.dp),
            verticalArrangement = Arrangement.spacedBy(layout.itemSpacing.dp),
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
                    previewAspectRatio = layout.previewAspectRatio,
                    revealDelayMillis = (index % 6) * 24L,
                    onReorderBounds = { bounds -> onReorderBounds(tab, bounds) },
                    onReorderBoundsDisposed = { bounds ->
                        onReorderBoundsDisposed(tab, bounds)
                    },
                    onPreviewBounds = { bounds -> onPreviewBounds(tab, bounds) },
                    onPreviewBoundsDisposed = { bounds ->
                        onPreviewBoundsDisposed(tab, bounds)
                    },
                    onSelect = { bounds -> onSelect(tab, bounds) },
                    onClose = { onCloseTab(tab) },
                    onSwipeDismissStart = { onSwipeDismissStart(tab) },
                    onSwipeDismissEnd = { onSwipeDismissEnd(tab) },
                    onSwipeDismiss = { onSwipeDismiss(tab) },
                    modifier = Modifier
                        .then(
                            if (reorderSessionId == null) Modifier.animateItem() else Modifier,
                        )
                        .zIndex(if (reorderDraggedTabId == tab.id) 6f else 0f)
                        .tabReorderVisualMotion(
                            sessionId = reorderSessionId,
                            isDragged = reorderDraggedTabId == tab.id,
                            targetOffset = reorderTranslation(tab.id),
                        )
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
    previewAspectRatio: Float,
    revealDelayMillis: Long,
    onReorderBounds: (Rect) -> Unit,
    onReorderBoundsDisposed: (Rect?) -> Unit,
    onPreviewBounds: (Rect) -> Unit,
    onPreviewBoundsDisposed: (Rect?) -> Unit,
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
    val reorderBoundsHolder = remember(tab.id) { TabBoundsHolder() }
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
            onPreviewBoundsDisposed(boundsHolder.bounds)
            onReorderBoundsDisposed(reorderBoundsHolder.bounds)
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
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                reorderBoundsHolder.bounds = bounds
                onReorderBounds(bounds)
            }
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
                    .aspectRatio(previewAspectRatio)
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
    reorderSessionId: String?,
    reorderDraggedTabId: String?,
    reorderTranslation: (String) -> Offset,
    reorderPointerInRoot: Offset?,
    reorderCanScrollBackward: Boolean,
    reorderCanScrollForward: Boolean,
    onReorderAutoScroll: (Float) -> Unit,
    onRowBounds: (BrowserTab, Rect) -> Unit,
    onRowBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    onSelect: (BrowserTab, Rect) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    TabReorderEdgeAutoScroll(
        sessionId = reorderSessionId,
        pointerInRoot = reorderPointerInRoot,
        viewportBounds = listBounds,
        orientation = Orientation.Vertical,
        canScrollBackward = listState.canScrollBackward && reorderCanScrollBackward,
        canScrollForward = listState.canScrollForward && reorderCanScrollForward,
        scrollBy = { delta -> listState.scrollBy(delta) },
        onScrolled = onReorderAutoScroll,
    )
    LaunchedEffect(initialTabId, selectedTabId) {
        if (tabs.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.scrollToItem(selectedIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { listBounds = it.boundsInRoot() },
        userScrollEnabled = interactionsEnabled,
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
                onBoundsDisposed = { bounds -> onRowBoundsDisposed(tab, bounds) },
                onSelect = { bounds -> onSelect(tab, bounds) },
                onClose = { onCloseTab(tab) },
                modifier = Modifier
                    .then(
                        if (reorderSessionId == null) Modifier.animateItem() else Modifier,
                    )
                    .zIndex(if (reorderDraggedTabId == tab.id) 6f else 0f)
                    .tabReorderVisualMotion(
                        sessionId = reorderSessionId,
                        isDragged = reorderDraggedTabId == tab.id,
                        targetOffset = reorderTranslation(tab.id),
                    )
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
    onBoundsDisposed: (Rect?) -> Unit,
    onSelect: (Rect) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boundsHolder = remember(tab.id) { TabBoundsHolder() }
    DisposableEffect(tab.id) {
        onDispose { onBoundsDisposed(boundsHolder.bounds) }
    }
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
    val chromeTokens = browserChromeSurfaceTokens().copy(
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    )
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
                BrowserChromeSurface(
                    blurTarget = null,
                    tokens = chromeTokens,
                    modifier = Modifier
                        .width(menuWidth)
                        .heightIn(max = screenHeight * 0.68f)
                        .testTag(SnoozeTestTags.TabActions)
                        .semantics { paneTitle = menuPaneTitle },
                    shape = MaterialTheme.shapes.large,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
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
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
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
private const val VIDEO_ONLY_WEB_VIEW_Z_INDEX = 100f
private val TAB_OVERVIEW_TOP_SPACING = 12.dp
private val TAB_OVERVIEW_PROFILE_SPACING = 4.dp
private val PROFILE_SWITCHER_LAYOUT_HEIGHT = 64.dp
private val HERO_PAGER_VERTICAL_PADDING = 4.dp
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
