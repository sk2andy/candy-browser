"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.toppings";
const USER_SCRIPT_PORT = "candy-topping-user-script";
const PROTOCOL_VERSION = 3;
let reconciliationQueue = Promise.resolve();
let listenersInstalled = false;
let nativePort = null;
let bridgeSequence = 0;
const pendingBridgeRequests = new Map();
const scriptWorlds = new Map();
const documentAuthorizations = new Map();
const userScriptPorts = new Map();

function documentKey(sender, scriptId) {
  if (!Number.isSafeInteger(sender?.tab?.id) || !Number.isSafeInteger(sender.frameId) ||
      typeof sender.documentId !== "string" || sender.documentId.length === 0) return null;
  return `${sender.tab.id}:${sender.frameId}:${sender.documentId}:${scriptId}`;
}

function portKey(tabId, frameId, documentId, scriptId) {
  return `${tabId}:${frameId}:${documentId}:${scriptId}`;
}

function randomChallenge() {
  const values = new Uint32Array(4);
  crypto.getRandomValues(values);
  return [...values].map((value) => value.toString(16).padStart(8, "0")).join("");
}

async function authorizeDocument(message, sender) {
  const key = documentKey(sender, message.scriptId);
  if (!key || sender.tab.incognito === true) return null;
  const cached = documentAuthorizations.get(key);
  if (cached) return cached;
  const challenge = randomChallenge();
  const response = await browser.tabs.sendMessage(sender.tab.id, {
    type: "authorize-session",
    protocolVersion: PROTOCOL_VERSION,
    challenge,
    scriptId: message.scriptId,
    frameId: sender.frameId,
    documentId: sender.documentId,
    url: String(sender.url || ""),
    topUrl: String(sender.tab.url || ""),
  }, { documentId: sender.documentId });
  if (response?.allowed !== true || response.challenge !== challenge ||
      typeof response.bindingToken !== "string" || response.bindingToken.length === 0 ||
      typeof response.candyTabId !== "string" || response.candyTabId.length === 0) return null;
  const authorization = {
    bindingToken: response.bindingToken,
    candyTabId: response.candyTabId,
    geckoTabId: sender.tab.id,
    frameId: sender.frameId,
    documentId: sender.documentId,
    url: String(sender.url || ""),
    topUrl: String(sender.tab.url || ""),
  };
  documentAuthorizations.set(key, authorization);
  setTimeout(() => {
    if (documentAuthorizations.get(key) === authorization) documentAuthorizations.delete(key);
  }, 10000);
  return authorization;
}

function notifyDocumentDisconnected(authorization, scriptId) {
  if (!nativePort || !authorization) return;
  nativePort.postMessage({
    type: "document-disconnected",
    protocolVersion: PROTOCOL_VERSION,
    scriptId,
    ...authorization,
  });
}

function ensureListeners() {
  if (listenersInstalled) return;
  const connectEvent = browser.runtime.onUserScriptConnect;
  const messageEvent = browser.runtime.onUserScriptMessage;
  if (!connectEvent || !messageEvent) throw new Error("user-script messaging is unavailable");
  connectEvent.addListener((port) => {
    if (port.name !== USER_SCRIPT_PORT) return;
    const scriptEntry = [...scriptWorlds.entries()].find(
      ([, worldId]) => worldId === port.sender?.userScriptWorldId,
    );
    if (!scriptEntry) {
      port.disconnect();
      return;
    }
    const [scriptId] = scriptEntry;
    const authorizationKey = documentKey(port.sender, scriptId);
    const authorization = authorizationKey && documentAuthorizations.get(authorizationKey);
    if (!authorization) {
      port.disconnect();
      return;
    }
    const key = portKey(authorization.geckoTabId, authorization.frameId,
      authorization.documentId, scriptId);
    const previous = userScriptPorts.get(key)?.port;
    if (previous && previous !== port) previous.disconnect();
    userScriptPorts.set(key, {
      port,
      scriptId,
      worldId: port.sender.userScriptWorldId,
      authorization,
    });
    port.onDisconnect.addListener(() => {
      if (userScriptPorts.get(key)?.port !== port) return;
      userScriptPorts.delete(key);
      if (authorizationKey) documentAuthorizations.delete(authorizationKey);
      notifyDocumentDisconnected(authorization, scriptId);
    });
  });
  messageEvent.addListener(async (message, sender) => {
    if (!message || message.protocolVersion !== PROTOCOL_VERSION) return undefined;
    const expectedWorld = scriptWorlds.get(message.scriptId);
    if (!expectedWorld || sender.userScriptWorldId !== expectedWorld) return undefined;
    if (message.type === "private-check") {
      let authorization;
      try {
        authorization = await authorizeDocument(message, sender);
      } catch (_) {
        authorization = null;
      }
      return { allowed: authorization !== null };
    }
    const key = documentKey(sender, message.scriptId);
    const portEntry = key && userScriptPorts.get(portKey(
      sender.tab.id,
      sender.frameId,
      sender.documentId,
      message.scriptId,
    ));
    const authorization = portEntry?.authorization;
    if (message.type !== "bridge" || !authorization || typeof message.payload !== "string" ||
        message.payload.length > 34816) return undefined;
    return requestNative({
      type: "bridge",
      protocolVersion: PROTOCOL_VERSION,
      scriptId: message.scriptId,
      payload: message.payload,
      ...authorization,
    });
  });
  listenersInstalled = true;
}

