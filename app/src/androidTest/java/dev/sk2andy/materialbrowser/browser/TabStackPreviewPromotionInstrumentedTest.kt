package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class TabStackPreviewPromotionInstrumentedTest {
    @Test
    fun promotedPreviewInheritsCurrentStack() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        BrowserSessionStore(context).apply {
            saveStartupAnimationEnabled(false)
            saveOpenHomeOnStartupEnabled(false)
            saveSearchSuggestionProvider(SearchSuggestionProvider.None)
            assertTrue(saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
        }
        try {
            EdgeToEdgeSiteFixtureServer { "<html><body>Stack preview fixture</body></html>" }.use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    var sessionId = 0L
                    scenario.onActivity { activity ->
                        val browser = activity.browserControllerForTesting()
                        val first = browser.selectedTabId
                        val second = browser.createTab()
                        browser.createTabStack(listOf(first, second), "Research", TabStackColor.Blueberry)
                        browser.updateNewTabsInCurrentStackEnabled(true)
                        assertTrue(browser.openExternalLinkPreview(server.fixtureUrl("/stack")))
                        sessionId = requireNotNull(browser.externalLinkPreviewState).sessionId
                    }
                    val readyDeadline = SystemClock.uptimeMillis() + 10_000
                    var ready = false
                    while (!ready && SystemClock.uptimeMillis() < readyDeadline) {
                        scenario.onActivity { activity ->
                            val browser = activity.browserControllerForTesting()
                            ready = browser.externalLinkPreviewEngineViewForTesting() != null &&
                                browser.externalLinkPreviewState?.isLoading == false
                        }
                        if (!ready) SystemClock.sleep(50)
                    }
                    assertTrue("Live preview must exist before promotion", ready)
                    var result: ExternalLinkPreviewCommitResult? = null
                    scenario.onActivity { activity ->
                        activity.browserControllerForTesting().commitExternalLinkPreview(sessionId) { result = it }
                    }
                    val deadline = SystemClock.uptimeMillis() + 10_000
                    var opened = false
                    while (!opened && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { opened = result is ExternalLinkPreviewCommitResult.Opened }
                        if (!opened) SystemClock.sleep(50)
                    }
                    assertTrue("Preview should promote without another permission grant", opened)
                    scenario.onActivity { activity ->
                        val browser = activity.browserControllerForTesting()
                        val tabId = (result as ExternalLinkPreviewCommitResult.Opened).tabId
                        assertTrue(tabId in browser.activeTabStacks.single().tabIds)
                    }
                }
            }
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
