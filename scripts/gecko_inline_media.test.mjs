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
      { revision: 3, navigationGeneration: 2, inlineMediaPlayerEnabled: true, inlineMediaPlayerMode: "button_fullscreen" },
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

test("content policy uses inline mode when extension policy is missing or invalid", () => {
  const context = vm.createContext({
    boundedSafeAreaInteger: (_value, _minimum, _maximum, fallback) => fallback,
  });
  const source = asset("background.js")
    .split("function contentPolicy(policy) {")[1]
    .split("function publishContentPolicy(token, policy) {")[0];
  vm.runInContext(`function contentPolicy(policy) {${source}`, context);

  assert.equal(
    context.contentPolicy().inlineMediaPlayerMode,
    "button_inline_and_fullscreen",
  );
  assert.equal(
    context.contentPolicy({ inlineMediaPlayerMode: "future-mode" }).inlineMediaPlayerMode,
    "button_inline_and_fullscreen",
  );
  assert.equal(
    context.contentPolicy({ inlineMediaPlayerMode: "button_fullscreen" }).inlineMediaPlayerMode,
    "button_fullscreen",
  );
});

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
    mode: "button_fullscreen",
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
      mode: "button_fullscreen",
      expected: true,
    },
  );
  assert.equal(
    harness.context.requestInlineVideoOpen({ ...request, expected: false }, sender),
    true,
  );
  assert.equal(harness.posted[2].expected, false);
  assert.equal(harness.posted[2].mode, undefined);

  for (const [invalid, invalidSender] of [
    [{ ...request, revision: 2 }, sender],
    [{ ...request, navigationGeneration: 1 }, sender],
    [{ ...request, elementNonce: "c".repeat(32) }, sender],
    [{ ...request, mode: "automatic" }, sender],
    [{ ...request, mode: undefined }, sender],
    [request, { tab: { id: 7 }, frameId: 2 }],
  ]) {
    assert.equal(harness.context.requestInlineVideoOpen(invalid, invalidSender), false);
  }
  assert.equal(harness.posted.length, 3);
});

function queuedInlineOpenHarness() {
  const background = candidateHarness();
  const sender = { tab: { id: 7 }, frameId: 0 };
  const policy = background.context.policiesByToken.get("token");
  policy.inlineMediaPlayerMode = "button_fullscreen";
  background.context.boundedSafeAreaInteger = (_value, _min, _max, fallback) => fallback;
  const backgroundSource = asset("background.js");
  vm.runInContext(
    `function contentPolicy(policy) {${backgroundSource
      .split("function contentPolicy(policy) {")[1]
      .split("function boundedSafeAreaInteger")[0]}`,
    background.context,
  );
  vm.runInContext(
    `function receiveOpen(message, sender) {
      if (message.type === "inline-video-open-request") {${backgroundSource
        .split('if (message.type === "inline-video-open-request") {')[1]
        .split('if (message.type === "inline-video-gesture-haptic")')[0]}
    }`,
    background.context,
  );
  const pending = [];
  const sent = [];
  const video = { isConnected: true };
  let currentVideo = video;
  let now = 0;
  const state = {
    inlineMediaPlayerEnabled: true,
    inlineMediaPlayerMode: "button_fullscreen",
    inlineOpenRequestKey: null,
    inlineOpenRequestTimer: null,
    inlineMediaPolicyRevision: 3,
    inlineMediaNavigationGeneration: 2,
  };
  const content = vm.createContext({
    candyPictureInPicturePlayback: state,
    candyInlineVideoDocumentNonce: candidate.documentNonce,
    candyInlineVideoElementNonce: () => candidate.elementNonce,
    candyInlineVideoForIdentity: () => currentVideo,
    currentCandyPictureInPictureVideo: () => currentVideo,
    candyVideoPresentationExpected: () => false,
    isCandyInlineVideoCandidate: (value) => value === currentVideo && value.isConnected,
    clearCandyInlineVideoOpenRequest: () => { state.inlineOpenRequestKey = null; },
    updateCandyInlineMediaPlayerPolicy: (updated) => {
      state.inlineMediaPlayerEnabled = updated.inlineMediaPlayerEnabled;
      state.inlineMediaPlayerMode = updated.inlineMediaPlayerMode;
      state.inlineMediaPolicyRevision = updated.revision;
      state.inlineMediaNavigationGeneration = updated.navigationGeneration;
    },
    reportCandyInlineVideoState: async () => {
      background.context.updateInlineVideoState({
        ...candidate,
        revision: state.inlineMediaPolicyRevision,
        navigationGeneration: state.inlineMediaNavigationGeneration,
      }, sender);
    },
    browser: {
      runtime: {
        sendMessage: (message) => {
          sent.push(message);
          return new Promise((resolve) => pending.push({ message, resolve }));
        },
      },
    },
    setTimeout: () => 1,
    performance: { now: () => now },
  });
  const contentSource = asset("content.js")
    .split("async function requestCandyInlineVideoOpen(video, enterFullscreen = false) {")[1]
    .split("async function requestCandyInlineVideoClose")[0];
  vm.runInContext(
    `async function requestCandyInlineVideoOpen(video, enterFullscreen = false) {${contentSource}`,
    content,
  );
  return {
    background,
    content,
    video,
    policy,
    state,
    sent,
    advanceTime: (elapsed) => { now += elapsed; },
    replaceVideo: () => { currentVideo = { isConnected: true }; },
    async deliver() {
      await new Promise((resolve) => setImmediate(resolve));
      const next = pending.shift();
      assert.ok(next, "Expected queued inline open request");
      next.resolve(await background.context.receiveOpen(next.message, sender));
      await new Promise((resolve) => setImmediate(resolve));
    },
    nativeOpens() {
      return background.posted.filter((message) => message.type === "inline-video-open-request");
    },
  };
}

test("queued inline open retries an inset policy revision for the same video and mode", async () => {
  const harness = queuedInlineOpenHarness();
  const opened = harness.content.requestCandyInlineVideoOpen(harness.video);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(harness.sent[0].revision, 3);
  harness.policy.revision = 4;
  await harness.deliver();
  assert.equal(harness.sent.length, 2);
  assert.equal(harness.sent[1].revision, 4);
  await harness.deliver();

  assert.equal(await opened, true);
  assert.equal(harness.nativeOpens().length, 1);
  assert.equal(harness.nativeOpens()[0].revision, 4);
  assert.equal(harness.nativeOpens()[0].documentNonce, candidate.documentNonce);
  assert.equal(harness.nativeOpens()[0].elementNonce, candidate.elementNonce);
});

for (const [name, change] of [
  ["navigation", (harness) => { harness.policy.navigationGeneration = 3; }],
  ["content navigation", (harness) => { harness.state.inlineMediaNavigationGeneration = 3; }],
  ["disabled mode", (harness) => { harness.policy.inlineMediaPlayerEnabled = false; }],
  ["different mode", (harness) => { harness.policy.inlineMediaPlayerMode = "automatic"; }],
  ["background candidate", (harness) => {
    harness.background.context.inlineVideosByTab.get(7).get(0).elementNonce = "c".repeat(32);
  }],
  ["content candidate", (harness) => { harness.replaceVideo(); }],
  ["expired intent", (harness) => { harness.advanceTime(3000); }],
]) {
  test(`queued inline open does not retry changed ${name}`, async () => {
    const harness = queuedInlineOpenHarness();
    const opened = harness.content.requestCandyInlineVideoOpen(harness.video);
    await new Promise((resolve) => setImmediate(resolve));
    harness.policy.revision = 4;
    change(harness);
    await harness.deliver();

    assert.equal(await opened, false);
    assert.equal(harness.sent.length, 1);
    assert.equal(harness.nativeOpens().length, 0);
  });
}

test("queued inline open keeps a newer matching policy already applied to content", async () => {
  const harness = queuedInlineOpenHarness();
  const opened = harness.content.requestCandyInlineVideoOpen(harness.video);
  await new Promise((resolve) => setImmediate(resolve));
  harness.policy.revision = 4;
  harness.state.inlineMediaPolicyRevision = 4;
  await harness.deliver();
  await harness.deliver();

  assert.equal(await opened, true);
  assert.equal(harness.nativeOpens().length, 1);
  assert.equal(harness.nativeOpens()[0].revision, 4);
});

test("queued inline open retries at most once across repeated policy changes", async () => {
  const harness = queuedInlineOpenHarness();
  const opened = harness.content.requestCandyInlineVideoOpen(harness.video);
  await new Promise((resolve) => setImmediate(resolve));
  harness.policy.revision = 4;
  await harness.deliver();
  assert.equal(harness.sent.length, 2);
  harness.policy.revision = 5;
  await harness.deliver();

  assert.equal(await opened, false);
  assert.equal(harness.sent.length, 2);
  assert.equal(harness.nativeOpens().length, 0);
});

test("queued automatic open stops when playback pauses before a policy retry", async () => {
  const harness = queuedInlineOpenHarness();
  harness.policy.inlineMediaPlayerMode = "automatic";
  harness.state.inlineMediaPlayerMode = "automatic";
  harness.video.paused = false;
  const opened = harness.content.requestCandyInlineVideoOpen(harness.video);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(harness.sent.length, 1);

  harness.policy.revision = 4;
  harness.video.paused = true;
  await harness.deliver();

  assert.equal(await opened, false);
  assert.equal(harness.sent.length, 1);
  assert.equal(harness.nativeOpens().length, 0);
});

