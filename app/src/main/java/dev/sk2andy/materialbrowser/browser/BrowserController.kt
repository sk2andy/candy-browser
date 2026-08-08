package dev.sk2andy.materialbrowser.browser

import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.net.Uri
import android.print.PrintManager
import android.view.PixelCopy
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.ServiceWorkerClient
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.Insets
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.CandyCosmeticScript
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
import dev.sk2andy.materialbrowser.blocking.ContentBlocker
import dev.sk2andy.materialbrowser.blocking.ForcedVerticalScrollScript
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer
import dev.sk2andy.materialbrowser.blocking.PrivacyPolicyRules
import dev.sk2andy.materialbrowser.blocking.PrivacyRuleDecisionAction
import dev.sk2andy.materialbrowser.blocking.PrivacyRuleDecisionSummary
import dev.sk2andy.materialbrowser.blocking.PrivacyXRayRepository
import dev.sk2andy.materialbrowser.blocking.PrivacyXRaySnapshot
import dev.sk2andy.materialbrowser.blocking.RequestProtectionRules
import dev.sk2andy.materialbrowser.blocking.SiteExceptionRules
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrides
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrideRules
import dev.sk2andy.materialbrowser.blocking.SiteProtectionState
import dev.sk2andy.materialbrowser.capsule.CapsuleDeletionRules
import dev.sk2andy.materialbrowser.capsule.CapsuleIconRenderer
import dev.sk2andy.materialbrowser.capsule.CapsuleIconMode
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
import dev.sk2andy.materialbrowser.browser.actions.WebContentActionState
import dev.sk2andy.materialbrowser.browser.actions.WebViewHitTestResolver
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionComposer
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.browser.commands.AndroidCommandCatalog
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommandRegistry
import dev.sk2andy.materialbrowser.browser.commands.CommandContext
import dev.sk2andy.materialbrowser.browser.commands.CommandCookieScope
import dev.sk2andy.materialbrowser.browser.commands.CommandMatcher
import dev.sk2andy.materialbrowser.browser.commands.WebViewCommandActions
import dev.sk2andy.materialbrowser.browser.commands.WebViewProfileCookies
import dev.sk2andy.materialbrowser.browser.credentials.SystemWebViewCredentials
import dev.sk2andy.materialbrowser.browser.integration.AssistantSummaryLauncher
import dev.sk2andy.materialbrowser.browser.integration.AssistantSummaryRequest
import dev.sk2andy.materialbrowser.browser.integration.AssistantSummaryResult
import dev.sk2andy.materialbrowser.browser.integration.DefaultBrowserRole
import dev.sk2andy.materialbrowser.browser.integration.ExternalAppLauncher
import dev.sk2andy.materialbrowser.browser.integration.ExternalLaunchResult
import dev.sk2andy.materialbrowser.browser.integration.PageShareLauncher
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.browser.integration.PageShareRequest
import dev.sk2andy.materialbrowser.browser.integration.PageShareResult
import dev.sk2andy.materialbrowser.browser.permissions.PermissionOrigin
import dev.sk2andy.materialbrowser.browser.permissions.ActivePermissionGrant
import dev.sk2andy.materialbrowser.browser.permissions.ActivePermissionLedger
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
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingLibraryRules
import dev.sk2andy.materialbrowser.data.CandyTrailRepository
import dev.sk2andy.materialbrowser.data.CandyRuleRepository
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.data.FavoriteUndoRules
import dev.sk2andy.materialbrowser.data.FaviconRepository
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.PermissionRadarStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleIconStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleStore
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabDuplicateRules
import dev.sk2andy.materialbrowser.data.TabPinningRules
import dev.sk2andy.materialbrowser.data.TabPreviewRepository
import dev.sk2andy.materialbrowser.data.TabPreviewCaptureRules
import dev.sk2andy.materialbrowser.data.TabRetentionRules
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BrowserController(
    private val activity: Activity,
    private val requestRuntimePermissions: (Set<String>) -> Unit = { permissions ->
        activity.requestPermissions(permissions.toTypedArray(), WEB_PERMISSION_REQUEST_CODE)
    },
    private val launchFileChooser: (Intent) -> Unit = { intent ->
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
    },
) {
    val tabs = mutableStateListOf<BrowserTab>()
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
    val siteCapsules = mutableStateListOf<SiteCapsule>()
    val contentActions = WebContentActionState()

    var selectedTabId by mutableStateOf("")
        private set
    var activeProfileId by mutableStateOf(DEFAULT_PROFILE_ID)
        private set
    var blockerSettings by mutableStateOf(BlockerSettings())
        private set
    var inactiveTabLifetime by mutableStateOf(InactiveTabLifetime.Never)
        private set
    var searchEngine by mutableStateOf(SearchEngine.Google)
        private set
    var searchSuggestionProvider by mutableStateOf(SearchSuggestionProvider.DuckDuckGo)
        private set
    var dismissResistancePercent by mutableIntStateOf(40)
        private set
    var tabOverviewMode by mutableStateOf(TabOverviewMode.Hero)
        private set
    var isAddressBarDocked by mutableStateOf(false)
        private set
    var isWebContentEdgeToEdgeEnabled by mutableStateOf(false)
        private set
    var isDefaultBrowser by mutableStateOf(false)
        private set
    var activeCapsuleId by mutableStateOf<String?>(null)
        private set
    var webViewRevision by mutableIntStateOf(0)
        private set
    var permissionPrompt by mutableStateOf<PermissionPrompt?>(null)
        private set
    val isProfileIsolationSupported: Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    val activeSiteCapsule: SiteCapsule?
        get() = activeCapsuleId?.let { id -> siteCapsules.firstOrNull { it.id == id } }
    val isCapsulePinningSupported: Boolean
        get() = capsuleShortcuts.isPinningSupported()
    val canCreateSiteCapsule: Boolean
        get() = SiteCapsuleRules.canCreate(siteCapsules.size)
    val selectedFavicon: Bitmap?
        get() = favicons[selectedTabId]
    private val bottomBarCompactStates = mutableStateMapOf<String, Boolean>()

    val isBottomBarCompact: Boolean
        get() = bottomBarCompactStates[selectedTabId] == true

    @VisibleForTesting
    fun selectedWebViewForTesting(): WebView = webViewFor(selectedTabId)

    private val webViews = mutableMapOf<String, WebView>()
    private val edgeToEdgePages = mutableMapOf<String, Boolean>()
    private val navigationGenerations = mutableMapOf<String, Int>()
    private val popupOpeners = mutableMapOf<String, String>()
    private val forcedVerticalScrollScriptHandlers = mutableMapOf<WebView, ScriptHandler>()
    private val cosmeticScriptHandlers = mutableMapOf<WebView, List<ScriptHandler>>()
    private val pendingConsentCssUrls = mutableMapOf<String, String?>()
    private val pageUrls = ConcurrentHashMap<String, String>()
    private val webViewProfileKeys = ConcurrentHashMap<String, String>()
    private val configuredServiceWorkerProfiles = mutableSetOf<String>()
    private var incognitoWebViewProfileName = newIncognitoWebViewProfileName()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fileChooserValidationExecutor = Executors.newSingleThreadExecutor()
    private var pendingPreviewCapture: Runnable? = null
    private val pendingBlockedCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingPrivacyTabs = ConcurrentHashMap.newKeySet<String>()
    private val reportedAllowedDecisions = ConcurrentHashMap<String, MutableSet<String>>()
    private val blockerFlushScheduled = AtomicBoolean(false)
    private val privacyXRayRepository = PrivacyXRayRepository()
    private val privacyEventLock = Any()
    private val temporarySiteExceptions = ConcurrentHashMap<String, Set<String>>()
    private val temporarySitePrivacyOverrides =
        ConcurrentHashMap<String, Map<String, SitePrivacyOverrides>>()
    private val permissionRepository = PermissionRadarRepository(PermissionRadarStore(activity))
    private val activePermissions = ActivePermissionLedger()
    private var pendingPermissionAccess: PendingPermissionAccess? = null
    private var pendingFileChooser: PendingFileChooser? = null
    private var permissionPromptSequence = 0L
    private var permissionRevision by mutableIntStateOf(0)
    private val protectionRequestContexts = ConcurrentHashMap<String, ProtectionRequestContext>()
    private var isActivityResumed = false
    private var isActivityStarted = false
    @Volatile
    private var destroyed = false
    private var previewCaptureInFlight = false
    private var dirtyPreviewTabId: String? = null
    private var previewCaptureEnabled = true
    private var previewContentBottomInWindowPx: Int? = null
    private var lastWindowInsets: WindowInsetsCompat? = null
    private var browserChromeOwnsIme = false
    private var previewEpoch = 0
    private var faviconEpoch = 0
    private val faviconGenerations = mutableMapOf<String, Int>()
    private val candyTrailHistoryBindings = mutableMapOf<String, CandyTrailHistoryBinding>()
    private val pendingCandyTrailTargets = mutableMapOf<String, String>()
    private val candyTrailGenerations = mutableMapOf<String, Int>()
    private val capsuleTabIds = mutableMapOf<String, String>()
    var activeCapsuleTabId: String? = null
        private set
    private val pendingCandyTrailRestoreIds = mutableSetOf<String>()
    private val suppressedCandyTrailTabIds = mutableSetOf<String>()
    private var candyTrailEpoch = 0
    private val store = BrowserSessionStore(activity)
    private val permanentMutedDomains = mutableStateMapOf<String, Set<String>>().apply {
        putAll(store.loadMutedDomains())
    }
    private val temporaryMutedDomains = mutableStateMapOf<String, Set<String>>()
    private val profileDeletionCoordinator =
        WebViewProfileDeletionCoordinator(store, ::tryDeleteNamedWebViewProfile)
    private val previewRepository = TabPreviewRepository.get(activity)
    private val faviconRepository = FaviconRepository.get(activity)
    private val candyTrailRepository = CandyTrailRepository.get(activity)
    private val siteCapsuleStore = SiteCapsuleStore(activity)
    private val siteCapsuleIconStore = SiteCapsuleIconStore(activity)
    private val capsuleShortcuts = CapsuleShortcutPublisher(activity)
    private val candyRuleRepository = CandyRuleRepository.get(activity)
    private val contentBlocker = ContentBlocker(activity)
    private val downloadManager = BrowserDownloadManager(activity)
    private val externalApps = ExternalAppLauncher(activity)
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

    val activeTabs: List<BrowserTab>
        get() = tabs.filter { it.profileId == activeProfileId }

    val canToggleSelectedDomainMute: Boolean
        get() = isDomainMuteSupported && DomainMuteRules.domainForUrl(selectedTab.url) != null

    val isSelectedDomainMuted: Boolean
        get() = isDomainMuted(selectedTab, selectedTab.url)

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
            webViews[tabId]?.reload()
        } else if (
            pendingPermissionAccess?.let { access ->
                access.site == site && permission in access.requested
            } == true
        ) {
            cancelPendingPermissionAccess(tabId)
        }
        if (permission == SitePermission.Location) {
            geolocationPermissionsFor(tabId)?.clear(normalizedOrigin)
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
            webViews[tabId]?.reload()
        }
        geolocationPermissionsFor(tabId)?.clear(normalizedOrigin)
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

    fun onRuntimePermissionResult(results: Map<String, Boolean>) {
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
            pending.delivery.complete(null)
            return
        }
        val parsed = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            .orEmpty()
            .map(Uri::toString)
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
                        pending.delivery.complete(null)
                    } else {
                        pendingFileChooser = null
                        pending.delivery.complete(safeUris)
                    }
                }
            }
        }.onFailure {
            if (pendingFileChooser === pending) pendingFileChooser = null
            pending.delivery.complete(null)
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
        deletePendingWebViewProfiles()
        filterRules += candyRuleRepository.load()
        rebuildCandyMatcher()
        val nowMillis = System.currentTimeMillis()
        blockerSettings = workerSettings
        inactiveTabLifetime = store.loadInactiveTabLifetime()
        searchEngine = store.loadSearchEngine()
        searchSuggestionProvider = store.loadSearchSuggestionProvider()
        dismissResistancePercent = store.loadDismissResistancePercent()
        tabOverviewMode = store.loadTabOverviewMode()
        isAddressBarDocked = store.loadAddressBarDocked()
        isWebContentEdgeToEdgeEnabled = store.loadWebContentEdgeToEdgeEnabled()
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
        activeProfileId = restoredActiveProfileId
            .takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        val (restoredTabs, restoredSelection) = store.loadTabs(nowMillis)
        history += store.loadHistory()
        favorites += store.loadFavorites()
        val profileIds = profiles.mapTo(mutableSetOf(), BrowserProfile::id)
        tabs += restoredTabs.take(MAX_TABS).map { tab ->
            if (tab.profileId in profileIds) tab else tab.copy(profileId = profiles.first().id)
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
        persist()
        // Incognito tabs are never restored. Remove data left by process death before
        // any private WebView can reuse the old profile.
        clearIncognitoProfile()
        restorePersistedPreviews()
        restorePersistedFavicons()
        restorePersistedCandyTrails()
        WebView.setWebContentsDebuggingEnabled(false)
        configureServiceWorkerBlocking()
        mainHandler.post(contentBlocker::prepareConsentScript)
    }

    fun attachSelectedWebView(container: FrameLayout) {
        val webView = webViewFor(selectedTabId)
        if (webView.parent === container && container.childCount == 1) {
            return
        }
        (webView.parent as? FrameLayout)?.removeView(webView)
        container.removeAllViews()
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        dispatchCurrentWindowInsets(selectedTabId, webView)
        SystemWebViewCredentials.onAttached(webView)
        if (isActivityResumed) resumeWebView(selectedTabId, webView)
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
        // callback, so forward every change to Chromium's WebView inset controller.
        dispatchWindowInsetsToAttachedWebViews(insets)
    }

    fun setBrowserChromeOwnsIme(ownsIme: Boolean) {
        if (browserChromeOwnsIme == ownsIme) return
        browserChromeOwnsIme = ownsIme
        val insets = lastWindowInsets ?: return
        dispatchWindowInsetsToAttachedWebViews(insets)
    }

    private fun dispatchWindowInsetsToAttachedWebViews(insets: WindowInsetsCompat) {
        webViews.forEach { (tabId, webView) ->
            if (webView.isAttachedToWindow) applyWindowInsets(tabId, webView, insets)
        }
    }

    fun detachWebView(container: FrameLayout) {
        container.removeAllViews()
    }

    private fun dispatchCurrentWindowInsets(tabId: String, webView: WebView) {
        // A reused WebView can attach after the content root's inset traversal. requestApplyInsets()
        // alone does not cross this Compose AndroidView holder, so dispatch the current snapshot.
        webView.doOnAttach { attachedView ->
            val insets = ViewCompat.getRootWindowInsets(attachedView) ?: lastWindowInsets
            if (insets != null) applyWindowInsets(tabId, webView, insets)
        }
    }

    private fun applyWindowInsets(
        tabId: String,
        webView: WebView,
        insets: WindowInsetsCompat,
    ) {
        val effectiveInsets = if (browserChromeOwnsIme) {
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()
        } else {
            insets
        }
        val drawsEdgeToEdge = drawsEdgeToEdge(tabId)
        val safeArea = effectiveInsets.getInsets(SAFE_AREA_INSET_TYPES)
        val navigationBars = effectiveInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val tappableElements = effectiveInsets.getInsets(WindowInsetsCompat.Type.tappableElement())
        val hasTappableNavigation =
            (navigationBars.left > 0 && tappableElements.left > 0) ||
                (navigationBars.top > 0 && tappableElements.top > 0) ||
                (navigationBars.right > 0 && tappableElements.right > 0) ||
                (navigationBars.bottom > 0 && tappableElements.bottom > 0)
        val usesGestureNavigation = navigationBars != Insets.NONE && !hasTappableNavigation
        val topMargin = if (drawsEdgeToEdge) 0 else safeArea.top
        val bottomMargin = when {
            drawsEdgeToEdge -> 0
            isWebContentEdgeToEdgeEnabled && usesGestureNavigation -> 0
            else -> safeArea.bottom
        }
        val margins = if (drawsEdgeToEdge) {
            Insets.NONE
        } else {
            Insets.of(safeArea.left, topMargin, safeArea.right, bottomMargin)
        }
        (webView.layoutParams as? FrameLayout.LayoutParams)?.let { layoutParams ->
            if (
                layoutParams.leftMargin != margins.left ||
                layoutParams.topMargin != margins.top ||
                layoutParams.rightMargin != margins.right ||
                layoutParams.bottomMargin != margins.bottom
            ) {
                layoutParams.setMargins(margins.left, margins.top, margins.right, margins.bottom)
                webView.layoutParams = layoutParams
            }
        }
        val rendererInsets = if (drawsEdgeToEdge) {
            effectiveInsets
        } else {
            WindowInsetsCompat.Builder(effectiveInsets)
                .setInsets(
                    SAFE_AREA_INSET_TYPES,
                    Insets.of(
                        0,
                        safeArea.top - topMargin,
                        0,
                        safeArea.bottom - bottomMargin,
                    ),
                )
                .build()
        }
        ViewCompat.dispatchApplyWindowInsets(webView, rendererInsets)
    }

    private fun hasSameNonImeInsets(
        previous: WindowInsetsCompat,
        current: WindowInsetsCompat,
    ): Boolean = NON_IME_INSET_TYPES.all { type ->
        previous.getInsets(type) == current.getInsets(type) &&
            previous.isVisible(type) == current.isVisible(type)
    }

    private fun drawsEdgeToEdge(tabId: String): Boolean =
        isWebContentEdgeToEdgeEnabled && edgeToEdgePages[tabId] == true

    private fun detectPageEdgeToEdge(tabId: String, webView: WebView) {
        val navigationGeneration = navigationGenerations[tabId] ?: return
        webView.evaluateJavascript(PageViewportFit.observerScript(navigationGeneration)) { result ->
            if (
                webViews[tabId] !== webView ||
                navigationGenerations[tabId] != navigationGeneration
            ) {
                return@evaluateJavascript
            }
            setPageEdgeToEdge(
                tabId,
                webView,
                enabled = PageViewportFit.isCoverResult(result),
                force = true,
            )
        }
    }

    private inner class ViewportFitBridge(
        private val tabId: String,
        private val webView: WebView,
    ) {
        @JavascriptInterface
        fun update(navigationGeneration: Int, enabled: Boolean) {
            mainHandler.post {
                if (
                    webViews[tabId] !== webView ||
                    navigationGenerations[tabId] != navigationGeneration
                ) {
                    return@post
                }
                setPageEdgeToEdge(tabId, webView, enabled)
            }
        }
    }

    private fun setPageEdgeToEdge(
        tabId: String,
        webView: WebView,
        enabled: Boolean,
        force: Boolean = false,
    ) {
        val previous = edgeToEdgePages.put(tabId, enabled)
        if (!force && previous == enabled) return
        val insets = ViewCompat.getRootWindowInsets(webView) ?: lastWindowInsets ?: return
        applyWindowInsets(tabId, webView, insets)
    }

    fun submitAddress(input: String) {
        bottomBarCompactStates[selectedTabId] = false
        val target = AddressResolver.resolve(input, searchEngine)
        val webView = webViewFor(selectedTabId)
        applyMediaPlaybackPolicy(selectedTabId, webView)
        updateTab(selectedTabId) {
            it.copy(
                isLoading = target != BLANK_URL,
                progress = 0,
                error = null,
            )
        }
        if (target == BLANK_URL) {
            webView.loadUrl(BLANK_URL)
        } else {
            webView.loadUrl(target)
        }
    }

    fun openUrl(url: String, inNewTab: Boolean = false) {
        leaveSiteCapsule()
        if (inNewTab) {
            createTab(url)
        } else {
            submitAddress(url)
        }
    }

    fun resolveCapsuleLaunch(action: String?, capsuleId: String?): CapsuleLaunchResolution =
        CapsuleIntentRules.resolve(action, capsuleId, siteCapsules)

    fun openSiteCapsule(capsuleId: String, navigateToStart: Boolean = true): Boolean {
        val capsule = siteCapsules.firstOrNull { it.id == capsuleId } ?: return false
        if (profiles.none { it.id == capsule.profileId }) return false
        if (activeProfileId != capsule.profileId) selectProfile(capsule.profileId)
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
        if (activeProfileId != capsule.profileId) selectProfile(capsule.profileId)
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
            webViewFor(selectedTabId).loadUrl(BLANK_URL)
        } else if (!selectedTab.isFreshBlankTab) {
            val previousTabId = selectedTabId
            if (createTab(BLANK_URL, isIncognito = false) == previousTabId) {
                submitAddress(BLANK_URL)
            }
        }
    }

    fun upsertSiteCapsule(draft: SiteCapsuleDraft, sourceFavicon: Bitmap? = null): CapsuleSaveResult {
        val existing = draft.id?.let { id -> siteCapsules.firstOrNull { it.id == id } }
        if (existing == null && !SiteCapsuleRules.canCreate(siteCapsules.size)) {
            return CapsuleSaveResult.LimitReached
        }
        if (profiles.none { it.id == draft.profileId }) return CapsuleSaveResult.Invalid
        val nowMillis = System.currentTimeMillis()
        val capsule = if (existing == null) {
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
        val updated = siteCapsules.filterNot { it.id == capsule.id } + capsule
        siteCapsules.clear()
        siteCapsules += SiteCapsuleRules.bounded(updated)
        siteCapsuleStore.save(siteCapsules)
        val profileEmoji = profiles.firstOrNull { it.id == capsule.profileId }?.emoji.orEmpty()
        val storedIcon = siteCapsuleIconStore.load(capsule.id)
        val icon = if (
            capsule.iconMode == CapsuleIconMode.Favicon &&
            sourceFavicon == null &&
            storedIcon != null
        ) {
            storedIcon
        } else {
            CapsuleIconRenderer.render(
                name = capsule.name,
                profileEmoji = profileEmoji,
                favicon = sourceFavicon.takeIf { capsule.iconMode == CapsuleIconMode.Favicon },
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
        if (activeCapsuleId == capsuleId) leaveSiteCapsule()
        capsuleTabIds.remove(capsuleId)
        siteCapsules.clear()
        siteCapsules += remaining
        siteCapsuleStore.save(siteCapsules)
        siteCapsuleIconStore.delete(capsuleId)
        capsuleShortcuts.disable(capsule, activity.getString(R.string.capsule_shortcut_deleted))
        return true
    }

    fun siteCapsuleIcon(capsuleId: String): Bitmap? = siteCapsuleIconStore.load(capsuleId)

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
                    profileEmoji = fallbackProfile.emoji,
                    favicon = null,
                )
            } else {
                siteCapsuleIconStore.load(capsule.id) ?: CapsuleIconRenderer.render(
                    name = capsule.name,
                    profileEmoji = fallbackProfile.emoji,
                    favicon = null,
                )
            }
            siteCapsuleIconStore.save(capsule.id, icon)
            capsuleShortcuts.update(capsule, icon)
        }
    }

    fun createTab(
        initialUrl: String = BLANK_URL,
        isIncognito: Boolean = selectedTab.isIncognito,
    ): String {
        val nowMillis = System.currentTimeMillis()
        pruneStaleTabs(nowMillis)
        if (tabs.size >= MAX_TABS) {
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
        webViews[selectedTabId]?.let(::pauseWebView)
        val resolvedUrl = if (initialUrl == BLANK_URL) {
            BLANK_URL
        } else {
            AddressResolver.resolve(initialUrl, searchEngine)
        }
        val tab = newTabState(
            url = resolvedUrl,
            nowMillis = nowMillis,
            isIncognito = isIncognito,
        )
        tabs += tab
        selectedTabId = tab.id
        rememberSelectedTab(activeProfileId, tab.id)
        bottomBarCompactStates[tab.id] = false
        persist()
        return tab.id
    }

    fun createBackgroundTab(initialUrl: String): String? {
        pruneStaleTabs()
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            return null
        }
        val resolvedUrl = AddressResolver.resolve(initialUrl, searchEngine)
        val tab = newTabState(
            url = resolvedUrl,
            nowMillis = System.currentTimeMillis(),
            isIncognito = selectedTab.isIncognito,
        )
        tabs += tab
        bottomBarCompactStates[tab.id] = false
        persist()
        pauseWebView(webViewFor(tab.id))
        contentActions.requestAddressBarPulse()
        contentActions.dismiss()
        return tab.id
    }

    fun createProfile(emoji: String, isolationEnabled: Boolean = false): String? {
        if (profiles.size >= MAX_PROFILES) {
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
        val safeEmoji = emoji.trim().takeIf(String::isNotEmpty) ?: return null
        val previousTabId = selectedTabId
        clearPermissionActivity(previousTabId)
        touchTab(previousTabId, System.currentTimeMillis())
        webViews[previousTabId]?.let(::pauseWebView)
        val profile = BrowserProfile(
            id = UUID.randomUUID().toString(),
            emoji = safeEmoji,
            isolationEnabled = WebViewProfileRules.effectiveIsolationEnabled(
                requested = isolationEnabled,
                multiProfileSupported = isProfileIsolationSupported,
            ),
        )
        profiles += profile
        activeProfileId = profile.id
        val tab = newTabState()
        tabs += tab
        selectedTabId = tab.id
        rememberSelectedTab(profile.id, tab.id)
        bottomBarCompactStates[tab.id] = false
        persist()
        return profile.id
    }

    fun selectProfile(profileId: String): Boolean {
        if (profileId == activeProfileId || profiles.none { it.id == profileId }) return false
        val previousTabId = selectedTabId
        clearPermissionActivity(previousTabId)
        touchTab(previousTabId, System.currentTimeMillis())
        rememberSelectedTab(activeProfileId, previousTabId)
        webViews[previousTabId]?.let(::pauseWebView)
        activeProfileId = profileId
        val profile = profiles.first { it.id == profileId }
        val targetTab = profile.selectedTabId
            ?.let { tabId -> tabs.firstOrNull { it.id == tabId && it.profileId == profileId } }
            ?: activeTabs.maxByOrNull(BrowserTab::lastAccessedAt)
            ?: newTabState().also(tabs::add)
        selectedTabId = targetTab.id
        touchTab(targetTab.id, System.currentTimeMillis())
        rememberSelectedTab(profileId, targetTab.id)
        persist()
        return true
    }

    fun updateProfileEmoji(profileId: String, emoji: String): Boolean {
        val safeEmoji = emoji.trim().takeIf(String::isNotEmpty) ?: return false
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index < 0 || profiles[index].emoji == safeEmoji) return false
        profiles[index] = profiles[index].copy(emoji = safeEmoji)
        persist()
        return true
    }

    fun setProfileIsolation(profileId: String, enabled: Boolean): Boolean {
        if (!isProfileIsolationSupported) return false
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index < 0 || profiles[index].isolationEnabled == enabled) return false
        val affectedTabIds = WebViewProfileRules.regularTabIdsForStorageChange(tabs, profileId)
        profiles[index] = profiles[index].copy(isolationEnabled = enabled)
        recreateWebViews(affectedTabIds)
        persist()
        return true
    }

    fun deleteProfile(profileId: String, excludedCapsuleId: String? = null): Boolean {
        if (profiles.size <= 1) return false
        val profileIndex = profiles.indexOfFirst { it.id == profileId }
        if (profileIndex < 0) return false
        val fallbackProfile = if (profileId == activeProfileId) {
            profiles.getOrNull(profileIndex + 1) ?: profiles[profileIndex - 1]
        } else {
            profiles.first { it.id == activeProfileId }
        }
        reassignSiteCapsules(profileId, fallbackProfile, excludedCapsuleId)
        val movedTabIds = WebViewProfileRules.tabIdsForProfileDeletion(tabs, profileId)
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
        permissionRepository.removeProfile(profileId)
        permissionRevision++
        val webViewProfileName = WebViewProfileRules.isolatedProfileName(profileId)
        clearExistingWebViewProfileData(webViewProfileName)
        clearProfileServiceWorkerClient(webViewProfileName)
        val movedTabs = WebViewProfileRules.moveTabs(
            tabs = tabs,
            sourceProfileId = profileId,
            targetProfileId = fallbackProfile.id,
        )
        val tabsRequiringWebViewRecreation =
            WebViewProfileRules.tabIdsRequiringWebViewRecreation(
                before = tabs,
                after = movedTabs,
                profiles = profiles,
                multiProfileSupported = isProfileIsolationSupported,
                incognitoProfileName = incognitoWebViewProfileName,
            )
        recreateWebViews(tabsRequiringWebViewRecreation)
        deleteOrScheduleWebViewProfile(webViewProfileName)
        tabs.clear()
        tabs += movedTabs
        movedTabIds.forEach { tabId ->
            updateProtectionRequestContext(tabId, pageUrls[tabId])
            webViews[tabId]?.let { webView ->
                webView.evaluateJavascript(ForcedVerticalScrollScript.cleanupScript, null)
                reloadTabWithProtection(tabId)
            }
        }
        profiles.removeAt(profileIndex)
        if (profileId == activeProfileId) activeProfileId = fallbackProfile.id
        val fallbackTabs = tabs.filter { it.profileId == fallbackProfile.id }
        replaceProfileTabs(fallbackProfile.id, TabPinningRules.orderedTabs(fallbackTabs))
        val fallbackSelection = selectedTabId.takeIf { selectedId ->
            tabs.any { it.id == selectedId && it.profileId == fallbackProfile.id }
        } ?: fallbackProfile.selectedTabId?.takeIf { selectedId ->
            tabs.any { it.id == selectedId && it.profileId == fallbackProfile.id }
        } ?: activeTabs.first().id
        if (activeProfileId == fallbackProfile.id) {
            selectedTabId = fallbackSelection
            rememberSelectedTab(fallbackProfile.id, fallbackSelection)
        }
        reconcileCandyTrailForks(System.currentTimeMillis())
        persist()
        return true
    }

    fun moveTabToProfile(tabId: String, profileId: String): Boolean {
        val sourceTab = tabs.firstOrNull { it.id == tabId } ?: return false
        if (sourceTab.profileId == profileId || profiles.none { it.id == profileId }) return false
        if (sourceTab.profileId == activeProfileId && activeTabs.size == 1 && tabs.size >= MAX_TABS) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        val sourceIndex = activeTabs.indexOfFirst { it.id == tabId }
        val oldAssignment = profileAssignmentFor(sourceTab)
        val movedTab = sourceTab.copy(profileId = profileId, blockedCount = 0)
        val newAssignment = profileAssignmentFor(movedTab)
        if (tabId == selectedTabId) {
            clearPermissionActivity(tabId)
            webViews[tabId]?.let(::pauseWebView)
        }
        clearPrivacyDataForTab(tabId)
        if (oldAssignment != newAssignment) recreateWebViews(setOf(tabId))
        updateTab(tabId) { movedTab }
        updateProtectionRequestContext(tabId, pageUrls[tabId])
        webViews[tabId]?.let { webView ->
            webView.evaluateJavascript(ForcedVerticalScrollScript.cleanupScript, null)
            reloadTabWithProtection(tabId)
        }
        replaceProfileTabs(
            profileId,
            TabPinningRules.orderedTabs(tabs.filter { it.profileId == profileId }),
        )
        if (tabId == selectedTabId) {
            selectedTabId = activeTabs.getOrNull(sourceIndex.coerceAtMost(activeTabs.lastIndex))?.id
                ?: newTabState(isIncognito = sourceTab.isIncognito).also(tabs::add).id
            touchTab(selectedTabId, System.currentTimeMillis())
            rememberSelectedTab(activeProfileId, selectedTabId)
        }
        reconcileCandyTrailForks(System.currentTimeMillis())
        persist()
        return true
    }

    fun downloadContextImage() {
        val target = contentActions.target ?: return
        val imageUrl = target.imageUrl ?: return
        val selectedWebView = webViews[selectedTabId]
        val action = target.downloadImageAction(
            userAgent = selectedWebView?.settings?.userAgentString,
            cookies = cookiesFor(selectedTabId, imageUrl),
        ) ?: return
        val result = downloadManager.enqueue(action.request)
        contentActions.reportDownload(result)
        contentActions.dismiss()
        showDownloadResult(result)
    }

    fun openContextLinkInBackground() {
        val url = contentActions.target?.openLinkInBackgroundAction()?.url ?: return
        createBackgroundTab(url)
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

    fun openSelectedPageExternally() {
        val url = selectedTab.url
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

    fun summarizeSelectedPageWithAssistant() {
        val tab = selectedTab
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

    fun shareSelectedPage() {
        val tab = selectedTab
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

    fun printSelectedPage() {
        val tab = selectedTab
        if (tab.url == BLANK_URL) return
        val webView = webViews[tab.id]
        val printManager = activity.getSystemService(PrintManager::class.java)
        if (webView == null || printManager == null) {
            showPrintingUnavailable()
            return
        }
        val jobName = tab.title.trim().takeIf(String::isNotEmpty)
            ?: AddressResolver.displayText(tab.url).takeIf(String::isNotBlank)
            ?: activity.getString(R.string.app_name)
        runCatching {
            printManager.print(
                jobName,
                webView.createPrintDocumentAdapter(jobName),
                null,
            )
        }.onFailure {
            showPrintingUnavailable()
        }
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
        pruneStaleTabs(nowMillis)
        if (tabId == selectedTabId) {
            persist()
            return
        }
        clearPermissionActivity(selectedTabId)
        webViews[selectedTabId]?.let(::pauseWebView)
        selectedTabId = tabId
        rememberSelectedTab(activeProfileId, tabId)
        persist()
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
        cancelPendingPreviewCapture()
        if (dirtyPreviewTabId == tab.id) dirtyPreviewTabId = null
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
            )
        }
        if (wasLastIncognitoTab) clearIncognitoProfile()
        reconcileCandyTrailForks(System.currentTimeMillis())
        webViewRevision++
        persist()
        return true
    }

    fun closeTab(tabId: String) {
        val nowMillis = System.currentTimeMillis()
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val closingTab = tabs[index]
        if (!TabDeletionRules.canDelete(closingTab)) return
        if (activeCapsuleTabId == tabId) leaveSiteCapsule()
        val closesLastIncognitoTab =
            closingTab.isIncognito && tabs.count(BrowserTab::isIncognito) == 1
        if (closesLastIncognitoTab) prepareIncognitoProfileForRemoval()
        val profileIndex = activeTabs.indexOfFirst { it.id == tabId }
        val popupOpenerId = popupOpeners.remove(tabId)
        removeTabResources(tabId)
        tabs.removeAt(index)
        if (selectedTabId == tabId) {
            selectedTabId = popupOpenerId
                ?.takeIf { openerId -> activeTabs.any { it.id == openerId } }
                ?: activeTabs.getOrNull(profileIndex.coerceAtMost(activeTabs.lastIndex))?.id
                ?: newTabState(
                    nowMillis = nowMillis,
                    isIncognito = closingTab.isIncognito,
                ).also(tabs::add).id
            touchTab(selectedTabId, nowMillis)
            rememberSelectedTab(activeProfileId, selectedTabId)
        }
        if (closingTab.isIncognito && tabs.none(BrowserTab::isIncognito)) {
            clearIncognitoProfile()
        }
        reconcileCandyTrailForks(nowMillis)
        persist()
    }

    fun setTabPinned(tabId: String, isPinned: Boolean): Boolean {
        val updatedTabs = TabPinningRules.withPinnedState(
            tabs = activeTabs,
            tabId = tabId,
            isPinned = isPinned,
        )
        if (updatedTabs == activeTabs) return false
        replaceProfileTabs(activeProfileId, updatedTabs)
        persist()
        return true
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
        webViews[selectedTabId]?.let(::pauseWebView)
        tabs += destinationTab
        setCandyTrail(originTab, forkedTrail)
        selectedTabId = destinationTab.id
        rememberSelectedTab(activeProfileId, destinationTab.id)
        bottomBarCompactStates[destinationTab.id] = false
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
        webViews[selectedTabId]?.let(::pauseWebView)
        tabs += destinationTab
        setCandyTrail(originTab, reopenedTrail)
        selectedTabId = destinationTab.id
        rememberSelectedTab(activeProfileId, destinationTab.id)
        bottomBarCompactStates[destinationTab.id] = false
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

        val existingWebView = webViews[tabId]
        if (existingWebView == null) {
            updateTab(tabId) { it.copy(url = node.url, title = node.title, isLoading = true, progress = 0) }
            webViewFor(tabId, initialUrlOverride = node.url)
            return true
        }
        val binding = candyTrailHistoryBindings[tabId] ?: CandyTrailHistoryBinding()
        val targetIndex = CandyTrailHistoryReconciler.indexOfNode(binding, nodeId)
        val delta = targetIndex?.minus(binding.currentIndex)
        if (delta != null && delta != 0) {
            existingWebView.goBackOrForward(delta)
        } else if (delta == null || existingWebView.url != node.url) {
            applyMediaPlaybackPolicy(tabId, existingWebView)
            existingWebView.loadUrl(node.url)
        } else {
            pendingCandyTrailTargets.remove(tabId)
        }
        return true
    }

    fun goBack() {
        val webView = webViews[selectedTabId]?.takeIf(WebView::canGoBack) ?: return
        val history = webView.copyBackForwardList()
        val targetUrl = history.getItemAtIndex(history.currentIndex - 1)?.url
        val capsule = activeCapsuleForTab(selectedTabId)
        if (capsule != null && targetUrl != null &&
            CapsuleNavigationRules.decide(capsule, targetUrl) ==
            CapsuleNavigationDecision.OpenInFullCandy
        ) {
            leaveSiteCapsule()
        }
        webView.goBack()
    }
    fun goForward() {
        val webView = webViews[selectedTabId]?.takeIf(WebView::canGoForward) ?: return
        val binding = candyTrailHistoryBindings[selectedTabId]
        binding?.entries?.getOrNull(binding.currentIndex + 1)?.nodeId?.let { targetNodeId ->
            pendingCandyTrailTargets[selectedTabId] = targetNodeId
        }
        webView.goForward()
    }
    fun reload() {
        updateTab(selectedTabId) { it.copy(isLoading = true, progress = 0, error = null) }
        webViewFor(selectedTabId).reload()
    }

    fun retryFailedPage(): Boolean {
        val tabId = selectedTabId
        if (selectedTab.error == null || selectedTab.isLoading) return false
        val webView = webViews[tabId]
        if (webView == null) {
            webViewFor(tabId)
        } else {
            webView.reload()
        }
        return true
    }

    fun stopLoading() {
        webViews[selectedTabId]?.stopLoading()
        updateTab(selectedTabId) { it.copy(isLoading = false) }
    }

    fun clearCacheAndReload(): Boolean {
        val tabId = selectedTabId
        if (selectedTab.url == BLANK_URL) return false
        val webView = webViewFor(tabId)
        updateTab(tabId) { it.copy(isLoading = true, progress = 0, error = null) }
        WebViewCommandActions.clearCacheAndReload(webView)
        return true
    }

    fun clearCookiesAndReload(onComplete: (Boolean) -> Unit): Boolean {
        val tabId = selectedTabId
        if (selectedTab.url == BLANK_URL) return false
        val webView = webViewFor(tabId)
        val cookieManager = WebViewProfileCookies.managerFor(webView) ?: return false
        val navigationGeneration = navigationGenerations[tabId]
        val capturedUrl = webView.url
        var reloaded = false
        WebViewCommandActions.clearCookiesAndReload(
            cookieManager = cookieManager,
            webView = webView,
            shouldReload = {
                val unchanged = tabs.any { it.id == tabId } &&
                    webViews[tabId] === webView &&
                    navigationGenerations[tabId] == navigationGeneration &&
                    webView.url == capturedUrl
                if (unchanged) {
                    updateTab(tabId) { it.copy(isLoading = true, progress = 0, error = null) }
                }
                reloaded = unchanged
                unchanged
            },
            onComplete = { onComplete(reloaded) },
        )
        return true
    }

    val commandCookieScope: CommandCookieScope
        get() = when {
            !isProfileIsolationSupported -> CommandCookieScope.AllWebViews
            else -> when (profileAssignmentFor(selectedTab)) {
                WebViewProfileAssignment.Default -> CommandCookieScope.SharedRegularProfile
                is WebViewProfileAssignment.Incognito -> CommandCookieScope.PrivateProfile
                is WebViewProfileAssignment.Isolated -> CommandCookieScope.IsolatedRegularProfile
            }
        }

    fun addressSuggestionItems(
        query: String,
        searchQueries: List<String> = emptyList(),
        limit: Int = 10,
    ): List<AddressSuggestionItem> {
        val duplicateTabIds = TabDuplicateRules.tabIdsToClose(activeTabs, selectedTabId)
        val expiredTabCount = TabRetentionRules.expiredTabIds(
            tabs = tabs,
            selectedTabId = selectedTabId,
            lifetime = inactiveTabLifetime,
            nowMillis = System.currentTimeMillis(),
        ).size
        val canCreateTab = tabs.size - expiredTabCount < MAX_TABS
        val canMoveSelectedTab = activeTabs.size > 1 || canCreateTab
        val definitions = BrowserCommandRegistry.commands(
            CommandContext(
                selectedTab = selectedTab,
                profiles = profiles,
                activeProfileId = activeProfileId,
                duplicateTabIds = duplicateTabIds,
                canCreateTab = canCreateTab,
                canCreateIncognitoTab = canCreateTab && isProfileIsolationSupported,
                canMoveSelectedTab = canMoveSelectedTab,
                hasLoadedPage = selectedTab.url != BLANK_URL,
                canClearCookies = webViews[selectedTabId]
                    ?.let(WebViewProfileCookies::managerFor) != null,
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
            limit = if (CommandMatcher.isExplicitCommandQuery(query)) definitions.size else limit,
        )
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
            history = history,
            tabs = activeTabs,
            selectedTabId = selectedTabId,
            isIncognito = selectedTab.isIncognito,
            query = query,
            limit = limit,
        )

    fun addressDomainCompletion(query: String): String? = BrowsingLibraryRules.domainCompletion(
        history = history,
        favorites = favorites,
        tabs = activeTabs,
        selectedTabId = selectedTabId,
        isIncognito = selectedTab.isIncognito,
        query = query,
    )

    val isSelectedTabFavorite: Boolean
        get() = !selectedTab.isIncognito && BrowsingLibraryRules.isFavorite(favorites, selectedTab.url)

    fun isFavorite(url: String): Boolean = BrowsingLibraryRules.isFavorite(favorites, url)

    fun toggleFavorite(tabId: String = selectedTabId): FavoriteMutation? {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        if (tab.isIncognito || tab.url == BLANK_URL) return null
        val before = favorites.toList()
        val wasFavorite = BrowsingLibraryRules.isFavorite(favorites, tab.url)
        val updated = BrowsingLibraryRules.toggleFavorite(
            current = favorites,
            entry = FavoriteEntry(
                url = tab.url,
                title = tab.title,
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
            current = favorites,
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
        collapseBottomBar()
        isAddressBarDocked = docked
        store.saveAddressBarDocked(docked)
    }

    fun updateWebContentEdgeToEdgeEnabled(enabled: Boolean) {
        if (isWebContentEdgeToEdgeEnabled == enabled) return
        isWebContentEdgeToEdgeEnabled = enabled
        store.saveWebContentEdgeToEdgeEnabled(enabled)
        lastWindowInsets?.let(::dispatchWindowInsetsToAttachedWebViews)
    }

    fun prepareTabOverview(onReady: () -> Unit) {
        pruneStaleTabs()
        // Opening the switcher must not wait for a GPU readback. Page commits and
        // scroll-idle capture keep this cache warm before the gesture starts.
        onReady()
    }

    fun setPreviewCaptureEnabled(enabled: Boolean) {
        previewCaptureEnabled = enabled
    }

    fun setPreviewContentBottomInWindowPx(bottomPx: Int) {
        previewContentBottomInWindowPx = bottomPx.takeIf { it > 0 }
    }

    fun previewTopInsetPx(tabId: String): Int {
        if (drawsEdgeToEdge(tabId)) return 0
        return lastWindowInsets
            ?.getInsets(SAFE_AREA_INSET_TYPES)
            ?.top
            ?.coerceAtLeast(0)
            ?: (webViews[tabId]?.layoutParams as? FrameLayout.LayoutParams)
                ?.topMargin
                ?.coerceAtLeast(0)
            ?: 0
    }

    fun updateBlockerSettings(settings: BlockerSettings) {
        val cookieConsentSettingChanged = workerSettings.hideCookieConsent != settings.hideCookieConsent
        val requestFilterSettingChanged =
            workerSettings.blockAdsAndTrackers != settings.blockAdsAndTrackers
        blockerSettings = settings
        workerSettings = settings
        store.saveBlockerSettings(settings)
        webViews.forEach { (tabId, webView) ->
            applyCookiePolicy(tabId, webView, pageUrls[tabId])
        }
        if (cookieConsentSettingChanged) {
            webViews.forEach { (tabId, webView) ->
                if (!settings.hideCookieConsent ||
                    !isCookieBannerRemovalEnabled(tabId, pageUrls[tabId])
                ) {
                    webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
                } else {
                    injectCookieConsentCss(tabId, webView)
                }
            }
        }
        if (requestFilterSettingChanged) {
            webViews.forEach { (tabId, webView) ->
                webView.evaluateJavascript(CandyCosmeticScript.cleanupScript, null)
                if (settings.blockAdsAndTrackers) {
                    installCosmeticDocumentStartScripts(tabId, webView)
                    injectCandyCosmeticFallback(tabId, webView, pageUrls[tabId] ?: webView.url)
                } else {
                    removeCosmeticDocumentStartScripts(webView)
                }
            }
        }
        if (cookieConsentSettingChanged && !settings.hideCookieConsent) {
            webViews.forEach { (tabId, webView) ->
                updateTab(tabId) { it.copy(isLoading = true, progress = 0, error = null) }
                webView.reload()
            }
        } else {
            reload()
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
        updateSitePrivacyOverrides(tabId) { current ->
            current.copy(cookieBannerRemovalDisabled = disabled)
        }

    fun setForceVerticalScrolling(tabId: String, enabled: Boolean): Boolean =
        updateSitePrivacyOverrides(tabId) { current ->
            current.copy(forceVerticalScrolling = enabled)
        }

    private fun updateSitePrivacyOverrides(
        tabId: String,
        transform: (SitePrivacyOverrides) -> SitePrivacyOverrides,
    ): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val host = PrivacyRequestSanitizer.webHost(pageUrls[tabId] ?: tab.url) ?: return false
        val current = sitePrivacyOverridesFor(tab)[host] ?: SitePrivacyOverrides()
        val updated = transform(current)
        if (updated == current) return false

        val affectedTabIds = linkedSetOf(tabId)
        if (tab.isIncognito) {
            val byHost = SitePrivacyOverrideRules.withOverride(
                temporarySitePrivacyOverrides[tabId].orEmpty(),
                host,
                updated,
            )
            if (byHost.isEmpty()) temporarySitePrivacyOverrides.remove(tabId)
            else temporarySitePrivacyOverrides[tabId] = byHost
            webViews[tabId]?.let { webView ->
                installForcedVerticalScrollDocumentStartScript(tabId, webView)
            }
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
            tabs.asSequence()
                .filter { candidate -> candidate.profileId == tab.profileId && !candidate.isIncognito }
                .forEach { candidate ->
                    val candidateHost = PrivacyRequestSanitizer.webHost(
                        pageUrls[candidate.id] ?: candidate.url,
                    )
                    if (candidateHost == host) affectedTabIds += candidate.id
                    webViews[candidate.id]?.let { webView ->
                        installForcedVerticalScrollDocumentStartScript(candidate.id, webView)
                    }
                }
        }
        siteExceptionRevision++
        affectedTabIds.forEach { affectedTabId ->
            webViews[affectedTabId]
                ?.evaluateJavascript(ForcedVerticalScrollScript.cleanupScript, null)
            if (affectedTabId == tabId || affectedTabId in webViews) {
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

    fun updateSearchEngine(engine: SearchEngine) {
        searchEngine = engine
        store.saveSearchEngine(engine)
    }

    fun updateSearchSuggestionProvider(provider: SearchSuggestionProvider) {
        searchSuggestionProvider = provider
        store.saveSearchSuggestionProvider(provider)
    }

    fun updateDismissResistancePercent(percent: Int) {
        dismissResistancePercent = percent.coerceIn(10, 90)
        store.saveDismissResistancePercent(dismissResistancePercent)
    }

    fun updateTabOverviewMode(mode: TabOverviewMode) {
        tabOverviewMode = mode
        store.saveTabOverviewMode(mode)
    }

    fun setSelectedDomainMuted(muted: Boolean): Boolean {
        if (!isDomainMuteSupported) return false
        val tab = selectedTab
        val domain = DomainMuteRules.domainForUrl(tab.url) ?: return false
        if (isDomainMuted(tab, tab.url) == muted) return false
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

    fun clearBrowsingData() {
        cancelPendingPermissionAccess()
        cancelPendingFileChooser()
        activePermissions.clear()
        permissionRepository.clearAll()
        permissionRevision++
        val regularForcedScrollTabIds = tabs.asSequence()
            .filterNot(BrowserTab::isIncognito)
            .filter { tab ->
                val host = PrivacyRequestSanitizer.webHost(pageUrls[tab.id] ?: tab.url)
                host != null && isForcedVerticalScrolling(tab, host)
            }
            .map(BrowserTab::id)
            .toSet()
        cancelPendingPreviewCapture()
        dirtyPreviewTabId = null
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
        clearAllWebViewProfileData()
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
        siteExceptionRevision++
        webViews.forEach { (tabId, webView) ->
            val pageUrl = pageUrls[tabId] ?: tabs.firstOrNull { it.id == tabId }?.url
                ?: BLANK_URL
            installForcedVerticalScrollDocumentStartScript(tabId, webView)
            applySiteProtectionForNavigation(tabId, webView, pageUrl)
            applyDomainMutePolicy(tabId, webView, pageUrl)
        }
        val incognitoTabIds = tabs.asSequence()
            .filter(BrowserTab::isIncognito)
            .map(BrowserTab::id)
            .toList()
        if (incognitoTabIds.isNotEmpty()) prepareIncognitoProfileForRemoval()
        recreateWebViews(incognitoTabIds.toSet())
        clearIncognitoProfile()
        webViews.values.forEach {
            it.clearCache(true)
            it.clearFormData()
            it.clearHistory()
        }
        tabs.indices.forEach { index -> tabs[index] = tabs[index].copy(blockedCount = 0) }
        history.clear()
        store.saveHistory(emptyList())
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
        suppressedCandyTrailTabIds += tabs.map(BrowserTab::id)
        candyTrails.clear()
        candyTrailRepository.clear()
        regularForcedScrollTabIds.forEach { tabId ->
            webViews[tabId]?.let { webView ->
                webView.evaluateJavascript(ForcedVerticalScrollScript.cleanupScript, null)
                reloadTabWithProtection(tabId)
            }
        }
        Toast.makeText(
            activity,
            activity.getString(R.string.toast_browsing_data_cleared),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun onPause() {
        isActivityResumed = false
        touchTab(selectedTabId, System.currentTimeMillis())
        cancelPendingPreviewCapture()
        webViews.values.forEach(::pauseWebView)
        CookieManager.getInstance().flush()
        webViews.values.forEach { webView ->
            if (isProfileIsolationSupported) cookieManagerFor(webView).flush()
        }
        persist()
    }

    fun onResume() {
        isActivityResumed = true
        isDefaultBrowser = DefaultBrowserRole.isHeld(activity)
        val nowMillis = System.currentTimeMillis()
        pruneStaleTabs(nowMillis, persistChanges = false)
        touchTab(selectedTabId, nowMillis)
        persist()
        webViews[selectedTabId]?.let { resumeWebView(selectedTabId, it) }
    }

    fun onStart() {
        isActivityStarted = true
    }

    fun onStop() {
        isActivityStarted = false
        if (pendingPermissionAccess?.awaitingRuntime != true) cancelPendingPermissionAccess()
        activePermissions.clear()
        permissionRevision++
    }

    fun destroy() {
        destroyed = true
        cancelPendingPermissionAccess()
        cancelPendingFileChooser()
        fileChooserValidationExecutor.shutdownNow()
        activePermissions.clear()
        permissionRepository.clearPrivateSession()
        cancelPendingPreviewCapture()
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
        savePersistentFilterRules()
        persist()
        if (tabs.any(BrowserTab::isIncognito)) prepareIncognitoProfileForRemoval()
        configuredServiceWorkerProfiles.toList().forEach(::clearProfileServiceWorkerClient)
        webViews.values.forEach(::destroyWebView)
        webViews.clear()
        webViewProfileKeys.clear()
        forcedVerticalScrollScriptHandlers.clear()
        cosmeticScriptHandlers.clear()
        pendingConsentCssUrls.clear()
        edgeToEdgePages.clear()
        navigationGenerations.clear()
        clearIncognitoProfile()
        if (
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(null)
        }
        pageUrls.clear()
        configuredServiceWorkerProfiles.clear()
        popupOpeners.clear()
        bottomBarCompactStates.clear()
        previews.clear()
        favicons.clear()
        privacySnapshots.clear()
        faviconGenerations.clear()
        candyTrailEpoch++
        candyTrailHistoryBindings.clear()
        pendingCandyTrailTargets.clear()
        pendingCandyTrailRestoreIds.clear()
        suppressedCandyTrailTabIds.clear()
        candyTrails.clear()
        candyTrailGenerations.clear()
    }

    private fun webViewFor(tabId: String, initialUrlOverride: String? = null): WebView =
        webViews.getOrPut(tabId) {
        val tab = tabs.first { it.id == tabId }
        createWebView(tabId).also { webView ->
            val initialUrl = initialUrlOverride ?: tab.url
            if (initialUrl != BLANK_URL) {
                updateTab(tabId) { it.copy(isLoading = true, progress = 0, error = null) }
                webView.loadUrl(initialUrl)
            }
        }
    }

    private fun createWebView(tabId: String): WebView = WebView(activity).apply {
        val tab = tabs.first { it.id == tabId }
        val profileAssignment = profileAssignmentFor(tab)
        when (profileAssignment) {
            WebViewProfileAssignment.Default -> Unit
            is WebViewProfileAssignment.Incognito ->
                WebViewCompat.setProfile(this, profileAssignment.profileName)
            is WebViewProfileAssignment.Isolated ->
                WebViewCompat.setProfile(this, profileAssignment.profileName)
        }
        webViewProfileKeys[tabId] = profileAssignment.storageKey
        configureProfileServiceWorkerBlocking(profileAssignment, this)
        updateProtectionRequestContext(tabId, tab.url)
        edgeToEdgePages[tabId] = false
        navigationGenerations[tabId] = 0
        addJavascriptInterface(ViewportFitBridge(tabId, this), PageViewportFit.bridgeName)
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        setBackgroundColor(if (nightMode == Configuration.UI_MODE_NIGHT_YES) Color.BLACK else Color.WHITE)
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            setGeolocationEnabled(true)
            enablePinchZoom()
            safeBrowsingEnabled = true
        }
        applyMediaPlaybackPolicy(tabId, this)
        applyDomainMutePolicy(tabId, this, tab.url)
        SystemWebViewCredentials.configure(this)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        }
        val configuredWebView = this
        cookieManagerFor(this).setAcceptCookie(true)
        applyCookiePolicy(tabId, configuredWebView, tab.url)
        webViewClient = browserWebViewClient(tabId)
        webChromeClient = browserChromeClient(tabId)
        setDownloadListener(downloadListener(tabId))
        installForcedVerticalScrollDocumentStartScript(tabId, this)
        installCosmeticDocumentStartScripts(tabId, this)
        setOnLongClickListener { clickedView ->
            val webView = clickedView as? WebView ?: return@setOnLongClickListener false
            val hit = webView.hitTestResult
            if (!WebViewHitTestResolver.supports(hit.type)) {
                return@setOnLongClickListener false
            }
            val handler = Handler(Looper.getMainLooper()) { message ->
                WebViewHitTestResolver.resolve(
                    hitType = hit.type,
                    extra = hit.extra,
                    focusedLinkUrl = message.data.getString("url"),
                    focusedImageUrl = message.data.getString("src"),
                )?.let(contentActions::show)
                true
            }
            webView.requestFocusNodeHref(handler.obtainMessage())
            true
        }
        val density = resources.displayMetrics.density
        val collapseThreshold = 24f * density
        val expandThreshold = 16f * density
        var accumulatedDistance = 0f
        var previousDirection = 0
        setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (tabId != selectedTabId) return@setOnScrollChangeListener
            schedulePreviewCapture(tabId)
            if (scrollY <= 0) {
                accumulatedDistance = 0f
                previousDirection = 0
                bottomBarCompactStates[tabId] = false
                return@setOnScrollChangeListener
            }

            val delta = scrollY - oldScrollY
            val direction = delta.compareTo(0)
            if (direction == 0) return@setOnScrollChangeListener
            if (direction != previousDirection) accumulatedDistance = 0f
            previousDirection = direction
            accumulatedDistance += kotlin.math.abs(delta.toFloat())
            val threshold = if (direction > 0) collapseThreshold else expandThreshold
            if (accumulatedDistance >= threshold) {
                val compact = direction > 0
                if (bottomBarCompactStates[tabId] != compact) {
                    bottomBarCompactStates[tabId] = compact
                }
                accumulatedDistance = 0f
            }
        }
    }

    private fun activeCapsuleForTab(tabId: String): SiteCapsule? = activeSiteCapsule
        ?.takeIf { activeCapsuleTabId == tabId && selectedTabId == tabId }

    private fun openCapsuleTargetInFullCandy(tabId: String, view: WebView, targetUrl: String) {
        if (activeCapsuleTabId != tabId) return
        leaveSiteCapsule()
        val previousTabId = selectedTabId
        if (createTab(targetUrl, isIncognito = false) == previousTabId) {
            applyMediaPlaybackPolicy(tabId, view)
            view.loadUrl(targetUrl)
        }
    }

    private fun browserWebViewClient(tabId: String) = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            clearPermissionActivity(tabId)
            val capsule = activeCapsuleForTab(tabId)
            if (capsule != null &&
                CapsuleNavigationRules.decide(capsule, url) ==
                CapsuleNavigationDecision.OpenInFullCandy
            ) {
                view.stopLoading()
                openCapsuleTargetInFullCandy(tabId, view, url)
                return
            }
            pageUrls[tabId] = url
            applyDomainMutePolicy(tabId, view, url)
            updateProtectionRequestContext(tabId, url)
            applySiteProtectionForNavigation(tabId, view, url)
            navigationGenerations[tabId] = (navigationGenerations[tabId] ?: 0) + 1
            suppressedCandyTrailTabIds.remove(tabId)
            setPageEdgeToEdge(tabId, view, false)
            val previousUrl = tabs.firstOrNull { it.id == tabId }?.url
            if (previousUrl != null && FaviconRules.changedSite(previousUrl, url)) {
                invalidateFavicon(tabId)
            }
            favicon?.let { storeFavicon(tabId, it) }
            bottomBarCompactStates[tabId] = false
            updateTab(tabId) {
                it.copy(url = url, isLoading = true, progress = 0, error = null)
            }
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            detectPageEdgeToEdge(tabId, view)
            injectCookieConsentCss(tabId, view, url)
            injectForcedVerticalScrollFallback(tabId, view, url)
            injectCandyCosmeticFallback(tabId, view, url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            pageUrls[tabId] = url
            updateNavigationState(tabId, view)
            val title = view.title?.takeIf(String::isNotBlank) ?: AddressResolver.displayText(url)
            updateTab(tabId) {
                it.copy(
                    url = url,
                    title = title,
                    isLoading = false,
                    progress = 100,
                )
            }
            recordHistory(tabId, url, title)
            if (view.url == url && pageUrls[tabId] == url) {
                updateCandyTrailPage(tabId, url, title)
            }
            detectPageEdgeToEdge(tabId, view)
            view.postVisualStateCallback(
                System.nanoTime(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        captureVisiblePreview(tabId)
                    }
                },
            )
            persist()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            val visibleUrl = url?.takeIf(String::isNotBlank)
                ?: view.url?.takeIf(String::isNotBlank)
            if (visibleUrl != null) {
                pageUrls[tabId] = visibleUrl
                updateTab(tabId) { tab -> WebViewProfileRules.withVisibleUrl(tab, visibleUrl) }
            }
            updateNavigationState(tabId, view)
            reconcileCandyTrailHistory(tabId, view, isReload)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (request.isForMainFrame || !workerSettings.blockAdsAndTrackers) return null
            if (request.url.scheme?.lowercase() !in WEB_SCHEMES) return null
            val requestContext = protectionRequestContexts[tabId] ?: return null
            val pageUrl by lazy(LazyThreadSafetyMode.NONE) {
                requestContext.pageHost?.let { host -> "https://$host" }
            }
            val sitePaused = isSiteProtectionPaused(tabId, null)
            if (sitePaused) return null
            val matcher = matcherFor(requestContext.isIncognito)
            val requestUrl by lazy(LazyThreadSafetyMode.NONE) { request.url.toString() }
            val candyDecision = if (matcher.hasRequestRules) {
                matcher.decideHosts(
                    requestHost = request.url.host,
                    pageHost = requestContext.pageHost,
                    profileId = requestContext.profileId,
                    isForMainFrame = false,
                )
            } else {
                null
            }
            if (candyDecision != null) {
                queueCandyRuleDecision(tabId, requestUrl, pageUrl, requestContext, candyDecision)
                return if (candyDecision.action == CandyDecisionAction.Block) {
                    blockedResponse()
                } else {
                    null
                }
            }
            val listedRequest = contentBlocker.shouldBlockHosts(
                requestHost = request.url.host,
                pageHost = requestContext.pageHost,
            )
            if (RequestProtectionRules.shouldBlock(
                    isForMainFrame = false,
                    blockerEnabled = true,
                    sitePaused = false,
                    isListedRequest = listedRequest,
                )
            ) {
                queueBlockedRequest(
                    tabId,
                    requestUrl,
                    pageUrl,
                    requestContext,
                    PrivacyRuleDecisionSummary(
                        ruleId = null,
                        label = activity.getString(R.string.filter_rule_builtin),
                        action = PrivacyRuleDecisionAction.Block,
                    ),
                )
                return blockedResponse()
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                val capsule = activeCapsuleForTab(tabId)
                    ?.takeIf { request.isForMainFrame }
                if (capsule == null) return false
                return when (CapsuleNavigationRules.decide(capsule, request.url.toString())) {
                    CapsuleNavigationDecision.StayInCapsule -> false
                    CapsuleNavigationDecision.OpenInFullCandy -> {
                        mainHandler.post {
                            openCapsuleTargetInFullCandy(tabId, view, request.url.toString())
                        }
                        true
                    }
                    CapsuleNavigationDecision.UseExistingUriPolicy -> false
                }
            }
            if (!request.isForMainFrame || !request.hasGesture()) return true
            return when (val result = externalApps.open(request.url)) {
                ExternalLaunchResult.Launched -> true
                is ExternalLaunchResult.OpenInBrowser -> {
                    applyMediaPlaybackPolicy(tabId, view)
                    view.loadUrl(result.url)
                    true
                }
                ExternalLaunchResult.Unsupported -> {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_no_matching_app),
                        Toast.LENGTH_SHORT,
                    ).show()
                    true
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                updateTab(tabId) {
                    it.copy(isLoading = false, error = error.description.toString())
                }
            }
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: android.net.http.SslError,
        ) {
            handler.cancel()
            updateTab(tabId) {
                it.copy(isLoading = false, error = activity.getString(R.string.error_unsafe_tls_blocked))
            }
        }

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponse,
        ) {
            callback.backToSafety(true)
            updateTab(tabId) {
                it.copy(isLoading = false, error = activity.getString(R.string.error_unsafe_site_blocked))
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            clearPermissionActivity(tabId)
            clearServiceWorkerClientsLosingLastWebView(setOf(tabId))
            webViews.remove(tabId)
            webViewProfileKeys.remove(tabId)
            removeForcedVerticalScrollDocumentStartScript(view)
            removeCosmeticDocumentStartScripts(view)
            edgeToEdgePages.remove(tabId)
            navigationGenerations.remove(tabId)
            candyTrailHistoryBindings.remove(tabId)
            pendingCandyTrailTargets.remove(tabId)
            (view.parent as? FrameLayout)?.removeView(view)
            view.destroy()
            webViewRevision++
            updateTab(tabId) {
                it.copy(
                    isLoading = false,
                    error = if (detail.didCrash()) {
                        activity.getString(R.string.error_renderer_crashed)
                    } else {
                        activity.getString(R.string.error_renderer_terminated)
                    },
                )
            }
            return true
        }
    }

    private fun injectCookieConsentCss(tabId: String, view: WebView, committedUrl: String? = null) {
        val pageUrl = committedUrl ?: pageUrls[tabId] ?: view.url
        if (!isCookieBannerRemovalEnabled(tabId, pageUrl)) return
        val readyScript = contentBlocker.consentScriptIfReady()
        if (readyScript != null) {
            pendingConsentCssUrls.remove(tabId)
            view.evaluateJavascript(readyScript, null)
            return
        }

        val alreadyPending = pendingConsentCssUrls.containsKey(tabId)
        pendingConsentCssUrls[tabId] = pageUrl
        if (alreadyPending) return
        contentBlocker.onConsentScriptReady { script ->
            mainHandler.post {
                val expectedUrl = pendingConsentCssUrls.remove(tabId)
                val currentView = webViews[tabId] ?: return@post
                val currentUrl = pageUrls[tabId] ?: currentView.url
                val expectedHost = expectedUrl?.let(PrivacyRequestSanitizer::webHost)
                val currentHost = currentUrl?.let(PrivacyRequestSanitizer::webHost)
                if (expectedHost != null && expectedHost == currentHost &&
                    isCookieBannerRemovalEnabled(tabId, currentUrl)
                ) {
                    currentView.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun injectForcedVerticalScrollFallback(tabId: String, view: WebView, pageUrl: String?) {
        if (view in forcedVerticalScrollScriptHandlers) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val host = PrivacyRequestSanitizer.webHost(pageUrl ?: tab.url) ?: return
        if (!isForcedVerticalScrolling(tab, host)) return
        ForcedVerticalScrollScript.create(forcedVerticalScrollHostsForTab(tabId))
            .takeIf(String::isNotEmpty)
            ?.let { script -> view.evaluateJavascript(script, null) }
    }

    private fun injectCandyCosmeticFallback(tabId: String, view: WebView, pageUrl: String?) {
        if (!workerSettings.blockAdsAndTrackers || isSiteProtectionPaused(tabId, pageUrl)) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val selectors = contentBlocker.adCosmeticSelectors(pageUrl) + matcherFor(tab.isIncognito)
            .cosmeticRules(pageUrl ?: return, tab.profileId)
            .mapNotNull(CandyRule::cosmeticSelector)
        val script = CandyCosmeticScript.create(selectors)
        if (script.isNotEmpty()) view.evaluateJavascript(script, null)
    }

    private fun installCosmeticDocumentStartScripts(tabId: String, view: WebView) {
        removeCosmeticDocumentStartScripts(view)
        if (!workerSettings.blockAdsAndTrackers ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val handlers = buildList {
            val bundledScript = CandyCosmeticScript.createScoped(
                rules = contentBlocker.adCosmeticRules,
                pausedHosts = siteExceptionHostsForTab(tabId),
            )
            if (bundledScript.isNotEmpty()) {
                runCatching {
                    WebViewCompat.addDocumentStartJavaScript(
                        view,
                        bundledScript,
                        ALL_WEB_ORIGINS,
                    )
                }.getOrNull()?.let(::add)
            }
            addAll(
                matcherFor(tab.isIncognito).rules.asSequence()
                    .filter { rule ->
                        rule.active && rule.kind == CandyRuleKind.CosmeticCss &&
                            (rule.profileId == null || rule.profileId == tab.profileId)
                    }
                    .sortedBy(CandyRule::id)
                    .take(MAX_COSMETIC_DOCUMENT_START_RULES)
                    .mapNotNull { rule ->
                        val host = rule.firstPartyHost ?: return@mapNotNull null
                        val selector = rule.cosmeticSelector ?: return@mapNotNull null
                        runCatching {
                            WebViewCompat.addDocumentStartJavaScript(
                                view,
                                CandyCosmeticScript.create(
                                    listOf(selector),
                                    siteExceptionHostsForTab(tabId),
                                ),
                                setOf(
                                    "https://$host",
                                    "https://*.$host",
                                    "http://$host",
                                    "http://*.$host",
                                ),
                            )
                        }.getOrNull()
                    }
                    .toList(),
            )
        }
        if (handlers.isNotEmpty()) cosmeticScriptHandlers[view] = handlers
    }

    private fun removeCosmeticDocumentStartScripts(view: WebView) {
        cosmeticScriptHandlers.remove(view).orEmpty().forEach { handler ->
            runCatching(handler::remove)
        }
    }

    private fun installForcedVerticalScrollDocumentStartScript(tabId: String, view: WebView) {
        removeForcedVerticalScrollDocumentStartScript(view)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val script = ForcedVerticalScrollScript.create(forcedVerticalScrollHostsForTab(tabId))
        if (script.isEmpty()) return
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(view, script, ALL_WEB_ORIGINS)
        }.getOrNull()?.let { handler -> forcedVerticalScrollScriptHandlers[view] = handler }
    }

    private fun removeForcedVerticalScrollDocumentStartScript(view: WebView) {
        forcedVerticalScrollScriptHandlers.remove(view)?.let { handler ->
            runCatching(handler::remove)
        }
    }

    private fun handleWebPermissionRequest(tabId: String, request: PermissionRequest) {
        if (pendingPermissionAccess != null) {
            request.deny()
            return
        }
        val origin = PermissionOrigin.normalize(request.origin.toString())
        val identity = permissionRequestIdentity(tabId, origin)
        if (identity == null || !isPermissionRequestCurrent(identity)) {
            request.deny()
            return
        }
        val resourcesByPermission = request.resources
            .mapNotNull { resource ->
                sitePermissionForWebResource(resource)?.let { permission -> permission to resource }
            }
            .groupBy({ it.first }, { it.second })
        val requested = resourcesByPermission.keys
        if (requested.isEmpty()) {
            request.deny()
            return
        }
        val site = PermissionSiteKey(identity.profileId, identity.origin)
        beginPermissionAccess(
            identity = identity,
            site = site,
            requested = requested,
            kind = PendingPermissionKind.WebResource,
            requestToken = request,
            grant = { granted ->
                val resources = granted.flatMap { permission ->
                    resourcesByPermission[permission].orEmpty()
                }.distinct()
                if (resources.isEmpty()) request.deny() else request.grant(resources.toTypedArray())
            },
            deny = request::deny,
        )
    }

    private fun handleGeolocationPermissionRequest(
        tabId: String,
        rawOrigin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        if (pendingPermissionAccess != null) {
            callback.invoke(rawOrigin, false, false)
            return
        }
        val origin = PermissionOrigin.normalize(rawOrigin)
        val identity = permissionRequestIdentity(tabId, origin)
        if (identity == null || !isPermissionRequestCurrent(identity)) {
            callback.invoke(rawOrigin, false, false)
            return
        }
        beginPermissionAccess(
            identity = identity,
            site = PermissionSiteKey(identity.profileId, identity.origin),
            requested = setOf(SitePermission.Location),
            kind = PendingPermissionKind.Geolocation,
            requestToken = callback,
            grant = { granted ->
                callback.invoke(rawOrigin, SitePermission.Location in granted, false)
            },
            deny = { callback.invoke(rawOrigin, false, false) },
        )
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
    }

    private fun cancelPendingPermissionAccess(tabId: String? = null) {
        val pending = pendingPermissionAccess ?: return
        if (tabId != null && pending.identity.tabId != tabId) return
        pendingPermissionAccess = null
        permissionPrompt = null
        runCatching { pending.delivery.deny() }
        permissionRevision++
    }

    private fun dropCanceledPermissionAccess(requestToken: Any) {
        val pending = pendingPermissionAccess ?: return
        if (pending.requestToken !== requestToken) return
        pendingPermissionAccess = null
        permissionPrompt = null
        pending.delivery.drop()
        permissionRevision++
    }

    private fun clearPermissionActivity(tabId: String) {
        cancelPendingPermissionAccess(tabId)
        cancelPendingFileChooser(tabId)
        removeActivePermissionsForTab(tabId)
    }

    private fun removeActivePermissionsForTab(tabId: String) {
        val removed = activePermissions.dropTab(tabId)
        if (removed) permissionRevision++
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
            pageUrls[identity.tabId] ?: webViews[identity.tabId]?.url ?: tab?.url,
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
                tabExists = tab != null && webViews[identity.tabId] != null,
            ),
        )
    }

    private fun hasRuntimePermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasRuntimePermissionFor(permission: SitePermission): Boolean = when (permission) {
        SitePermission.Location -> permission.runtimePermissions.any(::hasRuntimePermission)
        else -> permission.runtimePermissions.all(::hasRuntimePermission)
    }

    private fun geolocationPermissionsFor(tabId: String): GeolocationPermissions? {
        val webView = webViews[tabId] ?: return GeolocationPermissions.getInstance()
        return if (isProfileIsolationSupported) {
            runCatching { WebViewCompat.getProfile(webView).geolocationPermissions }.getOrNull()
        } else {
            GeolocationPermissions.getInstance()
        }
    }

    private fun sitePermissionForWebResource(resource: String): SitePermission? = when (resource) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermission.Camera
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermission.Microphone
        PermissionRequest.RESOURCE_MIDI_SYSEX -> SitePermission.MidiSysex
        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> SitePermission.ProtectedMedia
        else -> null
    }

    private fun handleFileChooser(
        tabId: String,
        webView: WebView,
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        cancelPendingFileChooser()
        val delivery = FileChooserResultDelivery<Array<Uri>?> { value ->
            callback.onReceiveValue(value)
        }
        val generation = navigationGenerations[tabId]
        val identity = generation?.let { FileChooserIdentity(tabId, it) }
        if (
            identity == null ||
            webViews[tabId] !== webView ||
            !isFileChooserCurrent(identity)
        ) {
            delivery.complete(null)
            return true
        }
        val pending = PendingFileChooser(
            identity = identity,
            delivery = delivery,
            allowMultiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,
            acceptTypes = params.acceptTypes.copyOf(),
        )
        pendingFileChooser = pending
        return runCatching {
            launchFileChooser(params.createIntent())
            true
        }.getOrElse {
            cancelPendingFileChooser(tabId)
            true
        }
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
                    webViews[identity.tabId] != null,
                isActivityResumed = isActivityStarted && !destroyed,
            ),
        )

    private fun cancelPendingFileChooser(tabId: String? = null) {
        val pending = pendingFileChooser ?: return
        if (tabId != null && pending.identity.tabId != tabId) return
        pendingFileChooser = null
        pending.delivery.complete(null)
    }

    private fun browserChromeClient(tabId: String) = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            val currentProgress = tabs.firstOrNull { it.id == tabId }?.progress ?: return
            if (newProgress in 1..99 && newProgress - currentProgress < 3) return
            updateTab(tabId) { it.copy(progress = newProgress, isLoading = newProgress < 100) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            title?.takeIf(String::isNotBlank)?.let { value ->
                updateTab(tabId) { it.copy(title = value) }
                view.url?.let { url -> updateCandyTrailPage(tabId, url, value) }
            }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            icon?.let { storeFavicon(tabId, it) }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            handleWebPermissionRequest(tabId, request)
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            val pending = pendingPermissionAccess
            if (pending?.requestToken === request) {
                dropCanceledPermissionAccess(request)
            }
            if (activePermissions.drop(request)) permissionRevision++
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            handleGeolocationPermissionRequest(tabId, origin, callback)
        }

        override fun onGeolocationPermissionsHidePrompt() {
            pendingPermissionAccess
                ?.takeIf { pending ->
                    pending.kind == PendingPermissionKind.Geolocation &&
                        pending.identity.tabId == tabId
                }
                ?.let { pending -> dropCanceledPermissionAccess(pending.requestToken) }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = handleFileChooser(
            tabId = tabId,
            webView = webView,
            callback = filePathCallback,
            params = fileChooserParams,
        )

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean = createManagedPopup(view, isUserGesture, resultMsg)

        override fun onCloseWindow(window: WebView) {
            val closingTabId = webViews.entries.firstOrNull { (_, webView) -> webView === window }?.key
                ?: return
            closeTab(closingTabId)
        }
    }

    private fun configureServiceWorkerBlocking() {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) return
        ServiceWorkerControllerCompat.getInstance()
            .setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        return interceptServiceWorkerRequest(request, DEFAULT_STORAGE_KEY)
                    }
                },
            )
    }

    private fun configureProfileServiceWorkerBlocking(
        assignment: WebViewProfileAssignment,
        webView: WebView,
    ) {
        if (assignment == WebViewProfileAssignment.Default) return
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) return
        val storageKey = assignment.storageKey
        if (!configuredServiceWorkerProfiles.add(storageKey)) return
        runCatching {
            WebViewCompat.getProfile(webView).serviceWorkerController.setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(
                        request: WebResourceRequest,
                    ): WebResourceResponse? = interceptServiceWorkerRequest(request, storageKey)
                },
            )
        }.onFailure {
            configuredServiceWorkerProfiles.remove(storageKey)
        }
    }

    private fun interceptServiceWorkerRequest(
        request: WebResourceRequest,
        storageKey: String,
    ): WebResourceResponse? {
        if (!workerSettings.blockAdsAndTrackers) return null
        if (request.url.scheme?.lowercase() !in WEB_SCHEMES) return null
        val relevantPages = protectionRequestContexts.entries.asSequence()
            .filter { (_, context) -> context.storageKey == storageKey }
            .mapNotNull { (tabId, context) -> tabId.takeIf { context.pageHost != null } }
            .toList()
        if (relevantPages.isEmpty()) return null
        // Android does not expose a reliable originating tab here. Preserve the existing
        // conservative all-page decision: a request is blocked only when every possible page
        // context agrees, including site pauses and upstream allowlist rules. Never attribute
        // these requests to a tab's X-Ray counters.
        val shouldBlock = relevantPages.all { tabId ->
            val context = protectionRequestContexts[tabId] ?: return@all false
            if (request.isForMainFrame || isSiteProtectionPaused(tabId, null)) return@all false
            val matcher = matcherFor(context.isIncognito)
            when (
                if (matcher.hasRequestRules) matcher.decideHosts(
                    requestHost = request.url.host,
                    pageHost = context.pageHost,
                    profileId = context.profileId,
                    isForMainFrame = false,
                )?.action else null
            ) {
                CandyDecisionAction.Allow -> false
                CandyDecisionAction.Block -> true
                null -> contentBlocker.shouldBlockHosts(
                    requestHost = request.url.host,
                    pageHost = context.pageHost,
                )
            }
        }
        return if (shouldBlock) {
            blockedResponse()
        } else {
            null
        }
    }

    private fun blockedResponse() = WebResourceResponse(null, null, null)

    private fun createManagedPopup(
        source: WebView,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        if (!isUserGesture || tabs.size >= MAX_TABS) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val openerTabId = webViews.entries.firstOrNull { (_, webView) -> webView === source }?.key
            ?: selectedTabId
        val popupTabId = createTab(
            isIncognito = tabs.firstOrNull { it.id == openerTabId }?.isIncognito
                ?: selectedTab.isIncognito,
        )
        leaveSiteCapsule()
        popupOpeners[popupTabId] = openerTabId
        transport.webView = webViewFor(popupTabId)
        resultMsg.sendToTarget()
        return true
    }

    private fun downloadListener(tabId: String = selectedTabId) =
        DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = BrowserDownloadRequestFactory.create(
                url = url,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                userAgent = userAgent,
                cookies = cookiesFor(tabId, url),
            )
            if (request == null) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_download_type_unsupported),
                    Toast.LENGTH_SHORT,
                ).show()
                return@DownloadListener
            }
            showDownloadResult(downloadManager.enqueue(request))
        }

    private fun showDownloadResult(result: DownloadActionResult) {
        Toast.makeText(
            activity,
            when (result) {
                is DownloadActionResult.Enqueued ->
                    activity.getString(R.string.toast_download_started, result.fileName)
                is DownloadActionResult.Failed -> activity.getString(R.string.error_download_start_failed)
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun updateNavigationState(tabId: String, view: WebView) {
        updateTab(tabId) {
            it.copy(canGoBack = view.canGoBack(), canGoForward = view.canGoForward())
        }
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
        synchronized(privacyEventLock) {
            if (destroyed || protectionRequestContexts[tabId] !== expectedContext) return
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

    private fun reconcileCandyTrailHistory(tabId: String, view: WebView, isReload: Boolean) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val history = view.copyBackForwardList()
        if (history.currentIndex !in 0 until history.size) return
        val urls = buildList(history.size) {
            repeat(history.size) { index -> add(history.getItemAtIndex(index).url.orEmpty()) }
        }
        val currentUrl = urls[history.currentIndex]
        if (tabId in suppressedCandyTrailTabIds) {
            if (currentUrl == pageUrls[tabId]) return
            suppressedCandyTrailTabIds.remove(tabId)
        }
        val pendingTargetNodeId = pendingCandyTrailTargets.remove(tabId)?.takeIf { targetNodeId ->
            candyTrails[tabId]?.nodes?.any { node ->
                node.id == targetNodeId && node.url == currentUrl
            } == true
        }
        val result = CandyTrailHistoryReconciler.reconcile(
            trail = candyTrails[tabId],
            tabId = tabId,
            previous = candyTrailHistoryBindings[tabId] ?: CandyTrailHistoryBinding(),
            snapshot = CandyTrailHistorySnapshot(
                urls = urls,
                currentIndex = history.currentIndex,
                isReload = isReload,
            ),
            title = view.title.orEmpty().ifBlank { tab.title },
            visitedAt = System.currentTimeMillis(),
            pendingTargetNodeId = pendingTargetNodeId,
        )
        candyTrailHistoryBindings[tabId] = result.binding
        setCandyTrail(tab, result.trail)
    }

    private fun updateCandyTrailPage(tabId: String, url: String, title: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
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
        if (candyTrails[tab.id] == trail) return
        candyTrailGenerations[tab.id] = candyTrailGenerations.getOrDefault(tab.id, 0) + 1
        candyTrails[tab.id] = trail
        if (tab.id !in pendingCandyTrailRestoreIds) candyTrailRepository.save(tab, trail)
    }

    private fun recordHistory(tabId: String, url: String, title: String) {
        if (tabs.firstOrNull { it.id == tabId }?.isIncognito != false) return
        val updated = BrowsingLibraryRules.addHistory(
            current = history,
            entry = HistoryEntry(
                url = url,
                title = title,
                lastVisitedAt = System.currentTimeMillis(),
            ),
        )
        if (updated == history) return
        history.clear()
        history += updated
        store.saveHistory(updated)
    }

    private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) tabs[index] = transform(tabs[index])
    }

    private fun captureVisiblePreview(
        tabId: String,
        width: Int = 480,
        onComplete: () -> Unit = {},
    ) {
        val tab = tabs.firstOrNull { it.id == tabId }
        val view = webViews[tabId]
        if (
            tab == null ||
            tab.isIncognito ||
            tabId != selectedTabId ||
            !isActivityResumed ||
            !previewCaptureEnabled ||
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
        if (previewCaptureInFlight) {
            dirtyPreviewTabId = tabId
            onComplete()
            return
        }
        val capturedUrl = view.url
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val decorView = activity.window.decorView
        val contentBottom = previewContentBottomInWindowPx ?: decorView.height
        val source = Rect(
            location[0].coerceAtLeast(0),
            location[1].coerceAtLeast(0),
            (location[0] + view.width).coerceAtMost(decorView.width),
            TabPreviewCaptureRules.sourceBottomPx(
                viewTopPx = location[1],
                viewHeightPx = view.height,
                decorHeightPx = decorView.height,
                contentBottomPx = contentBottom,
            ),
        )
        if (source.width() <= 0 || source.height() <= 0) {
            onComplete()
            return
        }
        val height = (source.height() * (width.toFloat() / source.width()))
            .toInt()
            .coerceIn(width, width * 3)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val captureEpoch = previewEpoch
        try {
            previewCaptureInFlight = true
            PixelCopy.request(
                activity.window,
                source,
                bitmap,
                { result ->
                    val stillSamePage =
                        tabId == selectedTabId &&
                            isActivityResumed &&
                            previewEpoch == captureEpoch &&
                            webViews[tabId] === view &&
                            previewCaptureEnabled &&
                            view.isAttachedToWindow &&
                            view.url == capturedUrl
                    if (result == PixelCopy.SUCCESS && stillSamePage && bitmap.hasVisualVariation()) {
                        previews[tabId] = bitmap
                        previewRepository.save(tabId, bitmap)
                    } else {
                        bitmap.recycle()
                    }
                    finishPreviewCapture()
                    onComplete()
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            finishPreviewCapture()
            onComplete()
        }
    }

    private fun Bitmap.hasVisualVariation(): Boolean {
        if (isRecycled || width <= 0 || height <= 0) return false
        var minimumRed = 255
        var minimumGreen = 255
        var minimumBlue = 255
        var maximumRed = 0
        var maximumGreen = 0
        var maximumBlue = 0
        val columns = 24
        val rows = 36
        repeat(columns) { column ->
            val x = ((column + 0.5f) * width / columns).toInt().coerceIn(0, width - 1)
            repeat(rows) { row ->
                val sampledHeight = height * 0.72f
                val y = (height * 0.1f + (row + 0.5f) * sampledHeight / rows)
                    .toInt()
                    .coerceIn(0, height - 1)
                val color = getPixel(x, y)
                minimumRed = minOf(minimumRed, Color.red(color))
                minimumGreen = minOf(minimumGreen, Color.green(color))
                minimumBlue = minOf(minimumBlue, Color.blue(color))
                maximumRed = maxOf(maximumRed, Color.red(color))
                maximumGreen = maxOf(maximumGreen, Color.green(color))
                maximumBlue = maxOf(maximumBlue, Color.blue(color))
            }
        }
        return maxOf(
            maximumRed - minimumRed,
            maximumGreen - minimumGreen,
            maximumBlue - minimumBlue,
        ) >= 12
    }

    private fun schedulePreviewCapture(tabId: String) {
        if (
            !isActivityResumed ||
            !previewCaptureEnabled ||
            tabs.firstOrNull { it.id == tabId }?.isIncognito != false
        ) return
        pendingPreviewCapture?.let(mainHandler::removeCallbacks)
        pendingPreviewCapture = Runnable {
            pendingPreviewCapture = null
            if (tabId == selectedTabId) captureVisiblePreview(tabId)
        }.also { mainHandler.postDelayed(it, PREVIEW_CAPTURE_IDLE_DELAY_MS) }
    }

    private fun cancelPendingPreviewCapture() {
        pendingPreviewCapture?.let(mainHandler::removeCallbacks)
        pendingPreviewCapture = null
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
        pendingCandyTrailRestoreIds += tabs.asSequence()
            .filterNot(BrowserTab::isIncognito)
            .map(BrowserTab::id)
        val restoreEpoch = candyTrailEpoch
        val restoreGenerations = tabs.associate { tab ->
            tab.id to candyTrailGenerations.getOrDefault(tab.id, 0)
        }
        candyTrailRepository.restore(
            tabs = tabs.toList(),
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
                    val runtimeBinding = candyTrailHistoryBindings[tabId]
                    val mergeResult = if (!generationUnchanged && runtimeTrail != null) {
                        CandyTrailRules.mergeRestoredWithRuntime(restoredTrail, runtimeTrail)
                    } else {
                        null
                    }
                    val mergedTrail = mergeResult?.trail ?: restoredTrail
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
        if (!tab.isIncognito) faviconRepository.save(tabId, bitmap)
    }

    private fun invalidateFavicon(tabId: String) {
        faviconGenerations[tabId] = faviconGenerations.getOrDefault(tabId, 0) + 1
        favicons.remove(tabId)
        faviconRepository.delete(tabId)
    }

    private fun finishPreviewCapture() {
        previewCaptureInFlight = false
        val dirtyTabId = dirtyPreviewTabId
        dirtyPreviewTabId = null
        if (dirtyTabId != null && dirtyTabId == selectedTabId) schedulePreviewCapture(dirtyTabId)
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
            protectionRequestContexts.values.forEach { context ->
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

    private fun persist() {
        store.saveTabs(tabs.toList(), selectedTabId)
        store.saveProfiles(profiles.toList(), activeProfileId)
        savePersistentFilterRules()
    }

    private fun savePersistentFilterRules() {
        candyRuleRepository.save(filterRules.filterNot { it.id in ephemeralRuleIds })
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
        webViews.forEach { (tabId, webView) ->
            installCosmeticDocumentStartScripts(tabId, webView)
            webView.evaluateJavascript(CandyCosmeticScript.cleanupScript, null)
            injectCandyCosmeticFallback(tabId, webView, pageUrls[tabId] ?: webView.url)
        }
        if (persist) savePersistentFilterRules()
    }

    private fun newTabState(
        url: String = BLANK_URL,
        nowMillis: Long = System.currentTimeMillis(),
        isIncognito: Boolean = false,
    ) = BrowserTab(
        id = UUID.randomUUID().toString(),
        lastAccessedAt = nowMillis,
        profileId = activeProfileId,
        isIncognito = isIncognito && isProfileIsolationSupported,
        url = url,
        title = if (url == BLANK_URL) "" else AddressResolver.displayText(url),
        isLoading = url != BLANK_URL,
    )

    private fun touchTab(tabId: String, nowMillis: Long) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) tabs[index] = tabs[index].copy(lastAccessedAt = nowMillis)
    }

    private fun rememberSelectedTab(profileId: String, tabId: String) {
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
    ): Boolean {
        val expiredIds = TabRetentionRules.expiredTabIds(
            tabs = tabs,
            selectedTabId = selectedTabId,
            lifetime = inactiveTabLifetime,
            nowMillis = nowMillis,
        )
        if (expiredIds.isEmpty()) return false
        val removedIncognitoTab = tabs.any { it.id in expiredIds && it.isIncognito }
        if (
            removedIncognitoTab &&
            tabs.none { it.isIncognito && it.id !in expiredIds }
        ) {
            prepareIncognitoProfileForRemoval()
        }
        expiredIds.forEach(::removeTabResources)
        tabs.removeAll { it.id in expiredIds }
        reconcileCandyTrailForks(nowMillis)
        if (removedIncognitoTab && tabs.none(BrowserTab::isIncognito)) {
            clearIncognitoProfile()
        }
        if (activeTabs.isEmpty()) {
            tabs += newTabState(nowMillis = nowMillis)
            selectedTabId = activeTabs.first().id
            rememberSelectedTab(activeProfileId, selectedTabId)
        }
        if (persistChanges) persist()
        return true
    }

    private fun removeTabResources(
        tabId: String,
        preserveFaviconGeneration: Boolean = false,
    ) {
        clearPermissionActivity(tabId)
        clearPrivacyDataForTab(tabId)
        popupOpeners.remove(tabId)
        popupOpeners.entries.removeAll { (_, openerId) -> openerId == tabId }
        webViews.remove(tabId)?.let(::destroyWebView)
        webViewProfileKeys.remove(tabId)
        edgeToEdgePages.remove(tabId)
        navigationGenerations.remove(tabId)
        pageUrls.remove(tabId)
        bottomBarCompactStates.remove(tabId)
        candyTrailHistoryBindings.remove(tabId)
        pendingCandyTrailTargets.remove(tabId)
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

    private fun reconcileCandyTrailForks(reconciledAt: Long) {
        val openTabs = tabs.map(BrowserTab::toCandyTrailForkTab)
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

    private fun cookiesFor(tabId: String, url: String): String? {
        val webView = webViews[tabId]
        if (webView != null) return cookieManagerFor(webView).getCookie(url)
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        return if (profileAssignmentFor(tab) == WebViewProfileAssignment.Default) {
            CookieManager.getInstance().getCookie(url)
        } else {
            null
        }
    }

    private fun clearPrivacyDataForTab(tabId: String) {
        synchronized(privacyEventLock) {
            protectionRequestContexts.remove(tabId)
            pendingBlockedCounts.remove(tabId)
            privacyXRayRepository.remove(tabId)
        }
        privacySnapshots.remove(tabId)
        reportedAllowedDecisions.remove(tabId)
        pendingConsentCssUrls.remove(tabId)
        temporarySiteExceptions.remove(tabId)
        temporarySitePrivacyOverrides.remove(tabId)
        updateTab(tabId) { tab ->
            if (tab.blockedCount == 0) tab else tab.copy(blockedCount = 0)
        }
        siteExceptionRevision++
    }

    private fun isSiteProtectionPaused(tabId: String, pageUrl: String?): Boolean {
        val context = protectionRequestContexts[tabId] ?: return false
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
        val context = ProtectionRequestContext(
            profileId = tab.profileId,
            isIncognito = tab.isIncognito,
            storageKey = profileAssignmentFor(tab).storageKey,
            pageHost = PrivacyRequestSanitizer.webHost(pageUrl ?: tab.url),
        )
        synchronized(privacyEventLock) {
            protectionRequestContexts[tabId] = context
        }
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

    private fun sitePrivacyOverridesFor(tab: BrowserTab): Map<String, SitePrivacyOverrides> =
        if (tab.isIncognito) {
            temporarySitePrivacyOverrides[tab.id].orEmpty()
        } else {
            permanentSitePrivacyOverrides[tab.profileId].orEmpty()
        }

    private fun forcedVerticalScrollHostsForTab(tabId: String): Set<String> {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return emptySet()
        return sitePrivacyOverridesFor(tab).asSequence()
            .filter { (_, overrides) -> overrides.forceVerticalScrolling }
            .map { (host, _) -> host }
            .toSet()
    }

    private fun isCookieBannerRemovalDisabled(tab: BrowserTab, host: String): Boolean =
        sitePrivacyOverridesFor(tab)[host]?.cookieBannerRemovalDisabled == true

    private fun isForcedVerticalScrolling(tab: BrowserTab, host: String): Boolean =
        sitePrivacyOverridesFor(tab)[host]?.forceVerticalScrolling == true

    private fun isCookieBannerRemovalEnabled(tabId: String, pageUrl: String?): Boolean {
        if (!workerSettings.hideCookieConsent || isSiteProtectionPaused(tabId, pageUrl)) return false
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        val host = PrivacyRequestSanitizer.webHost(pageUrl ?: tab.url) ?: return false
        return !isCookieBannerRemovalDisabled(tab, host)
    }

    private fun applyCookiePolicy(tabId: String, webView: WebView, pageUrl: String?) {
        val acceptThirdPartyCookies = PrivacyPolicyRules.acceptsThirdPartyCookies(
            blockThirdPartyCookies = workerSettings.blockThirdPartyCookies,
            sitePaused = isSiteProtectionPaused(tabId, pageUrl),
        )
        cookieManagerFor(webView).setAcceptThirdPartyCookies(webView, acceptThirdPartyCookies)
    }

    private fun applySiteProtectionForNavigation(
        tabId: String,
        webView: WebView,
        pageUrl: String,
    ) {
        applyCookiePolicy(tabId, webView, pageUrl)
        installCosmeticDocumentStartScripts(tabId, webView)
        if (!workerSettings.hideCookieConsent) {
            webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
        } else if (!isCookieBannerRemovalEnabled(tabId, pageUrl)) {
            webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
        }
    }

    private fun reloadTabWithProtection(tabId: String) {
        val webView = webViewFor(tabId)
        val pageUrl = pageUrls[tabId] ?: tabs.firstOrNull { it.id == tabId }?.url
        applyCookiePolicy(tabId, webView, pageUrl)
        installCosmeticDocumentStartScripts(tabId, webView)
        installForcedVerticalScrollDocumentStartScript(tabId, webView)
        if (!isCookieBannerRemovalEnabled(tabId, pageUrl)) {
            webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
        }
        updateTab(tabId) { it.copy(isLoading = true, progress = 0, error = null) }
        webView.reload()
    }

    private fun refreshProtectionForProfile(profileId: String) {
        tabs.asSequence()
            .filter { tab -> tab.profileId == profileId && !tab.isIncognito }
            .forEach { tab ->
                val webView = webViews[tab.id] ?: return@forEach
                val pageUrl = pageUrls[tab.id] ?: tab.url
                updateProtectionRequestContext(tab.id, pageUrl)
                installForcedVerticalScrollDocumentStartScript(tab.id, webView)
                installCosmeticDocumentStartScripts(tab.id, webView)
                applySiteProtectionForNavigation(tab.id, webView, pageUrl)
            }
    }

    private fun cookieManagerFor(webView: WebView): CookieManager =
        if (isProfileIsolationSupported) {
            WebViewCompat.getProfile(webView).cookieManager
        } else {
            CookieManager.getInstance()
        }

    private fun profileAssignmentFor(tab: BrowserTab): WebViewProfileAssignment =
        WebViewProfileRules.assignment(
            tab = tab,
            profiles = profiles,
            multiProfileSupported = isProfileIsolationSupported,
            incognitoProfileName = incognitoWebViewProfileName,
        )

    private fun recreateWebViews(tabIds: Set<String>) {
        if (tabIds.isEmpty()) return
        if (selectedTabId in tabIds) cancelPendingPreviewCapture()
        if (dirtyPreviewTabId in tabIds) dirtyPreviewTabId = null
        clearServiceWorkerClientsLosingLastWebView(tabIds)
        tabIds.forEach { tabId ->
            clearPermissionActivity(tabId)
            clearPrivacyDataForTab(tabId)
            candyTrailHistoryBindings.remove(tabId)
            pendingCandyTrailTargets.remove(tabId)
            webViews.remove(tabId)?.let(::destroyWebView)
            webViewProfileKeys.remove(tabId)
            edgeToEdgePages.remove(tabId)
            navigationGenerations.remove(tabId)
            pageUrls.remove(tabId)
        }
        webViewRevision++
    }

    private fun tryDeleteNamedWebViewProfile(profileName: String): Boolean {
        configuredServiceWorkerProfiles.remove(profileName)
        return runCatching {
            val profileStore = ProfileStore.getInstance()
            profileName !in profileStore.allProfileNames || profileStore.deleteProfile(profileName)
        }.getOrDefault(false)
    }

    private fun deleteOrScheduleWebViewProfile(profileName: String) {
        if (!isProfileIsolationSupported) return
        profileDeletionCoordinator.deleteOrSchedule(profileName)
    }

    private fun deletePendingWebViewProfiles() {
        if (!isProfileIsolationSupported) return
        val orphanedIncognitoProfiles = runCatching { ProfileStore.getInstance().allProfileNames }
            .getOrDefault(emptyList())
            .filterTo(linkedSetOf()) { it.startsWith(INCOGNITO_WEBVIEW_PROFILE_PREFIX) }
        profileDeletionCoordinator.retry(
            store.loadPendingWebViewProfileDeletions() + orphanedIncognitoProfiles,
        )
    }

    private fun clearServiceWorkerClientsLosingLastWebView(tabIds: Set<String>) {
        WebViewProfileRules.storageKeysLosingLastWebView(
            assignments = webViewProfileKeys.toMap(),
            removedTabIds = tabIds,
        ).forEach(::clearProfileServiceWorkerClient)
    }

    private fun clearProfileServiceWorkerClient(profileName: String) {
        existingWebViewForProfile(profileName)?.let { webView ->
            runCatching {
                WebViewCompat.getProfile(webView).serviceWorkerController.setServiceWorkerClient(null)
            }
        }
        configuredServiceWorkerProfiles.remove(profileName)
    }

    private fun prepareIncognitoProfileForRemoval() {
        clearExistingWebViewProfileData(incognitoWebViewProfileName)
        clearProfileServiceWorkerClient(incognitoWebViewProfileName)
    }

    private fun clearIncognitoProfile() {
        incognitoRuleHits.clear()
        temporaryMutedDomains.clear()
        permissionRepository.clearPrivateSession()
        permissionRevision++
        if (ephemeralRuleIds.isNotEmpty()) {
            filterRules.removeAll { it.id in ephemeralRuleIds }
            ephemeralRuleIds.clear()
            onFilterRulesChanged(persist = false)
        }
        if (!isProfileIsolationSupported) return
        deleteOrScheduleWebViewProfile(incognitoWebViewProfileName)
        incognitoWebViewProfileName = newIncognitoWebViewProfileName()
    }

    private fun clearAllWebViewProfileData() {
        clearProfileData(
            webStorage = WebStorage.getInstance(),
            cookieManager = CookieManager.getInstance(),
            geolocationPermissions = GeolocationPermissions.getInstance(),
        )
        if (!isProfileIsolationSupported) return
        val profileNames = runCatching { ProfileStore.getInstance().allProfileNames }
            .getOrDefault(emptyList())
            .filter { profileName ->
                profileName.startsWith(INCOGNITO_WEBVIEW_PROFILE_PREFIX) ||
                    WebViewProfileRules.isManagedIsolatedProfileName(profileName)
            }
        profileNames.forEach(::clearNamedWebViewProfileData)
    }

    private fun clearNamedWebViewProfileData(profileName: String) {
        val existingWebView = existingWebViewForProfile(profileName)
        val temporaryWebView = if (existingWebView == null) {
            WebView(activity).also { webView -> WebViewCompat.setProfile(webView, profileName) }
        } else {
            null
        }
        val webView = existingWebView ?: temporaryWebView ?: return
        runCatching {
            val profile = WebViewCompat.getProfile(webView)
            clearProfileData(
                webStorage = profile.webStorage,
                cookieManager = profile.cookieManager,
                geolocationPermissions = profile.geolocationPermissions,
            )
        }
        temporaryWebView?.let(::destroyWebView)
    }

    private fun clearExistingWebViewProfileData(profileName: String) {
        val webView = existingWebViewForProfile(profileName) ?: return
        runCatching {
            val profile = WebViewCompat.getProfile(webView)
            clearProfileData(
                webStorage = profile.webStorage,
                cookieManager = profile.cookieManager,
                geolocationPermissions = profile.geolocationPermissions,
            )
        }
    }

    private fun existingWebViewForProfile(profileName: String): WebView? =
        webViews.entries
            .firstOrNull { (tabId, _) -> webViewProfileKeys[tabId] == profileName }
            ?.value

    private fun clearProfileData(
        webStorage: WebStorage,
        cookieManager: CookieManager,
        geolocationPermissions: GeolocationPermissions,
    ) {
        geolocationPermissions.clearAll()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            WebStorageCompat.deleteBrowsingData(webStorage) {}
        } else {
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            webStorage.deleteAllData()
        }
    }

    private fun destroyWebView(webView: WebView) {
        removeForcedVerticalScrollDocumentStartScript(webView)
        removeCosmeticDocumentStartScripts(webView)
        (webView.parent as? FrameLayout)?.removeView(webView)
        webView.setOnScrollChangeListener(null)
        webView.stopLoading()
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun newIncognitoWebViewProfileName(): String =
        INCOGNITO_WEBVIEW_PROFILE_PREFIX + UUID.randomUUID().toString()

    private fun pauseWebView(webView: WebView) {
        webView.onPause()
        webView.settings.requireMediaPlaybackGesture()
    }

    private fun resumeWebView(tabId: String, webView: WebView) {
        applyMediaPlaybackPolicy(tabId, webView)
        applyDomainMutePolicy(
            tabId = tabId,
            webView = webView,
            pageUrl = pageUrls[tabId] ?: tabs.firstOrNull { it.id == tabId }?.url,
        )
        webView.onResume()
    }

    private fun applyMediaPlaybackPolicy(tabId: String, webView: WebView) {
        if (MediaPlaybackPolicy.requiresUserGesture(tabId, selectedTabId, isActivityResumed)) {
            webView.settings.requireMediaPlaybackGesture()
        } else {
            webView.settings.allowContinuousMediaPlayback()
        }
    }

    private fun isDomainMuted(tab: BrowserTab, pageUrl: String?): Boolean {
        val mutedDomains = if (tab.isIncognito) {
            temporaryMutedDomains[tab.profileId]
        } else {
            permanentMutedDomains[tab.profileId]
        }
        return DomainMuteRules.isMuted(pageUrl, mutedDomains.orEmpty())
    }

    private fun refreshDomainMuteForProfile(profileId: String, isIncognito: Boolean) {
        tabs.asSequence()
            .filter { tab -> tab.profileId == profileId && tab.isIncognito == isIncognito }
            .forEach { tab ->
                val webView = webViews[tab.id] ?: return@forEach
                applyDomainMutePolicy(tab.id, webView, pageUrls[tab.id] ?: tab.url)
            }
    }

    private fun applyDomainMutePolicy(tabId: String, webView: WebView, pageUrl: String?) {
        if (!isDomainMuteSupported) return
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        WebViewCompat.setAudioMuted(webView, isDomainMuted(tab, pageUrl))
    }

    private companion object {
        val isDomainMuteSupported: Boolean =
            WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)
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
        const val PREVIEW_CAPTURE_IDLE_DELAY_MS = 220L
        const val BLOCKER_COUNT_FLUSH_DELAY_MS = 250L
        const val MAX_COSMETIC_DOCUMENT_START_RULES = 64
        const val MAX_REPORTED_ALLOW_DECISIONS = 64
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

    private enum class PendingPermissionKind {
        WebResource,
        Geolocation,
    }

    private data class PendingFileChooser(
        val identity: FileChooserIdentity,
        val delivery: FileChooserResultDelivery<Array<Uri>?>,
        val allowMultiple: Boolean,
        val acceptTypes: Array<String>,
    )

    private data class ProtectionRequestContext(
        val profileId: String,
        val isIncognito: Boolean,
        val storageKey: String,
        val pageHost: String?,
        val pendingFilterHits: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
    )
}

enum class CapsuleSaveResult {
    PinRequested,
    PinningUnsupported,
    PinRequestFailed,
    Updated,
    UpdateFailed,
    LimitReached,
    Invalid,
}

private fun BrowserTab.toCandyTrailForkTab() = CandyTrailForkTab(
    id = id,
    profileId = profileId,
    isIncognito = isIncognito,
)
