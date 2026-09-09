package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeveloperOptionsSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aboutLongPressUnlocksDeveloperDestinationWithoutOpeningAbout() {
        var unlocked by mutableStateOf(false)
        var destination by mutableStateOf<SettingsDestination?>(null)
        composeRule.setContent {
            MaterialBrowserTheme {
                SettingsHomePage(
                    downloadSummary = "Candy",
                    onDestinationChanged = { destination = it },
                    onDismiss = {},
                    developerOptionsUnlocked = unlocked,
                    onUnlockDeveloperOptions = { unlocked = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.developer_options_title))
            .assertDoesNotExist()
        val aboutNode = composeRule
            .onNodeWithText(context.getString(R.string.settings_section_about_legal))
            .performScrollTo()
        val unlockLabel = context.getString(R.string.developer_options_unlock_action)
        aboutNode.assert(
            SemanticsMatcher("has labeled developer-options long click") { node ->
                runCatching { node.config[SemanticsActions.OnLongClick].label }.getOrNull() ==
                    unlockLabel
            },
        )
        aboutNode
            .performTouchInput { longClick() }

        assertTrue(unlocked)
        assertEquals(null, destination)
        composeRule.onNodeWithText(context.getString(R.string.developer_options_title))
            .assertIsDisplayed()
            .performClick()
        assertEquals(SettingsDestination.DeveloperOptions, destination)
    }

    @Test
    fun safeAreaControlsUpdateAndResetSettings() {
        var settings by mutableStateOf(DeveloperSettings())
        composeRule.setContent {
            MaterialBrowserTheme {
                DeveloperOptionsSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DeveloperOptionsTestTags.LayoutQuietPeriod)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(250f)
            }
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.RequiredFailures)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(4f)
            }

        assertEquals(250, settings.safeAreaLayoutQuietPeriodMillis)
        assertEquals(4, settings.safeAreaRequiredFailureCount)
        assertFalse(settings == DeveloperSettings())
        composeRule.onNodeWithTag(DeveloperOptionsTestTags.Reset)
            .performScrollTo()
            .performClick()
        assertEquals(DeveloperSettings(), settings)
    }
}
