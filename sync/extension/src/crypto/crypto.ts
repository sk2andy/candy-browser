import { argon2idAsync } from "@noble/hashes/argon2.js";

import type {
  DeviceIconDescriptor,
  EncryptedChange,
  RecoveryEnvelope,
  VaultEnvelope,
  VaultSecrets,
} from "../core/models.js";
import { defaultDeviceIconId, DEVICE_ICON_IDS } from "../core/device-icon-catalog.js";
import { base64UrlToBytes, bytesToBase64Url, utf8 } from "./encoding.js";

export const ARGON2_PARAMETERS: Readonly<{ memoryKiB: number; iterations: number; parallelism: number }> = Object.freeze({
  memoryKiB: 65_536,
  iterations: 3,
  parallelism: 1,
});

const VAULT_AAD = utf8("candy-sync/local-vault/v1");
const MAX_VAULT_CIPHERTEXT_BYTES = 131_072;

export interface DeviceIdentity {
  privateKeyPkcs8: Uint8Array;
  publicKeySpki: Uint8Array;
  fingerprint: string;
}

export interface RecoveryKdf {
  algorithm: "argon2id-v1";
  salt: string;
  memoryKiB: number;
  iterations: number;
  parallelism: number;
}

function buffer(bytes: Uint8Array): ArrayBuffer {
  return Uint8Array.from(bytes).buffer;
}

export function randomBytes(length: number): Uint8Array {
  if (!Number.isSafeInteger(length) || length <= 0) throw new Error("Invalid random byte length");
  return crypto.getRandomValues(new Uint8Array(length));
}

async function deriveKek(passphrase: Uint8Array, salt: Uint8Array, parameters = ARGON2_PARAMETERS): Promise<CryptoKey> {
  if (passphrase.length === 0) throw new Error("Passphrase must not be empty");
  const bits = await argon2idAsync(passphrase, salt, {
    m: parameters.memoryKiB,
    t: parameters.iterations,
    p: parameters.parallelism,
    dkLen: 32,
  });
  try {
    return await crypto.subtle.importKey("raw", buffer(bits), "AES-GCM", false, ["encrypt", "decrypt"]);
  } finally {
    bits.fill(0);
  }
}

export async function generateDeviceIdentity(): Promise<DeviceIdentity> {
  const pair = await crypto.subtle.generateKey(
    { name: "ECDH", namedCurve: "P-256" },
    true,
    ["deriveKey", "deriveBits"],
  );
  const privateKeyPkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const publicKeySpki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  return {
    privateKeyPkcs8,
    publicKeySpki,
    fingerprint: await fingerprintDeviceKey(publicKeySpki),
  };
}

function validateRecoveryKdf(kdf: RecoveryKdf): Uint8Array {
  if (kdf.algorithm !== "argon2id-v1") throw new Error("Unsupported recovery KDF");
  if (!Number.isSafeInteger(kdf.memoryKiB) || kdf.memoryKiB !== 65_536) throw new Error("Invalid Argon2 memory value");
  if (!Number.isSafeInteger(kdf.iterations) || kdf.iterations !== 3) throw new Error("Invalid Argon2 iteration count");
  if (!Number.isSafeInteger(kdf.parallelism) || kdf.parallelism !== 4) throw new Error("Invalid Argon2 parallelism");
  const salt = base64UrlToBytes(kdf.salt);
  if (salt.length !== 16) throw new Error("Invalid recovery salt");
  return salt;
}

function recoveryAad(workspaceId: string): Uint8Array {
  return utf8(`candy-sync/recovery-envelope/v1/${workspaceId}`);
}

export async function createRecoveryEnvelope(
  passphrase: Uint8Array,
  workspaceKey: Uint8Array,
  workspaceId: string,
  kdf: RecoveryKdf,
): Promise<RecoveryEnvelope> {
  if (workspaceKey.length !== 32) throw new Error("Workspace-Key muss 32 Bytes lang sein");
  const nonce = randomBytes(12);
  const key = await deriveKek(passphrase, validateRecoveryKdf(kdf), kdf);
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(recoveryAad(workspaceId)) },
    key,
    buffer(workspaceKey),
  );
  return { cryptoVersion: 1, nonce: bytesToBase64Url(nonce), ciphertext: bytesToBase64Url(new Uint8Array(ciphertext)) };
}

