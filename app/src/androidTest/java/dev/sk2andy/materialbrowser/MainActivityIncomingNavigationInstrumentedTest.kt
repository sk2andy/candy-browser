package dev.sk2andy.materialbrowser

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMainFrameNavigationRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestDecision
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutRules
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class MainActivityIncomingNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val handoffs = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW || intent.component != null) return null
            handoffs += Intent(intent)
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        instrumentation.addMonitor(monitor)
    }

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
        preferences.edit().clear().commit()
    }

    @Test
    fun incomingViewOpensPreviewWithoutReturningLinkToExternalApp() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            scenario.onActivity { activity ->
                val preview = requireNotNull(activity.browserControllerForTesting().externalLinkPreviewState)
                assertEquals(INCOMING_URL, preview.currentUrl)
                assertNull(preview.appHandoffExpiresAtElapsedRealtime)
            }
            assertEquals(0, handoffs.size)
        }
    }

    @Test
    fun incomingTabRedirectCannotReturnToAppButSubsequentTapCan() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(false)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            awaitIncomingTab(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(INCOMING_URL, controller.selectedTab.url)
                assertEquals(0, handoffs.size)
                assertEquals(
                    GeckoNavigationRequestDecision.Allow,
                    controller.dispatchSelectedGeckoNavigationRequestForTesting(
                        GeckoMainFrameNavigationRequest(
                            url = "https://www.youtube.com/watch?v=candy",
                            isRedirect = true,
                            hasUserGesture = false,
                            isDirectNavigation = false,
                        ),
                    ),
                )
                assertEquals(
                    GeckoNavigationRequestDecision.Allow,
                    controller.dispatchSelectedGeckoNavigationRequestForTesting(
                        GeckoMainFrameNavigationRequest(
                            url = APP_URL,
                            isRedirect = true,
                            hasUserGesture = false,
                            isDirectNavigation = false,
                        ),
                    ),
                )
                assertEquals(0, handoffs.size)
                assertEquals(
                    GeckoNavigationRequestDecision.Deny,
                    controller.dispatchSelectedGeckoNavigationRequestForTesting(
                        GeckoMainFrameNavigationRequest(
                            url = APP_URL,
                            isRedirect = false,
                            hasUserGesture = true,
                            isDirectNavigation = false,
                        ),
                    ),
                )
            }
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            while (handoffs.isEmpty() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(20L)
            }
            assertEquals(APP_URL, handoffs.single().dataString)
        }
    }

    @Test
    fun coldIncomingViewLoadsPage() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(false)
        IncomingPageServer().use { server ->
            ActivityScenario.launch<MainActivity>(incomingIntent(server.url)).use { scenario ->
                awaitIncomingPage(scenario, server)
            }
        }
    }

    @Test
    fun coldIncomingViewLoadsExternalPreviewPage() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        IncomingPageServer().use { server ->
            ActivityScenario.launch<MainActivity>(incomingIntent(server.url)).use { scenario ->
                var activity: MainActivity? = null
                scenario.onActivity { activity = it }
                awaitIncomingPreviewPage(requireNotNull(activity), server)
            }
        }
    }

    @Test
    fun warmIncomingViewLoadsExternalPreviewPage() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        IncomingPageServer().use { server ->
            val activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            ) as MainActivity
            try {
                instrumentation.runOnMainSync {
                    instrumentation.callActivityOnNewIntent(activity, incomingIntent(server.url))
                }
                awaitIncomingPreviewPage(activity, server)
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        }
    }

    @Test
    fun warmIncomingViewLoadsPage() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(false)
        IncomingPageServer().use { server ->
            val activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            ) as MainActivity
            try {
                instrumentation.runOnMainSync {
                    instrumentation.callActivityOnNewIntent(activity, incomingIntent(server.url))
                }
                awaitIncomingPage(activity, server)
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        }
    }

    @Test
    fun warmIncomingPreviewLeavesSiteCapsuleAndShowsIncomingPage() {
        assertIncomingLinkLeavesSiteCapsule(previewEnabled = true)
    }

    @Test
    fun warmIncomingViewLeavesSiteCapsuleAndShowsIncomingPage() {
        assertIncomingLinkLeavesSiteCapsule(previewEnabled = false)
    }

    @Test
    fun warmIncomingViewKeepsNewPreviewWithoutAppHandoffGrant() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            val nextUrl = "https://youtube.candy.test/redirect?q=https%3A%2F%2Fexample.com"
            lateinit var originalIntent: Intent
            scenario.onActivity { activity ->
                originalIntent = activity.intent
                activity.startActivity(
                    incomingIntent(nextUrl).apply {
                        removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                )
            }
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            var previewUrl: String? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    previewUrl = activity.browserControllerForTesting().externalLinkPreviewState?.currentUrl
                }
                if (previewUrl == nextUrl) break
                SystemClock.sleep(20L)
            }
            scenario.onActivity { activity ->
                try {
                    val preview = requireNotNull(activity.browserControllerForTesting().externalLinkPreviewState)
                    assertEquals(nextUrl, preview.currentUrl)
                    assertNull(preview.appHandoffExpiresAtElapsedRealtime)
                    assertEquals(0, handoffs.size)
                } finally {
                    // ActivityScenario matches lifecycle events against its original launch intent.
                    activity.intent = originalIntent
                }
            }
        }
    }

    @Test
    fun userDepartureKeepsPreviewForPasswordManagerReturn() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            var previewSessionId = -1L
            lateinit var previewEngineView: View
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                previewSessionId = requireNotNull(controller.externalLinkPreviewState).sessionId
                assertTrue(controller.prepareExternalLinkPreview(previewSessionId))
                previewEngineView = requireNotNull(
                    controller.externalLinkPreviewEngineViewForTesting(),
                )
                instrumentation.callActivityOnUserLeaving(activity)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()

                assertEquals(
                    INCOMING_URL,
                    controller.externalLinkPreviewState?.currentUrl,
                )
                assertEquals(previewSessionId, controller.externalLinkPreviewState?.sessionId)
                assertSame(previewEngineView, controller.externalLinkPreviewEngineViewForTesting())
            }
        }
    }

    @Test
    fun warmAppIconLaunchDismissesPreview() {
        assertWarmLaunchDismissesPreview(Intent.ACTION_MAIN)
    }

    @Test
    fun warmWidgetLaunchDismissesPreview() {
        assertWarmLaunchDismissesPreview(LauncherShortcutRules.ACTION_OPEN_APP)
    }

    private fun incomingIntent(url: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun assertIncomingLinkLeavesSiteCapsule(previewEnabled: Boolean) {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(previewEnabled)
        IncomingPageServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var activity: MainActivity? = null
                scenario.onActivity { current ->
                    activity = current
                    val controller = current.browserControllerForTesting()
                    val capsule = SiteCapsule(
                        id = "04a74ad8-7533-460c-bfbf-a135968940d5",
                        name = "Incoming link capsule",
                        startUrl = server.url,
                        profileId = controller.activeProfileId,
                        createdAtMillis = 1L,
                        updatedAtMillis = 1L,
                    )
                    controller.siteCapsules += capsule
                    assertTrue(controller.openSiteCapsule(capsule.id, navigateToStart = false))
                    assertEquals(capsule.id, controller.activeCapsuleId)
                    val originalIntent = current.intent
                    try {
                        instrumentation.callActivityOnNewIntent(current, incomingIntent(server.url))
                    } finally {
                        current.intent = originalIntent
                    }
                    assertNull(controller.activeSiteCapsule)
                }
                if (previewEnabled) {
                    awaitIncomingPreviewPage(requireNotNull(activity), server)
                } else {
                    awaitIncomingPage(scenario, server)
                }
            }
        }
    }

    private fun assertWarmLaunchDismissesPreview(action: String) {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            lateinit var originalIntent: Intent
            scenario.onActivity { activity ->
                originalIntent = activity.intent
                instrumentation.callActivityOnNewIntent(
                    activity,
                    Intent(activity, MainActivity::class.java)
                        .setAction(action),
                )
            }
            scenario.onActivity { activity ->
                try {
                    assertNull(activity.browserControllerForTesting().externalLinkPreviewState)
                } finally {
                    // ActivityScenario matches lifecycle events against its original launch intent.
                    activity.intent = originalIntent
                }
            }
        }
    }

    private fun awaitIncomingTab(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var currentUrl = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                currentUrl = activity.browserControllerForTesting().selectedTab.url
            }
            if (currentUrl == INCOMING_URL) return
            SystemClock.sleep(50L)
        }
        assertTrue("Incoming tab did not load; current URL: $currentUrl", currentUrl == INCOMING_URL)
    }

    private fun awaitIncomingPage(
        scenario: ActivityScenario<MainActivity>,
        server: IncomingPageServer,
    ) {
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        awaitIncomingPage(requireNotNull(activity), server)
    }

    private fun awaitIncomingPage(activity: MainActivity, server: IncomingPageServer) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var title = ""
        var currentUrl = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                val tab = activity.browserControllerForTesting().selectedTab
                title = tab.title
                currentUrl = tab.url
            }
            if (
                currentUrl == server.url &&
                title == "Incoming page" &&
                server.requests.get() > 0
            ) break
            SystemClock.sleep(50L)
        }
        assertEquals(server.url, currentUrl)
        assertEquals("Incoming page", title)
        assertTrue("Incoming page was not requested", server.requests.get() > 0)
    }

    private fun awaitIncomingPreviewPage(activity: MainActivity, server: IncomingPageServer) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var currentUrl: String? = null
        var isLoading = true
        var contentVisible = false
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                val preview = activity.browserControllerForTesting().externalLinkPreviewState
                currentUrl = preview?.currentUrl
                isLoading = preview?.isLoading != false
            }
            if (currentUrl == server.url && !isLoading && server.requests.get() > 0) {
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    val center = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
                    contentVisible = Color.red(center) > 180 &&
                        Color.green(center) < 100 &&
                        Color.blue(center) < 140
                    screenshot.recycle()
                }
                if (contentVisible) break
            }
            SystemClock.sleep(50L)
        }
        assertEquals(server.url, currentUrl)
        assertTrue("Incoming preview did not finish loading", !isLoading)
        assertTrue("Incoming preview was not requested", server.requests.get() > 0)
        assertTrue("Incoming preview page was not visible", contentVisible)
    }

    private companion object {
        const val INCOMING_URL = "https://youtube.candy.test/redirect?q=https%3A%2F%2Fgithub.com"
        const val APP_URL = "candy-fixture://return-to-app"
    }

    private class IncomingPageServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "incoming-page-fixture").apply {
            isDaemon = true
            start()
        }

        val url = "http://127.0.0.1:${socket.localPort}/incoming"
        val requests = AtomicInteger()

        private fun serve() {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: return
                connection.use {
                    runCatching {
                        val reader = connection.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain request headers.
                        }
                        if (request.contains(" /incoming ")) requests.incrementAndGet()
                        val body = (
                            "<!doctype html><title>Incoming page</title>" +
                                "<style>html,body{margin:0;min-height:100%;background:#ed273b}</style>" +
                                "<body>Incoming page</body>"
                            ).toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(1_000L)
        }
    }
}
