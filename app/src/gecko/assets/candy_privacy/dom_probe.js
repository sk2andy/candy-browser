"use strict";

// Inert until an explicit diagnostic-build shell request. No observers or page-layout repair.
globalThis.CandyDomProbe = Object.freeze({
  sample() {
    const finite = (value) => Number.isFinite(value) && Math.abs(value) <= 1e7 ? value : null;
    const pixels = (value) => /^-?(?:\d+\.?\d*|\.\d+)px$/.test(value) ? finite(parseFloat(value)) : null;
    const styles = new Map();
    const computed = (element) => {
      if (!styles.has(element)) styles.set(element, getComputedStyle(element));
      return styles.get(element);
    };
    const present = (value) => typeof value === "string" && value !== "" && value !== "none";
    const overflow = (value) => ["visible", "hidden", "clip", "scroll", "auto", "overlay"].includes(value) ? value : "other";
    const candidates = new Set();
    const add = (element) => {
      for (let depth = 0; element && depth < 8 && candidates.size < 16; depth++, element = element.parentElement) {
        if (element !== document.documentElement && element !== document.body) candidates.add(element);
      }
    };
    const read = (element) => {
      const style = computed(element);
      const rect = element.getBoundingClientRect();
      const tag = ["HTML", "BODY", "HEADER", "NAV", "MAIN", "DIV", "BUTTON", "A", "FORM", "INPUT", "SPAN", "IFRAME", "TEXTAREA", "SELECT"]
        .includes(element.tagName) ? element.tagName : "OTHER";
      const hasAnimationEffects = present(style.animationName);
      const hasTransitionEffects = (style.transitionDuration || "").split(",").some((value) => Number.parseFloat(value) > 0);
      const hasIndividualTransformEffects = ["translate", "scale", "rotate"].some((key) => present(style[key]));
      const hasOffsetPathEffects = present(style.offsetPath);
      const hasZoomEffects = typeof style.zoom === "string" && !["", "1", "normal", "100%"].includes(style.zoom);
      // Fixed categories only; no free author property names leave the diagnostic boundary.
      const transitionProperty = style.transitionProperty || "all";
      const transitionProperties = transitionProperty.length <= 2048 ? transitionProperty.split(",") : [];
      const transitionPropertyKind = transitionProperties.length === 1 && transitionProperties[0].trim() === "none"
        ? "none" : transitionProperties.length && transitionProperties.length <= 32 && transitionProperties.every((property) =>
          ["color", "background-color", "border-color", "border-top-color", "border-right-color", "border-bottom-color",
            "border-left-color", "outline-color", "text-decoration-color", "column-rule-color", "fill", "stroke",
            "stop-color", "flood-color", "lighting-color", "caret-color", "accent-color", "opacity"].includes(property.trim()))
          ? "paint-only" : "geometry-or-unknown";
      return {
        tag, position: ["static", "relative", "absolute", "fixed", "sticky"].includes(style.position) ? style.position : "other",
        x: finite(rect.x), y: finite(rect.y), width: finite(rect.width), height: finite(rect.height),
        top: pixels(style.top), paddingTop: pixels(style.paddingTop), marginTop: pixels(style.marginTop),
        maxBlockSize: pixels(style.maxBlockSize), hasTransform: present(style.transform),
        overflowX: overflow(style.overflowX), overflowY: overflow(style.overflowY),
        hasMovingEffects: present(style.transform) || hasAnimationEffects || hasTransitionEffects ||
          hasIndividualTransformEffects || hasOffsetPathEffects || hasZoomEffects,
        hasAnimationEffects, hasTransitionEffects, hasIndividualTransformEffects, hasOffsetPathEffects, hasZoomEffects,
        transitionPropertyKind,
        pointerEvents: ["auto", "none"].includes(style.pointerEvents) ? style.pointerEvents : "other",
        inert: element.inert === true,
        hasContainingBlockEffects: ["transform", "translate", "scale", "rotate", "perspective", "filter", "backdropFilter"].some((key) => present(style[key])) ||
          /\b(?:layout|paint|strict|content)\b/.test(style.contain || "") ||
          ["auto", "hidden"].includes(style.contentVisibility) ||
          /\b(?:transform|translate|scale|rotate|perspective|filter|backdrop-filter|contain)\b/.test(style.willChange || ""),
        displayed: style.display !== "none", visible: style.visibility === "visible",
      };
    };
    let env;
    const probe = document.createElement("div");
    try {
      // Inline !important defeats author rules; fixed/contained/hidden avoids normal-flow changes.
      const properties = {
        all: "initial", position: "fixed", left: "-10000px", top: "-10000px",
        width: "0", height: "0", contain: "strict", visibility: "hidden", "pointer-events": "none",
        "padding-top": "env(safe-area-inset-top, 0px)", "padding-right": "env(safe-area-inset-right, 0px)",
        "padding-bottom": "env(safe-area-inset-bottom, 0px)", "padding-left": "env(safe-area-inset-left, 0px)",
      };
      for (const [name, value] of Object.entries(properties)) probe.style.setProperty(name, value, "important");
      document.documentElement.appendChild(probe);
      const style = getComputedStyle(probe);
      env = { top: pixels(style.paddingTop), right: pixels(style.paddingRight),
        bottom: pixels(style.paddingBottom), left: pixels(style.paddingLeft) };
    } finally {
      probe.remove();
    }
    // Fixed anonymous top-right hit, useful for close controls just below the native inset.
    // Prioritize its chain within the same 16-candidate cap; no caller-provided coordinates.
    const topRightHit = document.elementFromPoint(Math.max(1, innerWidth - 24), Math.max(2, (env.top || 0) + 24));
    add(topRightHit);
    for (const selector of ["header", "nav", '[role="banner"]']) add(document.querySelector(selector));
    for (const x of [innerWidth * 0.1, innerWidth * 0.5, innerWidth * 0.9]) {
      for (const y of [1, Math.max(1, (env.top || 0) / 2), Math.max(2, (env.top || 0) + 2)]) {
        add(document.elementFromPoint(x, y));
      }
    }
    const viewport = document.querySelector('meta[name="viewport"]')?.content?.slice(0, 512) || "";
    const viewportFit = /viewport-fit\s*=\s*(cover|contain|auto)\b/i.exec(viewport)?.[1]?.toLowerCase() || "unset";
    // Fixed known selector only: the Google centered dialog quoted during investigation.
    const googleDialog = document.querySelector(".FAd6bc");
    // Anonymous first-flow chain only; bounded sibling inspection and cached styles, no selectors from callers.
    const flowStart = [];
    let current = document.body;
    let inspected = 0;
    while (current && flowStart.length < 8 && inspected < 32) {
      let child = current.firstElementChild;
      while (child && inspected++ < 32) {
        const style = computed(child);
        if (style.display !== "none" && !["fixed", "absolute"].includes(style.position)) break;
        child = child.nextElementSibling;
      }
      if (!child || inspected > 32) break;
      flowStart.push(read(child));
      current = child;
    }
    let cssSafeArea = { available: false, ready: false, enabled: false, insetPx: null };
    try {
      const configuration = globalThis.CandyContentTopInset?.cssSafeAreaConfiguration?.();
      if (configuration && typeof configuration === "object" && !Array.isArray(configuration)) {
        const inset = configuration.cssSafeAreaTopInsetPx;
        cssSafeArea = { available: true, ready: configuration.ready === true, enabled: configuration.enabled === true,
          insetPx: Number.isFinite(inset) && inset >= 0 && inset <= 10000 ? inset : null };
      }
    } catch (_error) {}
    // Read-only classifier metadata; never a readiness or acceptance gate.
    const cssSafeAreaDiagnostics = globalThis.CandyCssSafeAreaDiagnostics?.sample?.() || null;
    const focused = document.activeElement;
    const activeTag = focused?.tagName;
    const activeElementTag = !focused ? "NONE" :
      ["HTML", "BODY", "HEADER", "NAV", "MAIN", "DIV", "BUTTON", "A", "FORM", "INPUT", "SPAN", "IFRAME", "TEXTAREA", "SELECT"]
        .includes(activeTag) ? activeTag : "OTHER";
    return {
      version: 1, env, viewportFit, cssSafeArea, flowStart, cssSafeAreaDiagnostics, activeElementTag,
      readyState: ["loading", "interactive", "complete"].includes(document.readyState) ? document.readyState : "other",
      viewport: { width: finite(innerWidth), height: finite(innerHeight), density: finite(devicePixelRatio),
        scrollY: finite(scrollY), scale: finite(visualViewport?.scale), offsetTop: finite(visualViewport?.offsetTop) },
      darkPreferred: matchMedia("(prefers-color-scheme: dark)").matches,
      html: read(document.documentElement), body: document.body ? read(document.body) : null,
      topRightCandidateIndex: candidates.has(topRightHit) ? Array.from(candidates).indexOf(topRightHit) : null,
      candidates: Array.from(candidates, read), googleDialog: googleDialog ? read(googleDialog) : null,
    };
  },
});
