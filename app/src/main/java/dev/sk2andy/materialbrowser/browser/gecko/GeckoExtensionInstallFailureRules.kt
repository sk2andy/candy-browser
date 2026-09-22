package dev.sk2andy.materialbrowser.browser.gecko

import org.mozilla.geckoview.WebExtension

internal enum class GeckoExtensionInstallFailure {
    NetworkFailure,
    IncorrectHash,
    CorruptFile,
    FileAccess,
    Unsigned,
    UnexpectedType,
    UnexpectedVersion,
    IncorrectId,
    InvalidDomain,
    Blocklisted,
    Incompatible,
    UnsupportedType,
    AdminOnly,
    SoftBlocked,
    Cancelled,
    Postponed,
    Unknown,
}

internal class GeckoExtensionInstallException(
    val failure: GeckoExtensionInstallFailure,
    cause: Throwable,
) : Exception(cause)

internal object GeckoExtensionInstallFailureRules {
    fun fromGeckoErrorCode(code: Int): GeckoExtensionInstallFailure = when (code) {
        WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
            GeckoExtensionInstallFailure.NetworkFailure
        WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
            GeckoExtensionInstallFailure.IncorrectHash
        WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE ->
            GeckoExtensionInstallFailure.CorruptFile
        WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS ->
            GeckoExtensionInstallFailure.FileAccess
        WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
            GeckoExtensionInstallFailure.Unsigned
        WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE ->
            GeckoExtensionInstallFailure.UnexpectedType
        WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_VERSION ->
            GeckoExtensionInstallFailure.UnexpectedVersion
        WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_ID ->
            GeckoExtensionInstallFailure.IncorrectId
        WebExtension.InstallException.ErrorCodes.ERROR_INVALID_DOMAIN ->
            GeckoExtensionInstallFailure.InvalidDomain
        WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
            GeckoExtensionInstallFailure.Blocklisted
        WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
            GeckoExtensionInstallFailure.Incompatible
        WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE ->
            GeckoExtensionInstallFailure.UnsupportedType
        WebExtension.InstallException.ErrorCodes.ERROR_ADMIN_INSTALL_ONLY ->
            GeckoExtensionInstallFailure.AdminOnly
        WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED ->
            GeckoExtensionInstallFailure.SoftBlocked
        WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ->
            GeckoExtensionInstallFailure.Cancelled
        WebExtension.InstallException.ErrorCodes.ERROR_POSTPONED ->
            GeckoExtensionInstallFailure.Postponed
        else -> GeckoExtensionInstallFailure.Unknown
    }

    fun managerMessage(error: Throwable): GeckoExtensionManagerMessage {
        val failure = (error as? GeckoExtensionInstallException)?.failure
            ?: return GeckoExtensionManagerMessage.ActionFailed
        return when (failure) {
            GeckoExtensionInstallFailure.NetworkFailure ->
                GeckoExtensionManagerMessage.InstallNetworkFailure
            GeckoExtensionInstallFailure.IncorrectHash ->
                GeckoExtensionManagerMessage.InstallIncorrectHash
            GeckoExtensionInstallFailure.CorruptFile ->
                GeckoExtensionManagerMessage.InstallCorruptFile
            GeckoExtensionInstallFailure.FileAccess ->
                GeckoExtensionManagerMessage.InstallFileAccess
            GeckoExtensionInstallFailure.Unsigned ->
                GeckoExtensionManagerMessage.InstallUnsigned
            GeckoExtensionInstallFailure.UnexpectedType ->
                GeckoExtensionManagerMessage.InstallUnexpectedType
            GeckoExtensionInstallFailure.UnexpectedVersion ->
                GeckoExtensionManagerMessage.InstallUnexpectedVersion
            GeckoExtensionInstallFailure.IncorrectId ->
                GeckoExtensionManagerMessage.InstallIncorrectId
            GeckoExtensionInstallFailure.InvalidDomain ->
                GeckoExtensionManagerMessage.InstallInvalidDomain
            GeckoExtensionInstallFailure.Blocklisted ->
                GeckoExtensionManagerMessage.InstallBlocklisted
            GeckoExtensionInstallFailure.Incompatible ->
                GeckoExtensionManagerMessage.InstallIncompatible
            GeckoExtensionInstallFailure.UnsupportedType ->
                GeckoExtensionManagerMessage.InstallUnsupportedType
            GeckoExtensionInstallFailure.AdminOnly ->
                GeckoExtensionManagerMessage.InstallAdminOnly
            GeckoExtensionInstallFailure.SoftBlocked ->
                GeckoExtensionManagerMessage.InstallSoftBlocked
            GeckoExtensionInstallFailure.Cancelled ->
                GeckoExtensionManagerMessage.InstallCancelled
            GeckoExtensionInstallFailure.Postponed ->
                GeckoExtensionManagerMessage.InstallPostponed
            GeckoExtensionInstallFailure.Unknown -> GeckoExtensionManagerMessage.ActionFailed
        }
    }
}
