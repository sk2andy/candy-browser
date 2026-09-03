import assert from "node:assert/strict";
import test from "node:test";

import type { TabMutation } from "../src/core/models.js";
import {
  classifyDeltaRevision, classifyRealtimeCursor, emptyTabMutationState, monotonicCursor, nextOutboxAction,
  PullCursorGuard, reconciliationDrafts, reduceTabMutation, shouldApplyMutationToBrowser,
} from "../src/core/tab-mutation-rules.js";

const base = { schemaVersion: 2 as const, targetDeviceId: "desktop-1" };
const open: TabMutation = {
  ...base, mutationId: "mutation-open", type: "open",
  tab: { candyId: "tab-1", windowId: 1, index: 0, groupId: null, active: true, pinned: false, title: "One", url: "https://example.com/" },
};

test("reducer is idempotent and close tombstone wins over late updates or reopen", () => {
  const opened = reduceTabMutation(emptyTabMutationState(), open);
  assert.equal(opened.changed, true);
  const replay = reduceTabMutation(opened.state, open);
  assert.equal(replay.changed, false);
  assert.strictEqual(replay.state, opened.state);
  const closed = reduceTabMutation(opened.state, { ...base, mutationId: "mutation-close", type: "close", candyId: "tab-1" });
  assert.equal(closed.state.tabs["tab-1"], undefined);
  const late = reduceTabMutation(closed.state, { ...base, mutationId: "mutation-late", type: "navigate", candyId: "tab-1", url: "https://late.example/", title: "Late" });
  const reopened = reduceTabMutation(late.state, { ...open, mutationId: "mutation-reopen" });
  assert.equal(late.changed, false);
  assert.equal(reopened.changed, false);
});

test("reorder requires full stable identity set and assigns deterministic indices", () => {
  let state = reduceTabMutation(emptyTabMutationState(), open).state;
  state = reduceTabMutation(state, { ...open, mutationId: "mutation-open-2", tab: { ...open.tab, candyId: "tab-2", index: 1 } }).state;
  const reordered = reduceTabMutation(state, { ...base, mutationId: "mutation-order", type: "reorder", orderedCandyIds: ["tab-2", "tab-1"] });
  assert.equal(reordered.changed, true);
  assert.equal(reordered.state.tabs["tab-2"]?.index, 0);
  assert.equal(reordered.state.tabs["tab-1"]?.index, 1);
  const incomplete = reduceTabMutation(reordered.state, { ...base, mutationId: "mutation-bad-order", type: "reorder", orderedCandyIds: ["tab-1"] });
  assert.equal(incomplete.changed, false);
});

test("outbox coalesces navigation and close supersedes pending per-tab updates", () => {
  const navigation = { mutationType: "navigate" as const, candyId: "tab-1" };
  const pin = { mutationType: "set-pinned" as const, candyId: "tab-1" };
  assert.deepEqual(nextOutboxAction([navigation], { type: "navigate", candyId: "tab-1", url: "https://new.example/", title: "" }), { keep: 0, skipIncoming: false });
  assert.deepEqual(nextOutboxAction([navigation, pin], { type: "close", candyId: "tab-1" }), { keep: 0, skipIncoming: false });
  assert.deepEqual(nextOutboxAction([{ mutationType: "open", candyId: "tab-1" }, navigation], { type: "close", candyId: "tab-1" }), { keep: 0, skipIncoming: true });
});

test("revision classifier detects replay/gaps and suppresses writer echo", () => {
  assert.equal(classifyDeltaRevision("7", "7", "8"), "contiguous");
  assert.equal(classifyDeltaRevision("8", "7", "8"), "replay");
  assert.equal(classifyDeltaRevision("7", "8", "9"), "gap");
  assert.equal(classifyRealtimeCursor("epoch-a.7", "epoch-a.8"), "contiguous");
  assert.equal(classifyRealtimeCursor("epoch-a.8", "epoch-a.8"), "replay");
  assert.equal(classifyRealtimeCursor("epoch-a.7", "epoch-a.9"), "gap");
  assert.equal(classifyRealtimeCursor("epoch-a.7", "epoch-b.8"), "gap");
  const state = emptyTabMutationState();
  const navigate: TabMutation = {
    ...base, mutationId: "mutation-navigate", type: "navigate", candyId: "tab-1",
    url: "https://example.com/next", title: "Next",
  };
  assert.equal(shouldApplyMutationToBrowser(navigate, state, "desktop", "desktop"), false);
  assert.equal(shouldApplyMutationToBrowser(navigate, state, "phone", "desktop"), true);
  const replayed = reduceTabMutation(state, navigate).state;
  assert.equal(shouldApplyMutationToBrowser(navigate, replayed, "phone", "desktop"), false);
  const tombstoned = reduceTabMutation(state, {
    ...base, mutationId: "mutation-close", type: "close", candyId: "tab-1",
  }).state;
  assert.equal(shouldApplyMutationToBrowser(navigate, tombstoned, "phone", "desktop"), false);
});

test("push cursor advances monotonically and rejects an epoch change", () => {
  assert.equal(monotonicCursor("", "epoch-a.1"), "epoch-a.1");
  assert.equal(monotonicCursor("epoch-a.5", "epoch-a.2"), "epoch-a.5");
  assert.equal(monotonicCursor("epoch-a.5", "epoch-a.6"), "epoch-a.6");
  assert.throws(() => monotonicCursor("epoch-a.5", "epoch-b.6"), /epoch/u);
});

test("pull pagination rejects duplicate and cyclic cursors", () => {
  const duplicate = new PullCursorGuard();
  assert.throws(() => duplicate.accept("epoch-a.4", "epoch-a.4", true), /non-progressing/u);

  const cyclic = new PullCursorGuard();
  cyclic.accept("epoch-a.1", "epoch-a.2", true);
  cyclic.accept("epoch-a.2", "epoch-a.3", true);
  assert.throws(() => cyclic.accept("epoch-a.3", "epoch-a.1", true), /non-progressing/u);
  assert.throws(() => new PullCursorGuard().accept("epoch-a.4", "epoch-a.3", false), /non-progressing/u);
  assert.throws(() => new PullCursorGuard().accept("epoch-a.4", "epoch-b.5", false), /non-progressing/u);
  assert.throws(() => new PullCursorGuard().accept("epoch-a.4", "invalid", false), /invalid cursor/u);
});

test("opt-in reconciliation emits explicit cross-client mutations for the current browser state", () => {
  const current = [
    { ...open.tab, candyId: "tab-1", url: "https://example.com/new", title: "New", pinned: true },
    { ...open.tab, candyId: "tab-3", index: 1, url: "https://three.example/", title: "Three" },
  ];
  assert.deepEqual(reconciliationDrafts(["tab-1", "tab-2"], current), [
    { type: "close", candyId: "tab-2" },
    { type: "open", tab: current[1] },
    { type: "navigate", candyId: "tab-1", url: current[0]!.url, title: current[0]!.title },
    { type: "set-pinned", candyId: "tab-1", pinned: true },
    { type: "reorder", orderedCandyIds: ["tab-1", "tab-3"] },
  ]);
});
