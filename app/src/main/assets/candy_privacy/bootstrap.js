"use strict";

const NATIVE_APP = "dev.sk2andy.materialbrowser.privacy";

async function bind() {
  const token = new URL(location.href).searchParams.get("token");
  if (!token) return;
  const result = await browser.runtime.sendMessage({ type: "bind", token });
  if (!result || result.type !== "bound") return;
  await browser.runtime.sendNativeMessage(NATIVE_APP, {
    type: "bound",
    token,
    revision: result.revision,
  });
}

bind().catch(() => {});
