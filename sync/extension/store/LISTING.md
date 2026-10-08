# Candy Sync store listing

## Shared fields

| Field | Value |
| --- | --- |
| Name | Candy Sync |
| Default language | English (en-US) |
| Chrome short description / Firefox summary | Sync open tabs with Candy Browser through your own server. End-to-end encryption keeps tab URLs and titles private. |
| Homepage | https://sk2andy.github.io/candy-browser/ |
| Privacy policy | https://sk2andy.github.io/candy-browser/privacy/ |
| Support URL | https://github.com/sk2andy/candy-browser/issues |
| Source | https://github.com/sk2andy/candy-browser |
| License | Mozilla Public License 2.0 |
| Suggested Chrome category | Workflow & Planning |
| Firefox category | Tabs |

## Description

Keep your desktop tabs connected to Candy Browser with Candy Sync, an open-source extension for a self-hosted synchronization server.

• Synchronize eligible open tabs, including URLs, titles, order, pin state and supported group assignments.
• Apply changes from Candy Browser to the matching desktop synced profile: open, navigate, pin, reorder and close tabs.
• Encrypt tab payloads and device presentation data locally before upload.
• Choose your own server, device name and profile icon.
• Enable supported sync scopes and grant browser permissions during setup.
• Use realtime delivery when available, with durable recovery after interruptions.

Setup requires a Candy Sync server and its credentials. Candy does not provide a hosted sync service. Remote servers require HTTPS; localhost HTTP is available for development on the same device. Open the extension's Options page to connect. There is no toolbar popup.

Your E2EE passphrase stays on your device and unlocks the encrypted local vault. After restarting the browser, unlock the session again. The passphrase cannot be changed or recovered: keep it safe and use a different value from your server password.

Private tabs, browser-internal pages and local files are excluded. The selected server receives encrypted payloads and necessary authentication/routing metadata. This extension has no analytics or advertising code.

Bookmark synchronization and dedicated tab-group merging are not available in this version. Supported group assignments travel with tab snapshots.

Privacy policy: https://sk2andy.github.io/candy-browser/privacy/
Source and support: https://github.com/sk2andy/candy-browser

## Chrome privacy practices

Single purpose: Synchronize eligible browser tabs with Candy Browser through a user-selected self-hosted server, using local end-to-end encryption.

| Permission | Justification to paste into the dashboard |
| --- | --- |
| storage | Store server settings, encrypted local vault, encrypted pending changes, stable tab identifiers and redacted sync status. |
| alarms | Resume synchronization and recover missed changes when background realtime delivery is suspended. |
| tabs (optional) | Read eligible tab URLs, titles, order and pin state; apply synchronized opens, navigations, pin changes, reordering and closes after the user selects tab synchronization. Private tabs, browser-internal pages and local files are excluded. |
| tabGroups (optional) | Read supported group assignments for the encrypted tab snapshot after the user selects group synchronization. Dedicated group merging is not implemented. |
| host access (optional) | Contact the exact selected server scheme and hostname approved during setup. HTTPS wildcard declaration supports user-supplied self-hosted servers; localhost HTTP is only for same-device development. No content script reads website contents. |

Data handling must not be declared as “no user data.” Select **Authentication information**, **Personally identifiable information**, and **Web history**: setup sends server credentials; device name is encrypted; enabled tab sync sends encrypted URLs/titles and related tab state. Select only categories matching the dashboard wording; no page-content capture, analytics, advertising or sale. Encryption does not remove disclosure obligations. Explain that the developer runs no hosted service and receives no synced browsing data; the user's chosen server processes authentication and routing metadata, network metadata and encrypted payloads.

Remote code: **No**. JavaScript and cryptographic dependencies are bundled locally. No remote executable code or content scripts.

Certifications: Data is used only for the extension's stated tab-sync purpose, is not sold, is not used for lending/credit decisions, and is not transferred for unrelated purposes. These statements match the GitHub Pages policy, including Chrome's Limited Use statement.

