# Issue 302: Remember the address pill side during automatic parking

## Reproduction and cause

Baseline main `c56fd44a34bf17d043afe3aa5f332a577cc22b4f` still routed an
occluded page control to `BrowserController.parkAddressBarOnRight()`. That
explicit action forces the right edge and persists it as the remembered edge.
A previously chosen left edge was consequently lost on automatic parking.

On an isolated API 35 emulator, the new real Gecko and System WebView chat-editor
regressions both failed with expected `Left, 0.35` versus actual `Right, 0.35`.
The focused-input controller regression failed with expected `Left, 0.6` versus
actual `Right, 0.6`. All three passed after the fix.

The occlusion callback now calls the existing `updateAddressBarDocked(true)`
path. It reuses the remembered edge and height. The explicit configurable
right-parking action still forces right. No menu layout or mirroring changes
are included.

## Validation

The final source includes icon commit
`54bdff31bfbc6496a5b0c6cb4143da48ddd35aab` unchanged.

| Check | Result |
| --- | --- |
| `testFullDebugUnitTest`, `testFossDebugUnitTest` | 1,936 tests per distribution, no failures or skips |
| `lintFullDebug`, `lintFossDebug` | Passed; existing warnings remain |
| `assembleFullDebug`, `assembleFossDebug`, `assembleFullDebugAndroidTest` | Passed |
| `node --test scripts/text_input_occlusion.test.mjs` | Passed |
| Real Gecko and WebView chat-editor tests | Both edges and height survive auto-parking, real scrolling, new-document navigation, real page IME open/close, tab/profile switches, pause/resume, portrait/landscape, Activity recreation and fresh Activity/controller creation |
| 1768 x 2208 display override | Both real-engine tests passed again at the wider geometry |
| Process cold starts on public ChatGPT | Each engine/edge combination checked twice after `am force-stop`; visible edge bounds and stored edge/height matched in all eight checks |
| Adjacent instrumented regression suite | 16 tests passed, covering new real-engine regressions, IME/probe lifecycle, stale callbacks, manual restore, drag, remembered-edge chevron, explicit right parking and store round trips |
| Explicit right parking | Still changes a remembered left edge to right |

The public ChatGPT page loaded without login. Cold-start screenshots and UI bounds
were recorded on the actual page, without sending messages. A cookie banner
interrupted one UIAutomator dump; after rejecting non-essential cookies, the
remaining System WebView/right cold-start checks passed.

## Evidence and limits

Before/after instrumentation logs and PNGs, build/lint logs, cold-start UI XML and
screenshots are retained in the task evidence bundle. The final detailed report
records the published commit, CI and Library identifiers.

The automatic occlusion regression uses a deterministic, local fixed chat-editor
fixture through each real engine. The focused-input callback test additionally
uses a controlled engine port. Authenticated ChatGPT conversations, a physical
Fold device/hinge transition, and OEM-specific keyboard behavior were not tested.
The wider emulator display and rotation cover changed viewport geometry, not
physical hinge hardware. FOSS was built, linted and unit-tested; device tests used
FullDebug with both selectable engines. Activity and true process restarts were
checked separately.

The main checkout and unpublished Reader work were not modified. Each final
Android command targeted the dedicated `emulator-5598`; baseline evidence was
captured earlier on a separate dedicated instance. No releases or deployment
workflows were dispatched.
