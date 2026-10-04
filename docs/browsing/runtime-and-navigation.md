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
| Shared Gecko / System WebView CSS safe-area protection | Persistent bounded CSS anchors, configurable mutation/interaction gates, native header fallback and status-bar backdrop; scroll cancels broad discovery | `GeckoSafeAreaSettings`, `candy_privacy/content_safe_area_prototype.js`, `content_safe_area_reddit.js` |
| System WebView safe-area bridge | Synchronous document-start configuration, renderer-top clamping and navigation/revision-validated native reports | `SystemWebViewSafeAreaScript`, `SystemWebViewBrowserEngineAdapter` |

- Initial Gecko loads wait for an attached, measured viewport. Readiness is checked on both layout
  and attachment, so a view measured before attachment loads without waiting for a keyboard resize.
  Replacing or canceling the pending load removes both observers.
- A restored web tab waiting for its first viewport-bound load ignores initial `about:blank`
  navigation/location callbacks. Those engine placeholders cannot replace its saved URL and remove
  the page viewport before the queued load starts. Blank tabs, popups, explicit later blank
  navigation, failures and renderer termination keep their normal behavior.

## Gecko loading surfaces

| Surface | Background and lifetime | Owner |
| --- | --- | --- |
| New view before session binding | Opaque Gecko dark/light fallback follows the view context's night mode; set before the native surface can attach | `CandyGeckoViewSafeAreaBridge` |
| Session-bound view before first paint | Native cover and compositor clear color follow Gecko's effective website color scheme, including explicit overrides; Gecko clears the cover after its first paint | `CandyGeckoViewSafeAreaBridge`, Gecko compositor |
| Internal privacy bootstrap document | `color-scheme: light dark` gives the temporary extension document a theme-aware canvas while the real website waits for its privacy handshake | `candy_privacy/bootstrap.css` |
| Loaded website, reload and restored view | Keep direct `SurfaceView` rendering and Gecko-owned website colors; no persistent Candy page recoloring or loading overlay | `CandyGeckoEngineView`, Gecko compositor |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| External keyboard or mouse | `MainActivity` → `BrowserHardwareInputRules` → controller | Consume documented browser chords and auxiliary Back/Forward buttons; hand unmatched hardware keys to the selected engine unless browser chrome owns the IME; keep pointer wheels on normal Android dispatch and normalize only non-pointer vertical wheel reports before using the engine's relative-scroll fallback |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Trusted Google AMP URL | `AutoDeAmpRules` → shared main-frame navigation listener → controller-owned replacement load | When the default-on setting is enabled, unwrap only `google.com`/`www.google.com` `/amp/[s/]…` viewer URLs and reversible one-label `*.cdn.ampproject.org` `/c[/s]/…` or `/v[/s]/…` document-cache URLs; deny the wrapper before posting one publisher load |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized HTTP(S) URLs from `ACTION_VIEW` data or the complete `EXTRA_TEXT` value of `ACTION_SEND` `text/plain` and `text/html` shares. Incoming URLs stay in Candy without automatically handing the initial URL or its redirects back to another app; a subsequent user tap can authorize a handoff. The optional external-link preview keeps a transient Gecko session outside the tab/session store until **Open in Candy** creates a regular tab in the chosen profile; when disabled, the immediate-tab path remains. Root Back returns to the calling app. |
| Explicit special-scheme address | `BrowserUriPolicy` → `ExternalAppLauncher` | Treat typed, pasted or scanned safe schemes as user-authorized app handoffs; keep internal schemes blocked |
| App link or special scheme | `ExternalNavigationPolicy` → `BrowserUriPolicy` → `ExternalAppLauncher` | Keep a tapped same-site HTTP(S) redirector in the engine so its server redirect can resolve; route documented `play.google.com/store/` links explicitly to Google Play with web fallback; offer other cross-site targets and the remaining bounded redirect chain, including new-window and external-preview navigation, only to a direct non-browser default handler; either launch automatically or require confirmation according to the persisted browser setting; stop a redirected handoff before Android opens and restore only the validated source history entry; reopen an immediately returned same-site web link in its source Candy tab or existing external preview instead of creating another navigation surface; keep unavailable or ambiguous links in the engine; allow safe main-frame special-scheme handoffs; block unsafe/internal schemes and subframes |
| APK link or redirect | `ApkDownloadNavigationRules` → browser download pipeline | Route a tapped main-frame APK link and its authorized redirect chain directly to the selected download manager instead of rendering a blank engine page |
| Link Peek | `LinkPeekPreviewNavigationPolicy` → transient Gecko session | Keep only HTTP(S); do not hand off preview navigation |
| Site Capsule | `CapsuleIntentRules` → capsule runtime | Apply capsule-specific navigation boundary before normal routing |
| Desktop view | `DesktopSiteRules` / `DesktopNavigationRules` → controller → engine session | Store registrable domains per profile; coordinate Gecko's desktop user-agent and viewport mode with the target navigation |
| Always block pop-ups | `PopupSiteRules` → controller → Gecko navigation delegate | Reject popups for configured registrable opener domains; preserve transient popup/popunder quarantine and pending-window policy; persist regular settings per profile and keep private settings memory-only. Valid HTTP(S) popup targets leave pending state even for IP literals or localhost; URL acceptance uses `BrowserUriPolicy`, independently of filter-domain canonicalization. |
| Federated login | `FederatedLoginRules` → controller → Snackbar and `AlertDialog` | Detect only known cross-site identity SDK endpoints; change cookie, user-agent and popup policy only after explicit consent |
| CAPTCHA compatibility | `CaptchaCompatibilityRules` → controller → Snackbar and `AlertDialog` | Detect strict cross-site Cloudflare, Google reCAPTCHA, or hCaptcha endpoints; allow third-party cookies only after explicit consent |
| HTTP Basic authentication | `HttpAuthPromptRules` → `BrowserController` → `HttpAuthPromptDialog` | Prompt only for a selected, resumed tab when challenge host matches current top-level HTTP(S) host; keep credentials memory-only and warn on cleartext HTTP |
| Gecko downloads and uploads | `BrowserEngineDownloadRules` / `FileChooserRules` → controller → Android download/file presenters | Accept bounded HTTP(S) downloads and readable `content://` file results only; stage selected documents in app cache for GeckoView's path-based file prompt, cap staged content at 1 GiB, remove it when the session ends and clear orphaned files at next startup; reject stale session/navigation/activity results |
| Gecko permissions and prompts | `PermissionRequestRules` / `BrowserWebPromptRules` → controller → existing Candy dialogs and Android permission presenter | Preserve profile/private permission scope, deny stale prompts, and fail closed for unsupported sensitive prompt classes |
| Local userscript | `UserScriptRules` → Gecko Topping document-start bridge | Require an explicit HTTP(S) pattern, top frame and regular tab; apply full URL exclusions before source runs |
| Main-frame 404 | engine HTTP status → tab state → `PageErrorFeedbackRules` | Keep the navigation committed, preserve URL/title/history side effects, and cover the page with Candy's native not-found surface |
| Offline page | failed main-frame navigation + `BrowserConnectivityMonitor` → controller → `PageErrorFeedbackRules` | Require Android's validated default internet capability, never cover an already loaded page merely because connectivity drops, auto-reload on reconnect only before the game starts, and preserve the game behind an explicit reload banner afterward |
| Gecko first-page history and Root Back | `GeckoBootstrapHistoryRules` → engine history and `RootTabBackRules` | The first app-owned page replaces its internal Privacy bootstrap entry. Back/Forward skips legacy bootstrap entries using unchanged native indices; snapshots containing any bootstrap entry are rejected. With no real previous page, Back returns to an active opener first, keeping pinned children. Without an opener, Back closes into overview or returns a last/pinned website to Candy home. Android receives Back only once that terminal tab is already home. Only the current session's exact origin and token may authorize a bootstrap navigation. |
| Gecko native new-window binding | `GeckoNativeSessionBindingRules`, `GeckoPrivacyHostRuntime`, Candy Privacy background | Authenticate the exact extension/session/token/challenge/revision through a denied internal `tabs.update`, without loading a document. The original HTTP request waits for that binding, preserving POST data, referrer and opener; timeout, tab removal and native disconnect cancel it. Route the informational initial URI through the existing popup rules because Gecko does not emit another child load-request callback for it. |
| Gecko identity popup routing | `FederatedLoginRules` → Gecko navigation request → native new-session binding | Keep user-triggered new-window navigation to known HTTPS Google identity endpoints in Gecko, including provider navigation inside an adopted native popup. External-app fallback would replace the child with a GET tab and lose `window.opener`. Existing popup rules still decide whether to admit the child; cookie compatibility remains separately consented. |
| Gecko page-requested window close | Gecko content delegate → session/adapter close-request listener → controller | Honor `window.close()` only for native popup tabs adopted from Gecko in this process. Post closure through the existing tab-removal path, recheck exact session identity, and return to a valid opener only when the closing popup was selected. Background closure preserves the foreground selection; manual/restored/recreated tabs receive no close grant. Clear listeners on close/crash and remove grants on session replacement, tab removal, snooze and controller destruction. |
| Pull to refresh | `BrowserPullToRefreshLayout` → `BrowserPullGestureRules` / `BrowserPullToRefreshRules` → `BrowserController.reload()` | Admit a downward-dominant gesture anywhere on a visible, idle web page whose engine-reported document offset is at the top; on `instagram.com`, `tiktok.com`, `youtube.com` and their subdomains, require a start within 160 dp below the top safe inset so lower feed gestures stay with the page; keep blank, obscured, Find-in-page, overview and video-only surfaces out of the gesture path |

