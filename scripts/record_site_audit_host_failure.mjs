#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const [pass, rankValue, appApkPath, reason, durationValue] = process.argv.slice(2);
const rank = Number(rankValue);
const durationMillis = Number(durationValue);
if (!pass || !Number.isInteger(rank) || rank < 1 || rank > 10_000 || !appApkPath || !reason) {
  throw new Error(
    "Usage: scripts/record_site_audit_host_failure.mjs <pass> <rank> <app-apk> <reason> [duration-ms]",
  );
}
if (!Number.isFinite(durationMillis) || durationMillis < 0) {
  throw new Error("duration-ms must be a non-negative number");
}

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
      const [fixtureRank, domain] = line.split(",");
      return [Number(fixtureRank), domain];
    }),
);
const domain = fixtureDomains.get(rank);
if (!domain) throw new Error(`Fixture missing rank ${rank}`);

const buildId = crypto.createHash("sha256")
  .update(fs.readFileSync(appApkPath))
  .digest("hex");
const outputDirectory = path.resolve(scriptDirectory, `../build/top-site-audit/${pass}`);
const outputPath = path.join(outputDirectory, `sites-${pass}-${rank}-${rank}.jsonl`);
if (fs.existsSync(outputPath)) throw new Error(`Refusing to overwrite ${outputPath}`);
fs.mkdirSync(outputDirectory, { recursive: true });

const requestedUrl = `https://${domain}/`;
const record = {
  schemaVersion: 2,
  rank,
  domain,
  pass,
  buildId,
  requestedUrl,
  finalUrl: requestedUrl,
  title: "",
  durationMillis,
  loadingTimedOut: true,
  mainFrameError: `host-timeout:${reason}`,
  blockedTotal: 0,
  blockedDomains: [],
  probe: null,
  verticalScroll: null,
  safeAreaLayout: null,
  screenshot: null,
};
fs.writeFileSync(outputPath, `${JSON.stringify(record)}\n`, { flag: "wx" });
process.stdout.write(`${outputPath}\n`);
