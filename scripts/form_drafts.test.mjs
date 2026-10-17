import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import test from "node:test";

const source = readFileSync(new URL("../app/src/main/assets/form_drafts.js", import.meta.url), "utf8");

function fixture() {
  const events = new Map();
  const nodes = [];
  const output = [];
  class Textarea {
    constructor(id, attributes = {}) {
      this.id = id; this.attributes = attributes; this.tagName = "TEXTAREA";
      this.isConnected = true; this.text = ""; this.form = null;
      nodes.push(this);
    }
    get value() { return this.text; }
    set value(value) { this.text = value; }
    getAttribute(name) { return name === "id" ? this.id : this.attributes[name] || null; }
    matches() { return true; }
    getClientRects() { return this.attributes.hidden ? [] : [{}]; }
    closest(selector) {
      if (selector === "form") return this.form;
      if (selector.startsWith("textarea")) return this;
      if (selector.includes('[autocomplete="off"]') && this.attributes.autocomplete === "off") return this;
      return null;
    }
    dispatchEvent() {}
  }
  const doc = {
    readyState: "complete",
    querySelectorAll: () => nodes,
    addEventListener: (name, callback) => events.set(name, callback),
    removeEventListener: name => events.delete(name),
  };
  const win = {
    document: doc, crypto: webcrypto, location: { href: "https://example.org/reply?id=1#editor" },
    HTMLTextAreaElement: Textarea, Event: class {},
    getComputedStyle: () => ({ display: "block", visibility: "visible" }),
    addEventListener: (name, callback) => events.set(name, callback),
    removeEventListener: name => events.delete(name),
  };
  win.top = win;
  const sandbox = vm.createContext({ Uint8Array });
  vm.runInContext(source, sandbox);
  const drafts = sandbox.CandyFormDraftFactory.create(win, data => output.push(JSON.parse(JSON.stringify(data))));
  const edit = (node, text, trusted = true) => { node.value = text; events.get("input")({ target: node, isTrusted: trusted }); };
  return { drafts, output, Textarea, edit, events, win, sandbox };
}

test("opt-in and real input required; programmatic values never captured", () => {
  const f = fixture(); const field = new f.Textarea("reply");
  f.edit(field, "disabled"); assert.equal(f.output.length, 0);
  f.drafts.setEnabled(true); assert.equal(f.output.at(-1).ready, true);
  f.edit(field, "script", false); assert.equal(f.output.length, 1);
  f.edit(field, "actual reply"); assert.equal(f.output.at(-1).fields[0].value, "actual reply");
});

test("password payment autocomplete-off and ambiguous fields excluded", () => {
  const f = fixture(); f.drafts.setEnabled(true);
  for (const field of [new f.Textarea("password"), new f.Textarea("credit_card"),
    new f.Textarea("message", { autocomplete: "off" }), new f.Textarea("duplicate"), new f.Textarea("duplicate")]) {
    f.edit(field, "must never leave DOM");
  }
  const formField = new f.Textarea("reply");
  formField.form = { id: "login", getAttribute: () => "", elements: [{ tagName: "INPUT", type: "password", getAttribute: () => "" }] };
  f.edit(formField, "sensitive form");
  assert.equal(f.output.length, 1);
});

test("restore binds exact URL and document and never overwrites existing text", () => {
  const f = fixture(); const field = new f.Textarea("reply");
  f.drafts.setEnabled(true); f.edit(field, "saved reply");
  const payload = f.output.at(-1); field.value = "";
  assert.equal(f.drafts.restore({ ...payload, url: "https://example.org/other" }), false);
  assert.equal(f.drafts.restore({ ...payload, documentId: "old-document" }), false);
  assert.equal(f.drafts.restore(payload), true); assert.equal(field.value, "saved reply");
  field.value = "new reply";
  assert.equal(f.drafts.restore(payload), false); assert.equal(field.value, "new reply");
});

test("submit clears text without pagehide resurrection; subsequent edits start new draft", () => {
  const f = fixture(); const field = new f.Textarea("reply");
  const form = { id: "post", getAttribute: () => "", elements: [] };
  field.form = form; f.drafts.setEnabled(true); f.edit(field, "old reply");
  f.events.get("submit")({ target: form, isTrusted: true });
  assert.equal(f.output.at(-1).submitted, true); assert.deepEqual(f.output.at(-1).fields, []);
  const size = f.output.length; f.events.get("pagehide")(); assert.equal(f.output.length, size);
  f.edit(field, "corrected reply"); assert.equal(f.output.at(-1).submitted, false);
  assert.equal(f.output.at(-1).fields[0].value, "corrected reply");
});

test("page identity changes and disabled capture cannot reuse prior fields", () => {
  const f = fixture(); const field = new f.Textarea("reply");
  f.drafts.setEnabled(true); f.edit(field, "old route");
  f.win.location.href = "https://example.org/reply?id=2#editor";
  f.drafts.setEnabled(true); assert.deepEqual(f.output.at(-1).fields, []);
  f.drafts.setEnabled(false); const size = f.output.length;
  f.edit(field, "disabled"); assert.equal(f.output.length, size);
});

test("System WebView rebind replaces an old document-start transport token", () => {
  const f = fixture();
  const messages = [];
  f.win.location.protocol = "https:";
  Object.assign(f.sandbox, {
    self: f.win, top: f.win, window: f.win, location: f.win.location,
    CandyFormDraftBridge: { postMessage: raw => messages.push(JSON.parse(raw)) },
  });
  const kotlin = readFileSync(new URL(
    "../app/src/main/java/dev/sk2andy/materialbrowser/browser/systemwebview/SystemWebViewFormDrafts.kt",
    import.meta.url,
  ), "utf8");
  const template = kotlin.match(/private fun script\(\): String = """([\s\S]*?)"""\.trimIndent\(\)/)?.[1];
  assert.ok(template, "System WebView draft injection script exists");
  function inject(token) {
    const script = template.replace("$source", source)
      .replace("${JSONObject.quote(token)}", JSON.stringify(token))
      .replaceAll("$BRIDGE", "CandyFormDraftBridge");
    vm.runInContext(script, f.sandbox);
  }

  inject("departing-document-token");
  const oldDocumentId = messages.at(-1).payload.documentId;
  inject("current-document-token");
  assert.equal(messages.at(-1).token, "current-document-token");
  assert.notEqual(messages.at(-1).payload.documentId, oldDocumentId);
  const currentDocumentId = messages.at(-1).payload.documentId;
  inject("current-document-token");
  assert.equal(messages.at(-1).payload.documentId, currentDocumentId);
  f.edit(new f.Textarea("reply"), "New private draft");
  assert.equal(messages.at(-1).token, "current-document-token");
  assert.equal(messages.at(-1).payload.fields[0].value, "New private draft");
});
