import type { StoredSettings, SyncStatus, VaultSecrets } from "../core/models.js";
import { extensionApi } from "../platform/webextension.js";

const SETTINGS_KEY = "candySyncSettingsV1";
const STATUS_KEY = "candySyncStatusV1";
const SESSION_KEY = "candySyncSessionSecretsV1";
const TAB_IDENTITIES_KEY = "candySyncTabIdentitiesV1";

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
