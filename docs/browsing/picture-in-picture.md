# Picture-in-picture agent guide

Use this guide before changing HTML media detection, fullscreen video, the in-app mini-player or
Android picture-in-picture. The shorter product-level contract remains in
[`runtime-and-navigation.md`](runtime-and-navigation.md#web-media-fullscreen-and-picture-in-picture).

## Supported paths

| Trigger | Source surface | Presentation path |
| --- | --- | --- |
| Gecko fullscreen video enters Android PiP | Selected regular Gecko tab with active, playing video | Gecko `MediaSession.Delegate` metadata → same GeckoView reparented into Candy's fullscreen host → Android PiP |
| User switches tabs while Gecko fullscreen video plays | Selected regular Gecko tab | Same GeckoView stays in Candy's mini-player host |

Canvas-only players, unsupported DRM surfaces and hostile player scripts remain best-effort.

Gecko's native media-session delegate supplies playback state, transport commands and fullscreen
element metadata.
Candy only offers PiP after Gecko has reported an active, playing, fullscreen video with non-zero
dimensions and at least one video track. The existing GeckoView and GeckoSession are moved into the
fullscreen host; Candy never creates a second renderer or a second address bar.

Android's PiP mode callback must also be forwarded to Gecko's `CompositorController`. Without this
signal Android can create the PiP window while Gecko's texture compositor stops producing frames,
which leaves a white, non-playing surface. Candy forwards enter and exit exactly once to the owning
session. Candy pre-arms Gecko immediately before the system entry call, then treats the platform
mode callback as confirmation; a rejected transition rolls the signal back. It also keeps only that
session active while the Activity pauses, retries the expected playback command across the first
two seconds of the compositor transition, and keeps the transition alive for five seconds so loaded
emulators cannot tear down the renderer prematurely. User-initiated Play/Pause commands remain
authoritative and cancel pending retries.

Candy normally uses Gecko's texture backend so blur, clipping and tab motion keep working. Candy's
outer browser-content host keeps the same identity throughout the transition. During Android PiP
only, that host releases the GeckoSession from its texture-backed inner GeckoView and immediately
attaches it to a freshly initialized surface-backed GeckoView; on return it creates a fresh
texture-backed GeckoView the same way. A runtime backend mutation can leave Gecko's new SurfaceView
without a compositor buffer, so it must not be used for this transition. The outer host,
GeckoSession and tab identity never change.

The Home gesture requests PiP synchronously from `onUserLeaveHint`; Android auto-enter remains a
fallback. This puts Gecko into PiP before the Activity background lifecycle can make a page such as
YouTube pause its fullscreen media.

GeckoView 140 does not expose element geometry for ordinary inline video through its native media
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
| Compose host | Stable GeckoView surface above browser chrome | [`FullscreenVideoOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FullscreenVideoOverlay.kt) |
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
- Keep the same session and outer content-host identity for PiP. A fresh inner GeckoView may be
  created solely to initialize the required compositor backend; never create another session or
  select another tab.
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
| Android integration | `./gradlew lintFullDebug lintFossDebug assembleFullDebug assembleFossDebug` |

Run deterministic tests first. Treat live checks on `anichi.to` and `reanime.cz` as compatibility
smoke tests because their player hosts and markup can change independently of Candy.

## Debug lookup

| Symptom | Inspect first |
| --- | --- |
| PiP unavailable | Gecko `MediaSession` must report selected, regular, active, playing fullscreen video with dimensions and track |
| Wrong view in PiP | Verify selected session and bound Gecko view identities before reparenting |
| Video pauses during transition | Inspect Gecko media-session playback state and Android PiP mode callback ordering |
| PiP window is white | Verify the exact owning Gecko session received `CompositorController.onPipModeChanged(true)`, the PiP SurfaceView owns a non-zero compositor buffer, and the transition timeout did not close the session |
| Player stays fullscreen after return | Inspect same-session host reattachment and return-layout completion |
| Notification survives media end | Inspect inactive Gecko media state and BrowserMedia system-session publication |

Useful device checks, always with the session's explicit emulator serial:

```sh
adb -s "$CANDY_EMULATOR_SERIAL" shell dumpsys activity activities
adb -s "$CANDY_EMULATOR_SERIAL" shell dumpsys media_session
adb -s "$CANDY_EMULATOR_SERIAL" logcat -d | rg -i 'picture.?in.?picture|gecko.*media'
```
