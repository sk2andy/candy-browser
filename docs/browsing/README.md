# Browsing and gestures

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Runtime ownership, WebView lifecycle and navigation | [`runtime-and-navigation.md`](runtime-and-navigation.md) | `MainActivity`, `MainActivityPictureInPictureController`, `BrowserController`, `BrowserViewport`, `BrowserTab` |
| Gecko page continuity across tab switches and long background intervals | [`runtime-and-navigation.md`](runtime-and-navigation.md) | `GeckoContentPresentationGate`, `GeckoResumeCover`, `GeckoTabInteractionInstrumentedTest`, `BrowserPageResumeInstrumentedTest` |
| Default-on Auto De-AMP navigation and trusted URL shapes | [`runtime-and-navigation.md`](runtime-and-navigation.md#navigation-paths) | `AutoDeAmpRules`, `BrowserController`, `BrowserSessionStore`, `ProtectionSettingsPage` |
| Android Gecko, Firefox extensions and iOS WebKit boundary | [`platform-engines.md`](platform-engines.md) | `BrowserController`, `browser/gecko`, `shared`, `iosApp` |
| HTTPS-only modes, local warning and session exceptions | [`platform-engines.md`](platform-engines.md#https-only) | `HttpsOnlyMode`, `GeckoHttpsOnlyErrorPage`, `ProtectionSettingsPage` |
| Shared Gecko / System WebView CSS safe-area protection and developer tuning | [`runtime-and-navigation.md`](runtime-and-navigation.md#shared-css-safe-area-controls) | `GeckoSafeAreaSettings`, `SystemWebViewSafeAreaScript`, `DeveloperOptionsSettingsPage` |
| Shared Candy Edge persistent-CSS body-padding / fixed-sticky-top and event-driven stylesheet prototype | [`runtime-and-navigation.md`](runtime-and-navigation.md#current-candy-edge-prototype) | `content_safe_area_prototype.js`, Reddit component helper `content_safe_area_reddit.js`; larger classifier retained but not selected |
| Opt-in Gecko sampling, manual DOM probe and scroll/blur diagnostics | [`performance-diagnostics.md`](performance-diagnostics.md) | `GeckoPerformanceDiagnostics`, `GeckoDomDiagnostics`, `BrowserPerformanceTrace`, `GeckoPrivacyHostRuntime`, `WebContentTopInsetScript` |
| User-enabled app logs, raw Gecko diagnostics and manual export | [`app-logging.md`](app-logging.md) | `AppLogging`, `GeckoLogging`, `GeckoLogStore`, `DeveloperOptionsSettingsPage`, `CandyApplication` |
| Android/iOS feature-parity contract and completion gates | [`platform-feature-parity.md`](platform-feature-parity.md) | Shared semantic models, platform engine adapters and native renderers |
| Payment Request and checkout popups | [`platform-engines.md`](platform-engines.md#android-engine-selection) | `SystemWebViewBrowserEngineAdapter`, `BrowserController`, `PopupNavigationRules` |
| Fullscreen video, website/Android PiP and background media controls | [`picture-in-picture.md`](picture-in-picture.md) | `MainActivityPictureInPictureController`, `BrowserController`, `BrowserMediaPlaybackService`, `GeckoMedia3Playback`, `WebMediaContract`, `WebMediaBridgeScript`, `FullscreenVideoOverlay` |
| Google Cast remote video playback | [`google-cast.md`](google-cast.md) | `CastMediaRules`, `CastSessionController`, `CastControls` |
| GeckoView website push delivery | [`web-push.md`](web-push.md) | `GeckoWebPushCoordinator`, `FossWebPushTransport`, `FossWebPushStore` |
| Address input, commands, gestures, Link Peek, actions | [`address-actions-and-ui.md`](address-actions-and-ui.md) | `browser/commands`, `browser/actions`, `browser/integration`, `ui/Address*` |
| Site cookie/storage deletion and engine limitations | [`site-data.md`](site-data.md) | `SiteDataRules`, `WebViewSiteData`, `SiteDataConfirmationDialog` |
| Candy Recall local full-text history search | [`recall.md`](recall.md) | `recall`, `RecallRepository`, address suggestions, History search |
| SearXNG search, instance configuration, suggestions, fallback | [`searxng.md`](searxng.md) | `SearxngSettings`, `SearchEngine`, `SearchSuggestionProvider`, `BrowserSessionStore` |
| Kagi search and privacy-enhanced suggestions | [`kagi.md`](kagi.md) | `SearchEngine`, `SearchSuggestionProvider` |
| Google AI Mode user flow, routing, persistence, and privacy | [`google-ai-mode.md`](google-ai-mode.md) | `SearchEngine`, `AddressAiModeRules`, `BrowserSessionStore`, `AddressAiModeToggle` |
| Page translation providers, URL routing, persistence, and privacy | [`page-translation.md`](page-translation.md) | `PageTranslationRules`, `BrowserController`, `BrowserSessionStore`, `BrowserMainMenu`, `BrowserSettingsPage` |
| Appearance and browser theme | [`appearance-and-settings.md`](appearance-and-settings.md) | `AppearanceSettings`, `MaterialBrowserTheme`, `AppearanceNightMode`, `SettingsScreen`, `AppearanceSettingsPage` |
| Localized settings search and capability-filtered navigation | [`appearance-and-settings.md`](appearance-and-settings.md#settings-search) | `SettingsSearchRules`, `SettingsSearchCatalog`, `SettingsSearchField`, shared `SettingsSearchTarget` |
| Profile-scoped browsing history and retention controls | [`history.md`](history.md) | `BrowsingHistoryRepository`, `HistoryActivity`, `HistoryScreen` |
| Local download progress, search and cleanup | [`downloads.md`](downloads.md) | `DownloadRepository`, `DownloadsActivity`, `DownloadsScreen` |
| Favorite library, search and deletion undo | [`favorites.md`](favorites.md) | `BrowserSessionStore`, `BrowsingFavoritesRules`, `FavoritesActivity`, `FavoritesScreen` |
| App data ZIP export/import | [`app-data-archive.md`](app-data-archive.md) | `AppDataArchive*`, `AppDataTransferActivity`, `MainActivity`, `ProtectionSettingsPage` |
| Toppings / local userscripts | [`userscripts.md`](userscripts.md) | `browser/userscript`, `UserScriptStore`, `ToppingCatalogRepository` |

## Test lookup

Issue 205's controlled reproductions and integrated checks are recorded in
[`../audits/issue-205-gecko-loading-and-recovery.md`](../audits/issue-205-gecko-loading-and-recovery.md).

The GeckoView 155 to 157 compatibility review, upstream fixes and upgrade checks are recorded in
[`../audits/geckoview-157-upgrade.md`](../audits/geckoview-157-upgrade.md).

Issue 236's Candy Close fullscreen regression and launcher checks are recorded in
[`../audits/issue-236-candy-player.md`](../audits/issue-236-candy-player.md).

Issue 241's real YouTube PiP ancestor feedback reproduction, repeated native Home/return checks
and viewport measurements are recorded in
[`../audits/issue-241-gecko-youtube-pip.md`](../audits/issue-241-gecko-youtube-pip.md).

Issue 221's bootstrap-history reproduction, popup boundaries and verification are recorded in
[`../audits/issue-221-gecko-navigation.md`](../audits/issue-221-gecko-navigation.md).

Issue 239's Google identity popup routing, native close lifecycle and live Claude login checks are recorded in
[`../audits/issue-239-claude-google-login.md`](../audits/issue-239-claude-google-login.md).

Issue 259's System WebView OAuth popup adoption, POST/opener preservation and scoped login
compatibility checks are recorded in [`../audits/issue-259-system-webview-google-login.md`](../audits/issue-259-system-webview-google-login.md).

Issue 222's search-link History API race and navigation cancellation checks are recorded in
[`../audits/issue-222-external-app-navigation.md`](../audits/issue-222-external-app-navigation.md).

Issue 278's Gecko REWE header jump and retained Cover safe-area checks are recorded in
[`../audits/issue-278-cover-header-jumping.md`](../audits/issue-278-cover-header-jumping.md).

Lidl's interrupted CSS processing and GitHub's narrow navigation-panel investigation are recorded
in [`../audits/gecko-header-scroll-and-sidebars.md`](../audits/gecko-header-scroll-and-sidebars.md).

Dark System appearance, native website preferences and the address-editor/tab SurfaceView handoff
are recorded in [`../audits/dark-appearance-and-address-tab-handoff.md`](../audits/dark-appearance-and-address-tab-handoff.md).

| Surface | Tests |
| --- | --- |
| URL, search, AI mode, URI policy | `AddressResolverTest`, `SearchEngineTest`, `AddressAiModeRulesTest`, `AddressAiModeToggleInstrumentedTest`, `BrowserUriPolicyTest` |
| Gecko internal pages and engine boundary | `GeckoInternalPageRulesTest`, `AddressResolverTest`, `AddressAiModeRulesTest`, `SearchSuggestionProviderTest`, `GeckoInternalPageNavigationInstrumentedTest`, `GeckoRuntimeSettingsInstrumentedTest` |
| Gecko first-page Back, native popup binding, POST and private navigation | `GeckoBootstrapHistoryRulesTest`, `GeckoNativeSessionBindingRulesTest`, `PopupNavigationRulesTest`, `GeckoBootstrapHistoryInstrumentedTest`, `GeckoPopupNavigationInstrumentedTest`, `scripts/gecko_native_session_binding.test.mjs` |
| Auto De-AMP URL policy, persistence, navigation and UI | `AutoDeAmpRulesTest`, `AutoDeAmpSettingsInstrumentedTest`, focused `BrowserControllerGeckoViewBindingInstrumentedTest` method |
| External app settings, host-specific handlers, availability, prompts, redirects and returned links | `GeckoExternalAppNavigationInstrumentedTest`, `ExternalAppLinkHandlingTest`, `ExternalWebAppHandlerRulesInstrumentedTest`, `ExternalAppLauncherInstrumentedTest`, `BrowserSessionStoreInstrumentedTest`, `BrowserSettingsScreenInstrumentedTest`, focused `BrowserControllerGeckoViewBindingInstrumentedTest` methods |
| Commands and suggestions | `browser/commands/*Test`, `SearchSuggestionProviderTest` |
| Candy Recall rules, extraction, SQLite ranking and UI | `recall/*Test`, `RecallRepositoryInstrumentedTest`, focused address/History instrumented tests |
| Gestures and motion | `ui/Address*Test`, `ui/Address*InstrumentedTest` |
| Gecko keyboard viewport, focused input, chrome-owned IME and new-tab suggestion handoff | `GeckoViewInsetRulesTest`, `GeckoKeyboardInsetsInstrumentedTest`, `GeckoAddressTabSelectionInstrumentedTest`, `FullImmersiveModeInstrumentedTest` |
| Missed hidden-IME delivery after Activity resume or restored window focus | `GeckoKeyboardInsetsInstrumentedTest#resumeReconcilesKeyboardMarginsWhenHiddenInsetsWereMissed`, `#restoredWindowFocusReconcilesKeyboardMarginsWhenHiddenInsetsWereMissed`; assert native margins, visual viewport and restored lower-page pixels |
| Bottom control obstruction and automatic right parking | `AddressBarAutoDockRulesTest`, `TextInputOcclusionScriptTest`, `scripts/text_input_occlusion.test.mjs`, `GeckoPageControlOcclusionInstrumentedTest`, focused controller/System WebView instrumented tests |
| Restored launcher tab and initial Gecko viewport | `StartupPresentationRulesTest`, `BrowserInitialNavigationRulesTest`, `MainActivityRestoredTabInstrumentedTest`, `BrowserControllerInitialViewportInstrumentedTest` |
| WebView runtime, Basic authentication and Link Peek | `browser/*InstrumentedTest`, `BrowserControllerHttpAuthInstrumentedTest`, `ui/HttpAuthPromptDialogInstrumentedTest`, `ui/LinkPeekOverlayInstrumentedTest` |
| System WebView native OAuth popups, POST, close, Blob downloads and login compatibility | `SystemWebViewPopupNavigationInstrumentedTest`, `SystemWebViewBlobDownloadInstrumentedTest`, `FederatedLoginRulesTest` |
| Held OS Back over page links and canceled gesture recovery | `GeckoContentGestureRulesTest`, `GeckoBackGestureLinkPeekInstrumentedTest` |
| Payment Request and checkout popup routing | `SystemWebViewBrowserEngineInstrumentedTest`, `BrowserControllerGeckoViewBindingInstrumentedTest` |
| Topping parsing, catalog integrity, storage and UI | `browser/userscript/*Test`, `*Topping*InstrumentedTest`, `UserscriptManagementScreenInstrumentedTest` |
| Shared browser behavior and Gecko extension policy | `shared/src/commonTest`, `browser/gecko/*Test` |
| Gecko tab interaction, retained paint ordering and long background resume | `GeckoContentPresentationGateTest`, `GeckoTabInteractionInstrumentedTest`, `BrowserPageResumeInstrumentedTest`; evidence and limits: [`../audits/gecko-tab-paint-reset-recovery.md`](../audits/gecko-tab-paint-reset-recovery.md) |
| Gecko departing-frame pixel geometry, viewport resize and focused-editor file-picker return | `GeckoResumeCoverInstrumentedTest`, `GeckoIoPromptsInstrumentedTest#cancellingFilePickerWithFocusedEditorRestoresLivePageWithoutResumeCover`; identity rendering and capture invalidation: [`runtime-and-navigation.md`](runtime-and-navigation.md#invariants) |
| Gecko loading surface, bootstrap canvas, first paint, reload and view reattachment | `GeckoWebContentThemeInstrumentedTest#unpaintedGeckoSurfaceUsesDarkBackground`, `#unpaintedGeckoSurfaceUsesLightBackground`, `#darkLoadingSurfaceReleasesForFirstPaintAndReload`, `#lightLoadingSurfaceReleasesForFirstPaintAndReload` |
| Gecko website/Compose appearance, background night changes, missed configuration delivery and nested AppCompat night overrides | `GeckoAppearanceInstrumentedTest`, `GeckoWebContentThemeInstrumentedTest`; the cold-start method requires system dark before a fresh, isolated instrumentation process. Resume reproduction details: [`../audits/background-appearance-reconciliation.md`](../audits/background-appearance-reconciliation.md) |
| Dark new-tab/editor logo backing and tab-switch suggestion contrast | `BlankTabColorRulesTest`, `AddressSuggestionColorRulesTest`, `AddressEditorDarkThemeInstrumentedTest` |
| Effective website theme independent from native engine night caches | `BrowserWebContentAppearanceRulesTest`, focused `GeckoAppearanceInstrumentedTest.systemWebsitePreferenceIgnoresStaleGeckoNightConfiguration` |
| First inline website theme during a fresh System-dark process | `GeckoColdStartThemeInstrumentedTest`; run alone after enabling system dark and force-stopping the app |
| Edge-to-edge Safe-Area, Shadow DOM, observable fixture readiness and scroll scaling | `WebContentTopInsetScriptTest`, `WebContentTopInsetScriptInstrumentedTest`, `GeckoEdgeToEdgeInstrumentedTest`, `GeckoSafeAreaScalingInstrumentedTest`, `scripts/web_content_top_inset_performance.test.mjs`, `scripts/edge_to_edge_site_fixture.test.mjs` |
| Diagnostic activation, export and private-session cancellation | `GeckoPerformanceDiagnosticsRulesTest`, `GeckoPerformanceDiagnosticsInstrumentedTest`, `BrowserPerformanceTraceTest`, `scripts/web_content_top_inset_diagnostics.test.mjs` |
| Manual DOM probe, native env delivery, bounded payload and lifecycle cancellation | `GeckoDomDiagnosticsRulesTest`, `GeckoDomDiagnosticsInstrumentedTest`, `scripts/gecko_dom_probe.test.mjs` |
| Gecko bounded CSS classification, mutation gating, restoration and settings | `GeckoSafeAreaSettingsTest`, `GeckoCssSafeAreaInstrumentedTest`, `DeveloperOptionsSettingsPageInstrumentedTest`, `scripts/gecko_css_safe_area.test.mjs` |
| Shared Candy Edge prototype anchors, coalesced known-header top changes, owned CSS, cancellation and update gates | `scripts/gecko_safe_area_prototype.test.mjs`, `GeckoSafeAreaPrototypeInstrumentedTest`; live Release-APK smoke and manual site testing remain separate |
| System WebView shared CSS body/fixed/sticky protection, cover/author-env restoration, native/safe-host exclusions and private policies | `SystemWebViewSafeAreaInstrumentedTest`, `SystemWebViewBrowserEngineInstrumentedTest`; site matrix and Force safe area without duplicate offsets: `SystemWebViewEdgeToEdgeInstrumentedTest` |
| System WebView game/video fullscreen, device orientation and scroll restoration | `BrowserWindowStateRulesTest`, `FullImmersiveModeInstrumentedTest`, `SystemWebViewMediaBridgeInstrumentedTest`, focused `SystemWebViewBrowserEngineInstrumentedTest` methods |
| Gecko default-extension catalog, integrity and runtime install | `scripts/test_generate_gecko_default_extensions.py`, `GeckoDefaultExtension*Test`, `GeckoDefaultExtensionProvisioningInstrumentedTest` |
| Gecko website push protocol, decryption, foreground delivery and encrypted storage | `FossWebPushProtocolTest`, `WebPushCryptoTest`, `FossWebPushTransportInstrumentedTest`, `FossWebPushStoreInstrumentedTest` |
| Web media, fullscreen, PiP and Android system controls | `WebMediaContractTest`, `WebMediaBridgeInstrumentedTest`, `FullscreenVideoRulesTest`, `FullscreenVideoInstrumentedTest`, `FullscreenVideoActivityInstrumentedTest`, `FullscreenVideoOverlayInstrumentedTest`, `GeckoMedia3PlaybackTest`, `GeckoMedia3PlayerInstrumentedTest`, `BrowserMediaPlaybackServiceInstrumentedTest`, `GeckoMedia3ActivityE2eInstrumentedTest` |
