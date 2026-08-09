package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureOnboardingRulesTest {
    @Test
    fun `tab switch accepts either horizontal direction`() {
        assertTrue(
            GestureOnboardingRules.isCompleted(
                GestureOnboardingStep.SwitchTabs,
                dragX = -72f,
                dragY = 10f,
                threshold = 72f,
            ),
        )
        assertTrue(
            GestureOnboardingRules.isCompleted(
                GestureOnboardingStep.SwitchTabs,
                dragX = 72f,
                dragY = -10f,
                threshold = 72f,
            ),
        )
    }

    @Test
    fun `overview and close require dominant upward drags`() {
        listOf(
            GestureOnboardingStep.OpenTabOverview,
            GestureOnboardingStep.CloseTab,
        ).forEach { step ->
            assertTrue(
                GestureOnboardingRules.isCompleted(
                    step,
                    dragX = 8f,
                    dragY = -72f,
                    threshold = 72f,
                ),
            )
            assertFalse(
                GestureOnboardingRules.isCompleted(
                    step,
                    dragX = 80f,
                    dragY = -72f,
                    threshold = 72f,
                ),
            )
        }
    }

    @Test
    fun `short drag never completes a step`() {
        GestureOnboardingStep.entries.forEach { step ->
            assertFalse(
                GestureOnboardingRules.isCompleted(
                    step,
                    dragX = 20f,
                    dragY = -20f,
                    threshold = 72f,
                ),
            )
        }
    }
}
