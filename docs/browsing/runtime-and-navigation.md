# Runtime and navigation

## Ownership

| Layer | Responsibility | Entry points |
| --- | --- | --- |
| Activity | Android lifecycle, incoming intents, permission/file chooser launchers and root composition | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt) |
| Activity support | System PiP state, launcher-shortcut dispatch, userscript import, update prompt and appearance night mode | [`MainActivityPictureInPictureController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivityPictureInPictureController.kt), [`LauncherShortcutIntentHandler.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/LauncherShortcutIntentHandler.kt), [`UserScriptImporter.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/UserScriptImporter.kt), [`AppUpdatePrompt.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/AppUpdatePrompt.kt), [`AppearanceNightMode.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/AppearanceNightMode.kt) |
| Controller | Gecko session creation, tab/profile state, navigation, persistence coordination, platform and fullscreen-video callbacks | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Platform engine adapters | Own GeckoView sessions/extensions on Android and WKWebView/Toppings on iOS | [`platform-engines.md`](platform-engines.md) |
| Compose root | Read controller state, own transient screen state and route browser surfaces | [`BrowserScreen.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserScreen.kt) |
| Compose surfaces | Host WebView/preview content, address chrome, settings, modal surfaces and tab overview without owning browser state | [`BrowserViewport.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserViewport.kt), [`BrowserAddressChrome.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserAddressChrome.kt), [`BrowserSettingsOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserSettingsOverlay.kt), [`BrowserModalSurfaces.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserModalSurfaces.kt), [`BrowserTransientOverlays.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserTransientOverlays.kt), [`TabOverview.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/TabOverview.kt), [`FullscreenVideoOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FullscreenVideoOverlay.kt) |
| Policies | Resolve input, URLs, settings, media, file chooser and external routes | [`browser/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/) |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized web URLs through shared URI policy. The optional external-link preview keeps a transient Gecko session outside the tab/session store until **Open in Candy** creates a regular tab in the chosen profile; when disabled, the existing immediate-tab path remains unchanged. Root Back returns to the calling app. |
| Explicit special-scheme address | `BrowserUriPolicy` → `ExternalAppLauncher` | Treat typed, pasted or scanned safe schemes as user-authorized app handoffs; keep internal schemes blocked |
| App link or special scheme | `ExternalNavigationPolicy` → `BrowserUriPolicy` → `ExternalAppLauncher` | Offer tapped HTTP(S) app links and their bounded redirect chain, including external-preview navigation, only to a direct non-browser default handler; keep unavailable or ambiguous links in WebView; allow safe main-frame special-scheme handoffs; block unsafe/internal schemes and subframes |
| APK link or redirect | `ApkDownloadNavigationRules` → browser download pipeline | Route a tapped main-frame APK link and its authorized redirect chain directly to the selected download manager instead of rendering a blank WebView page |
| Link Peek | `LinkPeekPreviewNavigationPolicy` → transient Gecko session | Keep only HTTP(S); do not hand off preview navigation |
| Site Capsule | `CapsuleIntentRules` → capsule runtime | Apply capsule-specific navigation boundary before normal routing |
| Desktop view | `DesktopSiteRules` / `DesktopNavigationRules` → controller → engine session | Store registrable domains per profile; coordinate Gecko's desktop user-agent and viewport mode with the target navigation |
| Always block pop-ups | `PopupSiteRules` → controller → Gecko navigation delegate | Reject popups for configured registrable opener domains; preserve transient popup/popunder quarantine and pending-window policy; persist regular settings per profile and keep private settings memory-only |
| Federated login | `FederatedLoginRules` → controller → Snackbar and `AlertDialog` | Detect only known cross-site identity SDK endpoints; change cookie, user-agent and popup policy only after explicit consent |
| CAPTCHA compatibility | `CaptchaCompatibilityRules` → controller → Snackbar and `AlertDialog` | Detect strict cross-site Cloudflare, Google reCAPTCHA, or hCaptcha endpoints; allow third-party cookies only after explicit consent |
| HTTP Basic authentication | `HttpAuthPromptRules` → `BrowserController` → `HttpAuthPromptDialog` | Prompt only for a selected, resumed tab when challenge host matches current top-level HTTP(S) host; keep credentials memory-only and warn on cleartext HTTP |
| Gecko downloads and uploads | `BrowserEngineDownloadRules` / `FileChooserRules` → controller → Android download/file presenters | Accept bounded HTTP(S) downloads and readable `content://` file results only; reject stale session/navigation/activity results |
| Gecko permissions and prompts | `PermissionRequestRules` / `BrowserWebPromptRules` → controller → existing Candy dialogs and Android permission presenter | Preserve profile/private permission scope, deny stale prompts, and fail closed for unsupported sensitive prompt classes |
| Local userscript | `UserScriptRules` → Gecko Topping document-start bridge | Require an explicit HTTP(S) pattern, top frame and regular tab; apply full URL exclusions before source runs |

