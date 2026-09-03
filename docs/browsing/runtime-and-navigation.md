# Runtime and navigation

## Ownership

| Layer | Responsibility | Entry points |
| --- | --- | --- |
| Activity | Android lifecycle, incoming intents, permission/file chooser launchers, root theme, fullscreen video and system picture-in-picture | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt) |
| Controller | WebView creation, tab/profile state, navigation, persistence coordination, platform and fullscreen-video callbacks | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Compose | Render controller state and forward user actions | [`BrowserScreen.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserScreen.kt), [`StatusBarFrostedGlass.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/StatusBarFrostedGlass.kt), [`FullscreenVideoOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FullscreenVideoOverlay.kt) |
| Policies | Resolve input, URLs, settings, media, file chooser and external routes | [`browser/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/) |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized web URLs through shared URI policy. The optional external-link preview keeps the page memory-only until **Open in Candy** creates a regular tab in the chosen profile; when disabled, the existing immediate-tab path remains unchanged. Root Back returns to the calling app. |
| Explicit special-scheme address | `BrowserUriPolicy` → `ExternalAppLauncher` | Treat typed, pasted or scanned safe schemes as user-authorized app handoffs; keep internal schemes blocked |
| Web link or special scheme | `ExternalNavigationPolicy` → `BrowserUriPolicy` → `ExternalAppLauncher` | Keep HTTP(S) navigation in the current WebView; allow safe main-frame special-scheme handoffs; block unsafe/internal schemes and subframes |
| Link Peek | `LinkPeekPreviewNavigationPolicy` → preview WebView | Keep only HTTP(S); do not hand off preview navigation |
| Site Capsule | `CapsuleIntentRules` → capsule runtime | Apply capsule-specific navigation boundary before normal routing |
| Desktop view | `DesktopSiteRules` → controller → WebView settings | Store registrable domains per profile; apply desktop user agent and viewport before navigation |
| Federated login | `FederatedLoginRules` → controller → Snackbar and `AlertDialog` | Detect only known cross-site identity SDK endpoints; change cookie, user-agent and popup policy only after explicit consent |
| Local userscript | `UserScriptRules` → AndroidX WebKit document-start handler | Require an explicit HTTP(S) pattern, top frame and regular tab; apply full URL exclusions before source runs |

## Invariants

- Keep activity-result and lifecycle ownership in `MainActivity`; keep browser state in `BrowserController`.
- Keep separate browser intent filters for untyped HTTP(S) links and HTTP(S) links carrying the
  `text/html` MIME type. Adding a MIME type to the untyped filter makes ordinary links ineligible.
- Show WebView custom views above browser chrome and enable sensor rotation for their lifetime.
  Web fullscreen takes orientation priority over the tab overview portrait lock; exiting restores
  the current browser orientation and system-bar policy. Tab overview requests portrait only on
  compact screens; tablets and other `sw600dp` windows preserve their current orientation.
- Route untrusted URLs through existing normalizers. Do not add a second permissive parser.
- Keep the external-app return marker memory-only and scoped to the tab opened by the latest
  `ACTION_VIEW`. Web history consumes Back first; normal root tabs keep the tab-close/overview flow.
- Keep external-link preview sessions, URLs, WebViews, progress, and target-profile selection out of
  tab/session, history, Candy Trail, favicon, WebView-state, and tab-preview persistence. Recreate
  the preview WebView when its target profile changes and reload the final normalized HTTP(S) URL
  when promoting it to a regular tab. Preview loads still use the selected profile's cookies and
  DOM storage, so the feature is disposable UI rather than a private-browsing mode.
- Keep federated-login popup tabs session-ephemeral for their complete window lifetime. App
  backgrounding pauses their live WebView and resumes it on return, while tab/session, History,
  Recall, Candy Trail, WebView-state, and preview persistence exclude them. Process death therefore
  restores the opener instead of an identity-provider page.
- Resolve external intents on every permitted handoff attempt so apps installed while Candy remains
  open are immediately eligible. Show handoff feedback only after Android accepts the external launch.
- Keep all HTTP(S) links in the current WebView without resolving an Android activity. Users can
  hand the loaded page to another app through the explicit **Open in external app** action.
- Carry user intent across script-driven handoffs with a short-lived, tab-bound grant after a
  tapped HTTP(S) navigation. Consume the grant on the first special-scheme launch attempt.
- Treat WebView callbacks as stale-capable: bind work to tab/request/navigation identity before applying results.
- Keep private tab state memory-only and skip remote suggestions for private input.
- Keep private desktop-view domains memory-only; persist regular domains per profile only.
- Keep `CREDENTIAL_MANAGER_SET_ORIGIN` declared while WebViews use browser WebAuthn mode. Android
  Credential Manager requires it before a browser can request passkeys for a website origin.
- Never register userscript handlers on private or Link Peek WebViews. Userscript source is global
  regular-browser configuration, not private session state.
