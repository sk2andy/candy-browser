# GeckoView 155 to 157 upgrade

## Version and compatibility

| Item | Before | After | Evidence |
| --- | --- | --- | --- |
| Pinned release artifact | `155.0.20260903215306` | `157.0.20260924084938` | [Mozilla Maven metadata](https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml), inspected 2026-10-01; published artifact status is `release` |
| Required compile SDK | 37.1 | 37.1 | Both AAR metadata files; existing app/shared configuration already satisfies it |
| Kotlin standard library | 2.4.10 | 2.4.20 | Published Gradle module runtime dependencies; Kotlin plugins remain 2.4.10 |
| Play Services FIDO | 21.3.0 | 21.3.1 | Published Gradle module runtime dependencies; excluded from FOSS as before |
| Media3, AndroidX Core and Lifecycle | 1.11.0 / 1.19.0 / 2.11.0 | Unchanged | Published Gradle module runtime dependencies |
| Android/Gradle toolchain | AGP 9.4.0 / Gradle 9.6.0 | Unchanged | No higher AAR minimum; build verification below |
| App Android support | minSdk 33 / targetSdk 36 | Unchanged | Engine update does not change the app's platform support policy |

The same pin serves Full, FOSS, the System WebView compile-only adapter and Gecko test fixtures.
Gecko remains absent from the System WebView distribution's production runtime.

## Required changes and retained boundaries

