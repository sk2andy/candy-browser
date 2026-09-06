# Persistence and UI

## Persistence flow

| Stage | Source | Behavior |
| --- | --- | --- |
| Eligibility | `CandyTrailPersistenceRules` | Persist only eligible non-incognito tabs |
| Queue | [`CandyTrailRepository.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/CandyTrailRepository.kt) | Serialize restore/save/delete work on one executor |
| File | [`CandyTrailStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/CandyTrailStore.kt) | Versioned, atomic JSON in `noBackupFilesDir`; max 256 KiB |
| Restore | Repository + controller | Merge early WebView/Gecko runtime nodes with restored graph and remap history bindings |
| Cleanup | Store/repository | Prune files whose tab IDs are no longer retained |
| History redaction | History store + repository | Persist an idempotent tab/range job with history deletion, rewrite trail files in executor order, then acknowledge; retry interrupted jobs on foreground/startup |

## UI flow

| Concern | Source | Boundary |
| --- | --- | --- |
| Graph layout | `CandyTrailLayoutRules` | Deterministic node/fork positions from graph only |
| Viewport | `CandyTrailViewportRules` | Clamp pan/zoom and center selected content |
| Motion | `CandyTrailGraphMotion`, `CandyTrailMotionRules` | Keep animation math separate from graph mutation |
| Screen | [`CandyTrailScreen.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/CandyTrailScreen.kt) | Shared Android/iOS graph renderer emitting select/fork/reopen/close actions |

iOS supplies tab/session-identified `WKBackForwardList` snapshots to the shared reconciler and keeps the
resulting per-tab trails in memory. Private or session-ephemeral WebKit sessions emit no Trail state.

- Never write private trails to disk.
- Never create or write trails for session-ephemeral federated-login popup tabs.
- Preserve format-version migration and bounds when changing encoded fields.
- Test layout/motion on JVM; test Compose interaction and native engine traversal with instrumentation.
