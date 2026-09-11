# Runtime and navigation

## Ownership

| Layer | Responsibility | Entry points |
| --- | --- | --- |
| Activity | Android lifecycle, incoming intents, permission/file chooser launchers and root composition | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt) |
| Activity support | System PiP state, launcher-shortcut dispatch, userscript import, update prompt and appearance night mode | [`MainActivityPictureInPictureController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivityPictureInPictureController.kt), [`LauncherShortcutIntentHandler.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/LauncherShortcutIntentHandler.kt), [`UserScriptImporter.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/UserScriptImporter.kt), [`AppUpdatePrompt.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/AppUpdatePrompt.kt), [`AppearanceNightMode.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/AppearanceNightMode.kt) |
| Controller | Gecko session creation, tab/profile state, navigation, persistence coordination, platform and fullscreen-video callbacks | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Platform engine adapters | Own GeckoView sessions/extensions on Android and WKWebView/Toppings on iOS | [`platform-engines.md`](platform-engines.md) |
| Compose root | Read controller state, own transient screen state and route browser surfaces | [`BrowserScreen.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserScreen.kt) |
| Compose surfaces | Host engine/preview content, native page-error/offline presentation, address chrome, settings, modal surfaces and tab overview without owning browser state | [`BrowserViewport.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserViewport.kt), [`PageErrorFeedback.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/PageErrorFeedback.kt), [`BrowserAddressChrome.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserAddressChrome.kt), [`BrowserSettingsOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserSettingsOverlay.kt), [`BrowserModalSurfaces.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserModalSurfaces.kt), [`BrowserTransientOverlays.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserTransientOverlays.kt), [`TabOverview.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/TabOverview.kt), [`FullscreenVideoOverlay.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FullscreenVideoOverlay.kt) |
| Policies | Resolve input, URLs, settings, media, file chooser and external routes | [`browser/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/) |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| External keyboard or mouse | `MainActivity` → `BrowserHardwareInputRules` → controller | Consume documented browser chords and auxiliary Back/Forward buttons; hand unmatched hardware keys to the selected engine unless browser chrome owns the IME; keep pointer wheels on normal Android dispatch and normalize only non-pointer vertical wheel reports before using the engine's relative-scroll fallback |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized HTTP(S) URLs from `ACTION_VIEW` data or the complete `EXTRA_TEXT` value of `ACTION_SEND` `text/plain` and `text/html` shares. An incoming `ACTION_VIEW` app link first gets one direct non-browser-default handoff attempt; shared URLs stay in Candy. The optional external-link preview keeps a transient Gecko session outside the tab/session store until **Open in Candy** creates a regular tab in the chosen profile; when disabled, the existing immediate-tab path remains unchanged. Root Back returns to the calling app. |
| Explicit special-scheme address | `BrowserUriPolicy` → `ExternalAppLauncher` | Treat typed, pasted or scanned safe schemes as user-authorized app handoffs; keep internal schemes blocked |
| App link or special scheme | `ExternalNavigationPolicy` → `BrowserUriPolicy` → `ExternalAppLauncher` | Keep a tapped same-site HTTP(S) redirector in the engine so its server redirect can resolve; offer a cross-site target and the remaining bounded redirect chain, including external-preview navigation, only to a direct non-browser default handler; keep unavailable or ambiguous links in the engine; allow safe main-frame special-scheme handoffs; block unsafe/internal schemes and subframes |
| APK link or redirect | `ApkDownloadNavigationRules` → browser download pipeline | Route a tapped main-frame APK link and its authorized redirect chain directly to the selected download manager instead of rendering a blank engine page |
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
| Main-frame 404 | engine HTTP status → tab state → `PageErrorFeedbackRules` | Keep the navigation committed, preserve URL/title/history side effects, and cover the page with Candy's native not-found surface |
| Offline page | failed main-frame navigation + `BrowserConnectivityMonitor` → controller → `PageErrorFeedbackRules` | Require Android's validated default internet capability, never cover an already loaded page merely because connectivity drops, auto-reload on reconnect only before the game starts, and preserve the game behind an explicit reload banner afterward |

## Invariants

- Keep activity-result registration and lifecycle ownership in `MainActivity`; focused activity helpers
  receive explicit callbacks and must not become independent lifecycle owners. Keep browser state in
  `BrowserController`, transient root UI state in `BrowserScreen`, and focused composables stateless
  except for their existing local presentation state.
- Keep separate browser intent filters for untyped HTTP(S) links and HTTP(S) links carrying the
  `text/html` MIME type. Adding a MIME type to the untyped filter makes ordinary links ineligible.
- Register shares only for `ACTION_SEND` `text/plain` and `text/html`. Treat `EXTRA_TEXT` as the
  canonical literal payload for both types, require the complete value to normalize as one HTTP(S)
  URL within 32,768 characters, and never select a URL from prose, `EXTRA_HTML_TEXT`, or
  `ACTION_SEND_MULTIPLE`.
- Show Gecko fullscreen content above browser chrome and enable sensor rotation for its lifetime.
  Web-content fullscreen takes orientation priority over the tab overview portrait lock; exiting restores
  the current browser orientation, system-bar policy and soft-input adjustment. While system bars
  are hidden, keep the Activity at full height and let Compose IME insets move browser chrome above
  the keyboard; this avoids OEM `adjustResize` implementations leaving a black keyboard-sized area
  after the IME closes. Tab overview requests portrait only on compact screens; tablets and other
  `sw600dp` windows preserve their current orientation.
- Route untrusted URLs through existing normalizers. Do not add a second permissive parser.
- Keep the external-app return marker memory-only and scoped to the tab opened by the latest accepted
  `ACTION_VIEW` or `ACTION_SEND`. Engine history consumes Back first. A root tab with an active opener
  closes and returns to that opener; a deletable root tab with another active-profile sibling closes
  into the tab overview. When the root tab is the active profile's last tab, or the selected root tab is
  pinned, do not mutate tabs and let Android handle Back-to-Home. Tabs in other profiles do not become
  implicit Back targets.
- Keep external-link preview sessions, URLs, engine views, progress, and target-profile selection out of
  tab/session, history, Candy Trail, favicon, Gecko-session-state, and tab-preview persistence. Recreate
  the transient engine session when its target profile changes and reload the final normalized HTTP(S) URL
  when promoting it to a regular tab. Show the profile chooser only when multiple profiles exist.
  Preview loads still use the selected profile's cookies and
  DOM storage, so the feature is disposable UI rather than a private-browsing mode.
- On every cold accepted `ACTION_VIEW` or `ACTION_SEND` launch, keep native chrome interactive while
  Gecko and registrable-domain initialization complete, whether external preview is enabled or not.
  Defer unrelated Cast, media-session, and release-note work from this launch path. If Gecko preparation
  fails, show terminal feedback and return to the caller instead of leaving an endless loader. There is
  no Android WebView startup or renderer fallback.
- Keep federated-login popup tabs session-ephemeral for their complete window lifetime. App
  backgrounding pauses their live Gecko session and resumes it on return, while tab/session, History,
  Recall, Candy Trail, Gecko-session-state, and preview persistence exclude them. Process death therefore
  restores the opener instead of an identity-provider page.
- Resolve external intents on every permitted handoff attempt so apps installed while Candy remains
  open are immediately eligible. Show handoff feedback only after Android accepts the external launch.
- Offer an incoming `ACTION_VIEW` URL directly to its verified non-browser default before starting
  Gecko. If Android rejects that handoff, preserve the same URL and bounded initial-navigation grant
  through the existing preview or regular-tab web fallback. Shared `ACTION_SEND` URLs stay in Candy.
- Offer user-tapped HTTP(S) links to Android only when a direct non-browser default handler can
  receive them. Requiring both a default and a non-browser handler prevents browser/chooser loops;
  unavailable or ambiguous app links continue in the current engine session. A same-registrable-site
  redirector such as a search result's intermediate URL also stays in that session; its bounded
  user-navigation grant remains available to the cross-site server redirect that follows.
- Carry user intent across script-driven handoffs with a short-lived, tab- and engine-session-bound grant
  after a tapped HTTP(S) navigation. The grant permits an HTTP redirect or special-scheme handoff,
  ends on page completion or error, and is consumed by the first accepted external launch attempt.
  A passive special-scheme redirect without this grant stays blocked.
- Treat a newly delivered accepted `ACTION_VIEW` or `ACTION_SEND` as the same bounded user
  intent for its initial redirect chain, with or without external preview. Preserve only its original
  expiry across Activity recreation; replacing, reloading, stopping, or navigating away from its
  engine session revokes it.
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
- Treat Android connectivity as a process-local observable effect. A default network counts as online
  only with both `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED`; close the registered callback
  with `BrowserController`. Do not issue Candy-owned probe requests or replace an already usable page
  solely because the network disconnects. Show the offline surface after a main-frame transport failure.
  Offline Candy Circuit board, score, combo, moves and best score remain UI-local and memory-only.
  The deterministic 4×4 rotation puzzle gives a round twelve moves; a circuit scores only when at least
  four tiles form a cycle through reciprocal edge connections. Extra open or dangling branches do not
  invalidate that cycle. Scoring replaces every participating tile with a different randomized tile,
  rejects refills that already contain a closed cycle, and grants one capped nonlinear move reward per
  scoring turn: two moves for 4–7 tiles, three for 8–11, five for 12–15, and nine for all 16;
  consecutive scoring turns add up to three combo moves, with ten moves as the per-turn cap. A
  player may rebuild and score the same circuit positions again after refill. The UI resolves a scored
  turn as one input-locked sequence: rotate the closing
  tile, pulse and dissolve the closed circuit, then fly the randomized refill tiles in with a stable
  stagger; score and move semantics update from the reducer result without waiting for motion. Open the
  puzzle immediately with no intermediate play prompt. If connectivity
  returns, keep game state and morph the offline pill into a polite **Back online** banner. Its button
  plays the page exit motion before performing the only reload.
- Treat a main-frame HTTP 404 as a committed response, not a failed navigation. System WebView reports it
  from `onReceivedHttpError`; Gecko's authenticated internal Privacy WebExtension reports the main-frame
  response status because GeckoView's session delegate exposes transport errors but not HTTP response
  codes. Bind response messages to the request's policy revision and navigation generation. Reject
  non-HTTP(S), out-of-range, stale-policy, and URL-mismatched response messages. Subresource
  failures never replace page content. Clear status on every new navigation.
- Keep private desktop-view domains memory-only; persist regular domains per profile only.
- Desktop view must present one coherent desktop identity: desktop user-agent text, Linux desktop
  client hints, and a 980-CSS-pixel layout viewport. Rewrite mobile viewport sizing only for
  configured registrable domains, preserve unrelated directives such as `viewport-fit`, and restore
  page defaults through the required reload when desktop view is disabled.
- Keep private always-block-popup domains memory-only; persist regular domains per profile only.
- Keep `CREDENTIAL_MANAGER_QUERY_CANDIDATE_CREDENTIALS` and `CREDENTIAL_MANAGER_SET_ORIGIN`
  declared for GeckoView's passkey lookup, origin-bound WebAuthn, and Candy's password Credential
  Manager bridge. GeckoView 155 uses Android's framework Credential Manager for passkeys on API 34+
  when `android.software.credentials` exists. Regular HTTPS Gecko views expose
  native virtual Autofill nodes; private views do not. Developer options provide a default-off
  **Password manager on HTTP sites** override only when GeckoView is selected. Enabling it requires
  an explicit cleartext-HTTP warning confirmation. Once enabled, an
  explicit tap on an HTTP login field may open the default Android password manager for login
  selection. It never enables automatic HTTP filling, HTTP login saving, FedCM, passkeys, private
  tabs, Link Peek or external-link previews. Android System WebView exposes no public API for this
  insecure-origin exception, so the same setting is visible but disabled and explains that HTTPS or
  GeckoView is required. Credential providers may still reject Candy or an HTTP origin independently.
  Login save/select and FedCM callbacks carry a tab, profile, session, origin and navigation identity
  and deny stale, private or cross-origin work.
  `MainActivity` binds Gecko's process-owned `GeckoRuntime.ActivityDelegate` to a lifecycle-scoped
  Activity Result launcher so WebAuthn can open its passkey provider and return the result. A successful
  provider result may carry no `Intent` payload; Candy buffers it until the same Activity has resumed
  and the initiating tab, engine session and navigation generation are still current. While the provider
  owns the foreground, immediate background-retention policy protects that initiating tab. Destroying
  the Activity removes only its own delegate and rejects an unfinished request. Private tabs receive
  the same lifecycle protection without persisting credential or tab state. GeckoView 155's
  related-origin WebAuthn prompt remains on its default-deny path until Candy has a separately
  validated user-consent contract for cross-origin credential relationships.
  GeckoView 155 includes Mozilla's duplicate Credential Manager callback guard from bug 2008413;
  Candy still keeps each result bound to the exact Activity delegate and request generation.
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
- Keep the engine view's measured frame stable at the full window while pages scroll.
  `GeckoViewInsetRules` forwards side and navigation safe areas to the renderer without adding
  native margins. Candy owns the status-bar and cutout top edge for every normal page because a
  `viewport-fit=cover` declaration does not guarantee use of `env(safe-area-inset-top)`.
  The document-start compatibility inset protects normal flow and top-positioned content.
  Top-anchored fixed, sticky, absolute, and focused containers are shifted once into the safe area.
  A passive animation-frame-bounded scroll check handles newly stuck headers without recomputing
  established offsets. Owned offsets survive temporary hide/show, but
  are cleared when a visible element returns to normal flow. Persistent layout conflicts suspend
  timer retries until a later DOM change or user interaction resumes recovery before switching only
  the top edge into a navigation-scoped native fallback margin. The explicit
  per-site **Force safe area** override still moves every edge into native safe-area margins.
  Fullscreen keeps the renderer edge to edge.
  GeckoView keeps its default SurfaceView backend so frames reach Android's compositor directly.
  PiP, clipping and tab motion preserve the same browser host, GeckoView, surface, display and
  session; browser blur is a sibling chrome effect and does not require a TextureView copy. The
  static status-bar overlay remains outside the renderer and keeps system icons legible.
- Read page-scroll metrics through the engine port. The optional `BrowserScrollBar` observes them
  at up to 60 Hz without replacing the 15 Hz pill-collapse scroll path and is absent in
  fullscreen/video-only mode. Gecko's device-pixel-scaled document metrics update only the scrollbar;
  they never enter the renderer-coordinate pill-collapse direction reducer.
- Keep page touch streams and native fling physics in GeckoView. Compose parents must not cancel
  an active page gesture while arbitrating AndroidView input. No Chromium-specific reverse-fling
  workaround runs in the Gecko renderer. Android window-focus loss, engine deactivation and view
  detachment are terminal boundaries: if the platform omitted a final touch event, Candy sends one
  synthetic `ACTION_CANCEL` to Gecko before rejecting background content-menu callbacks.
- Add pure policy beside the owning package; leave `BrowserController` as integration wiring.

## TLS trust channels

| Build | Application ID | Trust anchors | Release asset |
| --- | --- | --- | --- |
| Standard | `dev.sk2andy.materialbrowser` | Gecko built-in roots for page/engine requests; Android system roots for Android networking | `CandyBrowser-v<version>-release.apk` |
| User CA | `dev.sk2andy.materialbrowser.ca` | Standard roots plus user-installed Android CA roots | `CandyBrowser-v<version>-ca-release.apk` |

- The build channel controls trust for both networking stacks: Android Network Security Config
  controls Android requests; `GeckoRuntimeSettingsFactory` passes `BuildConfig.TRUST_USER_CERTIFICATES`
  to Gecko's `enterpriseRootsEnabled`. Gecko owns a separate root store, so the XML configuration
  alone is insufficient. Broader trust requires installing the explicitly labeled User CA APK.
- Separate application IDs isolate app data and allow both channels to stay installed. Update
  selection preserves the installed channel and rejects a release that contains only the other
  channel's asset.
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
| Force safe area | Keeps the Gecko renderer inside native safe-area margins and ignores `viewport-fit=cover` for that host |
| Federated-login compatibility | Allows third-party cookies for the exact site host, removes embedded-browser user-agent markers, and permits user-initiated popups only to recognized identity-provider authentication paths |
| CAPTCHA compatibility | Allows third-party cookies for the exact site host without changing the user agent or popup policy |

- Compatibility overrides match the exact current host. Regular tabs persist them per profile;
  private tabs keep them in memory for that tab only.
- GeckoView 155 exposes `ACCEPT_FIRST_PARTY` only as a runtime-wide hard policy and provides no
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
| HTML media appears or starts | Gecko's native `MediaSession.Delegate` publishes playback, position and bounded element metadata for the exact Gecko session |
| Web page enters or exits fullscreen | `ContentDelegate.onFullScreen` owns the DOM-fullscreen lifecycle; media fullscreen metadata independently identifies the video and its dimensions |
| User selects another regular tab | The current eligible video may move into the draggable in-app mini-player; this is the only presentation path that reparents GeckoView |
| App leaves the foreground | The active eligible regular video is pinned in its original browser viewport before Activity PiP. The GeckoView, SurfaceView backend, GeckoDisplay and GeckoSession are not replaced or reparented |
| Android confirms PiP mode | The exact owning session receives one `CompositorController.onPipModeChanged` notification; preparation never pre-arms Gecko with an unconfirmed state |
| System media control is used | The app-owned Android `MediaSession` sends play, pause, stop or seek through Gecko's active native media session |
| Audible audio continues in background | A `mediaPlayback` foreground service owns the visible media notification while the Activity-owned Gecko session remains alive |
| PiP expands back into the app | Android expands the unchanged browser-hosted Gecko surface through a centered source rectangle matching Gecko's reported video aspect ratio; normal chrome returns after the expanded layout is ready |
| Fullscreen closes | Candy requests `GeckoSession.exitFullScreen()` and restores normal chrome without stopping unrelated media |
| Media ends, page navigates, crashes, closes, snoozes or is destroyed | Gecko session identity invalidates the endpoint; view, notification and session cleanup is idempotent |

- Gecko fullscreen, media fullscreen and playback callbacks are independent and may arrive in any
  order. Candy merges only callbacks from the current native media-session identity; stale ad/player
  sessions cannot overwrite the active YouTube state.
- Media metadata, presentation state and mini-player position are memory-only and never persisted.
- Repeated lifecycle callbacks for one PiP transition are idempotent. They do not switch the GeckoView
  backend, release its display, reparent its view or resend the same Gecko PiP state.
- PiP source bounds and Android aspect ratio use Gecko's video dimensions, fall back to 16:9 for
  invalid metadata and clamp extreme media ratios to Android's supported range.
- Private media may be detected transiently for local lifecycle correctness, but never becomes an
  in-app mini-player, Android PiP, system media session or notification.
- System PiP renders the video-only Gecko browser viewport. Onboarding,
  splash, update UI and Candy controls stay outside the PiP surface.
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
| Native 404/offline pages and Candy Circuit | `CandyCircuitRulesTest`, `PageErrorFeedbackRulesTest`, `BrowserConnectivityRulesTest`, `GeckoMainFrameResponseRulesTest`, `PageErrorFeedbackInstrumentedTest`, and engine-specific main-frame 404 coverage |
| Federated login | `FederatedLoginRulesTest`, `FederatedLoginPromptInstrumentedTest`, `BrowserSessionStoreInstrumentedTest`, and popup-blocker regression tests |
| CAPTCHA compatibility | `CaptchaCompatibilityRulesTest`, `CaptchaCompatibilityPromptInstrumentedTest`, `BrowserControllerCaptchaCompatibilityInstrumentedTest`, and `BrowserSessionStoreInstrumentedTest` |
| Gecko password Autofill, opt-in HTTP login selection, Credential Manager and browser-origin manifest contract | `CredentialPromptRulesTest`, `DeveloperOptionsSettingsPageInstrumentedTest` and `GeckoCredentialsInstrumentedTest` on API 34+ |
| WebView touch-stream ownership | `BrowserScrollInstrumentedTest#browserWebViewRetainsTouchStreamFromInterceptingParent` plus `#fullBrowserWindowKeepsWebViewTouchStreamsComplete` on API 34+ |
| WebView reverse-flick momentum | `BrowserMomentumRecoveryRulesTest` plus `BrowserScrollInstrumentedTest#busyLongPageKeepsEveryRapidAlternatingFlick` on the affected WebView version |
| Draggable page scrollbar | `BrowserScrollBarRulesTest`, `CandyPrivacyHostContractTest`, `BrowserScrollBarInstrumentedTest`, and `GeckoBottomBarScrollInstrumentedTest#realGeckoScrollbarPortReadsAndMovesLongDocument` on API 34+ |
| Edge-to-edge window, safe web viewport, focused search, and representative site layouts | `SystemWebViewEdgeToEdgeInstrumentedTest` and `GeckoEdgeToEdgeInstrumentedTest` run deterministic layout profiles derived from YouTube, Google, ESPN, NYTimes, CNN, Reddit, Facebook, IKEA, GitHub, Discord, Instagram, TapTap, Vimeo, Wikipedia, Stack Overflow, and DuckDuckGo on API 34+; the TapTap profile asserts safety immediately in the scroll task so delayed post-scroll repair cannot mask a jumping sticky header; live sites remain manual/nightly smoke targets rather than merge gates |
| Gecko media, fullscreen and PiP policy | `GeckoMediaRulesTest`, `FullscreenVideoRulesTest`, `GeckoBrowserEngineAdapterTest` and `GeckoPictureInPictureInstrumentedTest` on a dedicated API 34+ emulator |
| Android intent routing | `IncomingBrowserIntentInstrumentedTest`, `BrowserIntentFilterInstrumentedTest`, plus `MainActivityExternalBackInstrumentedTest` when lifecycle matters |
| Distribution and TLS channels | `./gradlew testFullDebugUnitTest testFossDebugUnitTest testFullUserCaDebugUnitTest assembleFullDebug assembleFossDebug assembleFullUserCaDebug`, then `python3 scripts/test_network_security_apks.py` |