test("automatic inline opening waits for playback and a decoded video frame", async () => {
  const video = {
    readyState: 0,
    videoWidth: 0,
    videoHeight: 0,
    clientWidth: 400,
    clientHeight: 225,
    duration: 537,
    currentTime: 0,
    paused: true,
    ended: false,
  };
  const opened = [];
  const reports = [];
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      inlineMediaPlayerEnabled: true,
      inlineMediaPlayerMode: "automatic",
      inlinePresentationExpected: false,
      inlineOpenRequestKey: null,
      inlineMediaPolicyRevision: 3,
      inlineMediaNavigationGeneration: 2,
      presentedVideo: null,
    },
    candyInlineVideoDocumentNonce: candidate.documentNonce,
    candyInlineVideoElementNonce: () => candidate.elementNonce,
    candyInlineMediaPlayerStartsAutomatically: () => true,
    candyVideoPresentationExpected: () => false,
    isCandyInlineVideoCandidate: (value) => value === video,
    currentCandyPictureInPictureVideo: () => video,
    updateCandyInlineVideoAction: () => {},
    updateCandyInlineVideoControlsOverlay: () => {},
    candyInlineVideoControlsVisibilityLabel: () => "controls",
    requestCandyInlineVideoOpen: (value) => { opened.push(value); },
    browser: { runtime: { sendMessage: (message) => {
      reports.push(message);
      return Promise.resolve();
    } } },
  });
  const source = asset("content.js")
    .split("function reportCandyInlineVideoState(preferredVideo = null) {")[1]
    .split("function clearCandyInlineVideoState() {")[0];
  vm.runInContext(`function reportCandyInlineVideoState(preferredVideo = null) {${source}`, context);

  await context.reportCandyInlineVideoState();
  assert.equal(reports.at(-1).active, true);
  assert.equal(opened.length, 0, "thumbnail-only video must not open Candy controls");

  video.readyState = 1;
  video.videoWidth = 1280;
  video.videoHeight = 720;
  await context.reportCandyInlineVideoState();
  assert.equal(opened.length, 0, "metadata and intrinsic dimensions are not a decoded frame");

  video.readyState = 2;
  video.videoWidth = 0;
  await context.reportCandyInlineVideoState();
  assert.equal(opened.length, 0, "current data without intrinsic video width must not open");

  video.videoWidth = 1280;
  await context.reportCandyInlineVideoState();
  assert.equal(opened.length, 0, "preloaded paused YouTube video must keep its site thumbnail");

  video.paused = false;
  await context.reportCandyInlineVideoState();
  assert.deepEqual(opened, [video]);

  context.candyPictureInPicturePlayback.inlinePresentationExpected = true;
  context.candyPictureInPicturePlayback.presentedVideo = video;
  video.paused = true;
  await context.reportCandyInlineVideoState();
  assert.equal(reports.at(-1).presented, true, "pausing an open Candy player keeps it presented");
  assert.deepEqual(opened, [video]);
  assert.match(asset("content.js"), /"loadeddata"/);
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
    resetCandyInlineVideoControlsVisibility: () => {},
    updateCandyInlineVideoControlsOverlay: () => {},
    candyInlineVideoControlsVisibilityLabel: () => "controls",
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
  assert.match(context.candyWobblyProgressPath(500), /^M 0 16 C /);
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
  context.rememberCandyInlineVideoStableOrigin = () => {};
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

for (const rememberBeforeDrag of [true, false]) {
  test(`first swipe fullscreen return restores unshifted video and controls (snapshot=${rememberBeforeDrag})`, () => {
    const videoProperties = new Map();
    const videoAttributes = new Set();
    const hostProperties = new Map();
    const frames = [];
    const bounds = (top, height = 225) => ({
      left: 0, right: 400, top, bottom: top + height, width: 400, height,
    });
    const player = {
      isConnected: true,
      parentElement: null,
      offsetWidth: 400,
      offsetHeight: 225,
      getBoundingClientRect: () => bounds(100),
      style: { setProperty: () => {}, removeProperty: () => {} },
      setAttribute: () => {},
      removeAttribute: () => {},
      requestFullscreen: () => {
        context.document.fullscreenElement = player;
        return Promise.resolve();
      },
    };
    const video = {
      isConnected: true,
      offsetWidth: 400,
      offsetHeight: 225,
      getBoundingClientRect: () => {
        const transform = videoProperties.get("transform") || "";
        const drag = Number(transform.match(/translate3d\(0, (-?[\d.]+)px/)?.[1] || 0);
        const originY = videoAttributes.has("data-candy-inline-video-origin") ?
          Number.parseFloat(videoProperties.get("--candy-inline-video-origin-y") || "0") : 0;
        return bounds(100 + drag + originY);
      },
      style: {
        getPropertyValue: (name) => videoProperties.get(name) || "",
        getPropertyPriority: () => "",
        setProperty: (name, value) => videoProperties.set(name, value),
        removeProperty: (name) => videoProperties.delete(name),
      },
      setAttribute: (name) => videoAttributes.add(name),
      removeAttribute: (name) => videoAttributes.delete(name),
    };
    const host = {
      isConnected: true,
      dataset: {},
      style: { setProperty: (name, value) => hostProperties.set(name, value) },
    };
    const context = vm.createContext({
      video, host,
      candyPictureInPicturePlayback: {
        inlineMediaPlayerEnabled: true,
        inlinePresentationExpected: true,
        presentedVideo: video,
        inlineControlsHost: host,
        inlineFullscreenOrigin: null,
        inlineStableOrigin: null,
        inlineStateFrame: null,
        candidates: new Set([video]),
        expected: false,
      },
      candyUsesBackgroundVideoVisibilityFix: true,
      candyInlineVideoSitePlayer: () => player,
      CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE: "data-candy-inline-video-fullscreen-origin",
      CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP: "--candy-inline-video-fullscreen-origin-top",
      CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT: "--candy-inline-video-fullscreen-origin-left",
      CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_STYLE_ATTRIBUTE: "data-candy-inline-video-fullscreen-origin-style",
      CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE: "data-candy-inline-video-origin",
      CANDY_INLINE_VIDEO_ORIGIN_X: "--candy-inline-video-origin-x",
      CANDY_INLINE_VIDEO_ORIGIN_Y: "--candy-inline-video-origin-y",
      CANDY_INLINE_VIDEO_ORIGIN_SCALE_X: "--candy-inline-video-origin-scale-x",
      CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y: "--candy-inline-video-origin-scale-y",
      CANDY_INLINE_VIDEO_ACTION_SIZE_PX: 56,
      CANDY_INLINE_VIDEO_CONTROLS_HEIGHT_PX: 88,
      document: {
        fullscreenElement: null,
        documentElement: { appendChild: () => {} },
        createElement: () => ({ setAttribute: () => {}, remove: () => {} }),
      },
      getComputedStyle: () => ({ top: "0px", left: "0px" }),
      innerWidth: 400, innerHeight: 800, scrollX: 0, scrollY: 0,
      location: { href: "https://m.youtube.com/watch?v=first-swipe" },
      requestAnimationFrame: (callback) => { frames.push(callback); return frames.length; },
      cancelAnimationFrame: () => {},
      clearTimeout: () => {},
      setCandyInlineActionStyle: (element, name, value) => element.style.setProperty(name, value),
      candyInlineVideoTapIsEligible: () => false,
      rememberCandyPictureInPictureVideos: () => {},
      reportCandyInlineVideoGestureHaptic: () => {},
      updateFullscreenGesture: () => ({ update: { shouldCommit: true }, thresholdChanged: false }),
    });
    const source = asset("content.js");
    const load = (start, end) => vm.runInContext(
      start + source.split(start)[1].split(end)[0], context,
    );
    load("function rememberCandyInlineVideoStableOrigin(video) {", "function clearCandyInlineVideoSiteControls() {");
    load("function setCandyInlineFullscreenGestureOffset(gesture, host, offset) {", "function removeCandyInlineVideoControlsOverlay() {");
    load("function positionCandyInlineVideoControls(video, host) {", "function createCandyInlineVideoControlsOverlay(video) {");
    load("function clearCandyInlineVideoFullscreenOrigin() {", "function reportCandyInlineVideoGestureHaptic(video, phase) {");
    load("function scheduleCandyInlineVideoStateReport() {", "function stopCandyInlineVideoStateObservation() {");
    load("const finishFullscreenGesture = (stopHaptic = true) => {", "  const updateFullscreenGesture = ");
    const pointerUp = source.split('fullscreenGesture.addEventListener("pointerup", (event) => {')[1]
      .split('  }, { passive: false });')[0];
    vm.runInContext(`function pointerUp(event) {${pointerUp}}`, context);
    context.reportCandyInlineVideoState = () => context.positionCandyInlineVideoControls(video, host);
    context.activeFullscreenGesture = {
      video, baseTransform: "", originalTransform: "", originalTransformPriority: "",
      rubberbandActive: false,
    };
    if (rememberBeforeDrag) context.positionCandyInlineVideoControls(video, host);
    context.setCandyInlineFullscreenGestureOffset(context.activeFullscreenGesture, host, 85);
    if (rememberBeforeDrag) {
      // The inline layout observer sees the gesture's video.style mutation before pointerup.
      context.scheduleCandyInlineVideoStateReport();
      frames.shift()();
    }
    assert.equal(video.getBoundingClientRect().top, 15);

    context.pointerUp({ isTrusted: true });
    context.document.fullscreenElement = null;
    context.reconcileCandyInlineVideoFullscreenOrigin();
    context.positionCandyInlineVideoControls(video, host);

    assert.equal(context.candyPictureInPicturePlayback.inlineFullscreenOrigin.videoBounds.top, 100);
    assert.deepEqual(video.getBoundingClientRect(), bounds(100));
    assert.equal(hostProperties.get("top"), "100px");
    assert.equal(hostProperties.get("height"), "225px");
    assert.equal(Number.parseFloat(hostProperties.get("top")) +
      Number.parseFloat(hostProperties.get("height")), 325);
    assert.equal(videoProperties.has("transform"), false);
  });
}

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
      alignmentMonitorFrame: null,
      layoutObserver: null,
      resizeObserver: null,
      pictureInPictureAncestors: [{
        removeAttribute: (name) => removedAttributes.push(["ancestor", name]),
      }],
      inlinePresentationExpected: true,
      presentedVideo: currentVideo,
    },
    CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE: "data-candy-picture-in-picture",
    CANDY_PICTURE_IN_PICTURE_VIDEO_ATTRIBUTE: "data-candy-picture-in-picture-video",
    CANDY_PICTURE_IN_PICTURE_ANCESTOR_ATTRIBUTE: "data-candy-picture-in-picture-ancestor",
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
      ["ancestor", "data-candy-picture-in-picture-ancestor"],
      ["stale", "data-candy-picture-in-picture-video"],
      ["current", "data-candy-picture-in-picture-video"],
    ],
  );
  assert.equal(removedProperties.length, 4);
});

