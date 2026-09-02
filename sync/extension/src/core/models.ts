export const SYNC_TYPES = ["tabs", "bookmarks", "groups"] as const;

export type SyncType = typeof SYNC_TYPES[number];

export interface SyncSelection {
  tabs: boolean;
  bookmarks: boolean;
  groups: boolean;
}

export interface StoredSettings {
  schemaVersion: 1;
  endpoint: string;
  username: string;
  deviceName: string;
  deviceIconId: string;
  workspaceId: string;
  deviceId: string;
  cursor: string;
  tabRevision: string;
  pendingTabChange?: EncryptedChange;
  selection: SyncSelection;
  vault: VaultEnvelope;
}

export interface VaultEnvelope {
  cryptoVersion: 1;
  kdf: {
    name: "argon2id";
    salt: string;
    memoryKiB: number;
    iterations: number;
    parallelism: number;
  };
  nonce: string;
  ciphertext: string;
}

export interface VaultSecrets {
  workspaceKey: string;
  devicePrivateKeyPkcs8: string;
  deviceToken: string;
  workspaceId: string;
  deviceId: string;
}

export interface RecoveryEnvelope {
  cryptoVersion: 1;
  nonce: string;
  ciphertext: string;
}

export interface DeviceIconDescriptor {
  schemaVersion: 1;
  catalogId: string;
  accentHue: number;
}

export type SyncStatusCode =
  | "unconfigured"
  | "locked"
  | "ready"
  | "syncing"
  | "current"
  | "offline"
  | "auth-error"
  | "crypto-error"
  | "permission-required"
  | "incompatible";

export interface SyncStatus {
  code: SyncStatusCode;
  message: string;
  updatedAt: string;
  lastCursor?: string;
  pendingChanges?: number;
}

export interface TabSnapshotEntry {
  candyId: string;
  windowId: number;
  index: number;
  groupId: number | null;
  active: boolean;
  pinned: boolean;
  title: string;
  url: string;
}

export interface DeviceTabSnapshot {
  schemaVersion: 1;
  capturedAt: string;
  tabs: TabSnapshotEntry[];
}

export interface EncryptedChange {
  changeId: string;
  deviceId: string;
  entity: "tabs";
  entityId: string;
  operation: "snapshot";
  baseRevision: string;
  schemaVersion: 1;
  cryptoVersion: 1;
  keyVersion: 1;
  nonce: string;
  ciphertext: string;
}
