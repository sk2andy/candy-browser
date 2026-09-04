package dev.sk2andy.materialbrowser.browser.credentials

import androidx.webkit.WebSettingsCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialFeaturePolicyTest {
    @Test
    fun `uses browser WebAuthn mode when provider supports feature`() {
        assertEquals(
            WebAuthenticationMode.BROWSER,
            CredentialFeaturePolicy.webAuthenticationMode(featureSupported = true),
        )
    }

    @Test
    fun `keeps safe provider default when WebAuthn feature is unavailable`() {
        assertEquals(
            WebAuthenticationMode.NONE,
            CredentialFeaturePolicy.webAuthenticationMode(featureSupported = false),
        )
    }

    @Test
    fun `reports browser support only when WebView retained requested mode`() {
        assertEquals(
            WebAuthenticationSupport.ENABLED_FOR_BROWSER,
            CredentialFeaturePolicy.configuredSupport(
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
            ),
        )
        listOf(
            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE,
            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP,
        ).forEach { appliedSupport ->
            assertEquals(
                WebAuthenticationSupport.CONFIGURATION_FAILED,
                CredentialFeaturePolicy.configuredSupport(appliedSupport),
            )
        }
    }
}
