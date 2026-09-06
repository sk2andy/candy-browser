package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GeckoViewRuntimeHandle private constructor(
    private val runtime: GeckoRuntime,
    override val extensions: GeckoExtensionRuntime,
    override val toppings: GeckoToppingHostRuntime,
    private val privacyHost: GeckoViewPrivacyHostRuntime,
) : GeckoRuntimeHandle {
    override fun createSession(
        profileId: String,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy,
        privacyEventSink: GeckoPrivacyEventSink,
    ): GeckoBrowserSession {
        require(GeckoProfileRules.isValidProfileId(profileId)) { "Invalid Gecko profile ID" }
        return GeckoViewBrowserSession(
            runtime = runtime,
            extensionController = runtime.webExtensionController,
            profileId = profileId,
            isPrivate = isPrivate,
            toppingHost = toppings,
            privacyHost = privacyHost,
            initialPrivacyPolicy = privacyPolicy,
            privacyEventSink = privacyEventSink,
        )
    }

    override fun clearAllData(onComplete: (Boolean) -> Unit) {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept(
            { onComplete(true) },
            { onComplete(false) },
        )
    }

    companion object {
        @UiThread
        fun create(context: Context): GeckoViewRuntimeHandle {
            val appContext = context.applicationContext
            val contentBlocking = ContentBlocking.Settings.Builder()
                .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
                .antiTracking(ContentBlocking.AntiTracking.NONE)
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .cookieBehavior(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS,
                )
                .cookieBehaviorPrivateMode(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS,
                )
                .build()
            val runtimeSettings = GeckoRuntimeSettings.Builder()
                .contentBlocking(contentBlocking)
                .build()
            val runtime = GeckoRuntime.create(appContext, runtimeSettings)
            val extensionController = runtime.webExtensionController
            val toppingHost = GeckoViewToppingHostRuntime(extensionController)
            return GeckoViewRuntimeHandle(
                runtime = runtime,
                extensions = GeckoViewExtensionRuntime(extensionController),
                toppings = toppingHost,
                privacyHost = GeckoViewPrivacyHostRuntime(
                    controller = extensionController,
                    initializationBarrier = toppingHost,
                ),
            )
        }
    }
}

