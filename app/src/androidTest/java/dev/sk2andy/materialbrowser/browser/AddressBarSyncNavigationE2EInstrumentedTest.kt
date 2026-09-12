package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncCacheStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncVaultStore
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import dev.sk2andy.materialbrowser.sync.SyncTab
import dev.sk2andy.materialbrowser.ui.AddressBarTestTags
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarSyncNavigationE2EInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store by lazy { BrowserSessionStore(context) }

    @Before
    fun setUp() {
        clearState()
        GestureOnboardingStore(context).markCompleted()
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        store.saveStartupAnimationEnabled(false)
        store.saveOpenHomeOnStartupEnabled(false)
        store.saveExternalLinkPreviewEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
    }

    @After
    fun tearDown() = clearState()

    @Test
    fun addressBarSearchStaysVisibleWhenStaleSyncSnapshotArrivesAfterRealGeckoLoad() {
        SearchFixtureServer().use { server ->
            store.saveSearchEngine(SearchEngine.SearXNG)
            store.saveSearxngSettings(
                SearxngSettings(
                    instanceUrl = server.baseUrl,
                    suggestionFallback = SearchSuggestionProvider.None,
                ),
            )
            val initialTab = BrowserTab(
                id = INITIAL_TAB_ID,
                lastAccessedAt = System.currentTimeMillis(),
                url = server.oldUrl,
                syncCandyId = CANDY_ID,
            )
            assertTrue(store.saveTabsImmediately(listOf(initialTab), initialTab.id))

            ActivityScenario.launch<MainActivity>(mainActivityIntent()).use { scenario ->
                awaitController(scenario, "Old Gecko page did not load") { controller ->
                    controller.selectedTabForTesting().let { tab ->
                        tab.url == server.oldUrl &&
                            tab.title == OLD_TITLE &&
                            !tab.isLoading
                    } && controller.selectedGeckoViewForTesting()?.isAttachedToWindow == true
                }
                assertEquals(1, server.oldPageRequests.get())

                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().applySyncRepositoryStateForTesting(
                        syncState(localTab = syncTab(server.oldUrl, OLD_TITLE, CANDY_ID)),
                    )
                }
                SystemClock.sleep(SYNC_SETTLE_MILLIS)

                composeRule.onNodeWithText(AddressResolver.displayText(server.oldUrl))
                    .assertIsDisplayed()
                    .performClick()
                val searchEditor = composeRule.onNodeWithTag(AddressBarTestTags.Editor)
                    .assertIsDisplayed()
                searchEditor.performTextReplacement(SEARCH_QUERY)
                searchEditor.performImeAction()

                awaitController(scenario, "Search result did not load in Gecko") { controller ->
                    controller.selectedTabForTesting().let { tab ->
                        tab.url == server.searchUrl &&
                            tab.title == SEARCH_TITLE &&
                            !tab.isLoading
                    } && controller.selectedGeckoViewForTesting()?.isAttachedToWindow == true
                }
                assertTrue(server.searchPageRequests.get() >= 1)
                val oldPageRequestsBeforeStaleSync = server.oldPageRequests.get()

                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().applySyncRepositoryStateForTesting(
                        syncState(localTab = syncTab(server.oldUrl, OLD_TITLE, CANDY_ID)),
                    )
                }
                SystemClock.sleep(STALE_SYNC_OBSERVATION_MILLIS)

                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    val selectedTab = controller.selectedTabForTesting()
                    assertEquals(server.searchUrl, selectedTab.url)
                    assertEquals(SEARCH_TITLE, selectedTab.title)
                    assertTrue(!selectedTab.isLoading)
                    assertNotNull(controller.selectedGeckoViewForTesting())
                }
                assertEquals(oldPageRequestsBeforeStaleSync, server.oldPageRequests.get())
            }
        }
    }

    private fun awaitController(
        scenario: ActivityScenario<MainActivity>,
        message: String,
        condition: (BrowserController) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var result = false
            scenario.onActivity { activity ->
                result = condition(activity.browserControllerForTesting())
            }
            if (result) return
            SystemClock.sleep(POLL_MILLIS)
        }
        var finalResult = false
        scenario.onActivity { activity ->
            finalResult = condition(activity.browserControllerForTesting())
        }
        assertTrue(message, finalResult)
    }

    private fun syncState(localTab: SyncTab? = null): SyncRepositoryState = SyncRepositoryState(
        settings = SyncConnectionSettings(
            endpoint = "https://sync.example",
            username = "user",
            deviceName = "Android",
            iconCatalogId = "phone",
            iconAccentHue = 120,
            localProfileId = DEFAULT_PROFILE_ID,
        ),
        status = SyncStatus.Ready,
        profiles = listOf(
            SyncProfile(
                deviceId = ANDROID_DEVICE_ID,
                displayName = "Android",
                icon = SyncDeviceIconDescriptor("phone", 120),
                revision = 0,
                tabs = listOfNotNull(localTab),
                lastSeenAt = NOW,
            ),
        ),
        pendingCount = 0,
        lastCursor = "epoch.0",
        lastSuccessAt = NOW,
        currentDeviceId = ANDROID_DEVICE_ID,
    )

    private fun syncTab(
        url: String,
        title: String,
        candyId: String,
    ) = SyncTab(
        candyId = candyId,
        windowId = 0,
        index = 0,
        groupId = null,
        active = true,
        pinned = false,
        title = title,
        url = url,
    )

    private fun mainActivityIntent(): Intent = Intent(context, MainActivity::class.java)
        .setAction(TEST_ACTIVITY_ACTION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun clearState() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
            ReleaseNotesStore.PREFERENCES_NAME,
            "candy_sync_settings",
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        AndroidSyncVaultStore(context).clear()
        AndroidSyncCacheStore(context).clear()
    }

    private class SearchFixtureServer : Closeable {
        private val server = ServerSocket(0, REQUEST_BACKLOG, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "address-sync-e2e-fixture").apply {
            isDaemon = true
            start()
        }

        val baseUrl = "http://127.0.0.1:${server.localPort}"
        val oldUrl = "$baseUrl/old"
        val searchUrl = "$baseUrl/search?q=candy%20sync%20race"
        val oldPageRequests = AtomicInteger()
        val searchPageRequests = AtomicInteger()

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        val reader = connection.getInputStream().bufferedReader()
                        val requestTarget = reader.readLine()
                            ?.split(' ')
                            ?.getOrNull(1)
                            .orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain headers before serving the deterministic local response.
                        }
                        val response = when {
                            requestTarget == "/old" -> {
                                oldPageRequests.incrementAndGet()
                                htmlResponse(OLD_TITLE, "Initial page")
                            }
                            requestTarget.startsWith("/search?") -> {
                                searchPageRequests.incrementAndGet()
                                htmlResponse(SEARCH_TITLE, "Results for $SEARCH_QUERY")
                            }
                            requestTarget.startsWith("/autocompleter?") -> jsonResponse("[]")
                            else -> notFoundResponse()
                        }
                        connection.getOutputStream().buffered().use { output ->
                            output.write(response)
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(1_000L)
        }

        private fun htmlResponse(title: String, content: String): ByteArray = response(
            status = "200 OK",
            contentType = "text/html; charset=utf-8",
            body = "<!doctype html><title>$title</title><main>$content</main>",
        )

        private fun jsonResponse(body: String): ByteArray = response(
            status = "200 OK",
            contentType = "application/json; charset=utf-8",
            body = body,
        )

        private fun notFoundResponse(): ByteArray = response(
            status = "404 Not Found",
            contentType = "text/plain; charset=utf-8",
            body = "Not found",
        )

        private fun response(
            status: String,
            contentType: String,
            body: String,
        ): ByteArray {
            val bodyBytes = body.toByteArray()
            return buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Connection: close\r\n\r\n")
                append(body)
            }.toByteArray()
        }
    }

    private companion object {
        const val ANDROID_DEVICE_ID = "android-device"
        const val CANDY_ID = "address-sync-e2e-candy"
        const val INITIAL_TAB_ID = "address-sync-e2e-tab"
        const val NOW = "2026-09-12T14:00:00Z"
        const val OLD_TITLE = "Old destination"
        const val SEARCH_TITLE = "Search results"
        const val SEARCH_QUERY = "candy sync race"
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 50L
        const val SYNC_SETTLE_MILLIS = 700L
        const val STALE_SYNC_OBSERVATION_MILLIS = 1_200L
        const val REQUEST_BACKLOG = 8
        const val TEST_ACTIVITY_ACTION =
            "dev.sk2andy.materialbrowser.test.ADDRESS_BAR_SYNC_NAVIGATION_E2E"
    }
}
