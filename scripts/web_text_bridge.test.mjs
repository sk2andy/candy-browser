import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import test from "node:test";

const source = readFileSync(new URL("../app/src/gecko/assets/candy_privacy/web_text_bridge.js", import.meta.url), "utf8");
function fixture(subframe = false) {
  let listener, resolvePolicy;
  const operations = [];
  const frame = {};
  vm.runInNewContext(source, {
    self: frame, top: subframe ? {} : frame,
    CandyWebTextOperations: { apply: payload => { operations.push(payload); return true; } },
    browser: { runtime: {
      onMessage: { addListener: value => { listener = value; } },
      sendMessage: () => new Promise(resolve => { resolvePolicy = resolve; }),
    } },
  });
  return { get listener() { return listener; }, operations, resolvePolicy };
}

test("web text operations require current revision and navigation identity", async () => {
  const f = fixture();
  const operation = { type: "web-text-operation", revision: 4, navigationGeneration: 8, payload: { operation: "translate" } };
  assert.equal(f.listener(operation), undefined);
  f.listener({ type: "content-policy", revision: 4, navigationGeneration: 8 });
  assert.equal(f.listener({ ...operation, revision: 3 }), undefined);
  assert.equal(f.listener({ ...operation, navigationGeneration: 7 }), undefined);
  assert.equal(await f.listener(operation), true);
  assert.equal(f.operations.length, 1);
});

test("late bootstrap policy cannot replace a newer pushed document policy", async () => {
  const f = fixture();
  f.listener({ type: "content-policy", revision: 5, navigationGeneration: 9 });
  f.resolvePolicy({ revision: 4, navigationGeneration: 8 });
  await Promise.resolve();
  assert.equal(f.listener({ type: "web-text-operation", revision: 4, navigationGeneration: 8 }), undefined);
  assert.equal(await f.listener({ type: "web-text-operation", revision: 5, navigationGeneration: 9, payload: {} }), true);
});

test("subframes cannot receive annotation or translation operations", () => {
  assert.equal(fixture(true).listener, undefined);
});
