"use strict";

const API_RESULTS_KEY = "api-results";
let primaryPage = null;
let optionsReady = false;
let conformanceStarted = false;

// Reproduce an extension's user-origin CSS only on the dedicated local fixture.
browser.webNavigation.onCompleted.addListener(async ({ tabId, frameId, url }) => {
  let page;
  try { page = new URL(url); } catch (_) { return; }
  if (frameId !== 0 || page.hostname !== "127.0.0.1" ||
      page.pathname !== "/site-matrix/user-origin-conflict") return;
  try {
    await browser.tabs.insertCSS(tabId, {
      frameId: 0,
      cssOrigin: "user",
      code: "html > body { padding-top: 0 !important; }",
    });
    await browser.tabs.executeScript(tabId, {
      frameId: 0,
      code: "document.documentElement.dataset.candyFixtureUserCss = 'installed';",
    });
  } catch (_) {
    // A navigation can replace the fixture before the extension has injected CSS.
  }
});

function maybeStartConformance() {
  if (primaryPage == null || !optionsReady || conformanceStarted) return false;
  conformanceStarted = true;
  browser.runtime.sendNativeMessage("browser", { type: "phase", phase: "probe-start" });
  runConformance();
  return true;
}

async function runConformance() {
  try {
    const results = await probeTab(primaryPage.tabId, primaryPage.pageUrl);
    await browser.runtime.sendNativeMessage("browser", {
      type: "conformance",
      url: primaryPage.pageUrl,
      results,
    });
    await browser.tabs.sendMessage(primaryPage.tabId, {
      type: "conformance-complete",
      results,
    });
  } catch (error) {
    const results = { error: String(error) };
    await browser.runtime.sendNativeMessage("browser", {
      type: "conformance",
      url: primaryPage.pageUrl,
      results,
    });
  }
}

browser.browserAction.setTitle({ title: "Candy conformance" });
browser.browserAction.setBadgeText({ text: "1" });

async function probeTab(tabId, pageUrl) {
  const results = {
    tabs: false,
    webNavigation: typeof browser.webNavigation?.onCommitted?.addListener === "function",
    scripting: false,
    css: false,
    storage: false,
    runtimeMessaging: false,
    downloads: false,
  };
  await browser.tabs.update(tabId, { active: true });
  const created = await browser.tabs.create({ active: false, index: 0 });
  await browser.tabs.remove(created.id);
  results.tabLifecycle = true;
  const tabs = await browser.tabs.query({ active: true, currentWindow: true });
  results.tabs = tabs.some(tab => tab.id === tabId);
  await browser.storage.local.set({ [API_RESULTS_KEY]: { marker: "stored" } });
  const stored = await browser.storage.local.get(API_RESULTS_KEY);
  results.storage = stored[API_RESULTS_KEY]?.marker === "stored";
  const reply = await browser.tabs.sendMessage(tabId, { type: "runtime-probe" });
  results.runtimeMessaging = reply?.marker === "content-reply";
  await browser.tabs.executeScript(tabId, {
    code: "document.documentElement.dataset.candyExtensionScript = 'applied';",
  });
  results.scripting = true;
  await browser.tabs.insertCSS(tabId, {
    code: "html { --candy-extension-css: applied; }",
  });
  results.css = true;
  const downloadId = await browser.downloads.download({
    url: `${pageUrl}?fixture-download=1`,
    filename: "candy-fixture.txt",
  });
  results.downloads = Number.isInteger(downloadId);
  return results;
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (message?.type === "primary-ready" && sender.tab?.id != null) {
    primaryPage = { tabId: sender.tab.id, pageUrl: sender.tab.url };
    browser.runtime.sendNativeMessage("browser", { type: "phase", phase: "primary-ready" });
    return Promise.resolve({ started: maybeStartConformance() });
  }
  if (message?.type !== "options-ready") {
    return undefined;
  }
  optionsReady = true;
  browser.runtime.sendNativeMessage("browser", { type: "phase", phase: "options-ready" });
  return Promise.resolve({ started: maybeStartConformance() });
});
