package dev.sk2andy.materialbrowser.ui

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkPeekOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actionTargetsUseIconsAndCurrentPreviewUrl() {
        val opens = AtomicInteger()
        val copiedUrl = AtomicReference<String>()
        val privateUrl = AtomicReference<String>()
        val sharedUrl = AtomicReference<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/articles/peek",
                    progress = 0f,
                    armed = false,
                    newTabTargetBounds = Rect(
                        left = 100f,
                        top = 100f,
                        right = 220f,
                        bottom = 220f,
                    ),
                    createPreviewWebView = ::previewWebView,
                    releasePreviewWebView = WebView::destroy,
                    onOpen = opens::incrementAndGet,
                    onCopyLink = copiedUrl::set,
                    onOpenInPrivate = privateUrl::set,
                    onShare = sharedUrl::set,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Root)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.Url, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.NewTabTargetOverlay).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_open_in_new_tab))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(LinkPeekTestTags.OpenTarget)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(composeRule.activity.getString(R.string.action_open_in_new_tab)),
                ),
            )
            .performClick()
            .performClick()
        composeRule.onNodeWithTag(LinkPeekTestTags.CopyLink)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(composeRule.activity.getString(R.string.external_link_preview_copy_link)),
                ),
            )
            .performClick()
        composeRule.onNodeWithTag(LinkPeekTestTags.OpenPrivate)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(composeRule.activity.getString(R.string.action_open_link_in_private_tab)),
                ),
            )
            .performClick()
        composeRule.onNodeWithTag(LinkPeekTestTags.Share)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(composeRule.activity.getString(R.string.action_share)),
                ),
            )
            .performClick()

        val overlayTargetBounds = composeRule
            .onNodeWithTag(LinkPeekTestTags.NewTabTargetOverlay)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(100f, overlayTargetBounds.left, 1f)
        assertEquals(100f, overlayTargetBounds.top, 1f)
        assertEquals(220f, overlayTargetBounds.right, 1f)
        assertEquals(220f, overlayTargetBounds.bottom, 1f)

        assertEquals(1, opens.get())
        assertEquals("https://example.com/preview", copiedUrl.get())
        assertEquals("https://example.com/preview", privateUrl.get())
        assertEquals("https://example.com/preview", sharedUrl.get())
    }

    @Test
    fun newTabTargetPulsesBeforeArmingAndStrengthensWithoutMovingWhenArmed() {
        val armed = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                MaterialBrowserTheme {
                    LinkPeekOverlay(
                        url = "https://example.com/pulse",
                        progress = 0f,
                        armed = armed.value,
                        newTabTargetBounds = Rect(700f, 100f, 820f, 220f),
                        createPreviewWebView = ::previewWebView,
                        releasePreviewWebView = WebView::destroy,
                        onOpen = {},
                        onDismiss = {},
                    )
                }
            }
            composeRule.mainClock.advanceTimeByFrame()
            val restingWidth = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetOverlay)
                .fetchSemanticsNode().boundsInRoot.width
            val restingRingWidth = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetPulseRing)
                .fetchSemanticsNode().boundsInRoot.width
            val restingActionBounds = listOf(
                LinkPeekTestTags.CopyLink,
                LinkPeekTestTags.OpenPrivate,
                LinkPeekTestTags.Share,
            ).associateWith { tag ->
                composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            }

            composeRule.mainClock.advanceTimeBy(220)
            val guidingWidth = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetOverlay)
                .fetchSemanticsNode().boundsInRoot.width
            val guidingRingBounds = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetPulseRing)
                .fetchSemanticsNode().boundsInRoot
            restingActionBounds.forEach { (tag, bounds) ->
                assertEquals(
                    bounds,
                    composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot,
                )
            }
            assertTrue(guidingWidth > restingWidth)
            assertTrue(guidingRingBounds.width > restingRingWidth)

            composeRule.runOnIdle { armed.value = true }
            composeRule.mainClock.advanceTimeByFrame()
            val pulsingWidth = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetOverlay)
                .fetchSemanticsNode().boundsInRoot.width
            val armedRingBounds = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetPulseRing)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(pulsingWidth > guidingWidth)
            assertTrue(armedRingBounds.width > guidingRingBounds.width)
            assertEquals(guidingRingBounds.center.x, armedRingBounds.center.x, 1f)
            assertEquals(guidingRingBounds.center.y, armedRingBounds.center.y, 1f)

            composeRule.runOnIdle { armed.value = false }
            composeRule.mainClock.advanceTimeByFrame()
            val guidingAgainRingBounds = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetPulseRing)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(guidingAgainRingBounds.width < armedRingBounds.width)
            assertTrue(guidingAgainRingBounds.width > restingRingWidth)
            assertEquals(guidingRingBounds.center.x, guidingAgainRingBounds.center.x, 1f)
            assertEquals(guidingRingBounds.center.y, guidingAgainRingBounds.center.y, 1f)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun commitFreezesRingPulseAcrossRepeatBoundary() {
        val committing = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                MaterialBrowserTheme {
                    LinkPeekOverlay(
                        url = "https://example.com/commit-ring",
                        progress = 1f,
                        armed = true,
                        committing = committing.value,
                        newTabTargetBounds = Rect(100f, 100f, 220f, 220f),
                        createPreviewWebView = ::previewWebView,
                        releasePreviewWebView = WebView::destroy,
                        onOpen = {},
                        onDismiss = {},
                    )
                }
            }
            composeRule.mainClock.advanceTimeBy(880)
            composeRule.runOnIdle { committing.value = true }
            composeRule.mainClock.advanceTimeByFrame()
            val frozenRingBounds = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetPulseRing)
                .fetchSemanticsNode().boundsInRoot

            composeRule.mainClock.advanceTimeBy(96)
            val afterRepeatBoundaryBounds = composeRule
                .onNodeWithTag(LinkPeekTestTags.NewTabTargetPulseRing)
                .fetchSemanticsNode().boundsInRoot

            assertEquals(frozenRingBounds.width, afterRepeatBoundaryBounds.width, 1f)
            assertEquals(frozenRingBounds.height, afterRepeatBoundaryBounds.height, 1f)
            assertEquals(frozenRingBounds.center.x, afterRepeatBoundaryBounds.center.x, 1f)
            assertEquals(frozenRingBounds.center.y, afterRepeatBoundaryBounds.center.y, 1f)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun newTabTargetUsesAbsoluteRootBoundsInRtl() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialBrowserTheme {
                    LinkPeekOverlay(
                        url = "https://example.com/rtl",
                        progress = 0f,
                        armed = false,
                        newTabTargetBounds = Rect(100f, 100f, 220f, 220f),
                        createPreviewWebView = ::previewWebView,
                        releasePreviewWebView = WebView::destroy,
                        onOpen = {},
                        onDismiss = {},
                    )
                }
            }
        }

        val overlayBounds = composeRule.onNodeWithTag(LinkPeekTestTags.NewTabTargetOverlay)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(100f, overlayBounds.left, 1f)
        assertEquals(100f, overlayBounds.top, 1f)
        assertEquals(220f, overlayBounds.right, 1f)
        assertEquals(220f, overlayBounds.bottom, 1f)
    }

    @Test
    fun actionsAndPlusUseBottomFallbackWhenNewTabActionIsMissing() {
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/fallback",
                    progress = 0f,
                    armed = false,
                    createPreviewWebView = ::previewWebView,
                    releasePreviewWebView = WebView::destroy,
                    onOpen = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.CopyLink).assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.OpenPrivate).assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.Share).assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.OpenTarget).assertIsDisplayed()
    }

    @Test
    fun privateActionStaysVisibleAndDisabledWhenPrivateTabsAreUnavailable() {
        val privateOpens = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/no-private",
                    progress = 0f,
                    armed = false,
                    canOpenInPrivate = false,
                    createPreviewWebView = ::previewWebView,
                    releasePreviewWebView = WebView::destroy,
                    onOpen = {},
                    onOpenInPrivate = { privateOpens.incrementAndGet() },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.OpenPrivate)
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, privateOpens.get())
    }

    @Test
    fun commitMotionOpensOnlyAfterLandingAndOnlyOnce() {
        val committing = mutableStateOf(false)
        val commitRequests = AtomicInteger()
        val opens = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                MaterialBrowserTheme {
                    LinkPeekOverlay(
                        url = "https://example.com/animated-commit",
                        progress = 1f,
                        armed = true,
                        committing = committing.value,
                        newTabTargetBounds = androidx.compose.ui.geometry.Rect(
                            left = 900f,
                            top = 1_900f,
                            right = 980f,
                            bottom = 1_980f,
                        ),
                        createPreviewWebView = ::previewWebView,
                        releasePreviewWebView = WebView::destroy,
                        onCommitRequested = {
                            commitRequests.incrementAndGet()
                            committing.value = true
                        },
                        onOpen = opens::incrementAndGet,
                        onDismiss = dismissals::incrementAndGet,
                    )
                }
            }
            composeRule.mainClock.advanceTimeByFrame()

            composeRule.onNodeWithTag(LinkPeekTestTags.OpenTarget).performClick().performClick()
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            composeRule.mainClock.advanceTimeBy(300)
            composeRule.runOnIdle {
                assertEquals(1, commitRequests.get())
                assertEquals(0, opens.get())
                assertEquals(0, dismissals.get())
            }

            composeRule.mainClock.advanceTimeBy(100)
            composeRule.runOnIdle { assertEquals(1, opens.get()) }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(1, opens.get()) }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun backDismissesWithoutOpening() {
        val visible = mutableStateOf(true)
        val opens = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                if (visible.value) {
                    LinkPeekOverlay(
                        url = "https://example.com/cancel",
                        progress = 0.4f,
                        armed = false,
                        createPreviewWebView = ::previewWebView,
                        releasePreviewWebView = WebView::destroy,
                        onOpen = opens::incrementAndGet,
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertFalse(visible.value)
        assertEquals(0, opens.get())
    }

    @Test
    fun previewWebViewIsReleasedWhenPeekLeavesComposition() {
        val visible = mutableStateOf(true)
        val releases = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                if (visible.value) {
                    LinkPeekOverlay(
                        url = "https://example.com/lifecycle",
                        progress = 0f,
                        armed = false,
                        createPreviewWebView = ::previewWebView,
                        releasePreviewWebView = { webView ->
                            releases.incrementAndGet()
                            webView.destroy()
                        },
                        onOpen = {},
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()

        assertEquals(1, releases.get())
    }

    @Test
    fun committedRedirectUpdatesDisplayedOrigin() {
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://redirect.example/start",
                    progress = 0f,
                    armed = false,
                    createPreviewWebView = { onProgressChanged, onCommittedUrlChanged ->
                        previewWebView(onProgressChanged, onCommittedUrlChanged).also {
                            onCommittedUrlChanged("http://destination.example/article")
                        }
                    },
                    releasePreviewWebView = WebView::destroy,
                    onOpen = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Url, useUnmergedTree = true)
            .assertTextEquals("http://destination.example/article")
        composeRule.onNodeWithText("destination.example").assertIsDisplayed()
        composeRule.onNodeWithText("HTTP").assertIsDisplayed()
    }

    @Test
    fun committedInternationalizedOriginShowsReadableHost() {
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://start.example",
                    progress = 0f,
                    armed = false,
                    createPreviewWebView = { onProgressChanged, onCommittedUrlChanged ->
                        previewWebView(onProgressChanged, onCommittedUrlChanged).also {
                            onCommittedUrlChanged("https://bücher.example/article")
                        }
                    },
                    releasePreviewWebView = WebView::destroy,
                    onOpen = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("bücher.example").assertIsDisplayed()
    }

    private fun previewWebView(
        onProgressChanged: (Int) -> Unit,
        onCommittedUrlChanged: (String) -> Unit,
    ): WebView =
        WebView(composeRule.activity).apply {
            loadData("<html><body>Preview</body></html>", "text/html", "UTF-8")
            onProgressChanged(100)
            onCommittedUrlChanged("https://example.com/preview")
        }
}
