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

test("Candy cookie defaults parse before the Gecko privacy host becomes ready", () => {
  const readAsset = (name) => fs.readFileSync(
    new URL(`../app/src/main/assets/${name}`, import.meta.url),
    "utf8",
  );
  assert.equal(rules.parseCandyDefaults(readAsset("candy_default_rules.txt")).length, 49);
});

test("safe-area policy push has a bounded content-side race fallback", () => {
  const bridge = fs.readFileSync(
    new URL("../app/src/main/assets/candy_privacy/content_top_inset_bridge.js", import.meta.url),
    "utf8",
  );
  const background = fs.readFileSync(
    new URL("../app/src/main/assets/candy_privacy/background.js", import.meta.url),
    "utf8",
  );

  assert.match(background, /contentPolicyRetryDelaysMillis/);
  assert.match(background, /scheduleContentPolicy\(details\.tabId\)/);
  assert.match(background, /const currentPolicy = token && policiesByToken\.get\(token\)/);
  assert.match(background, /latestPolicyRevisionByToken/);
  assert.match(background, /message\.revision < latestRevision/);
  assert.match(bridge, /policyRetryDelaysMillis/);
  assert.match(bridge, /if \(policyReady\) return/);
  assert.match(bridge, /policyRetryIndex >= policyRetryDelaysMillis\.length/);
  assert.equal((bridge.match(/policyRetryIndex = 0;/g) || []).length, 1);
  assert.match(background, /ready: Boolean\(policy\)/);
  assert.match(background, /revision: Number\.isSafeInteger\(policy\?\.revision\)/);
  assert.match(bridge, /revision < state\.revision/);
  assert.match(background, /safeAreaLayoutQuietPeriodMillis/);
  assert.match(background, /safeAreaRequiredFailureCount/);
  assert.match(bridge, /safeAreaLayoutQuietPeriodMillis/);
  assert.match(bridge, /safeAreaRequiredFailureCount/);
  assert.match(bridge, /revision !== state\.revision/);
  assert.match(bridge, /revision,/);
  assert.match(background, /policy\.revision === message\.revision/);
  assert.doesNotMatch(bridge, /console\.(?:log|warn|error)/);
});

test("privacy host uses required MV2 web origins for document-start scripts", () => {
  const manifest = JSON.parse(fs.readFileSync(
    new URL("../app/src/main/assets/candy_privacy/manifest.json", import.meta.url),
    "utf8",
  ));

  assert.equal(manifest.manifest_version, 2);
  assert.ok(manifest.permissions.includes("<all_urls>"));
  assert.equal(manifest.host_permissions, undefined);
  assert.equal(manifest.content_scripts[0].all_frames, false);
  assert.deepEqual(
    manifest.content_scripts[0].js,
    ["content_top_inset_bridge.js", "content_top_inset.js"],
  );
  assert.equal(manifest.content_scripts[1].all_frames, true);
});

test("newer privacy policy wins while older cookie rules are still loading", async () => {
  let resolveCookieAsset;
  let nativeMessageListener;
  const runtimeMessageListeners = [];
  const postedNativeMessages = [];
  const backgroundContext = vm.createContext({
    URL,
    Map,
    Set,
    Array,
    Promise,
    Number,
    String,
    Boolean,
    setTimeout,
    clearTimeout,
    CandyPrivacyRules: {
      parseCandyDefaults: () => [],
      hostMatches: () => false,
      cosmeticPayload: () => ({ selectors: [], procedural: [] }),
    },
    fetch: () => new Promise((resolve) => { resolveCookieAsset = resolve; }),
    browser: {
      runtime: {
        getURL: (path) => path,
        connectNative: () => ({
          postMessage: (message) => postedNativeMessages.push(message),
          onMessage: { addListener: (listener) => { nativeMessageListener = listener; } },
          onDisconnect: { addListener: () => {} },
        }),
        onMessage: { addListener: (listener) => runtimeMessageListeners.push(listener) },
      },
      tabs: {
        sendMessage: () => Promise.resolve(),
        onRemoved: { addListener: () => {} },
      },
      webRequest: { onBeforeRequest: { addListener: () => {} } },
    },
  });
  vm.runInContext(
    fs.readFileSync(
      new URL("../app/src/main/assets/candy_privacy/background.js", import.meta.url),
      "utf8",
    ),
    backgroundContext,
  );

  nativeMessageListener({
    type: "policy",
    protocolVersion: 2,
    token: "tab-token",
    revision: 1,
    navigationGeneration: 0,
    hideConsent: true,
  });
  nativeMessageListener({
    type: "policy",
    protocolVersion: 2,
    token: "tab-token",
    revision: 2,
    navigationGeneration: 0,
    hideConsent: false,
    safeAreaLayoutQuietPeriodMillis: 5000,
    safeAreaRequiredFailureCount: 0,
  });
  resolveCookieAsset({ ok: true, text: () => Promise.resolve("") });
  await new Promise((resolve) => setImmediate(resolve));

  const sendRuntimeMessage = async (message, sender) => {
    for (const listener of runtimeMessageListeners) {
      const result = listener(message, sender);
      if (result !== undefined) return result;
    }
    return undefined;
  };
  await sendRuntimeMessage(
    { type: "bind", token: "tab-token" },
    { tab: { id: 7 } },
  );
  const policy = await sendRuntimeMessage(
    { type: "content-policy-request" },
    { tab: { id: 7 } },
  );

  assert.equal(policy.revision, 2);
  assert.equal(policy.safeAreaLayoutQuietPeriodMillis, 800);
  assert.equal(policy.safeAreaRequiredFailureCount, 2);
  const fallbackCount = postedNativeMessages.filter(
    (message) => message.type === "safe-area-fallback",
  ).length;
  await sendRuntimeMessage(
    { type: "safe-area-fallback", navigationGeneration: 0, revision: 1 },
    { tab: { id: 7 } },
  );
  assert.equal(
    postedNativeMessages.filter((message) => message.type === "safe-area-fallback").length,
    fallbackCount,
  );
  await sendRuntimeMessage(
    { type: "safe-area-fallback", navigationGeneration: 0, revision: 2 },
    { tab: { id: 7 } },
  );
  assert.equal(
    postedNativeMessages.filter((message) => message.type === "safe-area-fallback").at(-1).revision,
    2,
  );
  assert.equal(
    postedNativeMessages.filter((message) => message.type === "policy-ready").at(-1).revision,
    1,
  );
});
