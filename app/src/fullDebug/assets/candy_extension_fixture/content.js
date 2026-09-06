"use strict";

const host = window.location.hostname;
document.title = `Candy Firefox Extension: ${host}`;

browser.runtime.onMessage.addListener(message => {
  if (message?.type === "runtime-probe") return Promise.resolve({ marker: "content-reply" });
  if (message?.type === "conformance-complete") {
    document.documentElement.dataset.candyExtensionResults = JSON.stringify(message.results);
  }
  return undefined;
});

browser.runtime.sendMessage({ type: "primary-ready" }).catch(error => {
  document.documentElement.dataset.candyExtensionError = String(error);
});
