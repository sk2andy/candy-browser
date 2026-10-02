# Issue 223: Candy Player fullscreen, Off and seeking

| Issue | Implemented behavior | Uploaded device evidence |
| --- | --- | --- |
| [224](https://github.com/sk2andy/candy-browser/issues/224) | Retain the acknowledged video through transient clipping and release native fullscreen header offsets; restore decoded inline video and surrounding content. | [Candy fixture](https://github.com/user-attachments/assets/6de0394c-3a97-495b-b400-e238bc8f8b4d), [native fixture](https://github.com/user-attachments/assets/0a51d1ec-2ac5-409f-9cc6-e385fa8cdc08), [live YouTube](https://github.com/user-attachments/assets/6a75ea22-1a7d-47ac-8777-d931df7d8ccc) |
| [225](https://github.com/sk2andy/candy-browser/issues/225) | Scope viewport sizing to the fullscreen video and its ancestor chain; remove owned styles on exit, replacement, Off and page cleanup. | [Website and Candy fullscreen](https://github.com/user-attachments/assets/a55158a3-a2b5-49e0-a4c9-4a4a21794771) |
| [226](https://github.com/sk2andy/candy-browser/issues/226) | Persist an Off choice, reject pending Candy opens and restore website controls without pausing playback. | [Settings, active-player disable and native playback](https://github.com/user-attachments/assets/10bdfbad-ed69-4bf4-922e-62ccce439dfb) |
| [227](https://github.com/sk2andy/candy-browser/issues/227) | Persist independent backward/forward distances; seek with trusted double taps, clamp to seekable bounds and show localized actual-distance feedback. | [Haptic sliders](https://github.com/user-attachments/assets/94fb40d4-7b0c-4fef-997b-9cf554fcad4f), [settings and configured seeking](https://github.com/user-attachments/assets/7e02e4b9-b10a-4744-9432-64caa8f1a6ae), [default seek bounds](https://github.com/user-attachments/assets/56c61d57-1055-4974-b746-fa5c983c464d) |

## Verification

| Check | Result |
| --- | --- |
| Full/FOSS unit tests | 1,728 passed per variant; zero failures, errors or skipped tests |
| Production Gecko handler, fullscreen-layout and safe-area Node suites | 160 passed |
| Full/FOSS lint and debug assembly | Passed |
| Settings Off choice and persistence | Passed |
| Disable active Candy Player; native playback/fullscreen/return | Passed |
| Acknowledged Candy fullscreen/page return with decoded pixels and surrounding content | Passed |
| Native YouTube-style fullscreen return with normal bounds, decoded pixels and surrounding content | Passed |
| Website/Candy portrait fullscreen alignment and owned-style cleanup | Passed |
| Native-input double taps in inline/fullscreen with lower and duration bounds | Passed |
| Independent distance settings, store normalization, Off retention and Activity recreation | 4 device tests passed |
| Discrete slider callbacks/disabled state, actual-seconds accessibility, haptic deduplication/fallback and RTL keyboard | 3 device tests passed on API 37; integrated main APK repeat passed, 3.786 s |
| Configured 5/15 then active 15/5 distances, same overlay/video, genuine 45-second seekable fixture | Device test passed, 44.363 s |

The configured-distance recording starts with real MainActivity Settings selecting 5 seconds
backward and 15 forward, with both persisted preferences verified. Its second segment is a
separate native-input fixture test that applies 5/15 and then 15/5 through the production
controller, checks unchanged video/control identity and paused state, and seeks inline/fullscreen.
The isolated production SettingsPage interaction test also passed in the integrated APK.

Each agent used a dedicated emulator with an explicit ADB serial. Device evidence uses the actual
app and decoded local VP8 fixtures on API 37. The additional manual live YouTube check is described
below. Neither environment verifies the original Android 16 phone. Pixel assertions require decoded cyan video; a fallback
background is not accepted as playback evidence. The integrated main APK was used for the focused
Off, fullscreen and double-tap runs; the final supplemented main APK also passed the native
YouTube-style return test in a fresh process.

## Native return regression

The native-only YouTube-style fixture exposed a separate safe-area ownership bug: Gecko presents
the fullscreen container as fixed, so Candy's page-header classifier added a top-inset rule.
That rule survived return to relative positioning, moving the video about 49 CSS pixels inside
its clipped parent. The baseline lost all sampled lower video pixels; the fixed player returns
to `top: 0` and its original rectangle with upper and lower decoded pixels and page content visible.

Fullscreen roots and descendants now release owned element top rules and cannot acquire new
header offsets. Exit rechecks only the former fullscreen subtree through the existing bounded
queue; still-fixed content regains one authored inset. Three deterministic regressions cover
fullscreen exclusion, author-style preservation and repeated transitions without offset stacking.
The native and acknowledged Candy return paths each retain their own device oracle.

## Live YouTube return check

On 2026-10-02, main `cf4a6513` (FullDebug 0.44.0-debug, Gecko) was tested with actual public
[`m.youtube.com` playback of Big Buck Bunny](https://m.youtube.com/watch?v=YE7VzlLtp-4) by
`@BlenderOfficial`, without sign-in, on a dedicated API 37 emulator in portrait. No local fixture,
server, injected page JavaScript or instrumentation was used for this recording.

| Recording time | Result |
| --- | --- |
| 00:04.6–00:10.0 | Native YouTube fullscreen → Android Back: moving video and title/channel/comments/recommendations/browser toolbar return. |
| 00:15.0–00:23.3 | Candy launcher → upward fullscreen swipe → downward return: moving video and Candy controls return inline; full page visible. |
| 00:28.9–00:31.6 | Pause → resume after return: changing frames and playback time continue. |

Both paths were repeated before the clean 36.5-second recording. The persistent near-black page
was not reproduced in these live sequences. The original Android 16 phone remains unverified.

## Slider settings follow-up

The independent backward and forward distance menus now use Material sliders with equally spaced
stops for 5, 10, 15, 20, 30 and 60 seconds. Rounded value bubbles follow the thin handle during
press, drag or keyboard focus; their reserved height grows with text size. Native Slider touch,
keyboard and accessibility actions remain intact, with localized actual seconds as the state
description. Off and unsupported engines disable both controls without clearing saved values.

Focused instrumentation records platform haptic requests: one segment tick per user stop change,
no duplicate/external/disabled feedback, and clock-tick fallback when segment feedback is unavailable.
The emulator checks event delivery rather than physical vibration strength.

The final main FullDebug APK passed all three slider tests on a separate API 37 emulator.
A real MainActivity visual check at 200% system font scale confirmed that the value bubble
remains readable above the handle without overlap. Full/FOSS lint and debug assemblies passed.

[Slider recording](https://github.com/user-attachments/assets/94fb40d4-7b0c-4fef-997b-9cf554fcad4f):
12.9 seconds of native finger drags in real MainActivity Settings; the final persisted values
were 15 seconds backward and 5 forward. [200% font proof](https://github.com/user-attachments/assets/83c83fa7-b23b-4161-80e3-6d85005ebb38)
shows the adaptive bubble clearance. Light and dark appearances were visually checked.

## Dedicated Player settings page

Settings → Player now owns video autoplay, Candy Player mode and both independent haptic
seek sliders. The Browser page no longer duplicates these controls. Header and native Android
Back return to Settings Home. Existing preference keys, controller updates and runtime policy
are unchanged; autoplay remains independently available when Candy Player is Off.

The shared home has a Player destination, localized title/summary and platform icon adapters.
Android opens the production Player page; the shared/iOS route remains explicitly disabled
until that platform owns equivalent player settings.

| Player-page verification | Result |
| --- | --- |
| Full/FOSS JVM suites | 1,728 passed per variant |
| Shared Settings rules | 200 passed, including route order and unsupported Player gate |
| Browser/Player/slider/navigation device suites | 17 passed on final FullDebug APK, API 37, 41.84 s |
| Full/FOSS lint and debug builds | Passed |

[Player-page recording](https://github.com/user-attachments/assets/5179a8fe-86a4-4565-ac87-2b96b7e90e64)
shows 11.1 seconds of actual MainActivity navigation from Settings Home to Player, native gestures
on both sliders and header Back to Home. The persisted final distances were 30 seconds backward
and 20 seconds forward. The recording was captured on a dedicated API 37 emulator.
