import { hasSyncPermissions } from "../browser-adapters/permissions.js";
import {
  applyTabMutation, applyTabSnapshot, collectTabSnapshot, mutationForCreatedTab,
  mutationForMovedTab, mutationForRemovedTab, mutationsForUpdatedTab,
} from "../browser-adapters/tabs.js";
import type {
  CommittedTabDelta, EncryptedChange, StoredSettings, SyncSelection, SyncStatus,
  TabDeltaOutbox, TabMutation, TabMutationDraft, VaultSecrets,
} from "../core/models.js";
import {
  classifyDeltaRevision, classifyRealtimeCursor, monotonicCursor, mutationCandyId, nextOutboxAction,
  PullCursorGuard, reconciliationDrafts, reduceTabMutation, shouldApplyMutationToBrowser,
} from "../core/tab-mutation-rules.js";
import { decryptTabMutation, decryptTabSnapshot, encryptTabMutation, encryptTabSnapshot } from "../crypto/crypto.js";
import { base64UrlToBytes } from "../crypto/encoding.js";
import { extensionApi } from "../platform/webextension.js";
import { ApiError, CandySyncApiClient, type DiscoveryResponse } from "../protocol/api-client.js";
import { CandySyncRealtimeClient, type RealtimeChangeFrame } from "../realtime/realtime-client.js";
import {
  loadSessionSecrets, loadSettings, loadTabDeltaOutbox, loadTabMutationState,
  saveSettings, saveSettingsAndTabDeltaOutbox, saveStatus, saveTabDeltaOutbox, saveTabMutationState,
} from "../storage/stores.js";

const PERIODIC_ALARM = "candy-sync-periodic";
const MAX_IMMEDIATE_SYNC_PASSES = 3;
let syncPromise: Promise<void> | null = null;
let syncRequested = false;
let localTabsDirty = true;
let applyingRemoteChange = false;
let operationTail: Promise<void> = Promise.resolve();
let realtime: CandySyncRealtimeClient | null = null;
let realtimeIdentity = "";

function enqueueOperation<T>(operation: () => Promise<T>): Promise<T> {
  const result = operationTail.then(operation, operation);
  operationTail = result.then(() => undefined, () => undefined);
  return result;
}

function status(code: SyncStatus["code"], message: string, extra: Partial<SyncStatus> = {}): SyncStatus {
  return { code, message, updatedAt: new Date().toISOString(), ...extra };
}

function revision(value: string): bigint {
  if (!/^(0|[1-9][0-9]{0,18})$/u.test(value)) throw new Error("Server returned an invalid revision.");
  const parsed = BigInt(value);
  if (parsed > 9_223_372_036_854_775_807n) throw new Error("Server returned an invalid revision.");
  return parsed;
}

function supportsV2(discovery: DiscoveryResponse): boolean {
  return discovery.versions.includes(2) && discovery.features.includes("tab-mutations-v2") && discovery.features.includes("realtime");
}

function deltaMetadata(settings: StoredSettings, baseRevision: string, mutationId: string) {
  return {
    changeId: crypto.randomUUID(), mutationId, workspaceId: settings.workspaceId, deviceId: settings.deviceId,
    entity: "tabs" as const, entityId: settings.deviceId, operation: "delta" as const, baseRevision,
    schemaVersion: 2 as const, cryptoVersion: 1 as const, keyVersion: 1 as const,
  };
}

async function queueTabMutation(settings: StoredSettings, workspaceKey: Uint8Array, draft: TabMutationDraft): Promise<void> {
  const outbox = await loadTabDeltaOutbox();
  const action = nextOutboxAction(outbox.items, draft);
  const retained = outbox.items.slice(0, action.keep);
  const mutationId = crypto.randomUUID();
  const mutation = { schemaVersion: 2, mutationId, targetDeviceId: settings.deviceId, ...draft } as TabMutation;
  if (action.skipIncoming) {
    await saveTabDeltaOutbox({ schemaVersion: 2, items: retained });
    const state = await loadTabMutationState();
    await saveTabMutationState(reduceTabMutation(state, mutation).state);
    return;
  }
  const baseRevision = retained.length > 0
    ? (revision(retained.at(-1)!.envelope.baseRevision) + 1n).toString()
    : (outbox.items[action.keep]?.envelope.baseRevision ?? settings.v2TabRevision ?? "0");
  const envelope = await encryptTabMutation(workspaceKey, deltaMetadata(settings, baseRevision, mutationId), mutation);
  const candyId = mutationCandyId(mutation);
  await saveTabDeltaOutbox({
    schemaVersion: 2,
    items: [...retained, { envelope, mutationType: mutation.type, ...(candyId ? { candyId } : {}), createdAt: new Date().toISOString() }],
  });
  const state = await loadTabMutationState();
  await saveTabMutationState(reduceTabMutation(state, mutation).state);
}

