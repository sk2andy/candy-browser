package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
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
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GeckoViewRuntimeHandle private constructor(
    private val runtime: GeckoRuntime,
    override val extensions: GeckoExtensionRuntime,
) : GeckoRuntimeHandle {
    override fun createSession(
        profileId: String,
        isPrivate: Boolean,
    ): GeckoBrowserSession {
        require(GeckoProfileRules.isValidProfileId(profileId)) { "Invalid Gecko profile ID" }
        return GeckoViewBrowserSession(
            runtime = runtime,
            profileId = profileId,
            isPrivate = isPrivate,
        )
    }

    companion object {
        @UiThread
        fun create(context: Context): GeckoViewRuntimeHandle {
            val appContext = context.applicationContext
            val runtime = GeckoRuntime.create(appContext)
            return GeckoViewRuntimeHandle(
                runtime = runtime,
                extensions = GeckoViewExtensionRuntime(runtime.webExtensionController),
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
        controller.list().await().map(WebExtension::toCandyExtension)

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
    override val profileId: String,
    override val isPrivate: Boolean,
) : GeckoBrowserSession {
    private val session = GeckoSession(
        GeckoSessionSettings.Builder()
            .contextId(profileId)
            .usePrivateMode(isPrivate)
            .build(),
    )

    @Volatile
    private var state = GeckoBrowserSessionState()

    @Volatile
    private var listener: GeckoBrowserSessionStateListener? = null

    private var boundView: GeckoView? = null
    private var closed = false

    init {
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
                current.copy(url = url, isLoading = true, progress = 0)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) =
                updateState { current -> current.copy(progress = progress.coerceIn(0, 100)) }

            override fun onPageStop(session: GeckoSession, success: Boolean) =
                updateState { current -> current.copy(isLoading = false, progress = 100) }
        }
        session.open(runtime)
    }

    override fun setStateListener(listener: GeckoBrowserSessionStateListener?) {
        this.listener = listener
        listener?.onStateChanged(state)
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

    override fun loadUrl(url: String): Boolean {
        if (closed) return false
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        session.loadUri(safeUrl)
        return true
    }

    override fun goBack() {
        if (!closed && state.canGoBack) session.goBack()
    }

    override fun goForward() {
        if (!closed && state.canGoForward) session.goForward()
    }

    override fun reload() {
        if (!closed) session.reload()
    }

    override fun stop() {
        if (!closed) session.stop()
    }

    @UiThread
    override fun close() {
        if (closed) return
        boundView?.releaseSession()
        boundView = null
        listener = null
        session.close()
        closed = true
    }

    private inline fun updateState(
        transform: (GeckoBrowserSessionState) -> GeckoBrowserSessionState,
    ) {
        state = transform(state)
        listener?.onStateChanged(state)
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
