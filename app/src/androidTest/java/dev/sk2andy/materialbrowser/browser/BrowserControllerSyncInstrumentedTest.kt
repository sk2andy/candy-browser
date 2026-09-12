package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFindResult
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncCacheStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncVaultStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncPendingMutation
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import dev.sk2andy.materialbrowser.sync.SyncTab
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerSyncInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mutations = CopyOnWriteArrayList<SyncPendingMutation>()
    private var controller: BrowserController? = null
    private var engineHost: FrameLayout? = null
    private var engineSession: RecordingGeckoSession? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            engineHost?.removeAllViews()
            engineHost = null
            engineSession = null
            controller?.onPause()
            controller?.onStop()
            controller?.destroy()
            controller = null
            clearState(activity)
        }
        mutations.clear()
    }

    @Test
    fun geckoNavigationPublishesNavigateWithoutHydrationEcho() {
        createController()
        applyState(syncState(remoteUrl = URL_A))
        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            assertTrue(browserController.selectProfile(SyncedProfileRuntimeRules.profileId(DESKTOP_ID)))
            assertEquals(URL_A, browserController.selectedTabForTesting().url)
            installSelectedEngine(activity, browserController)
        }
        completeNavigation(URL_A, "Page A")
        SystemClock.sleep(DEBOUNCE_SETTLE_MILLIS)
        assertTrue(mutations.isEmpty())

        activityRule.scenario.onActivity { _ -> requireNotNull(controller).submitAddress(URL_B) }
        assertEquals(URL_B, requireNotNull(engineSession).lastLoadUrl())
        completeNavigation(URL_B, "Page B")

        val mutation = awaitMutation(URL_B) as? SyncPendingMutation.Navigate
        assertNotNull(mutation)
        requireNotNull(mutation)
        assertEquals(DESKTOP_ID, mutation.targetDeviceId)
        assertEquals(REMOTE_CANDY_ID, mutation.candyId)
        assertEquals("Page B", mutation.title)
        assertEquals(URL_B, mutation.url)
        assertEquals(1, mutations.count { candidate -> candidate is SyncPendingMutation.Navigate })
    }

    @Test
    fun geckoBlankTabNavigationPublishesOpen() {
        createController()
        applyState(syncState())
        lateinit var expectedCandyId: String
        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            expectedCandyId = requireNotNull(browserController.selectedTabForTesting().syncCandyId)
            installSelectedEngine(activity, browserController)
            browserController.submitAddress(URL_BLANK_OPEN)
        }
        assertEquals(URL_BLANK_OPEN, requireNotNull(engineSession).lastLoadUrl())
        completeNavigation(URL_BLANK_OPEN, "Page Blank Open")

        val mutation = awaitMutation(URL_BLANK_OPEN) as? SyncPendingMutation.Open
        assertNotNull(mutation)
        requireNotNull(mutation)
        assertEquals(ANDROID_ID, mutation.targetDeviceId)
        assertEquals(expectedCandyId, mutation.tab.candyId)
        assertEquals("Page Blank Open", mutation.tab.title)
        assertEquals(URL_BLANK_OPEN, mutation.tab.url)
        SystemClock.sleep(DEBOUNCE_SETTLE_MILLIS)
        assertEquals(listOf(mutation), mutations.toList())
    }

    @Test
    fun remoteNavigationLoadsResidentGeckoSessionWithoutEcho() {
        createController()
        applyState(syncState(remoteUrl = URL_A))
        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            assertTrue(browserController.selectProfile(SyncedProfileRuntimeRules.profileId(DESKTOP_ID)))
            installSelectedEngine(activity, browserController)
        }
        completeNavigation(URL_A, "Page A")
        SystemClock.sleep(DEBOUNCE_SETTLE_MILLIS)
        mutations.clear()

        applyState(syncState(remoteUrl = URL_REMOTE, remoteRevision = 1))
        assertEquals(URL_REMOTE, requireNotNull(engineSession).lastLoadUrl())
        completeNavigation(URL_REMOTE, "Page Remote")

        SystemClock.sleep(DEBOUNCE_SETTLE_MILLIS)
        assertTrue(mutations.isEmpty())
        activityRule.scenario.onActivity { _ ->
            assertEquals(URL_REMOTE, requireNotNull(controller).selectedTabForTesting().url)
        }
    }

    @Test
    fun geckoSameDocumentNavigationPublishesNavigate() {
        createController()
        applyState(syncState(remoteUrl = URL_SPA_START))
        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            assertTrue(browserController.selectProfile(SyncedProfileRuntimeRules.profileId(DESKTOP_ID)))
            installSelectedEngine(activity, browserController)
        }
        completeNavigation(URL_SPA_START, "Page Spa Start")
        mutations.clear()

        dispatchEngineEvent(
            type = BrowserEngineEventType.StateChanged,
            url = URL_SPA_FINAL,
            title = "Page Spa Start",
            isLoading = false,
        )

        val mutation = awaitMutation(URL_SPA_FINAL) as? SyncPendingMutation.Navigate
        assertNotNull(mutation)
        requireNotNull(mutation)
        assertEquals(DESKTOP_ID, mutation.targetDeviceId)
        assertEquals(REMOTE_CANDY_ID, mutation.candyId)
        assertEquals("Page Spa Start", mutation.title)
        assertEquals(URL_SPA_FINAL, mutation.url)
        SystemClock.sleep(DEBOUNCE_SETTLE_MILLIS)
        assertEquals(listOf(mutation), mutations.toList())
    }

    @Test
    fun localNavigationIsNotReplacedByStaleLinkedSyncState() {
        createController()
        applyState(syncState())
        lateinit var localCandyId: String
        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            localCandyId = requireNotNull(browserController.selectedTabForTesting().syncCandyId)
            installSelectedEngine(activity, browserController)
            browserController.submitAddress(URL_A)
        }
        completeNavigation(URL_A, "Page A")
        applyState(syncState(localTab = tab(URL_A, localCandyId)))
        SystemClock.sleep(DEBOUNCE_SETTLE_MILLIS)
        mutations.clear()

        activityRule.scenario.onActivity { _ ->
            requireNotNull(controller).submitAddress(URL_B)
        }
        applyState(syncState(localTab = tab(URL_A, localCandyId)))
        activityRule.scenario.onActivity { _ ->
            assertEquals(URL_B, requireNotNull(controller).selectedTabForTesting().url)
        }
        assertEquals(URL_B, requireNotNull(engineSession).lastLoadUrl())

        completeNavigation(URL_B, "Page B")
        applyState(syncState(localTab = tab(URL_A, localCandyId)))

        activityRule.scenario.onActivity { _ ->
            assertEquals(URL_B, requireNotNull(controller).selectedTabForTesting().url)
        }
        assertEquals(URL_B, requireNotNull(engineSession).lastLoadUrl())
        assertTrue(awaitMutation(URL_B) is SyncPendingMutation.Navigate)

        applyState(syncState(localTab = tab(URL_B, localCandyId)))
        applyState(syncState(localTab = tab(URL_REMOTE, localCandyId)))
        activityRule.scenario.onActivity { _ ->
            assertEquals(URL_REMOTE, requireNotNull(controller).selectedTabForTesting().url)
        }
        assertEquals(URL_REMOTE, requireNotNull(engineSession).lastLoadUrl())
    }

    @Test
    fun newerRemoteNavigationRejectsSupersededGeckoCommit() {
        createController()
        applyState(syncState(remoteUrl = URL_SUPERSEDED))
        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            assertTrue(browserController.selectProfile(SyncedProfileRuntimeRules.profileId(DESKTOP_ID)))
            installSelectedEngine(activity, browserController)
        }

        applyState(syncState(remoteUrl = URL_LATEST, remoteRevision = 1))
        assertEquals(URL_LATEST, requireNotNull(engineSession).lastLoadUrl())
        dispatchEngineEvent(
            type = BrowserEngineEventType.NavigationCommitted,
            url = URL_SUPERSEDED,
            title = "Page Superseded",
            isLoading = false,
        )
        activityRule.scenario.onActivity { _ ->
            assertEquals(URL_LATEST, requireNotNull(controller).selectedTabForTesting().url)
        }
        assertEquals(URL_LATEST, requireNotNull(engineSession).lastLoadUrl())
        completeNavigation(URL_LATEST, "Page Latest")

        SystemClock.sleep(DEBOUNCE_SETTLE_MILLIS)
        assertTrue(mutations.isEmpty())
    }

    private fun createController() {
        activityRule.scenario.onActivity { activity ->
            clearState(activity)
            controller = BrowserController(activity).also { browserController ->
                browserController.syncMutationObserverForTesting = mutations::add
                browserController.onStart()
                browserController.onResume()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun installSelectedEngine(
        activity: ComponentActivity,
        browserController: BrowserController,
    ) {
        val session = RecordingGeckoSession(browserController.selectedTabForTesting().id)
        browserController.installGeckoEngineSessionForTesting(session)
        engineSession = session
        val host = FrameLayout(activity)
        activity.addContentView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        engineHost = host
        assertNotNull(browserController.attachSelectedBrowserEngineView(host))
    }

    private fun applyState(state: SyncRepositoryState) {
        activityRule.scenario.onActivity { _ ->
            requireNotNull(controller).applySyncRepositoryStateForTesting(state)
        }
        instrumentation.waitForIdleSync()
    }

    private fun completeNavigation(url: String, title: String) {
        dispatchEngineEvent(BrowserEngineEventType.NavigationStarted, url, null, true)
        dispatchEngineEvent(BrowserEngineEventType.NavigationCommitted, url, title, false)
        dispatchEngineEvent(BrowserEngineEventType.StateChanged, url, title, false)
    }

    private fun dispatchEngineEvent(
        type: BrowserEngineEventType,
        url: String,
        title: String?,
        isLoading: Boolean,
    ) {
        activityRule.scenario.onActivity { _ ->
            requireNotNull(controller).dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = requireNotNull(engineSession).tabId,
                    type = type,
                    address = url,
                    title = title,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                    isLoading = isLoading,
                ),
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitMutation(url: String): SyncPendingMutation? {
        repeat(POLL_ATTEMPTS) {
            mutations.firstOrNull { mutation ->
                when (mutation) {
                    is SyncPendingMutation.Open -> mutation.tab.url == url
                    is SyncPendingMutation.Navigate -> mutation.url == url
                    is SyncPendingMutation.Close,
                    is SyncPendingMutation.Reorder,
                    is SyncPendingMutation.SetPinned,
                    -> false
                }
            }?.let { return it }
            SystemClock.sleep(POLL_MILLIS)
        }
        return null
    }

    private fun syncState(
        remoteUrl: String? = null,
        remoteRevision: Long = 0,
        localTab: SyncTab? = null,
    ): SyncRepositoryState = SyncRepositoryState(
        settings = SyncConnectionSettings(
            endpoint = "https://sync.example",
            username = "user",
            deviceName = "Android",
            iconCatalogId = "phone",
            iconAccentHue = 120,
            localProfileId = DEFAULT_PROFILE_ID,
        ),
        status = SyncStatus.Ready,
        profiles = buildList {
            add(profile(ANDROID_ID, listOfNotNull(localTab)))
            remoteUrl?.let { url -> add(profile(DESKTOP_ID, listOf(tab(url)), remoteRevision)) }
        },
        pendingCount = 0,
        lastCursor = "epoch.0",
        lastSuccessAt = NOW,
        currentDeviceId = ANDROID_ID,
    )

    private fun profile(
        deviceId: String,
        tabs: List<SyncTab>,
        revision: Long = 0,
    ) = SyncProfile(
        deviceId = deviceId,
        displayName = deviceId,
        icon = SyncDeviceIconDescriptor("computer", 210),
        revision = revision,
        tabs = tabs,
        lastSeenAt = NOW,
    )

    private fun tab(
        url: String,
        candyId: String = REMOTE_CANDY_ID,
    ) = SyncTab(
        candyId = candyId,
        windowId = 0,
        index = 0,
        groupId = null,
        active = true,
        pinned = false,
        title = titleFor(url),
        url = url,
    )

    private fun titleFor(url: String): String = when (url) {
        URL_A -> "Page A"
        URL_REMOTE -> "Page Remote"
        URL_SPA_START -> "Page Spa Start"
        URL_SUPERSEDED -> "Page Superseded"
        URL_LATEST -> "Page Latest"
        else -> "Page"
    }

    private fun clearState(context: Context) {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("candy_sync_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AndroidSyncVaultStore(context).clear()
        AndroidSyncCacheStore(context).clear()
    }

    private class RecordingGeckoSession(
        override val tabId: String,
    ) : AndroidBrowserEngineSessionPort {
        private val commands = CopyOnWriteArrayList<BrowserEngineCommand>()

        override fun execute(command: BrowserEngineCommand) {
            commands += command
        }

        override fun setAudioMuted(muted: Boolean) = Unit

        fun lastLoadUrl(): String? = commands.lastOrNull {
            it.type == BrowserEngineCommandType.Load
        }?.address

        override fun createView(context: Context): View = View(context)

        override fun awaitContentPresented(listener: () -> Unit) = listener()

        override fun releaseView(view: View) = Unit

        override fun capturePreview(
            targetWidthPx: Int,
            visibleViewHeightPx: Int,
            maximumTargetHeightPx: Int,
            onComplete: (Bitmap?) -> Unit,
        ): BrowserEnginePreviewCapture? = null

        override fun findInPage(
            query: String,
            forward: Boolean,
            onComplete: (GeckoFindResult?) -> Unit,
        ) = onComplete(null)

        override fun clearFindInPage() = Unit

        override fun printPage(): Boolean = false

        override fun setDesktopMode(enabled: Boolean) = Unit

        override fun setActive(active: Boolean) = Unit

        override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) = Unit

        override fun setScrollListener(listener: BrowserEngineScrollListener?) = Unit

        override fun setContentTargetListener(listener: BrowserContentTargetListener?) = Unit

        override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) = Unit

        override fun setVideoAutoplayBlocked(blocked: Boolean) = Unit

        override fun executeMediaCommand(command: GeckoMediaCommand) = Unit

        override fun seekMedia(positionMillis: Long) = Unit

        override fun goToHistoryIndex(index: Int) = Unit

        override fun historyUrlAtOffset(offset: Int): String? = null

        override fun extractPageForReader(onComplete: (String?) -> Unit) = onComplete(null)

        override fun updatePrivacyPolicy(
            policy: GeckoPrivacyPolicy,
            reloadOnCookiePermissionChange: Boolean,
            onReady: () -> Unit,
        ) = onReady()
    }

    private companion object {
        const val ANDROID_ID = "android-device"
        const val DESKTOP_ID = "desktop-device"
        const val REMOTE_CANDY_ID = "remote-tab"
        const val NOW = "2026-09-06T08:00:00Z"
        const val URL_A = "https://example.test/a"
        const val URL_B = "https://example.test/b"
        const val URL_BLANK_OPEN = "https://example.test/blank-open"
        const val URL_REMOTE = "https://example.test/remote"
        const val URL_SPA_START = "https://example.test/spa-start"
        const val URL_SPA_FINAL = "https://example.test/spa-final"
        const val URL_SUPERSEDED = "https://example.test/superseded"
        const val URL_LATEST = "https://example.test/latest"
        const val POLL_ATTEMPTS = 100
        const val POLL_MILLIS = 25L
        const val DEBOUNCE_SETTLE_MILLIS = 700L
    }
}
