# Tabs and profiles

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Tab state, ordering, retention, WebView residency, persistence | [`lifecycle-and-persistence.md`](lifecycle-and-persistence.md) | `BrowserTab`, `TabWebViewResidencyRules`, `data/Tab*Rules`, `BrowserSessionStore` |
| Previews, snoozing, profiles, WebView isolation | [`previews-snooze-and-isolation.md`](previews-snooze-and-isolation.md) | `TabPreview*`, `SnoozedTab*`, `WebViewProfileRules` |
| Sync profile binding and writable remote device profiles | [`../sync/app-integration.md`](../sync/app-integration.md) | The current device binds to one persisted local profile; remote runtime projections reuse native WebView interaction but never enter `BrowserSessionStore` |
| Tab overview modes, ordering, hero layout and gestures | By default every overview mode reveals the selected tab whenever the overview opens, independent of retained scroll state. List mode can instead start at the newest end and bottom-align short lists. Optional recent-use sorting keeps pins first, places the least recently used tabs before the newest tabs, and disables manual reorder. When pins exist outside the current viewport, a fading edge affordance beside the bottom chrome scrolls back to them. A matching subtle settings affordance on the opposite edge opens Settings without closing the overview, so Back returns to the switcher. Both edge affordances keep their full 48dp hit targets inside the overview and use circular ripples without button pills. Both tab-action surfaces keep favorite and pin actions side by side. Their menus grow to 400dp where space allows and stay inside 12dp screen margins; the main browser menu wraps favorite and pin into a second toolbar row below 340dp, while the tab-card menu uses compact vertical toolbar content. Hero and grid previews stay compact in portrait and switch to 16:10 in landscape. Tablet-width grids use three columns; smaller grids use two. Hero cards remain height-bounded with room for neighbors. Grid cards place the favicon/title pill and floating close action over the preview; pinned cards keep the pin in the pill and omit close. Selected grid cards retain a primary outline. Grid neighbors fade with the shared entry progress, while the selected preview keeps one continuous crop through the hero-to-card handoff. The pager draw area extends behind profile chrome, so upward dismiss drags remain visible up to the system-bar boundary; profile chrome stays above that overflow for drawing and touch input. Profile controls remain interactive while the entry hero blocks tab-content touches. Address chrome stays above both entry and exit hero animations. A parked address pill follows the shared position-and-size spring into centered overview chrome and back without snapping between anchors. | `ui/BrowserScreen.kt`, `ui/BrowserMainMenu.kt`, `data/TabAutoSortingRules.kt`, `ui/TabOverviewHeroRules.kt`, `ui/TabOverviewGridRules.kt`, `ui/TabDismissPhysics.kt`, `ui/AddressBarMotion.kt` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Pure tab rules | `data/Tab*Test`, `BrowserTabTest`, `TabWebViewResidencyRulesTest` |
| Profiles and storage assignment | `WebViewProfileRulesTest`, `BrowserControllerProfilesInstrumentedTest`, `ProfileCreationFlowInstrumentedTest` |
| Previews | `TabPreview*Test`, `TabPreviewRefreshInstrumentedTest` |
| Snoozing | `Snooze*Test`, `Snooze*InstrumentedTest` |
| Tab overview layout and reorder | `TabOverviewReorderInstrumentedTest` |
