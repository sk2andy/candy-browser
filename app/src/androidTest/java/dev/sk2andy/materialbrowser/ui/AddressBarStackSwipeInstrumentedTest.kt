package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.TabStackColor
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarStackSwipeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            clearSession()
        }
    }

    @Test
    fun horizontalAddressBarSwipeStaysInCurrentStackAndStopsAtBothEnds() {
        val browser = showBrowser(selectedId = "source")
        val bar = composeRule.onNodeWithTag(AddressBarTestTags.PrimaryField)
        bar.performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals("linked", browser.selectedTabId) }
        bar.performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals("linked", browser.selectedTabId) }
        bar.performTouchInput { swipeRight() }
        composeRule.runOnIdle { assertEquals("source", browser.selectedTabId) }
        bar.performTouchInput { swipeRight() }
        composeRule.runOnIdle { assertEquals("source", browser.selectedTabId) }
    }

    @Test
    fun horizontalAddressBarSwipeFromUngroupedTabKeepsOrdinaryNavigation() {
        val browser = showBrowser(selectedId = "between")
        composeRule.onNodeWithTag(AddressBarTestTags.PrimaryField)
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals("linked", browser.selectedTabId) }
    }

    private fun showBrowser(selectedId: String): BrowserController {
        lateinit var browser: BrowserController
        composeRule.runOnIdle {
            clearSession()
            val tabs = listOf("before", "source", "between", "linked", "after")
                .mapIndexed { index, id ->
                    BrowserTab(
                        id = id,
                        lastAccessedAt = index.toLong() + 1L,
                        title = id,
                        url = "data:text/html,$id",
                    )
                }
            BrowserSessionStore(composeRule.activity).saveTabsImmediately(tabs, selectedId)
            browser = BrowserController(composeRule.activity)
            controller = browser
            assertNotNull(browser.createTabStack(listOf("source", "linked"), "Test", TabStackColor.Grape))
        }
        composeRule.setContent {
            MaterialBrowserTheme { BrowserScreen(browser) }
        }
        return browser
    }

    private fun clearSession() {
        composeRule.activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