internal class GeckoViewExtensionRuntime(
    private val controller: WebExtensionController,
    private val callbackScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    ),
) : GeckoExtensionRuntime {
    @Volatile
    private var permissionPrompt = GeckoExtensionPermissionPrompt {
        GeckoExtensionPermissionDecision.Denied
    }

    @Volatile
    private var changeListener = GeckoExtensionChangeListener { }

    init {
        controller.setPromptDelegate(PromptDelegate())
        controller.setAddonManagerDelegate(AddonManagerDelegate())
    }

    override fun setPermissionPrompt(prompt: GeckoExtensionPermissionPrompt) {
        permissionPrompt = prompt
    }

    override fun setChangeListener(listener: GeckoExtensionChangeListener) {
        changeListener = listener
    }

    override suspend fun listInstalled(): List<GeckoExtension> =
        controller.list().await()
            .filterNot { extension ->
                !GeckoExtensionRules.isVisibleToUserManager(extension.id)
            }
            .map(WebExtension::toCandyExtension)

    override suspend fun installSignedXpi(
        uri: String,
        installationMethod: String,
    ): GeckoExtension {
        require(installationMethod == WebExtensionController.INSTALLATION_METHOD_MANAGER) {
            "Signed user extensions must use the add-on manager installation method"
        }
        return controller.install(
            uri,
            WebExtensionController.INSTALLATION_METHOD_MANAGER,
        ).await().toCandyExtension()
    }

    override suspend fun enable(extensionId: String): GeckoExtension {
        val extension = requireInstalled(extensionId)
        return controller.enable(
            extension,
            WebExtensionController.EnableSource.USER,
        ).await().toCandyExtension()
    }

    override suspend fun disable(extensionId: String): GeckoExtension {
        val extension = requireInstalled(extensionId)
        return controller.disable(
            extension,
            WebExtensionController.EnableSource.USER,
        ).await().toCandyExtension()
    }

    override suspend fun update(extensionId: String): GeckoExtension {
        val installed = requireInstalled(extensionId)
        return (controller.update(installed).awaitNullable() ?: installed).toCandyExtension()
    }

    override suspend fun setAllowedInPrivateBrowsing(
        extensionId: String,
        allowed: Boolean,
    ): GeckoExtension = controller.setAllowedInPrivateBrowsing(
        requireInstalled(extensionId),
        allowed,
    ).await().toCandyExtension()

    override suspend fun uninstall(extensionId: String) {
        controller.uninstall(requireInstalled(extensionId)).awaitCompletion()
    }

    private suspend fun requireInstalled(extensionId: String): WebExtension =
        requireNotNull(controller.list().await().firstOrNull { extension ->
            GeckoExtensionRules.isVisibleToUserManager(extensionId) &&
            extension.id == extensionId
        }) { "Gecko extension is not installed: $extensionId" }

    private fun <T> promptResult(
        extension: WebExtension,
        kind: GeckoExtensionPermissionRequestKind,
        permissions: Array<String>,
        origins: Array<String>,
        mapper: (GeckoExtensionPermissionDecision) -> T,
    ): GeckoResult<T> {
        val result = GeckoResult<T>()
        callbackScope.launch {
            val decision = try {
                permissionPrompt.decide(
                    GeckoExtensionPermissionRequest(
                        kind = kind,
                        extensionId = extension.id,
                        extensionName = extension.metaData.name,
                        permissions = permissions.toList(),
                        origins = origins.toList(),
                    ),
                )
            } catch (error: CancellationException) {
                GeckoExtensionPermissionDecision.Denied
            } catch (_: Throwable) {
                GeckoExtensionPermissionDecision.Denied
            }
            result.complete(mapper(decision))
        }
        return result
    }

    private fun notifyChanged() {
        callbackScope.launch { changeListener.onChanged() }
    }

    private inner class PromptDelegate : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> = promptResult(
            extension = extension,
            kind = GeckoExtensionPermissionRequestKind.Install,
            permissions = permissions,
            origins = origins,
        ) { decision ->
            WebExtension.PermissionPromptResponse(
                decision.grantPermissions,
                decision.allowInPrivateBrowsing,
            )
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            updatedExtension: WebExtension,
            newPermissions: Array<String>,
            newOrigins: Array<String>,
        ): GeckoResult<AllowOrDeny> = promptResult(
            extension = updatedExtension,
            kind = GeckoExtensionPermissionRequestKind.Update,
            permissions = newPermissions,
            origins = newOrigins,
            mapper = GeckoExtensionPermissionDecision::toAllowOrDeny,
        )

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
        ): GeckoResult<AllowOrDeny> = promptResult(
            extension = extension,
            kind = GeckoExtensionPermissionRequestKind.Optional,
            permissions = permissions,
            origins = origins,
            mapper = GeckoExtensionPermissionDecision::toAllowOrDeny,
        )
    }

    private inner class AddonManagerDelegate : WebExtensionController.AddonManagerDelegate {
        override fun onInstalled(extension: WebExtension) = notifyChanged()

        override fun onUninstalled(extension: WebExtension) = notifyChanged()

        override fun onEnabled(extension: WebExtension) = notifyChanged()

        override fun onDisabled(extension: WebExtension) = notifyChanged()

        override fun onOptionalPermissionsChanged(extension: WebExtension) = notifyChanged()
    }
}

