# Privacy protection

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Request/cosmetic blocking, Candy Rules, validation and precedence | [`blocking-and-rules.md`](blocking-and-rules.md) | `blocking/ContentBlocker`, `RequestBlocker`, `CandyRule*` |
| Privacy X-Ray, Permission Radar, asset maintenance and audits | [`xray-permissions-and-maintenance.md`](xray-permissions-and-maintenance.md) | `PrivacyXRay`, `browser/permissions`, `scripts/`, `docs/audits/` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Host/cosmetic filtering | `blocking/RequestBlockerTest`, `EasyListCosmeticRulesTest`, blocker instrumented tests |
| Candy Rules and Filter Studio | `CandyRule*Test`, `FilterStudioScreenInstrumentedTest` |
| X-Ray and permissions | `PrivacyXRayTest`, `permissions/*Test`, matching UI instrumented tests |
| Federated-login cookie exception | `FederatedLoginRulesTest`, `FederatedLoginPromptInstrumentedTest`, `BrowserSessionStoreInstrumentedTest` |
| Generated assets and audits | `scripts/test_*`, `scripts/*.test.mjs`, `blocking/*AssetInstrumentedTest` |
