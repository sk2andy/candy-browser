import type { ReducedTab, TabMutation, TabMutationDraft, TabMutationState, TabSnapshotEntry } from "./models.js";
import { isSyncableTabUrl } from "./snapshot-rules.js";

const ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/u;
const CANDY_ID_PATTERN = /^[A-Za-z0-9._:-]{1,128}$/u;
const MAX_APPLIED_MUTATIONS = 2_048;
const MAX_TABS = 1_000;
const MAX_PULL_PAGES = 1_000;

function exactKeys(value: Record<string, unknown>, expected: readonly string[]): boolean {
  const keys = Object.keys(value);
  return keys.length === expected.length && keys.every((key) => expected.includes(key));
}

function identifier(value: unknown): string {
  if (typeof value !== "string" || !ID_PATTERN.test(value)) throw new Error("Invalid tab mutation identity");
  return value;
}

function candyIdentifier(value: unknown): string {
  if (typeof value !== "string" || !CANDY_ID_PATTERN.test(value)) throw new Error("Invalid Candy tab identity");
  return value;
}

function nonNegativeInteger(value: unknown, name: string): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) throw new Error(`Invalid ${name}`);
  return value as number;
}

function normalizedUrl(value: unknown): string {
  if (typeof value !== "string" || value.length > 16_384 || !isSyncableTabUrl(value)) throw new Error("Invalid tab mutation URL");
  return new URL(value).href;
}

function title(value: unknown): string {
  if (typeof value !== "string" || value.length > 4_096) throw new Error("Invalid tab mutation title");
  return value;
}

function parseTab(raw: unknown): TabSnapshotEntry {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new Error("Invalid open tab");
  const tab = raw as Record<string, unknown>;
  if (!exactKeys(tab, ["candyId", "windowId", "index", "groupId", "active", "pinned", "title", "url"])) throw new Error("Invalid open tab fields");
  if (tab.groupId !== null && (!Number.isSafeInteger(tab.groupId) || (tab.groupId as number) < 0)) throw new Error("Invalid tab group");
  if (typeof tab.active !== "boolean" || typeof tab.pinned !== "boolean") throw new Error("Invalid tab state");
  return {
    candyId: candyIdentifier(tab.candyId), windowId: nonNegativeInteger(tab.windowId, "tab window"),
    index: nonNegativeInteger(tab.index, "tab index"), groupId: tab.groupId as number | null,
    active: tab.active, pinned: tab.pinned, title: title(tab.title), url: normalizedUrl(tab.url),
  };
}

export function parseTabMutation(raw: unknown): TabMutation {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new Error("Invalid tab mutation");
  const value = raw as Record<string, unknown>;
  if (value.schemaVersion !== 2 || typeof value.type !== "string") throw new Error("Unsupported tab mutation");
  const mutationId = identifier(value.mutationId);
  const targetDeviceId = identifier(value.targetDeviceId);
  switch (value.type) {
    case "open":
      if (!exactKeys(value, ["schemaVersion", "mutationId", "targetDeviceId", "type", "tab"])) throw new Error("Invalid open mutation fields");
      return { schemaVersion: 2, mutationId, targetDeviceId, type: "open", tab: parseTab(value.tab) };
    case "navigate":
      if (!exactKeys(value, ["schemaVersion", "mutationId", "targetDeviceId", "type", "candyId", "title", "url"])) throw new Error("Invalid navigate mutation fields");
      return { schemaVersion: 2, mutationId, targetDeviceId, type: "navigate", candyId: candyIdentifier(value.candyId), title: title(value.title), url: normalizedUrl(value.url) };
    case "close":
      if (!exactKeys(value, ["schemaVersion", "mutationId", "targetDeviceId", "type", "candyId"])) throw new Error("Invalid close mutation fields");
      return { schemaVersion: 2, mutationId, targetDeviceId, type: "close", candyId: candyIdentifier(value.candyId) };
    case "reorder": {
      if (!exactKeys(value, ["schemaVersion", "mutationId", "targetDeviceId", "type", "orderedCandyIds"]) ||
          !Array.isArray(value.orderedCandyIds) || value.orderedCandyIds.length > MAX_TABS) throw new Error("Invalid reorder mutation fields");
      const orderedCandyIds = value.orderedCandyIds.map(candyIdentifier);
      if (new Set(orderedCandyIds).size !== orderedCandyIds.length) throw new Error("Duplicate reordered tab identity");
      return { schemaVersion: 2, mutationId, targetDeviceId, type: "reorder", orderedCandyIds };
    }
    case "set-pinned":
      if (!exactKeys(value, ["schemaVersion", "mutationId", "targetDeviceId", "type", "candyId", "pinned"]) || typeof value.pinned !== "boolean") throw new Error("Invalid pin mutation fields");
      return { schemaVersion: 2, mutationId, targetDeviceId, type: "set-pinned", candyId: candyIdentifier(value.candyId), pinned: value.pinned };
    default:
      throw new Error("Unsupported tab mutation type");
  }
}

