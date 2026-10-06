package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingHostState
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoTabHandoffTimeoutInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            controller?.destroy()
            preferences().edit().clear().commit()
        }
    }

    @Test
    fun delayedPaintCallbackCannotKeepInteractivePageBehindSnapshot() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        val ready = CountDownLatch(1)
        composeRule.runOnIdle {
            preferences().edit().clear().commit()
            GeckoRuntimeOwner.getOrCreate(composeRule.activity)
                .toppings.setStateListener { state ->
                    if (state == GeckoToppingHostState.Ready) ready.countDown()
                }
        }
        assertTrue(ready.await(45, TimeUnit.SECONDS))
        SystemClock.sleep(2_000L)
        EdgeToEdgeSiteFixtureServer {
            """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Live page</title>
                <style>body{margin:0;background:#e62626}
                  button{position:fixed;left:0;top:20vh;width:100vw;height:30vh}</style>
                <h1>Live page</h1><button>Change live page</button>
                <script>
                  document.querySelector('button').onclick=()=>{
                    document.body.style.background='#ffda00';
                    document.title='Live tapped:'+getComputedStyle(document.body).backgroundColor+
                      ':'+document.visibilityState+':'+document.body.getBoundingClientRect().height;
                  };
                </script>
            """.trimIndent()
        }.use { server ->
            lateinit var browser: BrowserController
            val handoff = mutableStateOf<TabHandoff?>(null)
            val drag = mutableFloatStateOf(0f)
            val bottom = mutableFloatStateOf(Float.NaN)
            composeRule.runOnIdle {
                browser = BrowserController(composeRule.activity).also { controller = it }
                browser.onStart()
                browser.onResume()
                browser.createTab(initialUrl = server.fixtureUrl("/live"))
            }
            composeRule.setContent {
                MaterialBrowserTheme {
                    BrowserViewport(
                        controller = browser,
                        webViewVideoOnlyPresentation = false,
                        selectedTab = browser.selectedTab,
                        dragOffset = drag,
                        travelDistance = composeRule.activity.window.decorView.width.toFloat(),
                        rootHeightPx = composeRule.activity.window.decorView.height.toFloat(),
                        bottomBarTopPx = bottom,
                        handoff = handoff.value,
                        handoffAlpha = 1f,
                        liveFrameTabId = null,
                        tabOverviewVisible = false,
                        // Simulate a missing native-to-Compose readiness callback.
                        onLiveFrame = {},
                        onHandoffExpired = { expired ->
                            if (handoff.value === expired) handoff.value = null
                        },
                        onSearch = {},
                        onFavorite = {},
                        blankTabModeProgress = 0f,
                        blankTabModeRevealOrigin = Offset.Zero,
                        onRetry = { false },
                        onBlurTargetAttached = {},
                        onBlurTargetReleased = {},
                    )
                }
            }
            awaitCondition { browser.selectedTab.title == "Live page" }
            val captured = CountDownLatch(1)
            composeRule.runOnIdle {
                browser.refreshSelectedTabPreview { captured.countDown() }
            }
            assertTrue("Preview capture did not finish", captured.await(10, TimeUnit.SECONDS))
            composeRule.mainClock.autoAdvance = false
            composeRule.runOnIdle {
                assertTrue(browser.previews[browser.selectedTabId] != null)
                handoff.value = TabHandoff(
                    tab = browser.selectedTab,
                    preview = browser.previews[browser.selectedTabId],
                    favicon = null,
                    previewTopInsetPx = browser.previewTopInsetPx(browser.selectedTabId),
                    isRestoring = true,
                )
                val host = requireNotNull(browser.selectedGeckoViewForTesting())
                val session = host.descendants().filterIsInstance<GeckoView>().first().session!!
                session.contentDelegate?.onPaintStatusReset(session)
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            awaitRestorationSnapshotPixels()
            composeRule.onRoot().performTouchInput { click(Offset(center.x, height * 0.35f)) }
            awaitCondition { browser.selectedTab.title.startsWith("Live tapped:") }
            composeRule.mainClock.advanceTimeBy(2_120L)
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertTrue("Restoration fade must keep its snapshot until it finishes", handoff.value != null)
            }
            awaitRestorationTransitionPixels()
            composeRule.mainClock.advanceTimeBy(400L)
            awaitCondition { handoff.value == null }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            val nativeColor = captureNativeColor()
            assertEquals(
                "Native renderer did not paint the interactive page",
                Color.rgb(255, 218, 0),
                nativeColor,
            )
            awaitLivePagePixels(nativeColor)
        }
    }

    private fun awaitRestorationTransitionPixels() {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var color = Color.TRANSPARENT
        while (SystemClock.elapsedRealtime() < deadline) {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                color = screenshot.getPixel(screenshot.width / 2, screenshot.height * 7 / 10)
                if (Color.red(color) > 150 && Color.green(color) > Color.blue(color) + 40) {
                    File(composeRule.activity.getExternalFilesDir(null), "handoff-restoration-transition.png")
                        .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    return
                }
            } finally {
                screenshot.recycle()
            }
            SystemClock.sleep(50L)
        }
        throw AssertionError("Restore did not crossfade into live color: ${Integer.toHexString(color)}")
    }

    private fun awaitRestorationSnapshotPixels() {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var color = Color.TRANSPARENT
        while (SystemClock.elapsedRealtime() < deadline) {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                color = screenshot.getPixel(screenshot.width / 2, screenshot.height * 7 / 10)
                if (
                    Color.red(color) in 40..160 &&
                    kotlin.math.abs(Color.red(color) - Color.green(color)) < 3 &&
                    kotlin.math.abs(Color.red(color) - Color.blue(color)) < 3
                ) {
                    File(composeRule.activity.getExternalFilesDir(null), "handoff-restoration-gray.png")
                        .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    return
                }
            } finally {
                screenshot.recycle()
            }
            SystemClock.sleep(50L)
        }
        throw AssertionError("Restore snapshot is not visibly gray: ${Integer.toHexString(color)}")
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            composeRule.runOnIdle { ready = condition() }
            if (ready) return
            SystemClock.sleep(25L)
        }
        throw AssertionError("Gecko handoff state did not settle")
    }

    private fun captureNativeColor(): Int {
        val captured = CountDownLatch(1)
        var color = Color.TRANSPARENT
        composeRule.runOnIdle {
            val view = requireNotNull(controller?.selectedGeckoViewForTesting())
                .descendants().filterIsInstance<GeckoView>().first()
            view.capturePixels().accept({ bitmap ->
                if (bitmap != null) {
                    color = bitmap.getPixel(bitmap.width / 2, bitmap.height * 7 / 10)
                    bitmap.recycle()
                }
                captured.countDown()
            }, { captured.countDown() })
        }
        assertTrue(captured.await(5, TimeUnit.SECONDS))
        return color
    }

    private fun awaitLivePagePixels(nativeColor: Int) {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var color = Color.TRANSPARENT
        while (SystemClock.elapsedRealtime() < deadline) {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                color = screenshot.getPixel(screenshot.width / 2, screenshot.height * 7 / 10)
            } finally {
                screenshot.recycle()
            }
            if (
                Color.red(color) > 235 && Color.green(color) in 195..240 &&
                Color.blue(color) < 25
            ) return
            SystemClock.sleep(50L)
        }
        var images = "unknown"
        composeRule.runOnIdle {
            images = controller?.selectedGeckoViewForTesting()?.descendants()
                ?.filterIsInstance<ImageView>()
                ?.map { "visibility=${it.visibility}, drawable=${it.drawable != null}" }
                ?.toList().toString()
        }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(composeRule.activity.getExternalFilesDir(null), "handoff-live-timeout.png")
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
        throw AssertionError(
            "Live tapped page remains hidden: ${Integer.toHexString(color)}, " +
                "native=${Integer.toHexString(nativeColor)}, " +
                "title=${controller?.selectedTab?.title}, images=$images",
        )
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        val group = this@descendants as? ViewGroup ?: return@sequence
        repeat(group.childCount) { index -> yieldAll(group.getChildAt(index).descendants()) }
    }

    private fun preferences() = composeRule.activity.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
}
