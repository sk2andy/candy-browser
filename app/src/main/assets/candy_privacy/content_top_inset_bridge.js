"use strict";

const policyRetryDelaysMillis = [25, 50, 100, 200, 400, 800, 1200];
const state = {
  topInsetPx: 0,
  navigationGeneration: 0,
  revision: 0,
  scrollMetricsEnabled: false,
};
let policyReady = false;
let policyRetryIndex = 0;
let policyRetryTimer = 0;
let scrollMetricsFrame = 0;

function publishScrollMetrics() {
  scrollMetricsFrame = 0;
  if (!policyReady || !state.scrollMetricsEnabled) return;
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
  state.revision = revision;
  state.topInsetPx = Number.isSafeInteger(policy.topInsetPx) ?
    Math.max(0, policy.topInsetPx) : 0;
  state.navigationGeneration = Number.isSafeInteger(policy.navigationGeneration) ?
    Math.max(0, policy.navigationGeneration) : 0;
  state.scrollMetricsEnabled = policy.scrollMetricsEnabled === true;
  globalThis.__candyReconcileContentTopInset?.();
  scheduleScrollMetrics();
}

globalThis.CandyContentTopInset = Object.freeze({
  topInsetPx: () => state.topInsetPx,
  navigationGeneration: () => state.navigationGeneration,
  policyRevision: () => state.revision,
  fallbackToNative: (navigationGeneration) => {
    if (navigationGeneration !== state.navigationGeneration) return;
    browser.runtime.sendMessage({
      type: "safe-area-fallback",
      navigationGeneration,
    }).catch(() => {});
  },
});

browser.runtime.onMessage.addListener((message) => {
  if (message?.type === "content-policy") applyPolicy(message);
});
globalThis.addEventListener("scroll", scheduleScrollMetrics, { passive: true });
globalThis.addEventListener("resize", scheduleScrollMetrics, { passive: true });
document.addEventListener("DOMContentLoaded", scheduleScrollMetrics, { once: true });
globalThis.addEventListener("load", scheduleScrollMetrics, { once: true });
requestPolicy();
