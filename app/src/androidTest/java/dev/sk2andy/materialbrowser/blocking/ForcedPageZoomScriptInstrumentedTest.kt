package dev.sk2andy.materialbrowser.blocking

import android.os.SystemClock
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForcedPageZoomScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun overridesInitialAndLaterViewportZoomLocks() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val view = loadPageWithZoomOverride()

        assertZoomEnabled(view)
        evaluate(
            view,
            """
                document.querySelector('meta[name="viewport"]').setAttribute(
                  'content',
                  'width = device-width; user-scalable = no; maximum-scale = 1; viewport-fit = cover'
                );
            """.trimIndent(),
        )
        assertTrue(awaitViewportContent(view).contains("viewport-fit=cover"))
        assertZoomEnabled(view)

        evaluate(view, ForcedPageZoomScript.cleanupScript)
        evaluate(
            view,
            "document.querySelector('meta[name=\"viewport\"]').content = 'user-scalable=no';",
        )
        assertTrue(viewportContent(view).contains("user-scalable=no"))
    }

    private fun loadPageWithZoomOverride(): WebView {
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        ForcedPageZoomScript.create(listOf("zoom.test")),
                        setOf("*"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://zoom.test/",
                        """
                            <html>
                              <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
                              </head>
                              <body></body>
                            </html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return createdView.get().also(webView::set)
    }

    private fun assertZoomEnabled(view: WebView) {
        val content = awaitViewportContent(view)
        assertTrue(content.contains("width=device-width"))
        assertTrue(content.contains("user-scalable=yes"))
        assertTrue(content.contains("maximum-scale=10"))
        assertFalse(content.contains("minimum-scale"))
        assertFalse(content.contains("user-scalable=no"))
    }

    private fun awaitViewportContent(view: WebView): String {
        val deadline = SystemClock.uptimeMillis() + RESULT_TIMEOUT_SECONDS * 1_000
        var content = viewportContent(view)
        while (
            SystemClock.uptimeMillis() < deadline &&
            (!content.contains("user-scalable=yes") || !content.contains("maximum-scale=10"))
        ) {
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
            content = viewportContent(view)
        }
        return content
    }

    private fun viewportContent(view: WebView): String =
        evaluate(view, "document.querySelector('meta[name=\"viewport\"]').content").orEmpty()

    private fun evaluate(view: WebView, script: String): String? {
        val result = AtomicReference<String?>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value?.removeSurrounding("\""))
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return result.get()
    }

    private companion object {
        const val RESULT_TIMEOUT_SECONDS = 10L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
