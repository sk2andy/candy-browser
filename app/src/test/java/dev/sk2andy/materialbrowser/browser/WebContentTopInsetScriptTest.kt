package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
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
    fun `root spacer extends the first visible page background behind the status bar`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("topContentBackground"))
        assertTrue(WebContentTopInsetScript.installScript.contains("cssPixels + 1"))
        assertTrue(WebContentTopInsetScript.installScript.contains("document.elementFromPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("document.elementsFromPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("paintedBackground"))
        assertTrue(WebContentTopInsetScript.installScript.contains("return style.backgroundColor"))
        assertFalse(WebContentTopInsetScript.installScript.contains("getComputedStyle(element, '::before')"))
        assertTrue(WebContentTopInsetScript.installScript.contains("meta[name=\"theme-color\"]"))
        assertTrue(WebContentTopInsetScript.installScript.contains("background: var"))
        assertTrue(WebContentTopInsetScript.installScript.contains("getPropertyValue(backgroundProperty)"))
        assertTrue(WebContentTopInsetScript.installScript.contains("getPropertyPriority(backgroundProperty)"))
        assertTrue(WebContentTopInsetScript.installScript.contains("document.body"))
    }

    @Test
    fun `script only changes the main frame after the root exists`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("documentElementObserver"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "documentElementObserver.observe(document, { childList: true })",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "globalThis.__candyReconcileContentTopInset = reconcile",
            ),
        )
    }

    @Test
    fun `viewport cover cannot disable the Candy owned top inset`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("topInsetPx"))
        assertFalse(WebContentTopInsetScript.installScript.contains("viewportFitsCover"))
        assertFalse(WebContentTopInsetScript.installScript.contains("viewport-fit"))
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
        assertTrue(WebContentTopInsetScript.installScript.contains("policyRevision"))
        assertTrue(WebContentTopInsetScript.installScript.contains("nativeFallbackRequestKey"))
    }

    @Test
    fun `small positioned controls get a local top inset offset`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("elementFromPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("obstructionSampleStep"))
        assertTrue(WebContentTopInsetScript.installScript.contains("trailingPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("planLocalOffset"))
        assertTrue(WebContentTopInsetScript.installScript.contains("isVisiblePositionedElement"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.opacity"))
        assertTrue(WebContentTopInsetScript.installScript.contains("translate: 0 var"))
        assertTrue(WebContentTopInsetScript.installScript.contains("position === 'fixed'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("absoluteCandidate"))
        assertTrue(WebContentTopInsetScript.installScript.contains("panelMaxHeight"))
        assertTrue(WebContentTopInsetScript.installScript.contains("isBackdrop"))
        assertTrue(WebContentTopInsetScript.installScript.contains("hasPositionedPeerCollision"))
        assertTrue(WebContentTopInsetScript.installScript.contains("peer.contains(plan.element)"))
        assertTrue(WebContentTopInsetScript.installScript.contains("interactivePeerSelector"))
        assertTrue(WebContentTopInsetScript.installScript.contains("isInteractivePositionedPeer"))
        assertTrue(WebContentTopInsetScript.installScript.contains("peerIsInteractive ||"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.cursor === 'pointer'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("findCompactViewportWidePeer"))
        assertTrue(WebContentTopInsetScript.installScript.contains("elementsFromPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("localOffsetCollisionDetected"))
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
        assertTrue(WebContentTopInsetScript.installScript.contains("stabilizationCheckDelaysMs"))
        assertTrue(WebContentTopInsetScript.installScript.contains("readyState === 'loading'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("addedNodes"))
        assertTrue(WebContentTopInsetScript.installScript.contains("addEventListener('load'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.display === 'none'"))
    }
}
