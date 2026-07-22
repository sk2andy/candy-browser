package dev.sk2andy.materialbrowser.blocking

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyPolicyInstrumentedTest {
    @Test
    fun appliesPausedSiteDecisionToWebViewThirdPartyCookiePolicy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            val cookies = CookieManager.getInstance()
            try {
                cookies.setAcceptThirdPartyCookies(
                    webView,
                    PrivacyPolicyRules.acceptsThirdPartyCookies(
                        blockThirdPartyCookies = true,
                        sitePaused = false,
                    ),
                )
                assertFalse(cookies.acceptThirdPartyCookies(webView))

                cookies.setAcceptThirdPartyCookies(
                    webView,
                    PrivacyPolicyRules.acceptsThirdPartyCookies(
                        blockThirdPartyCookies = true,
                        sitePaused = true,
                    ),
                )
                assertTrue(cookies.acceptThirdPartyCookies(webView))
            } finally {
                webView.destroy()
            }
        }
    }
}
