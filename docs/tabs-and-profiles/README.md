# Tabs and profiles

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Tab state, ordering, retention, persistence | [`lifecycle-and-persistence.md`](lifecycle-and-persistence.md) | `BrowserTab`, `data/Tab*Rules`, `BrowserSessionStore` |
| Previews, snoozing, profiles, WebView isolation | [`previews-snooze-and-isolation.md`](previews-snooze-and-isolation.md) | `TabPreview*`, `SnoozedTab*`, `WebViewProfileRules` |
| Tab overview modes, hero layout and gestures | Hero and grid previews stay compact in portrait and switch to 16:10 in landscape. Tablet-width grids use three columns; smaller grids use two. Hero cards remain height-bounded with room for neighbors. The pager draw area extends behind profile chrome, so upward dismiss drags remain visible up to the system-bar boundary. Address chrome stays above both entry and exit hero animations. | `ui/BrowserScreen.kt`, `ui/TabOverviewHeroRules.kt`, `ui/TabOverviewGridRules.kt`, `ui/TabDismissPhysics.kt` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Pure tab rules | `data/Tab*Test`, `BrowserTabTest` |
| Profiles and storage assignment | `WebViewProfileRulesTest`, `BrowserControllerProfilesInstrumentedTest` |
| Previews | `TabPreview*Test`, `TabPreviewRefreshInstrumentedTest` |
| Snoozing | `Snooze*Test`, `Snooze*InstrumentedTest` |
| Tab overview layout and reorder | `TabOverviewReorderInstrumentedTest` |
