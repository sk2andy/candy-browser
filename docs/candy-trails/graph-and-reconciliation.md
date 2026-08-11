# Graph and reconciliation

## Graph model

| Type | Meaning |
| --- | --- |
| `CandyTrail` | Per-tab graph, current node, monotonic node/fork ordinals |
| `CandyTrailNode` | HTTP(S) visit with stable ID, optional parent, title and timestamp |
| `CandyTrailFork` | Link from an origin node to another compatible tab; open or closed lifecycle |
| `CandyTrailHistoryBinding` | Mapping between live WebView history indices and trail node IDs |

## Rules

| Operation | Source | Invariant |
| --- | --- | --- |
| Record/select/update | [`CandyTrail.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/CandyTrail.kt) | Accept only HTTP(S); cap URL/title; reuse traversal targets |
| Normalize/retain | `CandyTrailRules` | Repair missing/cyclic parents; retain bounded graph and protected ancestry |
| Reconcile | [`CandyTrailHistoryReconciler.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/CandyTrailHistoryReconciler.kt) | Bind WebView back/forward/reload to existing nodes when identity is known |
| Fork | [`CandyTrailFork.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/CandyTrailFork.kt) | Origin/destination differ but share profile and privacy mode |

## Bounds

| Data | Limit |
| --- | ---: |
| Nodes per trail | 64 |
| Forks per trail | 32 |
| URL | 2,048 characters |
| Title | 160 characters |

Test new graph behavior as pure rules first. Add WebView instrumentation when history-index timing is part of the behavior.

