#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

export function validateBatch(records, expected, fixtureDomains) {
  if (records.length !== expected.ranks.length) {
    throw new Error(`Expected ${expected.ranks.length} records, got ${records.length}`);
  }
  records.forEach((record, index) => {
    const rank = expected.ranks[index];
    if (record.schemaVersion !== 2) throw new Error(`Rank ${rank}: unsupported schema`);
    if (record.rank !== rank) throw new Error(`Expected rank ${rank}, got ${record.rank}`);
    if (record.pass !== expected.pass) throw new Error(`Rank ${rank}: wrong pass`);
    if (record.buildId !== expected.buildId) throw new Error(`Rank ${rank}: wrong build ID`);
    if (record.domain !== fixtureDomains.get(rank)) throw new Error(`Rank ${rank}: wrong domain`);
  });
}

function main() {
  const [inputPath, pass, buildId, ranksValue] = process.argv.slice(2);
  if (!inputPath || !pass || !buildId || !ranksValue) {
    process.stderr.write(
      "Usage: scripts/validate_site_audit_batch.mjs <input.jsonl> <pass> <build-id> <ranks>\n",
    );
    process.exit(2);
  }
  const ranks = ranksValue.split(",").map(Number);
  if (ranks.some((rank) => !Number.isInteger(rank) || rank < 1 || rank > 10_000)) {
    throw new Error("Invalid expected ranks");
  }
  const records = fs.readFileSync(inputPath, "utf8")
    .split("\n")
    .filter(Boolean)
    .map((line) => JSON.parse(line));
  const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
  const fixturePath = path.resolve(
    scriptDirectory,
    "../app/src/androidTest/assets/tranco_PYG5J_top_10000.csv",
  );
  const fixtureDomains = new Map(
    fs.readFileSync(fixturePath, "utf8")
      .split("\n")
      .filter((line) => line && !line.startsWith("#"))
      .map((line) => {
        const [rank, domain] = line.split(",");
        return [Number(rank), domain];
      }),
  );
  validateBatch(records, { pass, buildId, ranks }, fixtureDomains);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main();