export function emptyTabMutationState(): TabMutationState {
  return { schemaVersion: 2, tabs: {}, tombstones: {}, appliedMutationIds: [] };
}

export function mutationCandyId(mutation: TabMutation | TabMutationDraft): string | undefined {
  if (mutation.type === "open") return mutation.tab.candyId;
  if (mutation.type === "reorder") return undefined;
  return mutation.candyId;
}

export function reconciliationDrafts(
  previousCandyIds: readonly string[],
  currentTabs: readonly TabSnapshotEntry[],
): TabMutationDraft[] {
  const previousIds = new Set(previousCandyIds);
  const currentIds = new Set(currentTabs.map((tab) => tab.candyId));
  const closes = [...previousIds]
    .filter((candyId) => !currentIds.has(candyId))
    .map((candyId): TabMutationDraft => ({ type: "close", candyId }));
  const opens = currentTabs
    .filter((tab) => !previousIds.has(tab.candyId))
    .map((tab): TabMutationDraft => ({ type: "open", tab }));
  const existing = currentTabs.filter((tab) => previousIds.has(tab.candyId));
  const navigations = existing.map((tab): TabMutationDraft => ({
    type: "navigate", candyId: tab.candyId, url: tab.url, title: tab.title,
  }));
  const pins = existing.map((tab): TabMutationDraft => ({
    type: "set-pinned", candyId: tab.candyId, pinned: tab.pinned,
  }));
  const reorder: TabMutationDraft = { type: "reorder", orderedCandyIds: currentTabs.map((tab) => tab.candyId) };
  return [...closes, ...opens, ...navigations, ...pins, reorder];
}

export function reduceTabMutation(state: TabMutationState, rawMutation: unknown): { state: TabMutationState; changed: boolean } {
  const mutation = parseTabMutation(rawMutation);
  if (state.appliedMutationIds.includes(mutation.mutationId)) return { state, changed: false };
  const tabs = { ...state.tabs };
  const tombstones = { ...state.tombstones };
  let changed = false;
  if (mutation.type === "reorder") {
    const existingIds = Object.keys(tabs);
    if (existingIds.length === 0) {
      changed = true;
    } else if (mutation.orderedCandyIds.length === existingIds.length && mutation.orderedCandyIds.every((id) => tabs[id])) {
      mutation.orderedCandyIds.forEach((id, index) => { tabs[id] = { ...tabs[id]!, index }; });
      changed = existingIds.some((id) => tabs[id]!.index !== state.tabs[id]!.index);
    }
  } else {
    const candyId = mutationCandyId(mutation)!;
    const current = tabs[candyId];
    if (mutation.type === "close") {
      changed = current !== undefined || tombstones[candyId] !== true;
      delete tabs[candyId];
      tombstones[candyId] = true;
    } else if (!tombstones[candyId]) {
      let next: ReducedTab | undefined;
      if (mutation.type === "open") next = mutation.tab;
      else if (mutation.type === "navigate") {
        if (current) next = { ...current, url: mutation.url, title: mutation.title };
        else changed = true;
      } else if (mutation.type === "set-pinned") {
        if (current) next = { ...current, pinned: mutation.pinned };
        else changed = true;
      }
      if (next) {
        tabs[candyId] = next;
        changed = JSON.stringify(current) !== JSON.stringify(next);
      }
    }
  }
  const appliedMutationIds = [...state.appliedMutationIds, mutation.mutationId].slice(-MAX_APPLIED_MUTATIONS);
  return { state: { schemaVersion: 2, tabs, tombstones, appliedMutationIds }, changed };
}

