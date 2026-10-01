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
    readOnly = false,
  } = {}) {
    this.readOnly = readOnly;
    this.tabIndex = Number(attributes.tabindex ?? -1);
    this.tagName = tagName;
    this.type = type;
    this.rect = rect;
    this.attributes = attributes;
    this.style = {
      position: "static",
      bottom: "auto",
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
    return selector.split(",").some((part) => {
      const token = part.trim();
      const match = token.match(/^(\w+)?(?:\[([^=\]]+)(?:="([^"\]]+)")?\])?$/);
      if (!match) return false;
      const [, tag, name, value] = match;
      return (!tag || this.tagName === tag.toUpperCase()) &&
        (!name || (name in this.attributes && (value === undefined || this.attributes[name] === value)));
    });
  }

  hasAttribute(name) {
    return name in this.attributes;
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

function probeHarness({
  element,
  focused = false,
  scrollHeight = 1000,
  scrollTop = 0,
  visualViewport,
  hitElement = element,
  scannedElements = element ? [element] : [],
} = {}) {
  const root = { clientHeight: 1000, scrollHeight, scrollTop };
  const elements = scannedElements;
  const document = {
    body: { scrollHeight },
    documentElement: { clientHeight: 1000, clientWidth: 1000, scrollHeight },
    scrollingElement: root,
    activeElement: focused ? element : null,
    createTreeWalker: () => {
      let index = 0;
      return { nextNode: () => elements[index++] || null };
    },
    elementFromPoint: () => hitElement || null,
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
  return (
    rect = { left: 0.1, top: 0.8, right: 0.9, bottom: 0.95 },
    focusedOnly = false,
  ) => context.CandyTextInputOcclusion.probe(rect, focusedOnly);
}

test("parks for overlapping visible editor anywhere in document", () => {
  assert.equal(probeHarness({ element: new FakeElement() })(), 2);
  assert.equal(probeHarness({ element: new FakeElement(), scrollHeight: 1100 })(), 2);
  assert.equal(probeHarness({
    element: new FakeElement({
      rect: { left: 100, top: 700, right: 900, bottom: 790, width: 800, height: 90 },
    }),
  })(), 0);
});

test("accepts plaintext editor and checkboxes but rejects disabled or hidden input", () => {
  assert.equal(probeHarness({
    element: new FakeElement({
      tagName: "DIV",
      attributes: { contenteditable: "plaintext-only" },
      isContentEditable: true,
    }),
  })(), 2);
  assert.equal(probeHarness({
    element: new FakeElement({ attributes: { disabled: "" } }),
  })(), 0);
  assert.equal(probeHarness({
    element: new FakeElement({ style: { opacity: "0.0" } }),
  })(), 0);
  assert.equal(probeHarness({
    element: new FakeElement({ tagName: "INPUT", type: "checkbox" }),
  })(), 2);
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

  assert.equal(probe({ left: 0, top: 0.8, right: 1, bottom: 1 }), 2);
});

test("focused probe starts only for text editor and follows movement", () => {
  const textarea = new FakeElement({
    rect: { left: 100, top: 700, right: 900, bottom: 790, width: 800, height: 90 },
  });
  const probe = probeHarness({ element: textarea, focused: true, scrollHeight: 2000 });

  assert.equal(probe(undefined, true), 1);
  textarea.rect = { left: 100, top: 960, right: 900, bottom: 1040, width: 800, height: 80 };
  assert.equal(probe(undefined, true), 2);
  assert.equal(probeHarness({ element: textarea })(undefined, true), 0);
  assert.equal(probeHarness({
    element: new FakeElement({
      tagName: "DIV",
      attributes: { contenteditable: "true" },
      isContentEditable: true,
    }),
    focused: true,
  })(undefined, true), 2);
});

test("focused probe accepts deeply nested editor below chrome", () => {
  const editor = new FakeElement({
    tagName: "DIV",
    attributes: { contenteditable: "true" },
    isContentEditable: true,
    rect: { left: 100, top: 960, right: 900, bottom: 1040, width: 800, height: 80 },
  });
  let current = editor;
  for (let depth = 0; depth < 28; depth += 1) {
    current.parentElement = new FakeElement({ tagName: "DIV" });
    current = current.parentElement;
  }
  const probe = probeHarness({ element: editor, focused: true });

  assert.equal(probe(undefined, true), 2);
  assert.equal(probe(), 1);
  current.style.opacity = "0";
  assert.equal(probe(undefined, true), 0);
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
    focusedOnly: true,
    viewportRect: { left: 0.1, top: 0.8, right: 0.9, bottom: 0.95 },
  };
  const harness = backgroundHarness(policy);

  harness.context.probeTextInputOcclusion(request);
  assert.equal(harness.sent.length, 1);
  assert.equal(harness.sent[0][2].frameId, 0);
  assert.equal(harness.sent[0][1].focusedOnly, true);
  harness.complete(2);
  await Promise.resolve();
  assert.equal(harness.posted[0].type, "text-input-occlusion-result");
  assert.equal(harness.posted[0].result, 2);
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


test("fixed and sticky bottom navigation park before document bottom without focus", () => {
  for (const position of ["fixed", "sticky"]) {
    const navigation = new FakeElement({ tagName: "NAV", style: { position, bottom: "12px" } });
    const link = new FakeElement({ tagName: "A", attributes: { href: "/inbox" }, parentElement: navigation });
    assert.equal(probeHarness({ element: link, scrollHeight: 5000 })(), 2);
  }
});

test("bottom anchor geometry catches auto bottom and ignores top anchored controls", () => {
  const button = new FakeElement({ tagName: "BUTTON", style: { position: "fixed" } });
  assert.equal(probeHarness({ element: button, scrollHeight: 5000 })(), 2);
  button.rect = { left: 100, top: 20, right: 900, bottom: 100, width: 800, height: 80 };
  assert.equal(probeHarness({ element: button, scrollHeight: 5000 })(), 0);
});

test("ordinary flowing buttons need document bottom and decorative fixed footer never parks", () => {
  const button = new FakeElement({ tagName: "BUTTON" });
  assert.equal(probeHarness({ element: button, scrollHeight: 5000 })(), 0);
  assert.equal(probeHarness({ element: button })(), 2);
  const footer = new FakeElement({ tagName: "DIV", style: { position: "fixed", bottom: "0px" } });
  assert.equal(probeHarness({ element: footer })(), 0);
});

test("disabled inert readonly hidden and covered controls do not park", () => {
  for (const options of [
    { attributes: { disabled: "" } },
    { attributes: { "aria-disabled": "true" } },
    { attributes: { inert: "" } },
    { attributes: { hidden: "" } },
    { readOnly: true },
    { style: { pointerEvents: "none" } },
    { style: { visibility: "hidden" } },
  ]) {
    assert.equal(probeHarness({ element: new FakeElement(options) })(), 0);
  }
  const editor = new FakeElement();
  assert.equal(probeHarness({ element: editor, hitElement: new FakeElement({ tagName: "DIV" }) })(), 0);
});

test("deep application input and hit-test fallback survive scan budget", () => {
  const editor = new FakeElement();
  let current = editor;
  for (let depth = 0; depth < 30; depth += 1) {
    current.parentElement = new FakeElement({ tagName: "DIV" });
    current = current.parentElement;
  }
  assert.equal(probeHarness({ element: editor, scrollHeight: 5000 })(), 2);
  const prefix = Array.from({ length: 4100 }, () => new FakeElement({ tagName: "DIV" }));
  assert.equal(probeHarness({ element: editor, scannedElements: [...prefix, editor] })(), 2);
});

test("repeat probe sees late inserted control without mutation observer", () => {
  const editor = new FakeElement({ style: { display: "none" } });
  const probe = probeHarness({ element: editor, scrollHeight: 5000 });
  assert.equal(probe(), 0);
  editor.style.display = "block";
  assert.equal(probe(), 2);
});

test("System WebView and Gecko execute identical predicates", () => {
  const kotlin = fs.readFileSync(new URL(
    "../app/src/main/java/dev/sk2andy/materialbrowser/browser/TextInputOcclusionScript.kt",
    import.meta.url,
  ), "utf8");
  const start = kotlin.indexOf("          const root = document.scrollingElement");
  const end = kotlin.indexOf("\n        })()", start);
  const body = kotlin.slice(start, end).split("\n").map((line) => line.slice(8)).join("\n")
    .replaceAll("obstruction.", "viewportRect.");
  const gecko = asset("text_input_occlusion.js");
  const geckoStart = gecko.indexOf("  const root = document.scrollingElement");
  const geckoEnd = gecko.indexOf("\n}\n\nglobalThis.CandyTextInputOcclusion");
  assert.equal(body, gecko.slice(geckoStart, geckoEnd));
});


test("bottom anchored chat frame parks without inspecting cross-origin contents", () => {
  const frame = new FakeElement({ tagName: "IFRAME", style: { position: "fixed", bottom: "0px" } });
  assert.equal(probeHarness({ element: frame, scrollHeight: 5000 })(), 2);
  frame.style.position = "static";
  assert.equal(probeHarness({ element: frame })(), 0);
});

test("offscreen controls skip ancestor style walks", () => {
  const button = new FakeElement({
    tagName: "BUTTON",
    rect: { left: 100, top: -100, right: 900, bottom: -20, width: 800, height: 80 },
  });
  Object.defineProperty(button, "style", { get: () => { throw new Error("Offscreen style read"); } });
  assert.equal(probeHarness({ element: button, scrollHeight: 5000 })(), 0);
});


test("negative tabindex on noninteractive role does not become control", () => {
  const heading = new FakeElement({ tagName: "DIV", attributes: { tabindex: "-1", role: "heading" } });
  assert.equal(probeHarness({ element: heading })(), 0);
  heading.attributes.role = "button";
  assert.equal(probeHarness({ element: heading })(), 2);
});
