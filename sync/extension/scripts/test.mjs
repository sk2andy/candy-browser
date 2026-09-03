import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { build } from "esbuild";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.join(root, ".test-build");
fs.rmSync(output, { recursive: true, force: true });
fs.mkdirSync(output, { recursive: true });

const entries = fs.readdirSync(path.join(root, "tests"))
  .filter((name) => name.endsWith(".test.ts"))
  .map((name) => path.join(root, "tests", name));

await build({
  entryPoints: entries,
  outdir: output,
  bundle: true,
  format: "esm",
  platform: "node",
  target: "node20",
  packages: "external",
  sourcemap: "inline",
});

const { spawnSync } = await import("node:child_process");
const compiledTests = fs.readdirSync(output)
  .filter((name) => name.endsWith(".test.js"))
  .sort()
  .map((name) => path.join(output, name));
const result = spawnSync(process.execPath, ["--test", ...compiledTests], { stdio: "inherit" });
process.exitCode = result.status ?? 1;
