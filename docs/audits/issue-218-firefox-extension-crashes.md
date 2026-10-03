# Issue 218: reported Firefox extension crashes

## Finding

The reported application crash could not be reproduced on 2026-10-03. All six exact signed XPIs
installed successfully in the current Debug build. Mullvad also installed through the real settings
UI in the published v0.44.0 and v0.43.0 arm64 Release APKs, and survived a process cold start.
In a follow-up, all six were enabled together in published v0.45.0 and exercised through the
browser UI, including navigation, history, reloads, tab changes, extension popups and a cold start.
No crash stack trace was generated. These results do not establish compatibility with every add-on
feature or rule out a Samsung-specific, existing-profile, or intermittent failure.

Source: [issue 218 comment](https://github.com/sk2andy/candy-browser/issues/218#issuecomment-5934103622).
The reporter names Mullvad with certainty; the other five names have varying confidence. The issue
specifies Samsung S25 / Android 16 / Gecko, but does not specify the Candy version or provide a trace.

## Environment and coverage

| Item | Value |
| --- | --- |
| Dedicated emulator | `codex_issue218_root`, `emulator-5610` |
| System | Android 16 / API 36, Google Play arm64 image, Pixel 6 hardware profile |
| Rendering | Software GPU (`swiftshader_indirect`) |
| Debug dependency | GeckoView `157.0.20260924084938` |
| v0.44.0 Release dependency | GeckoView `157.0.20260924084938` |
| v0.45.0 Release engine | GeckoView 157.0; published arm64 APK |
| v0.43.0 Release dependency | GeckoView `155.0.20260903215306` |
| Debug test boundary | Production `BrowserController` and extension chrome host; signed-XPI repository install, inventory refresh, 8-second settle |
| Release boundary | Published APK; Settings → Firefox extensions → Install; permission confirmation, installed inventory, process cold start |
| Additional v0.44.0 checks | Normal page navigation and Mullvad popup rendered after restart |
| Isolation | Fresh app data for each Debug add-on; release package separately installed; no physical device used |

The Debug build used the existing checkout, including unrelated in-progress changes. Published
Release APK tests remove that dependency. A temporary instrumented probe was removed after use;
its source and raw evidence remain under `/tmp/candy-issue218/` on the investigation host.

## Results

| Exact extension | Version | Debug install and settle | Release UI install | Cold start |
| --- | --- | --- | --- | --- |
| Mullvad Browser Extension | 0.9.10 | Pass; 1 test, 0 failures | Pass in v0.44.0 and v0.43.0; retained for v0.45.0 | Pass in Debug, v0.44.0, v0.43.0 and together in v0.45.0 |
| Chrome Mask | 10.1.0 | Pass; 1 test, 0 failures | Pass in v0.45.0 | Pass together in v0.45.0 |
| CanvasBlocker | 1.12 | Pass; 1 test, 0 failures | Pass in v0.45.0 | Pass together in v0.45.0 |
| FoxyProxy Standard | 9.8 | Pass; 1 test, 0 failures | Pass in v0.45.0 | Pass together in v0.45.0 |
| Zoom | 2.8.28 | Pass; 1 test, 0 failures | Pass in v0.45.0 | Pass together in v0.45.0 |
| LocalCDN | 2.6.86 | Pass; 1 test, 0 failures | Pass in v0.45.0 | Pass together in v0.45.0 |

The first Mullvad instrumentation shell connection ended early with an ADB transport interruption.
Device `TestRunner` logs independently record completion with `1 tests, 0 failed, 0 ignored`.
The other five instrumentation sessions returned `OK (1 test)` normally.

## Log analysis and code review

| Evidence | Interpretation |
| --- | --- |
| Android `logcat -b crash` empty throughout the captured tests | No captured Java fatal exception or native crash to analyze |
| Release `dumpsys activity exit-info` reports `USER REQUESTED / FORCE STOP` for deliberate cold-start teardown | Observed exits were test actions, not application crashes |
| Mullvad manifest warns that `search` is not a supported permission | A compatibility warning; installation still succeeds and the v0.44.0 popup renders |
| Zoom emits several `uncaught exception: Object { message }` messages | JavaScript errors without a reported message value or source stack; test and app continue |
| Zoom emits `TypeError: can't access property "WindowEventDispatcher", win is null` in `GeckoViewSessionStore.sys.mjs:149` during controller teardown | JavaScript session-store error after the settle marker; not an Android fatal exception |
| Mullvad v0.43.0 also emits an opaque JavaScript exception | Not a fatal app crash in the reproduction |
| `GeckoExtensionRepository` catches install/read/mutation failures | A rejected API or failed install alone does not prove an application crash |
| Extension delegates and icon callbacks run separately from the install coroutine | A future actual delegate/native trace must be inspected at its own callback boundary |
| Mullvad, Chrome Mask and CanvasBlocker use SVG action icons; all install without a crash, and Mullvad's icon visibly renders | SVG alone is not a reproduced crash trigger |
| Gecko consumer R8 rules keep GeckoView classes and JNI members | No static evidence of a Release-only callback-name stripping failure |

The install path, action/icon path, manifest normalization and Release shrinker rules were reviewed
independently by a second agent. No production fix is justified from the available evidence.

## Follow-up: all extensions enabled, real browser navigation

All six signed XPIs were installed in the release app's extension manager. All six enabled switches
were checked before navigation and again after the cold start. uBlock Origin 1.75.0 and I still
don't care about cookies 1.1.9 also remained enabled. Input used ADB taps and Android keyboard
shortcuts in the actual browser UI; no instrumented BrowserController navigation was used.
A separate clipboard helper supplied exact URLs. All device actions used the dedicated emulator.

| Exercise | Observed coverage / result |
| --- | --- |
| External pages | Mozilla Android add-ons, rekt.news, X, BrowserLeaks Canvas, BrowserLeaks WebGL, Wikipedia; 11 rendered external-page checkpoints across two passes |
| Local fixture | Three linked pages, 12 link clicks and 12 history pushes; 48 Canvas readbacks per load, WebGL queries and a Google CDN jQuery request |
| Reload | 24 UI reload commands |
| History | 48 back/forward key commands; each of the 3 additional full-page back/forward pairs sends two back keys and two forward keys to cross both the pushState entry and the page boundary. Fixture A/B transitions visibly confirmed. |
| Tabs | 6 new-tab commands, 6 close-tab commands, 24 next/previous-tab commands |
| Chrome Mask | Host-specific mask switched on for `127.0.0.1`; fixture visibly reported Chrome/154 User-Agent, including after restart |
| CanvasBlocker | Popup rendered and reported `CanvasBlocker on`; Canvas/WebGL fixture continued to render |
| LocalCDN | Popup rendered and reported 30 local CDN resource injections |
| Zoom | Popup rendered; plus control visibly changed 100 to 110; subsequent fixture layout enlarged |
| FoxyProxy / Mullvad | Both popups rendered; FoxyProxy had no configured proxy, Mullvad reported missing optional proxy permissions and no active VPN |
| Cold start | Intentional force-stop, then successful launch with all six still enabled; no boot loop |
| Android crash capture | Crash buffer empty at every checkpoint; no Java fatal exception or native fatal signal in continuous Logcat |
| App-managed diagnostics | Logging enabled before scripted navigation stress; export after restart contains startup/logging events, no `GeckoRendererCrashed`, `GeckoRendererKilled`, `UncaughtException` or native-crash event |
| Android process history | Main process remained PID 3576 until intentional force-stop; new PID 13567 after launch. Child exits were `EXIT_SELF` status 0 or deliberate `USER REQUESTED / FORCE STOP` |

The two initial navigation passes lasted approximately five minutes; activation, additional fixture
history tests, popups and restart extended the follow-up. One second-pass Wikipedia input remained
in the address editor rather than rendering; it is excluded from the 11 confirmed external loads.
An initial Zoom tap used popup-relative accessibility coordinates and dismissed the popup; it is
excluded from the verified Zoom result. A later screenshot-based tap confirmed the 110 value.

| JavaScript stack observed in v0.45.0 | Source-level interpretation |
| --- | --- |
| `onTabStateUpdate` → `updateSessionStoreFromTabListener` → `SSF_updateSessionStore` → `UpdateSessionStore`; `GeckoViewSessionStore.sys.mjs:149`, `win is null` | The Gecko module in the published APK dereferences `win.WindowEventDispatcher` without a null guard. Errors appeared during session/tab teardown; the app continued. This is not a captured Android fatal crash. |
| `raceResponses` at `ExtensionParent.sys.mjs:514`; `Actor 'Conduits' destroyed before query 'RuntimeMessage' was resolved` | Published Gecko source catches internal actor-destruction errors and calls `Cu.reportError`. An accompanying `reportCandyInlineVideoState` frame at `content.js:2056` belongs to Candy's bundled privacy extension, verified in the published APK. It is not evidence that one of the six third-party extensions crashed the app. |

These results cover enabled extensions and the features exercised above. Proxy/VPN traffic was
not exercised. Software GPU emulation does not reproduce the reporter's Samsung S25 hardware,
profile or exact extension settings. The reported crash remains unreproduced.

| Follow-up artifact under `/tmp/candy-issue218/` | Contents |
| --- | --- |
| `stress045/events.jsonl`, `stress045/*-crash.txt`, `stress045/*-exit-info.txt` | UI action markers, checkpoint crash buffers and Android process histories |
| `stress045/full-logcat.txt`, `stress045/app-logs.txt` | Continuous system/Gecko output and app-managed diagnostic export |
| `stress045/inventory-*.xml`, `stress045/inventory-after-restart-*.xml` | Enabled-switch inventory before and after restart |
| `stress045/*-popup*.png`, `stress045/masked-fixture.xml`, `stress045/zoom-plus-verified.xml` | Popup and active-feature evidence; Mullvad artifacts contain network addresses |
| `stress_ui.py`, `navigation_stress.py`, `popup_stress.py`, `stress-fixture/` | Reproduction helpers and local Canvas/WebGL/CDN fixture |

## Evidence lookup and next diagnostic boundary

| Artifact under `/tmp/candy-issue218/` | Contents |
| --- | --- |
| `<addon>-logcat.txt`, `<addon>-instrumentation.txt` | Debug installation and test results |
| `release044-mullvad-full-logcat.txt`, `release044-mullvad-crash.txt`, `release044-exit-info.txt` | v0.44.0 UI installation, popup, cold start and process-exit evidence |
| `release043-mullvad-install-logcat.txt`, `release043-mullvad-startup-logcat.txt`, `release043-exit-info.txt` | v0.43.0 installation, cold start and process-exit evidence |
| `GeckoReportedExtensionProbeInstrumentedTest.kt`, `ui_probe.py` | Diagnostic probe source |
| `*-manifest.json`, `*.xpi` | Exact reported package manifests and original packages |

Raw logs and popup screenshots may include network addresses; they are local diagnostic artifacts,
not committed project data. Temporary-directory evidence is not durable across host cleanup.

To identify the reported crash, obtain the affected Candy version, the exact last action before the
crash, and the failing device's Android crash buffer/full logcat. If Android reports a native Gecko
crash, retain its tombstone/minidump as well. A JavaScript console warning without a fatal trace
cannot establish the cause of an Android application exit.
