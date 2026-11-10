package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.userscript.GreasyForkInstallError
import dev.sk2andy.materialbrowser.browser.userscript.GreasyForkInstallStage
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptGrant
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRunAt
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GreasyForkInstallDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reviewShowsAccessAndSourceBeforeExplicitInstall() {
        var installations = 0
        var dismissals = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialBrowserTheme {
                GreasyForkInstallDialog(
                    stage = GreasyForkInstallStage.Review(script()),
                    onInstall = { installations++ },
                    onDismiss = { dismissals++ },
                )
            }
        }

        val experimentalLabel = context.getString(R.string.settings_inline_media_player_title)
            .removePrefix("Candy Player").trim()
        composeRule.onNodeWithText(context.getString(R.string.greasy_fork_install_title))
            .assertTextContains(experimentalLabel, substring = true)
        composeRule.onNodeWithText("Example script").assertExists()
        composeRule.onNodeWithText("https://example.com/*", substring = true).assertExists()
        composeRule.onNodeWithText("GM_addStyle", substring = true).assertExists()
        composeRule.onNodeWithTag(GreasyForkInstallTestTags.ShowSource).performScrollTo().performClick()
        composeRule.onNodeWithTag(GreasyForkInstallTestTags.Source)
            .assertTextContains("console.log('example')")
        composeRule.onNodeWithText(context.getString(R.string.action_done)).performClick()
        composeRule.runOnIdle { assertEquals(0, installations) }
        composeRule.onNodeWithTag(GreasyForkInstallTestTags.Install).performClick()
        composeRule.runOnIdle {
            assertEquals(1, installations)
            assertEquals(0, dismissals)
        }
    }

    @Test
    fun savingDisablesActionsAndFailureExplainsReason() {
        var stage by mutableStateOf<GreasyForkInstallStage>(
            GreasyForkInstallStage.Review(script(), isSaving = true),
        )
        var dismissals = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialBrowserTheme {
                GreasyForkInstallDialog(
                    stage = stage,
                    onInstall = {},
                    onDismiss = { dismissals++ },
                )
            }
        }

        composeRule.onNodeWithTag(GreasyForkInstallTestTags.Install).assertIsNotEnabled()
        composeRule.onNodeWithTag(GreasyForkInstallTestTags.Dismiss).assertIsNotEnabled()
        composeRule.runOnIdle { stage = GreasyForkInstallStage.Failed(GreasyForkInstallError.UnsupportedGrant) }
        composeRule.onNodeWithText(
            context.getString(R.string.userscript_import_error_unsupported_grant),
        ).assertExists()
        composeRule.onNodeWithTag(GreasyForkInstallTestTags.Dismiss).performClick()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    private fun script() = UserScript(
        id = "example",
        name = "Example script",
        source = "console.log('example')",
        matchPatterns = listOf("https://example.com/*"),
        includePatterns = emptyList(),
        excludePatterns = listOf("https://example.com/private/*"),
        grants = setOf(UserScriptGrant.AddStyle),
        runAt = UserScriptRunAt.DocumentEnd,
    )
}