- Apply desktop-view settings before main-frame navigation and reload matching open tabs when the domain preference changes.
- Keep the WebView's measured frame stable while normal pages scroll. A document-start root spacer
  starts normal flow content below the status bar and scrolls away in Chromium's own render path,
  without changing layout params or redispatching insets from the scroll callback. Explicit
  `viewport-fit=cover` pages remove the spacer. The per-site force-safe-area override,
  edge-to-edge-disabled audit mode, and unavailable or rejected document-start styling use a
  static native safe-area margin. Compact absolute or fixed page controls covering the reserved
  top strip receive a local document-owned offset, keeping the WebView edge-to-edge without hiding
  those controls behind system icons. Interaction-triggered fixed side drawers receive the same
  offset plus a bounded height, while empty full-screen backdrops may continue behind system icons.
  If a root-level spacer is neutralized by a site's own scroll container, Candy moves the spacer
  to that normal-flow page wrapper so headers still scroll away.
  Viewport-sized fixed roots and layouts that cannot be moved safely use the native fallback.
  Force-safe-area remains the manual compatibility path for pages using their own scroll container.
  The status-bar overlay still
  redraws a blurred content layer with a surface-tinted fade so system icons stay legible.
- Read page-scroll range, extent and offset through `BrowserWebView`. The optional Compose scroll
  thumb observes scroll changes without replacing the controller's WebView scroll listener and is
  removed from fullscreen/video-only presentation.
- Keep each WebView touch stream owned by `BrowserWebView` from `ACTION_DOWN` through its terminal
  event. Compose parents must not cancel an active page gesture while arbitrating AndroidView input.
- Preserve rapid reverse-flick momentum at the `BrowserWebView` boundary. Keep healthy native
  WebView flings; only replace a fast reverse fling after three consecutive stalled frames while
  a decaying reference fling still expects fast movement.
  Programmatic scroll-bar jumps clear transient momentum state before changing the offset.
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
- `BrowserController.onReceivedSslError` always cancels. Only an error URL matching the current
  main-frame target becomes a page-level error; failed embedded resources stay local to the page.
  Never use `SslErrorHandler.proceed()` to approximate user-CA support; valid user-CA chains are
  accepted before that callback.

## Domain compatibility overrides

| Override | Runtime behavior |
| --- | --- |
| Force vertical scrolling | Removes vertical page scroll locks without changing horizontal overflow |
| Force page zooming | Removes viewport `user-scalable`, minimum-scale and maximum-scale restrictions while preserving other viewport directives |
| Force safe area | Keeps the WebView below the top system-bar/display-cutout inset while scrolling and ignores `viewport-fit=cover` for that host |
| Federated-login compatibility | Allows third-party cookies for the exact site host, removes WebView-only user-agent markers, and permits user-initiated popups only to recognized identity-provider authentication paths |

- Compatibility overrides match the exact current host. Regular tabs persist them per profile;
  private tabs keep them in memory for that tab only.
- Changing an override reloads affected pages. Document-start scripts handle direct navigation and
  commit-visible fallbacks cover redirects whose final host was not known before navigation.
- A detected Google Identity Services SDK first produces a dismissible Snackbar. **Options** opens
  a centered Material 3 dialog; detection alone never changes browser policy. A tab grant is
  memory-only. A profile grant is persisted for the exact host and applies to matching regular tabs.
  Private tabs never expose or persist the profile grant. Privacy X-Ray shows the resulting cookie
  policy and provides a host-scoped action to revoke the grant.
- Federated-login popup exceptions require all three conditions: a user gesture, an active grant on
  the opener site, and a recognized HTTPS provider authentication path. The compatibility identity
  used for the provider user agent is removed when the popup leaves the provider. Its separate
  session-ephemeral identity remains until the popup closes. Other cross-site popups continue through
  the normal popup blocker.

## Web media, fullscreen and picture-in-picture

Agent implementation, security and debugging guide:
[`picture-in-picture.md`](picture-in-picture.md).

