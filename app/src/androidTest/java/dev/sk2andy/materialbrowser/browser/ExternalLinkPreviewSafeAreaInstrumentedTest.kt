package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.gecko.CandyGeckoView
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEvent
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalLinkPreviewSafeAreaInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private var controller: BrowserController? = null
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val originalPreferences = preferences.all.toMap()

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { controller?.destroy() }
        val editor = preferences.edit()
        // Restore the fixture without clearing unrelated existing browser settings.
        (preferences.all.keys - originalPreferences.keys).forEach(editor::remove)
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue(editor.commit())
        assertEquals(originalPreferences, preferences.all)
    }

    @Test
    fun hiddenKeyboardInsetDoesNotShrinkExternalPreview() {
        activityRule.scenario.onActivity { activity ->
            val browser = preparePreview(activity)
            val container = FrameLayout(activity)
            activity.setContentView(container)
            browser.attachExternalLinkPreview(container)
            assertTrue(previewView(browser).isAttachedToWindow)
            val hiddenKeyboardInsets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 360))
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()

            browser.onWindowInsetsChanged(hiddenKeyboardInsets)

            assertEquals(0, previewView(browser).insetLayoutSnapshot().margins.bottom)

            val visibleKeyboardInsets = WindowInsetsCompat.Builder(hiddenKeyboardInsets)
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            browser.onWindowInsetsChanged(visibleKeyboardInsets)

            assertEquals(360, previewView(browser).insetLayoutSnapshot().margins.bottom)
        }
    }

    @Test
    fun lateHeaderFallbackMovesPreviewRendererWithoutNavigating() {
        activityRule.scenario.onActivity { activity ->
            val browser = preparePreview(activity)
            val state = requireNotNull(browser.externalLinkPreviewState)
            val generation = browser.externalLinkPreviewSafeAreaNavigationGenerationForTesting

            browser.dispatchExternalLinkPreviewPrivacyEventForTesting(fallback(generation))

            val layout = previewView(browser).insetLayoutSnapshot()
            assertEquals(96, layout.margins.top)
            assertEquals(0, layout.rendererSafeAreaOverride?.top)
            assertEquals(WebContentTopInsetTransitionState.WebContentHeader, layout.topInsetTransitionState)
            assertEquals(state, browser.externalLinkPreviewState)
            browser.dispatchExternalLinkPreviewPrivacyEventForTesting(fallback(generation))
            assertEquals(layout, previewView(browser).insetLayoutSnapshot())
        }
    }

    @Test
    fun newDocumentClearsFallbackAndRejectsPreviousDocumentReport() {
        activityRule.scenario.onActivity { activity ->
            val browser = preparePreview(activity)
            val oldGeneration = browser.externalLinkPreviewSafeAreaNavigationGenerationForTesting
            browser.dispatchExternalLinkPreviewPrivacyEventForTesting(fallback(oldGeneration))
            assertEquals(96, previewView(browser).insetLayoutSnapshot().margins.top)

            browser.dispatchExternalPreviewEventForTesting(event(BrowserEngineEventType.NavigationStarted, PAGE_URL))

            val layout = previewView(browser).insetLayoutSnapshot()
            assertEquals(0, layout.margins.top)
            assertEquals(96, layout.rendererSafeAreaOverride?.top)
            browser.dispatchExternalLinkPreviewPrivacyEventForTesting(fallback(oldGeneration))
            assertEquals(layout, previewView(browser).insetLayoutSnapshot())
            browser.dispatchExternalLinkPreviewPrivacyEventForTesting(
                fallback(browser.externalLinkPreviewSafeAreaNavigationGenerationForTesting),
            )
            assertEquals(96, previewView(browser).insetLayoutSnapshot().margins.top)
        }
    }

    @Test
    fun sameDocumentRouteClearsGenericFallbackAndRejectsOldReport() {
        activityRule.scenario.onActivity { activity ->
            val browser = preparePreview(activity)
            browser.dispatchExternalPreviewEventForTesting(event(BrowserEngineEventType.NavigationCommitted, PAGE_URL))
            val oldGeneration = browser.externalLinkPreviewSafeAreaNavigationGenerationForTesting
            browser.dispatchExternalLinkPreviewPrivacyEventForTesting(fallback(oldGeneration, topHeader = false))
            assertEquals(96, previewView(browser).insetLayoutSnapshot().margins.top)
            assertEquals(
                WebContentTopInsetTransitionState.Other,
                previewView(browser).insetLayoutSnapshot().topInsetTransitionState,
            )

            browser.dispatchExternalPreviewEventForTesting(
                event(BrowserEngineEventType.StateChanged, "https://example.com/next"),
            )

            assertEquals(0, previewView(browser).insetLayoutSnapshot().margins.top)
            browser.dispatchExternalLinkPreviewPrivacyEventForTesting(fallback(oldGeneration))
            assertEquals(0, previewView(browser).insetLayoutSnapshot().margins.top)
        }
    }

    private fun preparePreview(activity: ComponentActivity): BrowserController {
        assertTrue(BrowserSessionStore(activity).saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
        val browser = BrowserController(activity).also { controller = it }
        browser.updateDeveloperSettings(DeveloperSettings())
        browser.onWindowInsetsChanged(
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
                .build(),
        )
        assertTrue(browser.openExternalLinkPreview(PAGE_URL))
        assertTrue(browser.prepareExternalLinkPreview(requireNotNull(browser.externalLinkPreviewState).sessionId))
        return browser
    }

    private fun previewView(browser: BrowserController) =
        browser.externalLinkPreviewEngineViewForTesting() as CandyGeckoView

    private fun fallback(generation: Int, topHeader: Boolean = true) = GeckoPrivacyEvent(
        requestUrl = "",
        pageUrl = PAGE_URL,
        ruleId = null,
        wasBlocked = false,
        isBuiltIn = false,
        isCompatibilityObservation = false,
        safeAreaFallbackNavigationGeneration = generation,
        safeAreaFallbackIsTopHeader = topHeader,
    )

    private fun event(type: BrowserEngineEventType, url: String) = BrowserEngineEvent(
        tabId = "preview",
        type = type,
        address = url,
        title = null,
        canGoBack = false,
        canGoForward = false,
        failureDescription = null,
    )

    private companion object {
        const val PAGE_URL = "https://example.com/first"
    }
}
