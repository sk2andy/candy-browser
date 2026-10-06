# Tab lifecycle and persistence

## Model and policy

| Concern | Source | Current invariant |
| --- | --- | --- |
| Tab model | [`BrowserTab.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserTab.kt) | Open tab records have no product count limit; runtime fields stay on immutable copies |
| Live Gecko sessions | `BrowserSessionResidencyRules`, `BrowserController` | Keep 10 recently used tabs loaded by default; user limit is 1–20 and applies globally across profiles. Eligible unselected tabs become idle after 3 minutes by default; developer options allow 1–60 minutes. A 30-second foreground sweep uses monotonic time, starting idle age when selection leaves the tab, and conservative native form/input checks. Background eviction instead uses the authenticated trusted-input probe. |
| Background memory | `BrowserBackgroundMemoryRules`, `BrowserController`, `MainActivity` | Apply the session budget immediately on the full process-background lifecycle transition and drop reconstructible UI images. Retain a configurable number of unselected warm sessions (default 0, range 0–20). The selected session does not consume this quota and is never evicted by this policy. Keep private tabs, playing/captured background media, PiP and pending file/permission/login flows. Gecko requires an authenticated all-frame trusted-input check reporting no user interaction; native programmatic/default form state alone does not protect a background session. Unknown checks receive at most three bounded attempts and still retain the session if unresolved. Foregrounding cancels checks/retries and reloads UI images; evicted tabs restore only when used. |
| Resident Gecko document presentation | `GeckoContentPresentationGate` | Returning to an already-painted document waits for the replacement surface to composite. Paint reset may precede or follow surface loss; only navigation or close clears the document paint history. Preview handoff and resume frames remain memory-only, including private tabs. |
| Debug session diagnostics | `BrowserSessionDiagnostics` | Debug builds expose opt-in `CandySessions` verbose logcat events for creation, selection, navigation, lifecycle and eviction causes. Enable with `adb -s SERIAL shell setprop log.tag.CandySessions VERBOSE`; disable with `SILENT`. Events contain object identities, booleans and resident counts only, and are suppressed while any private tab exists. A selected existing session without a new navigation distinguishes a warm renderer handoff from a page reload. |
| Pin/order | `TabPinningRules`, `TabReorderingRules`, `TabAutoSortingRules` | Pins stay before regular tabs. Optional automatic sorting orders each group by last access, oldest first and newest last, and disables manual reordering. |
| Create/duplicate selected page | `BrowserController` | The main ⋮ menu duplicates the selected loaded page into a selected, unpinned tab in the same profile and privacy mode. Only the URL is copied; JavaScript, form state, engine session history, Candy Trail and Stack membership remain independent. Existing stale-tab pruning still applies. |
| Delete/close duplicate tabs/bulk close | `TabDeletionRules`, `TabDuplicateRules` | Policy chooses valid targets before controller side effects. “Close all tabs” applies only to the active profile and keeps pinned tabs open; a blank replacement is created when needed. |
| Manual tab-close undo | `ClosedTabUndoRules`, `BrowserController`, `ClosedTabUndoSnackbarEffect` | The opt-in Tabs & gestures switch offers Undo for three seconds after an explicit local tab close, including gestures, address actions, root Back and hardware shortcuts. Only the latest close is recoverable. Restore preserves identity, original position subject to pins-first ordering, eligible Stack membership, preview and Trail; regular engine state is retained only until expiry. An untouched, unpinned matching blank replacement is removed, while a later user selection is preserved. Renderer state is rebuilt; private tabs reload their memory-only URL. Automatic, bulk, synced-runtime and transient popup closures do not create an offer. Profile switches/creation, backgrounding, clearing browsing data, disabling the setting and controller destruction clear the offer. |
| Retention | `TabRetentionRules`, `InactiveTabLifetime` | Timed retention never expires selected/protected or non-deletable tabs. `Immediately` closes the complete tab session, including pinned tabs, whenever Candy enters the background. `WhenAppCloses` keeps tabs across app switches and closes them when Candy's task finishes or is removed from Android Recents. An active federated-login popup remains protected, and a fresh blank tab replaces the cleared active profile session. |
| Overview mode | `TabOverviewMode` and `ui/TabOverview*Rules` | Cover flow uses an Android-switcher-like card at roughly 74% of screen width and 0.45 aspect, with the favicon and title overlaid at top-left; grid and list share the same controller tab state; both compact modes can open at the newest tabs and anchor short content at the bottom, with incomplete grid rows aligned to keep the newest tab bottom-right; the overview locks the activity to portrait until it closes |
| Candy Stacks | Shared `TabStack` and `TabStackRules`; Android `BrowserController`, `BrowserSessionStore`, and `TabStackUi` adapters | A stack contains at least two tabs with the same profile, privacy mode, and pin state. Shared rules own deterministic membership, collapse, preview, visibility, and persistence-boundary decisions; native adapters own storage and UI effects. Coverflow and Grid show an inline marker on every expanded member. The marker animates every visible member behind the tapped trigger tab and collapses them into a layered card at that position; tapping that card opens a separately configurable Coverflow, Grid, or List member folder. The chosen preview tab supplies collapsed content independently from the trigger anchor. Main List remains flat, and tab reordering is disabled while a stack is collapsed. |

## Persistence

| State | Storage | Rule |
| --- | --- | --- |
| Tabs and selection | [`BrowserSessionStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/BrowserSessionStore.kt) | Exclude incognito and live federated-login popup tabs; fall back to most recently accessed persistent tab. A synchronous pending-close marker lets a fresh task finish `WhenAppCloses` cleanup after process death, while restored tasks keep their tabs. |
| Profile wallpapers | `BrowserSessionStore`, `ProfileWallpaperStore` | Persist separate bounded crop/zoom metadata and Candy-owned, size-bounded image files for the new-tab and tab-switcher slots. Keep the active new-tab image in memory and load the switcher image only while its overview is used. |
| Biometric profile protection | `BrowserSessionStore`, `ProfileProtectionSession`, `BrowserController` | Persist only each local profile's bounded lock policy. Successful unlock state and monotonic background timing remain process-memory only. Full app background uses `ProcessLifecycleOwner`, so internal Candy activities and configuration changes do not consume cooldown. Every new process starts protected profiles locked. No separate private-tab policy is added; synced profiles never receive this policy. |
| Overview ordering preferences | `BrowserSessionStore` / iOS `UserDefaults` adapter | Persist compact-overview bottom anchoring on both platforms and automatic recent-use sorting on Android; both default off |
| Tab-close undo preference | `BrowserSessionStore` | Android persists only the opt-in boolean, default off. Closed-tab tokens, private URLs and private Trails are never persisted. |
| Startup home preference | `BrowserSessionStore` | Defaults off; regular launcher opens can add and select a blank tab without discarding restored tabs |
| Overview presentation preferences | `BrowserSessionStore` | Persist normal overview and Stack-folder modes independently. Normal overview defaults to Coverflow; Stack folders default to Grid. |
| Candy Stack metadata | Shared `TabStackRules` policy with Android `BrowserSessionStore` adapter | Persist validated regular-tab stacks with bounded names, stable colors, member IDs, chosen preview ID, collapse-trigger anchor ID, and collapsed state. Missing or stale preview and anchor IDs fall back to the first valid member. Private or mixed-private stacks are rejected at the storage boundary and remain memory-only. Missing members and stacks reduced below two tabs are discarded. |
| History and favorites | `BrowsingHistoryRepository`, `BrowserSessionStore`, `BrowsingLibrary`, `BrowsingFavoritesRules` | Keep local, bounded, canonicalized records. History belongs to a regular profile and can be viewed across a user-selected profile set. Favorites stay global, searchable from the browser menu and individually removable with revision-guarded Snackbar undo. |
| Gecko session state | `GeckoSessionStateStore` plus `GeckoSessionStateSnapshotRules` | Regular same-device tabs persist Gecko's opaque native `SessionState` separately from Candy tab summaries. Restore requires same tab ID, stable profile context ID and supported snapshot version; corrupt, stale, cross-profile and private snapshots are rejected and URL fallback remains available. |
| Gecko Privacy bootstrap history and recovery | `GeckoBootstrapHistoryRules`, `GeckoBootstrapRecoveryRules`, `GeckoPrivacyHostRuntime`, `GeckoViewRuntimeHandle`, `BrowserController` | The first app-initiated page replaces the internal binding document. Native popups authenticate their extension tab identity through a denied extension update request that never navigates; their original request, POST data, referrer and opener remain intact. HTTP requests wait for the authenticated policy binding. Valid HTTP(S) targets promote pending popups even for IP literals or localhost without a canonical filter domain. Back/Forward traversal skips legacy bootstrap entries using original native indices. Gecko rejects saving and restoring snapshots containing a bootstrap anywhere in history and falls back to the persisted tab URL without filtering native history indices. A persisted bootstrap address is reset to a usable blank tab; the tab's current Candy Trail web URL recovers its page when available. |
| Tab preview images | `TabPreviewCaptureRules`, `TabPreviewStore`, `TabPreviewRepository`, `BrowserController` | Capture visible content at native viewport width up to 1,280 px, scale both axes together within 3 million pixels and 4,096 px height, and persist lossless WebP. Ordinary overview images decode proportionally within 480 × 1,440 px (at most 2,764,800 ARGB bytes); only the current restoration handoff loads one sharper image. Owner identity, tab URL, active profile/access and preview epoch reject stale decodes. Release its reference on handoff completion, selection/profile changes, locking, background trim, data clearing and destruction. Full-resolution capture writes own at most two pending images and recycle each after writing or queue rejection. Private/ephemeral pages never enter this image store; old 480 px files stay small until recaptured. |
| Deletion side data | Controller + repositories | Remove preview, favicon, Gecko session state and trail consistently |
| Fullscreen video session | Memory only | Protect the owning regular tab's Gecko session while its custom view is expanded, floating or in system PiP; never restore the session or mini-player position |

