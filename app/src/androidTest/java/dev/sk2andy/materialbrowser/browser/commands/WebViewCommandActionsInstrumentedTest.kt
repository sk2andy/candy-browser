package dev.sk2andy.materialbrowser.browser.commands

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewCommandActionsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val cookieManager = CookieManager.getInstance()

    @After
    fun clearCookies() {
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            cookieManager.removeAllCookies { latch.countDown() }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        cookieManager.flush()
    }

    @Test
    fun clearCacheIncludesDiskFilesBeforeReload() {
        lateinit var webView: RecordingWebView

        instrumentation.runOnMainSync {
            webView = RecordingWebView(instrumentation.targetContext)
            WebViewCommandActions.clearCacheAndReload(webView)
        }

        assertEquals(listOf("cache:true", "reload"), webView.events)
        instrumentation.runOnMainSync(webView::destroy)
    }

    @Test
    fun clearCookiesFlushesProfileManagerAndReloadsWebView() {
        val url = "https://commands.example/"
        val cookieSet = CountDownLatch(1)
        val completed = CountDownLatch(1)
        lateinit var webView: RecordingWebView
        instrumentation.runOnMainSync {
            webView = RecordingWebView(instrumentation.targetContext)
            cookieManager.setAcceptCookie(true)
            cookieManager.setCookie(url, "command_test=present") { cookieSet.countDown() }
        }
        assertTrue(cookieSet.await(5, TimeUnit.SECONDS))
        assertTrue(cookieManager.getCookie(url).contains("command_test=present"))

        instrumentation.runOnMainSync {
            WebViewCommandActions.clearCookiesAndReload(
                cookieManager = cookieManager,
                webView = webView,
                onComplete = completed::countDown,
            )
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertNull(cookieManager.getCookie(url))
        assertEquals(listOf("reload"), webView.events)
        instrumentation.runOnMainSync(webView::destroy)
    }

    @Test
    fun resolverClearsOnlySelectedWebViewProfileCookies() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val profileName = "command-test-${UUID.randomUUID()}"
        val url = "https://profile-commands.example/"
        val defaultCookieSet = CountDownLatch(1)
        val privateCookieSet = CountDownLatch(1)
        val completed = CountDownLatch(1)
        var defaultWebView: WebView? = null
        var privateWebView: WebView? = null
        lateinit var defaultManager: CookieManager
        lateinit var privateManager: CookieManager
        try {
            instrumentation.runOnMainSync {
                defaultWebView = WebView(instrumentation.targetContext)
                privateWebView = WebView(instrumentation.targetContext)
                WebViewCompat.setProfile(checkNotNull(privateWebView), profileName)
                defaultManager = checkNotNull(
                    WebViewProfileCookies.managerFor(checkNotNull(defaultWebView)),
                )
                privateManager = checkNotNull(
                    WebViewProfileCookies.managerFor(checkNotNull(privateWebView)),
                )
                defaultManager.setCookie(url, "scope=default") { defaultCookieSet.countDown() }
                privateManager.setCookie(url, "scope=private") { privateCookieSet.countDown() }
            }
            assertTrue(defaultCookieSet.await(5, TimeUnit.SECONDS))
            assertTrue(privateCookieSet.await(5, TimeUnit.SECONDS))
            assertTrue(defaultManager.getCookie(url).contains("scope=default"))
            assertTrue(privateManager.getCookie(url).contains("scope=private"))

            instrumentation.runOnMainSync {
                WebViewCommandActions.clearCookiesAndReload(
                    cookieManager = privateManager,
                    webView = checkNotNull(privateWebView),
                    shouldReload = { false },
                    onComplete = completed::countDown,
                )
            }

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertNull(privateManager.getCookie(url))
            assertTrue(defaultManager.getCookie(url).contains("scope=default"))
        } finally {
            instrumentation.runOnMainSync {
                defaultWebView?.destroy()
                privateWebView?.destroy()
                runCatching { ProfileStore.getInstance().deleteProfile(profileName) }
            }
        }
    }

    private class RecordingWebView(context: Context) : WebView(context) {
        val events = mutableListOf<String>()

        override fun clearCache(includeDiskFiles: Boolean) {
            events += "cache:$includeDiskFiles"
        }

        override fun reload() {
            events += "reload"
        }
    }
}