async function rebaseOutbox(settings: StoredSettings, workspaceKey: Uint8Array, outbox: TabDeltaOutbox): Promise<TabDeltaOutbox> {
  let baseRevision = settings.v2TabRevision ?? "0";
  const items: TabDeltaOutbox["items"] = [];
  for (const item of outbox.items) {
    if (item.envelope.baseRevision === baseRevision) {
      items.push(item);
    } else {
      const mutation = await decryptTabMutation(workspaceKey, item.envelope);
      const envelope = await encryptTabMutation(workspaceKey, deltaMetadata(settings, baseRevision, item.envelope.mutationId), mutation);
      items.push({ ...item, envelope });
    }
    baseRevision = (revision(baseRevision) + 1n).toString();
  }
  const rebased = { schemaVersion: 2 as const, items };
  await saveTabDeltaOutbox(rebased);
  return rebased;
}

async function applyCommittedDelta(
  settings: StoredSettings,
  workspaceKey: Uint8Array,
  change: CommittedTabDelta,
  cursor: string,
): Promise<{ settings: StoredSettings; gap: boolean; applied: boolean }> {
  if (change.workspaceId !== settings.workspaceId) throw new Error("Server returned a delta for another workspace.");
  if (change.entityId !== settings.deviceId) return { settings: { ...settings, v2Cursor: cursor }, gap: false, applied: false };
  const classification = classifyDeltaRevision(settings.v2TabRevision ?? "0", change.baseRevision, change.revision);
  if (classification === "replay") return { settings: { ...settings, v2Cursor: cursor }, gap: false, applied: false };
  if (classification === "gap") {
    return { settings, gap: true, applied: false };
  }
  const mutation = await decryptTabMutation(workspaceKey, change);
  const state = await loadTabMutationState();
  const applyToBrowser = shouldApplyMutationToBrowser(mutation, state, change.deviceId, settings.deviceId);
  const reduced = reduceTabMutation(state, mutation);
  if (applyToBrowser) {
    applyingRemoteChange = true;
    try { await applyTabMutation(mutation); } finally { applyingRemoteChange = false; }
  }
  await saveTabMutationState(reduced.state);
  return { settings: { ...settings, v2Cursor: cursor, v2TabRevision: change.revision }, gap: false, applied: applyToBrowser };
}

async function pullV2(
  api: CandySyncApiClient,
  settings: StoredSettings,
  secrets: VaultSecrets,
  workspaceKey: Uint8Array,
): Promise<{ settings: StoredSettings; applied: number }> {
  let current = settings;
  let applied = 0;
  const pagination = new PullCursorGuard();
  while (true) {
    const requestedCursor = current.v2Cursor ?? "";
    const page = await api.pullDeltas(secrets.deviceToken, current.v2Cursor ?? "");
    pagination.accept(requestedCursor, page.nextCursor, page.hasMore);
    const confirmedMutationIds = new Set<string>();
    for (const change of page.changes) {
      const result = await applyCommittedDelta(current, workspaceKey, change, page.nextCursor);
      if (result.gap) throw new Error("Server returned a non-contiguous tab delta revision.");
      current = result.settings;
      if (result.applied) applied += 1;
      if (change.deviceId === settings.deviceId && change.entityId === settings.deviceId) {
        confirmedMutationIds.add(change.mutationId);
      }
    }
    current = { ...current, v2Cursor: page.nextCursor };
    let outbox = await loadTabDeltaOutbox();
    let confirmedPrefix = 0;
    while (confirmedPrefix < outbox.items.length &&
      confirmedMutationIds.has(outbox.items[confirmedPrefix]!.envelope.mutationId)) confirmedPrefix += 1;
    outbox = confirmedPrefix > 0
      ? { schemaVersion: 2, items: outbox.items.slice(confirmedPrefix) }
      : outbox;
    await saveSettingsAndTabDeltaOutbox(current, outbox);
    if (!page.hasMore) return { settings: current, applied };
  }
}

