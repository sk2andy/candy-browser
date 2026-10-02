# Issue 223: Candy Player fullscreen, Off and seeking

| Issue | Implemented behavior | Uploaded device evidence |
| --- | --- | --- |
| [224](https://github.com/sk2andy/candy-browser/issues/224) | Retain the exact acknowledged video through transient fullscreen clipping; restore decoded inline video and surrounding page content after downward return. | [Fullscreen/page return](https://github.com/user-attachments/assets/6de0394c-3a97-495b-b400-e238bc8f8b4d) |
| [225](https://github.com/sk2andy/candy-browser/issues/225) | Scope viewport sizing to the fullscreen video and its ancestor chain; remove owned styles on exit, replacement, Off and page cleanup. | [Website and Candy fullscreen](https://github.com/user-attachments/assets/a55158a3-a2b5-49e0-a4c9-4a4a21794771) |
| [226](https://github.com/sk2andy/candy-browser/issues/226) | Persist an Off choice, reject pending Candy opens and restore website controls without pausing playback. | [Settings, active-player disable and native playback](https://github.com/user-attachments/assets/10bdfbad-ed69-4bf4-922e-62ccce439dfb) |
| [227](https://github.com/sk2andy/candy-browser/issues/227) | Seek backward/forward with trusted double taps; clamp to seekable bounds and show localized actual-distance feedback. | [Inline and fullscreen seeking](https://github.com/user-attachments/assets/56c61d57-1055-4974-b746-fa5c983c464d) |

## Verification

| Check | Result |
| --- | --- |
| Full/FOSS unit tests | 1,724 passed per variant; zero failures, errors or skipped tests |
| Production Gecko handler and fullscreen-layout Node suites | 81 passed |
| Full/FOSS lint and debug assembly | Passed |
| Settings Off choice and persistence | Passed |
| Disable active Candy Player; native playback/fullscreen/return | Passed |
| Acknowledged Candy fullscreen/page return with decoded pixels and surrounding content | Passed |
| Website/Candy portrait fullscreen alignment and owned-style cleanup | Passed |
| Native-input double taps in inline/fullscreen with lower and duration bounds | Passed |

Each agent used a dedicated emulator with an explicit ADB serial. Device evidence uses the actual
app and decoded local VP8 fixtures on API 37. The recordings are not live YouTube tests and do not
verify the original Android 16 phone. Pixel assertions require decoded cyan video; a fallback
background is not accepted as playback evidence. The final integrated APK was used for the focused
Off, fullscreen and double-tap runs.

## Remaining compatibility finding

The native-only YouTube-style fixture retains pre-existing inline-return clipping with both the
baseline and changed code: the player returns about 49 CSS pixels above its clipping bounds.
Fullscreen sizing improves from approximately 414 × 233 to 414 × 924, but that separate native
return path remains unresolved. The acknowledged Candy return path has its own decoded-video and
whole-page regression test. This distinction is documented in issue 225; no native-only return
fix or live YouTube verification is claimed.
