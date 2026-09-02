import { removeEndpointPermission, requestSetupPermissions, requestSyncPermissions } from "../browser-adapters/permissions.js";
import { SYNC_TYPES, type StoredSettings, type SyncSelection, type SyncStatus, type VaultSecrets } from "../core/models.js";
import { defaultDeviceIconId, DEVICE_ICON_CATALOG, DEVICE_ICON_IDS } from "../core/device-icon-catalog.js";
import { endpointPermissionOrigin, normalizeEndpoint } from "../core/endpoint-rules.js";
import {
  createRecoveryEnvelope,
  createVault,
  deriveDeviceIconDescriptor,
  encryptDeviceIcon,
  encryptDeviceName,
  generateDeviceIdentity,
  randomBytes,
  unlockRecoveryEnvelope,
  unlockVault,
} from "../crypto/crypto.js";
import { bytesToBase64Url, utf8 } from "../crypto/encoding.js";
import { extensionApi } from "../platform/webextension.js";
import { CandySyncApiClient } from "../protocol/api-client.js";
import {
  clearSessionSecrets,
  loadSessionSecrets,
  loadSettings,
  loadStatus,
  saveSessionSecrets,
  saveSettings,
  saveStatus,
} from "../storage/stores.js";

const element = <T extends HTMLElement>(id: string): T => {
  const value = document.getElementById(id);
  if (!value) throw new Error(`Missing options element: ${id}`);
  return value as T;
};

const setupForm = element<HTMLFormElement>("setup-form");
const endpointInput = element<HTMLInputElement>("endpoint");
const usernameInput = element<HTMLInputElement>("username");
const passwordInput = element<HTMLInputElement>("password");
const deviceNameInput = element<HTMLInputElement>("device-name");
const deviceIconInput = element<HTMLSelectElement>("device-icon");
const passphraseInput = element<HTMLInputElement>("passphrase");
const confirmationInput = element<HTMLInputElement>("passphrase-confirmation");
const confirmationField = element<HTMLElement>("confirmation-field");
const acknowledgeRow = element<HTMLElement>("acknowledge-row");
const acknowledgeInput = element<HTMLInputElement>("acknowledge");
const tabsInput = element<HTMLInputElement>("sync-tabs");
const bookmarksInput = element<HTMLInputElement>("sync-bookmarks");
const groupsInput = element<HTMLInputElement>("sync-groups");
const connectButton = element<HTMLButtonElement>("connect");
const unlockButton = element<HTMLButtonElement>("unlock");
const saveSelectionButton = element<HTMLButtonElement>("save-selection");
const syncNowButton = element<HTMLButtonElement>("sync-now");
const lockButton = element<HTMLButtonElement>("lock");
const feedback = element<HTMLElement>("feedback");
const statusCard = element<HTMLElement>("status-card");
const statusCode = element<HTMLElement>("status-code");
const statusMessage = element<HTMLElement>("status-message");

let settings: StoredSettings | null = null;

function selection(): SyncSelection {
  return { tabs: tabsInput.checked, bookmarks: bookmarksInput.checked, groups: groupsInput.checked };
}

function setBusy(busy: boolean): void {
  for (const button of [connectButton, unlockButton, saveSelectionButton, syncNowButton, lockButton]) button.disabled = busy;
}

function showFeedback(message: string, kind: "error" | "success" | "neutral" = "neutral"): void {
  feedback.textContent = message;
  feedback.className = kind === "neutral" ? "feedback" : `feedback ${kind}`;
}

function statusLabel(code: SyncStatus["code"]): string {
  return ({
    unconfigured: "Nicht eingerichtet",
    locked: "Gesperrt",
    ready: "Bereit",
    syncing: "Synchronisiert …",
    current: "Aktuell",
    offline: "Offline",
    "auth-error": "Anmeldung fehlgeschlagen",
    "crypto-error": "Decryption failed",
    "permission-required": "Permission required",
    incompatible: "Server inkompatibel",
  })[code];
}

async function renderStatus(): Promise<void> {
  const current = await loadStatus();
  statusCard.dataset.state = current.code;
  statusCode.textContent = statusLabel(current.code);
  statusMessage.textContent = `${current.message} · ${new Date(current.updatedAt).toLocaleString()}`;
}

