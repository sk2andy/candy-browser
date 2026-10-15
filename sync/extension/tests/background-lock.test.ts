import assert from "node:assert/strict";
import test from "node:test";
import type { StoredSettings, VaultSecrets } from "../src/core/models.js";

test("background enforces idle on wake, serializes policy/unlock/lock, and rejects invalid timeout", async () => {
  const settings = {
    deviceId: "device", workspaceId: "workspace", lockPolicy: "idle", idleLockMinutes: 7,
    selection: { tabs: false, bookmarks: false, groups: false },
  } as StoredSettings;
  const secrets: VaultSecrets = { deviceId: "device", workspaceId: "workspace", workspaceKey: "key", devicePrivateKeyPkcs8: "private", deviceToken: "token" };
  const local = new Map<string, unknown>([["candySyncSettingsV1", settings]]);
  const session = new Map<string, unknown>([["candySyncSessionSecretsV1", secrets]]);
  const area = (values: Map<string, unknown>) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => { for (const [key, value] of Object.entries(items)) values.set(key, value); },
    remove: async (key: string) => { values.delete(key); },
  });
  const event = { addListener: () => undefined };
  let idleState: "idle" | "active" = "idle";
  const queriedSeconds: number[] = [];
  const detectionIntervals: number[] = [];
  let idleUnavailable = false;
  let idleListener: ((state: string) => void) | undefined;
  let listener: ((message: unknown, sender: unknown, respond: (value: unknown) => void) => boolean) | undefined;
  globalThis.chrome = {
    storage: { local: area(local), session: area(session) },
    idle: { queryState: async (seconds: number) => { queriedSeconds.push(seconds); if (idleUnavailable) throw new Error("idle unavailable"); return idleState; }, setDetectionInterval: (seconds: number) => { detectionIntervals.push(seconds); },
      onStateChanged: { addListener: (next: typeof idleListener) => { idleListener = next; } } },
    alarms: { get: async () => ({}), onAlarm: event },
    tabs: { onCreated: event, onRemoved: event, onMoved: event, onUpdated: event },
    permissions: { onAdded: event, onRemoved: event },
    runtime: { onInstalled: event, onStartup: event, onMessage: { addListener: (next: typeof listener) => { listener = next; } } },
  } as unknown as typeof chrome;
  const { startBackground, synchronizeTabs } = await import("../src/background/background.js");
  startBackground();
  await synchronizeTabs();
  assert.deepEqual(queriedSeconds, [420]);
  assert.equal(session.size, 0);
  assert.equal((local.get("candySyncStatusV1") as { code: string }).code, "locked");
  const send = (message: Record<string, unknown>): Promise<{ ok: boolean }> => new Promise((resolve) => {
    assert.equal(listener?.(message, {}, resolve as (value: unknown) => void), true);
  });
  idleState = "active";
  await Promise.all([
    send({ type: "UPDATE_LOCK_POLICY", policy: "never", minutes: 7 }),
    send({ type: "UNLOCK_SESSION", secrets }),
    send({ type: "LOCK_SYNC" }),
  ]);
  assert.equal(session.size, 0);
  assert.equal(local.has("candySyncPersistentUnlockV1"), false);
  assert.deepEqual(await send({ type: "UNLOCK_SESSION", secrets }), { ok: true });
  assert.equal(local.has("candySyncPersistentUnlockV1"), true);
  assert.equal((await send({ type: "UPDATE_LOCK_POLICY", policy: "idle", minutes: 1.5 })).ok, false);
  assert.equal((local.get("candySyncSettingsV1") as StoredSettings).lockPolicy, "never");
  await send({ type: "UPDATE_LOCK_POLICY", policy: "idle", minutes: 11 });
  assert.equal(local.has("candySyncPersistentUnlockV1"), false);
  idleListener?.("idle");
  await synchronizeTabs();
  assert.equal(session.size, 0);
  await send({ type: "UNLOCK_SESSION", secrets });
  assert.equal(detectionIntervals.at(-1), 660);
  idleUnavailable = true;
  await synchronizeTabs();
  assert.equal(session.size, 0);
  assert.equal((local.get("candySyncStatusV1") as { code: string }).code, "locked");
});


test("first enrollment configures the custom idle interval after background started unconfigured", async () => {
  const local = new Map<string, unknown>();
  const session = new Map<string, unknown>();
  const intervals: number[] = [];
  const area = (values: Map<string, unknown>) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => { for (const [key, value] of Object.entries(items)) values.set(key, value); },
    remove: async (key: string) => { values.delete(key); },
  });
  const event = { addListener: () => undefined };
  let listener: ((message: unknown, sender: unknown, respond: (value: unknown) => void) => boolean) | undefined;
  globalThis.chrome = {
    storage: { local: area(local), session: area(session) },
    idle: { queryState: async () => "active", setDetectionInterval: (seconds: number) => { intervals.push(seconds); }, onStateChanged: event },
    alarms: { get: async () => ({}), onAlarm: event },
    tabs: { onCreated: event, onRemoved: event, onMoved: event, onUpdated: event },
    permissions: { onAdded: event, onRemoved: event },
    runtime: { onInstalled: event, onStartup: event, onMessage: { addListener: (next: typeof listener) => { listener = next; } } },
  } as unknown as typeof chrome;
  const { startBackground, synchronizeTabs } = await import("../src/background/background.js");
  startBackground();
  await synchronizeTabs();
  assert.equal(intervals.at(-1), 1800);
  const settings = { deviceId: "new-device", workspaceId: "new-workspace", lockPolicy: "idle", idleLockMinutes: 7 } as StoredSettings;
  local.set("candySyncSettingsV1", settings);
  const secrets: VaultSecrets = { deviceId: settings.deviceId, workspaceId: settings.workspaceId, workspaceKey: "key", devicePrivateKeyPkcs8: "private", deviceToken: "token" };
  const response = await new Promise((resolve) => {
    assert.equal(listener?.({ type: "UNLOCK_SESSION", secrets }, {}, resolve), true);
  });
  assert.deepEqual(response, { ok: true });
  assert.equal(intervals.at(-1), 420);
  assert.deepEqual(session.get("candySyncSessionSecretsV1"), secrets);
});
