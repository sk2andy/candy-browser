# Gecko OS Back versus Link Peek

## Reproduction and cause

| Case | Baseline (5c78b8b0) | Evidence |
| --- | --- | --- |
| Left edge Back over a link, hold before release | Link Peek opens during hold | Real OS input; Gecko receives DOWN then CANCEL |
| Right edge Back over a link, hold before release | Link Peek opens during hold | Same native cancellation trace |
| Start Back over a link, hold, then cancel gesture | Link Peek opens during hold | Same native cancellation trace |

The fixture serves a full-screen link from a local HTTP server. Android UiAutomation injects
screen touch events; it does not invoke Candy's Back handler or synthesize content-target
callbacks in these three tests. The history fixture waits for the engine's actual back-history
callback before starting input. It also waits for Compose idle and a screenshot pixel from the
source page to distinguish it from the previous page. API 37 uses gesture navigation on an
exclusively owned emulator.

A diagnostic run proves that Android rewrites natural CANCEL timestamps: the root-page DOWN
identity was `1669014`, while CANCEL arrived with downTime and eventTime both `1669117`.
Matching cancellation to the supplied timestamp therefore missed a real OS cancellation.
[AOSP ViewGroup](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewGroup.java)
also creates synthetic CANCEL events with both timestamps set to the current uptime.

A right-side committed gesture exposed a second Candy defect during cold navigation. Temporary
logs proved that Android committed WebHistory, the controller sent Back, and Gecko reported
canGoBack=true while Candy's cached history still contained only `about:blank`. The cached-index
guard returned without issuing Gecko's native Back command. A later real System Back key worked. Native Back/Forward is now used for ordinary history; explicit index
overrides remain limited to snapshots containing internal bootstrap entries.

The final fixture holds at 28%, then continues the real gesture to 55% before release. History
commit remains mandatory. Temporary diagnostic Back-key actions and production logging were
removed before final verification.

## Changes

| Boundary | Contract |
| --- | --- |
| Native Gecko touch | Observe every DOWN independently of the handled result; record ACTION_CANCEL against the current stream before native dispatch and retain cancellation until a fresh DOWN |
| Native context-menu callback | Reject a canceled touch stream even if Gecko's delayed callback arrives after Android CANCEL |
| Predictive Back | Cancel the selected renderer touch at Back start; preserve Android's delegated Back handler and animation |
| Native history command | Do not block ordinary native Back/Forward with a lagging cached history snapshot; override indices only to skip internal bootstrap entries |
| Posted controller callback | Capture navigation and touch-cancellation generations before posting; recheck identity and cancellation before acting |

The guard applies to every configured page-link long-press outcome. Ordinary long-press is
restored by the next touch stream. No stored preference, private-mode persistence or website
script changes are involved.

## Verification

| Check | Result |
| --- | --- |
| Baseline real OS gesture suite, identical final test APK | All three fail specifically because Link Peek opens before UP; 29.616 seconds |
| Final API 37 OS gesture and callback suite | 5/5 pass; 48.101 seconds, including both real history commits and ordinary long-press after canceled history/system Back |
| Full/Foss/System WebView JVM suites | 1,722 tests each; zero failures, errors or skips |
| Full/Foss debug assembly and lint | Pass; legacy UAST avoids the repository's existing K2 internal FIR failure |
| Popup, native Back/Forward, root-tab and private-mode regressions, API 35 | 9/9 pass on completed retry; 517.710 seconds |
| Bootstrap progress and unsafe legacy snapshot regressions, API 35 | 2/2 pass; 0.121 seconds |
| Independent production review | No blocking findings |

The final app APK has SHA-256
`5f877cc3541a64a108460b3bf8d68ade2c676aa9450c2ea29ae225db8cb911e5`.
The identical test APK used for the final fixed/baseline comparison has SHA-256
`b374cd3dc14a3fc44ab69ae9ded69ce1c2ae3b0fed3df063231931d0555ddc8e`.

The initial API 35 popup run aborted when its exclusive emulator exited with SIGABRT. That
partial run is not counted as a pass; the completed retry is reported separately.
