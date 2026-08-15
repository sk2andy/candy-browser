package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserRootBackInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun systemBackOnRootTabWithoutOpenerClosesTabAndOpensOverview() {
        val browserController = createController()
        val closingTabId = browserController.selectedTabId
        setBrowserContent(browserController)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.tabs.none { it.id == closingTabId }
        }
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root).assertExists()
    }

    @Test
    fun tabOverviewRequestsPortraitUntilClosed() {
        val browserController = createController()
        val portraitLocked = AtomicBoolean(false)
        setBrowserContent(
            browserController = browserController,
            onTabOverviewPortraitLockChanged = portraitLocked::set,
        )

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) { portraitLocked.get() }
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root).assertExists()
        composeRule.runOnIdle { assertTrue(portraitLocked.get()) }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertFalse(portraitLocked.get())
        }
    }

    private fun createController(): BrowserController {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        return browserController
    }

    private fun setBrowserContent(
        browserController: BrowserController,
        onTabOverviewPortraitLockChanged: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(
                    controller = browserController,
                    onTabOverviewPortraitLockChanged = onTabOverviewPortraitLockChanged,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
