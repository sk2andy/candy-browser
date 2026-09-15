import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const asset = (name) => fs.readFileSync(
  new URL(`../app/src/gecko/assets/candy_privacy/${name}`, import.meta.url),
  "utf8",
);

class FakeElement {
  constructor({
    tagName = "TEXTAREA",
    type = "",
    rect = { left: 100, top: 850, right: 900, bottom: 930, width: 800, height: 80 },
    attributes = {},
    style = {},
    isContentEditable = false,
    parentElement = null,
  } = {}) {
    this.tagName = tagName;
    this.type = type;
    this.rect = rect;
    this.attributes = attributes;
    this.style = {
      display: "block",
      visibility: "visible",
      opacity: "1",
      pointerEvents: "auto",
      ...style,
    };
    this.isContentEditable = isContentEditable;
    this.parentElement = parentElement;
    this.shadowRoot = null;
  }

  matches(selector) {
    if (selector === ":disabled") return "disabled" in this.attributes;
    if (selector === "textarea,input,[contenteditable]") {
      return this.tagName === "TEXTAREA" || this.tagName === "INPUT" ||
        "contenteditable" in this.attributes;
    }
    return selector.split(",").some((part) => {
      const attribute = part.trim().slice(1, -1);
      if (attribute.includes("=")) {
        const [name, rawValue] = attribute.split("=");
        return this.attributes[name] === rawValue.replaceAll('"', "");
      }
      return attribute in this.attributes;
    });
  }

  getAttribute(name) {
    return this.attributes[name] ?? null;
  }

  getBoundingClientRect() {
    return this.rect;
  }

  getRootNode() {
    return { host: null };
  }
}

function probeHarness({ element, scrollHeight = 1000, scrollTop = 0, visualViewport } = {}) {
  const root = { clientHeight: 1000, scrollHeight, scrollTop };
  const elements = element ? [element] : [];
  const document = {
    body: { scrollHeight },
    documentElement: { clientHeight: 1000, clientWidth: 1000, scrollHeight },
    scrollingElement: root,
    createTreeWalker: () => {
      let index = 0;
      return { nextNode: () => elements[index++] || null };
    },
    elementFromPoint: () => elements[0] || null,
    elementsFromPoint: () => elements,
  };
  const context = vm.createContext({
    document,
    Element: FakeElement,
    NodeFilter: { SHOW_ELEMENT: 1 },
    innerHeight: 1000,
    innerWidth: 1000,
    scrollY: scrollTop,
    visualViewport,
    getComputedStyle: (candidate) => candidate.style,
  });
  vm.runInContext(asset("text_input_occlusion.js"), context);
  return (rect = { left: 0.1, top: 0.8, right: 0.9, bottom: 0.95 }) =>
    context.CandyTextInputOcclusion.probe(rect);
}

test("parks only for overlapping visible text editor at document bottom", () => {
  assert.equal(probeHarness({ element: new FakeElement() })(), true);
  assert.equal(probeHarness({ element: new FakeElement(), scrollHeight: 1100 })(), false);
  assert.equal(probeHarness({
    element: new FakeElement({
      rect: { left: 100, top: 700, right: 900, bottom: 790, width: 800, height: 90 },
    }),
  })(), false);
});

test("accepts plaintext contenteditable and rejects disabled hidden or non-text input", () => {
  assert.equal(probeHarness({
    element: new FakeElement({
      tagName: "DIV",
      attributes: { contenteditable: "plaintext-only" },
      isContentEditable: true,
    }),
  })(), true);
  assert.equal(probeHarness({
    element: new FakeElement({ attributes: { disabled: "" } }),
  })(), false);
  assert.equal(probeHarness({
    element: new FakeElement({ style: { opacity: "0.0" } }),
  })(), false);
  assert.equal(probeHarness({
    element: new FakeElement({ tagName: "INPUT", type: "checkbox" }),
  })(), false);
});

test("maps normalized chrome bounds through visual viewport offset", () => {
  const element = new FakeElement({
    rect: { left: 250, top: 500, right: 650, bottom: 560, width: 400, height: 60 },
  });
  const probe = probeHarness({
    element,
    scrollHeight: 1000,
    visualViewport: {
      width: 500,
      height: 500,
      offsetLeft: 200,
      offsetTop: 100,
      pageTop: 500,
    },
  });

  assert.equal(probe({ left: 0, top: 0.8, right: 1, bottom: 1 }), true);
});

test("probe is one-shot and contains no observer or timer", () => {
  const source = asset("text_input_occlusion.js");

  assert.doesNotMatch(source, /MutationObserver|ResizeObserver|setTimeout|setInterval/);
  assert.match(source, /rootIndex < 64/);
  assert.match(source, /visitedElements < 4096/);
});

function backgroundHarness(policy) {
  const sent = [];
  const posted = [];
  let complete;
  const source = asset("background.js");
  const start = source.indexOf("function normalizedViewportRect");
  const end = source.indexOf("function updatePictureInPicturePlayback");
  const context = vm.createContext({
    PROTOCOL_VERSION: 2,
    policiesByToken: new Map([["token", policy]]),
    tokenByTab: new Map([[7, "token"]]),
    nativePort: { postMessage: (message) => posted.push(message) },
    browser: {
      tabs: {
        sendMessage: (...args) => {
          sent.push(args);
          return new Promise((resolve) => { complete = resolve; });
        },
      },
    },
  });
  vm.runInContext(source.slice(start, end), context);
  return { context, sent, posted, complete: (value) => complete(value) };
}

test("background binds probe to top frame policy and navigation", async () => {
  const policy = { revision: 4, navigationGeneration: 9 };
  const request = {
    token: "token",
    revision: 4,
    navigationGeneration: 9,
    requestId: 3,
    viewportRect: { left: 0.1, top: 0.8, right: 0.9, bottom: 0.95 },
  };
  const harness = backgroundHarness(policy);

  harness.context.probeTextInputOcclusion(request);
  assert.equal(harness.sent.length, 1);
  assert.equal(harness.sent[0][2].frameId, 0);
  harness.complete(true);
  await Promise.resolve();
  assert.equal(harness.posted[0].type, "text-input-occlusion-result");
  assert.equal(harness.posted[0].occluded, true);
});

test("background rejects malformed or stale probe", () => {
  const policy = { revision: 4, navigationGeneration: 9 };
  const request = {
    token: "token",
    revision: 4,
    navigationGeneration: 9,
    requestId: 3,
    viewportRect: { left: 0.1, top: 0.8, right: 0.9, bottom: 0.95 },
  };
  for (const invalid of [
    { ...request, revision: 3 },
    { ...request, navigationGeneration: 8 },
    { ...request, requestId: 1.5 },
    { ...request, viewportRect: { left: 0.9, top: 0.8, right: 0.1, bottom: 0.95 } },
  ]) {
    const harness = backgroundHarness(policy);
    harness.context.probeTextInputOcclusion(invalid);
    assert.equal(harness.sent.length, 0);
  }
});
