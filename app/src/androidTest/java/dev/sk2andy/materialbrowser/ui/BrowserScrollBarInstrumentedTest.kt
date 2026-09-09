package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserScrollBarInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun progressActionUsesEngineNeutralScrollPort() {
        val requestedOffset = mutableIntStateOf(-1)
        setScrollBarContent { requestedOffset.intValue = it }
        val description = context.getString(R.string.scroll_bar_content_description)

        composeRule.onNodeWithContentDescription(description)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.5f)
            }

        composeRule.runOnIdle { assertEquals(750, requestedOffset.intValue) }
    }

    @Test
    fun touchDragMovesEngineDocumentForward() {
        val requestedOffset = mutableIntStateOf(-1)
        setScrollBarContent { requestedOffset.intValue = it }
        val description = context.getString(R.string.scroll_bar_content_description)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(description).performTouchInput {
            down(center)
            moveBy(Offset(0f, 120f))
            up()
        }

        composeRule.runOnIdle {
            assertTrue("Scrollbar drag did not request a lower page offset", requestedOffset.intValue > 0)
        }
    }

    private fun setScrollBarContent(onScroll: (Int) -> Unit) {
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.fillMaxSize()) {
                    BrowserScrollBar(
                        metrics = BrowserEngineScrollMetrics(
                            offsetPx = 0,
                            extentPx = 500,
                            rangePx = 2_000,
                        ),
                        revealNonce = 1,
                        onScrollToVerticalOffset = onScroll,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}
