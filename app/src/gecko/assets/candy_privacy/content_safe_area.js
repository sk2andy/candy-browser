"use strict";

(() => {
  const bridge = () => globalThis.CandyContentTopInset;
  const maxRoots = 32;
  const maxOwnedElements = 1024;
  const maxAncestors = 12;
  const maxUnknownElements = 64;
  const owned = new Map();
  let ownStyleBatches = new WeakMap();
  const dirtyRoots = new Set();
  const unknown = new Set();
  const residualFlow = new WeakSet();
  let observedRoots = new WeakSet();
  let configuration = null;
  let configurationKey = null;
  let environmentProbe = null;
  let environmentRetries = 0;
  let bodyPending = false;
  let bodyRetries = 0;
  let bodyRetryTimer = 0;
  let epoch = 0;
  let observer = null;
  let workerTimer = 0;
  let quietTimer = 0;
  let verificationTimer = 0;
  let verificationGeneration = 0;
  let interactionUntil = 0;
  let failures = 0;
  let fallbackRequested = false;
  let jobs = [];
  let cleanup = [];
  let initialRemaining = 0;
  let initialVisited = new WeakSet();
  let verificationCursor = 0;
  let verificationElements = null;
  let verificationCandidates = [];
  let confirmationCursor = 0;

  const now = () => globalThis.performance?.now?.() ?? Date.now();
  const bounded = (value, fallback, minimum, maximum) => Number.isFinite(value)
    ? Math.max(minimum, Math.min(maximum, Math.round(value))) : fallback;
  const safeTop = () => {
    const scale = Number(globalThis.devicePixelRatio);
    return (configuration?.cssSafeAreaTopInsetPx || 0) / (Number.isFinite(scale) && scale > 0 ? scale : 1);
  };
  const active = () => configuration?.ready === true && configuration.enabled === true && safeTop() > 0;
  const authorized = () => active() &&
    (!configuration.requireInteractionForUpdates || now() < interactionUntil);
  const phase = (name, callback) => {
    let started = false;
    try {
      if (bridge()?.performanceDiagnosticsEnabled?.() === true && performance?.mark && performance?.measure) {
        performance.mark(`${name}.start`);
        started = true;
      }
    } catch (_error) {}
    try { return callback(); } finally {
      if (started) {
        try {
          performance.measure(name, `${name}.start`);
        } catch (_error) {}
        try { performance.clearMarks(`${name}.start`); } catch (_error) {}
        try { performance.clearMeasures(name); } catch (_error) {}
      }
    }
  };
  const writeStyle = (element, property, value, priority) => {
    if (element.style.getPropertyValue(property) === value &&
        element.style.getPropertyPriority(property) === priority) return;
    let batch = ownStyleBatches.get(element);
    if (!batch) batch = { values: new Set(), after: "" };
    batch.values.add(element.getAttribute("style") || "");
    if (value) element.style.setProperty(property, value, priority);
    else element.style.removeProperty(property);
    batch.after = element.getAttribute("style") || "";
    batch.values.add(batch.after);
    ownStyleBatches.set(element, batch);
  };
  const restore = (element) => {
    const properties = owned.get(element);
    if (!properties) return;
    for (const [property, state] of properties) {
      // A site changing inline styling wins, including during disable/navigation cleanup.
      if (element.style.getPropertyValue(property) === state.value &&
          element.style.getPropertyPriority(property) === "important") {
        writeStyle(element, property, state.authorValue, state.authorPriority);
      }
    }
    owned.delete(element);
    unknown.delete(element);
  };
  const apply = (element, property, original) => {
    if (!owned.has(element) && owned.size >= maxOwnedElements) return;
    let properties = owned.get(element);
    if (!properties) { properties = new Map(); owned.set(element, properties); }
    const value = `max(${original}px, env(safe-area-inset-top, 0px))`;
    properties.set(property, {
      authorValue: element.style.getPropertyValue(property),
      authorPriority: element.style.getPropertyPriority(property), value,
    });
    writeStyle(element, property, value, "important");
  };
  const composedParent = (element) => element.parentElement || element.getRootNode?.().host || null;
  const unsupportedAncestor = (element, fixed) => {
    let current = element;
    for (let count = 0; current && count < maxAncestors; count++, current = composedParent(current)) {
      const style = getComputedStyle(current);
      if (current.assignedSlot ||
          ["transform", "translate", "perspective", "filter", "scale", "rotate", "offsetPath"].some((name) =>
            style[name] && style[name] !== "none") ||
          (style.zoom && !["1", "normal", "100%"].includes(style.zoom)) ||
          (style.animationName && style.animationName !== "none") ||
          /(?:transform|perspective|filter|contain)/.test(style.willChange || "") ||
          (style.transitionDuration || "0s").split(",").some((value) => Number.parseFloat(value) > 0) ||
          (fixed && /(?:layout|paint|strict|content)/.test(style.contain || ""))) return true;
      if (current !== element && current !== document.documentElement &&
          ["auto", "scroll", "hidden", "overlay"].includes(style.overflowY)) return true;
      if (current === document.documentElement) return false;
    }
    return current !== null;
  };
  const rememberUnknown = (element) => {
    if (unknown.size < maxUnknownElements) unknown.add(element);
  };
  const environmentDelivered = () => {
    const delivered = environmentProbe ? Number.parseFloat(getComputedStyle(environmentProbe).paddingTop) : 0;
    return Number.isFinite(delivered) && delivered >= safeTop() - 0.5;
  };
  const firstFlowAlreadyProtected = (body) => {
    let current = body;
    let inspected = 0;
    let insideSticky = false;
    for (let depth = 0; current && depth < maxAncestors; depth++) {
      let child = current.firstElementChild;
      while (child && inspected++ < maxAncestors) {
        const style = getComputedStyle(child);
        if (style.display !== "none" && !["fixed", "absolute"].includes(style.position)) {
          if (Number.parseFloat(style.marginTop) < 0) {
            residualFlow.add(child);
            rememberUnknown(child);
          }
          // Sticky offsets move painted boxes, not the surrounding normal-flow allocation.
          // Deferred env delivery may already have moved Candy-owned sticky and its children.
          insideSticky ||= style.position === "sticky";
          if (Number.parseFloat(style.paddingTop) >= safeTop() - 0.5) return true;
          if (!insideSticky) {
            const rect = child.getBoundingClientRect();
            if (rect.width > 1 && rect.height > 1 && rect.top >= safeTop() - 0.5) return true;
          }
          break;
        }
        child = child.nextElementSibling;
      }
      if (!child || inspected >= maxAncestors) return false;
      current = child;
    }
    return false;
  };
  const classify = (element) => {
    if (element === environmentProbe) return;
    unknown.delete(element);
    if (!(element instanceof Element) || !element.isConnected) { restore(element); return; }
    // Removing Candy's override exposes changed stylesheet/class author positioning.
    restore(element);
    const style = getComputedStyle(element);
    if (style.display === "none" || ["hidden", "collapse"].includes(style.visibility)) return;
    if (element === document.body) {
      if (!environmentDelivered()) { bodyPending = true; return; }
      bodyPending = false;
      const padding = Number.parseFloat(style.paddingTop);
      const flowProtected = firstFlowAlreadyProtected(element);
      if (["static", "relative"].includes(style.position) && Number.isFinite(padding) &&
          padding < safeTop() - 0.5 && !flowProtected &&
          !unsupportedAncestor(element, false)) apply(element, "padding-top", Math.max(0, padding));
      return;
    }
    if (style.position === "absolute") { rememberUnknown(element); return; }
    if (!["fixed", "sticky"].includes(style.position)) {
      if (residualFlow.has(element) && Number.parseFloat(style.marginTop) < 0) rememberUnknown(element);
      return;
    }
    const top = Number.parseFloat(style.top);
    if (!Number.isFinite(top) || !/px$/.test(style.top) || top < 0 ||
        Number.parseFloat(style.marginTop) < 0 ||
        unsupportedAncestor(element, style.position === "fixed")) {
      rememberUnknown(element);
      return;
    }
    if (top > safeTop()) return;
    // Sticky is classified by declared top, even if its initial rectangle is below the viewport.
    if (style.position === "fixed" && element.getBoundingClientRect().height >= innerHeight * 0.8) {
      rememberUnknown(element);
      return;
    }
    apply(element, "top", top);
  };
  const observeRoot = (root) => {
    if (!observer || !root || observedRoots.has(root)) return;
    observedRoots.add(root);
    observer.observe(root, {
      subtree: true, childList: true, attributes: true, attributeOldValue: true,
      attributeFilter: ["class", "style", "hidden", "open", "aria-expanded", "aria-hidden"],
    });
  };
  const enqueue = (root, isAuthorized = false, initial = false, shallow = false) => {
    if (!(root instanceof Element) || !root.isConnected) return;
    if (isAuthorized) {
      dirtyRoots.delete(root);
      dirtyRoots.add(root);
      if (dirtyRoots.size > maxRoots) dirtyRoots.delete(dirtyRoots.values().next().value);
    }
    if (jobs.some((job) => job.root === root && job.shallow === shallow && !(shallow && job.visited > 0))) return;
    if (jobs.length >= maxRoots) jobs.shift();
    jobs.push({ root, initial, shallow, authorized: isAuthorized, visited: 0, stack: [
      { node: root, entered: false, child: null, shadowVisited: false },
    ] });
  };
  const nextElement = (job) => {
    if (job.shallow && job.visited > 0) return null;
    while (job.stack.length) {
      const frame = job.stack.at(-1);
      if (!frame.entered) {
        frame.entered = true;
        frame.child = frame.node.firstElementChild;
        if (frame.node instanceof Element) return frame.node;
      }
      if (frame.child) {
        const node = frame.child;
        frame.child = node.nextElementSibling;
        job.stack.push({ node, entered: false, child: null, shadowVisited: false });
        continue;
      }
      if (!frame.shadowVisited) {
        frame.shadowVisited = true;
        if (frame.node.shadowRoot?.mode === "open") {
          observeRoot(frame.node.shadowRoot);
          job.stack.push({ node: frame.node.shadowRoot, entered: false, child: null, shadowVisited: true });
          continue;
        }
      }
      job.stack.pop();
    }
    return null;
  };
  const unsafeBox = (element) => {
    if (!element?.isConnected) return false;
    const style = getComputedStyle(element);
    if (!["fixed", "sticky", "absolute"].includes(style.position) || style.display === "none" ||
        ["hidden", "collapse"].includes(style.visibility) || Number.parseFloat(style.opacity) <= 0.01) return false;
    const rect = element.getBoundingClientRect();
    if (rect.top >= safeTop() - 0.5 || rect.bottom <= 0 || rect.width <= 1 || rect.height <= 1) return false;
    const x = Math.max(1, Math.min(innerWidth - 1, (rect.left + rect.right) / 2));
    const y = Math.max(1, Math.min(safeTop() - 1, rect.bottom - 1));
    const scope = element.getRootNode?.();
    const hits = typeof scope?.elementsFromPoint === "function"
      ? scope.elementsFromPoint(x, y) : document.elementsFromPoint(x, y);
    return hits.some((hit) => hit === element || element.contains(hit));
  };
  const actualOverlap = (element) => {
    const flow = residualFlow.has(element);
    if (flow) {
      if (!element?.isConnected || Number.parseFloat(getComputedStyle(element).marginTop) >= 0) return false;
    } else if (!unsafeBox(element)) return false;
    const contentSelector = "header,nav,button,input,select,textarea,a[href],[role=button],[role=link]";
    const interactiveSelector = "button,input,select,textarea,a[href],[role=button],[role=link]";
    if (!flow && element.matches?.(interactiveSelector)) return true;
    const shadowChild = element.shadowRoot?.mode === "open" ? element.shadowRoot.firstElementChild : null;
    if (!element.firstElementChild && !shadowChild) return !flow && element.matches?.(contentSelector) === true &&
      Number.parseFloat(getComputedStyle(element).paddingTop) < safeTop() - 0.5;
    // A panel's background may intentionally bleed behind system bars. Only inspect bounded
    // content boxes, not text, and never infer unsafe controls from the panel rectangle alone.
    const pending = [element.firstElementChild, shadowChild].filter(Boolean);
    for (let count = 0; pending.length && count < 8; count++) {
      const child = pending.pop();
      if (child.nextElementSibling) pending.push(child.nextElementSibling);
      if (child.firstElementChild) pending.push(child.firstElementChild);
      if (child.shadowRoot?.mode === "open" && child.shadowRoot.firstElementChild) {
        pending.push(child.shadowRoot.firstElementChild);
      }
      if (!child.matches?.(contentSelector) || !child.isConnected) continue;
      const style = getComputedStyle(child);
      if (style.display === "none" || ["hidden", "collapse"].includes(style.visibility)) continue;
      const rect = child.getBoundingClientRect();
      if (rect.top >= safeTop() - 0.5 || rect.bottom <= 0 || rect.width <= 1 || rect.height <= 1) continue;
      const scope = child.getRootNode?.();
      const x = Math.max(1, Math.min(innerWidth - 1, (rect.left + rect.right) / 2));
      const y = Math.max(1, Math.min(safeTop() - 1, rect.bottom - 1));
      const hits = typeof scope?.elementsFromPoint === "function"
        ? scope.elementsFromPoint(x, y) : document.elementsFromPoint(x, y);
      if (hits.some((hit) => hit === child || child.contains(hit))) return true;
    }
    return false;
  };
  const verify = (expectedEpoch, expectedVerificationGeneration) => {
    if (expectedEpoch !== epoch || expectedVerificationGeneration !== verificationGeneration) return;
    verificationTimer = 0;
    if (!active() || fallbackRequested) return;
    phase("Candy.SafeArea.Css.Verify", () => {
      // CSS env delivery is asynchronous. An undelivered inset is not a failed page layout.
      if (!environmentDelivered()) {
        if (++environmentRetries < 8) scheduleVerification();
        return;
      }
      const elements = verificationElements || (verificationElements = Array.from(unknown));
      const startedAt = now();
      let scanned = false;
      for (let checked = 0; verificationCursor < elements.length && checked < configuration.maxElementsPerBatch; checked++) {
        if (checked > 0 && now() - startedAt >= configuration.maxBatchDurationMillis) break;
        const element = elements[verificationCursor++];
        scanned = true;
        if (!element.isConnected) { unknown.delete(element); continue; }
        if (actualOverlap(element)) verificationCandidates.push(element);
      }
      if (verificationCursor < elements.length) { scheduleVerification(); return; }
      if (scanned && verificationCandidates.length) { scheduleVerification(); return; }
      // Retain identities only. A later candidate becoming safe must not hide an earlier one.
      let overlap = false;
      const confirmationStartedAt = now();
      for (let checked = 0; confirmationCursor < verificationCandidates.length && checked < configuration.maxElementsPerBatch; checked++) {
        if (checked > 0 && now() - confirmationStartedAt >= configuration.maxBatchDurationMillis) break;
        if (actualOverlap(verificationCandidates[confirmationCursor++])) { overlap = true; break; }
      }
      if (!overlap && confirmationCursor < verificationCandidates.length) { scheduleVerification(); return; }
      failures = overlap ? failures + 1 : 0;
      verificationCursor = 0;
      verificationElements = null;
      verificationCandidates = [];
      confirmationCursor = 0;
      if (failures >= configuration.safeAreaRequiredFailureCount) {
        fallbackRequested = true;
        bridge()?.fallbackToNative?.(configuration.navigationGeneration, configuration.revision);
      } else if (overlap) scheduleVerification();
    });
  };
  const scheduleVerification = () => {
    if (!active() || !unknown.size || fallbackRequested || verificationTimer) return;
    const expectedEpoch = epoch;
    const expectedVerificationGeneration = verificationGeneration;
    verificationTimer = setTimeout(() => verify(expectedEpoch, expectedVerificationGeneration), configuration.safeAreaLayoutQuietPeriodMillis);
  };
  const scheduleWorker = (delay = 0) => {
    if (workerTimer || (!cleanup.length && (!active() || !jobs.length))) return;
    const expectedEpoch = epoch;
    workerTimer = setTimeout(() => {
      if (expectedEpoch !== epoch) return;
      workerTimer = 0;
      phase("Candy.SafeArea.Css.Batch", () => {
        const startedAt = now();
        for (let count = 0; count < (configuration?.maxElementsPerBatch || 16); count++) {
          if (count > 0 && now() - startedAt >= (configuration?.maxBatchDurationMillis || 4)) break;
          if (cleanup.length) { restore(cleanup.pop()); continue; }
          if (!active() || !jobs.length) break;
          const job = jobs[0];
          if (job.initial && initialRemaining <= 0) {
            jobs.shift();
            continue;
          }
          const element = job.visited < configuration.maxInitialElements ? nextElement(job) : null;
          if (!element) {
            jobs.shift();
            dirtyRoots.delete(job.root);
            continue;
          }
          job.visited++;
          if (job.initial) {
            if (initialVisited.has(element)) continue;
            initialVisited.add(element);
            initialRemaining--;
          }
          classify(element);
        }
      });
      scheduleWorker();
      if (!jobs.length && !cleanup.length) {
        scheduleVerification();
        if (active() && bodyPending && !bodyRetryTimer && bodyRetries < 8) {
          bodyRetryTimer = setTimeout(() => {
            if (!active() || expectedEpoch !== epoch) return;
            bodyRetryTimer = 0;
            bodyRetries++;
            enqueue(document.body, false, false, true);
            scheduleWorker();
          }, configuration.safeAreaLayoutQuietPeriodMillis);
        }
      }
    }, delay);
  };
  const cancelWork = () => {
    clearTimeout(workerTimer); workerTimer = 0;
    clearTimeout(quietTimer); quietTimer = 0;
    clearTimeout(verificationTimer); verificationTimer = 0;
    clearTimeout(bodyRetryTimer); bodyRetryTimer = 0;
    jobs = [];
  };
  const onScroll = () => {
    if (!active()) return;
    epoch++;
    interactionUntil = 0;
    cancelWork();
    verificationCursor = 0;
    verificationElements = null;
    verificationCandidates = [];
    confirmationCursor = 0;
    failures = 0;
    // Configuration cleanup uses metadata/style ownership only, never geometry.
    if (cleanup.length) scheduleWorker();
    // Only an explicitly authorized dirty root may restart, from fresh DOM, after quiet.
    if (dirtyRoots.size) {
      const expectedEpoch = epoch;
      quietTimer = setTimeout(() => {
        if (!active() || expectedEpoch !== epoch) return;
        quietTimer = 0;
        for (const root of Array.from(dirtyRoots)) enqueue(root, true);
        scheduleWorker();
      }, configuration.safeAreaLayoutQuietPeriodMillis);
    }
  };
  const mutations = (records) => {
    if (!active()) return;
    phase("Candy.SafeArea.Css.Mutations", () => {
      const ownTargets = new Set();
      const relevant = records.slice(-128).filter((record) => {
        if (record.type !== "attributes" || record.attributeName !== "style") return true;
        const batch = ownStyleBatches.get(record.target);
        if (batch) ownTargets.add(record.target);
        return !batch || batch.after !== (record.target.getAttribute("style") || "") ||
          !batch.values.has(record.oldValue || "");
      });
      for (const target of ownTargets) ownStyleBatches.delete(target);
      if (relevant.length) {
        // Author changes invalidate quiet-layout evidence, even without authorization to repair.
        // Keep accepted workers intact, and never schedule geometry from this metadata reset.
        verificationGeneration++;
        clearTimeout(verificationTimer); verificationTimer = 0;
        verificationCursor = 0;
        verificationElements = null;
        verificationCandidates = [];
        confirmationCursor = 0;
        failures = 0;
      }
      if (!authorized()) return;
      const eligible = relevant.filter((record) =>
        (record.type === "attributes" && configuration.recheckChangedElements) ||
        (record.type === "childList" && configuration.recheckAddedElements && record.addedNodes?.length));
      if (!eligible.length) return;
      epoch++;
      cancelWork();
      verificationCursor = 0;
      verificationElements = null;
      verificationCandidates = [];
      confirmationCursor = 0;
      failures = 0;
      for (const root of Array.from(dirtyRoots)) enqueue(root, true);
      for (const record of eligible) {
        if (record.type === "attributes" && configuration.recheckChangedElements) {
          enqueue(record.target, true);
        } else if (record.type === "childList" && configuration.recheckAddedElements) {
          const added = record.addedNodes || [];
          for (let index = added.length - 1; index >= Math.max(0, added.length - maxRoots); index--) {
            enqueue(added[index], true);
          }
        }
      }
      scheduleWorker(configuration.mutationDebounceMillis);
    });
  };
  const onInteraction = (event) => {
    if (!active() || event.isTrusted !== true) return;
    if (event.type === "keydown" && (
      event.ctrlKey || event.metaKey || event.altKey ||
      [" ", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "PageUp", "PageDown", "Home", "End"].includes(event.key) ||
      !(event.key?.length === 1 || ["Backspace", "Delete", "Enter"].includes(event.key))
    )) return;
    interactionUntil = now() + configuration.interactionWindowMillis;
  };
  const initialize = () => {
    if (!active() || !document.body) return;
    if (!environmentProbe) {
      environmentProbe = document.createElement("span");
      environmentProbe.setAttribute("data-candy-css-safe-area-probe", "true");
      environmentProbe.style.cssText = "position:fixed!important;visibility:hidden!important;" +
        "pointer-events:none!important;width:0!important;height:0!important;" +
        "padding-top:env(safe-area-inset-top,0px)!important;";
      document.documentElement.appendChild(environmentProbe);
    }
    if (!observer) observer = new MutationObserver(mutations);
    observeRoot(document.documentElement);
    enqueue(document.body, false, true, true);
    // Fixed headers and navigation can precede a large feed in source order, or be visible late.
    for (const selector of ["header", "nav"]) {
      enqueue(document.querySelector(selector), false, true, true);
    }
    for (const x of [1, innerWidth / 2, Math.max(1, innerWidth - 1)]) {
      for (const element of document.elementsFromPoint(x, Math.min(innerHeight - 1, safeTop() + 1)).slice(0, 4)) {
        enqueue(element, false, true, true);
      }
    }
    enqueue(document.body, false, true);
    scheduleWorker();
  };
  globalThis.__candyConfigureCssSafeArea = () => {
    const incoming = bridge()?.cssSafeAreaConfiguration?.();
    const next = incoming ? {
      ...incoming,
      cssSafeAreaTopInsetPx: bounded(incoming.cssSafeAreaTopInsetPx, 0, 0, 10000),
      enabled: incoming.enabled !== false,
      recheckAddedElements: incoming.recheckAddedElements !== false,
      recheckChangedElements: incoming.recheckChangedElements !== false,
      requireInteractionForUpdates: incoming.requireInteractionForUpdates !== false,
      recheckOnResize: incoming.recheckOnResize !== false,
      interactionWindowMillis: bounded(incoming.interactionWindowMillis, 1000, 100, 5000),
      mutationDebounceMillis: bounded(incoming.mutationDebounceMillis, 150, 50, 1000),
      maxElementsPerBatch: bounded(incoming.maxElementsPerBatch, 16, 4, 64),
      maxBatchDurationMillis: bounded(incoming.maxBatchDurationMillis, 4, 1, 8),
      maxInitialElements: bounded(incoming.maxInitialElements, 512, 64, 2048),
      safeAreaLayoutQuietPeriodMillis: bounded(incoming.safeAreaLayoutQuietPeriodMillis, 400, 100, 800),
      safeAreaRequiredFailureCount: bounded(incoming.safeAreaRequiredFailureCount, 3, 2, 5),
    } : null;
    const key = JSON.stringify(next && [
      next.ready, next.navigationGeneration, next.cssSafeAreaTopInsetPx, next.enabled,
      next.recheckAddedElements, next.recheckChangedElements, next.requireInteractionForUpdates,
      next.recheckOnResize, next.interactionWindowMillis, next.mutationDebounceMillis,
      next.maxElementsPerBatch, next.maxBatchDurationMillis, next.maxInitialElements,
      next.safeAreaLayoutQuietPeriodMillis, next.safeAreaRequiredFailureCount,
    ]);
    // Revision and diagnostics messages still atomically update fallback identity, not the DOM.
    configuration = next;
    if (key === configurationKey) {
      if (active() && bodyPending) {
        enqueue(document.body, false, false, true);
        scheduleWorker();
      }
      return;
    }
    configurationKey = key;
    epoch++;
    cancelWork();
    interactionUntil = 0;
    dirtyRoots.clear();
    unknown.clear();
    failures = 0;
    fallbackRequested = false;
    environmentRetries = 0;
    bodyPending = false;
    bodyRetries = 0;
    initialRemaining = configuration?.maxInitialElements || 512;
    initialVisited = new WeakSet();
    verificationCursor = 0;
    verificationElements = null;
    verificationCandidates = [];
    confirmationCursor = 0;
    observer?.disconnect(); observer = null;
    observedRoots = new WeakSet();
    ownStyleBatches = new WeakMap();
    cleanup = Array.from(owned.keys());
    if (!active()) { environmentProbe?.remove(); environmentProbe = null; }
    initialize();
    scheduleWorker();
  };
  document.addEventListener("DOMContentLoaded", initialize, { once: true });
  for (const type of ["click", "drop", "input", "keydown"]) document.addEventListener(type, onInteraction, true);
  for (const type of ["scroll", "touchmove", "wheel"]) {
    document.addEventListener(type, onScroll, { capture: true, passive: true });
  }
  globalThis.addEventListener("scroll", onScroll, { passive: true });
  globalThis.addEventListener("resize", () => {
    if (!active() || !configuration.recheckOnResize) return;
    epoch++;
    cancelWork();
    verificationCursor = 0;
    verificationElements = null;
    verificationCandidates = [];
    confirmationCursor = 0;
    failures = 0;
    for (const element of owned.keys()) enqueue(element);
    enqueue(document.body);
    scheduleWorker(configuration.mutationDebounceMillis);
  });
  globalThis.__candyConfigureCssSafeArea();
})();
