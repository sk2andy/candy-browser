import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.join(root, "dist");

function build() {
  const result = spawnSync(process.execPath, [path.join(root, "scripts", "build.mjs")], {
    cwd: root,
    encoding: "utf8",
  });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout || "Build failed");
}

function files(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? files(target) : [target];
  });
}

function snapshot() {
  return Object.fromEntries(files(output).sort().map((file) => [
    path.relative(output, file),
    crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex"),
  ]));
}

build();
const first = snapshot();
build();
assert.deepEqual(snapshot(), first);
process.stdout.write(`${Object.keys(first).length} build artifacts are reproducible.\n`);
