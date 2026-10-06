import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const asset = (name) => fs.readFileSync(
  new URL(`../app/src/gecko/assets/candy_privacy/${name}`, import.meta.url),
  "utf8",
);

function inputHarness({ readyState = "loading", registrationFails = false, documentFields = {},
    userActivation = { hasBeenActive: false } } = {}) {
  const listeners = new Map();
  let queryListener;
  const context = vm.createContext({
    browser: { runtime: { onMessage: { addListener: (listener) => { queryListener = listener; } } } },
    crypto: { getRandomValues: (bytes) => bytes.fill(1) },
    navigator: { userActivation },
    document: {
      readyState,
      ...documentFields,
      addEventListener(type, listener, capture) {
        assert.equal(capture, true);
        if (registrationFails) throw new Error("Listener unavailable");
        listeners.set(type, listener);
      },
    },
  });
  vm.runInContext(asset("user_input.js"), context);
  return {
    context, listeners, query: (message) => queryListener(message),
    sample(expectedDocumentNonce) {
      return JSON.parse(JSON.stringify(context.CandyUserInput.sample("query", expectedDocumentNonce)));
    },
  };
}

test("document-start tracker installs before policy in every frame", () => {
  const manifest = JSON.parse(asset("manifest.json"));
  const registration = manifest.content_scripts.find((entry) => entry.js.includes("user_input.js"));
  assert.equal(registration.run_at, "document_start");
  assert.equal(registration.all_frames, true);
  assert.equal(registration.js[0], "user_input.js");
  assert.deepEqual(registration.js, ["user_input.js"]);
  assert.equal(registration.match_origin_as_fallback, true);
  const privacyRegistration = manifest.content_scripts.find((entry) => entry.js.includes("content.js"));
  assert.equal(privacyRegistration.match_origin_as_fallback, undefined);
  assert.equal(privacyRegistration.match_about_blank, undefined);
  assert.ok(manifest.permissions.includes("webNavigation"));
  const harness = inputHarness();
  assert.deepEqual(Array.from(harness.listeners.keys()), ["beforeinput", "input", "change"]);
  assert.deepEqual(harness.sample(), {
    nonce: "query", documentNonce: "01".repeat(16), hasUserInput: false,
  });
});

test("trusted input change and beforeinput stay sticky without reading values or shadow targets", () => {
  for (const type of ["beforeinput", "input", "change"]) {
    const harness = inputHarness();
    const event = {
      isTrusted: true,
      get target() { throw new Error("Retargeted shadow target must not be read"); },
      get data() { throw new Error("Input contents must not be read"); },
    };
    harness.listeners.get(type)(event);
    assert.equal(harness.sample().hasUserInput, true);
    harness.listeners.get("input")({ isTrusted: false });
    assert.equal(harness.sample().hasUserInput, true);
    assert.equal(inputHarness().sample().hasUserInput, false);
  }
  assert.doesNotMatch(asset("user_input.js"), /\.(?:value|target|data|textContent|innerHTML)\b|localStorage|sessionStorage/);
});

test("synthetic events and nonboolean trust never protect a clean document", () => {
  const harness = inputHarness();
  for (const isTrusted of [false, undefined, "true", 1]) {
    harness.listeners.get("input")({ isTrusted });
    assert.equal(harness.sample().hasUserInput, false);
  }
});

test("late or incomplete registration and stale document queries return unknown", () => {
  assert.equal(inputHarness({ registrationFails: true }).sample().hasUserInput, null);
  const late = inputHarness({ readyState: "complete" });
  assert.equal(late.sample().hasUserInput, null);
  late.listeners.get("input")({ isTrusted: true });
  assert.equal(late.sample().hasUserInput, true);
  assert.equal(inputHarness().sample("02".repeat(16)).hasUserInput, null);
});

function emptyBlankDocument() {
  const element = (localName) => ({
    localName, childNodes: [], isContentEditable: false, openOrClosedShadowRoot: null,
  });
  const head = element("head");
  const body = element("body");
  return {
    URL: "about:blank", designMode: "off", head, body,
    documentElement: { ...element("html"), childNodes: [head, body] },
  };
}

test("late untouched empty blank frame starts clean and later trusted input stays protected", () => {
  for (const readyState of ["interactive", "complete"]) {
    const harness = inputHarness({ readyState, documentFields: emptyBlankDocument() });
    assert.equal(harness.sample().hasUserInput, false);
    harness.listeners.get("input")({ isTrusted: true });
    assert.equal(harness.sample().hasUserInput, true);
    assert.equal(harness.sample("02".repeat(16)).hasUserInput, null);
    assert.equal(harness.sample().hasUserInput, true);
  }
});

