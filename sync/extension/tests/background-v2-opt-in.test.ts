import assert from "node:assert/strict";
import test from "node:test";

import type { EncryptedTabDelta, StoredSettings, VaultSecrets } from "../src/core/models.js";
import { decryptTabMutation, randomBytes } from "../src/crypto/crypto.js";
import { bytesToBase64Url } from "../src/crypto/encoding.js";

test("v2 opt-in reconciles tabs changed while synchronization was disabled", async () => {
  const workspaceKey = randomBytes(32);
  const settings: StoredSettings = {
    schemaVersion: 1, endpoint: "https://opt-in.sync.example/", username: "alice",
    deviceName: "Desktop", deviceIconId: "computer", workspaceId: "workspace-1", deviceId: "desktop-1",
    cursor: "epoch-v1.0", tabRevision: "0", protocolVersion: 2, v2Cursor: "epoch-v2.0",
    v2TabRevision: "0", v2Initialized: true, v2DisabledTabIds: ["tab-1", "tab-2"],
    v2ReconciliationPending: true, selection: { tabs: true, bookmarks: false, groups: false },
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
    ["candySyncTabIdentitiesV1", { "7": "tab-1" }],
  ]);
  const session = new Map<string, unknown>([["candySyncSessionSecretsV1", secrets]]);
  const area = (values: Map<string, unknown>) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => {
      for (const [key, value] of Object.entries(items)) values.set(key, value);
    },
    remove: async (key: string) => { values.delete(key); },
  });
  const event = { addListener: () => undefined };
  const fakeChrome = {
    permissions: { contains: async () => true, onAdded: event, onRemoved: event },
    tabs: {
      query: async () => [{
        id: 7, windowId: 1, index: 0, groupId: -1, active: true, pinned: true,
        incognito: false, url: "https://example.com/changed", title: "Changed",
      }],
      onCreated: event, onRemoved: event, onMoved: event, onUpdated: event,
    },
    storage: { local: area(local), session: area(session) },
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
    const { synchronizeTabsOnce } = await import("../src/background/background.js");
    await synchronizeTabsOnce();
    await new Promise((resolve) => setTimeout(resolve, 0));
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.WebSocket = originalWebSocket;
  }

  assert.deepEqual(await Promise.all(pushed.map((change) => decryptTabMutation(workspaceKey, change))), [
    { schemaVersion: 2, mutationId: pushed[0]!.mutationId, targetDeviceId: settings.deviceId, type: "close", candyId: "tab-2" },
    {
      schemaVersion: 2, mutationId: pushed[1]!.mutationId, targetDeviceId: settings.deviceId, type: "navigate",
      candyId: "tab-1", title: "Changed", url: "https://example.com/changed",
    },
    { schemaVersion: 2, mutationId: pushed[2]!.mutationId, targetDeviceId: settings.deviceId, type: "set-pinned", candyId: "tab-1", pinned: true },
    { schemaVersion: 2, mutationId: pushed[3]!.mutationId, targetDeviceId: settings.deviceId, type: "reorder", orderedCandyIds: ["tab-1"] },
  ]);
  const stored = local.get("candySyncSettingsV1") as StoredSettings;
  assert.equal(stored.v2ReconciliationPending, undefined);
  assert.equal(stored.v2DisabledTabIds, undefined);
  assert.equal(stored.v2TabRevision, "4");
  assert.deepEqual((local.get("candySyncTabDeltaOutboxV2") as { items: unknown[] }).items, []);
  workspaceKey.fill(0);
});
