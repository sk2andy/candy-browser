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
  presented: false,
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
      presented: false,
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

test("inline open request requires top frame current policy and exact candidate", () => {
  const harness = candidateHarness();
  const sender = { tab: { id: 7 }, frameId: 0 };
  harness.context.updateInlineVideoState(candidate, sender);
  const request = {
    type: "inline-video-open-request",
    revision: 3,
    navigationGeneration: 2,
    documentNonce: candidate.documentNonce,
    elementNonce: candidate.elementNonce,
  };

  assert.equal(harness.context.requestInlineVideoOpen(request, sender), true);
  assert.deepEqual(
    JSON.parse(JSON.stringify(harness.posted[1])),
    {
      type: "inline-video-open-request",
      protocolVersion: 2,
      token: "token",
      revision: 3,
      navigationGeneration: 2,
      documentNonce: candidate.documentNonce,
      elementNonce: candidate.elementNonce,
      expected: true,
    },
  );
  assert.equal(
    harness.context.requestInlineVideoOpen({ ...request, expected: false }, sender),
    true,
  );
  assert.equal(harness.posted[2].expected, false);

  for (const [invalid, invalidSender] of [
    [{ ...request, revision: 2 }, sender],
    [{ ...request, navigationGeneration: 1 }, sender],
    [{ ...request, elementNonce: "c".repeat(32) }, sender],
    [request, { tab: { id: 7 }, frameId: 2 }],
  ]) {
    assert.equal(harness.context.requestInlineVideoOpen(invalid, invalidSender), false);
  }
  assert.equal(harness.posted.length, 3);
});

test("picture in picture preparation stays bound to current policy and video identity", async () => {
  const nativeMessages = [];
  const contentMessages = [];
  const identity = {
    documentNonce: "a".repeat(32),
    elementNonce: "b".repeat(32),
  };
  const context = vm.createContext({
    policiesByToken: new Map([[
      "token",
      { revision: 3, navigationGeneration: 2 },
    ]]),
    tokenByTab: new Map([[7, "token"]]),
    nativePort: { postMessage: (message) => nativeMessages.push(message) },
    browser: {
      tabs: {
        sendMessage: (tabId, message, options) => {
          contentMessages.push({ tabId, message, options });
          return Promise.resolve({
            prepared: true,
            ...identity,
            videoLeft: 0,
            videoTop: 0,
            videoRight: 400,
            videoBottom: 225,
            viewportWidth: 400,
            viewportHeight: 225,
          });
        },
      },
    },
    PROTOCOL_VERSION: 2,
  });
  const source = asset("background.js")
    .split("function updatePictureInPicturePlayback(message) {")[1]
    .split("function updateInlineVideoPresentation(message) {")[0];
  vm.runInContext(`function updatePictureInPicturePlayback(message) {${source}`, context);
  const request = {
    token: "token",
    revision: 3,
    navigationGeneration: 2,
    requestId: 17,
    expected: true,
    ...identity,
  };

  context.updatePictureInPicturePlayback(request);
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(contentMessages.length, 1);
  assert.deepEqual(
    JSON.parse(JSON.stringify(contentMessages[0].options)),
    { frameId: 0 },
  );
  assert.equal(contentMessages[0].message.requestId, 17);
  assert.equal(nativeMessages.length, 1);
  assert.equal(nativeMessages[0].type, "picture-in-picture-playback-result");
  assert.equal(nativeMessages[0].prepared, true);
  context.updatePictureInPicturePlayback({ ...request, navigationGeneration: 1 });
  context.updatePictureInPicturePlayback({ ...request, elementNonce: "invalid" });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(contentMessages.length, 1);
  assert.equal(nativeMessages.length, 1);
});

test("repeated enabled policy reconciles inline state with new navigation identity", () => {
  let reports = 0;
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      candidates: new Set(),
      inlineMediaPlayerEnabled: false,
      inlineMediaPlayerMode: "button_fullscreen",
      inlineOpenRequestTimer: null,
      inlineOpenRequestKey: null,
      inlinePresentationExpected: false,
      inlineMediaPolicyRevision: 0,
      inlineMediaNavigationGeneration: 0,
      inlineMediaPlayerActionLabel: "Open in Candy Player",
      inlineMediaPlayerPlayLabel: "Play",
      inlineMediaPlayerPauseLabel: "Pause",
      inlineMediaPlayerSeekLabel: "Seek",
      presentedVideo: null,
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
    removeCandyInlineVideoAction: () => {},
    removeCandyInlineVideoControlsOverlay: () => {},
    updateCandyInlineVideoControlsOverlay: () => {},
    clearCandyInlineVideoOpenRequest: () => {},
    CANDY_INLINE_MEDIA_PLAYER_MODES: new Set([
      "button_fullscreen",
      "button_inline_and_fullscreen",
      "always_for_fullscreen",
      "automatic",
    ]),
  });
  context.top = context.self;
  const source = asset("content.js")
    .split("function scheduleCandyInlineVideoStateReport() {")[1]
    .split("function scheduleCandyPictureInPictureAlignment() {")[0];
  vm.runInContext(`function scheduleCandyInlineVideoStateReport() {${source}`, context);

  context.updateCandyInlineMediaPlayerEnabled(true, "button_fullscreen", 3, 2);
  context.updateCandyInlineMediaPlayerEnabled(true, "automatic", 4, 3);

  assert.equal(context.candyPictureInPicturePlayback.inlineMediaPolicyRevision, 4);
  assert.equal(context.candyPictureInPicturePlayback.inlineMediaNavigationGeneration, 3);
  assert.ok(reports >= 2);
});

