package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoNativeSessionBindingRulesTest {
    @Test
    fun `equivalent installed extension identity accepts new wrappers but rejects other sources`() {
        fun accepts(
            extensionId: String = CandyPrivacyHostContract.EXTENSION_ID,
            baseUrl: String = "moz-extension://host/",
            isCurrent: Boolean = true,
        ) = GeckoNativeSessionBindingRules.isInstalledExtensionSource(
            extensionId = extensionId,
            extensionBaseUrl = baseUrl,
            installedBaseUrl = "moz-extension://host/",
            hasCurrentInstalledExtension = isCurrent,
        )

        assertTrue(accepts())
        assertFalse(accepts(extensionId = "other-extension"))
        assertFalse(accepts(baseUrl = "moz-extension://other/"))
        assertFalse(accepts(isCurrent = false))
    }

    @Test
    fun `current authenticated session challenge accepts exact native tab and revision`() {
        assertEquals(GeckoNativeSessionBindingRequest(7, 2), request(URL))
    }

    @Test
    fun `wrong extension session token nonce and stale policy are rejected`() {
        assertNull(request(URL, hasInstalledExtension = false))
        assertNull(request(URL, hasExpectedSession = false))
        assertNull(request(URL, policyReady = false))
        assertNull(request(URL.replace("token=token", "token=other")))
        assertNull(request(URL.replace("challenge=nonce", "challenge=other")))
        assertNull(request(URL.replace("revision=2", "revision=1")))
        assertNull(request(URL.replace("moz-extension://host/", "moz-extension://other/")))
    }

    @Test
    fun `malformed tab revision and trailing parameters are rejected`() {
        listOf("0", "-1", "9007199254740992", "7.0", "7%26revision=2").forEach { tab ->
            assertNull(request(URL.replace("tab=7", "tab=$tab")))
        }
        assertNull(request("$URL&extra=true"))
        assertNull(request(null))
    }

    private fun request(
        url: String?,
        hasInstalledExtension: Boolean = true,
        hasExpectedSession: Boolean = true,
        policyReady: Boolean = true,
    ) = GeckoNativeSessionBindingRules.requestFor(
        url = url,
        extensionBaseUrl = "moz-extension://host/",
        token = "token",
        challenge = "nonce",
        currentRevision = 2,
        hasInstalledExtension = hasInstalledExtension,
        hasExpectedSession = hasExpectedSession,
        policyReady = policyReady,
    )

    private companion object {
        const val URL = "moz-extension://host/binding.html?token=token&challenge=nonce&tab=7&revision=2"
    }
}
