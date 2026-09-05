package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopViewInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun toggleAppliesAndResetsDesktopWebViewSettings() {
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            val tabId = controller.createTab("https://mobile.example.test/page")
            val webView = controller.selectedWebViewForTesting()
            val defaultUserAgent = WebSettings.getDefaultUserAgent(activity)
            val defaultMetadata = if (
                WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
            ) {
                WebSettingsCompat.getUserAgentMetadata(webView.settings)
            } else {
                null
            }

            assertTrue(controller.setDesktopView(tabId, true))

            assertTrue(controller.isDesktopView(tabId))
            assertFalse(webView.settings.userAgentString.contains("Android", ignoreCase = true))
            assertFalse(webView.settings.userAgentString.contains("Mobile", ignoreCase = true))
            assertTrue(webView.settings.useWideViewPort)
            assertTrue(webView.settings.loadWithOverviewMode)
            if (defaultMetadata != null) {
                val desktopMetadata = WebSettingsCompat.getUserAgentMetadata(webView.settings)
                assertFalse(desktopMetadata.isMobile)
                assertEquals("Linux", desktopMetadata.platform)
                assertEquals("", desktopMetadata.platformVersion)
                assertEquals("x86", desktopMetadata.architecture)
                assertEquals("", desktopMetadata.model)
                assertEquals(64, desktopMetadata.bitness)
                if (
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.USER_AGENT_METADATA_FORM_FACTORS,
                    )
                ) {
                    assertEquals(
                        listOf(UserAgentMetadata.FORM_FACTOR_DESKTOP),
                        desktopMetadata.formFactors,
                    )
                }
            }

            assertTrue(controller.setDesktopView(tabId, false))

            assertFalse(controller.isDesktopView(tabId))
            assertEquals(defaultUserAgent, webView.settings.userAgentString)
            assertFalse(webView.settings.useWideViewPort)
            assertFalse(webView.settings.loadWithOverviewMode)
            if (defaultMetadata != null) {
                assertEquals(
                    defaultMetadata,
                    WebSettingsCompat.getUserAgentMetadata(webView.settings),
                )
            }
        }
    }

    @Test
    fun desktopViewOverridesMobileViewportAndRestoresPageDefault() {
        LocalPageServer().use { server ->
            lateinit var tabId: String
            lateinit var webView: WebView
            activityRule.scenario.onActivity { activity ->
                clearPreferences(activity)
                val controller = BrowserController(activity).also { this.controller = it }
                tabId = controller.createTab(server.regularUrl)
                val container = FrameLayout(activity)
                activity.setContentView(container)
                controller.attachSelectedWebView(container)
                webView = controller.selectedWebViewForTesting()
            }
            awaitLoadedUrl(tabId, server.regularUrl)
            awaitDocumentViewport(webView, expectsDesktop = false)
            awaitInitialViewport(webView, expectsDesktop = false)

            activityRule.scenario.onActivity {
                assertTrue(controller!!.setDesktopView(tabId, true))
            }
            awaitDocumentViewport(webView, expectsDesktop = true)
            awaitInitialViewport(webView, expectsDesktop = true)
            awaitViewportContent(
                webView,
                "width=980, viewport-fit=cover, user-scalable=yes, maximum-scale=10",
            )

            activityRule.scenario.onActivity {
                assertTrue(controller!!.setForcePageZooming(tabId, true))
            }
            awaitDocumentViewport(webView, expectsDesktop = true)
            awaitInitialViewport(webView, expectsDesktop = true)
            awaitViewportContent(
                webView,
                "width=980, viewport-fit=cover, user-scalable=yes, maximum-scale=10",
            )

            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "document.querySelector('meta[name=viewport]').content = " +
                        "'width=device-width, initial-scale=1, user-scalable=no'",
                    null,
                )
            }
            awaitDocumentViewport(webView, expectsDesktop = true)
            awaitViewportContent(
                webView,
                "width=980, user-scalable=yes, maximum-scale=10",
            )

            activityRule.scenario.onActivity {
                assertTrue(controller!!.setDesktopView(tabId, false))
            }
            awaitDocumentViewport(webView, expectsDesktop = false)
            awaitInitialViewport(webView, expectsDesktop = false)
        }
    }

    @Test
    fun regularPreferencePersistsWhilePrivatePreferenceDoesNot() {
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            var controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(controller.isProfileIsolationSupported)
            val regularTabId = controller.createTab("https://news.example.test/")
            val privateTabId = controller.createTab(
                initialUrl = "https://private.example.test/",
                isIncognito = true,
            )

            assertTrue(controller.setDesktopView(regularTabId, true))
            assertTrue(controller.setDesktopView(privateTabId, true))
            controller.destroy()

            controller = BrowserController(activity).also { this.controller = it }
            val restoredRegularTab = controller.activeTabs.first { tab ->
                tab.url == "https://news.example.test/"
            }
            val freshPrivateTabId = controller.createTab(
                initialUrl = "https://private.example.test/",
                isIncognito = true,
            )

            assertTrue(controller.isDesktopView(restoredRegularTab.id))
            assertFalse(controller.isDesktopView(freshPrivateTabId))
        }
    }

    @Test
    fun linkAndHistoryNavigationSurviveDesktopUserAgentChanges() {
        LocalPageServer().use { server ->
            lateinit var tabId: String
            lateinit var webView: WebView
            activityRule.scenario.onActivity { activity ->
                clearPreferences(activity)
                BrowserSessionStore(activity).saveDesktopViewDomains(
                    mapOf(DEFAULT_PROFILE_ID to setOf("desktop.localhost")),
                )
                val controller = BrowserController(activity).also { this.controller = it }
                tabId = controller.createTab(server.regularUrl)
                webView = controller.selectedWebViewForTesting()
            }
            awaitLoadedUrl(tabId, server.regularUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = true)

            activityRule.scenario.onActivity {
                webView.evaluateJavascript("document.getElementById('target').click()", null)
            }
            awaitLoadedUrl(tabId, server.desktopUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = false)
            activityRule.scenario.onActivity {
                assertTrue(controller!!.isDesktopView(tabId))
                assertFalse(webView.settings.userAgentString.contains("Android", ignoreCase = true))
            }

            awaitHistory(server.regularUrl, server.desktopUrl, currentIndex = 1)
            activityRule.scenario.onActivity {
                assertTrue(controller!!.selectedTab.canGoBack)
                controller!!.goBack()
            }
            awaitLoadedUrl(tabId, server.regularUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = true)
            activityRule.scenario.onActivity {
                assertFalse(controller!!.isDesktopView(tabId))
                assertTrue(webView.settings.userAgentString.contains("Android", ignoreCase = true))
                assertTrue(controller!!.selectedTab.canGoForward)
                controller!!.goForward()
            }
            awaitLoadedUrl(tabId, server.desktopUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = false)
            awaitHistory(server.regularUrl, server.desktopUrl, currentIndex = 1)

            activityRule.scenario.onActivity {
                assertTrue(controller!!.isDesktopView(tabId))
                assertFalse(webView.settings.userAgentString.contains("Android", ignoreCase = true))
                assertTrue(webView.settings.useWideViewPort)
                assertTrue(webView.settings.loadWithOverviewMode)
            }
        }
    }

    @Test
    fun desktopToggleAndRedirectedResultClickDoNotReturnToSearchPage() {
        LocalPageServer().use { server ->
            lateinit var tabId: String
            lateinit var webView: WebView
            activityRule.scenario.onActivity { activity ->
                clearPreferences(activity)
                val controller = BrowserController(activity).also { this.controller = it }
                tabId = controller.createTab(server.regularUrl)
                val container = FrameLayout(activity)
                activity.setContentView(container)
                controller.attachSelectedWebView(container)
                webView = controller.selectedWebViewForTesting()
            }
            awaitLoadedUrl(tabId, server.regularUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = true)

            tapRedirectTarget(webView)
            awaitLoadedUrl(tabId, server.desktopUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = true)
            activityRule.scenario.onActivity {
                assertTrue(controller!!.selectedWebViewForTesting() === webView)
            }
            awaitCanGoBack(webView)

            activityRule.scenario.onActivity {
                assertTrue(controller!!.setDesktopView(tabId, true))
            }
            awaitLoadedUrl(tabId, server.desktopUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = false)
            awaitHistory(server.regularUrl, server.desktopUrl, currentIndex = 1)
            awaitCanGoBack(webView)

            activityRule.scenario.onActivity {
                assertTrue(controller!!.selectedTab.canGoBack)
                controller!!.goBack()
            }
            awaitLoadedUrl(tabId, server.regularUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = true)

            tapRedirectTarget(webView)
            awaitLoadedUrl(tabId, server.desktopUrl)
            awaitDocumentUserAgent(webView, expectsAndroid = false)
            awaitHistory(server.regularUrl, server.desktopUrl, currentIndex = 1)
        }
    }

    private fun awaitCanGoBack(webView: WebView) {
        val deadline = System.currentTimeMillis() + 10_000L
        var latestHistory = ""
        while (System.currentTimeMillis() < deadline) {
            val canGoBack = AtomicReference(false)
            activityRule.scenario.onActivity {
                canGoBack.set(webView.canGoBack())
                val history = webView.copyBackForwardList()
                latestHistory =
                    List(history.size) { index -> history.getItemAtIndex(index).url.orEmpty() }
                        .toString() + "@${history.currentIndex}"
            }
            if (canGoBack.get()) return
            Thread.sleep(50L)
        }
        throw AssertionError("WebView did not report canGoBack; history=$latestHistory")
    }

    private fun tapRedirectTarget(webView: WebView) {
        val coordinates = AtomicReference<Pair<Float, Float>>()
        activityRule.scenario.onActivity {
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            coordinates.set(
                location[0] + TAP_COORDINATE to location[1] + TAP_COORDINATE,
            )
        }
        val (x, y) = coordinates.get()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                x,
                y,
                0,
            ).let { event ->
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitLoadedUrl(tabId: String, expectedUrl: String) {
        val deadline = System.currentTimeMillis() + 10_000L
        val actualUrl = AtomicReference<String?>()
        val actualHistory = AtomicReference<String>()
        while (System.currentTimeMillis() < deadline) {
            val loaded = AtomicReference(false)
            activityRule.scenario.onActivity {
                val currentController = controller ?: return@onActivity
                val tab = currentController.activeTabs.firstOrNull { candidate ->
                    candidate.id == tabId
                }
                val webView = currentController.selectedWebViewForTesting()
                actualUrl.set(webView.url)
                val history = webView.copyBackForwardList()
                actualHistory.set(
                    List(history.size) { index -> history.getItemAtIndex(index).url.orEmpty() }
                        .toString() + "@${history.currentIndex}",
                )
                loaded.set(tab?.url == expectedUrl && webView.url == expectedUrl && !tab.isLoading)
            }
            if (loaded.get()) return
            Thread.sleep(50L)
        }
        throw AssertionError(
            "WebView did not finish $expectedUrl; current=${actualUrl.get()}, " +
                "history=${actualHistory.get()}",
        )
    }

    private fun awaitHistory(regularUrl: String, desktopUrl: String, currentIndex: Int) {
        val expectedUrls = listOf(regularUrl, desktopUrl)
        val deadline = System.currentTimeMillis() + 10_000L
        var latestUrls = emptyList<String>()
        var latestIndex = -1
        while (System.currentTimeMillis() < deadline) {
            val matches = AtomicReference(false)
            activityRule.scenario.onActivity {
                val history = controller!!.selectedWebViewForTesting().copyBackForwardList()
                latestUrls = List(history.size) { index -> history.getItemAtIndex(index).url.orEmpty() }
                latestIndex = history.currentIndex
                matches.set(latestUrls == expectedUrls && latestIndex == currentIndex)
            }
            if (matches.get()) return
            Thread.sleep(50L)
        }
        throw AssertionError(
            "WebView history did not become $expectedUrls@$currentIndex; " +
                "current=$latestUrls@$latestIndex",
        )
    }

    private fun awaitDocumentUserAgent(webView: WebView, expectsAndroid: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        val actualUserAgent = AtomicReference<String?>()
        val expectedKind = if (expectsAndroid) "android" else "desktop"
        while (System.currentTimeMillis() < deadline) {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "(() => {" +
                        "const ua = document.getElementById('request-ua')?.content || '';" +
                        "return ua.length === 0 ? 'missing' : " +
                        "(ua.includes('Android') ? 'android' : 'desktop');" +
                        "})()",
                ) { value ->
                    actualUserAgent.set(value)
                }
            }
            val value = actualUserAgent.get()
            if (value == "\"$expectedKind\"") return
            Thread.sleep(50L)
        }
        throw AssertionError(
            "Document request user agent did not become $expectedKind; " +
                "current=${actualUserAgent.get()}",
        )
    }

    private fun awaitDocumentViewport(webView: WebView, expectsDesktop: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        val actualViewport = AtomicReference<String?>()
        val expectedKind = if (expectsDesktop) "desktop" else "mobile"
        while (System.currentTimeMillis() < deadline) {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "JSON.stringify({" +
                        "kind: window.innerWidth >= 900 ? 'desktop' : 'mobile'," +
                        "width: window.innerWidth," +
                        "viewport: document.querySelector('meta[name=viewport]')?.content || ''" +
                        "})",
                ) { value -> actualViewport.set(value) }
            }
            val result = actualViewport.get()
            val hasExpectedKind = result?.contains(
                "\\\"kind\\\":\\\"$expectedKind\\\"",
            ) == true
            val hasExpectedWidth = !expectsDesktop || result?.contains("\\\"width\\\":980") == true
            if (hasExpectedKind && hasExpectedWidth) {
                return
            }
            Thread.sleep(50L)
        }
        throw AssertionError(
            "Document viewport did not become $expectedKind; current=${actualViewport.get()}",
        )
    }

    private fun awaitViewportContent(webView: WebView, expectedContent: String) {
        val deadline = System.currentTimeMillis() + 10_000L
        val actualContent = AtomicReference<String?>()
        while (System.currentTimeMillis() < deadline) {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "document.querySelector('meta[name=viewport]')?.content || ''",
                ) { value -> actualContent.set(value) }
            }
            if (actualContent.get() == "\"$expectedContent\"") return
            Thread.sleep(50L)
        }
        throw AssertionError(
            "Document viewport content did not become $expectedContent; " +
                "current=${actualContent.get()}",
        )
    }

    private fun awaitInitialViewport(webView: WebView, expectsDesktop: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        val actualViewport = AtomicReference<String?>()
        val expectedKind = if (expectsDesktop) "desktop" else "mobile"
        while (System.currentTimeMillis() < deadline) {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "document.documentElement.dataset.initialLayout || 'missing'",
                ) { value -> actualViewport.set(value) }
            }
            if (actualViewport.get() == "\"$expectedKind\"") return
            Thread.sleep(50L)
        }
        throw AssertionError(
            "Initial document viewport did not become $expectedKind; " +
                "current=${actualViewport.get()}",
        )
    }

    private fun clearPreferences(activity: ComponentActivity) {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private companion object {
        const val TAP_COORDINATE = 150f
    }

    private class LocalPageServer : Closeable {
        private val serverSocket = ServerSocket(
            0,
            8,
            InetAddress.getByName("0.0.0.0"),
        )
        private val serverThread = Thread({ serve() }, "candy-desktop-view-test-server").apply {
            isDaemon = true
            start()
        }

        val regularUrl: String =
            "http://regular.localhost:${serverSocket.localPort}/regular.html"
        val desktopUrl: String =
            "http://desktop.localhost:${serverSocket.localPort}/desktop.html"
        private val redirectUrl: String =
            "http://regular.localhost:${serverSocket.localPort}/redirect"

        private fun body(userAgent: String): ByteArray = (
                "<!doctype html><html><head>" +
                    "<meta name=viewport content=\"width=device-width, initial-scale=1, " +
                    "maximum-scale=1, user-scalable=no, viewport-fit=cover\">" +
                    "<script>document.documentElement.dataset.initialLayout = " +
                    "innerWidth >= 900 ? 'desktop' : 'mobile';</script>" +
                    "</head><body>" +
                    "<a id=redirect-target href=\"$redirectUrl\" " +
                    "style=\"display:block;width:200px;height:200px\">Redirect target</a>" +
                    "<a id=target href=\"$desktopUrl\">Target</a>" +
                    "<meta id=request-ua content=\"${userAgent.htmlEscaped()}\">" +
                    "</body></html>"
                ).toByteArray(Charsets.UTF_8)

        private fun serve() {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    connection.soTimeout = 2_000
                    runCatching {
                        val input = connection.getInputStream()
                        val requestBytes = ByteArrayOutputStream()
                        var matchedHeaderBytes = 0
                        while (matchedHeaderBytes < HTTP_HEADER_END.size) {
                            val next = input.read()
                            if (next < 0) break
                            requestBytes.write(next)
                            matchedHeaderBytes = if (
                                next == HTTP_HEADER_END[matchedHeaderBytes].toInt()
                            ) {
                                matchedHeaderBytes + 1
                            } else {
                                0
                            }
                        }
                        connection.getOutputStream().use response@{ output ->
                            val requestHeaders = requestBytes.toString(Charsets.US_ASCII.name())
                            val requestPath = requestHeaders.lineSequence()
                                .firstOrNull()
                                ?.split(' ')
                                ?.getOrNull(1)
                            if (requestPath == "/redirect") {
                                output.write(
                                    (
                                        "HTTP/1.1 302 Found\r\n" +
                                            "Location: $desktopUrl\r\n" +
                                            "Content-Length: 0\r\n" +
                                            "Cache-Control: no-store\r\n" +
                                            "Connection: close\r\n\r\n"
                                        ).toByteArray(Charsets.US_ASCII),
                                )
                                output.flush()
                                return@response
                            }
                            val userAgent = requestHeaders.lineSequence()
                                .firstOrNull { line ->
                                    line.startsWith("User-Agent:", ignoreCase = true)
                                }
                                ?.substringAfter(':')
                                ?.trim()
                                .orEmpty()
                            val responseBody = body(userAgent)
                            val headers = (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${responseBody.size}\r\n" +
                                    "Cache-Control: no-store\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(Charsets.US_ASCII)
                            output.write(headers)
                            output.write(responseBody)
                            output.flush()
                        }
                    }
                }
            }
        }

        override fun close() {
            serverSocket.close()
            serverThread.join(2_000L)
        }

        private companion object {
            val HTTP_HEADER_END = byteArrayOf(13, 10, 13, 10)

            fun String.htmlEscaped(): String = replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }
    }
}
