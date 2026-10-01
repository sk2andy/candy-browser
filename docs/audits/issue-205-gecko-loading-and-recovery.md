# Issue 205: Gecko loading and tab recovery

## Reproduction evidence

| Reported behavior | Controlled reproduction before the fix | Regression |
| --- | --- | --- |
| Bright loading flash in dark mode | An unpainted native Gecko surface drew `#FFFFFF` over a dark host. A delayed HTTP response also exposed a white bootstrap document; using a transparent cover alone made the light compositor black. | `GeckoWebContentThemeInstrumentedTest` |
| A previously open tab appears blank until another interaction | A native initial `about:blank` location callback replaced a restored web URL. Separately, a viewport measured before attachment kept its initial load queued even after attachment and activity resume, until another layout occurred. | `BrowserControllerInitialViewportInstrumentedTest` |
| Startup without the keyboard shows a fresh tab | With address focus set to Never and startup home enabled, a launcher start selected a new blank tab instead of the saved tab. | `MainActivityRestoredTabInstrumentedTest`, `StartupPresentationRulesTest` |
| UUID address and unrecoverable page | A real Gecko session loaded a web fixture, then received page-start/location callbacks for a bootstrap URL with a previous extension origin and binding token. The web address became `moz-extension://<UUID>/bootstrap.html?token=<UUID>`. | `CandyPrivacyHostInstrumentedTest#stalePrivacyBootstrapCallbacksDoNotReplaceRestoredPageAddress` |
| A saved tab already contains a bootstrap address | A persisted tab summary with that address and an existing Candy Trail must recover the current web URL into both controller state and the saved summary. | `BrowserControllerBootstrapRecoveryInstrumentedTest` |

Tests use independent API 35 emulators. Initial-location and stale-bootstrap callbacks are injected
through actual Gecko delegates to make the vulnerable ordering deterministic. This proves the code
paths; it does not establish the precise callback timing on the reporter's Xiaomi device.

## Resulting behavior

| Boundary | Contract |
| --- | --- |
| Loading surface | Set an opaque native cover before attaching a new renderer, using context night mode before binding and Gecko's effective website appearance after binding. The internal bootstrap canvas declares `color-scheme: light dark`. Gecko releases its cover after paint. |
| Initial viewport | Check initial-load readiness on layout and on a one-shot pre-draw after attachment. Remove layout, attachment and pre-draw observers when replacing or canceling the pending load. |
| Initial blank callback | Keep the expected web URL while a first viewport-bound load waits, including the privacy-policy handshake. Initial placeholder events cannot replace that URL. |
| Launcher preference | Address focus Never preserves the last selected tab and suppresses startup home selection. Other focus modes retain their existing startup behavior. |
| Internal address classification | Recognize only the bootstrap document's UUID origin, exact path and UUID token shape, including previous runtimes. Classification does not authorize navigation: trust still requires the current exact extension origin and binding token. |
| Native snapshots | Do not save or restore a snapshot whose current history entry is a bootstrap document. Retain normal URL fallback. |
| Previously poisoned summaries | Reset the internal address and recover a validated HTTP(S) address from that tab's existing Trail when available. Explicit address/home actions cancel delayed recovery. Without a usable Trail, show a reusable blank tab. |
| Privacy and extension options | Existing private persistence exclusions and extension options ownership remain in force. Normal extension options URLs do not match the internal bootstrap shape. |

## Focused device checks

| Integrated validation | Result |
| --- | --- |
| FullDebug JVM unit suite | 1,634 tests, no failures/errors/skips |
| FossDebug JVM unit suite | 1,634 tests, no failures/errors/skips |
| FullDebug and FossDebug lint | No errors; repository warnings remain |
| FullDebug, FossDebug and FullDebug test APK builds | Passed |
| Combined API 35 device run | 17/17 passed on dedicated `emulator-5590`, including three extension options navigation regressions |

Use the caller's own emulator. Gradle resolves the configured debug application ID and test APK:

```sh
ANDROID_SERIAL=emulator-5590 ./gradlew connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.sk2andy.materialbrowser.browser.BrowserControllerInitialViewportInstrumentedTest,dev.sk2andy.materialbrowser.MainActivityRestoredTabInstrumentedTest,dev.sk2andy.materialbrowser.browser.gecko.GeckoWebContentThemeInstrumentedTest,dev.sk2andy.materialbrowser.browser.gecko.CandyPrivacyHostInstrumentedTest#stalePrivacyBootstrapCallbacksDoNotReplaceRestoredPageAddress,dev.sk2andy.materialbrowser.browser.BrowserControllerBootstrapRecoveryInstrumentedTest
```

See [runtime ownership](../browsing/runtime-and-navigation.md),
[appearance settings](../browsing/appearance-and-settings.md) and
[tab persistence](../tabs-and-profiles/lifecycle-and-persistence.md).
