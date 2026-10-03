# Issue 236: closing Candy Player and YouTube launcher placement

| Source | Reproduction |
| --- | --- |
| [Issue 236](https://github.com/sk2andy/candy-browser/issues/236) | Gecko playback, Candy launcher, fullscreen, Candy Close, return to the page |
| [Submitted recording](https://github.com/user-attachments/assets/ad7157f6-7927-437c-bca4-d61cad66f09f) | Website and Candy fullscreen transitions; after Close, fullscreen persists and subsequent return shows black surrounding content and a narrow/duplicated video |
| Android 16 reproduction | `codex_issue236_root`, `emulator-5584`, Android 16 / API 36, host GPU, portrait |
| Clean verification emulator | `codex_issue236_api34`, `emulator-5584`, Android 14 / API 34, host GPU, portrait |

## Regression and fix

Close previously forwarded `expected=false` before leaving DOM fullscreen. That cleared native
Candy presentation identity while Gecko still owned fullscreen. The subsequent fullscreen exit
could no longer use the controller's acknowledged inline-restoration path.

Close now exits DOM fullscreen while retaining Candy ownership, waits for stable inline video and
control geometry, then forwards the close and restores site controls. It preserves playback. The
bounded wait accepts the controller's restoration generation update but rejects document, video,
controls-instance and mode replacement. Failed exit or timeout leaves the current controls usable.
A delayed content-side close response cannot remove a replacement presentation, including the same video.

Fullscreen exit also publishes a new inset policy. The native open-request gate previously discarded
Close while that revision awaited acknowledgement. Content could remove Candy controls while the
host retained its old open intent; policy acknowledgement then reopened the player. Compatible Close
now reaches the controller immediately for the current published or last acknowledged revision.
It cancels pending opens and retains privacy, navigation and minimum-compatible-revision checks.
Future, unrelated and incompatible revisions remain rejected.

The content/background boundary had a second revision race: Close neither refreshed its exact-video
candidate nor retried a newer compatible policy. Instrumentation without DOM mutations confirmed
stable restoration followed by `forwarded=false`. Close now awaits the exact candidate report and
reuses the bounded two-attempt policy refresh used by Open. Retry requires the same video, controls
host, URL, navigation and mode; background forwarding still requires exact current revision and
nonces. Missing or incompatible retry policy preserves the current controls.

Recognized YouTube launchers now float 16 CSS pixels below the exact video rectangle. Native
settings and captions remain unobstructed. Fullscreen or insufficient space removes the launcher;
stale geometry clicks are rejected. Other sites keep their existing position.

## Device oracle

`GeckoPictureInPictureInstrumentedTest#closingFullscreenCandyPlayerRestoresWholePageAndDecodedInlineVideo`
uses real MainActivity and Gecko, decoded cyan VP8 video, a YouTube-shaped clipped/transformed player,
and surrounding title/channel/comments/recommendation content. Native input taps the launcher,
swipes upward into fullscreen and taps Candy Close. It checks fullscreen/ownership/layout completion,
original inline geometry, unsuppressed site controls, continued playback, decoded pixels and visible
surrounding page at delayed and late checkpoints. Green CSS fallback is not accepted as video.

| Check | Result |
| --- | --- |
| Baseline APK from `8acd59f6`, new Close E2E | Failed: Close left DOM fullscreen active after Candy teardown |
| Full/FOSS unit suites | 1,762 passed per variant; zero failures/errors/skipped |
| Media/fullscreen Node regressions | 119 passed |
| Full/FOSS lint and debug assembly, isolated checkout | Passed |
| Fixed Close E2E on final APK, API 34 | Passed; clean APK, no diagnostic instrumentation |
| Close after 5-second evidence pause | Passed; clean APK |
| Native fullscreen/whole-page return without Candy takeover | Passed, API 34 |
| Player Off: native playback, fullscreen and persisted choice | Passed, API 34 |

Earlier device attempts failed before the journey with decoder initialization, startup ANR,
slow fixture loading and an Android System UI ANR dialog. These are recorded setup failures,
not regression passes. Android 16 became unable to finish reboot under concurrent host load;
clean final acceptance uses the dedicated API 34 emulator. Diagnostic measurements first changed
DOM attributes and affected report timing; non-mutating HTTP telemetry then exposed the rejected
close. All temporary diagnostics were removed before the clean acceptance runs.

Initial reproduction used Android 16; clean final acceptance used Android 14 and a deterministic
YouTube-shaped page. The reporter's OnePlus hardware remains unverified. The automatic address-bar parking mechanism is separate from Candy presentation: visible
bottom website controls can request the existing occlusion-based parking behavior.
