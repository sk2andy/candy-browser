package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
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
    fun retryClickImmediatelyShowsAccessibleProgressAndDisablesAction() {
        val retries = AtomicInteger()
        val state = mutableStateOf<PageErrorFeedbackState>(
            PageErrorFeedbackState.Error("Connection refused"),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = state.value,
                    onRetry = {
                        state.value = PageErrorFeedbackState.Retrying("Connection refused")
                        retries.incrementAndGet()
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Card)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                ),
            )
        composeRule.onNodeWithText("Connection refused").assertExists()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Retry)
            .assertHasClickAction()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, retries.get())
        composeRule.onNodeWithText("Connection refused").assertExists()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Card)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Retry).assertIsNotEnabled()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.RetryProgress).assertExists()
    }

    @Test
    fun retryProgressPreservesMessageAndDisablesDuplicateAction() {
        val retries = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                PageErrorFeedback(
                    state = PageErrorFeedbackState.Retrying("Connection refused"),
                    onRetry = retries::incrementAndGet,
                )
            }
        }

        composeRule.onNodeWithText("Connection refused").assertExists()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.Retry).assertIsNotEnabled()
        composeRule.onNodeWithTag(PageErrorFeedbackTestTags.RetryProgress).assertExists()

        assertEquals(0, retries.get())
    }
}
