# Privacy protection

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Request/cosmetic blocking, Candy Rules, validation and precedence | [`blocking-and-rules.md`](blocking-and-rules.md) | `blocking/ContentBlocker`, `RequestBlocker`, `CandyRule*` |
| Privacy X-Ray, Permission Radar, asset maintenance and audits | [`xray-permissions-and-maintenance.md`](xray-permissions-and-maintenance.md) | `PrivacyXRay`, `browser/permissions`, `scripts/`, `docs/audits/` |
| WebRTC IP protection and engine fallbacks | [`../browsing/platform-engines.md`](../browsing/platform-engines.md#webrtc-protection) | `WebRtcProtectionMode`, engine factories, Gecko Privacy host |
| Do Not Track and Global Privacy Control | [`../browsing/platform-engines.md`](../browsing/platform-engines.md#privacy-signals) | `PrivacySignalSettings`, engine privacy policy, Gecko Privacy host |
| Browser and page animation suppression | [`../browsing/platform-engines.md`](../browsing/platform-engines.md#animation-suppression) | `AppearanceSettings`, engine privacy policy, Gecko Privacy host |
| Default-on Auto De-AMP navigation | [`../browsing/runtime-and-navigation.md`](../browsing/runtime-and-navigation.md#navigation-paths) | `AutoDeAmpRules`, `BrowserController`, `BrowserSessionStore`, `ProtectionSettingsPage` |
| GeckoView encrypted DNS and System WebView boundary | [`../browsing/platform-engines.md`](../browsing/platform-engines.md#encrypted-dns) | `DnsOverHttpsSettings`, `BrowserSessionStore`, Gecko runtime settings |

## Test lookup

| Surface | Tests |
| --- | --- |
| Host/cosmetic filtering | `blocking/RequestBlockerTest`, `EasyListCosmeticRulesTest`, blocker instrumented tests |
| Candy Rules and Filter Studio | `CandyRule*Test`, `FilterStudioScreenInstrumentedTest` |
| X-Ray and permissions | `PrivacyXRayTest`, `permissions/*Test`, matching UI instrumented tests |
| Federated-login cookie exception | `FederatedLoginRulesTest`, `FederatedLoginPromptInstrumentedTest`, `BrowserSessionStoreInstrumentedTest` |
| CAPTCHA cookie exception | `CaptchaCompatibilityRulesTest`, `CaptchaCompatibilityPromptInstrumentedTest`, `BrowserControllerCaptchaCompatibilityInstrumentedTest` |
| WebRTC protection | `WebRtcProtectionRulesTest`, `WebRtcBlockerScriptInstrumentedTest`, `WebRtcProtectionSettingsInstrumentedTest`, `GeckoWebRtcProtectionInstrumentedTest`, `SystemWebViewBrowserEngineInstrumentedTest`, `candy_privacy_rules.test.mjs` |
| DNT and GPC | `PrivacySignalSettingsTest`, `PrivacySignalSettingsInstrumentedTest`, `CandyPrivacyHostContractTest`, `SystemWebViewBrowserEngineInstrumentedTest`, `privacy_signals.test.mjs` |
| Animation suppression | `WebContentAnimationPolicyScriptTest`, `CandyPrivacyHostContractTest`, `animation_policy.test.mjs`, `candy_privacy_rules.test.mjs` |
| Auto De-AMP | `AutoDeAmpRulesTest`, `AutoDeAmpSettingsInstrumentedTest`, focused `BrowserControllerGeckoViewBindingInstrumentedTest` method |
| Encrypted DNS | `DnsOverHttpsSettingsTest`, `DnsOverHttpsSettingsInstrumentedTest`, `GeckoDnsOverHttpsSettingsInstrumentedTest`, `BrowserSessionStoreInstrumentedTest` |
| Generated assets and audits | `scripts/test_*`, `scripts/*.test.mjs`, `blocking/*AssetInstrumentedTest` |
