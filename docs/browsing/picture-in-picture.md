# Picture-in-picture agent guide

Use this guide before changing HTML media detection, fullscreen video, the in-app mini-player or
Android picture-in-picture. The shorter product-level contract remains in
[`runtime-and-navigation.md`](runtime-and-navigation.md#web-media-fullscreen-and-picture-in-picture).

## Supported paths

| Trigger | Source surface | Presentation path |
| --- | --- | --- |
| Gecko fullscreen video enters Android PiP | Selected regular Gecko tab with active, playing video | Gecko fullscreen callbacks → original browser-hosted GeckoView → Android PiP |
| User switches tabs while Gecko fullscreen video plays | Selected regular Gecko tab | Same GeckoView stays in Candy's mini-player host |

Canvas-only players, unsupported DRM surfaces and hostile player scripts remain best-effort.

Gecko's native media-session delegate supplies playback state, transport commands and fullscreen
element metadata. `ContentDelegate.onFullScreen` independently owns the page fullscreen lifecycle;
Candy opens a video presentation only after both independent states agree and retains either exit
while Android PiP is active until the confirmed return callback. Candy exits page fullscreen
through GeckoSession's public `exitFullScreen()` API. Media-session callbacks from a
replaced YouTube ad/main session are ignored once that native media-session identity is stale.
Candy only offers PiP after Gecko has reported an active, playing, fullscreen video with non-zero
dimensions and at least one video track. The existing GeckoView and GeckoSession stay in the browser
viewport for expanded fullscreen and Android PiP. Candy never creates a second renderer or a second
address bar. Reparenting is reserved for the user-requested in-app mini-player, outside the Android
PiP transition.

Android PiP aspect ratio and transition source bounds follow Gecko's reported video dimensions.
Invalid dimensions fall back to 16:9; extreme values are clamped to Android's supported PiP range.
During Android's video-only presentation, the bundled content bridge hides non-video page content,
sizes the browser viewport to the media aspect ratio and moves the selected playing video to the
viewport origin. The measured offset correction is required for players such as YouTube whose
transformed player container remains below a fixed site header. Every temporary attribute, style
and offset is removed when PiP preparation is cancelled or PiP returns.

Android's confirmed PiP mode callback is forwarded to Gecko's `CompositorController` exactly once
per state change for the owning session. Preparation never sends this signal: Gecko documents it as
the notification that Android has already changed mode and uses it to apply its Android-PiP media
layout. A rejected transition therefore needs no compositor rollback. Candy keeps only the owning
session active while the Activity pauses, retries the expected playback command across the first
two seconds of the transition, and keeps the transition alive for five seconds so loaded emulators
cannot tear down the renderer prematurely. User-initiated Play/Pause commands remain authoritative
and cancel pending retries.

Candy keeps GeckoView's default SurfaceView backend so frames reach Android's compositor directly.
The browser host, outer Candy Gecko host, inner GeckoView, SurfaceView backend, GeckoDisplay,
GeckoSession and tab identity all remain unchanged from fullscreen preparation through PiP entry,
rotation and return. Blur remains a sibling chrome effect; clipping and tab motion operate on the
stable browser host instead of requiring a texture copy.
Releasing or reattaching the session can make the page leave DOM fullscreen, reflow YouTube chrome
into the PiP window and pause the media. Gecko's `CompositorController.onPipModeChanged` is the only
compositor transition signal. Do not switch backends or remove/add the GeckoView during transition.

The Gecko host reserves Android's mandatory/system gesture insets before dispatching touch to web
content. A Home or Back gesture therefore cannot reach a page long enough to activate a site's
long-press behavior, and Gecko context-menu callbacks are accepted only while the original pointer
remains a focused, stationary, non-PiP long-press candidate. Activity pause also dismisses any
already-visible content action.

The Home gesture prepares and pins the owner from `onUserLeaveHint`. Android 14 and earlier use an
explicit entry request because those releases cannot notify Candy early enough to hide page chrome
before auto-enter captures the Activity. Android 15+ may use its early transition callback with
prepared auto-enter. `onPictureInPictureRequested` handles an explicit system request on every
supported release. Preparation preserves the original host and playback intent, while only
Android's later mode callback tells Gecko that PiP is active.

GeckoView 155 does not expose element geometry for ordinary inline video through its native media
session API. Automatic background PiP and the in-app mini-player therefore remain limited to Gecko
video that has entered fullscreen. Do not infer inline-video eligibility from page-level state.

## Ownership map

| Owner | Responsibility | Main source |
| --- | --- | --- |
| Android Activity | Lifecycle callback forwarding and browser-system-UI coordination | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt) |
| Android PiP coordinator | PiP capability, params, auto-enter, explicit entry, mode state and return layout | [`MainActivityPictureInPictureController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivityPictureInPictureController.kt) |
| Browser controller | Gecko session identity, eligibility, same-view presentation, lifecycle cleanup and media publication | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Fullscreen policy | Gecko-view placement and Android PiP eligibility | [`FullscreenVideoRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/FullscreenVideoRules.kt) |
| Gecko media policy | Autoplay permission and fullscreen-video PiP eligibility | [`GeckoMediaRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/gecko/GeckoMediaRules.kt) |
| Compose hosts | Browser viewport for stable fullscreen/PiP; overlay only for the in-app mini-player | [`BrowserViewport.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserViewport.kt), [`FullscreenVideoOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FullscreenVideoOverlay.kt) |
| Background playback | Android media session, controls and foreground service | [`BrowserMediaPlaybackService.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserMediaPlaybackService.kt) |

