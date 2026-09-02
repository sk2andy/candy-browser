import type { DeviceTabSnapshot, TabSnapshotEntry } from "./models.js";

const ALLOWED_PROTOCOLS = new Set(["http:", "https:"]);
const MAX_TABS = 1_000;

export function isSyncableTabUrl(candidate: string | undefined): candidate is string {
  if (!candidate) return false;
  try {
    return ALLOWED_PROTOCOLS.has(new URL(candidate).protocol);
  } catch {
    return false;
  }
}

export interface BrowserTabLike {
  id?: number | undefined;
  windowId: number;
  index: number;
  groupId?: number | undefined;
  active: boolean;
  pinned: boolean;
  incognito: boolean;
  title?: string | undefined;
  url?: string | undefined;
  pendingUrl?: string | undefined;
}

export function snapshotFromTabs(
  tabs: readonly BrowserTabLike[],
  capturedAt: string,
  identities: Readonly<Record<string, string>> = {},
): DeviceTabSnapshot {
  const normalized = tabs.flatMap<TabSnapshotEntry>((tab) => {
    const candidate = tab.url ?? tab.pendingUrl;
    if (tab.incognito || tab.id == null || !candidate) return [];
    if (!isSyncableTabUrl(candidate)) return [];
    const candyId = identities[String(tab.id)] ?? `tab-${tab.windowId}-${tab.id}`;
    return [{
      candyId,
      windowId: tab.windowId,
      index: tab.index,
      groupId: tab.groupId != null && tab.groupId >= 0 ? tab.groupId : null,
      active: tab.active,
      pinned: tab.pinned,
      title: (tab.title ?? "").slice(0, 4_096),
      url: new URL(candidate).href,
    }];
  });
  normalized.sort((left, right) => left.windowId - right.windowId || left.index - right.index);
  return { schemaVersion: 1, capturedAt, tabs: normalized };
}

function exactKeys(value: Record<string, unknown>, keys: readonly string[]): boolean {
  const actual = Object.keys(value);
  return actual.length === keys.length && actual.every((key) => keys.includes(key));
}

export function parseDeviceTabSnapshot(value: unknown): DeviceTabSnapshot {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid tab snapshot");
  const snapshot = value as Record<string, unknown>;
  if (!exactKeys(snapshot, ["schemaVersion", "capturedAt", "tabs"]) || snapshot.schemaVersion !== 1) throw new Error("Invalid tab snapshot fields");
  if (typeof snapshot.capturedAt !== "string" || snapshot.capturedAt.length > 40 || !Number.isFinite(Date.parse(snapshot.capturedAt))) {
    throw new Error("Invalid tab snapshot timestamp");
  }
  if (!Array.isArray(snapshot.tabs) || snapshot.tabs.length > MAX_TABS) throw new Error("Invalid tab snapshot size");
  const seen = new Set<string>();
  const tabs = snapshot.tabs.map((raw): TabSnapshotEntry => {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new Error("Invalid tab snapshot entry");
    const tab = raw as Record<string, unknown>;
    if (!exactKeys(tab, ["candyId", "windowId", "index", "groupId", "active", "pinned", "title", "url"])) throw new Error("Invalid tab snapshot entry fields");
    if (typeof tab.candyId !== "string" || !/^[A-Za-z0-9._:-]{1,128}$/u.test(tab.candyId) || seen.has(tab.candyId)) throw new Error("Invalid tab identity");
    if (!Number.isSafeInteger(tab.windowId) || (tab.windowId as number) < 0 || !Number.isSafeInteger(tab.index) || (tab.index as number) < 0) throw new Error("Invalid tab position");
    if (tab.groupId !== null && (!Number.isSafeInteger(tab.groupId) || (tab.groupId as number) < 0)) throw new Error("Invalid tab group");
    if (typeof tab.active !== "boolean" || typeof tab.pinned !== "boolean") throw new Error("Invalid tab state");
    if (typeof tab.title !== "string" || tab.title.length > 4_096) throw new Error("Invalid tab title");
    if (typeof tab.url !== "string" || tab.url.length > 16_384 || !isSyncableTabUrl(tab.url)) throw new Error("Invalid tab URL");
    seen.add(tab.candyId);
    return tab as unknown as TabSnapshotEntry;
  });
  return { schemaVersion: 1, capturedAt: snapshot.capturedAt, tabs };
}
