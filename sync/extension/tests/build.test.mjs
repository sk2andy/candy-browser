import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

for (const browser of ["chromium", "firefox"]) {
  test(`${browser} build is complete and has no popup`, () => {
    const output = path.join(root, "dist", browser);
    const manifest = JSON.parse(fs.readFileSync(path.join(output, "manifest.json"), "utf8"));
    assert.equal(manifest.manifest_version, 3);
    assert.equal(manifest.options_ui.open_in_tab, true);
    assert.equal(manifest.action?.default_popup, undefined);
    assert.equal(JSON.stringify(manifest).includes("Candy Hosted"), false);
    for (const file of ["background.js", "device-icons-v1.json", "options/index.html", "options/options.js", "options/options.css"]) {
      assert.equal(fs.existsSync(path.join(output, file)), true, `Missing ${file}`);
    }
  });
}

test("browser manifests use their supported background environment", () => {
  const chromium = JSON.parse(fs.readFileSync(path.join(root, "dist/chromium/manifest.json"), "utf8"));
  const firefox = JSON.parse(fs.readFileSync(path.join(root, "dist/firefox/manifest.json"), "utf8"));
  assert.equal(chromium.background.service_worker, "background.js");
  assert.deepEqual(firefox.background.scripts, ["background.js"]);
  assert.equal(firefox.browser_specific_settings.gecko.strict_min_version, "140.0");
});
