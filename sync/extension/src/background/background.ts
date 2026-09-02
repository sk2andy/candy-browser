import { hasSyncPermissions } from "../browser-adapters/permissions.js";
import { applyTabSnapshot, collectTabSnapshot } from "../browser-adapters/tabs.js";
import { type EncryptedChange, type StoredSettings, type SyncSelection, type SyncStatus } from "../core/models.js";
import { decryptTabSnapshot, encryptTabSnapshot } from "../crypto/crypto.js";
import { base64UrlToBytes } from "../crypto/encoding.js";
import { extensionApi } from "../platform/webextension.js";
import { ApiError, CandySyncApiClient } from "../protocol/api-client.js";
import { loadSessionSecrets, loadSettings, saveSettings, saveStatus } from "../storage/stores.js";

const PERIODIC_ALARM = "candy-sync-periodic";
let syncPromise: Promise<void> | null = null;
let syncRequested = false;
let localTabsDirty = true;
let applyingRemoteSnapshot = false;
let operationTail: Promise<void> = Promise.resolve();

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

async function pullAndApplyTargetChanges(
  api: CandySyncApiClient,
  settings: StoredSettings,
  deviceToken: string,
  workspaceKey: Uint8Array,
): Promise<{ settings: StoredSettings; applied: number }> {
  let current = settings;
  let applied = 0;
  do {
    const page = await api.pull(deviceToken, current.cursor ?? "");
    for (const change of page.changes) {
      if (change.entityId !== current.deviceId || revision(change.revision) <= revision(current.tabRevision)) continue;
      if (revision(change.baseRevision) + 1n !== revision(change.revision)) throw new Error("Server returned a non-contiguous tab revision.");
      if (change.deviceId !== current.deviceId) {
        const plaintext = await decryptTabSnapshot(workspaceKey, change);
        applyingRemoteSnapshot = true;
        try {
          await applyTabSnapshot(plaintext);
        } finally {
          applyingRemoteSnapshot = false;
        }
        localTabsDirty = false;
        applied += 1;
      }
      const pendingTabChange = current.pendingTabChange;
      if (pendingTabChange && revision(pendingTabChange.baseRevision) < revision(change.revision)) {
        const { pendingTabChange: _discarded, ...withoutPending } = current;
        current = { ...withoutPending, tabRevision: change.revision };
      } else {
        current = { ...current, tabRevision: change.revision };
      }
    }
    current = { ...current, cursor: page.nextCursor };
    await saveSettings(current);
    if (!page.hasMore) break;
  } while (true);
  return { settings: current, applied };
}

export async function synchronizeTabsOnce(): Promise<void> {
  let settings = await loadSettings();
  if (!settings) {
    await saveStatus(status("unconfigured", "Candy Sync is not configured yet."));
    return;
  }
  const secrets = await loadSessionSecrets();
  if (!secrets) {
    await saveStatus(status("locked", "Passphrase erforderlich, um Sync zu entsperren."));
    return;
  }
  if (!settings.selection.tabs) {
    await saveStatus(status("ready", "Tabs-Sync ist deaktiviert."));
    return;
  }
  if (!await hasSyncPermissions(settings.selection)) {
    await saveStatus(status("permission-required", "Browser permission for the selected data types is missing."));
    return;
  }
  await saveStatus(status("syncing", "Synchronizing tabs with encryption …"));
  const workspaceKey = base64UrlToBytes(secrets.workspaceKey);
  try {
    const api = new CandySyncApiClient(settings.endpoint);
    const pulled = await pullAndApplyTargetChanges(api, settings, secrets.deviceToken, workspaceKey);
    settings = pulled.settings;
    let change: EncryptedChange | undefined = settings.pendingTabChange;
    let tabCount = 0;
    if (!change && localTabsDirty) {
      const snapshot = await collectTabSnapshot();
      tabCount = snapshot.tabs.length;
      change = await encryptTabSnapshot(workspaceKey, {
        changeId: crypto.randomUUID(),
        deviceId: settings.deviceId,
        entity: "tabs",
        entityId: settings.deviceId,
        operation: "snapshot",
        baseRevision: settings.tabRevision || "0",
        schemaVersion: 1,
        cryptoVersion: 1,
        keyVersion: 1,
      }, settings.selection.groups
        ? snapshot
        : { ...snapshot, tabs: snapshot.tabs.map((tab) => ({ ...tab, groupId: null })) });
      const latestSettings = await loadSettings();
      if (!latestSettings || latestSettings.deviceId !== settings.deviceId) throw new Error("Sync configuration changed while tabs were captured.");
      await saveSettings({ ...latestSettings, pendingTabChange: change });
      localTabsDirty = false;
    }
    if (change) {
      const pushed = await api.push(secrets.deviceToken, change);
      const confirmedRevision = pushed.revisions[change.changeId];
      if (!confirmedRevision) throw new Error("Server response contains no confirmed revision.");
      const latestSettings = await loadSettings();
      if (!latestSettings || latestSettings.pendingTabChange?.changeId !== change.changeId) {
        throw new Error("Pending change was replaced during synchronization.");
      }
      const { pendingTabChange: _pendingTabChange, ...confirmedSettings } = latestSettings;
      await saveSettings({ ...confirmedSettings, tabRevision: confirmedRevision });
      const message = tabCount > 0
        ? `${tabCount} tabs synchronized with encryption.`
        : "Pending tab snapshot confirmed idempotently.";
      await saveStatus(status("current", message, { lastCursor: pushed.cursor, pendingChanges: 0 }));
    } else {
      await saveStatus(status("current", pulled.applied > 0 ? `${pulled.applied} Remote-Snapshot angewendet.` : "Tabs sind aktuell.", {
        lastCursor: settings.cursor,
        pendingChanges: 0,
      }));
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) syncRequested = true;
    const message = error instanceof Error ? error.message : "Unbekannter Sync-Fehler";
    await saveStatus(status("offline", message, { pendingChanges: 1 }));
  } finally {
    workspaceKey.fill(0);
  }
}