function renderConfigured(config: StoredSettings): void {
  endpointInput.value = config.endpoint;
  usernameInput.value = config.username;
  deviceNameInput.value = config.deviceName;
  deviceIconInput.value = DEVICE_ICON_IDS.has(config.deviceIconId)
    ? config.deviceIconId
    : defaultDeviceIconId(navigator.userAgent, navigator.maxTouchPoints);
  tabsInput.checked = config.selection.tabs;
  bookmarksInput.checked = config.selection.bookmarks;
  groupsInput.checked = config.selection.groups;
  for (const input of [endpointInput, usernameInput, deviceNameInput, deviceIconInput, passwordInput]) input.disabled = true;
  passwordInput.required = false;
  confirmationInput.required = false;
  acknowledgeInput.required = false;
  confirmationField.hidden = true;
  acknowledgeRow.hidden = true;
  connectButton.hidden = true;
  unlockButton.hidden = false;
  saveSelectionButton.hidden = false;
  passphraseInput.autocomplete = "off";
}

async function initialize(): Promise<void> {
  for (const icon of DEVICE_ICON_CATALOG) {
    const option = document.createElement("option");
    option.value = icon.id;
    option.textContent = `${icon.emoji} ${icon.label}`;
    deviceIconInput.append(option);
  }
  deviceIconInput.value = defaultDeviceIconId(navigator.userAgent, navigator.maxTouchPoints);
  settings = await loadSettings();
  if (settings) {
    renderConfigured(settings);
    if (await loadSessionSecrets()) unlockButton.textContent = "Session ist entsperrt";
  } else {
    deviceNameInput.value = `${navigator.userAgent.includes("Firefox") ? "Firefox" : "Chromium"} on this device`;
  }
  await renderStatus();
}

setupForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (settings) return;
  let endpoint: string;
  try {
    endpoint = normalizeEndpoint(endpointInput.value);
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : "Endpoint is invalid.", "error");
    return;
  }
  if (passphraseInput.value !== confirmationInput.value) {
    showFeedback("Passphrase confirmation does not match.", "error");
    return;
  }
  if (passphraseInput.value.length < 16 || !acknowledgeInput.checked) {
    showFeedback("Use at least 16 characters and acknowledge the loss warning.", "error");
    return;
  }
  if (passphraseInput.value === passwordInput.value) {
    showFeedback("Use different values for the server password and E2EE passphrase.", "error");
    return;
  }

  // Permission request must remain the first asynchronous call in this user gesture.
  const granted = await requestSetupPermissions(selection(), endpointPermissionOrigin(endpoint));
  if (!granted) {
    showFeedback("The required browser or endpoint permission was not granted.", "error");
    return;
  }

  setBusy(true);
  showFeedback("Checking the server and generating local keys …");
  let password = passwordInput.value;
  const passphrase = utf8(passphraseInput.value);
  let devicePrivateKey: Uint8Array | null = null;
  let workspaceKey: Uint8Array | null = null;
  let committed = false;
  passphraseInput.value = "";
  confirmationInput.value = "";
  passwordInput.value = "";
  try {
    const api = new CandySyncApiClient(endpoint);
    const discovery = await api.discover();
    if (!discovery.features.includes("encrypted-device-icons")) throw new Error("Server does not support encrypted device icons.");
    if (!discovery.features.includes("editable-tab-profiles")) throw new Error("Server does not support editable synced tab profiles.");
    const bootstrap = await api.bootstrap(usernameInput.value.trim(), password);
    const deviceIdentity = await generateDeviceIdentity();
    devicePrivateKey = deviceIdentity.privateKeyPkcs8;
    if (bootstrap.initialized) {
      if (!bootstrap.recoveryEnvelope) throw new Error("Initialized workspace has no recovery envelope.");
      workspaceKey = await unlockRecoveryEnvelope(passphrase, bootstrap.recoveryEnvelope, bootstrap.workspaceId, bootstrap.kdf);
    } else {
      workspaceKey = randomBytes(32);
    }
    const encryptedName = await encryptDeviceName(
      workspaceKey,
      bootstrap.workspaceId,
      deviceIdentity.fingerprint,
      deviceNameInput.value.trim(),
    );
    const iconDescriptor = deriveDeviceIconDescriptor(
      navigator.userAgent,
      deviceIdentity.fingerprint,
      navigator.maxTouchPoints,
      deviceIconInput.value,
    );
    const encryptedIcon = await encryptDeviceIcon(workspaceKey, bootstrap.workspaceId, deviceIdentity.fingerprint, iconDescriptor);
    const recoveryEnvelope = bootstrap.initialized
      ? undefined
      : await createRecoveryEnvelope(passphrase, workspaceKey, bootstrap.workspaceId, bootstrap.kdf);
    const enrolled = await api.enroll(usernameInput.value.trim(), password, {
      deviceName: encryptedName,
      deviceIcon: encryptedIcon,
      deviceKeyFingerprint: deviceIdentity.fingerprint,
      publicKey: bytesToBase64Url(deviceIdentity.publicKeySpki),
      capabilities: SYNC_TYPES.filter((type) => selection()[type]),
      ...(recoveryEnvelope ? { recoveryEnvelope } : {}),
    });
    deviceIdentity.publicKeySpki.fill(0);
    if (enrolled.workspaceId !== bootstrap.workspaceId) throw new Error("Server changed the workspace during setup.");
    const secrets: VaultSecrets = {
      workspaceKey: bytesToBase64Url(workspaceKey),
      devicePrivateKeyPkcs8: bytesToBase64Url(devicePrivateKey),
      deviceToken: enrolled.token,
      workspaceId: enrolled.workspaceId,
      deviceId: enrolled.deviceId,
    };
    const vault = await createVault(passphrase, secrets);
    const nextSettings: StoredSettings = {
      schemaVersion: 1,
      endpoint,
      username: usernameInput.value.trim(),
      deviceName: deviceNameInput.value.trim(),
      deviceIconId: deviceIconInput.value,
      workspaceId: enrolled.workspaceId,
      deviceId: enrolled.deviceId,
      cursor: enrolled.cursor,
      tabRevision: "0",
      selection: selection(),
      vault,
    };
    await saveSettings(nextSettings);
    committed = true;
    await saveSessionSecrets(secrets);
    await saveStatus({ code: "ready", message: "Device enrolled; the first encrypted sync is starting.", updatedAt: new Date().toISOString() });
    settings = nextSettings;
    renderConfigured(nextSettings);
    showFeedback("Device enrolled securely. Password and passphrase were not stored.", "success");
    await extensionApi().runtime.sendMessage({ type: "SYNC_NOW" });
  } catch (error) {
    if (committed) {
      settings = await loadSettings();
      if (settings) renderConfigured(settings);
      await saveStatus({ code: "offline", message: "Setup is saved; unlock again to continue.", updatedAt: new Date().toISOString() });
    } else {
      await removeEndpointPermission(endpointPermissionOrigin(endpoint));
    }
    showFeedback(error instanceof Error ? error.message : "Einrichtung fehlgeschlagen.", "error");
  } finally {
    password = "";
    devicePrivateKey?.fill(0);
    workspaceKey?.fill(0);
    passphrase.fill(0);
    setBusy(false);
    await renderStatus();
  }
});