test("picture in picture restoration acknowledges after two rendered frames", async () => {
  const { context, renderFrame } = restorationHarness();
  let completed = false;
  const restoration = context.prepareCandyPictureInPicturePlayback({ expected: false })
    .then((result) => {
      completed = true;
      return result;
    });
  assert.equal(completed, false);
  await renderFrame();
  assert.equal(completed, false);
  await renderFrame();
  assert.equal((await restoration).prepared, true);
});

function restorationHarness() {
  const frames = new Map();
  const timers = new Map();
  const overlayBounds = [];
  let nextCallbackId = 0;
  let clock = 0;
  let siteTop = 100;
  let siteWidth = 400;
  const element = () => {
    const attributes = new Set();
    const properties = new Map();
    return {
      isConnected: true,
      parentElement: null,
      offsetWidth: 400,
      offsetHeight: 225,
      setAttribute: (name) => attributes.add(name),
      removeAttribute: (name) => attributes.delete(name),
      hasAttribute: (name) => attributes.has(name),
      style: {
        setProperty: (name, value) => properties.set(name, value),
        removeProperty: (name) => properties.delete(name),
        getPropertyValue: (name) => properties.get(name) || "",
      },
      remove: () => {},
    };
  };
  const player = element();
  const video = element();
  const root = element();
  const pipStyle = element();
  let pipStyleRemoved = false;
  pipStyle.remove = () => { pipStyleRemoved = true; };
  player.getBoundingClientRect = () => ({
    left: 0,
    top: siteTop + (player.hasAttribute("data-candy-inline-video-fullscreen-origin") ?
      Number.parseFloat(player.style.getPropertyValue("--candy-inline-video-fullscreen-origin-top")) : 0),
    width: siteWidth,
    height: 225,
  });
  video.getBoundingClientRect = () => {
    const bounds = player.getBoundingClientRect();
    return { ...bounds, right: bounds.left + bounds.width, bottom: bounds.top + bounds.height };
  };
  const host = element();
  host.getBoundingClientRect = () => video.getBoundingClientRect();
  const playback = {
    generation: 4,
    expected: false,
    inlinePresentationExpected: true,
    inlineMediaNavigationGeneration: 2,
    presentedVideo: video,
    inlineFullscreenOrigin: null,
    inlineControlsHost: host,
    inlineControlsVideo: video,
    alignmentFrame: null,
    alignmentMonitorFrame: null,
    pictureInPictureAncestors: [],
  };
  const context = vm.createContext({
    candyPictureInPicturePlayback: playback,
    candyPictureInPictureOriginalControls: new WeakMap(),
    candyInlineVideoDocumentNonce: candidate.documentNonce,
    candyInlineVideoElementNonces: new WeakMap([[video, candidate.elementNonce]]),
    candyUsesBackgroundVideoVisibilityFix: true,
    document: {
      fullscreenElement: null,
      documentElement: Object.assign(root, { appendChild: () => {} }),
      createElement: element,
      querySelectorAll: () => [video],
      querySelector: () => pipStyleRemoved ? null : pipStyle,
    },
    getComputedStyle: () => ({ top: "0px", left: "0px" }),
    innerWidth: 400, innerHeight: 800, scrollX: 0, scrollY: 0,
    location: { href: "https://m.youtube.com/watch?v=restore-readiness" },
    performance: { now: () => clock },
    requestAnimationFrame: (callback) => {
      frames.set(++nextCallbackId, callback);
      return nextCallbackId;
    },
    cancelAnimationFrame: (id) => frames.delete(id),
    setTimeout: (callback, delay) => {
      timers.set(++nextCallbackId, { callback, deadline: clock + delay });
      return nextCallbackId;
    },
    clearTimeout: (id) => timers.delete(id),
    updateCandyInlineVideoControlsOverlay: (currentVideo) => {
      assert.equal(currentVideo, video);
      overlayBounds.push(currentVideo.getBoundingClientRect());
    },
  });
  const source = asset("content.js");
  const load = (start, end) => vm.runInContext(
    start + source.split(start)[1].split(end)[0], context,
  );
  load("const CANDY_PICTURE_IN_PICTURE_ROOT_ATTRIBUTE =", "const CANDY_INLINE_MEDIA_PLAYER_MODES =");
  load("function updateCandyPictureInPictureVideoControls(video, expected) {", "function clearCandyInlineVideoPresentation() {");
  load("function clearCandyInlineVideoFullscreenOrigin() {", "function requestCandyInlineVideoFullscreen(video) {");
  load("function updateCandyPictureInPicturePlayback(expected) {", "function updateCandyInlineVideoPresentation(message) {");
  const renderFrame = async () => {
    clock += 16;
    const callbacks = Array.from(frames.values());
    frames.clear();
    callbacks.forEach((callback) => callback(clock));
    await new Promise((resolve) => setImmediate(resolve));
  };
  return {
    context, playback, player, video, root, frames, timers, renderFrame, overlayBounds, host,
    pipStyleRemoved: () => pipStyleRemoved,
    setLayout: (top, width) => { siteTop = top; siteWidth = width; },
    advanceClock: (value) => { clock = value; },
  };
}

test("PiP restoration waits for matching origin geometry and two stable frames", async () => {
  const { context, playback, player, video, root, renderFrame, overlayBounds,
    pipStyleRemoved, setLayout, advanceClock } = restorationHarness();
  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  const origin = playback.inlineFullscreenOrigin;
  playback.expected = true;
  context.updateCandyInlineVideoFullscreenOriginVisibility();
  root.setAttribute("data-candy-picture-in-picture");
  video.setAttribute("data-candy-picture-in-picture-video");
  setLayout(50, 320);
  let result;
  context.prepareCandyPictureInPicturePlayback({
    expected: false,
    documentNonce: candidate.documentNonce,
    elementNonce: candidate.elementNonce,
  }).then((value) => { result = value; });
  assert.equal(playback.generation, 5);
  assert.equal(pipStyleRemoved(), true);
  assert.equal(root.hasAttribute("data-candy-picture-in-picture"), false);
  assert.equal(video.hasAttribute("data-candy-picture-in-picture-video"), false);
  await renderFrame();
  await renderFrame();
  assert.equal(context.candyInlineVideoOriginLayoutMatches(origin), false);
  assert.equal(video.getBoundingClientRect().top, 50);
  assert.equal(result, undefined, "two frames must not acknowledge mismatching origin geometry");

  advanceClock(1_000);
  setLayout(50, 400);
  await renderFrame();
  assert.equal(video.getBoundingClientRect().top, 100);
  assert.equal(result, undefined, "one matching frame must not acknowledge restored geometry");
  await renderFrame();
  assert.equal(result?.prepared, true);
  assert.equal(overlayBounds.at(-1).top, origin.videoBounds.top);
  assert.equal(overlayBounds.at(-1).height, origin.videoBounds.height);
  assert.equal(playback.presentedVideo, video);
  assert.equal(playback.inlineFullscreenOrigin, origin);
  assert.equal(playback.inlineMediaNavigationGeneration, 2);
  assert.equal(playback.generation, 5);
});

test("PiP restoration preserves DOM fullscreen without applying the inline snapshot", async () => {
  const { context, playback, player, video, setLayout, renderFrame } = restorationHarness();
  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  playback.expected = true;
  context.document.fullscreenElement = player;
  setLayout(0, 800);
  const restoration = context.prepareCandyPictureInPicturePlayback({ expected: false });
  await renderFrame();
  await renderFrame();
  assert.equal((await restoration).prepared, true);
  assert.equal(video.getBoundingClientRect().top, 0);
});

test("direct fullscreen exit cannot acknowledge while DOM fullscreen remains active", async () => {
  const { context, player, video, setLayout, renderFrame } = restorationHarness();
  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  context.document.fullscreenElement = player;
  setLayout(0, 800);
  let result;
  context.prepareCandyPictureInPicturePlayback({ expected: false })
    .then((value) => { result = value; });

  await renderFrame();
  await renderFrame();
  assert.equal(result, undefined, "direct return must wait for DOM fullscreen to exit");

  context.document.fullscreenElement = null;
  setLayout(100, 400);
  await renderFrame();
  await renderFrame();
  assert.equal(result?.prepared, true);
  assert.equal(video.getBoundingClientRect().top, 100);
});

for (const surface of ["video", "controls"]) {
  test(`PiP restoration waits for the actual ${surface} rectangle after player layout matches`, async () => {
    const { context, player, video, host, renderFrame } = restorationHarness();
    context.preserveCandyInlineVideoFullscreenOrigin(video, player);
    const target = surface === "video" ? video : host;
    const originalBounds = target.getBoundingClientRect;
    target.getBoundingClientRect = () => {
      const bounds = originalBounds();
      return { ...bounds, top: bounds.top - 30, bottom: bounds.bottom - 30 };
    };
    let result;
    context.prepareCandyPictureInPicturePlayback({ expected: false })
      .then((value) => { result = value; });
    await renderFrame();
    await renderFrame();
    assert.equal(context.candyInlineVideoOriginLayoutMatches(
      context.candyPictureInPicturePlayback.inlineFullscreenOrigin,
    ), true);
    assert.equal(result, undefined);
    target.getBoundingClientRect = originalBounds;
    await renderFrame();
    assert.equal(result, undefined);
    await renderFrame();
    assert.equal(result?.prepared, true);
  });
}

for (const [reason, change] of [
  ["navigation generation", ({ playback }) => { playback.inlineMediaNavigationGeneration++; }],
  ["document URL", ({ context }) => { context.location.href += "&next=1"; }],
  ["video replacement", ({ playback }) => { playback.presentedVideo = {}; }],
  ["video detachment", ({ video }) => { video.isConnected = false; }],
  ["new PiP entry", ({ playback }) => { playback.expected = true; playback.generation++; }],
  ["superseding restore", ({ playback }) => { playback.generation++; }],
  ["closed inline presentation", ({ playback }) => { playback.inlinePresentationExpected = false; }],
]) {
  test(`PiP restoration cancels on ${reason}`, async () => {
    const harness = restorationHarness();
    const restoration = harness.context.prepareCandyPictureInPicturePlayback({ expected: false });
    await harness.renderFrame();
    change(harness);
    await harness.renderFrame();
    assert.equal((await restoration).prepared, false);
    assert.equal(harness.timers.size, 0);
  });
}