## Firefox privacy policy field

Use the full policy text from GitHub Pages, not a newly invented legal policy. The page covers Candy Browser and Candy Sync. Paste its visible article text into AMO's Privacy Policy field and retain its canonical link. `amo-metadata.json` contains fields supported by the Create API; privacy policy and support URL are completed in Edit Listing / policy settings after creation.

Firefox manifest disclosures: required `authenticationInfo` and `personallyIdentifyingInfo`; optional `browsingActivity`. The tab scope controls optional browsing-data transmission. Do not replace these with `none`.

## Reviewer notes

Self-hosted extension; no developer-hosted account or service. Reviewers can start their own disposable local server from the repository: in sync/server copy .env.example to .env, set CANDY_SYNC_USERNAME and a unique CANDY_SYNC_PASSWORD of at least 16 bytes. For this disposable same-device review server, explicitly set CANDY_SYNC_PUBLIC_URL=http://localhost:8080, CANDY_SYNC_ALLOW_HTTP=true, and CANDY_SYNC_BIND_ADDRESS=127.0.0.1. Run docker compose up --build -d to build the candy-sync server and candy-sync-tls gateway from the checked-out source. The gateway exposes local HTTP on port 8080. Use http://localhost:8080/ in extension Options; this configuration also advertises the correct local WebSocket scheme without installing a CA. Do not expose this HTTP review setup remotely. Choose a separate E2EE passphrase with at least 16 characters, repeat it and acknowledge the loss warning. Do not put the E2EE passphrase into server configuration. Create a second extension/browser profile or Android client using the same server credentials and passphrase to test cross-device synchronization. No fixed credentials are shipped. Build steps and source archive layout: BUILD.md at the submitted source archive root. All executable code is bundled locally; no content scripts or remote executable code. Required Firefox data categories: authenticationInfo and personallyIdentifyingInfo; optional browsingActivity accompanies optional tab synchronization. Public privacy policy: https://sk2andy.github.io/candy-browser/privacy/.

## Media

| File | Size | Purpose / caption |
| --- | --- | --- |
| `../assets/icons/icon-128.png` | 128 × 128 | Chrome listing icon; transparent source artwork. |
| `media/icon-64.png` | 64 × 64 | Firefox listing icon, derived from the existing repository icon. |
| `media/01-settings.png` | 1280 × 800 | Actual Chromium Options page: connect to your own server. |
| `media/02-security.png` | 1280 × 800 | Actual Chromium Options page: local passphrase and supported synchronization scopes. |
| `media/promo-440x280.png` | 440 × 280 | Chrome small promotional tile. |

Screenshots use a fresh isolated browser profile with the locally built extension. They show its genuine unconfigured state; no account, credentials, tab data or connected state was fabricated. Firefox can reuse these shared-UI screenshots; no Firefox-specific browser chrome appears. UI currently mixes English labels with some German status text. PNG screenshots and promo tile are opaque RGB after normalization. Promo HTML is retained for reproducibility.

## Verified official requirements

- [Chrome images](https://developer.chrome.com/docs/webstore/images): listing icon, small promo tile and at least one screenshot; screenshots 1280 × 800 or 640 × 400.
- [Firefox listing guidance](https://extensionworkshop.com/documentation/develop/create-an-appealing-listing/): summary up to 250 characters and recommended screenshot ratio 1.6:1.
- [web-ext AMO metadata](https://extensionworkshop.com/documentation/develop/web-ext-command-reference/#amo-metadata): first listed submission requires summary, categories and version license; later versions can provide approval notes.
- [AMO API](https://addons-server.readthedocs.io/en/latest/topics/api/addons.html): privacy policy is a separate policy endpoint; support URL can be edited after creation.

Store listing assets do not bypass account onboarding, reviewer evaluation or store approval.
