# Runtime and navigation

## Ownership

| Layer | Responsibility | Entry points |
| --- | --- | --- |
| Activity | Android lifecycle, incoming intents, permission/file chooser launchers, root theme, system picture-in-picture | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt) |
| Controller | WebView creation, tab/profile state, navigation, persistence coordination, platform and fullscreen-video callbacks | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Compose | Render controller state and forward user actions | [`BrowserScreen.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserScreen.kt), [`StatusBarFrostedGlass.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/StatusBarFrostedGlass.kt), [`FullscreenVideoOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FullscreenVideoOverlay.kt) |
| Policies | Resolve input, URLs, settings, media, file chooser and external routes | [`browser/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/) |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized web URLs through shared URI policy |
| Special scheme | `BrowserUriPolicy` → `ExternalAppLauncher` | Block unsafe/internal schemes; require explicit external handling |
| Link Peek | `LinkPeekPreviewNavigationPolicy` → preview WebView | Keep only HTTP(S); do not hand off preview navigation |
| Site Capsule | `CapsuleIntentRules` → capsule runtime | Apply capsule-specific navigation boundary before normal routing |
| Desktop view | `DesktopSiteRules` → controller → WebView settings | Store registrable domains per profile; apply desktop user agent and viewport before navigation |

## Invariants

- Keep activity-result and lifecycle ownership in `MainActivity`; keep browser state in `BrowserController`.
- Route untrusted URLs through existing normalizers. Do not add a second permissive parser.
- Treat WebView callbacks as stale-capable: bind work to tab/request/navigation identity before applying results.
- Keep private tab state memory-only and skip remote suggestions for private input.
- Keep private desktop-view domains memory-only; persist regular domains per profile only.
- Apply desktop-view settings before main-frame navigation and reload matching open tabs when the domain preference changes.
- Keep browser content edge-to-edge. The top safe area redraws a blurred content layer with a
  surface-tinted fade so status-bar icons stay legible without adding WebView padding.
- Add pure policy beside the owning package; leave `BrowserController` as integration wiring.

## TLS trust channels

| Build | Trust anchors | Release asset |
| --- | --- | --- |
| Standard | Android system CA store | `CandyBrowser-v<version>-release.apk` |
| User CA | Android system and user CA stores | `CandyBrowser-v<version>-user-ca-release.apk` |

- Network Security Config is static and app-wide. Android WebView cannot safely switch trust anchors
  from a runtime preference, so broader trust requires installing the explicitly labeled User CA APK.
- Both channels use the same application ID and signing key. Update selection preserves the installed
  channel and rejects a release that contains only the other channel's asset.
- User CA trust applies to all app HTTPS connections, not only rendered pages or a selected profile.
  The settings warning must remain visible in User CA builds.
- `BrowserController.onReceivedSslError` always cancels. Never use `SslErrorHandler.proceed()` to
  approximate user-CA support; valid user-CA chains are accepted before that callback.

## Domain compatibility overrides

| Override | Runtime behavior |
| --- | --- |
| Force vertical scrolling | Removes vertical page scroll locks without changing horizontal overflow |
| Force page zooming | Removes viewport `user-scalable`, minimum-scale and maximum-scale restrictions while preserving other viewport directives |

- Compatibility overrides match the exact current host. Regular tabs persist them per profile;
  private tabs keep them in memory for that tab only.
- Changing an override reloads affected pages. Document-start scripts handle direct navigation and
  commit-visible fallbacks cover redirects whose final host was not known before navigation.

## Web media, fullscreen and picture-in-picture

| Transition | Behavior |
| --- | --- |
| HTML media appears or starts | A document-start bridge observes bounded HTML5 `video`/`audio` state in supported HTTP(S) frames; the frame-specific reply proxy is the only command path back to that player |
| Web page requests fullscreen | `WebChromeClient.onShowCustomView` creates one transient controller-owned custom-view session; the root Compose overlay hosts Chromium's view |
| User selects another regular tab | The current eligible video is pinned and its source WebView moves into the draggable in-app mini-player while non-owning WebViews remain paused |
| App leaves the foreground | The active eligible regular video is pinned before Activity PiP. If Chromium returns its custom view to the page, Candy keeps the same WebView in its existing Android host, raises that host above browser chrome and switches the document to video-only presentation without reparenting the decoder surface. A pre-existing mini-player also keeps one stable Android host while its placement changes. While system PiP expects playback, page-driven background pauses are ignored; explicit system pause and stop commands still take effect |
| System media control is used | The app-owned Android `MediaSession` sends play, pause, stop or seek only through the accepted frame reply proxy |
| Audible audio continues in background | A `mediaPlayback` foreground service owns the visible media notification while the Activity-owned WebView and session remain alive |
| PiP expands back into the app | Android expands the shared WebView surface through a centered source rectangle matching the PiP/video aspect ratio instead of targeting the former inline-video rectangle. Presentation CSS and the prior Android host are then restored without pausing; the page-pause guard remains active until the resumed UI has settled |
| PiP closes or the app stops without entering PiP | Presentation CSS is restored, the owning WebView pauses and normal media gesture policy is restored |
| Media ends, page navigates, crashes, closes, snoozes or is destroyed | Navigation generation and WebView identity invalidate the endpoint; view, script, notification and session cleanup is idempotent |

- The bridge accepts telemetry only from the WebView and current navigation it was installed for,
  rejects non-HTTP(S) origins and bounds every identifier, numeric value and payload. Pages cannot
  select another tab or invoke native actions.
- Media sessions, frame endpoints, metadata, presentation state and mini-player position are
  memory-only and never persisted.
- Private media may be detected transiently for local lifecycle correctness, but never becomes an
  in-app mini-player, Android PiP, system media session or notification.
- System PiP renders only the custom video view or video-isolated source WebView. Onboarding,
  splash, update UI and Candy controls stay outside the PiP surface.
- Inline WebView presentation is limited to a top-level document. Subframe media can still use
  Chromium's native fullscreen custom view, but cannot expand its parent frame through the bridge.
- Compatibility is best effort for HTML5 media. DRM restrictions, canvas-only rendering,
  deliberately hostile players and site-specific visibility policies can still prevent control or
  continued playback.

## Verification

| Change | Check |
| --- | --- |
| Input/URL policy | Matching JVM rule test |
| WebView settings or callbacks | Focused browser instrumented test |
| Web media, fullscreen and PiP policy | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest` and `FullscreenVideoOverlayInstrumentedTest` on API 34+ |
| Android intent routing | Integration unit test plus launch instrumented test when lifecycle matters |
