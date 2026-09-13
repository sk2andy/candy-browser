"use strict";

const policyRetryDelaysMillis = [25, 50, 100, 200, 400, 800, 1200];
const state = {
  topInsetPx: 0,
  navigationGeneration: 0,
  revision: 0,
  scrollMetricsEnabled: false,
  performanceDiagnosticsEnabled: false,
  safeAreaLayoutQuietPeriodMillis: 400,
  safeAreaRequiredFailureCount: 3,
};
let policyReady = false;
let policyRetryIndex = 0;
let policyRetryTimer = 0;
let scrollMetricsFrame = 0;

function publishScrollMetrics() {
  scrollMetricsFrame = 0;
  if (!policyReady || !state.scrollMetricsEnabled) return;
  const phase = beginCandyPerformancePhase("Candy.ScrollMetrics.Publish");
  try {
    const root = document.documentElement;
    if (!root) return;
    const body = document.body;
    const scale = Number.isFinite(globalThis.devicePixelRatio) ?
      Math.max(1, globalThis.devicePixelRatio) : 1;
    const extentCssPx = Math.max(0, root.clientHeight || globalThis.innerHeight || 0);
    const rangeCssPx = Math.max(
      extentCssPx,
      root.scrollHeight || 0,
      body?.scrollHeight || 0,
    );
    browser.runtime.sendMessage({
      type: "scroll-metrics",
      revision: state.revision,
      offsetPx: Math.round(Math.max(0, globalThis.scrollY || 0) * scale),
      extentPx: Math.round(extentCssPx * scale),
      rangePx: Math.round(rangeCssPx * scale),
    }).catch(() => {});
  } finally {
    endCandyPerformancePhase("Candy.ScrollMetrics.Publish", phase);
  }
}

function beginCandyPerformancePhase(name) {
  if (!state.performanceDiagnosticsEnabled) return false;
  try {
    if (typeof globalThis.performance?.mark !== "function" ||
        typeof globalThis.performance?.measure !== "function") return false;
    globalThis.performance.mark(`${name}.start`);
    return true;
  } catch (_error) {
    return false;
  }
}

function endCandyPerformancePhase(name, started) {
  if (!started) return;
  try {
    globalThis.performance.mark(`${name}.end`);
    globalThis.performance.measure(name, `${name}.start`, `${name}.end`);
  } catch (_error) {
    // Profiling must not change scroll metrics or page protection.
  } finally {
    for (const mark of [`${name}.start`, `${name}.end`]) {
      try { globalThis.performance.clearMarks(mark); } catch (_error) {}
    }
    try { globalThis.performance.clearMeasures(name); } catch (_error) {}
  }
}

function scheduleScrollMetrics() {
  if (scrollMetricsFrame) return;
  scrollMetricsFrame = globalThis.requestAnimationFrame(publishScrollMetrics);
}

function requestPolicy() {
  policyRetryTimer = 0;
  if (policyReady) return;
  browser.runtime.sendMessage({ type: "content-policy-request" }).then(applyPolicy).catch(() => {
    schedulePolicyRetry();
  });
}

function schedulePolicyRetry() {
  if (policyReady || policyRetryTimer || policyRetryIndex >= policyRetryDelaysMillis.length) return;
  const delayMillis = policyRetryDelaysMillis[policyRetryIndex++];
  policyRetryTimer = setTimeout(requestPolicy, delayMillis);
}

function applyPolicy(policy) {
  if (!policy || policy.type !== "content-policy") return;
  if (policy.ready !== true) {
    schedulePolicyRetry();
    return;
  }
  const revision = Number.isSafeInteger(policy.revision) ? Math.max(0, policy.revision) : 0;
  if (revision < state.revision) return;
  policyReady = true;
  if (policyRetryTimer) clearTimeout(policyRetryTimer);
  policyRetryTimer = 0;
  const safeAreaLayoutQuietPeriodMillis =
    Number.isSafeInteger(policy.safeAreaLayoutQuietPeriodMillis) ?
      Math.min(800, Math.max(100, policy.safeAreaLayoutQuietPeriodMillis)) : 400;
  const safeAreaRequiredFailureCount =
    Number.isSafeInteger(policy.safeAreaRequiredFailureCount) ?
      Math.min(5, Math.max(2, policy.safeAreaRequiredFailureCount)) : 3;
  const safeAreaSettingsChanged =
    state.safeAreaLayoutQuietPeriodMillis !== safeAreaLayoutQuietPeriodMillis ||
    state.safeAreaRequiredFailureCount !== safeAreaRequiredFailureCount;
  state.revision = revision;
  state.topInsetPx = Number.isSafeInteger(policy.topInsetPx) ?
    Math.max(0, policy.topInsetPx) : 0;
  state.navigationGeneration = Number.isSafeInteger(policy.navigationGeneration) ?
    Math.max(0, policy.navigationGeneration) : 0;
  state.scrollMetricsEnabled = policy.scrollMetricsEnabled === true;
  state.performanceDiagnosticsEnabled = policy.performanceDiagnosticsEnabled === true;
  state.safeAreaLayoutQuietPeriodMillis = safeAreaLayoutQuietPeriodMillis;
  state.safeAreaRequiredFailureCount = safeAreaRequiredFailureCount;
  if (safeAreaSettingsChanged) globalThis.__candyReconfigureContentTopInset?.();
  else globalThis.__candyReconcileContentTopInset?.();
  scheduleScrollMetrics();
}

globalThis.CandyContentTopInset = Object.freeze({
  topInsetPx: () => state.topInsetPx,
  viewportCoverAllowed: () => true,
  navigationGeneration: () => state.navigationGeneration,
  policyRevision: () => state.revision,
  safeAreaLayoutQuietPeriodMillis: () => state.safeAreaLayoutQuietPeriodMillis,
  safeAreaRequiredFailureCount: () => state.safeAreaRequiredFailureCount,
  performanceDiagnosticsEnabled: () => state.performanceDiagnosticsEnabled,
  fallbackToNative: (navigationGeneration, revision) => {
    if (
      navigationGeneration !== state.navigationGeneration ||
      revision !== state.revision
    ) return;
    browser.runtime.sendMessage({
      type: "safe-area-fallback",
      navigationGeneration,
      revision,
    }).catch(() => {});
  },
});

browser.runtime.onMessage.addListener((message) => {
  if (message?.type === "content-policy") applyPolicy(message);
  if (message?.type === "performance-diagnostics-state" &&
      policyReady && message.revision === state.revision) {
    state.performanceDiagnosticsEnabled = message.performanceDiagnosticsEnabled === true;
  }
  if (message?.type === "performance-diagnostics-gap" &&
      policyReady && message.revision === state.revision &&
      state.performanceDiagnosticsEnabled) {
    try {
      if (typeof globalThis.performance?.mark !== "function") return;
      globalThis.performance.mark("Candy.Diagnostics.UserObservedGap");
    } catch (_error) {
      // An optional profiling marker must not change page behavior.
    } finally {
      try { globalThis.performance.clearMarks("Candy.Diagnostics.UserObservedGap"); } catch (_error) {}
    }
  }
});
globalThis.addEventListener("scroll", scheduleScrollMetrics, { passive: true });
globalThis.addEventListener("resize", scheduleScrollMetrics, { passive: true });
document.addEventListener("DOMContentLoaded", scheduleScrollMetrics, { once: true });
globalThis.addEventListener("load", scheduleScrollMetrics, { once: true });
requestPolicy();
