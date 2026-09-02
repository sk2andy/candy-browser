import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function read(browser) {
  return JSON.parse(fs.readFileSync(path.join(root, "dist", browser, "manifest.json"), "utf8"));
}

const chromium = read("chromium");
const firefox = read("firefox");
for (const manifest of [chromium, firefox]) {
  assert.equal(manifest.manifest_version, 3);
  assert.equal(manifest.options_ui?.open_in_tab, true);
  assert.equal(manifest.action?.default_popup, undefined);
  assert.deepEqual(manifest.permissions, ["alarms", "storage"]);
  assert.deepEqual(manifest.optional_permissions, ["tabs", "bookmarks", "tabGroups"]);
  assert.equal(JSON.stringify(manifest).includes("Candy Hosted"), false);
  assert.equal(manifest.content_security_policy.extension_pages.includes("'unsafe-eval'"), false);
  assert.equal(manifest.content_security_policy.extension_pages.includes("http:"), false);
}
assert.equal(chromium.background.service_worker, "background.js");
assert.equal(chromium.background.scripts, undefined);
assert.deepEqual(firefox.background.scripts, ["background.js"]);
assert.equal(firefox.background.service_worker, undefined);
assert.equal(firefox.browser_specific_settings.gecko.strict_min_version, "140.0");
assert.ok(firefox.browser_specific_settings.gecko.data_collection_permissions);
process.stdout.write("Chromium and Firefox manifests valid.\n");
