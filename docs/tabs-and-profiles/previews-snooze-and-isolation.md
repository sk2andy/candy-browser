# Previews, snoozing and isolation

## Previews

| Piece | Responsibility |
| --- | --- |
| `TabPreviewCaptureRules` | Capture regular pages at the renderer width, capped at 1,280 px and 3 million output pixels. Never upscale the source. Keep the ordinary decoded switcher cache at 480 px; reject likely failed PixelCopy results. |
| `GeckoPreviewCaptureRules` / `TabSwitchPreviewLayoutRules` | Capture the full visible renderer, including page pixels behind floating address chrome, then reconstruct the same safe-area top inset and captured height during swipe/Hero/Grid/List handoffs. Address-bar bounds never crop a departing page; the chrome is composed separately above it. A captured height is immutable for the transition and therefore does not follow the moving address bar. |
| `GeckoViewRuntimeHandle` | Seed replacement renderer views with their retained session's last pure inset layout before binding the session. Native margins and CSS safe-area ownership therefore do not briefly reset to a new view's defaults during a warm switch. Retain no Activity, view or WindowInsets instance; the controller applies known current insets before attaching the view, then refreshes root insets after attachment for changed window geometry, fullscreen or safe-drawing hosts. |
| `GeckoContentPresentationGate` | Establish engine readiness from a real composite and document paint proof; Android `OnDraw` is not Gecko content readiness. Decorative swipe/overview snapshots normally wait for the gate, but expire after two resumed seconds over an attached, visible, valid renderer surface if readiness delivery stalls. Expiration never marks engine content ready. Each handoff owns a distinct visual identity, so an old timeout cannot remove a replacement. |
| `BrowserController` | Own Gecko `capturePixels` timing, reject stale captures by tab/session/navigation generation, and validate the selected renderer binding again before reporting live content to Compose. |
| `TabHandoff` / `BrowserViewport` | Classify an unloaded regular tab before selecting it. Its fullscreen restoration snapshot starts grayscale and fades into live color over 250 ms; warm handoffs retain color and their 110 ms reveal. The tab switcher remains colored. Load one larger restoration bitmap only while its handoff owns the selected, resumed viewport; release it on completion, backgrounding, replacement or disposal. Owner identity prevents old cleanup from dropping a newer request. Color filtering changes drawing only and never copies or modifies stored bitmaps. |
| `TabPreviewRepository` | Serialize preview file I/O on one executor and prune unknown tab IDs |
| `TabPreviewStore` | Validate bitmap dimensions/encoding and bound stored data |
| `AtomicTabFileDirectory` | Share safe UUID filenames, atomic writes, pruning and explicit directory lifecycle with favicon and Gecko-session-state stores |
| iOS `BrowserTabSnapshotStore` | Keep one native `UIImage` per live tab in memory for Hero/Grid/List, invalidate it at navigation start/session replacement/tab close, and reject late `WKWebView` results by tab, session, navigation revision, request identity and current page URL |
| External Link Preview | Own one interactive, controller-managed engine session without registering a tab. Android uses a transient Gecko session and keeps the migration-only WebView runtime as a separate sealed binding. The session starts loading when its view attaches to the window, including cold launches whose Compose host attaches after its first update. It is memory-only, pauses while Candy is backgrounded, uses the selected regular profile's storage boundary, accepts only normalized HTTP(S) navigation unless a bounded external-app grant authorizes a handoff, records no browser history or Candy Trail, and is recreated for every profile change. Promotion destroys the preview and reloads its final normalized URL as one regular tab. Temporary app switches preserve the preview; an explicit Candy app-icon, widget, or launcher-shortcut launch discards it. |
| Preview profile protection | Selecting a locked preview profile authenticates before creating its engine session or accessing its storage, without switching the active browser profile. Cancellation keeps the original preview. Late results are rejected after another selection, preview replacement, runtime-generation change, or app backgrounding; rejected results never unlock the requested profile. Initial locked-profile previews cannot prepare a runtime. Relocking the preview's target profile destroys and dismisses that preview, including when the active browser profile remains unlocked. |

