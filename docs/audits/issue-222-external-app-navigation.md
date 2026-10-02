# Issue 222: Search-result external-app navigation audit

Issue: [Reddit links](https://github.com/sk2andy/candy-browser/issues/222).

## Evidence and cause

| Observation | Conclusion |
| --- | --- |
| Live Brave Search results for the reported query expose direct Reddit HTTPS anchors with `_self` targets | No search redirector or native popup is required for this symptom |
| The same result opens in a comparison Chromium browser using a mobile Gecko user agent | The inspected anchor and target are reachable outside Candy; this is not a native Gecko comparison |
| Brave Search updates its source URL with a `conversation` query parameter while remaining on the same document | Asynchronous source URL changes are a relevant input to Candy's source guard |
| Real Gecko fixture: tap a cross-site link, hold app lookup, call source `history.replaceState`, then release lookup | Unchanged production denies the target, then stays on the updated source URL; native history contains only the source |
| Four baseline controls without a source History API update | Ordinary taps, delayed repeated taps/Back, browser-fallback `_blank`, and Always ask pass |

Candy's Automatic mode denies the native request while discovering installed non-browser app
handlers. Without a handler, it must load the link in Candy. The previous completion guard compared
both the displayed source URL and a navigation generation also used by same-document safe-area
updates. A source History API update made that snapshot stale, so completion discarded the already
denied link without resuming navigation.

The controlled failure reproduces the reported loading-then-search-page symptom. It establishes a
Candy defect, but the original Galaxy Z Fold6 and its exact tap timing were not tested. No physical
device was used. The latest published v0.44.0 also predates issue 221's separate native-popup fix;
this audit does not attribute direct `_self` anchors to that popup defect.

## Fix boundaries

| Boundary | Enforcement |
| --- | --- |
| Same document changes its URL | Keep pending Automatic lookup; native History API preserves document identity |
| New document starts | Increment a dedicated document generation; reject old completion |
| New main-frame request | Increment the request revision already guarding asynchronous De-AMP replacement |
| Address, Back, Forward, Reload, Retry, Candy Trail, extension reload or Stop | Invalidate request revision before issuing the native command |
| Session, selected tab, profile or private surface changes | Retain existing exact-session and surface checks |
| Link Peek and confirmation prompts | Retain their existing strict snapshots |
| Session removal | Remove document/request generation bookkeeping |
| Browser fallback | Retain existing bounded grant, popup and privacy behavior |

## Verification

Dedicated API-36 emulator `codex_issue222_root`, serial `emulator-5580`, GeckoView 157.
The regression uses real native page taps and callbacks. A latch controls Android app lookup;
executor/main-thread completion markers make stale-request assertions deterministic.

| Check | Result |
| --- | --- |
| Baseline History API regression | Failed: denied target never resumed |
| Baseline controls | 4/4 passed |
| Fixed native navigation and cancellation regressions | 7/7 passed across focused runs; History API regression also passed in the final APK |
| Existing controller handoff, private popup, prompt and stale-surface regressions | 11/11 passed |
| Existing controller De-AMP replacement regression | Passed independently with isolated app-handler setup |
| Full/Foss unit tests | 1,724 passed per variant; no failures, errors or skips |
| Full/Foss debug assembly and Full test APK assembly | Passed |
| Full/Foss lint, serial `--max-workers=1 -Dlint.useK2Uast=false` | Passed, including final fixture setup |
| Independent code/style review | No actionable findings |

The initial cancellation fixtures made two unsupported assumptions: Stop would also normalize
transient denied-target UI state, and a replacement document would load while the serial app lookup
was still held. The final tests check retained native source history/no target request after Stop,
and observe the newer native request before releasing lookup and awaiting the replacement document.
No production routing was bypassed.

The existing De-AMP fixture inherited Automatic external-app handling but asserted Allow for
unrelated cross-site requests. Both baseline and fixed production return Deny for that mode before
lookup. Its setup now saves/restores the setting, uses Always ask and injects unavailable app
resolution so the test isolates De-AMP replacement and cancellation.

The initial parallel lint attempt crashed internally in settings/player test analysis. The serial
rerun passed without disabling checks. Verification includes other ongoing local changes in the
shared checkout; the commit contains only this issue's controller, fixture and documentation changes.
