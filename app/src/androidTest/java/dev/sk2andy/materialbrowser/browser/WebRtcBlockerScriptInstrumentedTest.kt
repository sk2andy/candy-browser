package dev.sk2andy.materialbrowser.browser

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebRtcBlockerScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun blocksPeerConnectionsBeforePageScriptsInTopPageAndFrame() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        WebRtcBlockerScript.installScript,
                        setOf("*"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://webrtc.test/",
                        """
                            <html><body>
                              <script>
                                const tryPeerConnection = () => {
                                  try { new RTCPeerConnection(); return "allowed"; }
                                  catch (error) { return error.name; }
                                };
                                window.topResult = tryPeerConnection();
                                window.RTCPeerConnection = function() {};
                                window.overwriteResult = tryPeerConnection();
                              </script>
                              <iframe srcdoc='<html><body>frame</body></html>'></iframe>
                            </body></html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val view = createdView.get().also(webView::set)

        assertEquals("NotAllowedError", evaluate(view, "window.topResult"))
        assertEquals("NotAllowedError", evaluate(view, "window.overwriteResult"))
        assertEquals(
            "NotAllowedError",
            awaitJavaScriptValue(
                view,
                """
                    (() => {
                      const frame = document.querySelector('iframe');
                      if (!frame?.contentWindow) return null;
                      try { new frame.contentWindow.RTCPeerConnection(); return "allowed"; }
                      catch (error) { return error.name; }
                    })()
                """.trimIndent(),
            ),
        )
    }

    private fun awaitJavaScriptValue(view: WebView, script: String): String? {
        val deadline = SystemClock.uptimeMillis() + RESULT_TIMEOUT_SECONDS * 1_000
        while (SystemClock.uptimeMillis() < deadline) {
            val value = evaluate(view, script)
            if (value != null && value != "null") return value
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return null
    }

    private fun evaluate(view: WebView, script: String): String? {
        val result = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value?.removeSurrounding("\""))
                latch.countDown()
            }
        }
        assertTrue(latch.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return result.get()
    }

    private companion object {
        const val RESULT_TIMEOUT_SECONDS = 5L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
