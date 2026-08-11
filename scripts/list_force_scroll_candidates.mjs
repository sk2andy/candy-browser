#!/usr/bin/env node

import fs from "node:fs";

import { parseCsv } from "./generate_site_privacy_defaults.mjs";

const [inputPath, batchSizeValue = "25"] = process.argv.slice(2);
const batchSize = Number(batchSizeValue);
if (!inputPath || !Number.isInteger(batchSize) || batchSize < 1 || batchSize > 100) {
  process.stderr.write(
    "Usage: scripts/list_force_scroll_candidates.mjs <audit.csv> [batch-size]\n",
  );
  process.exit(2);
}

const [header, ...rows] = parseCsv(fs.readFileSync(inputPath, "utf8"));
const column = Object.fromEntries(header.map((name, index) => [name, index]));
for (const required of ["rank", "scroll_classification", "force_classification"]) {
  if (column[required] == null) throw new Error(`Missing CSV column: ${required}`);
}
const ranks = rows
  .filter((row) =>
    row[column.scroll_classification] === "locked" &&
    ["pending", "indeterminate"].includes(row[column.force_classification]),
  )
  .map((row) => Number(row[column.rank]));
if (ranks.some((rank) => !Number.isInteger(rank) || rank < 1 || rank > 10_000)) {
  throw new Error("Invalid force-scroll candidate rank");
}
for (let index = 0; index < ranks.length; index += batchSize) {
  process.stdout.write(`${ranks.slice(index, index + batchSize).join(",")}\n`);
}
