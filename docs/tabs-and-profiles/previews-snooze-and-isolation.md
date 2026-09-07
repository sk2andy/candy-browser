# Previews, snoozing and isolation

## Previews

| Piece | Responsibility |
| --- | --- |
| `TabPreviewCaptureRules` | Bound capture geometry and reject likely failed PixelCopy results |
| `GeckoPreviewCaptureRules` / `TabSwitchPreviewLayoutRules` | Crop Gecko compositor captures to Candy's visible viewport, then reconstruct the same safe-area top inset and captured height during Hero/Grid/List handoffs. A captured height is immutable for the transition and therefore does not follow the moving address bar. |
| `GeckoContentPresentationGate` | Keep the preview over a GeckoView until Gecko has reported both a real first composite and valid content paint. `onPaintStatusReset` revokes page readiness until the next contentful paint; rebinding a detached surface retains that page paint but still requires a fresh composite. Android `OnDraw` is not treated as Gecko content readiness. |
| `BrowserController` | Own Gecko `capturePixels` timing, reject stale captures by tab/session/navigation generation, and validate the selected renderer binding again before reporting live content to Compose. |
| `TabPreviewRepository` | Serialize preview file I/O on one executor and prune unknown tab IDs |
| `TabPreviewStore` | Validate bitmap dimensions/encoding and bound stored data |
| `AtomicTabFileDirectory` | Share safe UUID filenames, atomic writes, pruning and explicit directory lifecycle with favicon and Gecko-session-state stores |
| iOS `BrowserTabSnapshotStore` | Keep one native `UIImage` per live tab in memory for Hero/Grid/List, invalidate it at navigation start/session replacement/tab close, and reject late `WKWebView` results by tab, session, navigation revision, request identity and current page URL |
| External Link Preview | Own one interactive, controller-managed engine session without registering a tab. Android uses a transient Gecko session and keeps the migration-only WebView runtime as a separate sealed binding. The session is memory-only, foreground-only, uses the selected regular profile's storage boundary, accepts only normalized HTTP(S) navigation unless a bounded external-app grant authorizes a handoff, records no browser history or Candy Trail, and is recreated for every profile change. Promotion destroys the preview and reloads its final normalized URL as one regular tab; a user-driven departure from Candy discards it. |

Hero and Grid use the same shared `TabCardHeroContent` interpolation on entry and exit. Android
supplies platform bitmaps and renderer readiness at the edge; the transition geometry, durations and
card crop stay in shared Compose so iOS and Android do not fork the tab-overview animation.

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