## Invariants

- Keep activity-result registration and lifecycle ownership in `MainActivity`; focused activity helpers
  receive explicit callbacks and must not become independent lifecycle owners. Keep browser state in
  `BrowserController`, transient root UI state in `BrowserScreen`, and focused composables stateless
  except for their existing local presentation state.
- Keep the selected Gecko session active while the Activity remains started and visible behind a
  translucent system surface such as Android Sharesheet. Pause interaction-sensitive work on
  `onPause`, but mark Gecko inactive only after `onStop`; otherwise its `SurfaceView` drops the
  visible page frame during the system transition. System WebView still receives `onPause` and
  `onResume` with the Activity because those calls suspend and resume its renderer processing.
- Keep separate browser intent filters for untyped HTTP(S) links and HTTP(S) links carrying the
  `text/html` MIME type. Adding a MIME type to the untyped filter makes ordinary links ineligible.
- Register shares only for `ACTION_SEND` `text/plain` and `text/html`. Treat `EXTRA_TEXT` as the
  canonical literal payload for both types, require the complete value to normalize as one HTTP(S)
  URL within 32,768 characters, and never select a URL from prose, `EXTRA_HTML_TEXT`, or
  `ACTION_SEND_MULTIPLE`.
- Show GeckoView and System WebView fullscreen content with Candy's address, tab, find and status
  chrome hidden. Gecko keeps sensor rotation for its fullscreen lifetime. A confirmed landscape
  System WebView video requests landscape while its page is fullscreen; game and other non-video
  System WebView fullscreen follows Android's normal orientation and user rotation policy.
  A native custom-view callback reports page fullscreen only: unrelated active media does not
  become fullscreen. The media bridge identifies video fullscreen through DOM element ancestry.
  System WebView keeps its native `WebChromeClient` custom view instead of entering Gecko's media
  presentation path. Native fullscreen exits once through its callback; the original document's
  scroll offset is restored after the inline visual-state/frame fence. Navigation, a newer
  fullscreen view and session close invalidate that memory-only restoration.
  In-app mini-player placement restores
  normal browser chrome.
  Web-content fullscreen takes orientation priority over the tab overview portrait lock; exiting restores
  the current browser orientation, system-bar policy and soft-input adjustment. While system bars
  are hidden, keep the Activity at full height and let Compose IME insets move browser chrome above
  the keyboard; this avoids OEM `adjustResize` implementations leaving a black keyboard-sized area
  after the IME closes. Tab overview requests portrait only on compact screens; tablets and other
  `sw600dp` windows preserve their current orientation.
- Forward effective window insets directly to each attached Gecko display through
  `GeckoDisplay.windowInsetsChanged`; dispatching them to the child Android view does not reach
  GeckoView's root-only keyboard listener. Replay them after session/view attachment. Gecko owns
  focused-input scrolling. Reserve the keyboard's bottom inset in the inner GeckoView's native
  margins so its rendering surface and visual viewport shrink, including in full immersive mode
  while Candy's outer host remains full height. Combine keyboard and native safe-area bottom
  margins with their maximum; a Compose safe-drawing host already owns keyboard space.
  On Activity resume and window-focus restoration, reconcile the current root insets and request
  a fresh content traversal. A hidden-IME callback missed while stopped or behind another window
  must not leave the inner renderer cropped by the previous keyboard height.
  Address editing and Find in page retain chrome-owned
  IME suppression, so their keyboards do not resize the underlying website.
- When an address suggestion selects an existing Gecko tab, dismiss the address editor and
  keyboard before binding the destination session. Wait for the observed IME bottom inset to
  reach zero and the next rendering frame; a native surface rebound while the keyboard is
  still open can retain a keyboard-sized crop even after Android and DOM bounds recover.
  The pending handoff is memory-only and cancels on Back, a new address-editor generation,
  source-tab/profile/private-mode change or newly opened settings/tab overview.
  Revalidate the destination before selecting it; private
  tabs never contribute persisted preview content. System WebView keeps its existing immediate
  tab-selection path.
- Gecko presentation readiness follows physical SurfaceView creation/destruction, compositor
  composition and page paint. A temporary surface loss preserves pending presentation requests;
  releasing view ownership cancels them. Android ViewTreeObserver drawing is not a Gecko frame
  signal. Posted presentation callbacks revalidate the surface generation before releasing a tab
  handoff, and rearm after a newer surface or paint reset.
  A previously painted document remains eligible for presentation after temporary surface loss,
  whether paint resets before or after the surface is lost. Current-document paint history survives
  that reset until navigation or close. Its replacement surface must still
  deliver a new composite. Gecko 157 can omit another contentful-paint callback for that unchanged
  document; waiting for it would leave the departing screenshot permanently over a live page.
  Navigation and renderer termination discard the retained document proof. Initial pages, new
  documents and paint resets on a continuously visible surface still require contentful paint.