export async function unlockRecoveryEnvelope(
  passphrase: Uint8Array,
  envelope: RecoveryEnvelope,
  workspaceId: string,
  kdf: RecoveryKdf,
): Promise<Uint8Array> {
  if (envelope.cryptoVersion !== 1) throw new Error("Unsupported recovery version");
  const nonce = base64UrlToBytes(envelope.nonce);
  const ciphertext = base64UrlToBytes(envelope.ciphertext);
  if (nonce.length !== 12 || ciphertext.length !== 48) throw new Error("Invalid recovery envelope");
  const key = await deriveKek(passphrase, validateRecoveryKdf(kdf), kdf);
  const workspaceKey = new Uint8Array(await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(recoveryAad(workspaceId)) },
    key,
    buffer(ciphertext),
  ));
  if (workspaceKey.length !== 32) throw new Error("Invalid workspace key");
  return workspaceKey;
}

export async function createVault(passphrase: Uint8Array, secrets: VaultSecrets): Promise<VaultEnvelope> {
  const salt = randomBytes(16);
  const nonce = randomBytes(12);
  const key = await deriveKek(passphrase, salt);
  const plaintext = utf8(JSON.stringify(secrets));
  try {
    const ciphertext = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(VAULT_AAD) },
      key,
      buffer(plaintext),
    );
    return {
      cryptoVersion: 1,
      kdf: { name: "argon2id", salt: bytesToBase64Url(salt), ...ARGON2_PARAMETERS },
      nonce: bytesToBase64Url(nonce),
      ciphertext: bytesToBase64Url(new Uint8Array(ciphertext)),
    };
  } finally {
    plaintext.fill(0);
  }
}

export async function unlockVault(passphrase: Uint8Array, envelope: VaultEnvelope): Promise<VaultSecrets> {
  validateVaultEnvelope(envelope);
  const key = await deriveKek(passphrase, base64UrlToBytes(envelope.kdf.salt), {
    memoryKiB: envelope.kdf.memoryKiB,
    iterations: envelope.kdf.iterations,
    parallelism: envelope.kdf.parallelism,
  });
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: buffer(base64UrlToBytes(envelope.nonce)), additionalData: buffer(VAULT_AAD) },
    key,
    buffer(base64UrlToBytes(envelope.ciphertext)),
  );
  const bytes = new Uint8Array(plaintext);
  try {
    const value = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)) as Partial<VaultSecrets>;
    for (const keyName of ["workspaceKey", "devicePrivateKeyPkcs8", "deviceToken", "workspaceId", "deviceId"] as const) {
      if (typeof value[keyName] !== "string" || value[keyName].length === 0) throw new Error("Vault contains invalid data");
    }
    return value as VaultSecrets;
  } finally {
    bytes.fill(0);
  }
}

export function validateVaultEnvelope(envelope: VaultEnvelope): void {
  if (envelope.cryptoVersion !== 1 || envelope.kdf.name !== "argon2id") throw new Error("Unsupported vault version");
  if (!Number.isSafeInteger(envelope.kdf.memoryKiB) || envelope.kdf.memoryKiB !== ARGON2_PARAMETERS.memoryKiB) throw new Error("Invalid Argon2 memory value");
  if (!Number.isSafeInteger(envelope.kdf.iterations) || envelope.kdf.iterations !== ARGON2_PARAMETERS.iterations) throw new Error("Invalid Argon2 iteration count");
  if (!Number.isSafeInteger(envelope.kdf.parallelism) || envelope.kdf.parallelism !== ARGON2_PARAMETERS.parallelism) throw new Error("Invalid Argon2 parallelism");
  if (envelope.kdf.salt.length !== 22 || base64UrlToBytes(envelope.kdf.salt).length !== 16) throw new Error("Invalid vault salt");
  if (envelope.nonce.length !== 16 || base64UrlToBytes(envelope.nonce).length !== 12) throw new Error("Invalid vault nonce");
  if (envelope.ciphertext.length < 22 || envelope.ciphertext.length > Math.ceil(MAX_VAULT_CIPHERTEXT_BYTES * 4 / 3)) {
    throw new Error("Invalid vault ciphertext size");
  }
  const ciphertextBytes = base64UrlToBytes(envelope.ciphertext).length;
  if (ciphertextBytes < 16 || ciphertextBytes > MAX_VAULT_CIPHERTEXT_BYTES) throw new Error("Invalid vault ciphertext size");
}

export async function fingerprintDeviceKey(deviceKey: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", buffer(deviceKey));
  return bytesToBase64Url(new Uint8Array(digest));
}

