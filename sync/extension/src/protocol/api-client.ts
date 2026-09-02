import type { EncryptedChange, RecoveryEnvelope, SyncType } from "../core/models.js";
import type { RecoveryKdf } from "../crypto/crypto.js";
import { utf8 } from "../crypto/encoding.js";

export interface DiscoveryResponse {
  protocol: "candy-sync";
  versions: number[];
  features: string[];
  limits: { payloadBytes: number };
}

export interface BootstrapResponse {
  workspaceId: string;
  initialized: boolean;
  kdf: RecoveryKdf;
  recoveryEnvelope: RecoveryEnvelope | null;
}

export interface EnrollmentResponse {
  workspaceId: string;
  deviceId: string;
  token: string;
  cursor: string;
}

export interface EncryptedText {
  nonce: string;
  ciphertext: string;
}

export interface EncryptedDeviceRecord {
  deviceId: string;
  publicKeyAlgorithm: "ECDH-P256-SPKI";
  publicKey: string;
  encryptedName: EncryptedText;
  encryptedIcon: EncryptedText | null;
  capabilities: SyncType[];
  status: "active" | "revoked";
  createdAt: string;
  lastSeenAt: string;
}

export interface PushResponse {
  cursor: string;
  revisions: Record<string, string>;
}

export interface PulledChange extends EncryptedChange {
  revision: string;
}

export interface PullResponse {
  changes: PulledChange[];
  nextCursor: string;
  hasMore: boolean;
}

export class ApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

function basicAuthorization(username: string, password: string): string {
  const bytes = utf8(`${username}:${password}`);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return `Basic ${btoa(binary)}`;
}

async function readJson(response: Response): Promise<unknown> {
  if (!response.ok) throw new ApiError(`Server responded with HTTP ${response.status}.`, response.status);
  const length = Number(response.headers.get("content-length") ?? "0");
  if (Number.isFinite(length) && length > 1_048_576) throw new ApiError("Server response is too large.");
  const reader = response.body?.getReader();
  if (!reader) throw new ApiError("Server response has no readable body.");
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > 1_048_576) {
      await reader.cancel();
      throw new ApiError("Server response is too large.");
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  let text: string;
  try {
    text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw new ApiError("Server response is not valid UTF-8.");
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new ApiError("Server response is not valid JSON.");
  }
}

function requireObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new ApiError("Server response has an invalid format.");
  return value as Record<string, unknown>;
}

function requireString(value: unknown, name: string): string {
  if (typeof value !== "string" || value.length === 0 || value.length > 4_096) throw new ApiError(`Server field ${name} is invalid.`);
  return value;
}

function requireIdentifier(value: unknown, name: string): string {
  const identifier = requireString(value, name);
  if (identifier.length > 128 || !/^[A-Za-z0-9_-]+$/u.test(identifier)) throw new ApiError(`Server field ${name} is invalid.`);
  return identifier;
}

function requireRevision(value: unknown, name: string): string {
  const revision = requireString(value, name);
  if (!/^(0|[1-9][0-9]{0,18})$/u.test(revision) || BigInt(revision) > 9_223_372_036_854_775_807n) {
    throw new ApiError(`Server field ${name} is invalid.`);
  }
  return revision;
}

function requireCiphertext(value: unknown, name: string): string {
  if (typeof value !== "string" || value.length === 0 || value.length > 512 * 1_024) {
    throw new ApiError(`Server field ${name} is invalid.`);
  }
  return value;
}

function requireIconCiphertext(value: unknown, name: string): string {
  if (typeof value !== "string" || value.length < 22 || value.length > 4_096) {
    throw new ApiError(`Server field ${name} is invalid.`);
  }
  return value;
}

function requireCapabilities(value: unknown): SyncType[] {
  if (!Array.isArray(value) || value.length < 1 || value.length > 16) throw new ApiError("Server device capabilities are invalid.");
  const allowed = new Set<SyncType>(["tabs", "bookmarks", "groups"]);
  const seen = new Set<SyncType>();
  const capabilities: SyncType[] = [];
  for (const item of value) {
    if (typeof item !== "string" || !allowed.has(item as SyncType) || seen.has(item as SyncType)) {
      throw new ApiError("Server device capabilities are invalid.");
    }
    const capability = item as SyncType;
    seen.add(capability);
    capabilities.push(capability);
  }
  return capabilities;
}

function requireRecoveryParameter(value: unknown, expected: number, name: string): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed !== expected) throw new ApiError(`Server field ${name} is invalid.`);
  return parsed;
}