Hero and Grid use the same shared `TabCardHeroContent` interpolation on entry and exit. Android
supplies platform bitmaps and renderer readiness at the edge; the transition geometry, durations and
card crop stay in shared Compose so iOS and Android do not fork the tab-overview animation.
Android captures the selected tab again before opening the overview, so existing lower-resolution
stored previews are replaced when that tab becomes active. An unloaded tab initially falls back to
its compact cached image while its larger stored snapshot loads. The larger capture keeps the existing
selected-renderer and navigation-generation checks and still skips private and ephemeral tabs;
regular-tab previews alone enter the bounded repository.

The visual timeout starts a bounded reveal after two resumed seconds over a valid visible surface,
using the same 250 ms restoration or 110 ms warm fade. Its timer and bitmap ownership suspend while
the activity is backgrounded. The timeout prefers a live renderer over an indefinitely stale screenshot. Surface validity
does not prove content paint: if Gecko itself stalls, expiration can briefly expose its loading
surface. Native engine readiness and presentation callbacks retain their independent paint checks.
Timeout release schedules a fresh Android root draw so the SurfaceView's transparent region is
updated after the Compose snapshot disappears; removing Compose state alone can retain old pixels.

## Snoozing

| Piece | Responsibility |
| --- | --- |
| `SnoozeTimeRules` | Convert presets/custom local times to wake instants |
| `SnoozeRules` | Permit only future, non-incognito snoozes |
| `SnoozeMutationRules` / `SnoozeUndoRules` / `SnoozeRestoreRules` | Pure reschedule, undo and due-tab restore behavior |
| `BrowserSessionStore.saveTabsAndSnoozedImmediately` | Commit active+snoozed snapshot together and roll back on failure |
| `SnoozeScheduler` / `SnoozeWakeNotifier` | Android alarm and notification edges |

Link Peek can snooze its committed preview URL without first creating an active tab. Confirmation
adds one regular local-profile tab directly to the atomic snoozed snapshot; cancellation leaves no
tab, history or Gecko session state. Private, synced and ephemeral sources cannot persist snoozed links.

## Private-tab notification

Android posts one ongoing, privacy-safe notification while any private tab exists in memory. Its
dedicated `private_tabs` notification channel is independent from snoozed-tab alerts. Tapping the
notification closes every private tab across profiles without opening the activity; no private
title or URL enters notification state. `PrivateTabsNotifier` owns the Android notification edge,
while `BrowserController.closeAllPrivateTabs` owns tab cleanup and regular-tab replacement.

## Profiles and Gecko storage

| Case | Gecko context |
| --- | --- |
| Regular non-isolated tab | Shared default context |
| Regular isolated tab | Stable Candy profile ID |
| Private tab | Stable `private:<profileId>` context regardless of regular-profile isolation; no persisted native snapshot |
| Profile move | Close old session, discard old native snapshot, reopen under the target profile mapping |

- Close affected Gecko sessions before deleting their storage context.
- Never call Gecko's context-wide deletion API for a non-isolated regular profile; its default context is shared.
- Delete an isolated profile's native context only after its sessions are closed. Private contexts remain separate.
- Use Gecko's context-specific deletion API; never clear another profile's storage as a fallback.
- Gecko dispatches context deletion without exposing a completion callback; do not report a verified completion from that API.
- Move/delete tabs and side data as one controller operation; preserve private/non-private boundary.

## Profiles and WebKit storage

| Case | WebKit data store |
| --- | --- |
| Regular non-isolated profile | Shared default persistent store |
| Regular isolated profile | Persistent named store keyed by the stable profile UUID |
| Private or ephemeral tab | Non-persistent store, regardless of profile |

- Profile creation, icon changes and isolation mutations use the shared `BrowserProfileRules` contract.
- The existing profile icon/isolation sheets compile from shared Compose on Android and iOS; platform code supplies
  strings, chrome color and icon rendering only.
- Changing isolation detaches and recreates only sessions owned by that profile. The shared tab records and current
  URLs remain intact and are reloaded into the new storage boundary.
- iOS tab previews are intentionally never persisted; this also preserves the private-tab boundary when private tabs are added to the iOS target.

Android's mapping is centralized in `GeckoProfileStorageRules.contextId` so session creation,
restoration and profile moves cannot drift apart.