test("PiP restoration times out without animation frames and cancels its own work", async () => {
  const { context, frames, timers, advanceClock } = restorationHarness();
  const restoration = context.prepareCandyPictureInPicturePlayback({ expected: false });
  assert.equal(frames.size, 1);
  advanceClock(2_000);
  for (const timer of Array.from(timers.values())) {
    if (timer.deadline <= 2_000) timer.callback();
  }
  assert.equal((await restoration).prepared, false);
  assert.equal(frames.size, 0);
  assert.equal(timers.size, 0);
});

test("PiP restoration cannot succeed when a late frame runs before the timeout task", async () => {
  const { context, renderFrame, advanceClock } = restorationHarness();
  const restoration = context.prepareCandyPictureInPicturePlayback({ expected: false });
  await renderFrame();
  advanceClock(2_000);
  await renderFrame();
  assert.equal((await restoration).prepared, false);
});

test("PiP restoration requires consecutive stable frames after a second layout shift", async () => {
  const { context, renderFrame, setLayout } = restorationHarness();
  let result;
  context.prepareCandyPictureInPicturePlayback({ expected: false })
    .then((value) => { result = value; });
  await renderFrame();
  setLayout(130, 400);
  await renderFrame();
  assert.equal(result, undefined);
  await renderFrame();
  assert.equal(result?.prepared, true);
});

test("PiP return keeps original player box through a delayed parent shift", () => {
  const properties = new Map();
  const videoProperties = new Map();
  const videoAttributes = new Set();
  const frames = [];
  const timeouts = [];
  const attributes = new Set();
  let siteTop = 100;
  let siteWidth = 400;
  let videoLocalTop = 0;
  let clock = 0;
  const player = {
    isConnected: true,
    offsetWidth: 400,
    offsetHeight: 225,
    style: {
      setProperty: (name, value) => properties.set(name, value),
      removeProperty: (name) => properties.delete(name),
    },
    setAttribute: (name) => attributes.add(name),
    removeAttribute: (name) => attributes.delete(name),
    getBoundingClientRect: () => ({
      left: 0,
      top: siteTop + (attributes.has("data-candy-inline-video-fullscreen-origin") ?
        Number.parseFloat(properties.get("--candy-inline-video-fullscreen-origin-top") || "0") :
        0),
      width: siteWidth,
      height: 225,
    }),
  };
  const video = {
    isConnected: true,
    offsetWidth: 400,
    offsetHeight: 225,
    style: {
      setProperty: (name, value) => videoProperties.set(name, value),
      removeProperty: (name) => videoProperties.delete(name),
    },
    setAttribute: (name) => videoAttributes.add(name),
    removeAttribute: (name) => videoAttributes.delete(name),
    getBoundingClientRect: () => ({
      left: videoAttributes.has("data-candy-inline-video-origin") ?
        Number.parseFloat(videoProperties.get("--candy-inline-video-origin-x") || "0") : 0,
      top: player.getBoundingClientRect().top + videoLocalTop +
        (videoAttributes.has("data-candy-inline-video-origin") ?
          Number.parseFloat(videoProperties.get("--candy-inline-video-origin-y") || "0") : 0),
      width: 400 * (videoAttributes.has("data-candy-inline-video-origin") ?
        Number(videoProperties.get("--candy-inline-video-origin-scale-x") || 1) : 1),
      height: 225 * (videoAttributes.has("data-candy-inline-video-origin") ?
        Number(videoProperties.get("--candy-inline-video-origin-scale-y") || 1) : 1),
    }),
  };
  const context = vm.createContext({
    candyPictureInPicturePlayback: { inlineFullscreenOrigin: null, expected: false },
    candyUsesBackgroundVideoVisibilityFix: true,
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE: "data-candy-inline-video-fullscreen-origin",
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP: "--candy-inline-video-fullscreen-origin-top",
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT: "--candy-inline-video-fullscreen-origin-left",
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_STYLE_ATTRIBUTE:
      "data-candy-inline-video-fullscreen-origin-style",
    CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE: "data-candy-inline-video-origin",
    CANDY_INLINE_VIDEO_ORIGIN_X: "--candy-inline-video-origin-x",
    CANDY_INLINE_VIDEO_ORIGIN_Y: "--candy-inline-video-origin-y",
    CANDY_INLINE_VIDEO_ORIGIN_SCALE_X: "--candy-inline-video-origin-scale-x",
    CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y: "--candy-inline-video-origin-scale-y",
    document: {
      fullscreenElement: null,
      documentElement: { appendChild: () => {} },
      createElement: () => ({ setAttribute: () => {}, remove: () => {} }),
    },
    getComputedStyle: () => ({ top: "0px", left: "0px" }),
    innerWidth: 400,
    innerHeight: 800,
    scrollX: 0,
    scrollY: 0,
    location: { href: "https://m.youtube.com/watch?v=test" },
    performance: { now: () => clock },
    requestAnimationFrame: (callback) => { frames.push(callback); return frames.length; },
    cancelAnimationFrame: () => {},
    setTimeout: (callback) => { timeouts.push(callback); return timeouts.length; },
    clearTimeout: () => {},
  });
  const source = asset("content.js")
    .split("function clearCandyInlineVideoFullscreenOrigin() {")[1]
    .split("function requestCandyInlineVideoFullscreen(video) {")[0];
  vm.runInContext(`function clearCandyInlineVideoFullscreenOrigin() {${source}`, context);
  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  assert.equal(player.getBoundingClientRect().top, 100);
  assert.equal(attributes.has("data-candy-inline-video-fullscreen-origin"), true);
  context.candyPictureInPicturePlayback.expected = true;
  context.updateCandyInlineVideoFullscreenOriginVisibility();
  assert.equal(attributes.has("data-candy-inline-video-fullscreen-origin"), false);
  siteTop = 150;
  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  assert.equal(context.candyPictureInPicturePlayback.inlineFullscreenOrigin.bounds.top, 100);
  context.restoreCandyInlineVideoFullscreenOrigin();
  assert.equal(player.getBoundingClientRect().top, 150);

  context.candyPictureInPicturePlayback.expected = false;
  context.restoreCandyInlineVideoFullscreenOrigin();
  assert.equal(attributes.has("data-candy-inline-video-fullscreen-origin"), true);
  assert.equal(player.getBoundingClientRect().top, 100);
  const alreadyAlignedTop = properties.get("--candy-inline-video-fullscreen-origin-top");
  frames.shift()();
  assert.equal(properties.get("--candy-inline-video-fullscreen-origin-top"), alreadyAlignedTop);
  clock = 1_200;
  siteTop = 185;
  frames.shift()();
  assert.equal(player.getBoundingClientRect().top, 100);
  assert.equal(properties.get("--candy-inline-video-fullscreen-origin-top"), "-85px");
  assert.equal(video.getBoundingClientRect().top, 100);

  videoLocalTop = 40;
  frames.shift()();
  assert.equal(video.getBoundingClientRect().top, 100);
  assert.equal(videoProperties.get("--candy-inline-video-origin-y"), "-40px");

  context.scrollY = 40;
  siteTop = 145;
  frames.shift()();
  assert.equal(player.getBoundingClientRect().top, 60);
  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  assert.equal(context.candyPictureInPicturePlayback.inlineFullscreenOrigin.bounds.top, 100);
  siteTop = 200;
  context.restoreCandyInlineVideoFullscreenOrigin();
  assert.equal(player.getBoundingClientRect().top, 60);
  context.document.fullscreenElement = {};
  context.updateCandyInlineVideoFullscreenOriginVisibility();
  assert.equal(attributes.has("data-candy-inline-video-fullscreen-origin"), false);
  context.document.fullscreenElement = null;
  context.restoreCandyInlineVideoFullscreenOrigin();
  assert.equal(attributes.has("data-candy-inline-video-fullscreen-origin"), true);

  siteWidth = 320;
  context.reconcileCandyInlineVideoFullscreenOrigin();
  timeouts.shift()();
  assert.equal(context.candyPictureInPicturePlayback.inlineFullscreenOrigin, null);

  context.clearCandyInlineVideoFullscreenOrigin();
  assert.equal(properties.has("--candy-inline-video-fullscreen-origin-top"), false);
  assert.equal(attributes.has("data-candy-inline-video-fullscreen-origin"), false);
});

