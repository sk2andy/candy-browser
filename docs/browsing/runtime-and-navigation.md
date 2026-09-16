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
| Gecko CSS safe-area protection | Bounded load classification, one-time CSS anchors, configurable relevant mutation/interaction gates; scroll cancels work without geometry reads | `GeckoSafeAreaSettings`, `content_safe_area.js` |
| System WebView safe-area mutation repair | Keep related attributes, owned-subtree and stylesheet/meta changes immediate; coalesce ancestor feed insertions into an animation frame; defer unrelated opaque feed changes to full quiet owned-layout revalidation before verification | `WebContentTopInsetScript` |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| External keyboard or mouse | `MainActivity` → `BrowserHardwareInputRules` → controller | Consume documented browser chords and auxiliary Back/Forward buttons; hand unmatched hardware keys to the selected engine unless browser chrome owns the IME; keep pointer wheels on normal Android dispatch and normalize only non-pointer vertical wheel reports before using the engine's relative-scroll fallback |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized HTTP(S) URLs from `ACTION_VIEW` data or the complete `EXTRA_TEXT` value of `ACTION_SEND` `text/plain` and `text/html` shares. Incoming URLs stay in Candy without automatically handing the initial URL or its redirects back to another app; a subsequent user tap can authorize a handoff. The optional external-link preview keeps a transient Gecko session outside the tab/session store until **Open in Candy** creates a regular tab in the chosen profile; when disabled, the immediate-tab path remains. Root Back returns to the calling app. |
| Explicit special-scheme address | `BrowserUriPolicy` → `ExternalAppLauncher` | Treat typed, pasted or scanned safe schemes as user-authorized app handoffs; keep internal schemes blocked |
| App link or special scheme | `ExternalNavigationPolicy` → `BrowserUriPolicy` → `ExternalAppLauncher` | Keep a tapped same-site HTTP(S) redirector in the engine so its server redirect can resolve; route documented `play.google.com/store/` links explicitly to Google Play with web fallback; offer other cross-site targets and the remaining bounded redirect chain, including new-window and external-preview navigation, only to a direct non-browser default handler; either launch automatically or require confirmation according to the persisted browser setting; stop a redirected handoff before Android opens and restore only the validated source history entry; reopen an immediately returned same-site web link in its source Candy tab or existing external preview instead of creating another navigation surface; keep unavailable or ambiguous links in the engine; allow safe main-frame special-scheme handoffs; block unsafe/internal schemes and subframes |
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
| Pull to refresh | `BrowserPullToRefreshLayout` → `BrowserPullGestureRules` / `BrowserPullToRefreshRules` → `BrowserController.reload()` | Admit a downward-dominant gesture only for a visible, idle web page whose engine-reported document offset is at the top; keep blank, obscured, Find-in-page, overview and video-only surfaces out of the gesture path |

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
- Show GeckoView and System WebView fullscreen content with Candy's address, tab, find and status
  chrome hidden, and enable sensor rotation for its lifetime. In-app mini-player placement restores
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
  Address editing and Find in page retain chrome-owned
  IME suppression, so their keyboards do not resize the underlying website.
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
- Offer user-tapped HTTP(S) links to Android only when a direct non-browser default handler can
  receive them. Requiring both a default and a non-browser handler prevents browser/chooser loops;
  unavailable or ambiguous app links continue in the current engine session. A same-registrable-site
  redirector such as a search result's intermediate URL also stays in that session; its bounded
  user-navigation grant remains available to the cross-site server redirect that follows.
