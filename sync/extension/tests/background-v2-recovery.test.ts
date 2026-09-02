import assert from "node:assert/strict";
import test from "node:test";

import type { StoredSettings, TabDeltaOutbox, TabMutationState, VaultSecrets } from "../src/core/models.js";
import { encryptTabMutation, randomBytes } from "../src/crypto/crypto.js";
import { bytesToBase64Url } from "../src/crypto/encoding.js";

test("v2 pull retires only the confirmed prefix and still pushes a locally applied pending mutation", async () => {
  const workspaceKey = randomBytes(32);
  const settings: StoredSettings = {
    schemaVersion: 1, endpoint: "https://recovery.sync.example/", username: "alice",
    deviceName: "Desktop", deviceIconId: "computer", workspaceId: "workspace-1",
    deviceId: "desktop-1", cursor: "epoch-v1.0", tabRevision: "0", protocolVersion: 2,
    v2Cursor: "epoch-v2.0", v2TabRevision: "0", v2Initialized: true,
    selection: { tabs: true, bookmarks: false, groups: false },
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
  const mutation = {
    schemaVersion: 2 as const, mutationId: "lost-response-mutation", targetDeviceId: settings.deviceId,
    type: "navigate" as const, candyId: "tab-1", title: "Recovered", url: "https://example.com/recovered",
  };
  const envelope = await encryptTabMutation(workspaceKey, {
    changeId: "lost-response-change", mutationId: mutation.mutationId, workspaceId: settings.workspaceId,
    deviceId: settings.deviceId, entity: "tabs", entityId: settings.deviceId, operation: "delta",
    baseRevision: "0", schemaVersion: 2, cryptoVersion: 1, keyVersion: 1,
  }, mutation);
  const pendingMutation = {
    schemaVersion: 2 as const, mutationId: "still-pending-mutation", targetDeviceId: settings.deviceId,
    type: "navigate" as const, candyId: "tab-1", title: "Pending", url: "https://example.com/pending",
  };
  const pendingEnvelope = await encryptTabMutation(workspaceKey, {
    changeId: "still-pending-change", mutationId: pendingMutation.mutationId, workspaceId: settings.workspaceId,
    deviceId: settings.deviceId, entity: "tabs", entityId: settings.deviceId, operation: "delta",
    baseRevision: "1", schemaVersion: 2, cryptoVersion: 1, keyVersion: 1,
  }, pendingMutation);
  const outbox: TabDeltaOutbox = {
    schemaVersion: 2,
    items: [
      { envelope, mutationType: mutation.type, candyId: mutation.candyId, createdAt: new Date(0).toISOString() },
      { envelope: pendingEnvelope, mutationType: pendingMutation.type, candyId: pendingMutation.candyId, createdAt: new Date(1).toISOString() },
    ],
  };
  const mutationState: TabMutationState = {
    schemaVersion: 2, tabs: {}, tombstones: {}, appliedMutationIds: [mutation.mutationId, pendingMutation.mutationId],
  };
  const local = new Map<string, unknown>([
    ["candySyncSettingsV1", settings],
    ["candySyncTabDeltaOutboxV2", outbox],
    ["candySyncTabMutationStateV2", mutationState],
  ]);
  const session = new Map<string, unknown>([["candySyncSessionSecretsV1", secrets]]);
  const writes: string[][] = [];
  const area = (values: Map<string, unknown>, track = false) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => {
      if (track) writes.push(Object.keys(items).sort());
      for (const [key, value] of Object.entries(items)) values.set(key, value);
    },
    remove: async (key: string) => { values.delete(key); },
  });
  const event = { addListener: () => undefined };
  const fakeChrome = {
    permissions: { contains: async () => true, onAdded: event, onRemoved: event },
    tabs: { onCreated: event, onRemoved: event, onMoved: event, onUpdated: event },
    storage: { local: area(local, true), session: area(session) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  let pushedChangeId = "";
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
    if (url.pathname === "/v2/sync/pull") return Response.json({
      changes: [{ ...envelope, revision: "1" }], nextCursor: "epoch-v2.1", hasMore: false,
    });
    if (url.pathname === "/v2/sync/push") {
      const body = JSON.parse(String(init?.body)) as { changes: Array<{ changeId: string }> };
      pushedChangeId = body.changes[0]!.changeId;
      return Response.json({ cursor: "epoch-v2.2", results: [{ changeId: pushedChangeId, revision: "2" }] });
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
    workspaceKey.fill(0);
  }

  assert.equal(pushedChangeId, pendingEnvelope.changeId);
  assert.deepEqual((local.get("candySyncTabDeltaOutboxV2") as TabDeltaOutbox).items, []);
  const stored = local.get("candySyncSettingsV1") as StoredSettings;
  assert.equal(stored.v2Cursor, "epoch-v2.2");
  assert.equal(stored.v2TabRevision, "2");
  assert.ok(writes.some((keys) => keys.join(",") === "candySyncSettingsV1,candySyncTabDeltaOutboxV2"));
});
