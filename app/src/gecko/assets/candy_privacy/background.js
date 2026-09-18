"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.privacy";
const PROTOCOL_VERSION = 2;
const COOKIE_RULE_FILE = "candy_default_rules.txt";
const policiesByToken = new Map();
const latestPolicyRevisionByToken = new Map();
const tokenByTab = new Map();
const pendingEvents = new Map();
const mainFrameRequestsById = new Map();
const contentPolicyTimersByTab = new Map();
const inlineVideosByTab = new Map();
const contentPolicyRetryDelaysMillis = [25, 50, 100, 200, 400, 800, 1200];
let nativePort = null;
let cookieRules = null;
let cookieRulesPromise = null;
let flushTimer = null;
let latestWebRtcPolicyRevision = 0;
let webRtcPolicyQueue = Promise.resolve();

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
    cssSafeAreaTopInsetPx: Number.isSafeInteger(policy?.cssSafeAreaTopInsetPx) ?
      Math.max(0, policy.cssSafeAreaTopInsetPx) : 0,
    geckoSafeAreaEnabled: policy?.geckoSafeAreaEnabled === true,
    recheckAddedElements: policy?.recheckAddedElements === true,
    recheckChangedElements: policy?.recheckChangedElements === true,
    requireInteractionForUpdates: policy?.requireInteractionForUpdates !== false,
    recheckOnResize: policy?.recheckOnResize === true,
    interactionWindowMillis: boundedSafeAreaInteger(policy?.interactionWindowMillis, 100, 5000, 1000),
    mutationDebounceMillis: boundedSafeAreaInteger(policy?.mutationDebounceMillis, 50, 1000, 150),
    maxElementsPerBatch: boundedSafeAreaInteger(policy?.maxElementsPerBatch, 4, 64, 16),
    maxBatchDurationMillis: boundedSafeAreaInteger(policy?.maxBatchDurationMillis, 1, 8, 4),
    maxInitialElements: boundedSafeAreaInteger(policy?.maxInitialElements, 64, 2048, 512),
    navigationGeneration: Number.isSafeInteger(policy?.navigationGeneration) ?
      Math.max(0, policy.navigationGeneration) : 0,
    scrollMetricsEnabled: policy?.scrollMetricsEnabled === true,
    inlineMediaPlayerEnabled: policy?.inlineMediaPlayerEnabled === true,
    performanceDiagnosticsEnabled: policy?.performanceDiagnosticsEnabled === true,
    domDiagnosticsEnabled: policy?.domDiagnosticsEnabled === true,
    safeAreaLayoutQuietPeriodMillis:
      Number.isSafeInteger(policy?.safeAreaLayoutQuietPeriodMillis) ?
        Math.min(800, Math.max(100, policy.safeAreaLayoutQuietPeriodMillis)) : 400,
    safeAreaRequiredFailureCount:
      Number.isSafeInteger(policy?.safeAreaRequiredFailureCount) ?
        Math.min(5, Math.max(2, policy.safeAreaRequiredFailureCount)) : 3,
  };
}

function boundedSafeAreaInteger(value, minimum, maximum, fallback) {
  return Number.isSafeInteger(value) ? Math.min(maximum, Math.max(minimum, value)) : fallback;
}

function publishContentPolicy(token, policy) {
  const tabEntry = Array.from(tokenByTab.entries()).find(([, value]) => value === token);
  if (!tabEntry) return;
  browser.tabs.sendMessage(tabEntry[0], contentPolicy(policy)).catch(() => {});
}

function publishPerformanceDiagnosticsState(message) {
  const policy = policiesByToken.get(message.token);
  if (!policy || message.revision !== policy.revision) return;
  const enabled = message.performanceDiagnosticsEnabled === true;
  if (policy.performanceDiagnosticsEnabled === enabled) return;
  policiesByToken.set(message.token, { ...policy, performanceDiagnosticsEnabled: enabled });
  const tabEntry = Array.from(tokenByTab.entries()).find(([, token]) => token === message.token);
  if (!tabEntry) return;
  browser.tabs.sendMessage(tabEntry[0], {
    type: "performance-diagnostics-state",
    revision: policy.revision,
    performanceDiagnosticsEnabled: enabled,
  }).catch(() => {});
}

function publishPerformanceDiagnosticsGap(message) {
  const policy = policiesByToken.get(message.token);
  if (!policy || message.revision !== policy.revision ||
      policy.performanceDiagnosticsEnabled !== true) return;
  const tabEntry = Array.from(tokenByTab.entries()).find(([, token]) => token === message.token);
  if (!tabEntry) return;
  browser.tabs.sendMessage(tabEntry[0], {
    type: "performance-diagnostics-gap",
    revision: policy.revision,
  }).catch(() => {});
}