export async function synchronizeTabs(): Promise<void> {
  syncRequested = true;
  if (syncPromise) return syncPromise;
  syncPromise = enqueueOperation(async () => {
    do {
      syncRequested = false;
      await synchronizeTabsOnce();
    } while (syncRequested);
  }).finally(() => { syncPromise = null; });
  return syncPromise;
}

async function updateSelection(nextSelection: SyncSelection): Promise<void> {
  await enqueueOperation(async () => {
    const latestSettings = await loadSettings();
    if (!latestSettings) throw new Error("Candy Sync is not configured yet.");
    await saveSettings({ ...latestSettings, selection: nextSelection });
    localTabsDirty = true;
    await synchronizeTabsOnce();
  });
}

async function ensureAlarm(): Promise<void> {
  const api = extensionApi();
  if (!await api.alarms.get(PERIODIC_ALARM)) {
    await api.alarms.create(PERIODIC_ALARM, { delayInMinutes: 1, periodInMinutes: 5 });
  }
}

export function startBackground(): void {
  const api = extensionApi();
  api.runtime.onInstalled.addListener(() => { void ensureAlarm(); });
  api.runtime.onStartup.addListener(() => { void ensureAlarm().then(synchronizeTabs); });
  api.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === PERIODIC_ALARM) void synchronizeTabs();
  });
  const schedule = () => {
    if (applyingRemoteSnapshot) return;
    localTabsDirty = true;
    void synchronizeTabs();
  };
  api.tabs.onCreated.addListener(schedule);
  api.tabs.onRemoved.addListener(schedule);
  api.tabs.onMoved.addListener(schedule);
  api.tabs.onUpdated.addListener(schedule);
  api.permissions.onAdded.addListener(schedule);
  api.permissions.onRemoved.addListener(schedule);
  api.runtime.onMessage.addListener((message: unknown, _sender, sendResponse) => {
    const typedMessage = message && typeof message === "object"
      ? message as { type?: unknown; selection?: unknown }
      : null;
    if (typedMessage?.type === "SYNC_NOW") {
      void synchronizeTabs().then(
        () => sendResponse({ ok: true }),
        (error: unknown) => sendResponse({ ok: false, error: error instanceof Error ? error.message : "Sync failed" }),
      );
      return true;
    }
    if (typedMessage?.type === "UPDATE_SELECTION") {
      const value = typedMessage.selection as Partial<SyncSelection> | undefined;
      if (!value || typeof value.tabs !== "boolean" || typeof value.bookmarks !== "boolean" || typeof value.groups !== "boolean") {
        sendResponse({ ok: false, error: "Invalid sync selection." });
        return false;
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
}
