"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.privacy";
const PROTOCOL_VERSION = 2;
const COOKIE_RULE_FILE = "candy_default_rules.txt";
const policiesByToken = new Map();
const latestPolicyRevisionByToken = new Map();
const tokenByTab = new Map();
const pendingEvents = new Map();
const contentPolicyTimersByTab = new Map();
const contentPolicyRetryDelaysMillis = [25, 50, 100, 200, 400, 800, 1200];
let nativePort = null;
let cookieRules = null;
let cookieRulesPromise = null;
let flushTimer = null;

async function loadText(fileName) {
  const response = await fetch(browser.runtime.getURL(`rules/${fileName}`));
  if (!response.ok) throw new Error(`Candy rule asset unavailable: ${fileName}`);
  return response.text();
}

async function loadCookieRules() {
  const candyDefaults = await loadText(COOKIE_RULE_FILE);
  return {
    cosmetics: [],
    procedural: [],
    candyDefaults: CandyPrivacyRules.parseCandyDefaults(candyDefaults),
  };
}

function ensureCookieRules() {
  if (!cookieRulesPromise) {
    cookieRulesPromise = loadCookieRules().then((rules) => {
      cookieRules = rules;
      return rules;
    });
  }
  return cookieRulesPromise;
}

function hostMatches(host, expected) {
  return CandyPrivacyRules.hostMatches(host, expected);
}

function hostFromUrl(rawUrl) {
  try {
    const value = new URL(rawUrl);
    return value.protocol === "http:" || value.protocol === "https:" ?
      value.hostname.toLowerCase().replace(/\.$/, "") : null;
  } catch (_) {
    return null;
  }
}

function isPaused(policy, pageHost) {
  return pageHost && policy.pausedHosts.some((host) => hostMatches(pageHost, host));
}

function contentPolicy(policy) {
  return {
    type: "content-policy",
    ready: Boolean(policy),
    revision: Number.isSafeInteger(policy?.revision) ? Math.max(0, policy.revision) : 0,
    topInsetPx: Number.isSafeInteger(policy?.topInsetPx) ? Math.max(0, policy.topInsetPx) : 0,
    navigationGeneration: Number.isSafeInteger(policy?.navigationGeneration) ?
      Math.max(0, policy.navigationGeneration) : 0,
    scrollMetricsEnabled: policy?.scrollMetricsEnabled === true,
  };
}

function publishContentPolicy(token, policy) {
  const tabEntry = Array.from(tokenByTab.entries()).find(([, value]) => value === token);
  if (!tabEntry) return;
  browser.tabs.sendMessage(tabEntry[0], contentPolicy(policy)).catch(() => {});
}

function scheduleContentPolicy(tabId) {
  const previousTimers = contentPolicyTimersByTab.get(tabId) || [];
  previousTimers.forEach(clearTimeout);
  const timers = contentPolicyRetryDelaysMillis.map((delayMillis) => setTimeout(() => {
    const token = tokenByTab.get(tabId);
    const currentPolicy = token && policiesByToken.get(token);
    browser.tabs.sendMessage(tabId, contentPolicy(currentPolicy)).catch(() => {});
  }, delayMillis));
  contentPolicyTimersByTab.set(tabId, timers);
}

function queueEvent(token, revision, event) {
  const key = `${token}\0${revision}`;
  const batch = pendingEvents.get(key) || { token, revision, events: [] };
  if (batch.events.length < 512) batch.events.push(event);
  pendingEvents.set(key, batch);
  if (!flushTimer) flushTimer = setTimeout(flushEvents, 100);
}

function flushEvents() {
  flushTimer = null;
  if (!nativePort) return;
  for (const batch of pendingEvents.values()) {
    nativePort.postMessage({ type: "events", protocolVersion: PROTOCOL_VERSION, ...batch });
  }
  pendingEvents.clear();
}

