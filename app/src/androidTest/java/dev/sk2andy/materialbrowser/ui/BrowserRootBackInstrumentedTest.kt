package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserRootBackInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null
    private val incomingBrowserNavigationRequestId = mutableIntStateOf(0)

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            incomingBrowserNavigationRequestId.intValue = 0
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

    @Test
    fun settingsFromOverviewReturnsToOverviewOnBack() {
        val browserController = createController()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setBrowserContent(browserController)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        val settingsBounds = composeRule
            .onNodeWithTag(TabOverviewChromeTestTags.Settings)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        composeRule.onRoot().performTouchInput { click(settingsBounds.center) }
        composeRule.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_tabs_gestures_title))
            .performTouchInput { click(center) }
        composeRule.onNodeWithTag(TabSettingsTestTags.ResidentTabLimit).assertExists()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.settings_title)).assertDoesNotExist()
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root).assertExists()
    }

    @Test
    fun incomingBrowserNavigationOpensNewTabAndClosesOverview() {
        val browserController = createController()
        setBrowserContent(browserController)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root).assertExists()

        val existingTab = browserController.selectedTab
        val existingTabCount = browserController.tabs.size
        composeRule.runOnIdle {
            browserController.openUrl(TARGET_URL, inNewTab = true)
            incomingBrowserNavigationRequestId.intValue++
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.tabs.size == existingTabCount + 1 &&
                browserController.selectedTab.url == TARGET_URL
        }
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                existingTab.url,
                browserController.tabs.single { it.id == existingTab.id }.url,
            )
            assertNotEquals(existingTab.id, browserController.selectedTabId)
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
                    incomingBrowserNavigationRequestId =
                        incomingBrowserNavigationRequestId.intValue,
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

    private companion object {
        const val TARGET_URL = "https://incoming.example.test/path"
    }
}
