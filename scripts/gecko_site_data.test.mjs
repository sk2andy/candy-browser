import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const asset = (name) => fs.readFileSync(
  new URL(`../app/src/gecko/assets/candy_site_data/${name}`, import.meta.url), "utf8",
);

function harness(failedFlags = 0) {
  const calls = [];
  const cookieCalls = [];
  const removed = [];
  const flags = {
    CLEAR_COOKIES: 1, CLEAR_DOM_QUOTA: 2, CLEAR_IMAGE_CACHE: 4,
    CLEAR_CSS_CACHE: 8, CLEAR_JS_CACHE: 16, CLEAR_NETWORK_CACHE: 32,
  };
  const context = vm.createContext({
    ExtensionAPI: class {},
    Ci: { nsIClearDataService: flags },
    ChromeUtils: { originAttributesMatchPattern(oa, pattern) {
      return oa.partitionSite === pattern.partitionKeyPattern.baseDomain;
    } },
    Services: {
      io: { newURI(value) {
        const url = new URL(value);
        return { scheme: url.protocol.slice(0, -1), host: url.hostname, userPass: url.username + url.password };
      } },
      // Only the Mozilla eTLD resolver decides site boundaries in production.
      eTLD: { getSchemelessSiteFromHost(host) {
        return host.endsWith("example.com") ? "example.com" : host;
      } },
      cookies: {
        getCookiesWithOriginAttributes(pattern) {
          cookieCalls.push(JSON.parse(pattern));
          return [
            { rawHost: "news.example.com", originAttributes: JSON.parse(pattern) },
            { rawHost: "unrelated.test", originAttributes: JSON.parse(pattern) },
            { rawHost: "embedded.test", originAttributes: { ...JSON.parse(pattern), partitionSite: "example.com" } },
          ];
        },
        removeCookiesFromExactHost(host, pattern) { removed.push({ host, pattern: JSON.parse(pattern) }); },
      },
      clearData: { deleteDataFromSite(site, pattern, userRequest, mask, callback) {
        calls.push(JSON.parse(JSON.stringify({ site, pattern, userRequest, mask })));
        callback(failedFlags);
      } },
    },
  });
  vm.runInContext(asset("api.js"), context);
  return { clear: new context.candySiteData().getAPI().candySiteData.clear, calls, cookieCalls, removed };
}

test("built-in deletion service has no web content or external message entry point", () => {
  const manifest = JSON.parse(asset("manifest.json"));
  assert.equal(manifest.content_scripts, undefined);
  assert.equal(manifest.externally_connectable, undefined);
  assert.deepEqual(manifest.permissions, ["geckoViewAddons", "nativeMessaging"]);
  assert.deepEqual(manifest.experiment_apis.candySiteData.parent.scopes, ["addon_parent"]);
  assert.doesNotMatch(asset("background.js"), /runtime\.onMessage|onMessageExternal/);
});

test("site deletion always includes explicit context and private boundaries", async () => {
  for (const [contextId, isPrivate, expectedContext, expectedPrivate] of [
    ["gvctx6162", false, "gvctx6162", 0],
    ["gvctx-3c5c", false, "gvctx-3c5c", 0],
    ["", false, "", 0],
    ["gvctx707269766174653a6162", true, "gvctx707269766174653a6162", 1],
  ]) {
    const fixture = harness();
    await fixture.clear("https://news.example.com:8443/article", contextId, isPrivate);
    assert.deepEqual(fixture.calls, [{
      site: "example.com",
      pattern: { geckoViewSessionContextId: expectedContext, privateBrowsingId: expectedPrivate, userContextId: 0 },
      userRequest: false,
      // The network cache cleaner ignores profile boundaries in Gecko 157.
      mask: 30,
    }]);
    assert.deepEqual(fixture.cookieCalls, [{
      geckoViewSessionContextId: expectedContext, privateBrowsingId: expectedPrivate, userContextId: 0,
    }]);
    assert.deepEqual(fixture.removed.map((entry) => entry.host), ["news.example.com", "embedded.test"]);
    assert.ok(fixture.removed.every((entry) => entry.pattern.privateBrowsingId === expectedPrivate &&
      entry.pattern.geckoViewSessionContextId === expectedContext));
  }
});

test("invalid URL and context cannot invoke native deletion", async () => {
  for (const [url, contextId] of [
    ["file:///etc/passwd", ""], ["about:blank", ""],
    ["https://user:password@news.example.com", ""],
    ["https://news.example.com", "profile-id"],
    ["https://news.example.com", "gvctxabc&privateBrowsingId=0"],
  ]) {
    const fixture = harness();
    await assert.rejects(fixture.clear(url, contextId, false));
    assert.equal(fixture.calls.length, 0);
  }
});

test("partial native failure rejects without widening scope", async () => {
  const fixture = harness(2);
  await assert.rejects(fixture.clear("https://news.example.com", "gvctx6162", false), /incomplete/);
  assert.equal(fixture.calls.length, 1);
});

test("native reply reports failure and succeeds only after privileged completion", async () => {
  let listener;
  let finish;
  const replies = [];
  const context = vm.createContext({
    browser: {
      runtime: { getManifest() { return { version: "1.0.2" }; }, connectNative(name) {
        assert.equal(name, "candySiteData");
        return {
          onMessage: { addListener(value) { listener = value; } },
          onDisconnect: { addListener() {} },
          postMessage(value) { replies.push(value); },
        };
      } },
      candySiteData: { clear() { return new Promise((resolve) => { finish = resolve; }); } },
    },
  });
  vm.runInContext(asset("background.js"), context);
  const pending = listener({ id: "request", url: "https://news.example.com", contextId: "", isPrivate: false });
  assert.equal(replies.length, 1);
  await listener({ id: "busy" });
  assert.equal(replies[1].id, "busy");
  assert.equal(replies[1].cleared, false);
  finish();
  await pending;
  assert.equal(replies[2].cleared, true);
  context.browser.candySiteData.clear = () => Promise.reject(new Error("native failure"));
  await listener({ id: "failed" });
  assert.equal(replies[3].id, "failed");
  assert.equal(replies[3].cleared, false);
});

test("disconnect reconnects without replying on a stale native port", async () => {
  const connections = [];
  const timers = [];
  let finish;
  const context = vm.createContext({
    setTimeout(fn) { timers.push(fn); },
    browser: {
      runtime: { getManifest() { return { version: "1.0.2" }; }, connectNative() {
        const connection = { replies: [] };
        connections.push(connection);
        return {
          onMessage: { addListener(value) { connection.message = value; } },
          onDisconnect: { addListener(value) { connection.disconnect = value; } },
          postMessage(value) { connection.replies.push(value); },
        };
      } },
      candySiteData: { clear() { return new Promise((resolve) => { finish = resolve; }); } },
    },
  });
  vm.runInContext(asset("background.js"), context);
  const first = connections[0];
  const pending = first.message({ id: "first" });
  first.disconnect();
  assert.equal(timers.length, 1);
  timers[0]();
  const second = connections[1];
  assert.equal(second.replies[0].type, "ready");
  await second.message({ id: "retry" });
  assert.equal(second.replies[1].cleared, false);
  finish();
  await pending;
  assert.equal(first.replies.length, 1);
  const next = second.message({ id: "next" });
  finish();
  await next;
  assert.equal(second.replies[2].cleared, true);
});
