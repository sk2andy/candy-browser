# Candy Sync reviewer build

This archive contains the unmodified TypeScript sources, exact npm lockfile,
browser manifests, icons, protocol fixtures and build scripts for both stores.
Bundling uses esbuild without minification. No remote executable code is loaded.

Prerequisites: Node.js 22.14 or newer, npm, Python 3 (packaging only).

From the extracted archive root:

```sh
cd sync/extension
npm ci
npm run build
```

Compare `dist/firefox/` with the submitted Firefox ZIP. The matching Chromium
build is in `dist/chromium/`. Both builds copy the root MPL-2.0 license and the
MIT license for the bundled `@noble/hashes` dependency.

`npm run verify` runs unit tests, protocol validation, build checks, manifest
validation and two-build reproducibility. The icon artwork provenance test
references the Android checkout, which is not needed to reproduce the extension
and is not included in this source archive. To run the complete gate, use the
corresponding repository revision instead.

The extension requires a user-selected Candy Sync server and an independently
chosen E2EE passphrase. Do not use real browsing data in review credentials.
Setup instructions: https://github.com/sk2andy/candy-browser/blob/main/docs/sync/server.md
Privacy policy: https://sk2andy.github.io/candy-browser/privacy/
