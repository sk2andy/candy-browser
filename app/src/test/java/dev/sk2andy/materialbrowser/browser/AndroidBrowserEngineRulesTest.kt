package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBrowserEngineRulesTest {
    @Test
    fun `unknown stored engine falls back to GeckoView`() {
        assertEquals(
            AndroidBrowserEngineKind.GeckoView,
            AndroidBrowserEngineKind.fromStableId("future-engine"),
        )
    }

    @Test
    fun `system WebView exposes toppings without Firefox extensions`() {
        val capabilities = AndroidBrowserEngineRules.capabilities(
            AndroidBrowserEngineKind.SystemWebView,
        )

        assertTrue(capabilities.toppings)
        assertFalse(capabilities.firefoxExtensions)
        assertTrue(capabilities.nativeAutoplayPolicy)
        assertFalse(capabilities.insecureHttpPasswordManagerSelection)
    }

    @Test
    fun `only GeckoView supports insecure HTTP password manager selection`() {
        assertTrue(
            AndroidBrowserEngineRules.capabilities(AndroidBrowserEngineKind.GeckoView)
                .insecureHttpPasswordManagerSelection,
        )
        assertFalse(
            AndroidBrowserEngineRules.capabilities(AndroidBrowserEngineKind.SystemWebView)
                .insecureHttpPasswordManagerSelection,
        )
    }
}