test("origin completion reconciles a late interpolated ancestor transform", () => {
  const playerProperties = new Map([["transform", "translateY(12px)"]]);
  const videoProperties = new Map();
  const playerAttributes = new Set();
  const videoAttributes = new Set();
  const frames = [];
  const mutationObservers = [];
  const documentListeners = [];
  let siteTop = 100;
  let clock = 0;
  const ancestor = {
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 400, height: 225 }),
    parentElement: null,
  };
  const player = {
    isConnected: true,
    parentElement: ancestor,
    offsetWidth: 400,
    offsetHeight: 225,
    style: {
      setProperty: (name, value) => playerProperties.set(name, value),
      removeProperty: (name) => playerProperties.delete(name),
      getPropertyValue: (name) => playerProperties.get(name) || "",
    },
    setAttribute: (name) => playerAttributes.add(name),
    removeAttribute: (name) => playerAttributes.delete(name),
    getBoundingClientRect: () => ({
      left: 0,
      top: siteTop + (playerAttributes.has("data-candy-inline-video-fullscreen-origin") ?
        Number.parseFloat(playerProperties.get("--candy-inline-video-fullscreen-origin-top") || "0") :
        0),
      width: 400,
      height: 225,
    }),
  };
  const video = {
    isConnected: true,
    offsetWidth: 400,
    offsetHeight: 225,
    style: {
      setProperty: (name, value) => videoProperties.set(name, value),
      removeProperty: (name) => videoProperties.delete(name),
    },
    setAttribute: (name) => videoAttributes.add(name),
    removeAttribute: (name) => videoAttributes.delete(name),
    getBoundingClientRect: () => ({
      left: 0,
      top: player.getBoundingClientRect().top,
      width: 400,
      height: 225,
    }),
  };
  const context = vm.createContext({
    candyPictureInPicturePlayback: { inlineFullscreenOrigin: null, expected: false },
    candyUsesBackgroundVideoVisibilityFix: true,
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_ATTRIBUTE: "data-candy-inline-video-fullscreen-origin",
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_TOP: "--candy-inline-video-fullscreen-origin-top",
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_LEFT: "--candy-inline-video-fullscreen-origin-left",
    CANDY_INLINE_VIDEO_FULLSCREEN_ORIGIN_STYLE_ATTRIBUTE:
      "data-candy-inline-video-fullscreen-origin-style",
    CANDY_INLINE_VIDEO_ORIGIN_ATTRIBUTE: "data-candy-inline-video-origin",
    CANDY_INLINE_VIDEO_ORIGIN_X: "--candy-inline-video-origin-x",
    CANDY_INLINE_VIDEO_ORIGIN_Y: "--candy-inline-video-origin-y",
    CANDY_INLINE_VIDEO_ORIGIN_SCALE_X: "--candy-inline-video-origin-scale-x",
    CANDY_INLINE_VIDEO_ORIGIN_SCALE_Y: "--candy-inline-video-origin-scale-y",
    document: {
      fullscreenElement: null,
      documentElement: { appendChild: () => {} },
      createElement: () => ({ setAttribute: () => {}, remove: () => {} }),
      addEventListener: (type, listener) => documentListeners.push({ type, listener }),
    },
    getComputedStyle: () => ({ top: "0px", left: "0px" }),
    innerWidth: 400,
    innerHeight: 800,
    scrollX: 0,
    scrollY: 0,
    location: { href: "https://m.youtube.com/watch?v=test" },
    performance: { now: () => clock },
    requestAnimationFrame: (callback) => { frames.push(callback); return frames.length; },
    cancelAnimationFrame: () => {},
    setTimeout: () => 1,
    clearTimeout: () => {},
    ResizeObserver: class {
      disconnect() {}
      observe() {}
    },
    scheduleCandyPictureInPictureAlignment: () => {},
    scheduleCandyInlineVideoStateReport: () => {},
    MutationObserver: class {
      constructor(callback) {
        this.callback = callback;
        this.observed = [];
        this.disconnected = false;
        mutationObservers.push(this);
      }

      observe(target, options) {
        this.observed.push({ target, options });
      }

      disconnect() {
        this.disconnected = true;
      }
    },
  });
  const source = asset("content.js")
    .split("function clearCandyInlineVideoFullscreenOrigin() {")[1]
    .split("function requestCandyInlineVideoFullscreen(video) {")[0];
  vm.runInContext(
    `function clearCandyInlineVideoFullscreenOrigin() {${source}`,
    context,
  );
  const completionSource = asset("content.js")
    .split('document.addEventListener("transitionend", scheduleCandyPictureInPictureAlignment, true);')[1]
    .split('window.addEventListener("visibilitychange", (event) => {')[0];
  vm.runInContext(completionSource, context);

  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  const observer = mutationObservers[0];
  assert.ok(observer);
  assert.ok(observer.observed.every(({ target }) => target !== player));

  clock = 6_501;
  siteTop = 132;
  observer.callback([{ target: ancestor, attributeName: "style" }]);
  assert.equal(frames.length, 1);
  frames.shift()();
  assert.equal(player.getBoundingClientRect().top, 100);
  assert.equal(playerProperties.get("transform"), "translateY(12px)");

  siteTop = 164;
  documentListeners
    .filter(({ type }) => type === "transitionend")
    .forEach(({ listener }) => listener({ target: ancestor }));
  assert.equal(frames.length, 1);
  frames.shift()();
  assert.equal(player.getBoundingClientRect().top, 100);
  assert.equal(frames.length, 0);

  context.clearCandyInlineVideoFullscreenOrigin();
  assert.equal(observer.disconnected, true);
});

test("first PiP expected message captures origin before video-only layout", () => {
  const calls = [];
  const video = { isConnected: true };
  const player = { isConnected: true };
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      expected: false,
      inlinePresentationExpected: true,
      presentedVideo: video,
      generation: 0,
    },
    candyInlineVideoSitePlayer: () => player,
    preserveCandyInlineVideoFullscreenOrigin: () => calls.push("capture"),
    updateCandyInlineVideoFullscreenOriginVisibility: () => calls.push("hide-origin"),
    removeCandyInlineVideoControlsOverlay: () => {},
    resetCandyInlineVideoControlsVisibility: () => {},
    rememberCandyPictureInPictureVideos: () => {},
    presentCandyPictureInPictureVideo: () => calls.push("video-only"),
    monitorCandyPictureInPictureAlignment: () => {},
    scheduleCandyPictureInPicturePlayback: () => {},
  });
  const source = asset("content.js")
    .split("function updateCandyPictureInPicturePlayback(expected) {")[1]
    .split("function nextCandyAnimationFrame() {")[0];
  vm.runInContext(`function updateCandyPictureInPicturePlayback(expected) {${source}`, context);

  context.updateCandyPictureInPicturePlayback(true);
  context.updateCandyPictureInPicturePlayback(true);
  assert.deepEqual(calls, ["capture", "hide-origin", "video-only", "hide-origin", "video-only"]);
});

test("fullscreen button keeps its origin through first presentation and PiP return", () => {
  const video = { isConnected: true, controls: true, parentElement: null };
  const otherVideo = { isConnected: true, controls: true, parentElement: null };
  const player = { isConnected: true };
  const origin = { video, player };
  const stableOrigin = { video, player };
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      expected: false,
      generation: 0,
      inlineControlsObserver: null,
      inlinePresentationResizeObserver: null,
      inlinePresentationLayoutObserver: null,
      inlinePresentationExpected: false,
      inlineFullscreenOrigin: null,
      inlineStableOrigin: null,
      presentedVideo: null,
    },
    candyInlineVideoOriginalControls: new WeakMap(),
    MutationObserver: class {
      disconnect() {}
      observe() {}
    },
    ResizeObserver: class {
      disconnect() {}
      observe() {}
    },
    isCandyInlineVideoCandidate: (candidate) => candidate === video || candidate === otherVideo,
    clearCandyInlineVideoOpenRequest: () => {},
    clearCandyInlineVideoControls: () => {},
    clearCandyInlineVideoFullscreenOrigin: () => {
      context.candyPictureInPicturePlayback.inlineFullscreenOrigin = null;
    },
    scheduleCandyInlineVideoStateReport: () => {},
    suppressCandyInlineVideoSiteControls: () => {},
    createCandyInlineVideoControlsOverlay: () => {},
    reportCandyInlineVideoState: () => {},
    candyInlineVideoSitePlayer: () => player,
    preserveCandyInlineVideoFullscreenOrigin: (candidate, sitePlayer) => {
      assert.equal(candidate, video);
      assert.equal(sitePlayer, player);
      if (!context.candyPictureInPicturePlayback.inlineFullscreenOrigin) {
        context.candyPictureInPicturePlayback.inlineFullscreenOrigin = origin;
      }
      if (!context.candyPictureInPicturePlayback.inlineStableOrigin) {
        context.candyPictureInPicturePlayback.inlineStableOrigin = stableOrigin;
      }
    },
    clearCandyPictureInPicturePresentation: () => {},
    restoreCandyInlineVideoFullscreenOrigin: () => {},
    updateCandyInlineVideoControlsOverlay: () => {},
    updateCandyInlineVideoFullscreenOriginVisibility: () => {},
    removeCandyInlineVideoControlsOverlay: () => {},
    resetCandyInlineVideoControlsVisibility: () => {},
    rememberCandyPictureInPictureVideos: () => {},
    presentCandyPictureInPictureVideo: () => {},
    monitorCandyPictureInPictureAlignment: () => {},
    scheduleCandyPictureInPicturePlayback: () => {},
  });
  const presentSource = asset("content.js")
    .split("function presentCandyInlineVideo(video) {")[1]
    .split("function isCandyInlineVideoCandidate(video) {")[0];
  const pictureInPictureSource = asset("content.js")
    .split("function updateCandyPictureInPicturePlayback(expected) {")[1]
    .split("function nextCandyAnimationFrame() {")[0];
  vm.runInContext(
    `function presentCandyInlineVideo(video) {${presentSource}` +
      `function updateCandyPictureInPicturePlayback(expected) {${pictureInPictureSource}`,
    context,
  );

  context.preserveCandyInlineVideoFullscreenOrigin(video, player);
  assert.equal(context.presentCandyInlineVideo(video), true);
  assert.equal(context.candyPictureInPicturePlayback.inlineFullscreenOrigin, origin);
  assert.equal(context.candyPictureInPicturePlayback.inlineStableOrigin, stableOrigin);

  context.updateCandyPictureInPicturePlayback(true);
  context.updateCandyPictureInPicturePlayback(false);
  assert.equal(context.candyPictureInPicturePlayback.inlineFullscreenOrigin, origin);
  assert.equal(context.candyPictureInPicturePlayback.inlineStableOrigin, stableOrigin);

  assert.equal(context.presentCandyInlineVideo(otherVideo), true);
  assert.equal(context.candyPictureInPicturePlayback.inlineFullscreenOrigin, null);
  assert.equal(context.candyPictureInPicturePlayback.inlineStableOrigin, null);
});

