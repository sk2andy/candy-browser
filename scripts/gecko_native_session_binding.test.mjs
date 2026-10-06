import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";

const background = readFileSync(
  new URL("../app/src/gecko/assets/candy_privacy/background.js", import.meta.url),
  "utf8",
);
const factory = background.slice(
  background.indexOf("function createNativeSessionBindings("),
  background.indexOf("function privacySignalSettings("),
);
const token = "11111111-2222-4333-8444-555555555555";
const challenge = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
const policy = { token, bindingChallenge: challenge, bindingMode: "native-tab", revision: 2 };

function harness() {
  const timers = new Map();
  let nextTimer = 1;
  const updates = [];
  const messages = [];
  const policiesByToken = new Map([[token, { ...policy }]]);
  const tokenByTab = new Map();
  const context = vm.createContext({
    PROTOCOL_VERSION: 2,
    setTimeout(action, delay) {
      const id = nextTimer++;
      timers.set(id, { action, delay });
      return id;
    },
    clearTimeout(id) { timers.delete(id); },
  });
  vm.runInContext(factory, context);
  const binding = context.createNativeSessionBindings({
    policiesByToken,
    tokenByTab,
    browser: {
      runtime: { getURL: (path) => `moz-extension://own/${path}` },
      tabs: {
        query: async () => [{ id: 7 }, { id: 8 }],
        update: async (id, details) => {
          updates.push({ id, url: details.url });
          throw new Error("Challenge navigation denied");
        },
      },
    },
    postNativeMessage: (message) => messages.push(message),
  });
  return { binding, timers, updates, messages, policiesByToken, tokenByTab };
}

test("native initial request waits for exact authenticated tab policy without URL replay", async () => {
  const h = harness();
  h.binding.policyChanged(policy);
  let released = false;
  const pending = h.binding.waitForPolicy(7).then((allowed) => {
    released = true;
    return allowed;
  });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(released, false);
  assert.ok(h.updates.some((entry) => entry.id === 7 && entry.url ===
    `moz-extension://own/binding.html?token=${token}&challenge=${challenge}&tab=7&revision=2`));
  assert.equal(h.tokenByTab.size, 0);
  assert.equal(h.binding.authenticate({ token, challenge, tabId: 7, revision: 2 }), true);
  assert.equal(await pending, true);
  assert.equal(h.tokenByTab.get(7), token);
  assert.equal(h.messages[0].type, "session-bound");
  assert.equal(h.messages[0].revision, 2);
  h.binding.close();
});

test("forged challenge stale revision malformed tab and duplicate binding never release request", async () => {
  const h = harness();
  h.binding.policyChanged(policy);
  const pending = h.binding.waitForPolicy(7);
  for (const message of [
    { token, challenge: "wrong", tabId: 7, revision: 2 },
    { token, challenge, tabId: 7, revision: 1 },
    { token, challenge, tabId: -1, revision: 2 },
    { token, challenge, tabId: 9007199254740992, revision: 2 },
    { token: "other", challenge, tabId: 7, revision: 2 },
  ]) assert.equal(h.binding.authenticate(message), false);
  assert.equal(h.tokenByTab.size, 0);
  h.binding.close();
  assert.equal(await pending, false);
});

test("timeout removal and native disconnect cancel held initial requests", async () => {
  for (const cancellation of ["timeout", "remove", "disconnect"]) {
    const h = harness();
    h.binding.policyChanged(policy);
    const pending = h.binding.waitForPolicy(7);
    if (cancellation === "timeout") {
      Array.from(h.timers.values()).find((timer) => timer.delay === 15000).action();
    } else if (cancellation === "remove") {
      h.binding.removeToken(token);
    } else {
      h.binding.close();
    }
    assert.equal(await pending, false);
    assert.equal(h.tokenByTab.size, 0);
    h.binding.close();
  }
});

test("one authenticated token cannot be applied to another tab", async () => {
  const h = harness();
  h.binding.policyChanged(policy);
  assert.equal(h.binding.authenticate({ token, challenge, tabId: 7, revision: 2 }), true);
  assert.equal(h.binding.authenticate({ token, challenge, tabId: 8, revision: 2 }), false);
  assert.equal(h.tokenByTab.has(8), false);
  h.binding.close();
});

test("challenge denied before native policy acknowledgement retries the same revision", async () => {
  const h = harness();
  h.binding.policyChanged(policy);
  const pending = h.binding.waitForPolicy(7);
  await new Promise((resolve) => setImmediate(resolve));
  const firstAttempts = h.updates.filter((entry) => entry.id === 7).length;
  assert.ok(firstAttempts > 0);
  const retry = Array.from(h.timers.values()).find((timer) => timer.delay === 100);
  assert.ok(retry);
  retry.action();
  await new Promise((resolve) => setImmediate(resolve));
  assert.ok(h.updates.filter((entry) => entry.id === 7).length > firstAttempts);
  assert.equal(h.binding.authenticate({ token, challenge, tabId: 7, revision: 2 }), true);
  assert.equal(await pending, true);
  h.binding.close();
});

test("removed unbound tab cancels its request while another native binding stays pending", async () => {
  const h = harness();
  h.binding.policyChanged(policy);
  const removed = h.binding.waitForPolicy(7);
  let otherSettled = false;
  const other = h.binding.waitForPolicy(8).then((allowed) => {
    otherSettled = true;
    return allowed;
  });
  h.binding.removeTab(7);
  assert.equal(await removed, false);
  assert.equal(otherSettled, false);
  h.binding.close();
  assert.equal(await other, false);
});

test("completed main-frame responses release request metadata after native disconnect", () => {
  let headersReceived;
  const requests = new Map();
  const context = vm.createContext({
    browser: { webRequest: { onHeadersReceived: { addListener(callback) { headersReceived = callback; } } } },
    mainFrameRequestsById: requests,
    nativePort: null,
  });
  vm.runInContext(background.slice(
    background.indexOf("browser.webRequest.onHeadersReceived.addListener("),
    background.indexOf("browser.webRequest.onErrorOccurred.addListener("),
  ), context);
  for (let cycle = 0; cycle < 100; cycle++) {
    const requestId = String(cycle);
    requests.set(requestId, { token, revision: 2, navigationGeneration: cycle });
    headersReceived({ type: "main_frame", requestId, statusCode: 200 });
    assert.equal(requests.size, 0, `Completed request retained after disconnect ${cycle}`);
  }
});