function publishInlineVideoState(tabId) {
  const token = tokenByTab.get(tabId);
  const policy = token && policiesByToken.get(token);
  if (!nativePort || !policy) return;
  const candidates = Array.from(inlineVideosByTab.get(tabId)?.values() || [])
    .filter((candidate) => candidate.active)
    .sort((first, second) => {
      const playbackDifference = Number(second.playing) - Number(first.playing);
      return playbackDifference || second.area - first.area;
    });
  const selected = candidates[0];
  nativePort.postMessage({
    type: "inline-video-state",
    protocolVersion: PROTOCOL_VERSION,
    token,
    revision: policy.revision,
    navigationGeneration: policy.navigationGeneration,
    active: Boolean(selected),
    playing: selected?.playing === true,
    videoWidth: selected?.videoWidth || 0,
    videoHeight: selected?.videoHeight || 0,
    documentNonce: selected?.documentNonce || "",
    elementNonce: selected?.elementNonce || "",
  });
}

function updateInlineVideoState(message, sender) {
  const tabId = sender.tab?.id;
  const frameId = sender.frameId;
  if (!Number.isInteger(tabId) || frameId !== 0) return;
  const token = tokenByTab.get(tabId);
  const policy = token && policiesByToken.get(token);
  if (
    !policy ||
    message.revision !== policy.revision ||
    message.navigationGeneration !== policy.navigationGeneration
  ) return;
  if (policy?.inlineMediaPlayerEnabled !== true) {
    inlineVideosByTab.delete(tabId);
    publishInlineVideoState(tabId);
    return;
  }
  const noncePattern = /^[a-f0-9]{32}$/;
  if (
    message.active === true &&
    (
      ![message.videoWidth, message.videoHeight]
        .every((value) => Number.isSafeInteger(value) && value > 0 && value <= 16384) ||
      !Number.isSafeInteger(message.area) ||
      message.area < 4096 ||
      !noncePattern.test(message.documentNonce) ||
      !noncePattern.test(message.elementNonce)
    )
  ) return;
  const frames = inlineVideosByTab.get(tabId) || new Map();
  if (message.active === true) {
    frames.set(frameId, {
      active: true,
      playing: message.playing === true,
      videoWidth: message.videoWidth,
      videoHeight: message.videoHeight,
      area: message.area,
      documentNonce: message.documentNonce,
      elementNonce: message.elementNonce,
    });
    inlineVideosByTab.set(tabId, frames);
  } else {
    frames.delete(frameId);
    if (frames.size) inlineVideosByTab.set(tabId, frames);
    else inlineVideosByTab.delete(tabId);
  }
  publishInlineVideoState(tabId);
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
    inlineVideosByTab.delete(details.tabId);
    publishInlineVideoState(details.tabId);
    if (policy) {
      policy.pageHost = hostFromUrl(details.url);
      if (typeof details.requestId === "string") {
        mainFrameRequestsById.set(details.requestId, {
          token,
          revision: policy.revision,
          navigationGeneration: policy.navigationGeneration,
        });
      }
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

browser.webRequest.onHeadersReceived.addListener((details) => {
  if (details.type !== "main_frame" || !nativePort || !Number.isInteger(details.statusCode)) {
    return;
  }
  const request = mainFrameRequestsById.get(details.requestId);
  mainFrameRequestsById.delete(details.requestId);
  if (!request) return;
  const cloudflareChallenge = (details.responseHeaders || []).some((header) =>
    typeof header.name === "string" &&
    header.name.toLowerCase() === "cf-mitigated" &&
    typeof header.value === "string" &&
    header.value.trim().toLowerCase() === "challenge"
  );
  nativePort.postMessage({
    type: "main-frame-response",
    protocolVersion: PROTOCOL_VERSION,
    token: request.token,
    revision: request.revision,
    navigationGeneration: request.navigationGeneration,
    url: details.url,
    statusCode: details.statusCode,
    ...(cloudflareChallenge ? { cloudflareChallenge: true } : {}),
  });
}, { urls: ["http://*/*", "https://*/*"] }, ["responseHeaders"]);

browser.webRequest.onErrorOccurred.addListener((details) => {
  if (details.type === "main_frame") mainFrameRequestsById.delete(details.requestId);
}, { urls: ["http://*/*", "https://*/*"] });

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
  if (message.type === "inline-video-state") {
    updateInlineVideoState(message, sender);
    return undefined;
  }
  if (
    message.type === "safe-area-fallback" &&
    Number.isSafeInteger(message.navigationGeneration) &&
    Number.isSafeInteger(message.revision)
  ) {
    const token = tokenByTab.get(sender.tab.id);
    const policy = token && policiesByToken.get(token);
    if (
      policy &&
      policy.navigationGeneration === message.navigationGeneration &&
      policy.revision === message.revision &&
      nativePort
    ) {
      nativePort.postMessage({
        type: "safe-area-fallback",
        protocolVersion: PROTOCOL_VERSION,
        token,
        revision: message.revision,
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
  inlineVideosByTab.delete(tabId);
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

function probeDom(message) {
  const policy = policiesByToken.get(message.token);
  if (policy?.domDiagnosticsEnabled !== true || policy.revision !== message.revision ||
      policy.navigationGeneration !== message.navigationGeneration ||
      !Number.isSafeInteger(message.requestId)) return;
  const tabEntry = Array.from(tokenByTab.entries()).find(([, token]) => token === message.token);
  if (!tabEntry) return;
  const postResult = (payload) => {
    const current = policiesByToken.get(message.token);
    if (!nativePort || current?.domDiagnosticsEnabled !== true ||
        current.revision !== message.revision || current.navigationGeneration !== message.navigationGeneration ||
        tokenByTab.get(tabEntry[0]) !== message.token) return;
    nativePort.postMessage({
      type: "dom-probe-result", protocolVersion: PROTOCOL_VERSION,
      token: message.token, revision: message.revision, requestId: message.requestId,
      navigationGeneration: message.navigationGeneration, payload,
    });
  };
  browser.tabs.sendMessage(tabEntry[0], {
    type: "dom-probe", revision: message.revision, navigationGeneration: message.navigationGeneration,
  }, { frameId: 0 }).then(postResult, () => postResult(null));
}

function normalizedViewportRect(value) {
  const values = [value?.left, value?.top, value?.right, value?.bottom];
  if (!values.every(Number.isFinite) || value.left < 0 || value.top < 0 ||
      value.right > 1 || value.bottom > 1 ||
      value.right <= value.left || value.bottom <= value.top) return null;
  return { left: value.left, top: value.top, right: value.right, bottom: value.bottom };
}

function probeTextInputOcclusion(message) {
  const policy = policiesByToken.get(message.token);
  const viewportRect = normalizedViewportRect(message.viewportRect);
  if (!policy || policy.revision !== message.revision ||
      policy.navigationGeneration !== message.navigationGeneration ||
      !Number.isSafeInteger(message.requestId) || !viewportRect) return;
  const tabEntry = Array.from(tokenByTab.entries()).find(([, token]) => token === message.token);
  if (!tabEntry) return;
  const postResult = (result) => {
    const current = policiesByToken.get(message.token);
    if (!nativePort || current?.revision !== message.revision ||
        current.navigationGeneration !== message.navigationGeneration ||
        tokenByTab.get(tabEntry[0]) !== message.token) return;
    nativePort.postMessage({
      type: "text-input-occlusion-result",
      protocolVersion: PROTOCOL_VERSION,
      token: message.token,
      revision: message.revision,
      navigationGeneration: message.navigationGeneration,
      requestId: message.requestId,
      result: Number.isInteger(result) && result >= 0 && result <= 2 ? result : 0,
    });
  };
  browser.tabs.sendMessage(tabEntry[0], {
    type: "text-input-occlusion-probe",
    viewportRect,
    focusedOnly: message.focusedOnly === true,
  }, { frameId: 0 }).then(postResult, () => postResult(false));
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

function updateInlineVideoPresentation(message) {
  const policy = policiesByToken.get(message.token);
  if (
    !policy ||
    policy.revision !== message.revision ||
    policy.navigationGeneration !== message.navigationGeneration ||
    typeof message.expected !== "boolean" ||
    !Number.isSafeInteger(message.requestId) ||
    (
      message.expected &&
      (
        policy.inlineMediaPlayerEnabled !== true ||
        !/^[a-f0-9]{32}$/.test(message.documentNonce) ||
        !/^[a-f0-9]{32}$/.test(message.elementNonce)
      )
    )
  ) return;
  const tabEntry = Array.from(tokenByTab.entries()).find(([, token]) => token === message.token);
  if (!tabEntry) return;
  const payload = {
    type: "inline-video-presentation",
    expected: message.expected,
    documentNonce: message.documentNonce,
    elementNonce: message.elementNonce,
  };
  const postResult = (accepted) => {
    const current = policiesByToken.get(message.token);
    if (
      !nativePort ||
      current?.revision !== message.revision ||
      current.navigationGeneration !== message.navigationGeneration ||
      tokenByTab.get(tabEntry[0]) !== message.token
    ) return;
    nativePort.postMessage({
      type: "inline-video-presentation-result",
      protocolVersion: PROTOCOL_VERSION,
      token: message.token,
      revision: message.revision,
      navigationGeneration: message.navigationGeneration,
      requestId: message.requestId,
      accepted: accepted === true,
    });
  };
  browser.tabs.sendMessage(tabEntry[0], payload, { frameId: 0 }).then(
    (result) => postResult(result?.accepted === true),
    () => postResult(false),
  );
}

async function applyWebRtcPolicy(message) {
  const network = browser.privacy?.network;
  const peerConnectionEnabled = network?.peerConnectionEnabled;
  const ipHandlingPolicy = network?.webRTCIPHandlingPolicy;
  if (!peerConnectionEnabled || !ipHandlingPolicy) {
    throw new Error("WebRTC privacy settings unavailable");
  }
  const details = { scope: "regular" };
  const clearOwnSetting = async (setting) => {
    await setting.clear(details);
    const current = await setting.get({});
    if (current.levelOfControl === "controlled_by_this_extension") {
      throw new Error("WebRTC privacy setting could not be cleared");
    }
  };
  const setAndVerify = async (setting, value) => {
    await setting.set({ ...details, value });
    const current = await setting.get({});
    if (current.value !== value || current.levelOfControl !== "controlled_by_this_extension") {
      throw new Error("WebRTC privacy setting is controlled elsewhere");
    }
  };
  if (message.peerConnectionsEnabled === false) {
    await setAndVerify(peerConnectionEnabled, false);
    await clearOwnSetting(ipHandlingPolicy);
  } else if (message.peerConnectionsEnabled === true && message.ipHandlingPolicy !== null) {
    await setAndVerify(ipHandlingPolicy, message.ipHandlingPolicy);
    await clearOwnSetting(peerConnectionEnabled);
  } else if (message.peerConnectionsEnabled === true && message.ipHandlingPolicy === null) {
    await clearOwnSetting(peerConnectionEnabled);
    await clearOwnSetting(ipHandlingPolicy);
  } else {
    throw new Error("Invalid WebRTC peer connection policy");
  }
}

function connectNative() {
  nativePort = browser.runtime.connectNative(NATIVE_APP);
  nativePort.onMessage.addListener((message) => {
    if (!message || message.protocolVersion !== PROTOCOL_VERSION) return;
    if (
      message.type === "webrtc-policy" &&
      Number.isSafeInteger(message.revision)
    ) {
      if (message.revision < latestWebRtcPolicyRevision) return;
      latestWebRtcPolicyRevision = message.revision;
      webRtcPolicyQueue = webRtcPolicyQueue.then(async () => {
        if (message.revision !== latestWebRtcPolicyRevision) return;
        await applyWebRtcPolicy(message);
        if (message.revision !== latestWebRtcPolicyRevision) return;
        nativePort?.postMessage({
          type: "webrtc-policy-ready",
          protocolVersion: PROTOCOL_VERSION,
          revision: message.revision,
        });
      }).catch((error) => {
        if (message.revision === latestWebRtcPolicyRevision) {
          nativePort?.postMessage({
            type: "failed",
            protocolVersion: PROTOCOL_VERSION,
            reason: String(error).slice(0, 512),
          });
        }
      });
    } else if (message.type === "performance-diagnostics-state") {
      publishPerformanceDiagnosticsState(message);
    } else if (message.type === "performance-diagnostics-gap") {
      publishPerformanceDiagnosticsGap(message);
    } else if (
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
        const tabEntry = Array.from(tokenByTab.entries())
          .find(([, token]) => token === message.token);
        if (message.inlineMediaPlayerEnabled !== true && tabEntry) {
          inlineVideosByTab.delete(tabEntry[0]);
          publishInlineVideoState(tabEntry[0]);
        }
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
      for (const [tabId, token] of tokenByTab) {
        if (token === message.token) {
          tokenByTab.delete(tabId);
          inlineVideosByTab.delete(tabId);
        }
      }
    } else if (message.type === "reader-extract" && typeof message.token === "string") {
      extractReader(message);
    } else if (
      message.type === "text-input-occlusion-probe" &&
      typeof message.token === "string"
    ) {
      probeTextInputOcclusion(message);
    } else if (message.type === "dom-probe" && typeof message.token === "string") {
      probeDom(message);
    } else if (
      message.type === "picture-in-picture-playback" &&
      typeof message.token === "string"
    ) {
      updatePictureInPicturePlayback(message);
    } else if (
      message.type === "inline-video-presentation" &&
      typeof message.token === "string"
    ) {
      updateInlineVideoPresentation(message);
    }
  });
  nativePort.onDisconnect.addListener(() => { nativePort = null; });
  nativePort.postMessage({ type: "ready", protocolVersion: PROTOCOL_VERSION });
}

connectNative();
