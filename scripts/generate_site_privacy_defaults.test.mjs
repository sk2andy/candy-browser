import assert from "node:assert/strict";
import test from "node:test";

import { generateDefaults, parseCsv } from "./generate_site_privacy_defaults.mjs";

test("parses quoted CSV fields", () => {
  assert.deepEqual(parseCsv('a,b\n1,"two, ""quoted"""\n'), [
    ["a", "b"],
    ["1", 'two, "quoted"'],
  ]);
});

const HEADER =
  "rank,current_final_host,cookie_classification,scroll_classification," +
  "force_classification,recommendation";

test("resolves duplicate hosts by evidence strength and safe fallback", () => {
  const csv = [
    HEADER,
    "1,www.z.example,hidden,locked,unavailable_after_retries,show_cookie_consent",
    "2,a.example,hidden,locked,not_fixed,show_cookie_consent",
    "3,www.z.example,hidden,locked,fixed,force_vertical_scroll",
    "4,a.example,hidden,locked,fixed,force_vertical_scroll",
  ].join("\n");
  assert.equal(
    generateDefaults(csv, 4),
    "# candy site privacy defaults v2\n" +
      "cookie_banner_removal_disabled\ta.example\n" +
      "force_vertical_scroll\twww.z.example\n",
  );
});

test("rejects incomplete audits and unsafe hosts", () => {
  assert.throws(
    () => generateDefaults(
      `${HEADER}\n1,a.example,hidden,scrollable,not_tested,none\n`,
      2,
    ),
    /Expected 2 audit rows/,
  );
  assert.throws(
    () => generateDefaults(
      `${HEADER}\n1,unsafe host,hidden,locked,fixed,force_vertical_scroll\n`,
      1,
    ),
    /Invalid recommended host/,
  );
  assert.throws(
    () => generateDefaults(
      `${HEADER}\n1,a.example,hidden,locked,pending,none\n`,
      1,
    ),
    /needs a conclusive/,
  );
});

test("rejects recommendations that contradict measured classifications", () => {
  const header = `${HEADER}\n`;
  assert.throws(
    () => generateDefaults(
      `${header}1,a.example,hidden,scrollable,not_tested,force_vertical_scroll\n`,
      1,
    ),
    /Inconsistent recommendation/,
  );
  assert.throws(
    () => generateDefaults(
      `${header}1,a.example,hidden,locked,not_fixed,force_vertical_scroll\n`,
      1,
    ),
    /Inconsistent recommendation/,
  );
  assert.throws(
    () => generateDefaults(
      `${header}1,a.example,hidden,locked,fixed,none\n`,
      1,
    ),
    /Inconsistent recommendation/,
  );
});

test("turns an explicit exhausted-retry outcome into visible consent", () => {
  const csv =
    `${HEADER}\n` +
    "1,a.example,hidden,locked,unavailable_after_retries,show_cookie_consent\n";
  assert.equal(
    generateDefaults(csv, 1),
    "# candy site privacy defaults v2\ncookie_banner_removal_disabled\ta.example\n",
  );
});
