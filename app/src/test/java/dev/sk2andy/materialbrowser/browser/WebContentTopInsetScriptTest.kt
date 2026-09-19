package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentTopInsetScriptTest {
    @Test
    fun `prompt addition protection retains one fresh wakeup and excludes attribute backlogs`() {
        val script = WebContentTopInsetScript.installScript
        val priority = script.substringAfter("const scheduleAddedPriorityLayoutCheck =")
            .substringBefore("const isInteractiveAtPoint =")
        val cancel = script.substringAfter("const cancelAddedPriorityLayoutCheck =")
            .substringBefore("const scheduleAddedPriorityLayoutCheck =")
        val scroll = script.substringAfter("const windowScrollListener =")
            .substringBefore("const protectInteractionTarget =")

        assertTrue(priority.contains("addedPriorityWork.roots.size === 0"))
        assertTrue(priority.contains("if (addedPriorityLayoutWakeup) return"))
        assertTrue(priority.contains("addedPriorityLayoutWakeup !== wakeup"))
        assertTrue(priority.contains("const request = addedPriorityLayoutRequest"))
        assertTrue(priority.contains("refreshPriorityCandidates(request.root, physicalPixels / density, true, isCurrent)"))
        assertTrue(priority.contains("continueAddedWork && isCurrent()"))
        assertTrue(cancel.contains("addedPriorityLayoutWakeup = null"))
        assertTrue(scroll.contains("scheduleAddedPriorityLayoutCheck()"))
        assertFalse(scroll.contains("cancelAddedPriorityLayoutCheck()"))
    }

    @Test
    fun `deferred recovery uses replaceable identities and preserves guarded emergency verification`() {
        val script = WebContentTopInsetScript.installScript
        val deferred = script.substringAfter("const armDeferredLayoutCheck =")
            .substringBefore("const scheduleDeferredLayoutCheck =")
        val verification = script.substringAfter("const scheduleScrollVerification =")
            .substringBefore("const armDeferredLayoutCheck =")
        val scroll = script.substringAfter("const windowScrollListener =")
            .substringBefore("const protectInteractionTarget =")

        assertTrue(deferred.contains("const scheduledRequest = { ...request, scrollGeneration }"))
        assertTrue(deferred.contains("deferredLayoutCheckRequest !== scheduledRequest"))
        assertTrue(deferred.contains("scheduledRequest.notBefore"))
        assertTrue(deferred.contains("lastScrollAt + layoutQuietPeriodMs()"))
        assertTrue(deferred.contains("armDeferredLayoutCheck(scheduledRequest)"))
        assertTrue(deferred.contains("} finally {"))
        assertTrue(deferred.contains("scheduleScrollVerification("))
        assertTrue(deferred.contains("!recoveryCompleted"))
        assertTrue(verification.contains("if (reopenDiscovery) candidateDiscoveryNeeded = true"))
        assertTrue(verification.contains("quietGeneration !== quietLayoutCheckGeneration"))
        assertTrue(verification.contains("generation !== scrollGeneration"))
        assertTrue(scroll.contains("armDeferredLayoutCheck(deferredLayoutCheckRequest)"))
        assertTrue(scroll.contains("quietGeneration !== quietLayoutCheckGeneration"))
    }

    @Test
    fun `priority backlog uses fair bounded lanes and retains dirty cursors until a fresh follow up`() {
        val script = WebContentTopInsetScript.installScript
        val traversal = script.substringAfter("const nextPriorityElement =")
            .substringBefore("const protectPriorityPositionedElement")
        val enqueue = script.substringAfter("const enqueuePrioritySubtree =")
            .substringBefore("const advancePriorityJob")
        val mutationObserver = script.substringAfter("observer = new MutationObserver((records) =>")
            .substringBefore("observer.observe(root, observerOptions)")
        val dispose = script.substringAfter("runtimeState.dispose = () =>")

        assertFalse(traversal.contains("Array.from(pendingPriorityRoots)"))
        assertTrue(traversal.contains("transition < maxDiscoveryPointsPerTask"))
        assertTrue(traversal.contains("preferAddedPriorityWork = !preferAddedPriorityWork"))
        assertTrue(traversal.contains("work.preferRecent = !work.preferRecent"))
        assertTrue(traversal.contains("advancePriorityJob(job, startedAt)"))
        assertTrue(traversal.contains("if (job.traversal.length > 0) return null"))
        assertTrue(traversal.contains("const needsFollowUp = job.dirty && root.isConnected"))
        assertTrue(traversal.contains("if (priorityRootJobs.get(root) !== job || !work.roots.has(root)) continue"))
        assertTrue(enqueue.contains("if (job.started) job.dirty = true"))
        assertTrue(enqueue.contains("work.roots.size > 1 ? work : other"))
        assertTrue(mutationObserver.contains("enqueuePrioritySubtree(record.target, false)"))
        assertTrue(mutationObserver.contains("removePriorityRoot(node)"))
        assertTrue(dispose.contains("work.roots.clear()"))
        assertTrue(dispose.contains("priorityRootJobs = new WeakMap()"))
    }

    @Test
    fun `compact peers reject invisible styles before querying layout geometry`() {
        val peer = WebContentTopInsetScript.installScript
            .substringAfter("const findCompactViewportWidePeer =")
            .substringBefore("const isPeerBehindPlan")

        assertTrue(peer.indexOf("style.display === 'none'") < peer.indexOf("readElementRect(current)"))
        assertTrue(peer.contains("!(Number.parseFloat(style.opacity) > 0.01)) continue"))
        assertTrue(peer.contains("rect.width >= readViewportSize().width * 0.8"))
        assertTrue(peer.contains("rect.height > 1"))
        assertFalse(peer.contains("rect.width > 1"))
    }

    @Test
    fun `performance phases are opt in static and cleared after publication`() {
        val script = WebContentTopInsetScript.installScript

        assertTrue(script.contains("performanceDiagnosticsEnabled?.() !== true"))
        assertTrue(script.contains("Candy.SafeArea.Reconcile"))
        assertTrue(script.contains("Candy.SafeArea.PointDiscovery"))
        assertTrue(script.contains("Candy.SafeArea.KnownSticky"))
        assertTrue(script.contains("Candy.SafeArea.KnownOffsets"))
        assertTrue(script.contains("Candy.SafeArea.QuietVerification"))
        assertTrue(script.contains("Candy.SafeArea.Mutations"))
        assertTrue(script.contains("performance.clearMarks(mark)"))
        assertTrue(script.contains("performance.clearMeasures(name)"))
        assertTrue(script.contains("performance.measure(name, mark)"))
        assertFalse(script.contains("performance.mark(`\${name}.end`)"))
        assertFalse(script.contains("performance.clearMarks()"))
        assertFalse(script.contains("performance.clearMeasures()"))
    }

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
    fun `viewport cover keeps author owned top headers edge to edge without disabling repair`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("topInsetPx"))
        assertTrue(WebContentTopInsetScript.installScript.contains("authorDeclaresViewportCover"))
        assertTrue(WebContentTopInsetScript.installScript.contains("viewport-fit"))
        assertFalse(WebContentTopInsetScript.installScript.contains("viewportCoverAllowed"))
        assertTrue(WebContentTopInsetScript.installScript.contains("html::before"))
    }

    @Test
    fun `semantic top header can upgrade an earlier emergency native fallback`() {
        val request = WebContentTopInsetScript.installScript
            .substringAfter("const requestNativeFallbackForTopHeader =")
            .substringBefore("const topContentBackground =")

        assertTrue(WebContentTopInsetScript.installScript.contains("let nativeTopHeaderRequested = false"))
        assertTrue(request.contains("if (nativeTopHeaderRequested) return true"))
        assertFalse(request.contains("if (nativeFallbackRequested)"))
        assertTrue(request.contains("nativeFallbackRequested = true"))
        assertTrue(request.contains("nativeTopHeaderRequested = true"))
        assertTrue(request.contains("themeColor"))
        assertTrue(request.contains("true,"))
        assertTrue(request.contains("rect.top < -0.5"))
        assertTrue(request.contains("rect.top > cssPixels + 0.5"))
        assertTrue(request.contains("style.position === 'fixed'"))
        assertTrue(request.contains("Number(globalThis.scrollY)"))
        assertTrue(request.contains("fixedTopHeaderCandidates.get(element)"))
        assertTrue(request.contains("fixedTopHeaderHasMeaningfulScroll(candidate, scrollY)"))
        assertTrue(WebContentTopInsetScript.installScript.contains("scrollY - candidate.scrollY"))
        assertTrue(WebContentTopInsetScript.installScript.contains("candidate.anchorTop -"))
        assertTrue(WebContentTopInsetScript.installScript.contains("Math.max(rect.height, cssPixels)"))
        assertTrue(WebContentTopInsetScript.installScript.contains("new Map()"))
    }

    @Test
    fun `zero inset removes prior page changes`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("physicalPixels <= 0"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.removeProperty"))
    }

    @Test
    fun `blocked spacer requests bounded native top fallback`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("getComputedStyle"))
        assertTrue(WebContentTopInsetScript.installScript.contains("suspendLayoutRecovery"))
        assertTrue(WebContentTopInsetScript.installScript.contains("suspendedLayoutRecoveryKey"))
        assertTrue(WebContentTopInsetScript.installScript.contains("fallbackToNative"))
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
        assertTrue(WebContentTopInsetScript.installScript.contains("'translate'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("`0px var("))
        assertTrue(WebContentTopInsetScript.installScript.contains("position === 'fixed'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("absoluteCandidate"))
        assertTrue(WebContentTopInsetScript.installScript.contains("panelMaxHeight"))
        assertTrue(WebContentTopInsetScript.installScript.contains("isBackdrop"))
        assertTrue(WebContentTopInsetScript.installScript.contains("hasPositionedPeerCollision"))
        assertTrue(WebContentTopInsetScript.installScript.contains("composedContains(peer, plan.element)"))
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
            .substringAfter("const refreshOffsetElements = (elements, cssPixels) =>")
            .substringBefore("const refreshKnownOffsets")

        assertTrue(refresh.contains("Keep an established offset"))
        assertTrue(refresh.contains("clearOwnedOffset(element)"))
        assertTrue(refresh.contains("!isVisiblePositionedElement(element)"))
    }

    @Test
    fun `sticky controls are rechecked and offset while the page scrolls`() {
        val scrollListener = WebContentTopInsetScript.installScript
            .substringAfter("const windowScrollListener = () =>")
            .substringBefore("const protectInteractionTarget")

        assertTrue(WebContentTopInsetScript.installScript.contains("position === 'sticky'"))
        assertTrue(WebContentTopInsetScript.installScript.contains("stickyAttribute"))
        assertTrue(WebContentTopInsetScript.installScript.contains("stickyOriginalTopProperty"))
        assertTrue(WebContentTopInsetScript.installScript.contains("stickyTopProperty"))
        assertTrue(WebContentTopInsetScript.installScript.contains("protectStickyTopAnchors"))
        assertTrue(WebContentTopInsetScript.installScript.contains("refreshOwnedStickyElements"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "top: var(",
            ),
        )
        assertTrue(WebContentTopInsetScript.installScript.contains("stickyScrollportTop"))
        assertTrue(WebContentTopInsetScript.installScript.contains("Math.max(originalTop"))
        assertTrue(WebContentTopInsetScript.installScript.contains("windowScrollListener"))
        assertTrue(WebContentTopInsetScript.installScript.contains("protectLateTopInset"))
        assertTrue(WebContentTopInsetScript.installScript.contains("verifyLateTopInset"))
        assertTrue(WebContentTopInsetScript.installScript.contains("scrollVerificationTimer"))
        assertTrue(WebContentTopInsetScript.installScript.contains("scrollVerificationFailures"))
        assertTrue(WebContentTopInsetScript.installScript.contains("requiredConsecutiveLayoutFailures"))
        assertTrue(WebContentTopInsetScript.installScript.contains("{ passive: true }"))
        assertTrue(WebContentTopInsetScript.installScript.contains("capture: true"))
        assertTrue(WebContentTopInsetScript.installScript.contains("style.position !== 'sticky'"))
        assertTrue(scrollListener.contains("refreshKnownStickyElements"))
        assertTrue(
            scrollListener.indexOf("refreshKnownStickyElements") <
                scrollListener.indexOf("scrollVerificationTimer = globalThis.setTimeout"),
        )
        assertTrue(
            scrollListener.indexOf("protectStickyTopAnchors") >
                scrollListener.indexOf("scrollVerificationTimer = globalThis.setTimeout"),
        )
    }

    @Test
    fun `open shadow roots participate in top obstruction checks`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("deepElementsFromPoint"))
        assertTrue(WebContentTopInsetScript.installScript.contains("element.shadowRoot"))
        assertTrue(WebContentTopInsetScript.installScript.contains("parentElementOrShadowHost"))
        assertTrue(WebContentTopInsetScript.installScript.contains("ownedOffsetElements"))
        assertTrue(WebContentTopInsetScript.installScript.contains("observeOpenShadowRoot"))
        assertTrue(WebContentTopInsetScript.installScript.contains("observedShadowRoots"))
        assertTrue(WebContentTopInsetScript.installScript.contains("observeDiscoveredShadowRoot"))
        assertTrue(WebContentTopInsetScript.installScript.contains("attachShadowHook"))
        assertTrue(WebContentTopInsetScript.installScript.contains("attachShadowHookActive = false"))
        assertTrue(WebContentTopInsetScript.installScript.contains("restoreAttachShadowHook"))
        assertFalse(
            WebContentTopInsetScript.installScript.contains("scope.querySelectorAll?.('*')"),
        )
    }

    @Test
    fun `scroll and feed mutations defer broad layout recovery`() {
        val scrollListener = WebContentTopInsetScript.installScript
            .substringAfter("const windowScrollListener = () =>")
            .substringBefore("const protectInteractionTarget")
        val mutationObserver = WebContentTopInsetScript.installScript
            .substringAfter("observer = new MutationObserver((records) =>")
            .substringBefore("observer.observe(root, observerOptions)")

        assertFalse(scrollListener.substringBefore("scrollVerificationTimer = globalThis.setTimeout")
            .contains("protectStickyTopAnchors"))
        assertTrue(scrollListener.contains("refreshKnownStickyElements"))
        assertTrue(scrollListener.contains("protectStickyTopAnchors"))
        assertFalse(mutationObserver.contains("scheduleImmediateLayoutCheck"))
        assertTrue(mutationObserver.contains("pendingOwnedLayoutMutation = true"))
        assertTrue(mutationObserver.contains("records.some(mutationTouchesOwnedLayout)"))
        assertTrue(mutationObserver.contains("scheduleOwnedMutationLayoutCheck"))
        assertTrue(mutationObserver.contains("records.some(mutationNeedsImmediateOwnedLayout)"))
        assertTrue(mutationObserver.contains("flushPendingOwnedLayoutMutation(physicalPixels / density)"))
        assertTrue(mutationObserver.contains("scheduleDeferredLayoutCheck(true)"))

        val pendingFlush = WebContentTopInsetScript.installScript
            .substringAfter("const flushPendingOwnedLayoutMutation =")
            .substringBefore("const refreshOwnedOffsets")
        assertTrue(pendingFlush.contains("revalidateOwnedStickyAnchors(cssPixels)"))
        assertTrue(pendingFlush.contains("refreshKnownOffsets(cssPixels)"))
        assertTrue(pendingFlush.contains("refreshKnownStickyElements(cssPixels)"))
    }

    @Test
    fun `incompatible root layout gets a targeted flow spacer before recovery stops`() {
        assertTrue(WebContentTopInsetScript.installScript.contains("readViewportSize().width * 0.8"))
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
        assertTrue(WebContentTopInsetScript.installScript.contains("isVisiblePositionedElement"))
    }

    @Test
    fun `ordinary offset plans do not resolve panel CSS height`() {
        val plan = WebContentTopInsetScript.installScript.substringAfter("const planLocalOffset =")
            .substringBefore("const applyLocalOffsetPlans")

        assertTrue(plan.contains("if (isFixedPanel)"))
        assertTrue(plan.indexOf("if (isFixedPanel)") < plan.indexOf("Number.parseFloat(style.height)"))
        assertTrue(plan.contains("let panelMaxHeight = null"))
    }

    @Test
    fun `mutation classification shares one synchronous read epoch with immediate repair`() {
        val observer = WebContentTopInsetScript.installScript
            .substringAfter("observer = new MutationObserver((records) =>")
            .substringBefore("const originalAttachShadow")

        assertTrue(observer.indexOf("withLayoutReadCache(() =>") < observer.indexOf("records.forEach"))
        assertTrue(observer.indexOf("withLayoutReadCache(() =>") < observer.indexOf("records.some(mutationTouchesOwnedLayout)"))
        assertTrue(observer.contains("flushPendingOwnedLayoutMutation(physicalPixels / density)"))
        assertTrue(observer.contains("endCandyPerformancePhase('Candy.SafeArea.Mutations', phase)"))
        assertFalse(observer.contains("getBoundingClientRect"))
    }

    @Test
    fun `parent reads are task local and owned writes invalidate reentrant read caches`() {
        val script = WebContentTopInsetScript.installScript
        val parentRead = script.substringAfter("const parentElementOrShadowHost = (element) =>")
            .substringBefore("const composedContains")
        val ownedWrite = script.substringAfter("const withOwnedLayoutWrite = (write) =>")
            .substringBefore("const setOwnedProperty")

        assertTrue(script.contains("layoutReadCache.parents = null"))
        assertTrue(parentRead.contains("!element || !cache"))
        assertTrue(parentRead.contains("cache.parents ||= new WeakMap()"))
        assertTrue(parentRead.contains("parents.has(element)"))
        assertTrue(parentRead.contains("layoutReadCache === cache && cache.parents === parents"))
        assertTrue(ownedWrite.contains("finally"))
        assertTrue(ownedWrite.contains("invalidateLayoutReadCache()"))
    }

    @Test
    fun `read collection invalidation is allocation free and readers guard publication`() {
        val script = WebContentTopInsetScript.installScript
        val invalidation = script.substringAfter("const invalidateLayoutReadCache = () =>")
            .substringBefore("const withLayoutReadCache")

        assertFalse(invalidation.contains("new WeakMap"))
        assertFalse(invalidation.contains("new Map"))
        assertTrue(script.contains("cache.styles ||= new WeakMap()"))
        assertTrue(script.contains("cache.rects === rects"))
        assertTrue(script.contains("cache.points === points"))
        assertTrue(script.contains("layoutWriteRevision === revision"))
    }

    @Test
    fun `sticky revalidation checks fresh position before resolving author top`() {
        val repair = WebContentTopInsetScript.installScript
            .substringAfter("const revalidateOwnedStickyAnchors =")
            .substringBefore("const mutationTouchesOwnedLayout")

        val cleanup = repair.indexOf("clearOwnedSticky(element)")
        val styleRead = repair.indexOf("readComputedStyle(element)")
        val positionGuard = repair.indexOf("if (style.position !== 'sticky') continue")
        val topRead = repair.indexOf("Number.parseFloat(style.top)")

        assertTrue(cleanup >= 0 && styleRead > cleanup)
        assertTrue(positionGuard > styleRead && topRead > positionGuard)
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
        assertTrue(WebContentTopInsetScript.installScript.contains("scrollLayoutCheckFrame"))
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
        assertTrue(reconfigure.contains("cancelDeferredLayoutCheck()"))
        val cancellation = WebContentTopInsetScript.installScript
            .substringAfter("const cancelDeferredLayoutCheck = () =>")
            .substringBefore("const scheduleScrollVerification =")
        assertTrue(cancellation.contains("globalThis.clearTimeout(deferredLayoutCheckTimer)"))
        assertTrue(cancellation.contains("deferredLayoutCheckRequest = null"))
        assertTrue(reconfigure.contains("cancelQuietLayoutCheck()"))
        assertTrue(reconfigure.contains("resetFailuresForPolicy(currentPolicyKey(), true)"))
        assertTrue(reconfigure.contains("reconcile()"))
    }

    @Test
    fun `policy revisions reset failures before they suspend layout recovery`() {
        val recoverySuspension = WebContentTopInsetScript.installScript
            .substringAfter("const suspendLayoutRecovery = (reason = 'unknown') =>")
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
        assertTrue(recoverySuspension.contains("requestNativeFallback()"))
        assertTrue(
            WebContentTopInsetScript.installScript.contains(
                "resetFailuresForPolicy(currentPolicyKey(), true)",
            ),
        )
    }
}