private class GeckoViewBrowserSession(
    runtime: GeckoRuntime,
    private val extensionController: WebExtensionController,
    override val profileId: String,
    override val isPrivate: Boolean,
    private val toppingHost: GeckoToppingHostRuntime,
    private val privacyHost: GeckoViewPrivacyHostRuntime,
    initialPrivacyPolicy: GeckoPrivacyPolicy,
    privacyEventSink: GeckoPrivacyEventSink,
) : GeckoBrowserSession {
    private val session = GeckoSession(
        GeckoSessionSettings.Builder()
            .contextId(profileId)
            .usePrivateMode(isPrivate)
            .useTrackingProtection(false)
            .build(),
    )

    @Volatile
    private var state = GeckoBrowserSessionState()

    @Volatile
    private var listener: GeckoBrowserSessionStateListener? = null

    private var boundView: GeckoView? = null
    private var closed = false
    private var active = false
    // Private sessions wait as well: the bundled host must first confirm that Gecko has revoked
    // private-browsing access, including recovery from an older installation that allowed it.
    private var toppingHostWaitRegistered = false
    private var pendingInitialUrl: String? = null
    private var privacyPolicy = initialPrivacyPolicy
    private var privacyBound = false
    private var privacyFailureDescription: String? = null
    private val privacyBinding: GeckoPrivacyBinding

    init {
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) =
                updateState { current -> current.copy(title = title) }

            override fun onCrash(session: GeckoSession) = updateState { current ->
                current.copy(
                    isLoading = false,
                    lastNavigationSucceeded = false,
                    crashed = true,
                )
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) = updateState { current -> current.copy(url = url) }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) =
                updateState { current -> current.copy(canGoBack = canGoBack) }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) =
                updateState { current -> current.copy(canGoForward = canGoForward) }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) = updateState { current ->
                current.copy(
                    url = url,
                    title = null,
                    isLoading = true,
                    progress = 0,
                    lastNavigationSucceeded = null,
                    failureDescription = null,
                )
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) =
                updateState { current -> current.copy(progress = progress.coerceIn(0, 100)) }

            override fun onPageStop(session: GeckoSession, success: Boolean) =
                updateState { current ->
                    current.copy(
                        isLoading = false,
                        progress = 100,
                        lastNavigationSucceeded = success,
                    )
                }
        }
        session.open(runtime)
        privacyBinding = privacyHost.bind(
            session = session,
            policy = initialPrivacyPolicy,
            sink = privacyEventSink,
            onBound = {
                privacyBound = true
                loadPendingUrlIfReady()
            },
            onFailure = ::failPrivacyGate,
        )
    }

    override fun setStateListener(listener: GeckoBrowserSessionStateListener?) {
        this.listener = listener
        listener?.onStateChanged(state)
    }

    @UiThread
    override fun setActive(active: Boolean) {
        if (closed || this.active == active) return
        extensionController.setTabActive(session, active)
        this.active = active
    }

    @UiThread
    override fun createView(context: Context): View {
        check(!closed) { "Cannot bind a closed Gecko session" }
        check(boundView == null) { "Gecko session already has a bound View" }
        return GeckoView(context).also { view ->
            view.setSession(session)
            boundView = view
        }
    }

    @UiThread
    override fun releaseView(view: View) {
        val geckoView = view as? GeckoView ?: return
        if (geckoView !== boundView) return
        geckoView.releaseSession()
        boundView = null
    }

    @UiThread
    override fun capturePreview(
        targetWidthPx: Int,
        visibleViewHeightPx: Int,
        maximumTargetHeightPx: Int,
        onComplete: (Bitmap?) -> Unit,
    ): BrowserEnginePreviewCapture? {
        val view = boundView?.takeIf { bound ->
            !closed &&
                bound.isAttachedToWindow &&
                bound.isShown &&
                bound.width > 0 &&
                bound.height > 0
        } ?: return null
        val viewHeightPx = view.height
        val result = try {
            view.capturePixels()
        } catch (_: IllegalStateException) {
            return null
        }
        var cancelled = false
        result.withHandler(Handler(Looper.getMainLooper())).accept(
            { captured ->
                if (cancelled) {
                    captured?.takeUnless(Bitmap::isRecycled)?.recycle()
                    return@accept
                }
                onComplete(
                    captured?.let { bitmap ->
                        preparePreviewBitmap(
                            captured = bitmap,
                            viewHeightPx = viewHeightPx,
                            visibleViewHeightPx = visibleViewHeightPx,
                            targetWidthPx = targetWidthPx,
                            maximumTargetHeightPx = maximumTargetHeightPx,
                        )
                    },
                )
            },
            {
                if (!cancelled) onComplete(null)
            },
        )
        return BrowserEnginePreviewCapture {
            cancelled = true
            result.cancel()
        }
    }

    override fun findInPage(
        query: String,
        forward: Boolean,
        onComplete: (GeckoFindResult?) -> Unit,
    ) {
        if (closed || query.isEmpty()) {
            if (query.isEmpty()) session.finder.clear()
            onComplete(null)
            return
        }
        val finder = session.finder
        finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
        val flags = if (forward) {
            GeckoSession.FINDER_FIND_FORWARD
        } else {
            GeckoSession.FINDER_FIND_BACKWARDS
        }
        finder.find(query, flags)
            .withHandler(Handler(Looper.getMainLooper()))
            .accept(
                { result ->
                    onComplete(
                        result?.let { value ->
                            val matchCount = value.total.coerceAtLeast(0)
                            GeckoFindResult(
                                activeMatchOrdinal = (value.current - 1)
                                    .coerceIn(0, (matchCount - 1).coerceAtLeast(0)),
                                matchCount = matchCount,
                                isDoneCounting = value.total >= 0,
                            )
                        },
                    )
                },
                { onComplete(null) },
            )
    }

    override fun clearFindInPage() {
        if (!closed) session.finder.clear()
    }

    override fun printPage(): Boolean {
        if (closed || boundView == null) return false
        session.printPageContent()
        return true
    }

    override fun setDesktopMode(enabled: Boolean) {
        if (closed) return
        session.settings.setUserAgentMode(
            if (enabled) {
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            },
        )
        session.settings.setViewportMode(
            if (enabled) {
                GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            },
        )
    }

    override fun loadUrl(url: String): Boolean {
        if (closed) return false
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        privacyFailureDescription?.let { description ->
            failPrivacyGate(description)
            return true
        }
        if (toppingHost.state != GeckoToppingHostState.Initializing && privacyBound) {
            session.loadUri(safeUrl)
            return true
        }
        pendingInitialUrl = safeUrl
        if (!toppingHostWaitRegistered) {
            toppingHostWaitRegistered = true
            toppingHost.runAfterInitialization {
                toppingHostWaitRegistered = false
                loadPendingUrlIfReady()
            }
        }
        return true
    }

    override fun updatePrivacyPolicy(policy: GeckoPrivacyPolicy, onReady: () -> Unit) {
        privacyPolicy = policy
        privacyBinding.update(policy, onReady)
    }

    override fun goBack() {
        if (!closed && privacyBound && state.canGoBack) session.goBack()
    }

    override fun goForward() {
        if (!closed && privacyBound && state.canGoForward) session.goForward()
    }

    override fun reload() {
        if (!closed && privacyBound) session.reload()
    }

    override fun stop() {
        if (!closed) session.stop()
    }

    @UiThread
    override fun close() {
        if (closed) return
        if (active) extensionController.setTabActive(session, false)
        active = false
        boundView?.releaseSession()
        boundView = null
        listener = null
        pendingInitialUrl = null
        privacyBinding.close()
        session.close()
        closed = true
    }

    private fun loadPendingUrlIfReady() {
        if (closed || toppingHost.state == GeckoToppingHostState.Initializing || !privacyBound) return
        val pendingUrl = pendingInitialUrl ?: return
        pendingInitialUrl = null
        session.loadUri(pendingUrl)
    }

    private fun failPrivacyGate(description: String) {
        if (closed) return
        privacyBound = false
        privacyFailureDescription = description
        pendingInitialUrl = null
        session.stop()
        updateState { current -> current.copy(isLoading = true, lastNavigationSucceeded = null) }
        updateState { current ->
            current.copy(
                isLoading = false,
                progress = 100,
                lastNavigationSucceeded = false,
                failureDescription = description,
            )
        }
    }

    private inline fun updateState(
        transform: (GeckoBrowserSessionState) -> GeckoBrowserSessionState,
    ) {
        state = transform(state)
        listener?.onStateChanged(state)
    }

    private fun preparePreviewBitmap(
        captured: Bitmap,
        viewHeightPx: Int,
        visibleViewHeightPx: Int,
        targetWidthPx: Int,
        maximumTargetHeightPx: Int,
    ): Bitmap? {
        val layout = GeckoPreviewCaptureRules.resolveBitmapLayout(
            viewHeightPx = viewHeightPx,
            visibleViewHeightPx = visibleViewHeightPx,
            capturedWidthPx = captured.width,
            capturedHeightPx = captured.height,
            targetWidthPx = targetWidthPx,
            maximumTargetHeightPx = maximumTargetHeightPx,
        ) ?: run {
            captured.recycle()
            return null
        }
        val cropped = if (layout.sourceHeightPx < captured.height) {
            Bitmap.createBitmap(captured, 0, 0, captured.width, layout.sourceHeightPx)
        } else {
            captured
        }
        val scaled = if (
            cropped.width == layout.targetWidthPx &&
            cropped.height == layout.targetHeightPx
        ) {
            cropped
        } else {
            Bitmap.createScaledBitmap(
                cropped,
                layout.targetWidthPx,
                layout.targetHeightPx,
                true,
            )
        }
        if (cropped !== captured && !cropped.isRecycled && cropped !== scaled) cropped.recycle()
        if (captured !== scaled && !captured.isRecycled) captured.recycle()
        return scaled
    }
}