## Engine session residency

- Tab records, previews, favicons and Candy Trails remain available in the tab overview when a
  Gecko session is evicted.
- Selecting or otherwise using a resident Gecko session makes it most recently used. When the configured
  limit is exceeded, the least-recently-used eligible Gecko session is persisted and destroyed.
- The selected tab, active media/PiP owners, pending permission/file flows, preview captures and
  managed popup transitions are protected. The limit may be exceeded temporarily while they remain
  protected.
- Foreground idle eviction retains its conservative native form/input checks. Background eviction requires only the authenticated trusted-input check for Gecko; native form defaults or programmatic values can be persisted without retaining the full live page. Both policies preserve private/media/prompt protections. Selection, navigation, document changes, memory-setting changes and lifecycle transitions invalidate stale checks. Unknown results retain the session. Each request has its own attempt identity and timeout, so late replies cannot consume a newer attempt on the same session.
- Regular Gecko tabs restore their persisted Gecko session history on demand when tab/profile/version
  validation succeeds. A newer explicit navigation waits for an accepted native restore to publish its
  history before loading, so a late restore cannot replace the newer page. A bounded restore wait cancels
  a stuck native restore before the newer page starts. Private tabs never write Gecko or Gecko session
  state to disk and therefore reload only their current in-memory URL after eviction.
