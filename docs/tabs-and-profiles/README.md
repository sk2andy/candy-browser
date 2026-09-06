# Tabs and profiles

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Tab state, ordering, retention, WebView residency, persistence | [`lifecycle-and-persistence.md`](lifecycle-and-persistence.md) | `BrowserTab`, `TabWebViewResidencyRules`, `data/Tab*Rules`, `BrowserSessionStore` |
| Previews, snoozing, profiles, WebView isolation | [`previews-snooze-and-isolation.md`](previews-snooze-and-isolation.md) | `TabPreview*`, `SnoozedTab*`, `WebViewProfileRules` |
| Candy Stacks prototype | Manual profile-bound tab groups with name, color, chosen preview, inline expand/collapse marker, layered collapsed card, trigger-anchored stacking/fanning, and a separately configurable Coverflow, Grid, or List folder view. The tapped marker tab owns the collapsed position while the configured preview independently supplies its cover. During Coverflow folding, a bounded decorative motion layer brings up to six off-screen stack members in from the visible edge without composing every intervening pager page. Coverflow and Grid are stack-aware; List remains flat. | `browser/TabStack`, `data/TabStackRules`, `ui/TabStackUi`, `ui/TabStackMotionRules`, `ui/BrowserScreen` |
| Tab overview modes, ordering, transitions and gestures | Users choose Coverflow, Grid, or List. Every mode reveals the selected tab whenever the overview opens, independent of retained scroll state. List can instead start at the newest end and bottom-align short lists. Optional recent-use sorting keeps pins first, places least recently used tabs before newest tabs, and disables manual reorder. When pins exist outside the viewport, a fading edge affordance beside bottom chrome scrolls back to them. A matching settings affordance opens Settings without closing the overview. Grid previews stay compact in portrait and switch to 16:10 in landscape; tablet widths use three columns and smaller grids use two. Coverflow and Grid render collapsed stacks as layered cards with a marker and folder action. Selected cards retain a primary outline. Shared hero rules own Coverflow plus entry/exit morphing and preview crop handoff. | `ui/BrowserScreen.kt`, `ui/BrowserMainMenu.kt`, `data/TabAutoSortingRules.kt`, `ui/TabOverviewHeroRules.kt`, `ui/TabOverviewGridRules.kt`, `ui/TabDismissPhysics.kt`, `ui/AddressBarMotion.kt` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Pure tab rules | `data/Tab*Test`, `BrowserTabTest`, `TabWebViewResidencyRulesTest` |
| Profiles and storage assignment | `WebViewProfileRulesTest`, `BrowserControllerProfilesInstrumentedTest`, `ProfileCreationFlowInstrumentedTest` |
| Previews | `TabPreview*Test`, `TabPreviewRefreshInstrumentedTest` |
| Snoozing | `Snooze*Test`, `Snooze*InstrumentedTest` |
| Tab overview layout and reorder | `TabOverviewReorderInstrumentedTest` |
| Candy Stacks rules, controller, persistence and UI | `TabStackRulesTest`, `BrowserControllerTabStackInstrumentedTest`, `BrowserSessionStoreInstrumentedTest`, `TabStackUiInstrumentedTest` |