test("inline video remembers its visible box before PiP host resize", () => {
  let top = 100;
  const player = {
    getBoundingClientRect: () => ({ left: 0, top, width: 400, height: 225 }),
    parentElement: null,
  };
  const video = {
    getBoundingClientRect: () => ({ left: 0, top, width: 400, height: 225 }),
  };
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      inlinePresentationExpected: true,
      presentedVideo: video,
      expected: false,
      inlineFullscreenOrigin: null,
      inlineStableOrigin: null,
    },
    candyInlineVideoSitePlayer: () => player,
    document: { fullscreenElement: null },
    getComputedStyle: () => ({ top: "0px", left: "0px" }),
    location: { href: "https://m.youtube.com/watch?v=test" },
    innerWidth: 400,
    innerHeight: 800,
    scrollX: 0,
    scrollY: 0,
  });
  const source = asset("content.js")
    .split("function rememberCandyInlineVideoStableOrigin(video) {")[1]
    .split("function clearCandyInlineVideoSiteControls() {")[0];
  vm.runInContext(`function rememberCandyInlineVideoStableOrigin(video) {${source}`, context);

  context.rememberCandyInlineVideoStableOrigin(video);
  const initial = context.candyPictureInPicturePlayback.inlineStableOrigin;
  assert.deepEqual(
    [initial.videoBounds.left, initial.videoBounds.top,
      initial.videoBounds.width, initial.videoBounds.height],
    [0, 100, 400, 225],
  );
  context.innerHeight = 400;
  top = 180;
  context.rememberCandyInlineVideoStableOrigin(video);
  assert.equal(context.candyPictureInPicturePlayback.inlineStableOrigin, initial);
});

test("PiP aligns a video inside a scaled YouTube player", () => {
  const properties = new Map();
  const scale = 0.75;
  const video = {
    isConnected: true,
    offsetWidth: 400,
    offsetHeight: 225,
    style: { setProperty: (name, value) => properties.set(name, value) },
    getBoundingClientRect: () => ({
      left: 50 + scale * Number.parseFloat(
        properties.get("--candy-picture-in-picture-offset-x") || "0",
      ),
      top: 100 + scale * Number.parseFloat(
        properties.get("--candy-picture-in-picture-offset-y") || "0",
      ),
      width: 400 * scale,
      height: 225 * scale,
    }),
  };
  const context = vm.createContext({
    candyPictureInPicturePlayback: { expected: true, presentedVideo: video },
    CANDY_PICTURE_IN_PICTURE_OFFSET_X: "--candy-picture-in-picture-offset-x",
    CANDY_PICTURE_IN_PICTURE_OFFSET_Y: "--candy-picture-in-picture-offset-y",
  });
  const source = asset("content.js")
    .split("function alignCandyPictureInPictureVideo(video) {")[1]
    .split("function scheduleCandyPictureInPictureAlignment() {")[0];
  vm.runInContext(`function alignCandyPictureInPictureVideo(video) {${source}`, context);

  context.alignCandyPictureInPictureVideo(video);
  assert.ok(Math.abs(video.getBoundingClientRect().left) < 0.5);
  assert.ok(Math.abs(video.getBoundingClientRect().top) < 0.5);
});

// Run the production shadow-overlay listeners with a minimal DOM backed by Node EventTarget.
// Geometry/observers are platform fakes; gesture classification, visibility, policy updates,
// presentation lifetime and every event listener below are the real content.js functions.
function inlineControlsHarness({ fullscreen = false, paused = true } = {}) {
  class Element extends EventTarget {
    constructor(tagName) {
      super();
      this.tagName = tagName;
      this.children = [];
      this.dataset = {};
      this.attributes = new Map();
      this.properties = new Map();
      this.style = {
        setProperty: (key, value) => this.properties.set(key, value),
        getPropertyValue: (key) => this.properties.get(key) || "",
        getPropertyPriority: () => "",
        removeProperty: (key) => this.properties.delete(key),
      };
      this.hidden = false;
      this.inert = false;
    }
    get isConnected() { return this === document.documentElement || Boolean(this.parentElement?.isConnected); }
    setAttribute(key, value) { this.attributes.set(key, String(value)); }
    getAttribute(key) { return this.attributes.get(key) ?? null; }
    removeAttribute(key) { this.attributes.delete(key); }
    append(...children) { children.forEach((child) => this.appendChild(child)); }
    appendChild(child) { child.remove(); child.parentElement = this; this.children.push(child); }
    remove() {
      if (this.parentElement) {
        this.parentElement.children = this.parentElement.children.filter((child) => child !== this);
      }
      this.parentElement = null;
    }
    attachShadow() { this.testShadow = new Element("shadow-root"); this.append(this.testShadow); return this.testShadow; }
    contains(element) { return element === this || this.children.some((child) => child.contains(element)); }
    getBoundingClientRect() { return { left: 0, top: 100, right: 400, bottom: 325, width: 400, height: 225 }; }
    setPointerCapture() {}
    closest() { return null; }
    querySelector(selector) {
      const className = selector.slice(1);
      for (const child of this.children) {
        if (child.className?.split(" ").includes(className)) return child;
        const found = child.querySelector(selector);
        if (found) return found;
      }
      return null;
    }
  }
  const document = new EventTarget();
  document.documentElement = new Element("html");
  document.createElement = (tag) => new Element(tag);
  document.createElementNS = (_namespace, tag) => new Element(tag);
  const video = new Element("video");
  Object.assign(video, { paused, ended: false, currentTime: 20, duration: 100, controls: true });
  const playbackCommands = [];
  video.play = () => { playbackCommands.push("play"); video.paused = false; video.dispatchEvent(new Event("play")); return Promise.resolve(); };
  video.pause = () => { playbackCommands.push("pause"); video.paused = true; video.dispatchEvent(new Event("pause")); };
  document.documentElement.append(video);
  document.fullscreenElement = fullscreen ? document.documentElement : null;
  let now = 1_000;
  const requests = [];
  const state = {
    expected: false,
    inlineMediaPlayerEnabled: true,
    inlineMediaPlayerMode: "button_inline_and_fullscreen",
    inlinePresentationExpected: true,
    presentedVideo: video,
    inlineControlsVisible: true,
    inlineControlsLastTouchToggleAt: -Infinity,
    inlineOpenRequest: { identity: "pending-existing-request" },
  };
  const labels = {
    inlineMediaPlayerActionLabel: "Open in Candy Player",
    inlineMediaPlayerPlayLabel: "Play",
    inlineMediaPlayerPauseLabel: "Pause",
    inlineMediaPlayerSeekLabel: "Seek",
    inlineMediaPlayerEnterFullscreenLabel: "Enter fullscreen",
    inlineMediaPlayerExitFullscreenLabel: "Exit fullscreen",
    inlineMediaPlayerCloseLabel: "Close Candy Player",
    inlineMediaPlayerShowControlsLabel: "Show controls",
    inlineMediaPlayerHideControlsLabel: "Hide controls",
  };
  Object.assign(state, labels);
  class Observer { observe() {} disconnect() {} }
  const frame = {};
  const context = vm.createContext({
    document, performance: { now: () => now }, self: frame, top: frame,
    boundedSafeAreaInteger: (_value, _minimum, _maximum, fallback) => fallback,
    innerWidth: 400, innerHeight: 800,
    getComputedStyle: () => ({ transform: "none" }),
    setTimeout: () => 1, clearTimeout: () => {},
    MutationObserver: Observer, ResizeObserver: Observer,
    candyPictureInPicturePlayback: state,
    candyInlineVideoOriginalControls: new WeakMap(),
    CANDY_INLINE_MEDIA_PLAYER_MODES: new Set([
      "button_fullscreen", "button_inline_and_fullscreen", "always_for_fullscreen", "automatic",
    ]),
    CANDY_INLINE_VIDEO_CONTROLS_HEIGHT_PX: 88,
    CANDY_INLINE_VIDEO_CONTROLS_ATTRIBUTE: "data-candy-inline-controls",
    CANDY_INLINE_VIDEO_ACTION_SIZE_PX: 56,
    CANDY_INLINE_CONTROLS_TAP_MAX_DURATION_MS: 350,
    CANDY_INLINE_FULLSCREEN_GESTURE_TOUCH_SLOP_PX: 10,
    CANDY_INLINE_FULLSCREEN_GESTURE_MIN_THRESHOLD_PX: 48,
    CANDY_INLINE_FULLSCREEN_GESTURE_MAX_THRESHOLD_PX: 96,
    CANDY_INLINE_FULLSCREEN_GESTURE_THRESHOLD_FRACTION: 0.13,
    setCandyInlineActionStyle: (element, key, value) => element.style.setProperty(key, value),
    CANDY_INLINE_FULLSCREEN_GESTURE_STICKY_FRACTION: 0.18,
    rememberCandyInlineVideoStableOrigin: () => {},
    reportCandyInlineVideoGestureHaptic: () => {},
    requestCandyInlineVideoFullscreen: () => { requests.push("fullscreen"); return Promise.resolve(); },
    requestCandyInlineVideoClose: () => requests.push("close"),
    clearCandyInlineVideoOpenRequest: () => { state.inlineOpenRequest = null; },
    removeCandyInlineVideoAction: () => {},
    suppressCandyInlineVideoSiteControls: () => {},
    clearCandyInlineVideoSiteControls: () => {},
    clearCandyInlineVideoFullscreenOrigin: () => {},
    startCandyInlineVideoStateObservation: () => {},
    reconcileCandyInlineVideoState: () => {},
    scheduleCandyInlineVideoStateReport: () => {},
    reportCandyInlineVideoState: () => {},
    isCandyInlineVideoCandidate: (element) => element?.isConnected,
  });
  const source = asset("content.js");
  for (const name of [
    "candyInlineFullscreenGestureDirection", "candyInlineFullscreenGestureUpdate",
    "candyInlineVideoCloseIsHiddenForMode", "candyInlineVideoCloseIsHidden",
    "candyInlineVideoTapIsEligible", "candyInlineVideoControlsVisibilityLabel",
    "candyInlineVideoControlsGestureMovement", "candyInlineVideoControlsClickIsActivation",
    "candyInlineVideoTime", "candyWobblyProgressPath", "candyInlineVideoControlsParent",
    "setCandyInlineFullscreenGestureOffset", "clearCandyInlineFullscreenGestureOffset",
    "removeCandyInlineVideoControlsOverlay", "resetCandyInlineVideoControlsVisibility",
    "positionCandyInlineVideoControls", "createCandyInlineVideoControlsOverlay",
    "updateCandyInlineVideoControlsOverlay", "updateCandyInlineMediaPlayerPolicy",
    "updateCandyInlineMediaPlayerEnabled", "clearCandyInlineVideoControls",
    "clearCandyInlineVideoPresentation", "presentCandyInlineVideo",
  ]) {
    const start = source.indexOf(`function ${name}(`);
    assert.ok(start >= 0, `production function ${name} exists`);
    const end = source.indexOf("\nfunction ", start + 1);
    vm.runInContext(source.slice(start, end), context);
  }
  context.createCandyInlineVideoControlsOverlay(video);
  const backgroundPolicy = asset("background.js")
    .split("function contentPolicy(policy) {")[1]
    .split("function publishContentPolicy(token, policy) {")[0];
  vm.runInContext(`function contentPolicy(policy) {${backgroundPolicy}`, context);
  function dispatch(element, type, properties = {}) {
    const event = new Event(type, { bubbles: true, cancelable: true });
    for (const [key, value] of Object.entries({
      isTrusted: true, isPrimary: true, pointerType: "touch", pointerId: 7,
      clientX: 200, clientY: 180, detail: 0, ...properties,
    })) Object.defineProperty(event, key, { value });
    for (let target = element; target; target = target.parentElement) {
      target.dispatchEvent(event);
      if (event.cancelBubble) break;
    }
    return event;
  }
  const element = (className) => state.inlineControlsHost.testShadow.querySelector(`.${className}`);
  const tap = () => {
    dispatch(element("fullscreen-gesture"), "pointerdown");
    now += 40;
    dispatch(element("fullscreen-gesture"), "pointerup");
  };
  return {
    context, document, state, video, playbackCommands, requests, element, dispatch, tap,
    advance: (millis) => { now += millis; },
    policy: (mode, overrides = {}) => context.updateCandyInlineMediaPlayerPolicy(context.contentPolicy({
      inlineMediaPlayerEnabled: true, inlineMediaPlayerMode: mode,
      revision: 4, navigationGeneration: 2, ...labels, ...overrides,
    })),
    newVideo: () => {
      const next = new Element("video");
      Object.assign(next, { paused: true, ended: false, currentTime: 0, duration: 60, controls: true });
      document.documentElement.append(next);
      return next;
    },
  };
}