test("late populated editable or shadow documents are never initialized clean", () => {
  for (const mutate of [
    (document) => { document.URL = "https://top.example/"; },
    (document) => { document.designMode = "on"; },
    (document) => { document.head.childNodes.push({}); },
    (document) => { document.body.childNodes.push({}); },
    (document) => { document.documentElement.childNodes.reverse(); },
    (document) => { document.body.localName = "frameset"; },
    (document) => { document.body = null; },
    ...["documentElement", "head", "body"].flatMap((key) => [
      (document) => { document[key].isContentEditable = true; },
      (document) => { document[key].openOrClosedShadowRoot = {}; },
      (document) => { delete document[key].openOrClosedShadowRoot; },
      (document) => { Object.defineProperty(document[key], "openOrClosedShadowRoot", {
        get() { throw new Error("Unavailable shadow access"); },
      }); },
    ]),
  ]) {
    const documentFields = emptyBlankDocument();
    mutate(documentFields);
    const harness = inputHarness({ readyState: "complete", documentFields });
    assert.equal(harness.sample().hasUserInput, null);
    harness.listeners.get("input")({ isTrusted: true });
    assert.equal(harness.sample().hasUserInput, true);
  }
});

test("prior or unavailable interaction state keeps late blank frame unknown", () => {
  for (const userActivation of [undefined, null, {}, { hasBeenActive: true }, { hasBeenActive: "false" }]) {
    const harness = inputHarness({
      readyState: "complete", documentFields: emptyBlankDocument(),
      userActivation: userActivation === undefined ? {} : userActivation,
    });
    assert.equal(harness.sample().hasUserInput, null);
  }
});

test("late populated frame cannot become clean by removing its contents after installation", () => {
  const documentFields = emptyBlankDocument();
  documentFields.body.childNodes.push({});
  const harness = inputHarness({ readyState: "complete", documentFields });
  documentFields.body.childNodes.length = 0;
  assert.equal(harness.sample().hasUserInput, null);
});

const request = {
  token: "token", revision: 3, navigationGeneration: 2, requestId: 1,
  nonce: "01234567-0123-0123-0123-0123456789ab",
};
const policy = { revision: 3, navigationGeneration: 2 };
const cleanFrames = [
  { frameId: 0, parentFrameId: -1, url: "https://top.example/", errorOccurred: false },
  { frameId: 7, parentFrameId: 0, url: "https://frame.example/", errorOccurred: false },
];

function backgroundHarness({ frames = cleanFrames, finalFrames = frames, result, frameError = false } = {}) {
  const posted = [];
  const sent = [];
  let frameReads = 0;
  const sampleCounts = new Map();
  const context = vm.createContext({
    policiesByToken: new Map([["token", policy]]), tokenByTab: new Map([[9, "token"]]),
    nativePort: { postMessage: (message) => posted.push(message) }, PROTOCOL_VERSION: 2,
    browser: {
      webNavigation: { getAllFrames: async (details) => {
        assert.deepEqual(JSON.parse(JSON.stringify(details)), { tabId: 9 });
        if (frameError) throw new Error("Frame inventory unavailable");
        return ++frameReads === 1 ? frames : finalFrames;
      } },
      tabs: { sendMessage: async (tabId, message, options) => {
        assert.equal(tabId, 9);
        sent.push([tabId, message, options]);
        const count = (sampleCounts.get(options.frameId) || 0) + 1;
        sampleCounts.set(options.frameId, count);
        return result ? result(options.frameId, count, message, context) : {
          nonce: message.nonce, documentNonce: "01".repeat(16), hasUserInput: false,
        };
      } },
    },
  });
  const source = asset("background.js").split("async function queryUserInput(message) {")[1]
    .split("function probeDom(message) {")[0];
  vm.runInContext(`async function queryUserInput(message) {${source}`, context);
  return { context, posted, sent, query: (value = request) => context.queryUserInput(value) };
}

test("clean result requires both document-bound samples from every current frame", async () => {
  const harness = backgroundHarness();
  await harness.query();
  assert.equal(harness.sent.length, 4);
  assert.deepEqual(harness.sent.map((entry) => entry[2].frameId), [0, 7, 0, 7]);
  assert.deepEqual(JSON.parse(JSON.stringify(harness.sent[0][1])), {
    type: "user-input-query", nonce: request.nonce,
  });
  assert.equal(harness.sent[2][1].documentNonce, "01".repeat(16));
  assert.deepEqual(JSON.parse(JSON.stringify(harness.posted)), [{
    type: "user-input-result", protocolVersion: 2, ...request, hasUserInput: false,
  }]);
});

