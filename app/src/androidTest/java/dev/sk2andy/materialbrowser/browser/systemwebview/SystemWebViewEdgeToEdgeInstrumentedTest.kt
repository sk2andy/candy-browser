package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemWebViewEdgeToEdgeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { BrowserSessionStore(context) }
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        val tab = BrowserTab(
            id = "system-webview-edge-to-edge-fixture",
            lastAccessedAt = System.currentTimeMillis(),
            url = TEST_URL,
        )
        assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun selectedSystemWebViewStartsAtWindowTop() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedBrowserEngineViewForTesting())
                val webView = requireNotNull(view.findSystemWebView())

                assertWindowTop(view, expectedTop = 0)
                assertWindowTop(webView, expectedTop = 0)
                assertEquals(0, (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                assertEquals(0, (webView.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
            }
        }
    }

    private fun awaitViewReady(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.let { view ->
                        view.isAttachedToWindow &&
                            view.width > 0 &&
                            view.height > 0 &&
                            view.findSystemWebView() != null
                    } == true
            }
            if (ready) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("System WebView did not become ready", false)
    }

    private fun View.findSystemWebView(): android.webkit.WebView? = when (this) {
        is android.webkit.WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findSystemWebView()
        }
        else -> null
    }

    private fun assertWindowTop(view: View, expectedTop: Int) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        assertEquals(expectedTop, location[1])
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION =
            "dev.sk2andy.materialbrowser.test.SYSTEM_WEBVIEW_EDGE_TO_EDGE"
        const val TEST_URL = "https://example.invalid/candy-system-webview-edge-to-edge"
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
    }
}
