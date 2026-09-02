import assert from "node:assert/strict";
import test from "node:test";

import { applyTabSnapshot, collectTabSnapshot } from "../src/browser-adapters/tabs.js";

function storageArea(local: Map<string, unknown>): chrome.storage.StorageArea {
  return {
    get: async (key: string | string[] | Record<string, unknown> | null) => {
      const keys = typeof key === "string" ? [key] : Array.isArray(key) ? key : Object.keys(key ?? {});
      return Object.fromEntries(keys.map((item) => [item, local.get(item)]));
    },
    set: async (items: Record<string, unknown>) => { for (const [key, value] of Object.entries(items)) local.set(key, value); },
  } as unknown as chrome.storage.StorageArea;
}

test("durable candy tab UUID survives navigation and collection", async () => {
  const local = new Map<string, unknown>();
  let url = "https://before.example/";
  const fakeChrome = {
    tabs: {
      query: async () => [{
        id: 7, windowId: 1, index: 0, groupId: -1, active: true, pinned: false, incognito: false, url, title: "Tab",
      }],
    },
    storage: { local: storageArea(local) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  const before = await collectTabSnapshot(new Date("2026-09-02T10:00:00Z"));
  url = "https://after.example/path";
  const after = await collectTabSnapshot(new Date("2026-09-02T10:01:00Z"));
  assert.match(before.tabs[0]!.candyId, /^[0-9a-f-]{36}$/u);
  assert.equal(after.tabs[0]!.candyId, before.tabs[0]!.candyId);
  assert.equal(after.tabs[0]!.url, "https://after.example/path");
});

test("remote reconciliation updates, creates, reorders and closes only eligible normal web tabs", async () => {
  const local = new Map<string, unknown>([["candySyncTabIdentitiesV1", { "1": "keep-a", "2": "remove-b" }]]);
  const tabs = [
    { id: 1, windowId: 1, index: 0, groupId: -1, active: true, highlighted: true, selected: true, pinned: false, incognito: false, url: "https://old.example/" },
    { id: 2, windowId: 1, index: 1, groupId: -1, active: false, highlighted: false, selected: false, pinned: false, incognito: false, url: "https://remove.example/" },
    { id: 3, windowId: 1, index: 2, groupId: -1, active: false, highlighted: false, selected: false, pinned: false, incognito: false, url: "chrome://settings/" },
    { id: 4, windowId: 2, index: 0, groupId: -1, active: false, highlighted: false, selected: false, pinned: false, incognito: true, url: "https://private.example/" },
  ] as unknown as Array<chrome.tabs.Tab & { id: number }>;
  let nextId = 5;
  const updates: Array<[number, chrome.tabs.UpdateProperties]> = [];
  const moves: Array<[number, chrome.tabs.MoveProperties]> = [];
  const removed: number[] = [];
  const fakeChrome = {
    tabs: {
      query: async () => tabs,
      update: async (id: number, properties: chrome.tabs.UpdateProperties) => {
        updates.push([id, properties]);
        const tab = tabs.find((item) => item.id === id)!;
        Object.assign(tab, properties);
        return tab;
      },
      create: async (properties: chrome.tabs.CreateProperties) => {
        const tab = {
          id: nextId++, windowId: 1, index: tabs.length, groupId: -1, active: properties.active ?? false,
          highlighted: false, selected: false, pinned: properties.pinned ?? false, incognito: false, url: String(properties.url),
        } as chrome.tabs.Tab & { id: number };
        tabs.push(tab);
        return tab;
      },
      move: async (id: number, properties: chrome.tabs.MoveProperties) => {
        moves.push([id, properties]);
        return tabs.find((item) => item.id === id)!;
      },
      remove: async (ids: number | number[]) => {
        for (const id of Array.isArray(ids) ? ids : [ids]) removed.push(id);
      },
    },
    storage: { local: storageArea(local) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  await applyTabSnapshot({
    schemaVersion: 1,
    capturedAt: "2026-09-02T10:02:00Z",
    tabs: [
      { candyId: "keep-a", windowId: 9, index: 1, groupId: null, active: false, pinned: false, title: "Updated", url: "https://updated.example/" },
      { candyId: "create-c", windowId: 9, index: 0, groupId: null, active: true, pinned: true, title: "Created", url: "https://created.example/" },
    ],
  });

  assert.deepEqual(removed, [2]);
  assert.equal(updates.some(([id, properties]) => id === 1 && properties.url === "https://updated.example/"), true);
  assert.equal(updates.some(([id, properties]) => id === 5 && properties.active === true), true);
  assert.deepEqual(moves.map(([id]) => id), [5, 1]);
  assert.equal(removed.includes(3), false);
  assert.equal(removed.includes(4), false);
  assert.deepEqual(local.get("candySyncTabIdentitiesV1"), { "1": "keep-a", "5": "create-c" });
});

test("remote snapshot parser rejects private/internal schemes and unknown fields before mutation", async () => {
  let queried = false;
  const fakeChrome = {
    tabs: { query: async () => { queried = true; return []; } },
    storage: { local: storageArea(new Map()) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;
  await assert.rejects(applyTabSnapshot({
    schemaVersion: 1,
    capturedAt: "2026-09-02T10:02:00Z",
    tabs: [{ candyId: "x", windowId: 1, index: 0, groupId: null, active: true, pinned: false, title: "", url: "chrome://settings/", extra: true }],
  }), /fields|URL/u);
  assert.equal(queried, false);
});
