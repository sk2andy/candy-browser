import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const android = fs.readFileSync(new URL(
  "../app/src/main/java/dev/sk2andy/materialbrowser/browser/userscript/UserScriptApi.kt", import.meta.url,
), "utf8");
const ios = fs.readFileSync(new URL("../iosApp/CandyIos/ToppingInstaller.swift", import.meta.url), "utf8");
const androidFunction = android.match(/"GM_openInTab",\s*"""([\s\S]*?)"""\.trimIndent\(\)/)[1];
const iosFunction = ios.match(/define\("GM_openInTab", expression: """([\s\S]*?)"""/)[1];

for (const [engine, source] of [["Android", androidFunction], ["iOS", iosFunction]]) {
  for (const [name, option, active] of [
    ["boolean background", true, false],
    ["boolean foreground", false, true],
    ["object foreground", { active: true }, true],
    ["object background", { active: false }, false],
    ["default background", undefined, false],
  ]) {
    test(`${engine}: ${name}`, () => {
      const emitted = [];
      const openInTab = vm.runInNewContext(`(${source})`, {
        URL,
        location: { href: "https://example.com/" },
        __candyBridge: { postMessage: raw => emitted.push(JSON.parse(raw)) },
        __candyPost: message => emitted.push(message),
      });
      openInTab("/new", option);
      assert.equal(emitted[0].active, active);
      assert.equal(emitted[0].url, "https://example.com/new");
    });
  }
}
