# Persistence and UI

## Persistence flow

| Stage | Source | Behavior |
| --- | --- | --- |
| Eligibility | `CandyTrailPersistenceRules` | Persist only eligible non-incognito tabs |
| Queue | [`CandyTrailRepository.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/CandyTrailRepository.kt) | Serialize restore/save/delete work on one executor |
| File | [`CandyTrailStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/CandyTrailStore.kt) | Versioned, atomic JSON in `noBackupFilesDir`; max 256 KiB |
| Restore | Repository + controller | Merge early runtime nodes with restored graph and remap history bindings |
| Cleanup | Store/repository | Prune files whose tab IDs are no longer retained |

## UI flow

| Concern | Source | Boundary |
| --- | --- | --- |
| Graph layout | `CandyTrailLayoutRules` | Deterministic node/fork positions from graph only |
| Viewport | `CandyTrailViewportRules` | Clamp pan/zoom and center selected content |
| Motion | `CandyTrailGraphMotion`, `CandyTrailMotionRules` | Keep animation math separate from graph mutation |
| Screen | [`CandyTrailScreen.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/CandyTrailScreen.kt) | Render graph and emit select/fork/reopen/close actions |

- Never write private trails to disk.
- Preserve format-version migration and bounds when changing encoded fields.
- Test layout/motion on JVM; test Compose interaction and WebView traversal with instrumentation.

