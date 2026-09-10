package dev.sk2andy.materialbrowser.browser.systemwebview

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.UiThread
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.blocking.ContentBlocker
import dev.sk2andy.materialbrowser.blocking.ConsentRequestRules
import dev.sk2andy.materialbrowser.blocking.CandyDecisionAction
import dev.sk2andy.materialbrowser.blocking.CandyMatcherSnapshot
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineCapabilities
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserEngineAuthPromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineAuthPromptResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineBooleanResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineContentPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineFileCapture
import dev.sk2andy.materialbrowser.browser.BrowserEngineFilePromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineFilePromptResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineMediaPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineNavigationTarget
import dev.sk2andy.materialbrowser.browser.BrowserEnginePermissionSetResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.browser.BrowserEngineWebPromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineWebPromptResponse
import dev.sk2andy.materialbrowser.browser.BrowserWebPromptKind
import dev.sk2andy.materialbrowser.browser.DesktopSiteRules
import dev.sk2andy.materialbrowser.browser.DesktopViewportScript
import dev.sk2andy.materialbrowser.browser.VideoAutoplayBlockerScript
import dev.sk2andy.materialbrowser.browser.WebContentTopInsetMode
import dev.sk2andy.materialbrowser.browser.WebContentTopInsetRules
import dev.sk2andy.materialbrowser.browser.WebContentTopInsetScript
import dev.sk2andy.materialbrowser.browser.engine.AndroidBrowserEngineFactory
import dev.sk2andy.materialbrowser.browser.systemwebview.credentials.SystemWebViewCredentials
import dev.sk2andy.materialbrowser.browser.systemwebview.commands.WebViewProfileCookies
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.GeckoAndroidPermissionRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoAuthPromptListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowsingData
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserHistoryState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCandyTrailHistoryEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCandyTrailHistoryTracker
import dev.sk2andy.materialbrowser.browser.gecko.GeckoContentPermissionRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoContextDownloadRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadCancellation
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadResponseListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExternalDownloadResponse
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFilePromptListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFindResult
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFullscreenStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMainFrameNavigationRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaPermissionRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestDecision
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNewSessionListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEvent
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingHostState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingInteractionDelegate
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetHost
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetLayout
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsets
import dev.sk2andy.materialbrowser.browser.gecko.GeckoWebPromptListener
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRuntime
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory
import dev.sk2andy.materialbrowser.data.UserScriptValueStore
import dev.sk2andy.materialbrowser.reader.ReaderExtractionScript
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Android System WebView runtime. No Gecko type is instantiated by this adapter. */
private data class SystemWebViewRequestPrivacyState(
    val policy: GeckoPrivacyPolicy,
    val candyMatcher: CandyMatcherSnapshot,
)

