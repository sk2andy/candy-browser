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
    fun staticCssHidesInitialAndLateBannersButPreservesPageScrollState() {
        val view = loadPage(
            """
                <!doctype html>
                <html><head></head><body style="overflow: hidden; overflow-y: hidden">
                  <div id="CybotCookiebotDialog">Cookie banner</div>
                  <div id="BorlabsCookieBox">Borlabs banner</div>
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
                  lateBanner.id = 'didomi-host';
                  document.body.appendChild(lateBanner);
                  return [
                    document.getElementById('material-browser-easylist-cookie-css') !== null,
                    getComputedStyle(document.getElementById('CybotCookiebotDialog')).display,
                    getComputedStyle(document.getElementById('BorlabsCookieBox')).display,
                    getComputedStyle(lateBanner).display,
                    getComputedStyle(document.getElementById('normal-modal')).display,
                    document.body.style.overflow,
                    document.body.style.overflowY
                  ].join('|');
                })();
            """.trimIndent(),
        )

        assertEquals("\"true|none|none|none|block|hidden|hidden\"", result)
    }

    @Test
    fun cookieCssCanBeRemovedWithoutChangingOtherPageStyles() {
        val view = loadPage(
            """
                <!doctype html><html><head></head><body style="overflow:hidden">
                  <div id="CybotCookiebotDialog">Cookie banner</div>
                </body></html>
            """.trimIndent(),
        )
        val blocker = ContentBlocker(instrumentation.targetContext)
        evaluate(view, blocker.consentScript)
        evaluate(view, blocker.consentRemovalScript)

        assertEquals(
            "\"true|block|hidden\"",
            evaluate(
                view,
                "[document.getElementById('material-browser-easylist-cookie-css') === null, " +
                    "getComputedStyle(document.getElementById('CybotCookiebotDialog')).display, " +
                    "document.body.style.overflow].join('|')",
            ),
        )
    }

    @Test
    fun cookieCssIsInjectedDirectlyAtPageCommitVisible() {
        val blocker = ContentBlocker(instrumentation.targetContext)
        val injected = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageCommitVisible(view: WebView, url: String) {
                            view.evaluateJavascript(blocker.consentScript) { injected.countDown() }
                        }
                    }
                    loadDataWithBaseURL(
                        "https://example.test/",
                        "<html><body><div id='CybotCookiebotDialog'>Banner</div></body></html>",
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(injected.await(10, TimeUnit.SECONDS))
        val view = createdView.get().also(webView::set)

        assertEquals(
            "\"true|none\"",
            evaluate(
                view,
                "[document.getElementById('material-browser-easylist-cookie-css') !== null, " +
                    "getComputedStyle(document.getElementById('CybotCookiebotDialog')).display]" +
                    ".join('|')",
            ),
        )
    }

    @Test
    fun siteCssHidesKnownCrossOriginConsentFrameWithoutFrameJavascript() {
        val view = loadPage(
            "<html><body><iframe id='consent'></iframe><iframe id='content'></iframe></body></html>",
            baseUrl = "https://web.de/",
        )
        evaluate(view, ContentBlocker(instrumentation.targetContext).consentScript)

        assertEquals(
            "\"none|inline\"",
            evaluate(
                view,
                """
                    (() => {
                      document.getElementById('consent').setAttribute(
                        'src',
                        'https://plus.web.de/consent'
                      );
                      return [
                        getComputedStyle(document.getElementById('consent')).display,
                        getComputedStyle(document.getElementById('content')).display
                      ].join('|');
                    })()
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun forcedVerticalScrollOverridesLateInlineLocksOnExactHost() {
        val view = loadPage(
            """
                <!doctype html>
                <html class="page-scroll-lock"><head><style>
                  html.page-scroll-lock { height: 100vh !important }
                  html.page-scroll-lock body {
                    overflow-y: hidden !important;
                    position: fixed !important;
                    top: 0 !important;
                    width: 100% !important;
                  }
                  main { height: 2400px }
                </style></head><body><main>Article</main></body></html>
            """.trimIndent(),
        )
        evaluate(view, ForcedVerticalScrollScript.create(listOf("example.test")))

        assertEquals(
            "\"auto|static|important|true\"",
            evaluate(
                view,
                """
                    [
                      getComputedStyle(document.body).overflowY,
                      getComputedStyle(document.body).position,
                      document.body.style.getPropertyPriority('overflow-y'),
                      parseFloat(getComputedStyle(document.body).height) > 2000
                    ].join('|')
                """.trimIndent(),
            ),
        )

        evaluate(
            view,
            """
                document.body.style.setProperty('overflow-y', 'hidden', 'important');
                document.body.style.setProperty('position', 'fixed', 'important');
            """.trimIndent(),
        )
        assertEquals(
            "\"auto|static\"",
            evaluate(
                view,
                "[getComputedStyle(document.body).overflowY, " +
                    "getComputedStyle(document.body).position].join('|')",
            ),
        )

        evaluate(
            view,
            """
                (() => {
                  const replacement = document.createElement('body');
                  replacement.innerHTML = '<main style="height:2400px">Replacement</main>';
                  replacement.style.setProperty('overflow-y', 'hidden', 'important');
                  replacement.style.setProperty('position', 'fixed', 'important');
                  document.documentElement.replaceChild(replacement, document.body);
                })();
            """.trimIndent(),
        )
        assertEquals(
            "\"auto|static\"",
            evaluate(
                view,
                "[getComputedStyle(document.body).overflowY, " +
                    "getComputedStyle(document.body).position].join('|')",
            ),
        )
    }

    @Test
    fun forcedVerticalScrollDoesNotAffectDifferentHost() {
        val view = loadPage(
            """
                <!doctype html><html><head></head>
                <body style="overflow-y:hidden;position:fixed"><main>Modal</main></body></html>
            """.trimIndent(),
        )
        evaluate(view, ForcedVerticalScrollScript.create(listOf("other.test")))

        assertEquals(
            "\"hidden|fixed\"",
            evaluate(
                view,
                "[getComputedStyle(document.body).overflowY, " +
                    "getComputedStyle(document.body).position].join('|')",
            ),
        )
    }

    private fun loadPage(html: String, baseUrl: String = "https://example.test/"): WebView {
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
                        baseUrl,
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
