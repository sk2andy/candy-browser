import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const geckoScript = fs.readFileSync(new URL(
  "../app/src/gecko/assets/candy_privacy/page_text.js", import.meta.url,
), "utf8");
const recallSource = fs.readFileSync(new URL(
  "../app/src/main/java/dev/sk2andy/materialbrowser/recall/RecallExtraction.kt", import.meta.url,
), "utf8");
const webViewScript = recallSource.match(/return """([\s\S]*?)"""\.trimIndent\(\)/)[1]
  .replaceAll("$maxTextChars", "64000");

function fixture(nodes, { body = true, topFrame = true, revision = 2, generation = 3 } = {}) {
  const listeners = [];
  let visited = 0;
  const baseStyle = { display: "block", visibility: "visible", opacity: "1", contentVisibility: "visible" };
  const root = { parentElement: null, style: baseStyle, matches: () => false };
  let styleReads = 0;
  function element(spec, parentElement) {
    return {
      parentElement,
      matches: selector => Boolean(spec.excluded && selector.includes(spec.excluded)),
      getClientRects: () => spec.noRects ? [] : [{}],
      style: { ...baseStyle, ...spec.style },
    };
  }
  const context = vm.createContext({
    self: root,
    top: topFrame ? root : {},
    location: { href: "https://example.test/article", protocol: "https:" },
    NodeFilter: { SHOW_TEXT: 4 },
    getComputedStyle: parent => { styleReads += 1; return parent.style; },
    browser: { runtime: { onMessage: { addListener: listener => listeners.push(listener) } } },
    CandyContentTopInset: {
      policyRevision: () => revision,
      navigationGeneration: () => generation,
    },
    document: {
      body: body ? root : null,
      title: "Article title",
      querySelector: () => null,
      createTreeWalker: () => ({ nextNode() {
        const item = nodes[visited++];
        if (item === undefined) return null;
        const spec = typeof item === "string" ? { text: item } : item;
        let ancestor = root;
        for (const ancestorSpec of [...(spec.ancestors || [])].reverse()) {
          ancestor = element(ancestorSpec, ancestor);
        }
        return { nodeValue: spec.text, parentElement: element(spec, ancestor) };
      } }),
    },
  });
  return { context, listeners, visited: () => visited, styleReads: () => styleReads };
}

async function extract(engine, nodes, options) {
  const current = fixture(nodes, options);
  let payload;
  if (engine === "Gecko") {
    vm.runInContext(geckoScript, current.context);
    payload = current.listeners.length ? await current.listeners[0]({
      type: "text-extract", revision: 2, navigationGeneration: 3,
    }) : null;
  } else {
    payload = JSON.parse(vm.runInContext(webViewScript, current.context));
    if (payload.error) payload = null;
  }
  return { payload: payload && JSON.parse(JSON.stringify(payload)), visited: current.visited(), styleReads: current.styleReads() };
}

for (const engine of ["Gecko", "System WebView"]) {
  test(`${engine}: extracts visible text and omits form, editable, navigation and hidden content`, async () => {
    const { payload } = await extract(engine, [
      "  Visible\n article\u0000 text ",
      ...["form", "input", "textarea", "select", "[contenteditable]", "nav", "script", "[hidden]"]
        .map(excluded => ({ text: "secret", excluded })),
      { text: "not rendered", noRects: true },
      { text: "invisible", style: { visibility: "hidden" } },
      { text: "transparent", style: { opacity: "0" } },
      "Last paragraph.",
    ]);
    assert.deepEqual(payload, {
      sourceUrl: "https://example.test/article",
      title: "Article title",
      text: "Visible article text Last paragraph.",
    });
  });

  test(`${engine}: rejects hidden ancestors and descendants inside editable regions`, async () => {
    const { payload } = await extract(engine, [
      "Visible paragraph.",
      { text: "secret opacity", ancestors: [{ style: { opacity: "0" } }] },
      { text: "secret content visibility", ancestors: [{ style: { contentVisibility: "hidden" } }] },
      { text: "secret collapsed", ancestors: [{ style: { visibility: "collapse" } }] },
      { text: "secret display", ancestors: [{ style: { display: "none" } }] },
      { text: "secret nested form", ancestors: [{ excluded: "form" }] },
      // Both contenteditable=true and contenteditable=false carry the excluded attribute.
      { text: "secret nested editable", ancestors: [{ excluded: "[contenteditable]" }] },
      { text: "secret noneditable descendant", excluded: "[contenteditable]",
        ancestors: [{ excluded: "[contenteditable]" }] },
    ]);
    assert.equal(payload.text, "Visible paragraph.");
  });

  test(`${engine}: bounds ancestor traversal and preserves shallow sibling text`, async () => {
    const { payload, styleReads } = await extract(engine, [
      { text: "too deep", ancestors: Array.from({ length: 200 }, () => ({})) },
      "Visible sibling.",
    ]);
    assert.equal(payload.text, "Visible sibling.");
    assert.ok(styleReads <= 130);
  });

  test(`${engine}: caps text at 64,000 characters`, async () => {
    const { payload } = await extract(engine, ["x".repeat(90_000)]);
    assert.equal(payload.text.length, 64_000);
  });

  test(`${engine}: stops after 20,000 text nodes`, async () => {
    const { payload, visited } = await extract(engine, [
      ...Array.from({ length: 20_000 }, () => ({ text: "excluded", excluded: "form" })),
      "must never read this",
    ]);
    assert.equal(visited, 20_000);
    assert.equal(payload?.text || "", "");
  });

  test(`${engine}: missing body safely rejects extraction`, async () => {
    assert.equal((await extract(engine, ["article"], { body: false })).payload, null);
  });
}

test("Gecko: rejects stale document revision and navigation generation", async () => {
  assert.equal((await extract("Gecko", ["article"], { revision: 1 })).payload, null);
  assert.equal((await extract("Gecko", ["article"], { generation: 1 })).payload, null);
});

test("Gecko: subframes cannot register extraction listener", async () => {
  assert.equal((await extract("Gecko", ["article"], { topFrame: false })).payload, null);
});

test("Gecko: rejects payload above serialized bridge limit", async () => {
  assert.equal((await extract("Gecko", ["\ud800".repeat(64_000)])).payload, null);
});
