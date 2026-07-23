package dev.sk2andy.materialbrowser.browser

import android.content.Intent
import android.widget.FrameLayout
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class CandyTrailWebViewInstrumentedTest {
    @Test
    fun pushStateCallbacksCreateDistinctPageNodes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val callbackLatch = AtomicReference<CountDownLatch?>()
        var trail: CandyTrail? = null
        var binding = CandyTrailHistoryBinding()
        lateinit var webView: WebView
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity

        instrumentation.runOnMainSync {
            webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        val history = view.copyBackForwardList()
                        if (history.currentIndex !in 0 until history.size) return
                        val result = CandyTrailHistoryReconciler.reconcile(
                            trail = trail,
                            tabId = TAB_ID,
                            previous = binding,
                            snapshot = CandyTrailHistorySnapshot(
                                urls = List(history.size) { index ->
                                    history.getItemAtIndex(index).url.orEmpty()
                                },
                                currentIndex = history.currentIndex,
                                isReload = isReload,
                            ),
                            title = url.orEmpty(),
                            visitedAt = System.nanoTime(),
                        )
                        trail = result.trail
                        binding = result.binding
                        callbackLatch.getAndSet(null)?.countDown()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse = WebResourceResponse(
                        "text/html",
                        "utf-8",
                        ByteArrayInputStream("<html><body>Candy Trail</body></html>".toByteArray()),
                    )
                }
            }
            activity.setContentView(webView)
        }

        try {
            awaitCallback(callbackLatch) {
                webView.loadUrl(ROOT)
            }
            awaitCallback(callbackLatch) {
                webView.evaluateJavascript("history.pushState({}, '', '/b')", null)
            }
            awaitCallback(callbackLatch) {
                webView.evaluateJavascript("history.pushState({}, '', '/c')", null)
            }

            val result = trail!!
            assertEquals(listOf(ROOT, "https://a.example/b", "https://a.example/c"),
                result.nodes.map { it.url })
            assertEquals(result.nodes[0].id, result.nodes[1].parentId)
            assertEquals(result.nodes[1].id, result.nodes[2].parentId)
        } finally {
            instrumentation.runOnMainSync {
                activity.setContentView(FrameLayout(activity))
                webView.destroy()
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }

    private fun awaitCallback(
        callbackLatch: AtomicReference<CountDownLatch?>,
        action: () -> Unit,
    ) {
        val latch = CountDownLatch(1)
        callbackLatch.set(latch)
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        assertTrue("WebView history callback timed out", latch.await(10, TimeUnit.SECONDS))
    }

    private companion object {
        const val TAB_ID = "00000000-0000-0000-0000-000000000001"
        const val ROOT = "https://a.example/root"
    }
}
