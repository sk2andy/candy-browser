"use strict";

(function installCandyCosmetics() {
  for (let frame = self; frame !== top;) {
    frame = frame.parent;
    try { void frame.document; } catch (_) { return; }
  }

  browser.runtime.sendMessage({ type: "cosmetics", url: location.href }).then((payload) => {
    if (!payload || payload.type !== "cosmetics") return;
    const selectors = Array.isArray(payload.selectors) ? payload.selectors.filter((selector) =>
      typeof selector === "string" && selector.length > 0 && selector.length <= 2048,
    ).slice(0, 4096) : [];
    if (selectors.length) {
      const style = document.createElement("style");
      style.dataset.candyPrivacy = "cosmetic";
      style.textContent = selectors.map((selector) =>
        `${selector}{display:none!important}`,
      ).join("\n");
      (document.documentElement || document).appendChild(style);

      if (selectors.includes("#data-protection-consent-dialog")) {
        const removeRedditConsentDialog = () => {
          const dialog = document.getElementById("data-protection-consent-dialog");
          if (!dialog) return false;
          dialog.remove();
          document.body?.classList.remove("rpl-scroll-lock");
          return true;
        };
        if (!removeRedditConsentDialog()) {
          const observer = new MutationObserver(() => {
            if (removeRedditConsentDialog()) observer.disconnect();
          });
          observer.observe(document.documentElement, { childList: true, subtree: true });
          setTimeout(() => observer.disconnect(), 10000);
        }
      }
    }

    const rules = Array.isArray(payload.procedural) ? payload.procedural.filter((rule) =>
      rule && (rule.action === "H" || rule.action === "R") &&
        typeof rule.selector === "string" && rule.selector.length > 0 && rule.selector.length <= 2048 &&
        typeof rule.text === "string" && rule.text.length <= 128 &&
        typeof rule.ignoreCase === "boolean",
    ).slice(0, 64) : [];
    if (!rules.length) return;

    let runs = 0;
    let scheduled = false;
    const apply = () => {
      scheduled = false;
      if (++runs > 20) {
        observer.disconnect();
        return;
      }
      const deadline = performance.now() + 8;
      rules.some((rule) => {
        if (performance.now() > deadline) return true;
        let nodes;
        try { nodes = document.querySelectorAll(rule.selector); } catch (_) { return false; }
        return Array.prototype.slice.call(nodes, 0, 128).some((node) => {
          if (performance.now() > deadline) return true;
          let content = node.textContent || "";
          let expected = rule.text;
          if (expected) {
            if (rule.ignoreCase) {
              content = content.toLowerCase();
              expected = expected.toLowerCase();
            }
            if (!content.includes(expected)) return false;
          }
          if (rule.action === "R") {
            node.remove();
          } else {
            node.dataset.candyProceduralHidden = "1";
            node.style.setProperty("display", "none", "important");
          }
          return false;
        });
      });
    };
    const schedule = () => {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(apply);
    };
    const observer = new MutationObserver(schedule);
    const start = () => {
      apply();
      observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        characterData: true,
      });
      setTimeout(() => observer.disconnect(), 5000);
    };
    if (document.documentElement) start();
    else addEventListener("DOMContentLoaded", start, { once: true });
  }).catch(() => { });

})();
