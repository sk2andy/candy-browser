package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserSession
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtension
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagementContext
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionMutationResult
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionPermissionDecision
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionPermissionRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionRepository
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionRuntime
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionSnapshot
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.shared.CandySharedFacade
import dev.sk2andy.materialbrowser.ui.GeckoBrowserScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class GeckoBrowserActivity : AppCompatActivity() {
    private lateinit var session: GeckoBrowserSession
    private lateinit var extensions: GeckoExtensionRepository
    private lateinit var extensionRuntime: GeckoExtensionRuntime
    private val shared = CandySharedFacade()
    private val managementContext = GeckoExtensionManagementContext(
        profileId = DEFAULT_PROFILE_ID,
        isPrivate = false,
    )

    private var browserState by mutableStateOf(GeckoBrowserSessionState())
    private var address by mutableStateOf(DEFAULT_START_URL)
    private var extensionSnapshot by mutableStateOf(GeckoExtensionSnapshot(emptyList()))
    private var extensionsVisible by mutableStateOf(false)
    private var extensionBusy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var permissionRequest by mutableStateOf<GeckoExtensionPermissionRequest?>(null)
    private var permissionDecision: CompletableDeferred<GeckoExtensionPermissionDecision>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val runtime = GeckoRuntimeOwner.getOrCreate(applicationContext)
        extensionRuntime = runtime.extensions
        extensions = GeckoExtensionRepository(extensionRuntime, ::requestExtensionPermission)
        session = runtime.createSession(DEFAULT_PROFILE_ID, isPrivate = false)
        session.setStateListener { state ->
            browserState = state
            state.url?.let { currentUrl -> address = currentUrl }
        }
        address = intent.dataString ?: DEFAULT_START_URL

        val appearanceSettings = BrowserSessionStore(this).loadAppearanceSettings()
        setContent {
            CandyTheme(settings = appearanceSettings) {
                LaunchedEffect(Unit) {
                    refreshExtensions()
                    navigate()
                }
                GeckoBrowserScreen(
                    session = session,
                    state = browserState,
                    address = address,
                    extensions = extensionSnapshot,
                    extensionsVisible = extensionsVisible,
                    extensionBusy = extensionBusy,
                    message = message,
                    permissionRequest = permissionRequest,
                    onAddressChanged = { address = it },
                    onNavigate = ::navigate,
                    onGoBack = session::goBack,
                    onGoForward = session::goForward,
                    onReloadOrStop = {
                        if (browserState.isLoading) session.stop() else session.reload()
                    },
                    onShowExtensions = {
                        extensionsVisible = true
                        refreshExtensions()
                    },
                    onHideExtensions = { extensionsVisible = false },
                    onInstallExtension = ::installExtension,
                    onSetExtensionEnabled = ::setExtensionEnabled,
                    onSetExtensionPrivate = ::setExtensionPrivate,
                    onUpdateExtension = ::updateExtension,
                    onUninstallExtension = ::uninstallExtension,
                    onPermissionDecision = ::completePermissionRequest,
                )
            }
        }
    }

    override fun onDestroy() {
        permissionDecision?.complete(GeckoExtensionPermissionDecision.Denied)
        permissionDecision = null
        permissionRequest = null
        if (::extensionRuntime.isInitialized) {
            extensionRuntime.setPermissionPrompt { GeckoExtensionPermissionDecision.Denied }
            extensionRuntime.setChangeListener { }
        }
        if (::session.isInitialized) session.close()
        super.onDestroy()
    }

    private fun navigate() {
        val resolved = shared.resolveAddress(address).url?.value
        if (resolved == null || !session.loadUrl(resolved)) {
            message = getString(R.string.gecko_invalid_address)
        } else {
            address = resolved
            message = null
        }
    }

    private fun refreshExtensions() {
        if (!::extensions.isInitialized) return
        lifecycleScope.launch {
            when (val result = extensions.refresh()) {
                is dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionReadResult.Loaded -> {
                    extensionSnapshot = result.snapshot
                    message = null
                }
                is dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionReadResult.Failed -> {
                    message = getString(R.string.gecko_extension_action_failed)
                }
            }
        }
    }

    private fun installExtension(url: String) = mutateExtension {
        extensions.installSignedXpi(url, managementContext)
    }

    private fun setExtensionEnabled(extension: GeckoExtension, enabled: Boolean) = mutateExtension {
        if (enabled) {
            extensions.enable(extension.id, managementContext)
        } else {
            extensions.disable(extension.id, managementContext)
        }
    }

    private fun setExtensionPrivate(extension: GeckoExtension, allowed: Boolean) = mutateExtension {
        extensions.setAllowedInPrivateBrowsing(extension.id, allowed, managementContext)
    }

    private fun updateExtension(extension: GeckoExtension) = mutateExtension {
        extensions.update(extension.id, managementContext)
    }

    private fun uninstallExtension(extension: GeckoExtension) = mutateExtension {
        extensions.uninstall(extension.id, managementContext)
    }

    private fun mutateExtension(
        mutation: suspend () -> GeckoExtensionMutationResult,
    ) {
        if (extensionBusy) return
        extensionBusy = true
        message = null
        lifecycleScope.launch {
            when (val result = mutation()) {
                is GeckoExtensionMutationResult.Applied -> extensionSnapshot = result.snapshot
                is GeckoExtensionMutationResult.Rejected -> {
                    message = when (result.reason) {
                        dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionRejection.InvalidSignedXpiUri ->
                            getString(R.string.gecko_extension_invalid_xpi)
                        else -> getString(R.string.gecko_extension_action_rejected)
                    }
                }
                is GeckoExtensionMutationResult.Failed -> {
                    message = getString(R.string.gecko_extension_action_failed)
                }
            }
            extensionBusy = false
        }
    }

    private suspend fun requestExtensionPermission(
        request: GeckoExtensionPermissionRequest,
    ): GeckoExtensionPermissionDecision {
        if (permissionDecision != null) return GeckoExtensionPermissionDecision.Denied
        val deferred = CompletableDeferred<GeckoExtensionPermissionDecision>()
        permissionDecision = deferred
        permissionRequest = request
        return try {
            deferred.await()
        } finally {
            if (permissionDecision === deferred) {
                permissionDecision = null
                permissionRequest = null
            }
        }
    }

    private fun completePermissionRequest(granted: Boolean, allowPrivate: Boolean) {
        val deferred = permissionDecision ?: return
        deferred.complete(
            GeckoExtensionPermissionDecision(
                grantPermissions = granted,
                allowInPrivateBrowsing = granted && allowPrivate,
            ),
        )
        permissionDecision = null
        permissionRequest = null
    }

    companion object {
        private const val DEFAULT_PROFILE_ID = "default"
        private const val DEFAULT_START_URL = "https://www.mozilla.org/firefox/browsers/mobile/"

        fun createIntent(context: Context): Intent = Intent(context, GeckoBrowserActivity::class.java)
    }
}
