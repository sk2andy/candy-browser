package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotation
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebAnnotationDialogsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val annotation = WebAnnotation(
        id = "quote", profileId = "work", url = "https://example.com/article", title = "Article",
        quote = "A memorable passage", note = "**Remember** this [source](https://example.com/source)", createdAt = 100,
    )

    @Test
    fun listSeparatesScrollReadAndEditAndFormatsNotePreview() {
        var scrolled: WebAnnotation? = null
        var read: WebAnnotation? = null
        var edited: WebAnnotation? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationLibrarySheet(
                    annotations = listOf(
                        annotation,
                        annotation.copy(id = "second", quote = "A second passage worth keeping", note = "A short follow-up note."),
                        annotation.copy(id = "third", quote = "Highlights stay easy to find", note = ""),
                    ),
                    onScrollTo = { scrolled = it },
                    onRead = { read = it },
                    onEdit = { edited = it },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Remember this source", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("web_annotation_actions_quote")
            .assertContentDescriptionEquals("More options: ${annotation.quote}")
        captureEvidence("annotation-list.png")
        composeRule.onNodeWithTag("web_annotation_actions_quote").performClick()
        composeRule.onNodeWithText("Read note").performClick()
        assertEquals(annotation, read)
        assertNull(scrolled)
        composeRule.onNodeWithTag("web_annotation_actions_quote").performClick()
        composeRule.onNodeWithText("Edit").performClick()
        assertEquals(annotation, edited)
        assertNull(scrolled)
        composeRule.onNodeWithTag("web_annotation_item_quote").performClick()
        assertEquals(annotation, scrolled)
    }

    @Test
    fun longListCanScrollToLastQuoteAndClose() {
        val annotations = (1..30).map { annotation.copy(id = "quote-$it", quote = "Saved passage $it") }
        var scrolled: WebAnnotation? = null
        var dismissed = false
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationLibrarySheet(
                    annotations = annotations,
                    onScrollTo = { scrolled = it },
                    onRead = {},
                    onEdit = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeRule.onNodeWithTag("web_annotation_list").performScrollToIndex(29)
        composeRule.onNodeWithTag("web_annotation_item_quote-30").performClick()
        assertEquals(annotations.last(), scrolled)
        composeRule.onNodeWithContentDescription("Close").performClick()
        assertEquals(true, dismissed)
    }

    @Test
    fun missingQuoteStillShowsReadableNoteAndEditAction() {
        var edited = false
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationReaderSheet(
                    annotation = annotation,
                    scrollFailed = true,
                    onOpenLink = {},
                    onEdit = { edited = true },
                    onScrollTo = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("A memorable passage").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This quote could not be located uniquely on the page. Your saved note is still available.",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Remember this source").performScrollTo().assertIsDisplayed()
        captureEvidence("annotation-note.png")
        composeRule.onNodeWithText("Edit").performClick()
        assertEquals(true, edited)
    }

    private fun captureEvidence(name: String) {
        if (InstrumentationRegistry.getArguments().getString("annotationEvidence") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "annotation-evidence")
        directory.mkdirs()
        composeRule.waitForIdle()
        UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, name))
    }
}
