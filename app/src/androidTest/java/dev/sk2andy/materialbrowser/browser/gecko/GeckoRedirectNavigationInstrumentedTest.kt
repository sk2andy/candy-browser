package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoRedirectNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GeckoRedirectTrapActivity.reset()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).apply {
            saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
            saveStartupAnimationEnabled(false)
        }
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        GeckoRedirectTrapActivity.reset()
    }

    @Test
    fun userDrivenSameSiteRedirectorRequestStaysInGecko() {
        RedirectFixtureServer().use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java)
                    .setAction(TEST_ACTIVITY_ACTION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            ).use { scenario ->
                var decision = GeckoNavigationRequestDecision.Deny
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.openUrl(server.searchUrl))
                    controller.dispatchGeckoEngineEventForTesting(
                        BrowserEngineEvent(
                            tabId = controller.selectedTab.id,
                            type = BrowserEngineEventType.NavigationCommitted,
                            address = server.searchUrl,
                            title = SEARCH_TITLE,
                            canGoBack = false,
                            canGoForward = false,
                            failureDescription = null,
                            isLoading = false,
                        ),
                    )
                    decision = controller.dispatchSelectedGeckoNavigationRequestForTesting(
                        GeckoMainFrameNavigationRequest(
                            url = server.redirectUrl,
                            isRedirect = false,
                            hasUserGesture = true,
                            isDirectNavigation = false,
                        ),
                    )
                }

                assertEquals(GeckoNavigationRequestDecision.Allow, decision)
                assertEquals(0, GeckoRedirectTrapActivity.launchCount())
            }
        }
    }

    @Test
    fun geckoFollowsHttp302ToCrossSiteShapedContent() {
        RedirectFixtureServer().use { server ->
            val store = BrowserSessionStore(context)
            val tab = BrowserTab(
                id = "gecko-redirect-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.redirectUrl,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))

            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java)
                    .setAction(TEST_ACTIVITY_ACTION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            ).use { scenario ->
                awaitCondition(
                    message = "Gecko did not commit the 302 target",
                    details = {
                        "requests:\n${server.requestLog()},\nurl=${scenario.currentUrl()}, " +
                            "title=${scenario.currentTitle()}, " +
                            "trap=${GeckoRedirectTrapActivity.launchCount()}"
                    },
                ) {
                    server.requestPaths.contains(REDIRECT_PATH) &&
                        server.requestPaths.contains(FINAL_PATH) &&
                        scenario.currentUrl() == server.finalUrl &&
                        scenario.currentTitle() == FINAL_TITLE
                }
                assertEquals(0, GeckoRedirectTrapActivity.launchCount())
            }
        }
    }

    private fun ActivityScenario<MainActivity>.currentUrl(): String {
        var value = ""
        onActivity { activity -> value = activity.browserControllerForTesting().selectedTab.url }
        return value
    }

    private fun ActivityScenario<MainActivity>.currentTitle(): String {
        var value = ""
        onActivity { activity -> value = activity.browserControllerForTesting().selectedTab.title }
        return value
    }

    private fun awaitCondition(
        message: String,
        details: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("$message; ${details()}", condition())
    }

    private class RedirectFixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, REQUEST_BACKLOG, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "gecko-redirect-fixture").apply {
            isDaemon = true
            start()
        }
        private val requests = CopyOnWriteArrayList<RequestObservation>()
        val requestPaths: List<String>
            get() = requests.map(RequestObservation::path)
        val searchUrl = "http://google.candy.localhost:${socket.localPort}$SEARCH_PATH"
        val redirectUrl = "http://google.candy.localhost:${socket.localPort}$REDIRECT_PATH"
        val finalUrl = "http://github.candy.localhost:${socket.localPort}$FINAL_PATH"

        fun requestLog(): String = requests.joinToString("\n") { request ->
            "${request.elapsedRealtimeMillis}: ${request.path}"
        }

        private fun serve() {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: return
                connection.use { client ->
                    runCatching {
                        client.soTimeout = CLIENT_READ_TIMEOUT_MILLIS
                        val reader = client.getInputStream().bufferedReader()
                        val requestPath = reader.readLine()
                            ?.split(' ')
                            ?.getOrNull(1)
                            ?: return@runCatching
                        requests += RequestObservation(
                            path = requestPath,
                            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                        )
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain request headers before responding.
                        }
                        val output = client.getOutputStream().buffered()
                        if (requestPath == REDIRECT_PATH) {
                            output.write(
                                (
                                    "HTTP/1.1 302 Found\r\n" +
                                        "Location: $finalUrl\r\n" +
                                        "Content-Length: 0\r\nConnection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII),
                            )
                        } else {
                            val body = if (requestPath == SEARCH_PATH) SEARCH_HTML else FINAL_HTML
                            val bytes = body.toByteArray(StandardCharsets.UTF_8)
                            output.write(
                                (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html; charset=utf-8\r\n" +
                                        "Content-Length: ${bytes.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII),
                            )
                            output.write(bytes)
                        }
                        output.flush()
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(1_000L)
        }

        private data class RequestObservation(
            val path: String,
            val elapsedRealtimeMillis: Long,
        )
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_REDIRECT"
        const val SEARCH_PATH = "/search?q=github"
        const val REDIRECT_PATH = "/redirect?q=github"
        const val FINAL_PATH = "/github"
        const val REQUEST_BACKLOG = 8
        const val CLIENT_READ_TIMEOUT_MILLIS = 1_000
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 50L
        const val SEARCH_TITLE = "Google fixture"
        val SEARCH_HTML =
            """
            <!doctype html><title>$SEARCH_TITLE</title><a href="$REDIRECT_PATH">GitHub</a>
            """.trimIndent()
        const val FINAL_TITLE = "GitHub fixture"
        const val FINAL_HTML = "<!doctype html><title>$FINAL_TITLE</title><h1>GitHub</h1>"
    }
}
