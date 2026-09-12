package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.sk2andy.materialbrowser.browser.BrowserEngineAndroidPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineAuthPromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineAuthPromptResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineBooleanResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineContentPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineDownloadResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineFileCapture
import dev.sk2andy.materialbrowser.browser.BrowserEngineFilePromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineFilePromptResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineMediaPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineNavigationTarget
import dev.sk2andy.materialbrowser.browser.BrowserEnginePermissionRules
import dev.sk2andy.materialbrowser.browser.BrowserEnginePermissionSetResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineWebPromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineWebPromptResponse
import dev.sk2andy.materialbrowser.browser.BrowserWebPromptKind
import dev.sk2andy.materialbrowser.browser.BrowserWebPromptChoice
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollEvent
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollEventSource
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetKind
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetRules
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.credentials.AndroidCredentialPromptHost
import dev.sk2andy.materialbrowser.browser.credentials.CredentialPromptHost
import dev.sk2andy.materialbrowser.browser.credentials.CredentialPromptIdentity
import dev.sk2andy.materialbrowser.browser.credentials.CredentialPromptRules
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.data.UserScriptValueStore
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.CandyGeckoViewSafeAreaBridge
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GeckoViewRuntimeHandle private constructor(
    private val runtime: GeckoRuntime,
    override val extensions: GeckoExtensionRuntime,
    override val toppings: GeckoToppingHostRuntime,
    private val privacyHost: GeckoViewPrivacyHostRuntime,
    private val downloadTransfers: GeckoDownloadTransferManager,
) : GeckoRuntimeHandle {
    private val cookieBehavior = GeckoCookieBehaviorCoordinator(
        runtime.settings.contentBlocking,
    )
    private val trackingPermissions = GeckoTrackingPermissionCoordinator(
        runtime.storageController,
    )

    override fun createSession(
        profileId: String,
        isolationEnabled: Boolean,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy,
        privacyEventSink: GeckoPrivacyEventSink,
    ): GeckoBrowserSession {
        require(GeckoProfileRules.isValidProfileId(profileId)) { "Invalid Gecko profile ID" }
        return GeckoViewBrowserSession(
            runtime = runtime,
            extensionController = runtime.webExtensionController,
            extensionRuntime = extensions,
            profileId = profileId,
            isolationEnabled = isolationEnabled,
            isPrivate = isPrivate,
            toppingHost = toppings,
            privacyHost = privacyHost,
            downloadTransfers = downloadTransfers,
            cookieBehavior = cookieBehavior,
            trackingPermissions = trackingPermissions,
            initialPrivacyPolicy = privacyPolicy,
            privacyEventSink = privacyEventSink,
        )
    }

    override fun adoptExtensionSession(
        session: GeckoSession,
        profileId: String,
        isolationEnabled: Boolean,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy,
        privacyEventSink: GeckoPrivacyEventSink,
    ): GeckoBrowserSession? {
        if (session.isOpen) return null
        if (session.settings.contextId !=
            GeckoProfileStorageRules.contextId(profileId, isolationEnabled, isPrivate)
        ) return null
        if (session.settings.usePrivateMode != isPrivate) return null
        return GeckoViewBrowserSession(
            runtime = runtime,
            extensionController = runtime.webExtensionController,
            extensionRuntime = extensions,
            profileId = profileId,
            isolationEnabled = isolationEnabled,
            isPrivate = isPrivate,
            toppingHost = toppings,
            privacyHost = privacyHost,
            downloadTransfers = downloadTransfers,
            cookieBehavior = cookieBehavior,
            trackingPermissions = trackingPermissions,
            initialPrivacyPolicy = privacyPolicy,
            privacyEventSink = privacyEventSink,
            session = session,
            openSession = false,
        )
    }

    override fun clearBrowsingData(
        data: GeckoBrowsingData,
        onComplete: (Boolean) -> Unit,
    ) {
        val flags = when (data) {
            GeckoBrowsingData.AllCaches -> StorageController.ClearFlags.ALL_CACHES
            GeckoBrowsingData.Cookies -> StorageController.ClearFlags.COOKIES
            GeckoBrowsingData.All -> StorageController.ClearFlags.ALL
        }
        runtime.storageController.clearData(flags)
            .withHandler(Handler(Looper.getMainLooper()))
            .accept(
                { onComplete(true) },
                { onComplete(false) },
            )
    }

    override fun requestProfileDataDeletion(profileId: String): Boolean {
        if (profileId.isBlank()) return false
        return runCatching {
            runtime.storageController.clearDataForSessionContext(profileId)
        }.isSuccess
    }

    @UiThread
    override fun setBlockThirdPartyCookies(blocked: Boolean) {
        cookieBehavior.setGloballyBlocked(blocked)
    }

    @UiThread
    override fun setWebRtcProtectionMode(mode: WebRtcProtectionMode, onReady: () -> Unit) {
        privacyHost.setWebRtcProtectionMode(mode, onReady)
    }

    @UiThread
    override fun setWebContentFontSizeFactor(factor: Float) {
        runtime.settings.automaticFontSizeAdjustment = false
        runtime.settings.fontSizeFactor = factor
    }

    @UiThread
    override fun setWebContentColorScheme(colorScheme: BrowserWebContentColorScheme) {
        runtime.settings.preferredColorScheme = when (colorScheme) {
            BrowserWebContentColorScheme.System -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
            BrowserWebContentColorScheme.Light -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            BrowserWebContentColorScheme.Dark -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        }
    }

    @UiThread
    override fun onConfigurationChanged(configuration: Configuration) {
        runtime.configurationChanged(configuration)
    }

    @UiThread
    override fun bindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate) {
        runtime.activityDelegate = delegate
    }

    @UiThread
    override fun unbindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate) {
        if (runtime.activityDelegate === delegate) runtime.activityDelegate = null
    }

    @VisibleForTesting
    fun webAuthnActivityDelegateForTesting(): GeckoRuntime.ActivityDelegate? =
        runtime.activityDelegate

    @VisibleForTesting
    fun ensureBuiltInExtensionFixture(): GeckoResult<WebExtension> =
        runtime.webExtensionController.ensureBuiltIn(
            TEST_EXTENSION_FIXTURE_LOCATION,
            TEST_EXTENSION_FIXTURE_ID,
        )

    companion object {
        private const val TEST_EXTENSION_FIXTURE_ID = "candy-firefox-fixture@sk2andy.dev"
        private const val TEST_EXTENSION_FIXTURE_LOCATION =
            "resource://android/assets/candy_extension_fixture/"

        @UiThread
        fun create(context: Context): GeckoViewRuntimeHandle {
            val appContext = context.applicationContext
            val contentBlocking = ContentBlocking.Settings.Builder()
                .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
                .antiTracking(ContentBlocking.AntiTracking.NONE)
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .cookieBehavior(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
                )
                .cookieBehaviorPrivateMode(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
                )
                .build()
            val runtimeSettings = GeckoRuntimeSettingsFactory.create(contentBlocking)
            val runtime = GeckoRuntime.create(appContext, runtimeSettings)
            val extensionController = runtime.webExtensionController
            val toppingHost = GeckoViewToppingHostRuntime(
                controller = extensionController,
                valueStore = UserScriptValueStore(appContext),
            )
            val privacyHost = GeckoViewPrivacyHostRuntime(
                controller = extensionController,
                initializationBarrier = toppingHost,
            )
            val downloadTransfers = GeckoDownloadTransferManager(
                context = appContext,
                executor = org.mozilla.geckoview.GeckoWebExecutor(runtime),
            )
            val defaultExtensionInstaller = GeckoViewDefaultExtensionInstaller(
                context = appContext,
                controller = extensionController,
            )
            return GeckoViewRuntimeHandle(
                runtime = runtime,
                extensions = GeckoViewExtensionRuntime(
                    runtime = runtime,
                    controller = extensionController,
                    downloadTransfers = downloadTransfers,
                    runAfterInternalHostInitialization = privacyHost::runAfterInitialization,
                    defaultExtensionInstaller = defaultExtensionInstaller,
                    defaultExtensionProvisionerFactory = {
                        GeckoDefaultExtensionProvisioner(
                            extensions = GeckoDefaultExtensionCatalog.load(appContext),
                            stateStore = GeckoDefaultExtensionPreferences(appContext),
                            assetVerifier = GeckoDefaultExtensionAssets(appContext),
                            installer = defaultExtensionInstaller,
                            retiredExtensionIds = GeckoDefaultExtensionCatalog.retiredExtensionIds,
                        )
                    },
                ),
                toppings = toppingHost,
                privacyHost = privacyHost,
                downloadTransfers = downloadTransfers,
            )
        }
    }
}

/**
 * GeckoView 155 exposes cookie behavior only on the shared runtime. Keep that runtime strict unless
 * the selected session has a confirmed, host-matched compatibility exception.
 */
private class GeckoCookieBehaviorCoordinator(
    private val settings: ContentBlocking.Settings,
) {
    private data class Claim(
        val active: Boolean,
        val allow: Boolean,
        val privateMode: Boolean,
    )

    private val claims = mutableMapOf<Any, Claim>()
    private var globallyBlocked = true
    private var appliedNormalBehavior = ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
    private var appliedPrivateBehavior = ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY

    fun setGloballyBlocked(blocked: Boolean): Boolean {
        globallyBlocked = blocked
        return reconcile()
    }

    fun update(owner: Any, active: Boolean, allow: Boolean, privateMode: Boolean): Boolean {
        claims[owner] = Claim(active = active, allow = allow, privateMode = privateMode)
        return reconcile()
    }

    fun remove(owner: Any): Boolean {
        claims.remove(owner)
        return reconcile()
    }

    private fun reconcile(): Boolean {
        val desiredNormalBehavior = desiredBehavior(privateMode = false)
        val desiredPrivateBehavior = desiredBehavior(privateMode = true)
        var changed = false
        if (desiredNormalBehavior != appliedNormalBehavior) {
            settings.setCookieBehavior(desiredNormalBehavior)
            appliedNormalBehavior = desiredNormalBehavior
            changed = true
        }
        if (desiredPrivateBehavior != appliedPrivateBehavior) {
            settings.setCookieBehaviorPrivateMode(desiredPrivateBehavior)
            appliedPrivateBehavior = desiredPrivateBehavior
            changed = true
        }
        return changed
    }

    private fun desiredBehavior(privateMode: Boolean): Int =
        if (
            !globallyBlocked || claims.values.any { claim ->
                claim.active && claim.allow && claim.privateMode == privateMode
            }
        ) {
            ContentBlocking.CookieBehavior.ACCEPT_ALL
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        }
}

/**
 * Gecko's tracking permission is scoped to site, session context and private mode, not to a tab.
 * Merge all tab claims so sibling tabs cannot alternately grant and revoke the same permission.
 */