async function deviceNameKey(workspaceKey: Uint8Array, workspaceId: string, fingerprint: string): Promise<CryptoKey> {
  if (workspaceKey.length !== 32) throw new Error("Workspace key must be 32 bytes");
  validateDeviceFingerprint(fingerprint);
  const baseKey = await crypto.subtle.importKey("raw", buffer(workspaceKey), "HKDF", false, ["deriveKey"]);
  return crypto.subtle.deriveKey(
    {
      name: "HKDF",
      hash: "SHA-256",
      salt: buffer(utf8(workspaceId)),
      info: buffer(utf8(`candy-sync/v1/device-name/${fingerprint}`)),
    },
    baseKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"],
  );
}

function deviceNameAad(workspaceId: string, fingerprint: string): Uint8Array {
  return utf8(JSON.stringify(["candy-sync-device-name", 1, workspaceId, fingerprint]));
}

export async function encryptDeviceName(
  workspaceKey: Uint8Array,
  workspaceId: string,
  fingerprint: string,
  deviceName: string,
): Promise<{ nonce: string; ciphertext: string }> {
  if (deviceName.length < 1 || deviceName.length > 80) throw new Error("Invalid device name");
  const nonce = randomBytes(12);
  const key = await deviceNameKey(workspaceKey, workspaceId, fingerprint);
  const plaintext = utf8(deviceName);
  try {
    const ciphertext = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(deviceNameAad(workspaceId, fingerprint)) },
      key,
      buffer(plaintext),
    );
    return { nonce: bytesToBase64Url(nonce), ciphertext: bytesToBase64Url(new Uint8Array(ciphertext)) };
  } finally {
    plaintext.fill(0);
  }
}

export async function decryptDeviceName(
  workspaceKey: Uint8Array,
  workspaceId: string,
  fingerprint: string,
  encryptedName: { nonce: string; ciphertext: string },
): Promise<string> {
  const nonce = base64UrlToBytes(encryptedName.nonce);
  const ciphertext = base64UrlToBytes(encryptedName.ciphertext);
  if (nonce.length !== 12 || ciphertext.length < 17 || ciphertext.length > 4_096) throw new Error("Invalid encrypted device name");
  const key = await deviceNameKey(workspaceKey, workspaceId, fingerprint);
  const plaintext = new Uint8Array(await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(deviceNameAad(workspaceId, fingerprint)) },
    key,
    buffer(ciphertext),
  ));
  try {
    const name = new TextDecoder("utf-8", { fatal: true }).decode(plaintext);
    if (name.length < 1 || name.length > 80) throw new Error("Invalid device name");
    return name;
  } finally {
    plaintext.fill(0);
  }
}

export function deriveDeviceIconDescriptor(
  userAgent: string,
  fingerprint: string,
  maxTouchPoints = 0,
  selectedCatalogId?: string,
): DeviceIconDescriptor {
  const fingerprintBytes = base64UrlToBytes(fingerprint);
  if (fingerprintBytes.length !== 32) throw new Error("Invalid device-key fingerprint");
  if (!Number.isSafeInteger(maxTouchPoints) || maxTouchPoints < 0) throw new Error("Invalid touch-point count");
  const hueSeed = ((fingerprintBytes[0] ?? 0) << 8) | (fingerprintBytes[1] ?? 0);
  return {
    schemaVersion: 1,
    catalogId: selectedCatalogId ?? defaultDeviceIconId(userAgent.slice(0, 1_024), maxTouchPoints),
    accentHue: hueSeed % 360,
  };
}

function validateDeviceIconDescriptor(value: unknown): DeviceIconDescriptor {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid device icon descriptor");
  const descriptor = value as Partial<DeviceIconDescriptor>;
  const allowedKeys = new Set(["schemaVersion", "catalogId", "accentHue"]);
  if (Object.keys(descriptor).length !== allowedKeys.size || Object.keys(descriptor).some((key) => !allowedKeys.has(key))) {
    throw new Error("Invalid device icon descriptor fields");
  }
  if (descriptor.schemaVersion !== 1 || typeof descriptor.catalogId !== "string" || !DEVICE_ICON_IDS.has(descriptor.catalogId)) {
    throw new Error("Unsupported device icon descriptor");
  }
  if (!Number.isSafeInteger(descriptor.accentHue) || descriptor.accentHue! < 0 || descriptor.accentHue! > 359) throw new Error("Invalid device icon accent");
  return descriptor as DeviceIconDescriptor;
}

function validateDeviceFingerprint(fingerprint: string): void {
  if (base64UrlToBytes(fingerprint).length !== 32) throw new Error("Invalid device-key fingerprint");
}

