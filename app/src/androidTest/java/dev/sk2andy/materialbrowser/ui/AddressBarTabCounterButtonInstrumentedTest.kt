package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarTabCounterButtonInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun counterExposesCountAndOpensTabOverview() {
        val clicks = mutableIntStateOf(0)
        composeRule.setContent {
            MaterialBrowserTheme {
                AddressBarTabCounterButton(
                    tabCount = 1,
                    onClick = { clicks.intValue++ },
                )
            }
        }

        composeRule.onNodeWithTag(AddressBarTestTags.TabButton)
            .assertContentDescriptionEquals(
                context.resources.getQuantityString(
                    R.plurals.cd_open_tab_overview_count,
                    1,
                    1,
                ),
            )
            .performClick()

        composeRule.runOnIdle { assertEquals(1, clicks.intValue) }
    }

    @Test
    fun overflowCounterUsesInfinity() {
        composeRule.setContent {
            MaterialBrowserTheme {
                AddressBarTabCounterButton(tabCount = 100, onClick = {})
            }
        }

        composeRule.onNodeWithTag(AddressBarTestTags.TabButton)
            .assertContentDescriptionEquals(
                context.resources.getQuantityString(
                    R.plurals.cd_open_tab_overview_count,
                    100,
                    100,
                ),
            )
    }
}
