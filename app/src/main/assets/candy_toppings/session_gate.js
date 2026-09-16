"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.toppings";
const PROTOCOL_VERSION = 3;

browser.runtime.onMessage.addListener((message) => {
  if (message?.type !== "authorize-session" ||
      message.protocolVersion !== PROTOCOL_VERSION ||
      typeof message.challenge !== "string") return undefined;
  return browser.runtime.sendNativeMessage(NATIVE_APP, message).then((response) => {
    if (typeof response !== "string") return null;
    try { return JSON.parse(response); } catch (_) { return null; }
  });
});