| Transition | Behavior |
| --- | --- |
| HTML media appears or starts | A document-start bridge observes bounded HTML5 `video`/`audio` state in supported HTTP(S) frames; the frame-specific reply proxy is the only command path back to that player |
| Web page requests fullscreen | `WebChromeClient.onShowCustomView` creates one transient controller-owned custom-view session; the root Compose overlay hosts Chromium's view |
| Top-level web video requests PiP | A user-activated `requestPictureInPicture()` compatibility bridge validates the exact current regular-tab video, then routes the request through Activity PiP; the page promise and enter/leave events follow confirmed Android mode changes |
| Embedded web video requests PiP | The same trusted tap first asks Chromium to fullscreen the exact iframe video. Candy accepts the PiP request only while that matching non-private custom-view session remains current, so surrounding page content never enters the system PiP surface |
| User selects another regular tab | The current eligible video is pinned and its source WebView moves into the draggable in-app mini-player while non-owning WebViews remain paused |
| App leaves the foreground | The active eligible regular video is pinned before Activity PiP. If Chromium returns its custom view to the page, Candy keeps the same WebView in its existing Android host, raises that host above browser chrome and switches the document to video-only presentation without reparenting the decoder surface. Only the video becomes a full-viewport compositor layer; its ancestor chain is unclipped without creating more full-screen layers. For an embedded player, the trusted document-start bridge also isolates each containing iframe up to the top document. A pre-existing mini-player keeps one stable Android host while its placement changes. While system PiP expects playback, page-driven background pauses are ignored; explicit system pause and stop commands still take effect |
| System media control is used | The app-owned Android `MediaSession` sends play, pause, stop or seek only through the accepted frame reply proxy |
| Audible audio continues in background | A `mediaPlayback` foreground service owns the visible media notification while the Activity-owned WebView and session remain alive |
| PiP expands back into the app | Android expands the shared WebView surface through a centered source rectangle matching the PiP/video aspect ratio instead of targeting the former inline-video rectangle. Presentation CSS and the prior Android host are then restored without pausing; the page-pause guard remains active until the resumed UI has settled |
| PiP closes or the app stops without entering PiP | Presentation CSS is restored, the owning WebView pauses and normal media gesture policy is restored |
| Media ends, page navigates, crashes, closes, snoozes or is destroyed | Navigation generation and WebView identity invalidate the endpoint; view, script, notification and session cleanup is idempotent |

- The bridge accepts telemetry only from the WebView and current navigation it was installed for,
  rejects non-HTTP(S) origins and bounds every identifier, numeric value and payload. Pages cannot
  select another tab. Web PiP requests require a current user activation and are limited to the
  exact eligible video in the selected regular tab. Embedded players additionally require their
  frame's fullscreen Permissions Policy and a matching Chromium custom-view session.
- Media sessions, frame endpoints, metadata, presentation state and mini-player position are
  memory-only and never persisted.
- Repeated lifecycle callbacks for one PiP transition are idempotent: they do not restyle the same
  document presentation or reattach its decoder surface. PiP source rectangles use Activity-local
  coordinates even when window metrics carry a display offset.
- When a site requests a background pause that PiP must suppress, the next play request reconciles
  the site's player state through native media events without exposing a paused transition frame.
- Inline PiP presentation repairs site-driven style changes and DOM reparenting while active. If a
  site replaces its playing video element, the new top-level video inherits the same transient PiP
  owner and playback intent; bounded command retries cannot override an explicit system pause.
- Private media may be detected transiently for local lifecycle correctness, but never becomes an
  in-app mini-player, Android PiP, system media session or notification.
- System PiP renders only the custom video view or video-isolated source WebView. Onboarding,
  splash, update UI and Candy controls stay outside the PiP surface.
- Explicit subframe PiP uses Chromium's transient fullscreen custom view. Automatic background PiP
  isolates the selected video and each containing iframe through a dedicated document-start relay.
  Its credential is separate from native bridge authorization, and each receiver verifies the
  sending frame relationship. Both paths restore the embedded player when Android PiP exits and
  never expose the surrounding parent document in the PiP surface.
- Compatibility is best effort for HTML5 media. DRM restrictions, canvas-only rendering,
  deliberately hostile players and site-specific visibility policies can still prevent control or
  continued playback.

## Google Cast

Implementation, privacy and compatibility guide: [`google-cast.md`](google-cast.md).

Direct HTTP(S) MP4, WebM, HLS and DASH sources from the selected regular tab can be loaded into
Google's Default Media Receiver. The Cast SDK owns device discovery and selection; Candy owns the
post-connection mini-controller. Private tabs never create Cast candidates. Authenticated, DRM,
blob and MSE playback remains best effort or unsupported because the receiver cannot inherit
WebView request state.

## Verification

| Change | Check |
| --- | --- |
| Input/URL policy | Matching JVM rule test |
| WebView settings or callbacks | Focused browser instrumented test |
| Federated login | `FederatedLoginRulesTest`, `FederatedLoginPromptInstrumentedTest`, `BrowserSessionStoreInstrumentedTest`, and popup-blocker regression tests |
| Browser WebAuthn manifest contract | `SystemWebViewCredentialsInstrumentedTest` on API 34+ |
| WebView touch-stream ownership | `BrowserScrollInstrumentedTest#browserWebViewRetainsTouchStreamFromInterceptingParent` plus `#fullBrowserWindowKeepsWebViewTouchStreamsComplete` on API 34+ |
| WebView reverse-flick momentum | `BrowserMomentumRecoveryRulesTest` plus `BrowserScrollInstrumentedTest#busyLongPageKeepsEveryRapidAlternatingFlick` on the affected WebView version |
| Web media, fullscreen and PiP policy | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest` and `FullscreenVideoOverlayInstrumentedTest` on API 34+ |
| Android intent routing | Integration unit test plus launch instrumented test when lifecycle matters |
| TLS trust channels | `./gradlew testFullDebugUnitTest testFullUserCaDebugUnitTest assembleFullDebug assembleFullUserCaDebug`, then `python3 scripts/test_network_security_apks.py` |
