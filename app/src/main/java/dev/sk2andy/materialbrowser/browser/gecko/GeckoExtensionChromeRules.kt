package dev.sk2andy.materialbrowser.browser.gecko

import java.net.URI
import java.util.Locale

internal object GeckoExtensionChromeRules {
    fun resolveAction(
        default: GeckoExtensionActionState?,
        tabOverride: GeckoExtensionActionState?,
    ): GeckoExtensionActionState? {
        if (default == null) return tabOverride
        if (tabOverride == null) return default
        require(default.key.extensionId == tabOverride.key.extensionId)
        require(default.key.kind == tabOverride.key.kind)
        return tabOverride.copy(
            title = tabOverride.title.ifBlank { default.title },
            badgeText = tabOverride.badgeText ?: default.badgeText,
            badgeBackgroundColor =
                tabOverride.badgeBackgroundColor ?: default.badgeBackgroundColor,
            badgeTextColor = tabOverride.badgeTextColor ?: default.badgeTextColor,
            icon = tabOverride.icon ?: default.icon,
        )
    }

    fun normalizeAction(
        key: GeckoExtensionActionKey,
        title: String?,
        enabled: Boolean?,
        badgeText: String?,
        badgeBackgroundColor: Int?,
        badgeTextColor: Int?,
        icon: android.graphics.Bitmap? = null,
    ): GeckoExtensionActionState? {
        if (!bounded(key.extensionId, MAX_EXTENSION_ID_LENGTH)) return null
        if (key.tabId != null && !bounded(key.tabId, MAX_TAB_ID_LENGTH)) return null
        if (!validOptionalText(title, MAX_TITLE_LENGTH)) return null
        if (!validOptionalText(badgeText, MAX_BADGE_LENGTH)) return null
        val normalizedTitle = title.orEmpty()
        val normalizedBadge = badgeText
        return GeckoExtensionActionState(
            key = key,
            title = normalizedTitle,
            enabled = enabled != false,
            badgeText = normalizedBadge,
            badgeBackgroundColor = badgeBackgroundColor,
            badgeTextColor = badgeTextColor,
            icon = icon,
        )
    }

    fun validateCreateTab(
        request: GeckoExtensionCreateTabRequest,
        extension: GeckoExtension,
    ): GeckoExtensionTabRequestResult<GeckoExtensionCreateTabRequest> {
        extensionRejection(extension, request.source)?.let {
            return GeckoExtensionTabRequestResult.Rejected(it)
        }
        if (
            request.index?.let { index -> index < 0 } == true ||
            request.cookieStoreId != null ||
            request.discarded ||
            request.openInReaderMode
        ) {
            return GeckoExtensionTabRequestResult.Rejected(
                GeckoExtensionTabRequestRejection.UnsupportedDetail,
            )
        }
        val normalizedUrl = request.url?.let { rawUrl ->
            normalizeWebOrOwnExtensionUrl(rawUrl, extension.baseUrl)
        }
            ?: request.url?.let {
                return GeckoExtensionTabRequestResult.Rejected(
                    GeckoExtensionTabRequestRejection.InvalidUrl,
                )
            }
        return GeckoExtensionTabRequestResult.Allowed(request.copy(url = normalizedUrl))
    }

    fun validateUpdateTab(
        request: GeckoExtensionUpdateTabRequest,
        extension: GeckoExtension,
        currentTarget: GeckoExtensionSessionIdentity?,
    ): GeckoExtensionTabRequestResult<GeckoExtensionUpdateTabRequest> {
        if (currentTarget != request.target) {
            return GeckoExtensionTabRequestResult.Rejected(
                GeckoExtensionTabRequestRejection.StaleSession,
            )
        }
        extensionRejection(extension, request.target)?.let {
            return GeckoExtensionTabRequestResult.Rejected(it)
        }
        if (request.highlighted != null || request.autoDiscardable != null) {
            return GeckoExtensionTabRequestResult.Rejected(
                GeckoExtensionTabRequestRejection.UnsupportedDetail,
            )
        }
        val normalizedUrl = request.url?.let { rawUrl ->
            normalizeWebOrOwnExtensionUrl(rawUrl, extension.baseUrl)
        }
            ?: request.url?.let {
                return GeckoExtensionTabRequestResult.Rejected(
                    GeckoExtensionTabRequestRejection.InvalidUrl,
                )
            }
        return GeckoExtensionTabRequestResult.Allowed(request.copy(url = normalizedUrl))
    }

    fun mayCloseTab(
        extension: GeckoExtension,
        requestedTarget: GeckoExtensionSessionIdentity,
        currentTarget: GeckoExtensionSessionIdentity?,
    ): GeckoExtensionTabRequestRejection? {
        if (requestedTarget != currentTarget) return GeckoExtensionTabRequestRejection.StaleSession
        return extensionRejection(extension, requestedTarget)
    }

    fun mayKeepPopup(
        popup: GeckoExtensionPopupIdentity,
        currentOwner: GeckoExtensionSessionIdentity?,
        extension: GeckoExtension?,
    ): Boolean =
        currentOwner == popup.owner &&
            extension?.enabled == true &&
            (!popup.owner.isPrivate || extension.allowedInPrivateBrowsing)

