"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.toppings";
const USER_SCRIPT_PORT = "candy-topping-user-script";
const PROTOCOL_VERSION = 2;
let reconciliationQueue = Promise.resolve();
let privateCheckListenerInstalled = false;
let userScriptConnectListenerInstalled = false;
let nativePort = null;
let activeCandyTabId = null;
let bridgeSequence = 0;
const pendingBridgeRequests = new Map();
const scriptWorlds = new Map();
const userScriptPorts = new Map();

function userScriptPortKey(geckoTabId, scriptId) {
  return `${geckoTabId}:${scriptId}`;
}

function ensureUserScriptConnectListener() {
  if (userScriptConnectListenerInstalled) return;
  const event = browser.runtime.onUserScriptConnect;
  if (!event) throw new Error("user-script port messaging is unavailable");
  event.addListener((port) => {
    if (port.name !== USER_SCRIPT_PORT || !Number.isSafeInteger(port.sender?.tab?.id)) {
      return;
    }
    const scriptEntry = [...scriptWorlds.entries()].find(
      ([, worldId]) => worldId === port.sender.userScriptWorldId,
    );
    if (!scriptEntry) {
      port.disconnect();
      return;
    }
    const [scriptId] = scriptEntry;
    const key = userScriptPortKey(port.sender.tab.id, scriptId);
    const previous = userScriptPorts.get(key)?.port;
    if (previous && previous !== port) previous.disconnect();
    userScriptPorts.set(key, {
      port,
      scriptId,
      worldId: port.sender.userScriptWorldId,
    });
    port.onDisconnect.addListener(() => {
      if (userScriptPorts.get(key)?.port === port) userScriptPorts.delete(key);
    });
  });
  userScriptConnectListenerInstalled = true;
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

function ensurePrivateCheckListener() {
  if (privateCheckListenerInstalled) return;
  const event = browser.runtime.onUserScriptMessage;
  if (!event) throw new Error("user-script messaging is unavailable");
  event.addListener((message, sender, sendResponse) => {
    if (!message || message.protocolVersion !== PROTOCOL_VERSION) {
      return undefined;
    }
    const expectedWorld = scriptWorlds.get(message.scriptId);
    if (!expectedWorld || sender.userScriptWorldId !== expectedWorld) return undefined;
    if (message.type === "private-check") {
      sendResponse({ allowed: sender.tab?.incognito !== true });
      return undefined;
    }
    if (message.type !== "bridge" || typeof message.payload !== "string" ||
        message.payload.length > 34816 || !Number.isSafeInteger(sender.tab?.id)) {
      return undefined;
    }
    return requestNative({
      type: "bridge",
      protocolVersion: PROTOCOL_VERSION,
      scriptId: message.scriptId,
      geckoTabId: sender.tab.id,
      candyTabId: activeCandyTabId,
      active: sender.tab.active === true,
      url: String(sender.url || ""),
      incognito: sender.tab.incognito === true,
      payload: message.payload,
    });
  });
  privateCheckListenerInstalled = true;
}

function isReconcileMessage(message) {
  return message &&
    message.type === "reconcile" &&
    message.protocolVersion === PROTOCOL_VERSION &&
    Number.isSafeInteger(message.revision) &&
    message.revision >= 0 &&
    Array.isArray(message.scripts);
}

async function reconcile(port, message) {
  if (!isReconcileMessage(message)) {
    return;
  }
  try {
    ensurePrivateCheckListener();
    ensureUserScriptConnectListener();
    const existing = await browser.userScripts.getScripts();
    const incomingIds = new Set(message.scripts.map((script) => script.id));
    const existingIds = new Set(existing.map((script) => script.id));
    const removedIds = existing
      .map((script) => script.id)
      .filter((id) => !incomingIds.has(id));
    const asRegistration = ({ scriptId, ...registration }) => registration;
    const updated = message.scripts
      .filter((script) => existingIds.has(script.id))
      .map(asRegistration);
    const added = message.scripts
      .filter((script) => !existingIds.has(script.id))
      .map(asRegistration);
    const worldIds = [...new Set(message.scripts.map((script) => script.worldId))];
    scriptWorlds.clear();
    message.scripts.forEach((script) => scriptWorlds.set(script.scriptId, script.worldId));
    for (const [key, entry] of userScriptPorts.entries()) {
      if (scriptWorlds.get(entry.scriptId) !== entry.worldId) {
        userScriptPorts.delete(key);
        entry.port.disconnect();
      }
    }
    for (const worldId of worldIds) {
      await browser.userScripts.configureWorld({
        worldId,
        messaging: true,
      });
    }
    if (removedIds.length > 0) {
      await browser.userScripts.unregister({
        ids: removedIds,
      });
    }
    if (updated.length > 0) {
      await browser.userScripts.update(updated);
    }
    if (added.length > 0) {
      await browser.userScripts.register(added);
    }
    port.postMessage({
      type: "ready",
      protocolVersion: PROTOCOL_VERSION,
      revision: message.revision,
    });
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
      return;
    }
    if (message?.type === "bridge-response" && Number.isSafeInteger(message.bridgeId)) {
      const pending = pendingBridgeRequests.get(message.bridgeId);
      if (!pending) return;
      pendingBridgeRequests.delete(message.bridgeId);
      clearTimeout(pending.timeout);
      pending.resolve(message.response);
      return;
    }
    if (message?.type === "bind-active" && typeof message.candyTabId === "string") {
      activeCandyTabId = message.candyTabId;
      browser.tabs.query({ active: true, currentWindow: true }).then((tabs) => {
        const tab = tabs.find((candidate) => Number.isSafeInteger(candidate.id));
        if (!tab) return;
        port.postMessage({
          type: "tab-bound",
          protocolVersion: PROTOCOL_VERSION,
          geckoTabId: tab.id,
          candyTabId: message.candyTabId,
        });
      });
      return;
    }
    if (message?.type === "menu-invoke" && Number.isSafeInteger(message.geckoTabId) &&
        typeof message.scriptId === "string" && typeof message.commandId === "string") {
      const userScriptPort = userScriptPorts.get(
        userScriptPortKey(message.geckoTabId, message.scriptId),
      )?.port;
      if (!userScriptPort) return;
      userScriptPort.postMessage({
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
