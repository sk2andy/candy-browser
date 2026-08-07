#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const [outputPath, ...inputPaths] = process.argv.slice(2);
if (!outputPath || inputPaths.length === 0) {
  process.stderr.write(
    "Usage: scripts/summarize_top_site_audit.mjs <output.csv> <input.jsonl>...\n",
  );
  process.exit(2);
}

const records = inputPaths.flatMap((inputPath) =>
  fs
    .readFileSync(inputPath, "utf8")
    .split("\n")
    .filter(Boolean)
    .map((line) => JSON.parse(line)),
);
records.sort((left, right) => left.rank - right.rank);

const ranks = records.map((record) => record.rank);
if (
  records.length !== 300 ||
  new Set(ranks).size !== 300 ||
  ranks.some((rank, index) => rank !== index + 1)
) {
  throw new Error("Expected exactly the contiguous ranks 1-300");
}

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const fixturePath = path.resolve(
  scriptDirectory,
  "../app/src/androidTest/assets/tranco_PYG5J_top_1000.csv",
);
const fixtureDomains = new Map(
  fs
    .readFileSync(fixturePath, "utf8")
    .split("\n")
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => {
      const [rank, domain] = line.split(",");
      return [Number(rank), domain];
    }),
);
for (const record of records) {
  if (fixtureDomains.get(record.rank) !== record.domain) {
    throw new Error(
      `Rank ${record.rank} domain mismatch: expected ${fixtureDomains.get(record.rank)}, got ${record.domain}`,
    );
  }
}

const passes = new Set(records.map((record) => String(record.pass || "").trim()));
const buildIds = new Set(records.map((record) => String(record.buildId || "").trim()));
if (passes.size !== 1 || passes.has("")) {
  throw new Error("Expected exactly one non-empty audit pass");
}
if (buildIds.size !== 1 || buildIds.has("")) {
  throw new Error("Expected exactly one non-empty buildId");
}

const csv = (value) => {
  const text = value == null ? "" : String(value);
  return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
};
const header = [
  "rank",
  "domain",
  "pass",
  "build_id",
  "final_url",
  "main_frame_error",
  "timed_out",
  "html",
  "text_length",
  "challenge",
  "scroll_locked",
  "visible_cookie_nodes",
  "visible_ad_nodes",
  "blocked_requests",
];
const lines = records.map((record) => {
  const probe = record.probe && typeof record.probe === "object" ? record.probe : {};
  return [
    record.rank,
    record.domain,
    record.pass,
    record.buildId,
    record.finalUrl,
    record.mainFrameError,
    record.loadingTimedOut,
    probe.html,
    probe.textLength,
    probe.challenge,
    probe.scrollLocked,
    probe.cookie?.visibleCount,
    probe.ads?.visibleCount,
    record.blockedTotal,
  ]
    .map(csv)
    .join(",");
});

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${header.join(",")}\n${lines.join("\n")}\n`);
