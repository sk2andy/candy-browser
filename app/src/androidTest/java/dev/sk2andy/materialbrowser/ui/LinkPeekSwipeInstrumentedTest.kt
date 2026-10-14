package dev.sk2andy.materialbrowser.ui

import android.view.MotionEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.LinkPeekSwipeAction
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkPeekSwipeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun leftSwipeAcrossPreviewDismissesWithoutOpeningOrRunningRightAction() {
        val visible = mutableStateOf(true)
        val dismissals = AtomicInteger()
        val opens = AtomicInteger()
        val copies = AtomicInteger()
        val previewDowns = AtomicInteger()
        val previewCancels = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                if (visible.value) {
                    LinkPeekOverlay(
                        url = "https://redirect.example/start",
                        progress = 0f,
                        armed = false,
                        rightSwipeAction = LinkPeekSwipeAction.Copy,
                        createPreviewView = { onProgressChanged, onCommittedUrlChanged ->
                            previewWebView(onProgressChanged, onCommittedUrlChanged).also { view ->
                                view.setOnTouchListener { _, event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> previewDowns.incrementAndGet()
                                        MotionEvent.ACTION_CANCEL -> previewCancels.incrementAndGet()
                                    }
                                    false
                                }
                            }
                        },
                        releasePreviewView = WebView::destroy,
                        onOpen = opens::incrementAndGet,
                        onCopyLink = { copies.incrementAndGet() },
                        onDismiss = {
                            dismissals.incrementAndGet()
                            visible.value = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeLeft() }

        composeRule.onNodeWithTag(LinkPeekTestTags.Root).assertDoesNotExist()
        assertEquals(1, dismissals.get())
        assertEquals(0, opens.get())
        assertEquals(0, copies.get())
        assertEquals(1, previewDowns.get())
        assertEquals(1, previewCancels.get())
    }

    @Test
    fun defaultRightSwipeRequestsBackgroundCommitOnceAndOpensCommittedUrlAfterLanding() {
        val committing = mutableStateOf(false)
        val commitRequests = AtomicInteger()
        val opens = AtomicInteger()
        val openedUrl = AtomicReference<String>()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://redirect.example/start",
                    progress = 0f,
                    armed = false,
                    committing = committing.value,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onOpenUrl = { url ->
                        opens.incrementAndGet()
                        openedUrl.set(url)
                    },
                    onCommitRequested = commitRequests::incrementAndGet,
                    onDismiss = dismissals::incrementAndGet,
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(LinkPeekTestTags.OpenTarget).performClick()
        assertEquals(1, commitRequests.get())
        assertEquals(0, opens.get())
        assertEquals(0, dismissals.get())

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { committing.value = true }
            composeRule.mainClock.advanceTimeBy(300)
            composeRule.runOnIdle { assertEquals(0, opens.get()) }
            composeRule.mainClock.advanceTimeBy(100)
            composeRule.runOnIdle {
                assertEquals(1, opens.get())
                assertEquals("https://example.com/preview", openedUrl.get())
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(1, opens.get()) }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun rightSwipeCopiesCommittedRedirectOnce() {
        assertRightSwipeUsesCommittedUrl(LinkPeekSwipeAction.Copy)
    }

    @Test
    fun rightSwipeOpensCommittedRedirectInForegroundOnce() {
        assertRightSwipeUsesCommittedUrl(LinkPeekSwipeAction.OpenForeground)
    }

    @Test
    fun rightSwipeOpensCommittedRedirectInNewStackOnce() {
        assertRightSwipeUsesCommittedUrl(LinkPeekSwipeAction.OpenNewStack)
    }

    @Test
    fun cancelledSwipeDoesNotRunActionsAndAllowsNextSwipe() {
        assertInterruptedSwipeKeepsActionsAvailable {
            down(Offset(width * 0.15f, center.y))
            moveTo(Offset(width * 0.85f, center.y), delayMillis = 200L)
            cancel()
        }
    }

    @Test
    fun secondPointerCancelsSwipeAndAllowsNextSwipe() {
        assertInterruptedSwipeKeepsActionsAvailable {
            down(pointerId = 0, position = Offset(width * 0.15f, center.y))
            moveTo(
                pointerId = 0,
                position = Offset(width * 0.85f, center.y),
                delayMillis = 200L,
            )
            down(pointerId = 1, position = Offset(center.x, center.y + 30f))
            up(pointerId = 1)
            up(pointerId = 0)
        }
    }

    @Test
    fun returningBelowThresholdBeforeReleaseDoesNotRunActions() {
        val distance = composeRule.activity.resources.displayMetrics.density * 24f
        assertInterruptedSwipeKeepsActionsAvailable {
            val start = Offset(width * 0.15f, center.y)
            down(start)
            moveTo(Offset(width * 0.85f, center.y), delayMillis = 200L)
            moveTo(start + Offset(distance, 0f), delayMillis = 200L)
            up()
        }
    }

    @Test
    fun physicalRightSwipeCopiesInRtl() {
        assertRightSwipeUsesCommittedUrl(LinkPeekSwipeAction.Copy, LayoutDirection.Rtl)
    }

    @Test
    fun physicalLeftSwipeClosesInRtl() {
        val copies = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialBrowserTheme {
                    LinkPeekOverlay(
                        url = "https://example.com/rtl",
                        progress = 0f,
                        armed = false,
                        rightSwipeAction = LinkPeekSwipeAction.Copy,
                        createPreviewView = ::previewWebView,
                        releasePreviewView = WebView::destroy,
                        onOpen = {},
                        onCopyLink = { copies.incrementAndGet() },
                        onDismiss = dismissals::incrementAndGet,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeLeft() }

        assertEquals(1, dismissals.get())
        assertEquals(0, copies.get())
    }

    @Test
    fun shortHorizontalSwipesKeepPreviewAndDoNotRunActions() {
        val copies = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/short-swipe",
                    progress = 0f,
                    armed = false,
                    rightSwipeAction = LinkPeekSwipeAction.Copy,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onCopyLink = { copies.incrementAndGet() },
                    onDismiss = dismissals::incrementAndGet,
                )
            }
        }

        val distance = composeRule.activity.resources.displayMetrics.density * 48f
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput {
            swipe(start = center, end = center + Offset(distance, 0f))
        }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput {
            swipe(start = center, end = center - Offset(distance, 0f))
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        assertEquals(0, copies.get())
        assertEquals(0, dismissals.get())
    }

    @Test
    fun verticalSwipeScrollsNativePreviewWithoutClosingOrRunningRightAction() {
        val view = AtomicReference<WebView>()
        val copies = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/scroll",
                    progress = 0f,
                    armed = false,
                    rightSwipeAction = LinkPeekSwipeAction.Copy,
                    createPreviewView = { onProgressChanged, onCommittedUrlChanged ->
                        previewWebView(onProgressChanged, onCommittedUrlChanged).also {
                            view.set(it)
                        }
                    },
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onCopyLink = { copies.incrementAndGet() },
                    onDismiss = dismissals::incrementAndGet,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            var loaded = false
            composeRule.runOnIdle { loaded = view.get().contentHeight > 1_000 }
            loaded
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeUp() }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(view.get().scrollY > 0) }
        assertEquals(0, copies.get())
        assertEquals(0, dismissals.get())
    }

    @Test
    fun unavailablePrivateAndPersistenceActionsDoNotRunAndLeaveCloseAvailable() {
        val selectedAction = mutableStateOf(LinkPeekSwipeAction.OpenPrivate)
        val actions = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/unavailable",
                    progress = 0f,
                    armed = false,
                    rightSwipeAction = selectedAction.value,
                    canOpenInPrivate = false,
                    canOpenNewStack = false,
                    canFavorite = false,
                    canSnooze = false,
                    canSaveReaderOffline = false,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = { actions.incrementAndGet() },
                    onOpenInPrivate = { actions.incrementAndGet() },
                    onOpenNewStack = { actions.incrementAndGet() },
                    onFavorite = { _, _ -> actions.incrementAndGet() },
                    onSnooze = { _, _ -> actions.incrementAndGet() },
                    onSaveReaderOffline = { _, _ -> actions.incrementAndGet() },
                    onDismiss = dismissals::incrementAndGet,
                )
            }
        }

        listOf(
            LinkPeekSwipeAction.OpenPrivate,
            LinkPeekSwipeAction.OpenNewStack,
            LinkPeekSwipeAction.Favorite,
            LinkPeekSwipeAction.Snooze,
            LinkPeekSwipeAction.ReaderLater,
        ).forEach { action ->
            composeRule.runOnIdle { selectedAction.value = action }
            composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
            assertEquals(action.name, 0, actions.get())
            assertEquals(action.name, 0, dismissals.get())
            composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeLeft() }
        assertEquals(1, dismissals.get())
        assertEquals(0, actions.get())
    }

    @Test
    fun readerRightSwipeWaitsForPreviewToFinishLoading() {
        val loadingProgress = AtomicReference<(Int) -> Unit>()
        val saves = AtomicInteger()
        val savedUrl = AtomicReference<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://redirect.example/start",
                    progress = 0f,
                    armed = false,
                    rightSwipeAction = LinkPeekSwipeAction.ReaderLater,
                    createPreviewView = { onProgressChanged, onCommittedUrlChanged ->
                        loadingProgress.set(onProgressChanged)
                        previewWebView({}, onCommittedUrlChanged).also {
                            onProgressChanged(40)
                        }
                    },
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onSaveReaderOffline = { url, _ ->
                        saves.incrementAndGet()
                        savedUrl.set(url)
                    },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        assertEquals(0, saves.get())
        composeRule.runOnIdle { loadingProgress.get().invoke(100) }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }

        assertEquals(1, saves.get())
        assertEquals("https://example.com/preview", savedUrl.get())
    }

    @Test
    fun readerRightSwipeStartsOnlyOneSaveWhileCallbackLeavesPreviewVisible() {
        val saves = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/reader-save",
                    progress = 0f,
                    armed = false,
                    rightSwipeAction = LinkPeekSwipeAction.ReaderLater,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onSaveReaderOffline = { _, _ -> saves.incrementAndGet() },
                    onDismiss = dismissals::incrementAndGet,
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        assertEquals(1, saves.get())
        assertEquals(0, dismissals.get())
    }

    @Test
    fun leftSwipeStillClosesAfterRightCallbackLeavesPreviewVisible() {
        val opens = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/new-stack",
                    progress = 0f,
                    armed = false,
                    rightSwipeAction = LinkPeekSwipeAction.OpenNewStack,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onOpenNewStack = { opens.incrementAndGet() },
                    onDismiss = dismissals::incrementAndGet,
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeLeft() }

        assertEquals(1, opens.get())
        assertEquals(1, dismissals.get())
    }

    @Test
    fun committingBlocksBothSwipeDirections() {
        val actions = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                MaterialBrowserTheme {
                    LinkPeekOverlay(
                        url = "https://example.com/committing",
                        progress = 0f,
                        armed = false,
                        committing = true,
                        rightSwipeAction = LinkPeekSwipeAction.Copy,
                        createPreviewView = ::previewWebView,
                        releasePreviewView = WebView::destroy,
                        onOpen = {},
                        onCommitRequested = { actions.incrementAndGet() },
                        onCopyLink = { actions.incrementAndGet() },
                        onDismiss = dismissals::incrementAndGet,
                    )
                }
            }
            composeRule.mainClock.advanceTimeByFrame()

            composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeLeft() }
            composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }

            assertEquals(0, actions.get())
            assertEquals(0, dismissals.get())
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun assertInterruptedSwipeKeepsActionsAvailable(
        gesture: TouchInjectionScope.() -> Unit,
    ) {
        val copies = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                LinkPeekOverlay(
                    url = "https://example.com/interrupted-swipe",
                    progress = 0f,
                    armed = false,
                    rightSwipeAction = LinkPeekSwipeAction.Copy,
                    createPreviewView = ::previewWebView,
                    releasePreviewView = WebView::destroy,
                    onOpen = {},
                    onCopyLink = { copies.incrementAndGet() },
                    onDismiss = dismissals::incrementAndGet,
                )
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput(gesture)

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).assertIsDisplayed()
        assertEquals(0, copies.get())
        assertEquals(0, dismissals.get())
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        assertEquals(1, copies.get())
        assertEquals(0, dismissals.get())
    }

    private fun assertRightSwipeUsesCommittedUrl(
        action: LinkPeekSwipeAction,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val actions = mutableListOf<Pair<LinkPeekSwipeAction, String>>()
        val backgroundOpens = AtomicInteger()
        val dismissals = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialBrowserTheme {
                    LinkPeekOverlay(
                        url = "https://redirect.example/start",
                        progress = 0f,
                        armed = false,
                        rightSwipeAction = action,
                        createPreviewView = ::previewWebView,
                        releasePreviewView = WebView::destroy,
                        onOpen = backgroundOpens::incrementAndGet,
                        onCopyLink = { actions.add(LinkPeekSwipeAction.Copy to it) },
                        onOpenForeground = { actions.add(LinkPeekSwipeAction.OpenForeground to it) },
                        onOpenNewStack = { actions.add(LinkPeekSwipeAction.OpenNewStack to it) },
                        onDismiss = dismissals::incrementAndGet,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(LinkPeekTestTags.Preview).performTouchInput { swipeRight() }

        assertEquals(listOf(action to "https://example.com/preview"), actions)
        assertEquals(0, backgroundOpens.get())
        assertEquals(0, dismissals.get())
    }

    private fun previewWebView(
        onProgressChanged: (Int) -> Unit,
        onCommittedUrlChanged: (String) -> Unit,
    ): WebView = WebView(composeRule.activity).apply {
        loadData(
            "<html><body style='margin:0'><div style='height:4000px'>Preview</div></body></html>",
            "text/html",
            "UTF-8",
        )
        onProgressChanged(100)
        onCommittedUrlChanged("https://example.com/preview")
    }
}
