# Browsing and gestures

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Runtime ownership, WebView lifecycle and navigation | [`runtime-and-navigation.md`](runtime-and-navigation.md) | `MainActivity`, `MainActivityPictureInPictureController`, `BrowserController`, `BrowserViewport`, `BrowserTab` |
| Android Gecko, Firefox extensions and iOS WebKit boundary | [`platform-engines.md`](platform-engines.md) | `BrowserController`, `browser/gecko`, `shared`, `iosApp` |
| Android/iOS feature-parity contract and completion gates | [`platform-feature-parity.md`](platform-feature-parity.md) | Shared semantic models, platform engine adapters and native renderers |
| Fullscreen video, website PiP and background PiP | [`picture-in-picture.md`](picture-in-picture.md) | `MainActivityPictureInPictureController`, `BrowserController`, `WebMediaContract`, `WebMediaBridgeScript`, `FullscreenVideoOverlay` |
| Google Cast remote video playback | [`google-cast.md`](google-cast.md) | `CastMediaRules`, `CastSessionController`, `CastControls` |
| Address input, commands, gestures, Link Peek, actions | [`address-actions-and-ui.md`](address-actions-and-ui.md) | `browser/commands`, `browser/actions`, `browser/integration`, `ui/Address*` |
| Candy Recall local full-text history search | [`recall.md`](recall.md) | `recall`, `RecallRepository`, address suggestions, History search |
| SearXNG search, instance configuration, suggestions, fallback | [`searxng.md`](searxng.md) | `SearxngSettings`, `SearchEngine`, `SearchSuggestionProvider`, `BrowserSessionStore` |
| Kagi search and privacy-enhanced suggestions | [`kagi.md`](kagi.md) | `SearchEngine`, `SearchSuggestionProvider` |
| Google AI Mode user flow, routing, persistence, and privacy | [`google-ai-mode.md`](google-ai-mode.md) | `SearchEngine`, `AddressAiModeRules`, `BrowserSessionStore`, `AddressAiModeToggle` |
| Page translation providers, URL routing, persistence, and privacy | [`page-translation.md`](page-translation.md) | `PageTranslationRules`, `BrowserController`, `BrowserSessionStore`, `BrowserMainMenu`, `BrowserSettingsPage` |
| Appearance and browser theme | [`appearance-and-settings.md`](appearance-and-settings.md) | `AppearanceSettings`, `MaterialBrowserTheme`, `AppearanceNightMode`, `SettingsScreen`, `AppearanceSettingsPage` |
| Profile-scoped browsing history and retention controls | [`history.md`](history.md) | `BrowsingHistoryRepository`, `HistoryActivity`, `HistoryScreen` |
| Local download progress, search and cleanup | [`downloads.md`](downloads.md) | `DownloadRepository`, `DownloadsActivity`, `DownloadsScreen` |
| Favorite library, search and deletion undo | [`favorites.md`](favorites.md) | `BrowserSessionStore`, `BrowsingFavoritesRules`, `FavoritesActivity`, `FavoritesScreen` |
| App data ZIP export/import | [`app-data-archive.md`](app-data-archive.md) | `AppDataArchive*`, `AppDataTransferActivity`, `MainActivity`, `ProtectionSettingsPage` |
| Toppings / local userscripts | [`userscripts.md`](userscripts.md) | `browser/userscript`, `UserScriptStore`, `ToppingCatalogRepository` |

## Test lookup

| Surface | Tests |
| --- | --- |
| URL, search, AI mode, URI policy | `AddressResolverTest`, `SearchEngineTest`, `AddressAiModeRulesTest`, `AddressAiModeToggleInstrumentedTest`, `BrowserUriPolicyTest` |
| Commands and suggestions | `browser/commands/*Test`, `SearchSuggestionProviderTest` |
| Candy Recall rules, extraction, SQLite ranking and UI | `recall/*Test`, `RecallRepositoryInstrumentedTest`, focused address/History instrumented tests |
| Gestures and motion | `ui/Address*Test`, `ui/Address*InstrumentedTest` |
| WebView runtime, Basic authentication and Link Peek | `browser/*InstrumentedTest`, `BrowserControllerHttpAuthInstrumentedTest`, `ui/HttpAuthPromptDialogInstrumentedTest`, `ui/LinkPeekOverlayInstrumentedTest` |
| Topping parsing, catalog integrity, storage and UI | `browser/userscript/*Test`, `*Topping*InstrumentedTest`, `UserscriptManagementScreenInstrumentedTest` |
| Shared browser behavior and Gecko extension policy | `shared/src/commonTest`, `browser/gecko/*Test` |
| Gecko default-extension catalog, integrity and runtime install | `scripts/test_generate_gecko_default_extensions.py`, `GeckoDefaultExtension*Test`, `GeckoDefaultExtensionProvisioningInstrumentedTest` |
| Web media, fullscreen and PiP | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest`, `FullscreenVideoOverlayInstrumentedTest` |
