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
    inlineVideoGestureHapticPhases: new Set([
      "rubberband-start",
      "rubberband-stop",
      "confirm",
    ]),
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

test("inline gesture haptics require exact presented top-frame video identity", () => {
  const harness = candidateHarness();
  const sender = { tab: { id: 7 }, frameId: 0 };
  const presented = { ...candidate, presented: true };
  harness.context.updateInlineVideoState(presented, sender);
  const request = {
    type: "inline-video-gesture-haptic",
    revision: 3,
    navigationGeneration: 2,
    documentNonce: candidate.documentNonce,
    elementNonce: candidate.elementNonce,
    phase: "rubberband-start",
  };

  assert.equal(harness.context.forwardInlineVideoGestureHaptic(request, sender), true);
  assert.deepEqual(
    JSON.parse(JSON.stringify(harness.posted[1])),
    {
      type: "inline-video-gesture-haptic",
      protocolVersion: 2,
      token: "token",
      revision: 3,
      navigationGeneration: 2,
      documentNonce: candidate.documentNonce,
      elementNonce: candidate.elementNonce,
      phase: "rubberband-start",
    },
  );

  for (const [invalid, invalidSender] of [
    [{ ...request, revision: 2 }, sender],
    [{ ...request, navigationGeneration: 1 }, sender],
    [{ ...request, elementNonce: "c".repeat(32) }, sender],
    [{ ...request, phase: "raw-pointer-delta" }, sender],
    [request, { tab: { id: 7 }, frameId: 2 }],
  ]) {
    assert.equal(harness.context.forwardInlineVideoGestureHaptic(invalid, invalidSender), false);
  }
  harness.context.updateInlineVideoState(candidate, sender);
  assert.equal(harness.context.forwardInlineVideoGestureHaptic(request, sender), false);
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

  context.updatePictureInPicturePlayback({
    token: "token",
    revision: 3,
    navigationGeneration: 2,
    requestId: 18,
    expected: false,
  });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(contentMessages.length, 2);
  assert.equal(contentMessages[1].message.expected, false);
  assert.equal(contentMessages[1].message.documentNonce, undefined);
  assert.equal(nativeMessages.length, 2);
  assert.equal(nativeMessages[1].requestId, 18);
  assert.equal(nativeMessages[1].prepared, true);
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

test("inline fullscreen swipe stays sticky before a bounded upward threshold", () => {
  const context = vm.createContext({
    CANDY_INLINE_FULLSCREEN_GESTURE_MIN_THRESHOLD_PX: 48,
    CANDY_INLINE_FULLSCREEN_GESTURE_MAX_THRESHOLD_PX: 96,
    CANDY_INLINE_FULLSCREEN_GESTURE_THRESHOLD_FRACTION: 0.13,
    CANDY_INLINE_FULLSCREEN_GESTURE_STICKY_FRACTION: 0.18,
  });
  const source = asset("content.js")
    .split("function candyInlineFullscreenGestureDirection(deltaX, deltaY, touchSlop) {")[1]
    .split("function candyInlineMediaPlayerShowsButton() {")[0];
  vm.runInContext(
    `function candyInlineFullscreenGestureDirection(deltaX, deltaY, touchSlop) {${source}`,
    context,
  );

  assert.equal(context.candyInlineFullscreenGestureDirection(0, -4, 10), "pending");
  assert.equal(context.candyInlineFullscreenGestureDirection(20, -20, 10), "rejected");
  assert.equal(context.candyInlineFullscreenGestureDirection(0, 20, 10), "rejected");
  assert.equal(context.candyInlineFullscreenGestureDirection(2, -20, 10), "up");

  const below = context.candyInlineFullscreenGestureUpdate(40, 400);
  const committed = context.candyInlineFullscreenGestureUpdate(52, 400);
  assert.equal(below.threshold, 52);
  assert.equal(below.shouldCommit, false);
  assert.ok(below.offset < 52 * 0.18);
  assert.equal(committed.shouldCommit, true);
  assert.equal(committed.offset, 52 * 0.18);
  assert.equal(context.candyInlineFullscreenGestureUpdate(500, 10_000).threshold, 96);
  assert.equal(context.candyInlineFullscreenGestureUpdate(10, 0).shouldCommit, false);

  const pointerUpSource = asset("content.js")
    .split('fullscreenGesture.addEventListener("pointerup", (event) => {')[1]
    .split('fullscreenGesture.addEventListener("pointercancel"')[0];
  assert.ok(
    pointerUpSource.indexOf("requestCandyInlineVideoFullscreen(video)") <
      pointerUpSource.indexOf('reportCandyInlineVideoGestureHaptic(video, "confirm")'),
    "fullscreen request must stay ahead of asynchronous bridge work in trusted pointerup",
  );
  assert.ok(
    pointerUpSource.indexOf('reportCandyInlineVideoGestureHaptic(video, "rubberband-stop")') <
      pointerUpSource.indexOf('reportCandyInlineVideoGestureHaptic(video, "confirm")'),
    "rubberband vibration must stop before fullscreen confirmation",
  );
});

test("inline fullscreen drag moves video and controls together and restores styles", () => {
  const properties = new Map([["transform", { value: "scale(1)", priority: "" }]]);
  const video = {
    isConnected: true,
    getBoundingClientRect: () => {
      const transform = properties.get("transform")?.value || "";
      const deltaY = Number(transform.match(/translate3d\(0, (-?[\d.]+)px/)?.[1] || 0);
      return { left: 0, right: 400, top: 100 + deltaY, bottom: 300 + deltaY };
    },
    style: {
      getPropertyValue: (name) => properties.get(name)?.value || "",
      getPropertyPriority: (name) => properties.get(name)?.priority || "",
      setProperty: (name, value, priority) => properties.set(name, { value, priority }),
      removeProperty: (name) => properties.delete(name),
    },
  };
  const hostProperties = new Map();
  const host = {
    isConnected: true,
    dataset: {},
    style: {
      setProperty: (name, value) => hostProperties.set(name, value),
    },
  };
  const context = vm.createContext({ Number });
  const source = asset("content.js")
    .split("function setCandyInlineFullscreenGestureOffset(gesture, host, offset) {")[1]
    .split("function removeCandyInlineVideoControlsOverlay() {")[0];
  vm.runInContext(
    `function setCandyInlineFullscreenGestureOffset(gesture, host, offset) {${source}`,
    context,
  );
  context.innerWidth = 400;
  context.innerHeight = 800;
  context.CANDY_INLINE_VIDEO_ACTION_SIZE_PX = 56;
  context.CANDY_INLINE_VIDEO_CONTROLS_HEIGHT_PX = 88;
  context.setCandyInlineActionStyle = (element, name, value) => {
    element.style.setProperty(name, value);
  };
  const positionSource = asset("content.js")
    .split("function positionCandyInlineVideoControls(video, host) {")[1]
    .split("function createCandyInlineVideoControlsOverlay(video) {")[0];
  vm.runInContext(`function positionCandyInlineVideoControls(video, host) {${positionSource}`, context);

  const gesture = {
    video,
    baseTransform: "matrix(1, 0, 0, 1, 0, 0)",
    originalTransform: video.style.getPropertyValue("transform"),
    originalTransformPriority: video.style.getPropertyPriority("transform"),
  };
  context.setCandyInlineFullscreenGestureOffset(gesture, host, 9.36);
  assert.equal(
    properties.get("transform").value,
    "translate3d(0, -9.36px, 0) matrix(1, 0, 0, 1, 0, 0)",
  );
  assert.equal(hostProperties.get("transform"), "translate3d(0, -9.36px, 0)");
  assert.equal(host.dataset.fullscreenGestureOffset, "9.36");
  assert.equal(video.getBoundingClientRect().top, 90.64);
  context.positionCandyInlineVideoControls(video, host);
  assert.equal(hostProperties.get("top"), "100px");

  context.clearCandyInlineFullscreenGestureOffset(gesture, host);
  assert.equal(properties.get("transform").value, "scale(1)");
  assert.equal(hostProperties.get("transform"), "none");
  assert.equal(host.dataset.fullscreenGestureOffset, undefined);
  context.positionCandyInlineVideoControls(video, host);
  assert.equal(hostProperties.get("top"), "100px");
});

test("picture in picture suppresses and restores native video controls", () => {
  const context = vm.createContext({
    candyPictureInPictureOriginalControls: new WeakMap(),
  });
  const source = asset("content.js")
    .split("function updateCandyPictureInPictureVideoControls(video, expected) {")[1]
    .split("function clearCandyPictureInPictureVideoPresentation(video) {")[0];
  vm.runInContext(
    `function updateCandyPictureInPictureVideoControls(video, expected) {${source}`,
    context,
  );
  const video = { controls: true };

  context.updateCandyPictureInPictureVideoControls(video, true);
  assert.equal(video.controls, false);
  video.controls = true;
  context.updateCandyPictureInPictureVideoControls(video, true);
  assert.equal(video.controls, false);
  context.updateCandyPictureInPictureVideoControls(video, false);
  assert.equal(video.controls, true);

  video.controls = false;
  context.updateCandyPictureInPictureVideoControls(video, true);
  context.updateCandyPictureInPictureVideoControls(video, false);
  assert.equal(video.controls, false);
});

test("picture in picture cleanup removes offsets from every marked video", () => {
  const removedAttributes = [];
  const removedProperties = [];
  const staleVideo = {
    removeAttribute: (name) => removedAttributes.push(["stale", name]),
    style: {
      removeProperty: (name) => removedProperties.push(["stale", name]),
    },
  };
  const currentVideo = {
    removeAttribute: (name) => removedAttributes.push(["current", name]),
    style: {
      removeProperty: (name) => removedProperties.push(["current", name]),
    },
  };
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      alignmentFrame: null,
      layoutObserver: null,
      resizeObserver: null,
      inlinePresentationExpected: true,
      presentedVideo: currentVideo,
    },
    CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE: "data-candy-picture-in-picture",
    CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE: "data-candy-picture-in-picture-video",
    CANDY_PICTURE_IN_PICTURE_STYLE_ATTRIBUTE: "data-candy-picture-in-picture-style",
    CANDY_PICTURE_IN_PICTURE_OFFSET_X: "--candy-picture-in-picture-offset-x",
    CANDY_PICTURE_IN_PICTURE_OFFSET_Y: "--candy-picture-in-picture-offset-y",
    clearCandyPictureInPictureVideoPresentation: (video) => {
      video?.removeAttribute("data-candy-picture-in-picture-video");
      video?.style.removeProperty("--candy-picture-in-picture-offset-x");
      video?.style.removeProperty("--candy-picture-in-picture-offset-y");
    },
    cancelAnimationFrame: () => {},
    document: {
      documentElement: { removeAttribute: () => {} },
      querySelectorAll: () => [staleVideo],
      querySelector: () => null,
    },
  });
  const source = asset("content.js")
    .split("function clearCandyPictureInPicturePresentation() {")[1]
    .split("function clearCandyInlineVideoPresentation() {")[0];
  vm.runInContext(
    `function clearCandyPictureInPicturePresentation() {${source}`,
    context,
  );

  context.clearCandyPictureInPicturePresentation();

  assert.deepEqual(
    JSON.parse(JSON.stringify(removedAttributes)),
    [
      ["stale", "data-candy-picture-in-picture-video"],
      ["current", "data-candy-picture-in-picture-video"],
    ],
  );
  assert.equal(removedProperties.length, 4);
});

