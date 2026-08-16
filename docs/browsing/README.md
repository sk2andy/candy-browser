# Browsing and gestures

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Runtime ownership, WebView lifecycle, navigation, fullscreen video and PiP | [`runtime-and-navigation.md`](runtime-and-navigation.md) | `MainActivity`, `BrowserController`, `BrowserTab`, `FullscreenVideoOverlay` |
| Address input, commands, gestures, Link Peek, actions | [`address-actions-and-ui.md`](address-actions-and-ui.md) | `browser/commands`, `browser/actions`, `browser/integration`, `ui/Address*` |
| Appearance and browser theme | [`appearance-and-settings.md`](appearance-and-settings.md) | `AppearanceSettings`, `MaterialBrowserTheme`, `SettingsScreen` |

## Test lookup

| Surface | Tests |
| --- | --- |
| URL, search, URI policy | `AddressResolverTest`, `SearchEngineTest`, `BrowserUriPolicyTest` |
| Commands and suggestions | `browser/commands/*Test`, `SearchSuggestionProviderTest` |
| Gestures and motion | `ui/Address*Test`, `ui/Address*InstrumentedTest` |
| WebView runtime and Link Peek | `browser/*InstrumentedTest`, `ui/LinkPeekOverlayInstrumentedTest` |
| Web media, fullscreen and PiP | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest`, `FullscreenVideoOverlayInstrumentedTest` |