for (const fullscreen of [false, true]) {
  test(`actual ${fullscreen ? "fullscreen" : "inline"} overlay tap toggles controls and hero without changing playback`, () => {
    const h = inlineControlsHarness({ fullscreen });
    assert.equal(h.element("hero-play").hidden, false);
    h.tap();
    assert.equal(h.state.inlineControlsVisible, false);
    assert.equal(h.element("controls").hidden, true);
    assert.equal(h.element("controls").inert, true);
    assert.equal(h.element("hero-play").hidden, true);
    assert.equal(h.element("stage").dataset.controlsHidden, "true");
    h.dispatch(h.element("fullscreen-gesture"), "click", { detail: 1 });
    h.dispatch(h.element("fullscreen-gesture"), "click", { detail: 0 });
    assert.equal(h.state.inlineControlsVisible, false, "touch compatibility clicks cannot toggle twice");
    h.tap();
    assert.equal(h.state.inlineControlsVisible, true);
    assert.equal(h.element("controls").hidden, false);
    assert.equal(h.element("controls").inert, false);
    assert.equal(h.element("hero-play").hidden, false);
    assert.equal(h.video.paused, true);
    assert.equal(h.video.currentTime, 20);
    assert.deepEqual(h.playbackCommands, []);
    assert.deepEqual(h.requests, []);
  });
}

test("actual overlay AT and keyboard activation toggles visibility with localized labels", () => {
  const h = inlineControlsHarness();
  h.policy("button_inline_and_fullscreen", {
    inlineMediaPlayerShowControlsLabel: "Steuerelemente anzeigen",
    inlineMediaPlayerHideControlsLabel: "Steuerelemente ausblenden",
  });
  assert.equal(h.element("fullscreen-gesture").getAttribute("aria-label"), "Steuerelemente ausblenden");
  h.dispatch(h.element("fullscreen-gesture"), "click", { detail: 0 });
  assert.equal(h.element("controls").hidden, true);
  assert.equal(h.element("fullscreen-gesture").getAttribute("aria-label"), "Steuerelemente anzeigen");
  h.dispatch(h.element("fullscreen-gesture"), "keydown", { key: "Enter" });
  assert.equal(h.element("controls").hidden, false);
  h.dispatch(h.element("fullscreen-gesture"), "keydown", { key: " ", repeat: true });
  assert.equal(h.element("controls").hidden, false, "held key cannot flicker controls");
  h.dispatch(h.element("fullscreen-gesture"), "keydown", { key: " " });
  assert.equal(h.element("controls").hidden, true);
  h.dispatch(h.element("fullscreen-gesture"), "keydown", { key: "Enter", isTrusted: false });
  h.dispatch(h.element("fullscreen-gesture"), "click", { isTrusted: false });
  assert.equal(h.element("controls").hidden, true);
  assert.deepEqual(h.playbackCommands, []);
});

test("actual overlay rejects cancelled pointers, foreign pointers, long taps and drag-return in fullscreen", () => {
  for (const kind of ["pointercancel", "lostpointercapture", "foreign", "long", "drag", "untrusted"]) {
    const h = inlineControlsHarness({ fullscreen: true });
    const surface = h.element("fullscreen-gesture");
    h.dispatch(surface, "pointerdown");
    if (kind === "pointercancel" || kind === "lostpointercapture") h.dispatch(surface, kind);
    if (kind === "long") h.advance(351);
    if (kind === "drag") {
      h.dispatch(surface, "pointermove", { clientY: 250 });
      h.dispatch(surface, "pointermove", { clientY: 180 });
    }
    h.dispatch(surface, "pointerup", {
      pointerId: kind === "foreign" ? 8 : 7,
      isTrusted: kind !== "untrusted",
    });
    assert.equal(h.state.inlineControlsVisible, true, kind);
    assert.equal(h.element("controls").hidden, false, kind);
    assert.deepEqual(h.playbackCommands, [], kind);
    assert.deepEqual(h.requests, [], kind);
  }
});

test("actual overlay rejects nonprimary and non-touch streams while playing", () => {
  const h = inlineControlsHarness({ paused: false });
  const surface = h.element("fullscreen-gesture");
  for (const properties of [
    { isTrusted: false }, { isPrimary: false }, { pointerType: "mouse" },
    { pointerType: "pen" }, { pointerType: "" },
  ]) {
    h.dispatch(surface, "pointerdown", properties);
    h.dispatch(surface, "pointerup", properties);
    assert.equal(h.state.inlineControlsVisible, true);
    assert.equal(h.element("controls").hidden, false);
  }
  h.tap();
  assert.equal(h.element("controls").hidden, true);
  h.tap();
  assert.equal(h.element("controls").hidden, false);
  assert.equal(h.element("hero-play").hidden, true);
  assert.equal(h.video.paused, false);
  assert.equal(h.video.currentTime, 20);
  assert.deepEqual(h.playbackCommands, []);
});

test("actual buttons, seek and fullscreen swipe act without free-surface toggles", () => {
  const h = inlineControlsHarness();
  for (const name of ["play-pause", "hero-play", "fullscreen", "close", "timeline"]) {
    h.dispatch(h.element(name), "pointerdown");
    h.dispatch(h.element(name), "pointerup");
    assert.equal(h.state.inlineControlsVisible, true, name);
  }
  h.dispatch(h.element("play-pause"), "click");
  assert.equal(h.video.paused, false);
  assert.equal(h.state.inlineControlsVisible, true);
  const seek = h.element("timeline").children.find((child) => child.tagName === "input");
  seek.value = "650";
  h.dispatch(seek, "input");
  assert.equal(h.video.currentTime, 65);
  assert.equal(h.state.inlineControlsVisible, true);
  const surface = h.element("fullscreen-gesture");
  h.dispatch(surface, "pointerdown");
  h.dispatch(surface, "pointermove", { clientY: 50 });
  h.dispatch(surface, "pointerup", { clientY: 50 });
  assert.deepEqual(h.requests, ["fullscreen"]);
  assert.equal(h.state.inlineControlsVisible, true);
  assert.equal(h.video.style.getPropertyValue("transform"), "");
});

test("production policy refresh applies every close mode without reopening or replacing presentation", () => {
  const h = inlineControlsHarness();
  const initialHost = h.state.inlineControlsHost;
  const request = h.state.inlineOpenRequest;
  for (const [mode, hidden] of [
    ["automatic", true], ["button_fullscreen", false],
    ["always_for_fullscreen", true], ["button_inline_and_fullscreen", false],
  ]) {
    h.policy(mode);
    const close = h.element("close");
    assert.equal(close.hidden, hidden, mode);
    assert.equal(close.style.getPropertyValue("display"), hidden ? "none" : "grid", mode);
    assert.equal(close.tabIndex, hidden ? -1 : 0, mode);
    assert.equal(close.getAttribute("aria-hidden"), String(hidden), mode);
    assert.equal(h.element("fullscreen").hidden, false, "fullscreen exit stays available");
    assert.equal(h.state.inlineControlsHost, initialHost);
    assert.equal(h.state.presentedVideo, h.video);
    assert.equal(h.state.inlineOpenRequest, request);
    assert.deepEqual(h.requests, []);
  }
});

test("PiP overlay recreation and policy refresh retain visibility; presentation cleanup and replacement reset it", () => {
  const h = inlineControlsHarness();
  h.tap();
  const firstHost = h.state.inlineControlsHost;
  h.state.expected = true;
  h.context.updateCandyInlineVideoControlsOverlay(h.video);
  assert.equal(h.state.inlineControlsHost, null);
  assert.equal(firstHost.isConnected, false);
  assert.equal(h.state.inlineControlsVisible, false);
  h.state.expected = false;
  h.context.updateCandyInlineVideoControlsOverlay(h.video);
  assert.notEqual(h.state.inlineControlsHost, firstHost);
  assert.equal(h.element("controls").hidden, true);
  h.policy("automatic");
  assert.equal(h.element("controls").hidden, true);
  assert.equal(h.element("close").hidden, true);
  h.policy("automatic", { inlineMediaPlayerPlayLabel: "Abspielen" });
  assert.equal(h.element("controls").hidden, true, "localized overlay recreation keeps choice");
  h.context.clearCandyInlineVideoPresentation();
  assert.equal(h.state.inlineControlsVisible, true);
  assert.equal(h.state.presentedVideo, null);
  h.context.presentCandyInlineVideo(h.video);
  assert.equal(h.element("controls").hidden, false);
  h.tap();
  const replacement = h.newVideo();
  h.context.presentCandyInlineVideo(replacement);
  assert.equal(h.state.presentedVideo, replacement);
  assert.equal(h.element("controls").hidden, false);
  assert.equal(h.element("hero-play").hidden, false);
});

