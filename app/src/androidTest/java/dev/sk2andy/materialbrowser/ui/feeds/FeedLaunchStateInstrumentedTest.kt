package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedLaunchStateInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pageUrl = "https://publisher.example/article"
    private val feedUrl = "https://publisher.example/feed.xml"

    @Test
    fun dismissedBrowserPreviewDoesNotReopenButOpenPreviewSurvivesRecreation() {
        val restoration = StateRestorationTester(composeRule)
        // An unavailable profile fails preview access before making a network request.
        val repository = FeedRepository.get(context, "launch-ui-${UUID.randomUUID()}")
        var discoveries = 0
        val invocation = mutableStateOf("first-invocation")
        lateinit var launchState: FeedLaunchState
        restoration.setContent {
            launchState = rememberFeedLaunchState(repository.profileId, false)
            LaunchedEffect(invocation.value, pageUrl) {
                if (launchState.needsDiscovery(invocation.value, pageUrl)) {
                    discoveries++
                    launchState.completeDiscovery(invocation.value, pageUrl, listOf(feedUrl))
                }
            }
            MaterialBrowserTheme {
                FeedsScreen(
                    repository = repository,
                    library = ReaderLibraryState(),
                    initialFeedUrl = launchState.selectedFeedUrl,
                    onInitialFeedConsumed = launchState::consumeSelectedFeed,
                    onOpenArticle = {},
                    onOpenSnapshot = {},
                    onOpenOriginal = {},
                    onDownloadArticle = {},
                    onRemoveSnapshot = {},
                    onPlayQueue = {},
                    onImportOpml = {},
                    onExportOpml = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("feeds_subscribe_url").assertTextContains(feedUrl)
        composeRule.runOnIdle { assertNull(launchState.selectedFeedUrl) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("feeds_subscribe_url").assertTextContains(feedUrl)
        composeRule.onNodeWithText(context.getString(R.string.feeds_cancel)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("feeds_subscribe_sheet").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, discoveries)
            invocation.value = "second-invocation"
        }
        composeRule.onNodeWithTag("feeds_subscribe_url").assertTextContains(feedUrl)
        composeRule.runOnIdle { assertEquals(2, discoveries) }
    }

    @Test
    fun pendingPreviewSurvivesUntilCollectionAcceptsIt() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var state: FeedLaunchState
        restoration.setContent { state = rememberFeedLaunchState("profile", false) }
        composeRule.runOnIdle { state.completeDiscovery("launch", pageUrl, listOf(feedUrl)) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertEquals(feedUrl, state.selectedFeedUrl)
            assertFalse(state.needsDiscovery("launch", pageUrl))
            state.consumeSelectedFeed()
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertNull(state.selectedFeedUrl)
            assertFalse(state.needsDiscovery("launch", pageUrl))
        }
    }

    @Test
    fun discoveryChoicesRestoreWhileOpenAndStayClosedAfterDismissal() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var state: FeedLaunchState
        restoration.setContent { state = rememberFeedLaunchState("profile", false) }
        val candidates = listOf(feedUrl, "https://publisher.example/news.xml")
        composeRule.runOnIdle { state.completeDiscovery("launch", pageUrl, candidates) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertEquals(candidates, state.discoveryCandidates)
            state.dismissCandidates()
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertTrue(state.discoveryCandidates.isEmpty())
            assertFalse(state.needsDiscovery("launch", pageUrl))
        }
    }

    @Test
    fun playerLaunchRestoresVisibilityWithoutReplayingDismissedRequest() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var state: FeedLaunchState
        restoration.setContent {
            state = rememberFeedLaunchState("profile", false)
            LaunchedEffect(Unit) { state.acceptQueueRequest("first-invocation", 42) }
        }
        composeRule.runOnIdle { assertTrue(state.queueVisible) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertTrue(state.queueVisible)
            state.queueVisible = false
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertFalse(state.queueVisible)
            state.acceptQueueRequest("second-invocation", 42)
            assertTrue(state.queueVisible)
        }
    }

    @Test
    fun savedDiscoveryChoicesAndAddressesAreBounded() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var candidates: FeedLaunchState
        lateinit var pending: FeedLaunchState
        restoration.setContent {
            candidates = rememberFeedLaunchState("profile", false)
            pending = rememberFeedLaunchState("profile", false)
        }
        composeRule.runOnIdle {
            candidates.completeDiscovery(
                "launch",
                pageUrl,
                List(40) { "https://publisher.example/feed-$it.xml" },
            )
            val oversized = "https://publisher.example/" + "a".repeat(4096)
            pending.completeDiscovery("launch", oversized, listOf(oversized))
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertEquals(32, candidates.discoveryCandidates.size)
            assertTrue(candidates.discoveryCandidates.all { it.length <= 4096 })
            assertNull(pending.discoveryHandledUrl)
            assertNull(pending.selectedFeedUrl)
        }
    }

    @Test
    fun privateDiscoveryAndLaunchStateAreNotSaved() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var state: FeedLaunchState
        restoration.setContent { state = rememberFeedLaunchState("profile", true) }
        composeRule.runOnIdle {
            state.completeDiscovery("launch", "https://private.example/secret", listOf(feedUrl))
            state.acceptQueueRequest("first-invocation", 42)
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertNull(state.discoveryHandledUrl)
            assertNull(state.selectedFeedUrl)
            assertTrue(state.discoveryCandidates.isEmpty())
            assertFalse(state.queueVisible)
            assertEquals(0, state.queueHandledId)
        }
    }
}
