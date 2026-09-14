"use strict";

// Reddit components keep scroll-state changes in CSS; no scroll-time DOM measurement.
(() => {
  if (globalThis.self !== globalThis.top) return;
  const hostname = globalThis.location?.hostname?.toLowerCase().replace(/\.$/, "") || "";
  if (hostname !== "reddit.com" && !hostname.endsWith(".reddit.com")) return;
  const appTag = "shreddit-app";
  const inset = "var(--candy-safe-area-inset-top, 0px)";
  const pagePadding = "var(--page-y-padding, 0px)";
  const layers = new Map();
  const observers = new Map();
  const definitions = new Set();
  let apps = new Set();
  let active = false;
  let protectedFlow = false;
  let flowChanged = null;
  let epoch = 0;
  let timer = 0;
  let structuralEvents = 0;

  function reportFlow(value) {
    if (protectedFlow === value) return;
    protectedFlow = value;
    flowChanged?.(value);
  }

  function css(prefix) {
    return `${prefix}reddit-header-small { top: ${inset} !important; }\n` +
      `${prefix}reddit-header-small.relative { top: calc(0px - ${pagePadding}) !important; }\n` +
      `${prefix}reddit-header-small[hidden-by-scroll] header { padding-top: ${inset} !important; }`;
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

  function schedule() {
    if (!active || timer || structuralEvents >= 256) return;
    structuralEvents++;
    const expected = epoch;
    timer = setTimeout(() => {
      timer = 0;
      if (active && epoch === expected) sync();
    }, 0);
  }

  function observe(target, desired) {
    if (!target) return;
    desired.add(target);
    if (observers.has(target) || observers.size >= 16) return;
    const observer = new MutationObserver((records) => {
      if (records.some((record) => [...record.addedNodes, ...record.removedNodes]
        .some((node) => node.nodeType === 1 && !ownsSource(node)))) schedule();
    });
    observer.observe(target, { childList: true });
    observers.set(target, observer);
  }

  function sync() {
    if (!active || !document.documentElement) return;
    for (const [root, style] of layers) {
      if (!style.isConnected) { style.remove(); layers.delete(root); }
    }
    const documentProtected = protect(document.documentElement,
      `:root ${appTag} { padding-top: calc(${pagePadding} + ${inset}) !important; }\n` +
      css(`:root ${appTag} `));
    const desired = new Set();
    const currentApps = new Set(Array.from(document.querySelectorAll(appTag)).slice(0, 2));
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
        for (const header of Array.from(scope.querySelectorAll("reddit-header-small")).slice(0, 4)) {
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
    apps = currentApps;
    for (const [target, observer] of observers) {
      if (!desired.has(target)) { observer.disconnect(); observers.delete(target); }
    }
    reportFlow(flow);
  }

  function inApp(element) {
    return apps.has(element?.closest?.(appTag)) || apps.has(element?.getRootNode?.().host);
  }

  function owns(element) {
    return active && element?.localName === "reddit-header-small" && inApp(element);
  }

  function ownsSource(owner) {
    return Array.from(layers.values()).includes(owner);
  }

  function added(node) {
    if (!active || node?.nodeType !== 1 || ownsSource(node)) return;
    if (!apps.size || node.localName === appTag ||
        (inApp(node) && (node.localName === "reddit-header-small" || node.classList?.contains("main-container")))) {
      schedule();
    }
  }

  function configure(enabled, callback) {
    flowChanged = typeof callback === "function" ? callback : null;
    if (active === !!enabled) { if (active) sync(); return; }
    active = !!enabled;
    epoch++;
    if (timer) clearTimeout(timer);
    timer = 0;
    if (!active) {
      for (const observer of observers.values()) observer.disconnect();
      observers.clear();
      for (const style of layers.values()) style.remove();
      layers.clear();
      apps.clear();
      reportFlow(false);
      return;
    }
    structuralEvents = 0;
    sync();
    for (const tag of [appTag, "reddit-header-small"]) {
      if (definitions.has(tag) || !globalThis.customElements?.whenDefined) continue;
      definitions.add(tag);
      globalThis.customElements.whenDefined(tag).then(() => {
        if (active) schedule();
      });
    }
  }

  globalThis.CandyRedditSafeArea = Object.freeze({ configure, sync, flowProtected: () => protectedFlow, owns, ownsSource, added });
})();
