# Browsing and gestures

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Runtime ownership, WebView lifecycle and navigation | [`runtime-and-navigation.md`](runtime-and-navigation.md) | `MainActivity`, `BrowserController`, `BrowserTab` |
| Fullscreen video, website PiP and background PiP | [`picture-in-picture.md`](picture-in-picture.md) | `MainActivity`, `BrowserController`, `WebMediaContract`, `WebMediaBridgeScript`, `FullscreenVideoOverlay` |
| Google Cast remote video playback | [`google-cast.md`](google-cast.md) | `CastMediaRules`, `CastSessionController`, `CastControls` |
| Address input, commands, gestures, Link Peek, actions | [`address-actions-and-ui.md`](address-actions-and-ui.md) | `browser/commands`, `browser/actions`, `browser/integration`, `ui/Address*` |
| Candy Recall local full-text history search | [`recall.md`](recall.md) | `recall`, `RecallRepository`, address suggestions, History search |
| SearXNG search, instance configuration, suggestions, fallback | [`searxng.md`](searxng.md) | `SearxngSettings`, `SearchEngine`, `SearchSuggestionProvider`, `BrowserSessionStore` |
| Kagi search and privacy-enhanced suggestions | [`kagi.md`](kagi.md) | `SearchEngine`, `SearchSuggestionProvider` |
| Google AI Mode user flow, routing, persistence, and privacy | [`google-ai-mode.md`](google-ai-mode.md) | `SearchEngine`, `AddressAiModeRules`, `BrowserSessionStore`, `AddressAiModeToggle` |
| Appearance and browser theme | [`appearance-and-settings.md`](appearance-and-settings.md) | `AppearanceSettings`, `MaterialBrowserTheme`, `SettingsScreen` |
| Profile-scoped browsing history and retention controls | [`history.md`](history.md) | `BrowsingHistoryRepository`, `HistoryActivity`, `HistoryScreen` |
| App data ZIP export/import | [`app-data-archive.md`](app-data-archive.md) | `AppDataArchive*`, `AppDataTransferActivity`, `SettingsScreen` |
| Toppings / local userscripts | [`userscripts.md`](userscripts.md) | `browser/userscript`, `UserScriptStore`, `ToppingCatalogRepository` |

## Test lookup

| Surface | Tests |
| --- | --- |
| URL, search, AI mode, URI policy | `AddressResolverTest`, `SearchEngineTest`, `AddressAiModeRulesTest`, `AddressAiModeToggleInstrumentedTest`, `BrowserUriPolicyTest` |
| Commands and suggestions | `browser/commands/*Test`, `SearchSuggestionProviderTest` |
| Candy Recall rules, extraction, SQLite ranking and UI | `recall/*Test`, `RecallRepositoryInstrumentedTest`, focused address/History instrumented tests |
| Gestures and motion | `ui/Address*Test`, `ui/Address*InstrumentedTest` |
| WebView runtime and Link Peek | `browser/*InstrumentedTest`, `ui/LinkPeekOverlayInstrumentedTest` |
| Topping parsing, catalog integrity, storage and UI | `browser/userscript/*Test`, `*Topping*InstrumentedTest`, `UserscriptManagementScreenInstrumentedTest` |
| Web media, fullscreen and PiP | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest`, `FullscreenVideoOverlayInstrumentedTest` |
