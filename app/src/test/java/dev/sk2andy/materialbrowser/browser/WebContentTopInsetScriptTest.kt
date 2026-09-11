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
    fun `viewport cover delegates top protection to engine safe area`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("topInsetPx"))
        assertTrue(WebContentTopInsetScript.installScript.contains("viewportFitsCover"))
        assertTrue(WebContentTopInsetScript.installScript.contains("viewportCoverAllowed"))
        assertTrue(WebContentTopInsetScript.installScript.contains("viewport-fit"))
    }

    @Test
    fun `zero inset removes prior page changes`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("physicalPixels <= 0"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.removeProperty"))
    }

    @Test
    fun `blocked spacer preserves edge to edge and stops repeated recovery`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("getComputedStyle"))
        assertTrue(WebContentTopInsetScript.installScript.contains("suspendLayoutRecovery"))
        assertTrue(WebContentTopInsetScript.installScript.contains("suspendedLayoutRecoveryKey"))
        assertFalse(WebContentTopInsetScript.installScript.contains("fallbackToNative"))
        assertTrue(WebContentTopInsetScript.installScript.contains("navigationGeneration"))
        assertTrue(WebContentTopInsetScript.installScript.contains("policyRevision"))
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
        assertTrue(WebContentTopInsetScript.installScript.contains("isInteractiveAtPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("isCompactInteractive"))
        assertTrue(WebContentTopInsetScript.installScript.contains("plannedCandidates"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "compactControlTopPaddingCssPixels = 8",
            ),
        )
        assertTrue(WebContentTopInsetScript.installScript.contains("minimumTop"))
        assertTrue(WebContentTopInsetScript.installScript.contains("plan.minimumTop"))
        assertTrue(WebContentTopInsetScript.installScript.contains("peerIsViewportWide"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.cursor === 'pointer'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("findCompactViewportWidePeer"))
        assertTrue(WebContentTopInsetScript.installScript.contains("elementsFromPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("localOffsetCollisionDetected"))
        assertTrue(WebContentTopInsetScript.installScript.contains("scheduleInteractionLayoutCheck"))
        assertTrue(WebContentTopInsetScript.installScript.contains("protectInteractionTarget"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "['focusin', 'compositionend', 'input']",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains("defaultLayoutQuietPeriodMs = 400"),
        )
        assertTrue(WebContentTopInsetScript.installScript.contains("interactionEvents"))
    }

    @Test
    fun `site hide and show transitions retain established fixed offsets`() {
        val refresh = WebContentTopInsetScript.installScript
            .substringAfter("const refreshOwnedOffsets = (cssPixels) =>")
            .substringBefore("const isBackdrop")

        assertTrue(refresh.contains("Keep an established offset"))
        assertTrue(refresh.contains("clearOwnedOffset(element)"))
        assertTrue(
            refresh.indexOf("Number.parseFloat(style.opacity) <= 0.01") <
                refresh.indexOf("clearOwnedOffset(element)"),
        )
    }

    @Test
    fun `sticky controls are rechecked and offset while the page scrolls`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("position === 'sticky'"))
        assertFalse(WebContentTopInsetScript.installScript.contains("windowScrollListener"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.position !== 'sticky'"))
    }

    @Test
    fun `incompatible root layout gets a targeted flow spacer before recovery stops`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("innerWidth * 0.8"))
        assertTrue(WebContentTopInsetScript.installScript.contains("installTargetedFlowInset"))
        assertTrue(WebContentTopInsetScript.installScript.contains("flowTargetAttribute"))
        assertTrue(WebContentTopInsetScript.installScript.contains("suspendLayoutRecovery"))
        assertTrue(WebContentTopInsetScript.installScript.contains("DOMContentLoaded"))
    }

    @Test
    fun `late root layout changes get bounded deferred checks`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("scheduleDeferredLayoutCheck"))
        assertTrue(WebContentTopInsetScript.installScript.contains("maxDeferredLayoutChecks"))
        assertTrue(WebContentTopInsetScript.installScript.contains("stabilizationCheckDelaysMs"))
        assertTrue(WebContentTopInsetScript.installScript.contains("readyState === 'loading'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("addedNodes"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "attributeFilter: ['class', 'content', 'style']",
            ),
        )
        assertTrue(WebContentTopInsetScript.installScript.contains("record.removedNodes"))
        assertTrue(WebContentTopInsetScript.installScript.contains("windowResizeListener"))
        assertTrue(WebContentTopInsetScript.installScript.contains("resumeLayoutRecovery"))
        assertTrue(WebContentTopInsetScript.installScript.contains("'load',"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.display === 'none'"))
    }

    @Test
    fun `layout recovery suspension waits for stability and repeated failures`() {
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "if (document.readyState === 'loading') return",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "defaultRequiredConsecutiveLayoutFailures = 3",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "consecutiveLayoutFailures < requiredFailureCount",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "safeAreaLayoutQuietPeriodMillis",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "safeAreaRequiredFailureCount",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "scheduleDeferredLayoutCheck(true)",
            ),
        )
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "scheduleDeferredLayoutCheck(false)",
            ),
        )
    }

    @Test
    fun `reinjection disposes every pending layout check and listener`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("previousState.dispose()"))
        assertTrue(WebContentTopInsetScript.installScript.contains("runtimeState.dispose = () =>"))
        assertTrue(WebContentTopInsetScript.installScript.contains("deferredLayoutCheckTimer = 0"))
        assertTrue(WebContentTopInsetScript.installScript.contains("domContentLoadedListener"))
        assertTrue(WebContentTopInsetScript.installScript.contains("windowLoadListener"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "runtimeState.stabilizationCheckTimers.forEach(globalThis.clearTimeout)",
            ),
        )
    }

    @Test
    fun `runtime reconfiguration resets pending failure confirmation`() {
        val reconfigure = WebContentTopInsetScript.installScript
            .substringAfter("const reconfigure = () =>")
            .substringBefore("const isTransparentColor")

        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "globalThis.__candyReconfigureContentTopInset = reconfigure",
            ),
        )
        assertTrue(reconfigure.contains("globalThis.clearTimeout(deferredLayoutCheckTimer)"))
        assertTrue(reconfigure.contains("resetFailuresForPolicy(currentPolicyKey(), true)"))
        assertTrue(reconfigure.contains("reconcile()"))
    }

    @Test
    fun `policy revisions reset failures before they suspend layout recovery`() {
        val recoverySuspension = WebContentTopInsetScript.installScript
            .substringAfter("const suspendLayoutRecovery = () =>")
            .substringBefore("const layoutRecoverySuspendedForCurrentPolicy")

        assertTrue(recoverySuspension.contains("resetFailuresForPolicy(policyKey)"))
        assertTrue(
            recoverySuspension.indexOf("resetFailuresForPolicy(policyKey)") <
                recoverySuspension.indexOf("consecutiveLayoutFailures++"),
        )
        assertTrue(recoverySuspension.contains("confirmedPolicyKey !== policyKey"))
        assertTrue(recoverySuspension.contains("resetFailuresForPolicy(confirmedPolicyKey, true)"))
        assertTrue(recoverySuspension.contains("suspendedLayoutRecoveryKey = policyKey"))
        assertFalse(recoverySuspension.contains("remove()"))
        assertFalse(recoverySuspension.contains("fallbackToNative"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "resetFailuresForPolicy(currentPolicyKey(), true)",
            ),
        )
    }
}
