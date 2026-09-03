import assert from "node:assert/strict";
import test from "node:test";

import { snapshotFromTabs } from "../src/core/snapshot-rules.js";

test("captures normal web tabs in deterministic window order", () => {
  const snapshot = snapshotFromTabs([
    { id: 8, windowId: 2, index: 0, active: false, pinned: true, incognito: false, url: "https://b.example/", title: "B", groupId: 4 },
    { id: 7, windowId: 1, index: 2, active: true, pinned: false, incognito: false, pendingUrl: "https://a.example/", title: "A" },
  ], "2026-09-02T10:00:00.000Z");
  assert.deepEqual(snapshot.tabs.map((tab) => tab.url), ["https://a.example/", "https://b.example/"]);
  assert.equal(snapshot.tabs[1]?.groupId, 4);
});

test("excludes private, internal, local-file and malformed tabs", () => {
  const base = { windowId: 1, index: 0, active: false, pinned: false, incognito: false };
  const snapshot = snapshotFromTabs([
    { ...base, id: 1, incognito: true, url: "https://private.example/" },
    { ...base, id: 2, url: "about:config" },
    { ...base, id: 3, url: "chrome://settings" },
    { ...base, id: 4, url: "file:///secret.txt" },
    { ...base, id: 5, url: "broken" },
    { ...base, id: 6, url: "https://kept.example/" },
  ], "2026-09-02T10:00:00.000Z");
  assert.deepEqual(snapshot.tabs.map((tab) => tab.url), ["https://kept.example/"]);
});

test("prefers a syncable pending navigation over the previous internal URL", () => {
  const snapshot = snapshotFromTabs([{
    id: 7,
    windowId: 1,
    index: 0,
    active: true,
    pinned: false,
    incognito: false,
    url: "about:blank",
    pendingUrl: "https://destination.example/path",
  }], "2026-09-03T08:00:00.000Z", { "7": "stable-tab" });

  assert.equal(snapshot.tabs[0]?.candyId, "stable-tab");
  assert.equal(snapshot.tabs[0]?.url, "https://destination.example/path");
});
