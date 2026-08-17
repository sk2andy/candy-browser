package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserscriptManagementScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedScriptCanBeToggledImportedAndDeleted() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        val deletes = mutableListOf<String>()
        val saves = mutableListOf<Pair<String?, String>>()
        var importRequested = false
        var discoverRequested = false
        composeRule.setContent {
            MaterialBrowserTheme {
                UserscriptManagementScreen(
                    scripts = listOf(testScript()),
                    onToggle = { id, enabled, onResult ->
                        toggles += id to enabled
                        onResult(null)
                    },
                    onSave = { id, source, onResult ->
                        saves += id to source
                        onResult(null)
                    },
                    onDelete = { id, onResult ->
                        deletes += id
                        onResult(null)
                    },
                    onImport = { importRequested = true },
                    onDiscover = { discoverRequested = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(UserscriptManagementTestTags.Screen).assertExists()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.edit("reader-theme"))
            .performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.EditorSource)
            .assertTextContains("// source")
        composeRule.onNodeWithTag(UserscriptManagementTestTags.EditorSave).performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.Editor).assertDoesNotExist()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.toggle("reader-theme"))
            .assertIsOn()
            .performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.Import).performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.Discover).performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.delete("reader-theme"))
            .performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.DeleteConfirmation).assertExists()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.DeleteConfirm).performClick()

        assertEquals(listOf("reader-theme" to "// source"), saves)
        assertEquals(listOf("reader-theme" to false), toggles)
        assertTrue(importRequested)
        assertTrue(discoverRequested)
        assertEquals(listOf("reader-theme"), deletes)
    }

    @Test
    fun editorStartsFromTemplateAndDisplaysValidationFailure() {
        composeRule.setContent {
            MaterialBrowserTheme {
                UserscriptManagementScreen(
                    scripts = emptyList(),
                    onToggle = { _, _, onResult -> onResult(null) },
                    onSave = { _, _, onResult -> onResult("Missing @match") },
                    onDelete = { _, onResult -> onResult(null) },
                    onImport = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(UserscriptManagementTestTags.Add).performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.Editor).assertExists()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.EditorSource)
            .assertTextContains("@match", substring = true)
            .performTextClearance()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.EditorSource)
            .performTextInput("invalid source")
        composeRule.onNodeWithTag(UserscriptManagementTestTags.EditorSave).performClick()
        composeRule.onNodeWithText("Missing @match").assertExists()
    }

    @Test
    fun mutationFailuresStayVisibleAndKeepDeleteConfirmationOpen() {
        composeRule.setContent {
            MaterialBrowserTheme {
                UserscriptManagementScreen(
                    scripts = listOf(testScript()),
                    onToggle = { _, _, onResult -> onResult("Toggle failed") },
                    onSave = { _, _, onResult -> onResult(null) },
                    onDelete = { _, onResult -> onResult("Delete failed") },
                    onImport = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(UserscriptManagementTestTags.toggle("reader-theme"))
            .performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.ActionError).assertExists()
        composeRule.onNodeWithText("Toggle failed").assertExists()

        composeRule.onNodeWithTag(UserscriptManagementTestTags.delete("reader-theme"))
            .performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.DeleteConfirm).performClick()
        composeRule.onNodeWithTag(UserscriptManagementTestTags.DeleteConfirmation).assertExists()
        composeRule.onNodeWithText("Delete failed").assertExists()
    }

    private fun testScript() = UserscriptUiItem(
        id = "reader-theme",
        name = "Reader theme",
        source = "// source",
        enabled = true,
        runAtLabel = "document-end",
        urlPatterns = listOf("https://example.com/*"),
    )
}
