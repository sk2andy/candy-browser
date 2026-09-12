package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingHostState
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncCacheStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncVaultStore
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewChromeTestTags
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewHeroRules
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
class GeckoTabOverviewHandoffInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        context.getSharedPreferences("candy_sync_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AndroidSyncVaultStore(context).clear()
        AndroidSyncCacheStore(context).clear()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
    }

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        preferences.edit().clear().commit()
        context.getSharedPreferences("candy_sync_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AndroidSyncVaultStore(context).clear()
        AndroidSyncCacheStore(context).clear()
    }

    @Test
    fun heroEntryAndExitKeepGeckoViewportPixelStableAcrossSafeArea() {
        StripeFixtureServer().use { server ->
            awaitGeckoRuntimeReadiness()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                composeRule.waitForIdle()
                lateinit var tabId: String
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.usesGeckoEngine)
                    assertTrue(controller.openUrl(server.url))
                    tabId = controller.selectedTabId
                }
                composeRule.waitForIdle()
                awaitCondition(scenario) { controller ->
                    controller.selectedTab.url == server.url &&
                        controller.selectedGeckoViewForTesting()?.isAttachedToWindow == true
                }
                scenario.onActivity { activity ->
                    activity.onBackPressedDispatcher.onBackPressed()
                }
                composeRule.waitForIdle()
                SystemClock.sleep(PAGE_SETTLE_MILLIS)
                awaitPreview(scenario)
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.previewTopInsetPx(tabId) > 0)
                    assertTrue(controller.selectedGeckoViewForTesting() is GeckoView)
                }

                val liveBefore = takeStableScreenshot()
                composeRule.mainClock.autoAdvance = false
                composeRule.onNodeWithTag(AddressBarTestTags.TabButton).performClick()
                advanceUntilNodeExists(TabOverviewChromeTestTags.Root)
                repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
                val entryStart = takeStableScreenshot()

                assertStripeViewportMatches(
                    expected = liveBefore,
                    actual = entryStart,
                    message = "Hero entry changed Gecko viewport pixels at the fullscreen handoff",
                    boundaryTolerancePx = PIXEL_STABLE_BOUNDARY_TOLERANCE_PX,
                )

                composeRule.mainClock.advanceTimeBy(
                    TabOverviewHeroRules.ENTRY_DURATION_MILLIS.toLong() + 32L,
                )
                composeRule.onNodeWithTag(SnoozeTestTags.overviewTab(tabId)).performClick()
                repeat(EXIT_NEAR_FULLSCREEN_FRAME_COUNT) {
                    composeRule.mainClock.advanceTimeByFrame()
                    composeRule.waitForIdle()
                }
                val exitNearFullscreen = takeStableScreenshot()
                assertStripeViewportMatches(
                    expected = liveBefore,
                    actual = exitNearFullscreen,
                    message = "Hero exit jumped before reaching the Gecko viewport",
                    tolerance = EXIT_FRAME_COLOR_TOLERANCE,
                    boundaryTolerancePx = EXIT_BOUNDARY_TOLERANCE_PX,
                )

                composeRule.mainClock.advanceTimeBy(32L)
                composeRule.mainClock.autoAdvance = true
                awaitNodeGone(TabOverviewChromeTestTags.Root)
                val liveAfter = takeStableScreenshot()
                assertStripeViewportMatches(
                    expected = liveBefore,
                    actual = liveAfter,
                    message = "Live Gecko viewport moved after the preview handoff",
                    boundaryTolerancePx = PIXEL_STABLE_BOUNDARY_TOLERANCE_PX,
                )
            }
        }
    }

    @Test
    fun restoredDismissAnchorAttachesGeckoOnlyAfterOverviewCloses() {
        val remainingTab = BrowserTab(
            id = "restored-remaining",
            lastAccessedAt = 1L,
            title = "Remaining",
            url = "https://remaining.example/",
        )
        val dismissedTab = BrowserTab(
            id = "restored-dismissed",
            lastAccessedAt = 2L,
            title = "Dismissed",
            url = "https://dismissed.example/",
        )
        assertTrue(
            BrowserSessionStore(context).saveTabsImmediately(
                tabs = listOf(remainingTab, dismissedTab),
                selectedTabId = dismissedTab.id,
            ),
        )
        awaitGeckoRuntimeReadiness()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForIdle()
            awaitCondition(scenario) { controller ->
                controller.selectedTabId == dismissedTab.id &&
                    dismissedTab.id in controller.residentTabIdsForTesting() &&
                    remainingTab.id !in controller.residentTabIdsForTesting()
            }

            composeRule.onNodeWithTag(AddressBarTestTags.TabButton).performClick()
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                runCatching {
                    composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root)
                        .fetchSemanticsNode()
                }.isSuccess
            }
            composeRule
                .onNodeWithTag(SnoozeTestTags.overviewTab(dismissedTab.id))
                .performTouchInput { swipeUp(durationMillis = 240L) }
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                var dismissed = false
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    dismissed = controller.selectedTabId == remainingTab.id &&
                        controller.activeTabs.none { tab -> tab.id == dismissedTab.id }
                }
                dismissed
            }
            repeat(3) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
            scenario.onActivity { activity ->
                assertFalse(
                    "Restored anchor attached Gecko while overview was visible",
                    remainingTab.id in activity.browserControllerForTesting()
                        .residentTabIdsForTesting(),
                )
            }

            composeRule
                .onNodeWithTag(SnoozeTestTags.overviewTab(remainingTab.id))
                .performClick()
            awaitNodeGone(TabOverviewChromeTestTags.Root)
            awaitCondition(scenario) { controller ->
                remainingTab.id in controller.residentTabIdsForTesting()
            }
        }
    }

    private fun takeStableScreenshot(): Bitmap {
        val captured = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        return captured.copy(Bitmap.Config.ARGB_8888, false).also {
            if (!captured.isRecycled) captured.recycle()
        }
    }

    private fun awaitPreview(scenario: ActivityScenario<MainActivity>) {
        val ready = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().refreshSelectedTabPreview { ready.countDown() }
        }
        assertTrue("Gecko preview capture did not finish", ready.await(10, TimeUnit.SECONDS))
        awaitCondition(scenario) { controller -> controller.previews[controller.selectedTabId] != null }
    }

    private fun awaitGeckoRuntimeReadiness() {
        val toppingHostReady = CountDownLatch(1)
        instrumentation.runOnMainSync {
            GeckoRuntimeOwner.getOrCreate(context)
                .toppings
                .setStateListener { state ->
                    if (state == GeckoToppingHostState.Ready) toppingHostReady.countDown()
                }
        }
        assertTrue(
            "Gecko Topping host did not initialize",
            toppingHostReady.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
        )
        instrumentation.waitForIdleSync()
        SystemClock.sleep(PRIVACY_HOST_SETTLE_MILLIS)
    }

    private fun advanceUntilNodeExists(tag: String) {
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            if (runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess) return
            SystemClock.sleep(25L)
        }
        throw AssertionError("Compose node did not appear: $tag")
    }

    private fun awaitNodeGone(tag: String) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isFailure) return
            SystemClock.sleep(25L)
        }
        throw AssertionError("Compose node did not disappear: $tag")
    }

    private fun awaitCondition(
        scenario: ActivityScenario<MainActivity>,
        predicate: (BrowserController) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { activity ->
                matched = predicate(activity.browserControllerForTesting())
            }
            if (matched) return
            SystemClock.sleep(STATE_POLL_INTERVAL_MILLIS)
        }
        var lastState = "unavailable"
        var engineViews = emptyMap<String, Int>()
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            val selectedView = controller.selectedGeckoViewForTesting()
            lastState = "url=${controller.selectedTab.url}, " +
                "loading=${controller.selectedTab.isLoading}, " +
                "tabs=${controller.tabs.size}, " +
                "selected=${controller.selectedTabId}, " +
                "capsule=${controller.activeSiteCapsule?.id}, " +
                "externalPreview=${controller.externalLinkPreviewState?.sessionId}, " +
                "view=${selectedView?.javaClass?.simpleName}, " +
                "attached=${selectedView?.isAttachedToWindow}, " +
                "preview=${controller.previews[controller.selectedTabId] != null}"
            engineViews = activity.window.decorView.descendants()
                .mapNotNull { view -> view.javaClass.simpleName.takeIf(String::isNotBlank) }
                .groupingBy { name -> name }
                .eachCount()
                .filterKeys { name -> name.contains("Gecko") || name.contains("Frosted") }
        }
        val composeTree = runCatching {
            composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 4)
        }.getOrElse { failure -> "unavailable (${failure.message})" }
        throw AssertionError(
            "Timed out waiting for Gecko tab-overview state: $lastState, " +
                "engineViews=$engineViews\n$composeTree",
        )
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        val group = this@descendants as? ViewGroup ?: return@sequence
        repeat(group.childCount) { index -> yieldAll(group.getChildAt(index).descendants()) }
    }

    private fun assertStripeViewportMatches(
        expected: Bitmap,
        actual: Bitmap,
        message: String,
        tolerance: Float = COLOR_TOLERANCE,
        boundaryTolerancePx: Int,
    ) {
        assertTrue("Screenshot dimensions changed", expected.width == actual.width)
        assertTrue("Screenshot dimensions changed", expected.height == actual.height)
        val distances = SAMPLE_HEIGHT_FRACTIONS.map { fraction ->
            val x = expected.width / 2
            val y = (expected.height * fraction).toInt().coerceIn(0, expected.height - 1)
            colorDistance(expected.getPixel(x, y), actual.getPixel(x, y))
        }
        assertTrue("$message: $distances", distances.all { distance -> distance <= tolerance })
        val expectedBoundaries = stripeBoundaries(expected)
        val actualBoundaries = stripeBoundaries(actual)
        val boundaryOffsets = expectedBoundaries.zip(actualBoundaries) { first, second ->
            kotlin.math.abs(first - second)
        }
        assertTrue(
            "$message; stripe-boundary offsets=$boundaryOffsets",
            boundaryOffsets.all { offset -> offset <= boundaryTolerancePx },
        )
    }

    private fun stripeBoundaries(bitmap: Bitmap): List<Int> {
        val x = bitmap.width / 2
        val labels = IntArray(bitmap.height) { y ->
            STRIPE_COLORS.indices.minBy { index ->
                colorDistance(bitmap.getPixel(x, y), STRIPE_COLORS[index])
            }
        }
        val greenStart = (bitmap.height / 5 until bitmap.height * 3 / 5)
            .first { y -> labels[y] == GREEN_STRIPE_INDEX }
        val blueStart = (greenStart + 1 until bitmap.height * 9 / 10)
            .first { y -> labels[y] == BLUE_STRIPE_INDEX }
        return listOf(greenStart, blueStart)
    }

    private fun colorDistance(first: Int, second: Int): Float = (
        kotlin.math.abs(Color.red(first) - Color.red(second)) +
            kotlin.math.abs(Color.green(first) - Color.green(second)) +
            kotlin.math.abs(Color.blue(first) - Color.blue(second))
        ) / (255f * 3f)

    private class StripeFixtureServer : Closeable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        socket.getInputStream().bufferedReader().readLine()
                        val body = """
                            <!doctype html>
                            <meta name="viewport" content="width=device-width,initial-scale=1">
                            <title>Hero stripes</title>
                            <style>
                              html,body{margin:0;width:100%;height:100%;overflow:hidden}
                              body{background:linear-gradient(to bottom,#e62626 0 33%,#23bd55 33% 66%,#2955dc 66% 100%)}
                            </style>
                        """.trimIndent()
                        val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${body.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" +
                            body
                        socket.getOutputStream().write(response.toByteArray())
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        val url = "http://127.0.0.1:${server.localPort}/hero"

        override fun close() {
            server.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        val SAMPLE_HEIGHT_FRACTIONS = listOf(0.20f, 0.46f, 0.72f)
        const val COLOR_TOLERANCE = 0.08f
        const val EXIT_FRAME_COLOR_TOLERANCE = 0.16f
        const val TIMEOUT_MILLIS = 45_000L
        const val STATE_POLL_INTERVAL_MILLIS = 250L
        const val PRIVACY_HOST_SETTLE_MILLIS = 2_000L
        const val PAGE_SETTLE_MILLIS = 2_000L
        const val PIXEL_STABLE_BOUNDARY_TOLERANCE_PX = 4
        const val EXIT_BOUNDARY_TOLERANCE_PX = 24
        const val EXIT_NEAR_FULLSCREEN_FRAME_COUNT = 14
        const val GREEN_STRIPE_INDEX = 1
        const val BLUE_STRIPE_INDEX = 2
        val STRIPE_COLORS = listOf(
            Color.rgb(0xE6, 0x26, 0x26),
            Color.rgb(0x23, 0xBD, 0x55),
            Color.rgb(0x29, 0x55, 0xDC),
        )
    }
}
