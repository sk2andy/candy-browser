# Picture-in-picture agent guide

Use this guide before changing HTML media detection, fullscreen video, the in-app mini-player or
Android picture-in-picture. The shorter product-level contract remains in
[`runtime-and-navigation.md`](runtime-and-navigation.md#web-media-fullscreen-and-picture-in-picture).

## Supported paths

| Trigger | Source surface | Presentation path |
| --- | --- | --- |
| Top-level page calls `requestPictureInPicture()` | HTML5 video in main frame | Trusted bridge request → pinned WebView → explicit Android PiP |
| Embedded page calls `requestPictureInPicture()` | HTML5 video in HTTP(S) iframe | User-activated Chromium custom view → explicit Android PiP |
| User backgrounds Candy with visible video playing | Top-level HTML5 video | Active channel → isolated WebView presentation → Android auto-enter PiP |
| User backgrounds Candy with visible embedded video playing | HTML5 video in HTTP(S) iframe | Frame visibility relay → isolated iframe chain and video → Android auto-enter PiP |
| User changes tabs while eligible video plays | Top-level or embedded HTML5 video | Same isolated WebView presentation → in-app mini-player |

Canvas-only players, unsupported DRM surfaces and hostile player scripts remain best-effort.

## Ownership map

| Owner | Responsibility | Main source |
| --- | --- | --- |
| Android Activity | PiP capability, params, auto-enter, explicit entry, mode callbacks and return layout | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt) |
| Browser controller | Channel identity, eligibility, presentation pinning, fullscreen session identity, lifecycle cleanup and media publication | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Native contract and rules | Bounded bridge parsing, commands, request eligibility and media scoring | [`WebMediaContract.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/WebMediaContract.kt) |
| Document-start bridge | HTML media telemetry, PiP compatibility API, playback commands, presentation styles and iframe relay | [`WebMediaBridgeScript.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/WebMediaBridgeScript.kt) |
| Fullscreen policy | Custom-view placement and Android PiP eligibility | [`FullscreenVideoRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/FullscreenVideoRules.kt) |
| Compose host | Stable custom-view or WebView surface above browser chrome | [`FullscreenVideoOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FullscreenVideoOverlay.kt) |
| Background playback | Android media session, controls and foreground service | [`WebMediaPlaybackService.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/WebMediaPlaybackService.kt) |

Keep `MainActivity` and `BrowserController` as orchestration. Put new deterministic eligibility or
state decisions in focused rules and unit-test them without Android when possible.

## Identity and trust boundaries

Every accepted media endpoint is bound to all of these values:

| Identity | Why it matters |
| --- | --- |
| Tab and profile | Page cannot select another tab or cross private/regular boundaries |
| WebView instance | Replaced or destroyed views cannot retain authority |
| Navigation generation | Late callbacks from a previous page become stale |
| Document and media IDs | Commands return to the exact reported element |
| Source origin and main-frame flag | Native code distinguishes top-level and embedded strategies |
| Frame-specific reply proxy | Native commands cannot be redirected through page-supplied routing data |

Preserve these invariants:

- Private media may be observed for local lifecycle cleanup, but must never create Android PiP,
  an in-app mini-player, a media notification or persistent state.
- Explicit page PiP requires a current selected regular tab, eligible video, transient user
  activation and applicable Permissions Policy.
- Resolve a page request only after `onPictureInPictureModeChanged(true)`. A successful
  `enterPictureInPictureMode()` return value only means Android accepted the request for processing.
- Keep at most one pending page request. Timeout, navigation, removal, stop or destruction must
  reject it and unwind presentation state idempotently.
- Pin the exact requesting channel or exact matching fullscreen session. Never reselect a different
  video after accepting a page request.
- Use separate credentials for native bridge authorization and iframe presentation relay.
- Embedded auto-PiP eligibility must include visibility through every iframe ancestor. Hidden,
  transparent and offscreen frames cannot outrank visible media.
- Relay receivers verify the direct `Window` source-to-frame relationship before changing styles.
- Keep pending requests, channels, fullscreen sessions and presentation ownership memory-only.

## Lifecycle states

| State | Owner | Exit conditions |
| --- | --- | --- |
| Reported channel | Controller | End/removal, document gone, navigation, tab close or WebView destruction |
| Fullscreen custom-view session | Controller and `WebChromeClient` | Page exits fullscreen, host dismisses, navigation or PiP return cleanup |
| WebView presentation | Controller and document-start bridge | PiP cancellation/return, stop, media end or owner invalidation |
| Pending page PiP request | Controller | Confirmed Android entry, rejection, timeout or any identity invalidation |
| Active page PiP request | Controller | Android mode exit, navigation, removal or destruction |
| Android PiP transition | Activity and controller | Mode callback, cancellation, stop or return-layout completion |

Repeated mode, hide, navigation and cleanup callbacks must remain harmless. Do not rely on one
callback ordering across Android or WebView versions.

## Embedded video strategies

Explicit iframe requests and automatic background PiP intentionally use different paths:

| Path | Required proof | Reason |
| --- | --- | --- |
| Explicit website button | Chromium fullscreen custom view for the exact video | Chromium consumes the user activation and provides a video-only surface |
| Automatic background transition | Playing eligible channel plus verified visible iframe chain | No page user activation exists when Android Home starts auto-enter |

For automatic presentation, each injected frame computes its direct child iframe visibility and
relays the cumulative ratio. Presentation commands isolate the video, then each containing iframe,
up to the main document. Normal inline styles are restored only when they still match Candy's
applied values, so page changes made during PiP are not overwritten blindly.

## Change checklist

| Change | Required companion work |
| --- | --- |
| Add or change bridge payload | Bound it in `WebMediaContract`, validate identity in controller, add parser/rule unit tests |
| Add a bridge command | Route only through stored reply proxy, make duplicate delivery safe, add instrumented command coverage |
| Change eligibility | Update pure rules and cover private, stale, hidden, paused, audio and zero-size cases |
| Change Activity PiP entry | Cover accepted, rejected and missing/late mode callbacks in Activity tests |
| Change iframe relay | Cover visible, offscreen, nested/cross-origin and restoration behavior |
| Change presentation CSS | Cover style drift, DOM moves, repeated preparation and exact restoration |
| Change cleanup | Cover navigation, element removal, tab close, WebView destruction and PiP return |

## Verification

Each agent session must reserve one emulator and use its explicit serial for all device commands.
Never share an emulator or use a physical device for automated Android tests.

Set `CANDY_EMULATOR_SERIAL` to the emulator assigned to the current agent session before using the
commands below.

| Layer | Minimum check |
| --- | --- |
| Contract and pure rules | `./gradlew testFullDebugUnitTest testFossDebugUnitTest` |
| Bridge or WebView behavior | `ANDROID_SERIAL=$CANDY_EMULATOR_SERIAL ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.sk2andy.materialbrowser.browser.WebMediaBridgeInstrumentedTest` |
| Activity PiP lifecycle | Run `FullscreenVideoActivityInstrumentedTest` on the same API 34+ session emulator |
| Fullscreen/overlay placement | Run `FullscreenVideoInstrumentedTest` and `FullscreenVideoOverlayInstrumentedTest` |
| Android integration | `./gradlew lintFullDebug lintFossDebug assembleFullDebug assembleFossDebug` |

Run deterministic tests first. Treat live checks on `anichi.to` and `reanime.cz` as compatibility
smoke tests because their player hosts and markup can change independently of Candy.

## Debug lookup

| Symptom | Inspect first |
| --- | --- |
| Website reports PiP unsupported | Document-start script installation, `pictureInPictureEnabled`, frame policy and private mode |
| Website button does nothing | User activation, exact pending request, matching custom-view session for iframe video |
| Home does not enter PiP | Active channel, cumulative visibility, private flag and Activity auto-enter params |
| Wrong video appears | Channel scoring and whether exact request/session identity was replaced by fallback selection |
| Page chrome appears in PiP | Presentation relay, iframe source mapping and full ancestor isolation |
| Video pauses during transition | Playback expectation, `keep-playing` command and suppressed page pause reconciliation |
| Player stays fullscreen after return | `picture-in-picture-left`, custom-view dismissal and return-layout cleanup |
| Notification survives media end | Channel removal, system media state publication and playback-service ownership |

Useful device checks, always with the session's explicit emulator serial:

```sh
adb -s "$CANDY_EMULATOR_SERIAL" shell dumpsys activity activities
adb -s "$CANDY_EMULATOR_SERIAL" shell dumpsys media_session
adb -s "$CANDY_EMULATOR_SERIAL" logcat -d | rg -i 'picture.?in.?picture|entered-pip|webmedia'
```