- A Gecko host captures its departing content on window-focus loss and keeps one short-lived
  bitmap only in RAM. If its surface is recreated, a native overlay retains that frame while the
  renderer stays attached and active. Current compositor/paint readiness releases the overlay.
  A retained valid surface needs no new paint event. Navigation, renderer termination and view
  release clear the buffer and reject late captures; private frames never enter preview storage.
  Gecko paint reset alone preserves the departing frame because it can also occur on pause.
- Route untrusted URLs through existing normalizers. Do not add a second permissive parser.
- Keep Auto De-AMP browser-wide, persisted and enabled by default. Both GeckoView and System WebView
  enter the same main-frame listener. Rewrite only trusted Google viewer/cache document shapes whose
  publisher host can be validated without ambiguity; reject user info, custom ports, local/IP targets,
  irreversible cache-host hashes and nested wrappers. Preserve `/c` document-cache query and fragment
  data as publisher-owned; for Google and `/v` viewer URLs, strip known viewer-only query and fragment
  metadata while preserving the remaining publisher data. Deny the wrapper navigation, then
  post exactly one publisher load so the AMP URL never becomes an extra history entry. Cancel that
  posted replacement if the setting changes or a newer request reaches the same engine session, and
  deny a repeated no-gesture replacement to the same publisher within the bounded redirect-loop guard.
  New-window targets keep their existing popup policy.
- Before System WebView replaces an explicit destination, stop its active load. Gecko serializes a
  newer explicit load behind any accepted native history restore before replacing the previous
  document. In both engines, delayed privacy-policy callbacks apply only to their still-current
  request, so an older completion cannot replace a newer user destination.
- Keep the external-app return marker memory-only and scoped to the tab opened by the latest accepted
  `ACTION_VIEW` or `ACTION_SEND`. Engine history consumes Back first. A root tab with an active opener
  closes and returns to that opener, including foreground/background tabs opened through Link Peek.
  A pinned root tab with an active opener selects that opener while keeping the pinned tab and its
  website intact. Only once the opener is absent do the Home/overview rules apply. A deletable root
  tab with another active-profile sibling closes
  into the tab overview. When the root tab is the active profile's last tab, or the selected root tab is
  pinned, first return its website to Candy home in the same tab, profile and private mode; retain its
  pin and close the engine session with Back/Forward capabilities cleared. Only Back from that blank
  home delegates to Android. Externally opened root tabs retain Back-to-caller priority. Tabs in other
  profiles do not become implicit Back targets.
- An accepted incoming link leaves Site Capsule presentation and closes Settings, the Firefox
  extension manager, extension action popup and other transient navigation surfaces. Cancel pending address-editor callbacks
  so an older preview capture cannot open an editor over the incoming page. Reusing a returned link in
  its original externally opened tab preserves that tab's Back-to-caller marker; if its source tab or
  preview is no longer current, continue through the ordinary incoming-link path.
- An external-app browser fallback keeps the original navigation target. New-window targets open a
  popup child under the existing opener/profile/private and popup policies without replacing the
  source tab. Retain the original bounded redirect grant without extending its lifetime; allow the
  first browser fallback load once, then route its subsequent redirects through the normal app-link
  policy. A fallback never grants app handoff to an unrequested incoming link.
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
- In `Automatic` mode, use Android's direct non-browser default handler when one exists. Otherwise,
  search installed apps for a host-specific HTTP(S) handler off the UI thread. Open a sole matching
  app directly, or show Android's app chooser when several match; unavailable links continue in Candy.
  Apps installed while Candy remains open are immediately eligible. Recheck the source after lookup
  and show handoff feedback only after Android accepts the external launch.
  Bind a regular-tab lookup to the exact engine session, document-start generation and main-frame
  request revision, while retaining selected-tab/profile/private-surface checks. A same-document
  History API URL update does not cancel the original tap or its browser fallback. A new document,
  newer main-frame request or explicit navigation/Stop command invalidates the lookup immediately.
  Link Peek and confirmation prompts keep their existing strict URL/generation snapshots.
- Honor Android's selection of Candy for incoming `ACTION_VIEW` and `ACTION_SEND` URLs. Neither
  the initial URL nor its automatic redirect chain receives an external-navigation grant; this prevents
  the calling app from returning the same link to Candy indefinitely. A subsequent user tap in the
  preview or regular tab follows the normal bounded external-navigation policy.
- Launch external activities with `FLAG_ACTIVITY_NEW_TASK` even from an Activity so standard-mode
  app activities use their own task affinity rather than joining Candy's Recents entry. Rebuild
  `intent://` requests with only `ACTION_VIEW`, browsable data, and the requested external package;
  discard supplied components, selectors, extras, and flags. Valid HTTP(S) intent data can reach the
  named app before its validated browser fallback, with non-browser/default-handler requirements;
  Candy's own package and unsafe/internal schemes remain ineligible.
- Offer user-tapped HTTP(S) links to matching non-browser apps in `Automatic` mode. Keep browsers out
  of the app chooser; unavailable app links continue in Candy. A same-registrable-site
  redirector such as a search result's intermediate URL also stays in that session; its bounded
  user-navigation grant remains available to the cross-site server redirect that follows.
- Persist the browser-wide external-app handling mode as `Automatic` by default or `Always ask`.
  In ask mode, resolve HTTP(S) app-link availability without launching, show one current-source-bound
  confirmation, and never launch before confirmation. Cancellation leaves the source in place;
  links without a direct app handler continue in Candy without a misleading prompt.
  HTTP(S) handlers must declare an authority matching the target with a non-empty host suffix.
  A catch-all `host="*"` download or utility handler is not a site app, even when Android selects it
  as the default. Apply the same check to installed-app discovery and actual unscoped launches;
  an empty candidate list returns to Candy without retrying a generic handler. Domain-scoped
  wildcard handlers such as `*.spotify.com` remain eligible for matching subdomains.
  User-invoked **Open in app** actions remain already confirmed and use the same default-handler and
  matching-app selection as `Automatic`, including a chooser when several matching apps exist.
  Apply the same navigation policy
  before creating a `target=_blank` or `window.open` tab so app links cannot bypass the handoff path.
- Remember the normalized source URL before that redirect chain. After Android accepts a redirected
  app handoff, return the engine's deny decision before posting the Android launch, stop the redirect,
  and retry source recovery both before and after Activity resume. The normal path therefore never
  commits the intermediary. If an engine race already committed it, go Back only when the same session
  still exposes the exact source as its previous entry, then replace the restored entry to discard the
  forward `302 Moved` branch. This rare recovery can reload the source but never skips to an unrelated
  document. Keep one short-lived, memory-only record of the handed-off target and source surface.
  If the receiving app immediately returns the same registrable-site web link to Candy, consume that
  record once and continue in the source tab or existing preview instead of creating another preview.
