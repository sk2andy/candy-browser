package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressAiModeToggleInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun toggleSwitchesBetweenRegularSearchAndAiMode() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(false) }
            MaterialBrowserTheme {
                AddressAiModeToggle(
                    selected = selected,
                    onSelectedChange = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag(AddressBarTestTags.AiModeToggle)
            .assertIsOff()
            .assertContentDescriptionEquals(
                context.getString(R.string.cd_enable_google_ai_mode),
            )
            .performClick()
            .assertIsOn()
            .assertContentDescriptionEquals(
                context.getString(R.string.cd_disable_google_ai_mode),
            )
    }
}
