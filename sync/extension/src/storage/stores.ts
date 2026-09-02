import type { StoredSettings, SyncStatus, TabDeltaOutbox, TabMutationState, VaultSecrets } from "../core/models.js";
import { emptyTabMutationState } from "../core/tab-mutation-rules.js";
import { extensionApi } from "../platform/webextension.js";

const SETTINGS_KEY = "candySyncSettingsV1";
const STATUS_KEY = "candySyncStatusV1";
const SESSION_KEY = "candySyncSessionSecretsV1";
const TAB_IDENTITIES_KEY = "candySyncTabIdentitiesV1";
const TAB_DELTA_OUTBOX_KEY = "candySyncTabDeltaOutboxV2";
const TAB_MUTATION_STATE_KEY = "candySyncTabMutationStateV2";
const MAX_TAB_DELTA_OUTBOX_ITEMS = 2_001;

export async function loadSettings(): Promise<StoredSettings | null> {
  const result = await extensionApi().storage.local.get(SETTINGS_KEY);
  return (result[SETTINGS_KEY] as StoredSettings | undefined) ?? null;
}

export async function saveSettings(settings: StoredSettings): Promise<void> {
  await extensionApi().storage.local.set({ [SETTINGS_KEY]: settings });
}

export async function loadStatus(): Promise<SyncStatus> {
  const result = await extensionApi().storage.local.get(STATUS_KEY);
  return (result[STATUS_KEY] as SyncStatus | undefined) ?? {
    code: "unconfigured",
    message: "Candy Sync is not configured yet.",
    updatedAt: new Date(0).toISOString(),
  };
}

export async function saveStatus(status: SyncStatus): Promise<void> {
  await extensionApi().storage.local.set({ [STATUS_KEY]: status });
}

export async function loadSessionSecrets(): Promise<VaultSecrets | null> {
  const result = await extensionApi().storage.session.get(SESSION_KEY);
  return (result[SESSION_KEY] as VaultSecrets | undefined) ?? null;
}

export async function saveSessionSecrets(secrets: VaultSecrets): Promise<void> {
  const api = extensionApi();
  if (api.storage.session.setAccessLevel) {
    await api.storage.session.setAccessLevel({ accessLevel: "TRUSTED_CONTEXTS" });
  }
  await api.storage.session.set({ [SESSION_KEY]: secrets });
}

export async function clearSessionSecrets(): Promise<void> {
  await extensionApi().storage.session.remove(SESSION_KEY);
}

export async function loadTabIdentities(): Promise<Record<string, string>> {
  const result = await extensionApi().storage.local.get(TAB_IDENTITIES_KEY);
  const value = result[TAB_IDENTITIES_KEY];
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const identities: Record<string, string> = {};
  for (const [tabId, candyId] of Object.entries(value)) {
    if (/^[1-9][0-9]{0,15}$/u.test(tabId) && typeof candyId === "string" && /^[A-Za-z0-9._:-]{1,128}$/u.test(candyId)) {
      identities[tabId] = candyId;
    }
  }
  return identities;
}

export async function saveTabIdentities(identities: Record<string, string>): Promise<void> {
  await extensionApi().storage.local.set({ [TAB_IDENTITIES_KEY]: identities });
}

export async function loadTabDeltaOutbox(): Promise<TabDeltaOutbox> {
  const result = await extensionApi().storage.local.get(TAB_DELTA_OUTBOX_KEY);
  const value = result[TAB_DELTA_OUTBOX_KEY] as Partial<TabDeltaOutbox> | undefined;
  if (!value || value.schemaVersion !== 2 || !Array.isArray(value.items)) return { schemaVersion: 2, items: [] };
  return { schemaVersion: 2, items: value.items.slice(0, MAX_TAB_DELTA_OUTBOX_ITEMS) } as TabDeltaOutbox;
}

export async function saveTabDeltaOutbox(outbox: TabDeltaOutbox): Promise<void> {
  if (outbox.schemaVersion !== 2 || outbox.items.length > MAX_TAB_DELTA_OUTBOX_ITEMS) throw new Error("Invalid tab delta outbox");
  await extensionApi().storage.local.set({ [TAB_DELTA_OUTBOX_KEY]: outbox });
}

export async function saveSettingsAndTabDeltaOutbox(
  settings: StoredSettings,
  outbox: TabDeltaOutbox,
): Promise<void> {
  if (outbox.schemaVersion !== 2 || outbox.items.length > MAX_TAB_DELTA_OUTBOX_ITEMS) throw new Error("Invalid tab delta outbox");
  await extensionApi().storage.local.set({
    [SETTINGS_KEY]: settings,
    [TAB_DELTA_OUTBOX_KEY]: outbox,
  });
}

export async function loadTabMutationState(): Promise<TabMutationState> {
  const result = await extensionApi().storage.local.get(TAB_MUTATION_STATE_KEY);
  const value = result[TAB_MUTATION_STATE_KEY] as Partial<TabMutationState> | undefined;
  if (!value || value.schemaVersion !== 2 || !value.tabs || !value.tombstones || !Array.isArray(value.appliedMutationIds)) {
    return emptyTabMutationState();
  }
  return value as TabMutationState;
}

export async function saveTabMutationState(state: TabMutationState): Promise<void> {
  // URLs and titles already live in browser tab storage. Persist only replay/tombstone policy.
  await extensionApi().storage.local.set({ [TAB_MUTATION_STATE_KEY]: { ...state, tabs: {} } });
}