async function reconcileV2AfterOptIn(settings: StoredSettings, workspaceKey: Uint8Array): Promise<StoredSettings> {
  if (!settings.v2Initialized || !settings.v2ReconciliationPending) return settings;
  const snapshot = await collectTabSnapshot();
  for (const draft of reconciliationDrafts(settings.v2DisabledTabIds ?? [], snapshot.tabs)) {
    await queueTabMutation(settings, workspaceKey, draft);
  }
  const latest = await loadSettings();
  if (!latest || latest.deviceId !== settings.deviceId) throw new Error("Sync configuration changed during tab reconciliation.");
  const { v2DisabledTabIds: _baseline, v2ReconciliationPending: _pending, ...reconciled } = latest;
  const next = reconciled as StoredSettings;
  await saveSettings(next);
  localTabsDirty = false;
  return next;
}

async function bootstrapV2Outbox(settings: StoredSettings, workspaceKey: Uint8Array): Promise<void> {
  const outbox = await loadTabDeltaOutbox();
  if (settings.v2Initialized) return;
  if (outbox.items.length === 0) {
    const snapshot = await collectTabSnapshot();
    for (const tab of snapshot.tabs) {
      await queueTabMutation(settings, workspaceKey, {
      type: "open", tab,
      });
    }
  }
  const latest = await loadSettings();
  if (!latest || latest.deviceId !== settings.deviceId) throw new Error("Sync configuration changed during v2 initialization.");
  await saveSettings({ ...latest, protocolVersion: 2, v2Initialized: true });
  localTabsDirty = false;
}

async function synchronizeV2(
  api: CandySyncApiClient,
  settings: StoredSettings,
  secrets: VaultSecrets,
  workspaceKey: Uint8Array,
): Promise<void> {
  settings = {
    ...settings,
    protocolVersion: 2,
    v2TabRevision: settings.v2TabRevision ?? settings.tabRevision ?? "0",
  };
  settings = await reconcileV2AfterOptIn(settings, workspaceKey);
  const pulled = await pullV2(api, settings, secrets, workspaceKey);
  settings = { ...pulled.settings, protocolVersion: 2 };
  await saveSettings(settings);
  let outbox = await loadTabDeltaOutbox();
  if (outbox.items.length > 0 && outbox.items[0]!.envelope.baseRevision !== (settings.v2TabRevision ?? "0")) {
    outbox = await rebaseOutbox(settings, workspaceKey, outbox);
  }
  await bootstrapV2Outbox(settings, workspaceKey);
  settings = (await loadSettings()) ?? settings;
  outbox = await loadTabDeltaOutbox();
  let pushed = 0;
  while (outbox.items.length > 0) {
    const item = outbox.items[0]!;
    if (item.envelope.workspaceId !== settings.workspaceId || item.envelope.deviceId !== settings.deviceId ||
        item.envelope.entityId !== settings.deviceId) throw new Error("Stored tab delta has invalid routing metadata.");
    const verifiedMutation = await decryptTabMutation(workspaceKey, item.envelope);
    if (verifiedMutation.type !== item.mutationType || mutationCandyId(verifiedMutation) !== item.candyId) {
      throw new Error("Stored tab delta metadata does not match its ciphertext.");
    }
    const response = await api.pushDelta(secrets.deviceToken, item.envelope);
    const confirmedRevision = response.results.find((result) => result.changeId === item.envelope.changeId)?.revision;
    if (!confirmedRevision) throw new Error("Server response contains no confirmed delta revision.");
    settings = { ...settings, v2Cursor: monotonicCursor(settings.v2Cursor ?? "", response.cursor), v2TabRevision: confirmedRevision };
    outbox = { schemaVersion: 2, items: outbox.items.slice(1) };
    await saveSettingsAndTabDeltaOutbox(settings, outbox);
    pushed += 1;
  }
  await saveStatus(status("current", pushed > 0
    ? `${pushed} encrypted tab changes synchronized.`
    : pulled.applied > 0 ? `${pulled.applied} remote tab changes applied.` : "Tabs are current.",
  { ...(settings.v2Cursor ? { lastCursor: settings.v2Cursor } : {}), pendingChanges: 0 }));
  ensureRealtime(api, settings, secrets);
}

