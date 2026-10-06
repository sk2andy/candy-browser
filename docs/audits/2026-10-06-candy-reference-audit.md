# Candy reference ownership follow-up — 2026-10-06

This review follows the [background-memory investigation](2026-10-06-background-memory-analysis.md).
It separates source-confirmed missing cleanup from native Gecko retention and intentional resident sessions.
No additional Pixel heap was collected; raw heaps, page strings and native dumps are excluded.

## Confirmed Candy cleanup paths

| Retainer | Trigger | Correction | Regression evidence |
| --- | --- | --- | --- |
| SessionController → removed extension ActionDelegate/SessionTabDelegate → ExtensionChrome → controller | Uninstall/reconcile removes an extension before its bound sessions close | Null both delegates on every bound session before discarding the inventory entry | Real local extension uninstall plus public Gecko delegate getters |
| Pending/queued download choice → response/session/preview closures | Source session closes/unloads or preview ends while chooser remains pending | Remove all choices of that source before release callbacks; preserve unrelated sources and transferred active downloads | Source close alone releases; preview end, independent queue, transferred ownership, reentrant teardown and stale dialog identities |
| Main MessageQueue → delayed popup Runnable → controller/Activity | Pending popup/controller closes before its 5/30-second timeout | Own callbacks by popup ID and cancel on final close, completed transition and destroy | Weak callback collection while original thirty-second deadline is still in the future |
| Process-owned WebRTC readiness callback → adapter snapshot → event sink/owner | A policy change never acknowledges; tabs close meanwhile | Keep a weak snapshot; factory retains live tabs normally and closed adapters still reject commands | Withheld ACK plus ReferenceQueue proves closed adapter/event owner collection; delayed ACK reloads only the original live snapshot |
| Find-in-page state → old engine session | Same-tab engine recreation keeps selected ID unchanged | Close the exact find owner before closing/replacing its session | Find dialog/session teardown on same selected tab recreation |

The popup retention was bounded by its deadline; it cannot explain a ten-minute plateau.
The extension/download/find defects require those flows to be used. No byte savings are claimed from
source inspection, object identity or WeakReference checks alone.

## Other paths and limits

| Area | Observation |
| --- | --- |
| Runtime owner | One application-context-owned runtime per process is intentional; factory does not retain an Activity |
| Final session teardown | Public session delegates, notification decision provider, Topping/native message delegates and bound views are cleared; warm view release/rebinding keeps active delegates |
| Controller teardown | Lifecycle/connectivity/Snooze/private-registry observers, sync/media hosts, session maps and preview bindings are released |
| Native Gecko retention | Pure-Gecko controls retain internal chrome windows/event listeners after close; separate from Candy ownership and still unresolved |
| Input guard | Unknown authenticated all-frame results deliberately retain a session. This is conservative residency policy, not an unreachable dangling reference |
| Bounded async work | Privacy startup, media restoration and translation callbacks have existing bounded completion/failure paths; source audit does not establish every native callback always completes |
| Allocation attribution | Main-process ART allocations were small compared with total process memory. A complete native byte breakdown still requires Gecko reports under a matched workload |

## Crash-helper experiment

The resolved release AAR compiles Java `BuildConfig.MOZ_CRASHREPORTER` true. Java startup always
creates pipes and binds the helper independently of GeckoView's configured crash handler.
A narrowly pinned experimental Java gate returned `{-1, -1}` before both effects.

| Separate cold process, dedicated API 37 emulator | Result |
| --- | --- |
| Unchanged helper startup | One helper; regular and private local documents loaded; test passed in 8.339 seconds |
| Java-only helper suppression | Native SIGSEGV during startup, about three seconds after process start |

The negative test explains why a Java/manifest preference is unsafe. Exact-source Android
`CrashReporter::OOPInit` explicitly bypasses the reporting-enabled early return and connects to the
helper unconditionally. An invalid descriptor reaches Rust `OwnedFd::from_raw_fd(-1)` and its
`fd != -1` assertion. `MOZ_CRASHREPORTER_DISABLE` does not suppress this rendezvous.
The experimental build instrumentation, bridge, UI switch and preference were removed.
The Pixel was not modified during this experiment.

A real optional helper requires a coordinated native Gecko build: gate the native rendezvous and
review child-process crash IPC/reporting assumptions, or use a matching build without crash reporting.
No ineffective switch or arbitrary process kill is included in Candy.

Exact primary sources:

- [Java startup](https://github.com/mozilla-firefox/firefox/blob/fdd757a2e09c9471cddf383e64e631e4ce178499/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoRuntime.java)
- [Native reporting/helper startup](https://github.com/mozilla-firefox/firefox/blob/fdd757a2e09c9471cddf383e64e631e4ce178499/toolkit/crashreporter/nsExceptionHandler.cpp)
- [Rust IPC connector](https://github.com/mozilla-firefox/firefox/blob/fdd757a2e09c9471cddf383e64e631e4ce178499/toolkit/crashreporter/crash_helper_common/src/ipc_connector/unix.rs)

## Verification

| Check | Result |
| --- | --- |
| Full/Foss JVM suites, including both WebRTC ownership cases | 1,865 tests per variant; no failures, errors or skips |
| WebRTC withheld-ACK paired control | Restoring the old strong snapshot fails the new collection test after 5.043 seconds: neither closed adapter nor event owner is collected. With the weak snapshot, both are collected; Full/Foss tests pass in 0.012/0.014 seconds |
| Download, popup and same-tab find ownership on dedicated API 37 emulator | 20 Android tests passed |
| Actual extension uninstall and delegate getters | One Android test passed |
| Native final-close ownership | One Android test passed; closed Candy session and destroyed controller collected |
| Repeated regular/private switches, resume and background restore | Three Android tests passed |
| Full/Foss lint and APK builds; Full Android test APK | Passed; no lint errors |
| Final APK experiment removal | No experimental crash-helper bridge in either variant's DEX |
| Whitespace validation | `git diff --check` passed |

Android checks used the dedicated `emulator-5590`, with unchanged upstream Gecko startup.
The Android APK included the controller, extension and final-session cleanup fixes; the independent
WebRTC weak-snapshot change was verified by the real factory JVM tests and included in the final
Full/Foss APK builds. These checks establish cleanup and interaction behavior, not a matched Pixel
memory comparison. The Pixel was not modified or deployed to during this review.

An experimental build success alone is not evidence that disabling the helper works. The cold-start
failure above is why the optional-helper experiment was removed.
