package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarParkingInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            clearPreferences(activity)
        }
    }

    @Test
    fun successfulPageLoadParksWhenGlobalSettingIsEnabled() {
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            controller.updateAddressBarDocked(false)
            controller.updateAlwaysParkAddressBarAfterLoad(true)

            assertFalse(controller.isAddressBarDocked)

            controller.selectedWebViewForTesting().loadUrl(
                "data:text/html,<html><body>ready</body></html>",
            )
        }

        val parked = awaitCondition { controller?.isAddressBarDocked == true }
        val diagnostics = AtomicReference<String>()
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            diagnostics.set(
                "tab=${controller.selectedTab}; " +
                    "webViewUrl=${controller.selectedWebViewForTesting().url}",
            )
            assertFalse(BrowserSessionStore(it).loadAddressBarDocked())
        }
        assertTrue(diagnostics.get(), parked)
    }

    @Test
    fun leavingConfiguredDomainRestoresManualPlacement() {
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            controller.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://news.example/",
                "<html><body>news</body></html>",
                "text/html",
                "UTF-8",
                null,
            )
        }
        assertTrue(
            awaitCondition {
                controller?.selectedTab?.let { tab ->
                    tab.url == "https://news.example/" && !tab.isLoading
                } == true
            },
        )

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            assertTrue(controller.setSelectedAddressBarAlwaysParked(true))
            assertTrue(controller.isAddressBarDocked)
            controller.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://other.example/",
                "<html><body>other</body></html>",
                "text/html",
                "UTF-8",
                null,
            )
        }
        assertTrue(
            awaitCondition {
                controller?.selectedTab?.let { tab ->
                    tab.url == "https://other.example/" && !tab.isLoading
                } == true
            },
        )
        activityRule.scenario.onActivity {
            assertFalse(requireNotNull(controller).isAddressBarDocked)
        }
    }

    @Test
    fun regularDomainPreferencePersistsWhilePrivatePreferenceDoesNot() {
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            var controller = BrowserController(activity).also { this.controller = it }
            val regularTabId = controller.createTab("https://news.example.test/")
            val privateTabId = controller.createTab(
                initialUrl = "https://private.example.test/",
                isIncognito = true,
            )

            assertTrue(controller.setAddressBarAlwaysParked(regularTabId, true))
            assertTrue(controller.setAddressBarAlwaysParked(privateTabId, true))
            controller.destroy()

            controller = BrowserController(activity).also { this.controller = it }
            val restoredRegularTab = controller.activeTabs.first { tab ->
                tab.url == "https://news.example.test/"
            }
            val freshPrivateTabId = controller.createTab(
                initialUrl = "https://private.example.test/",
                isIncognito = true,
            )

            assertTrue(controller.isAddressBarAlwaysParked(restoredRegularTab.id))
            assertFalse(controller.isAddressBarAlwaysParked(freshPrivateTabId))
        }
    }

    private fun awaitCondition(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val matched = AtomicBoolean()
            activityRule.scenario.onActivity { matched.set(condition()) }
            if (matched.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun clearPreferences(activity: ComponentActivity) {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