- Persist the browser-wide external-app handling mode as `Automatic` by default or `Always ask`.
  In ask mode, resolve HTTP(S) app-link availability without launching, show one current-source-bound
  confirmation, and never launch before confirmation. Cancellation leaves the source in place;
  links without a direct app handler continue in Candy without a misleading prompt. Explicit
  user-invoked **Open in app** actions remain already confirmed. Apply the same navigation policy
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
- Route documented HTTPS `play.google.com/store/` links directly to `com.android.vending` without
  generic app-link resolution flags. If Google Play is unavailable or rejects the launch, let the
  originating engine session continue the normalized HTTPS request as Candy's browser fallback.
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
  `GeckoViewInsetRules` forwards all native safe areas to CSS, including the top edge, without
  native margins. Normal Gecko tabs and Link Peek disable the legacy document repair at the
  Gecko-only bridge before installing any of its observers or hooks. Gecko's separate bounded CSS
  layer stays inactive when the document declares `viewport-fit=cover`; Gecko remains the sole owner
  of `env(safe-area-inset-*)`, and Candy does not add body or positioned-element offsets that could
  distort the page's full-height or IME scroll geometry. Other documents classify suitable body flow
  and viewport-bound top anchors, then add the inset to their original top positions once. It does not
  repeatedly measure correctly protected headers while scrolling.
  Only authorized relevant mutations and configured resize/configuration changes reclassify.
  Unknown layouts retain verified emergency native top fallback rather than speculative CSS changes.
  Fullscreen and Compose safe-drawing hosts retain their duplicate-inset exclusions.
  System WebView retains shared document repair: Candy owns its status-bar and cutout top edge because a
  `viewport-fit=cover` declaration does not guarantee use of `env(safe-area-inset-top)`.
  The document-start compatibility inset protects normal flow and top-positioned content.
  Top-anchored fixed, sticky, absolute, and focused containers are shifted once into the safe area.
  Stable viewport-sticky headers use an inherited CSS `max(originalTop, topInset)` anchor, including
  an owned inline top override inside open Shadow DOM. They need no style/rectangle reads or CSS
  rewrites during scrolling. Semantic author/ancestor changes explicitly revalidate and restore
  author inline values and priorities; unrelated feed additions do not rewrite these anchors.
  A passive animation-frame-bounded scroll check refreshes a separate set of already-owned nested or moving
  sticky headers while scrolling. CSS-owned headers do not enter that iteration. An empty JS set schedules
  no known-header frame except for a synchronous scroll during an owned DOM write, which can precede
  registration of a new JS anchor. Candidate discovery and broader layout recovery wait until
  both scrolling and relevant DOM changes have been quiet for the configured interval. Each scroll
  replaces the pending recovery execution identity; no old geometry resumes. Only the dirty cause,
  discovery authorization and latest DOM deadline survive. One recovery request replaces redundant
  scroll-quiet protection. Cancelled callbacks, including already-queued timers and frames, reject
  their old identity. Independent emergency verification survives a skipped or throwing recovery
  pass, reopening discovery only if that pass did not complete; a successful clean result does not
  force a redundant verification scan. It cannot override a newer DOM request, scroll, root or policy.
  Newly added subtrees have a separate bounded, style-first frame path so fixed controls inside
  offscreen wrappers do not wait through continuous scrolling. That path visits only added jobs,
  never drains a large attribute backlog or declares global protection complete. Reinserted
  attribute jobs become added-eligible without rewinding their identity cursor. Scroll replaces
  execution state but keeps a queued wakeup, which resolves the latest state with fresh reads;
  interrupted candidates retain only identity for retry. Reconfiguration/disposal invalidate the
  wakeup. Disabled insets and exceptions cannot spin an unconsumed backlog through frames. Owned
  offsets survive temporary hide/show, but
  are cleared when a visible element returns to normal flow. Persistent layout conflicts suspend
  timer retries until a later DOM change or user interaction resumes recovery before switching only
  the top edge into a navigation-scoped native fallback margin. The explicit
  per-site **Force safe area** override still moves every edge into native safe-area margins.
  Fullscreen keeps the renderer edge to edge.
  GeckoView keeps its default SurfaceView backend when no backdrop capture is needed, so frames
  reach Android's compositor directly. Frosted chrome with non-zero blur and transparency switches
  the renderer to TextureView for live page capture; turning blur off restores SurfaceView.
  PiP, clipping and tab motion preserve the same browser host, GeckoView, surface, display and
  session. The static status-bar overlay remains outside the renderer and keeps system icons legible.