    fun normalizeOptionsPageUrl(baseUrl: String?, optionsPageUrl: String?): String? {
        val base = parseExtensionUrl(baseUrl) ?: return null
        val options = parseExtensionUrl(optionsPageUrl) ?: return null
        if (base.scheme != options.scheme || base.host != options.host || base.port != options.port) {
            return null
        }
        return options.normalize().toASCIIString()
    }

    fun effectiveAudioMuted(
        domainMuted: Boolean,
        extensionOverride: Boolean?,
    ): Boolean = extensionOverride ?: domainMuted

    val capabilityMatrix = listOf(
        GeckoExtensionCapability("browserAction/action + pageAction", GeckoExtensionCapabilitySupport.CandyDelegate, "WebExtension.ActionDelegate"),
        GeckoExtensionCapability("popup", GeckoExtensionCapabilitySupport.CandyDelegate, "ActionDelegate.onOpenPopup/onTogglePopup"),
        GeckoExtensionCapability("options", GeckoExtensionCapabilitySupport.CandyDelegate, "TabDelegate.onOpenOptionsPage"),
        GeckoExtensionCapability("tabs.create", GeckoExtensionCapabilitySupport.CandyDelegate, "Active/background, pinned and bounded index through TabDelegate"),
        GeckoExtensionCapability("tabs.update/remove", GeckoExtensionCapabilitySupport.CandyDelegate, "Active, URL and close through SessionTabDelegate"),
        GeckoExtensionCapability("tabs.create container/discarded/reader", GeckoExtensionCapabilitySupport.Unsupported, "Candy has no matching GeckoView 140 host state"),
        GeckoExtensionCapability("tabs.update highlighted/autoDiscardable", GeckoExtensionCapabilitySupport.Unsupported, "Candy has no multi-selection or auto-discard tab model"),
        GeckoExtensionCapability("tabs.update muted/pinned", GeckoExtensionCapabilitySupport.Unsupported, "GeckoView 140 rejects these fields in the Firefox extension schema before SessionTabDelegate"),
        GeckoExtensionCapability("tabs query/events", GeckoExtensionCapabilitySupport.GeckoOwned, "setTabActive + Gecko session delegates"),
        GeckoExtensionCapability("webNavigation", GeckoExtensionCapabilitySupport.GeckoOwned, "Gecko extension engine"),
        GeckoExtensionCapability("scripting/executeScript + CSS", GeckoExtensionCapabilitySupport.GeckoOwned, "Gecko extension engine"),
        GeckoExtensionCapability("storage", GeckoExtensionCapabilitySupport.GeckoOwned, "Gecko extension engine/profile"),
        GeckoExtensionCapability("runtime messaging", GeckoExtensionCapabilitySupport.GeckoOwned, "Gecko extension engine"),
        GeckoExtensionCapability("downloads", GeckoExtensionCapabilitySupport.CandyDelegate, "WebExtension.DownloadDelegate"),
        GeckoExtensionCapability("windows API", GeckoExtensionCapabilitySupport.Unsupported, "No GeckoView 140 public window delegate"),
    )

    private fun extensionRejection(
        extension: GeckoExtension,
        session: GeckoExtensionSessionIdentity?,
    ): GeckoExtensionTabRequestRejection? = when {
        !extension.enabled -> GeckoExtensionTabRequestRejection.Disabled
        session?.isPrivate == true && !extension.allowedInPrivateBrowsing ->
            GeckoExtensionTabRequestRejection.PrivateAccessDenied
        else -> null
    }

    private fun normalizeWebOrOwnExtensionUrl(rawUrl: String, extensionBaseUrl: String?): String? {
        if (rawUrl.isBlank() || rawUrl.length > MAX_URL_LENGTH || rawUrl.any(Char::isISOControl)) {
            return null
        }
        if (rawUrl != rawUrl.trim()) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme !in ALLOWED_SCHEMES || uri.host.isNullOrBlank() || uri.rawUserInfo != null) {
            return null
        }
        if (scheme == MOZ_EXTENSION_SCHEME) {
            val base = parseExtensionUrl(extensionBaseUrl) ?: return null
            if (base.host != uri.host || base.port != uri.port) return null
        }
        if (uri.rawFragment?.length.orZero() > MAX_FRAGMENT_LENGTH) return null
        return uri.normalize().toASCIIString()
    }

    private fun parseExtensionUrl(rawUrl: String?): URI? {
        if (rawUrl == null || rawUrl.length > MAX_URL_LENGTH || rawUrl.any(Char::isISOControl)) {
            return null
        }
        if (rawUrl != rawUrl.trim()) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) != MOZ_EXTENSION_SCHEME) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        return uri
    }

    private fun validOptionalText(value: String?, maxLength: Int): Boolean =
        value == null || (value.length <= maxLength && value.none(Char::isISOControl))

    private fun bounded(value: String, maxLength: Int): Boolean =
        value.isNotBlank() && value.length <= maxLength && value.none(Char::isISOControl)

    private fun Int?.orZero(): Int = this ?: 0

    private const val MAX_EXTENSION_ID_LENGTH = 512
    private const val MAX_TAB_ID_LENGTH = 256
    private const val MAX_TITLE_LENGTH = 512
    private const val MAX_BADGE_LENGTH = 64
    private const val MAX_URL_LENGTH = 4_096
    private const val MAX_FRAGMENT_LENGTH = 2_048
    private const val MOZ_EXTENSION_SCHEME = "moz-extension"
    private val ALLOWED_SCHEMES = setOf("http", "https", MOZ_EXTENSION_SCHEME)
}
