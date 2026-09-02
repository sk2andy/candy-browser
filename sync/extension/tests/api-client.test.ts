import assert from "node:assert/strict";
import test from "node:test";

import { CandySyncApiClient } from "../src/protocol/api-client.js";

test("calls the native fetch function with its browser-global receiver", async () => {
  const originalFetch = globalThis.fetch;
  let receiver: unknown;
  const receiverSensitiveFetch: typeof fetch = function (this: unknown) {
    receiver = this;
    if (this !== globalThis) throw new TypeError("Illegal invocation");
    return Promise.resolve(Response.json({
      protocol: "candy-sync",
      versions: [1],
      allowHttp: false,
      features: ["e2ee"],
      limits: { payloadBytes: 1_048_576 },
    }));
  };
  globalThis.fetch = receiverSensitiveFetch;
  try {
    await new CandySyncApiClient("https://sync.example/").discover();
    assert.equal(receiver, globalThis);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("uses discovery, Basic bootstrap/enrollment and never sends E2EE passphrase", async () => {
  const requests: Array<{ url: string; init: RequestInit | undefined }> = [];
  const fetcher: typeof fetch = async (input, init) => {
    const url = String(input);
    requests.push({ url, init });
    if (url.endsWith("/.well-known/candy-sync")) {
      return Response.json({ protocol: "candy-sync", versions: [1], allowHttp: false, features: ["e2ee", "encrypted-device-icons"], limits: { payloadBytes: 1_048_576 } });
    }
    if (url.endsWith("/v1/bootstrap")) return Response.json({
      workspaceId: "workspace-1",
      initialized: false,
      kdf: { algorithm: "argon2id-v1", salt: "AAAAAAAAAAAAAAAAAAAAAA", memoryKiB: 65_536, iterations: 3, parallelism: 4 },
      recoveryEnvelope: null,
    });
    if (url.endsWith("/v1/devices") && init?.method === "GET") return Response.json({
      devices: [{
        deviceId: "device-1",
        publicKeyAlgorithm: "ECDH-P256-SPKI",
        publicKey: "public-key",
        encryptedName: { nonce: "name-nonce", ciphertext: "name-ciphertext" },
        encryptedIcon: { nonce: "icon-nonce", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA" },
        capabilities: ["tabs"],
        status: "active",
        createdAt: "2026-09-02T10:00:00Z",
        lastSeenAt: "2026-09-02T10:00:00Z",
      }],
    });
    if (url.endsWith("/v1/sync/push")) return Response.json({ cursor: "epoch-1.1", revisions: { "change-1": "1" } });
    return Response.json({ workspaceId: "workspace-1", deviceId: "device-1", token: "device-token", cursor: "epoch-1.0" }, { status: 201 });
  };
  const client = new CandySyncApiClient("https://sync.example/", fetcher);
  await client.discover();
  await client.bootstrap("alice", "auth-password");
  await client.enroll("alice", "auth-password", {
    deviceName: { nonce: "nonce", ciphertext: "ciphertext" },
    deviceIcon: { nonce: "icon-nonce", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA" },
    deviceKeyFingerprint: "fingerprint",
    publicKey: "public-key",
    capabilities: ["tabs"],
    recoveryEnvelope: { cryptoVersion: 1, nonce: "nonce", ciphertext: "ciphertext" },
  });
  const devices = await client.listDevices("device-token");
  assert.deepEqual(devices[0]?.encryptedIcon, { nonce: "icon-nonce", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA" });
  const enrollmentBody = JSON.parse(String(requests[2]?.init?.body)) as Record<string, unknown>;
  assert.deepEqual(enrollmentBody.encryptedIcon, { nonce: "icon-nonce", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA" });
  assert.equal("deviceIcon" in enrollmentBody, false);
  await client.push("device-token", {
    changeId: "change-1",
    deviceId: "device-1",
    entity: "tabs",
    entityId: "device-1",
    operation: "snapshot",
    baseRevision: "0",
    schemaVersion: 1,
    cryptoVersion: 1,
    keyVersion: 1,
    nonce: "nonce",
    ciphertext: "ciphertext",
  });
  assert.deepEqual(requests.map(({ url, init }) => [new URL(url).pathname, init?.method]), [
    ["/.well-known/candy-sync", "GET"],
    ["/v1/bootstrap", "GET"],
    ["/v1/devices", "POST"],
    ["/v1/devices", "GET"],
    ["/v1/sync/push", "POST"],
  ]);
  assert.equal(new Headers(requests[0]?.init?.headers).has("Authorization"), false);
  assert.equal(requests[0]?.init?.redirect, "error");
  assert.match(String(new Headers(requests[1]?.init?.headers).get("Authorization")), /^Basic /u);
  assert.equal(new Headers(requests[3]?.init?.headers).get("Authorization"), "Bearer device-token");
  assert.equal(new Headers(requests[4]?.init?.headers).get("Authorization"), "Bearer device-token");
  assert.equal(new Headers(requests[4]?.init?.headers).get("Idempotency-Key"), "change-1");
  assert.doesNotMatch(JSON.stringify(requests), /E2EE|passphrase|auth-password/u);
});

test("stops reading a chunked response above the one MiB limit", async () => {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new Uint8Array(700_000));
      controller.enqueue(new Uint8Array(700_000));
      controller.close();
    },
  });
  const client = new CandySyncApiClient("https://sync.example/", async () => new Response(body));
  await assert.rejects(client.discover(), /too large/u);
});

test("rejects incompatible discovery responses", async () => {
  const client = new CandySyncApiClient("https://sync.example/", async () => Response.json({
    protocol: "other",
    versions: [2],
    limits: { payloadBytes: 1_048_576 },
  }));
  await assert.rejects(client.discover(), /Protocol v1/u);
});

test("never sends credentials to remote HTTP before explicit server approval", async () => {
  const requests: Array<{ url: string; init: RequestInit | undefined }> = [];
  const client = new CandySyncApiClient("http://sync.example/", async (input, init) => {
    requests.push({ url: String(input), init });
    return Response.json({
      protocol: "candy-sync",
      versions: [1],
      allowHttp: false,
      features: ["e2ee"],
      limits: { payloadBytes: 1_048_576 },
    });
  });
  await assert.rejects(client.bootstrap("alice", "secret"), /before credentials/u);
  assert.equal(requests.length, 0);
  await assert.rejects(client.discover(), /does not allow remote HTTP/u);
  assert.equal(requests.length, 1);
  assert.equal(new Headers(requests[0]?.init?.headers).has("Authorization"), false);
});

test("allows remote HTTP credentials after discovery advertises the server flag", async () => {
  const requests: Array<{ url: string; init: RequestInit | undefined }> = [];
  const client = new CandySyncApiClient("http://sync.example/", async (input, init) => {
    requests.push({ url: String(input), init });
    if (String(input).endsWith("/.well-known/candy-sync")) {
      return Response.json({
        protocol: "candy-sync",
        versions: [1],
        allowHttp: true,
        features: ["e2ee"],
        limits: { payloadBytes: 1_048_576 },
      });
    }
    return Response.json({
      workspaceId: "workspace-1",
      initialized: false,
      kdf: { algorithm: "argon2id-v1", salt: "AAAAAAAAAAAAAAAAAAAAAA", memoryKiB: 65_536, iterations: 3, parallelism: 4 },
      recoveryEnvelope: null,
    });
  });
  await client.discover();
  await client.bootstrap("alice", "secret");
  assert.equal(new Headers(requests[1]?.init?.headers).has("Authorization"), true);
});

test("pull requests one bounded page and accepts a large encrypted payload", async () => {
  let requestedUrl = "";
  const ciphertext = "A".repeat(100_000);
  const client = new CandySyncApiClient("https://sync.example/", async (input) => {
    requestedUrl = String(input);
    return Response.json({
      changes: [{
        changeId: "change-1",
        deviceId: "device-1",
        entity: "tabs",
        entityId: "device-1",
        operation: "snapshot",
        baseRevision: "0",
        revision: "1",
        schemaVersion: 1,
        cryptoVersion: 1,
        keyVersion: 1,
        nonce: "AAAAAAAAAAAAAAAA",
        ciphertext,
      }],
      nextCursor: "epoch.1",
      hasMore: true,
    });
  });
  const result = await client.pull("device-token", "epoch.0");
  assert.equal(result.changes[0]?.ciphertext.length, ciphertext.length);
  assert.equal(new URL(requestedUrl).searchParams.get("limit"), "100");
});

test("cross-device tab PUT preserves writer/target encrypted metadata", async () => {
  let request: { url: string; init?: RequestInit } | undefined;
  const client = new CandySyncApiClient("https://sync.example/", async (input, init) => {
    request = { url: String(input), ...(init ? { init } : {}) };
    return Response.json({ revision: "8", cursor: "epoch.8" });
  });
  const result = await client.putTabSnapshot("writer-token", "desktop-target", {
    changeId: "android-change-1",
    deviceId: "android-writer",
    entity: "tabs",
    entityId: "desktop-target",
    operation: "snapshot",
    baseRevision: "7",
    schemaVersion: 1,
    cryptoVersion: 1,
    keyVersion: 1,
    nonce: "AAAAAAAAAAAAAAAA",
    ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
  });
  assert.deepEqual(result, { revision: "8", cursor: "epoch.8" });
  assert.equal(new URL(request!.url).pathname, "/v1/devices/desktop-target/tabs");
  assert.equal(new Headers(request!.init?.headers).get("Idempotency-Key"), "android-change-1");
  assert.deepEqual(JSON.parse(String(request!.init?.body)), {
    changeId: "android-change-1",
    expectedRevision: "7",
    revision: "8",
    schemaVersion: 1,
    cryptoVersion: 1,
    keyVersion: 1,
    nonce: "AAAAAAAAAAAAAAAA",
    ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
  });
});

test("rejects device capabilities outside the protocol schema", async () => {
  const device = {
    deviceId: "device-1",
    publicKeyAlgorithm: "ECDH-P256-SPKI",
    publicKey: "public-key",
    encryptedName: { nonce: "name-nonce", ciphertext: "name-ciphertext" },
    encryptedIcon: { nonce: "icon-nonce", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA" },
    status: "active",
    createdAt: "2026-09-02T10:00:00Z",
    lastSeenAt: "2026-09-02T10:00:00Z",
  };
  for (const capabilities of [["tabs", "tabs"], ["tabs", "history"], [], ["groups", 1]]) {
    const client = new CandySyncApiClient("https://sync.example/", async () => Response.json({
      devices: [{ ...device, capabilities }],
    }));
    await assert.rejects(client.listDevices("device-token"), /capabilities/u);
  }
});
