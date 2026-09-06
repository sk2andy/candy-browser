# Tab lifecycle and persistence

## Model and policy

| Concern | Source | Current invariant |
| --- | --- | --- |
| Tab model | [`BrowserTab.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserTab.kt) | Maximum 50 open tab records; runtime fields stay on immutable copies |
| Live Gecko sessions | `BrowserSessionResidencyRules`, `BrowserController` | Keep 10 recently used tabs loaded by default; user limit is 1–20 and applies globally across profiles |
| Pin/order | `TabPinningRules`, `TabReorderingRules`, `TabAutoSortingRules` | Pins stay before regular tabs. Optional automatic sorting orders each group by last access, oldest first and newest last, and disables manual reordering. |
| Delete/duplicate/bulk close | `TabDeletionRules`, `TabDuplicateRules` | Policy chooses valid targets before controller side effects. “Close all tabs” applies only to the active profile and keeps pinned tabs open; a blank replacement is created when needed. |
| Retention | `TabRetentionRules`, `InactiveTabLifetime` | Timed retention never expires selected/protected or non-deletable tabs. `Immediately` closes the complete tab session, including pinned tabs, when the app becomes fully hidden, except during configuration changes, picture-in-picture, or an active federated-login popup that must return from an authenticator; a fresh blank tab replaces the cleared active profile session. |
| Overview mode | `TabOverviewMode` and `ui/TabOverview*Rules` | Cover flow uses an Android-switcher-like card at roughly 74% of screen width and 0.45 aspect, with the favicon and title overlaid at top-left; grid and list share the same controller tab state; list mode can open and anchor short content at the bottom; the overview locks the activity to portrait until it closes |

## Persistence

| State | Storage | Rule |
| --- | --- | --- |
| Tabs and selection | [`BrowserSessionStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/BrowserSessionStore.kt) | Exclude incognito and live federated-login popup tabs; fall back to most recently accessed persistent tab |
| Profile wallpapers | `BrowserSessionStore`, `ProfileWallpaperStore` | Persist separate bounded crop/zoom metadata and Candy-owned, size-bounded image files for the new-tab and tab-switcher slots. Keep the active new-tab image in memory and load the switcher image only while its overview is used. |
| Overview ordering preferences | `BrowserSessionStore` | Persist list-bottom anchoring and automatic recent-use sorting; both default off |
| Startup home preference | `BrowserSessionStore` | Defaults off; regular launcher opens can add and select a blank tab without discarding restored tabs |
| History and favorites | `BrowsingHistoryRepository`, `BrowserSessionStore`, `BrowsingLibrary` | Keep local, bounded, canonicalized records; history is owned by a regular profile and can be viewed across a user-selected profile set |
| Gecko session state | `GeckoSessionStateStore` plus `GeckoSessionStateSnapshotRules` | Regular same-device tabs persist Gecko's opaque native `SessionState` separately from Candy tab summaries. Restore requires same tab ID, stable profile context ID and supported snapshot version; corrupt, stale, cross-profile and private snapshots are rejected and URL fallback remains available. |
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
- Regular Gecko tabs restore their persisted Gecko session history on demand when tab/profile/version
  validation succeeds. Private tabs never write Gecko or Gecko session state to disk and therefore reload
  only their current in-memory URL after eviction.
- Federated-login popup tabs keep their live Gecko session across normal background/foreground transitions
  but never write tab summaries, Gecko session state, previews, History, Recall, or Candy Trails. If the
  process dies, only their persistent opener is restored.

## Mutation checklist

- Compute tab/profile mutations through existing rules before touching Gecko sessions or stores.
- Preserve stable tab IDs across normal restore; reset transient load/error/progress state when reconstructing.
- Apply persistence policy before encoding. Never rely on callers to pre-filter private tabs.
- Keep selection valid after deletion, retention, profile moves and snooze restore.
- When automatic sorting is enabled, derive visible order from `lastAccessedAt`; keep pins grouped
  first and reject manual reorder mutations.
- End an owning fullscreen-video session before its tab or Gecko session is removed. Private sessions end
  when selection leaves their tab; regular sessions may remain transiently attached as a mini-player.
- Remove both owned wallpaper files when deleting a profile. A missing or corrupt file clears only
  that slot's stale profile metadata and falls back to the normal surface. Wallpaper never renders
  for private or synced runtime profiles. Legacy single-wallpaper data is atomically copied into
  both slots before its original file is removed.
- Never reassign history when deleting a profile; delete that profile's rows. Private tabs never
  enter history, and address suggestions only consume history for the selected tab's profile.
