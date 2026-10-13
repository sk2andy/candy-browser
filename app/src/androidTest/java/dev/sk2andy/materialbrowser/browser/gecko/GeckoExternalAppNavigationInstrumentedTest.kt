package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
import dev.sk2andy.materialbrowser.browser.integration.ExternalAppLauncher
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoExternalAppNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: BrowserController
    private lateinit var host: FrameLayout
    private lateinit var originalEngineKind: AndroidBrowserEngineKind
    private lateinit var originalHttpsOnlyMode: HttpsOnlyMode
    private var originalTabs: Pair<List<BrowserTab>, String?>? = null
    private var originalHistory: List<HistoryEntry>? = null
    private var originalExternalAppLinkHandling: ExternalAppLinkHandling? = null
    private val lookupGate = AtomicReference<LookupGate?>()
    private val callbacks = CopyOnWriteArrayList<String>()
    private val navigationRequests = CopyOnWriteArrayList<GeckoMainFrameNavigationRequest>()

    @Before
    fun setUp() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalHttpsOnlyMode = store.loadHttpsOnlyMode()
            originalTabs = store.loadTabs()
            originalHistory = store.loadHistory()
            originalExternalAppLinkHandling = store.loadExternalAppLinkHandling()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            store.saveHttpsOnlyMode(HttpsOnlyMode.Off)
            store.saveExternalAppLinkHandling(ExternalAppLinkHandling.Automatic)
            val initialTab = BrowserTab(
                id = "external-app-initial-${UUID.randomUUID()}",
                lastAccessedAt = System.currentTimeMillis(),
                url = "about:blank",
            )
            assertTrue(store.saveTabsImmediately(listOf(initialTab), initialTab.id))
            controller = BrowserController(
                activity = composeRule.activity,
                externalApps = ExternalAppLauncher(
                    context = composeRule.activity,
                    findExternalWebPackages = { intent ->
                        lookupGate.get()?.takeIf { it.url == intent.dataString }?.let { gate ->
                            gate.started.countDown()
                            check(gate.release.await(15, TimeUnit.SECONDS)) {
                                "External app lookup was not released"
                            }
                        }
                        emptyList()
                    },
                    canResolveExternalActivity = { false },
                ),
            )
            host = FrameLayout(composeRule.activity)
            composeRule.activity.addContentView(
                host,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            controller.onResume()
        }
    }

    @After
    fun tearDown() {
        lookupGate.getAndSet(null)?.release?.countDown()
        composeRule.runOnIdle {
            if (::controller.isInitialized) controller.destroy()
            val store = BrowserSessionStore(composeRule.activity)
            if (::originalEngineKind.isInitialized) {
                assertTrue(store.saveAndroidBrowserEngineKind(originalEngineKind))
            }
            if (::originalHttpsOnlyMode.isInitialized) {
                store.saveHttpsOnlyMode(originalHttpsOnlyMode)
                GeckoRuntimeOwner.getOrCreate(composeRule.activity)
                    .setHttpsOnlyMode(originalHttpsOnlyMode)
            }
            originalTabs?.let { (tabs, selection) ->
                assertTrue(store.saveTabsImmediately(tabs, selection.orEmpty()))
            }
            originalHistory?.let { assertTrue(store.commitHistory(it)) }
            originalExternalAppLinkHandling?.let(store::saveExternalAppLinkHandling)
        }
    }

    @Test
    fun automaticCrossSiteLinkWithoutAppNavigatesAfterTap() {
        verifyCrossSiteNavigation(delayLookup = false, openNewWindow = false)
    }

    @Test
    fun delayedAutomaticCrossSiteLinkWithoutAppNavigatesAfterRepeatedTapAndBack() {
        verifyCrossSiteNavigation(delayLookup = true, openNewWindow = false, repetitions = 3)
    }

    @Test
    fun automaticCrossSiteFallbackSurvivesSourceHistoryReplaceState() {
        verifyCrossSiteNavigation(
            delayLookup = true,
            openNewWindow = false,
            replaceSourceHistory = true,
        )
    }

    @Test
    fun delayedAutomaticHttpRedirectWithoutAppResumesCrossSiteTarget() {
        RedirectFixtureServer(::page).use { server ->
            composeRule.runOnIdle {
                controller.createTab(initialUrl = server.sourceUrl)
                controller.attachSelectedBrowserEngineView(host)
            }
            awaitPage(server.sourceUrl, "External app source")
            val sourceId = composeRule.runOnIdle {
                observeSelectedSession()
                controller.selectedTabId
            }
            val gate = LookupGate(server.targetUrl)
            lookupGate.set(gate)
            try {
                tapPage()
                assertTrue(
                    "Redirected app lookup did not start; ${diagnostics()}",
                    gate.started.await(5, TimeUnit.SECONDS),
                )
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .waitForIdle(2_000L)
                composeRule.runOnIdle {
                    assertTrue(navigationRequests.any { request ->
                        request.url == server.redirectUrl && request.hasUserGesture
                    })
                    assertTrue(navigationRequests.any { request ->
                        request.url == server.targetUrl && request.isRedirect
                    })
                    assertEquals(0, server.targetRequests.get())
                }
            } finally {
                gate.release.countDown()
            }
            awaitPage(server.targetUrl, "External app target")
            awaitAutomaticLookupCompletion()
            awaitCondition("Redirect fallback did not retain source history") {
                nativeHistoryUrls() == listOf(server.sourceUrl, server.targetUrl)
            }
            composeRule.runOnIdle {
                assertEquals(sourceId, controller.selectedTabId)
                assertTrue(controller.selectedTab.canGoBack)
                assertEquals(1, server.targetRequests.get())
                assertNull(controller.selectedTab.error)
                assertNull(controller.externalAppPrompt)
            }
        }
    }

    @Test
    fun delayedAutomaticDirectLinkWhileSourceStillLoadingResumesTarget() {
        RedirectFixtureServer(::page, holdSourceLoad = true).use { server ->
            composeRule.runOnIdle {
                controller.createTab(initialUrl = server.sourceUrl)
                controller.attachSelectedBrowserEngineView(host)
            }
            assertTrue(
                "Slow source image was not requested",
                server.slowRequestStarted.await(5, TimeUnit.SECONDS),
            )
            awaitCondition("Source content did not become available during its pending image load") {
                controller.selectedTab.let { tab ->
                    tab.url == server.sourceUrl && tab.title == "External app source" && tab.isLoading
                }
            }
            val sourceId = composeRule.runOnIdle {
                observeSelectedSession()
                controller.selectedTabId
            }
            val gate = LookupGate(server.targetUrl)
            lookupGate.set(gate)
            try {
                tapPage()
                assertTrue(
                    "Direct app lookup did not start while source was loading; ${diagnostics()}",
                    gate.started.await(5, TimeUnit.SECONDS),
                )
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .waitForIdle(2_000L)
                composeRule.runOnIdle {
                    assertTrue(navigationRequests.any { request ->
                        request.url == server.targetUrl && request.hasUserGesture && !request.isRedirect
                    })
                    assertEquals(0, server.targetRequests.get())
                }
            } finally {
                gate.release.countDown()
            }
            awaitPage(server.targetUrl, "External app target")
            awaitAutomaticLookupCompletion()
            awaitCondition("Direct fallback did not retain loading source history") {
                nativeHistoryUrls() == listOf(server.sourceUrl, server.targetUrl)
            }
            composeRule.runOnIdle {
                assertEquals(sourceId, controller.selectedTabId)
                assertTrue(controller.selectedTab.canGoBack)
                assertEquals(1, server.targetRequests.get())
                assertNull(controller.selectedTab.error)
                assertNull(controller.externalAppPrompt)
            }
        }
    }

    @Test
    fun delayedAutomaticTargetBlankLinkWithoutAppOpensBrowserFallbackPopup() {
        verifyCrossSiteNavigation(delayLookup = true, openNewWindow = true)
    }

    @Test
    fun askEveryTimeCrossSiteLinkWithoutAppNavigatesWithoutPrompt() {
        composeRule.runOnIdle {
            controller.updateExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)
        }
        verifyCrossSiteNavigation(delayLookup = false, openNewWindow = false)
    }

    @Test
    fun automaticCrossSiteLookupDoesNotReplaceLaterDocumentNavigation() {
        verifyCanceledLookup(replaceSourceDocument = true)
    }

    @Test
    fun stopLoadingCancelsPendingAutomaticCrossSiteFallback() {
        verifyCanceledLookup(replaceSourceDocument = false)
    }

    private fun verifyCanceledLookup(replaceSourceDocument: Boolean) {
        val targetUrl = AtomicReference<String>()
        val targetRequests = AtomicInteger()
        val clickAttribute = if (replaceSourceDocument) {
            "onclick=\"setTimeout(() => location.assign('/replacement'), 150)\""
        } else {
            ""
        }
        EdgeToEdgeSiteFixtureServer { target ->
            when (target.substringBefore('?')) {
                "/source" -> page(
                    "External app source",
                    "<a href='${targetUrl.get()}' $clickAttribute>Open cross-site result</a>",
                )
                "/replacement" -> page("Replacement document", "Replacement loaded")
                "/target" -> {
                    targetRequests.incrementAndGet()
                    page("External app target", "Target loaded")
                }
                else -> "<title>Fixture resource</title>"
            }
        }.use { server ->
            val sourceUrl = server.fixtureUrl("/source").replace("127.0.0.1", "localhost")
            val replacementUrl = server.fixtureUrl("/replacement").replace("127.0.0.1", "localhost")
            val destinationUrl = server.fixtureUrl("/target")
            targetUrl.set(destinationUrl)
            composeRule.runOnIdle {
                controller.createTab(initialUrl = sourceUrl)
                controller.attachSelectedBrowserEngineView(host)
            }
            awaitPage(sourceUrl, "External app source")
            val sourceId = composeRule.runOnIdle {
                observeSelectedSession()
                controller.selectedTabId
            }
            val gate = LookupGate(destinationUrl)
            lookupGate.set(gate)
            try {
                tapPage()
                assertTrue("External app lookup did not start", gate.started.await(5, TimeUnit.SECONDS))
                if (replaceSourceDocument) {
                    awaitCondition("Replacement navigation was not requested", timeoutMillis = 5_000L) {
                        navigationRequests.any { request -> request.url == replacementUrl }
                    }
                } else {
                    composeRule.runOnIdle { controller.stopLoading() }
                }
            } finally {
                gate.release.countDown()
            }
            if (replaceSourceDocument) {
                awaitPage(replacementUrl, "Replacement document")
            }
            awaitAutomaticLookupCompletion()
            composeRule.runOnIdle {
                assertEquals(sourceId, controller.selectedTabId)
                if (replaceSourceDocument) {
                    assertEquals(replacementUrl, controller.selectedTab.url)
                    assertNull(controller.selectedTab.error)
                } else {
                    assertEquals(listOf(sourceUrl), nativeHistoryUrls())
                }
                assertNull(controller.externalAppPrompt)
                assertEquals(0, targetRequests.get())
            }
        }
    }

    private fun awaitAutomaticLookupCompletion() {
        val executor = composeRule.runOnIdle {
            BrowserController::class.java.getDeclaredField("externalAppLookupExecutor").let { field ->
                field.isAccessible = true
                field.get(controller) as Executor
            }
        }
        val completed = CountDownLatch(1)
        // This executor is serial. Its marker posts behind the launch completion on main,
        // so cancellation assertions cannot race the still-pending fallback callback.
        executor.execute {
            Handler(Looper.getMainLooper()).post { completed.countDown() }
        }
        assertTrue("Automatic app lookup did not complete", completed.await(5, TimeUnit.SECONDS))
    }

    private fun verifyCrossSiteNavigation(
        delayLookup: Boolean,
        openNewWindow: Boolean,
        repetitions: Int = 1,
        replaceSourceHistory: Boolean = false,
    ) {
        val targetUrl = AtomicReference<String>()
        val targetAttribute = if (openNewWindow) "target='_blank'" else ""
        val clickAttribute = if (replaceSourceHistory) {
            "onclick=\"setTimeout(() => history.replaceState({}, '', '?conversation=fixture'), 150)\""
        } else {
            ""
        }
        EdgeToEdgeSiteFixtureServer { target ->
            when (target.substringBefore('?')) {
                "/source" -> page(
                    "External app source",
                    "<a href='${targetUrl.get()}' $targetAttribute $clickAttribute>Open cross-site result</a>",
                )
                "/target" -> page("External app target", "Target loaded")
                else -> "<title>Fixture resource</title>"
            }
        }.use { server ->
            val sourceUrl = server.fixtureUrl("/source").replace("127.0.0.1", "localhost")
            val historySourceUrl = if (replaceSourceHistory) {
                "$sourceUrl?conversation=fixture"
            } else {
                sourceUrl
            }
            val destinationUrl = server.fixtureUrl("/target")
            targetUrl.set(destinationUrl)
            composeRule.runOnIdle {
                controller.createTab(initialUrl = sourceUrl)
                controller.attachSelectedBrowserEngineView(host)
            }
            awaitPage(sourceUrl, "External app source")
            val sourceId = composeRule.runOnIdle {
                observeSelectedSession()
                controller.selectedTabId
            }
            repeat(repetitions) { iteration ->
                callbacks.clear()
                val gate = LookupGate(destinationUrl).takeIf { delayLookup }
                lookupGate.set(gate)
                try {
                    tapPage()
                    if (gate != null) {
                        assertTrue(
                            "External app lookup did not start; ${diagnostics()}",
                            gate.started.await(5, TimeUnit.SECONDS),
                        )
                        // Let real Gecko callbacks from the denied navigation reach Candy
                        // while the package lookup still owns its captured source snapshot.
                        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                            .waitForIdle(2_000L)
                        if (replaceSourceHistory) {
                            awaitCondition(
                                "Source replaceState did not commit while lookup was blocked",
                                timeoutMillis = 5_000L,
                            ) {
                                controller.selectedTab.url == historySourceUrl
                            }
                        }
                        composeRule.runOnIdle {
                            callbacks.add("lookup blocked: ${controller.selectedTab}")
                        }
                        Log.i("GeckoExternalAppTest", "Lookup blocked; ${diagnostics()}")
                    }
                } finally {
                    gate?.release?.countDown()
                }
                if (openNewWindow) {
                    awaitCondition("Popup did not become active") {
                        controller.activeTabs.any { tab ->
                            tab.openerTabId == sourceId && tab.url == destinationUrl
                        }
                    }
                    composeRule.runOnIdle {
                        val child = controller.activeTabs.first { tab ->
                            tab.openerTabId == sourceId && tab.url == destinationUrl
                        }
                        controller.selectTab(child.id)
                        controller.attachSelectedBrowserEngineView(host)
                    }
                }
                awaitPage(destinationUrl, "External app target")
                awaitCondition("Native history did not settle") {
                    runCatching(::nativeHistoryUrls).getOrNull() == if (openNewWindow) {
                        listOf(destinationUrl)
                    } else {
                        listOf(historySourceUrl, destinationUrl)
                    }
                }
                composeRule.runOnIdle {
                    assertNull(controller.externalAppPrompt)
                    assertNull(controller.selectedTab.error)
                    if (openNewWindow) {
                        assertEquals(sourceId, controller.selectedTab.openerTabId)
                    } else {
                        assertEquals(sourceId, controller.selectedTabId)
                        assertTrue(controller.selectedTab.canGoBack)
                        if (iteration < repetitions - 1) controller.goBack()
                    }
                }
                if (iteration < repetitions - 1) {
                    awaitPage(sourceUrl, "External app source")
                }
            }
        }
    }

    private fun observeSelectedSession() {
        val session = selectedGeckoSession()
        val stateField = session.javaClass.getDeclaredField("listener").apply {
            isAccessible = true
        }
        val stateListener = stateField.get(session) as GeckoBrowserSessionStateListener
        session.setStateListener { state ->
            callbacks.add("state: $state")
            stateListener.onStateChanged(state)
        }
        val navigationField = session.javaClass.getDeclaredField("navigationRequestListener").apply {
            isAccessible = true
        }
        val navigationListener = navigationField.get(session) as GeckoNavigationRequestListener
        session.setNavigationRequestListener { request ->
            val decision = navigationListener.onNavigationRequest(request)
            navigationRequests.add(request)
            callbacks.add("navigation: $request -> $decision")
            decision
        }
    }

    private fun awaitPage(url: String, title: String) =
        awaitCondition("Page did not finish: url=$url, title=$title") {
            controller.selectedTab.let { tab ->
                tab.url == url && tab.title == title && !tab.isLoading && tab.error == null
            }
        }

    private fun awaitCondition(
        message: String,
        timeoutMillis: Long = 30_000L,
        condition: () -> Boolean,
    ) {
        try {
            composeRule.waitUntil(timeoutMillis) { composeRule.runOnIdle(condition) }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("$message; ${diagnostics()}", error)
        }
    }

    private fun tapPage() {
        awaitCondition("Gecko view did not attach") {
            controller.selectedGeckoViewForTesting()?.let { view ->
                view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0
            } == true
        }
        val presented = CountDownLatch(1)
        composeRule.runOnIdle { selectedGeckoSession().awaitContentPresented(presented::countDown) }
        assertTrue("Gecko content was not presented", presented.await(10, TimeUnit.SECONDS))
        val point = composeRule.runOnIdle {
            val view = requireNotNull(controller.selectedGeckoViewForTesting())
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            intArrayOf(location[0] + view.width / 2, location[1] + view.height / 2)
        }
        assertTrue(
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(point[0], point[1]),
        )
    }

    private fun selectedGeckoSession(): GeckoBrowserSession {
        val field = BrowserController::class.java.getDeclaredField("browserEngineSessions")
        field.isAccessible = true
        val sessions = field.get(controller) as Map<*, *>
        val adapter = requireNotNull(sessions[controller.selectedTabId])
        val sessionField = adapter.javaClass.getDeclaredField("session")
        sessionField.isAccessible = true
        return sessionField.get(adapter) as GeckoBrowserSession
    }

    private fun nativeHistoryUrls(): List<String> {
        val session = selectedGeckoSession()
        val field = session.javaClass.getDeclaredField("latestSessionState")
        field.isAccessible = true
        val history = requireNotNull(field.get(session)) as GeckoSession.HistoryDelegate.HistoryList
        return history.map { it.uri.orEmpty() }
    }

    private fun diagnostics(): String = composeRule.runOnIdle {
        val state = runCatching {
            val session = selectedGeckoSession()
            session.javaClass.getDeclaredField("state").let { field ->
                field.isAccessible = true
                field.get(session)
            }
        }.getOrNull()
        "selected=${controller.selectedTab}, nativeState=$state, " +
            "nativeHistory=${runCatching(::nativeHistoryUrls).getOrNull()}, " +
            "tabs=${controller.tabs}, callbacks=$callbacks"
    }

    private fun page(title: String, body: String): String = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title><style>html,body{margin:0;height:100%}a{position:fixed;inset:0;
        display:flex;align-items:center;justify-content:center;font-size:32px}</style></head>
        <body>$body</body></html>
    """.trimIndent()

    private class RedirectFixtureServer(
        private val page: (String, String) -> String,
        private val holdSourceLoad: Boolean = false,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "external-app-redirect-fixture").apply {
            isDaemon = true
            start()
        }
        val sourceUrl = "http://localhost:${socket.localPort}/source"
        val redirectUrl = "http://localhost:${socket.localPort}/redirect"
        val targetUrl = "http://127.0.0.1:${socket.localPort}/target"
        val targetRequests = AtomicInteger()
        val slowRequestStarted = CountDownLatch(1)
        private val releaseSourceLoad = CountDownLatch(1)
        private val workers = CopyOnWriteArrayList<Thread>()

        private fun serve() {
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (error: SocketException) {
                    if (socket.isClosed) return
                    continue
                }
                val worker = Thread({ serveConnection(connection) }, "external-app-fixture-response")
                    .apply { isDaemon = true }
                workers += worker
                worker.start()
            }
        }

        private fun serveConnection(connection: Socket) {
            connection.use { client ->
                runCatching {
                    client.soTimeout = 5_000
                    val reader = client.getInputStream().bufferedReader()
                    val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        .substringBefore('?')
                    while (!reader.readLine().isNullOrEmpty()) {
                        // Drain request headers before responding.
                    }
                    val output = client.getOutputStream()
                    if (path == "/redirect") {
                        output.write(
                            ("HTTP/1.1 302 Found\r\nLocation: $targetUrl\r\n" +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n")
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                    } else {
                        val html = when (path) {
                            "/source" -> page(
                                "External app source",
                                if (holdSourceLoad) {
                                    "<a href='$targetUrl'>Open cross-site result</a>" +
                                        "<img src='/slow.svg' width='1' height='1' alt=''>"
                                } else {
                                    "<a href='/redirect'>Open redirected result</a>"
                                },
                            )
                            "/target" -> {
                                targetRequests.incrementAndGet()
                                page("External app target", "Target loaded")
                            }
                            "/slow.svg" -> {
                                slowRequestStarted.countDown()
                                check(releaseSourceLoad.await(60, TimeUnit.SECONDS)) {
                                    "Slow source image was not released"
                                }
                                "<svg xmlns='http://www.w3.org/2000/svg' width='1' height='1'/>"
                            }
                            else -> "<title>Fixture resource</title>"
                        }
                        val body = html.toByteArray(StandardCharsets.UTF_8)
                        val contentType = if (path == "/slow.svg") "image/svg+xml" else "text/html"
                        output.write(
                            ("HTTP/1.1 200 OK\r\n" +
                                "Content-Type: $contentType; charset=utf-8\r\n" +
                                "Cache-Control: no-store\r\nContent-Length: ${body.size}\r\n" +
                                "Connection: close\r\n\r\n")
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        output.write(body)
                    }
                    output.flush()
                }
                // Gecko can close a canceled document or image connection before the response.
            }
        }

        override fun close() {
            releaseSourceLoad.countDown()
            socket.close()
            thread.join(2_000L)
            workers.forEach { worker -> worker.join(2_000L) }
        }
    }

    private class LookupGate(val url: String) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
    }
}
