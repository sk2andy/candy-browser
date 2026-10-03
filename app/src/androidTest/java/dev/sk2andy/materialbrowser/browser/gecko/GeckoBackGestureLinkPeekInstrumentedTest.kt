package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.RootTabBackDecision
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.FileInputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class GeckoBackGestureLinkPeekInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = BrowserSessionStore(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        assertEquals("Real edge-back input requires gesture navigation", "2", navigationMode())
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        store.saveStartupAnimationEnabled(false)
        store.saveOpenHomeOnStartupEnabled(false)
        store.saveLinkLongPressAction(LinkLongPressAction.LinkPeek)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun heldLeftBackGestureOverLinkDoesNotOpenPeekAndCommitsHistory() {
        assertCommittedBackGesture(fromRight = false)
    }

    @Test
    fun heldRightBackGestureOverLinkDoesNotOpenPeekAndCommitsHistory() {
        assertCommittedBackGesture(fromRight = true)
    }

    @Test
    fun leftBackGestureOnFirstPageReturnsHomeBeforeLeavingBrowser() {
        assertCommittedRootBackGesture(fromRight = false)
    }

    @Test
    fun rightBackGestureOnFirstPageReturnsHomeBeforeLeavingBrowser() {
        assertCommittedRootBackGesture(fromRight = true)
    }

    @Test
    fun backGestureFromLinkPeekTabTraversesHistoryThenReturnsToSource() {
        assertLinkPeekBackGesture(isPinned = false)
    }

    @Test
    fun backGestureFromPinnedLinkPeekTabTraversesHistoryThenReturnsToSource() {
        assertLinkPeekBackGesture(isPinned = true)
    }

    @Test
    fun canceledBackGestureOverLinkAllowsSubsequentOrdinaryLongPress() {
        assertCanceledBackGesture(hasHistory = true)
    }

    @Test
    fun canceledBackGestureOverFirstPageAllowsSubsequentOrdinaryLongPress() {
        assertCanceledBackGesture(hasHistory = false)
    }

    @Test
    fun queuedGeckoContextTargetIsRejectedWhenBrowserTouchIsCanceledBeforeDelivery() {
        withSourcePage { scenario, server, _ ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.dispatchSelectedGeckoContentTargetForTesting(
                    WebContentTarget(linkUrl = server.fixtureUrl(TARGET_PATH)),
                )
                controller.cancelSelectedBrowserEngineTouch()
            }
            instrumentation.waitForIdleSync()
            composeRule.waitForIdle()
            assertFalse("Queued context callback survived touch cancellation", isPeekVisible(scenario))
        }
    }

    private fun assertCanceledBackGesture(hasHistory: Boolean) {
        withSourcePage(hasHistory = hasHistory) { scenario, server, actions ->
            val gesture = beginBackGesture(scenario, fromRight = false)
            try {
                assertNoPeekDuringHold(scenario, actions)
                injectTouch(MotionEvent.ACTION_MOVE, gesture.downTime, gesture.startX, gesture.y)
                SystemClock.sleep(100)
            } finally {
                injectTouch(MotionEvent.ACTION_UP, gesture.downTime, gesture.startX, gesture.y)
            }
            assertPage(scenario, server.fixtureUrl(SOURCE_PATH), SOURCE_TITLE)
            assertFalse(isPeekVisible(scenario))
            instrumentation.uiAutomation.waitForIdle(500L, 5_000L)
            awaitCondition("Canceled OS back did not restore the focused source window") {
                var focused = false
                scenario.onActivity { activity ->
                    focused = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        activity.window.decorView.hasWindowFocus()
                }
                focused && sourcePixelsVisible()
            }
            composeRule.waitForIdle()

            val center = windowSize(scenario)
            val downTime = SystemClock.uptimeMillis()
            injectTouch(MotionEvent.ACTION_DOWN, downTime, center.first / 2f, center.second / 3f)
            try {
                awaitCondition("Canceled back permanently disabled ordinary Gecko link long-press") {
                    isPeekVisible(scenario)
                }
            } finally {
                injectTouch(MotionEvent.ACTION_UP, downTime, center.first / 2f, center.second / 3f)
            }
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(server.fixtureUrl(TARGET_PATH), controller.contentActions.target?.linkUrl)
                assertEquals(server.fixtureUrl(SOURCE_PATH), controller.selectedTab.url)
            }
        }
    }

    private fun assertCommittedBackGesture(fromRight: Boolean) {
        withSourcePage { scenario, server, actions ->
            val gesture = beginBackGesture(scenario, fromRight)
            try {
                assertNoPeekDuringHold(scenario, actions)
            } finally {
                finishCommittedBackGesture(scenario, gesture)
            }
            // Native history changing proves Android accepted the injected edge gesture.
            assertPage(scenario, server.fixtureUrl(PREVIOUS_PATH), PREVIOUS_TITLE, actions)
            assertFalse("Committed OS back opened Link Peek", isPeekVisible(scenario))
        }
    }

    private fun assertCommittedRootBackGesture(fromRight: Boolean) {
        withSourcePage(hasHistory = false) { scenario, _, _ ->
            var tabId = ""
            scenario.onActivity { activity ->
                tabId = activity.browserControllerForTesting().selectedTabId
                assertTrue(activity.onBackPressedDispatcher.hasEnabledCallbacks())
            }
            finishCommittedBackGesture(scenario, beginBackGesture(scenario, fromRight))
            awaitCondition("First-page Back did not return to Candy home") {
                var home = false
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    home = controller.selectedTab.url == "about:blank"
                    if (home) {
                        assertEquals(tabId, controller.selectedTabId)
                        assertFalse(controller.selectedTab.canGoBack)
                        assertFalse(controller.selectedTab.canGoForward)
                        assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    }
                }
                home
            }
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
                assertFalse(activity.browserControllerForTesting().contentActions.isLinkPeekVisible)
            }
            finishCommittedBackGesture(scenario, beginBackGesture(scenario, fromRight))
            awaitCondition("Back at Candy home did not leave the browser") {
                !scenario.state.isAtLeast(Lifecycle.State.RESUMED)
            }
        }
    }

    private fun assertLinkPeekBackGesture(isPinned: Boolean) {
        withSourcePage(hasHistory = false) { scenario, server, _ ->
            var sourceTabId = ""
            var childTabId = ""
            val childUrl = server.fixtureUrl(TARGET_PATH)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                sourceTabId = controller.selectedTabId
                controller.contentActions.show(WebContentTarget(linkUrl = childUrl), sourceTabId)
                assertTrue(controller.openContextLinkInForeground(childUrl))
                childTabId = controller.selectedTabId
                assertEquals(sourceTabId, controller.selectedTab.openerTabId)
                if (isPinned) assertTrue(controller.setTabPinned(childTabId, true))
            }
            assertPage(scenario, childUrl, SOURCE_TITLE)
            scenario.onActivity { activity ->
                assertTrue(activity.browserControllerForTesting().openUrl(server.fixtureUrl(PREVIOUS_PATH)))
            }
            assertPage(scenario, server.fixtureUrl(PREVIOUS_PATH), PREVIOUS_TITLE)
            awaitCondition("Link Peek child did not acquire real page history") {
                var hasHistory = false
                scenario.onActivity { activity ->
                    hasHistory = activity.browserControllerForTesting().selectedTab.canGoBack
                }
                hasHistory
            }
            composeRule.waitForIdle()
            finishCommittedBackGesture(scenario, beginBackGesture(scenario, fromRight = false))
            assertPage(scenario, childUrl, SOURCE_TITLE)
            scenario.onActivity { activity ->
                assertEquals(childTabId, activity.browserControllerForTesting().selectedTabId)
                assertFalse(activity.browserControllerForTesting().selectedTab.canGoBack)
            }
            composeRule.waitForIdle()
            finishCommittedBackGesture(scenario, beginBackGesture(scenario, fromRight = false))
            assertPage(scenario, server.fixtureUrl(SOURCE_PATH), SOURCE_TITLE)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(sourceTabId, controller.selectedTabId)
                assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                if (isPinned) {
                    val child = controller.tabs.single { it.id == childTabId }
                    assertTrue(child.isPinned)
                    assertEquals(childUrl, child.url)
                } else {
                    assertTrue(controller.tabs.none { it.id == childTabId })
                }
            }
        }
    }

    private fun withSourcePage(
        hasHistory: Boolean = true,
        block: (ActivityScenario<MainActivity>, EdgeToEdgeSiteFixtureServer, List<Int>) -> Unit,
    ) {
        EdgeToEdgeSiteFixtureServer { path ->
            val title = if (path == PREVIOUS_PATH) PREVIOUS_TITLE else SOURCE_TITLE
            val background = if (path == PREVIOUS_PATH) "#264a94" else "#247643"
            """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>$title</title><style>
                html,body{margin:0;width:100%;height:100%;background:$background}
                a{position:fixed;inset:0;display:block;background:$background;color:white}
                </style><a href="$TARGET_PATH">Full-screen link including both gesture edges</a>
            """.trimIndent()
        }.use { server ->
            val initialPath = if (hasHistory) PREVIOUS_PATH else SOURCE_PATH
            val initialTitle = if (hasHistory) PREVIOUS_TITLE else SOURCE_TITLE
            val tab = BrowserTab(
                id = "gecko-real-back-link",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.fixtureUrl(initialPath),
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                assertPage(scenario, server.fixtureUrl(initialPath), initialTitle)
                if (hasHistory) {
                    scenario.onActivity { activity ->
                        assertTrue(activity.browserControllerForTesting().openUrl(server.fixtureUrl(SOURCE_PATH)))
                    }
                    assertPage(scenario, server.fixtureUrl(SOURCE_PATH), SOURCE_TITLE)
                    awaitCondition("Fixture lacks real back history") {
                        var ready = false
                        scenario.onActivity { activity ->
                            ready = activity.browserControllerForTesting().selectedTab.canGoBack
                        }
                        ready
                    }
                } else {
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        assertFalse(controller.selectedTab.canGoBack)
                        assertEquals(RootTabBackDecision.ReturnToHome, controller.selectedRootTabBackDecision)
                    }
                }
                composeRule.waitForIdle()
                awaitCondition("Source link did not reach the visible compositor frame") {
                    sourcePixelsVisible()
                }
                val actions = CopyOnWriteArrayList<Int>()
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    requireNotNull(controller.selectedGeckoViewForTesting()).findGeckoView()
                        .setOnTouchListener { _, event ->
                            actions += event.actionMasked
                            false
                        }
                }
                block(scenario, server, actions)
            }
        }
    }

    private fun beginBackGesture(scenario: ActivityScenario<MainActivity>, fromRight: Boolean): Gesture {
        val size = windowSize(scenario)
        val startX = if (fromRight) size.first - 1f else 1f
        val endX = if (fromRight) size.first * 0.72f else size.first * 0.28f
        val gesture = Gesture(SystemClock.uptimeMillis(), startX, endX, size.second / 3f)
        injectTouch(MotionEvent.ACTION_DOWN, gesture.downTime, startX, gesture.y)
        SystemClock.sleep(50)
        injectTouch(MotionEvent.ACTION_MOVE, gesture.downTime, endX, gesture.y)
        return gesture
    }

    private fun finishCommittedBackGesture(scenario: ActivityScenario<MainActivity>, gesture: Gesture) {
        val width = windowSize(scenario).first
        val commitX = width * if (gesture.startX > gesture.endX) 0.45f else 0.55f
        repeat(3) { index ->
            SystemClock.sleep(16)
            val x = gesture.endX + (commitX - gesture.endX) * (index + 1f) / 3
            injectTouch(MotionEvent.ACTION_MOVE, gesture.downTime, x, gesture.y)
        }
        injectTouch(MotionEvent.ACTION_UP, gesture.downTime, commitX, gesture.y)
    }

    private fun assertNoPeekDuringHold(scenario: ActivityScenario<MainActivity>, actions: List<Int>) {
        val deadline = SystemClock.uptimeMillis() + ViewConfiguration.getLongPressTimeout() * 2L + 300
        while (SystemClock.uptimeMillis() < deadline) {
            assertFalse(
                "Held OS back opened Link Peek before UP; native touch actions=" +
                    actions.map(MotionEvent::actionToString),
                isPeekVisible(scenario),
            )
            SystemClock.sleep(50)
        }
        assertTrue(
            "Edge gesture never canceled native page input: ${actions.map(MotionEvent::actionToString)}",
            actions.contains(MotionEvent.ACTION_DOWN) && actions.contains(MotionEvent.ACTION_CANCEL),
        )
    }

    private fun assertPage(
        scenario: ActivityScenario<MainActivity>,
        url: String,
        title: String,
        actions: List<Int> = emptyList(),
    ) {
        var state = "unavailable"
        try {
            awaitCondition("Expected page $url ($title) did not load", description = { state }) {
                var loaded = false
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    val tab = controller.selectedTab
                    state = "tab=${tab.id}, url=${tab.url}, title=${tab.title}, " +
                        "loading=${tab.isLoading}, canGoBack=${tab.canGoBack}, " +
                        "peek=${controller.contentActions.isLinkPeekVisible}, " +
                        "resumed=${activity.lifecycle.currentState}"
                    loaded = tab.url == url && tab.title == title && !tab.isLoading
                }
                loaded
            }
        } catch (failure: AssertionError) {
            val screenshot = File(context.getExternalFilesDir(null), "gecko-back-timeout.png")
            instrumentation.uiAutomation.takeScreenshot()?.let { pixels ->
                try {
                    screenshot.outputStream().use { output ->
                        pixels.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                } finally {
                    pixels.recycle()
                }
            }
            throw AssertionError(
                "${failure.message}; nativeActions=${actions.map(MotionEvent::actionToString)}; " +
                    "screenshot=${screenshot.absolutePath}",
                failure,
            )
        }
    }

    private fun sourcePixelsVisible(): Boolean {
        val pixels = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            .let { captured -> captured.copy(Bitmap.Config.ARGB_8888, false).also { captured.recycle() } }
        return try {
            val color = pixels.getPixel(pixels.width / 2, pixels.height / 3)
            abs(Color.red(color) - 36) < 12 && abs(Color.green(color) - 118) < 12 &&
                abs(Color.blue(color) - 67) < 12
        } finally {
            pixels.recycle()
        }
    }

    private fun isPeekVisible(scenario: ActivityScenario<MainActivity>): Boolean {
        var visible = false
        scenario.onActivity { activity ->
            visible = activity.browserControllerForTesting().contentActions.isLinkPeekVisible
        }
        return visible
    }

    private fun windowSize(scenario: ActivityScenario<MainActivity>): Pair<Int, Int> {
        var result = 0 to 0
        scenario.onActivity { activity ->
            result = activity.window.decorView.width to activity.window.decorView.height
        }
        return result
    }

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this is ViewGroup) {
            repeat(childCount) { index ->
                runCatching { getChildAt(index).findGeckoView() }.getOrNull()?.let { return it }
            }
        }
        error("Selected native GeckoView is missing")
    }

    private fun injectTouch(action: Int, downTime: Long, x: Float, y: Float) {
        MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(
                    "OS input injection failed: ${MotionEvent.actionToString(action)}",
                    instrumentation.uiAutomation.injectInputEvent(event, true),
                )
            } finally {
                event.recycle()
            }
        }
    }

    private fun navigationMode(): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("settings get secure navigation_mode")
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText().trim() }
            .also { descriptor.close() }
    }

    private fun awaitCondition(
        message: String,
        description: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("$message; ${description()}", condition())
    }

    private data class Gesture(val downTime: Long, val startX: Float, val endX: Float, val y: Float)

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_REAL_BACK_LINK"
        const val TIMEOUT_MILLIS = 30_000L
        const val PREVIOUS_PATH = "/back-destination"
        const val SOURCE_PATH = "/back-source"
        const val TARGET_PATH = "/peek-target"
        const val PREVIOUS_TITLE = "Candy real back destination"
        const val SOURCE_TITLE = "Candy real back source"
    }
}
