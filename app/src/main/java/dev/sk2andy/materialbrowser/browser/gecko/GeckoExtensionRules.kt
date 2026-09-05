package dev.sk2andy.materialbrowser.browser.gecko

import java.net.URI
import java.util.Locale

internal object GeckoExtensionRules {
    fun normalizeSignedXpiUri(rawUri: String): String? {
        if (rawUri.isBlank() || rawUri.length > MAX_URI_LENGTH || rawUri.any(Char::isISOControl)) {
            return null
        }
        if (rawUri != rawUri.trim()) return null
        val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) != HTTPS_SCHEME) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null) return null
        if (uri.port !in setOf(-1, HTTPS_PORT)) return null
        if (uri.rawPath.isNullOrBlank()) return null
        return uri.normalize().toASCIIString()
    }

    fun managementRejection(
        context: GeckoExtensionManagementContext,
    ): GeckoExtensionRejection? = when {
        context.profileId.isBlank() ||
            context.profileId.length > MAX_PROFILE_ID_LENGTH ||
            context.profileId.any(Char::isISOControl) ->
            GeckoExtensionRejection.InvalidManagementContext

        context.isPrivate -> GeckoExtensionRejection.PrivateManagementContext
        else -> null
    }

    fun canonicalSnapshot(extensions: List<GeckoExtension>): GeckoExtensionSnapshot {
        require(extensions.size <= MAX_EXTENSIONS) { "Too many installed Gecko extensions" }
        require(extensions.map(GeckoExtension::id).distinct().size == extensions.size) {
            "Duplicate Gecko extension ID"
        }
        extensions.forEach(::requireValidExtension)
        return GeckoExtensionSnapshot(
            extensions.sortedWith(
                compareBy<GeckoExtension> { extension ->
                    extension.name?.lowercase(Locale.ROOT) ?: extension.id.lowercase(Locale.ROOT)
                }.thenBy(GeckoExtension::id),
            ),
        )
    }

    fun normalizePermissionRequest(
        request: GeckoExtensionPermissionRequest,
    ): GeckoExtensionPermissionRequest? {
        if (!isBoundedText(request.extensionId, MAX_EXTENSION_ID_LENGTH)) return null
        if (request.extensionName != null &&
            !isBoundedText(request.extensionName, MAX_EXTENSION_NAME_LENGTH)
        ) return null
        val permissions = normalizeValues(request.permissions) ?: return null
        val origins = normalizeValues(request.origins) ?: return null
        if (permissions.size + origins.size > MAX_PERMISSION_VALUES) {
            return null
        }
        return request.copy(
            permissions = permissions,
            origins = origins,
        )
    }

    fun restrictPermissionDecision(
        request: GeckoExtensionPermissionRequest,
        decision: GeckoExtensionPermissionDecision,
    ): GeckoExtensionPermissionDecision {
        if (!decision.grantPermissions) return GeckoExtensionPermissionDecision.Denied
        return decision.copy(
            allowInPrivateBrowsing =
                decision.allowInPrivateBrowsing &&
                    request.kind == GeckoExtensionPermissionRequestKind.Install,
        )
    }

    private fun normalizeValues(values: List<String>): List<String>? {
        if (values.size > MAX_PERMISSION_VALUES) return null
        if (values.any { value -> !isBoundedText(value, MAX_PERMISSION_VALUE_LENGTH) }) return null
        return values.distinct().sorted()
    }

    private fun requireValidExtension(extension: GeckoExtension) {
        require(isBoundedText(extension.id, MAX_EXTENSION_ID_LENGTH)) {
            "Invalid Gecko extension ID"
        }
        require(
            extension.name == null ||
                isBoundedText(extension.name, MAX_EXTENSION_NAME_LENGTH),
        ) { "Invalid Gecko extension name" }
        require(
            extension.version == null ||
                isBoundedText(extension.version, MAX_EXTENSION_VERSION_LENGTH),
        ) { "Invalid Gecko extension version" }
    }

    private fun isBoundedText(value: String, maxLength: Int): Boolean =
        value.isNotBlank() && value.length <= maxLength && value.none(Char::isISOControl)

    private const val HTTPS_SCHEME = "https"
    private const val HTTPS_PORT = 443
    private const val MAX_URI_LENGTH = 4_096
    private const val MAX_PROFILE_ID_LENGTH = 256
    private const val MAX_EXTENSIONS = 512
    private const val MAX_EXTENSION_ID_LENGTH = 512
    private const val MAX_EXTENSION_NAME_LENGTH = 512
    private const val MAX_EXTENSION_VERSION_LENGTH = 128
    private const val MAX_PERMISSION_VALUES = 256
    private const val MAX_PERMISSION_VALUE_LENGTH = 2_048
}
