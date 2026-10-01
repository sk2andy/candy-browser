"use strict";

const enabled = document.getElementById("enabled");
if (new URL(location.href).searchParams.get("navigation-window") === "1") {
  const link = document.getElementById("asset-window");
  link.hidden = false;
  link.style.cssText = "position:fixed;left:16px;top:100px;width:220px;height:56px;display:grid;place-items:center;background:#ddd;color:#111;";
}
browser.storage.local.get("options-enabled").then(value => {
  enabled.checked = value["options-enabled"] === true;
});
browser.runtime.sendMessage({ type: "options-ready" });
enabled.addEventListener("change", () => {
  browser.storage.local.set({ "options-enabled": enabled.checked });
});
