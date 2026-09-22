import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const context = vm.createContext({ Map, Object, Set });
const source = fs.readFileSync(
  new URL("../app/src/gecko/assets/candy_privacy/privacy_signals.js", import.meta.url),
  "utf8",
);
vm.runInContext(source, context);
const signals = context.CandyPrivacySignals;

test("request headers replace spoofed values and keep unrelated headers", () => {
  const headers = signals.requestHeaders(
    [
      { name: "Accept", value: "text/html" },
      { name: "dNt", value: "0" },
      { name: "SEC-GPC", value: "0" },
    ],
    { doNotTrackEnabled: true, globalPrivacyControlEnabled: true },
  );

  assert.deepEqual(JSON.parse(JSON.stringify(headers)), [
    { name: "Accept", value: "text/html" },
    { name: "DNT", value: "1" },
    { name: "Sec-GPC", value: "1" },
  ]);
});

test("disabled request signals remove stale privacy headers", () => {
  const headers = signals.requestHeaders(
    [{ name: "DNT", value: "1" }, { name: "Sec-GPC", value: "1" }],
    { doNotTrackEnabled: false, globalPrivacyControlEnabled: false },
  );

  assert.deepEqual(JSON.parse(JSON.stringify(headers)), []);
});

test("document signals expose enabled values and neutral disabled values", () => {
  const navigator = {};
  assert.equal(signals.installDocumentSignals(
    { navigator },
    { doNotTrackEnabled: true, globalPrivacyControlEnabled: true },
  ), true);
  assert.equal(navigator.doNotTrack, "1");
  assert.equal(navigator.globalPrivacyControl, true);
  assert.equal(Object.getOwnPropertyDescriptor(navigator, "doNotTrack").get, undefined);
  assert.equal(Object.getOwnPropertyDescriptor(navigator, "doNotTrack").writable, false);
  assert.equal(Object.getOwnPropertyDescriptor(navigator, "globalPrivacyControl").get, undefined);
  assert.equal(Object.getOwnPropertyDescriptor(navigator, "globalPrivacyControl").writable, false);

  signals.installDocumentSignals(
    { navigator },
    { doNotTrackEnabled: false, globalPrivacyControlEnabled: false },
  );
  assert.equal(navigator.doNotTrack, null);
  assert.equal(navigator.globalPrivacyControl, false);
});

test("registered document-start code uses primitive descriptors and rejects stale revisions", () => {
  const navigator = {};
  const pageContext = vm.createContext({ navigator });
  vm.runInContext(
    signals.registrationCode(
      { doNotTrackEnabled: true, globalPrivacyControlEnabled: true },
      4,
    ),
    pageContext,
  );

  const dnt = Object.getOwnPropertyDescriptor(navigator, "doNotTrack");
  const gpc = Object.getOwnPropertyDescriptor(navigator, "globalPrivacyControl");
  assert.equal(dnt.value, "1");
  assert.equal(dnt.get, undefined);
  assert.equal(dnt.set, undefined);
  assert.equal(dnt.writable, false);
  assert.equal(typeof dnt.value, "string");
  assert.equal(gpc.value, true);
  assert.equal(gpc.get, undefined);
  assert.equal(gpc.set, undefined);
  assert.equal(gpc.writable, false);
  assert.equal(typeof gpc.value, "boolean");

  vm.runInContext(
    signals.registrationCode(
      { doNotTrackEnabled: false, globalPrivacyControlEnabled: false },
      5,
    ),
    pageContext,
  );
  vm.runInContext(
    signals.registrationCode(
      { doNotTrackEnabled: true, globalPrivacyControlEnabled: true },
      4,
    ),
    pageContext,
  );
  assert.equal(navigator.doNotTrack, null);
  assert.equal(navigator.globalPrivacyControl, false);
  assert.equal(navigator.__candyPrivacySignalRevision, 5);
});
