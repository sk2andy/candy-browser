# Candy Trails

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Graph model and WebView/Gecko history reconciliation | [`graph-and-reconciliation.md`](graph-and-reconciliation.md) | `CandyTrail`, `CandyTrailHistoryReconciler`, `GeckoCandyTrailHistory`, `CandyTrailFork` |
| Persistence, restore, layout, viewport and screen | [`persistence-and-ui.md`](persistence-and-ui.md) | `CandyTrailStore`, `CandyTrailRepository`, `ui/CandyTrail*` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Graph and forks | `CandyTrailRulesTest`, `CandyTrailForkRulesTest` |
| History binding | `CandyTrailHistoryReconcilerTest`, `GeckoCandyTrailHistoryTest`, `CandyTrailWebViewInstrumentedTest`, `GeckoCandyTrailInstrumentedTest` |
| Persistence | `CandyTrailPersistenceRulesTest`, `CandyTrailStoreInstrumentedTest` |
| Layout and UI | `CandyTrailLayoutRulesTest`, `CandyTrailScreenInstrumentedTest` |
