# X-Ray, permissions and filter maintenance

## Privacy X-Ray

| Piece | Responsibility |
| --- | --- |
| `PrivacyRequestSanitizer` | Reduce request/page URLs to safe attribution data |
| `PrivacyRequestClassifier` / `PrivacyPartyClassifier` | Classify category and first/third-party relation |
| `PrivacyRetention` / `PrivacyAggregation` | Bound retained per-tab observations and summaries |
| `PrivacyXRayRepository` | Own live snapshots, rule decisions and site exceptions |
| Address-bar connection badge | Opens one site sheet with Privacy X-Ray and Permission Radar tabs, including when no requests were blocked; shows the blocked count and uses distinct HTTPS, HTTP and unavailable indicators; keeps a 48 dp touch target around a 40 dp visible badge |
| `PrivacyXRaySheet` | Shows the page address connection type and switches between the live X-Ray snapshot and site permissions; Frosted mode blurs the active browser-content source behind the sheet |
| Gecko request-blocking card | Explains that Firefox extension requests are absent from Candy counters; opens the enabled uBlock Origin browser action only for the current selected tab, through Gecko's existing profile/private-aware popup path |
| Gecko Candy counters | Count only Candy's observed cookie-banner request blocks; zero does not imply uBlock blocked nothing. Candy Studio/rule actions are unavailable here because Gecko ad/tracker filtering belongs to Firefox extensions. Candy pause does not pause extensions. |
| System WebView counters | Keep existing Candy request/rule observations, categories, domain actions and Studio |

Gecko's public `webRequest.onErrorOccurred` exposes request errors without the blocking extension's
identity. Extension cancellation can produce `NS_ERROR_ABORT`, which cannot be treated as evidence
that uBlock blocked a request. Candy neither infers counts from errors nor reads extension-private
storage or logs. The native ContentBlocking delegate observes Gecko protection, not uBlock's filter
decisions. See [the API contract](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest/onErrorOccurred)
and [Mozilla's cancellation path](https://searchfox.org/firefox-main/source/toolkit/components/extensions/webrequest/WebRequest.sys.mjs).

| Regression evidence | Contract |
| --- | --- |
| `GeckoPrivacyXRayInstrumentedTest` | Signed bundled uBlock blocks a packaged EasyList script URL against a local server while an allowed control loads; disabling and re-enabling uBlock proves the source. Candy never synthesizes uBlock events, including after navigation. |
| `PrivacyXRayEngineInstrumentedTest` | Production site-info wiring shows the Gecko source explanation and preserves System WebView counters/Studio |
| `GeckoPrivacyXRayPopupInstrumentedTest` | Real signed uBlock popup through the site-info button; exact extension/tab/private identity, owner-switch dismissal, and no borrowed action for a non-selected or private-ineligible tab |
| `GeckoPrivacyXRayRulesTest` | Exact uBlock action identity, current tab, enabled action and unavailable states |

X-Ray observations remain memory-only and bounded per tab. Existing navigation resets and private
cleanup apply unchanged. Gecko's extension host excludes extensions without explicit private access;
X-Ray does not grant access or persist a separate extension count.

## Permission Radar

| Stage | Source | Invariant |
| --- | --- | --- |
| Origin | `PermissionOrigin` | Normalize HTTP(S); allow only potentially trustworthy origins |
| Request identity | `PermissionRequestRules` | Tab/profile/navigation generation must be current, selected and resumed |
| Decision | `PermissionRadarRepository` | Separate persistent, private and allow-once state |
| Android grant | `runtimePermissions`, `PermissionResponseDelivery` | Web permission is granted only after required Android runtime permissions |
| Storage | `PermissionRadarStore` | Persist only non-private, non-`Ask` decisions |
| Website info | Address-bar lock/warning button and `PermissionRadarSheet` | Show the current HTTP(S) scheme and open per-site decisions even without an active request |
| Website notifications | Gecko content permission, Android `POST_NOTIFICATIONS`, `GeckoWebNotificationPresenter` | Ask through Permission Radar, align persistent decisions with Gecko storage, then publish permitted page notifications through Android; private notifications are denied |

Android System WebView does not expose website Notifications or Web Push. Its Permission Radar row
explains this limitation and does not offer a notification grant; there is no page-request callback
to trigger a prompt when a site attempts to use those APIs.

Notification grants are persistent site decisions because Gecko stores the result of a content
permission request. Permission Radar does not offer a session-only notification grant. Denying the
Android notification permission records a site block so Candy and Gecko keep the same decision.

Gecko's page notification API is separate from background Web Push subscriptions. Background
delivery needs a push transport and `WebPushDelegate`; the page notification path does not create
or receive push subscriptions.

## Generated assets and audits

| Ownership | Files | Update rule |
| --- | --- | --- |
| Upstream-generated | `easylist_*`, `uassets_*` | Replace only through matching pinned `scripts/update_*` generator |
| Candy-owned rules | `candy_default_rules.txt`, `cookie_banner_overrides.css` | Edit directly with audit evidence; upstream generators must not modify |
| Candy-owned runtime | `CandyCosmeticScript`, `GenericCosmeticRuntime`, `AdvancedFilterRules`, `BundledBlockingSnapshotProvider`, `BlockingStartGate`, `ProceduralCosmeticRules` | Keep runtime behavior in reviewed Kotlin/JS; never copy upstream scriptlets |

Release builds hash Candy-owned filter sources before and after upstream generation and fail if a
fetch/compiler changes them. This keeps custom JS/CSS behavior outside replaceable list outputs.
The EasyList updater compiles scoped and full supported generic standard CSS into its v2 asset. The
uAssets updater validates and compiles the pinned stable include manifest (`filters-general`,
mobile, yearly archives, and link-shortener rules) into separate advanced URL/popup/popunder assets
and its supported generic cosmetic subset. Both retain `#@#`/`$ghide` semantics for merged runtime
resolution.
The network updater resolves the complete pinned EasyList/EasyPrivacy template graph into a sorted
host index and scoped allow pairs. A separately pinned HaGeZi Pro source contributes only hosts not
already covered by Candy, EasyList, or uAssets; its compiler verifies the source SHA-256, declared
count, syntax, ordering, uniqueness, and byte budgets before replacement. The release workflow
regenerates EasyList and uAssets before the HaGeZi delta and fails on every generated-asset diff.
Short-lived `quick-fixes` are not shipped. Candy-owned runtime and curated CSS stay outside outputs.

| Change | Required path |
| --- | --- |
| EasyList/uAssets network or cosmetic data | Run matching `scripts/update_*` generator; keep source revision pinned |
| Candy curated host | Record evidence in [`../audits/`](../audits/); regenerate the HaGeZi delta |
| Compiler behavior | Run matching `scripts/test_compile_*` tests, including `test_compile_advanced_filters.py` |
| Site privacy defaults | Update audit CSV, run `generate_site_privacy_defaults.mjs`, run matching `.test.mjs` |
| Bundled asset shape | Run relevant `blocking/*AssetInstrumentedTest` |
| Release-facing generated file | Confirm generator produces clean diff on second run |

- Generated assets are outputs: edit generator/source/audit evidence, not generated text by hand.
- Do not promote benchmark-only, consent, social API, login, video, or CDN hosts into global rules
  merely to raise a synthetic score; prefer scoped/path rules when maintained filters provide them.
- Preserve upstream source, pinned revision, license/notice files and transformation script together.
- Keep audit evidence in [`../audits/`](../audits/); do not replace measured classifications with undocumented exceptions.
