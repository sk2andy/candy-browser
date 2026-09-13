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
              const maxDiscoveryPointsPerTask = 8;
              const discoveryTaskBudgetMillis = 4;
              const maxPendingPriorityRoots = 256;
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
              const ownedCssStickyElements =
                globalThis[stateKey]?.ownedCssStickyElements || new Set();
              const ownedOriginalTopStates =
                globalThis[stateKey]?.ownedOriginalTopStates || new Map();
              const ownedStickyInlineTop = `var(${'$'}{stickyTopProperty}, 0px)`;
              let layoutWriteRevision = 0;
              let discoveryGeometryRevision = 0;
              let activePointDiscovery = null;
              let activeTopInsetProtection = null;
              let pointDiscoveryProgress = null;
              let pendingOwnedLayoutMutation = false;
              let ownedMutationLayoutFrame = 0;
              let ownedMutationLayoutRequest = null;
              const pendingPriorityRoots = new Set();
              const priorityRootRevisions = new WeakMap();
              let priorityEnqueueRevision = 0;
              const priorityTraversal = [];
              // Read epochs never survive a synchronous task or a Candy layout write.
              let layoutReadCache = null;
              const invalidateLayoutReadCache = () => {
                if (!layoutReadCache) return;
                layoutReadCache.styles = new WeakMap();
                layoutReadCache.rects = new WeakMap();
                layoutReadCache.points = new Map();
              };
              const withLayoutReadCache = (callback) => {
                if (layoutReadCache) return callback();
                layoutReadCache = {};
                invalidateLayoutReadCache();
                try {
                  return callback();
                } finally {
                  layoutReadCache = null;
                }
              };
              const readComputedStyle = (element, pseudo = null) => {
                if (!layoutReadCache) return getComputedStyle(element, pseudo);
                let styles = layoutReadCache.styles.get(element);
                if (!styles) {
                  styles = new Map();
                  layoutReadCache.styles.set(element, styles);
                }
                if (!styles.has(pseudo)) styles.set(pseudo, getComputedStyle(element, pseudo));
                return styles.get(pseudo);
              };
              const readElementRect = (element) => {
                if (!layoutReadCache) return element.getBoundingClientRect();
                if (!layoutReadCache.rects.has(element)) {
                  layoutReadCache.rects.set(element, element.getBoundingClientRect());
                }
                return layoutReadCache.rects.get(element);
              };
              const noteOwnedLayoutWrite = () => {
                layoutWriteRevision++;
                invalidateLayoutReadCache();
              };
              const setOwnedProperty = (element, name, value) => {
                if (element.style.getPropertyValue(name) === value &&
                    element.style.getPropertyPriority(name) === 'important') return;
                noteOwnedLayoutWrite();
                element.style.setProperty(name, value, 'important');
              };
              const setOwnedAttribute = (element, name, value) => {
                if (element.getAttribute(name) === value) return;
                noteOwnedLayoutWrite();
                element.setAttribute(name, value);
              };
              const removeOwnedProperty = (element, name) => {
                if (!element.style.getPropertyValue(name)) return;
                noteOwnedLayoutWrite();
                element.style.removeProperty(name);
              };
              const removeOwnedAttribute = (element, name) => {
                if (!element.hasAttribute(name)) return;
                noteOwnedLayoutWrite();
                element.removeAttribute(name);
              };
              let candidateDiscoveryNeeded = true;
              let candidateDiscoveryPolicyKey = null;
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
              let observeDiscoveredShadowRoot = () => {};
              const beginCandyPerformancePhase = (name) => {
                try {
                  if (globalThis.$bridgeName?.performanceDiagnosticsEnabled?.() !== true) {
                    return false;
                  }
                  if (typeof globalThis.performance?.mark !== 'function' ||
                      typeof globalThis.performance?.measure !== 'function') return false;
                  globalThis.performance.mark(`${'$'}{name}.start`);
                  return true;
                } catch (_error) {
                  return false;
                }
              };
              const endCandyPerformancePhase = (name, started) => {
                if (!started) return;
                try {
                  globalThis.performance.mark(`${'$'}{name}.end`);
                  globalThis.performance.measure(name, `${'$'}{name}.start`, `${'$'}{name}.end`);
                } catch (_error) {
                  // Diagnostics must never change page protection or its exception behavior.
                } finally {
                  for (const mark of [`${'$'}{name}.start`, `${'$'}{name}.end`]) {
                    try { globalThis.performance.clearMarks(mark); } catch (_error) {}
                  }
                  try { globalThis.performance.clearMeasures(name); } catch (_error) {}
                }
              };
              const currentPolicyKey = () => {
                const generation = Number(
                  globalThis.$bridgeName?.navigationGeneration?.(),
                ) || 0;
                const revision = Number(
                  globalThis.$bridgeName?.policyRevision?.(),
                ) || 0;
                return `${'$'}{generation}:${'$'}{revision}`;
              };
              const pointDiscoverySignature = () => [
                currentPolicyKey(),
                Number(globalThis.$bridgeName?.topInsetPx?.()) || 0,
                Number(globalThis.devicePixelRatio) || 1,
                globalThis.innerWidth,
                globalThis.innerHeight,
                globalThis.scrollX,
                globalThis.scrollY,
                discoveryGeometryRevision,
                layoutWriteRevision,
              ].join(':');
              const cancelPointDiscovery = () => {
                const cancelled = activePointDiscovery || activeTopInsetProtection;
                if (activePointDiscovery) globalThis.clearTimeout(activePointDiscovery.timer);
                activePointDiscovery = null;
                activeTopInsetProtection = null;
                if (cancelled) candidateDiscoveryNeeded = true;
              };
              const invalidatePointDiscovery = () => {
                discoveryGeometryRevision++;
                cancelPointDiscovery();
              };
              const discoveryNow = () => {
                try {
                  const value = globalThis.performance?.now?.();
                  if (Number.isFinite(value)) return value;
                } catch (_error) {}
                return Date.now();
              };
              const prioritizedDiscoveryAxis = (limit) => {
                const points = sampleAxis(limit);
                return Array.from(new Set([
                  points[0],
                  Math.max(1, limit - compactControlTopPaddingCssPixels - 1),
                  points[Math.floor(points.length / 2)],
                  points.at(-1),
                  points[Math.floor(points.length / 4)],
                  points[Math.floor(points.length * 3 / 4)],
                  ...points,
                ])).filter((value) => value !== undefined);
              };
              // A job retains point cursors and element identities, never layout snapshots.
              // One native hit-test can still be slow; yielding only bounds their accumulation.
              const scanInsetPoints = (root, cssPixels, visit, complete, restart = () => {}, runImmediately = false) => {
                const xs = prioritizedDiscoveryAxis(globalThis.innerWidth);
                const rows = sampleAxis(cssPixels);
                // Tiny controls can straddle the bottom inset edge without occupying early rows.
                const ys = Array.from(new Set([rows.at(-1), ...rows]))
                  .filter((value) => value !== undefined);
                const gridKey = [
                  currentPolicyKey(),
                  Number(globalThis.$bridgeName?.topInsetPx?.()) || 0,
                  globalThis.devicePixelRatio,
                  globalThis.innerWidth,
                  globalThis.innerHeight,
                  cssPixels,
                  xs.join(','),
                  ys.join(','),
                ].join(':');
                if (pointDiscoveryProgress?.root !== root || pointDiscoveryProgress.key !== gridKey) {
                  pointDiscoveryProgress = { root, key: gridKey, cursor: 0, seedTurn: true };
                }
                const job = {
                  root,
                  protection: activeTopInsetProtection,
                  signature: pointDiscoverySignature(),
                  xs,
                  ys,
                  progress: pointDiscoveryProgress,
                  cursor: pointDiscoveryProgress.cursor,
                  scanned: 0,
                  seed: 0,
                  seedY: rows[0],
                  seedTurn: pointDiscoveryProgress.seedTurn,
                  timer: 0,
                };
                const totalPoints = xs.length * ys.length;
                const seedCount = Math.min(6, xs.length);
                activePointDiscovery = job;
                const runChunk = () => {
                  if (activePointDiscovery !== job) return;
                  if (document.documentElement !== root || job.signature !== pointDiscoverySignature()) {
                    cancelPointDiscovery();
                    return;
                  }
                  job.timer = 0;
                  const phase = beginCandyPerformancePhase('Candy.SafeArea.DiscoveryChunk');
                  try {
                    withLayoutReadCache(() => {
                      const startedAt = discoveryNow();
                      if (refreshPriorityCandidates(root, cssPixels)) {
                        restart();
                        job.signature = pointDiscoverySignature();
                        job.scanned = 0;
                        if (activeTopInsetProtection) activeTopInsetProtection.signature = job.signature;
                      }
                      if (discoveryNow() - startedAt >= discoveryTaskBudgetMillis) {
                        job.timer = globalThis.setTimeout(runChunk, 0);
                        return;
                      }
                      for (let count = 0; count < maxDiscoveryPointsPerTask && job.scanned < totalPoints; count++) {
                        if (count > 0 && discoveryNow() - startedAt >= discoveryTaskBudgetMillis) break;
                        // Seeds keep common controls prompt; interleaving prevents a slow seed barrier.
                        const isSeed = job.seedTurn && job.seed < seedCount;
                        const point = isSeed ? job.seed++ : job.cursor;
                        const x = job.xs[isSeed ? point : point % job.xs.length];
                        const y = isSeed ? job.seedY : job.ys[Math.floor(point / job.xs.length)];
                        job.seedTurn = !isSeed;
                        job.progress.seedTurn = job.seedTurn;
                        if (!isSeed) {
                          job.cursor = (job.cursor + 1) % totalPoints;
                          // This is a scheduling hint, never retained coverage or a negative proof.
                          job.progress.cursor = job.cursor;
                        }
                        const result = visit(deepElementsFromPoint(x, y));
                        if (result === 'restart') {
                          job.signature = pointDiscoverySignature();
                          job.scanned = 0;
                          if (discoveryNow() - startedAt >= discoveryTaskBudgetMillis) break;
                          continue;
                        }
                        if (!isSeed) job.scanned++;
                        if (result === false) {
                          activePointDiscovery = null;
                          complete(false);
                          return;
                        }
                        if (discoveryNow() - startedAt >= discoveryTaskBudgetMillis) break;
                      }
                      if (activePointDiscovery !== job) return;
                      if (job.scanned >= totalPoints) {
                        activePointDiscovery = null;
                        complete(true);
                      } else {
                        job.timer = globalThis.setTimeout(runChunk, 0);
                      }
                    });
                  } catch (error) {
                    if (activePointDiscovery === job) {
                      globalThis.clearTimeout(job.timer);
                      activePointDiscovery = null;
                      candidateDiscoveryNeeded = true;
                    }
                    // Completion can throw after releasing the point job or starting a replacement.
                    if (job.protection && activeTopInsetProtection === job.protection) {
                      activeTopInsetProtection = null;
                      candidateDiscoveryNeeded = true;
                    }
                    throw error;
                  } finally {
                    endCandyPerformancePhase('Candy.SafeArea.DiscoveryChunk', phase);
                  }
                };
                if (runImmediately) runChunk();
                else job.timer = globalThis.setTimeout(runChunk, 0);
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
                removeOwnedAttribute(element, offsetAttribute);
                removeOwnedAttribute(element, panelAttribute);
                removeOwnedProperty(element, offsetProperty);
                removeOwnedProperty(element, panelMaxHeightProperty);
                if (translateState?.value) {
                  noteOwnedLayoutWrite();
                  element.style.setProperty(
                    'translate',
                    translateState.value,
                    translateState.priority,
                  );
                } else {
                  removeOwnedProperty(element, 'translate');
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
                removeOwnedAttribute(element, stickyAttribute);
                removeOwnedProperty(element, stickyOriginalTopProperty);
                removeOwnedProperty(element, stickyTopProperty);
                const topState = ownedOriginalTopStates.get(element);
                if (topState && element.style.getPropertyValue('top') === ownedStickyInlineTop &&
                    element.style.getPropertyPriority('top') === 'important') {
                  if (topState.value) {
                    noteOwnedLayoutWrite();
                    element.style.setProperty('top', topState.value, topState.priority);
                  } else {
                    removeOwnedProperty(element, 'top');
                  }
                }
                ownedOriginalTopStates.delete(element);
                ownedCssStickyElements.delete(element);
                ownedStickyElements.delete(element);
              };
              const clearOwnedStickyElements = () => {
                new Set([
                  ...ownedStickyElements,
                  ...document.querySelectorAll(stickySelector),
                ]).forEach(clearOwnedSticky);
              };
              const clearOwnedFlowTarget = (root) => {
                removeOwnedAttribute(root, flowRootAttribute);
                document.querySelectorAll(flowTargetSelector).forEach((element) => {
                  removeOwnedAttribute(element, flowTargetAttribute);
                  removeOwnedProperty(element, flowMarginProperty);
                  removeOwnedProperty(element, flowOffsetProperty);
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
                candidateDiscoveryNeeded = true;
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
              const deepElementsFromPoint = (x, y) => {
                const pointKey = `${'$'}{x}:${'$'}{y}`;
                const cached = layoutReadCache?.points.get(pointKey);
                if (cached) return cached;
                const phase = beginCandyPerformancePhase('Candy.SafeArea.PointDiscovery');
                try {
                  const layers = [];
                  const visitedRoots = new Set();
                  let scope = document;
                  while (scope && !visitedRoots.has(scope)) {
                    visitedRoots.add(scope);
                    const hitElements = typeof scope.elementsFromPoint === 'function'
                      ? Array.from(scope.elementsFromPoint(x, y))
                      : typeof scope.elementFromPoint === 'function'
                        ? [scope.elementFromPoint(x, y)].filter(Boolean)
                        : [];
                    layers.push(hitElements);
                    const shadowRoot = hitElements
                      .map((element) => element.shadowRoot)
                      .find((candidate) => candidate && !visitedRoots.has(candidate));
                    if (!shadowRoot) break;
                    observeDiscoveredShadowRoot(shadowRoot);
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
                  layoutReadCache?.points.set(pointKey, elements);
                  return elements;
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.PointDiscovery', phase);
                }
              };
              const isVisiblePositionedElement = (element) => {
                const style = readComputedStyle(element);
                const rect = readElementRect(element);
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
                  const position = readComputedStyle(current).position;
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
              const refreshStickyElements = (elements, cssPixels) => {
                for (const element of elements) {
                  if (!element.isConnected) {
                    clearOwnedSticky(element);
                    continue;
                  }
                  if (ownedCssStickyElements.has(element)) continue;
                  const style = readComputedStyle(element);
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
                  // An ancestor anchor can move a nested sticky scrollport; read after its write.
                  applyStickyTopAnchor(element, originalTop, cssPixels);
                }
              };
              const refreshKnownStickyElements = (cssPixels) => withLayoutReadCache(() => {
                const phase = beginCandyPerformancePhase('Candy.SafeArea.KnownSticky');
                try {
                    refreshStickyElements(Array.from(ownedStickyElements), cssPixels);
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.KnownSticky', phase);
                }
              });
              const refreshOwnedStickyElements = (cssPixels) => {
                refreshStickyElements(
                  new Set([
                    ...ownedStickyElements,
                    ...document.querySelectorAll(stickySelector),
                  ]),
                  cssPixels,
                );
              };
              const stickyScrollportTop = (element, root) => {
                for (
                  let current = parentElementOrShadowHost(element);
                  current && current !== root;
                  current = parentElementOrShadowHost(current)
                ) {
                  const style = readComputedStyle(current);
                  if (['auto', 'scroll', 'hidden', 'overlay'].includes(style.overflowY)) {
                    return Math.max(
                      0,
                      readElementRect(current).top +
                        (Number.parseFloat(style.borderTopWidth) || 0),
                    );
                  }
                }
                return 0;
              };
              const isIdentityTransform = (value) => {
                if (typeof value !== 'string' || !value.endsWith(')')) return false;
                const prefix = value.startsWith('matrix3d(')
                  ? 'matrix3d('
                  : value.startsWith('matrix(') ? 'matrix(' : null;
                if (!prefix) return false;
                const expected = prefix === 'matrix('
                  ? [1, 0, 0, 1, 0, 0]
                  : [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
                const values = value.slice(prefix.length, -1).split(',');
                return values.length === expected.length && values.every((part, index) =>
                  part.trim() !== '' && Number(part) === expected[index]);
              };
              const hasMovingStickyStyle = (style, allowOwnIdentityTransform = false) => {
                const durations = (style.transitionDuration || '0s').split(',').map(Number.parseFloat);
                const properties = (style.transitionProperty || 'none').split(',').map((value) => value.trim());
                if (properties.some((value, index) =>
                    ['all', 'position', 'top', 'bottom', 'left', 'right', 'inset',
                      'inset-block', 'inset-block-start', 'inset-block-end',
                      'inset-inline', 'inset-inline-start', 'inset-inline-end',
                      'margin', 'margin-top', 'margin-bottom', 'margin-block',
                      'margin-block-start', 'margin-block-end', 'padding', 'padding-top',
                      'padding-bottom', 'padding-block', 'padding-block-start', 'padding-block-end',
                      'height', 'min-height', 'max-height', 'width', 'min-width', 'max-width',
                      'transform', 'translate', 'scale', 'rotate', 'zoom'].includes(value) &&
                    (durations[index % durations.length] || 0) > 0)) return true;
                if (style.animationName && style.animationName !== 'none' &&
                    style.animationName.split(',').some((value) => value.trim() !== 'none')) return true;
                return ['transform', 'translate', 'perspective', 'scale', 'rotate'].some((name) =>
                  style[name] && style[name] !== 'none' &&
                    !(name === 'transform' && allowOwnIdentityTransform && isIdentityTransform(style[name]))) ||
                  (style.zoom && !['1', 'normal', '100%'].includes(style.zoom)) ||
                  (style.offsetPath && style.offsetPath !== 'none');
              };
              const canUseCssStickyAnchor = (element, root) => {
                if (ownedOffsetElements.has(element) || readComputedStyle(element).position !== 'sticky') return false;
                for (let current = element; current; current = parentElementOrShadowHost(current)) {
                  // Slot assignment can introduce a scrollport absent from the light-DOM path.
                  if (current.assignedSlot) return false;
                  const style = readComputedStyle(current);
                  if (hasMovingStickyStyle(style)) return false;
                  if (current !== element && current !== root &&
                      ['auto', 'scroll', 'hidden', 'overlay'].includes(style.overflowY)) return false;
                  if (current === root) break;
                }
                return true;
              };
              const stickyAnchorAffectedByMutation = (element, record) => {
                if (!isRelevantLayoutMutation(record)) return false;
                if (record.target?.matches?.('style, link[rel~="stylesheet"], meta[name="viewport"]')) return true;
                if ([...(record.addedNodes || []), ...(record.removedNodes || [])].some((node) =>
                    node.matches?.('style, link[rel~="stylesheet"]') ||
                    node.querySelector?.('style, link[rel~="stylesheet"]'))) return true;
                if (record.type === 'attributes') return record.target === element ||
                  composedContains(record.target, element);
                // Sibling insertion/removal can change ancestor-scoped :has()/structural rules.
                return composedContains(record.target, element) ||
                  [...(record.addedNodes || []), ...(record.removedNodes || [])].some((node) =>
                    node === element || composedContains(node, element));
              };
              const revalidateOwnedStickyAnchors = (cssPixels, records = null) => withLayoutReadCache(() => {
                // Remove both inline and selector-based overrides before reading the author top.
                for (const element of Array.from(ownedStickyElements)) {
                  if (records && !records.some((record) => stickyAnchorAffectedByMutation(element, record))) continue;
                  clearOwnedSticky(element);
                  if (!element.isConnected) continue;
                  const style = readComputedStyle(element);
                  const originalTop = Number.parseFloat(style.top);
                  if (style.position !== 'sticky' || !Number.isFinite(originalTop) || originalTop < -0.5) continue;
                  applyStickyTopAnchor(element, originalTop, cssPixels);
                }
              });
              const mutationTouchesOwnedLayout = (record) => {
                if (!isRelevantLayoutMutation(record)) return false;
                const globalStyleSelector = 'style, link[rel~="stylesheet"], meta';
                if (record.target?.matches?.(globalStyleSelector)) return true;
                const changedNodes = [...(record.addedNodes || []), ...(record.removedNodes || [])];
                if (changedNodes.some((node) => node.matches?.(globalStyleSelector) ||
                    node.querySelector?.(globalStyleSelector))) return true;
                const ownedElements = new Set([...ownedOffsetElements, ...ownedStickyElements]);
                return [record.target, ...changedNodes].some((node) => node &&
                  Array.from(ownedElements).some((element) =>
                    node === element || composedContains(node, element) || composedContains(element, node) ||
                    (node.host && composedContains(node.host, element))));
              };
              const mutationNeedsImmediateOwnedLayout = (record) => {
                if (!mutationTouchesOwnedLayout(record)) return false;
                // A page rAF can mutate attributes after this frame's callback list is fixed.
                // Keep fresh protection in its microtask checkpoint, not one paint later.
                if (record.type === 'attributes') return true;
                const globalStyleSelector = 'style, link[rel~="stylesheet"], meta';
                if (record.target?.matches?.(globalStyleSelector)) return true;
                if ([...(record.addedNodes || []), ...(record.removedNodes || [])].some((node) =>
                    node.matches?.(globalStyleSelector) || node.querySelector?.(globalStyleSelector))) return true;
                return [...ownedOffsetElements, ...ownedStickyElements].some((element) =>
                  record.target === element || composedContains(element, record.target));
              };
              const applyStickyTopAnchor = (element, originalTop, cssPixels) => {
                if (canUseCssStickyAnchor(element, document.documentElement)) {
                  if (!ownedOriginalTopStates.has(element)) {
                    ownedOriginalTopStates.set(element, {
                      value: element.style.getPropertyValue('top'),
                      priority: element.style.getPropertyPriority('top'),
                    });
                  }
                  setOwnedProperty(element, stickyOriginalTopProperty, `${'$'}{originalTop}px`);
                  setOwnedProperty(element, stickyTopProperty,
                    `max(${'$'}{originalTop}px, var(${'$'}{property}, 0px))`);
                  setOwnedProperty(element, 'top', ownedStickyInlineTop);
                  setOwnedAttribute(element, stickyAttribute, 'true');
                  ownedStickyElements.add(element);
                  ownedCssStickyElements.add(element);
                  return;
                }
                if (ownedCssStickyElements.has(element)) clearOwnedSticky(element);
                const scrollportTop = stickyScrollportTop(
                  element,
                  document.documentElement,
                );
                const anchoredTop = Math.max(originalTop, cssPixels - scrollportTop);
                if (anchoredTop <= originalTop + 0.5) {
                  clearOwnedSticky(element);
                  return;
                }
                setOwnedProperty(element, stickyOriginalTopProperty, `${'$'}{originalTop}px`);
                setOwnedProperty(element, stickyTopProperty, `${'$'}{anchoredTop}px`);
                setOwnedAttribute(element, stickyAttribute, 'true');
                ownedStickyElements.add(element);
              };
              const protectStickyTopAnchors = (root, cssPixels, refreshOwned = true) => withLayoutReadCache(() => {
                if (!candidateDiscoveryNeeded) return;
                if (activeTopInsetProtection?.root === root &&
                    activeTopInsetProtection.signature === pointDiscoverySignature()) {
                  if (refreshOwned) refreshKnownStickyElements(cssPixels);
                  return;
                }
                if (refreshOwned) refreshOwnedStickyElements(cssPixels);
                const activeOwnedHeader = Array.from(ownedStickyElements).some((element) => {
                  if (!element.isConnected) return false;
                  const rect = readElementRect(element);
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
                      if (candidate && readComputedStyle(candidate).position === 'sticky') {
                        candidates.add(candidate);
                        break;
                      }
                    }
                  }
                }
                candidates.forEach((element) => {
                  if (element.getAttribute(stickyAttribute) === 'true') return;
                  const style = readComputedStyle(element);
                  const originalTop = Number.parseFloat(style.top);
                  if (
                    !Number.isFinite(originalTop) ||
                    originalTop < -0.5
                  ) {
                    return;
                  }
                  applyStickyTopAnchor(element, originalTop, cssPixels);
                });
              });
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
                const style = readComputedStyle(element);
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
                const rect = readElementRect(element);
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
                  setOwnedProperty(plan.element, offsetProperty, `${'$'}{plan.offset}px`);
                  setOwnedProperty(plan.element, 'translate', `0px var(${'$'}{offsetProperty}, 0px)`);
                  setOwnedAttribute(plan.element, offsetAttribute, 'true');
                  ownedOffsetElements.add(plan.element);
                  if (plan.panelMaxHeight === null) {
                    removeOwnedAttribute(plan.element, panelAttribute);
                    removeOwnedProperty(plan.element, panelMaxHeightProperty);
                  } else {
                    setOwnedProperty(plan.element, panelMaxHeightProperty, `${'$'}{plan.panelMaxHeight}px`);
                    setOwnedAttribute(plan.element, panelAttribute, 'true');
                  }
                }
                return plans.every((plan) => {
                  if (plan.position === 'absolute' && globalThis.scrollY > 0) return true;
                  const rect = readElementRect(plan.element);
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
              const enqueuePrioritySubtree = (element) => {
                if (!(element instanceof Element)) return;
                pendingPriorityRoots.delete(element);
                pendingPriorityRoots.add(element);
                priorityRootRevisions.set(element, ++priorityEnqueueRevision);
                if (pendingPriorityRoots.size > maxPendingPriorityRoots) {
                  // Priority is best-effort only. Full-grid verification remains authoritative.
                  pendingPriorityRoots.delete(pendingPriorityRoots.values().next().value);
                }
              };
              const nextPriorityElement = () => {
                if (pendingPriorityRoots.size > 0) {
                  const roots = Array.from(pendingPriorityRoots);
                  const node = roots.at(-1);
                  const revision = priorityRootRevisions.get(node);
                  if (priorityTraversal.length === 0 || revision > priorityTraversal.at(-1).revision) {
                    pendingPriorityRoots.delete(node);
                    priorityTraversal.push({ node, revision, entered: false, child: null, shadowVisited: false });
                    if (priorityTraversal.length > maxPendingPriorityRoots) {
                      priorityTraversal.splice(0, priorityTraversal.length - maxPendingPriorityRoots);
                    }
                  }
                }
                while (priorityTraversal.length > 0) {
                  const frame = priorityTraversal.at(-1);
                  if ((frame.node instanceof Element && !frame.node.isConnected) ||
                      (frame.node.host && !frame.node.host.isConnected)) {
                    priorityTraversal.pop();
                    continue;
                  }
                  if (!frame.entered) {
                    frame.entered = true;
                    frame.child = frame.node.firstElementChild;
                    if (frame.node instanceof Element) return frame.node;
                  }
                  if (frame.child) {
                    const node = frame.child;
                    frame.child = node.nextElementSibling;
                    priorityTraversal.push({ node, revision: frame.revision, entered: false, child: null, shadowVisited: false });
                    continue;
                  }
                  if (!frame.shadowVisited) {
                    frame.shadowVisited = true;
                    const shadowRoot = frame.node.shadowRoot;
                    if (shadowRoot?.mode === 'open') {
                      observeDiscoveredShadowRoot(shadowRoot);
                      priorityTraversal.push({ node: shadowRoot, revision: frame.revision, entered: false, child: null, shadowVisited: true });
                      continue;
                    }
                  }
                  priorityTraversal.pop();
                }
                return null;
              };
              const protectPriorityPositionedElement = (element, cssPixels, allowAbsolute = false) => {
                const style = readComputedStyle(element);
                if (ownedOffsetElements.has(element) ||
                    (style.position !== 'fixed' && !(allowAbsolute && style.position === 'absolute'))) return false;
                for (let current = element; current; current = parentElementOrShadowHost(current)) {
                  // An identity transform on the fixed box preserves its own offset geometry.
                  // Ancestor transforms still change containing blocks, even when visually identity.
                  if (current.assignedSlot || hasMovingStickyStyle(
                      readComputedStyle(current), current === element && style.position === 'fixed')) return false;
                }
                if (!isVisiblePositionedElement(element)) return false;
                const plan = planLocalOffset(element, cssPixels);
                if (!plan || plan.offset <= 0 ||
                    hasPositionedPeerCollision([plan], document.documentElement, true)) return false;
                return applyLocalOffsetPlans([plan]);
              };
              const refreshPriorityCandidates = (root, cssPixels) => {
                const revision = layoutWriteRevision;
                const startedAt = discoveryNow();
                for (let count = 0; count < maxDiscoveryPointsPerTask; count++) {
                  const element = nextPriorityElement();
                  if (!element) break;
                  const style = readComputedStyle(element);
                  if (style.position === 'sticky' && !ownedStickyElements.has(element)) {
                    const originalTop = Number.parseFloat(style.top);
                    if (Number.isFinite(originalTop) && originalTop >= -0.5 && canUseCssStickyAnchor(element, root)) {
                      applyStickyTopAnchor(element, originalTop, cssPixels);
                    }
                  } else if (style.position === 'fixed') {
                    protectPriorityPositionedElement(element, cssPixels);
                  }
                  if (discoveryNow() - startedAt >= discoveryTaskBudgetMillis) break;
                }
                return layoutWriteRevision !== revision;
              };
              const isInteractiveAtPoint = (element, boundary) => {
                for (
                  let current = element;
                  current && composedContains(boundary, current);
                  current = parentElementOrShadowHost(current)
                ) {
                  const style = readComputedStyle(current);
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
                  const style = readComputedStyle(current);
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
                  const style = readComputedStyle(current);
                  const rect = readElementRect(current);
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
                  const currentRect = readElementRect(plan.element);
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
                      const peerStyle = readComputedStyle(peer);
                      if (
                        peerStyle.display === 'none' ||
                        peerStyle.visibility === 'hidden' ||
                        peerStyle.visibility === 'collapse' ||
                        Number.parseFloat(peerStyle.opacity) <= 0.01
                      ) {
                        return false;
                      }
                      const peerRect = readElementRect(peer);
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
              const refreshOffsetElements = (elements, cssPixels) => {
                const plans = [];
                for (const element of elements) {
                  if (!element.isConnected) {
                    clearOwnedOffset(element);
                    continue;
                  }
                  const style = readComputedStyle(element);
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
              const refreshKnownOffsets = (cssPixels) => withLayoutReadCache(() => {
                const phase = beginCandyPerformancePhase('Candy.SafeArea.KnownOffsets');
                try {
                    return refreshOffsetElements(Array.from(ownedOffsetElements), cssPixels);
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.KnownOffsets', phase);
                }
              });
              const flushPendingOwnedLayoutMutation = (cssPixels) => withLayoutReadCache(() => {
                if (!pendingOwnedLayoutMutation) return true;
                // Opaque CSS may affect any established header; deferral is not a safety proof.
                revalidateOwnedStickyAnchors(cssPixels);
                const protectedOffsets = refreshKnownOffsets(cssPixels);
                refreshKnownStickyElements(cssPixels);
                pendingOwnedLayoutMutation = !protectedOffsets;
                return protectedOffsets;
              });
              const cancelOwnedMutationLayoutCheck = () => {
                if (ownedMutationLayoutFrame) globalThis.cancelAnimationFrame(ownedMutationLayoutFrame);
                ownedMutationLayoutFrame = 0;
                ownedMutationLayoutRequest = null;
              };
              const scheduleOwnedMutationLayoutCheck = () => {
                if (ownedMutationLayoutRequest) return;
                const request = { root: document.documentElement, policyKey: currentPolicyKey() };
                ownedMutationLayoutRequest = request;
                ownedMutationLayoutFrame = globalThis.requestAnimationFrame(() => {
                  if (ownedMutationLayoutRequest !== request) return;
                  ownedMutationLayoutFrame = 0;
                  ownedMutationLayoutRequest = null;
                  if (document.documentElement !== request.root || currentPolicyKey() !== request.policyKey) return;
                  const phase = beginCandyPerformancePhase('Candy.SafeArea.OwnedMutationFrame');
                  try {
                    const physicalPixels = Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                    const density = Number(globalThis.devicePixelRatio) || 1;
                    if (physicalPixels <= 0 || !request.root) return;
                    if (!flushPendingOwnedLayoutMutation(physicalPixels / density)) {
                      scheduleDeferredLayoutCheck(false);
                    }
                  } finally {
                    endCandyPerformancePhase('Candy.SafeArea.OwnedMutationFrame', phase);
                  }
                });
              };
              const refreshOwnedOffsets = (cssPixels) => {
                return refreshOffsetElements(
                  new Set([
                    ...ownedOffsetElements,
                    ...document.querySelectorAll(offsetSelector),
                  ]),
                  cssPixels,
                );
              };
              const isBackdrop = (element, candidates) => {
                const style = readComputedStyle(element);
                if (style.position !== 'fixed') return false;
                const rect = readElementRect(element);
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
                  const candidateStyle = readComputedStyle(candidate);
                  if (
                    candidateStyle.position !== 'fixed' ||
                    candidateStyle.display === 'none' ||
                    candidateStyle.visibility === 'hidden' ||
                    candidateStyle.visibility === 'collapse'
                  ) {
                    return false;
                  }
                  const candidateRect = readElementRect(candidate);
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
                complete = () => {},
                allowRecoverySuspension = false,
              ) => {
                const signature = pointDiscoverySignature();
                const previous = activeTopInsetProtection;
                if (previous && previous.root === root && previous.signature === signature &&
                    (!previous.fixedOnly || fixedOnly) &&
                    (!previous.preserveOwnedOffsets || preserveOwnedOffsets)) {
                  if (allowRecoverySuspension && !previous.allowRecoverySuspension) {
                    previous.complete = complete;
                    previous.allowRecoverySuspension = true;
                  }
                  return;
                }
                cancelPointDiscovery();
                const operation = {
                  root, signature, fixedOnly, preserveOwnedOffsets,
                  complete, allowRecoverySuspension, started: false,
                };
                activeTopInsetProtection = operation;
                const finish = (protectedTop) => {
                  if (activeTopInsetProtection !== operation) return;
                  activeTopInsetProtection = null;
                  operation.complete(protectedTop);
                };
                const ignored = (element) =>
                  !element || element === root || element === body || element === style ||
                  element.matches?.(flowTargetSelector) || element.tagName === 'HEAD';
                const runPass = (pass) => {
                  if (activeTopInsetProtection !== operation) return;
                  operation.signature = pointDiscoverySignature();
                  const candidates = new Set();
                  const plannedCandidates = new Set();
                  const runImmediately = !operation.started;
                  operation.started = true;
                  scanInsetPoints(root, cssPixels, (elements) => {
                      for (const element of elements) {
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
                          const candidateRect = readElementRect(candidate);
                          const coversViewport =
                            candidateRect.width >= globalThis.innerWidth * 0.8 &&
                            candidateRect.height >= globalThis.innerHeight * 0.8;
                          if (!coversViewport) candidates.add(candidate);
                          continue;
                        }
                        // Visible positioned controls cannot wait for the remaining viewport matrix.
                        // Preserve author motion and backdrop guards, then validate projected peers.
                        const revisionBeforePriority = layoutWriteRevision;
                        protectPriorityPositionedElement(candidate, cssPixels, !fixedOnly);
                        if (layoutWriteRevision !== revisionBeforePriority) {
                          // Its write may move descendants and invalidate every earlier negative hit.
                          candidates.clear();
                          plannedCandidates.clear();
                          operation.signature = pointDiscoverySignature();
                          return 'restart';
                        }
                        candidates.add(candidate);
                        plannedCandidates.add(candidate);
                        break;
                      }
                      return true;
                  }, (scanned) => {
                  if (!scanned) { finish(false); return; }
                  if (plannedCandidates.size === 0) { finish(candidates.size === 0); return; }
                  const candidateList = Array.from(candidates);
                  const backdropPeers = Array.from(new Set([
                    ...candidateList,
                    ...document.querySelectorAll(`[${'$'}{panelAttribute}="true"]`),
                  ]));
                  // Candidate plans must be read again after the final yielded point query.
                  const plans = Array.from(plannedCandidates)
                    .filter((element) => !isBackdrop(element, backdropPeers))
                    .map((element) => element.isConnected ? planLocalOffset(element, cssPixels) : null);
                  if (plans.some((plan) => !plan)) { finish(false); return; }
                  if (plans.length === 0) { finish(true); return; }
                  if (hasPositionedPeerCollision(plans, root, true)) {
                    localOffsetCollisionDetected = true;
                    finish(false);
                    return;
                  }
                  if (!applyLocalOffsetPlans(plans)) { finish(false); return; }
                  if (pass >= 2) { finish(false); return; }
                  runPass(pass + 1);
                  }, () => {
                    candidates.clear();
                    plannedCandidates.clear();
                  }, runImmediately);
                };
                runPass(0);
              };
              const findFlowTarget = (element, root, body) => {
                let target = element;
                for (let current = element; current && current !== root; current = current.parentElement) {
                  const position = readComputedStyle(current).position;
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
                setOwnedAttribute(root, flowRootAttribute, 'true');
                const targetStyle = readComputedStyle(target);
                const originalMargin = targetStyle.marginTop;
                const originalMarginPixels = Number.parseFloat(originalMargin);
                if (!Number.isFinite(originalMarginPixels) || !originalMargin.endsWith('px')) {
                  clearOwnedFlowTarget(root);
                  return false;
                }
                const requiredOffset = Math.max(
                  0,
                  cssPixels - readElementRect(target).top,
                );
                setOwnedProperty(target, flowMarginProperty, originalMargin);
                setOwnedProperty(target, flowOffsetProperty, `${'$'}{requiredOffset}px`);
                setOwnedAttribute(target, flowTargetAttribute, 'true');
                return readElementRect(target).top >= cssPixels - 0.5;
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
                  if (resetFailures) candidateDiscoveryNeeded = true;
                  reconcile();
                }, layoutQuietPeriodMs());
              };
              const scheduleImmediateLayoutCheck = () => {
                if (layoutRecoverySuspendedForCurrentPolicy()) return;
                if (immediateLayoutCheckFrame) {
                  globalThis.cancelAnimationFrame(immediateLayoutCheckFrame);
                }
                immediateLayoutCheckFrame = globalThis.requestAnimationFrame(() => {
                  immediateLayoutCheckFrame = 0;
                  reconcile(false);
                });
              };
              const protectLateTopInset = () => withLayoutReadCache(() => {
                const phase = beginCandyPerformancePhase('Candy.SafeArea.QuietProtection');
                try {
                  const root = document.documentElement;
                  const body = document.body;
                  const style = document.querySelector(ownedSelector);
                  const physicalPixels = Number(
                    globalThis.$bridgeName?.topInsetPx?.(),
                  ) || 0;
                  if (!root || !body || !style || physicalPixels <= 0) return;
                  const density = Number(globalThis.devicePixelRatio) || 1;
                  if (!flushPendingOwnedLayoutMutation(physicalPixels / density)) {
                    scheduleDeferredLayoutCheck(false);
                    return;
                  }
                  localOffsetCollisionDetected = false;
                  if (!candidateDiscoveryNeeded) {
                    if (!refreshKnownOffsets(physicalPixels / density)) {
                      candidateDiscoveryNeeded = true;
                      scheduleDeferredLayoutCheck(false);
                    }
                    return;
                  }
                  protectTopInset(
                    root,
                    body,
                    style,
                    physicalPixels / density,
                    true,
                    true,
                    (protectedTop) => {
                      deferredLayoutChecks++;
                      if (protectedTop) {
                        candidateDiscoveryNeeded = false;
                        consecutiveLayoutFailures = 0;
                      } else {
                        scheduleDeferredLayoutCheck(false);
                      }
                    },
                  );
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.QuietProtection', phase);
                }
              });
              const verifyLateTopInset = () => withLayoutReadCache(() => {
                const phase = beginCandyPerformancePhase('Candy.SafeArea.QuietVerification');
                try {
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
                  if (!flushPendingOwnedLayoutMutation(cssPixels)) {
                    scheduleDeferredLayoutCheck(false);
                    return;
                  }
                  if (!candidateDiscoveryNeeded) return;
                  if (activePointDiscovery || activeTopInsetProtection) {
                    scrollVerificationTimer = globalThis.setTimeout(verifyLateTopInset, layoutQuietPeriodMs());
                    return;
                  }
                  scanInsetPoints(root, cssPixels, (elements) => {
                      for (const element of elements) {
                        if (
                          !element || element === root || element === body || element === style ||
                          element.tagName === 'HEAD'
                        ) {
                          continue;
                        }
                        const candidate = findPositionedCandidate(element, root, true);
                        if (!candidate) continue;
                        const candidateRect = readElementRect(candidate);
                        const coversViewport =
                          candidateRect.width >= globalThis.innerWidth * 0.8 &&
                          candidateRect.height >= globalThis.innerHeight * 0.8;
                        if (coversViewport) continue;
                        return false;
                      }
                      return true;
                  }, (protectedTop) => {
                        if (protectedTop) {
                          scrollVerificationFailures = 0;
                          scrollVerificationPolicyKey = currentPolicyKey();
                          return;
                        }
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
                  });
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.QuietVerification', phase);
                }
              });
              const windowScrollListener = () => {
                invalidatePointDiscovery();
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
                  // APZ must keep content-main-thread work bounded while fling frames paint.
                  // Candidate discovery can force style/layout across large Shadow DOM feeds, so
                  // only refresh the small set that Candy already owns until scrolling settles.
                  refreshKnownStickyElements(physicalPixels / density);
                });
                scrollVerificationTimer = globalThis.setTimeout(
                  () => {
                    scrollVerificationTimer = 0;
                    // A previously offscreen sticky header can become active without a mutation.
                    candidateDiscoveryNeeded = true;
                    scrollLayoutCheckFrame = globalThis.requestAnimationFrame(() => {
                      scrollLayoutCheckFrame = 0;
                      const root = document.documentElement;
                      const physicalPixels = Number(
                        globalThis.$bridgeName?.topInsetPx?.(),
                      ) || 0;
                      if (root && physicalPixels > 0) {
                        const density = Number(globalThis.devicePixelRatio) || 1;
                        protectStickyTopAnchors(root, physicalPixels / density);
                      }
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
              const protectInteractionTarget = (event) => withLayoutReadCache(() => {
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
              });
              const protectFocusedContainer = (root, cssPixels) => {
                const target = document.activeElement;
                if (!(target instanceof Element) || target === document.body) return;
                const candidate = findPositionedCandidate(target, root, false);
                if (!candidate) return;
                const plan = planLocalOffset(candidate, cssPixels);
                if (plan) applyLocalOffsetPlans([plan]);
              };
              const scheduleInteractionLayoutCheck = () => {
                invalidatePointDiscovery();
                resumeLayoutRecovery();
                scheduleDeferredLayoutCheck(true);
              };
              const scheduleImmediateInteractionLayoutCheck = (event) => {
                invalidatePointDiscovery();
                protectInteractionTarget(event);
                resumeLayoutRecovery();
                scheduleImmediateLayoutCheck();
                scheduleDeferredLayoutCheck(true);
              };
              const reconfigure = () => {
                cancelOwnedMutationLayoutCheck();
                invalidatePointDiscovery();
                if (deferredLayoutCheckTimer) {
                  globalThis.clearTimeout(deferredLayoutCheckTimer);
                  deferredLayoutCheckTimer = 0;
                }
                resetFailuresForPolicy(currentPolicyKey(), true);
                pendingOwnedLayoutMutation = true;
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
                  ? paintedBackground(readComputedStyle(document.body))
                  : null;
                if (bodyBackground) return bodyBackground;
                return paintedBackground(readComputedStyle(root)) || 'transparent';
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
                      const background = paintedBackground(readComputedStyle(element));
                      if (background) return background;
                      if (element === root) break;
                    }
                  }
                }
                return activeThemeColor() || canvasBackground(root);
              };
              const reconcile = (allowRecoverySuspension = true) => withLayoutReadCache(() => {
                const phase = beginCandyPerformancePhase('Candy.SafeArea.Reconcile');
                try {
                  const root = document.documentElement;
                  if (!root) return;
                  const policyKey = currentPolicyKey();
                  if (candidateDiscoveryPolicyKey !== policyKey) {
                    cancelOwnedMutationLayoutCheck();
                    candidateDiscoveryNeeded = true;
                    pendingOwnedLayoutMutation = true;
                    candidateDiscoveryPolicyKey = policyKey;
                  }
                  const physicalPixels =
                    Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                  if (physicalPixels <= 0) {
                    cancelOwnedMutationLayoutCheck();
                    pendingOwnedLayoutMutation = false;
                    consecutiveLayoutFailures = 0;
                    removeOwnedAttribute(root, 'data-candy-browser-top-inset-failure');
                    clearOwnedOffsets();
                    clearOwnedStickyElements();
                    clearOwnedFlowTarget(root);
                    noteOwnedLayoutWrite();
                    document.querySelector(ownedSelector)?.remove();
                    removeOwnedProperty(root, property);
                    removeOwnedProperty(root, backgroundProperty);
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
                    noteOwnedLayoutWrite();
                    root.appendChild(style);
                  }
                  const density = Number(globalThis.devicePixelRatio) || 1;
                  const cssPixels = physicalPixels / density;
                  const propertyValue = `${'$'}{cssPixels}px`;
                  if (
                    root.style.getPropertyValue(property) !== propertyValue ||
                    root.style.getPropertyPriority(property) !== 'important'
                  ) {
                    setOwnedProperty(root, property, propertyValue);
                  }
                  const topBackground = topContentBackground(root, cssPixels);
                  if (
                    root.style.getPropertyValue(backgroundProperty) !== topBackground ||
                    root.style.getPropertyPriority(backgroundProperty) !== 'important'
                  ) {
                    setOwnedProperty(root, backgroundProperty, topBackground);
                  }
                  if (!flushPendingOwnedLayoutMutation(cssPixels)) {
                    if (allowRecoverySuspension) suspendLayoutRecovery('owned-offset-refresh');
                    return;
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
                    ? Number.parseFloat(readComputedStyle(activeFlowTarget).marginTop)
                    : Number.parseFloat(readComputedStyle(root, '::before').height);
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
                  if (candidateDiscoveryNeeded && document.readyState !== 'loading') {
                    localOffsetCollisionDetected = false;
                    const body = document.body;
                    const isAtDocumentTop = globalThis.scrollY <= 0;
                    const finishProtection = (protectedTop) => {
                      deferredLayoutChecks++;
                      if (!protectedTop) {
                        if (allowRecoverySuspension) suspendLayoutRecovery(
                          localOffsetCollisionDetected ? 'positioned-peer-collision' : 'top-obstruction',
                        );
                        return;
                      }
                      candidateDiscoveryNeeded = document.readyState === 'loading';
                      consecutiveLayoutFailures = 0;
                      removeOwnedAttribute(root, 'data-candy-browser-top-inset-failure');
                    };
                    protectTopInset(root, body, style, cssPixels, !isAtDocumentTop, false,
                      (protectedTop) => {
                        if (!protectedTop && !localOffsetCollisionDetected && isAtDocumentTop &&
                            installTargetedFlowInset(root, body, style, cssPixels)) {
                          if (!refreshOwnedOffsets(cssPixels)) { finishProtection(false); return; }
                          protectTopInset(root, body, style, cssPixels, false, false,
                            finishProtection, allowRecoverySuspension);
                          return;
                        }
                        finishProtection(protectedTop);
                      }, allowRecoverySuspension);
                    return;
                  }
                  candidateDiscoveryNeeded = document.readyState === 'loading';
                  consecutiveLayoutFailures = 0;
                  removeOwnedAttribute(root, 'data-candy-browser-top-inset-failure');
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.Reconcile', phase);
                }
              });
              const styleWithoutCandyProperties = (value, element = null) => {
                let authorTop = '';
                const topState = ownedOriginalTopStates.get(element);
                if (topState) {
                  const declaration = (value || '').match(/(?:^|;)\s*top\s*:\s*([^;]*)/i)?.[1]?.trim() || '';
                  const declaredValue = declaration.replace(/\s*!\s*important\s*${'$'}/i, '').trim();
                  const declaredPriority = /!\s*important\s*${'$'}/i.test(declaration) ? 'important' : '';
                  authorTop = declaredValue === ownedStickyInlineTop
                    ? `${'$'}{topState.value}|${'$'}{topState.priority}`
                    : `${'$'}{declaredValue}|${'$'}{declaredPriority}`;
                  value = (value || '').replace(/(^|;)\s*top\s*:[^;]*(?=;|${'$'})/gi, '${'$'}1');
                }
                const normalized = (value || '')
                  .replace(/--candy-browser-[^:;]+\s*:\s*[^;]*(?:;|${'$'})/gi, '')
                  .replace(
                    /translate\s*:\s*0(?:px)?\s+var\(--candy-browser-owned-top-inset-offset[^;]*(?:;|${'$'})/gi,
                    '',
                  )
                  .replace(/\s+/g, ' ')
                  .trim();
                return topState
                  ? `${'$'}{normalized.replace(/^;+|;+${'$'}/g, '')}|author-top:${'$'}{authorTop}`
                  : normalized;
              };
              const mutationNeedsCandidateDiscovery = (record) => {
                const root = document.documentElement;
                const cssPixels = (Number(globalThis.$bridgeName?.topInsetPx?.()) || 0) /
                  (Number(globalThis.devicePixelRatio) || 1);
                if (!root || cssPixels <= 0) return false;
                // Opaque stylesheets and :has()/structural selectors can reposition an old static
                // node anywhere in this root. Neither leaf shape nor offscreen bounds prove safety.
                return isRelevantLayoutMutation(record);
              };
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
                  styleWithoutCandyProperties(record.oldValue, record.target) !==
                    styleWithoutCandyProperties(record.target?.getAttribute?.('style'), record.target);
              };
              const start = () => {
                const root = document.documentElement;
                if (!root) return;
                const previousState = globalThis[stateKey];
                if (typeof previousState?.dispose === 'function') {
                  previousState.dispose();
                } else {
                  previousState?.observer?.disconnect();
                  previousState?.restoreAttachShadowHook?.();
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
                const observeOpenShadowRoot = (shadowRoot) => {
                  if (!shadowRoot || shadowRoot.mode !== 'open' ||
                      observedShadowRoots.has(shadowRoot)) return;
                  observedShadowRoots.add(shadowRoot);
                  observer.observe(shadowRoot, observerOptions);
                };
                observeDiscoveredShadowRoot = observeOpenShadowRoot;
                observer = new MutationObserver((records) => {
                  if (!attachShadowHookActive) return;
                  const phase = beginCandyPerformancePhase('Candy.SafeArea.Mutations');
                  try {
                    records.forEach((record) => {
                      record.addedNodes?.forEach((node) => {
                        if (node instanceof Element) {
                          observeOpenShadowRoot(node.shadowRoot);
                          enqueuePrioritySubtree(node);
                        }
                      });
                      if (record.type === 'attributes' && isRelevantLayoutMutation(record)) {
                        enqueuePrioritySubtree(record.target);
                      }
                      if (record.removedNodes?.length > 0) {
                        pendingPriorityRoots.forEach((node) => {
                          if (!node.isConnected) pendingPriorityRoots.delete(node);
                        });
                      }
                    });
                    const interruptedDiscovery = (activePointDiscovery || activeTopInsetProtection) &&
                      records.some(isRelevantLayoutMutation);
                    if (interruptedDiscovery || withLayoutReadCache(() => records.some((record) =>
                        isRelevantLayoutMutation(record) && mutationNeedsCandidateDiscovery(record)))) {
                      invalidatePointDiscovery();
                      pendingOwnedLayoutMutation = true;
                      resumeLayoutRecovery();
                      // Feed discovery waits for quiet; direct header repair below stays immediate.
                      // Queue recovery before any fresh read can throw; pending work is not proof.
                      scheduleDeferredLayoutCheck(true);
                      if (records.some(mutationTouchesOwnedLayout)) {
                        if (records.some(mutationNeedsImmediateOwnedLayout)) {
                          cancelOwnedMutationLayoutCheck();
                          const immediatePhase = beginCandyPerformancePhase('Candy.SafeArea.OwnedMutationImmediate');
                          try {
                            const physicalPixels = Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                            const density = Number(globalThis.devicePixelRatio) || 1;
                            if (physicalPixels > 0) flushPendingOwnedLayoutMutation(physicalPixels / density);
                          } finally {
                            endCandyPerformancePhase('Candy.SafeArea.OwnedMutationImmediate', immediatePhase);
                          }
                        } else {
                          scheduleOwnedMutationLayoutCheck();
                        }
                      }
                    }
                  } finally {
                    endCandyPerformancePhase('Candy.SafeArea.Mutations', phase);
                  }
                });
                const originalAttachShadow = Element.prototype.attachShadow;
                let attachShadowHookActive = true;
                const attachShadowHook = function(options) {
                  const shadowRoot = Reflect.apply(originalAttachShadow, this, [options]);
                  if (attachShadowHookActive) {
                    observeOpenShadowRoot(shadowRoot);
                    if (this.isConnected) {
                      enqueuePrioritySubtree(this);
                      invalidatePointDiscovery();
                      scheduleDeferredLayoutCheck(true);
                    }
                  }
                  return shadowRoot;
                };
                try {
                  Element.prototype.attachShadow = attachShadowHook;
                } catch (_error) {
                  // Point discovery still observes visible open roots when prototypes are locked.
                }
                const restoreAttachShadowHook = () => {
                  attachShadowHookActive = false;
                  if (Element.prototype.attachShadow !== attachShadowHook) return;
                  try {
                    Element.prototype.attachShadow = originalAttachShadow;
                  } catch (_error) {
                    // A locked prototype is outside Candy's ownership.
                  }
                };
                observer.observe(root, observerOptions);
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
                  cancelOwnedMutationLayoutCheck();
                  invalidatePointDiscovery();
                  resumeLayoutRecovery();
                  const cssPixels = (Number(globalThis.$bridgeName?.topInsetPx?.()) || 0) /
                    (Number(globalThis.devicePixelRatio) || 1);
                  revalidateOwnedStickyAnchors(cssPixels);
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
                  restoreAttachShadowHook,
                  ownedOffsetElements,
                  ownedTranslateStates,
                  ownedStickyElements,
                  ownedCssStickyElements,
                  ownedOriginalTopStates,
                  stabilizationCheckTimers: [],
                  dispose: null,
                };
                runtimeState.dispose = () => {
                  cancelOwnedMutationLayoutCheck();
                  invalidatePointDiscovery();
                  pendingPriorityRoots.clear();
                  pointDiscoveryProgress = null;
                  pendingOwnedLayoutMutation = false;
                  priorityTraversal.length = 0;
                  observer.disconnect();
                  restoreAttachShadowHook();
                  globalThis.clearTimeout(deferredLayoutCheckTimer);
                  deferredLayoutCheckTimer = 0;
                  globalThis.cancelAnimationFrame(immediateLayoutCheckFrame);
                  immediateLayoutCheckFrame = 0;
                  globalThis.cancelAnimationFrame(scrollLayoutCheckFrame);
                  scrollLayoutCheckFrame = 0;
                  observeDiscoveredShadowRoot = () => {};
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
                      candidateDiscoveryNeeded = true;
                      deferredLayoutChecks = 0;
                      scheduleDeferredLayoutCheck(false);
                    }, delayMs));
                globalThis[stateKey] = runtimeState;
                pendingOwnedLayoutMutation = true;
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
