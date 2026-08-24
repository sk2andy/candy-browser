# Tab lifecycle and persistence

## Model and policy

| Concern | Source | Current invariant |
| --- | --- | --- |
| Tab model | [`BrowserTab.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserTab.kt) | Maximum 50 open tab records; runtime fields stay on immutable copies |
| Live WebViews | `TabWebViewResidencyRules`, `BrowserController` | Keep 10 recently used tabs loaded by default; user limit is 1–20 and applies globally across profiles |
| Pin/order | `TabPinningRules`, `TabReorderingRules` | Pinned ordering is normalized after mutations |
| Delete/duplicate | `TabDeletionRules`, `TabDuplicateRules` | Policy chooses valid targets before controller side effects |
| Retention | `TabRetentionRules`, `InactiveTabLifetime` | Never expire selected/protected or non-deletable tabs |
| Overview mode | `TabOverviewMode` and `ui/TabOverview*Rules` | Cover flow uses an Android-switcher-like card at roughly 74% of screen width and 0.45 aspect, with the favicon and title overlaid at top-left; grid and list share the same controller tab state; the overview locks the activity to portrait until it closes |

## Persistence

| State | Storage | Rule |
| --- | --- | --- |
| Tabs and selection | [`BrowserSessionStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/BrowserSessionStore.kt) | Exclude incognito tabs; fall back to most recently accessed persistent tab |
| History and favorites | `BrowserSessionStore`, `BrowsingLibrary` | Keep local, bounded, canonicalized records |
| WebView history state | `TabWebViewStateStore`/`Repository` | Persist separately from the tab summary and prune orphan files |
| Deletion side data | Controller + repositories | Remove preview, favicon, WebView state and trail consistently |
| Fullscreen video session | Memory only | Protect the owning regular tab's WebView while its custom view is expanded, floating or in system PiP; never restore the session or mini-player position |

## WebView residency

- Tab records, previews, favicons and Candy Trails remain available in the tab overview when a
  WebView is evicted.
- Selecting or otherwise using a resident WebView makes it most recently used. When the configured
  limit is exceeded, the least-recently-used eligible WebView is persisted and destroyed.
- The selected tab, active media/PiP owners, pending permission/file flows, preview captures and
  managed popup transitions are protected. The limit may be exceeded temporarily while they remain
  protected.
- Regular tabs restore their persisted WebView history on demand. Private tabs never write WebView
  state to disk and therefore reload their current URL after eviction.

## Mutation checklist

- Compute tab/profile mutations through existing rules before touching WebViews or stores.
- Preserve stable tab IDs across normal restore; reset transient load/error/progress state when reconstructing.
- Apply persistence policy before encoding. Never rely on callers to pre-filter private tabs.
- Keep selection valid after deletion, retention, profile moves and snooze restore.
- End an owning fullscreen-video session before its tab or WebView is removed. Private sessions end
  when selection leaves their tab; regular sessions may remain transiently attached as a mini-player.
