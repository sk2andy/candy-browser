"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.privacy";
const PROTOCOL_VERSION = 2;
const RULE_FILES = {
  blocked: [
    "blocked_hosts.txt",
    "easylist_blocked_hosts.txt",
    "hagezi_blocked_hosts.txt",
    "uassets_blocked_hosts.txt",
  ],
  blockedPairs: ["uassets_blocked_host_pairs.txt"],
  allowedPairs: ["easylist_allowed_host_pairs.txt", "uassets_allowed_host_pairs.txt"],
  familyAllows: ["first_party_family_allowed_host_pairs.txt"],
  advanced: "uassets_advanced_filters.txt",
  cosmetics: ["easylist_cosmetic_rules.txt", "uassets_cosmetic_rules.txt"],
  procedural: "uassets_procedural_cosmetic_rules.txt",
  candyDefaults: "candy_default_rules.txt",
};
const policiesByToken = new Map();
const tokenByTab = new Map();
const pendingEvents = new Map();
let nativePort = null;
let bundledRules = null;
let flushTimer = null;

function lines(text) {
  return text.split(/\r?\n/).map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"));
}

async function loadLines(fileNames) {
  const groups = await Promise.all(fileNames.map(async (name) => {
    const response = await fetch(browser.runtime.getURL(`rules/${name}`));
    if (!response.ok) throw new Error(`Candy rule asset unavailable: ${name}`);
    return lines(await response.text());
  }));
  return groups.flat();
}

async function loadText(fileName) {
  const response = await fetch(browser.runtime.getURL(`rules/${fileName}`));
  if (!response.ok) throw new Error(`Candy rule asset unavailable: ${fileName}`);
  return response.text();
}

function pairIndex(rows) {
  const result = new Map();
  for (const row of rows) {
    const fields = row.toLowerCase().split("\t");
    if (fields.length !== 2) throw new Error("Invalid Candy pair asset");
    const values = result.get(fields[0]) || [];
    values.push(fields[1]);
    result.set(fields[0], values);
  }
  return result;
}

async function loadBundledRules() {
  const [
    blocked,
    blockedPairs,
    allowedPairs,
    familyAllows,
    advanced,
    easyListCosmetics,
    uAssetsCosmetics,
    procedural,
    candyDefaults,
  ] = await Promise.all([
    loadLines(RULE_FILES.blocked),
    loadLines(RULE_FILES.blockedPairs),
    loadLines(RULE_FILES.allowedPairs),
    loadLines(RULE_FILES.familyAllows),
    loadText(RULE_FILES.advanced),
    loadText(RULE_FILES.cosmetics[0]),
    loadText(RULE_FILES.cosmetics[1]),
    loadText(RULE_FILES.procedural),
    loadText(RULE_FILES.candyDefaults),
  ]);
  return {
    blocked: new Set(blocked.map((host) => host.toLowerCase())),
    blockedPairs: pairIndex(blockedPairs),
    allowedPairs: pairIndex(allowedPairs),
    familyAllows: pairIndex(familyAllows),
    advanced: CandyPrivacyRules.parseAdvanced(advanced),
    cosmetics: [
      ...CandyPrivacyRules.parseCosmetic(easyListCosmetics, "candy-easylist-cosmetic:2"),
      ...CandyPrivacyRules.parseCosmetic(uAssetsCosmetics, "candy-uassets-cosmetic:2"),
    ],
    procedural: CandyPrivacyRules.parseProcedural(procedural),
    candyDefaults: CandyPrivacyRules.parseCandyDefaults(candyDefaults),
  };
}

function suffixes(host) {
  const values = [];
  for (let candidate = host; candidate;) {
    values.push(candidate);
    const dot = candidate.indexOf(".");
    if (dot < 0) break;
    candidate = candidate.slice(dot + 1);
  }
  return values;
}

function hostMatches(host, expected) {
  return CandyPrivacyRules.hostMatches(host, expected);
}

function sameSite(first, second) {
  return hostMatches(first, second) || hostMatches(second, first);
}

function patternMatches(host, pattern) {
  return CandyPrivacyRules.hostPatternMatches(host, pattern);
}

function pairMatches(index, requestHost, pageHost, matcher = hostMatches) {
  for (const requestCandidate of suffixes(requestHost)) {
    const pages = index.get(requestCandidate);
    if (pages && pages.some((page) => page === "*" || matcher(pageHost, page))) return true;
  }
  return false;
}