unlockButton.addEventListener("click", async () => {
  if (!settings) return;
  if (!passphraseInput.value) {
    showFeedback("Passphrase zum Entsperren eingeben.", "error");
    return;
  }
  setBusy(true);
  const passphrase = utf8(passphraseInput.value);
  passphraseInput.value = "";
  try {
    const secrets = await unlockVault(passphrase, settings.vault);
    await saveSessionSecrets(secrets);
    await saveStatus({ code: "ready", message: "Keys unlocked for this browser session.", updatedAt: new Date().toISOString() });
    showFeedback("Session unlocked. The passphrase was discarded.", "success");
    await extensionApi().runtime.sendMessage({ type: "SYNC_NOW" });
  } catch {
    await saveStatus({ code: "crypto-error", message: "Passphrase is incorrect or the vault is damaged.", updatedAt: new Date().toISOString() });
    showFeedback("Passphrase is incorrect or the local vault is damaged.", "error");
  } finally {
    passphrase.fill(0);
    setBusy(false);
    await renderStatus();
  }
});

saveSelectionButton.addEventListener("click", async () => {
  const nextSelection = selection();
  const granted = await requestSyncPermissions(nextSelection);
  if (!granted) {
    showFeedback("Selection was not changed because a permission is missing.", "error");
    const current = await loadSettings();
    if (current) renderConfigured(current);
    return;
  }
  const result = await extensionApi().runtime.sendMessage({ type: "UPDATE_SELECTION", selection: nextSelection }) as { ok?: boolean; error?: string };
  if (!result?.ok) {
    showFeedback(result?.error ?? "Selection could not be saved.", "error");
    return;
  }
  settings = await loadSettings();
  showFeedback("Selection saved.", "success");
  await renderStatus();
});

syncNowButton.addEventListener("click", async () => {
  setBusy(true);
  try {
    await extensionApi().runtime.sendMessage({ type: "SYNC_NOW" });
  } finally {
    setBusy(false);
    await renderStatus();
  }
});

lockButton.addEventListener("click", async () => {
  await clearSessionSecrets();
  await saveStatus({ code: "locked", message: "Local keys were removed from session storage.", updatedAt: new Date().toISOString() });
  showFeedback("Session gesperrt.", "success");
  await renderStatus();
});

extensionApi().storage.onChanged.addListener((_changes, area) => {
  if (area === "local") void renderStatus();
});

void initialize();
