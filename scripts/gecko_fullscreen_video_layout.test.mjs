import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import vm from "node:vm";

const source = readFileSync(new URL(
  "../app/src/gecko/assets/candy_privacy/content.js", import.meta.url,
), "utf8");

function harness() {
  const element = (parentElement = null) => ({
    parentElement,
    isConnected: true,
    attributes: new Map(),
    setAttribute(name, value) { this.attributes.set(name, value); },
    removeAttribute(name) { this.attributes.delete(name); },
  });
  const root = element();
  const parent = element(root);
  const video = element(parent);
  video.authoredStyle = "height:56.25vw;transform:translateZ(0)";
  root.contains = (candidate) => candidate === video;
  root.querySelector = () => video;
  const styles = [];
  const document = {
    fullscreenElement: root,
    documentElement: { appendChild(style) { styles.push(style); } },
    createElement: () => ({ removed: false, remove() { this.removed = true; } }),
  };
  const state = { inlineMediaPlayerEnabled: true, presentedVideo: video, fullscreenVideoLayout: null };
  const context = vm.createContext({
    document,
    candyPictureInPicturePlayback: state,
    CANDY_FULLSCREEN_VIDEO_ATTRIBUTE: "data-candy-fullscreen-video",
    CANDY_FULLSCREEN_VIDEO_ANCESTOR_ATTRIBUTE: "data-candy-fullscreen-video-ancestor",
    CANDY_FULLSCREEN_VIDEO_ROOT_ATTRIBUTE: "data-candy-fullscreen-video-root",
  });
  vm.runInContext(
    "function clearCandyFullscreenVideoLayout() {" + source
      .split("function clearCandyFullscreenVideoLayout() {")[1]
      .split("function updateCandyInlineVideoFullscreenOriginVisibility() {")[0],
    context,
  );
  return { context, document, state, root, parent, video, styles, element };
}

test("fullscreen layout stays scoped to exact video and clears authored inline styles unchanged", () => {
  const h = harness();
  h.context.updateCandyFullscreenVideoLayout();
  assert.equal(h.styles.length, 1);
  assert.equal(h.video.attributes.has("data-candy-fullscreen-video"), true);
  assert.match(h.styles[0].textContent, /:fullscreen video\[data-candy-fullscreen-video\]/);
  h.context.updateCandyFullscreenVideoLayout();
  assert.equal(h.styles.length, 1, "repeated reconciliation must keep same layout owner");
  h.document.fullscreenElement = null;
  h.context.updateCandyFullscreenVideoLayout();
  assert.equal(h.state.fullscreenVideoLayout, null);
  assert.equal(h.video.attributes.size, 0);
  assert.equal(h.parent.attributes.size, 0);
  assert.equal(h.root.attributes.size, 0);
  assert.equal(h.styles[0].removed, true);
  assert.equal(h.video.authoredStyle, "height:56.25vw;transform:translateZ(0)");
});

test("disabled player and direct video fullscreen leave site presentation untouched", () => {
  for (const directVideo of [false, true]) {
    const h = harness();
    if (directVideo) {
      h.document.fullscreenElement = h.video;
      h.video.contains = (candidate) => candidate === h.video;
    } else h.state.inlineMediaPlayerEnabled = false;
    h.context.updateCandyFullscreenVideoLayout();
    assert.equal(h.styles.length, 0);
    assert.equal(h.video.attributes.size, 0);
  }
});

test("replacement video releases stale fullscreen layout before acquiring new video", () => {
  const h = harness();
  h.context.updateCandyFullscreenVideoLayout();
  const replacement = h.element(h.parent);
  h.root.contains = (candidate) => candidate === replacement;
  h.root.querySelector = () => replacement;
  h.state.presentedVideo = replacement;
  h.context.updateCandyFullscreenVideoLayout();
  assert.equal(h.video.attributes.size, 0);
  assert.equal(h.styles[0].removed, true);
  assert.equal(replacement.attributes.has("data-candy-fullscreen-video"), true);
  h.state.inlineMediaPlayerEnabled = false;
  h.context.updateCandyFullscreenVideoLayout();
  assert.equal(replacement.attributes.size, 0);
  assert.equal(h.styles[1].removed, true);
});
