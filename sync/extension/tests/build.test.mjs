import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function pngDimensions(file) {
  const bytes = fs.readFileSync(file);
  assert.equal(bytes.subarray(1, 4).toString("ascii"), "PNG");
  return [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

test("extension icons stay tied to the reviewed Candy app artwork", () => {
  const source = path.resolve(root, "../../app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png");
  assert.equal(sha256(source), "7fd347d9eb5bb0df158f68f3382a412143144de3688f949608f69fc91a1cb8ad");
  assert.deepEqual(
    Object.fromEntries([16, 32, 48, 96, 128].map((size) => [size, sha256(path.join(root, "assets/icons", `icon-${size}.png`))])),
    {
      16: "39a155b8e63026bc59ed8d949a881e0fd8d65d0366a815b77cae0ce376b153bc",
      32: "c9d81330ed7ce06324b13ab750cc9e4a0ba3fe8d3710e64086c0e061cd4c5bdf",
      48: "95051801389cbdc76d2e1aba68629e1345fb2eec7fb5401bb112883d67db305c",
      96: "f787502a73d7b46c53d2330128af86b34e746cf289cdeef530b812626ff36091",
      128: "15d3652d7aef2d50dcf6e07c3ea1aff7e3907523181e28a708758fe101ec6c6f",
    },
  );
});

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
    for (const size of [16, 32, 48, 96, 128]) {
      const icon = path.join(output, "icons", `icon-${size}.png`);
      assert.equal(fs.existsSync(icon), true, `Missing ${icon}`);
      assert.deepEqual(pngDimensions(icon), [size, size]);
      assert.equal(manifest.icons[String(size)], `icons/icon-${size}.png`);
    }
  });
}

test("browser manifests use their supported background environment", () => {
  const chromium = JSON.parse(fs.readFileSync(path.join(root, "dist/chromium/manifest.json"), "utf8"));
  const firefox = JSON.parse(fs.readFileSync(path.join(root, "dist/firefox/manifest.json"), "utf8"));
  assert.equal(chromium.background.service_worker, "background.js");
  assert.deepEqual(firefox.background.scripts, ["background.js"]);
  assert.equal(firefox.browser_specific_settings.gecko.strict_min_version, "140.0");
  const expectedHosts = ["https://*/*", "http://localhost/*", "http://127.0.0.1/*", "http://[::1]/*"];
  assert.deepEqual(chromium.optional_host_permissions, expectedHosts);
  assert.deepEqual(firefox.optional_host_permissions, expectedHosts);
  assert.deepEqual(chromium.optional_permissions, ["tabs", "tabGroups"]);
  assert.deepEqual(firefox.optional_permissions, ["tabs", "tabGroups"]);
  assert.deepEqual(firefox.browser_specific_settings.gecko.data_collection_permissions.optional, ["browsingActivity"]);

  const chromiumOptions = fs.readFileSync(path.join(root, "dist/chromium/options/options.js"), "utf8");
  const firefoxOptions = fs.readFileSync(path.join(root, "dist/firefox/options/options.js"), "utf8");
  assert.match(chromiumOptions, /var IS_FIREFOX_BUILD = false;/u);
  assert.match(firefoxOptions, /var IS_FIREFOX_BUILD = true;/u);
  assert.doesNotMatch(chromiumOptions, /__CANDY_SYNC_FIREFOX__/u);
  assert.doesNotMatch(firefoxOptions, /__CANDY_SYNC_FIREFOX__/u);
});
