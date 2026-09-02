import assert from "node:assert/strict";
import test from "node:test";

import type { StoredSettings } from "../src/core/models.js";

test("background serializes selection updates without losing revision or durable outbox", async () => {
  const settings: StoredSettings = {
    schemaVersion: 1,
    endpoint: "https://sync.example/",
    username: "alice",
    deviceName: "Desktop",
    deviceIconId: "computer",
    workspaceId: "workspace-1",
    deviceId: "device-1",
    cursor: "epoch-1.7",
    tabRevision: "7",
    pendingTabChange: {
      changeId: "pending-1",
      deviceId: "device-1",
      entity: "tabs",
      entityId: "device-1",
      operation: "snapshot",
      baseRevision: "7",
      schemaVersion: 1,
      cryptoVersion: 1,
      keyVersion: 1,
      nonce: "AAAAAAAAAAAAAAAA",
      ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
    },
    selection: { tabs: true, bookmarks: false, groups: true },
    vault: {
      cryptoVersion: 1,
      kdf: { name: "argon2id", salt: "AAAAAAAAAAAAAAAAAAAAAA", memoryKiB: 65_536, iterations: 3, parallelism: 1 },
      nonce: "AAAAAAAAAAAAAAAA",
      ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
    },
  };
  const local = new Map<string, unknown>([["candySyncSettingsV1", settings]]);
  let runtimeListener: ((message: unknown, sender: unknown, respond: (value: unknown) => void) => boolean) | undefined;
  const event = { addListener: () => undefined };
  const fakeChrome = {
    alarms: { get: async () => ({ name: "candy-sync-periodic" }), create: async () => undefined, onAlarm: event },
    tabs: { onCreated: event, onRemoved: event, onMoved: event, onUpdated: event },
    permissions: { onAdded: event, onRemoved: event, contains: async () => true },
    runtime: {
      onInstalled: event,
      onStartup: event,
      onMessage: { addListener: (listener: typeof runtimeListener) => { runtimeListener = listener; } },
    },
    storage: {
      local: {
        get: async (key: string) => ({ [key]: local.get(key) }),
        set: async (values: Record<string, unknown>) => { for (const [key, value] of Object.entries(values)) local.set(key, value); },
      },
      session: { get: async () => ({}) },
    },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  const { startBackground } = await import("../src/background/background.js");
  startBackground();
  assert.ok(runtimeListener);

  const send = (selection: StoredSettings["selection"]): Promise<unknown> => new Promise((resolve) => {
    assert.equal(runtimeListener?.({ type: "UPDATE_SELECTION", selection }, {}, resolve), true);
  });
  await Promise.all([
    send({ tabs: false, bookmarks: false, groups: false }),
    send({ tabs: true, bookmarks: false, groups: false }),
  ]);

  const stored = local.get("candySyncSettingsV1") as StoredSettings;
  assert.deepEqual(stored.selection, { tabs: true, bookmarks: false, groups: false });
  assert.equal(stored.tabRevision, "7");
  assert.equal(stored.pendingTabChange?.changeId, "pending-1");
});
