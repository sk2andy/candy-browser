import assert from "node:assert/strict";
import test from "node:test";

import type { EncryptedTabDelta } from "../src/core/models.js";
import { decryptTabMutation, encryptTabMutation } from "../src/crypto/crypto.js";
import { base64UrlToBytes, bytesToBase64Url } from "../src/crypto/encoding.js";

const fields = {
  changeId: "change-1",
  mutationId: "mutation-1",
  workspaceId: "workspace-1",
  deviceId: "phone-1",
  entity: "tabs" as const,
  entityId: "desktop-1",
  operation: "delta" as const,
  baseRevision: "7",
  schemaVersion: 2 as const,
  cryptoVersion: 1 as const,
  keyVersion: 1 as const,
};

const mutation = {
  schemaVersion: 2 as const,
  mutationId: "mutation-1",
  targetDeviceId: "desktop-1",
  type: "navigate" as const,
  candyId: "tab-1",
  url: "https://example.com/path",
  title: "Example",
};

test("tab delta matches fixed HKDF/AES-GCM known-answer vector", async () => {
  const key = Uint8Array.from({ length: 32 }, (_, index) => index);
  const nonce = Uint8Array.from({ length: 12 }, (_, index) => 0xa0 + index);
  const encrypted = await encryptTabMutation(key, fields, mutation, nonce);
  assert.equal(encrypted.nonce, "oKGio6Slpqeoqaqr");
  assert.equal(encrypted.ciphertext, "5Kht0grOpg8vOfI8gpO4y9SV3GJxOCqB23ihkpH4rukEYkaEEnsrFrgi9dcNep-k4YIwWLXo13ejkU9eMmawkb05Z1DxCUXdB8vRUWHbHYnPZMtIZRMRpbCrSksm9lNsAyB-_RQLINh6mCzZH5-jco6XzHgd6m0xKuFVE_ESwVMZ30fAFDMnqtVPzN2js72qI9wRb3GiT688AOOb8pcosY6jofTVak4qlERI8LAsPSEU");
  assert.deepEqual(await decryptTabMutation(key, encrypted), mutation);
});

test("tab delta rejects tampering and every security-bound routing field", async () => {
  const key = new Uint8Array(32).fill(9);
  const encrypted = await encryptTabMutation(key, fields, mutation);
  const ciphertext = base64UrlToBytes(encrypted.ciphertext);
  ciphertext[0] = (ciphertext[0] ?? 0) ^ 1;
  await assert.rejects(decryptTabMutation(key, { ...encrypted, ciphertext: bytesToBase64Url(ciphertext) }));
  const attacks: Array<Partial<EncryptedTabDelta>> = [
    { workspaceId: "workspace-2" }, { deviceId: "phone-2" }, { entityId: "desktop-2" },
    { changeId: "change-2" }, { mutationId: "mutation-2" }, { baseRevision: "8" },
  ];
  for (const attack of attacks) await assert.rejects(decryptTabMutation(key, { ...encrypted, ...attack }));
});

test("tab delta plaintext parser rejects extra fields and internal URLs after authenticated decryption", async () => {
  const key = new Uint8Array(32).fill(3);
  await assert.rejects(encryptTabMutation(key, fields, { ...mutation, extra: true }), /fields/u);
  await assert.rejects(encryptTabMutation(key, fields, { ...mutation, url: "chrome://settings/" }), /URL/u);
});

test("tab delta accepts the largest protocol reorder payload", async () => {
  const key = new Uint8Array(32).fill(5);
  const orderedCandyIds = Array.from({ length: 1_000 }, (_, index) =>
    `tab-${index.toString().padStart(4, "0")}-${"x".repeat(119)}`);
  const largest = {
    schemaVersion: 2 as const,
    mutationId: mutation.mutationId,
    targetDeviceId: mutation.targetDeviceId,
    type: "reorder" as const,
    orderedCandyIds,
  };
  const encrypted = await encryptTabMutation(key, fields, largest);
  assert.deepEqual(await decryptTabMutation(key, encrypted), largest);
});