test("picture in picture restoration acknowledges after two rendered frames", async () => {
  const frames = [];
  const playbackUpdates = [];
  const overlayUpdates = [];
  const presentedVideo = {};
  const context = vm.createContext({
    candyPictureInPicturePlayback: { presentedVideo },
    updateCandyPictureInPicturePlayback: (expected) => playbackUpdates.push(expected),
    nextCandyAnimationFrame: () => new Promise((resolve) => frames.push(resolve)),
    updateCandyInlineVideoControlsOverlay: (video) => overlayUpdates.push(video),
  });
  const source = asset("content.js")
    .split("async function prepareCandyPictureInPicturePlayback(message) {")[1]
    .split("function updateCandyInlineVideoPresentation(message) {")[0];
  vm.runInContext(
    `async function prepareCandyPictureInPicturePlayback(message) {${source}`,
    context,
  );

  let completed = false;
  const restoration = context.prepareCandyPictureInPicturePlayback({ expected: false })
    .then((result) => {
      completed = true;
      return result;
    });
  assert.deepEqual(playbackUpdates, [false]);
  assert.equal(completed, false);
  assert.equal(frames.length, 1);

  frames.shift()();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(completed, false);
  assert.equal(frames.length, 1);

  frames.shift()();
  assert.equal((await restoration).prepared, true);
  assert.deepEqual(overlayUpdates, [presentedVideo]);
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
  assert.match(source, /\.transport \{\s*border: 0;/);
  assert.match(
    source,
    /html\[\$\{CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE\}\] \[\$\{CANDY_INLINE_VIDEO_CONTROLS_ATTRIBUTE\}\] \{[\s\S]*?display: none !important;/,
  );
  assert.match(source, /updateCandyPictureInPictureVideoControls\(video, true\)/);
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
  assert.match(source, /return target\.requestFullscreen\(\)/);
  assert.match(source, /function candyInlineFullscreenGestureUpdate\(/);
  assert.match(source, /type: "inline-video-gesture-haptic"/);
  assert.match(source, /requestCandyInlineVideoFullscreen\(video\)/);
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
