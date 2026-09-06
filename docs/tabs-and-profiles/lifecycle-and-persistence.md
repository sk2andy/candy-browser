# Tab lifecycle and persistence

## Model and policy

| Concern | Source | Current invariant |
| --- | --- | --- |
| Tab model | [`BrowserTab.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserTab.kt) | Maximum 50 open tab records; runtime fields stay on immutable copies |
| Live WebViews | `TabWebViewResidencyRules`, `BrowserController` | Keep 10 recently used tabs loaded by default; user limit is 1–20 and applies globally across profiles |
| Pin/order | `TabPinningRules`, `TabReorderingRules`, `TabAutoSortingRules` | Pins stay before regular tabs. Optional automatic sorting orders each group by last access, oldest first and newest last, and disables manual reordering. |
| Delete/duplicate | `TabDeletionRules`, `TabDuplicateRules` | Policy chooses valid targets before controller side effects |
| Retention | `TabRetentionRules`, `InactiveTabLifetime` | Timed retention never expires selected/protected or non-deletable tabs. `Immediately` closes the complete tab session, including pinned tabs, when the app becomes fully hidden, except during configuration changes or picture-in-picture; a fresh blank tab replaces the cleared active profile session. |
| Overview mode | `TabOverviewMode` and `ui/TabOverview*Rules` | Users choose Coverflow, Grid, or List. Missing and unknown values fall back to Coverflow. List can open and anchor short content at the bottom; the overview locks the activity to portrait until it closes. Shared hero rules own Coverflow and entry/exit morphing. |
| Candy Stacks prototype | `TabStack`, `TabStackRules`, `TabStackUi` | A stack contains at least two tabs with the same profile, privacy mode, and pin state. Coverflow and Grid show an inline marker on every expanded member. The marker animates every visible member behind the tapped trigger tab and collapses them into a layered card at that position; tapping that card opens a separately configurable Coverflow, Grid, or List member folder. The chosen preview tab supplies collapsed content independently from the trigger anchor. Main List remains flat, and reordering is disabled while a stack is collapsed. |

## Persistence

| State | Storage | Rule |
| --- | --- | --- |
| Tabs and selection | [`BrowserSessionStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/BrowserSessionStore.kt) | Exclude incognito tabs; fall back to most recently accessed persistent tab |
| Overview ordering preferences | `BrowserSessionStore` | Persist list-bottom anchoring and automatic recent-use sorting; both default off |
| Overview presentation preferences | `BrowserSessionStore` | Persist normal overview and Stack-folder modes independently. Normal overview defaults to Coverflow; Stack folders default to Grid. |
| Candy Stack metadata | `BrowserSessionStore` | Persist validated regular-tab stacks with bounded names, stable colors, member IDs, chosen preview ID, collapse-trigger anchor ID, and collapsed state. Missing or stale preview and anchor IDs fall back to the first valid member. Private or mixed-private stacks are rejected at the storage boundary and remain memory-only. Missing members and stacks reduced below two tabs are discarded. |
| History and favorites | `BrowsingHistoryRepository`, `BrowserSessionStore`, `BrowsingLibrary` | Keep local, bounded, canonicalized records; history is owned by a regular profile and can be viewed across a user-selected profile set |
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
- When automatic sorting is enabled, derive visible order from `lastAccessedAt`; keep pins grouped
  first and reject manual reorder mutations.
- Keep stack grouping separate from the flat tab order. Creating or extending a stack must keep all
  members in one profile, privacy mode, and pin state. Closing, snoozing, moving, or repinning a
  member reconciles the stack and dissolves it below two members.
- End an owning fullscreen-video session before its tab or WebView is removed. Private sessions end
  when selection leaves their tab; regular sessions may remain transiently attached as a mini-player.
- Never reassign history when deleting a profile; delete that profile's rows. Private tabs never
  enter history, and address suggestions only consume history for the selected tab's profile.
