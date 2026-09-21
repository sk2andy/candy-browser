package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class WebContentTopInsetTransitionRulesTest {
    @Test
    fun `only document and web content header ownership changes animate`() {
        assertEquals(
            true,
            WebContentTopInsetTransitionRules.shouldAnimate(
                previousState = WebContentTopInsetTransitionState.Document,
                nextState = WebContentTopInsetTransitionState.WebContentHeader,
            ),
        )
        assertEquals(
            true,
            WebContentTopInsetTransitionRules.shouldAnimate(
                previousState = WebContentTopInsetTransitionState.WebContentHeader,
                nextState = WebContentTopInsetTransitionState.Document,
            ),
        )
        assertEquals(
            false,
            WebContentTopInsetTransitionRules.shouldAnimate(
                previousState = WebContentTopInsetTransitionState.WebContentHeader,
                nextState = WebContentTopInsetTransitionState.Other,
            ),
        )
        assertEquals(
            false,
            WebContentTopInsetTransitionRules.shouldAnimate(
                previousState = WebContentTopInsetTransitionState.Other,
                nextState = WebContentTopInsetTransitionState.WebContentHeader,
            ),
        )
    }

    @Test
    fun `adding native top inset keeps current pixels stationary`() {
        assertEquals(
            -96f,
            WebContentTopInsetTransitionRules.startTranslationY(
                currentTranslationY = 0f,
                previousTopInsetPx = 0,
                nextTopInsetPx = 96,
                canAnimate = true,
            ),
        )
    }

    @Test
    fun `removing native top inset keeps current pixels stationary`() {
        assertEquals(
            96f,
            WebContentTopInsetTransitionRules.startTranslationY(
                currentTranslationY = 0f,
                previousTopInsetPx = 96,
                nextTopInsetPx = 0,
                canAnimate = true,
            ),
        )
    }

    @Test
    fun `interrupted transition preserves current visual position`() {
        assertEquals(
            56f,
            WebContentTopInsetTransitionRules.startTranslationY(
                currentTranslationY = -40f,
                previousTopInsetPx = 96,
                nextTopInsetPx = 0,
                canAnimate = true,
            ),
        )
    }

    @Test
    fun `disabled animation applies final layout without translation`() {
        assertEquals(
            0f,
            WebContentTopInsetTransitionRules.startTranslationY(
                currentTranslationY = 12f,
                previousTopInsetPx = 0,
                nextTopInsetPx = 96,
                canAnimate = false,
            ),
        )
    }
}
