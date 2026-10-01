package dev.sk2andy.materialbrowser.browser.gecko

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsRules
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GeckoSessionStateStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineFailureKind
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoHttpsOnlyNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var controller: BrowserController
    private lateinit var host: FrameLayout
    private lateinit var originalEngineKind: AndroidBrowserEngineKind
    private lateinit var originalHttpsOnlyMode: HttpsOnlyMode
    private lateinit var originalDnsSettings: DnsOverHttpsSettings
    private var originalTabs: Pair<List<BrowserTab>, String?>? = null
    private var originalHistory: List<HistoryEntry>? = null
    private var activeServer: HttpOnlyFixtureServer? = null
    private var restoreSnapshotDiagnostics: String? = null

    @Before
    fun setUp() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalHttpsOnlyMode = store.loadHttpsOnlyMode()
            originalDnsSettings = store.loadDnsOverHttpsSettings()
            originalTabs = store.loadTabs()
            originalHistory = store.loadHistory()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            store.saveHttpsOnlyMode(HttpsOnlyMode.AllTabs)
            store.saveDnsOverHttpsSettings(DnsOverHttpsRules.Default)
            val initialTab = BrowserTab(
                id = "https-only-initial-${UUID.randomUUID()}",
                lastAccessedAt = System.currentTimeMillis(),
                url = BLANK_URL,
            )
            assertTrue(store.saveTabsImmediately(listOf(initialTab), initialTab.id))
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
            if (::originalHttpsOnlyMode.isInitialized) {
                store.saveHttpsOnlyMode(originalHttpsOnlyMode)
                GeckoRuntimeOwner.getOrCreate(composeRule.activity)
                    .setHttpsOnlyMode(originalHttpsOnlyMode)
            }
            if (::originalDnsSettings.isInitialized) {
                store.saveDnsOverHttpsSettings(originalDnsSettings)
                GeckoRuntimeOwner.getOrCreate(composeRule.activity)
                    .setDnsOverHttpsSettings(originalDnsSettings)
            }
            originalTabs?.let { (tabs, selection) ->
                assertTrue(store.saveTabsImmediately(tabs, selection.orEmpty()))
            }
            originalHistory?.let { assertTrue(store.commitHistory(it)) }
        }
    }

    @Test
    fun warningShowsNativePageAndTemporaryExceptionStaysOutOfPrivateTabs() {
        fixtureServer("warning").use { server ->
            openPage(server.url)
            awaitWarning()
            composeRule.runOnIdle {
                // The browser keeps the attempted address, never the internal data URI.
                assertEquals(server.url, controller.selectedTab.url)
                assertFalse(controller.selectedTab.url.startsWith("data:"))
            }
            assertFalse(device.hasObject(By.text(contextString(R.string.https_only_warning_open_http))))
            clickLabel(R.string.https_only_warning_options)
            clickLabel(R.string.https_only_warning_open_http)
            awaitFixture(server.url)
            assertTrue(server.httpRequestCount.get() > 0)
            composeRule.runOnIdle {
                assertEquals(HttpsOnlyMode.AllTabs, controller.httpsOnlyMode)
                val runtime = GeckoRuntimeOwner.getOrCreate(composeRule.activity) as GeckoViewRuntimeHandle
                assertEquals(
                    GeckoRuntimeSettings.HTTPS_ONLY,
                    runtime.httpsOnlyModeForTesting(),
                )
                controller.reload()
            }
            awaitFixture(server.url)

            openPage(server.url, isPrivate = true)
            awaitWarning()
            composeRule.runOnIdle {
                assertTrue(controller.selectedTab.isIncognito)
                val store = BrowserSessionStore(composeRule.activity)
                assertTrue(store.flush())
                assertTrue(store.loadTabs().first.none { it.id == controller.selectedTabId })
            }
        }
    }

    @Test
    fun privateOnlyAllowsRegularHttpButWarnsInPrivateTab() {
        fixtureServer("private").use { server ->
            composeRule.runOnIdle { controller.updateHttpsOnlyMode(HttpsOnlyMode.PrivateOnly) }
            openPage(server.url)
            awaitFixture(server.url)

            openPage(server.url, isPrivate = true)
            awaitWarning()
            clickLabel(R.string.https_only_warning_options)
            clickLabel(R.string.https_only_warning_open_http)
            awaitFixture(server.url)
            composeRule.runOnIdle {
                assertTrue(controller.selectedTab.isIncognito)
                assertEquals(HttpsOnlyMode.PrivateOnly, controller.httpsOnlyMode)
            }
        }
    }

    @Test
    fun disabledModeAllowsHttpInRegularAndPrivateTabs() {
        fixtureServer("off").use { server ->
            composeRule.runOnIdle { controller.updateHttpsOnlyMode(HttpsOnlyMode.Off) }
            openPage(server.url)
            awaitFixture(server.url)
            openPage(server.url, isPrivate = true)
            awaitFixture(server.url)
        }
    }

    @Test
    fun retryAttemptsHttpsAgainAndKeepsWarningWithoutGlobalException() {
        fixtureServer("retry").use { server ->
            openPage(server.url)
            awaitWarning()
            val previousTlsAttempts = server.tlsAttemptCount.get()

            clickLabel(R.string.https_only_warning_retry)

            awaitCondition("Retry did not trigger a new TLS request and HTTPS-only failure") {
                server.tlsAttemptCount.get() > previousTlsAttempts && composeRule.runOnIdle {
                    !controller.selectedTab.isLoading &&
                        controller.selectedTab.failureKind == BrowserEngineFailureKind.HttpsOnly
                }
            }
            awaitWarning()
            composeRule.runOnIdle { assertEquals(HttpsOnlyMode.AllTabs, controller.httpsOnlyMode) }
        }
    }

    @Test
    fun appearanceConfigurationChangePreservesProtectedWarning() {
        fixtureServer("appearance").use { server ->
            openPage(server.url)
            awaitWarning()
            val previousTlsAttempts = server.tlsAttemptCount.get()

            composeRule.runOnIdle { controller.onAppearanceConfigurationChanged() }

            awaitCondition("Appearance refresh did not retry the protected HTTPS upgrade") {
                server.tlsAttemptCount.get() > previousTlsAttempts && composeRule.runOnIdle {
                    !controller.selectedTab.isLoading &&
                        controller.selectedTab.failureKind == BrowserEngineFailureKind.HttpsOnly
                }
            }
            awaitWarning()
            composeRule.runOnIdle {
                assertEquals(server.url, controller.selectedTab.url)
                assertEquals(HttpsOnlyMode.AllTabs, controller.httpsOnlyMode)
            }
            assertEquals("Appearance refresh must not open the HTTP fixture", 0, server.httpRequestCount.get())
        }
    }

    @Test
    fun modeChangeWhileWarningIsOpenKeepsRetryOnHttps() {
        fixtureServer("mode-change").use { server ->
            openPage(server.url)
            awaitWarning()
            val previousTlsAttempts = server.tlsAttemptCount.get()
            composeRule.runOnIdle { controller.updateHttpsOnlyMode(HttpsOnlyMode.Off) }

            clickLabel(R.string.https_only_warning_retry)

            awaitCondition("Retry after disabling HTTPS-only did not make a new HTTPS attempt") {
                server.tlsAttemptCount.get() > previousTlsAttempts && composeRule.runOnIdle {
                    !controller.selectedTab.isLoading &&
                        controller.selectedTab.failureKind == BrowserEngineFailureKind.Other
                }
            }
            assertEquals("Retry must never silently open the HTTP fixture", 0, server.httpRequestCount.get())
            composeRule.runOnIdle {
                assertTrue(controller.selectedTab.url.startsWith("https://"))
                assertEquals(HttpsOnlyMode.Off, controller.httpsOnlyMode)
                assertEquals(HttpsOnlyMode.Off, BrowserSessionStore(composeRule.activity).loadHttpsOnlyMode())
                val runtime = GeckoRuntimeOwner.getOrCreate(composeRule.activity) as GeckoViewRuntimeHandle
                assertEquals(GeckoRuntimeSettings.ALLOW_ALL, runtime.httpsOnlyModeForTesting())
            }
        }
    }

    @Test
    fun backFromWarningReturnsToPreviousLoadedPage() {
        fixtureServer("back").use { server ->
            openPage(server.safeUrl)
            awaitFixture(server.safeUrl)
            composeRule.runOnIdle { controller.submitAddress(server.url) }
            awaitWarning()

            clickLabel(R.string.https_only_warning_back)

            awaitFixture(server.safeUrl)
        }
    }

    @Test
    fun restoredWarningRetriesHttpUpgradeAndPreservesPreviousPage() {
        fixtureServer("restore").use { server ->
            openPage(server.safeUrl)
            awaitFixture(server.safeUrl)
            composeRule.runOnIdle { controller.submitAddress(server.url) }
            awaitWarning()
            val previousTlsAttempts = server.tlsAttemptCount.get()
            val previousHttpRequests = server.httpRequestCount.get()
            composeRule.runOnIdle { selectedNativeSession().flushSessionState() }
            awaitCondition("Gecko did not persist the committed warning history before recreation") {
                composeRule.runOnIdle {
                    controller.onPause()
                    val encodedState = GeckoSessionStateStore(composeRule.activity)
                        .load(controller.selectedTabId)?.encodedState
                    val nativeState = encodedState?.let(GeckoSession.SessionState::fromString)
                    restoreSnapshotDiagnostics = nativeState?.let { snapshot ->
                        "index=${snapshot.currentIndex}, urls=${snapshot.map { it.uri }}"
                    } ?: "no native snapshot"
                    val currentUrl = nativeState?.getOrNull(nativeState.currentIndex)?.uri
                    currentUrl == server.url || currentUrl == server.url.replaceFirst("http:", "https:")
                }
            }

            composeRule.runOnIdle {
                val selectedTabId = controller.selectedTabId
                assertEquals(server.url, controller.selectedTab.url)
                controller.onPause()
                assertTrue(BrowserSessionStore(composeRule.activity).flush())
                controller.destroy()
                val encodedState = GeckoSessionStateStore(composeRule.activity)
                    .load(selectedTabId)?.encodedState
                restoreSnapshotDiagnostics = encodedState?.let { encoded ->
                    runCatching {
                        val nativeState = requireNotNull(GeckoSession.SessionState.fromString(encoded))
                        "index=${nativeState.currentIndex}, urls=${nativeState.map { it.uri }}"
                    }.getOrElse { error -> "unreadable=${error.message}" }
                } ?: "no native snapshot"
                host.removeAllViews()
                controller = BrowserController(composeRule.activity)
                assertEquals(selectedTabId, controller.selectedTabId)
                controller.attachSelectedBrowserEngineView(host)
                controller.onResume()
            }

            awaitCondition("Restoring the warning did not trigger a new protected HTTPS attempt") {
                server.tlsAttemptCount.get() > previousTlsAttempts && composeRule.runOnIdle {
                    !controller.selectedTab.isLoading &&
                        controller.selectedTab.failureKind == BrowserEngineFailureKind.HttpsOnly
                }
            }
            awaitWarning()
            composeRule.runOnIdle { assertEquals(server.url, controller.selectedTab.url) }
            assertEquals(previousHttpRequests, server.httpRequestCount.get())

            clickLabel(R.string.https_only_warning_back)

            awaitFixture(server.safeUrl)
        }
    }

    @Test
    fun firstPageWarningBackReturnsToBlankHomeWithoutHttpException() {
        fixtureServer("home").use { server ->
            openPage(server.url)
            awaitWarning()

            clickLabel(R.string.https_only_warning_back)

            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.runOnIdle {
                    controller.selectedTab.url == BLANK_URL && !controller.selectedTab.isLoading
                }
            }
            composeRule.runOnIdle {
                assertNull(controller.selectedTab.error)
                assertEquals(HttpsOnlyMode.AllTabs, controller.httpsOnlyMode)
            }
        }
    }

    @Test
    fun warningAtFirstHistoryEntryWithForwardPageReturnsToBlankHome() {
        fixtureServer("forward").use { server ->
            composeRule.runOnIdle { controller.updateHttpsOnlyMode(HttpsOnlyMode.Off) }
            openPage(server.url)
            awaitFixture(server.url)
            composeRule.runOnIdle {
                selectedNativeSession().purgeHistory()
                // Reload publishes the navigation flags after Gecko clears its history.
                controller.reload()
            }
            awaitFixture(server.url)
            awaitCondition("Could not remove initial bootstrap history") {
                composeRule.runOnIdle {
                    !controller.selectedTab.canGoBack && !controller.selectedTab.canGoForward
                }
            }
            composeRule.runOnIdle { controller.submitAddress(server.safeUrl) }
            awaitFixture(server.safeUrl)
            // Select the exact fixture index; Gecko's normal Back may skip untouched entries.
            composeRule.runOnIdle { selectedNativeSession().gotoHistoryIndex(0) }
            awaitFixture(server.url)
            composeRule.runOnIdle {
                assertFalse(controller.selectedTab.canGoBack)
                assertTrue(controller.selectedTab.canGoForward)
                controller.updateHttpsOnlyMode(HttpsOnlyMode.AllTabs)
                controller.reload()
            }
            awaitWarning()

            clickLabel(R.string.https_only_warning_back)

            awaitCondition("First history entry with forward pages did not return home") {
                composeRule.runOnIdle {
                    controller.selectedTab.url == BLANK_URL && !controller.selectedTab.isLoading
                }
            }
            composeRule.runOnIdle {
                assertNull(controller.selectedTab.error)
                assertNull(controller.selectedTab.failureKind)
                assertEquals(HttpsOnlyMode.AllTabs, controller.httpsOnlyMode)
            }
        }
    }

    private fun openPage(url: String, isPrivate: Boolean = false) {
        composeRule.runOnIdle {
            controller.createTab(initialUrl = url, isIncognito = isPrivate)
            controller.attachSelectedBrowserEngineView(host)
        }
    }

    private fun awaitWarning() {
        val visible = device.wait(
            Until.hasObject(By.textContains(contextString(R.string.https_only_warning_title))),
            TIMEOUT_MILLIS,
        )
        assertTrue(
            "Native HTTPS-only warning did not become accessible; ${diagnostics(includeHierarchy = !visible)}",
            visible,
        )
        composeRule.runOnIdle {
            assertFalse(controller.selectedTab.isLoading)
            assertEquals(BrowserEngineFailureKind.HttpsOnly, controller.selectedTab.failureKind)
        }
    }

    private fun clickLabel(resourceId: Int) {
        val label = contextString(resourceId)
        device.waitForIdle()
        val target = requireNotNull(
            device.wait(Until.findObject(By.textContains(label).clickable(true)), TIMEOUT_MILLIS / 2)
                ?: device.wait(Until.findObject(By.descContains(label).clickable(true)), TIMEOUT_MILLIS / 2),
        ) {
            "Native warning action missing: $label; ${diagnostics(includeHierarchy = true)}"
        }
        target.click()
        instrumentation.waitForIdleSync()
        device.waitForIdle()
    }

    private fun contextString(resourceId: Int): String =
        composeRule.activity.getString(resourceId)

    private fun awaitFixture(url: String) {
        awaitCondition("HTTP fixture did not finish loading") {
            composeRule.runOnIdle {
                controller.selectedTab.let { tab ->
                    tab.url == url && tab.title == FIXTURE_TITLE && !tab.isLoading
                }
            }
        }
        composeRule.runOnIdle { assertNull(controller.selectedTab.error) }
        assertFalse(device.hasObject(By.text(contextString(R.string.https_only_warning_title))))
    }

    private fun fixtureServer(name: String): HttpOnlyFixtureServer = HttpOnlyFixtureServer(name).also {
        activeServer = it
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("$message; ${diagnostics()}", condition())
    }

    private fun selectedNativeSession(): GeckoSession = requireNotNull(
        requireNotNull(controller.selectedGeckoViewForTesting()).findGeckoView().session,
    )

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this !is ViewGroup) error("GeckoView descendant is missing")
        for (index in 0 until childCount) {
            runCatching { getChildAt(index).findGeckoView() }.getOrNull()?.let { return it }
        }
        error("GeckoView descendant is missing")
    }

    private fun diagnostics(includeHierarchy: Boolean = false): String {
        val browserState = composeRule.runOnIdle {
            val tab = controller.selectedTab
            val view = controller.selectedGeckoViewForTesting()
            val runtime = GeckoRuntimeOwner.getOrCreate(composeRule.activity) as GeckoViewRuntimeHandle
            "url=${tab.url.take(180)}, title=${tab.title}, loading=${tab.isLoading}, " +
                "error=${tab.error}, kind=${tab.failureKind}, progress=${tab.progress}, " +
                "mode=${controller.httpsOnlyMode}/${runtime.httpsOnlyModeForTesting()}, " +
                "view=${view?.width}x${view?.height}, attached=${view?.isAttachedToWindow}"
        }
        val hierarchy = if (includeHierarchy) {
            runCatching {
                val file = File(composeRule.activity.cacheDir, "https-only-failure-hierarchy.xml")
                device.dumpWindowHierarchy(file)
                "\nhierarchy=${file.readText().take(16_000)}"
            }.getOrElse { error -> "\nhierarchy unavailable: ${error.message}" }
        } else {
            ""
        }
        return "$browserState; ${activeServer?.diagnostics()}; restore=$restoreSnapshotDiagnostics$hierarchy"
    }

    private class HttpOnlyFixtureServer(name: String) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val hostname = "$name.candy-https.test"
        val url = "http://$hostname:${socket.localPort}/fixture"
        val safeUrl = "http://localhost:${socket.localPort}/fixture"
        val httpRequestCount = AtomicInteger()
        val tlsAttemptCount = AtomicInteger()
        private val requestLog = CopyOnWriteArrayList<String>()
        private val thread = Thread(::serve, "https-only-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                val connection = runCatching(socket::accept).getOrNull() ?: return
                connection.use { client ->
                    runCatching {
                        client.soTimeout = 1_000
                        val input = client.getInputStream()
                        // Close TLS handshakes immediately: this fixture supports HTTP only.
                        val firstByte = input.read()
                        requestLog += "first-byte=$firstByte"
                        if (firstByte != 'G'.code) {
                            if (firstByte == TLS_HANDSHAKE_CONTENT_TYPE) tlsAttemptCount.incrementAndGet()
                            return@runCatching
                        }
                        val reader = input.bufferedReader(StandardCharsets.US_ASCII)
                        val request = reader.readLine() ?: return@runCatching
                        requestLog += "request=G$request"
                        if (!request.startsWith("ET /fixture ")) return@runCatching
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain request headers before responding.
                        }
                        httpRequestCount.incrementAndGet()
                        val body = "<!doctype html><title>$FIXTURE_TITLE</title><h1>$FIXTURE_TITLE</h1>"
                            .toByteArray(StandardCharsets.UTF_8)
                        client.getOutputStream().buffered().apply {
                            write(
                                (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html; charset=utf-8\r\n" +
                                        "Content-Length: ${body.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII),
                            )
                            write(body)
                            flush()
                        }
                    }.onFailure { error -> requestLog += "failure=${error.javaClass.simpleName}: ${error.message}" }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(1_000)
        }

        fun diagnostics(): String =
            "http=${httpRequestCount.get()}, tls=${tlsAttemptCount.get()}, requests=${requestLog.takeLast(16)}"
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val FIXTURE_TITLE = "Candy HTTP-only fixture"
        const val TLS_HANDSHAKE_CONTENT_TYPE = 22

        private var fixtureConfigPath: String? = null
        private var fixtureConfigNeedsRestore = false
        private val fixtureDomains = listOf(
            "warning", "private", "off", "retry", "back", "home", "forward",
            "mode-change", "restore", "appearance",
        )
            .joinToString(",") { name -> "$name.candy-https.test" }

        @JvmStatic
        @BeforeClass
        fun configureLocalFixtureDnsBeforeGeckoStarts() {
            assertFalse(
                "Run this suite in its own instrumentation process so Gecko can read fixture DNS settings",
                GeckoRuntimeOwner.hasRuntime(),
            )
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val path = "/data/local/tmp/${context.packageName}-geckoview-config.yaml"
            val backupPath = "$path.https-only-fixture-backup"
            val result = shell(
                "if [ -e ${shellQuote(backupPath)} ]; then printf 'BACKUP_EXISTS'; " +
                    "elif [ -e ${shellQuote(path)} ]; then " +
                    "mv ${shellQuote(path)} ${shellQuote(backupPath)} && printf 'BACKED_UP'; " +
                    "else printf 'NO_EXISTING_CONFIG'; fi",
            )
            assertTrue(
                "Cannot safely prepare Gecko fixture config: $result",
                result == "BACKED_UP" || result == "NO_EXISTING_CONFIG",
            )
            fixtureConfigPath = path
            fixtureConfigNeedsRestore = true
            val yaml = "prefs:\n  network.dns.localDomains: \"$fixtureDomains\"\n"
            assertEquals(
                "CONFIGURED",
                shell(
                    "printf '%s' ${shellQuote(yaml)} > ${shellQuote(path)} && " +
                        "chmod 644 ${shellQuote(path)} && printf 'CONFIGURED'",
                ),
            )
        }

        @JvmStatic
        @AfterClass
        fun restoreOriginalGeckoDebugConfig() {
            val path = fixtureConfigPath ?: return
            if (!fixtureConfigNeedsRestore) return
            val backupPath = "$path.https-only-fixture-backup"
            assertEquals(
                "RESTORED",
                shell(
                    "rm -f ${shellQuote(path)} && " +
                        "if [ -e ${shellQuote(backupPath)} ]; then " +
                        "mv ${shellQuote(backupPath)} ${shellQuote(path)}; fi && printf 'RESTORED'",
                ),
            )
            fixtureConfigNeedsRestore = false
        }

        private fun shell(command: String): String {
            // UiAutomation tokenizes command strings; send shell syntax through stdin instead.
            val descriptors = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommandRw("sh")
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).bufferedWriter().use { input ->
                    input.write("exec sh -c ${shellQuote(command)} 2>&1\n")
                }
                return ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).bufferedReader().use {
                    it.readText().trim()
                }
            } finally {
                descriptors.forEach { it.close() }
            }
        }

        private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
