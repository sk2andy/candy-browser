package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineMediaPlayerSeekSliderInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun secondsSemanticsAndHapticsFollowOnlyUserStopChanges() {
        var seconds by mutableStateOf(10)
        var enabled by mutableStateOf(true)
        val changed = mutableListOf<Int>()
        val view = CountingHapticView(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalView provides view) {
                MaterialBrowserTheme {
                    InlineMediaPlayerSeekSlider(
                        title = context.getString(R.string.settings_inline_media_player_seek_backward),
                        seconds = seconds,
                        enabled = enabled,
                        onSecondsChanged = {
                            seconds = it
                            changed.add(it)
                        },
                        modifier = Modifier.testTag("seek"),
                    )
                }
            }
        }
        val slider = composeRule.onNodeWithTag("seek")
        slider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(current = 1f, range = 0f..5f, steps = 4),
        ))
        slider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(R.string.settings_inline_media_player_seek_seconds, 10),
        ))
        assertEquals(emptyList<Int>(), view.calls)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        assertEquals(listOf(15), changed)
        assertEquals(listOf(HapticFeedbackConstants.SEGMENT_TICK), view.calls)
        composeRule.runOnIdle { seconds = 30 }
        slider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(R.string.settings_inline_media_player_seek_seconds, 30),
        ))
        assertEquals(listOf(HapticFeedbackConstants.SEGMENT_TICK), view.calls)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        assertEquals(listOf(15, 60), changed)
        assertEquals(List(2) { HapticFeedbackConstants.SEGMENT_TICK }, view.calls)
        composeRule.runOnIdle { enabled = false }
        slider.assertIsNotEnabled()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertEquals(60, seconds)
        assertEquals(List(2) { HapticFeedbackConstants.SEGMENT_TICK }, view.calls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nativeKeyboardMirrorsStopsInRtlAndTickFallsBackWhenUnsupported() {
        var seconds by mutableStateOf(10)
        val view = CountingHapticView(context, segmentTickSupported = false)
        composeRule.setContent {
            CompositionLocalProvider(LocalView provides view, LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialBrowserTheme {
                    InlineMediaPlayerSeekSlider(
                        title = context.getString(R.string.settings_inline_media_player_seek_forward),
                        seconds = seconds,
                        enabled = true,
                        onSecondsChanged = { seconds = it },
                        modifier = Modifier.testTag("seek"),
                    )
                }
            }
        }
        val slider = composeRule.onNodeWithTag("seek")
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(15, seconds)
        assertEquals(listOf(HapticFeedbackConstants.SEGMENT_TICK, HapticFeedbackConstants.CLOCK_TICK), view.calls)
        slider.performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(5, seconds)
        slider.performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(60, seconds)
        slider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(R.string.settings_inline_media_player_seek_seconds, 60),
        ))
        assertEquals(6, view.calls.size)
    }

    private class CountingHapticView(
        context: Context,
        private val segmentTickSupported: Boolean = true,
    ) : View(context) {
        val calls = mutableListOf<Int>()

        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            calls.add(feedbackConstant)
            return segmentTickSupported || feedbackConstant != HapticFeedbackConstants.SEGMENT_TICK
        }
    }
}