- Federated-login popup tabs keep their live Gecko session across normal background/foreground transitions
  but never write tab summaries, Gecko session state, previews, History, Recall, or Candy Trails. If the
  process dies, only their persistent opener is restored.

## Gecko history cache and developer controls

| Control | Default | Range | Application |
| --- | --- | --- | --- |
| Foreground resident tab count | 10 | 1–20 | Existing Tabs & gestures preference; global across profiles |
| Foreground idle timeout | 3 minutes | 1–60 minutes | Developer options; live, 30-second sweep |
| Unselected background warm tabs | 0 | 0–20 | Developer options; live; selected tab excluded, protected sessions may exceed quota |
| Cached history-page lifetime | 5 minutes | 1–60 minutes | Developer options; next full process start; Gecko only |

- Gecko runtime settings select `STRATEGY_ISOLATE_HIGH_VALUE`. Sensitive origins retain Gecko's
  isolation policy; ordinary origins may share a process. Process count is not resident-tab count.
- Gecko's multi-page BFCache stays enabled and retains its native memory-dependent global count.
  The developer lifetime writes only `browser.sessionhistory.contentViewerTimeout` into a small
  atomic, no-backup startup configuration through `GeckoRuntimeSettings.configFilePath`.
  This configuration contains no URLs, tab IDs or session state. A write failure logs and keeps
  native defaults rather than preventing startup.
- Gecko 157's history tracker expires after three generations, each lasting half the preference.
  `GeckoHistoryCacheRules` scales the chosen lifetime by two thirds, so the nominal idle expiry
  falls between two thirds and the chosen lifetime. Memory pressure can release a page earlier;
  Android process suspension can delay timer delivery. The currently displayed document is not
  a cached history viewer and remains loaded.
- Exactly five cached pages per tab and eviction of only the newly-forward page on Back are not
  implemented. Gecko's configurable viewer count is global, its per-history viewer window is
  compiled into the engine, and individual viewer eviction is not exposed by GeckoView.
  Navigation history is preserved.

