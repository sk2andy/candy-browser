import assert from "node:assert/strict";
import test from "node:test";

import { classifyPair, summarize, toCsv } from "./summarize_site_default_audit.mjs";

const record = (overrides = {}) => ({
  rank: 1,
  schemaVersion: 2,
  domain: "example.com",
  pass: "current",
  buildId: "build",
  finalUrl: "https://www.example.com/",
  loadingTimedOut: false,
  mainFrameError: null,
  probe: { html: true, challenge: false, cookie: { visibleCount: 0 } },
  verticalScroll: { applicable: true, before: 0, after: 0, maximum: 500, worked: false },
  ...overrides,
});

test("classifies hidden banner, locked page, and successful force override", () => {
  const baseline = record({
    pass: "baseline",
    probe: { html: true, challenge: false, cookie: { visibleCount: 1 } },
  });
  const current = record();
  const force = record({
    pass: "force-scroll",
    verticalScroll: { applicable: true, before: 0, after: 0, maximum: 500, worked: false },
    forceVerticalScroll: {
      enabled: true,
      finalUrl: "https://www.example.com/",
      loadingTimedOut: false,
      mainFrameError: null,
      probe: { html: true, challenge: false, cookie: { visibleCount: 0 } },
      verticalScroll: { applicable: true, after: 512, worked: true },
    },
  });

  assert.deepEqual(classifyPair(baseline, current, force), {
    cookieClassification: "hidden",
    scrollClassification: "locked",
    forceClassification: "fixed",
    recommendation: "force_vertical_scroll",
  });
});

test("keeps no-banner and failed force outcomes explicit", () => {
  const noBanner = classifyPair(record({ pass: "baseline" }), record());
  assert.equal(noBanner.cookieClassification, "not_present_in_baseline");
  assert.equal(noBanner.scrollClassification, "not_tested");

  const baseline = record({
    pass: "baseline",
    probe: { html: true, challenge: false, cookie: { visibleCount: 1 } },
  });
  const failed = classifyPair(baseline, record(), record({
    pass: "force-scroll",
    forceVerticalScroll: {
      enabled: true,
      finalUrl: "https://www.example.com/",
      loadingTimedOut: false,
      mainFrameError: null,
      probe: { html: true, challenge: false, cookie: { visibleCount: 0 } },
      verticalScroll: { applicable: true, after: 0, worked: false },
    },
  }));
  assert.equal(failed.forceClassification, "not_fixed");
  assert.equal(failed.recommendation, "show_cookie_consent");
});

test("rejects post-force redirect as indeterminate", () => {
  const baseline = record({
    pass: "baseline",
    probe: { html: true, challenge: false, cookie: { visibleCount: 1 } },
  });
  const force = record({
    pass: "force-scroll",
    forceVerticalScroll: {
      enabled: true,
      finalUrl: "https://different.example/",
      loadingTimedOut: false,
      mainFrameError: null,
      probe: { html: true, challenge: false, cookie: { visibleCount: 0 } },
      verticalScroll: { applicable: true, after: 512, worked: true },
    },
  });
  assert.equal(classifyPair(baseline, record(), force).forceClassification, "indeterminate");
});

test("validates paired rank coverage and emits stable CSV", () => {
  const baseline = record({
    pass: "baseline",
    probe: { html: true, challenge: false, cookie: { visibleCount: 1 } },
  });
  const rows = summarize([baseline, record()], new Map([[1, "example.com"]]), 1);
  const csv = toCsv(rows);
  assert.match(csv, /^rank,target_domain,/);
  assert.match(csv, /1,example\.com,www\.example\.com,www\.example\.com,1,0,hidden/);
  assert.match(csv, /force_attempted,force_attempt_count,force_scroll_after,force_classification/);
  assert.match(csv, /force_pre_error,force_pre_timed_out,force_enabled/);
  assert.match(csv, /force_result_error,force_result_timed_out/);
});

test("marks repeated inconclusive force attempts as terminal with visible consent", () => {
  const baseline = record({
    pass: "baseline",
    probe: { html: true, challenge: false, cookie: { visibleCount: 1 } },
  });
  const force = record({
    pass: "force-scroll",
    auditAttemptsExhausted: true,
    auditAttemptCount: 2,
    mainFrameError: "webview-not-attached",
    probe: null,
    verticalScroll: null,
  });
  assert.deepEqual(classifyPair(baseline, record(), force), {
    cookieClassification: "hidden",
    scrollClassification: "locked",
    forceClassification: "unavailable_after_retries",
    recommendation: "show_cookie_consent",
  });
});

test("rejects force evidence from a different APK", () => {
  const baseline = record({
    pass: "baseline",
    probe: { html: true, challenge: false, cookie: { visibleCount: 1 } },
  });
  const force = record({ pass: "force-scroll", buildId: "other-build" });
  assert.throws(
    () => summarize([baseline, record(), force], new Map([[1, "example.com"]]), 1),
    /same single Candy APK build ID/,
  );
});

test("rejects unknown passes and out-of-range paired records", () => {
  const baseline = record({ pass: "baseline" });
  assert.throws(
    () => summarize(
      [baseline, record(), record({ pass: "candidate" })],
      new Map([[1, "example.com"]]),
      1,
    ),
    /Unexpected audit pass/,
  );
  assert.throws(
    () => summarize(
      [baseline, record(), record({ rank: 2, pass: "baseline" })],
      new Map([[1, "example.com"]]),
      1,
    ),
    /Unexpected baseline rank 2/,
  );
});
