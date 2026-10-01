package dev.sk2andy.materialbrowser.browser.gecko

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
import dev.sk2andy.materialbrowser.browser.PrivacySignalSettings
import dev.sk2andy.materialbrowser.browser.RootTabBackDecision
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoPopupNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: BrowserController
    private lateinit var host: FrameLayout
    private lateinit var originalEngineKind: AndroidBrowserEngineKind
    private var originalTabs: Pair<List<BrowserTab>, String?>? = null
    private var originalHistory: List<HistoryEntry>? = null
    private var originalPrivacySignalSettings: PrivacySignalSettings? = null
    private var originalExternalAppLinkHandling: ExternalAppLinkHandling? = null

    @Before
    fun setUp() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalTabs = store.loadTabs()
            originalHistory = store.loadHistory()
            originalPrivacySignalSettings = store.loadPrivacySignalSettings()
            originalExternalAppLinkHandling = store.loadExternalAppLinkHandling()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            store.saveExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)
            store.savePrivacySignalSettings(
                PrivacySignalSettings(doNotTrackEnabled = true, globalPrivacyControlEnabled = true),
            )
            controller = BrowserController(composeRule.activity)
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
        composeRule.runOnIdle {
            if (::controller.isInitialized) controller.destroy()
            val store = BrowserSessionStore(composeRule.activity)
            if (::originalEngineKind.isInitialized) {
                assertTrue(store.saveAndroidBrowserEngineKind(originalEngineKind))
            }
            originalTabs?.let { (tabs, selection) ->
                assertTrue(store.saveTabsImmediately(tabs, selection.orEmpty()))
            }
            originalHistory?.let { assertTrue(store.commitHistory(it)) }
            originalPrivacySignalSettings?.let(store::savePrivacySignalSettings)
            originalExternalAppLinkHandling?.let(store::saveExternalAppLinkHandling)
        }
    }

    @Test
    fun firstPageDoesNotKeepPrivacyBootstrapInNativeHistory() {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        fixtureServer(requests).use { server ->
            val firstUrl = server.fixtureUrl("/first")
            val secondUrl = server.fixtureUrl("/second")
            openPage(firstUrl, "Popup first")
            awaitNativeHistory(firstUrl, listOf(firstUrl))
            composeRule.runOnIdle {
                assertEquals(listOf(firstUrl), nativeHistoryUrls())
                assertFalse(controller.selectedTab.canGoBack)
                controller.submitAddress(secondUrl)
            }
            awaitPage(secondUrl, "Popup second")
            awaitNativeHistory(secondUrl, listOf(firstUrl, secondUrl), canGoBack = true)
            composeRule.runOnIdle {
                assertEquals(listOf(firstUrl, secondUrl), nativeHistoryUrls())
                assertTrue(controller.selectedTab.canGoBack)
                controller.goBack()
            }
            awaitPage(firstUrl, "Popup first")
            awaitNativeHistory(firstUrl, listOf(firstUrl, secondUrl), canGoForward = true)
            composeRule.runOnIdle {
                assertFalse(controller.selectedTab.canGoBack)
                assertTrue(controller.selectedTab.canGoForward)
            }
            reloadAndAwaitRequest(firstUrl, "Popup first", requests, "/first")
            composeRule.runOnIdle {
                assertEquals(listOf(firstUrl, secondUrl), nativeHistoryUrls())
                assertFalse(controller.selectedTab.canGoBack)
                controller.goForward()
            }
            awaitPage(secondUrl, "Popup second")
        }
    }

    @Test
    fun firstPageInManualNewTabRootBackClosesTabAndRequestsOverview() {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        fixtureServer(requests).use { server ->
            val firstUrl = server.fixtureUrl("/first")
            openManualTabPage(firstUrl, "Popup first")
            awaitNativeHistory(firstUrl, listOf(firstUrl))
            composeRule.runOnIdle {
                val tabId = controller.selectedTabId
                assertTrue(controller.activeTabs.size > 1)
                assertFalse(controller.selectedTab.canGoBack)
                assertEquals(
                    RootTabBackDecision.CloseAndShowTabOverview,
                    controller.performSelectedRootTabBack(),
                )
                assertTrue(controller.tabs.none { it.id == tabId })
            }
        }
    }

    @Test
    fun firstPageInSoleManualTabRootBackDelegatesToSystemAndKeepsTab() {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        fixtureServer(requests).use { server ->
            val firstUrl = server.fixtureUrl("/first")
            openManualTabPage(firstUrl, "Popup first")
            awaitNativeHistory(firstUrl, listOf(firstUrl))
            composeRule.runOnIdle {
                val tabId = controller.selectedTabId
                controller.activeTabs.filter { it.id != tabId }.forEach { tab ->
                    controller.setTabPinned(tab.id, false)
                    controller.closeTab(tab.id)
                }
                assertEquals(listOf(tabId), controller.activeTabs.map { it.id })
                assertFalse(controller.selectedTab.canGoBack)
                assertEquals(
                    RootTabBackDecision.DelegateToSystem,
                    controller.performSelectedRootTabBack(),
                )
                assertEquals(tabId, controller.selectedTabId)
                assertTrue(controller.tabs.any { it.id == tabId })
            }
        }
    }

    @Test
    fun targetBlankPopupKeepsRealHistoryAfterBackAndReload() {
        verifyPopupNavigation(isPrivate = false)
    }

    @Test
    fun targetBlankAutomaticFallbackKeepsGetHistoryAndRootBackWorking() {
        composeRule.runOnIdle {
            controller.updateExternalAppLinkHandling(ExternalAppLinkHandling.Automatic)
        }
        verifyPopupNavigation(isPrivate = false)
    }

    @Test
    fun privateTargetBlankPopupKeepsNavigationOutOfPersistentStorage() {
        verifyPopupNavigation(isPrivate = true)
    }

    @Test
    fun targetBlankPostPopupPreservesOriginalBodyReferrerAndOpener() {
        PostPopupFixtureServer().use { server ->
            val openerUrl = server.url("/form-opener")
            val popupUrl = server.url("/form-popup")
            openPage(openerUrl, "POST opener")
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            openPopupChild(openerId, popupUrl, "POST popup with opener")
            val request = requireNotNull(server.popupRequest.get())
            assertEquals("POST", request.method)
            assertEquals("payload=candy-post-fixture", request.body)
            assertEquals(openerUrl, request.referrer)
            assertEquals("1", request.doNotTrack)
            assertEquals("1", request.globalPrivacyControl)
            composeRule.runOnIdle {
                assertEquals(listOf(popupUrl), nativeHistoryUrls())
                assertFalse(controller.selectedTab.canGoBack)
                assertNull(controller.selectedTab.error)
            }
        }
    }

    @Test
    fun targetBlankPopupImmediatePushStateKeepsBothRealHistoryEntries() {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        fixtureServer(requests).use { server ->
            val firstUrl = server.fixtureUrl("/first-hash")
            val pushedUrl = server.fixtureUrl("/first-pushed")
            openPage(server.fixtureUrl("/opener-hash"), "Popup hash opener")
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            openPopupChild(
                openerId,
                pushedUrl,
                "Popup pushed",
                historyUrls = listOf(firstUrl, pushedUrl),
                canGoBack = true,
            )
            composeRule.runOnIdle {
                assertEquals(listOf(firstUrl, pushedUrl), nativeHistoryUrls())
                assertTrue(controller.selectedTab.canGoBack)
                // Gecko's user Back skips untouched script-created entries. Verify the
                // preserved entry directly without changing that native protection policy.
                selectedSession().goToHistoryIndex(0)
            }
            awaitPage(firstUrl, "Popup hash first")
            awaitNativeHistory(firstUrl, listOf(firstUrl, pushedUrl), canGoForward = true)
            composeRule.runOnIdle {
                assertEquals(listOf(firstUrl, pushedUrl), nativeHistoryUrls())
                assertFalse(controller.selectedTab.canGoBack)
                assertTrue(controller.selectedTab.canGoForward)
                assertNull(controller.selectedTab.error)
            }
            assertEquals(1, requestCount(requests, "/first-hash"))
            assertEquals(0, requestCount(requests, "/first-pushed"))
        }
    }

    @Test
    fun repeatedDelayedTargetBlankPopupsKeepBackAndReloadWorking() {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        fixtureServer(requests, responseDelayMillis = 150).use { server ->
            val openerUrl = server.fixtureUrl("/opener")
            val firstUrl = server.fixtureUrl("/first")
            val secondUrl = server.fixtureUrl("/second")
            openPage(openerUrl, "Popup opener")
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            repeat(3) {
                val popupId = openPopupChild(openerId, firstUrl, "Popup first")
                tapPage()
                awaitPage(secondUrl, "Popup second")
                awaitNativeHistory(secondUrl, listOf(firstUrl, secondUrl), canGoBack = true)
                composeRule.runOnIdle { controller.goBack() }
                awaitPage(firstUrl, "Popup first")
                awaitNativeHistory(firstUrl, listOf(firstUrl, secondUrl), canGoForward = true)
                reloadAndAwaitRequest(firstUrl, "Popup first", requests, "/first")
                awaitNativeHistory(firstUrl, listOf(firstUrl, secondUrl), canGoForward = true)
                composeRule.runOnIdle {
                    assertNull(controller.selectedTab.error)
                    assertEquals(
                        RootTabBackDecision.CloseAndReturnToOpener,
                        controller.performSelectedRootTabBack(),
                    )
                    assertEquals(openerId, controller.selectedTabId)
                    assertTrue(controller.tabs.none { it.id == popupId })
                    controller.attachSelectedBrowserEngineView(host)
                }
                awaitPage(openerUrl, "Popup opener")
            }
        }
    }

    private fun verifyPopupNavigation(isPrivate: Boolean) {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        fixtureServer(requests).use { server ->
            val openerUrl = server.fixtureUrl("/opener")
            val firstUrl = server.fixtureUrl("/first")
            val secondUrl = server.fixtureUrl("/second")
            openPage(openerUrl, "Popup opener", isPrivate)
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            val popupId = openPopupChild(openerId, firstUrl, "Popup first")
            composeRule.runOnIdle {
                val opener = controller.tabs.first { it.id == openerId }
                assertEquals(openerId, controller.selectedTab.openerTabId)
                assertEquals(opener.profileId, controller.selectedTab.profileId)
                assertEquals(isPrivate, controller.selectedTab.isIncognito)
                assertEquals(listOf(firstUrl), nativeHistoryUrls())
                assertFalse(controller.selectedTab.canGoBack)
                assertNull(controller.externalAppPrompt)
            }
            assertTrue("Popup never requested first document", requestCount(requests, "/first") > 0)
            tapPage()
            awaitPage(secondUrl, "Popup second")
            awaitNativeHistory(secondUrl, listOf(firstUrl, secondUrl), canGoBack = true)
            composeRule.runOnIdle {
                assertEquals(listOf(firstUrl, secondUrl), nativeHistoryUrls())
                assertTrue(controller.selectedTab.canGoBack)
                controller.goBack()
            }
            awaitPage(firstUrl, "Popup first")
            awaitNativeHistory(firstUrl, listOf(firstUrl, secondUrl), canGoForward = true)
            composeRule.runOnIdle {
                assertFalse(controller.selectedTab.canGoBack)
                assertTrue(controller.selectedTab.canGoForward)
            }
            reloadAndAwaitRequest(firstUrl, "Popup first", requests, "/first")
            awaitNativeHistory(firstUrl, listOf(firstUrl, secondUrl), canGoForward = true)
            composeRule.runOnIdle {
                assertEquals(listOf(firstUrl, secondUrl), nativeHistoryUrls())
                assertNull(controller.selectedTab.error)
                if (isPrivate) {
                    val store = BrowserSessionStore(composeRule.activity)
                    assertTrue(store.flush())
                    assertTrue(store.loadTabs().first.none { it.id == openerId || it.id == popupId })
                    assertTrue(store.loadHistory().none {
                        it.url == openerUrl || it.url == firstUrl || it.url == secondUrl
                    })
                }
                assertEquals(
                    RootTabBackDecision.CloseAndReturnToOpener,
                    controller.performSelectedRootTabBack(),
                )
                assertEquals(openerId, controller.selectedTabId)
                assertTrue(controller.tabs.none { it.id == popupId })
                controller.attachSelectedBrowserEngineView(host)
            }
            awaitPage(openerUrl, "Popup opener")
            reloadAndAwaitRequest(openerUrl, "Popup opener", requests, "/opener")
            awaitNativeHistory(openerUrl, listOf(openerUrl))
            composeRule.runOnIdle {
                assertEquals(listOf(openerUrl), nativeHistoryUrls())
                assertFalse(controller.selectedTab.canGoBack)
            }
        }
    }

    private fun openPopupChild(
        openerId: String,
        url: String,
        title: String,
        historyUrls: List<String> = listOf(url),
        canGoBack: Boolean = false,
    ): String {
        tapPage()
        var childId: String? = null
        try {
            composeRule.waitUntil(30_000) {
                composeRule.runOnIdle {
                    childId = controller.activeTabs.firstOrNull { tab ->
                        tab.openerTabId == openerId && tab.url == url
                    }?.id
                    childId != null
                }
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError(
                "Popup did not become active: opener=$openerId, url=$url; ${popupDiagnostics()}",
                error,
            )
        }
        val popupId = requireNotNull(childId)
        composeRule.runOnIdle {
            controller.selectTab(popupId)
            controller.attachSelectedBrowserEngineView(host)
        }
        awaitPage(url, title)
        awaitNativeHistory(url, historyUrls, canGoBack = canGoBack)
        return popupId
    }

    private fun popupDiagnostics(): String = composeRule.runOnIdle {
        val field = BrowserController::class.java.getDeclaredField("browserEngineSessions")
        field.isAccessible = true
        val sessions = field.get(controller) as Map<*, *>
        val states = sessions.entries.joinToString { (tabId, adapter) ->
            val details = runCatching {
                requireNotNull(adapter)
                val sessionField = adapter.javaClass.getDeclaredField("session")
                sessionField.isAccessible = true
                val session = requireNotNull(sessionField.get(adapter))
                val stateField = session.javaClass.getDeclaredField("state")
                stateField.isAccessible = true
                val historyField = session.javaClass.getDeclaredField("latestSessionState")
                historyField.isAccessible = true
                val history = historyField.get(session) as? GeckoSession.HistoryDelegate.HistoryList
                "type=${session.javaClass.simpleName}, state=${stateField.get(session)}, " +
                    "history=${history?.map { it.uri.orEmpty() }}"
            }.getOrElse { error -> "unavailable=$error" }
            "$tabId: $details"
        }
        "selectedId=${controller.selectedTabId}, tabs=${controller.tabs}, " +
            "activeTabs=${controller.activeTabs}, externalAppPrompt=${controller.externalAppPrompt}, " +
            "contentTarget=${controller.contentActions.target}, sessions=[$states]"
    }

    private fun openPage(url: String, title: String, isPrivate: Boolean = false) {
        composeRule.runOnIdle {
            controller.createTab(initialUrl = url, isIncognito = isPrivate)
            controller.attachSelectedBrowserEngineView(host)
        }
        awaitPage(url, title)
    }

    private fun openManualTabPage(url: String, title: String) {
        composeRule.runOnIdle {
            controller.createTab()
            controller.attachSelectedBrowserEngineView(host)
            controller.submitAddress(url)
        }
        awaitPage(url, title)
    }

    private fun awaitPage(url: String, title: String) {
        try {
            composeRule.waitUntil(30_000) {
                composeRule.runOnIdle {
                    controller.selectedTab.let { tab ->
                        tab.url == url && tab.title == title && !tab.isLoading && tab.error == null
                    }
                }
            }
        } catch (error: ComposeTimeoutException) {
            val diagnostics = composeRule.runOnIdle {
                val session = selectedGeckoSession()
                val nativeState = runCatching {
                    session.javaClass.getDeclaredField("state").let { field ->
                        field.isAccessible = true
                        field.get(session)
                    }
                }.getOrNull()
                "selected=${controller.selectedTab}, nativeState=$nativeState, " +
                    "nativeHistory=${runCatching(::nativeHistoryUrls).getOrNull()}, " +
                    "tabs=${controller.tabs}"
            }
            throw AssertionError("Page did not finish: url=$url, title=$title; $diagnostics", error)
        }
    }

    private fun awaitNativeHistory(
        url: String,
        urls: List<String>,
        canGoBack: Boolean = false,
        canGoForward: Boolean = false,
    ) {
        try {
            composeRule.waitUntil(30_000) {
                composeRule.runOnIdle {
                    selectedSession().historyUrlAtOffset(0) == url &&
                        runCatching(::nativeHistoryUrls).getOrNull() == urls &&
                        controller.selectedTab.canGoBack == canGoBack &&
                        controller.selectedTab.canGoForward == canGoForward
                }
            }
        } catch (error: ComposeTimeoutException) {
            val actual = composeRule.runOnIdle {
                "tab=${controller.selectedTab}, " +
                    "nativeHistory=${runCatching(::nativeHistoryUrls).getOrNull()}"
            }
            throw AssertionError("Native history did not settle: expected=$urls, current=$url; $actual", error)
        }
    }

    private fun reloadAndAwaitRequest(
        url: String,
        title: String,
        requests: ConcurrentHashMap<String, AtomicInteger>,
        path: String,
    ) {
        val before = requestCount(requests, path)
        composeRule.runOnIdle { controller.reload() }
        composeRule.waitUntil(30_000) { requestCount(requests, path) > before }
        awaitPage(url, title)
    }

    private fun tapPage() {
        composeRule.waitUntil(10_000) {
            composeRule.runOnIdle {
                controller.selectedGeckoViewForTesting()?.let { view ->
                    view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0
                } == true
            }
        }
        val clickPoint = composeRule.runOnIdle {
            val view = requireNotNull(controller.selectedGeckoViewForTesting())
            assertTrue(view.isAttachedToWindow && view.isShown)
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            intArrayOf(location[0] + view.width / 2, location[1] + view.height / 2)
        }
        assertTrue(
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .click(clickPoint[0], clickPoint[1]),
        )
    }

    private fun selectedSession(): AndroidBrowserEngineSessionPort {
        val field = BrowserController::class.java.getDeclaredField("browserEngineSessions")
        field.isAccessible = true
        val sessions = field.get(controller) as Map<*, *>
        return sessions[controller.selectedTabId] as AndroidBrowserEngineSessionPort
    }

    private fun nativeHistoryUrls(): List<String> {
        val session = selectedGeckoSession()
        val field = session.javaClass.getDeclaredField("latestSessionState")
        field.isAccessible = true
        val history = requireNotNull(field.get(session)) as GeckoSession.HistoryDelegate.HistoryList
        return history.map { it.uri.orEmpty() }
    }

    private fun selectedGeckoSession(): GeckoBrowserSession {
        val adapter = selectedSession()
        val field = adapter.javaClass.getDeclaredField("session")
        field.isAccessible = true
        return field.get(adapter) as GeckoBrowserSession
    }

    private fun fixtureServer(
        requests: ConcurrentHashMap<String, AtomicInteger>,
        responseDelayMillis: Long = 0,
    ): EdgeToEdgeSiteFixtureServer = EdgeToEdgeSiteFixtureServer { target ->
        val path = target.substringBefore('?')
        requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
        if (responseDelayMillis > 0 && (path == "/first" || path == "/second")) {
            Thread.sleep(responseDelayMillis)
        }
        when (path) {
            "/opener" -> page("Popup opener", "<a href='/first' target='_blank'>Open popup</a>")
            "/opener-hash" -> page(
                "Popup hash opener",
                "<a href='/first-hash' target='_blank'>Open popup</a>",
            )
            "/first-hash" -> page(
                "Popup hash first",
                """
                    <script>
                    window.addEventListener('popstate', () => {
                        document.title = location.pathname === '/first-hash'
                            ? 'Popup hash first' : 'Popup pushed';
                    });
                    history.pushState({}, '', '/first-pushed');
                    document.title = 'Popup pushed';
                    </script>
                    Popup pushState loaded
                """.trimIndent(),
            )
            "/first" -> page("Popup first", "<a href='/second'>Next page</a>")
            "/second" -> page("Popup second", "Second page loaded")
            else -> "<title>Fixture resource</title>"
        }
    }

    private fun requestCount(requests: ConcurrentHashMap<String, AtomicInteger>, path: String): Int =
        requests[path]?.get() ?: 0

    private fun page(title: String, body: String): String = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title><style>html,body{margin:0;height:100%}a{position:fixed;inset:0;
        display:flex;align-items:center;justify-content:center;font-size:32px}</style></head>
        <body>$body</body></html>
    """.trimIndent()

    private class PostPopupFixtureServer : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val popupRequest = AtomicReference<PostRequest>()
        private val thread = Thread(::serve, "gecko-post-popup-fixture").apply {
            isDaemon = true
            start()
        }

        fun url(path: String): String = "http://127.0.0.1:${server.localPort}$path"

        private fun serve() {
            while (!server.isClosed) {
                try {
                    server.accept().use { connection ->
                        val reader = connection.getInputStream()
                            .bufferedReader(StandardCharsets.US_ASCII)
                        val requestLine = reader.readLine().orEmpty().split(' ')
                        val method = requestLine.getOrNull(0).orEmpty()
                        val path = requestLine.getOrNull(1).orEmpty()
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            headers[header.substringBefore(':').lowercase()] =
                                header.substringAfter(':').trim()
                        }
                        val bodyLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val requestBody = StringBuilder()
                        repeat(bodyLength) {
                            val char = reader.read()
                            if (char >= 0) requestBody.append(char.toChar())
                        }
                        val body = when (path) {
                            "/form-opener" -> """
                                <!doctype html><html><head>
                                <meta name="viewport" content="width=device-width,initial-scale=1">
                                <title>POST opener</title>
                                <style>button{position:fixed;inset:0;font-size:32px}</style>
                                </head><body>
                                <form action="/form-popup" method="post" target="_blank" rel="opener">
                                <input type="hidden" name="payload" value="candy-post-fixture">
                                <button type="submit">Open POST popup</button></form>
                                </body></html>
                            """.trimIndent()
                            "/form-popup" -> {
                                popupRequest.set(
                                    PostRequest(
                                        method = method,
                                        body = requestBody.toString(),
                                        referrer = headers["referer"],
                                        doNotTrack = headers["dnt"],
                                        globalPrivacyControl = headers["sec-gpc"],
                                    ),
                                )
                                """
                                    <!doctype html><html><head><title>POST popup missing context</title>
                                    </head><body><script>
                                    if (window.opener && window.opener.location.pathname === '/form-opener'
                                        && document.referrer === '${url("/form-opener")}') {
                                        document.title = 'POST popup with opener';
                                    }
                                    </script>POST popup loaded</body></html>
                                """.trimIndent()
                            }
                            else -> "<title>POST fixture resource</title>"
                        }.toByteArray(StandardCharsets.UTF_8)
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Cache-Control: no-store\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (server.isClosed) return
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(2_000L)
        }
    }

    private data class PostRequest(
        val method: String,
        val body: String,
        val referrer: String?,
        val doNotTrack: String?,
        val globalPrivacyControl: String?,
    )
}