## Invariants

- Keep activity-result registration and lifecycle ownership in `MainActivity`; focused activity helpers
  receive explicit callbacks and must not become independent lifecycle owners. Keep browser state in
  `BrowserController`, transient root UI state in `BrowserScreen`, and focused composables stateless
  except for their existing local presentation state.
- Keep separate browser intent filters for untyped HTTP(S) links and HTTP(S) links carrying the
  `text/html` MIME type. Adding a MIME type to the untyped filter makes ordinary links ineligible.
- Show WebView custom views above browser chrome and enable sensor rotation for their lifetime.
  Web fullscreen takes orientation priority over the tab overview portrait lock; exiting restores
  the current browser orientation and system-bar policy. Tab overview requests portrait only on
  compact screens; tablets and other `sw600dp` windows preserve their current orientation.
- Route untrusted URLs through existing normalizers. Do not add a second permissive parser.
- Keep the external-app return marker memory-only and scoped to the tab opened by the latest
  `ACTION_VIEW`. Web history consumes Back first; normal root tabs keep the tab-close/overview flow.
- Keep external-link preview sessions, URLs, engine views, progress, and target-profile selection out of
  tab/session, history, Candy Trail, favicon, WebView-state, and tab-preview persistence. Recreate
  the transient engine session when its target profile changes and reload the final normalized HTTP(S) URL
  when promoting it to a regular tab. Show the profile chooser only when multiple profiles exist.
  Preview loads still use the selected profile's cookies and
  DOM storage, so the feature is disposable UI rather than a private-browsing mode.
- On cold external `ACTION_VIEW` launches, keep native chrome interactive while Gecko and
  registrable-domain initialization complete. Defer unrelated Cast, media-session, and release-note
  work from this launch path. There is no Android WebView startup or renderer fallback.
- Keep federated-login popup tabs session-ephemeral for their complete window lifetime. App
  backgrounding pauses their live WebView and resumes it on return, while tab/session, History,
  Recall, Candy Trail, WebView-state, and preview persistence exclude them. Process death therefore
  restores the opener instead of an identity-provider page.
- Resolve external intents on every permitted handoff attempt so apps installed while Candy remains
  open are immediately eligible. Show handoff feedback only after Android accepts the external launch.
- Offer user-tapped HTTP(S) links to Android only when a direct non-browser default handler can
  receive them. Requiring both a default and a non-browser handler prevents browser/chooser loops;
  unavailable or ambiguous app links continue in the current WebView.
- Carry user intent across script-driven handoffs with a short-lived, tab- and WebView-bound grant
  after a tapped HTTP(S) navigation. The grant permits an HTTP redirect or special-scheme handoff,
  ends on page completion or error, and is consumed by the first accepted external launch attempt.
  A passive special-scheme redirect without this grant stays blocked.
- Treat a newly delivered external `ACTION_VIEW` as the same bounded user intent for its initial
  redirect chain, with or without external preview. Preserve only its original expiry across Activity
  recreation; replacing, reloading, stopping, or navigating away from its WebView revokes it.
- Route only user-tapped main-frame APK links and their authorized redirects into downloads.
  Passive navigation, subframes, malformed URLs, and embedded credentials remain blocked from this
  shortcut. External previews retain one bounded, memory-only download grant for the exact active
  main-frame URL and its observed redirect chain until its first download, completion, error, or
  expiry, so a slow authorized redirect cannot lose user intent or authorize unrelated content.
  Server-declared APK downloads continue through Gecko's external-response listener, and requests with a
  sanitized `.apk` filename always use the Android package MIME type.
