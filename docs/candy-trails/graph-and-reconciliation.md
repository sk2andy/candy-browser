# Graph and reconciliation

## Graph model

| Type | Meaning |
| --- | --- |
| `CandyTrail` | Per-tab graph, current node, monotonic node/fork ordinals |
| `CandyTrailNode` | HTTP(S) visit with stable ID, optional parent, title and timestamp |
| `CandyTrailFork` | Link from an origin node to another compatible tab; open or closed lifecycle |
| `CandyTrailHistoryBinding` | Mapping between the active engine's history indices and trail node IDs |

## Rules

| Operation | Source | Invariant |
| --- | --- | --- |
| Record/select/update | [`CandyTrail.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/browser/CandyTrail.kt) | Accept only HTTP(S); cap URL/title; reuse traversal targets |
| Normalize/retain | `CandyTrailRules` | Repair missing/cyclic parents; retain bounded graph and protected ancestry |
| History redaction | `CandyTrailRules` | Remove profile/time-matched nodes, reconnect children to the nearest retained ancestor and discard forks whose origin was removed |
| Reconcile | [`CandyTrailHistoryReconciler.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/browser/CandyTrailHistoryReconciler.kt) | Bind WebView, Gecko or WKWebView back/forward/reload to existing nodes when identity is known |
| Gecko history | `browser/gecko/GeckoCandyTrailHistory.kt` | Convert GeckoView's immutable history list/current index into the same reconciler input; only history events may change graph topology |
| Title refinement | `CandyTrailHistoryReconciler.refineCurrentTitle` | Accept a late engine title only when its URL already matches the current node, preventing location/history callback races from rewriting ancestry |
| Fork | [`CandyTrailFork.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/browser/CandyTrailFork.kt) | Origin/destination differ but share profile and privacy mode |

## Bounds

| Data | Limit |
| --- | ---: |
| Nodes per trail | 64 |
| Forks per trail | 32 |
| URL | 2,048 characters |
| Title | 160 characters |

Test new graph behavior as pure rules first. Add engine instrumentation when native history-index timing is part of the behavior.
