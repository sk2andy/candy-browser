import assert from "node:assert/strict";
import test from "node:test";

import type { CandySyncApiClient } from "../src/protocol/api-client.js";
import { CandySyncRealtimeClient, parseRealtimeFrame, reconnectDelayMs } from "../src/realtime/realtime-client.js";

const change = {
  changeId: "change-1", mutationId: "mutation-1", workspaceId: "workspace-1", deviceId: "phone-1",
  entity: "tabs", entityId: "desktop-1", operation: "delta", baseRevision: "0", revision: "1",
  schemaVersion: 2, cryptoVersion: 1, keyVersion: 1, nonce: "AAAAAAAAAAAAAAAA", ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
};

test("realtime frame accepts committed deltas and rejects malformed or oversized data", () => {
  assert.deepEqual(parseRealtimeFrame(JSON.stringify({ type: "change", cursor: "epoch.1", change })), {
    type: "change", cursor: "epoch.1", change,
  });
  assert.equal(parseRealtimeFrame(JSON.stringify({ type: "pong" })), null);
  assert.throws(() => parseRealtimeFrame(JSON.stringify({ type: "change", cursor: "epoch.1", change: { ...change, revision: "3x" } })), /revision/u);
  assert.throws(() => parseRealtimeFrame("x".repeat(600_001)), /frame/u);
});

test("reconnect uses bounded exponential backoff with jitter", () => {
  assert.deepEqual([0, 1, 2, 3, 4, 5, 6, 20].map((attempt) => reconnectDelayMs(attempt, 0)), [1_000, 2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000]);
  assert.equal(reconnectDelayMs(0, 1), 1_250);
  assert.equal(reconnectDelayMs(20, 1), 75_000);
});

test("realtime client exchanges ticket then delivers committed encrypted frame", async () => {
  const listeners = new Map<string, Array<(event: MessageEvent | Event) => void>>();
  const socket = {
    readyState: 0,
    addEventListener: (type: string, listener: (event: MessageEvent | Event) => void) => {
      listeners.set(type, [...(listeners.get(type) ?? []), listener]);
    },
    send: () => undefined,
    close: () => undefined,
  } as unknown as WebSocket;
  const api = {
    createRealtimeTicket: async () => ({ ticket: "single-use", expiresAt: "2026-09-02T15:00:00Z" }),
    realtimeUrl: (ticket: string) => `wss://sync.example/v2/realtime?ticket=${ticket}`,
  } as unknown as CandySyncApiClient;
  let socketUrl = "";
  const frames: unknown[] = [];
  const client = new CandySyncRealtimeClient(api, "token", async (frame) => { frames.push(frame); }, (url) => {
    socketUrl = url; return socket;
  });
  client.start();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(socketUrl, "wss://sync.example/v2/realtime?ticket=single-use");
  for (const listener of listeners.get("message") ?? []) listener({ data: JSON.stringify({ type: "change", cursor: "epoch.1", change }) } as MessageEvent);
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.deepEqual(frames, [{ type: "change", cursor: "epoch.1", change }]);
  client.stop();
});
