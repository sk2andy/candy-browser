import assert from "node:assert/strict";
import test from "node:test";

import { validateBatch } from "./validate_site_audit_batch.mjs";

const records = [1, 2].map((rank) => ({
  schemaVersion: 2,
  rank,
  domain: `${rank}.example`,
  pass: "current",
  buildId: "build",
}));
const fixture = new Map([[1, "1.example"], [2, "2.example"]]);

test("validates schema, exact ranks, pass, build, and domains", () => {
  assert.doesNotThrow(() => validateBatch(
    records,
    { pass: "current", buildId: "build", ranks: [1, 2] },
    fixture,
  ));
  assert.throws(
    () => validateBatch(
      records,
      { pass: "current", buildId: "other", ranks: [1, 2] },
      fixture,
    ),
    /wrong build ID/,
  );
  assert.throws(
    () => validateBatch(
      records,
      { pass: "current", buildId: "build", ranks: [2, 1] },
      fixture,
    ),
    /Expected rank 2/,
  );
});
