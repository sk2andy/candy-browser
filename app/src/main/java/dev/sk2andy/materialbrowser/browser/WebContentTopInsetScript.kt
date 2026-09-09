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
              const delayedInteractionCheckMs = 350;
              const stabilizationCheckDelaysMs = [250, 1000, 2500, 5000];
              let deferredLayoutChecks = 0;
              let deferredLayoutCheckTimer = 0;
              let nativeFallbackRequestKey = null;
              let localOffsetCollisionDetected = false;
              const clearOwnedOffsets = () => {
                document.querySelectorAll(offsetSelector).forEach((element) => {
                  element.removeAttribute(offsetAttribute);
                  element.removeAttribute(panelAttribute);
                  element.style.removeProperty(offsetProperty);
                  element.style.removeProperty(panelMaxHeightProperty);
                });
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
                const generation = Number(
                  globalThis.$bridgeName?.navigationGeneration?.(),
                ) || 0;
                const revision = Number(
                  globalThis.$bridgeName?.policyRevision?.(),
                ) || 0;
                const requestKey = `${'$'}{generation}:${'$'}{revision}`;
                if (nativeFallbackRequestKey === requestKey) return;
                nativeFallbackRequestKey = requestKey;
                clearOwnedOffsets();
                const root = document.documentElement;
                if (root) {
                  clearOwnedFlowTarget(root);
                  document.querySelector(ownedSelector)?.remove();
                  root.style.removeProperty(property);
                  root.style.removeProperty(backgroundProperty);
                }
                globalThis.$bridgeName?.fallbackToNative?.(generation);
              };
              const nativeFallbackRequestedForCurrentPolicy = () => {
                const generation = Number(
                  globalThis.$bridgeName?.navigationGeneration?.(),
                ) || 0;
                const revision = Number(
                  globalThis.$bridgeName?.policyRevision?.(),
                ) || 0;
                return nativeFallbackRequestKey === `${'$'}{generation}:${'$'}{revision}`;
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
                for (let current = element; current && current !== root; current = current.parentElement) {
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
                    !absoluteCandidate &&
                    isVisiblePositionedElement(current)
                  ) {
                    absoluteCandidate = current;
                  }
                }
                return absoluteCandidate;
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
                const offset = Math.max(0, cssPixels - unshiftedTop);
                const isFixedPanel = style.position === 'fixed' && isViewportTall;
                const computedHeight = Number.parseFloat(style.height);
                const boxExtras = Number.isFinite(computedHeight)
                  ? Math.max(0, rect.height - computedHeight)
                  : 0;
                return {
                  element,
                  position: style.position,
                  offset,
                  panelMaxHeight: isFixedPanel
                    ? Math.max(0, rect.height + previousOffset - offset - boxExtras)
                    : null,
                };
              };
              const applyLocalOffsetPlans = (plans, cssPixels) => {
                for (const plan of plans) {
                  plan.element.style.setProperty(
                    offsetProperty,
                    `${'$'}{plan.offset}px`,
                    'important',
                  );
                  plan.element.setAttribute(offsetAttribute, 'true');
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
                  return rect.top >= cssPixels - 0.5 &&
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
              const findPositionedPeer = (
                element,
                root,
                excluded,
                includeNonInteractiveAbsolute,
              ) => {
                for (let current = element; current && current !== root; current = current.parentElement) {
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
                    !excluded.contains(current) &&
                    !current.contains(excluded) &&
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
                for (let current = element; current && current !== root; current = current.parentElement) {
                  if (
                    current === excluded ||
                    excluded.contains(current) ||
                    current.contains(excluded)
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
              const hasPositionedPeerCollision = (plans, root, projected) => {
                const xCoordinates = [
                  1,
                  Math.max(1, globalThis.innerWidth / 2),
                  Math.max(1, globalThis.innerWidth - 1),
                ];
                return plans.some((plan) => {
                  const currentRect = plan.element.getBoundingClientRect();
                  const projectionOffset = projected ? plan.offset : 0;
                  const rect = {
                    left: currentRect.left,
                    right: currentRect.right,
                    top: currentRect.top + projectionOffset,
                    bottom: currentRect.bottom + projectionOffset,
                  };
                  const yCoordinates = [
                    Math.max(1, rect.top + 1),
                    Math.max(1, (rect.top + rect.bottom) / 2),
                    Math.max(1, rect.bottom - 1),
                  ].filter((value) => value < globalThis.innerHeight);
                  return xCoordinates.some((x) => yCoordinates.some((y) =>
                    Array.from(document.elementsFromPoint(x, y)).some((element) => {
                      if (
                        element === plan.element ||
                        plan.element.contains(element) ||
                        element.contains(plan.element)
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
                        plan.element.contains(peer) ||
                        peer.contains(plan.element)
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
                      const peerIsInteractive = isInteractivePositionedPeer(peer, peerStyle);
                      return (peerIsInteractive || peerRect.width >= globalThis.innerWidth * 0.8) &&
                        rect.right > peerRect.left && rect.left < peerRect.right &&
                        rect.bottom > peerRect.top + 0.5 && rect.top < peerRect.bottom - 0.5;
                    })
                  ));
                });
              };
              const refreshOwnedOffsets = (cssPixels) => {
                const plans = [];
                for (const element of document.querySelectorAll(offsetSelector)) {
                  const style = getComputedStyle(element);
                  if (
                    style.display === 'none' ||
                    style.visibility === 'hidden' ||
                    style.visibility === 'collapse' ||
                    Number.parseFloat(style.opacity) <= 0.01 ||
                    (
                      style.position !== 'absolute' &&
                      style.position !== 'fixed' &&
                      style.position !== 'sticky'
                    )
                  ) {
                    element.removeAttribute(offsetAttribute);
                    element.removeAttribute(panelAttribute);
                    element.style.removeProperty(offsetProperty);
                    element.style.removeProperty(panelMaxHeightProperty);
                    continue;
                  }
                  plans.push(planLocalOffset(element, cssPixels));
                }
                return plans.every(Boolean) &&
                  applyLocalOffsetPlans(plans, cssPixels);
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
              const protectTopInset = (root, body, style, cssPixels, fixedOnly) => {
                const ignored = (element) =>
                  !element || element === root || element === body || element === style ||
                  element.matches?.(flowTargetSelector) || element.tagName === 'HEAD';
                for (let pass = 0; pass < 3; pass++) {
                  const candidates = new Set();
                  for (const y of sampleAxis(cssPixels)) {
                    for (const x of sampleAxis(globalThis.innerWidth)) {
                      const element = document.elementFromPoint(x, y);
                      if (ignored(element)) continue;
                      const candidate = findPositionedCandidate(element, root, fixedOnly);
                      if (!candidate) {
                        if (fixedOnly) continue;
                        return false;
                      }
                      candidates.add(candidate);
                    }
                  }
                  if (candidates.size === 0) return true;
                  const candidateList = Array.from(candidates);
                  const backdropPeers = Array.from(new Set([
                    ...candidateList,
                    ...document.querySelectorAll(`[${'$'}{panelAttribute}="true"]`),
                  ]));
                  const plans = candidateList
                    .filter((element) => !isBackdrop(element, backdropPeers))
                    .map((element) => planLocalOffset(element, cssPixels));
                  if (plans.length === 0) return true;
                  if (!plans.every(Boolean)) return false;
                  if (hasPositionedPeerCollision(plans, root, true)) {
                    localOffsetCollisionDetected = true;
                    return false;
                  }
                  if (!applyLocalOffsetPlans(plans, cssPixels)) return false;
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
              const scheduleDeferredLayoutCheck = () => {
                if (
                  document.readyState === 'loading' ||
                  nativeFallbackRequestedForCurrentPolicy() ||
                  deferredLayoutCheckTimer ||
                  deferredLayoutChecks >= maxDeferredLayoutChecks
                ) {
                  return;
                }
                deferredLayoutCheckTimer = globalThis.setTimeout(() => {
                  deferredLayoutCheckTimer = 0;
                  deferredLayoutChecks++;
                  reconcile();
                }, 50);
              };
              const scheduleInteractionLayoutCheck = () => {
                deferredLayoutChecks = 0;
                scheduleDeferredLayoutCheck();
                const state = globalThis[stateKey];
                if (!state) return;
                globalThis.clearTimeout(state.interactionLayoutCheckTimer);
                state.interactionLayoutCheckTimer = globalThis.setTimeout(() => {
                  state.interactionLayoutCheckTimer = 0;
                  deferredLayoutChecks = 0;
                  scheduleDeferredLayoutCheck();
                }, delayedInteractionCheckMs);
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
              const reconcile = () => {
                const root = document.documentElement;
                if (!root) return;
                const physicalPixels =
                  Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                if (physicalPixels <= 0) {
                  clearOwnedOffsets();
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
                    [${'$'}{offsetAttribute}="true"] {
                      translate: 0 var(${'$'}{offsetProperty}, 0px) !important;
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
                  requestNativeFallback();
                  return;
                }
                if (!refreshOwnedOffsets(cssPixels)) {
                  requestNativeFallback();
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
                    requestNativeFallback();
                  }
                }
              };
              const start = () => {
                const root = document.documentElement;
                if (!root) return;
                const previousState = globalThis[stateKey];
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
                if (previousState?.windowScrollListener) {
                  globalThis.removeEventListener(
                    'scroll',
                    previousState.windowScrollListener,
                    true,
                  );
                }
                const observer = new MutationObserver((records) => {
                  if (records.some((record) => record.addedNodes?.length > 0)) {
                    scheduleDeferredLayoutCheck();
                  }
                });
                observer.observe(root, {
                  childList: true,
                  subtree: true,
                });
                const interactionEvents = [
                  'click',
                  'change',
                  'focusin',
                  'keydown',
                  'pointerup',
                ];
                interactionEvents.forEach((eventName) => {
                  document.addEventListener(
                    eventName,
                    scheduleInteractionLayoutCheck,
                    true,
                  );
                });
                globalThis.addEventListener(
                  'scroll',
                  scheduleInteractionLayoutCheck,
                  true,
                );
                globalThis[stateKey] = {
                  observer,
                  interactionEvents,
                  interactionLayoutCheckTimer: 0,
                  interactionListener: scheduleInteractionLayoutCheck,
                  windowScrollListener: scheduleInteractionLayoutCheck,
                  stabilizationCheckTimers: stabilizationCheckDelaysMs.map((delayMs) =>
                    globalThis.setTimeout(() => {
                      deferredLayoutChecks = 0;
                      reconcile();
                    }, delayMs)),
                };
                reconcile();
                if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', reconcile, { once: true });
                  globalThis.addEventListener('load', reconcile, { once: true });
                }
              };
              globalThis.__candyReconcileContentTopInset = reconcile;
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
