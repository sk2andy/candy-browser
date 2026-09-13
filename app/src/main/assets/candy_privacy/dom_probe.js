"use strict";

// Inert until an explicit diagnostic-build shell request. No observers or page-layout repair.
globalThis.CandyDomProbe = Object.freeze({
  sample() {
    const finite = (value) => Number.isFinite(value) && Math.abs(value) <= 1e7 ? value : null;
    const pixels = (value) => /^-?(?:\d+\.?\d*|\.\d+)px$/.test(value) ? finite(parseFloat(value)) : null;
    const candidates = new Set();
    const add = (element) => {
      for (let depth = 0; element && depth < 8 && candidates.size < 16; depth++, element = element.parentElement) {
        if (element !== document.documentElement && element !== document.body) candidates.add(element);
      }
    };
    const read = (element) => {
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      const tag = ["HTML", "BODY", "HEADER", "NAV", "MAIN", "DIV", "BUTTON", "A", "FORM", "INPUT", "SPAN"]
        .includes(element.tagName) ? element.tagName : "OTHER";
      return {
        tag, position: ["static", "relative", "absolute", "fixed", "sticky"].includes(style.position) ? style.position : "other",
        x: finite(rect.x), y: finite(rect.y), width: finite(rect.width), height: finite(rect.height),
        top: pixels(style.top), paddingTop: pixels(style.paddingTop), marginTop: pixels(style.marginTop),
        maxBlockSize: pixels(style.maxBlockSize), hasTransform: style.transform !== "none",
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
    return {
      version: 1, env, viewportFit,
      viewport: { width: finite(innerWidth), height: finite(innerHeight), density: finite(devicePixelRatio),
        scrollY: finite(scrollY), scale: finite(visualViewport?.scale), offsetTop: finite(visualViewport?.offsetTop) },
      darkPreferred: matchMedia("(prefers-color-scheme: dark)").matches,
      html: read(document.documentElement), body: document.body ? read(document.body) : null,
      candidates: Array.from(candidates, read), googleDialog: googleDialog ? read(googleDialog) : null,
    };
  },
});
