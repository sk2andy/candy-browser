package dev.sk2andy.materialbrowser.browser.credentials

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
}
