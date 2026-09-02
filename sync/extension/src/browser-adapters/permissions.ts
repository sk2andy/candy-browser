import type { SyncSelection } from "../core/models.js";
import { dataCollectionForSelection, permissionsForSelection } from "../core/permission-rules.js";
import { IS_FIREFOX_BUILD } from "../platform/browser-target.js";
import { extensionApi } from "../platform/webextension.js";

type FirefoxPermissionRequest = chrome.permissions.Permissions & { data_collection?: string[] };

export function permissionRequest(
  selection: SyncSelection,
  origins: string[],
  includeFirefoxDataCollection: boolean,
): FirefoxPermissionRequest {
  const request: FirefoxPermissionRequest = {
    origins,
    permissions: permissionsForSelection(selection) as chrome.runtime.ManifestPermission[],
  };
  if (includeFirefoxDataCollection) {
    request.data_collection = dataCollectionForSelection(selection);
  }
  return request;
}

async function requestPermissions(request: FirefoxPermissionRequest): Promise<boolean> {
  const invoke = extensionApi().permissions.request as unknown as (
    permissions: FirefoxPermissionRequest,
  ) => Promise<boolean>;
  return invoke(request);
}

export async function requestSyncPermissions(selection: SyncSelection): Promise<boolean> {
  return requestPermissions(permissionRequest(selection, [], IS_FIREFOX_BUILD));
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
  return requestPermissions(permissionRequest(selection, [originPattern], IS_FIREFOX_BUILD));
}

export async function removeEndpointPermission(originPattern: string): Promise<boolean> {
  return extensionApi().permissions.remove({ origins: [originPattern] });
}
