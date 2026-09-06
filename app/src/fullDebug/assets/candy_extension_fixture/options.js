"use strict";

const enabled = document.getElementById("enabled");
browser.storage.local.get("options-enabled").then(value => {
  enabled.checked = value["options-enabled"] === true;
});
browser.runtime.sendMessage({ type: "options-ready" });
enabled.addEventListener("change", () => {
  browser.storage.local.set({ "options-enabled": enabled.checked });
});