- Treat Gecko delegate callbacks as stale-capable: bind downloads, file selection, runtime/content/media
  permissions, authentication and web prompts to the exact session plus navigation generation.
  Navigation, tab replacement, backgrounding and destruction cancel pending delivery exactly once.
- Keep private tab state memory-only and skip remote suggestions for private input.
- Keep private desktop-view domains memory-only; persist regular domains per profile only.
- Desktop view must present one coherent desktop identity: desktop user-agent text, Linux desktop
  client hints, and a 980-CSS-pixel layout viewport. Rewrite mobile viewport sizing only for
  configured registrable domains, preserve unrelated directives such as `viewport-fit`, and restore
  page defaults through the required reload when desktop view is disabled.
- Keep private always-block-popup domains memory-only; persist regular domains per profile only.
- Keep `CREDENTIAL_MANAGER_SET_ORIGIN` declared for GeckoView's origin-bound WebAuthn and Candy's
  password Credential Manager bridge. GeckoView 140 uses Android's framework Credential Manager for
  passkeys on API 34+ when `android.software.credentials` exists. Regular HTTPS Gecko views expose
  native virtual Autofill nodes; private views do not. Login save/select and FedCM callbacks carry a
  tab, profile, session, origin and navigation identity and deny stale, private or cross-origin work.
  Candy stores no credential database and logs no credential values. Ship AndroidX Credential Manager
  in both distributions and its Google Password Manager fallback only in Full. Providers must
  separately trust Candy's package and release signing certificate in their privileged-browser
  allowlist. Provider storage is origin-scoped by Android, not partitioned by Candy profile.
- Handle HTTP authentication through Gecko's prompt delegate on the main thread. Keep entered
  credentials out of app storage and logs. Cancel a pending challenge when its tab, session,
  navigation, selection, or activity lifetime becomes stale. Validate the challenge against the
  current top-level origin; cleartext HTTP prompts warn that credentials can be exposed.
- Never register Topping handlers on private or Link Peek engine sessions. Topping source is global
  regular-browser configuration, not private session state.
- Apply desktop identity to the target Gecko session before controller-owned navigation or history
  traversal. Never replay a committed navigation or convert POST to GET. Reload matching open tabs
  only when the user explicitly changes the domain preference.
- Keep the engine view's measured frame stable while pages scroll. `GeckoViewInsetRules` keeps the
  normal Gecko renderer edge to edge, while Gecko's native cutout integration owns CSS safe-area
  values and `setVerticalClipping` keeps fixed bottom content above system navigation. Compose
  chrome consumes the same system-bar insets. The explicit per-site **Force safe area** override
  converts all safe edges to native margins; refreshing that override redispatches current insets.
  Gecko views use the TextureView backend so Candy's shared backdrop blur and tab motion can sample
  and transform rendered pixels. The status-bar overlay keeps system icons legible.
- Read page-scroll metrics through the engine port. The optional `BrowserScrollBar` observes them
  without replacing the pill-collapse scroll listener and is absent in fullscreen/video-only mode.
- Keep page touch streams and native fling physics in GeckoView. Compose parents must not cancel
  an active page gesture while arbitrating AndroidView input. No Chromium-specific reverse-fling
  workaround runs in the Gecko renderer.
- Add pure policy beside the owning package; leave `BrowserController` as integration wiring.

## TLS trust channels

| Build | Trust anchors | Release asset |
| --- | --- | --- |
| Standard | Gecko built-in roots for page/engine requests; Android system roots for Android networking | `CandyBrowser-v<version>-release.apk` |
| User CA | Standard roots plus user-installed Android CA roots | `CandyBrowser-v<version>-user-ca-release.apk` |

- The build channel controls trust for both networking stacks: Android Network Security Config
  controls Android requests; `GeckoRuntimeSettingsFactory` passes `BuildConfig.TRUST_USER_CERTIFICATES`
  to Gecko's `enterpriseRootsEnabled`. Gecko owns a separate root store, so the XML configuration
  alone is insufficient. Broader trust requires installing the explicitly labeled User CA APK.
- Both channels use the same application ID and signing key. Update selection preserves the installed
  channel and rejects a release that contains only the other channel's asset.
- User CA trust applies to all app HTTPS connections, not only rendered pages or a selected profile.
  The settings warning must remain visible in User CA builds.
