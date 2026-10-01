# Background appearance reconciliation

## Scope and reproduction

The reported setting is **System**: Candy starts light, stays in the background while Android changes
to dark, and returns with both browser chrome and the website still light. The checks use a dedicated
API 35 emulator (initial reproduction: `emulator-5592`; completion: `emulator-5594`), not the reporter's
physical device. A short ordinary stopped-Activity
transition already passed before the fix; the device-specific long-background delivery pattern has
not been reproduced directly.

| Controlled baseline | Result before the fix |
| --- | --- |
| Light → stopped Activity → system dark → same Activity resumed → system light | Passed |
| Activity/application resources dark; deliberately seed Gecko's previous light configuration while stopped; resume | Failed: website remained `theme-light` |
| Activity/application resources dark; deliberately distribute the previous light configuration to the view tree and Gecko while stopped; resume | Failed: Compose remained light and website remained `theme-light` |

The injected state models missed delivery without reflection or production test switches. The test
first confirms the stale light/dark view and website state, then resumes the existing Activity.
Compose is observed through a test `ComposeView` using the production `CandyTheme`; this checks the
shared theme/configuration path, not a pixel capture of the whole browser. The local website uses
CSS `prefers-color-scheme` and publishes its media-query state in its title.

## Ownership and invariants

| Owner | Reconciliation |
| --- | --- |
| `MainActivity` | Apply the selected AppCompat policy; read effective Activity resources after nested callbacks; update the night marker before page effects |
| Activity view tree | Receive a copied effective configuration on callbacks, start and resume, including when resources are current but Compose delivery was missed |
| `BrowserController` | Forward current Activity configuration at initialization, start and resume before sessions become active |
| Gecko runtime | Refresh cached system night mode even when its existing preferred scheme remains `SYSTEM` |
| Existing page refresh path | Run only when effective night resources change; unchanged resumes and stale view/runtime repair retain the loaded document |

Tests preserve preferences and the original system night mode. They assert both missed-delivery
directions, effective view configuration at the actual start callback before resume, correct Compose
and website appearance while subsequently paused (`STARTED`), same Activity/tab identity, two
successive resumes, and stable HTTP page-request counts
after a bounded settling interval. Normal background transitions also exercise forced light and dark
choices while the system mode changes.

`ActivityScenario.moveToState(STARTED)` first resumes, then pauses the Activity. A zero-size test View
records delivered configuration, and an `ActivityLifecycleMonitorRegistry` callback captures it at
the actual `Stage.STARTED` event. This event runs after `MainActivity.onStart()` returns and before
`onResume()`, as confirmed by
[AndroidX's instrumentation source](https://github.com/android/android-test/blob/main/runner/monitor/java/androidx/test/runner/MonitoringInstrumentation.java).
Against the original resume-only fix, this check failed with expected night mode `32` but delivered
night mode `16`. Reconciliation at start closes that gap before Gecko sessions are activated.

## Verification

Completed on 2026-10-01 using dedicated API 35 AVD `candy_theme_finish_20261001`
(`emulator-5594`) and the existing GeckoView 155 dependency. The parallel Gecko upgrade is a separate
change and is not part of this fix's verification.

| Check | Full Debug | Foss Debug |
| --- | --- | --- |
| `testFullDebugUnitTest` / `testFossDebugUnitTest` | 1,634 passed; 0 skipped | 1,634 passed; 0 skipped |
| `GeckoAppearanceInstrumentedTest`, system dark before fresh instrumentation | 4 passed; 0 skipped | 4 passed; 0 skipped |
| `AppearanceWebThemeInstrumentedTest` + `AppearanceSystemBarsInstrumentedTest` | 6 passed; 0 skipped | 6 passed; 0 skipped |
| `lintFullDebug` / `lintFossDebug` | 0 errors; existing warnings remain | 0 errors; existing warnings remain |
| App and Android-test APK assembly | Passed | Passed |
| Independent final production/test style review | Passed | Passed |

The focused suites were run sequentially with explicit `adb -s emulator-5594 shell am instrument`
commands, including the previously hanging forced-dark background path. A visual smoke check of the
actual Full Debug browser chrome and a local CSS media-query webpage also passed light → background
→ dark → foreground and dark → background → light → foreground using the existing Activity task.
The reporter's particular long-background behavior on a physical device remains unverified; the
controlled missed-delivery regression fails before the fix and passes with it.