test("inline presentation pins the clicked video across competing playback", () => {
  const clicked = { connected: true };
  const competing = { connected: true };
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      inlinePresentationExpected: true,
      presentedVideo: clicked,
    },
    isCandyInlineVideoCandidate: (video) => video?.connected === true,
    currentCandyPictureInPictureVideo: () => competing,
  });
  const source = asset("content.js")
    .split("function candyPictureInPictureVideoToPresent(preferredVideo = null) {")[1]
    .split("function presentCandyPictureInPictureVideo(preferredVideo = null) {")[0];
  vm.runInContext(
    `function candyPictureInPictureVideoToPresent(preferredVideo = null) {${source}`,
    context,
  );

  assert.equal(context.candyPictureInPictureVideoToPresent(), clicked);
  clicked.connected = false;
  assert.equal(context.candyPictureInPictureVideoToPresent(), null);
  context.candyPictureInPicturePlayback.inlinePresentationExpected = false;
  assert.equal(context.candyPictureInPictureVideoToPresent(), competing);
});

test("expressive progress path stays bounded and becomes wobbly", () => {
  const context = vm.createContext({});
  const source = asset("content.js")
    .split("function candyWobblyProgressPath(value) {")[1]
    .split("function candyInlineVideoSitePlayer(video) {")[0];
  vm.runInContext(`function candyWobblyProgressPath(value) {${source}`, context);

  assert.equal(context.candyWobblyProgressPath(-20), "M 0 16");
  assert.equal(context.candyWobblyProgressPath(0), "M 0 16");
  assert.match(context.candyWobblyProgressPath(500), /^M 0 16 L /);
  assert.match(context.candyWobblyProgressPath(500), /500\.00 /);
  assert.match(context.candyWobblyProgressPath(500), /1[2-9]\.\d{2}/);
  assert.match(context.candyWobblyProgressPath(5_000), /1000\.00 /);
});

