package dev.sk2andy.materialbrowser.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.integration.ExternalAppLauncher
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerExternalNavigationInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

    @Before
    fun setUp() {
        activityRule.scenario.onActivity(::clearSession)
    }

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            clearSession(activity)
        }
    }

    @Test
    fun externalPreviewHandsTappedWebLinkToDirectAppHandler() {
        activityRule.scenario.onActivity { activity ->
            val recordingContext = RecordingContext(activity)
            val browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(recordingContext),
            ).also { controller = it }
            assertTrue(browserController.openExternalLinkPreview(SOURCE_URL))
            val preview = requireNotNull(browserController.externalLinkPreviewState)
            assertTrue(browserController.prepareExternalLinkPreview(preview.sessionId))
            val previewWebView = requireNotNull(
                browserController.externalLinkPreviewWebViewForTesting(),
            )

            val handled = previewWebView.webViewClient.shouldOverrideUrlLoading(
                previewWebView,
                TestWebResourceRequest(
                    url = APP_LINK_URL,
                    hasGesture = true,
                ),
            )

            assertTrue(handled)
            assertEquals(APP_LINK_URL, recordingContext.lastIntent?.dataString)
            assertTrue(
                requireNotNull(recordingContext.lastIntent).flags and
                    Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0,
            )
        }
    }

    @Test
    fun externalPreviewRequiresGestureGrantForSpecialSchemeRedirect() {
        activityRule.scenario.onActivity { activity ->
            val recordingContext = RecordingContext(activity).apply {
                rejectWebLinks = true
            }
            val browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(recordingContext),
            ).also { controller = it }
            assertTrue(browserController.openExternalLinkPreview(SOURCE_URL))
            val preview = requireNotNull(browserController.externalLinkPreviewState)
            assertTrue(browserController.prepareExternalLinkPreview(preview.sessionId))
            val previewWebView = requireNotNull(
                browserController.externalLinkPreviewWebViewForTesting(),
            )
            val client = previewWebView.webViewClient

            assertFalse(
                client.shouldOverrideUrlLoading(
                    previewWebView,
                    TestWebResourceRequest(
                        url = APP_LINK_URL,
                        hasGesture = true,
                    ),
                ),
            )
            recordingContext.lastIntent = null
            assertTrue(
                client.shouldOverrideUrlLoading(
                    previewWebView,
                    TestWebResourceRequest(
                        url = SPECIAL_SCHEME_URL,
                        hasGesture = false,
                    ),
                ),
            )
            assertEquals(SPECIAL_SCHEME_URL, recordingContext.lastIntent?.dataString)

            recordingContext.lastIntent = null
            assertTrue(
                client.shouldOverrideUrlLoading(
                    previewWebView,
                    TestWebResourceRequest(
                        url = SECOND_SPECIAL_SCHEME_URL,
                        hasGesture = false,
                        isRedirect = true,
                    ),
                ),
            )
            assertNull(recordingContext.lastIntent)
        }
    }

    @Test
    fun incomingExternalPreviewAuthorizesItsAppRedirect() {
        HangingServer().use { server ->
            lateinit var recordingContext: RecordingContext
            lateinit var previewWebView: WebView
            activityRule.scenario.onActivity { activity ->
                val container = FrameLayout(activity)
                activity.setContentView(container)
                recordingContext = RecordingContext(activity)
                val browserController = BrowserController(
                    activity = activity,
                    externalApps = ExternalAppLauncher(recordingContext),
                ).also { controller = it }
                browserController.onStart()
                browserController.onResume()
                assertTrue(
                    browserController.openExternalLinkPreview(
                        url = server.url,
                        allowInitialAppHandoff = true,
                    ),
                )
                val preview = requireNotNull(browserController.externalLinkPreviewState)
                assertTrue(browserController.prepareExternalLinkPreview(preview.sessionId))
                previewWebView = requireNotNull(
                    browserController.externalLinkPreviewWebViewForTesting(),
                )
                browserController.attachExternalLinkPreview(container)
            }
            waitUntil {
                var started = false
                activityRule.scenario.onActivity {
                    started = previewWebView.url == server.url
                }
                started
            }

            activityRule.scenario.onActivity {
                val handled = previewWebView.webViewClient.shouldOverrideUrlLoading(
                    previewWebView,
                    TestWebResourceRequest(
                        url = APP_LINK_URL,
                        hasGesture = false,
                        isRedirect = true,
                    ),
                )

                assertTrue(handled)
                assertEquals(APP_LINK_URL, recordingContext.lastIntent?.dataString)
            }
        }
    }

    @Test
    fun incomingRegularTabAuthorizesItsAppRedirect() {
        HangingServer().use { server ->
            lateinit var recordingContext: RecordingContext
            lateinit var webView: WebView
            activityRule.scenario.onActivity { activity ->
                recordingContext = RecordingContext(activity)
                val browserController = BrowserController(
                    activity = activity,
                    externalApps = ExternalAppLauncher(recordingContext),
                ).also { controller = it }
                assertTrue(
                    browserController.openUrl(
                        url = server.url,
                        inNewTab = true,
                        authorizeInitialExternalNavigation = true,
                    ),
                )
                webView = browserController.selectedWebViewForTesting()
            }
            waitForWebViewUrl(webView, server.url)

            activityRule.scenario.onActivity {
                val handled = webView.webViewClient.shouldOverrideUrlLoading(
                    webView,
                    TestWebResourceRequest(
                        url = APP_LINK_URL,
                        hasGesture = false,
                        isRedirect = true,
                    ),
                )

                assertTrue(handled)
                assertEquals(APP_LINK_URL, recordingContext.lastIntent?.dataString)
            }
        }
    }

    @Test
    fun replacementNavigationRevokesPreviousAppHandoffGrant() {
        HangingServer().use { server ->
            lateinit var recordingContext: RecordingContext
            lateinit var browserController: BrowserController
            lateinit var webView: WebView
            activityRule.scenario.onActivity { activity ->
                recordingContext = RecordingContext(activity)
                browserController = BrowserController(
                    activity = activity,
                    externalApps = ExternalAppLauncher(recordingContext),
                ).also { controller = it }
                assertTrue(
                    browserController.openUrl(
                        url = server.url,
                        inNewTab = true,
                        authorizeInitialExternalNavigation = true,
                    ),
                )
                webView = browserController.selectedWebViewForTesting()
            }
            waitForWebViewUrl(webView, server.url)

            activityRule.scenario.onActivity {
                browserController.submitAddress(REPLACEMENT_URL)
                assertTrue(
                    webView.webViewClient.shouldOverrideUrlLoading(
                        webView,
                        TestWebResourceRequest(
                            url = SPECIAL_SCHEME_URL,
                            hasGesture = false,
                            isRedirect = true,
                        ),
                    ),
                )
                assertNull(recordingContext.lastIntent)
            }
        }
    }

    @Test
    fun desktopReplayPreservesCurrentAppHandoffGrant() {
        HangingServer().use { server ->
            lateinit var recordingContext: RecordingContext
            lateinit var webView: WebView
            val desktopUrl = server.url(DESKTOP_HOST)
            activityRule.scenario.onActivity { activity ->
                recordingContext = RecordingContext(activity).apply {
                    rejectWebLinks = true
                }
                val browserController = BrowserController(
                    activity = activity,
                    externalApps = ExternalAppLauncher(recordingContext),
                ).also { controller = it }
                val desktopTabId = browserController.createTab(desktopUrl)
                assertTrue(browserController.setDesktopView(desktopTabId, true))
                assertTrue(
                    browserController.openUrl(
                        url = server.url("localhost"),
                        inNewTab = true,
                        authorizeInitialExternalNavigation = true,
                    ),
                )
                webView = browserController.selectedWebViewForTesting()
            }
            waitForWebViewUrl(webView, server.url("localhost"))

            activityRule.scenario.onActivity {
                assertTrue(
                    webView.webViewClient.shouldOverrideUrlLoading(
                        webView,
                        TestWebResourceRequest(
                            url = desktopUrl,
                            hasGesture = false,
                            isRedirect = true,
                        ),
                    ),
                )
            }
            waitForWebViewUrl(webView, desktopUrl)

            activityRule.scenario.onActivity {
                recordingContext.rejectWebLinks = false
                recordingContext.lastIntent = null
                assertTrue(
                    webView.webViewClient.shouldOverrideUrlLoading(
                        webView,
                        TestWebResourceRequest(
                            url = SPECIAL_SCHEME_URL,
                            hasGesture = false,
                            isRedirect = true,
                        ),
                    ),
                )
                assertEquals(SPECIAL_SCHEME_URL, recordingContext.lastIntent?.dataString)
            }
        }
    }

    @Test
    fun staleRegularWebViewCannotHandLinkToExternalApp() {
        activityRule.scenario.onActivity { activity ->
            val recordingContext = RecordingContext(activity)
            val browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(recordingContext),
            ).also { controller = it }
            val staleTabId = browserController.selectedTabId
            val staleWebView = browserController.selectedWebViewForTesting()
            val staleClient = staleWebView.webViewClient
            browserController.createTab()
            browserController.closeTab(staleTabId)

            assertTrue(
                staleClient.shouldOverrideUrlLoading(
                    staleWebView,
                    TestWebResourceRequest(
                        url = APP_LINK_URL,
                        hasGesture = true,
                    ),
                ),
            )
            assertNull(recordingContext.lastIntent)
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var rejectWebLinks = false
        var lastIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            lastIntent = Intent(intent)
            if (rejectWebLinks && intent.data?.scheme in setOf("http", "https")) {
                throw ActivityNotFoundException()
            }
        }
    }

    private class TestWebResourceRequest(
        url: String,
        private val hasGesture: Boolean,
        private val isRedirect: Boolean = false,
    ) : WebResourceRequest {
        private val uri = Uri.parse(url)

        override fun getUrl(): Uri = uri

        override fun isForMainFrame(): Boolean = true

        override fun isRedirect(): Boolean = isRedirect

        override fun hasGesture(): Boolean = hasGesture

        override fun getMethod(): String = "GET"

        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private class HangingServer : Closeable {
        private val server = ServerSocket(
            0,
            1,
            InetAddress.getByName("127.0.0.1"),
        )

        val url = url("127.0.0.1")

        fun url(host: String) = "http://$host:${server.localPort}/hold"

        init {
            thread(name = "external-navigation-hanging-server", isDaemon = true) {
                runCatching {
                    server.accept().use {
                        while (!server.isClosed) SystemClock.sleep(POLL_INTERVAL_MILLIS)
                    }
                }
            }
        }

        override fun close() {
            server.close()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Condition not met within $TIMEOUT_MILLIS ms")
    }

    private fun waitForWebViewUrl(webView: WebView, expectedUrl: String) {
        waitUntil {
            var currentUrl: String? = null
            activityRule.scenario.onActivity { currentUrl = webView.url }
            currentUrl == expectedUrl
        }
    }

    private fun clearSession(context: Context) {
        context.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private companion object {
        const val SOURCE_URL = "https://example.invalid/source"
        const val APP_LINK_URL = "https://app.example.invalid/open"
        const val REPLACEMENT_URL = "https://replacement.example.invalid/page"
        const val SPECIAL_SCHEME_URL = "candy-app://open/authorized"
        const val SECOND_SPECIAL_SCHEME_URL = "candy-app://open/passive"
        const val DESKTOP_HOST = "desktop.127.0.0.1.nip.io"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
