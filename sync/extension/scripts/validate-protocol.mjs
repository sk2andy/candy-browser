import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import YAML from "yaml";

const extensionRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const protocolRoot = path.resolve(extensionRoot, "../protocol");
const schemasRoot = path.join(protocolRoot, "schemas");
const fixturesRoot = path.join(protocolRoot, "fixtures");
const schemaNames = ["bootstrap-v1", "device-v1", "encrypted-change-v1", "tab-snapshot-payload-v1", "tab-snapshot-v1"];

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
const schemas = new Map();
for (const name of schemaNames) {
  const schema = JSON.parse(fs.readFileSync(path.join(schemasRoot, `${name}.schema.json`), "utf8"));
  schemas.set(name, schema);
  ajv.addSchema(schema);
}

for (const name of schemaNames) {
  const validate = ajv.getSchema(schemas.get(name).$id);
  assert.ok(validate, `schema was not compiled: ${name}`);
  for (const expectation of ["valid", "invalid"]) {
    const fixture = JSON.parse(fs.readFileSync(path.join(fixturesRoot, `${name}.${expectation}.json`), "utf8"));
    const accepted = validate(fixture);
    assert.equal(accepted, expectation === "valid", `${name}.${expectation}: ${ajv.errorsText(validate.errors)}`);
  }
}

const openapi = YAML.parse(fs.readFileSync(path.join(protocolRoot, "openapi.yaml"), "utf8"));
assert.equal(openapi.openapi, "3.1.0");
assert.ok(openapi.paths["/v1/bootstrap"]?.get);
assert.ok(openapi.paths["/v1/sync/push"]?.post);
assert.ok(openapi.paths["/v1/sync/pull"]?.get);
assert.equal(schemas.get("encrypted-change-v1").$defs.ciphertext.maxLength, 524_288);
assert.equal(schemas.get("tab-snapshot-v1").$defs.ciphertext.maxLength, 524_288);

const iconCatalog = JSON.parse(fs.readFileSync(path.join(protocolRoot, "device-icons-v1.json"), "utf8"));
assert.equal(iconCatalog.schemaVersion, 1);
assert.ok(Array.isArray(iconCatalog.icons) && iconCatalog.icons.length > 0 && iconCatalog.icons.length <= 128);
assert.equal(new Set(iconCatalog.icons.map((icon) => icon.id)).size, iconCatalog.icons.length);
for (const icon of iconCatalog.icons) {
  assert.match(icon.id, /^[a-z][a-z0-9-]{0,31}$/u);
  assert.ok(typeof icon.emoji === "string" && icon.emoji.length > 0 && icon.emoji.length <= 16);
  assert.ok(typeof icon.label === "string" && icon.label.length > 0 && icon.label.length <= 40);
}

console.log(`5 protocol schemas, 10 fixtures, ${iconCatalog.icons.length} shared icons, and OpenAPI structure are valid.`);
