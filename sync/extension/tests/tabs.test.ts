import assert from "node:assert/strict";
import test from "node:test";

import {
  applyTabMutation, applyTabSnapshot, collectTabSnapshot, mutationForCreatedTab,
  mutationForMovedTab, mutationForRemovedTab, mutationsForUpdatedTab,
} from "../src/browser-adapters/tabs.js";

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

test("transient navigation keeps identity and commits as navigate instead of close plus open", async () => {
  const local = new Map<string, unknown>([["candySyncTabIdentitiesV1", { "7": "stable-tab" }]]);
  const transientTab = {
    id: 7,
    windowId: 1,
    index: 0,
    active: true,
    pinned: false,
    incognito: false,
    status: "loading",
    url: "about:blank",
  } as chrome.tabs.Tab;
  const fakeChrome = {
    tabs: { query: async () => [transientTab] },
    storage: { local: storageArea(local) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  const loading = await mutationsForUpdatedTab(
    7,
    { status: "loading" },
    transientTab,
  );
  assert.deepEqual(loading, []);
  assert.deepEqual((await collectTabSnapshot()).tabs, []);
  assert.deepEqual(local.get("candySyncTabIdentitiesV1"), { "7": "stable-tab" });

  const committed = await mutationsForUpdatedTab(
    7,
    { url: "https://destination.example/" },
    ({
      id: 7,
      windowId: 1,
      index: 0,
      active: true,
      pinned: false,
      incognito: false,
      status: "complete",
      url: "https://destination.example/",
      title: "Destination",
    } as chrome.tabs.Tab),
  );
  assert.deepEqual(committed, [{
    type: "navigate",
    candyId: "stable-tab",
    url: "https://destination.example/",
    title: "Destination",
  }]);
});

test("completed navigation to an excluded URL closes the synced tab", async () => {
  const local = new Map<string, unknown>([["candySyncTabIdentitiesV1", { "7": "stable-tab" }]]);
  const fakeChrome = {
    storage: { local: storageArea(local) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  assert.deepEqual(await mutationsForUpdatedTab(
    7,
    { status: "complete" },
    ({
      id: 7,
      windowId: 1,
      index: 0,
      active: true,
      pinned: false,
      incognito: false,
      status: "complete",
      url: "chrome://settings/",
    } as chrome.tabs.Tab),
  ), [{ type: "close", candyId: "stable-tab" }]);
  assert.deepEqual(local.get("candySyncTabIdentitiesV1"), {});
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

test("local browser events emit small mutations with stable identity and exclude private/internal tabs", async () => {
  const local = new Map<string, unknown>();
  const tab = { id: 7, windowId: 1, index: 2, active: true, pinned: false, incognito: false, url: "https://one.example/", title: "One" } as chrome.tabs.Tab;
  const fakeChrome = { tabs: { query: async () => [tab] }, storage: { local: storageArea(local) } } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;
  const opened = await mutationForCreatedTab(tab);
  assert.equal(opened?.type, "open");
  const candyId = opened!.type === "open" ? opened.tab.candyId : "";
  assert.deepEqual(await mutationsForUpdatedTab(7, { url: "https://two.example/" }, { ...tab, url: "https://two.example/", title: "Two" }), [{
    type: "navigate", candyId, url: "https://two.example/", title: "Two",
  }]);
  assert.deepEqual(await mutationsForUpdatedTab(7, { pinned: true }, { ...tab, pinned: true }), [{
    type: "set-pinned", candyId, pinned: true,
  }]);
  assert.deepEqual(await mutationForMovedTab(7, 4), { type: "reorder", orderedCandyIds: [candyId] });
  assert.deepEqual(await mutationForRemovedTab(7), { type: "close", candyId });
  assert.equal(await mutationForCreatedTab({ ...tab, id: 8, incognito: true }), null);
  assert.equal(await mutationForCreatedTab({ ...tab, id: 9, url: "chrome://settings/" }), null);
});

test("remote mutation applies one browser operation and maintains UUID mapping", async () => {
  const local = new Map<string, unknown>();
  const tabs: chrome.tabs.Tab[] = [];
  const updates: unknown[] = [];
  const moves: unknown[] = [];
  const removals: number[] = [];
  const fakeChrome = {
    tabs: {
      create: async (properties: chrome.tabs.CreateProperties) => {
        const tab = { id: 11, windowId: 1, index: properties.index ?? 0, active: false, pinned: properties.pinned ?? false, incognito: false, url: properties.url } as chrome.tabs.Tab;
        tabs.push(tab); return tab;
      },
      update: async (tabId: number, properties: chrome.tabs.UpdateProperties) => { updates.push([tabId, properties]); return tabs[0]!; },
      move: async (tabId: number, properties: chrome.tabs.MoveProperties) => { moves.push([tabId, properties]); return tabs[0]!; },
      remove: async (tabId: number) => { removals.push(tabId); },
    },
    storage: { local: storageArea(local) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;
  const common = { schemaVersion: 2 as const, targetDeviceId: "desktop-1" };
  await applyTabMutation({ ...common, mutationId: "mutation-open", type: "open", tab: {
    candyId: "remote-tab", windowId: 1, index: 2, groupId: null, active: false, pinned: false, title: "", url: "https://one.example/",
  } });
  await applyTabMutation({ ...common, mutationId: "mutation-nav", type: "navigate", candyId: "remote-tab", url: "https://two.example/", title: "" });
  await applyTabMutation({ ...common, mutationId: "mutation-pin", type: "set-pinned", candyId: "remote-tab", pinned: true });
  await applyTabMutation({ ...common, mutationId: "mutation-order", type: "reorder", orderedCandyIds: ["remote-tab"] });
  await applyTabMutation({ ...common, mutationId: "mutation-close", type: "close", candyId: "remote-tab" });
  assert.deepEqual(local.get("candySyncTabIdentitiesV1"), {});
  assert.deepEqual(updates, [[11, { url: "https://two.example/" }], [11, { pinned: true }]]);
  assert.deepEqual(moves, [[11, { index: 0 }]]);
  assert.deepEqual(removals, [11]);
});
