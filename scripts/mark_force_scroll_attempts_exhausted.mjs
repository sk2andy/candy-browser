#!/usr/bin/env node

import fs from "node:fs";

const [outputPath, rankValue, ...inputPaths] = process.argv.slice(2);
const rank = Number(rankValue);
if (!outputPath || !Number.isInteger(rank) || rank < 1 || rank > 10_000 || inputPaths.length < 2) {
  throw new Error(
    "Usage: scripts/mark_force_scroll_attempts_exhausted.mjs " +
      "<output.jsonl> <rank> <attempt.jsonl> <attempt.jsonl>...",
  );
}
if (new Set(inputPaths).size !== inputPaths.length) {
  throw new Error("Attempt paths must be unique");
}

const attempts = inputPaths.map((inputPath) => {
  const matches = fs.readFileSync(inputPath, "utf8")
    .split("\n")
    .filter(Boolean)
    .map((line) => JSON.parse(line))
    .filter((record) => record.rank === rank);
  if (matches.length !== 1) {
    throw new Error(`${inputPath} must contain exactly one record for rank ${rank}`);
  }
  return matches[0];
});
const [first] = attempts;
for (const attempt of attempts) {
  if (
    attempt.schemaVersion !== 2 || attempt.pass !== "force-scroll" ||
    attempt.domain !== first.domain || attempt.buildId !== first.buildId
  ) {
    throw new Error(`Force-scroll attempt mismatch at rank ${rank}`);
  }
}

const terminalRecord = {
  ...attempts.at(-1),
  auditAttemptsExhausted: true,
  auditAttemptCount: attempts.length,
};
fs.writeFileSync(outputPath, `${JSON.stringify(terminalRecord)}\n`, { flag: "wx" });
