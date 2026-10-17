# iOS shared browser expansion

Worktree: `codex/ios-shared-parity`, based on `3117df47`.

## Ownership

| Layer | Responsibility |
| --- | --- |
| Shared Kotlin | Regular session restore, private boundaries, pinned tabs, favorites/history, desktop preferences, Snooze scheduling/restoration, profile mutations, Reader library codec/controller/speech state, download filenames |
| Shared Compose | Overview, profiles, settings, Reader, favorites/history and platform presentation contracts |
| iOS adapters | WebKit, protected atomic storage, speech synthesis, native web prompts, download transfer/export, Liquid Glass and SF Symbols |
| Android adapters | Existing System WebView/Gecko orchestration; app-compiled Material search renderer |

The Android Material search implementation must remain compiled against the app's resolved Material
dependency. Moving that call directly into the Compose Multiplatform module produced a runtime
`NoSuchMethodError`; the shared state/renderer contract preserves reuse without that binary mismatch.
The corrected Favorites screen passes its existing device tests.

## Verification

| Check | Result |
| --- | --- |
| Shared Kotlin on isolated iOS simulator | 309 tests passed, 55 test cases |
| Full/Foss unit tests | 1,996 tests passed per variant; no failures/errors/skips |
| Full/Foss lint and debug assembly | Passed |
| Full Android test APK | Built |
| Favorites, Reader UI, Appearance settings, Reader store | 25 device tests passed after the search-renderer fix |
| Web prompt UI | 3 device tests passed |
| System WebView engine | 11/12 passed; remaining 404 test also fails on unchanged baseline |
| Gecko overview handoff | 1/3 passed; both remaining failures also reproduce on unchanged baseline |
| Native Swift contracts | All 13 standalone executables passed, including viewport geometry (13 assertions) and profile restore (9 assertions) |
| Snooze, profile sheets and Appearance | 24/27 Android device tests passed; three failures reproduce on unchanged baseline |
| Final platform-slider regression | Appearance 7/7 and Tabs settings 9/9 Android device tests passed |
| Independent reviews | Shared state/privacy, native lifecycle/download cleanup and presentation compatibility reviewed |

Android tests used only dedicated API-37 AVD `codex_ios_parity_20261016`, serial `emulator-5842`.
iOS tests used dedicated device set `/tmp/candy-ios-parity-devices` and simulator
`FCCDD08A-173C-4CFD-9DFA-3DCC88CB32DF`. No physical Android device was used.

## Baseline comparison

A separate clean checkout of `3117df47` was built and installed on the same dedicated Android emulator.
The following failures reproduce with unchanged production and test sources:

| Test | Failure on baseline and changed build |
| --- | --- |
| `mainFrame404IsCommittedWithNotFoundStatus` | HTTP status does not become 404 within ten seconds |
| `restoredDismissAnchorAttachesGeckoOnlyAfterOverviewCloses` | Address-bar tab-button semantic node missing |
| `heroEntryAndExitKeepGeckoViewportPixelStableAcrossSafeArea` | Existing viewport pixel assertion fails |
| `listOverflowOpensActionsAboveChromeBeforeSnoozePicker` | Expected tab ID remains null after menu interaction |
| `gridOverflowOpensActionsAboveChromeBeforeSnoozePicker` | Expected tab ID remains null after menu interaction |
| `tabOverviewCloseAllKeepsPinnedTabs` | An extra pinned tab from the preceding failed test remains |

These comparisons establish pre-existing failures in this environment, not a clean pass for those
contracts. The change does not suppress or weaken their assertions.

## Scope limits

This expansion does not establish full Android feature parity. See
[`platform-engines.md`](../browsing/platform-engines.md) and [`iosApp/README.md`](../../iosApp/README.md)
for remaining backend, permission and presentation gaps. In particular, Site Capsules, advanced library workflows, advanced site/permission controls,
profile protection/reordering and persistent/background/resumable download management remain open.
Snooze foreground/launch restoration and OS notification scheduling are connected; notification delivery
was not manually validated. iOS background execution and undo are not equivalent to Android.


## Native simulator evidence

| Flow | Result |
| --- | --- |
| Navigation and viewport | HTTP fixture and public HTTPS page rendered; overview round-trip and restored selected tab exercised |
| Favorites | Add, remove and search exercised in the shared library |
| Private tabs | Opened through native chrome; private URL absent from saved state and private tab absent after relaunch |
| JavaScript prompts | Alert, confirm and text prompt accepted; immediate chained prompts and cancellation returned correct results |
| Downloads | Native Files export picker appeared for an attachment; cancellation removed temporary payload and retained the page |
| Reader | Extracted, saved, played speech, paused/stopped; saved article remained readable after server shutdown and process restart |
| Snooze | Custom date picker saved pending record and removed active tab with notifications denied; pending library survived process restart and “Open now” restored its tab |
| Profiles | Isolated local profile created; default-profile isolation enabled, persisted across process restart and disabled again without crash; deletion confirmation inspected without submitting |
| Appearance | Dark/System, animation toggle and 100/125% page size exercised; preferences survived process restart; native slider taps verified at 100%, 125% and 200% |
| Safe area | Normal, fixed-header and viewport-fit-cover fixtures checked on iOS 26; no duplicated top safe-area padding |
| Liquid Glass | Final native SwiftUI regular-glass address bar verified over white and black HTTPS page content; address and action glyphs adapt contrast |

