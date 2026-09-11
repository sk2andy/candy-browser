package dev.sk2andy.materialbrowser.browser

internal object WebContentTopInsetScript {
    const val bridgeName = "CandyContentTopInset"

    val installScript: String =
        """
            (() => {
              const styleId = 'candy-browser-content-top-inset';
              const ownedSelector = `style#${'$'}{styleId}[data-candy-browser-owned="true"]`;
              const property = '--candy-browser-content-top-inset';
              const backgroundProperty = '--candy-browser-content-top-background';
              const offsetAttribute = 'data-candy-browser-top-inset-offset';
              const offsetSelector = `[${'$'}{offsetAttribute}="true"]`;
              const offsetProperty = '--candy-browser-owned-top-inset-offset';
              const stickyAttribute = 'data-candy-browser-top-inset-sticky';
              const stickySelector = `[${'$'}{stickyAttribute}="true"]`;
              const stickyOriginalTopProperty = '--candy-browser-owned-sticky-original-top';
              const stickyTopProperty = '--candy-browser-owned-sticky-top';
              const panelAttribute = 'data-candy-browser-top-inset-panel';
              const panelMaxHeightProperty = '--candy-browser-owned-panel-max-height';
              const flowRootAttribute = 'data-candy-browser-targeted-top-inset';
              const flowTargetAttribute = 'data-candy-browser-top-inset-flow-target';
              const flowTargetSelector = `[${'$'}{flowTargetAttribute}="true"]`;
              const flowMarginProperty = '--candy-browser-owned-flow-margin';
              const flowOffsetProperty = '--candy-browser-owned-flow-offset';
              const stateKey = '__candyBrowserContentTopInset';
              const obstructionSampleStep = 12;
              const maxDeferredLayoutChecks = 8;
              const defaultLayoutQuietPeriodMs = 400;
              const minimumLayoutQuietPeriodMs = 100;
              const maximumLayoutQuietPeriodMs = 800;
              const defaultRequiredConsecutiveLayoutFailures = 3;
              const minimumRequiredConsecutiveLayoutFailures = 2;
              const maximumRequiredConsecutiveLayoutFailures = 5;
              const compactControlTopPaddingCssPixels = 8;
              const stabilizationCheckDelaysMs = [250, 1000, 2500, 5000];
              const ownedOffsetElements =
                globalThis[stateKey]?.ownedOffsetElements || new Set();
              const ownedTranslateStates =
                globalThis[stateKey]?.ownedTranslateStates || new Map();
              const ownedStickyElements =
                globalThis[stateKey]?.ownedStickyElements || new Set();
              let deferredLayoutChecks = 0;
              let deferredLayoutCheckTimer = 0;
              let immediateLayoutCheckFrame = 0;
              let consecutiveLayoutFailures = 0;
              let activePolicyKey = null;
              let suspendedLayoutRecoveryKey = null;
              let localOffsetCollisionDetected = false;
              let nativeFallbackRequested = false;
              let scrollLayoutCheckFrame = 0;
              let scrollVerificationTimer = 0;
              let scrollVerificationFailures = 0;
              let scrollVerificationPolicyKey = null;
              let shadowHitTestCache = new WeakMap();
              let shadowHitTestCacheResetFrame = 0;
              const currentPolicyKey = () => {
                const generation = Number(
                  globalThis.$bridgeName?.navigationGeneration?.(),
                ) || 0;
                const revision = Number(
                  globalThis.$bridgeName?.policyRevision?.(),
                ) || 0;
                return `${'$'}{generation}:${'$'}{revision}`;
              };
              const resetFailuresForPolicy = (policyKey, force = false) => {
                if (!force && activePolicyKey === policyKey) return;
                activePolicyKey = policyKey;
                deferredLayoutChecks = 0;
                consecutiveLayoutFailures = 0;
              };
              const layoutQuietPeriodMs = () => {
                const value = Number(
                  globalThis.$bridgeName?.safeAreaLayoutQuietPeriodMillis?.(),
                );
                return Number.isSafeInteger(value)
                  ? Math.min(maximumLayoutQuietPeriodMs, Math.max(minimumLayoutQuietPeriodMs, value))
                  : defaultLayoutQuietPeriodMs;
              };
              const requiredConsecutiveLayoutFailures = () => {
                const value = Number(
                  globalThis.$bridgeName?.safeAreaRequiredFailureCount?.(),
                );
                return Number.isSafeInteger(value)
                  ? Math.min(
                    maximumRequiredConsecutiveLayoutFailures,
                    Math.max(minimumRequiredConsecutiveLayoutFailures, value),
                  )
                  : defaultRequiredConsecutiveLayoutFailures;
              };
              const clearOwnedOffset = (element) => {
                const translateState = ownedTranslateStates.get(element);
                element.removeAttribute(offsetAttribute);
                element.removeAttribute(panelAttribute);
                element.style.removeProperty(offsetProperty);
                element.style.removeProperty(panelMaxHeightProperty);
                if (translateState?.value) {
                  element.style.setProperty(
                    'translate',
                    translateState.value,
                    translateState.priority,
                  );
                } else {
                  element.style.removeProperty('translate');
                }
                ownedTranslateStates.delete(element);
                ownedOffsetElements.delete(element);
              };
              const clearOwnedOffsets = () => {
                new Set([
                  ...ownedOffsetElements,
                  ...document.querySelectorAll(offsetSelector),
                ]).forEach(clearOwnedOffset);
              };
              const clearOwnedSticky = (element) => {
                element.removeAttribute(stickyAttribute);
                element.style.removeProperty(stickyOriginalTopProperty);
                element.style.removeProperty(stickyTopProperty);
                ownedStickyElements.delete(element);
              };
              const clearOwnedStickyElements = () => {
                new Set([
                  ...ownedStickyElements,
                  ...document.querySelectorAll(stickySelector),
                ]).forEach(clearOwnedSticky);
              };
              const clearOwnedFlowTarget = (root) => {
                root.removeAttribute(flowRootAttribute);
                document.querySelectorAll(flowTargetSelector).forEach((element) => {
                  element.removeAttribute(flowTargetAttribute);
                  element.style.removeProperty(flowMarginProperty);
                  element.style.removeProperty(flowOffsetProperty);
                });
              };
              const requestNativeFallback = () => {
                if (nativeFallbackRequested) return;
                nativeFallbackRequested = true;
                const generation = Number(
                  globalThis.$bridgeName?.navigationGeneration?.(),
                ) || 0;
                const revision = Number(
                  globalThis.$bridgeName?.policyRevision?.(),
                ) || 0;
                globalThis.$bridgeName?.fallbackToNative?.(generation, revision);
              };
              const suspendLayoutRecovery = (reason = 'unknown') => {
                document.documentElement?.setAttribute(
                  'data-candy-browser-top-inset-failure',
                  reason,
                );
                if (document.readyState === 'loading') return;
                const policyKey = currentPolicyKey();
                resetFailuresForPolicy(policyKey);
                const requiredFailureCount = requiredConsecutiveLayoutFailures();
                consecutiveLayoutFailures++;
                if (consecutiveLayoutFailures < requiredFailureCount) {
                  scheduleDeferredLayoutCheck(false);
                  return;
                }
                const confirmedPolicyKey = currentPolicyKey();
                if (confirmedPolicyKey !== policyKey) {
                  resetFailuresForPolicy(confirmedPolicyKey, true);
                  scheduleDeferredLayoutCheck(false);
                  return;
                }
                suspendedLayoutRecoveryKey = policyKey;
                requestNativeFallback();
              };
              const layoutRecoverySuspendedForCurrentPolicy = () => {
                return suspendedLayoutRecoveryKey === currentPolicyKey();
              };
              const resumeLayoutRecovery = () => {
                const policyKey = currentPolicyKey();
                if (suspendedLayoutRecoveryKey === policyKey) {
                  suspendedLayoutRecoveryKey = null;
                }
                resetFailuresForPolicy(policyKey, true);
                deferredLayoutChecks = 0;
              };
              const sampleAxis = (limit) => {
                const points = [];
                for (let point = 1; point < limit; point += obstructionSampleStep) {
                  points.push(point);
                }
                const trailingPoint = limit - 1;
                if (trailingPoint >= 0 && points.at(-1) !== trailingPoint) {
                  points.push(trailingPoint);
                }
                return points;
              };
              const parentElementOrShadowHost = (element) =>
                element?.parentElement || element?.getRootNode?.()?.host || null;
              const composedContains = (container, element) => {
                for (
                  let current = element;
                  current;
                  current = parentElementOrShadowHost(current)
                ) {
                  if (current === container) return true;
                }
                return false;
              };
              const cachedShadowElements = (scope) => {
                const cached = shadowHitTestCache.get(scope);
                if (cached) return cached;
                const elements = Array.from(scope.querySelectorAll?.('*') || []).map(
                  (element) => ({ element, rect: element.getBoundingClientRect() }),
                );
                shadowHitTestCache.set(scope, elements);
                if (!shadowHitTestCacheResetFrame) {
                  shadowHitTestCacheResetFrame = globalThis.requestAnimationFrame(() => {
                    shadowHitTestCacheResetFrame = 0;
                    shadowHitTestCache = new WeakMap();
                  });
                }
                return elements;
              };
              const deepElementsFromPoint = (x, y) => {
                const layers = [];
                const visitedRoots = new Set();
                let scope = document;
                while (scope && !visitedRoots.has(scope)) {
                  visitedRoots.add(scope);
                  const hitElements = typeof scope.elementsFromPoint === 'function'
                    ? Array.from(scope.elementsFromPoint(x, y))
                    : [];
                  const hasScopedHit = hitElements.some(
                    (element) => element.getRootNode?.() === scope,
                  );
                  const queriedElements = scope === document || hasScopedHit
                    ? []
                    : cachedShadowElements(scope).filter(({ rect }) => {
                      return rect.left <= x && rect.right >= x &&
                        rect.top <= y && rect.bottom >= y;
                    }).map(({ element }) => element);
                  const scopedElements = [...hitElements, ...queriedElements];
                  layers.push(scopedElements);
                  const shadowRoot = scopedElements
                    .map((element) => element.shadowRoot)
                    .find((candidate) => candidate && !visitedRoots.has(candidate));
                  if (!shadowRoot) break;
                  scope = shadowRoot;
                }
                const elements = [];
                const visitedElements = new Set();
                layers.reverse().forEach((layer) => layer.forEach((element) => {
                  if (!visitedElements.has(element)) {
                    visitedElements.add(element);
                    elements.push(element);
                  }
                }));
                return elements;
              };
              const isVisiblePositionedElement = (element) => {
                const style = getComputedStyle(element);
                const rect = element.getBoundingClientRect();
                return style.display !== 'none' &&
                  style.visibility !== 'hidden' &&
                  style.visibility !== 'collapse' &&
                  Number.parseFloat(style.opacity) > 0.01 &&
                  rect.width > 1 && rect.height > 1;
              };
              const findPositionedCandidate = (element, root, fixedOnly) => {
                let absoluteCandidate = null;
                for (
                  let current = element;
                  current && current !== root;
                  current = parentElementOrShadowHost(current)
                ) {
                  const position = getComputedStyle(current).position;
                  if (
                    (position === 'fixed' || position === 'sticky') &&
                    isVisiblePositionedElement(current)
                  ) {
                    return current;
                  }
                  if (
                    !fixedOnly &&
                    position === 'absolute' &&
                    isVisiblePositionedElement(current)
                  ) {
                    absoluteCandidate = current;
                  }
                }
                return absoluteCandidate;
              };
              const refreshOwnedStickyElements = (cssPixels) => {
                for (const element of new Set([
                  ...ownedStickyElements,
                  ...document.querySelectorAll(stickySelector),
                ])) {
                  if (!element.isConnected) {
                    clearOwnedSticky(element);
                    continue;
                  }
                  const style = getComputedStyle(element);
                  const originalTop = Number.parseFloat(
                    element.style.getPropertyValue(stickyOriginalTopProperty),
                  );
                  if (
                    style.position !== 'sticky' ||
                    !Number.isFinite(originalTop) ||
                    originalTop < -0.5
                  ) {
                    clearOwnedSticky(element);
                    continue;
                  }
                  applyStickyTopAnchor(element, originalTop, cssPixels);
                }
              };
              const stickyScrollportTop = (element, root) => {
                for (
                  let current = parentElementOrShadowHost(element);
                  current && current !== root;
                  current = parentElementOrShadowHost(current)
                ) {
                  const style = getComputedStyle(current);
                  if (['auto', 'scroll', 'hidden', 'overlay'].includes(style.overflowY)) {
                    return Math.max(
                      0,
                      current.getBoundingClientRect().top +
                        (Number.parseFloat(style.borderTopWidth) || 0),
                    );
                  }
                }
                return 0;
              };
              const applyStickyTopAnchor = (element, originalTop, cssPixels) => {
                const scrollportTop = stickyScrollportTop(
                  element,
                  document.documentElement,
                );
                const anchoredTop = Math.max(originalTop, cssPixels - scrollportTop);
                if (anchoredTop <= originalTop + 0.5) {
                  clearOwnedSticky(element);
                  return;
                }
                element.style.setProperty(
                  stickyOriginalTopProperty,
                  `${'$'}{originalTop}px`,
                  'important',
                );
                element.style.setProperty(
                  stickyTopProperty,
                  `${'$'}{anchoredTop}px`,
                  'important',
                );
                element.setAttribute(stickyAttribute, 'true');
                ownedStickyElements.add(element);
              };
              const protectStickyTopAnchors = (root, cssPixels, refreshOwned = true) => {
                if (refreshOwned) refreshOwnedStickyElements(cssPixels);
                const activeOwnedHeader = Array.from(ownedStickyElements).some((element) => {
                  if (!element.isConnected) return false;
                  const rect = element.getBoundingClientRect();
                  return rect.width >= globalThis.innerWidth * 0.8 &&
                    rect.top >= cssPixels - 0.5 && rect.top <= cssPixels + 0.5 &&
                    rect.bottom > cssPixels + 0.5;
                });
                if (activeOwnedHeader) return;
                const sampleY = [
                  ...sampleAxis(cssPixels),
                  Math.min(globalThis.innerHeight - 1, cssPixels + 1),
                ];
                const sampleX = [
                  1,
                  globalThis.innerWidth * 0.25,
                  globalThis.innerWidth * 0.5,
                  globalThis.innerWidth * 0.75,
                  globalThis.innerWidth - 1,
                ].filter((value) => value >= 0 && value < globalThis.innerWidth);
                const candidates = new Set();
                for (const y of sampleY) {
                  for (const x of sampleX) {
                    for (const element of deepElementsFromPoint(x, y)) {
                      const candidate = findPositionedCandidate(element, root, false);
                      if (candidate && getComputedStyle(candidate).position === 'sticky') {
                        candidates.add(candidate);
                        break;
                      }
                    }
                  }
                }
                candidates.forEach((element) => {
                  if (element.getAttribute(stickyAttribute) === 'true') return;
                  const style = getComputedStyle(element);
                  const originalTop = Number.parseFloat(style.top);
                  if (
                    !Number.isFinite(originalTop) ||
                    originalTop < -0.5
                  ) {
                    return;
                  }
                  applyStickyTopAnchor(element, originalTop, cssPixels);
                });
              };
              const hasActiveTranslateMotion = (style) => {
                const durations = style.transitionDuration.split(',').map(Number.parseFloat);
                const properties = style.transitionProperty.split(',').map((value) => value.trim());
                const transitionMovesTranslate = properties.some((value, index) =>
                  (value === 'all' || value === 'translate') &&
                  (durations[index % durations.length] || 0) > 0);
                const animationDuration = style.animationDuration
                  .split(',')
                  .some((value) => Number.parseFloat(value) > 0);
                return transitionMovesTranslate ||
                  (style.animationName !== 'none' && animationDuration);
              };
              const planLocalOffset = (element, cssPixels) => {
                const style = getComputedStyle(element);
                const isOwned = element.getAttribute(offsetAttribute) === 'true';
                if (
                  (
                    style.position !== 'absolute' &&
                    style.position !== 'fixed' &&
                    style.position !== 'sticky'
                  ) ||
                  (!isOwned && style.translate !== 'none') ||
                  hasActiveTranslateMotion(style)
                ) {
                  return null;
                }
                const rect = element.getBoundingClientRect();
                const isViewportWide = rect.width >= globalThis.innerWidth * 0.8;
                const isViewportTall = rect.height >= globalThis.innerHeight * 0.8;
                if (isViewportWide && isViewportTall) {
                  return null;
                }
                const previousOffset = isOwned
                  ? Number.parseFloat(element.style.getPropertyValue(offsetProperty)) || 0
                  : 0;
                const unshiftedTop = rect.top - previousOffset +
                  (style.position === 'absolute' ? globalThis.scrollY : 0);
                const isCompactInteractive =
                  !isViewportWide &&
                  !isViewportTall &&
                  isInteractivePositionedPeer(element, style);
                const minimumTop = cssPixels + (
                  isCompactInteractive
                    ? compactControlTopPaddingCssPixels
                    : 0
                );
                const offset = Math.max(0, minimumTop - unshiftedTop);
                const isFixedPanel = style.position === 'fixed' && isViewportTall;
                const computedHeight = Number.parseFloat(style.height);
                const boxExtras = Number.isFinite(computedHeight)
                  ? Math.max(0, rect.height - computedHeight)
                  : 0;
                return {
                  element,
                  position: style.position,
                  isCompactInteractive,
                  minimumTop,
                  offset,
                  panelMaxHeight: isFixedPanel
                    ? Math.max(0, rect.height + previousOffset - offset - boxExtras)
                    : null,
                };
              };
              const applyLocalOffsetPlans = (plans) => {
                for (const plan of plans) {
                  if (plan.element.getAttribute(offsetAttribute) !== 'true') {
                    ownedTranslateStates.set(plan.element, {
                      value: plan.element.style.getPropertyValue('translate'),
                      priority: plan.element.style.getPropertyPriority('translate'),
                    });
                  }
                  plan.element.style.setProperty(
                    offsetProperty,
                    `${'$'}{plan.offset}px`,
                    'important',
                  );
                  plan.element.style.setProperty(
                    'translate',
                    `0 var(${'$'}{offsetProperty}, 0px)`,
                    'important',
                  );
                  plan.element.setAttribute(offsetAttribute, 'true');
                  ownedOffsetElements.add(plan.element);
                  if (plan.panelMaxHeight === null) {
                    plan.element.removeAttribute(panelAttribute);
                    plan.element.style.removeProperty(panelMaxHeightProperty);
                  } else {
                    plan.element.style.setProperty(
                      panelMaxHeightProperty,
                      `${'$'}{plan.panelMaxHeight}px`,
                      'important',
                    );
                    plan.element.setAttribute(panelAttribute, 'true');
                  }
                }
                return plans.every((plan) => {
                  if (plan.position === 'absolute' && globalThis.scrollY > 0) return true;
                  const rect = plan.element.getBoundingClientRect();
                  return rect.top >= plan.minimumTop - 0.5 &&
                    (plan.panelMaxHeight === null || rect.bottom <= globalThis.innerHeight + 0.5);
                });
              };
              const interactivePeerSelector = [
                'a[href]',
                'button',
                'input',
                'select',
                'textarea',
                'summary',
                '[role="button"]',
                '[role="link"]',
                '[tabindex]',
                '[contenteditable="true"]',
              ].join(',');
              const isInteractivePositionedPeer = (element, style) =>
                element.matches(interactivePeerSelector) ||
                element.querySelector(interactivePeerSelector) !== null ||
                element.hasAttribute('onclick') ||
                style.cursor === 'pointer';
              const isInteractiveAtPoint = (element, boundary) => {
                for (
                  let current = element;
                  current && composedContains(boundary, current);
                  current = parentElementOrShadowHost(current)
                ) {
                  const style = getComputedStyle(current);
                  if (
                    current.matches(interactivePeerSelector) ||
                    current.hasAttribute('onclick') ||
                    style.cursor === 'pointer'
                  ) {
                    return true;
                  }
                  if (current === boundary) return false;
                }
                return false;
              };
              const findPositionedPeer = (
                element,
                root,
                excluded,
                includeNonInteractiveAbsolute,
              ) => {
                for (
                  let current = element;
                  current && current !== root;
                  current = parentElementOrShadowHost(current)
                ) {
                  const style = getComputedStyle(current);
                  const position = style.position;
                  if (
                    position === 'absolute' &&
                    !includeNonInteractiveAbsolute &&
                    !isInteractivePositionedPeer(current, style)
                  ) {
                    continue;
                  }
                  if (
                    current !== excluded &&
                    !composedContains(excluded, current) &&
                    !composedContains(current, excluded) &&
                    (
                      position === 'absolute' ||
                      position === 'fixed' ||
                      position === 'sticky'
                    ) &&
                    isVisiblePositionedElement(current)
                  ) {
                    return current;
                  }
                }
                return null;
              };
              const findCompactViewportWidePeer = (element, root, excluded) => {
                for (
                  let current = element;
                  current && current !== root;
                  current = parentElementOrShadowHost(current)
                ) {
                  if (
                    current === excluded ||
                    composedContains(excluded, current) ||
                    composedContains(current, excluded)
                  ) {
                    continue;
                  }
                  const style = getComputedStyle(current);
                  const rect = current.getBoundingClientRect();
                  if (
                    style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    style.visibility !== 'collapse' &&
                    Number.parseFloat(style.opacity) > 0.01 &&
                    rect.width >= globalThis.innerWidth * 0.8 &&
                    rect.height > 1 &&
                    rect.height < globalThis.innerHeight * 0.5
                  ) {
                    return current;
                  }
                }
                return null;
              };
              const isPeerBehindPlan = (planElement, peer, planRect, peerRect) => {
                const overlapLeft = Math.max(planRect.left, peerRect.left);
                const overlapRight = Math.min(planRect.right, peerRect.right);
                const overlapTop = Math.max(planRect.top, peerRect.top);
                const overlapBottom = Math.min(planRect.bottom, peerRect.bottom);
                if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) return false;
                const stack = deepElementsFromPoint(
                  (overlapLeft + overlapRight) / 2,
                  (overlapTop + overlapBottom) / 2,
                );
                const planIndex = stack.findIndex((element) =>
                  element === planElement || composedContains(planElement, element));
                const peerIndex = stack.findIndex((element) =>
                  element === peer || composedContains(peer, element));
                return planIndex >= 0 && peerIndex > planIndex;
              };
              const hasPositionedPeerCollision = (plans, root, projected) => {
                return plans.some((plan) => {
                  const currentRect = plan.element.getBoundingClientRect();
                  const projectionOffset = projected ? plan.offset : 0;
                  const rect = {
                    left: currentRect.left,
                    right: currentRect.right,
                    top: currentRect.top + projectionOffset,
                    bottom: currentRect.bottom + projectionOffset,
                  };
                  const xCoordinates = [
                    Math.max(1, rect.left + 1),
                    Math.max(1, (rect.left + rect.right) / 2),
                    Math.min(globalThis.innerWidth - 1, rect.right - 1),
                  ].filter((value) => value >= 0 && value < globalThis.innerWidth);
                  const yCoordinates = [
                    Math.max(1, rect.top + 1),
                    Math.max(1, (rect.top + rect.bottom) / 2),
                    Math.max(1, rect.bottom - 1),
                  ].filter((value) => value < globalThis.innerHeight);
                  return xCoordinates.some((x) => yCoordinates.some((y) =>
                    deepElementsFromPoint(x, y).some((element) => {
                      if (
                        element === plan.element ||
                        composedContains(plan.element, element) ||
                        composedContains(element, plan.element)
                      ) {
                        return false;
                      }
                      const peer = findPositionedPeer(
                        element,
                        root,
                        plan.element,
                        plan.position === 'absolute',
                      ) ||
                        (plan.position === 'absolute'
                          ? findCompactViewportWidePeer(element, root, plan.element)
                          : null);
                      if (
                        !peer ||
                        peer === plan.element ||
                        composedContains(plan.element, peer) ||
                        composedContains(peer, plan.element)
                      ) {
                        return false;
                      }
                      const peerStyle = getComputedStyle(peer);
                      if (
                        peerStyle.display === 'none' ||
                        peerStyle.visibility === 'hidden' ||
                        peerStyle.visibility === 'collapse' ||
                        Number.parseFloat(peerStyle.opacity) <= 0.01
                      ) {
                        return false;
                      }
                      const peerRect = peer.getBoundingClientRect();
                      if (isPeerBehindPlan(plan.element, peer, currentRect, peerRect)) {
                        return false;
                      }
                      const peerIsInteractive = isInteractiveAtPoint(element, peer);
                      const peerIsViewportWide =
                        peerRect.width >= globalThis.innerWidth * 0.8;
                      return (
                        peerIsInteractive && !peerIsViewportWide ||
                        !plan.isCompactInteractive && peerIsViewportWide
                      ) &&
                        rect.right > peerRect.left && rect.left < peerRect.right &&
                        rect.bottom > peerRect.top + 0.5 && rect.top < peerRect.bottom - 0.5;
                    })
                  ));
                });
              };
              const refreshOwnedOffsets = (cssPixels) => {
                const plans = [];
                for (const element of new Set([
                  ...ownedOffsetElements,
                  ...document.querySelectorAll(offsetSelector),
                ])) {
                  if (!element.isConnected) {
                    clearOwnedOffset(element);
                    continue;
                  }
                  const style = getComputedStyle(element);
                  if (!isVisiblePositionedElement(element)) {
                    // Keep an established offset while a site temporarily hides its header.
                    // Scroll events do not reconcile, so transient scroll motion stays untouched.
                    continue;
                  }
                  if (
                    style.position !== 'absolute' &&
                    style.position !== 'fixed' &&
                    style.position !== 'sticky'
                  ) {
                    clearOwnedOffset(element);
                    continue;
                  }
                  plans.push(planLocalOffset(element, cssPixels));
                }
                return plans.every(Boolean) &&
                  applyLocalOffsetPlans(plans);
              };
              const isBackdrop = (element, candidates) => {
                const style = getComputedStyle(element);
                if (style.position !== 'fixed') return false;
                const rect = element.getBoundingClientRect();
                if (
                  rect.width < globalThis.innerWidth * 0.8 ||
                  rect.height < globalThis.innerHeight * 0.8 ||
                  element.childElementCount > 0 || element.shadowRoot ||
                  (element.textContent || '').trim()
                ) {
                  return false;
                }
                const zIndex = Number.parseFloat(style.zIndex);
                if (!Number.isFinite(zIndex)) return false;
                return candidates.some((candidate) => {
                  if (candidate === element) return false;
                  const candidateStyle = getComputedStyle(candidate);
                  if (
                    candidateStyle.position !== 'fixed' ||
                    candidateStyle.display === 'none' ||
                    candidateStyle.visibility === 'hidden' ||
                    candidateStyle.visibility === 'collapse'
                  ) {
                    return false;
                  }
                  const candidateRect = candidate.getBoundingClientRect();
                  const candidateZIndex = Number.parseFloat(candidateStyle.zIndex);
                  return candidateRect.width < globalThis.innerWidth * 0.8 &&
                    candidateRect.height >= globalThis.innerHeight * 0.8 &&
                    candidateRect.right > 0 && candidateRect.left < globalThis.innerWidth &&
                    candidateRect.bottom > 0 && candidateRect.top < globalThis.innerHeight &&
                    Number.isFinite(candidateZIndex) && candidateZIndex > zIndex;
                });
              };
              const protectTopInset = (
                root,
                body,
                style,
                cssPixels,
                fixedOnly,
                preserveOwnedOffsets = false,
              ) => {
                const ignored = (element) =>
                  !element || element === root || element === body || element === style ||
                  element.matches?.(flowTargetSelector) || element.tagName === 'HEAD';
                for (let pass = 0; pass < 3; pass++) {
                  const candidates = new Set();
                  const plannedCandidates = new Map();
                  for (const y of sampleAxis(cssPixels)) {
                    for (const x of sampleAxis(globalThis.innerWidth)) {
                      for (const element of deepElementsFromPoint(x, y)) {
                        if (ignored(element)) continue;
                        const candidate = findPositionedCandidate(element, root, fixedOnly);
                        if (!candidate) {
                          if (fixedOnly) continue;
                          return false;
                        }
                        if (
                          preserveOwnedOffsets &&
                          candidate.getAttribute(offsetAttribute) === 'true'
                        ) {
                          break;
                        }
                        const plan = planLocalOffset(candidate, cssPixels);
                        if (!plan) {
                          const candidateRect = candidate.getBoundingClientRect();
                          const coversViewport =
                            candidateRect.width >= globalThis.innerWidth * 0.8 &&
                            candidateRect.height >= globalThis.innerHeight * 0.8;
                          if (!coversViewport) candidates.add(candidate);
                          continue;
                        }
                        candidates.add(candidate);
                        plannedCandidates.set(candidate, plan);
                        break;
                      }
                    }
                  }
                  if (plannedCandidates.size === 0) return candidates.size === 0;
                  const candidateList = Array.from(candidates);
                  const backdropPeers = Array.from(new Set([
                    ...candidateList,
                    ...document.querySelectorAll(`[${'$'}{panelAttribute}="true"]`),
                  ]));
                  const plans = Array.from(plannedCandidates.values())
                    .filter((plan) => !isBackdrop(plan.element, backdropPeers));
                  if (plans.length === 0) return true;
                  if (hasPositionedPeerCollision(plans, root, true)) {
                    localOffsetCollisionDetected = true;
                    return false;
                  }
                  if (!applyLocalOffsetPlans(plans)) return false;
                }
                return false;
              };
              const findFlowTarget = (element, root, body) => {
                let target = element;
                for (let current = element; current && current !== root; current = current.parentElement) {
                  const position = getComputedStyle(current).position;
                  if (position === 'absolute' || position === 'fixed' || position === 'sticky') {
                    return null;
                  }
                  target = current;
                  if (current.parentElement === body) return current;
                }
                return target === body ? null : target;
              };
              const installTargetedFlowInset = (root, body, style, cssPixels) => {
                if (!body) return false;
                let target = null;
                for (const y of sampleAxis(cssPixels)) {
                  for (const x of sampleAxis(globalThis.innerWidth)) {
                    const element = document.elementFromPoint(x, y);
                    if (
                      !element || element === root || element === body || element === style ||
                      element.tagName === 'HEAD' ||
                      findPositionedCandidate(element, root, false)
                    ) {
                      continue;
                    }
                    const currentTarget = findFlowTarget(element, root, body);
                    if (!currentTarget || target && currentTarget !== target) return false;
                    target = currentTarget;
                  }
                }
                if (!target) return false;
                clearOwnedFlowTarget(root);
                root.setAttribute(flowRootAttribute, 'true');
                const targetStyle = getComputedStyle(target);
                const originalMargin = targetStyle.marginTop;
                const originalMarginPixels = Number.parseFloat(originalMargin);
                if (!Number.isFinite(originalMarginPixels) || !originalMargin.endsWith('px')) {
                  clearOwnedFlowTarget(root);
                  return false;
                }
                const requiredOffset = Math.max(
                  0,
                  cssPixels - target.getBoundingClientRect().top,
                );
                target.style.setProperty(flowMarginProperty, originalMargin, 'important');
                target.style.setProperty(flowOffsetProperty, `${'$'}{requiredOffset}px`, 'important');
                target.setAttribute(flowTargetAttribute, 'true');
                return target.getBoundingClientRect().top >= cssPixels - 0.5;
              };
              const scheduleDeferredLayoutCheck = (resetFailures = true) => {
                if (
                  document.readyState === 'loading' ||
                  layoutRecoverySuspendedForCurrentPolicy() ||
                  deferredLayoutChecks >= maxDeferredLayoutChecks
                ) {
                  return;
                }
                if (resetFailures) consecutiveLayoutFailures = 0;
                if (deferredLayoutCheckTimer) {
                  globalThis.clearTimeout(deferredLayoutCheckTimer);
                }
                deferredLayoutCheckTimer = globalThis.setTimeout(() => {
                  deferredLayoutCheckTimer = 0;
                  deferredLayoutChecks++;
                  reconcile();
                }, layoutQuietPeriodMs());
              };
              const scheduleImmediateLayoutCheck = () => {
                if (layoutRecoverySuspendedForCurrentPolicy()) return;
                if (immediateLayoutCheckFrame) {
                  globalThis.cancelAnimationFrame(immediateLayoutCheckFrame);
                }
                immediateLayoutCheckFrame = globalThis.requestAnimationFrame(() => {
                  reconcile(false);
                  immediateLayoutCheckFrame = globalThis.requestAnimationFrame(() => {
                    immediateLayoutCheckFrame = 0;
                    reconcile(false);
                  });
                });
              };
              const protectLateTopInset = () => {
                const root = document.documentElement;
                const body = document.body;
                const style = document.querySelector(ownedSelector);
                const physicalPixels = Number(
                  globalThis.$bridgeName?.topInsetPx?.(),
                ) || 0;
                if (!root || !body || !style || physicalPixels <= 0) return;
                const density = Number(globalThis.devicePixelRatio) || 1;
                localOffsetCollisionDetected = false;
                const protectedTop = protectTopInset(
                  root,
                  body,
                  style,
                  physicalPixels / density,
                  true,
                  true,
                );
                if (protectedTop) {
                  consecutiveLayoutFailures = 0;
                } else {
                  scheduleDeferredLayoutCheck(false);
                }
              };
              const verifyLateTopInset = () => {
                scrollVerificationTimer = 0;
                const root = document.documentElement;
                const body = document.body;
                const style = document.querySelector(ownedSelector);
                const physicalPixels = Number(
                  globalThis.$bridgeName?.topInsetPx?.(),
                ) || 0;
                if (!root || !body || !style || physicalPixels <= 0) return;
                const density = Number(globalThis.devicePixelRatio) || 1;
                const cssPixels = physicalPixels / density;
                for (const y of sampleAxis(cssPixels)) {
                  for (const x of sampleAxis(globalThis.innerWidth)) {
                    for (const element of deepElementsFromPoint(x, y)) {
                      if (
                        !element || element === root || element === body || element === style ||
                        element.tagName === 'HEAD'
                      ) {
                        continue;
                      }
                      const candidate = findPositionedCandidate(element, root, true);
                      if (!candidate) continue;
                      const candidateRect = candidate.getBoundingClientRect();
                      const coversViewport =
                        candidateRect.width >= globalThis.innerWidth * 0.8 &&
                        candidateRect.height >= globalThis.innerHeight * 0.8;
                      if (coversViewport) continue;
                      const policyKey = currentPolicyKey();
                      if (scrollVerificationPolicyKey !== policyKey) {
                        scrollVerificationPolicyKey = policyKey;
                        scrollVerificationFailures = 0;
                      }
                      scrollVerificationFailures++;
                      if (
                        scrollVerificationFailures >= requiredConsecutiveLayoutFailures()
                      ) {
                        requestNativeFallback();
                      } else {
                        scrollVerificationTimer = globalThis.setTimeout(
                          verifyLateTopInset,
                          layoutQuietPeriodMs(),
                        );
                      }
                      return;
                    }
                  }
                }
                scrollVerificationFailures = 0;
                scrollVerificationPolicyKey = currentPolicyKey();
              };
              const windowScrollListener = () => {
                scrollVerificationFailures = 0;
                scrollVerificationPolicyKey = currentPolicyKey();
                if (scrollVerificationTimer) {
                  globalThis.clearTimeout(scrollVerificationTimer);
                }
                if (scrollLayoutCheckFrame) {
                  globalThis.cancelAnimationFrame(scrollLayoutCheckFrame);
                  scrollLayoutCheckFrame = 0;
                }
                scrollLayoutCheckFrame = globalThis.requestAnimationFrame(() => {
                  scrollLayoutCheckFrame = 0;
                  const root = document.documentElement;
                  const physicalPixels = Number(
                    globalThis.$bridgeName?.topInsetPx?.(),
                  ) || 0;
                  if (!root || physicalPixels <= 0) return;
                  const density = Number(globalThis.devicePixelRatio) || 1;
                  protectStickyTopAnchors(root, physicalPixels / density, false);
                });
                scrollVerificationTimer = globalThis.setTimeout(
                  () => {
                    scrollVerificationTimer = 0;
                    scrollLayoutCheckFrame = globalThis.requestAnimationFrame(() => {
                      scrollLayoutCheckFrame = 0;
                      protectLateTopInset();
                      scrollVerificationTimer = globalThis.setTimeout(
                        verifyLateTopInset,
                        layoutQuietPeriodMs(),
                      );
                    });
                  },
                  layoutQuietPeriodMs(),
                );
              };
              const protectInteractionTarget = (event) => {
                const root = document.documentElement;
                const target = event?.target;
                if (!root || !(target instanceof Element)) return;
                const physicalPixels =
                  Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                if (physicalPixels <= 0) return;
                const candidate = findPositionedCandidate(target, root, false);
                if (!candidate) return;
                const density = Number(globalThis.devicePixelRatio) || 1;
                const plan = planLocalOffset(candidate, physicalPixels / density);
                if (plan) applyLocalOffsetPlans([plan]);
              };
              const protectFocusedContainer = (root, cssPixels) => {
                const target = document.activeElement;
                if (!(target instanceof Element) || target === document.body) return;
                const candidate = findPositionedCandidate(target, root, false);
                if (!candidate) return;
                const plan = planLocalOffset(candidate, cssPixels);
                if (plan) applyLocalOffsetPlans([plan]);
              };
              const scheduleInteractionLayoutCheck = () => {
                resumeLayoutRecovery();
                scheduleDeferredLayoutCheck(true);
              };
              const scheduleImmediateInteractionLayoutCheck = (event) => {
                protectInteractionTarget(event);
                resumeLayoutRecovery();
                scheduleImmediateLayoutCheck();
                scheduleDeferredLayoutCheck(true);
              };
              const reconfigure = () => {
                if (deferredLayoutCheckTimer) {
                  globalThis.clearTimeout(deferredLayoutCheckTimer);
                  deferredLayoutCheckTimer = 0;
                }
                resetFailuresForPolicy(currentPolicyKey(), true);
                reconcile();
              };
              const isTransparentColor = (color) =>
                !color || color === 'transparent' ||
                (color.startsWith('rgba(') &&
                  Number.parseFloat(color.slice(color.lastIndexOf(',') + 1)) === 0);
              const paintedBackground = (style) => {
                const image = style.backgroundImage;
                if (image && image !== 'none' && !image.includes('url(')) {
                  return style.background;
                }
                if (!isTransparentColor(style.backgroundColor)) {
                  return style.backgroundColor;
                }
                return null;
              };
              const canvasBackground = (root) => {
                const bodyBackground = document.body
                  ? paintedBackground(getComputedStyle(document.body))
                  : null;
                if (bodyBackground) return bodyBackground;
                return paintedBackground(getComputedStyle(root)) || 'transparent';
              };
              const activeThemeColor = () => {
                const candidates = document.querySelectorAll('meta[name="theme-color"]');
                for (const candidate of candidates) {
                  const media = candidate.getAttribute('media');
                  const color = candidate.getAttribute('content')?.trim();
                  if (
                    color &&
                    (!media || globalThis.matchMedia?.(media).matches) &&
                    globalThis.CSS?.supports?.('color', color)
                  ) {
                    return color;
                  }
                }
                return null;
              };
              const topContentBackground = (root, cssPixels) => {
                const viewportWidth = globalThis.innerWidth;
                const viewportHeight = globalThis.innerHeight;
                if (viewportWidth <= 0 || viewportHeight <= 1) {
                  return activeThemeColor() || canvasBackground(root);
                }
                const sampleY = Math.min(
                  viewportHeight - 1,
                  Math.max(1, cssPixels + 1),
                );
                const sampleX = [
                  Math.max(1, viewportWidth / 2),
                  1,
                  Math.max(1, viewportWidth - 1),
                ];
                for (const x of sampleX) {
                  const visited = new Set();
                  for (const hit of document.elementsFromPoint(x, sampleY)) {
                    for (
                      let element = hit;
                      element && !visited.has(element);
                      element = element.parentElement
                    ) {
                      visited.add(element);
                      const background = paintedBackground(getComputedStyle(element));
                      if (background) return background;
                      if (element === root) break;
                    }
                  }
                }
                return activeThemeColor() || canvasBackground(root);
              };
              const reconcile = (allowRecoverySuspension = true) => {
                const root = document.documentElement;
                if (!root) return;
                const physicalPixels =
                  Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                if (physicalPixels <= 0) {
                  consecutiveLayoutFailures = 0;
                  root.removeAttribute('data-candy-browser-top-inset-failure');
                  clearOwnedOffsets();
                  clearOwnedStickyElements();
                  clearOwnedFlowTarget(root);
                  document.querySelector(ownedSelector)?.remove();
                  root.style.removeProperty(property);
                  root.style.removeProperty(backgroundProperty);
                  return;
                }
                let style = document.querySelector(ownedSelector);
                if (!style) {
                  style = document.createElement('style');
                  style.id = styleId;
                  style.dataset.candyBrowserOwned = 'true';
                  style.textContent = `
                    html::before {
                      content: '' !important;
                      display: block !important;
                      height: var(${'$'}{property}, 0px) !important;
                      min-height: var(${'$'}{property}, 0px) !important;
                      background: var(${'$'}{backgroundProperty}, transparent) !important;
                      pointer-events: none !important;
                      visibility: visible !important;
                    }
                    html[${'$'}{flowRootAttribute}="true"]::before {
                      height: 0 !important;
                      min-height: 0 !important;
                    }
                    [${'$'}{flowTargetAttribute}="true"] {
                      margin-top: calc(
                        var(${'$'}{flowMarginProperty}, 0px) +
                        var(${'$'}{flowOffsetProperty}, 0px)
                      ) !important;
                    }
                    [${'$'}{stickyAttribute}="true"] {
                      top: var(${'$'}{stickyTopProperty}, 0px) !important;
                    }
                    [${'$'}{panelAttribute}="true"] {
                      max-height: var(${'$'}{panelMaxHeightProperty}) !important;
                    }
                  `;
                  root.appendChild(style);
                }
                const density = Number(globalThis.devicePixelRatio) || 1;
                const cssPixels = physicalPixels / density;
                const propertyValue = `${'$'}{cssPixels}px`;
                if (
                  root.style.getPropertyValue(property) !== propertyValue ||
                  root.style.getPropertyPriority(property) !== 'important'
                ) {
                  root.style.setProperty(property, propertyValue, 'important');
                }
                const topBackground = topContentBackground(root, cssPixels);
                if (
                  root.style.getPropertyValue(backgroundProperty) !== topBackground ||
                  root.style.getPropertyPriority(backgroundProperty) !== 'important'
                ) {
                  root.style.setProperty(backgroundProperty, topBackground, 'important');
                }
                protectFocusedContainer(root, cssPixels);
                protectStickyTopAnchors(root, cssPixels);
                let activeFlowTarget = document.querySelector(flowTargetSelector);
                if (
                  root.getAttribute(flowRootAttribute) === 'true' &&
                  !activeFlowTarget
                ) {
                  clearOwnedFlowTarget(root);
                  activeFlowTarget = null;
                }
                const usesTargetedFlowInset = Boolean(activeFlowTarget) &&
                  root.getAttribute(flowRootAttribute) === 'true';
                const expectedPixels = usesTargetedFlowInset && activeFlowTarget
                  ? Number.parseFloat(
                    activeFlowTarget.style.getPropertyValue(flowMarginProperty),
                  ) + Number.parseFloat(
                    activeFlowTarget.style.getPropertyValue(flowOffsetProperty),
                  )
                  : cssPixels;
                const appliedPixels = usesTargetedFlowInset && activeFlowTarget
                  ? Number.parseFloat(getComputedStyle(activeFlowTarget).marginTop)
                  : Number.parseFloat(getComputedStyle(root, '::before').height);
                if (
                  !Number.isFinite(appliedPixels) || !Number.isFinite(expectedPixels) ||
                  Math.abs(appliedPixels - expectedPixels) > 0.5
                ) {
                  if (allowRecoverySuspension) suspendLayoutRecovery('flow-inset-mismatch');
                  return;
                }
                if (!refreshOwnedOffsets(cssPixels)) {
                  if (allowRecoverySuspension) suspendLayoutRecovery('owned-offset-refresh');
                  return;
                }
                if (document.readyState !== 'loading') {
                  localOffsetCollisionDetected = false;
                  const body = document.body;
                  const isAtDocumentTop = globalThis.scrollY <= 0;
                  let topInsetProtected = protectTopInset(
                    root,
                    body,
                    style,
                    cssPixels,
                    !isAtDocumentTop,
                  );
                  if (
                    !topInsetProtected && !localOffsetCollisionDetected && isAtDocumentTop &&
                    installTargetedFlowInset(root, body, style, cssPixels)
                  ) {
                    topInsetProtected =
                      refreshOwnedOffsets(cssPixels) &&
                      protectTopInset(root, body, style, cssPixels, false);
                  }
                  if (!topInsetProtected) {
                    if (allowRecoverySuspension) suspendLayoutRecovery(
                      localOffsetCollisionDetected ? 'positioned-peer-collision' : 'top-obstruction',
                    );
                    return;
                  }
                }
                consecutiveLayoutFailures = 0;
                root.removeAttribute('data-candy-browser-top-inset-failure');
              };
              const styleWithoutCandyProperties = (value) => (value || '')
                .replace(/--candy-browser-[^:;]+\s*:\s*[^;]*(?:;|${'$'})/gi, '')
                .replace(
                  /translate\s*:\s*0\s+var\(--candy-browser-owned-top-inset-offset[^;]*(?:;|${'$'})/gi,
                  '',
                )
                .replace(/\s+/g, ' ')
                .trim();
              const isRelevantLayoutMutation = (record) => {
                if (record.addedNodes?.length > 0 || record.removedNodes?.length > 0) {
                  return true;
                }
                if (record.type !== 'attributes') return false;
                if (
                  record.attributeName === 'content' &&
                  record.target?.matches?.('meta[name="viewport"]')
                ) {
                  return true;
                }
                if (record.attributeName === 'class') return true;
                return record.attributeName === 'style' &&
                  styleWithoutCandyProperties(record.oldValue) !==
                    styleWithoutCandyProperties(record.target?.getAttribute?.('style'));
              };
              const start = () => {
                const root = document.documentElement;
                if (!root) return;
                const previousState = globalThis[stateKey];
                if (typeof previousState?.dispose === 'function') {
                  previousState.dispose();
                } else {
                  previousState?.observer?.disconnect();
                  globalThis.clearTimeout(previousState?.interactionLayoutCheckTimer);
                  previousState?.stabilizationCheckTimers?.forEach(globalThis.clearTimeout);
                  previousState?.interactionEvents?.forEach((eventName) => {
                    document.removeEventListener(
                      eventName,
                      previousState.interactionListener,
                      true,
                    );
                  });
                  if (previousState?.windowResizeListener) {
                    globalThis.removeEventListener(
                      'resize',
                      previousState.windowResizeListener,
                    );
                  }
                  if (previousState?.windowScrollListener) {
                    globalThis.removeEventListener(
                      'scroll',
                      previousState.windowScrollListener,
                    );
                    document.removeEventListener(
                      'scroll',
                      previousState.windowScrollListener,
                      true,
                    );
                  }
                }
                const observerOptions = {
                  attributeFilter: ['class', 'content', 'style'],
                  attributeOldValue: true,
                  attributes: true,
                  childList: true,
                  subtree: true,
                };
                const observedShadowRoots = new WeakSet();
                let observer = null;
                const observeOpenShadowRoots = (scope) => {
                  const elements = [
                    ...(scope instanceof Element ? [scope] : []),
                    ...Array.from(scope.querySelectorAll?.('*') || []),
                  ];
                  elements.forEach((element) => {
                    const shadowRoot = element.shadowRoot;
                    if (!shadowRoot || observedShadowRoots.has(shadowRoot)) return;
                    observedShadowRoots.add(shadowRoot);
                    observer.observe(shadowRoot, observerOptions);
                    observeOpenShadowRoots(shadowRoot);
                  });
                };
                observer = new MutationObserver((records) => {
                  records.forEach((record) => {
                    record.addedNodes?.forEach((node) => {
                      if (node instanceof Element) observeOpenShadowRoots(node);
                    });
                  });
                  if (records.some(isRelevantLayoutMutation)) {
                    resumeLayoutRecovery();
                    scheduleImmediateLayoutCheck();
                    scheduleDeferredLayoutCheck(true);
                  }
                });
                observer.observe(root, observerOptions);
                observeOpenShadowRoots(root);
                const interactionEvents = [
                  'click',
                  'change',
                  'keydown',
                  'pointerup',
                ];
                const immediateInteractionEvents = ['focusin', 'compositionend', 'input'];
                interactionEvents.forEach((eventName) => {
                  document.addEventListener(
                    eventName,
                    scheduleInteractionLayoutCheck,
                    true,
                  );
                });
                immediateInteractionEvents.forEach((eventName) => {
                  document.addEventListener(
                    eventName,
                    scheduleImmediateInteractionLayoutCheck,
                    true,
                  );
                });
                const windowResizeListener = () => {
                  resumeLayoutRecovery();
                  scheduleImmediateLayoutCheck();
                  scheduleDeferredLayoutCheck(true);
                };
                globalThis.addEventListener('resize', windowResizeListener);
                globalThis.addEventListener('scroll', windowScrollListener, { passive: true });
                document.addEventListener('scroll', windowScrollListener, {
                  capture: true,
                  passive: true,
                });
                const domContentLoadedListener = () => scheduleDeferredLayoutCheck(true);
                const windowLoadListener = () => scheduleDeferredLayoutCheck(true);
                const runtimeState = {
                  observer,
                  interactionEvents,
                  interactionListener: scheduleInteractionLayoutCheck,
                  immediateInteractionEvents,
                  immediateInteractionListener: scheduleImmediateInteractionLayoutCheck,
                  windowResizeListener,
                  windowScrollListener,
                  domContentLoadedListener,
                  windowLoadListener,
                  ownedOffsetElements,
                  ownedTranslateStates,
                  ownedStickyElements,
                  stabilizationCheckTimers: [],
                  dispose: null,
                };
                runtimeState.dispose = () => {
                  observer.disconnect();
                  globalThis.clearTimeout(deferredLayoutCheckTimer);
                  deferredLayoutCheckTimer = 0;
                  globalThis.cancelAnimationFrame(immediateLayoutCheckFrame);
                  immediateLayoutCheckFrame = 0;
                  globalThis.cancelAnimationFrame(scrollLayoutCheckFrame);
                  scrollLayoutCheckFrame = 0;
                  globalThis.cancelAnimationFrame(shadowHitTestCacheResetFrame);
                  shadowHitTestCacheResetFrame = 0;
                  shadowHitTestCache = new WeakMap();
                  globalThis.clearTimeout(scrollVerificationTimer);
                  scrollVerificationTimer = 0;
                  runtimeState.stabilizationCheckTimers.forEach(globalThis.clearTimeout);
                  interactionEvents.forEach((eventName) => {
                    document.removeEventListener(
                      eventName,
                      scheduleInteractionLayoutCheck,
                      true,
                    );
                  });
                  immediateInteractionEvents.forEach((eventName) => {
                    document.removeEventListener(
                      eventName,
                      scheduleImmediateInteractionLayoutCheck,
                      true,
                    );
                  });
                  globalThis.removeEventListener('resize', windowResizeListener);
                  globalThis.removeEventListener('scroll', windowScrollListener);
                  document.removeEventListener('scroll', windowScrollListener, true);
                  document.removeEventListener(
                    'DOMContentLoaded',
                    domContentLoadedListener,
                  );
                  globalThis.removeEventListener('load', windowLoadListener);
                };
                runtimeState.stabilizationCheckTimers = stabilizationCheckDelaysMs.map((delayMs) =>
                    globalThis.setTimeout(() => {
                      deferredLayoutChecks = 0;
                      scheduleDeferredLayoutCheck(false);
                    }, delayMs));
                globalThis[stateKey] = runtimeState;
                reconcile();
                if (document.readyState === 'loading') {
                  document.addEventListener(
                    'DOMContentLoaded',
                    domContentLoadedListener,
                    { once: true },
                  );
                  globalThis.addEventListener(
                    'load',
                    windowLoadListener,
                    { once: true },
                  );
                }
              };
              globalThis.__candyReconcileContentTopInset = reconcile;
              globalThis.__candyReconfigureContentTopInset = reconfigure;
              if (document.documentElement) {
                start();
              } else {
                const documentElementObserver = new MutationObserver(() => {
                  if (!document.documentElement) return;
                  documentElementObserver.disconnect();
                  start();
                });
                documentElementObserver.observe(document, { childList: true });
              }
            })();
        """.trimIndent()
}
