# Site data deletion

| Concern | Contract |
| --- | --- |
| Entry points | The security/connection sheet and `>Clear this site’s cookies & data` use the same command definition and confirmation dialog. The existing global **Clear cookies & reload** command retains its distinct scope. |
| Target | Capture tab, URL/origin/host, profile, privacy mode and navigation generation before confirmation. Reject a changed selection, page, profile or session. Cancellation does not delete or reload. |
| System WebView | Require `DELETE_BROWSING_DATA`. Call `WebStorageCompat.deleteBrowsingDataForSite` on the actual view's `Profile.webStorage`; use default `WebStorage` only for a regular, non-isolated context without `MULTI_PROFILE`. Private and isolated contexts require `MULTI_PROFILE`. |
| Site boundary | The WebView API expands an address to its registrable domain, including sibling/subdomains, all schemes/ports, network cache, cookies (including HttpOnly), JavaScript storage and embedded/partitioned storage. The dialog displays the captured host/origin and explains this larger boundary. Two ports on the same host are not independent sites. |
| Storage boundary | Isolated regular profiles and the private storage context remain separate. Regular profiles with isolation disabled share storage; System WebView private tabs also share one private runtime storage context. The dialog warns that other tabs/profiles sharing this context lose this site's data too. |
| GeckoView 157 | Site deletion is unavailable. Public host/base-domain deletion lacks Candy's session-context filter; public context deletion clears every site. WebExtension `cookieStoreId` omits `geckoViewSessionContextId`, so distinct Candy profiles collapse to the same store. Private IndexedDB/localStorage deletion is a no-op in that extension API. Neither path is used as a fallback. |
| Completion | Stop the originating WebView load, wait for the native callback, then reload only the same unchanged originating page/session. Rejection or unsupported capability never invokes global deletion. Other live pages can write new data during/after the non-atomic native deletion. |
| Issue status | Issue 250 remains incomplete while Gecko cannot safely combine site and Candy profile boundaries. Do not mark it resolved based on WebView support alone. |

## API evidence

| Source | Confirmed behavior |
| --- | --- |
| [AndroidX WebStorageCompat reference](https://developer.android.com/reference/androidx/webkit/WebStorageCompat) | Profile-bound storage, site expansion, supported data categories and completion callback. AndroidX WebKit 1.16.0 is used by Candy. |
| Resolved GeckoView `157.0.20260924084938` AAR, `assets/omni.ja` | `ext-toolkit.js` store mapping checks `privateBrowsingId`/`userContextId`, omitting `geckoViewSessionContextId`. `ext-browsingData.js` filters by that store and skips private quota deletion. `GeckoViewStorageController.sys.mjs` exposes host-only, domain-only and entire-context deletion. |
| [Gecko extension store mapping](https://raw.githubusercontent.com/mozilla-firefox/firefox/main/toolkit/components/extensions/parent/ext-toolkit.js), [browsing-data implementation](https://raw.githubusercontent.com/mozilla-firefox/firefox/main/toolkit/components/extensions/parent/ext-browsingData.js), [native storage bridge](https://raw.githubusercontent.com/mozilla-firefox/firefox/main/mobile/shared/modules/geckoview/GeckoViewStorageController.sys.mjs) | Upstream implementations checked against the resolved AAR; upstream `main` alone is not a version pin. |

## Verification lookup

| Boundary | Tests |
| --- | --- |
| URL validation and immutable identity | `SiteDataRulesTest` |
| One command, captured target and async dispatch | `BrowserCommandRegistryTest`, `CommandDispatcherTest` |
| Controller completion, failure, stale navigation/session and selection | `BrowserControllerGeckoViewBindingInstrumentedTest#siteDataDeletion*` injects a controllable engine session; separate native tests cover actual storage deletion. |
| Supported, shared-storage, stale and unsupported confirmation | `SiteDataConfirmationDialogInstrumentedTest`, `PrivacyXRaySheetInstrumentedTest` |
| Real WebView site/profile/private cookie and storage boundaries | `WebViewSiteDataInstrumentedTest`: independent local hosts `127.0.0.1` and `127.0.0.2`; cookies, HttpOnly cookies, localStorage and IndexedDB seeded before deletion. |

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
| Actual GeckoView refusal | [Explanation and Cancel only](images/site-data/gecko-unsupported.png). This proves refusal, not Gecko deletion. The local fixture omitted its charset declaration, causing mojibake in the background page; the product dialog text is correct. |
| Independent review | Code/API and captured screenshots reviewed; approved as a WebView partial implementation. Issue 250 remains incomplete. |
