package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupAddressEditorInstrumentedTest {
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
    fun startupRequestOpensAndFocusesAddressEditor() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(
                    controller = browserController,
                    openAddressEditorOnLaunch = true,
                )
            }
        }

        composeRule.onNodeWithTag(AddressBarTestTags.Editor)
            .assertIsDisplayed()
            .assertIsFocused()
    }

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
