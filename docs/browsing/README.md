# Browsing and gestures

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Runtime ownership, WebView lifecycle, navigation, fullscreen video and PiP | [`runtime-and-navigation.md`](runtime-and-navigation.md) | `MainActivity`, `BrowserController`, `BrowserTab`, `FullscreenVideoOverlay` |
| Address input, commands, gestures, Link Peek, actions | [`address-actions-and-ui.md`](address-actions-and-ui.md) | `browser/commands`, `browser/actions`, `browser/integration`, `ui/Address*` |
| Google AI Mode user flow, routing, persistence, and privacy | [`google-ai-mode.md`](google-ai-mode.md) | `SearchEngine`, `AddressAiModeRules`, `BrowserSessionStore`, `AddressAiModeToggle` |
| Appearance and browser theme | [`appearance-and-settings.md`](appearance-and-settings.md) | `AppearanceSettings`, `MaterialBrowserTheme`, `SettingsScreen` |
| Toppings / local userscripts | [`userscripts.md`](userscripts.md) | `browser/userscript`, `UserScriptStore`, `ToppingCatalogRepository` |

## Test lookup

| Surface | Tests |
| --- | --- |
| URL, search, AI mode, URI policy | `AddressResolverTest`, `SearchEngineTest`, `AddressAiModeRulesTest`, `AddressAiModeToggleInstrumentedTest`, `BrowserUriPolicyTest` |
| Commands and suggestions | `browser/commands/*Test`, `SearchSuggestionProviderTest` |
| Gestures and motion | `ui/Address*Test`, `ui/Address*InstrumentedTest` |
| WebView runtime and Link Peek | `browser/*InstrumentedTest`, `ui/LinkPeekOverlayInstrumentedTest` |
| Topping parsing, catalog integrity, storage and UI | `browser/userscript/*Test`, `*Topping*InstrumentedTest`, `UserscriptManagementScreenInstrumentedTest` |
| Web media, fullscreen and PiP | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest`, `FullscreenVideoOverlayInstrumentedTest` |
