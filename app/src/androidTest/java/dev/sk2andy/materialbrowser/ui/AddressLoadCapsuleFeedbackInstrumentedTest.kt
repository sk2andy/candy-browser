package dev.sk2andy.materialbrowser.ui

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

    private fun setFeedbackContent(progressPercent: Int) {
        composeRule.setContent {
            MaterialBrowserTheme {
                val density = LocalDensity.current
                Box(modifier = Modifier.size(width = 320.dp, height = 56.dp)) {
                    AddressLoadCapsuleFeedback(
                        tabId = "tab",
                        isLoading = true,
                        progressPercent = progressPercent,
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
