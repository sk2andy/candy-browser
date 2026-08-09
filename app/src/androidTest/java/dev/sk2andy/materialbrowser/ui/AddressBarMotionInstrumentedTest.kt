package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarMotionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactExpandedFadeThroughNeverPaintsBothContentsInOneFrame() {
        val presentation = mutableStateOf(AddressBarPresentation.Compact)
        setTransitionContent(presentation)
        assertTargetColor(AddressBarPresentation.Compact)

        verifyDirection(
            presentation = presentation,
            target = AddressBarPresentation.Expanded,
        )
        verifyDirection(
            presentation = presentation,
            target = AddressBarPresentation.Compact,
        )
    }

    @Test
    fun rapidCompactExpandedReversalNeverPaintsBothContentsInOneFrame() {
        val presentation = mutableStateOf(AddressBarPresentation.Compact)
        setTransitionContent(presentation)

        composeRule.runOnIdle { presentation.value = AddressBarPresentation.Expanded }
        advanceFramesAndAssertNoOverlap(frameCount = 3)
        composeRule.runOnIdle { presentation.value = AddressBarPresentation.Compact }
        advanceFramesAndAssertNoOverlap(frameCount = 16)
        assertTargetColor(AddressBarPresentation.Compact)

        verifyDirection(
            presentation = presentation,
            target = AddressBarPresentation.Expanded,
        )
        composeRule.runOnIdle { presentation.value = AddressBarPresentation.Compact }
        advanceFramesAndAssertNoOverlap(frameCount = 6)
        composeRule.runOnIdle { presentation.value = AddressBarPresentation.Expanded }
        advanceFramesAndAssertNoOverlap(frameCount = 16)
        assertTargetColor(AddressBarPresentation.Expanded)
    }

    @Test
    fun expandedAddressInputFadesOutBeforeOverviewActionsFadeIn() {
        val presentation = mutableStateOf(AddressBarPresentation.Expanded)
        setTransitionContent(
            presentation = presentation,
            foregroundPresentation = AddressBarPresentation.Expanded,
        )
        assertTargetColor(
            target = AddressBarPresentation.Expanded,
            foregroundPresentation = AddressBarPresentation.Expanded,
        )

        verifyDirection(
            presentation = presentation,
            target = AddressBarPresentation.Overview,
            foregroundPresentation = AddressBarPresentation.Expanded,
        )
    }

    @Test
    fun compactToEditingRequestsFocusAfterExpandedContentIsComposed() {
        val presentation = mutableStateOf(AddressBarPresentation.Compact)
        val editValue = mutableStateOf("")
        val focusRequester = FocusRequester()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AddressBarPresentationTransition(
                presentation = presentation.value,
            ) { target ->
                if (target == AddressBarPresentation.Expanded) {
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        focusRequester.requestFocus()
                    }
                    BasicTextField(
                        value = editValue.value,
                        onValueChange = { editValue.value = it },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag(EditorTag),
                    )
                } else {
                    Box(Modifier.size(100.dp))
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { presentation.value = AddressBarPresentation.Expanded }
        repeat(16) { composeRule.mainClock.advanceTimeByFrame() }

        composeRule.onNodeWithTag(EditorTag).assertIsFocused()
    }

    private fun setTransitionContent(
        presentation: MutableState<AddressBarPresentation>,
        foregroundPresentation: AddressBarPresentation = AddressBarPresentation.Compact,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.Black),
                ) {
                    AddressBarPresentationTransition(
                        presentation = presentation.value,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(TransitionTag),
                    ) { target ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (target == foregroundPresentation) {
                                        Color.Red
                                    } else {
                                        Color.Blue
                                    },
                                ),
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun verifyDirection(
        presentation: MutableState<AddressBarPresentation>,
        target: AddressBarPresentation,
        foregroundPresentation: AddressBarPresentation = AddressBarPresentation.Compact,
    ) {
        composeRule.runOnIdle { presentation.value = target }
        advanceFramesAndAssertNoOverlap(frameCount = 16)
        assertTargetColor(target, foregroundPresentation)
    }

    private fun advanceFramesAndAssertNoOverlap(frameCount: Int) {
        repeat(frameCount) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            val center = centerColor()
            assertFalse(
                "Frame $frame painted outgoing and incoming content together: $center",
                center.red > VisibleChannelThreshold && center.blue > VisibleChannelThreshold,
            )
        }
    }

    private fun assertTargetColor(
        target: AddressBarPresentation,
        foregroundPresentation: AddressBarPresentation = AddressBarPresentation.Compact,
    ) {
        val center = centerColor()
        val targetVisible = if (target == foregroundPresentation) {
            center.red > SettledChannelThreshold && center.blue < VisibleChannelThreshold
        } else {
            center.blue > SettledChannelThreshold && center.red < VisibleChannelThreshold
        }
        assertTrue("Expected settled $target content, but center pixel was $center", targetVisible)
    }

    private fun centerColor(): Color {
        val pixels = composeRule.onNodeWithTag(TransitionTag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private companion object {
        const val TransitionTag = "address_bar_presentation_transition"
        const val EditorTag = "address_bar_editor"
        const val VisibleChannelThreshold = 0.04f
        const val SettledChannelThreshold = 0.95f
    }
}
