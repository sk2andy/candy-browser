package dev.sk2andy.materialbrowser.browser.gecko

internal data class GeckoExtension(
    val id: String,
    val name: String?,
    val version: String?,
    val enabled: Boolean,
    val allowedInPrivateBrowsing: Boolean,
    val isBuiltIn: Boolean,
    val temporary: Boolean = false,
    val signedState: Int = -1,
    val disabledFlags: Int = 0,
    val location: String? = null,
    val baseUrl: String? = null,
    val optionsPageUrl: String? = null,
    val opensOptionsPageInTab: Boolean = false,
)

internal data class GeckoExtensionSnapshot(
    val extensions: List<GeckoExtension>,
) {
    fun extension(id: String): GeckoExtension? = extensions.firstOrNull { it.id == id }
}

internal data class GeckoExtensionOptionsTarget(
    val extensionId: String,
    val url: String,
)

internal enum class GeckoExtensionPermissionRequestKind {
    Install,
    Update,
    Optional,
}

internal data class GeckoExtensionPermissionRequest(
    val kind: GeckoExtensionPermissionRequestKind,
    val extensionId: String,
    val extensionName: String?,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollectionPermissions: List<String> = emptyList(),
)

internal data class GeckoExtensionPermissionDecision(
    val grantPermissions: Boolean,
    val allowInPrivateBrowsing: Boolean = false,
) {
    companion object {
        val Denied = GeckoExtensionPermissionDecision(grantPermissions = false)
    }
}

internal fun interface GeckoExtensionPermissionPrompt {
    suspend fun decide(
        request: GeckoExtensionPermissionRequest,
    ): GeckoExtensionPermissionDecision
}

internal fun interface GeckoExtensionChangeListener {
    suspend fun onChanged()
}

internal data class GeckoExtensionManagementContext(
    val profileId: String,
    val isPrivate: Boolean,
)

internal enum class GeckoExtensionRejection {
    InvalidSignedXpiUri,
    InvalidManagementContext,
    PrivateManagementContext,
    ExtensionNotFound,
    BuiltInExtensionProtected,
}

internal sealed interface GeckoExtensionReadResult {
    data class Loaded(val snapshot: GeckoExtensionSnapshot) : GeckoExtensionReadResult

    data class Failed(val error: Throwable) : GeckoExtensionReadResult
}

internal sealed interface GeckoExtensionMutationResult {
    data class Applied(
        val extensionId: String,
        val snapshot: GeckoExtensionSnapshot,
    ) : GeckoExtensionMutationResult

    data class Rejected(val reason: GeckoExtensionRejection) : GeckoExtensionMutationResult

    data class Failed(val error: Throwable) : GeckoExtensionMutationResult
}

/**
 * Narrow port implemented by WebExtensionController.
 *
 * installSignedXpi must call GeckoView's normal install API. It must never route untrusted input
 * through installBuiltIn: Gecko remains the authority that validates the XPI and Mozilla signature.
 */
internal interface GeckoExtensionRuntime {
    fun setPermissionPrompt(prompt: GeckoExtensionPermissionPrompt)

    fun setChangeListener(listener: GeckoExtensionChangeListener) = Unit

    fun setChromeHost(host: GeckoExtensionChromeHost?) = Unit

    fun attachChromeSession(
        session: org.mozilla.geckoview.GeckoSession,
        identity: GeckoExtensionSessionIdentity,
    ) = Unit

    fun detachChromeSession(session: org.mozilla.geckoview.GeckoSession) = Unit

    fun onSelectedChromeSessionChanged() = Unit

    fun clickChromeAction(key: GeckoExtensionActionKey): Boolean = false

    fun dismissChromePopup() = Unit

    suspend fun listInstalled(): List<GeckoExtension>

    suspend fun installSignedXpi(
        uri: String,
        installationMethod: String,
    ): GeckoExtension

    suspend fun enable(extensionId: String): GeckoExtension

    suspend fun disable(extensionId: String): GeckoExtension

    suspend fun update(extensionId: String): GeckoExtension

    suspend fun setAllowedInPrivateBrowsing(
        extensionId: String,
        allowed: Boolean,
    ): GeckoExtension

    suspend fun uninstall(extensionId: String)
}
