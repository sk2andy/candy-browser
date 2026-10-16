"use strict";

let port = null;
let clearing = false;

function connect() {
  const connected = browser.runtime.connectNative("candySiteData");
  port = connected;
  connected.onMessage.addListener(async (message) => {
    if (typeof message?.id !== "string") return;
    let cleared = false;
    if (!clearing) {
      clearing = true;
      try {
        await browser.candySiteData.clear(message.url, message.contextId, message.isPrivate);
        cleared = true;
      } catch (_) {
        // Never widen deletion or report partial failure as success.
      } finally {
        clearing = false;
      }
    }
    if (port === connected) connected.postMessage({ id: message.id, cleared });
  });
  connected.onDisconnect.addListener(() => {
    if (port !== connected) return;
    port = null;
    setTimeout(connect, 250);
  });
  connected.postMessage({ type: "ready", version: browser.runtime.getManifest().version });
}

connect();
