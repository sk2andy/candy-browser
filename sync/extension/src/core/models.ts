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
  v2Cursor?: string;
  v2TabRevision?: string;
  v2Initialized?: boolean;
  v2DisabledTabIds?: string[];
  v2ReconciliationPending?: boolean;
  pendingTabChange?: EncryptedChange;
  /** Negotiated transport. Missing means legacy v1 until discovery succeeds. */
  protocolVersion?: 1 | 2;
  selection: SyncSelection;
  vault: VaultEnvelope;
}

interface TabMutationBase {
  schemaVersion: 2;
  mutationId: string;
  targetDeviceId: string;
}

export type TabMutation =
  | (TabMutationBase & { type: "open"; tab: TabSnapshotEntry })
  | (TabMutationBase & { type: "navigate"; candyId: string; url: string; title: string })
  | (TabMutationBase & { type: "close"; candyId: string })
  | (TabMutationBase & { type: "reorder"; orderedCandyIds: string[] })
  | (TabMutationBase & { type: "set-pinned"; candyId: string; pinned: boolean });

export type TabMutationDraft =
  | { type: "open"; tab: TabSnapshotEntry }
  | { type: "navigate"; candyId: string; url: string; title: string }
  | { type: "close"; candyId: string }
  | { type: "reorder"; orderedCandyIds: string[] }
  | { type: "set-pinned"; candyId: string; pinned: boolean };

export interface EncryptedTabDelta {
  changeId: string;
  mutationId: string;
  workspaceId: string;
  deviceId: string;
  entity: "tabs";
  entityId: string;
  operation: "delta";
  baseRevision: string;
  schemaVersion: 2;
  cryptoVersion: 1;
  keyVersion: 1;
  nonce: string;
  ciphertext: string;
}

export interface CommittedTabDelta extends EncryptedTabDelta {
  revision: string;
}

export interface TabDeltaOutboxItem {
  envelope: EncryptedTabDelta;
  mutationType: TabMutation["type"];
  candyId?: string;
  createdAt: string;
}

export interface TabDeltaOutbox {
  schemaVersion: 2;
  items: TabDeltaOutboxItem[];
}

export interface ReducedTab {
  candyId: string;
  windowId: number;
  groupId: number | null;
  active: boolean;
  url: string;
  title: string;
  pinned: boolean;
  index: number;
}

export interface TabMutationState {
  schemaVersion: 2;
  tabs: Record<string, ReducedTab>;
  tombstones: Record<string, true>;
  appliedMutationIds: string[];
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