- System WebView's shared safe-area read caches, including Light-/Shadow-DOM parent paths and null parents, are scoped to
  one synchronous layout-read epoch and invalidated before and after actual Candy writes. Post-write
  invalidation also clears reads made by synchronous author/custom-element reactions, even when a
  setter throws. Collections are allocated only on their first actual read, not on invalidation;
  getters return their local result and publish only into the unchanged epoch/collection. Reentrant
  reads cannot overwrite a newer cache, including the first viewport read. No parent path survives a task or yield.
  The full synchronous mutation callback shares this lazy epoch across record classification and
  immediate repair; unrelated classification alone reads no viewport geometry. Stable reconciliations
  reuse known candidates, while quiet scroll, relevant DOM
  changes, interaction/resize and startup stabilization reopen discovery. Owned CSS/attributes are
  changed only when necessary; JS sticky anchors stay sequential to preserve nested scrollport geometry.
  Sticky revalidation clears ownership and reads fresh position before resolving author top; headers
  that stopped being sticky release their ownership without resolving top.
  Never skip
  an unknown subtree based only on its wrapper rectangle: a fixed child can lie in the protected top
  strip even when its parent is offscreen.
- A leaf addition outside the top strip is not a general CSS safety proof: structural selectors or
  `:has()` can reposition an older element elsewhere. Relevant author mutations therefore reopen
  conservative discovery. Unrelated feed mutations mark all owned layout pending without synchronous
  rectangle/style reads or CSS-sticky rewrites; quiet reconciliation/protection/verification fully
  revalidates it before any positive result. Related attributes, mutations inside an owned subtree,
  stylesheet and meta changes retain immediate fresh repair: an author animation-frame callback can
  change a header after that frame's callback list is fixed. Ancestor feed insertions coalesce into
  an animation-frame repair. This is not a universal same-paint guarantee for structural CSS such as
  `:has()`; mutation inside an author frame can delay that repair until the following frame.
  Focus/input and initial-install protection remain immediate. The repair frame checks
  document/policy identity, reads current insets, and is cancelled on disposal/reconfiguration; pending
  work and exceptions are not safety
  proofs; navigation/policy changes revalidate and disposal clears this volatile state.
  Added/changed subtrees get bounded early candidate registration through alternating Added and
  Attribute lanes. Recent-new/FIFO turns preserve older cursor progress; recurring changes coalesce
  one fresh follow-up instead of repeatedly restarting a large feed. Removed/reparented cursors
  require a fresh pass too. The best-effort global 256-root cap admits the first job of either lane;
  overflow cannot establish CSS safety. Traversal bookkeeping and bounded completion transitions
  share the cooperative time budget. Queue completion does not replace full fresh coverage; dense
  verification yields between fresh read epochs, while known CSS sticky anchors remain read-free
  during scrolling. Cancelled work is not successful verification and never consumes a fallback
  failure confirmation.
  Retain only a rotating grid cursor and seed/raster-turn scheduling hint across cancelled author/scroll epochs, not old layout
  reads or proof coverage. First-row common seeds and trailing-row raster priority reduce late-control
  latency; all points still require fresh verification before success. Root/policy/inset/density and
  viewport changes discard the hint. Sticky discovery shares the portioned dense scan instead of
  running a separate synchronous grid. A just-below-inset row anchors initially unstuck headers;
  its probes delay, never discard, common first-row seeds. Dense progress slots are preserved.
  Registration crosses absolute descendants and Shadow hosts while keeping a fixed descendant as a
  positioning barrier. Stable CSS-sticky registration needs no rectangle; nested/moving repair stays
  sequential. A positive result requires fresh dense and extra-row coverage, not registration alone.
  Initial sticky anchoring reuses existing background hits without extra queries; nested/moving
  anchors retain sequential geometry. Cancellation of queued discovery cannot undo that anchor. Hidden positioned elements
  skip rectangle reads. Task-local viewport dimensions are read lazily; style-only mutation checks
  request none. Reconcile captures dimensions before owned writes and reuses them through those
  writes only; every new task that needs dimensions reads fresh values. Style/element/hit-test caches still invalidate
  on actual writes and never survive yields.
  Offset planning resolves CSS height only for tall fixed panels that need box-extras calculations;
  ordinary headers and controls use their already-read rectangle without that additional CSSOM query.
  Packet validation is budgeted but a slow atomic viewport read still permits one discovery point,
  preventing zero-progress rescheduling. Four milliseconds remains a cooperative, not hard, limit.
  Early JS protection accepts only an exact identity transform on the fixed candidate itself;
  ancestor transforms and author motion remain conservative. It does not relax CSS sticky ownership
  or remove author transforms/stacking contexts.
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

