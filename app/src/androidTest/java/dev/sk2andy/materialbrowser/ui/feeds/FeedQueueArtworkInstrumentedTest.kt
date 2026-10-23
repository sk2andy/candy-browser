package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedQueueArtworkInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun suppliedFaviconKeepsActualPlaybackProgressAccessible() {
        val favicon = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedQueueContent(
                    state = queueState(),
                    playback = FeedQueuePlaybackState(progress = 0.42f),
                    onTogglePlayback = {},
                    onNext = {},
                    onSpeedChange = {},
                    onMoveUp = {},
                    onRemove = {},
                    onImportSaved = {},
                    onRetry = {},
                    favicon = favicon,
                )
            }
        }
        composeRule.onNodeWithTag("feeds_queue_favicon").assertExists()
        composeRule.onNodeWithTag("feeds_queue_favicon_fallback").assertDoesNotExist()
        composeRule
            .onNodeWithTag("feeds_queue_progress")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(current = 0.42f, range = 0f..1f),
                )
            )
    }

    @Test
    fun invalidBitmapFallsBackToSourceMonogram() {
        val favicon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { recycle() }
        composeRule.setContent {
            MaterialBrowserTheme { FeedQueueArtwork(favicon = favicon, source = "Field Notes") }
        }
        composeRule.onNodeWithTag("feeds_queue_favicon").assertDoesNotExist()
        composeRule.onNodeWithTag("feeds_queue_favicon_fallback").assertExists()
    }

    @Test
    fun playerHasNoCloseButtonAndKeepsExplicitStopAction() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedQueueContent(
                    state = queueState(),
                    playback = FeedQueuePlaybackState(),
                    onTogglePlayback = {},
                    onNext = {},
                    onSpeedChange = {},
                    onMoveUp = {},
                    onRemove = {},
                    onImportSaved = {},
                    onRetry = {},
                    onStop = { events += "stop" },
                )
            }
        }
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.feeds_player_close))
            .assertDoesNotExist()
        composeRule
            .onNodeWithTag("feeds_queue_player")
            .performScrollToNode(hasTestTag("feeds_queue_stop"))
        composeRule.onNodeWithTag("feeds_queue_stop").performClick()
        composeRule.runOnIdle { assertEquals(listOf("stop"), events) }
    }

    private fun queueState() =
        FeedState(
            articles =
                listOf(FeedArticle("first", emptySet(), "https://example.org/story", "Story")),
            queue = listOf("first"),
        )
}
