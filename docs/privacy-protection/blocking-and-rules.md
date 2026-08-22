# Blocking and Candy Rules

## Blocking pipeline

| Layer | Source | Role |
| --- | --- | --- |
| Process snapshot | `BundledBlockingSnapshotProvider` | Build immutable bundled matchers once per app process on background workers |
| Bundled network lists | `ContentBlocker` + `RequestBlocker` | Exact/subdomain host and scoped pair blocking with allow exceptions |
| Advanced URL lists | `AdvancedFilterRules` | Host-bucketed URL-path/wildcard rules plus scoped popup/popunder decisions |
| Procedural cosmetics | `ProceduralCosmeticRules` | Bounded literal-text hiding and element removal for scoped upstream rules |
| Bundled cosmetic lists | `EasyListCosmeticRules`, `BundledCandyRules` | Resolve scoped and bounded generic standard selectors with exceptions |
| Consent handling | `ConsentBlockerScript` + curated request rules | Hide consent UI, stop known modal CMP runtimes and apply bounded declarative site rules |
| User/import/subscription rules | `CandyRule*`, `CandyRuleRepository` | Validate, normalize, persist and compile per-profile matchers |
| Runtime interception | `BrowserController` | Combine site settings, bundled lists and Candy Rule decision for WebView requests |

Scoped cosmetic document-start rules run in every frame whose origin matches the registered page or
user-rule origin. Generic cosmetics use a Candy-owned token scanner in the top document and its
same-origin frames; cross-origin frames remain outside that boundary and rely on request blocking.

Bundled advanced rules support bounded host-anchored paths, `*` wildcards, `^` separators,
positive/negative `domain` scopes, first/third-party scopes, allow exceptions, `$popup`, and
`$popunder`.
Popup rules inspect HTTP(S) main-frame targets created by `onCreateWindow`; automatic non-gesture
windows remain rejected by existing WebView policy. A same-site first target stays monitored for
the popup tab lifetime so a delayed cross-site advertising hop cannot bypass policy.
Site pause, profile, and private-tab ownership come from actual opener. Cross-site windows without a
matching allow rule remain hidden behind an action Snackbar; choosing Open resumes the same
quarantined WebView so `window.opener`, profile, and private context survive. Unopened popup tabs are
never persisted. Popunder rules use a bounded five-second correlation window between the surviving
child and a redirected opener, then close the listed opener. Regex filters, redirects, `$important`,
arbitrary JavaScript, and trusted uBO scriptlets fail closed.

The EasyList and uAssets cosmetic compilers include their supported global standard-CSS subsets.
Global and scoped `#@#` exceptions cancel matching selectors across merged sources; supported
`$ghide` exceptions disable only global selectors for matching sites. Kotlin resolves those host
semantics before the Candy-owned runtime receives a bounded deny policy. Simple global ID/class
selectors are token-indexed and injected only when matching DOM tokens occur. Complex global
selectors use one bounded stylesheet. The immutable prefix-compressed selector payload is built on
the cosmetic worker, cached once per top document, and never expanded into thousands of
`insertRule` calls during navigation. Scanner batches process at most 512 nodes or 4 ms, retain at
most 8,192 pending nodes, and inject at most 1,024 selectors / 96 KiB per document.
The data-only WebView bridge requires an unexposed per-WebView token, accepts canonical hosts rather
than arbitrary URLs, and keeps at most 64 resolved host policies; invalid calls fail closed.
Four Candy-owned native CSS rules additionally collapse media with a high-confidence `ads_banner`
filename token. They add no request matching or DOM scanning and require a path or filename boundary
after the token so ambiguous editorial examples and banner documentation remain visible.

Network host matching combines Candy's curated hosts, the complete supported EasyList/EasyPrivacy
template graph, uAssets, and a deduplicated HaGeZi Pro delta. Sorted byte indexes keep the larger
bundles off the per-request allocation path. Curated owner-family exceptions may allow a blocked
service only on a PSL-validated family such as `google.*`; lookalike suffixes do not match. User
Candy allow rules and site pause still take precedence over bundled blocking.

Candy accepts a deliberately narrow procedural subset: terminal literal `:has-text(...)` and
`:remove()` rules. Runtime scans at most 128 matches per selector, uses an 8 ms batch budget, stops
after 20 runs or 5 seconds, and never evaluates upstream JavaScript or regular expressions. Exact
zero-argument `+js(nowoif)` rules use a Candy-owned synchronous `window.open` defuser in
matching documents. Upstream scriptlet code and arguments are never copied or evaluated.

Bundled network, URL, popup, and procedural assets start parsing as soon as the first
`ContentBlocker` is created. The immutable snapshot is application-scoped, survives Activity
recreation, and is reused by every tab. A blank WebView and browser chrome may appear immediately;
the first external load or persisted WebView-state restore waits for snapshot readiness. The latest
pending navigation per tab wins, while stop, close, snooze, blank navigation, WebView recreation,
and controller destruction cancel stale starts. After process death the snapshot is rebuilt in the
background before restored pages can issue requests. Internal `about:blank` callbacks from a newly
created waiting WebView are ignored so they cannot overwrite a persisted restore state.

WebView request callbacks pass their already parsed request/page hosts into `ContentBlocker`.
Advanced rules inspect path/query only when a host or page bucket has candidates; the legacy
fallback reuses the same hosts instead of parsing both URLs again.

## Candy Rule precedence

| Higher priority | Lower priority |
| --- | --- |
| Scoped pair allow | Scoped pair block |
| More specific page/request host | Less specific host |
| Allow at equal specificity | Block at equal specificity |
| Stable rule ID tie-break | — |

## Guardrails

- Never intercept a main-frame request with a Candy network rule.
- Preserve first-party escape for broad host rules; precise generated URL-path rules may block
  first-party resources after their explicit allow exceptions are checked.
- Validate hosts, public suffixes, selectors, profile IDs and HTTPS subscription sources atomically.
- Keep persistent matcher free of ephemeral private rules; private matcher may include them only in memory.
- Support only declared Candy/ABP subsets. Reject unsupported syntax instead of approximating it.
- Curated consent-runtime hosts apply provider-wide only while cookie-banner removal is enabled;
  site protection pause and per-site consent overrides remain escape hatches.

## Main files

| Concern | File |
| --- | --- |
| Runtime blocker | [`ContentBlocker.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/ContentBlocker.kt) |
| Async process snapshot | [`BundledBlockingSnapshot.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/BundledBlockingSnapshot.kt) |
| First-load/restore gate | [`BlockingStartGate.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BlockingStartGate.kt) |
| Host lookup | [`RequestBlocker.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/RequestBlocker.kt) |
| URL/popup lookup | [`AdvancedFilterRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/AdvancedFilterRules.kt) |
| Procedural runtime | [`ProceduralCosmeticRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/ProceduralCosmeticRules.kt) |
| Generic cosmetic runtime | [`GenericCosmeticRuntime.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/GenericCosmeticRuntime.kt) |
| Rule validation/matching | [`CandyRule.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/CandyRule.kt) |
| Import/export/subscriptions | [`CandyRuleFormat.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/CandyRuleFormat.kt) |
