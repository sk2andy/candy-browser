package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopViewInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun toggleAppliesAndResetsDesktopWebViewSettings() {
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            val tabId = controller.createTab("https://mobile.example.test/page")
            val webView = controller.selectedWebViewForTesting()
            val defaultUserAgent = WebSettings.getDefaultUserAgent(activity)
            val defaultMetadata = if (
                WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
            ) {
                WebSettingsCompat.getUserAgentMetadata(webView.settings)
            } else {
                null
            }

            assertTrue(controller.setDesktopView(tabId, true))

            assertTrue(controller.isDesktopView(tabId))
            assertFalse(webView.settings.userAgentString.contains("Android", ignoreCase = true))
            assertFalse(webView.settings.userAgentString.contains("Mobile", ignoreCase = true))
            assertTrue(webView.settings.useWideViewPort)
            assertTrue(webView.settings.loadWithOverviewMode)
            if (defaultMetadata != null) {
                assertFalse(WebSettingsCompat.getUserAgentMetadata(webView.settings).isMobile)
            }

            assertTrue(controller.setDesktopView(tabId, false))

            assertFalse(controller.isDesktopView(tabId))
            assertEquals(defaultUserAgent, webView.settings.userAgentString)
            assertFalse(webView.settings.useWideViewPort)
            assertFalse(webView.settings.loadWithOverviewMode)
            if (defaultMetadata != null) {
                assertEquals(
                    defaultMetadata,
                    WebSettingsCompat.getUserAgentMetadata(webView.settings),
                )
            }
        }
    }

    @Test
    fun regularPreferencePersistsWhilePrivatePreferenceDoesNot() {
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            var controller = BrowserController(activity).also { this.controller = it }
            val regularTabId = controller.createTab("https://news.example.test/")
            val privateTabId = controller.createTab(
                initialUrl = "https://private.example.test/",
                isIncognito = true,
            )

            assertTrue(controller.setDesktopView(regularTabId, true))
            assertTrue(controller.setDesktopView(privateTabId, true))
            controller.destroy()

            controller = BrowserController(activity).also { this.controller = it }
            val restoredRegularTab = controller.activeTabs.first { tab ->
                tab.url == "https://news.example.test/"
            }
            val freshPrivateTabId = controller.createTab(
                initialUrl = "https://private.example.test/",
                isIncognito = true,
            )

            assertTrue(controller.isDesktopView(restoredRegularTab.id))
            assertFalse(controller.isDesktopView(freshPrivateTabId))
        }
    }

    private fun clearPreferences(activity: ComponentActivity) {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