- Route documented HTTPS `play.google.com/store/` links directly to `com.android.vending` even
  from `www.google.com` search results, where both hosts share the same registrable site. Require
  a user tap or its authorized redirect chain. Do not use generic app-link resolution flags. If
  Google Play is unavailable or rejects the launch, let the originating engine session continue
  the normalized HTTPS request as Candy's browser fallback.
- For a direct app handoff from a regular tab, Gecko may report navigation started before its
  request is denied and then report navigation failed. Use the current engine history entry as the
  source even when the source page still reports loading; prefer its tab snapshot when available.
  Restore that tab when the denied target fails. Ignore late state updates for that target so
  returning from the external app shows the original page.
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
- Keep pull-to-refresh state transient and scoped to the selected engine view. Gecko scroll metrics stay
  enabled only for the selected tab, or for every tab when Candy's page scrollbar needs them, so both
  browser engines use the same top-of-document admission rule without background-tab scroll IPC.
  Missing metrics fail closed. Offset the native refresh indicator below the top safe-drawing inset so it
  stays clear of display cutouts. Normal navigation does not show the pull indicator, and the existing
  menu reload remains the accessible non-gesture action.
  Full-page pull admission is the default. Only Instagram, TikTok and YouTube (including subdomains)
  retain the 160 dp top start zone to protect Reels, video feeds and nested scrollers below that zone.
  Match the canonical HTTP(S) host, never URL path/query text or lookalike domain suffixes, and update
  the restriction with the selected page. A gesture keeps the start-zone decision made on touch-down.
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
  plays the page exit motion before performing the only retry. Load the exact failed URL when the engine
  has no matching committed history entry; retain normal reload semantics when history already points at
  the target, including committed HTTP failures such as 404.
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
- System WebView enables wide-viewport and overview loading in both mobile and desktop modes.
  Mobile pages retain their authored viewport width and scale; legacy pages without a viewport
  directive can fit their wide layout to the screen. Returning from desktop mode does not disable
  this mobile viewport support.
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
  A selected Gecko tab's first navigation waits until its renderer view is attached, measured and
  has received the current native insets. This preserves author `initial-scale` viewport directives
  on the first load; background sessions and later navigations keep their existing load behavior.
  Both engines use `GeckoViewInsetRules` with native CSS safe-area delivery enabled and the
  [shared Candy Edge prototype](#current-candy-edge-prototype). Gecko's content bridge and
  System WebView's synchronous `SystemWebViewSafeAreaScript` install the same prototype and Reddit
  helper from `candy_privacy/`. System WebView no longer installs `WebContentTopInsetScript`.
  The shared prototype protects body flow and bounded fixed/sticky anchors, preserves existing
  cover-page protection, and reports native top-header fallback and status-bar backdrop with the
  current navigation generation and policy revision. The System WebView bridge bounds its CSS
  policy inset by the current layout's top inset and sets it to zero while a native top margin owns
  that edge. `SystemWebViewHost` clamps every Android `dispatchApplyWindowInsets` and
  `onApplyWindowInsets` delivery before provider code observes it. `SystemWebViewSafeAreaRules`
  enables native CSS safe-area delivery only for a known provider milestone of at least 144.
  Older or unrecognized providers receive zero renderer safe-area insets from their first delivery,
  while the shared CSS policy still receives the actual layout/policy top inset. Because that CSS
  protects only the top edge, `SystemWebViewSafeAreaRules.withNativeCutoutMargins` preserves actual
  side/bottom `displayCutout()` edges in native margins for those legacy providers; it does not add
  a general navigation-bar bottom margin. Fullscreen and Compose safe-drawing hosts retain their
  existing ownership and receive no extra margins. This avoids stale native `env()` values when Force safe area activates; WebView 133 can retain a previous cutout
  inset after the platform cutout becomes null. No synthetic zero cutout is introduced. Modern
  native `env()` values remain available subject to the same layout exclusions and authoritative
  where the page already keeps cover content below the safe area.
  Native top-header activation and removal set the final engine margin immediately, then ease the
  previous top edge into place with a temporary visual offset. The renderer receives its final
  viewport in one layout pass. Fullscreen, safe-drawing hosts, forced fallbacks and changed system
  insets always snap. The explicit per-site **Force safe area** override retains native margins.
  GeckoView always keeps its default SurfaceView backend so frames reach Android's compositor
  directly. Android 17 and newer apply Frosted blur through a rounded native SurfaceView region;
  Android 13 through 16 keep the translucent glass treatment without website blur.
  PiP, clipping and tab motion preserve the same browser host, GeckoView, surface, display and
  session. The static status-bar overlay remains outside the renderer and keeps system icons legible.
- Read page-scroll metrics through the engine port. The optional `BrowserScrollBar` observes them
  at up to 60 Hz without replacing the independently rate-limited pill-collapse scroll path and is absent in
  fullscreen/video-only mode. Gecko's device-pixel-scaled document metrics update only the scrollbar;
  they never enter the renderer-coordinate pill-collapse direction reducer.
- The pill-collapse dispatcher defaults to optimized mode: 30 updates per second, stepping down to
  15 only after sustained slow UI frames measured while scrolling. Developer options
  can instead select fixed 120, 60, 30 or 15 Hz caps. The adaptive tier is session-only and is not persisted.
- Keep page touch streams and native fling physics in GeckoView. Compose parents must not cancel
  an active page gesture while arbitrating AndroidView input. No Chromium-specific reverse-fling
  workaround runs in the Gecko renderer. Android window-focus loss, engine deactivation and view
  detachment are terminal boundaries: if the platform omitted a final touch event, Candy sends one
  synthetic `ACTION_CANCEL` to Gecko before rejecting background content-menu callbacks.
- Add pure policy beside the owning package; leave `BrowserController` as integration wiring.

## Shared CSS safe-area controls

### Current Candy Edge prototype

Gecko's experimental host manifest and System WebView's document-start bridge select the same
`candy_privacy/content_safe_area_prototype.js` and `content_safe_area_reddit.js` assets. The larger
Gecko `content_safe_area.js` classifier remains in source but is not selected; both classifiers must
not run together. The legacy `WebContentTopInsetScript` is not installed by either normal engine.
This prototype is not a universal layout-compatibility guarantee.

| Engine boundary | Inset and lifecycle contract |
| --- | --- |
| GeckoView | Native safe-area values remain available through CSS `env(safe-area-inset-*)`; the content bridge provides the shared policy and validates native reports |
| System WebView | `SystemWebViewSafeAreaScript` synchronously provides normalized shared settings, navigation generation and revision. Its CSS policy top inset is bounded by the current layout/policy inset and cleared for a native top margin; fallback and backdrop reports return through the same native bridge. `SystemWebViewHost` clamps every `dispatchApplyWindowInsets` and `onApplyWindowInsets` delivery before provider code, so framework traversals cannot restore an inset already owned by native margins |
| Modern / older WebView | `SystemWebViewSafeAreaRules` gates native CSS delivery at a known WebView milestone of at least 144, matching Android's documented full `systemBars()` / `displayCutout()` support. Older or unrecognized versions receive zero renderer safe areas from the first dispatch while the shared CSS retains the actual layout/policy top inset. This prevents stale cutout `env()` values after Force safe area on WebView 133. Legacy CSS protects only the top, so actual side/bottom cutout edges retain native margins through `withNativeCutoutMargins`; navigation-bar bottom alone adds none. Fullscreen and safe-drawing hosts keep their existing ownership without extra margins. Modern cover pages preserve existing native `env()` protection; no synthetic zero cutout is used |
| Native exclusions | Fullscreen, Compose safe-drawing hosts, Link Peek and native fallback margins retain their existing duplicate-inset exclusions |

WebView's version-dependent native inset behavior is described in
[Android's window-insets documentation](https://developer.android.com/develop/ui/views/layout/webapps/understand-window-insets).

| Rule | Prototype behavior |
| --- | --- |
| Inset source | Existing native policy inset divided by device-pixel ratio, exposed as `--candy-safe-area-inset-top` |
| `viewport-fit=cover` | Inspect visible fixed/sticky semantic headers, the first 64 body elements, and a short normal-flow chain. Preserve top positions and padding already at or beyond the native inset; apply missing body/header top protection when visible content enters the top safe band. Recheck relevant author DOM/style and viewport-meta changes, and watch native `env(safe-area-inset-top)` delivery for at most three seconds after configuration so late renderer insets cannot leave a duplicate offset. Skip predeclared selectors, CSS-source cloning and native top-header fallback on cover pages. Both engines retain native `env(safe-area-inset-*)` delivery where supported. Reddit's scoped component helper remains active. This top-edge geometric check cannot prove protection for every hidden or later-activated layout. |
| Normal page flow | A per-document stylesheet raises body top padding to at least the inset; larger initial padding is preserved |
| Semantic top header | A visible viewport-wide `header`, `nav`, `[role=banner]`, or `[role=navigation]` with `position: fixed/sticky` and a nonnegative top anchor requests a navigation-scoped native top margin on non-cover pages. Reddit discovery prioritizes its known `reddit-header-small`, `reddit-header-large`, and `shreddit-header` hosts. Candy paints the status-bar sibling with the active `theme-color`, then the resolved opaque header/page background, and selects contrasting status icons. Cover pages retain engine-owned edge-to-edge layout and use the bounded CSS check above. |
| Status-bar backdrop for protected headers | During document/header discovery, a viewport-wide fixed or sticky semantic header anchored to the top safe inset and an opaque active `theme-color` request a solid status-bar backdrop. At most 24 cached header/ancestor styles are checked in one discovery pass; recent additions take priority, and Reddit's open component roots report structural header changes. Repeated class states are ignored; changed states and Cover layout changes are coalesced after 150 ms. Scrolling never requests backdrop work. The backdrop only paints Candy's status-bar sibling; it does not alter the engine viewport, CSS inset, or native top margin. The report is revision/navigation-validated and cleared on navigation or tab removal. Theme metadata and media-query changes update or clear the color. Pages without a valid theme color keep the normal status-bar treatment. |
| Other fixed / sticky | Bounded per-element stylesheet rules apply `originalTop + inset` to discovered nonnegative finite resolved CSS-pixel tops. Negative tops retain author geometry by default; the developer **Add inset to negative top values** switch restores addition. Fixed boxes extending into the lower half of the viewport are excluded because CSSOM can resolve an undeclared top on a bottom-anchored box; no positioned-element padding or inline top is added |
| Full-viewport fixed modal | A fixed box with top and bottom at zero and geometry covering the viewport may receive the top inset while retaining bottom zero. A fixed full-viewport overlay containing a full-viewport dialog instead gives its border-box content top padding and lifts bounded absolute buttons in its first header below the inset, leaving the overlay behind the status bar. This covers ReactModal-style portals whose dialog is absolutely positioned and centered with a transform. Narrow dialogs, scrims, and bottom-anchored navigation retain their geometry. At startup and after DOM insertion, bounded candidates with an inline full-viewport inset or modal/dialog hint bypass the click gate; they are checked in the cooperative worker. Tracked boxes are rechecked when their class, style, or content changes, so hidden modals and drawers release protection. Cover pages still preserve author padding or child content already below the safe area and recheck when a tracked modal disappears. A nameless full-screen overlay with unsafe child content may be indistinguishable from a modal; named scrims and backdrops without a full-viewport dialog child are excluded. |
| Predeclared selectors | Initial and event-driven CSS-source scans protect full selectors with literal `fixed`/`sticky` and a finite pixel `top` in the same CSS declaration block, even before any element matches that state. Negative declarations participate in same-selector source precedence but receive no protection rule unless the developer switch is on. Fixed selectors with a bottom anchor or inset shorthand require element geometry and stay on the bounded element path. |
| Selector ownership | Elements matching a protected selector do not receive a second element-level top addition; body padding and unmatched element protection remain separate |
| Retained anchors | Existing rule identities are checked before reading computed style; normal nonnegative author inline resets do not remove the rule or add another inset. With negative-top addition off, actual class changes, negative/unresolved inline tops and inline-top removal check up to 16 affected cached or selector-owned targets outside the interaction gate. Ancestor class changes include bounded cached descendants; newly inserted selector matches get a bounded depth-two check. Candy's two top stylesheets are disabled only during synchronous author-top reads. Negative targets release element-top protection and receive a zero-specificity selector exclusion; returning to a nonnegative top restores protection. Repeated unchanged states and ordinary scroll do not perform these reads. Unsupported/opaque CSS, unobserved layout changes and targets beyond these limits remain best-effort. Reddit's separate component rules retain their existing scoped behavior. |
| Amazon.in sticky toolbar states | On amazon.in and its subdomains, non-cover pages seed `:root .s-mobile-toolbar-sticky` with one inset top and the more specific `.s-mobile-toolbar-sticky.s-mobile-toolbar-offscreen` state with `top: 0px !important`. The hidden state retains the author’s `translateY(-100%)` behavior so no toolbar remainder enters the status-bar band. Both rules share the authored-negative-top exclusion, enable/inset/cleanup lifecycle and selector ownership; class switching uses CSS matching without new scroll callbacks or scans. The same shared rules run on Gecko and System WebView |
| DOM fullscreen | The fullscreen root and its descendants do not acquire per-element top-header rules. Entering fullscreen releases only their owned top declarations. On exit, the bounded former subtree is rechecked: inline players regain authored geometry, while still-fixed surfaces receive their original top plus one inset. Author inline styles and unrelated protections stay intact. |
| Other top values | Literal `auto` and unresolved values are not changed. Negative resolved CSS-pixel values are preserved by default; only explicit developer opt-in adds the inset. Nonnegative and above-inset tops remain included subject to the lower-half fixed-box guard. The switch defaults off, persists globally, resets with the shared defaults and rebuilds active protection when changed. |
| Initial discovery | Protect the first available body without waiting for the worker; one bounded body traversal plus a coalesced semantic check when the DOM becomes interactive and again at final load; at most eight semantic candidates, 32 cached candidate/ancestor identities and eight fixed-header proofs are retained |
| Later discovery | DOM subtrees retain trusted click/drop gates. Relevant semantic additions/class changes, trusted clicks and stylesheet loads also request the bounded semantic check, allowing late SPA hydration without a reload. CSS-source changes retain their separate queue without an interaction requirement. |
| Same-document routes | A same-document URL change while loading stays false starts a new safe-area generation even when the document remains alive. Candy clears the previous route's native top-header fallback and status-bar backdrop, then republishes the policy. The prototype rechecks an unchanged `viewport-fit=cover` declaration for the new route. During the next two seconds, ordinary element moves in the document or Reddit's observed open component roots can request at most three debounced cover rechecks; these are mutation-driven and never triggered by scrolling. Title-only state changes retain the current generation. |
| Scroll | Only pages with active Candy safe-area protection register scroll listeners. Their handler cancels pending broad work and advances a generation; non-cover pages may verify cached fixed-header candidates after at least 150 ms of quiet for native fallback. An already-pending semantic header check survives an early scroll and runs after the quiet period. Cover pages already handling their own safe area have no scroll listener. Status-bar backdrop discovery never starts from scroll. The scroll handler performs no style, geometry or selector reads, and scroll never starts a new document query. |
| Settings | Existing enable, DOM mutation/interaction, batch and resize controls remain; CSS sources reuse worker batch/time limits with fixed prototype source limits; only post-load sources use the 500-ms cooldown |
| Native / privacy | The outer host remains full-window. Top-header and emergency fallback state is memory-only, tab/navigation-scoped, revision-validated, and cleared on navigation or tab removal. |
| Reddit component exception | `content_safe_area_reddit.js` supplies scoped app-flow/header rules in the document and observed open component roots, including `reddit-header-small`, `reddit-header-large`, and `shreddit-header`. It uses the greater of the renderer's `env(safe-area-inset-top)` and Candy's bounded inset; scroll-state attributes remain CSS-only. |

This iteration tests approach A: persistent author-origin CSS, not periodic mutation repair. Each
document owns separate element and selector stylesheets and bounded element markers. Rules persist while that document and
their matching elements remain; disable/configuration changes remove the prototype's rules and
markers without restoring over the page's newer inline top or padding. Existing discovery gates
remain: an unrelated replacement element is not automatically protected merely because its
predecessor was protected. No 500-ms background DOM scan is introduced.

The selector experiment protects predeclared class-driven states: when scrolling adds a persistent
header class, the engine applies the matching CSS rule without a new Candy style/geometry measurement.
New elements matching an admitted selector also inherit protection without discovery. The initial
CSS scan and any bounded reconciliation alternate with element discovery in the same cooperative worker;
ordinary scroll does not initiate scanning or selector classification.

The initial body rule is published synchronously when an active policy and body are available,
including the parser's first body insertion. Body protection created while loading has one
synchronous author-padding refresh when the DOM becomes interactive; only its own padding
declaration is temporarily removed for that read and immediately replaced with the greater of
author padding and the inset. This preserves larger author padding without a yielded unprotected
frame. It is not a guarantee against layout shifts caused by later author CSS or delayed native
policy delivery, and does not introduce recurring body measurements.

The CSS-source queue registers at most 128 stylesheet identities, visits at most 4,096 rules per
source version, and caps the configuration epoch at 65,536 rule visits, 4,096 source events and
131,072 cooperative CSS work steps. Unsupported entries consume these budgets. It admits at
most 256 distinct selectors, at most 2,048 characters per selector and 32,768 characters in the
combined ownership matcher. The configured total protection-rule cap also applies. Source top
importance is retained when choosing between eligible rules with exactly the same selector;
accepted protection declarations themselves are important. Element and selector rules share the
configured protection cap, with one slot reserved for body protection. Body protection is processed
before the initial CSS queue. Exhausted budgets or scroll cancellation can leave coverage partial;
scrolling never resumes the scan.

Initial sources come from `document.styleSheets`; source insertions/removals, text changes and
`href`/`rel`/`media`/`disabled` attributes enqueue only the affected source. Link `load` events
capture newly available sheets. Sources recognized before full load are queued immediately;
initial discovery also shortens a previously delayed pending deadline. Post-load sources are
deduplicated and wait 500 ms from their first queued event; further changes do not indefinitely
postpone that deadline. There is no
periodic polling or background full-DOM repair. Changes arriving without CSS-source events do not
start this queue merely because a positioned element changes class while scrolling.

Captured source candidates are combined in current stylesheet order. A replacement selector sheet
is built with inactive media; only a completed replacement becomes active. Canceled staging work
does not remove the last committed protection. Source updates replace captured top values, never
read Candy-adjusted computed tops or repeatedly add the inset. Candy's own active/staging sources
are excluded from ingestion. The existing manual probe can export aggregate source counters only;
it does not trigger processing or add style/geometry reads.

Validated selector rules are also stored as the staging style element's text before activation.
Gecko rebuilds a style element's sheet after media-attribute changes or detach/rebind; empty text
would discard CSSOM-only insertions. Persisting canonical text retains the rules during this swap,
and committed rule references are refreshed. This adds bounded text preparation/parsing, not
scroll-driven work. Original Page Source remains distinct from the live injected style element.

Only plain loaded stylesheets and ordinary complete selector rules are admitted in this first
iteration. Grouping contexts (`@media`, `@supports`, `@layer`), nontrivial sheet media, imports,
keyframes, CSS nesting, split position/top declarations and non-pixel top expressions are skipped.
Inaccessible cross-origin `cssRules` are skipped without fetching a second copy of page CSS.
Direct `insertRule`/`deleteRule`/`replace`/`replaceSync` edits without DOM source events, adopted
stylesheets and shadow-tree sources are not monitored; no page-world API hooks are installed.

The maintained known-site rule set contains an Amazon.de exception:
`:root #btf-sub-nav-top-navigation-bar.persistent-header` receives
`top: calc(0px + var(--candy-safe-area-inset-top)) !important` in the early owned layer.
Only amazon.de and its subdomains match. The rule protects the observed zero-top header as soon as
its class activates, regardless of external stylesheet accessibility or inline normal resets;
`:root` raises specificity above the observed author-important selector. It reserves one rule
slot, prevents duplicate element-top protection and follows the same enable/inset/cleanup lifecycle.
There is no extra observer, network request, scroll scan or separate per-site setting.
Source-discovery, opaque-CSS and LINK-race experiments are not included in this smaller follow-up.

Amazon.in and its subdomains seed a separate non-cover toolbar-state pair in the same shared
layer. `:root .s-mobile-toolbar-sticky` receives one inset top; the more specific
`:root .s-mobile-toolbar-sticky.s-mobile-toolbar-offscreen` receives `top: 0px !important`.
Amazon hides that toolbar with `translateY(-100%)`, so keeping an inset top while hidden would
leave an inset-sized remainder in the status-bar band. The hidden rule preserves the original
zero-top hiding anchor without changing the authored transform. Both declarations use the same
negative-top exclusion and prevent duplicate generic element-top protection. Gecko and System
WebView apply the class-state change through CSS matching; no extra scroll callback, observer or
scan is introduced. Cover pages retain their existing shared-policy exclusions.

For google.com/google.de and their subdomains, the observed absolute compact-menu container
`:root #navd` and expanded-search state `:root #tsf .A7Yvie.emcav` receive the same early important
zero-top-plus-inset rule. The menu rule moves its hamburger below the status bar without shifting
unrelated page flow. The stylesheet also exists before a later focus changes the search container
from static to fixed; browser selector matching supplies protection without a delayed Candy repair.
The rules reserve slots and skip duplicate element protection, using the same
enable/inset/cleanup lifecycle. Other Google layout variants are not inferred.

For reddit.com and its subdomains, `content_safe_area_reddit.js` owns one stylesheet per relevant
scope: document rules are restricted to `shreddit-app`; open app roots receive local rules and
open `reddit-header-small`, `reddit-header-large`, or `shreddit-header` roots receive host-relative
rules. The app gets its author `--page-y-padding` plus the greater of renderer and Candy inset as
top padding, rather than adding the inset later at `.main-container`. Fixed Reddit header variants
get inset top; each `.relative` variant subtracts the author page-padding reserve from its top
offset. Its internal `header` gets inset top padding
only while the host has `hidden-by-scroll`. Observed Reddit layouts put the target nodes in light
DOM despite owning additional open shadow roots, so document and shadow scopes remain distinct.
The app-level flow reserve also moves the normal-flow subreddit banner below the header;
no additional banner margin duplicates this reserve. A live r/pcmasterrace fixed-header layout
confirmed unchanged first-content position and a normally scrolling banner. A live home-page
relative-header variant with zero author page-padding retained the same safe header position.
Absolute banners and relative headers with nonzero author page-padding remain manual checks.

When an actual protected app exists, the prototype retains author body padding
without adding another Candy body inset, including before `.main-container` appears. If app coverage
disappears, general body-inset protection returns. Each handoff removes only Candy's body-padding
declaration before reading and
republishing in the same task; no cumulative inset is captured. Known Reddit header hosts also
skip generic element-top addition, and the helper's styles are excluded from CSS-source ingestion.

Initial/configuration, DOM-ready/load and custom-element-definition events synchronize only the
named components. Direct child-list observers on component roots and their immediate containers
coalesce structural changes; no attributes or feed-wide subtree observation is installed by the
helper. `hidden-by-scroll` uses ordinary selector matching, with no Candy scroll callback or
computed-style/geometry read. Limits are eight owned sheets, sixteen direct observers and 256
coalesced structural tasks per enable epoch; named selector queries are not an exhaustive DOM
budget proof. Closed roots, deeply nested unobserved replacements and roots attached without a
definition/structural event remain limitations. Disable removes only the helper's styles, observers
and pending task. There is no page-world `attachShadow` hook, polling or network request.

Same-block declarations
are candidates, not a general proof of the final cascade; inline-important and other stronger
rules remain boundaries. Real Amazon CDN accessibility and product-state coverage require separate
manual verification, not inference from the synthetic class-switch regression.

The stylesheet uses `!important`, which overrides normal inline declarations, but author inline
`!important` and stronger competing author-important selectors can still win. This is not a
user-origin stylesheet or a universal cascade guarantee. Generic initial classification still happens
after content becomes available. Known state rules are seeded in advance of later state changes;
they do not guarantee protection before native inset configuration is ready on the first page paint.
Removing or editing the prototype's own stylesheet or markers is outside this persistence guarantee;
normal header style resets are the regression target. Responsive author top/padding changes remain
masked while the corresponding captured rule wins, until its source version is updated or protection
is disabled/reconfigured; element-level captures retain their existing ownership behavior.

`top` has the CSS initial value `auto`, not zero. CSSOM `getComputedStyle()` may return a resolved
used pixel value for a positioned visible box; the prototype filters the returned value, not author
stylesheet declarations. The bounded selector scan reads explicit declaration pairs only; it does
not reconstruct the cascade of arbitrary split declarations.

The lower-half guard leaves bottom-anchored fixed controls and taller bottom panels alone when
CSSOM resolves an undeclared `top` into pixels. It can also leave an explicitly top-anchored
control extending into that region alone; the existing bounded discovery cap still applies.
Candy's floating address bar remains a separate
overlay above the full-window engine view and can cover a site's bottom navigation.

Known limitations are intentionally left for manual testing: iframe contents, absolute descendants,
nested positioning/scrolling containers, full-height fixed panels, larger DOMs beyond the traversal
cap, and unsupported stylesheet changes affecting elements outside the admitted subtree. The prototype does not
run the old classifier's footprint verification or automatically infer when emergency fallback is
needed. Existing explicit/native fallback paths remain available; bounded semantic headers can
request native top fallback as described in the table above. Known-site rules remain scoped to the
listed hosts and layouts.

Manual feedback on the previous clamp-based prototype (2026-09-14):

| Sites / issue | Feedback / verification |
| --- | --- |
| Google, Wikipedia, GitHub, CNN, Hackernews, Reddit, eBay, Kleinanzeigen, taptap.io, amazon.de | No errors reported in the tested states; not a complete state-matrix acceptance claim |
| Vinted | Sticky `top: 0` header can still be missed at initial load: host 1.5.25 passed one dedicated API 35 Release swipe, but the broader-rule host 1.5.26 missed the header on its tested load. Arithmetic and focused Gecko tests pass; real-site initial-load reliability and exact cause remain unresolved |
| Load timing | Strong flicker from post-load CSS changes; explicitly deferred until after this header-rule fix |

### Existing classifier and controls

Developer options contain a shared **Edge-to-edge** section for Gecko and System WebView. Changes
are normalized, stored as global configuration without page/private state, and pushed to live
session policies. They tune the shared prototype's CSS corrections and mutation checks. Disabling
the layer removes its owned styles and observers; native CSS inset delivery and explicit/native
fallback remain available. The retained Gecko classifier below is not the active prototype.
The existing **Safe-area fallback** section and per-site **Force safe area** remain separate.

| Control | Default | Range / effect |
| --- | --- | --- |
| CSS protection | On | Disable one-time CSS classification and its observer, not native insets |
| Recheck added elements | On | Only bounded new subtrees |
| Recheck changed elements | On | Relevant class/style/visibility/open changes, including existing menus |
| Require user interaction | On | Click, typing or drop authorizes relevant updates; scrolling clears authorization |
| Recheck on resize | On | Viewport changes may reclassify; normal scroll does not |
| Interaction window | 1000 ms | 100–5000 ms, steps of 100 ms |
| Mutation debounce | 150 ms | 50–1000 ms, steps of 50 ms |
| Elements per batch | 16 | 4–64, steps of 4 |
| Cooperative batch target | 4 ms | 1–8 ms; a single native style/geometry query can overshoot |
| DOM classification cap | 512 elements | 64–2048, steps of 64; shared initial cap and cap per later subtree, not an exhaustive DOM safety proof |

Sticky eligibility uses the declared top anchor and containing-block path, not only the current
rectangle: a header initially below the viewport can still receive its CSS anchor before sticking.
An element simply being near the status bar is insufficient. Nested scrollers, transformed
containing blocks, tall panels and otherwise unsupported layouts require bounded overlap validation
before the retained emergency fallback. No scroll event starts discovery; quiet validation reads only
the bounded candidates cached by load, mutation or interaction discovery.

## TLS trust channels

| Build | Application ID | Trust anchors | Release asset |
| --- | --- | --- | --- |
| Standard | `dev.sk2andy.materialbrowser` | Gecko built-in roots for page/engine requests; Android system roots for Android networking | `CandyBrowser-v<version>-release.apk` |
| System WebView | `dev.sk2andy.materialbrowser.systemwebview` | Android system roots for page and app networking | `CandyBrowser-v<version>-systemwebview-release.apk` |
| User CA | `dev.sk2andy.materialbrowser.ca` | Standard roots plus user-installed Android CA roots | `CandyBrowser-v<version>-ca-release.apk` |

- The build channel controls trust for both networking stacks: Android Network Security Config
  controls Android requests; `GeckoRuntimeSettingsFactory` passes `BuildConfig.TRUST_USER_CERTIFICATES`
  to Gecko's `enterpriseRootsEnabled`. Gecko owns a separate root store, so the XML configuration
  alone is insufficient. Broader trust requires installing the explicitly labeled User CA APK.
- Separate application IDs isolate app data and allow all channels to stay installed. Update
  selection preserves the installed channel and rejects a release that contains only the other
  channel's asset.
- The System WebView channel is fixed to Android's installed WebView provider and contains no
  GeckoView runtime or Firefox extensions.
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
- A main-frame HTTPS response carrying the exact case-insensitive `cf-mitigated: challenge` signal
  enters the same Snackbar-to-dialog consent flow as embedded CAPTCHA clients. A tab-scoped grant is
  memory-only for the exact current tab and page host, then reloads; the profile choice remains an
  explicit persistent decision. An external-link preview carrying this signal moves into a normal
  tab first so the same consent flow owns the exception. Embedded Cloudflare Turnstile, Google
  reCAPTCHA/Enterprise, and hCaptcha clients keep this consent flow. Their detection requires HTTPS
  plus a recognized provider host and path; lookalike,
  first-party, malformed, and generic vendor requests do not receive compatibility.
  CAPTCHA grants affect only third-party-cookie policy. They never enable federated-login user-agent
  or popup compatibility.
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
| Experimental Candy Player detects inline video | The trusted content host reports a bounded top-frame candidate. The persisted Candy Player mode decides whether a button opens fullscreen, offers inline and fullscreen presentation, website fullscreen is replaced automatically, or detection starts Candy Player automatically |
| Web page enters or exits fullscreen | `ContentDelegate.onFullScreen` owns the DOM-fullscreen lifecycle; media fullscreen metadata independently identifies the video and its dimensions |
| User selects another regular tab | The current eligible video may move into the draggable in-app mini-player; this is the only presentation path that reparents GeckoView |
| App leaves the foreground | The active eligible regular video is pinned in its original browser viewport before Activity PiP. The GeckoView, SurfaceView backend, GeckoDisplay and GeckoSession are not replaced or reparented |
| Android confirms PiP mode | The exact owning session receives one `CompositorController.onPipModeChanged` notification; preparation never pre-arms Gecko with an unconfirmed state |
| System media control is used | The app-owned Android `MediaSession` sends play, pause, stop or seek through Gecko's active native media session |
| Audible audio continues in background | A `mediaPlayback` foreground service owns the visible media notification while the Activity-owned Gecko session remains alive |
| PiP expands back into the app | Android expands the unchanged browser-hosted Gecko surface through a centered source rectangle matching Gecko's reported video aspect ratio; normal chrome returns after the expanded layout is ready, and an acknowledged inline Candy Player remains active in its original page box |
| Fullscreen closes | Candy requests `GeckoSession.exitFullScreen()` and restores normal chrome without stopping unrelated media |
| Media ends, page navigates, crashes, closes, snoozes or is destroyed | Gecko session identity invalidates the endpoint; view, notification and session cleanup is idempotent |

- Gecko fullscreen, media fullscreen and playback callbacks are independent and may arrive in any
  order. Candy merges only callbacks from the current native media-session identity; stale ad/player
  sessions cannot overwrite the active YouTube state.
- Media metadata, presentation state and mini-player position are memory-only and never persisted.
- The experimental Candy Player mode persists and defaults to the inline-and-fullscreen button
  mode. The other enabled modes open fullscreen through a button, replace website fullscreen, or
  start Candy Player when a video is detected. Off removes Candy launchers and controls, cancels
  pending opens and restores website/native controls without pausing playback; website fullscreen
  remains available. Absent, unknown and legacy preference values retain the existing button default.
  Only top-frame HTML video in GeckoView is supported; cross-origin embeds remain unsupported.
- Candy Player's backward and forward double-tap distances persist independently under
  Settings → Player, alongside the player mode and video-autoplay preference. Each accepts
  5, 10, 15, 20, 30 or 60 seconds and defaults to 10; unsupported values use
  the default independently. Runtime policy refresh applies both values to an existing player.
- Candy Player provides direct Android picture-in-picture from a recognized inline video; users do
  not need to enter fullscreen first.
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
| Pull to refresh | `BrowserPullGestureRulesTest`, `BrowserPullToRefreshRulesTest`, and `BrowserPullToRefreshLayoutInstrumentedTest` on an API 34+ emulator |
| Android web-content fullscreen chrome | `FullscreenVideoRulesTest` plus `FullscreenVideoChromeInstrumentedTest` in the Full and System WebView builds on a dedicated API 34+ emulator |
| Edge-to-edge window, safe web viewport, focused search, and representative site layouts | `SystemWebViewEdgeToEdgeInstrumentedTest` and `GeckoEdgeToEdgeInstrumentedTest` run deterministic layout profiles derived from YouTube, Google, ESPN, NYTimes, CNN, Reddit, Facebook, IKEA, GitHub, Discord, Instagram, TapTap, Vimeo, Wikipedia, Stack Overflow, and DuckDuckGo on API 34+; the TapTap profile asserts safety immediately in the scroll task so delayed post-scroll repair cannot mask a jumping sticky header; live sites remain manual/nightly smoke targets rather than merge gates |
| Gecko media, fullscreen and PiP policy | `GeckoMediaRulesTest`, `FullscreenVideoRulesTest`, `GeckoBrowserEngineAdapterTest` and `GeckoPictureInPictureInstrumentedTest` on a dedicated API 34+ emulator |
| Long background/resume page continuity | `GeckoContentPresentationGateTest` and `BrowserPageResumeInstrumentedTest` on a dedicated API 34+ emulator; repeated 31-second intervals, real input/rendered pixels, scroll/history, no reload, regular/private Gecko and System WebView |
| Android intent routing | `IncomingBrowserIntentInstrumentedTest`, `ExternalAppLauncherInstrumentedTest`, and `MainActivityIncomingNavigationInstrumentedTest` for cold/warm incoming links, initial redirects, and subsequent tapped handoffs |
| Distribution and TLS channels | `./gradlew testFullDebugUnitTest testFossDebugUnitTest testFullUserCaDebugUnitTest assembleFullDebug assembleFossDebug assembleFullUserCaDebug`, then `python3 scripts/test_network_security_apks.py` |
