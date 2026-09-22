package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.webkit.WebView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserViewportRect
import dev.sk2andy.materialbrowser.browser.PageTranslationContentOutcome
import dev.sk2andy.materialbrowser.browser.PageTranslationRecoveryRules
import dev.sk2andy.materialbrowser.browser.PrivacySignalSettings
import dev.sk2andy.materialbrowser.browser.TextInputOcclusionProbeMode
import dev.sk2andy.materialbrowser.browser.TextInputOcclusionProbeResult
import dev.sk2andy.materialbrowser.browser.TextInputOcclusionScript
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.reader.ReaderExtractionScript
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewBrowserEngineInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @Before
    fun setUp() {
        composeRule.activity
            .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(
                BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
                AndroidBrowserEngineKind.SystemWebView.stableId,
            )
            .commit()
        GeckoRuntimeOwner.resetForTesting()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            composeRule.activity
                .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        GeckoRuntimeOwner.resetForTesting()
    }

    @Test
    fun selectedTabUsesSystemWebViewWithoutStartingGecko() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val browserController = BrowserController(activity)
            controller = browserController
            val host = FrameLayout(activity)
            activity.addContentView(
                host,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )

            val engineView = browserController.attachSelectedBrowserEngineView(host)

            assertFalse(browserController.usesGeckoEngine)
            assertNotNull(engineView)
            assertTrue(engineView.containsWebView())
            assertFalse(GeckoRuntimeOwner.hasRuntimeForTesting())
        }
    }

    @Test
    fun edgeToEdgeKeepsRendererAtTopAndPublishesSafeAreaInset() {
        lateinit var webView: WebView
        composeRule.runOnIdle {
            val (browserController, createdWebView) = createControllerWithView()
            controller = browserController
            webView = createdWebView
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout(),
                        Insets.of(0, EXPECTED_TOP_INSET, 0, 0),
                    )
                    .build(),
            )
            webView.loadDataWithBaseURL(
                "https://safe-area.test/",
                "<html><body>safe area</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            assertEquals(0, (webView.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
            webView.evaluateJavascript(
                "${dev.sk2andy.materialbrowser.browser.WebContentTopInsetScript.bridgeName}.topInsetPx()",
            ) { value ->
                result.set(value)
                completed.countDown()
            }
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertTrue(result.get().toInt() >= EXPECTED_TOP_INSET)
    }

    @Test
    fun autoplayBlockingIsEnabledInControllerAndSystemProviderByDefault() {
        composeRule.runOnIdle {
            val (browserController, webView) = createControllerWithView()
            controller = browserController

            assertTrue(browserController.isVideoAutoplayBlocked)
            assertTrue(webView.settings.mediaPlaybackRequiresUserGesture)
        }
    }

    @Test
    fun webRtcProtectionBlocksByDefaultAndStandardRestoresCompatibility() {
        lateinit var browserController: BrowserController
        lateinit var webView: WebView
        composeRule.runOnIdle {
            val created = createControllerWithView()
            browserController = created.first
            webView = created.second
            controller = browserController
            webView.loadDataWithBaseURL(
                "https://webrtc.test/",
                """
                    <html><head><title>Checking WebRTC</title></head><body><script>
                      try {
                        const connection = new RTCPeerConnection();
                        connection.close();
                        document.title = 'WebRTC available';
                      } catch (error) {
                        document.title = 'WebRTC blocked';
                      }
                    </script></body></html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.title == "WebRTC blocked"
        }
        composeRule.runOnIdle {
            browserController.updateWebRtcProtectionMode(WebRtcProtectionMode.Standard)
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.title == "WebRTC available"
        }
    }

    @Test
    fun privacySignalsReachMainFrameRequestAndDocumentAndCanBeDisabled() {
        PrivacySignalServer().use { server ->
            lateinit var browserController: BrowserController
            composeRule.runOnIdle {
                browserController = createControllerWithView().first
                controller = browserController
                browserController.submitAddress(server.url)
            }

            composeRule.waitUntil(timeoutMillis = 10_000L) {
                browserController.selectedTab.title == "1|true"
            }
            assertTrue(server.awaitRequests(1))
            assertEquals("1", server.headersAt(0)["dnt"])
            assertEquals("1", server.headersAt(0)["sec-gpc"])

            composeRule.runOnIdle {
                browserController.updatePrivacySignalSettings(
                    PrivacySignalSettings(
                        doNotTrackEnabled = false,
                        globalPrivacyControlEnabled = false,
                    ),
                )
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                browserController.selectedTab.title == "null|false"
            }
            assertTrue(server.awaitRequests(2))
            assertFalse(server.headersAt(1).containsKey("dnt"))
            assertFalse(server.headersAt(1).containsKey("sec-gpc"))

            composeRule.runOnIdle {
                browserController.updatePrivacySignalSettings(PrivacySignalSettings.Default)
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                browserController.selectedTab.title == "1|true"
            }
            assertTrue(server.awaitRequests(3))
            assertEquals("1", server.headersAt(2)["dnt"])
            assertEquals("1", server.headersAt(2)["sec-gpc"])

            composeRule.runOnIdle { browserController.reload() }
            assertTrue(server.awaitRequests(4))
            assertEquals("1", server.headersAt(3)["dnt"])
            assertEquals("1", server.headersAt(3)["sec-gpc"])
        }
    }

    @Test
    fun mainFrame404IsCommittedWithNotFoundStatus() {
        NotFoundServer().use { server ->
            lateinit var browserController: BrowserController
            composeRule.runOnIdle {
                browserController = createControllerWithView().first
                controller = browserController
                browserController.submitAddress(server.url)
            }

            composeRule.waitUntil(timeoutMillis = 10_000L) {
                browserController.selectedTab.httpStatusCode == 404
            }

            composeRule.runOnIdle {
                assertEquals(server.url, browserController.selectedTab.url)
                assertEquals("Missing", browserController.selectedTab.title)
                assertEquals(null, browserController.selectedTab.error)
                assertFalse(browserController.selectedTab.isLoading)
            }
        }
    }

    @Test
    fun translationContentProbeDistinguishesBlankGoogleShellFromVisiblePage() {
        lateinit var browserController: BrowserController
        lateinit var webView: WebView
        composeRule.runOnIdle {
            val created = createControllerWithView()
            browserController = created.first
            webView = created.second
            controller = browserController
            webView.loadDataWithBaseURL(
                "https://mt-cc.translate.goog/",
                """
                    <html>
                      <head><title>Blank probe ready</title></head>
                      <body><iframe id="gt-nvframe" style="width:100%;height:57px"></iframe></body>
                    </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.title == "Blank probe ready"
        }
        assertEquals(PageTranslationContentOutcome.Empty, extractContentOutcome(webView))

        composeRule.runOnIdle {
            webView.loadDataWithBaseURL(
                "https://mt-cc.translate.goog/",
                "<html><head><title>Visible probe ready</title></head><body>OK</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.title == "Visible probe ready"
        }
        assertEquals(PageTranslationContentOutcome.Visible, extractContentOutcome(webView))
    }

    @Test
    fun textInputOcclusionProbeRequiresVisibleEditorAndDocumentBottom() {
        lateinit var browserController: BrowserController
        lateinit var webView: WebView
        composeRule.runOnIdle {
            val created = createControllerWithView()
            browserController = created.first
            webView = created.second
            controller = browserController
            webView.loadDataWithBaseURL(
                "https://input-probe.test/",
                """
                    <html>
                      <head><title>Input probe ready</title></head>
                      <body style="margin:0;height:100vh">
                        <div id="editor"></div>
                        <script>
                          const root = document.getElementById('editor').attachShadow({mode:'open'});
                          const editor = document.createElement('div');
                          editor.contentEditable = 'true';
                          editor.style = 'position:fixed;left:10%;right:10%;bottom:20px;height:80px';
                          root.appendChild(editor);
                        </script>
                      </body>
                    </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.title == "Input probe ready"
        }
        assertTrue(evaluateTextInputOcclusion(webView))
        assertFalse(
            evaluateTextInputOcclusion(
                webView,
                TextInputOcclusionProbeMode.FocusedTextInput,
            ),
        )

        composeRule.runOnIdle {
            webView.evaluateJavascript(
                """
                    (() => {
                      document.body.style.height='200vh';
                      const root = document.getElementById('editor').shadowRoot;
                      const editor = root.querySelector('[contenteditable]');
                      let parent = root;
                      for (let depth = 0; depth < 28; depth += 1) {
                        const child = document.createElement('div');
                        parent.appendChild(child);
                        parent = child;
                      }
                      parent.appendChild(editor);
                      editor.style.bottom='-120px';
                      editor.focus();
                      document.title='Scrollable input probe';
                    })();
                """.trimIndent(),
                null,
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            browserController.selectedTab.title == "Scrollable input probe"
        }
        assertFalse(evaluateTextInputOcclusion(webView))
        assertTrue(
            evaluateTextInputOcclusion(
                webView,
                TextInputOcclusionProbeMode.FocusedTextInput,
            ),
        )
    }

    private fun extractContentOutcome(webView: WebView): PageTranslationContentOutcome {
        val result = AtomicReference<String?>()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            webView.evaluateJavascript(ReaderExtractionScript.javascript) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        return PageTranslationRecoveryRules.contentOutcome(result.get())
    }

    private fun evaluateTextInputOcclusion(
        webView: WebView,
        mode: TextInputOcclusionProbeMode = TextInputOcclusionProbeMode.AllEditors,
    ): Boolean {
        val result = AtomicReference<String?>()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            webView.evaluateJavascript(
                TextInputOcclusionScript.javascript(
                    viewportRect = BrowserViewportRect(
                        leftFraction = 0.05f,
                        topFraction = 0.8f,
                        rightFraction = 0.95f,
                        bottomFraction = 0.98f,
                    ),
                    mode = mode,
                ),
            ) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        return result.get() == TextInputOcclusionProbeResult.Occluded.wireValue.toString()
    }

    private fun createControllerWithView(): Pair<BrowserController, WebView> {
        val activity = composeRule.activity
        val browserController = BrowserController(activity)
        val host = FrameLayout(activity)
        activity.addContentView(
            host,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val engineView = requireNotNull(browserController.attachSelectedBrowserEngineView(host))
        return browserController to requireNotNull(engineView.findWebView())
    }

    private fun android.view.View?.containsWebView(): Boolean = when (this) {
        is WebView -> true
        is ViewGroup -> (0 until childCount).any { index -> getChildAt(index).containsWebView() }
        else -> false
    }

    private fun android.view.View?.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findWebView()
        }
        else -> null
    }

    private class NotFoundServer : Closeable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "system-webview-404-fixture").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${server.localPort}/missing"

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        connection.getInputStream().bufferedReader().apply {
                            readLine()
                            while (!readLine().isNullOrEmpty()) {
                                // Drain request headers before deterministic response.
                            }
                        }
                        val body = "<html><head><title>Missing</title></head><body>404</body></html>"
                            .toByteArray()
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 404 Not Found\r\nContent-Type: text/html; charset=utf-8\r\n"
                                    .toByteArray(),
                            )
                            output.write(
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(1_000L)
        }
    }

    private class PrivacySignalServer : Closeable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val rootHeaders = mutableListOf<Map<String, String>>()
        private val thread = Thread(::serve, "system-webview-privacy-signals-fixture").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${server.localPort}/"

        @Synchronized
        fun headersAt(index: Int): Map<String, String> = rootHeaders[index]

        fun awaitRequests(expected: Int): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                if (requestCount() >= expected) return true
                Thread.yield()
            }
            return requestCount() >= expected
        }

        @Synchronized
        private fun requestCount(): Int = rootHeaders.size

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        val reader = connection.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        val headers = buildMap {
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                val separator = line.indexOf(':')
                                if (separator > 0) {
                                    put(
                                        line.substring(0, separator).lowercase(),
                                        line.substring(separator + 1).trim(),
                                    )
                                }
                            }
                        }
                        if (requestLine.substringAfter(' ').substringBefore(' ') == "/") {
                            synchronized(this) { rootHeaders += headers }
                        }
                        val body = """
                            <html><head><script>
                            document.title = String(navigator.doNotTrack) + '|' +
                              String(navigator.globalPrivacyControl);
                            </script></head><body>privacy signals</body></html>
                        """.trimIndent().toByteArray()
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                                    .toByteArray(),
                            )
                            output.write(
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        const val EXPECTED_TOP_INSET = 96
    }
}
