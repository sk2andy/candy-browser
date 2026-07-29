package dev.sk2andy.materialbrowser.browser

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.Insets
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
import dev.sk2andy.materialbrowser.blocking.ContentBlocker
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer
import dev.sk2andy.materialbrowser.blocking.PrivacyPolicyRules
import dev.sk2andy.materialbrowser.blocking.PrivacyXRayRepository
import dev.sk2andy.materialbrowser.blocking.PrivacyXRaySnapshot
import dev.sk2andy.materialbrowser.blocking.RequestProtectionRules
import dev.sk2andy.materialbrowser.blocking.SiteExceptionRules
import dev.sk2andy.materialbrowser.blocking.SiteProtectionState
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
import dev.sk2andy.materialbrowser.browser.integration.PageShareRequest
import dev.sk2andy.materialbrowser.browser.integration.PageShareResult
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingLibraryRules
import dev.sk2andy.materialbrowser.data.CandyTrailRepository
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FaviconRepository
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabDuplicateRules
import dev.sk2andy.materialbrowser.data.TabPinningRules
import dev.sk2andy.materialbrowser.data.TabPreviewRepository
import dev.sk2andy.materialbrowser.data.TabRetentionRules
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BrowserController(private val activity: Activity) {
    val tabs = mutableStateListOf<BrowserTab>()
    val profiles = mutableStateListOf<BrowserProfile>()
    val previews = mutableStateMapOf<String, Bitmap>()
    val favicons = mutableStateMapOf<String, Bitmap>()
    val history = mutableStateListOf<HistoryEntry>()
    val favorites = mutableStateListOf<FavoriteEntry>()
    val privacySnapshots = mutableStateMapOf<String, PrivacyXRaySnapshot>()
    val candyTrails = mutableStateMapOf<String, CandyTrail>()
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
    var dismissResistancePercent by mutableIntStateOf(40)
        private set
    var isDefaultBrowser by mutableStateOf(false)
        private set
    var webViewRevision by mutableIntStateOf(0)
        private set
    val isProfileIsolationSupported: Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    private val bottomBarCompactStates = mutableStateMapOf<String, Boolean>()

    val isBottomBarCompact: Boolean
        get() = bottomBarCompactStates[selectedTabId] == true

    private val webViews = mutableMapOf<String, WebView>()
    private val edgeToEdgePages = mutableMapOf<String, Boolean>()
    private val navigationGenerations = mutableMapOf<String, Int>()
    private val popupOpeners = mutableMapOf<String, String>()
    private val consentScriptHandlers = mutableMapOf<WebView, ScriptHandler>()
    private val pageUrls = ConcurrentHashMap<String, String>()
    private val webViewProfileKeys = ConcurrentHashMap<String, String>()
    private val configuredServiceWorkerProfiles = mutableSetOf<String>()
    private var incognitoWebViewProfileName = newIncognitoWebViewProfileName()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPreviewCapture: Runnable? = null
    private val pendingBlockedCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val blockerFlushScheduled = AtomicBoolean(false)
    private val privacyXRayRepository = PrivacyXRayRepository()
    private val privacyEventLock = Any()
    private val temporarySiteExceptions = ConcurrentHashMap<String, Set<String>>()
    private val protectionRequestContexts = ConcurrentHashMap<String, ProtectionRequestContext>()
    private var isActivityResumed = true
    @Volatile
    private var destroyed = false
    private var previewCaptureInFlight = false
    private var dirtyPreviewTabId: String? = null
    private var previewCaptureEnabled = true
    private var lastWindowInsets: WindowInsetsCompat? = null
    private var previewEpoch = 0
    private var faviconEpoch = 0
    private val faviconGenerations = mutableMapOf<String, Int>()
    private val candyTrailHistoryBindings = mutableMapOf<String, CandyTrailHistoryBinding>()
    private val pendingCandyTrailTargets = mutableMapOf<String, String>()
    private val candyTrailGenerations = mutableMapOf<String, Int>()
    private val pendingCandyTrailRestoreIds = mutableSetOf<String>()
    private val suppressedCandyTrailTabIds = mutableSetOf<String>()
    private var candyTrailEpoch = 0
    private val store = BrowserSessionStore(activity)
    private val profileDeletionCoordinator =
        WebViewProfileDeletionCoordinator(store, ::tryDeleteNamedWebViewProfile)
    private val previewRepository = TabPreviewRepository.get(activity)
    private val faviconRepository = FaviconRepository.get(activity)
    private val candyTrailRepository = CandyTrailRepository.get(activity)
    private val contentBlocker = ContentBlocker(activity)
    private val downloadManager = BrowserDownloadManager(activity)
    private val externalApps = ExternalAppLauncher(activity)
    private val assistantSummary = AssistantSummaryLauncher(activity)
    private val pageShare = PageShareLauncher(activity)
    private val commandCatalog = AndroidCommandCatalog(activity)

    @Volatile
    private var permanentSiteExceptions = store.loadPermanentSiteExceptions()
    private var siteExceptionRevision by mutableIntStateOf(0)

    @Volatile
    private var workerSettings = store.loadBlockerSettings()

    val selectedTab: BrowserTab
        get() = tabs.firstOrNull { it.id == selectedTabId }
            ?: activeTabs.firstOrNull()
            ?: tabs.first()

    val activeTabs: List<BrowserTab>
        get() = tabs.filter { it.profileId == activeProfileId }

    fun privacySnapshot(tabId: String): PrivacyXRaySnapshot =
        privacySnapshots[tabId] ?: PrivacyXRaySnapshot.Empty

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
        )
    }

    init {
        deletePendingWebViewProfiles()
        val nowMillis = System.currentTimeMillis()
        blockerSettings = workerSettings
        inactiveTabLifetime = store.loadInactiveTabLifetime()
        searchEngine = store.loadSearchEngine()
        dismissResistancePercent = store.loadDismissResistancePercent()
        isDefaultBrowser = DefaultBrowserRole.isHeld(activity)
        val (restoredProfiles, restoredActiveProfileId) = store.loadProfiles()
        profiles += restoredProfiles.take(MAX_PROFILES)
        val restoredProfileIds = profiles.mapTo(mutableSetOf(), BrowserProfile::id)
        permanentSiteExceptions = permanentSiteExceptions
            .filterKeys(restoredProfileIds::contains)
            .mapValues { (_, hosts) ->
                hosts.mapNotNull(SiteExceptionRules::normalizedException)
                    .take(SiteExceptionRules.MAX_PER_PROFILE)
                    .toSet()
            }
        store.savePermanentSiteExceptions(permanentSiteExceptions)
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
        lastWindowInsets = insets
        // Compose owns the root inset listener. AndroidView children do not receive that
        // callback, so forward every change to Chromium's WebView inset controller.
        webViews.forEach { (tabId, webView) ->
            if (webView.isAttachedToWindow) {
                applyWindowInsets(tabId, webView, insets)
            }
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
        val drawsEdgeToEdge = edgeToEdgePages[tabId] == true
        val safeArea = insets.getInsets(SAFE_AREA_INSET_TYPES)
        val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val tappableElements = insets.getInsets(WindowInsetsCompat.Type.tappableElement())
        val hasTappableNavigation =
            (navigationBars.left > 0 && tappableElements.left > 0) ||
                (navigationBars.top > 0 && tappableElements.top > 0) ||
                (navigationBars.right > 0 && tappableElements.right > 0) ||
                (navigationBars.bottom > 0 && tappableElements.bottom > 0)
        val usesGestureNavigation = navigationBars != Insets.NONE && !hasTappableNavigation
        val topMargin = if (drawsEdgeToEdge) {
            0
        } else {
            (safeArea.top - webView.scrollY).coerceAtLeast(0)
        }
        val bottomMargin = if (drawsEdgeToEdge || usesGestureNavigation) 0 else safeArea.bottom
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
            insets
        } else {
            WindowInsetsCompat.Builder(insets)
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

    private fun updateScrollAwareInsets(
        tabId: String,
        webView: WebView,
        scrollY: Int,
        oldScrollY: Int,
    ) {
        if (edgeToEdgePages[tabId] == true) return
        val insets = ViewCompat.getRootWindowInsets(webView) ?: lastWindowInsets ?: return
        val safeTop = insets.getInsets(SAFE_AREA_INSET_TYPES).top
        val topMargin = (safeTop - scrollY).coerceAtLeast(0)
        val oldTopMargin = (safeTop - oldScrollY).coerceAtLeast(0)
        if (topMargin != oldTopMargin) applyWindowInsets(tabId, webView, insets)
    }

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
        if (inNewTab) {
            createTab(url)
        } else {
            submitAddress(url)
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

    fun deleteProfile(profileId: String): Boolean {
        if (profiles.size <= 1) return false
        val profileIndex = profiles.indexOfFirst { it.id == profileId }
        if (profileIndex < 0) return false
        val fallbackProfile = if (profileId == activeProfileId) {
            profiles.getOrNull(profileIndex + 1) ?: profiles[profileIndex - 1]
        } else {
            profiles.first { it.id == activeProfileId }
        }
        val movedTabIds = WebViewProfileRules.tabIdsForProfileDeletion(tabs, profileId)
        movedTabIds.forEach(::clearPrivacyDataForTab)
        if (permanentSiteExceptions.containsKey(profileId)) {
            permanentSiteExceptions = permanentSiteExceptions - profileId
            store.savePermanentSiteExceptions(permanentSiteExceptions)
            siteExceptionRevision++
        }
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
                if (workerSettings.hideCookieConsent) {
                    installCookieConsentDocumentStartScript(tabId, webView)
                }
                applySiteProtectionForNavigation(
                    tabId = tabId,
                    webView = webView,
                    pageUrl = pageUrls[tabId] ?: tabs.first { it.id == tabId }.url,
                )
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
        if (tabId == selectedTabId) webViews[tabId]?.let(::pauseWebView)
        clearPrivacyDataForTab(tabId)
        if (oldAssignment != newAssignment) recreateWebViews(setOf(tabId))
        updateTab(tabId) { movedTab }
        updateProtectionRequestContext(tabId, pageUrls[tabId])
        webViews[tabId]?.let { webView ->
            if (workerSettings.hideCookieConsent) {
                installCookieConsentDocumentStartScript(tabId, webView)
            }
            applySiteProtectionForNavigation(
                tabId = tabId,
                webView = webView,
                pageUrl = pageUrls[tabId] ?: sourceTab.url,
            )
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

    fun requestDefaultBrowserRole() {
        val intent = DefaultBrowserRole.createRequestIntent(activity)
        if (intent == null) {
            isDefaultBrowser = DefaultBrowserRole.isHeld(activity)
            Toast.makeText(
                activity,
                activity.getString(
                    if (isDefaultBrowser) {
                        R.string.toast_already_default_browser
                    } else {
                        R.string.toast_default_browser_selection_unavailable
                    },
                ),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        activity.startActivity(intent)
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
        val nowMillis = System.currentTimeMillis()
        touchTab(selectedTabId, nowMillis)
        touchTab(tabId, nowMillis)
        pruneStaleTabs(nowMillis)
        if (tabId == selectedTabId) {
            persist()
            return
        }
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

    fun goBack() = webViews[selectedTabId]?.takeIf(WebView::canGoBack)?.goBack() ?: Unit
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

    fun clearCookiesAndReload(): Boolean {
        val tabId = selectedTabId
        if (selectedTab.url == BLANK_URL) return false
        val webView = webViewFor(tabId)
        val cookieManager = WebViewProfileCookies.managerFor(webView) ?: return false
        val navigationGeneration = navigationGenerations[tabId]
        val capturedUrl = webView.url
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
                unchanged
            },
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

    fun addressSuggestionItems(query: String, limit: Int = 10): List<AddressSuggestionItem> {
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
            query,
            navigationMatches,
            commandMatches,
            if (CommandMatcher.isExplicitCommandQuery(query)) definitions.size else limit,
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

    val isSelectedTabFavorite: Boolean
        get() = !selectedTab.isIncognito && BrowsingLibraryRules.isFavorite(favorites, selectedTab.url)

    fun isFavorite(url: String): Boolean = BrowsingLibraryRules.isFavorite(favorites, url)

    fun toggleFavorite(tabId: String = selectedTabId): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        if (tab.isIncognito || tab.url == BLANK_URL) return false
        val wasFavorite = BrowsingLibraryRules.isFavorite(favorites, tab.url)
        val updated = BrowsingLibraryRules.toggleFavorite(
            current = favorites,
            entry = FavoriteEntry(
                url = tab.url,
                title = tab.title,
                addedAt = System.currentTimeMillis(),
            ),
        )
        favorites.clear()
        favorites += updated
        store.saveFavorites(updated)
        return !wasFavorite
    }

    fun expandBottomBar() {
        bottomBarCompactStates[selectedTabId] = false
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

    fun updateBlockerSettings(settings: BlockerSettings) {
        val cookieConsentSettingChanged = workerSettings.hideCookieConsent != settings.hideCookieConsent
        blockerSettings = settings
        workerSettings = settings
        store.saveBlockerSettings(settings)
        webViews.forEach { (tabId, webView) ->
            applyCookiePolicy(tabId, webView, pageUrls[tabId])
        }
        if (cookieConsentSettingChanged) {
            webViews.forEach { (tabId, webView) ->
                if (settings.hideCookieConsent) {
                    installCookieConsentDocumentStartScript(tabId, webView)
                    if (isSiteProtectionPaused(tabId, pageUrls[tabId])) {
                        webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
                    } else {
                        webView.evaluateJavascript(
                            contentBlocker.consentScriptFor(siteExceptionHostsForTab(tabId)),
                            null,
                        )
                    }
                } else {
                    removeCookieConsentDocumentStartScript(webView)
                    webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
                }
            }
        }
        reload()
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

    fun updateInactiveTabLifetime(lifetime: InactiveTabLifetime) {
        inactiveTabLifetime = lifetime
        store.saveInactiveTabLifetime(lifetime)
        pruneStaleTabs()
    }

    fun updateSearchEngine(engine: SearchEngine) {
        searchEngine = engine
        store.saveSearchEngine(engine)
    }

    fun updateDismissResistancePercent(percent: Int) {
        dismissResistancePercent = percent.coerceIn(10, 90)
        store.saveDismissResistancePercent(dismissResistancePercent)
    }

    fun clearBrowsingData() {
        cancelPendingPreviewCapture()
        dirtyPreviewTabId = null
        tabs.forEach { tab ->
            updateProtectionRequestContext(tab.id, pageUrls[tab.id] ?: tab.url)
        }
        mainHandler.removeCallbacks(blockerCountFlush)
        synchronized(privacyEventLock) {
            pendingBlockedCounts.clear()
            blockerFlushScheduled.set(false)
            privacyXRayRepository.clear()
        }
        clearAllWebViewProfileData()
        privacySnapshots.clear()
        temporarySiteExceptions.clear()
        permanentSiteExceptions = emptyMap()
        store.savePermanentSiteExceptions(emptyMap())
        siteExceptionRevision++
        webViews.forEach { (tabId, webView) ->
            val pageUrl = pageUrls[tabId] ?: tabs.firstOrNull { it.id == tabId }?.url
                ?: BLANK_URL
            if (workerSettings.hideCookieConsent) installCookieConsentDocumentStartScript(tabId, webView)
            applySiteProtectionForNavigation(tabId, webView, pageUrl)
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

    fun destroy() {
        destroyed = true
        cancelPendingPreviewCapture()
        mainHandler.removeCallbacks(blockerCountFlush)
        synchronized(privacyEventLock) {
            pendingBlockedCounts.clear()
            blockerFlushScheduled.set(false)
            privacyXRayRepository.clear()
            protectionRequestContexts.clear()
        }
        temporarySiteExceptions.clear()
        persist()
        if (tabs.any(BrowserTab::isIncognito)) prepareIncognitoProfileForRemoval()
        configuredServiceWorkerProfiles.toList().forEach(::clearProfileServiceWorkerClient)
        webViews.values.forEach(::destroyWebView)
        webViews.clear()
        webViewProfileKeys.clear()
        consentScriptHandlers.clear()
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
            enablePinchZoom()
            safeBrowsingEnabled = true
        }
        applyMediaPlaybackPolicy(tabId, this)
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
        installCookieConsentDocumentStartScript(tabId, this)
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
            updateScrollAwareInsets(tabId, this, scrollY, oldScrollY)
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

    private fun browserWebViewClient(tabId: String) = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageUrls[tabId] = url
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
            injectCookieConsentCssFallback(tabId, view)
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
            finalizeCookieConsentBlocking(tabId, view)
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
            val requestUrl = request.url.toString()
            val requestContext = protectionRequestContexts[tabId] ?: return null
            val pageUrl = requestContext.pageHost?.let { host ->
                "https://$host"
            }
            val sitePaused = isSiteProtectionPaused(tabId, pageUrl)
            if (sitePaused) return null
            val listedRequest = contentBlocker.shouldBlock(requestUrl, pageUrl)
            if (RequestProtectionRules.shouldBlock(
                    isForMainFrame = false,
                    blockerEnabled = true,
                    sitePaused = false,
                    isListedRequest = listedRequest,
                )
            ) {
                queueBlockedRequest(tabId, requestUrl, pageUrl, requestContext)
                return blockedResponse()
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
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
            clearServiceWorkerClientsLosingLastWebView(setOf(tabId))
            webViews.remove(tabId)
            webViewProfileKeys.remove(tabId)
            removeCookieConsentDocumentStartScript(view)
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

    private fun injectCookieConsentCssFallback(tabId: String, view: WebView) {
        if (
            workerSettings.hideCookieConsent &&
            !isSiteProtectionPaused(tabId, pageUrls[tabId]) &&
            view !in consentScriptHandlers
        ) {
            view.evaluateJavascript(
                contentBlocker.consentScriptFor(siteExceptionHostsForTab(tabId)),
                null,
            )
        }
    }

    private fun installCookieConsentDocumentStartScript(
        tabId: String,
        view: WebView,
    ) {
        removeCookieConsentDocumentStartScript(view)
        if (
            !workerSettings.hideCookieConsent ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return

        // Document-start injection runs before page JavaScript. The script itself exits subframes;
        // keep the onPageCommitVisible fallback for WebView providers lacking this feature.
        // https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set)
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(
                view,
                contentBlocker.consentScriptFor(siteExceptionHostsForTab(tabId)),
                ALL_WEB_ORIGINS,
            )
        }.getOrNull()?.let { handler -> consentScriptHandlers[view] = handler }
    }

    private fun removeCookieConsentDocumentStartScript(view: WebView) {
        consentScriptHandlers.remove(view)?.let { handler ->
            runCatching(handler::remove)
        }
    }

    private fun finalizeCookieConsentBlocking(tabId: String, view: WebView) {
        if (workerSettings.hideCookieConsent && !isSiteProtectionPaused(tabId, pageUrls[tabId])) {
            view.evaluateJavascript(contentBlocker.consentCleanupScript, null)
        }
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

        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) = callback.invoke(origin, false, false)

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
        val relevantPages = protectionRequestContexts.entries.asSequence()
            .filter { (_, context) -> context.storageKey == storageKey }
            .mapNotNull { (tabId, context) ->
                context.pageHost?.let { pageHost -> tabId to "https://$pageHost" }
            }
            .toList()
        if (relevantPages.isEmpty()) return null
        val requestUrl = request.url.toString()
        // Android does not expose a reliable originating tab here. Preserve the existing
        // conservative all-page decision: a request is blocked only when every possible page
        // context agrees, including site pauses and upstream allowlist rules. Never attribute
        // these requests to a tab's X-Ray counters.
        val shouldBlock = relevantPages.all { (tabId, pageUrl) ->
            RequestProtectionRules.shouldBlock(
                isForMainFrame = request.isForMainFrame,
                blockerEnabled = true,
                sitePaused = isSiteProtectionPaused(tabId, pageUrl),
                isListedRequest = contentBlocker.shouldBlock(requestUrl, pageUrl),
            )
        }
        return if (shouldBlock) {
            blockedResponse()
        } else {
            null
        }
    }

    private fun blockedResponse() = WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "No Content",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

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
    ) {
        synchronized(privacyEventLock) {
            if (destroyed || protectionRequestContexts[tabId] !== expectedContext) return
            privacyXRayRepository.record(tabId, requestUrl, pageUrl)
            pendingBlockedCounts.computeIfAbsent(tabId) { AtomicInteger() }.incrementAndGet()
            if (blockerFlushScheduled.compareAndSet(false, true)) {
                mainHandler.postDelayed(blockerCountFlush, BLOCKER_COUNT_FLUSH_DELAY_MS)
            }
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
        val source = Rect(
            location[0].coerceAtLeast(0),
            location[1].coerceAtLeast(0),
            (location[0] + view.width).coerceAtMost(decorView.width),
            (location[1] + view.height).coerceAtMost(decorView.height),
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
            blockerFlushScheduled.set(false)
            if (!destroyed &&
                pendingBlockedCounts.values.any { it.get() > 0 } &&
                blockerFlushScheduled.compareAndSet(false, true)
            ) {
                mainHandler.postDelayed(this, BLOCKER_COUNT_FLUSH_DELAY_MS)
            }
        }
    }

    private fun persist() {
        store.saveTabs(tabs.toList(), selectedTabId)
        store.saveProfiles(profiles.toList(), activeProfileId)
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
        temporarySiteExceptions.remove(tabId)
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
        if (!workerSettings.hideCookieConsent) {
            removeCookieConsentDocumentStartScript(webView)
            webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
        } else {
            if (webView !in consentScriptHandlers) {
                installCookieConsentDocumentStartScript(tabId, webView)
            }
            if (isSiteProtectionPaused(tabId, pageUrl)) {
                webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
            } else {
                webView.evaluateJavascript(
                    contentBlocker.consentScriptFor(siteExceptionHostsForTab(tabId)),
                    null,
                )
            }
        }
    }

    private fun reloadTabWithProtection(tabId: String) {
        val webView = webViewFor(tabId)
        val pageUrl = pageUrls[tabId] ?: tabs.firstOrNull { it.id == tabId }?.url
        applyCookiePolicy(tabId, webView, pageUrl)
        if (workerSettings.hideCookieConsent) {
            installCookieConsentDocumentStartScript(tabId, webView)
        }
        if (isSiteProtectionPaused(tabId, pageUrl) || !workerSettings.hideCookieConsent) {
            webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
        } else {
            webView.evaluateJavascript(
                contentBlocker.consentScriptFor(siteExceptionHostsForTab(tabId)),
                null,
            )
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
                if (workerSettings.hideCookieConsent) {
                    installCookieConsentDocumentStartScript(tab.id, webView)
                }
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
        removeCookieConsentDocumentStartScript(webView)
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
        webView.onResume()
    }

    private fun applyMediaPlaybackPolicy(tabId: String, webView: WebView) {
        if (MediaPlaybackPolicy.requiresUserGesture(tabId, selectedTabId, isActivityResumed)) {
            webView.settings.requireMediaPlaybackGesture()
        } else {
            webView.settings.allowContinuousMediaPlayback()
        }
    }

    private companion object {
        val ALL_WEB_ORIGINS = setOf("*")
        val SAFE_AREA_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        const val PREVIEW_CAPTURE_IDLE_DELAY_MS = 220L
        const val BLOCKER_COUNT_FLUSH_DELAY_MS = 250L
    }

    private data class ProtectionRequestContext(
        val profileId: String,
        val isIncognito: Boolean,
        val storageKey: String,
        val pageHost: String?,
    )
}

private fun BrowserTab.toCandyTrailForkTab() = CandyTrailForkTab(
    id = id,
    profileId = profileId,
    isIncognito = isIncognito,
)
