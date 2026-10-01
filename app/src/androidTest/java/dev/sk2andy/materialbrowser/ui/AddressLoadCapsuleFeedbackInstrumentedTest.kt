package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserAddressLoadStyle
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressLoadCapsuleFeedbackInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun determinateLoadExposesProgressSemantics() {
        setFeedbackContent(progressPercent = 42)

        composeRule.onNodeWithTag(FeedbackTag).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(current = 0.42f, range = 0f..1f, steps = 0),
            ),
        )
    }

    @Test
    fun indeterminateLoadExposesIndeterminateSemantics() {
        setFeedbackContent(progressPercent = 0)

        composeRule.onNodeWithTag(FeedbackTag).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        )
    }

    @Test
    fun tonalLoadKeepsDeterminateProgressSemantics() {
        setFeedbackContent(progressPercent = 42, style = BrowserAddressLoadStyle.Tonal)
        assertProgress(0.42f)
    }

    @Test
    fun tonalLoadKeepsIndeterminateProgressSemantics() {
        setFeedbackContent(progressPercent = 0, style = BrowserAddressLoadStyle.Tonal)
        composeRule.onNodeWithTag(FeedbackTag).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        )
    }

    @Test
    fun engineProgressUpdatesThenCompletionRemovesFeedback() {
        val progress = mutableStateOf(0)
        val loading = mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 56.dp)) {
                    AddressLoadCapsuleFeedback(
                        tabId = "tab",
                        isLoading = loading.value,
                        progressPercent = progress.value,
                        morphProgress = 0f,
                        morphTargetSizePx = 56f,
                        modifier = Modifier.matchParentSize().testTag(FeedbackTag),
                    )
                }
            }
        }
        composeRule.runOnIdle { progress.value = 42 }
        assertProgress(0.42f)
        composeRule.runOnIdle { progress.value = 80 }
        assertProgress(0.8f)
        composeRule.runOnIdle {
            progress.value = 100
            loading.value = false
        }
        composeRule.onNodeWithTag(FeedbackTag).assertDoesNotExist()
    }

    @Test
    fun interruptedLoadRemovesFeedbackWithoutClaimingCompletion() {
        val loading = mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 56.dp)) {
                    AddressLoadCapsuleFeedback(
                        tabId = "tab",
                        isLoading = loading.value,
                        progressPercent = 42,
                        morphProgress = 0f,
                        morphTargetSizePx = 56f,
                        modifier = Modifier.matchParentSize().testTag(FeedbackTag),
                    )
                }
            }
        }
        assertProgress(0.42f)
        composeRule.runOnIdle { loading.value = false }
        composeRule.onNodeWithTag(FeedbackTag).assertDoesNotExist()
    }

    private fun assertProgress(progress: Float) {
        composeRule.onNodeWithTag(FeedbackTag).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(current = progress, range = 0f..1f, steps = 0),
            ),
        )
    }

    private fun setFeedbackContent(
        progressPercent: Int,
        style: BrowserAddressLoadStyle = BrowserAddressLoadStyle.Rainbow,
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                val density = LocalDensity.current
                Box(modifier = Modifier.size(width = 320.dp, height = 56.dp)) {
                    AddressLoadCapsuleFeedback(
                        tabId = "tab",
                        isLoading = true,
                        progressPercent = progressPercent,
                        style = style,
                        morphProgress = 0f,
                        morphTargetSizePx = with(density) { 56.dp.toPx() },
                        modifier = Modifier
                            .matchParentSize()
                            .testTag(FeedbackTag),
                    )
                }
            }
        }
    }

    private companion object {
        const val FeedbackTag = "address_load_feedback"
    }
}
