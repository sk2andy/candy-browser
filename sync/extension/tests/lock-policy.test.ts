import assert from "node:assert/strict";
import test from "node:test";
import type { StoredSettings, VaultSecrets } from "../src/core/models.js";
import { idleLockSeconds, isIdleLockMinutes, isLockPolicy, lockPolicy } from "../src/core/lock-rules.js";
import { clearSessionSecrets, loadSessionSecrets, saveSessionSecrets, saveSettings } from "../src/storage/stores.js";

const settings = { deviceId: "device", workspaceId: "workspace" } as StoredSettings;
const secrets: VaultSecrets = { deviceId: "device", workspaceId: "workspace", workspaceKey: "key", devicePrivateKeyPkcs8: "private", deviceToken: "token" };

function browserStorage() {
  const local = new Map<string, unknown>();
  const session = new Map<string, unknown>();
  const area = (values: Map<string, unknown>) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => { for (const [key, value] of Object.entries(items)) values.set(key, value); },
    remove: async (key: string) => { values.delete(key); },
    setAccessLevel: async () => undefined,
  });
  globalThis.chrome = { storage: { local: area(local), session: area(session) } } as unknown as typeof chrome;
  return { local, session };
}

test("missing and invalid policies preserve browser-restart locking, custom timeout is bounded", () => {
  assert.equal(lockPolicy(null), "browser-restart");
  assert.equal(lockPolicy(settings), "browser-restart");
  assert.equal(lockPolicy({ ...settings, lockPolicy: "unknown" } as unknown as StoredSettings), "browser-restart");
  assert.equal(isLockPolicy("idle"), true);
  assert.equal(isLockPolicy("never"), true);
  assert.equal(idleLockSeconds(settings), 1800);
  assert.equal(idleLockSeconds({ ...settings, idleLockMinutes: 7 }), 420);
  for (const value of [0, -1, 1.5, 1441, NaN, Infinity, "30"]) assert.equal(isIdleLockMinutes(value), false);
  assert.equal(isIdleLockMinutes(1), true);
  assert.equal(isIdleLockMinutes(1440), true);
});

test("only explicit never policy survives restart and changing policy removes persistent secrets", async () => {
  const { local, session } = browserStorage();
  await saveSettings(settings);
  await saveSessionSecrets(secrets);
  assert.equal(local.has("candySyncPersistentUnlockV1"), false);
  session.clear();
  assert.equal(await loadSessionSecrets(), null);
  await saveSettings({ ...settings, lockPolicy: "never" });
  await saveSessionSecrets(secrets);
  session.clear();
  assert.deepEqual(await loadSessionSecrets(), secrets);
  await saveSettings({ ...settings, lockPolicy: "idle", idleLockMinutes: 30 });
  await saveSessionSecrets(secrets);
  assert.equal(local.has("candySyncPersistentUnlockV1"), false);
  session.clear();
  assert.equal(await loadSessionSecrets(), null);
});

test("manual lock removes both copies and saved access cannot unlock another device", async () => {
  const { local, session } = browserStorage();
  await saveSettings({ ...settings, lockPolicy: "never" });
  await saveSessionSecrets(secrets);
  await saveSettings({ ...settings, deviceId: "other", lockPolicy: "never" });
  assert.equal(await loadSessionSecrets(), null);
  await clearSessionSecrets();
  assert.equal(local.has("candySyncPersistentUnlockV1"), false);
  assert.equal(session.size, 0);
});
