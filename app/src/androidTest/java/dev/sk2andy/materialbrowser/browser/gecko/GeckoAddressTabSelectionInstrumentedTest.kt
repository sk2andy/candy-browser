package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.ui.AddressBarTestTags
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoAddressTabSelectionInstrumentedTest {
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
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        store.saveStartupAnimationEnabled(false)
        store.saveOpenHomeOnStartupEnabled(false)
        store.saveSearchSuggestionProvider(SearchSuggestionProvider.None)
        store.saveRecallEnabled(false)
        store.saveAddressBarActionLayout(AddressBarActionLayout.Default)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun switchToOpenTabSuggestionAfterNewTabKeyboardRestoresGeckoGeometryAndPixels() {
        verifyTabSelection(immersive = false)
    }

    @Test
    fun immersiveSwitchToOpenTabSuggestionAfterNewTabKeyboardRestoresGeckoGeometryAndPixels() {
        verifyTabSelection(immersive = true)
    }

    @Test
    fun frostedSwitchToOpenTabSuggestionAfterNewTabKeyboardRestoresGeckoGeometryAndPixels() {
        store.saveAppearanceSettings(
            store.loadAppearanceSettings().copy(surfaceStyle = BrowserSurfaceStyle.Frosted),
        )
        verifyTabSelection(immersive = false)
    }

    private fun verifyTabSelection(immersive: Boolean) {
        store.saveFullImmersiveModeEnabled(immersive)
        EdgeToEdgeSiteFixtureServer { HTML }.use { server ->
            val tab = BrowserTab(
                id = "gecko-address-tab-viewport",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.url,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                val initialReport = awaitReport(scenario) { true }
                awaitCondition("Initial keyboard remained visible") {
                    !geometry(scenario).imeVisible
                }
                awaitCondition("Initial Gecko surface did not measure") {
                    geometry(scenario).let { it.hostHeight > 0 && it.engineHeight > 0 && it.surfaceHeight > 0 }
                }
                awaitCondition("Initial website pixels did not appear") {
                    screenshot().let { pixels ->
                        try {
                            stripePixelsVisible(pixels)
                        } finally {
                            pixels.recycle()
                        }
                    }
                }
                val initialGeometry = geometry(scenario)
                val initialPixels = screenshot()
                try {
                    repeat(3) {
                        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_new_tab))
                            .performClick()
                        awaitCondition("New-tab editor did not show real keyboard") {
                            geometry(scenario).imeVisible
                        }
                        composeRule.onNodeWithTag(AddressBarTestTags.Editor)
                            .performTextReplacement(server.url)
                        composeRule.waitUntil(TIMEOUT_MILLIS) {
                            composeRule.onAllNodesWithText(context.getString(R.string.action_switch_to_tab))
                                .fetchSemanticsNodes().isNotEmpty()
                        }
                        val keyboardGeometry = geometry(scenario)
                        composeRule.onNodeWithText(context.getString(R.string.action_switch_to_tab))
                            .performClick()
                        awaitCondition("Suggestion did not select original Gecko tab") {
                            var selected = false
                            scenario.onActivity { activity ->
                                selected = activity.browserControllerForTesting().selectedTabId == tab.id
                            }
                            selected
                        }
                        awaitCondition("Keyboard/renderer geometry did not restore: ${geometry(scenario)}") {
                            geometry(scenario).let { current ->
                                !current.imeVisible && current.hostHeight == initialGeometry.hostHeight &&
                                    current.engineHeight == initialGeometry.engineHeight &&
                                    current.surfaceHeight == initialGeometry.surfaceHeight &&
                                    current.surfaceFrameHeight == initialGeometry.surfaceFrameHeight &&
                                    current.engineWidth == initialGeometry.engineWidth &&
                                    current.engineBottomMargin == initialGeometry.engineBottomMargin
                            }
                        }
                        val restoredReport = awaitReport(scenario) { current ->
                            abs(current.getDouble("height") - initialReport.getDouble("height")) < 1 &&
                                abs(current.getDouble("width") - initialReport.getDouble("width")) < 1 &&
                                abs(current.getDouble("scale") - initialReport.getDouble("scale")) < 0.01
                        }
                        assertEquals(initialReport.getDouble("density"), restoredReport.getDouble("density"), 0.01)
                        composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertDoesNotExist()
                        try {
                            awaitCondition("Returned page retained a keyboard-sized black area or changed scale") {
                                screenshot().let { restoredPixels ->
                                    try {
                                        pixelsMatch(initialPixels, restoredPixels)
                                    } finally {
                                        restoredPixels.recycle()
                                    }
                                }
                            }
                        } catch (failure: AssertionError) {
                            throw AssertionError(
                                "${failure.message}; initial=$initialGeometry; keyboard=$keyboardGeometry; " +
                                    "restored=${geometry(scenario)}",
                                failure,
                            )
                        }
                    }
                } finally {
                    initialPixels.recycle()
                }
            }
        }
    }

    private fun geometry(scenario: ActivityScenario<MainActivity>): Geometry {
        var result = Geometry()
        scenario.onActivity { activity ->
            val host = activity.browserControllerForTesting().selectedGeckoViewForTesting()
            val engine = (host as? ViewGroup)?.getChildAt(0)
            val surface = engine?.findSurface()
            result = Geometry(
                windowAdjustment = activity.window.attributes.softInputMode and
                    WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
                imeVisible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                hostHeight = host?.height ?: 0,
                engineWidth = engine?.width ?: 0,
                engineHeight = engine?.height ?: 0,
                surfaceHeight = surface?.height ?: 0,
                surfaceFrameHeight = surface?.holder?.surfaceFrame?.height() ?: 0,
                engineBottomMargin = (engine?.layoutParams as? ViewGroup.MarginLayoutParams)
                    ?.bottomMargin ?: 0,
            )
        }
        return result
    }

    private fun View.findSurface(): SurfaceView? {
        if (this is SurfaceView) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index -> getChildAt(index).findSurface()?.let { return it } }
        return null
    }

    private fun awaitReport(
        scenario: ActivityScenario<MainActivity>,
        matches: (JSONObject) -> Boolean,
    ): JSONObject {
        var result: JSONObject? = null
        var lastTitle = ""
        awaitCondition("Website viewport did not restore") {
            scenario.onActivity { activity ->
                lastTitle = activity.browserControllerForTesting().selectedTab.title
                if (lastTitle.startsWith(REPORT_PREFIX)) {
                    val report = JSONObject(lastTitle.removePrefix(REPORT_PREFIX))
                    if (matches(report)) result = report
                }
            }
            result != null
        }
        return requireNotNull(result) { "Last viewport report=$lastTitle" }
    }

    private fun awaitCondition(message: String, matches: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (matches()) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    private fun screenshot(): Bitmap {
        val captured = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        return captured.copy(Bitmap.Config.ARGB_8888, false).also { captured.recycle() }
    }

    private fun stripePixelsVisible(pixels: Bitmap): Boolean =
        listOf(0.20f, 0.45f, 0.72f).withIndex().all { (index, fraction) ->
            val color = pixels.getPixel(pixels.width / 4, (pixels.height * fraction).toInt())
            val channels = listOf(Color.red(color), Color.green(color), Color.blue(color))
            channels[index] > channels.filterIndexed { channel, _ -> channel != index }.max() + 30
        }

    private fun pixelsMatch(first: Bitmap, second: Bitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val x = first.width / 4
        return listOf(0.20f, 0.45f, 0.72f).all { fraction ->
            val y = (first.height * fraction).toInt()
            val before = first.getPixel(x, y)
            val after = second.getPixel(x, y)
            abs(Color.red(before) - Color.red(after)) < 20 &&
                abs(Color.green(before) - Color.green(after)) < 20 &&
                abs(Color.blue(before) - Color.blue(after)) < 20
        }
    }

    private data class Geometry(
        val windowAdjustment: Int = 0,
        val imeVisible: Boolean = false,
        val hostHeight: Int = 0,
        val engineWidth: Int = 0,
        val engineHeight: Int = 0,
        val surfaceHeight: Int = 0,
        val surfaceFrameHeight: Int = 0,
        val engineBottomMargin: Int = 0,
    )

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_ADDRESS_TAB_VIEWPORT"
        const val TIMEOUT_MILLIS = 30_000L
        const val REPORT_PREFIX = "Candy tab viewport: "
        val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;width:100%;height:200vh}
            .stripes{position:fixed;inset:0;background:linear-gradient(to bottom,
            #e62626 0 33%,#23bd55 33% 66%,#2955dc 66% 100%)}</style><div class="stripes"></div>
            <script>
            let previous='';
            function sample(){
              const report=JSON.stringify({height:visualViewport.height,width:visualViewport.width,
                scale:visualViewport.scale,density:devicePixelRatio});
              if(report!==previous){previous=report;document.title='$REPORT_PREFIX'+report;}
              requestAnimationFrame(sample);
            }
            requestAnimationFrame(sample);
            </script>
        """.trimIndent()
    }
}
