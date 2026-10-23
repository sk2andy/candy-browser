package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotation
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WebAnnotationNoteEditorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val annotation = WebAnnotation(
        id = "editor", profileId = "work", url = "https://example.com/article", title = "Article",
        quote = "Good notes connect an idea to its source.", note = "", createdAt = 100,
    )

    @Test
    fun selectedTextFormatsImmediatelyAndSavesMarkdown() {
        var saved: String? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationNoteEditor(annotation, false, false, { saved = it }, {}, {})
            }
        }
        val editor = composeRule.onNodeWithTag("web_annotation_editor")
        editor.performTextReplacement("Remember this source")
        editor.performTextInputSelection(TextRange(0, 8))
        composeRule.onNodeWithContentDescription("Bold").performScrollTo().performClick()
        editor.assertTextContains("**Remember** this source")
        composeRule.onNodeWithTag("web_annotation_preview_tab").performClick().assertIsSelected()
        editor.assertDoesNotExist()
        composeRule.onNodeWithText("Remember this source").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("**Remember** this source", saved)
    }

    @Test
    fun draftSurvivesRecreationAndDismissRequiresExplicitDiscard() {
        var dismissed = false
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialBrowserTheme {
                WebAnnotationNoteEditor(annotation, false, false, {}, {}, { dismissed = true })
            }
        }
        composeRule.onNodeWithTag("web_annotation_editor").performTextReplacement("Unsaved draft")
        composeRule.onNodeWithTag("web_annotation_preview_tab").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("web_annotation_preview_tab").assertIsSelected()
        composeRule.onNodeWithText("Unsaved draft").assertIsDisplayed()
        composeRule.onNodeWithTag("web_annotation_edit_tab").performClick().assertIsSelected()
        composeRule.onNodeWithTag("web_annotation_editor").assertTextContains("Unsaved draft")
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()
        assertFalse(dismissed)
        composeRule.onNodeWithTag("web_annotation_editor").assertTextContains("Unsaved draft")
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithText("Discard", substring = false).performClick()
        assertEquals(true, dismissed)
    }

    @Test
    fun characterLimitRejectsTypingAndFormattingOverflow() {
        val full = "x".repeat(4_000)
        var saved: String? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationNoteEditor(annotation.copy(note = full), false, false, { saved = it }, {}, {})
            }
        }
        val editor = composeRule.onNodeWithTag("web_annotation_editor")
        editor.performTextReplacement(full + "y")
        editor.assertTextContains(full)
        composeRule.onNodeWithContentDescription("Bold").performScrollTo().performClick()
        editor.assertTextContains(full)
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(full, saved)
    }

    @Test
    fun savingDisablesFormattingAndRepeatedSubmission() {
        var saving by androidx.compose.runtime.mutableStateOf(false)
        var saveCount = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationNoteEditor(
                    annotation, false, true,
                    onSave = { saveCount++; saving = true },
                    onDelete = {},
                    onDismiss = {},
                    saving = saving,
                )
            }
        }
        composeRule.onNodeWithTag("web_annotation_editor").performTextReplacement("My draft")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Bold").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Delete").assertIsNotEnabled()
        assertEquals(1, saveCount)
        composeRule.runOnIdle { saving = false }
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(2, saveCount)
    }

    @Test
    fun longNoteScrollsInsideEditorWhileTopActionsStayVisible() {
        val longNote = (1..100).joinToString("\n") { "Line $it" }
        var deleted = false
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationNoteEditor(annotation.copy(note = longNote), false, true, {}, { deleted = true }, {})
            }
        }
        val editor = composeRule.onNodeWithTag("web_annotation_editor")
        editor.performTextInputSelection(TextRange(longNote.length))
        composeRule.waitForIdle()
        captureEvidence("annotation-editor-keyboard.png")
        editor.performTouchInput { click(center) }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        composeRule.waitForIdle()
        val bounds = editor.fetchSemanticsNode().boundsInRoot
        val selectionBefore = editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].start
        editor.performTouchInput { swipeDown() }
        editor.performTouchInput { click(center) }
        val selectionAfter = editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].start
        assertTrue("Scrolling reveals earlier text at the same position", selectionAfter < selectionBefore)
        assertEquals(bounds, editor.fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithText("Save").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete").assertIsDisplayed().performClick()
        assertTrue(deleted)
    }

    @Test
    fun editorAndReaderShowRichMarkdown() {
        val rich = annotation.copy(note = "## Key idea\n\n**Keep the context**, not just the quote.\n\n- Read the original\n- Compare your notes\n\n[Open the source](https://example.com/source)")
        var editing by androidx.compose.runtime.mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                if (editing) {
                    WebAnnotationNoteEditor(rich, false, true, {}, {}, { editing = false })
                } else {
                    WebAnnotationReaderSheet(rich, false, {}, {}, {}, {})
                }
            }
        }
        captureEvidence("annotation-editor-tabs.png")
        composeRule.onNodeWithTag("web_annotation_preview_tab").performClick()
        composeRule.onNodeWithText("Key idea").assertIsDisplayed()
        captureEvidence("annotation-preview-tab.png")
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithText("Key idea").assertIsDisplayed()
        composeRule.onNodeWithText("Open the source").assertIsDisplayed()
        captureEvidence("annotation-reader-m3.png")
    }

    private fun captureEvidence(name: String) {
        if (InstrumentationRegistry.getArguments().getString("annotationEvidence") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "annotation-evidence")
        directory.mkdirs()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        instrumentation.waitForIdleSync()
        val device = UiDevice.getInstance(instrumentation)
        device.waitForIdle()
        device.takeScreenshot(File(directory, name))
    }
}
