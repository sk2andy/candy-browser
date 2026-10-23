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
| Presentation | `CandyTrailPresentationRules` | Semantic zoom, nearest overview target and per-node branch projection; active ancestry is never hidden and persisted history is unchanged |
| Screen | [`CandyTrailScreen.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/CandyTrailScreen.kt) | Shared Android/iOS graph renderer emitting select/fork/reopen/close actions |

iOS supplies tab/session-identified `WKBackForwardList` snapshots to the shared reconciler and keeps the
resulting per-tab trails in memory. Private or session-ephemeral WebKit sessions emit no Trail state.

- Never write private trails to disk.
- Never create or write trails for session-ephemeral federated-login popup tabs.
- Preserve format-version migration and bounds when changing encoded fields.
- Test layout/motion on JVM; test Compose interaction and native engine traversal with instrumentation.

## Graph presentation

| Interaction | Behavior |
| --- | --- |
| Overview/detail | Low zoom shows topology symbols. Dense overview taps focus the nearest page or fork at readable zoom; they never navigate immediately. Full-detail cards show larger titles and source identity. |
| Navigation | Selecting a detailed page/fork dispatches immediately; entry/path animation never delays a browser action. |
| Camera | Separate Show all and Current page actions. Bounds account for measured header and bottom controls; pinch keeps the gesture anchor stable. |
| Branches | Node actions fold/expand that node’s side branches with hidden-item counts. Current page and its ancestors stay visible, even after navigation changes. Focus current path temporarily hides all side branches. |
| Alternate list | Graph/List selection offers the same navigation and branch actions with readable, scrollable cards. Large text uses the measured header height. |
| Motion | Cards and edges share one interpolated layout. Shared Compose uses M3 Expressive default spatial and fast effects spring tokens until its Material dependency exposes MotionScheme. Entry/path motion is bounded; no perpetual animation. |
| Favicons | Android reuses already-held icons from matching regular/private-mode, same-profile open tabs by exact URL. No historical favicon cache or network fetch is introduced. Other nodes retain source initials. |
| Details | Node action sheet exposes the complete title and URL, plus fork and branch actions. |

## Presentation verification

| Contract | Tests |
| --- | --- |
| Active ancestry, branch restoration, overview picking and synchronized layout | `CandyTrailPresentationRulesTest` |
| Immediate action dispatch, mounted graph deletion and fold/expand | `CandyTrailPolishInstrumentedTest` |