test("Gecko 157 frame inventory omits unsupported errorOccurred but still requires document answers", async () => {
  const frames = cleanFrames.map(({ errorOccurred: _unsupported, ...frame }) => frame);
  const harness = backgroundHarness({ frames });
  await harness.query();
  assert.equal(harness.posted[0].hasUserInput, false);
  const unreachable = backgroundHarness({ frames, result: () => undefined });
  await unreachable.query();
  assert.equal(unreachable.posted[0].hasUserInput, null);
});

test("trusted input in any cross-origin frame protects tab even when another frame is unknown", async () => {
  const harness = backgroundHarness({ result: (frameId, _count, message) => frameId === 0 ? null : {
    nonce: message.nonce, documentNonce: "01".repeat(16), hasUserInput: true,
  } });
  await harness.query();
  assert.equal(harness.posted[0].hasUserInput, true);
});

test("missing malformed rejected or replayed frame answers are unknown never clean", async () => {
  for (const result of [
    () => undefined,
    () => { throw new Error("Unreachable frame"); },
    () => ({ nonce: request.nonce, documentNonce: "01".repeat(16), hasUserInput: "false" }),
    () => ({ nonce: request.nonce, documentNonce: "01".repeat(16), hasUserInput: null }),
    () => ({ nonce: request.nonce, documentNonce: "bad", hasUserInput: false }),
    () => ({ nonce: "stale", documentNonce: "01".repeat(16), hasUserInput: false }),
  ]) {
    const harness = backgroundHarness({ result });
    await harness.query();
    assert.equal(harness.posted[0].hasUserInput, null);
  }
});

test("same URL iframe document replacement between samples is unknown", async () => {
  const harness = backgroundHarness({ result: (_frameId, count, message) => ({
    nonce: message.nonce, documentNonce: (count === 1 ? "01" : "02").repeat(16), hasUserInput: false,
  }) });
  await harness.query();
  assert.equal(harness.posted[0].hasUserInput, null);
});

test("frame errors missing inventory and new frames cannot permit eviction", async () => {
  for (const frames of [null, [], [cleanFrames[1]], [{ ...cleanFrames[0], errorOccurred: true }],
    [{ ...cleanFrames[0], errorOccurred: null }], [cleanFrames[0], cleanFrames[0]]]) {
    const harness = backgroundHarness({ frames });
    await harness.query();
    assert.equal(harness.posted[0].hasUserInput, null);
    assert.equal(harness.sent.length, 0);
  }
  for (const options of [{ frameError: true }, { finalFrames: [cleanFrames[0]] },
    { finalFrames: [...cleanFrames, { ...cleanFrames[1], frameId: 8 }] },
    { finalFrames: [{ ...cleanFrames[0], url: "https://new.example/" }, cleanFrames[1]] }]) {
    const harness = backgroundHarness(options);
    await harness.query();
    assert.equal(harness.posted[0].hasUserInput, null);
  }
});

test("navigation policy replacement and removed bindings discard queued aggregate", async () => {
  for (const invalidate of [
    (context) => context.policiesByToken.set("token", { ...policy, revision: 4 }),
    (context) => context.policiesByToken.set("token", { ...policy, navigationGeneration: 3 }),
    (context) => context.tokenByTab.clear(),
  ]) {
    const harness = backgroundHarness({ result: (_frameId, _count, message, context) => {
      invalidate(context);
      return { nonce: message.nonce, documentNonce: "01".repeat(16), hasUserInput: false };
    } });
    await harness.query();
    assert.equal(harness.posted.length, 0);
  }
});

test("invalid native identity is never forwarded to documents", async () => {
  for (const changed of [{ requestId: 0 }, { requestId: 1.5 }, { nonce: "bad" },
    { revision: 2 }, { navigationGeneration: 1 }]) {
    const harness = backgroundHarness();
    await harness.query({ ...request, ...changed });
    assert.equal(harness.sent.length, 0);
    assert.equal(harness.posted.length, 0);
  }
});

test("standalone probe answers before policy and carries only anonymous protection state", async () => {
  const harness = inputHarness();
  const result = await harness.query({ type: "user-input-query", nonce: request.nonce });
  assert.equal(result.hasUserInput, false);
  assert.deepEqual(Object.keys(result), ["nonce", "documentNonce", "hasUserInput"]);
  harness.listeners.get("beforeinput")({ isTrusted: true });
  assert.equal((await harness.query({ type: "user-input-query", nonce: request.nonce })).hasUserInput, true);
  assert.equal(harness.query({ type: "content-policy", nonce: request.nonce }), undefined);
  assert.equal(harness.query({ type: "user-input-query", nonce: 1 }), undefined);
  assert.doesNotMatch(asset("content.js"), /user-input-query/);
});