internal class SystemWebViewBrowserEngineFactory(
    private val context: Context,
) : AndroidBrowserEngineFactory {
    override val kind = AndroidBrowserEngineKind.SystemWebView
    override val capabilities = AndroidBrowserEngineCapabilities.SystemWebView

    private var scripts = emptyList<UserScript>()
    private var toppingDelegate = GeckoToppingInteractionDelegate.None
    private var toppingStateListener: (GeckoToppingHostState) -> Unit = {}
    private var blockThirdPartyCookies = true
    private var fontSizeFactor = 1f
    private val sessions = mutableSetOf<SystemWebViewBrowserEngineSession>()
    private val knownCookieManagers = mutableListOf<CookieManager>()
    private val incognitoProfileName = INCOGNITO_PROFILE_NAME
    private val toppingRuntime = UserScriptRuntime(
        valueStore = UserScriptValueStore(context.applicationContext),
        onMenuCommandsChanged = { tabId, commands ->
            toppingDelegate.onMenuCommandsChanged(tabId, commands)
        },
        onOpenTab = { request -> toppingDelegate.onOpenTab(request) },
    )
    private val contentBlocker = ContentBlocker(context.applicationContext)

    init {
        cleanupStalePrivateProfiles()
        contentBlocker.prepareConsentScript()
        contentBlocker.prepareCosmeticRules()
    }

    override fun reconcileToppings(scripts: List<UserScript>) {
        this.scripts = scripts.toList()
        sessions.forEach { it.installToppings(this.scripts) }
        toppingStateListener(GeckoToppingHostState.Ready)
    }

    override fun setToppingHostStateListener(listener: (GeckoToppingHostState) -> Unit) {
        toppingStateListener = listener
        listener(GeckoToppingHostState.Ready)
    }

    override fun setToppingInteractionDelegate(delegate: GeckoToppingInteractionDelegate) {
        toppingDelegate = delegate
    }

    override fun invokeToppingMenuCommand(command: UserScriptMenuCommand) {
        toppingRuntime.invokeMenuCommand(command)
    }

    override fun clearToppingValues(scriptId: String) {
        toppingRuntime.clearValues(scriptId)
    }

    override fun clearBrowsingData(data: GeckoBrowsingData, onComplete: (Boolean) -> Unit) {
        when (data) {
            GeckoBrowsingData.AllCaches -> {
                sessions.forEach { it.clearCache() }
                onComplete(true)
            }
            GeckoBrowsingData.Cookies -> clearCookies(onComplete)
            GeckoBrowsingData.All -> {
                sessions.forEach { it.clearAllData() }
                android.webkit.WebStorage.getInstance().deleteAllData()
                clearCookies(onComplete)
            }
        }
    }

    override fun clearAllData(onComplete: (Boolean) -> Unit) =
        clearBrowsingData(GeckoBrowsingData.All, onComplete)

    override fun requestProfileDataDeletion(profileId: String): Boolean {
        if (!supportsMultiProfile() || profileId.isBlank()) return false
        return runCatching {
            val name = WebViewProfileRules.isolatedProfileName(profileId)
            val store = ProfileStore.getInstance()
            name !in store.allProfileNames || store.deleteProfile(name)
        }.getOrDefault(false)
    }

    override fun setBlockThirdPartyCookies(blocked: Boolean) {
        blockThirdPartyCookies = blocked
        sessions.forEach { it.setGlobalThirdPartyCookieBlocking(blocked) }
    }

    override fun setWebContentFontSizeFactor(factor: Float) {
        fontSizeFactor = factor.coerceIn(0.5f, 2f)
        sessions.forEach { it.setFontSizeFactor(fontSizeFactor) }
    }

    override fun clearPrivateData() {
        if (!supportsMultiProfile()) return
        deleteProfileIfPresent(incognitoProfileName)
    }

    override fun shutdown() {
        if (supportsMultiProfile()) deleteProfileIfPresent(incognitoProfileName)
        knownCookieManagers.clear()
    }

    override fun create(
        tabId: String,
        profileId: String,
        isolationEnabled: Boolean,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy,
        privacyEventSink: GeckoPrivacyEventSink,
        trailHistoryEventSink: GeckoCandyTrailHistoryEventSink,
        eventSink: BrowserEngineEventSink,
    ): AndroidBrowserEngineSessionPort = SystemWebViewBrowserEngineSession(
        context = context,
        tabId = tabId,
        profileId = profileId,
        isolationEnabled = isolationEnabled,
        isPrivate = isPrivate,
        incognitoProfileName = incognitoProfileName,
        multiProfileSupported = supportsMultiProfile(),
        contentBlocker = contentBlocker,
        toppingRuntime = toppingRuntime,
        initialScripts = scripts,
        initialPrivacyPolicy = privacyPolicy,
        privacyEventSink = privacyEventSink,
        trailHistoryEventSink = trailHistoryEventSink,
        eventSink = eventSink,
        blockThirdPartyCookies = blockThirdPartyCookies,
        fontSizeFactor = fontSizeFactor,
        onClosed = sessions::remove,
    ).also { session ->
        sessions += session
        session.cookieManager()?.let(::rememberCookieManager)
    }

    private fun clearCookies(onComplete: (Boolean) -> Unit) {
        val managers = (knownCookieManagers +
            sessions.mapNotNull(SystemWebViewBrowserEngineSession::cookieManager))
            .distinctBy { manager -> System.identityHashCode(manager) }
            .ifEmpty { listOf(CookieManager.getInstance()) }
        val remaining = AtomicInteger(managers.size)
        managers.forEach { manager ->
            manager.removeAllCookies {
                manager.flush()
                if (remaining.decrementAndGet() == 0) onComplete(true)
            }
        }
    }

    companion object {
        private const val LEGACY_INCOGNITO_PROFILE_PREFIX = "candy_incognito_v1_"
        private const val INCOGNITO_PROFILE_PREFIX = "candy_incognito_v2_"
        private const val INCOGNITO_PROFILE_NAME = "${INCOGNITO_PROFILE_PREFIX}runtime"

        fun supportsMultiProfile(): Boolean =
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

        private fun cleanupStalePrivateProfiles() {
            if (!supportsMultiProfile()) return
            runCatching {
                ProfileStore.getInstance().allProfileNames
                    .filter { profileName ->
                        profileName.startsWith(INCOGNITO_PROFILE_PREFIX) ||
                            profileName.startsWith(LEGACY_INCOGNITO_PROFILE_PREFIX)
                    }
                    .forEach(::deleteProfileIfPresent)
            }
        }

        private fun deleteProfileIfPresent(profileName: String) {
            runCatching {
                val profileStore = ProfileStore.getInstance()
                if (profileName in profileStore.allProfileNames) {
                    profileStore.deleteProfile(profileName)
                }
            }
        }
    }

    private fun rememberCookieManager(manager: CookieManager) {
        if (knownCookieManagers.none { it === manager }) knownCookieManagers += manager
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
private class SystemWebViewBrowserEngineSession(
    context: Context,
    override val tabId: String,
    profileId: String,
    isolationEnabled: Boolean,
    private val isPrivate: Boolean,
    incognitoProfileName: String,
    multiProfileSupported: Boolean,
    private val contentBlocker: ContentBlocker,
    private val toppingRuntime: UserScriptRuntime,
    initialScripts: List<UserScript>,
    initialPrivacyPolicy: GeckoPrivacyPolicy,
    private val privacyEventSink: GeckoPrivacyEventSink,
    trailHistoryEventSink: GeckoCandyTrailHistoryEventSink,
    private val eventSink: BrowserEngineEventSink,
    blockThirdPartyCookies: Boolean,
    fontSizeFactor: Float,
    private val onClosed: (SystemWebViewBrowserEngineSession) -> Unit,
) : AndroidBrowserEngineSessionPort {
    private val appContext = context.applicationContext
    private val profileId = profileId
    private val webView = SystemWebViewHost(context, ::onSafeAreaFallback)
    private val host = webView
    private var closed = false
    private var active = true
    private var desktopMode = false
    private var desktopViewportDomain: String? = null
    private var autoplayBlocked = true
    private var privacyPolicy = initialPrivacyPolicy
    @Volatile
    private var requestPrivacyState = SystemWebViewRequestPrivacyState(
        policy = initialPrivacyPolicy,
        candyMatcher = CandyMatcherSnapshot.compile(initialPrivacyPolicy.candyRules),
    )
    private var policyRevision = 0L
    private var navigationRequestListener: GeckoNavigationRequestListener? = null
    private var newSessionListener: GeckoNewSessionListener? = null
    private var downloadResponseListener: GeckoDownloadResponseListener? = null
    private var filePromptListener: GeckoFilePromptListener? = null
    private var androidPermissionListener: GeckoAndroidPermissionRequestListener? = null
    private var contentPermissionListener: GeckoContentPermissionRequestListener? = null
    private var mediaPermissionListener: GeckoMediaPermissionRequestListener? = null
    private var authPromptListener: GeckoAuthPromptListener? = null
    private var webPromptListener: GeckoWebPromptListener? = null
    private var mediaStateListener: GeckoMediaSessionStateListener? = null
    private var fullscreenStateListener: GeckoFullscreenStateListener? = null
    private var scrollListener: BrowserEngineScrollListener? = null
    private var contentTargetListener: dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener? = null
    private var autoplayScriptHandler: ScriptHandler? = null
    private var topInsetScriptHandler: ScriptHandler? = null
    private var mediaScriptHandler: ScriptHandler? = null
    private var desktopViewportScriptHandler: ScriptHandler? = null
    private val mediaBridgeToken = UUID.randomUUID().toString().replace("-", "")
    private var customFullscreenView: View? = null
    private var customFullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lastFullscreenState = false
    private var latestMediaState = GeckoMediaSessionState()
    private var consentReadyCallbackRegistered = false
    private var cosmeticsReadyCallbackRegistered = false
    private var lastFindQuery: String? = null
    private var lastLoadFailed = false
    private var lastMainFrameHttpResponse: Pair<String, Int>? = null
    @Volatile
    private var currentPageUrl: String? = initialPrivacyPolicy.pageHost
    private val defaultUserAgent: String
    private val assignedProfileName: String?
    private val trailHistoryTracker = GeckoCandyTrailHistoryTracker(tabId) { event ->
        trailHistoryEventSink.onHistoryEvent(this, event)
    }

    private companion object {
        const val PLATFORM_STATE_URL = "candy.system_webview.state_url"
        const val POPUP_CAPTURE_TIMEOUT_MILLIS = 5_000L
    }

    init {
        require(tabId.isNotBlank()) { "A System WebView engine session needs a tab ID" }
        if (multiProfileSupported) {
            assignedProfileName = when {
                isPrivate -> incognitoProfileName
                isolationEnabled -> WebViewProfileRules.isolatedProfileName(profileId)
                else -> null
            }
            if (assignedProfileName != null) WebViewCompat.setProfile(webView, assignedProfileName)
        } else {
            assignedProfileName = null
        }
        defaultUserAgent = webView.settings.userAgentString
        configureWebView(fontSizeFactor)
        installTopInsetScript()
        installToppings(initialScripts)
        setGlobalThirdPartyCookieBlocking(blockThirdPartyCookies)
        applyPrivacyPolicy()
    }

    @UiThread
    override fun execute(command: BrowserEngineCommand) {
        if (closed) return
        when (command.type) {
            BrowserEngineCommandType.Load -> webView.loadUrl(requireNotNull(command.address))
            BrowserEngineCommandType.Back -> if (webView.canGoBack()) webView.goBack()
            BrowserEngineCommandType.Forward -> if (webView.canGoForward()) webView.goForward()
            BrowserEngineCommandType.Reload -> webView.reload()
            BrowserEngineCommandType.Stop -> webView.stopLoading()
            BrowserEngineCommandType.Close -> close()
        }
    }

    override fun createView(context: Context): View {
        check(!closed) { "Cannot bind a closed browser engine session" }
        if (!isPrivate) SystemWebViewCredentials.onAttached(webView)
        return host
    }

    override fun awaitContentPresented(listener: () -> Unit) {
        if (!closed) host.post(listener)
    }

    override fun releaseView(view: View) {
        if (view === host) (host.parent as? ViewGroup)?.removeView(host)
    }

    override fun setActive(active: Boolean) {
        if (closed || this.active == active) return
        this.active = active
        if (active) webView.onResume() else webView.onPause()
    }

    override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) {
        mediaStateListener = listener
        listener?.onStateChanged(latestMediaState)
    }

    override fun setFullscreenStateListener(listener: GeckoFullscreenStateListener?) {
        fullscreenStateListener = listener
    }

    override fun setScrollListener(listener: BrowserEngineScrollListener?) {
        scrollListener = listener
    }

    override fun setNativeVerticalScrollBarEnabled(enabled: Boolean) {
        webView.isVerticalScrollBarEnabled = enabled
    }

    override fun setContentTargetListener(
        listener: dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener?,
    ) {
        contentTargetListener = listener
    }

    override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) {
        navigationRequestListener = listener
    }

    override fun setNewSessionListener(listener: GeckoNewSessionListener?) {
        newSessionListener = listener
    }

    override fun setDownloadResponseListener(listener: GeckoDownloadResponseListener?) {
        downloadResponseListener = listener
    }

    override fun startContextDownload(
        request: GeckoContextDownloadRequest,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? = startDownload(
        url = request.url,
        contentDisposition = request.suggestedFileName?.let { "attachment; filename=\"$it\"" },
        mimeType = null,
        listener = listener,
    )

    override fun setFilePromptListener(listener: GeckoFilePromptListener?) {
        filePromptListener = listener
    }

    override fun setAndroidPermissionRequestListener(
        listener: GeckoAndroidPermissionRequestListener?,
    ) {
        androidPermissionListener = listener
    }

    override fun setContentPermissionRequestListener(
        listener: GeckoContentPermissionRequestListener?,
    ) {
        contentPermissionListener = listener
    }

    override fun setMediaPermissionRequestListener(
        listener: GeckoMediaPermissionRequestListener?,
    ) {
        mediaPermissionListener = listener
    }

    override fun setAuthPromptListener(listener: GeckoAuthPromptListener?) {
        authPromptListener = listener
    }

    override fun setWebPromptListener(listener: GeckoWebPromptListener?) {
        webPromptListener = listener
    }

    override fun setVideoAutoplayBlocked(blocked: Boolean) {
        if (closed || autoplayBlocked == blocked) return
        autoplayBlocked = blocked
        webView.settings.mediaPlaybackRequiresUserGesture = blocked
        installAutoplayPolicy()
        webView.reload()
    }

    override fun setAudioMuted(muted: Boolean) {
        if (closed) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            WebViewCompat.setAudioMuted(webView, muted)
        } else {
            webView.evaluateJavascript(
                "document.querySelectorAll('video,audio').forEach(m=>m.muted=${muted});",
                null,
            )
        }
    }

    override fun executeMediaCommand(command: GeckoMediaCommand) {
        if (closed) return
        val action = when (command) {
            GeckoMediaCommand.Play -> "m.play()"
            GeckoMediaCommand.Pause -> "m.pause()"
            GeckoMediaCommand.Stop -> "m.pause();m.currentTime=0"
        }
        webView.evaluateJavascript(
            "(()=>{const m=[...document.querySelectorAll('video,audio')]" +
                ".find(m=>!m.paused&&!m.ended)||document.querySelector('video,audio');" +
                "if(m){$action;}})()",
            null,
        )
    }

    override fun seekMedia(positionMillis: Long) {
        if (!closed) {
            webView.evaluateJavascript(
                "const m=document.querySelector('video,audio');if(m)m.currentTime=${positionMillis.coerceAtLeast(0) / 1000.0}",
                null,
            )
        }
    }

    override fun goToHistoryIndex(index: Int) {
        val history = webView.copyBackForwardList()
        val offset = index - history.currentIndex
        if (!closed && offset != 0 && webView.canGoBackOrForward(offset)) {
            webView.goBackOrForward(offset)
        }
    }

    override fun historyUrlAtOffset(offset: Int): String? {
        val history = webView.copyBackForwardList()
        return history.getItemAtIndex(history.currentIndex + offset)?.url
    }

    override fun capturePreview(
        targetWidthPx: Int,
        visibleViewHeightPx: Int,
        maximumTargetHeightPx: Int,
        onComplete: (Bitmap?) -> Unit,
    ): BrowserEnginePreviewCapture? {
        if (closed || targetWidthPx <= 0 || visibleViewHeightPx <= 0) return null
        val cancelled = AtomicBoolean(false)
        webView.post {
            if (closed || cancelled.get() || webView.width <= 0 || webView.height <= 0) {
                onComplete(null)
                return@post
            }
            val targetHeight = minOf(
                maximumTargetHeightPx.coerceAtLeast(1),
                (visibleViewHeightPx.toLong() * targetWidthPx / webView.width.coerceAtLeast(1))
                    .toInt()
                    .coerceAtLeast(1),
            )
            val bitmap = runCatching {
                Bitmap.createBitmap(targetWidthPx, targetHeight, Bitmap.Config.ARGB_8888).also {
                    val canvas = Canvas(it)
                    canvas.scale(
                        targetWidthPx.toFloat() / webView.width,
                        targetHeight.toFloat() / visibleViewHeightPx,
                    )
                    webView.draw(canvas)
                }
            }.getOrNull()
            if (!cancelled.get()) onComplete(bitmap) else bitmap?.recycle()
        }
        return BrowserEnginePreviewCapture { cancelled.set(true) }
    }

    override fun findInPage(
        query: String,
        forward: Boolean,
        onComplete: (GeckoFindResult?) -> Unit,
    ) {
        if (closed) return onComplete(null)
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            onComplete(
                GeckoFindResult(
                    activeMatchOrdinal = activeMatchOrdinal,
                    matchCount = numberOfMatches,
                    isDoneCounting = isDoneCounting,
                ),
            )
        }
        when {
            query.isBlank() -> {
                lastFindQuery = null
                webView.clearMatches()
            }
            query != lastFindQuery -> {
                lastFindQuery = query
                webView.findAllAsync(query)
            }
            else -> webView.findNext(forward)
        }
    }

    override fun clearFindInPage() {
        lastFindQuery = null
        webView.setFindListener(null)
        webView.clearMatches()
    }

    override fun printPage(): Boolean {
        val printManager = webView.context.getSystemService(PrintManager::class.java) ?: return false
        printManager.print(
            webView.title?.takeIf(String::isNotBlank) ?: "Candy page",
            webView.createPrintDocumentAdapter(webView.title ?: "Candy page"),
            PrintAttributes.Builder().build(),
        )
        return true
    }

    override fun setDesktopMode(enabled: Boolean) {
        if (closed) return
        val nextDomain = DesktopSiteRules.domainForUrl(currentPageUrl)
            ?: DesktopSiteRules.normalizedDomain(privacyPolicy.pageHost)
        if (desktopMode == enabled && (!enabled || desktopViewportDomain == nextDomain)) return
        desktopMode = enabled
        desktopViewportDomain = nextDomain.takeIf { enabled }
        webView.settings.userAgentString = if (enabled) {
            DesktopSiteRules.desktopUserAgent(defaultUserAgent)
        } else {
            defaultUserAgent
        }
        webView.settings.useWideViewPort = enabled
        webView.settings.loadWithOverviewMode = enabled
        installDesktopViewportPolicy()
    }

    override fun scrollMetrics(): BrowserEngineScrollMetrics = BrowserEngineScrollMetrics(
        offsetPx = webView.scrollY,
        extentPx = webView.height,
        rangePx = host.contentScrollRangePx().coerceAtLeast(webView.height),
    )

    override fun scrollToVerticalOffset(offsetPx: Int) {
        webView.scrollTo(webView.scrollX, offsetPx.coerceAtLeast(0))
    }

    override fun platformViewStateSnapshot(): Bundle? {
        if (closed || isPrivate) return null
        val state = Bundle()
        val history = runCatching { webView.saveState(state) }.getOrNull()
        val currentUrl = history?.currentItem?.url
        if (history == null || history.size == 0 || currentUrl.isNullOrBlank()) return null
        state.putString(PLATFORM_STATE_URL, currentUrl)
        return state
    }

    override fun restorePlatformViewState(state: Bundle, expectedUrl: String): Boolean {
        if (closed || isPrivate) return false
        if (state.getString(PLATFORM_STATE_URL) != expectedUrl) return false
        val history = runCatching { webView.restoreState(state) }.getOrNull() ?: return false
        val restoredUrl = history.currentItem?.url ?: return false
        if (history.size == 0 || restoredUrl != expectedUrl) return false
        currentPageUrl = restoredUrl
        return true
    }

    override fun exitFullscreen() {
        if (closed) return
        webView.evaluateJavascript(
            "document.fullscreenElement ? document.exitFullscreen() : undefined",
            null,
        )
        dismissCustomFullscreenView(notify = true)
    }

    override fun extractPageForReader(onComplete: (String?) -> Unit) {
        if (closed) onComplete(null)
        else webView.evaluateJavascript(ReaderExtractionScript.javascript, onComplete)
    }

    override fun updatePrivacyPolicy(
        policy: GeckoPrivacyPolicy,
        reloadOnCookiePermissionChange: Boolean,
        onReady: () -> Unit,
    ) {
        if (closed) return
        val cookiePolicyChanged = privacyPolicy.blockThirdPartyCookies != policy.blockThirdPartyCookies ||
            privacyPolicy.allowThirdPartyCookiesForSite != policy.allowThirdPartyCookiesForSite
        val matcher = if (privacyPolicy.candyRules == policy.candyRules) {
            requestPrivacyState.candyMatcher
        } else {
            CandyMatcherSnapshot.compile(policy.candyRules)
        }
        privacyPolicy = policy
        requestPrivacyState = SystemWebViewRequestPrivacyState(policy, matcher)
        policyRevision++
        applyPrivacyPolicy()
        if (reloadOnCookiePermissionChange && cookiePolicyChanged) webView.reload()
        onReady()
    }

    fun installToppings(scripts: List<UserScript>) {
        if (!closed) toppingRuntime.install(tabId, webView, scripts, isPrivate)
    }

    fun setGlobalThirdPartyCookieBlocking(blocked: Boolean) {
        if (closed) return
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            webView,
            !blocked || privacyPolicy.allowThirdPartyCookiesForSite,
        )
    }

    fun setFontSizeFactor(factor: Float) {
        if (!closed) webView.settings.textZoom = (factor.coerceIn(0.5f, 2f) * 100).toInt()
    }

    fun clearCache() {
        if (!closed) webView.clearCache(true)
    }

    fun cookieManager(): CookieManager? =
        if (closed) null else WebViewProfileCookies.managerFor(webView)

    fun clearAllData() {
        if (closed) return
        webView.clearCache(true)
        webView.clearFormData()
        webView.clearHistory()
    }

    private fun close(publishClosedEvent: Boolean = true) {
        if (closed) return
        val closedEvent = if (publishClosedEvent) {
            BrowserEngineEvent(
                tabId = tabId,
                type = BrowserEngineEventType.Closed,
                address = currentPageUrl,
                title = runCatching { webView.title }.getOrNull(),
                canGoBack = runCatching { webView.canGoBack() }.getOrDefault(false),
                canGoForward = runCatching { webView.canGoForward() }.getOrDefault(false),
                failureDescription = null,
                isLoading = false,
            )
        } else {
            null
        }
        closed = true
        toppingRuntime.remove(webView)
        autoplayScriptHandler?.remove()
        topInsetScriptHandler?.remove()
        desktopViewportScriptHandler?.remove()
        removeMediaBridge()
        dismissCustomFullscreenView(notify = false)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        (host.parent as? ViewGroup)?.removeView(host)
        webView.destroy()
        onClosed(this)
        closedEvent?.let(eventSink::onEngineEvent)
    }

    private fun configureWebView(fontSizeFactor: Float) {
        webView.settings.apply {
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
            safeBrowsingEnabled = true
            mediaPlaybackRequiresUserGesture = autoplayBlocked
            textZoom = (fontSizeFactor.coerceIn(0.5f, 2f) * 100).toInt()
            enablePinchZoom()
            applyWebsiteDarkeningPolicy(forceDarkWebsites = false)
        }
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (isPrivate) {
            webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            @Suppress("DEPRECATION")
            webView.settings.saveFormData = false
        } else {
            SystemWebViewCredentials.configure(webView)
        }
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            scrollListener?.onScrollChanged(
                dev.sk2andy.materialbrowser.browser.BrowserEngineScrollEvent(
                    scrollYPx = scrollY,
                ),
            )
        }
        webView.setOnLongClickListener {
            val hit = webView.hitTestResult
            val target = WebViewHitTestResolver.resolve(hit.type, hit.extra)
            target?.let { contentTargetListener?.onLongPress(it) }
            target != null
        }
        webView.webViewClient = browserClient()
        webView.webChromeClient = chromeClient()
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val metadata = dev.sk2andy.materialbrowser.browser.BrowserEngineDownloadResponse(
                url = url,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
            )
            downloadResponseListener?.onDownloadResponse(
                GeckoExternalDownloadResponse(
                    metadata = metadata,
                    startTransfer = { listener ->
                        startDownload(url, contentDisposition, mimeType, listener)
                    },
                    discard = {},
                ),
            )
        }
        installMediaBridge()
        installAutoplayPolicy()
    }

    private fun browserClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (closed) return true
            val url = request.url.toString()
            val decision = navigationRequestListener?.onNavigationRequest(
                GeckoMainFrameNavigationRequest(
                    url = url,
                    isRedirect = request.isRedirect,
                    hasUserGesture = request.hasGesture(),
                    isDirectNavigation = !request.isRedirect,
                    target = BrowserEngineNavigationTarget.Current,
                ),
            ) ?: GeckoNavigationRequestDecision.Allow
            return decision == GeckoNavigationRequestDecision.Deny ||
                dispatchDirectPackageDownload(url)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val requestState = requestPrivacyState
            val policy = requestState.policy
            val url = request.url.toString()
            if (request.isForMainFrame) return null
            val candyDecision = if (policy.blockAdsAndTrackers) {
                requestState.candyMatcher.decide(
                    requestUrl = url,
                    pageUrl = currentPageUrl,
                    profileId = profileId,
                    isForMainFrame = false,
                )
            } else {
                null
            }
            val blocked = when (candyDecision?.action) {
                CandyDecisionAction.Allow -> false
                CandyDecisionAction.Block -> true
                null -> ConsentRequestRules.shouldBlock(
                    isForMainFrame = false,
                    cookieBannerRemovalEnabled = policy.hideCookieConsent &&
                        !policy.cookieBannerRemovalDisabled,
                    sitePaused = false,
                    requestHost = request.url.host,
                ) || (
                    policy.blockAdsAndTrackers &&
                        contentBlocker.shouldBlock(url, currentPageUrl)
                    )
            }
            if (!blocked) return null
            privacyEventSink.onEvent(
                GeckoPrivacyEvent(
                    requestUrl = url,
                    pageUrl = currentPageUrl,
                    ruleId = candyDecision?.ruleId,
                    wasBlocked = true,
                    isBuiltIn = true,
                    isCompatibilityObservation = false,
                ),
            )
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                204,
                "Blocked",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            lastLoadFailed = false
            lastMainFrameHttpResponse = null
            currentPageUrl = url
            toppingRuntime.clearMenuCommands(webView)
            latestMediaState = GeckoMediaSessionState()
            mediaStateListener?.onStateChanged(latestMediaState)
            updateFullscreenState(false)
            publish(BrowserEngineEventType.NavigationStarted, address = url, isLoading = true)
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            applyDocumentCosmetics(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            currentPageUrl = url
            applyDocumentCosmetics(url)
            publishHistoryState(view)
            val httpStatusCode = lastMainFrameHttpResponse
                ?.takeIf { (responseUrl) -> responseUrl == url }
                ?.second
            publish(
                if (lastLoadFailed) {
                    BrowserEngineEventType.NavigationFailed
                } else {
                    BrowserEngineEventType.NavigationCommitted
                },
                address = url,
                isLoading = false,
                failureDescription = "System WebView navigation failed".takeIf { lastLoadFailed },
                httpStatusCode = httpStatusCode,
            )
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: android.webkit.WebResourceError,
        ) {
            if (request.isForMainFrame) lastLoadFailed = true
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame) {
                lastMainFrameHttpResponse = request.url.toString() to errorResponse.statusCode
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            publish(BrowserEngineEventType.StateChanged, address = url)
            publishHistoryState(view)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            if (!closed) {
                publish(
                    type = BrowserEngineEventType.Crashed,
                    address = currentPageUrl,
                    title = null,
                    isLoading = false,
                    failureDescription = "System WebView renderer exited",
                )
                close(publishClosedEvent = false)
            }
            return true
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView,
            handler: HttpAuthHandler,
            host: String,
            realm: String,
        ) {
            val listener = authPromptListener ?: return handler.cancel()
            listener.onAuthPrompt(
                BrowserEngineAuthPromptRequest(
                    uri = "https://$host",
                    realm = realm,
                    onlyPassword = false,
                    isProxy = false,
                    isCrossOriginSubresource = false,
                    response = object : BrowserEngineAuthPromptResponse {
                        override fun confirm(username: String, password: String) {
                            handler.proceed(username, password)
                        }

                        override fun dismiss() = handler.cancel()
                    },
                ),
            )
        }
    }

    private fun chromeClient() = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String?) {
            publish(BrowserEngineEventType.StateChanged, title = title)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customFullscreenView != null) {
                callback.onCustomViewHidden()
                return
            }
            val activity = webView.context as? Activity ?: return callback.onCustomViewHidden()
            customFullscreenView = view
            customFullscreenCallback = callback
            (activity.window.decorView as? ViewGroup)?.addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            updateFullscreenState(true)
        }

        override fun onHideCustomView() {
            dismissCustomFullscreenView(notify = true)
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(view.context)
            assignedProfileName?.let { profileName -> WebViewCompat.setProfile(popup, profileName) }
            if (isPrivate) {
                popup.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            val destroyPopup = Runnable {
                runCatching { popup.stopLoading() }
                runCatching { popup.destroy() }
            }
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    popupView: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val targetUrl = request.url.toString()
                    if (!contentBlocker.shouldBlockPopup(targetUrl, currentPageUrl)) {
                        navigationRequestListener?.onNavigationRequest(
                            GeckoMainFrameNavigationRequest(
                                url = targetUrl,
                                isRedirect = request.isRedirect,
                                hasUserGesture = isUserGesture || request.hasGesture(),
                                isDirectNavigation = !request.isRedirect,
                                target = BrowserEngineNavigationTarget.New,
                            ),
                        )
                    } else {
                        privacyEventSink.onEvent(
                            GeckoPrivacyEvent(
                                requestUrl = targetUrl,
                                pageUrl = currentPageUrl,
                                ruleId = null,
                                wasBlocked = true,
                                isBuiltIn = true,
                                isCompatibilityObservation = false,
                            ),
                        )
                    }
                    popupView.removeCallbacks(destroyPopup)
                    destroyPopup.run()
                    return true
                }
            }
            popup.postDelayed(destroyPopup, POPUP_CAPTURE_TIMEOUT_MILLIS)
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowFileChooser(
            view: WebView,
            filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            val listener = filePromptListener ?: return false
            listener.onFilePrompt(
                BrowserEngineFilePromptRequest(
                    mimeTypes = fileChooserParams.acceptTypes.filter(String::isNotBlank),
                    allowMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                    capture = if (fileChooserParams.isCaptureEnabled) {
                        BrowserEngineFileCapture.Any
                    } else {
                        BrowserEngineFileCapture.None
                    },
                    response = BrowserEngineFilePromptResponse { values ->
                        filePathCallback.onReceiveValue(
                            values?.map(Uri::parse)?.toTypedArray(),
                        )
                    },
                ),
            )
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val requested = buildSet {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources) {
                    add(SitePermission.Camera)
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) {
                    add(SitePermission.Microphone)
                }
            }
            val listener = mediaPermissionListener ?: return request.deny()
            listener.onMediaPermissionRequest(
                BrowserEngineMediaPermissionRequest(
                    origin = request.origin.toString(),
                    permissions = requested,
                    response = BrowserEnginePermissionSetResponse { allowed ->
                        val resources = buildList {
                            if (SitePermission.Camera in allowed) {
                                add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                            }
                            if (SitePermission.Microphone in allowed) {
                                add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                            }
                        }
                        if (resources.isEmpty()) request.deny()
                        else request.grant(resources.toTypedArray())
                    },
                ),
            )
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            val listener = contentPermissionListener
                ?: return callback.invoke(origin, false, false)
            listener.onContentPermissionRequest(
                BrowserEngineContentPermissionRequest(
                    origin = origin,
                    permission = SitePermission.Location,
                    response = BrowserEngineBooleanResponse { allowed ->
                        callback.invoke(origin, allowed, false)
                    },
                ),
            )
        }

        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean = dispatchPrompt(
            BrowserWebPromptKind.Alert,
            message,
            null,
            onConfirm = { result.confirm() },
            onDismiss = { result.cancel() },
        )

        override fun onJsConfirm(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean = dispatchPrompt(
            BrowserWebPromptKind.Confirm,
            message,
            null,
            onConfirm = { result.confirm() },
            onDismiss = { result.cancel() },
        )

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean = dispatchPrompt(
            BrowserWebPromptKind.Text,
            message,
            defaultValue,
            onConfirm = { value -> result.confirm(value) },
            onDismiss = { result.cancel() },
        )

        override fun onJsBeforeUnload(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean = dispatchPrompt(
            BrowserWebPromptKind.BeforeUnload,
            message,
            null,
            onConfirm = { result.confirm() },
            onDismiss = { result.cancel() },
        )
    }

    private fun publishHistoryState(view: WebView) {
        val history = view.copyBackForwardList()
        if (history.currentIndex !in 0 until history.size) return
        trailHistoryTracker.onHistoryStateChanged(
            GeckoBrowserHistoryState(
                urls = List(history.size) { index -> history.getItemAtIndex(index).url },
                currentIndex = history.currentIndex,
                currentTitle = view.title,
            ),
        )
    }

    private fun dispatchPrompt(
        kind: BrowserWebPromptKind,
        message: String,
        defaultValue: String?,
        onConfirm: (String?) -> Unit,
        onDismiss: () -> Unit,
    ): Boolean {
        val listener = webPromptListener ?: return false
        listener.onWebPrompt(
            BrowserEngineWebPromptRequest(
                kind = kind,
                title = webView.title,
                message = message,
                defaultValue = defaultValue,
                response = object : BrowserEngineWebPromptResponse {
                    override fun confirm(value: String?) = onConfirm(value)

                    override fun dismiss() = onDismiss()
                },
            ),
        )
        return true
    }

    private fun applyPrivacyPolicy() {
        setGlobalThirdPartyCookieBlocking(privacyPolicy.blockThirdPartyCookies)
        host.updatePolicy(
            topInsetEnabled = privacyPolicy.topInsetPx > 0,
            navigationGeneration = privacyPolicy.navigationGeneration,
            policyRevision = policyRevision,
            safeAreaLayoutQuietPeriodMillis = privacyPolicy.safeAreaLayoutQuietPeriodMillis,
            safeAreaRequiredFailureCount = privacyPolicy.safeAreaRequiredFailureCount,
        )
    }

    private fun applyDocumentCosmetics(url: String?) {
        val policy = privacyPolicy
        if (policy.hideCookieConsent && !policy.cookieBannerRemovalDisabled) {
            val consentScript = contentBlocker.consentScriptIfReady()
            if (consentScript != null) {
                webView.evaluateJavascript(consentScript, null)
            } else if (!consentReadyCallbackRegistered) {
                consentReadyCallbackRegistered = true
                contentBlocker.onConsentScriptReady { readyScript ->
                    webView.post {
                        if (
                            !closed &&
                            privacyPolicy.hideCookieConsent &&
                            !privacyPolicy.cookieBannerRemovalDisabled
                        ) {
                            webView.evaluateJavascript(readyScript, null)
                        }
                    }
                }
            }
        } else {
            webView.evaluateJavascript(contentBlocker.consentRemovalScript, null)
        }
        if (policy.blockAdsAndTrackers) {
            val script = listOf(
                contentBlocker.adCosmeticDocumentStartScript(
                pageUrl = url,
                pausedHosts = policy.pausedHosts,
                ),
                contentBlocker.adProceduralDocumentStartScript(url),
                contentBlocker.windowOpenDefuserScript(url),
                candyCosmeticScript(url),
            ).filter(String::isNotBlank).joinToString("\n")
            if (script.isNotBlank()) webView.evaluateJavascript(script, null)
            if (!cosmeticsReadyCallbackRegistered) {
                cosmeticsReadyCallbackRegistered = true
                contentBlocker.onCosmeticRulesReady {
                    webView.post { if (!closed) applyDocumentCosmetics(currentPageUrl) }
                }
            }
        }
    }

    private fun candyCosmeticScript(url: String?): String {
        val pageUrl = url ?: return ""
        val selectors = requestPrivacyState.candyMatcher.cosmeticRules(pageUrl, profileId)
            .mapNotNull { it.cosmeticSelector }
        if (selectors.isEmpty()) return ""
        val css = selectors.joinToString("\n") { selector ->
            "$selector { display: none !important; }"
        }
        return "(()=>{const s=document.createElement('style');" +
            "s.dataset.candyUserCosmetic='1';s.textContent=${org.json.JSONObject.quote(css)};" +
            "(document.head||document.documentElement).appendChild(s);})()"
    }

    private fun installTopInsetScript() {
        topInsetScriptHandler = addDocumentStartScript(WebContentTopInsetScript.installScript)
        host.setDocumentStartAvailable(topInsetScriptHandler != null)
    }

    private fun installAutoplayPolicy() {
        autoplayScriptHandler?.remove()
        autoplayScriptHandler = null
        webView.evaluateJavascript(VideoAutoplayBlockerScript.cleanupScript, null)
        if (
            !autoplayBlocked ||
            !supportsDocumentStartInjection()
        ) return
        autoplayScriptHandler = addDocumentStartScript(VideoAutoplayBlockerScript.installScript)
    }

    private fun installDesktopViewportPolicy() {
        desktopViewportScriptHandler?.remove()
        desktopViewportScriptHandler = null
        if (!desktopMode) return
        val domain = desktopViewportDomain ?: return
        desktopViewportScriptHandler = addDocumentStartScript(
            DesktopViewportScript.create(listOf(domain)),
        )
    }

    private fun installMediaBridge() {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !supportsDocumentStartInjection()
        ) return
        runCatching {
            WebViewCompat.addWebMessageListener(
                webView,
                SystemWebViewMediaBridge.NAME,
                setOf("*"),
            ) { _, message, sourceOrigin, _, _ ->
                if (sourceOrigin.scheme?.lowercase() !in setOf("http", "https")) {
                    return@addWebMessageListener
                }
                val state = SystemWebViewMediaBridge.parse(message.data, mediaBridgeToken)
                    ?: return@addWebMessageListener
                latestMediaState = GeckoMediaSessionState(
                        isActive = state.isActive,
                        isPlaying = state.isPlaying,
                        isFullscreen = state.isFullscreen || customFullscreenView != null,
                        title = state.title,
                        currentPositionMillis = state.currentPositionMillis,
                        durationMillis = state.durationMillis,
                        playbackRate = state.playbackRate,
                        sourceUrl = state.sourceUrl,
                        videoWidth = state.videoWidth,
                        videoHeight = state.videoHeight,
                        audioTrackCount = 1,
                        videoTrackCount = if (state.isVideo) 1 else 0,
                    )
                mediaStateListener?.onStateChanged(latestMediaState)
                updateFullscreenState(state.isFullscreen || customFullscreenView != null)
            }
            mediaScriptHandler = addDocumentStartScript(
                SystemWebViewMediaBridge.script(mediaBridgeToken),
            ) ?: error("Document-start injection unavailable")
        }.onFailure { removeMediaBridge() }
    }

    private fun removeMediaBridge() {
        mediaScriptHandler?.remove()
        mediaScriptHandler = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            runCatching {
                WebViewCompat.removeWebMessageListener(webView, SystemWebViewMediaBridge.NAME)
            }
        }
    }

    private fun dismissCustomFullscreenView(notify: Boolean) {
        customFullscreenView?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        customFullscreenView = null
        customFullscreenCallback?.onCustomViewHidden()
        customFullscreenCallback = null
        if (notify) updateFullscreenState(false)
    }

    private fun updateFullscreenState(fullscreen: Boolean) {
        if (lastFullscreenState == fullscreen) return
        lastFullscreenState = fullscreen
        fullscreenStateListener?.onStateChanged(fullscreen)
    }

    private fun pageExecutionWorld(): JavaScriptExecutionWorld? = runCatching {
        WebViewCompat.getExecutionWorld(webView, JavaScriptExecutionWorld.PAGE_WORLD_NAME)
    }.getOrNull()

    private fun supportsDocumentStartInjection(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD) ||
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private fun addDocumentStartScript(script: String): ScriptHandler? = runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
            val pageWorld = pageExecutionWorld() ?: return null
            WebViewCompat.addJavaScriptOnEvent(
                webView,
                script,
                WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                setOf("*"),
                pageWorld,
            )
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        } else {
            return null
        }
    }.getOrNull()

    private fun onSafeAreaFallback(
        navigationGeneration: Int,
        revision: Long,
    ) {
        val policy = privacyPolicy
        if (
            navigationGeneration != policy.navigationGeneration ||
            revision != policyRevision
        ) {
            return
        }
        privacyEventSink.onEvent(
            GeckoPrivacyEvent(
                requestUrl = webView.url.orEmpty(),
                pageUrl = webView.url,
                ruleId = null,
                wasBlocked = false,
                isBuiltIn = true,
                isCompatibilityObservation = false,
                safeAreaFallbackNavigationGeneration = navigationGeneration,
            ),
        )
    }

    private fun startDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? {
        val safeUri = runCatching { Uri.parse(url) }.getOrNull()
            ?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?: return null
        val downloadRequest = BrowserDownloadRequestFactory.create(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
        )
        val fileName = downloadRequest?.fileName ?: "download"
        val effectiveMimeType = if (
            downloadRequest?.let(BrowserDownloadRequestFactory::isAndroidPackage) == true
        ) {
            "application/vnd.android.package-archive"
        } else {
            mimeType
        }
        val request = DownloadManager.Request(safeUri)
            .setMimeType(effectiveMimeType)
            .setTitle(fileName)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName,
            )
        WebViewProfileCookies.managerFor(webView)?.getCookie(url)
            ?.let { request.addRequestHeader("Cookie", it) }
        request.addRequestHeader("User-Agent", webView.settings.userAgentString)
        val manager = appContext.getSystemService(DownloadManager::class.java) ?: return null
        val id = runCatching { manager.enqueue(request) }.getOrElse {
            listener.onFailed(GeckoDownloadFailure.Storage)
            return null
        }
        listener.onStarted(
            GeckoDownloadTransferStart(
                id = id.toInt(),
                fileName = fileName,
                mimeType = effectiveMimeType ?: "application/octet-stream",
                sourceUrl = url,
                referrer = webView.url,
                startedAtMillis = System.currentTimeMillis(),
                totalBytes = -1,
            ),
        )
        return GeckoDownloadCancellation { manager.remove(id) }
    }

    private fun dispatchDirectPackageDownload(url: String): Boolean {
        val fileName = runCatching { Uri.parse(url).lastPathSegment }
            .getOrNull()
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: return false
        val listener = downloadResponseListener ?: return false
        val mimeType = "application/vnd.android.package-archive"
        listener.onDownloadResponse(
            GeckoExternalDownloadResponse(
                metadata = dev.sk2andy.materialbrowser.browser.BrowserEngineDownloadResponse(
                    url = url,
                    contentDisposition = "attachment; filename=\"$fileName\"",
                    mimeType = mimeType,
                ),
                startTransfer = { transferListener ->
                    startDownload(
                        url = url,
                        contentDisposition = "attachment; filename=\"$fileName\"",
                        mimeType = mimeType,
                        listener = transferListener,
                    )
                },
                discard = {},
            ),
        )
        return true
    }

    private fun publish(
        type: BrowserEngineEventType,
        address: String? = webView.url,
        title: String? = webView.title,
        isLoading: Boolean? = null,
        failureDescription: String? = null,
        httpStatusCode: Int? = null,
    ) {
        eventSink.onEngineEvent(
            BrowserEngineEvent(
                tabId = tabId,
                type = type,
                address = address,
                title = title,
                canGoBack = webView.canGoBack(),
                canGoForward = webView.canGoForward(),
                failureDescription = failureDescription,
                isLoading = isLoading,
                httpStatusCode = httpStatusCode,
            ),
        )
    }

}

