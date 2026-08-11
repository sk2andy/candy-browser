#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

export function findMissingRanks(records, expectedDomains, pass, buildId) {
  const found = new Set();
  for (const record of records) {
    const expectedDomain = expectedDomains.get(record.rank);
    if (
      record.schemaVersion !== 2 ||
      record.pass !== pass ||
      record.buildId !== buildId ||
      expectedDomain == null ||
      record.domain !== expectedDomain
    ) {
      throw new Error(`Invalid ${pass} audit record at rank ${record.rank}`);
    }
    if (found.has(record.rank)) {
      throw new Error(`Duplicate ${pass} audit record at rank ${record.rank}`);
    }
    found.add(record.rank);
  }
  return [...expectedDomains.keys()].filter((rank) => !found.has(rank));
}

export function validateFileRanks(name, records, pass) {
  const match = name.match(new RegExp(`^sites-${pass}-(\\d+)-(\\d+)\\.jsonl$`));
  const firstRank = Number(match?.[1]);
  const lastRank = Number(match?.[2]);
  if (
    !match ||
    records.length === 0 ||
    records[0].rank !== firstRank ||
    records.at(-1).rank !== lastRank ||
    records.some((record, index) => index > 0 && record.rank <= records[index - 1].rank)
  ) {
    throw new Error(`Audit filename does not match ordered ranks: ${name}`);
  }
}

function readRecords(root, pass) {
  if (!fs.existsSync(root)) return [];
  return fs.readdirSync(root)
    .filter((name) => name.startsWith(`sites-${pass}-`) && name.endsWith(".jsonl"))
    .sort()
    .flatMap((name) => {
      const records = fs.readFileSync(path.join(root, name), "utf8")
        .split("\n")
        .filter(Boolean)
        .map((line) => JSON.parse(line));
      validateFileRanks(name, records, pass);
      return records;
    });
}

function main() {
  const [pass, appApkPath, batchSizeValue = "25"] = process.argv.slice(2);
  const batchSize = Number(batchSizeValue);
  if (
    !["baseline", "current"].includes(pass) ||
    !appApkPath ||
    !Number.isInteger(batchSize) ||
    batchSize < 1 ||
    batchSize > 100
  ) {
    process.stderr.write(
      "Usage: scripts/list_missing_site_audit_ranks.mjs " +
        "<baseline|current> <app-apk> [batch-size]\n",
    );
    process.exit(2);
  }
  const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
  const fixturePath = path.resolve(
    scriptDirectory,
    "../app/src/androidTest/assets/tranco_PYG5J_top_10000.csv",
  );
  const expectedDomains = new Map(
    fs.readFileSync(fixturePath, "utf8")
      .split("\n")
      .filter((line) => line && !line.startsWith("#"))
      .map((line) => {
        const [rank, domain] = line.split(",");
        return [Number(rank), domain];
      }),
  );
  const buildId = crypto.createHash("sha256")
    .update(fs.readFileSync(appApkPath))
    .digest("hex");
  const root = path.resolve(scriptDirectory, `../build/top-site-audit/${pass}`);
  const missing = findMissingRanks(readRecords(root, pass), expectedDomains, pass, buildId);
  for (let index = 0; index < missing.length; index += batchSize) {
    process.stdout.write(`${missing.slice(index, index + batchSize).join(",")}\n`);
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main();
