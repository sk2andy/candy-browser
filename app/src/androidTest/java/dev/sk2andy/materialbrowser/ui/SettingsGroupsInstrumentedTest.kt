package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsGroupsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun connectedRowsExposeSeparateLabeledSwitchesAndDisabledRowRetainsValue() {
        var writes = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                SettingsPage(title = "Privacy", onBack = {}) {
                    SettingsPreferenceGroup {
                        SettingsSwitch(
                            title = "Save history",
                            subtitle = "Save visits on this device",
                            checked = true,
                            onCheckedChange = { writes++ },
                        )
                        SettingsPreferenceDivider()
                        SettingsSwitch(
                            title = "Clear when Candy closes",
                            subtitle = "Retain this choice while history is disabled",
                            checked = true,
                            enabled = false,
                            onCheckedChange = { writes++ },
                        )
                    }
                }
            }
        }
        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        composeRule.onAllNodes(switchRole).assertCountEquals(2)
        composeRule.onNode(switchRole and hasText("Save history")).assertIsOn().performClick()
        assertEquals(1, writes)
        composeRule.onNode(switchRole and hasText("Clear when Candy closes"))
            .assertIsOn()
            .assertIsNotEnabled()
            .performClick()
        assertEquals(1, writes)
    }

    @Test
    fun compactPageWrapsHeadingAndPreferenceAtDoubleFontScale() {
        val heading = "Theme and interface preferences"
        val label = "Keep related browser controls together"
        composeRule.setContent {
            MaterialBrowserTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp)) {
                        SettingsPage(title = heading, onBack = {}) {
                            SettingsPreferenceGroup {
                                SettingsSwitch(
                                    title = label,
                                    subtitle = "A longer explanation remains readable at larger text sizes.",
                                    checked = false,
                                    onCheckedChange = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        listOf(heading, label).forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text, useUnmergedTree = true)
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            assertFalse(layouts.single().hasVisualOverflow)
        }
    }
}
