# Issue 223: Candy Player fullscreen, Off and seeking

| Issue | Implemented behavior | Uploaded device evidence |
| --- | --- | --- |
| [224](https://github.com/sk2andy/candy-browser/issues/224) | Retain the acknowledged video through transient clipping and release native fullscreen header offsets; restore decoded inline video and surrounding content. | [Candy return](https://github.com/user-attachments/assets/6de0394c-3a97-495b-b400-e238bc8f8b4d), [native return](https://github.com/user-attachments/assets/0a51d1ec-2ac5-409f-9cc6-e385fa8cdc08) |
| [225](https://github.com/sk2andy/candy-browser/issues/225) | Scope viewport sizing to the fullscreen video and its ancestor chain; remove owned styles on exit, replacement, Off and page cleanup. | [Website and Candy fullscreen](https://github.com/user-attachments/assets/a55158a3-a2b5-49e0-a4c9-4a4a21794771) |
| [226](https://github.com/sk2andy/candy-browser/issues/226) | Persist an Off choice, reject pending Candy opens and restore website controls without pausing playback. | [Settings, active-player disable and native playback](https://github.com/user-attachments/assets/10bdfbad-ed69-4bf4-922e-62ccce439dfb) |
| [227](https://github.com/sk2andy/candy-browser/issues/227) | Seek backward/forward with trusted double taps; clamp to seekable bounds and show localized actual-distance feedback. | [Inline and fullscreen seeking](https://github.com/user-attachments/assets/56c61d57-1055-4974-b746-fa5c983c464d) |

## Verification

| Check | Result |
| --- | --- |
| Full/FOSS unit tests | 1,724 passed per variant; zero failures, errors or skipped tests |
| Production Gecko handler, fullscreen-layout and safe-area Node suites | 154 passed |
| Full/FOSS lint and debug assembly | Passed |
| Settings Off choice and persistence | Passed |
| Disable active Candy Player; native playback/fullscreen/return | Passed |
| Acknowledged Candy fullscreen/page return with decoded pixels and surrounding content | Passed |
| Native YouTube-style fullscreen return with normal bounds, decoded pixels and surrounding content | Passed |
| Website/Candy portrait fullscreen alignment and owned-style cleanup | Passed |
| Native-input double taps in inline/fullscreen with lower and duration bounds | Passed |

Each agent used a dedicated emulator with an explicit ADB serial. Device evidence uses the actual
app and decoded local VP8 fixtures on API 37. The recordings are not live YouTube tests and do not
verify the original Android 16 phone. Pixel assertions require decoded cyan video; a fallback
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
The native and acknowledged Candy return paths each retain their own device oracle. Live YouTube
verification on the original Android 16 phone remains outside this fixture evidence.
