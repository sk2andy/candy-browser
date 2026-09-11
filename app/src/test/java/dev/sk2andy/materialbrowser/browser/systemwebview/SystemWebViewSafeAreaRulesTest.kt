package dev.sk2andy.materialbrowser.browser.systemwebview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWebViewSafeAreaRulesTest {
    @Test
    fun `full CSS safe area starts with WebView milestone 144`() {
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("143.0.7499.1"))
        assertTrue(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("144.0.7559.1"))
        assertTrue(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("145.0.7632.218"))
    }

    @Test
    fun `missing or malformed provider version keeps Candy compatibility enabled`() {
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets(null))
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("dev"))
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets(""))
    }
}