export function nextOutboxAction(
  existing: readonly { mutationType: TabMutation["type"]; candyId?: string }[],
  incoming: TabMutationDraft,
): { keep: number; skipIncoming: boolean } {
  const incomingCandyId = mutationCandyId(incoming);
  const last = existing.at(-1);
  if (incoming.type === "navigate" && last?.mutationType === "navigate" && last.candyId === incomingCandyId) return { keep: existing.length - 1, skipIncoming: false };
  if (incoming.type !== "close") return { keep: existing.length, skipIncoming: false };
  let keep = existing.length;
  while (keep > 0) {
    const candidate = existing[keep - 1]!;
    if (candidate.candyId !== incomingCandyId) break;
    if (candidate.mutationType === "open") return { keep: keep - 1, skipIncoming: true };
    if (candidate.mutationType === "close") return { keep, skipIncoming: true };
    keep -= 1;
  }
  return { keep, skipIncoming: false };
}

export function classifyDeltaRevision(currentRevision: string, baseRevision: string, incomingRevision: string): "replay" | "contiguous" | "gap" {
  const current = BigInt(currentRevision);
  const base = BigInt(baseRevision);
  const incoming = BigInt(incomingRevision);
  if (incoming <= current) return "replay";
  return base === current && incoming === current + 1n ? "contiguous" : "gap";
}

export function classifyRealtimeCursor(currentCursor: string, incomingCursor: string): "replay" | "contiguous" | "gap" {
  const parse = (value: string): { epoch: string; sequence: bigint } | null => {
    const match = /^([A-Za-z0-9_-]+)\.(0|[1-9][0-9]{0,18})$/u.exec(value);
    if (!match) return null;
    const sequence = BigInt(match[2]!);
    return sequence <= 9_223_372_036_854_775_807n ? { epoch: match[1]!, sequence } : null;
  };
  const current = parse(currentCursor);
  const incoming = parse(incomingCursor);
  if (!current || !incoming || current.epoch !== incoming.epoch) return "gap";
  if (incoming.sequence <= current.sequence) return "replay";
  return incoming.sequence === current.sequence + 1n ? "contiguous" : "gap";
}

function parseCursor(value: string): { epoch: string; sequence: bigint } {
  const match = /^([A-Za-z0-9_-]+)\.(0|[1-9][0-9]{0,18})$/u.exec(value);
  if (!match) throw new Error("Server returned an invalid cursor.");
  const sequence = BigInt(match[2]!);
  if (sequence > 9_223_372_036_854_775_807n) throw new Error("Server returned an invalid cursor.");
  return { epoch: match[1]!, sequence };
}

export function monotonicCursor(currentCursor: string, candidateCursor: string): string {
  const candidate = parseCursor(candidateCursor);
  if (!currentCursor) return candidateCursor;
  const current = parseCursor(currentCursor);
  if (current.epoch !== candidate.epoch) throw new Error("Server changed the cursor epoch unexpectedly.");
  return candidate.sequence > current.sequence ? candidateCursor : currentCursor;
}

export class PullCursorGuard {
  #pages = 0;

  accept(currentCursor: string, nextCursor: string, hasMore: boolean): void {
    this.#pages += 1;
    const next = parseCursor(nextCursor);
    const current = currentCursor ? parseCursor(currentCursor) : null;
    const regressed = current !== null && (current.epoch !== next.epoch || next.sequence < current.sequence);
    const stalled = current !== null && next.sequence === current.sequence;
    if (regressed || (hasMore && stalled) || (hasMore && this.#pages >= MAX_PULL_PAGES)) {
      throw new Error("Server returned non-progressing sync pagination.");
    }
  }
}

export function shouldApplyMutationToBrowser(
  mutation: TabMutation,
  state: TabMutationState,
  writerDeviceId: string,
  localDeviceId: string,
): boolean {
  if (writerDeviceId === localDeviceId || state.appliedMutationIds.includes(mutation.mutationId)) return false;
  if (mutation.type === "reorder") return true;
  return state.tombstones[mutationCandyId(mutation)!] !== true;
}