browser.webRequest.onBeforeRequest.addListener((details) => {
  const token = tokenByTab.get(details.tabId);
  const policy = token && policiesByToken.get(token);
  if (details.type === "main_frame") {
    if (policy) {
      policy.pageHost = hostFromUrl(details.url);
      scheduleContentPolicy(details.tabId);
    }
    return {};
  }
  if (!policy) return { cancel: true };
  const requestHost = hostFromUrl(details.url);
  const pageHost = policy.pageHost;
  if (!requestHost) return {};
  const observesCompatibility = Array.isArray(policy.compatibilityRequestHosts) &&
    policy.compatibilityRequestHosts.some((host) => hostMatches(requestHost, host));
  if (observesCompatibility) {
    queueEvent(token, policy.revision, {
      requestUrl: details.url,
      pageUrl: pageHost ? `https://${pageHost}/` : null,
      compatibilityObservation: true,
    });
  }
  if (isPaused(policy, pageHost)) return {};
  if (policy.hideConsent && !cookieRules) return { cancel: true };
  if (policy.hideConsent && !policy.cookieBannerRemovalDisabled &&
      (requestHost === "cmp.inmobi.com" || requestHost.endsWith(".cmp.inmobi.com"))) {
    queueEvent(token, policy.revision, {
      requestUrl: details.url,
      pageUrl: pageHost ? `https://${pageHost}/` : null,
      action: "B",
      builtIn: true,
    });
    return { cancel: true };
  }
  return {};
}, { urls: ["http://*/*", "https://*/*"] }, ["blocking"]);

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || !sender.tab) {
    return undefined;
  }
  if (message.type === "bind" && policiesByToken.has(message.token)) {
    tokenByTab.set(sender.tab.id, message.token);
    const policy = policiesByToken.get(message.token);
    return Promise.resolve({ type: "bound", revision: policy.revision });
  }
  if (
    message.type === "binding-acknowledged" &&
    message.protocolVersion === PROTOCOL_VERSION &&
    typeof message.token === "string" &&
    Number.isSafeInteger(message.revision)
  ) {
    const policy = policiesByToken.get(message.token);
    if (policy && message.revision >= 1 && message.revision <= policy.revision && nativePort) {
      nativePort.postMessage({
        type: "session-bound",
        protocolVersion: PROTOCOL_VERSION,
        token: message.token,
        revision: message.revision,
      });
    }
    return undefined;
  }
  if (message.type === "content-policy-request") {
    const token = tokenByTab.get(sender.tab.id);
    const policy = token && policiesByToken.get(token);
    return Promise.resolve(contentPolicy(policy));
  }
  if (
    message.type === "safe-area-fallback" &&
    Number.isSafeInteger(message.navigationGeneration)
  ) {
    const token = tokenByTab.get(sender.tab.id);
    const policy = token && policiesByToken.get(token);
    if (
      policy &&
      policy.navigationGeneration === message.navigationGeneration &&
      nativePort
    ) {
      nativePort.postMessage({
        type: "safe-area-fallback",
        protocolVersion: PROTOCOL_VERSION,
        token,
        revision: policy.revision,
        navigationGeneration: message.navigationGeneration,
      });
    }
    return undefined;
  }
  if (message.type === "scroll-metrics") {
    const token = tokenByTab.get(sender.tab.id);
    const policy = token && policiesByToken.get(token);
    if (
      policy?.scrollMetricsEnabled === true &&
      Number.isSafeInteger(message.revision) &&
      message.revision === policy.revision &&
      nativePort
    ) {
      nativePort.postMessage({
        type: "scroll-metrics",
        protocolVersion: PROTOCOL_VERSION,
        token,
        revision: policy.revision,
        offsetPx: message.offsetPx,
        extentPx: message.extentPx,
        rangePx: message.rangePx,
      });
    }
    return undefined;
  }
  if (message.type === "cosmetics") {
    const token = tokenByTab.get(sender.tab.id);
    const policy = token && policiesByToken.get(token);
    const frameHost = hostFromUrl(sender.url || message.url);
    if (!cookieRules || !policy || !frameHost) {
      return Promise.resolve({ type: "cosmetics", selectors: [], procedural: [] });
    }
    return Promise.resolve({
      type: "cosmetics",
      revision: policy.revision,
      ...CandyPrivacyRules.cosmeticPayload(cookieRules, policy, frameHost),
    });
  }
  return undefined;
});