Engine basis: [Gecko 157 history tracker and viewer window](https://github.com/mozilla-firefox/firefox/blob/FIREFOX_157_0_RELEASE/docshell/shistory/nsSHistory.h),
[global viewer policy](https://github.com/mozilla-firefox/firefox/blob/FIREFOX_157_0_RELEASE/docshell/shistory/nsSHistory.cpp),
and [scriptable history interface](https://github.com/mozilla-firefox/firefox/blob/FIREFOX_157_0_RELEASE/docshell/shistory/nsISHistory.idl).

## Background memory

| Resource | Background behavior | Resume behavior |
| --- | --- | --- |
| Gecko visibility and audio | Hidden sessions become inactive; media suspension remains disabled. Visible or entering PiP keeps its owner active. Playback owners remain protected from session eviction. | The selected visible session becomes active again; no media pause command is introduced. |
| Gecko renderer view | Activity stop and full app background release ordinary GeckoView bindings, including their Activity/prompt ownership, while retaining the selected GeckoSession. A stopped-binding gate rejects synchronous Compose reattachment during release. Media-presentation and visible/entering PiP views remain protected. | Activity start permits attachment again and invalidates the observable engine-view revision. A fresh GeckoView binds the same retained session; the document, JavaScript state and history stay live without reload. |
| Gecko process priority | The selected session receives `PRIORITY_HIGH` independently of renderer visibility, so backgrounding alone does not demote it. On deselection, a negative form check restores `PRIORITY_DEFAULT`; positive or unknown form state retains high priority for at most three minutes. Selection, navigation and close invalidate stale asynchronous results. Priority is an engine eviction hint, separate from Candy's resident-tab budget and input protections. | Reselection cancels the old expiry and restores high priority immediately. Native `setActive` is reasserted even when Candy's cached visibility is unchanged, because GeckoView surface attachment can change native activity independently. |
| Tab previews and favicons | Drop regular decoded images and the single sharp restoration preview; invalidate pending loads/captures. Preserve private in-memory images and all stored regular image files. | Reload persisted regular thumbnails under fresh epochs. Decode a sharper image only for the current restoration handoff. |
| Profile wallpaper and favorite icons | Drop decoded images and invalidate pending loads. Complete pending overview-opening callbacks once so trimming cannot leave the overview blocked. | Reload the current profile/favorite images, including a requested switcher wallpaper. |
| Gecko resume cover | Clear the decoded cover and invalidate an in-flight capture without closing the page or changing content-presentation state. | The existing compositor presents its retained document. |
| Background Gecko sessions | Start eviction immediately after full app background and view release. Snapshot and close eligible inactive regular sessions above the unselected warm-tab quota. Require an authenticated all-frame probe to report no trusted user input; native form defaults alone do not block eviction. The document-local flag never reads input values. Unknown results/timeouts get at most three attempts, with a 2-second deadline per attempt and 250 ms between attempts; unresolved, changed, missing or unsupported documents stay resident. Every reply rechecks attempt, lifecycle, session, document and current protection identity. | Selected/protected sessions remain live. Evicted tabs remain unloaded on foreground return and restore saved history/state on selection/use. Pages reconstruct DOM and JavaScript; arbitrary live JS state is lost. |
| System WebView sessions | UI images use the same trimming path. The engine currently has no authoritative form-state query, so unknown form state retains its sessions. | Live documents resume through the existing platform adapter. |

The renderer release and priority split follow Firefox 157's
[engine-view presenter stop](https://github.com/mozilla-firefox/firefox/blob/FIREFOX_157_0_RELEASE/mobile/android/android-components/components/feature/session/src/main/java/mozilla/components/feature/session/engine/EngineViewPresenter.kt#L53-L57)
and [session-prioritization middleware](https://github.com/mozilla-firefox/firefox/blob/FIREFOX_157_0_RELEASE/mobile/android/android-components/components/browser/state/src/main/java/mozilla/components/browser/state/engine/middleware/SessionPrioritizationMiddleware.kt).
Candy retains its own configurable residency budgets and conservative input checks.
Priority hints do not flush caches or establish a measured memory reduction.

The input probe has a separate all-frame registration with
[`match_origin_as_fallback`](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/content_scripts),
supported since Firefox 128. HTTP/HTTPS-owned `srcdoc`, `blob` and other inherited-origin
documents can answer the same document-bound query; other privacy scripts retain their
existing HTTP/HTTPS scope. The probe still requires document-start registration before
reporting no input. Gecko injects late into empty `about:blank` frames, so those frames
remain unknown and keep their session resident. A trusted input event still protects
their document even after late registration. Unreachable frames and changed nonces
also remain unknown.

Gecko's native `containsFormData()` reports saveable form state, including state a
page supplied without a user edit. Real Gecko 157 tests on API 37 distinguish these
signals. Foreground idle protection still uses the combined check; background
eviction uses the trusted-input probe directly.

| Untouched fixture | Trusted-input probe used in background | Native form check | Combined foreground check |
| --- | --- | --- | --- |
| Input-free control page | `false` | `false` | `false` |
| `select` with its second option selected in markup | `false` | `true` | `true` |
| Text field assigned by page JavaScript | `false` | `true` | `true` |

The former background grace control is retired. Older stored grace values are ignored.
A 30-second Handler in a cached Android process cannot guarantee execution before
[Android's cached-app freezer](https://source.android.com/docs/core/perf/cached-apps-freezer).
Immediate checks and bounded retries reduce this timing risk but do not guarantee
completion if Android suspends execution early. No service or alarm keeps the app awake.

App logs, when enabled and outside private browsing, record only budget-applied,
session-unloaded, trusted-input-protected and unresolved-input event names with
timestamps. They contain no tab IDs, URLs or input values.

Verification: `BrowserBackgroundMemoryRulesTest`, `BrowserForegroundMemoryRulesTest`, `BrowserMemorySettingsTest`, `BrowserControllerBackgroundMemoryInstrumentedTest`,
`GeckoBackgroundMemoryInstrumentedTest`, `GeckoBackgroundMemoryFramesInstrumentedTest`,
`GeckoBackgroundRestoreInstrumentedTest`,
`GeckoUserInputRequestInstrumentedTest`,
`GeckoResumeCoverMemoryInstrumentedTest`, `BrowserPageResumeInstrumentedTest`,
`GeckoTabInteractionInstrumentedTest`, `GeckoSessionPriorityControllerTest`,
`GeckoMedia3ActivityE2eInstrumentedTest`, and `scripts/gecko_user_input.test.mjs`.

## Memory-control verification (2026-10-04)

| Check | Result |
| --- | --- |
| Full/Foss JVM | 1,848 tests per flavor, no failures |
| Full/Foss lint and assembly | Passed |
| Controller memory/idle contracts on API 37 | 24 passed |
| Native input protection, cache startup configuration, isolation, long page resume, developer UI and bounded store settings on API 37 | 36 distinct cases passed; native form cases rerun after correcting the synthetic tap to hold DOWN for 50 ms, matching existing Gecko fixture gestures |
| All 28 locales | Unique resource keys and format-placeholder parity passed |

These checks establish lifecycle behavior and settings wiring, not a measured RAM reduction on the
Pixel. Resident-session and Gecko-process counts remain different measurements.

## Mutation checklist

- Compute tab/profile mutations through existing rules before touching Gecko sessions or stores.
- Preserve stable tab IDs across normal restore; reset transient load/error/progress state when reconstructing.
- Apply persistence policy before encoding. Never rely on callers to pre-filter private tabs.
- Keep selection valid after deletion, retention, profile moves and snooze restore.
- When automatic sorting is enabled, derive visible order from `lastAccessedAt`; keep pins grouped
  first and reject manual reorder mutations.
- Keep stack grouping separate from the flat tab order. Creating or extending a stack must keep all
  members in one profile, privacy mode, and pin state. Closing, snoozing, moving, or repinning a
  member reconciles the stack and dissolves it below two members.
- End an owning fullscreen-video session before its tab or Gecko session is removed. Private sessions end
  when selection leaves their tab; regular sessions may remain transiently attached as a mini-player.
- Remove both owned wallpaper files when deleting a profile. A missing or corrupt file clears only
  that slot's stale profile metadata and falls back to the normal surface. Wallpaper never renders
  for private or synced runtime profiles. Legacy single-wallpaper data is atomically copied into
  both slots before its original file is removed.
- Never reassign history when deleting a profile; delete that profile's rows. Private tabs never
  enter history, and address suggestions only consume history for the selected tab's profile.
- Keep biometric protection fail-closed across process death. Do not attach or activate the selected
  engine view while its profile is locked. Hide profile-owned History and Snooze projections, pause
  its media, clear published media metadata, and never describe this access gate as file encryption.
