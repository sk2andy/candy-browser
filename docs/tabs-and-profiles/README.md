# Tabs and profiles

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Tab state, ordering, retention, persistence | [`lifecycle-and-persistence.md`](lifecycle-and-persistence.md) | `BrowserTab`, `data/Tab*Rules`, `BrowserSessionStore` |
| Previews, snoozing, profiles, WebView isolation | [`previews-snooze-and-isolation.md`](previews-snooze-and-isolation.md) | `TabPreview*`, `SnoozedTab*`, `WebViewProfileRules` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Pure tab rules | `data/Tab*Test`, `BrowserTabTest` |
| Profiles and storage assignment | `WebViewProfileRulesTest`, `BrowserControllerProfilesInstrumentedTest` |
| Previews | `TabPreview*Test`, `TabPreviewRefreshInstrumentedTest` |
| Snoozing | `Snooze*Test`, `Snooze*InstrumentedTest` |

