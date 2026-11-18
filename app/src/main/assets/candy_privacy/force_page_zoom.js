(() => {
  let enabled = false;
  let observer = null;
  let startListener = null;

  function stop() {
    observer?.disconnect();
    observer = null;
    if (startListener) document.removeEventListener("readystatechange", startListener);
    startListener = null;
  }

  function apply() {
    document.querySelectorAll("meta[name]").forEach((viewport) => {
      if (viewport.getAttribute("name")?.toLowerCase() !== "viewport") return;
      const directives = (viewport.getAttribute("content") || "")
        .replace(/\s*=\s*/g, "=")
        .split(/[,;\s]+/)
        .filter(Boolean)
        .filter((directive) => !["user-scalable", "minimum-scale", "maximum-scale"]
          .includes(directive.split("=", 1)[0].toLowerCase()));
      directives.push("user-scalable=yes", "maximum-scale=10");
      const content = directives.join(", ");
      if (viewport.getAttribute("content") !== content) viewport.setAttribute("content", content);
    });
  }

  function start() {
    startListener = null;
    if (!document.documentElement) return;
    observer = new MutationObserver(apply);
    observer.observe(document.documentElement, {
      attributes: true, attributeFilter: ["name", "content"], childList: true, subtree: true,
    });
    apply();
  }

  globalThis.CandyForcePageZoom = Object.freeze({
    setEnabled(value) {
      if (enabled === value) return;
      enabled = value;
      stop();
      if (!enabled) return;
      if (document.documentElement) start();
      else {
        startListener = start;
        document.addEventListener("readystatechange", startListener, { once: true });
      }
    },
  });
})();
