package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebContentTopInsetScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun reinjectionCancelsPendingFallbackConfirmation() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(TopInsetBridge(fallbackReceived))
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            """
                const blocker = document.createElement('style');
                blocker.textContent =
                  'html:root::before { height: 0 !important; min-height: 0 !important; }';
                document.head.appendChild(blocker);
            """.trimIndent(),
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        evaluate(
            view,
            """
                globalThis.__candyReconcileContentTopInset();
                globalThis.__candyReconcileContentTopInset();
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertFalse(
            "A pending check from the previous injection requested fallback",
            fallbackReceived.await(STALE_TIMER_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
        assertTrue(
            "The current injection never confirmed the persistent layout failure",
            fallbackReceived.await(FALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    private fun loadPage(bridge: TopInsetBridge): WebView {
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    addJavascriptInterface(bridge, WebContentTopInsetScript.bridgeName)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://safe-area.test/",
                        "<html><head></head><body><main>Content</main></body></html>",
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return createdView.get().also(webView::set)
    }

    private fun evaluate(view: WebView, script: String) {
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { evaluated.countDown() }
        }
        assertTrue(evaluated.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private class TopInsetBridge(
        private val fallbackReceived: CountDownLatch,
    ) {
        @JavascriptInterface
        fun topInsetPx(): Int = TOP_INSET_PX

        @JavascriptInterface
        fun navigationGeneration(): Int = NAVIGATION_GENERATION

        @JavascriptInterface
        fun policyRevision(): Long = POLICY_REVISION

        @JavascriptInterface
        fun fallbackToNative(generation: Int) {
            if (generation == NAVIGATION_GENERATION) fallbackReceived.countDown()
        }
    }

    private companion object {
        const val TOP_INSET_PX = 96
        const val NAVIGATION_GENERATION = 7
        const val POLICY_REVISION = 11L
        const val MUTATION_SETTLE_MILLIS = 100L
        const val STALE_TIMER_WINDOW_MILLIS = 600L
        const val PAGE_TIMEOUT_SECONDS = 5L
        const val FALLBACK_TIMEOUT_SECONDS = 3L
    }
}
