# Site data deletion

| Concern | Contract |
| --- | --- |
| Entry points | The security/connection sheet and `>Clear this site’s cookies & data` use the same command definition and confirmation dialog. The existing global **Clear cookies & reload** command retains its distinct scope. |
| Target | Capture tab, URL/origin/host, profile, privacy mode and navigation generation before confirmation. Reject a changed selection, page, profile or session. Cancellation does not delete or reload. |
| System WebView | Require `DELETE_BROWSING_DATA`. Call `WebStorageCompat.deleteBrowsingDataForSite` on the actual view's `Profile.webStorage`; use default `WebStorage` only for a regular, non-isolated context without `MULTI_PROFILE`. Private and isolated contexts require `MULTI_PROFILE`. |
| Site boundary | The WebView API expands an address to its registrable domain, including sibling/subdomains, all schemes/ports, network cache, cookies (including HttpOnly), JavaScript storage and embedded/partitioned storage. The dialog displays the captured host/origin and explains this larger boundary. Two ports on the same host are not independent sites. |
| Storage boundary | Isolated regular profiles and the private storage context remain separate. Regular profiles with isolation disabled share storage; System WebView private tabs also share one private runtime storage context. The dialog warns that other tabs/profiles sharing this context lose this site's data too. |
| GeckoView 157 | `GeckoSiteDataRuntime` lazily installs the APK-owned privileged `candy_site_data` host. Its parent-process API combines Mozilla's registrable-site resolver with explicit `geckoViewSessionContextId`, `privateBrowsingId` and default `userContextId` filters. Cookies use `getCookiesWithOriginAttributes`, which includes private cookies; matching site-owned and site-partitioned cookies are removed with their exact attributes. `nsIClearDataService.deleteDataFromSite` clears quota storage (including IndexedDB, localStorage, Cache API and service workers), sessionStorage and image/CSS/JS resource caches with the same filter. The public unscoped StorageController and WebExtension `cookieStoreId` APIs are never fallbacks. |
| Gecko HTTP cache | The network-cache site cleaner ignores origin-attribute filters in Gecko 157. Candy deliberately retains this cache and compression dictionaries instead of clearing another profile's cache. Cookies and website storage are reset; this operation is not a full network-cache purge. |
| Internal host boundary | No content scripts, host permissions, external messaging, webpage message listener or arbitrary code execution API. Only the authenticated background native port accepts requests. Context encoding mirrors the pinned Gecko AAR. Native requests are serialized, checked again before delayed dispatch, and fail on timeout or any incomplete deletion. Dispatched requests fail on disconnect; unsent requests wait for the new connection. A timed-out dispatched deletion keeps its slot until the uncancellable native operation settles. Reconnection and extension-version handshakes reject old upgrade ports. URLs and private context identifiers remain in memory only. The host is hidden from user extension management and excluded from System WebView-only APK assets. |
| Completion | Stop the originating browser load, wait for the native callback, then reload only the same unchanged originating page/session. Rejection or unsupported capability never invokes global deletion. Other live pages can write new data during/after the non-atomic native deletion. |
| Issue status | Issue 250 has implementations for supported System WebView providers and GeckoView 157, including isolated, shared-default and private Gecko storage contexts. |

## API evidence