export class CandySyncApiClient {
  constructor(private readonly endpoint: string, private readonly fetcher: typeof fetch = fetch) {}

  private url(path: string): string {
    return new URL(path.replace(/^\//u, ""), this.endpoint).href;
  }

  private async request(path: string, init: RequestInit): Promise<unknown> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);
    try {
      return await readJson(await this.fetcher(this.url(path), {
        ...init,
        redirect: "error",
        cache: "no-store",
        signal: controller.signal,
      }));
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (error instanceof DOMException && error.name === "AbortError") throw new ApiError("Server request timed out.");
      throw new ApiError("Server is unreachable.");
    } finally {
      clearTimeout(timeout);
    }
  }

  async discover(): Promise<DiscoveryResponse> {
    const object = requireObject(await this.request("/.well-known/candy-sync", { method: "GET" }));
    if (object.protocol !== "candy-sync" || !Array.isArray(object.versions) || !object.versions.includes(1)) {
      throw new ApiError("Server does not support Candy Sync Protocol v1.");
    }
    const limits = requireObject(object.limits);
    const payloadBytes = Number(limits.payloadBytes);
    if (!Number.isSafeInteger(payloadBytes) || payloadBytes < 1_024) throw new ApiError("Server reports invalid limits.");
    return {
      protocol: "candy-sync",
      versions: object.versions.filter((value): value is number => Number.isSafeInteger(value)),
      features: Array.isArray(object.features) ? object.features.filter((value): value is string => typeof value === "string") : [],
      limits: { payloadBytes },
    };
  }

  async bootstrap(username: string, password: string): Promise<BootstrapResponse> {
    const object = requireObject(await this.request("/v1/bootstrap", {
      method: "GET",
      headers: { Authorization: basicAuthorization(username, password) },
    }));
    const kdf = requireObject(object.kdf);
    const recovery = object.recoveryEnvelope === null || object.recoveryEnvelope === undefined
      ? null
      : requireObject(object.recoveryEnvelope);
    return {
      workspaceId: requireString(object.workspaceId, "workspaceId"),
      initialized: object.initialized === true,
      kdf: {
        algorithm: kdf.algorithm === "argon2id-v1" ? "argon2id-v1" : (() => { throw new ApiError("Server reports an invalid KDF."); })(),
        salt: requireString(kdf.salt, "kdf.salt"),
        memoryKiB: requireRecoveryParameter(kdf.memoryKiB, 65_536, "kdf.memoryKiB"),
        iterations: requireRecoveryParameter(kdf.iterations, 3, "kdf.iterations"),
        parallelism: requireRecoveryParameter(kdf.parallelism, 4, "kdf.parallelism"),
      },
      recoveryEnvelope: recovery ? {
        cryptoVersion: recovery.cryptoVersion === 1 ? 1 : (() => { throw new ApiError("Server reports an invalid recovery envelope."); })(),
        nonce: requireString(recovery.nonce, "recoveryEnvelope.nonce"),
        ciphertext: requireString(recovery.ciphertext, "recoveryEnvelope.ciphertext"),
      } : null,
    };
  }

  async enroll(
    username: string,
    password: string,
    input: {
      deviceName: EncryptedText;
      deviceIcon: EncryptedText;
      deviceKeyFingerprint: string;
      publicKey: string;
      capabilities: SyncType[];
      recoveryEnvelope?: RecoveryEnvelope;
    },
  ): Promise<EnrollmentResponse> {
    const object = requireObject(await this.request("/v1/devices", {
      method: "POST",
      headers: { Authorization: basicAuthorization(username, password), "Content-Type": "application/json" },
      body: JSON.stringify({
        encryptedName: input.deviceName,
        encryptedIcon: input.deviceIcon,
        deviceKeyFingerprint: input.deviceKeyFingerprint,
        publicKeyAlgorithm: "ECDH-P256-SPKI",
        publicKey: input.publicKey,
        capabilities: input.capabilities,
        ...(input.recoveryEnvelope ? { recoveryEnvelope: input.recoveryEnvelope } : {}),
      }),
    }));
    return {
      workspaceId: requireIdentifier(object.workspaceId, "workspaceId"),
      deviceId: requireIdentifier(object.deviceId, "deviceId"),
      token: requireString(object.token, "token"),
      cursor: requireString(object.cursor, "cursor"),
    };
  }

