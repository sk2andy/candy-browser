# Edge-to-edge safe-area audit

## Scope

This audit verifies that web content remains below the status bar and display cutout in Candy's
default safe mode. Website edge-to-edge rendering remains available as an explicit opt-in. The
audit uses the pinned first 100 domains from Tranco list
`PYG5J`, generated on 2026-08-06, plus Reddit at rank 102 because it motivated issue #2.

Infrastructure domains, DNS failures, redirects, and browser error documents remain in the fixed
cohort. They are not replaced. The assertion measures the attached WebView geometry before and
after a real scroll attempt; it does not claim that every target returned usable HTML.

## Environment

| Field | Value |
|---|---|
| Ranking | Tranco `PYG5J`, ranks 1–100; Reddit rank 102 |
| Emulator | Android API 36, 1080×2400 |
| WebView | 150.0.7871.181 |
| App locale | `de-DE` |
| Safe top inset | 63 physical pixels |
| Edge-to-edge websites | Off |
| Audited APK SHA-256 | `426b4d92d649d353e6e4044b3c159237897512da911e508bb3cb719d9849825f` |

The safe-area pass reuses one stable tab to avoid rotating and deleting an incognito WebView
profile for every domain. Popups are closed and the audit tab is explicitly reselected before each
measurement. HTML pages receive a bounded audit-only spacer and are checked through both DOM and
native WebView scrolling. The one XML response is geometry-checked without a DOM scroll probe.
Any unsafe or unmeasured layout fails the instrumentation run.

## Results

| Check | Result |
|---|---:|
| Fixed ranks recorded | 100/100 |
| Safe-area geometry passed | 100/100 |
| Unsafe layouts | 0 |
| Unmeasured layouts | 0 |
| HTML scroll probes | 99 |
| Non-HTML geometry-only probes | 1 |
| Main-frame errors retained in cohort | 34 |
| Loading timeouts retained in cohort | 1 |
| Reddit geometry and scroll | Passed |

Reddit finished at `https://www.reddit.com/`. Its WebView top remained at 63 px while native
WebView scroll advanced from 0 to 2625 px.

| Result artifact | SHA-256 |
|---|---|
| `sites-safe-area-1-100.jsonl` | `9ed545ceb62b03a4484fc82396dd9cc6d52f369ee89e7647eb4ea84b56ea5ff5` |
| `sites-safe-area-102-102.jsonl` | `6b3d25d2742fcac73a4459802ca9818c81388673269866738cd8bd47d1623898` |

The hashes above describe the pulled audit artifacts in `build/top-site-audit/safe-area/`.

## Pixel 8 verification

The follow-up candidate changes only the missing-preference default from edge-to-edge to safe mode;
the audited safe-mode layout path is unchanged. Version 17 (`0.1-edgefix2-debug`) was installed over
the existing dedicated Pixel app without clearing its Reddit tab or browsing data.

| Check | Result |
|---|---:|
| Installed APK SHA-256 | `130f08c0178071371388f4e2444680372b3e1abf6bcec45f981dee8eb40ce734` |
| Reddit viewport declaration | `viewport-fit=cover` |
| Reddit CSS safe-area inset | 0 px in safe mode |
| WebView window top | 132 physical px |
| Display cutout/status-bar safe top | 132 physical px |
| `App öffnen` window top | approximately 159 physical px |
| Visual result | Control fully below the system bar |

The page still declares `viewport-fit=cover`, but its fixed header does not consume the CSS safe-area
inset. Default safe mode therefore positions the entire WebView below the cutout instead of trusting
the declaration as a compatibility guarantee.
