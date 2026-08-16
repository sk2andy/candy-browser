# Tabs and profiles

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Tab state, ordering, retention, persistence | [`lifecycle-and-persistence.md`](lifecycle-and-persistence.md) | `BrowserTab`, `data/Tab*Rules`, `BrowserSessionStore` |
| Previews, snoozing, profiles, WebView isolation | [`previews-snooze-and-isolation.md`](previews-snooze-and-isolation.md) | `TabPreview*`, `SnoozedTab*`, `WebViewProfileRules` |
| Tab overview modes, hero layout and gestures | Hero cards keep their resting geometry while the pager draw area extends behind profile chrome, so upward dismiss drags remain visible up to the system-bar boundary | `ui/BrowserScreen.kt`, `ui/TabDismissPhysics.kt` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Pure tab rules | `data/Tab*Test`, `BrowserTabTest` |
| Profiles and storage assignment | `WebViewProfileRulesTest`, `BrowserControllerProfilesInstrumentedTest` |
| Previews | `TabPreview*Test`, `TabPreviewRefreshInstrumentedTest` |
| Snoozing | `Snooze*Test`, `Snooze*InstrumentedTest` |
| Tab overview layout and reorder | `TabOverviewReorderInstrumentedTest` |
