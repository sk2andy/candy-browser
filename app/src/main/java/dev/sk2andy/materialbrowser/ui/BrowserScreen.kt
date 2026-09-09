@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.shared.ui.TabOverviewHeroRules

import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.FindInPageRules
import dev.sk2andy.materialbrowser.browser.cast.CastUiState
import dev.sk2andy.materialbrowser.browser.CapsuleSaveResult
import dev.sk2andy.materialbrowser.browser.MAX_PROFILES
import dev.sk2andy.materialbrowser.browser.MAX_TABS
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperEditorContract
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperEditorRequest
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import dev.sk2andy.materialbrowser.browser.wallpaperFor
import dev.sk2andy.materialbrowser.browser.RootTabBackResult
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionClient
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionRules
import dev.sk2andy.materialbrowser.browser.commands.AddressAiModeRules
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.browser.commands.AddressSubmission
import dev.sk2andy.materialbrowser.browser.commands.AddressSubmissionRules
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommand
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommandKind
import dev.sk2andy.materialbrowser.browser.commands.CommandActions
import dev.sk2andy.materialbrowser.browser.commands.CommandConfirmation
import dev.sk2andy.materialbrowser.browser.commands.CommandDispatchOutcome
import dev.sk2andy.materialbrowser.browser.commands.CommandDispatcher
import dev.sk2andy.materialbrowser.browser.commands.CommandMatcher
import dev.sk2andy.materialbrowser.browser.commands.CommandSuggestion
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.capsule.CapsuleIconMode
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleDraft
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorContract
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorRequest
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.reader.ReaderStudioSession
import dev.sk2andy.materialbrowser.reader.ReaderStudioSessionRules
import dev.sk2andy.materialbrowser.recall.RecallMatch
import eightbitlab.com.blurview.BlurTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import kotlin.coroutines.resume

private enum class BrowserBackTarget {
    ReaderStudio,
    FilterStudio,
    SnoozedTabs,
    SettingsSubpage,
    Settings,
    AddressEditor,
    FindInPage,
    CandyTrail,
    TabOverview,
    WebHistory,
    ExternalApp,
    RootTab,
}

