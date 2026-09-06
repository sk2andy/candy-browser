"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.privacy";
const PROTOCOL_VERSION = 2;

async function bind() {
  const token = new URL(location.href).searchParams.get("token");
  if (!token) return;
  const result = await browser.runtime.sendMessage({ type: "bind", token });
  if (!result || result.type !== "bound") return;
  const nativeResult = await browser.runtime.sendNativeMessage(NATIVE_APP, {
    type: "bound",
    token,
    revision: result.revision,
  });
  if (nativeResult !== true) return;
  browser.runtime.sendMessage({
    type: "binding-acknowledged",
    protocolVersion: PROTOCOL_VERSION,
    token,
    revision: result.revision,
  }).catch(() => {});
}

bind().catch(() => {});