private class SystemWebViewHost(
    context: Context,
    private val onFallback: (Int, Long) -> Unit,
) : WebView(context), GeckoViewInsetHost {
    private var topInsetPx = 0
    private var layoutTopInsetPx = 0
    private var navigationGeneration = 0
    private var policyRevision = 0L
    private var topInsetEnabled = false
    private var safeAreaLayoutQuietPeriodMillis = 400
    private var safeAreaRequiredFailureCount = 3
    private var documentStartAvailable = false
    private var currentLayout = GeckoViewInsetLayout(
        margins = GeckoViewInsets.Zero,
        rendererSafeAreaOverride = null,
        scrollableTopInsetPx = 0,
    )

    init {
        addJavascriptInterface(
            object {
                @android.webkit.JavascriptInterface
                fun topInsetPx(): Int = topInsetPx

                @android.webkit.JavascriptInterface
                fun viewportCoverAllowed(): Boolean = true

                @android.webkit.JavascriptInterface
                fun navigationGeneration(): Int = navigationGeneration

                @android.webkit.JavascriptInterface
                fun policyRevision(): Long = policyRevision

                @android.webkit.JavascriptInterface
                fun safeAreaLayoutQuietPeriodMillis(): Int = safeAreaLayoutQuietPeriodMillis

                @android.webkit.JavascriptInterface
                fun safeAreaRequiredFailureCount(): Int = safeAreaRequiredFailureCount

                @android.webkit.JavascriptInterface
                fun fallbackToNative(
                    generation: Int,
                    revision: Long,
                ) = post { onFallback(generation, revision) }
            },
            WebContentTopInsetScript.bridgeName,
        )
    }

    fun contentScrollRangePx(): Int = computeVerticalScrollRange()

    fun setDocumentStartAvailable(available: Boolean) {
        if (documentStartAvailable == available) return
        documentStartAvailable = available
        applyCurrentLayout()
    }

    fun updatePolicy(
        topInsetEnabled: Boolean,
        navigationGeneration: Int,
        policyRevision: Long,
        safeAreaLayoutQuietPeriodMillis: Int,
        safeAreaRequiredFailureCount: Int,
    ) {
        val wasEnabled = this.topInsetEnabled
        val settingsChanged =
            this.safeAreaLayoutQuietPeriodMillis != safeAreaLayoutQuietPeriodMillis ||
                this.safeAreaRequiredFailureCount != safeAreaRequiredFailureCount
        this.topInsetEnabled = topInsetEnabled
        this.navigationGeneration = navigationGeneration
        this.safeAreaLayoutQuietPeriodMillis = safeAreaLayoutQuietPeriodMillis
        this.safeAreaRequiredFailureCount = safeAreaRequiredFailureCount
        this.policyRevision = policyRevision
        val previousTopInset = topInsetPx
        applyCurrentLayout()
        if (wasEnabled != topInsetEnabled || previousTopInset != topInsetPx) {
            evaluateJavascript(WebContentTopInsetScript.installScript, null)
        } else if (settingsChanged) {
            evaluateJavascript("globalThis.__candyReconfigureContentTopInset?.();", null)
        }
    }

    override fun updateInsets(
        layout: GeckoViewInsetLayout,
        windowInsets: WindowInsetsCompat,
    ) {
        currentLayout = layout
        layoutTopInsetPx = layout.scrollableTopInsetPx
        val previousTopInset = topInsetPx
        val previousBottomPadding = paddingBottom
        applyCurrentLayout()
        ViewCompat.dispatchApplyWindowInsets(this, windowInsets)
        if (previousTopInset != topInsetPx || previousBottomPadding != paddingBottom) {
            evaluateJavascript(WebContentTopInsetScript.installScript, null)
        }
    }

    private fun applyCurrentLayout() {
        val layout = currentLayout
        val mode = WebContentTopInsetRules.resolve(
            drawsEdgeToEdge = layout.margins.top == 0 && layout.scrollableTopInsetPx == 0,
            forceSafeArea = layout.margins.top > 0,
            scrollableDocumentEnabled = topInsetEnabled && layout.scrollableTopInsetPx > 0,
            documentStartAvailable = documentStartAvailable,
        )
        val nativeTopInset = when (mode) {
            WebContentTopInsetMode.NativeSafeArea ->
                layout.margins.top.coerceAtLeast(layout.scrollableTopInsetPx)
            WebContentTopInsetMode.EdgeToEdge,
            WebContentTopInsetMode.ScrollableDocument,
            -> layout.margins.top
        }
        (layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            if (
                params.leftMargin != layout.margins.left ||
                params.topMargin != nativeTopInset ||
                params.rightMargin != layout.margins.right ||
                params.bottomMargin != layout.margins.bottom
            ) {
                params.setMargins(
                    layout.margins.left,
                    nativeTopInset,
                    layout.margins.right,
                    layout.margins.bottom,
                )
                layoutParams = params
            }
        }
        topInsetPx = if (mode == WebContentTopInsetMode.ScrollableDocument) layoutTopInsetPx else 0
        val bottomPadding = layout.rendererSafeAreaOverride?.bottom?.coerceAtLeast(0) ?: 0
        setPadding(0, 0, 0, bottomPadding)
    }
}
