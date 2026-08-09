# Arc Search advertising parity audit

## Scope

This audit compares Candy's production `BrowserController` with Arc Search on the same Android
emulator. It focuses on the two reported failures: sponsored Google results and promoted Reddit
posts. Deterministic local fixtures cover the corresponding DOM structures without depending on
live ad inventory.

The result is scoped to the scenarios below. It is not a claim that a finite live sample proves
correct behavior on every website.

## Inputs and integrity

| Input | Version | SHA-256 / signer | Result |
|---|---|---|---|
| Supplied single APK | Arc Search 1.12.0 (81) | APK `28ae20d9b776f6535b569f1ffd93cad6bdade5c91025a4fb5045e5ca258ee5da`; AOSP test-key signer `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc` | Not used for live comparison: required ABI/density splits and `libadblock_jni.so` are absent |
| Play-installed Arc split set | Arc Search 1.12.9 (94) | Base APK `05ce7a8ad29863ba0195dea68c2afc09bfc48d974c9a280915e58cf156d7b661`; Google Play signer `ba0887a3b42cd6cb5a807b6ba927528486e3ea73c7502d5699bf8227911021b5` | Installed successfully and used as comparator |
| Final Candy candidate | Source base `06987a12fb4181ce78fe76e64307330215fc1199` plus the changes documented below | Debug APK `8738178881af263cfbb6090d65e91237469d9156145f31e8d14c71487b4487fe` | Installed successfully; deterministic fixtures and multilingual live follow-up completed |

The official Arc split hashes were: arm64-v8a
`3c1daed3858aae825b59426eef4b6a9c2753735c53d0af444365dc5269a24c2c`, German
`eb6e669afe4b4b8f0e1905ccd7a9e30442a435ce9406842d69a6af72cfe3f94f`, English
`de85e52686b3a2f0b702875a2889ea4af365da03a3b71527ac5a6e6a85f0c08d`, and xxhdpi
`166617aa05d62fe3a82d5159728b14955cc4d4c412a65fc2a26aeadd00bbe3bb`.

## Environment and method

| Field | Value |
|---|---|
| Emulator | `Medium_Phone_API_36.1`, `emulator-5554`, 1080×2400 |
| Android | 16 / API 36.1 |
| System WebView | 134.0.6998.135 |
| Locale / time zone | German / Europe-Berlin |
| Arc setting | Block ads enabled during onboarding |
| Consent | Optional cookies rejected in both browsers |
| Evidence | Screen inspection plus Android accessibility hierarchy |

Each live scenario opened the same URL in Arc and Candy. A run was considered usable only when
organic content rendered. Residual advertising required an explicit `Sponsored`, `Promoted`,
`Advertisement`, `Anzeige`, or equivalent Google label; vendor names alone did not count. Reddit
was checked at the initial viewport and three Candy scroll checkpoints.

## Live results

| Scenario | Arc Search 1.12.9 | Candy candidate | Organic content |
|---|---|---|---|
| Google `hotel berlin` | `Gesponserte Hotels` and `Gesponserte Ergebnisse` visible; Booking.com sponsored result visible | No sponsored/advertising label or sponsored result container visible | Present in Candy: hotel module plus organic KAYAK/HolidayCheck results |
| Google `kredit vergleich` | `Anzeigen` and `Gesponserte Ergebnisse` visible; CHECK24, Smava, Verivox, and ING ad results visible | No sponsored/advertising label or ad explanation visible | Present in Candy: organic Finanzcheck, Check24, Tarifcheck, and Consors results |
| Reddit `/r/popular/` | A complete post exposed as `Advertisement: …`, author `u/battlefield`, and `Promoted` | No `Promoted`, `Advertisement`, or `Gesponsert` node at initial view or three scroll checkpoints | Normal posts and post actions remained accessible |

For these complaint-driven live scenarios, Candy has zero residual labelled ad units while Arc has
at least one in every scenario.

The broader follow-up covered 27 distinct scenarios in ten languages/regions. Candy also removed
Arc-visible Amazon, Interia, Coupang, and UOL units. Naver retained the same labelled user clip in
both browsers while Candy removed the surrounding platform ad frames. Full evidence, invalid-run
rules, and remaining gaps are in [multilingual-ad-blocking.md](multilingual-ad-blocking.md).

## Root cause

