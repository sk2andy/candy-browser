"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.toppings";
const PROTOCOL_VERSION = 1;
let reconciliationQueue = Promise.resolve();
let privateCheckListenerInstalled = false;

function ensurePrivateCheckListener() {
  if (privateCheckListenerInstalled) return;
  const event = browser.runtime.onUserScriptMessage;
  if (!event) throw new Error("user-script messaging is unavailable");
  event.addListener((message, sender, sendResponse) => {
    if (!message || message.type !== "private-check" ||
        message.protocolVersion !== PROTOCOL_VERSION) {
      return undefined;
    }
    sendResponse({ allowed: sender.tab?.incognito !== true });
    return undefined;
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
    const existing = await browser.userScripts.getScripts();
    const incomingIds = new Set(message.scripts.map((script) => script.id));
    const existingIds = new Set(existing.map((script) => script.id));
    const removedIds = existing
      .map((script) => script.id)
      .filter((id) => !incomingIds.has(id));
    const updated = message.scripts.filter((script) => existingIds.has(script.id));
    const added = message.scripts.filter((script) => !existingIds.has(script.id));
    const worldIds = [...new Set(message.scripts.map((script) => script.worldId))];
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
  port.onMessage.addListener((message) => {
    reconciliationQueue = reconciliationQueue.then(() => reconcile(port, message));
  });
}

connect();
