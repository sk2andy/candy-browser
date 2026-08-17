# Blocking and Candy Rules

## Blocking pipeline

| Layer | Source | Role |
| --- | --- | --- |
| Bundled network lists | `ContentBlocker` + `RequestBlocker` | Exact/subdomain host and scoped pair blocking with allow exceptions |
| Bundled cosmetic lists | `EasyListCosmeticRules`, `BundledCandyRules` | Resolve origin-scoped standard selectors |
| Consent handling | `ConsentBlockerScript` + curated request rules | Hide consent UI, stop known modal CMP runtimes and apply bounded declarative site rules |
| User/import/subscription rules | `CandyRule*`, `CandyRuleRepository` | Validate, normalize, persist and compile per-profile matchers |
| Runtime interception | `BrowserController` | Combine site settings, bundled lists and Candy Rule decision for WebView requests |

## Candy Rule precedence

| Higher priority | Lower priority |
| --- | --- |
| Scoped pair allow | Scoped pair block |
| More specific page/request host | Less specific host |
| Allow at equal specificity | Block at equal specificity |
| Stable rule ID tie-break | — |

## Guardrails

- Never intercept a main-frame request with a Candy network rule.
- Preserve first-party escape and explicit allow exceptions before bundled blocking.
- Validate hosts, public suffixes, selectors, profile IDs and HTTPS subscription sources atomically.
- Keep persistent matcher free of ephemeral private rules; private matcher may include them only in memory.
- Support only declared Candy/ABP subsets. Reject unsupported syntax instead of approximating it.
- Curated consent-runtime hosts apply provider-wide only while cookie-banner removal is enabled;
  site protection pause and per-site consent overrides remain escape hatches.

## Main files

| Concern | File |
| --- | --- |
| Runtime blocker | [`ContentBlocker.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/ContentBlocker.kt) |
| Host lookup | [`RequestBlocker.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/RequestBlocker.kt) |
| Rule validation/matching | [`CandyRule.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/CandyRule.kt) |
| Import/export/subscriptions | [`CandyRuleFormat.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/CandyRuleFormat.kt) |
