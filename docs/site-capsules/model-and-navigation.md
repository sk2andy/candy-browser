# Model and navigation

## Model

| Concern | Source | Rule |
| --- | --- | --- |
| Create/update/sanitize | [`SiteCapsule.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/SiteCapsule.kt) | Validate opaque ID, name, HTTP(S) URL and profile; cap collection at 64 |
| Profile projection | [`CapsuleProfileRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleProfileRules.kt) | Referenced profile must exist; isolation requires provider support |
| Chrome | `CapsuleChromeMode` | Persist explicit mode; UI derives control visibility from it |
| Icon | `CapsuleIconMode` | Use favicon or profile fallback through explicit projection |

## Navigation decisions

| Mode | Stay inside capsule when |
| --- | --- |
| `SameOrigin` | Scheme, normalized host and effective port match start URL |
| `SameRegistrableDomain` | Public-suffix-aware site key matches start URL |
| `AllLinks` | Target is a valid HTTP(S) URL |

[`CapsuleNavigationRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleNavigationRules.kt) returns `StayInCapsule`, `OpenInFullCandy`, or `UseExistingUriPolicy`. Keep non-web schemes on the shared browser policy path.

## Security invariants

- Normalize IDNs, ports and public suffixes before comparing sites.
- Reject credentials, malformed authorities and unsafe schemes.
- Do not weaken navigation scope because a shortcut or persisted record is trusted.
- Keep dedicated-profile ownership explicit; profile isolation is capability-dependent.

