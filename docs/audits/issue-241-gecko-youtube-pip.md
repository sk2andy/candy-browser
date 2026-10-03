# Issue 241: YouTube PiP ancestor feedback

| Source / environment | Detail |
| --- | --- |
| [Issue 241](https://github.com/sk2andy/candy-browser/issues/241) | Gecko fullscreen → Android Home → PiP → return; recurring vertical movement, black bands and flicker |
| Live page | [Big Buck Bunny on YouTube](https://m.youtube.com/watch?v=aqz-KE-bpKQ); actual mobile YouTube, decoded 640 × 360 video |
| Device | Dedicated `codex_issue241_root`, `emulator-5596`, Android 16 / API 36, host GPU, portrait |
| App | FullDebug, `dev.sk2andy.materialbrowser.linkpeek`, bundled Gecko/content bridge |
| Local environment adjustments | HTTP/3 disabled through temporary Gecko configuration after network stalls; Play services/Play Store disabled on this disposable emulator after boot ANRs |
| Date | 2026-10-03 |

## Reproduction and fix

Repeated PiP presentation classified ancestor clipping and transforms using computed styles.
Candy's own PiP CSS had already neutralized those properties, so the next pass removed the
ancestor marker. The site's original transform/clipping then became visible again and the next
pass reapplied the marker. Playback events and scheduled retries kept this feedback loop alive.

The bridge now retains an owned ancestor while it remains in the presented video's ancestor
chain. It classifies newly encountered ancestors and applies only changed markers. Reparenting
removes obsolete ownership; return/cancellation still restores all temporary attributes, styles,
native controls and measured offsets.

`scripts/gecko_inline_media.test.mjs` executes the production presentation and cleanup functions
with computed styles that reflect Candy's own overrides. The new regression failed on the
baseline's second presentation. It verifies stable repeated ownership without redundant writes,
new clipping ancestors, reparenting and full cleanup. A separate review found no actionable
findings in the focused change.

## Actual YouTube observations

Native input tapped YouTube Play and Enter full screen. The temporary instrumented probe pressed
Android Home, waited for the real `Activity.isInPictureInPictureMode` callback and checked the
existing renderer. Return used the Android launcher intent delivered to the existing activity;
the probe waited for actual PiP exit and completed layout restoration. No local video page or
synthetic PiP callback was used for these live checks.

DOM instrumentation sampled the current video and viewport at approximately 100 ms intervals
and observed ancestor marker mutations. Pixel recordings were also inspected. The table compares
the first 27 seconds of PiP, ignoring the first two seconds for geometry checks. Sampling slows
under baseline layout churn, so counts differ. A geometry mismatch means top offset above one
CSS pixel or video-height/viewport-height ratio differing from 1 by more than 0.01. The Android PiP
window sizes differed between runs; measurements use viewport-relative geometry.

| Observation | Baseline | Fixed |
| --- | --- | --- |
| Ancestor marker mutations in first 27 seconds | 1,400 | 2, one per protected ancestor at entry |
| Geometry mismatches after entry grace period | 65 / 114 samples | 0 / 228 samples |
| Video top | Alternated between 0 and 48 CSS pixels | 0 CSS pixels |
| Video height / viewport height | Repeatedly dropped to approximately 0.253 | Approximately 0.998 |
| Recording | Recurring large black bands and black frames | Stable video during the comparison interval |
| Return to page | Native restoration completed; old-video DOM sampler became detached | Current video at `(0, 97.07)`, `414 × 233`; playback advancing, readyState 4, no PiP ancestor markers |

Local evidence includes `issue241-youtube-vorher-nachher.mp4`, a 25-second side-by-side comparison
of the baseline recording at 00:25–00:50 and the fixed recording at 00:30–00:55. Both run in real
time; the lower-screen PiP regions are cropped equally and labelled. The different Android PiP
window sizes are disclosed in the video. Unmodified full-screen recordings remain available as
`issue241-baseline-final.mp4` and `issue241-fixed.mp4` in the local investigation artifacts.

The final fixed probe repeated the journey twice in the same tab. Both cycles retained the exact
host, inner GeckoView and SurfaceView through real PiP entry and return. First cycle had zero
geometry mismatches in 192 settled samples. Second cycle had one mismatch in 179 samples when
YouTube replaced its video element: ownership was removed/reapplied for that replacement, and
the video returned to the viewport origin by the next sample, approximately 149 ms later.
This isolated replacement transition remains distinguishable from the baseline's continuous
oscillation. Second-return DOM collection missed the probe's teardown; native identity and
restoration assertions passed for both returns.

## Verification

| Check | Result |
| --- | --- |
| FullDebug unit tests | 1,766 passed; no failures, errors or skips |
| FossDebug unit tests | 1,766 passed; no failures, errors or skips |
| `node --test scripts/gecko_inline_media.test.mjs scripts/gecko_fullscreen_video_layout.test.mjs` | 104 passed in isolated snapshot; final working-copy rerun passed 119 after concurrent work added tests |
| `lintFullDebug lintFossDebug` | Passed in isolated worktree |
| `assembleFullDebug assembleFossDebug assembleFullDebugAndroidTest` | Passed in isolated worktree |
| Live baseline / fixed single-cycle probes | Both completed actual Home and launcher-return journeys; baseline visual/DOM defect reproduced |
| Final live fixed probe, two cycles | `OK (1 test)`; same GeckoView and SurfaceView on both entry/return cycles |
| Focused review | No actionable findings |

Earlier emulator attempts failed before completing the live journey because of boot/network
problems and an assertion on UiDevice's Home return value. They are not counted as passes.
The successful probe asserts actual Android mode instead of that input API's Boolean result.

Live checks cover Android 16 on an emulator and launcher return. Samsung hardware, the SystemUI
PiP expand control, and a signed release APK remain unverified. The temporary operator-driven
probe, debugging configuration, recordings and DOM samples are local investigation artifacts;
the deterministic JavaScript regression is the repository test intended for ongoing use.

## Follow-up: fullscreen down-chevron

The Pixel screenshot `Screenshot_20261003-170734.png` showed Candy's internal mini-player
(dots/up/close controls), a blank selected-page surface and a YouTube logo inside the small host.
This is separate from Android PiP. The native down-chevron called `minimizeFullscreenVideo()`,
which transferred the entire selected GeckoView into Candy's overlay. The browser viewport then
had no renderer. That operation did not request the content bridge's video-only preparation;
the precise site fullscreen/rendering disruption behind the logo was not established.

The user chose return to the YouTube page and requested removing the down-chevron. Both native
expanded-fullscreen buttons were removed, together with their unused parameters, import, test tag
and availability getter. Existing fullscreen return and tab-departure mini-player behavior remain
in their existing owners. The translations remain because the content player still uses the label.

| Follow-up check | Result |
| --- | --- |
| Dedicated live device | `codex_issue241_chevron`, `emulator-5604`, API 36, host GPU; same temporary network/debug adjustments as above |
| Real mobile YouTube, automatic Candy mode | Native Play → upward swipe → fullscreen → downward swipe → whole YouTube page; video visibly continued |
| Final operator probe | `OK (1 test)`; required current selected DOM fullscreen and eligible playback before checking absence of the native chevron; exact GeckoView and SurfaceView retained on return |
| Return pixels | Recording shows the decoded inline video, title/channel, comments, recommendations and browser chrome; no empty selected-page surface |
| Full/FOSS lint and debug assembly | Passed in isolated worktree |
| Focused production review / whitespace check | Passed, no actionable findings |
| Pixel deployment | Tested FullDebug APK installed as `Candy Link Peek`; deployment does not change rotation settings |

The initial native-fullscreen check returned through YouTube's own exit control. Android's first-use
fullscreen education initially consumed/interfered with inputs; native Back is not claimed as a live
pass. The final check used Candy's actual upward/downward gestures after dismissing that education.
The earlier two-cycle probe was tightened to require current content fullscreen; only the final
single-cycle probe is counted for this follow-up.

`issue241-youtube-vollbild-rueckkehr.mp4` is a local 39-second, real-time evidence clip, taken from
00:10–00:49 of `issue241-chevron-final.mp4` and scaled to 720 × 1600 without cropping. It shows
fullscreen without the native chevron, followed by restored page content and continued playback.
The temporary live probe is retained with local investigation artifacts, not shipped as a network-
dependent repository test.