async function deviceIconKey(workspaceKey: Uint8Array, workspaceId: string, fingerprint: string): Promise<CryptoKey> {
  if (workspaceKey.length !== 32) throw new Error("Workspace key must be 32 bytes");
  validateDeviceFingerprint(fingerprint);
  const baseKey = await crypto.subtle.importKey("raw", buffer(workspaceKey), "HKDF", false, ["deriveKey"]);
  return crypto.subtle.deriveKey(
    {
      name: "HKDF",
      hash: "SHA-256",
      salt: buffer(utf8(workspaceId)),
      info: buffer(utf8(`candy-sync/v1/device-icon/${fingerprint}`)),
    },
    baseKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"],
  );
}

function deviceIconAad(workspaceId: string, fingerprint: string): Uint8Array {
  return utf8(JSON.stringify(["candy-sync-device-icon", 1, workspaceId, fingerprint]));
}

export async function encryptDeviceIcon(
  workspaceKey: Uint8Array,
  workspaceId: string,
  fingerprint: string,
  descriptor: DeviceIconDescriptor,
): Promise<{ nonce: string; ciphertext: string }> {
  const nonce = randomBytes(12);
  const plaintext = utf8(JSON.stringify(validateDeviceIconDescriptor(descriptor)));
  try {
    const key = await deviceIconKey(workspaceKey, workspaceId, fingerprint);
    const ciphertext = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(deviceIconAad(workspaceId, fingerprint)) },
      key,
      buffer(plaintext),
    );
    return { nonce: bytesToBase64Url(nonce), ciphertext: bytesToBase64Url(new Uint8Array(ciphertext)) };
  } finally {
    plaintext.fill(0);
  }
}

export async function decryptDeviceIcon(
  workspaceKey: Uint8Array,
  workspaceId: string,
  fingerprint: string,
  encryptedIcon: { nonce: string; ciphertext: string },
): Promise<DeviceIconDescriptor> {
  const nonce = base64UrlToBytes(encryptedIcon.nonce);
  const ciphertext = base64UrlToBytes(encryptedIcon.ciphertext);
  if (nonce.length !== 12 || ciphertext.length < 17 || ciphertext.length > 4_096) throw new Error("Invalid encrypted device icon");
  const key = await deviceIconKey(workspaceKey, workspaceId, fingerprint);
  const plaintext = new Uint8Array(await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(deviceIconAad(workspaceId, fingerprint)) },
    key,
    buffer(ciphertext),
  ));
  try {
    return validateDeviceIconDescriptor(JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(plaintext)) as unknown);
  } finally {
    plaintext.fill(0);
  }
}

async function derivePayloadKey(workspaceKey: Uint8Array, targetDeviceId: string): Promise<CryptoKey> {
  const baseKey = await crypto.subtle.importKey("raw", buffer(workspaceKey), "HKDF", false, ["deriveKey"]);
  return crypto.subtle.deriveKey(
    { name: "HKDF", hash: "SHA-256", salt: buffer(utf8(targetDeviceId)), info: buffer(utf8("candy-sync/v1/payload/tabs")) },
    baseKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"],
  );
}

export function changeAad(change: Omit<EncryptedChange, "nonce" | "ciphertext">): Uint8Array {
  return utf8(JSON.stringify([
    "candy-sync-change",
    change.cryptoVersion,
    change.keyVersion,
    change.schemaVersion,
    change.deviceId,
    change.changeId,
    change.entity,
    change.entityId,
    change.operation,
    change.baseRevision,
  ]));
}

export async function encryptTabSnapshot(
  workspaceKey: Uint8Array,
  change: Omit<EncryptedChange, "nonce" | "ciphertext">,
  snapshot: unknown,
): Promise<EncryptedChange> {
  const nonce = randomBytes(12);
  const plaintext = utf8(JSON.stringify(snapshot));
  try {
    const key = await derivePayloadKey(workspaceKey, change.entityId);
    const ciphertext = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: buffer(nonce), additionalData: buffer(changeAad(change)) },
      key,
      buffer(plaintext),
    );
    return { ...change, nonce: bytesToBase64Url(nonce), ciphertext: bytesToBase64Url(new Uint8Array(ciphertext)) };
  } finally {
    plaintext.fill(0);
  }
}

export async function decryptTabSnapshot(workspaceKey: Uint8Array, change: EncryptedChange): Promise<unknown> {
  const { nonce, ciphertext, ...aadFields } = change;
  const key = await derivePayloadKey(workspaceKey, change.entityId);
  const plaintext = new Uint8Array(await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: buffer(base64UrlToBytes(nonce)), additionalData: buffer(changeAad(aadFields)) },
    key,
    buffer(base64UrlToBytes(ciphertext)),
  ));
  try {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(plaintext));
  } finally {
    plaintext.fill(0);
  }
}