test("inline free-surface tap toggles controls only for trusted primary touch", () => {
  const context = vm.createContext({ performance: { now: () => 100 } });
  const assetSource = asset("content.js");
  const source = assetSource
    .split("function candyInlineVideoTapIsEligible(event, gesture) {")[1]
    .split("function candyInlineVideoNonce()")[0];
  const movementSource = assetSource
    .split("function candyInlineVideoControlsGestureMovement(gesture, event) {")[1]
    .split("function candyInlineVideoNonce()")[0];
  vm.runInContext(
    `const CANDY_INLINE_CONTROLS_TAP_MAX_DURATION_MS = 350;\n` +
      `const CANDY_INLINE_FULLSCREEN_GESTURE_TOUCH_SLOP_PX = 10;\n` +
      `function candyInlineVideoTapIsEligible(event, gesture) {${source}` +
      `function candyInlineVideoControlsGestureMovement(gesture, event) {${movementSource}`,
    context,
  );
  const pending = {
    direction: "pending", startedAt: 0, startX: 10, startY: 20, maxMovement: 0,
  };
  const trustedTouch = {
    isTrusted: true, isPrimary: true, pointerType: "touch", clientX: 12, clientY: 22,
  };
  assert.equal(context.candyInlineVideoTapIsEligible(trustedTouch, pending), true);
  assert.equal(
    context.candyInlineVideoTapIsEligible({ ...trustedTouch, isTrusted: false }, pending),
    false,
  );
  assert.equal(
    context.candyInlineVideoTapIsEligible({ ...trustedTouch, isPrimary: false }, pending),
    false,
  );
  assert.equal(
    context.candyInlineVideoTapIsEligible({ ...trustedTouch, pointerType: "mouse" }, pending),
    false,
  );
  assert.equal(
    context.candyInlineVideoTapIsEligible(trustedTouch, { direction: "up" }),
    false,
  );
  assert.equal(
    context.candyInlineVideoTapIsEligible(trustedTouch, { ...pending, startedAt: -351 }),
    false,
  );
  const moved = { ...pending };
  context.candyInlineVideoControlsGestureMovement(moved, { clientX: 21, clientY: 20 });
  assert.equal(moved.maxMovement, 11);
  assert.equal(context.candyInlineVideoTapIsEligible(trustedTouch, moved), false);
  const clickSource = assetSource
    .split("function candyInlineVideoControlsClickIsActivation(event, now, lastTouchToggleAt) {")[1]
    .split("function candyInlineVideoNonce()")[0];
  vm.runInContext(
    `function candyInlineVideoControlsClickIsActivation(event, now, lastTouchToggleAt) {${clickSource}`,
    context,
  );
  assert.equal(
    context.candyInlineVideoControlsClickIsActivation({ isTrusted: true, detail: 0 }, 1_000, 0),
    true,
    "assistive technology click activates controls",
  );
  assert.equal(
    context.candyInlineVideoControlsClickIsActivation({ isTrusted: true, detail: 0 }, 200, 0),
    false,
    "synthetic click immediately following touch is suppressed",
  );
  assert.equal(
    context.candyInlineVideoControlsClickIsActivation({ isTrusted: true, detail: 1 }, 1_000, 0),
    false,
  );
});

test("inline controls hide close for modes that immediately reopen", () => {
  const source = asset("content.js");
  const context = vm.createContext({});
  const closeSource = source
    .split("function candyInlineVideoCloseIsHiddenForMode(mode) {")[1]
    .split("function candyInlineVideoCloseIsHidden() {")[0];
  vm.runInContext(
    `function candyInlineVideoCloseIsHiddenForMode(mode) {${closeSource}` +
      `function candyInlineVideoCloseIsHidden() {` +
      source.split("function candyInlineVideoCloseIsHidden() {")[1]
        .split("function candyInlineVideoTapIsEligible")[0],
    context,
  );
  context.candyPictureInPicturePlayback = { inlineMediaPlayerMode: "button_fullscreen" };
  for (const [mode, hidden] of [
    ["button_fullscreen", false],
    ["button_inline_and_fullscreen", false],
    ["always_for_fullscreen", true],
    ["automatic", true],
  ]) {
    assert.equal(context.candyInlineVideoCloseIsHiddenForMode(mode), hidden);
    context.candyPictureInPicturePlayback.inlineMediaPlayerMode = mode;
    assert.equal(context.candyInlineVideoCloseIsHidden(), hidden);
  }
});

test("inline gesture state rejects drag-return, cancellation, and long touch", () => {
  const source = asset("content.js");
  const context = vm.createContext({ performance: { now: () => 350 } });
  const tapSource = source
    .split("function candyInlineVideoTapIsEligible(event, gesture) {")[1]
    .split("function candyInlineVideoNonce()")[0];
  const movementSource = source
    .split("function candyInlineVideoControlsGestureMovement(gesture, event) {")[1]
    .split("function candyInlineVideoNonce()")[0];
  vm.runInContext(
    `const CANDY_INLINE_CONTROLS_TAP_MAX_DURATION_MS = 350;\n` +
      `const CANDY_INLINE_FULLSCREEN_GESTURE_TOUCH_SLOP_PX = 10;\n` +
      `function candyInlineVideoTapIsEligible(event, gesture) {${tapSource}` +
      `function candyInlineVideoControlsGestureMovement(gesture, event) {${movementSource}`,
    context,
  );
  const event = { isTrusted: true, isPrimary: true, pointerType: "touch", clientX: 0, clientY: 0 };
  const gesture = { direction: "pending", startedAt: 0, startX: 0, startY: 0, maxMovement: 0 };
  context.candyInlineVideoControlsGestureMovement(gesture, { clientX: 11, clientY: 0 });
  context.candyInlineVideoControlsGestureMovement(gesture, { clientX: 1, clientY: 0 });
  assert.equal(gesture.maxMovement, 11, "drag returning to origin stays a drag");
  assert.equal(context.candyInlineVideoTapIsEligible(event, gesture), false);
  gesture.maxMovement = 0;
  assert.equal(context.candyInlineVideoTapIsEligible(event, gesture), true);
  gesture.direction = "cancelled";
  assert.equal(context.candyInlineVideoTapIsEligible(event, gesture), false);
  gesture.direction = "pending";
  context.performance.now = () => 351;
  assert.equal(context.candyInlineVideoTapIsEligible(event, gesture), false);
  for (const pointerType of ["pen", ""]) {
    context.performance.now = () => 100;
    assert.equal(
      context.candyInlineVideoTapIsEligible({ ...event, pointerType }, gesture),
      false,
    );
  }
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
  assert.match(source, /scheduleCandyInlineVideoFullscreenOriginReconciliation/);
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
  assert.match(source, /Promise\.resolve\(target\.requestFullscreen\(\)\)/);
  assert.match(source, /function candyInlineFullscreenGestureUpdate\(/);
  assert.match(source, /type: "inline-video-gesture-haptic"/);
  assert.match(source, /requestCandyInlineVideoFullscreen\(video\)/);
  assert.match(source, /document\.exitFullscreen\(\)/);
  assert.match(source, /candyInlineVideoControlsParent\(video\)/);
  assert.match(source, /parent\.appendChild\(candyPictureInPicturePlayback\.inlineControlsHost\)/);
  assert.match(source, /removeCandyInlineVideoControlsOverlay\(\)/);
  assert.match(
    source,
    /!candyPictureInPicturePlayback\.presentedVideo\?\.isConnected/,
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

test("fullscreen exit retains acknowledged Candy presentation while explicit cleanup remains scoped", () => {
  const source = asset("content.js");
  const fullscreenChangeSource = source
    .split('document.addEventListener("fullscreenchange", () => {')[1]
    .split('document.addEventListener("transitionend"')[0];

  assert.doesNotMatch(fullscreenChangeSource, /clearCandyInlineVideoPresentation\(\)/);
  assert.match(source, /function requestCandyInlineVideoClose\(video\)[\s\S]*?clearCandyInlineVideoPresentation\(\)/);
  assert.match(source, /window\.addEventListener\("pagehide"[\s\S]*?clearCandyInlineVideoPresentation\(\)/);
  assert.match(
    source,
    /if \(!normalized\) \{[\s\S]*?clearCandyInlineVideoPresentation\(\)/,
  );
});

test("fullscreen exit keeps acknowledged Candy controls active", () => {
  const video = { isConnected: true };
  let fullscreenChange = null;
  let overlayUpdates = 0;
  const context = vm.createContext({
    candyPictureInPicturePlayback: {
      expected: false,
      inlineMediaPlayerEnabled: false,
      inlinePresentationExpected: true,
      presentedVideo: video,
    },
    document: {
      fullscreenElement: null,
      addEventListener: (type, listener) => {
        if (type === "fullscreenchange") fullscreenChange = listener;
      },
    },
    window: { dispatchEvent: () => {} },
    Event: class {},
    requestAnimationFrame: (callback) => callback(),
    updateCandyInlineVideoFullscreenOriginVisibility: () => {},
    restoreCandyInlineVideoFullscreenOrigin: () => {},
    scheduleCandyPictureInPictureAlignment: () => {},
    suppressCandyInlineVideoSiteControls: () => {},
    updateCandyInlineVideoControlsOverlay: (candidate) => {
      assert.equal(candidate, video);
      overlayUpdates += 1;
    },
  });
  const source = asset("content.js")
    .split('document.addEventListener("fullscreenchange", () => {')[1]
    .split('document.addEventListener("transitionend"')[0];
  vm.runInContext(`document.addEventListener("fullscreenchange", () => {${source}`, context);

  fullscreenChange();
  assert.equal(context.candyPictureInPicturePlayback.inlinePresentationExpected, true);
  assert.equal(context.candyPictureInPicturePlayback.presentedVideo, video);
  assert.equal(overlayUpdates, 2);
});
