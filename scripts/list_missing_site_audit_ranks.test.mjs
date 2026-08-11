#!/usr/bin/env node

import assert from "node:assert/strict";
import test from "node:test";

import { findMissingRanks, validateFileRanks } from "./list_missing_site_audit_ranks.mjs";

const domains = new Map([[1, "one.test"], [2, "two.test"], [3, "three.test"]]);
const record = (rank) => ({
  schemaVersion: 2,
  rank,
  domain: domains.get(rank),
  pass: "baseline",
  buildId: "build",
});

test("lists only ranks without a valid root record", () => {
  assert.deepEqual(findMissingRanks([record(1), record(3)], domains, "baseline", "build"), [2]);
});

test("rejects stale, malformed, and duplicate records", () => {
  assert.throws(
    () => findMissingRanks([{ ...record(1), buildId: "stale" }], domains, "baseline", "build"),
    /Invalid baseline audit record/,
  );
  assert.throws(
    () => findMissingRanks([record(1), record(1)], domains, "baseline", "build"),
    /Duplicate baseline audit record/,
  );
});

test("requires filenames to match strictly ordered record boundaries", () => {
  assert.doesNotThrow(() =>
    validateFileRanks("sites-baseline-1-3.jsonl", [record(1), record(3)], "baseline"),
  );
  assert.throws(
    () => validateFileRanks("sites-baseline-1-3.jsonl", [record(1), record(2)], "baseline"),
    /filename does not match ordered ranks/,
  );
  assert.throws(
    () => validateFileRanks("sites-baseline-1-3.jsonl", [record(3), record(1)], "baseline"),
    /filename does not match ordered ranks/,
  );
});