private data class PendingLinkSnooze(
    val url: String,
    val title: String?,
    val sourceTabId: String,
) {
    fun displayTab(): BrowserTab = BrowserTab(
        id = "link-peek-snooze:$sourceTabId:${url.hashCode()}",
        lastAccessedAt = 0L,
        title = title.orEmpty(),
        url = url,
    )
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
    incomingBrowserNavigationRequestId: Int = 0,
    externalLaunchTabId: String? = null,
    onReturnToExternalApp: () -> Unit = {},
    onExternalPreviewCommitted: (String) -> Unit = {},
    onTabOverviewPortraitLockChanged: (Boolean) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onImportUserScript: () -> Unit = {},
    onExportAppData: () -> Unit = {},
    onImportAppData: () -> Unit = {},
    onOpenFirefoxExtensions: (() -> Unit)? = null,
    onManageFirefoxExtensions: (() -> Unit)? = onOpenFirefoxExtensions,
    openAddressEditorOnLaunch: Boolean = false,
    launcherAddressEditorRequestId: Int = 0,
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
    controller.externalLinkPreviewState?.let { state ->
        ExternalLinkPreviewScreen(
            controller = controller,
            state = state,
            onReturnToExternalApp = onReturnToExternalApp,
            onCommitted = onExternalPreviewCommitted,
            onTabOverviewPortraitLockChanged = currentTabOverviewPortraitLockChanged,
        )
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var tabOverviewVisible by rememberSaveable { mutableStateOf(false) }
    var candyTrailTabId by rememberSaveable { mutableStateOf<String?>(null) }
    var candyTrailSourceBounds by remember { mutableStateOf<Rect?>(null) }
    var addressEditorVisible by remember { mutableStateOf(openAddressEditorOnLaunch) }
    val aiModeSelectedState = remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsDestination by rememberSaveable { mutableStateOf(SettingsDestination.Home) }
    var snoozedTabsVisible by rememberSaveable { mutableStateOf(false) }
    var snoozeTabId by remember { mutableStateOf<String?>(null) }
    var pendingLinkSnooze by remember { mutableStateOf<PendingLinkSnooze?>(null) }
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
    var addressValue by remember {
        val initialAddress = controller.selectedTab.url
            .takeUnless { it == BLANK_URL }
            .orEmpty()
        mutableStateOf(
            if (openAddressEditorOnLaunch) {
                TextFieldValue(
                    text = initialAddress,
                    selection = TextRange(initialAddress.length, 0),
                )
            } else {
                TextFieldValue()
            },
        )
    }
    var remoteSearchSuggestions by remember { mutableStateOf(emptyList<String>()) }
    var localRecallMatches by remember { mutableStateOf(emptyList<RecallMatch>()) }
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
    var browserRootBottomInWindowPx by remember { mutableIntStateOf(0) }
    val bottomBarTopPx = remember { mutableFloatStateOf(Float.NaN) }
    var addressNewTabButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var keepLinkPeekAddressBarExpanded by remember { mutableStateOf(false) }
    var tabOverviewOpening by remember { mutableStateOf(false) }
    var tabHandoff by remember { mutableStateOf<TabHandoff?>(null) }
    var browserContentBlurTarget by remember { mutableStateOf<BlurTarget?>(null) }
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
    val activeLocalProfile = controller.localBrowserProfiles
        .firstOrNull { profile -> profile.id == controller.activeProfileId }
    val profileWallpaperRuntime = activeLocalProfile
        ?.newTabWallpaper
        ?.let { wallpaper ->
            controller.activeProfileWallpaperBitmap
                ?.takeIf { bitmap -> !bitmap.isRecycled }
                ?.asImageBitmap()
                ?.let { bitmap -> ProfileWallpaperRuntime(bitmap, wallpaper) }
        }
    val tabSwitcherWallpaperRuntime = activeLocalProfile
        ?.tabSwitcherWallpaper
        ?.let { wallpaper ->
            controller.activeProfileTabSwitcherWallpaperBitmap
                ?.takeIf { bitmap -> !bitmap.isRecycled }
                ?.asImageBitmap()
                ?.let { bitmap -> ProfileWallpaperRuntime(bitmap, wallpaper) }
        }
    val visibleProfiles = if (controller.profilesEnabled) {
        controller.localBrowserProfiles
    } else {
        controller.localBrowserProfiles.take(1)
    }
    val visibleProfileIds = visibleProfiles.mapTo(hashSetOf(), BrowserProfile::id)
    val visibleSnoozedTabs = controller.snoozedTabs.filter {
        it.tab.profileId in visibleProfileIds
    }
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
                iconEmoji = submission.iconEmoji,
                iconColor = submission.iconColor,
            ),
            sourceFavicon = submission.sourceFavicon,
            customIcon = submission.customIcon,
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
            CapsuleSaveResult.IconSaveFailed -> R.string.capsule_icon_save_failed
            CapsuleSaveResult.LimitReached -> R.string.capsule_limit_reached
            CapsuleSaveResult.Invalid -> R.string.capsule_invalid_configuration
        }
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
        if (result == CapsuleSaveResult.PinRequested || result == CapsuleSaveResult.Updated) {
            rootView.performConfirmHaptic()
        }
    }
    val profileWallpaperEditorLauncher = rememberLauncherForActivityResult(
        contract = ProfileWallpaperEditorContract(),
    ) { submission ->
        if (submission != null) {
            controller.updateProfileWallpaper(
                submission.profileId,
                submission.target,
                submission.wallpaper,
            )
        }
        controller.restoreActiveProfileWallpapersAfterEditing()
    }
    fun openProfileWallpaperEditor(profileId: String, wallpaperTarget: ProfileWallpaperTarget) {
        val profile = controller.localBrowserProfiles.firstOrNull { it.id == profileId } ?: return
        controller.releaseActiveProfileWallpaperForEditing(profileId)
        profileWallpaperEditorLauncher.launch(
            ProfileWallpaperEditorRequest(
                profileId = profile.id,
                target = wallpaperTarget,
                wallpaper = profile.wallpaperFor(wallpaperTarget),
            ),
        )
    }
    fun openSiteCapsuleEditor(existing: SiteCapsule?, sourceTab: BrowserTab?) {
        if (existing == null && sourceTab == null) return
        val sourceTitle = existing?.name ?: sourceTab?.title.orEmpty().ifBlank {
            sourceTab?.url?.let(AddressResolver::displayText).orEmpty()
        }
        val sourceUrl = existing?.startUrl ?: sourceTab?.url.orEmpty()
        val storedSourceIcon = existing?.let { capsule ->
            controller.siteCapsuleSourceIcon(capsule.id)
        }
        val storedCustomIcon = existing?.let { capsule ->
            controller.siteCapsuleCustomIcon(capsule.id)
        }
        val legacyRenderedIcon = existing
            ?.takeIf { capsule ->
                capsule.iconMode == CapsuleIconMode.Favicon && storedSourceIcon == null
            }
            ?.let { capsule -> controller.siteCapsuleRenderedIcon(capsule.id) }
        siteCapsuleEditorLauncher.launch(
            SiteCapsuleEditorRequest(
                existing = existing,
                sourceTabId = sourceTab?.id,
                sourceTitle = sourceTitle,
                sourceUrl = sourceUrl,
                profiles = visibleProfiles,
                activeProfileId = controller.activeProfileId.takeIf { id ->
                    visibleProfiles.any { it.id == id }
                } ?: visibleProfiles.first().id,
                profileIsolationSupported = controller.isProfileIsolationSupported,
                pinningSupported = controller.isCapsulePinningSupported,
                canCreate = controller.canCreateSiteCapsule,
                canCreateDedicatedProfile = controller.profilesEnabled &&
                    controller.localBrowserProfiles.size < MAX_PROFILES &&
                    controller.tabs.size < MAX_TABS,
                previewIcon = storedSourceIcon
                    ?: legacyRenderedIcon
                    ?: sourceTab?.let { controller.favicons[it.id] },
                previewIconIsRendered = legacyRenderedIcon != null,
                customIcon = storedCustomIcon,
            ),
        )
        rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val favoriteAddedMessage = stringResource(R.string.favorite_added_confirmation)
    val favoriteRemovedMessage = stringResource(R.string.favorite_removed_confirmation)
    val snoozeConfirmationMessage = stringResource(R.string.snooze_confirmation)
    val undoLabel = stringResource(R.string.action_undo)
    val showFavoriteMutation: (FavoriteMutation) -> Unit = { mutation ->
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
    val toggleFavoriteWithFeedback: (String) -> Unit = { tabId ->
        controller.toggleFavorite(tabId)?.let(showFavoriteMutation)
    }
    BrowserOfferSnackbarEffects(controller, feedbackSnackbarHostState)
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
            controller.loadActiveProfileTabSwitcherWallpaper wallpaperReady@{
                if (!tabOverviewOpening) return@wallpaperReady
                controller.prepareTabOverview prepared@{
                    if (!tabOverviewOpening) return@prepared
                    tabOverviewOpening = false
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        tabOverviewVisible = true
                    }
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
        controller.releaseActiveProfileTabSwitcherWallpaper()
    }
    LaunchedEffect(incomingBrowserNavigationRequestId) {
        if (incomingBrowserNavigationRequestId != 0) closeTabOverview()
    }
    val openAddressEditor: () -> Unit = {
        if (activeCommandExecutionId == null) {
            controller.closeFindInPage()
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
    LaunchedEffect(launcherAddressEditorRequestId) {
        if (launcherAddressEditorRequestId > 0) {
            closeTabOverview()
            settingsVisible = false
            openAddressEditor()
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
        controller.searxngSettings,
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
                searxngInstanceUrl = controller.searxngSettings.instanceUrl,
            )
        ) {
            return@LaunchedEffect
        }
        delay(SearchSuggestionRules.DEBOUNCE_MILLIS)
        remoteSearchSuggestions = searchSuggestionClient.suggestions(
            provider = controller.searchSuggestionProvider,
            query = addressValue.text.trim(),
            searxngSettings = controller.searxngSettings,
        )
    }
    LaunchedEffect(
        addressEditorVisible,
        addressValue.text,
        selectedTab.id,
        selectedTab.profileId,
        selectedTab.isIncognito,
        controller.isRecallEnabled,
        controller.isHistorySuggestionsEnabled,
    ) {
        localRecallMatches = emptyList()
        if (!addressEditorVisible) return@LaunchedEffect
        localRecallMatches = suspendCancellableCoroutine { continuation ->
            controller.searchRecallForAddress(addressValue.text) { matches ->
                if (continuation.isActive) continuation.resume(matches)
            }
        }
    }
    val suggestionItems by remember {
        derivedStateOf {
            if (addressEditorVisible) {
                controller.addressSuggestionItems(
                    query = addressValue.text,
                    searchQueries = remoteSearchSuggestions,
                    recallMatches = localRecallMatches,
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
        override fun clearCacheAndReload(onComplete: (Boolean) -> Unit): Boolean =
            controller.clearCacheAndReload(onComplete)
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
            is AddressSuggestionItem.Recall -> selectNavigation(
                AddressSuggestion(
                    url = item.match.url,
                    title = item.match.title,
                ),
            )
        }
    }

    fun fillAddressFromSuggestion(item: AddressSuggestionItem): Unit {
        val text = when (item) {
            is AddressSuggestionItem.Navigation -> item.suggestion.url
            is AddressSuggestionItem.Search -> item.query
            is AddressSuggestionItem.Recall -> item.match.url
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

    LaunchedEffect(
        addressEditorVisible,
        tabOverviewVisible,
        settingsVisible,
        readerStudioSession,
    ) {
        if (
            addressEditorVisible ||
            tabOverviewVisible ||
            settingsVisible ||
            readerStudioSession != null
        ) {
            controller.closeFindInPage()
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
            controller.findInPageState != null -> BrowserBackTarget.FindInPage
            candyTrailTabId != null -> BrowserBackTarget.CandyTrail
            tabOverviewVisible || tabOverviewOpening -> BrowserBackTarget.TabOverview
            selectedTab.canGoBack -> BrowserBackTarget.WebHistory
            selectedTab.id == externalLaunchTabId -> BrowserBackTarget.ExternalApp
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
                        SettingsDestination.AddressBarActions ->
                            SettingsDestination.TabsAndGestures
                        SettingsDestination.LinkPeekActions ->
                            SettingsDestination.TabsAndGestures
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
                BrowserBackTarget.FindInPage -> controller.closeFindInPage()
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
                BrowserBackTarget.ExternalApp -> onReturnToExternalApp()
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

    BrowserImeOwnershipEffect(controller, addressEditorVisible)
    val firefoxExtensionOptionsTitle = controller.selectedFirefoxExtensionOptionsTitle
    val showFirefoxExtensionOptionsChrome =
        firefoxExtensionOptionsTitle != null && !webViewVideoOnlyPresentation
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
            .onGloballyPositioned { coordinates ->
                browserRootBottomInWindowPx = coordinates.boundsInWindow().bottom.roundToInt()
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
        CompositionLocalProvider(LocalProfileWallpaper provides profileWallpaperRuntime) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showFirefoxExtensionOptionsChrome) {
                    FirefoxExtensionOptionsTopBar(
                        title = requireNotNull(firefoxExtensionOptionsTitle),
                        onBack = controller::goBack,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
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
                        onBlurTargetAttached = { target -> browserContentBlurTarget = target },
                        onBlurTargetReleased = { target ->
                            if (browserContentBlurTarget === target) browserContentBlurTarget = null
                        },
                    )
                }
            }
        }

        controller.findInPageState
            ?.takeIf { firefoxExtensionOptionsTitle == null }
            ?.let { findState ->
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
                backdropSource = browserContentBlurTarget.asCandyChromeBackdropSource(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .zIndex(25f),
            )
        }

        if (firefoxExtensionOptionsTitle == null) {
            BrowserAddressChrome(
            controller = controller,
            selectedTab = selectedTab,
            addressEditorVisible = addressEditorVisible,
            showInteractiveBlankStart = showInteractiveBlankStart,
            blankTabModeProgress = blankTabModeProgress,
            blankTabModeRevealOrigin = blankTabModeRevealOrigin,
            profileWallpaperRuntime = profileWallpaperRuntime,
            commandFeedback = commandFeedback,
            suggestionItems = suggestionItems,
            highlightedSuggestionIndex = highlightedSuggestionIndex,
            browserHeightPx = browserHeightPx,
            bottomBarTopPx = bottomBarTopPx,
            browserContentBlurTarget = browserContentBlurTarget,
            linkPeekAddressBarExpanded = linkPeekAddressBarExpanded,
            castUiState = castUiState,
            settingsVisible = settingsVisible,
            addressValue = addressValue,
            domainCompletion = domainCompletion,
            addressFocusNonce = addressFocusNonce,
            showAiModeToggle = showAiModeToggle,
            aiModeSelectedState = aiModeSelectedState,
            browserDragOffset = browserDragOffset,
            browserWidthPx = browserWidthPx,
            tabSwitchTravelPx = tabSwitchTravelPx,
            tabHandoffAlpha = tabHandoffAlpha,
            tabOverviewOpening = tabOverviewOpening,
            tabOverviewVisible = tabOverviewVisible,
            overviewGestureProgress = overviewGestureProgress,
            addressBarMorphInFront = addressBarMorphInFront,
            browserRootBottomInWindowPx = browserRootBottomInWindowPx,
            visibleSnoozedTabCount = visibleSnoozedTabs.size,
            webViewVideoOnlyPresentation = webViewVideoOnlyPresentation,
            onToggleCastPlayback = onToggleCastPlayback,
            onSeekCast = onSeekCast,
            onCastVolumeChange = onCastVolumeChange,
            onDisconnectCast = onDisconnectCast,
            openAddressEditor = openAddressEditor,
            onAddressEditorDismiss = { addressEditorVisible = false },
            onAddressValueChanged = { addressValue = it },
            onHighlightedSuggestionChanged = { highlightedSuggestionIndex = it },
            selectSuggestion = ::selectSuggestion,
            fillAddressFromSuggestion = ::fillAddressFromSuggestion,
            moveSuggestionHighlight = ::moveSuggestionHighlight,
            submitAddressOrCommand = ::submitAddressOrCommand,
            onLiveFrameCleared = { liveFrameTabId = null },
            onTabHandoffChanged = { tabHandoff = it },
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
            onOverviewGestureStarted = { overviewGestureSettleJob?.cancel() },
            onOverviewGestureCancelled = settleOverviewGesture,
            openNewTabAndEdit = openNewTabAndEdit,
            toggleFavoriteWithFeedback = toggleFavoriteWithFeedback,
            onBlankTabModeRevealOriginChanged = { blankTabModeRevealOrigin = it },
            onSnoozedTabs = { snoozedTabsVisible = true },
            onOpenHistory = onOpenHistory,
            onOpenFirefoxExtensions = onOpenFirefoxExtensions,
            onSettings = {
                settingsDestination = SettingsDestination.Home
                settingsVisible = true
            },
            onPrivacyXRay = { privacyXRayTabId = selectedTab.id },
            onPermissionRadar = {
                permissionRadarTabId = selectedTab.id
                permissionRadarOrigin = null
            },
            onNewTabButtonBoundsChanged = { addressNewTabButtonBounds = it },
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

        CompositionLocalProvider(LocalProfileWallpaper provides profileWallpaperRuntime) {
            TabOverview(
                controller = controller,
                backgroundWallpaper = tabSwitcherWallpaperRuntime,
                visible = tabOverviewVisible,
                bottomBarTopPx = bottomBarTopPx,
                onClose = closeTabOverview,
                onSelect = {
                    val target = controller.activeTabs.firstOrNull { tab -> tab.id == it }
                    if (target != null && target.id != controller.selectedTabId) {
                        liveFrameTabId = null
                        tabHandoff = TabHandoff(
                            tabId = target.id,
                            preview = controller.previews[target.id]
                                .takeUnless { target.isIncognito },
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
                onOpenSettings = {
                    settingsDestination = SettingsDestination.Home
                    settingsVisible = true
                },
                onOpenSyncSettings = {
                    settingsDestination = SettingsDestination.Sync
                    settingsVisible = true
                },
                onEditProfileWallpaper = ::openProfileWallpaperEditor,
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
        }

        SnoozeTabDialog(
            tab = pendingLinkSnooze?.displayTab()
                ?: snoozeTabId?.let { id -> controller.tabs.firstOrNull { it.id == id } },
            onSnooze = { wakeAtMillis ->
                pendingLinkSnooze?.let { pending ->
                    val undoToken = controller.snoozeContextLink(
                        url = pending.url,
                        title = pending.title,
                        wakeAtMillis = wakeAtMillis,
                        sourceTabId = pending.sourceTabId,
                    )
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
                    return@SnoozeTabDialog undoToken != null
                }
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
            onDismiss = {
                snoozeTabId = null
                pendingLinkSnooze = null
            },
        )

        BrowserSettingsOverlay(
            controller = controller,
            visible = settingsVisible,
            destination = settingsDestination,
            predictiveBackCommitted = settingsPredictiveBackCommitted,
            predictiveBackProgress = settingsBackProgress.value,
            predictiveBackEdgeSign = settingsBackEdgeSign,
            selectedTab = selectedTab,
            visibleProfileIds = visibleProfileIds,
            onDestinationChanged = { settingsDestination = it },
            onOpenPrivacyXRay = {
                privacyXRayTabId = selectedTab.id
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            onOpenPermissionRadar = {
                permissionRadarTabId = selectedTab.id
                permissionRadarOrigin = null
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            onEditCapsule = { capsule ->
                openSiteCapsuleEditor(existing = capsule, sourceTab = null)
            },
            onDeleteCapsule = { capsule -> pendingCapsuleDelete = capsule },
            onImportUserScript = onImportUserScript,
            onOpenFilterStudio = {
                filterStudioSelectedRuleId = null
                filterStudioVisible = true
            },
            onExportAppData = onExportAppData,
            onImportAppData = onImportAppData,
            onClearData = { clearDialogVisible = true },
            onOpenLegalUrl = { url ->
                settingsVisible = false
                controller.openUrl(url, inNewTab = true)
            },
            onOpenFirefoxExtensions = onManageFirefoxExtensions,
            onDismiss = { settingsVisible = false },
        )

        BrowserModalSurfaces(
            controller = controller,
            privacyXRayTabId = privacyXRayTabId,
            permissionRadarTabId = permissionRadarTabId,
            permissionRadarOrigin = permissionRadarOrigin,
            filterStudioVisible = filterStudioVisible,
            filterStudioSelectedRuleId = filterStudioSelectedRuleId,
            browserContentBlurTarget = browserContentBlurTarget,
            visibleProfiles = visibleProfiles,
            favoriteFeedbackEvent = favoriteFeedbackEvent,
            feedbackSnackbarHostState = feedbackSnackbarHostState,
            snoozedTabsVisible = snoozedTabsVisible,
            visibleSnoozedTabs = visibleSnoozedTabs,
            onOpenFilterStudio = { ruleId ->
                filterStudioSelectedRuleId = ruleId
                privacyXRayTabId = null
                filterStudioVisible = true
            },
            onPrivacyXRayDismiss = { privacyXRayTabId = null },
            onPermissionOriginSelected = { permissionRadarOrigin = it },
            onPermissionRadarDismiss = {
                permissionRadarTabId = null
                permissionRadarOrigin = null
            },
            onFilterStudioDismiss = {
                filterStudioVisible = false
                filterStudioSelectedRuleId = null
            },
            onFavoriteFeedbackFinished = { completedId ->
                if (favoriteFeedbackEvent?.id == completedId) favoriteFeedbackEvent = null
            },
            onSnoozedTabsDismiss = { snoozedTabsVisible = false },
        )

    }

    BrowserTransientOverlays(
        controller = controller,
        clearDialogVisible = clearDialogVisible,
        onClearDialogDismiss = { clearDialogVisible = false },
        pendingCommand = pendingCommand,
        onPendingCommandDismiss = {
            pendingCommand = null
            addressFocusNonce++
        },
        onPendingCommandConfirmed = { command ->
            pendingCommand = null
            runCommand(command)
        },
        addressNewTabButtonBounds = addressNewTabButtonBounds,
        pendingCapsuleDelete = pendingCapsuleDelete,
        onPendingCapsuleDeleteDismiss = { pendingCapsuleDelete = null },
        onFavoriteLink = { url, title ->
            controller.toggleContextLinkFavorite(url, title)?.let(showFavoriteMutation)
        },
        onSnoozeLink = { url, title, sourceTabId ->
            pendingLinkSnooze = PendingLinkSnooze(url, title, sourceTabId)
        },
    )
    FirefoxExtensionChrome(controller)
}
