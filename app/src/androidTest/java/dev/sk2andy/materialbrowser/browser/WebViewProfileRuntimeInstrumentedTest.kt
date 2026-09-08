package dev.sk2andy.materialbrowser.browser.systemwebview

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewProfileRuntimeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun defaultIsolatedAndIncognitoWebViewsUseDifferentProfiles() {
        assumeMultiProfileSupport()
        val isolatedName = WebViewProfileRules.isolatedProfileName(UUID.randomUUID().toString())
        val incognitoName = INCOGNITO_WEBVIEW_PROFILE_PREFIX + UUID.randomUUID()

        onMain {
            val defaultView = WebView(context)
            val isolatedView = WebView(context).also { WebViewCompat.setProfile(it, isolatedName) }
            val incognitoView = WebView(context).also { WebViewCompat.setProfile(it, incognitoName) }
            try {
                assertEquals(Profile.DEFAULT_PROFILE_NAME, WebViewCompat.getProfile(defaultView).name)
                assertEquals(isolatedName, WebViewCompat.getProfile(isolatedView).name)
                assertEquals(incognitoName, WebViewCompat.getProfile(incognitoView).name)
                assertNotEquals(
                    WebViewCompat.getProfile(isolatedView).name,
                    WebViewCompat.getProfile(incognitoView).name,
                )
            } finally {
                defaultView.destroy()
                isolatedView.destroy()
                incognitoView.destroy()
                deleteProfiles(isolatedName, incognitoName)
            }
        }
    }

    @Test
    fun cookiesDoNotCrossIsolatedOrIncognitoBoundary() {
        assumeMultiProfileSupport()
        val isolatedName = WebViewProfileRules.isolatedProfileName(UUID.randomUUID().toString())
        val incognitoName = INCOGNITO_WEBVIEW_PROFILE_PREFIX + UUID.randomUUID()
        val origin = "https://${UUID.randomUUID()}.example.test"

        onMain {
            val isolatedView = WebView(context).also { WebViewCompat.setProfile(it, isolatedName) }
            val incognitoView = WebView(context).also { WebViewCompat.setProfile(it, incognitoName) }
            val isolatedCookies = WebViewCompat.getProfile(isolatedView).cookieManager
            val incognitoCookies = WebViewCompat.getProfile(incognitoView).cookieManager
            val defaultCookies = CookieManager.getInstance()
            try {
                isolatedCookies.setCookie(origin, "candy_scope=isolated")
                incognitoCookies.setCookie(origin, "candy_scope=incognito")

                assertTrue(isolatedCookies.getCookie(origin).orEmpty().contains("candy_scope=isolated"))
                assertFalse(isolatedCookies.getCookie(origin).orEmpty().contains("candy_scope=incognito"))
                assertTrue(incognitoCookies.getCookie(origin).orEmpty().contains("candy_scope=incognito"))
                assertFalse(incognitoCookies.getCookie(origin).orEmpty().contains("candy_scope=isolated"))
                assertNull(defaultCookies.getCookie(origin))
            } finally {
                isolatedCookies.removeAllCookies(null)
                incognitoCookies.removeAllCookies(null)
                isolatedView.destroy()
                incognitoView.destroy()
                deleteProfiles(isolatedName, incognitoName)
            }
        }
    }

    private fun assumeMultiProfileSupport() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    private fun deleteProfiles(vararg profileNames: String) {
        profileNames.forEach { profileName ->
            runCatching { ProfileStore.getInstance().deleteProfile(profileName) }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }
}