- Gecko validates certificate chains; Candy does not bypass certificate errors. Only errors bound
  to the current main-frame session/navigation may become page-level errors. User-CA support imports
  roots through Gecko's native setting, not a certificate-error exception.
- API contract: [Gecko trust architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html)
  and [enterpriseRootsEnabled](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoRuntimeSettings.Builder.html#enterpriseRootsEnabled(boolean)).

## Domain compatibility overrides

| Override | Runtime behavior |
| --- | --- |
| Force vertical scrolling | Removes vertical page scroll locks without changing horizontal overflow |
| Force page zooming | Removes viewport `user-scalable`, minimum-scale and maximum-scale restrictions while preserving other viewport directives |
| Force safe area | Keeps the WebView below the top system-bar/display-cutout inset while scrolling and ignores `viewport-fit=cover` for that host |
| Federated-login compatibility | Allows third-party cookies for the exact site host, removes WebView-only user-agent markers, and permits user-initiated popups only to recognized identity-provider authentication paths |
| CAPTCHA compatibility | Allows third-party cookies for the exact site host without changing the user agent or popup policy |

- Compatibility overrides match the exact current host. Regular tabs persist them per profile;
  private tabs keep them in memory for that tab only.
- GeckoView 140 exposes `ACCEPT_FIRST_PARTY` only as a runtime-wide hard policy and provides no
  public site-scoped override for it. Candy therefore keeps that strict mode by default, then uses
  `ACCEPT_ALL` only while a selected session has a confirmed, exact-current-host SSO, CAPTCHA, or
  paused-site exception. Normal and private modes are coordinated separately. The coordinator
  restores strict mode before cross-host main-frame navigation and as soon as that session becomes
  inactive, loses the exception, or closes. Gecko's runtime is shared, so inactive same-mode sibling
  sessions technically share the temporary setting. Candy marks them inactive through Gecko's
  session lifecycle, but GeckoView does not guarantee that inactivity stops every background network
  actor.
- Changing an override reloads affected pages. Document-start scripts handle direct navigation and
  commit-visible fallbacks cover redirects whose final host was not known before navigation.
- A detected Google Identity Services SDK first produces a dismissible Snackbar. **Options** opens
  a centered Material 3 dialog; detection alone never changes browser policy. A tab grant is
  memory-only. A profile grant is persisted for the exact host and applies to matching regular tabs.
  Private tabs never expose or persist the profile grant. Privacy X-Ray shows the resulting cookie
  policy and provides a host-scoped action to revoke the grant.
- A detected cross-site Cloudflare, Google reCAPTCHA/Enterprise, or hCaptcha client first produces
  the same Snackbar-to-dialog consent flow. Detection requires HTTPS plus a recognized provider host
  and path; lookalike, first-party, malformed, and generic vendor requests do not prompt. CAPTCHA
  grants use the same exact-host tab/profile/private boundaries, but affect only third-party-cookie
  policy. They never enable federated-login user-agent or popup compatibility.
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
| CAPTCHA compatibility | `CaptchaCompatibilityRulesTest`, `CaptchaCompatibilityPromptInstrumentedTest`, `BrowserControllerCaptchaCompatibilityInstrumentedTest`, and `BrowserSessionStoreInstrumentedTest` |
| Gecko password Autofill, Credential Manager and browser-origin manifest contract | `GeckoCredentialsInstrumentedTest` on API 34+ |
| WebView touch-stream ownership | `BrowserScrollInstrumentedTest#browserWebViewRetainsTouchStreamFromInterceptingParent` plus `#fullBrowserWindowKeepsWebViewTouchStreamsComplete` on API 34+ |
| WebView reverse-flick momentum | `BrowserMomentumRecoveryRulesTest` plus `BrowserScrollInstrumentedTest#busyLongPageKeepsEveryRapidAlternatingFlick` on the affected WebView version |
| Web media, fullscreen and PiP policy | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest` and `FullscreenVideoOverlayInstrumentedTest` on API 34+ |
| Android intent routing | Integration unit test plus launch instrumented test when lifecycle matters |
| TLS trust channels | `./gradlew testFullDebugUnitTest testFullUserCaDebugUnitTest assembleFullDebug assembleFullUserCaDebug`, then `python3 scripts/test_network_security_apks.py` |