async function pullAndApplyV1(
  api: CandySyncApiClient,
  settings: StoredSettings,
  token: string,
  workspaceKey: Uint8Array,
): Promise<{ settings: StoredSettings; applied: number }> {
  let current = settings;
  let applied = 0;
  const pagination = new PullCursorGuard();
  while (true) {
    const requestedCursor = current.cursor ?? "";
    const page = await api.pull(token, requestedCursor);
    pagination.accept(requestedCursor, page.nextCursor, page.hasMore);
    for (const change of page.changes) {
      if (change.entityId !== current.deviceId || revision(change.revision) <= revision(current.tabRevision)) continue;
      if (revision(change.baseRevision) + 1n !== revision(change.revision)) throw new Error("Server returned a non-contiguous tab revision.");
      if (change.deviceId !== current.deviceId) {
        const plaintext = await decryptTabSnapshot(workspaceKey, change);
        applyingRemoteChange = true;
        try { await applyTabSnapshot(plaintext); } finally { applyingRemoteChange = false; }
        localTabsDirty = false;
        applied += 1;
      }
      const pending = current.pendingTabChange;
      if (pending && revision(pending.baseRevision) < revision(change.revision)) {
        const { pendingTabChange: _discarded, ...withoutPending } = current;
        current = { ...withoutPending, tabRevision: change.revision };
      } else current = { ...current, tabRevision: change.revision };
    }
    current = { ...current, cursor: page.nextCursor };
    await saveSettings(current);
    if (!page.hasMore) return { settings: current, applied };
  }
}

async function synchronizeV1(
  api: CandySyncApiClient,
  settings: StoredSettings,
  secrets: VaultSecrets,
  workspaceKey: Uint8Array,
): Promise<void> {
  realtime?.stop(); realtime = null; realtimeIdentity = "";
  const pulled = await pullAndApplyV1(api, settings, secrets.deviceToken, workspaceKey);
  settings = { ...pulled.settings, protocolVersion: 1 };
  let change: EncryptedChange | undefined = settings.pendingTabChange;
  let tabCount = 0;
  if (!change && localTabsDirty) {
    const snapshot = await collectTabSnapshot();
    tabCount = snapshot.tabs.length;
    change = await encryptTabSnapshot(workspaceKey, {
      changeId: crypto.randomUUID(), deviceId: settings.deviceId, entity: "tabs", entityId: settings.deviceId,
      operation: "snapshot", baseRevision: settings.tabRevision || "0", schemaVersion: 1, cryptoVersion: 1, keyVersion: 1,
    }, settings.selection.groups ? snapshot : { ...snapshot, tabs: snapshot.tabs.map((tab) => ({ ...tab, groupId: null })) });
    const latest = await loadSettings();
    if (!latest || latest.deviceId !== settings.deviceId) throw new Error("Sync configuration changed while tabs were captured.");
    await saveSettings({ ...latest, protocolVersion: 1, pendingTabChange: change });
    localTabsDirty = false;
  }
  if (change) {
    const pushed = await api.push(secrets.deviceToken, change);
    const confirmedRevision = pushed.revisions[change.changeId];
    if (!confirmedRevision) throw new Error("Server response contains no confirmed revision.");
    const latest = await loadSettings();
    if (!latest || latest.pendingTabChange?.changeId !== change.changeId) throw new Error("Pending change was replaced during synchronization.");
    const { pendingTabChange: _pending, ...confirmed } = latest;
    await saveSettings({ ...confirmed, protocolVersion: 1, tabRevision: confirmedRevision });
    await saveStatus(status("current", tabCount > 0 ? `${tabCount} tabs synchronized with encryption.` : "Pending tab snapshot confirmed idempotently.", {
      lastCursor: pushed.cursor, pendingChanges: 0,
    }));
  } else {
    await saveSettings(settings);
    await saveStatus(status("current", pulled.applied > 0 ? `${pulled.applied} remote snapshots applied.` : "Tabs are current.", {
      lastCursor: settings.cursor, pendingChanges: 0,
    }));
  }
}