## Gecko CSS safe-area controls

### Current Candy Edge prototype

The current experimental host manifest selects `content_safe_area_prototype.js` instead of the
larger `content_safe_area.js` classifier. The latter remains available in source; both must not run
together. This prototype is not a compatibility claim for the layouts described below.

| Rule | Prototype behavior |
| --- | --- |
| Inset source | Existing native policy inset divided by device-pixel ratio, exposed as `--candy-safe-area-inset-top` |
| `viewport-fit=cover` | Skip the complete Candy CSS layer, including body, fixed/sticky, known-site and Reddit rules; keep Gecko's native renderer safe-area delivery |
| Normal page flow | A per-document stylesheet raises body top padding to at least the inset; larger initial padding is preserved |
| Fixed / sticky | Bounded per-element stylesheet rules apply `originalTop + inset` to every discovered finite resolved CSS-pixel top, without an upper threshold; no positioned-element padding or inline top is added |
| Predeclared selectors | Initial and event-driven CSS-source scans protect full selectors with literal `fixed`/`sticky` and a finite pixel `top` in the same CSS declaration block, even before any element matches that state |
| Selector ownership | Elements matching a protected selector do not receive a second element-level top addition; body padding and unmatched element protection remain separate |
| Retained anchors | Existing rule identities are checked before reading computed style; normal author inline resets do not remove the rule or add another inset |
| Other top values | Literal `auto` and unresolved values are not changed; all finite resolved CSS-pixel values, including negative and above-inset tops, are included |
| Initial discovery | Protect the first available body without waiting for the worker; one bounded body traversal plus a single semantic seed when the DOM becomes interactive, without waiting for all subresources; first `header` preferred, `nav` then `[role="banner"]` used only as fallbacks (at most three fixed queries); at most eight shallow header/ancestor checks are reserved from the initial traversal cap |
| Later discovery | DOM subtrees retain trusted click/drop gates; newly loaded links, style insertion/text changes and source attributes use a separate CSS queue without an interaction requirement |
| Scroll | Cancels pending work; does not start style/geometry reads or repair |
| Settings | Existing enable, DOM mutation/interaction, batch and resize controls remain; CSS sources reuse worker batch/time limits with fixed prototype source limits; only post-load sources use the 500-ms cooldown |
| Native / privacy | Full-window renderer, native inset delivery, existing fallback bridge and private-session boundaries remain unchanged |
| Reddit component exception | `content_safe_area_reddit.js` supplies scoped app-flow/header rules in the document and observed open component roots; scroll-state attributes are matched by CSS, not JavaScript repair |

This iteration tests approach A: persistent author-origin CSS, not periodic mutation repair. Each
document owns separate element and selector stylesheets and bounded element markers. Rules persist while that document and
their matching elements remain; disable/configuration changes remove the prototype's rules and
markers without restoring over the page's newer inline top or padding. Existing discovery gates
remain: an unrelated replacement element is not automatically protected merely because its
predecessor was protected. No 500-ms background DOM scan is introduced.

The selector experiment protects predeclared class-driven states: when scrolling adds a persistent
header class, Gecko applies the matching CSS rule without a new Candy style/geometry measurement.
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

For google.com/google.de and their subdomains, the observed absolute compact-menu container
`:root #navd` and expanded-search state `:root #tsf .A7Yvie.emcav` receive the same early important
zero-top-plus-inset rule. The menu rule moves its hamburger below the status bar without shifting
unrelated page flow. The stylesheet also exists before a later focus changes the search container
from static to fixed; browser selector matching supplies protection without a delayed Candy repair.
The rules reserve slots and skip duplicate element protection, using the same
enable/inset/cleanup lifecycle. Other Google layout variants are not inferred.

