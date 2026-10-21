package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomWorkflowSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun templateAssignmentsSaveTogetherAfterStateRestoration() {
        var configuration by mutableStateOf(CustomWorkflowConfiguration())
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialBrowserTheme {
                CustomWorkflowSettingsPage(
                    configuration = configuration,
                    onConfigurationChanged = { value, result -> configuration = value; result(true) },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(CustomWorkflowTestTags.template(0)).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Name).performTextReplacement("Read later")
        CustomWorkflowPlacement.entries.forEach { placement ->
            composeRule.onNodeWithTag(CustomWorkflowTestTags.placement(placement)).performScrollTo().performClick()
            composeRule.onNodeWithTag(CustomWorkflowTestTags.slot(placement, 0)).performScrollTo().performClick()
            composeRule.onNodeWithText(context.getString(R.string.action_done)).performClick()
        }
        composeRule.runOnIdle { assertTrue(configuration.workflows.isEmpty()) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Save).performScrollTo().performClick()
        composeRule.runOnIdle {
            val workflow = configuration.workflows.single()
            assertEquals("Read later", workflow.name)
            assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab), workflow.steps)
            assertEquals(CustomWorkflowPlacement.entries.toSet(), configuration.bindings.map { it.placement }.toSet())
            assertTrue(configuration.bindings.all { it.workflowId == workflow.id && it.slot == 0 })
        }
    }

    @Test
    fun deletingKeepsAssignedHoleUntilAssignmentIsCleared() {
        val workflow = CustomWorkflow("later", "Read later", steps = listOf(CustomWorkflowStep.CopyUrl))
        val binding = CustomWorkflowBinding(CustomWorkflowPlacement.MenuFooter, 0, workflow.id)
        var configuration by mutableStateOf(CustomWorkflowConfiguration(listOf(workflow), listOf(binding)))
        composeRule.setContent {
            MaterialBrowserTheme {
                CustomWorkflowSettingsPage(
                    configuration = configuration,
                    onConfigurationChanged = { value, result -> configuration = value; result(true) },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(CustomWorkflowTestTags.workflow(workflow.id)).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Delete).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, configuration.workflows.size) }
        composeRule.onNodeWithTag(CustomWorkflowTestTags.ConfirmDelete).performClick()
        composeRule.runOnIdle {
            assertTrue(configuration.workflows.isEmpty())
            assertEquals(listOf(binding), configuration.bindings)
        }
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Assignments).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.slot(CustomWorkflowPlacement.MenuFooter, 0)).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.ClearAssignment).performClick()
        composeRule.runOnIdle { assertTrue(configuration.bindings.isEmpty()) }
    }

    @Test
    fun invalidReorderBlocksSaveAndValidReorderCanBeSaved() {
        val workflow = CustomWorkflow("later", "Read later", steps = listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab))
        var configuration by mutableStateOf(CustomWorkflowConfiguration(listOf(workflow)))
        composeRule.setContent {
            MaterialBrowserTheme {
                CustomWorkflowSettingsPage(
                    configuration = configuration,
                    onConfigurationChanged = { value, result -> configuration = value; result(true) },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(CustomWorkflowTestTags.workflow(workflow.id)).performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.stepMenu(0)).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.moveDown(0)).performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.ValidationError).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Save).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.stepMenu(1)).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.moveUp(1)).performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Save).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(workflow.steps, configuration.workflows.single().steps) }
    }

    @Test
    fun failedSaveKeepsDraftAndDoesNotPublishChanges() {
        var attempts = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                CustomWorkflowSettingsPage(
                    configuration = CustomWorkflowConfiguration(),
                    onConfigurationChanged = { _, result -> attempts++; result(false) },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(CustomWorkflowTestTags.template(0)).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Save).performScrollTo().performClick()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.SaveError).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CustomWorkflowTestTags.Name).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, attempts) }
    }
}
