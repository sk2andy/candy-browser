import assert from "node:assert/strict";
import test from "node:test";

import { dataCollectionForSelection, permissionsForSelection, selectionWith } from "../src/core/permission-rules.js";
import { permissionRequest } from "../src/browser-adapters/permissions.js";

test("maps selected data types to least browser permissions", () => {
  assert.deepEqual(permissionsForSelection({ tabs: true, bookmarks: false, groups: false }), ["tabs"]);
  assert.deepEqual(
    permissionsForSelection({ tabs: false, bookmarks: true, groups: true }),
    ["bookmarks", "tabGroups", "tabs"],
  );
  assert.deepEqual(dataCollectionForSelection({ tabs: false, bookmarks: true, groups: true }), ["bookmarksInfo", "browsingActivity"]);
});

test("adds Firefox data collection only to Firefox permission requests", () => {
  const selection = { tabs: true, bookmarks: false, groups: true };
  assert.deepEqual(
    permissionRequest(selection, ["http://192.168.86.28:7070/*"], false),
    {
      origins: ["http://192.168.86.28:7070/*"],
      permissions: ["tabGroups", "tabs"],
    },
  );
  assert.deepEqual(
    permissionRequest(selection, ["http://192.168.86.28:7070/*"], true),
    {
      origins: ["http://192.168.86.28:7070/*"],
      permissions: ["tabGroups", "tabs"],
      data_collection: ["browsingActivity"],
    },
  );
});

test("updates selection without mutating source", () => {
  const source = { tabs: true, bookmarks: false, groups: false };
  const result = selectionWith(source, "groups", true);
  assert.deepEqual(source, { tabs: true, bookmarks: false, groups: false });
  assert.deepEqual(result, { tabs: true, bookmarks: false, groups: true });
});
