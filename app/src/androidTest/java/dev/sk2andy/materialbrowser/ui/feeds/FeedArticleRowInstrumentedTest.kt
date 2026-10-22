package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedOfflineStatus
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedArticleRowInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bookmarkAndQueueToggleIndependentlyWithoutOpeningTheArticle() {
        var opened = 0
        var actions = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                var article by remember {
                    mutableStateOf(
                        FeedArticle(
                            id = "article",
                            sourceIds = emptySet(),
                            url = "https://example.org/article",
                            title = "A calmer reading list",
                            offlineStatus = FeedOfflineStatus.Ready,
                        )
                    )
                }
                var queued by remember { mutableStateOf(false) }
                FeedArticleRow(
                    article = article,
                    state = FeedState(queue = if (queued) listOf("article") else emptyList()),
                    onOpen = { opened++ },
                    onActions = { actions++ },
                    onToggleSaved = { article = article.copy(isStarred = !article.isStarred) },
                    onToggleQueue = { queued = !queued },
                )
            }
        }
        composeRule.onNodeWithTag("feed_bookmark_article").assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithTag("feed_queue_article").assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText(context.getString(R.string.feeds_offline)).assertExists()
        composeRule.onNodeWithTag("feed_bookmark_article").performClick().assertIsOff()
        composeRule.onNodeWithTag("feed_queue_article").assertIsOn()
        composeRule.onNodeWithTag("feed_actions_article").performClick()
        assertEquals(0, opened)
        assertEquals(1, actions)
        composeRule.onNodeWithTag("feed_article_article").performClick()
        assertEquals(1, opened)
    }
}
