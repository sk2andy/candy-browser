# Tabs and profiles

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Tab state, ordering, retention, WebView residency, persistence | [`lifecycle-and-persistence.md`](lifecycle-and-persistence.md) | `BrowserTab`, `TabWebViewResidencyRules`, `data/Tab*Rules`, `BrowserSessionStore` |
| Previews, snoozing, profiles, WebView isolation | [`previews-snooze-and-isolation.md`](previews-snooze-and-isolation.md) | `TabPreview*`, `SnoozedTab*`, `WebViewProfileRules` |
| Tab overview modes, hero layout and gestures | Every overview mode reveals the selected tab whenever the overview opens, independent of retained scroll state. Hero and grid previews stay compact in portrait and switch to 16:10 in landscape. Tablet-width grids use three columns; smaller grids use two. Hero cards remain height-bounded with room for neighbors. Grid cards place the favicon/title pill and floating close action over the preview; pinned cards keep the pin in the pill and omit close. Selected grid cards retain a primary outline. Grid neighbors fade with the shared entry progress, while the selected preview keeps one continuous crop through the hero-to-card handoff. The pager draw area extends behind profile chrome, so upward dismiss drags remain visible up to the system-bar boundary; profile chrome stays above that overflow for drawing and touch input. Profile controls remain interactive while the entry hero blocks tab-content touches. Address chrome stays above both entry and exit hero animations. A parked address pill follows the shared position-and-size spring into centered overview chrome and back, without snapping between anchors. | `ui/BrowserScreen.kt`, `ui/TabOverviewHeroRules.kt`, `ui/TabOverviewGridRules.kt`, `ui/TabDismissPhysics.kt` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Pure tab rules | `data/Tab*Test`, `BrowserTabTest`, `TabWebViewResidencyRulesTest` |
| Profiles and storage assignment | `WebViewProfileRulesTest`, `BrowserControllerProfilesInstrumentedTest`, `ProfileCreationFlowInstrumentedTest` |
| Previews | `TabPreview*Test`, `TabPreviewRefreshInstrumentedTest` |
| Snoozing | `Snooze*Test`, `Snooze*InstrumentedTest` |
| Tab overview layout and reorder | `TabOverviewReorderInstrumentedTest` |
