package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestureOnboardingScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyGestureMustBePerformedInOrder() {
        val completed = AtomicBoolean(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                GestureOnboardingScreen(onCompleted = { completed.set(true) })
            }
        }

        startTutorial()

        composeRule.onNodeWithTag(tag(GestureOnboardingStep.PullToRefresh))
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(tag(GestureOnboardingStep.PullToRefresh)).assertIsDisplayed()

        composeRule.onNodeWithTag(tag(GestureOnboardingStep.PullToRefresh))
            .performTouchInput { swipeDown() }
        composeRule.onNodeWithTag(tag(GestureOnboardingStep.SwitchTabs))
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(tag(GestureOnboardingStep.OpenTabOverview))
            .performScrollTo()
            .performTouchInput {
                swipe(
                    start = center,
                    end = center + Offset(0f, -320f),
                )
            }
        composeRule.onNodeWithTag(tag(GestureOnboardingStep.CloseTab))
            .performScrollTo()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(tag(GestureOnboardingStep.CloseTab))
            .performTouchInput { swipeUp() }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("gesture_onboarding_celebration").assertIsDisplayed()
        assertFalse(completed.get())
        composeRule.onAllNodesWithTag("gesture_onboarding_finish").assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onAllNodesWithTag("gesture_onboarding_finish").assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("gesture_onboarding_finish").assertIsDisplayed()
        composeRule.onNodeWithTag("gesture_onboarding_finish").performClick()
        composeRule.mainClock.advanceTimeBy(360)
        assertFalse(completed.get())
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 2_000) { completed.get() }
        assertTrue(completed.get())
    }

    @Test
    fun accessibilityCompletionActionIsExposed() {
        composeRule.setContent {
            MaterialBrowserTheme {
                GestureOnboardingScreen(onCompleted = {})
            }
        }

        startTutorial()

        composeRule.onNodeWithTag(tag(GestureOnboardingStep.PullToRefresh))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    }

    @Test
    fun skipCompletesFromTheWelcomePage() {
        val completed = AtomicBoolean(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                GestureOnboardingScreen(onCompleted = { completed.set(true) })
            }
        }

        composeRule.onNodeWithTag("gesture_onboarding_skip").performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { completed.get() }
        assertTrue(completed.get())
    }

    private fun startTutorial() {
        composeRule.onNodeWithTag("gesture_onboarding_welcome").assertIsDisplayed()
        composeRule.onAllNodesWithTag(tag(GestureOnboardingStep.PullToRefresh))
            .assertCountEquals(0)
        composeRule.onNodeWithTag("gesture_onboarding_start").performClick()
        composeRule.onNodeWithTag(tag(GestureOnboardingStep.PullToRefresh))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(pointerTag(GestureOnboardingStep.PullToRefresh))
            .assertCountEquals(1)
    }

    private fun tag(step: GestureOnboardingStep): String = "gesture_onboarding_${step.name}"

    private fun pointerTag(step: GestureOnboardingStep): String =
        "gesture_onboarding_pointer_${step.name}"
}
