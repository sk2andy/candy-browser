# Issue 304: Reddit navigation and concurrent app lookup

Issue: [Reddit still broken](https://github.com/sk2andy/candy-browser/issues/304).

## Evidence and cause

| Observation | Conclusion |
| --- | --- |
| Reported Galaxy Z Fold6 / Android 17 / Gecko recording shows Reddit taps briefly loading, then retaining Brave results | A denied link without its browser fallback reproduces the visible symptom |
| The inspected mobile Brave results for Evie Launcher expose direct Reddit HTTPS anchors with `_self` targets | No search redirector is required for the inspected links |
| Baseline native HTTP 302 and slow-source direct-link fixtures both pass | Neither eager redirect rollback nor tapping before the source finishes establishes this report's cause |
| Controlled controller regression holds the selected Reddit lookup, then delivers an already-authorized redirect from another session | The selected link is denied and never receives its fallback Load command |
| Completion diagnostics show selected request 1 rejected because background request 2 becomes the globally latest lookup | A shared request counter incorrectly couples independent sessions |
| A single live Brave-to-Reddit click loads the Reddit post on the dedicated emulator before the fix | The exact Fold6 reproduction is not established; concurrent cancellation is a confirmed Candy defect with the same visible outcome |

The Automatic mode checks installed app handlers asynchronously after denying the native link.
Its completion must either launch an app or resume the web URL while the source remains current.
Previously every session shared one latest-request ID. A background session could start a valid
redirect lookup after a tab switch and overwrite that ID. The selected lookup was discarded even
though its exact session, selected tab, document and request generations remained valid. The
background lookup subsequently failed its own foreground-source guard, leaving both denied
requests without a browser fallback.

## Fix boundaries

| Boundary | Enforcement |
| --- | --- |
| Concurrent tab or external-preview lookups | Keep the latest request ID independently per policy tab |
| Repeated lookup from the same source | A unique controller sequence prevents an older completion matching a later request |
| A newer document/request, Stop, navigation, profile/private surface or exact session change | Retain existing source guards and cancellation |
| Completion, explicit navigation, session close, preview release or controller destruction | Remove the pending lookup entry |
| Background source completion | Retain the foreground check; never launch an app or replace another tab from stale background work |
| Browser fallback, popup and redirect authorization | Retain existing bounded grants and routing |

## Verification

Dedicated API-35 emulator `codex_issue304_20261013`, explicit serial `emulator-5584`, GeckoView 157.
The concurrent-session regression uses the real controller, an injected engine port and a gated
Android app resolver. A marker on its serial executor posts behind both completions on main, so
assertions cannot race pending callbacks. The native controls use real Gecko taps, callbacks,
HTTP requests and history; the source-loading control holds an image response while the link is
already visible.

| Check | Result |
| --- | --- |
| Baseline concurrent-session regression | Failed: expected selected Reddit Load; observed no Load |
| Baseline native HTTP redirect and slow-source controls | 2/2 passed |
| Fixed native and controller navigation checks | 20/20 passed: 9 real Gecko controls and 11 controller checks, including the concurrent-session regression |
| Full/Foss unit tests | 1,936 passed per variant; zero failures, errors or skips |
| Full/Foss debug and Full test APK assembly | Passed |
| Full/Foss lint, serial `--max-workers=1 -Dlint.useK2Uast=false` | Passed |
| Independent code/style review | No actionable findings |

The original Fold6 and Android 17 were not tested. This change fixes the demonstrated cross-session
cancellation rather than claiming all possible Reddit/network/extension failures are resolved.
Temporary investigation logs were removed from production before final verification.
