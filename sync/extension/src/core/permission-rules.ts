import type { SyncSelection, SyncType } from "./models.js";

export interface PermissionPlan {
  permissions: string[];
  origins: string[];
  dataCollection: string[];
}

export function permissionsForSelection(selection: SyncSelection): string[] {
  const permissions = new Set<string>();
  if (selection.tabs || selection.groups) permissions.add("tabs");
  if (selection.bookmarks) permissions.add("bookmarks");
  if (selection.groups) permissions.add("tabGroups");
  return [...permissions].sort();
}

export function dataCollectionForSelection(selection: SyncSelection): string[] {
  const values = new Set<string>();
  if (selection.tabs || selection.groups) values.add("browsingActivity");
  if (selection.bookmarks) values.add("bookmarksInfo");
  return [...values].sort();
}

export function selectionWith(selection: SyncSelection, type: SyncType, enabled: boolean): SyncSelection {
  return { ...selection, [type]: enabled };
}