Keep `MainActivity` and `BrowserController` as orchestration. Put new deterministic eligibility or
state decisions in focused rules and unit-test them without Android when possible.

## Identity and trust boundaries

Every accepted media endpoint is bound to all of these values:

| Identity | Why it matters |
| --- | --- |
| Tab and profile | Page cannot select another tab or cross private/regular boundaries |
| Gecko session and view | Replaced, closed or detached renderers cannot retain authority |
| Navigation generation | Late callbacks from a previous page become stale |

Preserve these invariants:

- Private media may be observed for local lifecycle cleanup, but must never create Android PiP,
  an in-app mini-player, a media notification or persistent state.
- Gecko media state is memory-only, scoped to the exact tab session, and discarded on navigation,
  deactivation, crash, close or session replacement.
- Android PiP requires a current selected regular tab, an active playing fullscreen Gecko video,
  non-zero dimensions and a video track.
- Keep the same session, view, backend, display and browser-host identity for PiP. Never create a
  replacement renderer, switch backend or reparent the view during entry or return.
- Keep Gecko media state and presentation ownership memory-only.

## Lifecycle states

| State | Owner | Exit conditions |
| --- | --- | --- |
| Gecko media state | Gecko session adapter | Navigation, deactivation, crash, close or replacement |
| Gecko fullscreen presentation | Controller and Compose host | Media ends, host dismisses, navigation, PiP exit or session replacement |
| Android PiP transition | Activity and controller | Mode callback, cancellation, stop or return-layout completion |

Repeated mode, navigation and cleanup callbacks stay idempotent.

## Change checklist

| Change | Required companion work |
| --- | --- |
| Change Gecko eligibility | Update `GeckoPictureInPictureRules`; cover private, stale, paused, audio and zero-size states |
| Change Activity PiP entry | Cover accepted, rejected and missing/late mode callbacks in the Gecko instrumentation suite |
| Change presentation host | Preserve exact Gecko session/view identity; test overview→mini→expanded transitions |
| Change cleanup | Cover navigation, tab close, session replacement and PiP return |

## Verification

Each agent session must reserve one emulator and use its explicit serial for all device commands.
Never share an emulator or use a physical device for automated Android tests.

Set `CANDY_EMULATOR_SERIAL` to the emulator assigned to the current agent session before using the
commands below.

| Layer | Minimum check |
| --- | --- |
| Contract and pure rules | `./gradlew testFullDebugUnitTest testFossDebugUnitTest` |
| Gecko PiP lifecycle | Run `GeckoPictureInPictureInstrumentedTest` on the same API 34+ session emulator |
| Fullscreen/overlay placement | Covered by `GeckoPictureInPictureInstrumentedTest` on the same API 34+ emulator |
| Inline player offset isolation | Local transformed-player fixture in `GeckoPictureInPictureInstrumentedTest` |
| Android integration | `./gradlew lintFullDebug lintFossDebug assembleFullDebug assembleFossDebug` |

Run deterministic tests first. Treat live checks on YouTube, `anichi.to` and `reanime.cz` as
compatibility smoke tests because their player hosts and markup can change independently of Candy.

## Debug lookup

| Symptom | Inspect first |
| --- | --- |
| PiP unavailable | Gecko `MediaSession` must report selected, regular, active, playing fullscreen video with dimensions and track |
| Wrong view in PiP | Verify selected session, browser host, GeckoView, SurfaceView and display stay unchanged |
| Video pauses during transition | Inspect Gecko media-session playback state and Android PiP mode callback ordering |
| PiP shows a logo or stale page frame | Verify no backend switch or view reparent occurred and that the confirmed mode callback reached the exact owning session |
| Player stays fullscreen after return | Inspect same-session host reattachment and return-layout completion |
| Notification survives media end | Inspect inactive Gecko media state and BrowserMedia system-session publication |
| App crashes after rapid Play/Pause | Foreground playback service must promote itself in `onCreate` before validating or stopping a queued start |

Useful device checks, always with the session's explicit emulator serial:

```sh
adb -s "$CANDY_EMULATOR_SERIAL" shell dumpsys activity activities
adb -s "$CANDY_EMULATOR_SERIAL" shell dumpsys media_session
adb -s "$CANDY_EMULATOR_SERIAL" logcat -d | rg -i 'picture.?in.?picture|gecko.*media'
```
