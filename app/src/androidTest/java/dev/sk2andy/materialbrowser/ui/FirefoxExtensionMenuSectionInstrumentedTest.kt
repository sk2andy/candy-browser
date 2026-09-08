package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionKey
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionKind
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirefoxExtensionMenuSectionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyActionsOmitSectionAndHeader() {
        composeRule.setContent {
            MaterialBrowserTheme {
                Column {
                    FirefoxExtensionMenuSection(actions = emptyList(), onAction = {})
                }
            }
        }

        composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.Actions).assertDoesNotExist()
        composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.SectionTitle).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_title))
            .assertDoesNotExist()
    }

    @Test
    fun actionSectionShowsHeaderAndRoutesClick() {
        val actionKey = GeckoExtensionActionKey(
            extensionId = "cookies@example.test",
            tabId = "tab",
            kind = GeckoExtensionActionKind.Browser,
        )
        val clicked = AtomicReference<GeckoExtensionActionKey>()
        composeRule.setContent {
            MaterialBrowserTheme {
                Column {
                    FirefoxExtensionMenuSection(
                        actions = listOf(
                            GeckoExtensionActionState(
                                key = actionKey,
                                title = "I still don't care about cookies",
                                enabled = true,
                                badgeText = "1",
                                badgeBackgroundColor = null,
                                badgeTextColor = null,
                            ),
                        ),
                        onAction = clicked::set,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.SectionTitle).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_title)).assertExists()
        composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.action(actionKey.saveableId))
            .assertExists()
            .performClick()
        composeRule.runOnIdle { assertEquals(actionKey, clicked.get()) }
    }
}
