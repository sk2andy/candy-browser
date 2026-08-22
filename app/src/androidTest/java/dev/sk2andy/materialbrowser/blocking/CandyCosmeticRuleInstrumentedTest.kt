package dev.sk2andy.materialbrowser.blocking

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
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

    @Test
    fun documentStartCssRunsInMatchingFramesAndCleanupRemovesEveryStyle() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val loaded = CountDownLatch(1)
        val created = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            created.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        CandyCosmeticScript.create(listOf(".sponsor")),
                        setOf("https://news.example"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
                    }
                    loadDataWithBaseURL(
                        "https://news.example/",
                        """
                            <html><body>
                              <iframe id="frame" srcdoc="<div class='sponsor'>ad</div>"></iframe>
                            </body></html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        val view = created.get().also(webView::set)
        assertTrue(loaded.await(10, TimeUnit.SECONDS))

        assertEquals(
            "\"none|true\"",
            evaluate(
                view,
                "[getComputedStyle(document.getElementById('frame').contentDocument" +
                    ".querySelector('.sponsor')).display," +
                    "document.getElementById('frame').contentDocument" +
                    ".querySelector('style[data-candy-filter]')!==null].join('|')",
            ),
        )

        evaluate(view, CandyCosmeticScript.cleanupScript)

        assertEquals(
            "\"block|false\"",
            evaluate(
                view,
                "[getComputedStyle(document.getElementById('frame').contentDocument" +
                    ".querySelector('.sponsor')).display," +
                    "document.getElementById('frame').contentDocument" +
                    ".querySelector('style[data-candy-filter]')!==null].join('|')",
            ),
        )
    }

    @Test
    fun bundledGenericRuntimeHidesStaticAndDynamicAdsAndCleansUpIdempotently() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val blocker = ContentBlocker(instrumentation.targetContext)
        blocker.awaitCosmeticRulesForTesting()
        val result = loadGenericFixtureAndEvaluate(
            baseUrl = "https://generic.example/",
            html = """
                <html><body>
                  <div id="static" class="ad-space">ad</div>
                  <main id="organic">organic</main>
                </body></html>
            """.trimIndent(),
            payload = blocker.genericCosmeticPayload(),
            policy = blocker.genericCosmeticPolicyForHost("generic.example"),
            expected = "\"none|none|block\"",
            probe = """
                (() => {
                  let dynamic = document.getElementById('dynamic');
                  if (!dynamic) {
                    dynamic = document.createElement('div');
                    dynamic.id = 'dynamic';
                    dynamic.className = 'ad-unit';
                    document.body.appendChild(dynamic);
                  }
                  return [
                    getComputedStyle(document.getElementById('static')).display,
                    getComputedStyle(dynamic).display,
                    getComputedStyle(document.getElementById('organic')).display
                  ].join('|');
                })()
            """.trimIndent(),
        )

        val view = webView.get()
        val styleCount = evaluate(
            view,
            "String(document.querySelectorAll('style[data-candy-generic-filter]').length)",
        )
        evaluate(
            view,
            GenericCosmeticScript.create(bridgeToken = GENERIC_BRIDGE_TOKEN),
        )

        assertEquals("\"none|none|block\"", result)
        assertEquals(
            styleCount,
            evaluate(
                view,
                "String(document.querySelectorAll('style[data-candy-generic-filter]').length)",
            ),
        )

        evaluate(view, GenericCosmeticScript.cleanupScript)

        assertEquals(
            "\"block|block|0\"",
            evaluate(
                view,
                "[getComputedStyle(document.getElementById('static')).display," +
                    "getComputedStyle(document.getElementById('dynamic')).display," +
                    "document.querySelectorAll('style[data-candy-generic-filter]').length].join('|')",
            ),
        )
    }

    @Test
    fun bundledGenericRuntimeHonorsUpstreamExceptionAndGenericHide() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val blocker = ContentBlocker(instrumentation.targetContext)
        blocker.awaitCosmeticRulesForTesting()
        val excepted = loadGenericFixtureAndEvaluate(
            baseUrl = "https://1cloudfile.com/",
            html = "<div id='candidate' class='advert-wrapper'>organic download</div>",
            payload = blocker.genericCosmeticPayload(),
            policy = blocker.genericCosmeticPolicyForHost("1cloudfile.com"),
            expected = "\"block\"",
            probe = "getComputedStyle(document.getElementById('candidate')).display",
        )
        val disabled = loadGenericFixtureAndEvaluate(
            baseUrl = "https://adblockplus.org/",
            html = "<div id='candidate' class='ad-space'>documented example</div>",
            payload = blocker.genericCosmeticPayload(),
            policy = blocker.genericCosmeticPolicyForHost("adblockplus.org"),
            expected = "\"block\"",
            probe = "getComputedStyle(document.getElementById('candidate')).display",
        )

        assertEquals("\"block\"", excepted)
        assertEquals("\"block\"", disabled)
    }

    @Test
    fun documentStartCssSkipsSameOriginGrandchildBehindCrossOriginFrame() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val pages = mapOf(
            "news.example/top" to """
                <html><body>
                  <script>
                    window.frameDisplay = 'pending';
                    addEventListener('message', function(event) {
                      window.frameDisplay = event.data;
                    });
                  </script>
                  <iframe src="https://other.example/middle"></iframe>
                </body></html>
            """.trimIndent(),
            "other.example/middle" to
                "<iframe src=\"https://news.example/inner\"></iframe>",
            "news.example/inner" to """
                <div class="sponsor">ad</div>
                <script>
                  top.postMessage(
                    getComputedStyle(document.querySelector('.sponsor')).display,
                    '*'
                  );
                </script>
            """.trimIndent(),
        )
        val loaded = CountDownLatch(1)
        val created = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            created.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        CandyCosmeticScript.create(listOf(".sponsor")),
                        setOf("*"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val key = "${request.url.host}${request.url.path}"
                            val html = pages[key] ?: return null
                            return WebResourceResponse(
                                "text/html",
                                "utf-8",
                                ByteArrayInputStream(html.toByteArray()),
                            )
                        }

                        override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
                    }
                    loadUrl("https://news.example/top")
                },
            )
        }
        val view = created.get().also(webView::set)
        assertTrue(loaded.await(10, TimeUnit.SECONDS))

        assertEquals("\"block\"", awaitEvaluation(view, "window.frameDisplay", "\"pending\""))
    }

    @Test
    fun malformedSelectorDoesNotDisableFollowingValidSelector() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val rules = listOf(
            CandyRule.new(
                action = CandyRuleAction.Cosmetic,
                kind = CandyRuleKind.CosmeticCss,
                firstPartyHost = "news.example",
                cosmeticSelector = ".invalid[",
            ),
            CandyRule.new(
                action = CandyRuleAction.Cosmetic,
                kind = CandyRuleKind.CosmeticCss,
                firstPartyHost = "news.example",
                cosmeticSelector = ".sponsor",
            ),
        )
        val result = loadAndRead(
            baseUrl = "https://news.example/",
            pausedHosts = emptySet(),
            script = CandyCosmeticScript.createScoped(rules),
        )

        assertEquals("\"none|true\"", result)
    }

    @Test
    fun bundledGoogleRulesHideWholeStaticAndDynamicAdContainers() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://www.google.com/search?q=hotel",
            html = """
                <html><body>
                  <main id="organic">organic result</main>
                  <section id="tads" aria-label="Ads"><a>paid result</a></section>
                  <section id="google-s-ad">paid hotel</section>
                </body></html>
            """.trimIndent(),
            probe = """
                (() => {
                  const dynamic = document.createElement('div');
                  dynamic.id = 'dynamic-ad';
                  dynamic.dataset.isAd = '1';
                  document.body.appendChild(dynamic);
                  return [
                    getComputedStyle(document.getElementById('tads')).display,
                    getComputedStyle(document.getElementById('google-s-ad')).display,
                    getComputedStyle(dynamic).display,
                    getComputedStyle(document.getElementById('organic')).display
                  ].join('|');
                })()
            """.trimIndent(),
        )

        assertEquals("\"none|none|none|block\"", result)
    }

    @Test
    fun bundledRedditRulesHidePromotedCardsAndKeepOrganicPosts() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://www.reddit.com/r/popular/",
            html = """
                <html><body>
                  <shreddit-post id="organic">organic post</shreddit-post>
                  <shreddit-ad-post id="promoted">promoted post</shreddit-ad-post>
                  <div id="tracked" data-faceplate-tracking-context='{"promoted":true}'>ad</div>
                  <div id="advertisement" data-before-content="advertisement">ad</div>
                </body></html>
            """.trimIndent(),
            probe = """
                [
                  getComputedStyle(document.getElementById('promoted')).display,
                  getComputedStyle(document.getElementById('tracked')).display,
                  getComputedStyle(document.getElementById('advertisement')).display,
                  getComputedStyle(document.getElementById('organic')).display
                ].join('|')
            """.trimIndent(),
        )

        assertEquals("\"none|none|none|inline\"", result)
    }

    @Test
    fun bundledAmazonWildcardRulesHideStaticAndDynamicSponsoredResults() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://www.amazon.de/s?k=laptop",
            html = """
                <html><body>
                  <div id="organic" class="s-result-item">organic product</div>
                  <div id="sponsored" class="s-result-item">
                    <span class="puis-sponsored-label-text">Sponsored</span>
                  </div>
                  <div id="featured" cel_widget_id="MAIN-FEATURED_ASINS_LIST-8">Sponsored</div>
                </body></html>
            """.trimIndent(),
            probe = """
                (() => {
                  const dynamic = document.createElement('div');
                  dynamic.id = 'dynamic-sponsored';
                  dynamic.className = 's-result-item';
                  dynamic.innerHTML = '<span class="puis-sponsored-label-text">Sponsored</span>';
                  document.body.appendChild(dynamic);
                  return [
                    getComputedStyle(document.getElementById('sponsored')).display,
                    getComputedStyle(document.getElementById('featured')).display,
                    getComputedStyle(dynamic).display,
                    getComputedStyle(document.getElementById('organic')).display
                  ].join('|');
                })()
            """.trimIndent(),
        )

        assertEquals("\"none|none|none|block\"", result)
    }

    @Test
    fun bundledInteriaRulesHideAdSlotsAndSponsoredCards() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://www.interia.pl/",
            html = """
                <html><body>
                  <li id="organic" class="news-li tile wide">
                    <a class="tile-a"><span class="tile-label">organic story</span></a>
                  </li>
                  <div id="slot" class="ad common-ad">REKLAMA</div>
                  <li id="sponsored" class="news-li tile wide">
                    <a class="tile-a"><span class="tile-label span-sponsored">ARTYKUŁ SPONSOROWANY</span></a>
                  </li>
                </body></html>
            """.trimIndent(),
            probe = """
                [
                  getComputedStyle(document.getElementById('slot')).display,
                  getComputedStyle(document.getElementById('sponsored')).display,
                  getComputedStyle(document.getElementById('organic')).display
                ].join('|')
            """.trimIndent(),
        )

        assertEquals("\"none|none|list-item\"", result)
    }

    @Test
    fun bundledCorriereRulesHideAdAndSponsoredContentCards() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://www.corriere.it/",
            html = """
                <html><body>
                  <article id="organic">organic story</article>
                  <div id="slot" class="card card--adv">PUBBLICITÀ</div>
                  <section id="sponsored" class="contenuto-sponsorizzato">CONTENUTO SPONSORIZZATO</section>
                </body></html>
            """.trimIndent(),
            probe = """
                [
                  getComputedStyle(document.getElementById('slot')).display,
                  getComputedStyle(document.getElementById('sponsored')).display,
                  getComputedStyle(document.getElementById('organic')).display
                ].join('|')
            """.trimIndent(),
        )

        assertEquals("\"none|none|block\"", result)
    }

    @Test
    fun bundledNaverRuleHidesLabelledAdFrames() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://m.naver.com/",
            html = """
                <html><body>
                  <article id="organic">organic story</article>
                  <iframe id="ad" title="AD"></iframe>
                </body></html>
            """.trimIndent(),
            probe = """
                [
                  getComputedStyle(document.getElementById('ad')).display,
                  getComputedStyle(document.getElementById('organic')).display
                ].join('|')
            """.trimIndent(),
        )

        assertEquals("\"none|block\"", result)
    }

    @Test
    fun bundledCoupangRuleHidesPersonalizedAdsAndKeepsOrganicCarousel() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://www.coupang.com/",
            html = """
                <html><body>
                  <div id="organic" class="carousel-widget-container organic_products">
                    organic product
                  </div>
                  <div id="personalized" class="carousel-widget-container personalized_ads">
                    광고
                  </div>
                </body></html>
            """.trimIndent(),
            probe = """
                [
                  getComputedStyle(document.getElementById('personalized')).display,
                  getComputedStyle(document.getElementById('organic')).display
                ].join('|')
            """.trimIndent(),
        )

        assertEquals("\"none|block\"", result)
    }

    @Test
    fun bundledUolRuleHidesPublicidadeCards() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://www.uol.com.br/",
            html = """
                <html><body>
                  <article id="organic">organic story</article>
                  <div id="ad" class="section__item cardAd cardAd--showLabel--true"
                       aria-label="Publicidade"></div>
                </body></html>
            """.trimIndent(),
            probe = """
                [
                  getComputedStyle(document.getElementById('ad')).display,
                  getComputedStyle(document.getElementById('organic')).display
                ].join('|')
            """.trimIndent(),
        )

        assertEquals("\"none|block\"", result)
    }

    @Test
    fun curatedAdRulesHideStrongMediaAndAdjacentFallbackWithoutBroadFalsePositives() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val result = loadFixtureAndEvaluate(
            baseUrl = "https://publisher.example/",
            html = """
                <html><body>
                  <img id="compound" src="/banners/pr_advertising_ads_banner.gif">
                  <img id="token" src="/images/ads_banner.png">
                  <img id="advertising" src="/images/advertising-guide.png">
                  <img id="banner" src="/images/header-banner.png">
                  <img id="example" src="/docs/no_ads_banner_example.png">
                  <iframe id="guidelines" src="/docs/advertising/banner-guidelines.html"></iframe>
                  <div id="ad_banner"></div>
                  <div id="AlternateMessage"><a href="https://shop.example/product">fallback</a></div>
                  <div id="alternate-message-guide">alternate content guide</div>
                </body></html>
            """.trimIndent(),
            probe = """
                [
                  getComputedStyle(document.getElementById('compound')).display,
                  getComputedStyle(document.getElementById('token')).display,
                  getComputedStyle(document.getElementById('advertising')).display,
                  getComputedStyle(document.getElementById('banner')).display,
                  getComputedStyle(document.getElementById('example')).display,
                  getComputedStyle(document.getElementById('guidelines')).display,
                  getComputedStyle(document.getElementById('AlternateMessage')).display,
                  getComputedStyle(document.getElementById('alternate-message-guide')).display
                ].join('|')
            """.trimIndent(),
        )

        assertEquals("\"none|none|inline|inline|inline|inline|none|block\"", result)
    }

    private fun loadAndRead(
        baseUrl: String,
        pausedHosts: Set<String>,
        script: String = CandyCosmeticScript.create(listOf(".sponsor"), pausedHosts),
    ): String? {
        val loaded = CountDownLatch(1)
        val created = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            created.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        script,
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

    private fun loadFixtureAndEvaluate(baseUrl: String, html: String, probe: String): String? {
        val loaded = CountDownLatch(1)
        val created = AtomicReference<WebView>()
        val blocker = ContentBlocker(instrumentation.targetContext)
        blocker.awaitCosmeticRulesForTesting()
        val script = blocker.adCosmeticDocumentStartScript(baseUrl)
        instrumentation.runOnMainSync {
            created.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*"))
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
                    }
                    loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                },
            )
        }
        assertTrue(loaded.await(10, TimeUnit.SECONDS))
        val view = created.get().also(webView::set)
        val result = AtomicReference<String?>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(probe) {
                result.set(it)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun loadGenericFixtureAndEvaluate(
        baseUrl: String,
        html: String,
        payload: String,
        policy: String,
        expected: String,
        probe: String,
    ): String? {
        webView.getAndSet(null)?.let { previous ->
            instrumentation.runOnMainSync { previous.destroy() }
        }
        val loaded = CountDownLatch(1)
        val created = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            created.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    addJavascriptInterface(
                        GenericFixtureBridge(
                            token = GENERIC_BRIDGE_TOKEN,
                            payload = payload,
                            policy = policy,
                        ),
                        GenericCosmeticScript.BRIDGE_NAME,
                    )
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        GenericCosmeticScript.create(bridgeToken = GENERIC_BRIDGE_TOKEN),
                        setOf("*"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
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
        assertTrue(loaded.await(10, TimeUnit.SECONDS))
        val view = created.get().also(webView::set)
        return awaitExpected(view, probe, expected)
    }

    private class GenericFixtureBridge(
        private val token: String,
        private val payload: String,
        private val policy: String,
    ) {
        @JavascriptInterface
        fun payload(candidateToken: String): String =
            payload.takeIf { candidateToken == token }.orEmpty()

        @JavascriptInterface
        fun policy(candidateToken: String, rawHost: String): String =
            policy.takeIf { candidateToken == token && rawHost.isNotBlank() }.orEmpty()
    }

    private fun awaitEvaluation(view: WebView, script: String, pending: String): String? {
        repeat(50) {
            val result = evaluate(view, script)
            if (result != pending) return result
            Thread.sleep(100)
        }
        return evaluate(view, script)
    }

    private fun awaitExpected(view: WebView, script: String, expected: String): String? {
        repeat(50) {
            val result = evaluate(view, script)
            if (result == expected) return result
            Thread.sleep(100)
        }
        return evaluate(view, script)
    }

    private fun evaluate(view: WebView, script: String): String? {
        val result = AtomicReference<String?>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) {
                result.set(it)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private companion object {
        const val GENERIC_BRIDGE_TOKEN = "01234567-89ab-cdef-0123-456789abcdef"
    }
}