function requestNative(message) {
  if (!nativePort) return Promise.reject(new Error("native bridge unavailable"));
  bridgeSequence = (bridgeSequence % 2147483647) + 1;
  const bridgeId = bridgeSequence;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      pendingBridgeRequests.delete(bridgeId);
      reject(new Error("native bridge timed out"));
    }, 10000);
    pendingBridgeRequests.set(bridgeId, { resolve, reject, timeout });
    nativePort.postMessage({ ...message, bridgeId });
  });
}

async function reconcile(port, message) {
  if (message?.type !== "reconcile" || message.protocolVersion !== PROTOCOL_VERSION ||
      !Number.isSafeInteger(message.revision) || message.revision < 0 ||
      !Array.isArray(message.scripts)) return;
  try {
    ensureListeners();
    const existing = await browser.userScripts.getScripts();
    const incomingIds = new Set(message.scripts.map((script) => script.id));
    const existingIds = new Set(existing.map((script) => script.id));
    const removedIds = existing.map((script) => script.id).filter((id) => !incomingIds.has(id));
    const asRegistration = ({ scriptId, ...registration }) => registration;
    const updated = message.scripts.filter((script) => existingIds.has(script.id)).map(asRegistration);
    const added = message.scripts.filter((script) => !existingIds.has(script.id)).map(asRegistration);
    scriptWorlds.clear();
    message.scripts.forEach((script) => scriptWorlds.set(script.scriptId, script.worldId));
    documentAuthorizations.clear();
    for (const [key, entry] of userScriptPorts.entries()) {
      if (scriptWorlds.get(entry.scriptId) !== entry.worldId) {
        userScriptPorts.delete(key);
        entry.port.disconnect();
      }
    }
    for (const worldId of new Set(message.scripts.map((script) => script.worldId))) {
      await browser.userScripts.configureWorld({ worldId, messaging: true });
    }
    if (removedIds.length > 0) await browser.userScripts.unregister({ ids: removedIds });
    if (updated.length > 0) await browser.userScripts.update(updated);
    if (added.length > 0) await browser.userScripts.register(added);
    port.postMessage({ type: "ready", protocolVersion: PROTOCOL_VERSION, revision: message.revision });
  } catch (error) {
    port.postMessage({
      type: "failed",
      protocolVersion: PROTOCOL_VERSION,
      revision: message.revision,
      reason: String(error).slice(0, 512),
    });
  }
}

function connect() {
  const port = browser.runtime.connectNative(NATIVE_APP);
  nativePort = port;
  port.onMessage.addListener((message) => {
    if (message?.type === "reconcile") {
      reconciliationQueue = reconciliationQueue.then(() => reconcile(port, message));
    } else if (message?.type === "bridge-response" && Number.isSafeInteger(message.bridgeId)) {
      const pending = pendingBridgeRequests.get(message.bridgeId);
      if (!pending) return;
      pendingBridgeRequests.delete(message.bridgeId);
      clearTimeout(pending.timeout);
      pending.resolve(message.response);
    } else if (message?.type === "menu-invoke" && Number.isSafeInteger(message.geckoTabId) &&
        Number.isSafeInteger(message.frameId) && typeof message.documentId === "string" &&
        typeof message.scriptId === "string" && typeof message.commandId === "string") {
      userScriptPorts.get(portKey(message.geckoTabId, message.frameId,
        message.documentId, message.scriptId))?.port.postMessage({
        type: "menu-invoke",
        scriptId: message.scriptId,
        commandId: message.commandId,
      });
    }
  });
  port.onDisconnect.addListener(() => {
    if (nativePort === port) nativePort = null;
    for (const pending of pendingBridgeRequests.values()) {
      clearTimeout(pending.timeout);
      pending.reject(new Error("native bridge disconnected"));
    }
    pendingBridgeRequests.clear();
  });
}

connect();