| Surface | Upgrade decision | Basis |
| --- | --- | --- |
| Dependency | Pin the stable 157 artifact | [Firefox Android 157 release](https://www.firefox.com/en-US/firefox/android/157.0/releasenotes/), 2026-09-29 |
| `ContentParams` / `PageMetadata` | No source migration | 156 changed constructors; Candy uses neither class |
| PDF export and signatures | No source migration | Candy does not call `saveAsPdf`, `getPdfViewerEditor` or `PdfViewerController` |
| Full-page screenshots | Available for a future feature | 157 adds `GeckoView.captureFullPage` / `GeckoDisplay.captureFullPage`; current previews keep their existing viewport contract |
| Immediate audible autoplay | Enable the existing skipped device regression | [Bug 2049064](https://bugzilla.mozilla.org/show_bug.cgi?id=2049064) was fixed in 154; it is not a new 157 fix |
| Extension test fixture | Accept a global Browser Action as well as a primary-tab override | Fixture JavaScript sets title/badge without `tabId`; Gecko correctly publishes `tabId = null`. Relevant JavaScript in the actual 155/157 AARs and tagged Java sources are unchanged |
| Four Privacy safe-area fixtures | Replace obsolete `html::before` expectations with native inset plus current CSS protection | Regular/private flow, fixed and scrolled sticky geometry; dynamic header addition; live CSS disable/re-enable with unchanged document identity. Six privacy/cookie test bodies are unchanged. Legacy fallback-count/timing fields are not exercised by the current Gecko CSS prototype |
| Permission writes | Keep bounded asynchronous readback before reload | `StorageController.setPermission` still returns `void` in both AARs |
| Google Pay | No confirmed upgrade fix | [Mozilla bug 1963064](https://bugzilla.mozilla.org/show_bug.cgi?id=1963064) remains open. Native Google Pay handoff is unavailable in Gecko; a merchant's web-popup fallback is a separate path. Candy System WebView enables Payment Request, but no live checkout was verified in this audit |
| Privacy, Toppings, extensions | Keep current scope, generation and private-mode guards | Upgrade does not replace Candy's ownership or persistence boundaries |
| Sampler | Keep Java sampling disabled; recheck native lifecycle | 157 `GeckoJavaSampler.java` and `ProfilerController.java` match 155.0.1; Java teardown still clears `sSamplingRunnable` before marker storage stops |

API changes are from the [GeckoView API changelog](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/doc-files/CHANGELOG).
Existing documents that record a limitation observed in 155 remain historical evidence unless the
corresponding 157 contract is explicitly verified here.

## Upstream fixes relevant to Candy

| Change | Expected Candy benefit | Confidence and limits |
| --- | --- | --- |
| [A/V playback-rate drift, bug 1955841](https://bugzilla.mozilla.org/show_bug.cgi?id=1955841) | Better audio/video synchronization after repeatedly switching speed, including YouTube web playback | Core playback fix ships in 157; Android reproduction is attached upstream. Not a guarantee against every media desynchronization; no Candy-specific before/after media measurement |
| [WebRTC video orientation, bug 1340372](https://bugzilla.mozilla.org/show_bug.cgi?id=1340372) | Correct orientation of incoming phone/tablet camera streams when WebRTC is enabled | Core WebRTC fix ships in 157; no Candy-specific call measurement. Does not change Android device auto-rotation or Candy's WebRTC protection policy |
| [156 CSS/SVG/DOM corrections](https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Releases/156) | Correct scrollbar feature detection, `text-box-trim`, SVG text-event offsets and selection/deletion across Shadow DOM boundaries | Included engine behavior; does not prove a fix for Candy's safe-area, keyboard or chrome layout |
| [157 animation corrections](https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Releases/157) | Correct reversal of zero-rate and scroll-driven website animations | Included engine behavior; Candy's animation-suppression setting remains authoritative |
| [156 security fixes](https://www.mozilla.org/en-US/security/advisories/mfsa2026-90/) and [157 security fixes](https://www.mozilla.org/en-US/security/advisories/mfsa2026-97/) | Updated Gecko fixes for native media, graphics, JavaScript, DOM and other engine components | Advisory scope includes Firefox application and platform-specific issues too; not every listed CVE maps to Candy |

Firefox UI features, VPN changes and desktop-only fixes are not counted as Candy engine fixes.
The [156 file-provider crash fix, bug 2063609](https://bugzilla.mozilla.org/show_bug.cgi?id=2063609),
changes Android Components `Uri.getFileName` / `FilePicker`, which Candy does not use. Candy owns its
upload staging and prompts. Firefox date-picker presenter changes also do not establish a Candy fix.

## Sampler source check

| 155.0.1 to 157.0 source comparison | Result |
| --- | --- |
| `GeckoJavaSampler.java` / `ProfilerController.java` | Byte-identical |
| Native profiler-state observers | Main-thread dispatch and `NotifyProfilerStateChanged(true/false)` unchanged |
| Native stop ordering | Create stopped promise, notify stopped observers, delete sampler thread, return promise; unchanged |
| Java marker teardown | `sSamplingRunnable = null` still precedes `sMarkerStorage.stop()`; keep Java sampling and explicit Java markers disabled |

Checked against the tagged 157 sources for
[`platform.cpp`](https://raw.githubusercontent.com/mozilla-firefox/firefox/FIREFOX_157_0_RELEASE/tools/profiler/core/platform.cpp),
[`GeckoJavaSampler.java`](https://raw.githubusercontent.com/mozilla-firefox/firefox/FIREFOX_157_0_RELEASE/mobile/android/geckoview/src/main/java/org/mozilla/gecko/GeckoJavaSampler.java)
and [`ProfilerController.java`](https://raw.githubusercontent.com/mozilla-firefox/firefox/FIREFOX_157_0_RELEASE/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/ProfilerController.java).

## Verification

| Check | Result |
| --- | --- |
| Full/FOSS build, lint and unit suites | Passed: both APKs assemble, lint reports zero errors, 1,634 JVM tests per flavor with zero failures/skips |
| Focused Gecko device contracts on dedicated API 35 emulator | Passed: 36 cases across the nine suites below, after correcting obsolete fixtures and isolating persistent extension state |
| System WebView artifact and runtime dependency boundary | Passed: debug build/lint, `verifySystemWebViewReleaseDependencies`, `verifyFossReleaseDependencies`; APK contains no Gecko assets/native entries |
| Sampler native lifecycle and private-session cancellation | Passed: all five `GeckoPerformanceDiagnosticsInstrumentedTest` cases with diagnostics enabled |

### Device test isolation

| Observation | Resolution |
| --- | --- |
| A comma-separated Gradle class selection produced only the three autoplay cases | Inspect result XML rather than treating task success as proof that every requested class ran; run remaining classes individually with `adb -s emulator-5596 shell am instrument` |
| Extension fixture persists in Gecko after its test process exits and changes fixture page titles | Clear this dedicated emulator's app data between independent suites; the clean redirect suite passes both tests |
| Original Extension Chrome fixture rejects global actions | Same failure reproduced with Gecko 155; corrected fixture passes its complete conformance case on 157 |
| Legacy `html::before` safe-area assertion | Original document-start representative also fails on 155; migrated full Privacy suite passes all ten cases on 157 |
| Final lint recheck | Initial retry crashed inside lint while analyzing the concurrently edited `BrowserPullToRefreshLayoutInstrumentedTest.kt` (`Unknown annotation target RunWith`); separate Full/FOSS retry with `--max-workers=1` passed |

App-data clearing is limited to this session's disposable emulator. Session restore assertions run
within their suite without clearing between test steps; extension persistence phase tests are not
part of this audit.

| Suite | Final passed cases |
| --- | ---: |
| `GeckoAutoplayInstrumentedTest` | 3 |
| `GeckoRuntimeSettingsInstrumentedTest` | 3 |
| `GeckoExtensionChromeInstrumentedTest` | 1 |
| `CandyPrivacyHostInstrumentedTest` | 10 |
| `CandyToppingHostInstrumentedTest` | 1 |
| `GeckoSessionRestoreInstrumentedTest` | 5 |
| `GeckoRedirectNavigationInstrumentedTest` | 2 |
| `GeckoPerformanceDiagnosticsInstrumentedTest` | 5 |
| `GeckoWebContentThemeInstrumentedTest` | 6 |
| **Total across focused runs** | **36** |

Build checks:

```sh
./gradlew lintFullDebug lintFossDebug assembleFullDebug assembleFossDebug \
  testFullDebugUnitTest testFossDebugUnitTest
./gradlew lintSystemwebviewDebug assembleSystemwebviewDebug \
  verifySystemWebViewReleaseDependencies verifyFossReleaseDependencies
./gradlew assembleFullDebug assembleFullDebugAndroidTest -Pcandy.performanceDiagnostics=true
```

After installing the diagnostic Full and test APKs on this dedicated emulator, run each suite
individually with its fully qualified class name:

```sh
adb -s emulator-5596 shell pm clear dev.sk2andy.materialbrowser.linkpeek
adb -s emulator-5596 shell am instrument -w -r \
  -e class dev.sk2andy.materialbrowser.browser.gecko.CandyPrivacyHostInstrumentedTest \
  dev.sk2andy.materialbrowser.linkpeek.test/androidx.test.runner.AndroidJUnitRunner
```

The app/test IDs above are this checkout's default Debug IDs. The diagnostics build is required for
the sampler suite. Do not reuse this clearing sequence for extension persistence phase tests.

Device tests use this session's dedicated `candy_gecko157_20261001` AVD and explicit
`ANDROID_SERIAL=emulator-5596`. No physical device or another session's emulator is used.
