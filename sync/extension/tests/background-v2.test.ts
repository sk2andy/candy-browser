import assert from "node:assert/strict";
import test from "node:test";

import type { StoredSettings, VaultSecrets } from "../src/core/models.js";
import { decryptTabMutation, randomBytes } from "../src/crypto/crypto.js";
import { bytesToBase64Url } from "../src/crypto/encoding.js";

test("v2 initializes with encrypted opens, keeps v1 cursor separate, and drains exact durable outbox", async () => {
  const workspaceKey = randomBytes(32);
  const settings: StoredSettings = {
    schemaVersion: 1, endpoint: "https://sync.example/", username: "alice", deviceName: "Desktop", deviceIconId: "computer",
    workspaceId: "workspace-1", deviceId: "desktop-1", cursor: "epoch-v1.4", tabRevision: "4",
    selection: { tabs: true, bookmarks: false, groups: false },
    vault: { cryptoVersion: 1, kdf: { name: "argon2id", salt: "AAAAAAAAAAAAAAAAAAAAAA", memoryKiB: 65_536, iterations: 3, parallelism: 1 }, nonce: "AAAAAAAAAAAAAAAA", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA" },
  };
  const secrets: VaultSecrets = {
    workspaceKey: bytesToBase64Url(workspaceKey), devicePrivateKeyPkcs8: bytesToBase64Url(randomBytes(64)),
    deviceToken: "device-token", workspaceId: "workspace-1", deviceId: "desktop-1",
  };
  const local = new Map<string, unknown>([["candySyncSettingsV1", settings]]);
  const session = new Map<string, unknown>([["candySyncSessionSecretsV1", secrets]]);
  const localWrites: string[][] = [];
  const area = (values: Map<string, unknown>, writes?: string[][]) => ({
    get: async (key: string) => ({ [key]: values.get(key) }),
    set: async (items: Record<string, unknown>) => {
      writes?.push(Object.keys(items).sort());
      for (const [key, value] of Object.entries(items)) values.set(key, value);
    },
    remove: async (key: string) => { values.delete(key); },
  });
  const event = { addListener: () => undefined };
  const fakeChrome = {
    permissions: { contains: async () => true, onAdded: event, onRemoved: event },
    tabs: {
      query: async () => [{ id: 7, windowId: 1, index: 0, groupId: -1, active: true, pinned: false, incognito: false, url: "https://example.com/", title: "Example" }],
      onCreated: event, onRemoved: event, onMoved: event, onUpdated: event,
    },
    storage: { local: area(local, localWrites), session: area(session) },
  } as unknown as typeof chrome;
  (globalThis as typeof globalThis & { chrome: typeof chrome }).chrome = fakeChrome;

  let pushed: Record<string, unknown> | undefined;
  const requests: string[] = [];
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
    requests.push(`${init?.method} ${url.pathname}`);
    if (url.pathname === "/.well-known/candy-sync") return Response.json({
      protocol: "candy-sync", versions: [1, 2], allowHttp: false,
      features: ["e2ee", "tab-mutations-v2", "realtime"], limits: { payloadBytes: 1_048_576 },
    });
    if (url.pathname === "/v2/sync/pull") return Response.json({ changes: [], nextCursor: "epoch-v2.0", hasMore: false });
    if (url.pathname === "/v2/sync/push") {
      pushed = JSON.parse(String(init?.body)) as Record<string, unknown>;
      const change = (pushed.changes as Array<Record<string, unknown>>)[0]!;
      assert.equal(change.baseRevision, "4");
      return Response.json({ cursor: "epoch-v2.1", results: [{ changeId: change.changeId, revision: "5" }] });
    }
    if (url.pathname === "/v2/realtime/tickets") return Response.json({ ticket: "ticket-1", expiresAt: "2026-09-02T15:00:00Z" }, { status: 201 });
    throw new Error(`Unexpected request ${url.pathname}`);
  };
  try {
    const { synchronizeTabsOnce } = await import("../src/background/background.js");
    await synchronizeTabsOnce();
    await new Promise((resolve) => setTimeout(resolve, 0));
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.WebSocket = originalWebSocket;
  }

  const envelope = (pushed!.changes as Array<Record<string, unknown>>)[0]!;
  assert.deepEqual(await decryptTabMutation(workspaceKey, envelope as never), {
    schemaVersion: 2, mutationId: envelope.mutationId, targetDeviceId: "desktop-1", type: "open",
    tab: {
      candyId: (local.get("candySyncTabIdentitiesV1") as Record<string, string>)["7"], windowId: 1, index: 0,
      groupId: null, active: true, pinned: false, title: "Example", url: "https://example.com/",
    },
  });
  const stored = local.get("candySyncSettingsV1") as StoredSettings;
  assert.equal(stored.cursor, "epoch-v1.4");
  assert.equal(stored.tabRevision, "4");
  assert.equal(stored.v2Cursor, "epoch-v2.1");
  assert.equal(stored.v2TabRevision, "5");
  assert.equal(stored.v2Initialized, true);
  assert.deepEqual((local.get("candySyncTabDeltaOutboxV2") as { items: unknown[] }).items, []);
  assert.ok(localWrites.some((keys) => keys.join(",") === "candySyncSettingsV1,candySyncTabDeltaOutboxV2"));
  assert.deepEqual(requests.slice(0, 3), ["GET /.well-known/candy-sync", "GET /v2/sync/pull", "POST /v2/sync/push"]);
  workspaceKey.fill(0);
});