  async listDevices(token: string): Promise<EncryptedDeviceRecord[]> {
    const object = requireObject(await this.request("/v1/devices", {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    }));
    if (!Array.isArray(object.devices) || object.devices.length > 1_000) throw new ApiError("Server field devices is invalid.");
    return object.devices.map((raw): EncryptedDeviceRecord => {
      const value = requireObject(raw);
      const encryptedName = requireObject(value.encryptedName);
      const encryptedIcon = value.encryptedIcon === null || value.encryptedIcon === undefined
        ? null
        : requireObject(value.encryptedIcon);
      if (value.publicKeyAlgorithm !== "ECDH-P256-SPKI") throw new ApiError("Server device uses unsupported key algorithm.");
      if (value.status !== "active" && value.status !== "revoked") throw new ApiError("Server device has invalid status.");
      return {
        deviceId: requireIdentifier(value.deviceId, "devices.deviceId"),
        publicKeyAlgorithm: "ECDH-P256-SPKI",
        publicKey: requireString(value.publicKey, "devices.publicKey"),
        encryptedName: {
          nonce: requireString(encryptedName.nonce, "devices.encryptedName.nonce"),
          ciphertext: requireCiphertext(encryptedName.ciphertext, "devices.encryptedName.ciphertext"),
        },
        encryptedIcon: encryptedIcon ? {
          nonce: requireString(encryptedIcon.nonce, "devices.encryptedIcon.nonce"),
          ciphertext: requireIconCiphertext(encryptedIcon.ciphertext, "devices.encryptedIcon.ciphertext"),
        } : null,
        capabilities: requireCapabilities(value.capabilities),
        status: value.status,
        createdAt: requireString(value.createdAt, "devices.createdAt"),
        lastSeenAt: requireString(value.lastSeenAt, "devices.lastSeenAt"),
      };
    });
  }

  async push(token: string, change: EncryptedChange): Promise<PushResponse> {
    const object = requireObject(await this.request("/v1/sync/push", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": change.changeId,
      },
      body: JSON.stringify({ changes: [change] }),
    }));
    const revisionObject = requireObject(object.revisions);
    const revisions: Record<string, string> = {};
    for (const [changeId, revision] of Object.entries(revisionObject)) revisions[changeId] = requireString(revision, `revisions.${changeId}`);
    return { cursor: requireString(object.cursor, "cursor"), revisions };
  }

  async pull(token: string, after = ""): Promise<PullResponse> {
    const object = requireObject(await this.request(`/v1/sync/pull?after=${encodeURIComponent(after)}&limit=100`, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    }));
    if (!Array.isArray(object.changes)) throw new ApiError("Server field changes is invalid.");
    const changes = object.changes.map((raw): PulledChange => {
      const value = requireObject(raw);
      if (value.entity !== "tabs" || value.operation !== "snapshot") throw new ApiError("Server change has an unsupported type.");
      if (value.schemaVersion !== 1 || value.cryptoVersion !== 1 || value.keyVersion !== 1) throw new ApiError("Server change has an unsupported version.");
      return {
        changeId: requireIdentifier(value.changeId, "changeId"),
        deviceId: requireIdentifier(value.deviceId, "deviceId"),
        entity: "tabs",
        entityId: requireIdentifier(value.entityId, "entityId"),
        operation: "snapshot",
        baseRevision: requireRevision(value.baseRevision, "baseRevision"),
        revision: requireRevision(value.revision, "revision"),
        schemaVersion: 1,
        cryptoVersion: 1,
        keyVersion: 1,
        nonce: requireString(value.nonce, "nonce"),
        ciphertext: requireCiphertext(value.ciphertext, "ciphertext"),
      };
    });
    return {
      changes,
      nextCursor: requireString(object.nextCursor, "nextCursor"),
      hasMore: object.hasMore === true,
    };
  }

  async putTabSnapshot(token: string, targetDeviceId: string, change: EncryptedChange): Promise<{ revision: string; cursor: string }> {
    if (change.entityId !== targetDeviceId) throw new ApiError("Target device does not match encrypted change metadata.");
	const expectedRevision = requireRevision(change.baseRevision, "baseRevision");
    const object = requireObject(await this.request(`/v1/devices/${encodeURIComponent(targetDeviceId)}/tabs`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": change.changeId,
      },
      body: JSON.stringify({
        changeId: change.changeId,
        expectedRevision,
        revision: (BigInt(expectedRevision) + 1n).toString(),
        schemaVersion: change.schemaVersion,
        cryptoVersion: change.cryptoVersion,
        keyVersion: change.keyVersion,
        nonce: change.nonce,
        ciphertext: change.ciphertext,
      }),
    }));
    return { revision: requireRevision(object.revision, "revision"), cursor: requireString(object.cursor, "cursor") };
  }
}