test("content presentation requires exact document and element identity", () => {
  const source = asset("content.js");
  assert.match(source, /documentNonce !== candyInlineVideoDocumentNonce/);
  assert.match(source, /candyInlineVideoElementNonce\(video\) === elementNonce/);
  assert.match(source, /inlinePresentationExpected/);
  assert.match(source, /inlineMediaPlayerEnabled/);
  assert.match(source, /visibleWidth >= minimumVisibleSize/);
  assert.match(source, /visibleHeight >= minimumVisibleSize/);
  assert.match(source, /childList: true/);
  assert.match(source, /function startCandyInlineVideoStateObservation\(\)/);
  assert.match(source, /inlineStateObserver\.observe\(document/);
  assert.match(source, /window\.addEventListener\("pageshow"/);
  assert.match(source, /window\.addEventListener\("scroll", scheduleCandyInlineVideoStateReport/);
  assert.match(source, /if \(!video \|\| !video\.isConnected \|\| video\.ended\)/);
  assert.match(source, /candyInlineVideoOriginalControls\.set\(video, video\.controls\)/);
  assert.match(source, /video\.controls = false/);
  assert.match(source, /video\.controls = candyInlineVideoOriginalControls\.get\(video\)/);
  assert.match(source, /reconcileCandyInlineVideoState\(\)/);
  assert.match(source, /attachShadow\(\{ mode: "closed" \}\)/);
  assert.match(source, /!event\.isTrusted/);
  assert.match(source, /if \(video\.paused\) Promise\.resolve\(video\.play\(\)\)/);
  assert.match(source, /await reportCandyInlineVideoState\(video\)/);
  assert.match(source, /response\?\.forwarded !== true/);
  assert.match(source, /inlineMediaPlayerMode === "button_fullscreen"/);
  assert.match(source, /inlineMediaPlayerMode === "always_for_fullscreen"/);
  assert.match(source, /inlineMediaPlayerMode === "automatic"/);
  assert.match(source, /await nextCandyAnimationFrame\(\)/);
  assert.match(source, /isCandyInlineVideoActionClick\(event, video, host\)/);
  assert.match(source, /inlineActionLayoutObserver\.observe\(element/);
  assert.match(source, /attributeFilter: \["class", "hidden", "style"\]/);
  assert.match(source, /inlineActionResizeObserver\.observe\(video\)/);
  assert.match(source, /inlinePresentationLayoutObserver\.observe\(element/);
  assert.match(source, /inlinePresentationResizeObserver\.observe\(video\)/);
  assert.match(source, /data-candy-inline-video-controls/);
  assert.match(source, /border-radius: 50%/);
  assert.match(source, /button \{[\s\S]*?border: 0;[\s\S]*?outline: none;/);
  assert.match(source, /button:focus-visible \{ outline: 3px solid white; outline-offset: 3px; \}/);
  assert.doesNotMatch(source, /rgba\(255,255,255,\.24\) inset/);
  assert.match(source, /candy-hero-launch/);
  assert.match(source, /heroPlay\.dataset\.activating = "true"/);
  assert.match(source, /heroActivationTimer = setTimeout\(finishHeroActivation, 700\)/);
  assert.match(source, /\.hero-play\[data-activating="true"\][^{]*\{\s*animation: none;/s);
  assert.match(source, /padding: 0 16px 14px/);
  assert.match(source, /transparent 4px 8px/);
  const actionSource = source
    .split("function createCandyInlineVideoAction(video) {")[1]
    .split("function candyInlineVideoActionPosition(video) {")[0];
  assert.match(actionSource, /border-radius: 17px/);
  assert.match(actionSource, /border: 0;/);
  assert.doesNotMatch(actionSource, /border: 1px solid/);
  assert.doesNotMatch(actionSource, /rgba\(255,255,255,\.14\) inset/);
  assert.doesNotMatch(actionSource, /candy-action-float|candy-action-morph/);
  assert.doesNotMatch(actionSource, /\["contain", "strict"\]/);
  assert.match(source, /candyWobblyProgressPath\(seekValue\)/);
  assert.match(source, /data-candy-inline-video-site-player/);
  assert.match(source, /#movie_player, \.html5-video-player, ytm-player/);
  assert.match(source, /\.ytp-chrome-bottom/);
  assert.match(source, /clearCandyInlineVideoSiteControls\(\)/);
  assert.match(source, /createCandyInlineVideoControlsOverlay\(video\)/);
  assert.match(source, /if \(video\.paused \|\| video\.ended\)/);
  assert.match(source, /else video\.pause\(\)/);
  assert.match(source, /video\.currentTime = Number\(seek\.value\)/);
  assert.match(source, /target\?\.requestFullscreen\(\)/);
  assert.match(source, /document\.exitFullscreen\(\)/);
  assert.match(source, /candyInlineVideoControlsParent\(video\)/);
  assert.match(source, /parent\.appendChild\(candyPictureInPicturePlayback\.inlineControlsHost\)/);
  assert.match(source, /removeCandyInlineVideoControlsOverlay\(\)/);
  assert.match(
    source,
    /!isCandyInlineVideoCandidate\(candyPictureInPicturePlayback\.presentedVideo\)/,
  );
  assert.match(source, /type: "inline-video-open-request"/);
  assert.match(source, /function requestCandyInlineVideoClose\(video\)/);
  assert.match(source, /expected: false/);
  assert.match(source, /utility\.append\(fullscreen, close\)/);
  assert.match(source, /candyInlineVideoForIdentity/);
  assert.match(source, /presentCandyInlineVideo\(video\)/);
  assert.match(source, /presented: Boolean\(/);
  assert.match(
    source,
    /inlinePresentationExpected \?[\s\S]*presentedVideo : null[\s\S]*currentCandyPictureInPictureVideo\(\)/,
  );
  const inlinePresentationSource = source
    .split("function updateCandyInlineVideoPresentation(message) {")[1]
    .split('document.addEventListener("play"')[0];
  assert.match(inlinePresentationSource, /presentCandyInlineVideo\(video\)/);
  assert.doesNotMatch(inlinePresentationSource, /presentCandyPictureInPictureVideo/);
  const pictureInPictureSource = source
    .split("function presentCandyPictureInPictureVideo(preferredVideo = null) {")[1]
    .split("function rememberCandyPictureInPictureVideos() {")[0];
  assert.match(
    pictureInPictureSource,
    /!candyPictureInPicturePlayback\.expected/,
  );
  const synchronousAlignment = pictureInPictureSource.lastIndexOf(
    "alignCandyPictureInPictureVideo(video);",
  );
  const scheduledAlignment = pictureInPictureSource.lastIndexOf(
    "scheduleCandyPictureInPictureAlignment();",
  );
  assert.ok(synchronousAlignment >= 0);
  assert.ok(scheduledAlignment > synchronousAlignment);
});
