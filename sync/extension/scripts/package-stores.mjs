import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const result = spawnSync("python3", [path.join(root, "scripts/package-stores.py")], { stdio: "inherit" });
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
