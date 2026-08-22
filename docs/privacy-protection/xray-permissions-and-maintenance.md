# X-Ray, permissions and filter maintenance

## Privacy X-Ray

| Piece | Responsibility |
| --- | --- |
| `PrivacyRequestSanitizer` | Reduce request/page URLs to safe attribution data |
| `PrivacyRequestClassifier` / `PrivacyPartyClassifier` | Classify category and first/third-party relation |
| `PrivacyRetention` / `PrivacyAggregation` | Bound retained per-tab observations and summaries |
| `PrivacyXRayRepository` | Own live snapshots, rule decisions and site exceptions |
| `PrivacyXRaySheet` | Render snapshot and emit rule/override actions |

## Permission Radar

| Stage | Source | Invariant |
| --- | --- | --- |
| Origin | `PermissionOrigin` | Normalize HTTP(S); allow only potentially trustworthy origins |
| Request identity | `PermissionRequestRules` | Tab/profile/navigation generation must be current, selected and resumed |
| Decision | `PermissionRadarRepository` | Separate persistent, private and allow-once state |
| Android grant | `runtimePermissions`, `PermissionResponseDelivery` | Web permission is granted only after required Android runtime permissions |
| Storage | `PermissionRadarStore` | Persist only non-private, non-`Ask` decisions |

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
| Candy curated host | Require independent maintained-list corroboration; regenerate the HaGeZi delta |
| Compiler behavior | Run matching `scripts/test_compile_*` tests, including `test_compile_advanced_filters.py` |
| Site privacy defaults | Update audit CSV, run `generate_site_privacy_defaults.mjs`, run matching `.test.mjs` |
| Bundled asset shape | Run relevant `blocking/*AssetInstrumentedTest` |
| Release-facing generated file | Confirm generator produces clean diff on second run |

- Generated assets are outputs: edit generator/source/audit evidence, not generated text by hand.
- Do not promote benchmark-only, consent, social API, login, video, or CDN hosts into global rules
  merely to raise a synthetic score; prefer scoped/path rules when maintained filters provide them.
- Preserve upstream source, pinned revision, license/notice files and transformation script together.
- Keep audit evidence in [`../audits/`](../audits/); do not replace measured classifications with undocumented exceptions.
