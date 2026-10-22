package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedQueueContentInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun queueOffersPlaybackReorderingRemovalAndSavedImport() {
        var state by
            mutableStateOf(
                FeedState(
                    articles =
                        listOf(
                            article("first", "First article"),
                            article("second", "Second article"),
                            article("saved", "A bookmark").copy(isStarred = true),
                        ),
                    queue = listOf("first", "second"),
                )
            )
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedQueueContent(
                    state = state,
                    playback = FeedQueuePlaybackState(progress = 0.42f),
                    onTogglePlayback = { events += "play" },
                    onNext = { events += "next" },
                    onSpeedChange = { events += "rate:$it" },
                    onMoveUp = { id ->
                        events += "up:$id"
                        state = state.copy(queue = listOf("second", "first"))
                    },
                    onRemove = { id ->
                        events += "remove:$id"
                        state = state.copy(queue = listOf("second"))
                    },
                    onImportSaved = { events += "saved" },
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("feeds_queue_play").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("feeds_queue_next").performClick()
        composeRule.onNodeWithTag("feeds_queue_speed").performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.feeds_speech_speed, "1.5"))
            .performClick()
        composeRule
            .onNodeWithTag("feeds_queue_player")
            .performScrollToNode(hasTestTag("feeds_queue_up_second"))
        composeRule.onNodeWithTag("feeds_queue_up_second").performClick()
        composeRule.onNodeWithTag("feeds_queue_player").performScrollToIndex(0)
        composeRule.onNodeWithTag("feeds_queue_current_title").assertTextEquals("Second article")
        composeRule
            .onNodeWithTag("feeds_queue_player")
            .performScrollToNode(hasTestTag("feeds_queue_remove_first"))
        composeRule.onNodeWithTag("feeds_queue_remove_first").performClick()
        composeRule
            .onNodeWithTag("feeds_queue_player")
            .performScrollToNode(hasText(context.getString(R.string.feeds_player_import_saved)))
        composeRule
            .onNodeWithText(context.getString(R.string.feeds_player_import_saved))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf("play", "next", "rate:1.5", "up:second", "remove:first", "saved"),
                events,
            )
        }
    }

    @Test
    fun unavailableVoiceExplainsWhyPlaybackCannotStart() {
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedQueueContent(
                    state =
                        FeedState(
                            articles = listOf(article("first", "First article")),
                            queue = listOf("first"),
                        ),
                    playback =
                        FeedQueuePlaybackState(
                            status = ReaderSpeechStatus.Unavailable,
                            progress = 0.42f,
                        ),
                    onTogglePlayback = { error("Unavailable voice must not start playback") },
                    onNext = {},
                    onSpeedChange = {},
                    onMoveUp = {},
                    onRemove = {},
                    onImportSaved = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("feeds_queue_play").assertIsNotEnabled()
        composeRule
            .onNodeWithText(context.getString(R.string.feeds_player_progress, 42))
            .assertExists()
        composeRule
            .onNodeWithTag("feeds_queue_player")
            .performScrollToNode(hasText(context.getString(R.string.feeds_speech_unavailable)))
        composeRule
            .onNodeWithText(context.getString(R.string.feeds_speech_unavailable))
            .assertExists()
    }

    private fun article(id: String, title: String) =
        FeedArticle(id, emptySet(), "https://example.org/$id", title)
}
