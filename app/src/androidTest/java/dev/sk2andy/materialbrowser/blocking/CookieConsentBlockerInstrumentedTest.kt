package dev.sk2andy.materialbrowser.blocking

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CookieConsentBlockerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun hidesInitialAndLateCookieBannersWithoutHidingUnrelatedContent() {
        val view = loadPage(
            """
                <!doctype html>
                <html><head></head><body style="overflow: hidden; overflow-y: hidden">
                  <div id="CybotCookiebotDialog">Cookie banner</div>
                  <div id="normal-modal">Normal content</div>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, ContentBlocker(instrumentation.targetContext).consentScript)

        val result = evaluate(
            view,
            """
                (() => {
                  const lateBanner = document.createElement('div');
                  lateBanner.className = 'qc-cmp2-container';
                  document.body.appendChild(lateBanner);
                  const injectedStyle = document.getElementById('material-browser-easylist-cookie-css');
                  return [
                    injectedStyle !== null,
                    injectedStyle && injectedStyle.sheet ? injectedStyle.sheet.cssRules.length : -1,
                    getComputedStyle(document.getElementById('CybotCookiebotDialog')).display,
                    getComputedStyle(lateBanner).display,
                    getComputedStyle(document.getElementById('normal-modal')).display,
                    document.body.style.overflow || 'cleared',
                    document.body.style.overflowY || 'cleared'
                  ].join('|');
                })();
            """.trimIndent(),
        )

        assertEquals("\"true|121|none|none|block|cleared|cleared\"", result)
    }

    @Test
    fun preservesScrollLockWhenNoKnownCookieBannerExists() {
        val view = loadPage(
            """
                <!doctype html>
                <html><head></head><body style="overflow: hidden">
                  <div id="normal-modal">Normal content</div>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, ContentBlocker(instrumentation.targetContext).consentScript)

        val result = evaluate(
            view,
            """
                [
                  document.body.style.overflow,
                  getComputedStyle(document.getElementById('normal-modal')).display
                ].join('|');
            """.trimIndent(),
        )

        assertEquals("\"hidden|block\"", result)
    }

    private fun loadPage(html: String): WebView {
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://example.test/",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(10, TimeUnit.SECONDS))
        return createdView.get().also(webView::set)
    }

    private fun evaluate(view: WebView, script: String): String {
        val result = AtomicReference<String>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }

        assertTrue(evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }
}
