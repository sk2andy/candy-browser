import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const context = vm.createContext({
  URL,
  Set,
  Map,
  Uint8Array,
  TextDecoder,
  atob,
});
const source = fs.readFileSync(
  new URL("../app/src/main/assets/candy_privacy/rules.js", import.meta.url),
  "utf8",
);
vm.runInContext(source, context);
const rules = context.CandyPrivacyRules;
const encode = (value) => Buffer.from(value, "utf8").toString("base64url");

test("advanced request rules preserve anchors, separators, party, and allow precedence", () => {
  const asset = [
    "candy-advanced-filter:2",
    "# Rules: 3",
    `B\tN\ttracker.example\t${encode("|/ads/*^")}\tnews.example\t-\t3\t-`,
    `A\tN\ttracker.example\t${encode("|/ads/allowed.js|")}\tnews.example\t-\t3\t-`,
    `B\tP\t*\t${encode("*")}\t-\t-\t*\t-`,
  ].join("\n");
  const parsed = rules.parseAdvanced(asset);

  assert.equal(parsed.length, 2);
  assert.equal(
    rules.advancedDecision(parsed, "https://tracker.example/ads/file.js?x=1", "news.example"),
    "B",
  );
  assert.equal(
    rules.advancedDecision(parsed, "https://tracker.example/ads/allowed.js", "news.example"),
    "A",
  );
  assert.equal(rules.advancedDecision(parsed, "https://tracker.example/ads/file.js", null), null);
  assert.equal(rules.urlPatternMatches("/ads/a", "|/ads/^^"), false);
});

test("cosmetic payload is host scoped, respects pause, and includes dynamic and procedural rules", () => {
  const cosmeticAsset = [
    "candy-test-cosmetic:2",
    "# Hide rules: 2",
    "# Exception rules: 1",
    "# Generic hide exceptions: 0",
    `A\tnews.example\t-\t${encode(".allowed")}`,
    `H\tnews.example\t-\t${encode(".allowed")}`,
    `H\tnews.example\t-\t${encode(".scoped-ad")}`,
  ].join("\n");
  const proceduralAsset = [
    "candy-procedural-cosmetic:1",
    "# Rules: 1",
    `H\tnews.example\t${encode(".sponsor")}\t${encode("Sponsored")}\ti`,
  ].join("\n");
  const staticRules = {
    cosmetics: rules.parseCosmetic(cosmeticAsset, "candy-test-cosmetic:2"),
    procedural: rules.parseProcedural(proceduralAsset),
    candyDefaults: [],
  };
  const policy = {
    pageHost: "news.example",
    blockAds: true,
    hideConsent: false,
    cookieBannerRemovalDisabled: true,
    pausedHosts: [],
    cosmetics: [{ id: "dynamic", h: "news.example", s: ".personal-ad" }],
  };

  const payload = rules.cosmeticPayload(staticRules, policy, "cdn.news.example");
  assert.ok(payload.selectors.includes(".scoped-ad"));
  assert.ok(payload.selectors.includes(".personal-ad"));
  assert.ok(!payload.selectors.includes(".allowed"));
  assert.equal(payload.procedural.length, 1);
  const paused = rules.cosmeticPayload(
    staticRules,
    { ...policy, pausedHosts: ["news.example"] },
    "news.example",
  );
  assert.equal(paused.selectors.length, 0);
  assert.equal(paused.procedural.length, 0);
});

test("public suffix wildcard matching mirrors Candy host rules", () => {
  assert.equal(rules.hostPatternMatches("sub.direct-cloud.co.uk", "direct-cloud.*"), true);
  assert.equal(rules.hostPatternMatches("direct-cloud.invalid.example", "direct-cloud.*"), false);
  assert.equal(rules.registrableDomain("cdn.news.co.uk"), "news.co.uk");
});

test("Reddit consent policy exposes only the observed dialog selector", () => {
  const candyDefaults = rules.parseCandyDefaults(fs.readFileSync(
    new URL("../app/src/main/assets/candy_default_rules.txt", import.meta.url),
    "utf8",
  ));
  const payload = rules.cosmeticPayload(
    { cosmetics: [], procedural: [], candyDefaults },
    {
      pageHost: "www.reddit.com",
      blockAds: false,
      hideConsent: true,
      cookieBannerRemovalDisabled: false,
      pausedHosts: [],
      cosmetics: [],
    },
    "www.reddit.com",
  );

  assert.deepEqual(Array.from(payload.selectors), ["#data-protection-consent-dialog"]);
});

test("all generated Gecko rule assets parse before the privacy host becomes ready", () => {
  const readAsset = (name) => fs.readFileSync(
    new URL(`../app/src/main/assets/${name}`, import.meta.url),
    "utf8",
  );
  assert.equal(rules.parseAdvanced(readAsset("uassets_advanced_filters.txt")).length, 747);
  assert.ok(rules.parseCosmetic(
    readAsset("easylist_cosmetic_rules.txt"),
    "candy-easylist-cosmetic:2",
  ).length > 30_000);
  assert.ok(rules.parseCosmetic(
    readAsset("uassets_cosmetic_rules.txt"),
    "candy-uassets-cosmetic:2",
  ).length > 10_000);
  assert.equal(rules.parseProcedural(readAsset("uassets_procedural_cosmetic_rules.txt")).length, 232);
  assert.equal(rules.parseCandyDefaults(readAsset("candy_default_rules.txt")).length, 49);
});
