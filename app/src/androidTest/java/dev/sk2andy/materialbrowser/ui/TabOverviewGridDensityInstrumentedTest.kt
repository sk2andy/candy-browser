package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewChromeTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabOverviewGridDensityInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun overviewSettingsSliderPersistsAndResizesActualCards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BrowserSessionStore(context)
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView))
        store.saveStartupAnimationEnabled(false)
        store.saveStartupAddressFocusMode(StartupAddressFocusMode.Never)
        store.saveTabOverviewMode(TabOverviewMode.Grid)
        store.saveTabOverviewGridColumns(0)
        store.saveTabListStartsAtBottom(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var firstTabId = ""
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                repeat(6) { index ->
                    controller.createBackgroundTab("https://fixture-$index.example", showContentActionFeedback = false)
                }
                firstTabId = controller.activeTabs.first().id
            }
            compose.onNodeWithTag(AddressBarTestTags.TabButton).performClick()
            val grid = compose.onNodeWithTag(TabOverviewChromeTestTags.Grid)
            grid.assertIsDisplayed().performScrollToIndex(0)
            val card = compose.onNodeWithTag(SnoozeTestTags.overviewTab(firstTabId))
            val initialWidth = card.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.width

            compose.onNodeWithTag(TabOverviewChromeTestTags.Settings).assertIsDisplayed().performClick()
            for (route in listOf(R.string.settings_home_tabs_title, R.string.settings_tab_overview_mode)) {
                val entry = compose.onNode(hasText(context.getString(route)) and hasClickAction())
                compose.waitUntil(10_000) { runCatching { entry.fetchSemanticsNode() }.isSuccess }
                entry.performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.waitForIdle()
            }

            val slider = compose.onNodeWithTag(TAB_GRID_DENSITY_SLIDER)
            compose.waitUntil(10_000) { runCatching { slider.fetchSemanticsNode() }.isSuccess }
            slider.performScrollTo().assertIsDisplayed()
            slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(1f)) }
            compose.waitForIdle()
            assertEquals(1, BrowserSessionStore(context).loadTabOverviewGridColumns())
            repeat(3) {
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
            grid.assertIsDisplayed().performScrollToIndex(0)
            val resizedWidth = card.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.width
            assertTrue("One column must widen the original card", resizedWidth > initialWidth * 1.5f)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(1, it.browserControllerForTesting().tabOverviewGridColumns)
            }
        }
    }
}
