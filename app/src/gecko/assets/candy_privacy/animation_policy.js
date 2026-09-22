"use strict";

(function installAnimationPolicyHelpers(global) {
  const styleAttribute = "data-candy-animation-policy";
  const stylesheet = `
    *, *::before, *::after {
      animation-delay: 0s !important;
      animation-duration: 0.001ms !important;
      animation-iteration-count: 1 !important;
      scroll-behavior: auto !important;
      transition-delay: 0s !important;
      transition-duration: 0s !important;
    }
    ::view-transition-group(*),
    ::view-transition-image-pair(*),
    ::view-transition-old(*),
    ::view-transition-new(*) {
      animation-delay: 0s !important;
      animation-duration: 0.001ms !important;
      animation-iteration-count: 1 !important;
    }
  `;

  function settleAnimation(animation) {
    if (!animation || animation.playState === "finished" || animation.playState === "idle") return;
    try {
      if (animation.effect?.getTiming?.().iterations === Infinity) animation.cancel();
      else animation.finish();
    } catch (_) {
      try { animation.cancel(); } catch (_) { }
    }
  }

  function settleDocumentAnimations(targetDocument) {
    try { targetDocument?.getAnimations?.().forEach(settleAnimation); } catch (_) { }
  }

  function installContentPolicy(target, animationsEnabled) {
    const targetDocument = target?.document;
    if (!targetDocument) return;
    const existing = targetDocument.querySelector(`style[${styleAttribute}]`);
    if (animationsEnabled !== false) {
      existing?.remove();
      return;
    }
    if (!existing) {
      const style = targetDocument.createElement("style");
      style.setAttribute(styleAttribute, "disabled");
      style.textContent = stylesheet;
      const parent = targetDocument.head || targetDocument.documentElement;
      if (parent) parent.appendChild(style);
      else targetDocument.addEventListener("DOMContentLoaded", () => {
        (targetDocument.head || targetDocument.documentElement)?.appendChild(style);
      }, { once: true });
    }
    settleDocumentAnimations(targetDocument);
  }

  function registrationCode(revision) {
    const safeRevision = Number.isSafeInteger(revision) && revision >= 0 ? revision : 0;
    return `(() => {
  "use strict";
  const revisionKey = "__candyAnimationPolicyRevision";
  if (Number.isSafeInteger(globalThis[revisionKey]) && globalThis[revisionKey] > ${safeRevision}) {
    return;
  }
  globalThis[revisionKey] = ${safeRevision};
  const settle = (animation) => {
    if (!animation || animation.playState === "finished" || animation.playState === "idle") return;
    try {
      if (animation.effect?.getTiming?.().iterations === Infinity) animation.cancel();
      else animation.finish();
    } catch (_) {
      try { animation.cancel(); } catch (_) {}
    }
  };
  const settleTarget = (event) => {
    try { event.target?.getAnimations?.({ subtree: true }).forEach(settle); } catch (_) {}
  };
  document.addEventListener("animationstart", settleTarget, true);
  document.addEventListener("transitionrun", settleTarget, true);
  try { document.getAnimations().forEach(settle); } catch (_) {}
})();`;
  }

  global.CandyAnimationPolicy = {
    installContentPolicy,
    registrationCode,
    settleDocumentAnimations,
    stylesheet,
  };
})(globalThis);
