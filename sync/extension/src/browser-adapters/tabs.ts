import type { DeviceTabSnapshot } from "../core/models.js";
import { isSyncableTabUrl, parseDeviceTabSnapshot, snapshotFromTabs } from "../core/snapshot-rules.js";
import { extensionApi } from "../platform/webextension.js";
import { loadTabIdentities, saveTabIdentities } from "../storage/stores.js";

export async function collectTabSnapshot(now = new Date()): Promise<ReturnType<typeof snapshotFromTabs>> {
  const tabs = await extensionApi().tabs.query({});
  const storedIdentities = await loadTabIdentities();
  const liveIdentities: Record<string, string> = {};
  for (const tab of tabs) {
    if (tab.id == null || tab.incognito || !isSyncableTabUrl(tab.url ?? tab.pendingUrl)) continue;
    liveIdentities[String(tab.id)] = storedIdentities[String(tab.id)] ?? crypto.randomUUID();
  }
  await saveTabIdentities(liveIdentities);
  return snapshotFromTabs(tabs.map((tab) => ({
    id: tab.id,
    windowId: tab.windowId,
    index: tab.index,
    groupId: tab.groupId,
    active: tab.active,
    pinned: tab.pinned,
    incognito: tab.incognito,
    title: tab.title,
    url: tab.url,
    pendingUrl: tab.pendingUrl,
  })), now.toISOString(), liveIdentities);
}

export async function applyTabSnapshot(rawSnapshot: unknown): Promise<DeviceTabSnapshot> {
  const snapshot = parseDeviceTabSnapshot(rawSnapshot);
  const api = extensionApi();
  const allTabs = await api.tabs.query({});
  const identities = await loadTabIdentities();
  const eligible = allTabs.filter((tab): tab is chrome.tabs.Tab & { id: number } =>
    tab.id != null && !tab.incognito && isSyncableTabUrl(tab.url ?? tab.pendingUrl));
  const byCandyId = new Map<string, chrome.tabs.Tab & { id: number }>();
  for (const tab of eligible) {
    const candyId = identities[String(tab.id)];
    if (candyId) byCandyId.set(candyId, tab);
  }
  const unclaimed = eligible.filter((tab) => !byCandyId.has(identities[String(tab.id)] ?? ""));
  const claimedTabIds = new Set<number>();
  const nextIdentities: Record<string, string> = {};
  const ordered = [...snapshot.tabs].sort((left, right) => Number(right.pinned) - Number(left.pinned) || left.index - right.index);
  let activeTabId: number | undefined;
  for (let index = 0; index < ordered.length; index += 1) {
    const desired = ordered[index]!;
    let tab = byCandyId.get(desired.candyId) ?? unclaimed.shift();
    if (tab) {
      const updated = await api.tabs.update(tab.id, { url: desired.url, pinned: desired.pinned, active: false });
      tab = { ...tab, ...updated, id: tab.id };
    } else {
      const created = await api.tabs.create({ url: desired.url, pinned: desired.pinned, active: false });
      if (created.id == null || created.incognito) throw new Error("Browser did not create a normal tab");
      tab = { ...created, id: created.id };
    }
    await api.tabs.move(tab.id, { index });
    claimedTabIds.add(tab.id);
    nextIdentities[String(tab.id)] = desired.candyId;
    if (desired.active && activeTabId === undefined) activeTabId = tab.id;
  }
  const removeIds = eligible.map((tab) => tab.id).filter((id) => !claimedTabIds.has(id));
  if (removeIds.length > 0) await api.tabs.remove(removeIds);
  if (activeTabId !== undefined) await api.tabs.update(activeTabId, { active: true });
  await saveTabIdentities(nextIdentities);
  return snapshot;
}