## Local build environment

On October 16, the simulator executable required `EXCLUDED_SOURCE_FILE_NAMES=Assets.xcassets` on the
command line because `/Users/sk2andy/Library/Developer` was owned by `root:wheel`. No source or project
setting excluded the assets. Both official simulator runtimes reached the same directory permission
failure during app-icon compilation.

On October 17, the user restored ownership to `sk2andy:staff` with administrator authorization. Xcode
and the default CoreSimulator device set are now writable. A stale Kotlin/Native compilation cache
referencing the earlier temporary build home was regenerated. The complete simulator build then
passed, including asset catalog and app-icon compilation, without the asset-exclusion argument.
The freshly signed build was installed on the restarted Candy simulator.

The previous device state was cloned through `simctl` into the normal simulator device list as
`Candy iOS Parity`, UUID `51BD5EE8-E7B1-42CE-8D76-660C6170CD03`. The original isolated device remains
shut down with its contents intact. Simulator was reopened and its Candy window raised for review.

The dedicated simulator remains separate from normal devices. Android regression comparisons used
a temporary clean baseline checkout, removed after testing. No commits or pull requests were created.

## Issues found and fixed during native review

| Issue | Fix and evidence |
| --- | --- |
| Default Candy profile lost isolation on restart | Replay default metadata through shared mutations before creating engines; 9 native restore checks and simulator restart passed |
| Isolation replacement recursively constructed WebViews | Debugger confirmed `init → Topping attach → command notification → synchronizeTabs → init`; command notifications now project menu state only. Native isolation on/off and cold restart passed |
| Replaced Topping owner shadowed the new engine | Retire same-tab bridge handlers/documents/commands before registering the replacement; retain frame/private/message-WebView checks |
| Web-content process death retained downloads | Cancel owner transfers before reporting a crashed session |
| Explicit Snooze wake retained old Reader/Find surfaces | Dismiss source-bound features and speech before selecting the restored tab; automatic due restoration still preserves current selection |
| Remote-profile private tab disappeared on sync | Create private tab in an eligible local profile; five new common regressions cover reconciliation, ownership and capacity |
| Native symbols and switches produced opaque rectangles/clipping | Render static SF glyphs as alpha-preserving Compose bitmaps; fit live native switches within their surface; simulator visual check passed |
| Glass foreground became unreadable over dark pages | Apply native SwiftUI regular glass to the framed content so its foreground adapts with the material; final simulator check passed |

Actual OS notification delivery, background/resumable downloads, advanced Capsule flows and device-specific
permission prompts are not covered by these checks. iOS 18 lacks the public obscured-content-inset API
used on iOS 26; its cover-page safe-area limitation remains documented in the native host guide.

## October 17: address-pill compaction and manual parking

The native address bar previously had a fixed size and advertised docking as unsupported. WebKit now
observes its own scroll view without replacing its delegate and sends document-relative offsets through
the physically shared Android `BrowserChromeScrollRules`. Shared acceptance rejects background,
replaced and closed renderers; navigation and selected-session changes reset the baseline.

`AddressBarDockPlacement`, `AddressBarDockingRules`, gesture constants and dock-offset geometry are
also physically shared. SwiftUI supplies the compact domain pill, remembered-side park button and
52-by-48-point edge handle. A tap restores without keyboard focus; a 320-ms hold starts two-dimensional
repositioning with the existing resistance, breakaway and snap policy. Active parking and remembered
edge/height persist separately without page data. Blank tabs retain full address entry.

| Regression gate | Result |
| --- | --- |
| Shared Kotlin on the dedicated iOS simulator | 325 passed, including nine new scroll/dock bridge regressions |
| Full/Foss Android unit tests | 1,996 passed per variant |
| Full/Foss lint and debug assembly | Passed |
| Android address-bar docking instrumentation | 19/19 passed on dedicated `emulator-5842`; emulator shut down afterward |
| Full native iOS build | Passed with assets enabled |
| Native XCUITest | Passed: downward compaction, upward expansion, parking, cross-edge/height hold-and-drag, restart persistence, restoration without keyboard and blank-tab entry |

The XCUITest target hosts its own ephemeral loopback page and uses real coordinate scroll and
half-second hold-and-drag events. It accepts arbitrary previously remembered park positions. Test
execution targets only `51BD5EE8-E7B1-42CE-8D76-660C6170CD03`, with `ARCHS=arm64`,
`ONLY_ACTIVE_ARCH=YES` and parallel simulator testing disabled. Other concurrent simulator devices
are not used. Automatic control-occlusion parking remains a separate Android-only adapter.
