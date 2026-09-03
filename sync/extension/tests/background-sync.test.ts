import assert from "node:assert/strict";
import test from "node:test";

import type { StoredSettings, VaultSecrets } from "../src/core/models.js";
import { encryptTabSnapshot, randomBytes } from "../src/crypto/crypto.js";
import { bytesToBase64Url } from "../src/crypto/encoding.js";

test("pulls and applies a winning remote CAS revision before attempting pending local push", async () => {
  const workspaceKey = randomBytes(32);
  const pending = await encryptTabSnapshot(workspaceKey, {
    changeId: "local-pending",
    deviceId: "desktop-target",
    entity: "tabs",
    entityId: "desktop-target",
    operation: "snapshot",
    baseRevision: "0",
    schemaVersion: 1,
    cryptoVersion: 1,
    keyVersion: 1,
  }, { schemaVersion: 1, capturedAt: "2026-09-02T10:00:00Z", tabs: [] });
  const remote = await encryptTabSnapshot(workspaceKey, {
    changeId: "android-won-cas",
    deviceId: "android-writer",
    entity: "tabs",
    entityId: "desktop-target",
    operation: "snapshot",
    baseRevision: "0",
    schemaVersion: 1,
    cryptoVersion: 1,
    keyVersion: 1,
  }, { schemaVersion: 1, capturedAt: "2026-09-02T10:01:00Z", tabs: [] });
  const settings: StoredSettings = {
    schemaVersion: 1,
    endpoint: "https://sync.example/",
    username: "alice",
    deviceName: "Desktop",
    deviceIconId: "computer",
    workspaceId: "workspace-1",
    deviceId: "desktop-target",
    cursor: "epoch.0",
    tabRevision: "0",
    pendingTabChange: pending,
    selection: { tabs: true, bookmarks: false, groups: false },
    vault: {
      cryptoVersion: 1,
      kdf: { name: "argon2id", salt: "AAAAAAAAAAAAAAAAAAAAAA", memoryKiB: 65_536, iterations: 3, parallelism: 1 },
      nonce: "AAAAAAAAAAAAAAAA",
      ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
    },
  };
  const secrets: VaultSecrets = {
    workspaceKey: bytesToBase64Url(workspaceKey),
    devicePrivateKeyPkcs8: bytesToBase64Url(randomBytes(64)),
    deviceToken: "desktop-token",
    workspaceId: "workspace-1",
    deviceId: "desktop-target",
  };
  const local = new Map<string, unknown>([["candySyncSettingsV1", settings]]);
  const session = new Map<string, unknown>([["candySyncSessionSecretsV1", secrets]]);
  const area = (values: Map<string, unknown>) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => { for (const [key, value] of Object.entries(items)) values.set(key, value); },
    remove: async (key: string) => { values.delete(key); },
  });
  const event = { addListener: () => undefined };
  const fakeChrome = {
    permissions: { contains: async () => true, onAdded: event, onRemoved: event },
    tabs: {
      query: async () => [],
      remove: async () => undefined,
      update: async () => { throw new Error("No tab update expected"); },
      create: async () => { throw new Error("No tab create expected"); },
      move: async () => { throw new Error("No tab move expected"); },
      onCreated: event, onRemoved: event, onMoved: event, onUpdated: event,
    },
    storage: { local: area(local), session: area(session) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  const requests: string[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input, init) => {
    const path = new URL(String(input)).pathname;
    requests.push(`${init?.method} ${path}`);
    if (path === "/.well-known/candy-sync") {
      return Response.json({
        protocol: "candy-sync",
        versions: [1],
        allowHttp: false,
        features: ["e2ee", "tab-snapshots", "encrypted-device-icons"],
        limits: { payloadBytes: 1_048_576 },
      });
    }
    if (path === "/v1/sync/pull") {
      return Response.json({ changes: [{ ...remote, revision: "1" }], nextCursor: "epoch.1", hasMore: false });
    }
    throw new Error(`Unexpected request: ${path}`);
  };
  try {
    const { synchronizeTabsOnce } = await import("../src/background/background.js");
    await synchronizeTabsOnce();
  } finally {
    globalThis.fetch = originalFetch;
    workspaceKey.fill(0);
  }

  assert.deepEqual(requests, ["GET /.well-known/candy-sync", "GET /v1/sync/pull"]);
  const stored = local.get("candySyncSettingsV1") as StoredSettings;
  assert.equal(stored.cursor, "epoch.1");
  assert.equal(stored.tabRevision, "1");
  assert.equal(stored.pendingTabChange, undefined);
});