browser.tabs.onRemoved.addListener((tabId) => {
  (contentPolicyTimersByTab.get(tabId) || []).forEach(clearTimeout);
  contentPolicyTimersByTab.delete(tabId);
  const token = tokenByTab.get(tabId);
  tokenByTab.delete(tabId);
  if (token) {
    policiesByToken.delete(token);
    latestPolicyRevisionByToken.delete(token);
  }
});

function postReaderResult(message, payload) {
  if (!nativePort) return;
  nativePort.postMessage({
    type: "reader-result",
    protocolVersion: PROTOCOL_VERSION,
    token: message.token,
    revision: message.revision,
    requestId: message.requestId,
    payload,
  });
}

function extractReader(message) {
  const policy = policiesByToken.get(message.token);
  if (!policy || policy.revision !== message.revision || !Number.isSafeInteger(message.requestId)) {
    postReaderResult(message, null);
    return;
  }
  const tabEntry = Array.from(tokenByTab.entries()).find(([, token]) => token === message.token);
  if (!tabEntry) {
    postReaderResult(message, null);
    return;
  }
  browser.tabs.sendMessage(tabEntry[0], { type: "reader-extract" }, { frameId: 0 }).then(
    (payload) => postReaderResult(message, payload || null),
    () => postReaderResult(message, null),
  );
}

function updatePictureInPicturePlayback(message) {
  const policy = policiesByToken.get(message.token);
  if (!policy || policy.revision !== message.revision || typeof message.expected !== "boolean") {
    return;
  }
  const tabEntry = Array.from(tokenByTab.entries()).find(([, token]) => token === message.token);
  if (!tabEntry) return;
  browser.tabs.sendMessage(tabEntry[0], {
    type: "picture-in-picture-playback",
    expected: message.expected,
  }).catch(() => {});
}

function connectNative() {
  nativePort = browser.runtime.connectNative(NATIVE_APP);
  nativePort.onMessage.addListener((message) => {
    if (!message || message.protocolVersion !== PROTOCOL_VERSION) return;
    if (
      message.type === "policy" &&
      typeof message.token === "string" &&
      Number.isSafeInteger(message.revision)
    ) {
      const latestRevision = latestPolicyRevisionByToken.get(message.token) || 0;
      const acknowledgePolicy = () => {
        nativePort?.postMessage({
          type: "policy-ready",
          protocolVersion: PROTOCOL_VERSION,
          token: message.token,
          revision: message.revision,
        });
      };
      if (message.revision < latestRevision) {
        acknowledgePolicy();
        return;
      }
      latestPolicyRevisionByToken.set(message.token, message.revision);
      const publishPolicy = () => {
        if (latestPolicyRevisionByToken.get(message.token) !== message.revision) {
          acknowledgePolicy();
          return;
        }
        policiesByToken.set(message.token, message);
        publishContentPolicy(message.token, message);
        acknowledgePolicy();
      };
      if (message.hideConsent) {
        ensureCookieRules().then(publishPolicy).catch((error) => {
          if (latestPolicyRevisionByToken.get(message.token) !== message.revision) {
            acknowledgePolicy();
            return;
          }
          nativePort?.postMessage({
            type: "failed",
            protocolVersion: PROTOCOL_VERSION,
            reason: String(error).slice(0, 512),
          });
        });
      } else {
        publishPolicy();
      }
    } else if (message.type === "remove" && typeof message.token === "string") {
      policiesByToken.delete(message.token);
      latestPolicyRevisionByToken.delete(message.token);
      for (const [tabId, token] of tokenByTab) if (token === message.token) tokenByTab.delete(tabId);
    } else if (message.type === "reader-extract" && typeof message.token === "string") {
      extractReader(message);
    } else if (
      message.type === "picture-in-picture-playback" &&
      typeof message.token === "string"
    ) {
      updatePictureInPicturePlayback(message);
    }
  });
  nativePort.onDisconnect.addListener(() => { nativePort = null; });
  nativePort.postMessage({ type: "ready", protocolVersion: PROTOCOL_VERSION });
}

connectNative();
