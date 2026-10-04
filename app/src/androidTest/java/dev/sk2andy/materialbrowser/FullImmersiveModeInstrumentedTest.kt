package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.ui.AddressBarTestTags
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullImmersiveModeInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences("browser_session", Context.MODE_PRIVATE)
    }
    private val onboardingPreferences by lazy {
        context.getSharedPreferences(GestureOnboardingStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        onboardingPreferences.edit()
            .putInt(
                GestureOnboardingStore.KEY_COMPLETED_VERSION,
                GestureOnboardingStore.CURRENT_VERSION,
            )
            .commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        onboardingPreferences.edit().clear().commit()
    }

    @Test
    fun toggleHidesBarsWithoutResizingForImeAndRestoresWindowMode() {
        // The IME owns navigation-bar visibility; this check exercises browser window flags.
        BrowserSessionStore(context).saveStartupAddressFocusMode(StartupAddressFocusMode.Never)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))
            val originalSoftInputMode = AtomicReference<Int>()

            scenario.onActivity { activity ->
                originalSoftInputMode.set(activity.window.attributes.softInputMode)
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(true)

                assertEquals(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                    activity.window.attributes.softInputMode.adjustmentMode(),
                )
                activity.applyFullImmersiveMode(
                    enabled = true,
                    keepWindowFullHeightForIme = true,
                )
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = false))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(false)

                assertEquals(
                    originalSoftInputMode.get(),
                    activity.window.attributes.softInputMode,
                )
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))
        }
    }

    @Test
    fun webContentFullscreenExitRestoresSystemBars() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting()
                    .reportSelectedBrowserEngineFullscreenStateForTesting(true)
                assertEquals(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView,
                    ).systemBarsBehavior,
                )
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = false))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting()
                    .reportSelectedBrowserEngineFullscreenStateForTesting(false)
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))
            scenario.onActivity { activity ->
                assertEquals(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView,
                    ).systemBarsBehavior,
                )
            }
        }
    }

    @Test
    fun systemWebViewLandscapeVideoFullscreenRestoresOrientationOnExit() {
        BrowserSessionStore(context).saveAndroidBrowserEngineKind(
            AndroidBrowserEngineKind.SystemWebView,
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.reportSelectedBrowserEngineFullscreenStateForTesting(true)
                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.requestedOrientation,
                )
                controller.reportSelectedBrowserEngineMediaStateForTesting(
                    GeckoMediaSessionState(
                        isActive = true,
                        isPlaying = true,
                        isFullscreen = true,
                        videoTrackCount = 1,
                        videoWidth = 1_920,
                        videoHeight = 1_080,
                    ),
                )
                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    activity.requestedOrientation,
                )
            }
            scenario.onActivity { activity ->
                activity.browserControllerForTesting()
                    .reportSelectedBrowserEngineFullscreenStateForTesting(false)
                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.requestedOrientation,
                )
            }
        }
    }

    @Test
    fun systemWebViewGameFullscreenKeepsLockedPortraitAndRestoresPage() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val originalRotation = Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
        )
        val originalUserRotation = Settings.System.getInt(
            context.contentResolver,
            Settings.System.USER_ROTATION,
        )
        BrowserSessionStore(context).saveStartupAddressFocusMode(StartupAddressFocusMode.Never)
        BrowserSessionStore(context).saveAndroidBrowserEngineKind(
            AndroidBrowserEngineKind.SystemWebView,
        )
        val tab = BrowserTab(
            id = "system-webview-fullscreen-fixture",
            lastAccessedAt = System.currentTimeMillis(),
            url = "https://fullscreen.test/",
        )
        assertTrue(BrowserSessionStore(context).saveTabsImmediately(listOf(tab), tab.id))
        device.executeShellCommand("settings put system accelerometer_rotation 0")
        device.executeShellCommand("settings put system user_rotation 0")
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        try {
            val intent = Intent(context, MainActivity::class.java)
                .setAction("dev.sk2andy.materialbrowser.test.SYSTEM_WEBVIEW_FULLSCREEN")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                lateinit var webView: WebView
                composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                    var ready = false
                    scenario.onActivity { activity ->
                        val view = activity.browserControllerForTesting()
                            .selectedBrowserEngineViewForTesting()
                            ?.findWebView()
                        if (
                            view != null && view.isAttachedToWindow && view.height > view.width &&
                            activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                        ) {
                            webView = view
                            ready = true
                        }
                    }
                    ready
                }
                scenario.onActivity {
                    webView.loadDataWithBaseURL(
                        "https://fullscreen.test/",
                        """
                            <!doctype html><html><head><meta name="viewport" content="width=device-width"></head>
                            <body style="height:2000px">
                            <div id="game" style="margin-top:400px"><iframe srcdoc="<canvas></canvas>"></iframe>
                            <button style="position:fixed;left:50%;top:50%;width:50%;height:64px;
                              transform:translate(-50%,-50%)"
                              onclick="window.fullscreenClicked = true;
                                document.querySelector('#game').requestFullscreen()">Fullscreen</button>
                            </div><script>window.pageIdentity = 'original-game';</script></body></html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
                composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                    evaluateWebView(scenario, webView, "window.pageIdentity === 'original-game'") == "true"
                }
                assertTrue(awaitImeVisibility(scenario, expectedVisible = false))
                composeRule.waitForIdle()
                evaluateWebView(scenario, webView, "window.scrollTo(0, 400)")
                composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                    var scrolled = false
                    scenario.onActivity { scrolled = webView.scrollY > 0 }
                    scrolled
                }
                val originalScroll = evaluateWebView(scenario, webView, "window.scrollY")
                val buttonRect = JSONArray(
                    evaluateWebView(
                        scenario,
                        webView,
                        "(() => { const r = document.querySelector('button').getBoundingClientRect(); " +
                            "return [r.left + r.width / 2, r.top + r.height / 2, innerWidth]; })()",
                    ),
                )
                val fullscreenButton = IntArray(2)
                scenario.onActivity {
                    val location = IntArray(2)
                    webView.getLocationOnScreen(location)
                    val scale = webView.width / buttonRect.getDouble(2)
                    fullscreenButton[0] = location[0] + (buttonRect.getDouble(0) * scale).toInt()
                    fullscreenButton[1] = location[1] + (buttonRect.getDouble(1) * scale).toInt()
                }
                assertTrue(device.click(fullscreenButton[0], fullscreenButton[1]))
                assertEquals("true", evaluateWebView(scenario, webView, "window.fullscreenClicked"))
                composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                    evaluateWebView(scenario, webView, "document.fullscreenElement?.id") == "\"game\""
                }
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().isSelectedWebContentFullscreen)
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
                    assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
                    assertTrue(activity.browserControllerForTesting().exitSelectedWebContentFullscreen())
                }
                composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                    evaluateWebView(scenario, webView, "document.fullscreenElement === null") == "true"
                }
                assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))
                assertEquals("\"original-game\"", evaluateWebView(scenario, webView, "window.pageIdentity"))
                composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                    evaluateWebView(scenario, webView, "window.scrollY") == originalScroll
                }
                scenario.onActivity { activity ->
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
                    assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
                }
            }
        } finally {
            device.executeShellCommand("settings put system user_rotation $originalUserRotation")
            device.executeShellCommand("settings put system accelerometer_rotation $originalRotation")
        }
    }

    @Test
    fun immersiveAddressEditorKeepsFullRootHeightAcrossImeTransition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(AddressBarTestTags.Editor)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(awaitImeVisibility(scenario, expectedVisible = true))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(true)
            }
            assertTrue(awaitFullRootHeight(scenario))

            val fullRootHeight = AtomicReference<Int>()
            scenario.onActivity { activity ->
                val content = activity.findViewById<View>(android.R.id.content)
                fullRootHeight.set(content.height)
                assertEquals(
                    activity.windowManager.currentWindowMetrics.bounds.height(),
                    content.height,
                )
            }

            val editorBottomWithIme = composeRule.onNodeWithTag(AddressBarTestTags.Editor)
                .fetchSemanticsNode().boundsInRoot.bottom
            scenario.onActivity { activity ->
                val content = activity.findViewById<View>(android.R.id.content)
                val imeBottom = requireNotNull(ViewCompat.getRootWindowInsets(content))
                    .getInsets(WindowInsetsCompat.Type.ime())
                    .bottom
                assertEquals(fullRootHeight.get(), content.height)
                assertTrue(editorBottomWithIme <= content.height - imeBottom)
                WindowCompat.getInsetsController(activity.window, content)
                    .hide(WindowInsetsCompat.Type.ime())
            }
            assertTrue(awaitImeVisibility(scenario, expectedVisible = false))

            composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                composeRule.onNodeWithTag(AddressBarTestTags.Editor)
                    .fetchSemanticsNode().boundsInRoot.bottom > editorBottomWithIme
            }
            scenario.onActivity { activity ->
                assertEquals(
                    fullRootHeight.get(),
                    activity.findViewById<View>(android.R.id.content).height,
                )
            }
        }
    }

    private fun evaluateWebView(
        scenario: ActivityScenario<MainActivity>,
        webView: WebView,
        expression: String,
    ): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        scenario.onActivity {
            webView.evaluateJavascript(expression) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue("JavaScript result timed out", completed.await(5, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findWebView()
        }
        else -> null
    }

    private fun awaitSystemBarsVisibility(
        scenario: ActivityScenario<MainActivity>,
        expectedVisible: Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + VISIBILITY_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val matchesExpectedVisibility = AtomicReference(false)
            scenario.onActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                matchesExpectedVisibility.set(
                    insets != null &&
                        insets.isVisible(WindowInsetsCompat.Type.statusBars()) == expectedVisible &&
                        insets.isVisible(WindowInsetsCompat.Type.navigationBars()) == expectedVisible,
                )
            }
            if (matchesExpectedVisibility.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitImeVisibility(
        scenario: ActivityScenario<MainActivity>,
        expectedVisible: Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + VISIBILITY_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val matchesExpectedVisibility = AtomicReference(false)
            scenario.onActivity { activity ->
                matchesExpectedVisibility.set(
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == expectedVisible,
                )
            }
            if (matchesExpectedVisibility.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitFullRootHeight(scenario: ActivityScenario<MainActivity>): Boolean {
        val deadline = SystemClock.uptimeMillis() + VISIBILITY_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val rootIsFullHeight = AtomicReference(false)
            scenario.onActivity { activity ->
                rootIsFullHeight.set(
                    activity.findViewById<View>(android.R.id.content).height ==
                        activity.windowManager.currentWindowMetrics.bounds.height(),
                )
            }
            if (rootIsFullHeight.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private companion object {
        const val VISIBILITY_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}

private fun Int.adjustmentMode(): Int =
    this and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