| Layer | Arc Search sample | Candy before this change | Effect |
|---|---|---|---|
| Network lists | EasyList plus EasyPrivacy through Brave `adblock-rust` | Compiled EasyList/EasyPrivacy host subset plus uAssets | Both block many third-party requests; neither network layer alone removes first-party inline Google/Reddit ads |
| Cosmetic ads | EasyList contains the needed selectors, but the inspected Arc integration does not call its cosmetic API | Only 20 advertising selectors; Google absent and Reddit limited to legacy `.ad-link-bar` | Sponsored containers remain visible |
| CSS failure isolation | Not applicable to the separate Arc cookie stylesheet | All selectors were grouped into one stylesheet payload | One malformed selector could prevent later valid rules from applying |

The supplied Arc 1.12.0 asset contains 63,039 EasyList lines, 53,595 EasyPrivacy lines, and a
12,010-line banner stylesheet. The banner stylesheet is consent-oriented. Static inspection found
the relevant Google and Reddit selectors in EasyList, but no cosmetic-engine call in the inspected
integration. Arc passes `*` as the request type to its native network checker, which is treated as
`Other` by the bundled `adblock-rust` version; resource-type-only rules therefore cannot all match.

## Candidate behavior

Candy now compiles 16,497 domain-specific standard-CSS hides and 652 exceptions from pinned,
current EasyList/EasyPrivacy source templates. A separate GPL uAssets asset adds 2,002 hides and
50 exceptions. Cross-source deduplication and exception handling produce 19,189 unique records:
18,487 hides and 702 exceptions. The deterministic assets retain per-rule domain exclusions and
wildcard TLD patterns without entering user-rule limits. Generic rules and procedural/extended
operators are skipped instead of approximated.

Kotlin resolves indexed rules for the navigation host before WebView registration. Asset parsing
and indexing run off the main thread; curated rules bridge startup, then current WebViews receive
the compiled exact-origin document-start script plus fallback. Result is a small per-site payload,
not a 19,189-record JavaScript scan on every page or a multi-second first-navigation UI stall.
`www.google.*` uses Guava's ICANN registry-suffix data, matching regional boundaries such as
`.com`, `.fr`, `.co.kr`, and `.com.sg`, but not lookalikes such as `www.google.evil.com` or
`www.google.com.de`. Bundled cosmetics are deliberately disabled for Google Mail, Maps, and
Accounts hosts. Reddit old/new promoted-post selectors come directly from EasyList.

Each selector is inserted independently with `CSSStyleSheet.insertRule`. Invalid selectors are
ignored individually; they no longer disable every later rule for the page. Organic fixture nodes
must remain visible.

Known GET targets are registered before navigation and explicit URL ports are preserved. Android
WebView does not surface cross-origin form POST targets before document start, so those navigations
use the page-commit fallback and may briefly display cosmetic content before the stylesheet lands.

## Verification

| Gate | Result |
|---|---:|
| Cosmetic compiler tests | 10 passed, 0 failed |
| Generated EasyList cosmetic asset | SHA-256 `ab43ad60aa6ed0b160573b3316b069edaa843b7fb191e3c378b8712334e4c544` |
| Generated uAssets cosmetic asset | SHA-256 `07c06a144ef5e3f2b0697a316e676226d9053fe8a6f24c389ef550b97a00e78d` |
| JVM unit tests | 477 passed, 0 failed |
| Lint | Passed |
| Relevant asset/cosmetic instrumentation | 16 passed, 0 failed on `Medium_Phone_API_36.1` |
| Full blocking instrumentation package | 25 passed, 0 failed; 1 opt-in top-site audit skipped |
| Google deterministic fixture | Static `#tads`, `#google-s-ad`, and dynamic `data-is-ad=1` hidden; organic result visible |
| Reddit deterministic fixture | `shreddit-ad-post`, promoted tracking container, and advertisement container hidden; organic post visible |
| Amazon deterministic fixture | static, featured, and dynamic sponsored products hidden; organic product visible |
| Multilingual deterministic fixtures | Interia, Corriere, Naver, Coupang, and UOL ad containers hidden; organic controls visible |
| Malformed-selector fixture | Invalid selector ignored; following valid selector still hides its target |
| Scoped payload guard | Google `.fr` script stays below 64,000 characters; compiler-declared worst host has 85 selectors and stays below 128,000 characters; Mail payload is empty |

The existing top-300 report is not used as Arc parity evidence: it opens only domain roots, has no
Arc pass, and does not exercise Google result pages or a scrolled Reddit feed.
