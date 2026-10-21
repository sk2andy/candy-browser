import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL(
  '../app/src/main/java/dev/sk2andy/materialbrowser/browser/VideoAutoplayBlockerScript.kt',
  import.meta.url,
), 'utf8');
const script = source
  .split('val installScript: String = """\n')[1]
  .split('\n    """.trimIndent()')[0]
  .replaceAll("${'$'}", '$')
  .replaceAll('$CLEANUP_KEY', '__candyVideoAutoplayBlockerCleanup')
  .replaceAll('$CLEANUP_MESSAGE', '__candyVideoAutoplayBlockerCleanupMessage');

function blockerHarness() {
  const listeners = new Map();
  const microtasks = [];
  const playbackEvents = [];
  let mutationCallback;
  let now = 100;

  class Element {
    nodeType = 1;
    children = [];

    querySelectorAll(selector) {
      assert.equal(selector, 'video[autoplay]');
      return this.children.filter(child => child instanceof HTMLVideoElement && child.autoplay);
    }
  }

  class HTMLMediaElement extends Element {
    paused = true;
    pauseCount = 0;

    play() {
      this.paused = false;
      playbackEvents.push(() => listeners.get('play')?.({ target: this }));
      return Promise.resolve();
    }

    pause() {
      this.paused = true;
      this.pauseCount++;
    }
  }

  class HTMLVideoElement extends HTMLMediaElement {
    autoplay = false;
  }

  const context = vm.createContext({
    Element,
    HTMLMediaElement,
    HTMLVideoElement,
    Node: { ELEMENT_NODE: 1 },
    document: new Element(),
    MutationObserver: class {
      constructor(callback) { mutationCallback = callback; }
      observe() {}
      disconnect() {}
    },
    navigator: { userActivation: { isActive: true } },
    performance: { now: () => now },
    queueMicrotask: callback => microtasks.push(callback),
    DOMException,
    frames: [],
    addEventListener: (type, listener) => listeners.set(type, listener),
    removeEventListener: type => listeners.delete(type),
  });
  context.window = context;
  vm.runInContext(script, context);

  return {
    video: () => new HTMLVideoElement(),
    async playFromClick(video) {
      listeners.get('click')({
        type: 'click',
        isTrusted: true,
        composedPath: () => [video],
      });
      await video.play();
      while (microtasks.length) microtasks.shift()();
      while (playbackEvents.length) playbackEvents.shift()();
      now += 2_000;
      assert.equal(video.paused, false, 'Trusted playback must start before DOM changes');
    },
    add(video, wrapped = false) {
      const root = wrapped ? new Element() : video;
      if (wrapped) root.children.push(video);
      mutationCallback([{ type: 'childList', addedNodes: [root] }]);
    },
    changeAutoplay(video) {
      mutationCallback([{ type: 'attributes', target: video, attributeName: 'autoplay' }]);
    },
  };
}

test('moving manually playing video directly preserves playback', async () => {
  const h = blockerHarness();
  const video = h.video();
  await h.playFromClick(video);

  h.add(video);

  assert.equal(video.paused, false);
  assert.equal(video.pauseCount, 0);
});

test('moving manually playing video inside player preserves playback', async () => {
  const h = blockerHarness();
  const video = h.video();
  await h.playFromClick(video);

  h.add(video, true);

  assert.equal(video.paused, false);
  assert.equal(video.pauseCount, 0);
});

test('removing autoplay from manually playing video preserves playback', async () => {
  const h = blockerHarness();
  const video = h.video();
  await h.playFromClick(video);
  video.autoplay = false;

  h.changeAutoplay(video);

  assert.equal(video.paused, false);
  assert.equal(video.pauseCount, 0);
});

for (const wrapped of [false, true]) {
  test(`new autoplay video ${wrapped ? 'inside player' : 'added directly'} is stopped`, () => {
    const h = blockerHarness();
    const video = h.video();
    video.autoplay = true;
    video.paused = false;

    h.add(video, wrapped);

    assert.equal(video.autoplay, false);
    assert.equal(video.paused, true);
    assert.equal(video.pauseCount, 1);
  });
}

test('enabling autoplay stops video but later removal record preserves explicit restart', async () => {
  const h = blockerHarness();
  const video = h.video();
  video.autoplay = true;
  video.paused = false;
  h.changeAutoplay(video);
  assert.equal(video.autoplay, false);
  assert.equal(video.paused, true);
  assert.equal(video.pauseCount, 1);
  await h.playFromClick(video);

  h.changeAutoplay(video);

  assert.equal(video.paused, false);
  assert.equal(video.pauseCount, 1);
});
