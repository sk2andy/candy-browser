package dev.sk2andy.materialbrowser.browser

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.BundledSitePrivacyDefaults
import dev.sk2andy.materialbrowser.blocking.CandyDecisionAction
import dev.sk2andy.materialbrowser.blocking.CandyFilterPresets
import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer
import dev.sk2andy.materialbrowser.blocking.CandyImportScope
import dev.sk2andy.materialbrowser.blocking.CandyMatcherSnapshot
import dev.sk2andy.materialbrowser.blocking.CandyMatcherSnapshots
import dev.sk2andy.materialbrowser.blocking.CandyPublicSuffixRules
import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleDecision
import dev.sk2andy.materialbrowser.blocking.CandyRuleFormat
import dev.sk2andy.materialbrowser.blocking.CandyRuleImport
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.blocking.CandyRuleOrigin
import dev.sk2andy.materialbrowser.blocking.CandyRulePreview
import dev.sk2andy.materialbrowser.blocking.CandyRuleValidation
import dev.sk2andy.materialbrowser.blocking.CandyRuleValidator
import dev.sk2andy.materialbrowser.blocking.CandySubscriptionRules
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer
import dev.sk2andy.materialbrowser.blocking.PrivacyPolicyRules
import dev.sk2andy.materialbrowser.blocking.PrivacyRuleDecisionAction
import dev.sk2andy.materialbrowser.blocking.PrivacyRuleDecisionSummary
import dev.sk2andy.materialbrowser.blocking.PrivacyXRayRepository
import dev.sk2andy.materialbrowser.blocking.PrivacyXRaySnapshot
import dev.sk2andy.materialbrowser.blocking.SiteExceptionRules
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrides
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrideRules
import dev.sk2andy.materialbrowser.blocking.SiteProtectionState
import dev.sk2andy.materialbrowser.capsule.CapsuleDeletionRules
import dev.sk2andy.materialbrowser.capsule.CapsuleFullCandyTransition
import dev.sk2andy.materialbrowser.capsule.CapsuleFullCandyTransitionRules
import dev.sk2andy.materialbrowser.capsule.CapsuleIconRenderer
import dev.sk2andy.materialbrowser.capsule.CapsuleIconMode
import dev.sk2andy.materialbrowser.capsule.CapsuleIconUpdateRules
import dev.sk2andy.materialbrowser.capsule.CapsuleIntentRules
import dev.sk2andy.materialbrowser.capsule.CapsuleLaunchResolution
import dev.sk2andy.materialbrowser.capsule.CapsuleNavigationDecision
import dev.sk2andy.materialbrowser.capsule.CapsuleNavigationRules
import dev.sk2andy.materialbrowser.capsule.CapsuleShortcutPublisher
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleDraft
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleRules
import dev.sk2andy.materialbrowser.browser.actions.BrowserDownloadManager
import dev.sk2andy.materialbrowser.browser.actions.DownloadActionResult
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadLaunchResult
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManager
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.actions.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.actions.LinkLongPressOutcome
import dev.sk2andy.materialbrowser.browser.actions.LinkLongPressRules
import dev.sk2andy.materialbrowser.browser.actions.PendingDownloadChoice
import dev.sk2andy.materialbrowser.browser.actions.WebContentActionState
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.cast.CastMediaCandidate
import dev.sk2andy.materialbrowser.browser.cast.CastMediaIdentity
import dev.sk2andy.materialbrowser.browser.cast.CastMediaRules
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionComposer
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.browser.commands.AndroidCommandCatalog
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommandRegistry
import dev.sk2andy.materialbrowser.browser.commands.CommandContext
import dev.sk2andy.materialbrowser.browser.commands.CommandCookieScope
import dev.sk2andy.materialbrowser.browser.commands.CommandMatcher
import dev.sk2andy.materialbrowser.browser.credentials.HttpAuthPrompt
import dev.sk2andy.materialbrowser.browser.credentials.HttpAuthPromptRules
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserEngineSessionFactory
import dev.sk2andy.materialbrowser.browser.gecko.GeckoProfileStorageRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowsingData
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowsingDataReloadRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCandyTrailHistoryEvent
import dev.sk2andy.materialbrowser.browser.gecko.GeckoContextDownloadRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExternalDownloadResponse
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionKey
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionChromeHost
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionChromeRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionCreateTabRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionPopupIdentity
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionSessionIdentity
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionUpdateTabRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMainFrameNavigationRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNewSessionRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestDecision
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPictureInPictureRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEvent
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicyRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoSessionStateRestoreDecision
import dev.sk2andy.materialbrowser.browser.gecko.GeckoSessionStateSnapshotRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingHostState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingInteractionDelegate
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetHost
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsets
import dev.sk2andy.materialbrowser.browser.integration.AssistantSummaryLauncher
import dev.sk2andy.materialbrowser.browser.integration.AssistantSummaryRequest
import dev.sk2andy.materialbrowser.browser.integration.AssistantSummaryResult
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.browser.integration.DefaultBrowserRole
import dev.sk2andy.materialbrowser.browser.integration.ExternalAppLauncher
import dev.sk2andy.materialbrowser.browser.integration.ExternalLaunchResult
import dev.sk2andy.materialbrowser.browser.integration.ExternalNavigationGrant
import dev.sk2andy.materialbrowser.browser.integration.ExternalNavigationGrantRules
import dev.sk2andy.materialbrowser.browser.integration.ExternalNavigationPolicy
import dev.sk2andy.materialbrowser.browser.integration.ExternalPreviewDownloadGrant
import dev.sk2andy.materialbrowser.browser.integration.ExternalPreviewDownloadGrantRules
import dev.sk2andy.materialbrowser.browser.integration.PageShareLauncher
import dev.sk2andy.materialbrowser.browser.integration.PageShareRequest
import dev.sk2andy.materialbrowser.browser.integration.PageShareResult
import dev.sk2andy.materialbrowser.browser.permissions.ActivePermissionGrant
import dev.sk2andy.materialbrowser.browser.permissions.ActivePermissionLedger
import dev.sk2andy.materialbrowser.browser.permissions.PermissionOrigin
import dev.sk2andy.materialbrowser.browser.permissions.PermissionResponseDelivery
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPrompt
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPromptChoice
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarEntry
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarRepository
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarSnapshot
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRequestIdentity
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRequestRules
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRequestState
import dev.sk2andy.materialbrowser.browser.permissions.PermissionSiteKey
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionActivity
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionDecision
import dev.sk2andy.materialbrowser.browser.permissions.runtimePermissions
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogEntry
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogRules
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFailureReason
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyResolution
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptGrant
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptOpenTabRequest
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRejectionReason
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRules
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarActionLayoutRules
import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingLibraryRules
import dev.sk2andy.materialbrowser.data.BrowsingHistoryRepository
import dev.sk2andy.materialbrowser.data.CandyTrailRepository
import dev.sk2andy.materialbrowser.data.CandyRuleRepository
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.data.FavoriteUndoRules
import dev.sk2andy.materialbrowser.data.FaviconRepository
import dev.sk2andy.materialbrowser.data.HistoryClearRequest
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayoutRules
import dev.sk2andy.materialbrowser.data.DownloadManagerMode
import dev.sk2andy.materialbrowser.data.PermissionRadarStore
import dev.sk2andy.materialbrowser.data.PendingCandyTrailRedaction
import dev.sk2andy.materialbrowser.data.ProfileWallpaperStore
import dev.sk2andy.materialbrowser.data.RecallRepository
import dev.sk2andy.materialbrowser.data.SiteCapsuleIconStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleStore
import dev.sk2andy.materialbrowser.data.SnoozeRestoreRules
import dev.sk2andy.materialbrowser.data.SnoozeRules
import dev.sk2andy.materialbrowser.data.SnoozeMutationRules
import dev.sk2andy.materialbrowser.data.SnoozeRuntimeRegistry
import dev.sk2andy.materialbrowser.data.SnoozeScheduler
import dev.sk2andy.materialbrowser.data.SnoozeUndoRules
import dev.sk2andy.materialbrowser.data.SnoozeUndoToken
import dev.sk2andy.materialbrowser.data.SnoozeWakeNotifier
import dev.sk2andy.materialbrowser.data.SnoozedTab
import dev.sk2andy.materialbrowser.data.SnoozedTabStore
import dev.sk2andy.materialbrowser.data.TabAutoSortingRules
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabDuplicateRules
import dev.sk2andy.materialbrowser.data.TabPinningRules
import dev.sk2andy.materialbrowser.data.TabReorderingRules
import dev.sk2andy.materialbrowser.data.TabStackRules
import dev.sk2andy.materialbrowser.data.TabPreviewRepository
import dev.sk2andy.materialbrowser.data.TabPreviewCaptureRules
import dev.sk2andy.materialbrowser.data.TabPreviewQuality
import dev.sk2andy.materialbrowser.data.TabRetentionRules
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.data.GeckoSessionStateStore
import dev.sk2andy.materialbrowser.data.ToppingCatalogRefreshResult
import dev.sk2andy.materialbrowser.data.ToppingCatalogRepository
import dev.sk2andy.materialbrowser.data.ToppingDownloadResult
import dev.sk2andy.materialbrowser.data.UserScriptRepository
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncCacheStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncSettingsStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncVaultStore
import dev.sk2andy.materialbrowser.data.sync.CandySyncRepository
import dev.sk2andy.materialbrowser.reader.ReaderExtractionFailure
import dev.sk2andy.materialbrowser.reader.ReaderExtractionParser
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import dev.sk2andy.materialbrowser.recall.RecallExtractionIdentity
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.recall.RecallRules
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncPendingMutation
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncTab
import java.util.ArrayDeque
import java.util.UUID
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private class PendingGeckoPreviewCapture(
    val tabId: String,
    val session: AndroidBrowserEngineSessionPort,
    val view: View,
    val pageUrl: String,
    val navigationGeneration: Int,
    val previewEpoch: Int,
    val sourceRect: Rect,
    onComplete: () -> Unit,
    var acceptAfterDeparture: Boolean,
) {
    val completionCallbacks = mutableListOf(onComplete)
    var capture: BrowserEnginePreviewCapture? = null
    var timeout: Runnable? = null
    var uiCompleted = false
    var expired = false
}

internal data class FullscreenVideoState(
    val tabId: String,
    val minimizedByUser: Boolean,
    val sourceRevision: Int,
    val source: FullscreenVideoSource,
    val host: FullscreenVideoHost,
)

internal enum class FullscreenVideoSource {
    GeckoView,
}

internal enum class FullscreenVideoHost {
    Overlay,
}

internal sealed interface UserScriptSaveOutcome {
    data object Saved : UserScriptSaveOutcome
    data object LimitReached : UserScriptSaveOutcome
    data object Missing : UserScriptSaveOutcome
    data object PersistenceFailed : UserScriptSaveOutcome
    data class Rejected(val reason: UserScriptRejectionReason) : UserScriptSaveOutcome
    data class DependencyFailed(
        val reason: UserScriptDependencyFailureReason,
    ) : UserScriptSaveOutcome
}

private class GeckoMediaPresentation(
    val tabId: String,
    val session: AndroidBrowserEngineSessionPort,
    val view: View,
    var minimizedByUser: Boolean,
)

private data class FindInPageSession(
    val id: Long,
    val tabId: String,
    val geckoSession: AndroidBrowserEngineSessionPort? = null,
    val navigationGeneration: Int,
) {
    init {
        require(geckoSession != null)
    }
}

private data class GeckoViewBinding(
    val tabId: String,
    val session: AndroidBrowserEngineSessionPort,
    val view: View,
)

private data class NativeSafeAreaFallbackReload(
    val navigationGeneration: Int,
    val url: String?,
)

private data class GeckoLinkPeekBinding(
    val sourceTabId: String,
    val contentRevision: Long,
    val session: AndroidBrowserEngineSessionPort,
    val view: View,
    var committedUrl: String,
    var title: String? = null,
    var progress: Int = 0,
    var isLoading: Boolean = true,
)

class BrowserController(
    private val activity: Activity,
    private val requestRuntimePermissions: (Set<String>) -> Unit = { permissions ->
        activity.requestPermissions(permissions.toTypedArray(), WEB_PERMISSION_REQUEST_CODE)
    },
    private val launchFileChooser: (Intent) -> Unit = { intent ->
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
    },
    private val requestSnoozeNotificationPermission: () -> Unit = {},
    private val onFullImmersiveModeChanged: (Boolean) -> Unit = {},
    private val onMediaStateChanged: () -> Unit = {},
    private val externalApps: ExternalAppLauncher = ExternalAppLauncher(activity),
) {
    val usesGeckoEngine: Boolean
        get() = BuildConfig.USE_GECKO_ENGINE

    val supportsPageContentActions: Boolean
        get() = true

    private val isDomainMuteSupported: Boolean
        get() = true

    val tabs = mutableStateListOf<BrowserTab>()
    val tabStacks = mutableStateListOf<TabStack>()
    val profiles = mutableStateListOf<BrowserProfile>()
    val previews = mutableStateMapOf<String, Bitmap>()
    val favicons = mutableStateMapOf<String, Bitmap>()
    val history = mutableStateListOf<HistoryEntry>()
    val favorites = mutableStateListOf<FavoriteEntry>()
    private var favoriteRevision = 0L
    val privacySnapshots = mutableStateMapOf<String, PrivacyXRaySnapshot>()
    val filterRules = mutableStateListOf<CandyRule>()
    private val incognitoRuleHits = mutableStateMapOf<String, Int>()
    val candyTrails = mutableStateMapOf<String, CandyTrail>()
    val snoozedTabs = mutableStateListOf<SnoozedTab>()
    val siteCapsules = mutableStateListOf<SiteCapsule>()
    internal val userScripts = mutableStateListOf<UserScript>()
    internal var toppingCatalogResult by mutableStateOf<ToppingCatalogRefreshResult?>(null)
        private set
    internal var isToppingCatalogLoading by mutableStateOf(false)
        private set
    internal val busyToppingIds = mutableStateListOf<String>()
    val contentActions = WebContentActionState()
    internal val firefoxExtensionActions = mutableStateListOf<GeckoExtensionActionState>()
    internal var firefoxExtensionPopupView by mutableStateOf<View?>(null)
        private set
    private var firefoxExtensionPopupIdentity: GeckoExtensionPopupIdentity? = null
    val externalDownloadManagers = mutableStateListOf<ExternalDownloadManagerApp>()
    var activeProfileWallpaperBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var activeProfileTabSwitcherWallpaperBitmap by mutableStateOf<Bitmap?>(null)
        private set

    private var selectedTabIdState by mutableStateOf("")
    var selectedTabId: String
        get() = selectedTabIdState
        private set(value) {
            if (selectedTabIdState == value) return
            if (findInPageState?.tabId != value) closeFindInPage()
            selectedTabIdState = value
            geckoMediaPresentation
                ?.takeIf { presentation ->
                    presentation.tabId != value &&
                        tabs.firstOrNull { it.id == presentation.tabId }?.isIncognito == true
                }
                ?.let { clearGeckoMediaPresentation() }
        }
    var activeProfileId by mutableStateOf(DEFAULT_PROFILE_ID)
        private set
    var profilesEnabled by mutableStateOf(true)
        private set
    var blockerSettings by mutableStateOf(BlockerSettings())
        private set
    var inactiveTabLifetime by mutableStateOf(InactiveTabLifetime.Never)
        private set
    var residentTabLimit by mutableIntStateOf(BrowserSessionResidencyRules.DEFAULT_LIMIT)
        private set
    var searchEngine by mutableStateOf(SearchEngine.Google)
        private set
    var pageTranslationProvider by mutableStateOf(PageTranslationProvider.Google)
        private set
    var linkLongPressAction by mutableStateOf(LinkLongPressAction.LinkPeek)
        private set
    var searxngSettings by mutableStateOf(SearxngSettings())
        private set
    var isAiModeToggleVisible by mutableStateOf(false)
        private set
    var isRecallEnabled by mutableStateOf(false)
        private set
    var searchSuggestionProvider by mutableStateOf(defaultSearchSuggestionProvider())
        private set
    var isHistorySuggestionsEnabled by mutableStateOf(true)
        private set
    var dismissResistancePercent by mutableIntStateOf(40)
        private set
    var tabOverviewMode by mutableStateOf(TabOverviewMode.Hero)
        private set
    var tabStackFolderMode by mutableStateOf(TabOverviewMode.Grid)
        private set
    var tabListStartsAtBottom by mutableStateOf(false)
        private set
    var automaticTabSortingEnabled by mutableStateOf(false)
        private set
    var addressBarDockPlacement by mutableStateOf<AddressBarDockPlacement?>(null)
        private set
    private var lastAddressBarDockPlacement = AddressBarDockPlacement.Default
    val lastAddressBarDockEdge: AddressBarDockEdge
        get() = lastAddressBarDockPlacement.edge
    val isAddressBarDocked: Boolean
        get() = addressBarDockPlacement != null
    var isAddressBarDockingEnabled by mutableStateOf(true)
        private set
    var isExternalLinkPreviewEnabled by mutableStateOf(false)
        private set
    var addressBarActionLayout by mutableStateOf(AddressBarActionLayout.Default)
        private set
    var linkPeekActionLayout by mutableStateOf(LinkPeekActionLayout.Default)
        private set
    internal var findInPageState by mutableStateOf<FindInPageState?>(null)
        private set
    private var findInPageSession: FindInPageSession? = null
    private var nextFindInPageSessionId = 0L
    var isFullImmersiveModeEnabled by mutableStateOf(false)
        private set
    var isStartupAnimationEnabled by mutableStateOf(true)
        private set
    var isOpenHomeOnStartupEnabled by mutableStateOf(false)
        private set
    var isScrollBarEnabled by mutableStateOf(false)
        private set
    var isVideoAutoplayBlocked by mutableStateOf(false)
        private set
    var externalLinkPreviewState by mutableStateOf<ExternalLinkPreviewState?>(null)
        private set
    var appearanceSettings by mutableStateOf(AppearanceSettings())
        private set
    var downloadSettings by mutableStateOf(BrowserDownloadSettings())
        private set
    var pendingDownloadChoice by mutableStateOf<PendingDownloadChoice?>(null)
        private set
    var isWebContentEdgeToEdgeEnabled by mutableStateOf(true)
        private set
    private var isScrollAwareTopInsetEnabled = true
    var isDefaultBrowser by mutableStateOf(false)
        private set
    var activeCapsuleId by mutableStateOf<String?>(null)
        private set
    var engineViewRevision by mutableIntStateOf(0)
        private set
    var permissionPrompt by mutableStateOf<PermissionPrompt?>(null)
        private set
    var httpAuthPrompt by mutableStateOf<HttpAuthPrompt?>(null)
        private set
    var webPrompt by mutableStateOf<BrowserWebPrompt?>(null)
        private set
    internal var fullscreenVideoState by mutableStateOf<FullscreenVideoState?>(null)
        private set
    internal var castMediaCandidate by mutableStateOf<CastMediaCandidate?>(null)
        private set
    val isProfileIsolationSupported: Boolean
        get() = isProfileIsolationSupportedState
    private var isProfileIsolationSupportedState by mutableStateOf(true)

    val canOpenLinkInPrivate: Boolean
        get() = isProfileIsolationSupported && !isSyncedProfile(activeProfileId)
    val isVideoAutoplayBlockingSupported: Boolean
        get() = isVideoAutoplayBlockingSupportedState
    private var isVideoAutoplayBlockingSupportedState by mutableStateOf(true)

    val isUserScriptSupported: Boolean
        get() = isUserScriptSupportedState
    private var isUserScriptSupportedState by mutableStateOf(false)

    val activeSiteCapsule: SiteCapsule?
        get() = activeCapsuleId?.let { id -> siteCapsules.firstOrNull { it.id == id } }
    val isCapsulePinningSupported: Boolean
        get() = capsuleShortcuts.isPinningSupported()
    val canCreateSiteCapsule: Boolean
        get() = SiteCapsuleRules.canCreate(siteCapsules.size)
    val selectedFavicon: Bitmap?
        get() = favicons[selectedTabId]
    private val bottomBarCompactStates = mutableStateMapOf<String, Boolean>()
    private val browserChromeScrollStates = mutableMapOf<String, BrowserChromeScrollState>()

    val isBottomBarCompact: Boolean
        get() = bottomBarCompactStates[selectedTabId] == true

    internal val canMinimizeFullscreenVideo: Boolean
        get() = presentationIsPrivate() == false

    internal val isFullscreenVideoExpanded: Boolean
        get() = fullscreenVideoPlacement(videoOnlyPresentation = false) ==
            FullscreenVideoPlacement.Expanded

    internal val isPictureInPictureEligible: Boolean
        get() = GeckoPictureInPictureRules.isEligible(
            state = geckoMediaStates[selectedTabId],
            isPrivate = selectedTab.isIncognito,
            isSelectedTab = true,
        )

    internal val systemMediaState: BrowserMediaState?
        get() = geckoSystemMediaState()

    @VisibleForTesting
    internal fun reportSelectedGeckoMediaStateForTesting(state: GeckoMediaSessionState) {
        check(usesGeckoEngine)
        val session = geckoEngineSessionFor(selectedTabId)
        onGeckoMediaState(selectedTabId, session, state)
    }

    @VisibleForTesting
    fun selectedGeckoViewForTesting(): View? = geckoViewBindings.values
        .firstOrNull { binding -> binding.tabId == selectedTabId }
        ?.view

    /** Routes a semantic Gecko content-target callback through normal Link Peek handling. */
    @VisibleForTesting
    internal fun dispatchSelectedGeckoContentTargetForTesting(target: WebContentTarget) {
        check(usesGeckoEngine)
        geckoEngineSessionFor(selectedTabId).dispatchContentTargetForTesting(target)
    }

    @VisibleForTesting
    fun hasPendingFileChooserForTesting(): Boolean = pendingFileChooser != null

    @VisibleForTesting
    fun detectFederatedLoginForTesting(requestUrl: String) {
        val context = protectionRequestContexts[selectedTabId] ?: return
        detectFederatedLoginRequest(selectedTabId, requestUrl, context)
    }

    @VisibleForTesting
    fun detectCaptchaForTesting(requestUrl: String) {
        val context = protectionRequestContexts[selectedTabId] ?: return
        detectCaptchaRequest(selectedTabId, requestUrl, context)
    }

    @VisibleForTesting
    fun acceptsThirdPartyCookiesForTesting(tabId: String = selectedTabId): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return PrivacyPolicyRules.acceptsThirdPartyCookies(
            blockThirdPartyCookies = workerSettings.blockThirdPartyCookies,
            sitePaused = isSiteProtectionPaused(tabId, tab.url),
        ) || isFederatedLoginCompatibilityEnabled(tab, tab.url) ||
            isCaptchaCompatibilityEnabled(tab, tab.url)
    }

    @VisibleForTesting
    val pendingPopupCountForTesting: Int
        get() = pendingPopupNavigations.size

    @VisibleForTesting
    val transientPopupCountForTesting: Int
        get() = transientPopupTabIds.size

    @VisibleForTesting
    fun selectedTabForTesting(): BrowserTab = selectedTab

    @VisibleForTesting
    fun residentTabIdsForTesting(): Set<String> = geckoEngineSessions.keys.toSet()

    @VisibleForTesting
    val activeLinkPeekPreviewCountForTesting: Int
        get() = geckoLinkPeekBindings.size

    @VisibleForTesting
    fun externalLinkPreviewEngineViewForTesting(): View? =
        externalLinkPreviewRuntime?.binding?.view

    @VisibleForTesting
    fun externalLinkPreviewUsesGeckoForTesting(): Boolean =
        externalLinkPreviewRuntime?.binding is ExternalLinkPreviewEngineBinding.Gecko

    @VisibleForTesting
    val videoAutoplayScriptHandlerCountForTesting: Int
        get() = if (isVideoAutoplayBlocked) geckoEngineSessions.size else 0

    @VisibleForTesting
    internal var syncMutationObserverForTesting: ((SyncPendingMutation) -> Unit)? = null

    @VisibleForTesting
    internal fun applySyncRepositoryStateForTesting(state: SyncRepositoryState) {
        applySyncRepositoryState(state)
    }

    @VisibleForTesting
    internal fun dispatchGeckoEngineEventForTesting(event: BrowserEngineEvent) {
        onGeckoEngineEvent(event)
    }

    @VisibleForTesting
    internal fun installGeckoEngineSessionForTesting(session: AndroidBrowserEngineSessionPort) {
        require(tabs.any { tab -> tab.id == session.tabId })
        geckoEngineSessions.put(session.tabId, session)?.execute(BrowserEngineCommands.close())
        connectGeckoScrollListener(session.tabId, session)
    }

    private val geckoEngineSessions = mutableMapOf<String, AndroidBrowserEngineSessionPort>()
    private val geckoViewBindings = mutableMapOf<FrameLayout, GeckoViewBinding>()
    private val geckoViewMutationHosts = mutableSetOf<FrameLayout>()
    private val geckoViewSessionsBeingReleased = mutableSetOf<AndroidBrowserEngineSessionPort>()
    private val pendingGeckoViewAttachRetries = mutableMapOf<FrameLayout, Any>()
    private var isGeckoViewBindingMutationInProgress = false
    private val geckoEngineSessionFactory by lazy(LazyThreadSafetyMode.NONE) {
        GeckoBrowserEngineSessionFactory(activity.applicationContext)
    }
    private val residentSessionAccessOrder = mutableMapOf<String, Long>()
    private var residentSessionAccessSequence = 0L
    private var residentSessionTrimScheduled = false
    private var fullscreenVideoSourceRevision = 0
    private var geckoMediaPresentation: GeckoMediaPresentation? = null
    private var fullscreenVideoInsideSafeDrawingHost = false
    private var pictureInPictureTransitionPending = false
    private var pictureInPictureTransitionGeneration = 0
    private var pictureInPicturePlaybackRetryGeneration = 0
    private var pictureInPictureOwnerTabId: String? = null
    private var pictureInPictureCompositorSession: AndroidBrowserEngineSessionPort? = null
    private var pictureInPicturePlaybackExpected = false
    private var isInPictureInPicture = false
    private val geckoMediaStates = mutableMapOf<String, GeckoMediaSessionState>()
    private val geckoLinkPeekBindings = mutableMapOf<View, GeckoLinkPeekBinding>()
    private var nextGeckoLinkPeekId = 0L
    private var externalLinkPreviewRuntime: ExternalLinkPreviewRuntime? = null
    private var nextExternalLinkPreviewSessionId = 0L
    private val navigationGenerations = mutableMapOf<String, Int>()
    private val nativeSafeAreaFallbackTabs = mutableSetOf<String>()
    private val nativeSafeAreaFallbackReloads = mutableMapOf<String, NativeSafeAreaFallbackReload>()
    private val committedRecallPages = mutableMapOf<String, RecallExtractionIdentity>()
    private val externalNavigationGrants = mutableMapOf<String, ExternalNavigationGrant>()
    private val pendingInitialExternalNavigationGrants =
        mutableMapOf<String, ExternalNavigationGrant>()
    private var webContentRequestGeneration = 0L
    private var userScriptMutationPending = false
    private var toppingCatalogRefreshGeneration = 0
    private val pendingConsentCssUrls = mutableMapOf<String, String?>()
    private val pendingPopupNavigations = mutableMapOf<String, PendingPopupNavigation>()
    private val pendingPopunderNavigations = mutableMapOf<String, PendingPopunderNavigation>()
    private val transientPopupTabIds = mutableSetOf<String>()
    private var blockedPopupSequence = 0L
    internal var blockedPopupOffer by mutableStateOf<BlockedPopupOffer?>(null)
        private set
    internal var federatedLoginOffer by mutableStateOf<FederatedLoginOffer?>(null)
        private set
    private var federatedLoginOfferSequence = 0L
    private val federatedLoginOfferKeys = ConcurrentHashMap<String, String>()
    internal var captchaCompatibilityOffer by mutableStateOf<CaptchaCompatibilityOffer?>(null)
        private set
    private var captchaCompatibilityOfferSequence = 0L
    private val captchaCompatibilityOfferKeys =
        ConcurrentHashMap<String, MutableSet<String>>()
    private val federatedLoginPopupTabIds = mutableSetOf<String>()
    private val federatedLoginCompatibilityTabIds = mutableSetOf<String>()
    private val pageUrls = ConcurrentHashMap<String, String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    val syncIconCatalog = SyncDeviceIconCatalog.decode(
        activity.assets.open("candy_sync_device_icons_v1.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() },
    )
    private val syncRepository = CandySyncRepository(
        settingsStore = AndroidSyncSettingsStore(activity),
        vaultStore = AndroidSyncVaultStore(activity),
        cacheStore = AndroidSyncCacheStore(activity),
        iconCatalog = syncIconCatalog,
    )
    var syncState by mutableStateOf(syncRepository.currentState())
        private set
    private var syncObservation: AutoCloseable? = null
    private val locallyPendingSyncCandyIds = mutableSetOf<String>()
    private val pendingSyncNavigationRunnables = mutableMapOf<String, Runnable>()
    private val remoteSyncNavigationUrls = mutableMapOf<String, String>()
    private val supersededRemoteSyncNavigationUrls = mutableMapOf<String, MutableSet<String>>()
    private val syncRefreshRunnable = object : Runnable {
        override fun run() {
            if (destroyed || !isActivityStarted) return
            syncRepository.refresh()
            mainHandler.postDelayed(this, SYNC_REFRESH_INTERVAL_MILLIS)
        }
    }
    private val fileChooserValidationExecutor = Executors.newSingleThreadExecutor()
    private val profileWallpaperExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "profile-wallpaper")
    }
    private val historyMutationExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "browser-history-mutation")
    }
    private val pendingBlockedCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingPrivacyTabs = ConcurrentHashMap.newKeySet<String>()
    private val reportedAllowedDecisions = ConcurrentHashMap<String, MutableSet<String>>()
    private val blockerFlushScheduled = AtomicBoolean(false)
    private val privacyXRayRepository = PrivacyXRayRepository()
    private val privacyEventLock = Any()
    private val temporarySiteExceptions = ConcurrentHashMap<String, Set<String>>()
    private val temporarySitePrivacyOverrides =
        ConcurrentHashMap<String, Map<String, SitePrivacyOverrides>>()
    private val permissionStore = PermissionRadarStore(activity)
    private val permissionRepository = PermissionRadarRepository(permissionStore)
    private val activePermissions = ActivePermissionLedger()
    private var pendingPermissionAccess: PendingPermissionAccess? = null
    private var pendingGeckoAndroidPermissionRequest: PendingGeckoAndroidPermissionRequest? = null
    private var pendingFileChooser: PendingFileChooser? = null
    private var permissionPromptSequence = 0L
    private var pendingHttpAuthChallenge: PendingHttpAuthChallenge? = null
    private var httpAuthPromptSequence = 0L
    private var pendingWebPrompt: PendingWebPrompt? = null
    private var webPromptSequence = 0L
    private var permissionRevision by mutableIntStateOf(0)
    private val protectionRequestContexts = ConcurrentHashMap<String, ProtectionRequestContext>()
    private var isActivityResumed = false
    private var isActivityStarted = false
    private var recallDisablePending = false
    private var browsingDataClearPending = false
    @Volatile
    private var destroyed = false
    private var previewContentBottomInWindowPx: Int? = null
    private val pendingGeckoPreviewCaptures = mutableMapOf<String, PendingGeckoPreviewCapture>()
    @VisibleForTesting
    var previewCaptureRequestCountForTesting = 0
        private set
    private var lastWindowInsets: WindowInsetsCompat? = null
    private var browserChromeOwnsIme = false
    private var previewEpoch = 0
    private var faviconEpoch = 0
    private val faviconGenerations = mutableMapOf<String, Int>()
    private val candyTrailHistoryBindings = mutableMapOf<String, CandyTrailHistoryBinding>()
    private val pendingCandyTrailTargets = mutableMapOf<String, String>()
    private val candyTrailGenerations = mutableMapOf<String, Int>()
    private val capsuleTabIds = mutableMapOf<String, String>()
    private val pendingRecallProfileDeletions = mutableSetOf<String>()
    var activeCapsuleTabId: String? = null
        private set
    private val pendingCandyTrailRestoreIds = mutableSetOf<String>()
    private val suppressedCandyTrailTabIds = mutableSetOf<String>()
    private val candyTrailRedactionsDuringRestore = mutableListOf<PendingCandyTrailRedaction>()
    private var isCandyTrailRestoreInProgress = false
    private var candyTrailEpoch = 0
    private val store = BrowserSessionStore(activity)
    private val historyRepository = BrowsingHistoryRepository.get(activity)
    private val recallRepository = RecallRepository.get(activity)
    private val snoozedTabStore = SnoozedTabStore(activity)
    private val snoozeScheduler = SnoozeScheduler(activity)
    private val snoozeRestoreCallback: (Long) -> Unit = { nowMillis ->
        mainHandler.post {
            if (!destroyed) restoreDueSnoozedTabs(nowMillis)
        }
    }
    private val permanentMutedDomains = mutableStateMapOf<String, Set<String>>().apply {
        putAll(store.loadMutedDomains())
    }
    private val temporaryMutedDomains = mutableStateMapOf<String, Set<String>>()
    private val extensionTabMuteOverrides = mutableMapOf<String, Boolean>()
    private val permanentDesktopViewDomains = mutableStateMapOf<String, Set<String>>().apply {
        putAll(store.loadDesktopViewDomains())
    }
    private val temporaryDesktopViewDomains = mutableStateMapOf<String, Set<String>>()
    private val permanentAlwaysBlockPopupDomains =
        mutableStateMapOf<String, Set<String>>().apply {
            putAll(store.loadAlwaysBlockPopupDomains())
        }
    private val temporaryAlwaysBlockPopupDomains = mutableStateMapOf<String, Set<String>>()
    private val previewRepository = TabPreviewRepository.get(activity)
    private val faviconRepository = FaviconRepository.get(activity)
    private val candyTrailRepository = CandyTrailRepository.get(activity)
    private val geckoSessionStateStore = GeckoSessionStateStore(activity.applicationContext)
    private val siteCapsuleStore = SiteCapsuleStore(activity)
    private val siteCapsuleIconStore = SiteCapsuleIconStore(activity)
    private val profileWallpaperStore = ProfileWallpaperStore(activity.applicationContext)
    private var profileWallpaperLoadGeneration = 0
    private var profileTabSwitcherWallpaperLoadGeneration = 0
    private val capsuleShortcuts = CapsuleShortcutPublisher(activity)
    private val candyRuleRepository = CandyRuleRepository.get(activity)
    private val userScriptRepository = UserScriptRepository.get(activity)
    private val userScriptCommandsByTab = mutableStateMapOf<String, List<UserScriptMenuCommand>>()
    private val toppingCatalogRepository = ToppingCatalogRepository.get(activity)
    private val bundledSitePrivacyDefaults = BundledSitePrivacyDefaults.load(activity)
    private val downloadManager = BrowserDownloadManager(activity)
    private val externalDownloadManager = ExternalDownloadManager(activity)
    private val queuedDownloadChoices = ArrayDeque<PendingDownloadChoice>()
    private val assistantSummary = AssistantSummaryLauncher(activity)
    private val pageShare = PageShareLauncher(activity)
    private val commandCatalog = AndroidCommandCatalog(activity)
    private val matcherSnapshot = AtomicReference(CandyMatcherSnapshot.Empty)
    private val incognitoMatcherSnapshot = AtomicReference(CandyMatcherSnapshot.Empty)
    private val ephemeralRuleIds = mutableSetOf<String>()

    @Volatile
    private var permanentSiteExceptions = store.loadPermanentSiteExceptions()
    private var permanentSitePrivacyOverrides = store.loadSitePrivacyOverrides()
    private var siteExceptionRevision by mutableIntStateOf(0)

    @Volatile
    private var workerSettings = store.loadBlockerSettings()

    val selectedTab: BrowserTab
        get() = tabs.firstOrNull { it.id == selectedTabId }
            ?: activeTabs.firstOrNull()
            ?: tabs.first()

    internal val selectedUserScriptMenuCommands: List<UserScriptMenuCommand>
        get() = userScriptCommandsByTab[selectedTabId].orEmpty()

    internal fun invokeUserScriptMenuCommand(command: UserScriptMenuCommand) {
        if (command.tabId != selectedTabId) return
        geckoEngineSessionFactory.invokeToppingMenuCommand(command)
    }

    val activeTabs: List<BrowserTab>
        get() = tabs.filter { tab ->
            tab.profileId == activeProfileId && tab.id !in transientPopupTabIds
        }.let { activeTabs ->
            if (automaticTabSortingEnabled) {
                TabAutoSortingRules.orderedTabs(activeTabs, selectedTabId)
            } else {
                activeTabs
            }
        }

    private val localProfiles: List<BrowserProfile>
        get() = profiles.filterNot(BrowserProfile::isSynced)

    val localBrowserProfiles: List<BrowserProfile>
        get() = localProfiles

    private fun profileForId(profileId: String): BrowserProfile? =
        profiles.firstOrNull { it.id == profileId }

    private fun isSyncedProfile(profileId: String): Boolean =
        profiles.any { it.id == profileId && it.isSynced }

    private fun syncTargetDeviceId(profileId: String): String? {
        profiles.firstOrNull { it.id == profileId }?.syncedDeviceId?.let { return it }
        val state = syncState
        val currentDeviceId = state.currentDeviceId ?: return null
        val configuredProfileId = state.settings?.localProfileId
            ?.takeIf { candidate -> localProfiles.any { it.id == candidate } }
            ?: activeProfileId.takeIf { candidate -> localProfiles.any { it.id == candidate } }
            ?: localProfiles.firstOrNull()?.id
        return currentDeviceId.takeIf { configuredProfileId == profileId }
    }

    private fun isSyncTargetProfile(profileId: String): Boolean =
        syncTargetDeviceId(profileId) != null

    private fun isBoundSyncProfile(profileId: String): Boolean =
        !isSyncedProfile(profileId) && syncTargetDeviceId(profileId) != null

    val activeTabStacks: List<TabStack>
        get() = TabStackRules.sanitized(tabStacks, activeTabs)

    val stackAwareOverviewTabs: List<BrowserTab>
        get() = TabStackRules.visibleTabs(
            tabs = activeTabs,
            stacks = activeTabStacks,
        )

    val gridOverviewTabs: List<BrowserTab>
        get() = stackAwareOverviewTabs

    fun tabStackFor(tabId: String): TabStack? =
        activeTabStacks.firstOrNull { stack -> tabId in stack.tabIds }

    fun stackAwareOverviewTabId(tabId: String): String = TabStackRules.visibleTabId(
        tabId = tabId,
        tabs = activeTabs,
        stacks = activeTabStacks,
    )

    val canToggleSelectedDomainMute: Boolean
        get() = supportsPageContentActions && canToggleDomainMute(selectedTabId)

    val isSelectedDomainMuted: Boolean
        get() = isDomainMuted(selectedTabId)

    val canToggleSelectedAlwaysBlockPopups: Boolean
        get() = supportsPageContentActions && canToggleAlwaysBlockPopups(selectedTabId)

    val isSelectedAlwaysBlockPopups: Boolean
        get() = isAlwaysBlockPopupsEnabled(selectedTabId)

    val canToggleSelectedDesktopView: Boolean
        get() = canToggleDesktopView(selectedTabId)

    val isSelectedDesktopView: Boolean
        get() = isDesktopView(selectedTabId)

    fun canToggleDomainMute(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val pageUrl = pageUrls[tabId] ?: tab.url
        return isDomainMuteSupported && DomainMuteRules.domainForUrl(pageUrl) != null
    }

    fun isDomainMuted(tabId: String): Boolean {
        siteExceptionRevision
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return isDomainMuted(tab, pageUrls[tabId] ?: tab.url)
    }

    fun canToggleAlwaysBlockPopups(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val pageUrl = pageUrls[tabId] ?: tab.url
        return PopupSiteRules.domainForUrl(pageUrl) != null
    }

    fun isAlwaysBlockPopupsEnabled(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return isAlwaysBlockPopupsEnabled(tab, pageUrls[tabId] ?: tab.url)
    }

    fun canToggleDesktopView(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val pageUrl = pageUrls[tabId] ?: tab.url
        return DesktopSiteRules.domainForUrl(pageUrl) != null
    }

    fun isDesktopView(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return isDesktopView(tab, pageUrls[tabId] ?: tab.url)
    }

    fun permissionRadarSnapshot(
        tabId: String = selectedTabId,
        requestedOrigin: String? = null,
    ): PermissionRadarSnapshot {
        permissionRevision
        val tab = tabs.firstOrNull { it.id == tabId } ?: return PermissionRadarSnapshot.Empty
        val currentOrigin = PermissionOrigin.normalize(pageUrls[tabId] ?: tab.url)
        val selectedOrigin = PermissionOrigin.normalize(requestedOrigin) ?: currentOrigin
        val knownOrigins = buildSet {
            addAll(permissionRepository.origins(tab.profileId, tab.isIncognito))
            currentOrigin?.let(::add)
        }.sorted()
        val origin = selectedOrigin ?: return PermissionRadarSnapshot.Empty.copy(
            isPrivate = tab.isIncognito,
            knownOrigins = knownOrigins,
        )
        val site = PermissionSiteKey(tab.profileId, origin)
        val pending = pendingPermissionAccess
            ?.takeIf { access -> access.identity.tabId == tabId && access.identity.origin == origin }
            ?.requested
            .orEmpty()
        val active = activePermissions.permissions(tabId, site)
        return PermissionRadarSnapshot(
            site = site,
            isPrivate = tab.isIncognito,
            knownOrigins = knownOrigins,
            entries = SitePermission.entries.map { permission ->
                PermissionRadarEntry(
                    permission = permission,
                    decision = permissionRepository.decision(site, permission, tab.isIncognito),
                    allowedForSession = permissionRepository.isAllowedForSession(
                        site,
                        permission,
                        tab.isIncognito,
                    ),
                    activity = when (permission) {
                        in pending -> SitePermissionActivity.Pending
                        in active -> SitePermissionActivity.Active
                        else -> SitePermissionActivity.Idle
                    },
                )
            },
        )
    }

    fun hasPermissionActivity(tabId: String = selectedTabId): Boolean {
        permissionRevision
        return pendingPermissionAccess?.identity?.tabId == tabId || activePermissions.hasTab(tabId)
    }

    fun setSitePermissionDecision(
        tabId: String,
        origin: String,
        permission: SitePermission,
        decision: SitePermissionDecision,
    ): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val normalizedOrigin = PermissionOrigin.normalize(origin) ?: return false
        if (normalizedOrigin != origin) return false
        val site = PermissionSiteKey(tab.profileId, normalizedOrigin)
        permissionRepository.setDecision(site, permission, decision, tab.isIncognito)
        permissionRevision++
        if (
            activePermissions.has(tabId, site, permission)
        ) {
            cancelPendingPermissionAccess(tabId)
            removeActivePermissionsForTab(tabId)
            clearExternalNavigationAuthorization(tabId)
            geckoEngineSessions[tabId]?.execute(BrowserEngineCommands.reload())
        } else if (
            pendingPermissionAccess?.let { access ->
                access.site == site && permission in access.requested
            } == true
        ) {
            cancelPendingPermissionAccess(tabId)
        }

        return true
    }

    fun resetSitePermissions(tabId: String, origin: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val normalizedOrigin = PermissionOrigin.normalize(origin) ?: return false
        if (normalizedOrigin != origin) return false
        val site = PermissionSiteKey(tab.profileId, normalizedOrigin)
        permissionRepository.resetSite(site, tab.isIncognito)
        permissionRevision++
        if (pendingPermissionAccess?.site == site) cancelPendingPermissionAccess(tabId)
        if (activePermissions.hasSite(tabId, site)) {
            removeActivePermissionsForTab(tabId)
            clearExternalNavigationAuthorization(tabId)
            geckoEngineSessions[tabId]?.execute(BrowserEngineCommands.reload())
        }
        return true
    }

    fun respondToPermissionPrompt(promptId: Long, choice: PermissionPromptChoice) {
        val pending = pendingPermissionAccess?.takeIf { it.promptId == promptId } ?: return
        if (!isPermissionRequestCurrent(pending.identity)) {
            cancelPendingPermissionAccess(pending.identity.tabId)
            return
        }
        val prompted = pending.prompted
        when (choice) {
            PermissionPromptChoice.AllowOnce -> permissionRepository.allowOnce(
                pending.site,
                prompted,
                pending.identity.isPrivate,
            )
            PermissionPromptChoice.AllowAlways -> prompted.forEach { permission ->
                permissionRepository.setDecision(
                    pending.site,
                    permission,
                    SitePermissionDecision.Allow,
                    pending.identity.isPrivate,
                )
            }
            PermissionPromptChoice.Block -> prompted.forEach { permission ->
                permissionRepository.setDecision(
                    pending.site,
                    permission,
                    SitePermissionDecision.Block,
                    pending.identity.isPrivate,
                )
            }
        }
        permissionPrompt = null
        permissionRevision++
        val allowed = if (choice == PermissionPromptChoice.Block) {
            pending.allowed
        } else {
            pending.allowed + prompted
        }
        continuePermissionAccess(pending.copy(allowed = allowed, prompted = emptySet()))
    }

    fun respondToHttpAuthPrompt(promptId: Long, username: String, password: String) {
        val pending = pendingHttpAuthChallenge?.takeIf { it.promptId == promptId } ?: return
        if (!isHttpAuthChallengeCurrent(pending)) {
            cancelPendingHttpAuthChallenge()
            return
        }
        pendingHttpAuthChallenge = null
        httpAuthPrompt = null
        runCatching { pending.confirm(username, password) }
            .onFailure { runCatching(pending.dismiss) }
        scheduleResidentSessionTrim()
    }

    fun cancelHttpAuthPrompt(promptId: Long) {
        if (pendingHttpAuthChallenge?.promptId != promptId) return
        cancelPendingHttpAuthChallenge()
    }

    fun confirmWebPrompt(promptId: Long, value: String? = null) {
        val pending = pendingWebPrompt?.takeIf { it.promptId == promptId } ?: return
        if (!isWebPromptCurrent(pending)) {
            cancelPendingWebPrompt()
            return
        }
        val prompt = webPrompt
        pendingWebPrompt = null
        webPrompt = null
        val shareLaunched = if (prompt?.kind == BrowserWebPromptKind.Share) {
            prompt.shareUri?.let { uri ->
                PageShareRequest.create(uri, prompt.title.orEmpty())
                    ?.let(PageShareLauncher(activity)::launch)
            } == PageShareResult.Launched
        } else true
        val responseValue = if (prompt?.kind == BrowserWebPromptKind.Share) {
            null
        } else value?.take(BrowserWebPromptRules.MAX_INPUT_LENGTH)
        runCatching {
            if (shareLaunched) pending.response.confirm(responseValue) else pending.response.dismiss()
        }
            .onFailure { runCatching(pending.response::dismiss) }
    }

    fun cancelWebPrompt(promptId: Long) {
        if (pendingWebPrompt?.promptId != promptId) return
        cancelPendingWebPrompt()
    }

    fun onRuntimePermissionResult(results: Map<String, Boolean>) {
        pendingGeckoAndroidPermissionRequest?.let { pending ->
            pendingGeckoAndroidPermissionRequest = null
            val isCurrent = isGeckoRendererCurrent(
                tabId = pending.tabId,
                session = pending.session,
                navigationGeneration = pending.navigationGeneration,
                requireSelected = true,
            )
            pending.request.response.complete(
                isCurrent && pending.request.permissions.all { permission ->
                    results[permission] == true || hasRuntimePermission(permission)
                },
            )
            return
        }
        val pending = pendingPermissionAccess?.takeIf(PendingPermissionAccess::awaitingRuntime)
            ?: return
        if (!isPermissionRequestCurrent(pending.identity, requireResumed = false)) {
            cancelPendingPermissionAccess(pending.identity.tabId)
            return
        }
        val granted = PermissionRequestRules.afterRuntimeResult(pending.allowed) { permission ->
            when (permission) {
                SitePermission.Location -> permission.runtimePermissions.any { runtimePermission ->
                    results[runtimePermission] == true || hasRuntimePermission(runtimePermission)
                }
                else -> permission.runtimePermissions.all { runtimePermission ->
                    results[runtimePermission] == true || hasRuntimePermission(runtimePermission)
                }
            }
        }
        finishPermissionAccess(pending, granted)
    }

    fun onFileChooserResult(resultCode: Int, data: Intent?) {
        val pending = pendingFileChooser ?: return
        if (!isFileChooserCurrent(pending.identity)) {
            pendingFileChooser = null
            finalizeFileCapture(pending.captureOutput, keep = false)
            pending.delivery.complete(null)
            scheduleResidentSessionTrim()
            return
        }
        val parsed = if (resultCode == Activity.RESULT_OK) {
            buildList {
                pending.captureOutput?.let { output -> add(output.uri.toString()) }
                data?.data?.let { add(it.toString()) }
                data?.clipData?.let { clip ->
                    repeat(clip.itemCount) { index -> add(clip.getItemAt(index).uri.toString()) }
                }
            }
        } else {
            emptyList()
        }
        runCatching {
            fileChooserValidationExecutor.execute {
                val safeUris = FileChooserRules.sanitizedUris(parsed, pending.allowMultiple)
                    .map(Uri::parse)
                    .filter { uri -> isSafeFileChooserResult(uri, pending.acceptTypes) }
                    .toTypedArray()
                    .takeIf(Array<Uri>::isNotEmpty)
                mainHandler.post {
                    if (
                        pendingFileChooser !== pending ||
                        !isFileChooserCurrent(pending.identity)
                    ) {
                        if (pendingFileChooser === pending) pendingFileChooser = null
                        finalizeFileCapture(pending.captureOutput, keep = false)
                        pending.delivery.complete(null)
                        scheduleResidentSessionTrim()
                    } else {
                        pendingFileChooser = null
                        finalizeFileCapture(pending.captureOutput, keep = safeUris != null)
                        pending.delivery.complete(safeUris)
                        scheduleResidentSessionTrim()
                    }
                }
            }
        }.onFailure {
            if (pendingFileChooser === pending) pendingFileChooser = null
            finalizeFileCapture(pending.captureOutput, keep = false)
            pending.delivery.complete(null)
            scheduleResidentSessionTrim()
        }
    }

    fun privacySnapshot(tabId: String): PrivacyXRaySnapshot =
        privacySnapshots[tabId] ?: PrivacyXRaySnapshot.Empty

    fun filterRule(ruleId: String): CandyRule? = filterRules.firstOrNull { it.id == ruleId }

    fun filterRulesFor(tabId: String): List<CandyRule> =
        if (tabs.firstOrNull { it.id == tabId }?.isIncognito == true) {
            filterRules.map { rule ->
                rule.copy(
                    hitCount = (rule.hitCount.toLong() + incognitoRuleHits.getOrDefault(rule.id, 0))
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                )
            }
        } else {
            filterRules.filterNot { it.id in ephemeralRuleIds }
        }

    fun filterSubscriptionRulesFor(tabId: String): List<CandyRule> =
        if (tabs.firstOrNull { it.id == tabId }?.isIncognito == true) {
            filterRules.filter { it.id in ephemeralRuleIds }
        } else {
            filterRules.filterNot { it.id in ephemeralRuleIds }
        }

    fun filterStudioTestUrl(tabId: String): String =
        pageUrls[tabId] ?: tabs.firstOrNull { it.id == tabId }?.url.orEmpty()

    fun testFilterRule(tabId: String, requestHostOrUrl: String): CandyRuleDecision? {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        val requestUrl = if (CandyHostCanonicalizer.webHost(requestHostOrUrl) != null) {
            requestHostOrUrl
        } else {
            CandyHostCanonicalizer.canonicalHost(requestHostOrUrl)?.let { "https://$it/" }
                ?: return null
        }
        return matcherFor(tab.isIncognito).decide(
            requestUrl = requestUrl,
            pageUrl = filterStudioTestUrl(tabId),
            profileId = tab.profileId,
            isForMainFrame = false,
        )
    }

    fun siteProtectionState(tabId: String): SiteProtectionState {
        siteExceptionRevision
        val tab = tabs.firstOrNull { it.id == tabId } ?: return SiteProtectionState()
        val host = PrivacyRequestSanitizer.webHost(pageUrls[tabId] ?: tab.url)
            ?: return SiteProtectionState(canPersist = SiteExceptionRules.mayPersist(tab.isIncognito))
        val temporaryPaused = SiteExceptionRules.isPaused(
            pageHost = host,
            exceptions = temporarySiteExceptions[tabId].orEmpty(),
        )
        val persistentPaused = !tab.isIncognito && SiteExceptionRules.isPaused(
            pageHost = host,
            exceptions = permanentSiteExceptions[tab.profileId].orEmpty(),
        )
        return SiteProtectionState(
            host = host,
            isPaused = temporaryPaused || persistentPaused,
            isPersistent = persistentPaused,
            canPersist = SiteExceptionRules.mayPersist(tab.isIncognito),
            cookieBannerRemovalDisabled = isCookieBannerRemovalDisabled(tab, host),
            forceVerticalScrolling = isForcedVerticalScrolling(tab, host),
            forcePageZooming = isPageZoomingForced(tab, host),
            forceSafeArea = isSafeAreaForced(tab, host),
            thirdPartyLoginAllowed = isFederatedLoginCompatibilityEnabled(tab, pageUrls[tabId]),
            captchaCompatibilityAllowed = isCaptchaCompatibilityEnabled(tab, pageUrls[tabId]),
        )
    }

    fun addFilterRuleFromXRay(
        tabId: String,
        requestHost: String,
        action: CandyRuleAction,
        siteScoped: Boolean,
    ): CandyRule? {
        if (action == CandyRuleAction.Cosmetic) return null
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        val safeRequestHost = CandyHostCanonicalizer.canonicalHost(requestHost) ?: return null
        val firstPartyHost = if (siteScoped) {
            val pageHost = CandyHostCanonicalizer.webHost(filterStudioTestUrl(tabId)) ?: return null
            CandyPublicSuffixRules.registrableDomain(pageHost) ?: return null
        } else {
            null
        }
        val candidate = CandyRule.new(
            action = action,
            kind = if (siteScoped) CandyRuleKind.HostPair else CandyRuleKind.RequestHost,
            requestHost = safeRequestHost,
            firstPartyHost = firstPartyHost,
            group = activity.getString(
                if (tab.isIncognito) R.string.filter_group_private else R.string.filter_group_xray,
            ),
            origin = CandyRuleOrigin.PrivacyXRay,
        )
        return addFilterRule(candidate, temporary = tab.isIncognito)
    }

    fun addFilterRule(candidate: CandyRule, temporary: Boolean = selectedTab.isIncognito): CandyRule? {
        val validated = (CandyRuleValidator.validate(candidate) as? CandyRuleValidation.Valid)?.rule
            ?: return null
        val duplicate = filterRules.firstOrNull { existing ->
            existing.id == validated.id ||
                (existing.action == validated.action &&
                existing.kind == validated.kind &&
                existing.requestHost == validated.requestHost &&
                existing.firstPartyHost == validated.firstPartyHost &&
                existing.cosmeticSelector == validated.cosmeticSelector &&
                existing.profileId == validated.profileId)
        }
        if (duplicate != null) return duplicate.takeIf { sameRuleSemantics(it, validated) }
        if (filterRules.size >= CandyRuleValidator.MAX_RULES) return null
        if (validated.kind == CandyRuleKind.CosmeticCss &&
            filterRules.count { it.kind == CandyRuleKind.CosmeticCss } >=
            CandyRuleValidator.MAX_COSMETIC_RULES
        ) return null
        filterRules += validated
        if (temporary) ephemeralRuleIds += validated.id
        onFilterRulesChanged(persist = !temporary)
        return validated
    }

    fun setFilterRuleActive(ruleId: String, active: Boolean): Boolean {
        val index = filterRules.indexOfFirst { it.id == ruleId }
        if (index < 0 || filterRules[index].active == active) return false
        filterRules[index] = filterRules[index].copy(active = active)
        onFilterRulesChanged(persist = ruleId !in ephemeralRuleIds)
        return true
    }

    fun updateFilterRule(candidate: CandyRule): CandyRule? {
        val index = filterRules.indexOfFirst { it.id == candidate.id }
        if (index < 0) return null
        val validated = (CandyRuleValidator.validate(candidate) as? CandyRuleValidation.Valid)?.rule
            ?: return null
        if (filterRules.any { it.id != validated.id && sameRuleSemantics(it, validated) }) return null
        val previous = filterRules[index]
        if (previous.kind != CandyRuleKind.CosmeticCss &&
            validated.kind == CandyRuleKind.CosmeticCss &&
            filterRules.count { it.kind == CandyRuleKind.CosmeticCss } >=
            CandyRuleValidator.MAX_COSMETIC_RULES
        ) return null
        filterRules[index] = validated.copy(hitCount = previous.hitCount)
        onFilterRulesChanged(persist = validated.id !in ephemeralRuleIds)
        return filterRules[index]
    }

    fun deleteFilterRule(ruleId: String): Boolean {
        val removed = filterRules.firstOrNull { it.id == ruleId } ?: return false
        filterRules.remove(removed)
        val temporary = ephemeralRuleIds.remove(ruleId)
        onFilterRulesChanged(persist = !temporary)
        privacySnapshots.replaceAll { _, snapshot ->
            snapshot.copy(
                domains = snapshot.domains.map { domain ->
                    if (domain.ruleDecision?.ruleId == ruleId) {
                        domain.copy(
                            ruleDecision = domain.ruleDecision.copy(
                                label = activity.getString(R.string.filter_rule_deleted),
                            ),
                        )
                    } else {
                        domain
                    }
                },
            )
        }
        return true
    }

    fun importFilterRules(text: String): CandyRulePreview = CandyRuleImport.parse(text)

    fun applyFilterImport(preview: CandyRulePreview): Int {
        if (!preview.isApplicable) return 0
        val profileIds = profiles.map(BrowserProfile::id)
        if (preview.rules.any { !CandyImportScope.isAllowed(it.profileId, profileIds) }) return 0
        val temporary = selectedTab.isIncognito
        val additions = prepareRuleBatch(preview.rules) ?: return 0
        if (additions.isEmpty()) return 0
        filterRules += additions
        if (temporary) ephemeralRuleIds += additions.map(CandyRule::id)
        onFilterRulesChanged(persist = !temporary)
        return additions.size
    }

    fun applyFilterSubscription(sourceUrl: String, preview: CandyRulePreview): Int {
        if (!preview.isApplicable || !CandyRuleValidator.isSafeHttpsUrl(sourceUrl)) return 0
        val targetScopes = preview.rules.map(CandyRule::profileId).toSet()
        if (targetScopes.size != 1) return 0
        val targetProfileId = targetScopes.first()
        if (!CandyImportScope.isAllowed(targetProfileId, profiles.map(BrowserProfile::id))) return 0
        val temporary = selectedTab.isIncognito
        val sourcePrefix = if (temporary) {
            "private-${UUID.randomUUID()}"
        } else {
            "subscription-${sourceUrl.hashCode().toUInt().toString(16)}"
        }
        val imported = CandyRuleValidator.normalizeAll(
            preview.rules.mapIndexed { index, rule ->
                rule.copy(
                    id = "$sourcePrefix-$index-${semanticRuleKey(rule).hashCode().toUInt().toString(16)}",
                    origin = CandyRuleOrigin.Subscription,
                    sourceUrl = sourceUrl,
                    updatedAtMillis = System.currentTimeMillis(),
                    group = CandyFilterPresets.groupFor(sourceUrl)
                        ?: runCatching { java.net.URI(sourceUrl).host }.getOrNull()?.take(48)
                        ?: activity.getString(R.string.filter_group_subscription),
                )
            },
        )
        if (temporary) {
            val oldIds = filterRules.asSequence()
                .filter {
                    CandySubscriptionRules.isSameSourceScope(it, sourceUrl, targetProfileId) &&
                        it.id in ephemeralRuleIds
                }
                .map(CandyRule::id)
                .toSet()
            val retained = filterRules.filterNot { it.id in oldIds }
            val persistentSourceIds = retained.asSequence()
                .filter {
                    CandySubscriptionRules.isSameSourceScope(it, sourceUrl, targetProfileId) &&
                        it.id !in ephemeralRuleIds
                }
                .map(CandyRule::id)
                .toSet()
            val additions = prepareRuleBatch(
                input = imported,
                base = retained,
                ignoreSemanticsForIds = persistentSourceIds,
            ) ?: return 0
            filterRules.removeAll { it.id in oldIds }
            ephemeralRuleIds.removeAll(oldIds)
            incognitoRuleHits.keys.removeAll(oldIds)
            filterRules += additions
            ephemeralRuleIds += additions.map(CandyRule::id)
            onFilterRulesChanged(persist = false)
            return additions.size
        }
        val oldIds = filterRules.asSequence()
            .filter {
                CandySubscriptionRules.isSameSourceScope(it, sourceUrl, targetProfileId) &&
                    it.id !in ephemeralRuleIds
            }
            .map(CandyRule::id)
            .toSet()
        val retained = filterRules.filterNot { it.id in oldIds }
        val additions = prepareRuleBatch(imported, retained) ?: return 0
        filterRules.removeAll { it.id in oldIds }
        filterRules += additions
        onFilterRulesChanged(persist = true)
        return additions.size
    }

    fun exportFilterRules(): String = CandyRuleFormat.export(
        filterRules.filterNot { it.id in ephemeralRuleIds },
    )

    private fun prepareRuleBatch(
        input: List<CandyRule>,
        base: List<CandyRule> = filterRules,
        ignoreSemanticsForIds: Set<String> = emptySet(),
    ): List<CandyRule>? {
        val existingIds = base.mapTo(mutableSetOf(), CandyRule::id)
        val existingSemantics = base.asSequence()
            .filterNot { it.id in ignoreSemanticsForIds }
            .mapTo(mutableSetOf(), CandySubscriptionRules::storageKey)
        var cosmeticCount = base.count { it.kind == CandyRuleKind.CosmeticCss }
        val additions = ArrayList<CandyRule>(input.size)
        for (inputRule in input) {
            var rule = (CandyRuleValidator.validate(inputRule) as? CandyRuleValidation.Valid)?.rule
                ?: return null
            if (rule.origin == CandyRuleOrigin.Import && rule.group == "Imported") {
                rule = rule.copy(group = activity.getString(R.string.filter_group_imported))
            }
            val semantics = CandySubscriptionRules.storageKey(rule)
            if (!existingSemantics.add(semantics)) continue
            if (rule.id in existingIds) rule = rule.copy(id = UUID.randomUUID().toString())
            if (base.size + additions.size >= CandyRuleValidator.MAX_RULES) return null
            if (rule.kind == CandyRuleKind.CosmeticCss &&
                ++cosmeticCount > CandyRuleValidator.MAX_COSMETIC_RULES
            ) return null
            existingIds += rule.id
            additions += rule
        }
        return additions
    }

    private fun semanticRuleKey(rule: CandyRule): String = listOf(
        rule.action.name,
        rule.kind.name,
        rule.requestHost.orEmpty(),
        rule.firstPartyHost.orEmpty(),
        rule.cosmeticSelector.orEmpty(),
        rule.profileId.orEmpty(),
    ).joinToString("\u0000")

    private fun sameRuleSemantics(left: CandyRule, right: CandyRule): Boolean =
        semanticRuleKey(left) == semanticRuleKey(right)

    init {
        filterRules += candyRuleRepository.load()
        userScripts += userScriptRepository.load()
        if (usesGeckoEngine) {
            geckoEngineSessionFactory.setBlockThirdPartyCookies(
                workerSettings.blockThirdPartyCookies,
            )
            geckoEngineSessionFactory.setExtensionChromeHost(
                object : GeckoExtensionChromeHost {
                    override fun currentSessionIdentity(): GeckoExtensionSessionIdentity? =
                        geckoEngineSessionFactory.extensionSessionIdentity(selectedTabId)
                            ?.takeIf { geckoEngineSessions.containsKey(it.tabId) }

                    override fun isCurrentSession(identity: GeckoExtensionSessionIdentity): Boolean =
                        geckoEngineSessions.containsKey(identity.tabId) &&
                            geckoEngineSessionFactory.extensionSessionIdentity(identity.tabId) == identity

                    override fun createTab(
                        request: GeckoExtensionCreateTabRequest,
                        session: GeckoSession,
                    ): String? {
                        val existingTabIds = tabs.mapTo(hashSetOf(), BrowserTab::id)
                        val source = request.source
                        val tabId = if (request.active) {
                            this@BrowserController.createTab(
                                isIncognito = source?.isPrivate == true,
                                openerTabId = source?.tabId,
                            )
                        } else {
                            this@BrowserController.createBackgroundTab(
                                initialUrl = BLANK_URL,
                                openerTabId = source?.tabId,
                                isIncognito = source?.isPrivate == true,
                            ) ?: return null
                        }
                        if (tabId in existingTabIds) return null
                        if (request.pinned) this@BrowserController.setTabPinned(tabId, true)
                        if (
                            request.index?.let { index ->
                                !this@BrowserController.positionExtensionCreatedTab(tabId, index)
                            } == true
                        ) {
                            this@BrowserController.closeTab(tabId)
                            return null
                        }
                        if (!geckoEngineSessionFactory.prepareSession(tabId, session)) {
                            this@BrowserController.closeTab(tabId)
                            return null
                        }
                        this@BrowserController.geckoEngineSessionFor(tabId)
                        return tabId
                    }

                    override fun updateTab(request: GeckoExtensionUpdateTabRequest): Boolean {
                        if (!isCurrentSession(request.target)) return false
                        request.pinned?.let { pinned ->
                            this@BrowserController.setTabPinned(request.target.tabId, pinned)
                        }
                        request.muted?.let { muted ->
                            if (!this@BrowserController.setExtensionTabMuted(
                                    tabId = request.target.tabId,
                                    muted = muted,
                                )
                            ) {
                                return false
                            }
                        }
                        if (request.active == true) {
                            this@BrowserController.selectTab(request.target.tabId)
                        }
                        return true
                    }

                    override fun closeTab(target: GeckoExtensionSessionIdentity): Boolean {
                        if (!isCurrentSession(target)) return false
                        val before = tabs.size
                        this@BrowserController.closeTab(target.tabId)
                        return tabs.size < before
                    }

                    override fun openPopup(
                        popup: GeckoExtensionPopupIdentity,
                        session: GeckoSession,
                        toggle: Boolean,
                    ): Boolean {
                        if (!isCurrentSession(popup.owner)) return false
                        this@BrowserController.releaseFirefoxExtensionPopupView()
                        val view = GeckoView(activity).also { geckoView ->
                            geckoView.setSession(session)
                        }
                        firefoxExtensionPopupIdentity = popup
                        firefoxExtensionPopupView = view
                        return true
                    }

                    override fun closePopup(popup: GeckoExtensionPopupIdentity) {
                        if (firefoxExtensionPopupIdentity != popup) return
                        this@BrowserController.releaseFirefoxExtensionPopupView()
                    }

                    override fun openOptionsPage(
                        extensionId: String,
                        owner: GeckoExtensionSessionIdentity,
                        url: String,
                        openInTab: Boolean,
                    ): String? {
                        if (!isCurrentSession(owner)) return null
                        val existingTabIds = tabs.mapTo(hashSetOf(), BrowserTab::id)
                        val tabId = this@BrowserController.createTab(
                            isIncognito = owner.isPrivate,
                            openerTabId = owner.tabId,
                        )
                        if (tabId in existingTabIds) return null
                        if (!this@BrowserController.geckoEngineSessionFor(tabId).loadExtensionUrl(url)) {
                            this@BrowserController.closeTab(tabId)
                            return null
                        }
                        return tabId
                    }

                    override fun onActionsChanged(actions: List<GeckoExtensionActionState>) {
                        firefoxExtensionActions.clear()
                        firefoxExtensionActions.addAll(actions)
                    }

                },
            )
            geckoEngineSessionFactory.setToppingInteractionDelegate(
                object : GeckoToppingInteractionDelegate {
                    override fun onMenuCommandsChanged(
                        tabId: String,
                        commands: List<UserScriptMenuCommand>,
                    ) {
                        if (commands.isEmpty()) {
                            userScriptCommandsByTab.remove(tabId)
                        } else {
                            userScriptCommandsByTab[tabId] = commands
                        }
                    }

                    override fun onOpenTab(request: UserScriptOpenTabRequest) {
                        openUserScriptTab(request)
                    }
                },
            )
            geckoEngineSessionFactory.setToppingHostStateListener { state ->
                isUserScriptSupportedState = state == GeckoToppingHostState.Ready
            }
            geckoEngineSessionFactory.reconcileToppings(userScripts)
        }
        rebuildCandyMatcher()
        val nowMillis = System.currentTimeMillis()
        snoozedTabs += snoozedTabStore.load()
        candyTrailRepository.processPendingRedactions()
        blockerSettings = workerSettings
        inactiveTabLifetime = store.loadInactiveTabLifetime()
        searxngSettings = store.loadSearxngSettings()
        residentTabLimit = store.loadResidentTabLimit()
        searchEngine = store.loadSearchEngine()
        pageTranslationProvider = store.loadPageTranslationProvider()
        linkLongPressAction = store.loadLinkLongPressAction()
        linkPeekActionLayout = store.loadLinkPeekActionLayout()
        isAiModeToggleVisible = store.loadAiModeToggleVisible()
        isRecallEnabled = store.loadRecallEnabled()
        if (!isRecallEnabled) recallRepository.clearAsync()
        searchSuggestionProvider = store.loadSearchSuggestionProvider(
            fallback = defaultSearchSuggestionProvider(),
        )
        isHistorySuggestionsEnabled = store.loadHistorySuggestionsEnabled()
        dismissResistancePercent = store.loadDismissResistancePercent()
        tabOverviewMode = store.loadTabOverviewMode()
        tabStackFolderMode = store.loadTabStackFolderMode()
        tabListStartsAtBottom = store.loadTabListStartsAtBottom()
        automaticTabSortingEnabled = store.loadAutomaticTabSortingEnabled()
        isAddressBarDockingEnabled = store.loadAddressBarDockingEnabled()
        isExternalLinkPreviewEnabled = store.loadExternalLinkPreviewEnabled()
        val storedAddressBarDockPlacement = store.loadAddressBarDockPlacement()
        lastAddressBarDockPlacement = store.loadLastAddressBarDockPlacement()
            ?: storedAddressBarDockPlacement
            ?: AddressBarDockPlacement.Default
        addressBarDockPlacement = storedAddressBarDockPlacement
            .takeIf { isAddressBarDockingEnabled }
        if (!isAddressBarDockingEnabled && storedAddressBarDockPlacement != null) {
            store.saveAddressBarDockPlacement(null)
        }
        addressBarActionLayout = store.loadAddressBarActionLayout()
        isFullImmersiveModeEnabled = store.loadFullImmersiveModeEnabled()
        isStartupAnimationEnabled = store.loadStartupAnimationEnabled()
        isOpenHomeOnStartupEnabled = store.loadOpenHomeOnStartupEnabled()
        isScrollBarEnabled = store.loadScrollBarEnabled()
        isVideoAutoplayBlocked =
            isVideoAutoplayBlockingSupported && store.loadVideoAutoplayBlocked()
        appearanceSettings = store.loadAppearanceSettings()
        if (usesGeckoEngine) {
            geckoEngineSessionFactory.setWebContentFontSizeFactor(
                appearanceSettings.webContentFontSizePercent / 100f,
            )
        }
        downloadSettings = store.loadDownloadSettings()
        refreshExternalDownloadManagers()
        store.clearLegacyWebContentEdgeToEdgePreference()
        profilesEnabled = store.loadProfilesEnabled()
        isDefaultBrowser = DefaultBrowserRole.isHeld(activity)
        val (restoredProfiles, restoredActiveProfileId) = store.loadProfiles()
        profiles += restoredProfiles.take(MAX_PROFILES)
        val restoredProfileIds = profiles.mapTo(mutableSetOf(), BrowserProfile::id)
        siteCapsules += siteCapsuleStore.load()
            .filter { capsule -> capsule.profileId in restoredProfileIds }
            .let(SiteCapsuleRules::bounded)
        siteCapsuleStore.save(siteCapsules)
        siteCapsuleIconStore.cleanup(siteCapsules.mapTo(hashSetOf(), SiteCapsule::id))
        permanentSiteExceptions = permanentSiteExceptions
            .filterKeys(restoredProfileIds::contains)
            .mapValues { (_, hosts) ->
                hosts.mapNotNull(SiteExceptionRules::normalizedException)
                    .take(SiteExceptionRules.MAX_PER_PROFILE)
                    .toSet()
            }
        store.savePermanentSiteExceptions(permanentSiteExceptions)
        permanentSitePrivacyOverrides = permanentSitePrivacyOverrides
            .filterKeys(restoredProfileIds::contains)
        store.saveSitePrivacyOverrides(permanentSitePrivacyOverrides)
        permanentMutedDomains.keys.retainAll(restoredProfileIds)
        store.saveMutedDomains(permanentMutedDomains.toMap())
        permanentDesktopViewDomains.keys.retainAll(restoredProfileIds)
        store.saveDesktopViewDomains(permanentDesktopViewDomains.toMap())
        permanentAlwaysBlockPopupDomains.keys.retainAll(restoredProfileIds)
        store.saveAlwaysBlockPopupDomains(permanentAlwaysBlockPopupDomains.toMap())
        activeProfileId = if (profilesEnabled) {
            restoredActiveProfileId.takeIf { id -> profiles.any { it.id == id } }
        } else {
            null
        } ?: profiles.first().id
        val configuredWallpapers = profiles.flatMapTo(hashSetOf()) { profile ->
            buildList {
                if (profile.newTabWallpaper != null) {
                    add(profile.id to ProfileWallpaperTarget.NewTab)
                }
                if (profile.tabSwitcherWallpaper != null) {
                    add(profile.id to ProfileWallpaperTarget.TabSwitcher)
                }
            }
        }
        profileWallpaperExecutor.execute {
            profileWallpaperStore.migrateLegacy(
                configuredWallpapers.mapTo(hashSetOf()) { (profileId, _) -> profileId },
            )
            profileWallpaperStore.cleanup(configuredWallpapers)
        }
        refreshActiveProfileWallpaper()
        val (restoredTabs, restoredSelection) = store.loadTabs(nowMillis)
        history += historyRepository.snapshot()
        favorites += store.loadFavorites()
        val profileIds = profiles.mapTo(mutableSetOf(), BrowserProfile::id)
        tabs += restoredTabs.take(MAX_TABS).map { tab ->
            if (tab.profileId in profileIds) tab else tab.copy(profileId = profiles.first().id)
        }
        val tabsBeforeInitialSnoozeRestore = tabs.toList()
        val snoozedBeforeInitialRestore = snoozedTabs.toList()
        val initialSnoozeRestore = SnoozeRestoreRules.restoreDue(
            tabs = tabs,
            snoozedTabs = snoozedTabs,
            profiles = profiles,
            activeProfileId = activeProfileId,
            nowMillis = nowMillis,
            maxTabs = MAX_TABS,
        )
        if (initialSnoozeRestore.completedTabIds.isNotEmpty()) {
            tabs.clear()
            tabs += initialSnoozeRestore.tabs
            val remaining = snoozedTabs.filterNot {
                it.tab.id in initialSnoozeRestore.completedTabIds
            }
            snoozedTabs.clear()
            snoozedTabs += remaining
        }
        if (activeTabs.isEmpty()) tabs += newTabState(nowMillis = nowMillis)
        val rememberedSelection = profiles.first { it.id == activeProfileId }.selectedTabId
        selectedTabId = rememberedSelection
            ?.takeIf { id -> activeTabs.any { it.id == id } }
            ?: restoredSelection?.takeIf { id -> activeTabs.any { it.id == id } }
            ?: activeTabs.first().id
        rememberSelectedTab(activeProfileId, selectedTabId)
        pruneStaleTabs(nowMillis, persistChanges = false)
        touchTab(selectedTabId, nowMillis)
        if (initialSnoozeRestore.completedTabIds.isNotEmpty()) {
            val snapshotPersisted = store.saveTabsAndSnoozedImmediately(
                tabs = persistableTabs(tabs),
                selectedTabId = selectedTabId,
                snoozedTabs = snoozedTabs,
            )
            val startupSnapshot = SnoozeRestoreRules.settleStartupRestore(
                originalTabs = tabsBeforeInitialSnoozeRestore,
                originalSnoozedTabs = snoozedBeforeInitialRestore,
                restoredTabs = tabs.toList(),
                remainingSnoozedTabs = snoozedTabs.toList(),
                snapshotPersisted = snapshotPersisted,
            )
            if (snapshotPersisted) {
                SnoozeWakeNotifier(activity).notifyRestored(
                    tabs.filter {
                        it.id in initialSnoozeRestore.restoredTabIds &&
                            (profilesEnabled || it.profileId == profiles.first().id)
                    },
                )
            } else {
                tabs.clear()
                tabs += startupSnapshot.tabs
                snoozedTabs.clear()
                snoozedTabs += startupSnapshot.snoozedTabs
                if (activeTabs.isEmpty()) tabs += newTabState(nowMillis = nowMillis)
                selectedTabId = rememberedSelection
                    ?.takeIf { id -> activeTabs.any { it.id == id } }
                    ?: restoredSelection?.takeIf { id -> activeTabs.any { it.id == id } }
                    ?: activeTabs.first().id
                rememberSelectedTab(activeProfileId, selectedTabId)
                touchTab(selectedTabId, nowMillis)
            }
        }
        tabStacks += store.loadTabStacks(tabs)
        persist()
        geckoSessionStateStore.prune(
            (tabs.asSequence() + snoozedTabs.asSequence().map(SnoozedTab::tab))
                .filterNot(BrowserTab::isIncognito)
                .mapTo(linkedSetOf(), BrowserTab::id),
        )
        restorePersistedPreviews()
        restorePersistedFavicons()
        restorePersistedCandyTrails()
        SnoozeRuntimeRegistry.register(snoozeRestoreCallback)
        snoozeScheduler.schedule(snoozedTabs, nowMillis)
        syncObservation = syncRepository.observe { state ->
            mainHandler.post {
                if (!destroyed) applySyncRepositoryState(state)
            }
        }
    }

    fun attachSelectedBrowserEngineView(
        container: FrameLayout,
        onContentPresented: ((String) -> Unit)? = null,
    ): View? {
        if (browsingDataClearPending) {
            container.removeAllViews()
            return null
        }
        return attachSelectedGeckoView(container, onContentPresented)
    }

    private fun attachSelectedGeckoView(
        container: FrameLayout,
        onContentPresented: ((String) -> Unit)?,
    ): View? {
        val selectedSessionIsBeingReleased =
            geckoEngineSessions[selectedTabId] in geckoViewSessionsBeingReleased
        if (isGeckoViewBindingMutationInProgress || selectedSessionIsBeingReleased) {
            if (
                !isGeckoViewBindingMutationInProgress ||
                container !in geckoViewMutationHosts
            ) {
                scheduleGeckoViewAttachRetry(container, onContentPresented)
            }
            val binding = geckoViewBindings[container]
            return binding?.view?.takeIf { view ->
                binding.tabId == selectedTabId && view.parent === container
            }
        }
        isGeckoViewBindingMutationInProgress = true
        geckoViewMutationHosts += container
        return try {
            attachSelectedGeckoViewOnce(container, onContentPresented)
        } finally {
            geckoViewMutationHosts.clear()
            isGeckoViewBindingMutationInProgress = false
        }
    }

    private fun scheduleGeckoViewAttachRetry(
        container: FrameLayout,
        onContentPresented: ((String) -> Unit)?,
    ) {
        if (pendingGeckoViewAttachRetries.containsKey(container)) return
        val retryToken = Any()
        pendingGeckoViewAttachRetries[container] = retryToken
        container.post {
            if (pendingGeckoViewAttachRetries[container] !== retryToken) return@post
            pendingGeckoViewAttachRetries.remove(container)
            if (!destroyed && container.isAttachedToWindow) {
                attachSelectedGeckoView(container, onContentPresented)
            }
        }
    }

    private fun attachSelectedGeckoViewOnce(
        container: FrameLayout,
        onContentPresented: ((String) -> Unit)?,
    ): View? {
        fun awaitContent(binding: GeckoViewBinding) {
            if (onContentPresented == null) return
            binding.session.awaitContentPresented {
                container.post {
                    if (
                        geckoViewBindings[container] === binding &&
                        binding.tabId == selectedTabId &&
                        binding.view.parent === container
                    ) {
                        onContentPresented(binding.tabId)
                    }
                }
            }
        }

        val current = geckoViewBindings[container]
        if (current?.tabId == selectedTabId && current.view.parent === container) {
            awaitContent(current)
            return current.view
        }
        if (
            current?.tabId == selectedTabId &&
            geckoMediaPresentation?.view === current.view
        ) {
            container.removeAllViews()
            return null
        }
        if (current?.tabId == selectedTabId && current.view.parent == null) {
            container.removeAllViews()
            attachGeckoViewBinding(container, current)
            dispatchCurrentWindowInsets(current.view, current.tabId)
            awaitContent(current)
            return current.view
        }
        current?.let { binding ->
            releaseGeckoView(binding.session, binding.view)
            geckoViewBindings.remove(container)
        }
        container.removeAllViews()
        val tabId = selectedTabId
        val engineSession = geckoEngineSessionFor(tabId)
        val transferable = geckoViewBindings.entries.firstOrNull { (host, binding) ->
            host !== container &&
                binding.tabId == tabId &&
                binding.session === engineSession &&
                binding.view !== geckoMediaPresentation?.view
        }
        if (transferable != null) {
            val sourceContainer = transferable.key
            val binding = transferable.value
            geckoViewMutationHosts += sourceContainer
            (binding.view.parent as? ViewGroup)?.removeView(binding.view)
            geckoViewBindings.remove(sourceContainer)
            attachGeckoViewBinding(container, binding)
            dispatchCurrentWindowInsets(binding.view, binding.tabId)
            awaitContent(binding)
            return binding.view
        }
        val mediaPresentationOwnsRenderer = geckoMediaPresentation?.let { presentation ->
            presentation.tabId == tabId &&
                presentation.session === engineSession &&
                geckoViewBindings.values.any { binding ->
                    binding.tabId == tabId &&
                        binding.session === engineSession &&
                        binding.view === presentation.view
                }
        } == true
        if (mediaPresentationOwnsRenderer) return null
        val view = engineSession.createView(container.context)
        val binding = GeckoViewBinding(
            tabId = tabId,
            session = engineSession,
            view = view,
        )
        attachGeckoViewBinding(container, binding)
        dispatchCurrentWindowInsets(view, tabId)
        awaitContent(binding)
        return view
    }

    /** Registers ownership before addView can synchronously re-enter Compose's AndroidView update. */
    private fun attachGeckoViewBinding(
        container: FrameLayout,
        binding: GeckoViewBinding,
    ) {
        geckoViewBindings[container] = binding
        try {
            container.addView(
                binding.view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        } catch (failure: RuntimeException) {
            if (geckoViewBindings[container] === binding) {
                geckoViewBindings.remove(container)
                releaseGeckoView(binding.session, binding.view)
            }
            throw failure
        }
    }

    private fun releaseGeckoView(
        session: AndroidBrowserEngineSessionPort,
        view: View,
    ) {
        geckoViewSessionsBeingReleased += session
        try {
            session.releaseView(view)
        } finally {
            geckoViewSessionsBeingReleased -= session
        }
    }

    fun onWindowInsetsChanged(insets: WindowInsetsCompat) {
        val previousInsets = lastWindowInsets
        lastWindowInsets = insets
        if (
            browserChromeOwnsIme &&
            previousInsets != null &&
            hasSameNonImeInsets(previousInsets, insets)
        ) {
            return
        }
        // Compose owns the root inset listener. AndroidView children do not receive that
        // callback, so forward every change to the active engine views.
        dispatchWindowInsetsToAttachedEngineViews(insets)
        if (
            previousInsets?.getInsets(SAFE_AREA_INSET_TYPES)?.top !=
            insets.getInsets(SAFE_AREA_INSET_TYPES).top
        ) {
            refreshGeckoContentTopInsetPolicies()
        }
    }

    fun setBrowserChromeOwnsIme(ownsIme: Boolean) {
        if (browserChromeOwnsIme == ownsIme) return
        browserChromeOwnsIme = ownsIme
        val insets = lastWindowInsets ?: return
        dispatchWindowInsetsToAttachedEngineViews(insets)
    }

    private fun dispatchWindowInsetsToAttachedEngineViews(insets: WindowInsetsCompat) {
        geckoViewBindings.values.forEach { binding ->
            if (binding.view.isAttachedToWindow) {
                applyGeckoWindowInsets(binding.view, binding.tabId, insets)
            }
        }
        geckoLinkPeekBindings.values.forEach { binding ->
            if (binding.view.isAttachedToWindow) {
                applyGeckoWindowInsets(
                    view = binding.view,
                    tabId = null,
                    insets = insets,
                    isInsideSafeDrawingHost = true,
                )
            }
        }
        externalLinkPreviewRuntime?.binding?.view
            ?.takeIf(View::isAttachedToWindow)
            ?.let { view -> applyGeckoWindowInsets(view, tabId = null, insets) }
    }

    fun detachBrowserEngineView(container: FrameLayout) {
        pendingGeckoViewAttachRetries.remove(container)
        if (isGeckoViewBindingMutationInProgress) {
            if (container !in geckoViewMutationHosts) {
                mainHandler.post { if (!destroyed) detachBrowserEngineView(container) }
            }
            return
        }
        isGeckoViewBindingMutationInProgress = true
        geckoViewMutationHosts += container
        try {
            val binding = geckoViewBindings[container]
            if (binding?.view === geckoMediaPresentation?.view) {
                container.removeAllViews()
                return
            }
            geckoViewBindings.remove(container)?.let { detached ->
                releaseGeckoView(detached.session, detached.view)
            }
            container.removeAllViews()
        } finally {
            geckoViewMutationHosts.clear()
            isGeckoViewBindingMutationInProgress = false
        }
    }

    internal fun fullscreenVideoPlacement(
        videoOnlyPresentation: Boolean,
    ): FullscreenVideoPlacement? = FullscreenVideoRules.placement(
        sessionTabId = fullscreenVideoState?.tabId,
        selectedTabId = selectedTabId,
        minimizedByUser = fullscreenVideoState?.minimizedByUser == true,
        videoOnlyPresentation = videoOnlyPresentation,
    )

    internal fun attachFullscreenVideoView(
        container: FrameLayout,
        isInsideSafeDrawingHost: Boolean = false,
    ) {
        val presentation = geckoMediaPresentation ?: return
        if (
            geckoEngineSessions[presentation.tabId] !== presentation.session ||
            geckoViewBindings.values.none { binding ->
                binding.tabId == presentation.tabId &&
                    binding.session === presentation.session &&
                    binding.view === presentation.view
            }
        ) {
            clearGeckoMediaPresentation()
            return
        }
        fullscreenVideoInsideSafeDrawingHost = isInsideSafeDrawingHost
        val videoView = presentation.view
        if (videoView.parent === container && container.childCount == 1) {
            dispatchCurrentWindowInsets(
                view = videoView,
                tabId = presentation.tabId,
                isInsideSafeDrawingHost = isInsideSafeDrawingHost,
            )
            return
        }
        (videoView.parent as? ViewGroup)?.removeView(videoView)
        container.removeAllViews()
        container.addView(
            videoView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        dispatchCurrentWindowInsets(
            view = videoView,
            tabId = presentation.tabId,
            isInsideSafeDrawingHost = isInsideSafeDrawingHost,
        )
    }

    internal fun detachFullscreenVideoView(container: FrameLayout) {
        container.removeAllViews()
    }

    internal fun minimizeFullscreenVideo() {
        if (presentationIsPrivate() != false) return
        geckoMediaPresentation?.let { presentation ->
            presentation.minimizedByUser = true
            fullscreenVideoInsideSafeDrawingHost = true
        }
        publishFullscreenVideoState()
    }

    internal fun expandFullscreenVideo() {
        val tabId = presentationTabId() ?: return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return clearGeckoMediaPresentation()
        geckoMediaPresentation?.minimizedByUser = false
        fullscreenVideoInsideSafeDrawingHost = false
        if (tab.profileId != activeProfileId && !selectProfile(tab.profileId)) return
        selectTab(tab.id)
        publishFullscreenVideoState()
    }

    internal fun exitFullscreenVideo() {
        notifyGeckoPictureInPictureModeChanged(false)
        pictureInPicturePlaybackRetryGeneration++
        pictureInPictureOwnerTabId = null
        pictureInPicturePlaybackExpected = false
        geckoEngineSessions[presentationTabId()]?.executeMediaCommand(GeckoMediaCommand.Stop)
        clearGeckoMediaPresentation()
    }

    fun prepareForPictureInPicture() = prepareGeckoPictureInPicture()

    private fun prepareGeckoPictureInPicture() {
        val tab = tabs.firstOrNull { candidate -> candidate.id == selectedTabId } ?: return
        val mediaState = geckoMediaStates[tab.id]
        if (
            !GeckoPictureInPictureRules.isEligible(
                state = mediaState,
                isPrivate = tab.isIncognito,
                isSelectedTab = true,
            )
        ) return
        val session = geckoEngineSessions[tab.id] ?: return
        val binding = geckoViewBindings.values.firstOrNull { candidate ->
            candidate.tabId == tab.id && candidate.session === session
        } ?: return
        val current = geckoMediaPresentation
        if (current == null) {
            fullscreenVideoInsideSafeDrawingHost = false
            geckoMediaPresentation = GeckoMediaPresentation(
                tabId = tab.id,
                session = session,
                view = binding.view,
                minimizedByUser = false,
            )
            publishFullscreenVideoState()
        } else if (
            current.tabId != tab.id ||
            current.session !== session ||
            current.view !== binding.view
        ) {
            return
        }
        if (!pictureInPictureTransitionPending && !isInPictureInPicture) {
            pictureInPictureTransitionGeneration++
        }
        pictureInPicturePlaybackExpected =
            GeckoPictureInPictureRules.playbackExpectedDuringTransition(
                currentExpected = pictureInPicturePlaybackExpected,
                transitionPending = pictureInPictureTransitionPending,
                inPictureInPicture = isInPictureInPicture,
                mediaIsPlaying = mediaState?.isPlaying == true,
            )
        pictureInPictureTransitionPending = true
        pictureInPictureOwnerTabId = tab.id
        session.setPictureInPicturePlaybackExpected(pictureInPicturePlaybackExpected)
        notifyGeckoPictureInPictureModeChanged(true)
    }

    private fun clearGeckoMediaPresentation() {
        val presentation = geckoMediaPresentation ?: return
        if (pictureInPictureCompositorSession === presentation.session) {
            notifyGeckoPictureInPictureModeChanged(false)
        }
        fullscreenVideoInsideSafeDrawingHost = false
        geckoMediaPresentation = null
        publishFullscreenVideoState()
    }

    fun cancelPictureInPictureTransition() {
        if (isInPictureInPicture) return
        val ownerSession = activeMediaCommandSession()
        notifyGeckoPictureInPictureModeChanged(false)
        pictureInPictureTransitionGeneration++
        pictureInPicturePlaybackRetryGeneration++
        pictureInPictureTransitionPending = false
        pictureInPictureOwnerTabId = null
        pictureInPicturePlaybackExpected = false
        if (!isActivityResumed) ownerSession?.setActive(false)
        scheduleResidentSessionTrim()
    }

    fun onPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        isInPictureInPicture = inPictureInPicture
        if (inPictureInPicture) {
            prepareGeckoPictureInPicture()
            pictureInPictureTransitionPending = false
            currentPictureInPicturePresentation()?.session?.setActive(true)
            notifyGeckoPictureInPictureModeChanged(true)
            resumePictureInPicturePlayback()
        } else {
            val returnsToPresentation = pictureInPictureOwnerTabId != null
            notifyGeckoPictureInPictureModeChanged(
                inPictureInPicture = false,
                preservePlaybackExpectation = returnsToPresentation,
            )
            pictureInPictureTransitionGeneration++
            pictureInPicturePlaybackRetryGeneration++
            pictureInPictureTransitionPending = returnsToPresentation
            if (!returnsToPresentation) {
                pictureInPictureOwnerTabId = null
                pictureInPicturePlaybackExpected = false
            }
        }
        scheduleResidentSessionTrim()
    }

    private fun notifyGeckoPictureInPictureModeChanged(
        inPictureInPicture: Boolean,
        preservePlaybackExpectation: Boolean = false,
    ) {
        if (!inPictureInPicture) {
            pictureInPictureCompositorSession?.let { session ->
                if (!preservePlaybackExpectation) {
                    session.setPictureInPicturePlaybackExpected(false)
                }
                session.notifyPictureInPictureModeChanged(false)
            }
            pictureInPictureCompositorSession = null
            return
        }
        val presentation = currentPictureInPicturePresentation() ?: return
        if (pictureInPictureCompositorSession === presentation.session) {
            presentation.session.notifyPictureInPictureModeChanged(true)
            return
        }
        pictureInPictureCompositorSession
            ?.notifyPictureInPictureModeChanged(false)
        presentation.session.notifyPictureInPictureModeChanged(true)
        pictureInPictureCompositorSession = presentation.session
    }

    private fun resumePictureInPicturePlayback() {
        if (!pictureInPicturePlaybackExpected) return
        val presentation = currentPictureInPicturePresentation() ?: return
        presentation.session.setPictureInPicturePlaybackExpected(true)
        presentation.session.executeMediaCommand(GeckoMediaCommand.Play)
        val retryGeneration = ++pictureInPicturePlaybackRetryGeneration
        PICTURE_IN_PICTURE_PLAY_RETRY_DELAYS_MILLIS.forEach { delayMillis ->
            mainHandler.postDelayed(
                {
                    if (
                        retryGeneration != pictureInPicturePlaybackRetryGeneration ||
                        !isInPictureInPicture ||
                        !pictureInPicturePlaybackExpected ||
                        currentPictureInPicturePresentation() !== presentation
                    ) {
                        return@postDelayed
                    }
                    presentation.session.executeMediaCommand(GeckoMediaCommand.Play)
                },
                delayMillis,
            )
        }
    }

    private fun currentPictureInPicturePresentation(): GeckoMediaPresentation? {
        val presentation = geckoMediaPresentation ?: return null
        if (
            presentation.tabId != pictureInPictureOwnerTabId ||
            geckoEngineSessions[presentation.tabId] !== presentation.session ||
            geckoViewBindings.values.none { binding ->
                binding.tabId == presentation.tabId &&
                    binding.session === presentation.session &&
                    binding.view === presentation.view
            }
        ) {
            return null
        }
        return presentation
    }

    fun completePictureInPictureReturn() {
        if (isInPictureInPicture || !pictureInPictureTransitionPending) return
        val presentation = currentPictureInPicturePresentation()
        if (pictureInPicturePlaybackExpected) {
            presentation?.session?.executeMediaCommand(GeckoMediaCommand.Play)
        }
        presentation?.session?.setPictureInPicturePlaybackExpected(false)
        pictureInPicturePlaybackRetryGeneration++
        pictureInPictureTransitionPending = false
        pictureInPictureOwnerTabId = null
        pictureInPicturePlaybackExpected = false
        if (
            presentation != null &&
            geckoMediaStates[presentation.tabId]?.isFullscreen != true
        ) {
            clearGeckoMediaPresentation()
        }
        scheduleResidentSessionTrim()
    }

    /** Builds an ephemeral Gecko renderer without registering a tab or writing history. */
    fun createLinkPeekPreviewView(
        url: String,
        onProgressChanged: (Int) -> Unit,
        onCommittedUrlChanged: (String) -> Unit,
    ): View {
        val safeUrl = requireNotNull(BrowserUriPolicy.normalizeHttpUrl(url))
        val sourceTab = tabs.first { it.id == selectedTabId }
        return createGeckoLinkPeekPreview(
            sourceTab = sourceTab,
            url = safeUrl,
            onProgressChanged = onProgressChanged,
            onCommittedUrlChanged = onCommittedUrlChanged,
        )
    }

    fun releaseLinkPeekPreviewView(view: View) {
        geckoLinkPeekBindings.remove(view)?.let { binding ->
            releaseGeckoView(binding.session, binding.view)
            binding.session.execute(BrowserEngineCommands.close())
            return
        }
    }

    private fun createGeckoLinkPeekPreview(
        sourceTab: BrowserTab,
        url: String,
        onProgressChanged: (Int) -> Unit,
        onCommittedUrlChanged: (String) -> Unit,
    ): View {
        val previewTabId = "link-peek-${++nextGeckoLinkPeekId}"
        var binding: GeckoLinkPeekBinding? = null
        val initialContext = protectionRequestContextFor(sourceTab, url)
        lateinit var session: AndroidBrowserEngineSessionPort
        session = geckoEngineSessionFactory.create(
            tabId = previewTabId,
            profileId = sourceTab.profileId,
            isolationEnabled = profileForId(sourceTab.profileId)?.isolationEnabled == true,
            isPrivate = sourceTab.isIncognito,
            privacyPolicy = geckoPrivacyPolicyFor(
                tab = sourceTab,
                pageUrl = url,
                context = initialContext,
            ),
            privacyEventSink = GeckoPrivacyEventSink { },
            eventSink = { event ->
                event.address?.let(BrowserUriPolicy::normalizeHttpUrl)?.let { committedUrl ->
                    binding?.committedUrl = committedUrl
                    binding?.title = event.title?.takeIf(String::isNotBlank)
                    session.setDesktopMode(isDesktopView(sourceTab, committedUrl))
                    session.updatePrivacyPolicy(
                        geckoPrivacyPolicyFor(
                            tab = sourceTab,
                            pageUrl = committedUrl,
                            context = protectionRequestContextFor(sourceTab, committedUrl),
                        ),
                    )
                    onCommittedUrlChanged(committedUrl)
                }
                when (event.type) {
                    BrowserEngineEventType.NavigationStarted -> {
                        binding?.isLoading = true
                        binding?.progress = 0
                        onProgressChanged(0)
                    }
                    BrowserEngineEventType.NavigationCommitted,
                    BrowserEngineEventType.NavigationFailed,
                    -> {
                        binding?.isLoading = false
                        binding?.progress = 100
                        onProgressChanged(100)
                    }
                    BrowserEngineEventType.StateChanged,
                    BrowserEngineEventType.Crashed,
                    BrowserEngineEventType.Closed,
                    -> Unit
                }
            },
        )
        val view = session.createView(activity).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isEnabled = false
            isLongClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        session.setVideoAutoplayBlocked(isVideoAutoplayBlocked)
        session.setDesktopMode(isDesktopView(sourceTab, url))
        binding = GeckoLinkPeekBinding(
            sourceTabId = sourceTab.id,
            contentRevision = contentActions.revision,
            session = session,
            view = view,
            committedUrl = url,
        )
        geckoLinkPeekBindings[view] = binding
        dispatchCurrentWindowInsets(view, tabId = null, isInsideSafeDrawingHost = true)
        session.execute(BrowserEngineCommands.load(url))
        return view
    }

    fun openExternalLinkPreview(
        url: String,
        allowInitialAppHandoff: Boolean = false,
        restoredAppHandoffExpirationElapsedRealtime: Long? = null,
    ): Boolean {
        val safeUrl = ExternalLinkPreviewRules.safeCurrentUrl(url) ?: return false
        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        val appHandoffExpiration = restoredAppHandoffExpirationElapsedRealtime
            ?.takeIf { expiration -> expiration >= nowElapsedRealtime }
            ?: if (allowInitialAppHandoff) {
                nowElapsedRealtime + ExternalNavigationGrantRules.MAX_LIFETIME_MILLIS
            } else {
                null
            }
        val targetProfileId = ExternalLinkPreviewRules.targetProfileId(
            profiles = localBrowserProfiles,
            profilesEnabled = profilesEnabled,
            requestedProfileId = null,
            activeProfileId = activeProfileId,
        ) ?: return false
        closeFindInPage()
        contentActions.dismiss()
        releaseExternalLinkPreviewRuntime(resumeSelectedTab = false)
        minimizeGeckoMediaForTabDeparture(selectedTabId)
        geckoEngineSessions[selectedTabId]?.setActive(false)

        val sessionId = ++nextExternalLinkPreviewSessionId
        externalLinkPreviewState = ExternalLinkPreviewState(
            sessionId = sessionId,
            generation = 0,
            currentUrl = safeUrl,
            targetProfileId = targetProfileId,
            appHandoffExpiresAtElapsedRealtime = appHandoffExpiration,
        )
        return true
    }

    fun prepareExternalLinkPreview(sessionId: Long): Boolean {
        externalLinkPreviewRuntime?.let { runtime ->
            return runtime.sessionId == sessionId
        }
        val state = externalLinkPreviewState?.takeIf { it.sessionId == sessionId }
            ?: return false
        createExternalLinkPreviewRuntime(state)
        return true
    }

    fun selectExternalLinkPreviewProfile(sessionId: Long, profileId: String): Boolean {
        val current = externalLinkPreviewState?.takeIf { it.sessionId == sessionId }
            ?: return false
        val targetProfileId = ExternalLinkPreviewRules.targetProfileId(
            profiles = localBrowserProfiles,
            profilesEnabled = profilesEnabled,
            requestedProfileId = profileId,
            activeProfileId = activeProfileId,
        ) ?: return false
        if (targetProfileId == current.targetProfileId) return false
        val safeUrl = ExternalLinkPreviewRules.safeCurrentUrl(current.currentUrl) ?: return false
        releaseExternalLinkPreviewRuntime(resumeSelectedTab = false)
        val updatedState = current.copy(
            generation = current.generation + 1,
            currentUrl = safeUrl,
            targetProfileId = targetProfileId,
            appHandoffExpiresAtElapsedRealtime = null,
            progress = 0,
            isLoading = true,
            canGoBack = false,
            isContentReady = false,
        )
        if (true) {
            createExternalLinkPreviewRuntime(updatedState)
        }
        else externalLinkPreviewState = updatedState
        return true
    }

    fun dismissExternalLinkPreview(sessionId: Long? = null): Boolean {
        val state = externalLinkPreviewState ?: return false
        if (sessionId != null && state.sessionId != sessionId) return false
        releaseExternalLinkPreviewRuntime(resumeSelectedTab = true)
        return true
    }

    fun goBackInExternalLinkPreview(sessionId: Long): Boolean {
        val runtime = externalLinkPreviewRuntime
            ?.takeIf { it.sessionId == sessionId }
            ?: return false
        runtime.geckoBinding.let { binding ->
            if (externalLinkPreviewState?.canGoBack != true) return false
            runtime.downloadGrant = null
            externalNavigationGrants.remove(runtime.policyTab.id)
            clearExternalLinkPreviewAppHandoff(sessionId)
            binding.session.historyUrlAtOffset(-1)?.let { targetUrl ->
                binding.session.setDesktopMode(isDesktopView(runtime.policyTab, targetUrl))
            }
            binding.session.execute(BrowserEngineCommands.stop())
            binding.session.execute(BrowserEngineCommands.back())
            return true
        }
        return false
    }

    fun commitExternalLinkPreview(sessionId: Long): ExternalLinkPreviewCommitResult {
        val state = externalLinkPreviewState?.takeIf { it.sessionId == sessionId }
            ?: return ExternalLinkPreviewCommitResult.MissingPreview
        pruneStaleTabs()
        if (tabs.size >= MAX_TABS) {
            showTabLimitReached()
            return ExternalLinkPreviewCommitResult.TabLimitReached
        }
        val safeUrl = ExternalLinkPreviewRules.safeCurrentUrl(state.currentUrl)
            ?: return ExternalLinkPreviewCommitResult.MissingPreview
        val targetProfileId = ExternalLinkPreviewRules.targetProfileId(
            profiles = localBrowserProfiles,
            profilesEnabled = profilesEnabled,
            requestedProfileId = state.targetProfileId,
            activeProfileId = activeProfileId,
        ) ?: return ExternalLinkPreviewCommitResult.MissingPreview
        if (profilesEnabled && targetProfileId != activeProfileId) {
            selectProfile(targetProfileId)
        }
        val previousTabId = selectedTabId
        val tabId = createTab(
            initialUrl = safeUrl,
            isIncognito = false,
            openerTabId = previousTabId,
        )
        return if (tabId == previousTabId) {
            ExternalLinkPreviewCommitResult.TabLimitReached
        } else {
            releaseExternalLinkPreviewRuntime(resumeSelectedTab = false)
            if (usesGeckoEngine && isActivityResumed) {
                geckoEngineSessions[selectedTabId]?.setActive(true)
            }
            ExternalLinkPreviewCommitResult.Opened(tabId)
        }
    }

    fun attachExternalLinkPreview(container: FrameLayout) {
        val runtime = externalLinkPreviewRuntime ?: run {
            container.removeAllViews()
            return
        }
        val view = runtime.binding.view
        if (view.parent === container && container.childCount == 1) return
        (view.parent as? ViewGroup)?.removeView(view)
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        dispatchCurrentWindowInsets(view, tabId = null)
        startExternalLinkPreviewIfReady(runtime)
        if (isActivityResumed) {
            runtime.geckoBinding.session.setActive(true)
        }
    }

    fun detachExternalLinkPreview(container: FrameLayout) {
        externalLinkPreviewRuntime
            ?.takeIf { runtime -> runtime.binding.view.parent === container }
            ?.geckoBinding
            ?.session
            ?.setActive(false)
        container.removeAllViews()
    }

    fun shareExternalLinkPreview(sessionId: Long) {
        val state = externalLinkPreviewState?.takeIf { it.sessionId == sessionId } ?: return
        val request = PageShareRequest.create(url = state.currentUrl, title = "") ?: return
        if (pageShare.launch(request) == PageShareResult.Unsupported) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_no_matching_app),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openExternalLinkPreviewFindInPage(sessionId: Long): Boolean {
        val runtime = externalLinkPreviewRuntime
            ?.takeIf { it.sessionId == sessionId }
            ?: return false
        val session = runtime.geckoBinding.session
        closeFindInPage()
        findInPageSession = FindInPageSession(
            id = ++nextFindInPageSessionId,
            tabId = runtime.policyTab.id,
            geckoSession = session,
            navigationGeneration = runtime.generation,
        )
        findInPageState = FindInPageState(tabId = runtime.policyTab.id)
        return true
    }

    val isExternalLinkPreviewDesktopView: Boolean
        get() {
            val runtime = externalLinkPreviewRuntime ?: return false
            return isDesktopView(runtime.policyTab, externalLinkPreviewState?.currentUrl)
        }

    fun setExternalLinkPreviewDesktopView(sessionId: Long, enabled: Boolean): Boolean {
        val runtime = externalLinkPreviewRuntime
            ?.takeIf { it.sessionId == sessionId }
            ?: return false
        val state = externalLinkPreviewState ?: return false
        val domain = DesktopSiteRules.domainForUrl(state.currentUrl) ?: return false
        val current = permanentDesktopViewDomains[runtime.policyTab.profileId].orEmpty()
        val updated = DesktopSiteRules.withDesktopViewState(
            current = current,
            domain = domain,
            enabled = enabled,
        )
        if (updated == current) return false
        if (updated.isEmpty()) permanentDesktopViewDomains.remove(runtime.policyTab.profileId)
        else permanentDesktopViewDomains[runtime.policyTab.profileId] = updated
        store.saveDesktopViewDomains(permanentDesktopViewDomains.toMap())
        reloadDesktopViewDomain(runtime.policyTab.profileId, isIncognito = false, domain = domain)
        recreateExternalLinkPreviewRuntime(state)
        return true
    }

    private fun recreateExternalLinkPreviewRuntime(state: ExternalLinkPreviewState) {
        releaseExternalLinkPreviewRuntime(resumeSelectedTab = false)
        createExternalLinkPreviewRuntime(
            state.copy(
                generation = state.generation + 1,
                appHandoffExpiresAtElapsedRealtime = null,
                progress = 0,
                isLoading = true,
                canGoBack = false,
            ),
        )
    }

    private fun createExternalLinkPreviewRuntime(state: ExternalLinkPreviewState) {
        val policyTab = BrowserTab(
            id = "external-preview-${state.sessionId}",
            lastAccessedAt = System.currentTimeMillis(),
            profileId = state.targetProfileId,
            url = state.currentUrl,
            isLoading = true,
        )
        createExternalLinkPreviewGeckoRuntime(state, policyTab)
    }

    private fun createExternalLinkPreviewGeckoRuntime(
        state: ExternalLinkPreviewState,
        policyTab: BrowserTab,
    ) {
        val requestContext = protectionRequestContextFor(policyTab, state.currentUrl)
        synchronized(privacyEventLock) {
            protectionRequestContexts[policyTab.id] = requestContext
        }
        lateinit var session: AndroidBrowserEngineSessionPort
        session = geckoEngineSessionFactory.create(
            tabId = policyTab.id,
            profileId = policyTab.profileId,
            isolationEnabled = profileForId(policyTab.profileId)?.isolationEnabled == true,
            isPrivate = false,
            privacyPolicy = geckoPrivacyPolicyFor(
                tab = policyTab,
                pageUrl = state.currentUrl,
                context = requestContext,
            ),
            privacyEventSink = GeckoPrivacyEventSink { },
            eventSink = { event ->
                mainHandler.post {
                    onExternalLinkPreviewGeckoEvent(
                        sessionId = state.sessionId,
                        generation = state.generation,
                        session = session,
                        event = event,
                    )
                }
            },
        )
        session.setNavigationRequestListener { request ->
            onExternalLinkPreviewGeckoNavigationRequest(
                sessionId = state.sessionId,
                generation = state.generation,
                session = session,
                request = request,
            )
        }
        session.setDownloadResponseListener { response ->
            val runtime = currentExternalLinkPreviewGeckoRuntime(
                sessionId = state.sessionId,
                generation = state.generation,
                session = session,
            ) ?: return@setDownloadResponseListener response.close()
            val metadata = response.metadata
            val nowElapsedRealtime = SystemClock.elapsedRealtime()
            if (!ExternalPreviewDownloadGrantRules.canConsume(
                    grant = runtime.downloadGrant,
                    url = metadata.url,
                    nowElapsedRealtime = nowElapsedRealtime,
                )
            ) return@setDownloadResponseListener response.close()
            val request = BrowserEngineDownloadRules.request(
                response = metadata,
                referrer = externalLinkPreviewState?.currentUrl,
            ) ?: return@setDownloadResponseListener response.close()
            if (!BrowserDownloadRequestFactory.isAndroidPackage(request)) {
                return@setDownloadResponseListener response.close()
            }
            runtime.downloadGrant = null
            externalNavigationGrants.remove(runtime.policyTab.id)
            response.start(
                object : GeckoDownloadTransferListener {
                    override fun onStarted(start: GeckoDownloadTransferStart) {
                        showDownloadResult(
                            DownloadActionResult.Enqueued(start.id.toLong(), start.fileName),
                        )
                        mainHandler.post { dismissExternalLinkPreview(state.sessionId) }
                    }

                    override fun onFailed(reason: GeckoDownloadFailure) {
                        showDownloadResult(
                            DownloadActionResult.Failed(
                                activity.getString(R.string.error_download_start_failed),
                            ),
                        )
                    }
                },
            )
        }
        session.setDesktopMode(isDesktopView(policyTab, state.currentUrl))
        val view = session.createView(activity)
        externalLinkPreviewRuntime = ExternalLinkPreviewRuntime(
            sessionId = state.sessionId,
            generation = state.generation,
            policyTab = policyTab,
            binding = ExternalLinkPreviewEngineBinding.Gecko(
                session = session,
                view = view,
            ),
        )
        externalLinkPreviewState = state.copy(isContentReady = true)
    }

    private fun onExternalLinkPreviewGeckoEvent(
        sessionId: Long,
        generation: Int,
        session: AndroidBrowserEngineSessionPort,
        event: BrowserEngineEvent,
    ) {
        val runtime = currentExternalLinkPreviewGeckoRuntime(
            sessionId = sessionId,
            generation = generation,
            session = session,
        ) ?: return
        val state = externalLinkPreviewState ?: return
        val wasSafeAreaForced = isExternalLinkPreviewSafeAreaForced(runtime.binding.view)
        val safeUrl = ExternalLinkPreviewRules.safeCurrentUrl(event.address)
        if (event.type == BrowserEngineEventType.NavigationStarted &&
            findInPageSession?.geckoSession === session
        ) {
            closeFindInPage()
        }
        if (event.type == BrowserEngineEventType.NavigationStarted && safeUrl != null) {
            val requestContext = protectionRequestContextFor(runtime.policyTab, safeUrl)
            synchronized(privacyEventLock) {
                protectionRequestContexts[runtime.policyTab.id] = requestContext
            }
            session.updatePrivacyPolicy(
                policy = geckoPrivacyPolicyFor(
                    tab = runtime.policyTab,
                    pageUrl = safeUrl,
                    context = requestContext,
                ),
                reloadOnCookiePermissionChange = true,
            )
        }
        if (event.type == BrowserEngineEventType.NavigationCommitted && safeUrl != null) {
            if (
                clearExternalNavigationGrantForCallback(
                    tabId = runtime.policyTab.id,
                    callbackUrl = safeUrl,
                    currentBrowserUrl = safeUrl,
                )
            ) {
                clearExternalLinkPreviewAppHandoff(sessionId)
            }
        }
        externalLinkPreviewState = when (event.type) {
            BrowserEngineEventType.NavigationStarted -> state.copy(
                currentUrl = safeUrl ?: state.currentUrl,
                progress = 0,
                isLoading = true,
                canGoBack = event.canGoBack,
            )
            BrowserEngineEventType.NavigationCommitted,
            BrowserEngineEventType.NavigationFailed,
            -> state.copy(
                currentUrl = safeUrl ?: state.currentUrl,
                progress = 100,
                isLoading = false,
                canGoBack = event.canGoBack,
            )
            BrowserEngineEventType.StateChanged -> state.copy(
                currentUrl = safeUrl ?: state.currentUrl,
                canGoBack = event.canGoBack,
            )
            BrowserEngineEventType.Crashed,
            BrowserEngineEventType.Closed,
            -> state.copy(
                progress = 100,
                isLoading = false,
                isContentReady = false,
                canGoBack = false,
            )
        }
        if (wasSafeAreaForced != isExternalLinkPreviewSafeAreaForced(runtime.binding.view)) {
            dispatchCurrentWindowInsets(runtime.binding.view, tabId = null)
        }
    }

    private fun onExternalLinkPreviewGeckoNavigationRequest(
        sessionId: Long,
        generation: Int,
        session: AndroidBrowserEngineSessionPort,
        request: GeckoMainFrameNavigationRequest,
    ): GeckoNavigationRequestDecision {
        val runtime = currentExternalLinkPreviewGeckoRuntime(
            sessionId = sessionId,
            generation = generation,
            session = session,
        ) ?: return GeckoNavigationRequestDecision.Deny
        val safeHttpUrl = ExternalLinkPreviewRules.safeCurrentUrl(request.url)
        if (safeHttpUrl != null && runtime.pendingInternalNavigationUrl == safeHttpUrl) {
            runtime.pendingInternalNavigationUrl = null
            return GeckoNavigationRequestDecision.Allow
        }
        val scheme = runCatching { Uri.parse(request.url).scheme }.getOrNull()?.lowercase()
        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        if (safeHttpUrl != null) {
            runtime.downloadGrant = if (request.hasUserGesture) {
                ExternalPreviewDownloadGrantRules.start(
                    url = safeHttpUrl,
                    nowElapsedRealtime = nowElapsedRealtime,
                )
            } else {
                runtime.downloadGrant?.let { grant ->
                    ExternalPreviewDownloadGrantRules.followRedirect(
                        grant = grant,
                        url = safeHttpUrl,
                        isForMainFrame = true,
                        isRedirect = request.isRedirect,
                        nowElapsedRealtime = nowElapsedRealtime,
                    )
                }
            }
            updateExternalNavigationGrant(
                tabId = runtime.policyTab.id,
                url = safeHttpUrl,
                isForMainFrame = true,
                hasGesture = request.hasUserGesture,
                isRedirect = request.isRedirect,
                nowElapsedRealtime = nowElapsedRealtime,
            )
            val hasUserNavigationGrant = ExternalNavigationGrantRules.isActive(
                externalNavigationGrants[runtime.policyTab.id],
                nowElapsedRealtime,
            )
            if (
                ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                    scheme = scheme,
                    isForMainFrame = true,
                    hasGesture = request.hasUserGesture,
                    isRedirect = request.isRedirect,
                    hasUserNavigationGrant = hasUserNavigationGrant,
                ) && externalApps.openWebUrlExternally(safeHttpUrl) ==
                ExternalLaunchResult.Launched
            ) {
                externalNavigationGrants.remove(runtime.policyTab.id)
                clearExternalLinkPreviewAppHandoff(sessionId)
                showExternalAppOpenedToast()
                return GeckoNavigationRequestDecision.Deny
            }
            session.setDesktopMode(isDesktopView(runtime.policyTab, safeHttpUrl))
            return GeckoNavigationRequestDecision.Allow
        }
        val hasUserNavigationGrant = ExternalNavigationGrantRules.isActive(
            externalNavigationGrants[runtime.policyTab.id],
            nowElapsedRealtime,
        )
        if (
            !ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = scheme,
                isForMainFrame = true,
                hasGesture = request.hasUserGesture,
                isRedirect = request.isRedirect,
                hasUserNavigationGrant = hasUserNavigationGrant,
            )
        ) return GeckoNavigationRequestDecision.Deny
        externalNavigationGrants.remove(runtime.policyTab.id)
        clearExternalLinkPreviewAppHandoff(sessionId)
        return when (val result = externalApps.open(Uri.parse(request.url))) {
            ExternalLaunchResult.Launched -> {
                showExternalAppOpenedToast()
                GeckoNavigationRequestDecision.Deny
            }
            is ExternalLaunchResult.OpenInBrowser -> {
                mainHandler.post {
                    navigateExternalLinkPreviewGecko(runtime, result.url)
                }
                GeckoNavigationRequestDecision.Deny
            }
            ExternalLaunchResult.Unsupported -> {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_no_matching_app),
                    Toast.LENGTH_SHORT,
                ).show()
                GeckoNavigationRequestDecision.Deny
            }
        }
    }

    private fun currentExternalLinkPreviewGeckoRuntime(
        sessionId: Long,
        generation: Int,
        session: AndroidBrowserEngineSessionPort,
    ): ExternalLinkPreviewRuntime? {
        val runtime = externalLinkPreviewRuntime ?: return null
        if (destroyed || !ExternalLinkPreviewRules.isCurrent(
                state = externalLinkPreviewState,
                sessionId = sessionId,
                generation = generation,
            ) || runtime.sessionId != sessionId || runtime.generation != generation ||
            runtime.geckoBinding.session !== session || runtime.policyTab.id != session.tabId
        ) return null
        return runtime
    }

    private fun navigateExternalLinkPreviewGecko(
        runtime: ExternalLinkPreviewRuntime,
        url: String,
    ) {
        val safeUrl = ExternalLinkPreviewRules.safeCurrentUrl(url) ?: return
        val binding = runtime.geckoBinding
        if (externalLinkPreviewRuntime !== runtime) return
        runtime.pendingInternalNavigationUrl = safeUrl
        binding.session.setDesktopMode(isDesktopView(runtime.policyTab, safeUrl))
        binding.session.execute(BrowserEngineCommands.stop())
        binding.session.execute(BrowserEngineCommands.load(safeUrl))
    }

    private fun startExternalLinkPreviewIfReady(runtime: ExternalLinkPreviewRuntime) {
        if (
            runtime !== externalLinkPreviewRuntime ||
            runtime.hasStarted ||
            !runtime.binding.view.isAttachedToWindow
        ) return
        runtime.hasStarted = true
        val state = externalLinkPreviewState?.takeIf { it.sessionId == runtime.sessionId } ?: return
        runtime.downloadGrant = ExternalPreviewDownloadGrantRules.start(
            url = state.currentUrl,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
        )
        state.appHandoffExpiresAtElapsedRealtime?.let { expiration ->
            val grant = ExternalNavigationGrant(
                currentUrl = state.currentUrl,
                expiresAtElapsedRealtime = expiration,
            )
            if (ExternalNavigationGrantRules.isActive(grant, SystemClock.elapsedRealtime())) {
                externalNavigationGrants[runtime.policyTab.id] = grant
            }
        }
        val binding = runtime.geckoBinding
        runtime.pendingInternalNavigationUrl = state.currentUrl
        binding.session.setActive(isActivityResumed)
        binding.session.execute(BrowserEngineCommands.load(state.currentUrl))
    }

    private fun clearExternalLinkPreviewAppHandoff(sessionId: Long) {
        val state = externalLinkPreviewState?.takeIf { it.sessionId == sessionId } ?: return
        if (state.appHandoffExpiresAtElapsedRealtime != null) {
            externalLinkPreviewState = state.copy(appHandoffExpiresAtElapsedRealtime = null)
        }
    }

    private fun releaseExternalLinkPreviewRuntime(resumeSelectedTab: Boolean) {
        val runtime = externalLinkPreviewRuntime
        if (findInPageSession?.geckoSession === runtime?.geckoBinding?.session) closeFindInPage()
        externalLinkPreviewRuntime = null
        externalLinkPreviewState = null
        runtime?.policyTab?.id?.let { policyTabId ->
            externalNavigationGrants.remove(policyTabId)
            synchronized(privacyEventLock) {
                protectionRequestContexts.remove(policyTabId)?.let(::flushPendingFilterHits)
            }
        }
        runtime?.geckoBinding?.let { binding ->
            (binding.view.parent as? ViewGroup)?.removeView(binding.view)
            binding.session.setActive(false)
            releaseGeckoView(binding.session, binding.view)
            binding.session.execute(BrowserEngineCommands.close())
        }
        if (resumeSelectedTab && isActivityResumed) {
            geckoEngineSessions[selectedTabId]?.setActive(true)
        }
    }

    private fun dispatchCurrentWindowInsets(
        view: View,
        tabId: String?,
        isInsideSafeDrawingHost: Boolean = false,
    ) {
        // A newly bound GeckoView can attach after the root traversal owned by Compose.
        view.doOnAttach { attachedView ->
            fun applyCurrentInsets() {
                val insets = ViewCompat.getRootWindowInsets(attachedView) ?: lastWindowInsets
                if (insets != null) {
                    applyGeckoWindowInsets(
                        view = attachedView,
                        tabId = tabId,
                        insets = insets,
                        isInsideSafeDrawingHost = isInsideSafeDrawingHost,
                    )
                }
            }
            applyCurrentInsets()
            if (!isInsideSafeDrawingHost) {
                attachedView.post {
                    if (attachedView.isAttachedToWindow) applyCurrentInsets()
                }
            }
        }
    }

    private fun applyGeckoWindowInsets(
        view: View,
        tabId: String?,
        insets: WindowInsetsCompat,
        isInsideSafeDrawingHost: Boolean = false,
    ) {
        val effectiveInsets = if (browserChromeOwnsIme) {
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()
        } else {
            insets
        }
        val safeArea = effectiveInsets.getInsets(SAFE_AREA_INSET_TYPES)
        val isFullscreenContent = tabId != null && fullscreenVideoState?.tabId == tabId
        val forceNativeSafeArea = if (tabId != null) {
            isSafeAreaForced(tabId) || tabId in nativeSafeAreaFallbackTabs
        } else {
            isExternalLinkPreviewSafeAreaForced(view)
        }
        val layout = GeckoViewInsetRules.resolve(
            safeArea = safeArea.toGeckoViewInsets(),
            forceNativeSafeArea = forceNativeSafeArea,
            useScrollableTopInset = tabId != null &&
                isScrollAwareTopInsetEnabled &&
                !forceNativeSafeArea,
            isFullscreenContent = isFullscreenContent,
            isInsideSafeDrawingHost = isInsideSafeDrawingHost ||
                (isFullscreenContent && fullscreenVideoInsideSafeDrawingHost),
        )
        (view.layoutParams as? FrameLayout.LayoutParams)?.let { layoutParams ->
            if (
                layoutParams.leftMargin != 0 ||
                layoutParams.topMargin != 0 ||
                layoutParams.rightMargin != 0 ||
                layoutParams.bottomMargin != 0
            ) {
                layoutParams.setMargins(0, 0, 0, 0)
                view.layoutParams = layoutParams
            }
        }
        // The outer host always fills the edge-to-edge window. Its inner GeckoView either receives
        // GeckoView 155's current root safe area or native margins for Candy's explicit override.
        (view as? GeckoViewInsetHost)?.updateInsets(layout, effectiveInsets)
    }

    private fun Insets.toGeckoViewInsets(): GeckoViewInsets = GeckoViewInsets(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )

    private fun hasSameNonImeInsets(
        previous: WindowInsetsCompat,
        current: WindowInsetsCompat,
    ): Boolean = NON_IME_INSET_TYPES.all { type ->
        previous.getInsets(type) == current.getInsets(type) &&
            previous.isVisible(type) == current.isVisible(type)
    }

    private fun drawsEdgeToEdge(tabId: String): Boolean =
        !isSafeAreaForced(tabId) &&
            isWebContentEdgeToEdgeEnabled

    fun submitAddress(
        input: String,
        searchMode: SearchMode = SearchMode.Web,
    ) {
        bottomBarCompactStates[selectedTabId] = false
        clearExternalNavigationAuthorization(selectedTabId)
        val externalUri = BrowserUriPolicy.normalizeExternalUri(input)?.let(Uri::parse)
        val target = when (val result = externalUri?.let(externalApps::open)) {
            ExternalLaunchResult.Launched -> {
                showExternalAppOpenedToast()
                return
            }
            is ExternalLaunchResult.OpenInBrowser -> result.url
            ExternalLaunchResult.Unsupported -> {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_no_matching_app),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            null -> {
                if (
                    searchEngine == SearchEngine.SearXNG &&
                    AddressResolver.isSearchQuery(input) &&
                    SearxngRules.normalizedInstanceUrl(searxngSettings.instanceUrl) == null
                ) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_searxng_instance_required),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                AddressResolver.resolve(
                    input = input,
                    searchEngine = searchEngine,
                    searchMode = searchMode,
                    searxngInstanceUrl = searxngSettings.instanceUrl,
                )
            }
        }
        activeCapsuleForTab(selectedTabId)?.let { capsule ->
            if (
                CapsuleNavigationRules.decide(capsule, target) ==
                CapsuleNavigationDecision.OpenInFullCandy
            ) {
                openCapsuleTargetInFullCandy(selectedTabId, target)
                return
            }
        }
        val tabId = selectedTabId
        val existingSession = geckoEngineSessions[tabId]
        updateTab(tabId) {
            it.copy(
                url = target,
                title = "",
                isLoading = target != BLANK_URL,
                progress = 0,
                error = null,
            )
        }
        if (target == BLANK_URL) {
            closeGeckoEngineSession(tabId)
        } else if (existingSession == null) {
            geckoEngineSessionFor(tabId)
        } else {
            loadGeckoWithPrivacy(tabId, existingSession, target)
        }
    }

    fun openUrl(
        url: String,
        inNewTab: Boolean = false,
        authorizeInitialExternalNavigation: Boolean = false,
    ): Boolean {
        leaveSiteCapsule()
        if (inNewTab) {
            val previousTabId = selectedTabId
            return createTab(
                initialUrl = url,
                openerTabId = previousTabId,
                authorizeInitialExternalNavigation = authorizeInitialExternalNavigation,
            ) != previousTabId
        }
        submitAddress(url)
        return true
    }

    fun openHistoryEntry(url: String, profileId: String): Boolean {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        val targetProfileId = profileId.takeIf { id -> profiles.any { it.id == id } }
            ?: return false
        if (profilesEnabled && targetProfileId != activeProfileId) {
            selectProfile(targetProfileId)
        }
        if (selectedTab.isIncognito) {
            createTab(initialUrl = safeUrl, isIncognito = false)
        } else {
            openUrl(safeUrl)
        }
        return true
    }

    internal fun applyHistoryClearRequests(requests: List<HistoryClearRequest>) {
        if (requests.isEmpty()) return
        val tabsById = (tabs + snoozedTabs.map { snoozed -> snoozed.tab })
            .associateBy(BrowserTab::id)
        val redactions = requests.mapIndexedNotNull { index, request ->
            val tabIds = tabsById.values.asSequence()
                .filterNot(BrowserTab::isIncognito)
                .filter { tab -> tab.profileId in request.profileIds }
                .mapTo(linkedSetOf(), BrowserTab::id)
            if (tabIds.isEmpty()) return@mapIndexedNotNull null
            PendingCandyTrailRedaction(
                id = "activity-result-$index",
                tabIds = tabIds,
                sinceInclusiveMillis = request.sinceInclusiveMillis,
                untilExclusiveMillis = request.untilExclusiveMillis,
            )
        }
        applyCandyTrailRedactions(redactions, tabsById)
    }

    private fun applyCandyTrailRedactions(
        redactions: List<PendingCandyTrailRedaction>,
        tabsById: Map<String, BrowserTab> =
            (tabs + snoozedTabs.map { snoozed -> snoozed.tab }).associateBy(BrowserTab::id),
    ) {
        if (redactions.isEmpty()) return
        if (isCandyTrailRestoreInProgress) candyTrailRedactionsDuringRestore += redactions
        var changed = false
        candyTrails.toMap().forEach { (tabId, trail) ->
            val tab = tabsById[tabId]?.takeUnless(BrowserTab::isIncognito) ?: return@forEach
            val matchingRedactions = redactions.filter { redaction -> tabId in redaction.tabIds }
            if (matchingRedactions.isEmpty()) return@forEach
            val retained = matchingRedactions.fold(trail) { current, redaction ->
                CandyTrailRules.removeVisitedRange(
                    trail = current,
                    sinceInclusiveMillis = redaction.sinceInclusiveMillis,
                    untilExclusiveMillis = redaction.untilExclusiveMillis,
                )
            }
            if (retained == trail) return@forEach
            changed = true
            val retainedNodeIds = retained.nodes.mapTo(mutableSetOf(), CandyTrailNode::id)
            candyTrailHistoryBindings[tabId]?.let { binding ->
                candyTrailHistoryBindings[tabId] = CandyTrailHistoryReconciler.retainNodeIds(
                    binding = binding,
                    retainedNodeIds = retainedNodeIds,
                )
            }
            pendingCandyTrailTargets[tabId]?.takeUnless(retainedNodeIds::contains)?.let {
                pendingCandyTrailTargets.remove(tabId)
            }
            if (trail.currentNodeId !in retainedNodeIds) suppressedCandyTrailTabIds += tabId
            setCandyTrail(tab, retained)
        }
        if (changed) reconcileCandyTrailForks(System.currentTimeMillis())
    }

    fun resolveCapsuleLaunch(action: String?, capsuleId: String?): CapsuleLaunchResolution =
        CapsuleIntentRules.resolve(action, capsuleId, siteCapsules)

    fun openSiteCapsule(capsuleId: String, navigateToStart: Boolean = true): Boolean {
        val capsule = siteCapsules.firstOrNull { it.id == capsuleId } ?: return false
        if (profiles.none { it.id == capsule.profileId }) return false
        if (activeProfileId != capsule.profileId && !selectProfile(capsule.profileId)) return false
        val rememberedTab = capsuleTabIds[capsule.id]
            ?.let { tabId -> activeTabs.firstOrNull { it.id == tabId && !it.isIncognito } }
        val matchingSelectedTab = selectedTab.takeIf { tab ->
            !tab.isIncognito &&
                tab.profileId == capsule.profileId &&
                (tab.isFreshBlankTab || tab.url == capsule.startUrl)
        }
        val targetTab = rememberedTab ?: matchingSelectedTab ?: run {
            val previousTabId = selectedTabId
            val createdTabId = createTab(isIncognito = false)
            if (createdTabId == previousTabId && !selectedTab.isFreshBlankTab) return false
            selectedTab
        }
        if (selectedTabId != targetTab.id) selectTab(targetTab.id)
        activeCapsuleId = capsule.id
        activeCapsuleTabId = targetTab.id
        capsuleTabIds[capsule.id] = targetTab.id
        capsuleShortcuts.reportUsed(capsule)
        if (navigateToStart && targetTab.url != capsule.startUrl) submitAddress(capsule.startUrl)
        return true
    }

    fun restoreSiteCapsule(capsuleId: String, tabId: String?): Boolean {
        val capsule = siteCapsules.firstOrNull { it.id == capsuleId } ?: return false
        val targetTab = tabId?.let { restoredId -> tabs.firstOrNull { it.id == restoredId } }
            ?: return false
        if (targetTab.isIncognito || targetTab.profileId != capsule.profileId) return false
        if (targetTab.url != BLANK_URL &&
            CapsuleNavigationRules.decide(capsule, targetTab.url) !=
            CapsuleNavigationDecision.StayInCapsule
        ) {
            return false
        }
        if (activeProfileId != capsule.profileId && !selectProfile(capsule.profileId)) return false
        if (selectedTabId != targetTab.id) selectTab(targetTab.id)
        activeCapsuleId = capsule.id
        activeCapsuleTabId = targetTab.id
        capsuleTabIds[capsule.id] = targetTab.id
        capsuleShortcuts.reportUsed(capsule)
        if (targetTab.url == BLANK_URL) submitAddress(capsule.startUrl)
        return true
    }

    fun leaveSiteCapsule() {
        activeCapsuleId = null
        activeCapsuleTabId = null
    }

    fun openSiteCapsuleInFullCandy() {
        val capsule = activeSiteCapsule ?: return
        val transition = CapsuleFullCandyTransitionRules.resolve(
            capsule = capsule,
            activeCapsuleTabId = activeCapsuleTabId,
            selectedTabId = selectedTabId,
            selectedProfileId = selectedTab.profileId,
            selectedTabIsPrivate = selectedTab.isIncognito,
        )
        if (transition !is CapsuleFullCandyTransition.KeepCurrentTab) return
        leaveSiteCapsule()
    }

    fun openNormalHomeFromInvalidCapsule() {
        leaveSiteCapsule()
        if (selectedTab.isIncognito) {
            updateTab(selectedTabId) {
                it.copy(
                    title = "",
                    url = BLANK_URL,
                    progress = 0,
                    isLoading = false,
                    canGoBack = false,
                    canGoForward = false,
                    blockedCount = 0,
                    error = null,
                )
            }
            setBlankTabIncognito(false)
            clearExternalNavigationAuthorization(selectedTabId)
            closeGeckoEngineSession(selectedTabId)

        } else if (!selectedTab.isFreshBlankTab) {
            val previousTabId = selectedTabId
            if (createTab(BLANK_URL, isIncognito = false) == previousTabId) {
                submitAddress(BLANK_URL)
            }
        }
    }

    fun openNormalHome(): Boolean {
        leaveSiteCapsule()
        StartupHomeRules.reusableBlankTabId(activeTabs)?.let { tabId ->
            if (selectedTabId != tabId) selectTab(tabId)
            return true
        }
        val previousTabId = selectedTabId
        return createTab(BLANK_URL, isIncognito = false) != previousTabId
    }

    fun upsertSiteCapsule(
        draft: SiteCapsuleDraft,
        sourceFavicon: Bitmap? = null,
        customIcon: Bitmap? = null,
    ): CapsuleSaveResult {
        val existing = draft.id?.let { id -> siteCapsules.firstOrNull { it.id == id } }
        if (existing == null && !SiteCapsuleRules.canCreate(siteCapsules.size)) {
            return CapsuleSaveResult.LimitReached
        }
        if (
            profiles.none { it.id == draft.profileId } ||
            isSyncedProfile(draft.profileId)
        ) return CapsuleSaveResult.Invalid
        val nowMillis = System.currentTimeMillis()
        val proposedCapsule = if (existing == null) {
            SiteCapsuleRules.create(
                draft = draft,
                id = UUID.randomUUID().toString(),
                nowMillis = nowMillis,
                multiProfileSupported = isProfileIsolationSupported,
            )
        } else {
            SiteCapsuleRules.update(
                existing = existing,
                draft = draft,
                nowMillis = nowMillis,
                multiProfileSupported = isProfileIsolationSupported,
            )
        } ?: return CapsuleSaveResult.Invalid
        val storedIcon = siteCapsuleIconStore.load(proposedCapsule.id)
        val storedSourceFavicon = siteCapsuleIconStore.loadSource(proposedCapsule.id)
        val storedCustomIcon = siteCapsuleIconStore.loadCustom(proposedCapsule.id)
        val customIconChanged = customIcon != null && runCatching {
            storedCustomIcon?.sameAs(customIcon) != true
        }.getOrDefault(true)
        if (
            customIconChanged &&
            !siteCapsuleIconStore.saveCustom(proposedCapsule.id, checkNotNull(customIcon))
        ) return CapsuleSaveResult.IconSaveFailed
        val iconCustomizationChanged = existing == null ||
            existing.iconMode != proposedCapsule.iconMode ||
            existing.iconEmoji != proposedCapsule.iconEmoji ||
            existing.iconColor != proposedCapsule.iconColor
        val capsule = proposedCapsule.copy(
            iconMode = CapsuleIconUpdateRules.resolveMode(
                requestedMode = proposedCapsule.iconMode,
                hasSourceFavicon = sourceFavicon != null || storedSourceFavicon != null,
                hasCustomIcon = customIcon != null || storedCustomIcon != null,
                hasRenderedIcon = storedIcon != null,
                customizationChanged = iconCustomizationChanged,
            ),
        )
        val updated = siteCapsules.filterNot { it.id == capsule.id } + capsule
        siteCapsules.clear()
        siteCapsules += SiteCapsuleRules.bounded(updated)
        siteCapsuleStore.save(siteCapsules)
        if (sourceFavicon != null) siteCapsuleIconStore.saveSource(capsule.id, sourceFavicon)
        val icon = when {
            capsule.iconMode == CapsuleIconMode.Custom -> {
                CapsuleIconRenderer.render(
                    name = capsule.name,
                    iconEmoji = capsule.iconEmoji,
                    iconColor = capsule.iconColor,
                    favicon = null,
                    customIcon = checkNotNull(customIcon ?: storedCustomIcon),
                )
            }
            capsule.iconMode == CapsuleIconMode.Favicon &&
                sourceFavicon == null && storedSourceFavicon == null &&
                storedIcon != null -> storedIcon
            else -> CapsuleIconRenderer.render(
                name = capsule.name,
                iconEmoji = capsule.iconEmoji,
                iconColor = capsule.iconColor,
                favicon = if (capsule.iconMode == CapsuleIconMode.Favicon) {
                    sourceFavicon ?: storedSourceFavicon
                } else {
                    null
                },
            )
        }
        siteCapsuleIconStore.save(capsule.id, icon)
        return if (existing == null) {
            if (!capsuleShortcuts.isPinningSupported()) {
                CapsuleSaveResult.PinningUnsupported
            } else if (capsuleShortcuts.requestPin(capsule, icon)) {
                CapsuleSaveResult.PinRequested
            } else {
                CapsuleSaveResult.PinRequestFailed
            }
        } else if (!capsuleShortcuts.isPinned(capsule)) {
            if (!capsuleShortcuts.isPinningSupported()) {
                CapsuleSaveResult.PinningUnsupported
            } else if (capsuleShortcuts.requestPin(capsule, icon)) {
                CapsuleSaveResult.PinRequested
            } else {
                CapsuleSaveResult.PinRequestFailed
            }
        } else {
            if (capsuleShortcuts.update(capsule, icon)) {
                CapsuleSaveResult.Updated
            } else {
                CapsuleSaveResult.UpdateFailed
            }
        }
    }

    fun deleteSiteCapsule(capsuleId: String, deleteDedicatedProfileConfirmed: Boolean): Boolean {
        val capsule = siteCapsules.firstOrNull { it.id == capsuleId } ?: return false
        val remaining = siteCapsules.filterNot { it.id == capsuleId }
        val plan = CapsuleDeletionRules.plan(
            capsule = capsule,
            remainingCapsules = remaining,
            deleteDedicatedProfileConfirmed = deleteDedicatedProfileConfirmed,
        )
        if (plan.deleteDedicatedProfile) {
            if (profiles.size == 1 && profiles.single().id != DEFAULT_PROFILE_ID) {
                profiles += DEFAULT_BROWSER_PROFILE
            }
            if (!deleteProfile(capsule.profileId, capsuleId)) return false
        }
        return finishSiteCapsuleDeletion(capsuleId)
    }

    fun deleteSiteCapsuleAsync(
        capsuleId: String,
        deleteDedicatedProfileConfirmed: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        val capsule = siteCapsules.firstOrNull { it.id == capsuleId }
        if (capsule == null) {
            onComplete(false)
            return
        }
        val plan = CapsuleDeletionRules.plan(
            capsule = capsule,
            remainingCapsules = siteCapsules.filterNot { it.id == capsuleId },
            deleteDedicatedProfileConfirmed = deleteDedicatedProfileConfirmed,
        )
        if (!plan.deleteDedicatedProfile) {
            onComplete(finishSiteCapsuleDeletion(capsuleId))
            return
        }
        if (profiles.size == 1 && profiles.single().id != DEFAULT_PROFILE_ID) {
            profiles += DEFAULT_BROWSER_PROFILE
        }
        deleteProfileAsync(capsule.profileId, capsuleId) { deleted ->
            onComplete(deleted && finishSiteCapsuleDeletion(capsuleId))
        }
    }

    private fun finishSiteCapsuleDeletion(capsuleId: String): Boolean {
        val capsule = siteCapsules.firstOrNull { it.id == capsuleId } ?: return false
        val remaining = siteCapsules.filterNot { it.id == capsuleId }
        if (activeCapsuleId == capsuleId) leaveSiteCapsule()
        capsuleTabIds.remove(capsuleId)
        siteCapsules.clear()
        siteCapsules += remaining
        siteCapsuleStore.save(siteCapsules)
        siteCapsuleIconStore.delete(capsuleId)
        capsuleShortcuts.disable(capsule, activity.getString(R.string.capsule_shortcut_deleted))
        return true
    }

    fun siteCapsuleSourceIcon(capsuleId: String): Bitmap? =
        siteCapsuleIconStore.loadSource(capsuleId)

    fun siteCapsuleCustomIcon(capsuleId: String): Bitmap? =
        siteCapsuleIconStore.loadCustom(capsuleId)

    fun siteCapsuleRenderedIcon(capsuleId: String): Bitmap? = siteCapsuleIconStore.load(capsuleId)

    internal fun refreshToppingCatalog() {
        val generation = ++toppingCatalogRefreshGeneration
        isToppingCatalogLoading = true
        toppingCatalogRepository.refresh { result ->
            if (destroyed || generation != toppingCatalogRefreshGeneration) return@refresh
            isToppingCatalogLoading = false
            toppingCatalogResult = result
        }
    }

    internal fun setToppingEnabled(
        toppingId: String,
        enabled: Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val entry = toppingCatalogEntry(toppingId)
        if (entry == null || toppingId in busyToppingIds) {
            onComplete(false)
            return
        }
        val scriptId = ToppingCatalogRules.stableScriptId(toppingId)
        val installed = userScripts.firstOrNull { script -> script.id == scriptId }
        if (installed != null) {
            busyToppingIds += toppingId
            setUserScriptEnabled(scriptId, enabled) { saved ->
                busyToppingIds.remove(toppingId)
                onComplete(saved)
            }
            return
        }
        if (!enabled) {
            onComplete(true)
            return
        }
        downloadAndSaveTopping(entry = entry, preserveEnabled = false, onComplete = onComplete)
    }

    internal fun updateTopping(
        toppingId: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val entry = toppingCatalogEntry(toppingId)
        val installed = userScripts.any { script ->
            script.id == ToppingCatalogRules.stableScriptId(toppingId)
        }
        if (entry == null || !installed || toppingId in busyToppingIds) {
            onComplete(false)
            return
        }
        downloadAndSaveTopping(entry = entry, preserveEnabled = true, onComplete = onComplete)
    }

    private fun toppingCatalogEntry(id: String): ToppingCatalogEntry? {
        val catalog = when (val result = toppingCatalogResult) {
            is ToppingCatalogRefreshResult.Fresh -> result.catalog
            is ToppingCatalogRefreshResult.Cached -> result.catalog
            is ToppingCatalogRefreshResult.Error,
            null,
            -> return null
        }
        return catalog.toppings.firstOrNull { entry -> entry.id == id }
    }

    private fun downloadAndSaveTopping(
        entry: ToppingCatalogEntry,
        preserveEnabled: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        busyToppingIds += entry.id
        toppingCatalogRepository.download(entry) { result ->
            if (destroyed) return@download
            val downloaded = (result as? ToppingDownloadResult.Accepted)?.script
            if (downloaded == null) {
                busyToppingIds.remove(entry.id)
                onComplete(false)
                return@download
            }
            val existing = userScripts.firstOrNull { script -> script.id == downloaded.id }
            val script = downloaded.copy(
                enabled = if (preserveEnabled) existing?.enabled ?: true else true,
            )
            userScriptRepository.resolveDependencies(script) { resolution ->
                mainHandler.post {
                    if (destroyed) return@post
                    val resolved = (resolution as? UserScriptDependencyResolution.Resolved)?.script
                    if (resolved == null) {
                        busyToppingIds.remove(entry.id)
                        onComplete(false)
                        return@post
                    }
                    val proposed = userScripts.toMutableList()
                    val index = proposed.indexOfFirst { candidate -> candidate.id == resolved.id }
                    if (index >= 0) proposed[index] = resolved else proposed += resolved
                    if (!UserScriptRules.isWithinCollectionBounds(proposed)) {
                        busyToppingIds.remove(entry.id)
                        onComplete(false)
                        return@post
                    }
                    commitUserScripts(proposed) { persisted ->
                        busyToppingIds.remove(entry.id)
                        onComplete(persisted)
                    }
                }
            }
        }
    }

    internal fun saveUserScript(
        id: String?,
        source: String,
        onComplete: (UserScriptSaveOutcome) -> Unit,
    ) {
        val existing = id?.let { candidate -> userScripts.firstOrNull { it.id == candidate } }
        if (id != null && existing == null) {
            onComplete(UserScriptSaveOutcome.Missing)
            return
        }
        if (existing == null && userScripts.size >= UserScriptParser.MAX_SCRIPTS) {
            onComplete(UserScriptSaveOutcome.LimitReached)
            return
        }
        val result = UserScriptParser.parse(
            id = existing?.id ?: UUID.randomUUID().toString(),
            source = source,
            enabled = existing?.enabled ?: true,
            updatedAtMillis = System.currentTimeMillis(),
        )
        val script = when (result) {
            is UserScriptParseResult.Accepted -> result.script
            is UserScriptParseResult.Rejected -> {
                onComplete(UserScriptSaveOutcome.Rejected(result.reason))
                return
            }
        }
        userScriptRepository.resolveDependencies(script) { resolution ->
            mainHandler.post {
                if (destroyed) return@post
                val resolved = when (resolution) {
                    is UserScriptDependencyResolution.Resolved -> resolution.script
                    is UserScriptDependencyResolution.Failed -> {
                        onComplete(UserScriptSaveOutcome.DependencyFailed(resolution.reason))
                        return@post
                    }
                }
                val proposed = userScripts.toMutableList()
                val index = proposed.indexOfFirst { it.id == resolved.id }
                if (index >= 0) proposed[index] = resolved else proposed += resolved
                if (!UserScriptRules.isWithinCollectionBounds(proposed)) {
                    onComplete(UserScriptSaveOutcome.LimitReached)
                    return@post
                }
                commitUserScripts(proposed) { persisted ->
                    onComplete(
                        if (persisted) {
                            UserScriptSaveOutcome.Saved
                        } else {
                            UserScriptSaveOutcome.PersistenceFailed
                        },
                    )
                }
            }
        }
    }

    internal fun setUserScriptEnabled(
        id: String,
        enabled: Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val index = userScripts.indexOfFirst { it.id == id }
        if (index < 0 || userScripts[index].enabled == enabled) {
            onComplete(index >= 0)
            return
        }
        val proposed = userScripts.toMutableList()
        proposed[index] = proposed[index].copy(
            enabled = enabled,
            updatedAtMillis = System.currentTimeMillis(),
        )
        if (!UserScriptRules.isWithinCollectionBounds(proposed)) {
            onComplete(false)
            return
        }
        commitUserScripts(proposed = proposed, onComplete = onComplete)
    }

    internal fun deleteUserScript(id: String, onComplete: (Boolean) -> Unit = {}) {
        if (userScripts.none { it.id == id }) {
            onComplete(false)
            return
        }
        commitUserScripts(
            proposed = userScripts.filterNot { it.id == id },
            onComplete = onComplete,
            onPersisted = { geckoEngineSessionFactory.clearToppingValues(id) },
        )
    }

    private fun commitUserScripts(
        proposed: List<UserScript>,
        onPersisted: () -> Unit = {},
        onComplete: (Boolean) -> Unit,
    ) {
        if (userScriptMutationPending) {
            onComplete(false)
            return
        }
        userScriptMutationPending = true
        val snapshot = proposed.toList()
        userScriptRepository.save(snapshot) { persisted ->
            if (persisted) onPersisted()
            mainHandler.post {
                if (destroyed) return@post
                userScriptMutationPending = false
                if (persisted) {
                    userScripts.clear()
                    userScripts += snapshot
                    geckoEngineSessionFactory.reconcileToppings(snapshot)
                }
                onComplete(persisted)
            }
        }
    }

    private fun reassignSiteCapsules(
        sourceProfileId: String,
        fallbackProfile: BrowserProfile,
        excludedCapsuleId: String? = null,
    ) {
        val affected = siteCapsules.filter {
            it.profileId == sourceProfileId && it.id != excludedCapsuleId
        }
        if (affected.isEmpty()) return
        val nowMillis = System.currentTimeMillis()
        val replacements = affected.associate { capsule ->
            capsule.id to capsule.copy(
                profileId = fallbackProfile.id,
                ownsDedicatedProfile = false,
                isolatedStorageRequested = false,
                updatedAtMillis = nowMillis,
            )
        }
        siteCapsules.replaceAll { capsule -> replacements[capsule.id] ?: capsule }
        siteCapsuleStore.save(siteCapsules)
        replacements.values.forEach { capsule ->
            val icon = if (capsule.iconMode == CapsuleIconMode.ProfileFallback) {
                CapsuleIconRenderer.render(
                    name = capsule.name,
                    iconEmoji = capsule.iconEmoji,
                    iconColor = capsule.iconColor,
                    favicon = null,
                )
            } else {
                siteCapsuleIconStore.load(capsule.id) ?: CapsuleIconRenderer.render(
                    name = capsule.name,
                    iconEmoji = capsule.iconEmoji,
                    iconColor = capsule.iconColor,
                    favicon = null,
                )
            }
            siteCapsuleIconStore.save(capsule.id, icon)
            capsuleShortcuts.update(capsule, icon)
        }
    }

    fun hasTabCapacity(nowMillis: Long = System.currentTimeMillis()): Boolean =
        tabs.size - staleTabIds(nowMillis).size < MAX_TABS

    fun prepareTabCreation(
        targetProfileId: String = activeProfileId,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        pruneStaleTabs(nowMillis)
        val requiredTabCount = if (
            targetProfileId != activeProfileId && tabs.none { it.profileId == targetProfileId }
        ) {
            2
        } else {
            1
        }
        return tabs.size <= MAX_TABS - requiredTabCount
    }

    fun createTab(
        initialUrl: String = BLANK_URL,
        isIncognito: Boolean = selectedTab.isIncognito,
        openerTabId: String? = null,
        authorizeInitialExternalNavigation: Boolean = false,
    ): String {
        val nowMillis = System.currentTimeMillis()
        if (!prepareTabCreation(nowMillis = nowMillis)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            return selectedTabId
        }
        if (activeCapsuleTabId != null) leaveSiteCapsule()
        clearPermissionActivity(selectedTabId)
        touchTab(selectedTabId, nowMillis)
        val resolvedUrl = if (initialUrl == BLANK_URL) {
            BLANK_URL
        } else {
            AddressResolver.resolve(
                input = initialUrl,
                searchEngine = searchEngine,
                searxngInstanceUrl = searxngSettings.instanceUrl,
            )
        }
        val tab = newTabState(
            url = resolvedUrl,
            nowMillis = nowMillis,
            isIncognito = isIncognito && !isSyncedProfile(activeProfileId),
            openerTabId = openerTabId,
        )
        if (authorizeInitialExternalNavigation) {
            ExternalNavigationGrantRules.start(
                url = resolvedUrl,
                nowElapsedRealtime = SystemClock.elapsedRealtime(),
            )?.let { grant ->
                pendingInitialExternalNavigationGrants[tab.id] = grant
            }
        }
        tabs += tab
        markSyncedTabPending(tab)
        updateSelectedTabId(tab.id)
        rememberSelectedTab(activeProfileId, tab.id)
        enqueueSyncedTab(tab.id)
        persist()
        return tab.id
    }

    fun duplicateSelectedTab(): String? {
        val sourceTab = selectedTab
        if (sourceTab.url == BLANK_URL) return null
        val duplicateTabId = createTab(
            initialUrl = sourceTab.url,
            isIncognito = sourceTab.isIncognito,
        )
        return duplicateTabId.takeUnless { it == sourceTab.id }
    }

    fun createBackgroundTab(
        initialUrl: String,
        openerTabId: String? = null,
        isIncognito: Boolean = selectedTab.isIncognito,
        transientPopup: Boolean = false,
    ): String? {
        pruneStaleTabs()
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            return null
        }
        val openerTab = openerTabId?.let { id -> tabs.firstOrNull { tab -> tab.id == id } }
        val resolvedUrl = if (initialUrl == BLANK_URL) {
            BLANK_URL
        } else {
            AddressResolver.resolve(
                input = initialUrl,
                searchEngine = searchEngine,
                searxngInstanceUrl = searxngSettings.instanceUrl,
            )
        }
        val tab = newTabState(
            url = resolvedUrl,
            nowMillis = System.currentTimeMillis(),
            isIncognito = openerTab?.isIncognito ?: isIncognito,
            openerTabId = openerTabId,
            profileId = openerTab?.profileId ?: activeProfileId,
        )
        tabs += tab
        if (!transientPopup) {
            markSyncedTabPending(tab)
            enqueueSyncedTab(tab.id)
        }
        if (transientPopup) transientPopupTabIds += tab.id
        persist()
        if (!transientPopup) {
            contentActions.requestAddressBarPulse()
            contentActions.dismiss()
        }
        return tab.id
    }

    fun openBlockedPopup(token: Long) {
        val offer = blockedPopupOffer?.takeIf { it.token == token } ?: return
        val tab = tabs.firstOrNull { it.id == offer.popupTabId } ?: run {
            blockedPopupOffer = null
            return
        }
        val view = geckoEngineSessions[tab.id] ?: run {
            blockedPopupOffer = null
            closeTab(tab.id)
            return
        }
        blockedPopupOffer = null
        transientPopupTabIds.remove(tab.id)
        if (!FederatedLoginRules.isProviderNavigation(offer.targetUrl)) {
            federatedLoginCompatibilityTabIds.remove(tab.id)
        }
        if (tab.profileId != activeProfileId && profilesEnabled) selectProfile(tab.profileId)
        updateTab(tab.id) { current ->
            current.copy(url = offer.targetUrl, isLoading = true, progress = 0, error = null)
        }
        tabs.firstOrNull { it.id == tab.id }?.let(::markSyncedTabPending)
        scheduleSyncedTabNavigation(tab.id)
        loadGeckoWithPrivacy(tab.id, view, offer.targetUrl)
        selectTab(tab.id)
    }

    fun dismissBlockedPopup(token: Long) {
        val offer = blockedPopupOffer?.takeIf { it.token == token } ?: return
        blockedPopupOffer = null
        closeTab(offer.popupTabId)
    }

    fun showFederatedLoginOptions(token: Long) {
        val offer = federatedLoginOffer?.takeIf { it.token == token } ?: return
        if (!isCurrentFederatedLoginOffer(offer)) {
            federatedLoginOffer = null
            return
        }
        federatedLoginOffer = offer.copy(showDialog = true)
    }

    fun respondToFederatedLoginOffer(
        token: Long,
        choice: FederatedLoginPromptChoice,
    ) {
        val offer = federatedLoginOffer?.takeIf { it.token == token } ?: return
        federatedLoginOffer = null
        if (choice == FederatedLoginPromptChoice.Deny || !isCurrentFederatedLoginOffer(offer)) {
            return
        }
        if (choice == FederatedLoginPromptChoice.AllowForProfile && offer.isPrivate) return
        updateSitePrivacyOverrides(
            tabId = offer.tabId,
            persistently = choice == FederatedLoginPromptChoice.AllowForProfile,
        ) { current, _ ->
            current.copy(thirdPartyLoginAllowed = true)
        }
    }

    fun revokeFederatedLoginCompatibility(tabId: String): Boolean =
        updateSitePrivacyOverrides(tabId) { current, _ ->
            current.copy(thirdPartyLoginAllowed = null)
        }

    fun showCaptchaCompatibilityOptions(token: Long) {
        val offer = captchaCompatibilityOffer?.takeIf { it.token == token } ?: return
        if (!isCurrentCaptchaCompatibilityOffer(offer)) {
            captchaCompatibilityOffer = null
            return
        }
        captchaCompatibilityOffer = offer.copy(showDialog = true)
    }

    fun respondToCaptchaCompatibilityOffer(
        token: Long,
        choice: CaptchaCompatibilityPromptChoice,
    ) {
        val offer = captchaCompatibilityOffer?.takeIf { it.token == token } ?: return
        captchaCompatibilityOffer = null
        if (choice == CaptchaCompatibilityPromptChoice.Deny ||
            !isCurrentCaptchaCompatibilityOffer(offer)
        ) return
        if (choice == CaptchaCompatibilityPromptChoice.AllowForProfile && offer.isPrivate) return
        updateSitePrivacyOverrides(
            tabId = offer.tabId,
            persistently = choice == CaptchaCompatibilityPromptChoice.AllowForProfile,
        ) { current, _ ->
            current.copy(captchaCompatibilityAllowed = true)
        }
    }

    fun revokeThirdPartyCookieCompatibility(tabId: String): Boolean =
        updateSitePrivacyOverrides(tabId) { current, _ ->
            current.copy(
                thirdPartyLoginAllowed = null,
                captchaCompatibilityAllowed = null,
            )
        }

    fun createProfile(emoji: String, isolationEnabled: Boolean = false): String? {
        if (!profilesEnabled) return null
        if (localProfiles.size >= MAX_PROFILES) {
            Toast.makeText(
                activity,
                activity.resources.getQuantityString(
                    R.plurals.toast_profile_limit_reached,
                    MAX_PROFILES,
                    MAX_PROFILES,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            return null
        }
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            return null
        }
        val profile = BrowserProfileRules.create(
            draft = BrowserProfileDraft(
                emoji = emoji,
                isolationRequested = isolationEnabled,
            ),
            profileId = UUID.randomUUID().toString(),
            isolationSupported = isProfileIsolationSupported,
        ) ?: return null
        val previousTabId = selectedTabId
        clearPermissionActivity(previousTabId)
        touchTab(previousTabId, System.currentTimeMillis())
        profiles += profile
        activeProfileId = profile.id
        refreshActiveProfileWallpaper()
        val tab = newTabState()
        tabs += tab
        updateSelectedTabId(tab.id)
        rememberSelectedTab(profile.id, tab.id)
        persist()
        return profile.id
    }

    fun selectProfile(profileId: String): Boolean {
        if (!profilesEnabled) return false
        if (profileId == activeProfileId || profiles.none { it.id == profileId }) return false
        val previousTabId = selectedTabId
        clearPermissionActivity(previousTabId)
        touchTab(previousTabId, System.currentTimeMillis())
        rememberSelectedTab(activeProfileId, previousTabId)
        activeProfileId = profileId
        refreshActiveProfileWallpaper()
        releaseActiveProfileTabSwitcherWallpaper()
        val profile = profiles.first { it.id == profileId }
        val targetTab = profile.selectedTabId
            ?.let { tabId -> tabs.firstOrNull { it.id == tabId && it.profileId == profileId } }
            ?: activeTabs.maxByOrNull(BrowserTab::lastAccessedAt)
            ?: newTabState().also(tabs::add)
        updateSelectedTabId(targetTab.id)
        touchTab(targetTab.id, System.currentTimeMillis())
        rememberSelectedTab(profileId, targetTab.id)
        persist()
        return true
    }

    fun updateProfileEmoji(profileId: String, emoji: String): Boolean {
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index < 0) return false
        val updatedProfile = BrowserProfileRules.updateEmoji(
            profile = profiles[index],
            emoji = emoji,
        ) ?: return false
        profiles[index] = updatedProfile
        persist()
        return true
    }

    fun updateProfileWallpaper(
        profileId: String,
        wallpaperTarget: ProfileWallpaperTarget,
        wallpaper: ProfileWallpaper?,
    ): Boolean {
        if (isSyncedProfile(profileId)) return false
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index < 0) return false
        profiles[index] = profiles[index].withWallpaper(
            target = wallpaperTarget,
            wallpaper = wallpaper?.let(ProfileWallpaperRules::sanitize),
        )
        persist()
        return true
    }

    fun releaseActiveProfileWallpaperForEditing(profileId: String) {
        if (profileId != activeProfileId) return
        profileWallpaperLoadGeneration += 1
        profileTabSwitcherWallpaperLoadGeneration += 1
        activeProfileWallpaperBitmap = null
        activeProfileTabSwitcherWallpaperBitmap = null
    }

    fun restoreActiveProfileWallpapersAfterEditing() {
        refreshActiveProfileWallpaper()
        loadActiveProfileTabSwitcherWallpaper()
    }

    private fun refreshActiveProfileWallpaper() {
        val profile = localProfiles.firstOrNull { it.id == activeProfileId }
        val wallpaper = profile?.newTabWallpaper
        val profileId = profile?.id
        val generation = ++profileWallpaperLoadGeneration
        activeProfileWallpaperBitmap = null
        if (profileId == null || wallpaper == null) return
        profileWallpaperExecutor.execute {
            val loaded = profileWallpaperStore.load(profileId, ProfileWallpaperTarget.NewTab)
            mainHandler.post {
                if (
                    destroyed ||
                    generation != profileWallpaperLoadGeneration ||
                    activeProfileId != profileId
                ) {
                    loaded?.recycle()
                    return@post
                }
                if (loaded != null) {
                    activeProfileWallpaperBitmap = loaded
                    return@post
                }
                val index = profiles.indexOfFirst { candidate -> candidate.id == profileId }
                if (index >= 0 && profiles[index].newTabWallpaper == wallpaper) {
                    profiles[index] = profiles[index].copy(newTabWallpaper = null)
                    persist()
                }
            }
        }
    }

    fun loadActiveProfileTabSwitcherWallpaper(onReady: () -> Unit = {}) {
        val profile = localProfiles.firstOrNull { it.id == activeProfileId }
        val wallpaper = profile?.tabSwitcherWallpaper
        val profileId = profile?.id
        val generation = ++profileTabSwitcherWallpaperLoadGeneration
        activeProfileTabSwitcherWallpaperBitmap = null
        if (profileId == null || wallpaper == null) {
            onReady()
            return
        }
        profileWallpaperExecutor.execute {
            val loaded = profileWallpaperStore.load(
                profileId,
                ProfileWallpaperTarget.TabSwitcher,
            )
            mainHandler.post {
                if (
                    destroyed ||
                    generation != profileTabSwitcherWallpaperLoadGeneration ||
                    activeProfileId != profileId
                ) {
                    loaded?.recycle()
                    return@post
                }
                if (loaded != null) {
                    activeProfileTabSwitcherWallpaperBitmap = loaded
                    onReady()
                    return@post
                }
                val index = profiles.indexOfFirst { candidate -> candidate.id == profileId }
                if (index >= 0 && profiles[index].tabSwitcherWallpaper == wallpaper) {
                    profiles[index] = profiles[index].copy(tabSwitcherWallpaper = null)
                    persist()
                }
                onReady()
            }
        }
    }

    fun releaseActiveProfileTabSwitcherWallpaper() {
        profileTabSwitcherWallpaperLoadGeneration += 1
        activeProfileTabSwitcherWallpaperBitmap = null
    }

    fun setProfileIsolation(profileId: String, enabled: Boolean): Boolean {
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index < 0) return false
        val updatedProfile = BrowserProfileRules.updateIsolation(
            profile = profiles[index],
            enabled = enabled,
            isolationSupported = isProfileIsolationSupported,
        ) ?: return false
        val affectedTabIds = GeckoProfileStorageRules.affectedTabIds(tabs, profileId)
        profiles[index] = updatedProfile
        recreateEngineSessions(affectedTabIds)
        externalLinkPreviewState
            ?.takeIf { it.targetProfileId == profileId }
            ?.let(::recreateExternalLinkPreviewRuntime)
        persist()
        return true
    }

    fun deleteProfile(profileId: String, excludedCapsuleId: String? = null): Boolean =
        deleteProfileInternal(
            profileId = profileId,
            excludedCapsuleId = excludedCapsuleId,
            recallAlreadyDeleted = false,
        )

    fun deleteProfileAsync(
        profileId: String,
        excludedCapsuleId: String? = null,
        onComplete: (Boolean) -> Unit,
    ) {
        if (
            localProfiles.size <= 1 ||
            isSyncedProfile(profileId) ||
            isBoundSyncProfile(profileId) ||
            profiles.none { it.id == profileId }
        ) {
            onComplete(false)
            return
        }
        if (!pendingRecallProfileDeletions.add(profileId)) {
            onComplete(false)
            return
        }
        recallRepository.deleteProfilesAsync(setOf(profileId)) { deleted ->
            val result = deleted && !destroyed && deleteProfileInternal(
                profileId = profileId,
                excludedCapsuleId = excludedCapsuleId,
                recallAlreadyDeleted = true,
            )
            pendingRecallProfileDeletions.remove(profileId)
            onComplete(result)
        }
    }

    private fun deleteProfileInternal(
        profileId: String,
        excludedCapsuleId: String?,
        recallAlreadyDeleted: Boolean,
    ): Boolean {
        if (localProfiles.size <= 1 || isSyncedProfile(profileId) || isBoundSyncProfile(profileId)) {
            return false
        }
        val profileIndex = profiles.indexOfFirst { it.id == profileId }
        if (profileIndex < 0) return false
        val remainingLocalProfiles = localProfiles.filterNot { it.id == profileId }
        val fallbackProfile = if (profileId == activeProfileId) {
            remainingLocalProfiles.first()
        } else {
            localProfiles.first { it.id == activeProfileId }
        }
        val removedProfileTrailTabIds = (
            tabs.asSequence() + snoozedTabs.asSequence().map { snoozed -> snoozed.tab }
        )
            .filter { tab -> tab.profileId == profileId && !tab.isIncognito }
            .mapTo(linkedSetOf(), BrowserTab::id)
        val previewToRecreate = externalLinkPreviewState
            ?.takeIf { it.targetProfileId == profileId }
            ?.copy(targetProfileId = fallbackProfile.id)
        if (previewToRecreate != null) {
            releaseExternalLinkPreviewRuntime(resumeSelectedTab = false)
        }
        val movedTabIds = GeckoProfileStorageRules.affectedTabIds(tabs, profileId)
        // Native context deletion requires every session in that context to be closed first.
        dismissFirefoxExtensionPopup()
        contentActions.dismiss()
        destroyLinkPeekPreviewSessions()
        movedTabIds.forEach(::closeGeckoEngineSession)
        val removedProfile = profiles[profileIndex]
        if (GeckoProfileStorageRules.requiresContextDeletion(removedProfile.isolationEnabled) &&
            !geckoEngineSessionFactory.requestProfileDataDeletion(profileId)
        ) return false
        val historyMutation = historyRepository.clearProfiles(
            profileIds = setOf(profileId),
            trailTabIds = removedProfileTrailTabIds,
            recallAlreadyDeleted = recallAlreadyDeleted,
        )
        if (!historyMutation.committed) return false
        if (historyMutation.history.size != history.size) {
            history.clear()
            history += historyMutation.history
        }
        reassignSiteCapsules(profileId, fallbackProfile, excludedCapsuleId)
        removedProfileTrailTabIds.forEach(geckoSessionStateStore::delete)
        movedTabIds.forEach(extensionTabMuteOverrides::remove)
        movedTabIds.forEach(::clearPrivacyDataForTab)
        val profileRuleIds = filterRules.filter { it.profileId == profileId }.map(CandyRule::id).toSet()
        if (profileRuleIds.isNotEmpty()) {
            filterRules.removeAll { it.id in profileRuleIds }
            ephemeralRuleIds.removeAll(profileRuleIds)
            onFilterRulesChanged(persist = true)
        }
        if (permanentSiteExceptions.containsKey(profileId)) {
            permanentSiteExceptions = permanentSiteExceptions - profileId
            store.savePermanentSiteExceptions(permanentSiteExceptions)
            siteExceptionRevision++
        }
        if (permanentSitePrivacyOverrides.containsKey(profileId)) {
            permanentSitePrivacyOverrides = permanentSitePrivacyOverrides - profileId
            store.saveSitePrivacyOverrides(permanentSitePrivacyOverrides)
            siteExceptionRevision++
        }
        if (permanentMutedDomains.remove(profileId) != null) {
            store.saveMutedDomains(permanentMutedDomains.toMap())
        }
        temporaryMutedDomains.remove(profileId)
        if (permanentDesktopViewDomains.remove(profileId) != null) {
            store.saveDesktopViewDomains(permanentDesktopViewDomains.toMap())
        }
        temporaryDesktopViewDomains.remove(profileId)
        if (permanentAlwaysBlockPopupDomains.remove(profileId) != null) {
            store.saveAlwaysBlockPopupDomains(permanentAlwaysBlockPopupDomains.toMap())
        }
        temporaryAlwaysBlockPopupDomains.remove(profileId)
        permissionRepository.removeProfile(profileId)
        permissionRevision++
        val movedTabs = GeckoProfileStorageRules.movedTabs(
            tabs = tabs,
            sourceProfileId = profileId,
            targetProfileId = fallbackProfile.id,
        )
        tabs.clear()
        tabs += movedTabs
        val reassignedSnoozed = snoozedTabs.map { snoozed ->
            if (snoozed.tab.profileId == profileId) {
                snoozed.copy(tab = snoozed.tab.copy(profileId = fallbackProfile.id))
            } else {
                snoozed
            }
        }
        if (snoozedTabStore.save(reassignedSnoozed)) {
            snoozedTabs.clear()
            snoozedTabs += reassignedSnoozed
        }
        movedTabIds.forEach { tabId ->
            updateProtectionRequestContext(tabId, pageUrls[tabId])

        }
        profiles.removeAt(profileIndex)
        profileWallpaperExecutor.execute { profileWallpaperStore.delete(profileId) }
        val profileTrailRedactions = store.loadPendingCandyTrailRedactions().filter { redaction ->
            redaction.tabIds.any(removedProfileTrailTabIds::contains)
        }
        applyCandyTrailRedactions(profileTrailRedactions)
        candyTrailRepository.processPendingRedactions()
        if (profileId == activeProfileId) {
            activeProfileId = fallbackProfile.id
            refreshActiveProfileWallpaper()
            loadActiveProfileTabSwitcherWallpaper()
        }
        val fallbackTabs = tabs.filter { it.profileId == fallbackProfile.id }
        replaceProfileTabs(fallbackProfile.id, TabPinningRules.orderedTabs(fallbackTabs))
        val fallbackSelection = selectedTabId.takeIf { selectedId ->
            tabs.any { it.id == selectedId && it.profileId == fallbackProfile.id }
        } ?: fallbackProfile.selectedTabId?.takeIf { selectedId ->
            tabs.any { it.id == selectedId && it.profileId == fallbackProfile.id }
        } ?: activeTabs.first().id
        if (activeProfileId == fallbackProfile.id) {
            updateSelectedTabId(fallbackSelection)
            rememberSelectedTab(fallbackProfile.id, fallbackSelection)
        }
        reconcileCandyTrailForks(System.currentTimeMillis())
        persist()
        previewToRecreate?.let(::createExternalLinkPreviewRuntime)
        return true
    }

    fun moveTabToProfile(tabId: String, profileId: String): Boolean {
        if (!profilesEnabled) return false
        val sourceTab = tabs.firstOrNull { it.id == tabId } ?: return false
        if (isSessionEphemeralTab(tabId)) return false
        if (sourceTab.profileId == profileId || profiles.none { it.id == profileId }) return false
        val targetIsSynced = isSyncedProfile(profileId)
        val targetSyncs = isSyncTargetProfile(profileId)
        if (sourceTab.isIncognito && targetIsSynced) return false
        if (sourceTab.profileId == activeProfileId && activeTabs.size == 1 && tabs.size >= MAX_TABS) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        val sourceIndex = activeTabs.indexOfFirst { it.id == tabId }
        if (isSyncTargetProfile(sourceTab.profileId)) enqueueSyncedTabClose(sourceTab)
        val movedTab = sourceTab.copy(
            profileId = profileId,
            isIncognito = sourceTab.isIncognito && !targetIsSynced,
            blockedCount = 0,
            syncCandyId = if (targetSyncs && !sourceTab.isIncognito) {
                sourceTab.syncCandyId ?: UUID.randomUUID().toString()
            } else {
                null
            },
        )
        if (tabId == selectedTabId) {
            clearPermissionActivity(tabId)
        }
        clearPrivacyDataForTab(tabId)
        if (GeckoProfileStorageRules.contextChanged(sourceTab, movedTab)) {
            closeGeckoEngineSession(tabId)
            geckoSessionStateStore.delete(tabId)
        }
        updateTab(tabId) { movedTab }
        updateProtectionRequestContext(tabId, pageUrls[tabId])

        replaceProfileTabs(
            profileId,
            TabPinningRules.orderedTabs(tabs.filter { it.profileId == profileId }),
        )
        if (targetSyncs && !movedTab.isIncognito) {
            markSyncedTabPending(movedTab)
            enqueueSyncedTab(movedTab.id)
        }
        if (tabId == selectedTabId) {
            updateSelectedTabId(
                activeTabs.getOrNull(sourceIndex.coerceAtMost(activeTabs.lastIndex))?.id
                    ?: newTabState(isIncognito = sourceTab.isIncognito).also(tabs::add).id,
            )
            touchTab(selectedTabId, System.currentTimeMillis())
            rememberSelectedTab(activeProfileId, selectedTabId)
        }
        reconcileCandyTrailForks(System.currentTimeMillis())
        persist()
        return true
    }

    fun downloadContextImage() {
        val tabId = currentContentActionTabId() ?: return
        val imageUrl = contentActions.target?.imageUrl ?: return
        requestContextDownload(tabId, imageUrl)
    }

    fun downloadContextLink() {
        val tabId = currentContentActionTabId() ?: return
        val linkUrl = contentActions.target?.linkUrl ?: return
        requestContextDownload(tabId, linkUrl)
    }

    private fun requestContextDownload(tabId: String, url: String) {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return
        val session = geckoEngineSessionFor(tabId)
        var reported = false
        fun report(result: DownloadActionResult) {
            if (reported) return
            reported = true
            contentActions.reportDownload(result)
            showDownloadResult(result)
        }
        fun reportFailure() = report(
            DownloadActionResult.Failed(activity.getString(R.string.error_download_start_failed)),
        )
        contentActions.dismiss()
        val cancellation = session.startContextDownload(
            request = GeckoContextDownloadRequest(safeUrl, referrer = referrerFor(tabId)),
            listener = object : GeckoDownloadTransferListener {
                override fun onStarted(start: GeckoDownloadTransferStart) {
                    report(DownloadActionResult.Enqueued(start.id.toLong(), start.fileName))
                }

                override fun onFailed(reason: GeckoDownloadFailure) = reportFailure()
            },
        )
        if (cancellation == null) reportFailure()
    }

    private fun contextActionSourceTab(): BrowserTab? {
        val tabId = contentActions.sourceTabId ?: selectedTabId
        if (tabId != selectedTabId) return null
        return tabs.firstOrNull { tab -> tab.id == tabId }
    }

    private fun contextLinkSourceTab(): BrowserTab? = contextActionSourceTab()
        ?.takeIf { contentActions.target?.linkUrl != null }

    val canPersistContextLink: Boolean
        get() = contextLinkSourceTab()?.isIncognito == false

    val contextLinkSourceTabId: String?
        get() = contextLinkSourceTab()?.id

    val canSnoozeContextLink: Boolean
        get() = contextLinkSourceTab()?.let { tab ->
            !tab.isIncognito &&
                !isSyncedProfile(tab.profileId) &&
                !isSessionEphemeralTab(tab.id)
        } == true

    private fun currentContentActionTabId(): String? {
        contextActionSourceTab()?.let { tab -> return tab.id }
        contentActions.dismiss()
        return null
    }

    fun confirmDownloadChoice(managerId: String?) {
        val choice = pendingDownloadChoice ?: return
        pendingDownloadChoice = null
        val result = if (managerId == null) {
            downloadManager.enqueue(choice.request)
        } else {
            val app = choice.apps.firstOrNull { it.id == managerId }
            if (app == null) {
                downloadManager.enqueue(choice.request)
            } else {
                launchExternallyOrFallback(choice.request, app, choice.isIncognito)
            }
        }
        showDownloadResult(result)
        showNextDownloadChoice()
    }

    fun dismissDownloadChoice() {
        pendingDownloadChoice = null
        showNextDownloadChoice()
    }

    fun openContextLinkInBackground() {
        val url = contentActions.target?.openLinkInBackgroundAction()?.url ?: return
        openContextLinkInBackground(url)
    }

    fun openContextLinkInBackground(url: String): Boolean {
        val sourceTab = contextLinkSourceTab() ?: return false
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        contentActions.dismiss()
        if (createBackgroundTab(safeUrl, openerTabId = sourceTab.id) != null) {
            contentActions.requestLinkPeekNewTabPulse()
            return true
        }
        return false
    }

    fun openContextLinkInForeground(url: String): Boolean {
        val sourceTab = contextLinkSourceTab() ?: return false
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        contentActions.dismiss()
        val tabId = createTab(
            initialUrl = safeUrl,
            isIncognito = sourceTab.isIncognito,
            openerTabId = sourceTab.id,
        )
        return tabId != sourceTab.id && selectedTabId == tabId
    }

    private fun handleWebContentLongPress(target: WebContentTarget, tabId: String) {
        when (
            LinkLongPressRules.outcome(
                action = linkLongPressAction,
                target = target,
                canOpenInPrivate = canOpenLinkInPrivate,
            )
        ) {
            LinkLongPressOutcome.ShowContext -> contentActions.show(target, tabId)
            LinkLongPressOutcome.CopyLink -> {
                contentActions.dismiss()
                copyLink(requireNotNull(target.linkUrl))
            }
            LinkLongPressOutcome.OpenInNewTab -> {
                contentActions.dismiss()
                createBackgroundTab(requireNotNull(target.linkUrl), openerTabId = tabId)
            }
            LinkLongPressOutcome.OpenInPrivateTab -> {
                if (!openLinkInPrivate(requireNotNull(target.linkUrl))) {
                    contentActions.show(target, tabId)
                }
            }
            LinkLongPressOutcome.Share -> {
                contentActions.dismiss()
                shareLink(requireNotNull(target.linkUrl))
            }
        }
    }

    fun openLinkInPrivate(url: String): Boolean {
        if (!canOpenLinkInPrivate) return false
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        val openerTabId = selectedTabId
        contentActions.dismiss()
        val tabId = createTab(
            initialUrl = safeUrl,
            isIncognito = true,
            openerTabId = openerTabId,
        )
        return tabId != openerTabId && selectedTab.isIncognito
    }

    fun openDefaultBrowserSettings() {
        if (!DefaultBrowserRole.openSettings(activity)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_default_browser_selection_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openSelectedPageExternally() = openPageExternally(selectedTabId)

    fun translateSelectedPage() {
        val translationUrl = PageTranslationRules.buildTranslationUrl(
            provider = pageTranslationProvider,
            sourceUrl = selectedTab.url,
            targetLanguage = PageTranslationRules.targetLanguage(
                activity.resources.configuration.locales[0].language,
            ),
        ) ?: return
        submitAddress(translationUrl)
    }

    private fun showExternalAppOpenedToast() {
        Toast.makeText(
            activity,
            activity.getString(R.string.toast_opening_external_app),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun updateExternalNavigationGrant(
        tabId: String,
        url: String,
        isForMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
        nowElapsedRealtime: Long,
    ) {
        val updatedGrant = if (isForMainFrame && hasGesture) {
            ExternalNavigationGrantRules.start(url, nowElapsedRealtime)
        } else {
            externalNavigationGrants[tabId]?.let { grant ->
                ExternalNavigationGrantRules.followRedirect(
                    grant = grant,
                    url = url,
                    isForMainFrame = isForMainFrame,
                    isRedirect = isRedirect,
                    nowElapsedRealtime = nowElapsedRealtime,
                )
            }
        }
        if (updatedGrant == null) externalNavigationGrants.remove(tabId)
        else externalNavigationGrants[tabId] = updatedGrant
    }

    private fun clearExternalNavigationGrantForCallback(
        tabId: String,
        callbackUrl: String?,
        currentBrowserUrl: String?,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        val grant = externalNavigationGrants[tabId] ?: return false
        if (
            ExternalNavigationGrantRules.shouldClearForMainFrameCallback(
                grant = grant,
                callbackUrl = callbackUrl,
                currentBrowserUrl = currentBrowserUrl,
                nowElapsedRealtime = nowElapsedRealtime,
            )
        ) {
            externalNavigationGrants.remove(tabId)
            return true
        }
        return false
    }

    private fun clearExternalNavigationAuthorization(tabId: String) {
        externalNavigationGrants.remove(tabId)
        pendingInitialExternalNavigationGrants.remove(tabId)
    }

    private fun activatePendingInitialExternalNavigationGrant(
        tabId: String,
        pageUrl: String,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime(),
    ) {
        val pendingGrant = pendingInitialExternalNavigationGrants.remove(tabId) ?: return
        if (
            pendingGrant.currentUrl == BrowserUriPolicy.normalizeHttpUrl(pageUrl) &&
            ExternalNavigationGrantRules.isActive(pendingGrant, nowElapsedRealtime)
        ) {
            externalNavigationGrants[tabId] = pendingGrant
        }
    }

    fun openPageExternally(tabId: String) {
        val url = tabs.firstOrNull { it.id == tabId }?.url ?: return
        if (url == BLANK_URL) return
        when (externalApps.openWebUrlExternally(url)) {
            ExternalLaunchResult.Launched -> Unit
            is ExternalLaunchResult.OpenInBrowser,
            ExternalLaunchResult.Unsupported,
            -> Toast.makeText(
                activity,
                activity.getString(R.string.toast_no_external_app),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openIncomingAppLink(url: String): Boolean {
        if (externalApps.openWebUrlExternally(url) != ExternalLaunchResult.Launched) return false
        showExternalAppOpenedToast()
        return true
    }

    fun summarizeSelectedPageWithAssistant() = summarizePageWithAssistant(selectedTabId)

    fun summarizePageWithAssistant(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val request = AssistantSummaryRequest.create(
            url = tab.url,
            title = tab.title,
            instruction = activity.getString(R.string.assistant_summary_prompt),
        ) ?: return
        if (assistantSummary.launch(request) == AssistantSummaryResult.Unsupported) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_assistant_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun extractSelectedPageForReader(onResult: (ReaderExtractionResult) -> Unit) {
        val tab = selectedTab
        if (
            !tab.url.startsWith("https://", ignoreCase = true) &&
            !tab.url.startsWith("http://", ignoreCase = true)
        ) {
            onResult(ReaderExtractionResult.Failure(ReaderExtractionFailure.UnsupportedPage))
            return
        }
        val engineSession = geckoEngineSessions[tab.id]
        if (engineSession == null) {
            onResult(ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse))
            return
        }
        val expectedUrl = tab.url
        val expectedNavigationGeneration = navigationGenerations.getOrDefault(tab.id, 0)
        engineSession.extractPageForReader { result ->
            val currentTab = tabs.firstOrNull { candidate -> candidate.id == tab.id }
            if (
                destroyed ||
                selectedTabId != tab.id ||
                currentTab?.url != expectedUrl ||
                navigationGenerations.getOrDefault(tab.id, 0) !=
                expectedNavigationGeneration ||
                geckoEngineSessions[tab.id] !== engineSession
            ) {
                onResult(
                    ReaderExtractionResult.Failure(
                        ReaderExtractionFailure.InvalidResponse,
                    ),
                )
            } else {
                onResult(ReaderExtractionParser.parseJson(result))
            }
        }
    }

    fun saveLinkPeekToReader(
        previewView: View,
        expectedUrl: String,
        onResult: (ReaderExtractionResult) -> Unit,
    ) {
        val safeExpectedUrl = BrowserUriPolicy.normalizeHttpUrl(expectedUrl)
        val binding = geckoLinkPeekBindings[previewView]
        val sourceTab = binding?.sourceTabId?.let { sourceTabId ->
            tabs.firstOrNull { tab -> tab.id == sourceTabId }
        }
        if (
            safeExpectedUrl == null ||
            binding == null ||
            sourceTab == null ||
            sourceTab.isIncognito
        ) {
            onResult(ReaderExtractionResult.Failure(ReaderExtractionFailure.UnsupportedPage))
            return
        }
        if (
            binding.isLoading ||
            binding.progress < 100 ||
            !isCurrentLinkPeekPreview(previewView, binding, safeExpectedUrl)
        ) {
            onResult(ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse))
            return
        }

        binding.session.extractPageForReader { rawResult ->
            if (!isCurrentLinkPeekPreview(previewView, binding, safeExpectedUrl)) {
                onResult(ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse))
                return@extractPageForReader
            }
            val result = ReaderExtractionParser.parseJson(rawResult)
            val document = (result as? ReaderExtractionResult.Success)?.document
            if (
                document == null ||
                BrowserUriPolicy.normalizeHttpUrl(document.sourceUrl) != safeExpectedUrl
            ) {
                onResult(
                    result.takeIf { document == null }
                        ?: ReaderExtractionResult.Failure(
                            ReaderExtractionFailure.InvalidResponse,
                        ),
                )
                return@extractPageForReader
            }
            ReaderLibraryRepository.get(activity).saveSnapshotWithResult(
                document = document,
                progress = 0f,
                isPrivate = false,
            ) { snapshot ->
                if (snapshot == null) {
                    onResult(
                        ReaderExtractionResult.Failure(
                            ReaderExtractionFailure.InvalidResponse,
                        ),
                    )
                    return@saveSnapshotWithResult
                }
                if (isCurrentLinkPeekPreview(previewView, binding, safeExpectedUrl)) {
                    contentActions.dismiss()
                }
                onResult(result)
            }
        }
    }

    private fun isCurrentLinkPeekPreview(
        previewView: View,
        binding: GeckoLinkPeekBinding,
        expectedUrl: String,
    ): Boolean = !destroyed &&
        geckoLinkPeekBindings[previewView] === binding &&
        contentActions.isLinkPeekVisible &&
        contentActions.revision == binding.contentRevision &&
        (contentActions.sourceTabId ?: selectedTabId) == binding.sourceTabId &&
        selectedTabId == binding.sourceTabId &&
        tabs.firstOrNull { tab -> tab.id == binding.sourceTabId }?.isIncognito == false &&
        binding.committedUrl == expectedUrl &&
        !binding.isLoading

    fun openFindInPage(): Boolean {
        val tab = selectedTab
        if (tab.url == BLANK_URL) return false
        val session = geckoEngineSessions[tab.id] ?: return false
        closeFindInPage()
        findInPageSession = FindInPageSession(
            id = ++nextFindInPageSessionId,
            tabId = tab.id,
            geckoSession = session,
            navigationGeneration = navigationGenerations.getOrDefault(tab.id, 0),
        )
        findInPageState = FindInPageState(tabId = tab.id)
        return true

    }

    private fun isFindInPageSessionCurrent(session: FindInPageSession): Boolean {
        if (session.geckoSession != null) {
            val previewRuntime = externalLinkPreviewRuntime
            if (
                previewRuntime?.policyTab?.id == session.tabId &&
                previewRuntime.geckoBinding.session === session.geckoSession
            ) {
                return previewRuntime.generation == session.navigationGeneration &&
                    ExternalLinkPreviewRules.isCurrent(
                        state = externalLinkPreviewState,
                        sessionId = previewRuntime.sessionId,
                        generation = previewRuntime.generation,
                    )
            }
            return selectedTabId == session.tabId &&
                geckoEngineSessions[session.tabId] === session.geckoSession &&
                navigationGenerations.getOrDefault(session.tabId, 0) ==
                session.navigationGeneration
        }

        return false
    }

    fun updateFindInPageQuery(query: String) {
        val session = findInPageSession ?: return
        val state = findInPageState?.takeIf { it.tabId == session.tabId } ?: return
        val updated = FindInPageRules.withQuery(state, query)
        if (updated === state) return
        findInPageState = updated
        if (query.isEmpty()) {
            session.geckoSession?.clearFindInPage()
        } else {
            session.geckoSession?.findInPage(
                query = query,
                forward = true,
            ) findComplete@{ result ->
                val currentSession = findInPageSession
                val currentState = findInPageState
                if (
                    result == null ||
                    currentSession?.id != session.id ||
                    currentState?.tabId != session.tabId ||
                    currentState.query != query ||
                    !isFindInPageSessionCurrent(session)
                ) {
                    return@findComplete
                }
                findInPageState = FindInPageRules.withResult(
                    state = currentState,
                    activeMatchOrdinal = result.activeMatchOrdinal,
                    matchCount = result.matchCount,
                    isDoneCounting = result.isDoneCounting,
                )
            }
        }
    }

    fun findNextInPage(forward: Boolean): Boolean {
        val session = findInPageSession ?: return false
        val state = findInPageState ?: return false
        if (!FindInPageRules.canNavigate(state)) return false
        session.geckoSession?.findInPage(
            query = state.query,
            forward = forward,
        ) findComplete@{ result ->
            val currentSession = findInPageSession
            val currentState = findInPageState
            if (
                result == null ||
                currentSession?.id != session.id ||
                currentState?.tabId != session.tabId ||
                currentState.query != state.query ||
                !isFindInPageSessionCurrent(session)
            ) {
                return@findComplete
            }
            findInPageState = FindInPageRules.withResult(
                state = currentState,
                activeMatchOrdinal = result.activeMatchOrdinal,
                matchCount = result.matchCount,
                isDoneCounting = result.isDoneCounting,
            )
        }
        return true
    }

    fun closeFindInPage() {
        val session = findInPageSession
        findInPageSession = null
        findInPageState = null
        nextFindInPageSessionId++

        session?.geckoSession?.clearFindInPage()
    }

    fun shareSelectedPage() = sharePage(selectedTabId)

    fun copyLink(url: String) {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(
                activity.getString(R.string.external_link_preview_copy_label),
                safeUrl,
            ),
        )
        Toast.makeText(activity, R.string.toast_link_copied, Toast.LENGTH_SHORT).show()
    }

    fun shareLink(url: String) {
        val request = PageShareRequest.create(url = url, title = "") ?: return
        if (pageShare.launch(request) == PageShareResult.Unsupported) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_no_matching_app),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun sharePage(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val request = PageShareRequest.create(
            url = tab.url,
            title = tab.title,
        ) ?: return
        if (pageShare.launch(request) == PageShareResult.Unsupported) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_no_matching_app),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun printSelectedPage() = printPage(selectedTabId)

    internal fun clickFirefoxExtensionAction(key: GeckoExtensionActionKey): Boolean =
        geckoEngineSessionFactory.clickExtensionAction(key)

    internal fun dismissFirefoxExtensionPopup() {
        geckoEngineSessionFactory.dismissExtensionPopup()
        releaseFirefoxExtensionPopupView()
    }

    private fun releaseFirefoxExtensionPopupView() {
        (firefoxExtensionPopupView as? GeckoView)?.releaseSession()
        firefoxExtensionPopupView = null
        firefoxExtensionPopupIdentity = null
    }

    fun printPage(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.url == BLANK_URL) return
        if (geckoEngineSessions[tab.id]?.printPage() != true) showPrintingUnavailable()
    }

    private fun showPrintingUnavailable() {
        Toast.makeText(
            activity,
            activity.getString(R.string.toast_printing_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun selectTab(tabId: String) {
        if (activeTabs.none { it.id == tabId }) return
        if (activeCapsuleTabId != null && activeCapsuleTabId != tabId) leaveSiteCapsule()
        val nowMillis = System.currentTimeMillis()
        touchTab(selectedTabId, nowMillis)
        touchTab(tabId, nowMillis)
        markResidentSessionAccess(tabId)
        scheduleResidentSessionTrim()
        pruneStaleTabs(nowMillis)
        if (tabId == selectedTabId) {
            persist()
            return
        }
        clearPermissionActivity(selectedTabId)
        updateSelectedTabId(tabId)
        rememberSelectedTab(activeProfileId, tabId)
        notifyMediaStateChanged()
        persist()
    }

    fun openSnoozedWakeTab(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId && !it.isIncognito } ?: return false
        if (tab.profileId != activeProfileId && !selectProfile(tab.profileId)) return false
        selectTab(tabId)
        return selectedTabId == tabId
    }

    fun switchToOpenTab(tabId: String): Boolean {
        if (tabId == selectedTabId || activeTabs.none { it.id == tabId }) return false
        val blankSourceTabId = selectedTab.takeIf(BrowserTab::isFreshBlankTab)?.id
        selectTab(tabId)
        blankSourceTabId?.let(::closeTab)
        return true
    }

    fun setBlankTabIncognito(enabled: Boolean): Boolean {
        val tab = selectedTab
        if (isSyncedProfile(tab.profileId)) return false
        if (tab.url != BLANK_URL || tab.isIncognito == enabled) return false
        if (enabled && !isProfileIsolationSupported) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_incognito_unsupported),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        val wasLastIncognitoTab = tab.isIncognito && tabs.count(BrowserTab::isIncognito) == 1
        if (wasLastIncognitoTab) prepareIncognitoProfileForRemoval()
        removeTabResources(tab.id, preserveFaviconGeneration = true)
        updateTab(tab.id) {
            it.copy(
                isIncognito = enabled,
                title = "",
                progress = 0,
                isLoading = false,
                canGoBack = false,
                canGoForward = false,
                blockedCount = 0,
                error = null,
                syncCandyId = when {
                    enabled -> null
                    isBoundSyncProfile(it.profileId) ->
                        it.syncCandyId ?: UUID.randomUUID().toString()
                    else -> it.syncCandyId
                },
            )
        }
        if (wasLastIncognitoTab) clearIncognitoProfile()
        reconcileCandyTrailForks(System.currentTimeMillis())
        engineViewRevision++
        persist()
        return true
    }

    fun closeTab(tabId: String) {
        val nowMillis = System.currentTimeMillis()
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val closingTab = tabs[index]
        if (!TabDeletionRules.canDelete(closingTab)) return
        enqueueSyncedTabClose(closingTab)
        if (activeCapsuleTabId == tabId) leaveSiteCapsule()
        val closesLastIncognitoTab =
            closingTab.isIncognito && tabs.count(BrowserTab::isIncognito) == 1
        if (closesLastIncognitoTab) prepareIncognitoProfileForRemoval()
        val profileIndex = activeTabs.indexOfFirst { it.id == tabId }
        val openerTabId = closingTab.openerTabId
        removeTabResources(tabId)
        tabs.removeAt(index)
        if (selectedTabId == tabId) {
            updateSelectedTabId(
                openerTabId?.takeIf { openerId -> activeTabs.any { it.id == openerId } }
                    ?: activeTabs.getOrNull(profileIndex.coerceAtMost(activeTabs.lastIndex))?.id
                    ?: newTabState(
                        nowMillis = nowMillis,
                        isIncognito = closingTab.isIncognito,
                    ).also(tabs::add).id,
            )
            touchTab(selectedTabId, nowMillis)
            rememberSelectedTab(activeProfileId, selectedTabId)
            markSyncedTabPending(selectedTab)
        }
        if (closingTab.isIncognito && tabs.none(BrowserTab::isIncognito)) {
            clearIncognitoProfile()
        }
        reconcileCandyTrailForks(nowMillis)
        persist()
    }

    fun closeAllTabs(): Int {
        val tabIds = TabDeletionRules.deletableTabIds(activeTabs)
        if (tabIds.isEmpty()) return 0
        removeTabs(
            tabIds = tabIds,
            nowMillis = System.currentTimeMillis(),
            persistChanges = true,
        )
        return tabIds.size
    }

    fun closeSelectedRootTab(): RootTabBackResult {
        val closingTab = tabs.firstOrNull { it.id == selectedTabId }
            ?: return RootTabBackResult.ShowTabOverview
        if (!TabDeletionRules.canDelete(closingTab)) {
            return RootTabBackResult.ShowTabOverview
        }
        val openerTabId = closingTab.openerTabId
            ?.takeIf { openerId -> activeTabs.any { it.id == openerId } }
        closeTab(closingTab.id)
        return if (openerTabId != null && selectedTabId == openerTabId) {
            RootTabBackResult.ReturnedToOpener
        } else {
            RootTabBackResult.ShowTabOverview
        }
    }

    fun snoozeContextLink(
        url: String,
        title: String?,
        wakeAtMillis: Long,
    ): SnoozeUndoToken? {
        val sourceTabId = contextLinkSourceTab()?.id ?: return null
        return snoozeContextLink(
            url = url,
            title = title,
            wakeAtMillis = wakeAtMillis,
            sourceTabId = sourceTabId,
        )
    }

    fun snoozeContextLink(
        url: String,
        title: String?,
        wakeAtMillis: Long,
        sourceTabId: String,
    ): SnoozeUndoToken? = snoozeContextLink(
        url = url,
        title = title,
        wakeAtMillis = wakeAtMillis,
        sourceTabId = sourceTabId,
        nowMillis = System.currentTimeMillis(),
    )

    @VisibleForTesting
    internal fun snoozeContextLink(
        url: String,
        title: String?,
        wakeAtMillis: Long,
        sourceTabId: String,
        nowMillis: Long,
    ): SnoozeUndoToken? {
        if (selectedTabId != sourceTabId) return null
        val sourceTab = tabs.firstOrNull { tab -> tab.id == sourceTabId } ?: return null
        if (
            sourceTab.isIncognito ||
            isSyncedProfile(sourceTab.profileId) ||
            isSessionEphemeralTab(sourceTab.id)
        ) return null
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return null
        val snoozedTab = newTabState(
            url = safeUrl,
            nowMillis = nowMillis,
            isIncognito = false,
            openerTabId = sourceTab.id,
            profileId = sourceTab.profileId,
        ).copy(
            title = title.orEmpty().trim().take(MAX_CONTEXT_LINK_TITLE_CHARS).ifEmpty {
                AddressResolver.displayText(safeUrl)
            },
        )
        if (!SnoozeRules.canSnooze(snoozedTab, wakeAtMillis, nowMillis)) return null
        val appliedSnoozedTab = SnoozedTab(
            tab = snoozedTab,
            wakeAtMillis = wakeAtMillis,
            createdAtMillis = nowMillis,
        )
        val updatedSnoozed = (snoozedTabs + appliedSnoozedTab)
            .sortedWith(compareBy<SnoozedTab>({ it.wakeAtMillis }, { it.tab.id }))
        if (!store.saveTabsAndSnoozedImmediately(
                tabs = persistableTabs(tabs),
                selectedTabId = selectedTabId,
                snoozedTabs = updatedSnoozed,
            )
        ) return null

        snoozedTabs.clear()
        snoozedTabs += updatedSnoozed
        snoozeScheduler.schedule(updatedSnoozed, nowMillis)
        runCatching(requestSnoozeNotificationPermission)
        contentActions.dismiss()
        return SnoozeUndoToken(
            tabId = snoozedTab.id,
            appliedSnoozedTab = appliedSnoozedTab,
            originalIndex = (tabs.indexOfFirst { tab -> tab.id == sourceTab.id } + 1)
                .coerceAtLeast(0),
            originalSelectedTabId = selectedTabId,
            selectedTabIdAfterSnooze = selectedTabId,
            replacementTabId = null,
            touchedTabBefore = null,
            touchedTabAfter = null,
        )
    }

    fun snoozeTab(
        tabId: String,
        wakeAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): SnoozeUndoToken? {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return null
        val tab = tabs[index]
        if (isSyncedProfile(tab.profileId)) return null
        if (isSessionEphemeralTab(tabId)) return null
        if (!SnoozeRules.canSnooze(tab, wakeAtMillis, nowMillis)) return null
        val originalTabStack = tabStacks.firstOrNull { tabId in it.tabIds }
        val updatedSnoozed = (snoozedTabs.filterNot { it.tab.id == tabId } +
            SnoozedTab(tab, wakeAtMillis, nowMillis))
            .sortedWith(compareBy<SnoozedTab>({ it.wakeAtMillis }, { it.tab.id }))
        val profileIndex = activeTabs.indexOfFirst { it.id == tabId }
        val openerTabId = tab.openerTabId
        val updatedTabs = tabs.toMutableList().apply { removeAt(index) }
        val originalSelection = selectedTabId
        var updatedSelection = selectedTabId
        var replacementTabId: String? = null
        var touchedTabBefore: BrowserTab? = null
        var touchedTabAfter: BrowserTab? = null
        if (selectedTabId == tabId) {
            val activeRemaining = updatedTabs.filter { it.profileId == activeProfileId }
            updatedSelection = openerTabId
                ?.takeIf { openerId -> activeRemaining.any { it.id == openerId } }
                ?: activeRemaining.getOrNull(
                    profileIndex.coerceAtMost(activeRemaining.lastIndex),
                )?.id
                ?: newTabState(nowMillis = nowMillis).also { replacement ->
                    replacementTabId = replacement.id
                    updatedTabs += replacement
                }.id
            val selectedIndex = updatedTabs.indexOfFirst { it.id == updatedSelection }
            if (selectedIndex >= 0) {
                touchedTabBefore = updatedTabs[selectedIndex]
                touchedTabAfter = updatedTabs[selectedIndex].copy(
                    lastAccessedAt = nowMillis,
                )
                updatedTabs[selectedIndex] = touchedTabAfter
            }
        }
        if (!store.saveTabsAndSnoozedImmediately(
                tabs = persistableTabs(updatedTabs),
                selectedTabId = updatedSelection,
                snoozedTabs = updatedSnoozed,
            )
        ) return null

        enqueueSyncedTabClose(tab)
        if (activeCapsuleTabId == tabId) leaveSiteCapsule()
        removeTabRuntimeForSnooze(tab)
        tabs.clear()
        tabs += updatedTabs
        snoozedTabs.clear()
        snoozedTabs += updatedSnoozed
        if (selectedTabId == tabId) {
            updateSelectedTabId(updatedSelection)
            rememberSelectedTab(activeProfileId, selectedTabId)
        }
        reconcileCandyTrailForks(nowMillis)
        persist()
        snoozeScheduler.schedule(snoozedTabs, nowMillis)
        runCatching(requestSnoozeNotificationPermission)
        return SnoozeUndoToken(
            tabId = tabId,
            appliedSnoozedTab = updatedSnoozed.first { it.tab.id == tabId },
            originalIndex = index,
            originalSelectedTabId = originalSelection,
            selectedTabIdAfterSnooze = updatedSelection,
            replacementTabId = replacementTabId,
            touchedTabBefore = touchedTabBefore,
            touchedTabAfter = touchedTabAfter,
            originalTabStack = originalTabStack,
            tabStackAfterSnooze = originalTabStack?.let { original ->
                tabStacks.firstOrNull { it.id == original.id }
            },
        )
    }

    fun undoSnooze(
        token: SnoozeUndoToken,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val result = SnoozeUndoRules.undo(
            tabs = tabs,
            selectedTabId = selectedTabId,
            snoozedTabs = snoozedTabs,
            token = token,
            maxTabs = MAX_TABS,
        ) ?: return false
        if (!profilesEnabled && result.restoredTab.profileId != profiles.first().id) return false
        if (!store.saveTabsAndSnoozedImmediately(
                tabs = persistableTabs(result.tabs),
                selectedTabId = result.selectedTabId,
                snoozedTabs = result.snoozedTabs,
            )
        ) return false

        result.removedReplacementTabId?.let(::removeTabResources)
        tabs.clear()
        tabs += result.tabs
        token.originalTabStack?.let { originalStack ->
            val restoredStacks = TabStackRules.restoreSnoozedMember(
                stacks = tabStacks,
                tabs = tabs,
                restoredTabId = result.restoredTab.id,
                originalStack = originalStack,
                stackAfterSnooze = token.tabStackAfterSnooze,
            )
            tabStacks.clear()
            tabStacks += restoredStacks
        }
        snoozedTabs.clear()
        snoozedTabs += result.snoozedTabs
        updateSelectedTabId(result.selectedTabId)
        if (selectedTabId == result.restoredTab.id) activeProfileId = result.restoredTab.profileId
        rememberSelectedTab(activeProfileId, selectedTabId)
        reconcileCandyTrailForks(nowMillis)
        restoreSnoozedCandyTrail(result.restoredTab)
        markSyncedTabPending(result.restoredTab)
        enqueueSyncedTab(result.restoredTab.id)
        persist()
        snoozeScheduler.schedule(result.snoozedTabs, nowMillis)
        return true
    }

    fun rescheduleSnoozedTab(
        tabId: String,
        wakeAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val updated = SnoozeMutationRules.rescheduled(
            tabs = snoozedTabs,
            tabId = tabId,
            wakeAtMillis = wakeAtMillis,
            nowMillis = nowMillis,
        ) ?: return false
        if (!snoozedTabStore.save(updated)) return false
        snoozedTabs.clear()
        snoozedTabs += updated
        snoozeScheduler.schedule(updated, nowMillis)
        return true
    }

    fun openSnoozedTabNow(
        tabId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val snoozed = snoozedTabs.firstOrNull { it.tab.id == tabId } ?: return false
        if (!profilesEnabled && snoozed.tab.profileId != profiles.first().id) return false
        val result = SnoozeRestoreRules.restoreDue(
            tabs = tabs,
            snoozedTabs = listOf(snoozed.copy(wakeAtMillis = nowMillis)),
            profiles = profiles,
            activeProfileId = activeProfileId,
            nowMillis = nowMillis,
            maxTabs = MAX_TABS,
        )
        if (tabId !in result.completedTabIds || result.tabs.none { it.id == tabId }) return false
        val restoredTab = result.tabs.first { it.id == tabId }
        val remaining = SnoozeMutationRules.deleted(snoozedTabs, tabId) ?: return false
        if (!store.saveTabsAndSnoozedImmediately(
                tabs = persistableTabs(result.tabs),
                selectedTabId = tabId,
                snoozedTabs = remaining,
            )
        ) return false
        tabs.clear()
        tabs += result.tabs
        activeProfileId = restoredTab.profileId
        updateSelectedTabId(tabId)
        rememberSelectedTab(activeProfileId, tabId)
        snoozedTabs.clear()
        snoozedTabs += remaining
        reconcileCandyTrailForks(nowMillis)
        restoreSnoozedCandyTrail(restoredTab)
        markSyncedTabPending(restoredTab)
        enqueueSyncedTab(restoredTab.id)
        persist()
        snoozeScheduler.schedule(remaining, nowMillis)
        return true
    }

    fun deleteSnoozedTab(tabId: String): Boolean {
        val remaining = SnoozeMutationRules.deleted(snoozedTabs, tabId) ?: return false
        if (!snoozedTabStore.save(remaining)) return false
        snoozedTabs.clear()
        snoozedTabs += remaining
        candyTrails.remove(tabId)
        candyTrailGenerations.remove(tabId)
        candyTrailRepository.delete(tabId)
        reconcileCandyTrailForks(System.currentTimeMillis())
        geckoSessionStateStore.delete(tabId)
        snoozeScheduler.schedule(remaining)
        return true
    }

    fun setTabPinned(tabId: String, isPinned: Boolean): Boolean {
        if (isSessionEphemeralTab(tabId)) return false
        val updatedTabs = TabPinningRules.withPinnedState(
            tabs = activeTabs,
            tabId = tabId,
            isPinned = isPinned,
        )
        if (updatedTabs == activeTabs) return false
        replaceProfileTabs(activeProfileId, updatedTabs)
        enqueueSyncedTabPinned(tabId, isPinned)
        persist()
        return true
    }

    fun createTabStack(
        tabIds: List<String>,
        name: String,
        color: TabStackColor,
        previewTabId: String? = null,
    ): String? {
        val activeTabIds = activeTabs.mapTo(hashSetOf(), BrowserTab::id)
        if (tabIds.any { it !in activeTabIds }) return null
        val stackId = UUID.randomUUID().toString()
        val updated = TabStackRules.create(
            stacks = tabStacks,
            tabs = tabs,
            tabIds = tabIds,
            stackId = stackId,
            name = name,
            color = color,
            previewTabId = previewTabId,
        ) ?: return null
        tabStacks.replaceWith(updated)
        persist()
        return stackId
    }

    fun addTabToStack(tabId: String, stackId: String): Boolean {
        val updated = TabStackRules.addTab(
            stacks = tabStacks,
            tabs = tabs,
            tabId = tabId,
            stackId = stackId,
        ) ?: return false
        if (updated == tabStacks) return false
        tabStacks.replaceWith(updated)
        persist()
        return true
    }

    fun updateTabStack(
        stackId: String,
        tabIds: List<String>,
        name: String,
        color: TabStackColor,
        previewTabId: String? = null,
    ): Boolean {
        val activeTabIds = activeTabs.mapTo(hashSetOf(), BrowserTab::id)
        if (tabIds.any { it !in activeTabIds }) return false
        val updated = TabStackRules.update(
            stacks = tabStacks,
            tabs = tabs,
            stackId = stackId,
            tabIds = tabIds,
            name = name,
            color = color,
            previewTabId = previewTabId,
        ) ?: return false
        if (updated == tabStacks) return false
        tabStacks.replaceWith(updated)
        persist()
        return true
    }

    fun removeTabFromStack(tabId: String): Boolean {
        val updated = TabStackRules.removeTab(tabStacks, tabId)
        if (updated == tabStacks) return false
        tabStacks.replaceWith(updated)
        persist()
        return true
    }

    fun toggleTabStackCollapsed(stackId: String, triggerTabId: String? = null): Boolean {
        val updated = TabStackRules.toggleCollapsed(
            stacks = tabStacks,
            stackId = stackId,
            triggerTabId = triggerTabId,
        ) ?: return false
        tabStacks.replaceWith(updated)
        persist()
        return true
    }

    fun setTabStackPreview(stackId: String, tabId: String): Boolean {
        if (activeTabStacks.none { stack -> stack.id == stackId && tabId in stack.tabIds }) {
            return false
        }
        val updated = TabStackRules.setPreviewTab(tabStacks, stackId, tabId) ?: return false
        if (updated == tabStacks) return false
        tabStacks.replaceWith(updated)
        persist()
        return true
    }

    fun reorderTab(tabId: String, destinationIndex: Int): Boolean {
        if (automaticTabSortingEnabled) return false
        if (isSessionEphemeralTab(tabId)) return false
        val updatedTabs = TabReorderingRules.move(
            tabs = activeTabs,
            tabId = tabId,
            requestedIndex = destinationIndex,
        )
        if (updatedTabs == activeTabs) return false
        replaceProfileTabs(activeProfileId, updatedTabs)
        enqueueSyncedTabOrder(activeProfileId)
        persist()
        return true
    }

    private fun positionExtensionCreatedTab(tabId: String, requestedIndex: Int): Boolean {
        if (automaticTabSortingEnabled || isSessionEphemeralTab(tabId)) return false
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId } ?: return false
        if (tab.profileId != activeProfileId) return false
        val currentTabs = activeTabs
        val destinationIndex = TabReorderingRules.clampedDestinationIndex(
            tabs = currentTabs,
            tabId = tabId,
            requestedIndex = requestedIndex,
        ) ?: return false
        if (currentTabs.indexOfFirst { candidate -> candidate.id == tabId } == destinationIndex) {
            return true
        }
        return reorderTab(tabId, requestedIndex)
    }

    fun candyTrail(tabId: String): CandyTrail = candyTrails[tabId] ?: CandyTrail(tabId)

    fun forkCandyTrailNode(tabId: String, nodeId: String): String? {
        val nowMillis = System.currentTimeMillis()
        touchTab(tabId, nowMillis)
        pruneStaleTabs(nowMillis)
        val originTab = activeTabs.firstOrNull { it.id == tabId } ?: return null
        val trail = candyTrails[tabId] ?: return null
        val node = trail.nodes.firstOrNull { it.id == nodeId } ?: return null
        if (!CandyTrailForkRules.canCreateFork(tabs.size, MAX_TABS)) {
            showTabLimitReached()
            return null
        }
        val destinationTab = newTabState(
            url = node.url,
            nowMillis = nowMillis,
            isIncognito = originTab.isIncognito,
        ).copy(title = node.title.ifBlank { AddressResolver.displayText(node.url) })
        val forkedTrail = CandyTrailForkRules.create(
            trail = trail,
            originTab = originTab.toCandyTrailForkTab(),
            originNodeId = nodeId,
            destinationTab = destinationTab.toCandyTrailForkTab(),
            createdAt = nowMillis,
        ) ?: return null

        touchTab(selectedTabId, nowMillis)
        tabs += destinationTab
        setCandyTrail(originTab, forkedTrail)
        updateSelectedTabId(destinationTab.id)
        rememberSelectedTab(activeProfileId, destinationTab.id)
        persist()
        return destinationTab.id
    }

    fun activateCandyTrailFork(tabId: String, forkId: String): String? {
        val nowMillis = System.currentTimeMillis()
        touchTab(tabId, nowMillis)
        pruneStaleTabs(nowMillis)
        val originTab = activeTabs.firstOrNull { it.id == tabId } ?: return null
        val trail = candyTrails[tabId] ?: return null
        val fork = trail.forks.firstOrNull { it.id == forkId } ?: return null
        val openDestination = fork.destinationTabId?.let { destinationId ->
            activeTabs.firstOrNull { destination ->
                destination.id == destinationId &&
                    destination.profileId == originTab.profileId &&
                    destination.isIncognito == originTab.isIncognito
            }
        }
        if (openDestination != null) {
            selectTab(openDestination.id)
            return openDestination.id
        }
        if (!CandyTrailForkRules.canCreateFork(tabs.size, MAX_TABS)) {
            showTabLimitReached()
            return null
        }
        val destinationTab = newTabState(
            url = fork.url,
            nowMillis = nowMillis,
            isIncognito = originTab.isIncognito,
        ).copy(title = fork.title.ifBlank { AddressResolver.displayText(fork.url) })
        val reopenedTrail = CandyTrailForkRules.reopen(
            trail = trail,
            forkId = forkId,
            originTab = originTab.toCandyTrailForkTab(),
            destinationTab = destinationTab.toCandyTrailForkTab(),
            reopenedAt = nowMillis,
        ) ?: return null

        touchTab(selectedTabId, nowMillis)
        tabs += destinationTab
        setCandyTrail(originTab, reopenedTrail)
        updateSelectedTabId(destinationTab.id)
        rememberSelectedTab(activeProfileId, destinationTab.id)
        persist()
        return destinationTab.id
    }

    fun navigateToCandyTrailNode(tabId: String, nodeId: String): Boolean {
        val tab = activeTabs.firstOrNull { it.id == tabId } ?: return false
        val trail = candyTrails[tabId] ?: return false
        val node = trail.nodes.firstOrNull { it.id == nodeId } ?: return false
        val selectedTrail = CandyTrailRules.selectNode(trail, nodeId, System.currentTimeMillis())
            ?: return false
        setCandyTrail(tab, selectedTrail)
        pendingCandyTrailTargets[tabId] = nodeId
        selectTab(tabId)

        val existingSession = geckoEngineSessions[tabId]
        val binding = candyTrailHistoryBindings[tabId] ?: CandyTrailHistoryBinding()
        val targetIndex = CandyTrailHistoryReconciler.indexOfNode(binding, nodeId)
        updateTab(tabId) {
            it.copy(url = node.url, title = node.title, isLoading = true, progress = 0)
        }
        if (existingSession == null) {
            geckoEngineSessionFor(tabId)
        } else if (targetIndex != null && targetIndex != binding.currentIndex) {
            existingSession.goToHistoryIndex(targetIndex)
        } else if (targetIndex == binding.currentIndex && pageUrls[tabId] == node.url) {
            pendingCandyTrailTargets.remove(tabId)
            updateTab(tabId) { current -> current.copy(isLoading = false, progress = 100) }
        } else {
            loadGeckoWithPrivacy(tabId, existingSession, node.url)
        }
        return true

    }

    fun goBack() {
        if (!selectedTab.canGoBack) return
        val session = geckoEngineSessions[selectedTabId] ?: return
        val capsule = activeCapsuleForTab(selectedTabId)
        val targetUrl = session.historyUrlAtOffset(-1)
        if (
            capsule != null &&
            targetUrl != null &&
            CapsuleNavigationRules.decide(capsule, targetUrl) ==
            CapsuleNavigationDecision.OpenInFullCandy
        ) {
            leaveSiteCapsule()
        }
        session.execute(BrowserEngineCommands.back())
    }
    fun goForward() {
        if (!selectedTab.canGoForward) return
        val binding = candyTrailHistoryBindings[selectedTabId]
        binding?.entries?.getOrNull(binding.currentIndex + 1)?.nodeId?.let { targetNodeId ->
            pendingCandyTrailTargets[selectedTabId] = targetNodeId
        }
        geckoEngineSessions[selectedTabId]?.execute(BrowserEngineCommands.forward())
    }
    fun reload() {
        updateTab(selectedTabId) { it.copy(isLoading = true, progress = 0, error = null) }
        geckoEngineSessionFor(selectedTabId).execute(BrowserEngineCommands.reload())
    }

    internal fun reloadSelectedPageAfterExtensionChange() {
        if (!usesGeckoEngine) return
        geckoEngineSessions[selectedTabId]?.execute(BrowserEngineCommands.reload())
    }

    fun retryFailedPage(): Boolean {
        val tabId = selectedTabId
        if (selectedTab.error == null || selectedTab.isLoading) return false
        updateTab(tabId) { it.copy(isLoading = true, progress = 0, error = null) }
        geckoEngineSessionFor(tabId).execute(BrowserEngineCommands.reload())
        return true

    }

    fun stopLoading() {
        geckoEngineSessions[selectedTabId]?.execute(BrowserEngineCommands.stop())
        updateTab(selectedTabId) { it.copy(isLoading = false) }
    }

    fun clearCacheAndReload(onComplete: (Boolean) -> Unit): Boolean {
        val tabId = selectedTabId
        if (selectedTab.url == BLANK_URL) return false
        clearGeckoBrowsingDataAndReload(
            tabId = tabId,
            data = GeckoBrowsingData.AllCaches,
            onComplete = onComplete,
        )
        return true
    }

    fun clearCookiesAndReload(onComplete: (Boolean) -> Unit): Boolean {
        val tabId = selectedTabId
        if (selectedTab.url == BLANK_URL) return false
        clearGeckoBrowsingDataAndReload(
            tabId = tabId,
            data = GeckoBrowsingData.Cookies,
            onComplete = onComplete,
        )
        return true
    }

    private fun clearGeckoBrowsingDataAndReload(
        tabId: String,
        data: GeckoBrowsingData,
        onComplete: (Boolean) -> Unit,
    ) {
        clearExternalNavigationAuthorization(tabId)
        val session = geckoEngineSessionFor(tabId)
        val navigationGeneration = navigationGenerations[tabId]
        val capturedUrl = pageUrls[tabId] ?: selectedTab.url
        geckoEngineSessionFactory.clearBrowsingData(data) { cleared ->
            if (!cleared) {
                onComplete(false)
                return@clearBrowsingData
            }
            val currentTab = tabs.firstOrNull { tab -> tab.id == tabId }
            val unchanged = GeckoBrowsingDataReloadRules.canReload(
                capturedUrl = capturedUrl,
                currentUrl = currentTab?.let { tab -> pageUrls[tabId] ?: tab.url },
                capturedNavigationGeneration = navigationGeneration,
                currentNavigationGeneration = navigationGenerations[tabId],
                sameSession = geckoEngineSessions[tabId] === session,
            )
            if (unchanged) {
                updateTab(tabId) { tab ->
                    tab.copy(isLoading = true, progress = 0, error = null)
                }
                session.execute(BrowserEngineCommands.reload())
            }
            onComplete(unchanged)
        }
    }

    val commandCookieScope: CommandCookieScope
        get() = CommandCookieScope.AllBrowserProfiles

    fun addressSuggestionItems(
        query: String,
        searchQueries: List<String> = emptyList(),
        recallMatches: List<RecallMatch> = emptyList(),
        limit: Int = 10,
    ): List<AddressSuggestionItem> {
        if (RecallRules.isExplicitCommand(query)) {
            return AddressSuggestionComposer.compose(
                query = query,
                navigation = emptyList(),
                commands = emptyList(),
                searchQueries = emptyList(),
                recallMatches = recallMatches,
                limit = limit,
            )
        }
        val duplicateTabIds = TabDuplicateRules.tabIdsToClose(activeTabs, selectedTabId)
        val canCreateTab = hasTabCapacity()
        val canMoveSelectedTab = activeTabs.size > 1 || canCreateTab
        val definitions = BrowserCommandRegistry.commands(
            CommandContext(
                selectedTab = selectedTab,
                profiles = profiles,
                activeProfileId = activeProfileId,
                profilesEnabled = profilesEnabled,
                duplicateTabIds = duplicateTabIds,
                canCreateTab = canCreateTab,
                canCreateIncognitoTab = canCreateTab &&
                    isProfileIsolationSupported &&
                    !isSyncedProfile(activeProfileId),
                canMoveSelectedTab = canMoveSelectedTab,
                hasLoadedPage = selectedTab.url != BLANK_URL,
                canClearCookies = true,
            ),
        )
        val commandMatches = CommandMatcher.match(
            query = query,
            commands = commandCatalog.localize(definitions, commandCookieScope),
            limit = if (CommandMatcher.isExplicitCommandQuery(query)) definitions.size else limit,
        )
        val navigationMatches = if (CommandMatcher.isExplicitCommandQuery(query)) {
            emptyList()
        } else {
            addressSuggestions(query, limit)
        }
        return AddressSuggestionComposer.compose(
            query = query,
            navigation = navigationMatches,
            commands = commandMatches,
            searchQueries = searchQueries,
            recallMatches = recallMatches,
            limit = if (CommandMatcher.isExplicitCommandQuery(query)) definitions.size else limit,
        )
    }

    fun searchRecallForAddress(query: String, onComplete: (List<RecallMatch>) -> Unit) {
        val tab = selectedTab
        val recallQuery = if (RecallRules.isExplicitCommand(query)) {
            RecallRules.explicitQuery(query)
        } else {
            RecallRules.addressQuery(query)
        }
        if (
            !isRecallEnabled || recallDisablePending || browsingDataClearPending ||
            tab.isIncognito || recallQuery == null ||
            !RecallRules.canSearchFromAddress(query, isHistorySuggestionsEnabled)
        ) {
            onComplete(emptyList())
            return
        }
        val expectedTabId = tab.id
        val expectedProfileId = tab.profileId
        val expectedInput = query
        val limit = if (RecallRules.isExplicitCommand(query)) {
            RecallRules.MAX_COMMAND_RESULTS
        } else {
            RecallRules.MAX_ADDRESS_RESULTS
        }
        recallRepository.search(
            profileIds = setOf(expectedProfileId),
            query = recallQuery,
            limit = limit,
        ) { matches ->
            val current = tabs.firstOrNull { candidate -> candidate.id == expectedTabId }
            val accepted = isRecallEnabled && !recallDisablePending && !browsingDataClearPending &&
                RecallRules.canSearchFromAddress(
                    expectedInput,
                    isHistorySuggestionsEnabled,
                ) &&
                !destroyed &&
                selectedTabId == expectedTabId &&
                current?.isIncognito == false &&
                current.profileId == expectedProfileId
            onComplete(if (accepted && expectedInput == query) matches else emptyList())
        }
    }

    fun closeDuplicateTabs(confirmedTabIds: List<String>): Int {
        val currentlyClosable = TabDuplicateRules.tabIdsToClose(activeTabs, selectedTabId).toSet()
        val closeIds = confirmedTabIds.filter(currentlyClosable::contains)
        if (closeIds.isEmpty()) return 0
        val removedIncognitoTab = tabs.any { it.id in closeIds && it.isIncognito }
        closeIds.forEach(::removeTabResources)
        tabs.removeAll { it.id in closeIds }
        if (removedIncognitoTab && tabs.none(BrowserTab::isIncognito)) clearIncognitoProfile()
        reconcileCandyTrailForks(System.currentTimeMillis())
        persist()
        return closeIds.size
    }

    fun addressSuggestions(query: String, limit: Int = 8): List<AddressSuggestion> =
        BrowsingLibraryRules.addressSuggestions(
            history = history.filter { entry -> entry.profileId == selectedTab.profileId },
            tabs = activeTabs,
            selectedTabId = selectedTabId,
            isIncognito = selectedTab.isIncognito,
            query = query,
            limit = limit,
            includeHistory = isHistorySuggestionsEnabled,
        )

    fun addressDomainCompletion(query: String): String? = BrowsingLibraryRules.domainCompletion(
        history = history.filter { entry -> entry.profileId == selectedTab.profileId },
        favorites = favorites,
        tabs = activeTabs,
        selectedTabId = selectedTabId,
        isIncognito = selectedTab.isIncognito,
        query = query,
        includeHistory = isHistorySuggestionsEnabled,
    )

    val isSelectedTabFavorite: Boolean
        get() = !selectedTab.isIncognito && BrowsingLibraryRules.isFavorite(favorites, selectedTab.url)

    fun isFavorite(url: String): Boolean = BrowsingLibraryRules.isFavorite(favorites, url)

    fun toggleFavorite(tabId: String = selectedTabId): FavoriteMutation? {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        if (tab.isIncognito || tab.url == BLANK_URL) return null
        return toggleFavoriteEntry(
            url = tab.url,
            title = tab.title,
        )
    }

    fun toggleContextLinkFavorite(url: String, title: String?): FavoriteMutation? {
        val sourceTab = contextLinkSourceTab() ?: return null
        if (sourceTab.isIncognito) return null
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return null
        val mutation = toggleFavoriteEntry(
            url = safeUrl,
            title = title.orEmpty(),
        )
        if (mutation != null) contentActions.dismiss()
        return mutation
    }

    private fun toggleFavoriteEntry(url: String, title: String): FavoriteMutation? {
        val before = favorites.toList()
        val wasFavorite = BrowsingLibraryRules.isFavorite(favorites, url)
        val updated = BrowsingLibraryRules.toggleFavorite(
            current = favorites,
            entry = FavoriteEntry(
                url = url,
                title = title,
                addedAt = System.currentTimeMillis(),
            ),
        )
        if (updated == before) return null
        favorites.clear()
        favorites += updated
        store.saveFavorites(updated)
        return FavoriteMutation(
            before = before,
            applied = updated,
            added = !wasFavorite,
            revision = ++favoriteRevision,
        )
    }

    fun undoFavorite(mutation: FavoriteMutation): Boolean {
        val restored = FavoriteUndoRules.restore(
            current = favorites.toList(),
            currentRevision = favoriteRevision,
            mutation = mutation,
        ) ?: return false
        favoriteRevision++
        favorites.clear()
        favorites += restored
        store.saveFavorites(restored)
        return true
    }

    fun expandBottomBar() {
        bottomBarCompactStates[selectedTabId] = false
    }

    private fun collapseBottomBar() {
        bottomBarCompactStates[selectedTabId] = true
    }

    fun updateAddressBarDocked(docked: Boolean) {
        if (docked && !isAddressBarDockingEnabled) return
        val placement = if (docked) {
            addressBarDockPlacement ?: lastAddressBarDockPlacement
        } else {
            null
        }
        updateAddressBarDockPlacement(placement)
    }

    fun parkAddressBarOnRight() {
        if (!isAddressBarDockingEnabled) return
        updateAddressBarDockPlacement(
            lastAddressBarDockPlacement.copy(edge = AddressBarDockEdge.Right),
        )
    }

    fun updateAddressBarDockPlacement(placement: AddressBarDockPlacement?) {
        val normalized = placement?.normalized()
        if (normalized != null && !isAddressBarDockingEnabled) return
        if (addressBarDockPlacement == normalized) return
        collapseBottomBar()
        addressBarDockPlacement = normalized
        if (normalized != null) lastAddressBarDockPlacement = normalized
        store.saveAddressBarDockPlacement(normalized)
    }

    fun updateAddressBarDockingEnabled(enabled: Boolean) {
        if (isAddressBarDockingEnabled == enabled) return
        isAddressBarDockingEnabled = enabled
        store.saveAddressBarDockingEnabled(enabled)
        if (!enabled) updateAddressBarDocked(false)
    }

    fun updateExternalLinkPreviewEnabled(enabled: Boolean) {
        if (isExternalLinkPreviewEnabled == enabled) return
        isExternalLinkPreviewEnabled = enabled
        store.saveExternalLinkPreviewEnabled(enabled)
        if (!enabled) dismissExternalLinkPreview()
    }

    fun updateLinkLongPressAction(action: LinkLongPressAction) {
        if (linkLongPressAction == action) return
        linkLongPressAction = action
        store.saveLinkLongPressAction(action)
    }

    fun updateAddressBarActionLayout(layout: AddressBarActionLayout) {
        val normalized = AddressBarActionLayoutRules.normalize(layout)
        if (addressBarActionLayout == normalized) return
        addressBarActionLayout = normalized
        store.saveAddressBarActionLayout(normalized)
    }

    fun updateLinkPeekActionLayout(layout: LinkPeekActionLayout) {
        val normalized = LinkPeekActionLayoutRules.normalize(layout)
        if (linkPeekActionLayout == normalized) return
        linkPeekActionLayout = normalized
        store.saveLinkPeekActionLayout(normalized)
    }

    fun updateFullImmersiveModeEnabled(enabled: Boolean) {
        if (isFullImmersiveModeEnabled == enabled) return
        isFullImmersiveModeEnabled = enabled
        store.saveFullImmersiveModeEnabled(enabled)
        onFullImmersiveModeChanged(enabled)
    }

    fun updateStartupAnimationEnabled(enabled: Boolean) {
        if (isStartupAnimationEnabled == enabled) return
        isStartupAnimationEnabled = enabled
        store.saveStartupAnimationEnabled(enabled)
    }

    fun updateOpenHomeOnStartupEnabled(enabled: Boolean) {
        if (isOpenHomeOnStartupEnabled == enabled) return
        isOpenHomeOnStartupEnabled = enabled
        store.saveOpenHomeOnStartupEnabled(enabled)
    }

    fun updateScrollBarEnabled(enabled: Boolean) {
        if (isScrollBarEnabled == enabled) return
        isScrollBarEnabled = enabled
        store.saveScrollBarEnabled(enabled)
    }

    fun updateVideoAutoplayBlocked(blocked: Boolean) {
        if (blocked && !isVideoAutoplayBlockingSupported) return
        if (isVideoAutoplayBlocked == blocked) return
        isVideoAutoplayBlocked = blocked
        store.saveVideoAutoplayBlocked(blocked)
        geckoEngineSessions.values.forEach { session ->
            session.setVideoAutoplayBlocked(blocked)
        }
    }

    fun updateAppearanceSettings(settings: AppearanceSettings) {
        val normalized = settings.normalized()
        if (appearanceSettings == normalized) return
        val fontSizeChanged =
            appearanceSettings.webContentFontSizePercent != normalized.webContentFontSizePercent
        appearanceSettings = normalized
        store.saveAppearanceSettings(normalized)
        if (fontSizeChanged && usesGeckoEngine) {
            geckoEngineSessionFactory.setWebContentFontSizeFactor(
                normalized.webContentFontSizePercent / 100f,
            )
            geckoEngineSessions[selectedTabId]?.execute(BrowserEngineCommands.reload())
        }
    }

    fun configureSync(settings: SyncConnectionSettings): Boolean =
        settings.localProfileId
            ?.takeIf { profileId -> localProfiles.any { it.id == profileId } }
            ?.let { syncRepository.configure(settings) }
            ?: false

    fun enrollSync(
        serverPassword: CharArray,
        passphrase: CharArray,
        onComplete: (SyncEnrollmentOutcome) -> Unit,
    ) {
        syncRepository.enroll(serverPassword, passphrase).whenComplete { outcome, _ ->
            mainHandler.post {
                if (!destroyed) {
                    onComplete(outcome ?: SyncEnrollmentOutcome.Failed)
                    if (outcome == SyncEnrollmentOutcome.Enrolled) syncRepository.refresh()
                }
            }
        }
    }

    fun refreshSync() {
        syncRepository.refresh()
    }

    fun onAppearanceConfigurationChanged() {
        val externalPreview = externalLinkPreviewState
        if (geckoLinkPeekBindings.isNotEmpty()) {
            contentActions.dismiss()
        }
        destroyLinkPeekPreviewSessions()
        if (externalPreview != null) recreateExternalLinkPreviewRuntime(externalPreview)
    }

    fun updateDownloadSettings(settings: BrowserDownloadSettings) {
        val normalized = settings.normalized()
        if (downloadSettings == normalized) return
        downloadSettings = normalized
        store.saveDownloadSettings(normalized)
    }

    fun updateProfilesEnabled(enabled: Boolean) {
        if (profilesEnabled == enabled) return
        if (!enabled) {
            val firstProfileId = profiles.first().id
            if (activeProfileId != firstProfileId) selectProfile(firstProfileId)
        }
        profilesEnabled = enabled
        store.saveProfilesEnabled(enabled)
        if (!enabled) {
            externalLinkPreviewState
                ?.takeIf { it.targetProfileId != profiles.first().id }
                ?.copy(targetProfileId = profiles.first().id)
                ?.let(::recreateExternalLinkPreviewRuntime)
        }
    }

    fun updateWebContentEdgeToEdgeEnabled(enabled: Boolean) {
        if (
            isWebContentEdgeToEdgeEnabled == enabled &&
            isScrollAwareTopInsetEnabled == enabled
        ) {
            return
        }
        isWebContentEdgeToEdgeEnabled = enabled
        isScrollAwareTopInsetEnabled = enabled
        lastWindowInsets?.let(::dispatchWindowInsetsToAttachedEngineViews)
        refreshGeckoContentTopInsetPolicies()
    }

    fun prepareTabOverview(onReady: () -> Unit = {}) {
        pruneStaleTabs()
        refreshSelectedTabPreview(onReady)
    }

    fun refreshSelectedTabPreview(onReady: () -> Unit = {}) {
        captureVisiblePreview(selectedTabId, onComplete = onReady)
    }

    fun refreshSelectedTabPreviewBeforeDeparture(onReady: () -> Unit = {}) {
        captureVisiblePreview(
            selectedTabId,
            onComplete = onReady,
            acceptAfterDeparture = true,
        )
    }

    fun setPreviewContentBottomInWindowPx(bottomPx: Int) {
        previewContentBottomInWindowPx = bottomPx.takeIf { it > 0 }
    }

    fun previewTopInsetPx(tabId: String): Int = if (drawsEdgeToEdge(tabId) && !isSafeAreaForced(tabId)) {
        0
    } else {
        lastWindowInsets?.getInsets(SAFE_AREA_INSET_TYPES)?.top?.coerceAtLeast(0) ?: 0
    }

    fun updateBlockerSettings(settings: BlockerSettings) {
        val thirdPartyCookieSettingChanged =
            workerSettings.blockThirdPartyCookies != settings.blockThirdPartyCookies
        val cookieConsentSettingChanged =
            workerSettings.hideCookieConsent != settings.hideCookieConsent
        blockerSettings = settings
        workerSettings = settings
        store.saveBlockerSettings(settings)
        if (!thirdPartyCookieSettingChanged && !cookieConsentSettingChanged) return
        if (thirdPartyCookieSettingChanged) {
            geckoEngineSessionFactory.setBlockThirdPartyCookies(settings.blockThirdPartyCookies)
        }
        geckoEngineSessions.forEach { (tabId, session) ->
            updateProtectionRequestContext(tabId, pageUrls[tabId])
            geckoPrivacyPolicyFor(tabId)?.let { policy ->
                session.updatePrivacyPolicy(policy) {
                    session.execute(BrowserEngineCommands.reload())
                }
            }
        }
    }

    fun pauseSiteProtection(tabId: String, persistently: Boolean): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val host = PrivacyRequestSanitizer.webHost(pageUrls[tabId] ?: tab.url) ?: return false
        if (persistently && SiteExceptionRules.mayPersist(tab.isIncognito)) {
            permanentSiteExceptions = permanentSiteExceptions + (
                tab.profileId to SiteExceptionRules.withException(
                    permanentSiteExceptions[tab.profileId].orEmpty(),
                    host,
                )
            )
            temporarySiteExceptions.computeIfPresent(tabId) { _, hosts ->
                hosts.filterNot { exception ->
                    SiteExceptionRules.isPaused(host, listOf(exception))
                }.toSet().takeIf(Set<String>::isNotEmpty)
            }
            store.savePermanentSiteExceptions(permanentSiteExceptions)
            refreshProtectionForProfile(tab.profileId)
        } else temporarySiteExceptions[tabId] = setOf(host)
        siteExceptionRevision++
        reloadTabWithProtection(tabId)
        return true
    }

    fun resumeSiteProtection(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val host = PrivacyRequestSanitizer.webHost(pageUrls[tabId] ?: tab.url) ?: return false
        var changed = false
        var persistentChanged = false
        temporarySiteExceptions.computeIfPresent(tabId) { _, hosts ->
            val retained = hosts.filterNot { exception ->
                SiteExceptionRules.isPaused(host, listOf(exception))
            }.toSet()
            changed = changed || retained.size != hosts.size
            retained.takeIf(Set<String>::isNotEmpty)
        }
        if (!tab.isIncognito) {
            val profileHosts = permanentSiteExceptions[tab.profileId].orEmpty()
            val retained = profileHosts.filterNot { exception ->
                SiteExceptionRules.isPaused(host, listOf(exception))
            }.toSet()
            if (retained.size != profileHosts.size) {
                changed = true
                persistentChanged = true
                permanentSiteExceptions = if (retained.isEmpty()) {
                    permanentSiteExceptions - tab.profileId
                } else {
                    permanentSiteExceptions + (tab.profileId to retained)
                }
                store.savePermanentSiteExceptions(permanentSiteExceptions)
            }
        }
        if (!changed) return false
        if (persistentChanged) refreshProtectionForProfile(tab.profileId)
        siteExceptionRevision++
        reloadTabWithProtection(tabId)
        return true
    }

    fun setCookieBannerRemovalDisabled(tabId: String, disabled: Boolean): Boolean =
        updateSitePrivacyOverrides(tabId) { current, host ->
            current.copy(
                cookieBannerRemovalDisabled = SitePrivacyOverrideRules.overrideForSelection(
                    enabled = disabled,
                    bundledDefault = bundledSitePrivacyDefaults.cookieBannerRemovalDisabled(host),
                ),
            )
        }

    fun setForceVerticalScrolling(tabId: String, enabled: Boolean): Boolean =
        updateSitePrivacyOverrides(tabId) { current, host ->
            current.copy(
                forceVerticalScrolling = SitePrivacyOverrideRules.overrideForSelection(
                    enabled = enabled,
                    bundledDefault = bundledSitePrivacyDefaults.forceVerticalScrolling(host),
                ),
            )
        }

    fun setForcePageZooming(tabId: String, enabled: Boolean): Boolean =
        updateSitePrivacyOverrides(tabId) { current, _ ->
            current.copy(
                forcePageZooming = SitePrivacyOverrideRules.overrideForSelection(
                    enabled = enabled,
                    bundledDefault = false,
                ),
            )
        }

    fun setForceSafeArea(tabId: String, enabled: Boolean): Boolean =
        updateSitePrivacyOverrides(tabId, reloadAffectedPages = false) { current, _ ->
            current.copy(
                forceSafeArea = SitePrivacyOverrideRules.overrideForSelection(
                    enabled = enabled,
                    bundledDefault = false,
                ),
            )
        }

    private fun updateSitePrivacyOverrides(
        tabId: String,
        reloadAffectedPages: Boolean = true,
        persistently: Boolean = true,
        transform: (SitePrivacyOverrides, String) -> SitePrivacyOverrides,
    ): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val host = PrivacyRequestSanitizer.webHost(pageUrls[tabId] ?: tab.url) ?: return false
        val current = sitePrivacyOverridesFor(tab)[host] ?: SitePrivacyOverrides()
        val updated = transform(current, host)
        if (updated == current) return false

        val affectedTabIds = linkedSetOf(tabId)
        if (tab.isIncognito || !persistently) {
            val byHost = SitePrivacyOverrideRules.withOverride(
                temporarySitePrivacyOverrides[tabId].orEmpty(),
                host,
                updated,
            )
            if (byHost.isEmpty()) temporarySitePrivacyOverrides.remove(tabId)
            else temporarySitePrivacyOverrides[tabId] = byHost

        } else {
            val byHost = SitePrivacyOverrideRules.withOverride(
                permanentSitePrivacyOverrides[tab.profileId].orEmpty(),
                host,
                updated,
            )
            permanentSitePrivacyOverrides = if (byHost.isEmpty()) {
                permanentSitePrivacyOverrides - tab.profileId
            } else {
                permanentSitePrivacyOverrides + (tab.profileId to byHost)
            }
            store.saveSitePrivacyOverrides(permanentSitePrivacyOverrides)
            temporarySitePrivacyOverrides.computeIfPresent(tabId) { _, overrides ->
                SitePrivacyOverrideRules.withOverride(
                    current = overrides,
                    host = host,
                    overrides = SitePrivacyOverrides(),
                ).takeIf(Map<String, SitePrivacyOverrides>::isNotEmpty)
            }
            tabs.asSequence()
                .filter { candidate -> candidate.profileId == tab.profileId && !candidate.isIncognito }
                .forEach { candidate ->
                    val candidateHost = PrivacyRequestSanitizer.webHost(
                        pageUrls[candidate.id] ?: candidate.url,
                    )
                    if (candidateHost == host) affectedTabIds += candidate.id

                }
        }
        siteExceptionRevision++
        affectedTabIds.forEach { affectedTabId ->
            lastWindowInsets?.let { insets ->
                geckoViewBindings.values.filter { it.tabId == affectedTabId }.forEach { binding ->
                    applyGeckoWindowInsets(binding.view, binding.tabId, insets)
                }
            }
            val hasResidentEngineSession = affectedTabId == tabId ||
                affectedTabId in geckoEngineSessions
            if (reloadAffectedPages && hasResidentEngineSession) {
                reloadTabWithProtection(affectedTabId)
            }
        }
        return true
    }

    fun updateInactiveTabLifetime(lifetime: InactiveTabLifetime) {
        inactiveTabLifetime = lifetime
        store.saveInactiveTabLifetime(lifetime)
        pruneStaleTabs()
    }

    fun updateResidentTabLimit(limit: Int) {
        residentTabLimit = BrowserSessionResidencyRules.normalizedLimit(limit)
        store.saveResidentTabLimit(residentTabLimit)
        scheduleResidentSessionTrim()
    }

    fun updateSearchEngine(engine: SearchEngine) {
        searchEngine = engine
        store.saveSearchEngine(engine)
    }

    fun updatePageTranslationProvider(provider: PageTranslationProvider) {
        pageTranslationProvider = provider
        store.savePageTranslationProvider(provider)
    }

    fun updateSearxngSettings(settings: SearxngSettings) {
        searxngSettings = SearxngRules.sanitize(settings)
        store.saveSearxngSettings(searxngSettings)
    }

    fun updateAiModeToggleVisible(visible: Boolean) {
        isAiModeToggleVisible = visible
        store.saveAiModeToggleVisible(visible)
    }

    fun updateRecallEnabled(enabled: Boolean) {
        if (recallDisablePending || isRecallEnabled == enabled) return
        if (enabled) {
            isRecallEnabled = true
            store.saveRecallEnabled(true)
            return
        }
        recallDisablePending = true
        historyMutationExecutor.execute {
            val cleared = recallRepository.clear()
            mainHandler.post {
                if (!destroyed && cleared) {
                    isRecallEnabled = false
                    store.saveRecallEnabled(false)
                }
                recallDisablePending = false
            }
        }
    }

    fun updateSearchSuggestionProvider(provider: SearchSuggestionProvider) {
        searchSuggestionProvider = provider
        store.saveSearchSuggestionProvider(provider)
    }

    fun updateHistorySuggestionsEnabled(enabled: Boolean) {
        isHistorySuggestionsEnabled = enabled
        store.saveHistorySuggestionsEnabled(enabled)
    }

    fun updateDismissResistancePercent(percent: Int) {
        dismissResistancePercent = percent.coerceIn(10, 90)
        store.saveDismissResistancePercent(dismissResistancePercent)
    }

    fun updateTabOverviewMode(mode: TabOverviewMode) {
        tabOverviewMode = mode
        store.saveTabOverviewMode(mode)
    }

    fun updateTabStackFolderMode(mode: TabOverviewMode) {
        tabStackFolderMode = mode
        store.saveTabStackFolderMode(mode)
    }

    fun updateTabListStartsAtBottom(enabled: Boolean) {
        tabListStartsAtBottom = enabled
        store.saveTabListStartsAtBottom(enabled)
    }

    fun updateAutomaticTabSortingEnabled(enabled: Boolean) {
        automaticTabSortingEnabled = enabled
        store.saveAutomaticTabSortingEnabled(enabled)
        persist()
    }

    fun setSelectedDomainMuted(muted: Boolean): Boolean = setDomainMuted(selectedTabId, muted)

    fun setSelectedAlwaysBlockPopups(enabled: Boolean): Boolean =
        setAlwaysBlockPopups(selectedTabId, enabled)

    fun setAlwaysBlockPopups(tabId: String, enabled: Boolean): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val pageUrl = pageUrls[tabId] ?: tab.url
        val domain = PopupSiteRules.domainForUrl(pageUrl) ?: return false
        if (isAlwaysBlockPopupsEnabled(tab, pageUrl) == enabled) return false
        val domainsByProfile = if (tab.isIncognito) {
            temporaryAlwaysBlockPopupDomains
        } else {
            permanentAlwaysBlockPopupDomains
        }
        val updated = PopupSiteRules.withAlwaysBlockState(
            current = domainsByProfile[tab.profileId].orEmpty(),
            domain = domain,
            enabled = enabled,
        )
        if (updated.isEmpty()) domainsByProfile.remove(tab.profileId)
        else domainsByProfile[tab.profileId] = updated
        if (!tab.isIncognito) {
            store.saveAlwaysBlockPopupDomains(permanentAlwaysBlockPopupDomains.toMap())
        }
        return true
    }

    fun setDomainMuted(tabId: String, muted: Boolean): Boolean {
        if (!isDomainMuteSupported) return false
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val pageUrl = pageUrls[tabId] ?: tab.url
        val domain = DomainMuteRules.domainForUrl(pageUrl) ?: return false
        if (isDomainMuted(tab, pageUrl) == muted) return false
        val domainsByProfile = if (tab.isIncognito) {
            temporaryMutedDomains
        } else {
            permanentMutedDomains
        }
        val updated = DomainMuteRules.withMutedState(
            current = domainsByProfile[tab.profileId].orEmpty(),
            domain = domain,
            muted = muted,
        )
        if (updated.isEmpty()) domainsByProfile.remove(tab.profileId)
        else domainsByProfile[tab.profileId] = updated
        if (!tab.isIncognito) store.saveMutedDomains(permanentMutedDomains.toMap())
        refreshDomainMuteForProfile(tab.profileId, tab.isIncognito)
        return true
    }

    fun setSelectedDesktopView(enabled: Boolean): Boolean =
        setDesktopView(selectedTabId, enabled)

    fun canExportAppData(): Boolean = tabs.none(BrowserTab::isIncognito)

    fun setDesktopView(tabId: String, enabled: Boolean): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val pageUrl = pageUrls[tabId] ?: tab.url
        val domain = DesktopSiteRules.domainForUrl(pageUrl) ?: return false
        if (isDesktopView(tab, pageUrl) == enabled) return false
        val domainsByProfile = if (tab.isIncognito) {
            temporaryDesktopViewDomains
        } else {
            permanentDesktopViewDomains
        }
        val updated = DesktopSiteRules.withDesktopViewState(
            current = domainsByProfile[tab.profileId].orEmpty(),
            domain = domain,
            enabled = enabled,
        )
        if (updated.isEmpty()) domainsByProfile.remove(tab.profileId)
        else domainsByProfile[tab.profileId] = updated
        if (!tab.isIncognito) {
            store.saveDesktopViewDomains(permanentDesktopViewDomains.toMap())
        }
        reloadDesktopViewDomain(
            profileId = tab.profileId,
            isIncognito = tab.isIncognito,
            domain = domain,
        )
        return true
    }

    fun clearBrowsingData() {
        if (browsingDataClearPending) return
        browsingDataClearPending = true
        cancelPendingPermissionAccess()
        cancelPendingHttpAuthChallenge()
        cancelPendingFileChooser()
        cancelPendingWebPrompt()
        dismissFirefoxExtensionPopup()
        releaseExternalLinkPreviewRuntime(resumeSelectedTab = false)
        destroyLinkPeekPreviewSessions()
        geckoEngineSessions.keys.toList().forEach(::closeGeckoEngineSession)
        geckoSessionStateStore.clear()
        geckoEngineSessionFactory.clearAllData { cleared ->
            if (destroyed) return@clearAllData
            if (!cleared) {
                browsingDataClearPending = false
                Toast.makeText(activity, R.string.history_clear_failed, Toast.LENGTH_SHORT).show()
                engineViewRevision++
                return@clearAllData
            }
            finishClearingBrowsingData()
            engineViewRevision++
        }
    }

    private fun finishClearingBrowsingData() {
        activePermissions.clear()
        permissionRepository.clearAll()
        permissionRevision++
        tabs.forEach { tab ->
            updateProtectionRequestContext(tab.id, pageUrls[tab.id] ?: tab.url)
        }
        mainHandler.removeCallbacks(blockerCountFlush)
        synchronized(privacyEventLock) {
            pendingBlockedCounts.clear()
            pendingPrivacyTabs.clear()
            reportedAllowedDecisions.clear()
            blockerFlushScheduled.set(false)
            privacyXRayRepository.clear()
        }
        incognitoRuleHits.clear()
        if (filterRules.any { it.hitCount > 0 }) {
            filterRules.indices.forEach { index ->
                filterRules[index] = filterRules[index].copy(hitCount = 0)
            }
            savePersistentFilterRules()
        }
        privacySnapshots.clear()
        temporarySiteExceptions.clear()
        permanentSiteExceptions = emptyMap()
        store.savePermanentSiteExceptions(emptyMap())
        temporarySitePrivacyOverrides.clear()
        permanentSitePrivacyOverrides = emptyMap()
        store.saveSitePrivacyOverrides(emptyMap())
        temporaryMutedDomains.clear()
        permanentMutedDomains.clear()
        store.saveMutedDomains(emptyMap())
        temporaryDesktopViewDomains.clear()
        permanentDesktopViewDomains.clear()
        store.saveDesktopViewDomains(emptyMap())
        temporaryAlwaysBlockPopupDomains.clear()
        permanentAlwaysBlockPopupDomains.clear()
        store.saveAlwaysBlockPopupDomains(emptyMap())
        siteExceptionRevision++

        clearIncognitoProfile()
        tabs.indices.forEach { index ->
            tabs[index] = tabs[index].copy(blockedCount = 0, canGoBack = false, canGoForward = false)
        }
        historyMutationExecutor.execute {
            val mutation = historyRepository.clear()
            mainHandler.post {
                if (!destroyed) {
                    if (mutation.committed) {
                        history.clear()
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.toast_browsing_data_cleared),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.history_clear_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    browsingDataClearPending = false
                    engineViewRevision++
                }
            }
        }
        snoozedTabs.clear()
        snoozedTabStore.save(emptyList())
        snoozeScheduler.schedule(emptyList())
        previewEpoch++
        previews.clear()
        previewRepository.clear()
        faviconEpoch++
        faviconGenerations.clear()
        favicons.clear()
        faviconRepository.clear()
        candyTrailEpoch++
        candyTrailGenerations.clear()
        candyTrailHistoryBindings.clear()
        pendingCandyTrailTargets.clear()
        pendingCandyTrailRestoreIds.clear()
        candyTrailRedactionsDuringRestore.clear()
        isCandyTrailRestoreInProgress = false
        suppressedCandyTrailTabIds += tabs.map(BrowserTab::id)
        candyTrails.clear()
        candyTrailRepository.clear()
        geckoSessionStateStore.clear()
    }

    fun onPause() {
        contentActions.dismiss()
        if (externalLinkPreviewState == null) {
            captureVisiblePreview(selectedTabId, acceptAfterDeparture = true)
        }
        isActivityResumed = false
        geckoMediaPresentation
            ?.takeIf { presentation ->
                tabs.firstOrNull { it.id == presentation.tabId }?.isIncognito == true
            }
            ?.let { clearGeckoMediaPresentation() }
        touchTab(selectedTabId, System.currentTimeMillis())
        geckoEngineSessions.forEach(::persistGeckoSessionState)
        if (
            geckoMediaPresentation == null &&
            !pictureInPictureTransitionPending &&
            !isInPictureInPicture
        ) {
            geckoEngineSessions[selectedTabId]?.setActive(false)
        }
        externalLinkPreviewRuntime?.geckoBinding?.session?.setActive(false)
        persist()
    }

    fun prepareForAppDataTransfer(onReady: (Boolean) -> Unit) {
        ReaderLibraryRepository.get(activity).awaitIdle {
            externalLinkPreviewRuntime?.geckoBinding?.session?.setActive(false)
            geckoEngineSessions.forEach(::persistGeckoSessionState)
            persist()
            val persistentWritersReady = listOf(
                previewRepository.flush(),
                faviconRepository.flush(),
                candyTrailRepository.flush(),
                candyRuleRepository.flush(),
                userScriptRepository.flush(),
                store.flush(),
                permissionStore.flush(),
            ).all { ready -> ready }
            if (!persistentWritersReady) resumeEngineSessionsAfterTransferPreparationFailure()
            onReady(persistentWritersReady)
        }
    }

    private fun resumeEngineSessionsAfterTransferPreparationFailure() {
        if (externalLinkPreviewState == null) {

            if (isActivityResumed) geckoEngineSessions[selectedTabId]?.setActive(true)
        }
        externalLinkPreviewRuntime?.geckoBinding
            ?.takeIf { binding -> isActivityResumed && binding.view.isAttachedToWindow }
            ?.session
            ?.setActive(true)
    }

    fun onResume() {
        reloadHistory()
        applyCandyTrailRedactions(store.loadPendingCandyTrailRedactions())
        candyTrailRepository.processPendingRedactions()
        isActivityResumed = true
        if (!isInPictureInPicture && !pictureInPictureTransitionPending) {
            cancelPictureInPictureTransition()
        }
        isDefaultBrowser = DefaultBrowserRole.isHeld(activity)
        refreshExternalDownloadManagers()
        val nowMillis = System.currentTimeMillis()
        restoreDueSnoozedTabs(nowMillis)
        pruneStaleTabs(nowMillis, persistChanges = false)
        touchTab(selectedTabId, nowMillis)
        persist()
        if (externalLinkPreviewState == null) {
            geckoEngineSessions[selectedTabId]?.setActive(true)
        }
        externalLinkPreviewRuntime?.geckoBinding
            ?.takeIf { binding -> binding.view.isAttachedToWindow }
            ?.session
            ?.setActive(true)
    }

    fun onStart() {
        isActivityStarted = true
        syncRepository.startRealtime()
        mainHandler.removeCallbacks(syncRefreshRunnable)
        mainHandler.post(syncRefreshRunnable)
    }

    fun onStop(isInPictureInPictureMode: Boolean = false) {
        val wasActivityStarted = isActivityStarted
        val shouldCloseTabsWhenHidden = wasActivityStarted && !activity.isChangingConfigurations
        isActivityStarted = false
        syncRepository.stopRealtime()
        mainHandler.removeCallbacks(syncRefreshRunnable)
        val keepsPictureInPictureMedia = isInPictureInPictureMode ||
            isInPictureInPicture ||
            pictureInPictureTransitionPending
        if (!keepsPictureInPictureMedia) {
            stopPictureInPictureMedia()
        } else if (!isInPictureInPictureMode && !isInPictureInPicture) {
            val transitionGeneration = pictureInPictureTransitionGeneration
            mainHandler.postDelayed(
                {
                    if (
                        pictureInPictureTransitionGeneration == transitionGeneration &&
                        !isActivityStarted &&
                        !isInPictureInPicture &&
                        !activity.isInPictureInPictureMode &&
                        !destroyed
                    ) {
                        stopPictureInPictureMedia()
                        if (shouldCloseTabsWhenHidden) closeTabsOnBackground()
                    }
                },
                PICTURE_IN_PICTURE_TRANSITION_TIMEOUT_MILLIS,
            )
        }
        if (
            shouldCloseTabsWhenHidden &&
            !keepsPictureInPictureMedia
        ) {
            closeTabsOnBackground()
        }
        if (pendingPermissionAccess?.awaitingRuntime != true) cancelPendingPermissionAccess()
        pendingGeckoAndroidPermissionRequest?.request?.response?.complete(false)
        pendingGeckoAndroidPermissionRequest = null
        cancelPendingHttpAuthChallenge()
        cancelPendingWebPrompt()
        activePermissions.clear()
        permissionRevision++
        geckoEngineSessions.forEach(::persistGeckoSessionState)
    }

    private fun stopPictureInPictureMedia() {
        val ownerSession = activeMediaCommandSession()
        notifyGeckoPictureInPictureModeChanged(false)
        pictureInPictureTransitionGeneration++
        pictureInPicturePlaybackRetryGeneration++
        pictureInPictureTransitionPending = false
        pictureInPictureOwnerTabId = null
        pictureInPicturePlaybackExpected = false
        isInPictureInPicture = false
        ownerSession?.executeMediaCommand(GeckoMediaCommand.Pause)
        if (!isActivityResumed) ownerSession?.setActive(false)
        clearGeckoMediaPresentation()
    }

    fun destroy() {
        if (usesGeckoEngine) {
            // The runtime is process-scoped; do not let it retain this Activity via the listener.
            geckoEngineSessionFactory.setExtensionChromeHost(null)
            releaseFirefoxExtensionPopupView()
            geckoEngineSessionFactory.setToppingHostStateListener {}
            geckoEngineSessionFactory.setToppingInteractionDelegate(
                GeckoToppingInteractionDelegate.None,
            )
        }
        SnoozeRuntimeRegistry.unregister(snoozeRestoreCallback)
        mainHandler.removeCallbacks(syncRefreshRunnable)
        pendingSyncNavigationRunnables.values.forEach(mainHandler::removeCallbacks)
        pendingSyncNavigationRunnables.clear()
        remoteSyncNavigationUrls.clear()
        supersededRemoteSyncNavigationUrls.clear()
        syncObservation?.close()
        syncObservation = null
        syncRepository.close()
        closeFindInPage()
        releaseExternalLinkPreviewRuntime(resumeSelectedTab = false)
        clearGeckoMediaPresentation()
        destroyed = true
        notifyGeckoPictureInPictureModeChanged(false)
        pictureInPicturePlaybackRetryGeneration++
        pictureInPictureTransitionPending = false
        pictureInPictureOwnerTabId = null
        pictureInPicturePlaybackExpected = false
        isInPictureInPicture = false

        pendingGeckoPreviewCaptures.values.forEach { request ->
            request.timeout?.let(mainHandler::removeCallbacks)
            request.capture?.cancel()
        }
        pendingGeckoPreviewCaptures.clear()
        transientPopupTabIds.toList().forEach(::discardTransientPopup)
        pendingPopupNavigations.clear()
        pendingPopunderNavigations.clear()
        transientPopupTabIds.clear()
        blockedPopupOffer = null
        federatedLoginOffer = null
        federatedLoginOfferKeys.clear()
        captchaCompatibilityOffer = null
        captchaCompatibilityOfferKeys.clear()
        cancelPendingPermissionAccess()
        pendingGeckoAndroidPermissionRequest?.request?.response?.complete(false)
        pendingGeckoAndroidPermissionRequest = null
        cancelPendingHttpAuthChallenge()
        cancelPendingWebPrompt()
        cancelPendingFileChooser()
        fileChooserValidationExecutor.shutdownNow()
        profileWallpaperLoadGeneration++
        profileTabSwitcherWallpaperLoadGeneration++
        profileWallpaperExecutor.shutdownNow()
        historyMutationExecutor.shutdown()
        activePermissions.clear()
        permissionRepository.clearPrivateSession()
        mainHandler.removeCallbacks(blockerCountFlush)
        synchronized(privacyEventLock) {
            pendingBlockedCounts.clear()
            pendingPrivacyTabs.clear()
            blockerFlushScheduled.set(false)
            privacyXRayRepository.clear()
            protectionRequestContexts.clear()
        }
        temporarySiteExceptions.clear()
        temporarySitePrivacyOverrides.clear()
        temporaryMutedDomains.clear()
        temporaryDesktopViewDomains.clear()
        temporaryAlwaysBlockPopupDomains.clear()
        savePersistentFilterRules()
        persist()
        federatedLoginPopupTabIds.clear()
        federatedLoginCompatibilityTabIds.clear()
        destroyLinkPeekPreviewSessions()
        if (tabs.any(BrowserTab::isIncognito)) prepareIncognitoProfileForRemoval()
        geckoViewBindings.keys.toList().forEach(::detachBrowserEngineView)
        geckoEngineSessions.keys.toList().forEach(::closeGeckoEngineSession)
        residentSessionAccessOrder.clear()
        castMediaCandidate = null
        pendingConsentCssUrls.clear()
        navigationGenerations.clear()
        nativeSafeAreaFallbackTabs.clear()
        nativeSafeAreaFallbackReloads.clear()
        committedRecallPages.clear()
        externalNavigationGrants.clear()
        pendingInitialExternalNavigationGrants.clear()

        pageUrls.clear()
        bottomBarCompactStates.clear()
        browserChromeScrollStates.clear()
        previews.clear()
        favicons.clear()
        privacySnapshots.clear()
        faviconGenerations.clear()
        candyTrailEpoch++
        candyTrailHistoryBindings.clear()
        pendingCandyTrailTargets.clear()
        pendingCandyTrailRestoreIds.clear()
        candyTrailRedactionsDuringRestore.clear()
        isCandyTrailRestoreInProgress = false
        suppressedCandyTrailTabIds.clear()
        candyTrails.clear()
        candyTrailGenerations.clear()
    }

    private fun geckoEngineSessionFor(tabId: String): AndroidBrowserEngineSessionPort =
        geckoEngineSessions.getOrPut(tabId) {
            val tab = tabs.first { candidate -> candidate.id == tabId }
            BrowserInputDiagnostics.engineCreated(tab.id, "gecko")
            navigationGenerations.putIfAbsent(tab.id, 0)
            updateProtectionRequestContext(tab.id, tab.url)
            geckoEngineSessionFactory.create(
                tabId = tab.id,
                profileId = tab.profileId,
                isolationEnabled = profileForId(tab.profileId)?.isolationEnabled == true,
                isPrivate = tab.isIncognito,
                privacyPolicy = requireNotNull(geckoPrivacyPolicyFor(tab.id)),
                privacyEventSink = GeckoPrivacyEventSink { event ->
                    mainHandler.post { onGeckoPrivacyEvent(tab.id, event) }
                },
                trailHistoryEventSink = ::onGeckoTrailHistoryEvent,
                eventSink = ::onGeckoEngineEvent,
            ).also { session ->
                session.setVideoAutoplayBlocked(isVideoAutoplayBlocked)
                session.setAudioMuted(isTabAudioMuted(tab, tab.url))
                connectGeckoScrollListener(tab.id, session)
                session.setMediaStateListener(
                    GeckoMediaSessionStateListener { state ->
                        mainHandler.post { onGeckoMediaState(tab.id, session, state) }
                    },
                )
                session.setContentTargetListener { target ->
                    mainHandler.post {
                        onGeckoContentTarget(
                            tabId = tab.id,
                            session = session,
                            navigationGeneration = navigationGenerations[tab.id],
                            target = target,
                        )
                    }
                }
                session.setNavigationRequestListener { request ->
                    onGeckoNavigationRequest(tab.id, session, request)
                }
                session.setNewSessionListener { request ->
                    onGeckoNewSession(tab.id, session, request)
                }
                session.setDownloadResponseListener { response ->
                    onGeckoDownloadResponse(tab.id, session, response)
                }
                session.setFilePromptListener { request ->
                    onGeckoFilePrompt(tab.id, session, request)
                }
                session.setAndroidPermissionRequestListener { request ->
                    onGeckoAndroidPermissionRequest(tab.id, session, request)
                }
                session.setContentPermissionRequestListener { request ->
                    onGeckoContentPermissionRequest(tab.id, session, request)
                }
                session.setMediaPermissionRequestListener { request ->
                    onGeckoMediaPermissionRequest(tab.id, session, request)
                }
                session.setAuthPromptListener { request ->
                    onGeckoAuthPrompt(tab.id, session, request)
                }
                session.setWebPromptListener { request ->
                    onGeckoWebPrompt(tab.id, session, request)
                }
                session.setActive(
                    isActivityResumed &&
                        externalLinkPreviewState == null &&
                        tab.id == selectedTabId,
                )
                val restoreDecision = GeckoSessionStateSnapshotRules.restoreDecision(
                    snapshot = geckoSessionStateStore.load(tab.id),
                    tabId = tab.id,
                    profileId = tab.profileId,
                    isPrivate = tab.isIncognito || isSessionEphemeralTab(tab.id),
                )
                val restored = (restoreDecision as? GeckoSessionStateRestoreDecision.Restore)
                    ?.snapshot
                    ?.let { snapshot -> session.restoreSessionState(snapshot.encodedState) }
                    ?: false
                if (!restored && tab.url != BLANK_URL) {
                    if (!tab.isIncognito) geckoSessionStateStore.delete(tab.id)
                    session.execute(BrowserEngineCommands.load(tab.url))
                }
            }
        }.also {
            markResidentSessionAccess(tabId)
            scheduleResidentSessionTrim()
        }

    private fun connectGeckoScrollListener(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
    ) {
        browserChromeScrollStates.putIfAbsent(
            tabId,
            BrowserChromeScrollState(
                previousScrollYPx = session.scrollMetrics()?.offsetPx?.coerceAtLeast(0) ?: 0,
            ),
        )
        val rateDispatcher = BrowserEngineScrollRateDispatcher(
            schedule = { delayMillis, dispatch ->
                mainHandler.postDelayed(dispatch, delayMillis)
            },
            nowMillis = SystemClock::uptimeMillis,
        ) { event ->
            val eventGeneration = event.navigationGeneration
            onBrowserEngineScroll(
                tabId = tabId,
                rendererIsCurrent = geckoEngineSessions[tabId] === session &&
                    eventGeneration != null &&
                    eventGeneration == navigationGenerations.getOrDefault(tabId, 0),
                event = event,
            )
        }
        session.setScrollListener { event ->
            rateDispatcher.onScrollChanged(
                event.copy(
                    navigationGeneration = navigationGenerations.getOrDefault(tabId, 0),
                ),
            )
        }
    }

    private fun onGeckoNavigationRequest(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        request: GeckoMainFrameNavigationRequest,
    ): GeckoNavigationRequestDecision {
        if (destroyed || geckoEngineSessions[tabId] !== session) {
            return GeckoNavigationRequestDecision.Allow
        }
        val scheme = runCatching { Uri.parse(request.url).scheme }.getOrNull()?.lowercase()
        val safeHttpUrl = BrowserUriPolicy.normalizeHttpUrl(request.url)
        if (request.target == BrowserEngineNavigationTarget.New) {
            if (safeHttpUrl == null || !request.hasUserGesture) {
                return GeckoNavigationRequestDecision.Deny
            }
            mainHandler.post {
                if (!destroyed && geckoEngineSessions[tabId] === session) {
                    createGeckoPopup(tabId, safeHttpUrl)
                }
            }
            return GeckoNavigationRequestDecision.Deny
        }
        if (isQuarantinedPopup(tabId)) return GeckoNavigationRequestDecision.Deny
        if (handlePendingPopupNavigation(tabId, session, request.url).isBlocked) {
            return GeckoNavigationRequestDecision.Deny
        }
        if (handlePendingPopunderOpenerNavigation(tabId, session, request.url)) {
            return GeckoNavigationRequestDecision.Deny
        }
        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        if (safeHttpUrl != null) {
            updateExternalNavigationGrant(
                tabId = tabId,
                url = safeHttpUrl,
                isForMainFrame = true,
                hasGesture = request.hasUserGesture,
                isRedirect = request.isRedirect,
                nowElapsedRealtime = nowElapsedRealtime,
            )
        }
        val hasGrant = ExternalNavigationGrantRules.isActive(
            externalNavigationGrants[tabId],
            nowElapsedRealtime,
        )
        if (
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = scheme,
                isForMainFrame = true,
                hasGesture = request.hasUserGesture,
                isRedirect = request.isRedirect,
                hasUserNavigationGrant = hasGrant,
            )
        ) {
            val result = if (safeHttpUrl != null) {
                externalApps.openWebUrlExternally(safeHttpUrl)
            } else {
                externalApps.open(Uri.parse(request.url))
            }
            when (result) {
                ExternalLaunchResult.Launched -> {
                    externalNavigationGrants.remove(tabId)
                    showExternalAppOpenedToast()
                    return GeckoNavigationRequestDecision.Deny
                }
                is ExternalLaunchResult.OpenInBrowser -> {
                    mainHandler.post {
                        if (!destroyed && geckoEngineSessions[tabId] === session) {
                            openUrl(result.url)
                        }
                    }
                    return GeckoNavigationRequestDecision.Deny
                }
                ExternalLaunchResult.Unsupported -> if (safeHttpUrl == null) {
                    return GeckoNavigationRequestDecision.Deny
                }
            }
        }
        val capsule = activeCapsuleForTab(tabId) ?: return GeckoNavigationRequestDecision.Allow
        if (
            CapsuleNavigationRules.decide(capsule, request.url) !=
            CapsuleNavigationDecision.OpenInFullCandy
        ) {
            return GeckoNavigationRequestDecision.Allow
        }
        mainHandler.post {
            if (!destroyed && geckoEngineSessions[tabId] === session) {
                openCapsuleTargetInFullCandy(tabId, request.url)
            }
        }
        return GeckoNavigationRequestDecision.Deny
    }

    private fun onGeckoNewSession(
        openerTabId: String,
        openerSession: AndroidBrowserEngineSessionPort,
        request: GeckoNewSessionRequest,
    ): Boolean {
        if (destroyed || geckoEngineSessions[openerTabId] !== openerSession) return false
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(request.url) ?: return false
        return createGeckoPopup(openerTabId, safeUrl, request.session)
    }

    private fun createGeckoPopup(
        openerTabId: String,
        targetUrl: String,
        preparedSession: GeckoSession? = null,
    ): Boolean {
        val opener = tabs.firstOrNull { it.id == openerTabId } ?: return false
        val openerUrl = pageUrls[openerTabId] ?: opener.url
        val sitePaused = isSiteProtectionPaused(openerTabId, openerUrl)
        if (isAlwaysBlockPopupsEnabled(opener, openerUrl)) return false
        val popupTabId = createBackgroundTab(
            initialUrl = targetUrl,
            openerTabId = openerTabId,
            isIncognito = opener.isIncognito,
            transientPopup = true,
        ) ?: return false
        if (
            preparedSession != null &&
            !geckoEngineSessionFactory.prepareSession(popupTabId, preparedSession)
        ) {
            closeTab(popupTabId)
            return false
        }
        if (isFederatedLoginCompatibilityEnabled(opener, openerUrl)) {
            federatedLoginCompatibilityTabIds += popupTabId
        }
        val pending = PendingPopupNavigation(
            openerTabId = openerTabId,
            openerUrl = openerUrl,
            profileId = opener.profileId,
            isIncognito = opener.isIncognito,
            sitePaused = sitePaused,
            hadUserGesture = true,
        )
        pendingPopupNavigations[popupTabId] = pending
        geckoEngineSessionFor(popupTabId)
        mainHandler.postDelayed({
            if (pendingPopupNavigations[popupTabId] === pending) {
                pendingPopupNavigations.remove(popupTabId)
                if (popupTabId in transientPopupTabIds) discardTransientPopup(popupTabId)
                scheduleResidentSessionTrim()
            }
        }, PopupNavigationRules.PENDING_TIMEOUT_MILLIS)
        return true
    }

    private fun onGeckoDownloadResponse(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        response: GeckoExternalDownloadResponse,
    ) {
        if (!isGeckoRendererCurrent(tabId, session, navigationGenerations[tabId])) {
            response.close()
            return
        }
        response.start(
            object : GeckoDownloadTransferListener {
                override fun onStarted(start: GeckoDownloadTransferStart) {
                    showDownloadResult(
                        DownloadActionResult.Enqueued(start.id.toLong(), start.fileName),
                    )
                }

                override fun onFailed(reason: GeckoDownloadFailure) {
                    showDownloadResult(
                        DownloadActionResult.Failed(
                            activity.getString(R.string.error_download_start_failed),
                        ),
                    )
                }
            },
        )
    }

    private fun onGeckoFilePrompt(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        request: BrowserEngineFilePromptRequest,
    ) {
        cancelPendingFileChooser()
        val generation = navigationGenerations[tabId]
        if (!isGeckoRendererCurrent(tabId, session, generation, requireSelected = true)) {
            request.response.complete(null)
            return
        }
        val identity = FileChooserIdentity(tabId, requireNotNull(generation))
        val delivery = FileChooserResultDelivery<Array<Uri>?> { uris ->
            request.response.complete(uris?.map(Uri::toString))
        }
        val captureAction = FileChooserRules.captureAction(request.capture, request.mimeTypes)
        val captureOutput = captureAction?.let(::createFileCaptureOutput)
        pendingFileChooser = PendingFileChooser(
            identity = identity,
            delivery = delivery,
            geckoSession = session,
            allowMultiple = request.allowMultiple,
            acceptTypes = request.mimeTypes.toTypedArray(),
            captureOutput = captureOutput,
        )
        if (captureAction != null && captureOutput != null) {
            val intent = Intent(captureAction)
                .putExtra(MediaStore.EXTRA_OUTPUT, captureOutput.uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply {
                    when (request.capture) {
                        BrowserEngineFileCapture.User -> putExtra(CAMERA_FACING_EXTRA, CAMERA_FACING_FRONT)
                        BrowserEngineFileCapture.Environment ->
                            putExtra(CAMERA_FACING_EXTRA, CAMERA_FACING_BACK)
                        else -> Unit
                    }
                }
            runCatching { launchFileChooser(intent) }
                .onFailure { cancelPendingFileChooser(tabId) }
            return
        }
        val mimeTypes = request.mimeTypes.filter { value -> value.contains('/') }.distinct()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypes.singleOrNull() ?: "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.allowMultiple)
        }
        runCatching { launchFileChooser(intent) }
            .onFailure { cancelPendingFileChooser(tabId) }
    }

    private fun onGeckoAndroidPermissionRequest(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        request: BrowserEngineAndroidPermissionRequest,
    ) {
        val generation = navigationGenerations[tabId]
        if (!isGeckoRendererCurrent(tabId, session, generation, requireSelected = true)) {
            request.response.complete(false)
            return
        }
        pendingGeckoAndroidPermissionRequest?.request?.response?.complete(false)
        val missing = request.permissions.filterNotTo(linkedSetOf(), ::hasRuntimePermission)
        if (missing.isEmpty()) {
            request.response.complete(true)
            return
        }
        pendingGeckoAndroidPermissionRequest = PendingGeckoAndroidPermissionRequest(
            tabId = tabId,
            session = session,
            navigationGeneration = requireNotNull(generation),
            request = request,
        )
        runCatching { requestRuntimePermissions(missing) }
            .onFailure {
                pendingGeckoAndroidPermissionRequest = null
                request.response.complete(false)
            }
    }

    private fun onGeckoContentPermissionRequest(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        request: BrowserEngineContentPermissionRequest,
    ) {
        beginGeckoPermissionAccess(
            tabId = tabId,
            session = session,
            origin = request.origin,
            requested = setOf(request.permission),
            requestToken = request.response,
            grant = { allowed -> request.response.complete(request.permission in allowed) },
            deny = { request.response.complete(false) },
        )
    }

    private fun onGeckoMediaPermissionRequest(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        request: BrowserEngineMediaPermissionRequest,
    ) {
        beginGeckoPermissionAccess(
            tabId = tabId,
            session = session,
            origin = request.origin,
            requested = request.permissions,
            requestToken = request.response,
            grant = request.response::complete,
            deny = { request.response.complete(emptySet()) },
        )
    }

    private fun beginGeckoPermissionAccess(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        origin: String,
        requested: Set<SitePermission>,
        requestToken: Any,
        grant: (Set<SitePermission>) -> Unit,
        deny: () -> Unit,
    ) {
        if (pendingPermissionAccess != null) {
            deny()
            return
        }
        val normalizedOrigin = PermissionOrigin.normalize(origin)
        val identity = permissionRequestIdentity(tabId, normalizedOrigin)
        if (
            identity == null ||
            !isGeckoRendererCurrent(
                tabId = tabId,
                session = session,
                navigationGeneration = identity.navigationGeneration,
                requireSelected = true,
            ) ||
            !isPermissionRequestCurrent(identity)
        ) {
            deny()
            return
        }
        beginPermissionAccess(
            identity = identity,
            site = PermissionSiteKey(identity.profileId, identity.origin),
            requested = requested,
            kind = PendingPermissionKind.Gecko,
            requestToken = requestToken,
            grant = grant,
            deny = deny,
        )
    }

    private fun onGeckoAuthPrompt(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        request: BrowserEngineAuthPromptRequest,
    ) {
        val generation = navigationGenerations[tabId]
        val pageUrl = pageUrls[tabId] ?: tabs.firstOrNull { tab -> tab.id == tabId }?.url
        val challengeHost = request.uri?.let { value ->
            runCatching { java.net.URI(value).host }.getOrNull()
        }
        val details = if (request.isProxy) {
            HttpAuthPromptRules.proxyChallengeDetails(
                host = challengeHost,
                realm = request.realm,
                proxyUrl = request.uri,
            )
        } else {
            HttpAuthPromptRules.challengeDetails(
                host = challengeHost,
                realm = request.realm,
                pageUrl = pageUrl,
            )
        }
        if (
            details == null || (!request.isProxy && request.isCrossOriginSubresource) ||
            !isGeckoRendererCurrent(tabId, session, generation, requireSelected = true)
        ) {
            request.response.dismiss()
            return
        }
        cancelPendingHttpAuthChallenge()
        val promptId = ++httpAuthPromptSequence
        pendingHttpAuthChallenge = PendingHttpAuthChallenge(
            promptId = promptId,
            tabId = tabId,
            geckoSession = session,
            navigationGeneration = requireNotNull(generation),
            confirm = request.response::confirm,
            dismiss = request.response::dismiss,
        )
        httpAuthPrompt = HttpAuthPrompt(
            id = promptId,
            tabId = tabId,
            host = details.host,
            realm = details.realm,
            isPageSecure = details.isPageSecure,
            isProxy = request.isProxy,
        )
    }

    private fun onGeckoWebPrompt(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        request: BrowserEngineWebPromptRequest,
    ) {
        val generation = navigationGenerations[tabId]
        if (!isGeckoRendererCurrent(tabId, session, generation, requireSelected = true)) {
            request.response.dismiss()
            return
        }
        cancelPendingWebPrompt()
        val promptId = ++webPromptSequence
        val prompt = BrowserWebPromptRules.sanitized(
            id = promptId,
            tabId = tabId,
            kind = request.kind,
            title = request.title,
            message = request.message,
            defaultValue = request.defaultValue,
            choices = request.choices,
            allowMultiple = request.allowMultiple,
            shareUri = request.shareUri,
        )
        if (prompt == null) {
            request.response.dismiss()
            return
        }
        pendingWebPrompt = PendingWebPrompt(
            promptId = promptId,
            tabId = tabId,
            session = session,
            navigationGeneration = requireNotNull(generation),
            response = request.response,
        )
        webPrompt = prompt
    }

    private fun isGeckoRendererCurrent(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        navigationGeneration: Int?,
        requireSelected: Boolean = false,
    ): Boolean =
        !destroyed &&
            isActivityStarted &&
            (!requireSelected || selectedTabId == tabId) &&
            geckoEngineSessions[tabId] === session &&
            navigationGeneration != null &&
            navigationGenerations[tabId] == navigationGeneration &&
            tabs.any { tab -> tab.id == tabId }

    private fun onGeckoContentTarget(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        navigationGeneration: Int?,
        target: WebContentTarget,
    ) {
        if (
            destroyed ||
            !isActivityStarted ||
            !isActivityResumed ||
            selectedTabId != tabId ||
            geckoEngineSessions[tabId] !== session ||
            navigationGenerations[tabId] != navigationGeneration
        ) {
            return
        }
        handleWebContentLongPress(target, tabId)
    }

    private fun onGeckoMediaState(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        state: GeckoMediaSessionState,
    ) {
        if (destroyed || geckoEngineSessions[tabId] !== session) return
        if (state.isActive) geckoMediaStates[tabId] = state else geckoMediaStates.remove(tabId)
        val presentation = geckoMediaPresentation
        if (
            presentation?.tabId == tabId &&
            (!state.isActive ||
                (!state.isFullscreen &&
                    !pictureInPictureTransitionPending &&
                    !isInPictureInPicture))
        ) {
            clearGeckoMediaPresentation()
        }
        castMediaCandidate = geckoCastMediaCandidate(tabId, state)
        notifyMediaStateChanged()
    }

    private fun geckoSystemMediaState(): BrowserMediaState? {
        val mediaTabId = pictureInPictureOwnerTabId
            ?.takeIf { isInPictureInPicture || pictureInPictureTransitionPending }
            ?: selectedTabId
        val tab = tabs.firstOrNull { candidate -> candidate.id == mediaTabId } ?: return null
        if (tab.isIncognito) return null
        val state = geckoMediaStates[tab.id]?.takeIf { media -> media.isActive } ?: return null
        return geckoMediaStateForSystem(tab.id, state)
    }

    private fun geckoMediaStateForSystem(
        tabId: String,
        state: GeckoMediaSessionState,
    ): BrowserMediaState? {
        if (!state.isActive) return null
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId } ?: return null
        val video = state.videoTrackCount > 0
        return BrowserMediaState(
            tabId = tab.id,
            title = state.title?.take(MAX_WEB_MEDIA_TITLE_LENGTH) ?: tab.title,
            origin = Uri.parse(tab.url).host?.removePrefix("www.").orEmpty(),
            kind = if (video) BrowserMediaKind.Video else BrowserMediaKind.Audio,
            isPlaying = state.isPlaying,
            currentPositionMillis = state.currentPositionMillis,
            durationMillis = state.durationMillis,
            playbackRate = state.playbackRate,
            sourceUrl = state.sourceUrl,
            contentType = null,
            posterUrl = null,
        )
    }

    private fun geckoCastMediaCandidate(
        tabId: String,
        state: GeckoMediaSessionState,
    ): CastMediaCandidate? {
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId } ?: return null
        val source = CastMediaRules.source(
            state = geckoMediaStateForSystem(tabId, state),
            isPrivate = tab.isIncognito,
            isSelectedTab = tabId == selectedTabId,
        ) ?: return null
        return CastMediaCandidate(
            identity = CastMediaIdentity(
                tabId = tabId,
                navigationGeneration = navigationGenerations[tabId] ?: 0,
                documentId = state.sourceUrl.orEmpty(),
                mediaId = state.sourceUrl.orEmpty(),
                origin = Uri.parse(tab.url).host.orEmpty(),
            ),
            source = source,
        )
    }

    internal fun pauseCastMedia(candidate: CastMediaCandidate): Boolean {
        val session = geckoEngineSessions[candidate.identity.tabId] ?: return false
        val current = geckoMediaStates[candidate.identity.tabId] ?: return false
        if (geckoCastMediaCandidate(candidate.identity.tabId, current)?.source?.url != candidate.source.url) {
            return false
        }
        session.executeMediaCommand(GeckoMediaCommand.Pause)
        return true
    }

    private fun notifyMediaStateChanged() {
        onMediaStateChanged()
        scheduleResidentSessionTrim()
    }

    private fun minimizeGeckoMediaForTabDeparture(tabId: String) {
        val presentation = geckoMediaPresentation ?: return
        if (presentation.tabId != tabId) return
        val isPrivate = tabs.firstOrNull { tab -> tab.id == tabId }?.isIncognito == true
        if (isPrivate) clearGeckoMediaPresentation()
        else {
            presentation.minimizedByUser = true
            fullscreenVideoInsideSafeDrawingHost = true
            publishFullscreenVideoState()
        }
    }

    private fun loadGeckoWithPrivacy(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
        url: String,
    ) {
        pageUrls[tabId] = url
        updateProtectionRequestContext(tabId, url)
        val policy = geckoPrivacyPolicyFor(tabId) ?: return
        session.updatePrivacyPolicy(policy) {
            session.execute(BrowserEngineCommands.load(url))
        }
    }

    private fun geckoPrivacyPolicyFor(tabId: String): GeckoPrivacyPolicy? {
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId } ?: return null
        val pageUrl = pageUrls[tabId] ?: tab.url
        val context = protectionRequestContexts[tabId]
            ?: protectionRequestContextFor(tab, pageUrl).also { created ->
                protectionRequestContexts[tabId] = created
            }
        return geckoPrivacyPolicyFor(
            tab = tab,
            pageUrl = pageUrl,
            context = context,
            topInsetPx = geckoContentTopInsetPx(tabId),
            navigationGeneration = navigationGenerations.getOrDefault(tabId, 0),
        )
    }

    private fun geckoPrivacyPolicyFor(
        tab: BrowserTab,
        pageUrl: String,
        context: ProtectionRequestContext,
        topInsetPx: Int = 0,
        navigationGeneration: Int = 0,
    ): GeckoPrivacyPolicy {
        val siteProtectionPaused = isSiteProtectionPaused(tab.id, context, pageUrl)
        val federatedLoginCompatibilityEnabled =
            isFederatedLoginCompatibilityEnabled(tab, pageUrl)
        val captchaCompatibilityEnabled = isCaptchaCompatibilityEnabled(tab, pageUrl)
        return GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = PrivacyRequestSanitizer.webHost(pageUrl),
            pausedHosts = siteExceptionHostsForTab(tab.id),
            hideCookieConsent = workerSettings.hideCookieConsent && !siteProtectionPaused,
            cookieBannerRemovalDisabled = context.cookieBannerRemovalDisabled,
            blockThirdPartyCookies = workerSettings.blockThirdPartyCookies,
            allowThirdPartyCookiesForSite =
                siteProtectionPaused ||
                    federatedLoginCompatibilityEnabled ||
                    captchaCompatibilityEnabled,
            topInsetPx = topInsetPx,
            navigationGeneration = navigationGeneration,
        )
    }

    private fun geckoContentTopInsetPx(tabId: String): Int {
        if (
            !isScrollAwareTopInsetEnabled ||
            isSafeAreaForced(tabId) ||
            tabId in nativeSafeAreaFallbackTabs ||
            fullscreenVideoState?.tabId == tabId
        ) {
            return 0
        }
        return lastWindowInsets
            ?.getInsets(SAFE_AREA_INSET_TYPES)
            ?.top
            ?.coerceAtLeast(0)
            ?: 0
    }

    private fun refreshGeckoContentTopInsetPolicies() {
        geckoEngineSessions.forEach { (tabId, session) ->
            geckoPrivacyPolicyFor(tabId)?.let { policy ->
                session.updatePrivacyPolicy(policy)
            }
        }
    }

    private fun onGeckoPrivacyEvent(tabId: String, event: GeckoPrivacyEvent) {
        event.safeAreaFallbackNavigationGeneration?.let { navigationGeneration ->
            if (
                navigationGenerations.getOrDefault(tabId, 0) == navigationGeneration &&
                nativeSafeAreaFallbackTabs.add(tabId)
            ) {
                lastWindowInsets?.let(::dispatchWindowInsetsToAttachedEngineViews)
                nativeSafeAreaFallbackReloads[tabId] = NativeSafeAreaFallbackReload(
                    navigationGeneration = navigationGeneration + 1,
                    url = pageUrls[tabId],
                )
                geckoEngineSessions[tabId]?.execute(BrowserEngineCommands.reload())
            }
            return
        }
        val context = protectionRequestContexts[tabId] ?: return
        if (event.isCompatibilityObservation) {
            val observedPageHost = event.pageUrl?.let(PrivacyRequestSanitizer::webHost) ?: return
            if (observedPageHost != context.pageHost) return
            detectFederatedLoginRequest(tabId, event.requestUrl, context)
            detectCaptchaRequest(tabId, event.requestUrl, context)
            return
        }
        if (event.isBuiltIn) {
            if (event.wasBlocked) {
                queueBlockedRequest(
                    tabId = tabId,
                    requestUrl = event.requestUrl,
                    pageUrl = event.pageUrl,
                    expectedContext = context,
                    decision = PrivacyRuleDecisionSummary(
                        ruleId = null,
                        label = activity.getString(R.string.filter_rule_builtin),
                        action = PrivacyRuleDecisionAction.Block,
                    ),
                )
            }
            return
        }
        val rule = filterRules.firstOrNull { candidate -> candidate.id == event.ruleId } ?: return
        queueCandyRuleDecision(
            tabId = tabId,
            requestUrl = event.requestUrl,
            pageUrl = event.pageUrl,
            expectedContext = context,
            decision = CandyRuleDecision(
                action = if (event.wasBlocked) CandyDecisionAction.Block else CandyDecisionAction.Allow,
                ruleId = rule.id,
                rule = rule,
            ),
        )
    }

    private fun onGeckoEngineEvent(event: BrowserEngineEvent) {
        if (destroyed || geckoEngineSessions[event.tabId] == null) return
        if (ignoreSupersededRemoteNavigationEvent(event)) {
            engineViewRevision++
            return
        }
        when (event.type) {
            BrowserEngineEventType.NavigationStarted -> {
                val nextNavigationGeneration =
                    navigationGenerations.getOrDefault(event.tabId, 0) + 1
                val pendingFallbackReload = nativeSafeAreaFallbackReloads.remove(event.tabId)
                val preservesNativeSafeAreaFallback = pendingFallbackReload != null &&
                    pendingFallbackReload.navigationGeneration == nextNavigationGeneration &&
                    pendingFallbackReload.url == event.address
                val hadNativeSafeAreaFallback = !preservesNativeSafeAreaFallback &&
                    nativeSafeAreaFallbackTabs.remove(event.tabId)
                clearPermissionActivity(event.tabId)
                if (contentActions.sourceTabId == event.tabId) contentActions.dismiss()
                resetBrowserChromeScroll(event.tabId)
                navigationGenerations[event.tabId] = nextNavigationGeneration
                event.address?.let { address -> pageUrls[event.tabId] = address }
                refreshDomainMuteForTab(event.tabId)
                updateProtectionRequestContext(event.tabId, event.address)
                geckoPrivacyPolicyFor(event.tabId)?.let { policy ->
                    geckoEngineSessions[event.tabId]?.updatePrivacyPolicy(
                        policy = policy,
                        reloadOnCookiePermissionChange = true,
                    )
                }
                if (hadNativeSafeAreaFallback) {
                    lastWindowInsets?.let(::dispatchWindowInsetsToAttachedEngineViews)
                }
                updateTab(event.tabId) { tab ->
                    tab.copy(
                        url = event.address ?: tab.url,
                        title = "",
                        isLoading = true,
                        progress = 0,
                        error = null,
                    )
                }
            }
            BrowserEngineEventType.NavigationCommitted -> {
                event.address?.let { address -> pageUrls[event.tabId] = address }
                refreshDomainMuteForTab(event.tabId)
                val currentTab = tabs.firstOrNull { tab -> tab.id == event.tabId }
                updateTab(event.tabId) { tab ->
                    tab.copy(
                        url = event.address ?: tab.url,
                        title = event.title ?: tab.title,
                        isLoading = false,
                        progress = 100,
                        canGoBack = event.canGoBack,
                        canGoForward = event.canGoForward,
                        error = null,
                    )
                }
                val committedUrl = event.address ?: currentTab?.url
                if (committedUrl != null) {
                    refineGeckoCandyTrailTitle(
                        tabId = event.tabId,
                        url = committedUrl,
                        title = event.title.orEmpty().ifBlank { currentTab?.title.orEmpty() },
                    )
                }
                scheduleSyncedTabNavigation(
                    tabId = event.tabId,
                    remoteNavigationFinished = true,
                )
                persist()
            }
            BrowserEngineEventType.NavigationFailed -> {
                clearRemoteSyncNavigationTracking(event.tabId)
                updateTab(event.tabId) { tab ->
                    tab.copy(
                        isLoading = false,
                        progress = 100,
                        canGoBack = event.canGoBack,
                        canGoForward = event.canGoForward,
                        error = event.failureDescription,
                    )
                }
            }
            BrowserEngineEventType.StateChanged -> {
                event.address?.let { address -> pageUrls[event.tabId] = address }
                refreshDomainMuteForTab(event.tabId)
                val currentTab = tabs.firstOrNull { tab -> tab.id == event.tabId }
                updateTab(event.tabId) { tab ->
                    tab.copy(
                        url = event.address ?: tab.url,
                        title = event.title ?: tab.title,
                        canGoBack = event.canGoBack,
                        canGoForward = event.canGoForward,
                    )
                }
                val changedUrl = event.address ?: currentTab?.url
                val changedTitle = event.title?.takeIf(String::isNotBlank)
                if (changedUrl != null && changedTitle != null) {
                    refineGeckoCandyTrailTitle(event.tabId, changedUrl, changedTitle)
                }
                if (currentTab?.isLoading == true && event.isLoading == false) {
                    clearRemoteSyncNavigationTracking(event.tabId)
                }
                if (currentTab?.isLoading == false) {
                    val previousUrl = BrowserUriPolicy.normalizeHttpUrl(currentTab.url)
                    val changedUrl = event.address?.let(BrowserUriPolicy::normalizeHttpUrl)
                    if (changedUrl != null && changedUrl != previousUrl) {
                        scheduleSyncedTabNavigation(event.tabId)
                    }
                    persist()
                }
            }
            BrowserEngineEventType.Crashed -> {
                cancelPendingGeckoPreviewCapture(event.tabId)
                clearRemoteSyncNavigationTracking(event.tabId)
                if (geckoMediaPresentation?.tabId == event.tabId) {
                    clearGeckoMediaPresentation()
                }
                geckoMediaStates.remove(event.tabId)
                val crashedBindings = geckoViewBindings.entries
                    .filter { (_, binding) -> binding.tabId == event.tabId }
                crashedBindings.forEach { (host, _) -> geckoViewBindings.remove(host) }
                crashedBindings.forEach { (_, binding) ->
                    releaseGeckoView(binding.session, binding.view)
                    (binding.view.parent as? ViewGroup)?.removeView(binding.view)
                }
                geckoEngineSessions.remove(event.tabId)
                updateTab(event.tabId) { tab ->
                    tab.copy(
                        isLoading = false,
                        canGoBack = event.canGoBack,
                        canGoForward = event.canGoForward,
                        error = event.failureDescription,
                    )
                }
            }
            BrowserEngineEventType.Closed -> {
                clearRemoteSyncNavigationTracking(event.tabId)
                if (geckoMediaPresentation?.tabId == event.tabId) {
                    clearGeckoMediaPresentation()
                }
                geckoMediaStates.remove(event.tabId)
                geckoEngineSessions.remove(event.tabId)
            }
        }
        engineViewRevision++
    }

    private fun onGeckoTrailHistoryEvent(
        session: AndroidBrowserEngineSessionPort,
        event: GeckoCandyTrailHistoryEvent,
    ) {
        if (geckoEngineSessions[event.tabId] !== session) return
        persistGeckoSessionState(event.tabId, session)
        reconcileCandyTrailHistory(
            tabId = event.tabId,
            snapshot = event.snapshot,
            title = event.title,
        )
    }

    private fun reconcileCandyTrailHistory(
        tabId: String,
        snapshot: CandyTrailHistorySnapshot,
        title: String,
    ) {
        if (isSessionEphemeralTab(tabId)) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (snapshot.currentIndex !in snapshot.urls.indices) return
        val currentUrl = snapshot.urls[snapshot.currentIndex]
        if (tabId in suppressedCandyTrailTabIds) {
            if (currentUrl == pageUrls[tabId]) return
            suppressedCandyTrailTabIds.remove(tabId)
        }
        val pendingTargetNodeId = pendingCandyTrailTargets[tabId]?.takeIf { targetNodeId ->
            candyTrails[tabId]?.nodes?.any { node ->
                node.id == targetNodeId && node.url == currentUrl
            } == true
        }
        if (pendingTargetNodeId != null) {
            pendingCandyTrailTargets.remove(tabId)
        }
        val result = CandyTrailHistoryReconciler.reconcile(
            trail = candyTrails[tabId],
            tabId = tabId,
            previous = candyTrailHistoryBindings[tabId] ?: CandyTrailHistoryBinding(),
            snapshot = snapshot,
            title = title.ifBlank { tab.title },
            visitedAt = System.currentTimeMillis(),
            pendingTargetNodeId = pendingTargetNodeId,
        )
        candyTrailHistoryBindings[tabId] = result.binding
        setCandyTrail(tab, result.trail)
    }

    private fun refineGeckoCandyTrailTitle(tabId: String, url: String, title: String) {
        if (title.isBlank() || isSessionEphemeralTab(tabId)) return
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId } ?: return
        if (isSyncedProfile(tab.profileId)) return
        val trail = candyTrails[tabId] ?: return
        val refined = CandyTrailHistoryReconciler.refineCurrentTitle(
            trail = trail,
            url = url,
            title = title,
            visitedAt = System.currentTimeMillis(),
        )
        if (refined == trail) return
        setCandyTrail(
            tab,
            refined,
        )
    }

    private fun closeGeckoEngineSession(tabId: String) {
        cancelPendingGeckoPreviewCapture(tabId)
        if (geckoMediaPresentation?.tabId == tabId) clearGeckoMediaPresentation()
        geckoMediaStates.remove(tabId)
        val closingBindings = geckoViewBindings.entries
            .filter { (_, binding) -> binding.tabId == tabId }
        closingBindings.forEach { (host, _) -> geckoViewBindings.remove(host) }
        closingBindings.forEach { (_, binding) ->
            releaseGeckoView(binding.session, binding.view)
            (binding.view.parent as? ViewGroup)?.removeView(binding.view)
        }
        geckoEngineSessions.remove(tabId)?.let { session ->
            persistGeckoSessionState(tabId, session)
            session.execute(BrowserEngineCommands.close())
        }
    }

    private fun persistGeckoSessionState(
        tabId: String,
        session: AndroidBrowserEngineSessionPort,
    ) {
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId }
        if (
            tab == null ||
            tab.url == BLANK_URL ||
            tab.isIncognito ||
            isSessionEphemeralTab(tabId) ||
            isSyncedProfile(tab.profileId)
        ) {
            geckoSessionStateStore.delete(tabId)
            return
        }
        val snapshot = GeckoSessionStateSnapshotRules.forPersistence(
            tabId = tab.id,
            profileId = tab.profileId,
            isPrivate = false,
            encodedState = session.sessionStateSnapshot(),
        ) ?: return
        geckoSessionStateStore.save(snapshot)
    }

    private fun onBrowserEngineScroll(
        tabId: String,
        rendererIsCurrent: Boolean,
        event: BrowserEngineScrollEvent,
    ) {
        if (
            !BrowserChromeScrollRules.accepts(
                eventTabId = tabId,
                selectedTabId = selectedTabId,
                tabExists = activeTabs.any { tab -> tab.id == tabId },
                rendererIsCurrent = rendererIsCurrent,
                destroyed = destroyed,
            )
        ) return
        val density = activity.resources.displayMetrics.density
        val update = BrowserChromeScrollRules.update(
            state = browserChromeScrollStates[tabId] ?: BrowserChromeScrollState(),
            event = event,
            collapseThresholdPx = BOTTOM_BAR_COLLAPSE_THRESHOLD_DP * density,
            expandThresholdPx = BOTTOM_BAR_EXPAND_THRESHOLD_DP * density,
        )
        browserChromeScrollStates[tabId] = update.state
        update.compact?.let { compact ->
            if (bottomBarCompactStates[tabId] != compact) {
                bottomBarCompactStates[tabId] = compact
            }
        }
    }

    private fun resetBrowserChromeScroll(tabId: String) {
        browserChromeScrollStates.remove(tabId)
        bottomBarCompactStates[tabId] = false
    }

    private fun markResidentSessionAccess(tabId: String) {
        if (tabId !in geckoEngineSessions) return
        residentSessionAccessSequence++
        residentSessionAccessOrder[tabId] = residentSessionAccessSequence
    }

    private fun scheduleResidentSessionTrim() {
        if (residentSessionTrimScheduled || destroyed) return
        residentSessionTrimScheduled = true
        mainHandler.post {
            residentSessionTrimScheduled = false
            if (!destroyed) trimResidentSessions()
        }
    }

    private fun trimResidentSessions() {
        residentSessionAccessOrder.keys.retainAll(geckoEngineSessions.keys)
        val evictionIds = BrowserSessionResidencyRules.evictionOrder(
            residentTabIds = geckoEngineSessions.keys,
            accessOrder = residentSessionAccessOrder,
            protectedTabIds = protectedResidentTabIds(),
            limit = residentTabLimit,
        )
        if (evictionIds.isEmpty()) return
        evictionIds.forEach(::evictResidentSession)
        engineViewRevision++
    }

    private fun protectedResidentTabIds(): Set<String> = buildSet {
        selectedTabId.takeIf(String::isNotBlank)?.let(::add)
        geckoMediaPresentation?.tabId?.let(::add)
        pictureInPictureOwnerTabId?.let(::add)
        pendingPermissionAccess?.identity?.tabId?.let(::add)
        pendingHttpAuthChallenge?.tabId?.let(::add)
        pendingFileChooser?.identity?.tabId?.let(::add)
        addAll(pendingGeckoPreviewCaptures.keys)
        addAll(transientPopupTabIds)
        addAll(activeFederatedLoginFlowTabIds())
        blockedPopupOffer?.popupTabId?.let(::add)
        pendingPopupNavigations.forEach { (popupTabId, pending) ->
            add(popupTabId)
            add(pending.openerTabId)
        }
        pendingPopunderNavigations.values.forEach { pending ->
            add(pending.openerTabId)
            add(pending.popupTabId)
        }
        geckoEngineSessions.keys.filterTo(this) { tabId -> hasPermissionActivity(tabId) }
    }

    private fun evictResidentSession(tabId: String) {
        if (tabId !in geckoEngineSessions) return
        closeGeckoEngineSession(tabId)
        residentSessionAccessOrder.remove(tabId)
        updateTab(tabId) { it.copy(isLoading = false) }
    }

    private fun activeCapsuleForTab(tabId: String): SiteCapsule? = activeSiteCapsule
        ?.takeIf { activeCapsuleTabId == tabId && selectedTabId == tabId }

    private fun openCapsuleTargetInFullCandy(tabId: String, targetUrl: String) {
        val capsule = activeCapsuleForTab(tabId) ?: return
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId } ?: return
        val transition = CapsuleFullCandyTransitionRules.resolve(
            capsule = capsule,
            activeCapsuleTabId = activeCapsuleTabId,
            selectedTabId = selectedTabId,
            selectedProfileId = tab.profileId,
            selectedTabIsPrivate = tab.isIncognito,
            targetUrl = targetUrl,
        ) as? CapsuleFullCandyTransition.OpenTargetInNewTab ?: return
        leaveSiteCapsule()
        val previousTabId = selectedTabId
        if (createTab(transition.targetUrl, isIncognito = false) == previousTabId) {
            submitAddress(transition.targetUrl)
        }
    }

    private fun openUserScriptTab(request: UserScriptOpenTabRequest) {
        val sourceTab = tabs.firstOrNull { tab -> tab.id == request.tabId } ?: return
        if (
            sourceTab.isIncognito ||
            sourceTab.profileId != activeProfileId ||
            geckoEngineSessions[sourceTab.id] == null ||
            userScripts.none { script ->
                script.id == request.scriptId &&
                    script.enabled &&
                    UserScriptGrant.OpenInTab in script.grants
            }
        ) return
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(request.url) ?: return
        if (request.active) {
            createTab(
                initialUrl = safeUrl,
                isIncognito = false,
                openerTabId = sourceTab.id,
            )
        } else {
            createBackgroundTab(
                initialUrl = safeUrl,
                openerTabId = sourceTab.id,
                isIncognito = false,
            )
        }
    }

    fun playActiveMedia() {
        if (isInPictureInPicture) {
            pictureInPicturePlaybackExpected = true
            resumePictureInPicturePlayback()
        } else {
            activeMediaCommandSession()?.executeMediaCommand(GeckoMediaCommand.Play)
        }
    }

    fun pauseActiveMedia() {
        pictureInPicturePlaybackExpected = false
        pictureInPicturePlaybackRetryGeneration++
        activeMediaCommandSession()?.let { session ->
            session.setPictureInPicturePlaybackExpected(false)
            session.executeMediaCommand(GeckoMediaCommand.Pause)
        }
    }

    fun stopActiveMedia() {
        pictureInPicturePlaybackExpected = false
        pictureInPicturePlaybackRetryGeneration++
        activeMediaCommandSession()?.let { session ->
            session.setPictureInPicturePlaybackExpected(false)
            session.executeMediaCommand(GeckoMediaCommand.Stop)
        }
    }

    fun seekActiveMedia(positionMillis: Long) {
        activeMediaCommandSession()?.seekMedia(positionMillis.coerceAtLeast(0L))
    }

    private fun activeMediaCommandSession(): AndroidBrowserEngineSessionPort? {
        val pictureInPictureTabId = pictureInPictureOwnerTabId
            ?.takeIf { isInPictureInPicture || pictureInPictureTransitionPending }
        return geckoEngineSessions[pictureInPictureTabId ?: selectedTabId]
    }
    private fun presentationTabId(): String? =
        geckoMediaPresentation?.tabId

    private fun presentationIsPrivate(): Boolean? = presentationTabId()?.let { tabId ->
        tabs.firstOrNull { it.id == tabId }?.isIncognito
    }

    private fun beginPermissionAccess(
        identity: PermissionRequestIdentity,
        site: PermissionSiteKey,
        requested: Set<SitePermission>,
        kind: PendingPermissionKind,
        requestToken: Any,
        grant: (Set<SitePermission>) -> Unit,
        deny: () -> Unit,
    ) {
        val matrix = PermissionRequestRules.decisions(
            permissions = requested,
            decisionFor = { permission ->
                permissionRepository.decision(site, permission, identity.isPrivate)
            },
            allowedForSession = { permission ->
                permissionRepository.isAllowedForSession(site, permission, identity.isPrivate)
            },
        )
        val promptId = if (matrix.pending.isEmpty()) null else ++permissionPromptSequence
        val pending = PendingPermissionAccess(
            identity = identity,
            site = site,
            requested = requested,
            allowed = matrix.allowed,
            prompted = matrix.pending,
            kind = kind,
            requestToken = requestToken,
            promptId = promptId,
            awaitingRuntime = false,
            delivery = PermissionResponseDelivery(grant, deny),
        )
        pendingPermissionAccess = pending
        permissionRevision++
        if (promptId != null) {
            permissionPrompt = PermissionPrompt(
                id = promptId,
                tabId = identity.tabId,
                site = site,
                permissions = matrix.pending,
                isPrivate = identity.isPrivate,
            )
        } else {
            continuePermissionAccess(pending)
        }
    }

    private fun continuePermissionAccess(pending: PendingPermissionAccess) {
        if (!isPermissionRequestCurrent(
                pending.identity,
                requireResumed = !pending.awaitingRuntime,
            )
        ) {
            cancelPendingPermissionAccess(pending.identity.tabId)
            return
        }
        pendingPermissionAccess = pending
        val missingRuntimePermissions = pending.allowed.flatMapTo(linkedSetOf()) { permission ->
            if (hasRuntimePermissionFor(permission)) emptySet()
            else permission.runtimePermissions.filterNot(::hasRuntimePermission)
        }
        if (missingRuntimePermissions.isEmpty()) {
            finishPermissionAccess(pending, pending.allowed)
            return
        }
        pendingPermissionAccess = pending.copy(awaitingRuntime = true)
        permissionRevision++
        runCatching { requestRuntimePermissions(missingRuntimePermissions) }
            .onFailure { cancelPendingPermissionAccess(pending.identity.tabId) }
    }

    private fun finishPermissionAccess(
        pending: PendingPermissionAccess,
        granted: Set<SitePermission>,
    ) {
        if (pendingPermissionAccess?.requestToken !== pending.requestToken) return
        if (!isPermissionRequestCurrent(
                pending.identity,
                requireResumed = !pending.awaitingRuntime,
            )
        ) {
            cancelPendingPermissionAccess(pending.identity.tabId)
            return
        }
        pendingPermissionAccess = null
        permissionPrompt = null
        if (granted.isEmpty()) {
            runCatching { pending.delivery.deny() }
        } else {
            runCatching { pending.delivery.grant(granted) }
                .onSuccess {
                    activePermissions.record(pending.requestToken, ActivePermissionGrant(
                        tabId = pending.identity.tabId,
                        site = pending.site,
                        permissions = granted,
                    ))
                }
                .onFailure { activePermissions.drop(pending.requestToken) }
        }
        permissionRevision++
        scheduleResidentSessionTrim()
    }

    private fun cancelPendingPermissionAccess(tabId: String? = null) {
        val pending = pendingPermissionAccess ?: return
        if (tabId != null && pending.identity.tabId != tabId) return
        pendingPermissionAccess = null
        permissionPrompt = null
        runCatching { pending.delivery.deny() }
        permissionRevision++
        scheduleResidentSessionTrim()
    }

    private fun cancelPendingHttpAuthChallenge(
        tabId: String? = null,
    ) {
        val pending = pendingHttpAuthChallenge ?: return
        if (tabId != null && pending.tabId != tabId) return
        pendingHttpAuthChallenge = null
        httpAuthPrompt = null
        runCatching(pending.dismiss)
        scheduleResidentSessionTrim()
    }

    private fun isHttpAuthChallengeCurrent(pending: PendingHttpAuthChallenge): Boolean =
        !destroyed &&
            isActivityResumed &&
            selectedTabId == pending.tabId &&
            (
                pending.geckoSession?.let { session ->
                    geckoEngineSessions[pending.tabId] === session
                } ?: true
            ) &&
            tabs.any { tab -> tab.id == pending.tabId } &&
            navigationGenerations[pending.tabId] == pending.navigationGeneration

    private fun cancelPendingWebPrompt(tabId: String? = null) {
        val pending = pendingWebPrompt ?: return
        if (tabId != null && pending.tabId != tabId) return
        pendingWebPrompt = null
        webPrompt = null
        runCatching(pending.response::dismiss)
    }

    private fun isWebPromptCurrent(pending: PendingWebPrompt): Boolean =
        !destroyed &&
            isActivityResumed &&
            selectedTabId == pending.tabId &&
            geckoEngineSessions[pending.tabId] === pending.session &&
            navigationGenerations[pending.tabId] == pending.navigationGeneration

    private fun dropCanceledPermissionAccess(requestToken: Any) {
        val pending = pendingPermissionAccess ?: return
        if (pending.requestToken !== requestToken) return
        pendingPermissionAccess = null
        permissionPrompt = null
        pending.delivery.drop()
        permissionRevision++
        scheduleResidentSessionTrim()
    }

    private fun clearPermissionActivity(tabId: String) {
        cancelPendingPermissionAccess(tabId)
        pendingGeckoAndroidPermissionRequest
            ?.takeIf { pending -> pending.tabId == tabId }
            ?.let { pending ->
                pendingGeckoAndroidPermissionRequest = null
                pending.request.response.complete(false)
            }
        cancelPendingFileChooser(tabId)
        cancelPendingHttpAuthChallenge(tabId)
        cancelPendingWebPrompt(tabId)
        removeActivePermissionsForTab(tabId)
    }

    private fun removeActivePermissionsForTab(tabId: String) {
        val removed = activePermissions.dropTab(tabId)
        if (removed) {
            permissionRevision++
            scheduleResidentSessionTrim()
        }
    }

    private fun permissionRequestIdentity(
        tabId: String,
        normalizedOrigin: String?,
    ): PermissionRequestIdentity? {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        val origin = normalizedOrigin ?: return null
        val generation = navigationGenerations[tabId] ?: return null
        return PermissionRequestIdentity(
            tabId = tabId,
            profileId = tab.profileId,
            origin = origin,
            navigationGeneration = generation,
            isPrivate = tab.isIncognito,
        )
    }

    private fun isPermissionRequestCurrent(
        identity: PermissionRequestIdentity,
        requireResumed: Boolean = true,
    ): Boolean {
        val tab = tabs.firstOrNull { it.id == identity.tabId }
        val currentOrigin = PermissionOrigin.normalize(
            pageUrls[identity.tabId] ?: tab?.url,
        )
        return PermissionRequestRules.isCurrent(
            identity,
            PermissionRequestState(
                tabId = identity.tabId,
                profileId = tab?.profileId.orEmpty(),
                topLevelOrigin = currentOrigin,
                navigationGeneration = navigationGenerations[identity.tabId],
                isPrivate = tab?.isIncognito ?: false,
                isSelected = selectedTabId == identity.tabId,
                isActivityResumed = if (requireResumed) {
                    isActivityResumed && !destroyed
                } else {
                    isActivityStarted && !destroyed
                },
                tabExists = tab != null &&
                    (geckoEngineSessions[identity.tabId] != null),
            ),
        )
    }

    private fun hasRuntimePermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasRuntimePermissionFor(permission: SitePermission): Boolean = when (permission) {
        SitePermission.Location -> permission.runtimePermissions.any(::hasRuntimePermission)
        else -> permission.runtimePermissions.all(::hasRuntimePermission)
    }

    private fun isSafeFileChooserResult(uri: Uri, acceptTypes: Array<String>): Boolean {
        val authority = uri.authority?.lowercase() ?: return false
        val ownPackage = activity.packageName.lowercase()
        if (authority == ownPackage || authority.startsWith("$ownPackage.")) return false
        val resolver = activity.contentResolver
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        if (!FileChooserRules.acceptsMimeType(mimeType, acceptTypes)) return false
        return runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun isFileChooserCurrent(identity: FileChooserIdentity): Boolean =
        FileChooserRules.isCurrent(
            identity,
            FileChooserState(
                selectedTabId = selectedTabId,
                navigationGeneration = navigationGenerations[identity.tabId],
                tabExists = tabs.any { it.id == identity.tabId } &&
                    (
                        pendingFileChooser?.geckoSession?.let { session ->
                            geckoEngineSessions[identity.tabId] === session
                        } ?: (geckoEngineSessions[identity.tabId] != null)
                    ),
                isActivityResumed = isActivityStarted && !destroyed,
            ),
        )

    private fun cancelPendingFileChooser(tabId: String? = null) {
        val pending = pendingFileChooser ?: return
        if (tabId != null && pending.identity.tabId != tabId) return
        pendingFileChooser = null
        finalizeFileCapture(pending.captureOutput, keep = false)
        pending.delivery.complete(null)
        scheduleResidentSessionTrim()
    }

    private fun createFileCaptureOutput(action: String): FileCaptureOutput? {
        val isVideo = action == MediaStore.ACTION_VIDEO_CAPTURE
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val name = "candy-capture-${System.currentTimeMillis()}.${if (isVideo) "mp4" else "jpg"}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { activity.contentResolver.insert(collection, values) }.getOrNull()
            ?: return null
        return FileCaptureOutput(uri = uri, mimeType = mimeType)
    }

    private fun finalizeFileCapture(output: FileCaptureOutput?, keep: Boolean) {
        output ?: return
        runCatching {
            if (keep) {
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    .let { values -> activity.contentResolver.update(output.uri, values, null, null) }
            } else {
                activity.contentResolver.delete(output.uri, null, null)
            }
        }
    }

    private fun publishFullscreenVideoState() {
        val presentation = geckoMediaPresentation
        fullscreenVideoSourceRevision++
        fullscreenVideoState = presentation?.let {
            FullscreenVideoState(
                tabId = it.tabId,
                minimizedByUser = it.minimizedByUser,
                sourceRevision = fullscreenVideoSourceRevision,
                source = FullscreenVideoSource.GeckoView,
                host = FullscreenVideoHost.Overlay,
            )
        }
        scheduleResidentSessionTrim()
    }

    private fun isQuarantinedPopup(tabId: String): Boolean =
        blockedPopupOffer?.popupTabId == tabId

    private fun discardTransientPopup(tabId: String) {
        val index = tabs.indexOfFirst { tab -> tab.id == tabId }
        if (index < 0) {
            transientPopupTabIds.remove(tabId)
            federatedLoginPopupTabIds.remove(tabId)
            federatedLoginCompatibilityTabIds.remove(tabId)
            return
        }
        removeTabResources(tabId)
        tabs.removeAt(index)
    }

    private fun handlePendingPopupNavigation(
        tabId: String,
        view: AndroidBrowserEngineSessionPort,
        targetUrl: String,
    ): PopupNavigationDecision {
        val pending = pendingPopupNavigations[tabId]
            ?: return PopupNavigationDecision.Allow
        if (
            isFederatedLoginCompatibilityEnabled(pending.openerTabId, pending.openerUrl) &&
            FederatedLoginRules.isProviderNavigation(targetUrl)
        ) {
            federatedLoginPopupTabIds += tabId
        }
        val decision = PopupNavigationRules.decide(
            pending = pending,
            targetUrl = targetUrl,
            blockerEnabled = false,
            filterDecision = { _, _ -> PopupFilterDecision.NoMatch },
        )
        if (decision == PopupNavigationDecision.KeepPending) return decision
        if (decision != PopupNavigationDecision.AllowSameSite) {
            pendingPopupNavigations.remove(tabId)
            scheduleResidentSessionTrim()
        }
        if (decision == PopupNavigationDecision.AllowSameSite) {
            recordPopunderChildNavigation(pending, tabId, targetUrl)
            promoteTransientPopup(pending, tabId)
        } else if (decision == PopupNavigationDecision.Allow ||
            decision == PopupNavigationDecision.AllowListed
        ) {
            if (decision == PopupNavigationDecision.AllowListed) {
                pendingPopunderNavigations[pending.openerTabId]
                    ?.takeIf { candidate -> candidate.popupTabId == tabId }
                    ?.let { candidate ->
                        pendingPopunderNavigations.remove(candidate.openerTabId)
                        scheduleResidentSessionTrim()
                    }
            } else {
                recordPopunderChildNavigation(pending, tabId, targetUrl)
            }
            promoteTransientPopup(pending, tabId)
        } else if (decision == PopupNavigationDecision.BlockListed) {
            view.execute(BrowserEngineCommands.stop())
            mainHandler.post {
                if (!destroyed && geckoEngineSessions[tabId] === view) closeTab(tabId)
            }
        } else if (decision == PopupNavigationDecision.BlockCrossSite) {
            view.execute(BrowserEngineCommands.stop())
            transientPopupTabIds += tabId
            offerBlockedPopup(tabId, targetUrl)
            if (selectedTabId == tabId && tabs.any { tab -> tab.id == pending.openerTabId }) {
                selectTab(pending.openerTabId)
            }
            persist()
        }
        return decision
    }

    private fun promoteTransientPopup(pending: PendingPopupNavigation, tabId: String) {
        if (!transientPopupTabIds.remove(tabId)) return
        scheduleResidentSessionTrim()
        captureVisiblePreview(
            tabId = pending.openerTabId,
            onComplete = {
                if (!destroyed &&
                    tabId !in transientPopupTabIds &&
                    !isQuarantinedPopup(tabId) &&
                    tabs.any { tab -> tab.id == tabId }
                ) {
                    leaveSiteCapsule()
                    selectTab(tabId)
                }
            },
            acceptAfterDeparture = true,
        )
        persist()
    }

    private fun offerBlockedPopup(tabId: String, targetUrl: String) {
        blockedPopupOffer?.let { previous ->
            blockedPopupOffer = null
            if (previous.popupTabId != tabId) closeTab(previous.popupTabId)
        }
        blockedPopupSequence++
        blockedPopupOffer = BlockedPopupOffer(
            token = blockedPopupSequence,
            popupTabId = tabId,
            targetUrl = targetUrl,
        )
    }

    private fun recordPopunderChildNavigation(
        popup: PendingPopupNavigation,
        popupTabId: String,
        targetUrl: String,
    ) {
        val candidate = pendingPopunderNavigations[popup.openerTabId]
            ?.takeIf { it.popupTabId == popupTabId }
            ?: return
        evaluatePopunder(
            PopunderNavigationRules.withChildUrl(candidate, targetUrl),
            openerView = geckoEngineSessions[popup.openerTabId],
        )
    }

    private fun handlePendingPopunderOpenerNavigation(
        openerTabId: String,
        openerView: AndroidBrowserEngineSessionPort,
        targetUrl: String,
    ): Boolean {
        val candidate = pendingPopunderNavigations[openerTabId] ?: return false
        return evaluatePopunder(
            PopunderNavigationRules.withOpenerTarget(candidate, targetUrl),
            openerView = openerView,
        )
    }

    private fun evaluatePopunder(
        candidate: PendingPopunderNavigation,
        openerView: AndroidBrowserEngineSessionPort?,
    ): Boolean {
        val decision = PopunderNavigationRules.decide(
            pending = candidate,
            nowMillis = SystemClock.elapsedRealtime(),
            blockerEnabled = false,
            filterDecision = { _, _ -> PopupFilterDecision.NoMatch },
        )
        if (decision == PopunderNavigationDecision.KeepPending) {
            pendingPopunderNavigations[candidate.openerTabId] = candidate
            return false
        }
        if (pendingPopunderNavigations[candidate.openerTabId]?.popupTabId == candidate.popupTabId) {
            pendingPopunderNavigations.remove(candidate.openerTabId)
            scheduleResidentSessionTrim()
        }
        if (decision != PopunderNavigationDecision.Block) return false
        openerView?.execute(BrowserEngineCommands.stop())
        mainHandler.post {
            if (destroyed || tabs.none { tab -> tab.id == candidate.popupTabId }) return@post
            transientPopupTabIds.remove(candidate.popupTabId)
            val child = tabs.first { tab -> tab.id == candidate.popupTabId }
            if (child.profileId != activeProfileId && profilesEnabled) selectProfile(child.profileId)
            selectTab(candidate.popupTabId)
            val opener = tabs.firstOrNull { tab -> tab.id == candidate.openerTabId }
            if (opener?.isPinned == true) {
                openerView?.let { view ->
                    updateTab(opener.id) { tab ->
                        tab.copy(
                            url = candidate.originalOpenerUrl,
                            isLoading = true,
                            progress = 0,
                            error = null,
                        )
                    }
                    loadGeckoWithPrivacy(opener.id, view, candidate.originalOpenerUrl)
                }
            } else {
                closeTab(candidate.openerTabId)
            }
        }
        return true
    }

    private fun routeDownload(request: BrowserDownloadRequest, tabId: String): DownloadActionResult? =
        when (downloadSettings.managerMode) {
            DownloadManagerMode.BuiltIn -> downloadManager.enqueue(request)
            DownloadManagerMode.AskEveryTime -> {
                val apps = externalDownloadManager.discover(request)
                if (apps.isEmpty()) {
                    downloadManager.enqueue(request)
                } else {
                    enqueueDownloadChoice(
                        PendingDownloadChoice(
                            request = request,
                            apps = apps,
                            isIncognito = tabs.firstOrNull { it.id == tabId }?.isIncognito == true,
                        ),
                    )
                    null
                }
            }
            DownloadManagerMode.External -> {
                val app = externalDownloadManager.discover(request).firstOrNull {
                    it.id == downloadSettings.externalManagerId
                }
                if (app == null) {
                    downloadManager.enqueue(request)
                } else {
                    launchExternallyOrFallback(
                        request = request,
                        app = app,
                        isIncognito = tabs.firstOrNull { it.id == tabId }?.isIncognito == true,
                    )
                }
            }
        }

    private fun enqueueDownloadChoice(choice: PendingDownloadChoice) {
        if (pendingDownloadChoice == null) {
            pendingDownloadChoice = choice
        } else {
            queuedDownloadChoices.addLast(choice)
        }
    }

    private fun showNextDownloadChoice() {
        pendingDownloadChoice = queuedDownloadChoices.pollFirst()
    }

    private fun launchExternallyOrFallback(
        request: BrowserDownloadRequest,
        app: ExternalDownloadManagerApp,
        isIncognito: Boolean,
    ): DownloadActionResult = when (
        val result = externalDownloadManager.launch(
            request = request,
            app = app,
            settings = downloadSettings,
            allowSessionData = !isIncognito,
        )
    ) {
        is ExternalDownloadLaunchResult.Launched -> DownloadActionResult.HandedOff(
            fileName = request.fileName,
            appName = result.appName,
        )
        ExternalDownloadLaunchResult.Unavailable -> downloadManager.enqueue(request)
    }

    private fun refreshExternalDownloadManagers() {
        val discovered = externalDownloadManager.discover()
        if (externalDownloadManagers == discovered) return
        externalDownloadManagers.clear()
        externalDownloadManagers += discovered
    }

    private fun showDownloadResult(result: DownloadActionResult) {
        Toast.makeText(
            activity,
            when (result) {
                is DownloadActionResult.Enqueued ->
                    activity.getString(R.string.toast_download_started, result.fileName)
                is DownloadActionResult.HandedOff ->
                    activity.getString(R.string.toast_download_handed_off, result.appName)
                is DownloadActionResult.Failed -> activity.getString(R.string.error_download_start_failed)
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun queueBlockedRequest(
        tabId: String,
        requestUrl: String,
        pageUrl: String?,
        expectedContext: ProtectionRequestContext,
        decision: PrivacyRuleDecisionSummary? = null,
    ) {
        synchronized(privacyEventLock) {
            if (destroyed || protectionRequestContexts[tabId] !== expectedContext) return
            if (decision == null) {
                privacyXRayRepository.record(tabId, requestUrl, pageUrl)
            } else {
                privacyXRayRepository.recordDecision(
                    tabId = tabId,
                    requestUrl = requestUrl,
                    pageUrl = pageUrl,
                    wasBlocked = true,
                    decision = decision,
                )
            }
            pendingBlockedCounts.computeIfAbsent(tabId) { AtomicInteger() }.incrementAndGet()
            pendingPrivacyTabs += tabId
            if (blockerFlushScheduled.compareAndSet(false, true)) {
                mainHandler.postDelayed(blockerCountFlush, BLOCKER_COUNT_FLUSH_DELAY_MS)
            }
        }
    }

    private fun queueCandyRuleDecision(
        tabId: String,
        requestUrl: String,
        pageUrl: String?,
        expectedContext: ProtectionRequestContext,
        decision: CandyRuleDecision,
    ) {
        synchronized(privacyEventLock) {
            if (destroyed || protectionRequestContexts[tabId] !== expectedContext) return
            expectedContext.pendingFilterHits
                .computeIfAbsent(decision.ruleId) { AtomicInteger() }
                .incrementAndGet()
            if (decision.action == CandyDecisionAction.Allow) {
                val requestHost = CandyHostCanonicalizer.webHost(requestUrl).orEmpty()
                val reported = reportedAllowedDecisions.computeIfAbsent(tabId) {
                    ConcurrentHashMap.newKeySet()
                }
                val key = "$requestHost\u0000${decision.ruleId}"
                if (reported.size >= MAX_REPORTED_ALLOW_DECISIONS || !reported.add(key)) {
                    scheduleBlockerFlush()
                    return
                }
            }
            val wasBlocked = decision.action == CandyDecisionAction.Block
            privacyXRayRepository.recordDecision(
                tabId = tabId,
                requestUrl = requestUrl,
                pageUrl = pageUrl,
                wasBlocked = wasBlocked,
                decision = PrivacyRuleDecisionSummary(
                    ruleId = decision.ruleId,
                    label = "${decision.rule.group} · ${decision.rule.id.take(8)}",
                    action = if (wasBlocked) {
                        PrivacyRuleDecisionAction.Block
                    } else {
                        PrivacyRuleDecisionAction.Allow
                    },
                ),
            )
            if (wasBlocked) {
                pendingBlockedCounts.computeIfAbsent(tabId) { AtomicInteger() }.incrementAndGet()
            }
            pendingPrivacyTabs += tabId
            scheduleBlockerFlush()
        }
    }

    private fun scheduleBlockerFlush() {
        if (blockerFlushScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(blockerCountFlush, BLOCKER_COUNT_FLUSH_DELAY_MS)
        }
    }

    private fun updateCandyTrailPage(tabId: String, url: String, title: String) {
        if (isSessionEphemeralTab(tabId)) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (isSyncedProfile(tab.profileId)) return
        if (tabId in suppressedCandyTrailTabIds || pageUrls[tabId] != url) return
        val nowMillis = System.currentTimeMillis()
        val trail = candyTrails[tabId]
        if (trail == null) {
            setCandyTrail(
                tab,
                CandyTrailRules.recordNavigation(
                    current = null,
                    tabId = tabId,
                    url = url,
                    title = title,
                    visitedAt = nowMillis,
                ),
            )
            return
        }
        setCandyTrail(
            tab,
            CandyTrailRules.updateCurrentPage(
                trail = trail,
                url = url,
                title = title,
                visitedAt = nowMillis,
            ),
        )
    }

    private fun setCandyTrail(tab: BrowserTab, trail: CandyTrail) {
        if (isSyncedProfile(tab.profileId)) return
        if (isSessionEphemeralTab(tab.id)) return
        if (candyTrails[tab.id] == trail) return
        candyTrailGenerations[tab.id] = candyTrailGenerations.getOrDefault(tab.id, 0) + 1
        candyTrails[tab.id] = trail
        if (tab.id !in pendingCandyTrailRestoreIds) candyTrailRepository.save(tab, trail)
    }

    private fun recordHistory(tabId: String, url: String, title: String): Boolean {
        if (isSessionEphemeralTab(tabId)) return false
        val tab = tabs.firstOrNull { it.id == tabId }?.takeUnless(BrowserTab::isIncognito)
            ?: return false
        if (isSyncedProfile(tab.profileId)) return false
        val result = historyRepository.record(
            HistoryEntry(
                url = url,
                title = title,
                lastVisitedAt = System.currentTimeMillis(),
                profileId = tab.profileId,
            ),
        )
        val updated = result.history
        if (updated == history) return result.recorded
        history.clear()
        history += updated
        return result.recorded
    }

    internal fun reloadHistory() {
        val restored = historyRepository.snapshot()
        if (restored == history) return
        history.clear()
        history += restored
    }

    private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) tabs[index] = transform(tabs[index])
    }

    private fun captureVisiblePreview(
        tabId: String,
        width: Int = 480,
        onComplete: () -> Unit = {},
        acceptAfterDeparture: Boolean = false,
    ) {
        if (isSessionEphemeralTab(tabId)) {
            onComplete()
            return
        }
        captureVisibleGeckoPreview(
            tabId = tabId,
            width = width,
            onComplete = onComplete,
            acceptAfterDeparture = acceptAfterDeparture,
        )
    }

    private fun captureVisibleGeckoPreview(
        tabId: String,
        width: Int,
        onComplete: () -> Unit,
        acceptAfterDeparture: Boolean,
    ) {
        pendingGeckoPreviewCaptures[tabId]?.let { pending ->
            if (!pending.uiCompleted) {
                pending.completionCallbacks += onComplete
                if (acceptAfterDeparture) pending.acceptAfterDeparture = true
            } else {
                onComplete()
            }
            return
        }
        val tab = tabs.firstOrNull { it.id == tabId }
        val binding = geckoViewBindings.values.firstOrNull { current ->
            current.tabId == tabId && current.view.parent != null
        }
        val view = binding?.view
        if (
            tab == null ||
            tab.isIncognito ||
            tabId != selectedTabId ||
            !isActivityResumed ||
            binding == null ||
            view == null ||
            !view.isAttachedToWindow ||
            !view.isShown ||
            view.width <= 0 ||
            view.height <= 0 ||
            tab.url == BLANK_URL
        ) {
            onComplete()
            return
        }
        val sourceRect = previewSourceRect(view) ?: run {
            onComplete()
            return
        }
        val request = PendingGeckoPreviewCapture(
            tabId = tabId,
            session = binding.session,
            view = view,
            pageUrl = tab.url,
            navigationGeneration = navigationGenerations.getOrDefault(tabId, 0),
            previewEpoch = previewEpoch,
            sourceRect = sourceRect,
            onComplete = onComplete,
            acceptAfterDeparture = acceptAfterDeparture,
        )
        pendingGeckoPreviewCaptures[tabId] = request
        previewCaptureRequestCountForTesting++
        request.timeout = Runnable {
            if (pendingGeckoPreviewCaptures[tabId] !== request) return@Runnable
            pendingGeckoPreviewCaptures.remove(tabId)
            request.expired = true
            request.capture?.cancel()
            completeGeckoPreviewOpening(request)
        }.also { timeout ->
            mainHandler.postDelayed(timeout, GECKO_PREVIEW_CAPTURE_TIMEOUT_MS)
        }
        request.capture = binding.session.capturePreview(
            targetWidthPx = width,
            visibleViewHeightPx = sourceRect.height(),
            maximumTargetHeightPx = width * 3,
            onComplete = captureComplete@{ bitmap ->
                if (pendingGeckoPreviewCaptures[tabId] !== request) {
                    bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                    return@captureComplete
                }
                pendingGeckoPreviewCaptures.remove(tabId)
                request.timeout?.let(mainHandler::removeCallbacks)
                if (
                    bitmap == null ||
                    request.expired ||
                    !isCurrentGeckoPreviewCapture(request)
                ) {
                    bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                    completeGeckoPreviewOpening(request)
                    return@captureComplete
                }
                val candidateQuality = bitmap.previewQuality()
                if (
                    candidateQuality != null &&
                    TabPreviewCaptureRules.shouldStorePixelCopy(candidateQuality)
                ) {
                    previews[request.tabId] = bitmap
                    previewRepository.save(request.tabId, bitmap)
                } else {
                    bitmap.recycle()
                }
                completeGeckoPreviewOpening(request)
            },
        )
        if (request.capture == null && pendingGeckoPreviewCaptures[tabId] === request) {
            pendingGeckoPreviewCaptures.remove(tabId)
            request.timeout?.let(mainHandler::removeCallbacks)
            completeGeckoPreviewOpening(request)
        }
    }

    private fun completeGeckoPreviewOpening(request: PendingGeckoPreviewCapture) {
        if (request.uiCompleted) return
        request.uiCompleted = true
        val callbacks = request.completionCallbacks.toList()
        request.completionCallbacks.clear()
        callbacks.forEach { callback -> callback() }
        scheduleResidentSessionTrim()
    }

    private fun cancelPendingGeckoPreviewCapture(tabId: String) {
        val request = pendingGeckoPreviewCaptures.remove(tabId) ?: return
        request.expired = true
        request.timeout?.let(mainHandler::removeCallbacks)
        request.capture?.cancel()
        completeGeckoPreviewOpening(request)
    }

    private fun isCurrentGeckoPreviewCapture(request: PendingGeckoPreviewCapture): Boolean =
        !destroyed &&
            !isSessionEphemeralTab(request.tabId) &&
            previewEpoch == request.previewEpoch &&
            geckoEngineSessions[request.tabId] === request.session &&
            navigationGenerations.getOrDefault(request.tabId, 0) == request.navigationGeneration &&
            tabs.firstOrNull { tab -> tab.id == request.tabId }?.url == request.pageUrl &&
            (
                request.acceptAfterDeparture ||
                    (
                        isActivityResumed &&
                            selectedTabId == request.tabId &&
                            request.view.isAttachedToWindow &&
                            request.sourceRect == previewSourceRect(request.view)
                        )
                )

    private fun previewSourceRect(view: View): Rect? {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val decorView = activity.window.decorView
        val sourceBottomPx = TabPreviewCaptureRules.sourceBottomPx(
            viewTopPx = location[1],
            viewHeightPx = view.height,
            decorHeightPx = decorView.height,
            contentBottomPx = previewContentBottomInWindowPx ?: decorView.height,
        )
        return Rect(
            location[0].coerceIn(0, decorView.width),
            location[1].coerceIn(0, decorView.height),
            (location[0] + view.width).coerceIn(0, decorView.width),
            sourceBottomPx.coerceIn(0, decorView.height),
        ).takeIf { rect -> rect.width() > 0 && rect.height() > 0 }
    }

    private fun View.hasTransparentViewInHierarchy(): Boolean {
        var current: View? = this
        while (current != null) {
            if (current.alpha <= 0f) return true
            current = current.parent as? View
        }
        return false
    }

    private fun Bitmap.previewQuality(): TabPreviewQuality? {
        if (isRecycled || width <= 0 || height <= 0) return null
        var minimumRed = 255
        var minimumGreen = 255
        var minimumBlue = 255
        var maximumRed = 0
        var maximumGreen = 0
        var maximumBlue = 0
        var nearBlackSamples = 0
        val columns = 12
        val rows = 18
        repeat(columns) { column ->
            val x = ((column + 0.5f) * width / columns).toInt().coerceIn(0, width - 1)
            repeat(rows) { row ->
                val sampledHeight = height * 0.72f
                val y = (height * 0.1f + (row + 0.5f) * sampledHeight / rows)
                    .toInt()
                    .coerceIn(0, height - 1)
                val color = getPixel(x, y)
                if (
                    Color.red(color) <= PREVIEW_NEAR_BLACK_CHANNEL_MAX &&
                    Color.green(color) <= PREVIEW_NEAR_BLACK_CHANNEL_MAX &&
                    Color.blue(color) <= PREVIEW_NEAR_BLACK_CHANNEL_MAX
                ) {
                    nearBlackSamples++
                }
                minimumRed = minOf(minimumRed, Color.red(color))
                minimumGreen = minOf(minimumGreen, Color.green(color))
                minimumBlue = minOf(minimumBlue, Color.blue(color))
                maximumRed = maxOf(maximumRed, Color.red(color))
                maximumGreen = maxOf(maximumGreen, Color.green(color))
                maximumBlue = maxOf(maximumBlue, Color.blue(color))
            }
        }
        return TabPreviewQuality(
            visualRange = maxOf(
                maximumRed - minimumRed,
                maximumGreen - minimumGreen,
                maximumBlue - minimumBlue,
            ),
            nearBlackFraction = nearBlackSamples.toFloat() / (columns * rows),
        )
    }

    private fun restorePersistedPreviews() {
        val restoredTabIds = tabs.asSequence()
            .filterNot(BrowserTab::isIncognito)
            .mapTo(linkedSetOf(), BrowserTab::id)
        val restoreEpoch = previewEpoch
        previewRepository.restore(restoredTabIds) { tabId, bitmap ->
            mainHandler.post {
                if (
                    !destroyed &&
                    previewEpoch == restoreEpoch &&
                    tabs.any { it.id == tabId } &&
                    previews[tabId] == null
                ) {
                    val quality = bitmap.previewQuality()
                    if (quality == null || TabPreviewCaptureRules.isLikelyFailedCapture(quality)) {
                        bitmap.recycle()
                        previewRepository.delete(tabId)
                        return@post
                    }
                    previews[tabId] = bitmap
                } else {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun restorePersistedFavicons() {
        val restoredTabIds = tabs.asSequence()
            .filterNot(BrowserTab::isIncognito)
            .mapTo(linkedSetOf(), BrowserTab::id)
        val restoreEpoch = faviconEpoch
        val restoreGenerations = restoredTabIds.associateWith { tabId ->
            faviconGenerations[tabId] ?: 0
        }
        faviconRepository.restore(restoredTabIds) { tabId, bitmap ->
            mainHandler.post {
                if (
                    !destroyed &&
                    faviconEpoch == restoreEpoch &&
                    faviconGenerations.getOrDefault(tabId, 0) == restoreGenerations[tabId] &&
                    tabs.any { it.id == tabId && !it.isIncognito } &&
                    favicons[tabId] == null
                ) {
                    favicons[tabId] = bitmap
                } else {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun restorePersistedCandyTrails() {
        isCandyTrailRestoreInProgress = true
        pendingCandyTrailRestoreIds += tabs.asSequence()
            .filterNot(BrowserTab::isIncognito)
            .map(BrowserTab::id)
        val restoreEpoch = candyTrailEpoch
        val restoreGenerations = tabs.associate { tab ->
            tab.id to candyTrailGenerations.getOrDefault(tab.id, 0)
        }
        candyTrailRepository.restore(
            tabs = tabs.toList(),
            retainedTabIds = snoozedTabs.mapTo(linkedSetOf()) { it.tab.id },
            onLoaded = { tabId, restoredTrail -> mainHandler.post {
                val tab = tabs.firstOrNull { it.id == tabId && !it.isIncognito }
                val runtimeTrail = candyTrails[tabId]
                val generationUnchanged = candyTrailGenerations.getOrDefault(tabId, 0) ==
                    restoreGenerations[tabId]
                if (
                    !destroyed &&
                    candyTrailEpoch == restoreEpoch &&
                    tab != null
                ) {
                    val safeRestoredTrail = candyTrailRedactionsDuringRestore
                        .asSequence()
                        .filter { redaction -> tabId in redaction.tabIds }
                        .fold(restoredTrail) { current, redaction ->
                            CandyTrailRules.removeVisitedRange(
                                trail = current,
                                sinceInclusiveMillis = redaction.sinceInclusiveMillis,
                                untilExclusiveMillis = redaction.untilExclusiveMillis,
                            )
                        }
                    val runtimeBinding = candyTrailHistoryBindings[tabId]
                    val mergeResult = if (!generationUnchanged && runtimeTrail != null) {
                        CandyTrailRules.mergeRestoredWithRuntime(safeRestoredTrail, runtimeTrail)
                    } else {
                        null
                    }
                    val mergedTrail = mergeResult?.trail ?: safeRestoredTrail
                    val reconciledTrail = CandyTrailForkRules.reconcile(
                        trail = mergedTrail,
                        originTab = tab.toCandyTrailForkTab(),
                        openTabs = tabs.map(BrowserTab::toCandyTrailForkTab),
                        reconciledAt = System.currentTimeMillis(),
                    )
                    candyTrails[tabId] = reconciledTrail
                    if (mergeResult != null && runtimeBinding != null) {
                        candyTrailHistoryBindings[tabId] = CandyTrailHistoryReconciler.remapNodeIds(
                            runtimeBinding,
                            mergeResult.runtimeNodeIds,
                        )
                    } else {
                        candyTrailHistoryBindings.remove(tabId)
                    }
                    candyTrailGenerations[tabId] =
                        candyTrailGenerations.getOrDefault(tabId, 0) + 1
                    pendingCandyTrailRestoreIds.remove(tabId)
                    candyTrailRepository.save(tab, reconciledTrail)
                }
            } },
            onComplete = { mainHandler.post {
                isCandyTrailRestoreInProgress = false
                candyTrailRedactionsDuringRestore.clear()
                val unresolvedIds = pendingCandyTrailRestoreIds.toList()
                pendingCandyTrailRestoreIds.clear()
                unresolvedIds.forEach { tabId ->
                    val tab = tabs.firstOrNull { it.id == tabId && !it.isIncognito }
                    val trail = candyTrails[tabId]
                    if (tab != null && trail != null) candyTrailRepository.save(tab, trail)
                }
            } },
        )
    }

    private fun storeFavicon(tabId: String, bitmap: Bitmap) {
        val tab = tabs.firstOrNull { it.id == tabId }
        if (bitmap.isRecycled || tab == null) return
        favicons[tabId] = bitmap
        if (!tab.isIncognito && !isSessionEphemeralTab(tabId)) {
            faviconRepository.save(tabId, bitmap)
        }
    }

    private fun invalidateFavicon(tabId: String) {
        faviconGenerations[tabId] = faviconGenerations.getOrDefault(tabId, 0) + 1
        favicons.remove(tabId)
        faviconRepository.delete(tabId)
    }

    private val blockerCountFlush = object : Runnable {
        override fun run() {
            pendingBlockedCounts.forEach { (tabId, count) ->
                val delta = count.getAndSet(0)
                if (delta > 0 && tabs.any { it.id == tabId }) {
                    updateTab(tabId) { it.copy(blockedCount = it.blockedCount + delta) }
                    privacySnapshots[tabId] = privacyXRayRepository.snapshot(tabId)
                } else if (tabs.none { it.id == tabId }) {
                    privacyXRayRepository.remove(tabId)
                }
            }
            pendingBlockedCounts.entries.removeAll { (tabId, count) ->
                count.get() == 0 && tabs.none { it.id == tabId }
            }
            pendingPrivacyTabs.toList().forEach { tabId ->
                if (tabs.any { it.id == tabId }) {
                    privacySnapshots[tabId] = privacyXRayRepository.snapshot(tabId)
                }
                pendingPrivacyTabs.remove(tabId)
            }
            protectionRequestContexts.values.forEach(::flushPendingFilterHits)
            blockerFlushScheduled.set(false)
            if (!destroyed &&
                (
                    pendingBlockedCounts.values.any { it.get() > 0 } ||
                        protectionRequestContexts.values.any { context ->
                            context.pendingFilterHits.values.any { it.get() > 0 }
                        } ||
                        pendingPrivacyTabs.isNotEmpty()
                    ) &&
                blockerFlushScheduled.compareAndSet(false, true)
            ) {
                mainHandler.postDelayed(this, BLOCKER_COUNT_FLUSH_DELAY_MS)
            }
        }
    }

    private fun flushPendingFilterHits(context: ProtectionRequestContext) {
        context.pendingFilterHits.forEach { (ruleId, count) ->
            val delta = count.getAndSet(0)
            if (delta > 0) {
                val index = filterRules.indexOfFirst { it.id == ruleId }
                if (context.isIncognito) {
                    incognitoRuleHits[ruleId] = (
                        incognitoRuleHits.getOrDefault(ruleId, 0).toLong() + delta
                        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                } else if (index >= 0) {
                    val current = filterRules[index]
                    filterRules[index] = current.copy(
                        hitCount = (current.hitCount.toLong() + delta)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt(),
                    )
                }
            }
        }
        context.pendingFilterHits.entries.removeAll { (_, count) -> count.get() == 0 }
    }

    private fun persist() {
        val reconciledStacks = TabStackRules.sanitized(tabStacks, tabs)
        if (reconciledStacks != tabStacks) tabStacks.replaceWith(reconciledStacks)
        val persistentTabs = persistableTabs(tabs)
        store.saveTabs(persistentTabs, selectedTabId)
        store.saveTabStacks(tabStacks, persistentTabs)
        val persistentProfiles = localProfiles
        val persistentActiveProfileId = activeProfileId
            .takeIf { id -> persistentProfiles.any { it.id == id } }
            ?: persistentProfiles.first().id
        store.saveProfiles(persistentProfiles, persistentActiveProfileId)
        savePersistentFilterRules()
    }

    private fun <T> MutableList<T>.replaceWith(values: List<T>) {
        clear()
        addAll(values)
    }

    private fun persistableTabs(source: Collection<BrowserTab>): List<BrowserTab> =
        source.filterNot { tab ->
            isSessionEphemeralTab(tab.id) || isSyncedProfile(tab.profileId)
        }

    private fun isSessionEphemeralTab(tabId: String): Boolean =
        tabId in transientPopupTabIds || tabId in federatedLoginPopupTabIds

    private fun activeFederatedLoginFlowTabIds(): Set<String> = buildSet {
        federatedLoginPopupTabIds.forEach { popupTabId ->
            add(popupTabId)
            tabs.firstOrNull { tab -> tab.id == popupTabId }
                ?.openerTabId
                ?.let(::add)
        }
    }

    private fun syncProtectedRuntimeTabIds(): Set<String> = buildSet {
        addAll(activeFederatedLoginFlowTabIds())
        addAll(transientPopupTabIds)
        pendingPopupNavigations.forEach { (popupTabId, pending) ->
            add(popupTabId)
            add(pending.openerTabId)
        }
    }

    private fun savePersistentFilterRules() {
        candyRuleRepository.save(filterRules.filterNot { it.id in ephemeralRuleIds })
    }

    private fun applySyncRepositoryState(state: SyncRepositoryState) {
        syncState = state
        val currentDeviceId = state.currentDeviceId
        val settings = state.settings
        val boundProfileId = settings?.localProfileId
            ?.takeIf { candidate -> localProfiles.any { it.id == candidate } }
            ?: activeProfileId.takeIf { candidate -> localProfiles.any { it.id == candidate } }
            ?: localProfiles.firstOrNull()?.id
        if (
            currentDeviceId != null &&
            settings != null &&
            settings.localProfileId == null &&
            boundProfileId != null
        ) {
            syncRepository.configure(settings.copy(localProfileId = boundProfileId))
        }
        profiles.indices.forEach { index ->
            val profile = profiles[index]
            if (!profile.isSynced) {
                val linkedDeviceId = currentDeviceId.takeIf { profile.id == boundProfileId }
                if (profile.linkedSyncDeviceId != linkedDeviceId) {
                    profiles[index] = profile.copy(linkedSyncDeviceId = linkedDeviceId)
                }
            }
        }
        val remoteById = state.profiles
            .filterNot { it.deviceId == currentDeviceId }
            .associateBy(SyncProfile::deviceId)
        locallyPendingSyncCandyIds.removeAll { candyId ->
            state.profiles.any { profile -> profile.tabs.any { it.candyId == candyId } }
        }

        val removedProfiles = profiles.filter { profile ->
            profile.isSynced && profile.syncedDeviceId !in remoteById
        }
        if (removedProfiles.any { it.id == activeProfileId }) {
            selectProfile(localProfiles.first().id)
        }
        val removedProfileIds = removedProfiles.mapTo(hashSetOf(), BrowserProfile::id)
        tabs.filter { it.profileId in removedProfileIds }
            .map(BrowserTab::id)
            .forEach(::removeTabResources)
        tabs.removeAll { it.profileId in removedProfileIds }
        profiles.removeAll { it.id in removedProfileIds }

        val ownProfile = state.profiles.firstOrNull { it.deviceId == currentDeviceId }
        if (ownProfile != null && boundProfileId != null) {
            publishUntrackedLinkedTabs(boundProfileId, ownProfile)
            val linkedCapacity = (
                MAX_TABS - tabs.count { it.profileId != boundProfileId }
                ).coerceAtLeast(0)
            val reconciliation = SyncedProfileRuntimeRules.reconcileLinkedProfile(
                profile = ownProfile,
                localProfileId = boundProfileId,
                existingTabs = tabs,
                nowMillis = System.currentTimeMillis(),
                maxTabs = linkedCapacity,
                locallyPendingCandyIds = locallyPendingSyncCandyIds,
                protectedRuntimeTabIds = syncProtectedRuntimeTabIds(),
            )
            reconciliation.removedRuntimeTabIds.forEach(::removeTabResources)
            replaceProfileTabs(boundProfileId, reconciliation.tabs)
            reconciliation.hydrations.forEach(::prepareSyncedTabHydration)
            reconciliation.navigations.forEach(::applySyncedTabNavigation)
        }

        var remainingCapacity = (MAX_TABS - tabs.count { !isSyncedProfile(it.profileId) })
            .coerceAtLeast(0)
        state.profiles.filterNot { it.deviceId == currentDeviceId }.forEach { remote ->
            val profileId = SyncedProfileRuntimeRules.profileId(remote.deviceId)
            val existingProfile = profiles.firstOrNull { it.id == profileId }
            val reconciliation = SyncedProfileRuntimeRules.reconcile(
                profile = remote,
                existingTabs = tabs,
                nowMillis = System.currentTimeMillis(),
                maxTabs = remainingCapacity,
                locallyPendingCandyIds = locallyPendingSyncCandyIds,
                protectedRuntimeTabIds = syncProtectedRuntimeTabIds(),
            )
            remainingCapacity = (remainingCapacity - reconciliation.tabs.size).coerceAtLeast(0)
            val selectedRuntimeTabId = existingProfile?.selectedTabId
                ?.takeIf { id -> reconciliation.tabs.any { it.id == id } }
                ?: remote.tabs.firstOrNull(SyncTab::active)?.candyId?.let { candyId ->
                    reconciliation.tabs.firstOrNull { it.syncCandyId == candyId }?.id
                }
                ?: reconciliation.tabs.firstOrNull()?.id
            val iconEmoji = syncIconCatalog.icons
                .firstOrNull { it.id == remote.icon.catalogId }
                ?.emoji
                ?: DEFAULT_PROFILE_EMOJI
            val runtimeProfile = SyncedProfileRuntimeRules.runtimeProfile(
                profile = remote,
                iconEmoji = iconEmoji,
                selectedTabId = selectedRuntimeTabId,
            )
            val profileIndex = profiles.indexOfFirst { it.id == profileId }
            if (profileIndex >= 0) profiles[profileIndex] = runtimeProfile else profiles += runtimeProfile

            reconciliation.removedRuntimeTabIds.forEach(::removeTabResources)
            replaceProfileTabs(profileId, reconciliation.tabs)
            reconciliation.hydrations.forEach(::prepareSyncedTabHydration)
            reconciliation.navigations.forEach(::applySyncedTabNavigation)
        }

        if (activeTabs.none { it.id == selectedTabId }) {
            val replacement = profiles.first { it.id == activeProfileId }.selectedTabId
                ?.let { id -> activeTabs.firstOrNull { it.id == id } }
                ?: activeTabs.firstOrNull()
                ?: if (tabs.size < MAX_TABS) newTabState().also(tabs::add) else null
            if (replacement != null) {
                updateSelectedTabId(replacement.id)
                rememberSelectedTab(activeProfileId, replacement.id)
            } else {
                selectProfile(localProfiles.first().id)
            }
        }
        persist()
    }

    private fun publishUntrackedLinkedTabs(
        profileId: String,
        remote: SyncProfile,
    ) {
        val assignedCandyIds = mutableSetOf<String>()
        tabs.indices.forEach { index ->
            val tab = tabs[index]
            if (
                tab.profileId == profileId &&
                !tab.isIncognito &&
                !isSessionEphemeralTab(tab.id) &&
                tab.syncCandyId == null &&
                (tab.url == BLANK_URL || BrowserUriPolicy.normalizeHttpUrl(tab.url) != null)
            ) {
                val candyId = UUID.randomUUID().toString()
                tabs[index] = tab.copy(syncCandyId = candyId)
                assignedCandyIds += candyId
            }
        }
        tabs.filter { tab ->
            tab.profileId == profileId && !tab.isIncognito && !isSessionEphemeralTab(tab.id)
        }
            .forEach { tab ->
                val candyId = tab.syncCandyId ?: return@forEach
                if (candyId !in assignedCandyIds) return@forEach
                val tabIndex = tabs.filter { candidate ->
                    candidate.profileId == profileId &&
                        !candidate.isIncognito &&
                        !isSessionEphemeralTab(candidate.id)
                }
                    .indexOfFirst { it.id == tab.id }
                val outbound = SyncedProfileRuntimeRules.outboundTab(
                    tab = tab,
                    index = tabIndex,
                    selectedTabId = selectedTabId,
                ) ?: return@forEach
                locallyPendingSyncCandyIds += candyId
                mutateSync(
                    SyncPendingMutation.Open(
                        mutationId = UUID.randomUUID().toString(),
                        targetDeviceId = remote.deviceId,
                        tab = outbound,
                    ),
                )
            }
        if (assignedCandyIds.isNotEmpty()) persist()
    }

    private fun markSyncedTabPending(tab: BrowserTab) {
        if (isSessionEphemeralTab(tab.id)) return
        tab.syncCandyId?.let(locallyPendingSyncCandyIds::add)
    }

    private fun prepareSyncedTabHydration(navigation: SyncedTabNavigation): String? {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(navigation.url) ?: return null
        if (tabs.none { tab -> tab.id == navigation.runtimeTabId }) return null
        pendingSyncNavigationRunnables.remove(navigation.runtimeTabId)
            ?.let(mainHandler::removeCallbacks)
        remoteSyncNavigationUrls.put(navigation.runtimeTabId, safeUrl)
            ?.takeIf { previousUrl -> previousUrl != safeUrl }
            ?.let { previousUrl ->
                supersededRemoteSyncNavigationUrls
                    .getOrPut(navigation.runtimeTabId) { mutableSetOf() }
                    .add(previousUrl)
            }
        return safeUrl
    }

    private fun ignoreSupersededRemoteNavigationEvent(event: BrowserEngineEvent): Boolean {
        val safeUrl = event.address?.let(BrowserUriPolicy::normalizeHttpUrl) ?: return false
        if (safeUrl !in supersededRemoteSyncNavigationUrls[event.tabId].orEmpty()) return false
        if (
            event.type == BrowserEngineEventType.NavigationCommitted ||
            event.type == BrowserEngineEventType.NavigationFailed ||
            event.isLoading == false
        ) {
            val expectedUrl = remoteSyncNavigationUrls[event.tabId] ?: return true
            geckoEngineSessions[event.tabId]?.let { session ->
                loadGeckoWithPrivacy(event.tabId, session, expectedUrl)
            }
        }
        return true
    }

    private fun clearRemoteSyncNavigationTracking(tabId: String) {
        remoteSyncNavigationUrls.remove(tabId)
        supersededRemoteSyncNavigationUrls.remove(tabId)
    }

    private fun applySyncedTabNavigation(navigation: SyncedTabNavigation) {
        val safeUrl = prepareSyncedTabHydration(navigation) ?: return
        geckoEngineSessions[navigation.runtimeTabId]?.let { session ->
            loadGeckoWithPrivacy(navigation.runtimeTabId, session, safeUrl)
        }
    }

    private fun scheduleSyncedTabNavigation(
        tabId: String,
        remoteNavigationFinished: Boolean = false,
    ) {
        if (isSessionEphemeralTab(tabId)) {
            pendingSyncNavigationRunnables.remove(tabId)?.let(mainHandler::removeCallbacks)
            return
        }
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (!isSyncTargetProfile(tab.profileId) || tab.isIncognito) return
        remoteSyncNavigationUrls[tabId]?.let { expectedUrl ->
            if (BrowserUriPolicy.normalizeHttpUrl(tab.url) == expectedUrl) {
                if (remoteNavigationFinished) clearRemoteSyncNavigationTracking(tabId)
                return
            }
            clearRemoteSyncNavigationTracking(tabId)
        }
        pendingSyncNavigationRunnables.remove(tabId)?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            pendingSyncNavigationRunnables.remove(tabId)
            enqueueSyncedTab(tabId)
        }
        pendingSyncNavigationRunnables[tabId] = runnable
        mainHandler.postDelayed(runnable, SYNC_NAVIGATION_DEBOUNCE_MILLIS)
    }

    private fun enqueueSyncedTab(tabId: String) {
        if (isSessionEphemeralTab(tabId)) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val targetDeviceId = syncTargetDeviceId(tab.profileId) ?: return
        val tabIndex = tabs.filter { candidate ->
            candidate.profileId == tab.profileId && !isSessionEphemeralTab(candidate.id)
        }.indexOfFirst { it.id == tabId }
        if (tabIndex < 0) return
        val outbound = SyncedProfileRuntimeRules.outboundTab(tab, tabIndex, selectedTabId) ?: return
        val remote = syncState.profiles.firstOrNull { it.deviceId == targetDeviceId }
        val remoteTab = remote?.tabs?.firstOrNull { it.candyId == outbound.candyId }
        if (remoteTab?.url == outbound.url && remoteTab.title == outbound.title) return
        val mutationId = UUID.randomUUID().toString()
        val mutation = if (remoteTab != null) {
            SyncPendingMutation.Navigate(
                mutationId = mutationId,
                targetDeviceId = targetDeviceId,
                candyId = outbound.candyId,
                title = outbound.title,
                url = outbound.url,
            )
        } else {
            locallyPendingSyncCandyIds += outbound.candyId
            SyncPendingMutation.Open(
                mutationId = mutationId,
                targetDeviceId = targetDeviceId,
                tab = outbound,
            )
        }
        mutateSync(mutation)
    }

    private fun enqueueSyncedTabClose(tab: BrowserTab) {
        if (isSessionEphemeralTab(tab.id)) return
        val candyId = tab.syncCandyId ?: return
        val targetDeviceId = syncTargetDeviceId(tab.profileId) ?: return
        pendingSyncNavigationRunnables.remove(tab.id)?.let(mainHandler::removeCallbacks)
        locallyPendingSyncCandyIds.remove(candyId)
        mutateSync(
            SyncPendingMutation.Close(
                mutationId = UUID.randomUUID().toString(),
                targetDeviceId = targetDeviceId,
                candyId = candyId,
            ),
        )
    }

    private fun enqueueSyncedTabOrder(profileId: String) {
        val targetDeviceId = syncTargetDeviceId(profileId) ?: return
        val orderedCandyIds = tabs.filter { tab ->
            tab.profileId == profileId && !isSessionEphemeralTab(tab.id)
        }
            .mapNotNull(BrowserTab::syncCandyId)
        mutateSync(
            SyncPendingMutation.Reorder(
                mutationId = UUID.randomUUID().toString(),
                targetDeviceId = targetDeviceId,
                orderedCandyIds = orderedCandyIds,
            ),
        )
    }

    private fun enqueueSyncedTabPinned(tabId: String, pinned: Boolean) {
        if (isSessionEphemeralTab(tabId)) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val candyId = tab.syncCandyId ?: return
        val targetDeviceId = syncTargetDeviceId(tab.profileId) ?: return
        mutateSync(
            SyncPendingMutation.SetPinned(
                mutationId = UUID.randomUUID().toString(),
                targetDeviceId = targetDeviceId,
                candyId = candyId,
                pinned = pinned,
            ),
        )
    }

    private fun mutateSync(mutation: SyncPendingMutation) {
        syncMutationObserverForTesting?.invoke(mutation)
        syncRepository.mutate(mutation)
    }

    private fun rebuildCandyMatcher() {
        val snapshots = CandyMatcherSnapshots.compile(filterRules.toList(), ephemeralRuleIds)
        matcherSnapshot.set(snapshots.persistent)
        incognitoMatcherSnapshot.set(snapshots.incognito)
    }

    private fun matcherFor(isIncognito: Boolean): CandyMatcherSnapshot =
        if (isIncognito) incognitoMatcherSnapshot.get() else matcherSnapshot.get()

    private fun onFilterRulesChanged(persist: Boolean) {
        rebuildCandyMatcher()
        if (persist) savePersistentFilterRules()
    }

    private fun newTabState(
        url: String = BLANK_URL,
        nowMillis: Long = System.currentTimeMillis(),
        isIncognito: Boolean = false,
        openerTabId: String? = null,
        profileId: String = activeProfileId,
    ) = BrowserTab(
        id = UUID.randomUUID().toString(),
        lastAccessedAt = nowMillis,
        openerTabId = openerTabId,
        profileId = profileId,
        isIncognito = isIncognito && isProfileIsolationSupported && !isSyncedProfile(profileId),
        url = url,
        title = if (url == BLANK_URL) "" else AddressResolver.displayText(url),
        isLoading = url != BLANK_URL,
        syncCandyId = UUID.randomUUID().toString().takeIf {
            !isIncognito && isSyncTargetProfile(profileId)
        },
    )

    private fun touchTab(tabId: String, nowMillis: Long) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) tabs[index] = tabs[index].copy(lastAccessedAt = nowMillis)
    }

    private fun rememberSelectedTab(profileId: String, tabId: String) {
        if (isSessionEphemeralTab(tabId)) return
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index >= 0 && profiles[index].selectedTabId != tabId) {
            profiles[index] = profiles[index].copy(selectedTabId = tabId)
        }
    }

    private fun replaceProfileTabs(profileId: String, orderedTabs: List<BrowserTab>) {
        val insertionIndex = tabs.indexOfFirst { it.profileId == profileId }
            .takeIf { it >= 0 }
            ?: tabs.size
        tabs.removeAll { it.profileId == profileId }
        tabs.addAll(insertionIndex.coerceAtMost(tabs.size), orderedTabs)
    }

    private fun pruneStaleTabs(
        nowMillis: Long = System.currentTimeMillis(),
        persistChanges: Boolean = true,
    ): Boolean = removeTabs(
        tabIds = staleTabIds(nowMillis),
        nowMillis = nowMillis,
        persistChanges = persistChanges,
    )

    private fun staleTabIds(nowMillis: Long): Set<String> =
        TabRetentionRules.expiredTabIds(
            tabs = tabs,
            selectedTabId = selectedTabId,
            lifetime = inactiveTabLifetime,
            nowMillis = nowMillis,
        ) - activeFederatedLoginFlowTabIds()

    private fun closeTabsOnBackground(
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val closeIds = TabRetentionRules.tabIdsToCloseOnBackground(
            tabs = tabs,
            lifetime = inactiveTabLifetime,
        ) - activeFederatedLoginFlowTabIds()
        return removeTabs(
            tabIds = closeIds,
            nowMillis = nowMillis,
            persistChanges = true,
        )
    }

    private fun removeTabs(
        tabIds: Set<String>,
        nowMillis: Long,
        persistChanges: Boolean,
    ): Boolean {
        if (tabIds.isEmpty()) return false
        if (activeCapsuleTabId in tabIds) leaveSiteCapsule()
        val removedIncognitoTab = tabs.any { it.id in tabIds && it.isIncognito }
        if (
            removedIncognitoTab &&
            tabs.none { it.isIncognito && it.id !in tabIds }
        ) {
            prepareIncognitoProfileForRemoval()
        }
        tabs.filter { it.id in tabIds }.forEach(::enqueueSyncedTabClose)
        tabIds.forEach(::removeTabResources)
        tabs.removeAll { it.id in tabIds }
        reconcileCandyTrailForks(nowMillis)
        if (removedIncognitoTab && tabs.none(BrowserTab::isIncognito)) {
            clearIncognitoProfile()
        }
        if (activeTabs.isEmpty()) {
            val replacement = newTabState(nowMillis = nowMillis)
            tabs += replacement
            markSyncedTabPending(replacement)
            updateSelectedTabId(activeTabs.first().id)
        } else if (tabs.none { it.id == selectedTabId }) {
            updateSelectedTabId(activeTabs.maxByOrNull(BrowserTab::lastAccessedAt)!!.id)
        }
        profiles.indices.forEach { index ->
            val profile = profiles[index]
            val selection = profile.selectedTabId
                ?.takeIf { selectedId ->
                    tabs.any { tab -> tab.id == selectedId && tab.profileId == profile.id }
                }
                ?: tabs.asSequence()
                    .filter { tab -> tab.profileId == profile.id }
                    .maxByOrNull(BrowserTab::lastAccessedAt)
                    ?.id
            if (selection != profile.selectedTabId) {
                profiles[index] = profile.copy(selectedTabId = selection)
            }
        }
        rememberSelectedTab(activeProfileId, selectedTabId)
        if (persistChanges) persist()
        return true
    }

    private fun removeTabResources(
        tabId: String,
        preserveFaviconGeneration: Boolean = false,
    ) {
        closeGeckoEngineSession(tabId)
        geckoSessionStateStore.delete(tabId)
        pendingSyncNavigationRunnables.remove(tabId)?.let(mainHandler::removeCallbacks)
        clearRemoteSyncNavigationTracking(tabId)
        tabs.firstOrNull { it.id == tabId }?.syncCandyId?.let(locallyPendingSyncCandyIds::remove)
        clearPermissionActivity(tabId)
        clearPrivacyDataForTab(tabId)
        residentSessionAccessOrder.remove(tabId)
        navigationGenerations.remove(tabId)
        nativeSafeAreaFallbackTabs.remove(tabId)
        nativeSafeAreaFallbackReloads.remove(tabId)
        clearExternalNavigationAuthorization(tabId)
        pageUrls.remove(tabId)
        extensionTabMuteOverrides.remove(tabId)
        bottomBarCompactStates.remove(tabId)
        browserChromeScrollStates.remove(tabId)
        candyTrailHistoryBindings.remove(tabId)
        pendingCandyTrailTargets.remove(tabId)
        pendingPopupNavigations.remove(tabId)
        pendingPopunderNavigations.entries.removeAll { (_, pending) ->
            pending.openerTabId == tabId || pending.popupTabId == tabId
        }
        transientPopupTabIds.remove(tabId)
        federatedLoginPopupTabIds.remove(tabId)
        federatedLoginCompatibilityTabIds.remove(tabId)
        if (blockedPopupOffer?.popupTabId == tabId) blockedPopupOffer = null
        if (federatedLoginOffer?.tabId == tabId) federatedLoginOffer = null
        if (captchaCompatibilityOffer?.tabId == tabId) captchaCompatibilityOffer = null
        pendingCandyTrailRestoreIds.remove(tabId)
        suppressedCandyTrailTabIds.remove(tabId)
        candyTrails.remove(tabId)
        candyTrailGenerations.remove(tabId)
        candyTrailRepository.delete(tabId)
        previews.remove(tabId)
        previewRepository.delete(tabId)
        invalidateFavicon(tabId)
        if (!preserveFaviconGeneration) faviconGenerations.remove(tabId)
    }

    private fun removeTabRuntimeForSnooze(tab: BrowserTab) {
        candyTrails[tab.id]?.let { trail -> candyTrailRepository.save(tab, trail) }
        closeGeckoEngineSession(tab.id)

        clearPrivacyDataForTab(tab.id)
        residentSessionAccessOrder.remove(tab.id)
        navigationGenerations.remove(tab.id)
        nativeSafeAreaFallbackTabs.remove(tab.id)
        nativeSafeAreaFallbackReloads.remove(tab.id)
        pageUrls.remove(tab.id)
        bottomBarCompactStates.remove(tab.id)
        browserChromeScrollStates.remove(tab.id)
        candyTrailHistoryBindings.remove(tab.id)
        pendingCandyTrailTargets.remove(tab.id)
        pendingPopupNavigations.remove(tab.id)
        pendingPopunderNavigations.entries.removeAll { (_, pending) ->
            pending.openerTabId == tab.id || pending.popupTabId == tab.id
        }
        transientPopupTabIds.remove(tab.id)
        federatedLoginPopupTabIds.remove(tab.id)
        federatedLoginCompatibilityTabIds.remove(tab.id)
        if (blockedPopupOffer?.popupTabId == tab.id) blockedPopupOffer = null
        if (federatedLoginOffer?.tabId == tab.id) federatedLoginOffer = null
        if (captchaCompatibilityOffer?.tabId == tab.id) captchaCompatibilityOffer = null
        pendingCandyTrailRestoreIds.remove(tab.id)
        suppressedCandyTrailTabIds.remove(tab.id)
        previews.remove(tab.id)
        previewRepository.delete(tab.id)
        invalidateFavicon(tab.id)
        faviconGenerations.remove(tab.id)
    }

    private fun restoreDueSnoozedTabs(nowMillis: Long): Int {
        val result = SnoozeRestoreRules.restoreDue(
            tabs = tabs,
            snoozedTabs = snoozedTabs,
            profiles = profiles,
            activeProfileId = activeProfileId,
            nowMillis = nowMillis,
            maxTabs = MAX_TABS,
        )
        if (result.completedTabIds.isEmpty()) {
            snoozeScheduler.schedule(snoozedTabs, nowMillis)
            return 0
        }
        val oldIds = tabs.mapTo(hashSetOf(), BrowserTab::id)
        val validSelection = selectedTabId.takeIf { selected ->
            result.tabs.any { it.id == selected }
        } ?: result.tabs.firstOrNull { it.profileId == activeProfileId }?.id
            ?: result.tabs.first().id
        val remaining = snoozedTabs.filterNot { it.tab.id in result.completedTabIds }
        if (!store.saveTabsAndSnoozedImmediately(
                tabs = persistableTabs(result.tabs),
                selectedTabId = validSelection,
                snoozedTabs = remaining,
            )
        ) return 0
        tabs.clear()
        tabs += result.tabs
        updateSelectedTabId(validSelection)
        snoozedTabs.clear()
        snoozedTabs += remaining
        reconcileCandyTrailForks(nowMillis)
        result.tabs.asSequence()
            .filter { it.id !in oldIds }
            .forEach { restoredTab ->
                restoreSnoozedCandyTrail(restoredTab)
                markSyncedTabPending(restoredTab)
                enqueueSyncedTab(restoredTab.id)
            }
        persist()
        snoozeScheduler.schedule(remaining, nowMillis)
        val restoredTabs = result.tabs.filter { it.id in result.restoredTabIds }
        SnoozeWakeNotifier(activity).notifyRestored(
            restoredTabs.filter { profilesEnabled || it.profileId == profiles.first().id },
        )
        return restoredTabs.size
    }

    private fun restoreSnoozedCandyTrail(tab: BrowserTab) {
        if (tab.isIncognito || candyTrails.containsKey(tab.id)) return
        val restoreEpoch = candyTrailEpoch
        candyTrailRepository.restoreTab(tab.id) { trail ->
            mainHandler.post {
                val activeTab = tabs.firstOrNull { it.id == tab.id && !it.isIncognito }
                if (!destroyed && candyTrailEpoch == restoreEpoch && activeTab != null &&
                    !candyTrails.containsKey(tab.id)
                ) {
                    candyTrails[tab.id] = CandyTrailForkRules.reconcile(
                        trail = trail,
                        originTab = activeTab.toCandyTrailForkTab(),
                        openTabs = (tabs + snoozedTabs.map(SnoozedTab::tab))
                            .map(BrowserTab::toCandyTrailForkTab),
                        reconciledAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    private fun reconcileCandyTrailForks(reconciledAt: Long) {
        val openTabs = (tabs + snoozedTabs.map(SnoozedTab::tab))
            .map(BrowserTab::toCandyTrailForkTab)
        candyTrails.toMap().forEach { (originTabId, trail) ->
            val originTab = tabs.firstOrNull { it.id == originTabId }
            if (originTab == null) return@forEach
            val reconciled = CandyTrailForkRules.reconcile(
                trail = trail,
                originTab = originTab.toCandyTrailForkTab(),
                openTabs = openTabs,
                reconciledAt = reconciledAt,
            )
            setCandyTrail(originTab, reconciled)
        }
    }

    private fun showTabLimitReached() {
        Toast.makeText(
            activity,
            activity.getString(R.string.toast_tab_limit_reached, MAX_TABS),
            Toast.LENGTH_SHORT,
        ).show()
    }


    private fun referrerFor(tabId: String): String? = pageUrls[tabId]
        ?: tabs.firstOrNull { it.id == tabId }?.url

    private fun clearPrivacyDataForTab(
        tabId: String,
        clearTemporarySiteOverrides: Boolean = true,
    ) {
        synchronized(privacyEventLock) {
            protectionRequestContexts.remove(tabId)
            pendingBlockedCounts.remove(tabId)
            privacyXRayRepository.remove(tabId)
        }
        privacySnapshots.remove(tabId)
        reportedAllowedDecisions.remove(tabId)
        pendingConsentCssUrls.remove(tabId)
        temporarySiteExceptions.remove(tabId)
        if (clearTemporarySiteOverrides) temporarySitePrivacyOverrides.remove(tabId)
        federatedLoginOfferKeys.remove(tabId)
        captchaCompatibilityOfferKeys.remove(tabId)
        updateTab(tabId) { tab ->
            if (tab.blockedCount == 0) tab else tab.copy(blockedCount = 0)
        }
        siteExceptionRevision++
    }

    private fun detectFederatedLoginRequest(
        tabId: String,
        requestUrl: String,
        requestContext: ProtectionRequestContext,
    ) {
        if (!workerSettings.blockThirdPartyCookies) return
        val pageHost = requestContext.pageHost ?: return
        val provider = FederatedLoginRules.providerForSubresource(
            requestUrl = requestUrl,
            pageUrl = "https://$pageHost/",
        ) ?: return
        mainHandler.post {
            if (destroyed || selectedTabId != tabId) return@post
            val tab = tabs.firstOrNull { it.id == tabId } ?: return@post
            val currentHost = PrivacyRequestSanitizer.webHost(pageUrls[tabId] ?: tab.url)
            if (currentHost != pageHost || tab.profileId != requestContext.profileId) return@post
            if (navigationGenerations[tabId] != requestContext.navigationGeneration) return@post
            if (isFederatedLoginCompatibilityEnabled(tab, pageUrls[tabId])) return@post
            val offerKey = "$pageHost:${provider.name}"
            if (federatedLoginOfferKeys[tabId] == offerKey) return@post
            federatedLoginOfferKeys[tabId] = offerKey
            federatedLoginOfferSequence++
            federatedLoginOffer = FederatedLoginOffer(
                token = federatedLoginOfferSequence,
                tabId = tabId,
                profileId = tab.profileId,
                pageHost = pageHost,
                provider = provider,
                isPrivate = tab.isIncognito,
                navigationGeneration = requestContext.navigationGeneration,
            )
        }
    }

    private fun isCurrentFederatedLoginOffer(offer: FederatedLoginOffer): Boolean {
        val tab = tabs.firstOrNull { it.id == offer.tabId } ?: return false
        val currentHost = PrivacyRequestSanitizer.webHost(pageUrls[tab.id] ?: tab.url)
        return selectedTabId == tab.id &&
            tab.profileId == offer.profileId &&
            tab.isIncognito == offer.isPrivate &&
            currentHost == offer.pageHost &&
            navigationGenerations[tab.id] == offer.navigationGeneration
    }

    private fun detectCaptchaRequest(
        tabId: String,
        requestUrl: String,
        requestContext: ProtectionRequestContext,
    ) {
        if (!workerSettings.blockThirdPartyCookies) return
        val pageHost = requestContext.pageHost ?: return
        val provider = CaptchaCompatibilityRules.providerForSubresource(
            requestUrl = requestUrl,
            pageUrl = "https://$pageHost/",
        ) ?: return
        mainHandler.post {
            if (destroyed || selectedTabId != tabId || !workerSettings.blockThirdPartyCookies) {
                return@post
            }
            val tab = tabs.firstOrNull { it.id == tabId } ?: return@post
            val pageUrl = pageUrls[tabId] ?: tab.url
            val currentHost = PrivacyRequestSanitizer.webHost(pageUrl)
            if (currentHost != pageHost || tab.profileId != requestContext.profileId) return@post
            if (navigationGenerations[tabId] != requestContext.navigationGeneration) return@post
            if (isSiteProtectionPaused(tabId, pageUrl) ||
                isFederatedLoginCompatibilityEnabled(tab, pageUrl) ||
                isCaptchaCompatibilityEnabled(tab, pageUrl)
            ) return@post
            val offerKey = "${requestContext.navigationGeneration}:$pageHost:${provider.name}"
            val tabOfferKeys = captchaCompatibilityOfferKeys.computeIfAbsent(tabId) {
                ConcurrentHashMap.newKeySet()
            }
            if (!tabOfferKeys.add(offerKey)) return@post
            captchaCompatibilityOfferSequence++
            captchaCompatibilityOffer = CaptchaCompatibilityOffer(
                token = captchaCompatibilityOfferSequence,
                tabId = tabId,
                profileId = tab.profileId,
                pageHost = pageHost,
                provider = provider,
                isPrivate = tab.isIncognito,
                navigationGeneration = requestContext.navigationGeneration,
            )
        }
    }

    private fun isCurrentCaptchaCompatibilityOffer(
        offer: CaptchaCompatibilityOffer,
    ): Boolean {
        val tab = tabs.firstOrNull { it.id == offer.tabId } ?: return false
        val currentHost = PrivacyRequestSanitizer.webHost(pageUrls[tab.id] ?: tab.url)
        return selectedTabId == tab.id &&
            tab.profileId == offer.profileId &&
            tab.isIncognito == offer.isPrivate &&
            currentHost == offer.pageHost &&
            navigationGenerations[tab.id] == offer.navigationGeneration
    }

    private fun isSiteProtectionPaused(tabId: String, pageUrl: String?): Boolean {
        val context = protectionRequestContexts[tabId] ?: return false
        return isSiteProtectionPaused(tabId, context, pageUrl)
    }

    private fun isSiteProtectionPaused(
        tabId: String,
        context: ProtectionRequestContext,
        pageUrl: String?,
    ): Boolean {
        val pageHost = pageUrl?.let(PrivacyRequestSanitizer::webHost) ?: context.pageHost ?: return false
        if (SiteExceptionRules.isPaused(pageHost, temporarySiteExceptions[tabId].orEmpty())) {
            return true
        }
        return !context.isIncognito && SiteExceptionRules.isPaused(
            pageHost,
            permanentSiteExceptions[context.profileId].orEmpty(),
        )
    }

    private fun updateProtectionRequestContext(tabId: String, pageUrl: String?) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val context = protectionRequestContextFor(tab, pageUrl ?: tab.url)
        synchronized(privacyEventLock) {
            protectionRequestContexts[tabId] = context
        }
    }

    private fun protectionRequestContextFor(
        tab: BrowserTab,
        pageUrl: String,
    ): ProtectionRequestContext {
        val pageHost = PrivacyRequestSanitizer.webHost(pageUrl)
        return ProtectionRequestContext(
            profileId = tab.profileId,
            isIncognito = tab.isIncognito,
            storageKey = GeckoProfileStorageRules.privacyStorageKey(tab),
            pageHost = pageHost,
            cookieBannerRemovalDisabled = pageHost == null ||
                isCookieBannerRemovalDisabled(tab, pageHost),
            navigationGeneration = navigationGenerations.getOrDefault(tab.id, 0),
        )
    }

    private fun siteExceptionHostsForTab(tabId: String): Set<String> {
        val context = protectionRequestContexts[tabId] ?: return emptySet()
        val temporary = temporarySiteExceptions[tabId].orEmpty()
        return if (context.isIncognito) {
            temporary
        } else {
            temporary + permanentSiteExceptions[context.profileId].orEmpty()
        }
    }

    private fun sitePrivacyOverridesFor(tab: BrowserTab): Map<String, SitePrivacyOverrides> {
        val temporary = temporarySitePrivacyOverrides[tab.id].orEmpty()
        if (tab.isIncognito) return temporary
        val permanent = permanentSitePrivacyOverrides[tab.profileId].orEmpty()
        if (temporary.isEmpty()) return permanent
        return permanent + temporary
    }

    private fun forcedVerticalScrollHostsForTab(
        tabId: String,
        pageUrl: String? = null,
    ): Set<String> {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return emptySet()
        val overridesByHost = sitePrivacyOverridesFor(tab)
        val forcedHosts = overridesByHost.asSequence()
            .filter { (_, overrides) -> overrides.forceVerticalScrolling == true }
            .map { (host, _) -> host }
            .toMutableSet()
        val pageHost = PrivacyRequestSanitizer.webHost(pageUrl ?: pageUrls[tabId] ?: tab.url)
        if (pageHost != null && SitePrivacyOverrideRules.forceVerticalScrolling(
                overrides = overridesByHost[pageHost],
                bundledDefault = bundledSitePrivacyDefaults.forceVerticalScrolling(pageHost),
            )
        ) {
            forcedHosts += pageHost
        }
        return forcedHosts
    }

    private fun forcedPageZoomHostsForTab(
        tabId: String,
        pageUrl: String? = null,
    ): Set<String> {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return emptySet()
        val overridesByHost = sitePrivacyOverridesFor(tab)
        val forcedHosts = overridesByHost.asSequence()
            .filter { (_, overrides) -> overrides.forcePageZooming == true }
            .map { (host, _) -> host }
            .toMutableSet()
        val pageHost = PrivacyRequestSanitizer.webHost(pageUrl ?: pageUrls[tabId] ?: tab.url)
        if (pageHost != null && SitePrivacyOverrideRules.forcePageZooming(
                overridesByHost[pageHost],
            )
        ) {
            forcedHosts += pageHost
        }
        return forcedHosts
    }

    private fun isCookieBannerRemovalDisabled(tab: BrowserTab, host: String): Boolean =
        SitePrivacyOverrideRules.cookieBannerRemovalDisabled(
            overrides = sitePrivacyOverridesFor(tab)[host],
            bundledDefault = bundledSitePrivacyDefaults.cookieBannerRemovalDisabled(host),
        )

    private fun isForcedVerticalScrolling(tab: BrowserTab, host: String): Boolean =
        SitePrivacyOverrideRules.forceVerticalScrolling(
            overrides = sitePrivacyOverridesFor(tab)[host],
            bundledDefault = bundledSitePrivacyDefaults.forceVerticalScrolling(host),
        )

    private fun isPageZoomingForced(tab: BrowserTab, host: String): Boolean =
        SitePrivacyOverrideRules.forcePageZooming(sitePrivacyOverridesFor(tab)[host])

    private fun isSafeAreaForced(tab: BrowserTab, host: String): Boolean =
        SitePrivacyOverrideRules.forceSafeArea(sitePrivacyOverridesFor(tab)[host])

    private fun isFederatedLoginCompatibilityEnabled(tab: BrowserTab, pageUrl: String?): Boolean {
        val host = PrivacyRequestSanitizer.webHost(pageUrl ?: tab.url) ?: return false
        return SitePrivacyOverrideRules.thirdPartyLoginAllowed(sitePrivacyOverridesFor(tab)[host])
    }

    private fun isFederatedLoginCompatibilityEnabled(tabId: String, pageUrl: String?): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return isFederatedLoginCompatibilityEnabled(tab, pageUrl)
    }

    private fun isCaptchaCompatibilityEnabled(tab: BrowserTab, pageUrl: String?): Boolean {
        val host = PrivacyRequestSanitizer.webHost(pageUrl ?: tab.url) ?: return false
        return SitePrivacyOverrideRules.captchaCompatibilityAllowed(
            sitePrivacyOverridesFor(tab)[host],
        )
    }

    private fun isCaptchaCompatibilityEnabled(tabId: String, pageUrl: String?): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return isCaptchaCompatibilityEnabled(tab, pageUrl)
    }

    private fun isSafeAreaForced(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val host = PrivacyRequestSanitizer.webHost(pageUrls[tabId] ?: tab.url) ?: return false
        return isSafeAreaForced(tab, host)
    }

    private fun isExternalLinkPreviewSafeAreaForced(view: View): Boolean {
        val runtime = externalLinkPreviewRuntime
            ?.takeIf { it.binding.view === view }
            ?: return false
        val pageUrl = externalLinkPreviewState
            ?.takeIf { it.sessionId == runtime.sessionId }
            ?.currentUrl
            ?: runtime.policyTab.url
        val host = PrivacyRequestSanitizer.webHost(pageUrl) ?: return false
        return isSafeAreaForced(runtime.policyTab, host)
    }

    private fun isCookieBannerRemovalEnabled(tabId: String, pageUrl: String?): Boolean {
        if (!workerSettings.hideCookieConsent || isSiteProtectionPaused(tabId, pageUrl)) return false
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val host = PrivacyRequestSanitizer.webHost(pageUrl ?: tab.url) ?: return false
        return !isCookieBannerRemovalDisabled(tab, host)
    }

    private fun reloadTabWithProtection(tabId: String) {
        updateProtectionRequestContext(tabId, pageUrls[tabId])
        val session = geckoEngineSessionFor(tabId)
        geckoPrivacyPolicyFor(tabId)?.let { policy ->
            session.updatePrivacyPolicy(policy) {
                session.execute(BrowserEngineCommands.reload())
            }
        }
    }

    private fun refreshProtectionForProfile(profileId: String) {
        tabs.asSequence()
            .filter { tab -> tab.profileId == profileId && !tab.isIncognito }
            .forEach { tab ->
                updateProtectionRequestContext(tab.id, pageUrls[tab.id] ?: tab.url)
                geckoEngineSessions[tab.id]?.let { session ->
                    geckoPrivacyPolicyFor(tab.id)?.let { policy ->
                        session.updatePrivacyPolicy(policy)
                    }
                }
            }
    }

    private fun recreateEngineSessions(
        tabIds: Set<String>,
        reloadImmediately: Boolean = false,
    ) {
        if (tabIds.isEmpty()) return
        tabIds.forEach(::closeGeckoEngineSession)
        engineViewRevision++
        if (reloadImmediately) {
            tabIds.forEach { tabId ->
                tabs.firstOrNull { tab -> tab.id == tabId && tab.url != BLANK_URL }
                    ?.let { geckoEngineSessionFor(tabId) }
            }
        }
    }

    private fun prepareIncognitoProfileForRemoval() {
        if (geckoLinkPeekBindings.isNotEmpty()) contentActions.dismiss()
    }

    private fun clearIncognitoProfile() {
        incognitoRuleHits.clear()
        temporaryMutedDomains.clear()
        temporaryDesktopViewDomains.clear()
        temporaryAlwaysBlockPopupDomains.clear()
        permissionRepository.clearPrivateSession()
        permissionRevision++
        if (ephemeralRuleIds.isNotEmpty()) {
            filterRules.removeAll { it.id in ephemeralRuleIds }
            ephemeralRuleIds.clear()
            onFilterRulesChanged(persist = false)
        }
    }

    private fun updateSelectedTabId(tabId: String) {
        if (selectedTabId != tabId) cancelPendingHttpAuthChallenge()
        if (contentActions.sourceTabId != null && contentActions.sourceTabId != tabId) {
            contentActions.dismiss()
        }
        val previousTabId = selectedTabId
        if (usesGeckoEngine && previousTabId != tabId) {
            geckoEngineSessions[previousTabId]?.setActive(false)
        }
        selectedTabId = tabId
        if (usesGeckoEngine && previousTabId != tabId) {
            geckoEngineSessions[tabId]?.setActive(
                isActivityResumed && externalLinkPreviewState == null,
            )
            geckoEngineSessionFactory.notifySelectedExtensionTabChanged()
            castMediaCandidate = geckoMediaStates[tabId]?.let { state ->
                geckoCastMediaCandidate(tabId, state)
            }
            notifyMediaStateChanged()
        }
    }

    private fun destroyLinkPeekPreviewSessions() {
        geckoLinkPeekBindings.keys.toList().forEach(::releaseLinkPeekPreviewView)
    }

    private fun isDomainMuted(tab: BrowserTab, pageUrl: String?): Boolean {
        val mutedDomains = if (tab.isIncognito) {
            temporaryMutedDomains[tab.profileId]
        } else {
            permanentMutedDomains[tab.profileId]
        }
        return DomainMuteRules.isMuted(pageUrl, mutedDomains.orEmpty())
    }

    private fun isTabAudioMuted(tab: BrowserTab, pageUrl: String?): Boolean =
        GeckoExtensionChromeRules.effectiveAudioMuted(
            domainMuted = isDomainMuted(tab, pageUrl),
            extensionOverride = extensionTabMuteOverrides[tab.id],
        )

    private fun setExtensionTabMuted(tabId: String, muted: Boolean): Boolean {
        val tab = tabs.firstOrNull { candidate -> candidate.id == tabId } ?: return false
        extensionTabMuteOverrides[tabId] = muted
        geckoEngineSessions[tabId]?.setAudioMuted(
            isTabAudioMuted(tab, pageUrls[tabId] ?: tab.url),
        )
        return true
    }

    private fun isDesktopView(tab: BrowserTab, pageUrl: String?): Boolean {
        val desktopDomains = if (tab.isIncognito) {
            temporaryDesktopViewDomains[tab.profileId]
        } else {
            permanentDesktopViewDomains[tab.profileId]
        }
        return DesktopSiteRules.isDesktopView(pageUrl, desktopDomains.orEmpty())
    }

    private fun isAlwaysBlockPopupsEnabled(tab: BrowserTab, pageUrl: String?): Boolean {
        val domains = if (tab.isIncognito) {
            temporaryAlwaysBlockPopupDomains[tab.profileId]
        } else {
            permanentAlwaysBlockPopupDomains[tab.profileId]
        }
        return PopupSiteRules.shouldAlwaysBlock(pageUrl, domains.orEmpty())
    }

    private fun reloadDesktopViewDomain(
        profileId: String,
        isIncognito: Boolean,
        domain: String,
    ) {
        tabs.asSequence()
            .filter { tab -> tab.profileId == profileId && tab.isIncognito == isIncognito }
            .filter { tab ->
                DesktopSiteRules.domainForUrl(pageUrls[tab.id] ?: tab.url) == domain
            }
            .forEach { tab ->
                val session = geckoEngineSessionFor(tab.id)
                session.setDesktopMode(isDesktopView(tab, pageUrls[tab.id] ?: tab.url))
                session.execute(BrowserEngineCommands.reload())
            }
    }

    private fun desktopViewDomains(tab: BrowserTab): Set<String> = if (tab.isIncognito) {
        temporaryDesktopViewDomains[tab.profileId].orEmpty()
    } else {
        permanentDesktopViewDomains[tab.profileId].orEmpty()
    }

    private fun refreshDomainMuteForProfile(profileId: String, isIncognito: Boolean) {
        tabs.filter { it.profileId == profileId && it.isIncognito == isIncognito }
            .forEach { tab -> refreshDomainMuteForTab(tab.id) }
    }

    private fun refreshDomainMuteForTab(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val session = geckoEngineSessions[tabId] ?: return
        session.setAudioMuted(isTabAudioMuted(tab, pageUrls[tabId] ?: tab.url))
    }

    private companion object {
        val ALL_WEB_ORIGINS = setOf("*")
        val WEB_SCHEMES = setOf("http", "https")
        val SAFE_AREA_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        val NON_IME_INSET_TYPES = intArrayOf(
            WindowInsetsCompat.Type.statusBars(),
            WindowInsetsCompat.Type.navigationBars(),
            WindowInsetsCompat.Type.captionBar(),
            WindowInsetsCompat.Type.systemGestures(),
            WindowInsetsCompat.Type.mandatorySystemGestures(),
            WindowInsetsCompat.Type.tappableElement(),
            WindowInsetsCompat.Type.displayCutout(),
        )
        const val PREVIEW_CAPTURE_TIMEOUT_MS = 64L
        const val CAMERA_FACING_EXTRA = "android.intent.extras.CAMERA_FACING"
        const val CAMERA_FACING_BACK = 0
        const val CAMERA_FACING_FRONT = 1
        const val GECKO_PREVIEW_CAPTURE_TIMEOUT_MS = 1_000L
        const val BOTTOM_BAR_COLLAPSE_THRESHOLD_DP = 24f
        const val BOTTOM_BAR_EXPAND_THRESHOLD_DP = 16f
        const val PREVIEW_NEAR_BLACK_CHANNEL_MAX = 16
        const val BLOCKER_COUNT_FLUSH_DELAY_MS = 250L
        const val SYNC_REFRESH_INTERVAL_MILLIS = 15_000L
        const val SYNC_NAVIGATION_DEBOUNCE_MILLIS = 450L
        const val MAX_COSMETIC_DOCUMENT_START_RULES = 64
        const val MAX_GENERIC_POLICY_HOST_LENGTH = 253
        const val MAX_GENERIC_POLICY_CACHE_ENTRIES = 64
        const val MAX_REPORTED_ALLOW_DECISIONS = 64
        const val MAX_TLS_MAIN_FRAME_TARGETS = 16
        const val MAX_CONTEXT_LINK_TITLE_CHARS = 500
        const val MAX_WEB_MEDIA_TITLE_LENGTH = 160
        const val MAX_WEB_MEDIA_ORIGIN_LENGTH = 255
        const val MAX_WEB_MEDIA_CHANNELS_PER_WEBVIEW = 32
        const val MAX_RETIRED_WEB_MEDIA_DOCUMENTS = 64
        const val MAX_WEB_MEDIA_MESSAGES_PER_WINDOW = 128
        const val WEB_MEDIA_RATE_WINDOW_MILLIS = 1_000L
        const val PICTURE_IN_PICTURE_FALLBACK_GRACE_MILLIS = 900L
        const val PICTURE_IN_PICTURE_EXIT_GUARD_DELAY_MILLIS = 350L
        val PICTURE_IN_PICTURE_PLAY_RETRY_DELAYS_MILLIS = longArrayOf(250L, 1_000L, 2_000L)
        const val PICTURE_IN_PICTURE_TRANSITION_TIMEOUT_MILLIS = 5_000L
        const val WEB_PICTURE_IN_PICTURE_FULLSCREEN_CLEANUP_DELAY_MILLIS = 250L
        const val WEB_PICTURE_IN_PICTURE_REQUEST_TIMEOUT_MILLIS = 5_000L
        const val WEB_PERMISSION_REQUEST_CODE = 7_041
        const val FILE_CHOOSER_REQUEST_CODE = 7_042
    }

    private data class PendingPermissionAccess(
        val identity: PermissionRequestIdentity,
        val site: PermissionSiteKey,
        val requested: Set<SitePermission>,
        val allowed: Set<SitePermission>,
        val prompted: Set<SitePermission>,
        val kind: PendingPermissionKind,
        val requestToken: Any,
        val promptId: Long?,
        val awaitingRuntime: Boolean,
        val delivery: PermissionResponseDelivery,
    )

    private data class PendingHttpAuthChallenge(
        val promptId: Long,
        val tabId: String,
            val geckoSession: AndroidBrowserEngineSessionPort?,
        val navigationGeneration: Int,
        val confirm: (String, String) -> Unit,
        val dismiss: () -> Unit,
    )

    private data class PendingGeckoAndroidPermissionRequest(
        val tabId: String,
        val session: AndroidBrowserEngineSessionPort,
        val navigationGeneration: Int,
        val request: BrowserEngineAndroidPermissionRequest,
    )

    private data class PendingWebPrompt(
        val promptId: Long,
        val tabId: String,
        val session: AndroidBrowserEngineSessionPort,
        val navigationGeneration: Int,
        val response: BrowserEngineWebPromptResponse,
    )

    private enum class PendingPermissionKind {
        WebResource,
        Geolocation,
        Gecko,
    }

    private data class PendingFileChooser(
        val identity: FileChooserIdentity,
        val delivery: FileChooserResultDelivery<Array<Uri>?>,
        val geckoSession: AndroidBrowserEngineSessionPort?,
        val allowMultiple: Boolean,
        val acceptTypes: Array<String>,
        val captureOutput: FileCaptureOutput?,
    )

    private data class FileCaptureOutput(
        val uri: Uri,
        val mimeType: String,
    )

    private data class ProtectionRequestContext(
        val profileId: String,
        val isIncognito: Boolean,
        val storageKey: String,
        val pageHost: String?,
        val cookieBannerRemovalDisabled: Boolean,
        val navigationGeneration: Int,
        val pendingFilterHits: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
    )

    private data class LinkPeekProtectionState(
        val pageUrl: String,
        val requestContext: ProtectionRequestContext,
    )

    private sealed interface ExternalLinkPreviewEngineBinding {
        val view: View

        data class Gecko(
            val session: AndroidBrowserEngineSessionPort,
            override val view: View,
        ) : ExternalLinkPreviewEngineBinding
    }

    private data class ExternalLinkPreviewRuntime(
        val sessionId: Long,
        val generation: Int,
        val policyTab: BrowserTab,
        val binding: ExternalLinkPreviewEngineBinding,
        var hasStarted: Boolean = false,
        var downloadGrant: ExternalPreviewDownloadGrant? = null,
        var pendingInternalNavigationUrl: String? = null,
    ) {
        val geckoBinding: ExternalLinkPreviewEngineBinding.Gecko
            get() = binding as ExternalLinkPreviewEngineBinding.Gecko
    }
}

private fun defaultSearchSuggestionProvider(): SearchSuggestionProvider =
    if (BuildConfig.FOSS_DISTRIBUTION) {
        SearchSuggestionProvider.None
    } else {
        SearchSuggestionProvider.DuckDuckGo
    }

enum class CapsuleSaveResult {
    PinRequested,
    PinningUnsupported,
    PinRequestFailed,
    Updated,
    UpdateFailed,
    IconSaveFailed,
    LimitReached,
    Invalid,
}

private fun BrowserTab.toCandyTrailForkTab() = CandyTrailForkTab(
    id = id,
    profileId = profileId,
    isIncognito = isIncognito,
)
