package dev.sk2andy.materialbrowser.browser.gecko

import android.graphics.Bitmap

internal enum class GeckoExtensionActionKind {
    Browser,
    Page,
}

internal data class GeckoExtensionActionKey(
    val extensionId: String,
    val tabId: String?,
    val kind: GeckoExtensionActionKind,
) {
    /** Bundle-saveable identity for Compose lazy containers and state restoration. */
    val saveableId: String
        get() = buildString {
            append(extensionId.length)
            append(':')
            append(extensionId)
            append('|')
            tabId?.let {
                append(it.length)
                append(':')
                append(it)
            } ?: append('-')
            append('|')
            append(kind.name)
        }
}

internal data class GeckoExtensionActionState(
    val key: GeckoExtensionActionKey,
    val title: String,
    val enabled: Boolean,
    val badgeText: String?,
    val badgeBackgroundColor: Int?,
    val badgeTextColor: Int?,
    val icon: Bitmap? = null,
)

internal data class GeckoExtensionSessionIdentity(
    val tabId: String,
    val profileId: String,
    val isPrivate: Boolean,
    val generation: Long,
    val isolationEnabled: Boolean = false,
)

internal data class GeckoExtensionPopupIdentity(
    val extensionId: String,
    val owner: GeckoExtensionSessionIdentity,
    val generation: Long,
)

internal data class GeckoExtensionCreateTabRequest(
    val extensionId: String,
    val source: GeckoExtensionSessionIdentity?,
    val url: String?,
    val active: Boolean,
    val pinned: Boolean,
    val index: Int?,
    val cookieStoreId: String?,
    val discarded: Boolean,
    val openInReaderMode: Boolean,
)

internal data class GeckoExtensionUpdateTabRequest(
    val extensionId: String,
    val target: GeckoExtensionSessionIdentity,
    val url: String?,
    val active: Boolean?,
    val pinned: Boolean?,
    val highlighted: Boolean?,
    val muted: Boolean?,
    val autoDiscardable: Boolean?,
)

internal enum class GeckoExtensionTabRequestRejection {
    Disabled,
    PrivateAccessDenied,
    StaleSession,
    InvalidUrl,
    UnsupportedDetail,
}

internal sealed interface GeckoExtensionTabRequestResult<out T> {
    data class Allowed<T>(val value: T) : GeckoExtensionTabRequestResult<T>

    data class Rejected(
        val reason: GeckoExtensionTabRequestRejection,
    ) : GeckoExtensionTabRequestResult<Nothing>
}

internal data class GeckoExtensionCapability(
    val api: String,
    val support: GeckoExtensionCapabilitySupport,
    val evidence: String,
)

internal enum class GeckoExtensionCapabilitySupport {
    GeckoOwned,
    CandyDelegate,
    Unsupported,
}
