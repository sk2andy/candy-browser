package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClosedTabUndoSnackbarInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            composeRule.activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun undoActionRestoresClosedTabAndDismissesSnackbar() {
        lateinit var browser: BrowserController
        val hostState = SnackbarHostState()
        composeRule.runOnIdle {
            composeRule.activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE,
            ).edit().clear().commit()
            browser = BrowserController(composeRule.activity).also { controller = it }
            browser.updateClosedTabUndoEnabled(true)
        }
        val tabId = browser.selectedTabId
        composeRule.setContent {
            MaterialBrowserTheme {
                ClosedTabUndoSnackbarEffect(browser, hostState)
                SnackbarHost(hostState)
            }
        }
        composeRule.runOnIdle { browser.closeTabFromUser(tabId) }

        val message = composeRule.activity.getString(R.string.tab_closed)
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_undo))
            .performClick()
        composeRule.runOnIdle { assertEquals(tabId, browser.selectedTabId) }
        composeRule.onNodeWithText(message).assertDoesNotExist()
    }
}
