package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class AddressBarAutoDockEngineInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = BrowserSessionStore(context)

    @After
    fun tearDown() = clearSession()

    @Test
    fun systemWebViewManualExpansionSurvivesComposerAndAddressKeyboardTransitions() {
        assertManualExpansion(AndroidBrowserEngineKind.SystemWebView)
    }

    @Test
    fun geckoManualExpansionSurvivesComposerAndAddressKeyboardTransitions() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        assertManualExpansion(AndroidBrowserEngineKind.GeckoView)
    }

    private fun assertManualExpansion(engine: AndroidBrowserEngineKind) {
        clearSession()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        assertTrue(store.saveAndroidBrowserEngineKind(engine))
        store.saveStartupAnimationEnabled(false)
        store.saveOpenHomeOnStartupEnabled(false)
        store.saveExternalLinkPreviewEnabled(false)
        store.saveSearchSuggestionProvider(SearchSuggestionProvider.None)
        store.saveAddressBarDockingEnabled(true)
        store.saveAddressBarDocked(false)

        EdgeToEdgeSiteFixtureServer { fixtureHtml() }.use { server ->
            val tab = BrowserTab(
                id = "manual-expansion-${engine.stableId}",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.fixtureUrl("/site-matrix/composer"),
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            val intent = Intent(context, MainActivity::class.java)
                .setAction("dev.sk2andy.materialbrowser.test.ADDRESS_BAR_AUTO_DOCK")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                awaitController(scenario) { controller ->
                    controller.selectedTab.title == READY_TITLE &&
                        !controller.selectedTab.isLoading && controller.isAddressBarDocked
                }
                scenario.onActivity { activity ->
                    val view = activity.browserControllerForTesting().selectedBrowserEngineViewForTesting()
                    assertTrue(
                        "Expected real $engine renderer",
                        if (engine == AndroidBrowserEngineKind.SystemWebView) {
                            view is WebView
                        } else {
                            (view as? ViewGroup)?.getChildAt(0) is GeckoView
                        },
                    )
                }
                composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab).performClick()
                assertRemainsUnparked(scenario)

                repeat(2) {
                    // Focus the real bottom composer; its same-document URL update must not
                    // release manual suppression when the page keyboard changes geometry.
                    tapComposer(scenario)
                    awaitIme(scenario, visible = true)
                    awaitController(scenario) { it.selectedTab.url.endsWith("#response") }
                    hideIme(scenario)
                    assertRemainsUnparked(scenario)

                    if (hasCompactAddressBar()) {
                        composeRule.onNodeWithTag(
                            testTag = AddressBarDockTestTags.CompactAddress,
                            useUnmergedTree = true,
                        )
                            .performTouchInput { click(center) }
                    }
                    composeRule.onNodeWithTag(AddressBarTestTags.PrimaryField).performClick()
                    composeRule.onNodeWithTag(
                        testTag = AddressBarTestTags.Editor,
                        useUnmergedTree = true,
                    )
                        .assertIsFocused()
                        .performTextReplacement("https://editable.test/")
                    composeRule.onNodeWithTag(
                        testTag = AddressBarTestTags.Editor,
                        useUnmergedTree = true,
                    )
                        .assertTextEquals("https://editable.test/")
                    awaitIme(scenario, visible = true)
                    composeRule.onNodeWithContentDescription(
                        context.getString(R.string.cd_close_address_input),
                    ).performClick()
                    hideIme(scenario)
                    assertRemainsUnparked(scenario)
                }
                assertEquals("Keyboard and same-document updates must not reload", 1, server.documentRequestCount.get())

                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().submitAddress(
                        server.fixtureUrl("/site-matrix/new-document"),
                    )
                }
                awaitController(scenario) { controller ->
                    controller.selectedTab.url.endsWith("/new-document") &&
                        !controller.selectedTab.isLoading && controller.isAddressBarDocked
                }
                assertEquals("New document permits automatic parking again", 2, server.documentRequestCount.get())
            }
        }
    }

    private fun assertRemainsUnparked(scenario: ActivityScenario<MainActivity>) {
        // Observe more than two regular probe intervals, including callbacks after IME settle.
        val deadline = SystemClock.elapsedRealtime() + 4_500L
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                assertFalse("Manual expansion was undone", activity.browserControllerForTesting().isAddressBarDocked)
            }
            SystemClock.sleep(100L)
        }
        // Page IME scrolling may compact normal chrome while Compose renders the transition.
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            hasCompactAddressBar() ||
                composeRule.onNodeWithTag(AddressBarTestTags.PrimaryField).isDisplayed()
        }
        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab).assertDoesNotExist()
    }

    private fun hasCompactAddressBar(): Boolean =
        composeRule.onNodeWithTag(
            testTag = AddressBarDockTestTags.CompactAddress,
            useUnmergedTree = true,
        ).isDisplayed()

    private fun tapComposer(scenario: ActivityScenario<MainActivity>) {
        var x = 0f
        var y = 0f
        scenario.onActivity { activity ->
            val view = requireNotNull(activity.browserControllerForTesting().selectedBrowserEngineViewForTesting())
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            x = location[0] + view.width * 0.2f
            y = location[1] + view.height - 130f * view.resources.displayMetrics.density
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private fun hideIme(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        awaitIme(scenario, visible = false)
    }

    private fun awaitIme(scenario: ActivityScenario<MainActivity>, visible: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var matches = false
            scenario.onActivity { activity ->
                matches = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            if (matches) return
            SystemClock.sleep(50L)
        }
        throw AssertionError("Expected keyboard visible=$visible")
    }

    private fun awaitController(
        scenario: ActivityScenario<MainActivity>,
        condition: (BrowserController) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var matches = false
            scenario.onActivity { activity -> matches = condition(activity.browserControllerForTesting()) }
            if (matches) return
            SystemClock.sleep(50L)
        }
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            assertTrue("Controller state did not settle: ${controller.selectedTab}", condition(controller))
        }
    }

    private fun clearSession() {
        for (name in listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
            ReleaseNotesStore.PREFERENCES_NAME,
        )) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    private fun fixtureHtml(): String = """
        <!doctype html>
        <html><head>
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <link rel="icon" href="data:,">
          <title>$READY_TITLE</title>
          <style>
            html,body { margin:0; }
            main { height:300vh; }
            textarea { position:fixed; bottom:16px; left:10%; width:80%; height:140px; }
          </style>
        </head><body>
          <main>AI response with bottom-pinned composer</main>
          <textarea aria-label="Ask anything" onfocus="history.replaceState({}, '', '#response')"></textarea>
        </body></html>
    """.trimIndent()

    private companion object {
        const val READY_TITLE = "Candy manual expansion fixture"
    }
}
