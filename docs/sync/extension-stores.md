# Candy Sync store releases

| Need | Source |
| --- | --- |
| Product homepage | https://sk2andy.github.io/candy-browser/ |
| Privacy policy, including Candy Sync | https://sk2andy.github.io/candy-browser/privacy/ |
| Publisher imprint | https://sk2andy.github.io/candy-browser/imprint/ |
| Descriptions, media and permission disclosures | [`../../sync/extension/store/LISTING.md`](../../sync/extension/store/LISTING.md) |
| Firefox initial listing metadata | [`../../sync/extension/store/amo-metadata.json`](../../sync/extension/store/amo-metadata.json) |
| Reviewer build instructions | [`../../sync/extension/store/SOURCE_BUILD.md`](../../sync/extension/store/SOURCE_BUILD.md) |
| Release workflow | [`../../.github/workflows/publish-sync-extension.yml`](../../.github/workflows/publish-sync-extension.yml) |

## First publication

| Store | One-time preparation |
| --- | --- |
| Chrome Web Store | Sign in to the [Developer Dashboard](https://chrome.google.com/webstore/devconsole/), complete publisher registration, upload the Chromium ZIP, fill listing/privacy fields, upload media, choose public visibility, and submit the initial listing. Record extension and publisher IDs. The API updates an existing item; it cannot create the initial listing. |
| Firefox Add-ons | Sign in to the [Developer Hub](https://addons.mozilla.org/developers/), complete publisher terms/profile and obtain API credentials. Submit a listed version with Firefox ZIP and source ZIP, or use the workflow's initial listing metadata. Supply test-server access privately to Mozilla if requested; never commit reviewer credentials. |

Store accounts, agreements, registration payments and reviewer access must be completed before
publishing. Upload success means submission; public availability still requires store approval.

## GitHub configuration

Configure repository secrets/variables in Settings → Secrets and variables → Actions, or in
the named GitHub environments. Environment values take precedence.

| Environment | Type | Name | Value |
| --- | --- | --- | --- |
| `chrome-web-store` | Variable | `CWS_PUBLISHER_ID` | Publisher ID from Developer Dashboard account settings |
| `chrome-web-store` | Variable | `CWS_EXTENSION_ID` | 32-letter extension ID of the initial uploaded item |
| `chrome-web-store` | Secret | `CWS_CLIENT_ID` | Google OAuth client with Chrome Web Store API enabled |
| `chrome-web-store` | Secret | `CWS_CLIENT_SECRET` | Corresponding OAuth client secret |
| `chrome-web-store` | Secret | `CWS_REFRESH_TOKEN` | Offline token for the owning publisher with `https://www.googleapis.com/auth/chromewebstore` scope |
| `firefox-addons` | Secret | `AMO_JWT_ISSUER` | API key from [AMO credentials](https://addons.mozilla.org/developers/addon/api/key/) |
| `firefox-addons` | Secret | `AMO_JWT_SECRET` | Corresponding API secret |

Missing configuration explicitly fails the affected store job. Both stores submit independently
after shared verification. Tokens and raw OAuth error bodies are not logged. If environment
protection requires approval, GitHub waits; configure protection for the intended automatic flow.

Official setup: [Chrome API](https://developer.chrome.com/docs/webstore/using-api),
[Firefox signing](https://extensionworkshop.com/documentation/develop/web-ext-command-reference/).

## Package and release

| Step | Action | Gate |
| --- | --- | --- |
| 1 | Inside `sync/extension`, run `npm version X.Y.Z --no-git-tag-version` | Three numeric components, each 0–65535; strictly newer than both store versions |
| 2 | Run `npm ci`, `npm run verify`, `npm run test:stores`, `npm run package:stores` | Built manifests inherit package version; stale builds rejected |
| 3 | Merge reviewed change into `main` | Source and workflow available on release commit |
| 4 | Tag that commit `sync-extension-vX.Y.Z`; push tag | Exact match with package version; Android `v*` tags do not publish extensions |
| 5 | Inspect **Publish Candy Sync extension** in Actions | Checksums verified before each submission |
| 6 | Inspect both developer dashboards | Reviewer requests resolved; public listing approved |

Manual workflow runs from `main` and takes an existing tag's version. It rebuilds that tag's
exact sources. Duplicate accepted versions may be rejected; use **Re-run failed jobs** when
one store already succeeded. Store approval remains asynchronous.

| Artifact in `sync/extension/release/` | Purpose |
| --- | --- |
| `candy-sync-chromium-X.Y.Z.zip` | Chrome upload; manifest at ZIP root |
| `candy-sync-firefox-X.Y.Z.zip` | Listed Firefox version; fixed Gecko ID `candy-sync@sk2andy.dev` |
| `candy-sync-source-X.Y.Z.zip` | Reviewer sources, exact lockfile, build scripts, assets, protocol fixtures and `BUILD.md` |
| `SHA256SUMS` | SHA-256 of all three ZIPs |

ZIP order, timestamps and permissions are fixed. Reviewer sources exclude dependencies, builds,
local state and credentials. Packages include project and bundled dependency licenses.

## Verification

| Check | Command |
| --- | --- |
| Extension contracts and reproducibility | `npm run verify` |
| ZIP contents, identical bytes, stale-build rejection | `npm run test:stores` |
| Release policy and mocked upload/review failures | `node --test tests/publish-stores.test.mjs` |
| Firefox marketplace validator | `npx --yes --package=web-ext@10.7.0 web-ext lint --source-dir=dist/firefox --no-config-discovery` |

Local checks do not exercise store credentials or guarantee review approval.