private class GeckoTrackingPermissionCoordinator(
    private val storageController: StorageController,
) {
    private data class Key(
        val uri: String,
        val contextId: String?,
        val privateMode: Boolean,
    )

    private data class Claim(
        val allow: Boolean,
        val reload: () -> Unit,
    )

    private data class Entry(
        var permission: GeckoSession.PermissionDelegate.ContentPermission,
        val claims: MutableMap<Any, Claim> = mutableMapOf(),
        var appliedValue: Int? = null,
    )

    private val ownerKeys = mutableMapOf<Any, Key>()
    private val entries = mutableMapOf<Key, Entry>()
    private val initializationCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initializationTimeout = Runnable {
        finishInitialization(succeeded = false)
    }
    private var initializationSucceeded: Boolean? = null

    val isReady: Boolean
        get() = initializationSucceeded == true

    init {
        mainHandler.postDelayed(initializationTimeout, INITIALIZATION_TIMEOUT_MILLIS)
        clearPersistedTrackingPermissions(attempt = 0)
    }

    private fun clearPersistedTrackingPermissions(attempt: Int) {
        storageController.getAllPermissions()
            .withHandler(mainHandler)
            .accept(
                { permissions ->
                    val stalePermissions = permissions.orEmpty().filter { permission ->
                        permission.permission ==
                            GeckoSession.PermissionDelegate.PERMISSION_TRACKING &&
                            permission.value ==
                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    }
                    if (stalePermissions.isEmpty()) {
                        finishInitialization(succeeded = true)
                    } else if (attempt >= MAX_PERMISSION_CLEANUP_ATTEMPTS) {
                        finishInitialization(succeeded = false)
                    } else {
                        stalePermissions.forEach { permission ->
                            storageController.setPermission(
                                permission,
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
                            )
                        }
                        mainHandler.postDelayed(
                            { clearPersistedTrackingPermissions(attempt + 1) },
                            PERMISSION_CLEANUP_RETRY_MILLIS,
                        )
                    }
                },
                { finishInitialization(succeeded = false) },
            )
    }

    fun runAfterInitialization(action: (Boolean) -> Unit) {
        val result = initializationSucceeded
        if (result == null) initializationCallbacks += action else action(result)
    }

    fun update(
        owner: Any,
        permission: GeckoSession.PermissionDelegate.ContentPermission,
        allow: Boolean,
        reloadOwnerOnChange: Boolean,
        reload: () -> Unit,
    ) {
        if (permission.permission != GeckoSession.PermissionDelegate.PERMISSION_TRACKING) return
        val key = Key(
            uri = permissionScopeUri(permission.uri),
            contextId = permission.contextId,
            privateMode = permission.privateMode,
        )
        if (ownerKeys[owner] != key) remove(owner)
        ownerKeys[owner] = key
        val entry = entries.getOrPut(key) { Entry(permission = permission) }
        entry.permission = permission
        entry.claims[owner] = Claim(allow = allow, reload = reload)
        reconcile(key, owner.takeUnless { reloadOwnerOnChange })
    }

    fun updateClaim(
        owner: Any,
        allow: Boolean,
        reloadOwnerOnChange: Boolean,
    ) {
        val key = ownerKeys[owner] ?: return
        val entry = entries[key] ?: return
        val claim = entry.claims[owner] ?: return
        entry.claims[owner] = claim.copy(allow = allow)
        reconcile(key, owner.takeUnless { reloadOwnerOnChange })
    }

    fun remove(owner: Any) {
        val key = ownerKeys.remove(owner) ?: return
        val entry = entries[key] ?: return
        entry.claims.remove(owner)
        reconcile(key, skippedReloadOwner = null)
        if (entry.claims.isEmpty()) entries.remove(key)
    }

    private fun reconcile(key: Key, skippedReloadOwner: Any?) {
        val entry = entries[key] ?: return
        val desiredValue = when {
            entry.claims.any { (_, claim) -> claim.allow } ->
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            entry.appliedValue == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ||
                entry.permission.value ==
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ->
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            else -> return
        }
        val currentValue = entry.appliedValue ?: entry.permission.value
        if (currentValue == desiredValue) return
        storageController.setPermission(entry.permission, desiredValue)
        entry.appliedValue = desiredValue
        entry.claims.forEach { (owner, claim) ->
            if (owner !== skippedReloadOwner) claim.reload()
        }
    }

    private fun finishInitialization(succeeded: Boolean) {
        if (initializationSucceeded != null) return
        mainHandler.removeCallbacks(initializationTimeout)
        initializationSucceeded = succeeded
        val callbacks = initializationCallbacks.toList()
        initializationCallbacks.clear()
        callbacks.forEach { action -> action(succeeded) }
    }

    private fun permissionScopeUri(rawUri: String): String {
        val parsed = runCatching { URI(rawUri) }.getOrNull() ?: return rawUri
        val scheme = parsed.scheme?.lowercase() ?: return rawUri
        val host = parsed.host?.lowercase() ?: return rawUri
        return runCatching {
            URI(scheme, null, host, parsed.port, null, null, null).toASCIIString()
        }.getOrDefault(rawUri)
    }

    private companion object {
        const val INITIALIZATION_TIMEOUT_MILLIS = 15_000L
        const val MAX_PERMISSION_CLEANUP_ATTEMPTS = 4
        const val PERMISSION_CLEANUP_RETRY_MILLIS = 50L
    }
}