function ensureRealtime(api: CandySyncApiClient, settings: StoredSettings, secrets: VaultSecrets): void {
  const identity = `${settings.endpoint}\n${settings.workspaceId}\n${settings.deviceId}\n${secrets.deviceToken}`;
  if (realtime && realtimeIdentity === identity) return;
  realtime?.stop(); realtimeIdentity = identity;
  realtime = new CandySyncRealtimeClient(api, secrets.deviceToken, handleRealtimeChange);
  realtime.start();
}

async function handleRealtimeChange(frame: RealtimeChangeFrame): Promise<void> {
  let catchUp = false;
  await enqueueOperation(async () => {
    const settings = await loadSettings();
    const secrets = await loadSessionSecrets();
    if (!settings || !secrets || settings.protocolVersion !== 2 || !settings.selection.tabs) return;
    const cursorState = classifyRealtimeCursor(settings.v2Cursor ?? "", frame.cursor);
    if (cursorState === "gap") { catchUp = true; return; }
    if (cursorState === "replay") return;
    const workspaceKey = base64UrlToBytes(secrets.workspaceKey);
    try {
      const result = await applyCommittedDelta(settings, workspaceKey, frame.change, frame.cursor);
      if (result.gap) catchUp = true;
      else await saveSettings(result.settings);
    } finally { workspaceKey.fill(0); }
  });
  if (catchUp) void synchronizeTabs();
}

export async function synchronizeTabsOnce(): Promise<void> {
  let settings = await loadSettings();
  if (!settings) { await saveStatus(status("unconfigured", "Candy Sync is not configured yet.")); return; }
  const secrets = await loadSessionSecrets();
  if (!secrets) { await saveStatus(status("locked", "Passphrase required to unlock sync.")); return; }
  if (!settings.selection.tabs) {
    realtime?.stop(); realtime = null; realtimeIdentity = "";
    await saveStatus(status("ready", "Tab sync is disabled."));
    return;
  }
  if (!await hasSyncPermissions(settings.selection)) {
    await saveStatus(status("permission-required", "Browser permission for the selected data types is missing.")); return;
  }
  await saveStatus(status("syncing", "Synchronizing encrypted tab changes …"));
  const workspaceKey = base64UrlToBytes(secrets.workspaceKey);
  try {
    const api = new CandySyncApiClient(settings.endpoint);
    const discovery = await api.discover();
    settings = (await loadSettings()) ?? settings;
    if (supportsV2(discovery)) await synchronizeV2(api, settings, secrets, workspaceKey);
    else await synchronizeV1(api, settings, secrets, workspaceKey);
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) syncRequested = true;
    const message = error instanceof Error ? error.message : "Unknown sync error";
    const outbox = await loadTabDeltaOutbox();
    await saveStatus(status("offline", message, { pendingChanges: outbox.items.length || (settings.pendingTabChange ? 1 : 0) }));
  } finally { workspaceKey.fill(0); }
}

export async function synchronizeTabs(): Promise<void> {
  syncRequested = true;
  if (syncPromise) return syncPromise;
  syncPromise = enqueueOperation(async () => {
    let passes = 0;
    do {
      syncRequested = false;
      await synchronizeTabsOnce();
      passes += 1;
    } while (syncRequested && passes < MAX_IMMEDIATE_SYNC_PASSES);
    syncRequested = false;
  }).finally(() => { syncPromise = null; });
  return syncPromise;
}

async function handleLocalMutations(mutations: readonly TabMutationDraft[]): Promise<void> {
  if (applyingRemoteChange || mutations.length === 0) return;
  const settings = await loadSettings();
  const secrets = await loadSessionSecrets();
  if (!settings || !secrets || !settings.selection.tabs) return;
  if (settings.protocolVersion !== 2) {
    localTabsDirty = true;
    void synchronizeTabs();
    return;
  }
  const workspaceKey = base64UrlToBytes(secrets.workspaceKey);
  try { for (const mutation of mutations) await queueTabMutation(settings, workspaceKey, mutation); }
  finally { workspaceKey.fill(0); }
  void synchronizeTabs();
}

