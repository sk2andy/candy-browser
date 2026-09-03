import assert from "node:assert/strict";
import test from "node:test";

import { argon2id } from "@noble/hashes/argon2.js";
import fc from "fast-check";

import type { EncryptedChange, VaultSecrets } from "../src/core/models.js";
import {
  createVault,
  createRecoveryEnvelope,
  decryptDeviceName,
  decryptDeviceIcon,
  decryptTabSnapshot,
  deriveDeviceIconDescriptor,
  encryptDeviceIcon,
  encryptDeviceName,
  encryptTabSnapshot,
  generateDeviceIdentity,
  randomBytes,
  unlockRecoveryEnvelope,
  unlockVault,
  validateVaultEnvelope,
} from "../src/crypto/crypto.js";
import { base64UrlToBytes, bytesToBase64Url, utf8 } from "../src/crypto/encoding.js";

test("matches RFC 9106 Argon2id test vector", () => {
  const result = argon2id(new Uint8Array(32).fill(1), new Uint8Array(16).fill(2), {
    t: 3,
    m: 32,
    p: 4,
    dkLen: 32,
    key: new Uint8Array(8).fill(3),
    personalization: new Uint8Array(12).fill(4),
  });
  assert.equal(Buffer.from(result).toString("hex"), "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659");
});

test("vault round-trips secrets and rejects a wrong passphrase", async () => {
  const secrets: VaultSecrets = {
    workspaceKey: bytesToBase64Url(randomBytes(32)),
    devicePrivateKeyPkcs8: bytesToBase64Url(randomBytes(96)),
    deviceToken: "token-secret",
    workspaceId: "workspace-1",
    deviceId: "device-1",
  };
  const envelope = await createVault(utf8("correct horse battery staple"), secrets);
  assert.deepEqual(await unlockVault(utf8("correct horse battery staple"), envelope), secrets);
  await assert.rejects(unlockVault(utf8("incorrect passphrase value"), envelope));
  assert.doesNotMatch(JSON.stringify(envelope), /token-secret|correct horse/u);
});

test("creates distinct importable device private keys and matching public fingerprints", async () => {
  const first = await generateDeviceIdentity();
  const second = await generateDeviceIdentity();
  assert.notEqual(bytesToBase64Url(first.privateKeyPkcs8), bytesToBase64Url(second.privateKeyPkcs8));
  assert.notEqual(first.fingerprint, second.fingerprint);
  await crypto.subtle.importKey("pkcs8", Uint8Array.from(first.privateKeyPkcs8).buffer, { name: "ECDH", namedCurve: "P-256" }, false, ["deriveKey", "deriveBits"]);
});

test("derives stable device-specific profile icons", () => {
  const firstFingerprint = bytesToBase64Url(Uint8Array.from({ length: 32 }, (_, index) => index));
  const secondFingerprint = bytesToBase64Url(Uint8Array.from({ length: 32 }, (_, index) => 255 - index));
  const iphone = deriveDeviceIconDescriptor(
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Mobile",
    firstFingerprint,
    5,
  );
  assert.deepEqual(
    deriveDeviceIconDescriptor("Mozilla/5.0 (iPhone) Mobile", firstFingerprint, 5),
    iphone,
  );
  assert.equal(iphone.catalogId, "phone");
  assert.notDeepEqual(deriveDeviceIconDescriptor("Mozilla/5.0 (Windows NT 10.0)", secondFingerprint), iphone);
  assert.equal(deriveDeviceIconDescriptor("Mozilla/5.0 (X11; CrOS x86_64)", firstFingerprint).catalogId, "computer");
  assert.equal(deriveDeviceIconDescriptor("Mozilla/5.0 (Macintosh)", firstFingerprint, 5).catalogId, "phone");
  assert.equal(deriveDeviceIconDescriptor("Mozilla/5.0", firstFingerprint, 0, "candy").catalogId, "candy");
  assert.throws(() => deriveDeviceIconDescriptor("Mozilla/5.0", "invalid"));
});

test("device icon round-trips with domain-separated authenticated encryption", async () => {
  const workspaceKey = randomBytes(32);
  const identity = await generateDeviceIdentity();
  const descriptor = deriveDeviceIconDescriptor("Mozilla/5.0 (Windows NT 10.0)", identity.fingerprint);
  const otherIdentity = await generateDeviceIdentity();
  const encrypted = await encryptDeviceIcon(workspaceKey, "workspace-1", identity.fingerprint, descriptor);
  assert.deepEqual(await decryptDeviceIcon(workspaceKey, "workspace-1", identity.fingerprint, encrypted), descriptor);
  assert.doesNotMatch(JSON.stringify(encrypted), /computer|accentHue|catalogId/u);
  const damaged = base64UrlToBytes(encrypted.ciphertext);
  damaged[0] = (damaged[0] ?? 0) ^ 1;
  await assert.rejects(decryptDeviceIcon(workspaceKey, "workspace-1", identity.fingerprint, { ...encrypted, ciphertext: bytesToBase64Url(damaged) }));
  await assert.rejects(decryptDeviceIcon(workspaceKey, "workspace-2", identity.fingerprint, encrypted));
  await assert.rejects(decryptDeviceIcon(workspaceKey, "workspace-1", otherIdentity.fingerprint, encrypted));
  const encryptedName = await encryptDeviceName(workspaceKey, "workspace-1", identity.fingerprint, "Desktop");
  assert.equal(await decryptDeviceName(workspaceKey, "workspace-1", identity.fingerprint, encryptedName), "Desktop");
  await assert.rejects(decryptDeviceName(workspaceKey, "workspace-1", otherIdentity.fingerprint, encryptedName));
  await assert.rejects(decryptDeviceName(workspaceKey, "workspace-2", identity.fingerprint, encryptedName));
  await assert.rejects(decryptDeviceIcon(workspaceKey, "workspace-1", identity.fingerprint, encryptedName));
});

