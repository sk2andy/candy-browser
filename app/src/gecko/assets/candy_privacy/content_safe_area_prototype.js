"use strict";

// Experimental top-document policy: no iframe, containing-block or footprint proof.
(() => {
  if (globalThis.self !== globalThis.top) return;
  const owned = new Map();
  const ownWrites = new WeakMap();
  const rules = new Map();
  const hostname = typeof globalThis.location?.hostname === "string" ? globalThis.location.hostname.toLowerCase().replace(/\.$/, "") : "";
  const knownTopSelectors = hostname === "amazon.de" || hostname.endsWith(".amazon.de") ?
    [":root #btf-sub-nav-top-navigation-bar.persistent-header"] :
    ["google.com", "google.de"].some((host) => hostname === host || hostname.endsWith(`.${host}`)) ?
      [":root #navd", ":root #tsf .A7Yvie.emcav"] : [];
  const knownTopMatcher = knownTopSelectors.join(", ");
  const markerPrefix = `data-candy-safe-area-${Math.random().toString(36).slice(2)}`;
  let layerEpoch = 0;
  let markerName = "";
  let markerId = 0;
  let layer = null;
  let selectorLayer = null;
  let selectorScan = null;
  let selectorsScanned = false;
  const sources = new Map();
  let sourceQueue = [];
  let sourceDiscovery = null;
  let selectorBuild = null;
  const selectorRules = new Map();
  const cssCounts = { discovered: 0, late: 0, scans: 0, rules: 0, errors: 0, unsupported: 0, capped: 0, cancelled: 0 };
  // CSS source limits are independent of the smaller DOM initial-discovery budget.
  const cssLimits = { sheets: 128, rulesPerSheet: 4096, selectors: 256, lifetimeRules: 65536,
    lifetimeEvents: 4096, cooldownMillis: 500 };
  let sourceEvents = 0;
  let sourceWork = 0;
  let firstInitialization = null;
  let protectedSelectors = [];
  const selectorImportance = new Map();
  let selectorMatcher = "";
  let configuration = null;
  let configurationKey = "";
  let inset = 0;
  let jobs = [];
  let cleanup = [];
  let timer = 0;
  let timerEpoch = 0;
  let observer = null;
  let interactionUntil = 0;
  let bodyPending = false;
  let semanticSeeded = false;
  let initialDomSeeded = false;
  let protectedBody = null;
  let redditFlowProtected = false;
  let refreshBodyAtReady = false;
  let cssTurn = true;

  function viewportFitCoversSafeArea() {
    const content = document.querySelector('meta[name="viewport" i]')?.getAttribute("content");
    return typeof content === "string" &&
      /(?:^|[\s,;])viewport-fit\s*=\s*cover(?=$|[\s,;])/i.test(content);
  }

  function pixels(value) {
    if (typeof value !== "string" || !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)px$/.test(value.trim())) return null;
    const number = Number(value.trim().slice(0, -2));
    return Number.isFinite(number) ? number : null;
  }

  function stillOwned(element, entry) {
    return element.style.getPropertyValue(entry.name) === entry.applied &&
      element.style.getPropertyPriority(entry.name) === "important";
  }

  function write(element, name, value, priority = "") {
    const before = element.getAttribute("style") || "";
    if (value) element.style.setProperty(name, value, priority);
    else element.style.removeProperty(name);
    let record = ownWrites.get(element);
    if (!record || record.after !== before) record = { before: new Set(), after: "" };
    record.before.add(before);
    record.after = element.getAttribute("style") || "";
    ownWrites.set(element, record);
  }

  function apply(element, name, value) {
    let entries = owned.get(element);
    const entry = entries?.find((current) => current.name === name);
    if (entry) {
      if (!stillOwned(element, entry)) {
        entries.splice(entries.indexOf(entry), 1);
        if (!entries.length) owned.delete(element);
      }
      return; // Do not remove/reapply retained protection on each mutation.
    }
    if (!entries) {
      if (owned.size >= configuration.maxInitialElements) return;
      entries = [];
      owned.set(element, entries);
    }
    const original = { name, value: element.style.getPropertyValue(name),
      priority: element.style.getPropertyPriority(name) };
    write(element, name, value, "important");
    const applied = element.style.getPropertyValue(name);
    if (element.style.getPropertyPriority(name) !== "important" ||
        (name === "--candy-safe-area-inset-top" ? applied !== value :
          pixels(applied) === null || Math.abs(pixels(applied) - pixels(value)) > 0.001)) {
      if (!entries.length) owned.delete(element);
      return;
    }
    entries.push({ ...original, applied });
  }

  function restore(element, entries) {
    if (!Array.isArray(entries)) {
      if (element.getAttribute(entries.attribute) === entries.id) {
        if (entries.original === null) element.removeAttribute(entries.attribute);
        else element.setAttribute(entries.attribute, entries.original);
      }
      return;
    }
    for (const entry of entries) {
      if (stillOwned(element, entry)) write(element, entry.name, entry.value, entry.priority);
    }
  }

  function releaseRule(element) {
    const entry = rules.get(element);
    if (!entry) return;
    const index = Array.from(layer?.sheet?.cssRules || []).indexOf(entry.rule);
    if (index >= 0) layer.sheet.deleteRule(index);
    rules.delete(element);
    restore(element, entry);
  }

  function ensureLayer() {
    if (layer) return layer.isConnected ? layer.sheet : null;
    layer = document.createElement("style");
    layer.textContent = knownTopSelectors.map((selector) =>
      `${selector} { top: calc(0px + var(--candy-safe-area-inset-top)) !important; }`).join("\n");
    document.documentElement.appendChild(layer);
    if (!layer.sheet) { layer.remove(); layer = null; return null; }
    return layer.sheet;
  }

  function selectorOwns(element) {
    if (globalThis.CandyRedditSafeArea?.owns(element)) return true;
    if (!selectorMatcher && !knownTopMatcher) return false;
    try {
      if (knownTopMatcher && element.matches(knownTopMatcher)) return true;
      return !!selectorMatcher && element.matches(selectorMatcher);
    }
    catch { return false; }
  }

  function releaseElementTop(element) {
    const entry = rules.get(element);
    if (!entry) return;
    if (entry.rule.style.getPropertyValue("top")) entry.rule.style.removeProperty("top");
    if (!entry.rule.style.getPropertyValue("padding-top")) releaseRule(element);
  }

  function startSelectorScan() {
    if (selectorsScanned || !configuration?.active || document.readyState === "loading") return;
    selectorsScanned = true;
    sourceDiscovery = { index: 0 };
  }

  function queueSource(sheet, owner = sheet?.ownerNode, delayed = true) {
    if (globalThis.CandyRedditSafeArea?.ownsSource(owner)) return;
    if (!sheet || sheet === layer?.sheet || sheet === selectorLayer?.sheet ||
        sheet === selectorBuild?.staging?.sheet || owner === layer || owner === selectorLayer || owner === selectorBuild?.staging) return;
    if (sourceEvents >= cssLimits.lifetimeEvents || cssCounts.rules >= cssLimits.lifetimeRules) { cssCounts.capped++; return; }
    sourceEvents++;
    let source = sources.get(sheet);
    if (!source) {
      if (sources.size >= cssLimits.sheets) { cssCounts.capped++; return; }
      source = { sheet, owner, revision: 0, candidates: new Map(), due: 0 };
      sources.set(sheet, source);
      cssCounts.discovered++;
      if (delayed) cssCounts.late++;
    }
    source.revision++;
    if (!sourceQueue.includes(source)) {
      source.due = performance.now() + (delayed ? cssLimits.cooldownMillis : 0);
      sourceQueue.push(source);
    } else if (!delayed) source.due = Math.min(source.due, performance.now());
    if (selectorScan?.source === source) selectorScan = null;
  }

  function sourceNode(node) {
    if (!(node instanceof Element) || isOwnSource(node)) return;
    if (node.localName === "style" || node.localName === "link") {
      // A replaced href/sheet or removed owner invalidates its previous cloned rules.
      let invalidated = false;
      for (const [sheet, source] of sources) {
        if (source.owner === node && (!node.isConnected || sheet !== node.sheet)) {
          sources.delete(sheet);
          sourceQueue = sourceQueue.filter((queued) => queued !== source);
          if (selectorScan?.source === source) selectorScan = null;
          invalidated = true;
        }
      }
      if (node.isConnected && node.sheet) queueSource(node.sheet, node, document.readyState === "complete");
      else if (invalidated && (!node.isConnected || node.localName === "style" || node.disabled === true ||
          node.getAttribute("disabled") !== null ||
          !(node.getAttribute("rel") || "").toLowerCase().split(/\s+/).includes("stylesheet"))) beginSelectorBuild();
      // A connected replacement may not expose sheet until load. Retain the last
      // committed protection while waiting, rather than committing an empty gap.
    }
  }

  function isOwnSource(node) {
    return globalThis.CandyRedditSafeArea?.ownsSource(node) ||
      node === layer || node === selectorLayer || node === selectorBuild?.staging ||
      layer?.contains(node) || selectorLayer?.contains(node) || selectorBuild?.staging?.contains(node);
  }

  function beginSelectorBuild() {
    selectorBuild?.staging?.remove();
    const ordered = [];
    // Preserve current document source order, including a late sheet inserted before another.
    for (let index = 0; index < Math.min(document.styleSheets.length, cssLimits.sheets + 3); index++) {
      const source = sources.get(document.styleSheets[index]);
      if (source) ordered.push(source);
    }
    selectorBuild = { phase: "collect", sources: ordered.values(), candidates: null,
      result: new Map(), matcherLength: 0 };
  }

  function buildSelectorStep() {
    const build = selectorBuild;
    if (build.phase === "collect") {
      if (!build.candidates) {
        const next = build.sources.next();
        if (next.done) {
          build.staging = document.createElement("style");
          build.staging.setAttribute("media", "not all");
          document.documentElement.appendChild(build.staging);
          if (!build.staging.sheet) { build.staging.remove(); selectorBuild = null; return; }
          build.phase = "insert";
          build.entries = build.result.entries();
          build.applied = new Map();
          return;
        }
        build.candidates = next.value.candidates.entries();
      }
      const next = build.candidates.next();
      if (next.done) { build.candidates = null; return; }
      const [selector, candidate] = next.value;
      const previous = build.result.get(selector);
      if (!candidate.important && previous?.important) return;
      if (!previous && (build.result.size >= Math.min(cssLimits.selectors, configuration.maxInitialElements - Math.max(1, rules.size) - knownTopSelectors.length) ||
          build.matcherLength + selector.length + 2 > 32768)) { cssCounts.capped++; return; }
      if (!previous) build.matcherLength += selector.length + 2;
      // Map order follows the last applicable declaration's source order.
      build.result.delete(selector);
      build.result.set(selector, candidate);
      return;
    }
    if (build.phase === "insert") {
      const next = build.entries.next();
      if (next.done) {
        // Prepare bounded text before publishing: interruption retains working protection.
        // Gecko reparses STYLE text when media changes or the owner moves. Keep
        // validated CSS in the node, not only insertRule-only CSSOM state.
        build.staging.textContent = [...build.applied.values()].map((entry) => entry.text).join("\n");
        selectorLayer?.remove();
        selectorLayer = build.staging;
        build.staging = null;
        if (selectorLayer.nextElementSibling) document.documentElement.appendChild(selectorLayer);
        selectorLayer.removeAttribute("media");
        selectorRules.clear();
        selectorImportance.clear();
        let index = 0;
        for (const [selector, entry] of build.applied) {
          selectorRules.set(selector, selectorLayer.sheet.cssRules[index++]);
          selectorImportance.set(selector, entry.important);
        }
        protectedSelectors = [...build.applied.keys()];
        selectorMatcher = protectedSelectors.join(", ");
        build.phase = "reconcile";
        build.elements = rules.entries();
        return;
      }
      const [selector, candidate] = next.value;
      const sheet = build.staging.sheet;
      try {
        const text = `${selector} { top: ${candidate.top + inset}px !important; }`;
        const index = sheet.insertRule(text, sheet.cssRules.length);
        build.applied.set(selector, { rule: sheet.cssRules[index], important: candidate.important, text });
      } catch { cssCounts.unsupported++; }
      return;
    }
    const next = build.elements.next();
    if (next.done) selectorBuild = null;
    else if (selectorOwns(next.value[0])) releaseElementTop(next.value[0]);
  }

  function scanSelectorStep() {
    const scan = selectorScan;
    if (scan.source.revision !== scan.revision) { selectorScan = null; return; }
    if (scan.index >= scan.list.length || scan.index >= cssLimits.rulesPerSheet || cssCounts.rules >= cssLimits.lifetimeRules) {
      if (scan.index < scan.list.length) cssCounts.capped++;
      scan.source.candidates = scan.candidates;
      selectorScan = null;
      cssCounts.scans++;
      if (!sourceQueue.some((source) => source.due <= performance.now())) beginSelectorBuild();
      return;
    }
    const rule = scan.list[scan.index++];
    cssCounts.rules++;
    // Grouping/import/keyframe/nested rules are deliberately unsupported in this prototype.
    if (rule.type !== 1 || rule.cssRules?.length) { cssCounts.unsupported++; return; }
    const position = rule.style.getPropertyValue("position");
    const top = pixels(rule.style.getPropertyValue("top"));
    const selector = rule.selectorText;
    if ((position !== "fixed" && position !== "sticky") || top === null || !selector || selector.length > 2048) return;
    const important = rule.style.getPropertyPriority("top") === "important";
    if (!important && scan.candidates.get(selector)?.important) return;
    if (!scan.candidates.has(selector) && scan.candidates.size >= cssLimits.selectors) { cssCounts.capped++; return; }
    scan.candidates.delete(selector);
    scan.candidates.set(selector, { top, important });
  }

  function cssSourceStep() {
    if (sourceWork >= 131072) {
      if (selectorBuild || selectorScan || sourceDiscovery || sourceQueue.length) cssCounts.capped++;
      selectorBuild?.staging?.remove();
      selectorBuild = null; selectorScan = null; sourceDiscovery = null; sourceQueue = [];
      return false;
    }
    if (selectorBuild || selectorScan || sourceDiscovery || sourceQueue.some((source) => source.due <= performance.now())) sourceWork++;
    if (selectorBuild) { buildSelectorStep(); return true; }
    if (selectorScan) { scanSelectorStep(); return true; }
    if (sourceDiscovery) {
      if (sourceDiscovery.index < document.styleSheets.length &&
          sourceDiscovery.index < cssLimits.sheets + 3) {
        queueSource(document.styleSheets[sourceDiscovery.index++], undefined, false);
      } else {
        if (sourceDiscovery.index < document.styleSheets.length) cssCounts.capped++;
        sourceDiscovery = null;
      }
      return true;
    }
    const index = sourceQueue.findIndex((source) => source.due <= performance.now());
    if (index < 0) return false;
    const source = sourceQueue.splice(index, 1)[0];
    let list = [];
    try {
      if ((!source.owner || source.owner.isConnected) && !source.sheet.disabled &&
          (source.owner?.localName !== "link" || (source.owner.getAttribute("rel") || "").toLowerCase().split(/\s+/).includes("stylesheet")) &&
          (!source.sheet.media?.mediaText || source.sheet.media.mediaText === "all")) list = source.sheet.cssRules;
      else cssCounts.unsupported++;
    } catch (error) {
      if (error?.name === "SecurityError") cssCounts.errors++;
      else cssCounts.unsupported++;
    } // No cross-origin fetch or permission bypass.
    selectorScan = { source, revision: source.revision, list, index: 0, candidates: new Map() };
    return true;
  }

  // Author origin: normal inline resets lose to these rules; inline !important can win.
  function applyRule(element, name, value) {
    // Author removal/tampering is not repaired; a new configuration creates a new layer.
    if (layer && !layer.isConnected) return;
    let entry = rules.get(element);
    if (entry && element.getAttribute(entry.attribute) !== entry.id) {
      releaseRule(element);
      entry = null;
    }
    if (!entry) {
      if (rules.size >= configuration.maxInitialElements || markerId >= configuration.maxInitialElements) return;
      const sheet = ensureLayer();
      if (!sheet || sheet.cssRules.length + selectorRules.size >= configuration.maxInitialElements) return;
      const id = String(++markerId);
      const index = layer.sheet.insertRule(`:root [${markerName}="${id}"] {}`, layer.sheet.cssRules.length);
      entry = { attribute: markerName, id, original: element.getAttribute(markerName), rule: layer.sheet.cssRules[index] };
      rules.set(element, entry);
      element.setAttribute(markerName, id);
    }
    if (!entry.rule.style.getPropertyValue(name)) entry.rule.style.setProperty(name, value, "important");
  }

  function cancel() {
    if (timer) clearTimeout(timer);
    timer = 0;
    timerEpoch++;
    jobs = [];
    if (selectorScan || sourceQueue.length || sourceDiscovery || selectorBuild) cssCounts.cancelled++;
    selectorScan = null; // Scroll cancellation may leave bounded selector coverage partial.
    sourceQueue = [];
    sourceDiscovery = null;
    selectorBuild?.staging?.remove();
    selectorBuild = null;
    interactionUntil = 0;
  }

  function schedule(delay = 0) {
    if (timer || (!cleanup.length && !bodyPending && !jobs.length && !selectorScan &&
        !selectorBuild && !sourceDiscovery && !sourceQueue.length)) return;
    if (!cleanup.length && !bodyPending && !jobs.length && !selectorScan && !selectorBuild && !sourceDiscovery && sourceQueue.length) {
      delay = Math.max(delay, Math.max(0, Math.min(...sourceQueue.map((source) => source.due)) - performance.now()));
    }
    const epoch = timerEpoch;
    timer = setTimeout(() => {
      if (epoch !== timerEpoch) return;
      timer = 0;
      work();
    }, delay);
  }

  function enqueue(root, initial = false, shallow = false) {
    if (root instanceof Element && !root.isConnected) releaseRule(root);
    if (!(root instanceof Element) || !root.isConnected ||
        jobs.some((job) => job.root === root && job.shallow === shallow)) return;
    if (jobs.length >= 16) return;
    const job = { root, next: root, remaining: shallow ? 1 : configuration.maxInitialElements, initial, shallow };
    if (shallow) jobs.unshift(job);
    else jobs.push(job);
    return job;
  }

  function seedSemanticHeader() {
    if (semanticSeeded || !configuration?.active || !document.body || document.readyState === "loading") return;
    semanticSeeded = true;
    let current = document.querySelector("header") ?? document.querySelector("nav") ?? document.querySelector('[role="banner"]');
    for (let depth = 0; current && current !== document.body &&
        current !== document.documentElement && depth < 8; depth++, current = current.parentElement) {
      enqueue(current, true, true);
    }
  }

  function nextElement(element, root) {
    if (element.firstElementChild) return element.firstElementChild;
    for (let current = element; current && current !== root; current = current.parentElement) {
      if (current.nextElementSibling) return current.nextElementSibling;
    }
    return null;
  }

  function classify(element, style) {
    if (selectorOwns(element)) { releaseElementTop(element); return; }
    const entry = rules.get(element);
    if (entry && layer?.isConnected && element.getAttribute(entry.attribute) === entry.id &&
        entry.rule.style.getPropertyValue("top")) return;
    if (entry && (!layer?.isConnected || element.getAttribute(entry.attribute) !== entry.id)) releaseRule(element);
    style ??= getComputedStyle(element);
    if (style.position !== "fixed" && style.position !== "sticky") return;
    const top = pixels(style.top);
    if (top !== null) applyRule(element, "top", `${top + inset}px`);
  }

  function seedInitialDom() {
    if (initialDomSeeded || !configuration?.active || document.readyState === "loading" || !document.body) return;
    initialDomSeeded = true;
    seedSemanticHeader();
    const job = enqueue(document.body, true);
    // Body plus eight reserved shallow checks share the original DOM node cap.
    if (job) { job.next = nextElement(document.body, document.body); job.remaining -= 9; }
  }

  function protectBody() {
    if (!configuration?.active || cleanup.length || !document.body || !document.documentElement) return;
    if (viewportFitCoversSafeArea()) {
      configure();
      return;
    }
    bodyPending = false;
    apply(document.documentElement, "--candy-safe-area-inset-top", `${inset}px`);
    const flowProtected = globalThis.CandyRedditSafeArea?.flowProtected() === true;
    if ((refreshBodyAtReady && document.readyState !== "loading") || flowProtected !== redditFlowProtected) {
      // Do not measure our early padding as author padding. Remove/read/republish
      // in this same task, so later parser CSS is preserved without a paint gap.
      rules.get(document.body)?.rule.style.removeProperty("padding-top");
      refreshBodyAtReady = false;
    }
    const style = getComputedStyle(document.body);
    const padding = pixels(style.paddingTop);
    if (padding !== null) applyRule(document.body, "padding-top", `${flowProtected ? padding : Math.max(padding, inset)}px`);
    redditFlowProtected = flowProtected;
    classify(document.body, style);
    protectedBody = document.body;
    if (document.readyState === "loading") refreshBodyAtReady = true;
    seedInitialDom();
  }

  function work() {
    const started = performance.now();
    let count = 0;
    while (count < configuration.maxElementsPerBatch &&
        performance.now() - started < configuration.maxBatchDurationMillis) {
      if (cleanup.length) {
        const [element, entries] = cleanup.shift();
        restore(element, entries);
        count++;
        continue;
      }
      if (!configuration.active) break;
      if (bodyPending) {
        if (document.body) {
          protectBody();
          count++;
          continue;
        }
        bodyPending = false;
      }
      if ((cssTurn || !jobs.length) && cssSourceStep()) { cssTurn = false; count++; continue; }
      const job = jobs[0];
      if (!job) { if (cssSourceStep()) { count++; continue; } break; }
      if (!job.next || !job.remaining || !job.root.isConnected ||
          (!job.initial && configuration.requireInteractionForUpdates &&
            (interactionUntil <= 0 || performance.now() > interactionUntil))) {
        if (!job.root.isConnected) releaseRule(job.root);
        jobs.shift();
        continue;
      }
      const element = job.next;
      job.next = job.shallow ? null : nextElement(element, job.root);
      job.remaining--;
      count++;
      cssTurn = true;
      if (element.isConnected && job.root.contains(element)) classify(element);
      else if (!element.isConnected) releaseRule(element);
    }
    schedule();
  }

  function mutations(records) {
    if (!configuration.active) return;
    if (document.body && protectedBody !== document.body) {
      bodyPending = true;
      protectBody(); // One bounded body operation before the next paint, not a subtree scan.
    }
    if (globalThis.CandyRedditSafeArea) {
      let remaining = 16;
      for (let index = 0; index < Math.min(records.length, 64) && remaining > 0; index++) {
        const record = records[index];
        if (record.type !== "childList") continue;
        for (let child = 0; child < record.addedNodes.length && remaining-- > 0; child++) {
          globalThis.CandyRedditSafeArea.added(record.addedNodes[child]);
        }
      }
    }
    // Source events are independent of the trusted DOM-discovery interaction window.
    let remaining = 64;
    for (let index = 0; index < Math.min(records.length, 128) && remaining > 0; index++) {
      const record = records[index];
      if (isOwnSource(record.target)) continue;
      if (record.type === "characterData") {
        sourceNode(record.target.parentElement);
        remaining--;
      } else if (record.type === "attributes") {
        if (["href", "rel", "media", "disabled"].includes(record.attributeName)) sourceNode(record.target);
      } else if (record.type === "childList") {
        sourceNode(record.target);
        const pending = [];
        for (const list of [record.addedNodes, record.removedNodes]) {
          for (let child = 0; child < Math.min(list.length, remaining); child++) pending.push(list[child]);
        }
        while (pending.length && remaining-- > 0) {
          const node = pending.shift();
          if (!(node instanceof Element) || isOwnSource(node)) continue;
          sourceNode(node);
          for (let child = node.firstElementChild; child && pending.length < 64; child = child.nextElementSibling) pending.push(child);
        }
      }
    }
    schedule();
    if (configuration.requireInteractionForUpdates &&
        (interactionUntil <= 0 || performance.now() > interactionUntil)) return;
    for (let index = 0; index < Math.min(records.length, 128); index++) {
      const record = records[index];
      if (isOwnSource(record.target) ||
          ["style", "link"].includes(record.target.localName)) continue;
      if (record.type === "attributes" && configuration.recheckChangedElements) {
        const own = ownWrites.get(record.target);
        if (record.attributeName === "style" && own &&
            own.after === (record.target.getAttribute("style") || "") && own.before.has(record.oldValue || "")) continue;
        enqueue(record.target);
      } else if (record.type === "childList" && configuration.recheckAddedElements) {
        for (let child = 0; child < Math.min(record.addedNodes.length, 16); child++) {
          if (!isOwnSource(record.addedNodes[child]) && !["style", "link"].includes(record.addedNodes[child].localName)) enqueue(record.addedNodes[child]);
        }
      }
    }
    schedule(configuration.mutationDebounceMillis);
  }

  function observe() {
    if (!configuration?.active || observer || !document.documentElement) return;
    observer = new MutationObserver(mutations);
    observer.observe(document.documentElement, { childList: true, subtree: true,
      characterData: true, attributes: true, attributeOldValue: true,
      attributeFilter: ["class", "style", "hidden", "href", "rel", "media", "disabled"] });
  }

  function configure(resize = false) {
    const incoming = globalThis.CandyContentTopInset?.cssSafeAreaConfiguration?.();
    if (!incoming) return;
    const scale = Number.isFinite(globalThis.devicePixelRatio) && globalThis.devicePixelRatio > 0 ? globalThis.devicePixelRatio : 1;
    const nextInset = Number.isFinite(incoming.cssSafeAreaTopInsetPx) ? Math.min(10000, Math.max(0, incoming.cssSafeAreaTopInsetPx)) / scale : 0;
    const bounded = (value, minimum, maximum, fallback) => Number.isSafeInteger(value) ? Math.min(maximum, Math.max(minimum, value)) : fallback;
    const next = { active: incoming.ready === true && incoming.enabled === true && nextInset > 0 &&
      !viewportFitCoversSafeArea(),
      recheckAddedElements: incoming.recheckAddedElements === true,
      recheckChangedElements: incoming.recheckChangedElements === true,
      requireInteractionForUpdates: incoming.requireInteractionForUpdates !== false,
      recheckOnResize: incoming.recheckOnResize === true,
      interactionWindowMillis: bounded(incoming.interactionWindowMillis, 100, 5000, 1000),
      mutationDebounceMillis: bounded(incoming.mutationDebounceMillis, 50, 1000, 150),
      maxElementsPerBatch: bounded(incoming.maxElementsPerBatch, 4, 64, 16),
      maxBatchDurationMillis: bounded(incoming.maxBatchDurationMillis, 1, 8, 4),
      maxInitialElements: bounded(incoming.maxInitialElements, 64, 2048, 512) };
    const key = JSON.stringify([next, nextInset, incoming.navigationGeneration]);
    if (key === configurationKey) {
      if (resize && next.active) { enqueue(document.body, true); schedule(); }
      return;
    }
    cancel();
    observer?.disconnect();
    observer = null;
    // Remove protection before any fresh computed-style read for the next inset.
    layer?.remove();
    layer = null;
    selectorLayer?.remove();
    selectorLayer = null;
    cleanup.push(...rules);
    rules.clear();
    markerName = `${markerPrefix}-${++layerEpoch}`;
    markerId = 0;
    protectedSelectors = [];
    selectorImportance.clear();
    selectorMatcher = "";
    selectorsScanned = false;
    sources.clear();
    selectorRules.clear();
    sourceEvents = 0;
    sourceWork = 0;
    for (const key of Object.keys(cssCounts)) cssCounts[key] = 0;
    cleanup.push(...owned);
    owned.clear();
    configuration = next;
    configurationKey = key;
    inset = nextInset;
    if (next.active && !firstInitialization) firstInitialization = { readyState: document.readyState, atMillis: performance.now() };
    bodyPending = next.active && !!document.body;
    semanticSeeded = false;
    initialDomSeeded = false;
    protectedBody = null;
    redditFlowProtected = false;
    refreshBodyAtReady = false;
    cssTurn = true;
    if (next.active && !cleanup.length && document.documentElement) {
      apply(document.documentElement, "--candy-safe-area-inset-top", `${inset}px`);
      protectBody();
    }
    startSelectorScan();
    observe();
    globalThis.CandyRedditSafeArea?.configure(next.active, () => {
      if (!configuration?.active) return;
      bodyPending = true;
      protectBody();
      schedule();
    });
    schedule();
  }

  function interaction(event) {
    if (!event.isTrusted || !configuration?.active) return;
    interactionUntil = performance.now() + configuration.interactionWindowMillis;
    if ((configuration.recheckAddedElements || configuration.recheckChangedElements) &&
        event.target !== document.body && event.target !== document.documentElement) {
      enqueue(event.target);
      schedule(configuration.mutationDebounceMillis);
    }
  }

  function scroll() {
    cancel();
    bodyPending = false;
    if (cleanup.length) schedule();
  }

  globalThis.__candyConfigureCssSafeArea = configure;
  document.addEventListener("DOMContentLoaded", () => {
    globalThis.CandyRedditSafeArea?.sync();
    configure();
    observe();
    startSelectorScan();
    if (configuration?.active && (protectedBody !== document.body || refreshBodyAtReady)) {
      bodyPending = true;
      protectBody();
    }
    seedInitialDom();
    schedule();
  }, { once: true });
  document.addEventListener("click", interaction, true);
  document.addEventListener("drop", interaction, true);
  document.addEventListener("load", (event) => {
    if (configuration?.active && event.target?.localName === "link") { sourceNode(event.target); schedule(); }
  }, true);
  globalThis.addEventListener("load", () => { globalThis.CandyRedditSafeArea?.sync(); startSelectorScan(); seedInitialDom(); seedSemanticHeader(); schedule(); }, { once: true });
  document.addEventListener("scroll", scroll, { capture: true, passive: true });
  globalThis.addEventListener("scroll", scroll, { passive: true });
  globalThis.addEventListener("resize", () => { if (configuration?.recheckOnResize) configure(true); }, { passive: true });
  configure();
  globalThis.CandyCssSafeAreaDiagnostics = Object.freeze({
    sample: () => globalThis.CandyContentTopInset?.domDiagnosticsEnabled?.() === true ? {
      active: configuration?.active === true, initialized: firstInitialization !== null,
      firstReadyState: firstInitialization?.readyState || "other", firstAtMillis: firstInitialization?.atMillis ?? null,
      ownedCount: rules.size + owned.size, unknownCount: 0, bodyPending,
      pendingJobCount: jobs.length, dirtyRootCount: 0, interactionActive: performance.now() < interactionUntil,
      cssSourceCount: Math.min(65535, sources.size), cssLateSourceCount: Math.min(65535, cssCounts.late),
      cssRulesVisited: Math.min(65535, cssCounts.rules), cssRulesApplied: Math.min(65535, selectorRules.size),
      cssSecurityErrors: Math.min(65535, cssCounts.errors), cssUnsupportedRules: Math.min(65535, cssCounts.unsupported),
      cssBudgetHits: Math.min(65535, cssCounts.capped), cssScrollCancellations: Math.min(65535, cssCounts.cancelled),
    } : null,
  });
})();
