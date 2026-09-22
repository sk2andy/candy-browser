package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.geckoview.WebExtension

class GeckoExtensionInstallFailureRulesTest {
    @Test
    fun `every Gecko install error keeps its actionable failure category`() {
        val expected = mapOf(
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE to
                GeckoExtensionInstallFailure.NetworkFailure,
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH to
                GeckoExtensionInstallFailure.IncorrectHash,
            WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE to
                GeckoExtensionInstallFailure.CorruptFile,
            WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS to
                GeckoExtensionInstallFailure.FileAccess,
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED to
                GeckoExtensionInstallFailure.Unsigned,
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE to
                GeckoExtensionInstallFailure.UnexpectedType,
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_VERSION to
                GeckoExtensionInstallFailure.UnexpectedVersion,
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_ID to
                GeckoExtensionInstallFailure.IncorrectId,
            WebExtension.InstallException.ErrorCodes.ERROR_INVALID_DOMAIN to
                GeckoExtensionInstallFailure.InvalidDomain,
            WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED to
                GeckoExtensionInstallFailure.Blocklisted,
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE to
                GeckoExtensionInstallFailure.Incompatible,
            WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE to
                GeckoExtensionInstallFailure.UnsupportedType,
            WebExtension.InstallException.ErrorCodes.ERROR_ADMIN_INSTALL_ONLY to
                GeckoExtensionInstallFailure.AdminOnly,
            WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED to
                GeckoExtensionInstallFailure.SoftBlocked,
            WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED to
                GeckoExtensionInstallFailure.Cancelled,
            WebExtension.InstallException.ErrorCodes.ERROR_POSTPONED to
                GeckoExtensionInstallFailure.Postponed,
        )

        expected.forEach { (errorCode, failure) ->
            assertEquals(
                failure,
                GeckoExtensionInstallFailureRules.fromGeckoErrorCode(errorCode),
            )
        }
    }

    @Test
    fun `unknown Gecko install error keeps generic fallback`() {
        assertEquals(
            GeckoExtensionInstallFailure.Unknown,
            GeckoExtensionInstallFailureRules.fromGeckoErrorCode(Int.MIN_VALUE),
        )
        assertEquals(
            GeckoExtensionManagerMessage.ActionFailed,
            GeckoExtensionInstallFailureRules.managerMessage(
                GeckoExtensionInstallException(
                    failure = GeckoExtensionInstallFailure.Unknown,
                    cause = IllegalStateException("Unknown Gecko install failure"),
                ),
            ),
        )
        assertEquals(
            GeckoExtensionManagerMessage.ActionFailed,
            GeckoExtensionInstallFailureRules.managerMessage(
                IllegalStateException("Unrelated runtime failure"),
            ),
        )
    }
}
