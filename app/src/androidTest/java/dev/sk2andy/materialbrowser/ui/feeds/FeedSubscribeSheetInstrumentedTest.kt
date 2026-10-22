package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.feeds.FeedPreview
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedSubscribeSheetInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun savingFailureKeepsPreviewAndRetryDismissesOnlyAfterSuccess() {
        var attempts = 0
        var dismissed = false
        val repository = FeedRepository.get(context, "subscribe-ui-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedSubscribeDialog(
                    repository,
                    FeedState(),
                    "https://example.com/feed",
                    mutate = {},
                    onDismiss = { dismissed = true },
                    previewRequest = { Result.success(preview(it, "A source worth keeping")) },
                    subscribeRequest = { _, _ ->
                        attempts++
                        if (attempts == 1) Result.failure(IOException("storage full"))
                        else Result.success(Unit)
                    },
                )
            }
        }
        composeRule.onNodeWithText("A source worth keeping").assertExists()
        composeRule.onNodeWithTag("feeds_subscribe_confirm").performClick()
        composeRule.onNodeWithTag("feeds_subscribe_error").assertExists()
        composeRule.onNodeWithText("A source worth keeping").assertExists()
        composeRule.runOnIdle { assertFalse(dismissed) }
        composeRule.onNodeWithTag("feeds_subscribe_confirm").performClick()
        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertEquals(2, attempts)
        }
    }

    @Test
    fun privatePreviewNeverOffersPersistentSubscription() {
        val repository = FeedRepository.get(context, "private-subscribe-ui", isPrivate = true)
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedSubscribeDialog(
                    repository,
                    FeedState(),
                    "https://example.com/feed",
                    mutate = {},
                    onDismiss = {},
                    previewRequest = { Result.success(preview(it, "Private source preview")) },
                    subscribeRequest = { _, _ -> error("Private preview must never subscribe") },
                )
            }
        }
        composeRule.onNodeWithText("Private source preview").assertExists()
        composeRule.onNodeWithTag("feeds_subscribe_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("feeds_subscribe_handle", useUnmergedTree = true).assertExists()
    }

    @Test
    fun recreationKeepsAnUnsubmittedNormalAddressWithoutRequestingIt() {
        val restoration = StateRestorationTester(composeRule)
        var requests = 0
        val repository = FeedRepository.get(context, "subscribe-draft-${UUID.randomUUID()}")
        restoration.setContent {
            MaterialBrowserTheme {
                FeedSubscribeDialog(
                    repository,
                    FeedState(),
                    "",
                    mutate = {},
                    onDismiss = {},
                    previewRequest = {
                        requests++
                        Result.success(preview(it, "Unexpected"))
                    },
                )
            }
        }
        composeRule
            .onNodeWithTag("feeds_subscribe_url")
            .performTextReplacement("https://publisher.example/my-feed")
        restoration.emulateSavedInstanceStateRestore()
        composeRule
            .onNodeWithTag("feeds_subscribe_url")
            .assertTextContains("https://publisher.example/my-feed")
        composeRule.runOnIdle { assertEquals(0, requests) }
    }

    @Test
    fun replacingAddressDiscardsLatePreviewFromEarlierRequest() {
        val stale = CompletableDeferred<Result<FeedPreview>>()
        val repository = FeedRepository.get(context, "stale-subscribe-ui-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedSubscribeDialog(
                    repository,
                    FeedState(),
                    "https://old.example/feed",
                    mutate = {},
                    onDismiss = {},
                    previewRequest = { url ->
                        if (url.contains("old.example"))
                            withContext(NonCancellable) { stale.await() }
                        else Result.success(preview(url, "The source I chose"))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("feeds_subscribe_loading").assertExists()
        composeRule
            .onNodeWithTag("feeds_subscribe_url")
            .performTextReplacement("https://new.example/feed")
        composeRule.onNodeWithTag("feeds_subscribe_url").performImeAction()
        composeRule.onNodeWithText("The source I chose").assertExists()
        stale.complete(Result.success(preview("https://old.example/feed", "Obsolete source")))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Obsolete source").assertDoesNotExist()
        composeRule.onNodeWithText("The source I chose").assertExists()
    }

    private fun preview(url: String, title: String) = FeedPreview(url, title, url, emptyList())
}
