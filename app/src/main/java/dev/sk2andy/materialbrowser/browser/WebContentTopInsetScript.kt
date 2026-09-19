package dev.sk2andy.materialbrowser.browser

internal object WebContentTopInsetScript {
    const val bridgeName = "CandyContentTopInset"

    val installScript: String =
        """
            (() => {
              if (globalThis.$bridgeName?.nativeSafeAreaOnly?.()) return;
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
              const ownedJsStickyElements = globalThis[stateKey]?.ownedJsStickyElements ||
                new Set(Array.from(ownedStickyElements).filter((element) => !ownedCssStickyElements.has(element)));
              const ownedOriginalTopStates =
                globalThis[stateKey]?.ownedOriginalTopStates || new Map();
              const ownedStickyInlineTop = `var(${'$'}{stickyTopProperty}, 0px)`;
              let layoutWriteRevision = 0;
              let ownedLayoutWriteDepth = 0;
              let discoveryGeometryRevision = 0;
              let activePointDiscovery = null;
              let activeTopInsetProtection = null;
              let pointDiscoveryProgress = null;
              let pendingOwnedLayoutMutation = false;
              let ownedMutationLayoutFrame = 0;
              let ownedMutationLayoutRequest = null;
              let addedPriorityLayoutFrame = 0;
              let addedPriorityLayoutRequest = null;
              let addedPriorityLayoutWakeup = null;
              const pendingPriorityRoots = new Set();
              const addedPriorityWork = { roots: new Set(), recent: null, preferRecent: true };
              const attributePriorityWork = { roots: new Set(), recent: null, preferRecent: true };
              let priorityRootJobs = new WeakMap();
              let preferAddedPriorityWork = true;
              // Read epochs never survive a synchronous task or a Candy layout write.
              let layoutReadCache = null;
              const readViewportSize = () => {
                const cache = layoutReadCache;
                if (cache?.viewport) return cache.viewport;
                const revision = layoutWriteRevision;
                const viewport = { width: globalThis.innerWidth, height: globalThis.innerHeight };
                if (cache && layoutReadCache === cache && !cache.viewport && layoutWriteRevision === revision) {
                  cache.viewport = viewport;
                }
                return viewport;
              };
              const invalidateLayoutReadCache = () => {
                if (!layoutReadCache) return;
                // Header teardown/rebuild must not allocate unused collections on every write.
                layoutReadCache.styles = null;
                layoutReadCache.rects = null;
                layoutReadCache.parents = null;
                layoutReadCache.points = null;
              };
              const withLayoutReadCache = (callback) => {
                if (layoutReadCache) return callback();
                // Style-only policy/mutation checks must not force a layout viewport read.
                // Once needed, dimensions survive owned writes only, never a task/yield boundary.
                layoutReadCache = { viewport: null, styles: null, rects: null, parents: null, points: null };
                try {
                  return callback();
                } finally {
                  layoutReadCache = null;
                }
              };
              const readComputedStyle = (element, pseudo = null) => {
                const cache = layoutReadCache;
                if (!cache) return getComputedStyle(element, pseudo);
                const collection = cache.styles ||= new WeakMap();
                let styles = collection.get(element);
                if (!styles) {
                  styles = new Map();
                  collection.set(element, styles);
                }
                if (styles.has(pseudo)) return styles.get(pseudo);
                const value = getComputedStyle(element, pseudo);
                if (layoutReadCache === cache && cache.styles === collection &&
                    collection.get(element) === styles && !styles.has(pseudo)) styles.set(pseudo, value);
                return value;
              };
              const readElementRect = (element) => {
                const cache = layoutReadCache;
                if (!cache) return element.getBoundingClientRect();
                const rects = cache.rects ||= new WeakMap();
                if (rects.has(element)) return rects.get(element);
                const value = element.getBoundingClientRect();
                if (layoutReadCache === cache && cache.rects === rects && !rects.has(element)) rects.set(element, value);
                return value;
              };
              const noteOwnedLayoutWrite = () => {
                layoutWriteRevision++;
                invalidateLayoutReadCache();
              };
              const withOwnedLayoutWrite = (write) => {
                noteOwnedLayoutWrite();
                ownedLayoutWriteDepth++;
                try {
                  return write();
                } finally {
                  ownedLayoutWriteDepth--;
                  // Synchronous author reactions may read, then reparent during a DOM setter.
                  invalidateLayoutReadCache();
                }
              };
              const setOwnedProperty = (element, name, value) => {
                if (element.style.getPropertyValue(name) === value &&
                    element.style.getPropertyPriority(name) === 'important') return;
                withOwnedLayoutWrite(() => element.style.setProperty(name, value, 'important'));
              };
              const setOwnedAttribute = (element, name, value) => {
                if (element.getAttribute(name) === value) return;
                withOwnedLayoutWrite(() => element.setAttribute(name, value));
              };
              const removeOwnedProperty = (element, name) => {
                if (!element.style.getPropertyValue(name)) return;
                withOwnedLayoutWrite(() => element.style.removeProperty(name));
              };
              const removeOwnedAttribute = (element, name) => {
                if (!element.hasAttribute(name)) return;
                withOwnedLayoutWrite(() => element.removeAttribute(name));
              };
              let candidateDiscoveryNeeded = true;
              let candidateDiscoveryPolicyKey = null;
              let deferredLayoutChecks = 0;
              let deferredLayoutCheckTimer = 0;
              let deferredLayoutCheckRequest = null;
              let immediateLayoutCheckFrame = 0;
              let consecutiveLayoutFailures = 0;
              let activePolicyKey = null;
              let suspendedLayoutRecoveryKey = null;
              let localOffsetCollisionDetected = false;
              let nativeFallbackRequested = false;
              let nativeTopHeaderRequested = false;
              let fixedTopHeaderCandidates = new Map();
              let scrollLayoutCheckFrame = 0;
              let quietLayoutCheckFrame = 0;
              let quietLayoutCheckGeneration = 0;
              let lastScrollAt = null;
              let scrollGeneration = 0;
              let scrollVerificationTimer = 0;
              let scrollVerificationFailures = 0;
              let scrollVerificationPolicyKey = null;
              let observeDiscoveredShadowRoot = () => {};
              const maximumFixedTopHeaderCandidates = 8;
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
                const mark = `${'$'}{name}.start`;
                try {
                  // The legacy two-argument overload ends at the current timestamp.
                  globalThis.performance.measure(name, mark);
                } catch (_error) {
                  // Diagnostics must never change page protection or its exception behavior.
                } finally {
                  try { globalThis.performance.clearMarks(mark); } catch (_error) {}
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
              const pointDiscoverySignature = () => {
                const viewport = readViewportSize();
                return [
                currentPolicyKey(),
                Number(globalThis.$bridgeName?.topInsetPx?.()) || 0,
                Number(globalThis.devicePixelRatio) || 1,
                viewport.width,
                viewport.height,
                globalThis.scrollX,
                globalThis.scrollY,
                discoveryGeometryRevision,
                layoutWriteRevision,
                ].join(':');
              };
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
              const scanInsetPoints = (root, cssPixels, visit, complete, restart = () => {}, runImmediately = false,
                                       discoverSticky = false) => {
                const viewport = readViewportSize();
                const xs = prioritizedDiscoveryAxis(readViewportSize().width);
                const rows = sampleAxis(cssPixels);
                // Tiny controls can straddle the bottom inset edge without occupying early rows.
                const ys = Array.from(new Set([rows.at(-1), ...rows]))
                  .filter((value) => value !== undefined);
                const gridKey = [
                  currentPolicyKey(),
                  Number(globalThis.$bridgeName?.topInsetPx?.()) || 0,
                  globalThis.devicePixelRatio,
                  readViewportSize().width,
                  readViewportSize().height,
                  cssPixels,
                  xs.join(','),
                  ys.join(','),
                ].join(':');
                if (pointDiscoveryProgress?.root !== root || pointDiscoveryProgress.key !== gridKey) {
                  pointDiscoveryProgress = { root, key: gridKey, cursor: 0, seedTurn: true,
                    stickyCursor: 0, stickyCountdown: maxDiscoveryPointsPerTask - 1 };
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
                  stickyXs: Array.from(new Set([viewport.width * 0.5, 1, viewport.width * 0.25,
                    viewport.width * 0.75, viewport.width - 1]))
                    .filter((value) => value >= 0 && value < viewport.width),
                  // Dense points already discover sticky headers inside the strip. Only the
                  // just-below-inset row needs extra samples to anchor an initially unstuck header.
                  stickyYs: [Math.min(viewport.height - 1, cssPixels + 1)]
                    .filter((value) => value >= 0 && value < viewport.height),
                  stickyCursor: pointDiscoveryProgress.stickyCursor,
                  stickyScanned: 0,
                  stickyCountdown: pointDiscoveryProgress.stickyCountdown,
                  timer: 0,
                };
                const totalPoints = xs.length * ys.length;
                const totalStickyPoints = discoverSticky ? job.stickyXs.length * job.stickyYs.length : 0;
                const seedCount = Math.min(6, xs.length);
                activePointDiscovery = job;
                const runChunk = () => {
                  if (activePointDiscovery !== job) return;
                  if (document.documentElement !== root) {
                    cancelPointDiscovery();
                    return;
                  }
                  job.timer = 0;
                  const phase = beginCandyPerformancePhase('Candy.SafeArea.DiscoveryChunk');
                  try {
                    withLayoutReadCache(() => {
                      const startedAt = discoveryNow();
                      // Validate with this packet's fresh viewport dimensions; account the native
                      // read in the same budget before adding priority or hit-test work.
                      if (job.signature !== pointDiscoverySignature()) {
                        cancelPointDiscovery();
                        return;
                      }
                      const hasPriorityBudget = discoveryNow() - startedAt < discoveryTaskBudgetMillis;
                      if (hasPriorityBudget && refreshPriorityCandidates(root, cssPixels)) {
                        restart();
                        job.signature = pointDiscoverySignature();
                        job.scanned = 0;
                        job.stickyScanned = 0;
                        if (activeTopInsetProtection) activeTopInsetProtection.signature = job.signature;
                      }
                      if (hasPriorityBudget && discoveryNow() - startedAt >= discoveryTaskBudgetMillis) {
                        job.timer = globalThis.setTimeout(runChunk, 0);
                        return;
                      }
                      for (let count = 0; count < maxDiscoveryPointsPerTask &&
                           (job.scanned < totalPoints || job.stickyScanned < totalStickyPoints); count++) {
                        if (count > 0 && discoveryNow() - startedAt >= discoveryTaskBudgetMillis) break;
                        // Interleave sticky bootstrap with seeds/raster. A cancelled slow header
                        // query must not starve the dense trailing row or retain old safety proofs.
                        const isSeed = job.seedTurn && job.seed < seedCount;
                        if (job.stickyScanned < totalStickyPoints &&
                            (isSeed && job.stickyCountdown <= 0 || job.scanned >= totalPoints)) {
                          // Delay a seed, never discard it or steal a dense coverage/progress slot.
                          if (isSeed) {
                            job.seedTurn = false;
                            job.progress.seedTurn = false;
                          }
                          const point = job.stickyCursor;
                          job.stickyCursor = (point + 1) % totalStickyPoints;
                          job.progress.stickyCursor = job.stickyCursor;
                          job.stickyCountdown = maxDiscoveryPointsPerTask - 1;
                          job.progress.stickyCountdown = job.stickyCountdown;
                          const x = job.stickyXs[point % job.stickyXs.length];
                          const y = job.stickyYs[Math.floor(point / job.stickyXs.length)];
                          if (protectDiscoveredStickyElements(deepElementsFromPoint(x, y), root, cssPixels)) {
                            restart();
                            job.signature = pointDiscoverySignature();
                            job.scanned = 0;
                            job.stickyScanned = 0;
                            if (activeTopInsetProtection) activeTopInsetProtection.signature = job.signature;
                          } else {
                            job.stickyScanned++;
                          }
                          continue;
                        }
                        job.stickyCountdown--;
                        job.progress.stickyCountdown = job.stickyCountdown;
                        // Seeds keep common controls prompt; interleaving prevents a slow seed barrier.
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
                        const elements = deepElementsFromPoint(x, y);
                        if (discoverSticky && protectDiscoveredStickyElements(elements, root, cssPixels)) {
                          restart();
                          job.signature = pointDiscoverySignature();
                          job.scanned = 0;
                          job.stickyScanned = 0;
                          if (activeTopInsetProtection) activeTopInsetProtection.signature = job.signature;
                          continue;
                        }
                        const result = visit(elements);
                        if (result === 'restart') {
                          job.signature = pointDiscoverySignature();
                          job.scanned = 0;
                          job.stickyScanned = 0;
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
                      if (job.scanned >= totalPoints && job.stickyScanned >= totalStickyPoints) {
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
                if (activePolicyKey !== policyKey) fixedTopHeaderCandidates = new Map();
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
                  withOwnedLayoutWrite(() => element.style.setProperty(
                    'translate',
                    translateState.value,
                    translateState.priority,
                  ));
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
                    withOwnedLayoutWrite(() => element.style.setProperty('top', topState.value, topState.priority));
                  } else {
                    removeOwnedProperty(element, 'top');
                  }
                }
                ownedOriginalTopStates.delete(element);
                ownedCssStickyElements.delete(element);
                ownedJsStickyElements.delete(element);
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
                globalThis.$bridgeName?.fallbackToNative?.(generation, revision, null, false);
              };
              const suspendLayoutRecovery = (reason = 'unknown') => {
                if (document.documentElement) {
                  setOwnedAttribute(document.documentElement, 'data-candy-browser-top-inset-failure', reason);
                }
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
              const parentElementOrShadowHost = (element) => {
                const cache = layoutReadCache;
                if (!element || !cache) return element?.parentElement || element?.getRootNode?.()?.host || null;
                const parents = cache.parents ||= new WeakMap();
                if (parents.has(element)) return parents.get(element);
                const value = element.parentElement || element.getRootNode?.()?.host || null;
                if (layoutReadCache === cache && cache.parents === parents && !parents.has(element)) parents.set(element, value);
                return value;
              };
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
                const cache = layoutReadCache;
                const points = cache ? (cache.points ||= new Map()) : null;
                if (points?.has(pointKey)) return points.get(pointKey);
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
                    let shadowRoot = null;
                    for (const element of hitElements) {
                      const candidate = element.shadowRoot;
                      if (candidate && !visitedRoots.has(candidate)) {
                        shadowRoot = candidate;
                        break;
                      }
                    }
                    if (!shadowRoot) break;
                    observeDiscoveredShadowRoot(shadowRoot);
                    scope = shadowRoot;
                  }
                  const elements = [];
                  const visitedElements = new Set();
                  for (let layer = layers.length - 1; layer >= 0; layer--) {
                    for (const element of layers[layer]) {
                      if (!visitedElements.has(element)) {
                        visitedElements.add(element);
                        elements.push(element);
                      }
                    }
                  }
                  if (points && layoutReadCache === cache && cache.points === points && !points.has(pointKey)) {
                    points.set(pointKey, elements);
                  }
                  return elements;
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.PointDiscovery', phase);
                }
              };
              const isVisiblePositionedElement = (element) => {
                const style = readComputedStyle(element);
                if (style.display === 'none' || style.visibility === 'hidden' ||
                    style.visibility === 'collapse' || !(Number.parseFloat(style.opacity) > 0.01)) return false;
                const rect = readElementRect(element);
                return rect.width > 1 && rect.height > 1;
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
                if (ownedJsStickyElements.size === 0) return;
                const phase = beginCandyPerformancePhase('Candy.SafeArea.KnownSticky');
                try {
                    refreshStickyElements(Array.from(ownedJsStickyElements), cssPixels);
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
                  // A declassified header needs cleanup, not an explicit resolved top getter.
                  if (style.position !== 'sticky') continue;
                  const originalTop = Number.parseFloat(style.top);
                  if (!Number.isFinite(originalTop) || originalTop < -0.5) continue;
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
                  ownedJsStickyElements.delete(element);
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
                ownedJsStickyElements.add(element);
              };
              const protectStickyTopAnchors = (_root, cssPixels, refreshOwned = true) => withLayoutReadCache(() => {
                // Unknown headers share the bounded discovery job, not a second synchronous grid.
                // Already-owned nested/moving anchors still repair in sequential fresh read epochs.
                if (candidateDiscoveryNeeded && refreshOwned) refreshOwnedStickyElements(cssPixels);
              });
              const protectDiscoveredStickyElements = (elements, root, cssPixels) => {
                const revision = layoutWriteRevision;
                for (const element of elements) {
                  for (let current = element; current && current !== root;
                       current = parentElementOrShadowHost(current)) {
                    const style = readComputedStyle(current);
                    if (!['fixed', 'sticky', 'absolute'].includes(style.position)) continue;
                    // Absolute children remain positioned inside their enclosing sticky header.
                    if (style.position === 'absolute') continue;
                    if (style.position !== 'sticky') break;
                    if (ownedStickyElements.has(current)) break;
                    const originalTop = Number.parseFloat(style.top);
                    // CSS-sticky registration does not need a rectangle, even offscreen. Nested
                    // scrollports retain applyStickyTopAnchor's sequential geometry-based path.
                    if (Number.isFinite(originalTop) && originalTop >= -0.5 &&
                        style.display !== 'none' && style.visibility !== 'hidden' &&
                        style.visibility !== 'collapse' && Number.parseFloat(style.opacity) > 0.01) {
                      if (requestNativeFallbackForTopHeader(current, style)) return true;
                      applyStickyTopAnchor(current, originalTop, cssPixels);
                    }
                    break;
                  }
                }
                return layoutWriteRevision !== revision;
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
                const isViewportWide = rect.width >= readViewportSize().width * 0.8;
                const isViewportTall = rect.height >= readViewportSize().height * 0.8;
                if (isViewportWide && isViewportTall) {
                  return null;
                }
                if (
                  (style.position === 'fixed' || style.position === 'sticky') &&
                  requestNativeFallbackForTopHeader(element, style)
                ) {
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
                let panelMaxHeight = null;
                if (isFixedPanel) {
                  const computedHeight = Number.parseFloat(style.height);
                  const boxExtras = Number.isFinite(computedHeight)
                    ? Math.max(0, rect.height - computedHeight)
                    : 0;
                  panelMaxHeight = Math.max(0, rect.height + previousOffset - offset - boxExtras);
                }
                return {
                  element,
                  position: style.position,
                  isCompactInteractive,
                  minimumTop,
                  offset,
                  panelMaxHeight,
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
                    (plan.panelMaxHeight === null || rect.bottom <= readViewportSize().height + 0.5);
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
              const removePriorityRoot = (element) => {
                const job = priorityRootJobs.get(element);
                if (job) {
                  job.work.roots.delete(element);
                  if (job.work.recent === element) job.work.recent = null;
                }
                pendingPriorityRoots.delete(element);
                priorityRootJobs.delete(element);
              };
              const enqueuePrioritySubtree = (element, newlyAdded = true) => {
                if (!(element instanceof Element)) return;
                const work = newlyAdded ? addedPriorityWork : attributePriorityWork;
                const job = priorityRootJobs.get(element);
                if (job) {
                  // Finish the current identity-only walk; author changes require a fresh follow-up.
                  if (job.started) job.dirty = true;
                  if (newlyAdded && job.work !== addedPriorityWork) {
                    job.work.roots.delete(element);
                    if (job.work.recent === element) job.work.recent = null;
                    job.work = addedPriorityWork;
                    addedPriorityWork.roots.add(element);
                    addedPriorityWork.recent = element;
                  }
                } else {
                  priorityRootJobs.set(element, { work, started: false, dirty: false,
                    traversal: [{ node: element, entered: false, child: null, shadowVisited: false }] });
                  pendingPriorityRoots.add(element);
                  work.roots.add(element);
                  work.recent = element;
                }
                if (pendingPriorityRoots.size > maxPendingPriorityRoots) {
                  // An attribute flood must not discard the other lane's waiting added controls.
                  // Priority remains best-effort; only full fresh discovery can prove protection.
                  const other = work === addedPriorityWork ? attributePriorityWork : addedPriorityWork;
                  const evictionWork = work.roots.size > 1 ? work : other;
                  removePriorityRoot(evictionWork.roots.values().next().value);
                }
              };
              const advancePriorityJob = (job, startedAt) => {
                const traversal = job.traversal;
                while (traversal.length > 0) {
                  if (discoveryNow() - startedAt >= discoveryTaskBudgetMillis) return null;
                  const frame = traversal.at(-1);
                  if ((frame.node instanceof Element && !frame.node.isConnected) ||
                      (frame.node.host && !frame.node.host.isConnected) ||
                      (frame.parent && frame.node.parentNode !== frame.parent)) {
                    job.dirty = true;
                    traversal.pop();
                    continue;
                  }
                  if (!frame.entered) {
                    job.started = true;
                    frame.entered = true;
                    frame.child = frame.node.firstElementChild;
                    if (frame.node instanceof Element) return frame.node;
                  }
                  if (frame.child) {
                    const node = frame.child;
                    if (node.parentNode !== frame.node) {
                      // A removed cursor no longer exposes the original following siblings.
                      job.dirty = true;
                      frame.child = null;
                      continue;
                    }
                    frame.child = node.nextElementSibling;
                    traversal.push({ node, parent: frame.node, entered: false, child: null, shadowVisited: false });
                    continue;
                  }
                  if (!frame.shadowVisited) {
                    frame.shadowVisited = true;
                    const shadowRoot = frame.node.shadowRoot;
                    if (shadowRoot?.mode === 'open') {
                      observeDiscoveredShadowRoot(shadowRoot);
                      traversal.push({ node: shadowRoot, entered: false, child: null, shadowVisited: true });
                      continue;
                    }
                  }
                  traversal.pop();
                }
                return null;
              };
              const nextPriorityElement = (startedAt = discoveryNow(), newlyAddedOnly = false) => {
                // Finished jobs do not each impose a timer yield, but transitions remain bounded.
                for (let transition = 0; transition < maxDiscoveryPointsPerTask; transition++) {
                  if (discoveryNow() - startedAt >= discoveryTaskBudgetMillis) break;
                  const first = preferAddedPriorityWork ? addedPriorityWork : attributePriorityWork;
                  const second = preferAddedPriorityWork ? attributePriorityWork : addedPriorityWork;
                  preferAddedPriorityWork = !preferAddedPriorityWork;
                  const work = newlyAddedOnly ? addedPriorityWork : first.roots.size > 0 ? first : second;
                  if (work.roots.size === 0) break;
                  const root = work.preferRecent && work.roots.has(work.recent)
                    ? work.recent : work.roots.values().next().value;
                  work.preferRecent = !work.preferRecent;
                  const job = priorityRootJobs.get(root);
                  const element = advancePriorityJob(job, startedAt);
                  if (priorityRootJobs.get(root) !== job || !work.roots.has(root)) continue;
                  if (element) {
                    // Recent new roots stay prompt; alternating FIFO slots advance older cursors.
                    work.roots.delete(root);
                    work.roots.add(root);
                    pendingPriorityRoots.delete(root);
                    pendingPriorityRoots.add(root);
                    return element;
                  }
                  if (job.traversal.length > 0) return null;
                  const needsFollowUp = job.dirty && root.isConnected;
                  if (priorityRootJobs.get(root) !== job) continue;
                  if (needsFollowUp) {
                    job.started = false;
                    job.dirty = false;
                    job.traversal = [{ node: root, entered: false, child: null, shadowVisited: false }];
                    work.roots.delete(root);
                    work.roots.add(root);
                    pendingPriorityRoots.delete(root);
                    pendingPriorityRoots.add(root);
                    if (work.recent === root) work.recent = null;
                  } else {
                    removePriorityRoot(root);
                  }
                }
                return null;
              };
              const protectPriorityPositionedElement = (element, cssPixels, allowAbsolute = false, isCurrent = null) => {
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
                if (isCurrent && !isCurrent()) return false;
                return applyLocalOffsetPlans([plan]);
              };
              const refreshPriorityCandidates = (root, cssPixels, newlyAddedOnly = false, isCurrent = null) => {
                const revision = layoutWriteRevision;
                const startedAt = discoveryNow();
                for (let count = 0; count < maxDiscoveryPointsPerTask; count++) {
                  if (isCurrent && !isCurrent()) break;
                  const element = nextPriorityElement(startedAt, newlyAddedOnly);
                  if (!element) break;
                  if (isCurrent && !isCurrent()) {
                    if (newlyAddedOnly) enqueuePrioritySubtree(element);
                    break;
                  }
                  const style = readComputedStyle(element);
                  if (isCurrent && !isCurrent()) {
                    if (newlyAddedOnly) enqueuePrioritySubtree(element);
                    break;
                  }
                  if (style.position === 'sticky' && !ownedStickyElements.has(element)) {
                    const originalTop = Number.parseFloat(style.top);
                    if (Number.isFinite(originalTop) && originalTop >= -0.5 && canUseCssStickyAnchor(element, root)) {
                      if (isCurrent && !isCurrent()) {
                        if (newlyAddedOnly) enqueuePrioritySubtree(element);
                        break;
                      }
                      applyStickyTopAnchor(element, originalTop, cssPixels);
                    }
                  } else if (style.position === 'fixed') {
                    protectPriorityPositionedElement(element, cssPixels, false, isCurrent);
                  }
                  if (isCurrent && !isCurrent()) {
                    if (newlyAddedOnly) enqueuePrioritySubtree(element);
                    break;
                  }
                  if (discoveryNow() - startedAt >= discoveryTaskBudgetMillis) break;
                }
                return layoutWriteRevision !== revision;
              };
              const cancelAddedPriorityLayoutCheck = () => {
                globalThis.cancelAnimationFrame(addedPriorityLayoutFrame);
                addedPriorityLayoutFrame = 0;
                addedPriorityLayoutRequest = null;
                addedPriorityLayoutWakeup = null;
              };
              const scheduleAddedPriorityLayoutCheck = () => {
                if (addedPriorityWork.roots.size === 0) return;
                const request = { root: document.documentElement, policyKey: currentPolicyKey(),
                  scrollGeneration };
                if (!request.root) return;
                addedPriorityLayoutRequest = request;
                // Replace execution state, but keep a queued wakeup: author-frame scrolling must
                // not cancel the only opportunity to protect newly inserted controls indefinitely.
                if (addedPriorityLayoutWakeup) return;
                const wakeup = {};
                addedPriorityLayoutWakeup = wakeup;
                addedPriorityLayoutFrame = globalThis.requestAnimationFrame(() => {
                  if (addedPriorityLayoutWakeup !== wakeup) return;
                  addedPriorityLayoutFrame = 0;
                  addedPriorityLayoutWakeup = null;
                  const request = addedPriorityLayoutRequest;
                  addedPriorityLayoutRequest = null;
                  if (!request) return;
                  const isCurrent = () => request.root === document.documentElement &&
                    request.policyKey === currentPolicyKey() && request.scrollGeneration === scrollGeneration;
                  if (!isCurrent() || !document.body) return;
                  const phase = beginCandyPerformancePhase('Candy.SafeArea.AddedPriority');
                  let continueAddedWork = false;
                  try {
                    withLayoutReadCache(() => {
                      const physicalPixels = Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                      if (physicalPixels <= 0 || !isCurrent()) return;
                      const density = Number(globalThis.devicePixelRatio) || 1;
                      // New controls cannot wait through an uninterrupted fling. Do not drain the
                      // potentially large attribute lane or claim completed global protection here.
                      refreshPriorityCandidates(request.root, physicalPixels / density, true, isCurrent);
                      continueAddedWork = true;
                    });
                  } finally {
                    endCandyPerformancePhase('Candy.SafeArea.AddedPriority', phase);
                    if (continueAddedWork && isCurrent()) scheduleAddedPriorityLayoutCheck();
                  }
                });
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
                  if (style.display === 'none' || style.visibility === 'hidden' ||
                      style.visibility === 'collapse' || !(Number.parseFloat(style.opacity) > 0.01)) continue;
                  const rect = readElementRect(current);
                  if (
                    rect.width >= readViewportSize().width * 0.8 &&
                    rect.height > 1 &&
                    rect.height < readViewportSize().height * 0.5
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
                    Math.min(readViewportSize().width - 1, rect.right - 1),
                  ].filter((value) => value >= 0 && value < readViewportSize().width);
                  const yCoordinates = [
                    Math.max(1, rect.top + 1),
                    Math.max(1, (rect.top + rect.bottom) / 2),
                    Math.max(1, rect.bottom - 1),
                  ].filter((value) => value < readViewportSize().height);
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
                        peerRect.width >= readViewportSize().width * 0.8;
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
                  rect.width < readViewportSize().width * 0.8 ||
                  rect.height < readViewportSize().height * 0.8 ||
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
                  return candidateRect.width < readViewportSize().width * 0.8 &&
                    candidateRect.height >= readViewportSize().height * 0.8 &&
                    candidateRect.right > 0 && candidateRect.left < readViewportSize().width &&
                    candidateRect.bottom > 0 && candidateRect.top < readViewportSize().height &&
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
                            candidateRect.width >= readViewportSize().width * 0.8 &&
                            candidateRect.height >= readViewportSize().height * 0.8;
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
                  }, runImmediately, true);
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
                  for (const x of sampleAxis(readViewportSize().width)) {
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
              const cancelQuietLayoutCheck = () => {
                quietLayoutCheckGeneration++;
                globalThis.clearTimeout(scrollVerificationTimer);
                scrollVerificationTimer = 0;
                globalThis.cancelAnimationFrame(quietLayoutCheckFrame);
                quietLayoutCheckFrame = 0;
              };
              const cancelDeferredLayoutCheck = () => {
                globalThis.clearTimeout(deferredLayoutCheckTimer);
                deferredLayoutCheckTimer = 0;
                deferredLayoutCheckRequest = null;
              };
              const scheduleScrollVerification = (root, policyKey, generation, reopenDiscovery = false) => {
                if (deferredLayoutCheckRequest || generation !== scrollGeneration ||
                    root !== document.documentElement || policyKey !== currentPolicyKey()) return;
                if (reopenDiscovery) candidateDiscoveryNeeded = true;
                cancelQuietLayoutCheck();
                const quietGeneration = quietLayoutCheckGeneration;
                scrollVerificationTimer = globalThis.setTimeout(() => {
                  if (quietGeneration !== quietLayoutCheckGeneration ||
                      deferredLayoutCheckRequest || generation !== scrollGeneration ||
                      root !== document.documentElement || policyKey !== currentPolicyKey()) return;
                  scrollVerificationTimer = 0;
                  verifyLateTopInset();
                }, layoutQuietPeriodMs());
              };
              const armDeferredLayoutCheck = (request) => {
                globalThis.clearTimeout(deferredLayoutCheckTimer);
                // A new identity also rejects an already-queued callback from the cancelled timer.
                const scheduledRequest = { ...request, scrollGeneration };
                deferredLayoutCheckRequest = scheduledRequest;
                const deadline = Math.max(
                  scheduledRequest.notBefore,
                  lastScrollAt === null ? 0 : lastScrollAt + layoutQuietPeriodMs(),
                );
                deferredLayoutCheckTimer = globalThis.setTimeout(() => {
                  if (deferredLayoutCheckRequest !== scheduledRequest) return;
                  deferredLayoutCheckTimer = 0;
                  if (scheduledRequest.root !== document.documentElement ||
                      scheduledRequest.policyKey !== currentPolicyKey()) {
                    deferredLayoutCheckRequest = null;
                    return;
                  }
                  const remaining = Math.max(
                    scheduledRequest.notBefore,
                    lastScrollAt === null ? 0 : lastScrollAt + layoutQuietPeriodMs(),
                  ) - discoveryNow();
                  if (remaining > 0) {
                    armDeferredLayoutCheck(scheduledRequest);
                    return;
                  }
                  // Consume before effects: synchronous author reactions may queue a newer request.
                  deferredLayoutCheckRequest = null;
                  let recoveryCompleted = false;
                  try {
                    if (document.readyState === 'loading' ||
                        layoutRecoverySuspendedForCurrentPolicy() ||
                        deferredLayoutChecks >= maxDeferredLayoutChecks) return;
                    if (scheduledRequest.resetFailures) candidateDiscoveryNeeded = true;
                    reconcile();
                    recoveryCompleted = true;
                  } finally {
                    // Merging recovery must not discard the independent emergency verification.
                    if (scheduledRequest.scrollGeneration > 0) {
                      scheduleScrollVerification(
                        scheduledRequest.root,
                        scheduledRequest.policyKey,
                        scheduledRequest.scrollGeneration,
                        !recoveryCompleted,
                      );
                    }
                  }
                }, Math.max(0, deadline - discoveryNow()));
              };
              const scheduleDeferredLayoutCheck = (resetFailures = true) => {
                if (
                  document.readyState === 'loading' ||
                  layoutRecoverySuspendedForCurrentPolicy() ||
                  deferredLayoutChecks >= maxDeferredLayoutChecks
                ) {
                  return;
                }
                const root = document.documentElement;
                if (!root) return;
                const policyKey = currentPolicyKey();
                const previousRequest = deferredLayoutCheckRequest;
                if (resetFailures) consecutiveLayoutFailures = 0;
                cancelQuietLayoutCheck();
                armDeferredLayoutCheck({
                  root,
                  policyKey,
                  resetFailures: resetFailures || Boolean(previousRequest &&
                    previousRequest.root === root && previousRequest.policyKey === policyKey &&
                    previousRequest.resetFailures),
                  notBefore: discoveryNow() + layoutQuietPeriodMs(),
                });
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
                  const policyKey = currentPolicyKey();
                  const generation = scrollGeneration;
                  const quietGeneration = quietLayoutCheckGeneration;
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
                    scheduleScrollVerification(root, policyKey, generation);
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
                          candidateRect.width >= readViewportSize().width * 0.8 &&
                          candidateRect.height >= readViewportSize().height * 0.8;
                        if (coversViewport) continue;
                        return false;
                      }
                      return true;
                  }, (protectedTop) => {
                        if (quietGeneration !== quietLayoutCheckGeneration ||
                            generation !== scrollGeneration || deferredLayoutCheckRequest ||
                            root !== document.documentElement || policyKey !== currentPolicyKey()) return;
                        if (protectedTop) {
                          scrollVerificationFailures = 0;
                          scrollVerificationPolicyKey = currentPolicyKey();
                          return;
                        }
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
                          scheduleScrollVerification(root, policyKey, generation);
                        }
                  });
                } finally {
                  endCandyPerformancePhase('Candy.SafeArea.QuietVerification', phase);
                }
              });
              const windowScrollListener = () => {
                lastScrollAt = discoveryNow();
                scrollGeneration++;
                scheduleAddedPriorityLayoutCheck();
                invalidatePointDiscovery();
                scrollVerificationFailures = 0;
                scrollVerificationPolicyKey = currentPolicyKey();
                cancelQuietLayoutCheck();
                if (scrollLayoutCheckFrame) {
                  globalThis.cancelAnimationFrame(scrollLayoutCheckFrame);
                  scrollLayoutCheckFrame = 0;
                }
                // A synchronous author reaction may scroll before a new JS anchor is registered.
                if (ownedJsStickyElements.size > 0 || ownedLayoutWriteDepth > 0) {
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
                }
                const root = document.documentElement;
                const policyKey = currentPolicyKey();
                if (deferredLayoutCheckRequest) {
                  if (deferredLayoutCheckRequest.root === root &&
                      deferredLayoutCheckRequest.policyKey === policyKey) {
                    armDeferredLayoutCheck(deferredLayoutCheckRequest);
                    return;
                  }
                  cancelDeferredLayoutCheck();
                }
                const generation = scrollGeneration;
                const quietGeneration = quietLayoutCheckGeneration;
                scrollVerificationTimer = globalThis.setTimeout(
                  () => {
                    if (quietGeneration !== quietLayoutCheckGeneration ||
                        generation !== scrollGeneration || deferredLayoutCheckRequest ||
                        root !== document.documentElement || policyKey !== currentPolicyKey()) return;
                    scrollVerificationTimer = 0;
                    // A previously offscreen sticky header can become active without a mutation.
                    candidateDiscoveryNeeded = true;
                    quietLayoutCheckFrame = globalThis.requestAnimationFrame(() => {
                      if (quietGeneration !== quietLayoutCheckGeneration ||
                          generation !== scrollGeneration || deferredLayoutCheckRequest ||
                          root !== document.documentElement || policyKey !== currentPolicyKey()) return;
                      quietLayoutCheckFrame = 0;
                      if (withLayoutReadCache(verifyFixedTopHeaderCandidates)) return;
                      const physicalPixels = Number(
                        globalThis.$bridgeName?.topInsetPx?.(),
                      ) || 0;
                      if (root && physicalPixels > 0) {
                        const density = Number(globalThis.devicePixelRatio) || 1;
                        protectStickyTopAnchors(root, physicalPixels / density);
                      }
                      protectLateTopInset();
                      scheduleScrollVerification(root, policyKey, generation);
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
                scrollGeneration++;
                cancelAddedPriorityLayoutCheck();
                cancelOwnedMutationLayoutCheck();
                invalidatePointDiscovery();
                cancelDeferredLayoutCheck();
                cancelQuietLayoutCheck();
                resetFailuresForPolicy(currentPolicyKey(), true);
                pendingOwnedLayoutMutation = true;
                reconcile();
              };
              const isTransparentColor = (color) =>
                !color || color === 'transparent' ||
                (color.startsWith('rgba(') &&
                  Number.parseFloat(color.slice(color.lastIndexOf(',') + 1)) === 0);
              const normalizedOpaqueColor = (value) => {
                if (typeof value !== 'string') return null;
                const color = value.trim().toLowerCase();
                const shortHex = /^#([0-9a-f]{3})$/.exec(color);
                if (shortHex) {
                  return `#${'$'}{[...shortHex[1]].map((channel) => channel + channel).join('')}`;
                }
                if (/^#[0-9a-f]{6}$/.test(color)) return color;
                const rgb = /^rgba?\(\s*([\d.]+)(?:\s*,\s*|\s+)([\d.]+)(?:\s*,\s*|\s+)([\d.]+)(?:\s*[,/]\s*([\d.]+)(%)?)?\s*\)$/
                  .exec(color);
                if (!rgb) return null;
                if (
                  rgb[4] !== undefined &&
                  (rgb[5] ? Number(rgb[4]) < 100 : Number(rgb[4]) < 1)
                ) {
                  return null;
                }
                const values = rgb.slice(1, 4).map(Number);
                if (values.some((channel) => !Number.isFinite(channel))) return null;
                return `#${'$'}{values.map((channel) =>
                  Math.min(255, Math.max(0, Math.round(channel)))
                    .toString(16)
                    .padStart(2, '0')).join('')}`;
              };
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
                    const normalized = normalizedOpaqueColor(color);
                    if (normalized) return normalized;
                  }
                }
                return null;
              };
              const authorDeclaresViewportCover = () => {
                const content = document.querySelector('meta[name="viewport" i]')
                  ?.getAttribute('content');
                return typeof content === 'string' &&
                  /(?:^|[\s,;])viewport-fit\s*=\s*cover(?=${'$'}|[\s,;])/i.test(content);
              };
              const nextTopHeaderContentAnchor = (element) => {
                for (
                  let current = element;
                  current && current !== document.body && current !== document.documentElement;
                  current = parentElementOrShadowHost(current)
                ) {
                  for (
                    let sibling = current.nextElementSibling;
                    sibling;
                    sibling = sibling.nextElementSibling
                  ) {
                    if (!['link', 'meta', 'script', 'style'].includes(sibling.localName)) {
                      return sibling;
                    }
                  }
                }
                return null;
              };
              const rememberFixedTopHeaderCandidate = (element, rect, scrollY, cssPixels) => {
                if (
                  !fixedTopHeaderCandidates.has(element) &&
                  fixedTopHeaderCandidates.size >= maximumFixedTopHeaderCandidates
                ) {
                  return null;
                }
                const anchor = nextTopHeaderContentAnchor(element);
                const candidate = {
                  anchor,
                  anchorTop: anchor ? readElementRect(anchor).top : null,
                  minimumScrollDelta: Math.max(rect.height, cssPixels),
                  scrollGeneration,
                  scrollY,
                };
                fixedTopHeaderCandidates.set(element, candidate);
                return candidate;
              };
              const fixedTopHeaderHasMeaningfulScroll = (candidate, scrollY) => {
                if (scrollGeneration <= candidate.scrollGeneration) return false;
                if (scrollY - candidate.scrollY >= candidate.minimumScrollDelta) return true;
                if (!candidate.anchor?.isConnected || !Number.isFinite(candidate.anchorTop)) {
                  return false;
                }
                return candidate.anchorTop - readElementRect(candidate.anchor).top >=
                  candidate.minimumScrollDelta;
              };
              const requestNativeFallbackForTopHeader = (element, style) => {
                if (nativeTopHeaderRequested) return true;
                if (globalThis.$bridgeName?.nativeTopHeaderEnabled?.() !== true) return false;
                if (authorDeclaresViewportCover()) return false;
                if (style.position !== 'fixed' && style.position !== 'sticky') return false;
                const opacity = Number.parseFloat(style.opacity);
                if (
                  style.display === 'none' ||
                  style.visibility === 'hidden' ||
                  style.visibility === 'collapse' ||
                  (Number.isFinite(opacity) && opacity <= 0.01)
                ) {
                  if (style.position === 'fixed') fixedTopHeaderCandidates.delete(element);
                  return false;
                }
                const originalTop = Number.parseFloat(style.top);
                const physicalPixels = Number(globalThis.$bridgeName?.topInsetPx?.()) || 0;
                const density = Number(globalThis.devicePixelRatio) || 1;
                const cssPixels = physicalPixels / density;
                if (
                  !Number.isFinite(originalTop) ||
                  originalTop < -0.5 ||
                  originalTop > cssPixels + 0.5
                ) {
                  return false;
                }
                const semanticSelector =
                  'header, nav, [role="banner"], [role="navigation"]';
                const headerLikeTag =
                  /(?:^|-)(?:header|topbar|navbar)(?:-|${'$'})/.test(element.localName || '');
                if (
                  !headerLikeTag && !element.matches(semanticSelector) &&
                  !element.querySelector(semanticSelector)
                ) {
                  return false;
                }
                const rect = readElementRect(element);
                const viewport = readViewportSize();
                if (
                  rect.width < viewport.width * 0.5 ||
                  rect.height <= 1 ||
                  rect.height > viewport.height * 0.5 ||
                  rect.top < -0.5 ||
                  rect.top > cssPixels + 0.5
                ) {
                  if (style.position === 'fixed') fixedTopHeaderCandidates.delete(element);
                  return false;
                }
                if (style.position === 'fixed') {
                  const scrollY = Number(globalThis.scrollY) || 0;
                  let candidate = fixedTopHeaderCandidates.get(element);
                  if (!candidate) {
                    rememberFixedTopHeaderCandidate(element, rect, scrollY, cssPixels);
                    return false;
                  }
                  if (!fixedTopHeaderHasMeaningfulScroll(candidate, scrollY)) {
                    if (scrollY < candidate.scrollY) {
                      candidate = rememberFixedTopHeaderCandidate(
                        element,
                        rect,
                        scrollY,
                        cssPixels,
                      );
                    }
                    return false;
                  }
                }
                const themeColor = activeThemeColor() ||
                  normalizedOpaqueColor(paintedBackground(style)) ||
                  normalizedOpaqueColor(canvasBackground(document.documentElement));
                const generation = Number(globalThis.$bridgeName?.navigationGeneration?.());
                const revision = Number(globalThis.$bridgeName?.policyRevision?.());
                if (!Number.isFinite(generation) || !Number.isFinite(revision)) return false;
                nativeFallbackRequested = true;
                nativeTopHeaderRequested = true;
                setOwnedAttribute(
                  document.documentElement,
                  'data-candy-browser-native-top-header',
                  'true',
                );
                globalThis.$bridgeName?.fallbackToNative?.(
                  generation,
                  revision,
                  themeColor,
                  true,
                );
                return true;
              };
              const verifyFixedTopHeaderCandidates = () => {
                for (const [element] of fixedTopHeaderCandidates) {
                  if (!element.isConnected) {
                    fixedTopHeaderCandidates.delete(element);
                    continue;
                  }
                  const style = readComputedStyle(element);
                  if (style.position !== 'fixed') {
                    fixedTopHeaderCandidates.delete(element);
                    if (
                      style.position === 'sticky' &&
                      requestNativeFallbackForTopHeader(element, style)
                    ) {
                      return true;
                    }
                    continue;
                  }
                  if (requestNativeFallbackForTopHeader(element, style)) return true;
                }
                return false;
              };
              const topContentBackground = (root, cssPixels) => {
                const viewportWidth = readViewportSize().width;
                const viewportHeight = readViewportSize().height;
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
                  const hits = document.elementsFromPoint(x, sampleY);
                  // Reuse an existing background query to anchor stable CSS sticky headers before
                  // an owned-style observer can cancel deferred discovery. No extra hit-test;
                  // nested/moving anchors retain sequential geometry and full discovery still follows.
                  if (candidateDiscoveryNeeded) protectDiscoveredStickyElements(hits, root, cssPixels);
                  for (const hit of hits) {
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
                    withOwnedLayoutWrite(() => document.querySelector(ownedSelector)?.remove());
                    removeOwnedProperty(root, property);
                    removeOwnedProperty(root, backgroundProperty);
                    return;
                  }
                  // Capture dimensions before owned styling, only for an actual protection pass.
                  readViewportSize();
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
                    withOwnedLayoutWrite(() => root.appendChild(style));
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
                    // Classification and repair share ancestry only until an actual owned write.
                    withLayoutReadCache(() => {
                      records.forEach((record) => {
                        record.addedNodes?.forEach((node) => {
                          if (node instanceof Element) {
                            observeOpenShadowRoot(node.shadowRoot);
                            enqueuePrioritySubtree(node);
                          }
                        });
                        if (record.type === 'attributes' && isRelevantLayoutMutation(record)) {
                          enqueuePrioritySubtree(record.target, false);
                        }
                        if (record.removedNodes?.length > 0) {
                          pendingPriorityRoots.forEach((node) => {
                            if (!node.isConnected) {
                              removePriorityRoot(node);
                            }
                          });
                        }
                      });
                      const interruptedDiscovery = (activePointDiscovery || activeTopInsetProtection) &&
                        records.some(isRelevantLayoutMutation);
                      if (interruptedDiscovery || records.some((record) =>
                          isRelevantLayoutMutation(record) && mutationNeedsCandidateDiscovery(record))) {
                        invalidatePointDiscovery();
                        pendingOwnedLayoutMutation = true;
                        resumeLayoutRecovery();
                        scheduleAddedPriorityLayoutCheck();
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
                    });
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
                  ownedJsStickyElements,
                  ownedOriginalTopStates,
                  stabilizationCheckTimers: [],
                  dispose: null,
                };
                runtimeState.dispose = () => {
                  scrollGeneration++;
                  lastScrollAt = null;
                  cancelAddedPriorityLayoutCheck();
                  cancelOwnedMutationLayoutCheck();
                  invalidatePointDiscovery();
                  pendingPriorityRoots.clear();
                  for (const work of [addedPriorityWork, attributePriorityWork]) {
                    work.roots.clear();
                    work.recent = null;
                    work.preferRecent = true;
                  }
                  priorityRootJobs = new WeakMap();
                  preferAddedPriorityWork = true;
                  pointDiscoveryProgress = null;
                  pendingOwnedLayoutMutation = false;
                  observer.disconnect();
                  restoreAttachShadowHook();
                  cancelDeferredLayoutCheck();
                  cancelQuietLayoutCheck();
                  globalThis.cancelAnimationFrame(immediateLayoutCheckFrame);
                  immediateLayoutCheckFrame = 0;
                  globalThis.cancelAnimationFrame(scrollLayoutCheckFrame);
                  scrollLayoutCheckFrame = 0;
                  observeDiscoveredShadowRoot = () => {};
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
