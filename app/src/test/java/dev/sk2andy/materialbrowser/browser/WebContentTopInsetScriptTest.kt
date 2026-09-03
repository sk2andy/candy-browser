package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentTopInsetScriptTest {
    @Test
    fun `script converts the Android inset to CSS pixels`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("physicalPixels / density"))
        assertTrue(WebContentTopInsetScript.installScript.contains("devicePixelRatio"))
    }

    @Test
    fun `script uses one scrollable root spacer`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("html::before"))
        assertTrue(WebContentTopInsetScript.installScript.contains("display: block"))
    }

    @Test
    fun `script only changes the main frame after the root exists`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("globalThis.top !== globalThis"))
        assertTrue(WebContentTopInsetScript.installScript.contains("readystatechange"))
    }

    @Test
    fun `script removes the spacer for viewport cover`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("viewportCoverAllowed"))
        assertTrue(WebContentTopInsetScript.installScript.contains("viewportFitsCover"))
        assertTrue(WebContentTopInsetScript.installScript.contains("viewportFit === 'cover'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("MutationObserver"))
    }

    @Test
    fun `zero inset removes prior page changes`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("physicalPixels <= 0"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.removeProperty"))
    }

    @Test
    fun `blocked spacer requests native fallback for the current document`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("getComputedStyle"))
        assertTrue(WebContentTopInsetScript.installScript.contains("fallbackToNative"))
        assertTrue(WebContentTopInsetScript.installScript.contains("navigationGeneration"))
    }

    @Test
    fun `small positioned controls get a local top inset offset`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("elementFromPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("obstructionSampleStep"))
        assertTrue(WebContentTopInsetScript.installScript.contains("trailingPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("planLocalOffset"))
        assertTrue(WebContentTopInsetScript.installScript.contains("translate: 0 var"))
        assertTrue(WebContentTopInsetScript.installScript.contains("absoluteCandidate"))
        assertTrue(WebContentTopInsetScript.installScript.contains("position === 'fixed'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("panelMaxHeight"))
        assertTrue(WebContentTopInsetScript.installScript.contains("isBackdrop"))
        assertTrue(WebContentTopInsetScript.installScript.contains("scheduleInteractionLayoutCheck"))
        assertTrue(WebContentTopInsetScript.installScript.contains("delayedInteractionCheckMs"))
        assertTrue(WebContentTopInsetScript.installScript.contains("interactionEvents"))
    }

    @Test
    fun `incompatible root layout gets a targeted flow spacer before native fallback`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("innerWidth * 0.8"))
        assertTrue(WebContentTopInsetScript.installScript.contains("installTargetedFlowInset"))
        assertTrue(WebContentTopInsetScript.installScript.contains("flowTargetAttribute"))
        assertTrue(WebContentTopInsetScript.installScript.contains("requestNativeFallback"))
        assertTrue(WebContentTopInsetScript.installScript.contains("DOMContentLoaded"))
    }

    @Test
    fun `late root layout changes get bounded deferred checks`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("scheduleDeferredLayoutCheck"))
        assertTrue(WebContentTopInsetScript.installScript.contains("maxDeferredLayoutChecks"))
        assertTrue(WebContentTopInsetScript.installScript.contains("readyState === 'loading'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("addedNodes"))
        assertTrue(WebContentTopInsetScript.installScript.contains("addEventListener('load'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.display === 'none'"))
    }
}
