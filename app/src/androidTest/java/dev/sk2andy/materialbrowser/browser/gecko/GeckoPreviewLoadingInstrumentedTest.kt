package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoPreviewLoadingInstrumentedTest {
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
    fun unsuccessfulPageStopEndsLinkPeekLoadingWithoutReportingEarlyCompletion() {
        activityRule.scenario.onActivity { activity ->
            val browser = createController(activity)
            val progressChanges = mutableListOf<Int>()
            val view = browser.createLinkPeekPreviewView(
                url = PAGE_URL,
                onProgressChanged = progressChanges::add,
                onCommittedUrlChanged = {},
            )
            val session = requireNotNull(nativeView(view)?.session)
            val delegate = requireNotNull(session.progressDelegate)

            delegate.onPageStop(session, false)
            assertTrue(progressChanges.isEmpty())

            delegate.onPageStart(session, PAGE_URL)
            delegate.onProgressChange(session, 100)
            assertEquals(listOf(0), progressChanges)

            delegate.onPageStop(session, false)

            assertEquals(listOf(0, 100), progressChanges)
        }
    }

    @Test
    fun unsuccessfulPageStopEndsExternalPreviewLoading() {
        lateinit var browser: BrowserController
        lateinit var session: GeckoSession
        lateinit var delegate: GeckoSession.ProgressDelegate
        var intermediateProgress = 0
        activityRule.scenario.onActivity { activity ->
            browser = createController(activity)
            assertTrue(browser.openExternalLinkPreview(PAGE_URL))
            assertTrue(browser.prepareExternalLinkPreview(requireNotNull(browser.externalLinkPreviewState).sessionId))
            val view = requireNotNull(browser.externalLinkPreviewEngineViewForTesting())
            session = requireNotNull(nativeView(view)?.session)
            delegate = requireNotNull(session.progressDelegate)
            delegate.onPageStop(session, false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity {
            assertTrue(requireNotNull(browser.externalLinkPreviewState).isLoading)
            delegate.onPageStart(session, PAGE_URL)
            delegate.onProgressChange(session, 65)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity {
            assertTrue(requireNotNull(browser.externalLinkPreviewState).isLoading)
            intermediateProgress = requireNotNull(browser.externalLinkPreviewState).progress
            delegate.onPageStop(session, false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity {
            val state = requireNotNull(browser.externalLinkPreviewState)
            assertFalse(state.isLoading)
            assertEquals(100, state.progress)
            assertEquals(PAGE_URL, state.currentUrl)
            assertEquals(65, intermediateProgress)
        }
    }

    private fun createController(activity: ComponentActivity): BrowserController {
        assertTrue(BrowserSessionStore(activity).saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
        return BrowserController(activity).also { controller = it }
    }

    private fun nativeView(view: View): GeckoView? = when (view) {
        is GeckoView -> view
        is ViewGroup -> (0 until view.childCount)
            .firstNotNullOfOrNull { index -> nativeView(view.getChildAt(index)) }
        else -> null
    }

    private companion object {
        const val PAGE_URL = "https://example.com/preview"
    }
}