| Source | Confirmed behavior |
| --- | --- |
| [AndroidX WebStorageCompat reference](https://developer.android.com/reference/androidx/webkit/WebStorageCompat) | Profile-bound storage, site expansion, supported data categories and completion callback. AndroidX WebKit 1.16.0 is used by Candy. |
| Resolved GeckoView `157.0.20260924084938` AAR, `assets/omni.ja` | `ext-toolkit.js` store mapping checks `privateBrowsingId`/`userContextId`, omitting `geckoViewSessionContextId`. `ext-browsingData.js` filters by that store and skips private quota deletion. `GeckoViewStorageController.sys.mjs` exposes host-only, domain-only and entire-context deletion. |
| Same resolved AAR, `Extension.sys.mjs` / `ExtensionCommon.sys.mjs` / `ClearDataService.sys.mjs` | Built-in APK extensions are privileged and may load a parent `experiment_apis` implementation. Site quota/resource cleaners accept origin-attribute patterns; network-cache site clearing does not. The generic CookieCleaner omits private cookies, so Candy uses the cookie manager's pattern-aware enumeration instead. `StorageController.createSafeSessionContextId` encodes named contexts as signed UTF-8 BigInteger hex with a `gvctx` prefix. |
| [Mozilla cookie manager IDL](https://raw.githubusercontent.com/mozilla-firefox/firefox/main/netwerk/cookie/nsICookieManager.idl) | `cookies` exposes regular cookies only; `getCookiesWithOriginAttributes` explicitly supports private cookies. |
| [Gecko extension store mapping](https://raw.githubusercontent.com/mozilla-firefox/firefox/main/toolkit/components/extensions/parent/ext-toolkit.js), [browsing-data implementation](https://raw.githubusercontent.com/mozilla-firefox/firefox/main/toolkit/components/extensions/parent/ext-browsingData.js), [native storage bridge](https://raw.githubusercontent.com/mozilla-firefox/firefox/main/mobile/shared/modules/geckoview/GeckoViewStorageController.sys.mjs) | Upstream implementations checked against the resolved AAR; upstream `main` alone is not a version pin. |

## Verification lookup

| Boundary | Tests |
| --- | --- |
| URL validation and immutable identity | `SiteDataRulesTest` |
| One command, captured target and async dispatch | `BrowserCommandRegistryTest`, `CommandDispatcherTest` |
| Controller completion, failure, stale navigation/session and selection | `BrowserControllerGeckoViewBindingInstrumentedTest#siteDataDeletion*` injects a controllable engine session; separate native tests cover actual storage deletion. |
| Supported, shared-storage, stale and unsupported confirmation | `SiteDataConfirmationDialogInstrumentedTest`, `PrivacyXRaySheetInstrumentedTest` |
| Real WebView site/profile/private cookie and storage boundaries | `WebViewSiteDataInstrumentedTest`: independent local hosts `127.0.0.1` and `127.0.0.2`; cookies, HttpOnly cookies, localStorage and IndexedDB seeded before deletion. |
| Real Gecko site/profile/private boundaries | `GeckoSiteDataInstrumentedTest`: regular and HttpOnly cookies, localStorage, sessionStorage and IndexedDB on independent local hosts; two isolated regular profiles, two private contexts and two profiles sharing default storage. Invalid URLs and changed-site requests cannot delete; closed sessions reject deletion. |
| Privileged API scope, failure and reconnect | `scripts/gecko_site_data.test.mjs`; `GeckoBrowserEngineAdapterTest`; `GeckoExtensionRulesTest`. |

## Runtime evidence

Captured from a dedicated API 36 emulator on 2026-10-04 with System WebView 133.0.6943.137 and the actual FullDebug app. The fixture stores real cookies, localStorage and IndexedDB; it does not reseed after reload.

| Check | Result |
| --- | --- |
| Full / Foss JVM suites | 1,789 tests each; no failures, errors or skips. |
| Full / Foss lint and debug assembly | Passed; FullDebug Android test APK also assembled. |
| Focused API 36 instrumentation | 17 passed: native storage (2), controller target/completion (6), confirmation (3), site sheet (4), profile/private boundaries (2). |
| Native storage after deletion | [Site A empty; independent Site B and other profile/private storage unchanged](images/site-data/native-sites-after-delete.png). A separate private-context deletion also preserved regular data. |
| Actual Candy deletion and reload | [Before](images/site-data/app-before-delete.png), [confirmation](images/site-data/app-confirmation.png), [after](images/site-data/app-after-delete.png). All three storage categories changed from stored to empty; the fixture server recorded a new page request. |
| Actual command palette | [One site command alongside the existing global command](images/site-data/palette.png); selecting the site command opened the same confirmation. |
| Historical GeckoView refusal (before the privileged implementation) | [Explanation and Cancel only](images/site-data/gecko-unsupported.png). Superseded by the Gecko implementation above. The old fixture omitted its charset declaration, causing mojibake in the background page. |
| Historical independent review | Code/API and captured screenshots reviewed; approved at that time as a WebView partial implementation. |

## Gecko verification (2026-10-16)

| Check | Result |
| --- | --- |
| Dedicated device | API 35 `codex_issue250_20261016`, explicit `emulator-5580` serial; no physical device used. |
| Native Gecko storage | 2 passed: isolated regular profiles, shared default storage, private contexts, Unicode context IDs, cookies including HttpOnly, localStorage, sessionStorage and IndexedDB. Other-site and other-context values survive; no fixture data is reseeded during inspection. |
| Confirmation and controller regression | 9 passed: confirmation/shared-storage scope, unsupported and stale targets, async success/failure, changed navigation, selected tab and replaced session. |
| Privileged API and reconnect | 6 Node tests passed, including site-partitioned cookies, explicit private/default context filters, partial failures, busy requests and reconnects during an active deletion. |
| Full / Foss JVM suites | 1,997 passed per flavor; no failures, errors or skips. |
| Lint and builds | Full/Foss lint, Full/Foss/System WebView debug APKs and FullDebug Android test APK passed. |
| Host upgrade | Controlled installation of host 1.0.3, then APK update to 1.0.4 without clearing app data; both native storage tests passed. |
| App smoke check | Opened the real Gecko site-information sheet on `https://example.com`, selected the site-data action, reviewed domain/shared-storage confirmation and confirmed deletion. The page reloaded and Candy displayed “Site data deleted and tab reloaded”. |
| Distribution boundary | Full/Foss APKs contain the internal host; the System WebView APK contains neither its assets nor Gecko's `libxul.so`. |