For reddit.com and its subdomains, `content_safe_area_reddit.js` owns one stylesheet per relevant
scope: document rules are restricted to `shreddit-app`; open app roots receive local rules and
open `reddit-header-small` roots receive host-relative rules. The app gets its author
`--page-y-padding` plus the inset as top padding, rather than adding the inset later at
`.main-container`. Fixed `reddit-header-small` gets inset top; the `.relative` variant subtracts
the author page-padding reserve from its top offset. Its internal `header` gets inset top padding
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

Removing the top threshold deliberately widens this experimental rule: a lower or bottom-anchored
fixed box can also move if CSSOM resolves its top into pixels. This is not a universal layout-safety
proof, and the existing bounded discovery cap still applies.

Known limitations are intentionally left for manual testing: iframe contents, absolute descendants,
nested positioning/scrolling containers, full-height fixed panels, larger DOMs beyond the traversal
cap, and unsupported stylesheet changes affecting elements outside the admitted subtree. The prototype does not
run the old classifier's footprint verification or automatically infer when emergency fallback is
needed. Existing explicit/native fallback paths remain available; no new native top margin is added.
Site-specific exceptions are not part of this first prototype.

Manual feedback on the previous clamp-based prototype (2026-09-14):

| Sites / issue | Feedback / verification |
| --- | --- |
| Google, Wikipedia, GitHub, CNN, Hackernews, Reddit, eBay, Kleinanzeigen, taptap.io, amazon.de | No errors reported in the tested states; not a complete state-matrix acceptance claim |
| Vinted | Sticky `top: 0` header can still be missed at initial load: host 1.5.25 passed one dedicated API 35 Release swipe, but the broader-rule host 1.5.26 missed the header on its tested load. Arithmetic and focused Gecko tests pass; real-site initial-load reliability and exact cause remain unresolved |
| Load timing | Strong flicker from post-load CSS changes; explicitly deferred until after this header-rule fix |

### Existing classifier and controls

Developer options contain a separate **Gecko edge-to-edge** section. Changes are normalized,
stored as global configuration without page/private state, and pushed to live session policies.
They do not change Android renderer margins, the native CSS inset contract, System WebView repair,
autoplay or live blur. Disabling the layer restores still-owned inline CSS; author overrides win.
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
before the retained emergency fallback. No scroll event starts a new discovery or validation pass.

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
| Pull to refresh | `BrowserPullGestureRulesTest`, `BrowserPullToRefreshRulesTest`, and `BrowserPullToRefreshLayoutInstrumentedTest` on an API 34+ emulator |
| Android web-content fullscreen chrome | `FullscreenVideoRulesTest` plus `FullscreenVideoChromeInstrumentedTest` in the Full and System WebView builds on a dedicated API 34+ emulator |
| Edge-to-edge window, safe web viewport, focused search, and representative site layouts | `SystemWebViewEdgeToEdgeInstrumentedTest` and `GeckoEdgeToEdgeInstrumentedTest` run deterministic layout profiles derived from YouTube, Google, ESPN, NYTimes, CNN, Reddit, Facebook, IKEA, GitHub, Discord, Instagram, TapTap, Vimeo, Wikipedia, Stack Overflow, and DuckDuckGo on API 34+; the TapTap profile asserts safety immediately in the scroll task so delayed post-scroll repair cannot mask a jumping sticky header; live sites remain manual/nightly smoke targets rather than merge gates |
| Gecko media, fullscreen and PiP policy | `GeckoMediaRulesTest`, `FullscreenVideoRulesTest`, `GeckoBrowserEngineAdapterTest` and `GeckoPictureInPictureInstrumentedTest` on a dedicated API 34+ emulator |
| Android intent routing | `IncomingBrowserIntentInstrumentedTest`, `ExternalAppLauncherInstrumentedTest`, and `MainActivityIncomingNavigationInstrumentedTest` for cold/warm incoming links, initial redirects, and subsequent tapped handoffs |
| Distribution and TLS channels | `./gradlew testFullDebugUnitTest testFossDebugUnitTest testFullUserCaDebugUnitTest assembleFullDebug assembleFossDebug assembleFullUserCaDebug`, then `python3 scripts/test_network_security_apks.py` |
