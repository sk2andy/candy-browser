#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

export const EXPECTED_RANKS = 10_000;

const isObject = (value) => value !== null && typeof value === "object" && !Array.isArray(value);

const visibleCookies = (record) =>
  isObject(record?.probe?.cookie) ? Number(record.probe.cookie.visibleCount || 0) : null;

const finalHost = (record) => {
  try {
    return new URL(record.finalUrl).hostname.toLowerCase();
  } catch {
    return "";
  }
};

const usable = (record) =>
  isObject(record?.probe) &&
  record.probe.html === true &&
  record.probe.challenge !== true &&
  visibleCookies(record) !== null &&
  !record.loadingTimedOut &&
  record.mainFrameError == null;

export function classifyPair(baseline, current, force = null) {
  const baselineHost = finalHost(baseline).replace(/^www\./, "");
  const currentHost = finalHost(current).replace(/^www\./, "");
  const sameFinalHost = baselineHost !== "" && baselineHost === currentHost;
  let cookieClassification = "indeterminate";
  if (usable(baseline) && usable(current) && sameFinalHost) {
    if (visibleCookies(baseline) > 0) {
      cookieClassification = visibleCookies(current) === 0 ? "hidden" : "still_visible";
    } else {
      cookieClassification = "not_present_in_baseline";
    }
  }

  let scrollClassification = "not_tested";
  if (cookieClassification === "hidden") {
    const scroll = current.verticalScroll;
    if (!isObject(scroll)) scrollClassification = "indeterminate";
    else if (scroll.applicable !== true) scrollClassification = "not_applicable";
    else scrollClassification = scroll.worked === true ? "scrollable" : "locked";
  }

  let forceClassification = "not_tested";
  if (scrollClassification === "locked") {
    if (!force) {
      forceClassification = "pending";
    } else {
      const preForceScroll = force.verticalScroll;
      const result = force.forceVerticalScroll;
      const scroll = isObject(result) ? result.verticalScroll : null;
      const forceHost = finalHost(force).replace(/^www\./, "");
      const resultHost = finalHost(result).replace(/^www\./, "");
      if (
        !usable(force) || visibleCookies(force) !== 0 || forceHost !== currentHost ||
        !isObject(preForceScroll)
      ) {
        forceClassification = "indeterminate";
      } else if (preForceScroll.applicable !== true || preForceScroll.worked === true) {
        forceClassification = "not_reproduced";
      } else if (
        !isObject(result) || result.enabled !== true || !isObject(scroll) ||
        !usable(result) || visibleCookies(result) !== 0 || resultHost !== currentHost
      ) {
        forceClassification = "indeterminate";
      } else if (result.loadingTimedOut || result.mainFrameError != null) {
        forceClassification = "indeterminate";
      } else {
        forceClassification = scroll.applicable === true && scroll.worked === true
          ? "fixed"
          : "not_fixed";
      }
    }
  }
  if (
    forceClassification === "indeterminate" &&
    force?.auditAttemptsExhausted === true &&
    Number.isInteger(force.auditAttemptCount) &&
    force.auditAttemptCount >= 2
  ) {
    forceClassification = "unavailable_after_retries";
  }

  const recommendation = forceClassification === "fixed"
    ? "force_vertical_scroll"
    : cookieClassification === "hidden" &&
      scrollClassification === "locked" &&
      ["not_fixed", "unavailable_after_retries"].includes(forceClassification)
      ? "show_cookie_consent"
      : "none";
  return {
    cookieClassification,
    scrollClassification,
    forceClassification,
    recommendation,
  };
}

const readRecords = (inputPaths) => inputPaths.flatMap((inputPath) =>
  fs.readFileSync(inputPath, "utf8")
    .split("\n")
    .filter(Boolean)
    .map((line, index) => {
      try {
        return JSON.parse(line);
      } catch (error) {
        throw new Error(`${inputPath}:${index + 1}: ${error.message}`);
      }
    }),
);

const indexRecords = (records) => {
  const byPass = new Map();
  for (const record of records) {
    const pass = String(record.pass || "").trim();
    const rank = Number(record.rank);
    if (!pass || !Number.isInteger(rank)) throw new Error("Every record needs pass and integer rank");
    if (record.schemaVersion !== 2) throw new Error(`Unsupported audit schema at ${pass} rank ${rank}`);
    const recordsByRank = byPass.get(pass) || new Map();
    if (recordsByRank.has(rank)) throw new Error(`Duplicate ${pass} rank ${rank}`);
    recordsByRank.set(rank, record);
    byPass.set(pass, recordsByRank);
  }
  return byPass;
};

const readFixture = (fixturePath) => new Map(
  fs.readFileSync(fixturePath, "utf8")
    .split("\n")
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => {
      const [rank, domain] = line.split(",");
      return [Number(rank), domain];
    }),
);

const csv = (value) => {
  const text = value == null ? "" : String(value);
  return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
};

