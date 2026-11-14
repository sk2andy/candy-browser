import assert from "node:assert/strict";
import test from "node:test";

import { mozillaAppCallback, showMozillaCallback } from "../site/oauth/mozilla/callback.mjs";

test("preserves the OAuth code, state and scoped keys at a fixed app destination", () => {
  const parameters = new URLSearchParams({
    code: "once+/=&", state: "random+/=&", keys: "encrypted.keys+/=", action: "signin",
    redirect_uri: "https://other.example/", access_token: "ignored", flowId: "ignored",
  });
  const result = mozillaAppCallback(parameters.toString());
  const url = new URL(result.url);
  assert.equal(url.protocol, "dev.sk2andy.materialbrowser:");
  assert.equal(url.host, "oauth");
  assert.equal(url.pathname, "/callback");
  assert.deepEqual([...url.searchParams], [...parameters].slice(0, 4));
  assert.equal(result.failed, false);
});

test("passes an OAuth failure back to Candy without treating it as success", () => {
  const parameters = new URLSearchParams({ state: "pending", error: "access_denied", error_description: "Cancelled", error_uri: "https://example.test/error" });
  const result = mozillaAppCallback(parameters.toString());
  assert.equal(result.failed, true);
  assert.equal(new URL(result.url).searchParams.get("error_description"), "Cancelled");
  assert.equal(new URL(result.url).searchParams.get("error_uri"), "https://example.test/error");
});

test("does not create a callback for empty, incomplete, ambiguous or duplicated responses", () => {
  for (const query of ["", "?state=s", "?code=c", "?error=denied", "?code=&state=s", "?code=c&error=denied&state=s", "?code=c&code=d&state=s", "?code=c&state=s&state=t", "?code=c&state=s&keys=a&keys=b"]) {
    assert.equal(mozillaAppCallback(query), null, query);
  }
});

function showPage(search) {
  const nodes = Object.fromEntries(["callback-title", "callback-message", "open-candy", "callback-help"].map((id) => [id, { hidden: true }]));
  let replacement;
  showMozillaCallback({
    location: { search, pathname: "/candy-browser/oauth/mozilla/" },
    history: { replaceState: (...args) => { replacement = args; } },
  }, { getElementById: (id) => nodes[id] });
  assert.deepEqual(replacement, [null, "", "/candy-browser/oauth/mozilla/"]);
  return nodes;
}

test("offers an explicit app link and clears the browser URL without claiming login succeeded", () => {
  const nodes = showPage("?code=one-time&state=pending");
  assert.equal(nodes["callback-title"].textContent, "Continue in Candy");
  assert.equal(nodes["open-candy"].hidden, false);
  assert.equal(nodes["open-candy"].href, "dev.sk2andy.materialbrowser://oauth/callback?code=one-time&state=pending");
});

test("shows static failure text without rendering untrusted error descriptions", () => {
  const nodes = showPage("?error=access_denied&state=pending&error_description=%3Cscript%3E");
  assert.equal(nodes["callback-title"].textContent, "Sign-in was not completed");
  assert.equal(nodes["open-candy"].hidden, false);
  assert.equal(nodes["callback-message"].textContent.includes("<script>"), false);
});

test("shows restart instructions without an app link for a direct visit", () => {
  const nodes = showPage("");
  assert.equal(nodes["callback-title"].textContent, "Start sign-in in Candy");
  assert.equal(nodes["open-candy"].hidden, true);
  assert.equal(nodes["open-candy"].href, undefined);
});
