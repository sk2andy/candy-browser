package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandySplashScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun splashUsesOneIconAndNoWordmark() {
        composeRule.setContent {
            MaterialBrowserTheme {
                CandySplashScreen()
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithTag("candy_splash").assertIsDisplayed()
        composeRule.onAllNodesWithTag("candy_splash_icon").assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.app_name)).assertCountEquals(0)
    }
}
