# Candy Trails

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Graph model, navigation recording, WebView history reconciliation | [`graph-and-reconciliation.md`](graph-and-reconciliation.md) | `CandyTrail`, `CandyTrailHistoryReconciler`, `CandyTrailFork` |
| Persistence, restore, layout, viewport and screen | [`persistence-and-ui.md`](persistence-and-ui.md) | `CandyTrailStore`, `CandyTrailRepository`, `ui/CandyTrail*` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Graph and forks | `CandyTrailRulesTest`, `CandyTrailForkRulesTest` |
| History binding | `CandyTrailHistoryReconcilerTest`, `CandyTrailWebViewInstrumentedTest` |
| Persistence | `CandyTrailPersistenceRulesTest`, `CandyTrailStoreInstrumentedTest` |
| Layout and UI | `CandyTrailLayoutRulesTest`, `CandyTrailScreenInstrumentedTest` |

