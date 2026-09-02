import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { build } from "esbuild";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const requested = process.argv[2];
const browsers = requested ? [requested] : ["chromium", "firefox"];

if (browsers.some((browser) => !["chromium", "firefox"].includes(browser))) {
  throw new Error(`Unsupported browser: ${requested}`);
}

function merge(left, right) {
  const result = { ...left };
  for (const [key, value] of Object.entries(right)) {
    result[key] = value && typeof value === "object" && !Array.isArray(value)
      ? merge(left[key] ?? {}, value)
      : value;
  }
  return result;
}

function copy(source, target) {
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(source, target);
}

for (const browser of browsers) {
  const output = path.join(root, "dist", browser);
  fs.rmSync(output, { recursive: true, force: true });
  fs.mkdirSync(path.join(output, "options"), { recursive: true });

  await Promise.all([
    build({
      entryPoints: [path.join(root, `src/entrypoints/background.${browser}.ts`)],
      outfile: path.join(output, "background.js"),
      bundle: true,
      format: "esm",
      platform: "browser",
      target: browser === "chromium" ? "chrome121" : "firefox139",
      sourcemap: true,
      legalComments: "none",
    }),
    build({
      entryPoints: [path.join(root, "src/entrypoints/options.ts")],
      outfile: path.join(output, "options", "options.js"),
      bundle: true,
      format: "esm",
      platform: "browser",
      target: browser === "chromium" ? "chrome121" : "firefox139",
      sourcemap: true,
      legalComments: "none",
    }),
  ]);

  const base = JSON.parse(fs.readFileSync(path.join(root, "manifests", "base.json"), "utf8"));
  const overlay = JSON.parse(fs.readFileSync(path.join(root, "manifests", `${browser}.json`), "utf8"));
  fs.writeFileSync(path.join(output, "manifest.json"), `${JSON.stringify(merge(base, overlay), null, 2)}\n`);
  copy(path.join(root, "src", "options", "index.html"), path.join(output, "options", "index.html"));
  copy(path.join(root, "src", "options", "options.css"), path.join(output, "options", "options.css"));
  copy(path.resolve(root, "../protocol/device-icons-v1.json"), path.join(output, "device-icons-v1.json"));
}