private object GeckoProfileRules {
    fun isValidProfileId(profileId: String): Boolean =
        profileId.isNotBlank() &&
            profileId.length <= MAX_PROFILE_ID_LENGTH &&
            profileId.none(Char::isISOControl)

    private const val MAX_PROFILE_ID_LENGTH = 256
}

private fun WebExtension.toCandyExtension() = GeckoExtension(
    id = id,
    name = metaData.name,
    version = metaData.version,
    enabled = metaData.enabled,
    allowedInPrivateBrowsing = metaData.allowedInPrivateBrowsing,
    isBuiltIn = isBuiltIn,
)

private fun GeckoExtensionPermissionDecision.toAllowOrDeny(): AllowOrDeny =
    if (grantPermissions) AllowOrDeny.ALLOW else AllowOrDeny.DENY

private suspend fun <T> GeckoResult<T>.await(): T = suspendCancellableCoroutine { continuation ->
    val delivered = withHandler(Handler(Looper.getMainLooper()))
    delivered.accept(
        { value ->
            if (!continuation.isActive) return@accept
            if (value == null) {
                continuation.resumeWithException(
                    IllegalStateException("GeckoResult completed without a value"),
                )
            } else {
                continuation.resume(value)
            }
        },
        { error ->
            if (!continuation.isActive) return@accept
            continuation.resumeWithException(
                error ?: IllegalStateException("GeckoResult failed without an exception"),
            )
        },
    )
    continuation.invokeOnCancellation { cancel() }
}

private suspend fun <T> GeckoResult<T>.awaitNullable(): T? =
    suspendCancellableCoroutine { continuation ->
        val delivered = withHandler(Handler(Looper.getMainLooper()))
        delivered.accept(
            { value -> if (continuation.isActive) continuation.resume(value) },
            { error ->
                if (!continuation.isActive) return@accept
                continuation.resumeWithException(
                    error ?: IllegalStateException("GeckoResult failed without an exception"),
                )
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }

private suspend fun GeckoResult<*>.awaitCompletion(): Unit =
    suspendCancellableCoroutine { continuation ->
        val delivered = withHandler(Handler(Looper.getMainLooper()))
        delivered.accept(
            { _ -> if (continuation.isActive) continuation.resume(Unit) },
            { error ->
                if (!continuation.isActive) return@accept
                continuation.resumeWithException(
                    error ?: IllegalStateException("GeckoResult failed without an exception"),
                )
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }
