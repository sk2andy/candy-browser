import assert from "node:assert/strict";
import test from "node:test";

import { publishChrome, releaseVersion, requiredConfiguration } from "../scripts/publish-stores.mjs";

const configuration = {
  CWS_PUBLISHER_ID: "publisher-123",
  CWS_EXTENSION_ID: "a".repeat(32),
  CWS_CLIENT_ID: "client-id",
  CWS_CLIENT_SECRET: "sensitive-client-secret",
  CWS_REFRESH_TOKEN: "sensitive-refresh-token",
};

function tagged(version) {
  return { GITHUB_EVENT_NAME: "push", GITHUB_REF: `refs/tags/sync-extension-v${version}` };
}

test("only extension version tags or manual main releases can publish", () => {
  assert.equal(releaseVersion(tagged("1.2.3"), "1.2.3"), "1.2.3");
  assert.equal(releaseVersion({
    GITHUB_EVENT_NAME: "workflow_dispatch", GITHUB_REF: "refs/heads/main", RELEASE_VERSION: "1.2.3",
  }, "1.2.3"), "1.2.3");
  for (const environment of [
    { GITHUB_EVENT_NAME: "push", GITHUB_REF: "refs/tags/v1.2.3" },
    { GITHUB_EVENT_NAME: "workflow_dispatch", GITHUB_REF: "refs/heads/topic", RELEASE_VERSION: "1.2.3" },
    { GITHUB_EVENT_NAME: "pull_request", GITHUB_REF: "refs/heads/main", RELEASE_VERSION: "1.2.3" },
    tagged("1.2.4"), tagged("1.2.3-rc.1"), tagged("1.2"), tagged("01.2.3"), tagged("0.0.0"), tagged("65536.2.3"),
  ]) {
    assert.throws(() => releaseVersion(environment, "1.2.3"));
  }
});

test("missing credentials fail before any network request without exposing secrets", async () => {
  const missing = { ...configuration, CWS_REFRESH_TOKEN: "" };
  await assert.rejects(publishChrome(Buffer.from("zip"), missing, {
    fetchImpl: () => assert.fail("Must not contact the store"),
  }), /Missing store configuration: CWS_REFRESH_TOKEN/u);
  assert.throws(() => requiredConfiguration({ AMO_JWT_ISSUER: "" }, ["AMO_JWT_ISSUER", "AMO_JWT_SECRET"]),
    /AMO_JWT_ISSUER, AMO_JWT_SECRET/u);
});

function mockRequests(responses) {
  const calls = [];
  return {
    calls,
    fetchImpl: async (url, options) => {
      calls.push({ url, options });
      const response = responses.shift();
      assert.ok(response, "Unexpected store request");
      return { ok: true, status: 200, json: async () => response };
    },
  };
}

test("Chrome waits for async upload before submitting a normal public review", async () => {
  const mock = mockRequests([
    { access_token: "access-token" }, { uploadState: "IN_PROGRESS" },
    { lastAsyncUploadState: "IN_PROGRESS" }, { lastAsyncUploadState: "SUCCEEDED" },
    { state: "PENDING_REVIEW" },
  ]);
  const archive = Buffer.from("reviewed-package");
  const sleeps = [];
  assert.equal(await publishChrome(archive, configuration, {
    fetchImpl: mock.fetchImpl, sleep: async (delay) => sleeps.push(delay),
  }), "PENDING_REVIEW");
  assert.equal(mock.calls.length, 5);
  assert.deepEqual(sleeps, [10_000, 10_000]);
  assert.equal(mock.calls[0].options.body.get("grant_type"), "refresh_token");
  assert.match(mock.calls[1].url, /\/upload\/v2\/publishers\/publisher-123\/items\/a{32}:upload$/u);
  assert.equal(mock.calls[1].options.body, archive);
  assert.equal(mock.calls[1].options.headers.Authorization, "Bearer access-token");
  assert.deepEqual(JSON.parse(mock.calls[4].options.body), {
    publishType: "DEFAULT_PUBLISH", blockOnWarnings: true, skipReview: false,
  });
});

test("failed, unknown or timed out uploads never reach publish", async () => {
  for (const state of ["FAILED", "NOT_FOUND", "UPLOAD_STATE_UNSPECIFIED", "IN_PROGRESS", undefined]) {
    const mock = mockRequests([{ access_token: "access-token" }, { uploadState: state }]);
    await assert.rejects(publishChrome(Buffer.from("zip"), configuration, {
      fetchImpl: mock.fetchImpl, maxPolls: 0,
    }), /upload did not succeed/u);
    assert.equal(mock.calls.length, 2);
  }
});

test("OAuth errors never expose server response bodies or credentials", async () => {
  await assert.rejects(publishChrome(Buffer.from("zip"), configuration, {
    fetchImpl: async () => ({ ok: false, status: 401, json: () => assert.fail("Must not read error body") }),
  }), (error) => {
    assert.match(error.message, /authentication failed \(HTTP 401\)/u);
    assert.equal(error.message.includes(configuration.CWS_CLIENT_SECRET), false);
    return true;
  });
});

test("public submission rejects staged, testers-only and rejected results", async () => {
  for (const state of ["STAGED", "PUBLISHED_TO_TESTERS", "REJECTED", "CANCELLED", undefined]) {
    const mock = mockRequests([{ access_token: "access-token" }, { uploadState: "SUCCEEDED" }, { state }]);
    await assert.rejects(publishChrome(Buffer.from("zip"), configuration, { fetchImpl: mock.fetchImpl }),
      /not accepted for public publishing/u);
  }
});

test("invalid identity, missing token, invalid JSON and network failures fail closed", async () => {
  await assert.rejects(publishChrome(Buffer.from("zip"), { ...configuration, CWS_EXTENSION_ID: "wrong" }, {
    fetchImpl: () => assert.fail("Must not contact the store"),
  }), /Invalid Chrome publisher or extension ID/u);
  const mock = mockRequests([{}]);
  await assert.rejects(publishChrome(Buffer.from("zip"), configuration, { fetchImpl: mock.fetchImpl }),
    /did not return an access token/u);
  await assert.rejects(publishChrome(Buffer.from("zip"), configuration, {
    fetchImpl: async () => ({ ok: true, json: async () => { throw new Error("secret response"); } }),
  }), { message: "Chrome authentication returned invalid JSON." });
  await assert.rejects(publishChrome(Buffer.from("zip"), configuration, {
    fetchImpl: async () => { throw new Error(configuration.CWS_CLIENT_SECRET); },
  }), { message: "Chrome authentication network request failed." });
});