export function summarize(records, fixtureDomains, expectedRanks = EXPECTED_RANKS) {
  const byPass = indexRecords(records);
  for (const pass of byPass.keys()) {
    if (!["baseline", "current", "force-scroll"].includes(pass)) {
      throw new Error(`Unexpected audit pass: ${pass}`);
    }
  }
  const baseline = byPass.get("baseline") || new Map();
  const current = byPass.get("current") || new Map();
  const force = byPass.get("force-scroll") || new Map();
  for (const [pass, recordsByRank] of [
    ["baseline", baseline],
    ["current", current],
    ["force-scroll", force],
  ]) {
    for (const rank of recordsByRank.keys()) {
      if (rank < 1 || rank > expectedRanks) {
        throw new Error(`Unexpected ${pass} rank ${rank}`);
      }
    }
  }
  const buildIds = (recordsByRank) => new Set(
    [...recordsByRank.values()].map((record) => String(record.buildId || "").trim()),
  );
  const baselineBuildIds = buildIds(baseline);
  const currentBuildIds = buildIds(current);
  if (
    baselineBuildIds.size !== 1 || baselineBuildIds.has("") ||
    currentBuildIds.size !== 1 || currentBuildIds.has("")
  ) {
    throw new Error("Baseline and current must each use exactly one non-empty build ID");
  }
  if ([...baselineBuildIds][0] !== [...currentBuildIds][0]) {
    throw new Error("Baseline and current must use the same Candy APK build ID");
  }
  if (force.size > 0) {
    const forceBuildIds = buildIds(force);
    if (
      forceBuildIds.size !== 1 || forceBuildIds.has("") ||
      [...forceBuildIds][0] !== [...currentBuildIds][0]
    ) {
      throw new Error("Force-scroll must use the same single Candy APK build ID");
    }
  }
  const rows = [];
  for (let rank = 1; rank <= expectedRanks; rank += 1) {
    const expectedDomain = fixtureDomains.get(rank);
    const baselineRecord = baseline.get(rank);
    const currentRecord = current.get(rank);
    if (!expectedDomain) throw new Error(`Fixture missing rank ${rank}`);
    if (!baselineRecord || !currentRecord) throw new Error(`Audit pair missing rank ${rank}`);
    if (baselineRecord.domain !== expectedDomain || currentRecord.domain !== expectedDomain) {
      throw new Error(`Rank ${rank} domain mismatch`);
    }
    const forceRecord = force.get(rank) || null;
    if (forceRecord && forceRecord.domain !== expectedDomain) {
      throw new Error(`Force rank ${rank} domain mismatch`);
    }
    rows.push({
      rank,
      domain: expectedDomain,
      baseline: baselineRecord,
      current: currentRecord,
      force: forceRecord,
      classification: classifyPair(baselineRecord, currentRecord, forceRecord),
    });
  }
  return rows;
}

export function toCsv(rows) {
  const header = [
    "rank", "target_domain", "baseline_final_host", "current_final_host",
    "baseline_cookie_visible", "current_cookie_visible", "cookie_classification",
    "scroll_applicable", "scroll_before", "scroll_after", "scroll_maximum",
    "scroll_classification", "force_attempted", "force_attempt_count", "force_scroll_after",
    "force_classification", "recommendation", "force_pre_error",
    "force_pre_timed_out", "force_enabled", "force_result_error",
    "force_result_timed_out", "baseline_error", "current_error",
    "baseline_timed_out", "current_timed_out", "baseline_challenge", "current_challenge",
    "baseline_build_id", "current_build_id",
  ];
  const lines = rows.map(({ rank, domain, baseline, current, force, classification }) => {
    const scroll = isObject(current.verticalScroll) ? current.verticalScroll : {};
    const forceResult = isObject(force?.forceVerticalScroll) ? force.forceVerticalScroll : {};
    const forceScroll = isObject(forceResult.verticalScroll) ? forceResult.verticalScroll : {};
    return [
      rank,
      domain,
      finalHost(baseline),
      finalHost(current),
      visibleCookies(baseline),
      visibleCookies(current),
      classification.cookieClassification,
      scroll.applicable,
      scroll.before,
      scroll.after,
      scroll.maximum,
      classification.scrollClassification,
      force ? true : false,
      force ? (force.auditAttemptCount || 1) : 0,
      forceScroll.after,
      classification.forceClassification,
      classification.recommendation,
      force?.mainFrameError,
      force?.loadingTimedOut,
      forceResult.enabled,
      forceResult.mainFrameError,
      forceResult.loadingTimedOut,
      baseline.mainFrameError,
      current.mainFrameError,
      baseline.loadingTimedOut,
      current.loadingTimedOut,
      baseline.probe?.challenge,
      current.probe?.challenge,
      baseline.buildId,
      current.buildId,
    ].map(csv).join(",");
  });
  return `${header.join(",")}\n${lines.join("\n")}\n`;
}

function main() {
  const [outputPath, ...inputPaths] = process.argv.slice(2);
  if (!outputPath || inputPaths.length === 0) {
    process.stderr.write(
      "Usage: scripts/summarize_site_default_audit.mjs <output.csv> <input.jsonl>...\n",
    );
    process.exit(2);
  }
  const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
  const fixturePath = path.resolve(
    scriptDirectory,
    "../app/src/androidTest/assets/tranco_PYG5J_top_10000.csv",
  );
  const rows = summarize(readRecords(inputPaths), readFixture(fixturePath));
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, toCsv(rows));
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main();