function bundledShouldBlock(requestHost, pageHost) {
  if (pageHost && sameSite(requestHost, pageHost)) return false;
  if (pairMatches(bundledRules.allowedPairs, requestHost, pageHost || "")) return false;
  if (pairMatches(bundledRules.familyAllows, requestHost, pageHost || "", patternMatches)) return false;
  if (pageHost && pairMatches(bundledRules.blockedPairs, requestHost, pageHost)) return true;
  return suffixes(requestHost).some((candidate) => bundledRules.blocked.has(candidate));
}

function candyComparator(left, right) {
  const leftPairAllow = left.k === "P" && left.a === "A" ? 1 : 0;
  const rightPairAllow = right.k === "P" && right.a === "A" ? 1 : 0;
  if (leftPairAllow !== rightPairAllow) return rightPairAllow - leftPairAllow;
  if ((left.k === "P") !== (right.k === "P")) return left.k === "P" ? -1 : 1;
  if ((left.f || "").length !== (right.f || "").length) return (right.f || "").length - (left.f || "").length;
  if ((left.r || "").length !== (right.r || "").length) return (right.r || "").length - (left.r || "").length;
  if ((left.a === "A") !== (right.a === "A")) return left.a === "A" ? -1 : 1;
  if (left.id < right.id) return -1;
  if (left.id > right.id) return 1;
  return 0;
}

function candyDecision(policy, requestHost, pageHost) {
  const matches = policy.rules.filter((rule) => hostMatches(requestHost, rule.r) &&
    (rule.k !== "P" || (pageHost && hostMatches(pageHost, rule.f))));
  if (!matches.length) return null;
  matches.sort(candyComparator);
  return matches[0];
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
    if (policy) policy.pageHost = hostFromUrl(details.url);
    return {};
  }
  if (!bundledRules || !policy) return { cancel: true };
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
  if (policy.blockAds) {
    const decision = candyDecision(policy, requestHost, pageHost);
    if (decision) {
      queueEvent(token, policy.revision, {
        requestUrl: details.url,
        pageUrl: pageHost ? `https://${pageHost}/` : null,
        ruleId: decision.id,
        action: decision.a,
      });
      return { cancel: decision.a === "B" };
    }
  }
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
  if (policy.blockAds) {
    const advancedDecision = CandyPrivacyRules.advancedDecision(
      bundledRules.advanced,
      details.url,
      pageHost,
    );
    if (advancedDecision === "A") return {};
    if (advancedDecision === "B") {
      queueEvent(token, policy.revision, {
        requestUrl: details.url,
        pageUrl: pageHost ? `https://${pageHost}/` : null,
        action: "B",
        builtIn: true,
      });
      return { cancel: true };
    }
  }
  if (policy.blockAds && bundledShouldBlock(requestHost, pageHost)) {
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
  if (message.type === "cosmetics") {
    const token = tokenByTab.get(sender.tab.id);
    const policy = token && policiesByToken.get(token);
    const frameHost = hostFromUrl(sender.url || message.url);
    if (!bundledRules || !policy || !frameHost) {
      return Promise.resolve({ type: "cosmetics", selectors: [], procedural: [] });
    }
    return Promise.resolve({
      type: "cosmetics",
      revision: policy.revision,
      ...CandyPrivacyRules.cosmeticPayload(bundledRules, policy, frameHost),
    });
  }
  return undefined;
});

browser.tabs.onRemoved.addListener((tabId) => {
  const token = tokenByTab.get(tabId);
  tokenByTab.delete(tabId);
  if (token) policiesByToken.delete(token);
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
    if (message.type === "policy" && typeof message.token === "string") {
      policiesByToken.set(message.token, message);
      nativePort.postMessage({
        type: "policy-ready",
        protocolVersion: PROTOCOL_VERSION,
        token: message.token,
        revision: message.revision,
      });
    } else if (message.type === "remove" && typeof message.token === "string") {
      policiesByToken.delete(message.token);
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
  loadBundledRules().then((rules) => {
    bundledRules = rules;
    nativePort.postMessage({ type: "ready", protocolVersion: PROTOCOL_VERSION });
  }).catch((error) => {
    nativePort.postMessage({
      type: "failed",
      protocolVersion: PROTOCOL_VERSION,
      reason: String(error).slice(0, 512),
    });
  });
}

connectNative();
