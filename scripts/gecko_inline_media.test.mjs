import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import vm from "node:vm";

const asset = (name) => readFileSync(
  new URL(`../app/src/gecko/assets/candy_privacy/${name}`, import.meta.url),
  "utf8",
);

function candidateHarness() {
  const posted = [];
  const context = vm.createContext({
    inlineVideosByTab: new Map(),
    tokenByTab: new Map([[7, "token"]]),
    policiesByToken: new Map([[
      "token",
      { revision: 3, navigationGeneration: 2, inlineMediaPlayerEnabled: true },
    ]]),
    nativePort: { postMessage: (message) => posted.push(message) },
    PROTOCOL_VERSION: 2,
  });
  const source = asset("background.js")
    .split("function publishInlineVideoState(tabId) {")[1]
    .split("function scheduleContentPolicy(tabId) {")[0];
  vm.runInContext(`function publishInlineVideoState(tabId) {${source}`, context);
  return { context, posted };
}

const candidate = {
  type: "inline-video-state",
  revision: 3,
  navigationGeneration: 2,
  active: true,
  playing: true,
  videoWidth: 1280,
  videoHeight: 720,
  area: 921600,
  documentNonce: "a".repeat(32),
  elementNonce: "b".repeat(32),
};

test("inline candidate accepts only bounded top-frame video state", () => {
  const harness = candidateHarness();
  harness.context.updateInlineVideoState(candidate, { tab: { id: 7 }, frameId: 4 });
  assert.equal(harness.posted.length, 0);

  harness.context.updateInlineVideoState(candidate, { tab: { id: 7 }, frameId: 0 });
  assert.equal(harness.posted.length, 1);
  assert.deepEqual(
    JSON.parse(JSON.stringify(harness.posted[0])),
    {
      type: "inline-video-state",
      protocolVersion: 2,
      token: "token",
      revision: 3,
      navigationGeneration: 2,
      active: true,
      playing: true,
      videoWidth: 1280,
      videoHeight: 720,
      documentNonce: "a".repeat(32),
      elementNonce: "b".repeat(32),
    },
  );

  harness.context.updateInlineVideoState(
    { ...candidate, active: false, videoWidth: 0, videoHeight: 0, area: 0 },
    { tab: { id: 7 }, frameId: 0 },
  );
  assert.equal(harness.posted[1].active, false);
});

test("inline candidate rejects malformed identity and dimensions", () => {
  for (const invalid of [
    { ...candidate, videoWidth: 16385 },
    { ...candidate, videoHeight: -1 },
    { ...candidate, area: Number.MAX_SAFE_INTEGER + 1 },
    { ...candidate, area: 4095 },
    { ...candidate, documentNonce: "author-data" },
    { ...candidate, elementNonce: "c".repeat(31) },
  ]) {
    const harness = candidateHarness();
    harness.context.updateInlineVideoState(invalid, { tab: { id: 7 }, frameId: 0 });
    assert.equal(harness.posted.length, 0);
  }
});

test("inline candidate rejects stale policy identity", () => {
  for (const stale of [
    { ...candidate, revision: 2 },
    { ...candidate, navigationGeneration: 1 },
  ]) {
    const harness = candidateHarness();
    harness.context.updateInlineVideoState(stale, { tab: { id: 7 }, frameId: 0 });
    assert.equal(harness.posted.length, 0);
  }
});

test("disabled inline player rejects and clears candidates", () => {
  const harness = candidateHarness();
  harness.context.updateInlineVideoState(candidate, { tab: { id: 7 }, frameId: 0 });
  harness.context.policiesByToken.get("token").inlineMediaPlayerEnabled = false;
  harness.context.updateInlineVideoState(candidate, { tab: { id: 7 }, frameId: 0 });

  assert.equal(harness.posted.length, 2);
  assert.equal(harness.posted[1].active, false);
});

test("repeated enabled policy reconciles inline state with new navigation identity", () => {
  let reports = 0;
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      candidates: new Set(),
      inlineMediaPlayerEnabled: false,
      inlinePresentationExpected: false,
      inlineMediaPolicyRevision: 0,
      inlineMediaNavigationGeneration: 0,
      inlineStateFrame: null,
      inlineStateObserver: null,
    },
    self: {},
    top: null,
    document: {},
    MutationObserver: class {
      disconnect() {}
      observe() {}
    },
    requestAnimationFrame: (callback) => {
      callback();
      return null;
    },
    cancelAnimationFrame: () => {},
    setTimeout: (callback) => callback(),
    rememberCandyPictureInPictureVideos: () => {},
    reportCandyInlineVideoState: () => { reports += 1; },
    clearCandyPictureInPicturePresentation: () => {},
    clearCandyInlineVideoState: () => {},
  });
  context.top = context.self;
  const source = asset("content.js")
    .split("function scheduleCandyInlineVideoStateReport() {")[1]
    .split("function scheduleCandyPictureInPictureAlignment() {")[0];
  vm.runInContext(`function scheduleCandyInlineVideoStateReport() {${source}`, context);

  context.updateCandyInlineMediaPlayerEnabled(true, 3, 2);
  context.updateCandyInlineMediaPlayerEnabled(true, 4, 3);

  assert.equal(context.candyPictureInPicturePlayback.inlineMediaPolicyRevision, 4);
  assert.equal(context.candyPictureInPicturePlayback.inlineMediaNavigationGeneration, 3);
  assert.ok(reports >= 2);
});

test("content presentation requires exact document and element identity", () => {
  const source = asset("content.js");
  assert.match(source, /message\.documentNonce !== candyInlineVideoDocumentNonce/);
  assert.match(source, /message\.elementNonce !== candyInlineVideoElementNonce\(video\)/);
  assert.match(source, /inlinePresentationExpected/);
  assert.match(source, /inlineMediaPlayerEnabled/);
  assert.match(source, /bounds\.width \* bounds\.height >= 4096/);
  assert.match(source, /childList: true/);
  assert.match(source, /function startCandyInlineVideoStateObservation\(\)/);
  assert.match(source, /inlineStateObserver\.observe\(document/);
  assert.match(source, /window\.addEventListener\("pageshow"/);
  assert.match(source, /window\.addEventListener\("scroll", scheduleCandyInlineVideoStateReport/);
  assert.match(source, /if \(!video\.isConnected \|\| video\.ended\)/);
  assert.match(source, /candyInlineVideoOriginalControls\.set\(video, video\.controls\)/);
  assert.match(source, /video\.controls = true/);
  assert.match(source, /video\.controls = candyInlineVideoOriginalControls\.get\(video\)/);
  assert.match(source, /reconcileCandyInlineVideoState\(\)/);
  assert.match(source, /return \{ accepted: candyPictureInPicturePlayback\.presentedVideo === video \}/);
});
