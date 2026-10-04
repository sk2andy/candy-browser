# Issue 278: REWE loading and Cover header continuity

## Evidence and scope

| Item | Observation |
| --- | --- |
| Issue | [#278](https://github.com/sk2andy/candy-browser/issues/278) reports an older Google-logo tap with System WebView; this is separate from the current Gecko reproduction |
| User recording | REWE initial-load content moves below the status bar; the supplied clip is approximately four seconds |
| Current live reproduction | `https://www.rewe.de/`, GeckoView 157, Candy 0.45.1-debug, dedicated API 34 emulator `codex_issue278_root`, serial `emulator-5582` |
| Page policy | Live page declares `width=device-width, initial-scale=1, viewport-fit=cover` |
| Before fix | One captured trusted menu tap produces header rect tops `49.0667 → 0 → 49.0667` CSS px while `scrollY` remains zero and body padding remains zero. Later settled-page taps can complete the rebuild between rendered frames and do not always show the jump |
| User clarification | The supplied reproduction concerns loading REWE, rather than opening its menu. The earlier menu fix addressed an additional case and did not establish that the loading case was fixed |
| Loading reproduction | Navigating from the blank start page through the address editor paints REWE's header behind the status bar, then shifts it down. A 30 fps video analysis finds the first red header band at native y zero before later reaching y 128 |
| Loading cause and fix | Cover semantic-header discovery rejected `readyState=loading`. SVG-first header flow also receives no body padding, so the sticky header remained unprotected until parser completion. Loading body protection now runs the existing bounded semantic-header classifier synchronously; loading class/style promotions recheck immediately instead of waiting for the quiet worker |
| Loading result | `output/issue278/rewe-page-load-after.mp4` includes blank start, URL entry and the complete visible page load. Every detected header frame starts at native y 128, including its first appearance; the earlier recording is retained as `rewe-page-load-before.mp4` |
| Protection continuity | A relevant Cover DOM/class revision removes Candy's protection sheets and resets the worker even when the effective protection decision stays unchanged |
| REWE scroll-lock cause | On the first menu opening, REWE changes its body from static to fixed at top zero. The sticky header's computed top remains `49.0667px`, but its rect drops to zero. The debounced worker protects the fixed body roughly 300 ms later, restoring the header's visible inset |
| Continuity fix | Restore the sheets after the synchronous author-layout probe; exclude the DOM revision from the effective configuration key; retain ownership while refreshing body protection, semantic discovery and backdrop |
| Scroll-lock fix | In the existing trusted-interaction and changed-element gates, preserve already sufficient body padding; otherwise flush the body's authored geometry and classify that one box before the next paint, using the existing temporary stylesheet-disable helper. A child offset already supplied by Candy must not be mistaken for authored protection. If the engine retains the child's sticky offset and the new body rule would move already-safe content farther down, release that new body top rule. Descendant discovery stays debounced |
| Live result | Four trusted menu clicks after a fresh app launch retain header rect `49.0667px` in every sampled frame, including the first opening; body padding and document scroll remain zero |
| Clean demonstration | Final FullDebug build installed on `emulator-5582`; four menu taps recorded after a clean app-data launch without the diagnostic Topping. `output/issue278/rewe-after.mp4` shows the stable header through the first opening and repeated closing/opening |
| Limits | The original reported Google/System WebView environment is not established as the same reproduction. A new document can render before its asynchronous extension policy arrives; the loading fix guarantees protection once that policy is available. Raw first-frame geometry remains recorded by the native test. Existing retained author-top/padding and bounded discovery limits remain documented in the runtime guide |

## Verification

| Check | Result |
| --- | --- |
| Node shared prototype, Reddit helper and retained classifier suites | 130 passed, including preservation of already sufficient Candy body padding during scroll locking |
| Node regression before fix | Unchanged Cover class change detached the protection stylesheet |
| Node regression after fix | Preserves connected stylesheet, rule and marker identity across changed classes and inserted navigation; discovers newly unsafe body flow; immediately protects a scroll-locked body while retaining interaction/settings gates |
| Node scroll-lock regression before immediate body fix | Fails before the worker runs: expected body top `32px`, observed `0px` |
| Node loading regressions before startup fix | Both existing SVG-first sticky headers and parser-time class promotions fail before a worker runs: expected header top `32px`, observed `0px` |
| Gecko prototype instrumented suite | 12 passed on API 34, including five repeated class changes with per-frame geometry and stylesheet-removal checks, four trusted scroll-lock clicks, and a parser blocked before DOM readiness. The parser fixture records first/min/max policy-ready header top `52.5167px` before and after completion with zero body padding; raw first-frame geometry and pre-policy samples are separate diagnostics |
| Corrected device regression against original source | Fails: existing protection stylesheet removed; expected zero removals, observed one |
| System WebView safe-area instrumented suite | 8 passed on the same isolated emulator; repeated SVG-first sticky-header scroll locking without a duplicate inset, Cover/non-Cover, native env delivery, disabling/re-enabling, native ownership, private session and negative-top lifecycle |
| Build variants | `assembleFullDebug assembleFossDebug` passed |
| Independent code review | Existing active, interaction-window and changed-element gates retained; one body classification and bounded geometry reads per mutation batch. Duplicate-offset verification prefers the first eight cached semantic header candidates, so a hidden portal preceding the header does not mask working child protection. No new scroll work or host-specific exception |

The regression fixture uses the minimum cooperative worker batch/time settings and
additional positioned anchors. It checks every rendered frame, no unexpected scrolling,
and retention of the existing CSSOM protection sheet. A separate SVG-first sticky-header
fixture checks four trusted menu clicks, body scroll locking, every rendered header frame,
and preservation of zero body padding and document scroll. The live site measurement comes
from a temporary local Topping on the isolated emulator; it is not shipped.
