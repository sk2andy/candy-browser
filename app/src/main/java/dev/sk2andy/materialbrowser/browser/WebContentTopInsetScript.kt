package dev.sk2andy.materialbrowser.browser

internal object WebContentTopInsetScript {
    const val bridgeName = "CandyContentTopInset"

    val installScript: String =
        """
            (() => {
              if (globalThis.top !== globalThis) return;
              const styleId = 'candy-browser-content-top-inset';
              const ownedSelector = `style#${'$'}{styleId}[data-candy-browser-owned="true"]`;
              const property = '--candy-browser-content-top-inset';
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
              let deferredLayoutChecks = 0;
              let deferredLayoutCheckTimer = 0;
              let nativeFallbackRequested = false;
              const isSeparator = (character) =>
                character === ' ' || character === '\t' || character === '\n' ||
                character === '\r' || character === '=' || character === ',' ||
                character === '\0';
              const viewportFitsCover = () => {
                let viewportFit = 'auto';
                document.querySelectorAll('meta[name]').forEach((meta) => {
                  if ((meta.getAttribute('name') || '').toLowerCase() !== 'viewport') return;
                  if (!meta.hasAttribute('content')) return;
                  const buffer = (meta.getAttribute('content') || '').toLowerCase();
                  let currentViewportFit = 'auto';
                  for (let index = 0; index < buffer.length;) {
                    while (index < buffer.length && isSeparator(buffer[index])) index++;
                    const keyStart = index;
                    while (index < buffer.length && !isSeparator(buffer[index])) index++;
                    const key = buffer.substring(keyStart, index);
                    while (index < buffer.length && buffer[index] !== '=' && buffer[index] !== ',') {
                      index++;
                    }
                    while (index < buffer.length && isSeparator(buffer[index])) {
                      if (buffer[index] === ',') break;
                      index++;
                    }
                    const valueStart = index;
                    while (index < buffer.length && !isSeparator(buffer[index])) index++;
                    if (key === 'viewport-fit') {
                      currentViewportFit = buffer.substring(valueStart, index);
                    }
                  }
                  viewportFit = currentViewportFit;
                });
                return viewportFit === 'cover';
              };
              const containsMeta = (node) =>
                node instanceof Element &&
                (node.tagName === 'META' || Boolean(node.querySelector('meta')));
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
                if (nativeFallbackRequested) return;
                nativeFallbackRequested = true;
                clearOwnedOffsets();
                document.documentElement &&
                  clearOwnedFlowTarget(document.documentElement);
                const generation = Number(
                  globalThis.$bridgeName?.navigationGeneration?.(),
                ) || 0;
                globalThis.$bridgeName?.fallbackToNative?.(generation);
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
              const findPositionedCandidate = (element, root, fixedOnly) => {
                let absoluteCandidate = null;
                for (let current = element; current && current !== root; current = current.parentElement) {
                  const position = getComputedStyle(current).position;
                  if (position === 'fixed') return current;
                  if (!fixedOnly && position === 'absolute' && !absoluteCandidate) {
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
                  (style.position !== 'absolute' && style.position !== 'fixed') ||
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
                  (style.position === 'fixed' ? 0 : globalThis.scrollY);
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
              const refreshOwnedOffsets = (cssPixels) => {
                const plans = [];
                for (const element of document.querySelectorAll(offsetSelector)) {
                  const style = getComputedStyle(element);
                  if (
                    style.display === 'none' ||
                    style.visibility === 'hidden' ||
                    style.visibility === 'collapse' ||
                    (style.position !== 'absolute' && style.position !== 'fixed')
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
                  if (!plans.every(Boolean) || !applyLocalOffsetPlans(plans, cssPixels)) {
                    return false;
                  }
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
                  nativeFallbackRequested ||
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
              const reconcile = () => {
                const root = document.documentElement;
                if (!root) return;
                const physicalPixels =
                  globalThis.$bridgeName?.viewportCoverAllowed?.() === true && viewportFitsCover()
                  ? 0
                  : Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                if (physicalPixels <= 0) {
                  clearOwnedOffsets();
                  clearOwnedFlowTarget(root);
                  document.querySelector(ownedSelector)?.remove();
                  root.style.removeProperty(property);
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
                      pointer-events: none !important;
                      visibility: hidden !important;
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
                    !topInsetProtected && isAtDocumentTop &&
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
                previousState?.interactionEvents?.forEach((eventName) => {
                  document.removeEventListener(
                    eventName,
                    previousState.interactionListener,
                    true,
                  );
                });
                const observer = new MutationObserver((records) => {
                  const viewportMayHaveChanged = records.some((record) =>
                    (record.type === 'attributes' && record.target.tagName === 'META') ||
                    Array.from(record.addedNodes || []).some(containsMeta) ||
                    Array.from(record.removedNodes || []).some(containsMeta));
                  if (viewportMayHaveChanged) reconcile();
                  else if (records.some((record) => record.addedNodes?.length > 0)) {
                    scheduleDeferredLayoutCheck();
                  }
                });
                observer.observe(root, {
                  attributes: true,
                  attributeFilter: ['name', 'content'],
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
                globalThis[stateKey] = {
                  observer,
                  interactionEvents,
                  interactionLayoutCheckTimer: 0,
                  interactionListener: scheduleInteractionLayoutCheck,
                };
                reconcile();
                if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', reconcile, { once: true });
                  globalThis.addEventListener('load', reconcile, { once: true });
                }
              };
              if (document.documentElement) start();
              else document.addEventListener('readystatechange', start, { once: true });
            })();
        """.trimIndent()
}