test("recovery envelope round-trips workspace key and binds workspace AAD", async () => {
  const passphrase = utf8("correct horse battery staple");
  const workspaceKey = randomBytes(32);
  const kdf = {
    algorithm: "argon2id-v1" as const,
    salt: bytesToBase64Url(randomBytes(16)),
    memoryKiB: 65_536,
    iterations: 3,
    parallelism: 4,
  };
  const envelope = await createRecoveryEnvelope(passphrase, workspaceKey, "workspace-1", kdf);
  assert.deepEqual(await unlockRecoveryEnvelope(passphrase, envelope, "workspace-1", kdf), workspaceKey);
  await assert.rejects(unlockRecoveryEnvelope(passphrase, envelope, "workspace-2", kdf));
  assert.doesNotMatch(JSON.stringify(envelope), new RegExp(bytesToBase64Url(workspaceKey), "u"));
});

test("recovery KDF rejects downgraded and non-integral server parameters", async () => {
  const passphrase = utf8("correct horse battery staple");
  const workspaceKey = randomBytes(32);
  const base = {
    algorithm: "argon2id-v1" as const,
    salt: bytesToBase64Url(randomBytes(16)),
    memoryKiB: 65_536,
    iterations: 3,
    parallelism: 4,
  };
  await assert.rejects(createRecoveryEnvelope(passphrase, workspaceKey, "workspace-1", { ...base, memoryKiB: 16_384 }));
  await assert.rejects(createRecoveryEnvelope(passphrase, workspaceKey, "workspace-1", { ...base, iterations: 3.5 }));
  await assert.rejects(createRecoveryEnvelope(passphrase, workspaceKey, "workspace-1", { ...base, parallelism: 1 }));
});

test("vault rejects attacker-controlled excessive KDF parameters before derivation", () => {
  assert.throws(() => validateVaultEnvelope({
    cryptoVersion: 1,
    kdf: { name: "argon2id", salt: bytesToBase64Url(new Uint8Array(16)), memoryKiB: 16_384, iterations: 3, parallelism: 1 },
    nonce: bytesToBase64Url(new Uint8Array(12)),
    ciphertext: "A".repeat(100),
  }), /memory value/u);
  assert.throws(() => validateVaultEnvelope({
    cryptoVersion: 1,
    kdf: { name: "argon2id", salt: bytesToBase64Url(new Uint8Array(16)), memoryKiB: 1_000_000, iterations: 3, parallelism: 1 },
    nonce: bytesToBase64Url(new Uint8Array(12)),
    ciphertext: "AA",
  }));
  assert.throws(() => validateVaultEnvelope({
    cryptoVersion: 1,
    kdf: { name: "argon2id", salt: bytesToBase64Url(new Uint8Array(16)), memoryKiB: 65_536, iterations: 3, parallelism: 1 },
    nonce: bytesToBase64Url(new Uint8Array(12)),
    ciphertext: "A".repeat(200_000),
  }), /ciphertext size/u);
});

function changeFields(changeId = "change-1"): Omit<EncryptedChange, "nonce" | "ciphertext"> {
  return {
    changeId,
    deviceId: "device-1",
    entity: "tabs",
    entityId: "device-1",
    operation: "snapshot",
    baseRevision: "0",
    schemaVersion: 1,
    cryptoVersion: 1,
    keyVersion: 1,
  };
}

test("AES-GCM payload detects ciphertext and authenticated-metadata changes", async () => {
  const key = randomBytes(32);
  const encrypted = await encryptTabSnapshot(key, changeFields(), { tabs: [{ url: "https://secret.example/" }] });
  assert.deepEqual(await decryptTabSnapshot(key, encrypted), { tabs: [{ url: "https://secret.example/" }] });

  const damaged = base64UrlToBytes(encrypted.ciphertext);
  damaged[0] = (damaged[0] ?? 0) ^ 1;
  await assert.rejects(decryptTabSnapshot(key, { ...encrypted, ciphertext: bytesToBase64Url(damaged) }));
  await assert.rejects(decryptTabSnapshot(key, { ...encrypted, changeId: "attacker-change" }));
});

test("payload encryption round-trips arbitrary JSON-safe tab labels", async () => {
  await fc.assert(fc.asyncProperty(fc.string({ maxLength: 80 }), async (title) => {
    const key = new Uint8Array(32).fill(7);
    const encrypted = await encryptTabSnapshot(key, changeFields(crypto.randomUUID()), { title });
    assert.deepEqual(await decryptTabSnapshot(key, encrypted), { title });
  }), { numRuns: 25 });
});
