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

| Change | Required path |
| --- | --- |
| EasyList/uAssets network or cosmetic data | Run matching `scripts/update_*` generator; keep source revision pinned |
| Compiler behavior | Run `scripts/test_compile_easylist_cosmetic.py` and/or `scripts/test_compile_uassets_cosmetic.py` |
| Site privacy defaults | Update audit CSV, run `generate_site_privacy_defaults.mjs`, run matching `.test.mjs` |
| Bundled asset shape | Run relevant `blocking/*AssetInstrumentedTest` |
| Release-facing generated file | Confirm generator produces clean diff on second run |

- Generated assets are outputs: edit generator/source/audit evidence, not generated text by hand.
- Preserve upstream source, pinned revision, license/notice files and transformation script together.
- Keep audit evidence in [`../audits/`](../audits/); do not replace measured classifications with undocumented exceptions.

