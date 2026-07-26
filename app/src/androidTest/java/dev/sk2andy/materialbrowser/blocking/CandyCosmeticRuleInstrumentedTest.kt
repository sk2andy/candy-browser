package dev.sk2andy.materialbrowser.blocking

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyCosmeticRuleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view -> instrumentation.runOnMainSync { view.destroy() } }
    }

    @Test
    fun documentStartCssRunsOnAllowedOrigin() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val allowed = loadAndRead("https://news.example/", pausedHosts = emptySet())
        assertEquals("\"none|true\"", allowed)
    }

    @Test
    fun documentStartCssDoesNotRunOnLookalikeOrigin() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val lookalike = loadAndRead("https://notnews.example/", pausedHosts = emptySet())
        assertEquals("\"block|false\"", lookalike)
    }

    @Test
    fun documentStartCssHonorsPausedHostGuard() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val paused = loadAndRead("https://news.example/", pausedHosts = setOf("news.example"))
        assertEquals("\"block|false\"", paused)
    }

    private fun loadAndRead(baseUrl: String, pausedHosts: Set<String>): String? {
        val loaded = CountDownLatch(1)
        val created = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            created.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        CandyCosmeticScript.create(listOf(".sponsor"), pausedHosts),
                        setOf("https://news.example", "https://*.news.example"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
                    }
                    loadDataWithBaseURL(
                        baseUrl,
                        "<html><body><div class='sponsor'>ad</div></body></html>",
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(loaded.await(10, TimeUnit.SECONDS))
        val view = created.get().also(webView::set)
        val result = AtomicReference<String?>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(
                "[getComputedStyle(document.querySelector('.sponsor')).display," +
                    "document.querySelector('style[data-candy-filter]')!==null].join('|')",
            ) {
                result.set(it)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }
}
