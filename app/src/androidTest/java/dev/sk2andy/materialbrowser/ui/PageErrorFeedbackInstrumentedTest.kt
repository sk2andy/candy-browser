package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageErrorFeedbackInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notFoundPageOffersReloadWithoutTechnicalErrorText() {
        val reloads = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = PageErrorFeedbackState.NotFound,
                    onRetry = reloads::incrementAndGet,
                    onGameChange = {},
                )
            }
        }

        composeRule.onNodeWithText("This page was snacked away").assertExists()
        composeRule.onNodeWithContentDescription("Destination not found").assertExists()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Retry).performClick()

        assertEquals(1, reloads.get())
    }

    @Test
    fun unreachablePageUsesFriendlyMissingDestinationLayout() {
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = PageErrorFeedbackState.Error("unknown host"),
                    onRetry = {},
                    onGameChange = {},
                )
            }
        }

        composeRule.onNodeWithText("Website not found").assertExists()
        composeRule.onNodeWithContentDescription("Destination not found").assertExists()
        composeRule.onNodeWithText("unknown host").assertDoesNotExist()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Retry).assertExists()
    }

    @Test
    fun offlineStateImmediatelyShowsAccessibleCandyCircuitGame() {
        val state = mutableStateOf<PageErrorFeedbackState>(PageErrorFeedbackState.Offline())
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = state.value,
                    onRetry = {},
                    onGameChange = { game ->
                        val offline = state.value as PageErrorFeedbackState.Offline
                        state.value = offline.copy(game = game)
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Game).assertExists()
        composeRule.onNodeWithText("Play Candy Circuit").assertDoesNotExist()
        composeRule.onNodeWithText("Candy Circuit").assertExists()
        composeRule.onNodeWithText("12").assertExists()
        val scoreHeight = composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Score)
            .fetchSemanticsNode().boundsInRoot.height
        val bestHeight = composeRule.onNodeWithTag(PageErrorFeedbackTestTags.BestScore)
            .fetchSemanticsNode().boundsInRoot.height
        val movesHeight = composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Moves)
            .fetchSemanticsNode().boundsInRoot.height
        assertEquals(scoreHeight, bestHeight, 0.5f)
        assertEquals(scoreHeight, movesHeight, 0.5f)
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.TilePrefix + 5).performClick()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Score).assertTextContains("400")
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Moves).assertTextContains("13")
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.TilePrefix + 5)
            .assertContentDescriptionContains("Row 2, column 2", substring = true)
    }

    @Test
    fun reconnectBannerKeepsGameAndLoadsOnlyAfterButtonClick() {
        val reloads = AtomicInteger()
        val state = mutableStateOf<PageErrorFeedbackState>(
            PageErrorFeedbackState.Offline(),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = state.value,
                    onRetry = reloads::incrementAndGet,
                    onGameChange = { game ->
                        val offline = state.value as PageErrorFeedbackState.Offline
                        state.value = offline.copy(game = game)
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.OnlineBanner).assertDoesNotExist()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.TilePrefix + 5).performClick()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Score).assertTextContains("400")
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Moves).assertTextContains("13")
        composeRule.runOnIdle {
            val offline = state.value as PageErrorFeedbackState.Offline
            state.value = offline.copy(isOnlineReady = true)
        }
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Game).assertExists()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Score).assertTextContains("400")
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Moves).assertTextContains("13")
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.OnlineBanner)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        assertEquals(0, reloads.get())

        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Retry).performClick()
        assertEquals(1, reloads.get())
    }

    @Test
    fun closedLoopCelebratesBeforeRefillTilesUnlock() {
        val state = mutableStateOf<PageErrorFeedbackState>(PageErrorFeedbackState.Offline())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = state.value,
                    onRetry = {},
                    onGameChange = { game ->
                        val offline = state.value as PageErrorFeedbackState.Offline
                        state.value = offline.copy(game = game)
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.TilePrefix + 5).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Celebration).assertExists()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.TilePrefix + 0).assertIsNotEnabled()

        composeRule.mainClock.advanceTimeBy(
            CandyCircuitMotionRules.RESOLUTION_DURATION_MILLIS.toLong() + 100L,
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Celebration).assertDoesNotExist()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.TilePrefix + 0).assertIsEnabled()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Score).assertTextContains("400")
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Moves).assertTextContains("13")
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun finishedRoundDisablesTilesAndOffersRestart() {
        val state = mutableStateOf<PageErrorFeedbackState>(
            PageErrorFeedbackState.Offline(
                game = CandyCircuitGameState(movesRemaining = 0, bestScore = 1_600),
            ),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = state.value,
                    onRetry = {},
                    onGameChange = { game ->
                        val offline = state.value as PageErrorFeedbackState.Offline
                        state.value = offline.copy(game = game)
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.TilePrefix + 5).assertIsNotEnabled()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Restart).performClick()
        composeRule.onNodeWithText("12").assertExists()
    }
}
