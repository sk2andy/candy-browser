import assert from "node:assert/strict";
import test from "node:test";

import type { EncryptedTabDelta, StoredSettings, VaultSecrets } from "../src/core/models.js";
import { decryptTabMutation, randomBytes } from "../src/crypto/crypto.js";
import { bytesToBase64Url } from "../src/crypto/encoding.js";

test("v2 unlock reconciles tabs opened changed closed and reordered while locked", async () => {
  const workspaceKey = randomBytes(32);
  const settings: StoredSettings = {
    schemaVersion: 1, endpoint: "https://opt-in.sync.example/", username: "alice",
    deviceName: "Desktop", deviceIconId: "computer", workspaceId: "workspace-1", deviceId: "desktop-1",
    cursor: "epoch-v1.0", tabRevision: "0", protocolVersion: 2, v2Cursor: "epoch-v2.0",
    v2TabRevision: "0", v2Initialized: true, lockPolicy: "browser-restart", selection: { tabs: true, bookmarks: false, groups: false },
    vault: {
      cryptoVersion: 1,
      kdf: { name: "argon2id", salt: "AAAAAAAAAAAAAAAAAAAAAA", memoryKiB: 65_536, iterations: 3, parallelism: 1 },
      nonce: "AAAAAAAAAAAAAAAA", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
    },
  };
  const secrets: VaultSecrets = {
    workspaceKey: bytesToBase64Url(workspaceKey), devicePrivateKeyPkcs8: bytesToBase64Url(randomBytes(64)),
    deviceToken: "device-token", workspaceId: settings.workspaceId, deviceId: settings.deviceId,
  };
  const local = new Map<string, unknown>([
    ["candySyncSettingsV1", settings],
    ["candySyncTabIdentitiesV1", { "7": "tab-1", "8": "tab-2" }],
  ]);
  const session = new Map<string, unknown>();
  const area = (values: Map<string, unknown>) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => {
      for (const [key, value] of Object.entries(items)) values.set(key, value);
    },
    remove: async (key: string) => { values.delete(key); },
  });
  const event = { addListener: () => undefined };
  let onCreated: ((tab: chrome.tabs.Tab) => void) | undefined;
  let onRemoved: ((tabId: number) => void) | undefined;
  let onUpdated: ((tabId: number, info: chrome.tabs.OnUpdatedInfo, tab: chrome.tabs.Tab) => void) | undefined;
  let onMoved: ((tabId: number, info: { toIndex: number }) => void) | undefined;
  let onMessage: ((message: unknown, sender: unknown, respond: (reply: unknown) => void) => boolean) | undefined;
  const changed = {
    id: 7, windowId: 1, index: 1, groupId: -1, active: false, pinned: true,
    incognito: false, url: "https://example.com/changed", title: "Changed",
  } as chrome.tabs.Tab;
  const created = {
    id: 9, windowId: 1, index: 0, groupId: -1, active: true, pinned: false,
    incognito: false, url: "https://example.com/created", title: "Created",
  } as chrome.tabs.Tab;
  const fakeChrome = {
    permissions: { contains: async () => true, onAdded: event, onRemoved: event },
    tabs: {
      query: async () => [created, changed],
      onCreated: { addListener: (listener: typeof onCreated) => { onCreated = listener; } },
      onRemoved: { addListener: (listener: typeof onRemoved) => { onRemoved = listener; } },
      onUpdated: { addListener: (listener: typeof onUpdated) => { onUpdated = listener; } },
      onMoved: { addListener: (listener: typeof onMoved) => { onMoved = listener; } },
    },
    storage: { local: area(local), session: area(session) },
    idle: { queryState: async () => "active", setDetectionInterval: () => undefined, onStateChanged: event },
    alarms: { get: async () => ({}), onAlarm: event },
    runtime: {
      onInstalled: event, onStartup: event,
      onMessage: { addListener: (listener: typeof onMessage) => { onMessage = listener; } },
    },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  const pushed: EncryptedTabDelta[] = [];
  const originalFetch = globalThis.fetch;
  const originalWebSocket = globalThis.WebSocket;
  class QuietWebSocket {
    readyState = 0;
    addEventListener(): void {}
    send(): void {}
    close(): void {}
  }
  globalThis.WebSocket = QuietWebSocket as unknown as typeof WebSocket;
  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    if (url.pathname === "/.well-known/candy-sync") return Response.json({
      protocol: "candy-sync", versions: [1, 2], allowHttp: false,
      features: ["e2ee", "tab-mutations-v2", "realtime"], limits: { payloadBytes: 1_048_576 },
    });
    if (url.pathname === "/v2/sync/pull") {
      return Response.json({ changes: [], nextCursor: "epoch-v2.0", hasMore: false });
    }
    if (url.pathname === "/v2/sync/push") {
      const body = JSON.parse(String(init?.body)) as { changes: EncryptedTabDelta[] };
      const change = body.changes[0]!;
      pushed.push(change);
      const revision = String(pushed.length);
      return Response.json({ cursor: `epoch-v2.${revision}`, results: [{ changeId: change.changeId, revision }] });
    }
    if (url.pathname === "/v2/realtime/tickets") {
      return Response.json({ ticket: "ticket-1", expiresAt: "2026-09-02T15:00:00Z" }, { status: 201 });
    }
    throw new Error(`Unexpected ${init?.method} ${url.pathname}`);
  };
  try {
    const { startBackground, synchronizeTabs } = await import("../src/background/background.js");
    startBackground();
    await synchronizeTabs();
    onCreated!(created);
    onRemoved!(8);
    onUpdated!(7, { url: changed.url!, title: changed.title!, pinned: true }, changed);
    onMoved!(9, { toIndex: 0 });
    await synchronizeTabs();
    assert.equal(pushed.length, 0);
    assert.equal((local.get("candySyncStatusV1") as { code: string }).code, "locked");
    assert.deepEqual(await new Promise((resolve) => {
      assert.equal(onMessage!({ type: "UNLOCK_SESSION", secrets }, {}, resolve), true);
    }), { ok: true });
    await synchronizeTabs();
    await synchronizeTabs();
    await new Promise((resolve) => setTimeout(resolve, 0));
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.WebSocket = originalWebSocket;
  }

  assert.equal(pushed.length, 5, "Unlock must publish every change made while locked");
  const createdId = (local.get("candySyncTabIdentitiesV1") as Record<string, string>)["9"]!;
  assert.deepEqual(await Promise.all(pushed.map((change) => decryptTabMutation(workspaceKey, change))), [
    { schemaVersion: 2, mutationId: pushed[0]!.mutationId, targetDeviceId: settings.deviceId, type: "close", candyId: "tab-2" },
    {
      schemaVersion: 2, mutationId: pushed[1]!.mutationId, targetDeviceId: settings.deviceId, type: "open",
      tab: { candyId: createdId, windowId: 1, index: 0, groupId: null, active: true, pinned: false,
        title: "Created", url: "https://example.com/created" },
    },
    {
      schemaVersion: 2, mutationId: pushed[2]!.mutationId, targetDeviceId: settings.deviceId, type: "navigate",
      candyId: "tab-1", title: "Changed", url: "https://example.com/changed",
    },
    { schemaVersion: 2, mutationId: pushed[3]!.mutationId, targetDeviceId: settings.deviceId, type: "set-pinned", candyId: "tab-1", pinned: true },
    { schemaVersion: 2, mutationId: pushed[4]!.mutationId, targetDeviceId: settings.deviceId, type: "reorder", orderedCandyIds: [createdId, "tab-1"] },
  ]);
  const stored = local.get("candySyncSettingsV1") as StoredSettings;
  assert.equal(stored.v2ReconciliationPending, undefined);
  assert.equal(stored.v2DisabledTabIds, undefined);
  assert.equal(stored.v2TabRevision, "5");
  assert.deepEqual((local.get("candySyncTabDeltaOutboxV2") as { items: unknown[] }).items, []);
  workspaceKey.fill(0);
});
