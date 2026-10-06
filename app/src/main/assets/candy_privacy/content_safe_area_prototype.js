"use strict";

// Experimental top-document policy: no iframe, containing-block or footprint proof.
(() => {
  if (globalThis.self !== globalThis.top) return;
  const owned = new Map();
  const ownWrites = new WeakMap();
  const rules = new Map();
  const topCandidates = new Set();
  const negativeTopElements = new Map();
  let topMutationStates = new WeakMap();
  let topInlineValues = new WeakMap();
  let checkedSelectorTops = new WeakSet();
  let negativeTopMarker = "";
  const hostname = typeof globalThis.location?.hostname === "string" ? globalThis.location.hostname.toLowerCase().replace(/\.$/, "") : "";
  const amazonIndia = hostname === "amazon.in" || hostname.endsWith(".amazon.in");
  const knownTopSelectors = hostname === "amazon.de" || hostname.endsWith(".amazon.de") ?
    [":root #btf-sub-nav-top-navigation-bar.persistent-header"] :
    amazonIndia ? [":root .s-mobile-toolbar-sticky"] :
    ["google.com", "google.de"].some((host) => hostname === host || hostname.endsWith(`.${host}`)) ?
      [":root #navd", ":root #tsf .A7Yvie.emcav"] : [];
  const hiddenTopSelectors = amazonIndia ? [":root .s-mobile-toolbar-sticky.s-mobile-toolbar-offscreen"] : [];
  const knownTopMatcher = knownTopSelectors.join(", ");
  const markerPrefix = `data-candy-safe-area-${Math.random().toString(36).slice(2)}`;
  let layerEpoch = 0;
  let markerName = "";
  let markerId = 0;
  let layer = null;
  let fullscreenRoot = document.fullscreenElement;
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
  let coverLayoutRevision = 0;
  let coverCheckedRevision = -1;
  let coverCheckedInset = -1;
  let coverNeedsProtection = false;
  let coverEnvProbe = null;
  let coverEnvWatchGeneration = 0;
  let coverEnvRecheckedInset = null;
  let inset = 0;
  let jobs = [];
  let cleanup = [];
  let timer = 0;
  let timerDue = 0;
  let timerEpoch = 0;
  let observer = null;
  let interactionUntil = 0;
  let bodyPending = false;
  let semanticCheckPending = false;
  let semanticChecks = 0;
  let coverSemanticSeeded = false;
  let initialDomSeeded = false;
  let protectedBody = null;
  let redditFlowProtected = false;
  let refreshBodyAtReady = false;
  let cssTurn = true;
  let nativeFallbackRequested = false;
  let nativeFallbackThemeColor = null;
  let statusBarBackdropHeaderConfirmed = false;
  let statusBarBackdropHeader = null;
  let reportedStatusBarBackdropColor = null;
  let reportedStatusBarBackdropRevision = null;
  let statusBarBackdropPending = false;
  const themeMediaWatchers = new Map();
  let fixedHeaderCandidates = new Map();
  let semanticHeaderCandidates = new Set();
  const headerTopCandidates = new Set();
  const dirtyHeaderTops = new Set();
  const headerTopSelectorOverrides = new Map();
  let headerTopMutationStates = new WeakMap();
  let headerTopCheckDue = 0;
  let lastHeaderTopCheck = -Infinity;
  let headerTopMarker = "";
  let backdropPriorityCandidates = [];
  let backdropActivationStates = new WeakMap();
  let coverLayoutMutationStates = new WeakMap();
  let coverLayoutPending = false;
  let coverLayoutDue = 0;
  let routeCoverSettlingUntil = 0;
  let routeCoverRechecks = 0;
  let viewportOverlayCandidates = [];
  const knownViewportOverlays = new Set();
  const viewportOverlayContents = new WeakMap();
  let scrollListenersAttached = false;
  let headerVerificationPending = false;
  let scrollGeneration = 0;
  const semanticSelector = 'header, nav, [role="banner"], [role="navigation"]';
  const knownNativeHeaderSelector = hostname === "reddit.com" || hostname.endsWith(".reddit.com")
    ? 'reddit-header-small, reddit-header-large, shreddit-header'
    : '';
  const semanticHeaderSelector = knownNativeHeaderSelector
    ? `${knownNativeHeaderSelector}, ${semanticSelector}`
    : semanticSelector;
  const maxFixedHeaderCandidates = 8;
  const maxSemanticHeaderCandidates = 32;
  const maxBackdropStyleChecks = 24;
  const maxBackdropPriorityCandidates = 8;
  const minimumHeaderVerificationQuietMillis = 150;
  const maxSemanticChecks = 256;
  const maxHeaderTopCandidates = 8;
  const minimumHeaderTopCheckIntervalMillis = 50;

  function viewportFitCoversSafeArea() {
    const content = document.querySelector('meta[name="viewport" i]')?.getAttribute("content");
    return typeof content === "string" &&
      /(?:^|[\s,;])viewport-fit\s*=\s*cover(?=$|[\s,;])/i.test(content);
  }

  function visibleNearTop(element, style, safeTop) {
    if (style.display === "none" || style.visibility === "hidden" || style.visibility === "collapse" ||
        Number.parseFloat(style.opacity) <= 0.01) return false;
    const rect = element.getBoundingClientRect();
    return rect.width > 1 && rect.height > 1 && rect.top < safeTop - 0.5 && rect.bottom > 1;
  }

  function zeroInset(value) {
    const parts = typeof value === "string" ? value.trim().split(/\s+/) : [];
    return parts.length > 0 && parts.length <= 4 &&
      parts.every((part) => /^(?:0|0px)$/.test(part));
  }

  function isViewportFixedOverlay(element, style, top = pixels(style.top), bottom = pixels(style.bottom)) {
    if (style.position !== "fixed" || top === null || bottom === null ||
        Math.abs(top) > 1 || Math.abs(bottom) > 1) return false;
    const rect = element.getBoundingClientRect();
    const width = Math.max(1, globalThis.innerWidth || document.documentElement.clientWidth || 0);
    const height = Math.max(1, globalThis.innerHeight || document.documentElement.clientHeight || 0);
    return rect.width >= width - 2 && rect.height >= height - 2 &&
      rect.left <= 2 && rect.top <= 2 && rect.right >= width - 2 && rect.bottom >= height - 2;
  }

  function isViewportModalSurface(element, style, top, bottom, safeTop = inset) {
    if (!isViewportFixedOverlay(element, style, top, bottom)) return false;
    const firstChild = element.firstElementChild;
    if (firstChild && firstChild.getBoundingClientRect().top >= safeTop - 0.5) return false;
    if (element.getAttribute("role") === "dialog" || element.getAttribute("aria-modal") === "true") return true;
    const identity = `${element.id || ""} ${element.className || ""}`;
    if (/(?:backdrop|scrim|dimmer|shade|veil|overlay)/i.test(identity)) return false;
    if (/(?:modal|dialog|interstitial)/i.test(identity)) return true;
    return !!firstChild || !!element.textContent?.trim();
  }

  function viewportDialogContent(element, style, safeTop = inset) {
    if (!isViewportFixedOverlay(element, style)) return null;
    const content = element.firstElementChild;
    if (!content || (content.getAttribute("role") !== "dialog" &&
        content.getAttribute("aria-modal") !== "true" &&
        !/(?:modal|dialog)/i.test(`${content.id || ""} ${content.className || ""}`))) return null;
    const rect = content.getBoundingClientRect();
    const width = Math.max(1, globalThis.innerWidth || document.documentElement.clientWidth || 0);
    const height = Math.max(1, globalThis.innerHeight || document.documentElement.clientHeight || 0);
    if (rect.width < width - 2 || rect.height < height - 2 ||
        rect.left > 2 || rect.top > 2 || rect.right < width - 2 || rect.bottom < height - 2) return null;
    const contentStyle = getComputedStyle(content);
    const paddingTop = pixels(contentStyle.paddingTop);
    if (contentStyle.boxSizing !== "border-box" || paddingTop === null ||
        paddingTop >= safeTop - 0.5 ||
        content.firstElementChild?.getBoundingClientRect().top >= safeTop - 0.5) return null;
    return { element: content, paddingTop };
  }

  function releaseViewportOverlayRules(element) {
    for (const content of viewportOverlayContents.get(element) || []) releaseRule(content);
    viewportOverlayContents.delete(element);
    releaseRule(element);
  }

  function protectViewportDialogContent(content) {
    const paddingTop = Math.max(content.paddingTop, inset);
    applyRule(content.element, "padding-top", `${paddingTop}px`);
    const protectedElements = [content.element];
    const header = content.element.firstElementChild;
    const controls = header ? [header, ...Array.from(header.children || []).slice(0, 8)] : [];
    for (const control of controls) {
      if (control.localName !== "button" && control.getAttribute("role") !== "button") continue;
      const style = getComputedStyle(control);
      const top = pixels(style.top);
      if (style.position !== "absolute" || top === null || top < 0 ||
          control.getBoundingClientRect().top >= inset - 0.5) continue;
      applyRule(control, "top", `${Math.max(top + paddingTop - content.paddingTop, inset)}px`);
      protectedElements.push(control);
    }
    return protectedElements;
  }

  function coverFlowNeedsProtection(safeTop) {
    if (!document.body || globalThis.CandyRedditSafeArea?.flowProtected() === true) return false;
    if ((pixels(getComputedStyle(document.body).paddingTop) || 0) >= safeTop - 0.5) return false;
    let element = document.body.firstElementChild;
    for (let inspected = 0; element && inspected < 16; inspected++) {
      const style = getComputedStyle(element);
      if (["fixed", "absolute"].includes(style.position) || !visibleNearTop(element, style, safeTop)) {
        element = element.nextElementSibling;
        continue;
      }
      if ((pixels(style.paddingTop) || 0) >= safeTop - 0.5 ||
          ["video", "canvas", "img", "svg"].includes(element.localName)) return false;
      if (element.firstElementChild && inspected < 8) {
        element = element.firstElementChild;
        continue;
      }
      let child = element.firstChild;
      for (let checked = 0; child && checked < 8; checked++, child = child.nextSibling) {
        if (child.nodeType === 3 && child.textContent?.slice(0, 128).trim()) return true;
      }
      return null;
    }
    return null;
  }

  function coverTopNeedsProtection(safeTop) {
    if (!document.body || safeTop <= 0) return false;
    const anchorNeedsProtection = (element) => {
      const style = getComputedStyle(element);
      if (style.position !== "fixed" && style.position !== "sticky") return false;
      if (viewportDialogContent(element, style, safeTop)) return true;
      const rect = element.getBoundingClientRect();
      if (!visibleNearTop(element, style, safeTop) ||
          (rect.height > globalThis.innerHeight / 2 && !isViewportModalSurface(element, style, undefined, undefined, safeTop)) ||
          (pixels(style.top) ?? safeTop) >= safeTop - 0.5) return false;
      if ((pixels(style.top) || 0) + (pixels(style.paddingTop) || 0) >= safeTop - 0.5) return false;
      const firstChild = element.firstElementChild;
      return !firstChild || firstChild.getBoundingClientRect().top < safeTop - 0.5;
    };
    for (const element of knownViewportOverlays) {
      if (!element.isConnected) { knownViewportOverlays.delete(element); continue; }
      if (anchorNeedsProtection(element)) return true;
    }
    for (const element of Array.from(document.querySelectorAll(semanticSelector)).slice(0, 8)) {
      if (anchorNeedsProtection(element)) return true;
    }
    for (let element = document.body.firstElementChild, inspected = 0;
        element && inspected < 64; element = nextElement(element, document.body), inspected++) {
      if (anchorNeedsProtection(element)) return true;
    }
    for (const element of Array.from(document.body.children || []).slice(-8)) {
      if (anchorNeedsProtection(element)) return true;
    }
    return coverFlowNeedsProtection(safeTop) === true;
  }

  function normalizedOpaqueColor(value) {
    if (typeof value !== "string") return null;
    const color = value.trim().toLowerCase();
    const shortHex = /^#([0-9a-f]{3})$/.exec(color);
    if (shortHex) return `#${[...shortHex[1]].map((channel) => channel + channel).join("")}`;
    if (/^#[0-9a-f]{6}$/.test(color)) return color;
    const rgb = /^rgba?\(\s*([\d.]+)(?:\s*,\s*|\s+)([\d.]+)(?:\s*,\s*|\s+)([\d.]+)(?:\s*[,/]\s*([\d.]+)(%)?)?\s*\)$/.exec(color);
    if (!rgb) return null;
    if (rgb[4] !== undefined && (rgb[5] ? Number(rgb[4]) < 100 : Number(rgb[4]) < 1)) return null;
    const values = rgb.slice(1, 4).map(Number);
    if (values.some((channel) => !Number.isFinite(channel))) return null;
    const channels = values.map((channel) =>
      Math.min(255, Math.max(0, Math.round(channel))).toString(16).padStart(2, "0"));
    return `#${channels.join("")}`;
  }

  function activeThemeColor() {
    const candidates = document.querySelectorAll?.('meta[name="theme-color" i]') || [];
    for (const candidate of candidates) {
      const media = candidate.getAttribute("media");
      if (media && globalThis.matchMedia && !globalThis.matchMedia(media).matches) continue;
      const color = normalizedOpaqueColor(candidate.getAttribute("content"));
      if (color) return color;
    }
    return null;
  }

  function watchThemeColorMedia() {
    for (const candidate of document.querySelectorAll?.('meta[name="theme-color" i]') || []) {
      const media = candidate.getAttribute("media");
      if (!media || themeMediaWatchers.has(media) || themeMediaWatchers.size >= 8 ||
          !globalThis.matchMedia) continue;
      const query = globalThis.matchMedia(media);
      const changed = () => {
        if (!statusBarBackdropHeaderConfirmed) return;
        statusBarBackdropPending = true;
        schedule();
      };
      query.addEventListener?.("change", changed);
      themeMediaWatchers.set(media, { query, changed });
    }
  }

  function headerThemeColor(element, style) {
    const declared = activeThemeColor();
    if (declared) return declared;
    for (let current = element; current; current = current.parentElement) {
      const color = normalizedOpaqueColor(current === element ? style.backgroundColor : getComputedStyle(current).backgroundColor);
      if (color) return color;
      if (current === document.body || current === document.documentElement) break;
    }
    return (document.body ? normalizedOpaqueColor(getComputedStyle(document.body).backgroundColor) : null) ||
      normalizedOpaqueColor(getComputedStyle(document.documentElement).backgroundColor);
  }

  function nextHeaderContentAnchor(element) {
    for (let current = element; current && current !== document.body &&
        current !== document.documentElement; current = composedParent(current)) {
      for (let sibling = current.nextElementSibling; sibling; sibling = sibling.nextElementSibling) {
        if (!['link', 'meta', 'script', 'style'].includes(sibling.localName)) return sibling;
      }
    }
    return null;
  }

  function rememberFixedHeaderCandidate(element, rect, scrollY) {
    if (!fixedHeaderCandidates.has(element) &&
        fixedHeaderCandidates.size >= maxFixedHeaderCandidates) return null;
    const anchor = nextHeaderContentAnchor(element);
    const candidate = {
      anchor,
      anchorTop: anchor?.getBoundingClientRect().top ?? null,
      minimumScrollDelta: Math.max(rect.height, inset),
      scrollGeneration,
      scrollY,
    };
    fixedHeaderCandidates.set(element, candidate);
    return candidate;
  }

  function fixedHeaderHasMeaningfulScroll(candidate, scrollY) {
    if (scrollGeneration <= candidate.scrollGeneration) return false;
    if (scrollY - candidate.scrollY >= candidate.minimumScrollDelta) return true;
    if (!candidate.anchor?.isConnected || !Number.isFinite(candidate.anchorTop)) return false;
    return candidate.anchorTop - candidate.anchor.getBoundingClientRect().top >=
      candidate.minimumScrollDelta;
  }

  function requestNativeFallbackForHeader(element, style) {
    if (nativeFallbackRequested || !configuration?.active || configuration.cover) return false;
    if (style.position !== "fixed" && style.position !== "sticky") return false;
    const opacity = Number.parseFloat(style.opacity);
    if (style.display === "none" || style.visibility === "hidden" ||
        style.visibility === "collapse" || (Number.isFinite(opacity) && opacity <= 0.01)) {
      if (style.position === "fixed") fixedHeaderCandidates.delete(element);
      return false;
    }
    const top = pixels(style.top);
    if (top === null || top < -0.5 || top > inset + 0.5) return false;
    const headerLikeTag = /(?:^|-)(?:header|topbar|navbar)(?:-|$)/.test(element.localName || "");
    if (!headerLikeTag && !element.matches(semanticSelector) &&
        !element.querySelector?.(semanticSelector)) return false;
    const rect = element.getBoundingClientRect();
    const viewportWidth = Math.max(1, globalThis.innerWidth || document.documentElement.clientWidth || 0);
    const viewportHeight = Math.max(1, globalThis.innerHeight || document.documentElement.clientHeight || 0);
    if (rect.width < viewportWidth * 0.5 || rect.height <= 1 || rect.height > viewportHeight * 0.5 ||
        rect.top < -0.5 || rect.top > inset + 0.5) {
      if (style.position === "fixed") fixedHeaderCandidates.delete(element);
      return false;
    }
    if (style.position === "fixed") {
      const scrollY = Number(globalThis.scrollY) || 0;
      let candidate = fixedHeaderCandidates.get(element);
      if (!candidate) {
        rememberFixedHeaderCandidate(element, rect, scrollY);
        return false;
      }
      if (!fixedHeaderHasMeaningfulScroll(candidate, scrollY)) {
        if (scrollY < candidate.scrollY) {
          candidate = rememberFixedHeaderCandidate(element, rect, scrollY);
        }
        return false;
      }
    }
    nativeFallbackRequested = true;
    nativeFallbackThemeColor = headerThemeColor(element, style);
    document.documentElement.setAttribute("data-candy-browser-native-top-header", "true");
    globalThis.CandyContentTopInset?.fallbackToNative?.(
      configuration.navigationGeneration,
      configuration.revision,
      nativeFallbackThemeColor,
      true,
    );
    return true;
  }

  function verifyFixedHeaderCandidates() {
    const visited = new Set();
    for (const [element] of fixedHeaderCandidates) {
      visited.add(element);
      if (!element.isConnected) {
        fixedHeaderCandidates.delete(element);
        continue;
      }
      const style = getComputedStyle(element);
      if (style.position !== "fixed") {
        fixedHeaderCandidates.delete(element);
        if (style.position === "sticky" && requestNativeFallbackForHeader(element, style)) {
          return true;
        }
        continue;
      }
      if (requestNativeFallbackForHeader(element, style)) return true;
    }
    for (const element of semanticHeaderCandidates) {
      if (visited.has(element)) continue;
      if (!element.isConnected) {
        semanticHeaderCandidates.delete(element);
        continue;
      }
      const style = getComputedStyle(element);
      if (style.position === "fixed" || style.position === "sticky") {
        if (requestNativeFallbackForHeader(element, style)) return true;
      }
    }
    return false;
  }

  function reportStatusBarBackdrop() {
    if (nativeFallbackRequested || !configuration?.safeAreaEnabled) return;
    if (statusBarBackdropHeaderConfirmed && !statusBarBackdropHeader?.isConnected) {
      statusBarBackdropHeaderConfirmed = false;
      statusBarBackdropHeader = null;
      publishStatusBarBackdrop(null);
    }
    watchThemeColorMedia();
    const themeColor = activeThemeColor();
    if (statusBarBackdropHeaderConfirmed) {
      publishStatusBarBackdrop(themeColor);
      return;
    }
    if (!themeColor) {
      publishStatusBarBackdrop(null);
      return;
    }
    const viewportWidth = Math.max(1, globalThis.innerWidth || document.documentElement.clientWidth || 0);
    const viewportHeight = Math.max(1, globalThis.innerHeight || document.documentElement.clientHeight || 0);
    const candidates = [...backdropPriorityCandidates,
      ...(globalThis.CandyRedditSafeArea?.headerCandidates?.() || []),
      ...semanticHeaderCandidates];
    const visited = new Set();
    candidateLoop: for (const candidate of candidates) {
      for (let element = candidate, depth = 0; element && depth < 8;
          element = composedParent(element), depth++) {
        if (!element.isConnected || visited.has(element) || element === document.body ||
            element === document.documentElement) continue;
        if (visited.size >= maxBackdropStyleChecks) break candidateLoop;
        visited.add(element);
        const style = getComputedStyle(element);
        if (style.position !== "fixed" && style.position !== "sticky") continue;
        if (style.display === "none" || style.visibility === "hidden" ||
            style.visibility === "collapse" || Number.parseFloat(style.opacity) <= 0.01) continue;
        const top = pixels(style.top);
        if (style.top === "auto" || (top !== null && (top < inset - 2 || top > inset + 2))) continue;
        const rect = element.getBoundingClientRect();
        if (rect.width < viewportWidth * 0.5 || rect.height <= 1 ||
            rect.height > viewportHeight * 0.5 || rect.top < inset - 2 ||
            rect.top > (style.position === "sticky" ? viewportHeight * 0.5 : inset + 2)) continue;
        statusBarBackdropHeaderConfirmed = true;
        statusBarBackdropHeader = element;
        backdropPriorityCandidates = [];
        coverLayoutMutationStates.set(element, `class:${element.getAttribute("class") || ""}`);
        publishStatusBarBackdrop(themeColor);
        return;
      }
    }
    publishStatusBarBackdrop(null);
  }

  function publishStatusBarBackdrop(color, policy = configuration, forcePublish = false) {
    const revision = `${policy.navigationGeneration}:${policy.revision}`;
    if (!forcePublish && color === null && reportedStatusBarBackdropColor === null) {
      reportedStatusBarBackdropRevision = revision;
      return;
    }
    if (!forcePublish && color === reportedStatusBarBackdropColor && revision === reportedStatusBarBackdropRevision) return;
    reportedStatusBarBackdropColor = color;
    reportedStatusBarBackdropRevision = revision;
    globalThis.CandyContentTopInset?.statusBarBackdrop?.(
      policy.navigationGeneration, policy.revision, color);
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
    // Amazon hides its sticky toolbar by translating its full height above top 0.
    // Preserve that anchor while hidden; class changes stay entirely in CSS.
    layer.textContent = configuration?.cover ? "" : [
      ...knownTopSelectors.map((selector) =>
        `${topSelector(selector)} { top: calc(0px + var(--candy-safe-area-inset-top)) !important; }`),
      ...hiddenTopSelectors.map((selector) => `${topSelector(selector)} { top: 0px !important; }`)
    ].join("\n");
    document.documentElement.appendChild(layer);
    if (!layer.sheet) { layer.remove(); layer = null; return null; }
    return layer.sheet;
  }

  function selectorOwns(element) {
    if (globalThis.CandyRedditSafeArea?.owns(element)) return true;
    if (headerTopSelectorOverrides.has(element)) return false;
    if (configuration?.cover) return false;
    if (!selectorMatcher && !knownTopMatcher) return false;
    try {
      if (knownTopMatcher && element.matches(knownTopMatcher)) return true;
      return !!selectorMatcher && element.matches(selectorMatcher);
    }
    catch { return false; }
  }

  function topSelector(selector) {
    // Append zero-specificity exclusions to each complete selector, preserving
    // commas inside functional pseudo-classes, attributes and quoted strings.
    const selectors = [];
    let start = 0, depth = 0, quote = "", pseudoElement = -1;
    const append = (end) => {
      const insertion = pseudoElement >= start ? pseudoElement : end;
      const exclusion = `:not(:where([${headerTopMarker}]))` +
        (configuration.addInsetToNegativeTop ? "" : `:not(:where([${negativeTopMarker}]))`);
      selectors.push(`${selector.slice(start, insertion).trimEnd()}${exclusion}${selector.slice(insertion, end)}`);
    };
    for (let index = 0; index < selector.length; index++) {
      const character = selector[index];
      if (character === "\\") { index++; continue; }
      if (quote) { if (character === quote) quote = ""; continue; }
      if (character === '"' || character === "'") { quote = character; continue; }
      if (character === "(" || character === "[") depth++;
      else if (character === ")" || character === "]") depth--;
      else if (character === ":" && selector[index + 1] === ":" && depth === 0 && pseudoElement < 0) pseudoElement = index;
      else if (character === "," && depth === 0) {
        append(index);
        start = index + 1;
        pseudoElement = -1;
      }
    }
    append(selector.length);
    return selectors.map((value) => value.trim()).join(", ");
  }

  function withAuthorTopStyles(read) {
    const sheets = [layer?.sheet, selectorLayer?.sheet].filter(Boolean);
    const disabled = sheets.map((sheet) => sheet.disabled);
    try {
      for (const sheet of sheets) sheet.disabled = true;
      return read();
    } finally {
      for (let index = 0; index < sheets.length; index++) sheets[index].disabled = disabled[index];
    }
  }

  function preserveNegativeTop(element, top) {
    if (configuration.addInsetToNegativeTop) return false;
    if (top !== null && top < 0) {
      releaseElementTop(element);
      if (!negativeTopElements.has(element) && negativeTopElements.size < configuration.maxInitialElements) {
        const entry = { attribute: negativeTopMarker, id: "true", original: element.getAttribute(negativeTopMarker) };
        negativeTopElements.set(element, entry);
        element.setAttribute(negativeTopMarker, entry.id);
      }
      return true;
    }
    const entry = negativeTopElements.get(element);
    if (entry) {
      restore(element, entry);
      negativeTopElements.delete(element);
    }
    return false;
  }

  function recheckNegativeTopMutations(records) {
    if (configuration.addInsetToNegativeTop || !configuration.active) return;
    const changed = new Set();
    for (const record of records.slice(0, 64)) {
      if (record.type === "childList") {
        const pending = Array.from(record.addedNodes || []).slice(0, 8).map((element) => ({ element, depth: 0 }));
        let remaining = 16;
        while (pending.length && remaining-- > 0 && changed.size < 16) {
          const { element, depth } = pending.shift();
          if (!(element instanceof Element) || isOwnSource(element)) continue;
          if (selectorOwns(element)) { rememberTopCandidate(element); changed.add(element); }
          if (depth < 2) {
            for (const child of Array.from(element.children || []).slice(0, 4)) {
              pending.push({ element: child, depth: depth + 1 });
            }
          }
        }
        if (changed.size >= 16) break;
        continue;
      }
      if (record.type !== "attributes" || !["class", "style"].includes(record.attributeName) ||
          isOwnSource(record.target)) continue;
      const target = record.target;
      const state = `${target.getAttribute("class") || ""}\n${target.getAttribute("style") || ""}`;
      if (topMutationStates.get(target) === state) continue;
      topMutationStates.set(target, state);
      const inlineTop = target.style.getPropertyValue("top");
      const previousInlineTop = topInlineValues.get(target);
      topInlineValues.set(target, inlineTop);
      if (record.attributeName === "style" && !negativeTopElements.has(target)) {
        const top = pixels(inlineTop);
        if ((!inlineTop && !previousInlineTop) || (top !== null && top >= 0)) continue;
      }
      if (topCandidates.has(target) || selectorOwns(target)) changed.add(target);
      if (record.attributeName === "class") {
        for (const candidate of topCandidates) {
          if (changed.size >= 16) break;
          if (target !== candidate && target.contains(candidate)) changed.add(candidate);
        }
      }
      if (changed.size >= 16) break;
    }
    // Known headers use the coalesced author-layout pass, including positive tops.
    for (const element of headerTopCandidates) changed.delete(element);
    if (!changed.size) return;
    const authorTops = withAuthorTopStyles(() => Array.from(changed).slice(0, 16).map((element) =>
      [element, pixels(getComputedStyle(element).top)]));
    for (const [element, top] of authorTops) {
      if (!element.isConnected) { topCandidates.delete(element); continue; }
      if (selectorOwns(element)) checkedSelectorTops.add(element);
      const wasNegative = negativeTopElements.has(element);
      if (!preserveNegativeTop(element, top) && wasNegative) classify(element);
    }
  }

  function rememberTopCandidate(element) {
    if (semanticHeaderCandidates.has(element) || element.matches(semanticHeaderSelector) ||
        /(?:header|topbar|navbar|masthead)/i.test(`${element.id || ""} ${element.className || ""}`)) {
      rememberHeaderTopCandidate(element);
    }
    if (topCandidates.has(element) || topCandidates.size >= configuration.maxInitialElements) return;
    topCandidates.add(element);
    topMutationStates.set(element, `${element.getAttribute("class") || ""}\n${element.getAttribute("style") || ""}`);
    topInlineValues.set(element, element.style.getPropertyValue("top"));
  }

  function rememberHeaderTopCandidate(element) {
    pruneHeaderTopCandidates();
    if (headerTopCandidates.has(element) || headerTopCandidates.size >= maxHeaderTopCandidates) return;
    headerTopCandidates.add(element);
    headerTopMutationStates.set(element, headerTopMutationState(element));
  }

  function pruneHeaderTopCandidates() {
    for (const header of headerTopCandidates) {
      if (header.isConnected) continue;
      releaseElementTop(header);
      const entry = headerTopSelectorOverrides.get(header);
      if (entry) restore(header, entry);
      headerTopSelectorOverrides.delete(header);
      headerTopCandidates.delete(header);
      dirtyHeaderTops.delete(header);
    }
  }

  function headerTopMutationState(element) {
    return JSON.stringify(["class", "style", "hidden"].map((name) => element.getAttribute(name)));
  }

  function touchesKnownHeader(target) {
    if (!(target instanceof Element)) return false;
    for (const header of headerTopCandidates) {
      if (target === header || target.contains(header)) return true;
    }
    return false;
  }

  function queueHeaderTopCheck(element) {
    if (!configuration?.active || nativeFallbackRequested || !element.isConnected) return;
    dirtyHeaderTops.add(element);
    headerTopCheckDue = Math.max(performance.now(), lastHeaderTopCheck + minimumHeaderTopCheckIntervalMillis);
  }

  function queueKnownHeaderTopChecks() {
    pruneHeaderTopCandidates();
    for (const header of headerTopCandidates) queueHeaderTopCheck(header);
    schedule();
  }

  function queueHeaderTopMutations(records) {
    if (!configuration?.active || !configuration.recheckChangedElements) return;
    pruneHeaderTopCandidates();
    for (const record of records.slice(0, 128)) {
      const target = record.type === "characterData" ? record.target?.parentElement : record.target;
      if (!(target instanceof Element) || isOwnSource(target)) continue;
      if (["style", "link"].includes(target.localName)) {
        if (record.type !== "attributes" || ["href", "rel", "media", "disabled"].includes(record.attributeName)) {
          for (const header of headerTopCandidates) queueHeaderTopCheck(header);
        }
        continue;
      }
      if (record.type !== "attributes" || !["class", "style", "hidden"].includes(record.attributeName) ||
          !touchesKnownHeader(target)) continue;
      const own = ownWrites.get(target);
      if (record.attributeName === "style" && own && own.after === (target.getAttribute("style") || "") &&
          own.before.has(record.oldValue || "")) continue;
      const state = headerTopMutationState(target);
      if (headerTopMutationStates.get(target) === state) continue;
      headerTopMutationStates.set(target, state);
      for (const header of headerTopCandidates) {
        if (target === header || target.contains(header)) queueHeaderTopCheck(header);
      }
    }
    schedule();
  }

  function refreshHeaderTops() {
    const headers = Array.from(dirtyHeaderTops).slice(0, Math.min(maxHeaderTopCandidates, configuration.maxElementsPerBatch));
    for (const header of headers) dirtyHeaderTops.delete(header);
    lastHeaderTopCheck = performance.now();
    if (dirtyHeaderTops.size) headerTopCheckDue = lastHeaderTopCheck + minimumHeaderTopCheckIntervalMillis;
    // Snapshot primitive author values while both owned sheets are disabled once.
    // A browser style flush can exceed the cooperative worker's time budget.
    const samples = withAuthorTopStyles(() => headers.filter((header) => header.isConnected).map((header) => {
      const author = getComputedStyle(header);
      const style = {};
      for (const name of ["position", "top", "bottom", "paddingTop", "display", "visibility", "opacity"]) style[name] = author[name];
      if (configuration.cover) style.authorChildTop = header.firstElementChild?.getBoundingClientRect().top;
      return [header, style];
    }));
    for (const [header, style] of samples) {
      if (globalThis.CandyRedditSafeArea?.owns(header)) continue;
      if (!configuration.cover && !headerTopSelectorOverrides.has(header)) {
        const entry = { attribute: headerTopMarker, id: "true", original: header.getAttribute(headerTopMarker) };
        headerTopSelectorOverrides.set(header, entry);
        header.setAttribute(entry.attribute, entry.id);
      }
      classify(header, style, true);
      if (style.position === "sticky" || (style.position === "fixed" && !fixedHeaderCandidates.has(header))) {
        requestNativeFallbackForHeader(header, style);
      }
    }
    pruneHeaderTopCandidates();
  }

  function releaseElementTop(element) {
    const entry = rules.get(element);
    if (!entry) return;
    if (entry.rule.style.getPropertyValue("top")) entry.rule.style.removeProperty("top");
    if (!entry.rule.style.getPropertyValue("padding-top")) releaseRule(element);
  }

  function startSelectorScan() {
    if (selectorsScanned || !configuration?.active || configuration.cover || document.readyState === "loading") return;
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
    if (configuration?.cover) return;
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
      if (invalidated || node.localName === "style" ||
          (node.getAttribute("rel") || "").toLowerCase().split(/\s+/).includes("stylesheet")) {
        queueKnownHeaderTopChecks();
      }
      // A connected replacement may not expose sheet until load. Retain the last
      // committed protection while waiting, rather than committing an empty gap.
    }
  }

  function isOwnSource(node) {
    return globalThis.CandyRedditSafeArea?.ownsSource(node) ||
      node === layer || node === selectorLayer || node === selectorBuild?.staging || node === coverEnvProbe ||
      layer?.contains(node) || selectorLayer?.contains(node) || selectorBuild?.staging?.contains(node);
  }

  function watchCoverNativeInset() {
    const generation = ++coverEnvWatchGeneration;
    if (!configuration?.cover) {
      coverEnvProbe?.remove();
      coverEnvProbe = null;
      return;
    }
    if (!document.body || !globalThis.requestAnimationFrame) return;
    if (!coverEnvProbe?.isConnected) {
      coverEnvProbe = document.createElement("div");
      coverEnvProbe.style.setProperty("position", "fixed");
      coverEnvProbe.style.setProperty("visibility", "hidden");
      coverEnvProbe.style.setProperty("padding-top", "env(safe-area-inset-top, 0px)");
      document.body.appendChild(coverEnvProbe);
    }
    const deadline = performance.now() + 3000;
    let lastNativeInset = null;
    let frames = 0;
    const sample = () => {
      if (generation !== coverEnvWatchGeneration || !configuration?.cover) return;
      if (++frames % 4 !== 0) { globalThis.requestAnimationFrame(sample); return; }
      const nativeInset = pixels(getComputedStyle(coverEnvProbe).paddingTop) || 0;
      if (lastNativeInset !== null && Math.abs(nativeInset - lastNativeInset) > 0.5) {
        coverEnvRecheckedInset = inset;
        coverLayoutRevision++;
        configure();
        return;
      }
      lastNativeInset = nativeInset;
      if (coverNeedsProtection && nativeInset >= inset - 0.5 && coverEnvRecheckedInset !== inset) {
        // The first author-layout check may have run before Gecko delivered env().
        coverEnvRecheckedInset = inset;
        coverLayoutRevision++;
        configure();
        return;
      }
      if (nativeInset < inset - 0.5 && performance.now() < deadline) globalThis.requestAnimationFrame(sample);
    };
    globalThis.requestAnimationFrame(sample);
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
      // Retain negative candidates through source precedence to supersede an
      // earlier positive declaration of the same selector, without publishing it.
      if (candidate.top < 0 && !configuration.addInsetToNegativeTop) return;
      const sheet = build.staging.sheet;
      try {
        const text = `${topSelector(selector)} { top: ${candidate.top + inset}px !important; }`;
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
    if (position === "fixed" &&
        (rule.style.getPropertyValue("bottom") || rule.style.getPropertyValue("inset"))) return;
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
  function applyRule(element, name, value, replace = false) {
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
    const previous = entry.rule.style.getPropertyValue(name);
    if ((!previous || replace) && previous !== value) entry.rule.style.setProperty(name, value, "important");
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
    semanticCheckPending = false;
    headerVerificationPending = false;
    statusBarBackdropPending = false;
    coverLayoutPending = false;
    interactionUntil = 0;
  }

  function schedule(delay = 0) {
    const otherWorkPending = cleanup.length > 0 || bodyPending || jobs.length > 0 || !!selectorScan ||
      !!selectorBuild || !!sourceDiscovery || sourceQueue.length > 0 || semanticCheckPending ||
      headerVerificationPending || statusBarBackdropPending || coverLayoutPending || viewportOverlayCandidates.length > 0;
    if (!otherWorkPending && !dirtyHeaderTops.size) return;
    if (coverLayoutPending && !cleanup.length && !bodyPending && !jobs.length && !selectorScan &&
        !selectorBuild && !sourceDiscovery && !sourceQueue.length && !semanticCheckPending &&
        !headerVerificationPending && !statusBarBackdropPending && !viewportOverlayCandidates.length) {
      delay = Math.max(delay, coverLayoutDue - performance.now());
    }
    if (!cleanup.length && !bodyPending && !jobs.length && !selectorScan && !selectorBuild &&
        !sourceDiscovery && !semanticCheckPending && !headerVerificationPending &&
        !statusBarBackdropPending && !coverLayoutPending && !viewportOverlayCandidates.length &&
        sourceQueue.length) {
      delay = Math.max(delay, Math.max(0, Math.min(...sourceQueue.map((source) => source.due)) - performance.now()));
    }
    if (dirtyHeaderTops.size) {
      const headerDelay = Math.max(0, headerTopCheckDue - performance.now());
      delay = otherWorkPending ? Math.min(delay, headerDelay) : headerDelay;
    }
    const due = performance.now() + Math.max(0, delay);
    if (timer && timerDue <= due) return;
    if (timer) clearTimeout(timer);
    timerDue = due;
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

  function queueViewportOverlayCandidate(element, recheckKnown = false) {
    if (!(element instanceof Element) || !element.isConnected || isOwnSource(element) ||
        (knownViewportOverlays.has(element) && !recheckKnown) ||
        viewportOverlayCandidates.includes(element) || viewportOverlayCandidates.length >= 16) return;
    viewportOverlayCandidates.push(element);
  }

  function hasInlineFullViewportInset(element) {
    const inline = element?.getAttribute?.("style");
    if (!inline || !/(?:inset|top|bottom)/i.test(inline)) return false;
    const style = element?.style;
    if (!style) return false;
    const top = style.getPropertyValue("top").trim();
    const bottom = style.getPropertyValue("bottom").trim();
    return zeroInset(style.getPropertyValue("inset")) ||
      (/^(?:0|0px)$/.test(top) && /^(?:0|0px)$/.test(bottom));
  }

  function hasViewportOverlayHint(element) {
    return element?.getAttribute?.("role") === "dialog" ||
      element?.getAttribute?.("aria-modal") === "true" ||
      /(?:modal|dialog|interstitial)/i.test(`${element?.id || ""} ${element?.className || ""}`);
  }

  function queueAddedViewportOverlays(records) {
    if (!configuration?.safeAreaEnabled) return;
    let remaining = 16;
    for (const record of records.slice(-32).reverse()) {
      if (remaining <= 0) break;
      if (record.type === "attributes" && ["style", "class", "role", "aria-modal"].includes(record.attributeName)) {
        const target = record.target;
        remaining--;
        const known = knownViewportOverlays.has(target) ? target :
          (knownViewportOverlays.has(target.parentElement) ? target.parentElement : null);
        if (known) {
          if (record.oldValue !== target.getAttribute(record.attributeName)) {
            queueViewportOverlayCandidate(known, true);
          }
        } else if (["role", "aria-modal"].includes(record.attributeName) &&
            hasViewportOverlayHint(target.parentElement)) {
          queueViewportOverlayCandidate(target.parentElement);
        } else if (hasInlineFullViewportInset(target) || hasViewportOverlayHint(target)) {
          queueViewportOverlayCandidate(target);
        }
        continue;
      }
      if (record.type !== "childList") continue;
      if (knownViewportOverlays.has(record.target)) {
        queueViewportOverlayCandidate(record.target, true);
      } else if (knownViewportOverlays.has(record.target?.parentElement)) {
        queueViewportOverlayCandidate(record.target.parentElement, true);
      }
      const pending = Array.from(record.addedNodes || []).slice(-8).reverse()
        .map((element) => ({ element, depth: 0 }));
      while (pending.length && remaining > 0) {
        const { element, depth } = pending.shift();
        remaining--;
        if (!(element instanceof Element) || isOwnSource(element)) continue;
        if (hasInlineFullViewportInset(element) || hasViewportOverlayHint(element)) {
          queueViewportOverlayCandidate(element);
        }
        if (depth >= 2) continue;
        for (const child of Array.from(element.children || []).slice(-4).reverse()) {
          pending.push({ element: child, depth: depth + 1 });
        }
      }
    }
    if (viewportOverlayCandidates.length) schedule(configuration.mutationDebounceMillis);
  }

  function composedParent(element) {
    return element?.parentElement || element?.getRootNode?.().host || null;
  }

  function rememberBackdropHeaderCandidate(candidate) {
    backdropPriorityCandidates = backdropPriorityCandidates.filter((element) => element !== candidate);
    backdropPriorityCandidates.push(candidate);
    if (backdropPriorityCandidates.length > maxBackdropPriorityCandidates) {
      backdropPriorityCandidates.shift();
    }
    for (let element = candidate, depth = 0; element && depth < 8;
        element = composedParent(element), depth++) {
      if (semanticHeaderCandidates.size >= maxSemanticHeaderCandidates) break;
      semanticHeaderCandidates.add(element);
    }
  }

  function changedAttributeState(states, element, attributeName) {
    const state = `${attributeName}:${element.getAttribute(attributeName) || ""}`;
    if (states.get(element) === state) return false;
    states.set(element, state);
    return true;
  }

  function isCoverLayoutAnchor(element) {
    if (element === document.body) return true;
    if (["header", "nav", "main", "reddit-header-small", "reddit-header-large",
        "shreddit-header"].includes(element?.localName)) return true;
    const role = element?.getAttribute?.("role");
    return role === "banner" || role === "navigation";
  }

  function containsRelevantAddedNode(nodes, selector) {
    const queue = Array.from(nodes || []).slice(0, 8).map((element) => ({ element, depth: 0 }));
    let remaining = 64;
    while (queue.length && remaining-- > 0) {
      const { element, depth } = queue.shift();
      if (element?.nodeType !== 1 || isOwnSource(element)) continue;
      if (element.matches?.(selector)) return true;
      if (depth >= 4) continue;
      for (let child = element.firstElementChild, checked = 0; child && checked < 8;
          child = child.nextElementSibling, checked++) {
        queue.push({ element: child, depth: depth + 1 });
      }
    }
    return false;
  }

  function seedSemanticHeaders(allowLoading = false) {
    const loading = document.readyState === "loading";
    if (!configuration?.safeAreaEnabled || !document.body || (loading && !allowLoading) ||
        nativeFallbackRequested || semanticChecks >= maxSemanticChecks ||
        (configuration.cover && coverSemanticSeeded)) return;
    const candidates = [];
    if (knownNativeHeaderSelector) {
      candidates.push(...Array.from(document.querySelectorAll(knownNativeHeaderSelector)).slice(0, 4));
    }
    for (const candidate of document.querySelectorAll(semanticSelector)) {
      if (!candidates.includes(candidate)) candidates.push(candidate);
      if (candidates.length >= 8) break;
    }
    if (loading && !candidates.length) return;
    semanticChecks++;
    if (configuration.cover && !loading) coverSemanticSeeded = true;
    const visited = new Set();
    for (const candidate of candidates) {
      rememberHeaderTopCandidate(candidate);
      let current = candidate;
      for (let depth = 0; current && current !== document.body &&
          current !== document.documentElement && depth < 8; depth++, current = composedParent(current)) {
        if (visited.has(current)) continue;
        visited.add(current);
        if (semanticHeaderCandidates.size < maxSemanticHeaderCandidates) {
          semanticHeaderCandidates.add(current);
        }
        if (configuration.active) classify(current, getComputedStyle(current));
        if (nativeFallbackRequested) return;
      }
    }
  }

  function requestSemanticHeaderCheck(delay = 0) {
    if (!configuration?.safeAreaEnabled || nativeFallbackRequested ||
        semanticChecks >= maxSemanticChecks) return;
    if (!configuration.cover || !coverSemanticSeeded) semanticCheckPending = true;
    if (!statusBarBackdropHeaderConfirmed) statusBarBackdropPending = true;
    schedule(delay);
  }

  function nextElement(element, root) {
    if (element.firstElementChild) return element.firstElementChild;
    for (let current = element; current && current !== root; current = current.parentElement) {
      if (current.nextElementSibling) return current.nextElementSibling;
    }
    return null;
  }

  function isRootAbsoluteHeader(element, style, top) {
    if (style.position !== "absolute" || top === null || top < 0 ||
        top > Math.max(64, inset * 2)) return false;
    const offsetParent = element.offsetParent;
    if (offsetParent !== document.body && offsetParent !== document.documentElement) return false;
    if (!element.matches(semanticSelector) &&
        !/(?:header|topbar|navbar|masthead)/i.test(`${element.id} ${element.className}`)) return false;
    const rect = element.getBoundingClientRect();
    const viewportWidth = Math.max(1, globalThis.innerWidth || document.documentElement.clientWidth || 0);
    const viewportHeight = Math.max(1, globalThis.innerHeight || document.documentElement.clientHeight || 0);
    return rect.width >= viewportWidth * 0.5 && rect.height > 1 && rect.height <= viewportHeight * 0.5;
  }

  function classify(element, style, refreshTop = false) {
    // Gecko presents container fullscreen as a fixed box. It is not a page header:
    // retaining its top inset after it becomes inline clips video in its parent.
    if (document.fullscreenElement?.contains(element)) {
      releaseElementTop(element);
      return;
    }
    const topHeaderChecked = style !== undefined;
    if (topHeaderChecked && style.position !== "fixed") fixedHeaderCandidates.delete(element);
    if (!refreshTop && topHeaderChecked && (style.position === "fixed" || style.position === "sticky") &&
        requestNativeFallbackForHeader(element, style)) return;
    if (selectorOwns(element)) {
      rememberTopCandidate(element);
      if (!configuration.addInsetToNegativeTop && !checkedSelectorTops.has(element)) {
        preserveNegativeTop(element, withAuthorTopStyles(() => pixels(getComputedStyle(element).top)));
        checkedSelectorTops.add(element);
      }
      releaseElementTop(element);
      return;
    }
    const entry = rules.get(element);
    if (!refreshTop && entry && layer?.isConnected && element.getAttribute(entry.attribute) === entry.id &&
        entry.rule.style.getPropertyValue("top")) return;
    if (entry && (!layer?.isConnected || element.getAttribute(entry.attribute) !== entry.id)) releaseRule(element);
    style ??= getComputedStyle(element);
    if (style.position !== "fixed") fixedHeaderCandidates.delete(element);
    const top = pixels(style.top);
    const rootAbsoluteHeader = isRootAbsoluteHeader(element, style, top);
    if (style.position !== "fixed" && style.position !== "sticky" && !rootAbsoluteHeader) {
      if (refreshTop) releaseElementTop(element);
      return;
    }
    rememberTopCandidate(element);
    if (preserveNegativeTop(element, top)) return;
    if (!refreshTop && !topHeaderChecked && requestNativeFallbackForHeader(element, style)) return;
    // CSSOM can resolve an auto top to pixels for a bottom-anchored fixed box.
    // Keep fixed boxes extending into the lower half out of top-inset rules.
    const bottom = style.position === "fixed" ? pixels(style.bottom) : null;
    if (style.position === "fixed" && top !== null &&
        (top >= globalThis.innerHeight / 2 || (bottom !== null && bottom <= globalThis.innerHeight / 2)) &&
        !isViewportModalSurface(element, style, top, bottom)) {
      if (refreshTop) releaseElementTop(element);
      return;
    }
    if (top === null) {
      if (refreshTop) releaseElementTop(element);
      return;
    }
    if (configuration.cover) {
      if (top >= inset - 0.5 || top + (pixels(style.paddingTop) || 0) >= inset - 0.5 ||
          (refreshTop ? style.authorChildTop : element.firstElementChild?.getBoundingClientRect().top) >= inset - 0.5) {
        if (refreshTop) releaseElementTop(element);
        return;
      }
      applyRule(element, "top", `${top + inset}px`, refreshTop);
    } else applyRule(element, "top", `${top + inset}px`, refreshTop);
  }

  function seedInitialDom() {
    if (initialDomSeeded || !configuration?.active || document.readyState === "loading" || !document.body) return;
    initialDomSeeded = true;
    requestSemanticHeaderCheck();
    const job = enqueue(document.body, true);
    // Body plus eight reserved shallow checks share the original DOM node cap.
    if (job) { job.next = nextElement(document.body, document.body); job.remaining -= 9; }
  }

  function protectBody() {
    if (!configuration?.active || (cleanup.length && !configuration.cover) ||
        !document.body || !document.documentElement) return;
    bodyPending = cleanup.length > 0;
    if (!cleanup.length) apply(document.documentElement, "--candy-safe-area-inset-top", `${inset}px`);
    const flowProtected = globalThis.CandyRedditSafeArea?.flowProtected() === true;
    if ((refreshBodyAtReady && document.readyState !== "loading") || flowProtected !== redditFlowProtected) {
      // Do not measure our early padding as author padding. Remove/read/republish
      // in this same task, so later parser CSS is preserved without a paint gap.
      rules.get(document.body)?.rule.style.removeProperty("padding-top");
      refreshBodyAtReady = false;
    }
    const style = getComputedStyle(document.body);
    const padding = pixels(style.paddingTop);
    if (padding !== null && (!configuration.cover || coverFlowNeedsProtection(inset) === true)) {
      applyRule(document.body, "padding-top", `${flowProtected ? padding : Math.max(padding, inset)}px`);
    }
    redditFlowProtected = flowProtected;
    classify(document.body, style);
    protectedBody = document.body;
    if (document.readyState === "loading") refreshBodyAtReady = true;
    // Cover flow can start with an SVG logo and need no body padding. Its
    // sticky header still needs protection before the parser's first paint.
    if (configuration.cover && document.readyState === "loading") seedSemanticHeaders(true);
    seedInitialDom();
  }

  function work() {
    const started = performance.now();
    let count = 0;
    while (count < configuration.maxElementsPerBatch &&
        performance.now() - started < configuration.maxBatchDurationMillis) {
      if (coverLayoutPending && performance.now() >= coverLayoutDue) {
        coverLayoutPending = false;
        if (routeCoverSettlingUntil > 0) routeCoverRechecks++;
        coverLayoutRevision++;
        configure();
        return;
      }
      if (cleanup.length) {
        const [element, entries] = cleanup.shift();
        restore(element, entries);
        count++;
        continue;
      }
      if (dirtyHeaderTops.size && performance.now() >= headerTopCheckDue) {
        refreshHeaderTops();
        count += Math.min(maxHeaderTopCandidates, configuration.maxElementsPerBatch);
        continue;
      }
      if (headerVerificationPending) {
        headerVerificationPending = false;
        verifyFixedHeaderCandidates();
        count++;
        continue;
      }
      if (semanticCheckPending) {
        semanticCheckPending = false;
        seedSemanticHeaders();
        count++;
        continue;
      }
      if (statusBarBackdropPending) {
        statusBarBackdropPending = false;
        reportStatusBarBackdrop();
        count++;
        continue;
      }
      if (viewportOverlayCandidates.length) {
        const element = viewportOverlayCandidates.shift();
        if (element.isConnected) {
          const wasKnown = knownViewportOverlays.has(element);
          if (wasKnown) {
            releaseViewportOverlayRules(element);
          }
          const style = getComputedStyle(element);
          const content = viewportDialogContent(element, style);
          if (content || isViewportModalSurface(element, style)) {
            knownViewportOverlays.add(element);
            if (knownViewportOverlays.size > 8) {
              const oldest = knownViewportOverlays.values().next().value;
              knownViewportOverlays.delete(oldest);
              releaseViewportOverlayRules(oldest);
            }
            if (configuration.cover && !configuration.active) {
              coverLayoutRevision++;
              configure();
              queueViewportOverlayCandidate(element, true);
              schedule();
              return;
            }
            if (content) {
              viewportOverlayContents.set(element, protectViewportDialogContent(content));
            } else classify(element, style);
          } else if (wasKnown) {
            knownViewportOverlays.delete(element);
            if (configuration.cover) {
              coverLayoutRevision++;
              configure();
              return;
            }
          }
        }
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

  function requestRouteCoverRecheck() {
    if (!configuration?.cover || performance.now() >= routeCoverSettlingUntil ||
        routeCoverRechecks >= 3) return;
    coverLayoutPending = true;
    coverLayoutDue = performance.now() + minimumHeaderVerificationQuietMillis;
    schedule(minimumHeaderVerificationQuietMillis);
  }

  function releaseDetachedElements() {
    // Ownership is bounded, but a single detached node can retain a large page
    // subtree. Sweep metadata even outside the trusted DOM-discovery window.
    // Check connectivity only; removal must never schedule layout discovery.
    globalThis.CandyRedditSafeArea?.releaseDetachedElements?.();
    for (const element of rules.keys()) {
      if (!element.isConnected) releaseRule(element);
    }
    for (const [element, entries] of owned) {
      if (element.isConnected) continue;
      restore(element, entries);
      owned.delete(element);
    }
    for (const [element, entry] of negativeTopElements) {
      if (element.isConnected) continue;
      restore(element, entry);
      negativeTopElements.delete(element);
    }
    for (const candidates of [topCandidates, fixedHeaderCandidates, semanticHeaderCandidates]) {
      for (const element of candidates.keys()) {
        if (!element.isConnected) candidates.delete(element);
      }
    }
    backdropPriorityCandidates = backdropPriorityCandidates.filter((element) => element.isConnected);
    viewportOverlayCandidates = viewportOverlayCandidates.filter((element) => element.isConnected);
    jobs = jobs.filter((job) => job.root.isConnected);
    let removedSources = false;
    for (const [sheet, source] of sources) {
      if (!source.owner || source.owner.isConnected) continue;
      sources.delete(sheet);
      if (selectorScan?.source === source) selectorScan = null;
      removedSources = true;
    }
    if (removedSources) {
      sourceQueue = sourceQueue.filter((source) => sources.has(source.sheet));
      beginSelectorBuild();
    }
  }

  function mutations(records) {
    if (records.some((record) => record.type === "childList" && record.removedNodes?.length)) {
      releaseDetachedElements();
    }
    queueHeaderTopMutations(records);
    recheckNegativeTopMutations(records);
    const wasCover = configuration?.cover === true;
    let removedViewportOverlay = false;
    for (const element of knownViewportOverlays) {
      if (element.isConnected) continue;
      knownViewportOverlays.delete(element);
      releaseViewportOverlayRules(element);
      removedViewportOverlay = true;
    }
    if (removedViewportOverlay && wasCover) {
      coverLayoutRevision++;
      configure();
      queueAddedViewportOverlays(records);
      return;
    }
    queueAddedViewportOverlays(records);
    let coverAttributeChanged = false;
    const changedCoverLayout = records.slice(0, 32).some((record) => {
      const target = record.target;
      if (isOwnSource(target)) return false;
      if (!wasCover) {
        if (record.type === "attributes") {
          return record.attributeName === "content" && target?.matches?.('meta[name="viewport" i]');
        }
        return record.type === "childList" &&
          (containsRelevantAddedNode(record.addedNodes, 'meta[name="viewport" i]') ||
            containsRelevantAddedNode(record.removedNodes, 'meta[name="viewport" i]'));
      }
      if (record.type === "attributes") {
        if (["class", "style"].includes(record.attributeName)) {
          if (isCoverLayoutAnchor(target) &&
              changedAttributeState(coverLayoutMutationStates, target, record.attributeName)) {
            coverAttributeChanged = true;
          }
          return false;
        }
        return (target?.matches?.('meta[name="viewport" i]') && record.attributeName === "content") ||
          (["href", "rel", "media", "disabled"].includes(record.attributeName) &&
            ["style", "link"].includes(target?.localName));
      }
      if (record.type === "characterData") return target?.parentElement?.localName === "style";
      if (record.type !== "childList") return false;
      const selector = `${semanticSelector}, main, meta[name="viewport" i], style, link`;
      return containsRelevantAddedNode(record.addedNodes, selector) ||
        containsRelevantAddedNode(record.removedNodes, selector);
    });
    if (changedCoverLayout && (wasCover || viewportFitCoversSafeArea())) {
      coverLayoutRevision++;
      configure();
    } else if (coverAttributeChanged && wasCover) {
      if (document.readyState === "loading") {
        // Parser-time CSS can promote an already inserted header to sticky.
        coverLayoutRevision++;
        configure();
      } else {
        coverLayoutPending = true;
        coverLayoutDue = performance.now() + minimumHeaderVerificationQuietMillis;
        schedule(minimumHeaderVerificationQuietMillis);
      }
    }
    if (wasCover && records.slice(0, 32).some((record) => record.type === "childList" &&
          [...Array.from(record.addedNodes || []).slice(0, 8),
            ...Array.from(record.removedNodes || []).slice(0, 8)].some((node) =>
            node?.nodeType === 1 && node.localName !== "style" && !isOwnSource(node)))) {
      // SPA routers can move ordinary divs after the URL event. Re-probe only
      // during this short route transition, without work on scroll.
      requestRouteCoverRecheck();
    }
    const themeChanged = records.slice(0, 64).some((record) => {
      if (record.type === "attributes") {
        return ["content", "media"].includes(record.attributeName) &&
          record.target?.matches?.('meta[name="theme-color" i]');
      }
      if (record.type !== "childList") return false;
      if (record.target !== document.head && record.target !== document.documentElement &&
          record.target?.localName !== "head") return false;
      return [...Array.from(record.addedNodes || []).slice(0, 8),
        ...Array.from(record.removedNodes || []).slice(0, 8)].some((node) =>
        node?.matches?.('meta[name="theme-color" i]'));
    });
    if (themeChanged && configuration?.safeAreaEnabled) {
      statusBarBackdropPending = true;
      schedule();
    }
    if (configuration.cover && !configuration.active) {
      if (statusBarBackdropHeaderConfirmed) {
        if (!statusBarBackdropHeader?.isConnected) {
          statusBarBackdropPending = true;
          schedule();
        }
      } else {
        let changedHeader = false;
        let remainingNodes = 64;
        for (const record of records.slice(0, 16)) {
          if (record.type === "attributes") {
            const target = record.target;
            if (["class", "style"].includes(record.attributeName) &&
                (semanticHeaderCandidates.has(target) || target?.matches?.(semanticHeaderSelector)) &&
                changedAttributeState(backdropActivationStates, target, record.attributeName)) {
              rememberBackdropHeaderCandidate(target);
              changedHeader = true;
            }
            continue;
          }
          if (record.type !== "childList") continue;
          const queue = Array.from(record.addedNodes || []).slice(0, 8)
            .map((element) => ({ element, depth: 0 }));
          while (queue.length && remainingNodes-- > 0) {
            const { element, depth } = queue.shift();
            if (element?.nodeType !== 1) continue;
            if (element.matches?.(semanticHeaderSelector)) {
              rememberBackdropHeaderCandidate(element);
              changedHeader = true;
            }
            if (depth >= 4) continue;
            for (let child = element.firstElementChild, checked = 0; child && checked < 8;
                child = child.nextElementSibling, checked++) {
              queue.push({ element: child, depth: depth + 1 });
            }
          }
        }
        if (changedHeader) {
          statusBarBackdropPending = true;
          schedule();
        }
      }
    } else {
      const semanticMutation = records.slice(0, 64).some((record) => {
        if (record.type === "attributes") {
          if (touchesKnownHeader(record.target)) return false;
          return record.target?.matches?.(semanticHeaderSelector) ||
            !!record.target?.querySelector?.(semanticSelector);
        }
        if (record.type !== "childList") return false;
        if (record.target?.matches?.(semanticHeaderSelector) ||
            (record.target !== document.body && record.target !== document.documentElement &&
              !!record.target?.querySelector?.(semanticSelector))) return true;
        return Array.from(record.addedNodes || []).slice(0, 16).some((node) =>
          node?.nodeType === 1 &&
          (node.matches?.(semanticHeaderSelector) || !!node.querySelector?.(semanticSelector)));
      });
      if (semanticMutation) requestSemanticHeaderCheck();
    }
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
    let bodyRechecked = false;
    for (let index = 0; index < Math.min(records.length, 128); index++) {
      const record = records[index];
      if (isOwnSource(record.target) ||
          ["style", "link"].includes(record.target.localName)) continue;
      if (record.type === "attributes" && configuration.recheckChangedElements) {
        const own = ownWrites.get(record.target);
        if (record.attributeName === "style" && own &&
            own.after === (record.target.getAttribute("style") || "") && own.before.has(record.oldValue || "")) continue;
        if (!bodyRechecked && configuration.cover && record.target === document.body &&
            ["class", "style"].includes(record.attributeName)) {
          // A scroll lock can turn the body into the sticky header's fixed
          // containing block. Protect that one box before the next paint.
          const padding = pixels(getComputedStyle(document.body).paddingTop) || 0;
          if (padding < inset - 0.5) {
            const child = Array.from(semanticHeaderCandidates).slice(0, 8).find((candidate) => {
              if (!candidate.isConnected || !document.body.contains(candidate) ||
                  !candidate.matches(semanticHeaderSelector)) return false;
              const rect = candidate.getBoundingClientRect();
              return rect.height > 1 && rect.top >= 0 && rect.top <= inset + 64;
            }) || document.body.firstElementChild;
            const childTop = child?.getBoundingClientRect().top;
            const alreadyProtected = !!rules.get(document.body)?.rule.style.getPropertyValue("top");
            // Existing Candy offsets on its sticky child are not author safety.
            withAuthorTopStyles(() => {
              document.body.getBoundingClientRect();
              classify(document.body);
            });
            // Some engines keep the child's sticky offset in a fixed body.
            // Retain that working offset instead of adding a second inset.
            if (!alreadyProtected && child?.isConnected && childTop >= inset - 0.5 &&
                child.getBoundingClientRect().top > childTop + 0.5) releaseElementTop(document.body);
          }
          bodyRechecked = true;
        }
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
    if ((!configuration?.active && !configuration?.cover) || observer || !document.documentElement) return;
    observer = new MutationObserver(mutations);
    observer.observe(document.documentElement, { childList: true, subtree: true,
      characterData: true, attributes: true, attributeOldValue: true,
      attributeFilter: ["class", "style", "hidden", "role", "aria-modal", "href", "rel", "media", "disabled", "content"] });
  }

  function updateScrollListeners() {
    const needed = configuration?.active === true;
    if (needed === scrollListenersAttached) return;
    if (needed) {
      document.addEventListener("scroll", scroll, { capture: true, passive: true });
      globalThis.addEventListener("scroll", scroll, { passive: true });
    } else {
      document.removeEventListener("scroll", scroll, true);
      globalThis.removeEventListener("scroll", scroll);
    }
    scrollListenersAttached = needed;
  }

  function configure(resize = false) {
    const incoming = globalThis.CandyContentTopInset?.cssSafeAreaConfiguration?.();
    if (!incoming) return;
    const scale = Number.isFinite(globalThis.devicePixelRatio) && globalThis.devicePixelRatio > 0 ? globalThis.devicePixelRatio : 1;
    const nextInset = Number.isFinite(incoming.cssSafeAreaTopInsetPx) ? Math.min(10000, Math.max(0, incoming.cssSafeAreaTopInsetPx)) / scale : 0;
    const redditActive = incoming.ready === true && incoming.enabled === true && nextInset > 0 &&
      !!globalThis.CandyRedditSafeArea;
    const bounded = (value, minimum, maximum, fallback) => Number.isSafeInteger(value) ? Math.min(maximum, Math.max(minimum, value)) : fallback;
    const nextNavigationGeneration = Number.isSafeInteger(incoming.navigationGeneration) ?
      Math.max(0, incoming.navigationGeneration) : 0;
    const routeChanged = configuration !== null &&
      configuration.navigationGeneration !== nextNavigationGeneration;
    const cover = viewportFitCoversSafeArea();
    const recheckCover = cover && document.body &&
      (routeChanged || coverCheckedRevision !== coverLayoutRevision || coverCheckedInset !== nextInset);
    if (recheckCover) {
      // Restore the current protection in this task. An unchanged cover decision
      // must not detach its stylesheet and wait for the worker to rebuild it.
      coverNeedsProtection = withAuthorTopStyles(() => coverTopNeedsProtection(nextInset));
      coverCheckedRevision = coverLayoutRevision;
      coverCheckedInset = nextInset;
    }
    const next = { safeAreaEnabled: incoming.ready === true && incoming.enabled === true && nextInset > 0,
      active: incoming.ready === true && incoming.enabled === true && nextInset > 0 &&
      (!cover || coverNeedsProtection), cover,
      navigationGeneration: nextNavigationGeneration,
      recheckAddedElements: incoming.recheckAddedElements === true,
      recheckChangedElements: incoming.recheckChangedElements === true,
      requireInteractionForUpdates: incoming.requireInteractionForUpdates !== false,
      recheckOnResize: incoming.recheckOnResize === true,
      addInsetToNegativeTop: incoming.addInsetToNegativeTop === true,
      interactionWindowMillis: bounded(incoming.interactionWindowMillis, 100, 5000, 1000),
      mutationDebounceMillis: bounded(incoming.mutationDebounceMillis, 50, 1000, 150),
      maxElementsPerBatch: bounded(incoming.maxElementsPerBatch, 4, 64, 16),
      maxBatchDurationMillis: bounded(incoming.maxBatchDurationMillis, 1, 8, 4),
      maxInitialElements: bounded(incoming.maxInitialElements, 64, 2048, 512) };
    const key = JSON.stringify([next, nextInset, redditActive, incoming.navigationGeneration]);
    // A tab switch updates scroll reporting and policy freshness, not safe-area geometry.
    // Keep delivery revisions out of the layout key so owned CSS survives that update.
    next.revision = Number.isSafeInteger(incoming.revision) ? Math.max(0, incoming.revision) : 0;
    if (key === configurationKey) {
      const revisionChanged = configuration.revision !== next.revision;
      configuration = next;
      if (revisionChanged) {
        if (nativeFallbackRequested) {
          globalThis.CandyContentTopInset?.fallbackToNative?.(
            next.navigationGeneration, next.revision, nativeFallbackThemeColor, true);
        } else {
          // An older report (including a clear) may still be in flight when native
          // publishes the next policy. Replay with current freshness, without discovery.
          publishStatusBarBackdrop(reportedStatusBarBackdropColor, next, true);
        }
      }
      if (recheckCover) {
        coverSemanticSeeded = false;
        statusBarBackdropHeaderConfirmed = false;
        statusBarBackdropHeader = null;
        if (next.active) protectBody();
        requestSemanticHeaderCheck();
      }
      if (resize && next.active) { queueKnownHeaderTopChecks(); enqueue(document.body, true); schedule(); }
      return;
    }
    if (configuration?.navigationGeneration !== next.navigationGeneration) {
      coverEnvRecheckedInset = null;
      coverLayoutMutationStates = new WeakMap();
      backdropActivationStates = new WeakMap();
      backdropPriorityCandidates = [];
      routeCoverSettlingUntil = routeChanged && cover ? performance.now() + 2000 : 0;
      routeCoverRechecks = 0;
    }
    if ((!next.safeAreaEnabled ||
        (statusBarBackdropHeaderConfirmed && !statusBarBackdropHeader?.isConnected)) &&
        reportedStatusBarBackdropColor) {
      publishStatusBarBackdrop(null, next);
    }
    cancel();
    viewportOverlayCandidates = [];
    observer?.disconnect();
    observer = null;
    // Remove protection before any fresh computed-style read for the next inset.
    layer?.remove();
    layer = null;
    selectorLayer?.remove();
    selectorLayer = null;
    cleanup.push(...rules);
    rules.clear();
    cleanup.push(...negativeTopElements);
    negativeTopElements.clear();
    cleanup.push(...headerTopSelectorOverrides);
    headerTopSelectorOverrides.clear();
    headerTopCandidates.clear();
    dirtyHeaderTops.clear();
    headerTopMutationStates = new WeakMap();
    lastHeaderTopCheck = -Infinity;
    topCandidates.clear();
    topMutationStates = new WeakMap();
    topInlineValues = new WeakMap();
    checkedSelectorTops = new WeakSet();
    markerName = `${markerPrefix}-${++layerEpoch}`;
    negativeTopMarker = `${markerName}-negative`;
    headerTopMarker = `${markerName}-header`;
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
    updateScrollListeners();
    inset = nextInset;
    if (next.active && !firstInitialization) firstInitialization = { readyState: document.readyState, atMillis: performance.now() };
    bodyPending = next.active && !!document.body;
    semanticCheckPending = false;
    semanticChecks = 0;
    coverSemanticSeeded = false;
    initialDomSeeded = false;
    protectedBody = null;
    redditFlowProtected = false;
    refreshBodyAtReady = false;
    cssTurn = true;
    nativeFallbackRequested = false;
    nativeFallbackThemeColor = null;
    statusBarBackdropHeaderConfirmed = false;
    statusBarBackdropHeader = null;
    statusBarBackdropPending = false;
    for (const { query, changed } of themeMediaWatchers.values()) {
      query.removeEventListener?.("change", changed);
    }
    themeMediaWatchers.clear();
    fixedHeaderCandidates = new Map();
    semanticHeaderCandidates = new Set();
    headerVerificationPending = false;
    scrollGeneration = 0;
    if ((next.active || redditActive) && (!cleanup.length || next.cover) && document.documentElement) {
      if (!cleanup.length) apply(document.documentElement, "--candy-safe-area-inset-top", `${inset}px`);
      if (next.active) protectBody();
    }
    startSelectorScan();
    observe();
    if (next.safeAreaEnabled && document.body) {
      for (const element of knownViewportOverlays) queueViewportOverlayCandidate(element, true);
      for (const child of Array.from(document.body.children || []).slice(-8)) {
        if (hasInlineFullViewportInset(child) || hasViewportOverlayHint(child)) {
          queueViewportOverlayCandidate(child);
        }
        for (let nested = child.firstElementChild, inspected = 0;
            nested && inspected < 2; nested = nested.nextElementSibling, inspected++) {
          if (hasInlineFullViewportInset(nested) || hasViewportOverlayHint(nested)) {
            queueViewportOverlayCandidate(nested);
          }
        }
      }
    }
    watchCoverNativeInset();
    globalThis.CandyRedditSafeArea?.configure(redditActive, (_flow, structuralChange) => {
      if (structuralChange) {
        if (performance.now() < routeCoverSettlingUntil && routeCoverRechecks < 3) {
          for (const candidate of globalThis.CandyRedditSafeArea?.headerCandidates?.() || []) {
            rememberBackdropHeaderCandidate(candidate);
          }
          statusBarBackdropPending = true;
          schedule();
        }
        requestRouteCoverRecheck();
        return;
      }
      for (const candidate of globalThis.CandyRedditSafeArea?.headerCandidates?.() || []) {
        rememberBackdropHeaderCandidate(candidate);
      }
      statusBarBackdropPending = true;
      schedule();
      if (!configuration?.active) return;
      bodyPending = true;
      protectBody();
      schedule();
    }, next.navigationGeneration);
    requestSemanticHeaderCheck();
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
    requestSemanticHeaderCheck();
  }

  function fullscreenChanged() {
    const previous = fullscreenRoot;
    fullscreenRoot = document.fullscreenElement;
    if (fullscreenRoot) {
      for (const element of rules.keys()) {
        if (fullscreenRoot.contains(element)) releaseElementTop(element);
      }
    } else if (previous && configuration?.active) {
      // Recheck only the former fullscreen subtree. Still-fixed content regains
      // protection from its authored top; an inline player receives no offset.
      enqueue(previous, true);
      schedule();
    }
  }

  function scroll() {
    scrollGeneration++;
    const semanticWasPending = semanticCheckPending;
    const backdropWasPending = statusBarBackdropPending;
    const coverLayoutWasPending = coverLayoutPending;
    cancel();
    bodyPending = false;
    semanticCheckPending = semanticWasPending;
    statusBarBackdropPending = backdropWasPending;
    coverLayoutPending = coverLayoutWasPending;
    if (coverLayoutPending) coverLayoutDue = performance.now() + minimumHeaderVerificationQuietMillis;
    headerVerificationPending = configuration?.active === true && !configuration.cover &&
      (fixedHeaderCandidates.size > 0 || semanticHeaderCandidates.size > 0);
    if (headerVerificationPending || statusBarBackdropPending || semanticCheckPending ||
        coverLayoutPending || viewportOverlayCandidates.length || dirtyHeaderTops.size) {
      schedule(Math.max(
        minimumHeaderVerificationQuietMillis,
        configuration?.mutationDebounceMillis || minimumHeaderVerificationQuietMillis,
      ));
    }
    if (cleanup.length) schedule();
  }

  globalThis.__candyConfigureCssSafeArea = configure;
  document.addEventListener("fullscreenchange", fullscreenChanged);
  document.addEventListener("DOMContentLoaded", () => {
    globalThis.CandyRedditSafeArea?.sync();
    if (configuration?.cover) coverLayoutRevision++;
    configure();
    observe();
    startSelectorScan();
    if (configuration?.active && (protectedBody !== document.body || refreshBodyAtReady)) {
      bodyPending = true;
      protectBody();
    }
    seedInitialDom();
    requestSemanticHeaderCheck();
    schedule();
  }, { once: true });
  document.addEventListener("click", interaction, true);
  document.addEventListener("drop", interaction, true);
  document.addEventListener("load", (event) => {
    if (event.target?.localName === "link" &&
        (event.target.getAttribute("rel") || "").toLowerCase().split(/\s+/).includes("stylesheet")) {
      queueKnownHeaderTopChecks();
    }
    if (configuration?.cover && event.target?.localName === "link") {
      coverLayoutRevision++;
      configure();
    }
    if (configuration?.active && event.target?.localName === "link") {
      sourceNode(event.target);
      if (!headerTopCandidates.size) requestSemanticHeaderCheck();
      schedule();
    }
  }, true);
  globalThis.addEventListener("load", () => {
    globalThis.CandyRedditSafeArea?.sync();
    if (configuration?.cover) coverLayoutRevision++;
    configure();
    startSelectorScan();
    seedInitialDom();
    requestSemanticHeaderCheck();
    schedule();
  }, { once: true });
  globalThis.addEventListener("resize", () => {
    if (configuration?.cover) coverLayoutRevision++;
    if (configuration?.recheckOnResize || configuration?.cover) configure(true);
  }, { passive: true });
  configure();
  globalThis.CandyCssSafeAreaDiagnostics = Object.freeze({
    sample: () => globalThis.CandyContentTopInset?.domDiagnosticsEnabled?.() === true ? {
      active: configuration?.active === true, initialized: firstInitialization !== null,
      firstReadyState: firstInitialization?.readyState || "other", firstAtMillis: firstInitialization?.atMillis ?? null,
      ownedCount: rules.size + owned.size, unknownCount: 0, bodyPending,
      pendingJobCount: jobs.length + (semanticCheckPending ? 1 : 0), dirtyRootCount: 0,
      interactionActive: performance.now() < interactionUntil,
      cssSourceCount: Math.min(65535, sources.size), cssLateSourceCount: Math.min(65535, cssCounts.late),
      cssRulesVisited: Math.min(65535, cssCounts.rules), cssRulesApplied: Math.min(65535, selectorRules.size),
      cssSecurityErrors: Math.min(65535, cssCounts.errors), cssUnsupportedRules: Math.min(65535, cssCounts.unsupported),
      cssBudgetHits: Math.min(65535, cssCounts.capped), cssScrollCancellations: Math.min(65535, cssCounts.cancelled),
    } : null,
  });
})();
