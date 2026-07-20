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
import android.view.PixelCopy
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.webkit.ProfileStore
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.ContentBlocker
import dev.sk2andy.materialbrowser.browser.actions.BrowserDownloadManager
import dev.sk2andy.materialbrowser.browser.actions.DownloadActionResult
import dev.sk2andy.materialbrowser.browser.actions.WebContentActionState
import dev.sk2andy.materialbrowser.browser.actions.WebViewHitTestResolver
import dev.sk2andy.materialbrowser.browser.credentials.SystemWebViewCredentials
import dev.sk2andy.materialbrowser.browser.integration.DefaultBrowserRole
import dev.sk2andy.materialbrowser.browser.integration.ExternalAppLauncher
import dev.sk2andy.materialbrowser.browser.integration.ExternalLaunchResult
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingLibraryRules
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FaviconRepository
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabPreviewRepository
import dev.sk2andy.materialbrowser.data.TabRetentionRules
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BrowserController(private val activity: Activity) {
    val tabs = mutableStateListOf<BrowserTab>()
    val previews = mutableStateMapOf<String, Bitmap>()
    val favicons = mutableStateMapOf<String, Bitmap>()
    val history = mutableStateListOf<HistoryEntry>()
    val favorites = mutableStateListOf<FavoriteEntry>()
    val contentActions = WebContentActionState()

    var selectedTabId by mutableStateOf("")
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
    private val bottomBarCompactStates = mutableStateMapOf<String, Boolean>()

    val isBottomBarCompact: Boolean
        get() = bottomBarCompactStates[selectedTabId] == true

    private val webViews = mutableMapOf<String, WebView>()
    private val popupOpeners = mutableMapOf<String, String>()
    private val pageUrls = ConcurrentHashMap<String, String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPreviewCapture: Runnable? = null
    private val pendingBlockedCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val blockerFlushScheduled = AtomicBoolean(false)
    private var isActivityResumed = true
    @Volatile
    private var destroyed = false
    private var previewCaptureInFlight = false
    private var dirtyPreviewTabId: String? = null
    private var previewCaptureEnabled = true
    private var previewEpoch = 0
    private var faviconEpoch = 0
    private val faviconGenerations = mutableMapOf<String, Int>()
    private val store = BrowserSessionStore(activity)
    private val previewRepository = TabPreviewRepository.get(activity)
    private val faviconRepository = FaviconRepository.get(activity)
    private val contentBlocker = ContentBlocker(activity)
    private val downloadManager = BrowserDownloadManager(activity)
    private val externalApps = ExternalAppLauncher(activity)

    @Volatile
    private var workerSettings = store.loadBlockerSettings()

    val selectedTab: BrowserTab
        get() = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.first()

    init {
        val nowMillis = System.currentTimeMillis()
        blockerSettings = workerSettings
        inactiveTabLifetime = store.loadInactiveTabLifetime()
        searchEngine = store.loadSearchEngine()
        dismissResistancePercent = store.loadDismissResistancePercent()
        isDefaultBrowser = DefaultBrowserRole.isHeld(activity)
        val (restoredTabs, restoredSelection) = store.loadTabs(nowMillis)
        history += store.loadHistory()
        favorites += store.loadFavorites()
        tabs += restoredTabs.take(MAX_TABS)
        if (tabs.isEmpty()) tabs += newTabState(nowMillis = nowMillis)
        selectedTabId = restoredSelection?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id
        pruneStaleTabs(nowMillis, persistChanges = false)
        touchTab(selectedTabId, nowMillis)
        persist()
        // Incognito tabs are never restored. Remove data left by process death before
        // any private WebView can reuse the old profile.
        clearIncognitoProfile()
        restorePersistedPreviews()
        restorePersistedFavicons()
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
        dispatchCurrentWindowInsets(webView)
        SystemWebViewCredentials.onAttached(webView)
        if (isActivityResumed) resumeWebView(selectedTabId, webView)
    }

    fun onWindowInsetsChanged(insets: WindowInsetsCompat) {
        // Compose owns the root inset listener. AndroidView children do not receive that
        // callback, so forward every change to Chromium's WebView inset controller.
        webViews.values.forEach { webView ->
            if (webView.isAttachedToWindow) {
                ViewCompat.dispatchApplyWindowInsets(webView, insets)
            }
        }
    }

    fun detachWebView(container: FrameLayout) {
        container.removeAllViews()
    }

    private fun dispatchCurrentWindowInsets(webView: WebView) {
        // A reused WebView can attach after the content root's inset traversal. requestApplyInsets()
        // alone does not cross this Compose AndroidView holder, so dispatch the current snapshot.
        webView.doOnAttach { attachedView ->
            ViewCompat.getRootWindowInsets(attachedView)?.let { insets ->
                ViewCompat.dispatchApplyWindowInsets(attachedView, insets)
            }
        }
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
        val tab = newTabState(url = resolvedUrl, nowMillis = nowMillis, isIncognito = isIncognito)
        tabs += tab
        selectedTabId = tab.id
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

    fun downloadContextImage() {
        val target = contentActions.target ?: return
        val imageUrl = target.imageUrl ?: return
        val selectedWebView = webViews[selectedTabId]
        val action = target.downloadImageAction(
            userAgent = selectedWebView?.settings?.userAgentString,
            cookies = cookieManagerFor(selectedTabId).getCookie(imageUrl),
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

    fun selectTab(tabId: String) {
        if (tabs.none { it.id == tabId }) return
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
        persist()
    }

    fun switchToOpenTab(tabId: String): Boolean {
        if (tabId == selectedTabId || tabs.none { it.id == tabId }) return false
        val blankSourceTabId = selectedTab.takeIf(BrowserTab::isFreshBlankTab)?.id
        selectTab(tabId)
        blankSourceTabId?.let(::closeTab)
        return true
    }

    fun setBlankTabIncognito(enabled: Boolean): Boolean {
        val tab = selectedTab
        if (tab.url != BLANK_URL || tab.isIncognito == enabled) return false
        val wasLastIncognitoTab = tab.isIncognito && tabs.count(BrowserTab::isIncognito) == 1
        cancelPendingPreviewCapture()
        if (dirtyPreviewTabId == tab.id) dirtyPreviewTabId = null
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
        webViewRevision++
        persist()
        return true
    }

    fun closeTab(tabId: String) {
        val nowMillis = System.currentTimeMillis()
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val closingTab = tabs[index]
        val popupOpenerId = popupOpeners.remove(tabId)
        removeTabResources(tabId)
        tabs.removeAt(index)
        if (selectedTabId == tabId) {
            selectedTabId = popupOpenerId
                ?.takeIf { openerId -> tabs.any { it.id == openerId } }
                ?: tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id
                ?: newTabState(
                    nowMillis = nowMillis,
                    isIncognito = closingTab.isIncognito,
                ).also(tabs::add).id
            touchTab(selectedTabId, nowMillis)
        }
        if (closingTab.isIncognito && tabs.none(BrowserTab::isIncognito)) {
            clearIncognitoProfile()
        }
        persist()
    }

    fun goBack() = webViews[selectedTabId]?.takeIf(WebView::canGoBack)?.goBack() ?: Unit
    fun goForward() = webViews[selectedTabId]?.takeIf(WebView::canGoForward)?.goForward() ?: Unit
    fun reload() {
        updateTab(selectedTabId) { it.copy(isLoading = true, progress = 0, error = null) }
        webViewFor(selectedTabId).reload()
    }

    fun stopLoading() {
        webViews[selectedTabId]?.stopLoading()
        updateTab(selectedTabId) { it.copy(isLoading = false) }
    }

    fun addressSuggestions(query: String, limit: Int = 8): List<AddressSuggestion> =
        BrowsingLibraryRules.addressSuggestions(
            history = history,
            tabs = tabs,
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
        webViews.values.forEach {
            cookieManagerFor(it).setAcceptThirdPartyCookies(it, !settings.blockThirdPartyCookies)
        }
        if (cookieConsentSettingChanged) {
            webViews.values.forEach { webView ->
                if (settings.hideCookieConsent) {
                    injectCookieConsentCss(webView)
                } else {
                    webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
                }
            }
        }
        reload()
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
        mainHandler.removeCallbacks(blockerCountFlush)
        pendingBlockedCounts.clear()
        blockerFlushScheduled.set(false)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        val incognitoTabIds = tabs.asSequence()
            .filter(BrowserTab::isIncognito)
            .map(BrowserTab::id)
            .toList()
        incognitoTabIds.forEach { tabId ->
            webViews.remove(tabId)?.let(::destroyWebView)
        }
        clearIncognitoProfile()
        if (incognitoTabIds.isNotEmpty()) webViewRevision++
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
        pendingBlockedCounts.clear()
        blockerFlushScheduled.set(false)
        persist()
        webViews.values.forEach(::destroyWebView)
        webViews.clear()
        clearIncognitoProfile()
        if (
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(null)
        }
        pageUrls.clear()
        popupOpeners.clear()
        bottomBarCompactStates.clear()
        previews.clear()
        favicons.clear()
        faviconGenerations.clear()
    }

    private fun webViewFor(tabId: String): WebView = webViews.getOrPut(tabId) {
        val tab = tabs.first { it.id == tabId }
        createWebView(tabId).also { webView ->
            if (tab.url != BLANK_URL) {
                updateTab(tabId) { it.copy(isLoading = true, progress = 0, error = null) }
                webView.loadUrl(tab.url)
            }
        }
    }

    private fun createWebView(tabId: String): WebView = WebView(activity).apply {
        val tab = tabs.first { it.id == tabId }
        if (tab.isIncognito && supportsMultipleProfiles()) {
            WebViewCompat.setProfile(this, INCOGNITO_PROFILE_NAME)
            configureIncognitoServiceWorkerBlocking(this)
        }
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
        cookieManagerFor(this).apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(configuredWebView, !workerSettings.blockThirdPartyCookies)
        }
        webViewClient = browserWebViewClient(tabId)
        webChromeClient = browserChromeClient(tabId)
        setDownloadListener(downloadListener(tabId))
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

    private fun browserWebViewClient(tabId: String) = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageUrls[tabId] = url
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
            injectCookieConsentCss(view)
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
            finalizeCookieConsentBlocking(view)
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
            updateNavigationState(tabId, view)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (
                !request.isForMainFrame &&
                workerSettings.blockAdsAndTrackers &&
                contentBlocker.shouldBlock(request.url.toString(), pageUrls[tabId])
            ) {
                queueBlockedCount(tabId)
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
            webViews.remove(tabId)
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

    private fun injectCookieConsentCss(view: WebView) {
        if (workerSettings.hideCookieConsent) {
            view.evaluateJavascript(contentBlocker.consentScript, null)
        }
    }

    private fun finalizeCookieConsentBlocking(view: WebView) {
        if (workerSettings.hideCookieConsent) {
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
                        return interceptServiceWorkerRequest(request, isIncognito = false)
                    }
                },
            )
    }

    private fun configureIncognitoServiceWorkerBlocking(webView: WebView) {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) return
        runCatching {
            WebViewCompat.getProfile(webView).serviceWorkerController.setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(
                        request: WebResourceRequest,
                    ): WebResourceResponse? = interceptServiceWorkerRequest(request, isIncognito = true)
                },
            )
        }
    }

    private fun interceptServiceWorkerRequest(
        request: WebResourceRequest,
        isIncognito: Boolean,
    ): WebResourceResponse? {
        if (!workerSettings.blockAdsAndTrackers) return null
        val relevantPageUrls = tabs.asSequence()
            .filter { it.isIncognito == isIncognito }
            .mapNotNull { pageUrls[it.id] }
            .toList()
        if (relevantPageUrls.isEmpty()) return null
        val requestUrl = request.url.toString()
        return if (relevantPageUrls.all { pageUrl -> contentBlocker.shouldBlock(requestUrl, pageUrl) }) {
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
                cookies = cookieManagerFor(tabId).getCookie(url),
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

    private fun queueBlockedCount(tabId: String) {
        if (destroyed) return
        pendingBlockedCounts.getOrPut(tabId) { AtomicInteger() }.incrementAndGet()
        if (blockerFlushScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(blockerCountFlush, BLOCKER_COUNT_FLUSH_DELAY_MS)
        }
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
                if (delta > 0) updateTab(tabId) { it.copy(blockedCount = it.blockedCount + delta) }
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

    private fun persist() = store.saveTabs(tabs.toList(), selectedTabId)

    private fun newTabState(
        url: String = BLANK_URL,
        nowMillis: Long = System.currentTimeMillis(),
        isIncognito: Boolean = false,
    ) = BrowserTab(
        id = UUID.randomUUID().toString(),
        lastAccessedAt = nowMillis,
        isIncognito = isIncognito,
        url = url,
        title = if (url == BLANK_URL) "" else AddressResolver.displayText(url),
        isLoading = url != BLANK_URL,
    )

    private fun touchTab(tabId: String, nowMillis: Long) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) tabs[index] = tabs[index].copy(lastAccessedAt = nowMillis)
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
        expiredIds.forEach(::removeTabResources)
        tabs.removeAll { it.id in expiredIds }
        if (removedIncognitoTab && tabs.none(BrowserTab::isIncognito)) {
            clearIncognitoProfile()
        }
        if (tabs.isEmpty()) {
            tabs += newTabState(nowMillis = nowMillis)
            selectedTabId = tabs.first().id
        }
        if (persistChanges) persist()
        return true
    }

    private fun removeTabResources(
        tabId: String,
        preserveFaviconGeneration: Boolean = false,
    ) {
        popupOpeners.remove(tabId)
        popupOpeners.entries.removeAll { (_, openerId) -> openerId == tabId }
        webViews.remove(tabId)?.let(::destroyWebView)
        pageUrls.remove(tabId)
        pendingBlockedCounts.remove(tabId)
        bottomBarCompactStates.remove(tabId)
        previews.remove(tabId)
        previewRepository.delete(tabId)
        invalidateFavicon(tabId)
        if (!preserveFaviconGeneration) faviconGenerations.remove(tabId)
    }

    private fun cookieManagerFor(tabId: String): CookieManager =
        webViews[tabId]?.let(::cookieManagerFor) ?: CookieManager.getInstance()

    private fun cookieManagerFor(webView: WebView): CookieManager =
        if (supportsMultipleProfiles()) {
            runCatching { WebViewCompat.getProfile(webView).cookieManager }
                .getOrDefault(CookieManager.getInstance())
        } else {
            CookieManager.getInstance()
        }

    private fun supportsMultipleProfiles(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private fun clearIncognitoProfile() {
        if (!supportsMultipleProfiles()) return
        runCatching {
            ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE_NAME)
        }
    }

    private fun destroyWebView(webView: WebView) {
        (webView.parent as? FrameLayout)?.removeView(webView)
        webView.setOnScrollChangeListener(null)
        webView.stopLoading()
        webView.loadUrl(BLANK_URL)
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

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
        const val INCOGNITO_PROFILE_NAME = "candy_incognito"
        const val PREVIEW_CAPTURE_IDLE_DELAY_MS = 220L
        const val BLOCKER_COUNT_FLUSH_DELAY_MS = 250L
    }
}
