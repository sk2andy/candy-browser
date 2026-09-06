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
| Runtime interception | `BrowserController` | Combine site settings, bundled lists and Candy Rule decisions for the active renderer |

Scoped cosmetic document-start rules run in every frame whose origin matches the registered page or
user-rule origin. Generic cosmetics use a Candy-owned token scanner in the top document and its
same-origin frames; cross-origin frames remain outside that boundary and rely on request blocking.

Bundled advanced rules support bounded host-anchored paths, `*` wildcards, `^` separators,
positive/negative `domain` scopes, first/third-party scopes, allow exceptions, `$popup`, and
`$popunder`.
Popup rules inspect HTTP(S) main-frame targets created by `onCreateWindow`; automatic non-gesture
windows remain rejected by existing WebView policy. A same-site first target stays monitored for a
bounded five-second window so a delayed cross-site advertising hop cannot bypass policy.
Site pause, profile, and private-tab ownership come from actual opener. Cross-site windows without a
matching rule honor the user gesture and open normally; explicit popup rules still block matching
targets. A per-domain **Always block pop-ups** override rejects every `onCreateWindow` request from
the registrable opener domain before Candy creates a popup tab. This strict override is independent
of site pause, filter allow rules, and federated-login compatibility, and never offers an **Open**
action. Regular tabs persist it per profile; private tabs keep it in memory until the private session
ends. Popunder rules use a bounded five-second correlation window between the surviving child and a
redirected opener, then close the listed opener. Regex filters, redirects, `$important`, arbitrary
JavaScript, and trusted uBO scriptlets fail closed.

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
Five Candy-owned native CSS rules additionally cover high-confidence leftovers. Four collapse media
with a bounded `ads_banner` filename token; one collapses an `#AlternateMessage` fallback only when
it directly follows `#ad_banner`. They add no request matching or DOM scanning. Ambiguous editorial
examples, banner documentation, and unrelated alternate-content elements remain visible.

Candy's host-scoped consent defaults remove Reddit's current data-protection consent dialog and its
temporary body scroll lock. This native fallback applies in Gecko even when an installed third-party
consent extension ships an outdated Reddit rule or cannot inject CSS through the legacy
`tabs.insertCSS` API.

Network host matching combines Candy's curated hosts, the complete supported EasyList/EasyPrivacy
template graph, uAssets, and a deduplicated HaGeZi Pro delta. Sorted byte indexes keep the larger
bundles off the per-request allocation path. Curated owner-family exceptions may allow a blocked
service only on a PSL-validated family such as `google.*`; lookalike suffixes do not match. User
Candy allow rules and site pause still take precedence over bundled blocking.

### GeckoView host filtering

Android Gecko sessions install the private bundled `candy-privacy-host` extension before any
external navigation. Its synchronous `webRequest.onBeforeRequest` listener mirrors all bundled host,
host-pair, allow-pair and first-party-family assets, advanced request URL rules, the curated consent
request rule, and the active profile's Candy host rules. A document-start content script applies
host-scoped EasyList/uAssets selectors, Candy defaults, active profile Candy cosmetic rules, and the
bounded procedural subset. BrowserController republishes the complete per-tab policy when Candy
rules, protection settings, private rules, or site pause change. A policy revision is acknowledged
before a dependent navigation or reload continues.

Candy allow rules are authoritative, so Gecko's own tracking-protection classifier is disabled for
these sessions. Gecko Safe Browsing remains enabled. The global third-party-cookie switch changes
the process runtime between `ACCEPT_FIRST_PARTY` and `ACCEPT_ALL` for normal and private sessions.
The host also observes only the recognized SSO and CAPTCHA request hosts, including requests that
arrive before a just-published policy update; native blocking state, HTTPS host/path, current-page,
policy-revision direction, and public-suffix validation drives the existing consent flow.
GeckoView 140 has no site-scoped override for hard `ACCEPT_FIRST_PARTY` rejection. After consent,
Candy therefore switches the shared runtime to `ACCEPT_ALL` only while the matching page is the
selected session. Normal and private modes remain separate. Candy restores `ACCEPT_FIRST_PARTY`
before cross-host main-frame navigation and on deactivation, revocation, or close. Inactive
same-mode sibling sessions share the runtime. Candy marks them inactive through Gecko's session
lifecycle during that bounded compatibility window, though GeckoView does not specify that as a
complete network suspension.
Block and allow decisions are returned to native code in bounded batches for the existing hit counts
and Privacy X-Ray pipeline. The built-in host is hidden from the user extension manager and is
explicitly enabled in private browsing; ordinary installed Firefox extensions retain their separate
permission policy.

The first external load is fail-closed until the extension assets, private permission, policy, and
session binding are all acknowledged. Initialization, policy, and bootstrap binding each have a
15-second bound. A timeout, asset failure, or post-initialization native-port disconnect emits a
failed navigation with a Candy Privacy error instead of loading without protection. A restart caused
by initially applying the private permission may reconnect within the same bounded initialization
window. Session binding uses two acknowledgements: native first authenticates the exact extension,
session and token generation and returns a primitive response; only after the extension page receives
that response does its background port confirm the binding. Candy posts the real page navigation only
after this confirmation. Navigating synchronously from the message delegate can tear down Gecko's
extension-page actor before the native response is delivered. Policy revisions may overtake an
in-flight bootstrap, but an authenticated older revision can only bind the same live generation; the
external-load gate still waits for both that session binding and the latest policy acknowledgement.

The Gecko host deliberately excludes generic, unscoped cosmetic selectors: only host-scoped rules
run in page content, and sensitive-host exclusions plus upstream exceptions remain authoritative.
Popup/popunder rules, the `window.open` defuser, generic token-scanned cosmetics, and consent DOM
handling still need separate Gecko adapters before the Gecko path has the complete WebView
protection surface.

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
| Gecko host contract/runtime | [`CandyPrivacyHostContract.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/gecko/CandyPrivacyHostContract.kt), [`GeckoPrivacyHostRuntime.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/gecko/GeckoPrivacyHostRuntime.kt) |
| Async process snapshot | [`BundledBlockingSnapshot.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/BundledBlockingSnapshot.kt) |
| First-load/restore gate | [`BlockingStartGate.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BlockingStartGate.kt) |
| Host lookup | [`RequestBlocker.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/RequestBlocker.kt) |
| URL/popup lookup | [`AdvancedFilterRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/AdvancedFilterRules.kt) |
| Procedural runtime | [`ProceduralCosmeticRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/ProceduralCosmeticRules.kt) |
| Generic cosmetic runtime | [`GenericCosmeticRuntime.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/GenericCosmeticRuntime.kt) |
| Rule validation/matching | [`CandyRule.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/CandyRule.kt) |
| Import/export/subscriptions | [`CandyRuleFormat.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/blocking/CandyRuleFormat.kt) |