internal class GeckoViewExtensionRuntime(
    private val runtime: GeckoRuntime,
    private val controller: WebExtensionController,
    private val downloadTransfers: GeckoDownloadTransferManager,
    private val runAfterInternalHostInitialization: ((Boolean) -> Unit) -> Unit = { action ->
        action(true)
    },
    private val defaultExtensionInstaller: GeckoViewDefaultExtensionInstaller,
    defaultExtensionProvisionerFactory: suspend () -> GeckoDefaultExtensionProvisioner,
    private val callbackScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    ),
) : GeckoExtensionRuntime {
    private var chrome: GeckoViewExtensionChrome? = null
    private val defaultExtensionStartup: GeckoDefaultExtensionStartup

    @Volatile
    private var permissionPrompt = GeckoExtensionPermissionPrompt {
        GeckoExtensionPermissionDecision.Denied
    }

    @Volatile
    private var changeListener = GeckoExtensionChangeListener { }

    init {
        controller.setPromptDelegate(PromptDelegate())
        controller.setAddonManagerDelegate(AddonManagerDelegate())
        defaultExtensionStartup = GeckoDefaultExtensionStartup(
            scope = callbackScope,
            runAfterInternalHostInitialization = runAfterInternalHostInitialization,
            provisionerFactory = defaultExtensionProvisionerFactory,
        )
    }

    override fun setPermissionPrompt(prompt: GeckoExtensionPermissionPrompt) {
        permissionPrompt = prompt
    }

    override fun setChangeListener(listener: GeckoExtensionChangeListener) {
        changeListener = listener
    }

    @UiThread
    @SuppressLint("WrongThread") // GeckoResult is explicitly rebound to the main Handler below.
    override fun setChromeHost(host: GeckoExtensionChromeHost?) {
        chrome?.close()
        chrome = host?.let {
            GeckoViewExtensionChrome(runtime, controller, it, downloadTransfers)
        }
        if (host == null) return
        controller.list().withHandler(Handler(Looper.getMainLooper())).accept(
            { installed -> chrome?.reconcile(installed.orEmpty()) },
            { chrome?.reconcile(emptyList()) },
        )
    }

    @UiThread
    override fun attachChromeSession(
        session: GeckoSession,
        identity: GeckoExtensionSessionIdentity,
    ) {
        chrome?.attachSession(session, identity)
    }

    @UiThread
    override fun detachChromeSession(session: GeckoSession) {
        chrome?.detachSession(session)
    }

    @UiThread
    override fun onSelectedChromeSessionChanged() {
        chrome?.onSelectedSessionChanged()
    }

    @UiThread
    override fun clickChromeAction(key: GeckoExtensionActionKey): Boolean =
        chrome?.click(key) == true

    @UiThread
    override fun dismissChromePopup() {
        chrome?.dismissPopup()
    }

    override suspend fun listInstalled(): List<GeckoExtension> {
        defaultExtensionStartup.await()
        defaultExtensionStartup.retryFailedIfDue()
        val installed = controller.list().await()
        chrome?.reconcile(installed)
        return installed
            .filterNot { extension ->
                !GeckoExtensionRules.isVisibleToUserManager(extension.id)
            }
            .map(WebExtension::toCandyExtension)
    }

    override suspend fun installSignedXpi(
        uri: String,
        installationMethod: String,
    ): GeckoExtension {
        defaultExtensionStartup.await()
        require(installationMethod == WebExtensionController.INSTALLATION_METHOD_MANAGER) {
            "Signed user extensions must use the add-on manager installation method"
        }
        val installed = controller.install(
            uri,
            WebExtensionController.INSTALLATION_METHOD_MANAGER,
        ).await()
        if (!defaultExtensionStartup.markUserInstallSucceeded(installed.id)) {
            controller.uninstall(installed).awaitCompletion()
            error("Default extension installation state could not be persisted")
        }
        return installed.toCandyExtension()
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
        val installed = requireInstalled(extensionId)
        check(defaultExtensionStartup.markRemovalRequested(extensionId)) {
            "Default extension removal state could not be persisted"
        }
        controller.uninstall(installed).awaitCompletion()
    }

    private suspend fun requireInstalled(extensionId: String): WebExtension {
        defaultExtensionStartup.await()
        return requireNotNull(controller.list().await().firstOrNull { extension ->
            GeckoExtensionRules.isVisibleToUserManager(extensionId) &&
            extension.id == extensionId
        }) { "Gecko extension is not installed: $extensionId" }
    }

    private fun <T> promptResult(
        extension: WebExtension,
        kind: GeckoExtensionPermissionRequestKind,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>,
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
                        dataCollectionPermissions = dataCollectionPermissions.toList(),
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

    @SuppressLint("WrongThread") // GeckoResult is explicitly rebound to the main Handler below.
    private fun notifyChanged() {
        controller.list().withHandler(Handler(Looper.getMainLooper())).accept(
            { installed ->
                chrome?.reconcile(installed.orEmpty())
                callbackScope.launch { changeListener.onChanged() }
            },
            { callbackScope.launch { changeListener.onChanged() } },
        )
    }

    private inner class PromptDelegate : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val automatic = defaultExtensionInstaller.automaticPermissionDecision(extension)
            if (automatic != null) {
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(
                        automatic.grantPermissions,
                        automatic.allowInPrivateBrowsing,
                        // Bundled defaults never silently opt into technical/interaction telemetry.
                        false,
                    ),
                )
            }
            return promptResult(
                extension = extension,
                kind = GeckoExtensionPermissionRequestKind.Install,
                permissions = permissions,
                origins = origins,
                dataCollectionPermissions = dataCollectionPermissions,
            ) { decision ->
                WebExtension.PermissionPromptResponse(
                    decision.grantPermissions,
                    decision.allowInPrivateBrowsing,
                    GeckoExtensionRules.grantsTechnicalAndInteractionData(
                        dataCollectionPermissions.toList(),
                        decision,
                    ),
                )
            }
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<String>,
            newOrigins: Array<String>,
            newDataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny> = promptResult(
            extension = extension,
            kind = GeckoExtensionPermissionRequestKind.Update,
            permissions = newPermissions,
            origins = newOrigins,
            dataCollectionPermissions = newDataCollectionPermissions,
            mapper = GeckoExtensionPermissionDecision::toAllowOrDeny,
        )

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny> = promptResult(
            extension = extension,
            kind = GeckoExtensionPermissionRequestKind.Optional,
            permissions = permissions,
            origins = origins,
            dataCollectionPermissions = dataCollectionPermissions,
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
    private val extensionRuntime: GeckoExtensionRuntime,
    override val profileId: String,
    private val isolationEnabled: Boolean,
    override val isPrivate: Boolean,
    private val toppingHost: GeckoToppingHostRuntime,
    private val privacyHost: GeckoViewPrivacyHostRuntime,
    private val downloadTransfers: GeckoDownloadTransferManager,
    private val cookieBehavior: GeckoCookieBehaviorCoordinator,
    private val trackingPermissions: GeckoTrackingPermissionCoordinator,
    initialPrivacyPolicy: GeckoPrivacyPolicy,
    privacyEventSink: GeckoPrivacyEventSink,
    private val session: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .contextId(GeckoProfileStorageRules.contextId(profileId, isolationEnabled, isPrivate))
            .usePrivateMode(isPrivate)
            .build(),
    ),
    openSession: Boolean = true,
) : GeckoBrowserSession {
    private data class AutoplayPermissionKey(
        val uri: String,
        val contextId: String?,
        val privateMode: Boolean,
        val permission: Int,
    )

    private val storageController = runtime.storageController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cookieBehaviorOwner = Any()
    private val trackingPermissionOwner = Any()

    @Volatile
    private var state = GeckoBrowserSessionState()

    @Volatile
    private var listener: GeckoBrowserSessionStateListener? = null

    @Volatile
    private var historyState: GeckoBrowserHistoryState? = null

    @Volatile
    private var historyStateListener: GeckoBrowserHistoryStateListener? = null

    @Volatile
    private var contentTargetListener: BrowserContentTargetListener? = null

    @Volatile
    private var navigationRequestListener: GeckoNavigationRequestListener? = null

    @Volatile
    private var newSessionListener: GeckoNewSessionListener? = null

    @Volatile
    private var downloadResponseListener: GeckoDownloadResponseListener? = null

    @Volatile
    private var filePromptListener: GeckoFilePromptListener? = null

    @Volatile
    private var androidPermissionRequestListener: GeckoAndroidPermissionRequestListener? = null

    @Volatile
    private var contentPermissionRequestListener: GeckoContentPermissionRequestListener? = null

    @Volatile
    private var mediaPermissionRequestListener: GeckoMediaPermissionRequestListener? = null

    @Volatile
    private var authPromptListener: GeckoAuthPromptListener? = null

    @Volatile
    private var webPromptListener: GeckoWebPromptListener? = null

    @Volatile
    private var mediaState = GeckoMediaSessionState()

    @Volatile
    private var mediaStateListener: GeckoMediaSessionStateListener? = null

    @Volatile
    private var fullscreenStateListener: GeckoFullscreenStateListener? = null

    @Volatile
    private var scrollListener: BrowserEngineScrollListener? = null

    private var boundView: CandyGeckoView? = null
    private var backdropCaptureEnabled = false
    private val contentPresentationGate = GeckoContentPresentationGate()
    private var activeMediaSession: MediaSession? = null
    private var videoAutoplayBlocked = false
    private var audioMuted = false
    private var httpPasswordManagerSelectionEnabled = false
    private var autoplayPolicyRevision = 0
    private var autoplayLocationUrl: String? = null
    private val autoplayPermissions =
        mutableMapOf<Int, GeckoSession.PermissionDelegate.ContentPermission>()
    private val appliedAutoplayValues = mutableMapOf<AutoplayPermissionKey, Int>()
    private var closed = false
    private var active = false
    private var inPictureInPicture = false
    private var pictureInPicturePlaybackExpected = false
    private var extensionIdentity: GeckoExtensionSessionIdentity? = null
    private var credentialPromptHost: CredentialPromptHost? = null
    private var credentialNavigationGeneration = 0L
    private val credentialPromptBridge = GeckoCredentialPromptBridge(
        currentLoginSelectionIdentity = ::currentCredentialLoginSelectionIdentity,
        currentSecureIdentity = ::currentCredentialPromptIdentity,
        currentHost = { credentialPromptHost },
    )
    // Private sessions wait as well: the bundled host must first confirm that Gecko has revoked
    // private-browsing access, including recovery from an older installation that allowed it.
    private var toppingHostWaitRegistered = false
    private var trackingPermissionWaitRegistered = false
    private var pendingInitialUrl: String? = null
    private var latestSessionState: GeckoSession.SessionState? = null
    private var pendingRestoredSessionState: GeckoSession.SessionState? = null
    private var privacyPolicy = initialPrivacyPolicy
    private var currentPageUrl: String? = null
    private var trackingPermission: GeckoSession.PermissionDelegate.ContentPermission? = null
    private var privacyBound = false
    private var privacyFailureDescription: String? = null
    private val privacyBinding: GeckoPrivacyBinding
    init {
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFirstComposite(session: GeckoSession) {
                contentPresentationGate.onFirstComposite()
            }

            override fun onFirstContentfulPaint(session: GeckoSession) {
                contentPresentationGate.onFirstContentfulPaint()
            }

            override fun onPaintStatusReset(session: GeckoSession) {
                contentPresentationGate.onPaintStatusReset()
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                fullscreenStateListener?.onStateChanged(fullScreen)
            }

            override fun onTitleChange(session: GeckoSession, title: String?) =
                updateState { current -> current.copy(title = title) }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                val target = BrowserContentTargetRules.resolve(
                    kind = element.type.toBrowserContentTargetKind(),
                    linkUrl = element.linkUri,
                    sourceUrl = element.srcUri,
                ) ?: return
                val view = boundView ?: return
                if (
                    !active ||
                    !view.hasWindowFocus() ||
                    inPictureInPicture ||
                    pictureInPicturePlaybackExpected
                ) return
                contentTargetListener?.onLongPress(target)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val contentDisposition = response.headers.entries.firstOrNull { (name, _) ->
                    name.equals("content-disposition", ignoreCase = true)
                }?.value
                val contentType = response.headers.entries.firstOrNull { (name, _) ->
                    name.equals("content-type", ignoreCase = true)
                }?.value
                val listener = downloadResponseListener
                if (listener == null) {
                    runCatching { response.body?.close() }
                    return
                }
                listener.onDownloadResponse(
                    GeckoExternalDownloadResponse(
                        metadata = BrowserEngineDownloadResponse(
                            url = response.uri,
                            contentDisposition = contentDisposition,
                            mimeType = contentType,
                        ),
                        startTransfer = { transferListener ->
                            downloadTransfers.startResponse(
                                owner = GeckoDownloadOwner(profileId, isPrivate, session),
                                response = response,
                                referrer = currentPageUrl,
                                listener = transferListener,
                            )
                        },
                        discard = { runCatching { response.body?.close() } },
                    ),
                )
            }

            override fun onCrash(session: GeckoSession) = onContentProcessTerminated()

            override fun onKill(session: GeckoSession) = onContentProcessTerminated()
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                updateState { current ->
                    current.copy(
                        failureDescription = GECKO_NAVIGATION_FAILURE,
                        failureKind = GeckoNavigationFailureRules.kindForErrorCode(error.code),
                    )
                }
                return null
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                if (
                    privacyHost.isTrustedBootstrapNavigation(
                        session = session,
                        url = request.uri,
                        isDirectNavigation = request.isDirectNavigation,
                        hasUserGesture = request.hasUserGesture,
                        isRedirect = request.isRedirect,
                    )
                ) return GeckoResult.allow()
                val decision = navigationRequestListener?.onNavigationRequest(
                    GeckoMainFrameNavigationRequest(
                        url = request.uri,
                        isRedirect = request.isRedirect,
                        hasUserGesture = request.hasUserGesture,
                        isDirectNavigation = request.isDirectNavigation,
                        target = when (request.target) {
                            GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW ->
                                BrowserEngineNavigationTarget.New
                            GeckoSession.NavigationDelegate.TARGET_WINDOW_NONE ->
                                BrowserEngineNavigationTarget.None
                            else -> BrowserEngineNavigationTarget.Current
                        },
                    ),
                ) ?: GeckoNavigationRequestDecision.Allow
                return when (decision) {
                    GeckoNavigationRequestDecision.Allow -> {
                        cookieBehavior.update(
                            owner = cookieBehaviorOwner,
                            active = active,
                            allow = privacyPolicy.allowsThirdPartyCookiesForPage(request.uri),
                            privateMode = isPrivate,
                        )
                        GeckoResult.allow()
                    }
                    GeckoNavigationRequestDecision.Deny -> GeckoResult.deny()
                }
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String,
            ): GeckoResult<GeckoSession> {
                val child = GeckoSession(GeckoSessionSettings.Builder(session.settings).build())
                val accepted = newSessionListener?.onNewSession(
                    GeckoNewSessionRequest(uri, child),
                ) == true
                return GeckoResult.fromValue(child.takeIf { accepted })
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (privacyHost.isBootstrapNavigation(session, url)) return
                currentPageUrl = url
                val cookieBehaviorChanged = cookieBehavior.update(
                    owner = cookieBehaviorOwner,
                    active = active,
                    allow = privacyPolicy.allowsThirdPartyCookiesForPage(url),
                    privateMode = isPrivate,
                )
                if (autoplayLocationUrl != url) {
                    autoplayLocationUrl = url
                    appliedAutoplayValues.clear()
                }
                autoplayPermissions.clear()
                var autoplayPermissionChanged = false
                perms.filter { permission -> isAutoplayPermission(permission) }
                    .forEach { permission ->
                        autoplayPermissions[permission.permission] = permission
                        autoplayPermissionChanged =
                            reconcileAutoplayPermission(permission) || autoplayPermissionChanged
                    }
                perms.firstOrNull { permission ->
                    permission.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING
                }?.let { permission ->
                    trackingPermission = permission
                    trackingPermissions.update(
                        owner = trackingPermissionOwner,
                        permission = permission,
                        allow = privacyPolicy.allowsThirdPartyCookiesForSite(permission),
                        reloadOwnerOnChange = !cookieBehaviorChanged,
                        reload = session::reload,
                    )
                }
                updateState { current -> current.copy(url = url) }
                if (autoplayPermissionChanged) {
                    beginAutoplayPermissionSync(url)
                } else if (cookieBehaviorChanged) {
                    session.reload()
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) =
                updateState { current -> current.copy(canGoBack = canGoBack) }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) =
                updateState { current -> current.copy(canGoForward = canGoForward) }
        }
        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(
                session: GeckoSession,
                historyList: GeckoSession.HistoryDelegate.HistoryList,
            ) {
                (historyList as? GeckoSession.SessionState)?.let { nativeState ->
                    latestSessionState = GeckoSession.SessionState(nativeState)
                }
                val currentIndex = historyList.currentIndex
                val updated = GeckoBrowserHistoryState(
                    urls = historyList.map { item -> item.uri.orEmpty() },
                    currentIndex = currentIndex,
                    currentTitle = historyList.getOrNull(currentIndex)?.title,
                )
                historyState = updated
                historyStateListener?.onHistoryStateChanged(updated)
            }
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                scrollListener?.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = scrollY))
            }
        }
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                val requested = BrowserEnginePermissionRules.runtimePermissions(permissions)
                val requestListener = androidPermissionRequestListener
                if (requested == null || requestListener == null) {
                    callback.reject()
                    return
                }
                requestListener.onAndroidPermissionRequest(
                    BrowserEngineAndroidPermissionRequest(
                        permissions = requested,
                        response = BrowserEngineBooleanResponse { allowed ->
                            if (closed) return@BrowserEngineBooleanResponse
                            if (allowed) callback.grant() else callback.reject()
                        },
                    ),
                )
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? {
                val permission = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ->
                        GeckoAutoplayPermission.Audible
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ->
                        GeckoAutoplayPermission.Inaudible
                    else -> GeckoAutoplayPermission.Other
                }
                val value = when (GeckoAutoplayRules.resolve(permission, videoAutoplayBlocked)) {
                    GeckoAutoplayDecision.Allow ->
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    GeckoAutoplayDecision.Deny ->
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    GeckoAutoplayDecision.Defer -> {
                        val sitePermission = when (perm.permission) {
                            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ->
                                SitePermission.Location
                            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS ->
                                SitePermission.ProtectedMedia
                            else -> return null
                        }
                        val requestListener = contentPermissionRequestListener
                            ?: return GeckoResult.fromValue(
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
                            )
                        val result = GeckoResult<Int>()
                        requestListener.onContentPermissionRequest(
                            BrowserEngineContentPermissionRequest(
                                origin = perm.uri,
                                permission = sitePermission,
                                response = BrowserEngineBooleanResponse { allowed ->
                                    result.complete(
                                        if (!closed && allowed) {
                                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                        } else {
                                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                                        },
                                    )
                                },
                            ),
                        )
                        return result
                    }
                }
                autoplayPermissions[perm.permission] = perm
                appliedAutoplayValues[perm.autoplayKey()] = value
                return GeckoResult.fromValue(value)
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                val videoSources = video.orEmpty()
                val audioSources = audio.orEmpty()
                val requested = BrowserEnginePermissionRules.requestedMediaPermissions(
                    hasCamera = videoSources.any { source ->
                        source.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                    },
                    hasMicrophone = audioSources.any { source ->
                        source.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE ||
                            source.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_AUDIOCAPTURE
                    },
                    hasUnsupportedVideoSource = videoSources.any { source ->
                        source.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                    },
                    hasUnsupportedAudioSource = audioSources.any { source ->
                        source.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE &&
                            source.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_AUDIOCAPTURE
                    },
                )
                val requestListener = mediaPermissionRequestListener
                if (requested == null || requestListener == null) {
                    callback.reject()
                    return
                }
                requestListener.onMediaPermissionRequest(
                    BrowserEngineMediaPermissionRequest(
                        origin = uri,
                        permissions = requested,
                        response = BrowserEnginePermissionSetResponse { allowed ->
                            if (closed) return@BrowserEnginePermissionSetResponse
                            val selectedVideo = videoSources.firstOrNull { source ->
                                SitePermission.Camera in allowed &&
                                    source.source ==
                                    GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                            }
                            val selectedAudio = audioSources.firstOrNull { source ->
                                SitePermission.Microphone in allowed &&
                                    (source.source ==
                                        GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE ||
                                        source.source ==
                                        GeckoSession.PermissionDelegate.MediaSource.SOURCE_AUDIOCAPTURE)
                            }
                            if (
                                (SitePermission.Camera !in requested || selectedVideo != null) &&
                                (SitePermission.Microphone !in requested || selectedAudio != null) &&
                                allowed.isNotEmpty()
                            ) {
                                callback.grant(selectedVideo, selectedAudio)
                            } else {
                                callback.reject()
                            }
                        },
                    ),
                )
            }
        }
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt,
            ) = dispatchWebPrompt(
                request = BrowserEngineWebPromptRequest(
                    kind = BrowserWebPromptKind.Alert,
                    title = prompt.title,
                    message = prompt.message,
                    defaultValue = null,
                    response = webActionPromptResponse(
                        confirm = prompt::dismiss,
                        dismiss = prompt::dismiss,
                    ),
                ),
                fallback = prompt::dismiss,
            )

            override fun onButtonPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt,
            ) = dispatchWebPrompt(
                request = BrowserEngineWebPromptRequest(
                    kind = BrowserWebPromptKind.Confirm,
                    title = prompt.title,
                    message = prompt.message,
                    defaultValue = null,
                    response = webActionPromptResponse(
                        confirm = {
                            prompt.confirm(
                                GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE,
                            )
                        },
                        dismiss = {
                            prompt.confirm(
                                GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE,
                            )
                        },
                    ),
                ),
                fallback = prompt::dismiss,
            )

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt,
            ) = dispatchWebPrompt(
                request = BrowserEngineWebPromptRequest(
                    kind = BrowserWebPromptKind.Text,
                    title = prompt.title,
                    message = prompt.message,
                    defaultValue = prompt.defaultValue,
                    response = webPromptResponse(
                        confirm = { value -> prompt.confirm(value.orEmpty()) },
                        dismiss = prompt::dismiss,
                    ),
                ),
                fallback = prompt::dismiss,
            )

            override fun onBeforeUnloadPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt,
            ) = dispatchWebPrompt(
                request = BrowserEngineWebPromptRequest(
                    kind = BrowserWebPromptKind.BeforeUnload,
                    title = null,
                    message = null,
                    defaultValue = null,
                    response = webActionPromptResponse(
                        confirm = { prompt.confirm(AllowOrDeny.ALLOW) },
                        dismiss = { prompt.confirm(AllowOrDeny.DENY) },
                    ),
                ),
                fallback = prompt::dismiss,
            )

            override fun onRepostConfirmPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt,
            ) = dispatchWebPrompt(
                request = BrowserEngineWebPromptRequest(
                    kind = BrowserWebPromptKind.Repost,
                    title = null,
                    message = null,
                    defaultValue = null,
                    response = webActionPromptResponse(
                        confirm = { prompt.confirm(AllowOrDeny.ALLOW) },
                        dismiss = { prompt.confirm(AllowOrDeny.DENY) },
                    ),
                ),
                fallback = prompt::dismiss,
            )

            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val requestListener = filePromptListener
                    ?: return GeckoResult.fromValue(prompt.dismiss())
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                requestListener.onFilePrompt(
                    BrowserEngineFilePromptRequest(
                        mimeTypes = prompt.mimeTypes.orEmpty().toList(),
                        allowMultiple = prompt.type ==
                            GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE,
                        capture = when (prompt.capture) {
                            GeckoSession.PromptDelegate.FilePrompt.Capture.ANY ->
                                BrowserEngineFileCapture.Any
                            GeckoSession.PromptDelegate.FilePrompt.Capture.USER ->
                                BrowserEngineFileCapture.User
                            GeckoSession.PromptDelegate.FilePrompt.Capture.ENVIRONMENT ->
                                BrowserEngineFileCapture.Environment
                            else -> BrowserEngineFileCapture.None
                        },
                        response = BrowserEngineFilePromptResponse { values ->
                            val uris = values.orEmpty().map(android.net.Uri::parse).toTypedArray()
                            val context = boundView?.context?.applicationContext
                            result.complete(
                                if (!closed && context != null && uris.isNotEmpty()) {
                                    prompt.confirm(context, uris)
                                } else {
                                    prompt.dismiss()
                                },
                            )
                        },
                    ),
                )
                return result
            }

            override fun onFolderUploadPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FolderUploadPrompt,
            ) = dispatchWebPrompt(
                BrowserEngineWebPromptRequest(
                    kind = BrowserWebPromptKind.FolderUpload,
                    title = prompt.title,
                    message = prompt.directoryName,
                    defaultValue = null,
                    response = webActionPromptResponse(
                        confirm = { prompt.confirm(AllowOrDeny.ALLOW) },
                        dismiss = { prompt.confirm(AllowOrDeny.DENY) },
                    ),
                ),
                fallback = { prompt.confirm(AllowOrDeny.DENY) },
            )

            override fun onChoicePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ChoicePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val choices = prompt.choices.orEmpty()
                return dispatchWebPrompt(
                    BrowserEngineWebPromptRequest(
                        kind = BrowserWebPromptKind.Choice,
                        title = prompt.title,
                        message = prompt.message,
                        defaultValue = null,
                        choices = choices.map { choice ->
                            BrowserWebPromptChoice(
                                id = choice.id,
                                label = choice.label,
                                selected = choice.selected,
                                disabled = choice.disabled,
                                separator = choice.separator,
                            )
                        },
                        allowMultiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE,
                        response = webPromptResponse(
                            confirm = { value ->
                                val selected = value.orEmpty().split(CHOICE_VALUE_SEPARATOR)
                                    .filter(String::isNotEmpty)
                                    .mapNotNull { id -> choices.firstOrNull { it.id == id } }
                                    .toTypedArray()
                                if (prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
                                    prompt.confirm(selected)
                                } else {
                                    selected.firstOrNull()?.let(prompt::confirm) ?: prompt.dismiss()
                                }
                            },
                            dismiss = prompt::dismiss,
                        ),
                    ),
                    fallback = prompt::dismiss,
                )
            }

            override fun onColorPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ColorPrompt,
            ) = dispatchWebPrompt(BrowserEngineWebPromptRequest(
                kind = BrowserWebPromptKind.Color, title = prompt.title, message = null,
                defaultValue = prompt.defaultValue, response = webPromptResponse(
                    confirm = { value -> prompt.confirm(value ?: prompt.defaultValue ?: "") }, dismiss = prompt::dismiss,
                ),
            ), prompt::dismiss)

            override fun onDateTimePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.DateTimePrompt,
            ) = dispatchWebPrompt(BrowserEngineWebPromptRequest(
                kind = BrowserWebPromptKind.DateTime, title = prompt.title, message = null,
                defaultValue = prompt.defaultValue, response = webPromptResponse(
                    confirm = { value -> prompt.confirm(value ?: prompt.defaultValue ?: "") }, dismiss = prompt::dismiss,
                ),
            ), prompt::dismiss)

            override fun onPopupPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.PopupPrompt,
            ) = GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

            override fun onSharePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.SharePrompt,
            ) = dispatchWebPrompt(BrowserEngineWebPromptRequest(
                kind = BrowserWebPromptKind.Share, title = prompt.title, message = prompt.text,
                defaultValue = null, shareUri = prompt.uri,
                response = webActionPromptResponse(
                    confirm = { prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.SUCCESS) },
                    dismiss = { prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.ABORT) },
                ),
            ), { prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.ABORT) })

            override fun onRequestCertificate(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.CertificateRequest,
            ) = GeckoResult.fromValue(prompt.dismiss())

            override fun onLoginSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>,
            ) = credentialPromptBridge.onLoginSave(request)

            override fun onAddressSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.AddressSaveOption>,
            ) = GeckoResult.fromValue(request.dismiss())

            override fun onCreditCardSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<
                    Autocomplete.CreditCardSaveOption,
                    >,
            ) = GeckoResult.fromValue(request.dismiss())

            override fun onLoginSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<
                    Autocomplete.LoginSelectOption,
                    >,
            ) = credentialPromptBridge.onLoginSelect(request)

            override fun onCreditCardSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<
                    Autocomplete.CreditCardSelectOption,
                    >,
            ) = GeckoResult.fromValue(request.dismiss())

            override fun onAddressSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<
                    Autocomplete.AddressSelectOption,
                    >,
            ) = GeckoResult.fromValue(request.dismiss())

            override fun onSelectIdentityCredentialProvider(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.ProviderSelectorPrompt,
            ) = credentialPromptBridge.onIdentityProviderSelect(prompt)

            override fun onSelectIdentityCredentialAccount(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.AccountSelectorPrompt,
            ) = credentialPromptBridge.onIdentityAccountSelect(prompt)

            override fun onShowPrivacyPolicyIdentityCredential(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.PrivacyPolicyPrompt,
            ) = credentialPromptBridge.onIdentityPrivacyPolicy(prompt)

            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val requestListener = authPromptListener
                    ?: return GeckoResult.fromValue(prompt.dismiss())
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val flags = prompt.authOptions.flags
                requestListener.onAuthPrompt(
                    BrowserEngineAuthPromptRequest(
                        uri = prompt.authOptions.uri,
                        realm = prompt.message ?: prompt.title,
                        onlyPassword = flags and
                            GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0,
                        isProxy = flags and
                            GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.PROXY != 0,
                        isCrossOriginSubresource = flags and
                            GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags
                                .CROSS_ORIGIN_SUB_RESOURCE != 0,
                        response = object : BrowserEngineAuthPromptResponse {
                            override fun confirm(username: String, password: String) {
                                result.complete(
                                    if (closed) {
                                        prompt.dismiss()
                                    } else if (flags and
                                        GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags
                                            .ONLY_PASSWORD != 0
                                    ) {
                                        prompt.confirm(password)
                                    } else {
                                        prompt.confirm(username, password)
                                    },
                                )
                            }

                            override fun dismiss() {
                                result.complete(prompt.dismiss())
                            }
                        },
                    ),
                )
                return result
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                activeMediaSession = mediaSession
                mediaSession.muteAudio(audioMuted)
                updateMediaState { GeckoMediaSessionRules.activatedState() }
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (activeMediaSession !== mediaSession) return
                activeMediaSession = null
                updateMediaState { GeckoMediaSessionState() }
            }

            override fun onMetadata(
                session: GeckoSession,
                mediaSession: MediaSession,
                meta: MediaSession.Metadata,
            ) {
                if (activeMediaSession !== mediaSession) return
                updateMediaState { current ->
                    current.copy(title = meta.title, artist = meta.artist)
                }
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                if (activeMediaSession !== mediaSession) return
                updateMediaState { current -> current.copy(isActive = true, isPlaying = true) }
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                if (activeMediaSession !== mediaSession) return
                updateMediaState { current -> current.copy(isPlaying = false) }
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                if (activeMediaSession !== mediaSession) return
                updateMediaState { GeckoMediaSessionRules.stoppedState() }
            }

            override fun onPositionState(
                session: GeckoSession,
                mediaSession: MediaSession,
                positionState: MediaSession.PositionState,
            ) {
                if (activeMediaSession !== mediaSession) return
                updateMediaState { current ->
                    current.copy(
                        currentPositionMillis = positionState.position.toBoundedMediaMillis() ?: 0,
                        durationMillis = positionState.duration.toBoundedMediaMillis(),
                        playbackRate = positionState.playbackRate
                            .takeIf(Double::isFinite)
                            ?.coerceIn(0.1, 16.0)
                            ?.toFloat()
                            ?: 1f,
                    )
                }
            }

            override fun onFullscreen(
                session: GeckoSession,
                mediaSession: MediaSession,
                enabled: Boolean,
                meta: MediaSession.ElementMetadata?,
            ) {
                if (activeMediaSession !== mediaSession) return
                updateMediaState { current ->
                    current.copy(
                        isFullscreen = enabled,
                        sourceUrl = meta?.source.takeIf { enabled },
                        durationMillis = meta?.duration
                            ?.toBoundedMediaMillis()
                            ?.takeIf { enabled }
                            ?: current.durationMillis,
                        videoWidth = meta?.width
                            ?.coerceIn(0, Int.MAX_VALUE.toLong())
                            ?.toInt()
                            ?.takeIf { enabled }
                            ?: 0,
                        videoHeight = meta?.height
                            ?.coerceIn(0, Int.MAX_VALUE.toLong())
                            ?.toInt()
                            ?.takeIf { enabled }
                            ?: 0,
                        audioTrackCount = meta?.audioTrackCount?.coerceAtLeast(0)
                            ?.takeIf { enabled }
                            ?: 0,
                        videoTrackCount = meta?.videoTrackCount?.coerceAtLeast(0)
                            ?.takeIf { enabled }
                            ?: 0,
                    )
                }
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (privacyHost.isBootstrapNavigation(session, url)) return
                currentPageUrl = url
                invalidateCredentialPrompts(recreateHost = true)
                activeMediaSession = null
                updateMediaState { GeckoMediaSessionState() }
                updateState { current ->
                    current.copy(
                        url = url,
                        title = null,
                        isLoading = true,
                        progress = 0,
                        lastNavigationSucceeded = null,
                        failureDescription = null,
                        failureKind = null,
                        httpStatusCode = null,
                    )
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) =
                updateState { current -> current.copy(progress = progress.coerceIn(0, 100)) }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                updateState { current ->
                    current.copy(
                        isLoading = false,
                        progress = 100,
                        lastNavigationSucceeded = success,
                    )
                }
                ensureCredentialPromptHost()
            }
        }
        if (openSession) session.open(runtime)
        session.setActive(false)
        privacyBinding = privacyHost.bind(
            session = session,
            policy = initialPrivacyPolicy,
            sink = privacyEventSink,
            onScrollMetrics = { metrics ->
                scrollListener?.onScrollChanged(
                    BrowserEngineScrollEvent(
                        scrollYPx = metrics.offsetPx,
                        source = BrowserEngineScrollEventSource.DocumentMetrics,
                    ),
                )
            },
            onMainFrameResponse = { response ->
                val responseUrl = BrowserUriPolicy.normalizeHttpUrl(response.url)
                val pageUrl = currentPageUrl?.let(BrowserUriPolicy::normalizeHttpUrl)
                if (responseUrl != null && responseUrl == pageUrl) {
                    updateState { current -> current.copy(httpStatusCode = response.statusCode) }
                }
            },
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

    override fun setHistoryStateListener(listener: GeckoBrowserHistoryStateListener?) {
        historyStateListener = listener
        historyState?.let { state -> listener?.onHistoryStateChanged(state) }
    }

    override fun setContentTargetListener(listener: BrowserContentTargetListener?) {
        contentTargetListener = listener
    }

    @VisibleForTesting
    override fun dispatchContentTargetForTesting(target: WebContentTarget) {
        contentTargetListener?.onLongPress(target)
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
    ): GeckoDownloadCancellation? {
        if (closed) return null
        val webRequest = runCatching {
            WebRequest.Builder(request.url)
                .method("GET")
                .apply { request.referrer?.let(::referrer) }
                .build()
        }.getOrElse {
            listener.onFailed(GeckoDownloadFailure.InvalidRequest)
            return null
        }
        return downloadTransfers.start(
            transfer = GeckoDownloadTransferRequest(
                owner = GeckoDownloadOwner(profileId, isPrivate, session),
                request = webRequest,
                fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                suggestedFileName = request.suggestedFileName,
            ),
            listener = listener,
        )
    }

    override fun setFilePromptListener(listener: GeckoFilePromptListener?) {
        filePromptListener = listener
    }

    override fun setAndroidPermissionRequestListener(
        listener: GeckoAndroidPermissionRequestListener?,
    ) {
        androidPermissionRequestListener = listener
    }

    override fun setContentPermissionRequestListener(
        listener: GeckoContentPermissionRequestListener?,
    ) {
        contentPermissionRequestListener = listener
    }

    override fun setMediaPermissionRequestListener(listener: GeckoMediaPermissionRequestListener?) {
        mediaPermissionRequestListener = listener
    }

    override fun setAuthPromptListener(listener: GeckoAuthPromptListener?) {
        authPromptListener = listener
    }

    override fun setWebPromptListener(listener: GeckoWebPromptListener?) {
        webPromptListener = listener
    }

    private fun dispatchWebPrompt(
        request: BrowserEngineWebPromptRequest,
        fallback: () -> GeckoSession.PromptDelegate.PromptResponse,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val response = request.response as GeckoWebPromptResponse
        val requestListener = webPromptListener
            ?: return GeckoResult.fromValue(fallback())
        requestListener.onWebPrompt(request)
        return response.result
    }

    private fun currentCredentialPromptIdentity(): CredentialPromptIdentity? {
        val extension = extensionIdentity ?: return null
        return CredentialPromptRules.identity(
            tabId = extension.tabId,
            profileId = profileId,
            isPrivate = isPrivate,
            isActive = active,
            pageUrl = currentPageUrl,
            sessionGeneration = extension.generation,
            navigationGeneration = credentialNavigationGeneration,
        )
    }

    private fun currentCredentialLoginSelectionIdentity(): CredentialPromptIdentity? {
        val extension = extensionIdentity ?: return null
        return CredentialPromptRules.loginSelectionIdentity(
            tabId = extension.tabId,
            profileId = profileId,
            isPrivate = isPrivate,
            isActive = active,
            pageUrl = currentPageUrl,
            sessionGeneration = extension.generation,
            navigationGeneration = credentialNavigationGeneration,
            allowHttp = httpPasswordManagerSelectionEnabled,
        )
    }

    private fun invalidateCredentialPrompts(recreateHost: Boolean) {
        credentialNavigationGeneration++
        credentialPromptHost?.close()
        credentialPromptHost = if (recreateHost && active && !isPrivate) {
            boundView?.context?.let(AndroidCredentialPromptHost::create)
        } else {
            null
        }
    }

    private fun ensureCredentialPromptHost() {
        if (credentialPromptHost == null && active && !closed && !isPrivate) {
            credentialPromptHost = boundView?.context?.let(AndroidCredentialPromptHost::create)
        }
    }

    private fun webActionPromptResponse(
        confirm: () -> GeckoSession.PromptDelegate.PromptResponse,
        dismiss: () -> GeckoSession.PromptDelegate.PromptResponse,
    ): BrowserEngineWebPromptResponse = GeckoWebPromptResponse(
        isCurrent = { !closed },
        confirmAction = { confirm() },
        dismissAction = dismiss,
    )

    private fun webPromptResponse(
        confirm: (String?) -> GeckoSession.PromptDelegate.PromptResponse,
        dismiss: () -> GeckoSession.PromptDelegate.PromptResponse,
    ): BrowserEngineWebPromptResponse = GeckoWebPromptResponse(
        isCurrent = { !closed },
        confirmAction = confirm,
        dismissAction = dismiss,
    )

    override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) {
        mediaStateListener = listener
        listener?.onStateChanged(mediaState)
    }

    override fun setScrollListener(listener: BrowserEngineScrollListener?) {
        scrollListener = listener
    }

    override fun setVideoAutoplayBlocked(blocked: Boolean) {
        if (videoAutoplayBlocked == blocked) return
        videoAutoplayBlocked = blocked
        autoplayPermissions.values.forEach { permission ->
            reconcileAutoplayPermission(permission)
        }
        // Gecko stores site permission results and the current document caches its decision.
        // Confirm both stored values before reloading: setPermission is asynchronous and has no
        // completion callback in GeckoView 155.
        beginAutoplayPermissionSync(state.url)
    }

    override fun setHttpPasswordManagerSelectionEnabled(enabled: Boolean) {
        if (isPrivate || httpPasswordManagerSelectionEnabled == enabled) return
        httpPasswordManagerSelectionEnabled = enabled
        invalidateCredentialPrompts(recreateHost = true)
    }

    private fun beginAutoplayPermissionSync(url: String?) {
        val currentUrl = url?.let(BrowserUriPolicy::normalizeHttpUrl) ?: return
        val revision = ++autoplayPolicyRevision
        verifyAutoplayPermissions(
            revision = revision,
            blocked = videoAutoplayBlocked,
            currentUrl = currentUrl,
            attempt = 0,
        )
    }

    private fun verifyAutoplayPermissions(
        revision: Int,
        blocked: Boolean,
        currentUrl: String,
        attempt: Int,
    ) {
        val permissionScope = autoplayPermissions.values.firstOrNull()
        if (permissionScope == null) {
            scheduleMissingAutoplayPermissionRetry(
                revision = revision,
                blocked = blocked,
                currentUrl = currentUrl,
                attempt = attempt,
            )
            return
        }
        storageController.getPermissions(
            permissionScope.uri,
            permissionScope.contextId,
            permissionScope.privateMode,
        )
            .withHandler(mainHandler)
            .accept(
                { permissions ->
                    if (!isCurrentAutoplayPolicy(revision, blocked, currentUrl)) return@accept
                    val desiredValue = if (blocked) {
                        GeckoAutoplayDecision.Deny
                    } else {
                        GeckoAutoplayDecision.Allow
                    }
                    val desiredStorageValue = desiredValue.toStorageValue()
                    val autoplayValues = buildMap {
                        permissions.orEmpty()
                            .filter { permission -> isAutoplayPermission(permission) }
                            .forEach { permission ->
                                autoplayPermissions[permission.permission] = permission
                                val permissionKind = permission.toAutoplayPermission()
                                val storedValue = permission.value.toAutoplayDecision()
                                put(permissionKind, storedValue)
                                if (permission.value != desiredStorageValue) {
                                    storageController.setPermission(permission, desiredStorageValue)
                                    appliedAutoplayValues[permission.autoplayKey()] =
                                        desiredStorageValue
                                }
                            }
                    }
                    when (
                        GeckoAutoplayPermissionSyncRules.resolve(
                            storedValues = autoplayValues,
                            desiredValue = desiredValue,
                            attempt = attempt,
                        )
                    ) {
                        GeckoAutoplayPermissionSyncAction.Reload ->
                            scheduleAutoplayPermissionReload(
                                revision = revision,
                                blocked = blocked,
                                currentUrl = currentUrl,
                            )
                        GeckoAutoplayPermissionSyncAction.Retry -> scheduleAutoplayPermissionRetry(
                            revision = revision,
                            blocked = blocked,
                            currentUrl = currentUrl,
                            attempt = attempt + 1,
                        )
                        GeckoAutoplayPermissionSyncAction.KeepCurrentDocument -> Unit
                    }
                },
                {
                    if (!isCurrentAutoplayPolicy(revision, blocked, currentUrl)) return@accept
                    when (
                        GeckoAutoplayPermissionSyncRules.resolve(
                            storedValues = emptyMap(),
                            desiredValue = if (blocked) {
                                GeckoAutoplayDecision.Deny
                            } else {
                                GeckoAutoplayDecision.Allow
                            },
                            attempt = attempt,
                        )
                    ) {
                        GeckoAutoplayPermissionSyncAction.Retry -> scheduleAutoplayPermissionRetry(
                            revision = revision,
                            blocked = blocked,
                            currentUrl = currentUrl,
                            attempt = attempt + 1,
                        )
                        GeckoAutoplayPermissionSyncAction.Reload,
                        GeckoAutoplayPermissionSyncAction.KeepCurrentDocument,
                        -> Unit
                    }
                },
            )
    }

    private fun scheduleMissingAutoplayPermissionRetry(
        revision: Int,
        blocked: Boolean,
        currentUrl: String,
        attempt: Int,
    ) {
        when (
            GeckoAutoplayPermissionSyncRules.resolve(
                storedValues = emptyMap(),
                desiredValue = if (blocked) {
                    GeckoAutoplayDecision.Deny
                } else {
                    GeckoAutoplayDecision.Allow
                },
                attempt = attempt,
            )
        ) {
            GeckoAutoplayPermissionSyncAction.Retry -> scheduleAutoplayPermissionRetry(
                revision = revision,
                blocked = blocked,
                currentUrl = currentUrl,
                attempt = attempt + 1,
            )
            GeckoAutoplayPermissionSyncAction.Reload,
            GeckoAutoplayPermissionSyncAction.KeepCurrentDocument,
            -> Unit
        }
    }

    private fun scheduleAutoplayPermissionRetry(
        revision: Int,
        blocked: Boolean,
        currentUrl: String,
        attempt: Int,
    ) {
        mainHandler.postDelayed(
            {
                if (isCurrentAutoplayPolicy(revision, blocked, currentUrl)) {
                    verifyAutoplayPermissions(
                        revision = revision,
                        blocked = blocked,
                        currentUrl = currentUrl,
                        attempt = attempt,
                    )
                }
            },
            GeckoAutoplayPermissionSyncRules.RETRY_DELAY_MILLIS,
        )
    }

    private fun scheduleAutoplayPermissionReload(
        revision: Int,
        blocked: Boolean,
        currentUrl: String,
    ) {
        // The permission-store read can observe the new values before Gecko's document-side
        // permission observer. Give it one bounded propagation turn before creating a new document.
        mainHandler.postDelayed(
            {
                if (isCurrentAutoplayPolicy(revision, blocked, currentUrl)) session.reload()
            },
            GeckoAutoplayPermissionSyncRules.PROPAGATION_DELAY_MILLIS,
        )
    }

    private fun GeckoSession.PermissionDelegate.ContentPermission.toAutoplayPermission() =
        when (permission) {
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ->
                GeckoAutoplayPermission.Audible
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ->
                GeckoAutoplayPermission.Inaudible
            else -> GeckoAutoplayPermission.Other
        }

    private fun Int.toAutoplayDecision() = when (this) {
        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ->
            GeckoAutoplayDecision.Allow
        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY ->
            GeckoAutoplayDecision.Deny
        else -> GeckoAutoplayDecision.Defer
    }

    private fun GeckoAutoplayDecision.toStorageValue() = when (this) {
        GeckoAutoplayDecision.Allow ->
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        GeckoAutoplayDecision.Deny ->
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        GeckoAutoplayDecision.Defer ->
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
    }

    private fun isCurrentAutoplayPolicy(
        revision: Int,
        blocked: Boolean,
        url: String,
    ): Boolean = !closed &&
        autoplayPolicyRevision == revision &&
        videoAutoplayBlocked == blocked &&
        state.url?.let(BrowserUriPolicy::normalizeHttpUrl) == url

    private fun reconcileAutoplayPermission(
        permission: GeckoSession.PermissionDelegate.ContentPermission,
    ): Boolean {
        if (!isAutoplayPermission(permission)) return false
        val desiredValue = if (videoAutoplayBlocked) {
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        } else {
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        }
        val key = permission.autoplayKey()
        val currentValue = appliedAutoplayValues[key] ?: permission.value
        if (currentValue == desiredValue) return false
        storageController.setPermission(permission, desiredValue)
        appliedAutoplayValues[key] = desiredValue
        return true
    }

    private fun GeckoSession.PermissionDelegate.ContentPermission.autoplayKey() =
        AutoplayPermissionKey(
            uri = uri,
            contextId = contextId,
            privateMode = privateMode,
            permission = permission,
        )

    override fun executeMediaCommand(command: GeckoMediaCommand) {
        val mediaSession = activeMediaSession?.takeIf(MediaSession::isActive) ?: return
        when (command) {
            GeckoMediaCommand.Play -> mediaSession.play()
            GeckoMediaCommand.Pause -> mediaSession.pause()
            GeckoMediaCommand.Stop -> mediaSession.stop()
        }
    }

    override fun setAudioMuted(muted: Boolean) {
        audioMuted = muted
        activeMediaSession?.takeIf(MediaSession::isActive)?.muteAudio(muted)
    }

    override fun seekMedia(positionMillis: Long) {
        val mediaSession = activeMediaSession?.takeIf(MediaSession::isActive) ?: return
        mediaSession.seekTo(positionMillis.coerceAtLeast(0L) / 1_000.0, true)
    }

    @UiThread
    override fun notifyPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        if (closed || this.inPictureInPicture == inPictureInPicture) return
        this.inPictureInPicture = inPictureInPicture
        session.compositorController.onPipModeChanged(inPictureInPicture)
    }

    @UiThread
    override fun setPictureInPicturePlaybackExpected(expected: Boolean) {
        if (closed) return
        pictureInPicturePlaybackExpected = expected
        privacyBinding.setPictureInPicturePlaybackExpected(expected)
    }

    override fun setFullscreenStateListener(listener: GeckoFullscreenStateListener?) {
        fullscreenStateListener = listener
    }

    override fun exitFullscreen() {
        if (!closed) session.exitFullScreen()
    }

    @UiThread
    override fun setActive(active: Boolean) {
        if (closed) return
        if (!active) boundView?.cancelActiveTouch()
        if (this.active == active) return
        session.setActive(active)
        extensionController.setTabActive(session, active)
        this.active = active
        invalidateCredentialPrompts(recreateHost = active)
        if (active) extensionRuntime.onSelectedChromeSessionChanged()
        val cookieBehaviorChanged = cookieBehavior.update(
            owner = cookieBehaviorOwner,
            active = active,
            allow = privacyPolicy.allowsThirdPartyCookiesForPage(currentPageUrl),
            privateMode = isPrivate,
        )
        if (cookieBehaviorChanged && active && currentPageUrl != null) session.reload()
    }

    @UiThread
    override fun bindExtensionTab(tabId: String, generation: Long) {
        check(!closed) { "Cannot bind a closed Gecko session" }
        val identity = GeckoExtensionSessionIdentity(
            tabId = tabId,
            profileId = profileId,
            isPrivate = isPrivate,
            generation = generation,
        )
        extensionIdentity = identity
        extensionRuntime.attachChromeSession(session, identity)
    }

    @UiThread
    override fun setBackdropCaptureEnabled(enabled: Boolean) {
        if (backdropCaptureEnabled == enabled) return
        backdropCaptureEnabled = enabled
        boundView?.setBackdropCaptureEnabled(enabled)
    }

    @UiThread
    override fun createView(context: Context): View {
        check(!closed) { "Cannot bind a closed Gecko session" }
        check(boundView == null) { "Gecko session already has a bound View" }
        return CandyGeckoView(context).also { view ->
            view.setBackdropCaptureEnabled(backdropCaptureEnabled)
            view.configureAutofill(isPrivate)
            AndroidCredentialPromptHost.activityContext(context)?.let { activityContext ->
                view.setActivityContextDelegate { activityContext }
            }
            view.setSession(session)
            boundView = view
            if (active) credentialPromptHost = AndroidCredentialPromptHost.create(context)
        }
    }

    @UiThread
    override fun scrollMetrics(): BrowserEngineScrollMetrics? =
        privacyBinding.scrollMetrics()

    @UiThread
    override fun scrollToVerticalOffset(offsetPx: Int) {
        boundView?.scrollToVerticalOffset(offsetPx)
    }

    @UiThread
    override fun scrollByVerticalOffset(deltaPx: Int) {
        val currentOffsetPx = privacyBinding.scrollMetrics()?.offsetPx ?: 0
        boundView?.scrollToVerticalOffset(
            (currentOffsetPx + deltaPx).coerceAtLeast(0),
        )
    }

    @UiThread
    override fun awaitContentPresented(listener: () -> Unit) {
        if (!closed) contentPresentationGate.awaitContentPresented(listener)
    }

    @UiThread
    override fun releaseView(view: View) {
        val geckoView = view as? CandyGeckoView ?: return
        if (geckoView !== boundView) return
        // Clear ownership before releaseSession or prompt cancellation can synchronously re-enter
        // Compose and ask the controller to attach this session again.
        boundView = null
        credentialNavigationGeneration++
        val staleCredentialPromptHost = credentialPromptHost
        credentialPromptHost = null
        geckoView.setActivityContextDelegate(null)
        geckoView.releaseSession()
        contentPresentationGate.onSurfaceDetached()
        staleCredentialPromptHost?.close()
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

    @UiThread
    override fun extractPageForReader(onComplete: (String?) -> Unit) {
        if (closed || !privacyBound || currentPageUrl == null) {
            onComplete(null)
            return
        }
        privacyBinding.extractPageForReader(onComplete)
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

    override fun sessionStateSnapshot(): String? =
        if (closed || isPrivate) null else latestSessionState?.toString()

    override fun restoreSessionState(encodedState: String): Boolean {
        if (closed || isPrivate || encodedState.length !in 1..GeckoSessionStateSnapshotRules.MAX_ENCODED_STATE_CHARS) {
            return false
        }
        val restored = runCatching { GeckoSession.SessionState.fromString(encodedState) }.getOrNull()
            ?: return false
        latestSessionState = GeckoSession.SessionState(restored)
        pendingRestoredSessionState = restored
        restorePendingStateIfReady()
        return true
    }

    override fun loadUrl(url: String): Boolean {
        if (closed) return false
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        return loadValidatedUrl(safeUrl)
    }

    override fun loadExtensionUrl(url: String): Boolean {
        if (closed) return false
        val parsed = runCatching { URI(url) }.getOrNull() ?: return false
        if (
            parsed.scheme != "moz-extension" ||
            parsed.host.isNullOrBlank() ||
            parsed.rawUserInfo != null ||
            url.length > MAX_EXTENSION_URL_LENGTH ||
            url.any(Char::isISOControl)
        ) return false
        return loadValidatedUrl(parsed.normalize().toASCIIString())
    }

    private fun loadValidatedUrl(safeUrl: String): Boolean {
        invalidateCredentialPrompts(recreateHost = false)
        privacyFailureDescription?.let { description ->
            failPrivacyGate(description)
            return true
        }
        if (
            toppingHost.state != GeckoToppingHostState.Initializing &&
            trackingPermissions.isReady &&
            privacyBound
        ) {
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
        if (!trackingPermissionWaitRegistered) {
            trackingPermissionWaitRegistered = true
            trackingPermissions.runAfterInitialization { succeeded ->
                trackingPermissionWaitRegistered = false
                if (succeeded) {
                    loadPendingUrlIfReady()
                } else {
                    failPrivacyGate(TRACKING_PERMISSION_CLEANUP_FAILURE)
                }
            }
        }
        return true
    }

    override fun updatePrivacyPolicy(
        policy: GeckoPrivacyPolicy,
        reloadOnCookiePermissionChange: Boolean,
        onReady: () -> Unit,
    ) {
        privacyPolicy = policy
        val cookieBehaviorChanged = cookieBehavior.update(
            owner = cookieBehaviorOwner,
            active = active,
            allow = policy.allowsThirdPartyCookiesForPage(currentPageUrl),
            privateMode = isPrivate,
        )
        trackingPermissions.updateClaim(
            owner = trackingPermissionOwner,
            allow = trackingPermission?.let(policy::allowsThirdPartyCookiesForSite) == true,
            reloadOwnerOnChange = reloadOnCookiePermissionChange && !cookieBehaviorChanged,
        )
        if (cookieBehaviorChanged && reloadOnCookiePermissionChange) session.reload()
        privacyBinding.update(policy, onReady)
    }

    override fun goBack() {
        if (!closed && privacyBound && state.canGoBack) {
            invalidateCredentialPrompts(recreateHost = false)
            session.goBack()
        }
    }

    override fun goForward() {
        if (!closed && privacyBound && state.canGoForward) {
            invalidateCredentialPrompts(recreateHost = false)
            session.goForward()
        }
    }

    override fun goToHistoryIndex(index: Int) {
        if (!closed && privacyBound && index >= 0) {
            invalidateCredentialPrompts(recreateHost = false)
            session.gotoHistoryIndex(index)
        }
    }

    override fun historyUrlAtOffset(offset: Int): String? {
        if (closed || !privacyBound) return null
        val current = historyState ?: return null
        return current.urls.getOrNull(current.currentIndex + offset)
    }

    override fun reload() {
        if (!closed && privacyBound) {
            invalidateCredentialPrompts(recreateHost = false)
            session.reload()
        }
    }

    override fun stop() {
        if (!closed) {
            invalidateCredentialPrompts(recreateHost = true)
            session.stop()
        }
    }

    @UiThread
    override fun close() {
        if (closed) return
        closed = true
        if (active) extensionController.setTabActive(session, false)
        downloadTransfers.cancelOwner(session)
        extensionRuntime.detachChromeSession(session)
        extensionIdentity = null
        active = false
        invalidateCredentialPrompts(recreateHost = false)
        boundView?.releaseSession()
        boundView = null
        contentPresentationGate.close()
        listener = null
        historyStateListener = null
        contentTargetListener = null
        navigationRequestListener = null
        newSessionListener = null
        downloadResponseListener = null
        filePromptListener = null
        androidPermissionRequestListener = null
        contentPermissionRequestListener = null
        mediaPermissionRequestListener = null
        authPromptListener = null
        webPromptListener = null
        mediaStateListener = null
        fullscreenStateListener = null
        scrollListener = null
        activeMediaSession = null
        inPictureInPicture = false
        pictureInPicturePlaybackExpected = false
        pendingInitialUrl = null
        cookieBehavior.remove(cookieBehaviorOwner)
        trackingPermissions.remove(trackingPermissionOwner)
        privacyBinding.close()
        session.close()
    }

    private fun loadPendingUrlIfReady() {
        if (
            closed ||
            toppingHost.state == GeckoToppingHostState.Initializing ||
            !trackingPermissions.isReady ||
            !privacyBound
        ) return
        if (restorePendingStateIfReady()) return
        val pendingUrl = pendingInitialUrl ?: return
        pendingInitialUrl = null
        session.loadUri(pendingUrl)
    }

    private fun restorePendingStateIfReady(): Boolean {
        if (closed || !privacyBound) return false
        val restored = pendingRestoredSessionState ?: return false
        pendingRestoredSessionState = null
        session.restoreState(restored)
        return true
    }

    private fun updateMediaState(transform: (GeckoMediaSessionState) -> GeckoMediaSessionState) {
        val updated = transform(mediaState)
        if (updated == mediaState) return
        mediaState = updated
        mediaStateListener?.onStateChanged(updated)
    }

    private fun Double.toBoundedMediaMillis(): Long? = takeIf { value ->
        value.isFinite() && value >= 0
    }?.times(1_000)
        ?.coerceAtMost(MAX_MEDIA_TIME_MILLIS.toDouble())
        ?.toLong()

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

    private fun onContentProcessTerminated() = updateState { current ->
        current.copy(
            isLoading = false,
            lastNavigationSucceeded = false,
            crashed = true,
        )
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

    private companion object {
        const val GECKO_NAVIGATION_FAILURE = "Gecko navigation failed"
        const val CHOICE_VALUE_SEPARATOR = "\u001F"
        const val MAX_EXTENSION_URL_LENGTH = 4_096
        const val MAX_MEDIA_TIME_MILLIS = 604_800_000L
        const val TRACKING_PERMISSION_CLEANUP_FAILURE =
            "Gecko tracking permission cleanup failed"

        fun isAutoplayPermission(
            permission: GeckoSession.PermissionDelegate.ContentPermission,
        ): Boolean = permission.permission ==
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
            permission.permission ==
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
    }
}

/**
 * Small GeckoView edge exposing Android's protected scroll metrics to the shared chrome.
 * Gecko still owns all scrolling; Candy only renders and drags the indicator.
 */
internal class CandyGeckoView(context: Context) : FrameLayout(context), GeckoViewInsetHost {
    private var autofillEnabled = true
    private var activityContextDelegate: GeckoView.ActivityContextDelegate? = null
    private var insetLayout = GeckoViewInsetLayout(
        margins = GeckoViewInsets.Zero,
        rendererSafeAreaOverride = null,
        scrollableTopInsetPx = 0,
    )
    private var windowInsets: WindowInsetsCompat? = null
    private val engineView = createEngineView()

    init {
        addEngineView(engineView)
    }

    fun configureAutofill(isPrivate: Boolean) {
        importantForAutofill = if (isPrivate) {
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_AUTOFILL_YES
        }
        autofillEnabled = !isPrivate
        configureEngineView(engineView)
    }

    fun setBackdropCaptureEnabled(enabled: Boolean) {
        engineView.setBackdropCaptureEnabled(enabled)
    }

    fun setActivityContextDelegate(delegate: GeckoView.ActivityContextDelegate?) {
        activityContextDelegate = delegate
        engineView.setActivityContextDelegate(delegate)
    }

    fun setSession(session: GeckoSession) {
        engineView.setSession(session)
        engineView.dispatchRendererSafeAreaAfterSessionAttach()
    }

    fun releaseSession(): GeckoSession? {
        engineView.cancelActiveTouch()
        return engineView.releaseSession()
    }

    fun capturePixels(): GeckoResult<Bitmap> = engineView.captureContentPixels()

    fun cancelActiveTouch(): Boolean = engineView.cancelActiveTouch()

    fun requestEngineFocus(): Boolean = engineView.requestFocus()

    fun dispatchEngineKeyEvent(event: KeyEvent): Boolean = engineView.dispatchKeyEvent(event)

    fun dispatchEngineGenericMotionEvent(event: MotionEvent): Boolean =
        engineView.dispatchGenericMotionEvent(event)

    fun scrollToVerticalOffset(offsetPx: Int) {
        engineView.panZoomController.scrollTo(
            ScreenLength.fromPixels(0.0),
            ScreenLength.fromPixels(offsetPx.coerceAtLeast(0).toDouble()),
            PanZoomController.SCROLL_BEHAVIOR_AUTO,
        )
    }

    override fun updateInsets(
        layout: GeckoViewInsetLayout,
        windowInsets: WindowInsetsCompat,
    ) {
        insetLayout = layout
        this.windowInsets = windowInsets
        applyInsets(engineView)
    }

    private fun createEngineView(): CandyGeckoEngineView =
        CandyGeckoEngineView(context).also { view ->
            configureEngineView(view)
        }

    private fun configureEngineView(view: CandyGeckoEngineView) {
        view.importantForAutofill = importantForAutofill
        view.setAutofillEnabled(autofillEnabled)
        view.setActivityContextDelegate(activityContextDelegate)
        view.updateRendererSafeAreaOverride(insetLayout.rendererSafeAreaOverride)
    }

    private fun addEngineView(view: CandyGeckoEngineView) {
        val margins = insetLayout.margins
        addView(
            view,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(margins.left, margins.top, margins.right, margins.bottom)
            },
        )
        windowInsets?.let { insets -> ViewCompat.dispatchApplyWindowInsets(view, insets) }
        view.updateRendererSafeAreaOverride(insetLayout.rendererSafeAreaOverride)
    }

    private fun applyInsets(view: CandyGeckoEngineView) {
        val margins = insetLayout.margins
        (view.layoutParams as? LayoutParams)?.let { layoutParams ->
            if (
                layoutParams.leftMargin != margins.left ||
                layoutParams.topMargin != margins.top ||
                layoutParams.rightMargin != margins.right ||
                layoutParams.bottomMargin != margins.bottom
            ) {
                layoutParams.setMargins(margins.left, margins.top, margins.right, margins.bottom)
                view.layoutParams = layoutParams
            }
        }
        windowInsets?.let { insets -> ViewCompat.dispatchApplyWindowInsets(view, insets) }
        view.updateRendererSafeAreaOverride(insetLayout.rendererSafeAreaOverride)
    }
}

private class CandyGeckoEngineView(context: Context) : CandyGeckoViewSafeAreaBridge(context) {
    private var backdropCaptureEnabled = false
    private var gestureState = GeckoContentGestureState()
    private var latestTouchEvent: MotionEvent? = null
    private var rendererSafeAreaOverride: GeckoViewInsets? = null

    fun setBackdropCaptureEnabled(enabled: Boolean) {
        if (backdropCaptureEnabled == enabled) return
        backdropCaptureEnabled = enabled
        setViewBackend(
            if (enabled) GeckoView.BACKEND_TEXTURE_VIEW else GeckoView.BACKEND_SURFACE_VIEW,
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (
            event.actionMasked != MotionEvent.ACTION_DOWN &&
            GeckoContentGestureRules.hasCancelledStream(gestureState)
        ) {
            return true
        }
        val handled = super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureState = GeckoContentGestureRules.onDown(
                    state = gestureState,
                    downTime = event.downTime,
                    handled = handled,
                )
                if (handled) rememberTouchEvent(event) else clearTouchEvent()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                gestureState = GeckoContentGestureRules.onTerminal(
                    state = gestureState,
                    downTime = event.downTime,
                )
                if (gestureState.activeTouchDownTime == null) clearTouchEvent()
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP,
            -> if (gestureState.activeTouchDownTime == event.downTime) {
                rememberTouchEvent(event)
            }

            else -> Unit
        }
        return handled
    }

    fun cancelActiveTouch(): Boolean {
        val transition = GeckoContentGestureRules.cancel(gestureState)
        gestureState = transition.state
        val downTime = transition.dispatchCancelDownTime ?: return false
        val cancelEvent = latestTouchEvent?.takeIf { event -> event.downTime == downTime }
            ?: return false
        latestTouchEvent = null
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        return try {
            super.dispatchTouchEvent(cancelEvent)
        } finally {
            cancelEvent.recycle()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) cancelActiveTouch()
        super.onWindowFocusChanged(hasWindowFocus)
    }

    fun updateRendererSafeAreaOverride(insets: GeckoViewInsets?) {
        rendererSafeAreaOverride = insets
        dispatchRendererSafeAreaOverride()
    }

    fun dispatchRendererSafeAreaAfterSessionAttach() {
        post(::dispatchRendererSafeAreaOverride)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        dispatchRendererSafeAreaOverride()
    }

    override fun onDetachedFromWindow() {
        cancelActiveTouch()
        super.onDetachedFromWindow()
    }

    override fun gatherTransparentRegion(region: Region?): Boolean {
        val gathered = super.gatherTransparentRegion(region)
        if (rendererSafeAreaOverride != null) dispatchRendererSafeAreaOverride()
        return gathered
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        post(::dispatchRendererSafeAreaOverride)
    }

    fun captureContentPixels(): GeckoResult<Bitmap> = capturePixels()

    private fun dispatchRendererSafeAreaOverride() {
        val insets = rendererSafeAreaOverride ?: ViewCompat.getRootWindowInsets(this)
            ?.getInsets(SAFE_AREA_INSET_TYPES)
            ?.let { safeArea ->
                GeckoViewInsets(
                    left = safeArea.left,
                    top = safeArea.top,
                    right = safeArea.right,
                    bottom = safeArea.bottom,
                )
            }
            ?: return
        dispatchCandySafeAreaInsets(
            insets.top,
            insets.right,
            insets.bottom,
            insets.left,
        )
    }

    private fun rememberTouchEvent(event: MotionEvent) {
        latestTouchEvent?.recycle()
        latestTouchEvent = MotionEvent.obtainNoHistory(event)
    }

    private fun clearTouchEvent() {
        latestTouchEvent?.recycle()
        latestTouchEvent = null
    }

    private companion object {
        val SAFE_AREA_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }
}

private class GeckoWebPromptResponse(
    private val isCurrent: () -> Boolean,
    private val confirmAction: (String?) -> GeckoSession.PromptDelegate.PromptResponse,
    private val dismissAction: () -> GeckoSession.PromptDelegate.PromptResponse,
) : BrowserEngineWebPromptResponse {
    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
    private var completed = false

    override fun confirm(value: String?) {
        if (completed) return
        completed = true
        result.complete(if (isCurrent()) confirmAction(value) else dismissAction())
    }

    override fun dismiss() {
        if (completed) return
        completed = true
        result.complete(dismissAction())
    }
}

private fun Int.toBrowserContentTargetKind(): BrowserContentTargetKind = when (this) {
    GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> BrowserContentTargetKind.Image
    GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO -> BrowserContentTargetKind.Video
    GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> BrowserContentTargetKind.Audio
    else -> BrowserContentTargetKind.None
}

private fun GeckoPrivacyPolicy.allowsThirdPartyCookiesForSite(
    permission: GeckoSession.PermissionDelegate.ContentPermission,
): Boolean = blockThirdPartyCookies &&
    allowThirdPartyCookiesForSite &&
    pageHost != null &&
    runCatching { URI(permission.uri).host?.lowercase() }.getOrNull() == pageHost

private fun GeckoPrivacyPolicy.allowsThirdPartyCookiesForPage(url: String?): Boolean =
    blockThirdPartyCookies &&
        allowThirdPartyCookiesForSite &&
        pageHost != null &&
        runCatching { URI(url.orEmpty()).host?.lowercase() }.getOrNull() == pageHost

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
    temporary = metaData.temporary,
    signedState = metaData.signedState,
    disabledFlags = metaData.disabledFlags,
    location = location,
    baseUrl = metaData.baseUrl,
    optionsPageUrl = metaData.optionsPageUrl,
    opensOptionsPageInTab = metaData.openOptionsPageInTab,
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
