"use strict";

// Reddit components keep scroll-state changes in CSS; no scroll-time DOM measurement.
(() => {
  if (globalThis.self !== globalThis.top) return;
  const hostname = globalThis.location?.hostname?.toLowerCase().replace(/\.$/, "") || "";
  if (hostname !== "reddit.com" && !hostname.endsWith(".reddit.com")) return;
  const appTag = "shreddit-app";
  const headerTags = ["reddit-header-small", "reddit-header-large", "shreddit-header"];
  const headerTagSet = new Set(headerTags);
  const inset = "max(var(--candy-safe-area-inset-top, 0px), env(safe-area-inset-top, 0px))";
  const pagePadding = "var(--page-y-padding, 0px)";
  const layers = new Map();
  const observers = new Map();
  const definitions = new Set();
  let apps = new Set();
  let headers = new Set();
  let active = false;
  let protectedFlow = false;
  let flowChanged = null;
  let epoch = 0;
  let timer = 0;
  let structuralEvents = 0;
  let structuralChangePending = false;
  let navigationGeneration = null;

  function reportFlow(value) {
    if (protectedFlow === value) return;
    protectedFlow = value;
    flowChanged?.(value);
  }

  function css(prefix) {
    return headerTags.map((tag) =>
      `${prefix}${tag} { top: ${inset} !important; }\n` +
      `${prefix}${tag}.relative { top: calc(0px - ${pagePadding}) !important; }\n` +
      `${prefix}${tag}[hidden-by-scroll] header { padding-top: ${inset} !important; }`,
    ).join("\n");
  }

  function protect(root, text) {
    const retained = layers.get(root);
    if (retained?.isConnected) return true;
    if (!root || layers.size >= 8) return false;
    const style = document.createElement("style");
    style.textContent = text;
    root.appendChild(style);
    layers.set(root, style);
    return true;
  }

  function schedule(structuralChange = false) {
    if (structuralChange) structuralChangePending = true;
    if (!active || timer || structuralEvents >= 256) return;
    structuralEvents++;
    const expected = epoch;
    timer = setTimeout(() => {
      timer = 0;
      const changed = structuralChangePending;
      structuralChangePending = false;
      if (active && epoch === expected) sync(changed);
    }, 0);
  }

  function observe(target, desired) {
    if (!target) return;
    desired.add(target);
    if (observers.has(target) || observers.size >= 16) return;
    const observer = new MutationObserver((records) => {
      if (records.some((record) => record.removedNodes.length)) releaseDetachedElements();
      if (records.some((record) => [...record.addedNodes, ...record.removedNodes]
        .some((node) => node.nodeType === 1 && !ownsSource(node)))) schedule(true);
    });
    observer.observe(target, { childList: true });
    observers.set(target, observer);
  }

  function releaseDetachedElements() {
    // Releasing bounded ownership must still work after discovery exhausts its
    // per-navigation structural budget, including roots detached by an ancestor.
    for (const [root, style] of layers) {
      if (!style.isConnected) { style.remove(); layers.delete(root); }
    }
    for (const [target, observer] of observers) {
      if (!target.isConnected) { observer.disconnect(); observers.delete(target); }
    }
    for (const candidates of [apps, headers]) {
      for (const element of candidates) {
        if (!element.isConnected) candidates.delete(element);
      }
    }
    if (!apps.size) reportFlow(false);
  }

  function sync(structuralChange = false) {
    if (!active || !document.documentElement) return;
    for (const [root, style] of layers) {
      if (!style.isConnected) { style.remove(); layers.delete(root); }
    }
    const documentProtected = protect(document.documentElement,
      `:root ${appTag} { padding-top: calc(${pagePadding} + ${inset}) !important; }\n` +
      css(`:root ${appTag} `));
    const desired = new Set();
    const currentApps = new Set(Array.from(document.querySelectorAll(appTag)).slice(0, 2));
    const currentHeaders = new Set();
    let flow = false;
    if (!currentApps.size) observe(document.documentElement, desired);
    for (const app of currentApps) {
      if (documentProtected) flow = true;
      observe(app, desired);
      observe(app.parentElement, desired);
      const scopes = [app];
      if (app.shadowRoot && protect(app.shadowRoot, `${css("")}\n` +
          `:host { padding-top: calc(${pagePadding} + ${inset}) !important; }`)) {
        scopes.push(app.shadowRoot);
        observe(app.shadowRoot, desired);
      }
      for (const scope of scopes) {
        const main = scope.querySelector(".main-container");
        if (main) observe(main.parentElement, desired);
        const scopeHeaders = headerTags.flatMap((tag) => Array.from(scope.querySelectorAll(tag))).slice(0, 4);
        for (const header of scopeHeaders) {
          currentHeaders.add(header);
          observe(header, desired);
          if (header.shadowRoot) {
            protect(header.shadowRoot, `:host { top: ${inset} !important; }\n` +
              `:host(.relative) { top: calc(0px - ${pagePadding}) !important; }\n` +
              `:host([hidden-by-scroll]) header { padding-top: ${inset} !important; }`);
            observe(header.shadowRoot, desired);
          }
        }
      }
    }
    const headersChanged = headers.size !== currentHeaders.size ||
      Array.from(currentHeaders).some((header) => !headers.has(header));
    const flowWasChanged = protectedFlow !== flow;
    apps = currentApps;
    headers = currentHeaders;
    for (const [target, observer] of observers) {
      if (!desired.has(target)) { observer.disconnect(); observers.delete(target); }
    }
    reportFlow(flow);
    if (headersChanged && !flowWasChanged) flowChanged?.(flow);
    if (structuralChange && !headersChanged && !flowWasChanged) flowChanged?.(flow, true);
  }

  function inApp(element) {
    return apps.has(element?.closest?.(appTag)) || apps.has(element?.getRootNode?.().host);
  }

  function owns(element) {
    return active && headerTagSet.has(element?.localName) && inApp(element);
  }

  function ownsSource(owner) {
    return Array.from(layers.values()).includes(owner);
  }

  function added(node) {
    if (!active || node?.nodeType !== 1 || ownsSource(node)) return;
    if (!apps.size || node.localName === appTag ||
        (inApp(node) && (headerTagSet.has(node.localName) || node.classList?.contains("main-container")))) {
      schedule();
    }
  }

  function configure(enabled, callback, generation) {
    flowChanged = typeof callback === "function" ? callback : null;
    if (navigationGeneration !== generation) {
      navigationGeneration = generation;
      structuralEvents = 0;
    }
    if (active === !!enabled) { if (active) sync(); return; }
    active = !!enabled;
    epoch++;
    if (timer) clearTimeout(timer);
    timer = 0;
    structuralChangePending = false;
    if (!active) {
      for (const observer of observers.values()) observer.disconnect();
      observers.clear();
      for (const style of layers.values()) style.remove();
      layers.clear();
      apps.clear();
      headers.clear();
      reportFlow(false);
      return;
    }
    structuralEvents = 0;
    sync();
    for (const tag of [appTag, ...headerTags]) {
      if (definitions.has(tag) || !globalThis.customElements?.whenDefined) continue;
      definitions.add(tag);
      globalThis.customElements.whenDefined(tag).then(() => {
        if (active) schedule();
      });
    }
  }

  globalThis.CandyRedditSafeArea = Object.freeze({ configure, sync, flowProtected: () => protectedFlow,
    headerCandidates: () => Array.from(headers).slice(0, 8), owns, ownsSource, added, releaseDetachedElements });
})();
