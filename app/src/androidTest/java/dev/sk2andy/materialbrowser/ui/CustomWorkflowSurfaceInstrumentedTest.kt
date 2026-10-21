package dev.sk2andy.materialbrowser.ui

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import dev.sk2andy.materialbrowser.shared.ui.CustomWorkflowIcons
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.shared.ui.BrowserCustomAction
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomWorkflowSurfaceInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun customPeekSlotReceivesCommittedUrlAndPreviewView() {
        val invokedId = AtomicReference<String>()
        val invokedUrl = AtomicReference<String>()
        val invokedView = AtomicReference<WebView>()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/redirect",
                    progress = 0f,
                    armed = false,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onDismiss = {},
                    customActionSlots = mapOf(0 to BrowserCustomAction("save", "Save", CustomWorkflowIcons.Download)),
                    onCustomAction = { id, url, view ->
                        invokedId.set(id)
                        invokedUrl.set(url)
                        invokedView.set(view)
                    },
                )
            }
        }
        composeRule.onNodeWithTag(LinkPeekTestTags.CopyLink).assertDoesNotExist()
        composeRule.onNodeWithTag(LinkPeekTestTags.OpenTarget).assertIsDisplayed()
        composeRule.onNodeWithTag("custom_workflow_peek:0").performClick()
        assertEquals("save", invokedId.get())
        assertEquals("https://example.com/article", invokedUrl.get())
        assertNotNull(invokedView.get())
    }

    @Test
    fun deletedPeekBindingLeavesGapAndSwipeDoesNotFallBackToOpen() {
        val openCount = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/article",
                    progress = 0f,
                    armed = false,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = { openCount.incrementAndGet() },
                    onCommitRequested = { openCount.incrementAndGet() },
                    onDismiss = {},
                    customActionSlots = mapOf(0 to null),
                    hasCustomSwipeAction = true,
                    customSwipeAction = null,
                )
            }
        }
        composeRule.onNodeWithTag(LinkPeekTestTags.CopyLink).assertDoesNotExist()
        composeRule.onNodeWithTag("custom_workflow_peek:0").assertDoesNotExist()
        composeRule.onNodeWithTag(LinkPeekTestTags.OpenTarget).assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        assertEquals(0, openCount.get())
    }

    @Test
    fun customPeekSwipeInvokesWorkflowOnceWithCommittedUrl() {
        val invocationCount = AtomicInteger()
        val invokedUrl = AtomicReference<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/redirect",
                    progress = 0f,
                    armed = false,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onDismiss = {},
                    hasCustomSwipeAction = true,
                    customSwipeAction = BrowserCustomAction("save", "Save", CustomWorkflowIcons.Download),
                    onCustomAction = { _, url, _ ->
                        invocationCount.incrementAndGet()
                        invokedUrl.set(url)
                    },
                )
            }
        }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        assertEquals(1, invocationCount.get())
        assertEquals("https://example.com/article", invokedUrl.get())
    }

    private fun previewWebView(onProgress: (Int) -> Unit, onUrl: (String) -> Unit): WebView =
        WebView(composeRule.activity).also { view ->
            view.post {
                onUrl("https://example.com/article")
                onProgress(100)
            }
        }
}
