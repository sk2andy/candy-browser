import type { SyncSelection } from "../core/models.js";
import { dataCollectionForSelection, permissionsForSelection } from "../core/permission-rules.js";
import { extensionApi } from "../platform/webextension.js";

type FirefoxPermissionRequest = chrome.permissions.Permissions & { data_collection?: string[] };

async function requestPermissions(request: FirefoxPermissionRequest): Promise<boolean> {
  const invoke = extensionApi().permissions.request as unknown as (
    permissions: FirefoxPermissionRequest,
  ) => Promise<boolean>;
  return invoke(request);
}

export async function requestSyncPermissions(selection: SyncSelection): Promise<boolean> {
  const api = extensionApi();
  const request: FirefoxPermissionRequest = {
    permissions: permissionsForSelection(selection) as chrome.runtime.ManifestPermission[],
  };
  if ((globalThis as typeof globalThis & { browser?: unknown }).browser) {
    request.data_collection = dataCollectionForSelection(selection);
  }
  return requestPermissions(request);
}

export async function hasSyncPermissions(selection: SyncSelection): Promise<boolean> {
  return extensionApi().permissions.contains({
    permissions: permissionsForSelection(selection) as chrome.runtime.ManifestPermission[],
  });
}

export async function requestEndpointPermission(originPattern: string): Promise<boolean> {
  return requestPermissions({ origins: [originPattern] });
}

export async function requestSetupPermissions(selection: SyncSelection, originPattern: string): Promise<boolean> {
  const request: FirefoxPermissionRequest = {
    origins: [originPattern],
    permissions: permissionsForSelection(selection) as chrome.runtime.ManifestPermission[],
  };
  if ((globalThis as typeof globalThis & { browser?: unknown }).browser) {
    request.data_collection = dataCollectionForSelection(selection);
  }
  return requestPermissions(request);
}

export async function removeEndpointPermission(originPattern: string): Promise<boolean> {
  return extensionApi().permissions.remove({ origins: [originPattern] });
}
