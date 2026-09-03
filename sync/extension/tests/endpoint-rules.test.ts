import assert from "node:assert/strict";
import test from "node:test";

import { endpointPermissionOrigin, normalizeEndpoint } from "../src/core/endpoint-rules.js";

test("normalizes secure origins and loopback development endpoints", () => {
  assert.equal(normalizeEndpoint(" https://sync.example.net "), "https://sync.example.net/");
  assert.equal(normalizeEndpoint("http://localhost:8080/"), "http://localhost:8080/");
  assert.equal(normalizeEndpoint("http://[::1]:8080/"), "http://[::1]:8080/");
  assert.equal(normalizeEndpoint("http://sync.example.net/", true), "http://sync.example.net/");
  assert.equal(endpointPermissionOrigin("http://sync.example.net/"), "http://sync.example.net/*");
  assert.equal(endpointPermissionOrigin("http://sync.example.net:7070/"), "http://sync.example.net/*");
  assert.equal(endpointPermissionOrigin("https://sync.example.net/"), "https://sync.example.net/*");
});

test("rejects unsafe or ambiguous endpoints", () => {
  for (const value of [
    "http://sync.example.net/",
    "https://user:secret@sync.example.net/",
    "https://sync.example.net/path",
    "https://sync.example.net/?query=yes",
    "file:///tmp/server",
    "not a url",
  ]) {
    assert.throws(() => normalizeEndpoint(value));
  }
});