async function updateSelection(nextSelection: SyncSelection): Promise<void> {
  await enqueueOperation(async () => {
    const latest = await loadSettings();
    if (!latest) throw new Error("Candy Sync is not configured yet.");
    let next: StoredSettings = { ...latest, selection: nextSelection };
    if (latest.selection.tabs && !nextSelection.tabs && latest.protocolVersion === 2) {
      const snapshot = await collectTabSnapshot();
      next = { ...next, v2DisabledTabIds: snapshot.tabs.map((tab) => tab.candyId), v2ReconciliationPending: false };
    } else if (!latest.selection.tabs && nextSelection.tabs && latest.protocolVersion === 2) {
      next = { ...next, v2ReconciliationPending: true };
    }
    await saveSettings(next);
    localTabsDirty = true;
    await synchronizeTabsOnce();
  });
}

async function ensureAlarm(): Promise<void> {
  const api = extensionApi();
  if (!await api.alarms.get(PERIODIC_ALARM)) await api.alarms.create(PERIODIC_ALARM, { delayInMinutes: 1, periodInMinutes: 1 });
}

export function startBackground(): void {
  const api = extensionApi();
  api.runtime.onInstalled.addListener(() => { void ensureAlarm(); });
  api.runtime.onStartup.addListener(() => { void ensureAlarm().then(synchronizeTabs); });
  api.alarms.onAlarm.addListener((alarm) => { if (alarm.name === PERIODIC_ALARM) void synchronizeTabs(); });
  api.tabs.onCreated.addListener((tab) => {
    if (applyingRemoteChange) return;
    void enqueueOperation(async () => {
      const mutation = await mutationForCreatedTab(tab);
      await handleLocalMutations(mutation ? [mutation] : []);
    }).catch(() => undefined);
  });
  api.tabs.onRemoved.addListener((tabId) => {
    if (applyingRemoteChange) return;
    void enqueueOperation(async () => {
      const mutation = await mutationForRemovedTab(tabId);
      await handleLocalMutations(mutation ? [mutation] : []);
    }).catch(() => undefined);
  });
  api.tabs.onMoved.addListener((tabId, info) => {
    if (applyingRemoteChange) return;
    void enqueueOperation(async () => {
      const mutation = await mutationForMovedTab(tabId, info.toIndex);
      await handleLocalMutations(mutation ? [mutation] : []);
    }).catch(() => undefined);
  });
  api.tabs.onUpdated.addListener((tabId, info, tab) => {
    if (applyingRemoteChange) return;
    void enqueueOperation(async () => handleLocalMutations(await mutationsForUpdatedTab(tabId, info, tab))).catch(() => undefined);
  });
  const permissionChanged = () => { localTabsDirty = true; void synchronizeTabs(); };
  api.permissions.onAdded.addListener(permissionChanged);
  api.permissions.onRemoved.addListener(permissionChanged);
  api.runtime.onMessage.addListener((message: unknown, _sender, sendResponse) => {
    const typed = message && typeof message === "object" ? message as { type?: unknown; selection?: unknown } : null;
    if (typed?.type === "SYNC_NOW") {
      void synchronizeTabs().then(() => sendResponse({ ok: true }), (error: unknown) => sendResponse({ ok: false, error: error instanceof Error ? error.message : "Sync failed" }));
      return true;
    }
    if (typed?.type === "UPDATE_SELECTION") {
      const value = typed.selection as Partial<SyncSelection> | undefined;
      if (!value || typeof value.tabs !== "boolean" || typeof value.bookmarks !== "boolean" || typeof value.groups !== "boolean") {
        sendResponse({ ok: false, error: "Invalid sync selection." }); return false;
      }
      void updateSelection({ tabs: value.tabs, bookmarks: value.bookmarks, groups: value.groups }).then(
        () => sendResponse({ ok: true }),
        (error: unknown) => sendResponse({ ok: false, error: error instanceof Error ? error.message : "Selection could not be saved." }),
      );
      return true;
    }
    return false;
  });
  void ensureAlarm();
  void synchronizeTabs();
}
