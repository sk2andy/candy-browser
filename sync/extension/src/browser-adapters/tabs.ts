import type { DeviceTabSnapshot, TabMutation, TabMutationDraft } from "../core/models.js";
import { parseDeviceTabSnapshot, snapshotFromTabs, syncableTabUrl } from "../core/snapshot-rules.js";
import { extensionApi } from "../platform/webextension.js";
import { loadTabIdentities, saveTabIdentities } from "../storage/stores.js";

export async function collectTabSnapshot(now = new Date()): Promise<ReturnType<typeof snapshotFromTabs>> {
  const tabs = await extensionApi().tabs.query({});
  const storedIdentities = await loadTabIdentities();
  const liveIdentities: Record<string, string> = {};
  for (const tab of tabs) {
    if (tab.id == null || tab.incognito) continue;
    const key = String(tab.id);
    const existing = storedIdentities[key];
    if (!syncableTabUrl(tab) && !existing) continue;
    liveIdentities[key] = existing ?? crypto.randomUUID();
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
    tab.id != null && !tab.incognito && syncableTabUrl(tab) != null);
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

function tabUrl(tab: Pick<chrome.tabs.Tab, "url" | "pendingUrl">): string | undefined {
  return syncableTabUrl(tab);
}

function isTransientNavigation(changeInfo: chrome.tabs.OnUpdatedInfo, tab: chrome.tabs.Tab): boolean {
  return changeInfo.status === "loading" || tab.status === "loading" || tab.pendingUrl !== undefined;
}

async function identityFor(tabId: number, create: boolean): Promise<string | undefined> {
  const identities = await loadTabIdentities();
  const key = String(tabId);
  const existing = identities[key];
  if (existing || !create) return existing;
  const candyId = crypto.randomUUID();
  await saveTabIdentities({ ...identities, [key]: candyId });
  return candyId;
}

export async function mutationForCreatedTab(tab: chrome.tabs.Tab): Promise<TabMutationDraft | null> {
  const candidate = tabUrl(tab);
  if (tab.id == null || tab.incognito || !candidate) return null;
  const candyId = await identityFor(tab.id, true);
  return candyId ? { type: "open", tab: {
    candyId, windowId: tab.windowId, index: tab.index, groupId: tab.groupId != null && tab.groupId >= 0 ? tab.groupId : null,
    active: tab.active, pinned: tab.pinned, title: (tab.title ?? "").slice(0, 4_096), url: new URL(candidate).href,
  } } : null;
}

export async function mutationsForUpdatedTab(
  tabId: number,
  changeInfo: chrome.tabs.OnUpdatedInfo,
  tab: chrome.tabs.Tab,
): Promise<TabMutationDraft[]> {
  const identities = await loadTabIdentities();
  const existing = identities[String(tabId)];
  const candidate = tabUrl(tab);
  if (tab.incognito || !candidate) {
    if (!existing) return [];
    if (!tab.incognito && isTransientNavigation(changeInfo, tab)) return [];
    const { [String(tabId)]: _removed, ...next } = identities;
    await saveTabIdentities(next);
    return [{ type: "close", candyId: existing }];
  }
  if (!existing) {
    const opened = await mutationForCreatedTab(tab);
    return opened ? [opened] : [];
  }
  const mutations: TabMutationDraft[] = [];
  if (changeInfo.url !== undefined || changeInfo.title !== undefined) {
    mutations.push({
      type: "navigate",
      candyId: existing,
      url: new URL(candidate).href,
      title: (tab.title ?? "").slice(0, 4_096),
    });
  }
  if (changeInfo.pinned !== undefined) {
    mutations.push({ type: "set-pinned", candyId: existing, pinned: tab.pinned });
  }
  return mutations;
}

export async function mutationForRemovedTab(tabId: number): Promise<TabMutationDraft | null> {
  const identities = await loadTabIdentities();
  const candyId = identities[String(tabId)];
  if (!candyId) return null;
  const { [String(tabId)]: _removed, ...next } = identities;
  await saveTabIdentities(next);
  return { type: "close", candyId };
}

export async function mutationForMovedTab(tabId: number, _toIndex: number): Promise<TabMutationDraft | null> {
  const candyId = await identityFor(tabId, false);
  if (!candyId) return null;
  const snapshot = await collectTabSnapshot();
  return { type: "reorder", orderedCandyIds: snapshot.tabs.map((tab) => tab.candyId) };
}

export async function applyTabMutation(mutation: TabMutation): Promise<number | null> {
  const api = extensionApi();
  const identities = await loadTabIdentities();
  const candyId = mutation.type === "open" ? mutation.tab.candyId : mutation.type === "reorder" ? undefined : mutation.candyId;
  const entry = candyId ? Object.entries(identities).find(([, identity]) => identity === candyId) : undefined;
  const tabId = entry ? Number(entry[0]) : undefined;
  switch (mutation.type) {
    case "open": {
      const desired = mutation.tab;
      if (tabId !== undefined) {
        await api.tabs.update(tabId, { url: desired.url, pinned: desired.pinned });
        await api.tabs.move(tabId, { index: desired.index });
        return tabId;
      }
      const created = await api.tabs.create({ url: desired.url, pinned: desired.pinned, active: false, index: desired.index });
      if (created.id == null || created.incognito) throw new Error("Browser did not create a normal tab");
      await saveTabIdentities({ ...identities, [String(created.id)]: desired.candyId });
      return created.id;
    }
    case "navigate":
      if (tabId !== undefined) await api.tabs.update(tabId, { url: mutation.url });
      return tabId ?? null;
    case "close":
      if (tabId !== undefined) {
        await api.tabs.remove(tabId);
        const { [String(tabId)]: _removed, ...next } = identities;
        await saveTabIdentities(next);
      }
      return tabId ?? null;
    case "reorder":
      for (let index = 0; index < mutation.orderedCandyIds.length; index += 1) {
        const desiredId = mutation.orderedCandyIds[index]!;
        const browserEntry = Object.entries(identities).find(([, id]) => id === desiredId);
        if (browserEntry) await api.tabs.move(Number(browserEntry[0]), { index });
      }
      return null;
    case "set-pinned":
      if (tabId !== undefined) await api.tabs.update(tabId, { pinned: mutation.pinned });
      return tabId ?? null;
  }
}
