import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function packageStores() {
  const result = spawnSync(process.execPath, ["scripts/package-stores.mjs"], { cwd: root, encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  return Object.fromEntries(fs.readdirSync(path.join(root, "release")).map((name) => [
    name, fs.readFileSync(path.join(root, "release", name)),
  ]));
}

test("store ZIPs are deterministic and reviewer sources exclude local state", () => {
  const first = packageStores();
  assert.deepEqual(packageStores(), first);
  const result = spawnSync("python3", ["-c", `
import json
from pathlib import Path
from zipfile import ZipFile
root = Path.cwd()
version = json.loads((root / 'package.json').read_text())['version']
for browser in ('chromium', 'firefox'):
    with ZipFile(root / 'release' / f'candy-sync-{browser}-{version}.zip') as archive:
        assert json.loads(archive.read('manifest.json'))['version'] == version
        assert 'LICENSE' in archive.namelist()
        assert 'licenses/noble-hashes.txt' in archive.namelist()
with ZipFile(root / 'release' / f'candy-sync-source-{version}.zip') as archive:
    names = archive.namelist()
    assert 'BUILD.md' in names
    assert 'sync/extension/package-lock.json' in names
    assert 'sync/protocol/device-icons-v1.json' in names
    assert not any(part in name.split('/') for name in names for part in
        ('node_modules', 'dist', '.test-build', 'release', '.git', '__pycache__'))
    assert not any(name.endswith(('.env', '.pem', '.key', '.pyc')) for name in names)
`], { cwd: root, encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
});

test("packaging rejects a build with a stale version", () => {
  const manifestPath = path.join(root, "dist/firefox/manifest.json");
  const original = fs.readFileSync(manifestPath);
  try {
    const manifest = JSON.parse(original);
    manifest.version = "999.0.0";
    fs.writeFileSync(manifestPath, JSON.stringify(manifest));
    const result = spawnSync(process.execPath, ["scripts/package-stores.mjs"], { cwd: root, encoding: "utf8" });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Stale firefox build/u);
  } finally {
    fs.writeFileSync(manifestPath, original);
  }
});
