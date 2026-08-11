#!/usr/bin/env node

import fs from "node:fs";
import { pathToFileURL } from "node:url";

const HEADER = "# candy site privacy defaults v2";
const HOST_PATTERN = /^(?=.{1,253}$)(?!-)(?:[a-z0-9-]{1,63}\.)*[a-z0-9-]{1,63}$/;

export function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else if (character === '"') {
        quoted = false;
      } else {
        field += character;
      }
    } else if (character === '"' && field === "") {
      quoted = true;
    } else if (character === ",") {
      row.push(field);
      field = "";
    } else if (character === "\n") {
      row.push(field.replace(/\r$/, ""));
      if (row.some((value) => value !== "")) rows.push(row);
      row = [];
      field = "";
    } else {
      field += character;
    }
  }
  if (quoted) throw new Error("Unterminated quoted CSV field");
  if (field !== "" || row.length > 0) {
    row.push(field.replace(/\r$/, ""));
    rows.push(row);
  }
  return rows;
}

export function generateDefaults(csvText, expectedRanks = 10_000) {
  const [header, ...values] = parseCsv(csvText);
  if (!header) throw new Error("CSV is empty");
  const column = Object.fromEntries(header.map((name, index) => [name, index]));
  for (const required of [
    "rank",
    "current_final_host",
    "cookie_classification",
    "scroll_classification",
    "force_classification",
    "recommendation",
  ]) {
    if (column[required] == null) throw new Error(`Missing CSV column: ${required}`);
  }
  if (values.length !== expectedRanks) {
    throw new Error(`Expected ${expectedRanks} audit rows, got ${values.length}`);
  }
  const hostRules = new Map();
  values.forEach((row, index) => {
    const rank = Number(row[column.rank]);
    if (rank !== index + 1) throw new Error(`Expected rank ${index + 1}, got ${row[column.rank]}`);
    const scrollClassification = row[column.scroll_classification];
    const forceClassification = row[column.force_classification];
    const recommendation = row[column.recommendation];
    const cookieClassification = row[column.cookie_classification];
    if (![
      "hidden", "still_visible", "not_present_in_baseline", "indeterminate",
    ].includes(cookieClassification)) {
      throw new Error(`Invalid cookie classification at rank ${rank}: ${cookieClassification}`);
    }
    if (![
      "not_tested", "scrollable", "not_applicable", "locked", "indeterminate",
    ].includes(scrollClassification)) {
      throw new Error(`Invalid scroll classification at rank ${rank}: ${scrollClassification}`);
    }
    if (![
      "not_tested", "pending", "indeterminate", "fixed", "not_fixed", "not_reproduced",
      "unavailable_after_retries",
    ].includes(forceClassification)) {
      throw new Error(`Invalid force classification at rank ${rank}: ${forceClassification}`);
    }
    if (!["none", "force_vertical_scroll", "show_cookie_consent"].includes(recommendation)) {
      throw new Error(`Invalid recommendation at rank ${rank}: ${recommendation}`);
    }
    if (
      scrollClassification === "locked" &&
      ![
        "fixed", "not_fixed", "not_reproduced", "unavailable_after_retries",
      ].includes(forceClassification)
    ) {
      throw new Error(
        `Locked rank ${rank} needs a conclusive force-scroll result`,
      );
    }
    const expectedRecommendation = scrollClassification === "locked" && forceClassification === "fixed"
      ? "force_vertical_scroll"
      : cookieClassification === "hidden" &&
        scrollClassification === "locked" &&
        ["not_fixed", "unavailable_after_retries"].includes(forceClassification)
        ? "show_cookie_consent"
        : "none";
    if (recommendation !== expectedRecommendation) {
      throw new Error(`Inconsistent recommendation at rank ${rank}`);
    }
    if (recommendation === "none") return;
    const host = row[column.current_final_host].trim().toLowerCase();
    if (!HOST_PATTERN.test(host) || host.split(".").some((label) => label.endsWith("-"))) {
      throw new Error(`Invalid recommended host at rank ${rank}: ${host}`);
    }
    const preset = recommendation === "force_vertical_scroll"
      ? { rule: "force_vertical_scroll", priority: 2 }
      : {
          rule: "cookie_banner_removal_disabled",
          priority: forceClassification === "not_fixed" ? 3 : 1,
        };
    const currentPreset = hostRules.get(host);
    if (!currentPreset || preset.priority > currentPreset.priority) hostRules.set(host, preset);
  });
  const rules = [...hostRules]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([host, { rule }]) => `${rule}\t${host}`);
  return `${HEADER}\n${rules.join("\n")}${rules.length ? "\n" : ""}`;
}

function main() {
  const [inputPath, outputPath] = process.argv.slice(2);
  if (!inputPath || !outputPath) {
    process.stderr.write(
      "Usage: scripts/generate_site_privacy_defaults.mjs <audit.csv> <output.txt>\n",
    );
    process.exit(2);
  }
  fs.writeFileSync(outputPath, generateDefaults(fs.readFileSync(inputPath, "utf8")));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
